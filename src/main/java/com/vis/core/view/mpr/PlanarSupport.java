package com.vis.core.view.mpr;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.joml.Vector3d;

import com.vis.core.view.D2.processing.ImagePlusDicomTagTools;
import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

import ij.ImagePlus;
import ij.measure.Calibration;

public class PlanarSupport {
	
	public static void main(String[] args) {
//		String dir = "D:\\Dropbox\\Graphy-WorkSpace2\\graphy-parent\\graphy-resource\\src\\test\\resources\\dicom_samples\\LGG-104\\06-26-2000-MRI Hd wow-05523\\4-Gad Ax T2 Straight-38151";
//		ImagePlus mri = FolderOpener.open(dir);
	}
	
	private final String ipp = "0020,0032";//image position patient
	private final String iop = "0020,0037";//image orientation patient
	
	public String isPlanarOf(DicomObject dcm) {
		double[] image_ori = dcm.getDoubles(Tag.Image​Orientation​Patient);
		if(image_ori == null) {
			return CutSurface.UNKNOWN.name();
		}
		return ImageOrientation.getCutSurface(dcm).name();
	}
	
	/**
	 * 
	 * @param dcm
	 * @param tag: imageplus tag format e.g., "0010,0010"
	 * @return
	 */
	public String isPlanarOf(ImagePlus dcm) {
		ImagePlusDicomTagTools tool = new ImagePlusDicomTagTools();
		double[] image_ori = tool.getDoubles(dcm, iop);
		if(image_ori == null) {
			return CutSurface.UNKNOWN.name();
		}
		return ImageOrientation.getCutSurface(dcm).name();
	}
	
	/**
	 * 
	 * https://nipy.org/nibabel/dicom/dicom_orientation.html
	 * @param srcImp
	 * @param col: col pos on src imp
	 * @param row: row pos on src imp
	 * @param slicePos: slice pos on src imp (1 to N)
	 * @return
	 */
	public Vector3d getNewImagePositionPatient2D(ImagePlus srcImp, double col, double row, int slicePos) {
		ImagePlusDicomTagTools tools = new ImagePlusDicomTagTools();
		srcImp.setPosition(slicePos);
		double[] ipp = tools.getDoubles(srcImp, this.ipp);//imagePositionPatient
		double[] iop = tools.getDoubles(srcImp, this.iop);//imageOrientationPatient
		if(ipp == null || iop == null) {
			return null;
		}
				
		Calibration cal = srcImp.getCalibration();
		double px = cal.pixelWidth;//Column in Dicom Pixel Spacing
		double py = cal.pixelHeight;//Row in Dicom Pixel Spacing
		
		/*
		 * it code is also OK to use single image.
		 */
		double[][] mat0 = new double[][] {
			new double[] {iop[3]*py,iop[0]*px,0.0,ipp[0]},
			new double[] {iop[4]*py,iop[1]*px,0.0,ipp[1]},
			new double[] {iop[5]*py,iop[2]*px,0.0,ipp[2]},
			new double[] {0.0      ,0.0      ,0.0,1.0}
		};
		
		double[][] mat1 = new double[][] {
			new double[] {row},
			new double[] {col},
			new double[] {0},//keep zero.
			new double[] {1}
		};
		
		RealMatrix matrix0 = new Array2DRowRealMatrix(mat0);
		RealMatrix matrix1 = new Array2DRowRealMatrix(mat1);
		RealMatrix res = matrix0.multiply(matrix1);//same as dot products
		double[][] newIpp = res.getData();
		
		//System.out.println("new ipp:"+newIpp[0][0]+" "+newIpp[1][0]+" "+newIpp[2][0]);
		return new Vector3d(newIpp[0][0],newIpp[1][0],newIpp[2][0]);
	}
	
