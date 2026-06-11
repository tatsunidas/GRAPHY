package com.vis.core.view.D2.ui.orientation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;

/**
 * @author tatsunidas
 */
public class PlanarSupport {

	//debug
	public static void main(String[] args) {
		/*
		 * AX、COR、SAGのIOP変換で回転が使えるのは完全に回転がない面の状態のときのみ。
		 * 多少でもどこかの成分に回転が含まれる場合、RowColから直行ベクトルを使う。
		 * 直交した方向余弦を得たいときは、calculateNormal(row, col)で得る。
		 */
		//axi src
//		ImagePlus src =  ij.plugin.FolderOpener.open(
//				"/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151");
		
//		ImagePlus src = FolderOpener.open("/home/tatsunidas/デスクトップ/LUNG1-246");

		//cor src
//		String corDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/T1COR";
//		ImagePlus src = FolderOpener.open(corDir);
		
		//sag src
//		String sagDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
//		ImagePlus src = FolderOpener.open(sagDir);
		
		//Sag to Axi EXAMPLE
//		double[] srcIOP = GDicomTools.getImageOrientationPatient(src, 1);
//		double[] row = new double[] {srcIOP[0],srcIOP[1],srcIOP[2]};//Z vertor
//		double[] col = new double[] {srcIOP[3],srcIOP[4],srcIOP[5]};//Y vector
//		Vector3d norm = calculateNormal(new Vector3d(row), new Vector3d(col), true);//X vector
//		/*
//		 * {-1,0,0} to {1,0,0} of X vector in HFS
//		 */
//		if(norm.x < 0.0) {
//			norm.x *= -1;
//			norm.y *= -1;
//			norm.z *= -1;
//		}
//		
//		double[] axi_iop = new double[] {norm.x,norm.y,norm.z,row[0],row[1],row[2]};
//		
//		LocalizerPoster.validateDirectionCosines(axi_iop);//clear
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
		return ImageOrientation.getCutSurface(dcm);
	}

	/**
	 * Nibabel is RAS coordinates, but DICOM is LPS.
	 * https://nipy.org/nibabel/dicom/dicom_orientation.html
	 * 
	 * @param srcImp
	 * @param col:      col pos on src imp (0 to W-1)
	 * @param row:      row pos on src imp (0 to H-1)
	 * @param slicePos: slice pos on src imp (1 to N)
	 * @return
	 */
	public static Vector3d getNewImagePositionPatient2D(ImagePlus srcImp, double col, double row, int slicePos) {
		if(slicePos < 1 || slicePos > srcImp.getNSlices()) {
			throw new IllegalArgumentException("Slice position should be 1 <= slicePos <= stackSize");
		}
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
		return new Vector3d(newIpp[0][0], newIpp[1][0], newIpp[2][0]);
	}
	
	public static Vector3d getNewImagePositionPatient(int w, int h, double[] voxelSize, double[] iop, Vector3d centerIPP) {
		Vector3d rcsCenter = centerIPP; // ipp on slice center
		int rows = h;
		int cols = w;
		double voxelSizeX = voxelSize[0]; // x
		double voxelSizeY = voxelSize[1]; // y
		double voxelSizeZ = voxelSize[2]; // z

		Vector3d rowVector = new Vector3d(iop[0], iop[1], iop[2]).normalize();
		Vector3d colVector = new Vector3d(iop[3], iop[4], iop[5]).normalize();
		Vector3d zDirection = new Vector3d(rowVector).cross(colVector).normalize();

		Vector3d rowOffset = new Vector3d(rowVector).mul(-cols * voxelSizeX / 2.0);
		Vector3d colOffset = new Vector3d(colVector).mul(-rows * voxelSizeY / 2.0);
		Vector3d zOffset = new Vector3d(zDirection).mul(-voxelSizeZ / 2.0);

		return new Vector3d(rcsCenter).add(rowOffset).add(colOffset).add(zOffset);
	}

