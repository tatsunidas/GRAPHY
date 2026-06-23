/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.vis.core.centerline.CenterlineBranch;
import com.vis.core.centerline.CenterlineGraph;
import com.vis.core.centerline.CenterlineNode;
import com.vis.core.slicer.Centerline3D;
import com.vis.core.slicer.VolumeSampler;

/**
 * Draws a {@link CenterlineGraph} (skeleton branches + endpoint/bifurcation
 * nodes) over the volume/mesh already shown in {@link GLCanvas}, mirroring
 * {@link EndoPathRenderer}'s GL pattern.
 *
 * The graph's geometry lives in physical (LPS, mm) coordinates, decoupled
 * from any particular volume - so every point is converted through
 * {@link VolumeSampler#toLocalRenderSpace} (using a sampler built from the
 * volume currently shown in the canvas) before upload, to land in the same
 * coordinate space the canvas's model matrix already assumes for meshes.
 */
public class CenterlineGraphRenderer {

	private static final double SAMPLE_STEP_MM = 1.0;

	private static final Vector3f BRANCH_COLOR = new Vector3f(0f, 1f, 0.4f);
	private static final Vector3f SELECTED_BRANCH_COLOR = new Vector3f(1f, 0.15f, 0.15f);
	private static final Vector3f LEAF_NODE_COLOR = new Vector3f(1f, 1f, 0f);
	private static final Vector3f BRANCH_POINT_COLOR = new Vector3f(0.2f, 0.6f, 1f);
	private static final Vector3f SELECTED_NODE_COLOR = new Vector3f(1f, 0.4f, 0f);
	private static final Vector3f LIVE_CURVE_COLOR = new Vector3f(1f, 1f, 1f); // white, drawn thick/on top

	private int programId;
	private int mvpLoc, colorLoc;
	private int lineVao, lineVbo;
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

		lineVao = glGenVertexArrays();
		lineVbo = setupVao(lineVao);

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

	public void render(CenterlineGraph graph, VolumeSampler sampler, Matrix4f mvp, Set<Integer> selectedBranchIds,
			Set<Integer> selectedNodeIds) {
		render(graph, sampler, mvp, selectedBranchIds, selectedNodeIds, null);
	}

	/**
	 * @param liveCurve the currently selected/extracted curve (a single
	 *        branch's curve, or a multi-branch path concatenated by
	 *        {@link CenterlineGraph#extractPath}) drawn prominently on top
	 *        of everything else, regardless of whether it corresponds to
	 *        one existing graph branch or several. May be null.
	 */
	public void render(CenterlineGraph graph, VolumeSampler sampler, Matrix4f mvp, Set<Integer> selectedBranchIds,
			Set<Integer> selectedNodeIds, Centerline3D liveCurve) {
		render(graph, sampler, mvp, selectedBranchIds, selectedNodeIds, liveCurve, LIVE_CURVE_COLOR);
	}

	/**
	 * Same as {@link #render(CenterlineGraph, VolumeSampler, Matrix4f, Set, Set, Centerline3D)},
	 * but with an explicit {@code liveCurveColor} - needed when more than one
	 * "live curve" overlay can be on screen at once (e.g. GLCanvas keeps
	 * drawing the centerline an endoscopy path was loaded from, separately
	 * from whatever CenterlineAnalysisDialog currently has selected) and
	 * they'd otherwise be indistinguishable.
	 */
	public void render(CenterlineGraph graph, VolumeSampler sampler, Matrix4f mvp, Set<Integer> selectedBranchIds,
			Set<Integer> selectedNodeIds, Centerline3D liveCurve, Vector3f liveCurveColor) {
		if (programId <= 0 || sampler == null) {
			return;
		}

		glUseProgram(programId);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			glUniformMatrix4fv(mvpLoc, false, mvp.get(stack.mallocFloat(16)));
		}

		if (graph != null) {
			for (CenterlineBranch branch : graph.getBranches()) {
				float[] pts = sampleCurveLocal(branch.getCurve(), sampler);
				boolean selected = selectedBranchIds != null && selectedBranchIds.contains(branch.getId());
				uploadAndDrawHeap(lineVao, lineVbo, pts, selected ? SELECTED_BRANCH_COLOR : BRANCH_COLOR,
						GL_LINE_STRIP);
			}
		}

		if (liveCurve != null && liveCurve.size() >= 2) {
			glLineWidth(3f);
			float[] pts = sampleCurveLocal(liveCurve, sampler);
			uploadAndDrawHeap(lineVao, lineVbo, pts, liveCurveColor, GL_LINE_STRIP);
			glLineWidth(1f);
		}

		if (graph == null) {
			glUseProgram(0);
			return;
		}

		List<float[]> leafPts = new ArrayList<>();
		List<float[]> branchPtPts = new ArrayList<>();
		List<float[]> selectedPts = new ArrayList<>();
		for (CenterlineNode node : graph.getNodes()) {
			Vector3d local = sampler.toLocalRenderSpace(node.getPosition());
			float[] p = { (float) local.x, (float) local.y, (float) local.z };
			if (selectedNodeIds != null && selectedNodeIds.contains(node.getId())) {
				selectedPts.add(p);
			} else if (node.getDegree() == 1) {
				leafPts.add(p);
			} else if (node.getDegree() >= 3) {
				branchPtPts.add(p);
			}
		}

		glPointSize(8f);
		uploadAndDrawStack(pointVao, pointVbo, flatten(leafPts), LEAF_NODE_COLOR, GL_POINTS);
		uploadAndDrawStack(pointVao, pointVbo, flatten(branchPtPts), BRANCH_POINT_COLOR, GL_POINTS);
		glPointSize(13f);
		uploadAndDrawStack(pointVao, pointVbo, flatten(selectedPts), SELECTED_NODE_COLOR, GL_POINTS);
		glPointSize(1f);

		glUseProgram(0);
	}

	private float[] sampleCurveLocal(Centerline3D curve, VolumeSampler sampler) {
		double length = curve.getTotalLength();
		int samples = Math.max(2, (int) Math.ceil(length / SAMPLE_STEP_MM) + 1);
		float[] data = new float[samples * 3];
		for (int i = 0; i < samples; i++) {
			double s = (samples == 1) ? 0 : length * i / (samples - 1);
			Vector3d physical = curve.positionAt(s);
			Vector3d local = sampler.toLocalRenderSpace(physical);
			data[i * 3] = (float) local.x;
			data[i * 3 + 1] = (float) local.y;
			data[i * 3 + 2] = (float) local.z;
		}
		return data;
	}

	private static float[] flatten(List<float[]> points) {
		float[] out = new float[points.size() * 3];
		int idx = 0;
		for (float[] p : points) {
			out[idx++] = p[0];
			out[idx++] = p[1];
			out[idx++] = p[2];
		}
		return out;
	}

	// Branch polylines can have many samples for long branches; use the heap rather than MemoryStack.
	private void uploadAndDrawHeap(int vao, int vbo, float[] data, Vector3f color, int mode) {
		if (data.length == 0) return;
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

	// Node point counts are small; MemoryStack is fine.
	private void uploadAndDrawStack(int vao, int vbo, float[] data, Vector3f color, int mode) {
		if (data.length == 0) return;
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
