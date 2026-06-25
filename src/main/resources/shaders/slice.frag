#version 330 core

in vec3 vTexCoord;
out vec4 FragColor;

uniform sampler3D volumeTex;
uniform sampler1D uLutTex;
uniform float uWinCenter;
uniform float uWinWidth;
uniform float uMin;
uniform float uMax;

// ★追加: ROI用の変数
uniform sampler3D roiTex;
uniform bool uShowRoi;
uniform vec4 uRoiColors[32];

// 3D裁断（クリッピング）領域。ローカル単位立方体空間の境界(各成分 -0.5〜0.5)。
// 断面表示ではテクスチャ座標(0〜1)に +0.5 して比較し、領域外フラグメントを破棄する。
// 裁断OFF時/最大時は (-0.5, 0.5) が渡され、立方体全体＝裁断なしと等価になる。
uniform vec3 uClipMin;
uniform vec3 uClipMax;

void main() {
	vec3 sampleCoord = vTexCoord;

    // 裁断領域外の断面ピクセルは描画しない
    vec3 clipLo = uClipMin + 0.5;
    vec3 clipHi = uClipMax + 0.5;
    if (any(lessThan(sampleCoord, clipLo)) || any(greaterThan(sampleCoord, clipHi))) {
        discard;
    }
    // 座標の反転 (Z軸も volume.frag と同様に反転)
    // vec3 sampleCoord = vec3(1.0 - vTexCoord.x, 1.0 - vTexCoord.y, 1.0 - vTexCoord.z);
    
    // 1. ボリューム(CT)のサンプリング
    float rawVal = texture(volumeTex, sampleCoord).r;
    float val = (rawVal - uMin) / (uMax - uMin);
    float winMin = uWinCenter - (uWinWidth * 0.5);
    val = clamp((val - winMin) / uWinWidth, 0.0, 1.0);
    vec4 srcColor = texture(uLutTex, val);

    // 2. ROIのサンプリングとブレンド
    if (uShowRoi) {
        float rawRoiVal = texture(roiTex, sampleCoord).r;
        int roiId = int(rawRoiVal * 255.0 + 0.5);

        if (roiId > 0 && roiId < 32) {
            vec4 rColor = uRoiColors[roiId];
            // CT画像の色の上に、ROIの色をアルファブレンド
            srcColor.rgb = mix(srcColor.rgb, rColor.rgb, rColor.a);
        }
    }

    FragColor = srcColor;
}