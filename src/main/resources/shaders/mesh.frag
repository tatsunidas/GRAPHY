#version 330 core

in vec3 FragPos;
in vec3 Normal;

out vec4 FragColor;

uniform vec4 uColor;      // メッシュのベースカラー（RGBA）
uniform vec3 uLightPos;   // 光源の位置（カメラ位置などを渡す）

void main() {
    // --- 環境光 (Ambient) ---
    // 真っ暗にならないように最低限の明るさを担保
    float ambientStrength = 0.3;
    vec3 ambient = ambientStrength * uColor.rgb;

    // --- 拡散反射 (Diffuse) ---
    // 光の当たる角度に応じて明るさを変える
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(uLightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * uColor.rgb * 0.7;

    // 最終的な色を決定
    vec3 result = ambient + diffuse;
    FragColor = vec4(result, uColor.a);
}