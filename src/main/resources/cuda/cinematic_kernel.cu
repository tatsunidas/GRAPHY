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
// Unlike the GLSL version (which relies on rasterizing a cube to get a
// per-pixel entry point via vPos), CUDA has no rasterizer: each thread
// reconstructs its own primary ray by unprojecting its pixel through the
// inverse of the same mvp matrix the GL path uses, which lands on the same
// near-clip-plane point as the rasterized vPos would - so the two paths
// produce equivalent rays for the same camera.
//
// Output is written into a 2D RGBA32F surface (the same kind of
// accumulation texture CinematicRendererGL uses), accumulated with a plain
// read-modify-write add. CinematicRendererCuda clears it the same way the GL
// path does (a normal glClear through the GL texture) before accumulation
// restarts, and divides/tonemaps it for display with the same present
// shader, so both backends share the exact same display pipeline and only
// differ in how the radiance gets computed.

// 意図的にヘッダを一切includeしない: cudaTextureObject_t/cudaSurfaceObject_t/float4や
// tex1D/tex3D/surf2Dread/surf2Dwrite、threadIdx/blockIdx、sqrtf等の数学組み込み関数は
// すべてCUDA Cデバイス言語のコア組み込み（NVRTCがホスト用ツールキットのヘッダ無しでも
// 認識する）であり、cuda_runtime.h等のホスト向けヘッダをNVRTCに読ませると、むしろ
// 解決できない依存ヘッダで失敗するリスクがある。

#define PRIMARY_STEPS 128
#define SHADOW_STEPS 48
#define MAX_BOUNCES 4
#define PI 3.14159265359f

struct Vec3 {
	float x, y, z;
};

__device__ inline Vec3 vec3(float x, float y, float z) {
	Vec3 v;
	v.x = x;
	v.y = y;
	v.z = z;
	return v;
}

