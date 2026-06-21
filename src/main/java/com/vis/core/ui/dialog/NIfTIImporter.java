/**
 * Copyright Visionary Imaging Services, inc.
 * @author tatsunidas
 */
package com.vis.core.ui.dialog;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Date;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.vis.configuration.ConfigInfo;
import com.vis.core.log.Log;
import com.vis.core.media.NIfTIToDicomConverter;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.dimse.DimseUtilities;

@SuppressWarnings("serial")
public class NIfTIImporter extends JDialog implements Runnable {
	
	private JFileChooser jfc;
	private ImportNIfTIPanel panel;
	private DatabaseHandler db = DatabaseHandler.getInstance();
	private Thread t;
	
	// ★ プログレスバー用のUIコンポーネント
	private JProgressBar progressBar;
	private JLabel statusLabel;
	
	private final String NoName = "NoName";
	private final String NoPID = "NoPID";
	
	public NIfTIImporter(JFrame parent, boolean modal) {
		super(parent, modal);
		if(db == null) {
			Log.logger.severe("NIfTIImporter()::DB does not exists");
			return;
		}
		
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		panel = new ImportNIfTIPanel();
		
		jfc = new JFileChooser() {
			@Override
			public void approveSelection() {
				String studyDateStr = panel.getStudyDateString();
				if (!studyDateStr.isEmpty()) {
					// 日付が入力されている場合、フォーマットが正しいか厳格にチェック
					java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd");
					sdf.setLenient(false); // 存在しない日付（13月など）をエラーにする
					try {
						sdf.parse(studyDateStr);
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(this, 
								"Invalid Study Date format.\nPlease use yyyy/MM/dd or leave it blank.", 
								"Input Error", JOptionPane.ERROR_MESSAGE);
						return; // ★ エラーの場合は return する（ダイアログを閉じずに処理を中断）
					}
				}
				// エラーがなければ本来の処理（ダイアログを閉じて doAction へ進む）を実行
				super.approveSelection();
			}
		};
		jfc.setDialogTitle("NIfTI to DICOM Importer -select .nii or .nii.gz-");
		jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		jfc.setMultiSelectionEnabled(true);
		jfc.setFileFilter(new FileNameExtensionFilter("NIfTI Images (*.nii, *.nii.gz)", "nii", "gz"));
		jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
		jfc.setAccessory(panel);
		jfc.setApproveButtonText("Convert & Import");
		
		// JFileChooser はモーダルで開く
		// "this" is never shown (same issue as NonDicomImageImporter) - anchor on
		// the already-realized parent to avoid "Window must not be zero".
		doAction(jfc.showOpenDialog(parent));
	}
	
	private void doAction(int res) {
		if(res != JFileChooser.APPROVE_OPTION) {
			dispose();
			return;
		}
		
		// ★ ファイル選択後、このダイアログをプログレス画面として構築する
		setTitle("Importing NIfTI...");
		setLayout(new BorderLayout(10, 10));
		JPanel p = new JPanel(new GridLayout(2, 1, 5, 5));
		p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		
		statusLabel = new JLabel("Initializing...");
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		
		p.add(statusLabel);
		p.add(progressBar);
		add(p, BorderLayout.CENTER);
		
		setSize(400, 150);
		setLocationRelativeTo(getParent());
		
		// 変換スレッドを開始
		t = new Thread(this);
		t.start();
		
		// ダイアログを表示して待機（処理が終わるまでブロックされる）
		setVisible(true); 
	}
	
