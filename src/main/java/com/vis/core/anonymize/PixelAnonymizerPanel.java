/**
 * Copyright visionary imaging services, inc.
 * @author tatsunidas
 */
package com.vis.core.anonymize;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.dcm4che3.data.UID;

import com.vis.configuration.Resources;
import com.vis.configuration.RoiDBKey;
import com.vis.core.log.Log;
import com.vis.core.ui.listener.RoiObjListener;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.util.DeleteFolder;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import com.vis.dicom.image.DicomImage;
import com.vis.imageio.PixelDataDecoder;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@SuppressWarnings("serial")
public class PixelAnonymizerPanel extends JPanel {

	private StudyCheckBoxTree studyTree;

	private ButtonGroup toolButtonGroup; // ツールボタンの排他制御用

	private JPanel seriesDisplayPanel; // Praparatを配置するパネル
	private JPanel maskRoiListPanel; // ROIパネルを並べるリスト
	private AttributeAnonymizerPanel attrAnonPanel;

	private JButton btnApplyAll;
	private JToggleButton btnTogglePreview;

	private JProgressBar progressBar;
	private JButton btnExecute;

	private Map<String, Praparat> praparatMap = new HashMap<>();
	private Praparat currentActivePraparat; // 現在画面に表示されているPraparat

	// 作成ROI追跡用のリスト
	private final List<RoiObj> tempRois = new ArrayList<>();

	// ★ これを追加：プレビュー時に一時的に生成される複製ROIを管理するリスト
	private final List<RoiObj> previewClonedRois = new ArrayList<>();

	private final Map<RoiObj, Integer> roiModeMap = new HashMap<>();
	private final Map<RoiObj, String> roiCustomTextMap = new HashMap<>();

	private boolean executing = false;

	SwingWorker<Void, String> currentWorker;

	public PixelAnonymizerPanel() {
		initUI();
	}

	private void initUI() {
		setLayout(new BorderLayout(5, 5));

		// ==========================================
		// 上部 (Center): ツリー、画像ビューワ、ROIリスト
		// ==========================================
		JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		mainSplit.setResizeWeight(0.2); // 左ペインの比率
		mainSplit.setContinuousLayout(true); // バーをドラッグ中も中身をリアルタイムに再描画する
		mainSplit.setDividerSize(8); // バーの幅を少し太くして掴みやすくする（デフォルトは細すぎる場合があります）
		mainSplit.setOneTouchExpandable(true); // バーにワンタッチで折りたたむ矢印ボタンを付ける（便利です）

		// 右: ビューワとROIリストの分割
		JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		rightSplit.setResizeWeight(0.7); // ビューワの比率を大きく

		// 中央: シリーズ表示パネル (Praparat)
		seriesDisplayPanel = new JPanel(new BorderLayout());
		seriesDisplayPanel.setBorder(new TitledBorder("Series Display"));
		seriesDisplayPanel.setMinimumSize(new Dimension(300, 200));

		JToolBar roiToolBar = new JToolBar();
		roiToolBar.setFloatable(false); // ツールバーを固定
		toolButtonGroup = new ButtonGroup();
		roiToolBar.add(createToolButton("Pointer", Viewer2DToolBar.Windowing, true)); // デフォルト
		roiToolBar.addSeparator();
		roiToolBar.add(createToolButton("Rectangle", RoiType.RECTANGLE.id(), false));
		roiToolBar.add(createToolButton("Polygon", RoiType.POLYGON.id(), false));
		roiToolBar.add(createToolButton("Oval", RoiType.OVAL.id(), false));
		seriesDisplayPanel.add(roiToolBar, BorderLayout.NORTH);
		rightSplit.setLeftComponent(seriesDisplayPanel);

		// 左: スタディツリー
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
		studyTree = new StudyCheckBoxTree(root, this);
		JScrollPane treeScroll = new JScrollPane(studyTree);
		treeScroll.setBorder(new TitledBorder("Study / Series (Uncheck to Exclude)"));
		treeScroll.setMinimumSize(new Dimension(200, 0));
		mainSplit.setLeftComponent(treeScroll);

		// 右端: マスクROI管理リストパネル
		JPanel roiPanel = createMaskRoiListPanel();
		roiPanel.setMinimumSize(new Dimension(250, 0));
		rightSplit.setRightComponent(roiPanel);

		mainSplit.setRightComponent(rightSplit);

		// ==========================================
		// 下部 (South): 属性匿名化パネル ＆ 実行パネル
		// ==========================================
		JPanel bottomContainer = new JPanel(new BorderLayout());

		// 属性匿名化パネル（PIXEL_MODEで初期化し、入力フォルダ指定を隠す）
		attrAnonPanel = new AttributeAnonymizerPanel(AttributeAnonymizerPanel.Mode.PIXEL_MODE);
		attrAnonPanel.setPreferredSize(new Dimension(800, 300));
		bottomContainer.add(attrAnonPanel, BorderLayout.CENTER);

		// 実行パネル (プログレスバー + Executeボタン)
		JPanel execPanel = new JPanel(new BorderLayout(10, 0));
		execPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setString("Ready");

		btnExecute = new JButton("Execute Pixel & Attribute Anonymization");
		btnExecute.setPreferredSize(new Dimension(250, 40));
		btnExecute.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		btnExecute.addActionListener(this::onExecuteClicked);

		execPanel.add(progressBar, BorderLayout.CENTER);
		execPanel.add(btnExecute, BorderLayout.EAST);

		bottomContainer.add(execPanel, BorderLayout.SOUTH);

		JSplitPane rootVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		rootVerticalSplit.setTopComponent(mainSplit); // 上に画像エリア
		rootVerticalSplit.setBottomComponent(bottomContainer); // 下に属性設定エリア
		rootVerticalSplit.setResizeWeight(0.6); // 初期状態で上のエリアを60%にする
		rootVerticalSplit.setContinuousLayout(true);
		rootVerticalSplit.setDividerSize(10);
		rootVerticalSplit.setOneTouchExpandable(true); // 折りたたみボタンを有効化

		add(rootVerticalSplit, BorderLayout.CENTER);
	}

