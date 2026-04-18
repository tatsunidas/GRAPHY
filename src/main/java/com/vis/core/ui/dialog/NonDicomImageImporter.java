/**
 * Copyright Visionary Imaging Services, inc.
 * @author tatsunidas
 */
package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Calendar;
import java.util.HashMap;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.vis.configuration.ConfigInfo;
import com.vis.core.log.Log;
import com.vis.core.media.NonDicomImportOrchestrator;
import com.vis.core.media.NonDicomMediaContext;
import com.vis.core.util.DateUtils;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.Tag;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.dimse.DimseUtilities;

/**
 * 
 * convart consumer format image/video/pdf to dicom
 * 
 * Premise;
 * Motivation to import a general image/video is saving it as a secondary capture.
 * Integration into the existing Dicom series is not recommended.(but this can do by integrate series function)
 * 
 * Functions:
 * Integrate general images into an existing study as a new series
 * If new patient, will import as a new studies not yet available in DB.
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class NonDicomImageImporter extends JDialog implements Runnable{
	
	JFileChooser jfc;
	ImportNonDicomImagePanel panel;
	String approveButtonText = "Import";
	String approveToolTip = "";
	
	DatabaseHandler db = DatabaseHandler.getInstance();
	Thread t;
	
	final String NoName = "NoName";//OOMUNE^SOUDAROU
	final String NoPID = "NoPID";
	
	public NonDicomImageImporter(JFrame parent, boolean modal) {
		super(parent, modal);
		if(db == null) {
			Log.logger.severe(" NonDicomImageImporter()::DB does not exists");
			return;
		}
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		panel = new ImportNonDicomImagePanel();
		//add to JFileChooser
		jfc = new JFileChooser();
		jfc.setDialogTitle("Non Dicom Image Importer-select files(no folder)-");
		jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);//NO DIR
		jfc.setMultiSelectionEnabled(true);
		jfc.setCurrentDirectory(new File(System.getProperty("user.home")));
		jfc.setAccessory(panel);
		jfc.setApproveButtonText(approveButtonText);
//		jfc.setApproveButtonToolTipText(approveToolTip);
		doAction(jfc.showOpenDialog(this));
	}
	
	void doAction(int res) {
		if(res != JFileChooser.APPROVE_OPTION) {
			dispose();
			return;
		}
		t = new Thread(this);
		t.start();
	}
	
	void doImport() {
		boolean importNewStudy = panel.isImportNew();
		HashMap<Integer,String> inputs = panel.getInputs();
		
		File[] files = jfc.getSelectedFiles();
		if(files == null || files.length == 0) return;
		
		String pname = inputs.get(Tag.PatientName);
		String pid = inputs.get(Tag.PatientID);
		String sex = inputs.get(Tag.PatientSex);
		String dob = inputs.get(Tag.PatientBirthDate);
		String studyDesc = inputs.get(Tag.StudyDescription);
		String seriesDesc = inputs.get(Tag.SeriesDescription);
		String studyUID = inputs.get(Tag.StudyInstanceUID);
		
		// 1. 患者IDの必須チェック
		if(pid == null || pid.trim().length()==0) {
			int res = PopUpMessage.showDialog(
					this, 
					"PatientID is blank", 
					"You have to input PatientID.\nIf you'd like to continue no PatientID, PatientID will set to NoPID.", 
					JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION);
			if(res != JOptionPane.OK_OPTION) {
				Thread.interrupted();
				return;
			}
			pid = NoPID;
		}
		if(pname == null || pname.trim().length()==0) {
			pname = NoName;
		}

		// 2. Contextの生成とメタデータの詰め込み
		NonDicomMediaContext context = new NonDicomMediaContext();
		context.pname = pname;
		context.pid = pid;
		context.sex = sex;
		context.dob = dob;
		context.studyDesc = studyDesc;
		context.seriesDesc = seriesDesc;
		
		Calendar now = Calendar.getInstance();
		// ※お使いの DateUtils に合わせて現在日時の文字列生成メソッドを調整してください
		String nowDateStr = DateUtils.toDicomDateString(now.getTime());
		String nowTimeStr = DateUtils.toDicomTimeString(now.getTime());
		
		context.contentDate = nowDateStr;
		context.contentTime = nowTimeStr;

		int initialSeriesNumber = 1;

		// 3. 既存Studyに追加するか、新規Studyにするかの分岐
		if(!importNewStudy) {
			// 既存のStudyに追加する場合
			context.studyUID = studyUID;
			HashMap<String, String> studyInfo = db.getStudyInfo(pid, studyUID);
			int numOfSeries = db.getNumOfSeriesInStudy(studyUID);
			
			if(numOfSeries == 0) {
				PopUpMessage.showDialog(this, "NoneDicomFileImport Error", "This study does not have any series, empty study.", JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			// 既存のStudy情報を引き継ぐ
			context.studyDate = studyInfo.get("StudyDate");
			context.studyTime = studyInfo.get("StudyTime");
			
			initialSeriesNumber = numOfSeries + 1; // 続きの番号から
		} else {
			// 新規Studyの場合
			context.studyUID = UIDUtils.createUID();
			context.studyDate = nowDateStr;
			context.studyTime = nowTimeStr;
		}

		// 4. 一時ディレクトリの作成
		Path tempDir = null;
		try {
			tempDir = Files.createTempDirectory(ConfigInfo.AppName.toString(), new FileAttribute<?>[0]);
		} catch (IOException e) {
			Log.logger.severe(e.getMessage());
			Thread.interrupted();
			return;
		}
		
		// 5. オーケストレーターで処理する
		try {
			NonDicomImportOrchestrator orchestrator = new NonDicomImportOrchestrator();
			// 配列をListに変換して渡す
			orchestrator.executeImport(java.util.Arrays.asList(files), context, tempDir.toFile(), initialSeriesNumber);
			
			// 6. 変換が終わったらDBへ送信
			File[] outFiles = tempDir.toFile().listFiles();
			if (outFiles != null && outFiles.length > 0) {
				DimseUtilities.sendMe(outFiles);
			}
			
		} catch (Exception e) {
			Log.logger.severe("Import Failed: " + e.getMessage());
			e.printStackTrace();
		} finally {
			// 7. 一時フォルダのお掃除
			if (tempDir != null && Files.exists(tempDir)) {
				File[] tempFiles = tempDir.toFile().listFiles();
				if (tempFiles != null) {
					for(File f : tempFiles) {
						try { Files.delete(f.toPath()); } catch (IOException e) { e.printStackTrace(); }
					}
				}
				try { Files.delete(tempDir); } catch (IOException e) { e.printStackTrace(); }
				Log.logger.fine("Temporary directory deleted");
			}
		}
	}
	
	@Override
	public void run() {
		doImport();
		if(t != null && t.isAlive()) {
			Thread.interrupted();
		}
	}
}
