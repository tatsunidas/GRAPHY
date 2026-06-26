#version 330 core

// Monte Carlo volumetric path tracer ("cinematic" rendering). Draws the same
// unit cube as volume.frag and reuses its box-intersection / transfer
// function (uMin/uMax/uWinCenter/uWinWidth -> uLutTex) conventions so the
// image lines up with - and respects the same opacity curve as - the
// existing VR/MIP modes. Differs from volume.frag by treating the opacity
// curve as a per-step *interaction probability* (stochastic free-flight
// sampling) instead of a deterministic alpha blend, which is what lets light
// scatter, cast soft shadows and bounce between voxels.
//
// Surface BRDF: when the opacity gradient at a hit point exceeds
// uSurfaceGradientThreshold, the point is treated as a tissue/material
// boundary and shaded with a Cook-Torrance GGX BRDF (diffuse + specular +
// optional clearcoat) instead of the Henyey-Greenstein phase function.
// This is what produces the matte/glossy/clearcoat "cinematic" look.
//
// Outputs ONE accumulated-radiance sample per invocation (averaged over
// uSamplesPerFrame internal samples); CinematicRendererGL adds this into a
// floating-point accumulation buffer with GL_ONE/GL_ONE blending across
// frames, and a separate present pass divides by the frame count and
// tonemaps for display.

in vec3 vPos;
out vec4 FragColor;

uniform sampler3D volumeTex;
uniform sampler1D uLutTex;

uniform vec3 cameraPos;
uniform float uMin;
uniform float uMax;
uniform float uWinCenter;
uniform float uWinWidth;

uniform vec3 uLightDir;            // unit vector, points FROM the scene TOWARD the light
uniform float uLightIntensity;
uniform float uAmbientIntensity;
uniform float uAnisotropy;         // Henyey-Greenstein g, in (-1, 1)
uniform float uLightAngularRadius; // area-light cone half-angle (radians)
uniform int uSamplesPerFrame;
uniform uint uFrameSeed;

// 3D裁断領域（ローカル単位立方体 -0.5〜0.5）
uniform vec3 uClipMin;
uniform vec3 uClipMax;

// PBR material parameters
uniform float uRoughness;                // 0=mirror/glossy, 1=fully matte/diffuse
uniform float uSpecular;                 // dielectric F0 weight (F0 = 0.04 * specular)
uniform float uMetallic;                 // 0=dielectric, 1=metallic
uniform float uClearcoat;               // clearcoat layer strength (0..1)
uniform float uClearcoatRoughness;      // clearcoat glossiness (0=mirror, ~0.05=typical)
uniform float uSurfaceGradientThreshold; // gradient magnitude >= this -> surface BRDF

const int PRIMARY_STEPS = 128;
const int SHADOW_STEPS = 48;
const int MAX_BOUNCES = 4;
const float PI = 3.14159265359;

bool intersectBox(vec3 origin, vec3 dir, out float tNear, out float tFar) {
    vec3 invDir = 1.0 / dir;
    vec3 t1v = (uClipMin - origin) * invDir;
    vec3 t2v = (uClipMax - origin) * invDir;
    vec3 tMin = min(t1v, t2v);
    vec3 tMax = max(t1v, t2v);
    tNear = max(max(tMin.x, tMin.y), tMin.z);
    tFar = min(min(tMax.x, tMax.y), tMax.z);
    return tNear <= tFar && tFar > 0.0;
}

// Opacity + albedo at a given local-space position (same window/level + LUT mapping as volume.frag).
float sampleAlpha(vec3 localPos, out vec3 albedo) {
    vec3 texCoord = localPos + 0.5;
    float rawVal = texture(volumeTex, texCoord).r;
    float val = (rawVal - uMin) / (uMax - uMin);
    float winMin = uWinCenter - (uWinWidth * 0.5);
    val = clamp((val - winMin) / uWinWidth, 0.0, 1.0);
    vec4 src = texture(uLutTex, val);
    albedo = src.rgb;
    return src.a;
}

