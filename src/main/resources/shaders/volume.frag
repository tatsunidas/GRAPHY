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
uniform vec4 uRoiColors[32]; // 32個のカラーパレット配列

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

    float maxVal = 0.0;
    float bestRoiId = 0.0; // ★変更: MIPで一番明るかった場所のROIを記憶する
    vec4 accumulatedColor = vec4(0.0);

    for(int i = 0; i < steps; i++) {
        vec3 texCoord = currentPos + 0.5;
        texCoord.y = 1.0 - texCoord.y;
        texCoord.z = 1.0 - texCoord.z;

        float val = 0.0;
        vec4 srcColor = vec4(0.0);

        if (uShowVolume) {
            float rawVal = texture(volumeTex, texCoord).r;
            val = (rawVal - uMin) / (uMax - uMin);
            float winMin = uWinCenter - (uWinWidth * 0.5);
            val = clamp((val - winMin) / uWinWidth, 0.0, 1.0);
            srcColor = texture(uLutTex, val);
        }

        int currentRoiId = 0;
        if (uShowRoi) {
            // 値を 0〜255 の ID に戻す
            float rawRoiVal = texture(roiTex, texCoord).r;
            currentRoiId = int(rawRoiVal * 255.0 + 0.5);
        }

        // --- レンダリングモードに応じた処理 ---
        if (uRenderMode == 0) {
            // --- MIP (最大値投影) ---
            if(val > maxVal) {
                maxVal = val;
                // ★変更: 最も明るいボクセルを更新した時だけ、その場所のROI IDを記憶する
                if (currentRoiId > 0 && currentRoiId < 32) {
                    bestRoiId = float(currentRoiId);
                } else {
                    bestRoiId = 0.0; // ROI外ならリセット
                }
            }
        } else {
            // --- DVR ---
            if (currentRoiId > 0 && currentRoiId < 32) {
                vec4 rColor = uRoiColors[currentRoiId];
                srcColor.rgb = mix(srcColor.rgb, rColor.rgb, rColor.a);
                srcColor.a = max(srcColor.a, rColor.a);
            }
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
        if (uShowRoi && bestRoiId > 0.5) {
            int id = int(bestRoiId + 0.5);
            vec4 rColor = uRoiColors[id];
            mipOutput = mix(mipOutput, rColor.rgb, rColor.a);
        }
        FragColor = vec4(mipOutput, 1.0);
    } else {
        FragColor = accumulatedColor;
    }
}