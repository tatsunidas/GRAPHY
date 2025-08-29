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
package com.vis.core.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.ImageProcessor;

/**
 * 
 * @author tatsunidas
 *
 */
public class WandToolDialog extends JDialog {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	// --- 設定値を保持するフィールド ---
    private double tolerance;
    private String mode;
    private boolean smooth;
    private boolean wasOkPressed = false;

    // --- GUIコンポーネント ---
    private JSlider toleranceSlider;
    private JFormattedTextField toleranceField;
    private JComboBox<String> modeComboBox;
    private JCheckBox smoothCheckBox;
    private JButton okButton;
    private JButton cancelButton;

    // --- Toleranceの範囲とスライダーの精度を定義 ---
    private double minTolerance;
    private double maxTolerance;
    private final int SLIDER_MAX = 1000; // スライダーの内部的な最大値（精度を決定）

    private Praparat prap;
    
    private AWTEventListener globalMouseListener;

    /**
     * ダイアログのコンストラクタ
     * @param owner 親フレーム
     * @param title ダイアログのタイトル
     * @param initialTolerance Toleranceの初期値
     * @param minTolerance Toleranceの最小値
     * @param maxTolerance Toleranceの最大値
     * @throws Exception 
     */
	public WandToolDialog(Frame owner, String title, Praparat prap) throws Exception {
		super(owner, title, true/* modal */);
		this.prap = prap;
		// --- GUIの初期化 ---
		initUI();
		addListeners();
		initGlobalMouseListener();
		pack(); // コンポーネントのサイズに合わせてウィンドウサイズを調整
		setLocationRelativeTo(owner); // 親フレームの中央に表示
		setVisible(true);
	}

    /**
     * GUIコンポーネントを初期化し、パネルに配置する
     */
	private void initUI() {
		setLayout(new BorderLayout());

		// --- メインの入力パネル ---
		JPanel mainPanel = new JPanel(new GridBagLayout());
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5); // コンポーネント間の余白
		gbc.anchor = GridBagConstraints.WEST;

		// 1行目: Tolerance
		gbc.gridx = 0;
		gbc.gridy = 0;
		mainPanel.add(new JLabel("Tolerance:"), gbc);

		toleranceSlider = new JSlider(0, SLIDER_MAX, toleranceToSlider(tolerance));
		gbc.gridx = 1;
		gbc.weightx = 1.0; // ウィンドウ幅の変更に追従
		gbc.fill = GridBagConstraints.HORIZONTAL;
		mainPanel.add(toleranceSlider, gbc);

		NumberFormat format = NumberFormat.getNumberInstance();
		format.setMaximumFractionDigits(2); // 小数点以下2桁まで
		toleranceField = new JFormattedTextField(format);
		toleranceField.setValue(tolerance);
		toleranceField.setColumns(5); // テキストフィールドの幅
		gbc.gridx = 2;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		mainPanel.add(toleranceField, gbc);

		// 2行目: Mode
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		mainPanel.add(new JLabel("Mode:"), gbc);

		String[] modes = { "8-connected", "4-connected", "Legacy" };
		modeComboBox = new JComboBox<>(modes);
		gbc.gridx = 1;
		gbc.gridwidth = 2; // 2列分を占有
		gbc.fill = GridBagConstraints.HORIZONTAL;
		mainPanel.add(modeComboBox, gbc);

		// 3行目: Smooth if thresholded
		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.gridwidth = 2;
		smoothCheckBox = new JCheckBox("Smooth if thresholded");
		mainPanel.add(smoothCheckBox, gbc);

		// --- ボタンパネル ---
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		okButton = new JButton("OK");
		cancelButton = new JButton("Cancel");
		buttonPanel.add(cancelButton);
		buttonPanel.add(okButton);

