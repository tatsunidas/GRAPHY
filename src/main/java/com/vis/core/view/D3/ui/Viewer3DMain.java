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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
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
	public GLCanvas canvas;
	
	private JPanel roiColorPanel;

	public Viewer3DMain() {
		setTitle("GRAPHY 3D Viewer");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(1000, 800);
		setLayout(new BorderLayout());

		// 1. OpenGLの設定データを作成
		GLData data = new GLData();
		data.majorVersion = 3;
		data.minorVersion = 3;//3.2 or above

		// OpenGL >= 3.2 
		data.profile = GLData.Profile.CORE;

		// ダブルバッファはfalse：swapBufferのため
		data.doubleBuffer = false;
		
		data.forwardCompatible = true;

		// 2. キャンバスを作成して中央に配置
		canvas = new GLCanvas(data);
		add(canvas, BorderLayout.CENTER);

		// 3. メニューバーの作成
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");

		JMenuItem openItem = new JMenuItem("Open DICOM/Obj...");
		openItem.addActionListener(e -> {
			// ファイル選択ダイアログ
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fileChooser.setCurrentDirectory(new File("."));
			int result = fileChooser.showOpenDialog(this);

			// debug
//			String path = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";

			if (result == JFileChooser.APPROVE_OPTION) {
				String path = fileChooser.getSelectedFile().getAbsolutePath();

				// ★ここを追加：別スレッドで読み込む（UIを固まらせないため）
				new Thread(() -> {
					VolumeData vol = VolumeLoader.loadDicom(path);
					if (vol != null) {
						// Canvasにデータを渡す
						canvas.setVolumeData(vol); // ← これを使う
					}
				}).start();
			}
		});

		fileMenu.add(openItem);
		fileMenu.addSeparator();
		fileMenu.add(new JMenuItem("Exit"));
		menuBar.add(fileMenu);
		setJMenuBar(menuBar);

		// 4. ボタンパネルの作成（右側）
		JPanel controlPanel = new JPanel(new GridBagLayout());
		controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // パネル全体の余白
		controlPanel.setPreferredSize(new Dimension(320, 800)); // 扱いやすい幅を確保

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = GridBagConstraints.REMAINDER; // 横幅いっぱいに配置
		gbc.fill = GridBagConstraints.HORIZONTAL; // 横方向に引き伸ばす
		gbc.insets = new Insets(4, 4, 4, 4); // コンポーネント間の標準的な余白
		gbc.weightx = 1.0;
		gbc.weighty = 0.0;

		// タイトルラベル
		JLabel titleLabel = new JLabel("Control Panel", SwingConstants.CENTER);
		titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
		controlPanel.add(titleLabel, gbc);
		gbc.gridy++;

		// カメラリセットボタン
		JButton resetCamera = new JButton("Reset Camera");
		resetCamera.addActionListener(e -> {
			new Thread(() -> {
				canvas.resetCamera();
			}).start();
		});
		controlPanel.add(resetCamera, gbc);
		gbc.gridy++;

		// チェックボックス：MIP
		JCheckBox showMipChk = new JCheckBox("Show MIP");
		showMipChk.setSelected(true);
		showMipChk.addActionListener(e -> {
			new Thread(() -> {
				boolean isMip = showMipChk.isSelected();
				canvas.setMIPMode(isMip);
			}).start();
		});
		controlPanel.add(showMipChk, gbc);
		gbc.gridy++;

		// チェックボックス：Ortho Mode
		JCheckBox chkOrtho = new JCheckBox("Ortho Slices Mode");
		chkOrtho.addActionListener(e -> canvas.setOrthoMode(chkOrtho.isSelected()));
		controlPanel.add(chkOrtho, gbc);
		gbc.gridy++;

		// 区切り線 1
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// X Slider
		JSlider sliderX = new JSlider(0, 100, 50);
		controlPanel.add(new JLabel("Sagittal (X)"), gbc);
		gbc.gridy++;
		controlPanel.add(sliderX, gbc);
		gbc.gridy++;

		// Y Slider
		JSlider sliderY = new JSlider(0, 100, 50);
		controlPanel.add(new JLabel("Coronal (Y)"), gbc);
		gbc.gridy++;
		controlPanel.add(sliderY, gbc);
		gbc.gridy++;

		// Z Slider
		JSlider sliderZ = new JSlider(0, 100, 50);
		controlPanel.add(new JLabel("Axial (Z)"), gbc);
		gbc.gridy++;
		controlPanel.add(sliderZ, gbc);
		gbc.gridy++;

		// 区切り線 2
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// チェックボックス：Show Volume
		JCheckBox chkShowVol = new JCheckBox("Show Volume", true);
		chkShowVol.addActionListener(e -> canvas.setShowVolume(chkShowVol.isSelected()));
		controlPanel.add(chkShowVol, gbc);
		gbc.gridy++;

		// チェックボックス：Show ROI
		JCheckBox chkShowRoi = new JCheckBox("Show ROI", true);
		chkShowRoi.addActionListener(e -> canvas.setShowRoi(chkShowRoi.isSelected()));
		controlPanel.add(chkShowRoi, gbc);
		gbc.gridy++;

		// 区切り線 3
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// Ortho ROI Display Mode
		controlPanel.add(new JLabel("Ortho ROI Display Mode"), gbc);
		gbc.gridy++;

		String[] orthoModes = { "No ROI", "Slice Overlay (2D)", "Float Overlay (3D)", "Embedded (3D)" };
		javax.swing.JComboBox<String> comboOrthoRoi = new javax.swing.JComboBox<>(orthoModes);
		comboOrthoRoi.setSelectedIndex(1); // デフォルトは SLICE_2D
		comboOrthoRoi.addActionListener(e -> {
			int idx = comboOrthoRoi.getSelectedIndex();
			if (canvas != null) {
				if (idx == 0)
					canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.NONE);
				else if (idx == 1)
					canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.SLICE_2D);
				else if (idx == 2)
					canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.FLOAT_3D);
				else if (idx == 3)
					canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.EMBEDDED_3D);
			}
		});
		controlPanel.add(comboOrthoRoi, gbc);
		gbc.gridy++;

		// ROI Opacity
		JSlider sliderRoiAlpha = new JSlider(0, 100, 50);
		sliderRoiAlpha.setToolTipText("Adjust ROI Opacity");
		controlPanel.add(new JLabel("ROI Opacity"), gbc);
		gbc.gridy++;
		controlPanel.add(sliderRoiAlpha, gbc);
		gbc.gridy++;

		// イベントリスナーの登録
		sliderRoiAlpha.addChangeListener(e -> {
			if (canvas != null) {
				float alpha = sliderRoiAlpha.getValue() / 100.0f;
				canvas.setRoiAlpha(alpha);
			}
		});

		sliderX.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));
		sliderY.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));
		sliderZ.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));

		// 区切り線 4
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// ROI Group Colors タイトル
		controlPanel.add(new JLabel("ROI Group Colors"), gbc);
		gbc.gridy++;

		// ★ここが最大のポイント：JScrollPaneにのみ残りの縦幅を全割り当てする
		gbc.weighty = 1.0; // 縦方向の拡張ウェイトを設定
		gbc.fill = GridBagConstraints.BOTH; // 縦横両方に広げる設定に変更
		gbc.insets = new Insets(4, 4, 0, 4); // 最下部の余白調整

		roiColorPanel = new JPanel();
		roiColorPanel.setLayout(new BoxLayout(roiColorPanel, BoxLayout.Y_AXIS));
		JScrollPane roiScrollPane = new JScrollPane(roiColorPanel);
		roiScrollPane.setMinimumSize(new Dimension(200, 150)); // 最低限の高さ・幅を保証
		controlPanel.add(roiScrollPane, gbc);

		// ★追加: キャンバスから「ROI情報が更新されたよ」という通知を受け取ってUIを作る
		canvas.setOnRoiLoadedCallback(() -> refreshRoiColorUI());

		add(controlPanel, BorderLayout.EAST);
	}

	private void updateSlices(GLCanvas canvas, JSlider sx, JSlider sy, JSlider sz) {
		float x = sx.getValue() / 100.0f;
		float y = sy.getValue() / 100.0f;
		float z = sz.getValue() / 100.0f;
		canvas.setSlicePos(x, y, z);
	}
	
	// ==========================================
	// Viewer3DMainクラス内に新しいメソッドを追加
	// ==========================================
	private void refreshRoiColorUI() {
	    roiColorPanel.removeAll();
	    java.util.List<String> names = canvas.getRoiGroupNames();
	    java.util.List<java.awt.Color> colors = canvas.getRoiColors();

	    if (names == null || names.isEmpty()) {
	        roiColorPanel.add(new JLabel("No ROIs loaded."));
	    } else {
	        // 読み込まれたROIグループの数だけボタンを作る
	        for (int i = 0; i < names.size(); i++) {
	            final int index = i;
	            String name = names.get(i);
	            java.awt.Color c = colors.get(i);

	            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));
	            
	            // 色を表示・変更するためのボタン
	            JButton colorBtn = new JButton();
	            colorBtn.setPreferredSize(new java.awt.Dimension(20, 20));
	            colorBtn.setBackground(c);
	            colorBtn.setOpaque(true); // Windows/Mac等の見た目の違いを吸収
	            colorBtn.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK));

	            // ボタンを押したらカラーピッカーを開く
	            colorBtn.addActionListener(e -> {
	                java.awt.Color newColor = javax.swing.JColorChooser.showDialog(this, "Select Color for " + name, colorBtn.getBackground());
	                if (newColor != null) {
	                    colorBtn.setBackground(newColor);
	                    // Canvasに新しい色を伝える
	                    canvas.setRoiGroupColor(index, newColor);
	                }
	            });

	            row.add(colorBtn);
	            row.add(new JLabel(name));
	            roiColorPanel.add(row);
	        }
	    }
	    roiColorPanel.revalidate();
	    roiColorPanel.repaint();
	}
}
