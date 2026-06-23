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

import com.vis.core.log.Log;
import com.vis.core.view.D3.endo.EndoCamera;
import com.vis.core.view.D3.endo.EndoCommands;
import com.vis.core.view.D3.endo.EndoPath3D;
import com.vis.core.view.D3.roi.FreeFormRoi3D;
import com.vis.core.view.D3.util.AlignMesh;
import com.vis.core.view.D3.util.MeshExporter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup; // 追加
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton; // 追加
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * * @author tatsunidas
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
				if (frame.canvas != null && frame.canvas.isDisplayable()) {
					frame.canvas.render(); // これが呼ばれると paintGL() が動く
					frame.canvas.repaint();
				} else {
					// ウィンドウが閉じられてキャンバスが破棄されたら、このタイマー自体を安全に停止させる
					((javax.swing.Timer) e.getSource()).stop();
				}
			});
			timer.setRepeats(true);
			timer.start();

		});
	}

	/**
	 * */
	private static final long serialVersionUID = 1L;
	public GLCanvas canvas;

	private JPanel roiColorPanel;
	private JCheckBox chkShowMesh;
	/**
	 * Opacity curve control points, shared with VolumeOpacityCurveEditorDialog
	 * so the edited curve survives closing and reopening the dialog within
	 * this viewer window's lifetime.
	 */
	private final java.util.List<OpacityCurvePanel.ControlPoint> opacityCurvePoints = new java.util.ArrayList<>();

	// 仮想内視鏡: uスライダーのドラッグ開始時の値（ドラッグ確定時のコマンド構築用）
	private Float endoUDragStartValue = null;
	// 仮想内視鏡: 再生Timer等がプログラムからsliderEndoUを更新する際、ChangeListenerのコマンド化処理を抑制するフラグ
	private boolean suppressEndoUCommit = false;

	// ★ 追加: 現在選択（アクティブ）になっているROIグループ名を保持する変数
	private String selectedRoiGroupName = null;
	private java.util.Map<String, MeshData> rawMeshMap = new java.util.LinkedHashMap<>();

	// 追加：現在メッシュリストで選択（アクティブ）されているメッシュ名
	private String selectedMeshName = null;

	// 追加：メッシュ一覧を表示するためのUIパネル
	private JPanel meshListPanel;

	public Viewer3DMain() {
		setTitle("GRAPHY 3D Viewer");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(1000, 800);
		setLayout(new BorderLayout());

		// 1. OpenGLの設定データを作成
		GLData data = new GLData();
		data.majorVersion = 3;
		data.minorVersion = 3;// 3.2 or above

		// OpenGL >= 3.2
		data.profile = GLData.Profile.CORE;

		/*
		 * doubleBuffer = trueのとき、 GLCanvas.paintGL()内でswapBufferを有効にしておくこと
		 */
		data.doubleBuffer = true;

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

		JMenuItem openMeshItem = new JMenuItem("Open Mesh (OBJ/STL)...");
		openMeshItem.addActionListener(e -> {
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			fileChooser.setCurrentDirectory(new File("."));
			
			// 拡張子フィルタを設定しておくと親切です
			fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("3D Mesh Files (*.obj, *.stl)", "obj", "stl"));
			
			int result = fileChooser.showOpenDialog(this);

			if (result == JFileChooser.APPROVE_OPTION) {
				File selectedFile = fileChooser.getSelectedFile();
				String path = selectedFile.getAbsolutePath();
				
				// ==========================================================
				// ★ 修正: ファイル名から拡張子を取り除いて「メッシュ名」にする
				// 例: "tumor_mesh.stl" -> "tumor_mesh"
				// ==========================================================
				String fileName = selectedFile.getName();
				String meshName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

				// 別スレッドでメッシュを読み込み
				new Thread(() -> {
					MeshData mesh = MeshLoader.load(path);
					if (mesh != null) {
						
						// 1. エクスポート等のために、実寸(mm)のオリジナルデータをMapに登録
						MeshData rawClone = new MeshData(
								mesh.vertices.clone(), 
								mesh.normals.clone(), 
								mesh.indices.clone()
						);
						rawMeshMap.put(meshName, rawClone);
						selectedMeshName = meshName;

						// 2. 描画用に、ボリュームの描画空間（-0.5〜0.5）へ位置合わせを行う
						// (すでにDICOMボリュームがロードされている場合のみ実行)
						if (canvas.getVolumeData() != null) {
							AlignMesh.alignMeshToVolume(mesh, canvas.getVolumeData());
						}

						// 3. 描画スレッドでCanvasへの登録とUI更新を行う
						SwingUtilities.invokeLater(() -> {
							// 名前付きで複数管理としてGLCanvasに登録
							canvas.addOrUpdateMesh(meshName, mesh);
							
							canvas.setMeshVisible(true);
							if (chkShowMesh != null) chkShowMesh.setSelected(true);
							
							// ★ メッシュリストのUIを最新状態にリフレッシュ！
							refreshMeshListUI();
							
							JOptionPane.showMessageDialog(this, "Mesh '" + meshName + "' imported successfully!", "Success",
									JOptionPane.INFORMATION_MESSAGE);
						});
					} else {
						SwingUtilities.invokeLater(() -> {
							JOptionPane.showMessageDialog(this, "Failed to load mesh file.", "Error", JOptionPane.ERROR_MESSAGE);
						});
					}
				}).start();
			}
		});
		fileMenu.add(openMeshItem);

		JMenuItem exit = new JMenuItem("Exit");
		exit.addActionListener(e -> {
			dispose();
		});
		fileMenu.add(exit);

		menuBar.add(fileMenu);
		setJMenuBar(menuBar);

		// 4. ボタンパネルの作成（右側）
		JPanel controlPanel = new JPanel(new GridBagLayout());
		controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // パネル全体の余白
		// controlPanel.setPreferredSize(new Dimension(320, 800)); // 扱いやすい幅を確保

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

		// ---------------------------------------------------------
		// ★変更点：レンダリングモードをラジオボタンで択一化
		// ---------------------------------------------------------
		controlPanel.add(new JLabel("Rendering Mode", SwingConstants.LEFT), gbc);
		gbc.gridy++;

		JRadioButton radioVR = new JRadioButton("Volume Rendering (VR)");
		JRadioButton radioMIP = new JRadioButton("MIP");
		JRadioButton radioOrtho = new JRadioButton("Ortho Slices");
		JRadioButton radioCinematic = new JRadioButton("Cinematic Rendering");
		radioCinematic.setToolTipText(
				"<html>モンテカルロ・パストレーシングで光源の陰影・セルフシャドウを再現する表示モード。<br>"
				+ "通常のVR/MIPと違い照明計算を行うため、初めはノイズが見えるが静止していると自動的に収束する。<br>"
				+ "下のLight/Ambient/Shadow/Exposure系のスライダーで陰影の見え方を調整できる。</html>");

		// 元のコードの挙動に合わせて初期状態をMIPに設定
		radioMIP.setSelected(true);

		ButtonGroup renderGroup = new ButtonGroup();
		renderGroup.add(radioVR);
		renderGroup.add(radioMIP);
		renderGroup.add(radioOrtho);
		renderGroup.add(radioCinematic);

		// モード切替リスナー
		java.awt.event.ActionListener modeListener = e -> {
			new Thread(() -> {
				if (radioVR.isSelected()) {
					canvas.setShowVolume(true);
					canvas.setMIPMode(false);
					canvas.setOrthoMode(false);
					canvas.setCinematicMode(false);
				} else if (radioMIP.isSelected()) {
					canvas.setShowVolume(true);
					canvas.setMIPMode(true);
					canvas.setOrthoMode(false);
					canvas.setCinematicMode(false);
				} else if (radioOrtho.isSelected()) {
					canvas.setShowVolume(false); // Orthoモード時はボリューム非表示
					canvas.setMIPMode(false);
					canvas.setOrthoMode(true);
					canvas.setCinematicMode(false);
				} else if (radioCinematic.isSelected()) {
					canvas.setOrthoMode(false);
					canvas.setCinematicMode(true);
				}
			}).start();
		};

		radioVR.addActionListener(modeListener);
		radioMIP.addActionListener(modeListener);
		radioOrtho.addActionListener(modeListener);
		radioCinematic.addActionListener(modeListener);

		controlPanel.add(radioVR, gbc);
		gbc.gridy++;
		controlPanel.add(radioMIP, gbc);
		gbc.gridy++;
		controlPanel.add(radioOrtho, gbc);
		gbc.gridy++;
		controlPanel.add(radioCinematic, gbc);
		gbc.gridy++;

		// シネマティック・レンダリング専用コントロール（選択中のみ操作の意味がある）
		JLabel lblCinematicBackend = new JLabel("GPU: " + canvas.getCinematicBackendName());
		lblCinematicBackend.setToolTipText(
				"シネマティック・レンダリングを計算しているGPUバックエンド。CUDA対応GPUが検出できた場合はCUDA、できなければOpenGLにフォールバックする。");
		controlPanel.add(lblCinematicBackend, gbc);
		gbc.gridy++;

		controlPanel.add(new JLabel("Light Azimuth"), gbc);
		gbc.gridy++;
		JSlider sliderLightAzimuth = new JSlider(0, 360, 45);
		sliderLightAzimuth.setToolTipText("光源の水平方向の向き（0〜360度）。変えると、構造の側面に当たる陰影の出方が変わる。");
		controlPanel.add(sliderLightAzimuth, gbc);
		gbc.gridy++;

		controlPanel.add(new JLabel("Light Elevation"), gbc);
		gbc.gridy++;
		JSlider sliderLightElevation = new JSlider(-90, 90, 60);
		sliderLightElevation.setToolTipText(
				"光源の高さ方向の角度（-90=真下、0=真横、90=真上から照らす）。浅い角度にすると表面の凹凸がより強調された陰影になる。");
		controlPanel.add(sliderLightElevation, gbc);
		gbc.gridy++;

		controlPanel.add(new JLabel("Light Intensity"), gbc);
		gbc.gridy++;
		JSlider sliderLightIntensity = new JSlider(0, 400, 150);
		sliderLightIntensity.setToolTipText(
				"主光源の明るさ（0.0〜4.0）。上げるほど光が当たる面と影の面のコントラストが強くなる。2.0〜3.0程度にすると陰影が分かりやすい。");
		controlPanel.add(sliderLightIntensity, gbc);
		gbc.gridy++;

		controlPanel.add(new JLabel("Ambient Intensity"), gbc);
		gbc.gridy++;
		JSlider sliderAmbient = new JSlider(0, 100, 25);
		sliderAmbient.setToolTipText(
				"影の部分にも均等に加わる環境光の強さ（0.0〜1.0）。下げるほど影が濃くなり立体感が増す。0.1前後まで下げると陰影がはっきり見える。");
		controlPanel.add(sliderAmbient, gbc);
		gbc.gridy++;

		controlPanel.add(new JLabel("Shadow Softness"), gbc);
		gbc.gridy++;
		JSlider sliderShadowSoftness = new JSlider(0, 30, 8);
		sliderShadowSoftness.setToolTipText(
				"影の輪郭のやわらかさ（光源を点ではなく面として扱う角度、0〜30度）。大きいほど影がぼやけるが、ノイズが収束するまでのフレーム数も増える。0なら硬い輪郭の影になる。");
		controlPanel.add(sliderShadowSoftness, gbc);
		gbc.gridy++;

		controlPanel.add(new JLabel("Exposure"), gbc);
		gbc.gridy++;
		JSlider sliderExposure = new JSlider(10, 400, 150);
		sliderExposure.setToolTipText(
				"蓄積済みの画像に後から掛ける明るさの補正（0.1〜4.0）。蓄積をリセットせず即座に反映されるので、明るさだけ素早く調整したいときに使う。");
		controlPanel.add(sliderExposure, gbc);
		gbc.gridy++;

		controlPanel.add(new JLabel("Samples / Frame"), gbc);
		gbc.gridy++;
		JSlider sliderSamples = new JSlider(1, 16, 1);
		sliderSamples.setMajorTickSpacing(5);
		sliderSamples.setMinorTickSpacing(1);
		sliderSamples.setPaintTicks(true);
		sliderSamples.setPaintLabels(true);
		sliderSamples.setToolTipText(
				"<html>1フレームあたりに計算してから平均するパストレーシングのサンプル数（1〜16）。<br>"
				+ "静止中は複数フレームにわたって自動的にノイズが収束していくため、通常は1で十分。<br>"
				+ "カメラを動かしている最中のちらつきを減らしたい場合だけ2〜4程度に上げる。<br>"
				+ "値を上げるほど1フレームの計算量が増え、フレームレートが大きく低下する点に注意。</html>");
		controlPanel.add(sliderSamples, gbc);
		gbc.gridy++;

		Runnable applyCinematicParams = () -> {
			com.vis.core.view.D3.ui.cinematic.CinematicParams params = canvas.getCinematicParams();
			params.lightAzimuth = (float) Math.toRadians(sliderLightAzimuth.getValue());
			params.lightElevation = (float) Math.toRadians(sliderLightElevation.getValue());
			params.lightIntensity = sliderLightIntensity.getValue() / 100.0f;
			params.ambientIntensity = sliderAmbient.getValue() / 100.0f;
			params.lightAngularRadius = (float) Math.toRadians(sliderShadowSoftness.getValue());
			params.exposure = sliderExposure.getValue() / 100.0f;
			params.samplesPerFrame = sliderSamples.getValue();
			canvas.invalidateCinematicAccumulation();
		};
		sliderLightAzimuth.addChangeListener(e -> applyCinematicParams.run());
		sliderLightElevation.addChangeListener(e -> applyCinematicParams.run());
		sliderLightIntensity.addChangeListener(e -> applyCinematicParams.run());
		sliderAmbient.addChangeListener(e -> applyCinematicParams.run());
		sliderShadowSoftness.addChangeListener(e -> applyCinematicParams.run());
		sliderExposure.addChangeListener(e -> applyCinematicParams.run());
		sliderSamples.addChangeListener(e -> applyCinematicParams.run());
		// ---------------------------------------------------------

		// 区切り線 1
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// ---------------------------------------------------------
		// Color Map (LUT) - applies to the volume texture, shared by VR/MIP/
		// Ortho since they all sample the same 1D LUT texture in the shader.
		// ---------------------------------------------------------
		controlPanel.add(new JLabel("Color Map (LUT)", SwingConstants.LEFT), gbc);
		gbc.gridy++;

		String[] lutNames = com.vis.configuration.Resources.getLutNames();
		javax.swing.JComboBox<String> comboLut = new javax.swing.JComboBox<>(lutNames);
		comboLut.setSelectedItem("Grayscale");
		comboLut.addActionListener(e -> {
			String selectedLutName = (String) comboLut.getSelectedItem();
			new Thread(() -> {
				if ("Grayscale".equals(selectedLutName)) {
					canvas.setLutType(0);
				} else {
					ij.process.LUT selectedLut = com.vis.configuration.Resources.loadLUT(selectedLutName);
					if (selectedLut != null) {
						canvas.applyLut(selectedLut);
					}
				}
			}).start();
		});
		controlPanel.add(comboLut, gbc);
		gbc.gridy++;

		// Opacity curve editor: edits the alpha part of the LUT independently
		// of the color map above, against a histogram of the loaded volume.
		JButton btnEditOpacity = new JButton("Edit Opacity Curve...");
		btnEditOpacity.addActionListener(e -> {
			VolumeOpacityCurveEditorDialog dlg = new VolumeOpacityCurveEditorDialog(this, canvas, opacityCurvePoints);
			dlg.setVisible(true);
		});
		controlPanel.add(btnEditOpacity, gbc);
		gbc.gridy++;

		// General-purpose 3D centerline analysis: skeleton extraction from a mask ROI,
		// branch/path pruning, vascular CPR, and Straighten (2D or 3D).
		JButton btnCenterline = new JButton("Centerline Analysis...");
		btnCenterline.addActionListener(e -> CenterlineAnalysisDialog.showDialog(canvas, this));
		controlPanel.add(btnCenterline, gbc);
		gbc.gridy++;

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

		// チェックボックス：Show ROI (※Show Volumeは上のラジオボタンに統合したため削除)
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

		roiColorPanel = new JPanel();
		roiColorPanel.setLayout(new BoxLayout(roiColorPanel, BoxLayout.Y_AXIS));
		JScrollPane roiScrollPane = new JScrollPane(roiColorPanel);
		roiScrollPane.setMinimumSize(new Dimension(200, 150));
		roiScrollPane.setPreferredSize(new Dimension(300, 200)); // ←★この行を追加
		controlPanel.add(roiScrollPane, gbc);
		// ★追加: キャンバスから「ROI情報が更新されたよ」という通知を受け取ってUIを作る
		canvas.setOnRoiLoadedCallback(() -> refreshRoiColorUI());

		// 区切り線
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// メッシュ表示切り替えチェックボックス
		chkShowMesh = new JCheckBox("Show Mesh", true);
		chkShowMesh.addActionListener(e -> {
			if (canvas != null) {
				canvas.setMeshVisible(chkShowMesh.isSelected());
			}
		});
		controlPanel.add(chkShowMesh, gbc);
		gbc.gridy++;

		// 区切り線
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// 仮想内視鏡パスの編集モード切り替え／内視鏡視点切り替え（相互排他のため先に両方を宣言する）
		JCheckBox chkEndoPathEdit = new JCheckBox("Edit Endoscopy Path", false);
		JCheckBox chkEndoscopyView = new JCheckBox("Endoscopy View (Fly-Through)", false);

		chkEndoPathEdit.setToolTipText(
				"Ctrl+左クリック: 点を追加 / 左クリック+ドラッグ: 点を移動 / Delete: 選択中の点を削除");
		chkEndoPathEdit.addActionListener(e -> {
			if (canvas == null) return;
			boolean enabled = chkEndoPathEdit.isSelected();
			canvas.setEndoPathEditMode(enabled);
			if (enabled) {
				chkEndoscopyView.setSelected(false);
			}
			canvas.repaint();
		});
		controlPanel.add(chkEndoPathEdit, gbc);
		gbc.gridy++;

		chkEndoscopyView.setToolTipText("内視鏡カメラ視点に切り替える（パスに2点以上必要）");
		chkEndoscopyView.addActionListener(e -> {
			if (canvas == null) return;
			boolean enabled = chkEndoscopyView.isSelected();
			canvas.setEndoscopyMode(enabled);
			if (enabled) {
				chkEndoPathEdit.setSelected(false);
			}
			canvas.repaint();
		});
		controlPanel.add(chkEndoscopyView, gbc);
		gbc.gridy++;

		// 仮想内視鏡: 再生位置(u)のスライダー（既存sliderX/Y/Zと同じ0-100の規約）
		controlPanel.add(new JLabel("Endoscopy Position"), gbc);
		gbc.gridy++;

		JSlider sliderEndoU = new JSlider(0, 100, 0);
		sliderEndoU.addChangeListener(e -> {
			if (canvas == null || suppressEndoUCommit) return; // ★再生中などプログラムからの更新は無視
			EndoCamera cam = canvas.getEndoCamera();
			float u = sliderEndoU.getValue() / 100.0f;
			if (sliderEndoU.getValueIsAdjusting()) {
				if (endoUDragStartValue == null) {
					endoUDragStartValue = cam.getU(); // ドラッグ開始時の値を1度だけ記録
				}
				cam.setU(u); // ライブプレビューのみ（コマンドは発行しない）
				cam.resetLook(); // パスの移動中は常に進行方向を向かせる
				canvas.repaint();
			} else {
				float startU = (endoUDragStartValue != null) ? endoUDragStartValue : cam.getU();
				endoUDragStartValue = null;
				if (Math.abs(u - startU) > 1e-6f) {
					cam.setU(startU); // 一旦ドラッグ開始時の値に戻してからコマンドを構築する
					canvas.getUndoManager().addCommand(new EndoCommands.SetCameraUCommand(cam, u));
				}
				cam.resetLook();
				canvas.repaint();
			}
		});
		controlPanel.add(sliderEndoU, gbc);
		gbc.gridy++;

		// 仮想内視鏡: ジャンプボタン（先頭/前の点/次の点/末尾）
		JPanel endoJumpRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
		JButton btnJumpStart = new JButton("|< Start");
		JButton btnPrevPoint = new JButton("< Point");
		JButton btnNextPoint = new JButton("Point >");
		JButton btnJumpEnd = new JButton("End >|");

		btnJumpStart.addActionListener(e -> jumpEndoCamera(sliderEndoU, 0f));
		btnJumpEnd.addActionListener(e -> jumpEndoCamera(sliderEndoU, 1f));
		btnPrevPoint.addActionListener(e -> {
			EndoPath3D path = canvas.getEndoPath();
			if (path.size() >= 2) {
				int idx = path.findPreviousPointIndex(canvas.getEndoCamera().getU());
				jumpEndoCamera(sliderEndoU, path.getNormalizedDistanceAtPoint(idx));
			}
		});
		btnNextPoint.addActionListener(e -> {
			EndoPath3D path = canvas.getEndoPath();
			if (path.size() >= 2) {
				int idx = path.findNextPointIndex(canvas.getEndoCamera().getU());
				jumpEndoCamera(sliderEndoU, path.getNormalizedDistanceAtPoint(idx));
			}
		});
		endoJumpRow.add(btnJumpStart);
		endoJumpRow.add(btnPrevPoint);
		endoJumpRow.add(btnNextPoint);
		endoJumpRow.add(btnJumpEnd);
		controlPanel.add(endoJumpRow, gbc);
		gbc.gridy++;

		// 仮想内視鏡: Undo/Redoボタン（カット・パス編集と共通のundoManagerに委譲）
		JPanel endoUndoRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
		JButton btnEndoUndo = new JButton("Undo");
		JButton btnEndoRedo = new JButton("Redo");
		btnEndoUndo.addActionListener(e -> {
			canvas.getUndoManager().undo();
			sliderEndoU.setValue(Math.round(canvas.getEndoCamera().getU() * 100));
			canvas.repaint();
		});
		btnEndoRedo.addActionListener(e -> {
			canvas.getUndoManager().redo();
			sliderEndoU.setValue(Math.round(canvas.getEndoCamera().getU() * 100));
			canvas.repaint();
		});
		endoUndoRow.add(btnEndoUndo);
		endoUndoRow.add(btnEndoRedo);
		controlPanel.add(endoUndoRow, gbc);
		gbc.gridy++;

		// 仮想内視鏡: 再生速度
		controlPanel.add(new JLabel("Playback Speed"), gbc);
		gbc.gridy++;
		JSlider sliderEndoSpeed = new JSlider(10, 300, 100); // 10=0.1x, 100=1.0x, 300=3.0x
		sliderEndoSpeed.setToolTipText("1.0x = パス全体を約10秒で通過");
		controlPanel.add(sliderEndoSpeed, gbc);
		gbc.gridy++;

		// 仮想内視鏡: 再生/一時停止/停止
		final float baseSpeedPerSecond = 0.1f; // 1.0x時、全体の通過に約10秒

		JPanel endoPlaybackRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
		JButton btnEndoPlay = new JButton("Play");
		JButton btnEndoPause = new JButton("Pause");
		JButton btnEndoStop = new JButton("Stop");

		javax.swing.Timer endoPlaybackTimer = new javax.swing.Timer(16, evt -> {
			if (canvas == null) return;
			EndoPath3D path = canvas.getEndoPath();
			EndoCamera cam = canvas.getEndoCamera();
			if (path.size() < 2) return;

			float speedMultiplier = sliderEndoSpeed.getValue() / 100.0f;
			float dt = 0.016f; // Timerの間隔(16ms)を固定ステップとして使う（既存のレンダリングTimerと同じ簡易方式）
			float newU = cam.getU() + dt * baseSpeedPerSecond * speedMultiplier;

			boolean reachedEnd = newU >= 1f;
			if (reachedEnd) newU = 1f;
			cam.setU(newU);
			cam.resetLook(); // 再生中は常にパスの進行方向を向かせる

			suppressEndoUCommit = true;
			sliderEndoU.setValue(Math.round(newU * 100));
			suppressEndoUCommit = false;
			canvas.repaint();

			if (reachedEnd) {
				((javax.swing.Timer) evt.getSource()).stop(); // 終端到達で自動停止（ループ再生は将来項目）
				setComponentsEnabled(true, chkEndoPathEdit, chkEndoscopyView, sliderEndoU,
						btnJumpStart, btnPrevPoint, btnNextPoint, btnJumpEnd);
			}
		});
		endoPlaybackTimer.setRepeats(true);

		btnEndoPlay.addActionListener(e -> {
			if (canvas == null || canvas.getEndoPath().size() < 2) return;

			// 編集操作との排他: 再生中はパス編集を切り、内視鏡視点に入る
			chkEndoPathEdit.setSelected(false);
			canvas.setEndoPathEditMode(false);
			chkEndoscopyView.setSelected(true);
			canvas.setEndoscopyMode(true);

			setComponentsEnabled(false, chkEndoPathEdit, chkEndoscopyView, sliderEndoU,
					btnJumpStart, btnPrevPoint, btnNextPoint, btnJumpEnd);
			endoPlaybackTimer.start();
		});

		btnEndoPause.addActionListener(e -> {
			endoPlaybackTimer.stop();
			setComponentsEnabled(true, chkEndoPathEdit, chkEndoscopyView, sliderEndoU,
					btnJumpStart, btnPrevPoint, btnNextPoint, btnJumpEnd);
		});

		btnEndoStop.addActionListener(e -> {
			endoPlaybackTimer.stop();
			setComponentsEnabled(true, chkEndoPathEdit, chkEndoscopyView, sliderEndoU,
					btnJumpStart, btnPrevPoint, btnNextPoint, btnJumpEnd);
			if (canvas != null) {
				canvas.getEndoCamera().setU(0f);
				canvas.getEndoCamera().resetLook();
				suppressEndoUCommit = true;
				sliderEndoU.setValue(0);
				suppressEndoUCommit = false;
				canvas.repaint();
			}
		});

		endoPlaybackRow.add(btnEndoPlay);
		endoPlaybackRow.add(btnEndoPause);
		endoPlaybackRow.add(btnEndoStop);
		controlPanel.add(endoPlaybackRow, gbc);
		gbc.gridy++;

		// ==========================================================
		// Marching Cubes による選択グループのメッシュ生成ボタン（フェーズ1対応）
		// ==========================================================
		JButton btnGenerateMeshRoi = new JButton("Generate Mesh from Roi");
		btnGenerateMeshRoi.setToolTipText("Run Marching Cubes on current selected roi");
		btnGenerateMeshRoi.addActionListener(e -> {
			if (canvas.getVolumeData() == null)
				return;

			// ★ 修正: ポップアップダイアログを廃止し、ラジオボタンで選択中のグループを利用する
			if (selectedRoiGroupName == null) {
				JOptionPane.showMessageDialog(this, "Please select an ROI group from the list first.", "Info",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			// 確定したグループ名を変数に入れる
			String selectedGroup = selectedRoiGroupName;

			// UIの無効化
			btnGenerateMeshRoi.setEnabled(false);
			btnGenerateMeshRoi.setText("Generating 3D Mesh...");

			// 重い処理なので別スレッドで実行
			new Thread(() -> {
				try {
					// 1. GLCanvasから幾何情報とVolumeData、対象ROIを取得
					VolumeData currentVol = canvas.getVolumeData();
					java.util.List<FreeFormRoi3D> targetRois = canvas.getRoisByGroup(selectedGroup);

					// 2.物理座標ベースでメッシュをクリーンに生成
					Log.logger.info("Converting group [" + selectedGroup + "] to standard 3D mesh...");
					MeshData generatedMesh = convertRoiGroupToMesh(targetRois, currentVol);

					if (generatedMesh != null) {
						MeshData rawClone = new MeshData(
								generatedMesh.vertices.clone(), 
								generatedMesh.normals.clone(), 
								generatedMesh.indices.clone()
						);
						rawMeshMap.put(selectedGroup, rawClone);
						selectedMeshName = selectedGroup;

						// ボリュームの描画スケール(-0.5〜0.5空間)と位置を完全に同期
						AlignMesh.alignMeshToVolume(generatedMesh, canvas.getVolumeData());

						// 生成完了したら描画スレッドで画面に反映
						SwingUtilities.invokeLater(() -> {
							// 名前付きで複数管理のMapに登録
							canvas.addOrUpdateMesh(selectedGroup, generatedMesh); 
							
							// ==========================================================
							// ★ 追加: 元になったROIの色をメッシュの初期色として自動同期する
							// ==========================================================
							java.util.List<String> roiNames = canvas.getRoiGroupNames();
							java.util.List<java.awt.Color> roiColors = canvas.getRoiColors();
							int roiIndex = roiNames.indexOf(selectedGroup);
							if (roiIndex != -1) {
								canvas.setMeshColor(selectedGroup, roiColors.get(roiIndex));
							}
							
							canvas.setMeshVisible(true);
							chkShowMesh.setSelected(true);
							btnGenerateMeshRoi.setText("Generate Mesh from Roi");
							btnGenerateMeshRoi.setEnabled(true);
							
							refreshMeshListUI();
							
							JOptionPane.showMessageDialog(this, "3D Mesh generated and added to list!", "Success",
									JOptionPane.INFORMATION_MESSAGE);
						});
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					SwingUtilities.invokeLater(() -> {
						btnGenerateMeshRoi.setText("Generate Mesh from Roi");
						btnGenerateMeshRoi.setEnabled(true);
					});
				}
			}).start();
		});
		controlPanel.add(btnGenerateMeshRoi, gbc);
		gbc.gridy++;

		// ==========================================================
		//  STLエクスポートボタン（選択メッシュ対象）
		// ==========================================================
		JButton btnExportStl = new JButton("Export Mesh to STL...");
		btnExportStl.setToolTipText("Save the generated 3D mesh as a file for 3D printing");
		btnExportStl.addActionListener(e -> {
			// ★ 修正: 選択されているアクティブなメッシュがあるかチェック
			if (selectedMeshName == null || !rawMeshMap.containsKey(selectedMeshName)) {
				JOptionPane.showMessageDialog(this, "Please select a 3D mesh from the list to export.", "Warning",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			// ファイル保存ダイアログの表示
			javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
			fileChooser.setDialogTitle("Export Selected 3D Mesh as Binary STL");
			fileChooser.setSelectedFile(
					new File("exported_" + selectedMeshName.replace(":", "").replace(" ", "_") + ".stl"));

			fileChooser.setFileFilter(
					new javax.swing.filechooser.FileNameExtensionFilter("Stereolithography (*.stl)", "stl"));

			int userSelection = fileChooser.showSaveDialog(this);
			if (userSelection == javax.swing.JFileChooser.APPROVE_OPTION) {
				File fileToSave = fileChooser.getSelectedFile();

				if (!fileToSave.getName().toLowerCase().endsWith(".stl")) {
					fileToSave = new File(fileToSave.getAbsolutePath() + ".stl");
				}

				try {
					// ★ 修正: リストで選択されている「実寸(mm)のオリジナルメッシュ」を取得して投入！
					MeshData activeRawMesh = rawMeshMap.get(selectedMeshName);
					MeshExporter.exportToBinarySTL(fileToSave, activeRawMesh);

					JOptionPane.showMessageDialog(this, "Mesh successfully exported to:\n" + fileToSave.getName(),
							"Export Success", JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(this, "Failed to export STL:\n" + ex.getMessage(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});

		controlPanel.add(btnExportStl, gbc);
		gbc.gridy++;

		// メッシュの透明度スライダー (オプション)
		JSlider sliderMeshAlpha = new JSlider(0, 100, 100);
		sliderMeshAlpha.setToolTipText("Adjust Mesh Opacity");
		sliderMeshAlpha.addChangeListener(e -> {
			if (canvas != null) {
				float alpha = sliderMeshAlpha.getValue() / 100.0f;
				canvas.setMeshAlpha(alpha);
			}
		});
		controlPanel.add(new JLabel("Mesh Opacity"), gbc);
		gbc.gridy++;
		controlPanel.add(sliderMeshAlpha, gbc);
		gbc.gridy++;
		
		// 区切り線
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// ==========================================================
		// ★ 追加: 3D メッシュリスト（レイヤー管理パネル）の配置
		// ==========================================================
		controlPanel.add(new JLabel("Generated 3D Meshes"), gbc);
		gbc.gridy++;

		meshListPanel = new JPanel();
		meshListPanel.setLayout(new BoxLayout(meshListPanel, BoxLayout.Y_AXIS));
		JScrollPane meshScrollPane = new JScrollPane(meshListPanel);
		meshScrollPane.setMinimumSize(new Dimension(200, 100));
		meshScrollPane.setPreferredSize(new Dimension(300, 120)); // 程よい高さを確保
		controlPanel.add(meshScrollPane, gbc);
		gbc.gridy++;
		

		// ★ここが最大のポイント：JScrollPaneにのみ残りの縦幅を全割り当てする
		gbc.weighty = 1.0; // 縦方向の拡張ウェイトを設定
		gbc.fill = GridBagConstraints.BOTH; // 縦横両方に広げる設定に変更
		gbc.insets = new Insets(4, 4, 0, 4); // 最下部の余白調整

		// ==========================================================
		// ★ここから修正: controlPanelを直接addせず、JScrollPaneでラップする
		// ==========================================================
		JScrollPane mainControlScroll = new JScrollPane(controlPanel);
		mainControlScroll.setPreferredSize(new Dimension(340, 0)); // 幅を指定、高さはBorderLayoutにお任せ
		mainControlScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // 横スクロールバーは出さない
		mainControlScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED); // 縦は必要に応じて出す
		mainControlScroll.setBorder(BorderFactory.createEmptyBorder()); // 余分な枠線を消してスッキリさせる
		mainControlScroll.getVerticalScrollBar().setUnitIncrement(16); // ★重要: マウスホイールでのスクロール速度を快適にする

		add(mainControlScroll, BorderLayout.EAST);
	}

	// ==========================================================
	// 【新方針】物理座標ベースで標準VolumeにROIを焼き付けてメッシュ化する
	// ==========================================================
	private MeshData convertRoiGroupToMesh(java.util.List<FreeFormRoi3D> rois, VolumeData standardVol) {

		if (rois == null || rois.isEmpty() || standardVol == null)
			return null;

		int w = standardVol.width;
		int h = standardVol.height;
		int d = standardVol.depth;

		double[] startIpp = standardVol.startIpp;
		double[] iop = standardVol.iop;
		double[] stepZ = standardVol.stepZ;

		// 1. 標準空間と全く同じサイズの空のマスク配列を用意
		byte[] bakedVolumeBytes = new byte[w * h * d];

		Log.logger.info("Baking ROIs into standard volume space via physical coordinates...");

		// 2. 標準空間の全ボクセルを走査し、物理座標(mm)ベースでROIを焼き付ける
		int index = 0;
		for (int z = 0; z < d; z++) {
			double zOffX = z * stepZ[0];
			double zOffY = z * stepZ[1];
			double zOffZ = z * stepZ[2];

			for (int y = 0; y < h; y++) {
				double yOffX = iop[3] * (y * standardVol.pixelSpacingY);
				double yOffY = iop[4] * (y * standardVol.pixelSpacingY);
				double yOffZ = iop[5] * (y * standardVol.pixelSpacingY);

				for (int x = 0; x < w; x++) {
					double xOffX = iop[0] * (x * standardVol.pixelSpacingX);
					double xOffY = iop[1] * (x * standardVol.pixelSpacingX);
					double xOffZ = iop[2] * (x * standardVol.pixelSpacingX);

					// 標準Volumeにおける現在のボクセルの物理座標 (mm)
					double px = startIpp[0] + xOffX + yOffX + zOffX;
					double py = startIpp[1] + xOffY + yOffY + zOffY;
					double pz = startIpp[2] + xOffZ + yOffZ + zOffZ;

					// 対象グループのいずれかのROIにこの物理座標が含まれているか判定
					boolean isInside = false;
					for (FreeFormRoi3D roi : rois) {
						if (roi.containsPhysicalPoint(px, py, pz)) {
							isInside = true;
							break;
						}
					}

					// 含まれていればボクセルをON(255)にする
					if (isInside) {
						bakedVolumeBytes[index] = (byte) 255;
					}
					index++;
				}
			}
		}

		// 3. MarchingCubesに流し込むためのVolumeData（焼き付け版ダミー）を構築
		VolumeData meshVolume = new VolumeData(w, h, d, bakedVolumeBytes);
		meshVolume.pixelSpacingX = standardVol.pixelSpacingX;
		meshVolume.pixelSpacingY = standardVol.pixelSpacingY;
		meshVolume.sliceThickness = standardVol.sliceThickness;
		meshVolume.minVal = 0;
		meshVolume.maxVal = 255;

		// 4. 標準空間に完全に準拠したメッシュを生成！
		return MarchingCubes.generateMesh(meshVolume, 127.5f);
	}

	private void updateSlices(GLCanvas canvas, JSlider sx, JSlider sy, JSlider sz) {
		float x = sx.getValue() / 100.0f;
		float y = sy.getValue() / 100.0f;
		float z = sz.getValue() / 100.0f;
		canvas.setSlicePos(x, y, z);
	}

	// 複数のコンポーネントのenabled状態を一括で切り替える（再生中の編集系UIロックに使う）
	private void setComponentsEnabled(boolean enabled, java.awt.Component... components) {
		for (java.awt.Component c : components) {
			c.setEnabled(enabled);
		}
	}

	// 仮想内視鏡カメラをnewU(正規化距離)へジャンプさせ、Undo可能なコマンドとして発行する
	private void jumpEndoCamera(JSlider sliderEndoU, float newU) {
		EndoCamera cam = canvas.getEndoCamera();
		float oldU = cam.getU();
		if (Math.abs(newU - oldU) > 1e-6f) {
			canvas.getUndoManager().addCommand(new EndoCommands.SetCameraUCommand(cam, newU));
		}
		cam.resetLook(); // ポイント間ジャンプでは常にパスの進行方向を向かせる
		// この変更でChangeEventが飛ぶが、cam.getU()は既にnewUなのでコマンドは二重発行されない
		sliderEndoU.setValue(Math.round(newU * 100));
		canvas.repaint();
	}

	// ==========================================
	// ROIリストパネルの構築 (フェーズ1: ラジオボタン対応版)
	// ==========================================
	private void refreshRoiColorUI() {
		// まずパネルの中身を一度すべてクリアする
		roiColorPanel.removeAll();

		// Canvasから読み込み済みのグループ名と色のリストを取得
		java.util.List<String> names = canvas.getRoiGroupNames();
		java.util.List<java.awt.Color> colors = canvas.getRoiColors();

		// ROIが1つもない場合のフォールバック表示
		if (names == null || names.isEmpty()) {
			roiColorPanel.add(new JLabel("No ROIs loaded."));
			selectedRoiGroupName = null; // リセット
		} else {
			// ラジオボタンを1つだけ選択できるようにするためのボタングループ
			ButtonGroup bg = new ButtonGroup();

			// 選択状態が空、または既に存在しないグループを指している場合は、先頭をデフォルト選択にする
			if (selectedRoiGroupName == null || !names.contains(selectedRoiGroupName)) {
				selectedRoiGroupName = names.get(0);
			}

			// 読み込まれたROIグループの数だけ行（JPanel）を作る
			for (int i = 0; i < names.size(); i++) {
				final int index = i;
				String name = names.get(i);
				java.awt.Color c = colors.get(i);

				// 左詰めの行パネルを作成
				JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));

				// 1. アクティブ選択用のラジオボタン
				JRadioButton radioBtn = new JRadioButton();
				bg.add(radioBtn);
				if (name.equals(selectedRoiGroupName)) {
					radioBtn.setSelected(true);
				}
				radioBtn.addActionListener(e -> {
					selectedRoiGroupName = name; // クリックで選択中グループを更新
				});

				// 2. 色を表示・変更するためのカラーボタン
				JButton colorBtn = new JButton();
				colorBtn.setPreferredSize(new java.awt.Dimension(20, 20));
				colorBtn.setBackground(c);
				colorBtn.setOpaque(true);
				colorBtn.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK));

				// ボタンを押したら標準のカラーピッカーを開く
				colorBtn.addActionListener(e -> {
					java.awt.Color newColor = javax.swing.JColorChooser.showDialog(this, "Select Color for " + name,
							colorBtn.getBackground());
					if (newColor != null) {
						colorBtn.setBackground(newColor);
						canvas.setRoiGroupColor(index, newColor); // Canvasの色情報を更新し、再描画
					}
				});

				// 3. 行パネルにコンポーネントを配置
				row.add(radioBtn);
				row.add(colorBtn);
				row.add(new JLabel(name));

				// 出来上がった行をリストパネルに追加
				roiColorPanel.add(row);
			}
		}

		// Swingのレイアウト再計算と再描画を明示的に呼び出す
		roiColorPanel.revalidate();
		roiColorPanel.repaint();
	}
	
	// ==========================================================
	// メッシュリストパネルの構築 (フェーズ2: 個別カラーボタン追加版)
	// ==========================================================
	private void refreshMeshListUI() {
		meshListPanel.removeAll();

		if (rawMeshMap.isEmpty()) {
			meshListPanel.add(new JLabel("No meshes generated yet."));
			selectedMeshName = null;
		} else {
			ButtonGroup bg = new ButtonGroup();

			if (selectedMeshName == null || !rawMeshMap.containsKey(selectedMeshName)) {
				String lastKey = null;
				for (String key : rawMeshMap.keySet()) {
					lastKey = key;
				}
				selectedMeshName = lastKey;
			}

			// 保持しているすべてのメッシュを行として描画
			for (String meshName : rawMeshMap.keySet()) {
				JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));

				// 1. アクティブ選択用のラジオボタン
				JRadioButton radioBtn = new JRadioButton();
				bg.add(radioBtn);
				if (meshName.equals(selectedMeshName)) {
					radioBtn.setSelected(true);
				}
				radioBtn.addActionListener(e -> {
					selectedMeshName = meshName;
					canvas.setActiveMeshName(meshName);
				});

				// ==========================================================
				// ★ 追加: 2. メッシュ個別のカラーピッカー用ボタン
				// ==========================================================
				JButton colorBtn = new JButton();
				colorBtn.setPreferredSize(new java.awt.Dimension(20, 20));

				// Canvasから現在のこのメッシュの色を取得して背景色にする
				java.awt.Color currentMColor = canvas.getMeshColor(meshName);
				colorBtn.setBackground(currentMColor);
				colorBtn.setOpaque(true);
				colorBtn.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK));

				// カラーボタンクリックでダイアログを展開
				colorBtn.addActionListener(e -> {
					java.awt.Color newColor = javax.swing.JColorChooser.showDialog(this,
							"Select Color for Mesh [" + meshName + "]", colorBtn.getBackground());
					if (newColor != null) {
						colorBtn.setBackground(newColor);
						canvas.setMeshColor(meshName, newColor); // Canvas側の個別色を更新
					}
				});

				// 3. メッシュ名のラベル
				JLabel nameLabel = new JLabel(meshName);

				// レイアウトへ順次配置
				row.add(radioBtn);
				row.add(colorBtn); // ★ラジオボタンの隣に色ボタンを配置
				row.add(nameLabel);
				meshListPanel.add(row);
			}
		}
		meshListPanel.revalidate();
		meshListPanel.repaint();
	}
}