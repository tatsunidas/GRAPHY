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
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D3.roi.FreeFormRoi3D;

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
				}else {
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

		/*
		 * doubleBuffer = trueのとき、
		 * GLCanvas.paintGL()内でswapBufferを有効にしておくこと
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
		    int result = fileChooser.showOpenDialog(this);

		    if (result == JFileChooser.APPROVE_OPTION) {
		        String path = fileChooser.getSelectedFile().getAbsolutePath();
		        
		        // 別スレッドでメッシュを読み込み
		        new Thread(() -> {
		            MeshData mesh = MeshLoader.load(path);
		            if (mesh != null) {
		                // Canvasにデータを渡す
		                canvas.setMeshData(mesh);
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
		//controlPanel.setPreferredSize(new Dimension(320, 800)); // 扱いやすい幅を確保

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

		// 元のコードの挙動に合わせて初期状態をMIPに設定
		radioMIP.setSelected(true);

		ButtonGroup renderGroup = new ButtonGroup();
		renderGroup.add(radioVR);
		renderGroup.add(radioMIP);
		renderGroup.add(radioOrtho);

		// モード切替リスナー
		java.awt.event.ActionListener modeListener = e -> {
			new Thread(() -> {
				if (radioVR.isSelected()) {
					canvas.setShowVolume(true);
					canvas.setMIPMode(false);
					canvas.setOrthoMode(false);
				} else if (radioMIP.isSelected()) {
					canvas.setShowVolume(true);
					canvas.setMIPMode(true);
					canvas.setOrthoMode(false);
				} else if (radioOrtho.isSelected()) {
					canvas.setShowVolume(false); // Orthoモード時はボリューム非表示
					canvas.setMIPMode(false);
					canvas.setOrthoMode(true);
				}
			}).start();
		};

		radioVR.addActionListener(modeListener);
		radioMIP.addActionListener(modeListener);
		radioOrtho.addActionListener(modeListener);

		controlPanel.add(radioVR, gbc);
		gbc.gridy++;
		controlPanel.add(radioMIP, gbc);
		gbc.gridy++;
		controlPanel.add(radioOrtho, gbc);
		gbc.gridy++;
		// ---------------------------------------------------------

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
		
		// 区切り線
		controlPanel.add(new javax.swing.JSeparator(), gbc);
		gbc.gridy++;

		// メッシュ表示切り替えチェックボックス
		JCheckBox chkShowMesh = new JCheckBox("Show Mesh", true);
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

		// ==========================================================
		// ★追加: Marching Cubes によるメッシュ生成ボタン
		// ==========================================================
		JButton btnGenerateMesh = new JButton("Generate Mesh from Mask");
		btnGenerateMesh.setToolTipText("Run Marching Cubes on current volume");
		btnGenerateMesh.addActionListener(e -> {
			if (canvas.getVolumeData() == null)
				return;

			// UIが固まらないようにボタンを一時無効化
			btnGenerateMesh.setEnabled(false);
			btnGenerateMesh.setText("Generating...");
			
			// 重い処理なので別スレッドで実行
			new Thread(() -> {
				try {
					VolumeData vol = canvas.getVolumeData();

					// 閾値（アイソバリュー）をデータの最小・最大の中間に設定
					// マスクデータ(0と255)なら 127.5 になります
					float isoLevel = (vol.minVal + vol.maxVal) / 2.0f;

					// 先ほど作った最強のアルゴリズムを実行！
					MeshData generatedMesh = MarchingCubes.generateMesh(vol, isoLevel);

					if (generatedMesh != null) {
						// ★重要: ボリュームの描画スケール(-0.5〜0.5空間)と完全に位置を一致させる
						alignMeshToVolume(generatedMesh, vol);

						// 生成完了したら画面に反映してボタンを戻す
						SwingUtilities.invokeLater(() -> {
							canvas.setMeshData(generatedMesh);
							canvas.setMeshVisible(true);
							chkShowMesh.setSelected(true); // チェックボックスもONにする
							btnGenerateMesh.setText("Generate Mesh from Mask");
							btnGenerateMesh.setEnabled(true);
						});
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					SwingUtilities.invokeLater(() -> {
						btnGenerateMesh.setText("Generate Mesh from Mask");
						btnGenerateMesh.setEnabled(true);
					});
				}
			}).start();
		});

		controlPanel.add(btnGenerateMesh, gbc);
		gbc.gridy++;
		
		JButton btnGenerateMeshRoi = new JButton("Generate Mesh from Roi");
		btnGenerateMeshRoi.setToolTipText("Run Marching Cubes on current roi");
		// ==========================================================
		// Marching Cubes による選択グループのメッシュ生成ボタン（改良版）
		// ==========================================================
		btnGenerateMeshRoi.addActionListener(e -> {
			if (canvas.getVolumeData() == null)
				return;

			// 1. 現在存在するROIのグループ名一覧を取得
			java.util.List<String> groupNames = canvas.getRoiGroupNames();
			if (groupNames == null || groupNames.isEmpty()) {
				JOptionPane.showMessageDialog(this, "No ROI groups available to generate mesh.", "Info",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			// 2. ユーザーにどのグループを3Dメッシュ化するか選ばせるポップアップを表示
			String[] choices = groupNames.toArray(new String[0]);
			String selectedGroup = (String) JOptionPane.showInputDialog(this,
					"Select an ROI group to convert into a 3D Mesh:", "Select ROI Group", JOptionPane.QUESTION_MESSAGE,
					null, choices, choices[0]);

			// キャンセルされた場合は終了
			if (selectedGroup == null)
				return;

			// UIの無効化
			btnGenerateMeshRoi.setEnabled(false);
			btnGenerateMeshRoi.setText("Generating 3D Mesh...");

			// 重い処理なので別スレッドで実行
			new Thread(() -> {
				try {
					// 3. GLCanvasに追加したメソッドを使い、選択されたグループの全ROIを回収
					java.util.List<FreeFormRoi3D> targetRois = canvas.getRoisByGroup(selectedGroup);

					if (targetRois.isEmpty()) {
						Log.logger.warning("No ROI objects found for the group: " + selectedGroup);
						return;
					}

					// 4. 複数ROIを1つにまとめてメッシュ化するパイプラインを実行
					Log.logger.info("Merging and converting group [" + selectedGroup + "] to 3D mesh...");
					MeshData generatedMesh = convertRoiGroupToMesh(targetRois);

					if (generatedMesh != null) {
						// 5. ボリュームの描画スケール(-0.5〜0.5空間)と位置を完全に同期
						alignMeshToVolume(generatedMesh, canvas.getVolumeData());

						// 生成完了したら描画スレッドで画面に反映
						SwingUtilities.invokeLater(() -> {
							canvas.setMeshData(generatedMesh);
							canvas.setMeshVisible(true);
							chkShowMesh.setSelected(true);
							btnGenerateMeshRoi.setText("Generate Mesh from Roi");
							btnGenerateMeshRoi.setEnabled(true);
							JOptionPane.showMessageDialog(this, "3D Mesh generated successfully!", "Success",
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

		// メッシュの透明度スライダー (オプション)
		JSlider sliderMeshAlpha = new JSlider(0, 100, 100);
		sliderMeshAlpha.setToolTipText("Adjust Mesh Opacity");
		controlPanel.add(new JLabel("Mesh Opacity"), gbc);
		gbc.gridy++;
		controlPanel.add(sliderMeshAlpha, gbc);
		gbc.gridy++;

		sliderMeshAlpha.addChangeListener(e -> {
		    if (canvas != null) {
		        float alpha = sliderMeshAlpha.getValue() / 100.0f;
		        canvas.setMeshAlpha(alpha);
		    }
		});

		// ★ここが最大のポイント：JScrollPaneにのみ残りの縦幅を全割り当てする
		gbc.weighty = 1.0; // 縦方向の拡張ウェイトを設定
		gbc.fill = GridBagConstraints.BOTH; // 縦横両方に広げる設定に変更
		gbc.insets = new Insets(4, 4, 0, 4); // 最下部の余白調整

		// ★追加/変更: 内側のROIリストが潰れないように推奨サイズ(PreferredSize)を明示する
		roiColorPanel = new JPanel();
		roiColorPanel.setLayout(new BoxLayout(roiColorPanel, BoxLayout.Y_AXIS));
		JScrollPane roiScrollPane = new JScrollPane(roiColorPanel);
		roiScrollPane.setMinimumSize(new Dimension(200, 150));
		roiScrollPane.setPreferredSize(new Dimension(300, 200)); // ←★この行を追加
		controlPanel.add(roiScrollPane, gbc);

		// ★追加: キャンバスから「ROI情報が更新されたよ」という通知を受け取ってUIを作る
		canvas.setOnRoiLoadedCallback(() -> refreshRoiColorUI());

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
	// メッシュの座標をGLCanvasのボリューム描画空間にピッタリ合わせる
	// ==========================================================
	private void alignMeshToVolume(MeshData mesh, VolumeData vol) {
		// ボリュームの物理的なサイズ(mm)
		float physX = vol.width * (float) vol.pixelSpacingX;
		float physY = vol.height * (float) vol.pixelSpacingY;
		float physZ = vol.depth * (float) vol.sliceThickness;

		// 最大の辺（GLCanvasの calculateModelMatrix と同じ基準）
		float maxDim = Math.max(physX, Math.max(physY, physZ));

		// ボリュームの物理的な中心座標
		float cx = physX / 2.0f;
		float cy = physY / 2.0f;
		float cz = physZ / 2.0f;

		// すべての頂点を中心(0,0,0)に移動させ、最大辺が1.0になるように縮小
		for (int i = 0; i < mesh.vertices.length; i += 3) {
			// GLCanvasのボリューム描画ではY軸やZ軸が反転している場合があるため、
			// もしメッシュが上下逆・前後逆に出る場合は、ここの符号を反転(- を + にするなど)して調整します。
			mesh.vertices[i] = (mesh.vertices[i] - cx) / maxDim;
			mesh.vertices[i + 1] = (mesh.vertices[i + 1] - cy) / maxDim;
			mesh.vertices[i + 2] = (mesh.vertices[i + 2] - cz) / maxDim;
		}
	}
	
	// ==========================================================
	// 指定されたグループの全ROIを1つの3Dメッシュに結合・変換する
	// ==========================================================
	private MeshData convertRoiGroupToMesh(java.util.List<FreeFormRoi3D> rois) {
		if (rois == null || rois.isEmpty())
			return null;

		// 1. 空間基準となる最初のROIから次元（ボクセル数）やピクセル間隔を取得
		FreeFormRoi3D firstRoi = rois.get(0);
		int[] dims = firstRoi.getDimensions();
		int dimX = dims[0];
		int dimY = dims[1];
		int dimZ = dims[2];
		double[] spacing = firstRoi.getSpacing();

		// 2. 3D空間全体を格納する1次元の巨大なbyte配列を用意（初期値はすべて0）
		byte[] mergedVolumeBytes = new byte[dimX * dimY * dimZ];

		// 3. グループに属するすべてのROIの全スライスを走査し、1つのボリュームにマージ(OR)する
		for (FreeFormRoi3D roi : rois) {
			for (int z = 0; z < dimZ; z++) {
				// 各ROIが保持するZスライスのマスクデータ（0 or 255）を取得
				ij.process.ByteProcessor bp = roi.getMaskAsBytes(z);
				if (bp != null) {
					byte[] slicePixels = (byte[]) bp.getPixels();
					int offset = z * dimX * dimY;

					// マスクが存在する（255の）ピクセルを、結合先配列に上書きマージ
					for (int i = 0; i < slicePixels.length; i++) {
						if (slicePixels[i] != 0) {
							mergedVolumeBytes[offset + i] = (byte) 255;
						}
					}
				}
			}
		}

		// 4. MarchingCubesに流し込むためのVolumeData（マージ版ダミー）を構築
		VolumeData mergedVolume = new VolumeData(dimX, dimY, dimZ, mergedVolumeBytes);
		mergedVolume.pixelSpacingX = spacing[0];
		mergedVolume.pixelSpacingY = spacing[1];
		mergedVolume.sliceThickness = spacing[2];
		mergedVolume.minVal = 0;
		mergedVolume.maxVal = 255;

		// 5. 自作した最強の MarchingCubes アルゴリズムで一発生成！
		return MarchingCubes.generateMesh(mergedVolume, 127.5f);
	}
	
	// ==========================================================
	// FreeFormRoi3D を MeshData に変換するパイプライン
	// ==========================================================
	private MeshData convertRoiToMesh(FreeFormRoi3D roi) {
		if (!roi.isInitialized())
			return null;

		int[] dims = roi.getDimensions();
		int dimX = dims[0];
		int dimY = dims[1];
		int dimZ = dims[2];
		double[] spacing = roi.getSpacing();

		// 1. 3D空間全体を格納する1次元byte配列を用意
		byte[] volumeBytes = new byte[dimX * dimY * dimZ];

		// 2. ROIの各Zスライスからマスク(0 or 255)を取り出し、配列にコピー
		for (int z = 0; z < dimZ; z++) {
			ij.process.ByteProcessor bp = roi.getMaskAsBytes(z);
			if (bp != null) {
				byte[] slicePixels = (byte[]) bp.getPixels();
				int offset = z * dimX * dimY;
				System.arraycopy(slicePixels, 0, volumeBytes, offset, slicePixels.length);
			}
		}

		// 3. MarchingCubesに渡すためのVolumeData(ダミー)を構築
		VolumeData roiVolume = new VolumeData(dimX, dimY, dimZ, volumeBytes);
		roiVolume.pixelSpacingX = spacing[0];
		roiVolume.pixelSpacingY = spacing[1];
		roiVolume.sliceThickness = spacing[2];
		roiVolume.dataType = VolumeData.DataType.BYTE;
		roiVolume.minVal = 0;
		roiVolume.maxVal = 255;

		// 4. メッシュ生成！(閾値は0と255の中間)
		return MarchingCubes.generateMesh(roiVolume, 127.5f);
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