__device__ inline Vec3 add(Vec3 a, Vec3 b) { return vec3(a.x + b.x, a.y + b.y, a.z + b.z); }
__device__ inline Vec3 sub(Vec3 a, Vec3 b) { return vec3(a.x - b.x, a.y - b.y, a.z - b.z); }
__device__ inline Vec3 mulf(Vec3 a, float s) { return vec3(a.x * s, a.y * s, a.z * s); }
__device__ inline Vec3 mulv(Vec3 a, Vec3 b) { return vec3(a.x * b.x, a.y * b.y, a.z * b.z); }
__device__ inline float dot3(Vec3 a, Vec3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
__device__ inline Vec3 cross3(Vec3 a, Vec3 b) {
	return vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
}
__device__ inline float length3(Vec3 a) { return sqrtf(dot3(a, a)); }
__device__ inline Vec3 normalize3(Vec3 a) {
	float len = length3(a);
	return len > 1e-12f ? mulf(a, 1.0f / len) : a;
}

// ----- RNG: same PCG-hash chain as cinematic.frag, seeded per pixel+frame+sample. -----
__device__ inline unsigned int pcgHash(unsigned int x) {
	x = x * 747796405u + 2891336453u;
	unsigned int word = ((x >> ((x >> 28u) + 4u)) ^ x) * 277803737u;
	return (word >> 22u) ^ word;
}

struct Rng {
	unsigned int state;
};

__device__ inline float rngNext(Rng *rng) {
	rng->state = pcgHash(rng->state);
	return (float) rng->state * (1.0f / 4294967296.0f);
}

// Henyey-Greenstein phase function value for the cosine of the angle between
// the incoming and outgoing directions.
__device__ inline float phaseHG(float cosTheta, float g) {
	float g2 = g * g;
	float denom = 1.0f + g2 - 2.0f * g * cosTheta;
	denom = fmaxf(denom, 1e-4f);
	return (1.0f - g2) / (4.0f * PI * denom * sqrtf(denom));
}

// Importance-samples a new direction around `forward` according to the HG phase function with asymmetry g.
__device__ inline Vec3 sampleHG(Vec3 forward, float g, Rng *rng) {
	float cosTheta;
	if (fabsf(g) < 1e-3f) {
		cosTheta = 1.0f - 2.0f * rngNext(rng);
	} else {
		float sq = (1.0f - g * g) / (1.0f + g - 2.0f * g * rngNext(rng));
		cosTheta = (1.0f + g * g - sq * sq) / (2.0f * g);
	}
	float sinTheta = sqrtf(fmaxf(0.0f, 1.0f - cosTheta * cosTheta));
	float phi = 2.0f * PI * rngNext(rng);

	Vec3 up = fabsf(forward.z) < 0.999f ? vec3(0.0f, 0.0f, 1.0f) : vec3(1.0f, 0.0f, 0.0f);
	Vec3 tangent = normalize3(cross3(up, forward));
	Vec3 bitangent = cross3(forward, tangent);
	return add(add(mulf(tangent, sinTheta * cosf(phi)), mulf(bitangent, sinTheta * sinf(phi))), mulf(forward, cosTheta));
}

// Same area-light soft-shadow jitter as cinematic.frag's jitteredLightDir().
__device__ inline Vec3 jitteredLightDir(Vec3 lightDir, float angularRadius, Rng *rng) {
	if (angularRadius <= 0.0f) {
		return lightDir;
	}
	float r = angularRadius * sqrtf(rngNext(rng));
	float phi = 2.0f * PI * rngNext(rng);
	Vec3 up = fabsf(lightDir.z) < 0.999f ? vec3(0.0f, 0.0f, 1.0f) : vec3(1.0f, 0.0f, 0.0f);
	Vec3 tangent = normalize3(cross3(up, lightDir));
	Vec3 bitangent = cross3(lightDir, tangent);
	Vec3 offset = add(mulf(tangent, r * cosf(phi)), mulf(bitangent, r * sinf(phi)));
	return normalize3(add(lightDir, offset));
}

// 3D裁断（クリッピング）領域を考慮したボックス交差。clipMin/clipMax はローカル単位立方体
// 空間 (-0.5〜0.5)。裁断OFF時/最大時は (-0.5, 0.5) が渡され、立方体全体＝裁断なしと等価。
__device__ inline bool intersectBox(Vec3 origin, Vec3 dir, Vec3 clipMin, Vec3 clipMax, float *tNear, float *tFar) {
	float invDirX = 1.0f / dir.x, invDirY = 1.0f / dir.y, invDirZ = 1.0f / dir.z;
	float t1x = (clipMin.x - origin.x) * invDirX, t2x = (clipMax.x - origin.x) * invDirX;
	float t1y = (clipMin.y - origin.y) * invDirY, t2y = (clipMax.y - origin.y) * invDirY;
	float t1z = (clipMin.z - origin.z) * invDirZ, t2z = (clipMax.z - origin.z) * invDirZ;

	float tMinX = fminf(t1x, t2x), tMaxX = fmaxf(t1x, t2x);
	float tMinY = fminf(t1y, t2y), tMaxY = fmaxf(t1y, t2y);
	float tMinZ = fminf(t1z, t2z), tMaxZ = fmaxf(t1z, t2z);

	*tNear = fmaxf(fmaxf(tMinX, tMinY), tMinZ);
	*tFar = fminf(fminf(tMaxX, tMaxY), tMaxZ);
	return *tNear <= *tFar && *tFar > 0.0f;
}

// Opacity curve value (0..1) at a local-space position, mirroring volume.frag/cinematic.frag's
// window/level + LUT mapping, via CUDA texture objects bound to the same GL-interop volume/LUT arrays.
__device__ inline float sampleAlpha(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex, Vec3 localPos,
		float uMin, float uMax, float uWinCenter, float uWinWidth, Vec3 *albedo) {
	float u = localPos.x + 0.5f;
	float v = localPos.y + 0.5f;
	float w = localPos.z + 0.5f;

	float rawVal = tex3D<float>(volumeTex, u, v, w);
	float val = (rawVal - uMin) / (uMax - uMin);
	float winMin = uWinCenter - (uWinWidth * 0.5f);
	val = fminf(fmaxf((val - winMin) / uWinWidth, 0.0f), 1.0f);

	float4 src = tex1D<float4>(lutTex, val);
	albedo->x = src.x;
	albedo->y = src.y;
	albedo->z = src.z;
	return src.w;
}

__device__ inline float shadowTransmittance(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex, Vec3 origin,
		Vec3 lightDir, float angularRadius, float uMin, float uMax, float uWinCenter, float uWinWidth,
		Vec3 clipMin, Vec3 clipMax, Rng *rng) {
	Vec3 jLightDir = jitteredLightDir(lightDir, angularRadius, rng);
	float tNear, tFar;
	if (!intersectBox(origin, jLightDir, clipMin, clipMax, &tNear, &tFar)) {
		return 1.0f;
	}
	tNear = fmaxf(tNear, 0.0f);
	float dist = tFar - tNear;
	if (dist <= 0.0001f) {
		return 1.0f;
	}

	float stepSize = dist / (float) SHADOW_STEPS;
	Vec3 pos = add(origin, mulf(jLightDir, tNear));
	float opticalDepth = 0.0f;
	for (int i = 0; i < SHADOW_STEPS; i++) {
		Vec3 albedo;
		float alpha = sampleAlpha(volumeTex, lutTex, pos, uMin, uMax, uWinCenter, uWinWidth, &albedo);
		opticalDepth += -logf(fmaxf(1.0f - alpha, 1e-4f));
		pos = add(pos, mulf(jLightDir, stepSize));
	}
	return expf(-opticalDepth);
}

__device__ inline Vec3 tracePath(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex, Vec3 rayOrigin,
		Vec3 rayDir, Vec3 lightDir, float lightIntensity, float ambientIntensity, float anisotropy,
		float lightAngularRadius, float uMin, float uMax, float uWinCenter, float uWinWidth,
		Vec3 clipMin, Vec3 clipMax, Rng *rng) {
	Vec3 radiance = vec3(0.0f, 0.0f, 0.0f);
	Vec3 throughput = vec3(1.0f, 1.0f, 1.0f);

	for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
		float tNear, tFar;
		if (!intersectBox(rayOrigin, rayDir, clipMin, clipMax, &tNear, &tFar)) {
			break;
		}
		tNear = fmaxf(tNear, 0.0f);
		float dist = tFar - tNear;
		if (dist <= 0.0001f) {
			break;
		}

		float stepSize = dist / (float) PRIMARY_STEPS;
		Vec3 pos = add(rayOrigin, mulf(rayDir, tNear + rngNext(rng) * stepSize));

		bool hit = false;
		Vec3 hitAlbedo = vec3(0.0f, 0.0f, 0.0f);
		for (int i = 0; i < PRIMARY_STEPS; i++) {
			Vec3 albedo;
			float alpha = sampleAlpha(volumeTex, lutTex, pos, uMin, uMax, uWinCenter, uWinWidth, &albedo);
			if (rngNext(rng) < alpha) {
				hit = true;
				hitAlbedo = albedo;
				break;
			}
			pos = add(pos, mulf(rayDir, stepSize));
		}

		if (!hit) {
			break;
		}

		float cosTheta = dot3(rayDir, lightDir);
		float phase = phaseHG(cosTheta, anisotropy);
		float visibility = shadowTransmittance(volumeTex, lutTex, pos, lightDir, lightAngularRadius, uMin, uMax,
				uWinCenter, uWinWidth, clipMin, clipMax, rng);
		Vec3 direct = mulf(hitAlbedo, lightIntensity * visibility * phase * 4.0f * PI + ambientIntensity);
		radiance = add(radiance, mulv(throughput, direct));

		if (bounce >= 1) {
			float continueProb = fminf(fmaxf(fmaxf(hitAlbedo.x, fmaxf(hitAlbedo.y, hitAlbedo.z)), 0.05f), 0.95f);
			if (rngNext(rng) > continueProb) {
				break;
			}
			throughput = mulf(throughput, 1.0f / continueProb);
		}

		throughput = mulv(throughput, hitAlbedo);
		rayDir = sampleHG(rayDir, anisotropy, rng);
		rayOrigin = pos;
	}

	return radiance;
}