	/**
	 * @deprecated
	 * 
	 * This method is need fix. (can not change z axis value.)
	 * https://nipy.org/nibabel/dicom/dicom_orientation.html#working-out-the-z-coordinates-for-a-set-of-slices
	 * 
	 * @param srcImp
	 * @param col
	 * @param row
	 * @param slicePos
	 * @return
	 */
	public Vector3d getNewImagePositionPatient3D(ImagePlus srcImp, double col, double row, int slicePos) {
		if(srcImp.getNSlices() == 1 || slicePos == 1) {
			return getNewImagePositionPatient2D(srcImp, col, row, 1);
		}
		ImagePlusDicomTagTools tools = new ImagePlusDicomTagTools();
		srcImp.setPosition(1);//first image
		double[] T1 = tools.getDoubles(srcImp, this.ipp);//imagePositionPatient of first slice
		srcImp.setPosition(slicePos);//last position
		double[] Tn = tools.getDoubles(srcImp, this.ipp);//imagePositionPatient of last slice
		double[] iop = tools.getDoubles(srcImp, this.iop);//imageOrientationPatient
		if(T1 == null || Tn == null || iop == null) {
			return null;
		}

		int N = slicePos;//srcImp.getNSlices();
		Calibration cal = srcImp.getCalibration();
		double px = cal.pixelWidth;//Column in Dicom Pixel Spacing
		double py = cal.pixelHeight;//Row in Dicom Pixel Spacing

		double[][] mat0 = new double[][] {
			new double[] {iop[3]*py,iop[0]*px,(Tn[0]-T1[0])/(N-1),T1[0]},
			new double[] {iop[4]*py,iop[1]*px,(Tn[1]-T1[1])/(N-1),T1[1]},
			new double[] {iop[5]*py,iop[2]*px,(Tn[2]-T1[2])/(N-1),T1[2]},
			new double[] {0.0      ,0.0      ,0.0                ,1.0}
		};
		
		double[][] mat1 = new double[][] {
			new double[] {row},
			new double[] {col},
			new double[] {0},
			new double[] {1}
		};
		
		RealMatrix matrix0 = new Array2DRowRealMatrix(mat0);
		RealMatrix matrix1 = new Array2DRowRealMatrix(mat1);
		RealMatrix res = matrix0.multiply(matrix1);//same as dot products
		double[][] newIpp = res.getData();
		System.out.println("new ipp:"+newIpp[0][0]+" "+newIpp[1][0]+" "+newIpp[2][0]);
		return new Vector3d(newIpp[0][0],newIpp[1][0],newIpp[2][0]);
	}
	
	/**
	 * @deprecated
	 * 
	 * This method is need fix. (can not change z axis value.)
	 * https://nipy.org/nibabel/dicom/dicom_orientation.html#working-out-the-z-coordinates-for-a-set-of-slices
	 * 
	 * @param srcImp
	 * @param col
	 * @param row
	 * @param slicePos
	 * @return
	 */
	public Vector3d getNewImagePositionPatient3D_2(ImagePlus srcImp, double col, double row, int slicePos) {
		if(srcImp.getNSlices() == 1) {
			return getNewImagePositionPatient2D(srcImp, col, row, 1);
		}
		ImagePlusDicomTagTools tools = new ImagePlusDicomTagTools();
		srcImp.setPosition(1);//first image
		double[] T1 = tools.getDoubles(srcImp, this.ipp);//imagePositionPatient of first slice
		srcImp.setPosition(slicePos);//current
		double[] ipp = tools.getDoubles(srcImp, this.ipp);//imagePositionPatient of last slice
		double[] iop = tools.getDoubles(srcImp, this.iop);//imageOrientationPatient
		if(ipp == null || iop == null) {
			return null;
		}
		Vector3d col_f = new Vector3d(iop[3], iop[4], iop[5]);
		Vector3d row_f = new Vector3d(iop[0], iop[1], iop[2]);
		Vector3d n_ = col_f.cross(row_f);
		
		Calibration cal = srcImp.getCalibration();
		double px = cal.pixelWidth;//Column in Dicom Pixel Spacing
		double py = cal.pixelHeight;//Row in Dicom Pixel Spacing
		double pz = cal.pixelDepth;//Row in Dicom Pixel Spacing

		double[][] mat0 = new double[][] {
			new double[] {iop[3]*py,iop[0]*px,(T1[0]+pz*slicePos*n_.x()),T1[0]},
			new double[] {iop[4]*py,iop[1]*px,(T1[1]+pz*slicePos*n_.y()),T1[1]},
			new double[] {iop[5]*py,iop[2]*px,(T1[2]+pz*slicePos*n_.z()),T1[2]},
			new double[] {0.0      ,0.0      ,0.0                		,1.0}
		};
		
		double[][] mat1 = new double[][] {
			new double[] {row},
			new double[] {col},
			new double[] {0},
			new double[] {1}
		};
		
		RealMatrix matrix0 = new Array2DRowRealMatrix(mat0);
		RealMatrix matrix1 = new Array2DRowRealMatrix(mat1);
		RealMatrix res = matrix0.multiply(matrix1);//same as dot products
		double[][] newIpp = res.getData();
		System.out.println("new ipp:"+newIpp[0][0]+" "+newIpp[1][0]+" "+newIpp[2][0]);
		return new Vector3d(newIpp[0][0],newIpp[1][0],newIpp[2][0]);
	}
	
