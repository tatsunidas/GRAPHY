package com.vis.core.view.mpr;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;

public class PlanarSupport {

	//debug
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		/*
		 * Orthogonalな画像の再構成のためのIOP計算には、回転は使えない。
		 * 回転すると、全方向余弦が回転する。
		 * AX、COR、SAGのIOP変換で回転が使えるのは完全に回転がない面の状態のときのみ。
		 * 多少でもどこかの成分に回転が含まれる場合、RowColから直行ベクトルを使う。
		 * 直交した方向余弦を得たいときは、calculateNormal(row, col)で得る。
		 */
		//axi src
//		ImagePlus src = FolderOpener.open(
//				"/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");

		//cor src
//		String corDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/T1COR";
//		ImagePlus src = FolderOpener.open(corDir);
		
		//sag src
		String sagDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
		ImagePlus src = FolderOpener.open(sagDir);
		
		//Sag to Axi EXAMPLE
		double[] srcIOP = GDicomTools.getImageOrientationPatient(src, 1);
		double[] row = new double[] {srcIOP[0],srcIOP[1],srcIOP[2]};//Y vertor
		double[] col = new double[] {srcIOP[3],srcIOP[4],srcIOP[5]};//Z vector
		double[] norm = calculateNormal(row, col);//X vector
		/*
		 * {-1,0,0} to {1,0,0} of X vector in HFS
		 */
		if(norm[0] < 0.0) {
			norm[0] *= -1;
			norm[1] *= -1;
			norm[2] *= -1;
		}
		
		double[] axi_iop = new double[] {norm[0],norm[1],norm[2],row[0],row[1],row[2]};
		
		System.out.println(java.util.Arrays.toString(srcIOP));
		System.out.println(java.util.Arrays.toString(axi_iop));
		
		LocalizerPoster.validateDirectionCosines(axi_iop);//clear
	}

	private static final String ipp = "0020,0032";// image position patient
	private static final String iop = "0020,0037";// image orientation patient

	/**
	 * Use ImageOrientation.getCutSurface(dcm) instead this.
	 * @param dcm
	 * @return
	 */
	public static String planarOf(DicomObject dcm) {
		double[] image_ori = dcm.getDoubles(Tag.Image​Orientation​Patient);
		if (image_ori == null) {
			return CutSurface.UNKNOWN.name();
		}
		return ImageOrientation.getCutSurface(dcm).name();
	}
	
	/**
	 * Use ImageOrientation.getCutSurface(dcm) instead this.
	 * @param dcm
	 * @return
	 */
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
	 * @param col:      col pos on src imp (0 to W-1)
	 * @param row:      row pos on src imp (0 to H-1)
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
		
		double[] newIOP = new double[] { rotatedRow[0], rotatedRow[1], rotatedRow[2], rotatedCol[0], rotatedCol[1],
				rotatedCol[2] };
		
		for(int i=0; i<newIOP.length; i++) {
			newIOP[i] = truncate(newIOP[i], 6);
		}
		
		return newIOP;
	}

	public static double[] rotateVector(double[] vec, double rotateX, double rotateY, double rotateZ) {
		// X axis rotate matrix
		double[][] rotX = { 
				{ 1, 0, 0 }, 
				{ 0, Math.cos(Math.toRadians(rotateX)), -Math.sin(Math.toRadians(rotateX)) },
				{ 0, Math.sin(Math.toRadians(rotateX)), Math.cos(Math.toRadians(rotateX)) } };

		// Y axis rotate matrix
		double[][] rotY = { 
				{ Math.cos(Math.toRadians(rotateY)), 0, Math.sin(Math.toRadians(rotateY)) }, 
				{ 0, 1, 0 },
				{ -Math.sin(Math.toRadians(rotateY)), 0, Math.cos(Math.toRadians(rotateY)) } };

		// Z axis rotate matrix
		double[][] rotZ = { 
				{ Math.cos(Math.toRadians(rotateZ)), -Math.sin(Math.toRadians(rotateZ)), 0 },
				{ Math.sin(Math.toRadians(rotateZ)), Math.cos(Math.toRadians(rotateZ)), 0 }, 
				{ 0, 0, 1 } };

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
		y = truncate(y, 6);
		z = truncate(z, 6);
		return new double[] { vec[0], y, z };
	}

	public static double[] rotateAroundY(double[] vec, double angle) {
		double radians = Math.toRadians(angle);
		double x = vec[0] * Math.cos(radians) + vec[2] * Math.sin(radians);
		double z = -vec[0] * Math.sin(radians) + vec[2] * Math.cos(radians);
		x = truncate(x, 6);
		z = truncate(z, 6);
		return new double[] { x, vec[1], z };
	}

	public static double[] rotateAroundZ(double[] vec, double angle) {
		double radians = Math.toRadians(angle);
		double x = vec[0] * Math.cos(radians) - vec[1] * Math.sin(radians);
		double y = vec[0] * Math.sin(radians) + vec[1] * Math.cos(radians);
		x = truncate(x, 6);
		y = truncate(y, 6);
		return new double[] { x, y, vec[2] };
	}
	
	/**
	 * 
	 * HFP:Head First-Prone
	 * HFS:Head First-Supine
	 * HFDR:Head First-Decubitus Right
	 * HFDL:Head First-Decubitus Left
	 * FFDR:Feet First-Decubitus Right
	 * FFDL:Feet First-Decubitus Left
	 * FFP:Feet First-Prone
	 * FFS:Feet First-Supine
	 * LFP:Left First-Prone
	 * LFS:Left First-Supine
	 * RFP:Right First-Prone
	 * RFS:Right First-Supine
	 * AFDR:Anterior First-Decubitus Right
	 * AFDL:Anterior First-Decubitus Left
	 * PFDR:Posterior First-Decubitus Right
	 * PFDL:Posterior First-Decubitus Left
	 * 
	 * @param imp
	 * @return
	 */
	public static boolean isHeadFirst(ImagePlus imp) {
		String ptpos = GDicomTools.getTag(imp, "0018,5100");
		if(ptpos == null) {
			return true;//handle as head first
		}
		if(ptpos.startsWith("F")) {
			return false;
		}
		//H** and others
		return true;
	}
	
	public static double truncate(double value, int places) {
		if (places < 0)
			throw new IllegalArgumentException();
		BigDecimal bd = BigDecimal.valueOf(value);
		bd = bd.setScale(places, RoundingMode.DOWN); // 小数点以下を指定した桁で切り捨て
		return bd.doubleValue();
	}

	public static double[] truncate(double[] values, int places) {
		double[] v = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			v[i] = truncate(values[i], places);
		}
		return v;
	}
	
	public static double[] calculateNormal(double[] row, double[] col) {
	    // 外積を計算
	    double nx = row[1] * col[2] - row[2] * col[1];
	    double ny = row[2] * col[0] - row[0] * col[2];
	    double nz = row[0] * col[1] - row[1] * col[0];

	    // ベクトルの長さを計算して正規化
	    double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
	    return new double[] {nx / length, ny / length, nz / length};
	}

}
