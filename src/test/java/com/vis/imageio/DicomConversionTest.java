package com.vis.imageio;

import java.awt.Color;
import java.util.HashMap;

import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class DicomConversionTest {

	public static void main(String[] args) {
		System.out.println("========== 相互変換テストを開始します ==========");
		
		System.out.println("\n[1] Unsigned (符号なし) データのテスト");
		runTest(false);

		System.out.println("\n[2] Signed (符号付き) データのテスト");
		runTest(true);
		
		System.out.println("\n========== テスト完了 ==========");
	}

	private static void runTest(boolean isSigned) {
		// 1. 疑似データの作成
		ImagePlus originalImp = createMockData(isSigned);
		
		// [検証 1] ImagePlusのピクセル値とMin/Maxが期待通りか
		System.out.println("  -> [検証1] 生成されたImagePlusの状態確認");
		double min = originalImp.getDisplayRangeMin();
		double max = originalImp.getDisplayRangeMax();
		System.out.println("     DisplayRange: " + min + " to " + max);
		System.out.println("     isSigned16Bit: " + originalImp.getCalibration().isSigned16Bit());

		// 2. Praparat に通す (表示などによりデータが破壊されないかのシミュレート)
		Praparat prap = new Praparat(originalImp, Color.DARK_GRAY, Praparat.ViewMode.Normal, false);
		ImagePlus prapImp = prap.getImagePlus(); // または prap.getOriginalImage() 等

		// 3. ImagePlus -> DicomImage への変換（先ほど議論したXORによる安全な変換を使用）
		System.out.println("  -> [検証2] ImagePlus から DICOM への変換とピクセル値の整合性チェック");
		HashMap<Integer, DicomImage> dicomMap = imagePlusToDcmMock(prapImp, isSigned);
		
		// 4. 値の検証 (ピクセルデータの比較)
		DicomImage dcm = dicomMap.get(0);
		ImageProcessor img = dcm.getImageProcessor(0);
		short[] dcmPixels = (short[]) img.getPixels();
		
		// ImagePlus側の生データ（シフトされた状態）
		short[] impPixels = (short[]) prapImp.getProcessor().getPixels();

		boolean isMatched = true;
		for (int i = 0; i < dcmPixels.length; i++) {
			short expectedDicomValue;
			if (isSigned) {
				// Signedの場合、ImageJの内部データ(impPixels)は本来のDICOM値に+32768されている。
				// つまり、期待されるDICOM値は impPixels[i] から 32768 引いた値 (XOR 0x8000)
				expectedDicomValue = (short) (impPixels[i] ^ 0x8000);
			} else {
				// Unsignedの場合はそのまま一致するはず
				expectedDicomValue = impPixels[i];
			}

			if (dcmPixels[i] != expectedDicomValue) {
				System.err.println("     [エラー] ピクセル値の不一致発生! Index:" + i 
						+ " / Dicom:" + dcmPixels[i] + " / Expected:" + expectedDicomValue);
				isMatched = false;
				break;
			}
		}
		
		if (isMatched) {
			System.out.println("     [OK] ピクセル値は完全に一致しました！(データ破壊なし)");
		}
		
		// Praparat 通過後の ImagePlus のコントラストが飛んでいないかの最終確認
		if (prapImp.getDisplayRangeMin() != min || prapImp.getDisplayRangeMax() != max) {
			System.err.println("     [エラー] Praparat通過/変換後にコントラスト(DisplayRange)が変化しています！");
			System.err.println("     変更後 DisplayRange: " + prapImp.getDisplayRangeMin() + " to " + prapImp.getDisplayRangeMax());
		} else {
			System.out.println("     [OK] コントラスト(DisplayRange)も保持されています。");
		}
	}

	/**
	 * 疑似CTデータを作成します。
	 */
	private static ImagePlus createMockData(boolean isSigned) {
		int w = 256;
		int h = 256;
		short[] pixels = new short[w * h];

		// グラデーションを作成
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (isSigned) {
					// 疑似CT値: -1000(空気) 〜 +2000(骨)
					short hu = (short) (-1000 + (x * 3000 / w));
					// ★ ImageJの作法: Signedの場合は配列に格納する際に +32768 シフトさせる (XORでも可)
					pixels[y * w + x] = (short) (hu + 32768);
				} else {
					// Unsigned: 0 〜 3000
					pixels[y * w + x] = (short) (x * 3000 / w);
				}
			}
		}

		ShortProcessor sp = new ShortProcessor(w, h, pixels, null);
		ImagePlus imp = new ImagePlus(isSigned ? "Mock Signed" : "Mock Unsigned", sp);

		if (isSigned) {
			Calibration cal = new Calibration();
			cal.setSigned16BitCalibration(); // これでImageJに「中身は-32768して読んでね」と伝わる
			imp.setCalibration(cal);
			// Signed用の適切なコントラスト（例: CTの腹部条件 WW400, WL40くらい）
			imp.setDisplayRange(-160, 240); 
		} else {
			imp.setDisplayRange(0, 3000);
		}

		return imp;
	}

	/**
	 * Graphyの imagePlusToDcm を模した、安全な（XORを用いた）変換メソッド
	 */
	public static HashMap<Integer, DicomImage> imagePlusToDcmMock(ImagePlus imp, boolean dealWithSecondaryCapture) {
		HashMap<Integer, DicomImage> images = new HashMap<>();
		int w = imp.getWidth();
		int h = imp.getHeight();
		int samples = 1;
		int bits = 16;
		int s = imp.getNSlices();
		boolean signed16 = imp.getCalibration().isSigned16Bit(); // ProcessorではなくCalibrationからとるのが確実です

		for (int i = 0; i < s; i++) {
			DicomObject core = DicomObject.newDicomObject();
			// TODO: 本番ではここで addAttributes(core, i, imp, dealWithSecondaryCapture);
			core.setInt(Tag.Columns, VR.IS, w);
			core.setInt(Tag.Rows, VR.IS, h);
			core.setInt(Tag.SamplesPerPixel, VR.IS, samples);
			core.setInt(Tag.BitsAllocated, VR.IS, bits);
			core.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
			core.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");//CT Image Storage
			DicomImage dcmImg = DicomImage.newDicomImage(null, core, null, UID.ImplicitVRLittleEndian);
			
			imp.setSlice(i + 1);
			Object pix = imp.getProcessor().getPixels();
			
			if (signed16) {
				short[] originalPixels = (short[]) pix;
				short[] copiedPixels = new short[originalPixels.length];
				for (int k = 0; k < originalPixels.length; k++) {
					// ★ XOR を用いた安全な逆変換。元配列(originalPixels)は直接書き換えない！
//					copiedPixels[k] = (short) (originalPixels[k] ^ 0x8000);
					copiedPixels[k] = (short) (originalPixels[k] + 32768);
				}
				pix = copiedPixels;
			}
			
			dcmImg.setPixelData(0, w, h, samples, bits, pix);
			images.put(i, dcmImg);
		}
		return images;
	}
}