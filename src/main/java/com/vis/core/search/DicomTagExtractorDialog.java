/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.TagDict;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.GDicomTools;

/**
 * Seriesレベルのみ
 */
public class DicomTagExtractorDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private JRadioButton rbTreeTable;
	private JRadioButton rbFolder;
	private JButton btnSelectFolder;
	private File selectedFolder = null;

	private JTextField txtSearch;
	private DefaultListModel<String> dictListModel; // 左側の辞書リスト
	private JList<String> listDict;
	private DefaultListModel<String> selectedListModel; // 右側の選択済みリスト
	private JList<String> listSelected;

	private JButton btnExport;
	
	private JProgressBar progressBar;

	private List<String> allTagsList = new ArrayList<>();

	private DatabaseHandler db = DatabaseHandler.getInstance();
	
	// ★ 追加: ダイアログを開いた時点の選択ノードを保持する変数
	private List<DICOMNode> cachedSelectedNodes = null;
	
	private javax.swing.Timer searchTimer;
	
	private volatile boolean isExporting = false;
	private volatile boolean cancelRequested = false;
	
	// --- 管理用（自動出力）タグの定義 ---
	private static final List<String> ADMIN_TAGS = java.util.Arrays.asList(
			"0010,0020 - PatientID",
			"0008,0050 - AccessionNumber", 
			"0008,0060 - Modality", 
			"0008,0020 - StudyDate",
			"0020,000D - StudyInstanceUID", 
			"0020,000E - SeriesInstanceUID", 
			"0008,0018 - SOPInstanceUID");

	public DicomTagExtractorDialog(JFrame parent) {
		super(parent, "DICOM Tag Extractor", true);
		
		MainScreen mainScreen = (MainScreen) WindowManager.getMainScreen();
		if (mainScreen != null) {
			this.cachedSelectedNodes = mainScreen.getSelectedNode();
			if (this.cachedSelectedNodes == null || this.cachedSelectedNodes.isEmpty()) {
				int res = JOptionPane.showConfirmDialog(this, Resources.i18n("DicomTagExtractorDialog.confirm.noSelection"));
				if(res != JOptionPane.YES_OPTION) {
					/*
					 * ここでそのままdisposeするとフリーズする。 
					 * 単にreturnするのではなく、画面が表示された直後に閉じるよう予約する
					 */
					SwingUtilities.invokeLater(() -> this.dispose());
					return;
				}
			}
		}
		
		setSize(800, 400);
		setLocationRelativeTo(parent);
		setLayout(new BorderLayout());
		
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				handleClose();
			}
		});
		
		initUI();
	}

	private void initUI() {
		// 1. 対象データの選択パネル
		JPanel pnlTarget = new JPanel(new BorderLayout());
		pnlTarget.setBorder(BorderFactory.createTitledBorder("1. Select Target"));

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
        pnlTarget.add(pnlRadio);

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

		add(pnlTarget, BorderLayout.NORTH);

		// 2. タグ入力パネル
		JPanel pnlTags = createTagSelectionPanel();
		pnlTags.setBorder(BorderFactory.createTitledBorder("2. Enter DICOM Tags (e.g. 0010,0010)"));
		add(pnlTags, BorderLayout.CENTER);

		// 3. 実行ボタン
		btnExport = new JButton("Extract & Export to CSV");
		btnExport.addActionListener(e -> executeExport());
		
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setVisible(false); // 実行時のみ表示する

		JPanel pnlBottom = new JPanel(new BorderLayout());
		pnlBottom.add(btnExport, BorderLayout.NORTH);
		pnlBottom.add(progressBar, BorderLayout.SOUTH);
		add(pnlBottom, BorderLayout.SOUTH);
		
	}
	
	// ★ 追加: ダイアログが閉じられる際の処理
	private void handleClose() {
		if (isExporting) {
			int result = JOptionPane.showConfirmDialog(this,
					"The export process is currently running. Are you sure you want to cancel and close?",
					"Confirm Exit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {
				cancelRequested = true; // スレッドに中断を要求
				dispose(); // ダイアログを閉じる
			}
			// Noの場合は何もしない（ダイアログは開いたまま処理を継続）
		} else {
			dispose(); // 実行中でなければそのまま閉じる
		}
	}

	private void executeExport() {
		// 1. ユーザー入力の取得（UI操作）
		if (selectedListModel.isEmpty()) {
			JOptionPane.showMessageDialog(this, Resources.i18n("DicomTagExtractorDialog.error.noTags"), Resources.i18n("dialog.title.graphy"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		List<String> tagsToExtract = new ArrayList<>();
		for (int i = 0; i < selectedListModel.size(); i++) {
			tagsToExtract.add(selectedListModel.get(i));
		}
		boolean isTreeTable = rbTreeTable.isSelected();

		if (!isTreeTable && selectedFolder == null) {
			JOptionPane.showMessageDialog(this, Resources.i18n("DicomTagExtractorDialog.error.noFolder"), Resources.i18n("dialog.title.graphy"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// --- ファイル保存先の決定（上書き禁止ロジック） ---
		JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Save Exported CSV");
		File outputFile = null;
		boolean fileValid = false;

		while (!fileValid) {
			int ret = jfc.showSaveDialog(this);
			if (ret != JFileChooser.APPROVE_OPTION) {
				return; // ユーザーがキャンセルした場合は終了
			}

			outputFile = jfc.getSelectedFile();

			// 拡張子の自動付加 (.csv)
			if (!outputFile.getName().toLowerCase().endsWith(".csv")) {
				outputFile = new File(outputFile.getAbsolutePath() + ".csv");
			}

			// 上書きチェック
			if (outputFile.exists()) {
				JOptionPane.showMessageDialog(this,
						"The file already exists. Overwriting is not allowed.\nPlease choose a different file name.",
						"File Already Exists", JOptionPane.WARNING_MESSAGE);
				// ループが継続し、再度 showSaveDialog が開く
			} else {
				fileValid = true; // 存在しないパスが選ばれたのでループ脱出
			}
		}

		// 非同期処理で使うために final 変数にコピー
		final File finalOutputFile = outputFile;

		// ツリーテーブルから選択されたノード(DICOMNode)のリストを取得するのは、必ずEDT上で行う！
		final List<DICOMNode> selectedNodes;
		if (isTreeTable) {
			/*
			 * This lost focus when edit sequence builder
			 */
			//selectedNodes = ((MainScreen) WindowManager.getMainScreen()).getSelectedNode();
			selectedNodes = this.cachedSelectedNodes;//use directly
			if (selectedNodes == null || selectedNodes.isEmpty()) {
				JOptionPane.showMessageDialog(this, Resources.i18n("DicomTagExtractorDialog.error.noSeriesFromTree"), Resources.i18n("dialog.title.graphy"), JOptionPane.INFORMATION_MESSAGE);
				return;
			}
		} else {
			selectedNodes = null; // フォルダ指定の場合は使わない
		}

		// ★ 二重実行を防ぐため、UIコンポーネントをロック（無効化）する
		btnExport.setEnabled(false);
		btnSelectFolder.setEnabled(false);
		rbTreeTable.setEnabled(false);
		rbFolder.setEnabled(false);
		txtSearch.setEnabled(false);
		listDict.setEnabled(false);
		listSelected.setEnabled(false);

		// 2. バックグラウンド処理の開始
		new Thread(() -> {
			
			isExporting = true; // ★ 実行中フラグをON
			cancelRequested = false; // ★ キャンセルフラグを初期化
			PrintWriter csvWriter = null;
			
			try {
				csvWriter = new PrintWriter(new FileWriter(finalOutputFile));

				// --- CSVヘッダーの生成 ---
				StringBuilder headerStr = new StringBuilder("SeriesSource");
				
				// A. まず管理タグを出力
				for (String admin : ADMIN_TAGS) {
					String name = admin.split(" - ")[1];
					headerStr.append(",").append(name);
				}
				
				// B. 次にユーザー指定タグを出力
				for (String tagItem : tagsToExtract) {
					String cleanHeader = tagItem.replaceAll("[0-9A-Fa-f]{4},[0-9A-Fa-f]{4} - ", "");
					cleanHeader = cleanHeader.replace(" > ", ".");
					headerStr.append(",").append(cleanHeader);
				}
				csvWriter.println(headerStr.toString());

				// --- データ抽出ループ ---
				List<String> errorLog = new ArrayList<>();
				List<File> targetDicomFiles = getTargetDicomFilesSafe(isTreeTable, selectedNodes, errorLog);
				final DICOMBackend backend = DICOMBackend.getCurrent();
				
				if (cancelRequested) {
					if(csvWriter != null) {
						csvWriter.close();
					}
					Log.logger.info("Extraction process was interrupted.");
					return;
				}
				
				// ★追加：プログレスバーの初期化
				int totalSeries = targetDicomFiles.size();
				SwingUtilities.invokeLater(() -> {
					progressBar.setMaximum(totalSeries);
					progressBar.setValue(0);
					progressBar.setVisible(true);
				});

				for (int i =0; i < totalSeries; i++) {
					
					if (cancelRequested) {
						Log.logger.info("Extraction process was interrupted.");
						break; // ループを中断
					}
					
					File dcmFile = targetDicomFiles.get(i);
					StringBuilder row = new StringBuilder(dcmFile.getCanonicalPath());
					DicomImage dcm = DicomImage.newDicomImage(dcmFile.getCanonicalPath(),false, backend);
					DicomObject header = dcm.getHeader();

					// ★抽出対象の全リスト（管理タグ + ユーザー指定タグ）を連結
					List<String> totalTags = new ArrayList<>(ADMIN_TAGS);
					totalTags.addAll(tagsToExtract);

					for (String tagStr : totalTags) {
						String val = "N/A";
						if (header != null) {
							try {
								String[] pathParts = tagStr.split(" > ");
								DicomObject currentObj = header;
								
								for (int j = 0; j < pathParts.length; j++) {
									String hexTag = pathParts[j].substring(0, 9).replace(",", "");
									int tagInt = Integer.parseUnsignedInt(hexTag, 16);
									
									if (j == pathParts.length - 1) {
										String[] vals = currentObj.getStrings(tagInt);
										if (vals != null && vals.length > 0) {
											val = String.join("\\", vals);
										} else {
											String singleVal = currentObj.getString(tagInt);
											if (singleVal != null) val = singleVal;
										}
									} else {
										currentObj = currentObj.getNestedDataset(tagInt);
										if (currentObj == null) break;
									}
								}
							} catch (Exception e) {
								val = "ERROR";
							}
						}
						row.append(",\"").append(val.replace("\"", "\"\"")).append("\"");
					}
					csvWriter.println(row.toString());
					// プログレスバーの更新
					final int currentProgress = i + 1;
					SwingUtilities.invokeLater(() -> progressBar.setValue(currentProgress));
				}
				
				/*
				 *ここまで処理できていたら、ほぼ完了なので、最後まで実行する 
				 */
//				if (cancelRequested) {
//					Log.logger.info("Extraction process was interrupted.");
//					return;
//				}
				
				// ログの書き出し処理
				if (!errorLog.isEmpty()) {
					File logFile = new File(finalOutputFile.getAbsolutePath().replace(".csv", "_log.txt"));
					try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFile))) {
						for (String logMsg : errorLog) {
							logWriter.println(logMsg);
						}
					} catch (Exception e) {
						Log.logger.warning("DicomTagExtractorDialog: Failed to write log file: " + e.getMessage());
					}
				}
				
				// CSVと同じ階層に出力したタグリストが記録された .propertiesファイル を自動出力
				File propFile = new File(finalOutputFile.getAbsolutePath().replace(".csv", ".properties"));
				java.util.Properties props = new java.util.Properties();
				for (int i = 0; i < tagsToExtract.size(); i++) {
					props.setProperty("tag." + i, convertToTagOnly(tagsToExtract.get(i)));
				}
				try (java.io.FileWriter fw = new java.io.FileWriter(propFile)) {
					props.store(fw, "DICOM Tag Extractor Profile");
				}

				// 3. 完了通知（UIスレッドに戻す）
				SwingUtilities.invokeLater(() -> {
					String msg = "Export successfully completed!\n" + finalOutputFile.getAbsolutePath();
					if (!errorLog.isEmpty()) {
						msg += "\n\n(See _log.txt for warnings/errors.)";
					}
					JOptionPane.showMessageDialog(this, msg);
					dispose();
				});
			} catch (Exception ex) {
				if (!cancelRequested) {
					Log.logger.warning("DicomTagExtractorDialog: " + ex.getMessage());
					SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(this, Resources.i18n("DicomTagExtractorDialog.error.export") + " " + ex.getMessage(), Resources.i18n("dialog.title.error"),
							JOptionPane.ERROR_MESSAGE);
						btnExport.setEnabled(true);
						btnSelectFolder.setEnabled(true);
						rbTreeTable.setEnabled(true);
						rbFolder.setEnabled(true);
						txtSearch.setEnabled(true);
						listDict.setEnabled(true);
						listSelected.setEnabled(true);
					});
				}
			}finally {
				// ★ エラー時やキャンセル時でも確実にファイルをクローズし、実行中フラグを下ろす
				if (csvWriter != null) {
					try { csvWriter.close(); } catch (Exception ignored) {}
				}
				isExporting = false; 
			}
		}).start();
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
        	if (cancelRequested) return;
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

	// ローカルフォルダの走査（SeriesInstanceUIDでグルーピング）
	private void searchDicomFilesInFolder(File dir, Map<String, List<File>> seriesMap, List<String> errorLog) {
		if (cancelRequested) return;
		if (dir == null || !dir.isDirectory())
			return;

		File[] files = dir.listFiles();
		if (files == null)
			return;

		for (File f : files) {
			if (f.isDirectory()) {
				searchDicomFilesInFolder(f, seriesMap, errorLog);
			} else {
				if (DicomUtilities.isDICOMDIR(f))
					continue; // 除外
				if (!DicomUtilities.isDicomFile(f))
					continue; // DICOM以外は無視

				// このファイルのSeriesInstanceUIDを取得してマップに追加
				String seriesUID = DicomUtilities.getSeriesInstanceUID(f.getAbsolutePath());
				if (seriesUID != null && !seriesUID.trim().isEmpty()) {
					seriesMap.computeIfAbsent(seriesUID.trim(), k -> new ArrayList<>()).add(f);
				} else {
					errorLog.add("Failed to read SeriesUID: " + f.getAbsolutePath());
				}
			}
		}
	}


	// 検索文字列から正規表現パターンを作るユーティリティ
	private String createRegexFromWildcard(String searchStr) {
		if (searchStr == null || searchStr.trim().isEmpty()) {
			return ".*"; // 空欄ならすべて表示
		}
		// 1. 正規表現の特殊文字をエスケープ
		String regex = searchStr.trim().replace("\\", "\\\\").replace(".", "\\.").replace("+", "\\+")
				.replace("?", "\\?").replace("(", "\\(").replace(")", "\\)").replace("[", "\\[").replace("]", "\\]");

		// 2. ユーザーが入力した '*' を、正規表現の '.*' に変換
		regex = regex.replace("*", ".*");

		// 3. 大文字小文字を無視する (?i) を付ける
		return "(?i).*" + regex + ".*";
	}

	private JPanel createTagSelectionPanel() {
		JPanel pnlTags = new JPanel(new BorderLayout(5, 5));
		pnlTags.setBorder(BorderFactory.createTitledBorder("2. Select DICOM Tags"));

		// --- 左側：検索＆辞書リスト ---
		JPanel pnlLeft = new JPanel(new BorderLayout());
		pnlLeft.setPreferredSize(new Dimension(300, 0)); 
        pnlLeft.setMinimumSize(new Dimension(200, 0));
        
		dictListModel = new DefaultListModel<>();
		listDict = new JList<>(dictListModel);
		loadDicomDictionary(); // ★ここで全てのタグをロード
		
		txtSearch = new JTextField();
		txtSearch.setToolTipText("Search tags (e.g., *Name, 0010*)");
		// 検索窓に入力されるたびにリストを絞り込む
		// ★ タイマーの初期化 (300ms待ち)
	    searchTimer = new javax.swing.Timer(300, e -> filterList());
	    searchTimer.setRepeats(false);

	    // ★ リスナーで直接 filterList を呼ばず、triggerSearch を呼ぶように変更
	    txtSearch.getDocument().addDocumentListener(new DocumentListener() {
	        public void insertUpdate(DocumentEvent e) { triggerSearch(); }
	        public void removeUpdate(DocumentEvent e) { triggerSearch(); }
	        public void changedUpdate(DocumentEvent e) { triggerSearch(); }
	    });

		pnlLeft.add(txtSearch, BorderLayout.NORTH);
		pnlLeft.add(new JScrollPane(listDict), BorderLayout.CENTER);

		// --- 中央：追加・削除ボタン ---
		JPanel pnlCenter = new JPanel(new GridBagLayout());
		pnlCenter.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10)); // 左右に少し余白を入れて圧迫感を防ぐ
		JButton btnAdd = new JButton("Add >");
		JButton btnAddNested = new JButton("Add Nested >"); // sequence or private tag
		JButton btnRemove = new JButton("Remove");

		btnAdd.addActionListener(e -> {
			for (String val : listDict.getSelectedValuesList()) {
				if (!selectedListModel.contains(val))
					selectedListModel.addElement(val);
			}
		});

		// シーケンス追加ロジック (親 > 子 の形を作る)
		btnAddNested.addActionListener(e -> {
			// 新しい構築ダイアログを表示
			NestedTagBuilderDialog builder = new NestedTagBuilderDialog(this, allTagsList);
			builder.setVisible(true);

			// 結果を受け取ってリストに追加
			String result = builder.getResult();
			if (result != null && !selectedListModel.contains(result)) {
				selectedListModel.addElement(result);
			}
		});

		btnRemove.addActionListener(e -> {
			for (String val : listSelected.getSelectedValuesList()) {
				selectedListModel.removeElement(val);
			}
		});

		// GridBagConstraints を使って、配置のルールを細かく指定します
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0; // 縦一列に並べる
		gbc.gridy = GridBagConstraints.RELATIVE; // 自動で次の行へ
		gbc.fill = GridBagConstraints.HORIZONTAL; // ★横幅だけをボタンの最大幅に合わせて揃える（縦は伸ばさない）
		gbc.insets = new Insets(10, 0, 10, 0); // ボタン間の上下の余白（10px）

		pnlCenter.add(btnAdd, gbc);
		pnlCenter.add(btnAddNested, gbc);
		pnlCenter.add(btnRemove, gbc);

		// --- 右側：抽出対象リスト ---
		JPanel pnlRight = new JPanel(new BorderLayout());
		selectedListModel = new DefaultListModel<>();
		listSelected = new JList<>(selectedListModel);
		
		// ★ 追加：上部にヘッダとLoadボタンを配置
		JPanel pnlRightHeader = new JPanel(new BorderLayout());
		pnlRightHeader.add(new JLabel("Tags to Extract:"), BorderLayout.WEST);
		JButton btnLoadProps = new JButton("Load .properties");
		btnLoadProps.addActionListener(e -> loadPropertiesViaDialog());
		pnlRightHeader.add(btnLoadProps, BorderLayout.EAST);
		
		pnlRight.add(new JLabel("Tags to Extract:"), BorderLayout.NORTH);
		pnlRight.add(new JScrollPane(listSelected), BorderLayout.CENTER);
		
		// ドラッグ＆ドロップ (D&D) のサポート
		listSelected.setTransferHandler(new TransferHandler() {
			@Override
			public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
			}

			@SuppressWarnings("unchecked")
			@Override
			public boolean importData(TransferSupport support) {
				if (!canImport(support))
					return false;
				try {
					java.util.List<File> files = (java.util.List<File>) support.getTransferable()
							.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
					for (File file : files) {
						if (file.getName().toLowerCase().endsWith(".properties")) {
							loadPropertiesFile(file); // 後で作るメソッド
						}
					}
					return true;
				} catch (Exception e) {
					Log.logger.warning("DicomTagExtractorDialog: Error during drag-and-drop import: " + e.getMessage());
				}
				return false;
			}
		});

		// --- 全体の組み込み ---
		// 1. 右側エリア（ボタン群 ＋ 選択済みリスト）を構成
		JPanel pnlRightContainer = new JPanel(new BorderLayout());

		// ボタンパネルの周りに少し余白(左右10px)を入れて圧迫感を減らす
		pnlCenter.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

		pnlRightContainer.add(pnlCenter, BorderLayout.WEST);
		pnlRightContainer.add(pnlRight, BorderLayout.CENTER);

		// 2. SplitPaneの構成
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pnlLeft, pnlRightContainer);
		splitPane.setResizeWeight(0.5); // 中央で分割
		splitPane.setContinuousLayout(true); // ★ドラッグ中に中身をリアルタイム描画する（UX向上）

        // 初期ディバイダー位置を 1/3 (0.33) に設定する
        // invokeLater を使って描画直後に位置を確定させるのが確実です。
        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.33);
        });
		
		// 3. 親パネルへ追加
		pnlTags.add(splitPane, BorderLayout.CENTER);

		return pnlTags;
	}
	
	// --- プロパティファイル入出力用ユーティリティ ---

	/** フル文字列("0010,0010 - PatientName")からタグ番号のみ("0010,0010")を抽出する */
	private String convertToTagOnly(String fullString) {
		String[] parts = fullString.split(" > ");
		List<String> tagOnlyParts = new ArrayList<>();
		for (String part : parts) {
			tagOnlyParts.add(part.substring(0, 9)); // 先頭の "XXXX,XXXX" を取得
		}
		return String.join(" > ", tagOnlyParts);
	}

	/** タグ番号("0010,0010")から辞書を引いてフル文字列を復元する */
	private String restoreFullTagString(String tagOnlyString) {
		String[] parts = tagOnlyString.split(" > ");
		List<String> fullParts = new ArrayList<>();
		for (String part : parts) {
			String hexTag = part.trim();
			String fullPart = hexTag; // 辞書にない場合のフォールバック
			for (String dictTag : allTagsList) {
				if (dictTag.startsWith(hexTag)) {
					fullPart = dictTag;
					break;
				}
			}
			fullParts.add(fullPart);
		}
		return String.join(" > ", fullParts);
	}

	/** メニュー(ボタン)からのプロパティ読み込みダイアログ表示 */
	private void loadPropertiesViaDialog() {
		JFileChooser jfc = new JFileChooser();
		jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		jfc.setDialogTitle("Load Extractor Properties");
		jfc.setFileFilter(
				new javax.swing.filechooser.FileNameExtensionFilter("Properties File (*.properties)", "properties"));
		if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			loadPropertiesFile(jfc.getSelectedFile());
		}
	}

	/** プロパティファイルの読み込みとリストへの追加（重複排除） */
	private void loadPropertiesFile(File file) {
		try {
			java.util.Properties props = new java.util.Properties();
			try (java.io.FileReader fr = new java.io.FileReader(file)) {
				props.load(fr);
			}

			// プロパティの値（タグ番号のみ）をループで取得
			for (Object key : props.keySet()) {
				String tagOnly = props.getProperty((String) key);
				String fullTagString = restoreFullTagString(tagOnly);

				// 重複していなければ追加
				if (!selectedListModel.contains(fullTagString)) {
					selectedListModel.addElement(fullTagString);
				}
			}
		} catch (Exception e) {
			Log.logger.warning("DicomTagExtractorDialog: Failed to load properties file: " + e.getMessage());
			JOptionPane.showMessageDialog(this, Resources.i18n("DicomTagExtractorDialog.error.loadProperties"), Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
		}
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
	
	private void triggerSearch() {
        if (searchTimer.isRunning()) {
            searchTimer.restart();
        } else {
            searchTimer.start();
        }
    }

	private void loadDicomDictionary() {
		allTagsList.clear();
		dictListModel.clear();

		try {
			// TagDict からすべてのタグ情報（"0x00100010" = "PatientName"）を取得
			HashMap<String, String> tagMap = TagDict.getTagAndKeywordSet();

			for (Map.Entry<String, String> entry : tagMap.entrySet()) {
				String hexStr = entry.getKey(); // 例: "0x00100010"
				String keyword = entry.getValue(); // 例: "PatientName"

				if (hexStr != null && keyword != null && hexStr.startsWith("0x") && hexStr.length() == 10) {
					// "0x00100010" を "0010,0010" のフォーマットに整形
					String formattedHex = hexStr.substring(2, 6).toUpperCase() + ","
							+ hexStr.substring(6, 10).toUpperCase();

					// UI表示用の文字列を作る（例: "0010,0010 - PatientName"）
					String displayStr = formattedHex + " - " + keyword;
					
					// ★管理タグに含まれている場合は、UI用リストに追加しない
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

			// タグ番号順（アルファベット順）にソートして見やすくする
			Collections.sort(allTagsList);

			// UIのリストに表示
			for (String str : allTagsList) {
				dictListModel.addElement(str);
			}

		} catch (Exception e) {
			Log.logger.warning("DicomTagExtractorDialog: Failed to load DICOM tag dictionary.");
			// フォールバック
			allTagsList.add("0010,0010 - PatientName");
			allTagsList.add("0020,000D - StudyInstanceUID");
			for (String str : allTagsList) {
				dictListModel.addElement(str);
			}
		}
	}
}
