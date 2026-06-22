/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.centerline;

import sc.fiji.skeletonize3D.Skeletonize3D_;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;

/**
 * Reduces a binary voxel mask to a 1-voxel-wide topological skeleton using
 * Fiji's {@link Skeletonize3D_} - the standard Lee, Kashyap &amp; Chu (1994)
 * 3D parallel thinning algorithm (simple-point + Euler-invariance checks),
 * rather than a hand-rolled approximation.
 *
 * {@code Skeletonize3D_} is normally invoked as an ImageJ PlugInFilter
 * (setup()/run() wired up by the ImageJ UI), but both methods are public
 * and side-effect-free with respect to any global ImageJ state, so calling
 * them directly - {@code setup("", imp)} then {@code run(imp.getProcessor())}
 * - runs the exact same algorithm headlessly. {@code run()} internally
 * normalizes any non-zero input to a 0/1 mask, thins it, then scales the
 * result back to the usual 0/255 binary convention before returning.
 */
public class Skeletonizer3D {

	private Skeletonizer3D() {
	}

	public static byte[] skeletonizeMask(byte[] mask, int w, int h, int d) {
		ImageStack stack = new ImageStack(w, h);
		int sliceSize = w * h;
		for (int z = 0; z < d; z++) {
			byte[] slice = new byte[sliceSize];
			System.arraycopy(mask, z * sliceSize, slice, 0, sliceSize);
			stack.addSlice(new ByteProcessor(w, h, slice, null));
		}
		ImagePlus imp = new ImagePlus("mask", stack);

		Skeletonize3D_ skeletonizer = new Skeletonize3D_();
		skeletonizer.setup("", imp);
		skeletonizer.run(imp.getProcessor());

		byte[] result = new byte[mask.length];
		ImageStack resultStack = imp.getStack();
		for (int z = 0; z < d; z++) {
			byte[] slice = (byte[]) resultStack.getProcessor(z + 1).getPixels();
			System.arraycopy(slice, 0, result, z * sliceSize, sliceSize);
		}
		return result;
	}
}
