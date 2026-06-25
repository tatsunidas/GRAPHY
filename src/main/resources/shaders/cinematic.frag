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
// Outputs ONE accumulated-radiance sample per invocation (averaged over
// uSamplesPerFrame internal samples); CinematicRendererGL adds this into a
// floating-point accumulation buffer with GL_ONE/GL_ONE blending across
// frames, and a separate present pass divides by the frame count and
// tonemaps for display. This is what lets the image keep getting less noisy
// while the camera/transfer-function/lighting stay still.

in vec3 vPos;
out vec4 FragColor;

uniform sampler3D volumeTex;
uniform sampler1D uLutTex;

uniform vec3 cameraPos;
uniform float uMin;
uniform float uMax;
uniform float uWinCenter;
uniform float uWinWidth;

uniform vec3 uLightDir;          // unit vector, points FROM the scene TOWARD the light
uniform float uLightIntensity;
uniform float uAmbientIntensity;
uniform float uAnisotropy;       // Henyey-Greenstein g, in (-1, 1)
uniform float uLightAngularRadius; // area-light cone half-angle (radians) the shadow ray is jittered within
uniform int uSamplesPerFrame;
uniform uint uFrameSeed;

// 3D裁断（クリッピング）領域。volume.fragと同じ規約（ローカル単位立方体 -0.5〜0.5）。
// 裁断OFF時/最大時は (-0.5, 0.5) が渡され、立方体全体＝裁断なしと等価になる。
uniform vec3 uClipMin;
uniform vec3 uClipMax;

const int PRIMARY_STEPS = 128;
const int SHADOW_STEPS = 48;
const int MAX_BOUNCES = 4;
const float PI = 3.14159265359;

bool intersectBox(vec3 origin, vec3 dir, out float tNear, out float tFar) {
    vec3 boxMin = uClipMin;
    vec3 boxMax = uClipMax;
    vec3 invDir = 1.0 / dir;
    vec3 t1v = (boxMin - origin) * invDir;
    vec3 t2v = (boxMax - origin) * invDir;
    vec3 tMin = min(t1v, t2v);
    vec3 tMax = max(t1v, t2v);
    tNear = max(max(tMin.x, tMin.y), tMin.z);
    tFar = min(min(tMax.x, tMax.y), tMax.z);
    return tNear <= tFar && tFar > 0.0;
}

// Opacity curve value (0..1) at a given local-space position, using the same
// window/level + LUT mapping volume.frag uses for its alpha channel.
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

// ----- RNG: a small public-domain-style hash chain (PCG-like mix), seeded per pixel+frame+sample. -----
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

// Henyey-Greenstein phase function value for cosine of the angle between
// the incoming and outgoing directions.
float phaseHG(float cosTheta, float g) {
    float g2 = g * g;
    float denom = 1.0 + g2 - 2.0 * g * cosTheta;
    return (1.0 - g2) / (4.0 * PI * denom * sqrt(max(denom, 1e-4)));
}

// Importance-samples a new direction around `forward` according to the HG
// phase function with asymmetry `g`.
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

// Perturbs uLightDir within a small random cone so the light behaves like a
// small area light instead of an infinitesimal point - point lights cast
// hard-edged shadows that, on a single-scattering volume, can look so close
// to a flat alpha blend that the renderer barely reads as "cinematic" at
// all. Soft, slightly-blurred shadows are what actually sells the look, and
// since this is resampled every accumulated frame, the penumbra
// anti-aliases for free as accumulation converges.
vec3 jitteredLightDir() {
    if (uLightAngularRadius <= 0.0) {
        return uLightDir;
    }
    float r = uLightAngularRadius * sqrt(rand());
    float phi = 2.0 * PI * rand();
    vec3 up = abs(uLightDir.z) < 0.999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(up, uLightDir));
    vec3 bitangent = cross(uLightDir, tangent);
    vec3 offset = tangent * (r * cos(phi)) + bitangent * (r * sin(phi));
    return normalize(uLightDir + offset);
}

// Beer-Lambert transmittance toward the (area-)light from `origin`,
// converting the opacity curve's alpha into a per-step extinction
// coefficient.
float shadowTransmittance(vec3 origin) {
    vec3 lightDir = jitteredLightDir();
    float tNear, tFar;
    if (!intersectBox(origin, lightDir, tNear, tFar)) {
        return 1.0;
    }
    tNear = max(tNear, 0.0);
    float dist = tFar - tNear;
    if (dist <= 0.0001) {
        return 1.0;
    }

    float stepSize = dist / float(SHADOW_STEPS);
    vec3 pos = origin + lightDir * tNear;
    float opticalDepth = 0.0;
    for (int i = 0; i < SHADOW_STEPS; i++) {
        vec3 albedo;
        float alpha = sampleAlpha(pos, albedo);
        opticalDepth += -log(max(1.0 - alpha, 1e-4)) ;
        pos += lightDir * stepSize;
    }
    return exp(-opticalDepth);
}

// Traces one full stochastic path (primary ray + scattering bounces) and
// returns its radiance estimate.
vec3 tracePath(vec3 rayOrigin, vec3 rayDir) {
    vec3 radiance = vec3(0.0);
    vec3 throughput = vec3(1.0);

    for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
        float tNear, tFar;
        if (!intersectBox(rayOrigin, rayDir, tNear, tFar)) {
            break;
        }
        tNear = max(tNear, 0.0);
        float dist = tFar - tNear;
        if (dist <= 0.0001) {
            break;
        }

        float stepSize = dist / float(PRIMARY_STEPS);
        // Jitter the start so a fixed step grid turns into noise instead of banding.
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

        if (!hit) {
            break; // ray escaped the volume without interacting - no more contribution this path
        }

        // Direct lighting at the interaction point (next-event estimation).
        float cosTheta = dot(rayDir, uLightDir);
        float phase = phaseHG(cosTheta, uAnisotropy);
        float visibility = shadowTransmittance(pos);
        vec3 direct = hitAlbedo * (uLightIntensity * visibility * phase * 4.0 * PI + uAmbientIntensity);
        radiance += throughput * direct;

        // Russian roulette after a couple of bounces to keep the estimator unbiased while bounding cost.
        if (bounce >= 1) {
            float continueProb = clamp(max(hitAlbedo.r, max(hitAlbedo.g, hitAlbedo.b)), 0.05, 0.95);
            if (rand() > continueProb) {
                break;
            }
            throughput /= continueProb;
        }

        throughput *= hitAlbedo;
        rayDir = sampleHG(rayDir, uAnisotropy);
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