	/**
	 * e.g,
	 * [ r00 r01 r02 r03 r04 ]
	 * [ r10 r11 r12 r13 r14 ]
	 * [ r20 r21 r22 r23 r24 ]
	 * [ r30 r31 r32 r33 r34 ]
	 * 
	 * 
	 * row direction cosine is;
	 * [ r00     --->    r04 ]
	 * 
	 * col direction cosine is;
	 * [ r00  ...]
	 * [ ||   ...]
	 * [ \/   ...]
	 * [ r30  ...]
	 * 
	 * Row direction is means X direction in RCS.
	 * Column direction is means Y direction in RCS.
	 * 
	 * Do not worry about the +/- of the angle you specify.
	 *　Whether positive or negative, it will be negative if it exceeds 90.
	 * 
	 * Proof,
	 * Math.cos(Math.toRadians(0));//1
	 * Math.cos(Math.toRadians(-0));//1
	 * Math.cos(Math.toRadians(90));//0
	 * Math.cos(Math.toRadians(-90));//0, same as 90 degrees.
	 * Math.cos(Math.toRadians(180));//-1
	 * Math.cos(Math.toRadians(-180));//-1, same as 180 degrees.
	 * Math.cos(Math.toRadians(90+100));//-0.98
	 * 
	 * Math.toDegrees(Math.acos(-1));//180
	 * 
	 * @param srcImp
	 * @param row_rotateX
	 * @param row_rotateY
	 * @param row_rotateZ
	 * @param col_rotateX
	 * @param col_rotateY
	 * @param col_rotateZ
	 * @return
	 */
	public double[] getNewImageOrientationPatient(ImagePlus srcImp, int row_rotateX, int row_rotateY, int row_rotateZ, int col_rotateX, int col_rotateY, int col_rotateZ) {
		ImagePlusDicomTagTools tools = new ImagePlusDicomTagTools();
		double[] iop = tools.getDoubles(srcImp, this.iop);
//		boolean[] negative = new boolean[6];
//		for(int i=0;i<iop.length;i++) {
//			if(String.valueOf(iop[i]).startsWith("-")) {
//				negative[i] = true;
//			}else {
//				negative[i] = false;
//			}
//			//System.out.println(negative[i]);
//		}
		return rotateImageOrientationPatient(iop, row_rotateX, row_rotateY, row_rotateZ, col_rotateX, col_rotateY, col_rotateZ);
	}
	
