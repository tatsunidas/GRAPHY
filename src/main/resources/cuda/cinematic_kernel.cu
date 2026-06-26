// © Visionary Imaging Services, Inc.
//
// CUDA C counterpart of shaders/cinematic.frag - the same Monte Carlo
// volumetric path tracer (box intersection, stochastic free-flight sampling
// of the opacity curve, Henyey-Greenstein phase function, soft shadow ray,
// Russian roulette multi-bounce), written from scratch for the GPU compute
// path instead of the GLSL fragment-shader path. Compiled at runtime via
// NVRTC (see CinematicRendererCuda), so this file is never touched by nvcc
// at build time.
//
// Surface BRDF: when the opacity gradient at a hit point exceeds
// surfaceGradientThreshold, the point is treated as a tissue boundary and
// shaded with a Cook-Torrance GGX BRDF (diffuse + specular + optional
// clearcoat) instead of the Henyey-Greenstein phase function.
//
// Unlike the GLSL version (which relies on rasterizing a cube to get a
// per-pixel entry point via vPos), CUDA has no rasterizer: each thread
// reconstructs its own primary ray by unprojecting its pixel through the
// inverse of the same mvp matrix the GL path uses.

// 意図的にヘッダを一切includeしない: cudaTextureObject_t/cudaSurfaceObject_t/float4や
// tex1D/tex3D/surf2Dread/surf2Dwrite、threadIdx/blockIdx、sqrtf等の数学組み込み関数は
// すべてCUDA Cデバイス言語のコア組み込みであり、cuda_runtime.h等のホスト向けヘッダを
// NVRTCに読ませるとヘッダ依存で失敗するリスクがある。

#define PRIMARY_STEPS 128
#define SHADOW_STEPS 48
#define MAX_BOUNCES 4
#define PI 3.14159265359f

struct Vec3 {
	float x, y, z;
};

__device__ inline Vec3 vec3(float x, float y, float z) {
	Vec3 v; v.x = x; v.y = y; v.z = z; return v;
}

__device__ inline Vec3 add(Vec3 a, Vec3 b) { return vec3(a.x+b.x, a.y+b.y, a.z+b.z); }
__device__ inline Vec3 sub(Vec3 a, Vec3 b) { return vec3(a.x-b.x, a.y-b.y, a.z-b.z); }
__device__ inline Vec3 mulf(Vec3 a, float s) { return vec3(a.x*s, a.y*s, a.z*s); }
__device__ inline Vec3 mulv(Vec3 a, Vec3 b) { return vec3(a.x*b.x, a.y*b.y, a.z*b.z); }
__device__ inline Vec3 divv(Vec3 a, Vec3 b) { return vec3(a.x/b.x, a.y/b.y, a.z/b.z); }
__device__ inline Vec3 addscalar(Vec3 a, float s) { return vec3(a.x+s, a.y+s, a.z+s); }
__device__ inline float dot3(Vec3 a, Vec3 b) { return a.x*b.x + a.y*b.y + a.z*b.z; }
__device__ inline Vec3 cross3(Vec3 a, Vec3 b) {
	return vec3(a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x);
}
__device__ inline float length3(Vec3 a) { return sqrtf(dot3(a,a)); }
__device__ inline Vec3 normalize3(Vec3 a) {
	float len = length3(a);
	return len > 1e-12f ? mulf(a, 1.0f/len) : a;
}
__device__ inline float clampf(float v, float lo, float hi) { return fmaxf(lo, fminf(hi, v)); }
__device__ inline float maxcomp(Vec3 v) { return fmaxf(v.x, fmaxf(v.y, v.z)); }
// reflect(-V, H): reflect incident direction around half-vector (GLSL reflect convention)
__device__ inline Vec3 reflect3(Vec3 I, Vec3 N) {
	return sub(I, mulf(N, 2.0f * dot3(N, I)));
}

// ----- RNG: same PCG-hash chain as cinematic.frag -----
struct Rng { unsigned int state; };

