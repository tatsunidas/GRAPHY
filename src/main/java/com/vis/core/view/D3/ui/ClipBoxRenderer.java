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
 * 3D裁断（クリッピング）モードのバウンディングボックスをOpenGLで描画するクラス。
 *
 * ボリュームと同じローカル単位立方体空間（mvp = proj*view*model）で、
 * {@code clipMin}〜{@code clipMax} で定義される直方体を描く:
 * <ul>
 * <li>6つの境界面を半透明（{@link #FACE_ALPHA}）でブレンド描画する（要件「境界面を若干透明に」）。</li>
 * <li>12本の辺を不透明なラインで描画する。</li>
 * <li>マウスで操作中の面（{@code activeFace}）はハイライト色で強調する。</li>
 * </ul>
 *
 * {@link AxesGizmo}/{@link CutLineRenderer} と同じく、頂点バッファは毎フレーム
 * {@code clipMin/clipMax} から再構築してアップロードする（ボックスは小さいので安価）。
 *
 * @author tatsunidas
 */
public class ClipBoxRenderer {

	private int programId;
	private int faceVao, faceVbo;
	private int edgeVao, edgeVbo;

	private int mvpLoc, colorLoc;

	/** 境界面の不透明度。控えめにして内部のボリュームが透けて見えるようにする。 */
	private static final float FACE_ALPHA = 0.12f;

	private static final String VERT = "#version 330 core\n"
			+ "layout(location=0) in vec3 aPos;\n"
			+ "uniform mat4 mvp;\n"
			+ "void main(){\n"
			+ "    gl_Position = mvp * vec4(aPos, 1.0);\n"
			+ "}";

	private static final String FRAG = "#version 330 core\n"
			+ "uniform vec4 uColor;\n"
			+ "out vec4 FragColor;\n"
			+ "void main(){\n"
			+ "    FragColor = uColor;\n"
			+ "}";

	// 8つの頂点を min/max から組み立てるためのインデックス（0=min, 1=max を各軸で選択）
	// p0=(min,min,min) p1=(max,min,min) p2=(max,max,min) p3=(min,max,min)
	// p4=(min,min,max) p5=(max,min,max) p6=(max,max,max) p7=(min,max,max)
	private static final int[][] CORNER_SEL = {
			{ 0, 0, 0 }, { 1, 0, 0 }, { 1, 1, 0 }, { 0, 1, 0 },
			{ 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 }, { 0, 1, 1 } };

	// 6面（四角形）の頂点インデックス。面の並びは ClipBoxInteractor の faceId 規約に一致させる:
	// 0:X- 1:X+ 2:Y- 3:Y+ 4:Z- 5:Z+
	private static final int[][] FACE_QUADS = {
			{ 0, 3, 7, 4 }, // X-
			{ 1, 2, 6, 5 }, // X+
			{ 0, 1, 5, 4 }, // Y-
			{ 3, 2, 6, 7 }, // Y+
			{ 0, 1, 2, 3 }, // Z-
			{ 4, 5, 6, 7 } }; // Z+

	// 12辺の頂点インデックス
	private static final int[][] EDGES = {
			{ 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, // 底面(z=min)
			{ 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, // 上面(z=max)
			{ 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } }; // 垂直辺

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

		// 面用バッファ（6面 * 2三角形 * 3頂点 = 36頂点）
		faceVao = glGenVertexArrays();
		glBindVertexArray(faceVao);
		faceVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, faceVbo);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);

		// 辺用バッファ（12辺 * 2頂点 = 24頂点）
		edgeVao = glGenVertexArrays();
		glBindVertexArray(edgeVao);
		edgeVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, edgeVbo);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);

		glBindVertexArray(0);
	}

	private static float corner(Vector3f min, Vector3f max, int axis, int sel) {
		float v = (sel == 0) ? component(min, axis) : component(max, axis);
		return v;
	}

	private static float component(Vector3f v, int axis) {
		return axis == 0 ? v.x : (axis == 1 ? v.y : v.z);
	}

	/**
	 * バウンディングボックスを描画する。
	 *
	 * @param mvp        proj*view*model（ボリュームと同じ行列）
	 * @param clipMin    領域下限（ローカル空間 -0.5〜0.5）
	 * @param clipMax    領域上限（ローカル空間 -0.5〜0.5）
	 * @param activeFace 操作中の面ID(0..5)。無ければ -1。ハイライト描画される。
	 */
	public void render(Matrix4f mvp, Vector3f clipMin, Vector3f clipMax, int activeFace) {
		if (programId <= 0)
			return;

		// 8頂点を構築
		float[][] p = new float[8][3];
		for (int i = 0; i < 8; i++) {
			p[i][0] = corner(clipMin, clipMax, 0, CORNER_SEL[i][0]);
			p[i][1] = corner(clipMin, clipMax, 1, CORNER_SEL[i][1]);
			p[i][2] = corner(clipMin, clipMax, 2, CORNER_SEL[i][2]);
		}

		glUseProgram(programId);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			glUniformMatrix4fv(mvpLoc, false, mvp.get(stack.mallocFloat(16)));
		}

		boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
		boolean blendWasEnabled = glIsEnabled(GL_BLEND);
		boolean cullWasEnabled = glIsEnabled(GL_CULL_FACE);

		// --- 面（半透明）。深度書き込みはせず、内部のボリュームを透かす ---
		// フェイスカリングを無効化して全6面を必ず描画する（面ごとに頂点の巻き順が異なるため、
		// カリングが有効だと一部の面が裏面扱いで描画されず色がつかないことがある）。
		glDisable(GL_CULL_FACE);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDisable(GL_DEPTH_TEST);
		glDepthMask(false);

		glBindVertexArray(faceVao);
		glBindBuffer(GL_ARRAY_BUFFER, faceVbo);
		for (int f = 0; f < 6; f++) {
			int[] q = FACE_QUADS[f];
			float[] verts = {
					p[q[0]][0], p[q[0]][1], p[q[0]][2],
					p[q[1]][0], p[q[1]][1], p[q[1]][2],
					p[q[2]][0], p[q[2]][1], p[q[2]][2],
					p[q[0]][0], p[q[0]][1], p[q[0]][2],
					p[q[2]][0], p[q[2]][1], p[q[2]][2],
					p[q[3]][0], p[q[3]][1], p[q[3]][2] };
			try (MemoryStack stack = MemoryStack.stackPush()) {
				FloatBuffer fb = stack.mallocFloat(verts.length);
				fb.put(verts).flip();
				glBufferData(GL_ARRAY_BUFFER, fb, GL_DYNAMIC_DRAW);
			}
			if (f == activeFace) {
				glUniform4f(colorLoc, 1.0f, 0.85f, 0.2f, FACE_ALPHA * 2.5f); // ハイライト（黄）
			} else {
				glUniform4f(colorLoc, 0.3f, 0.7f, 1.0f, FACE_ALPHA); // 通常（水色）
			}
			glDrawArrays(GL_TRIANGLES, 0, 6);
		}

		glDepthMask(true);

		// --- 辺（不透明ライン、最前面） ---
		float[] edgeVerts = new float[EDGES.length * 2 * 3];
		int idx = 0;
		for (int[] e : EDGES) {
			for (int vi : e) {
				edgeVerts[idx++] = p[vi][0];
				edgeVerts[idx++] = p[vi][1];
				edgeVerts[idx++] = p[vi][2];
			}
		}
		glBindVertexArray(edgeVao);
		glBindBuffer(GL_ARRAY_BUFFER, edgeVbo);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer fb = stack.mallocFloat(edgeVerts.length);
			fb.put(edgeVerts).flip();
			glBufferData(GL_ARRAY_BUFFER, fb, GL_DYNAMIC_DRAW);
		}
		glUniform4f(colorLoc, 1.0f, 1.0f, 1.0f, 1.0f); // 白
		glLineWidth(2.0f);
		glDrawArrays(GL_LINES, 0, EDGES.length * 2);
		glLineWidth(1.0f);

		glBindVertexArray(0);
		glUseProgram(0);

		// 状態を元に戻す
		if (depthWasEnabled)
			glEnable(GL_DEPTH_TEST);
		else
			glDisable(GL_DEPTH_TEST);
		if (!blendWasEnabled)
			glDisable(GL_BLEND);
		if (cullWasEnabled)
			glEnable(GL_CULL_FACE);
		else
			glDisable(GL_CULL_FACE);
	}
}
