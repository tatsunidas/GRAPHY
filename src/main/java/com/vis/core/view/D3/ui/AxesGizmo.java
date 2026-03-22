/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.D3.ui;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/**
 * 
 * @author tatsunidas
 *
 */
public class AxesGizmo {
    private int programId;
    private int vaoId, vboId;
    private int mvpLoc;

    // X=赤, Y=緑, Z=青 のラインデータ
    // (位置x,y,z, 色r,g,b)
    private final float[] vertices = {
        // X軸 (赤)
        0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, // 原点
        1.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f, // X先

        // Y軸 (緑)
        0.0f, 0.0f, 0.0f,  0.0f, 1.0f, 0.0f, // 原点
        0.0f, 1.0f, 0.0f,  0.0f, 1.0f, 0.0f, // Y先

        // Z軸 (青)
        0.0f, 0.0f, 0.0f,  0.0f, 0.0f, 1.0f, // 原点
        0.0f, 0.0f, 1.0f,  0.0f, 0.0f, 1.0f  // Z先
    };

    private final String vertexShader = "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec3 aColor;\n" +
            "uniform mat4 mvp;\n" +
            "out vec3 vColor;\n" +
            "void main(){\n" +
            "    gl_Position = mvp * vec4(aPos, 1.0);\n" +
            "    vColor = aColor;\n" +
            "}";

    private final String fragmentShader = "#version 330 core\n" +
            "in vec3 vColor;\n" +
            "out vec4 FragColor;\n" +
            "void main(){\n" +
            "    FragColor = vec4(vColor, 1.0);\n" +
            "}";

    public void init() {
        // シェーダーコンパイル
        int vShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vShader, vertexShader);
        glCompileShader(vShader);

        int fShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fShader, fragmentShader);
        glCompileShader(fShader);

        programId = glCreateProgram();
        glAttachShader(programId, vShader);
        glAttachShader(programId, fShader);
        glLinkProgram(programId);
        
        glDeleteShader(vShader);
        glDeleteShader(fShader);

        mvpLoc = glGetUniformLocation(programId, "mvp");

        // バッファ作成
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(vertices.length);
            buffer.put(vertices).flip();
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        }

        // 位置属性 (Stride = 6 * float)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        // 色属性 (Stride = 6 * float, Offset = 3 * float)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    /**
     * 画面の隅にGizmoを描画する
     * @param viewMatrix メインカメラのView行列
     * @param windowWidth 画面全体の幅
     * @param windowHeight 画面全体の高さ
     */
    public void render(Matrix4f viewMatrix, int windowWidth, int windowHeight) {
        if (programId <= 0) return;

        // 1. ビューポートを「右下」の小さな領域に設定
        int size = 100; // 100x100ピクセルの領域
        int padding = 10;
        glViewport(windowWidth - size - padding, padding, size, size);
        
        // 2. 深度テストを無効化（常に最前面に表示するため）
        glDisable(GL_DEPTH_TEST);

        glUseProgram(programId);

        // 3. 行列の作成
        // Gizmo用のProjection (平行投影でOK)
        // 2.5fくらいの範囲を移す
        Matrix4f proj = new Matrix4f().ortho(-2.0f, 2.0f, -2.0f, 2.0f, -10.0f, 10.0f);
        
        // Gizmo用のView行列
        // メインカメラの「回転」だけを抽出する（平行移動は無視）
        // そうしないと、ズームしたときに矢印がどこかへ行ってしまうため
        Matrix4f viewRotationOnly = new Matrix4f(viewMatrix);
        viewRotationOnly.m30(0); viewRotationOnly.m31(0); viewRotationOnly.m32(0); // 平行移動成分をゼロに

        Matrix4f mvp = proj.mul(viewRotationOnly);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(mvpLoc, false, mvp.get(stack.mallocFloat(16)));
        }

        // 4. 線を描画 (太さを少し変えると見やすい)
        glLineWidth(3.0f); 
        glBindVertexArray(vaoId);
        glDrawArrays(GL_LINES, 0, 6); // 3本の線(2頂点 * 3)
        
        glBindVertexArray(0);
        glUseProgram(0);
        glLineWidth(1.0f); // 戻す

        // 5. 設定を戻す
        glEnable(GL_DEPTH_TEST);
        // ビューポートを全体に戻すのは、次回の paintGL の冒頭で行われるはずですが、
        // 念のためここでも戻しても良いです。
        glViewport(0, 0, windowWidth, windowHeight);
    }
}
