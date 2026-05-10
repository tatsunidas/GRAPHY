
import org.joml.Vector3d;

import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.plugin.FolderOpener;

public class DicomCoordinateTest {

	public static void main(String[] args) {
		
		// TODO: お手持ちのデータのフォルダパスに変更してください
		String headFirstPath = "/home/tatsunidas/graphy_sample_images/cDWI-Sample/B1000";
		String feetFirstPath = "/home/tatsunidas/graphy_sample_images/FFT_CT_ABD";
		
		ImagePlus headFirstImp = FolderOpener.open(headFirstPath);
		ImagePlus feetFirstImp = FolderOpener.open(feetFirstPath);
		
		System.out.println("========== DICOM 座標系 (LPS) 検証テスト ==========\n");
		
		if (headFirstImp != null) {
			checkCoordinates(headFirstImp, "Head First MRI");
		} else {
			System.out.println("Head First のデータが読み込めませんでした。");
		}
		
		if (feetFirstImp != null) {
			checkCoordinates(feetFirstImp, "Feet First CT");
		} else {
			System.out.println("Feet First のデータが読み込めませんでした。");
		}
	}

	public static void checkCoordinates(ImagePlus imp, String label) {
		System.out.println("--- [" + label + "] ---");
		
		// 1. Patient Position (0018,5100) の確認
		String ptPos = GDicomTools.getTag(imp, "0018,5100");
		System.out.println("Patient Position : " + (ptPos != null ? ptPos : "Unknown (HFSと仮定)"));
		
		// 2. Image Orientation Patient (0020,0037) の確認
		double[] iop = GDicomTools.getImageOrientationPatient(imp, 1);
		if (iop != null) {
			System.out.printf("Image Orientation  : Row(X)=[%.3f, %.3f, %.3f], Col(Y)=[%.3f, %.3f, %.3f]%n", 
					iop[0], iop[1], iop[2], iop[3], iop[4], iop[5]);
		}
		
		// 3. Image Position Patient (0020,0032) の確認 (最初と最後のスライス)
		int nSlices = imp.getNSlices();
		double[] firstIpp = GDicomTools.getImagePositionPatient(imp, 1);
		double[] lastIpp = GDicomTools.getImagePositionPatient(imp, nSlices);
		
		if (firstIpp != null && lastIpp != null) {
			System.out.printf("IPP (Slice 1)      : Z = %.3f%n", firstIpp[2]);
			System.out.printf("IPP (Slice %d)    : Z = %.3f%n", nSlices, lastIpp[2]);
			
			// スライス進行方向の計算
			double zDiff = lastIpp[2] - firstIpp[2];
			String direction = zDiff > 0 ? "足側 から 頭側 (S方向) へ進行" : "頭側 から 足側 (I方向) へ進行";
			System.out.println("スキャン進行方向   : " + direction);
		}

		// 4. スライス1の画像の四隅の絶対的な LPS 空間座標を計算
		System.out.println("画像四隅の LPS 空間座標 (Slice 1):");
		int w = imp.getWidth();
		int h = imp.getHeight();
		
		// 左上 (Top-Left)
		Vector3d tl = PlanarSupport.getNewImagePositionPatient2D(imp, 0, 0, 1);
		// 右上 (Top-Right)
		Vector3d tr = PlanarSupport.getNewImagePositionPatient2D(imp, w - 1, 0, 1);
		// 左下 (Bottom-Left)
		Vector3d bl = PlanarSupport.getNewImagePositionPatient2D(imp, 0, h - 1, 1);
		
		if (tl != null && tr != null && bl != null) {
			System.out.printf("  左上 (Top-Left)   : X=%.1f, Y=%.1f, Z=%.1f%n", tl.x, tl.y, tl.z);
			System.out.printf("  右上 (Top-Right)  : X=%.1f, Y=%.1f, Z=%.1f (Xが %.1f 変化)%n", tr.x, tr.y, tr.z, (tr.x - tl.x));
			System.out.printf("  左下 (Bottom-Left): X=%.1f, Y=%.1f, Z=%.1f (Yが %.1f 変化)%n", bl.x, bl.y, bl.z, (bl.y - tl.y));
			
			// X軸の増加方向の解剖学的意味
			String xDir = (tr.x > tl.x) ? "左(Left)" : "右(Right)";
			// Y軸の増加方向の解剖学的意味
			String yDir = (bl.y > tl.y) ? "背側(Posterior)" : "腹側(Anterior)";
			
			System.out.println("  => 解析結果:");
			System.out.println("     画面の右に行くほど、患者の " + xDir + " に向かっている");
			System.out.println("     画面の下に行くほど、患者の " + yDir + " に向かっている");
		}
		
		System.out.println("------------------------------------------\n");
	}
}