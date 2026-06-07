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
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.ui.dialog;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import com.vis.core.log.Log;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.ui.listener.RoiObjListener;
import ij.gui.Wand;

/**
 * Wandツールの設定を管理するダイアログ（3D対応拡張版）。
 * スライダーやモード変更時に、対象ROIのプロパティを更新し 2D/3D Wandを再トリガーします。
 */
public class WandToolDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    
    private static WandToolDialog instance;
    
    // 現在Wandで操作・調整対象となっているROI
    private RoiObj targetRoi;
    
    private double tolerance = 0.0;
    
    @SuppressWarnings("unused")
	private int mode = Wand.LEGACY_MODE;
    
    @SuppressWarnings("unused")
	private boolean smooth = false;
    private boolean wasOkPressed = false;
    
    private boolean isAdjusting = false;
    
    // 次元の定数文字列
    public final static String DIM_2D = "2D Mode";
    public final static String DIM_3D = "3D Mode";

    // 2D用接続性の定数文字列
    public final String MODE_LEGACY = "Legacy";
    public final String MODE_4connected = "4-connected";
    public final String MODE_8connected = "8-connected";

    // 3D用接続性の定数文字列
    public final static String MODE_3D_6 = "6-connected (Faces)";
    public final static String MODE_3D_12 = "12-connected (Edges)";
    public final static String MODE_3D_8 = "8-connected (Corners)";
    public final static String MODE_3D_26 = "26-connected (All)";

    // 3D用の独自接続性定数 (幅優先探索エンジンで使用)
    public final static int THREE_D_6 = 6;
    public final static int THREE_D_12 = 12;
    public final static int THREE_D_8 = 8;
    public final static int THREE_D_26 = 26;

    private JComboBox<String> dimensionComboBox; // 2D/3D 切り替え用
    private JSlider toleranceSlider;
    private JFormattedTextField toleranceField;
    private JComboBox<String> modeComboBox;
    private JCheckBox smoothCheckBox;
    private JButton okButton;
    private JButton cancelButton;

    private double minTolerance = 0.0;
    private double maxTolerance = 255.0;
    private final int SLIDER_MAX = 1000;
    
    /*
     * SlideGlassのlastPressedXYは, slideが切り替わるとリセットされるため、
     * wand時はここで保持しておく
     */
    private int seedImageX = -1;
    private int seedImageY = -1;
    private int seedImageZ = -1;
    
    private WandToolDialog(Frame owner, String title) {
        super(owner, title, false);
        initUI();
        addListeners();
        pack();
        setLocationRelativeTo(owner);
        
        RoiObj.addRoiListener(new RoiObjListener() {
            @Override
            public void roiModified(SlideGlass slide, int actionId) {
                if (actionId == RoiObjListener.MODIFIED) {
                    slide.repaintCanvasGlass();
                }
            }
        });
    }
    
    public static synchronized WandToolDialog getInstance(Frame owner, String title) {
        if (instance == null) {
            try {
                instance = new WandToolDialog(owner, title);
            } catch (Exception e) {
                Log.logger.log(Level.SEVERE, "WandToolDialogの作成に失敗しました。");
                return null;
            }
        }
        instance.setVisible(true);
        instance.toFront();
        return instance;
    }
    
    /**
     * 新規にWandROIが作成された際、調整対象としてセットします。
     */
    public void setTargetRoi(RoiObj roi) {
        this.targetRoi = roi;
    }

    /**
     * 値やモードが変更された際に対象ROIのプロパティを書き換え、再探索を実行
     */
    private void notifySettingsChanged() {
    	
    	com.vis.core.log.Log.logger.info("[WandToolDialog] notifySettingsChanged called. targetRoi = " + (targetRoi != null ? "EXISTS" : "NULL"));
    	
    	if (targetRoi != null ) {
            SlideGlass sg = targetRoi.getSlideGlass();
            if (sg == null) {
                return;
            }
            int lastX = sg.lastPressedX;
            int lastY = sg.lastPressedY;
            
            // ==========================================================
            // 2D/3Dモード切り替え時の英語警告ポップアップとロールバック処理
            // ==========================================================
            boolean currentRoiIs3D = (targetRoi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D);
            boolean uiIs3D = is3DMode();
            
            // 現在のROIの次元と、UI（コンボボックス）の設定に不一致がある場合（＝モードが切り替えられた）
            if (currentRoiIs3D != uiIs3D) {
                int response = javax.swing.JOptionPane.showConfirmDialog(
                    this,
                    "Changing the mode will delete the existing ROI. Do you want to proceed?",
                    "Change Mode",
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE
                );
                
             // ユーザーが「NO（変更をキャンセル）」を選択した場合
                if (response != javax.swing.JOptionPane.YES_OPTION) {
                    isAdjusting = true; // リスナーの無限連鎖を防ぐためにフラグを立てる
                    try {
                        if (currentRoiIs3D) {
                            // UIを3Dモードの状態に巻き戻す
                            dimensionComboBox.setSelectedItem(DIM_3D);
                            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modeComboBox.getModel();
                            model.removeAllElements();
                            model.addElement(MODE_3D_6);
                            model.addElement(MODE_3D_12);
                            model.addElement(MODE_3D_8);
                            model.addElement(MODE_3D_26);
                            modeComboBox.setSelectedIndex(0); // ★ 修正 2: 明示的に先頭を選択
                        } else {
                            // UIを2Dモードの状態に巻き戻す
                            dimensionComboBox.setSelectedItem(DIM_2D);
                            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modeComboBox.getModel();
                            model.removeAllElements();
                            model.addElement(MODE_LEGACY);
                            model.addElement(MODE_4connected);
                            model.addElement(MODE_8connected);
                            modeComboBox.setSelectedIndex(0); // ★ 修正 2: 明示的に先頭を選択
                        }
                    } finally {
                        isAdjusting = false; // フラグを解除
                    }
                    return; // 既存のROIを維持して処理を中断
                }
            }

            // ユーザーが承認（YES）した、または通常のパラメータ（Tolerance等）変更の場合は古いプレビューを削除
            if (currentRoiIs3D) {
                sg.getPraparat().removeRoi3D(targetRoi);
            }
            sg.deleteRoi(targetRoi); 
            
         // 参照を一旦クリア（探索メソッド内で再生成・セットされるため）
            targetRoi = null;

            // 3Dモードかどうかで実行メソッドを分岐
            if (is3DMode()) {
                // ★ 変更: UIの座標ではなく、記憶した絶対座標を渡す
                if (this.seedImageX != -1) {
                    sg.executeWand3D(this.seedImageX, this.seedImageY, this.seedImageZ);
                }
            } else {
                sg.executeWand(lastX, lastY);
            }
        }
    }

    public void setToleranceRange(double min, double max) {
        this.minTolerance = min;
        this.maxTolerance = max;
        
        if (this.tolerance < min) this.tolerance = min;
        if (this.tolerance > max) this.tolerance = max;
        
        isAdjusting = true;
        try {
            toleranceField.setValue(this.tolerance);
            toleranceSlider.setValue(toleranceToSlider(this.tolerance));
        } finally {
            isAdjusting = false;
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Dimension (2D / 3D) Mode
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Dimension:"), gbc);

        String[] dimensions = { DIM_2D, DIM_3D };
        dimensionComboBox = new JComboBox<>(dimensions);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(dimensionComboBox, gbc);

        // Tolerance
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Tolerance:"), gbc);

        toleranceSlider = new JSlider(0, SLIDER_MAX, toleranceToSlider(tolerance));
        toleranceSlider.setValue(50);//default
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(toleranceSlider, gbc);

        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(2);
        toleranceField = new JFormattedTextField(format);
        toleranceField.setValue(tolerance);
        toleranceField.setColumns(5);
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(toleranceField, gbc);

        // Mode (Connectivity)
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Connectivity:"), gbc);

        // 初期状態は2D用のコンボモデルをセット
        String[] initialModes = { MODE_LEGACY, MODE_4connected, MODE_8connected };
        modeComboBox = new JComboBox<>(initialModes);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(modeComboBox, gbc);

        // Smooth CheckBox
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        smoothCheckBox = new JCheckBox("Smooth if thresholded");
        smoothCheckBox.setSelected(false);
        smoothCheckBox.setEnabled(false);
        mainPanel.add(smoothCheckBox, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        okButton = new JButton("OK");
        cancelButton = new JButton("Cancel");
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addListeners() {
        // 次元コンボボックス切り替え時の動的制御リスナー
    	// 次元コンボボックス切り替え時の動的制御リスナー
        dimensionComboBox.addActionListener(e -> {
            if (isAdjusting) return;
            
            String selectedDim = (String) dimensionComboBox.getSelectedItem();
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modeComboBox.getModel();
            model.removeAllElements();
            
            if (DIM_3D.equals(selectedDim)) {
                model.addElement(MODE_3D_6);
                model.addElement(MODE_3D_12);
                model.addElement(MODE_3D_8);
                model.addElement(MODE_3D_26);
                smoothCheckBox.setSelected(false);
                smoothCheckBox.setEnabled(false);
            } else {
                model.addElement(MODE_LEGACY);
                model.addElement(MODE_4connected);
                model.addElement(MODE_8connected);
                smoothCheckBox.setEnabled(false);
            }
            
            // ★ 追加: リスト入れ替え後、必ず先頭の要素を選択状態にして Null を防ぐ
            isAdjusting = true;
            try {
                modeComboBox.setSelectedIndex(0);
            } finally {
                isAdjusting = false;
            }
            
            notifySettingsChanged();
        });

        toleranceSlider.addChangeListener(e -> {
            if (isAdjusting) return;
            isAdjusting = true;
            try {
                double newTolerance = sliderToTolerance(toleranceSlider.getValue());
                toleranceField.setValue(newTolerance);
                this.tolerance = newTolerance;
            } finally {
                isAdjusting = false;
            }
            notifySettingsChanged();
        });

        toleranceField.addPropertyChangeListener("value", evt -> {
            if (isAdjusting) return;
            isAdjusting = true;
            try {
                double newTolerance = ((Number) toleranceField.getValue()).doubleValue();
                if (newTolerance < minTolerance) newTolerance = minTolerance;
                if (newTolerance > maxTolerance) newTolerance = maxTolerance;

                toleranceSlider.setValue(toleranceToSlider(newTolerance));
                this.tolerance = newTolerance;
            } finally {
                isAdjusting = false;
            }
            notifySettingsChanged();
        });
        
        modeComboBox.addActionListener(e -> {
            if (isAdjusting) return; // ★ 修正 1: 自動書き換え中の二重発火をシャットアウト
            this.mode = getWandMode();
            notifySettingsChanged();
        });

        okButton.addActionListener(e -> {
            this.tolerance = getTolerance();
            this.mode = getWandMode();
            this.smooth = smoothCheckBox.isSelected();
            this.wasOkPressed = true;
            dispose();
        });

        cancelButton.addActionListener(e -> {
            this.wasOkPressed = false;
            dispose();
        });
    }

    private int toleranceToSlider(double toleranceValue) {
        if (maxTolerance - minTolerance == 0) return 0;
        return (int) Math.round(((toleranceValue - minTolerance) / (maxTolerance - minTolerance)) * SLIDER_MAX);
    }

    private double sliderToTolerance(int sliderValue) {
        return minTolerance + ((double) sliderValue / SLIDER_MAX) * (maxTolerance - minTolerance);
    }
    
    /**
     * マウスクリック時にシード座標をダイアログに記憶させます。
     */
    public void setSeedImageCoords(int x, int y, int z) {
        this.seedImageX = x;
        this.seedImageY = y;
        this.seedImageZ = z;
    }
    public int getSeedImageX() { return seedImageX; }
    public int getSeedImageY() { return seedImageY; }
    public int getSeedImageZ() { return seedImageZ; }
    
    @Override
    public void dispose() {
        super.dispose();
        instance = null;
    }

    public boolean wasOkPressed() { return wasOkPressed; }

    public double getTolerance() {
        Object v = toleranceField.getValue();
        if (v == null) return 0.5d;
        try {
            return ((Number) v).doubleValue();
        } catch (NumberFormatException e) {
            return 0.5d;
        }
    }

    /**
     * 現在が3Dモードが選択されているかを外部から参照するゲッター
     */
    public boolean is3DMode() {
        return DIM_3D.equals(dimensionComboBox.getSelectedItem());
    }

    /**
     * 選択されている次元と近傍定義に応じて正しい整数定数を返します。
     */
    public int getWandMode() {
        String selectedMode = (String) modeComboBox.getSelectedItem();
        if (selectedMode == null) return Wand.LEGACY_MODE;

        if (is3DMode()) {
            if (MODE_3D_6.equals(selectedMode)) {
                return THREE_D_6;
            } else if (MODE_3D_12.equals(selectedMode)) {
                return THREE_D_12;
            } else if (MODE_3D_8.equals(selectedMode)) {
                return THREE_D_8;
            } else {
                return THREE_D_26;
            }
        } else {
            if (MODE_4connected.equals(selectedMode)) {
                return Wand.FOUR_CONNECTED;
            } else if (MODE_8connected.equals(selectedMode)) {
                return Wand.EIGHT_CONNECTED;
            } else {
                return Wand.LEGACY_MODE;
            }
        }
    }

    public boolean isSmooth() {
        return smoothCheckBox.isSelected();
    }
}