// Alpha only - used for gradient computation to avoid constructing a dummy albedo.
float sampleAlphaOnly(vec3 localPos) {
    vec3 texCoord = localPos + 0.5;
    float rawVal = texture(volumeTex, texCoord).r;
    float val = (rawVal - uMin) / (uMax - uMin);
    float winMin = uWinCenter - (uWinWidth * 0.5);
    val = clamp((val - winMin) / uWinWidth, 0.0, 1.0);
    return texture(uLutTex, val).a;
}

// Central-difference gradient of the opacity field in local cube space.
// Points from low-opacity toward high-opacity (i.e. inward normal).
vec3 computeGradient(vec3 pos) {
    const float eps = 0.005;
    float dx = sampleAlphaOnly(pos + vec3(eps, 0.0, 0.0)) - sampleAlphaOnly(pos - vec3(eps, 0.0, 0.0));
    float dy = sampleAlphaOnly(pos + vec3(0.0, eps, 0.0)) - sampleAlphaOnly(pos - vec3(0.0, eps, 0.0));
    float dz = sampleAlphaOnly(pos + vec3(0.0, 0.0, eps)) - sampleAlphaOnly(pos - vec3(0.0, 0.0, eps));
    return vec3(dx, dy, dz);
}

// ----- RNG: PCG-like hash chain, seeded per pixel+frame+sample. -----
uint rngState;

uint pcgHash(uint x) {
    x = x * 747796405u + 2891336453u;
    uint word = ((x >> ((x >> 28u) + 4u)) ^ x) * 277803737u;
    return (word >> 22u) ^ word;
}

float rand() {
    rngState = pcgHash(rngState);
    return float(rngState) * (1.0 / 4294967296.0);
}

vec3 randomUnitVector() {
    float z = rand() * 2.0 - 1.0;
    float a = rand() * 2.0 * PI;
    float r = sqrt(max(0.0, 1.0 - z * z));
    return vec3(r * cos(a), r * sin(a), z);
}

// Henyey-Greenstein phase function.
float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    float denom = 1.0 + g2 - 2.0 * g * cosTheta;
    return (1.0 - g2) / (4.0 * PI * denom * sqrt(max(denom, 1e-4)));
}

// Importance-samples a new direction around `forward` per the HG distribution.
vec3 sampleHG(vec3 forward, float g) {
    float cosTheta;
    if (abs(g) < 1e-3) {
        cosTheta = 1.0 - 2.0 * rand();
    } else {
        float sq = (1.0 - g * g) / (1.0 + g - 2.0 * g * rand());
        cosTheta = (1.0 + g * g - sq * sq) / (2.0 * g);
    }
    float sinTheta = sqrt(max(0.0, 1.0 - cosTheta * cosTheta));
    float phi = 2.0 * PI * rand();
    vec3 up = abs(forward.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, forward));
    vec3 bitangent = cross(forward, tangent);
    return tangent * (sinTheta * cos(phi)) + bitangent * (sinTheta * sin(phi)) + forward * cosTheta;
}

// Jitters uLightDir within a small cone to simulate a soft area light.
vec3 jitteredLightDir() {
    if (uLightAngularRadius <= 0.0) return uLightDir;
    float r = uLightAngularRadius * sqrt(rand());
    float phi = 2.0 * PI * rand();
    vec3 up = abs(uLightDir.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, uLightDir));
    vec3 bitangent = cross(uLightDir, tangent);
    return normalize(uLightDir + tangent * (r * cos(phi)) + bitangent * (r * sin(phi)));
}

// Beer-Lambert transmittance toward the (area-)light from `origin`.
float shadowTransmittance(vec3 origin) {
    vec3 lightDir = jitteredLightDir();
    float tNear, tFar;
    if (!intersectBox(origin, lightDir, tNear, tFar)) return 1.0;
    tNear = max(tNear, 0.0);
    float dist = tFar - tNear;
    if (dist <= 0.0001) return 1.0;
    float stepSize = dist / float(SHADOW_STEPS);
    vec3 pos = origin + lightDir * tNear;
    float opticalDepth = 0.0;
    for (int i = 0; i < SHADOW_STEPS; i++) {
        vec3 albedo;
        float alpha = sampleAlpha(pos, albedo);
        opticalDepth += -log(max(1.0 - alpha, 1e-4));
        pos += lightDir * stepSize;
    }
    return exp(-opticalDepth);
}

