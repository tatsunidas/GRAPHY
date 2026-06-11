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

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.process.ShortProcessor;

/**
 * @author tatsunidas
 *
 */
public class GantryTiltCorrector {
	
	//debug
	public static void main(String[] args) {
		ImagePlus ct = FolderOpener.open("/home/tatsunidas/ダウンロード/TEST_CT");
		GantryTiltCorrector gtc = new GantryTiltCorrector();
		double tiltAngle = GDicomTools.getDouble(ct, 1, "0018,1120"/*Gantry/Detector Tilt*/);
		double pixelSpacingY = ct.getCalibration().pixelHeight;
		double sliceSpacing = GDicomTools.getVoxelDepth(ct);
		double reconSliceSpacing = 1d;
		ImagePlus recon = gtc.correctVolume3D(ct, tiltAngle, pixelSpacingY, sliceSpacing, reconSliceSpacing);
		recon.show();
	}

	/**
	 * Calculates the enclosing bounding box and corrects the gantry tilt using
	 * rigorous 3D resampling.
	 * 
	 * @param imp               The input ImagePlus object (16-bit), axial plane is acquired.
	 * @param tiltAngle         The gantry tilt angle (in degrees, e.g., 20.0 or
	 *                          -15.5)
	 * @param pixelSpacingY     The pixel spacing in the Y direction (mm)
	 * @param sliceSpacing      The original slice spacing (mm)
	 * @param reconSliceSpacing The new reconstructed slice thickness (mm) (e.g.,
	 *                          1.0mm)
	 * @return A new orthogonally reconstructed ImagePlus object
	 */
	public ImagePlus correctVolume3D(ImagePlus imp, double tiltAngle, double pixelSpacingY, double sliceSpacing,
			double reconSliceSpacing) {

		int width = imp.getWidth();
		int heightOrig = imp.getHeight();
		int depthOrig = imp.getStackSize();

		ImageStack oldStack = imp.getStack();
		
		CutSurface plane = PlanarSupport.planarOf(imp);
		if(plane != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Gantry Tilt Correction reqire axila plane image stack.");
		}
		
		double[] ippFirst = GDicomTools.getImagePositionPatient(imp, 1);
		double[] ippLast  = GDicomTools.getImagePositionPatient(imp, imp.getNSlices()); 
		double[] iop = GDicomTools.getImageOrientationPatient(imp, 1);
		
		if(!needsTiltCorrection(ippFirst, ippLast, iop)) {
			return imp;
		}

		// Calculate angles
		double tiltAngleRad = Math.toRadians(tiltAngle);
		double cosA = Math.cos(tiltAngleRad);
		double sinA = Math.sin(tiltAngleRad);
		double tanA = Math.tan(tiltAngleRad);

		// ==========================================
		// 1. Calculate the new bounding box (physical size)
		// ==========================================
		// Physical span in the Y direction (apparent height shrinks by a factor of cos
		// due to tilt)
		double ySpanPhys = (heightOrig - 1) * pixelSpacingY * Math.abs(cosA);

		// Physical span in the Z direction (original Z length + extra Z length
		// protruding due to tilt)
		double zSpanPhys = (depthOrig - 1) * sliceSpacing + (heightOrig - 1) * pixelSpacingY * Math.abs(sinA);

		// ==========================================
		// 2. Determine the new volume size
		// ==========================================
		// Calculate new height (maintaining Y pixel spacing)
		int heightNew = (int) Math.ceil(ySpanPhys / pixelSpacingY) + 1;

		// Requested processing: Divide the physical Z span by the reconstructed slice
		// thickness and round up to determine the new depth
		int depthNew = (int) Math.ceil(zSpanPhys / reconSliceSpacing) + 1;

		ImageStack newStack = new ImageStack(width, heightNew);

		// Offset for when the tilt is negative, causing the starting Z position to
		// shift to the minus side
		double zMinPhys = Math.min(0.0, (heightOrig - 1) * pixelSpacingY * sinA);

		// Expand original data into a 2D array (for faster random access)
		short[][] srcVolume = new short[depthOrig][];
		for (int z = 0; z < depthOrig; z++) {
			srcVolume[z] = (short[]) oldStack.getPixels(z + 1);
		}

		/*
		 * padding value (Out of range FOV extra Air HU)
		 */
		short paddingHU = (short) imp.getCalibration().getRawValue(-2000);

		// ==========================================
		// 3. 3D resampling (extraction via inverse mapping)
		// ==========================================
		for (int z = 0; z < depthNew; z++) {
			short[] destPixels = new short[width * heightNew];
			// Current physical Z coordinate in the new volume
			double physZ = zMinPhys + z * reconSliceSpacing;

			for (int y = 0; y < heightNew; y++) {
				// Current physical Y coordinate in the new volume
				double physY = y * pixelSpacingY;

				// Mathematical inverse mapping: Back-calculate the original pixel indices
				// (srcY, srcZ) from the new physical (Y, Z)
				double srcY = physY / (pixelSpacingY * cosA);
				double srcZ = (physZ - physY * tanA) / sliceSpacing;

				// Coordinate calculation for interpolation
				int sy0 = (int) Math.floor(srcY);
				int sy1 = sy0 + 1;
				int sz0 = (int) Math.floor(srcZ);
				int sz1 = sz0 + 1;

				double wy1 = srcY - sy0;
				double wy0 = 1.0 - wy1;
				double wz1 = srcZ - sz0;
				double wz0 = 1.0 - wz1;

				boolean isInside = (sy0 >= 0 && sy1 < heightOrig && sz0 >= 0 && sz1 < depthOrig);

				if (isInside) {
					// --- Fast route (completely inside) ---
					short[] sliceZ0 = srcVolume[sz0];
					short[] sliceZ1 = srcVolume[sz1];

					for (int x = 0; x < width; x++) {
						int idxY0 = sy0 * width + x;
						int idxY1 = sy1 * width + x;

						double val_y0_z0 = sliceZ0[idxY0] & 0xffff;
                     double val_y1_z0 = sliceZ0[idxY1] & 0xffff;
                     double val_y0_z1 = sliceZ1[idxY0] & 0xffff;
                     double val_y1_z1 = sliceZ1[idxY1] & 0xffff;

						// Z interpolation -> Y interpolation
						double val_y0 = val_y0_z0 * wz0 + val_y0_z1 * wz1;
						double val_y1 = val_y1_z0 * wz0 + val_y1_z1 * wz1;

						destPixels[y * width + x] = (short) Math.round(val_y0 * wy0 + val_y1 * wy1);
					}
				} else {
					// --- Safe route (padding boundary) ---
					for (int x = 0; x < width; x++) {
						double val_y0_z0 = getSafePixel(srcVolume, width, heightOrig, depthOrig, x, sy0, sz0,
								paddingHU);
						double val_y1_z0 = getSafePixel(srcVolume, width, heightOrig, depthOrig, x, sy1, sz0,
								paddingHU);
						double val_y0_z1 = getSafePixel(srcVolume, width, heightOrig, depthOrig, x, sy0, sz1,
								paddingHU);
						double val_y1_z1 = getSafePixel(srcVolume, width, heightOrig, depthOrig, x, sy1, sz1,
								paddingHU);

						double val_y0 = val_y0_z0 * wz0 + val_y0_z1 * wz1;
						double val_y1 = val_y1_z0 * wz0 + val_y1_z1 * wz1;

						destPixels[y * width + x] = (short) Math.round(val_y0 * wy0 + val_y1 * wy1);
					}
				}
			}
			newStack.addSlice("Recon Z=" + String.format("%.2f", physZ),
					new ShortProcessor(width, heightNew, destPixels, null));
		}
		// ==========================================
		// 4. Create new ImagePlus and update calibration
		// ==========================================
		ImagePlus correctedImp = new ImagePlus(imp.getTitle() + "_3DOccupied", newStack);
		
		//set ipp and iop
		double[][] newIpps = calculateNewIPP(
				ippFirst, ippLast, tiltAngle, pixelSpacingY, heightOrig, reconSliceSpacing, depthNew);
		
		String patID = GDicomTools.getTag(imp, "0010,0020");
		String studyUID = GDicomTools.getTag(imp, "0020,000D");
		String seriesUID = UIDUtils.createUID();
		String sopClassUID = GDicomTools.getTag(imp, "0008,0016");
		String refUID = GDicomTools.getTag(imp, "0020,0052");
		
		double[] pixsp = GDicomTools.getDoubles(imp, "0028,0030");
		
		for (int z = 0; z < depthNew; z++) {
//		    double newX = newIpps[z][0];
//		    double newY = newIpps[z][1];
//		    double newZ = newIpps[z][2];
		    GDicomTools.setImagePositionPatient(correctedImp, z+1, newIpps[z]);
		    // set AXIAL Image Orientation Patient
		    GDicomTools.setImageOrientationPatient(correctedImp, z+1, new double[] {1,0,0,0,1,0});
		    
		    //must to have
		    GDicomTools.setTag(correctedImp, z+1, "0010,0020", patID);
		    GDicomTools.setTag(correctedImp, z+1, "0020,000D", studyUID);
		    GDicomTools.setTag(correctedImp, z+1, "0020,000E", seriesUID);
		    GDicomTools.setTag(correctedImp, z+1, "0008,0018", UIDUtils.createUID()/*SOPInstUID*/);
		    GDicomTools.setTag(correctedImp, z+1, "0008,0016", sopClassUID);
		    GDicomTools.setTag(correctedImp, z+1, "0020,0052", refUID);

		    GDicomTools.setTag(correctedImp, z+1, "0028,0030", pixelSpacingY + "\\" + pixsp[1]);
            GDicomTools.setTag(correctedImp, z+1, "0018,0050", String.valueOf(reconSliceSpacing));
        }
		
		if (imp.getCalibration() != null) {
			Calibration cal = imp.getCalibration().copy();
			// Update metadata to the new reconstructed slice thickness since the
			// Z-direction thickness has changed
			cal.pixelDepth = reconSliceSpacing;
			correctedImp.setCalibration(cal);
		}
		
		correctedImp.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());

