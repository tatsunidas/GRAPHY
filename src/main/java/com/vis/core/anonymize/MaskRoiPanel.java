package com.vis.core.anonymize;

import javax.swing.*;

import com.vis.core.view.D2.roi.RoiObj;

import java.awt.*;

@SuppressWarnings("serial")
public class MaskRoiPanel extends JPanel {

    /**
     * パネル内で発生したイベントを親（PixelAnonymizerPanel等）に伝えるためのインターフェース
     */
    public interface MaskRoiPanelListener {
        void onRemoveRequested(MaskRoiPanel panel);
        void onRangeChanged(MaskRoiPanel panel);
    }

    private final RoiObj attachedRoi;
    private JComboBox<String> cmbRange;
    private JTextField txtCustomRange;
    private MaskRoiPanelListener listener;

    // --- 改良ポイント: コンストラクタに seriesLabel を追加 ---
    public MaskRoiPanel(RoiObj attachedRoi, String seriesLabel, int currentSlice, MaskRoiPanelListener listener) {
        this.attachedRoi = attachedRoi;
        this.listener = listener;
        initUI(seriesLabel, currentSlice);
    }

    private void initUI(String seriesLabel, int currentSlice) {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 2, 2, 2),
                BorderFactory.createEtchedBorder()
        ));
        
        // パネルの高さを少し広げて情報を入りやすくします
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 110)); 

        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(1, 2, 1, 2);
        gbc.weightx = 1.0;
        
        // --- 1. シリーズ表示ラベル (新規追加) ---
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblSeries = new JLabel(seriesLabel);
        lblSeries.setFont(lblSeries.getFont().deriveFont(Font.BOLD, 11f));
        lblSeries.setForeground(new Color(0, 70, 150)); // 濃い青色で区別
        centerPanel.add(lblSeries, gbc);

        // --- 2. ROI Type表示 ---
        gbc.gridy = 1;
        String typeName = attachedRoi.getRoiType().name();
        JLabel lblType = new JLabel("Type: " + typeName);
        lblType.setFont(lblType.getFont().deriveFont(Font.PLAIN, 10f));
        centerPanel.add(lblType, gbc);

        // --- 3. 適用範囲コンボボックス ---
        gbc.gridy = 2;
        cmbRange = new JComboBox<>(new String[]{
                "All Slices in Series",
                "Current Slice Only (" + currentSlice + ")",
                "Custom Range..."
        });
        centerPanel.add(cmbRange, gbc);

        // --- 4. Custom入力欄 ---
        gbc.gridy = 3;
        txtCustomRange = new JTextField();
        txtCustomRange.setVisible(false);
        centerPanel.add(txtCustomRange, gbc);

        // ... 以下、リスナー設定などは以前と同じ ...
        cmbRange.addActionListener(e -> {
            boolean isCustom = (cmbRange.getSelectedIndex() == 2);
            txtCustomRange.setVisible(isCustom);
            revalidate();
            if (listener != null) listener.onRangeChanged(this);
        });

        add(centerPanel, BorderLayout.CENTER);
        
        // 削除ボタン(X)
        JButton btnClose = new JButton("X");
        btnClose.setForeground(Color.RED);
        btnClose.addActionListener(e -> {
            if (listener != null) listener.onRemoveRequested(this);
        });
        JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.add(btnClose, BorderLayout.NORTH); 
        add(eastPanel, BorderLayout.EAST);
    }

    // --- Getter メソッド群 ---

    public RoiObj getAttachedRoi() { // 実際の Roi 型に書き換えてください
        return attachedRoi;
    }

    /**
     * 現在UIで選択されているスライスの適用範囲モードを返す
     * 0: All Slices, 1: Current Slice, 2: Custom Range
     */
    public int getSelectedRangeMode() {
        return cmbRange.getSelectedIndex();
    }

    /**
     * Custom Rangeが選ばれている場合、入力されたテキストを返す
     */
    public String getCustomRangeText() {
        return txtCustomRange.getText().trim();
    }
}
