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

import java.awt.Point;
import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.system.MemoryStack;

/**
 * カット操作中の輪郭線をOpenGLのライン描画として表示するクラス。
 *
 * GLCanvasは60FPSのTimerからAWTGLCanvas#render()を直接呼ぶ構成になっており、その経路は
 * Swingのpaint(Graphics)を経由しない。そのため輪郭線をGraphics2Dで描いても次のTimer Tickの
 * render()で即座に上書きされ表示されない。paintGL()内のGL描画として線を出すことで、
 * render()がTimerと通常のrepaint()のどちらから呼ばれても確実に表示されるようにする。
 *
 * @author tatsunidas
 */
public class CutLineRenderer {

	private int programId;
	private int vaoId, vboId;
	private int uploadedPointCount = 0;

	private static final String VERT = "#version 330 core\n"
			+ "layout(location=0) in vec2 aPos;\n" // 呼び出し側で計算済みのNDC座標 (-1.0 ~ 1.0)
			+ "void main(){\n"
			+ "    gl_Position = vec4(aPos, 0.0, 1.0);\n"
			+ "}";

	private static final String FRAG = "#version 330 core\n"
			+ "out vec4 FragColor;\n"
			+ "void main(){\n"
			+ "    FragColor = vec4(1.0, 1.0, 0.0, 1.0);\n" // 元のGraphics版と同じ黄色
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

		vaoId = glGenVertexArrays();
		glBindVertexArray(vaoId);

		vboId = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboId);
		glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);

		glBindVertexArray(0);
	}

	// マウスドラッグで記録した画面座標(AWTピクセル, 原点左上, Y下向き)をNDCに変換してGPUへ転送する
	private void upload(List<Point> screenPoints, int canvasWidth, int canvasHeight) {
		int n = screenPoints.size();
		float[] ndc = new float[n * 2];
		for (int i = 0; i < n; i++) {
			Point p = screenPoints.get(i);
			ndc[i * 2] = (p.x / (float) canvasWidth) * 2.0f - 1.0f;
			ndc[i * 2 + 1] = 1.0f - (p.y / (float) canvasHeight) * 2.0f;
		}

		glBindVertexArray(vaoId);
		glBindBuffer(GL_ARRAY_BUFFER, vboId);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buffer = stack.mallocFloat(ndc.length);
			buffer.put(ndc).flip();
			glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
		}
		uploadedPointCount = n;
		glBindVertexArray(0);
	}

	/**
	 * カット中の輪郭線を、現在のビューポート全体に最前面で描画する。
	 */
	public void render(List<Point> screenPoints, int canvasWidth, int canvasHeight) {
		if (programId <= 0 || screenPoints.size() < 2 || canvasWidth <= 0 || canvasHeight <= 0)
			return;

		upload(screenPoints, canvasWidth, canvasHeight);

		boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
		glDisable(GL_DEPTH_TEST);

		glUseProgram(programId);
		glLineWidth(2.0f);
		glBindVertexArray(vaoId);
		glDrawArrays(GL_LINE_LOOP, 0, uploadedPointCount); // 閉じた輪郭として描画
		glBindVertexArray(0);
		glUseProgram(0);
		glLineWidth(1.0f);

		if (depthWasEnabled)
			glEnable(GL_DEPTH_TEST);
	}
}
