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
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.ImageProcessor;

public class UseWandFunctionDirectly {

    public static void main(String[] args) {
        // ImageJの本体を初期化 (GUIなしでも動作可能)

        // 処理対象の画像を開く
        ImagePlus imp = IJ.openImage("https://imagej.net/ij/images/blobs.gif");
        if (imp == null) {
            IJ.error("画像を開けませんでした。");
            return;
        }
        imp.show(); // 処理結果を視覚的に確認するために表示

        // 画像のプロセッサーを取得
        ImageProcessor ip = imp.getProcessor();

        // Wand選択を実行したい座標を指定
        int x = 68;
        int y = 86;

        // Wandオブジェクトを生成
        Wand wand = new Wand(ip);

        // 指定した座標から輪郭を自動検出
        // autoOutline(x, y) は、ImageJのグローバルな許容差設定を使います。
        // autoOutline(x, y, tolerance) で許容差を直接指定することも可能です。
        wand.autoOutline(x, y, 190, 255);
        
        int n = wand.npoints;
        int[] xp = wand.xpoints;
        int[] yp = wand.ypoints;
        
        Roi roi = new PolygonRoi(xp, yp, n, PolygonRoi.TRACED_ROI);

        // 画像に検出したROIを設定（選択範囲として表示される）
        if (roi != null) {
            imp.setRoi(roi);
            imp.updateAndDraw();
            IJ.log("座標 (" + x + ", " + y + ") を起点とするWand選択が完了しました。");
        }
    }
}