/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import javax.swing.*;

import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;

import java.awt.*;

@SuppressWarnings("serial")
public class MaskRoiPanel extends JPanel {

	/**
	 * パネル内で発生したイベントを親（PixelAnonymizerPanel等）に伝えるためのインターフェース
	 */
	public interface MaskRoiPanelListener {
		void onRemoveRequested(MaskRoiPanel panel);

		void onRangeChanged(MaskRoiPanel panel);
	}

	Praparat ownerPraparat;
	private final RoiObj attachedRoi;
	private JComboBox<String> cmbRange;
	private JTextField txtCustomRange;
	private MaskRoiPanelListener listener;

	public MaskRoiPanel(RoiObj attachedRoi, com.vis.core.view.D2.ui.glasses.Praparat ownerPraparat, String seriesLabel,
			int currentSlice, MaskRoiPanelListener listener) {
		this.attachedRoi = attachedRoi;
		this.ownerPraparat = ownerPraparat; // 保持する
		this.listener = listener;
		initUI(seriesLabel, currentSlice);
	}

	private void initUI(String seriesLabel, int currentSlice) {
		setLayout(new BorderLayout(5, 5));
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2),
				BorderFactory.createEtchedBorder()));

		// パネルの高さを少し広げて情報を入りやすくします
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

		JPanel centerPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(1, 2, 1, 2);
		gbc.weightx = 1.0;

		// --- 1. シリーズ表示ラベル (新規追加) ---
		gbc.gridx = 0;
		gbc.gridy = 0;
		JLabel lblSeries = new JLabel(seriesLabel);
		lblSeries.setFont(lblSeries.getFont().deriveFont(Font.BOLD, 11f));
		lblSeries.setForeground(new Color(0, 70, 150)); // 濃い青色で区別
		centerPanel.add(lblSeries, gbc);

		// --- 2. ROI Type表示 ---
		gbc.gridy = 1;
		String typeName = attachedRoi.getRoiType().name();
		JLabel lblType = new JLabel("Type: " + typeName);
		lblType.setFont(lblType.getFont().deriveFont(Font.PLAIN, 10f));
		centerPanel.add(lblType, gbc);

		// --- 3. 適用範囲コンボボックス ---
		gbc.gridy = 2;
		cmbRange = new JComboBox<>(new String[] { "All Slices in Series", "Current Slice Only (" + currentSlice + ")",
				"Custom Range..." });
		centerPanel.add(cmbRange, gbc);
		// ... 以下、リスナー設定などは以前と同じ ...
		cmbRange.addActionListener(e -> {
			boolean isCustom = (cmbRange.getSelectedIndex() == 2);
			txtCustomRange.setVisible(isCustom);
			revalidate();
			if (listener != null)
				listener.onRangeChanged(this);
		});

		// --- 4. Custom入力欄 ---
		gbc.gridy = 3;
		txtCustomRange = new JTextField();
		txtCustomRange.setVisible(false);
		txtCustomRange.setToolTipText("e.g., 1-5, 8, 10");

		// ★ 追加: カスタムレンジのテキストを入力してEnterを押した時、またはフォーカスが外れた時にも変更を反映させる
		txtCustomRange.addActionListener(e -> {
			if (listener != null)
				listener.onRangeChanged(MaskRoiPanel.this);
		});
		txtCustomRange.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				if (listener != null)
					listener.onRangeChanged(MaskRoiPanel.this);
			}
		});

		// ★ 追加: 入力文字の制限 (数字、カンマ、ハイフン、スペースのみを許可)
		((javax.swing.text.AbstractDocument) txtCustomRange.getDocument())
				.setDocumentFilter(new javax.swing.text.DocumentFilter() {
					@Override
					public void insertString(FilterBypass fb, int offset, String string,
							javax.swing.text.AttributeSet attr) throws javax.swing.text.BadLocationException {
						if (string == null)
							return;
						// 正規表現で許可する文字を定義
						if (string.matches("^[0-9,\\- ]*$")) {
							super.insertString(fb, offset, string, attr);
						} else {
							Toolkit.getDefaultToolkit().beep(); // 不正な文字なら警告音を鳴らす
						}
					}

					@Override
					public void replace(FilterBypass fb, int offset, int length, String text,
							javax.swing.text.AttributeSet attrs) throws javax.swing.text.BadLocationException {
						if (text == null)
							return;
						if (text.matches("^[0-9,\\- ]*$")) {
							super.replace(fb, offset, length, text, attrs);
						} else {
							Toolkit.getDefaultToolkit().beep();
						}
					}
				});

		centerPanel.add(txtCustomRange, gbc);

		add(centerPanel, BorderLayout.CENTER);

		// 削除ボタン(X)
		JButton btnClose = new JButton("X");
		btnClose.setForeground(Color.RED);
		btnClose.addActionListener(e -> {
			if (listener != null)
				listener.onRemoveRequested(this);
		});
		JPanel eastPanel = new JPanel(new BorderLayout());
		eastPanel.add(btnClose, BorderLayout.NORTH);
		add(eastPanel, BorderLayout.EAST);
	}

	// MaskRoiPanel.java : setRangeSettings メソッド

	/**
	 * UIの選択状態（モードとカスタムテキスト）を外部から設定する
	 */
	public void setRangeSettings(int mode, String customText) {

		// 1. コンボボックスの選択状態を合わせる
		if (mode >= 0 && mode < cmbRange.getItemCount()) {
			cmbRange.setSelectedIndex(mode);
		}

		// 2. テキストボックスの文字列をセットする
		if (customText != null) {
			txtCustomRange.setText(customText);

			// モードが2 (Custom Range) の場合のみ、テキストボックスを可視化する
			txtCustomRange.setVisible(mode == 2);
		}

		// UIの変更を画面に反映させる
		revalidate();
		repaint();
	}

	// --- Getter メソッド群 ---

	public RoiObj getAttachedRoi() { // 実際の Roi 型に書き換えてください
		return attachedRoi;
	}

	/**
	 * 現在UIで選択されているスライスの適用範囲モードを返す 0: All Slices, 1: Current Slice, 2: Custom Range
	 */
	public int getSelectedRangeMode() {
		return cmbRange.getSelectedIndex();
	}

	/**
	 * Custom Rangeが選ばれている場合、入力されたテキストを返す
	 */
	public String getCustomRangeText() {
		return txtCustomRange.getText().trim();
	}

	/**
	 * コンボボックスの選択モードに応じて、対象となるスライスのZCTインデックス配列を返す。
	 * マルチチャンネル・マルチタイムフレームの場合は、対象Z位置の全C・Tのインデックスを含む。
	 * 
	 * @return ZCTインデックスの配列
	 */
	public int[] getTargetSliceIndices(Praparat ownerPraparat) {
		if (ownerPraparat == null || attachedRoi == null) {
			return new int[0];
		}

		int mode = getSelectedRangeMode();
		java.util.concurrent.ConcurrentHashMap<Integer, com.vis.core.view.D2.ui.glasses.SlideGlass> slides = ownerPraparat
				.getAllSlides();

		if (slides == null)
			return new int[0];

		if (mode == 0) {
			// --- モード0: All Slices in Series ---
			int[] indices = new int[slides.size()];
			int i = 0;
			for (Integer index : slides.keySet()) {
				indices[i++] = index;
			}
			// 昇順にソートしておくと安全
			java.util.Arrays.sort(indices);
			return indices;

		} else if (mode == 1) {
			// --- モード1: Current Slice Only ---
			// 描画されているスライドグラスから現在のZ位置を取得し、
			// 同じZ位置を持つ全チャンネル・タイムフレームのインデックスを返す
			com.vis.core.view.D2.ui.glasses.SlideGlass sg = attachedRoi.getSlideGlass();
			if (sg != null) {
				int[] currentZct = ownerPraparat.getSlidePositionZCTArray(sg);
				int currentZ = currentZct[0]; // Z位置を取得

				java.util.List<Integer> targetIndices = new java.util.ArrayList<>();
				for (Integer idx : slides.keySet()) {
					int[] zct = ownerPraparat.getSlidePositionZCTArray(idx);
					if (zct[0] == currentZ) {
						targetIndices.add(idx);
					}
				}
				java.util.Collections.sort(targetIndices);
				return targetIndices.stream().mapToInt(i -> i).toArray();
			}
			return new int[0];

		} else if (mode == 2) {
			// --- モード2: Custom Range ---
			// ユーザー入力(スライス番号: Z)をパースし、
			// 該当するZ位置を持つ全チャンネル・タイムフレームのインデックスを返す
			java.util.Set<Integer> targetZSet = parseCustomRangeToZ(getCustomRangeText());

			java.util.List<Integer> targetIndices = new java.util.ArrayList<>();
			for (Integer idx : slides.keySet()) {
				int[] zct = ownerPraparat.getSlidePositionZCTArray(idx);
				// そのZCTインデックスのZ位置が、ユーザーが指定したZの範囲に含まれているか
				if (targetZSet.contains(zct[0])) {
					targetIndices.add(idx);
				}
			}
			java.util.Collections.sort(targetIndices);
			return targetIndices.stream().mapToInt(i -> i).toArray();
		}

		return new int[0];
	}

	/**
	 * カンマ区切りやハイフン指定（例: "1-5", "1,3,5"）のテキストをパースし、 対象となるZインデックス（0ベース）のSetを返す。
	 * ※ユーザー入力は「スライス番号（1ベース）」であると想定して -1 しています。
	 */
	private java.util.Set<Integer> parseCustomRangeToZ(String text) {
		java.util.Set<Integer> zSet = new java.util.TreeSet<>();
		if (text == null || text.trim().isEmpty()) {
			return zSet;
		}

		String[] parts = text.split(",");
		for (String part : parts) {
			part = part.trim();
			if (part.isEmpty())
				continue;

			try {
				if (part.contains("-")) {
					String[] range = part.split("-");
					if (range.length == 2) {
						// UI入力(1ベース)を内部Z(0ベース)に変換
						int start = Integer.parseInt(range[0].trim()) - 1;
						int end = Integer.parseInt(range[1].trim()) - 1;

						int min = Math.max(0, Math.min(start, end));
						int max = Math.max(start, end);

						for (int z = min; z <= max; z++) {
							zSet.add(z);
						}
					}
				} else {
					int val = Integer.parseInt(part) - 1;
					if (val >= 0) {
						zSet.add(val);
					}
				}
			} catch (NumberFormatException e) {
				System.err.println("Invalid custom range format: " + part);
			}
		}
		return zSet;
	}
}
