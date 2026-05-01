/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import javax.swing.*;
import javax.swing.border.TitledBorder;

import com.vis.core.log.Log;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.List;
import java.util.logging.Level;

@SuppressWarnings("serial")
public class AttributeAnonymizerPanel extends JPanel {

    // 入出力先設定
    private JTextField txtSourceDir;
    private JTextField txtDestDir;
    private JButton btnBrowseSource;
    private JButton btnBrowseDest;

    // 匿名化設定
    private JTextField txtPatientName;
    private JTextField txtPatientID;
    
    // PS3.15 全オプションのチェックボックス
    private JCheckBox chkRetainDevice;
    private JCheckBox chkRetainInst;
    private JCheckBox chkRetainDatesFull;
    private JCheckBox chkRetainDatesMod;
    private JCheckBox chkRetainPatientChars;
    private JCheckBox chkRetainUIDs;
    private JCheckBox chkRetainPrivate;
    
    private JCheckBox chkCleanPixelData;
    private JCheckBox chkCleanVisualFeatures;
    private JCheckBox chkCleanDesc;
    private JCheckBox chkCleanStruct;
    private JCheckBox chkCleanGraph; 
    
    private JButton btnAdvancedSettings;
    
    private JTextField txtRandomSeed;
    
    // 実行とログ・進捗
    private JTextArea txtLogs;
    private JButton btnProceed;
    private JProgressBar progressBar; // 追加: プログレスバー
    private SwingWorker<Void, String> currentWorker; // 追加: 非同期ワーカー

    private AnonymizeConfig currentConfig = new AnonymizeConfig();

