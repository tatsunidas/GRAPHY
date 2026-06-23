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
	private int lutGeneration = 0;

	private int sliceShaderProgram = -1;
	private int sliceVaoId, sliceVboId;
	private int sliceMvpLoc, sliceTexLoc, sliceLutLoc;
	private int sliceWinCenterLoc, sliceWinWidthLoc;
	private int sliceModelLoc;
	
	private int roiTextureId = -1;
	private int showVolumeLoc, showRoiLoc;
	
	private boolean isVolumeVisible = true;
	private boolean isRoiVisible = true;
	private static final int MAX_ROIS = 32;
	private float[] roiColorsArray = new float[MAX_ROIS * 4];
	
	private int sliceShowRoiLoc, sliceRoiColorsLoc, sliceRoiTexLoc;
	private boolean orthoShowRoi = true; // 断面表示時にROIを重ねるかどうかのフラグ
	
	public void init() {
		compileShaders();
		createCube();

		renderModeLoc = glGetUniformLocation(shaderProgram, "uRenderMode");
		minLoc = glGetUniformLocation(shaderProgram, "uMin");
		maxLoc = glGetUniformLocation(shaderProgram, "uMax");
		winCenterLoc = glGetUniformLocation(shaderProgram, "uWinCenter");
		winWidthLoc = glGetUniformLocation(shaderProgram, "uWinWidth");
		
		showVolumeLoc = glGetUniformLocation(shaderProgram, "uShowVolume");
		showRoiLoc = glGetUniformLocation(shaderProgram, "uShowRoi");

		generateLUT(0);
	}

	public void initSliceRenderer() {
		
		String sliceVertexShaderSource = ShaderUtils.loadShaderAsString("/shaders/slice.vert");
	    String sliceFragmentShaderSource = ShaderUtils.loadShaderAsString("/shaders/slice.frag");
		
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
		
		sliceShowRoiLoc = glGetUniformLocation(sliceShaderProgram, "uShowRoi");
		sliceRoiColorsLoc = glGetUniformLocation(sliceShaderProgram, "uRoiColors");
		sliceRoiTexLoc = glGetUniformLocation(sliceShaderProgram, "roiTex");

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
		
		String vertexShaderSource = ShaderUtils.loadShaderAsString("/shaders/volume.vert");
	    String fragmentShaderSource = ShaderUtils.loadShaderAsString("/shaders/volume.frag");
		
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

	private static final int LUT_SIZE = 256;
	/** Color part of the LUT (RGB only), set by generateLUT()/applyLut(). */
	private byte[] currentLutRgb = defaultGrayscaleRgb();
	/**
	 * Opacity part of the LUT, set independently by applyOpacityCurve() so
	 * picking a different color map never discards a manually tuned opacity
	 * curve, and vice versa. Defaults to a plain linear ramp (same shape the
	 * old combined grayscale LUT used).
	 */
	private byte[] currentOpacity = defaultLinearOpacity();

	private static byte[] defaultGrayscaleRgb() {
		byte[] rgb = new byte[LUT_SIZE * 3];
		for (int i = 0; i < LUT_SIZE; i++) {
			byte v = (byte) i;
			rgb[i * 3] = v;
			rgb[i * 3 + 1] = v;
			rgb[i * 3 + 2] = v;
		}
		return rgb;
	}

	private static byte[] defaultLinearOpacity() {
		byte[] a = new byte[LUT_SIZE];
		for (int i = 0; i < LUT_SIZE; i++) {
			a[i] = (byte) i;
		}
		return a;
	}

	/**
	 * Switch to one of the built-in procedural color maps (0 = grayscale,
	 * anything else = the original hot/rainbow-style ramp). Only the RGB part
	 * changes - the current opacity curve (see applyOpacityCurve) is kept.
	 */
	public void generateLUT(int type) {
		for (int i = 0; i < LUT_SIZE; i++) {
			float t = (float) i / (LUT_SIZE - 1);
			float r, g, b;
			if (type == 0) {
				r = g = b = t;
			} else {
				r = Math.max(0, Math.min(1, Math.abs(t * 4 - 3) - 1));
				g = Math.max(0, Math.min(1, 2 - Math.abs(t * 4 - 2)));
				b = Math.max(0, Math.min(1, 2 - Math.abs(t * 4 - 1)));
			}
			currentLutRgb[i * 3] = (byte) (r * 255);
			currentLutRgb[i * 3 + 1] = (byte) (g * 255);
			currentLutRgb[i * 3 + 2] = (byte) (b * 255);
		}
		rebuildAndUploadLut();
	}

	/**
	 * Replace the opacity-vs-value curve (256 entries, 0-255) used across
	 * VR/MIP/Ortho, leaving the current color map untouched. Called by the
	 * volume opacity curve editor dialog.
	 */
	public void applyOpacityCurve(byte[] opacity256) {
		if (opacity256 == null || opacity256.length != LUT_SIZE) {
			throw new IllegalArgumentException("opacity curve must have exactly " + LUT_SIZE + " entries");
		}
		this.currentOpacity = opacity256;
		rebuildAndUploadLut();
	}

	public byte[] getCurrentOpacityCurve() {
		return currentOpacity.clone();
	}

	private void rebuildAndUploadLut() {
		java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(LUT_SIZE * 4);
		for (int i = 0; i < LUT_SIZE; i++) {
			buffer.put(currentLutRgb[i * 3]);
			buffer.put(currentLutRgb[i * 3 + 1]);
			buffer.put(currentLutRgb[i * 3 + 2]);
			buffer.put(currentOpacity[i]);
		}
		buffer.flip();
		uploadLutToGPU(buffer, LUT_SIZE);
		MemoryUtil.memFree(buffer);
		lutGeneration++;
	}

	/** Bumped every time the color map and/or opacity curve changes; lets callers detect a stale cache. */
	public int getLutGeneration() {
		return lutGeneration;
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
	
	public void setVolumeVisible(boolean visible) {
	    this.isVolumeVisible = visible;
	}

	public void setRoiVisible(boolean visible) {
	    this.isRoiVisible = visible;
	}

	public void setRoiColors(java.util.List<java.awt.Color> colors, float alpha) {
	    java.util.Arrays.fill(roiColorsArray, 0.0f);
	    
	    // [0]は空気用なので空け、[1]から格納する
	    for (int i = 0; i < colors.size() && i < (MAX_ROIS - 1); i++) {
	        java.awt.Color c = colors.get(i);
	        int offset = (i + 1) * 4; 
	        roiColorsArray[offset + 0] = c.getRed() / 255.0f;
	        roiColorsArray[offset + 1] = c.getGreen() / 255.0f;
	        roiColorsArray[offset + 2] = c.getBlue() / 255.0f;
	        roiColorsArray[offset + 3] = alpha;
	    }
	}
	
	// ★追加: フラグのセッター
	public void setOrthoShowRoi(boolean show) {
		this.orthoShowRoi = show;
	}

	public void loadLut(File lutFile) {
		try {
			IndexColorModel cm = LutLoader.open(lutFile.getAbsolutePath());
			if (cm == null) {
				System.err.println("Failed to load LUT: " + lutFile.getName());
				return;
			}
			applyLut(cm);
			System.out.println("Loaded LUT: " + lutFile.getName());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Upload an already-resolved color model (e.g. ij.process.LUT, which extends
	 * IndexColorModel) directly to the GPU, without going through a file. Used
	 * by the UI's color map picker, which resolves named LUTs (including the
	 * built-in bundled .lut files under luts/) via Resources.loadLUT(name).
	 */
	public void applyLut(IndexColorModel cm) {
		int size = cm.getMapSize();
		if (size != LUT_SIZE) {
			// Bundled .lut files and ij.process.LUT are always 256 entries;
			// guard anyway so a malformed file can't corrupt currentLutRgb.
			System.err.println("applyLut: unexpected LUT size " + size + ", expected " + LUT_SIZE);
			return;
		}
		byte[] r = new byte[size];
		byte[] g = new byte[size];
		byte[] b = new byte[size];

		cm.getReds(r);
		cm.getGreens(g);
		cm.getBlues(b);

		for (int i = 0; i < size; i++) {
			currentLutRgb[i * 3] = r[i];
			currentLutRgb[i * 3 + 1] = g[i];
			currentLutRgb[i * 3 + 2] = b[i];
		}
		rebuildAndUploadLut();
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

	public void render(org.joml.Matrix4f mvpMatrix, org.joml.Vector3f cameraPosLocal, boolean isEmbedded, float sX, float sY, float sZ) {
		if (shaderProgram <= 0 || vaoId <= 0) return;
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
		
		glUniform1i(glGetUniformLocation(shaderProgram, "uIsEmbedded"), isEmbedded ? 1 : 0);
		glUniform3f(glGetUniformLocation(shaderProgram, "uSlicePos"), sX - 0.5f, sY - 0.5f, sZ - 0.5f);
		
		// =======================================================
		// UIから設定された状態（表示/非表示フラグ）をシェーダーへ転送
		// =======================================================
		boolean actuallyShowRoi = isRoiVisible && (roiTextureId != -1);
		glUniform1i(showVolumeLoc, isVolumeVisible ? 1 : 0);
		glUniform1i(showRoiLoc, actuallyShowRoi ? 1 : 0);

		// =======================================================
		// ★ここが置き換わった部分：カラーパレット配列をGPUに送信
		// =======================================================
		int colorsArrayLoc = glGetUniformLocation(shaderProgram, "uRoiColors");
		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
		    java.nio.FloatBuffer fb = stack.mallocFloat(MAX_ROIS * 4);
		    fb.put(roiColorsArray).flip();
		    glUniform4fv(colorsArrayLoc, fb);
		}

		// =======================================================
		// ROIテクスチャのバインド (Texture Unit 2) [残す部分]
		// =======================================================
		glUniform1i(glGetUniformLocation(shaderProgram, "roiTex"), 2);
		if (roiTextureId != -1) {
		    glActiveTexture(GL_TEXTURE2);
		    glBindTexture(GL_TEXTURE_3D, roiTextureId);
		}

		// =======================================================
		// 3D投影行列とカメラ位置の送信 [絶対に消してはいけない部分！]
		// =======================================================
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
		// 不透明度カーブのアルファをスライス表示にも反映させる (VRモードと同様のブレンド)
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		boolean actuallyShowRoi = orthoShowRoi && (roiTextureId != -1);
		glUniform1i(sliceShowRoiLoc, actuallyShowRoi ? 1 : 0);

		if (actuallyShowRoi) {
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				java.nio.FloatBuffer fb = stack.mallocFloat(MAX_ROIS * 4);
				fb.put(roiColorsArray).flip();
				glUniform4fv(sliceRoiColorsLoc, fb);
			}
			glUniform1i(sliceRoiTexLoc, 2);
			glActiveTexture(GL_TEXTURE2);
			glBindTexture(GL_TEXTURE_3D, roiTextureId);
		}

		// X,Y,Zの板を描画する
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
	
	// ROIマスクをGPUへ転送するメソッド
	public void uploadRoiTexture(byte[] roiMask, int w, int h, int d) {
	    if (roiTextureId != -1) glDeleteTextures(roiTextureId);
	    
	    roiTextureId = glGenTextures();
	    glBindTexture(GL_TEXTURE_3D, roiTextureId);
	    
	    // ★修正: GL_LINEAR から GL_NEAREST に変更！ (ID同士が混ざるのを防ぐ)
	    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
	    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
	    
	    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	    glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
	    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

	    java.nio.ByteBuffer bBuf = MemoryUtil.memAlloc(roiMask.length);
	    bBuf.put(roiMask).flip();
	    glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, w, h, d, 0, GL_RED, GL_UNSIGNED_BYTE, bBuf);
	    MemoryUtil.memFree(bBuf);
	    
	    glBindTexture(GL_TEXTURE_3D, 0);
	}

	public void cleanup() {
		if (textureId != -1)
			glDeleteTextures(textureId);
	}

	public int getTextureId() {
		return textureId;
	}

	public int getLutTextureId() {
		return lutTextureId;
	}

	public float getNormalizedMin() {
		return normalizedMin;
	}

	public float getNormalizedMax() {
		return normalizedMax;
	}

	public boolean isVolumeVisible() {
		return isVolumeVisible;
	}
	
	public boolean isRoiVisible() {
		return isRoiVisible;
	}
}