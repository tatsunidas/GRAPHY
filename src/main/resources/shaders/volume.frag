#version 330 core

in vec3 vPos;
out vec4 FragColor;

uniform sampler3D volumeTex;
uniform sampler3D roiTex;
uniform sampler1D uLutTex;

uniform vec3 cameraPos;
uniform float uMin;
uniform float uMax;
uniform float uWinCenter;
uniform float uWinWidth;
uniform int uRenderMode;

uniform bool uShowVolume;
uniform bool uShowRoi;
uniform vec4 uRoiColor;

bool intersectBox(vec3 origin, vec3 dir, out float tNear, out float tFar) {
    vec3 boxMin = vec3(-0.5);
    vec3 boxMax = vec3(0.5);
    vec3 invDir = 1.0 / dir;
    vec3 tMin = (boxMin - origin) * invDir;
    vec3 tMax = (boxMax - origin) * invDir;
    vec3 t1 = min(tMin, tMax);
    vec3 t2 = max(tMin, tMax);
    tNear = max(max(t1.x, t1.y), t1.z);
    tFar = min(min(t2.x, t2.y), t2.z);
    return tNear <= tFar && tFar > 0.0;
}

void main() {
    vec3 rayDir = normalize(vPos - cameraPos);
    float tNear, tFar;
    if (!intersectBox(cameraPos, rayDir, tNear, tFar)) discard;
    tNear = max(tNear, 0.0);

    vec3 rayStart = cameraPos + rayDir * tNear;
    vec3 rayStop = cameraPos + rayDir * tFar;
    float dist = distance(rayStart, rayStop);
    int steps = 256;
    float stepSize = dist / float(steps);
    vec3 currentPos = rayStart;

    float maxVal = 0.0;                // MIP用
    float hitRoi = 0.0;                // MIP用のROI交差判定
    vec4 accumulatedColor = vec4(0.0); // DVR用 (RGB + Alpha)

    for(int i = 0; i < steps; i++) {
        vec3 texCoord = currentPos + 0.5;
        // 画像(左上原点)とOpenGL(左下原点)のズレを吸収するため、Y座標を反転
        texCoord.y = 1.0 - texCoord.y;

        float val = 0.0;
        vec4 srcColor = vec4(0.0);

        // 1. ボリュームのサンプリング
        if (uShowVolume) {
            float rawVal = texture(volumeTex, texCoord).r;
            val = (rawVal - uMin) / (uMax - uMin);
            float winMin = uWinCenter - (uWinWidth * 0.5);
            val = clamp((val - winMin) / uWinWidth, 0.0, 1.0);
            srcColor = texture(uLutTex, val);
        }

        // 2. ROIマスクのサンプリングとブレンド
        if (uShowRoi) {
            float roiVal = texture(roiTex, texCoord).r;
            if (roiVal > 0.5) {
                hitRoi = 1.0; 
                srcColor.rgb = mix(srcColor.rgb, uRoiColor.rgb, uRoiColor.a);
                srcColor.a = max(srcColor.a, uRoiColor.a);
            }
        }

        // 3. レンダリングモードに応じた合成
        if (uRenderMode == 0) {
            // --- MIP (最大値投影) ---
            if(val > maxVal) maxVal = val;
        } else {
            // --- DVR ---
            if (srcColor.a > 0.0) {
                accumulatedColor.rgb += (1.0 - accumulatedColor.a) * srcColor.a * srcColor.rgb;
                accumulatedColor.a   += (1.0 - accumulatedColor.a) * srcColor.a;
            }
            if (accumulatedColor.a >= 0.95) break;
        }

        currentPos += rayDir * stepSize;
    }

    if (uRenderMode == 0) {
        vec3 mipOutput = vec3(maxVal);
        if (uShowRoi && hitRoi > 0.5) {
            mipOutput = mix(mipOutput, uRoiColor.rgb, uRoiColor.a);
        }
        FragColor = vec4(mipOutput, 1.0);
    } else {
        FragColor = accumulatedColor;
    }
}