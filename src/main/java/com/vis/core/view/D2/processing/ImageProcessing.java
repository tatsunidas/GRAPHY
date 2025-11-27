package com.vis.core.view.D2.processing;

import java.awt.Color;
import java.awt.geom.Rectangle2D;

import javax.swing.JOptionPane;

import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.mpr.PlanarSupport;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

public class ImageProcessing {
	
	public ImageProcessing(){}
	
	public void invert(ImagePlus imp) {
		imp.getProcessor().invert();//invertLUT??
		imp.updateImage();
	}
	
	public void windowing(ImagePlus imp, double currentMin, double currentMax) {
		if(imp == null) {
			return;
		}
		if(imp.getType() == ImagePlus.COLOR_RGB) {
			//fail safe
			if(imp.getProcessor().getSnapshotPixels() == null) {
				imp.getProcessor().snapshot();
			}
			imp.getProcessor().reset();
		}
		imp.setDisplayRange(currentMin, currentMax);
		imp.updateAndDraw();
	}
	
	public ImagePlus zoom(ImagePlus imp, double mag) {
		int interpType = imp.getProcessor().getInterpolationMethod();
		ImagePlus imp2 = null;
		if(interpType == ImageProcessor.BILINEAR) {
			imp2 = imp.resize((int)(imp.getWidth()*mag), (int)(imp.getHeight()*mag), "bilinear");
		}else if(interpType == ImageProcessor.BICUBIC) {
			imp2 = imp.resize((int)(imp.getWidth()*mag), (int)(imp.getHeight()*mag), "bicubic");
		}else {
			//Use NONE(nearest)
			imp2 = imp.resize((int)(imp.getWidth()*mag), (int)(imp.getHeight()*mag), "nearest");
		}
		return imp2;
	}
	
	public void rotate(ImagePlus imp, double angle) {
		imp.getProcessor().rotate(angle);
		imp.updateAndDraw();
	}
	
	public ImagePlus rotateRight(ImagePlus imp, double currentAngle) {
		Calibration cal = imp.getCalibration().copy();
		ImageProcessor ip = imp.getProcessor().rotateRight();
		imp = new ImagePlus("replica", ip);
		imp.setCalibration(cal);
		//explicit
//		double angle = currentAngle+90;
//		rotate(imp,angle);
		imp.updateImage();
		return imp;
	}
	
	public ImagePlus rotateLeft(ImagePlus imp, double currentAngle) {
		Calibration cal = imp.getCalibration().copy();
		ImageProcessor ip = imp.getProcessor().rotateLeft();
		imp = new ImagePlus("replica", ip);
		imp.setCalibration(cal);
		//explicit
//		double angle = currentAngle+90;
//		rotate(imp,angle);
		imp.updateImage();
		return imp;
	}
	
	public void rotate180(ImagePlus imp, double currentAngle) {
		//explicit
		double angle = currentAngle+180;
		rotate(imp,angle);
	}
	
	public void flipHF(ImagePlus imp) {
//		imp.getProcessor().flipHorizontal();
//		imp.updateImage();
		// DO NOT USE
		imp.getProcessor().flipVertical();
		imp.updateAndDraw();// IMPORTANT
		imp.getProcessor().flipHorizontal();
		imp.updateAndDraw();// IMPORTANT
		//以下のコードでもOK
//		ImageProcessor ip = imp.getProcessor();
//		int width = ip.getWidth();
//		int height = ip.getHeight();
//		// 画像の高さの半分までループする (中央行はスワップ不要)
//		for (int y = 0; y < height / 2; y++) {
//			int topRowIndex = y;
//			int bottomRowIndex = height - 1 - y;
//
//			int[] row_top = new int[width];
//			ip.getRow(0, topRowIndex, row_top, width);
//			int[] row_bottom = new int[width];
//			ip.getRow(0, bottomRowIndex, row_bottom, width);
//
//			ip.putRow(0, topRowIndex, row_bottom, width);
//			ip.putRow(0, bottomRowIndex, row_top, width);
//		}
//		
//		// 【奇数行の修正】: 画像の高さが奇数の場合、中央の行を左右反転させる
//        if (height % 2 != 0) {
//            int middleRowIndex = height / 2;
//            int[] row_middle = new int[width];
//            
//            // 中央の行を取得
//            ip.getRow(0, middleRowIndex, row_middle, width);
//
//            // 左右反転を打ち消すために、配列を左右反転させる
////            reverseArray(row_middle); 
//            
//            // 修正された行を書き戻す
//            ip.putRow(0, middleRowIndex, row_middle, width);
//        }
//        ip.flipHorizontal();
//		imp.updateAndDraw();
	}