	/**
	 * 
	 * row direction cosine is [→]
	 * col direction cosine is [↓]
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
	public static double[] rotateImageOrientationPatient(ImagePlus srcImp, int rotRowX, int rotRowY, int rotRowZ, int rotColX, int rotColY, int rotColZ, double[] defVal) {
		double[] iop = GDicomTools.getDoubles(srcImp, PlanarSupport.iop);
		return rotateImageOrientationPatient(iop, rotRowX, rotRowY, rotRowZ, rotColX, rotColY, rotColZ, defVal);
	}
	
	public static double[] rotateImageOrientationPatient(double[] iop, double rotateRowX, double rotateRowY, double rotateRowZ, double rotateColX, double rotateColY, double rotateColZ, double[] defaultVal) {
		// iop is [rx, ry, rz, cx, cy, cz]
		double[] rowDirectionCos = { iop[0], iop[1], iop[2] };
		double[] colDirectionCos = { iop[3], iop[4], iop[5] };

		// Create a rotation matrix and apply rotation to each axis
		double[] rotatedRow = rotateVector(rowDirectionCos, rotateRowX, rotateRowY, rotateRowZ);
		double[] rotatedCol = rotateVector(colDirectionCos, rotateColX, rotateColY, rotateColZ);
		
		Vector3d row = d2v(rotatedRow).normalize();
		Vector3d col = d2v(rotatedCol).normalize();
		
		try {
			LocalizerPoster.validateDirectionCosines(row,col);
		}catch(IllegalArgumentException e) {
			System.out.println(e.getMessage());
			return defaultVal;
		}
		
		double[] newIOP = new double[] { row.x, row.y, row.z, col.x, col.y, col.z};
		
		return newIOP;
	}

	public static double[] rotateImagePositionPatient(double[] ipp, double rotateX, double rotateY, double rotateZ) {
		ipp = applyRotation(ipp, rotateX, rotateY, rotateZ);
		return ipp;
	}

	public static Vector3d rotateImageOrientationPatient(Vector3d rowOrCol, double rotateX, double rotateY, double rotateZ) {
		// iop is [rx, ry, rz, cx, cy, cz]
		// Create a rotation matrix and apply rotation to each axis
		Vector3d rotated = rotateVector(rowOrCol, rotateX, rotateY, rotateZ);
		rotated.x = truncate(rotated.x, 6);
		rotated.y = truncate(rotated.y, 6);
		rotated.z = truncate(rotated.z, 6);
		return rotated;
	}
	
	public static Vector3d rotateVector(Vector3d vec, double rotateX, double rotateY, double rotateZ) {
		Vector3d v = new Vector3d(vec);
		v.rotateX(Math.toRadians(rotateX));
		v.rotateY(Math.toRadians(rotateY));
		v.rotateZ(Math.toRadians(rotateZ));
		v.x = truncate(v.x, 6);
		v.y = truncate(v.y, 6);
		v.z = truncate(v.z, 6);
		return v;
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
	
	public static void rotateFromCenter(Vector3d vertex, Vector3d center, double rotateX, double rotateY,
			double rotateZ) {
		// move shift to center
		vertex.x -= center.x;
		vertex.y -= center.y;
		vertex.z -= center.z;

		// rotate X in YZ plane
		double tempY = vertex.y;
		double tempZ = vertex.z;
		vertex.y = tempY * Math.cos(rotateX) - tempZ * Math.sin(rotateX);
		vertex.z = tempY * Math.sin(rotateX) + tempZ * Math.cos(rotateX);

		// rotate Y in XZ plane
		double tempX = vertex.x;
		tempZ = vertex.z;
		vertex.x = tempX * Math.cos(rotateY) + tempZ * Math.sin(rotateY);
		vertex.z = -tempX * Math.sin(rotateY) + tempZ * Math.cos(rotateY);

		// rotate Z in XY plane
		tempX = vertex.x;
		tempY = vertex.y;
		vertex.x = tempX * Math.cos(rotateZ) - tempY * Math.sin(rotateZ);
		vertex.y = tempX * Math.sin(rotateZ) + tempY * Math.cos(rotateZ);

		// back to center
		vertex.x += center.x;
		vertex.y += center.y;
		vertex.z += center.z;
	}

	public static double[] multiplyMatrixAndVector(double[][] matrix, double[] vector) {
//		double[] result = new double[3];
//		for (int i = 0; i < 3; i++) {
//			result[i] = matrix[i][0] * vector[0] + matrix[i][1] * vector[1] + matrix[i][2] * vector[2];
//		}
		double[] result = new double[matrix.length];
	    for (int i = 0; i < matrix.length; i++) {
	        for (int j = 0; j < vector.length; j++) {
	            result[i] += matrix[i][j] * vector[j];
	        }
	    }
	   return result;
	}
	
	public static Vector3d[] transposeMatrix(Vector3d iop_row, Vector3d iop_col, Vector3d iop_norm) {
		Vector3d transposedRow = new Vector3d(iop_row.x, iop_col.x, iop_norm.x);
		Vector3d transposedCol = new Vector3d(iop_row.y, iop_col.y, iop_norm.y);
		Vector3d transposedNorm = new Vector3d(iop_row.z, iop_col.z, iop_norm.z);
		return new Vector3d[] { transposedRow, transposedCol, transposedNorm };
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
			// タグがない場合は標準的な Head First と仮定
			return true;//handle as head first
		}
		if(ptpos.startsWith("F")) {
			return false;
		}
		//H** and others
		return true;
	}
	
	/**
	 * DICOM RCS-LPS space coordinates is oriented by patient position.
	 * Z+ means direction to head side. Z- is foot side.
	 * Y+ means direction to posterior, Y- is anterior.
	 * X+ means direction to left side, X- is right side.
	 * @param imp
	 * @return
	 */
	public static boolean isSlicingToUpperZSide(ImagePlus axialImp) {
		if(ImageOrientation.getCutSurface(axialImp) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("This images are not AXIAL...");
		}
		int size = axialImp.getNSlices();
		int prev_pos = axialImp.getCurrentSlice();
		if(size > 1) {
			double[] ipp1 = GDicomTools.getImagePositionPatient(axialImp, 1);
			double[] ippN = GDicomTools.getImagePositionPatient(axialImp, size);
			if(ipp1 != null && ippN != null) {
				//back to prev pos
				axialImp.setSlice(prev_pos);
				return ipp1[2] < ippN[2];
			}
		}
		return false;
	}
	
