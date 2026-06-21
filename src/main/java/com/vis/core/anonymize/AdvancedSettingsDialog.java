/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import com.vis.configuration.Resources;

@SuppressWarnings("serial")
public class AdvancedSettingsDialog extends JDialog {

	private JTable table;
	private DefaultTableModel tableModel;
	// ★ 変更: オリジナルと作業用を分ける
	private AnonymizeConfig originalConfig;
	private AnonymizeConfig workingConfig;

	private boolean isConfirmed = false;

	// 検索用フィールド
	private JTextField txtSearch;

	// TagRuleをCSVの並びで保持するリスト
	private List<DicomTagRule> displayRules = new ArrayList<>();

	private static final int COL_RETAIN = 0;
	private static final int COL_TAG = 1;
	private static final int COL_NAME = 2;
	private static final int COL_ACTION = 3;
	private static final int COL_VALUE = 4;

	public AdvancedSettingsDialog(Window owner, AnonymizeConfig currentConfig) {
		//AttributeAnonymizerPanelを編集不可にする
		super(owner, "Advanced Tag Settings", ModalityType.APPLICATION_MODAL);
		// 1. オリジナルの参照を保持
		this.originalConfig = currentConfig;
		// 2. 作業用のコピーを作成（ここでの変更はオリジナルに影響しない）
		this.workingConfig = new AnonymizeConfig(currentConfig);
		initUI();
		loadDataToTable();
	}