	/**
	 * TODO...
	 * Should be use Vector3d.rotateXYZ...
	 * 
	 * @param iop
	 * @param row_rotateX
	 * @param row_rotateY
	 * @param row_rotateZ
	 * @param col_rotateX
	 * @param col_rotateY
	 * @param col_rotateZ
	 * @return
	 */
	public double[] rotateImageOrientationPatient(double[] iop, int row_rotateX, int row_rotateY, int row_rotateZ, int col_rotateX, int col_rotateY, int col_rotateZ) {

		double row_x_angle = Math.toDegrees(Math.acos(iop[0]));
		double row_y_angle = Math.toDegrees(Math.acos(iop[1]));
		double row_z_angle = Math.toDegrees(Math.acos(iop[2]));
		double col_x_angle = Math.toDegrees(Math.acos(iop[3]));
		double col_y_angle = Math.toDegrees(Math.acos(iop[4]));
		double col_z_angle = Math.toDegrees(Math.acos(iop[5]));
		
		double row_x_cos = Math.cos(Math.toRadians(row_x_angle+row_rotateX));
		double row_y_cos = Math.cos(Math.toRadians(row_y_angle+row_rotateY));
		double row_z_cos = Math.cos(Math.toRadians(row_z_angle+row_rotateZ));
		double col_x_cos = Math.cos(Math.toRadians(col_x_angle+col_rotateX));
		double col_y_cos = Math.cos(Math.toRadians(col_y_angle+col_rotateY));
		double col_z_cos = Math.cos(Math.toRadians(col_z_angle+col_rotateZ));
		
		//validate
//		Vector3d row_v = new Vector3d(row_x_cos,row_y_cos,row_z_cos);
//		Vector3d col_v = new Vector3d(col_x_cos,col_y_cos,col_z_cos);
//		System.out.println(Math.abs(row_v.lengthSquared() - 1));//shal be zero
//		System.out.println(Math.abs(col_v.lengthSquared() - 1));
		
		double[] res = new double[] {row_x_cos,row_y_cos,row_z_cos,col_x_cos,col_y_cos,col_z_cos};
//		System.out.println("org iop : "+iop[0]+","+iop[1]+","+iop[2]+","+iop[3]+","+iop[4]+","+iop[5]);
//		System.out.println("new iop : "+res[0]+","+res[1]+","+res[2]+","+res[3]+","+res[4]+","+res[5]);
		return res;
	}
	
	/**
	 * 
	 * TODO...
	 * Should be use Vector3d.rotateXYZ...
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public double[] rotateOrthogonallyImageOrientationPatient(ImagePlus from/*done setPosition*/, com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface to) {
		com.vis.core.view.D2.processing.ImagePlusDicomTagTools tools = new ImagePlusDicomTagTools();
        PlanarSupport psup = new PlanarSupport();
        com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface planar = com.vis.core.view.D2.ui.orientation.ImageOrientation.getCutSurface(from);
        if(planar.name().equals(to.name())) {
        	return tools.getDoubles(from, this.iop);
        }
		switch (planar) {
		case SAGITTAL:
			//YZ 0\1\0\0\0\-1
			// YZ to XY
			if(to.name().equals(CutSurface.AXIAL.name())) {
				return psup.getNewImageOrientationPatient(from, -90, 90, 0, 0, -90, 90);// 1\0\0\0\1\0
			}else {// YZ to XZ
				return psup.getNewImageOrientationPatient(from, -90, 90, 0, 0, 0, 0);// 1\0\0\0\0\-1
			}
		case CORONAL:
			//XZ 1\0\0\0\0\-1
			// XZ to XY
			if(to.name().equals(CutSurface.AXIAL.name())) {
				return psup.getNewImageOrientationPatient(from, 0, 0, 0, 0, -90, 90);// 1\0\0\0\1\0
			}else {// XZ to YZ
				return psup.getNewImageOrientationPatient(from, 90, -90, 0, 0, 0, 0);// 0\1\0\0\0\-1
			}
		case AXIAL:
		case OBLIQUE:// here, treat as axial
		case UNKNOWN:
		default:
			//XY 1\0\0\0\1\0 -> 0\90\90\90\0\90
			//XY to XZ
			if(to.name().equals(CutSurface.CORONAL.name())) {
				return psup.getNewImageOrientationPatient(from, 0, 0, 0, 0, 90, 90);// 1\0\0\0\0\-1
			}else {// XY to YZ
				return psup.getNewImageOrientationPatient(from, 90, -90, 0, 0, 90, 90);// 0\1\0\0\0\-1
			}
		}
	}
	
	@Deprecated
	public Vector3d searchCoordinateByIPP(Vector3d ippAtPoint, ImagePlus src) {
		Vector3d c = null;
		double distanceError = Double.MAX_VALUE;
		int w = src.getWidth();
		int h = src.getHeight();
		int s = src.getNSlices();
		for(int k=0;k<s;k++) {
			for(int j=0;j<h;j++) {
				for(int i=0;i<w;i++) {
					Vector3d v = getNewImagePositionPatient2D(src, j, i, k+1);
					double ds = v.distance(ippAtPoint);
					if(ds < distanceError) {
						distanceError = ds;
						c = new Vector3d(i,j,k);
						if(distanceError < 0.01) {
							return c;
						}
					}
				}
			}
		}
		return c;
	}
}
