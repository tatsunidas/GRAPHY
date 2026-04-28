/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.imageio;

import ij.process.ImageProcessor;
import com.vis.core.log.Log;

/**
 * DICOMのImageOrientationPatient(IOP)を解釈し、 Radiological
 * Convention（読影ルール）に合わせてImageProcessorを反転・回転させるユーティリティ。
 * 
 * NIfTIのインポートの際に使うことを検討したが、
 * そもそも、表示時に変換すること自体ナンセンスだろう。
 * 仮に、間違って、左手系の空間情報を持つDICOMが入ってきたら、事前に変換するべき。
 * 都度、このような小手先で変換するのはよくない。
 * 
 */
@Deprecated
public class OrientationCorrector {

	/**
	 * ImageProcessorに対して必要なフリップ処理を適用します。
	 */
	public static void correctProcessor(ImageProcessor ip, double[] iop) {
		if (ip == null || iop == null || iop.length < 6)
			return;

		boolean[] needsFlips = needsFlips(iop);
		boolean flipH = needsFlips[0];
		boolean flipV = needsFlips[1];
		// 3. 実際のImageProcessorにフリップ処理を適用
		if (flipH) {
			ip.flipHorizontal();
			Log.logger.fine("Applied Horizontal Flip based on IOP"); // デバッグ用
		}
		if (flipV) {
			ip.flipVertical();
			Log.logger.fine("Applied Vertical Flip based on IOP"); // デバッグ用
		}
	}
	
	/**
	 * 
	 * @param iop
	 * @return boolean[]{needsHorizontalFlip, needsVerticalFlip}
	 */
	public static boolean[] needsFlips(double[] iop) {
		double rx = iop[0], ry = iop[1], rz = iop[2];
		double cx = iop[3], cy = iop[4], cz = iop[5];

		// 1. 行(Row)と列(Col)の主軸（一番成分が大きい軸: 0=X, 1=Y, 2=Z）と、その符号を取得
		int axisR = getMajorAxis(rx, ry, rz);
		int axisC = getMajorAxis(cx, cy, cz);

		int signR = getSign(rx, ry, rz, axisR);
		int signC = getSign(cx, cy, cz, axisC);

		boolean flipH = false;
		boolean flipV = false;

		// 2. 断面ごとの「理想のベクトル」と比較して、反転が必要か判定する

		// --- アキシャル断面 (XY平面) ---
		if ((axisR == 0 && axisC == 1) || (axisR == 1 && axisC == 0)) {
			if (axisR == 0) {
				// 理想: Rowは左方向(+X), Colは背側方向(+Y)
				if (signR < 0)
					flipH = true; // 右方向(-X)に進んでいるなら左右反転
				if (signC < 0)
					flipV = true; // 腹方向(-Y)に進んでいるなら上下反転
			}
		}
		// --- コロナル断面 (XZ平面) ---
		else if ((axisR == 0 && axisC == 2) || (axisR == 2 && axisC == 0)) {
			if (axisR == 0) {
				// 理想: Rowは左方向(+X), Colは足側方向(-Z)
				if (signR < 0)
					flipH = true;
				if (signC > 0)
					flipV = true; // 頭方向(+Z)に進んでいるなら上下反転
			}
		}
		// --- サジタル断面 (YZ平面) ---
		else if ((axisR == 1 && axisC == 2) || (axisR == 2 && axisC == 1)) {
			if (axisR == 1) {
				// 理想: Rowは背側方向(+Y), Colは足側方向(-Z)
				if (signR < 0)
					flipH = true; // 腹方向(-Y)に進んでいるなら左右反転
				if (signC > 0)
					flipV = true;
			}
		}
		return new boolean[] {flipH, flipV};
	}
	
	private static int getMajorAxis(double x, double y, double z) {
		double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
		if (ax > ay && ax > az)
			return 0;
		if (ay > ax && ay > az)
			return 1;
		return 2;
	}

	private static int getSign(double x, double y, double z, int axis) {
		if (axis == 0)
			return x >= 0 ? 1 : -1;
		if (axis == 1)
			return y >= 0 ? 1 : -1;
		return z >= 0 ? 1 : -1;
	}
}
