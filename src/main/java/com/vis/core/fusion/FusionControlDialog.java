package com.vis.core.fusion;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Hashtable;
import com.vis.core.view.D2.ui.glasses.Praparat;

import ij.ImagePlus;

@SuppressWarnings("serial")
public class FusionControlDialog extends JDialog {
    
    private final Praparat pp;
    private JSlider opacitySlider;
    private JSpinner xShiftSpinner;
    private JSpinner yShiftSpinner;
    private JComboBox<String> lutComboBox; // ★新規追加: LUT選択用

    public FusionControlDialog(Window owner, Praparat pp) {
        super(owner, "Fusion Control", ModalityType.MODELESS);
        this.pp = pp;
        
        initComponents();
        
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // コンポーネントが増えたため、GridLayoutを 4行2列 に拡張します
        JPanel mainPanel = new JPanel(new GridLayout(4, 2, 10, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ==========================================
        // 1. 透明度変更バー（10%刻みの目盛り付き）
        // ==========================================
        mainPanel.add(new JLabel("Transparency:"));
        int initialOpacityPercent = (int) (pp.getCurrentFusionOpacity() * 100);
        opacitySlider = new JSlider(0, 100, initialOpacityPercent);
        
        // --- ★目盛りの設定を追加★ ---
        opacitySlider.setMajorTickSpacing(10); // 10%刻み
        opacitySlider.setPaintTicks(true);     // 目盛りの線（ヒゲ）を描画
        opacitySlider.setPaintLabels(true);    // ラベル（文字）を描画
        
        // 数値の後ろに "%" を付与したカスタムラベルテーブルを作成してセット
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        for (int i = 0; i <= 100; i += 20) { // 画面が狭くならないよう文字は20%刻みが綺麗です
            labelTable.put(i, new JLabel(i + "%"));
        }
        opacitySlider.setLabelTable(labelTable);
        // ------------------------------

        opacitySlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // 仕様: 「バーを離した時」にフュージョンをアップデートする
                if (!opacitySlider.getValueIsAdjusting()) {
                    updateFusion();
                }
            }
        });
        mainPanel.add(opacitySlider);

        // ==========================================
        // 2. Change LUT
        // ==========================================
        mainPanel.add(new JLabel("Color Map (LUT):"));
        
        String[] lutNames = com.vis.configuration.Resources.getLutNames();
        
        lutComboBox = new JComboBox<>(lutNames);
        
        //TODO
        // 現在前景に適用されているLUT名があれば、それを初期選択にする
//        ImagePlus fp = pp.getForegroundOverlay();
//        if(fp != null) {
//        	lutComboBox.setSelectedItem(fp.getLuts()[0]);
//        }
        
        // アイテムが選択されたら即座に反映
        lutComboBox.addActionListener(e -> {
            String selectedLutName = (String) lutComboBox.getSelectedItem();
            
            // 文字列から ImageJ の LUT オブジェクトへ変換する
            ij.process.LUT selectedLut = null;
            if (!"Grayscale".equals(selectedLutName)) {
                 selectedLut = com.vis.configuration.Resources.loadLUT(selectedLutName);
            }
            
            // Step 1 で作成したLUT更新ロジックを呼び出す
            pp.updateFusionLUT(selectedLut, selectedLutName);
        });
        mainPanel.add(lutComboBox);

        // ==========================================
        // 3. XY座標のシフト指定
        // ==========================================
        mainPanel.add(new JLabel("X axis shift (pixels):"));
        xShiftSpinner = new JSpinner(new SpinnerNumberModel(pp.getFusionOffsetX(), -2048, 2048, 1));
        xShiftSpinner.addChangeListener(e -> updateFusion());
        mainPanel.add(xShiftSpinner);

        mainPanel.add(new JLabel("Y axix shift (pixels):"));
        yShiftSpinner = new JSpinner(new SpinnerNumberModel(pp.getFusionOffsetY(), -2048, 2048, 1));
        yShiftSpinner.addChangeListener(e -> updateFusion());
        mainPanel.add(yShiftSpinner);

        add(mainPanel, BorderLayout.CENTER);

        // 下部の閉じるボタン
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        southPanel.add(closeBtn);
        add(southPanel, BorderLayout.SOUTH);
    }

    /**
     * 透過度とシフト位置のパラメータを更新します。
     */
    private void updateFusion() {
        double opacity = opacitySlider.getValue() / 100.0;
        int xShift = (Integer) xShiftSpinner.getValue();
        int yShift = (Integer) yShiftSpinner.getValue();
        
        pp.updateFusionParameters(opacity, xShift, yShift);
    }
}