	/**
	 * Check slicing A->P direction in RCS.
	 * @param imp
	 * @return
	 */
	public static boolean isSlicingToUpperYSide(ImagePlus corImp) {
		if(ImageOrientation.getCutSurface(corImp) != CutSurface.CORONAL) {
			throw new IllegalArgumentException("This images are not CORONAL...");
		}
		int size = corImp.getNSlices();
		int prev_pos = corImp.getCurrentSlice();
		if(size > 1) {
			double[] ipp1 = GDicomTools.getImagePositionPatient(corImp, 1/*slice pos*/);
			double[] ippN = GDicomTools.getImagePositionPatient(corImp, size/*slice pos*/);
			if(ipp1 != null && ippN != null) {
				//back to prev pos
				corImp.setSlice(prev_pos);
				return ipp1[1] < ippN[1];
			}
		}
		return false;
	}
	
	/**
	 * Check slicing R->L direction in RCS.
	 * @param imp
	 * @return
	 */
	public static boolean isSlicingToUpperXSide(ImagePlus imp) {
		if(ImageOrientation.getCutSurface(imp) != CutSurface.SAGITTAL) {
			throw new IllegalArgumentException("This images are not SAGITTAL...");
		}
		int size = imp.getNSlices();
		int prev_pos = imp.getCurrentSlice();
		if(size > 1) {
			double[] ipp1 = GDicomTools.getImagePositionPatient(imp, 1/*slice pos*/);
			double[] ippN = GDicomTools.getImagePositionPatient(imp, size/*slice pos*/);
			if(ipp1 != null && ippN != null) {
				//back to prev pos
				imp.setSlice(prev_pos);
				return ipp1[0] < ippN[0];
			}
		}
		return false;
	}
		
