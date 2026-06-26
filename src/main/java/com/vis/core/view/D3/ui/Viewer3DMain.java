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
 * and other provisions required than the other one. If you do not delete
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
import com.vis.core.view.D3.util.MeshAnalyzer;
import com.vis.core.view.D3.util.MeshExporter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
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
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

/**
 * @author tatsunidas
 */
public class Viewer3DMain extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Viewer3DMain frame = new Viewer3DMain();
            frame.setVisible(true);
            frame.revalidate();
            frame.repaint();

            javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
                if (frame.canvas != null && frame.canvas.isDisplayable()) {
                    frame.canvas.render();
                    frame.canvas.repaint();
                } else {
                    ((javax.swing.Timer) e.getSource()).stop();
                }
            });
            timer.setRepeats(true);
            timer.start();
        });
    }

    private static final long serialVersionUID = 1L;
    public GLCanvas canvas;

    // === Unified scene objects table ===
    private JTable sceneObjectTable;
    private SceneObjectTableModel sceneObjectModel;
    private JTextArea statsDetailArea;
    private JTextArea measureResultArea;

    private final java.util.List<OpacityCurvePanel.ControlPoint> opacityCurvePoints = new java.util.ArrayList<>();

    private Float endoUDragStartValue = null;
    private boolean suppressEndoUCommit = false;

    private java.util.Map<String, MeshData> rawMeshMap = new java.util.LinkedHashMap<>();

    public Viewer3DMain() {
        setTitle("GRAPHY 3D Viewer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1000, 800);
        setLayout(new BorderLayout());

        // ── 1. OpenGL canvas ──────────────────────────────────────────────
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        data.doubleBuffer = true;
        data.forwardCompatible = true;

        canvas = new GLCanvas(data);
        add(canvas, BorderLayout.CENTER);

        // ── 2. Menu bar ────────────────────────────────────────────────────
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open DICOM/Obj...");
        openItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setCurrentDirectory(new File("."));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getAbsolutePath();
                new Thread(() -> {
                    VolumeData vol = VolumeLoader.loadDicom(path);
                    if (vol != null) canvas.setVolumeData(vol);
                }).start();
            }
        });
        fileMenu.add(openItem);

        JMenuItem openMeshItem = new JMenuItem("Open Mesh (OBJ/STL)...");
        openMeshItem.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setCurrentDirectory(new File("."));
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "3D Mesh Files (*.obj, *.stl)", "obj", "stl"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File sel = fc.getSelectedFile();
                String path = sel.getAbsolutePath();
                String fileName = sel.getName();
                String meshName = fileName.contains(".")
                        ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

                new Thread(() -> {
                    MeshData mesh = MeshLoader.load(path);
                    if (mesh != null) {
                        MeshData rawClone = new MeshData(
                                mesh.vertices.clone(), mesh.normals.clone(), mesh.indices.clone());
                        rawMeshMap.put(meshName, rawClone);

                        if (canvas.getVolumeData() != null) {
                            AlignMesh.alignMeshToVolume(mesh, canvas.getVolumeData());
                        }
                        SwingUtilities.invokeLater(() -> {
                            canvas.addOrUpdateMesh(meshName, mesh);
                            canvas.setMeshVisible(true);
                            refreshSceneTable();
                            JOptionPane.showMessageDialog(this,
                                    "Mesh '" + meshName + "' imported successfully!",
                                    "Success", JOptionPane.INFORMATION_MESSAGE);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                                "Failed to load mesh file.", "Error", JOptionPane.ERROR_MESSAGE));
                    }
                }).start();
            }
        });
        fileMenu.add(openMeshItem);

        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        fileMenu.add(exit);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // ── 3. Control panel ───────────────────────────────────────────────
        JPanel controlPanel = new ScrollableControlPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;

        JLabel titleLabel = new JLabel("Control Panel", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        controlPanel.add(titleLabel, gbc); gbc.gridy++;

        JButton resetCamera = new JButton("Reset Camera");
        resetCamera.addActionListener(e -> new Thread(canvas::resetCamera).start());
        controlPanel.add(resetCamera, gbc); gbc.gridy++;

        // ── Rendering Mode ─────────────────────────────────────────────────
        controlPanel.add(new JLabel("Rendering Mode", SwingConstants.LEFT), gbc); gbc.gridy++;

        JRadioButton radioVR        = new JRadioButton("Volume Rendering (VR)");
        JRadioButton radioMIP       = new JRadioButton("MIP");
        JRadioButton radioOrtho     = new JRadioButton("Ortho Slices");
        JRadioButton radioCinematic = new JRadioButton("Cinematic Rendering");
        radioCinematic.setToolTipText(
                "<html>モンテカルロ・パストレーシングで光源の陰影・セルフシャドウを再現する表示モード。</html>");
        radioMIP.setSelected(true);

        ButtonGroup renderGroup = new ButtonGroup();
        renderGroup.add(radioVR); renderGroup.add(radioMIP);
        renderGroup.add(radioOrtho); renderGroup.add(radioCinematic);

        java.awt.event.ActionListener modeListener = e -> new Thread(() -> {
            if (radioVR.isSelected()) {
                canvas.setShowVolume(true); canvas.setMIPMode(false);
                canvas.setOrthoMode(false); canvas.setCinematicMode(false);
            } else if (radioMIP.isSelected()) {
                canvas.setShowVolume(true); canvas.setMIPMode(true);
                canvas.setOrthoMode(false); canvas.setCinematicMode(false);
            } else if (radioOrtho.isSelected()) {
                canvas.setShowVolume(false); canvas.setMIPMode(false);
                canvas.setOrthoMode(true); canvas.setCinematicMode(false);
            } else if (radioCinematic.isSelected()) {
                canvas.setOrthoMode(false); canvas.setCinematicMode(true);
            }
        }).start();

        radioVR.addActionListener(modeListener); radioMIP.addActionListener(modeListener);
        radioOrtho.addActionListener(modeListener); radioCinematic.addActionListener(modeListener);

        controlPanel.add(radioVR, gbc);        gbc.gridy++;
        controlPanel.add(radioMIP, gbc);       gbc.gridy++;
        controlPanel.add(radioOrtho, gbc);     gbc.gridy++;
        controlPanel.add(radioCinematic, gbc); gbc.gridy++;

        // ── Cinematic params ───────────────────────────────────────────────
        JLabel lblCinematicBackend = new JLabel("GPU: " + canvas.getCinematicBackendName());
        controlPanel.add(lblCinematicBackend, gbc); gbc.gridy++;
        canvas.setOnCinematicReadyCallback(
                () -> lblCinematicBackend.setText("GPU: " + canvas.getCinematicBackendName()));

        controlPanel.add(new JLabel("Light Azimuth"), gbc);   gbc.gridy++;
        JSlider sliderLightAzimuth  = new JSlider(0, 360, 45);
        sliderLightAzimuth.setToolTipText(
                "<html>光源の水平方向の角度（度）。<br>"
                + "0 = 正面、90 = 右、180 = 背面、270 = 左。</html>");
        controlPanel.add(sliderLightAzimuth,  gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Light Elevation"), gbc); gbc.gridy++;
        JSlider sliderLightElevation= new JSlider(-90, 90, 60);
        sliderLightElevation.setToolTipText(
                "<html>光源の仰角（度）。<br>"
                + "0 = 水平照射、90 = 真上から照射。<br>"
                + "マイナスにすると下から照射。</html>");
        controlPanel.add(sliderLightElevation, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Light Intensity"), gbc); gbc.gridy++;
        JSlider sliderLightIntensity= new JSlider(0, 400, 150);
        sliderLightIntensity.setToolTipText(
                "<html>光源の強さ（0〜4.0）。<br>"
                + "大きいほど明るく陰影が強くなる。<br>"
                + "Exposure と組み合わせて全体輝度を調整する。</html>");
        controlPanel.add(sliderLightIntensity, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Ambient Intensity"), gbc); gbc.gridy++;
        JSlider sliderAmbient       = new JSlider(0, 100, 25);
        sliderAmbient.setToolTipText(
                "<html>環境光（間接光）の強さ（0〜1.0）。<br>"
                + "増やすと影の中も明るくなりフラットな印象になる。<br>"
                + "0 にすると影が完全に黒くなる。</html>");
        controlPanel.add(sliderAmbient,        gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Shadow Softness"), gbc); gbc.gridy++;
        JSlider sliderShadowSoftness= new JSlider(0, 30, 8);
        sliderShadowSoftness.setToolTipText(
                "<html>影の柔らかさ（光源の見かけ上の半角度、度）。<br>"
                + "0 = 点光源（硬い影）、大きいほどペナンブラが広がりソフトな影になる。<br>"
                + "蓄積フレーム数が増えるほど滑らかに収束する。</html>");
        controlPanel.add(sliderShadowSoftness, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Exposure"), gbc);        gbc.gridy++;
        JSlider sliderExposure      = new JSlider(10, 400, 150);
        sliderExposure.setToolTipText(
                "<html>トーンマッピング前の輝度スケール（0.1〜4.0）。<br>"
                + "全体が明るすぎる・暗すぎる場合に調整する。<br>"
                + "Light Intensity と違い、蓄積バッファをリセットせずに即時反映される。</html>");
        controlPanel.add(sliderExposure,      gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Samples / Frame"), gbc); gbc.gridy++;
        JSlider sliderSamples       = new JSlider(1, 16, 1);
        sliderSamples.setToolTipText(
                "<html>1フレームあたりのパストレーシングサンプル数。<br>"
                + "多いほど高品質だが描画が重くなる。<br>"
                + "静止中は自動的にフレームが蓄積されてノイズが減少する。<br>"
                + "カメラを動かすと蓄積がリセットされる。</html>");
        sliderSamples.setMajorTickSpacing(5); sliderSamples.setMinorTickSpacing(1);
        sliderSamples.setPaintTicks(true);    sliderSamples.setPaintLabels(true);
        controlPanel.add(sliderSamples, gbc); gbc.gridy++;

        // ── PBR material parameters ───────────────────────────────────────
        controlPanel.add(new JLabel("── Material ──", SwingConstants.LEFT), gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Roughness  (0=Glossy / 100=Matte)"), gbc); gbc.gridy++;
        JSlider sliderRoughness = new JSlider(0, 100, 50);
        sliderRoughness.setToolTipText(
                "<html><b>表面の粗さ（GGX Roughness）</b><br>"
                + "0 = 完全な鏡面反射（ガラス・金属のような光沢）<br>"
                + "100 = 完全な拡散反射（石膏・マットな骨）<br>"
                + "<br>"
                + "骨: 70〜90、軟部組織: 40〜60、皮膚: 20〜40 が目安。</html>");
        controlPanel.add(sliderRoughness, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Specular"), gbc); gbc.gridy++;
        JSlider sliderSpecular = new JSlider(0, 100, 50);
        sliderSpecular.setToolTipText(
                "<html><b>鏡面反射成分の強さ</b><br>"
                + "非金属（誘電体）素材の基本反射率 F0 = 0.04 × Specular を調整する。<br>"
                + "Roughness が低い（ツヤがある）ほど効果が大きくなる。<br>"
                + "Metallic が 1.0 の場合はアルベド色が F0 に使われる。</html>");
        controlPanel.add(sliderSpecular, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Clearcoat"), gbc); gbc.gridy++;
        JSlider sliderClearcoat = new JSlider(0, 100, 0);
        sliderClearcoat.setToolTipText(
                "<html><b>クリアコート層の強さ</b><br>"
                + "基材（Roughness / Specular で設定した素材）の上に<br>"
                + "薄い透明の鏡面コート層を追加する。<br>"
                + "皮膚・膜・ぬれた表面のような光沢を表現する。<br>"
                + "0 = コートなし、100 = 最大コート。</html>");
        controlPanel.add(sliderClearcoat, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Clearcoat Roughness"), gbc); gbc.gridy++;
        JSlider sliderClearcoatRoughness = new JSlider(0, 100, 5);
        sliderClearcoatRoughness.setToolTipText(
                "<html><b>クリアコート層の粗さ</b><br>"
                + "0 に近いほど鋭い点状ハイライト（鏡面）になる。<br>"
                + "大きくするとハイライトが広がり曇ったコートになる。<br>"
                + "既定値 5（= 0.05）が皮膚・骨の表面光沢として自然。</html>");
        controlPanel.add(sliderClearcoatRoughness, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Surface Sensitivity"), gbc); gbc.gridy++;
        JSlider sliderGradThreshold = new JSlider(1, 100, 15);
        sliderGradThreshold.setToolTipText(
                "<html><b>サーフェス検出の感度（グラディエント閾値）</b><br>"
                + "不透明度グラディエントがこの値以上の点を<br>"
                + "組織境界面と判定し、BRDFシェーディングを適用する。<br>"
                + "<br>"
                + "値が大きい（50〜）: ほぼ HG ボリューム散乱のまま（変化が出にくい）<br>"
                + "値が小さい（10〜20）: 組織境界に BRDF が適用される ← 推奨範囲<br>"
                + "値が極小（1〜5）: 内部もBRDF化して不自然になる</html>");
        controlPanel.add(sliderGradThreshold, gbc); gbc.gridy++;

        Runnable applyCinematicParams = () -> {
            com.vis.core.view.D3.ui.cinematic.CinematicParams p = canvas.getCinematicParams();
            p.lightAzimuth              = (float) Math.toRadians(sliderLightAzimuth.getValue());
            p.lightElevation            = (float) Math.toRadians(sliderLightElevation.getValue());
            p.lightIntensity            = sliderLightIntensity.getValue() / 100.0f;
            p.ambientIntensity          = sliderAmbient.getValue() / 100.0f;
            p.lightAngularRadius        = (float) Math.toRadians(sliderShadowSoftness.getValue());
            p.exposure                  = sliderExposure.getValue() / 100.0f;
            p.samplesPerFrame           = sliderSamples.getValue();
            p.roughness                 = sliderRoughness.getValue() / 100.0f;
            p.specular                  = sliderSpecular.getValue() / 100.0f;
            p.clearcoat                 = sliderClearcoat.getValue() / 100.0f;
            p.clearcoatRoughness        = sliderClearcoatRoughness.getValue() / 100.0f;
            p.surfaceGradientThreshold  = sliderGradThreshold.getValue() / 100.0f;
            canvas.invalidateCinematicAccumulation();
        };
        sliderLightAzimuth       .addChangeListener(e -> applyCinematicParams.run());
        sliderLightElevation     .addChangeListener(e -> applyCinematicParams.run());
        sliderLightIntensity     .addChangeListener(e -> applyCinematicParams.run());
        sliderAmbient            .addChangeListener(e -> applyCinematicParams.run());
        sliderShadowSoftness     .addChangeListener(e -> applyCinematicParams.run());
        sliderExposure           .addChangeListener(e -> applyCinematicParams.run());
        sliderSamples            .addChangeListener(e -> applyCinematicParams.run());
        sliderRoughness          .addChangeListener(e -> applyCinematicParams.run());
        sliderSpecular           .addChangeListener(e -> applyCinematicParams.run());
        sliderClearcoat          .addChangeListener(e -> applyCinematicParams.run());
        sliderClearcoatRoughness .addChangeListener(e -> applyCinematicParams.run());
        sliderGradThreshold      .addChangeListener(e -> applyCinematicParams.run());

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── 3D Clipping ────────────────────────────────────────────────────
        controlPanel.add(new JLabel("3D Clipping", SwingConstants.LEFT), gbc); gbc.gridy++;

        JCheckBox chkClip3D = new JCheckBox("Edit Clip Box", false);
        chkClip3D.addActionListener(e -> { if (canvas != null) canvas.setClip3DEnabled(chkClip3D.isSelected()); });
        controlPanel.add(chkClip3D, gbc); gbc.gridy++;

        JButton btnResetClip = new JButton("Reset Clip Box");
        btnResetClip.addActionListener(e -> { if (canvas != null) canvas.resetClipBox(); });
        controlPanel.add(btnResetClip, gbc); gbc.gridy++;

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── Color Map (LUT) ────────────────────────────────────────────────
        controlPanel.add(new JLabel("Color Map (LUT)", SwingConstants.LEFT), gbc); gbc.gridy++;
        String[] lutNames = com.vis.configuration.Resources.getLutNames();
        javax.swing.JComboBox<String> comboLut = new javax.swing.JComboBox<>(lutNames);
        comboLut.setSelectedItem("Grayscale");
        comboLut.addActionListener(e -> {
            String sel = (String) comboLut.getSelectedItem();
            new Thread(() -> {
                if ("Grayscale".equals(sel)) {
                    canvas.setLutType(0);
                } else {
                    ij.process.LUT lut = com.vis.configuration.Resources.loadLUT(sel);
                    if (lut != null) canvas.applyLut(lut);
                }
            }).start();
        });
        controlPanel.add(comboLut, gbc); gbc.gridy++;

        JButton btnEditOpacity = new JButton("Edit Opacity Curve...");
        btnEditOpacity.addActionListener(e ->
                new VolumeOpacityCurveEditorDialog(this, canvas, opacityCurvePoints).setVisible(true));
        controlPanel.add(btnEditOpacity, gbc); gbc.gridy++;

        JButton btnCenterline = new JButton("Centerline Analysis...");
        btnCenterline.addActionListener(e -> CenterlineAnalysisDialog.showDialog(canvas, this));
        controlPanel.add(btnCenterline, gbc); gbc.gridy++;

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── Slice sliders ──────────────────────────────────────────────────
        JSlider sliderX = new JSlider(0, 100, 50);
        controlPanel.add(new JLabel("Sagittal (X)"), gbc); gbc.gridy++;
        controlPanel.add(sliderX, gbc);                    gbc.gridy++;

        JSlider sliderY = new JSlider(0, 100, 50);
        controlPanel.add(new JLabel("Coronal (Y)"), gbc);  gbc.gridy++;
        controlPanel.add(sliderY, gbc);                    gbc.gridy++;

        JSlider sliderZ = new JSlider(0, 100, 50);
        controlPanel.add(new JLabel("Axial (Z)"), gbc);    gbc.gridy++;
        controlPanel.add(sliderZ, gbc);                    gbc.gridy++;

        sliderX.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));
        sliderY.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));
        sliderZ.addChangeListener(e -> updateSlices(canvas, sliderX, sliderY, sliderZ));

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── Show ROI / Ortho mode / ROI opacity ────────────────────────────
        JCheckBox chkShowRoi = new JCheckBox("Show ROI", true);
        chkShowRoi.addActionListener(e -> canvas.setShowRoi(chkShowRoi.isSelected()));
        controlPanel.add(chkShowRoi, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Ortho ROI Display Mode"), gbc); gbc.gridy++;
        String[] orthoModes = {"No ROI", "Slice Overlay (2D)", "Float Overlay (3D)", "Embedded (3D)"};
        javax.swing.JComboBox<String> comboOrthoRoi = new javax.swing.JComboBox<>(orthoModes);
        comboOrthoRoi.setSelectedIndex(1);
        comboOrthoRoi.addActionListener(e -> {
            int idx = comboOrthoRoi.getSelectedIndex();
            if (canvas == null) return;
            if      (idx == 0) canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.NONE);
            else if (idx == 1) canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.SLICE_2D);
            else if (idx == 2) canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.FLOAT_3D);
            else               canvas.setOrthoRoiMode(GLCanvas.OrthoRoiMode.EMBEDDED_3D);
        });
        controlPanel.add(comboOrthoRoi, gbc); gbc.gridy++;

        JSlider sliderRoiAlpha = new JSlider(0, 100, 50);
        sliderRoiAlpha.setToolTipText("Adjust ROI Opacity");
        sliderRoiAlpha.addChangeListener(e -> {
            if (canvas != null) canvas.setRoiAlpha(sliderRoiAlpha.getValue() / 100.0f);
        });
        controlPanel.add(new JLabel("ROI Opacity"), gbc); gbc.gridy++;
        controlPanel.add(sliderRoiAlpha, gbc);            gbc.gridy++;

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── Scene Objects table (ROI + Mesh unified) ───────────────────────
        controlPanel.add(new JLabel("Scene Objects"), gbc); gbc.gridy++;

        sceneObjectModel = new SceneObjectTableModel();
        sceneObjectTable = new JTable(sceneObjectModel);
        sceneObjectTable.setRowHeight(22);
        sceneObjectTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        sceneObjectTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        // Column widths: Visible(24) | Name(fills) | Type(44) | Color(24)
        javax.swing.table.TableColumnModel tcm = sceneObjectTable.getColumnModel();
        tcm.getColumn(SceneObjectTableModel.COL_VISIBLE).setMinWidth(20);
        tcm.getColumn(SceneObjectTableModel.COL_VISIBLE).setMaxWidth(24);
        tcm.getColumn(SceneObjectTableModel.COL_VISIBLE).setPreferredWidth(24);
        tcm.getColumn(SceneObjectTableModel.COL_TYPE).setMinWidth(36);
        tcm.getColumn(SceneObjectTableModel.COL_TYPE).setMaxWidth(44);
        tcm.getColumn(SceneObjectTableModel.COL_TYPE).setPreferredWidth(44);
        tcm.getColumn(SceneObjectTableModel.COL_COLOR).setMinWidth(20);
        tcm.getColumn(SceneObjectTableModel.COL_COLOR).setMaxWidth(24);
        tcm.getColumn(SceneObjectTableModel.COL_COLOR).setPreferredWidth(24);
        tcm.getColumn(SceneObjectTableModel.COL_NAME).setPreferredWidth(200);
        tcm.getColumn(SceneObjectTableModel.COL_COLOR)
                .setCellRenderer(new ColorCellRenderer());
        tcm.getColumn(SceneObjectTableModel.COL_COLOR)
                .setCellEditor(new ColorCellEditor());

        // Respond to Visible / Color changes in the table
        sceneObjectModel.addTableModelListener(evt -> {
            int row = evt.getFirstRow();
            int col = evt.getColumn();
            SceneObjectTableModel.Entry entry = sceneObjectModel.getEntry(row);
            if (entry == null) return;
            if (col == SceneObjectTableModel.COL_VISIBLE) {
                if ("Mesh".equals(entry.type)) {
                    canvas.setMeshVisibleByName(entry.name, entry.visible);
                } else {
                    canvas.setShowRoi(entry.visible);
                }
            } else if (col == SceneObjectTableModel.COL_COLOR) {
                if ("Mesh".equals(entry.type)) {
                    canvas.setMeshColor(entry.name, entry.color);
                } else {
                    int roiIdx = 0;
                    for (int i = 0; i < sceneObjectModel.getRowCount(); i++) {
                        SceneObjectTableModel.Entry e2 = sceneObjectModel.getEntry(i);
                        if ("ROI".equals(e2.type)) {
                            if (i == row) { canvas.setRoiGroupColor(roiIdx, entry.color); break; }
                            roiIdx++;
                        }
                    }
                }
            }
        });

        // Selection → update stats detail area
        sceneObjectTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = sceneObjectTable.getSelectedRow();
            if (row >= 0) {
                SceneObjectTableModel.Entry entry = sceneObjectModel.getEntry(row);
                if (entry != null && entry.stats != null) {
                    updateStatsDetailArea(entry.name, entry.stats);
                } else {
                    statsDetailArea.setText("Select an object and click 'Compute Stats'.");
                }
            }
        });

        sceneObjectTable.setPreferredScrollableViewportSize(new Dimension(340, 168));
        JScrollPane tableScroll = new JScrollPane(sceneObjectTable);
        tableScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlPanel.add(tableScroll, gbc); gbc.gridy++;

        // Canvas callback: ROI loaded → refresh table
        canvas.setOnRoiLoadedCallback(() -> refreshSceneTable());

        // Compute Stats / Export CSV buttons
        JPanel statsBtnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        JButton btnComputeStats = new JButton("Compute Stats");
        btnComputeStats.addActionListener(e -> computeStatsForSelected());
        JButton btnExportCsv = new JButton("Export CSV...");
        btnExportCsv.addActionListener(e -> exportStatsCsv());
        statsBtnRow.add(btnComputeStats);
        statsBtnRow.add(btnExportCsv);
        controlPanel.add(statsBtnRow, gbc); gbc.gridy++;

        // Stats detail text area
        statsDetailArea = new JTextArea(5, 20);
        statsDetailArea.setEditable(false);
        statsDetailArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statsDetailArea.setText("Select an object and click 'Compute Stats'.");
        JScrollPane statsScroll = new JScrollPane(statsDetailArea);
        statsScroll.setPreferredSize(new Dimension(300, 100));
        controlPanel.add(statsScroll, gbc); gbc.gridy++;

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── Mesh Opacity ───────────────────────────────────────────────────
        JSlider sliderMeshAlpha = new JSlider(0, 100, 100);
        sliderMeshAlpha.setToolTipText("Adjust Mesh Opacity");
        sliderMeshAlpha.addChangeListener(e -> {
            if (canvas != null) canvas.setMeshAlpha(sliderMeshAlpha.getValue() / 100.0f);
        });
        controlPanel.add(new JLabel("Mesh Opacity"), gbc); gbc.gridy++;
        controlPanel.add(sliderMeshAlpha, gbc);            gbc.gridy++;

        // ── Generate Mesh from ROI ─────────────────────────────────────────
        JButton btnGenerateMeshRoi = new JButton("Generate Mesh from Roi");
        btnGenerateMeshRoi.setToolTipText("Run Marching Cubes on current selected ROI");
        btnGenerateMeshRoi.addActionListener(e -> {
            if (canvas.getVolumeData() == null) return;

            String selectedGroup = getSelectedRoiName();
            if (selectedGroup == null) {
                JOptionPane.showMessageDialog(this,
                        "Please select an ROI group from the Scene Objects list first.",
                        "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            btnGenerateMeshRoi.setEnabled(false);
            btnGenerateMeshRoi.setText("Generating 3D Mesh...");

            new Thread(() -> {
                try {
                    VolumeData currentVol = canvas.getVolumeData();
                    java.util.List<FreeFormRoi3D> targetRois = canvas.getRoisByGroup(selectedGroup);
                    Log.logger.info("Converting group [" + selectedGroup + "] to 3D mesh...");
                    MeshData generatedMesh = convertRoiGroupToMesh(targetRois, currentVol);

                    if (generatedMesh != null) {
                        MeshData rawClone = new MeshData(
                                generatedMesh.vertices.clone(),
                                generatedMesh.normals.clone(),
                                generatedMesh.indices.clone());
                        rawMeshMap.put(selectedGroup, rawClone);

                        AlignMesh.alignMeshToVolume(generatedMesh, canvas.getVolumeData());

                        SwingUtilities.invokeLater(() -> {
                            canvas.addOrUpdateMesh(selectedGroup, generatedMesh);

                            java.util.List<String> roiNames  = canvas.getRoiGroupNames();
                            java.util.List<java.awt.Color> roiColors = canvas.getRoiColors();
                            int roiIndex = roiNames.indexOf(selectedGroup);
                            if (roiIndex != -1) canvas.setMeshColor(selectedGroup, roiColors.get(roiIndex));

                            canvas.setMeshVisible(true);
                            btnGenerateMeshRoi.setText("Generate Mesh from Roi");
                            btnGenerateMeshRoi.setEnabled(true);
                            refreshSceneTable();
                            JOptionPane.showMessageDialog(this, "3D Mesh generated and added!", "Success",
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
        controlPanel.add(btnGenerateMeshRoi, gbc); gbc.gridy++;

        // ── Export Mesh to STL ─────────────────────────────────────────────
        JButton btnExportStl = new JButton("Export Mesh to STL...");
        btnExportStl.setToolTipText("Save the selected 3D mesh as STL for 3D printing");
        btnExportStl.addActionListener(e -> {
            String selMesh = getSelectedMeshName();
            if (selMesh == null || !rawMeshMap.containsKey(selMesh)) {
                JOptionPane.showMessageDialog(this,
                        "Please select a Mesh row from the Scene Objects list.",
                        "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
            fc.setDialogTitle("Export Selected 3D Mesh as Binary STL");
            fc.setSelectedFile(new File("exported_" + selMesh.replace(":", "").replace(" ", "_") + ".stl"));
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Stereolithography (*.stl)", "stl"));
            if (fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                if (!f.getName().toLowerCase().endsWith(".stl"))
                    f = new File(f.getAbsolutePath() + ".stl");
                try {
                    MeshExporter.exportToBinarySTL(f, rawMeshMap.get(selMesh));
                    JOptionPane.showMessageDialog(this,
                            "Mesh exported to:\n" + f.getName(), "Export Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this,
                            "Failed to export STL:\n" + ex.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        controlPanel.add(btnExportStl, gbc); gbc.gridy++;

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── Measurements ────────────────────────────────────────────────────
        controlPanel.add(new JLabel("Measurements"), gbc); gbc.gridy++;

        JRadioButton radioMeasNone = new JRadioButton("None");
        JRadioButton radioMeasDist = new JRadioButton("Distance (2-point)");
        JRadioButton radioMeasLine = new JRadioButton("3D Line");
        radioMeasNone.setSelected(true);
        ButtonGroup measGroup = new ButtonGroup();
        measGroup.add(radioMeasNone); measGroup.add(radioMeasDist); measGroup.add(radioMeasLine);

        radioMeasNone.addActionListener(e -> canvas.setMeasurementMode(GLCanvas.MeasurementMode.NONE));
        radioMeasDist.addActionListener(e -> canvas.setMeasurementMode(GLCanvas.MeasurementMode.DISTANCE));
        radioMeasLine.addActionListener(e -> canvas.setMeasurementMode(GLCanvas.MeasurementMode.LINE_3D));

        controlPanel.add(radioMeasNone, gbc); gbc.gridy++;
        controlPanel.add(radioMeasDist, gbc); gbc.gridy++;
        controlPanel.add(radioMeasLine, gbc); gbc.gridy++;

        JPanel measBtnRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        JButton btnMeasClear = new JButton("Clear");
        JButton btnMeasUndo  = new JButton("Undo");
        JButton btnMeasRedo  = new JButton("Redo");
        btnMeasClear.addActionListener(e -> {
            canvas.clearMeasurements();
            updateMeasureResultUI();
        });
        btnMeasUndo.addActionListener(e -> {
            canvas.getUndoManager().undo();
            updateMeasureResultUI();
            canvas.repaint();
        });
        btnMeasRedo.addActionListener(e -> {
            canvas.getUndoManager().redo();
            updateMeasureResultUI();
            canvas.repaint();
        });
        measBtnRow.add(btnMeasClear); measBtnRow.add(btnMeasUndo); measBtnRow.add(btnMeasRedo);
        controlPanel.add(measBtnRow, gbc); gbc.gridy++;

        measureResultArea = new JTextArea(5, 20);
        measureResultArea.setEditable(false);
        measureResultArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        measureResultArea.setText("Select Distance or 3D Line mode,\nthen click on a mesh surface.");
        JScrollPane measResultScroll = new JScrollPane(measureResultArea);
        measResultScroll.setPreferredSize(new Dimension(300, 100));
        controlPanel.add(measResultScroll, gbc); gbc.gridy++;

        canvas.setOnMeasurementUpdateCallback(() -> SwingUtilities.invokeLater(this::updateMeasureResultUI));

        controlPanel.add(new javax.swing.JSeparator(), gbc); gbc.gridy++;

        // ── Endoscopy path edit / view ──────────────────────────────────────
        JCheckBox chkEndoPathEdit  = new JCheckBox("Edit Endoscopy Path", false);
        JCheckBox chkEndoscopyView = new JCheckBox("Endoscopy View (Fly-Through)", false);

        chkEndoPathEdit.setToolTipText(
                "Ctrl+左クリック: 点を追加 / 左クリック+ドラッグ: 点を移動 / Delete: 選択中の点を削除");
        chkEndoPathEdit.addActionListener(e -> {
            if (canvas == null) return;
            canvas.setEndoPathEditMode(chkEndoPathEdit.isSelected());
            if (chkEndoPathEdit.isSelected()) chkEndoscopyView.setSelected(false);
            canvas.repaint();
        });
        controlPanel.add(chkEndoPathEdit, gbc); gbc.gridy++;

        chkEndoscopyView.setToolTipText("内視鏡カメラ視点に切り替える（パスに2点以上必要）");
        chkEndoscopyView.addActionListener(e -> {
            if (canvas == null) return;
            canvas.setEndoscopyMode(chkEndoscopyView.isSelected());
            if (chkEndoscopyView.isSelected()) chkEndoPathEdit.setSelected(false);
            canvas.repaint();
        });
        controlPanel.add(chkEndoscopyView, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Endoscopy Position"), gbc); gbc.gridy++;
        JSlider sliderEndoU = new JSlider(0, 100, 0);
        sliderEndoU.addChangeListener(e -> {
            if (canvas == null || suppressEndoUCommit) return;
            EndoCamera cam = canvas.getEndoCamera();
            float u = sliderEndoU.getValue() / 100.0f;
            if (sliderEndoU.getValueIsAdjusting()) {
                if (endoUDragStartValue == null) endoUDragStartValue = cam.getU();
                cam.setU(u);
                cam.resetLook();
                canvas.repaint();
            } else {
                float startU = (endoUDragStartValue != null) ? endoUDragStartValue : cam.getU();
                endoUDragStartValue = null;
                if (Math.abs(u - startU) > 1e-6f) {
                    cam.setU(startU);
                    canvas.getUndoManager().addCommand(new EndoCommands.SetCameraUCommand(cam, u));
                }
                cam.resetLook();
                canvas.repaint();
            }
        });
        controlPanel.add(sliderEndoU, gbc); gbc.gridy++;

        JPanel endoJumpRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        JButton btnJumpStart = new JButton("|< Start");
        JButton btnPrevPoint = new JButton("< Point");
        JButton btnNextPoint = new JButton("Point >");
        JButton btnJumpEnd   = new JButton("End >|");
        btnJumpStart.addActionListener(e -> jumpEndoCamera(sliderEndoU, 0f));
        btnJumpEnd  .addActionListener(e -> jumpEndoCamera(sliderEndoU, 1f));
        btnPrevPoint.addActionListener(e -> {
            EndoPath3D path = canvas.getEndoPath();
            if (path.size() >= 2) jumpEndoCamera(sliderEndoU,
                    path.getNormalizedDistanceAtPoint(path.findPreviousPointIndex(canvas.getEndoCamera().getU())));
        });
        btnNextPoint.addActionListener(e -> {
            EndoPath3D path = canvas.getEndoPath();
            if (path.size() >= 2) jumpEndoCamera(sliderEndoU,
                    path.getNormalizedDistanceAtPoint(path.findNextPointIndex(canvas.getEndoCamera().getU())));
        });
        endoJumpRow.add(btnJumpStart); endoJumpRow.add(btnPrevPoint);
        endoJumpRow.add(btnNextPoint); endoJumpRow.add(btnJumpEnd);
        controlPanel.add(endoJumpRow, gbc); gbc.gridy++;

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
        endoUndoRow.add(btnEndoUndo); endoUndoRow.add(btnEndoRedo);
        controlPanel.add(endoUndoRow, gbc); gbc.gridy++;

        controlPanel.add(new JLabel("Playback Speed"), gbc); gbc.gridy++;
        JSlider sliderEndoSpeed = new JSlider(10, 300, 100);
        sliderEndoSpeed.setToolTipText("1.0x = パス全体を約10秒で通過");
        controlPanel.add(sliderEndoSpeed, gbc); gbc.gridy++;

        final float baseSpeedPerSecond = 0.1f;
        JPanel endoPlaybackRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        JButton btnEndoPlay  = new JButton("Play");
        JButton btnEndoPause = new JButton("Pause");
        JButton btnEndoStop  = new JButton("Stop");

        javax.swing.Timer endoPlaybackTimer = new javax.swing.Timer(16, evt -> {
            if (canvas == null) return;
            EndoPath3D path = canvas.getEndoPath();
            EndoCamera cam  = canvas.getEndoCamera();
            if (path.size() < 2) return;
            float speedMul = sliderEndoSpeed.getValue() / 100.0f;
            float newU     = cam.getU() + 0.016f * baseSpeedPerSecond * speedMul;
            boolean end    = newU >= 1f;
            if (end) newU = 1f;
            cam.setU(newU);
            cam.resetLook();
            suppressEndoUCommit = true;
            sliderEndoU.setValue(Math.round(newU * 100));
            suppressEndoUCommit = false;
            canvas.repaint();
            if (end) {
                ((javax.swing.Timer) evt.getSource()).stop();
                setComponentsEnabled(true, chkEndoPathEdit, chkEndoscopyView, sliderEndoU,
                        btnJumpStart, btnPrevPoint, btnNextPoint, btnJumpEnd);
            }
        });
        endoPlaybackTimer.setRepeats(true);

        btnEndoPlay.addActionListener(e -> {
            if (canvas == null || canvas.getEndoPath().size() < 2) return;
            chkEndoPathEdit.setSelected(false); canvas.setEndoPathEditMode(false);
            chkEndoscopyView.setSelected(true);  canvas.setEndoscopyMode(true);
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
        endoPlaybackRow.add(btnEndoPlay); endoPlaybackRow.add(btnEndoPause); endoPlaybackRow.add(btnEndoStop);
        controlPanel.add(endoPlaybackRow, gbc); gbc.gridy++;

        // ── Filler: pushes all content to top ──────────────────────────────
        gbc.weighty = 1.0;
        gbc.fill    = GridBagConstraints.BOTH;
        gbc.insets  = new Insets(0, 0, 0, 0);
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        controlPanel.add(filler, gbc);

        // ── Final scroll wrapper ────────────────────────────────────────────
        JScrollPane mainControlScroll = new JScrollPane(controlPanel);
        mainControlScroll.setPreferredSize(new Dimension(400, 0));
        mainControlScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainControlScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainControlScroll.setBorder(BorderFactory.createEmptyBorder());
        mainControlScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(mainControlScroll, BorderLayout.EAST);
    }

    // =========================================================================
    // Scene Objects helpers
    // =========================================================================

    private String getSelectedRoiName() {
        int row = sceneObjectTable.getSelectedRow();
        if (row < 0) return null;
        SceneObjectTableModel.Entry e = sceneObjectModel.getEntry(row);
        return (e != null && "ROI".equals(e.type)) ? e.name : null;
    }

    private String getSelectedMeshName() {
        int row = sceneObjectTable.getSelectedRow();
        if (row < 0) return null;
        SceneObjectTableModel.Entry e = sceneObjectModel.getEntry(row);
        return (e != null && "Mesh".equals(e.type)) ? e.name : null;
    }

    private void refreshSceneTable() {
        java.util.List<String> roiNames  = canvas.getRoiGroupNames();
        java.util.List<java.awt.Color> roiColors = canvas.getRoiColors();

        java.util.Map<String, java.awt.Color> meshColorMap = new java.util.LinkedHashMap<>();
        for (String name : rawMeshMap.keySet()) {
            meshColorMap.put(name, canvas.getMeshColor(name));
        }
        sceneObjectModel.rebuildFrom(roiNames, roiColors, meshColorMap);
    }

    private void computeStatsForSelected() {
        int row = sceneObjectTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a row in the Scene Objects table first.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SceneObjectTableModel.Entry entry = sceneObjectModel.getEntry(row);
        if (entry == null) return;

        new Thread(() -> {
            MeshData target = null;
            if ("Mesh".equals(entry.type)) {
                target = rawMeshMap.get(entry.name);
            } else {
                // ROI: temporarily generate mesh for stats only
                java.util.List<FreeFormRoi3D> rois = canvas.getRoisByGroup(entry.name);
                VolumeData vol = canvas.getVolumeData();
                if (rois != null && !rois.isEmpty() && vol != null) {
                    target = convertRoiGroupToMesh(rois, vol);
                }
            }
            if (target == null) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Could not compute stats: no mesh data available.", "Warning",
                        JOptionPane.WARNING_MESSAGE));
                return;
            }
            MeshMeasureResult result = MeshAnalyzer.analyze(target);
            final int finalRow = row;
            final String name  = entry.name;
            SwingUtilities.invokeLater(() -> {
                sceneObjectModel.setStats(finalRow, result);
                updateStatsDetailArea(name, result);
            });
        }).start();
    }

    private void updateStatsDetailArea(String name, MeshMeasureResult r) {
        if (statsDetailArea == null) return;
        statsDetailArea.setText(String.format(
                "=== %s ===%n" +
                "Surface Area:   %,.1f mm²  (%.2f cm²)%n" +
                "Volume:          %,.1f mm³  (%.2f mL)%n" +
                "Long Diameter:   %.2f mm%n" +
                "Mid  Diameter:   %.2f mm%n" +
                "Short Diameter:  %.2f mm",
                name,
                r.surfaceAreaMm2, r.surfaceAreaMm2 / 100.0,
                r.volumeMm3,      r.volumeMm3 / 1000.0,
                r.longDiameterMm, r.midDiameterMm, r.shortDiameterMm));
    }

    private void exportStatsCsv() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Export Scene Stats as CSV");
        fc.setSelectedFile(new File("scene_stats.csv"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV (*.csv)", "csv"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) f = new File(f.getAbsolutePath() + ".csv");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), java.nio.charset.StandardCharsets.UTF_8))) {
            pw.println("Name,Type,Surface Area (mm2),Volume (mm3),Long Diameter (mm),Mid Diameter (mm),Short Diameter (mm)");
            for (int i = 0; i < sceneObjectModel.getRowCount(); i++) {
                SceneObjectTableModel.Entry e = sceneObjectModel.getEntry(i);
                if (e == null) continue;
                if (e.stats != null) {
                    pw.printf("\"%s\",%s,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            e.name, e.type,
                            e.stats.surfaceAreaMm2, e.stats.volumeMm3,
                            e.stats.longDiameterMm, e.stats.midDiameterMm, e.stats.shortDiameterMm);
                } else {
                    pw.printf("\"%s\",%s,,,,,%n", e.name, e.type);
                }
            }
            JOptionPane.showMessageDialog(this, "Exported to " + f.getName(), "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // Measurement UI update
    // =========================================================================

    private void updateMeasureResultUI() {
        if (canvas == null || measureResultArea == null) return;
        java.util.List<org.joml.Vector3f> mmPts = canvas.getMeasureMmPoints();
        GLCanvas.MeasurementMode mode = canvas.getMeasurementMode();
        StringBuilder sb = new StringBuilder();

        if (mode == GLCanvas.MeasurementMode.DISTANCE) {
            if (mmPts.size() >= 2) {
                float d = mmPts.get(0).distance(mmPts.get(1));
                sb.append(String.format("Distance: %.2f mm%n", d));
                sb.append(String.format("  Pt1: (%.1f, %.1f, %.1f) mm%n",
                        mmPts.get(0).x, mmPts.get(0).y, mmPts.get(0).z));
                sb.append(String.format("  Pt2: (%.1f, %.1f, %.1f) mm",
                        mmPts.get(1).x, mmPts.get(1).y, mmPts.get(1).z));
            } else if (mmPts.size() == 1) {
                sb.append(String.format("Pt1: (%.1f, %.1f, %.1f) mm%n",
                        mmPts.get(0).x, mmPts.get(0).y, mmPts.get(0).z));
                sb.append("Click a 2nd point to measure...");
            } else {
                sb.append("Click on a mesh surface to place first point.");
            }
        } else if (mode == GLCanvas.MeasurementMode.LINE_3D) {
            double total = 0;
            for (int i = 1; i < mmPts.size(); i++) {
                float d = mmPts.get(i - 1).distance(mmPts.get(i));
                sb.append(String.format("Seg.%d: %.2f mm%n", i, d));
                total += d;
            }
            if (mmPts.size() >= 2) {
                sb.append(String.format("Total: %.2f mm (%d pts)", total, mmPts.size()));
            } else {
                sb.append("Click on mesh surfaces to add line points.");
            }
        }
        measureResultArea.setText(sb.toString());
    }

    // =========================================================================
    // ROI → Mesh conversion (shared between Generate Mesh button and Compute Stats)
    // =========================================================================

    private MeshData convertRoiGroupToMesh(java.util.List<FreeFormRoi3D> rois, VolumeData standardVol) {
        if (rois == null || rois.isEmpty() || standardVol == null) return null;

        int w = standardVol.width, h = standardVol.height, d = standardVol.depth;
        double[] startIpp = standardVol.startIpp;
        double[] iop      = standardVol.iop;
        double[] stepZ    = standardVol.stepZ;

        byte[] bakedVolumeBytes = new byte[w * h * d];
        int index = 0;
        for (int z = 0; z < d; z++) {
            double zOffX = z * stepZ[0], zOffY = z * stepZ[1], zOffZ = z * stepZ[2];
            for (int y = 0; y < h; y++) {
                double yOffX = iop[3] * (y * standardVol.pixelSpacingY);
                double yOffY = iop[4] * (y * standardVol.pixelSpacingY);
                double yOffZ = iop[5] * (y * standardVol.pixelSpacingY);
                for (int x = 0; x < w; x++) {
                    double xOffX = iop[0] * (x * standardVol.pixelSpacingX);
                    double xOffY = iop[1] * (x * standardVol.pixelSpacingX);
                    double xOffZ = iop[2] * (x * standardVol.pixelSpacingX);
                    double px = startIpp[0] + xOffX + yOffX + zOffX;
                    double py = startIpp[1] + xOffY + yOffY + zOffY;
                    double pz = startIpp[2] + xOffZ + yOffZ + zOffZ;
                    for (FreeFormRoi3D roi : rois) {
                        if (roi.containsPhysicalPoint(px, py, pz)) {
                            bakedVolumeBytes[index] = (byte) 255;
                            break;
                        }
                    }
                    index++;
                }
            }
        }

        VolumeData meshVolume = new VolumeData(w, h, d, bakedVolumeBytes);
        meshVolume.pixelSpacingX  = standardVol.pixelSpacingX;
        meshVolume.pixelSpacingY  = standardVol.pixelSpacingY;
        meshVolume.sliceThickness = standardVol.sliceThickness;
        meshVolume.minVal = 0;
        meshVolume.maxVal = 255;
        return MarchingCubes.generateMesh(meshVolume, 127.5f);
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private void updateSlices(GLCanvas c, JSlider sx, JSlider sy, JSlider sz) {
        c.setSlicePos(sx.getValue() / 100.0f, sy.getValue() / 100.0f, sz.getValue() / 100.0f);
    }

    private void setComponentsEnabled(boolean enabled, java.awt.Component... comps) {
        for (java.awt.Component c : comps) c.setEnabled(enabled);
    }

    private void jumpEndoCamera(JSlider sliderEndoU, float newU) {
        EndoCamera cam = canvas.getEndoCamera();
        float oldU = cam.getU();
        if (Math.abs(newU - oldU) > 1e-6f)
            canvas.getUndoManager().addCommand(new EndoCommands.SetCameraUCommand(cam, newU));
        cam.resetLook();
        sliderEndoU.setValue(Math.round(newU * 100));
        canvas.repaint();
    }

    // =========================================================================
    // Inner classes
    // =========================================================================

    // ── SceneObjectTableModel ─────────────────────────────────────────────────
    static class SceneObjectTableModel extends AbstractTableModel {

        static final int COL_VISIBLE = 0;
        static final int COL_NAME    = 1;
        static final int COL_TYPE    = 2;
        static final int COL_COLOR   = 3;

        private static final String[] HEADERS = {"", "Name", "Type", "■"};
        private static final Class<?>[] TYPES  = {Boolean.class, String.class, String.class,
                                                   java.awt.Color.class};

        static class Entry {
            String name;
            String type;
            boolean visible = true;
            java.awt.Color color;
            MeshMeasureResult stats;
        }

        private final java.util.List<Entry> entries = new java.util.ArrayList<>();

        @Override public int getRowCount()    { return entries.size(); }
        @Override public int getColumnCount() { return 4; }
        @Override public String getColumnName(int col) { return HEADERS[col]; }
        @Override public Class<?> getColumnClass(int col) { return TYPES[col]; }
        @Override public boolean isCellEditable(int row, int col) {
            return col == COL_VISIBLE || col == COL_COLOR;
        }

        @Override public Object getValueAt(int row, int col) {
            Entry e = entries.get(row);
            switch (col) {
                case COL_VISIBLE: return e.visible;
                case COL_NAME:    return e.name;
                case COL_TYPE:    return e.type;
                case COL_COLOR:   return e.color;
                default:          return null;
            }
        }

        @Override public void setValueAt(Object val, int row, int col) {
            Entry e = entries.get(row);
            if (col == COL_VISIBLE && val instanceof Boolean) {
                e.visible = (Boolean) val; fireTableCellUpdated(row, col);
            } else if (col == COL_COLOR && val instanceof java.awt.Color) {
                e.color = (java.awt.Color) val; fireTableCellUpdated(row, col);
            }
        }

        public Entry getEntry(int row) {
            return (row >= 0 && row < entries.size()) ? entries.get(row) : null;
        }

        public void setStats(int row, MeshMeasureResult result) {
            if (row >= 0 && row < entries.size()) {
                entries.get(row).stats = result;
                fireTableRowsUpdated(row, row);
            }
        }

        public void rebuildFrom(java.util.List<String> roiNames,
                                java.util.List<java.awt.Color> roiColors,
                                java.util.Map<String, java.awt.Color> meshMap) {
            // Preserve computed stats and visibility across rebuilds
            java.util.Map<String, MeshMeasureResult> savedStats = new java.util.HashMap<>();
            java.util.Map<String, Boolean>            savedVis   = new java.util.HashMap<>();
            for (Entry e : entries) {
                savedStats.put(e.type + ":" + e.name, e.stats);
                savedVis  .put(e.type + ":" + e.name, e.visible);
            }
            entries.clear();

            if (roiNames != null) {
                for (int i = 0; i < roiNames.size(); i++) {
                    Entry e = new Entry();
                    e.name  = roiNames.get(i);
                    e.type  = "ROI";
                    e.color = (roiColors != null && i < roiColors.size())
                            ? roiColors.get(i) : java.awt.Color.CYAN;
                    e.stats = savedStats.get("ROI:" + e.name);
                    Boolean v = savedVis.get("ROI:" + e.name);
                    e.visible = (v != null) ? v : true;
                    entries.add(e);
                }
            }
            if (meshMap != null) {
                for (java.util.Map.Entry<String, java.awt.Color> me : meshMap.entrySet()) {
                    Entry e = new Entry();
                    e.name  = me.getKey();
                    e.type  = "Mesh";
                    e.color = me.getValue();
                    e.stats = savedStats.get("Mesh:" + e.name);
                    Boolean v = savedVis.get("Mesh:" + e.name);
                    e.visible = (v != null) ? v : true;
                    entries.add(e);
                }
            }
            fireTableDataChanged();
        }
    }

    // ── Color cell renderer ───────────────────────────────────────────────────
    private static class ColorCellRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, "", isSelected, hasFocus, row, col);
            if (value instanceof java.awt.Color) {
                label.setBackground((java.awt.Color) value);
                label.setOpaque(true);
            }
            return label;
        }
    }

    // ── Color cell editor (opens JColorChooser) ───────────────────────────────
    private static class ColorCellEditor extends AbstractCellEditor implements TableCellEditor {
        private java.awt.Color currentColor;
        private final JButton button = new JButton();

        ColorCellEditor() {
            button.setOpaque(true);
            button.setBorderPainted(false);
            button.addActionListener(e -> {
                java.awt.Color chosen = javax.swing.JColorChooser.showDialog(
                        button, "Select Color", currentColor);
                if (chosen != null) {
                    currentColor = chosen;
                    button.setBackground(chosen);
                    fireEditingStopped();
                } else {
                    fireEditingCanceled();
                }
            });
        }

        @Override public Object getCellEditorValue() { return currentColor; }

        @Override
        public java.awt.Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int col) {
            currentColor = (value instanceof java.awt.Color) ? (java.awt.Color) value : java.awt.Color.GRAY;
            button.setBackground(currentColor);
            return button;
        }
    }

    // ── ScrollableControlPanel ────────────────────────────────────────────────
    private static class ScrollableControlPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        ScrollableControlPanel(java.awt.LayoutManager layout) { super(layout); }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return o == SwingConstants.VERTICAL ? r.height : r.width;
        }
        @Override public boolean getScrollableTracksViewportWidth()  { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