// ----- PBR / GGX BRDF -----

// GGX / Trowbridge-Reitz Normal Distribution Function.
float D_GGX(float NoH, float roughness) {
    float a2 = roughness * roughness * roughness * roughness; // (alpha^2, alpha = roughness^2)
    float d = (NoH * NoH) * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d + 1e-7);
}

// Smith-Schlick Geometry Attenuation.
float G_Smith(float NoL, float NoV, float roughness) {
    float a2 = roughness * roughness * roughness * roughness;
    float k = a2 * 0.5; // Schlick remapping for direct lighting
    float gL = NoL / (NoL * (1.0 - k) + k + 1e-5);
    float gV = NoV / (NoV * (1.0 - k) + k + 1e-5);
    return gL * gV;
}

// Schlick Fresnel approximation.
vec3 F_Schlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(max(1.0 - cosTheta, 0.0), 5.0);
}

// Cook-Torrance specular BRDF value for a given N, V, L, roughness, and F0.
vec3 cookTorranceSpecular(vec3 N, vec3 V, vec3 L, float roughness, vec3 F0) {
    vec3 H = normalize(V + L);
    float NoH = max(dot(N, H), 0.0);
    float NoV = max(dot(N, V), 1e-4);
    float NoL = max(dot(N, L), 0.0);
    float VoH = max(dot(V, H), 0.0);
    float D = D_GGX(NoH, roughness);
    float G = G_Smith(NoL, NoV, roughness);
    vec3 F = F_Schlick(VoH, F0);
    return D * G * F / (4.0 * NoV * NoL + 1e-5);
}