// Unprojects pixel (x,y) through invMvp (row-major-as-laid-out-by-JOML, i.e. column-major
// storage matching GLSL convention) to a near-clip-plane point in local cube space,
// equivalent to the vPos the GLSL path obtains by rasterizing the volume cube.
__device__ inline Vec3 unprojectNear(const float *invMvp, float ndcX, float ndcY) {
	float cx[4] = { ndcX, ndcY, -1.0f, 1.0f };
	float out[4];
	for (int row = 0; row < 4; row++) {
		out[row] = invMvp[row + 0] * cx[0] + invMvp[row + 4] * cx[1] + invMvp[row + 8] * cx[2]
				+ invMvp[row + 12] * cx[3];
	}
	float invW = 1.0f / out[3];
	return vec3(out[0] * invW, out[1] * invW, out[2] * invW);
}

extern "C" __global__ void pathTraceKernel(cudaTextureObject_t volumeTex, cudaTextureObject_t lutTex,
		cudaSurfaceObject_t accumSurf, int width, int height, const float *invMvp, float camX, float camY,
		float camZ, float uMin, float uMax, float uWinCenter, float uWinWidth, float lightDirX, float lightDirY,
		float lightDirZ, float lightIntensity, float ambientIntensity, float anisotropy, float lightAngularRadius,
		int samplesPerFrame, unsigned int frameSeed,
		float clipMinX, float clipMinY, float clipMinZ, float clipMaxX, float clipMaxY, float clipMaxZ) {
	int x = blockIdx.x * blockDim.x + threadIdx.x;
	int y = blockIdx.y * blockDim.y + threadIdx.y;
	if (x >= width || y >= height) {
		return;
	}

	float ndcX = ((float) x + 0.5f) / (float) width * 2.0f - 1.0f;
	float ndcY = ((float) y + 0.5f) / (float) height * 2.0f - 1.0f;

	Vec3 camPos = vec3(camX, camY, camZ);
	Vec3 nearPoint = unprojectNear(invMvp, ndcX, ndcY);
	Vec3 primaryDir = normalize3(sub(nearPoint, camPos));
	Vec3 lightDir = vec3(lightDirX, lightDirY, lightDirZ);
	Vec3 clipMin = vec3(clipMinX, clipMinY, clipMinZ);
	Vec3 clipMax = vec3(clipMaxX, clipMaxY, clipMaxZ);

	unsigned int pixelSeed = (unsigned int) x * 1973u + (unsigned int) y * 9277u + frameSeed * 26699u;

	Vec3 accumulated = vec3(0.0f, 0.0f, 0.0f);
	int samples = samplesPerFrame > 0 ? samplesPerFrame : 1;
	for (int s = 0; s < samples; s++) {
		Rng rng;
		rng.state = pixelSeed ^ ((unsigned int) s * 374761393u);
		Vec3 sample = tracePath(volumeTex, lutTex, camPos, primaryDir, lightDir, lightIntensity, ambientIntensity,
				anisotropy, lightAngularRadius, uMin, uMax, uWinCenter, uWinWidth, clipMin, clipMax, &rng);
		accumulated = add(accumulated, sample);
	}
	accumulated = mulf(accumulated, 1.0f / (float) samples);

	int xByteOffset = x * (int) sizeof(float4);
	float4 prev = surf2Dread<float4>(accumSurf, xByteOffset, y);
	float4 next;
	next.x = prev.x + accumulated.x;
	next.y = prev.y + accumulated.y;
	next.z = prev.z + accumulated.z;
	next.w = 1.0f;
	surf2Dwrite<float4>(next, accumSurf, xByteOffset, y);
}