__device__ inline unsigned int pcgHash(unsigned int x) {
	x = x * 747796405u + 2891336453u;
	unsigned int word = ((x >> ((x >> 28u) + 4u)) ^ x) * 277803737u;
	return (word >> 22u) ^ word;
}

__device__ inline float rngNext(Rng *rng) {
	rng->state = pcgHash(rng->state);
	return (float)rng->state * (1.0f / 4294967296.0f);
}

__device__ inline Vec3 randomUnitVector3(Rng *rng) {
	float z = rngNext(rng) * 2.0f - 1.0f;
	float a = rngNext(rng) * 2.0f * PI;
	float r = sqrtf(fmaxf(1.0f - z*z, 0.0f));
	return vec3(r*cosf(a), r*sinf(a), z);
}

// ----- Volume sampling -----

__device__ inline float sampleAlphaCu(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex, Vec3 localPos,
		float uMin, float uMax, float uWinCenter, float uWinWidth, Vec3 *albedo) {
	float u = localPos.x + 0.5f, v = localPos.y + 0.5f, w = localPos.z + 0.5f;
	float rawVal = tex3D<float>(volumeTex, u, v, w);
	float val = (rawVal - uMin) / (uMax - uMin);
	float winMin = uWinCenter - uWinWidth * 0.5f;
	val = clampf((val - winMin) / uWinWidth, 0.0f, 1.0f);
	float4 src = tex1D<float4>(lutTex, val);
	albedo->x = src.x; albedo->y = src.y; albedo->z = src.z;
	return src.w;
}

__device__ inline float sampleAlphaOnlyCu(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex, Vec3 localPos,
		float uMin, float uMax, float uWinCenter, float uWinWidth) {
	Vec3 albedo;
	return sampleAlphaCu(volumeTex, lutTex, localPos, uMin, uMax, uWinCenter, uWinWidth, &albedo);
}

// Central-difference gradient of the opacity field (same eps as cinematic.frag).
__device__ inline Vec3 computeGradientCu(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex,
		Vec3 pos, float uMin, float uMax, float uWinCenter, float uWinWidth) {
	const float eps = 0.005f;
	float dx = sampleAlphaOnlyCu(volumeTex, lutTex, vec3(pos.x+eps, pos.y, pos.z), uMin, uMax, uWinCenter, uWinWidth)
	         - sampleAlphaOnlyCu(volumeTex, lutTex, vec3(pos.x-eps, pos.y, pos.z), uMin, uMax, uWinCenter, uWinWidth);
	float dy = sampleAlphaOnlyCu(volumeTex, lutTex, vec3(pos.x, pos.y+eps, pos.z), uMin, uMax, uWinCenter, uWinWidth)
	         - sampleAlphaOnlyCu(volumeTex, lutTex, vec3(pos.x, pos.y-eps, pos.z), uMin, uMax, uWinCenter, uWinWidth);
	float dz = sampleAlphaOnlyCu(volumeTex, lutTex, vec3(pos.x, pos.y, pos.z+eps), uMin, uMax, uWinCenter, uWinWidth)
	         - sampleAlphaOnlyCu(volumeTex, lutTex, vec3(pos.x, pos.y, pos.z-eps), uMin, uMax, uWinCenter, uWinWidth);
	return vec3(dx, dy, dz);
}

// ----- Henyey-Greenstein phase function -----

__device__ inline float phaseHG(float cosTheta, float g) {
	float g2 = g*g;
	float denom = fmaxf(1.0f + g2 - 2.0f*g*cosTheta, 1e-4f);
	return (1.0f - g2) / (4.0f * PI * denom * sqrtf(denom));
}

