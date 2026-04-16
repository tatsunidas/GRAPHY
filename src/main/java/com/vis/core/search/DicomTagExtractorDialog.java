/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.search;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.vis.core.facade.WindowManager;
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

	// --- クラスのフィールドに追加 ---
	private JTextField txtSearch;
	private DefaultListModel<String> dictListModel; // 左側の辞書リスト
	private JList<String> listDict;
	private DefaultListModel<String> selectedListModel; // 右側の選択済みリスト
	private JList<String> listSelected;

	private JButton btnExport;

	private List<String> allTagsList = new ArrayList<>();
	
	private DatabaseHandler db = DatabaseHandler.getInstance();

	public DicomTagExtractorDialog(JFrame parent) {
		super(parent, "DICOM Tag Extractor", true);
		setSize(800, 400);
		setLocationRelativeTo(parent);
		setLayout(new BorderLayout());
		initUI();
	}

	private void initUI() {
		// 1. 対象データの選択パネル
		JPanel pnlTarget = new JPanel(new GridLayout(3, 1));
		pnlTarget.setBorder(BorderFactory.createTitledBorder("1. Select Target"));

		rbTreeTable = new JRadioButton("Selected Series from TreeTable (HOME)", true);
		rbFolder = new JRadioButton("Select Local Folder...");

		ButtonGroup bg = new ButtonGroup();
		bg.add(rbTreeTable);
		bg.add(rbFolder);

		btnSelectFolder = new JButton("Browse");
		btnSelectFolder.setEnabled(false);

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

		pnlTarget.add(rbTreeTable);
		pnlTarget.add(rbFolder);
		pnlTarget.add(btnSelectFolder);
		add(pnlTarget, BorderLayout.NORTH);

		// 2. タグ入力パネル
		JPanel pnlTags = createTagSelectionPanel();
		pnlTags.setBorder(BorderFactory.createTitledBorder("2. Enter DICOM Tags (e.g. 0010,0010)"));
//		txtTags = new JTextArea("0010,0010\n0020,000D\n0020,000E"); // デフォルト値の例
//		pnlTags.add(new JScrollPane(txtTags), BorderLayout.CENTER);
		add(pnlTags, BorderLayout.CENTER);

		// 3. 実行ボタン
		btnExport = new JButton("Extract & Export to CSV");
		btnExport.addActionListener(e -> executeExport());
		add(btnExport, BorderLayout.SOUTH);
	}

	private void executeExport() {
		// 1. ユーザー入力の取得（UI操作）
		if (selectedListModel.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please select at least one tag to extract.");
			return;
		}

		List<String> tagsToExtract = new ArrayList<>();
		for (int i = 0; i < selectedListModel.size(); i++) {
			tagsToExtract.add(selectedListModel.get(i));
		}
		boolean isTreeTable = rbTreeTable.isSelected();

		if (!isTreeTable && selectedFolder == null) {
			JOptionPane.showMessageDialog(this, "Please select a target folder.");
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
			selectedNodes = ((MainScreen)WindowManager.getMainScreen()).getSelectedNode();
			if (selectedNodes == null || selectedNodes.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Please select at least one series from the TreeTable.");
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
			try {
				
				// ログを記録するリスト
				PrintWriter csvWriter = new PrintWriter(new FileWriter(finalOutputFile));

				StringBuilder headerStr = new StringBuilder("SeriesSource");
				for (String tagItem : tagsToExtract) {
					String cleanHeader = tagItem.replaceAll("[0-9A-Fa-f]{4},[0-9A-Fa-f]{4} - ", "");
					cleanHeader = cleanHeader.replace(" > ", ".");
					headerStr.append(",").append(cleanHeader);
				}
				csvWriter.println(headerStr.toString());

				// 重いファイル検索やDBアクセスはここで行う
				// ログを記録するリスト
				List<String> errorLog = new ArrayList<>();
				List<File> targetDicomFiles = getTargetDicomFilesSafe(isTreeTable, selectedNodes, errorLog);
				
				final DICOMBackend backend = DICOMBackend.getCurrent();

				for (File dcmFile : targetDicomFiles) {
					StringBuilder row = new StringBuilder(dcmFile.getCanonicalPath());
					
					// DicomImageを使ってヘッダ情報のみをパース（ピクセルは読み込まないので高速）
					DicomImage dcm = DicomImage.newDicomImage(dcmFile.getCanonicalPath(), backend);
					DicomObject header = dcm.getHeader();

					for (String tagStr : tagsToExtract) {
						String val = "N/A";
						
						if (header != null) {
							try {
								// 1. " > " で分割し、階層の配列にする
								String[] pathParts = tagStr.split(" > ");
								DicomObject currentObj = header;
								
								for (int i = 0; i < pathParts.length; i++) {
									// 2. 文字列からタグの16進数部分を抽出 ("0010,0010 - PatientName" -> "00100010")
									String hexTag = pathParts[i].substring(0, 9).replace(",", "");
									
									// 3. 16進数文字列をint型のタグ番号に変換（符号なし32bit）
									int tagInt = Integer.parseUnsignedInt(hexTag, 16);
									
									if (i == pathParts.length - 1) {
										// --- 最終階層：値の取得 ---
										
										// 配列（複数値）を持つタグ（Image Position Patient等）に対応するため getStrings を使用
										String[] vals = currentObj.getStrings(tagInt);
										if (vals != null && vals.length > 0) {
											val = String.join("\\", vals); // DICOM標準の "\" 区切りで結合
										} else {
											// getStrings で取れない場合（稀）のためのフォールバック
											String singleVal = currentObj.getString(tagInt);
											if (singleVal != null) {
												val = singleVal;
											}
										}
									} else {
										// --- 途中階層：シーケンスの中（Item #1）に潜る ---
										currentObj = currentObj.getNestedDataset(tagInt);
										
										if (currentObj == null) {
											// シーケンスが存在しなければループを抜ける（値は "N/A" のまま）
											break; 
										}
									}
								}
							} catch (Exception e) {
								val = "ERROR";
								e.printStackTrace();
							}
						}
						
						// CSVのエスケープ処理（ダブルクォーテーションを2つに重ねる）をして追加
						row.append(",\"").append(val.replace("\"", "\"\"")).append("\"");
					}
					csvWriter.println(row.toString());
				}

				csvWriter.close();
				
				// ★ ログの書き出し処理
				if (!errorLog.isEmpty()) {
					File logFile = new File(finalOutputFile.getAbsolutePath().replace(".csv", "_log.txt"));
					try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFile))) {
						for (String logMsg : errorLog) {
							logWriter.println(logMsg);
						}
					} catch (Exception e) {
						e.printStackTrace(); // ログ書き込み失敗時
					}
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
				ex.printStackTrace();
				// エラー時はダイアログを閉じず、ロックを解除して再試行できるようにする
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(this, "Error during export: " + ex.getMessage(), "Error",
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
		}).start();
	}

	private List<File> getTargetDicomFilesSafe(boolean isTreeTable, List<DICOMNode> selectedNodes, List<String> errorLog) {
		List<File> targetFiles = new ArrayList<>();
		
		if (isTreeTable) {
			if (db == null) {
				errorLog.add("Error: Database connection not found.");
				return targetFiles;
			}
			// ツリー展開処理
			collectFilesFromTree(selectedNodes, targetFiles, errorLog);
		} else {
			// ローカルフォルダの走査：SeriesInstanceUIDごとにファイルをグルーピングする
			Map<String, List<File>> seriesMap = new HashMap<>();
			searchDicomFilesInFolder(selectedFolder, seriesMap, errorLog);
			
			// 各シリーズの中から代表となる1枚を選出
			for (Map.Entry<String, List<File>> entry : seriesMap.entrySet()) {
				File repFile = GDicomTools.getRepresentativeFileOfSeries(entry.getValue(), errorLog);
				if (repFile != null) {
					targetFiles.add(repFile);
				} else {
					errorLog.add("Error: No valid representative file found for series: " + entry.getKey());
				}
			}
		}
		return targetFiles;
	}

