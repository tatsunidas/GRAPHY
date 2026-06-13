#version 330 core

in vec3 FragPos;
in vec3 Normal;
in vec4 VertexColor;

out vec4 FragColor;

uniform vec4 uColor;
uniform vec3 uLightPos;
uniform bool uUseVertexColor;

void main() {
   // 環境光 (Ambient)
   float ambientStrength = 0.4;
   vec3 ambient = ambientStrength * vec3(1.0, 1.0, 1.0);
   
   // 拡散光 (Diffuse)
   vec3 norm = normalize(Normal);
   vec3 lightDir = normalize(uLightPos - FragPos);
   float diff = max(dot(norm, lightDir), 0.0);
   vec3 diffuse = diff * vec3(1.0, 1.0, 1.0);
   
   // ベースカラーの決定 (頂点カラーを使うか、UI指定色を使うか)
   vec4 baseColor = uUseVertexColor ? VertexColor : uColor;
   vec3 result = (ambient + diffuse) * baseColor.rgb;
   
   // 透明度はUIのスライダー(uColor.a)を掛け合わせる
   FragColor = vec4(result, baseColor.a * uColor.a);
}