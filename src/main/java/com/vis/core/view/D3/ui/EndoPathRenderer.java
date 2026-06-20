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
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.vis.core.view.D3.endo.EndoCamera;
import com.vis.core.view.D3.endo.EndoPath3D;

/**
 * {@link EndoPath3D}の3D可視化（補間後の曲線＋制御点）をOpenGLで描画する。
 *
 * @author tatsunidas
 */
public class EndoPathRenderer {

	private static final int SAMPLES_PER_SEGMENT = 20;
	private static final Vector3f CURVE_COLOR = new Vector3f(0f, 1f, 1f); // シアン
	private static final Vector3f POINT_COLOR = new Vector3f(1f, 1f, 0f); // 黄
	private static final Vector3f SELECTED_POINT_COLOR = new Vector3f(1f, 0.4f, 0f); // オレンジ
	private static final Vector3f CAMERA_MARKER_COLOR = new Vector3f(1f, 0f, 1f); // マゼンタ
	private static final float CAMERA_MARKER_LENGTH = 0.08f;

	private int programId;
	private int mvpLoc, colorLoc;

	private int curveVao, curveVbo;
	private int pointVao, pointVbo;

	private static final String VERT = "#version 330 core\n"
			+ "layout(location=0) in vec3 aPos;\n"
			+ "uniform mat4 mvp;\n"
			+ "void main(){\n"
			+ "    gl_Position = mvp * vec4(aPos, 1.0);\n"
			+ "}";

	private static final String FRAG = "#version 330 core\n"
			+ "uniform vec3 uColor;\n"
			+ "out vec4 FragColor;\n"
			+ "void main(){\n"
			+ "    FragColor = vec4(uColor, 1.0);\n"
			+ "}";

	public void init() {
		int vShader = glCreateShader(GL_VERTEX_SHADER);
		glShaderSource(vShader, VERT);
		glCompileShader(vShader);

		int fShader = glCreateShader(GL_FRAGMENT_SHADER);
		glShaderSource(fShader, FRAG);
		glCompileShader(fShader);

		programId = glCreateProgram();
		glAttachShader(programId, vShader);
		glAttachShader(programId, fShader);
		glLinkProgram(programId);

		glDeleteShader(vShader);
		glDeleteShader(fShader);

		mvpLoc = glGetUniformLocation(programId, "mvp");
		colorLoc = glGetUniformLocation(programId, "uColor");

		curveVao = glGenVertexArrays();
		curveVbo = setupVao(curveVao);

		pointVao = glGenVertexArrays();
		pointVbo = setupVao(pointVao);
	}

