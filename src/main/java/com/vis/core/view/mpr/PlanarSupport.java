package com.vis.core.view.mpr;

import java.util.Arrays;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.measure.Calibration;

public class PlanarSupport {

	public static void main(String[] args) {
//		String dir = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\LGG-104\\06-26-2000-MRI Hd wow-05523\\4-Gad Ax T2 Straight-38151";
//		ImagePlus mri = FolderOpener.open(dir);

		double[] sagittalIOP = { 0, 1, 0, 0, 0, 1 }; // Sagittal IOP

//	    // 1. Y軸周りに -90度回転（Sagittal -> Axial）
//	    double[] intermediateIOP = rotateImageOrientationPatient(sagittalIOP, 0, 90, 0);
//
//	    // 2. Z軸周りに 90度回転（正しい向きにする）
//	    double[] axialIOP = rotateImageOrientationPatient(intermediateIOP, 0, 0, 90);

		double[] axialIOP = rotateImageOrientationPatient2(sagittalIOP, 0, 90, 90);

		// 1 Axial IOP: [-1.0, 6.123233995736766E-17, 0.0, 6.123233995736766E-17, 1.0,
		// 6.123233995736766E-17]
		// 結果を出力
		System.out.println("Axial IOP: " + Arrays.toString(axialIOP));

	}

	private static final String ipp = "0020,0032";// image position patient
	private static final String iop = "0020,0037";// image orientation patient

	public static String planarOf(DicomObject dcm) {
		double[] image_ori = dcm.getDoubles(Tag.Image​Orientation​Patient);
		if (image_ori == null) {
			return CutSurface.UNKNOWN.name();
		}
		return ImageOrientation.getCutSurface(dcm).name();
	}
	
	public static CutSurface planarOf(ImagePlus dcm) {
		double[] image_ori = GDicomTools.getDoubles(dcm, iop);
		if (image_ori == null) {
			return CutSurface.UNKNOWN;
		}
		return ImageOrientation.getCutSurface(dcm);
	}

	/**
	 * 
	 * https://nipy.org/nibabel/dicom/dicom_orientation.html
	 * 
	 * @param srcImp
	 * @param col:      col pos on src imp
	 * @param row:      row pos on src imp
	 * @param slicePos: slice pos on src imp (1 to N)
	 * @return
	 */
	public Vector3d getNewImagePositionPatient2D(ImagePlus srcImp, double col, double row, int slicePos) {
		srcImp.setPosition(slicePos);
		double[] ipp = GDicomTools.getDoubles(srcImp, PlanarSupport.ipp);// imagePositionPatient
		double[] iop = GDicomTools.getDoubles(srcImp, PlanarSupport.iop);// imageOrientationPatient
		if (ipp == null || iop == null) {
			return null;
		}

		Calibration cal = srcImp.getCalibration();
		double px = cal.pixelWidth;// Column in Dicom Pixel Spacing
		double py = cal.pixelHeight;// Row in Dicom Pixel Spacing

		/*
		 * it code is also OK to use single image.
		 */
		double[][] mat0 = new double[][] { new double[] { iop[3] * py, iop[0] * px, 0.0, ipp[0] },
				new double[] { iop[4] * py, iop[1] * px, 0.0, ipp[1] },
				new double[] { iop[5] * py, iop[2] * px, 0.0, ipp[2] }, new double[] { 0.0, 0.0, 0.0, 1.0 } };

		double[][] mat1 = new double[][] { new double[] { row }, new double[] { col }, new double[] { 0 }, // keep zero.
				new double[] { 1 } };

		RealMatrix matrix0 = new Array2DRowRealMatrix(mat0);
		RealMatrix matrix1 = new Array2DRowRealMatrix(mat1);
		RealMatrix res = matrix0.multiply(matrix1);// same as dot products
		double[][] newIpp = res.getData();

		// System.out.println("new ipp:"+newIpp[0][0]+" "+newIpp[1][0]+"
		// "+newIpp[2][0]);
		return new Vector3d(newIpp[0][0], newIpp[1][0], newIpp[2][0]);
	}

	/**
	 * 
	 * row direction cosine is; [→]
	 * col direction cosine is; [↓]
	 * 
	 * Row direction is means X direction in RCS. Column direction is means Y
	 * direction in RCS.
	 * 
	 * If Anatomical Orientation Type (0010,2210) is absent or has a value of BIPED,
	 * the x-axis is increasing to the left hand side of the patient. The y-axis is
	 * increasing to the posterior side of the patient. The z-axis is increasing
	 * toward the head of the patient.
	 * 
	 * Plus/Minus of direction cosine is determine direction.
	 * If plus, direction specify plus direction, minus means another side direction. 
	 * 
	 * @param srcImp
	 * @param rotateX
	 * @param rotateY
	 * @param rotateZ
	 * @return
	 */
	public static double[] rotateImageOrientationPatient(ImagePlus srcImp, int rotateX, int rotateY, int rotateZ) {
		double[] iop = GDicomTools.getDoubles(srcImp, PlanarSupport.iop);
		return rotateImageOrientationPatient(iop, rotateX, rotateY, rotateZ);
	}

	public static double[] rotateImagePositionPatient(double[] ipp, double rotateX, double rotateY, double rotateZ) {
		ipp = applyRotation(ipp, rotateX, rotateY, rotateZ);
		return ipp;
	}

