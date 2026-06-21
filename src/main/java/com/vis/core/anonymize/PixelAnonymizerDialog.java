/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import com.vis.configuration.Resources;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;

@SuppressWarnings("serial")
public class PixelAnonymizerDialog extends JDialog {

    private PixelAnonymizerPanel anonymizerPanel;

    public PixelAnonymizerDialog(Frame owner, DICOMNode study) {
        super(owner, "Pixel & Attribute Anonymizer", true); // モーダルダイアログ
        initDialog(study);
    }

    public PixelAnonymizerDialog(Dialog owner, DICOMNode study) {
        super(owner, "Pixel & Attribute Anonymizer", true);
        initDialog(study);
    }

    private void initDialog(DICOMNode study) {
        setLayout(new BorderLayout());

        // メインパネルの生成とデータのロード
        anonymizerPanel = new PixelAnonymizerPanel();
        anonymizerPanel.loadStudyData(study);

        add(anonymizerPanel, BorderLayout.CENTER);

        // ダイアログのサイズ設定（画面の80%程度にする例）
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.85);
        int height = (int) (screenSize.height * 0.85);
        setSize(width, height);
        setLocationRelativeTo(getOwner());

        // ウィンドウが閉じられる時の制御
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClosing();
            }
        });
    }
    
    @Override
    public void dispose() {
        // ★ 画面が破棄される直前に、必ず一時ROIのクリーンアップを実行する！
        if (anonymizerPanel != null) {
            anonymizerPanel.cleanupTemporaryRois();
        }
        super.dispose();//SwingUtils not need.
    }

    private void handleClosing() {
        if (anonymizerPanel.isExecuting()) {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    Resources.i18n("PixelAnonymizerDialog.confirm.interrupt"),
                    Resources.i18n("PixelAnonymizerDialog.title.confirmInterrupt"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                anonymizerPanel.stopProcess();
                dispose(); // ここを呼ぶと、上のオーバーライドされたdisposeが走りクリーンアップされる
            }
        } else {
        	dispose();
        }
    }
}