	private void initUI() {
		setLayout(new BorderLayout());
		setSize(800, 600);
		setLocationRelativeTo(getOwner());

		// --- ★検索パネルの追加 ---
		JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
		searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		searchPanel.add(new JLabel("Search (Tag or Name): "), BorderLayout.WEST);

		txtSearch = new JTextField();
		// 入力されるたびにテーブルを更新するリスナー
		txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				loadDataToTable();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				loadDataToTable();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				loadDataToTable();
			}
		});
		searchPanel.add(txtSearch, BorderLayout.CENTER);
		add(searchPanel, BorderLayout.NORTH); // 上部に配置

		String[] columnNames = { "Retain", "Tag", "Attribute Name", "Base Action", "Custom Value (Dummy)" };
		tableModel = new DefaultTableModel(columnNames, 0) {
			@Override
			public Class<?> getColumnClass(int col) {
				return col == COL_RETAIN ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int column) {
				// displayRules を使うことでフィルタ後も正しいルールを参照できる
				DicomTagRule rule = displayRules.get(row);

				// 今のオプション設定による「本来のアクション」を取得
				DicomTagRule.Action baseAction = workingConfig.getActionByOptionsAndDefault(rule);

				if (column == COL_RETAIN) {
					// 本来のアクションが既に 'K' (Keep) になるタグは、強制保持の必要がないためロックする
					return baseAction != DicomTagRule.Action.K;
				}

				if (column == COL_VALUE) {
					Boolean retained = (Boolean) getValueAt(row, COL_RETAIN);
					boolean isRetained = retained != null && retained;
					// Retainチェックが入っていなければ、元のActionに関わらず
					// 任意のタグにカスタムダミー値を書き込んで「強制Dアクション化」できる
					return !isRetained;
				}
				return false;
			}

			// ★重要：セルが編集された瞬間に Config を更新する
			@Override
			public void setValueAt(Object aValue, int row, int column) {
				super.setValueAt(aValue, row, column); // テーブルの値を更新

				DicomTagRule rule = displayRules.get(row);
				DicomTagRule.Action baseAction = workingConfig.getActionByOptionsAndDefault(rule);

				if (column == COL_RETAIN) {
					boolean isChecked = (Boolean) aValue;
					if (isChecked && baseAction != DicomTagRule.Action.K) {
						workingConfig.getManualRetainTags().add(rule.getTag());
					} else {
						workingConfig.getManualRetainTags().remove(rule.getTag());
					}
				} else if (column == COL_VALUE) {
					String customVal = (String) aValue;
					if (customVal != null && !customVal.trim().isEmpty()) {
						workingConfig.getCustomTagReplacements().put(rule.getTag(), customVal.trim());
					} else {
						workingConfig.getCustomTagReplacements().remove(rule.getTag());
					}
				}
			}
		};

		table = new JTable(tableModel);
		
		// --- ★ここから追加：COL_VALUE 列専用のレンダラーを設定 ---
		table.getColumnModel().getColumn(COL_VALUE).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {

				// 親クラスの標準的な描画コンポーネントを取得
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

				// 1. その行の Retain 列 (COL_RETAIN) の値を取得
				Boolean retained = (Boolean) table.getValueAt(row, COL_RETAIN);
				boolean isRetained = retained != null && retained;

				if (isRetained) {
					// 2. Retain されている場合（編集不可）：背景を薄いグレー、文字を濃いグレーに
					c.setBackground(new Color(240, 240, 240));
					c.setForeground(Color.GRAY);
				} else {
					// 3. Retain されていない場合（編集可能）：通常の背景色に戻す
					// ※isSelected（行が選択されているか）によって色を分けるのが Swing の標準的な挙動です
					if (isSelected) {
						c.setBackground(table.getSelectionBackground());
						c.setForeground(table.getSelectionForeground());
					} else {
						c.setBackground(table.getBackground());
						c.setForeground(table.getForeground());
					}
				}
				return c;
			}
		});
		
		table.getColumnModel().getColumn(COL_RETAIN).setPreferredWidth(50);
		table.getColumnModel().getColumn(COL_TAG).setPreferredWidth(100);
		table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(250);
		table.getColumnModel().getColumn(COL_ACTION).setPreferredWidth(100);
		table.getColumnModel().getColumn(COL_VALUE).setPreferredWidth(150);

		table.getModel().addTableModelListener(e -> {
			if (e.getColumn() == COL_RETAIN) {
				table.repaint();
			}
		});

		add(new JScrollPane(table), BorderLayout.CENTER);

		JPanel panelSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton btnOk = new JButton("OK");
		btnOk.addActionListener(e -> {
			// 3. ★ OKの時だけ、作業用の内容をオリジナルに書き戻す
			originalConfig.copyFrom(workingConfig);
			isConfirmed = true;
			closeDialog();
		});

		JButton btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(e -> {
			// 4. ★ キャンセルの時は何もしない（workingConfig が破棄されるだけ）
			closeDialog();
		});

		panelSouth.add(btnCancel);
		panelSouth.add(btnOk);
		add(panelSouth, BorderLayout.SOUTH);
		
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleSafeClose();
            }
        });
	}

	private void loadDataToTable() {

		String query = txtSearch.getText().toLowerCase().trim();
		displayRules.clear();
		tableModel.setRowCount(0); // テーブルを一度空にする

		// 1. マスターリストのコピーを作成
		List<DicomTagRule> sortedRules = new ArrayList<>(AnonymizeTagDictionary.TAG_RULES);

		// 2. ★タグ番号の昇順でソート
		sortedRules.sort((r1, r2) -> Integer.compareUnsigned(r1.getTag(), r2.getTag()));

		for (DicomTagRule rule : sortedRules) {

			// PatientName(0010,0010) と PatientID(0010,0020) は表示から除外する
			if (rule.getTag() == 0x00100010 || rule.getTag() == 0x00100020) {
				continue;
			}

			String tagHex = String.format("(%04X,%04X)", rule.getTag() >>> 16, rule.getTag() & 0xFFFF);
			String attrName = rule.getName().toLowerCase();

			// 3. ★検索フィルタリング
			if (!query.isEmpty()) {
				// タグ番号(ヘキサ)または属性名にヒットしなければスキップ
				if (!tagHex.toLowerCase().contains(query) && !attrName.contains(query)) {
					continue;
				}
			}

			// 表示用リストに追加、順序を固定
			displayRules.add(rule);

			// UI上のBase ActionはDICOMデフォルトアクションを見せる
			DicomTagRule.Action baseAction = workingConfig.getActionByOptionsAndDefault(rule);
			String actionStr = baseAction.name() + " (" + baseAction.getLabel() + ")";
			String customVal = workingConfig.getCustomTagReplacements().getOrDefault(rule.getTag(), "");
			// チェックボックスは「手動で保持設定されている」または「元々Kになる予定」の時にONにする
			boolean shouldRetain = workingConfig.getManualRetainTags().contains(rule.getTag())
					|| (baseAction == DicomTagRule.Action.K);

			tableModel.addRow(new Object[] { shouldRetain, tagHex, rule.getName(), actionStr, customVal });
		}
	}

	public boolean isConfirmed() {
		return isConfirmed;
	}
	
	private void handleSafeClose() {
        // isConfirmed は初期値の false のまま。
        // workingConfig の内容はオリジナルの config にコピーされないため、安全です。
		int res = JOptionPane.showConfirmDialog(this, Resources.i18n("AdvancedSettingsDialog.confirm.discard"),
				Resources.i18n("dialog.title.confirm"), JOptionPane.YES_NO_OPTION);
		// ★ YES_NO_OPTIONダイアログなのでYES_OPTIONで判定する（OK_OPTIONと同値だが意味的に正しい方を使う）
		if(res == JOptionPane.YES_OPTION) {
			closeDialog();
		}else {
			return;
		}
    }
	
	private void closeDialog() {
		SwingUtilities.invokeLater(() -> {
			setVisible(false);
			dispose(); // リソースの解放
		});
	}
}