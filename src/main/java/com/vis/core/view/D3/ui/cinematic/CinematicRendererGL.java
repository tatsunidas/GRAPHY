/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui.cinematic;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import com.vis.core.view.D3.ui.ShaderUtils;
import com.vis.core.view.D3.ui.VolumeRenderer;

/**
 * Always-available OpenGL implementation of {@link CinematicRenderer}. Runs
 * the {@code cinematic.frag} path tracer over the same unit cube
 * {@link VolumeRenderer} uses, additively blends one progressive step per
 * call into a floating-point accumulation FBO, then runs
 * {@code present.frag} to tonemap/average the accumulation buffer onto
 * whichever framebuffer the caller had bound (the canvas's default one).
 */
public class CinematicRendererGL implements CinematicRenderer {

	// Same unit cube as VolumeRenderer.cubeVertices - kept as our own copy so this
	// class doesn't depend on VolumeRenderer's private GL state, only its textures/uniforms.
	private final float[] cubeVertices = { -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
			-0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
			0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
			-0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
			-0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f,
			0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f,
			0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f };

	private final float[] quadVertices = { -1f, -1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, -1f, -1f };

	private int pathTraceProgram = -1;
	private int presentProgram = -1;

	private int cubeVao, cubeVbo;
	private int quadVao, quadVbo;

	private int accumFbo = -1;
	private int accumTex = -1;
	private int accumWidth, accumHeight;
	private int frameCount = 0;
	private int frameSeed = 0;

	@Override
	public void init() {
		pathTraceProgram = compileProgram(ShaderUtils.loadShaderAsString("/shaders/cinematic.vert"),
				ShaderUtils.loadShaderAsString("/shaders/cinematic.frag"));
		presentProgram = compileProgram(ShaderUtils.loadShaderAsString("/shaders/present.vert"),
				ShaderUtils.loadShaderAsString("/shaders/present.frag"));
		createCube();
		createQuad();
	}

	@Override
	public void resize(int width, int height) {
		if (width <= 0 || height <= 0) return;
		if (width == accumWidth && height == accumHeight && accumFbo != -1) return;

		accumWidth = width;
		accumHeight = height;

		if (accumTex != -1) glDeleteTextures(accumTex);
		if (accumFbo != -1) glDeleteFramebuffers(accumFbo);

		accumTex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, accumTex);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
		glBindTexture(GL_TEXTURE_2D, 0);

		accumFbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, accumFbo);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, accumTex, 0);
		int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (status != GL_FRAMEBUFFER_COMPLETE) {
			System.err.println("CinematicRendererGL: accumulation FBO incomplete, status=" + status);
		}
		glBindFramebuffer(GL_FRAMEBUFFER, 0);

		frameCount = 0;
	}

	@Override
	public void invalidateAccumulation() {
		frameCount = 0;
		if (accumFbo == -1) return;
		glBindFramebuffer(GL_FRAMEBUFFER, accumFbo);
		glClearColor(0f, 0f, 0f, 0f);
		glClear(GL_COLOR_BUFFER_BIT);
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}

	@Override
	public void render(Matrix4f mvp, Vector3f camPosLocal, VolumeRenderer volumeSource, CinematicParams params) {
		if (pathTraceProgram <= 0 || accumFbo == -1) return;

		// --- pass 1: accumulate one more progressive step into the float FBO ---
		glBindFramebuffer(GL_FRAMEBUFFER, accumFbo);
		glViewport(0, 0, accumWidth, accumHeight);
		glUseProgram(pathTraceProgram);

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_3D, volumeSource.getTextureId());
		glUniform1i(glGetUniformLocation(pathTraceProgram, "volumeTex"), 0);

		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_1D, volumeSource.getLutTextureId());
		glUniform1i(glGetUniformLocation(pathTraceProgram, "uLutTex"), 1);

		glUniform1f(glGetUniformLocation(pathTraceProgram, "uMin"), volumeSource.getNormalizedMin());
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uMax"), volumeSource.getNormalizedMax());
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uWinCenter"), volumeSource.getWindowCenter());
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uWinWidth"), volumeSource.getWindowWidth());

		// 3D裁断領域（VolumeRendererが保持する実効値。裁断OFF時は -0.5〜0.5 が入っている）
		float[] clipMin = volumeSource.getEffectiveClipMin();
		float[] clipMax = volumeSource.getEffectiveClipMax();
		glUniform3f(glGetUniformLocation(pathTraceProgram, "uClipMin"), clipMin[0], clipMin[1], clipMin[2]);
		glUniform3f(glGetUniformLocation(pathTraceProgram, "uClipMax"), clipMax[0], clipMax[1], clipMax[2]);

		float[] lightDir = lightDirectionFromAngles(params.lightAzimuth, params.lightElevation);
		glUniform3f(glGetUniformLocation(pathTraceProgram, "uLightDir"), lightDir[0], lightDir[1], lightDir[2]);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uLightIntensity"), params.lightIntensity);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uAmbientIntensity"), params.ambientIntensity);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uAnisotropy"), params.scatteringAnisotropy);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uLightAngularRadius"), params.lightAngularRadius);
		glUniform1i(glGetUniformLocation(pathTraceProgram, "uSamplesPerFrame"), Math.max(1, params.samplesPerFrame));
		glUniform1ui(glGetUniformLocation(pathTraceProgram, "uFrameSeed"), frameSeed);

		glUniform1f(glGetUniformLocation(pathTraceProgram, "uRoughness"), params.roughness);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uSpecular"), params.specular);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uMetallic"), params.metallic);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uClearcoat"), params.clearcoat);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uClearcoatRoughness"), params.clearcoatRoughness);
		glUniform1f(glGetUniformLocation(pathTraceProgram, "uSurfaceGradientThreshold"), params.surfaceGradientThreshold);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			glUniformMatrix4fv(glGetUniformLocation(pathTraceProgram, "mvp"), false, mvp.get(stack.mallocFloat(16)));
		}
		glUniform3f(glGetUniformLocation(pathTraceProgram, "cameraPos"), camPosLocal.x, camPosLocal.y,
				camPosLocal.z);

		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE);
		glEnable(GL_CULL_FACE);
		glCullFace(GL_FRONT); // same convention as VolumeRenderer.render(): only back faces rasterize, one fragment per pixel
		glFrontFace(GL_CCW);
		glDisable(GL_DEPTH_TEST);

		glBindVertexArray(cubeVao);
		glDrawArrays(GL_TRIANGLES, 0, 36);
		glBindVertexArray(0);

		glCullFace(GL_BACK);
		glDisable(GL_CULL_FACE);
		glDisable(GL_BLEND);

		frameCount++;
		frameSeed++;

		// --- pass 2: present (average by frame count + tonemap) onto the caller's framebuffer ---
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glViewport(0, 0, accumWidth, accumHeight);
		glUseProgram(presentProgram);

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, accumTex);
		glUniform1i(glGetUniformLocation(presentProgram, "uAccumTex"), 0);
		glUniform1f(glGetUniformLocation(presentProgram, "uFrameCount"), (float) frameCount);
		glUniform1f(glGetUniformLocation(presentProgram, "uExposure"), params.exposure);

		glDisable(GL_DEPTH_TEST);
		glBindVertexArray(quadVao);
		glDrawArrays(GL_TRIANGLES, 0, 6);
		glBindVertexArray(0);
		glUseProgram(0);
	}

	@Override
	public void dispose() {
		if (pathTraceProgram > 0) glDeleteProgram(pathTraceProgram);
		if (presentProgram > 0) glDeleteProgram(presentProgram);
		if (cubeVbo != 0) glDeleteBuffers(cubeVbo);
		if (cubeVao != 0) glDeleteVertexArrays(cubeVao);
		if (quadVbo != 0) glDeleteBuffers(quadVbo);
		if (quadVao != 0) glDeleteVertexArrays(quadVao);
		if (accumTex != -1) glDeleteTextures(accumTex);
		if (accumFbo != -1) glDeleteFramebuffers(accumFbo);
		pathTraceProgram = -1;
		presentProgram = -1;
		accumTex = -1;
		accumFbo = -1;
	}

	@Override
	public String getBackendName() {
		return "OpenGL";
	}

	private static float[] lightDirectionFromAngles(float azimuth, float elevation) {
		float cosEl = (float) Math.cos(elevation);
		float x = cosEl * (float) Math.cos(azimuth);
		float z = cosEl * (float) Math.sin(azimuth);
		float y = (float) Math.sin(elevation);
		return new float[] { x, y, z };
	}

	private void createCube() {
		cubeVao = glGenVertexArrays();
		glBindVertexArray(cubeVao);
		cubeVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, cubeVbo);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			java.nio.FloatBuffer fb = stack.mallocFloat(cubeVertices.length);
			fb.put(cubeVertices).flip();
			glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
		}
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	private void createQuad() {
		quadVao = glGenVertexArrays();
		glBindVertexArray(quadVao);
		quadVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			java.nio.FloatBuffer fb = stack.mallocFloat(quadVertices.length);
			fb.put(quadVertices).flip();
			glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
		}
		glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	private int compileProgram(String vertSrc, String fragSrc) {
		int vShader = glCreateShader(GL_VERTEX_SHADER);
		glShaderSource(vShader, vertSrc);
		glCompileShader(vShader);
		checkShaderCompile(vShader, "vertex");

		int fShader = glCreateShader(GL_FRAGMENT_SHADER);
		glShaderSource(fShader, fragSrc);
		glCompileShader(fShader);
		checkShaderCompile(fShader, "fragment");

		int program = glCreateProgram();
		glAttachShader(program, vShader);
		glAttachShader(program, fShader);
		glLinkProgram(program);
		if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
			System.err.println("CinematicRendererGL: program link failed: " + glGetProgramInfoLog(program));
		}

		glDeleteShader(vShader);
		glDeleteShader(fShader);
		return program;
	}

	private void checkShaderCompile(int shader, String label) {
		if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
			System.err.println("CinematicRendererGL: " + label + " shader compile failed: "
					+ glGetShaderInfoLog(shader));
		}
	}
}
