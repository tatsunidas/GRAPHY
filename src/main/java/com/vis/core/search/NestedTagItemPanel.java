/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.LineBorder;

/**
 * シーケンス内の1つの階層（タグ）を表すパネル
 */
public class NestedTagItemPanel extends JPanel {
	
    private static final long serialVersionUID = 1L;
	private String tagString;
    private JLabel lblName;
    private JButton btnDelete;

	public NestedTagItemPanel(String tagString, Runnable onDelete) {
		this.tagString = tagString;
		setLayout(new BorderLayout(5, 5));
		setBorder(BorderFactory.createCompoundBorder(new LineBorder(Color.LIGHT_GRAY, 1),
				BorderFactory.createEmptyBorder(5, 10, 5, 10)));
		setBackground(Color.WHITE);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		// ドラッグ用のハンドルアイコン代わり
		JLabel lblHandle = new JLabel("☰ ");
		lblHandle.setForeground(Color.GRAY);
		add(lblHandle, BorderLayout.WEST);

		lblName = new JLabel(tagString);
		add(lblName, BorderLayout.CENTER);

		btnDelete = new JButton("×");
		btnDelete.setMargin(new Insets(0, 5, 0, 5));
		btnDelete.addActionListener(e -> onDelete.run());
		add(btnDelete, BorderLayout.EAST);

		// カーソルを移動用に変更
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		
		setSelected(false); // 初期状態
	}

    public String getTagString() {
        return tagString;
    }
    
    public void setSelected(boolean selected) {
        // 選択時はオレンジ、通常時は薄いグレー。太さは2pxで固定するとガタつきません。
        Color borderColor = selected ? Color.ORANGE : Color.LIGHT_GRAY;
        
        setBorder(BorderFactory.createCompoundBorder(
            new javax.swing.border.LineBorder(borderColor, 2),
            BorderFactory.createEmptyBorder(5, 10, 5, 10) // 内側の余白は維持
        ));
        
        // 背景色も少し変えるとより分かりやすくなります（任意）
        setBackground(selected ? new Color(255, 250, 240) : Color.WHITE);
    }
}