    public AttributeAnonymizerPanel() {
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(createFileSelectionPanel(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        splitPane.setLeftComponent(createSettingsPanel());
        splitPane.setRightComponent(createLogPanel());

        add(splitPane, BorderLayout.CENTER);

        // 下部：プログレスバーと実行ボタンエリア
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        bottomPanel.add(progressBar, BorderLayout.CENTER);
        
        btnProceed = new JButton("Proceed");
        btnProceed.setPreferredSize(new Dimension(120, 30));
        btnProceed.addActionListener(this::onProceedClicked);
        bottomPanel.add(btnProceed, BorderLayout.EAST);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // パネルがウィンドウに追加されたときに閉じるイベントをフックする
    @Override
    public void addNotify() {
        super.addNotify();
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            // デフォルトの閉じる動作を無効化
            if (window instanceof JFrame) {
                ((JFrame) window).setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            } else if (window instanceof JDialog) {
                ((JDialog) window).setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            }

            // 重複登録を防ぐため、一度同種のリスナーを外す
            for (WindowListener wl : window.getWindowListeners()) {
                if (wl instanceof AnonymizerWindowAdapter) {
                    window.removeWindowListener(wl);
                }
            }
            window.addWindowListener(new AnonymizerWindowAdapter(window));
        }
    }

    // ウィンドウクローズ処理をハンドリングする内部クラス
    private class AnonymizerWindowAdapter extends WindowAdapter {
        private Window window;
        public AnonymizerWindowAdapter(Window w) { this.window = w; }
        
        @Override
        public void windowClosing(WindowEvent e) {
            if (currentWorker != null && !currentWorker.isDone()) {
                // 処理中の場合、中断するか確認
                int result = JOptionPane.showConfirmDialog(
                    AttributeAnonymizerPanel.this,
                    "Anonymization process is currently running.\nAre you sure you want to cancel and close?",
                    "Confirm Exit",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (result == JOptionPane.YES_OPTION) {
                    currentWorker.cancel(true); // ワーカーに中断信号を送る
                    SwingUtilities.invokeLater(()->{
                    	window.dispose(); // ウィンドウを閉じる
                    });
                    
                }
                // Noの場合は何もしない（ダイアログへ戻る）
            
            } else {//処理が走っていなければそのまま閉じる
            	SwingUtilities.invokeLater(()->{
                	window.dispose(); // ウィンドウを閉じる
                });
            }
        }
    }

    private JPanel createFileSelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Target Files"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("Source Dir:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        txtSourceDir = new JTextField();
        panel.add(txtSourceDir, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        btnBrowseSource = new JButton("Browse...");
        btnBrowseSource.addActionListener(e -> browseDirectory(txtSourceDir));
        panel.add(btnBrowseSource, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Dest Dir:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        txtDestDir = new JTextField();
        panel.add(txtDestDir, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        btnBrowseDest = new JButton("Browse...");
        btnBrowseDest.addActionListener(e -> browseDirectory(txtDestDir));
        panel.add(btnBrowseDest, gbc);

        return panel;
    }

    private JScrollPane createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Basic Application Level Confidentiality Profile"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int y_pos = 0;
        
        gbc.gridx = 0; gbc.gridy = y_pos++; gbc.weightx = 0;
        panel.add(new JLabel("Random Seed (Optional):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        txtRandomSeed = new JTextField();
        txtRandomSeed.setToolTipText("Enter a seed value for reproducible anonymization across different batches.");
        panel.add(txtRandomSeed, gbc);

        gbc.gridx = 0; gbc.gridy = y_pos++;
        panel.add(new JLabel("Patient Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        txtPatientName = new JTextField("de-identified");
        panel.add(txtPatientName, gbc);

        gbc.gridx = 0; gbc.gridy = y_pos++; gbc.weightx = 0;
        panel.add(new JLabel("Patient ID:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        txtPatientID = new JTextField("de-identified");
        panel.add(txtPatientID, gbc);
        
        chkRetainDevice         = new JCheckBox("Retain Device Identity", true);
        chkRetainInst           = new JCheckBox("Retain Institution Identity", true);
        chkRetainDatesFull      = new JCheckBox("Retain Dates (Full)", true);
        chkRetainDatesMod       = new JCheckBox("Retain Dates (Modified)", true);
        chkRetainPatientChars   = new JCheckBox("Retain Patient Characteristics", true);
        chkRetainUIDs           = new JCheckBox("Retain UIDs", true);
        chkRetainPrivate        = new JCheckBox("Retain Safe Private", true);
        chkCleanPixelData       = new JCheckBox("Clean Pixel Data (Remove burned-in text)", false);
        chkCleanVisualFeatures  = new JCheckBox("Clean Recognizable Visual Features (e.g. Face)", false);
        chkCleanDesc            = new JCheckBox("Clean Descriptor", true);
        chkCleanStruct          = new JCheckBox("Clean Structured Content", true);
        chkCleanGraph           = new JCheckBox("Clean Graphics", true);

        JCheckBox[] opts = { chkRetainPrivate, chkRetainUIDs, chkRetainDevice, chkRetainInst, chkRetainPatientChars,
                    chkRetainDatesFull, chkRetainDatesMod, 
                    chkCleanPixelData, chkCleanVisualFeatures, chkCleanDesc, chkCleanStruct, chkCleanGraph };

        for (JCheckBox cb : opts) {
                gbc.gridx = 0; gbc.gridy = y_pos++; gbc.gridwidth = 2;
                panel.add(cb, gbc);
        }

        gbc.gridy = y_pos++; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.EAST;
        btnAdvancedSettings = new JButton("Advanced Settings...");
        btnAdvancedSettings.addActionListener(e -> openAdvancedSettings());
        panel.add(btnAdvancedSettings, gbc);

        gbc.gridx = 0; gbc.gridy = y_pos++; gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JLabel(""), gbc);
        
        return new JScrollPane(panel);
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Execution Logs"));
        
        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        
        JScrollPane scrollPane = new JScrollPane(txtLogs);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private void browseDirectory(JTextField targetTextField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetTextField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void openAdvancedSettings() {
        syncUiToConfig();
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        AdvancedSettingsDialog dialog = new AdvancedSettingsDialog(parentFrame, currentConfig);
        dialog.setVisible(true);

        // ダイアログ内で変更された可能性があるため、確認後にログを出す
        appendLog("Advanced settings updated.", false);
    }
    
    private void syncUiToConfig() {
    	
    	// syncUiToConfig() 内
    	String seedStr = txtRandomSeed.getText().trim();
    	if (!seedStr.isEmpty()) {
    	    try {
    	        currentConfig.setRandomSeed(Long.parseLong(seedStr));
    	    } catch (NumberFormatException e) {
    	        // 文字列のハッシュ値をシードにするなどのフォールバック
    	    	JOptionPane.showConfirmDialog(this, "No numerical value was inputed, will not randomize studies.");
    	    	Log.logger.log(Level.WARNING, "No numerical value was inputed, will not use random seed.");
    	        currentConfig.setRandomSeed(null);
    	    }
    	} else {
    	    currentConfig.setRandomSeed(null); // シードなし
    	}
    	
        currentConfig.setReplacePatientName(txtPatientName.getText().trim());
        currentConfig.setReplacePatientId(txtPatientID.getText().trim());
        
        currentConfig.getOptions().clear();
        if (chkRetainPrivate.isSelected())      currentConfig.addOption(AnonymizeConfig.Option.RetainSafePrivate);
        if (chkRetainUIDs.isSelected())         currentConfig.addOption(AnonymizeConfig.Option.RetainUIDs);
        if (chkRetainDevice.isSelected())       currentConfig.addOption(AnonymizeConfig.Option.RetainDeviceIdentity);
        if (chkRetainInst.isSelected())         currentConfig.addOption(AnonymizeConfig.Option.RetainInstitutionIdentity);
        if (chkRetainPatientChars.isSelected()) currentConfig.addOption(AnonymizeConfig.Option.RetainPatientCharacteristics);
        if (chkRetainDatesFull.isSelected())    currentConfig.addOption(AnonymizeConfig.Option.RetainLongitudinalTemporalInformationFullDates);
        if (chkRetainDatesMod.isSelected())     currentConfig.addOption(AnonymizeConfig.Option.RetainLongitudinalTemporalInformationModifiedDates);
        if (chkCleanPixelData.isSelected())     currentConfig.addOption(AnonymizeConfig.Option.CleanPixelData);
        if (chkCleanVisualFeatures.isSelected())currentConfig.addOption(AnonymizeConfig.Option.CleanRecognizableVisualFeatures);
        if (chkCleanDesc.isSelected())          currentConfig.addOption(AnonymizeConfig.Option.CleanDescriptors);
        if (chkCleanStruct.isSelected())        currentConfig.addOption(AnonymizeConfig.Option.CleanStructuredContent);
        if (chkCleanGraph.isSelected())         currentConfig.addOption(AnonymizeConfig.Option.CleanGraphics);
    }

    private void onProceedClicked(ActionEvent e) {
        String srcPath = txtSourceDir.getText().trim();
        String destPath = txtDestDir.getText().trim();

        if (srcPath.isEmpty() || destPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select source and destination directories.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File src = new File(srcPath);
        File dest = new File(destPath);

        syncUiToConfig(); // 以前の不完全な個別チェックを削除し、一括で同期

        txtLogs.setText(""); // ログをクリア
        appendLog("Start anonymization process...", false);
        setUiEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("0%");

        currentWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                DicomAnonymizerEngine engine = new DicomAnonymizerEngine();
                
                // エンジンからプログレスとログのコールバックを受け取る
                engine.setProgressListener(new DicomAnonymizerEngine.ProgressListener() {
                    @Override
                    public void onProgress(int current, int total, String message) {
                        int percent = (int) (((double) current / total) * 100);
                        setProgress(percent); // PropertyChangeListener に通知
                        if (message != null) {
                            publish(message); // process メソッドにログを送信
                        }
                    }
                });

                engine.transcodeDirectory(src, dest, currentConfig);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                // UIスレッドで安全にログを追記
                for (String message : chunks) {
                    appendLog(message, false);
                }
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        appendLog("Process was canceled by user.", false);
                        progressBar.setString("Canceled");
                    } else {
                        get(); // 処理中に投げられた例外をキャッチ
                        appendLog("Finished successfully!", false);
                        progressBar.setString("100%");
                        JOptionPane.showMessageDialog(AttributeAnonymizerPanel.this, "Anonymization Completed!");
                    }
                } catch (Exception ex) {
                    appendLog("Error: " + ex.getMessage(), false);
                    ex.printStackTrace();
                    progressBar.setString("Error");
                    JOptionPane.showMessageDialog(AttributeAnonymizerPanel.this, "Error occurred:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setUiEnabled(true);
                    currentWorker = null;
                }
            }
        };

        // SwingWorker の progress プロパティが変わったときのリスナー
        currentWorker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
                progressBar.setString(progress + "%");
            }
        });

        currentWorker.execute();
    }

    private void appendLog(String message, boolean runInUI) {
        Runnable task = () -> {
            txtLogs.append(message + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        };
        if (runInUI) {
            SwingUtilities.invokeLater(task);
        } else {
            task.run(); // process() 内など既にUIスレッドの場合は直接実行
        }
    }

    private void setUiEnabled(boolean enabled) {
        btnProceed.setEnabled(enabled);
        txtSourceDir.setEnabled(enabled);
        txtDestDir.setEnabled(enabled);
        btnBrowseSource.setEnabled(enabled);
        btnBrowseDest.setEnabled(enabled);
        btnAdvancedSettings.setEnabled(enabled);
        
        // パネル内の全チェックボックスの有効/無効を切り替え
        chkRetainDevice.setEnabled(enabled);
        chkRetainInst.setEnabled(enabled);
        chkRetainDatesFull.setEnabled(enabled);
        chkRetainDatesMod.setEnabled(enabled);
        chkRetainPatientChars.setEnabled(enabled);
        chkRetainUIDs.setEnabled(enabled);
        chkRetainPrivate.setEnabled(enabled);
        chkCleanPixelData.setEnabled(enabled);
        chkCleanVisualFeatures.setEnabled(enabled);
        chkCleanDesc.setEnabled(enabled);
        chkCleanStruct.setEnabled(enabled);
        chkCleanGraph.setEnabled(enabled);
    }
}