		// --- 全体をフレームに追加 ---
		add(mainPanel, BorderLayout.CENTER);
		add(buttonPanel, BorderLayout.SOUTH);
	}

	/**
	 * イベントリスナーを設定する
	 */
	private void addListeners() {
		// スライダーが動かされた時の処理
		toleranceSlider.addChangeListener(e -> {
			// スライダーの値からTolerance値を計算
			double newTolerance = sliderToTolerance(toleranceSlider.getValue());
			// テキストフィールドに反映
			toleranceField.setValue(newTolerance);
		});

		// テキストフィールドでEnterが押された、またはフォーカスが外れた時の処理
		toleranceField.addPropertyChangeListener("value", evt -> {
			// テキストフィールドの値を取得
			double newTolerance = ((Number) toleranceField.getValue()).doubleValue();
			// 値を範囲内に補正
			if (newTolerance < minTolerance)
				newTolerance = minTolerance;
			if (newTolerance > maxTolerance)
				newTolerance = maxTolerance;
			// スライダーに反映
			toleranceSlider.setValue(toleranceToSlider(newTolerance));
		});

		// OKボタンが押された時の処理
		okButton.addActionListener(e -> {
			// 現在のGUIの状態から設定値を取得
			this.tolerance = ((Number) toleranceField.getValue()).doubleValue();
			this.mode = (String) modeComboBox.getSelectedItem();
			this.smooth = smoothCheckBox.isSelected();
			this.wasOkPressed = true;
			// ダイアログを閉じる
			dispose();
		});

		// Cancelボタンが押された時の処理
		cancelButton.addActionListener(e -> {
			this.wasOkPressed = false;
			// ダイアログを閉じる
			dispose();
		});
	}

	// --- 値の変換メソッド ---
	/**
	 * double型のTolerance値をJSlider用の整数値に変換する
	 */
	private int toleranceToSlider(double toleranceValue) {
		return (int) Math.round(((toleranceValue - minTolerance) / (maxTolerance - minTolerance)) * SLIDER_MAX);
	}

	/**
	 * JSliderの整数値をdouble型のTolerance値に変換する
	 */
	private double sliderToTolerance(int sliderValue) {
		return minTolerance + ((double) sliderValue / SLIDER_MAX) * (maxTolerance - minTolerance);
	}
	
    private void initGlobalMouseListener() {
        this.globalMouseListener = event -> {
            if (!(event instanceof MouseEvent)) {
                return;
            }
            MouseEvent mouseEvent = (MouseEvent) event;
            
            if (mouseEvent.getID() == MouseEvent.MOUSE_MOVED) {
            	Component source = mouseEvent.getComponent();
            	if (!SwingUtilities.isDescendingFrom(source, WandToolDialog.this)) {
            		System.out.println(source.getClass().getName());
            		focusTo(source);
            	}
            	return;
            }

            if (mouseEvent.getID() == MouseEvent.MOUSE_CLICKED && SwingUtilities.isLeftMouseButton(mouseEvent)) {
                Component source = mouseEvent.getComponent();
                if (!SwingUtilities.isDescendingFrom(source, WandToolDialog.this)) {
                    Point screenPoint = mouseEvent.getLocationOnScreen();
                    System.out.println("ダイアログ外のウィンドウがクリックされました！");
                    System.out.println("コンポーネント内の座標: " + mouseEvent.getPoint());
//                    doWand(screenPoint);
                }
            }
        };
    }
	
	private void doWand() {
		ArrayList<RoiObj> rois = prap.getCurrentSlide().getRois();
		if(rois == null || rois.size()==0) {
			return;
		}
		for(RoiObj ro : rois) {
			//use first selected roi
			if(ro.isArea() && ro.isSelected()) {
				SlideGlass sg = ro.getSlideGlass();
				sg.addRoi(ro);//update if already exists.
				
				break;
			}
		}
	}
	
	public void focusTo(Component prap) {
		
		if((prap instanceof Praparat) == false) {
			return;
		}
		
		if(this.prap == (Praparat)prap) {
			return;
		}
		
		this.prap = (Praparat)prap;
		
		SlideGlass sg = this.prap.getCurrentSlide();
		ImageProcessor ip = sg.getOriginalImage().getProcessor();
		
		this.minTolerance = ip.minValue();
		this.maxTolerance = ip.maxValue();
		this.tolerance = minTolerance;
		
		// スライダーに反映
		toleranceSlider.setValue(toleranceToSlider(this.tolerance));
	}

	// --- 外部から値を取得するためのメソッド ---
	public boolean wasOkPressed() {
		return wasOkPressed;
	}

	public double getTolerance() {
		return tolerance;
	}

	public String getMode() {
		return mode;
	}

	public boolean isSmooth() {
		return smooth;
	}
}