	public static double[] rotateImageOrientationPatient(double[] iop, double rotateX, double rotateY, double rotateZ) {
		// iop is [rx, ry, rz, cx, cy, cz]
		double[] rowDirectionCos = { iop[0], iop[1], iop[2] };
		double[] colDirectionCos = { iop[3], iop[4], iop[5] };

		// Create a rotation matrix and apply rotation to each axis
		double[] rotatedRow = rotateVector(rowDirectionCos, rotateX, rotateY, rotateZ);
		double[] rotatedCol = rotateVector(colDirectionCos, rotateX, rotateY, rotateZ);

		return new double[] { rotatedRow[0], rotatedRow[1], rotatedRow[2], rotatedCol[0], rotatedCol[1],
				rotatedCol[2] };
	}

	public static double[] rotateVector(double[] vec, double rotateX, double rotateY, double rotateZ) {
		// X axis rotate matrix
		double[][] rotX = { { 1, 0, 0 }, { 0, Math.cos(Math.toRadians(rotateX)), -Math.sin(Math.toRadians(rotateX)) },
				{ 0, Math.sin(Math.toRadians(rotateX)), Math.cos(Math.toRadians(rotateX)) } };

		// Y axis rotate matrix
		double[][] rotY = { { Math.cos(Math.toRadians(rotateY)), 0, Math.sin(Math.toRadians(rotateY)) }, { 0, 1, 0 },
				{ -Math.sin(Math.toRadians(rotateY)), 0, Math.cos(Math.toRadians(rotateY)) } };

		// Z axis rotate matrix
		double[][] rotZ = { { Math.cos(Math.toRadians(rotateZ)), -Math.sin(Math.toRadians(rotateZ)), 0 },
				{ Math.sin(Math.toRadians(rotateZ)), Math.cos(Math.toRadians(rotateZ)), 0 }, { 0, 0, 1 } };

		// apply each rotate
		vec = multiplyMatrixAndVector(rotX, vec);
		vec = multiplyMatrixAndVector(rotY, vec);
		vec = multiplyMatrixAndVector(rotZ, vec);

		return vec;
	}

	private static double[] multiplyMatrixAndVector(double[][] matrix, double[] vector) {
		double[] result = new double[3];
		for (int i = 0; i < 3; i++) {
			result[i] = matrix[i][0] * vector[0] + matrix[i][1] * vector[1] + matrix[i][2] * vector[2];
		}
		return result;
	}

	/**
	 * can reproduce same result of rotateImageOrientationPatient.
	 * 
	 * @param iop
	 * @param rotateX
	 * @param rotateY
	 * @param rotateZ
	 * @return
	 */
	public static double[] rotateImageOrientationPatient2(double[] iop, double rotateX, double rotateY,
			double rotateZ) {
		double[] row = { iop[0], iop[1], iop[2] };
		double[] col = { iop[3], iop[4], iop[5] };
		row = applyRotation(row, rotateX, rotateY, rotateZ);
		col = applyRotation(col, rotateX, rotateY, rotateZ);
		return new double[] { row[0], row[1], row[2], col[0], col[1], col[2] };
	}

	public static double[] applyRotation(double[] vec, double rotateX, double rotateY, double rotateZ) {
		// X axis rotation
		vec = rotateAroundX(vec, rotateX);
		// Y axis rotation
		vec = rotateAroundY(vec, rotateY);
		// Z axis rotation
		vec = rotateAroundZ(vec, rotateZ);
		return vec;
	}

	public static double[] rotateAroundX(double[] vec, double angle) {
		double radians = Math.toRadians(angle);
		double y = vec[1] * Math.cos(radians) - vec[2] * Math.sin(radians);
		double z = vec[1] * Math.sin(radians) + vec[2] * Math.cos(radians);
		return new double[] { vec[0], y, z };
	}

	public static double[] rotateAroundY(double[] vec, double angle) {
		double radians = Math.toRadians(angle);
		double x = vec[0] * Math.cos(radians) + vec[2] * Math.sin(radians);
		double z = -vec[0] * Math.sin(radians) + vec[2] * Math.cos(radians);
		return new double[] { x, vec[1], z };
	}

	public static double[] rotateAroundZ(double[] vec, double angle) {
		double radians = Math.toRadians(angle);
		double x = vec[0] * Math.cos(radians) - vec[1] * Math.sin(radians);
		double y = vec[0] * Math.sin(radians) + vec[1] * Math.cos(radians);
		return new double[] { x, y, vec[2] };
	}

	/**
	 * 
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public double[] rotateOrthogonallyImageOrientationPatient(ImagePlus from/* done setPosition */,
			com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface to) {
		com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface planar = com.vis.core.view.D2.ui.orientation.ImageOrientation
				.getCutSurface(from);
		if (planar.name().equals(to.name())) {
			return GDicomTools.getDoubles(from, PlanarSupport.iop);
		}
		switch (planar) {
		case SAGITTAL:
			// YZ 0\1\0\0\0\-1 :e.g, head iop.
			if (to.name().equals(CutSurface.AXIAL.name())) {
				return rotateImageOrientationPatient(from, 0, -90, 90);
			} else {// YZ to XZ
				return rotateImageOrientationPatient(from, 0, 0, -90);
			}
		case CORONAL:
			// XZ 1\0\0\0\0\-1:e.g, head iop.
			// XZ to XY
			if (to.name().equals(CutSurface.AXIAL.name())) {
				return rotateImageOrientationPatient(from, 90, 0, 0);
			} else {// XZ to YZ
				return rotateImageOrientationPatient(from, 0, 0, 90);
			}
		case AXIAL:
		case OBLIQUE:// here, treat as axial
		case UNKNOWN:
		default:
			// XY to XZ
			if (to.name().equals(CutSurface.CORONAL.name())) {
				return rotateImageOrientationPatient(from, -90, 0, 0);
			} else {// XY to YZ
				return rotateImageOrientationPatient(from, -90, 90, 0);
			}
		}
	}

}