	public void flipLR(ImagePlus imp) {
//		imp.getProcessor().flipHorizontal();
//		imp.updateAndDraw();
		imp.lock(); // 画像をロック
        try {
            ImageProcessor ip = imp.getProcessor();
            int width = ip.getWidth();
            int height = ip.getHeight();

            // テンポラリにピクセルを保持するための配列/変数
            // RGB画像の場合、ピクセル値はint配列または単一のintとして扱われる
            int tempPixel;

            // すべての行 (y) についてループ
            for (int y = 0; y < height; y++) {
                // 行の半分 (width / 2) までループ
                for (int x = 0; x < width / 2; x++) {

                    int leftX = x;
                    int rightX = width - 1 - x;

                    // 1. 左側のピクセル値を取得
                    // getPixel(x, y)は、画像タイプに応じたObject (e.g., int[], int, byte)を返す
                    tempPixel = ip.getPixel(leftX, y);

                    // 2. 右側のピクセル値を左側に設定
                    // getPixel(rightX, y)の結果を左側にputPixel
                    ip.putPixel(leftX, y, ip.getPixel(rightX, y));

                    // 3. テンポラリに保存した左側のピクセル値を右側に設定
                    ip.putPixel(rightX, y, tempPixel);
                }
            }
        } finally {
            imp.unlock(); // 処理が終わったら必ずロック解除
        }
        
        imp.updateAndDraw();
	}
	