	/**
	 * 入力された Raw スタックを、解剖学的な標準順序（LPS）に基づき、3D/MPR用に並び替えます。
	 * Axial: Z+ -> Z- (Head to Foot), to decrease
	 * Coronal: Y- -> Y+ (A to P), to increase
	 * Sagittal: X- -> X+ (R to L), to increase
	 */
    public static void standardizeStackOrientation(ImagePlus imp) {
        if (imp == null) return;
        int nSlices = imp.getNSlices();
        if (nSlices < 2) return;

        double[] ipp1 = GDicomTools.getImagePositionPatient(imp, 1);
        double[] ippN = GDicomTools.getImagePositionPatient(imp, nSlices);
        if (ipp1 == null || ippN == null) return;

        boolean needsReversal = false;
        CutSurface basePlane = ImageOrientation.getCutSurface(imp);

        if (basePlane == CutSurface.AXIAL) {
            // 目標: Z増加 (-100 -> 100)。 現在が Z減少 (100 -> -100) なら反転。
            if (ipp1[2] > ippN[2]) needsReversal = true;
        } else if (basePlane == CutSurface.CORONAL) {
            // 目標: Y増加 (-100 -> 100)。 現在が Y減少 (100 -> -100) なら反転。
            if (ipp1[1] > ippN[1]) needsReversal = true;
        } else if (basePlane == CutSurface.SAGITTAL) {
            // 目標: X増加 (-100 -> 100)。 現在が X減少 (100 -> -100) なら反転。
            if (ipp1[0] > ippN[0]) needsReversal = true;
        } else if (basePlane == CutSurface.OBLIQUE) {
            // 斜位の場合: 最も変化量の大きい軸で判断
            double dx = Math.abs(ippN[0] - ipp1[0]);
            double dy = Math.abs(ippN[1] - ipp1[1]);
            double dz = Math.abs(ippN[2] - ipp1[2]);

            if (dz >= dx && dz >= dy) { // Axial寄り
                if (ipp1[2] > ippN[2]) needsReversal = true;
            } else if (dy >= dx && dy >= dz) { // Coronal寄り
                if (ipp1[1] > ippN[1]) needsReversal = true;
            } else { // Sagittal寄り
                if (ipp1[0] > ippN[0]) needsReversal = true;
            }
        }

        if (needsReversal) {
            reverseStack(imp);
            Log.logger.log(Level.INFO, "Stack order reversed to LPS standard: " + basePlane);
        }
        
        Log.logger.info("Volume orientation was standardized.");
    }

    private static void reverseStack(ImagePlus imp) {
        ImageStack stack = imp.getStack();
        int n = stack.getSize();
        ImageStack reversedStack = new ImageStack(stack.getWidth(), stack.getHeight());

        for (int i = n; i >= 1; i--) {
            // 重要: getSliceLabel(i) を渡すことで、IPPやIOP等のメタデータ文字列が
            // そのまま新しい位置のスライスに引き継がれます。
            reversedStack.addSlice(stack.getSliceLabel(i), stack.getProcessor(i));
        }
        
        // 元のスタックに設定されていたCalibrationをコピーして適用
        Calibration cal = imp.getCalibration().copy();
        imp.setStack(reversedStack);
        imp.setCalibration(cal);
    }
    

    /**
     * ベクトルの外積（Cross Product）を計算
     */
    public static double[] crossProduct(double[] v1, double[] v2) {
    	Vector3d v1_ = new Vector3d(v1);
    	Vector3d v2_ = new Vector3d(v2);
    	Vector3d cross = crossProduct(v1_, v2_, true);
    	return new double[] {cross.x(), cross.y(), cross.z()};
//        return new double[] {
//            v1[1] * v2[2] - v1[2] * v2[1], // x
//            v1[2] * v2[0] - v1[0] * v2[2], // y
//            v1[0] * v2[1] - v1[1] * v2[0]  // z
//        };
    }
    
	/**
	 * This will return v1.cross(v2).
	 * 
	 * E.g.,
	 * Row=(1,0,0),Col=(0,1,0)
	 * Row.cross(Col)=(0,0,1)
	 * Col.cross(Row)=(0,0,−1)
	 * 
	 * @param v1 = vector 1
	 * @param v2 = vector 2
	 * @param normalize
	 * @return
	 */
	public static Vector3d crossProduct(Vector3d v1, Vector3d v2, boolean normalize) {
		Vector3d norm = new Vector3d();
		v1.cross(v2, norm);//keep col cross row.
		if(normalize) {
			norm = norm.normalize();
		}
		return norm;
	}

    /**
     * ベクトルの内積（Dot Product）を計算
     */
    public static double dotProduct(double[] v1, double[] v2) {
        return v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
    }
	
//	public static int getOriginSlicePosition(int totalSliceSize, boolean isSlicingIncreaseAxisCoord, boolean isHeadFirst) {
//		if (isHeadFirst && isSlicingIncreaseAxisCoord) return totalSliceSize;
//		if (!isHeadFirst && isSlicingIncreaseAxisCoord) return 1;
//		if (isHeadFirst && !isSlicingIncreaseAxisCoord) return 1;
//		if (!isHeadFirst && !isSlicingIncreaseAxisCoord) return totalSliceSize;
//		return 1;
//	}
	
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
	