__device__ inline Vec3 sampleHGCu(Vec3 forward, float g, Rng *rng) {
	float cosTheta;
	if (fabsf(g) < 1e-3f) {
		cosTheta = 1.0f - 2.0f * rngNext(rng);
	} else {
		float sq = (1.0f - g*g) / (1.0f + g - 2.0f*g*rngNext(rng));
		cosTheta = (1.0f + g*g - sq*sq) / (2.0f*g);
	}
	float sinTheta = sqrtf(fmaxf(0.0f, 1.0f - cosTheta*cosTheta));
	float phi = 2.0f * PI * rngNext(rng);
	Vec3 up = fabsf(forward.z) < 0.999f ? vec3(0.0f,0.0f,1.0f) : vec3(1.0f,0.0f,0.0f);
	Vec3 tangent = normalize3(cross3(up, forward));
	Vec3 bitangent = cross3(forward, tangent);
	return add(add(mulf(tangent, sinTheta*cosf(phi)), mulf(bitangent, sinTheta*sinf(phi))), mulf(forward, cosTheta));
}

__device__ inline Vec3 jitteredLightDirCu(Vec3 lightDir, float angularRadius, Rng *rng) {
	if (angularRadius <= 0.0f) return lightDir;
	float r = angularRadius * sqrtf(rngNext(rng));
	float phi = 2.0f * PI * rngNext(rng);
	Vec3 up = fabsf(lightDir.z) < 0.999f ? vec3(0.0f,0.0f,1.0f) : vec3(1.0f,0.0f,0.0f);
	Vec3 tangent = normalize3(cross3(up, lightDir));
	Vec3 bitangent = cross3(lightDir, tangent);
	return normalize3(add(lightDir, add(mulf(tangent, r*cosf(phi)), mulf(bitangent, r*sinf(phi)))));
}

// ----- Box intersection (clip-space aware) -----

__device__ inline bool intersectBox(Vec3 origin, Vec3 dir, Vec3 clipMin, Vec3 clipMax,
		float *tNear, float *tFar) {
	float invX=1.0f/dir.x, invY=1.0f/dir.y, invZ=1.0f/dir.z;
	float t1x=(clipMin.x-origin.x)*invX, t2x=(clipMax.x-origin.x)*invX;
	float t1y=(clipMin.y-origin.y)*invY, t2y=(clipMax.y-origin.y)*invY;
	float t1z=(clipMin.z-origin.z)*invZ, t2z=(clipMax.z-origin.z)*invZ;
	*tNear = fmaxf(fmaxf(fminf(t1x,t2x), fminf(t1y,t2y)), fminf(t1z,t2z));
	*tFar  = fminf(fminf(fmaxf(t1x,t2x), fmaxf(t1y,t2y)), fmaxf(t1z,t2z));
	return *tNear <= *tFar && *tFar > 0.0f;
}

// ----- Shadow transmittance -----

__device__ inline float shadowTransmittanceCu(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex,
		Vec3 origin, Vec3 lightDir, float angularRadius,
		float uMin, float uMax, float uWinCenter, float uWinWidth,
		Vec3 clipMin, Vec3 clipMax, Rng *rng) {
	Vec3 jld = jitteredLightDirCu(lightDir, angularRadius, rng);
	float tNear, tFar;
	if (!intersectBox(origin, jld, clipMin, clipMax, &tNear, &tFar)) return 1.0f;
	tNear = fmaxf(tNear, 0.0f);
	float dist = tFar - tNear;
	if (dist <= 0.0001f) return 1.0f;
	float stepSize = dist / (float)SHADOW_STEPS;
	Vec3 pos = add(origin, mulf(jld, tNear));
	float opticalDepth = 0.0f;
	for (int i = 0; i < SHADOW_STEPS; i++) {
		Vec3 albedo;
		float alpha = sampleAlphaCu(volumeTex, lutTex, pos, uMin, uMax, uWinCenter, uWinWidth, &albedo);
		opticalDepth += -logf(fmaxf(1.0f - alpha, 1e-4f));
		pos = add(pos, mulf(jld, stepSize));
	}
	return expf(-opticalDepth);
}

// ----- PBR / GGX BRDF -----

__device__ inline float D_GGX_Cu(float NoH, float roughness) {
	float a2 = roughness*roughness*roughness*roughness;
	float d = (NoH*NoH)*(a2-1.0f)+1.0f;
	return a2 / (PI*d*d + 1e-7f);
}

