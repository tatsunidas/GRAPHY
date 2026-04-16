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
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.dicom.TagDict;

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

		// ★ 修正ポイントA: バックグラウンド処理に入る前に、必要なUI情報を確定させる
		// ツリーテーブルから選択されたノード(DICOMNode)のリストを取得するのは、必ずEDT上で行う！
		final List<DICOMNode> selectedNodes;
		if (isTreeTable) {
			selectedNodes = WindowManager.getMainScreen().getSelectedNode();
			if (selectedNodes == null || selectedNodes.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Please select at least one series from the TreeTable.");
				return;
			}
		} else {
			selectedNodes = null; // フォルダ指定の場合は使わない
		}

		// ★ 修正ポイントB: 二重実行を防ぐため、UIコンポーネントをロック（無効化）する
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
				PrintWriter csvWriter = new PrintWriter(new FileWriter(finalOutputFile));

				StringBuilder header = new StringBuilder("SeriesSource");
				for (String tagItem : tagsToExtract) {
					// 例1: "0010,0010 - PatientName" -> "PatientName"
					// 例2: "SeqName > 0010,0010 - PatName" -> "SeqName.PatName"
					String cleanHeader = tagItem.replaceAll("[0-9A-Fa-f]{4},[0-9A-Fa-f]{4} - ", "");
					cleanHeader = cleanHeader.replace(" > ", ".");

					header.append(",").append(cleanHeader);
				}
				csvWriter.println(header.toString());

				// ★ 修正ポイントC: 重いファイル検索やDBアクセスはここで行う
				List<File> targetDicomFiles = getTargetDicomFilesSafe(isTreeTable, selectedNodes);

				for (File dcmFile : targetDicomFiles) {
					StringBuilder row = new StringBuilder(dcmFile.getName());

					for (String tagStr : tagsToExtract) {
						String val = "N/A";
						// ここにタグ抽出のコードを記述
						row.append(",\"").append(val.replace("\"", "\"\"")).append("\"");
					}
					csvWriter.println(row.toString());
				}

				csvWriter.close();

				// 3. 完了通知（UIスレッドに戻す）
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(this,
							"Export successfully completed!\n" + finalOutputFile.getAbsolutePath());
					dispose(); // 成功したらダイアログを閉じるのでロック解除は不要
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

	/**
	 * @param isTreeTable
	 * @param selectedNodes
	 * @return
	 */
	private List<File> getTargetDicomFilesSafe(boolean isTreeTable, List<DICOMNode> selectedNodes) {
		List<File> targetFiles = new ArrayList<>();
		if (isTreeTable) {
			// UIから渡された selectedNodes を使ってデータベース等にアクセスする（UIには触れない）
			for (DICOMNode node : selectedNodes) {
				if (node.getLevel() == DICOMNode.STUDY) {
					/*
					 * 再帰処理
					 */
				} else if (node.getLevel() == DICOMNode.SERIES) {

				} else if (node.getLevel() == DICOMNode.IMAGE) {
					/*
					 * 通常の利用方法ではないと考えるが、念の為。
					 */
				}
			}
		} else {
			// ローカルフォルダの走査
			searchDicomFilesInFolder(selectedFolder, targetFiles);
		}
		return targetFiles;
	}

	private void searchDicomFilesInFolder(File dir, List<File> list) {
		if (dir == null || !dir.isDirectory())
			return;
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				searchDicomFilesInFolder(f, list);
			} else if (f.getName().toLowerCase().endsWith(".dcm")) {
				/*
				 * 以下、走査対象外 dicom dir シリーズに紛れているセカンダリキャプチャ（ただし、セカンダリキャプチャのみのシリーズは走査対象とする）
				 * シリーズに紛れているキーオブジェクト等。画像以外の紛れている系のデータ。紛れておらず、単体で存在する場合は、走査する。
				 * 
				 * 上記、原則としてDICOMファイルであること。他に条件は必要だろうか？あれば条件を追加したい。読み込めなかったら？失敗データとしてログをテキストとして出力
				 * （ファイル名はCSVに合わせて、_log.txtなどにする）する。
				 */
				// 簡易実装: とりあえず見つかったdcmをすべて入れる
				list.add(f);
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
		gbc.gridx = 0;                             // 縦一列に並べる
		gbc.gridy = GridBagConstraints.RELATIVE;   // 自動で次の行へ
		gbc.fill = GridBagConstraints.HORIZONTAL;  // ★横幅だけをボタンの最大幅に合わせて揃える（縦は伸ばさない）
		gbc.insets = new Insets(10, 0, 10, 0);     // ボタン間の上下の余白（10px）

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
