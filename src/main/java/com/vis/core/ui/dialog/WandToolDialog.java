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

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
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
import javax.swing.SwingUtilities;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;
import com.vis.core.view.D2.roi.RoiConverter;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.glasses.EventGlass;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.plugin.WandToolOptions;
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
	
	// シングルトンインスタンスを保持するstaticフィールド
    private static WandToolDialog instance;
	
	// --- 設定値を保持するフィールド ---
    private double tolerance = 0.;
    private int mode = Wand.LEGACY_MODE;
    private boolean smooth = false;
    private boolean wasOkPressed = false;
    // UIコンポーネントの相互更新時のイベントループを防ぐためのフラグ ---
    private boolean isAdjusting = false;
    
    private final String MODE_LEGACY = "Legacy";
    private final String MODE_4connected = "4-connected";
    private final String MODE_8connected = "8-connected";

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
	private WandToolDialog(Frame owner, String title) throws Exception {
		super(owner, title, false/* modal, if true, block other windows/components */);
		// --- GUIの初期化 ---
		initUI();
		addListeners();
		initGlobalMouseListener();
		pack(); // コンポーネントのサイズに合わせてウィンドウサイズを調整
		setLocationRelativeTo(owner); // 親フレームの中央に表示
	}
	
	/**
     * ダイアログのシングルトンインスタンスを取得するメソッド
     * 既にインスタンスが存在し、表示されている場合はそれを返し、そうでなければ新規作成する
     * @param owner 親フレーム
     * @param title ダイアログのタイトル
     * @return WandToolDialogのインスタンス
     */
    public static synchronized WandToolDialog getInstance(Frame owner, String title) {
        if (instance == null) {
            try {
                instance = new WandToolDialog(owner, title);
            } catch (Exception e) {
                Log.message(Level.SEVERE, "WandToolDialogの作成に失敗しました。");
                return null;
            }
        }
        instance.setVisible(true);
        instance.toFront();
        return instance;
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

		String[] modes = { MODE_LEGACY, MODE_4connected, MODE_8connected };
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
		smoothCheckBox.setSelected(false);
		smoothCheckBox.setEnabled(false);//TODO 20250830
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
		// isAdjustingフラグを使用してイベントの連鎖を防止
		// スライダーが動かされた時の処理
		toleranceSlider.addChangeListener(e -> {
			if (isAdjusting)
				return; // 他のコンポーネントからの更新中は処理しない
			isAdjusting = true;
			try {
				// スライダーの値からTolerance値を計算
				double newTolerance = sliderToTolerance(toleranceSlider.getValue());
				// テキストフィールドに反映
				toleranceField.setValue(newTolerance);
			} finally {
				isAdjusting = false; // フラグをリセット
			}
		});

		// テキストフィールドでEnterが押された、またはフォーカスが外れた時の処理
		toleranceField.addPropertyChangeListener("value", evt -> {
			if (isAdjusting)
				return; // 他のコンポーネントからの更新中は処理しない
			isAdjusting = true;
			try {
				// テキストフィールドの値を取得
				double newTolerance = ((Number) toleranceField.getValue()).doubleValue();

				// 値を範囲内に補正
				boolean corrected = false;
				if (newTolerance < minTolerance) {
					newTolerance = minTolerance;
					corrected = true;
				}
				if (newTolerance > maxTolerance) {
					newTolerance = maxTolerance;
					corrected = true;
				}

				// スライダーに反映
				toleranceSlider.setValue(toleranceToSlider(newTolerance));

				// 値が範囲外だった場合、補正した値をテキストフィールド自身にも再設定
				if (corrected) {
					toleranceField.setValue(newTolerance);
				}

			} finally {
				isAdjusting = false; // フラグをリセット
			}
		});

		// OKボタンが押された時の処理
		okButton.addActionListener(e -> {
			// 現在のGUIの状態から設定値を取得
			this.tolerance = getTolerance();
			this.mode = getMode();
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
        if (maxTolerance - minTolerance == 0) return 0;
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
			Component source = mouseEvent.getComponent();

			// イベントソースがこのダイアログ自身またはその部品なら何もしない
			if (SwingUtilities.isDescendingFrom(source, WandToolDialog.this)) {
				return;
			}

			if (mouseEvent.getID() == MouseEvent.MOUSE_MOVED) {
				//System.out.println(source.getClass().getName());
				focusTo(source);
				return;
			}

			if (mouseEvent.getID() == MouseEvent.MOUSE_CLICKED && SwingUtilities.isLeftMouseButton(mouseEvent)) {
//				Point screenPoint = mouseEvent.getLocationOnScreen();//monitor display coodinates.
				Point displayCoordPointOnSlideGlass = mouseEvent.getPoint();
//				System.out.println(source.getClass().getName());//EventGlass in SlideGlass.
              doWand(displayCoordPointOnSlideGlass);
			}
		};
	}
	
	private void doWand(java.awt.Point mousePoint/*onDisplayImage*/) {
		if (this.prap == null) {
			return;
		}
		int toolType = prap.getCurrentViewerToolType();//from viewer2d
		if(toolType != Viewer2DToolBar.Wand) {
			return;
		}
		
		// create new
		SlideGlass sg = prap.getCurrentSlide();
		RoiObj r = wand(sg, mousePoint);
		if(r != null) {
			// init uids and roiid
			r.setSlideGlass(sg);
			sg.addRoi(r);// update if already exists.
		}
	}
	
	private RoiObj wand(SlideGlass sg, Point p) {
		// 処理対象の画像を取得
		ImagePlus imp = sg.getOriginalImage();
		if (imp == null) {
			Log.message(Level.SEVERE, "Cannot load imageplus from current slideglass... return null.");
			return null;
		}
		// 画像のプロセッサーを取得
		ImageProcessor ip = imp.getProcessor();
		// Wand選択を実行したい座標を指定
		int x = sg.offScreenX(p.x);
		int y = sg.offScreenY(p.y);
		
		ArrayList<RoiObj> rois = this.prap.getCurrentSlide().getRois();
		if (rois != null && rois.size() > 0) {
			// search point contained roi
			for (RoiObj ro : rois) {
				// use first found roi
				if (ro.isArea() && ro.contains(x, y)/* && ro.isSelected() */) {
					// まず、ROIをワンドでつくる
					Wand wand = new Wand(ip);
					// 指定した座標から輪郭を自動検出
					wand.autoOutline(x, y, getTolerance()/*許容差*/, getMode());
					int n = wand.npoints;
					if(n < 1) {
						System.out.println("Roi was not created by Wand. return null.");
						return null;
					}
					int[] xp = wand.xpoints;
					int[] yp = wand.ypoints;
					Roi roi = new PolygonRoi(xp, yp, n, PolygonRoi.TRACED_ROI);
					RoiObj r = new RoiConverter().convert2RoiObj(roi);
					// Contextを合わせる
					// color, line width
					r.copyAttributes(ro);
					// UIDs and RoiID
					HashMap<ContextKey, String> uids = ro.getUIDs();
					for (ContextKey k : uids.keySet()) {
						r.setProperty(k, uids.get(k));
					}
					return r;
				}
			}
		}

		// Wandオブジェクトを生成
		Wand wand = new Wand(ip);

		// 指定した座標から輪郭を自動検出
		wand.autoOutline(x, y, getTolerance()/*許容差*/, getMode());

		int n = wand.npoints;
		if(n < 1) {
			System.out.println("Roi was not created by Wand. return null.");
			return null;
		}
		int[] xp = wand.xpoints;
		int[] yp = wand.ypoints;
		Roi roi = new PolygonRoi(xp, yp, n, PolygonRoi.TRACED_ROI);
		return new RoiConverter().convert2RoiObj(roi);
	}
	
	public void focusTo(Component eventGlass) {
		
		if(eventGlass == null) {
			this.prap = null;//reset
			return;
		}
		
		if(!(eventGlass instanceof com.vis.core.view.D2.ui.glasses.EventGlass)) {
			this.prap = null;//reset
			return;
		}
		
		EventGlass eg = (EventGlass)eventGlass;
		Praparat currentPrap = ((SlideGlass)(eg.getParent())).getPraparat();
		
		if(currentPrap == null) {
			this.prap = null;//reset
			return;
		}
		
		if(this.prap == currentPrap) {
			return;
		}
		
		this.prap = currentPrap;
		
		SlideGlass sg = this.prap.getCurrentSlide();
		ImagePlus imp = sg.getOriginalImage();
		ij.process.ImageStatistics stats = imp.getStatistics(ij.measure.Measurements.MIN_MAX);
		this.minTolerance = stats.min;
		this.maxTolerance = stats.max;
		
		System.out.println("focus success");
	}
	
	@Override
    public void dispose() {
        super.dispose();
        // インスタンスが再作成されるように、static変数をnullに設定
        instance = null;
    }
	
	@Override
    public void setVisible(boolean b) {
        // MOUSE_EVENT_MASK: クリック、プレス、リリースなど
        // MOUSE_MOTION_EVENT_MASK: 移動、ドラッグなど
        long eventMask = AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK;

        if (b) {
            // ダイアログが表示されるときにリスナーを登録
            Toolkit.getDefaultToolkit().addAWTEventListener(globalMouseListener, eventMask);
        } else {
            // ダイアログが非表示になるときにリスナーを解除
            Toolkit.getDefaultToolkit().removeAWTEventListener(globalMouseListener);
        }
        super.setVisible(b);
    }

	// --- 外部から値を取得するためのメソッド ---
	public boolean wasOkPressed() {
		return wasOkPressed;
	}

	private double getTolerance() {
		Object v = toleranceField.getValue();
		if (v == null) {
			System.out.println("WandToolDialog: Cannot read tolerance type1, return 0.5.");
			return 0.5d;
		}
		try {
			tolerance = ((Number) toleranceField.getValue()).doubleValue();
		}catch (NumberFormatException e) {
			System.out.println("WandToolDialog: Cannot read tolerance type2, return 0.5.");
			return 0.5d;
		}
		return tolerance;
	}

	private int getMode() {
		String selected_mode = (String)modeComboBox.getSelectedItem();
		if(selected_mode.equals(MODE_4connected)) {
			return Wand.FOUR_CONNECTED;
		}else if(selected_mode.equals(MODE_8connected)) {
			return Wand.EIGHT_CONNECTED;
		}else {
			return Wand.LEGACY_MODE;
		}
	}

	public boolean isSmooth() {
		return smooth;
	}
}