__device__ inline float G_Smith_Cu(float NoL, float NoV, float roughness) {
	float a2 = roughness*roughness*roughness*roughness;
	float k = a2*0.5f;
	float gL = NoL / (NoL*(1.0f-k)+k+1e-5f);
	float gV = NoV / (NoV*(1.0f-k)+k+1e-5f);
	return gL*gV;
}

__device__ inline Vec3 F_Schlick_Cu(float cosTheta, Vec3 F0) {
	float t = powf(fmaxf(1.0f-cosTheta, 0.0f), 5.0f);
	return add(F0, mulf(sub(vec3(1.0f,1.0f,1.0f), F0), t));
}

__device__ inline Vec3 cookTorranceSpecularCu(Vec3 N, Vec3 V, Vec3 L, float roughness, Vec3 F0) {
	Vec3 H = normalize3(add(V, L));
	float NoH = fmaxf(dot3(N,H), 0.0f);
	float NoV = fmaxf(dot3(N,V), 1e-4f);
	float NoL = fmaxf(dot3(N,L), 0.0f);
	float VoH = fmaxf(dot3(V,H), 0.0f);
	float D = D_GGX_Cu(NoH, roughness);
	float G = G_Smith_Cu(NoL, NoV, roughness);
	Vec3 F = F_Schlick_Cu(VoH, F0);
	return mulf(F, D*G / (4.0f*NoV*NoL + 1.0e-5f));
}

// GGX-importance-sampled reflection direction around N for incoming V.
__device__ inline Vec3 sampleGGX_Cu(Vec3 V, Vec3 N, float roughness, Rng *rng) {
	float a2 = roughness*roughness*roughness*roughness;
	float r0 = rngNext(rng), r1 = rngNext(rng);
	float cosTheta2 = (1.0f - r0) / (r0*(a2-1.0f)+1.0f);
	float cosTheta = sqrtf(fmaxf(cosTheta2, 0.0f));
	float sinTheta = sqrtf(fmaxf(1.0f - cosTheta2, 0.0f));
	float phi = 2.0f * PI * r1;
	Vec3 up = fabsf(N.z) < 0.999f ? vec3(0.0f,0.0f,1.0f) : vec3(1.0f,0.0f,0.0f);
	Vec3 T = normalize3(cross3(up, N));
	Vec3 B = cross3(N, T);
	Vec3 H = normalize3(add(add(mulf(T, sinTheta*cosf(phi)), mulf(B, sinTheta*sinf(phi))), mulf(N, cosTheta)));
	// reflect(-V, H) to get outgoing ray direction
	Vec3 negV = mulf(V, -1.0f);
	return reflect3(negV, H);
}

// ----- Main path tracer -----

