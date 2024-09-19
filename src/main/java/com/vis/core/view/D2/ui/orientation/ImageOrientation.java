package com.vis.core.view.D2.ui.orientation;

/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

import org.joml.Vector3d;

import com.vis.core.view.D2.processing.ImagePlusDicomTagTools;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

import ij.ImagePlus;

/**
 * <a href=
 * "https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.2.html#sect_C.7.6.2.1.1">Image
 * Position and Image Orientation (Patient)</a>
 *
 * @author Nicolas Roduit
 * @author David A. Clunie
 * @author Tatsuaki Kobayashi
 */
public /*abstract*/ class ImageOrientation {
	
	public static void main(String[] args) {
//		String ct_ax = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\JIRA_DICOM\\CT_LEE_IR87a.dcm";
//		String ax = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\Localizer\\AXI\\708912FE";
//		String cor = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\Localizer\\COR\\5E79345D";
//		String sag = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\Localizer\\SAG\\2CC10C72";
//		DicomObject dcm_a = new DicomObject(ax, false);
//		DicomObject dcm_c = new DicomObject(cor, false);
//		DicomObject dcm_s = new DicomObject(sag, false);
//		//get cut surface
//		CutSurface res_a = ImageOrientation.getCutSurface(dcm_a);
//		CutSurface res_c = ImageOrientation.getCutSurface(dcm_c);
//		CutSurface res_s = ImageOrientation.getCutSurface(dcm_s);
//		System.out.println(res_a.name());
//		System.out.println(res_c.name());
//		System.out.println(res_s.name());
//		//get orientation
//		boolean biped_a = SubjectOrientation.isBiped(dcm_a);
//		boolean biped_c = SubjectOrientation.isBiped(dcm_c);
//		boolean biped_s = SubjectOrientation.isBiped(dcm_s);
////		System.out.println(biped_a);//true
////		System.out.println(biped_c);//true
////		System.out.println(biped_s);//true
//		System.out.println(ImageOrientation.getOrientation(ImageOrientation.getRowImagePosition(dcm_a), !biped_a));
//		System.out.println(ImageOrientation.getOrientation(ImageOrientation.getColumnImagePosition(dcm_a), !biped_a));
//		System.out.println(ImageOrientation.getOrientation(ImageOrientation.getRowImagePosition(dcm_c), !biped_c));
//		System.out.println(ImageOrientation.getOrientation(ImageOrientation.getColumnImagePosition(dcm_c), !biped_c));
//		System.out.println(ImageOrientation.getOrientation(ImageOrientation.getRowImagePosition(dcm_s), !biped_s));
//		System.out.println(ImageOrientation.getOrientation(ImageOrientation.getColumnImagePosition(dcm_s), !biped_s));
	}

	public enum CutSurface {
		UNKNOWN, AXIAL, SAGITTAL, CORONAL, OBLIQUE
	}

	private static final double OBLIQUITY_THRESHOLD = 0.8;

	public static org.joml.Vector3d getRowImagePosition(DicomObject dcm) {
		double[] imagePosition = dcm.getDoubles(Tag.Image​Orientation​Patient);
		if (imagePosition != null && imagePosition.length == 6) {
			return new Vector3d(imagePosition[0], imagePosition[1], imagePosition[2]);
		}
		return null;
	}

	public static org.joml.Vector3d getColumnImagePosition(DicomObject dcm) {
		double[] imagePosition =dcm.getDoubles(Tag.Image​Orientation​Patient);
		if (imagePosition != null && imagePosition.length == 6) {
			return new Vector3d(imagePosition[3], imagePosition[4], imagePosition[5]);
		}
		return null;
	}

	/**
	 * Get the orientation describing the major axis from a unit vector (direction
	 * cosine) as found in ImageOrientationPatient.
	 *
	 * <p>
	 * Some degree of deviation from one of the standard orthogonal axes is allowed
	 * before deciding no major axis applies and returning null.
	 *
	 * @param v the vector (direction cosine)
	 * @return the string describing the orientation of the vector, or null if
	 *         oblique
	 */
	private static Orientation getSubjectOrientation(Vector3d v, boolean quadruped) {
		double absX = Math.abs(v.x);
		double absY = Math.abs(v.y);
		double absZ = Math.abs(v.z);

		if (absX > OBLIQUITY_THRESHOLD && absX > absY && absX > absZ) {
			return quadruped ? SubjectOrientation.getQuadrupedXOrientation(v)
					: SubjectOrientation.getBipedXOrientation(v);
		} else if (absY > OBLIQUITY_THRESHOLD && absY > absX && absY > absZ) {
			return quadruped ? SubjectOrientation.getQuadrupedYOrientation(v)
					: SubjectOrientation.getBipedYOrientation(v);
		} else if (absZ > OBLIQUITY_THRESHOLD && absZ > absX && absZ > absY) {
			return quadruped ? SubjectOrientation.getQuadrupedZOrientation(v)
					: SubjectOrientation.getBipedZOrientation(v);
		}
		return null;
	}

	/**
	 * Get a plan describing the axial, coronal or sagittal plane from row and
	 * column unit vectors (direction cosines) as found in ImageOrientationPatient.
	 *
	 * <p>
	 * Some degree of deviation from one of the standard orthogonal planes is
	 * allowed before deciding the plane is OBLIQUE.
	 *
	 * @param vr the row vector
	 * @param vc the column vector
	 * @return the string describing the plane of orientation, AXIAL, CORONAL,
	 *         SAGITTAL or OBLIQUE
	 */
	public static CutSurface getCutsurface(Vector3d vr, Vector3d vc) {
		boolean quadruped = false;
		Orientation rowAxis = getSubjectOrientation(vr, quadruped);
		Orientation colAxis = getSubjectOrientation(vc, quadruped);
		if (rowAxis != null && colAxis != null) {
			if (rowAxis.getColor().equals(SubjectOrientation.blue)
					&& colAxis.getColor().equals(SubjectOrientation.red)) {
				return CutSurface.AXIAL;
			} else if (colAxis.getColor().equals(SubjectOrientation.blue)
					&& rowAxis.getColor().equals(SubjectOrientation.red)) {
				return CutSurface.AXIAL;
			} else if (rowAxis.getColor().equals(SubjectOrientation.blue)
					&& colAxis.getColor().equals(SubjectOrientation.green)) {
				return CutSurface.CORONAL;
			} else if (colAxis.getColor().equals(SubjectOrientation.blue)
					&& rowAxis.getColor().equals(SubjectOrientation.green)) {
				return CutSurface.CORONAL;
			} else if (rowAxis.getColor().equals(SubjectOrientation.red)
					&& colAxis.getColor().equals(SubjectOrientation.green)) {
				return CutSurface.SAGITTAL;
			} else if (colAxis.getColor().equals(SubjectOrientation.red)
					&& rowAxis.getColor().equals(SubjectOrientation.green)) {
				return CutSurface.SAGITTAL;
			}
		}
		return CutSurface.OBLIQUE;
	}

	public static CutSurface getCutSurface(DicomObject obj) {
		Vector3d vr = ImageOrientation.getRowImagePosition(obj);
		Vector3d vc = ImageOrientation.getColumnImagePosition(obj);
		if (vr != null && vc != null) {
			return ImageOrientation.getCutsurface(vr, vc);
		}
		return null;
	}
	
	public static CutSurface getCutSurface(ImagePlus imp) {
		ImagePlusDicomTagTools tools = new ImagePlusDicomTagTools();
		double[] iop = tools.getDoubles(imp, "0020,0037");
		if(iop == null) {
			return CutSurface.UNKNOWN;
		}
		Vector3d vr = new Vector3d(iop[0], iop[1], iop[2]);
		Vector3d vc = new Vector3d(iop[3], iop[4], iop[5]);
		if (vr != null && vc != null && !vr.equals(iop[3], iop[4], iop[5])) {
			return ImageOrientation.getCutsurface(vr, vc);
		}
		return CutSurface.UNKNOWN;
	}

	/**
	 * Get the letter representation of the orientation of a vector.
	 *
	 * <p>
	 * For bipeds, R (right) or L (left), A (anterior) or P (posterior), F (feet) *
	 * or H (head).
	 *
	 * <p>
	 * For quadrupeds, Le or Rt, V or D, Cr or Cd (with lower case; use
	 * toUpperCase() to produce valid CodeString for SubjectOrientation).
	 *
	 * @param v         the orientation vector
	 * @param quadruped true if subject is a quadruped rather than a biped
	 * @return a string rendering of the orientation, more than one letter if
	 *         oblique to the orthogonal axes, or empty string (not null) if fails
	 */
	public static String getOrientation(Vector3d v, boolean quadruped) {
		Orientation orientationX = quadruped ? SubjectOrientation.getQuadrupedXOrientation(v)
				: SubjectOrientation.getBipedXOrientation(v);
		Orientation orientationY = quadruped ? SubjectOrientation.getQuadrupedYOrientation(v)
				: SubjectOrientation.getBipedYOrientation(v);
		Orientation orientationZ = quadruped ? SubjectOrientation.getQuadrupedZOrientation(v)
				: SubjectOrientation.getBipedZOrientation(v);

		double absX = Math.abs(v.x);
		double absY = Math.abs(v.y);
		double absZ = Math.abs(v.z);

		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < 3; ++i) {
			if (absX > .0001 && absX >= absY && absX >= absZ) {
				buffer.append(orientationX.name());
				absX = 0;
			} else if (absY > .0001 && absY >= absX && absY >= absZ) {
				buffer.append(orientationY.name());
				absY = 0;
			} else if (absZ > .0001 && absZ >= absX && absZ >= absY) {
				buffer.append(orientationZ.name());
				absZ = 0;
			} else {
				break;
			}
		}
		return buffer.toString();
	}

	public static String getImageOrientationOpposite(String val, boolean quadruped) {
		if (quadruped) {
			return SubjectOrientation.getOppositeOrientation(SubjectOrientation.Quadruped.valueOf(val)).name();
		}
		return SubjectOrientation.getOppositeOrientation(SubjectOrientation.Biped.valueOf(val)).name();
	}

	/*
	 * TODO
	 */
	public static boolean hasSameOrientation(Praparat series1, Praparat series2) {
//	    if (series1 != null && series2 != null) {
//	      DicomImageElement image1 = series1.getMedia(MEDIA_POSITION.MIDDLE, null, null);
//	      DicomImageElement image2 = series2.getMedia(MEDIA_POSITION.MIDDLE, null, null);
//	      return hasSameOrientation(image1, image2);
//	    }
		return false;
	}

	public static boolean hasSameOrientation(DicomObject image1, DicomObject image2) {
		// Test if the two images have the same orientation
		if (image1 != null && image2 != null) {
			Vector3d vr1 = ImageOrientation.getRowImagePosition(image1);
			Vector3d vc1 = ImageOrientation.getColumnImagePosition(image1);
			Vector3d vr2 = ImageOrientation.getRowImagePosition(image2);
			Vector3d vc2 = ImageOrientation.getColumnImagePosition(image2);
			if (vr1 != null && vc1 != null && vr2 != null && vc2 != null) {
				return hasSameOrientation(vr1, vc1, vr2, vc2);
			}
		}
		return false;
	}

	public static boolean hasSameOrientation(Vector3d vr1, Vector3d vc1, Vector3d vr2, Vector3d vc2) {
		// Test if the two images have the same orientation
		if (vr1 != null && vc1 != null && vr2 != null && vc2 != null) {
			CutSurface plan1 = ImageOrientation.getCutsurface(vr1, vc1);
			CutSurface plan2 = ImageOrientation.getCutsurface(vr2, vc2);

			if (plan1 != null && !plan1.equals(CutSurface.OBLIQUE)) {
				return plan1.equals(plan2);
			}
			// If oblique search and if the plan has approximately the same orientation
			Vector3d normal1 = VectorUtils.computeNormalOfSurface(vr1, vc1);
			Vector3d normal2 = VectorUtils.computeNormalOfSurface(vr2, vc2);
			if (normal1 != null && normal2 != null) {
				normal1.mul(normal2);
				// A little tolerance
				return normal1.x + normal1.y + normal1.z > 0.95;
			}
		}
		return false;
	}
}
