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

import java.io.File;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;

/**
 * * @author tatsunidas
 *
 */
public class VolumeLoader {

	public static VolumeData loadDicom(String filePath) {
		System.out.println("Loading DICOM/Image: " + filePath);

		// ImageJの機能で画像を読み込む
		// Openerを使うとDICOMヘッダも適切に処理してくれます
		Opener opener = new Opener();
		ImagePlus imp = null;

		if (new File(filePath).isFile()) {
			imp = opener.openImage(filePath);
		} else {
			imp = FolderOpener.open(filePath);
		}

		return loadDicom(imp);
	}

	public static VolumeData loadDicom(Praparat pp) {
		System.out.println("Loading DICOM/Image from Praparat.");
		ImagePlus imp = pp.getImagePlus(-1,-1);
		// see, loadDicom
//		PlanarSupport.standardizeStackOrientation(imp);
		return loadDicom(imp);
	}

	public static VolumeData loadDicom(ImagePlus imp) {
		if (imp == null) {
			System.err.println("Failed to load image.");
			return null;
		}
		
		//at first, know what is modality
		Modality m = Modality.is(GDicomTools.getTag(imp, Tag.Modality));
		CutSurface plane = PlanarSupport.planarOf(imp);
		if(m == Modality.CT && plane == CutSurface.AXIAL) {
			GantryTiltCorrector gtc = new GantryTiltCorrector();
			double tiltAngle = GDicomTools.getDouble(imp, 1, "0018,1120"/*Gantry/Detector Tilt*/);
			double pixelSpacingY = imp.getCalibration().pixelHeight;
			double sliceSpacing = GDicomTools.getVoxelDepth(imp);
			double reconSliceSpacing = sliceSpacing < 1d ? sliceSpacing:1d;
			imp = gtc.correctVolume3D(imp, tiltAngle, pixelSpacingY, sliceSpacing, reconSliceSpacing);
		}

		// ★ 追加1: ボクセルサイズの確実な取得 (DICOMタグからの補完)
		checkSpatialCalibration(imp);

		// ★ 追加2: DICOM空間でのスライス順序（Z方向）の標準化
		PlanarSupport.standardizeStackOrientation(imp);

		// 3. SagittalやCoronalの場合、Axial (X=LR, Y=AP, Z=SI) に再配置する
		ImagePlus axialImp = convertToAxialIfNecessary(imp);
		if (axialImp != imp) {
			imp.close(); // 古い(Axial以外の)メモリを解放
			imp = axialImp; // 以降はスライスオーダーが標準化されたVolとして扱う
		}
		
		// --- 以下はすべて「Axial化され、DICOM空間に一致した」データとしての処理 ---

		// 1. サイズ情報の取得
		int w = imp.getWidth();
		int h = imp.getHeight();
		int d = imp.getNSlices(); // スタック枚数（3Dボリュームの奥行き）

		// 2. 空間情報（ボクセルサイズ）の取得
		Calibration cal = imp.getCalibration();
		double spaX = cal.pixelWidth;
		double spaY = cal.pixelHeight;
		double spaZ = cal.pixelDepth;

		Log.logger.fine(String.format("Stack Size: %d x %d x %d", w, h, d));
		Log.logger.fine(String.format("Stack Spacing: %.3f x %.3f x %.3f", spaX, spaY, spaZ));

		// 3. ピクセルデータの抽出
		// ImageJのデータ型に応じて適切な配列を作成します
		VolumeData volume = null;
		ImageStack stack = imp.getStack();
		int sliceSize = w * h;
		int type = imp.getType();

		if (type == ImagePlus.GRAY8 || type == ImagePlus.COLOR_256) {
			System.out.println("Detected Image Type: 8-bit");
			byte[] volumeData = new byte[sliceSize * d];
			for (int z = 0; z < d; z++) {
				byte[] slice = (byte[]) stack.getPixels(z + 1);
				System.arraycopy(slice, 0, volumeData, z * sliceSize, sliceSize);
			}
			volume = new VolumeData(w, h, d, volumeData);

		} else if (type == ImagePlus.GRAY16) {
			System.out.println("Detected Image Type: 16-bit (short)");
			short[] volumeData = new short[sliceSize * d];
			for (int z = 0; z < d; z++) {
				short[] slice = (short[]) stack.getPixels(z + 1);
				System.arraycopy(slice, 0, volumeData, z * sliceSize, sliceSize);
			}
			volume = new VolumeData(w, h, d, volumeData);

		} else if (type == ImagePlus.GRAY32) {
			System.out.println("Detected Image Type: 32-bit (float)");
			float[] volumeData = new float[sliceSize * d];
			for (int z = 0; z < d; z++) {
				float[] slice = (float[]) stack.getPixels(z + 1);
				System.arraycopy(slice, 0, volumeData, z * sliceSize, sliceSize);
			}
			volume = new VolumeData(w, h, d, volumeData);

		} else if (type == ImagePlus.COLOR_RGB) {
			System.out.println("Detected Image Type: RGB (int)");
			int[] volumeData = new int[sliceSize * d];
			for (int z = 0; z < d; z++) {
				int[] slice = (int[]) stack.getPixels(z + 1);
				System.arraycopy(slice, 0, volumeData, z * sliceSize, sliceSize);
			}
			volume = new VolumeData(w, h, d, volumeData);

		} else {
			System.err.println("Unsupported image type: " + type);
			return null;
		}

		// 空間情報をセット
		volume.pixelSpacingX = spaX;
		volume.pixelSpacingY = spaY;
		volume.sliceThickness = spaZ;
		
        int nSlices = d;
        
        if (nSlices >= 2) {
            // 最初のスライス(1)と最後のスライス(N)のIPPを実測
            double[] ipp1 = com.vis.dicom.image.GDicomTools.getImagePositionPatient(imp, 1);
            double[] ippN = com.vis.dicom.image.GDicomTools.getImagePositionPatient(imp, nSlices);
            
            com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface basePlane = com.vis.core.view.D2.ui.orientation.PlanarSupport.planarOf(imp);
            boolean isHeadFirst = com.vis.core.view.D2.ui.orientation.PlanarSupport.isHeadFirst(imp);
            boolean isReversed = false;

            // VolumeLoaderと同じ反転判定
            if (ipp1 != null && ippN != null) {
                if (basePlane == com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface.AXIAL || basePlane == com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface.OBLIQUE) {
                    if ((ippN[2] < ipp1[2]) != isHeadFirst) isReversed = true;
                }
            }

            System.out.println("=== Volume Loader Debug Log ===");
            System.out.println("ipp1: [" + ipp1[0] + ", " + ipp1[1] + ", " + ipp1[2] + "]");
            System.out.println("ippN: [" + ippN[0] + ", " + ippN[1] + ", " + ippN[2] + "]");
            /*
             * オーダーは標準化しているので、常にfalse
             */
            System.out.println("isReversed by VolumeLoader: " + isReversed);

            // ★追加: 1スライス進むごとの実際の物理移動ベクトル (X, Y, Z)
            double[] stepZ = new double[3];
            stepZ[0] = (ippN[0] - ipp1[0]) / (nSlices - 1);
            stepZ[1] = (ippN[1] - ipp1[1]) / (nSlices - 1);
            stepZ[2] = (ippN[2] - ipp1[2]) / (nSlices - 1);

            // VolumeData の [Z=0] に該当する物理座標を確定させる
            double[] volumeStartIpp = ipp1;
            //fail safe
            if (isReversed) {
                volumeStartIpp = ippN; // スタックが反転されたので、[Z=0]はippNになる
                // 進行方向も逆になる
                stepZ[0] = -stepZ[0];
                stepZ[1] = -stepZ[1];
                stepZ[2] = -stepZ[2];
            }

            System.out.println("volumeStartIpp: [" + volumeStartIpp[0] + ", " + volumeStartIpp[1] + ", " + volumeStartIpp[2] + "]");
            System.out.println("stepZ vector:   [" + stepZ[0] + ", " + stepZ[1] + ", " + stepZ[2] + "]");

            // IOPの取得
            double[] iop = GDicomTools.getDoubles(imp, Tag.ImageOrientationPatient);
            
            volume.startIpp = volumeStartIpp;
            volume.iop = iop;
            volume.stepZ = stepZ;
        }else {//only have a image
        	volume.startIpp = GDicomTools.getDoubles(imp, Tag.ImagePositionPatient);
            
        	if(volume.startIpp == null) {
        		volume.startIpp = new double[] {0.,0.,0.};
        	}
        	
        	volume.iop = GDicomTools.getDoubles(imp, Tag.ImageOrientationPatient);
            
        	if(volume.iop == null) {
        		volume.iop = new double[] {1.,0.,0.,0.,1.,0.};
        	}
        	
        	volume.stepZ = calculateDummyStepZ(volume.iop, spaZ);
        }
        
		// ImageJのメモリ解放を促進
		imp.close();

		return volume;
	}