__device__ inline Vec3 tracePath(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex,
		Vec3 rayOrigin, Vec3 rayDir,
		Vec3 lightDir, float lightIntensity, float ambientIntensity, float anisotropy, float lightAngularRadius,
		float uMin, float uMax, float uWinCenter, float uWinWidth,
		Vec3 clipMin, Vec3 clipMax,
		float roughness, float specular, float metallic, float clearcoat, float clearcoatRoughness,
		float surfaceGradientThreshold,
		Rng *rng) {

	Vec3 radiance = vec3(0.0f, 0.0f, 0.0f);
	Vec3 throughput = vec3(1.0f, 1.0f, 1.0f);

	for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
		float tNear, tFar;
		if (!intersectBox(rayOrigin, rayDir, clipMin, clipMax, &tNear, &tFar)) break;
		tNear = fmaxf(tNear, 0.0f);
		float dist = tFar - tNear;
		if (dist <= 0.0001f) break;

		float stepSize = dist / (float)PRIMARY_STEPS;
		Vec3 pos = add(rayOrigin, mulf(rayDir, tNear + rngNext(rng)*stepSize));

		bool hit = false;
		Vec3 hitAlbedo = vec3(0.0f, 0.0f, 0.0f);
		for (int i = 0; i < PRIMARY_STEPS; i++) {
			Vec3 albedo;
			float alpha = sampleAlphaCu(volumeTex, lutTex, pos, uMin, uMax, uWinCenter, uWinWidth, &albedo);
			if (rngNext(rng) < alpha) {
				hit = true;
				hitAlbedo = albedo;
				break;
			}
			pos = add(pos, mulf(rayDir, stepSize));
		}
		if (!hit) break;

		float visibility = shadowTransmittanceCu(volumeTex, lutTex, pos, lightDir, lightAngularRadius,
				uMin, uMax, uWinCenter, uWinWidth, clipMin, clipMax, rng);

		Vec3 gradient = computeGradientCu(volumeTex, lutTex, pos, uMin, uMax, uWinCenter, uWinWidth);
		float gradMag = length3(gradient);

		if (gradMag >= surfaceGradientThreshold) {
			// ---- Surface BRDF mode ----
			Vec3 N = normalize3(mulf(gradient, -1.0f));
			// Ensure N faces the incoming ray
			if (dot3(N, mulf(rayDir, -1.0f)) < 0.0f) N = mulf(N, -1.0f);

			Vec3 V = mulf(rayDir, -1.0f);
			Vec3 L = lightDir;
			float NoL = fmaxf(dot3(N, L), 0.0f);
			float NoV = fmaxf(dot3(N, V), 1e-4f);

			// F0: dielectric = 0.04*specular, metallic = albedo color
			Vec3 F0 = add(mulf(vec3(0.04f,0.04f,0.04f), specular*(1.0f-metallic)),
			              mulf(hitAlbedo, metallic));
			Vec3 Fv = F_Schlick_Cu(NoV, F0);
			float kS_scalar = clampf(maxcomp(Fv), 0.0f, 1.0f);
			float kD = (1.0f - kS_scalar) * (1.0f - metallic);

			// Direct lighting
			Vec3 diffuse = mulf(hitAlbedo, kD / PI);
			Vec3 spec    = cookTorranceSpecularCu(N, V, L, fmaxf(roughness, 0.01f), F0);
			Vec3 ccSpec  = mulf(cookTorranceSpecularCu(N, V, L, fmaxf(clearcoatRoughness, 0.01f),
			                                           vec3(0.04f,0.04f,0.04f)), clearcoat);

			Vec3 direct = mulf(add(add(diffuse, spec), ccSpec), lightIntensity * visibility * NoL);
			direct = add(direct, mulf(hitAlbedo, ambientIntensity));
			radiance = add(radiance, mulv(throughput, direct));

			// Next bounce: specular vs diffuse
			float specProb = clampf(kS_scalar, 0.1f, 0.9f);
			if (rngNext(rng) < specProb) {
				Vec3 nextDir = sampleGGX_Cu(V, N, fmaxf(roughness, 0.01f), rng);
				if (dot3(nextDir, N) <= 0.0f) break;
				Vec3 Fn = F_Schlick_Cu(fmaxf(dot3(N, nextDir), 0.0f), F0);
				throughput = mulv(throughput, mulf(Fn, 1.0f / specProb));
				rayDir = nextDir;
			} else {
				Vec3 rVec = randomUnitVector3(rng);
				Vec3 nextDir = normalize3(add(N, rVec));
				if (dot3(nextDir, N) <= 0.0f) break;
				throughput = mulv(throughput, mulf(hitAlbedo, 1.0f / (1.0f - specProb)));
				rayDir = nextDir;
			}

		} else {
			// ---- Volume HG scattering mode ----
			float cosTheta = dot3(rayDir, lightDir);
			float phase = phaseHG(cosTheta, anisotropy);
			Vec3 direct = mulf(hitAlbedo, lightIntensity * visibility * phase * 4.0f * PI + ambientIntensity);
			radiance = add(radiance, mulv(throughput, direct));

			if (bounce >= 1) {
				float continueProb = clampf(maxcomp(hitAlbedo), 0.05f, 0.95f);
				if (rngNext(rng) > continueProb) break;
				throughput = mulf(throughput, 1.0f / continueProb);
			}
			throughput = mulv(throughput, hitAlbedo);
			rayDir = sampleHGCu(rayDir, anisotropy, rng);
		}

		rayOrigin = pos;
	}

	return radiance;
}

