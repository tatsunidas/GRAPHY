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
package com.vis.core.facade;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.ResourceBundle;
import java.util.logging.Level;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.vis.core.log.Log;

@SuppressWarnings({ "serial"})
public class GraphySplashScreen extends JFrame {

	JProgressBar progress;

	public GraphySplashScreen() {
		setLayout(new BorderLayout());
		setUndecorated(true);

		// 1. ImageIconを使用して画像を確実にロードする
		Image splash = com.vis.configuration.Resources.Splash.loadImageFromResource();
		
		// 2. ★HiDPI対策：現在のモニターの解像度（スケーリング）に合わせてサイズを計算する
		// モニターのDPIを取得し、標準の96DPIに対して何倍にすべきか算出します
		int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
		double scale = dpi / 96.0;
		if (scale < 1.0) scale = 1.0;

		// 元の画像サイズ (869x495) に倍率をかける
		int targetW = (int) (869 * scale);
		int targetH = (int) (495 * scale);

		// 3. 画像をパネル全体に拡大描画する「SplashPanel」を追加
		SplashPanel sp = new SplashPanel(splash, targetW, targetH);
		add(sp, BorderLayout.CENTER);

		progress = new JProgressBar();
		progress.setMaximum(99);
		progress.setStringPainted(true);
		progress.setString("Ready to start ...");
		// プログレスバーの高さもスケーリングに合わせて微調整
		progress.setPreferredSize(new Dimension(targetW, (int)(25 * scale)));
		add(progress, BorderLayout.SOUTH);

		// ウィンドウサイズを計算したターゲットサイズに合わせる
		pack(); 
		
		setLocationRelativeTo(null);
		setVisible(true);
		toFront();
	}

	/**
	 * 画像をパネルのサイズに合わせて高品質に引き伸ばして描画するインナークラス
	 */
	private class SplashPanel extends JPanel {
		private Image img;

		SplashPanel(Image img, int w, int h) {
			this.img = img;
			// 推奨サイズを設定することで pack() した時にこのサイズになります
			setPreferredSize(new Dimension(w, h));
			setBackground(Color.BLACK);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2d = (Graphics2D) g;

			// ★ 拡大描画の設定（ボケを抑えて高品質にする）
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// 画像をパネルの左上(0,0)から、パネルの幅・高さ一杯に描画
			if (img != null) {
				g2d.drawImage(img, 0, 0, getWidth(), getHeight(), this);
			}
		}
	}

	public void startProgressAndClose(String progressPrefix, int max) {
		new Thread() {
			public void run() {
				SwingUtilities.invokeLater(() -> {
					progress.setMaximum(max);
					progress.setString("[" + progressPrefix + "]:"
							+ ResourceBundle.getBundle("i18n.i18n").getString("GraphySplashScreen.readyToStart"));
				});

				for (int i = 0; i < max; i++) {
					final int currentValue = i;
					SwingUtilities.invokeLater(() -> progress.setValue(currentValue));

					try {
						Thread.sleep(76);
					} catch (InterruptedException e) {
						Log.logger.log(Level.SEVERE, e.getMessage());
					}
				}
				
				SwingUtilities.invokeLater(() -> progress.setString("GRAPHY start ..."));
				
				try {
					Thread.sleep(max < 5 ? 2000 : 1200);
				} catch (InterruptedException e) {
					Log.logger.log(Level.SEVERE, e.getMessage());
				}
				
				SwingUtilities.invokeLater(() -> dispose());
			}
		}.start();
	}
}