	private int setupVao(int vao) {
		glBindVertexArray(vao);
		int vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);
		glBindVertexArray(0);
		return vbo;
	}

	/**
	 * パスの曲線（補間後）と制御点を描画する。深度テストの状態は変更しない
	 * （呼び出し時点で有効になっているはずで、ボリューム/メッシュとの自然な前後関係を活かす）。
	 */
	public void render(EndoPath3D path, Matrix4f mvp) {
		render(path, mvp, -1);
	}

	/** selectedIndex(0以上)を指定すると、その制御点だけ別色・大きめのサイズで強調表示する */
	public void render(EndoPath3D path, Matrix4f mvp, int selectedIndex) {
		if (programId <= 0 || path.isEmpty()) {
			return;
		}

		glUseProgram(programId);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			glUniformMatrix4fv(mvpLoc, false, mvp.get(stack.mallocFloat(16)));
		}

		if (path.size() >= 2) {
			float[] curvePoints = sampleCurve(path);
			uploadAndDrawHeap(curveVao, curveVbo, curvePoints, CURVE_COLOR, GL_LINE_STRIP);
		}

		float[] controlPoints = collectControlPoints(path);
		glPointSize(8f);
		uploadAndDrawStack(pointVao, pointVbo, controlPoints, POINT_COLOR, GL_POINTS);
		glPointSize(1f);

		if (selectedIndex >= 0 && selectedIndex < path.size()) {
			Vector3f p = path.getPoint(selectedIndex).getPosition();
			glPointSize(14f);
			uploadAndDrawStack(pointVao, pointVbo, new float[] { p.x, p.y, p.z }, SELECTED_POINT_COLOR, GL_POINTS);
			glPointSize(1f);
		}

		glUseProgram(0);
	}

	/**
	 * EndoCameraの現在位置・向きをローカルキューブ座標上にマーカーとして描画する
	 * （位置から接線方向への短い線＋やや大きめの点）。
	 * pathが空の場合は{@link EndoCamera#sampleLocal()}が例外を投げるため、呼び出し側で事前にガードすること。
	 */
	public void renderCameraMarker(EndoCamera camera, Matrix4f mvp) {
		if (programId <= 0) {
			return;
		}
		EndoPath3D.PathSample sample = camera.sampleLocal();

		glUseProgram(programId);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			glUniformMatrix4fv(mvpLoc, false, mvp.get(stack.mallocFloat(16)));
		}

		Vector3f tip = new Vector3f(sample.tangent).mul(CAMERA_MARKER_LENGTH).add(sample.position);
		float[] line = { sample.position.x, sample.position.y, sample.position.z, tip.x, tip.y, tip.z };
		uploadAndDrawStack(curveVao, curveVbo, line, CAMERA_MARKER_COLOR, GL_LINE_STRIP);

		glPointSize(11f);
		uploadAndDrawStack(pointVao, pointVbo,
				new float[] { sample.position.x, sample.position.y, sample.position.z }, CAMERA_MARKER_COLOR,
				GL_POINTS);
		glPointSize(1f);

		glUseProgram(0);
	}

	private float[] sampleCurve(EndoPath3D path) {
		int segmentCount = path.size() - 1;
		int sampleCount = segmentCount * SAMPLES_PER_SEGMENT + 1;
		float[] data = new float[sampleCount * 3];

		int idx = 0;
		for (int seg = 0; seg < segmentCount; seg++) {
			for (int k = 0; k < SAMPLES_PER_SEGMENT; k++) {
				float t = seg + (float) k / SAMPLES_PER_SEGMENT;
				Vector3f p = path.evaluatePosition(t);
				data[idx++] = p.x;
				data[idx++] = p.y;
				data[idx++] = p.z;
			}
		}
		Vector3f last = path.evaluatePosition((float) segmentCount);
		data[idx++] = last.x;
		data[idx++] = last.y;
		data[idx++] = last.z;
		return data;
	}

	private float[] collectControlPoints(EndoPath3D path) {
		float[] data = new float[path.size() * 3];
		int idx = 0;
		for (int i = 0; i < path.size(); i++) {
			Vector3f p = path.getPoint(i).getPosition();
			data[idx++] = p.x;
			data[idx++] = p.y;
			data[idx++] = p.z;
		}
		return data;
	}

	// 曲線サンプルはパスの長さに応じて数百〜数千要素になりうるため、MemoryStackではなくヒープを使う
	private void uploadAndDrawHeap(int vao, int vbo, float[] data, Vector3f color, int mode) {
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);

		FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
		buffer.put(data).flip();
		glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
		MemoryUtil.memFree(buffer);

		glUniform3f(colorLoc, color.x, color.y, color.z);
		glDrawArrays(mode, 0, data.length / 3);
		glBindVertexArray(0);
	}

	// 制御点は数が少ない想定なので、MemoryStackで十分
	private void uploadAndDrawStack(int vao, int vbo, float[] data, Vector3f color, int mode) {
		if (data.length == 0) {
			return;
		}
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.mallocFloat(data.length);
			buffer.put(data).flip();
			glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
		}
		glUniform3f(colorLoc, color.x, color.y, color.z);
		glDrawArrays(mode, 0, data.length / 3);
		glBindVertexArray(0);
	}
}
