package com.vis.core.view.D3.ui;

import static org.lwjgl.opengl.GL33.*;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.vis.core.log.Log;

public class MeshRenderer {

    private int shaderProgram = -1;
    private int vaoId = -1, vboVertices = -1, vboNormals = -1, eboIndices = -1;
    private int indexCount = 0;

    // uniform変数へのロケーション
    private int mvpLoc, modelLoc, colorLoc, lightPosLoc;

    public void init() {
        compileShaders();
    }

    private void compileShaders() {
        String vertexShaderSource = ShaderUtils.loadShaderAsString("/shaders/mesh.vert");
        String fragmentShaderSource = ShaderUtils.loadShaderAsString("/shaders/mesh.frag");

        int vShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vShader, vertexShaderSource);
        glCompileShader(vShader);
        if (glGetShaderi(vShader, GL_COMPILE_STATUS) == GL_FALSE) {
            Log.logger.severe("Mesh Vertex Shader Error: " + glGetShaderInfoLog(vShader));
        }

        int fShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fShader, fragmentShaderSource);
        glCompileShader(fShader);
        if (glGetShaderi(fShader, GL_COMPILE_STATUS) == GL_FALSE) {
            Log.logger.severe("Mesh Fragment Shader Error: " + glGetShaderInfoLog(fShader));
        }

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vShader);
        glAttachShader(shaderProgram, fShader);
        glLinkProgram(shaderProgram);

        glDeleteShader(vShader);
        glDeleteShader(fShader);

        // Uniformのロケーションを取得
        mvpLoc = glGetUniformLocation(shaderProgram, "mvp");
        modelLoc = glGetUniformLocation(shaderProgram, "model");
        colorLoc = glGetUniformLocation(shaderProgram, "uColor");
        lightPosLoc = glGetUniformLocation(shaderProgram, "uLightPos");
    }

    /**
     * メッシュデータをGPUにアップロードします。
     * 既存のデータがあれば破棄してメモリを解放します。
     */
    public void uploadMesh(MeshData mesh) {
        if (mesh == null || mesh.vertices == null || mesh.indices == null) return;

        cleanup(); // 古いバッファを削除

        this.indexCount = mesh.indices.length;

        // VAOの作成
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // 1. 頂点座標のVBO
        vboVertices = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboVertices);
        FloatBuffer verticesBuffer = MemoryUtil.memAllocFloat(mesh.vertices.length);
        verticesBuffer.put(mesh.vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        MemoryUtil.memFree(verticesBuffer);

        // 2. 法線ベクトルのVBO
        vboNormals = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboNormals);
        FloatBuffer normalsBuffer = MemoryUtil.memAllocFloat(mesh.normals.length);
        normalsBuffer.put(mesh.normals).flip();
        glBufferData(GL_ARRAY_BUFFER, normalsBuffer, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(1);
        MemoryUtil.memFree(normalsBuffer);

        // 3. インデックスのEBO
        eboIndices = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboIndices);
        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(mesh.indices.length);
        indicesBuffer.put(mesh.indices).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(indicesBuffer);

        // バインド解除
        glBindVertexArray(0);
        
        Log.logger.info("Mesh successfully uploaded to GPU. VAO: " + vaoId);
    }

    /**
     * メッシュを描画します。
     */
    public void render(Matrix4f mvpMatrix, Matrix4f modelMatrix, Vector3f cameraPosLocal, java.awt.Color meshColor, float alpha) {
        if (shaderProgram <= 0 || vaoId <= 0 || indexCount == 0) return;

        glUseProgram(shaderProgram);

        // 1. 行列の送信
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            glUniformMatrix4fv(mvpLoc, false, mvpMatrix.get(stack.mallocFloat(16)));
            glUniformMatrix4fv(modelLoc, false, modelMatrix.get(stack.mallocFloat(16)));
        }

        // 2. 光源位置（簡易的にカメラから光が出ているようにする）
        glUniform3f(lightPosLoc, cameraPosLocal.x, cameraPosLocal.y, cameraPosLocal.z);

        // 3. 色と透明度の送信
        float r = meshColor.getRed() / 255.0f;
        float g = meshColor.getGreen() / 255.0f;
        float b = meshColor.getBlue() / 255.0f;
        glUniform4f(colorLoc, r, g, b, alpha);

        // 4. アルファブレンド・デプステストの有効化
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        
        // メッシュ描画時は裏面カリングを有効にするのが標準的
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // 5. 描画
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        glUseProgram(0);
    }
    
    /**
     * 外部で管理されたVAOを指定してメッシュを描画します。（複数メッシュ同時表示用）
     */
    public void renderMesh(int targetVao, int targetIndexCount, Matrix4f mvpMatrix, Matrix4f modelMatrix, Vector3f cameraPosLocal, java.awt.Color meshColor, float alpha) {
        if (shaderProgram <= 0 || targetVao <= 0 || targetIndexCount == 0) return;

        glUseProgram(shaderProgram);

        // 1. 行列の送信 (MVPで位置とサイズ、Modelで回転を制御)
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            glUniformMatrix4fv(mvpLoc, false, mvpMatrix.get(stack.mallocFloat(16)));
            glUniformMatrix4fv(modelLoc, false, modelMatrix.get(stack.mallocFloat(16)));
        }

        // 2. 光源位置の送信
        glUniform3f(lightPosLoc, cameraPosLocal.x, cameraPosLocal.y, cameraPosLocal.z);

        // 3. 色と透明度の送信
        float r = meshColor.getRed() / 255.0f;
        float g = meshColor.getGreen() / 255.0f;
        float b = meshColor.getBlue() / 255.0f;
        glUniform4f(colorLoc, r, g, b, alpha);

        // 4. ステート設定
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // 5. 指定されたVAOをバインドして描画！
        glBindVertexArray(targetVao);
        glDrawElements(GL_TRIANGLES, targetIndexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        glUseProgram(0);
    }

    public void cleanup() {
        if (vaoId != -1) glDeleteVertexArrays(vaoId);
        if (vboVertices != -1) glDeleteBuffers(vboVertices);
        if (vboNormals != -1) glDeleteBuffers(vboNormals);
        if (eboIndices != -1) glDeleteBuffers(eboIndices);
        
        vaoId = -1;
        vboVertices = -1;
        vboNormals = -1;
        eboIndices = -1;
        indexCount = 0;
    }
}