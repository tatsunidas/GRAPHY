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
package com.vis.core.slicer;

import java.awt.image.ColorModel;

import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.LocalizerPoster;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.dicom.Tag;
import com.vis.dicom.image.GDicomTools;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 *  
 * @author tatsunidas
 *
 */
@SuppressWarnings("unused")
public class OrthogonalSlice {
	
	//debug
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		
		OrthogonalSlice slicer = new OrthogonalSlice();
		
//		String dir = "/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151";
//		ImagePlus xy = FolderOpener.open(dir);
		
//		xy.setPosition(xy.getNSlices()/2);
//		ImagePlus xz = slicer.cutHorizontally(xy, xy.getHeight()/2-1, 1);
//		xz.setTitle("COR");
//		ImagePlus yz = slicer.cutVirtically(xy, xy.getWidth()/2-1, 1);
//		yz.setTitle("SAG");
//		xz.show();
//		yz.show();
		
		String corDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/T1COR";
		ImagePlus xz = FolderOpener.open(corDir);
		ImagePlus xy = slicer.coronalToAxial(xz, GDicomTools.getTag(xz, Tag.PixelRepresentation).equals("1"));
		xy.show();
		
//		String sagDir = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
//		ImagePlus yz = FolderOpener.open(sagDir);
//		ImagePlus xy = slicer.sagittalToAxial(yz);
//		xy.show();
		
//		Vector3d row = new Vector3d(1.0, 0.0, 0.0);
//		Vector3d col = new Vector3d(0.0, -0.372, -0.927);
//		System.out.println(row.cross(col).normalize().toString());
	}
	
	/**
	 * @param standardizedOrientSrc: Axial(Z- to Z+ order), standardized slicing by PlanarSupport.standardizeStackOrientation
	 * @param y_cutPoint: 0-based Y coordinate to cut horizontally
	 * @return XZ (Coronal) ImagePlus
	 */
	public ImagePlus cutHorizontally(ImagePlus standardizedOrientSrc, int y_cutPoint) {
		if (PlanarSupport.planarOf(standardizedOrientSrc) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Need axial src volume");
		}

		ImageStack srcStack = getStack(standardizedOrientSrc);
		int width = srcStack.getWidth();
		int height = srcStack.getHeight();
		int nSlices = srcStack.getSize();

		int y = Math.max(0, Math.min(y_cutPoint, height - 1));

		ImageProcessor templateIp = srcStack.getProcessor(1);
		ImageProcessor xzProcessor = extractCoronalProcessor(srcStack, width, nSlices, y, templateIp);

		Calibration srcCal = standardizedOrientSrc.getCalibration().copy();
		double pixelWidth = srcCal.pixelWidth;
		double pixelHeight = srcCal.pixelHeight;
		double voxelDepth = GDicomTools.getVoxelDepth(standardizedOrientSrc);
		
		double aspectRatio = voxelDepth / pixelWidth;
		int resizedHeight = (int) Math.ceil(xzProcessor.getHeight() * aspectRatio);
		
		if (resizedHeight < 1) {
			throw new IllegalArgumentException("Cannot create XZ plane: resulting height is < 1.");
		}

		if (width != xzProcessor.getWidth() || resizedHeight != xzProcessor.getHeight()) {
			xzProcessor.setInterpolationMethod(ImageProcessor.NONE);
			xzProcessor = xzProcessor.resize(width, resizedHeight);
		}

		boolean isRGB = (templateIp instanceof ColorProcessor);
		double min = standardizedOrientSrc.getDisplayRangeMin();
		double max = standardizedOrientSrc.getDisplayRangeMax();
		
		if (!isRGB) {
			xzProcessor.setMinAndMax(min, max);
		}
		
		ColorModel cm = isRGB ? null : standardizedOrientSrc.getProcessor().getColorModel();
		if (cm != null && !(templateIp instanceof ColorProcessor)) {
			xzProcessor.setColorModel(cm);
		}

		ImagePlus xzImage = new ImagePlus(String.valueOf(y), xzProcessor);

		double[] axiIop = GDicomTools.getImageOrientationPatient(standardizedOrientSrc, 1);
		if (axiIop != null) {
			Vector3d rowX = new Vector3d(axiIop[0], axiIop[1], axiIop[2]);
			Vector3d colY = new Vector3d(axiIop[3], axiIop[4], axiIop[5]);
			// ★修正: CoronalのColベクトルは頭から足(-Z)へ向かうため、colY x rowX の外積にする
			Vector3d zVec = PlanarSupport.crossProduct(colY, rowX, true);
			double[] corIop = new double[] { rowX.x, rowX.y, rowX.z, zVec.x, zVec.y, zVec.z };

			// ★修正: 新しい画像の一番上(row 0)は頭(一番大きいZ)なので、IPPは nSlices 番目のスライスから取得する
			Vector3d ippVec = PlanarSupport.getNewImagePositionPatient2D(standardizedOrientSrc, 0, y, nSlices);
			if (ippVec != null) {
				double[] ipp = new double[] { ippVec.x(), ippVec.y(), ippVec.z() };
				GDicomTools.setImagePositionPatient(xzImage, 1, ipp);
				GDicomTools.setImageOrientationPatient(xzImage, 1, corIop);
			}
		}

		Calibration newCal = srcCal.copy();
		newCal.setXUnit(srcCal.getXUnit());
		newCal.setYUnit(srcCal.getZUnit());
		newCal.setZUnit(srcCal.getYUnit());
		newCal.pixelWidth = pixelWidth;
		newCal.pixelHeight = voxelDepth / aspectRatio; 
		newCal.pixelDepth = pixelHeight;
		xzImage.setCalibration(newCal);

		xzImage.setDisplayRange(min, max);
		if (standardizedOrientSrc.getLuts() != null && standardizedOrientSrc.getLuts().length > 0) {
			xzImage.setLut(standardizedOrientSrc.getLuts()[0]);
		}

		return xzImage;
	}

	private ImageProcessor extractCoronalProcessor(ImageStack srcStack, int width, int nSlices, int y, ImageProcessor templateIp) {
		if (templateIp instanceof ShortProcessor) {
			short[] newPix = new short[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				short[] srcPix = (short[]) srcStack.getPixels(i + 1);
				int targetRow = i;
				System.arraycopy(srcPix, width * y, newPix, width * targetRow, width);
			}
			return new ShortProcessor(width, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ByteProcessor) {
			byte[] newPix = new byte[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				byte[] srcPix = (byte[]) srcStack.getPixels(i + 1);
				int targetRow = i;
				System.arraycopy(srcPix, width * y, newPix, width * targetRow, width);
			}
			return new ByteProcessor(width, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof FloatProcessor) {
			float[] newPix = new float[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				float[] srcPix = (float[]) srcStack.getPixels(i + 1);
				int targetRow = i;
				System.arraycopy(srcPix, width * y, newPix, width * targetRow, width);
			}
			return new FloatProcessor(width, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ColorProcessor) {
			int[] newPix = new int[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				int[] srcPix = (int[]) srcStack.getPixels(i + 1);
				int targetRow = i;
				System.arraycopy(srcPix, width * y, newPix, width * targetRow, width);
			}
			return new ColorProcessor(width, nSlices, newPix);
		}
		throw new IllegalArgumentException("Unsupported ImageProcessor type");
	}

	
	/**
	 * Create a sagittal section for the input stack.
	 * * @param standardizedOrientSrc : Axial, standardized slicing by PlanarSupport.standardizeStackOrientation
	 * @param x_cutPoint: 0 to width-1
	 * @return YZ (Sagittal) ImagePlus
	 */
	public ImagePlus cutVertically(ImagePlus standardizedOrientSrc, int x_cutPoint) {
		if (PlanarSupport.planarOf(standardizedOrientSrc) != CutSurface.AXIAL) {
			throw new IllegalArgumentException("Need axial src volume");
		}

		ImageStack srcStack = getStack(standardizedOrientSrc);
		int width = srcStack.getWidth();
		int height = srcStack.getHeight();
		int nSlices = srcStack.getSize();

		int x = Math.max(0, Math.min(x_cutPoint, width - 1));

		ImageProcessor templateIp = srcStack.getProcessor(1);
		ImageProcessor yzProcessor = extractSagittalProcessor(srcStack, width, height, nSlices, x, templateIp);

		Calibration srcCal = standardizedOrientSrc.getCalibration().copy();
		double pixelWidth = srcCal.pixelWidth;
		double pixelHeight = srcCal.pixelHeight;
		double voxelDepth = GDicomTools.getVoxelDepth(standardizedOrientSrc);

		double aspectRatio = voxelDepth / pixelWidth;
		int resizedHeight = (int) Math.ceil(yzProcessor.getHeight() * aspectRatio);

		if (resizedHeight < 1) {
			throw new IllegalArgumentException("Cannot create YZ plane: resulting height is < 1.");
		}

		if (yzProcessor.getWidth() != yzProcessor.getWidth() || resizedHeight != yzProcessor.getHeight()) {
			yzProcessor.setInterpolationMethod(ImageProcessor.NONE);
			yzProcessor = yzProcessor.resize(yzProcessor.getWidth(), resizedHeight);
		}

		boolean isRGB = (templateIp instanceof ColorProcessor);
		double min = standardizedOrientSrc.getDisplayRangeMin();
		double max = standardizedOrientSrc.getDisplayRangeMax();

		if (!isRGB) {
			yzProcessor.setMinAndMax(min, max);
		}

		ColorModel cm = isRGB ? null : standardizedOrientSrc.getProcessor().getColorModel();
		if (cm != null && !(templateIp instanceof ColorProcessor)) {
			yzProcessor.setColorModel(cm);
		}

		ImagePlus yzImage = new ImagePlus(String.valueOf(x), yzProcessor);

		double[] axiIop = GDicomTools.getImageOrientationPatient(standardizedOrientSrc, 1);
		if (axiIop != null) {
			Vector3d rowX = new Vector3d(axiIop[0], axiIop[1], axiIop[2]);
			Vector3d colY = new Vector3d(axiIop[3], axiIop[4], axiIop[5]);
			// ★修正: SagittalのColベクトルも頭から足(-Z)へ向かうため、colY x rowX の外積にする
			Vector3d zVec = PlanarSupport.crossProduct(colY, rowX, true);
			double[] sagIop = new double[] { colY.x, colY.y, colY.z, zVec.x, zVec.y, zVec.z };

			// ★修正: 新しい画像の一番上(row 0)は頭なので、IPPは 1 番目のスライスから取得する
			Vector3d ippVec = PlanarSupport.getNewImagePositionPatient2D(standardizedOrientSrc, x, 0, 1);
			if (ippVec != null) {
				double[] ipp = new double[] { ippVec.x(), ippVec.y(), ippVec.z() };
				GDicomTools.setImagePositionPatient(yzImage, 1, ipp);
				GDicomTools.setImageOrientationPatient(yzImage, 1, sagIop);
			}
		}

		Calibration newCal = srcCal.copy();
		newCal.setXUnit(srcCal.getYUnit());
		newCal.setYUnit(srcCal.getZUnit());
		newCal.setZUnit(srcCal.getXUnit());
		newCal.pixelWidth = pixelHeight;
		newCal.pixelHeight = voxelDepth / aspectRatio;
		newCal.pixelDepth = pixelWidth;
		yzImage.setCalibration(newCal);

		yzImage.setDisplayRange(min, max);
		if (standardizedOrientSrc.getLuts() != null && standardizedOrientSrc.getLuts().length > 0) {
			yzImage.setLut(standardizedOrientSrc.getLuts()[0]);
		}

		return yzImage;
	}

	private ImageProcessor extractSagittalProcessor(ImageStack srcStack, int width, int height, int nSlices, int x, ImageProcessor templateIp) {
		if (templateIp instanceof ShortProcessor) {
			short[] newPix = new short[height * nSlices];
			for (int z = 0; z < nSlices; z++) {
				short[] srcPix = (short[]) srcStack.getPixels(z + 1);
				int targetRow = z;
				for (int y = 0; y < height; y++) {
					newPix[targetRow * height + y] = srcPix[x + y * width];
				}
			}
			return new ShortProcessor(height, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ByteProcessor) {
			byte[] newPix = new byte[height * nSlices];
			for (int z = 0; z < nSlices; z++) {
				byte[] srcPix = (byte[]) srcStack.getPixels(z + 1);
				int targetRow = z;
				for (int y = 0; y < height; y++) {
					newPix[targetRow * height + y] = srcPix[x + y * width];
				}
			}
			return new ByteProcessor(height, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof FloatProcessor) {
			float[] newPix = new float[height * nSlices];
			for (int z = 0; z < nSlices; z++) {
				float[] srcPix = (float[]) srcStack.getPixels(z + 1);
				int targetRow = z;
				for (int y = 0; y < height; y++) {
					newPix[targetRow * height + y] = srcPix[x + y * width];
				}
			}
			return new FloatProcessor(height, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ColorProcessor) {
			int[] newPix = new int[height * nSlices];
			for (int z = 0; z < nSlices; z++) {
				int[] srcPix = (int[]) srcStack.getPixels(z + 1);
				int targetRow = z;
				for (int y = 0; y < height; y++) {
					newPix[targetRow * height + y] = srcPix[x + y * width];
				}
			}
			return new ColorProcessor(height, nSlices, newPix);
		}
		throw new IllegalArgumentException("Unsupported ImageProcessor type");
	}
	
	
	/**
	 * CoronalスタックからAxialスタック全体を再構成します。
	 * * @param standardizedSrcCor : Coronal, standardized slicing (Y- to Y+, Anterior to Posterior)
	 * @return XY (Axial) ImagePlus stack
	 */
	public ImagePlus coronalToAxial(ImagePlus standardizedSrcCor, boolean isSigned) {
		if (PlanarSupport.planarOf(standardizedSrcCor) != CutSurface.CORONAL) {
			throw new IllegalArgumentException("Need COR stack volume");
		}

		ImageStack srcStack = getStack(standardizedSrcCor);
		int width = srcStack.getWidth();
		int height = srcStack.getHeight(); // Coronalの高さ = Axialのスライス枚数(Z)
		int nSlices = srcStack.getSize();  // Coronalの枚数 = Axialの高さ(Y)

		ImageProcessor templateIp = srcStack.getProcessor(1);
		boolean isRGB = templateIp instanceof ColorProcessor;
		double min = standardizedSrcCor.getDisplayRangeMin();
		double max = standardizedSrcCor.getDisplayRangeMax();
		Calibration srcCal = standardizedSrcCor.getCalibration().copy();

		// 1. アスペクト比に基づくリサイズ計算
		double voxelDepth = GDicomTools.getVoxelDepth(standardizedSrcCor);
		double pixelWidth = srcCal.pixelWidth;
		double pixelHeight = srcCal.pixelHeight;
		
		double aspectRatio = voxelDepth / pixelWidth; // ZとXの比率
		int resizedHeight = (int) Math.ceil(nSlices * aspectRatio);
		
		if (resizedHeight < 1) {
			throw new IllegalArgumentException("Cannot create XY plane: resulting height is < 1.");
		}

		// 2. Axialスタック共通の IOP (Image Orientation Patient) を計算
		// Coronalの Row(X) と Col(Z) の外積から、Axialの Col(Y) を算出する
		double[] corIop = GDicomTools.getImageOrientationPatient(standardizedSrcCor, 1);
		double[] axiIop = null;
		if (corIop != null) {
			Vector3d rowX = new Vector3d(corIop[0], corIop[1], corIop[2]);
			Vector3d colZ = new Vector3d(corIop[3], corIop[4], corIop[5]);
			// Head Firstを前提に標準化されているため、そのまま外積でPosterior(Y)方向が出る
			Vector3d colY = PlanarSupport.crossProduct(rowX, colZ, true);
			colY = PlanarSupport.truncate(colY, 6);
			axiIop = new double[] { rowX.x, rowX.y, rowX.z, colY.x, colY.y, colY.z };
		}

		// 3. 新しいAxialスタックの初期化
		ImageStack axiStack = new ImageStack(width, resizedHeight);

		// 4. Coronalの高さ(Z軸)に沿ってループし、各Axialスライス(XY面)を生成
		for (int y = 0; y < height; y++) {
			// ヘルパーメソッドでピクセルを抽出し、ImageProcessorを生成
			ImageProcessor axiIp = extractAxialProcessorFromCoronal(srcStack, width, nSlices, y, templateIp);

			// リサイズ処理 (アスペクト比の調整)
			if (width != axiIp.getWidth() || resizedHeight != axiIp.getHeight()) {
				axiIp.setInterpolationMethod(ImageProcessor.NONE);
				axiIp = axiIp.resize(width, resizedHeight);
			}

			// メタデータ(IPP/IOP)を付与するための一時的なImagePlus
			ImagePlus tempSlice = new ImagePlus(String.valueOf(y), axiIp);
			Vector3d ippVec = PlanarSupport.getNewImagePositionPatient2D(standardizedSrcCor, 0, y, 1);

			if (ippVec != null && axiIop != null) {
				double[] ipp = new double[] { ippVec.x(), ippVec.y(), ippVec.z() };
				GDicomTools.setImagePositionPatient(tempSlice, 1, ipp);
				GDicomTools.setImageOrientationPatient(tempSlice, 1, axiIop);
			}
			
			GDicomTools.setTag(tempSlice, 1, "0028,0103", isSigned ? "1":"0");

			// スライスラベル（メタデータ文字列）ごとスタックに追加
			axiStack.addSlice(tempSlice.getStack().getSliceLabel(1), axiIp);
		}

		// 5. 最終的なImagePlusの構築
		ImagePlus axiImage = new ImagePlus("XY", axiStack);

		// 6. ColorModel と コントラスト(Min/Max) の適用
		if (!isRGB) {
			axiImage.setDisplayRange(min, max);
			axiImage.getProcessor().setMinAndMax(min, max);
		}
		
		ColorModel cm = isRGB ? null : standardizedSrcCor.getProcessor().getColorModel();
		if (cm != null && !(templateIp instanceof ColorProcessor)) {
			// スタック全体にColorModelを適用する場合は通常Lutをセットする
			if (standardizedSrcCor.getLuts() != null && standardizedSrcCor.getLuts().length > 0) {
				axiImage.setLut(standardizedSrcCor.getLuts()[0]);
			}
		}

		// 7. 新しい Calibration (単位と解像度のマッピング) の設定
		Calibration newCal = srcCal.copy();
		newCal.setXUnit(srcCal.getXUnit());
		newCal.setYUnit(srcCal.getZUnit());
		newCal.setZUnit(srcCal.getYUnit());
		newCal.pixelWidth = pixelWidth;
		newCal.pixelHeight = voxelDepth / aspectRatio; // = pixelWidth になるはずです
		newCal.pixelDepth = pixelHeight;               // Coronalの縦ピクセルサイズが、Axialのスライス間隔になる
		axiImage.setCalibration(newCal);

		return axiImage;
	}

	/**
	 * Coronalスタックの指定したY座標（高さ）から1行ずつピクセルを抽出し、Axial面用のImageProcessorを作成します。
	 */
	private ImageProcessor extractAxialProcessorFromCoronal(ImageStack srcStack, int width, int nSlices, int y, ImageProcessor templateIp) {
		// Axial面(XY)の幅は元の幅(X)、高さはCoronalのスライス枚数(Y: 前後方向)になります
		if (templateIp instanceof ShortProcessor) {
			short[] newPix = new short[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				short[] srcPix = (short[]) srcStack.getPixels(i + 1);
				System.arraycopy(srcPix, width * y, newPix, width * i, width);
			}
			return new ShortProcessor(width, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ByteProcessor) {
			byte[] newPix = new byte[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				byte[] srcPix = (byte[]) srcStack.getPixels(i + 1);
				System.arraycopy(srcPix, width * y, newPix, width * i, width);
			}
			return new ByteProcessor(width, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof FloatProcessor) {
			float[] newPix = new float[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				float[] srcPix = (float[]) srcStack.getPixels(i + 1);
				System.arraycopy(srcPix, width * y, newPix, width * i, width);
			}
			return new FloatProcessor(width, nSlices, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ColorProcessor) {
			int[] newPix = new int[width * nSlices];
			for (int i = 0; i < nSlices; i++) {
				int[] srcPix = (int[]) srcStack.getPixels(i + 1);
				System.arraycopy(srcPix, width * y, newPix, width * i, width);
			}
			return new ColorProcessor(width, nSlices, newPix);
		}
		
		throw new IllegalArgumentException("Unsupported ImageProcessor type");
	}
	
	/**
	 * SagittalスタックからAxialスタック全体を再構成します。
	 * @param standardizedSrcSag : Sagittal, standardized slicing (X- to X+, Right to Left)
	 * @return XY (Axial) ImagePlus stack
	 */
	public ImagePlus sagittalToAxial(ImagePlus standardizedSrcSag, boolean isSigned) {
		if (PlanarSupport.planarOf(standardizedSrcSag) != CutSurface.SAGITTAL) {
			throw new IllegalArgumentException("Need SAG stack volume");
		}

		ImageStack srcStack = getStack(standardizedSrcSag);
		int sagWidth = srcStack.getWidth();   // Sagittalの幅(Y: 前後方向) = Axialの高さ
		int sagHeight = srcStack.getHeight(); // Sagittalの高さ(Z: 頭足方向) = Axialのスライス枚数
		int nSlices = srcStack.getSize();     // Sagittalの枚数(X: 左右方向) = Axialの幅

		ImageProcessor templateIp = srcStack.getProcessor(1);
		boolean isRGB = templateIp instanceof ColorProcessor;
		double min = standardizedSrcSag.getDisplayRangeMin();
		double max = standardizedSrcSag.getDisplayRangeMax();
		Calibration srcCal = standardizedSrcSag.getCalibration().copy();

		// 1. アスペクト比に基づくリサイズ計算
		double voxelDepth = GDicomTools.getVoxelDepth(standardizedSrcSag);
		double pixelWidth = srcCal.pixelWidth;
		double pixelHeight = srcCal.pixelHeight;
		
		double aspectRatio = voxelDepth / pixelWidth; // X(枚数) と Y(幅) の比率
		int resizedWidth = (int) Math.ceil(nSlices * aspectRatio);
		
		if (resizedWidth < 1) {
			throw new IllegalArgumentException("Cannot create XY plane: resulting width is < 1.");
		}

		// 2. Axialスタック共通の IOP (Image Orientation Patient) を計算
		// Sagittalの Col(Z) と Row(Y) の外積から、Axialの Row(X) を算出する
		double[] sagIop = GDicomTools.getImageOrientationPatient(standardizedSrcSag, 1);
		double[] axiIop = null;
		if (sagIop != null) {
			Vector3d rowY = new Vector3d(sagIop[0], sagIop[1], sagIop[2]); // A -> P
			Vector3d colZ = new Vector3d(sagIop[3], sagIop[4], sagIop[5]); // S -> I
			// 標準LPS座標において、S->I(col) と A->P(row) の外積は R->L(+X) になる
			Vector3d rowX = PlanarSupport.crossProduct(colZ, rowY, true); 
			rowX = PlanarSupport.truncate(rowX, 6);
			axiIop = new double[] { rowX.x, rowX.y, rowX.z, rowY.x, rowY.y, rowY.z };
		}

		// 3. 新しいAxialスタックの初期化 (幅: resizedWidth, 高さ: sagWidth)
		ImageStack axiStack = new ImageStack(resizedWidth, sagWidth);

		// 4. Sagittalの高さ(Z軸)に沿ってループし、各Axialスライス(XY面)を生成
		for (int y = 0; y < sagHeight; y++) {
			// ヘルパーメソッドでピクセルを抽出し、ImageProcessorを生成
			ImageProcessor axiIp = extractAxialProcessorFromSagittal(srcStack, sagWidth, sagHeight, nSlices, y, templateIp);

			// リサイズ処理 (アスペクト比の調整)
			if (nSlices != axiIp.getWidth() || sagWidth != axiIp.getHeight()) {
				axiIp.setInterpolationMethod(ImageProcessor.NONE);
				axiIp = axiIp.resize(resizedWidth, sagWidth);
			}

			// メタデータ(IPP/IOP)を付与するための一時的なImagePlus
			ImagePlus tempSlice = new ImagePlus(String.valueOf(y), axiIp);
			Vector3d ippVec = PlanarSupport.getNewImagePositionPatient2D(standardizedSrcSag, 0, y, 1);

			if (ippVec != null && axiIop != null) {
				double[] ipp = new double[] { ippVec.x(), ippVec.y(), ippVec.z() };
				GDicomTools.setImagePositionPatient(tempSlice, 1, ipp);
				GDicomTools.setImageOrientationPatient(tempSlice, 1, axiIop);
			}
			
			GDicomTools.setTag(tempSlice, 1, "0028,0103", isSigned ? "1":"0");

			// スライスラベル（メタデータ文字列）ごとスタックに追加
			axiStack.addSlice(tempSlice.getStack().getSliceLabel(1), axiIp);
		}

		// 5. 最終的なImagePlusの構築
		ImagePlus axiImage = new ImagePlus("XY", axiStack);

		// 6. ColorModel と コントラスト(Min/Max) の適用
		if (!isRGB) {
			axiImage.setDisplayRange(min, max);
			axiImage.getProcessor().setMinAndMax(min, max);
		}
		
		ColorModel cm = isRGB ? null : standardizedSrcSag.getProcessor().getColorModel();
		if (cm != null && !(templateIp instanceof ColorProcessor)) {
			if (standardizedSrcSag.getLuts() != null && standardizedSrcSag.getLuts().length > 0) {
				axiImage.setLut(standardizedSrcSag.getLuts()[0]);
			}
		}

		// 7. 新しい Calibration (単位と解像度のマッピング) の設定
		Calibration newCal = srcCal.copy();
		newCal.setXUnit(srcCal.getZUnit());
		newCal.setYUnit(srcCal.getXUnit());
		newCal.setZUnit(srcCal.getYUnit());
		newCal.pixelWidth = voxelDepth / aspectRatio; // = srcCal.pixelWidth になるはずです
		newCal.pixelHeight = pixelWidth;              // Sagittalの幅方向のピクセルサイズ
		newCal.pixelDepth = pixelHeight;              // Sagittalの縦方向(Z)がAxialの深さになる
		axiImage.setCalibration(newCal);

		return axiImage;
	}

	/**
	 * Sagittalスタックの指定したY座標（高さ）から1行ずつピクセルを抽出し、Axial面用のImageProcessorを作成します。
	 */
	private ImageProcessor extractAxialProcessorFromSagittal(ImageStack srcStack, int sagWidth, int sagHeight, int nSlices, int y, ImageProcessor templateIp) {
		// Axial面(XY)の幅は元のスライス枚数(X)、高さは元のSagittal幅(Y)になります
		int axiWidth = nSlices;
		int axiHeight = sagWidth;
		
		if (templateIp instanceof ShortProcessor) {
			short[] newPix = new short[axiWidth * axiHeight];
			for (int z = 0; z < nSlices; z++) { // z は Sagittal のスライス番号 (Axial の X になる)
				short[] srcPix = (short[]) srcStack.getPixels(z + 1);
				for (int x = 0; x < sagWidth; x++) { // x は Sagittal の横座標 (Axial の Y になる)
					newPix[x * axiWidth + z] = srcPix[y * sagWidth + x];
				}
			}
			return new ShortProcessor(axiWidth, axiHeight, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ByteProcessor) {
			byte[] newPix = new byte[axiWidth * axiHeight];
			for (int z = 0; z < nSlices; z++) {
				byte[] srcPix = (byte[]) srcStack.getPixels(z + 1);
				for (int x = 0; x < sagWidth; x++) {
					newPix[x * axiWidth + z] = srcPix[y * sagWidth + x];
				}
			}
			return new ByteProcessor(axiWidth, axiHeight, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof FloatProcessor) {
			float[] newPix = new float[axiWidth * axiHeight];
			for (int z = 0; z < nSlices; z++) {
				float[] srcPix = (float[]) srcStack.getPixels(z + 1);
				for (int x = 0; x < sagWidth; x++) {
					newPix[x * axiWidth + z] = srcPix[y * sagWidth + x];
				}
			}
			return new FloatProcessor(axiWidth, axiHeight, newPix, templateIp.getCurrentColorModel());
			
		} else if (templateIp instanceof ColorProcessor) {
			int[] newPix = new int[axiWidth * axiHeight];
			for (int z = 0; z < nSlices; z++) {
				int[] srcPix = (int[]) srcStack.getPixels(z + 1);
				for (int x = 0; x < sagWidth; x++) {
					newPix[x * axiWidth + z] = srcPix[y * sagWidth + x];
				}
			}
			return new ColorProcessor(axiWidth, axiHeight, newPix);
		}
		
		throw new IllegalArgumentException("Unsupported ImageProcessor type");
	}
	
	private ImageStack getStack(ImagePlus imp) {
		if (imp.isHyperStack()) {
			int slices = imp.getNSlices();
			int c = imp.getChannel();
			int z = imp.getSlice();
			int t = imp.getFrame();
			int mode = imp.getCompositeMode();
			boolean rgb = mode == IJ.COMPOSITE;
			ImageStack stack = imp.getStack();
			ImageStack stack2 = new ImageStack(imp.getWidth(), imp.getHeight());
			if (slices == 1) {
				String hdr = imp.getInfoProperty();
				if (rgb) {
					imp.setPositionWithoutUpdate(c, 1, t);
					stack2.addSlice(hdr, new ColorProcessor(imp.getImage()));
				} else {
					int index = imp.getStackIndex(c, 1, t);
					stack2.addSlice(hdr, stack.getProcessor(index));
				}
			} else {
				for (int i = 1; i <= slices; i++) {
					if (rgb) {
						imp.setPositionWithoutUpdate(c, i, t);
						stack2.addSlice(stack.getSliceLabel(i), new ColorProcessor(imp.getImage()));
					} else {
						int index = imp.getStackIndex(c, i, t);
						stack2.addSlice(stack.getSliceLabel(i), stack.getProcessor(index));
					}
				}
			}
			// reset position
			imp.setPosition(c, z, t);
			return stack2;
		} else {
			return imp.getStack();
		}
	}
}