	private void doImport() {
		boolean importNewStudy = panel.isImportNew();
		HashMap<Integer, String> inputs = panel.getInputs();
		Modality modality = panel.getSelectedModality();
		File metaJson = panel.getMetaJsonFile();
		File[] niftiFiles = jfc.getSelectedFiles();
		
		if(niftiFiles == null || niftiFiles.length == 0) return;
		
		String pid = inputs.get(Tag.PatientID);
		String pname = inputs.get(Tag.PatientName);
		String studyUID = inputs.get(Tag.StudyInstanceUID);
		
		if(pid == null || pid.trim().isEmpty()) pid = NoPID;
		if(pname == null || pname.trim().isEmpty()) pname = NoName;
		
		Date targetStudyDate = new Date(); // デフォルトは現在時刻
		String studyDateStr = panel.getStudyDateString();
		if (!studyDateStr.isEmpty()) {
			try {
				// 事前に JFileChooser の approveSelection でフォーマットチェック済みなので安全
				targetStudyDate = new java.text.SimpleDateFormat("yyyy/MM/dd").parse(studyDateStr);
			} catch (Exception e) {} 
		}

		int nextSeriesNumber = 1;
		if (!importNewStudy) {
			nextSeriesNumber = db.getNumOfSeriesInStudy(studyUID) + 1;
		}

		Path tempDir = null;
		try {
			tempDir = Files.createTempDirectory(ConfigInfo.AppName.toString() + "_nifti", new FileAttribute<?>[0]);
			
			int fileIndex = 1;
			int totalFiles = niftiFiles.length;
			
			for (File nifti : niftiFiles) {
				String currentSeriesUID = com.vis.dicom.UIDUtils.createUID();
				
				final String prefix = "[" + fileIndex + "/" + totalFiles + "] ";
				
				// NIfTIファイルのロード中はプログレスバーをリセット
				SwingUtilities.invokeLater(() -> {
					statusLabel.setText(prefix + "Loading " + nifti.getName() + "...");
					progressBar.setValue(0);
				});

				// ★ 進捗をUIに反映するリスナーを定義
				NIfTIToDicomConverter.ProgressListener listener = (current, total, msg) -> {
					SwingUtilities.invokeLater(() -> {
						progressBar.setIndeterminate(false); // アニメーションを解除
						progressBar.setString(null);         // デフォルトの「〇〇%」表示に戻す
						progressBar.setMaximum(total);
						progressBar.setValue(current);
						statusLabel.setText(prefix + "Converting: " + current + " / " + total + " slices");
					});
				};

				NIfTIToDicomConverter.saveAsDicom(
						nifti, metaJson, tempDir.toFile().getAbsolutePath(), modality, 
						pid, pname, studyUID, currentSeriesUID, targetStudyDate, nextSeriesNumber, listener // ★ リスナーを渡す
				);
				
				nextSeriesNumber++;
				fileIndex++;
			}
			
			// データベース（PACS）への送信処理
			SwingUtilities.invokeLater(() -> {
				statusLabel.setText("Sending to Database...");
				progressBar.setIndeterminate(true);  // 送信中は左右に動くアニメーションにする
				progressBar.setString("Sending..."); // ★ 0%の代わりに「Sending...」と表示させる
			});
			
			File[] dicomFiles = tempDir.toFile().listFiles();
			if (dicomFiles != null && dicomFiles.length > 0) {
				DimseUtilities.sendMe(dicomFiles);
				SwingUtilities.invokeLater(() -> {
					PopUpMessage.showDialog(this, "Success", "Import completed successfully.", JOptionPane.OK_OPTION,JOptionPane.INFORMATION_MESSAGE);
				});
			}
			
		} catch (Throwable e) { 
			Log.logger.severe("NIfTI Import Failed: " + e.getMessage());
			e.printStackTrace();
			SwingUtilities.invokeLater(() -> {
				PopUpMessage.showDialog(this, "Fatal Error", "Import failed: " + e.toString(), JOptionPane.OK_OPTION,JOptionPane.ERROR_MESSAGE);
			});
		} finally {
			cleanUp(tempDir);
			SwingUtilities.invokeLater(() -> dispose());
		}
	}
	
	private void cleanUp(Path path) {
		if (path == null || !Files.exists(path)) return;
		try {
			File[] files = path.toFile().listFiles();
			if (files != null) {
				for (File f : files) f.delete();
			}
			Files.delete(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		doImport();
	}
}