	private JPanel createMaskRoiListPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new TitledBorder("Mask ROIs"));

		// North: オプションボタン群
		JPanel optionsPanel = new JPanel(new GridLayout(3, 1, 2, 2));

		btnApplyAll = new JButton("Apply to All Series");
		setActionApplyToAllSeries(btnApplyAll);

		btnTogglePreview = new JToggleButton("Preview Mask as Blackout");
		setActionPreviewMask(btnTogglePreview);

		JButton btnClearAll = new JButton("Clear All ROIs");
		setActionClearAll(btnClearAll);

		optionsPanel.add(btnApplyAll);
		optionsPanel.add(btnTogglePreview);
		optionsPanel.add(btnClearAll);
		panel.add(optionsPanel, BorderLayout.NORTH);

		// Center: ROIパネルを追加していくコンテナ
		maskRoiListPanel = new JPanel();
		maskRoiListPanel.setLayout(new BoxLayout(maskRoiListPanel, BoxLayout.Y_AXIS));

		JScrollPane scrollPane = new JScrollPane(maskRoiListPanel);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		panel.add(scrollPane, BorderLayout.CENTER);

		return panel;
	}

	private void updateMaskRoiListForCurrentSeries() {
		if (maskRoiListPanel == null || currentActivePraparat == null)
			return;

		Object[] uids = currentActivePraparat.getUIDs();
		if (uids == null)
			return;
		String currentSeriesUID = (String) uids[2];
		if (currentSeriesUID == null)
			return;

		maskRoiListPanel.removeAll();

		for (RoiObj roi : tempRois) {
			String roiSeriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());
			if (roiSeriesUID == null || roiSeriesUID.equals(currentSeriesUID)) {

				// ★ 変更：Mapに記憶されている設定を読み出す（なければデフォルトの1と空白）
				int savedMode = roiModeMap.getOrDefault(roi, 1);
				String savedText = roiCustomTextMap.getOrDefault(roi, "");

				// ★ 変更：読み出した設定値を渡してパネルを復元する
				addMaskRoiPanel(roi, currentActivePraparat, savedMode, savedText);
			}
		}

		maskRoiListPanel.revalidate();
		maskRoiListPanel.repaint();
	}

	// =========================================================================================
	// 実行パイプライン (Pixel Masking -> Attribute Anonymization -> Output)
	// =========================================================================================
	private void onExecuteClicked(ActionEvent e) {
		String destPath = attrAnonPanel.getDestDirectory();
		if (destPath == null || destPath.isEmpty()) {
			JOptionPane.showMessageDialog(this, Resources.i18n("PixelAnonymizerPanel.error.noOutputDir"),
					Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
			return;
		}

		List<HashMap<String, String>> targets = getTargetSeriesList();
		if (targets.isEmpty()) {
			JOptionPane.showMessageDialog(this, Resources.i18n("PixelAnonymizerPanel.error.noSeries"),
					Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (btnTogglePreview != null && btnTogglePreview.isSelected()) {
			btnTogglePreview.doClick();
		}

		AnonymizeConfig config = attrAnonPanel.getCurrentConfig();
		File destDir = new File(destPath);

		this.executing = true;
		btnExecute.setEnabled(false);
		progressBar.setValue(0);
		progressBar.setString("Processing...");

		currentWorker = new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() throws Exception {
				publish("Start Anonymization Pipeline...");

				File tempDir = new File(System.getProperty("java.io.tmpdir"),
						"GraphyAnonTemp_" + System.currentTimeMillis());
				if (!tempDir.exists())
					tempDir.mkdirs();
				publish("Created temporary directory: " + tempDir.getAbsolutePath());

				publish("Step 1: Applying pixel masks to target series...");
				int totalSeries = targets.size();
				int currentSeries = 0;

				for (HashMap<String, String> series : targets) {
					if (isCancelled())
						break;
					currentSeries++;
					String seUid = series.get(RoiDBKey.SeriesInstanceUID.name());
					publish(String.format("Masking series (%d/%d): %s", currentSeries, totalSeries, seUid));

					// ★ 1シリーズの読み込み・処理失敗がパイプライン全体を中断させないようにする
					try {
						processPixelMaskingToTemp(seUid, tempDir, this);
					} catch (Exception ex) {
						Log.logger.severe("Failed to mask series: " + seUid + " (" + ex.getMessage() + ")");
						publish("Skipped (failed to mask): " + seUid);
					}
				}

				if (isCancelled()) {
					publish("Process cancelled.");
					return null;
				}

				publish("Step 2: Anonymizing DICOM attributes and exporting to destination...");
				DicomAnonymizerEngine engine = new DicomAnonymizerEngine();
				engine.setProgressListener((current, total, message) -> {
					// ★ totalが0の場合の0除算（setProgressの範囲外例外）を防ぐ
					int percent = total > 0 ? (int) (((double) current / total) * 100) : 0;
					setProgress(percent);
					// ★ パーセンテージも文字に含めて publish する
					publish(String.format("Attribute Anonymizing: %d%% - %s", percent, message));
				});

				engine.transcodeDirectory(tempDir, destDir, config);

				publish("Cleaning up temporary files...");
				DeleteFolder.deleteDirectoryRecursively(tempDir);

				return null;
			}

			@Override
			protected void process(List<String> chunks) {
				for (String msg : chunks) {
					Log.logger.info(msg);
					// ★ プログレスバーのテキストも更新する
					if (progressBar != null) {
						progressBar.setString(msg);
					}
				}
			}

			@Override
			protected void done() {
				executing = false;
				btnExecute.setEnabled(true);
				currentWorker = null;
				try {
					if (isCancelled()) {
						progressBar.setString("Canceled");
						JOptionPane.showMessageDialog(PixelAnonymizerPanel.this,
								Resources.i18n("PixelAnonymizerPanel.canceled"),
								Resources.i18n("dialog.title.canceled"), JOptionPane.WARNING_MESSAGE);
					} else {
						get();
						progressBar.setValue(100);
						progressBar.setString("Completed");
						JOptionPane.showMessageDialog(PixelAnonymizerPanel.this,
								Resources.i18n("PixelAnonymizerPanel.done"),
								Resources.i18n("dialog.title.complete"), JOptionPane.INFORMATION_MESSAGE);
					}
				} catch (Exception ex) {
					String detail = ex.getMessage() != null ? ex.getMessage() : ex.toString();
					progressBar.setString("Error");
					Log.logger.severe("Pixel anonymization error: " + detail);
					JOptionPane.showMessageDialog(PixelAnonymizerPanel.this,
							Resources.i18n("PixelAnonymizerPanel.error.occurred") + " " + detail,
							Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
					ex.printStackTrace();
				}finally {
					progressBar.setValue(0);
                    progressBar.setString("Ready"); // または空文字 "" にする
				}
			}
		};

		currentWorker.addPropertyChangeListener(evt -> {
			if ("progress".equals(evt.getPropertyName())) {
				progressBar.setValue((Integer) evt.getNewValue());
			}
		});

		currentWorker.execute();
	}

	private void processPixelMaskingToTemp(String seriesUid, File tempDir, SwingWorker<?, ?> worker) throws Exception {
		Praparat prap = praparatMap.get(seriesUid);
		if (prap == null)
			return;

		Map<Integer, List<RoiObj>> masksPerSlice = new HashMap<>();
		for (RoiObj roi : tempRois) {
			if (!seriesUid.equals(roi.getProperty(RoiDBKey.SeriesInstanceUID.name())))
				continue;
			int[] targets = calculateTargetZctIndices(prap, roi);
			for (int idx : targets) {
				masksPerSlice.computeIfAbsent(idx, k -> new ArrayList<>()).add(roi);
			}
		}

		if (prap.isMultiFrame()) {
			String filePath = prap.getImageFileLocations().get(0);
			com.vis.dicom.DicomReader reader = com.vis.dicom.DicomReader.newDicomReader(null);
			reader.read(filePath, true);
			com.vis.dicom.DicomObject header = reader.getHeader();
			com.vis.dicom.DicomObject fmi = reader.getFileMetaInfomation();
			com.vis.dicom.UID tsuid = reader.checkTSUID();

			// ★ 破損ファイル等でheader/tsuidがnullのまま返るケースを明示的に処理する
			if (header == null || fmi == null || tsuid == null) {
				throw new java.io.IOException("Failed to read DICOM file: " + filePath);
			}

			boolean isMpeg = tsuid.uid().startsWith("1.2.840.10008.1.2.4.10");

			if (isMpeg) {
				// MPEG 動画専用抽出・FFmpegマスキング・再カプセル化パイプライン
				processMpegMaskingToTemp(header, fmi, tsuid.uid(), tempDir, masksPerSlice, worker);

			} else {
				// 通常のマルチフレーム（スライス積層型等）: DicomWriter を使ったストリーム書き出し
				int numFrames = header.getInt(com.vis.dicom.Tag.NumberOfFrames, 1);
				com.vis.dicom.image.DicomImage dcm = new com.vis.dicom.dcm4cheImpl.DicomImageChe(filePath, header, fmi,
						tsuid);

				File tempFile = new File(tempDir, header.getString(com.vis.dicom.Tag.SOPInstanceUID) + ".dcm");
				com.vis.dicom.DicomWriter writer = com.vis.dicom.DicomWriter.newDicomWriter();
				writer.openStream(header, fmi, tempFile.getAbsolutePath(), tsuid.uid(), numFrames,
						dcm.getBitsAllocated(), dcm.getSamples(), dcm.getWidth(), dcm.getHeight());

				for (int i = 0; i < numFrames; i++) {
					if (worker.isCancelled()) {
						writer.closeStream();
						return;
					}

					// ★ UI更新: マルチフレームの処理進捗をプログレスバーに反映
					final int currentFrame = i + 1;
					final int totalFrames = numFrames;
					SwingUtilities.invokeLater(() -> {
						if (progressBar != null) {
							int percent = (int) (((double) currentFrame / totalFrames) * 100);
							progressBar.setIndeterminate(false);
							progressBar.setValue(percent);
							progressBar.setString(String.format("Masking Frames: %d / %d (%d%%)", currentFrame,
									totalFrames, percent));
						}
					});

					List<RoiObj> rois = masksPerSlice.get(i);
					byte[] frameBytes;

					if (rois != null && !rois.isEmpty()) {
						ij.process.ImageProcessor ip = dcm.getImageProcessor(i);
						applyMaskToImageProcessor(ip, rois);
						frameBytes = encodeImageProcessorToBytes(ip, dcm);
					} else {
						if (com.vis.imageio.Codec.isCompressed(dcm.getTSUID())) {
							ij.process.ImageProcessor ip = dcm.getImageProcessor(i);
							frameBytes = encodeImageProcessorToBytes(ip, dcm);
						} else {
							frameBytes = dcm.getPixelData(i);
						}
					}
					writer.writeFrame(frameBytes);
				}
				writer.closeStream();
			}

		} else {
			// 単一スライスのファイル群の処理
			Map<String, Integer> sopToZctMap = new HashMap<>();
			for (Map.Entry<Integer, SlideGlass> entry : prap.getAllSlides().entrySet()) {
				sopToZctMap.put(entry.getValue().getSOPInstanceUID(), entry.getKey());
			}

			List<String> filePaths = prap.getImageFileLocations();
			final int totalFiles = filePaths.size();
			for (int i = 0; i < filePaths.size(); i++) {
				if (worker.isCancelled())
					return;

				final int currentFile = i + 1;
				SwingUtilities.invokeLater(() -> {
					if (progressBar != null) {
						int percent = (int) (((double) currentFile / totalFiles) * 100);
						progressBar.setIndeterminate(false);
						progressBar.setValue(percent);
						progressBar.setString(
								String.format("Masking Files: %d / %d (%d%%)", currentFile, totalFiles, percent));
					}
				});

				String filePath = filePaths.get(i);
				com.vis.dicom.DicomReader reader = com.vis.dicom.DicomReader.newDicomReader(null);
				reader.read(filePath, true);
				DicomObject dcm = reader.getHeader();
				com.vis.dicom.DicomObject fmi = reader.getFileMetaInfomation();

				// ★ 破損ファイル等でheaderがnullのまま返るケースを明示的に処理し、このファイルだけをスキップする
				if (dcm == null || fmi == null) {
					Log.logger.warning("Failed to read DICOM file, skipping: " + filePath);
					continue;
				}

				String sop = dcm.getString(com.vis.dicom.Tag.SOPInstanceUID);
				Integer zctIdx = sopToZctMap.get(sop);
				com.vis.dicom.UID tsuid = reader.checkTSUID();

				if (zctIdx == null)
					continue;
				List<RoiObj> rois = masksPerSlice.get(zctIdx);

				if (rois != null && !rois.isEmpty()) {
					reader.read(filePath, true);
					com.vis.dicom.image.DicomImage dcmImg = DicomImage.newDicomImage(filePath, dcm, fmi, tsuid);
					ij.process.ImageProcessor ip = dcmImg.getImageProcessor(0);

					applyMaskToImageProcessor(ip, rois);
					byte[] newPixels = encodeImageProcessorToBytes(ip, dcmImg);

					if (com.vis.imageio.Codec.isCompressed(tsuid)) {
						tsuid = com.vis.dicom.UID.ExplicitVRLittleEndian;
						dcm.setString(com.vis.dicom.Tag.TransferSyntaxUID, com.vis.dicom.VR.UI, tsuid.uid());
						fmi.setString(com.vis.dicom.Tag.TransferSyntaxUID, com.vis.dicom.VR.UI, tsuid.uid());
					}

					dcmImg.setPixelData(0, ip.getWidth(), ip.getHeight(), dcmImg.getSamples(),
							dcmImg.getBitsAllocated(), newPixels);
					com.vis.dicom.DicomWriter writer = com.vis.dicom.DicomWriter.newDicomWriter();
					writer.writeDicomImage(dcmImg.getHeader(), fmi, new File(tempDir, sop + ".dcm").getAbsolutePath(),
							false);
				} else {
					java.nio.file.Files.copy(new java.io.File(filePath).toPath(),
							new java.io.File(tempDir, sop + ".dcm").toPath(),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

// // =========================================================================
//    // MPEG 用のヘルパーメソッド群 (FFmpeg利用)
//    // =========================================================================
//
//    private void processMpegMaskingToTemp(com.vis.dicom.DicomObject header, com.vis.dicom.DicomObject fmi, 
//                                          String tsUid, File tempDir, Map<Integer, List<RoiObj>> masksPerSlice, 
//                                          SwingWorker<?,?> worker) throws Exception {
//
//        // 1. DicomObject に新設したメソッドで MPEGデータを直接抽出！ (dcm4che非依存)
//        byte[] mpegBytes = header.getEncapsulatedPixelData();
//        if (mpegBytes == null) {
//            Log.logger.warning("Could not extract MPEG bytes from DICOM.");
//            return;
//        }
//
//        File tempIn = new File(tempDir, "temp_in_" + System.currentTimeMillis() + ".mp4");
//        File tempOut = new File(tempDir, "temp_out_" + System.currentTimeMillis() + ".mp4");
//        java.nio.file.Files.write(tempIn.toPath(), mpegBytes);
//
//        String filter = buildFFmpegFilter(masksPerSlice);
//        byte[] outBytes = mpegBytes;
//        
//        // 2. FFmpegによるマスキングと高画質再エンコード
//        if (!filter.isEmpty()) {
//            try {
//                ProcessBuilder pb = new ProcessBuilder(
//                        "ffmpeg", "-y", "-i", tempIn.getAbsolutePath(),
//                        "-vf", filter,
//                        "-c:v", "libx264", 
//                        "-preset", "medium", 
//                        "-crf", "18", // ★ 高画質を維持するオプション
//                        "-c:a", "copy",
//                        tempOut.getAbsolutePath());
//                Process p = pb.start();
//                int exitCode = p.waitFor();
//                
//                if (exitCode == 0 && tempOut.exists()) {
//                    outBytes = java.nio.file.Files.readAllBytes(tempOut.toPath());
//                } else {
//                    Log.logger.warning("FFmpeg processing failed with exit code: " + exitCode);
//                }
//            } catch (Exception e) {
//                Log.logger.severe("FFmpeg is not installed or not found in PATH. Skipping video masking.");
//                e.printStackTrace();
//            }
//        }
//
//        // 3. 処理後の動画バイト配列を、DicomObject にそのままセット！ (パディングも内部で自動処理)
//        header.setEncapsulatedPixelData(outBytes);
//
//        // 4. あとは通常の DicomWriter で書き出すだけ（Fragmentsの情報は保持されているためそのままカプセル化出力される）
//        File tempFile = new File(tempDir, header.getString(com.vis.dicom.Tag.SOPInstanceUID) + ".dcm");
//        com.vis.dicom.DicomWriter writer = com.vis.dicom.DicomWriter.newDicomWriter();
//        writer.writeDicomImage(header, fmi, tempFile.getAbsolutePath(), false);
//        
//        // 一時ファイルの削除
//        tempIn.delete();
//        tempOut.delete();
//    }

	// =========================================================================
	// MPEG 用のヘルパーメソッド群 (FFmpeg利用)
	// =========================================================================

	private void processMpegMaskingToTemp(com.vis.dicom.DicomObject header, com.vis.dicom.DicomObject fmi, String tsUid,
			File tempDir, Map<Integer, List<RoiObj>> masksPerSlice, SwingWorker<?, ?> worker) throws Exception {

		// 1. DicomObject に新設したメソッドで MPEGデータを直接抽出
		byte[] mpegBytes = header.getEncapsulatedPixelData();
		if (mpegBytes == null) {
			Log.logger.warning("Could not extract MPEG bytes from DICOM.");
			return;
		}

		File tempIn = new File(tempDir, "temp_in_" + System.currentTimeMillis() + ".mp4");
		File tempOut = new File(tempDir, "temp_out_" + System.currentTimeMillis() + ".mp4");
		java.nio.file.Files.write(tempIn.toPath(), mpegBytes);

		String filter = buildFFmpegFilter(masksPerSlice);
		
		// ==========================================================
		// ★ 追加検証1: Javaが生成したフィルター構文を強制的にログ出力する
		// ==========================================================
		Log.logger.info("==========================================================");
		Log.logger.info(" [VERIFY] Generated FFmpeg Filter: ");
		Log.logger.info(" " + filter);
		Log.logger.info("==========================================================");
		
		byte[] outBytes = mpegBytes;

		// 2. FFmpegによるマスキングと高画質再エンコード
		if (!filter.isEmpty()) {
			try {
				// ★ 変更: OSインストールの "ffmpeg" コマンドではなく、JAVE2内蔵のFFmpeg実行ファイルのパスを自動取得する
				ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator locator = new ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator();
				String javeFfmpegPath = locator.getExecutablePath();

				Log.logger.info("Using JAVE2 bundled FFMPEG: " + javeFfmpegPath);

				ProcessBuilder pb = new ProcessBuilder(javeFfmpegPath, "-y", "-i", tempIn.getAbsolutePath(), "-vf",
				        filter, "-c:v", "libx264", "-preset", "medium", "-crf", "18",
				        "-bf", "0",                  // Bフレームを無効化し、表示順とデコード順を完全に一致させる
				        "-movflags", "+faststart",   // MP4の再生タイミング情報(moovアトム)をファイルの先頭に配置する
				        "-c:a", "copy", tempOut.getAbsolutePath());
				Process p = pb.start();

				// ★ UIを「不確定(Indeterminate)モード」にして、動画処理中であることをユーザーに知らせる
				SwingUtilities.invokeLater(() -> {
					if (progressBar != null) {
						progressBar.setIndeterminate(true);
						progressBar.setString("Encoding MPEG video (FFmpeg is running)... please wait");
					}
				});

				// ★ FFmpegの標準出力を裏で読み捨てて、出力バッファ詰まりによるフリーズ（デッドロック）を完全に防ぐ
				try (java.io.BufferedReader reader = new java.io.BufferedReader(
						new java.io.InputStreamReader(p.getErrorStream()))) {
					String line = "";
					while ((line = reader.readLine()) != null) {
						if (worker.isCancelled()) {
							p.destroy();
							break;
						}
						// ==========================================================
						// ★ 変更検証2: fine だとコンソール設定によっては隠れてしまうため、
						// テスト時のみ INFO レベルで出力し、接頭辞をつけて見やすくする
						// ==========================================================
						Log.logger.info("[FFmpeg-LOG] " + line);
					}
				}

				int exitCode = p.waitFor();

				// ★ 処理完了後にIndeterminateモードを解除
				SwingUtilities.invokeLater(() -> {
					if (progressBar != null) {
						progressBar.setIndeterminate(false);
						progressBar.setString("MPEG video encoding completed.");
					}
				});

				if (exitCode == 0 && tempOut.exists()) {
					outBytes = java.nio.file.Files.readAllBytes(tempOut.toPath());
				} else {
					Log.logger.warning("FFmpeg processing failed with exit code: " + exitCode);
				}
			} catch (Exception e) {
				Log.logger.severe("JAVE2 FFmpeg execution failed. Skipping video masking.");
				e.printStackTrace();
			}
		}

		// 3. 処理後の動画バイト配列を、DicomObject にそのままセット (パディングも内部で自動処理)
		header.setEncapsulatedPixelData(outBytes);

		// 4. あとは通常の DicomWriter で書き出すだけ
		File tempFile = new File(tempDir, header.getString(com.vis.dicom.Tag.SOPInstanceUID) + ".dcm");
		com.vis.dicom.DicomWriter writer = com.vis.dicom.DicomWriter.newDicomWriter();
		writer.write(header, UID.MPEG4HP41, tempFile.getAbsolutePath());

		// 一時ファイルの削除
		tempIn.delete();
		tempOut.delete();
	}

	private String buildFFmpegFilter(Map<Integer, List<RoiObj>> masksPerSlice) {
		StringBuilder sb = new StringBuilder();
		com.vis.core.view.D2.roi.RoiConverter converter = new com.vis.core.view.D2.roi.RoiConverter();

		Map<RoiObj, java.util.List<Integer>> framesPerRoi = new HashMap<>();
		for (Map.Entry<Integer, List<RoiObj>> entry : masksPerSlice.entrySet()) {
			int frame = entry.getKey();
			for (RoiObj roi : entry.getValue()) {
				framesPerRoi.computeIfAbsent(roi, k -> new ArrayList<>()).add(frame);
			}
		}

		for (Map.Entry<RoiObj, java.util.List<Integer>> entry : framesPerRoi.entrySet()) {
			RoiObj roi = entry.getKey();
			java.util.List<Integer> frames = entry.getValue();
			java.util.Collections.sort(frames);

			ij.gui.Roi ijRoi = converter.convert2Roi(roi);
			if (ijRoi != null) {
				Rectangle bounds = ijRoi.getBounds();

				List<String> enableConditions = new ArrayList<>();
				int start = frames.get(0);
				int prev = start;

				for (int i = 1; i < frames.size(); i++) {
					int curr = frames.get(i);
					if (curr != prev + 1) {
						if (start == prev)
							// ProcessBuilderではシングルクォーテーションで囲む場合、カンマのエスケープ(\\)は不要
							enableConditions.add(String.format("eq(n,%d)", start));
						else
							enableConditions.add(String.format("between(n,%d,%d)", start, prev));
						start = curr;
					}
					prev = curr;
				}
				if (start == prev)
					enableConditions.add(String.format("eq(n,%d)", start));
				else
					enableConditions.add(String.format("between(n,%d,%d)", start, prev));
				
				// 複数条件を論理和(+)で結合
				String enableExpr = String.join("+", enableConditions);

				if (sb.length() > 0)
					sb.append(",");
				// ★ enableをシングルクォーテーションで囲み、安全にFFmpegに渡す
				sb.append(String.format("drawbox=x=%d:y=%d:w=%d:h=%d:color=black:t=fill:enable='%s'", 
						bounds.x, bounds.y, bounds.width, bounds.height, enableExpr));
			}
		}
		return sb.toString();
	}

	/**
	 * ROIを ImageProcessor に黒塗り(0)で描画する
	 */
	private void applyMaskToImageProcessor(ij.process.ImageProcessor ip, List<RoiObj> rois) {

		if (ip == null) {
			throw new IllegalArgumentException("ImageProcessor must not be null. Pixel extraction was failed.");
		}

		com.vis.core.view.D2.roi.RoiConverter converter = new com.vis.core.view.D2.roi.RoiConverter();
		ip.setColor(Color.BLACK); // 内部的にピクセル値 0 が設定される
		for (RoiObj roi : rois) {
			ij.gui.Roi ijRoi = converter.convert2Roi(roi);
			if (ijRoi != null) {
				ip.fill(ijRoi);
			}
		}
	}

	/**
	 * ImageProcessorのピクセルを、DICOMの形式（エンディアン、符号、RGB順など）に合わせてByte配列にエンコードする
	 */
	private byte[] encodeImageProcessorToBytes(ij.process.ImageProcessor ip, com.vis.dicom.image.DicomImage dcm) {
		PixelDataDecoder pdd = new PixelDataDecoder();
		byte[] blob = pdd.encodeImageProcessorToBytes(ip, dcm);
		return blob;
	}

	/**
	 * ROIの設定(Mode, CustomRange)から、対象となるZCTインデックスの配列を非UIスレッドで算出する
	 */
	private int[] calculateTargetZctIndices(Praparat prap, RoiObj roi) {
		int mode = roiModeMap.getOrDefault(roi, 1);
		String customTxt = roiCustomTextMap.getOrDefault(roi, "");

		java.util.concurrent.ConcurrentHashMap<Integer, SlideGlass> slides = prap.getAllSlides();
		if (slides == null)
			return new int[0];

		if (mode == 0) { // All
			int[] indices = new int[slides.size()];
			int i = 0;
			for (Integer index : slides.keySet())
				indices[i++] = index;
			java.util.Arrays.sort(indices);
			return indices;
		} else if (mode == 1) { // Current
			SlideGlass sg = roi.getSlideGlass();
			if (sg != null) {
				int[] currentZct = prap.getZCTArray(sg);
				int currentZ = currentZct[0];
				java.util.List<Integer> targetIndices = new java.util.ArrayList<>();
				for (Integer idx : slides.keySet()) {
					if (prap.calcZCTArrayFromIndex(idx)[0] == currentZ) {
						targetIndices.add(idx);
					}
				}
				java.util.Collections.sort(targetIndices);
				return targetIndices.stream().mapToInt(i -> i).toArray();
			}
			return new int[0];
		} else if (mode == 2) { // Custom
			java.util.Set<Integer> targetZSet = parseCustomRangeToZ(customTxt);
			java.util.List<Integer> targetIndices = new java.util.ArrayList<>();
			for (Integer idx : slides.keySet()) {
				if (targetZSet.contains(prap.calcZCTArrayFromIndex(idx)[0])) {
					targetIndices.add(idx);
				}
			}
			java.util.Collections.sort(targetIndices);
			return targetIndices.stream().mapToInt(i -> i).toArray();
		}
		return new int[0];
	}

	private java.util.Set<Integer> parseCustomRangeToZ(String text) {
		java.util.Set<Integer> zSet = new java.util.TreeSet<>();
		if (text == null || text.trim().isEmpty())
			return zSet;
		String[] parts = text.split(",");
		for (String part : parts) {
			part = part.trim();
			if (part.isEmpty())
				continue;
			try {
				if (part.contains("-")) {
					String[] range = part.split("-");
					if (range.length == 2) {
						int start = Integer.parseInt(range[0].trim()) - 1;
						int end = Integer.parseInt(range[1].trim()) - 1;
						int min = Math.max(0, Math.min(start, end));
						int max = Math.max(start, end);
						for (int z = min; z <= max; z++)
							zSet.add(z);
					}
				} else {
					int val = Integer.parseInt(part) - 1;
					if (val >= 0)
						zSet.add(val);
				}
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		return zSet;
	}

	/**
	 * DBなどから取得したスタディ情報を受け取り、ツリーを構築する
	 * 
	 * @param study 入力された1つのスタディ
	 */
	public void loadStudyData(DICOMNode study) {
		if (study == null)
			return;

		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			Log.logger.log(Level.SEVERE, "Graphy DB cannot found !");
			return;
		}

		String pid = study.getData(DICOMNode.PatientID);
		String studyUID = study.getData(DICOMNode.StudyInstanceUID);

		if (db.getNumOfSeries(pid, studyUID) <= 0) {
			Log.logger.log(Level.SEVERE, "This study does not have any series... please check DB records !");
			return;
		};

		// 1. Rootノードを作成（UI上は非表示にします）
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

		// 2. スタディノードの作成
		String studyLabel = String.format("Study: %s (%s,%s)", study.getData(DICOMNode.PatientName),
				study.getData(DICOMNode.StudyDate), study.getData(DICOMNode.Modality));
		StudyCheckBoxTree.CheckBoxNode studyNode = new StudyCheckBoxTree.CheckBoxNode(studyLabel, null);

		praparatMap.clear(); // 初期化

		// 3. シリーズノードの作成と追加
		List<HashMap<String, String>> seriesList = db.getSeriesInfoByUIDs(pid, studyUID);
		for (HashMap<String, String> seriesInfo : seriesList) {
			String seriesLabel = String.format("Series %s: %s [%s] (%s imgs)", seriesInfo.get("SeriesNumber"),
					seriesInfo.get("SeriesDescription"), seriesInfo.get("Modality"),
					seriesInfo.get("NumOfInstanceInSeries"));

			StudyCheckBoxTree.CheckBoxNode seriesNode = new StudyCheckBoxTree.CheckBoxNode(seriesLabel, seriesInfo);
			studyNode.add(seriesNode);

			// ★ ここで各シリーズ用のPraparatを構築してMapに保持しておく
			String seriesUid = seriesInfo.get("SeriesInstanceUID");
			Praparat prap = new Praparat(Praparat.ViewMode.SingleGrid);
			prap.loadSeries(pid, studyUID, seriesUid, null);
			setupPraparatRoiListener(prap);
			praparatMap.put(seriesUid, prap);
		}

        root.add(studyNode);

        // 4. ツリーモデルを更新
        DefaultTreeModel model = new DefaultTreeModel(root);
        studyTree.setModel(model);

        // 5. Rootを隠して、スタディを最上位として見せる
        studyTree.setRootVisible(false);
        studyTree.setShowsRootHandles(true);

        // 全ノードを展開状態にする
        expandAllNodes(studyTree, 0, studyTree.getRowCount());
        
    }

	protected void loadSeriesToPraparat(HashMap<String, String> seriesInfo) {
		// 1. Praparat にシリーズの画像データをセットする
		String pid = seriesInfo.get(RoiDBKey.PatientID.name());
		String studyUid = seriesInfo.get(RoiDBKey.StudyInstanceUID.name());
		String seriesUid = seriesInfo.get(RoiDBKey.SeriesInstanceUID.name());
		Praparat targetPrap = praparatMap.get(seriesUid);
		if (targetPrap != null && targetPrap != currentActivePraparat) {

			// ★ 追加: もしプレビューがONなら、強制的にOFFにしてクリーンアップを実行する
			if (btnTogglePreview != null && btnTogglePreview.isSelected()) {
				btnTogglePreview.doClick(); // クリックしたことにしてOFFの処理を走らせる
			}

			currentActivePraparat = targetPrap;

			// 画面の差し替え
			for (Component c : seriesDisplayPanel.getComponents()) {
				if (c instanceof Praparat) {
					seriesDisplayPanel.remove(c);
				}
			}

			seriesDisplayPanel.add(currentActivePraparat, BorderLayout.CENTER);
			currentActivePraparat.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			seriesDisplayPanel.revalidate();
			seriesDisplayPanel.repaint();

		}

		// 2. SwingWorkerによるバックグラウンド処理の定義
		SwingWorker<Void, Void> loadWorker = new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				// ▼ ここはバックグラウンドスレッドで実行されるため、UIはフリーズしません
				// 内部の future.get() が終わるまで、このスレッドはしっかり待機します
				currentActivePraparat.loadSeries(pid, studyUid, seriesUid, null);
				currentActivePraparat.doSingleGridLayout();
				currentActivePraparat.loadRoisFromDB();
				return null;
			}

			@Override
			protected void done() {
				// ▼ doInBackground() が完全に終わった後、UIスレッドで自動的に呼ばれます
				try {
					get(); // 万が一ロード中に例外が起きていればここでキャッチできる

					// 3. ロードが「確実に」終わったので、安全に最初の画像を表示
					currentActivePraparat.setImagePositionUsingSlider(0);

					// 2. ロード直後はツールをデフォルト（ポインター）に戻す
					toolButtonGroup.clearSelection();
					setSelectedState4Toggles("Pointer");

					currentActivePraparat.setLocalToolType(Viewer2DToolBar.Windowing);

					// 3. （オプション）以前描画したROIがこのシリーズ用にあればリストを更新する
					updateMaskRoiListForCurrentSeries();

				} catch (Exception ex) {
					String detail = ex.getMessage() != null ? ex.getMessage() : ex.toString();
					Log.logger.severe("Failed to load series: " + detail);
					ex.printStackTrace();
					JOptionPane.showMessageDialog(PixelAnonymizerPanel.this,
							Resources.i18n("PixelAnonymizerPanel.error.loadSeries") + " " + detail,
							Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
				} finally {
					// マウスカーソル等を元に戻す
					currentActivePraparat.setCursor(Cursor.getDefaultCursor());
				}
			}
		};

		// 3. 処理を実行！
		loadWorker.execute();
	}

	// ツリーをすべて展開するヘルパーメソッド
	private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
		for (int i = startingIndex; i < rowCount; ++i) {
			tree.expandRow(i);
		}
		if (tree.getRowCount() != rowCount) {
			expandAllNodes(tree, rowCount, tree.getRowCount());
		}
	}

	/**
	 * ROIツールバーのボタンを作成するヘルパーメソッド
	 */
	private JToggleButton createToolButton(String name, int toolType, boolean selected) {
		JToggleButton btn = new JToggleButton(name, selected);
		btn.getModel().setActionCommand(name);
		toolButtonGroup.add(btn);

		btn.addActionListener(e -> {
			if (btn.isSelected()) {
				if (currentActivePraparat == null) {
					return;
				}
				Log.logger.info("ToolType: " + toolType);
				currentActivePraparat.setLocalToolType(toolType);
			}
		});
		// 初期選択状態のボタンなら、生成時にツールをセットしておく
		if (selected && currentActivePraparat != null) {
			currentActivePraparat.setLocalToolType(toolType);
		}
		return btn;
	}

	private void setActionApplyToAllSeries(JButton btnApplyAll) {
		btnApplyAll.addActionListener(e -> {
			if (tempRois.isEmpty()) {
				JOptionPane.showMessageDialog(this, Resources.i18n("PixelAnonymizerPanel.error.noRoi"),
						Resources.i18n("dialog.title.information"), JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			DatabaseHandler db = DatabaseHandler.getInstance();
			if (db == null) {
				JOptionPane.showMessageDialog(this, Resources.i18n("PixelAnonymizerPanel.error.dbNotVisible"),
						Resources.i18n("dialog.title.warning"), JOptionPane.WARNING_MESSAGE);
				return;
			}

			// ツリーから「チェックがONの全シリーズ」を取得する（前回作成したメソッド）
			List<HashMap<String, String>> targetSeriesList = getTargetSeriesList();
			if (targetSeriesList.isEmpty())
				return;

			// 現在 Praparat で表示中のシリーズUIDを取得（スキップ用）
			Object[] uids = currentActivePraparat.getUIDs();
			if (uids == null)
				return;
			String currentSeriesUID = (String) uids[2];
			if (currentSeriesUID == null)
				return;

			int copiedCount = 0;

			for (HashMap<String, String> series : targetSeriesList) {
				String pid = series.get(RoiDBKey.PatientID.name());
				String studyUID = series.get(RoiDBKey.StudyInstanceUID.name());
				String seUID = series.get(RoiDBKey.SeriesInstanceUID.name());

				// 現在表示中のシリーズには既に描いてあるのでスキップ
				if (seUID.equals(currentSeriesUID)) {
					continue;
				}

				/*
				 * 画像の順番を保証する
				 */
				Praparat se = new Praparat(ViewMode.SingleGrid);
				se.loadSeries(pid, studyUID, seUID, null/* load all slice */);

				// カレントの各ROIをコピーして別シリーズに割り当てる
				List<RoiObj> temp = new ArrayList<RoiObj>(tempRois);
				for (RoiObj originalRoi : temp) {
					try {
						SlideGlass sg = originalRoi.getSlideGlass();
						if (sg == null) {
							continue;
						}

						// 1. ROIに紐づくパネルを探す
						MaskRoiPanel panel = getMaskRoiPanelByRoi(originalRoi);
						if (panel == null)
							continue;

						// 2. パネルから現在の設定値を取得する
						int selectedMode = panel.getSelectedRangeMode(); // 例: 2 (Custom Range)
						String customRangeText = panel.getCustomRangeText(); // 例: "2,4,6"

						int zctIdx = currentActivePraparat.getZCTIndex(sg);
						RoiObj clonedRoi = (RoiObj) originalRoi.clone();
						// 2. 所属するUIDsをターゲットのものに書き換え,DBに保存
						SlideGlass sg2 = se.getAllSlides().get(zctIdx);
						if (sg2 != null) {
							clonedRoi.setSlideGlass(sg2, true);
							sg2.addRoi(clonedRoi);
						}
						// 4. ダイアログ中断時のクリーンアップ対象（tempRois）にも追加しておく
						tempRois.add(clonedRoi);
						// 別のシリーズのため、コンテキストのみ保持
						roiModeMap.put(clonedRoi, selectedMode);
						roiCustomTextMap.put(clonedRoi, customRangeText);
						copiedCount++;
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}

			// ★ 既存のi18nキー(PixelAnonymizerPanel.info.maskCopied)が使われていなかったため、正しく利用する
			String msg = java.text.MessageFormat.format(Resources.i18n("PixelAnonymizerPanel.info.maskCopied"),
					targetSeriesList.size() - 1, copiedCount);
			JOptionPane.showMessageDialog(this, msg, Resources.i18n("dialog.title.information"),
					JOptionPane.INFORMATION_MESSAGE);
		});
	}

	private void setActionPreviewMask(JToggleButton btnTogglePreview) {
		btnTogglePreview.addActionListener(e -> {
			boolean isPreviewMode = btnTogglePreview.isSelected();
			if (isPreviewMode) {
				// ---------------------------------------------------
				// プレビュー ON: 設定に従ってマスクを黒塗り＆複製
				// ---------------------------------------------------

				// 描画中のトラブルを防ぐため、強制的にPointerツールに戻す
				setSelectedState4Toggles("Pointer");
				if (currentActivePraparat != null) {
					currentActivePraparat.setLocalToolType(Viewer2DToolBar.Windowing);
				}
				// Applu all seriesを一時的に無効化
				btnApplyAll.setEnabled(false);

				// リストに表示されている全てのMaskRoiPanelをループ
				for (Component comp : maskRoiListPanel.getComponents()) {
					if (comp instanceof MaskRoiPanel) {
						MaskRoiPanel panel = (MaskRoiPanel) comp;
						RoiObj originalRoi = panel.getAttachedRoi();

						// パネルから対象となるZCTインデックスの配列を取得
						int[] targetIndices = panel.getTargetSliceIndices(currentActivePraparat);

						SlideGlass originalSg = originalRoi.getSlideGlass();
						if (currentActivePraparat != null && targetIndices != null && originalSg != null) {

							int originalZctIdx = currentActivePraparat.getZCTIndex(originalSg);
							boolean containsOriginal = false;
							for (int idx : targetIndices) {
								if (idx == originalZctIdx) {
									containsOriginal = true;
									break;
								}
							}

							// 1. 元のROI自体の表示を制御する
							if (containsOriginal) {
								// ターゲットに含まれるなら黒塗り
								originalRoi.setStrokeColor(Color.BLACK);
								originalRoi.setFillColor(new Color(0, 0, 0, 255));
							} else {
								// ターゲットに含まれないならプレビュー中は「完全透明」にして消す
								originalRoi.setStrokeColor(new Color(0, 0, 0, 0));
								originalRoi.setFillColor(new Color(0, 0, 0, 0));
							}

							// 2. 指定された他のスライスにROIを複製して配置する
							for (int idx : targetIndices) {
								SlideGlass targetSg = currentActivePraparat.getAllSlides().get(idx);

								// 元のスライス以外にのみ複製を配置
								if (targetSg != null && targetSg != originalSg) {
									try {
										//RoiIDを共有している
										RoiObj clonedRoi = (RoiObj) originalRoi.clone();
										clonedRoi.setSlideGlass(targetSg, false); // DB保存フラグなどはfalseに
										clonedRoi.setStrokeColor(Color.BLACK);
										clonedRoi.setFillColor(new Color(0, 0, 0, 255));
										targetSg.addRoi(clonedRoi);
										previewClonedRois.add(clonedRoi); // 削除用に記録
									} catch (Exception ex) {
										ex.printStackTrace();
									}
								}
							}
						}
					}
				}
			} else {
				// ---------------------------------------------------
				// プレビュー OFF: 複製を削除し、元の色に戻す
				// ---------------------------------------------------

				// 1. 複製したプレビュー用ROIをすべて画面から削除
				for (RoiObj clonedRoi : previewClonedRois) {
					SlideGlass sg = clonedRoi.getSlideGlass();
					if (sg != null) {
						sg.deleteRoi(clonedRoi);
					}
				}
				previewClonedRois.clear(); // リストを空にする

				// 2. 元のROIの色を半透明の黄色に戻す
				for (RoiObj originalRoi : tempRois) {
					originalRoi.setStrokeColor(null); // デフォルト
					originalRoi.setFillColor(new Color(255, 255, 0, 50));
				}

				btnApplyAll.setEnabled(true);
			}

			// Praparat全体を再描画して色変更を反映
			if (currentActivePraparat != null) {
				currentActivePraparat.repaint();
			}
		});
	}

	private void setActionClearAll(JButton btnClearAll) {
		btnClearAll.addActionListener(e -> {
			if (tempRois.isEmpty())
				return;

			int result = JOptionPane.showConfirmDialog(this,
					Resources.i18n("PixelAnonymizerPanel.confirm.clearAllRois"),
					Resources.i18n("PixelAnonymizerPanel.title.clearAllRois"),
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

			if (result == JOptionPane.YES_OPTION) {

				// ★ 追加: もしプレビューがONなら、強制的にOFFにしてクリーンアップを実行する
				if (btnTogglePreview != null && btnTogglePreview.isSelected()) {
					btnTogglePreview.doClick(); // クリックしたことにしてOFFの処理を走らせる
				}

				// 現在 Praparat で表示中のシリーズUIDを取得
				Object[] uids = currentActivePraparat.getUIDs();
				if (uids == null) {
					return;
				}
				String currentSeriesUID = (String) uids[2];
				if (currentSeriesUID == null)
					return;

				// 削除対象となるROIを一時的に集めるリスト
				List<RoiObj> roisToRemove = new ArrayList<>();

				// 1. tempRois の中から、カレントシリーズに属するROIだけを抽出
				for (RoiObj roi : tempRois) {
					String roiSeriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());

					// UIDが一致する場合（または未設定の場合も現在のものとみなす安全策）
					if (roiSeriesUID == null || roiSeriesUID.equals(currentSeriesUID)) {
						roisToRemove.add(roi);
					}
				}

				// 2. 抽出したROIを画面(SlideGlass)、DB、そして追跡リストから削除
				for (RoiObj roi : roisToRemove) {
					SlideGlass sg = roi.getSlideGlass();
					if (sg != null) {
						sg.deleteRoi(roi); // CanvasGlass/SlideGlassから消す
					}
					tempRois.remove(roi); // 追跡リストから除外
				}

				// 3. UIリストの再構築（現在のシリーズのものだけ表示するため）
				updateMaskRoiListForCurrentSeries();
			}
		});
	}

	private void setSelectedState4Toggles(String name) {
		if (toolButtonGroup == null) {
			return;
		}
		while (toolButtonGroup.getElements().hasMoreElements()) {
			AbstractButton ab = toolButtonGroup.getElements().nextElement();
			ButtonModel m = ab.getModel();
			if (m.getActionCommand().equals(name)) {
				toolButtonGroup.setSelected(m, true);
				break;
			}
		}
	}

	/**
	 * Praparat 上で ROI が作成/削除された時のリスナーを設定する
	 */
	private void setupPraparatRoiListener(Praparat pp) {

		RoiObjListener listener = new RoiObjListener() {

			@Override
			public void roiModified(SlideGlass sg, int actionId) {

				if (sg == null) {
					return;
				}

				RoiObj currentRoi = sg.getActiveRoi();

//            	System.out.println("Listen !");
				if (currentRoi == null) {
					return;
				}

				/*
				 * どのアクションであっても、念の為追跡する 追跡リストに記録
				 */
				if (currentRoi != null) {
					if (!tempRois.contains(currentRoi)) {
						tempRois.add(currentRoi);
					}
				}

				if (currentRoi.getState() == RoiObj.CONSTRUCTING) {
					// doClick() は「現在の状態を反転」させるため、
	                // 必ず「ON (isSelected) の場合だけ」クリックするようにガードします
	                if (btnTogglePreview.isSelected()) {
	                    btnTogglePreview.doClick(); 
	                }
					return;
				}

				if (actionId == RoiObjListener.MODIFIED || actionId == RoiObjListener.COMPLETED) {
					SwingUtilities.invokeLater(() -> {
						// 新しく描いた時は、初期値として モード1 (Current Slice) を渡す
						addMaskRoiPanel(currentRoi, pp, 1, "");
						currentRoi.setFillColor(new Color(255, 255, 0, 50));
						currentRoi.setFillState(true);
						// doClick() は「現在の状態を反転」させるため、
		                // 必ず「ON (isSelected) の場合だけ」クリックするようにガードします
		                if (btnTogglePreview.isSelected()) {
		                    btnTogglePreview.doClick(); 
		                }
					});
				}

				if (actionId == RoiObjListener.SELECTED) {
					SwingUtilities.invokeLater(() -> {
						for (Component comp : maskRoiListPanel.getComponents()) {
							// コンポーネントが MaskRoiPanel クラスであるか確認
							if (comp instanceof MaskRoiPanel) {
								MaskRoiPanel panel = (MaskRoiPanel) comp;
								panel.setBackground(null);
							}
						}
						MaskRoiPanel panel = getMaskRoiPanelByRoi(currentRoi);
						if (panel != null) {
							// 例: 背景色を変えたり、ボーダーを太くして目立たせる
							panel.setBackground(Color.YELLOW);
							// スクロールバーを自動で動かして、そのパネルを見える位置に持ってくる
							maskRoiListPanel.scrollRectToVisible(panel.getBounds());
						}
					});
				}

				if (actionId == RoiObjListener.DELETED) {
					tempRois.remove(currentRoi);
					SwingUtilities.invokeLater(() -> {
						removeMaskRoiPanelByRoi(currentRoi);
					});
				}
			}
		};

		RoiObj.addRoiListener(listener);
	}

	private void addMaskRoiPanel(RoiObj targetRoi, Praparat pp, int mode, String custumRangeTxt) {

		if (targetRoi == null) {
			return;
		}

		if (getMaskRoiPanelByRoi(targetRoi) != null) {
			// already added
			return;
		}

		if (pp.getCurrentSlide() == null) {
			return;
		}

		String sNo = "Unknown";
		String sDesc = "NoSeDesc";

		if (pp.getCurrentSlide().getHeader() != null) {
			sNo = pp.getCurrentSlide().getHeader().getString(Tag.SeriesNumber);
			sDesc = pp.getCurrentSlide().getHeader().getString(Tag.SeriesDescription);
		}
		
        if (sNo == null) sNo = "Unknown";
        String seriesLabel = "Series " + sNo + (sDesc != null ? ": " + sDesc : "");
        
		// 座標(ZCT)から現在のスライス番号を取得 (先程のサブタスクを活用)
		int currentSlice = pp.getCurrentSlideZCTIndex();
		// 改良した MaskRoiPanel を生成
		MaskRoiPanel roiPanel = new MaskRoiPanel(targetRoi, pp, seriesLabel, currentSlice,
				new MaskRoiPanel.MaskRoiPanelListener() {
					@Override
					public void onRemoveRequested(MaskRoiPanel panel) {
						// 既存の削除処理
						RoiObj r = panel.getAttachedRoi();
						String rid = r.getProperty(RoiDBKey.RoiID);
						// 追跡リストから削除
						tempRois.remove(r);
						// 2. ★ 全スライスからこのROIを完全に抹消する
						// プレビューモードでは、1つのROIが複数のSlideGlassに登録されている可能性があるため
						java.util.concurrent.ConcurrentHashMap<Integer, SlideGlass> allSlides = currentActivePraparat
								.getAllSlides();
						synchronized(currentActivePraparat) {
							if (allSlides != null) {
								for (SlideGlass sg : allSlides.values()) {
									List<RoiObj> roisCopy = new ArrayList<>(sg.getRois());
									for(RoiObj ro: roisCopy) {
										// ★ RoiIDが未設定(null)のROIが存在してもNPEにならないようにする
										if(java.util.Objects.equals(ro.getProperty(RoiDBKey.RoiID), rid)) {
											sg.deleteRoi(ro);
										}
									}
								}
							}
						}
						maskRoiListPanel.remove(panel);
						maskRoiListPanel.revalidate();
						maskRoiListPanel.repaint();
						
						int cnt = 0;
						for(Component con : maskRoiListPanel.getComponents()) {
							if(con instanceof MaskRoiPanel) {
								cnt++;
							}
						}
						if(cnt == 0) {
							//reset preview blackout mode
							// doClick() は「現在の状態を反転」させるため、
				            // 必ず「ON (isSelected) の場合だけ」クリックするようにガードします
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									if (btnTogglePreview.isSelected()) {
						                btnTogglePreview.doClick(); 
						            }
								}
							});
						}

						// パネルが削除されたら記憶していた設定も消す
						roiModeMap.remove(r);
						roiCustomTextMap.remove(r);
						currentActivePraparat.repaint();
					}

					@Override
					public void onRangeChanged(MaskRoiPanel panel) {
						// ★ 追加：ユーザーが設定を変更したらMapに最新状態を記憶させる
						roiModeMap.put(panel.getAttachedRoi(), panel.getSelectedRangeMode());
						roiCustomTextMap.put(panel.getAttachedRoi(), panel.getCustomRangeText());
						// ★ 追加: プレビューON時に設定(コンボボックスやテキスト)が変更されたら、即座にプレビューを再描画する
						if (btnTogglePreview != null && btnTogglePreview.isSelected()) {
							btnTogglePreview.doClick(); // 一旦プレビューをOFFにする
							btnTogglePreview.doClick(); // 再度ONにして最新の設定でプレビューを描画し直す
						}
					}
				});

		// 外部から初期設定値（モード、テキスト）を流し込む
		roiPanel.setRangeSettings(mode, custumRangeTxt);

		// ★ 追加：初期設定値もMapに記憶させておく
		roiModeMap.put(targetRoi, mode);
		roiCustomTextMap.put(targetRoi, custumRangeTxt);

		maskRoiListPanel.add(roiPanel);
		maskRoiListPanel.revalidate();
		maskRoiListPanel.repaint();

	}

	private void removeMaskRoiPanelByRoi(RoiObj targetRoi) {
		MaskRoiPanel targetPanel = getMaskRoiPanelByRoi(targetRoi);
		if (targetPanel != null) {
			maskRoiListPanel.remove(targetPanel);
			maskRoiListPanel.revalidate();
			maskRoiListPanel.repaint();
		}
		int cnt = 0;
		for(Component con : maskRoiListPanel.getComponents()) {
			if(con instanceof MaskRoiPanel) {
				cnt++;
			}
		}
		if(cnt == 0) {
			//reset preview blackout mode
			// doClick() は「現在の状態を反転」させるため、
            // 必ず「ON (isSelected) の場合だけ」クリックするようにガードします
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					if (btnTogglePreview.isSelected()) {
		                btnTogglePreview.doClick(); 
		            }
				}
			});
		}
	}

	/**
	 * ツリーから「チェックがON」になっている SeriesEntity のリストを抽出する
	 */
	public List<HashMap<String, String>> getTargetSeriesList() {
		List<HashMap<String, String>> targetSeries = new ArrayList<>();

		DefaultTreeModel model = (DefaultTreeModel) studyTree.getModel();
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
		if (root == null || root.getChildCount() == 0)
			return targetSeries;

		// スタディノード（Rootの子）を取得
		StudyCheckBoxTree.CheckBoxNode studyNode = (StudyCheckBoxTree.CheckBoxNode) root.getChildAt(0);

		// ※もしスタディ全体のチェックが外れていれば、対象シリーズはゼロとする仕様の場合
		if (!studyNode.isSelected()) {
			return targetSeries;
		}

		// シリーズノードをループ
		for (int i = 0; i < studyNode.getChildCount(); i++) {
			StudyCheckBoxTree.CheckBoxNode seriesNode = (StudyCheckBoxTree.CheckBoxNode) studyNode.getChildAt(i);

			if (seriesNode.isSelected()) {
				// CheckBoxNode に保持させておいたエンティティを取り出す
				HashMap<String, String> entity = (HashMap<String, String>) seriesNode.seriesInfo;
				targetSeries.add(entity);
			}
		}

		return targetSeries;
	}

	/**
	 * 指定されたRoiオブジェクトに紐づくMaskRoiPanelをリストから探して取得する * @param targetRoi 探したいRoiオブジェクト
	 * 
	 * @return 見つかった場合はそのMaskRoiPanel、見つからない場合はnull
	 */
	private MaskRoiPanel getMaskRoiPanelByRoi(RoiObj targetRoi) {
		// パネルがまだない、または引数がnullの場合は早期リターン
		if (targetRoi == null || maskRoiListPanel == null) {
			return null;
		}

		// maskRoiListPanel に追加されているすべてのコンポーネントを走査
		for (Component comp : maskRoiListPanel.getComponents()) {
			// コンポーネントが MaskRoiPanel クラスであるか確認
			if (comp instanceof MaskRoiPanel) {
				MaskRoiPanel panel = (MaskRoiPanel) comp;
				RoiObj roi = panel.getAttachedRoi();
				// パネルが保持している Roi と、引数の Roi のインスタンスが同一か判定
				if (roi == targetRoi) {
					return panel; // 見つかったら返す
				}

				if (roi.equals(targetRoi)) {
					return panel;
				}

			}
		}
		return null; // 最後まで見つからなかった場合
	}

	/**
	 * このパネルで作成された一時ROIをすべてDBから削除する
	 */
	public void cleanupTemporaryRois() {
		for (RoiObj roi : tempRois) {
			try {
				SlideGlass sg = roi.getSlideGlass();
				if (sg != null) {
					sg.deleteRoi(roi);
				}
			} catch (Exception ex) {
				Log.logger.warning("Failed to delete temp ROI: " + ex.getMessage());
			}
		}
		tempRois.clear(); // 削除完了したらリストを空にする
	}

	public boolean isExecuting() {
		return executing;
	}

	public void stopProcess() {
		// SwingWorkerなどをキャンセルするロジック
		// if (worker != null) worker.cancel(true);
		executing = false;
	}
}