/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

@SuppressWarnings("serial")
public class AdvancedSettingsDialog extends JDialog {

    private JTable table;
    private DefaultTableModel tableModel;
    private AnonymizeConfig config;
    private boolean isConfirmed = false;

    private static final int COL_RETAIN = 0;
    private static final int COL_TAG = 1;
    private static final int COL_NAME = 2;
    private static final int COL_ACTION = 3;
    private static final int COL_VALUE = 4;

    public AdvancedSettingsDialog(Frame owner, AnonymizeConfig currentConfig) {
        super(owner, "Advanced Tag Settings", true); // モーダルダイアログ
        this.config = currentConfig;
        initUI();
        loadDataToTable();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setSize(800, 600);
        setLocationRelativeTo(getOwner());

        String[] columnNames = {"Retain", "Tag", "Attribute Name", "Base Action", "Custom Value (Dummy)"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == COL_RETAIN ? Boolean.class : String.class;
            }

			@Override
			public boolean isCellEditable(int row, int column) {
				DicomTagRule rule = AnonymizeTagDictionary.TAG_RULES.get(row);

				// アプローチ1: マスターオプションで自動保持されるタグは編集不可（ロック）にする
				if (column == COL_RETAIN) {
					return !config.isAutoRetain(rule);
				}

				// Value列は、Retainされておらず、かつアクションがD(ダミー置換)の場合のみ編集可能
				if (column == COL_VALUE) {
					boolean isRetained = (Boolean) getValueAt(row, COL_RETAIN);
					return !isRetained && rule.getDefaultAction() == DicomTagRule.Action.D;
				}
				return false;
			}
        };

        table = new JTable(tableModel);
        table.getColumnModel().getColumn(COL_RETAIN).setPreferredWidth(50);
        table.getColumnModel().getColumn(COL_TAG).setPreferredWidth(100);
        table.getColumnModel().getColumn(COL_NAME).setPreferredWidth(250);
        table.getColumnModel().getColumn(COL_ACTION).setPreferredWidth(100);
        table.getColumnModel().getColumn(COL_VALUE).setPreferredWidth(150);

        // チェックボックスの状態が変わったら即座にUIを再描画して、テキストフィールドの編集状態などを反映させる
        table.getModel().addTableModelListener(e -> {
            if (e.getColumn() == COL_RETAIN) {
                table.repaint();
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel panelSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("OK");
        btnOk.addActionListener(e -> {
            saveToConfig();
            isConfirmed = true;
            setVisible(false);
        });

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> setVisible(false));

        panelSouth.add(btnCancel);
        panelSouth.add(btnOk);
        add(panelSouth, BorderLayout.SOUTH);
    }

    private void loadDataToTable() {
        List<DicomTagRule> rules = AnonymizeTagDictionary.TAG_RULES;
        for (DicomTagRule rule : rules) {
            String tagHex = String.format("(%04X,%04X)", rule.getTag() >>> 16, rule.getTag() & 0xFFFF);
            String actionStr = rule.getDefaultAction().name() + " (" + rule.getDefaultAction().getLabel() + ")";
            String customVal = config.getCustomTagReplacements().getOrDefault(rule.getTag(), "");

            // ConfigからRetain状態を判定
            boolean shouldRetain = config.isRetain(rule);

            tableModel.addRow(new Object[]{shouldRetain, tagHex, rule.getName(), actionStr, customVal});
        }
    }

    private void saveToConfig() {
        config.getManualRetainTags().clear();
        config.getCustomTagReplacements().clear();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            DicomTagRule rule = AnonymizeTagDictionary.TAG_RULES.get(i);
            boolean isChecked = (Boolean) tableModel.getValueAt(i, COL_RETAIN);
            String customVal = (String) tableModel.getValueAt(i, COL_VALUE);

            // 自動Retainではないのにチェックが入っている場合は、個別の手動Retainリストへ追加
            if (isChecked && !config.isAutoRetain(rule)) {
                config.getManualRetainTags().add(rule.getTag());
            }

            // Dummy値が設定されていれば保持
            if (!isChecked && customVal != null && !customVal.trim().isEmpty()) {
                config.getCustomTagReplacements().put(rule.getTag(), customVal.trim());
            }
        }
    }

    public boolean isConfirmed() { return isConfirmed; }
}
