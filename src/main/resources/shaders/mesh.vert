#version 330 core

// Java側から送られてくるデータ
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;

out vec3 FragPos;
out vec3 Normal;

// 行列
uniform mat4 mvp;
uniform mat4 model;

void main() {
    // 陰影計算用のワールド座標と法線
    FragPos = vec3(model * vec4(aPos, 1.0));
    // ※スケーリングのみを想定しているため mat3(model) をそのまま使います
    Normal = mat3(model) * aNormal; 
    
    // 最終的な画面上の頂点位置
    gl_Position = mvp * vec4(aPos, 1.0);
}