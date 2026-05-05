/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import javax.swing.*;
import javax.swing.tree.*;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;

@SuppressWarnings("serial")
public class StudyCheckBoxTree extends JTree {

	public StudyCheckBoxTree(DefaultMutableTreeNode root, PixelAnonymizerPanel pap) {
		super(root);
		setCellRenderer(new CheckBoxTreeCellRenderer());
		// マウスクリックでチェックボックスを切り替えるリスナー (修正版)
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// クリックされた場所のパス（ノード）を取得
				TreePath path = getPathForLocation(e.getX(), e.getY());
				if (path == null)
					return;

				Object node = path.getLastPathComponent();
				if (node instanceof CheckBoxNode) {
					CheckBoxNode cbNode = (CheckBoxNode) node;
					if (isCheckBoxClicked(path, cbNode, e.getX(), e.getY())) {
						boolean newState = !cbNode.isSelected();
						cbNode.setSelected(newState);
						// 【おまけ】親(スタディ)のチェックを変えたら、子(シリーズ)も連動させる
						if (cbNode.getChildCount() > 0) {
							for (int i = 0; i < cbNode.getChildCount(); i++) {
								Object child = cbNode.getChildAt(i);
								if (child instanceof CheckBoxNode) {
									((CheckBoxNode) child).setSelected(newState);
								}
							}
						}
						// 画面を再描画
						repaint();
					}else {
						HashMap<String, String> seriesInfo = cbNode.seriesInfo;
						if (seriesInfo == null) {
							return;
						}
						SwingUtilities.invokeLater(() -> {
							pap.loadSeriesToPraparat(seriesInfo);
							pap.repaint();
						});
					}
				}
			}
		});
	}
	
	private boolean isCheckBoxClicked(TreePath path, CheckBoxNode cbNode, int x, int y) {
		// そのノードの描画領域（Rectangle）を取得
		Rectangle bounds = getPathBounds(path);
		if (bounds != null) {
			// ツリーのレンダラーコンポーネントを引っ張り出す
			Component rendererComp = getCellRenderer().getTreeCellRendererComponent(StudyCheckBoxTree.this,
					cbNode, isPathSelected(path), isExpanded(path), getModel().isLeaf(cbNode),
					getRowForPath(path), true);

			// レンダラーに実際のサイズを与えて内部のレイアウトを計算させる
			rendererComp.setBounds(bounds);
			rendererComp.doLayout();
			
			// JTree全体でのマウス座標を、レンダラー内部のローカル座標に変換
			int clickX = x - bounds.x;
			int clickY = y - bounds.y;

			// その座標にどのコンポーネントがあるか判定
			Component clickedComponent = rendererComp.getComponentAt(clickX, clickY);

			// クリックされたのが JCheckBox だった場合のみ状態を切り替える
			if (clickedComponent instanceof JCheckBox) {
				return true;
			}
		}
		return false;
	}

	// ツリーのノードとして扱うデータモデル
	public static class CheckBoxNode extends DefaultMutableTreeNode {
		private String text;
		private boolean selected;
		// 実際のスタディやシリーズのオブジェクトを保持するフィールド
		public HashMap<String, String> seriesInfo;

		public CheckBoxNode(String text, HashMap<String, String> seriesInfo) {
			super(text);
			this.text = text;
			this.seriesInfo = seriesInfo;
			this.selected = true; // デフォルトはON（出力対象）
		}

		public boolean isSelected() {
			return selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}

		public String getText() {
			return text;
		}
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
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {

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
