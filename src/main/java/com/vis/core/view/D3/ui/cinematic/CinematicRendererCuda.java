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

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.cuda.CU;
import org.lwjgl.cuda.CUDA_RESOURCE_DESC;
import org.lwjgl.cuda.CUDA_TEXTURE_DESC;
import org.lwjgl.cuda.CUGL;
import org.lwjgl.cuda.NVRTC;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import com.vis.core.view.D3.ui.ShaderUtils;
import com.vis.core.view.D3.ui.VolumeRenderer;

/**
 * CUDA-accelerated implementation of {@link CinematicRenderer}, using LWJGL's
 * own {@code org.lwjgl.cuda} bindings ({@link CU} driver API, {@link CUGL}
 * OpenGL interop, {@link NVRTC} runtime compilation) rather than a
 * third-party CUDA binding - see the plan doc for why. Runs the exact same
 * Monte Carlo path tracing algorithm as {@code cinematic.frag}
 * ({@code cuda/cinematic_kernel.cu}, written from scratch in CUDA C, not a
 * port), reading the volume/LUT textures and writing the accumulation buffer
 * directly through OpenGL-CUDA graphics interop so no data is duplicated and
 * LUT/window-level edits are automatically visible to the kernel.
 *
 * Every public method here assumes it is called with the canvas's GL context
 * current on the calling thread (i.e. from {@code initGL()}/{@code paintGL()}),
 * exactly like {@link CinematicRendererGL} - {@code CUGL} interop calls are
 * no different from raw GL calls in that respect.
 */
public class CinematicRendererCuda implements CinematicRenderer {

	private static final int BLOCK_SIZE = 16;
	private static final float[] QUAD_VERTICES = { -1f, -1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, -1f, -1f };

	private long cudaContext;
	private long cudaModule;
	private long kernelFunction;
	private long invMvpDevicePtr;

	private int presentProgram = -1;
	private int quadVao, quadVbo;

	private int accumFbo = -1;
	private int accumTex = -1;
	private int accumWidth, accumHeight;
	private int frameCount = 0;
	private int frameSeed = 0;

	private long volumeResource = 0;
	private long lutResource = 0;
	private long accumResource = 0;
	private int registeredVolumeTexId = -1;
	private int registeredLutTexId = -1;

	@Override
	public void init() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer deviceCount = stack.mallocInt(1);
			IntBuffer cudaDevices = stack.mallocInt(1);
			int device = 0;
			// このOpenGLコンテキストを駆動しているのと同じGPUのCUDAデバイスを使う（無ければ0番にフォールバック）。
			int err = CUGL.cuGLGetDevices(deviceCount, cudaDevices, CUGL.CU_GL_DEVICE_LIST_ALL);
			if (err == CU.CUDA_SUCCESS && deviceCount.get(0) > 0) {
				device = cudaDevices.get(0);
			}

			PointerBuffer pCtx = stack.mallocPointer(1);
			check(CU.cuCtxCreate(pCtx, 0, device), "cuCtxCreate");
			cudaContext = pCtx.get(0);

			compileKernel(device);

