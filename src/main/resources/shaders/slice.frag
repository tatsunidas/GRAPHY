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

void main() {
	vec3 sampleCoord = vTexCoord;
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