		return correctedImp;
	}

	// 戻り値を int に変更し、& 0xffff で符号なし整数として扱う
    private static int getSafePixel(short[][] vol, int width, int height, int depth, int x, int y, int z,
            short padding) {
        if (y < 0 || y >= height || z < 0 || z >= depth) {
            return padding & 0xffff;
        }
        return vol[z][y * width + x] & 0xffff;
    }

	/**
	 * Calculates the ImagePositionPatient (IPP) of the newly reconstructed Axial
	 * slices. * Note: This considers whether the original stack progresses in the
	 * positive or negative Z-axis direction (Head-First or Feet-First). * In the
	 * case of CT gantry tilt correction, IPP only moves in the Z direction.
	 * * @param origIppFirst IPP of the original first slice [X, Y, Z]
	 * 
	 * @param origIppLast       IPP of the original last slice [X, Y, Z]
	 * @param tiltAngle         Gantry tilt angle
	 * @param pixelSpacingY     Pixel spacing in the Y direction
	 * @param heightOrig        Original image height
	 * @param reconSliceSpacing New slice thickness
	 * @param depthNew          Number of newly generated slices (determined by
	 *                          correctVolume3D)
	 * @return 2D array containing the IPP of each new slice ( double[depthNew][3] )
	 */
	private double[][] calculateNewIPP(double[] origIppFirst, double[] origIppLast, double tiltAngle,
			double pixelSpacingY, int heightOrig, double reconSliceSpacing, int depthNew) {

		double[][] newIpps = new double[depthNew][3];

		// Use the coordinates of the original first slice as a reference
		double x0 = origIppFirst[0];
		double y0 = origIppFirst[1];
		double z0 = origIppFirst[2];

		// Get the progression direction in the Z direction (+1.0 or -1.0)
		double zDirection = Math.signum(origIppLast[2] - origIppFirst[2]);
		// *If it becomes 0 (e.g., only 1 slice), set the default direction (1.0)
		if (zDirection == 0)
			zDirection = 1.0;

		// Calculate the same Z offset reference value as calculated in correctVolume3D
		double sinA = Math.sin(Math.toRadians(tiltAngle));
		double zMinPhys = Math.min(0.0, (heightOrig - 1) * pixelSpacingY * sinA);

		for (int k = 0; k < depthNew; k++) {
			// The X and Y of the new IPP are completely identical to the original image
			newIpps[k][0] = x0;
			newIpps[k][1] = y0;

			// Z coordinate of the new IPP
			// physZ = zMinPhys + k * reconSliceSpacing
			// Multiply this by the physical progression direction of the stack (zDirection)
			// and add to Z0
			double physZ = zMinPhys + k * reconSliceSpacing;
			newIpps[k][2] = z0 + (zDirection * physZ);
		}

		return newIpps;
	}

	/**
	 * Geometrically determines the shear (gantry tilt) of the volume from IPP and
	 * IOP. * @param ippFirst IPP of the original first slice [X, Y, Z]
	 * 
	 * @param ippLast IPP of the original last slice [X, Y, Z]
	 * @param iop     ImageOrientationPatient of the slice [R_x, R_y, R_z, C_x, C_y,
	 *                C_z]
	 * @return Actual shear angle (in degrees). Correction is unnecessary if close
	 *         to 0.
	 */
	public double calculateActualShearAngle(double[] ippFirst, double[] ippLast, double[] iop) {
		// 1. Calculate the normal vector N of the slice (Cross product of Row and
		// Column vectors)
		double rx = iop[0], ry = iop[1], rz = iop[2];
		double cx = iop[3], cy = iop[4], cz = iop[5];

		double nx = ry * cz - rz * cy;
		double ny = rz * cx - rx * cz;
		double nz = rx * cy - ry * cx;

		// Normalize the normal vector (Usually IOP is a unit vector with length 1, but
		// just in case)
		double normN = Math.sqrt(nx * nx + ny * ny + nz * nz);
		nx /= normN;
		ny /= normN;
		nz /= normN;

		// 2. Calculate the volume progression vector V (IPP_last - IPP_first)
		double vx = ippLast[0] - ippFirst[0];
		double vy = ippLast[1] - ippFirst[1];
		double vz = ippLast[2] - ippFirst[2];

		double normV = Math.sqrt(vx * vx + vy * vy + vz * vz);

		// If there is only one slice or the positions are exactly the same, determine
		// that there is no shear
		if (normV == 0)
			return 0.0;

		// Normalize the progression vector
		vx /= normV;
		vy /= normV;
		vz /= normV;

		// 3. Calculate the dot product of N and V
		double dotProduct = Math.abs(nx * vx + ny * vy + nz * vz);

		// Prevent exceeding 1.0 due to floating-point errors
		if (dotProduct > 1.0)
			dotProduct = 1.0;

		// 4. Obtain the angle (in radians) from the dot product and convert to degrees
		double angleRad = Math.acos(dotProduct);
		return Math.toDegrees(angleRad);
	}

	/**
	 * Determines whether tilt correction should be executed.
	 */
	public boolean needsTiltCorrection(double[] ippFirst, double[] ippLast, double[] iop) {
		double actualAngle = calculateActualShearAngle(ippFirst, ippLast, iop);
		// Consider it "sheared" if the calculated angle is greater than 0.5 degrees
		// (Skip processing for 0.5 degrees or less as minute errors or practically
		// negligible tilt)
		boolean doTiltCorr = actualAngle > 0.5;
		Log.logger.info("Tilt angle:"+actualAngle+", Tilt Correction needs :("+doTiltCorr+")");
		return doTiltCorr;
	}
}