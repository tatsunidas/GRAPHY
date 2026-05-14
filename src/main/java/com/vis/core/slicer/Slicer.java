package com.vis.core.slicer;

import ij.*;
import ij.process.*;
import ij.measure.*;
import java.util.*;
import java.util.List;
import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.core.view.D2.ui.orientation.SlicePlane;
import com.vis.dicom.image.GDicomTools;

public class Slicer {

	private ImagePlus ref;
	double min = Double.MAX_VALUE;
	double raw_min = Double.MAX_VALUE;
	
	public static final int SLICECUT = 0;
	public static final int MEAN = 1;
	public static final int MAX = 2;
	public static final int MIN = 3;
	public static final int MEDIAN = 4;
	public static final int MODE = 5;
	
	public Slicer(ImagePlus ref) {
		setReferenceImage(ref);
		Calibration cal = ref.getCalibration();
		int size = ref.getNSlices();
		for(int i=0;i<size;i++) {
			ref.setSlice(i+1);
			double raw_v = ref.getProcessor().getStats().min;
			double v = cal.getCValue(raw_v);
			if(min > v) {
				min = v;
				raw_min = raw_v;
			}
		}
	}
	
	public void setReferenceImage(ImagePlus imp){
		this.ref =imp;
	}
	
//	public ImageProcessor slice(SlicePlane slicePlane, int mode/*reconstruction mode*/) {
//		if(mode == SLICECUT) {
//			
//			int w = (int)slicePlane.getGeometryOfSlice().getDimensions().y;//column size
//			int h = (int)slicePlane.getGeometryOfSlice().getDimensions().x;//row size
//			
//			int parent_w = ref.getWidth();
//			int parent_h = ref.getHeight();
//			int parent_s = ref.getNSlices();
//			List<Vector3d> pixCoord = slicePlane.computeVoxelCoordinatesInPixelCoords(ref);
//			
//			// === 座標変換のデバッグログ START ===
//			int centerIdx = (h / 2) * w + (w / 2);
//			if (pixCoord != null && pixCoord.size() > centerIdx) {
//				Vector3d first = pixCoord.get(0);
//				Vector3d center = pixCoord.get(centerIdx);
//				Vector3d last = pixCoord.get(pixCoord.size() - 1);
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] Ref Volume limits: W=%d, H=%d, Slices=%d", parent_w, parent_h, parent_s));
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] First pixel (0,0) mapped to Ref(x,y,z): [%.2f, %.2f, %.2f]", first.x, first.y, first.z));
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] Center pixel mapped to Ref(x,y,z): [%.2f, %.2f, %.2f]", center.x, center.y, center.z));
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] Last pixel mapped to Ref(x,y,z): [%.2f, %.2f, %.2f]", last.x, last.y, last.z));
//			} else {
//				com.vis.core.log.Log.logger.severe("[SLICER_PIX_DEBUG] pixCoord is null or empty!");
//			}
//			// === 座標変換のデバッグログ END ===
//
//			ImageProcessor ip = ref.getProcessor();
//			int oobCount = 0;
//			int nanCount = 0;
//
//			if(ip instanceof ByteProcessor) {
//				byte[] pixels = new byte[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
//						pixels[i] = (byte)raw_min;
//						nanCount++;
//						continue;
//					}
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (byte)raw_min;
//						oobCount++;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (byte) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] ByteProcessor Result -> OOB: %d, NaN: %d", oobCount, nanCount));
//				return new ByteProcessor(w, h, pixels);
//				
//			}else if(ip instanceof ShortProcessor) {
//				short[] pixels = new short[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					// ★ 追加: NaNによる座標破壊を防ぐ
//					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
//						pixels[i] = (short)raw_min;
//						nanCount++;
//						continue;
//					}
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (short)raw_min;
//						oobCount++;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (short) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] ShortProcessor Result -> Total: %d, OOB: %d, NaN: %d", (w*h), oobCount, nanCount));
//				ShortProcessor sp = new ShortProcessor(w, h);
//				sp.setPixels(pixels);
//				return sp;
//				
//			}else if(ip instanceof FloatProcessor) {
//				float[] pixels = new float[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
//						pixels[i] = (float)raw_min;
//						nanCount++;
//						continue;
//					}
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (float)raw_min;
//						oobCount++;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (float) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] FloatProcessor Result -> OOB: %d, NaN: %d", oobCount, nanCount));
//				return new FloatProcessor(w, h, pixels);
//				
//			}else if(ip instanceof ColorProcessor) {
//				int[] pixels = new int[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
//						pixels[i] = (int)raw_min;
//						nanCount++;
//						continue;
//					}
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (int)raw_min;
//						oobCount++;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (int) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] ColorProcessor Result -> OOB: %d, NaN: %d", oobCount, nanCount));
//				return new ColorProcessor(w, h, pixels);
//			}
//		}else {
//			return createSlice(slicePlane, ref, mode);
//		}
//		return null;
//	}
//	
//	public ImageProcessor createSlice(SlicePlane slicePlane, ImagePlus ref, int mode) {
//		
//		double refZ = GDicomTools.getVoxelDepth(ref);
//		double resolution = slicePlane.getGeometryOfSlice().getVoxelSpacing().z/refZ;
//		int numOfSubSlice = (int)Math.round(resolution);
//		if(numOfSubSlice <= 2/*see, PlanarSupport.divideSlice*/) {
//			return slice(slicePlane, SLICECUT);
//		}
//		
//		int w = (int)slicePlane.getGeometryOfSlice().getDimensions().y;
//		int h = (int)slicePlane.getGeometryOfSlice().getDimensions().x;
//		
//		int parent_w = ref.getWidth();
//		int parent_h = ref.getHeight();
//		int parent_s = ref.getNSlices();
//		ImageProcessor ip = ref.getProcessor();
//		
//		List<SlicePlane> subPlanes = PlanarSupport.divideSlice(slicePlane.getGeometryOfSlice(), numOfSubSlice);
//		
//		ImageStack subStack = new ImageStack((int)slicePlane.getGeometryOfSlice().getDimensions().y, (int)slicePlane.getGeometryOfSlice().getDimensions().x);
//		
//		for(SlicePlane sub: subPlanes) {
//			List<Vector3d> pixCoord = sub.computeVoxelCoordinatesInPixelCoords(ref);
//			if(ip instanceof ByteProcessor) {
//				byte[] pixels = new byte[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (byte)raw_min;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (byte) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				subStack.addSlice(new ByteProcessor(w, h, pixels));
//			}else if(ip instanceof ShortProcessor) {
//				short[] pixels = new short[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (short)raw_min;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (short) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				ShortProcessor sp = new ShortProcessor(w, h);
//				sp.setPixels(pixels);
//				subStack.addSlice(sp);
//			}else if(ip instanceof FloatProcessor) {
//				float[] pixels = new float[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (short)raw_min;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (float) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				subStack.addSlice(new FloatProcessor(w, h, pixels));
//			}else if(ip instanceof ColorProcessor) {
//				int[] pixels = new int[w*h];
//				for(int i=0;i<w*h;i++) {
//					Vector3d pixPos = pixCoord.get(i);
//					if ((pixPos.x < 0 || pixPos.y < 0 || pixPos.z < 0)||(pixPos.x > parent_w - 1 || pixPos.y > parent_h - 1 || pixPos.z > parent_s - 1)) {
//						pixels[i] = (short)raw_min;
//						continue;
//					}
//					ref.setSlice((int)pixPos.z+1);
//					pixels[i] = (int) ref.getProcessor().get((int)pixPos.x, (int)pixPos.y);
//				}
//				subStack.addSlice(new ColorProcessor(w, h, pixels));
//			}
//		}
//		return applyCalculateMode(subStack, mode);
//	}
	
	public ImageProcessor slice(SlicePlane slicePlane, int mode/*reconstruction mode*/) {
		if(mode == SLICECUT) {
			
			int w = (int)slicePlane.getGeometryOfSlice().getDimensions().y;//column size
			int h = (int)slicePlane.getGeometryOfSlice().getDimensions().x;//row size
			
			int parent_w = ref.getWidth();
			int parent_h = ref.getHeight();
			int parent_s = ref.getNSlices();
			List<Vector3d> pixCoord = slicePlane.computeVoxelCoordinatesInPixelCoords(ref);
			
			// === ★ 検証用デバッグログ START ===
			int centerIdx = (h / 2) * w + (w / 2);
			if (pixCoord != null && pixCoord.size() > centerIdx) {
				Vector3d first = pixCoord.get(0);
				Vector3d center = pixCoord.get(centerIdx);
				Vector3d last = pixCoord.get(pixCoord.size() - 1);
				com.vis.core.log.Log.logger.info(String.format("[SLICER_PIX_DEBUG] Ref Volume: W=%d, H=%d, Slices=%d",
						parent_w, parent_h, parent_s));
				com.vis.core.log.Log.logger.info(String.format(
						"[SLICER_PIX_DEBUG] First(0,0) -> Ref(x,y,z): [%.2f, %.2f, %.2f]", first.x, first.y, first.z));
				com.vis.core.log.Log.logger.info(String.format(
						"[SLICER_PIX_DEBUG] Center -> Ref(x,y,z): [%.2f, %.2f, %.2f]", center.x, center.y, center.z));
				com.vis.core.log.Log.logger.info(String
						.format("[SLICER_PIX_DEBUG] Last -> Ref(x,y,z): [%.2f, %.2f, %.2f]", last.x, last.y, last.z));
			}
			// === ★ 検証用デバッグログ END ===
			
			// ★ 超高速化: UI更新を伴う setSlice() を避けるため、全スライスのプロセッサをキャッシュする
			ImageStack stack = ref.getStack();
			ImageProcessor[] cachedProcs = new ImageProcessor[parent_s];
			for (int s = 0; s < parent_s; s++) {
				cachedProcs[s] = stack.getProcessor(s + 1);
			}
			
			ImageProcessor ip = ref.getProcessor();
			
			if(ip instanceof ByteProcessor) {
				byte[] pixels = new byte[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (byte)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (byte)raw_min;
						continue;
					}
					// ★ キャッシュから直接値を取得
					pixels[i] = (byte) cachedProcs[z].get(x, y);
				}
				return new ByteProcessor(w, h, pixels);
				
			} else if(ip instanceof ShortProcessor) {
				short[] pixels = new short[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (short)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (short)raw_min;
						continue;
					}
					pixels[i] = (short) cachedProcs[z].get(x, y);
				}
				ShortProcessor sp = new ShortProcessor(w, h);
				sp.setPixels(pixels);
				return sp;
				
			} else if(ip instanceof FloatProcessor) {
				float[] pixels = new float[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (float)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (float)raw_min;
						continue;
					}
					pixels[i] = (float) cachedProcs[z].get(x, y);
				}
				return new FloatProcessor(w, h, pixels);
				
			} else if(ip instanceof ColorProcessor) {
				int[] pixels = new int[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (int)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (int)raw_min;
						continue;
					}
					pixels[i] = (int) cachedProcs[z].get(x, y);
				}
				return new ColorProcessor(w, h, pixels);
			}
		} else {
			return createSlice(slicePlane, ref, mode);
		}
		return null;
	}
	
	public ImageProcessor createSlice(SlicePlane slicePlane, ImagePlus ref, int mode) {
		
		double refZ = GDicomTools.getVoxelDepth(ref);
		double resolution = slicePlane.getGeometryOfSlice().getVoxelSpacing().z/refZ;
		int numOfSubSlice = (int)Math.round(resolution);
		if(numOfSubSlice <= 2/*see, PlanarSupport.divideSlice*/) {
			return slice(slicePlane, SLICECUT);
		}
		
		int w = (int)slicePlane.getGeometryOfSlice().getDimensions().y;
		int h = (int)slicePlane.getGeometryOfSlice().getDimensions().x;
		
		int parent_w = ref.getWidth();
		int parent_h = ref.getHeight();
		int parent_s = ref.getNSlices();
		ImageProcessor ip = ref.getProcessor();
		
		// ★ 超高速化: createSlice側もキャッシュする
		ImageStack stack = ref.getStack();
		ImageProcessor[] cachedProcs = new ImageProcessor[parent_s];
		for (int s = 0; s < parent_s; s++) {
			cachedProcs[s] = stack.getProcessor(s + 1);
		}
		
		List<SlicePlane> subPlanes = PlanarSupport.divideSlice(slicePlane.getGeometryOfSlice(), numOfSubSlice);
		ImageStack subStack = new ImageStack((int)slicePlane.getGeometryOfSlice().getDimensions().y, (int)slicePlane.getGeometryOfSlice().getDimensions().x);
		
		for(SlicePlane sub: subPlanes) {
			List<Vector3d> pixCoord = sub.computeVoxelCoordinatesInPixelCoords(ref);
			
			if(ip instanceof ByteProcessor) {
				byte[] pixels = new byte[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (byte)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (byte)raw_min;
						continue;
					}
					pixels[i] = (byte) cachedProcs[z].get(x, y);
				}
				subStack.addSlice(new ByteProcessor(w, h, pixels));
				
			} else if(ip instanceof ShortProcessor) {
				short[] pixels = new short[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (short)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (short)raw_min;
						continue;
					}
					pixels[i] = (short) cachedProcs[z].get(x, y);
				}
				ShortProcessor sp = new ShortProcessor(w, h);
				sp.setPixels(pixels);
				subStack.addSlice(sp);
				
			} else if(ip instanceof FloatProcessor) {
				float[] pixels = new float[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (float)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (float)raw_min;
						continue;
					}
					pixels[i] = (float) cachedProcs[z].get(x, y);
				}
				subStack.addSlice(new FloatProcessor(w, h, pixels));
				
			} else if(ip instanceof ColorProcessor) {
				int[] pixels = new int[w*h];
				for(int i=0;i<w*h;i++) {
					Vector3d pixPos = pixCoord.get(i);
					if (Double.isNaN(pixPos.x) || Double.isNaN(pixPos.y) || Double.isNaN(pixPos.z)) {
						pixels[i] = (int)raw_min;
						continue;
					}
					int x = (int)pixPos.x; int y = (int)pixPos.y; int z = (int)pixPos.z;
					if (x < 0 || y < 0 || z < 0 || x >= parent_w || y >= parent_h || z >= parent_s) {
						pixels[i] = (int)raw_min;
						continue;
					}
					pixels[i] = (int) cachedProcs[z].get(x, y);
				}
				subStack.addSlice(new ColorProcessor(w, h, pixels));
			}
		}
		return applyCalculateMode(subStack, mode);
	}

	public ImageProcessor slice(ImagePlus imp, ij.gui.Roi roi) {
		Dynamic_Reslice slicer = new Dynamic_Reslice(imp);
		return slicer.getSlice(imp, roi);
	}
	
	private ImageProcessor applyCalculateMode(ImageStack subPlanes, int mode) {
		int w = subPlanes.getWidth();
		int h = subPlanes.getHeight();
		int s = subPlanes.getSize();
		float[] res = new float[subPlanes.getWidth()*subPlanes.getHeight()];
		int itr = 0;
		for(int y=0; y< h; y++) {
			for(int x=0; x<w; x++) {
				int[] v = new int[s];
				for(int z=0; z<s; z++) {
					ImageProcessor ip = subPlanes.getProcessor(z+1);
					v[z] = ip.get(x, y);
				}
				float pix = (float)min;
				if(mode == MEAN) {
					pix = (float)calculateAverage(v);
				}else if(mode == MAX) {
					pix = (float)findMax(v);
				}else if(mode == MIN) {
					pix = (float)findMin(v);
				}else if(mode == MEDIAN) {
					pix = (float)findMedian(v);
				}else if(mode == MODE) {
					pix = (float)findMode(v);
				}
				res[itr++] = pix;
			}
		}
		ImageProcessor ip = subPlanes.getProcessor(1);
		ImageProcessor ip_res = null;
		if(ip instanceof ByteProcessor) {
			byte[] arr = toByte(res);
			ip_res = new ByteProcessor(w, h, arr);
		}else if(ip instanceof ShortProcessor) {
			short[] arr = toShort(res);
			ip_res = new ShortProcessor(w, h);
			ip_res.setPixels(arr);
		}else if(ip instanceof FloatProcessor) {
			ip_res = new FloatProcessor(w, h, res);
		}else if(ip instanceof ColorProcessor) {
			int[] arr =toInt(res);
			ip_res = new ColorProcessor(w, h, arr);
		}
		return ip_res;
	}
	
    public static double calculateAverage(int[] array) {
        int sum = 0;
        for (int num : array) {
            sum += num;
        }
        return (double) sum / array.length;
    }

    public static int findMax(int[] array) {
        int max = array[0];
        for (int num : array) {
            if (num > max) {
                max = num;
            }
        }
        return max;
    }

    public static int findMin(int[] array) {
        int min = array[0];
        for (int num : array) {
            if (num < min) {
                min = num;
            }
        }
        return min;
    }

    public static double findMedian(int[] array) {
        Arrays.sort(array);
        int middle = array.length / 2;
        if (array.length % 2 == 0) {
            return (array[middle - 1] + array[middle]) / 2.0;
        } else {
            return array[middle];
        }
    }

    public static int findMode(int[] array) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();
        for (int num : array) {
            frequencyMap.put(num, frequencyMap.getOrDefault(num, 0) + 1);
        }

        int mode = array[0];
        int maxCount = 0;

        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mode = entry.getKey();
            }
        }

        return mode;
    }
    
	private byte[] toByte(float[] arr) {
		byte[] byteArray = new byte[arr.length];
		for (int i = 0; i < arr.length; i++) {
			byteArray[i] = (byte) Math.round(arr[i]);
		}
		return byteArray;
	}

	private short[] toShort(float[] floatArray) {
		short[] shortArray = new short[floatArray.length];
		for (int i = 0; i < floatArray.length; i++) {
			shortArray[i] = (short) Math.round(floatArray[i]);
		}
		return shortArray;
	}
	
	private int[] toInt(float[] floatArray) {
		int[] intArray = new int[floatArray.length];
		for (int i = 0; i < floatArray.length; i++) {
			intArray[i] = (int) Math.round(floatArray[i]);
		}
		return intArray;
	}
}