//	// ツリーテーブルからの再帰的なファイル収集
//	private void collectFilesFromTree(List<DICOMNode> nodes, List<File> targetFiles, List<String> errorLog) {
//		for (DICOMNode node : nodes) {
//			if (node.getLevel() == DICOMNode.STUDY) {
//				List<DICOMNode> seriesList = node.getChildren();
//				collectFilesFromTree(seriesList, targetFiles, errorLog); // 再帰呼び出し
//			} else if (node.getLevel() == DICOMNode.SERIES) {
//				// シリーズ内の全ファイルパスを取得
//				List<String> seriesFilePaths = db.getFileLocationsSeriesLevel(
//						node.getData(DICOMNode.StudyInstanceUID), 
//						node.getData(DICOMNode.SeriesInstanceUID));
//				
//				if (seriesFilePaths != null && !seriesFilePaths.isEmpty()) {
//					// StringのリストをFileのリストに変換
//					List<File> filesToEvaluate = new ArrayList<>();
//					for (String path : seriesFilePaths) filesToEvaluate.add(new File(path));
//					
//					// 代表ファイルを選出
//					File repFile = GDicomTools.getRepresentativeFileOfSeries(filesToEvaluate, errorLog);
//					if (repFile != null) {
//						targetFiles.add(repFile);
//					} else {
//						errorLog.add("Error: No valid file found in DB for Series: " + node.getData(DICOMNode.SeriesInstanceUID));
//					}
//				}
//			}
//		}
//	}
	
	// ツリーテーブルからの再帰的なファイル収集
		private void collectFilesFromTree(List<DICOMNode> nodes, List<File> targetFiles, List<String> errorLog) {
			for (DICOMNode node : nodes) {
				// ★修正1: PATIENT階層が選ばれていた場合は、下層（STUDY等）へ潜る
				if (node.getLevel() == DICOMNode.PATIENT || node.getLevel() == DICOMNode.ROOT) {
					List<DICOMNode> children = node.getChildren();
					if (children != null) collectFilesFromTree(children, targetFiles, errorLog);
					
				} else if (node.getLevel() == DICOMNode.STUDY) {
					List<DICOMNode> seriesList = node.getChildren();
					if (seriesList != null) collectFilesFromTree(seriesList, targetFiles, errorLog);
					
				} else if (node.getLevel() == DICOMNode.SERIES) {
					String studyUID = node.getData(DICOMNode.StudyInstanceUID);
					String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
					
					// ★修正2（最重要）: DICOM特有の見えないパディング（Null文字や空白）を完全に除去してDBの検索ミスを防ぐ
					if (studyUID != null) studyUID = studyUID.trim().replace("\0", "");
					if (seriesUID != null) seriesUID = seriesUID.trim().replace("\0", "");
					
					List<String> seriesFilePaths = db.getFileLocationsSeriesLevel(studyUID, seriesUID);
					
					if (seriesFilePaths != null && !seriesFilePaths.isEmpty()) {
						List<File> filesToEvaluate = new ArrayList<>();
						for (String path : seriesFilePaths) filesToEvaluate.add(new File(path));
						
						File repFile = GDicomTools.getRepresentativeFileOfSeries(filesToEvaluate, errorLog);
						if (repFile != null) {
							targetFiles.add(repFile);
						} else {
							errorLog.add("Error: シリーズ内に有効な画像が存在しません (Series=" + seriesUID + ")");
						}
					} else {
						errorLog.add("Error: DBからファイルパスを取得できませんでした (StudyUID=" + studyUID + ", SeriesUID=" + seriesUID + ")");
					}
				}
			}
		}

	// ローカルフォルダの走査（SeriesInstanceUIDでグルーピング）
	private void searchDicomFilesInFolder(File dir, Map<String, List<File>> seriesMap, List<String> errorLog) {
		if (dir == null || !dir.isDirectory()) return;
		
		File[] files = dir.listFiles();
		if (files == null) return;
		
		for (File f : files) {
			if (f.isDirectory()) {
				searchDicomFilesInFolder(f, seriesMap, errorLog);
			} else {
				if (DicomUtilities.isDICOMDIR(f)) continue; // 除外
				if (!DicomUtilities.isDicomFile(f)) continue; // DICOM以外は無視
				
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
//
//	/**
//	 * 再帰的にファイルを探索し、SeriesInstanceUIDをキーにしてMapに分類するヘルパーメソッド
//	 */
//	private void collectFilesToMap(File dir, Map<String, List<File>> seriesMap, List<String> errorLog) {
//		if (dir == null || !dir.isDirectory()) return;
//
//		File[] files = dir.listFiles();
//		if (files == null) return;
//
//		for (File f : files) {
//			if (f.isDirectory()) {
//				collectFilesToMap(f, seriesMap, errorLog);
//			} else {
//				// --- 走査対象外の除外 ---
//				
//				// 1. DICOMDIRの除外
//				if (DicomUtilities.isDICOMDIR(f)) {
//					continue; 
//				}
//				
//				// 2. 明らかにDICOMではないファイルの除外（拡張子で軽く弾いてから中身を見る）
//				// 拡張子がないDICOMもあるため、拡張子チェックは必須ではありませんが高速化のために .dcm を優先しても良いです。
//				if (!DicomUtilities.isDicomFile(f)) {
//					// ログが膨大になるので、完全に無関係なファイル（.txtなど）のエラー出力は省略するか適宜調整
//					continue;
//				}
//
//				// --- シリーズUIDの取得と分類 ---
//				String seriesUID = DicomUtilities.getSeriesInstanceUID(f.getAbsolutePath());
//				if (seriesUID != null && !seriesUID.trim().isEmpty()) {
//					// Mapに追加（キーが存在しなければ新しくListを作って追加）
//					seriesMap.computeIfAbsent(seriesUID.trim(), k -> new ArrayList<>()).add(f);
//				} else {
//					errorLog.add("読み込み失敗 (SeriesUID取得不可): " + f.getAbsolutePath());
//				}
//			}
//		}
//	}

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
		txtSearch = new JTextField();
		txtSearch.setToolTipText("Search tags (e.g., *Name, 0010*)");

		dictListModel = new DefaultListModel<>();
		listDict = new JList<>(dictListModel);
		loadDicomDictionary(); // ★ここで全てのタグをロード（後述）

		// 検索窓に入力されるたびにリストを絞り込む
		txtSearch.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				filterList();
			}

			public void removeUpdate(DocumentEvent e) {
				filterList();
			}

			public void changedUpdate(DocumentEvent e) {
				filterList();
			}
		});

		pnlLeft.add(txtSearch, BorderLayout.NORTH);
		pnlLeft.add(new JScrollPane(listDict), BorderLayout.CENTER);

		// --- 中央：追加・削除ボタン ---
		JPanel pnlCenter = new JPanel(new GridBagLayout());
		pnlCenter.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10)); // 左右に少し余白を入れて圧迫感を防ぐ
		JButton btnAdd = new JButton("Add >");
		JButton btnAddNested = new JButton("Add Nested >"); // シーケンス追加用
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
		pnlRight.add(new JLabel("Tags to Extract:"), BorderLayout.NORTH);
		pnlRight.add(new JScrollPane(listSelected), BorderLayout.CENTER);

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

		// 3. 親パネルへ追加
		pnlTags.add(splitPane, BorderLayout.CENTER);

		return pnlTags;
	}

	// 辞書の絞り込みメソッド
	private void filterList() {
		String searchStr = txtSearch.getText();
		String regex = createRegexFromWildcard(searchStr);
		Pattern pattern = Pattern.compile(regex);

		dictListModel.clear();
		// allTagsList は、ロード済みのすべてのタグ文字列を保持しているリスト(List<String>)
		for (String tagStr : allTagsList) {
			if (pattern.matcher(tagStr).matches()) {
				dictListModel.addElement(tagStr);
			}
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

					allTagsList.add(displayStr);
				}
			}

			// タグ番号順（アルファベット順）にソートして見やすくする
			Collections.sort(allTagsList);

			// UIのリストに表示
			for (String str : allTagsList) {
				dictListModel.addElement(str);
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("TagDictからのタグリスト取得に失敗しました。");
			// フォールバック
			allTagsList.add("0010,0010 - PatientName");
			allTagsList.add("0020,000D - StudyInstanceUID");
			for (String str : allTagsList) {
				dictListModel.addElement(str);
			}
		}
	}
}