	// =========================================================
	// ユーティリティメソッド群
	// =========================================================

	private static void checkSpatialCalibration(ImagePlus imp) {
		Calibration cal = imp.getCalibration();
		double[] pixelspacing = GDicomTools.getDoubles(imp, "0028,0030");

		if (cal.pixelWidth == 1.0 && pixelspacing != null && pixelspacing.length >= 2) {
			cal.pixelWidth = pixelspacing[1];
		}
		if (cal.pixelHeight == 1.0 && pixelspacing != null && pixelspacing.length >= 1) {
			cal.pixelHeight = pixelspacing[0];
		}

		// update depth
		double depth = GDicomTools.getVoxelDepth(imp);
		if (!Double.isNaN(depth) && depth > 0) {
			cal.pixelDepth = depth;
		}
	}

	/**
	 * SagittalやCoronalのImageStackを、Axial(X=左右, Y=前後, Z=頭足)に再配置する
	 */
	private static ImagePlus convertToAxialIfNecessary(ImagePlus imp) {
		CutSurface basePlane = PlanarSupport.planarOf(imp);
		if (basePlane == CutSurface.AXIAL || basePlane == CutSurface.OBLIQUE) {
			return imp; // 既にAxialベースなら何もしない
		}

		System.out.println("Reconstructing " + basePlane + " stack to AXIAL coordinate system...");

		int w = imp.getWidth();
		int h = imp.getHeight();
		int d = imp.getNSlices();
		ImageStack stack = imp.getStack();
		int type = imp.getType();
		Calibration cal = imp.getCalibration();
//		cal.pixelDepth = GDicomTools.getVoxelDepth(imp);

		int axW, axH, axD;
		double axPx, axPy, axPz;

		if (basePlane == CutSurface.SAGITTAL) {
			// Sagittal元の軸: X=前後(w), Y=頭足(h), Z=左右(d)
			// 目指すAxial: X=左右(d), Y=前後(w), Z=頭足(h)
			axW = d;
			axH = w;
			axD = h;
			axPx = cal.pixelDepth;
			axPy = cal.pixelWidth;
			axPz = cal.pixelHeight;
		} else {
			// Coronal元の軸: X=左右(w), Y=頭足(h), Z=前後(d)
			// 目指すAxial: X=左右(w), Y=前後(d), Z=頭足(h)
			axW = w;
			axH = d;
			axD = h;
			axPx = cal.pixelWidth;
			axPy = cal.pixelDepth;
			axPz = cal.pixelHeight;
		}

		ImageStack axStack = new ImageStack(axW, axH);

		for (int z = 0; z < axD; z++) {
			if (type == ImagePlus.GRAY8 || type == ImagePlus.COLOR_256) {
				byte[] axPixels = new byte[axW * axH];
				for (int y = 0; y < axH; y++) {
					for (int x = 0; x < axW; x++) {
						if (basePlane == CutSurface.SAGITTAL) {
							byte[] sagSlice = (byte[]) stack.getPixels(x + 1);
							axPixels[y * axW + x] = sagSlice[z * w + y];
						} else {
							byte[] corSlice = (byte[]) stack.getPixels(y + 1);
							axPixels[y * axW + x] = corSlice[z * w + x];
						}
					}
				}
				axStack.addSlice(null, axPixels);

			} else if (type == ImagePlus.GRAY16) {
				short[] axPixels = new short[axW * axH];
				for (int y = 0; y < axH; y++) {
					for (int x = 0; x < axW; x++) {
						if (basePlane == CutSurface.SAGITTAL) {
							short[] sagSlice = (short[]) stack.getPixels(x + 1);
							axPixels[y * axW + x] = sagSlice[z * w + y];
						} else {
							short[] corSlice = (short[]) stack.getPixels(y + 1);
							axPixels[y * axW + x] = corSlice[z * w + x];
						}
					}
				}
				axStack.addSlice(null, axPixels);

			} else if (type == ImagePlus.GRAY32) {
				float[] axPixels = new float[axW * axH];
				for (int y = 0; y < axH; y++) {
					for (int x = 0; x < axW; x++) {
						if (basePlane == CutSurface.SAGITTAL) {
							float[] sagSlice = (float[]) stack.getPixels(x + 1);
							axPixels[y * axW + x] = sagSlice[z * w + y];
						} else {
							float[] corSlice = (float[]) stack.getPixels(y + 1);
							axPixels[y * axW + x] = corSlice[z * w + x];
						}
					}
				}
				axStack.addSlice(null, axPixels);

			} else if (type == ImagePlus.COLOR_RGB) {
				int[] axPixels = new int[axW * axH];
				for (int y = 0; y < axH; y++) {
					for (int x = 0; x < axW; x++) {
						if (basePlane == CutSurface.SAGITTAL) {
							int[] sagSlice = (int[]) stack.getPixels(x + 1);
							axPixels[y * axW + x] = sagSlice[z * w + y];
						} else {
							int[] corSlice = (int[]) stack.getPixels(y + 1);
							axPixels[y * axW + x] = corSlice[z * w + x];
						}
					}
				}
				axStack.addSlice(null, axPixels);
			}
		}

		ImagePlus axImp = new ImagePlus("Axial_Reconstructed", axStack);
		Calibration axCal = cal.copy();
		axCal.pixelWidth = axPx;
		axCal.pixelHeight = axPy;
		axCal.pixelDepth = axPz;
		axImp.setCalibration(axCal);

		return axImp;
	}
	
	// ==========================================================
	// 画像が1枚しかない場合の stepZ の計算ロジック
	// ==========================================================
	private static double[] calculateDummyStepZ(double[] iop, double sliceThickness) {
		// スライス厚が0や未定義の場合は、最低限の厚みとして 1.0mm を仮定する
		double thickness = (sliceThickness > 0) ? sliceThickness : 1.0;

		if (iop != null && iop.length >= 6) {
			// IOPから外積を計算して法線ベクトルを求める
			double nx = iop[1] * iop[5] - iop[2] * iop[4];
			double ny = iop[2] * iop[3] - iop[0] * iop[5];
			double nz = iop[0] * iop[4] - iop[1] * iop[3];

			// 長さを1に正規化（DICOMのIOPは通常すでに正規化されていますが念のため）
			double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 0.000001) {
				return new double[] { (nx / len) * thickness, (ny / len) * thickness, (nz / len) * thickness };
			}
		}

		// IOPすら存在しない最悪のケースの究極のフォールバック
		// (純粋なアキシャル面を仮定して、Z軸方向にのみ厚みを持たせる)
		return new double[] { 0.0, 0.0, thickness };
	}
}