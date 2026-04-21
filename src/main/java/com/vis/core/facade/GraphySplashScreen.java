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
import java.util.ResourceBundle;
import java.util.logging.Level;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;

@SuppressWarnings({ "serial"})
public class GraphySplashScreen extends JFrame {

	JProgressBar progress;

	public GraphySplashScreen() {
		setLayout(new BorderLayout());
		setUndecorated(true);

		// 画像の読み込み完了を確実に待機する（元のコードの読み込み失敗バグを解消）
		ImageIcon splashIcon = Resources.Splash.loadIconFromResource();
		
		// JLabelに渡すことで、画像の「本来のピクセルサイズ（869x495）」で一切拡大せずに等倍表示する。
		// 拡大しないため、絶対にボケません。
		JLabel splashLabel = new JLabel(splashIcon);
		add(splashLabel, BorderLayout.CENTER);

		progress = new JProgressBar();
		progress.setMaximum(99);
		progress.setStringPainted(true);
		progress.setString("Ready to start ...");
		add(progress, BorderLayout.SOUTH);

		pack(); // 画像の本来のサイズに合わせてウィンドウをピタッと包み込む
		setLocationRelativeTo(null);
		setVisible(true);
		toFront();
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