	public static Vector3d truncate(Vector3d values, int places) {
		Vector3d v = new Vector3d(values);
		v.x = truncate(v.x, places);
		v.y = truncate(v.y, places);
		v.z = truncate(v.z, places);
		return v;
	}
	
	public static double[] v2d(Vector3d vec) {
		return new double[] {vec.x, vec.y, vec.z};
	}
	
	public static Vector3d d2v(double[] vec) {
		return new Vector3d (vec);
	}
	
	public static List<SlicePlane> divideSlice(
			GeometryOfSlice orgGeo, int resolution) {
		if (resolution %2 ==0 ) {
			Log.logger.fine("SubResolution must be odd number.");
			resolution -= 1;
		}
		if (resolution < 0) {
			throw new IllegalArgumentException("SubResolution must be > 0");
		}
		Vector3d originalIPP = new Vector3d(orgGeo.getTLHC());
		Vector3d rowVector = new Vector3d(orgGeo.getRow());
		Vector3d colVector = new Vector3d(orgGeo.getColumn());
		Vector3d dimension = new Vector3d(orgGeo.getDimensions());
		Vector3d voxelSize = new Vector3d(orgGeo.getVoxelSpacing());
		Double sliceThickness = orgGeo.getSliceThickness();
		if(sliceThickness == null || sliceThickness == Double.NaN) {
			sliceThickness = orgGeo.getVoxelSpacing().z;
		}
		
		// Rs = Rc × Rr
		Vector3d normalVector = new Vector3d(colVector).cross(rowVector).normalize();
		// Create SubResolution SlicePlanes
		double step = sliceThickness / (resolution-1);
		
		voxelSize.z = step;
		
		List<SlicePlane> dividedPlanes = new ArrayList<>();
        // 分割をオリジナルIPPを中心に配置
        int half = resolution / 2;
        for (int i = -half; i <= half; i++) {
            Vector3d newIPP = new Vector3d(normalVector).mul(i * step).add(originalIPP);
            SlicePlane inner = new SlicePlane(rowVector, colVector, newIPP, voxelSize, step, dimension);
            dividedPlanes.add(inner);
        }
		return dividedPlanes;
	}
	
	/**
	 * ベクトルを正規化し、直交性を保証する（NaN回避・堅牢化版）
	 */
	public static void normalizeAndOrthogonalize(Vector3d r, Vector3d c) {
	    // 定数: 計算誤差とみなす閾値
	    final double EPSILON = 1.0e-6;

	    // 1. Row (r) のチェックと正規化
	    if (r.lengthSquared() < EPSILON) {
	        System.err.println("Error: Row vector is too small or zero. Resetting to default.");
	        r.set(1, 0, 0); // 強制的にX軸にリセット
	    } else {
	        r.normalize();
	    }
	    
	    // この時点で r は NaN ではなく長さ1であることが保証される

	    // 2. 法線 (normal) の計算
	    Vector3d normal = new Vector3d();
	    r.cross(c, normal); // normal = r × c

	    // 3. 法線 (normal) のチェックと正規化
	    // r と c が平行、あるいは c がゼロの場合、外積結果はゼロベクトルになる
	    if (normal.lengthSquared() < EPSILON) {
	        System.err.println("Error: Vectors are parallel or Col is zero. Attempting fallback.");
	        
	        // 救済措置: r とは異なる適当なベクトル(Z軸など)を使って法線を作る
	        // もし r が Z軸(0,0,1) に近ければ、X軸(1,0,0) を使う
	        if (Math.abs(r.z) > 0.9) {
	            r.cross(new Vector3d(1, 0, 0), normal);
	        } else {
	            r.cross(new Vector3d(0, 0, 1), normal);
	        }
	        
	        // それでもダメなら強制リセット
	        if (normal.lengthSquared() < EPSILON) {
	            normal.set(0, 1, 0); // 適当なY軸
	        }
	    }
	    
	    // 法線を正規化
	    normal.normalize();

	    // 4. Col (c) の再計算
	    // 正規化された法線と r から、直交する c を逆算する
	    normal.cross(r, c); // c = normal × r
	    c.normalize();      // 数値誤差除去のため念のため正規化

	    // 結果の確認（デバッグ用）
	    if (Double.isNaN(r.x) || Double.isNaN(c.x)) {
	        System.err.println("Critical Error: NaN persisted after fix!");
	        // 最終防衛ライン：標準的なIOPに戻す
	        r.set(1, 0, 0);
	        c.set(0, 1, 0);
	    }
	}
}
