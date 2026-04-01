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
import ij.CompositeImage;
import ij.process.ImageProcessor;
import ij.plugin.RGBStackMerge;

import javax.swing.*;
import java.awt.*;
import java.util.stream.IntStream;

/**
 * 純粋なImageJとJavaの計算のみで**「物理空間ベースの3Dアフィン変換（回転・移動・スケーリング・パディング）」**を行います。
 * 
 * このコードは「DICOMヘッダ情報（座標）が正しい」ことを前提とした幾何学的整合（Geometric Alignment）です。
 * もし、患者が撮影中に動いてしまった場合など、座標情報だけではズレてしまう場合は、この処理を行った後に、前回のコードで示した「重心合わせ」や、ImageJの
 * TurboReg などを用いた「画像ベースの微調整」を行うのが完璧なワークフローとなります。
 * 
 * 
 * @author tatsunidas
 *
 */
public class DicomFusionAdvanced {

    public static void main(String[] args) {
        // 1. 画像読み込み
        String fixedPath = "path/to/fixed_dicom_dir";
        String movingPath = "path/to/moving_dicom_dir";

        // テスト用（必要に応じて書き換えてください）
        ImagePlus fixedImg = IJ.openImage(fixedPath);
        ImagePlus movingImg = IJ.openImage(movingPath);

        if (fixedImg == null || movingImg == null) {
            IJ.log("画像の読み込みに失敗しました。");
            return;
        }

        // 2. ジオメトリ情報の抽出 (DICOMタグ解析)
        // Fixed画像の「物理空間の箱」を定義します
        GeometryInfo geoFixed = new GeometryInfo(fixedImg);
        GeometryInfo geoMoving = new GeometryInfo(movingImg);

        IJ.log("リサンプリング計算を開始します (回転・位置・パディング処理)...");

        // 3. リサンプリング実行
        // Moving画像を、Fixed画像の座標系(グリッド)へ変換します
        ImagePlus resampledMoving = resampleImage3D(fixedImg, movingImg, geoFixed, geoMoving);

        // 4. 重ね合わせ表示 (Composite)
        showFusionUI(fixedImg, resampledMoving);
    }

