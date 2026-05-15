/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D2.ui.glasses;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vis.configuration.Resources;

public class CineSlider extends JPanel implements ActionListener {

	private static final long serialVersionUID = 1L;
	private CineSliderHelper slider;
	private JCheckBox check;
	private Praparat pp = null;
	private Timer timer;
	private int frame = 100;
	private ColorBar colorBar;
	private JLabel titleLabel;
	private String dimensionName; // Position："P", Channel:"C", Time:"T", 5 dimensional.

	public CineSlider(Praparat pp, String name) {
		super();
		this.pp = pp;
		this.dimensionName = name;
		
		setLayout(new BorderLayout());
		timer = new Timer(this.frame, this);
		timer.setCoalesce(true);
		
		// ラベルの準備
		titleLabel = new JLabel(name + ": ");
		titleLabel.setPreferredSize(new Dimension(80, 20));
		
		check = new JCheckBox(Resources.CineStartIcon.loadIconFromResource());
		check.setSelectedIcon(Resources.CineStopIcon.loadIconFromResource());
		check.setSelected(false);
		check.addChangeListener(e -> {
			if (check.isSelected()) {
				timer.setDelay(frame);
				startAnimation();
			} else {
				stopAnimation();
			}
		});

		// レイアウト構築
		JPanel westPanel = new JPanel(new BorderLayout());
		westPanel.add(check, BorderLayout.WEST);
		westPanel.add(titleLabel, BorderLayout.CENTER);
		
		add(westPanel, BorderLayout.WEST);
		
		slider = new CineSliderHelper();
		add(slider, BorderLayout.CENTER);
		
		// カラーバー（Zスライダーの時だけ表示させたい場合は後で制御）
		colorBar = new ColorBar(pp, 128, 10);
		add(colorBar, BorderLayout.NORTH);
	}

	// ★ 新規：カラーバーを表示するかどうか
	public void setColorBarVisible(boolean visible) {
		colorBar.setVisible(visible);
	}
	
	// ★ 新規：アニメーションボタンを表示するかどうか
	public void setCineButtonVisible(boolean visible) {
		check.setVisible(visible);
	}
	
	// ★ 新規追加：スライダー本体とラベルの表示/非表示を切り替える
	public void setSliderVisible(boolean visible) {
		slider.setVisible(visible);
		titleLabel.setVisible(visible);
	}

	public int getValue() {
		return slider.getValue();
	}

	// ★ 修正：外部から最大値を指定して初期化できるようにする
	public void initContext(int total) {
		slider.initContext(total);
	}

	public void setPosition(int ind) {
		if (ind < 0) ind = 0;
		int sliderPos = ind + 1;
		slider.setValue(sliderPos);
		updateLabel(sliderPos, slider.getMaximum());
	}
	
	private void updateLabel(int current, int total) {
		titleLabel.setText(dimensionName + ": " + current + "/" + total);
	}

	public void startAnimation() {
		if (timer != null && !timer.isRunning()) timer.start();
	}

	public void stopAnimation() {
		if (timer != null && timer.isRunning()) timer.stop();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		int next = getValue() + 1;
		if (next > slider.getMaximum()) next = 1;
		slider.setValue(next);
	}

	class CineSliderHelper extends JSlider implements ChangeListener {
		private static final long serialVersionUID = 1L;
		boolean initializing = false;

		public CineSliderHelper() {
			addChangeListener(this);
		}

		public void initContext(int total) {
			if (total <= 0) return;
			initializing = true;
			
			// ★ 修正箇所: NPEを回避するため、一時的にラベル描画をオフにする
		    setPaintLabels(false);
		    
			setLabelTable(null);
			setMinimum(1);
			setMaximum(total);
			
			int majorTickSpacing = Math.max(1, total / 10);
			setMajorTickSpacing(majorTickSpacing);
			setMinorTickSpacing(1);

			createLabelTableAndSet(majorTickSpacing, total);
			setPaintTicks(true);
			setPaintLabels(true); // ★ 修正箇所: テーブルをセットしてからオンに戻す
			setSnapToTicks(true);
			
			initializing = false;
			setValue(1);
			updateLabel(1, total);
		}
		
		private void createLabelTableAndSet(int majorTickSpacing, int total) {
			Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
			labelTable.put(1, new JLabel("1"));
			labelTable.put(total, new JLabel(String.valueOf(total)));
			
			if (total >= majorTickSpacing) {
				for (int i = 1; i <= total / majorTickSpacing; i++) {
					int val = i * majorTickSpacing;
					if (val > 1 && val < total) {
						labelTable.put(val, new JLabel(String.valueOf(val)));
					}
				}
			}
			setLabelTable(labelTable);
		}
		
		@Override
		public void stateChanged(ChangeEvent e) {
			if (initializing) return;
			
			JSlider source = (JSlider) e.getSource();
			/*
			 * if (!source.getValueIsAdjusting()) { ... } の判定を削除してもよいが、
			 * 動画のときに以上に重いので、つけておく
			 */
			if (!source.getValueIsAdjusting()) {
				int val = source.getValue();
				updateLabel(val, getMaximum());
				pp.notifyDimensionChanged(dimensionName, val - 1); 
			}
		}
	}
}
