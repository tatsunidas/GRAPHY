package com.vis.core.anonymize;

import com.vis.core.anonymize.AttributeAnonymizerPanel;
import com.vis.core.anonymize.AnonymizeConfig;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;

import java.awt.*;
import java.awt.event.ActionEvent;

public class PixelAnonymizerPanel extends JPanel {
	
	public static void main(String[] args) {
		new PixelAnonymizerPanel().setVisible(true);
	}

    private StudyCheckBoxTree studyTree;
    private JPanel seriesDisplayPanel; // Praparatを配置するパネル
    private JPanel maskRoiListPanel;   // ROIパネルを並べるリスト
    private AttributeAnonymizerPanel attrAnonPanel;
    
    private JProgressBar progressBar;
    private JButton btnExecute;

    public PixelAnonymizerPanel() {
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        // ==========================================
        // 上部 (Center): ツリー、画像ビューワ、ROIリスト
        // ==========================================
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.2); // 左ペインの比率

        // 左: スタディツリー
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        studyTree = new StudyCheckBoxTree(root);
        JScrollPane treeScroll = new JScrollPane(studyTree);
        treeScroll.setBorder(new TitledBorder("Study / Series (Uncheck to Exclude)"));
        mainSplit.setLeftComponent(treeScroll);

        // 右: ビューワとROIリストの分割
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        rightSplit.setResizeWeight(0.7); // ビューワの比率を大きく

        // 中央: シリーズ表示パネル (Praparat)
        seriesDisplayPanel = new JPanel(new BorderLayout());
        seriesDisplayPanel.setBorder(new TitledBorder("Series Display"));
        // TODO: ここに Praparat のインスタンスと ROIツールバーを add する
        JLabel lblPlaceholder = new JLabel("Praparat Viewer Placeholder", SwingConstants.CENTER);
        seriesDisplayPanel.add(lblPlaceholder, BorderLayout.CENTER);
        rightSplit.setLeftComponent(seriesDisplayPanel);

        // 右端: マスクROI管理リストパネル
        rightSplit.setRightComponent(createMaskRoiListPanel());

        mainSplit.setRightComponent(rightSplit);
        add(mainSplit, BorderLayout.CENTER);

        // ==========================================
        // 下部 (South): 属性匿名化パネル ＆ 実行パネル
        // ==========================================
        JPanel bottomContainer = new JPanel(new BorderLayout());

        // 属性匿名化パネル（PIXEL_MODEで初期化し、入力フォルダ指定を隠す）
        attrAnonPanel = new AttributeAnonymizerPanel(AttributeAnonymizerPanel.Mode.PIXEL_MODE);
        bottomContainer.add(attrAnonPanel, BorderLayout.CENTER);

        // 実行パネル (プログレスバー + Executeボタン)
        JPanel execPanel = new JPanel(new BorderLayout(10, 0));
        execPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        
        btnExecute = new JButton("Execute Pixel & Attribute Anonymization");
        btnExecute.setPreferredSize(new Dimension(250, 40));
        btnExecute.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        btnExecute.addActionListener(this::onExecuteClicked);

        execPanel.add(progressBar, BorderLayout.CENTER);
        execPanel.add(btnExecute, BorderLayout.EAST);

        bottomContainer.add(execPanel, BorderLayout.SOUTH);

        add(bottomContainer, BorderLayout.SOUTH);
    }

    private JPanel createMaskRoiListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Mask ROIs"));

        // North: オプションボタン群
        JPanel optionsPanel = new JPanel(new GridLayout(3, 1, 2, 2));
        JButton btnApplyAll = new JButton("Apply to All Series");
        JToggleButton btnTogglePreview = new JToggleButton("Preview Mask as Blackout");
        JButton btnClearAll = new JButton("Clear All ROIs");

        optionsPanel.add(btnApplyAll);
        optionsPanel.add(btnTogglePreview);
        optionsPanel.add(btnClearAll);
        panel.add(optionsPanel, BorderLayout.NORTH);

        // Center: ROIパネルを追加していくコンテナ
        maskRoiListPanel = new JPanel();
        maskRoiListPanel.setLayout(new BoxLayout(maskRoiListPanel, BoxLayout.Y_AXIS));
        
        JScrollPane scrollPane = new JScrollPane(maskRoiListPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scrollPane, BorderLayout.CENTER);

        // テスト用のダミーROIパネル追加
        addDummyRoiPanel("Rectangle", "All Slices");
        addDummyRoiPanel("Polygon", "Slice 1-5");

        return panel;
    }

    // 動的にROIパネルを追加するメソッド（仮）
    private void addDummyRoiPanel(String type, String range) {
        JPanel roiPanel = new JPanel(new BorderLayout());
        roiPanel.setBorder(BorderFactory.createEtchedBorder());
        roiPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.add(new JLabel("Type: " + type));
        infoPanel.add(new JLabel("Range: " + range));
        
        JButton btnClose = new JButton("X");
        btnClose.setForeground(Color.RED);
        btnClose.addActionListener(e -> {
            maskRoiListPanel.remove(roiPanel);
            maskRoiListPanel.revalidate();
            maskRoiListPanel.repaint();
        });

        roiPanel.add(infoPanel, BorderLayout.CENTER);
        roiPanel.add(btnClose, BorderLayout.EAST);

        maskRoiListPanel.add(roiPanel);
        maskRoiListPanel.revalidate();
    }

    private void onExecuteClicked(ActionEvent e) {
        // TODO: バッチ処理パイプラインの実行
        // 1. ツリーから Exclude されていないシリーズのリストを取得
        // 2. Tempフォルダへ画像をコピーし、ROI座標を元にピクセルを0埋め (E.3.1)
        // 3. attrAnonPanel から出力先と設定 (currentConfig) を取得
        // 4. DicomAnonymizerEngine.transcodeDirectory() を Temp -> Dest で実行
        JOptionPane.showMessageDialog(this, "Execute process started!");
    }
}