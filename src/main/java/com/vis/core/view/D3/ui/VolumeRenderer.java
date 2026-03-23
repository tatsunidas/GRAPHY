/* ***** BEGIN LICENSE BLOCK ***** * Version: MPL 1.1/GPL 2.0/LGPL 2.1 
 * * The contents of this file are subject to the Mozilla Public License Version 
 * 1.1 (the "License"); you may not use this file except in compliance with 
 * the License. You may obtain a copy of the License at 
 * http://www.mozilla.org/MPL/ 
 * * Software distributed under the License is distributed on an "AS IS" basis, 
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License 
 * for the specific language governing rights and limitations under the 
 * License. 
 * * The Original Code is part of graphy, hosted at https://github.com/graphy. 
 * * The Initial Developer of the Original Code is 
 * Visionary Imaging Services, Inc. 
 * Portions created by the Initial Developer are Copyright (C) 2015 
 * the Initial Developer. All Rights Reserved. 
 * * Contributor(s): 
 * See @authors listed below 
 * * Alternatively, the contents of this file may be used under the terms of 
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
 * * ***** END LICENSE BLOCK ***** */
package com.vis.core.view.D3.ui;

import org.lwjgl.system.MemoryUtil;

import ij.plugin.LutLoader;

import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*; // 3Dテクスチャ・RGB定数用 
import static org.lwjgl.opengl.GL30.*; // 3Dテクスチャ・浮動小数点フォーマット用 

/**
 * * * @author tatsunidas
 */
public class VolumeRenderer {

	// --- 1. バーテックスシェーダー ---
	private final String vertexShaderSource = "#version 330 core\n" + "layout (location = 0) in vec3 aPos;\n"
			+ "uniform mat4 mvp;\n" + "out vec3 vPos;\n" + "void main() {\n"
			+ "    gl_Position = mvp * vec4(aPos, 1.0);\n" + "    vPos = aPos;\n" + "}";

	// --- 2. フラグメントシェーダー (レイキャスティング本番) ---
	private final String fragmentShaderSource = "#version 330 core\n" + "in vec3 vPos;\n" + "out vec4 FragColor;\n" +

			"uniform sampler3D volumeTex;\n" + "uniform vec3 cameraPos;\n" + "uniform float uMin;\n"
			+ "uniform float uMax;\n" + "uniform float uWinCenter;\n" + "uniform float uWinWidth;\n" +

			"uniform int uRenderMode;\n" + "uniform sampler1D uLutTex;\n" +

			"bool intersectBox(vec3 origin, vec3 dir, out float tNear, out float tFar) {\n"
			+ "    vec3 boxMin = vec3(-0.5); vec3 boxMax = vec3(0.5);\n" + "    vec3 invDir = 1.0 / dir;\n"
			+ "    vec3 tMin = (boxMin - origin) * invDir;\n" + "    vec3 tMax = (boxMax - origin) * invDir;\n"
			+ "    vec3 t1 = min(tMin, tMax);\n" + "    vec3 t2 = max(tMin, tMax);\n"
			+ "    tNear = max(max(t1.x, t1.y), t1.z);\n" + "    tFar = min(min(t2.x, t2.y), t2.z);\n"
			+ "    return tNear <= tFar && tFar > 0.0;\n" + "}\n" +

			"void main() {\n" + "    vec3 rayDir = normalize(vPos - cameraPos);\n" + "    float tNear, tFar;\n"
			+ "    if (!intersectBox(cameraPos, rayDir, tNear, tFar)) discard;\n" + "    tNear = max(tNear, 0.0);\n" +

			"    vec3 rayStart = cameraPos + rayDir * tNear;\n" + "    vec3 rayStop = cameraPos + rayDir * tFar;\n"
			+ "    float dist = distance(rayStart, rayStop);\n" + "    int steps = 256;\n"
			+ "    float stepSize = dist / float(steps);\n" + "    vec3 currentPos = rayStart;\n" +

			"    float maxVal = 0.0;          // MIP用\n"
			+ "    vec4 accumulatedColor = vec4(0.0); // DVR用 (RGB + Alpha)\n" +

			"    for(int i=0; i<steps; i++) {\n" 
			+ "        vec3 texCoord = currentPos + 0.5;\n"
			// ★ 追加: 画像(左上原点)とOpenGL(左下原点)のズレを吸収するため、Y座標を反転
			+ "        texCoord.y = 1.0 - texCoord.y;\n"
			+ "        float rawVal = texture(volumeTex, texCoord).r;\n"
			+ "        float val = (rawVal - uMin) / (uMax - uMin);\n" + "        \n" + "        // Window/Level 適用\n"
			+ "        float winMin = uWinCenter - (uWinWidth * 0.5);\n" + "        val = (val - winMin) / uWinWidth;\n"
			+ "        val = clamp(val, 0.0, 1.0);\n" +