	@SuppressWarnings("unused")
	private void reverseArray(int[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            int temp = array[i];
            array[i] = array[array.length - 1 - i];
            array[array.length - 1 - i] = temp;
        }
    }
	
	
	public ImagePlus cropRect(ImagePlus imp/*already setSlice(n)*/, RoiObj rectRoi, boolean processSeries) {
		if (imp==null || rectRoi == null) {
			Log.logger.warning("Images or ROI is null, return null.");
			return null;
		}
		RoiObj roi = rectRoi;
		RoiType type = roi.getRoiType();
		if (type != RoiType.RECTANGLE && type != RoiType.OVAL && type != RoiType.POLYGON) {
			return null;
		}
		Rectangle2D rect = roi.getBounds();
//		Roi r = new RoiConverter().convert2Roi(roi);
		Roi r = new Roi(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
		Calibration cal = imp.getCalibration().copy();
		if(!processSeries) {
			ImageProcessor ip = imp.getProcessor().duplicate();
			String hdr = imp.getStack().getSliceLabel(imp.getCurrentSlice());
			ip.setRoi(r);
			ImageProcessor ip_ = ip.crop().duplicate();
			ImagePlus crop = new ImagePlus(imp.getTitle()+"_CROP", ip_);
			crop.setProperty("Info", hdr);
			crop.deleteRoi();
			//update image position patient
			Vector3d ipp = new PlanarSupport().getNewImagePositionPatient2D(imp, rect.getX(), rect.getY(), imp.getCurrentSlice());
			GDicomTools.setImagePositionPatient(crop, 1, new double[] {ipp.x,ipp.y,ipp.z});
			//update instance uid.
			GDicomTools.setTag(crop, 1, "0008,0018", UIDUtils.createUID());
			GDicomTools.setTag(crop, 1, "0028,0011", ""+ip_.getWidth());//columns
			GDicomTools.setTag(crop, 1, "0028,0010", ""+ip_.getHeight());//rows
			crop.setCalibration(cal);
			return crop;
		}else {
			int size = imp.getNSlices();
			ImageStack stack = new ImageStack();
			for(int i=0; i<size; i++) {
				imp.setSlice(i+1);
				ImageProcessor ip = imp.getProcessor().duplicate();
				ip.setRoi(r);
				ip = ip.crop();
				ImageStack is = new ImageStack(ip.getWidth(),ip.getHeight());
				is.addSlice(imp.getStack().getSliceLabel(i+1), ip);
				//update uid
				ImagePlus temp = new ImagePlus(""+(i+1), is);
				GDicomTools.setTag(temp, 1, "0008,0018", UIDUtils.createUID());
				GDicomTools.setTag(temp, 1, "0028,0011", ""+ip.getWidth());//columns
				GDicomTools.setTag(temp, 1, "0028,0010", ""+ip.getHeight());//rows
				/*
				 * In Crop, will change W H attribute values.
				 *  Here, replace label to default.
				 */
				temp.deleteRoi();
				stack.addSlice(temp.getStack().getSliceLabel(1), temp.getProcessor());
			}
			ImagePlus im = new ImagePlus(imp.getTitle()+"_CROP", stack);
			im.setCalibration(cal);
			return im;
		}
	}
	
	public ImagePlus cut(ImagePlus imp, RoiObj roi, boolean processSeries) {
		if(imp==null || roi == null) {
			Log.logger.warning("Images or ROI is null, return null.");
			return null;
		}
		RoiType roiType = roi.getRoiType();
		if (roiType == RoiType.ANGLE || roiType == RoiType.ARROW || roiType == RoiType.FREELINE || roiType == RoiType.POINT
				|| roiType == RoiType.LINE) {
			JOptionPane.showMessageDialog(Viewer2DScreen.getInstance(), "You need set closed type roi. return null.");
			return null;
		}
		Calibration cal = imp.getCalibration().copy();
		ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roi);
		ImageStack stack = new ImageStack();
		if(!processSeries) {
			/*after setSlice(N)*/
			ImageProcessor ip = imp.getProcessor().duplicate();
			/** Sets fill/draw color. */
			ip.setColor(Color.BLACK);
			ip.fill(ijRoi);
			ImageStack is = new ImageStack();
			is.addSlice(imp.getStack().getSliceLabel(imp.getCurrentSlice()), ip);
			ImagePlus temp = new ImagePlus("", is);
			GDicomTools.setTag(temp, 1, "0008,0018", UIDUtils.createUID());//inst uid
			stack.addSlice(temp.getStack().getSliceLabel(1), temp.getProcessor());
			ImagePlus cut = new ImagePlus(imp.getTitle()+"_CUT", stack);
			cut.setProperty("Info", imp.getStack().getSliceLabel(imp.getCurrentSlice()));
			cut.setCalibration(cal);
			cut.resetDisplayRange();
			return cut;
		}else {
			int size = imp.getNSlices();
			for(int i=0; i<size; i++) {
				imp.setSlice(i+1);
				ImageProcessor ip = imp.getProcessor().duplicate();
				/** Sets the foreground fill/draw color. */
				ip.setColor(Color.BLACK);
				ip.fill(ijRoi);
				ImageStack is = new ImageStack();
				is.addSlice(imp.getStack().getSliceLabel(i+1), ip);
				ImagePlus temp = new ImagePlus(""+(i+1), is);
				GDicomTools.setTag(temp, 1, "0008,0018", UIDUtils.createUID());
				stack.addSlice(temp.getStack().getSliceLabel(1), temp.getProcessor());
			}
			ImagePlus im = new ImagePlus(imp.getTitle()+"_CUT", stack);
			im.resetDisplayRange();
			im.setCalibration(cal);
			return im;
		}
	}
}
