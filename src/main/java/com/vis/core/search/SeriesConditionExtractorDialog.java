/**
 * copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.TagDict;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;

@SuppressWarnings("serial")
public class SeriesConditionExtractorDialog extends JDialog {
	
	public static void main(String[] args) {
		new SeriesConditionExtractorDialog(null).setVisible(true);
	}

	private JTextField txtSearch;
	private DefaultListModel<String> dictListModel; // 左側の辞書リスト
	private JList<String> listDict;
	
	private List<String> allTagsList = new ArrayList<>();
	
    private JPanel pnlConditionsContainer;
    private List<ConditionItemPanel> conditionPanels = new ArrayList<>();
    private JTextArea txtVerificationResult;
    private JButton btnVerify;
    private JButton btnExtract;
    private JRadioButton rbTreeTable;
    private JRadioButton rbFolder;
    private JButton btnSelectFolder;
    private File selectedFolder = null;
    private File destinationFolder = null;
    private List<DICOMNode> cachedSelectedNodes = null;
    private DatabaseHandler db = DatabaseHandler.getInstance();
    private ConditionVerifier.VerificationResult lastVerificationResult = null;
    
    private javax.swing.Timer searchTimer;
    
    private JProgressBar progressBar; // 追加
    private JCheckBox chkRenameSequential;
    
    //ADMIN_TAGS is keep empty.
    private static final List<String> ADMIN_TAGS = java.util.Arrays.asList();

    public SeriesConditionExtractorDialog(JFrame parent) {
        super(parent, "Series Condition Extractor", true);
        
        MainScreen mainScreen = (MainScreen) WindowManager.getMainScreen();
        if (mainScreen != null) {
            this.cachedSelectedNodes = mainScreen.getSelectedNode();
            if (this.cachedSelectedNodes == null || this.cachedSelectedNodes.isEmpty()) {
                int res = JOptionPane.showConfirmDialog(this, "No selected series from the TreeTable, would you continue to use choose directory function?");
                if(res != JOptionPane.YES_OPTION) {
                    dispose();
                    return;
                }
            }
        }
        
        setSize(1100, 750); // パネルが増えたため少し幅を拡張
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        initUI();
    }

    private void initUI() {
        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
    	JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setBorder(BorderFactory.createTitledBorder("1. Select Source"));
        
        rbTreeTable = new JRadioButton("Selected Series from TreeTable (HOME)", true);
        rbFolder = new JRadioButton("Select Local Folder...");
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbTreeTable);
        bg.add(rbFolder);
        
        btnSelectFolder = new JButton("Browse");
        btnSelectFolder.setEnabled(false);

        JPanel pnlRadio = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlRadio.add(rbTreeTable);
        pnlRadio.add(rbFolder);
        pnlRadio.add(btnSelectFolder);
        pnlTop.add(pnlRadio);
        
        rbFolder.addActionListener(e -> btnSelectFolder.setEnabled(true));
        rbTreeTable.addActionListener(e -> btnSelectFolder.setEnabled(false));

        btnSelectFolder.addActionListener(e -> {
            JFileChooser jfc = new JFileChooser();
            jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedFolder = jfc.getSelectedFile();
                rbFolder.setText("Folder: " + selectedFolder.getName());
            }
        });
        return pnlTop;
    }

    private JPanel createCenterPanel() {
        JPanel pnlCenter = new JPanel(new BorderLayout());
        
        // --- 左側：検索＆辞書リスト ---
        JPanel pnlLeft = new JPanel(new BorderLayout());
        pnlLeft.setPreferredSize(new Dimension(300, 0)); 
        pnlLeft.setMinimumSize(new Dimension(200, 0));
        pnlLeft.setBorder(BorderFactory.createTitledBorder("2. Select DICOM Tags"));
        txtSearch = new JTextField();
		txtSearch.setToolTipText("Search tags (e.g., *Name, 0010*)");
		
		dictListModel = new DefaultListModel<>();
		listDict = new JList<>(dictListModel);
		loadDicomDictionary();

		// 検索窓に入力されるたびにリストを絞り込む
		// ★ 修正: Timerを使って、入力が300ミリ秒止まった時にだけ検索を実行する
        searchTimer = new javax.swing.Timer(300, e -> filterList());
        searchTimer.setRepeats(false); // 1回だけ実行
        
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { triggerSearch(); }
            public void removeUpdate(DocumentEvent e) { triggerSearch(); }
            public void changedUpdate(DocumentEvent e) { triggerSearch(); }
        });

		pnlLeft.add(txtSearch, BorderLayout.NORTH);
		pnlLeft.add(new JScrollPane(listDict), BorderLayout.CENTER);

        // --- 中央：追加・削除ボタン群 (GridBagLayout) ---
        JPanel pnlButtons = new JPanel(new java.awt.GridBagLayout());
        pnlButtons.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JButton btnAdd = new JButton("Add >");
        JButton btnAddNested = new JButton("Add Nested >");
        JButton btnRemoveAll = new JButton("Clear All");

        // アクション: 単一タグの追加
        btnAdd.addActionListener(e -> {
            for (String val : listDict.getSelectedValuesList()) {
                String vr = determineVR(val);
                addConditionPanel(val, vr);
            }
        });

        // アクション: ネストタグの構築と追加
        btnAddNested.addActionListener(e -> {
            NestedTagBuilderDialog builder = new NestedTagBuilderDialog(this, allTagsList);
            builder.setVisible(true);
            String result = builder.getResult();
            if (result != null && !result.isEmpty()) {
                String vr = determineVR(result);
                addConditionPanel(result, vr);
            }
        });

        // アクション: すべての条件をクリア
        btnRemoveAll.addActionListener(e -> {
            pnlConditionsContainer.removeAll();
            conditionPanels.clear();
            pnlConditionsContainer.revalidate();
            pnlConditionsContainer.repaint();
            btnExtract.setEnabled(false); // 条件変更後は再度検証が必要なためボタンを無効化
        });

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = java.awt.GridBagConstraints.RELATIVE;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.insets = new java.awt.Insets(10, 0, 10, 0);

        pnlButtons.add(btnAdd, gbc);
        pnlButtons.add(btnAddNested, gbc);
        pnlButtons.add(btnRemoveAll, gbc);

        // --- 右側：抽出条件リスト ---
        pnlConditionsContainer = new JPanel();
        pnlConditionsContainer.setLayout(new BoxLayout(pnlConditionsContainer, BoxLayout.Y_AXIS));
        JScrollPane scrollConditions = new JScrollPane(pnlConditionsContainer);
        scrollConditions.setBorder(BorderFactory.createTitledBorder("3. Extraction Conditions"));
        
        JPanel pnlRightContainer = new JPanel(new BorderLayout());
        pnlRightContainer.add(pnlButtons, BorderLayout.WEST);
        pnlRightContainer.add(scrollConditions, BorderLayout.CENTER);

        // --- SplitPaneで分割 ---
        javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, pnlLeft, pnlRightContainer);
        splitPane.setResizeWeight(0.3); // 左側に3割のスペースを割り当て
        splitPane.setContinuousLayout(true);
        
        // 初期ディバイダー位置を 1/3 (0.33) に設定する
        // invokeLater を使って描画直後に位置を確定させるのが確実です。
        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.33);
        });

        pnlCenter.add(splitPane, BorderLayout.CENTER);
        return pnlCenter;
    }

    private JPanel createBottomPanel() {
        JPanel pnlBottom = new JPanel(new BorderLayout());
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false); // 通常時は隠しておく

        txtVerificationResult = new JTextArea(10, 50);
        txtVerificationResult.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtVerificationResult.setEditable(false);
        JScrollPane scrollResult = new JScrollPane(txtVerificationResult);
        scrollResult.setBorder(BorderFactory.createTitledBorder("4. Verification Preview"));
        
        	// テキストエリアの上にプログレスバーを配置
        JPanel pnlCenter = new JPanel(new BorderLayout());
        pnlCenter.add(progressBar, BorderLayout.NORTH);
        pnlCenter.add(scrollResult, BorderLayout.CENTER);
        pnlBottom.add(pnlCenter, BorderLayout.CENTER);

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnVerify = new JButton("Verify (Preview)");
        btnExtract = new JButton("Extract & Copy");
        btnExtract.setEnabled(false);

        btnVerify.addActionListener(e -> executeVerification());
        btnExtract.addActionListener(e -> executeExtraction());
        
        chkRenameSequential = new JCheckBox("Rename to sequential numbers (001, 002...)", true);
        
        pnlButtons.add(chkRenameSequential);
        pnlButtons.add(btnVerify);
        pnlButtons.add(btnExtract);
        pnlBottom.add(pnlButtons, BorderLayout.SOUTH);
        
        return pnlBottom;
    }

    // ConditionItemPanel を動的に追加
    private void addConditionPanel(String tagString, String vr) {
        SearchCondition newCond = new SearchCondition(tagString, tagString, vr, false);
        final ConditionItemPanel[] panelHolder = new ConditionItemPanel[1];

        panelHolder[0] = new ConditionItemPanel(newCond, () -> {
            if (panelHolder[0] != null) {
                pnlConditionsContainer.remove(panelHolder[0]);
                conditionPanels.remove(panelHolder[0]);
                pnlConditionsContainer.revalidate();
                pnlConditionsContainer.repaint();
                btnExtract.setEnabled(false);
            }
        });

        conditionPanels.add(panelHolder[0]);
        pnlConditionsContainer.add(panelHolder[0]);
        pnlConditionsContainer.revalidate();
    }
    
    /**
     * DICOMタグの文字列（ネスト対応）からVR（Value Representation）を判定します。
     */
    private String determineVR(String tagPath) {
        try {
            // ネストされている場合は最後の要素（対象となる値を持つタグ）を取得
            String[] parts = tagPath.split(" > ");
            String lastPart = parts[parts.length - 1]; // 例: "0010,0010 - PatientName"
            
            // 先頭の9文字 (例: "0010,0010") からカンマを除去 -> "00100010"
            String hexTag = lastPart.substring(0, 9).replace(",", "");
            int tagInt = Integer.parseUnsignedInt(hexTag, 16);
            
            // TagDict を用いて VR の文字列表現を取得
            String vr = TagDict.vrTypeToString(tagInt);
            if (vr != null && !vr.isEmpty()) {
                return vr;
            }
        } catch (Exception e) {
            System.err.println("Failed to determine VR for tag: " + tagPath);
        }
        return "UN"; // 取得に失敗した場合や不明な場合は "UN" (Unknown) とする
    }
    
	// 辞書の絞り込みメソッド (ゼロ幅スペースなどの透明文字にも対応した最終版)
	private void filterList() {
		String searchStr = txtSearch.getText();
		if (searchStr == null)
			searchStr = "";

		// 1. スペース（半角・全角）に加えて、不可視文字（ゼロ幅スペース \u200B など）も除去
		String cleanSearch = searchStr.replaceAll("[\\s　\\u200B]+", "").toLowerCase();

		String regex = createRegexFromWildcard(searchStr);
		Pattern pattern = Pattern.compile(regex);

		DefaultListModel<String> newModel = new DefaultListModel<>();
		for (String tagStr : allTagsList) {
			// 2. 辞書側の文字列からも不可視文字を完全に消し去る
			String cleanTag = tagStr.replaceAll("[\\s　\\u200B]+", "").toLowerCase();

			boolean containsMatch = cleanTag.contains(cleanSearch);
			boolean regexMatch = false;
			try {
				regexMatch = pattern.matcher(tagStr).matches();
			} catch (Exception e) {
			}

			// どちらかの条件に合致すればリストに追加
			if (containsMatch || regexMatch) {
				newModel.addElement(tagStr);
			}
		}

		listDict.setModel(newModel);
		dictListModel = newModel; 
 	}
	
	// ★ 追加: 入力があるたびにタイマーをリセットする
    private void triggerSearch() {
        if (searchTimer.isRunning()) {
            searchTimer.restart();
        } else {
            searchTimer.start();
        }
    }
	
	// 検索文字列から正規表現パターンを作るユーティリティ
    private String createRegexFromWildcard(String searchStr) {
        if (searchStr == null || searchStr.trim().isEmpty()) {
            return ".*"; // 空欄ならすべて表示
        }
        String regex = searchStr.trim().replace("\\", "\\\\").replace(".", "\\.").replace("+", "\\+")
                .replace("?", "\\?").replace("(", "\\(").replace(")", "\\)").replace("[", "\\[").replace("]", "\\]");
        regex = regex.replace("*", ".*");
        return "(?i).*" + regex + ".*";
    }
    
    private void loadDicomDictionary() {
		allTagsList.clear();
		dictListModel.clear();

		try {
			HashMap<String, String> tagMap = TagDict.getTagAndKeywordSet();

			for (Map.Entry<String, String> entry : tagMap.entrySet()) {
				String hexStr = entry.getKey(); 
				String keyword = entry.getValue(); 

				if (hexStr != null && keyword != null && hexStr.startsWith("0x") && hexStr.length() == 10) {
					String formattedHex = hexStr.substring(2, 6).toUpperCase() + ","
							+ hexStr.substring(6, 10).toUpperCase();

					String displayStr = formattedHex + " - " + keyword;
					
					boolean isAdmin = false;
					for (String admin : ADMIN_TAGS) {
						if (admin.startsWith(formattedHex)) {
							isAdmin = true;
							break;
						}
					}
					if (!isAdmin) {
						allTagsList.add(displayStr);
					}
				}
			}

			Collections.sort(allTagsList);

			for (String str : allTagsList) {
				dictListModel.addElement(str);
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("TagDictからのタグリスト取得に失敗しました。");
			allTagsList.add("0010,0010 - PatientName");
			allTagsList.add("0020,000D - StudyInstanceUID");
			for (String str : allTagsList) {
				dictListModel.addElement(str);
			}
		}
	}

    // --- 検証処理 ---
    private void executeVerification() {
        if (conditionPanels.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add at least one condition.");
            return;
        }

        List<SearchCondition> activeConditions = new ArrayList<>();
        for (ConditionItemPanel panel : conditionPanels) {
            activeConditions.add(panel.getCondition());
        }

        btnVerify.setEnabled(false);
        btnExtract.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        txtVerificationResult.setText("Verifying... Please wait.\n");

        // ★ 修正1: 第2引数(進捗データの型)を String から Object に変更
        SwingWorker<ConditionVerifier.VerificationResult, Object> worker = new SwingWorker<>() {
            @Override
            protected ConditionVerifier.VerificationResult doInBackground() throws Exception {
                List<String> errorLog = new ArrayList<>();
                List<File> targetFiles = getTargetDicomFilesSafe(rbTreeTable.isSelected(), cachedSelectedNodes, errorLog);

                if (targetFiles.isEmpty()) {
                    // String を publish
                    publish("No valid series found to evaluate.\nErrors:\n" + String.join("\n", errorLog));
                    return null;
                }
                // 最大値をセット
                SwingUtilities.invokeLater(() -> progressBar.setMaximum(targetFiles.size()));

                // Integer(count) を publish
                return ConditionVerifier.verify(targetFiles, activeConditions, DICOMBackend.getCurrent(), 
                    count -> publish(count));
            }

            @Override
            protected void process(List<Object> chunks) {
                // ★ 修正2: 受け取るリストを Object 型に変更し、String の場合の処理を追加
                for (Object o : chunks) {
                    if (o instanceof Integer) {
                        progressBar.setValue((Integer) o);
                    } else if (o instanceof String) {
                        txtVerificationResult.append((String) o + "\n");
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    ConditionVerifier.VerificationResult result = get();
                    if (result != null) {
                        lastVerificationResult = result;
                        txtVerificationResult.setText(result.summaryText + "\n\n" + result.treeText);
                        btnExtract.setEnabled(result.matchedSeries > 0);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    txtVerificationResult.setText("Error during verification: " + ex.getMessage());
                } finally {
                    btnVerify.setEnabled(true);
                    progressBar.setVisible(false); // 終わったら隠す
                }
            }
        };
        worker.execute();
    }

    // --- 抽出処理 ---
    private void executeExtraction() {
        if (lastVerificationResult == null || lastVerificationResult.validTargetFiles.isEmpty()) return;

        if (destinationFolder == null) {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle("Select Destination Folder for Extraction");
            jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                destinationFolder = jfc.getSelectedFile();
            } else {
                return;
            }
        }
        
        final boolean doRename = chkRenameSequential.isSelected();
        
		// ★追加: 連番リネームONの場合、作成予定のフォルダが既に存在しないか事前チェック
		if (doRename) {
			boolean conflict = false;
			// 今回作成する予定の連番（001〜N）が、1つでも既に存在するか確認
			for (int i = 1; i <= lastVerificationResult.validTargetFiles.size(); i++) {
				File checkDir = new File(destinationFolder, String.format("%03d", i));
				if (checkDir.exists()) {
					conflict = true;
					break;
				}
			}

			if (conflict) {
				JOptionPane.showMessageDialog(this,
						"The selected folder already contains sequential folders (e.g., 001, 002).\n"
								+ "To prevent data mixing or overwrite errors, please select a different or empty folder.",
						"Folder Conflict", JOptionPane.WARNING_MESSAGE);

				// 次回「Extract」ボタンを押した時に再度フォルダ選択ダイアログが出るようにリセット
				destinationFolder = null;
				return; // 抽出処理をここで中断
			}
		}

        btnExtract.setEnabled(false);
        btnVerify.setEnabled(false);
        progressBar.setMaximum(lastVerificationResult.validTargetFiles.size());
        progressBar.setValue(0);
        progressBar.setVisible(true);
        
        txtVerificationResult.append("\n\nStarting extraction and copying...\n");

        SwingWorker<Void, Object> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                DICOMBackend backend = DICOMBackend.getCurrent();
                boolean isTreeTable = rbTreeTable.isSelected();
                List<File> copiedUniqueFolders = new ArrayList<>();

                // Phase 1: コピー処理
                for (int i = 0; i < lastVerificationResult.validTargetFiles.size(); i++) {
                    File repFile = lastVerificationResult.validTargetFiles.get(i);
                    DicomImage dcm = DicomImage.newDicomImage(repFile.getCanonicalPath(), backend);
                    
                    String uniqueFolderName = ConditionVerifier.generateUniqueFolderName(dcm.getHeader());
                    File seriesDestDir = new File(destinationFolder, uniqueFolderName);
                    if (!seriesDestDir.exists()) seriesDestDir.mkdirs();
                    copiedUniqueFolders.add(seriesDestDir);

                    String studyUid = dcm.getHeader().getString(0x0020000D);
                    String seriesUid = dcm.getHeader().getString(0x0020000E);
                    List<File> filesToCopy = getSeriesFiles(isTreeTable, studyUid, seriesUid, repFile);

                    // ★微調整: nullチェックを追加し、安全にループを回す
                    if (filesToCopy != null) {
                        for (File srcFile : filesToCopy) {
                            File destFile = new File(seriesDestDir, srcFile.getName());
                            Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    publish(i + 1); // 進捗を送信
                }

                // ★微調整: ログ出力と同時に、UI(テキストエリア)にもメッセージを送る
                String phase2Msg = "Copying finished. Starting rename and CSV generation...";
                Log.logger.log(Level.INFO, phase2Msg);
                publish(phase2Msg);

                // Phase 2 & 3: 連番リネームとCSV出力
                if (doRename) {
                    // do rename and create mapping_table
                	/*
                	 * Avoid overwrite csv
                	 */
                    File csvFile = getUniqueFile(destinationFolder, "mapping_table.csv");
                    try (PrintWriter csvWriter = new PrintWriter(new FileWriter(csvFile))) {
                        csvWriter.println("OriginalFolderName,SequentialFolderName");
                        for (int i = 0; i < copiedUniqueFolders.size(); i++) {
                            File uniqueDir = copiedUniqueFolders.get(i);
                            String originalName = uniqueDir.getName();
                            String sequentialName = String.format("%03d", i + 1);
                            File sequentialDir = new File(destinationFolder, sequentialName);

                            if (uniqueDir.renameTo(sequentialDir)) {
                                csvWriter.println(originalName + "," + sequentialName);
                            } else {
                                csvWriter.println(originalName + ",RENAME_FAILED_" + sequentialName);
                            }
                        }
                    }
                } else {
                    	// ★ チェックOFF: リネームはせず、抽出したフォルダの一覧リストだけをCSVに出力する
                    String phase2MsgSkipped = "Skipping rename phase. Generating list CSV...";
                    Log.logger.log(Level.INFO, phase2MsgSkipped);
                    publish(phase2MsgSkipped);

                    File csvFile = getUniqueFile(destinationFolder, "extracted_series_list.csv");
                    try (PrintWriter csvWriter = new PrintWriter(new FileWriter(csvFile))) {
                        csvWriter.println("ExtractedFolderName");
                        for (File uniqueDir : copiedUniqueFolders) {
                            csvWriter.println(uniqueDir.getName());
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Object> chunks) {
                for (Object o : chunks) {
                    if (o instanceof Integer) {
                        progressBar.setValue((Integer) o);
                    } else if (o instanceof String) {
                        txtVerificationResult.append((String) o + "\n");
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    get(); // 例外チェック用
                    txtVerificationResult.append("Extraction successfully completed!\nSaved to: " + destinationFolder.getAbsolutePath() + "\n");
                    if(doRename) {
                    	JOptionPane.showMessageDialog(SeriesConditionExtractorDialog.this, "Extraction and renaming completed successfully!\nMapping saved to mapping_table.csv.");
                    }else {
                    	JOptionPane.showMessageDialog(SeriesConditionExtractorDialog.this, "Extraction and renaming completed successfully!\nExported list saved to extracted_series_list.csv.");
                    }
                    dispose();
                } catch (InterruptedException | ExecutionException ex) {
                    JOptionPane.showMessageDialog(SeriesConditionExtractorDialog.this, "Error during extraction: " + ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    txtVerificationResult.append("Error: " + ex.getCause().getMessage() + "\n");
                } finally {
                    btnExtract.setEnabled(true);
                    btnVerify.setEnabled(true);
                    progressBar.setVisible(false);
                }
            }
        };
        worker.execute();
    }
    
    /**
     * ファイルが既に存在する場合、枝番を付けてユニークなファイル名を返します。
     * 例: mapping_table.csv -> mapping_table_1.csv
     */
    private File getUniqueFile(File folder, String fileName) {
        File file = new File(folder, fileName);
        if (!file.exists()) {
            return file;
        }

        String name = "";
        String ext = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            name = fileName.substring(0, dotIndex);
            ext = fileName.substring(dotIndex);
        } else {
            name = fileName;
        }

        int count = 1;
        while (file.exists()) {
            file = new File(folder, name + "_" + count + ext);
            count++;
        }
        return file;
    }

    // --- ファイル収集用のヘルパーメソッド群 ---
    private List<File> getTargetDicomFilesSafe(boolean isTreeTable, List<DICOMNode> selectedNodes, List<String> errorLog) {
        List<File> targetFiles = new ArrayList<>();
        if (isTreeTable) {
            if (db == null) { errorLog.add("Error: Database connection not found."); return targetFiles; }
            
            // ★修正: シリーズの重複収集を防ぐためのMapを用意 (SeriesInstanceUID -> 代表ファイル)
            Map<String, File> uniqueSeriesMap = new LinkedHashMap<>();
            collectFilesFromTree(selectedNodes, uniqueSeriesMap, errorLog);
            
            // Mapに集めた「重複のない代表ファイル」のリストを結果として返す
            targetFiles.addAll(uniqueSeriesMap.values());
            
        } else {
            Map<String, List<File>> seriesMap = new HashMap<>();
            searchDicomFilesInFolder(selectedFolder, seriesMap, errorLog);
            for (Map.Entry<String, List<File>> entry : seriesMap.entrySet()) {
                File repFile = GDicomTools.getRepresentativeFileOfSeries(entry.getValue(), errorLog);
                if (repFile != null) targetFiles.add(repFile);
                else errorLog.add("Error: No valid representative file found for series: " + entry.getKey());
            }
        }
        return targetFiles;
    }

    // ★修正: 第二引数を List<File> から Map<String, File> に変更
    private void collectFilesFromTree(List<DICOMNode> nodes, Map<String, File> uniqueSeriesMap, List<String> errorLog) {
        for (DICOMNode node : nodes) {
            if (node.getLevel() == DICOMNode.PATIENT || node.getLevel() == DICOMNode.ROOT) {
                List<DICOMNode> children = node.getChildren();
                if (children != null) collectFilesFromTree(children, uniqueSeriesMap, errorLog);
            } else if (node.getLevel() == DICOMNode.STUDY) {
                List<DICOMNode> seriesList = node.getChildren();
                if (seriesList != null) collectFilesFromTree(seriesList, uniqueSeriesMap, errorLog);
            } else if (node.getLevel() == DICOMNode.SERIES) {
                String studyUID = node.getData(DICOMNode.StudyInstanceUID);
                String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
                if (studyUID != null) studyUID = studyUID.trim().replace("\0", "");
                if (seriesUID != null) seriesUID = seriesUID.trim().replace("\0", "");

                // ★修正: すでにMapに登録済みのシリーズなら、無駄な処理を省いてスキップする
                if (seriesUID == null || uniqueSeriesMap.containsKey(seriesUID)) {
                    continue; 
                }

                List<String> seriesFilePaths = db.getFileLocationsSeriesLevel(studyUID, seriesUID);
                if (seriesFilePaths != null && !seriesFilePaths.isEmpty()) {
                    List<File> filesToEvaluate = new ArrayList<>();
                    for (String path : seriesFilePaths) filesToEvaluate.add(new File(path));
                    
                    File repFile = GDicomTools.getRepresentativeFileOfSeries(filesToEvaluate, errorLog);
                    if (repFile != null) {
                        // ★修正: 重複チェック用のMapに登録する
                        uniqueSeriesMap.put(seriesUID, repFile);
                    } else {
                        errorLog.add("Error: シリーズ内に有効な画像が存在しません (Series=" + seriesUID + ")");
                    }
                } else {
                    errorLog.add("Error: DBからファイルパスを取得できませんでした");
                }
            }
        }
    }

    private void searchDicomFilesInFolder(File dir, Map<String, List<File>> seriesMap, List<String> errorLog) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                searchDicomFilesInFolder(f, seriesMap, errorLog);
            } else {
                if (DicomUtilities.isDICOMDIR(f) || !DicomUtilities.isDicomFile(f)) continue;
                String seriesUID = DicomUtilities.getSeriesInstanceUID(f.getAbsolutePath());
                if (seriesUID != null && !seriesUID.trim().isEmpty()) {
                    seriesMap.computeIfAbsent(seriesUID.trim(), k -> new ArrayList<>()).add(f);
                } else {
                    errorLog.add("Failed to read SeriesUID: " + f.getAbsolutePath());
                }
            }
        }
    }

    private List<File> getSeriesFiles(boolean isTreeTable, String studyUid, String seriesUid, File repFile) {
        List<File> files = new ArrayList<>();
        if (isTreeTable) {
            if (db == null) return null;
            List<String> paths = db.getFileLocationsSeriesLevel(studyUid, seriesUid);
            if (paths != null) {
                for (String p : paths) files.add(new File(p));
            }
        } else {
            File parentDir = repFile.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                File[] children = parentDir.listFiles();
                if (children != null) {
                    for (File f : children) {
                        if (f.isFile() && DicomUtilities.isDicomFile(f) && !DicomUtilities.isDICOMDIR(f)) {
                            if (seriesUid.equals(DicomUtilities.getSeriesInstanceUID(f.getAbsolutePath()))) {
                                files.add(f);
                            }
                        }
                    }
                }
            }
        }
        return files;
    }
}