			PointerBuffer pInvMvp = stack.mallocPointer(1);
			check(CU.cuMemAlloc(pInvMvp, 16L * Float.BYTES), "cuMemAlloc(invMvp)");
			invMvpDevicePtr = pInvMvp.get(0);
		}

		createPresentProgram();
		createQuad();
	}

	private void compileKernel(int device) {
		String source = ShaderUtils.loadShaderAsString("/cuda/cinematic_kernel.cu");

		long program;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pProgram = stack.mallocPointer(1);
			check(NVRTC.nvrtcCreateProgram(pProgram, source, "cinematic_kernel.cu", null, null),
					"nvrtcCreateProgram");
			program = pProgram.get(0);

			int compileResult;
			try (MemoryStack archStack = MemoryStack.stackPush()) {
				IntBuffer major = archStack.mallocInt(1);
				IntBuffer minor = archStack.mallocInt(1);
				PointerBuffer options;
				if (CU.cuDeviceComputeCapability(major, minor, device) == CU.CUDA_SUCCESS) {
					String archOpt = "--gpu-architecture=compute_" + major.get(0) + minor.get(0);
					options = archStack.mallocPointer(1);
					options.put(0, archStack.UTF8(archOpt));
				} else {
					options = null;
				}
				compileResult = NVRTC.nvrtcCompileProgram(program, options);
			}

			if (compileResult != NVRTC.NVRTC_SUCCESS) {
				throw new RuntimeException("NVRTC compile failed: " + getProgramLog(program));
			}

			PointerBuffer ptxSize = stack.mallocPointer(1);
			check(NVRTC.nvrtcGetPTXSize(program, ptxSize), "nvrtcGetPTXSize");
			ByteBuffer ptx = MemoryUtil.memAlloc((int) ptxSize.get(0));
			try {
				check(NVRTC.nvrtcGetPTX(program, ptx), "nvrtcGetPTX");

				PointerBuffer pModule = stack.mallocPointer(1);
				check(CU.cuModuleLoadData(pModule, ptx), "cuModuleLoadData");
				cudaModule = pModule.get(0);
			} finally {
				MemoryUtil.memFree(ptx);
			}

			PointerBuffer pFunction = stack.mallocPointer(1);
			check(CU.cuModuleGetFunction(pFunction, cudaModule, "pathTraceKernel"), "cuModuleGetFunction");
			kernelFunction = pFunction.get(0);

			pProgram.put(0, program);
			NVRTC.nvrtcDestroyProgram(pProgram);
		}
	}

	private static String getProgramLog(long program) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer logSize = stack.mallocPointer(1);
			if (NVRTC.nvrtcGetProgramLogSize(program, logSize) != NVRTC.NVRTC_SUCCESS) {
				return "(no log available)";
			}
			ByteBuffer log = MemoryUtil.memAlloc((int) logSize.get(0));
			try {
				NVRTC.nvrtcGetProgramLog(program, log);
				return MemoryUtil.memUTF8(log, log.remaining() - 1);
			} finally {
				MemoryUtil.memFree(log);
			}
		}
	}

	@Override
	public void resize(int width, int height) {
		if (width <= 0 || height <= 0) return;
		if (width == accumWidth && height == accumHeight && accumFbo != -1) return;

		unregisterAccum();

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
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
		glBindTexture(GL_TEXTURE_2D, 0);

		accumFbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, accumFbo);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, accumTex, 0);
		int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (status != GL_FRAMEBUFFER_COMPLETE) {
			System.err.println("CinematicRendererCuda: accumulation FBO incomplete, status=" + status);
		}
		glBindFramebuffer(GL_FRAMEBUFFER, 0);

		// CUDAから書き込むのでSURFACE_LDSTフラグが必須（読み取り専用のvolume/LUTとは異なる）。
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pRes = stack.mallocPointer(1);
			check(CUGL.cuGraphicsGLRegisterImage(pRes, accumTex, GL_TEXTURE_2D,
					CU.CU_GRAPHICS_REGISTER_FLAGS_SURFACE_LDST), "cuGraphicsGLRegisterImage(accum)");
			accumResource = pRes.get(0);
		}

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
		if (kernelFunction == 0 || accumResource == 0) return;

		ensureVolumeLutRegistered(volumeSource);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			// 1. invMvpをデバイスにアップロード
			Matrix4f invMvp = new Matrix4f(mvp).invert();
			java.nio.FloatBuffer invMvpHost = stack.mallocFloat(16);
			invMvp.get(invMvpHost);
			check(CU.cuMemcpyHtoD(invMvpDevicePtr, invMvpHost), "cuMemcpyHtoD(invMvp)");

			// 2. 3つのGLリソース(ボリューム/LUT/蓄積テクスチャ)をCUDAにマップする
			PointerBuffer resources = stack.mallocPointer(3);
			resources.put(0, volumeResource).put(1, lutResource).put(2, accumResource);
			check(CU.cuGraphicsMapResources(resources, 0), "cuGraphicsMapResources");

			long volumeTexObj = 0, lutTexObj = 0, accumSurfObj = 0;
			try {
				PointerBuffer pArray = stack.mallocPointer(1);

				check(CU.cuGraphicsSubResourceGetMappedArray(pArray, volumeResource, 0, 0),
						"cuGraphicsSubResourceGetMappedArray(volume)");
				volumeTexObj = createReadOnlyTexObject(stack, pArray.get(0));

				check(CU.cuGraphicsSubResourceGetMappedArray(pArray, lutResource, 0, 0),
						"cuGraphicsSubResourceGetMappedArray(lut)");
				lutTexObj = createReadOnlyTexObject(stack, pArray.get(0));

				check(CU.cuGraphicsSubResourceGetMappedArray(pArray, accumResource, 0, 0),
						"cuGraphicsSubResourceGetMappedArray(accum)");
				accumSurfObj = createSurfObject(stack, pArray.get(0));

				launchKernel(stack, volumeTexObj, lutTexObj, accumSurfObj, camPosLocal, volumeSource, params);
			} finally {
				if (accumSurfObj != 0) CU.cuSurfObjectDestroy(accumSurfObj);
				if (volumeTexObj != 0) CU.cuTexObjectDestroy(volumeTexObj);
				if (lutTexObj != 0) CU.cuTexObjectDestroy(lutTexObj);
				check(CU.cuGraphicsUnmapResources(resources, 0), "cuGraphicsUnmapResources");
			}
		}

		frameCount++;
		frameSeed++;

		presentPass();
	}

	private long createReadOnlyTexObject(MemoryStack stack, long array) {
		CUDA_RESOURCE_DESC resDesc = CUDA_RESOURCE_DESC.calloc(stack);
		resDesc.resType(CU.CU_RESOURCE_TYPE_ARRAY);
		resDesc.res_array_hArray(array);

		CUDA_TEXTURE_DESC texDesc = CUDA_TEXTURE_DESC.calloc(stack);
		texDesc.addressMode(0, CU.CU_TR_ADDRESS_MODE_CLAMP);
		texDesc.addressMode(1, CU.CU_TR_ADDRESS_MODE_CLAMP);
		texDesc.addressMode(2, CU.CU_TR_ADDRESS_MODE_CLAMP);
		texDesc.filterMode(CU.CU_TR_FILTER_MODE_LINEAR);
		texDesc.flags(CU.CU_TRSF_NORMALIZED_COORDINATES);

		LongBuffer pTexObj = stack.mallocLong(1);
		check(CU.cuTexObjectCreate(pTexObj, resDesc, texDesc, null), "cuTexObjectCreate");
		return pTexObj.get(0);
	}

	private long createSurfObject(MemoryStack stack, long array) {
		CUDA_RESOURCE_DESC resDesc = CUDA_RESOURCE_DESC.calloc(stack);
		resDesc.resType(CU.CU_RESOURCE_TYPE_ARRAY);
		resDesc.res_array_hArray(array);

		LongBuffer pSurfObj = stack.mallocLong(1);
		check(CU.cuSurfObjectCreate(pSurfObj, resDesc), "cuSurfObjectCreate");
		return pSurfObj.get(0);
	}

	private void launchKernel(MemoryStack stack, long volumeTexObj, long lutTexObj, long accumSurfObj,
			Vector3f camPosLocal, VolumeRenderer volumeSource, CinematicParams params) {
		float[] lightDir = lightDirectionFromAngles(params.lightAzimuth, params.lightElevation);

		LongBuffer pVolumeTex = stack.longs(volumeTexObj);
		LongBuffer pLutTex = stack.longs(lutTexObj);
		LongBuffer pAccumSurf = stack.longs(accumSurfObj);
		IntBuffer pWidth = stack.ints(accumWidth);
		IntBuffer pHeight = stack.ints(accumHeight);
		LongBuffer pInvMvpPtr = stack.longs(invMvpDevicePtr);
		java.nio.FloatBuffer pCamX = stack.floats(camPosLocal.x);
		java.nio.FloatBuffer pCamY = stack.floats(camPosLocal.y);
		java.nio.FloatBuffer pCamZ = stack.floats(camPosLocal.z);
		java.nio.FloatBuffer pUMin = stack.floats(volumeSource.getNormalizedMin());
		java.nio.FloatBuffer pUMax = stack.floats(volumeSource.getNormalizedMax());
		java.nio.FloatBuffer pWinCenter = stack.floats(volumeSource.getWindowCenter());
		java.nio.FloatBuffer pWinWidth = stack.floats(volumeSource.getWindowWidth());
		java.nio.FloatBuffer pLightDirX = stack.floats(lightDir[0]);
		java.nio.FloatBuffer pLightDirY = stack.floats(lightDir[1]);
		java.nio.FloatBuffer pLightDirZ = stack.floats(lightDir[2]);
		java.nio.FloatBuffer pLightIntensity = stack.floats(params.lightIntensity);
		java.nio.FloatBuffer pAmbient = stack.floats(params.ambientIntensity);
		java.nio.FloatBuffer pAnisotropy = stack.floats(params.scatteringAnisotropy);
		java.nio.FloatBuffer pAngular = stack.floats(params.lightAngularRadius);
			IntBuffer pSamples = stack.ints(Math.max(1, params.samplesPerFrame));
			IntBuffer pSeed = stack.ints(frameSeed);

			// 3D裁断領域（VolumeRendererが保持する実効値。裁断OFF時は -0.5〜0.5 が入っている）
			float[] clipMin = volumeSource.getEffectiveClipMin();
			float[] clipMax = volumeSource.getEffectiveClipMax();
			java.nio.FloatBuffer pClipMinX = stack.floats(clipMin[0]);
			java.nio.FloatBuffer pClipMinY = stack.floats(clipMin[1]);
			java.nio.FloatBuffer pClipMinZ = stack.floats(clipMin[2]);
			java.nio.FloatBuffer pClipMaxX = stack.floats(clipMax[0]);
			java.nio.FloatBuffer pClipMaxY = stack.floats(clipMax[1]);
			java.nio.FloatBuffer pClipMaxZ = stack.floats(clipMax[2]);

			// PBR material parameters
			java.nio.FloatBuffer pRoughness          = stack.floats(params.roughness);
			java.nio.FloatBuffer pSpecular           = stack.floats(params.specular);
			java.nio.FloatBuffer pMetallic           = stack.floats(params.metallic);
			java.nio.FloatBuffer pClearcoat          = stack.floats(params.clearcoat);
			java.nio.FloatBuffer pClearcoatRoughness = stack.floats(params.clearcoatRoughness);
			java.nio.FloatBuffer pGradThreshold      = stack.floats(params.surfaceGradientThreshold);

			PointerBuffer kernelParams = stack.mallocPointer(34);
			kernelParams.put(0, pVolumeTex).put(1, pLutTex).put(2, pAccumSurf).put(3, pWidth).put(4, pHeight)
					.put(5, pInvMvpPtr).put(6, pCamX).put(7, pCamY).put(8, pCamZ).put(9, pUMin).put(10, pUMax)
					.put(11, pWinCenter).put(12, pWinWidth).put(13, pLightDirX).put(14, pLightDirY).put(15, pLightDirZ)
					.put(16, pLightIntensity).put(17, pAmbient).put(18, pAnisotropy).put(19, pAngular).put(20, pSamples)
					.put(21, pSeed).put(22, pClipMinX).put(23, pClipMinY).put(24, pClipMinZ)
					.put(25, pClipMaxX).put(26, pClipMaxY).put(27, pClipMaxZ)
					.put(28, pRoughness).put(29, pSpecular).put(30, pMetallic)
					.put(31, pClearcoat).put(32, pClearcoatRoughness).put(33, pGradThreshold);

		int gridX = (accumWidth + BLOCK_SIZE - 1) / BLOCK_SIZE;
		int gridY = (accumHeight + BLOCK_SIZE - 1) / BLOCK_SIZE;
		check(CU.cuLaunchKernel(kernelFunction, gridX, gridY, 1, BLOCK_SIZE, BLOCK_SIZE, 1, 0, 0L, kernelParams, null),
				"cuLaunchKernel");
		check(CU.cuCtxSynchronize(), "cuCtxSynchronize");
	}

	private void ensureVolumeLutRegistered(VolumeRenderer volumeSource) {
		int volTexId = volumeSource.getTextureId();
		int lutTexId = volumeSource.getLutTextureId();

		if (volTexId != registeredVolumeTexId) {
			if (volumeResource != 0) {
				CU.cuGraphicsUnregisterResource(volumeResource);
				volumeResource = 0;
			}
			if (volTexId != -1) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer pRes = stack.mallocPointer(1);
					check(CUGL.cuGraphicsGLRegisterImage(pRes, volTexId, GL_TEXTURE_3D,
							CU.CU_GRAPHICS_REGISTER_FLAGS_READ_ONLY), "cuGraphicsGLRegisterImage(volume)");
					volumeResource = pRes.get(0);
				}
			}
			registeredVolumeTexId = volTexId;
		}

		if (lutTexId != registeredLutTexId) {
			if (lutResource != 0) {
				CU.cuGraphicsUnregisterResource(lutResource);
				lutResource = 0;
			}
			if (lutTexId != -1) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer pRes = stack.mallocPointer(1);
					check(CUGL.cuGraphicsGLRegisterImage(pRes, lutTexId, GL_TEXTURE_1D,
							CU.CU_GRAPHICS_REGISTER_FLAGS_READ_ONLY), "cuGraphicsGLRegisterImage(lut)");
					lutResource = pRes.get(0);
				}
			}
			registeredLutTexId = lutTexId;
		}
	}

	private void presentPass() {
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glViewport(0, 0, accumWidth, accumHeight);
		glUseProgram(presentProgram);

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, accumTex);
		glUniform1i(glGetUniformLocation(presentProgram, "uAccumTex"), 0);
		glUniform1f(glGetUniformLocation(presentProgram, "uFrameCount"), (float) frameCount);
		glUniform1f(glGetUniformLocation(presentProgram, "uExposure"), 1.5f);

		glDisable(GL_DEPTH_TEST);
		glBindVertexArray(quadVao);
		glDrawArrays(GL_TRIANGLES, 0, 6);
		glBindVertexArray(0);
		glUseProgram(0);
	}

	private static float[] lightDirectionFromAngles(float azimuth, float elevation) {
		float cosEl = (float) Math.cos(elevation);
		float x = cosEl * (float) Math.cos(azimuth);
		float z = cosEl * (float) Math.sin(azimuth);
		float y = (float) Math.sin(elevation);
		return new float[] { x, y, z };
	}

	private void unregisterAccum() {
		if (accumResource != 0) {
			CU.cuGraphicsUnregisterResource(accumResource);
			accumResource = 0;
		}
	}

	@Override
	public void dispose() {
		unregisterAccum();
		if (volumeResource != 0) {
			CU.cuGraphicsUnregisterResource(volumeResource);
			volumeResource = 0;
		}
		if (lutResource != 0) {
			CU.cuGraphicsUnregisterResource(lutResource);
			lutResource = 0;
		}
		if (invMvpDevicePtr != 0) {
			CU.cuMemFree(invMvpDevicePtr);
			invMvpDevicePtr = 0;
		}
		if (cudaModule != 0) {
			CU.cuModuleUnload(cudaModule);
			cudaModule = 0;
		}
		if (presentProgram > 0) glDeleteProgram(presentProgram);
		if (quadVbo != 0) glDeleteBuffers(quadVbo);
		if (quadVao != 0) glDeleteVertexArrays(quadVao);
		if (accumTex != -1) glDeleteTextures(accumTex);
		if (accumFbo != -1) glDeleteFramebuffers(accumFbo);
		accumTex = -1;
		accumFbo = -1;
		if (cudaContext != 0) {
			CU.cuCtxDestroy(cudaContext);
			cudaContext = 0;
		}
	}

	@Override
	public String getBackendName() {
		return "CUDA";
	}

	private void createPresentProgram() {
		presentProgram = compileProgram(ShaderUtils.loadShaderAsString("/shaders/present.vert"),
				ShaderUtils.loadShaderAsString("/shaders/present.frag"));
	}

	private void createQuad() {
		quadVao = glGenVertexArrays();
		glBindVertexArray(quadVao);
		quadVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			java.nio.FloatBuffer fb = stack.mallocFloat(QUAD_VERTICES.length);
			fb.put(QUAD_VERTICES).flip();
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
			System.err.println("CinematicRendererCuda: program link failed: " + glGetProgramInfoLog(program));
		}

		glDeleteShader(vShader);
		glDeleteShader(fShader);
		return program;
	}

	private void checkShaderCompile(int shader, String label) {
		if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
			System.err.println(
					"CinematicRendererCuda: " + label + " shader compile failed: " + glGetShaderInfoLog(shader));
		}
	}

	private static void check(int cudaError, String op) {
		if (cudaError != CU.CUDA_SUCCESS) {
			throw new RuntimeException("CinematicRendererCuda: " + op + " failed with CUDA error " + cudaError);
		}
	}
}