// GGX-importance-sampled reflection direction around N for incoming V.
// roughness=0 -> pure mirror; roughness=1 -> near-Lambertian specular lobe.
vec3 sampleGGX(vec3 V, vec3 N, float roughness) {
    float a2 = roughness * roughness * roughness * roughness;
    float r0 = rand();
    float r1 = rand();
    // GGX CDF inversion for NoH^2
    float cosTheta2 = (1.0 - r0) / (r0 * (a2 - 1.0) + 1.0);
    float cosTheta = sqrt(max(cosTheta2, 0.0));
    float sinTheta = sqrt(max(1.0 - cosTheta2, 0.0));
    float phi = 2.0 * PI * r1;
    // Half-vector in tangent space, then rotate to world space around N
    vec3 up = abs(N.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 T = normalize(cross(up, N));
    vec3 B = cross(N, T);
    vec3 H = normalize(T * (sinTheta * cos(phi)) + B * (sinTheta * sin(phi)) + N * cosTheta);
    // Reflect -V (incident) around H to get outgoing scattered direction
    return reflect(-V, H);
}

// Traces one full stochastic path and returns its radiance estimate.
vec3 tracePath(vec3 rayOrigin, vec3 rayDir) {
    vec3 radiance = vec3(0.0);
    vec3 throughput = vec3(1.0);

    for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
        float tNear, tFar;
        if (!intersectBox(rayOrigin, rayDir, tNear, tFar)) break;
        tNear = max(tNear, 0.0);
        float dist = tFar - tNear;
        if (dist <= 0.0001) break;

        float stepSize = dist / float(PRIMARY_STEPS);
        // Jitter the start to avoid banding
        vec3 pos = rayOrigin + rayDir * (tNear + rand() * stepSize);

        bool hit = false;
        vec3 hitAlbedo = vec3(0.0);
        for (int i = 0; i < PRIMARY_STEPS; i++) {
            vec3 albedo;
            float alpha = sampleAlpha(pos, albedo);
            if (rand() < alpha) {
                hit = true;
                hitAlbedo = albedo;
                break;
            }
            pos += rayDir * stepSize;
        }
        if (!hit) break;

        float visibility = shadowTransmittance(pos);

        // Gradient magnitude determines surface vs. volume scattering mode
        vec3 gradient = computeGradient(pos);
        float gradMag = length(gradient);

        if (gradMag >= uSurfaceGradientThreshold) {
            // ---- Surface BRDF mode (Cook-Torrance GGX + clearcoat) ----

            // Outward surface normal: gradient points inward (low->high alpha), so flip it.
            vec3 N = normalize(-gradient);
            // Ensure N faces the incoming ray (handles back-face hits at grazing angles)
            if (dot(N, -rayDir) < 0.0) N = -N;

            vec3 V = -rayDir;
            vec3 L = uLightDir;
            float NoL = max(dot(N, L), 0.0);
            float NoV = max(dot(N, V), 1e-4);

            // Base reflectance: dielectric uses 0.04*specular; metallic uses albedo color
            vec3 F0 = mix(vec3(0.04 * uSpecular), hitAlbedo, uMetallic);
            vec3 Fv = F_Schlick(NoV, F0);
            float kS_scalar = clamp(max(Fv.r, max(Fv.g, Fv.b)), 0.0, 1.0);
            float kD = (1.0 - kS_scalar) * (1.0 - uMetallic);

            // Direct lighting components
            vec3 diffuse  = kD * hitAlbedo / PI;
            vec3 spec     = cookTorranceSpecular(N, V, L, max(uRoughness, 0.01), F0);
            vec3 ccSpec   = uClearcoat * cookTorranceSpecular(N, V, L, max(uClearcoatRoughness, 0.01), vec3(0.04));

            vec3 direct = (diffuse + spec + ccSpec) * uLightIntensity * visibility * NoL
                        + hitAlbedo * uAmbientIntensity;
            radiance += throughput * direct;

            // --- Next bounce: importance sample between GGX specular and diffuse ---
            float specProb = clamp(kS_scalar, 0.1, 0.9);
            if (rand() < specProb) {
                // Specular bounce
                vec3 nextDir = sampleGGX(V, N, max(uRoughness, 0.01));
                if (dot(nextDir, N) <= 0.0) break;
                throughput *= F_Schlick(max(dot(N, nextDir), 0.0), F0) / specProb;
                rayDir = nextDir;
            } else {
                // Diffuse bounce (cosine-weighted hemisphere)
                vec3 nextDir = normalize(N + randomUnitVector());
                if (dot(nextDir, N) <= 0.0) break;
                throughput *= hitAlbedo / (1.0 - specProb);
                rayDir = nextDir;
            }

        } else {
            // ---- Volume HG scattering mode (existing path tracer logic) ----

            float cosTheta = dot(rayDir, uLightDir);
            float phase = phaseHG(cosTheta, uAnisotropy);
            vec3 direct = hitAlbedo * (uLightIntensity * visibility * phase * 4.0 * PI + uAmbientIntensity);
            radiance += throughput * direct;

            // Russian roulette to keep the estimator unbiased while bounding cost
            if (bounce >= 1) {
                float continueProb = clamp(max(hitAlbedo.r, max(hitAlbedo.g, hitAlbedo.b)), 0.05, 0.95);
                if (rand() > continueProb) break;
                throughput /= continueProb;
            }
            throughput *= hitAlbedo;
            rayDir = sampleHG(rayDir, uAnisotropy);
        }

        rayOrigin = pos;
    }

    return radiance;
}

void main() {
    vec3 primaryDir = normalize(vPos - cameraPos);

    uvec2 px = uvec2(gl_FragCoord.xy);
    uint pixelSeed = px.x * 1973u + px.y * 9277u + uFrameSeed * 26699u;

    vec3 accumulated = vec3(0.0);
    int samples = max(1, uSamplesPerFrame);
    for (int s = 0; s < samples; s++) {
        rngState = pixelSeed ^ (uint(s) * 374761393u);
        accumulated += tracePath(cameraPos, primaryDir);
    }
    accumulated /= float(samples);

    FragColor = vec4(accumulated, 1.0);
}