			"        if (uRenderMode == 0) {\n" + "            // --- MIP (最大値投影) ---\n"
			+ "            if(val > maxVal) maxVal = val;\n" + "            if(maxVal >= 1.0) break;\n"
			+ "        } else {\n" + "            // DVR\n" + "            vec4 srcColor = texture(uLutTex, val);\n"
			+ "            \n" + "            if (srcColor.a > 0.0) {\n"
			+ "                accumulatedColor.rgb += (1.0 - accumulatedColor.a) * srcColor.a * srcColor.rgb;\n"
			+ "                accumulatedColor.a   += (1.0 - accumulatedColor.a) * srcColor.a;\n" + "            }\n"
			+ "            if (accumulatedColor.a >= 0.95) break;\n" + "        }\n" +

			"        currentPos += rayDir * stepSize;\n" + "    }\n" +

			"    if (uRenderMode == 0) {\n" + "        FragColor = vec4(vec3(maxVal), 1.0);\n" + "    } else {\n"
			+ "        FragColor = accumulatedColor;\n" + "    }\n" + "}";

	// --- 断面用 バーテックスシェーダー ---
	private final String sliceVertexShaderSource = "#version 330 core\n" + "layout (location = 0) in vec3 aPos;\n"
			+ "uniform mat4 mvp;\n" + "uniform mat4 model;\n" + "out vec3 vTexCoord;\n" + "void main() {\n"
			+ "    gl_Position = mvp * vec4(aPos, 1.0);\n" + "    \n" + "    vec4 worldPos = model * vec4(aPos, 1.0);\n"
			+ "    vTexCoord = worldPos.xyz + 0.5;\n" + "}";

	// --- 断面用 フラグメントシェーダー ---
	private final String sliceFragmentShaderSource = "#version 330 core\n" + "in vec3 vTexCoord;\n"
			+ "out vec4 FragColor;\n" +

			"uniform sampler3D volumeTex;\n" + "uniform sampler1D uLutTex;\n" + "uniform float uMin, uMax;\n"
			+ "uniform float uWinCenter, uWinWidth;\n" +

			"void main() {\n" 
			+ "    if (vTexCoord.x < 0.0 || vTexCoord.x > 1.0 ||\n"
			+ "        vTexCoord.y < 0.0 || vTexCoord.y > 1.0 ||\n"
			+ "        vTexCoord.z < 0.0 || vTexCoord.z > 1.0) discard;\n" 
			+ "    vec3 sampleCoord = vec3(vTexCoord.x, 1.0 - vTexCoord.y, vTexCoord.z);\n"
			+ "    float rawVal = texture(volumeTex, sampleCoord).r;\n"
			+ "    float val = (rawVal - uMin) / (uMax - uMin);\n"
			+ "    float winMin = uWinCenter - (uWinWidth * 0.5);\n" + "    val = (val - winMin) / uWinWidth;\n"
			+ "    val = clamp(val, 0.0, 1.0);\n" + "    \n" + "    FragColor = texture(uLutTex, val);\n" + "}";

	private int textureId = -1;

	private int shaderProgram = -1;
	private int vaoId, vboId;
	private int mvpLoc, camLoc;

	private final float[] cubeVertices = { -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
			-0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
			0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
			-0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
			-0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f,
			0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f,
			0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f };

	private final float[] quadVertices = { -0.5f, -0.5f, 0.0f, 0.5f, -0.5f, 0.0f, 0.5f, 0.5f, 0.0f, 0.5f, 0.5f, 0.0f,
			-0.5f, 0.5f, 0.0f, -0.5f, -0.5f, 0.0f };

	private int width, height, depth;
	private int minLoc, maxLoc;
	private float normalizedMin, normalizedMax;
	private int winCenterLoc, winWidthLoc;
	private float windowCenter = 0.5f;
	private float windowWidth = 1.0f;
	private int renderModeLoc;
	private int currentRenderMode = 0;
	private int lutTextureId = -1;

	private int sliceShaderProgram = -1;
	private int sliceVaoId, sliceVboId;
	private int sliceMvpLoc, sliceTexLoc, sliceLutLoc;
	private int sliceWinCenterLoc, sliceWinWidthLoc;
	private int sliceModelLoc;