// Unprojects pixel through invMvp (JOML column-major layout) to a near-clip point in local cube space.
__device__ inline Vec3 unprojectNear(const float *invMvp, float ndcX, float ndcY) {
	float cx[4] = { ndcX, ndcY, -1.0f, 1.0f };
	float out[4];
	for (int row = 0; row < 4; row++) {
		out[row] = invMvp[row+0]*cx[0] + invMvp[row+4]*cx[1] + invMvp[row+8]*cx[2] + invMvp[row+12]*cx[3];
	}
	float invW = 1.0f / out[3];
	return vec3(out[0]*invW, out[1]*invW, out[2]*invW);
}

extern "C" __global__ void pathTraceKernel(
		cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex, cudaSurfaceObject_t accumSurf,
		int width, int height, const float *invMvp,
		float camX, float camY, float camZ,
		float uMin, float uMax, float uWinCenter, float uWinWidth,
		float lightDirX, float lightDirY, float lightDirZ,
		float lightIntensity, float ambientIntensity, float anisotropy, float lightAngularRadius,
		int samplesPerFrame, unsigned int frameSeed,
		float clipMinX, float clipMinY, float clipMinZ,
		float clipMaxX, float clipMaxY, float clipMaxZ,
		float roughness, float specular, float metallic,
		float clearcoat, float clearcoatRoughness, float surfaceGradientThreshold) {

	int x = blockIdx.x * blockDim.x + threadIdx.x;
	int y = blockIdx.y * blockDim.y + threadIdx.y;
	if (x >= width || y >= height) return;

	float ndcX = ((float)x + 0.5f) / (float)width  * 2.0f - 1.0f;
	float ndcY = ((float)y + 0.5f) / (float)height * 2.0f - 1.0f;

	Vec3 camPos    = vec3(camX, camY, camZ);
	Vec3 nearPoint = unprojectNear(invMvp, ndcX, ndcY);
	Vec3 primaryDir = normalize3(sub(nearPoint, camPos));
	Vec3 lightDir  = vec3(lightDirX, lightDirY, lightDirZ);
	Vec3 clipMin   = vec3(clipMinX, clipMinY, clipMinZ);
	Vec3 clipMax   = vec3(clipMaxX, clipMaxY, clipMaxZ);

	unsigned int pixelSeed = (unsigned int)x * 1973u + (unsigned int)y * 9277u + frameSeed * 26699u;

	Vec3 accumulated = vec3(0.0f, 0.0f, 0.0f);
	int samples = samplesPerFrame > 0 ? samplesPerFrame : 1;
	for (int s = 0; s < samples; s++) {
		Rng rng;
		rng.state = pixelSeed ^ ((unsigned int)s * 374761393u);
		Vec3 sample = tracePath(volumeTex, lutTex, camPos, primaryDir,
				lightDir, lightIntensity, ambientIntensity, anisotropy, lightAngularRadius,
				uMin, uMax, uWinCenter, uWinWidth, clipMin, clipMax,
				roughness, specular, metallic, clearcoat, clearcoatRoughness, surfaceGradientThreshold,
				&rng);
		accumulated = add(accumulated, sample);
	}
	accumulated = mulf(accumulated, 1.0f / (float)samples);

	int xByteOffset = x * (int)sizeof(float4);
	float4 prev = surf2Dread<float4>(accumSurf, xByteOffset, y);
	float4 next;
	next.x = prev.x + accumulated.x;
	next.y = prev.y + accumulated.y;
	next.z = prev.z + accumulated.z;
	next.w = 1.0f;
	surf2Dwrite<float4>(next, accumSurf, xByteOffset, y);
}
