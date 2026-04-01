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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.RGBStackMerge;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class DicomMaskedRegistration {

	private static final int BINS = 64; // ヒストグラムのビン数

	public static void main(String[] args) {
		// 1. 画像の読み込み
		ImagePlus fixed = IJ.openImage("path/to/fixed.dcm");
		ImagePlus moving = IJ.openImage("path/to/moving.dcm");

		// マスク画像の読み込み (マスクがない場合は null に設定)
		// マスクはFixed画像と同じサイズ・座標系である前提です
		// 合わせる必要がある場合は、DicomFusionAdvance.resampleImage3Dを使う。
		ImagePlus mask = IJ.openImage("path/to/fixed_mask.dcm");
		// ImagePlus mask = null; // マスクを使わない場合

		if (fixed == null || moving == null)
			return;

		IJ.log("マスク対応の相互情報量(MI)位置合わせを開始...");

		// 2. 最適化実行
		double[] bestParams = optimizeRegistration(fixed, moving, mask);

		IJ.log(String.format("完了: T(%.2f, %.2f, %.2f), R(%.3f, %.3f, %.3f)", bestParams[0], bestParams[1],
				bestParams[2], bestParams[3], bestParams[4], bestParams[5]));

		// 3. 最終結果の生成 (高精度補間)
		ImagePlus registeredImg = applyTransform(fixed, moving, bestParams, true);

		// 4. 結果表示
		registeredImg.show();
		RGBStackMerge.mergeChannels(new ImagePlus[] { fixed, registeredImg }, false).show();
	}

	/**
	 * 最適化エンジン マスク引数を受け取り、サンプリング生成に利用します。
	 */
	private static double[] optimizeRegistration(ImagePlus fixed, ImagePlus moving, ImagePlus mask) {
		double[] currentParams = { 0, 0, 0, 0, 0, 0 };
		double stepTrans = 2.0;
		double stepRot = Math.toRadians(2.0);

		// ★重要: マスクの有無に応じてサンプリング点を生成
		// マスク内のみを評価対象とするため、計算効率と精度が向上します
		int[][] samplePoints = generateSamplePoints(fixed, mask, 5000);

		// サンプル数が少なすぎる場合（マスクが小さすぎる等）の安全策
		if (samplePoints.length < 100) {
			IJ.log("警告: マスク領域が小さすぎます。全領域サンプリングに切り替えます。");
			samplePoints = generateSamplePoints(fixed, null, 5000);
		}

		double[] center = { fixed.getWidth() / 2.0, fixed.getHeight() / 2.0, fixed.getStackSize() / 2.0 };
		double[] rangeFixed = getMinMax(fixed);
		double[] rangeMoving = getMinMax(moving);

		double bestScore = Double.MAX_VALUE;

		for (int iter = 0; iter < 100; iter++) {
			boolean improved = false;
			for (int i = 0; i < 6; i++) {
				double originalVal = currentParams[i];
				double step = (i < 3) ? stepTrans : stepRot;

				// +方向
				currentParams[i] = originalVal + step;
				double scorePos = -calculateMutualInformation(fixed, moving, currentParams, center, samplePoints,
						rangeFixed, rangeMoving);

				// -方向
				currentParams[i] = originalVal - step;
				double scoreNeg = -calculateMutualInformation(fixed, moving, currentParams, center, samplePoints,
						rangeFixed, rangeMoving);

				if (scorePos < bestScore) {
					bestScore = scorePos;
					currentParams[i] = originalVal + step;
					improved = true;
				} else if (scoreNeg < bestScore) {
					bestScore = scoreNeg;
					currentParams[i] = originalVal - step;
					improved = true;
				} else {
					currentParams[i] = originalVal;
				}
			}

			if (!improved) {
				stepTrans *= 0.5;
				stepRot *= 0.5;
				if (stepTrans < 0.1)
					break;
			}
		}
		return currentParams;
	}

	/**
	 * サンプリング点生成メソッド (マスク対応版)
	 */
	private static int[][] generateSamplePoints(ImagePlus fixed, ImagePlus mask, int numPoints) {
		int w = fixed.getWidth();
		int h = fixed.getHeight();
		int d = fixed.getStackSize();

		// A. マスクがある場合: マスク内の有効座標をリストアップして抽出
		if (mask != null) {
			List<int[]> validIndices = new ArrayList<>();
			ImageStack maskStack = mask.getStack();

			// マスク画像をスキャン (マスクサイズはFixedと一致している前提)
			for (int z = 0; z < d; z++) {
				// ImageStackは1-based index、配列は0-basedなので注意
				ImageProcessor ip = maskStack.getProcessor(z + 1);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						// 画素値が0より大きければ有効領域とみなす
						if (ip.getPixelValue(x, y) > 0) {
							validIndices.add(new int[] { x, y, z });
						}
					}
				}
			}

			// マスク領域が空の場合はnullを返して呼び出し元で対処させるか、全領域にフォールバック
			if (validIndices.isEmpty())
				return new int[0][0];

			// シャッフルして先頭から必要な数だけ取り出す
			Collections.shuffle(validIndices);
			int count = Math.min(validIndices.size(), numPoints);
			int[][] results = new int[count][3];
			for (int i = 0; i < count; i++) {
				results[i] = validIndices.get(i);
			}
			return results;
		}

		// B. マスクがない場合: 全空間からランダムサンプリング
		else {
			Random rand = new Random();
			int[][] points = new int[numPoints][3];
			for (int i = 0; i < numPoints; i++) {
				points[i][0] = rand.nextInt(w);
				points[i][1] = rand.nextInt(h);
				points[i][2] = rand.nextInt(d);
			}
			return points;
		}
	}

	/**
	 * 相互情報量計算 generateSamplePointsですでにマスク内の点のみが選ばれているため、 ここでのマスク判定処理は不要になりました。
	 */
	private static double calculateMutualInformation(ImagePlus fixed, ImagePlus moving, double[] params,
			double[] center, int[][] points, double[] rFix, double[] rMov) {
		double[][] jointHist = new double[BINS][BINS];
		double[] histFix = new double[BINS];
		double[] histMov = new double[BINS];
		double totalSamples = 0;

		ImageStack movStack = moving.getStack();
		ImageStack fixStack = fixed.getStack();

		// 回転行列の計算
		double[][] R = getRotationMatrix(params[3], params[4], params[5]);
		double tx = params[0], ty = params[1], tz = params[2];

		for (int[] p : points) {
			int x = p[0];
			int y = p[1];
			int z = p[2];

			// Fixed値取得
			double valFix = fixStack.getVoxel(x, y, z);

			// ★変更点: マスクがある場合、背景閾値によるスキップは不要（マスクがROIを定義しているため）
			// ただし、マスクなしの場合は以前のロジック同様に閾値を入れることも検討可能
			// ここではシンプルに、渡された点はすべて計算対象とします。

			int binFix = getBinIndex(valFix, rFix[0], rFix[1]);

			// 座標変換 (Fixed -> Moving)
			double dx = x - center[0];
			double dy = y - center[1];
			double dz = z - center[2];

			double rx = R[0][0] * dx + R[0][1] * dy + R[0][2] * dz;
			double ry = R[1][0] * dx + R[1][1] * dy + R[1][2] * dz;
			double rz = R[2][0] * dx + R[2][1] * dy + R[2][2] * dz;

			double u = rx + center[0] - tx;
			double v = ry + center[1] - ty;
			double w = rz + center[2] - tz;

			// Moving画像範囲外チェック
			if (u >= 0 && u < moving.getWidth() && v >= 0 && v < moving.getHeight() && w >= 0
					&& w < moving.getStackSize()) {
				double valMov = movStack.getVoxel((int) u, (int) v, (int) w);
				int binMov = getBinIndex(valMov, rMov[0], rMov[1]);

				jointHist[binFix][binMov]++;
				histFix[binFix]++;
				histMov[binMov]++;
				totalSamples++;
			}
		}

		if (totalSamples == 0)
			return 0;

		// エントロピー計算
		double entropyFix = 0;
		double entropyMov = 0;
		double entropyJoint = 0;

		for (int i = 0; i < BINS; i++) {
			if (histFix[i] > 0) {
				double p = histFix[i] / totalSamples;
				entropyFix -= p * Math.log(p);
			}
			if (histMov[i] > 0) {
				double p = histMov[i] / totalSamples;
				entropyMov -= p * Math.log(p);
			}
		}

		for (int i = 0; i < BINS; i++) {
			for (int j = 0; j < BINS; j++) {
				if (jointHist[i][j] > 0) {
					double p = jointHist[i][j] / totalSamples;
					entropyJoint -= p * Math.log(p);
				}
			}
		}

		return entropyFix + entropyMov - entropyJoint;
	}

	// --- 以下、ヘルパーメソッド ---

	private static int getBinIndex(double val, double min, double max) {
		if (val <= min)
			return 0;
		if (val >= max)
			return BINS - 1;
		return (int) ((val - min) / (max - min) * BINS);
	}

	private static double[] getMinMax(ImagePlus imp) {
		return new double[] { imp.getDisplayRangeMin(), imp.getDisplayRangeMax() };
	}

	private static double[][] getRotationMatrix(double rx, double ry, double rz) {
		double cx = Math.cos(rx), sx = Math.sin(rx);
		double cy = Math.cos(ry), sy = Math.sin(ry);
		double cz = Math.cos(rz), sz = Math.sin(rz);
		double[][] R = new double[3][3];
		// Rz * Ry * Rx
		R[0][0] = cy * cz;
		R[0][1] = -cy * sz;
		R[0][2] = sy;
		R[1][0] = sx * sy * cz + cx * sz;
		R[1][1] = -sx * sy * sz + cx * cz;
		R[1][2] = -sx * cy;
		R[2][0] = -cx * sy * cz + sx * sz;
		R[2][1] = cx * sy * sz + sx * cz;
		R[2][2] = cx * cy;
		return R;
	}

	/**
	 * 最終的な変換画像の生成 optimizeRegistrationで求めたパラメータを使って、全画素を高精度(Trilinear)補間します。
	 */
	private static ImagePlus applyTransform(ImagePlus fixed, ImagePlus moving, double[] params, boolean bicubic) {
		int w = fixed.getWidth();
		int h = fixed.getHeight();
		int d = fixed.getStackSize();

		ImageStack resultStack = new ImageStack(w, h);
		ImageStack movStack = moving.getStack();

		double cx = w / 2.0;
		double cy = h / 2.0;
		double cz = d / 2.0;
		double[][] R = getRotationMatrix(params[3], params[4], params[5]);
		double tx = params[0], ty = params[1], tz = params[2];

		// 並列処理で全画素リサンプリング
		ImageProcessor[] slices = new ImageProcessor[d];
		IntStream.range(0, d).parallel().forEach(z -> {
			ImageProcessor ip = fixed.getProcessor().createProcessor(w, h);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {

					double dx = x - cx;
					double dy = y - cy;
					double dz = z - cz;

					double rx = R[0][0] * dx + R[0][1] * dy + R[0][2] * dz;
					double ry = R[1][0] * dx + R[1][1] * dy + R[1][2] * dz;
					double rz = R[2][0] * dx + R[2][1] * dy + R[2][2] * dz;

					/*
					 * 「Fixed画像（基準画像）の画素 (x,y,z) を埋めるために、Moving画像（移動画像）のどこの画素 (u,v,w) を参照すればよいか」
					 * を示す、Moving画像上での浮動小数点座標インデックス
					 */
					double u = rx + cx - tx;
					double v = ry + cy - ty;
					double w_coord = rz + cz - tz;

					// 高精度補間 (前回のコードのTrilinear補間ロジック等を使用)
					double val = getInterpolatedValue(movStack, u, v, w_coord);
					ip.putPixelValue(x, y, val);
				}
			}
			slices[z] = ip;
		});

		for (ImageProcessor ip : slices)
			resultStack.addSlice(ip);

		ImagePlus result = new ImagePlus("Registered", resultStack);
		result.setCalibration(fixed.getCalibration());
		return result;
	}

	/**
	 * 3D トライリニア補間 (Trilinear Interpolation) 座標 (x, y, z)
	 * における画素値を、近傍8画素から線形補間して求めます。 画像範囲外の場合は 0.0 を返します（ゼロパディング）。
	 */
	private static double getInterpolatedValue(ImageStack stack, double x, double y, double z) {
		int w = stack.getWidth();
		int h = stack.getHeight();
		int d = stack.getSize();

		// 1. 範囲外チェック
		// 補間には「次の画素(+1)」が必要なため、上限は width-1 未満である必要があります
		if (x < 0 || x >= w - 1 || y < 0 || y >= h - 1 || z < 0 || z >= d - 1) {
			return 0.0;
		}

		// 2. 整数座標（左上奥）と小数部分（重み）の計算
		int ix = (int) x;
		int iy = (int) y;
		int iz = (int) z;

		double dx = x - ix;
		double dy = y - iy;
		double dz = z - iz;

		// 3. 近傍8画素の値を取得 (ImageJの getVoxel は x, y, z の順)
		// Z (手前)
		double v000 = stack.getVoxel(ix, iy, iz);
		double v100 = stack.getVoxel(ix + 1, iy, iz);
		double v010 = stack.getVoxel(ix, iy + 1, iz);
		double v110 = stack.getVoxel(ix + 1, iy + 1, iz);

		// Z+1 (奥)
		double v001 = stack.getVoxel(ix, iy, iz + 1);
		double v101 = stack.getVoxel(ix + 1, iy, iz + 1);
		double v011 = stack.getVoxel(ix, iy + 1, iz + 1);
		double v111 = stack.getVoxel(ix + 1, iy + 1, iz + 1);

		// 4. 補間計算

		// Z平面ごとの補間 (X軸方向 -> Y軸方向)
		double c00 = v000 * (1 - dx) + v100 * dx;
		double c10 = v010 * (1 - dx) + v110 * dx;

		double c01 = v001 * (1 - dx) + v101 * dx;
		double c11 = v011 * (1 - dx) + v111 * dx;

		// Y軸方向の統合
		double c0 = c00 * (1 - dy) + c10 * dy;
		double c1 = c01 * (1 - dy) + c11 * dy;

		// 最後にZ軸方向で統合
		return c0 * (1 - dz) + c1 * dz;
	}

}