	public void init() {
		compileShaders();
		createCube();

		renderModeLoc = glGetUniformLocation(shaderProgram, "uRenderMode");
		minLoc = glGetUniformLocation(shaderProgram, "uMin");
		maxLoc = glGetUniformLocation(shaderProgram, "uMax");
		winCenterLoc = glGetUniformLocation(shaderProgram, "uWinCenter");
		winWidthLoc = glGetUniformLocation(shaderProgram, "uWinWidth");

		generateLUT(0);
	}

	public void initSliceRenderer() {
		int vShader = glCreateShader(GL_VERTEX_SHADER);
		glShaderSource(vShader, sliceVertexShaderSource);
		glCompileShader(vShader);

		int fShader = glCreateShader(GL_FRAGMENT_SHADER);
		glShaderSource(fShader, sliceFragmentShaderSource);
		glCompileShader(fShader);

		sliceShaderProgram = glCreateProgram();
		glAttachShader(sliceShaderProgram, vShader);
		glAttachShader(sliceShaderProgram, fShader);
		glLinkProgram(sliceShaderProgram);

		glDeleteShader(vShader);
		glDeleteShader(fShader);

		sliceMvpLoc = glGetUniformLocation(sliceShaderProgram, "mvp");
		sliceTexLoc = glGetUniformLocation(sliceShaderProgram, "volumeTex");
		sliceLutLoc = glGetUniformLocation(sliceShaderProgram, "uLutTex");
		sliceWinCenterLoc = glGetUniformLocation(sliceShaderProgram, "uWinCenter");
		sliceWinWidthLoc = glGetUniformLocation(sliceShaderProgram, "uWinWidth");
		sliceModelLoc = glGetUniformLocation(sliceShaderProgram, "model");

		sliceVaoId = glGenVertexArrays();
		glBindVertexArray(sliceVaoId);

		sliceVboId = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, sliceVboId);

		org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
		java.nio.FloatBuffer fb = stack.mallocFloat(quadVertices.length);
		fb.put(quadVertices).flip();
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
		stack.pop();

		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(0);

