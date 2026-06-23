#version 330 core

// Reads the cinematic renderer's accumulation buffer (sum of per-frame
// averaged samples, additively blended over uFrameCount frames), divides
// out the frame count, and applies a simple Reinhard tonemap + gamma so the
// unbounded HDR-ish path-traced radiance displays reasonably on screen.

in vec2 vUv;
out vec4 FragColor;

uniform sampler2D uAccumTex;
uniform float uFrameCount;
uniform float uExposure;

void main() {
    vec3 sum = texture(uAccumTex, vUv).rgb;
    vec3 color = (sum / max(uFrameCount, 1.0)) * uExposure;

    // Reinhard tonemap, then gamma-correct (the LUT/opacity curve already
    // works in display-referred space, so this just tames bounce highlights).
    color = color / (1.0 + color);
    color = pow(max(color, 0.0), vec3(1.0 / 2.2));

    FragColor = vec4(color, 1.0);
}
