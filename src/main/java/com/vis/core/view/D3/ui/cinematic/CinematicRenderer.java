/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui.cinematic;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.vis.core.view.D3.ui.VolumeRenderer;

/**
 * A Monte Carlo path-traced "cinematic" renderer for the volume currently
 * shown in {@link com.vis.core.view.D3.ui.GLCanvas}, progressively
 * accumulating samples across frames while the camera/transfer
 * function/lighting stay still. Has two implementations sharing this same
 * contract - {@code CinematicRendererGL} (always available, pure GLSL) and
 * a CUDA-accelerated one - so {@code GLCanvas} can use whichever was
 * selected at startup without caring which.
 */
public interface CinematicRenderer {

	/** Compiles shaders/kernels and allocates GPU resources. Must be called once, with a current GL context. */
	void init();

	/** (Re)allocates the accumulation buffer to match the canvas's physical pixel size, and resets it. */
	void resize(int width, int height);

	/**
	 * Renders one progressive step into the accumulation buffer and presents
	 * the current (averaged) result to the bound framebuffer. {@code mvp}
	 * and {@code camPosLocal} use the same local cube / model-matrix
	 * convention as {@link VolumeRenderer#render}; {@code volumeSource} is
	 * queried for the 3D volume texture, LUT texture, and window/level so
	 * the cinematic image uses the exact same transfer function the
	 * standard VR/MIP modes show.
	 */
	void render(Matrix4f mvp, Vector3f camPosLocal, VolumeRenderer volumeSource, CinematicParams params);

	/** Clears accumulated samples and restarts progressive refinement from scratch. */
	void invalidateAccumulation();

	/** Releases GPU resources. Safe to call even if {@link #init()} was never called. */
	void dispose();

	/** Short label for the UI's GPU-backend indicator, e.g. "OpenGL" or "CUDA". */
	String getBackendName();
}
