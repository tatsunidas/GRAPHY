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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ResourceBundle;
import java.util.logging.Level;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;

@SuppressWarnings({ "serial"})
public class GraphySplashScreen extends JFrame {

	JProgressBar progress;

	public GraphySplashScreen() {
		setLayout(new BorderLayout());
		setUndecorated(true);// title bar no visible
		Image splash = Resources.Splash.loadIconFromResource().getImage();
		// Create a buffered image with transparency
		BufferedImage bimage = new BufferedImage(splash.getWidth(null), splash.getHeight(null),
				BufferedImage.TYPE_INT_ARGB);

		// Draw the image on to the buffered image
		Graphics2D bGr = bimage.createGraphics();
		bGr.drawImage(splash, 0, 0, null);
		bGr.dispose();
		SplashPanel sp = new SplashPanel(bimage);
		add(sp, BorderLayout.CENTER);

		progress = new JProgressBar();
		progress.setMaximum(0);// default
		progress.setMaximum(99);// default
		progress.setStringPainted(true);
		progress.setString("Ready to start ...");
		add(progress, BorderLayout.SOUTH);

		// ★ 修正1: サイズ計算は pack() にすべて任せる！
		// SplashPanel が PreferredSize を持っているため、pack() だけで画像とバーが完璧に収まります。
		pack();

		// 注意: ここにあった setSize(...) は、高DPI環境でスケーリングを破壊するため削除しました。

		setLocationRelativeTo(null);
		setVisible(true);
		toFront();
	}

	public void startProgressAndClose(String progressPrefix, int max) {
		new Thread() {
			public void run() {
				// ★ 修正2: プログレスバー（UIコンポーネント）の操作は必ず invokeLater で包んで安全に行う！
				SwingUtilities.invokeLater(() -> {
					progress.setMaximum(max);
					progress.setString("[" + progressPrefix + "]:"
							+ ResourceBundle.getBundle("i18n.i18n").getString("GraphySplashScreen.readyToStart"));
				});

				for (int i = 0; i < max; i++) {
					final int currentValue = i;
					// UIの更新だけをEDT（UIスレッド）に投げる
					SwingUtilities.invokeLater(() -> {
						progress.setValue(currentValue);
					});

					try {
						Thread.sleep(76);
					} catch (InterruptedException e) {
						e.printStackTrace();
						Log.logger.log(Level.SEVERE, e.getMessage());
					}
				}
				
				SwingUtilities.invokeLater(() -> {
					progress.setString("GRAPHY start ...");
				});
				
				try {
					Thread.sleep(max < 5 ? 2000 : 1200);
				} catch (InterruptedException e) {
					e.printStackTrace();
					Log.logger.log(Level.SEVERE, e.getMessage());
				}
				
				// ウィンドウを閉じる処理もUI操作なので包む
				SwingUtilities.invokeLater(() -> {
					dispose();
				});
			}
		}.start();
	}

	private class SplashPanel extends JPanel {
		private BufferedImage bg;
		int w;
		int h;

		SplashPanel(BufferedImage bg) {
			this.bg = bg;
			this.w = bg.getWidth();
			this.h = bg.getHeight();
			this.setBounds(0, 0, w, h);
			this.setPreferredSize(new Dimension(w, h));
		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(bg, 0, 0, null);
		}
	}
}
