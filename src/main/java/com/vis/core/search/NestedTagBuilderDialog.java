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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class NestedTagBuilderDialog extends JDialog {
	
    private static final long serialVersionUID = 1L;
	private JTextField txtSearch;
    private JList<String> listDict;
    private DefaultListModel<String> dictListModel;
    private JPanel tagsContainer; // タグパネルが並ぶ場所
    private List<String> allTagsCache;
    private String resultPath = null;

    public NestedTagBuilderDialog(JDialog parent, List<String> allTags) {
        super(parent, "Build Nested Tag Sequence", true);
        this.allTagsCache = allTags;
        setSize(500, 600);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        initUI();
    }

    private void initUI() {
        // --- 上部：検索エリア ---
        JPanel pnlTop = new JPanel(new BorderLayout(5, 5));
        pnlTop.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        txtSearch = new JTextField();
        JButton btnAdd = new JButton("Add Stage");
        pnlTop.add(new JLabel("Search Tag: "), BorderLayout.WEST);
        pnlTop.add(txtSearch, BorderLayout.CENTER);
        pnlTop.add(btnAdd, BorderLayout.EAST);

        // --- 中央：検索結果リスト & 組み立てエリア ---
        dictListModel = new DefaultListModel<>();
        listDict = new JList<>(dictListModel);
        filterList(""); // 初期表示

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterList(txtSearch.getText()); }
            public void removeUpdate(DocumentEvent e) { filterList(txtSearch.getText()); }
            public void changedUpdate(DocumentEvent e) { filterList(txtSearch.getText()); }
        });

        // 下部の組み立てエリア
        tagsContainer = new JPanel();
        tagsContainer.setLayout(new BoxLayout(tagsContainer, BoxLayout.Y_AXIS));
        tagsContainer.setBackground(new Color(245, 245, 245));
        
        // ドラッグ操作のリスナー登録
        setupDragAndDrop();

        JScrollPane scrollBuild = new JScrollPane(tagsContainer);
        scrollBuild.setBorder(BorderFactory.createTitledBorder("Sequence Structure (Drag to reorder)"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(listDict), scrollBuild);
        split.setDividerLocation(200);

        add(pnlTop, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // --- 下部：決定ボタン ---
        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("Add to List");
        JButton btnCancel = new JButton("Cancel");
        
        btnOk.addActionListener(e -> finish());
        btnCancel.addActionListener(e -> dispose());
        
        pnlBottom.add(btnOk);
        pnlBottom.add(btnCancel);
        add(pnlBottom, BorderLayout.SOUTH);

        // Addボタンの動作
        btnAdd.addActionListener(e -> {
            String selected = listDict.getSelectedValue();
            if (selected != null) {
                addTagPanel(selected);
            }
        });
    }

    private void addTagPanel(String tag) {
        // ラムダ式の中から自分自身を参照するための「箱（配列）」を用意
        final NestedTagItemPanel[] panelHolder = new NestedTagItemPanel[1];
        
        panelHolder[0] = new NestedTagItemPanel(tag, () -> {
            // 自分自身を親コンテナから削除する
            if (panelHolder[0] != null) {
                tagsContainer.remove(panelHolder[0]);
                refreshContainer();
            }
        });

        // 画面に追加
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
                    // ★ ドラッグ開始：オレンジ色にする
                    draggingPanel.setSelected(true);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingPanel != null) {
                    // ★ ドラッグ終了：元の色に戻す
                    draggingPanel.setSelected(false);
                    draggingPanel = null;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingPanel == null) return;
                
                Point p = e.getPoint();
                // 範囲外にドラッグした時のためにガード
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

    private void finish() {
        Component[] comps = tagsContainer.getComponents();
        if (comps.length == 0) return;

        List<String> pathParts = new ArrayList<>();
        for (Component c : comps) {
            if (c instanceof NestedTagItemPanel) {
                pathParts.add(((NestedTagItemPanel) c).getTagString());
            }
        }
        
        // 数珠つなぎにする
        this.resultPath = String.join(" > ", pathParts);
        dispose();
    }

    public String getResult() {
        return resultPath;
    }
}