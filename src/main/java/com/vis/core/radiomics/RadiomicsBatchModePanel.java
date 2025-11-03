/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.radiomics;

import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.FolderOpener;

/**
 * Execute feature calculations with current settings condition.
 * 
 * @author tatsunidas
 *
 */
public class RadiomicsBatchModePanel extends JPanel {
	
	//debug
	public static void main(String[] args) {
		JFrame f = new JFrame();
		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		f.setSize(500, 500);
		RadiomicsSettings radSetting = new RadiomicsSettings();
		RadiomicsBatchModePanel rbmp = new RadiomicsBatchModePanel(radSetting);
		
		rbmp.setImageFolderPath("/home/tatsunidas/デスクトップ/batch_test_radj/T1_LEFT_PLAQUE/IMAGES");
		rbmp.setMaskFolderPath("/home/tatsunidas/デスクトップ/batch_test_radj/T1_LEFT_PLAQUE/MASKS");
		rbmp.setSaveFolderPath("/home/tatsunidas/デスクトップ/batch_result_radj");
		
		JTabbedPane tabPane = new JTabbedPane();
		tabPane.addTab("Settings", radSetting);
		tabPane.addTab("Batch", rbmp);
		
		f.add(tabPane);
		f.pack();
		f.setVisible(true);
		radSetting.adjustDividerLocation();
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	JTextField text1;
	JTextField text2;
	JTextField text3;
	JTextArea usageTextArea;
	JProgressBar progressBar;
	JButton runButton;
	
	private final String IMAGE_FOLDER_ACT = "IMAGE_FOLDER_ACT";
	private final String MASK_FOLDER_ACT = "MASK_FOLDER_ACT";
	private final String SAVE_FOLDER_ACT = "SAVE_FOLDER_ACT";
	private final String RUN_ACT = "RUN_ACT";
	
	final RadiomicsBatchModePanel comp;
	RadiomicsSettings radSettings;

	public RadiomicsBatchModePanel(RadiomicsSettings radSettings) {
		super();
		comp = this;
		this.radSettings = radSettings;
		buildup();
	}

	void buildup() {
		GridBagLayout gbl = new GridBagLayout();
		setLayout(gbl);
		// GridBagConstraints: レイアウト制約を保持するオブジェクト
		GridBagConstraints c = new GridBagConstraints();

		// ---------- 1行目: Image Parent Folder Path ----------
		// 共通設定: コンポーネント間の余白（上下左右に5ピクセル）
		c.insets = new Insets(5, 5, 5, 5);

		// 1. ラベル (0列目, 0行目)
		JLabel label1 = new JLabel("Image Parent Folder Path:");
		c.gridx = 0; // 列インデックス
		c.gridy = 0; // 行インデックス
		c.weightx = 0.0; // 幅の伸縮なし
		c.fill = GridBagConstraints.NONE; // 塗りつぶしなし
		c.anchor = GridBagConstraints.LINE_END; // 右寄せ
		add(label1, c);

		// 2. テキストフィールド (1列目, 0行目)
		text1 = new JTextField(20); // (20)はヒントの幅
		c.gridx = 1;
		c.gridy = 0;
		c.weightx = 1.0; // ★ここが重要: 幅の伸縮を許可
		c.fill = GridBagConstraints.HORIZONTAL; // ★ここが重要: 水平方向にセルを埋める
		c.anchor = GridBagConstraints.CENTER; // (fillするのであまり影響しない)
		add(text1, c);

		// 3. ボタン (2列目, 0行目)
		JButton button1 = new JButton("Choose");
		button1.setActionCommand(IMAGE_FOLDER_ACT);
		c.gridx = 2;
		c.gridy = 0;
		c.weightx = 0.0; // 幅の伸縮なし
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.LINE_START; // 左寄せ
		add(button1, c);

		// ---------- 2行目: Mask Parent Folder Path ----------
		// 4. ラベル (0列目, 1行目)
		JLabel label2 = new JLabel("Mask Parent Folder Path:");
		c.gridx = 0;
		c.gridy = 1; // ★行インデックスを 1 に変更
		c.weightx = 0.0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.LINE_END;
		add(label2, c);

		// 5. テキストフィールド (1列目, 1行目)
		text2 = new JTextField(20);
		c.gridx = 1;
		c.gridy = 1;
		c.weightx = 1.0; // ★伸縮
		c.fill = GridBagConstraints.HORIZONTAL; // ★水平に埋める
		c.anchor = GridBagConstraints.CENTER;
		add(text2, c);

		// 6. ボタン (2列目, 1行目)
		JButton button2 = new JButton("Choose");
		button2.setActionCommand(MASK_FOLDER_ACT);
		c.gridx = 2;
		c.gridy = 1;
		c.weightx = 0.0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.LINE_START;
		add(button2, c);

		// ---------- 3行目: Output save to ----------

		// 7. ラベル (0列目, 2行目)
		JLabel label3 = new JLabel("Output save to:");
		c.gridx = 0;
		c.gridy = 2; // ★行インデックスを 2 に変更
		c.weightx = 0.0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.LINE_END;
		add(label3, c);

		// 8. テキストフィールド (1列目, 2行目)
		text3 = new JTextField(20);
		c.gridx = 1;
		c.gridy = 2;
		c.weightx = 1.0; // ★伸縮
		c.fill = GridBagConstraints.HORIZONTAL; // ★水平に埋める
		c.anchor = GridBagConstraints.CENTER;
		add(text3, c);

		// 9. ボタン (2列目, 2行目)
		JButton button3 = new JButton("Choose");
		button3.setActionCommand(SAVE_FOLDER_ACT);
		c.gridx = 2;
		c.gridy = 2;
		c.weightx = 0.0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.LINE_START;
		add(button3, c);

		runButton = new JButton("Run Batch");
		runButton.setActionCommand(RUN_ACT);
		// ボタンのフォントを少し大きく太くする（オプション）
		runButton.setFont(runButton.getFont().deriveFont(Font.BOLD, 14f));

		c.gridx = 0;
		c.gridy = 3; // ★ 4行目
		c.gridwidth = 3; // 3列分またぐ
		c.weightx = 1.0;
		c.weighty = 0.0; // 縦には伸びない
		c.fill = GridBagConstraints.HORIZONTAL; // ★ 横幅いっぱいに広げる
		c.anchor = GridBagConstraints.CENTER;
		c.insets = new Insets(15, 5, 10, 5); // ★ 上下の余白を調整 (特に上を多めに)
		add(runButton, c);
		
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true); // "50%" のようにパーセンテージを表示
		progressBar.setVisible(false); // ★ 最初は非表示
		c.gridx = 0;
		c.gridy = 4; // ★ 5行目
		c.gridwidth = 3;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(5, 5, 5, 5);
		add(progressBar, c);

		// ---------- 5行目: 説明ラベル (★ 変更 gridy=4) ----------
		JLabel usageLabel = new JLabel("Batch Mode Usage:");
		c.gridx = 0;
		c.gridy = 5;
		c.gridwidth = 3;
		c.weightx = 1.0;
		c.weighty = 0.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.LINE_START;
		c.insets = new Insets(10, 5, 0, 5);
		add(usageLabel, c);

		// ---------- 6行目: JTextArea (★ 変更 gridy=5) ----------

		usageTextArea = new JTextArea();
		usageTextArea.setEditable(false);
		usageTextArea.setLineWrap(true);
		usageTextArea.setWrapStyleWord(true);
		usageTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		usageTextArea.setRows(5);

		String usageText = "Batchモードは、複数の画像とマスクのペアを一度に処理するためのモードです。\n\n"
				+ "1. 'Image Parent Folder Path'に、処理したい画像が含まれる親フォルダを指定します。\n"
				+ "期待されるフォルダ構成： parent:\n"
				+ "                             case1\n"
				+ "                             	image1\n"
				+ "                                image2\n"
				+ "                                imageX\n"
				+ "                             case2\n"
				+ "                                ...\n"
				+ "                             caseN\n"
				+ "                                 ...\n\n"
				+ "2. 'Mask Parent Folder Path'に、対応するマスク画像が含まれる親フォルダを指定します。\n"
				+ "期待されるフォルダ構成： parent:\n"
				+ "                             case1\n"
				+ "                             	mask1\n"
				+ "                                mask2\n"
				+ "                                maskX\n"
				+ "                             case2\n"
				+ "                                ...\n"
				+ "                             caseN\n"
				+ "                                 ...\n\n"
				+ "各画像は必ずCaseごとにフォルダに格納されている必要があります。\n"
				+ "(例) Imageフォルダが C:\\Data\\Images\\Set1 の場合、Maskは C:\\Data\\Masks\\Set1 である必要があります。\n"
				+ "各画像はスタックされていても構いません。\n"
				+ "画像とマスクのペアがあるもののみが計算対象とされます。\n"
				+ "画像とマスクで画像枚数に違いがある場合、マスクのファイル名末尾に記載されたアンダースコアで区切られたスライス番号（1~N）（'_001'など）を認識して、残りのスライスをブランク画像でパディングします。\n\n"
				+ "3. 'Output save to'に、処理結果CSVを保存するフォルダを指定します。\n";
		usageTextArea.setText(usageText);

		JScrollPane scrollPane = new JScrollPane(usageTextArea);

		c.gridx = 0;
		c.gridy = 6;
		c.gridwidth = 3;
		c.weightx = 1.0;
		c.weighty = 1.0; // 縦の余白を吸収
		c.fill = GridBagConstraints.BOTH; // 縦横両方に広がる
		c.anchor = GridBagConstraints.CENTER;
		c.insets = new Insets(5, 5, 5, 5);
		add(scrollPane, c);
		
		addActionToBtn(button1, text1);
		addActionToBtn(button2, text2);
		addActionToBtn(button3, text3);
		addActionToBtn(runButton, null);
	}
	
