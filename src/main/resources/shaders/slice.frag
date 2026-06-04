#version 330 core

in vec3 vTexCoord;
out vec4 FragColor;

uniform sampler3D volumeTex;
uniform sampler1D uLutTex;
uniform float uMin;
uniform float uMax;
uniform float uWinCenter;
uniform float uWinWidth;

void main() {
    if (vTexCoord.x < 0.0 || vTexCoord.x > 1.0 ||
        vTexCoord.y < 0.0 || vTexCoord.y > 1.0 ||
        vTexCoord.z < 0.0 || vTexCoord.z > 1.0) {
        discard;
    }
    
    vec3 sampleCoord = vec3(vTexCoord.x, 1.0 - vTexCoord.y, vTexCoord.z);
    float rawVal = texture(volumeTex, sampleCoord).r;
    float val = (rawVal - uMin) / (uMax - uMin);
    
    float winMin = uWinCenter - (uWinWidth * 0.5);
    val = (val - winMin) / uWinWidth;
    val = clamp(val, 0.0, 1.0);
    
    FragColor = texture(uLutTex, val);
}