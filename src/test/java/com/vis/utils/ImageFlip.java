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
package com.vis.utils;

import com.vis.core.view.D2.processing.ImageProcessing;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

/**
 * なぜか、上下反転はうまく行くが、左右反転ができない問題をテスト。
 */
public class ImageFlip {

	public static void main(String[] args) throws InterruptedException {
		String IMAGE_URL = "https://imagej.net/ij/images/CT%20Scan.dcm";
		ImagePlus imp = IJ.openImage(IMAGE_URL);
        if (imp == null) {
            IJ.error("画像の読み込みに失敗しました: " + IMAGE_URL);
            return;
        }
        imp.show(); // 画像ウィンドウを表示
        
        Thread.sleep(2000);

        // 2. カスタム左右反転を実行
        IJ.log("カスタム左右反転 (flipLR) を実行します...");
        
        ImageProcessing ips = new ImageProcessing();
        
        ips.flipLR(imp);
        
        IJ.log("左右反転処理が完了しました。画像を確認してください。");
        // このテストが成功した場合、元画像と反転画像は異なるはずです。
	}
	
	/**
     * 左右反転 (水平反転) を実行するカスタムメソッド。
     * ip.get() と ip.set() を使用してピクセルをスワップします。
     * * @param imp 対象のImagePlusオブジェクト
     */
    public static void flipLR(ImagePlus imp) {
        if (imp == null) return;

        imp.lock(); // 画像をロック
        try {
            ImageProcessor ip = imp.getProcessor();
            int width = ip.getWidth();
            int height = ip.getHeight();

            int tempValue; // テンポラリ変数

            // すべての行 (y) についてループ
            for (int y = 0; y < height; y++) {
                // 行の半分 (width / 2) までループ
                for (int x = 0; x < width / 2; x++) {

                    int leftX = x;
                    int rightX = width - 1 - x;

                    // 1. 左側のピクセル値を取得
                    tempValue = ip.get(leftX, y);

                    // 2. 右側のピクセル値を左側に設定
                    ip.set(leftX, y, ip.get(rightX, y));

                    // 3. テンポラリに保存した左側のピクセル値を右側に設定
                    ip.set(rightX, y, tempValue);
                }
            }
        } catch (Exception e) {
            IJ.handleException(e);
        } finally {
            imp.unlock(); // 処理が終わったら必ずロック解除
        }
        
        imp.updateAndDraw();
    }

}
