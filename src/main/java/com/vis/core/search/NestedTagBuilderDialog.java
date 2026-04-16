/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class NestedTagBuilderDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private JTextField txtSearch;
    private JList<String> listDict;
    private DefaultListModel<String> dictListModel;
    private JPanel tagsContainer;
    private List<String> allTagsCache;
    private String resultPath = null;

    // --- 追加：プライベートタグ入力用フィールド ---
    private JTextField txtPrivateTag;
    private JTextField txtPrivateName;

    public NestedTagBuilderDialog(JDialog parent, List<String> allTags) {
        super(parent, "Build Nested Tag Sequence", true);
        this.allTagsCache = allTags;
        setSize(550, 700); // フィールド増分に合わせて少しサイズアップ
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        initUI();
    }

    private void initUI() {
        // --- 1. 上部：検索エリア ---
        JPanel pnlTop = new JPanel(new BorderLayout(5, 5));
        pnlTop.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        txtSearch = new JTextField();
        JButton btnAdd = new JButton("Add Stage");
        pnlTop.add(new JLabel("Search Tag: "), BorderLayout.WEST);
        pnlTop.add(txtSearch, BorderLayout.CENTER);
        pnlTop.add(btnAdd, BorderLayout.EAST);

        // --- 2. 検索結果リスト (上半分) ---
        dictListModel = new DefaultListModel<>();
        listDict = new JList<>(dictListModel);
        filterList("");

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterList(txtSearch.getText()); }
            public void removeUpdate(DocumentEvent e) { filterList(txtSearch.getText()); }
            public void changedUpdate(DocumentEvent e) { filterList(txtSearch.getText()); }
        });

        // --- 3. 手動入力パネル (プライベートタグ用) ---
        // tagsContainerの上に配置するため、先に定義します
        JPanel pnlManual = new JPanel(new GridLayout(2, 2, 5, 5));
        pnlManual.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Manual Private Tag Entry", 
                TitledBorder.LEFT, TitledBorder.TOP));
        
        txtPrivateTag = new JTextField();
        txtPrivateName = new JTextField();
        pnlManual.add(new JLabel("  Tag Number (XXXX,XXXX):"));
        pnlManual.add(txtPrivateTag);
        pnlManual.add(new JLabel("  Tag Name (Optional):"));
        pnlManual.add(txtPrivateName);

        // --- 4. 組み立てエリア (下半分の中央) ---
        tagsContainer = new JPanel();
        tagsContainer.setLayout(new BoxLayout(tagsContainer, BoxLayout.Y_AXIS));
        tagsContainer.setBackground(new Color(245, 245, 245));
        setupDragAndDrop();

        JScrollPane scrollBuild = new JScrollPane(tagsContainer);
        scrollBuild.setBorder(BorderFactory.createTitledBorder("Sequence Structure (Drag to reorder)"));

        // --- 5. 下半分をまとめるパネル (入力パネル + 構築エリア) ---
        JPanel pnlBuildArea = new JPanel(new BorderLayout(5, 5));
        pnlBuildArea.add(pnlManual, BorderLayout.NORTH); // 入力パネルを「うえ」に配置
        pnlBuildArea.add(scrollBuild, BorderLayout.CENTER); // 構築エリアをその下に配置

        // --- 6. 全体をSplitPaneで分割 ---
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(listDict), pnlBuildArea);
        split.setDividerLocation(180);

        add(pnlTop, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // --- 7. 最下部：決定ボタン ---
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("Add to List");
        JButton btnCancel = new JButton("Cancel");
        btnOk.addActionListener(e -> finish());
        btnCancel.addActionListener(e -> dispose());
        pnlBottom.add(btnOk);
        pnlBottom.add(btnCancel);
        add(pnlBottom, BorderLayout.SOUTH);

        // Add Stage ボタンのロジックは前回と同様（プライベートタグ優先）
        btnAdd.addActionListener(e -> {
            String pTag = txtPrivateTag.getText().trim();
            String pName = txtPrivateName.getText().trim();

            if (!pTag.isEmpty()) {
                if (pTag.matches("^[0-9A-Fa-f]{4},[0-9A-Fa-f]{4}$")) {
                    String displayName = pTag.toUpperCase() + (pName.isEmpty() ? "" : " - " + pName);
                    addTagPanel(displayName);
                    txtPrivateTag.setText("");
                    txtPrivateName.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid Tag Format. Please use 'XXXX,XXXX'.", "Format Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                String selected = listDict.getSelectedValue();
                if (selected != null) addTagPanel(selected);
            }
        });
    }

    private void addTagPanel(String tag) {
        final NestedTagItemPanel[] panelHolder = new NestedTagItemPanel[1];
        panelHolder[0] = new NestedTagItemPanel(tag, () -> {
            if (panelHolder[0] != null) {
                tagsContainer.remove(panelHolder[0]);
                refreshContainer();
            }
        });
        tagsContainer.add(panelHolder[0]);
        refreshContainer();
    }

    private void refreshContainer() {
        tagsContainer.revalidate();
        tagsContainer.repaint();
    }

    private void filterList(String text) {
        dictListModel.clear();
        String regex = ".*" + text.replace("*", ".*") + ".*";
        Pattern p = Pattern.compile("(?i)" + regex);
        for (String s : allTagsCache) {
            if (p.matcher(s).matches()) dictListModel.addElement(s);
        }
    }

    private void setupDragAndDrop() {
        MouseAdapter ma = new MouseAdapter() {
            private NestedTagItemPanel draggingPanel = null;
            @Override
            public void mousePressed(MouseEvent e) {
                Component c = tagsContainer.getComponentAt(e.getPoint());
                if (c instanceof NestedTagItemPanel) {
                    draggingPanel = (NestedTagItemPanel) c;
                    draggingPanel.setSelected(true);
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingPanel != null) {
                    draggingPanel.setSelected(false);
                    draggingPanel = null;
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingPanel == null) return;
                Point p = e.getPoint();
                if (p.y < 0 || p.y > tagsContainer.getHeight()) return;
                Component over = tagsContainer.getComponentAt(p);
                if (over instanceof NestedTagItemPanel && over != draggingPanel) {
                    int overIndex = tagsContainer.getComponentZOrder(over);
                    tagsContainer.add(draggingPanel, overIndex);
                    refreshContainer();
                }
            }
        };
        tagsContainer.addMouseListener(ma);
        tagsContainer.addMouseMotionListener(ma);
    }

 // --- 既存の finish() メソッドを以下に置き換え ---
    private void finish() {
        Component[] comps = tagsContainer.getComponents();
        if (comps.length == 0) return;
        
        List<String> pathParts = new ArrayList<>();
        for (Component c : comps) {
            if (c instanceof NestedTagItemPanel) {
                pathParts.add(((NestedTagItemPanel) c).getTagString());
            }
        }
        
        // ★ 確定前にバリデーションを実行
        if (!validateSequence(pathParts)) {
            // 検証に失敗した場合はダイアログを閉じず、再編成を促す
            return; 
        }
        
        this.resultPath = String.join(" > ", pathParts);
        dispose();
    }

    /**
     * Validate tags order is valid.
     * Ignore private tags.
     */
	private boolean validateSequence(List<String> pathParts) {
		for (int i = 0; i < pathParts.size(); i++) {
			String tagString = pathParts.get(i);

			try {
				// e.g., "0008,1111 - ReferencedStudySequence" から "0008,1111" を抽出し、整数に変換
				String hexTag = tagString.substring(0, 9).replace(",", "");
				int tagInt = Integer.parseUnsignedInt(hexTag, 16);

				// 1. プライベートタグ判定 (上位16ビットのグループ番号が奇数)
				if ((tagInt & 0x00010000) != 0) {
					return true; // プライベートタグが含まれる場合は検証をスキップ (OK扱い)
				}

				// TagDictからVR(データ型)の文字列を取得 (例: "SQ", "PN", "LO" など)
				String vrStr = com.vis.dicom.TagDict.vrTypeToString(tagInt);

				// 辞書に存在しないタグ（手動入力の特殊タグなど）の場合も検証スキップ
				if (vrStr == null || vrStr.isEmpty()) {
					return true;
				}

				boolean isSQ = vrStr.contains("SQ");

				if (i < pathParts.size() - 1) {
					// 2. 中間階層の検証：必ず SQ(シーケンス) である必要がある
					if (!isSQ) {
						JOptionPane.showMessageDialog(this,
								"ネストの順序が不正です。\n\n" + "中間階層となるタグは、シーケンス(SQ)である必要があります。\n" + "エラー要因: " + tagString
										+ " (VR: " + vrStr + ")\n\n" + "ドラッグ＆ドロップで並び順を修正してください。",
								"Validation Error", JOptionPane.ERROR_MESSAGE);
						return false;
					}
				} else {
					// 3. 最終階層の検証：SQ(シーケンス) は指定できない (値を抽出するため)
					if (isSQ) {
						JOptionPane.showMessageDialog(this,
								"ネストの末尾が不正です。\n\n" + "最終要素はシーケンス(SQ)ではなく、抽出可能な値を持つタグを指定してください。\n" + "エラー要因: "
										+ tagString + " (VR: " + vrStr + ")",
								"Validation Error", JOptionPane.ERROR_MESSAGE);
						return false;
					}
				}

			} catch (Exception e) {
				// 解析不能な手動タグなどが混ざった場合は、とりあえず検証をパスさせる
				return true;
			}
		}

		return true; // 全ての検証をクリア
	}

    public String getResult() {
        return resultPath;
    }
}