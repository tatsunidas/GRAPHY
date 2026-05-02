package com.vis.core.anonymize;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@SuppressWarnings("serial")
public class StudyCheckBoxTree extends JTree {

    public StudyCheckBoxTree(DefaultMutableTreeNode root) {
        super(root);
        setCellRenderer(new CheckBoxTreeCellRenderer());
        
        // マウスクリックでチェックボックスを切り替えるリスナー
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = getRowForLocation(e.getX(), e.getY());
                if (row != -1) {
                    TreePath path = getPathForRow(row);
                    Object node = path.getLastPathComponent();
                    if (node instanceof CheckBoxNode) {
                        CheckBoxNode cbNode = (CheckBoxNode) node;
                        cbNode.setSelected(!cbNode.isSelected());
                        // TODO: ここで親・子ノードの連動チェック/アンチェック処理を入れる
                        repaint();
                    }
                }
            }
        });
    }

    // ツリーのノードとして扱うデータモデル
    public static class CheckBoxNode extends DefaultMutableTreeNode {
        private String text;
        private boolean selected;
        // 実際のスタディやシリーズのオブジェクトを保持するフィールド
        public Object dicomEntity; 

        public CheckBoxNode(String text, Object dicomEntity) {
            super(text);
            this.text = text;
            this.dicomEntity = dicomEntity;
            this.selected = true; // デフォルトはON（出力対象）
        }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public String getText() { return text; }
    }

    // レンダラー（表示の仕組み）
    private class CheckBoxTreeCellRenderer extends JPanel implements TreeCellRenderer {
        private JCheckBox checkBox;
        private JLabel label;

        public CheckBoxTreeCellRenderer() {
            setLayout(new BorderLayout());
            setOpaque(false);
            checkBox = new JCheckBox();
            checkBox.setOpaque(false);
            label = new JLabel();
            add(checkBox, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            
            if (value instanceof CheckBoxNode) {
                CheckBoxNode node = (CheckBoxNode) value;
                checkBox.setSelected(node.isSelected());
                label.setText(node.getText());
                
                // Exclude状態の視覚的フィードバック（グレーアウト）
                label.setForeground(node.isSelected() ? UIManager.getColor("Tree.textForeground") : Color.GRAY);
            }
            return this;
        }
    }
}
