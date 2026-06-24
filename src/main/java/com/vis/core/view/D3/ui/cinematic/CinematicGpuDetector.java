/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui.cinematic;

import java.nio.IntBuffer;

import org.lwjgl.cuda.CU;
import org.lwjgl.cuda.NVRTC;
import org.lwjgl.system.MemoryStack;

/**
 * Probes whether a usable NVIDIA CUDA device is available, so
 * {@link com.vis.core.view.D3.ui.GLCanvas} can pick {@code CinematicRendererCuda}
 * over the always-available {@code CinematicRendererGL}. Never throws: on a
 * machine with no NVIDIA GPU/driver (the normal case on macOS, since NVIDIA
 * dropped CUDA support there) the native {@code nvcuda} library simply isn't
 * present, which surfaces as {@link UnsatisfiedLinkError} or
 * {@link NoClassDefFoundError} - both are caught here exactly like any other
 * failure reason, so callers only ever see a plain boolean.
 *
 * Checks the GPU driver ({@code cuInit}/device count) AND NVRTC separately,
 * since they come from different places: the driver-side check only needs
 * {@code nvcuda}, which ships with any NVIDIA GPU driver, while NVRTC needs
 * its own {@code nvrtc64_*.dll}/{@code nvrtc-builtins64_*.dll} pair (Linux:
 * {@code libnvrtc.so.*}/{@code libnvrtc-builtins.so.*}), which are CUDA
 * Toolkit/redistributable components most end-user machines won't have just
 * from installing a GPU driver - a "has an NVIDIA GPU" machine very
 * frequently still fails this second check, and that's the expected,
 * supported-by-design outcome (fall back to OpenGL), not a bug.
 */
public final class CinematicGpuDetector {

	private CinematicGpuDetector() {
	}

	public static boolean isCudaAvailable() {
		try {
			int err = CU.cuInit(0);
			if (err != CU.CUDA_SUCCESS) {
				System.err.println("CinematicGpuDetector: cuInit failed (error " + err + "), falling back to OpenGL");
				return false;
			}
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer count = stack.mallocInt(1);
				err = CU.cuDeviceGetCount(count);
				if (err != CU.CUDA_SUCCESS || count.get(0) <= 0) {
					System.err.println("CinematicGpuDetector: no CUDA device found, falling back to OpenGL");
					return false;
				}
			}
		} catch (Throwable t) {
			System.err.println("CinematicGpuDetector: CUDA driver unavailable, falling back to OpenGL (" + t + ")");
			return false;
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer major = stack.mallocInt(1);
			IntBuffer minor = stack.mallocInt(1);
			int err = NVRTC.nvrtcVersion(major, minor);
			if (err != NVRTC.NVRTC_SUCCESS) {
				System.err.println("CinematicGpuDetector: NVRTC unavailable (error " + err + "), falling back to OpenGL");
				return false;
			}
		} catch (Throwable t) {
			// もっとも多いのはこのケース: GPUドライバ(nvcuda)はあるがnvrtc64_*.dll/
			// nvrtc-builtins64_*.dllが無い/対になっていない - 通常のエンドユーザー機では
			// CUDA Toolkitが入っていないのでむしろ普通に起きる。再配布パッケージ化(別タスク)で対応予定。
			System.err.println("CinematicGpuDetector: NVRTC unavailable (needs CUDA Toolkit's nvrtc64_*/"
					+ "nvrtc-builtins64_* redistributable, not just the GPU driver), falling back to OpenGL (" + t
					+ ")");
			return false;
		}

		return true;
	}
}