	private void addActionToBtn(JButton btn, JTextField tf) {
		if(btn.getActionCommand().equals(IMAGE_FOLDER_ACT)) {
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String p = chooseFolder(comp, "Open Image Parent Folder");
					if(p != null) {
						tf.setText(p);
					}
				}
			});
		}else if(btn.getActionCommand().equals(MASK_FOLDER_ACT)) {
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String p = chooseFolder(comp, "Open Mask Parent Folder");
					if(p != null) {
						tf.setText(p);
					}
				}
			});
		}else if(btn.getActionCommand().equals(SAVE_FOLDER_ACT)) {
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String p = saveFolder(comp, "Choose Save Folder");
					if(p != null) {
						tf.setText(p);
					}
				}
			});
		}else if(btn.getActionCommand().equals(RUN_ACT)) {
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					//check folders
					//validate image mask pair
					//run using threads and show progress.
					run_batch();
				}
			});
		}
		
	}
	
	/**
	 * フォルダ選択ダイアログを表示し、選択されたフォルダのパスを返すメソッド。
	 * @param parentComponent ダイアログの親となるコンポーネント (例: JPanel や JFrame)
	 * @param dialogTitle ダイアログのタイトルバーに表示する文字列
	 * @return 選択されたフォルダの絶対パス。キャンセルされた場合は null を返す。
	 */
	private String chooseFolder(Component parentComponent, String dialogTitle) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(dialogTitle);
		chooser.setCurrentDirectory(new java.io.File(System.getProperty("user.home")));
		int result = chooser.showOpenDialog(parentComponent);
		if (result == JFileChooser.APPROVE_OPTION) {
			java.io.File selectedFolder = chooser.getSelectedFile();
			return selectedFolder.getAbsolutePath();
		} else {
			return null;
		}
	}
	
	private String saveFolder(Component parentComponent, String dialogTitle) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(dialogTitle);
		chooser.setCurrentDirectory(new java.io.File(System.getProperty("user.home")));
		int result = chooser.showSaveDialog(parentComponent);
		if (result == JFileChooser.APPROVE_OPTION) {
			java.io.File selectedFolder = chooser.getSelectedFile();
			return selectedFolder.getAbsolutePath();
		} else {
			return null;
		}
	}
	
	public void setImageFolderPath(String p) {
		text1.setText(p);
	}
	
	public void setMaskFolderPath(String p) {
		text2.setText(p);
	}
	
	public void setSaveFolderPath(String p) {
		text3.setText(p);
	}
	
	private void run_batch() {
		// 1. ボタンを無効化（二重実行防止）
		runButton.setEnabled(false);

		// 2. プログレスバーを表示
		progressBar.setValue(0);
		progressBar.setVisible(true);

		// 3. ログエリアをクリア
		clearLog(); // 内部のStringBuilderをクリア
		usageTextArea.setText("バッチ処理を開始します...\n"); // GUIをクリア

		// 4. テキストフィールドから値を取得
		String imageParentDir = text1.getText();
		String maskParentDir = text2.getText();
		String saveToDir = text3.getText();

		// 5. SwingWorkerを作成して実行
		BatchWorker worker = new BatchWorker(imageParentDir, maskParentDir, saveToDir);

		// 6. JProgressBarを更新するためのリスナーを登録
		worker.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				// "progress" という名前のプロパティが変更されたら
				if ("progress".equals(evt.getPropertyName())) {
					int progress = (Integer) evt.getNewValue();
					progressBar.setValue(progress);
				}
			}
		});

		worker.execute(); // バックグラウンド処理を開始
	}
	
	/**
     * 文字列（ファイル名など）から末尾の連続する整数を抽出します。
     * * 拡張子（.tif など）は自動的に無視されます。
     * 例:
     * "mask_plaque_001.tif" -> 1
     * "mask_plaque_001"     -> 1
     * "image.10"            -> 10
     * "005"                 -> 5
     * "file123"             -> 123
     * "no_number_at_end"    -> empty
     *
     * @param input 入力文字列
     * @return 抽出された整数を Optional でラップして返します。見つからない場合は Optional.empty() を返します。
     */
	public static Optional<Integer> extractTrailingInteger(String input) {
		// 1. 入力が null または空の場合は、即座に empty を返す
		if (input == null || input.trim().isEmpty()) {
			return Optional.empty();
		}
		// 2. 拡張子を取り除く ("mask_plaque_001.tif" -> "mask_plaque_001")
		String baseName = input;
		int dotIndex = input.lastIndexOf('.');
		// ドットが存在し、それが文字列の先頭ではない場合に拡張子とみなす
		if (dotIndex > 0) {
			baseName = input.substring(0, dotIndex);
		}
		// 3. 正規表現でベース名の末尾から数字を検索
		Matcher matcher = LAST_DIGITS_PATTERN.matcher(baseName);

		// 4. マッチした場合
		if (matcher.find()) {
			// マッチしたグループ ((\\d+) の部分) を文字列として取得
			String numberStr = matcher.group(1);
			try {
				// 文字列を整数に変換して Optional でラップして返す
				// "001" は 1 に変換される
				return Optional.of(Integer.parseInt(numberStr));
			} catch (NumberFormatException e) {
				// (理論上、正規表現が \d+ なのでここには到達しないはずだが念のため)
				return Optional.empty();
			}
		}
		return Optional.empty();
	}
	
	// 検証結果を蓄積する
    private StringBuilder log = new StringBuilder();
    
    // ログ出力（コンソールと内部ログの両方）
    private void log(String message) {
        System.out.println(message);
        log.append(message).append("\n");
    }

    /**
     * 検証メソッドの実行
     * @param imageParentDir Imageフォルダ (例: .../Images)
     * @param maskParentDir  Maskフォルダ (例: .../Masks)
     * @return 検証に成功した場合は true、致命的な不備が見つかった場合は false
     */
	public boolean validateDataset(String imageParentDirPath, String maskParentDirPath) {
		clearLog();
		log("=== データセット検証開始 ===");
		
		File imageParentDir = new File(imageParentDirPath);
		File maskParentDir = new File(maskParentDirPath);
		
		if(!imageParentDir.exists() || !maskParentDir.exists()) {
			log("!! 検証失敗: Image親フォルダまたはMask親フォルダが見つかりません。");
			return false;
		}
		
		log("Image親フォルダ: " + imageParentDir.getAbsolutePath());
		log("Mask親フォルダ: " + maskParentDir.getAbsolutePath());

		// --- ステップ1: Caseフォルダのリストアップとペアリング ---
		Map<String, File> imageCaseMap = getCaseFolders(imageParentDir);
		Map<String, File> maskCaseMap = getCaseFolders(maskParentDir);

		if (imageCaseMap.isEmpty()) {
			log("!! 検証失敗: Image親フォルダにCaseフォルダが見つかりません。");
			return false;
		}

		List<String> pairedCaseNames = new ArrayList<>();
		log("\n--- Caseペアリング調査 ---");
		for (String imageCaseName : imageCaseMap.keySet()) {
			if (maskCaseMap.containsKey(imageCaseName)) {
				pairedCaseNames.add(imageCaseName);
			} else {
				log("(情報) Image側のみに存在: " + imageCaseName);
			}
		}
		log("◎ ペアが見つかったCase (検証対象): " + pairedCaseNames);
		log("------------------------\n");

		if (pairedCaseNames.isEmpty()) {
			log("!! 検証失敗: ImageとMaskで一致するCaseフォルダが一つも見つかりません。");
			return false;
		}

		// --- 各ペアの詳細検証 ---
		for (String caseName : pairedCaseNames) {
			log("--- 検証中: [" + caseName + "] ---");
			File imageCaseDir = imageCaseMap.get(caseName);
			File maskCaseDir = maskCaseMap.get(caseName);

			// 画像以外のファイルのチェック
			List<File> nonImageImageCase = getNonImageFiles(imageCaseDir);
			List<File> nonImageMaskCase = getNonImageFiles(maskCaseDir);

			if (!nonImageImageCase.isEmpty()) {
				log(" (警告) [" + caseName + "] Imageフォルダに画像以外のファイルが含まれています: "
						+ nonImageImageCase.stream().map(File::getName).collect(Collectors.joining(", ")));
				return false;
			}
			if (!nonImageMaskCase.isEmpty()) {
				log(" (警告) [" + caseName + "] Maskフォルダに画像以外のファイルが含まれています: "
						+ nonImageMaskCase.stream().map(File::getName).collect(Collectors.joining(", ")));
				return false;
			}

			// ImagePlusとしてスタックを読み込む
			ImagePlus impImage = loadStack(imageCaseDir);
			ImagePlus impMask = loadStack(maskCaseDir);

			// ステップ2: 画像の存在チェック
			if (impImage == null || impImage.getNSlices() == 0) {
				log(" (スキップ) Imageフォルダが空か、画像を読み込めません。");
				continue;
			}
			if (impMask == null || impMask.getNSlices() == 0) {
				log(" (スキップ) Maskフォルダが空か、画像を読み込めません。");
				continue;
			}

			// ステップ3 & 4: スライス枚数の比較
			int imageSlices = impImage.getNSlices();
			int maskSlices = impMask.getNSlices();
			log(" スライス数: Image=" + imageSlices + ", Mask=" + maskSlices);

			if (maskSlices > imageSlices) {
				log("!! 検証失敗: [" + caseName + "] Maskのスライス数(" + maskSlices + ")がImageのスライス数(" + imageSlices
						+ ")を上回っています。");
				impImage.close();
				impMask.close();
				return false; // 処理を中断
			}

			if (imageSlices == maskSlices) {
				log("検証OK: [" + caseName + "] Maskのスライス数(" + maskSlices + "), Imageのスライス数(" + imageSlices + ")");
				return true;
			}

			// ステップ5: Maskスライス数が少ない場合のチェック (0 < N < Image)
			if (maskSlices > 0 && maskSlices < imageSlices) {
				log(" (情報) Maskのスライス数がImageより少ないため、ファイル名またはSliceLabelからスライス位置を取得できるかをチェックします...");

				File[] maskFiles = getFilesInDir(maskCaseDir);
				int maskFileCount = maskFiles.length;

				if (maskFileCount > 1) {
					// サブケースA: 複数ファイル (連番ファイル) の場合
					// ファイル名に "_xxx" (スライス番号) があるかチェック
					if (!checkSliceLabelsForNumbers(impMask)) {
						log("!! 検証失敗: [" + caseName + "] Maskは複数ファイルですが、各スライスのSliceLabel(ファイル名)にスライス番号（数字）の記載が見つかりません。");
						impImage.close();
						impMask.close();
						return false;
					}
					log(" (OK) Maskファイル名にスライス番号パターンを確認しました。");

				} else if (maskFileCount == 1 && maskSlices >= 1) {
					// サブケースB: 単一ファイル (スタック) の場合
					// SliceLabelにスライス番号（数字）が記載されているかチェック
					if (!checkSliceLabelsForNumbers(impMask)) {
						log("!! 検証失敗: [" + caseName + "] Maskは単一スタック画像ですが、各スライスのSliceLabelにスライス番号（数字）の記載が見つかりません。");
						impImage.close();
						impMask.close();
						return false;
					}
					log(" (OK) MaskスタックのSliceLabelにスライス番号を確認しました。");

				} else if (maskFileCount > 1 && maskFileCount != maskSlices) {
					// ImageJが認識したスライス数と実ファイル数が異なる（異常ケース）
					log("!! 検証失敗: [" + caseName + "] Maskフォルダ内のファイル数(" + maskFileCount + ")とGRAPHYが認識したスライス数("
							+ maskSlices + ")が一致しません。");
					impImage.close();
					impMask.close();
					return false;
				}
			}
			log("--- [OK] " + caseName + " の検証完了 ---");
			impImage.close();
			impMask.close();
		}

		log("\n=== 検証完了 ===");
		log("すべての検証対象Caseが要件を満たしました。");
		return true;
	}

	/**
	 * 検証ログを取得します。
	 * 
	 * @return 検証プロセスの全ログ
	 */
	public String getLog() {
		return log.toString();
	}
    
	private void clearLog() {
		log.setLength(0);
	}

    // --- ヘルパーメソッド群 ---

    /**
     * 指定された親フォルダ直下にあるサブフォルダ（Case）をMapとして取得します。
     */
	private Map<String, File> getCaseFolders(File parentDir) {
		if (parentDir == null || !parentDir.isDirectory()) {
			return Collections.emptyMap();
		}
		File[] caseDirs = parentDir.listFiles(File::isDirectory);
		if (caseDirs == null) {
			return Collections.emptyMap();
		}
		return Arrays.stream(caseDirs).collect(Collectors.toMap(File::getName, file -> file));
	}

    /**
     * フォルダ内の画像（スタック/連番/DICOM）を ImagePlus として開きます。
     * ImageJの Opener がフォルダパスを解釈してよしなに開いてくれます。
     */
	private ImagePlus loadStack(File caseDir) {
		if (caseDir == null || !caseDir.isDirectory()) {
			return null;
		}
		return FolderOpener.open(caseDir.getAbsolutePath());
	}

    /**
     * フォルダ内のファイル一覧を取得します（システムファイルとサブフォルダを除く）。
     */
	private File[] getFilesInDir(File dir) {
		return dir.listFiles(
				f -> f.isFile() && !f.getName().equalsIgnoreCase("Thumbs.db") && !f.getName().startsWith("."));
	}


    /**
     * 現状、ファイルをImagePlusで読み込んだら、SliceLabelにファイル名が入るので、
     * すべて、SliceLabelから確認するようにしている。
     * 単一スタックで、スライスが１枚しかない場合は、動作を要確認。
     * 
     * ファイル名の配列をチェックし、末尾に "_数字" パターンがあるか検証します。
     */
	@SuppressWarnings("unused")
	private boolean checkFilenamesForSliceNumbers(File[] files) {
		// 正規表現: ファイル名の末尾が "_"(数字1桁以上)"."(拡張子) になっているか
		// 例: "image_001.tif", "mask_12.png"
		Pattern sliceNumberPattern = Pattern.compile("_(\\d+)\\.[^.]+$");
		for (File file : files) {
			if (!sliceNumberPattern.matcher(file.getName()).find()) {
				// 1つでもパターンに一致しないファイルがあればNG
				log(" (デバッグ) NGファイル名: " + file.getName());
				return false;
			}
		}
		return true;
	}
	
	private static final Pattern LAST_DIGITS_PATTERN = Pattern.compile("(\\d+)$");

	/**
	 * (ステップ5-B) スタック画像の SliceLabel をチェックし、数字が設定されているか検証します。
	 */
	/**
	 * Maskスタック画像のSliceLabelをチェックし、 スライス番号として解釈できる数字が含まれているか検証します。 
	 * @param impMask 検証対象の Mask ImagePlus
	 * @return すべてのスライスのラベルが有効な場合は true、そうでない場合は false
	 */
	private boolean checkSliceLabelsForNumbers(ImagePlus impMask) {
		// 1枚しかない場合も、そのラベルがスライス番号情報を含んでいるかチェックします。
		for (int i = 1; i <= impMask.getNSlices(); i++) {
			String label = impMask.getStack().getSliceLabel(i);
			if (label == null || label.trim().isEmpty()) {
				log(" (デバッグ) NG SliceLabel: スライス " + i + " のラベルが null または空です。");
				return false;
			}
			// 2. 拡張子を取り除く ("mask_plaque_001.tif" -> "mask_plaque_001")
			String baseName = label;
			int dotIndex = label.lastIndexOf('.');
			// ドットが存在し、それが文字列の先頭ではない場合に拡張子とみなす
			// (例: ".hiddenfile" は拡張子とみなさない)
			if (dotIndex > 0) {
				baseName = label.substring(0, dotIndex);
			}
			// パターンA: baseName全体が数字か？ (例: "1", "001")
			try {
				Integer.parseInt(baseName);
				// 成功。このスライスはOK。次のスライスのチェックへ移る。
				continue;
			} catch (NumberFormatException e) {
				// baseName全体が数字ではなかった (例: "mask_plaque_001", "image.1", "mask")
				// 次のパターンBのチェックに進む。
			}
			// パターンB: baseName全体が数字でない場合、末尾に数字があるか？
			// (例: "mask_plaque_001", "image.1")
			Matcher matcher = LAST_DIGITS_PATTERN.matcher(baseName);
			if (matcher.find()) {
				// 末尾に数字が見つかった (例: "001" from "mask_plaque_001")
				// このスライスはOK。次のスライスのチェックへ移る。
				continue;
			}
			// 4. パターンAにもBにも一致しなかった (例: "mask.tif" -> "mask")
			log(" (デバッグ) NG SliceLabel: スライス " + i + " のラベル '" + label + "' (ベース名: '" + baseName
					+ "') からスライス番号を特定できません。");
			return false;
		}
		// ループを完走した場合、すべてのスライスが有効
		return true;
	}

    /**
     * (ステップ6) フォルダ内に画像以外のファイル（DICOMと既知の画像拡張子、拡張子なし以外）があるか。
     */
	private List<File> getNonImageFiles(File dir) {
		Set<String> knownImageExt = new HashSet<>(
				Arrays.asList("tif", "tiff", "png", "jpg", "jpeg", "bmp", "gif", "dcm"));

		File[] files = getFilesInDir(dir); // システムファイルは除外済み
		List<File> nonImageFiles = new ArrayList<>();

		for (File file : files) {
			String name = file.getName().toLowerCase();
			int dotIndex = name.lastIndexOf('.');

			// 拡張子がない場合 (DICOMの可能性があるため許可)
			if (dotIndex == -1) {
				continue;
			}

			String ext = name.substring(dotIndex + 1);
			if (!knownImageExt.contains(ext)) {
				nonImageFiles.add(file);
			}
		}
		return nonImageFiles;
	}
	
	/**
     * バッチ処理を実行するための SwingWorker インナークラス
     * <Void, String>
     * - Void: doInBackground の戻り値 (今回は使わない)
     * - String: process メソッドに渡す型 (ログメッセージ)
     */
    private class BatchWorker extends SwingWorker<Void, String> {

        // 処理に必要なパラメータ
        private final String imageParentDir;
        private final String maskParentDir;
        private final String saveToDir;

        // コンストラクタでパラメータを受け取る
        public BatchWorker(String imageParentDir, String maskParentDir, String saveToDir) {
            this.imageParentDir = imageParentDir;
            this.maskParentDir = maskParentDir;
            this.saveToDir = saveToDir;
        }

        /**
         * ★ バックグラウンドスレッド (Worker Thread) で実行される重い処理
         */
        @Override
        protected Void doInBackground() throws Exception {
			if (saveToDir == null || saveToDir.length() == 0) {
				throw new Exception("Please select save folder. This process interupted.");
			}

			File ipd = new File(imageParentDir);
			File mpd = new File(maskParentDir);
			File saveTo = new File(saveToDir);
			if (!saveTo.exists()) {
				saveTo.mkdirs();
			}
			String parentName = ipd.getName();
            
			// --- 検証 ---
			// (validateDataset が内部で log() を呼ぶ場合、
			// そのログを publish するために getLog() を使う)
			clearLog(); // 親クラスのログバッファをクリア
			if (!validateDataset(imageParentDir, maskParentDir)) {
				publish(getLog()); // 検証ログをGUIに送信
				throw new Exception("データセットの検証に失敗しました。");
			}
			publish(getLog()); // 検証ログをGUIに送信
			clearLog(); // ログバッファをクリアして次のステップへ

			// --- ステップ1: Caseフォルダのリストアップとペアリング ---
			Map<String, File> imageCaseMap = getCaseFolders(ipd);
			Map<String, File> maskCaseMap = getCaseFolders(mpd);
			List<String> pairedCaseNames = new ArrayList<>();
			for (String imageCaseName : imageCaseMap.keySet()) {
				if (maskCaseMap.containsKey(imageCaseName)) {
					pairedCaseNames.add(imageCaseName);
				}
			}
            
			if (pairedCaseNames.isEmpty()) {
				throw new Exception("処理対象のペア Case が見つかりません。");
			}
			
			RadiomicsPipeline radpipe = new RadiomicsPipeline(radSettings);
			Properties settingProp = radSettings.currentSettings();
			List<String> featureNames = radSettings.getTargetFeatureNames();

			int totalCases = pairedCaseNames.size();
			
			///////////TO SAVE FILE NAME///////////
			//////////////////////////////////////////////////////////////////////////////////
			String option = "_";
			String val = settingProp.getProperty(SettingsContext.MASK_LABEL);
			if(val == null || val.equals("") || val.toLowerCase().equals("null") ) {
				//keep default
				throw new IllegalArgumentException("RadiomicsPipeline:preprocessing() require MASK_LABEL...");
			}
			int targetLabel = -1;
			try {
				targetLabel = Integer.valueOf(val);
			}catch(NumberFormatException e) {
				throw e;
			}
			
			boolean d3_basis = ((String)settingProp.get(SettingsContext.D3Basis)).equals("true");
			boolean doResample = ((String)settingProp.get(SettingsContext.Resampling)).equals("true");
			String x = settingProp.getProperty(SettingsContext.ResamplingX);
			String y = settingProp.getProperty(SettingsContext.ResamplingY);
			String z = settingProp.getProperty(SettingsContext.ResamplingZ);
			
			boolean doRangeFiltering = ((String)settingProp.get(SettingsContext.RangeFiltering)).equals("true");
			String rfMin = settingProp.getProperty(SettingsContext.RangeFilteringMin);
			String rfMax = settingProp.getProperty(SettingsContext.RangeFilteringMax);
			
			boolean doRemoveOutliers = ((String)settingProp.get(SettingsContext.RemoveOutliers)).equals("true");
			String roSigma = settingProp.getProperty(SettingsContext.RemoveOutliersSigma);
			
			boolean doNormalize = false;// todo
			
			option += "LBL"+targetLabel+"_";
			option += d3_basis ? "3D_":"2D_";
			option += doResample ? "ResampledX"+x+"Y"+y+"Z"+z+"_":"";
			option += doRangeFiltering ? "RangeFilterMin"+rfMin+"Max"+rfMax+"_":"";
			option += doRemoveOutliers ? "RemoveOutlierSigma"+roSigma+"_":"";
			option += doNormalize ? "Normalized":"";
			//add more options...
			
			//finally, remove last '_'
			if(option.endsWith("_")) {
				option = option.substring(0, option.length()-1);
			}
			//////////////////////////////////////////////////////////////////////////////////
			
			// --- メインループ (症例ごとの処理) ---
			for (int i = 0; i < totalCases; i++) {
				String caseName = pairedCaseNames.get(i);

				// ★ ログをGUIに送信
				publish("\n--- Start extraction: [" + caseName + "] (" + (i + 1) + "/" + totalCases + ") ---");

                // (元の処理ロジック)
				File imageCaseDir = imageCaseMap.get(caseName);
				File maskCaseDir = maskCaseMap.get(caseName);
				ImagePlus impImage = loadStack(imageCaseDir);
				ImagePlus impMask = loadStack(maskCaseDir);

				if (impImage == null || impMask == null) {
					publish(" (警告) [" + caseName + "] Image または Mask が読み込めません。スキップします。");
					continue; // 次の症例へ
				}

				int imageSlices = impImage.getNSlices();
				int maskSlices = impMask.getNSlices();

				if (maskSlices < imageSlices) {
					publish("This case Padding required.");
					int[] indices = new int[maskSlices];
					for(int p = 1; p<=maskSlices; p++) {
						String sliceLabel = impMask.getStack().getSliceLabel(p);
						Optional<Integer> result = extractTrailingInteger(sliceLabel);
						if (result.isPresent()) {
							indices[p-1] = result.get();
			            }
					}
					impMask = io.github.tatsunidas.radiomics.main.Utils.padMaskStack(impMask, impImage, indices);
					publish("--- [OK] " + caseName + " Padding completed ---");
				}
				ResultsTable rt = radpipe.calcAllFeatures(settingProp, featureNames, impImage, impMask);
				if(rt == null) {
					throw new Exception("Feature extraction was failed !!");
				}
				rt.saveColumnHeaders(true);
				rt.save(saveToDir+File.separator+parentName+"_"+caseName+"_"+option+".csv");
				publish("--- " + caseName + " finished ---");
				impImage.close();
				impMask.close();
				// ★ JProgressBar の値を更新 (0-100)
				int progress = (int) Math.round(((i + 1.0) / totalCases) * 100);
				setProgress(progress);
			}
			publish("\n--- Finish all cases with no error ---");
			return null; // doInBackground の戻り値 (Void)
		}

        /**
         * ★ GUIスレッド (EDT) で実行される
         * doInBackground 内で publish() が呼ばれるたびに実行されます。
         */
        @Override
		protected void process(List<String> chunks) {
			// chunks には publish() されたログメッセージのリストが入っている
			for (String message : chunks) {
				usageTextArea.append(message + "\n");
			}
			// ★ JTextArea を自動で一番下までスクロール
			usageTextArea.setCaretPosition(usageTextArea.getDocument().getLength());
		}

		/**
		 * ★ GUIスレッド (EDT) で実行される doInBackground が完了した (または例外で終了した) ときに、最後に一度だけ呼び出されます。
		 */
		@Override
		protected void done() {
			// 1. ボタンを再度有効化
			runButton.setEnabled(true);
			try {
				// 2. get() を呼んで、doInBackground で例外が発生したか確認
				get();
				// 3. 成功した場合
				progressBar.setValue(100); // 100% に
				JOptionPane.showMessageDialog(comp, "バッチ処理が正常に完了しました。", "Batch process was done.", JOptionPane.INFORMATION_MESSAGE);
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Thread.sleep(1000);
							progressBar.setVisible(false);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}).start();
			} catch (Exception e) {
				// 4. 失敗した場合 (doInBackground でスローされた例外)
				progressBar.setVisible(false); // エラー時はプログレスバーを隠す
				String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();

				usageTextArea.append("\n--- エラー発生 --- \n" + errorMessage);
				usageTextArea.setCaretPosition(usageTextArea.getDocument().getLength());

				JOptionPane.showMessageDialog(comp, "処理中にエラーが発生しました:\n" + errorMessage, "エラー",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	} // --- End of BatchWorker inner class ---

}
