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

uniform bool uIsEmbedded;
uniform vec3 uSlicePos;

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
    
    if (uIsEmbedded) {
        // X平面との交差判定
        if (abs(rayDir.x) > 1e-5) {
            float t = (uSlicePos.x - cameraPos.x) / rayDir.x;
            if (t > tNear && t < tFar) tFar = t; // 壁にぶつかったらそこでストップ
        }
        // Y平面との交差判定
        if (abs(rayDir.y) > 1e-5) {
            float t = (uSlicePos.y - cameraPos.y) / rayDir.y;
            if (t > tNear && t < tFar) tFar = t;
        }
        // Z平面との交差判定
        if (abs(rayDir.z) > 1e-5) {
            float t = (uSlicePos.z - cameraPos.z) / rayDir.z;
            if (t > tNear && t < tFar) tFar = t;
        }
    }

    vec3 rayStart = cameraPos + rayDir * tNear;
    vec3 rayStop = cameraPos + rayDir * tFar;
    float dist = distance(rayStart, rayStop);
    
    if (dist <= 0.0001) discard;
    
    int steps = 256;
    float stepSize = dist / float(steps);
    vec3 currentPos = rayStart;

    float maxVal = 0.0;
    float frontRoiId = 0.0; // ★変更: MIP用に「一番手前でぶつかったROI」を記憶する
    vec4 accumulatedColor = vec4(0.0);

    for(int i = 0; i < steps; i++) {
        vec3 texCoord = currentPos + 0.5;
        // 位置反転させる場合
        // texCoord.x = 1.0 - texCoord.x; // ★追加: 左右の鏡像反転を直す
        // texCoord.y = 1.0 - texCoord.y;
        // texCoord.z = 1.0 - texCoord.z;

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

            // ★追加: MIPモードの時、最初にぶつかったROIのIDを記憶する
            if (uRenderMode == 0 && currentRoiId > 0 && currentRoiId < 32) {
                if (frontRoiId < 0.5) {
                    frontRoiId = float(currentRoiId);
                }
            }
        }

        // --- レンダリングモードに応じた処理 ---
        if (uRenderMode == 0) {
            // --- MIP (最大値投影) ---
            // 不透明度カーブで0にされた値域は投影の対象から除外する。
            // (VRモードのアルファ合成と同様、カーブでコントラストを制限できるようにする)
            if (srcColor.a > 0.0 && val > maxVal) {
                maxVal = val;
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
        float outAlpha = 1.0; // これまでの通常動作（ボリュームの箱を不透明にする）
        
        if (uShowRoi && frontRoiId > 0.5) {
            int id = int(frontRoiId + 0.5);
            vec4 rColor = uRoiColors[id];
            mipOutput = mix(mipOutput, rColor.rgb, rColor.a);
            
            // ボリューム非表示(Float Overlay等)の時は、ROIの透明度スライダーの値をそのまま使う
            if (!uShowVolume) {
                outAlpha = rColor.a;
            }
        } else if (!uShowVolume) {
            // ROIにも当たらず、ボリュームも非表示なら「完全な透明」にして背景(Ortho等)を透かす
            outAlpha = 0.0;
        }

        FragColor = vec4(mipOutput, outAlpha);
    } else {
        FragColor = accumulatedColor;
    }
}