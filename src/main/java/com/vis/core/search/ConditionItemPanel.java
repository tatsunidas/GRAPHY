/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

@SuppressWarnings("serial")
public class ConditionItemPanel extends JPanel {

    private SearchCondition condition;
    private JComboBox<String> cmbType; // 選択(AND) or 除外(OR)
    private JComboBox<ConditionOperator> cmbOperator;
    private JTextField txtValue1;
    private JTextField txtValue2; // RANGEの時の終了値用
    private JLabel lblTilde; // RANGEの時の「〜」

    public ConditionItemPanel(SearchCondition initialCondition, Runnable onDelete) {
        this.condition = initialCondition;
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), // 下線のみ
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JPanel pnlLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        // 1. 選択基準 / 除外基準 トグル
        /*
         * Include (AND)
         * 
         * Includeに設定した条件は、すべて（AND）満たさなければなりません。
         * 
         * Exclude (OR)
         * 
         * Excludeに設定した条件は、**どれか1つでも（OR）当てはまったらアウト（除外）**になります。
         * Include条件をすべて完璧にクリアしていても、Excludeに引っかかった時点で問答無用で弾かれます。
         */
        cmbType = new JComboBox<>(new String[]{"Include (AND)", "Exclude (OR)"});
        cmbType.setSelectedIndex(condition.isExclusion() ? 1 : 0);
        cmbType.addActionListener(e -> condition.setExclusion(cmbType.getSelectedIndex() == 1));
        pnlLeft.add(cmbType);

        // 2. タグ名ラベル (ツールチップ対応)
        String tagDisplayText = condition.getTagPath() + " (" + condition.getVr() + ")";
        JLabel lblTag = new JLabel(tagDisplayText);
        lblTag.setPreferredSize(new Dimension(150, 20));
        lblTag.setToolTipText(tagDisplayText); // ★ ツールチップでフルネーム(フルパス)を表示
        pnlLeft.add(lblTag);

        // 3. 演算子ドロップダウン（VRに応じて選択肢を制限）
        ConditionOperator[] operators = getOperatorsForVR(condition.getVr());
        cmbOperator = new JComboBox<>(operators);
        cmbOperator.addActionListener(e -> {
            ConditionOperator op = (ConditionOperator) cmbOperator.getSelectedItem();
            condition.setOperator(op);
            boolean isRange = (op == ConditionOperator.RANGE);
            txtValue2.setVisible(isRange);
            lblTilde.setVisible(isRange);
            revalidate();
        });
        pnlLeft.add(cmbOperator);

        // 4. 値入力フィールド
        txtValue1 = new JTextField(10);
        txtValue1.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateVal(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateVal(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateVal(); }
            private void updateVal() { condition.setValue1(txtValue1.getText()); }
        });
        pnlLeft.add(txtValue1);

        lblTilde = new JLabel("~");
        lblTilde.setVisible(false);
        pnlLeft.add(lblTilde);

        txtValue2 = new JTextField(10);
        txtValue2.setVisible(false);
        txtValue2.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateVal(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateVal(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateVal(); }
            private void updateVal() { condition.setValue2(txtValue2.getText()); }
        });
        pnlLeft.add(txtValue2);

        // ★ 数値VRの場合は、入力フィールドに数字・小数点・マイナスのみを許可するフィルタを設定
        if (isNumeric(condition.getVr())) {
            applyNumericFilter(txtValue1);
            applyNumericFilter(txtValue2);
        }

        add(pnlLeft, BorderLayout.CENTER);

        // 5. 削除ボタン
        JButton btnDelete = new JButton("×");
        // ボタンの上下左右の余白（パディング）を少し調整してスッキリさせる
        btnDelete.setMargin(new java.awt.Insets(2, 7, 2, 7));
        btnDelete.addActionListener(e -> onDelete.run());
        
        // ★ 修正: ボタンを直接 EAST に置くのではなく、FlowLayout パネルで包んでから配置する
        // これにより、左側のパネル（pnlLeft）と垂直方向のベースラインが一致します
        JPanel pnlRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pnlRight.add(btnDelete);
        
        add(pnlRight, BorderLayout.EAST);
        
        // 初期状態の同期 (最初のアイテムを選択状態にする)
        if (operators.length > 0) {
            cmbOperator.setSelectedIndex(0);
            condition.setOperator(operators[0]);
        }
    }

    public SearchCondition getCondition() {
        return condition;
    }

    // --- ユーティリティメソッド群 ---

    /**
     * VRに基づいて適切な演算子のリストを返します。
     */
    private ConditionOperator[] getOperatorsForVR(String vr) {
        if (isDateOrTime(vr) || isNumeric(vr)) {
            // 日付・時刻、または数値の場合は範囲指定などを許可
            return new ConditionOperator[]{
                ConditionOperator.EQUALS, 
                ConditionOperator.GREATER_THAN_OR_EQUAL, 
                ConditionOperator.LESS_THAN_OR_EQUAL, 
                ConditionOperator.RANGE
            };
        } else {
            // 文字列などの場合は部分一致(CONTAINS)と完全一致(EQUALS)のみ
            return new ConditionOperator[]{
                ConditionOperator.EQUALS, 
                ConditionOperator.CONTAINS
            };
        }
    }

    private boolean isDateOrTime(String vr) {
        return vr != null && (vr.equals("DA") || vr.equals("DT") || vr.equals("TM"));
    }

    private boolean isNumeric(String vr) {
        return vr != null && (vr.equals("DS") || vr.equals("IS") || vr.equals("FL") || 
               vr.equals("FD") || vr.equals("SL") || vr.equals("SS") || 
               vr.equals("UL") || vr.equals("US"));
    }

    /**
     * テキストフィールドに、数値関連の文字(0-9, ., -)のみ入力を許可するフィルタを適用します。
     */
    private void applyNumericFilter(JTextField textField) {
        ((AbstractDocument) textField.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string == null) return;
                if (isNumericString(string)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null) {
                    super.replace(fb, offset, length, text, attrs);
                    return;
                }
                if (isNumericString(text)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
            
            private boolean isNumericString(String text) {
                // 数字、小数点、マイナス符号のみ許可
                return text.matches("[0-9\\.\\-]+");
            }
        });
    }
}