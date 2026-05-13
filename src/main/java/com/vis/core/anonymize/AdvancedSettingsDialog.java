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

    public AdvancedSettingsDialog(Window owner, AnonymizeConfig currentConfig) {
        super(owner, "Advanced Tag Settings");
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

                // 今のオプション設定による「本来のアクション」を取得
                DicomTagRule.Action baseAction = config.getActionByOptionsAndDefault(rule);

                if (column == COL_RETAIN) {
                    // 本来のアクションが既に 'K' (Keep) になるタグは、強制保持の必要がないためロックする
                    return baseAction != DicomTagRule.Action.K;
                }

                if (column == COL_VALUE) {
                    boolean isRetained = (Boolean) getValueAt(row, COL_RETAIN);
                    // 【強力な新仕様】
                    // Retainチェックが入っていなければ、元のActionに関わらず
                    // 任意のタグにカスタムダミー値を書き込んで「強制Dアクション化」できる
                    return !isRetained;
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
            
            // UI上のBase Action表示は「本来どうなるはずか」を見せる
            DicomTagRule.Action baseAction = config.getActionByOptionsAndDefault(rule);
            String actionStr = baseAction.name() + " (" + baseAction.getLabel() + ")";
            
            String customVal = config.getCustomTagReplacements().getOrDefault(rule.getTag(), "");

            // チェックボックスは「手動で保持設定されている」または「元々Kになる予定」の時にONにする
            boolean shouldRetain = config.getManualRetainTags().contains(rule.getTag()) || (baseAction == DicomTagRule.Action.K);

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

            DicomTagRule.Action baseAction = config.getActionByOptionsAndDefault(rule);

            // 本来は 'K' ではないのに、ユーザーがチェックを入れた場合のみ「手動Retain(上書き)」として登録
            if (isChecked && baseAction != DicomTagRule.Action.K) {
                config.getManualRetainTags().add(rule.getTag());
            }

            // チェックが入っておらず、値が入力されている場合のみ「カスタムダミー(上書き)」として登録
            if (!isChecked && customVal != null && !customVal.trim().isEmpty()) {
                config.getCustomTagReplacements().put(rule.getTag(), customVal.trim());
            }
        }
    }

    public boolean isConfirmed() { return isConfirmed; }
}