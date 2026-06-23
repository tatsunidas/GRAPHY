/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui.cinematic;

/**
 * Parameters shared by every {@link CinematicRenderer} implementation
 * (GLSL or CUDA): the lighting setup and per-frame sample budget for the
 * Monte Carlo path tracer, plus a generation counter the renderer uses to
 * notice when the transfer function (LUT/opacity curve) changed under it.
 */
public class CinematicParams {

	/** Radians, measured around the world +Y axis. */
	public float lightAzimuth = (float) Math.toRadians(45.0);
	/** Radians, 0 = horizon, +PI/2 = straight down from above. */
	public float lightElevation = (float) Math.toRadians(60.0);
	public float lightIntensity = 1.5f;
	public float ambientIntensity = 0.25f;

	/** Henyey-Greenstein asymmetry factor in (-1, 1); 0 = isotropic, >0 = forward scattering. */
	public float scatteringAnisotropy = 0.2f;

	/**
	 * Half-angle (radians) of the cone the shadow ray is randomly jittered
	 * within, simulating an area light instead of an infinitesimal point
	 * light. This is what gives the characteristic "cinematic" soft-shadow
	 * look - a pure point light just casts hard, VR-like edges that are easy
	 * to mistake for no shadow at all.
	 */
	public float lightAngularRadius = 0.08f;

	/** Multiplies the accumulated radiance right before tonemapping, to dial in how punchy the shading reads. */
	public float exposure = 1.5f;

	/** New path-traced samples accumulated per rendered frame, while the scene is static. */
	public int samplesPerFrame = 1;

	/**
	 * Bumped by the caller whenever the LUT/opacity transfer function changes,
	 * so a renderer that caches a fingerprint of these params can tell a
	 * same-valued-but-different-curve change apart and reset accumulation.
	 */
	public int transferFunctionGeneration = 0;

	public CinematicParams copy() {
		CinematicParams c = new CinematicParams();
		c.lightAzimuth = lightAzimuth;
		c.lightElevation = lightElevation;
		c.lightIntensity = lightIntensity;
		c.ambientIntensity = ambientIntensity;
		c.scatteringAnisotropy = scatteringAnisotropy;
		c.lightAngularRadius = lightAngularRadius;
		c.exposure = exposure;
		c.samplesPerFrame = samplesPerFrame;
		c.transferFunctionGeneration = transferFunctionGeneration;
		return c;
	}
}