    /**
     * 3Dリサンプリング処理
     * Fixed画像の全画素位置について、物理座標を計算し、そこにあるMoving画像の画素値を取得します。
     * これにより、回転補正、位置合わせ、FOV調整、パディングが同時に完了します。
     */
    private static ImagePlus resampleImage3D(ImagePlus fixed, ImagePlus moving, 
                                             GeometryInfo gFix, GeometryInfo gMov) {
        
        int w = fixed.getWidth();
        int h = fixed.getHeight();
        int d = fixed.getStackSize();

        ImageStack resultStack = new ImageStack(w, h);
        ImageStack movingStack = moving.getStack();

        // 高速化のため並列処理 (Parallel Stream)
        // Fixed画像の各スライス(z)についてループ
        // 注意: 結果を順番通りにStackに積むため、計算結果を配列に保持します
        ImageProcessor[] resultSlices = new ImageProcessor[d];

        IntStream.range(0, d).parallel().forEach(z -> {
            // Fixed画像の1スライス分のバッファ確保
            ImageProcessor ip = fixed.getProcessor().createProcessor(w, h);
            
            // ピクセルごとにスキャン
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    
                    // Step A: Fixed画像上の画素(x,y,z)を物理座標(mm)に変換
                    double[] physicalPoint = gFix.transformIndexToPhysicalPoint(x, y, z);

                    // Step B: 物理座標(mm)をMoving画像の画素インデックス(u,v,w)に逆変換
                    // ここで回転(Orientation)や原点ズレ(Position)が解消されます
                    double[] movingIndex = gMov.transformPhysicalPointToIndex(physicalPoint);

                    // Step C: 補間して画素値を取得 (境界外なら0 = パディング)
                    double pixelValue = getInterpolatedPixel(movingStack, movingIndex[0], movingIndex[1], movingIndex[2]);
                    
                    ip.putPixelValue(x, y, pixelValue);
                }
            }
            resultSlices[z] = ip;
        });

        // スタックの構築
        for (ImageProcessor ip : resultSlices) {
            resultStack.addSlice(ip);
        }

        ImagePlus resultImp = new ImagePlus("Resampled Moving", resultStack);
        resultImp.setCalibration(fixed.getCalibration().copy()); // メタデータはFixedと同一になる
        return resultImp;
    }

    /**
     * トライリニア補間 (Trilinear Interpolation)
     * 座標が画像の範囲外にある場合は 0 を返します (パディング処理)
     */
    private static double getInterpolatedPixel(ImageStack stack, double x, double y, double z) {
        int w = stack.getWidth();
        int h = stack.getHeight();
        int d = stack.getSize();

        // 範囲チェック (パディング処理)
        if (x < 0 || x >= w - 1 || y < 0 || y >= h - 1 || z < 0 || z >= d - 1) {
            return 0.0; // 背景色
        }

        int x0 = (int) x; int x1 = x0 + 1;
        int y0 = (int) y; int y1 = y0 + 1;
        int z0 = (int) z; int z1 = z0 + 1;

        // 小数部
        double xd = x - x0;
        double yd = y - y0;
        double zd = z - z0;

        // 近傍8点の値を取得 (Zは1始まりのStackインデックスに注意)
        double c000 = stack.getVoxel(x0, y0, z0);
        double c100 = stack.getVoxel(x1, y0, z0);
        double c010 = stack.getVoxel(x0, y1, z0);
        double c110 = stack.getVoxel(x1, y1, z0);
        double c001 = stack.getVoxel(x0, y0, z1);
        double c101 = stack.getVoxel(x1, y0, z1);
        double c011 = stack.getVoxel(x0, y1, z1);
        double c111 = stack.getVoxel(x1, y1, z1);

        // 線形補間
        double c00 = c000 * (1 - xd) + c100 * xd;
        double c01 = c001 * (1 - xd) + c101 * xd;
        double c10 = c010 * (1 - xd) + c110 * xd;
        double c11 = c011 * (1 - xd) + c111 * xd;

        double c0 = c00 * (1 - yd) + c10 * yd;
        double c1 = c01 * (1 - yd) + c11 * yd;

        return c0 * (1 - zd) + c1 * zd;
    }

    // --- ジオメトリ計算ヘルパークラス ---
    static class GeometryInfo {
        double[] origin = new double[3]; // Image Position (Patient)
        double[] spacing = new double[3]; // Pixel Spacing, Slice Thickness
        double[][] direction = new double[3][3]; // Image Orientation (Patient) Matrix

        public GeometryInfo(ImagePlus imp) {
            // 注意: 実運用ではDICOMヘッダから確実にパースする必要があります。
            // ここではImageJのCalibrationとPropertiesから簡易的に取得するロジックです。
            
            // 1. Spacing
            spacing[0] = imp.getCalibration().pixelWidth;
            spacing[1] = imp.getCalibration().pixelHeight;
            spacing[2] = imp.getCalibration().pixelDepth;

            // 2. Origin & Direction (Infoプロパティから解析)
            String info = (String) imp.getProperty("Info");
            parseDicomHeader(info);
        }

        // 画素インデックス(i,j,k) -> 物理座標(x,y,z)
        // P = Origin + i*DirX*SpcX + j*DirY*SpcY + k*DirZ*SpcZ
        public double[] transformIndexToPhysicalPoint(double i, double j, double k) {
            double px = origin[0] + (i * spacing[0] * direction[0][0]) + (j * spacing[1] * direction[0][1]) + (k * spacing[2] * direction[0][2]);
            double py = origin[1] + (i * spacing[0] * direction[1][0]) + (j * spacing[1] * direction[1][1]) + (k * spacing[2] * direction[1][2]);
            double pz = origin[2] + (i * spacing[0] * direction[2][0]) + (j * spacing[1] * direction[2][1]) + (k * spacing[2] * direction[2][2]);
            return new double[]{px, py, pz};
        }

        // 物理座標(x,y,z) -> 画素インデックス(i,j,k)
        // これは回転行列の逆行列(直交行列なら転置)を使って解きますが、
        // 簡易実装として、連立方程式を解くか、逆変換行列を保持するのが一般的です。
        // ここでは、正規直交基底を前提としてベクトル内積で求めます。
        public double[] transformPhysicalPointToIndex(double[] p) {
            // P_rel = P - Origin
            double dx = p[0] - origin[0];
            double dy = p[1] - origin[1];
            double dz = p[2] - origin[2];

            // DICOMのDirectionは通常、正規直交(Rotation Matrix)です。
            // Index = (P_rel dot Dir_axis) / Spacing
            
            // i成分 (Row方向)
            double i = (dx * direction[0][0] + dy * direction[1][0] + dz * direction[2][0]) / spacing[0];
            // j成分 (Column方向)
            double j = (dx * direction[0][1] + dy * direction[1][1] + dz * direction[2][1]) / spacing[1];
            // k成分 (Slice方向 - 外積で求めるNormalベクトル)
            double k = (dx * direction[0][2] + dy * direction[1][2] + dz * direction[2][2]) / spacing[2];

            return new double[]{i, j, k};
        }

        private void parseDicomHeader(String info) {
            // デフォルト値 (単位行列)
            direction[0][0] = 1; direction[1][1] = 1; direction[2][2] = 1;
            
            if (info == null) return;

            String imgPos = null;
            String imgOri = null;

            // ImageJのInfo文字列からタグ検索 (簡易実装)
            for (String line : info.split("\n")) {
                if (line.startsWith("0020,0032")) imgPos = getVal(line); // Position
                if (line.startsWith("0020,0037")) imgOri = getVal(line); // Orientation
            }

            if (imgPos != null) {
                String[] p = imgPos.split("\\\\");
                if(p.length >= 3) {
                    origin[0] = Double.parseDouble(p[0]);
                    origin[1] = Double.parseDouble(p[1]);
                    origin[2] = Double.parseDouble(p[2]);
                }
            }

            if (imgOri != null) {
                String[] d = imgOri.split("\\\\");
                if(d.length >= 6) {
                    // Row Vector (X軸方向)
                    direction[0][0] = Double.parseDouble(d[0]);
                    direction[1][0] = Double.parseDouble(d[1]);
                    direction[2][0] = Double.parseDouble(d[2]);
                    
                    // Column Vector (Y軸方向)
                    direction[0][1] = Double.parseDouble(d[3]);
                    direction[1][1] = Double.parseDouble(d[4]);
                    direction[2][1] = Double.parseDouble(d[5]);

                    // Slice Vector (Z軸方向 = X x Y 外積)
                    // DICOMヘッダにはZ方向ベクトルはないため、外積で算出する必要があります
                    direction[0][2] = direction[1][0]*direction[2][1] - direction[2][0]*direction[1][1];
                    direction[1][2] = direction[2][0]*direction[0][1] - direction[0][0]*direction[2][1];
                    direction[2][2] = direction[0][0]*direction[1][1] - direction[1][0]*direction[0][1];
                }
            }
        }
        private String getVal(String line) { return line.split("=")[1].trim(); }
    }

    /**
     * 結果表示UI (前回のコードと同様)
     */
    private static void showFusionUI(ImagePlus fixed, ImagePlus moving) {
        ImagePlus composite = new CompositeImage(fixed, CompositeImage.COMPOSITE);
        composite = RGBStackMerge.mergeChannels(new ImagePlus[]{fixed, moving}, false);
        composite.setTitle("Fusion Result (Corrected)");
        composite.show();
        
        JFrame frame = new JFrame("Alpha Control");
        JSlider slider = new JSlider(0, 100, 50);
        ImagePlus finalComposite = composite;
        slider.addChangeListener(e -> {
            ((CompositeImage)finalComposite).setC(2); 
            IJ.run(finalComposite, "Enhance Contrast", "saturated=0.35");
        });
        frame.add(slider);
        frame.pack();
        frame.setVisible(true);
    }
}
