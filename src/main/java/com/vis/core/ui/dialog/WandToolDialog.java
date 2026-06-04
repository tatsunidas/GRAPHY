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
 * Wandツールの設定を管理するダイアログ。
 * スライダー変更時に、対象ROIのプロパティを更新し RoiObjListener.MODIFIED をブロードキャストします。
 */
public class WandToolDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    
    private static WandToolDialog instance;
    
    // ★追加: 現在Wandで操作・調整対象となっているROI
    private RoiObj targetRoi;
    
    private double tolerance = 0.0;
    @SuppressWarnings("unused")
	private int mode = Wand.LEGACY_MODE;
    @SuppressWarnings("unused")
    private boolean smooth = false;
    private boolean wasOkPressed = false;
    
    private boolean isAdjusting = false;
    
    public final String MODE_LEGACY = "Legacy";
    public final String MODE_4connected = "4-connected";
    public final String MODE_8connected = "8-connected";

    private JSlider toleranceSlider;
    private JFormattedTextField toleranceField;
    private JComboBox<String> modeComboBox;
    private JCheckBox smoothCheckBox;
    private JButton okButton;
    private JButton cancelButton;

    private double minTolerance = 0.0;
    private double maxTolerance = 255.0;
    private final int SLIDER_MAX = 1000;
    
    private WandToolDialog(Frame owner, String title) {
        super(owner, title, false);
        initUI();
        addListeners();
        pack();
        setLocationRelativeTo(owner);
        
        RoiObj.addRoiListener(new RoiObjListener() {
            @Override
            public void roiModified(SlideGlass slide, int actionId) {
                // 自身が管理しているスライドのMODIFIEDイベントのみ処理
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
     * CanvasGlassなどで新規にWandROIが作成された際、調整対象としてセットします。
     */
    public void setTargetRoi(RoiObj roi) {
        this.targetRoi = roi;
    }

    /**
     * 値が変更された際に対象ROIのプロパティを書き換え、ネイティブイベントを発火
     */
    private void notifySettingsChanged() {
        if (targetRoi != null) {
        	SlideGlass sg = targetRoi.getSlideGlass();
        	if(sg == null) {
        		return;
        	}
        	int lastX = sg.lastPressedX;
        	int lastY = sg.lastPressedY;
        	sg.executeWand(lastX, lastY);
        	
            targetRoi.notifyListeners(RoiObjListener.MODIFIED);
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

        // Tolerance
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Tolerance:"), gbc);

        toleranceSlider = new JSlider(0, SLIDER_MAX, toleranceToSlider(tolerance));
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(toleranceSlider, gbc);

        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(2);
        toleranceField = new JFormattedTextField(format);
        toleranceField.setValue(tolerance);
        toleranceField.setColumns(5);
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(toleranceField, gbc);

        // Mode
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Mode:"), gbc);

        String[] modes = { MODE_LEGACY, MODE_4connected, MODE_8connected };
        modeComboBox = new JComboBox<>(modes);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(modeComboBox, gbc);

        // Smooth CheckBox
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2;
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

    public int getWandMode() {
        String selectedMode = (String) modeComboBox.getSelectedItem();
        if (MODE_4connected.equals(selectedMode)) {
            return Wand.FOUR_CONNECTED;
        } else if (MODE_8connected.equals(selectedMode)) {
            return Wand.EIGHT_CONNECTED;
        } else {
            return Wand.LEGACY_MODE;
        }
    }

    public boolean isSmooth() {
        return smoothCheckBox.isSelected();
    }
}