		glBindVertexArray(0);
	}

	private void compileShaders() {
		int vShader = glCreateShader(GL_VERTEX_SHADER);
		glShaderSource(vShader, vertexShaderSource);
		glCompileShader(vShader);
		if (glGetShaderi(vShader, GL_COMPILE_STATUS) == GL_FALSE) {
			System.err.println("Vertex Shader Error: " + glGetShaderInfoLog(vShader));
		}

		int fShader = glCreateShader(GL_FRAGMENT_SHADER);
		glShaderSource(fShader, fragmentShaderSource);
		glCompileShader(fShader);
		if (glGetShaderi(fShader, GL_COMPILE_STATUS) == GL_FALSE) {
			System.err.println("Fragment Shader Error: " + glGetShaderInfoLog(fShader));
		}

		shaderProgram = glCreateProgram();
		glAttachShader(shaderProgram, vShader);
		glAttachShader(shaderProgram, fShader);
		glLinkProgram(shaderProgram);
		if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
			System.err.println("Link Error: " + glGetProgramInfoLog(shaderProgram));
		}

		glDeleteShader(vShader);
		glDeleteShader(fShader);

		mvpLoc = glGetUniformLocation(shaderProgram, "mvp");
		camLoc = glGetUniformLocation(shaderProgram, "cameraPos");
	}

	public void generateLUT(int type) {
		int size = 256;
		java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(size * 4);

		for (int i = 0; i < size; i++) {
			float t = (float) i / (size - 1);
			float r = 0, g = 0, b = 0, a = t;

			if (type == 0) {
				r = g = b = t;
			} else {
				r = Math.max(0, Math.min(1, Math.abs(t * 4 - 3) - 1));
				g = Math.max(0, Math.min(1, 2 - Math.abs(t * 4 - 2)));
				b = Math.max(0, Math.min(1, 2 - Math.abs(t * 4 - 1)));
				a = (t < 0.1f) ? 0.0f : t * 0.8f;
			}

			buffer.put((byte) (r * 255));
			buffer.put((byte) (g * 255));
			buffer.put((byte) (b * 255));
			buffer.put((byte) (a * 255));
		}
		buffer.flip();

		if (lutTextureId == -1)
			lutTextureId = glGenTextures();

		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_1D, lutTextureId);

		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);

		glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		org.lwjgl.system.MemoryUtil.memFree(buffer);
	}

	private void createCube() {
		vaoId = glGenVertexArrays();
		glBindVertexArray(vaoId);
		vboId = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboId);

		org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
		java.nio.FloatBuffer fb = stack.mallocFloat(cubeVertices.length);
		fb.put(cubeVertices).flip();
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
		stack.pop();

		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	public void setWindowLevel(float center, float width) {
		this.windowCenter = Math.max(0.0f, Math.min(1.0f, center));
		this.windowWidth = Math.max(0.001f, Math.min(2.0f, width));
	}

	public float getWindowCenter() {
		return windowCenter;
	}

	public float getWindowWidth() {
		return windowWidth;
	}

	public void setRenderMode(int mode) {
		this.currentRenderMode = mode;
	}

	public void loadLut(File lutFile) {
		try {
			IndexColorModel cm = LutLoader.open(lutFile.getAbsolutePath());

			if (cm == null) {
				System.err.println("Failed to load LUT: " + lutFile.getName());
				return;
			}

			int size = cm.getMapSize();
			byte[] r = new byte[size];
			byte[] g = new byte[size];
			byte[] b = new byte[size];

			cm.getReds(r);
			cm.getGreens(g);
			cm.getBlues(b);

			java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(size * 4);

			for (int i = 0; i < size; i++) {
				buffer.put(r[i]);
				buffer.put(g[i]);
				buffer.put(b[i]);
				byte alpha = (byte) i;
				buffer.put(alpha);
			}
			buffer.flip();

			uploadLutToGPU(buffer, size);
			MemoryUtil.memFree(buffer);
			System.out.println("Loaded LUT: " + lutFile.getName());

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void uploadLutToGPU(java.nio.ByteBuffer buffer, int size) {
		if (lutTextureId == -1)
			lutTextureId = glGenTextures();

		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_1D, lutTextureId);

		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);

		glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
	}

	public void render(org.joml.Matrix4f mvpMatrix, org.joml.Vector3f cameraPosLocal) {
		if (shaderProgram <= 0 || vaoId <= 0) {
			System.err.println("CRITICAL: Renderer not initialized! Shader=" + shaderProgram + " VAO=" + vaoId);
			return;
		}

		glUseProgram(shaderProgram);

		glUniform1f(minLoc, normalizedMin);
		glUniform1f(maxLoc, normalizedMax);
		glUniform1f(winCenterLoc, windowCenter);
		glUniform1f(winWidthLoc, windowWidth);

		glUniform1i(renderModeLoc, currentRenderMode);

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_3D, textureId);
		glUniform1i(glGetUniformLocation(shaderProgram, "volumeTex"), 0);

		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_1D, lutTextureId);
		glUniform1i(glGetUniformLocation(shaderProgram, "uLutTex"), 1);

		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			glUniformMatrix4fv(mvpLoc, false, mvpMatrix.get(stack.mallocFloat(16)));
			glUniform3f(camLoc, cameraPosLocal.x, cameraPosLocal.y, cameraPosLocal.z);
		}

		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		glEnable(GL_CULL_FACE);
		glCullFace(GL_FRONT);
		glFrontFace(GL_CCW);
		glDisable(GL_DEPTH_TEST);

		glBindVertexArray(vaoId);
		glDrawArrays(GL_TRIANGLES, 0, 36);

		glEnable(GL_DEPTH_TEST);
		glCullFace(GL_BACK);

		glBindVertexArray(0);
		glUseProgram(0);
	}

	public void renderOrthoSlices(org.joml.Matrix4f projViewMatrix, float xSlicePos, float ySlicePos, float zSlicePos) {
		if (sliceShaderProgram <= 0)
			return;

		glUseProgram(sliceShaderProgram);

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_3D, textureId);
		glUniform1i(sliceTexLoc, 0);

		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_1D, lutTextureId);
		glUniform1i(sliceLutLoc, 1);

		glUniform1f(sliceWinCenterLoc, windowCenter);
		glUniform1f(sliceWinWidthLoc, windowWidth);
		glUniform1f(glGetUniformLocation(sliceShaderProgram, "uMin"), normalizedMin);
		glUniform1f(glGetUniformLocation(sliceShaderProgram, "uMax"), normalizedMax);

		glBindVertexArray(sliceVaoId);

		glEnable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);

		org.joml.Matrix4f modelZ = new org.joml.Matrix4f().translate(0.0f, 0.0f, zSlicePos - 0.5f);

		drawQuad(projViewMatrix, modelZ);

		org.joml.Matrix4f modelY = new org.joml.Matrix4f().translate(0.0f, ySlicePos - 0.5f, 0.0f)
				.rotateX((float) Math.toRadians(90));

		drawQuad(projViewMatrix, modelY);

		org.joml.Matrix4f modelX = new org.joml.Matrix4f().translate(xSlicePos - 0.5f, 0.0f, 0.0f)
				.rotateY((float) Math.toRadians(-90));

		drawQuad(projViewMatrix, modelX);

		glBindVertexArray(0);
		glUseProgram(0);
	}

	private void drawQuad(org.joml.Matrix4f projView, org.joml.Matrix4f model) {
		org.joml.Matrix4f mvp = new org.joml.Matrix4f(projView).mul(model);

		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			glUniformMatrix4fv(sliceMvpLoc, false, mvp.get(stack.mallocFloat(16)));
			glUniformMatrix4fv(sliceModelLoc, false, model.get(stack.mallocFloat(16)));
		}

		glDrawArrays(GL_TRIANGLES, 0, 6);
	}

	// =========================================================
	// ★ 大幅改良: テクスチャのアップロード (複数データ型に対応)
	// =========================================================
	public void uploadTexture(VolumeData vol) {
		this.width = vol.width;
		this.height = vol.height;
		this.depth = vol.depth;

		// データのMin/Maxを、OpenGLの 0.0~1.0 の世界に変換して保存しておく
		switch (vol.dataType) {
		case BYTE:
			this.normalizedMin = vol.minVal / 255.0f;
			this.normalizedMax = vol.maxVal / 255.0f;
			break;
		case SHORT:
			this.normalizedMin = vol.minVal / 65535.0f;
			this.normalizedMax = vol.maxVal / 65535.0f;
			break;
		case FLOAT:
			// floatの場合はOpenGLが値をそのまま扱うため、生のMin/Maxを適用
			this.normalizedMin = vol.minVal;
			this.normalizedMax = vol.maxVal;
			break;
		case RGB:
			// RGBはカラーマップではなく、自身の色を持つため範囲は固定
			this.normalizedMin = 0.0f;
			this.normalizedMax = 1.0f;
			break;
		}

		// ゼロ除算対策（真っ黒な画像の場合など）
		if (Math.abs(normalizedMax - normalizedMin) < 0.0001f) {
			normalizedMax = normalizedMin + 0.0001f;
		}

		// --- A. 古いテクスチャがあれば削除 ---
		if (textureId != -1) {
			glDeleteTextures(textureId);
		}

		// --- B. テクスチャIDの生成 ---
		textureId = glGenTextures();
		glBindTexture(GL_TEXTURE_3D, textureId);

		// --- C. テクスチャパラメータの設定 ---
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
		
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

		// --- D & E. データの転送準備 (Java -> Native Memory) と転送 ---
		try {
			switch (vol.dataType) {
			case BYTE:
				java.nio.ByteBuffer bBuf = MemoryUtil.memAlloc(((byte[]) vol.data).length);
				bBuf.put((byte[]) vol.data).flip();
				glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, width, height, depth, 0, GL_RED, GL_UNSIGNED_BYTE, bBuf);
				MemoryUtil.memFree(bBuf);
				break;

			case SHORT:
				java.nio.ShortBuffer sBuf = MemoryUtil.memAllocShort(((short[]) vol.data).length);
				sBuf.put((short[]) vol.data).flip();
				glTexImage3D(GL_TEXTURE_3D, 0, GL_R16, width, height, depth, 0, GL_RED, GL_UNSIGNED_SHORT, sBuf);
				MemoryUtil.memFree(sBuf);
				break;

			case FLOAT:
				java.nio.FloatBuffer fBuf = MemoryUtil.memAllocFloat(((float[]) vol.data).length);
				fBuf.put((float[]) vol.data).flip();
				glTexImage3D(GL_TEXTURE_3D, 0, GL_R32F, width, height, depth, 0, GL_RED, GL_FLOAT, fBuf);
				MemoryUtil.memFree(fBuf);
				break;

			case RGB:
				java.nio.IntBuffer iBuf = MemoryUtil.memAllocInt(((int[]) vol.data).length);
				iBuf.put((int[]) vol.data).flip();
				// ImageJのint配列(ARGB)の仕様に合わせて、GL_BGRAとGL_UNSIGNED_INT_8_8_8_8_REVで転送
				glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA8, width, height, depth, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV,
						iBuf);
				MemoryUtil.memFree(iBuf);
				break;
			}

			System.out.println("Texture Uploaded: ID=" + textureId + " (" + width + "x" + height + "x" + depth
					+ ") DataType=" + vol.dataType);

		} catch (Exception e) {
			e.printStackTrace();
		}

		glBindTexture(GL_TEXTURE_3D, 0); // バインド解除
	}

	public void cleanup() {
		if (textureId != -1)
			glDeleteTextures(textureId);
	}

	public int getTextureId() {
		return textureId;
	}
}