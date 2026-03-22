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
package com.vis.core.view.D3.ui;

import org.lwjgl.opengl.awt.GLData;
import java.awt.BorderLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
 * 
 * @author tatsunidas
 *
 */
public class Viewer3DMain extends JFrame {

	// debug
	public static void main(String[] args) {
		// SwingのEDTスレッドで起動
		SwingUtilities.invokeLater(() -> {
			Viewer3DMain frame = new Viewer3DMain();
			frame.setVisible(true); // ウィンドウを表示
			frame.revalidate();
			frame.repaint();

			javax.swing.Timer timer = new javax.swing.Timer(16, e -> { // 約60FPS
				if (frame.canvas != null) {
					frame.canvas.render(); // これが呼ばれると paintGL() が動く
					frame.canvas.repaint();
				}
			});
			timer.setRepeats(true);
			timer.start();
		});
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private GLCanvas canvas;

	public Viewer3DMain() {
		setTitle("GRAPHY 3D Viewer");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1000, 800);
		setLayout(new BorderLayout());

		// 1. OpenGLの設定データを作成
		GLData data = new GLData();
		// ★修正点: バージョン指定をあえてコメントアウトするか、低く設定します
		// LinuxのAWT環境では、3.3 Coreを要求すると失敗することが多いです。
		data.majorVersion = 3;
		data.minorVersion = 0; // 3.0まで落とす

		// ★修正点: プロファイルを指定しない（ドライバ任せ）か、COMPATIBILITYにする
		data.profile = GLData.Profile.CORE;

		// ダブルバッファはfalse：swapBufferのため
		data.doubleBuffer = false;

		// 2. キャンバスを作成して中央に配置
		canvas = new GLCanvas(data);
		add(canvas, BorderLayout.CENTER);

		// 3. メニューバーの作成
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");

		JMenuItem openItem = new JMenuItem("Open DICOM/Obj...");
		openItem.addActionListener(e -> {
			// ファイル選択ダイアログ
//            JFileChooser fileChooser = new JFileChooser();
//            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
//            fileChooser.setCurrentDirectory(new File("."));
//            int result = fileChooser.showOpenDialog(this);

			// debug
			String path = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";

//            if (result == JFileChooser.APPROVE_OPTION) {
//                String path = fileChooser.getSelectedFile().getAbsolutePath();

			// ★ここを追加：別スレッドで読み込む（UIを固まらせないため）
			new Thread(() -> {
				VolumeData vol = VolumeLoader.loadDicom(path);
				if (vol != null) {
					// Canvasにデータを渡す
					canvas.setVolumeData(vol); // ← これを使う
				}
			}).start();
//            }
		});

		fileMenu.add(openItem);
		fileMenu.addSeparator();
		fileMenu.add(new JMenuItem("Exit"));
		menuBar.add(fileMenu);
		setJMenuBar(menuBar);

		// 4. ボタンパネルの作成（右側）
		JPanel controlPanel = new JPanel();
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
		controlPanel.add(new JLabel("Control Panel"));
		
		JButton resetCamera = new JButton("Reset Camera");
		resetCamera.addActionListener(e -> {
			new Thread(() -> {
				canvas.resetCamera();
			}).start();
		});
		controlPanel.add(resetCamera);

		JCheckBox showMipChk = new JCheckBox("Show MIP");
		showMipChk.setSelected(true);
		showMipChk.addActionListener(e -> {
			new Thread(() -> {
				boolean isMip = showMipChk.isSelected();
				canvas.setMIPMode(isMip);
			}).start();
		});

		controlPanel.add(showMipChk);

		JCheckBox chkOrtho = new JCheckBox("Ortho Slices Mode");
		chkOrtho.addActionListener(e -> canvas.setOrthoMode(chkOrtho.isSelected()));
		controlPanel.add(chkOrtho);

		// X Slider
		JSlider sliderX = new JSlider(0, 100, 50);
		controlPanel.add(new JLabel("Sagittal (X)"));
		controlPanel.add(sliderX);

		// Y Slider
		JSlider sliderY = new JSlider(0, 100, 50);
		controlPanel.add(new JLabel("Coronal (Y)"));
		controlPanel.add(sliderY);

		// Z Slider
		JSlider sliderZ = new JSlider(0, 100, 50);
		controlPanel.add(new JLabel("Axial (Z)"));
		controlPanel.add(sliderZ);

		sliderX.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));
		sliderY.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));
		sliderZ.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));

		add(controlPanel, BorderLayout.EAST);
	}

	private void updateSlices(GLCanvas canvas, JSlider sx, JSlider sy, JSlider sz) {
		float x = sx.getValue() / 100.0f;
		float y = sy.getValue() / 100.0f;
		float z = sz.getValue() / 100.0f;
		canvas.setSlicePos(x, y, z);
	}
}
