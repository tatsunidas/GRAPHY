package com.vis.core.anonymize;


import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class AttributeAnonymizerTester {

    public static void main(String[] args) {
        // OS標準のLook & Feel（見た目）を適用する
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // SwingのUIコンポーネントはEvent Dispatch Thread (EDT) 上で生成・操作する
        SwingUtilities.invokeLater(() -> {
            // ウィンドウ（JFrame）の生成
            JFrame frame = new JFrame("DICOM Attribute Anonymizer - Test Mode");
            
            // ウィンドウを閉じた時にプログラムを終了する
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            // 先ほど作成したパネルをインスタンス化してウィンドウに追加
            AttributeAnonymizerPanel panel = new AttributeAnonymizerPanel();
            frame.getContentPane().add(panel);
            
            // ウィンドウのサイズを設定
            frame.setSize(800, 500);
            
            // ウィンドウを画面の中央に配置
            frame.setLocationRelativeTo(null);
            
            // ウィンドウを表示
            frame.setVisible(true);
        });
    }
}
