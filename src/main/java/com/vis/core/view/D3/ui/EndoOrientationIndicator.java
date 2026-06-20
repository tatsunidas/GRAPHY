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

/**
 * 内視鏡モード中、画面の固定コーナーに「ワールドの上方向(0,1,0)が現在の視点でどちらを向いているか」を
 * 示す矢印を描画するミニ方位インジケーター。{@link CutLineRenderer}と同じ、呼び出し側で計算済みのNDC座標
 * をそのまま渡すだけの最小シェーダーのパターンを使う。
 *
 * @author tatsunidas
 */
public class EndoOrientationIndicator {

	private static final Vector3f UP_COLOR = new Vector3f(0.2f, 1f, 0.2f); // AxesGizmoのY軸(緑)と同じ配色

	private int programId;
	private int colorLoc;
	private int vaoId, vboId;

	private static final String VERT = "#version 330 core\n"
			+ "layout(location=0) in vec2 aPos;\n" // 呼び出し側で計算済みのNDC座標 (-1.0 ~ 1.0)
			+ "void main(){\n"
			+ "    gl_Position = vec4(aPos, 0.0, 1.0);\n"
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

		colorLoc = glGetUniformLocation(programId, "uColor");

		vaoId = glGenVertexArrays();
		glBindVertexArray(vaoId);

		vboId = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboId);
		glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);

		glBindVertexArray(0);
	}

	/**
	 * ワールド空間の上方向(0,1,0)が現在のview行列でどちらを向いているかを、画面左下の固定コーナーに矢印として描画する。
	 *
	 * @param view 現在使用中のビュー行列（内視鏡モード中のみ呼び出される想定）
	 * @param canvasWidth 物理ピクセル幅（{@code axesGizmo}と同じくphysWを渡す）
	 * @param canvasHeight 物理ピクセル高さ（{@code axesGizmo}と同じくphysHを渡す）
	 */
	public void render(Matrix4f view, int canvasWidth, int canvasHeight) {
		if (programId <= 0 || canvasWidth <= 0 || canvasHeight <= 0) {
			return;
		}

		// 1. ワールドの上方向をview行列の回転成分だけで変換し、画面上の2D方向を求める
		Vector3f upInView = view.transformDirection(new Vector3f(0f, 1f, 0f));
		float dx = upInView.x;
		float dy = upInView.y;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-6f) {
			dx = 0f;
			dy = 1f;
		} else {
			dx /= len;
			dy /= len;
		}

		// 2. ピクセル空間（数学座標、Y上向き）で矢印の頂点を構築する
		float centerPx = 70f;
		float centerPyFromBottom = 70f;
		float radius = 26f;
		float barbLen = 9f;

		float tipX = dx * radius;
		float tipY = dy * radius;
		float backX = dx * radius * 0.7f;
		float backY = dy * radius * 0.7f;
		float perpX = -dy; // (dx,dy)を90度回転した方向
		float perpY = dx;
		float barb1X = backX + perpX * barbLen;
		float barb1Y = backY + perpY * barbLen;
		float barb2X = backX - perpX * barbLen;
		float barb2Y = backY - perpY * barbLen;

		// 数学座標(ox,oy) -> ピクセル(AWT座標、Y下向き) -> NDC
		float[] ndc = new float[12]; // center->tip, tip->barb1, tip->barb2 の3線分・6頂点
		toNdc(ndc, 0, 0f, 0f, centerPx, centerPyFromBottom, canvasWidth, canvasHeight);
		toNdc(ndc, 2, tipX, tipY, centerPx, centerPyFromBottom, canvasWidth, canvasHeight);
		toNdc(ndc, 4, tipX, tipY, centerPx, centerPyFromBottom, canvasWidth, canvasHeight);
		toNdc(ndc, 6, barb1X, barb1Y, centerPx, centerPyFromBottom, canvasWidth, canvasHeight);
		toNdc(ndc, 8, tipX, tipY, centerPx, centerPyFromBottom, canvasWidth, canvasHeight);
		toNdc(ndc, 10, barb2X, barb2Y, centerPx, centerPyFromBottom, canvasWidth, canvasHeight);

		glBindVertexArray(vaoId);
		glBindBuffer(GL_ARRAY_BUFFER, vboId);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.mallocFloat(ndc.length);
			buffer.put(ndc).flip();
			glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
		}

		boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
		glDisable(GL_DEPTH_TEST);

		glUseProgram(programId);
		glUniform3f(colorLoc, UP_COLOR.x, UP_COLOR.y, UP_COLOR.z);
		glLineWidth(2.0f);
		glDrawArrays(GL_LINES, 0, ndc.length / 2);
		glLineWidth(1.0f);
		glUseProgram(0);
		glBindVertexArray(0);

		if (depthWasEnabled) {
			glEnable(GL_DEPTH_TEST);
		}
	}

	// 数学座標(ox,oy)（centerからの相対オフセット, Y上向き）をNDCに変換し、destの指定位置に書き込む
	private void toNdc(float[] dest, int offset, float ox, float oy, float centerPx, float centerPyFromBottom,
			int canvasWidth, int canvasHeight) {
		float px = centerPx + ox;
		float py = (canvasHeight - centerPyFromBottom) - oy; // AWT座標(Y下向き)へ変換
		dest[offset] = (px / (float) canvasWidth) * 2.0f - 1.0f;
		dest[offset + 1] = 1.0f - (py / (float) canvasHeight) * 2.0f;
	}
}
