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
package com.vis.db;

import java.awt.Window;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

import javax.swing.JOptionPane;
import javax.swing.tree.DefaultMutableTreeNode;

import org.apache.commons.io.FileUtils;
import org.apache.derby.jdbc.EmbeddedDataSource;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.DBUtils;
import com.vis.core.util.DateUtils;
import com.vis.core.util.DeleteFolder;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.roi.RoiGeometry;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
import com.vis.dicom.dimse.DcmQRSCP;
import com.vis.dicom.dimse.DimseUtilities;
import com.vis.dicom.image.DicomImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vis.configuration.ConfigInfo;
import com.vis.configuration.RoiDBKey;
import com.vis.configuration.Resources;

/**
 * 
 * DatabaseHandler is a GRAPHY DB. DatabaseHandler has main two servers. - derby
 * : local db used to any tables and communicate with dcmqrscp. - DicomServer :
 * dcmqrscp.
 * 
 * @author tatsunidas
 */
public class DatabaseHandler {

	/*
	 * unit test
	 */
	public static void main(String[] args) {

//		String testDir = "/home/tatsunidas/デスクトップ/graphy/";
		DatabaseHandler db = new DatabaseHandlerBuilder().build();
		try {
			db.startingUp();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		db.shutdownDB();
	}

	/**
	 * Builder
	 * 
	 * @author tatsunidas
	 *
	 */
	public static class DatabaseHandlerBuilder {

		public DatabaseHandlerBuilder() {
		}

		public DatabaseHandler build() {
			return new DatabaseHandler(this);
		}
	}

	// singleton
	private static DatabaseHandler datbaseRef;

	// location
	private String dbdir = "";

	// derby
	private EmbeddedDataSource derby;
	private final String protocol = "jdbc:derby:";// connectionURL
	private final String driverName = "org.apache.derby.jdbc.EmbeddedDriver";
	private final String databasename = "graphydb";// will become db folder name
	private final String username = "graphy";
	private final String password = "graphy-mtfbwy";

	// dcmqrscp
	private DicomServer dcmqrscp;
	// DICOMweb (QIDO-RS/WADO-RS/STOW-RS) サーバー。DIMSEのdcmqrscpとはポート・プロトコルが別。
	private com.vis.dicom.web.DicomWebServer dicomWebServer;
	public final String defaultAET = "GRAPHY";
	public final String defaultHost = "localhost";
	public final String defaultPort = "4891";// for dimse,
//	private String defaultDBDir = DBUtils.defaultDBLocation();//see, Utils.getGraphyDBLocationFromProp()
	private boolean useDicomDir = false;

	/* ae.properties for dicomdir mode */
	private String aeProp = new File("./conf/ae.properties").getAbsolutePath();

	private boolean saveAsLink = false;

	private java.util.logging.Logger logger = Log.logger;

	private DatabaseHandler(DatabaseHandlerBuilder builder) {
		datbaseRef = this;
	}

	public void addNewLocale(String localeid) throws SQLException {
		String languagecode = "", countrycode = "";
		String languageAndCountry[] = localeid.split("_");
		if (languageAndCountry.length >= 2) {
			languagecode = languageAndCountry[0];
			countrycode = languageAndCountry[1];
		}
		Locale locale = new Locale(languagecode, countrycode);
		String language = locale.getDisplayLanguage();
		String country = locale.getDisplayCountry();
		insertLocale(language, country, languagecode, countrycode, localeid);
	}

	private double[] blob2DoubleArray(Blob b) {
		if (b == null) {
			return null;
		}
		try {
			int blobLength = (int) b.length();
			byte[] blobAsBytes = b.getBytes(1, blobLength);
			ByteBuffer bb2Back = ByteBuffer.wrap(blobAsBytes);
			double[] res = new double[blobAsBytes.length / 8];
			for (int i = 0; i < res.length; i++) {
				res[i] = bb2Back.getDouble();
			}
			b.free();
			return res;
		} catch (SQLException e) {
			b = null;
			return null;
		}
	}

	public boolean checkCanImport(DicomObject ds) {
		String studyUID = ds.getString(Tag.Study​Instance​UID);
		String seriesUID = ds.getString(Tag.Series​Instance​UID);
		String sopUID = ds.getString(Tag.SOP​Instance​UID);
		/* check already exists */
		if (checkImageRecordExists(studyUID, seriesUID, sopUID)) {
			return false;
		} else {
			return true;
		}
		/* add another rules... */
	}

	public boolean checkDBExists() {
		try {
			verifyConnection();
			try (Connection conn = openConnection()) {
				Set<String> tbl = getDBTable(conn);
				conn.commit();
				if (tbl == null || tbl.size() == 0) {
					return false;
				} else {
					return true;
				}
			}
		} catch (SQLException e) {
			logger.severe("database connection is not established: SQLState=" + e.getSQLState() + ", msg=" + e.getMessage());
			String msg;
			if ("XJ040".equals(e.getSQLState())) {
				// Derby database already locked by another JVM instance → GRAPHY is already running
				msg = "GRAPHY is already working!\n\nPlease close the existing GRAPHY window before starting a new one.";
				logger.warning("Derby DB is locked by another process. GRAPHY is likely already running.");
			} else {
				msg = "Failed to read the existing database or create a new one.\n"
						+ "If you have just updated GRAPHY and an existing database is present,\n"
						+ "this may be caused by SQL differences.\n"
						+ "Please try backing up or renaming the old '.GRAPHY' folder, and then restart the application.";
			}
			JOptionPane.showMessageDialog(null, msg, "GRAPHY", JOptionPane.WARNING_MESSAGE);
			return false;
		}
	}

	/**
	 * データベースへの接続を試み、接続可能であることを検証します。 このメソッドが正常に終了した場合、データベースは存在し、接続可能です。
	 *
	 * @throws SQLException データベースに接続できない場合。例外オブジェクトに原因が含まれます。
	 */
	public void verifyConnection() throws SQLException {
		try (Connection conn = openConnection()) {
			// Connectionオブジェクトが正常に取得できれば、検証は成功。
			// try-with-resourcesが自動的にコネクションを閉じてくれるため、
			// このブロック内では何もする必要はありません。
		}
		// openConnection() がSQLExceptionをスローした場合、
		// このメソッドはそれをキャッチせず、そのまま呼び出し元にスローします。
	}

	public boolean checkImageRecordExists(String studyIUID, String seriesIUID, String sopIUID) {
		String statement = "SELECT * FROM IMAGE WHERE StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, studyIUID);
			pstmt.setString(2, seriesIUID);
			pstmt.setString(3, sopIUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					conn.commit();
					return true;
				}
			}
			conn.commit();
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	public boolean checkRecordExists(String tablename, String fieldname, String value) {
		boolean res = false;
		String sql = "SELECT COUNT(" + fieldname + ") FROM " + tablename + " WHERE " + fieldname + " = ?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, value);
			try (ResultSet rs = pstmt.executeQuery();) {
				rs.next();
				if (rs.getInt(1) > 0) {
					res = true;
					conn.commit();
					return res;
				}
			}
			conn.commit();
		} catch (SQLException e) {
			return false;
		}
		return res;
	}

	public boolean checkSeriesRecordExists(String patID, String studyIUID, String seriesIUID) {
		String statement = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, seriesIUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				rset.setFetchSize(3);
				if (rset.next()) {
					conn.commit();
					return true;
				}
			}
			conn.commit();
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	public boolean checkStudyRecordExists(String patID, String studyIUID) {
		String statement = "SELECT * FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					conn.commit();
					return true;
				}
			}
			conn.commit();
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	/**
	 * データベースに必要なテーブルをすべて作成します。 すべてのテーブル作成は単一のトランザクションとして実行され、
	 * 一つでも失敗した場合はすべての変更がロールバックされます。
	 *
	 * @throws SQLException テーブルの作成に失敗した場合
	 */
	private void createTables() throws SQLException {
		// 1. 実行したいSQLリソースをリストにまとめる
		List<Resources> sqlResources = Arrays.asList(Resources.SQL_PATIENT, Resources.SQL_STUDY, Resources.SQL_SERIES,
				Resources.SQL_IMAGE, Resources.SQL_LISTENER, Resources.SQL_SERVERS, Resources.SQL_THEME,
				Resources.SQL_PRESET, Resources.SQL_LOCALE, Resources.SQL_MISCELLANEOUS, Resources.SQL_TEXTANNOTATION,
				Resources.SQL_ROI);

		try (Connection conn = openConnection(); Statement statement = conn.createStatement();) {
			SQLReader reader = new SQLReader(); // SQLReaderは一度だけインスタンス化する
			for (Resources resource : sqlResources) {
				// SQLファイルからクエリ文字列を取得する処理は、可読性のため変数に分ける
				String sqlQuery = reader.createQueries(resource.tempFile()).get(0);
				statement.executeUpdate(sqlQuery);
				logger.info(resource.name() + " テーブルの作成クエリを実行しました。");
			}
			// 3. すべてのクエリが成功した場合、トランザクションをコミット
			conn.commit();
			logger.info("すべてのテーブルが正常に作成され、トランザクションがコミットされました。");
		} catch (SQLException e) {
			// 4. 例外が発生した場合、ログに記録し、例外を再スローする
			// try-with-resources により conn.close() が自動的に呼ばれる。
			// コミットされていないトランザクションは、close時に自動ロールバックされる。
			logger.log(Level.SEVERE, "テーブル作成中にSQLエラーが発生しました。トランザクションはロールバックされます。", e);
			// 呼び出し元に失敗を伝えるために、例外をスローする
			throw e;
		}
	}

	/**
	 * 既存DB(createTables()は新規DB作成時にしか走らない)にDICOMweb用の列が無ければ追加する、
	 * 冪等なマイグレーション。新規DBでもLISTENER.sql側に既に列があるため、ここはno-opになる
	 * (DatabaseMetaData上に列が見つかるため)。Derbyには「列が無ければ追加」という構文が無いため、
	 * メタデータで存在確認してから個別にALTER TABLEする。
	 */
	private void migrateListenerTableIfNeeded() {
		try (Connection conn = openConnection()) {
			DatabaseMetaData meta = conn.getMetaData();
			boolean hasColumn;
			try (ResultSet rs = meta.getColumns(null, null, "LISTENER", "DICOMWEB_ENABLED")) {
				hasColumn = rs.next();
			}
			if (hasColumn) {
				return;
			}
			try (Statement st = conn.createStatement()) {
				st.executeUpdate("ALTER TABLE LISTENER ADD COLUMN dicomweb_enabled boolean default false");
				st.executeUpdate("ALTER TABLE LISTENER ADD COLUMN dicomweb_port integer");
				st.executeUpdate("ALTER TABLE LISTENER ADD COLUMN dicomweb_contextpath varchar(255) default '/dicomweb'");
				conn.commit();
				logger.info("LISTENERテーブルにDICOMweb用の列を追加しました(既存DBのマイグレーション)。");
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "LISTENERテーブルのDICOMweb列マイグレーションに失敗しました。", e);
		}
	}

	/**
	 * Delete all records. Table definitions will remain.
	 */
	@SuppressWarnings("unused")
	private void deleteAllRecord() {
		List<String> tablesToDelete = Arrays.asList("ROI", "TEXTANNOTATION", "MISCELLANEOUS", "LOCALE", "PRESET",
				"MODALITY", "THEME", "AE", "LISTENER", "IMAGE", "SERIES", "STUDY", "PATIENT");
		try (Connection conn = openConnection(); Statement statement = conn.createStatement();) {
			for (String tableName : tablesToDelete) {
				statement.executeUpdate("DELETE FROM " + tableName);
				logger.info("TABLE: '" + tableName + "' was cleaned-up.");
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.log(Level.SEVERE, "テーブル削除中にエラーが発生しました。トランザクションはロールバックされます。", ex);
		}
	}

	/*
	 * delete record and file&roi in instance level. if it save as link, delete only
	 * record, else delete both file and record. if it is last instance, delete
	 * parent directory folder too.
	 */
	public void deleteInstance(String patID, String studyUID, String seriesUID, String sopUID) {
		// the save as link function is not used now...
//		boolean saveAsLink = isInstanceSavedAsLink(patID, studyUID, seriesUID, sopUID);
		boolean saveAsLink = false;

		// file path from table
		String storeURI = getFileLocation(studyUID, seriesUID, sopUID);

		boolean done = false;
		// ROIを先に削除するためのSQL
		String deleteRoiStmt = "DELETE FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		String statement = "DELETE FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";

		// 外側のtry: Connectionの管理
		try (Connection conn = openConnection()) {

			// 1. ROIの削除 (先に実行)
			try (PreparedStatement pstmtRoi = conn.prepareStatement(deleteRoiStmt)) {
				pstmtRoi.setString(1, patID);
				pstmtRoi.setString(2, studyUID);
				pstmtRoi.setString(3, seriesUID);
				pstmtRoi.setString(4, sopUID);
				pstmtRoi.executeUpdate(); // 戻り値(削除件数)のチェックは不要(ROIが存在しない場合もあるため)
			} catch (SQLException e) {
				conn.rollback();
				throw e;
			}

			// 2. IMAGEの削除 (ROI削除後に実行)
			try (PreparedStatement pstmt = conn.prepareStatement(statement)) {
				pstmt.setString(1, patID);
				pstmt.setString(2, studyUID);
				pstmt.setString(3, seriesUID);
				pstmt.setString(4, sopUID);
				// found in db and deleted == 1
				done = pstmt.executeUpdate() == 1;
			} catch (SQLException e) {
				conn.rollback();
				throw e;
			}

			// 3. 両方の削除が成功したらコミット
			conn.commit();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		// if instance does not deleted, return here.
		if (done == false)
			return;

		/**
		 * check empty series/study/patient recursively.
		 */
		if (saveAsLink) {/* delete only record */
			if (getNumOfInstanceInSeries(patID, studyUID, seriesUID) == 0) {
				if (deleteSeriesRecord(patID, studyUID, seriesUID)) {
					if (getNumOfSeries(patID, studyUID) == 0) {
						if (deleteStudyRecord(patID, studyUID)) {
							if (getNumOfStudyByPatient(patID) == 0) {
								// ask would you like delete patient level record
								deletePatientRecord(patID);
							}
						}
					}
				}
			}
		} else {// delete file
			File instance = new File(storeURI);
			File seriesDir = instance.getParentFile();
			if (DeleteFolder.deleteFile(instance)) {// delete file first
				if (getNumOfInstanceInSeries(patID, studyUID, seriesUID) == 0) {
					File studyDir = seriesDir.getParentFile();
					if (DeleteFolder.deleteDirectory(seriesDir)) {// delete file first
						if (deleteSeriesRecord(patID, studyUID, seriesUID)) {
							if (getNumOfSeries(patID, studyUID) == 0) {
								File patDir = studyDir.getParentFile();
								if (DeleteFolder.deleteDirectory(studyDir)) {
									if (deleteStudyRecord(patID, studyUID)) {
										if (getNumOfStudyByPatient(patID) == 0) {
											if (DeleteFolder.deleteDirectory(patDir)) {
												// ask would you like delete patient level record
												deletePatientRecord(patID);
											} else {
												logger.log(Level.SEVERE,
														"Cannot delete patient dir...\n" + patDir.getAbsolutePath());
											}
										}
									}
								} else {
									logger.log(Level.SEVERE,
											"Cannot delete study dir...\n" + studyDir.getAbsolutePath());
								}
							}
						}
					} else {
						logger.log(Level.SEVERE, "Cannot delete series dir...\n" + seriesDir.getAbsolutePath());
					}
				}
			} else {
				logger.log(Level.SEVERE, "Cannot delete image instance file...\n" + storeURI);
			}
		}
	}

	public void deleteMissingLinkedFiles() {
		String statement = "SELECT * FROM IMAGE WHERE isLink=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setBoolean(1, true);
			try (ResultSet rs = pstmt.executeQuery();) {
				while (rs.next()) {
					String url = rs.getString("FileStoreUrl");
					String patID = rs.getString("PatientID");
					String studyUID = rs.getString("StudyInstanceUID");
					String seriesUID = rs.getString("SeriesInstanceUID");
					String sopUID = rs.getString("SOPInstanceUID");
					// check it was missing.
					if (!new File(url).exists()) {
						deleteInstance(patID, studyUID, seriesUID, sopUID);
					}
				}
				conn.commit();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		MainScreen main = WindowManager.getMainScreen();
		if (main != null) {
			main.loadLocalStudiesBySearchKey();
			// reset birds eye view ?
		}
	}

	public boolean deletePatientRecord(String patID) {

		if (getNumOfStudyByPatient(patID) > 0) {
			logger.warning(
					"Cannot delete. This Patient has study records, you should delete under level records first.");
			return false;
		}
		String statement = "DELETE FROM PATIENT WHERE PatientID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			int num = pstmt.executeUpdate();
			conn.commit();
			return num == 1;
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "SQL error occured, at deletePatientRecord", e);
		}
		return false;
	}

	/**
	 * 
	 * @param patID
	 * @param studyUid
	 * @param seriesUid
	 * @param sopUid : Dummy, multi stack対応のために緩和
	 * @param roiId
	 */
	public synchronized void deleteRoi(String patID, String studyUid, String seriesUid, String sopUid, String roiId) {
		String statement = "DELETE FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND RoiID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
//			pstmt.setString(x, sopUid);
			pstmt.setString(4, roiId);
			pstmt.executeUpdate();
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 指定されたテーブルから条件に一致する行を削除します。
	 *
	 * @param tableName  テーブル名 (SQLインジェクションを防ぐため、外部からの入力は検証必須)
	 * @param whereField 条件を指定するカラム名 (SQLインジェクションを防ぐため、外部からの入力は検証必須)
	 * @param whereValue 条件の値
	 * @throws SQLException SQLの実行に失敗した場合
	 */
	public void deleteRow(String tableName, String whereField, String whereValue) throws SQLException {
		String sql = "DELETE FROM " + tableName + " WHERE " + whereField + " = ?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, whereValue);
			// 更新された行数を取得する
			int affectedRows = pstmt.executeUpdate();
			logger.fine(tableName + " テーブルから " + affectedRows + " 行のレコードを削除しました。");
			conn.commit();
		} catch (SQLException e) {
			// 6. 失敗をログに記録し、呼び出し元に例外をスローする
			logger.severe("レコードの削除に失敗しました。トランザクションはロールバックされます。");
			// openConnection() が autoCommit=false のため、
			// commitされずにConnectionが閉じられると自動でロールバックされる。
			throw e;
		}
	}

	public boolean deleteSeriesRecord(String patID, String studyUID, String seriesUID) {
		if (getNumOfInstanceInSeries(patID, studyUID, seriesUID) > 0) {
			logger.severe(
					"Cannot delete. This Series has instance record. you should delete under level records first.");
			return false;
		}
		String statement = "DELETE FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			// num of deleted rows
			int deleted = pstmt.executeUpdate();
			conn.commit();
			return deleted == 1;
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		return false;
	}

	public void deleteServer(String nickname) {
//		int pk_ = getPrimaryKeyIndexInTable("SERVERS", "logicalname", nickname);
		int pk = getCommunicationServerPk(nickname);
		String statement = "DELETE FROM SERVERS WHERE pk=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setInt(1, pk);
			pstmt.executeUpdate();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public boolean deleteStudyRecord(String patID, String studyUID) {
		if (getNumOfSeries(patID, studyUID) > 0) {
			logger.severe(
					"This Study has some series record, if want to delete thie study, delete nested series first. return");
			return false;
		}
		String statement = "DELETE FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			int num = pstmt.executeUpdate();
			conn.commit();
			return num == 1;
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		return false;
	}

	private float[] doubleArr2floatArr(double[] da) {
		if (da == null) {
			return null;
		}
		float[] fa = new float[da.length];
		for (int i = 0; i < da.length; i++) {
			fa[i] = (float) da[i];
		}
		return fa;
	}

	public HashMap<String, String> findSeriesRecordWithSeriesIUIDAnd(String studyIUID, String seriesIUID) {
		String statement = "SELECT * FROM SERIES WHERE StudyInstanceUID=? AND SeriesInstanceUID=?";
		HashMap<String, String> map = new HashMap<>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, studyIUID);
			pstmt.setString(2, seriesIUID);
			ResultSet rset = pstmt.executeQuery();
			if (rset.next()) {
				map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
				map.put("Modality", rset.getString("Modality"));
				map.put("SeriesNumber", rset.getString("SeriesNumber"));
			}
			rset.close();
			conn.commit();
			if (map.size() == 0) {
				return null;
			} else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	/**
	 * StudyInstanceUIDだけからPatientIDを引く小さなヘルパー。
	 * getSeriesInfoByUIDs/getImagesInfoByUIDs/getImageInstanceInfoはPatientIDを
	 * 等価条件で要求する(LIKEではなく=なので、未知/nullでは絶対にマッチしない)ため、
	 * URL上にStudyInstanceUIDしか無いDICOMweb(QIDO-RS/WADO-RS)のスタディ配下エンドポイントは、
	 * まずこれでPatientIDを解決してから既存メソッドに渡す。
	 */
	public String getPatientIDByStudyUID(String studyIUID) {
		String statement = "SELECT PatientID FROM STUDY WHERE StudyInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement)) {
			pstmt.setString(1, studyIUID);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					String patID = rs.getString("PatientID");
					conn.commit();
					return patID;
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public HashMap<String, String> findStudyRecordByStudyIUID(String studyIUID) {
		String statement = "SELECT * FROM STUDY WHERE StudyInstanceUID=?";
		HashMap<String, String> map = new HashMap<>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, studyIUID);// start from 1
			ResultSet rset = pstmt.executeQuery();
//			while(rset.next()) {
			if (rset.next()) {// return first data only.
				map.put("StudyInstanceUID", studyIUID);
				map.put("StudyDate", rset.getString("StudyDate"));
				map.put("StudyTime", rset.getString("StudyTime"));
				map.put("AccessionNumber", rset.getString("AccessionNumber"));
				map.put("ReferringPhysicianName", rset.getString("ReferringPhysicianName"));
				map.put("StudyDescription", rset.getString("StudyDescription"));
				map.put("StudyID", rset.getString("StudyID"));
			}
			rset.close();
			conn.commit();
			if (map.size() == 0) {
				return null;
			} else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public ArrayList<String> getActiveModalities() {
		ArrayList<String> modalities = new ArrayList<String>();
		String stat = "SELECT SHORTNAME FROM MODALITY WHERE STATUS=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(stat);) {
			pstmt.setBoolean(1, true);
			ResultSet resultSet = pstmt.executeQuery();
			while (resultSet.next()) {
				modalities.add(resultSet.getString("shortname"));
			}
			resultSet.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return modalities;
	}

	public String getActiveTheme() {
		String stat = "SELECT NAME FROM THEME WHERE STATUS=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(stat);) {
			pstmt.setBoolean(1, true);
			ResultSet resultSet = pstmt.executeQuery();
			if (resultSet.next()) {
				conn.commit();
				return resultSet.getString("name");
			}
			conn.commit();
			resultSet.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	/**
	 * for QR in instance level.
	 * 
	 * @param patID
	 * @param studyIUID
	 * @param seriesIUID
	 * @param sopIUIDs
	 * @return
	 */
	public ArrayList<HashMap<String, String>> getAllCandidate4InstanceQuery(String patID, String studyIUID,
			String seriesIUID, String[] sopIUIDs) {
		ArrayList<HashMap<String, String>> instCandidate = new ArrayList<>();
		if (sopIUIDs != null && sopIUIDs.length != 0) {
			/* search by sopIUID && othersInstanceRelatedInfo */
			for (int i = 0; i < sopIUIDs.length; i++) {
				HashMap<String, String> instInfo = getInstanceQueryInfo(patID, studyIUID, seriesIUID, sopIUIDs[i]);
				if (instInfo != null) {
					instCandidate.add(instInfo);
				}
			}
		}
		if (instCandidate != null && instCandidate.size() != 0) {
			return instCandidate;
		}
		return null;
	}

	// see, ***QueryTaskUsingDB
	public ArrayList<HashMap<String, String>> getAllCandidate4PatientQuery(String[] patIDs) {
		ArrayList<HashMap<String, String>> patCandidate = new ArrayList<>();
		if (patIDs != null && patIDs.length != 0) {
			/* search pid && othersPatientRelatedInfo */
			for (int i = 0; i < patIDs.length; i++) {
				HashMap<String, String> patInfo = getPatientInfo(patIDs[i]);
				if (patInfo != null) {
					patCandidate.add(patInfo);
				}
			}
		}
		if (patCandidate != null && patCandidate.size() != 0) {
			return patCandidate;
		}
		return null;
	}

	public ArrayList<HashMap<String, String>> getAllCandidate4SeriesQuery(String patID, String studyIUID,
			String[] seriesIUIDs) {
		ArrayList<HashMap<String, String>> seriesCandidate = new ArrayList<>();
		if (seriesIUIDs != null && seriesIUIDs.length != 0) {
			/* search by seriesIUID && othersSeriesRelatedInfo */
			for (int i = 0; i < seriesIUIDs.length; i++) {
				HashMap<String, String> seriesInfo = findSeriesRecordWithSeriesIUIDAnd(studyIUID, seriesIUIDs[i]);
				if (seriesInfo != null) {
					seriesCandidate.add(seriesInfo);
				}
			}
		}
		if (seriesCandidate != null && seriesCandidate.size() != 0) {
			return seriesCandidate;
		}
		return null;
	}

	public ArrayList<HashMap<String, String>> getAllCandidate4StudyQuery(String patID, String[] studyIUIDs) {
		ArrayList<HashMap<String, String>> studyCandidate = new ArrayList<>();
		if (studyIUIDs != null && studyIUIDs.length != 0) {
			/* search by studyIUID && othersStudyRelatedInfo */
			for (int i = 0; i < studyIUIDs.length; i++) {
				HashMap<String, String> studyInfo = findStudyRecordByStudyIUID(studyIUIDs[i]);
				if (studyInfo != null) {
					studyCandidate.add(studyInfo);
				}
			}
		}
		if (studyCandidate != null && studyCandidate.size() != 0) {
			return studyCandidate;
		}
		return null;
	}

	public ArrayList<String> getAllInstanceUIDsFromSTUDY(String studyUid) {
		ArrayList<String> instanceUIDs = new ArrayList<>();
		String statement1 = "SELECT SeriesInstanceUID FROM SERIES WHERE StudyInstanceUID=?";
		String statement2 = "SELECT SOPInstanceUID FROM IMAGE WHERE StudyInstanceUID=? AND SeriesInstanceUID=? Order by InstanceNumber, FileStoreUrl";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(statement1);) {
			ps.setString(1, studyUid);
			try (ResultSet seriesInfo = ps.executeQuery();) {
				while (seriesInfo.next()) { // Series Iteration
					try (PreparedStatement ps2 = conn.prepareStatement(statement2);) {
						ps2.setString(1, studyUid);
						ps2.setString(2, seriesInfo.getString("SeriesInstanceUID"));
						try (ResultSet imageLocations = ps2.executeQuery();) {
							while (imageLocations.next()) {
								instanceUIDs.add(imageLocations.getString("SOPInstanceUID"));
							}
						}
					} catch (SQLException ex) {
						logger.severe(ex.getMessage());
					}
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return instanceUIDs;
	}

	public ArrayList<HashMap<String, Object>> getCommunicationServerList() {
		ArrayList<HashMap<String, Object>> serverMaterialsList = new ArrayList<HashMap<String, Object>>();
		String sql = "SELECT * FROM SERVERS";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			try (ResultSet serverInfo = pstmt.executeQuery()) {
				while (serverInfo.next()) {
					HashMap<String, Object> nodeMaterials = new HashMap<>();
					String logicalname = serverInfo.getString("logicalname") != null
							? serverInfo.getString("logicalname")
							: "";
					String aetitle = serverInfo.getString("aetitle") != null ? serverInfo.getString("aetitle") : "";
					String hostname = serverInfo.getString("hostname") != null ? serverInfo.getString("hostname") : "";
					int portVal = serverInfo.getInt("port");
					Object port = serverInfo.wasNull() ? null : portVal; // fix: Integer.valueOf(int) != null is always true
					String ciphers = serverInfo.getString("ciphers") != null ? serverInfo.getString("ciphers") : "";
					String retrievetype = serverInfo.getString("retrievetype") != null
							? serverInfo.getString("retrievetype")
							: "";
					String wadocontext = serverInfo.getString("wadocontext") != null
							? serverInfo.getString("wadocontext")
							: "";
					int wadoPortVal = serverInfo.getInt("wadoport");
					Object wadoport = serverInfo.wasNull() ? null : wadoPortVal; // fix: Integer.valueOf(int) != null is always true
					String wadoprotocol = serverInfo.getString("wadoprotocol") != null
							? serverInfo.getString("wadoprotocol")
							: "";
					String retTS = serverInfo.getString("retrievets") != null ? serverInfo.getString("retrievets") : "";
					nodeMaterials.put("logicalname", logicalname);
					nodeMaterials.put("aetitle", aetitle);
					nodeMaterials.put("hostname", hostname);
					nodeMaterials.put("port", port);
					nodeMaterials.put("ciphers", ciphers);
					nodeMaterials.put("retrievetype", retrievetype);
					nodeMaterials.put("wadocontext", wadocontext);
					nodeMaterials.put("wadoport", wadoport);
					nodeMaterials.put("wadoprotocol", wadoprotocol);
					nodeMaterials.put("retrievets", retTS);
					serverMaterialsList.add(nodeMaterials);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return serverMaterialsList;
	}

	/**
	 * 
	 * @param nickname
	 * @return server primary key, if not found return -1.
	 */
	public int getCommunicationServerPk(String nickname) {
		String sql = "SELECT * FROM SERVERS WHERE logicalname=?";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql);) {
			ps.setString(1, nickname);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					int pk = rs.getInt("pk");
					conn.commit();
					return pk;
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return -1;
	}

	/**
	 * localeテーブルから重複を除いた国のリストを取得します。 結果はアルファベット順にソートされます。
	 *
	 * @return 国名の配列。該当する国がない場合は空の配列を返します。
	 * @throws SQLException データベースアクセスエラーが発生した場合
	 */
	public String[] getCountryList() {
		// 1. クエリを1回にまとめる。ORDER BYで結果をソートすると、より使いやすくなる。
		String sql = "SELECT DISTINCT country FROM locale ORDER BY country";
		List<String> countryList = new ArrayList<>();
		try (Connection conn = openConnection();
				PreparedStatement pstmt = conn.prepareStatement(sql);
				ResultSet rs = pstmt.executeQuery()) {
			while (rs.next()) {
				countryList.add(rs.getString("country"));
			}
			conn.commit();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "国リストの取得中にエラーが発生しました。", e);
		}
		return countryList.toArray(new String[0]);
	}

	/**
	 * 
	 * @param withDatabaseNameFolder
	 * @return ../to dbdir/[graphydb]
	 */
	public String getDatabaseFolderPath(boolean withDatabaseNameFolder) {
		if (withDatabaseNameFolder) {
			return this.dbdir + File.separator + databasename;
		} else {
			return this.dbdir;
		}
	}

	public Locale getCurrentLocale() {
		// language, country
		String statement = "SELECT * FROM LOCALE WHERE STATUS=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setBoolean(1, true);// start from 1
			try (ResultSet resultSet = pstmt.executeQuery();) {
				if (resultSet.next()) {
					String langCode = resultSet.getString("languagecode");
					String countryCode = resultSet.getString("countrycode");
//					String language = resultSet.getString("language");
//					String country = resultSet.getString("country");
//					String localID = resultSet.getString("localeid");
					conn.commit();
					return new Locale(langCode, countryCode);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public String getDatabaseName() {
		return databasename;
	}

	private Set<String> getDBTable(Connection targetDBConn) throws SQLException {
		Set<String> set = new HashSet<String>();
		DatabaseMetaData dbmeta = targetDBConn.getMetaData();
		readDBTable(set, dbmeta, "TABLE", null);
		return set;
	}

	public String getDerbyDriverName() {
		return driverName;
	}

	/**
	 * 
	 * @return jdbc password
	 */
	public String getDerbyPassword() {
		return password;
	}

	/**
	 * @return jdbc protocol name
	 */
	public String getDerbyProtocolName() {
		return protocol;
	}

	public String getDerbyUserName() {
		return username;
	}

	public EmbeddedDataSource getEmbeddedDataSource() {
		return derby;
	}

	public String getFileLocation(String studyUID, String seriesUID, String sopUID) {
		String sql = "SELECT FileStoreUrl FROM IMAGE WHERE StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, studyUID);
			pstmt.setString(2, seriesUID);
			pstmt.setString(3, sopUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					conn.commit();
					return rset.getString("FileStoreUrl");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	/**
	 * return all instances file locations by specified patient id.
	 * 
	 * @param patID
	 * @return
	 */
	public ArrayList<String> getFileLocationsPatientLevel(String patID) {
		if (patID == null) {
			throw new NullPointerException("DB:getFileLocationsPatientLevel:: patId must be non-null.");
		}
		String sql = "SELECT FileStoreUrl FROM IMAGE WHERE PatientID=?";
		List<String> locs = new ArrayList<String>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, patID);// start from 1
			try (ResultSet rset = pstmt.executeQuery();) {
				while (rset.next()) {
					locs.add(rset.getString("FileStoreUrl"));
				}
			}
			conn.commit();
			if (locs.size() == 0) {
				return null;
			} else {
				// fail safe ?
				return new ArrayList<String>(new HashSet<>(locs));
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public ArrayList<String> getFileLocationsSeriesLevel(String studyUid, String seriesUid) {
		if (studyUid == null || seriesUid == null) {
			throw new NullPointerException("DB:getFileLocationsSeriesLevel:: studyUid or seriesUid must be non-null.");
		}
		String sql = "SELECT FileStoreUrl FROM IMAGE WHERE StudyInstanceUID=? AND SeriesInstanceUID=? Order by InstanceNumber asc";
		List<String> locs = new ArrayList<String>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, studyUid);// start from 1
			pstmt.setString(2, seriesUid);
			try (ResultSet rset = pstmt.executeQuery();) {
				while (rset.next()) {
					locs.add(rset.getString("FileStoreUrl"));
				}
			}
			conn.commit();
			if (locs.size() == 0) {
				return null;
			} else {
				return new ArrayList<String>(new HashSet<>(locs));
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public ArrayList<String> getFileLocationsStudyLevel(String studyUid) {
		if (studyUid == null) {
			throw new NullPointerException("studyUid must be non-null.");
		}
		String sql = "SELECT FileStoreUrl FROM IMAGE WHERE StudyInstanceUID=? Order by InstanceNumber asc";
		List<String> locs = new ArrayList<String>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, studyUid);// start from 1
			try (ResultSet rset = pstmt.executeQuery();) {
				while (rset.next()) {
					locs.add(rset.getString("FileStoreUrl"));
				}
			}
			conn.commit();
			if (locs.size() == 0) {
				return null;
			} else {
				return new ArrayList<String>(new HashSet<>(locs));
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public String getFirstInstanceLocation(String studyUid, String seriesUid) {
		if (studyUid == null || seriesUid == null) {
			throw new NullPointerException("studyUid or seriesUid must be non-null.");
		}
		ArrayList<String> list = getFileLocationsSeriesLevel(studyUid, seriesUid);
		if (list != null) {
			return list.get(0);
		}
		return null;
	}

	public ArrayList<HashMap<String, String>> getImageInstanceInfo(String pid, String studyIUID, String seriesIUID,
			String sopIUID) {
		String statement = "SELECT * FROM IMAGE WHERE";
		HashMap<Integer, String> keymap = new HashMap<Integer, String>();
		int pos = 1;
		if (pid != null) {
			statement = statement + " PatientID=?";
			keymap.put(pos, pid);
			pos++;
		}
		if (studyIUID != null) {
			if (pos == 1) {
				statement = statement + " StudyInstanceUID=?";
			} else {
				statement = statement + " AND StudyInstanceUID=?";
			}
			keymap.put(pos, studyIUID);
			pos++;
		}
		if (seriesIUID != null) {
			if (pos == 1) {
				statement = statement + " SeriesInstanceUID=?";
			} else {
				statement = statement + " AND SeriesInstanceUID=?";
			}
			keymap.put(pos, seriesIUID);
			pos++;
		}
		if (sopIUID != null) {
			if (pos == 1) {
				statement = statement + " SOPInstanceUID=?";
			} else {
				statement = statement + " AND SOPInstanceUID=?";
			}
			keymap.put(pos, sopIUID); // Bug fix: was incorrectly binding studyIUID here
			pos++;
		}

		// get result
		ArrayList<HashMap<String, String>> result = new ArrayList<>();
		HashMap<String, String> map = null;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			for (int keypos : keymap.keySet()) {
				pstmt.setString(keypos, keymap.get(keypos));
			}
			try (ResultSet rset = pstmt.executeQuery()) {
				rset.setFetchSize(10000);// limitation
				while (rset.next()) {
//					String cuid = instRec.getString(Tag.ReferencedSOPClassUIDInFile);
//					String iuid = instRec.getString(Tag.ReferencedSOPInstanceUIDInFile);
//					String tsuid = instRec.getString(Tag.ReferencedTransferSyntaxUIDInFile);
//					String uri = ddr.toFile(fileIDs).toURI().toString();
					map = new HashMap<String, String>();
					map.put("URI", new File(rset.getString("FileStoreUrl")).toURI().toString());
					map.put("SOPInstanceUID", rset.getString("SOPInstanceUID"));
					map.put("SOPClassUID", rset.getString("SOPClassUID"));
					map.put("TransferSyntaxUID", rset.getString("TransferSyntaxUID"));
					result.add(map);
				}
			}
			conn.commit();
			if (result.size() != 0) {
				return result;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public List<HashMap<String, String>> getImagesInfoByUIDs(String patID, String studyUID, String seriesUID) {
		int numOfInstanceInSeries = getNumOfInstanceInSeries(patID, studyUID, seriesUID);
		if (numOfInstanceInSeries < 1) {
			return null;
		}
		String sql = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber";
		List<HashMap<String, String>> imageInfoList = new ArrayList<HashMap<String, String>>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, patID);// start from 1
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				while (rset.next()) {
					HashMap<String, String> map = new HashMap<>();
					map.put("PatientID", patID);
					map.put("AcquisitionDateTime", rset.getString("AcquisitionDateTime"));
					map.put("AcquisitionNumber", rset.getString("AcquisitionNumber"));
					map.put("InstanceNumber", rset.getString("InstanceNumber"));
					// ?NumOfInstanceInSeries? -> should check...
					map.put("NumOfInstanceInSeries", String.valueOf(numOfInstanceInSeries));
					map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
					map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
					map.put("SOPInstanceUID", rset.getString("SOPInstanceUID"));
					imageInfoList.add(map);
				}
			}
			conn.commit();
			if (imageInfoList.size() == 0) {
				return null;
			} else {
				return imageInfoList;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public HashMap<String, String> getInfoset(String patID, String studyUID, String seriesUID) {
		HashMap<String, String> infoset = new HashMap<String, String>();
		infoset.put("PatientID", patID);
		infoset.put("PatientName", getValueFromPatient("PatientName", patID));
		String bod = getValueFromPatient("PatientBirthDate", patID);
		infoset.put("PatientBirthDate", bod);
		infoset.put("PatientSex", getValueFromPatient("PatientSex", patID));
		// calc age when study performed.
		String studyDate = getValueFromStudy("StudyDate", patID, studyUID);
		infoset.put("StudyDate", studyDate);
		Integer age = Utils.calculateAge(bod, studyDate);
		infoset.put("PatientAge", age == null ? null : String.valueOf(age));

		ArrayList<String> seriesUids = getSeriesUidList(patID, studyUID);
		HashSet<String> modalities = new HashSet<>();
		String modalitiesString = "";
		for (String seUid : seriesUids) {
			String m = getValueFromSeries("Modality", patID, studyUID, seUid);
			if (m != null) {
				modalities.add(m);
			}
		}
		for (Object m_ : modalities.toArray()) {
			modalitiesString += (String) m_ + ",";
		}
		// remove last comma (guard against empty modalities causing StringIndexOutOfBoundsException)
		if (!modalitiesString.isEmpty()) {
			modalitiesString = modalitiesString.substring(0, modalitiesString.length() - 1);
		}

//		infoset.put("Modality", getParticularInfoFromStudy("Modality", patID, studyUID));//, seriesUID));
		infoset.put("Modality", modalitiesString);
		return infoset;
	}

	/* to use when starting graphy */
	public static DatabaseHandler getInstance() {
		return datbaseRef;
	}

	public int getInstanceNo(String studyUid, String seriesUid, String sopUid) {
		String sql = "SELECT * FROM IMAGE WHERE StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, studyUid);
			pstmt.setString(2, seriesUid);
			pstmt.setString(3, sopUid);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					conn.commit();
					return rset.getInt("InstanceNumber");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return -1;
	}

	// see, DcmQRSCP
	public String getInstancePathUsingSOPInstanceUID(String sopIUID) {
		String sql = "SELECT * FROM IMAGE WHERE SOPInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, sopIUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					conn.commit();
					return rset.getString("FileStoreUrl");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	/**
	 * 
	 * @param patID
	 * @param studyIUID
	 * @param seriesIUID
	 * @param sopIUID
	 * @return
	 */
	public HashMap<String, String> getInstanceQueryInfo(String patID, String studyIUID, String seriesIUID,
			String sopIUID) {
		/* check item in keys */

		String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		HashMap<String, String> map = new HashMap<>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, seriesIUID);
			pstmt.setString(4, sopIUID);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					/*
					 * (0004,1500) CS [DICOM\6EFD8DF8\FF3A35F6\4C11115A] ReferencedFileID
					 * (0004,1510) UI [1.2.840.10008.5.1.4.1.1.4] ReferencedSOPClassUIDInFile//same
					 * as SOPClassUID (0004,1511) UI
					 * [1.3.6.1.4.1.14519.5.2.1.3344.2526.3991481572793857949648742095//same as SOP
					 * Instance UID // (0004,1512) UI
					 * [1.2.840.10008.1.2]ReferencedTransferSyntaxUIDInFile//same as
					 * TransferSyntaxUID (0020,0013) IS [6] InstanceNumber//mandatory for directory
					 * record
					 */
					map.put("ReferencedFileID", DicomUtilities.convertAbsPath2ReferencedFileID(
							rset.getString("FileStoreUrl"), rset.getBoolean("isLink")));
					map.put("SOPInstanceUID", sopIUID);
					map.put("SOPClassUID", rset.getString("SOPClassUID"));
					map.put("TransferSyntaxUID", rset.getString("TransferSyntaxUID"));
					map.put("InstanceNumber", rset.getString("InstanceNumber"));
				}
			}
			conn.commit();
			if (map.size() != 0) {
				return map; // Bug fix: was returning null when data found, and map when empty
			} else {
				return null;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public List<PresetModel> getPresetsForModality(Modality modality) {
		String sql1 = "select pk from modality where shortname=?";
		String sql2 = "select * from presets where modality_fk=?";
		List<PresetModel> presets = new ArrayList<PresetModel>();
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql1);) {
			ps.setString(1, modality.name());
			try (ResultSet modalityInfo = ps.executeQuery();) {
				if (modalityInfo.next()) {
					try (PreparedStatement ps2 = conn.prepareStatement(sql2);) {
						ps2.setString(1, String.valueOf(modalityInfo.getInt("pk")));
						try (ResultSet presetInfo = ps2.executeQuery();) {
							while (presetInfo.next()) {
								// String presetName, double ww, double wl, String lut, Modality m
								PresetModel preset = new PresetModel(presetInfo.getString("presetname"),
										presetInfo.getDouble("windowwidth"), presetInfo.getDouble("windowlevel"),
										presetInfo.getString("lut"), modality);
								presets.add(preset);
							}
						}
					}
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return presets;
	}

	public ArrayList<String> getInstanceUidList(String patID, String studyUid, String seriesUid) {
		if (patID == null || studyUid == null || seriesUid == null) {
			throw new NullPointerException("DB:getInstanceUidList:: contains NULL ID ... return.");
		}
		String sql = "SELECT SOPInstanceUID from IMAGE WHERE PatientID=? and StudyInstanceUID=? and SeriesInstanceUID=? Order by InstanceNumber, FileStoreUrl";
		ArrayList<String> sopUids = new ArrayList<String>();
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, patID);
			ps.setString(2, studyUid);
			ps.setString(3, seriesUid);
			try (ResultSet imageLocations = ps.executeQuery()) {
				while (imageLocations.next()) {
					sopUids.add(imageLocations.getString("SOPInstanceUID"));
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return sopUids;
	}

	public String getJNLPRetrieveType() {

		String sql = "select JNLPRetrieveType from miscellaneous";
		try (Connection conn = openConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet retrieveInfo = ps.executeQuery();) {
			if (retrieveInfo.next()) {
				String res = retrieveInfo.getString("JNLPRetrieveType");
				conn.commit();
				return res;
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	// When starting up, use prop file.
	public String[] getListenerDetails() {
		String statement = "SELECT * FROM LISTENER";
		String[] detail = null;
		try (Connection conn = openConnection();
				PreparedStatement ps = conn.prepareStatement(statement);
				ResultSet listenerInfo = ps.executeQuery();) {
			if (listenerInfo.next()) {
				detail = new String[4];
				detail[0] = listenerInfo.getString("aetitle");
				detail[1] = listenerInfo.getString("host");
				detail[2] = listenerInfo.getString("port");
				detail[3] = listenerInfo.getString("storagelocation");
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		if (detail == null) {
			// set default ?
		}
		return detail;
	}

	/**
	 * The DB location (derby and dcmqrscp) can be changed as desired.
	 * 
	 * @return
	 */
	public String getLocalDBLocation() throws Exception {
		if (dbdir == null || dbdir.isBlank()) {
			try {
				loadLocalDBLocation();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}
		return dbdir;
	}

	public boolean getLoopbackStatus() throws SQLException {
		String sql = "select Loopback from miscellaneous";
		try (Connection conn = openConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet loopBackStatus = ps.executeQuery();) {
			if (loopBackStatus.next()) {
				boolean res = loopBackStatus.getBoolean("Loopback");
				conn.commit();
				return res;
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			throw ex;
		}
		return saveAsLink;
	}

	public List<String> getModalitiesInStudyRealatedAllSeries(String patID, String studyUID) {
		if (getNumOfSeries(patID, studyUID) < 1) {
			return null;
		}
		String sql = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?";
		List<String> modalities = new ArrayList<String>();
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql);) {
			ps.setString(1, patID);// start from 1
			ps.setString(2, studyUID);
			try (ResultSet rset = ps.executeQuery();) {
				while (rset.next()) {
					modalities.add(rset.getString("Modality"));
				}
				conn.commit();
				if (modalities.size() == 0 /* isEmpty() DO NOT USE */) {
					return null;
				} else {
					List<String> noduplicate = new ArrayList<String>(new HashSet<>(modalities));
					return noduplicate;
				}
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public int getNumOfInstanceInSeries(String patID, String studyUID, String seriesUID) {
		int size = 0;
		String statement = "SELECT COUNT(SOPInstanceUID) FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		try (Connection conn = openConnection();) {
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			ResultSet rset = pstmt.executeQuery();
			if (rset.next()) {
				size = rset.getInt(1);
			}
			rset.close();
			pstmt.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return size;
	}

	public int getNumOfInstancePatient(String patID) {
		String statement = "SELECT COUNT(SOPInstanceUID) FROM IMAGE WHERE PatientID=?";
		int count = 0;
		try (Connection conn = openConnection();) {
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			ResultSet rset = pstmt.executeQuery();
			if (rset.next()) {
				count = rset.getInt(1);
			}
			rset.close();
			pstmt.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return count;
	}

	public int getNumOfInstancesInDB() {
		try (Connection conn = openConnection();) {
			ResultSet totalInfo = conn.createStatement().executeQuery("SELECT COUNT(SOPInstanceUID) FROM IMAGE");
			if (totalInfo.next()) {
				return totalInfo.getInt(1);
			}
			totalInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return 0;
	}

	/**
	 * 
	 * @param studyUid
	 * @return
	 */
	public int getNumOfInstancesInStudy(String studyUid) {
		int cnt = 0;
		String sql = "SELECT COUNT(*) FROM image WHERE StudyInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, studyUid);
			try (ResultSet totalInstancesInfo = ps.executeQuery()) {
				if (totalInstancesInfo.next()) {
					cnt = totalInstancesInfo.getInt(1);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return cnt;
	}

	/**
	 * By design, the StudyInstanceUID is guaranteed to be unique at the study
	 * level. Counting solely based on this UID is perfectly acceptable. However,
	 * using it in combination with the PatientID can be considered a more stringent
	 * approach. Either method is acceptable.
	 * 
	 * @param patID
	 * @param studyUID
	 * @return
	 */
	public int getNumOfInstanceStudy(String patID, String studyUID) {
		String statement = "SELECT COUNT(SOPInstanceUID) FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=?";
		int count = 0;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					count = rset.getInt(1);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return count;
	}

	public int getNumOfPatientsInDB() {
		int cnt = 0;
		try (Connection conn = openConnection();) {
			ResultSet totalInfo = conn.createStatement().executeQuery("SELECT COUNT(*) FROM PATIENT");
			if (totalInfo.next()) {
				cnt = totalInfo.getInt(1);
			}
			totalInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return cnt;
	}

	public int getNumOfSeries(String patID, String studyUID) {
		String statement = "SELECT COUNT(SeriesInstanceUID) FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?";
		int count = 0;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					count = rset.getInt(1);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return count;
	}

	public int getNumOfSeriesInDB() {
		int cnt = 0;
		try (Connection conn = openConnection();) {
			ResultSet totalInfo = conn.createStatement().executeQuery("SELECT COUNT(*) FROM SERIES");
			if (totalInfo.next()) {
				cnt = totalInfo.getInt(1);
			}
			totalInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return cnt;
	}

	public int getNumOfSeriesInStudy(String studyUid) {
		int cnt = 0;
		String sql = "SELECT COUNT(*) FROM SERIES WHERE StudyInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, studyUid);
			try (ResultSet totalInstancesInfo = ps.executeQuery()) {
				if (totalInstancesInfo.next()) {
					cnt = totalInstancesInfo.getInt(1);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return cnt;
	}

	public int getNumOfStudiesInDB() {
		int cnt = 0;
		try (Connection conn = openConnection();) {
			ResultSet totalStudiesInfo = conn.createStatement().executeQuery("SELECT COUNT(*) FROM STUDY");
			if (totalStudiesInfo.next()) {
				cnt = totalStudiesInfo.getInt(1);
			}
			totalStudiesInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return cnt;
	}

	/**
	 * @deprecated Has SQL injection risk. Use {@link #getNumOfStudyByPatient(String)} instead.
	 */
	@Deprecated
	public int getNumOfStudyInPatient(String patID) {
		return getNumOfStudyByPatient(patID);
	}

	public int getNumOfStudyByPatient(String patID) {
		String statement = "SELECT COUNT(StudyInstanceUID) FROM STUDY WHERE PatientID=?";
		int count = 0;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			try (ResultSet studyCount = pstmt.executeQuery()) {
				if (studyCount.next()) {
					count = studyCount.getInt(1);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return count;
	}

	public int getPrimaryKeyIndexInTable(String tableName, String whereField, String whereValue) {
		String sql = "select pk" + " from " + tableName + " where " + whereField + "=?";
		int pk = -1;
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, whereValue);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					pk = rs.getInt("pk");
					conn.commit();
					return pk;
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return -1;
	}

	/**
	 * not tested
	 * 
	 * @param tableName
	 * @param columnLabel
	 * @param whereField
	 * @param whereValue
	 * @return
	 */
	public Object getValue(String tableName, String columnLabel/* fieldName */, String whereField, Object whereValue) {
		String sql = "select *" + " from " + tableName + " where " + whereField + "=?";
		Object v = null;
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setObject(1, whereValue);
			try (ResultSet rs = ps.executeQuery();) {
				if (rs.next()) {
					v = rs.getObject(columnLabel);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return v;
	}

	public String getValueFromImage(String targetColName, String patID, String studyUID, String seriesUID,
			String sopUID) {
		String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		String something = null;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			pstmt.setString(4, sopUID);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					something = rset.getString(targetColName);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return something;
	}

	public String getValueFromPatient(String targetColName, String patID) {
		String statement = "SELECT * FROM PATIENT WHERE PatientID=?";
		String result = null;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					result = rset.getString(targetColName);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return result;
	}

	public String getValueFromSeries(String targetColName, String patID, String studyUid, String seriesUid) {
		String statement = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		String something = null;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					something = rset.getString(targetColName);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return something;
	}

	public String getValueFromStudy(String targetColName, String patID, String studyUid) {
		String statement = "SELECT * FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?";
		String something = null;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUid);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					something = rset.getString(targetColName);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return something;
	}

	/*
	 * return following info (0010,0010) PN [LGG-203] PatientName (0010,0020) LO
	 * [LGG-203] PatientID (0010,0030) DA [] PatientBirthDate (0010,0040) CS [M]
	 * PatientSex
	 */
	public HashMap<String, String> getPatientInfo(String patID) {
		if (patID == null) {
			return null;
		}
		String sql = "SELECT * FROM PATIENT WHERE PatientID=?";
		HashMap<String, String> map = new HashMap<>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, patID);// start from 1
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					map.put("PatientID", patID);
					map.put("PatientName", rset.getString("PatientName"));
					map.put("PatientBirthDate", rset.getString("PatientBirthDate"));
					map.put("PatientSex", rset.getString("PatientSex"));
				}
				conn.commit();
				if (map.size() == 0) {
					return null;
				} else {
					return map;
				}
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public String getRetrieveMode(String serverName) {
		String sql = "select retrievetype from servers where logicalname=?";
		String retType = null;
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, serverName);
			try (ResultSet serverNameInfo = ps.executeQuery();) {
				if (serverNameInfo.next()) {
					retType = serverNameInfo.getString("retrievetype");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			return null;
		}
		return retType;
	}

	public List<HashMap<String, String>> getSeriesInfoByUIDs(String patID, String studyUID) {
		if (getNumOfSeries(patID, studyUID) < 1) {
			return null;
		}
		String sql = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? order by SeriesNumber";
		List<HashMap<String, String>> seriesInfoList = new ArrayList<HashMap<String, String>>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, patID);// start from 1
			pstmt.setString(2, studyUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				while (rset.next()) {
					HashMap<String, String> map = new HashMap<>();
					map.put("PatientID", patID);
					map.put("SeriesDate", rset.getString("SeriesDate"));
					map.put("SeriesDescription", rset.getString("SeriesDescription"));
					map.put("Modality", rset.getString("Modality"));
					map.put("InstitutionName", rset.getString("InstitutionName"));
					map.put("ModelName", rset.getString("ModelName"));
					map.put("SeriesNumber", rset.getString("SeriesNumber"));
					map.put("NumOfInstanceInSeries",
							String.valueOf(getNumOfInstanceInSeries(rset.getString("PatientID"),
									rset.getString("StudyInstanceUID"), rset.getString("SeriesInstanceUID"))));
					map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
					map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
					seriesInfoList.add(map);
				}
				conn.commit();
				if (seriesInfoList.size() == 0/* isEmpty()* DO NOT USE */) {
					return null;
				} else {
					return seriesInfoList;
				}
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public HashMap<String, String> getSeriesInfoByUIDs(String patID, String studyUID, String seriesUID) {
		if (getNumOfSeries(patID, studyUID) < 1) {
			return null;
		}
		String sql = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		HashMap<String, String> map = new HashMap<String, String>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, patID);// start from 1
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					map.put("PatientID", patID);
					map.put("SeriesDate", rset.getString("SeriesDate"));
					map.put("SeriesDescription", rset.getString("SeriesDescription"));
					map.put("Modality", rset.getString("Modality"));
					map.put("InstitutionName", rset.getString("InstitutionName"));
					map.put("ModelName", rset.getString("ModelName"));
					map.put("SeriesNumber", rset.getString("SeriesNumber"));
					map.put("NumOfInstanceInSeries",
							String.valueOf(getNumOfInstanceInSeries(rset.getString("PatientID"),
									rset.getString("StudyInstanceUID"), rset.getString("SeriesInstanceUID"))));
					map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
					map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
				}
				conn.commit();
				if (map.size() == 0) {
					return null;
				} else {
					return map;
				}
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public String getSeriesIUID(String patID, String studyIUID, String sopIUID) {
		String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SOPInstanceUID=?";
		String uid = null;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement)) {
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, sopIUID);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					uid = rset.getString("SeriesInstanceUID");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return uid;
	}

	public ArrayList<String> getSeriesUidList(String patID, String studyUID) {
		String sql = "select SeriesInstanceUID from SERIES where PatientID=? and StudyInstanceUID=?";
		ArrayList<String> seriesUids = new ArrayList<String>();
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, patID);
			ps.setString(2, studyUID);
			try (ResultSet rset = ps.executeQuery()) {
				while (rset.next()) {
					seriesUids.add(rset.getString("SeriesInstanceUID"));
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return seriesUids;
	}

	public HashMap<String, Object> getServerInfo(String nickname) {
		String sql = "select * from servers where logicalname=?";
		HashMap<String, Object> nodeMaterials = null;
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, nickname);
			try (ResultSet serverInfo = ps.executeQuery();) {
				if (serverInfo.next()) {
					nodeMaterials = new HashMap<>();
					String logicalname = serverInfo.getString("logicalname") != null
							? serverInfo.getString("logicalname")
							: "";
					String aetitle = serverInfo.getString("aetitle") != null ? serverInfo.getString("aetitle") : "";
					String hostname = serverInfo.getString("hostname") != null ? serverInfo.getString("hostname") : "";
					int portVal = serverInfo.getInt("port");
					Object port = serverInfo.wasNull() ? null : portVal; // fix: Integer.valueOf(int) != null is always true
					String ciphers = serverInfo.getString("ciphers") != null ? serverInfo.getString("ciphers") : "";
					String retrievetype = serverInfo.getString("retrievetype") != null
							? serverInfo.getString("retrievetype")
							: "";
					String wadocontext = serverInfo.getString("wadocontext") != null
							? serverInfo.getString("wadocontext")
							: "";
					int wadoPortVal = serverInfo.getInt("wadoport");
					Object wadoport = serverInfo.wasNull() ? null : wadoPortVal; // fix: Integer.valueOf(int) != null is always true
					String wadoprotocol = serverInfo.getString("wadoprotocol") != null
							? serverInfo.getString("wadoprotocol")
							: "";
					String retTS = serverInfo.getString("retrievets") != null ? serverInfo.getString("retrievets") : "";
					nodeMaterials.put("logicalname", logicalname);
					nodeMaterials.put("aetitle", aetitle);
					nodeMaterials.put("hostname", hostname);
					nodeMaterials.put("port", port);
					nodeMaterials.put("ciphers", ciphers);
					nodeMaterials.put("retrievetype", retrievetype);
					nodeMaterials.put("wadocontext", wadocontext);
					nodeMaterials.put("wadoport", wadoport);
					nodeMaterials.put("wadoprotocol", wadoprotocol);
					nodeMaterials.put("retrievets", retTS);
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			return null;
		}
		return nodeMaterials;// if no match, return null.
	}

	/*
	 * for study node builder
	 */
	public HashMap<String, String> getStudyInfo(String patID, String studyUID) {
		String sql = "SELECT * FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?";
		HashMap<String, String> map = new HashMap<>();
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			// study info
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					map.put("PatientID", patID);
					map.put("PatientAge", rset.getString("PatientAge"));// age is study level, not pat level.
					map.put("StudyDate", rset.getString("StudyDate"));
					map.put("StudyTime", rset.getString("StudyTime"));
					map.put("StudyID", rset.getString("StudyID"));
					map.put("StudyDescription", rset.getString("StudyDescription"));
					map.put("ModalitiesInStudy", rset.getString("ModalitiesInStudy"));
					map.put("AccessionNumber", rset.getString("AccessionNumber"));
					map.put("NumOfSeriesInStudy", String.valueOf(getNumOfSeriesInStudy(studyUID)));
					map.put("NumOfInstancesInStudy", String.valueOf(getNumOfInstancesInStudy(studyUID)));
					map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
				}
				conn.commit();
				if (map.size() == 0) {
					return null;
				} else {
					return map;
				}
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public String getStudyIUID(String patID, String seriesIUID, String sopIUID) {
		String sql = "SELECT * FROM IMAGE WHERE PatientID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		String studyUID = null;
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, patID);
			pstmt.setString(2, seriesIUID);
			pstmt.setString(3, sopIUID);
			try (ResultSet rset = pstmt.executeQuery();) {
				if (rset.next()) {
					studyUID = rset.getString("StudyInstanceUID");
				}
				conn.commit();
				return studyUID;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return null;
	}

	public ArrayList<String> getStudyUidList(String patID) {
		String sql = "select StudyInstanceUID from STUDY where PatientID=?";
		ArrayList<String> studyUids = new ArrayList<String>();
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, patID);
			try (ResultSet rset = ps.executeQuery();) {
				while (rset.next()) {
					studyUids.add(rset.getString("StudyInstanceUID"));
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return studyUids;
	}

	public ArrayList<Integer> getTextAnnotationList() {
		String sql = "SELECT * FROM textannotation";
		ArrayList<Integer> tagList = new ArrayList<>();
		try (Connection conn = openConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql);) {
			while (rs.next()) {
				tagList.add(rs.getInt("tag"));
			}
			conn.commit();
		} catch (SQLException e) {
			logger.severe(e.getMessage());
		}
		return tagList;
	}

	public ArrayList<String> getThemes() {
		String sql = "select name from theme";
		ArrayList<String> themeNames = new ArrayList<String>();
		try (Connection conn = openConnection();
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql);) {
			while (rs.next()) {
				if (!rs.getString("name").equals("System")) {
					themeNames.add(rs.getString("name"));
				} else {
					themeNames.add(System.getProperty("os.name"));
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return themeNames;
	}

	public List<String[]> getUIDsByFileLocations(ArrayList<String> fileLocs) {
		if (fileLocs == null) {
			return null;
		}
		String sql = "SELECT * FROM IMAGE WHERE FileStoreUrl=?";
		List<String[]> idsetList = new ArrayList<String[]>();
		for (int i = 0; i < fileLocs.size(); i++) {
			try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
				pstmt.setString(1, fileLocs.get(i));
				try (ResultSet rset = pstmt.executeQuery()) {
					if (rset.next()) {
						String idset[] = new String[4];
						idset[0] = rset.getString("PatientID");
						idset[1] = rset.getString("StudyInstanceUID");
						idset[2] = rset.getString("SeriesInstanceUID");
						idset[3] = rset.getString("SOPInstanceUID");
						idsetList.add(idset);
					}
				}
				conn.commit();
			} catch (SQLException ex) {
				logger.severe(ex.getMessage());
			}
		}
		return idsetList;
	}

	private void insertDefaultListenerDetails() {
		String sql = "insert into listener(aetitle,host,port,storagelocation) values( ? , ? , ? , ?)";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
			pstmt.setString(1, defaultAET);
			pstmt.setString(2, defaultHost);
			pstmt.setString(3, defaultPort);
			pstmt.setString(4, getLocalDBLocation());
			pstmt.executeUpdate();
			conn.commit();
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.SEVERE, e.getMessage());
		}
	}

	/**
	 * countrycode 'JP' ISO 3166-1 alpha-2 で定義される国コード。 country 'Japan' または '日本' 国名。
	 * languagecode 'ja' ISO 639-1 で定義される言語コード。 language 'Japanese' または '日本語' 言語名。
	 * localeid 'ja_JP' 言語コードと国コードを組み合わせたロケールID。 status true このロケールが有効であることを示します。
	 */
	private void insertDefaultLocales() {
		// String sql = "insert into locale
		// (countrycode,country,languagecode,language,localeid,status)
		// values('JP','Japan','ja','Japanese','ja_JP',true)";
		try {
			addNewLocale("ja_JP");
			addNewLocale("en_GB");
			addNewLocale("ta_IN");
			addNewLocale("it_IT");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void insertDefaultPresets() {

		PresetModel p1 = new PresetModel("CT Abdomen", 350, 40, null, Modality.CT);
		PresetModel p2 = new PresetModel("CT Lung", 1500, -600, null, Modality.CT);
		PresetModel p3 = new PresetModel("CT bone", 2500, 400, null, Modality.CT);
		PresetModel p4 = new PresetModel("CT Brain", 80, 40, null, Modality.CT);
		PresetModel[] presets = new PresetModel[] { p1, p2, p3, p4 };
		for (PresetModel p : presets) {
			insertPreset(p);
		}
	}

	private void insertDefaultTextAnnotationList() throws SQLException {
		try (Connection conn = openConnection();) {
			// initial annotations
			ArrayList<Integer> tags = new ArrayList<>();
			tags.add(Tag.Patient​ID);
			tags.add(Tag.Patient​Name);
			tags.add(Tag.Patient​Birth​Date);
			tags.add(Tag.Patient​Age);
			tags.add(Tag.Patient​Sex);
			tags.add(Tag.Institution​Name);
			tags.add(Tag.Study​Date);
			tags.add(Tag.Study​Time);
			tags.add(Tag.Series​Description);
			tags.add(Tag.Instance​Number);
			tags.add(Tag.Series​Number);
			tags.add(Tag.Slice​Location);
			tags.add(Tag.Slice​Thickness);
			tags.add(Tag.Field​Of​View​Dimensions);
			tags.add(Tag.Manufacturer​Model​Name);
			tags.add(Tag.Rows);
			tags.add(Tag.Columns);
			String sql = "insert into textannotation(tag) values(?)";
			for (Integer tag : tags) {
//				String sql = "insert into textannotation(tag) values(";
//				sql = sql + String.valueOf(tag) + ")";
//				conn.createStatement().execute(sql);
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					ps.setString(1, String.valueOf(tag));
					ps.executeUpdate();
					conn.commit();
				}
			}
		}
	}

	private void insertImageInfo(DicomObject dataset, String filePath, String patientID, String studyUid,
			String seriesUid, boolean saveAsLink) throws Exception {

		boolean multiframe = false;
		int totalFrame = 0;
		boolean encapsulatedPDF = false;

		if (dataset.getString(Tag.SOP​Class​UID) != null
				&& dataset.getString(Tag.SOP​Class​UID).equals(UID.EncapsulatedPDFStorage.uid())) {
			encapsulatedPDF = true;
		}

		if (dataset.getString(Tag.Number​Of​Frames) != null
				&& Integer.parseInt(dataset.getString(Tag.Number​Of​Frames)) > 1) {
			multiframe = true;
			totalFrame = dataset.getInt(Tag.Number​Of​Frames, -1);
		}
		String acquisitionNo = dataset.getString(Tag.Acquisition​Number) != null
				? dataset.getString(Tag.Acquisition​Number)
				: "";
		java.util.Date acqDateTime = dataset.getDate(Tag.Acquisition​Date​Time);
		java.sql.Time sqlAcqDateTime = DateUtils.toSQLTime(acqDateTime);

		String frameOfRefUid = dataset.getString(Tag.Frame​Of​Reference​UID) != null
				? dataset.getString(Tag.Frame​Of​Reference​UID)
				: "";
		String imgPos = dataset.getBytes(Tag.Image​Position) != null ? new String(dataset.getBytes(Tag.Image​Position))
				: "";
		String imgOrientation = dataset.getBytes(Tag.Image​Orientation) != null
				? new String(dataset.getBytes(Tag.Image​Orientation))
				: "";
		String pixelSpacing = dataset.getBytes(Tag.Pixel​Spacing) != null
				? new String(dataset.getBytes(Tag.Pixel​Spacing))
				: "";
		int row = dataset.getInt(Tag.Rows, 0) != 0 ? dataset.getInt(Tag.Rows, 0) : 1;
		int columns = dataset.getInt(Tag.Columns, 0) != 0 ? dataset.getInt(Tag.Columns, 0) : 1;
		String referSopInsUid = "", image_type = "";
		String sliceThickness = dataset.getBytes(Tag.Spacing​Between​Slices) != null
				? new String(dataset.getBytes(Tag.Spacing​Between​Slices))
				: "";
		// To get the Referenced SOP Instance UID
		DicomObject refImageSeq = dataset.getNestedDataset(Tag.Referenced​Image​Sequence);
		if (refImageSeq != null) {
			referSopInsUid = refImageSeq.getString(Tag.Referenced​SOP​Instance​UID);
		}
		// To get the Image Type (LOCALIZER / AXIAL / OTHER)
		image_type = dataset.getBytes(Tag.Image​Type) != null ? new String(dataset.getBytes(Tag.Image​Type)) : "";
		String[] imageTypes = image_type.split("\\\\");
		if (imageTypes.length >= 3) {
			image_type = imageTypes[2];
		}
		String[] imagePosition = dataset.getStrings(Tag.Image​Position);
		String sliceLoc = imagePosition != null && imagePosition[2] != null ? imagePosition[2] : "0";
		/* TSUID only can get from dicominputstream... */
		String tsUID = DicomUtilities.getTransferSyntaxUID(filePath);
		String sql = "insert into image values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

		try (Connection conn = openConnection(); PreparedStatement insertStmt = conn.prepareStatement(sql);) {
			insertStmt.setString(1, dataset.getString(Tag.SOP​Instance​UID));
			insertStmt.setString(2, dataset.getString(Tag.SOP​Class​UID));
			insertStmt.setInt(3, dataset.getInt(Tag.Instance​Number, 1));
			insertStmt.setString(4, acquisitionNo);
			insertStmt.setBoolean(5, multiframe);
			insertStmt.setInt(6, totalFrame);
			insertStmt.setString(7, "partial");// deprecated
			insertStmt.setTime(8, sqlAcqDateTime);
			insertStmt.setTime(9, null);// TODO ForwardDateTime//deprecated
			insertStmt.setTime(10, null);// TODO ReceivedDateTime
			insertStmt.setString(11, "partial");// ReceiveStatus//deprecated
			insertStmt.setString(12, filePath);
			insertStmt.setBoolean(13, saveAsLink);
			insertStmt.setInt(14, Integer.parseInt(sliceLoc));
			insertStmt.setBoolean(15, encapsulatedPDF);
			insertStmt.setBoolean(16, false);// ThumbnailStatus//deprecated
			insertStmt.setString(17, frameOfRefUid);// deprecated
			insertStmt.setString(18, imgPos);// deprecated
			insertStmt.setString(19, imgOrientation);// deprecated
			insertStmt.setString(20, image_type);// deprecated
			insertStmt.setString(21, pixelSpacing);// deprecated
			insertStmt.setString(22, sliceThickness);// deprecated
			insertStmt.setInt(23, row);// deprecated
			insertStmt.setInt(24, columns);// deprecated
			insertStmt.setString(25, referSopInsUid.trim());// deprecated
			insertStmt.setString(26, tsUID);
			insertStmt.setString(27, patientID);
			insertStmt.setString(28, studyUid);
			insertStmt.setString(29, seriesUid);
			insertStmt.executeUpdate();
			conn.commit();
		} catch (SQLException ex) {
			// auto rollback
			logger.severe("DatabaseHandler - Unable to save instance information\n" + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private void insertLocale(String language, String country, String languagecode, String countrycode, String localeid)
			throws SQLException {

		String sql = "insert into locale(countrycode,country,languagecode,language,localeid,status) values(?,?,?,?,?,?)";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, countrycode);
			ps.setString(2, country);
			ps.setString(3, languagecode);
			ps.setString(4, language);
			ps.setString(5, localeid);
			ps.setBoolean(6, false);
			ps.executeUpdate();
			conn.commit();
		}
	}

	@SuppressWarnings("unused")
	private void insertDefaultMiscellaneous() throws SQLException {
		String sql = "insert into miscellaneous(Loopback,JNLPRetrieveType,AllowDynamicRetrieveType) values(true,'C-GET',false)";
//		String sql = "insert into miscellaneous(Loopback,JNLPRetrieveType,AllowDynamicRetrieveType) values(true,'C-MOVE',false)";
		try (Connection conn = openConnection(); Statement st = conn.createStatement();) {
			st.executeUpdate(sql);
			conn.commit();
		}
	}

	private void insertPatientInfo(DicomObject dataset) {
		if (!(checkRecordExists("PATIENT", "PatientID", dataset.getString(Tag.Patient​ID)))) {
			String sql = "insert into patient values(?,?,?,?)";
			java.util.Date bod = dataset.getDate(Tag.Patient​Birth​Date);
			java.sql.Date sqlBod = DateUtils.toSQLDateObj(bod);
			try (Connection conn = openConnection(); PreparedStatement insertStmt = conn.prepareStatement(sql);) {
				insertStmt.setString(1, dataset.getString(Tag.Patient​ID));
				insertStmt.setString(2, dataset.getString(Tag.Patient​Name));
				insertStmt.setDate(3, sqlBod);
				insertStmt.setString(4, dataset.getString(Tag.Patient​Sex));
				insertStmt.executeUpdate();
				conn.commit();
			} catch (SQLException ex) {
				logger.severe("DatabaseHandler - Unable to save patient information\n" + ex.getMessage());
			}
		}
	}

	public void insertPreset(PresetModel p) {
		String sql = "INSERT INTO presets(presetname, windowwidth, windowlevel, lut, modality) VALUES (?, ?, ?, ?, ?)";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, p.getPresetName());
			ps.setBigDecimal(2, p.getWW());
			ps.setBigDecimal(3, p.getWL());
			ps.setString(4, p.getLUT());
			ps.setString(5, p.getModality().name());
			ps.executeUpdate();
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * insert new roi. update if already exists.
	 * 
	 * @param roiCon
	 */
	public synchronized void insertRoi(HashMap<String, Object> roiCon) {
		/**
		 * If you change arguments, also check loadRoiContextFromInstance().
		 */
		String jsonProperties = "";
		if (roiCon.get(RoiDBKey.RoiMetaProperties.name()) != null) {
			@SuppressWarnings("unchecked")
			Map<String, String> metaAttributes = (Map<String, String>) roiCon.get(RoiDBKey.RoiMetaProperties.name());
			// 3. Gsonを使って Map -> JSON文字列 に変換
			Gson gson = new Gson();
			jsonProperties = gson.toJson(metaAttributes);
		}
		insertRoi((String) roiCon.get(RoiDBKey.RoiID.name()), (String) roiCon.get(RoiDBKey.Name.name()),
				Integer.parseInt((String) roiCon.get(RoiDBKey.RoiType.name())),
				(int) roiCon.get(RoiGeometry.OriginX.name()), (int) roiCon.get(RoiGeometry.OriginY.name()),
				(int) roiCon.get(RoiGeometry.Width.name()), (int) roiCon.get(RoiGeometry.Height.name()),
				(double[]) roiCon.get(RoiGeometry.PointX.name()), (double[]) roiCon.get(RoiGeometry.PointY.name()),
				(double[]) roiCon.get(RoiGeometry.Shape.name()),
				roiCon.get(RoiDBKey.InstanceNo.name()) == null ? Integer.MIN_VALUE
						: Integer.parseInt((String) roiCon.get(RoiDBKey.InstanceNo.name())),
				roiCon.get(RoiDBKey.RoiGroup.name()) == null ? Integer.MIN_VALUE
						: Integer.parseInt((String) roiCon.get(RoiDBKey.RoiGroup.name())),
				(String) roiCon.get(RoiDBKey.RoiLabel.name()), (String) roiCon.get(RoiDBKey.ObjectType.name()),
				(String) roiCon.get(RoiDBKey.Organ.name()), (String) roiCon.get(RoiDBKey.Description.name()),
				roiCon.get(RoiDBKey.StudyDate.name()) == null ? null
						: DateUtils.toSQLDateObj((String) roiCon.get(RoiDBKey.StudyDate.name())),
				(String) roiCon.get(RoiDBKey.CrossSection.name()), jsonProperties, (String) roiCon.get(RoiDBKey.PatientID.name()),
				(String) roiCon.get(RoiDBKey.StudyInstanceUID.name()), (String) roiCon.get(RoiDBKey.SeriesInstanceUID.name()),
				(String) roiCon.get(RoiDBKey.SOPInstanceUID.name()));
	}

	private void insertRoi(String roiId, String name, int roiType, int originX, int originY, int w, int h,
			double[] pointX, double[] pointY, double[] shapeArray, int instNo, int roiGroup, String roilbl,
			String objType, String organ, String desc/* description */, java.sql.Date studyDate, String crossSection,
			String jsonProperties, String pid, String studyUid, String seriesUid, String sopUid) {
		if (pointX != null && pointY != null) {
			if (pointX.length != pointY.length) {
				throw new IllegalArgumentException(
						getClass().getName() + "insertRoi:Can not save roi, pointXY is incorrect(count mismatch).");
			}
		}
		if (!(checkRecordExists("roi", "RoiID", roiId))) {
			// get as byte
			byte[] byteArrayX = null;
			byte[] byteArrayY = null;
			byte[] byteArrayShape = null;
			if (pointX != null) {
				ByteBuffer bbX = ByteBuffer.allocate(pointX.length * 8);
				for (int i = 0; i < pointX.length; i++) {
					bbX.putDouble(pointX[i]);
				}
				// get as byte
				byteArrayX = bbX.array();
			}
			if (pointY != null) {
				ByteBuffer bbY = ByteBuffer.allocate(pointY.length * 8);
				for (int i = 0; i < pointY.length; i++) {
					bbY.putDouble(pointY[i]);
				}
				// get as byte
				byteArrayY = bbY.array();
			}
			if (shapeArray != null) {
				ByteBuffer bbS = ByteBuffer.allocate(shapeArray.length * 8);
				for (int i = 0; i < shapeArray.length; i++) {
					bbS.putDouble(shapeArray[i]);
				}
				// get as byte
				byteArrayShape = bbS.array();
			}

			String sql = "insert into roi values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
			try (Connection conn = openConnection(); PreparedStatement insertStmt = conn.prepareStatement(sql);) {
				insertStmt.setString(1, roiId);
				insertStmt.setString(2, name);
				insertStmt.setInt(3, roiType);
				insertStmt.setInt(4, originX);
				insertStmt.setInt(5, originY);
				insertStmt.setInt(6, w);
				insertStmt.setInt(7, h);
				insertStmt.setBlob(8, byteArrayX == null ? null : new ByteArrayInputStream(byteArrayX),
						byteArrayX == null ? 0 : byteArrayX.length);
				insertStmt.setBlob(9, byteArrayY == null ? null : new ByteArrayInputStream(byteArrayY),
						byteArrayY == null ? 0 : byteArrayY.length);
				insertStmt.setBlob(10, byteArrayShape == null ? null : new ByteArrayInputStream(byteArrayShape),
						byteArrayShape == null ? 0 : byteArrayShape.length);
				insertStmt.setInt(11, instNo);
				insertStmt.setInt(12, roiGroup);
				insertStmt.setString(13, roilbl);
				insertStmt.setString(14, objType);
				insertStmt.setString(15, organ);
				insertStmt.setString(16, desc);
				insertStmt.setDate(17, studyDate);
				insertStmt.setString(18, crossSection);
				insertStmt.setString(19, jsonProperties);
				insertStmt.setString(20, pid);
				insertStmt.setString(21, studyUid);
				insertStmt.setString(22, seriesUid);
				insertStmt.setString(23, sopUid);
				insertStmt.executeUpdate();
				conn.commit();
			} catch (SQLException ex) {
				logger.severe("DatabaseHandler - Unable save ROI\n" + ex.getMessage());
			}
		} else {// already exists
			// updation
			updateRoiInfo(roiId, name, roiType, originX, originY, w, h, pointX, pointY, shapeArray, instNo, roiGroup,
					roilbl, objType, organ, desc, studyDate, crossSection, jsonProperties, pid, studyUid, seriesUid,
					sopUid);
		}
	}

	private void insertSeriesInfo(final DicomObject dataset, String patientId, String studyUid, boolean saveAsLink) {
		if (!checkRecordExists("SERIES", "SeriesInstanceUID", dataset.getString(Tag.Series​Instance​UID))) {
			/* Series Date */
			java.util.Date date = dataset.getDate(Tag.Series​Date);
			java.sql.Date sqlDate = DateUtils.toSQLDateObj(date);
			/* Series Time */
			java.util.Date time = dataset.getDate(Tag.Series​Time);
			/* ignore milliseconds */
			java.sql.Time sqlTime = DateUtils.toSQLTime(time);

			int numImages = getNumOfInstanceInSeries(patientId, studyUid, dataset.getString(Tag.Series​Instance​UID))
					+ 1;

			String institution = (dataset.getString(Tag.Institution​Name) != null
					&& dataset.getString(Tag.Institution​Name).length() > 0) ? dataset.getString(Tag.Institution​Name)
							: "";
			String seriesNo = (dataset.getString(Tag.Series​Number) != null
					&& dataset.getString(Tag.Series​Number).length() > 0) ? dataset.getString(Tag.Series​Number) : "";
			String modality = (dataset.getString(Tag.Modality) != null && dataset.getString(Tag.Modality).length() > 0)
					? dataset.getString(Tag.Modality)
					: "";
			String modelName = (dataset.getString(Tag.Manufacturer​Model​Name) != null
					&& dataset.getString(Tag.Manufacturer​Model​Name).length() > 0)
							? dataset.getString(Tag.Manufacturer​Model​Name)
							: "";
			String seriesDesc = (dataset.getString(Tag.Series​Description) != null
					&& dataset.getString(Tag.Series​Description).length() > 0)
							? dataset.getString(Tag.Series​Description)
							: "";
			String bodyPartExamined = (dataset.getString(Tag.Body​Part​Examined) != null
					&& dataset.getString(Tag.Body​Part​Examined).length() > 0)
							? dataset.getString(Tag.Body​Part​Examined)
							: "";
			String sql = "insert into series values(?,?,?,?,?,?,?,?,?,?,?,?)";
			try (Connection conn = openConnection(); PreparedStatement insertStmt = conn.prepareStatement(sql);) {
				insertStmt.setString(1, dataset.getString(Tag.Series​Instance​UID));
				insertStmt.setString(2, seriesNo);
				insertStmt.setDate(3, sqlDate);
				insertStmt.setTime(4, sqlTime);
				insertStmt.setString(5, modality);
				insertStmt.setString(6, modelName);
				insertStmt.setString(7, seriesDesc);
				insertStmt.setString(8, bodyPartExamined);
				insertStmt.setString(9, institution);
				insertStmt.setInt(10, numImages);
				insertStmt.setString(11, patientId);
				insertStmt.setString(12, studyUid);
				insertStmt.executeUpdate();
				conn.commit();
				// count up num of series in study table. really confuse "NoOfSeries" means num
				// of series...
				update("study", "NoOfSeries"/* Series number */,
						getNumOfSeries(patientId, studyUid)/* already updated */, "StudyInstanceUID", studyUid);
			} catch (SQLException ex) {
				logger.severe("DatabaseHandler - Unable to save series information\n" + ex.getMessage());
			}
		}
	}

	/**
	 * 
	 * pk 管理番号（データベース用） 1, 2, 3... logicalname 人間が見るためのあだ名 A病院のCT装置 aetitle
	 * DICOM通信で使うユニークID CT_SCANNER_01 hostname ネットワーク上の住所（IPアドレス） 192.168.1.10 port
	 * 住所の部屋番号（窓口番号） 104 ciphers 暗号化の種類 TLS_... (指定があれば暗号化) retrievetype 画像の取得方法
	 * C-MOVE, WADO wadocontext Web取得用のURLパス /wado wadoport Web取得用のポート番号 8080
	 * wadoprotocol Web取得用のプロトコル http retrievets 取得する画像のデータ圧縮形式 1.2.840.10008.1.2.1
	 * 
	 * @param nickname
	 * @param aet
	 * @param hostname
	 * @param port
	 * @param ciphers
	 * @param retrievetype
	 * @param wadocontext
	 * @param wadoport
	 * @param wadoprotocol
	 * @param retrievets
	 */
	public void insertServer(String nickname, String aet, String hostname, int port, String ciphers,
			String retrievetype, String wadocontext, int wadoport, String wadoprotocol, String retrievets) {
		String statement = "INSERT INTO SERVERS (pk,logicalname,aetitle,hostname,port,ciphers,retrievetype,wadocontext,wadoport,wadoprotocol,retrievets) VALUES (default,?,?,?,?,?,?,?,?,?,?)";
		try (Connection conn = openConnection();
				PreparedStatement insertStmt = conn.prepareStatement(statement, Statement.RETURN_GENERATED_KEYS);) {
			insertStmt.setString(1, nickname);
			insertStmt.setString(2, aet);
			insertStmt.setString(3, hostname);
			insertStmt.setInt(4, port);
			insertStmt.setString(5, ciphers);
			insertStmt.setString(6, retrievetype);// RetrieveType()
			insertStmt.setString(7, wadocontext);// WadoURL():wadocontext
			insertStmt.setInt(8, wadoport);// WadoPort
			insertStmt.setString(9, wadoprotocol);// WadoProtocol()
			insertStmt.setString(10, retrievets);// RetrieveTransferSyntax()
			insertStmt.executeUpdate();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe("DatabaseHandler:can not read sql...\n" + ex.getMessage());
		}
	}

	private void insertStudyInfo(DicomObject dataset, boolean saveAsLink, String patientID) {
		if (!checkRecordExists("STUDY", "StudyInstanceUID", dataset.getString(Tag.Study​Instance​UID))) {
			try {
				/* Study date */
				java.util.Date date = dataset.getDate(Tag.Study​Date);
				java.sql.Date sqlDate = DateUtils.toSQLDateObj(date);

				/* Study Time */
				java.util.Date time = dataset.getDate(Tag.Study​Time);
				/* ignore milliseconds in db, but it remains in dataset. */
				java.sql.Time sqlTime = DateUtils.toSQLTime(time);

				java.util.Date birthOfDate = dataset.getDate(Tag.Patient​Birth​Date);
				Integer age = Utils.calculateAge(birthOfDate, date);
				// to avoid sql exception
				if (age == null) {
					age = -1;
				}
				String accessionNo = (dataset.getString(Tag.Accession​Number) != null
						&& dataset.getString(Tag.Accession​Number).length() > 0)
								? dataset.getString(Tag.Accession​Number)
								: "";
				String refName = (dataset.getString(Tag.Referring​Physician​Name) != null
						&& dataset.getString(Tag.Referring​Physician​Name).length() > 0)
								? dataset.getString(Tag.Referring​Physician​Name)
								: "";
				String retAe = (dataset.getString(Tag.Retrieve​AE​Title) != null
						&& dataset.getString(Tag.Retrieve​AE​Title).length() > 0)
								? dataset.getString(Tag.Retrieve​AE​Title)
								: "";
				String studyDesc = (dataset.getString(Tag.Study​Description) != null
						&& dataset.getString(Tag.Study​Description).length() > 0)
								? dataset.getString(Tag.Study​Description)
								: "";
				String studyId = (dataset.getString(Tag.Study​ID) != null
						&& dataset.getString(Tag.Study​ID).length() > 0) ? dataset.getString(Tag.Study​ID) : "";
				int numOfSeries = getNumOfSeries(patientID, dataset.getString(Tag.Study​Instance​UID)) + 1;
				int numOfInst = getNumOfInstancesInStudy(dataset.getString(Tag.Study​Instance​UID)) + 1;

				String sql = "insert into study values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
				try (Connection conn = openConnection(); PreparedStatement insertStmt = conn.prepareStatement(sql);) {
					// be careful
					insertStmt.setString(1, dataset.getString(Tag.Study​Instance​UID));
					insertStmt.setDate(2, sqlDate);
					insertStmt.setTime(3, sqlTime);
					insertStmt.setString(4, accessionNo);
					insertStmt.setString(5, refName);// deprecated
					insertStmt.setString(6, studyDesc);
					insertStmt.setString(7, studyId);
					insertStmt.setString(8, dataset.getString(Tag.Modalities​In​Study));
					insertStmt.setInt(9, numOfSeries);
					insertStmt.setInt(10, numOfInst);
					insertStmt.setInt(11, 0);// RecdImgCnt//deprecated
					insertStmt.setInt(12, 0);// SendImgCnt//deprecated
					insertStmt.setString(13, retAe);// deprecated
					insertStmt.setBoolean(14, false);// DownloadStatus
					insertStmt.setInt(15, age);
					insertStmt.setString(16, patientID);
					insertStmt.executeUpdate();
					conn.commit();
				}
			} catch (SQLException ex) {
				logger.severe("DatabaseHandler - Unable to save study information\n" + ex.getMessage());
			}
		}
	}

	public boolean isAlreadyRegisteredServer(String identicalNickname) {
		if (identicalNickname == null) {
			throw new NullPointerException("DB:isAlreadyRegisteredServer::Cannot search NULL, " + identicalNickname);
		}
		int pk = getCommunicationServerPk(identicalNickname);
		if (pk == -1) {
			return false;
		} else {
			return true;
		}
	}

	public boolean isConnectionStillActive(Connection conn) throws SQLException {
		try {
			if (conn == null || conn.isClosed()) {
				return false;
			} else {
				return true;
			}
		} catch (SQLException e) {
			logger.severe(e.getMessage());
			throw e;
		}
	}

	public boolean isDownloadPending(String studyUid) {
		boolean pending = false;
		String sql = "select DownloadStatus from study where StudyInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql);) {
			ps.setString(1, studyUid);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					pending = rs.getBoolean("DownloadStatus");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return pending;
	}

	/*
	 * get saveAsLink information from instance
	 */
	public boolean isInstanceSavedAsLink(String studyUID, String seriesUID, String sopUID) {
		boolean res = false;
		String statement = "SELECT * FROM IMAGE WHERE StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, studyUID);
			pstmt.setString(2, seriesUID);
			pstmt.setString(3, sopUID);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					res = rset.getBoolean("isLink");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return res;
	}

	public ArrayList<HashMap<String, String>> listStudies(String patientName, String patientID, String dob,
			String accNo, String studyDate, String studyDesc, String modality) {
		ArrayList<HashMap<String, String>> result = new ArrayList<>();
		try (Connection conn = openConnection();) {
			ResultSet matchingInfo = conn.createStatement().executeQuery(
					"select * from patient inner join study on patient.PatientID=study.PatientID where upper(patient.PatientID) like '"
							+ patientID + "' and upper(patient.PatientName) like '" + patientName
							+ "' and patient.PatientBirthDate like '" + dob
							+ "' and upper(study.AccessionNumber) like '" + accNo + "' and study.StudyDate like '"
							+ studyDate + "' and upper(study.StudyDescription) like '" + studyDesc
							+ "' and upper(study.ModalitiesInStudy) like '" + modality + "'");
			while (matchingInfo.next()) {
				// ★ 修正: 行ごとに新しいMapを作る(以前はループ外の1個のMapを使い回しており、
				// 複数件マッチすると全件が同じ参照=最後の行のコピーになるバグがあった)
				HashMap<String, String> matchingStudies = new HashMap<String, String>();
				matchingStudies.put("PatientID", matchingInfo.getString("PatientID"));
				matchingStudies.put("PatientName", matchingInfo.getString("PatientName")); // Bug fix: key was "PatientID"
				matchingStudies.put("PatientBirthDate", matchingInfo.getString("PatientBirthDate"));
				matchingStudies.put("AccessionNumber", matchingInfo.getString("AccessionNumber"));
				matchingStudies.put("StudyDate", matchingInfo.getString("StudyDate"));
				matchingStudies.put("StudyTime", matchingInfo.getString("StudyTime"));
				matchingStudies.put("StudyDescription", matchingInfo.getString("StudyDescription"));
				matchingStudies.put("ModalitiesInStudy", matchingInfo.getString("ModalitiesInStudy"));
				matchingStudies.put("StudyDescription", matchingInfo.getString("StudyDescription"));
				matchingStudies.put("NoOfSeries", matchingInfo.getString("NoOfSeries"));
				matchingStudies.put("NoOfInstances", matchingInfo.getString("NoOfInstances"));
				matchingStudies.put("StudyInstanceUID", matchingInfo.getString("StudyInstanceUID"));
				result.add(matchingStudies);
			}
			matchingInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		} catch (NumberFormatException nfe) {
			logger.severe(nfe.getMessage());
		}
		return result;
	}

	public HashMap<String, Object> loadImageNodeMaterials(ResultSet imageInfo) throws SQLException {
		HashMap<String, Object> nodeMaterial = new HashMap<String, Object>();
		nodeMaterial.put("level", 4);
		nodeMaterial.put("PatientID", imageInfo.getString("PatientID"));
		nodeMaterial.put("AcquisitionDateTime", imageInfo.getString("AcquisitionDateTime"));
		nodeMaterial.put("AcquisitionNumber", imageInfo.getString("AcquisitionNumber"));
		nodeMaterial.put("InstanceNumber", imageInfo.getString("InstanceNumber"));
		nodeMaterial.put("StudyInstanceUID", imageInfo.getString("StudyInstanceUID"));
		nodeMaterial.put("SeriesInstanceUID", imageInfo.getString("SeriesInstanceUID"));
		nodeMaterial.put("SOPInstanceUID", imageInfo.getString("SOPInstanceUID"));
		return nodeMaterial;
	}

	/**
	 * load db directory and set dbdir.
	 * 
	 * @throws Exception
	 */
	public void loadLocalDBLocation() throws Exception {
		String loc = null;
		if (derby != null) {
			String[] details = getListenerDetails();
			if (details == null || details.length < 4 || details[3] == null) {
				logger.warning("DB:loadLocalDBLocation():: listener details unavailable or incomplete, falling back to prop file.");
				loc = Utils.getGraphyDBLocationFromProp().getAbsolutePath();
			} else {
				loc = details[3];
			}
		} else {
			loc = Utils.getGraphyDBLocationFromProp().getAbsolutePath();
		}
		if (loc == null) {
			throw new Exception("DB:loadLocalDBLocation():: Can not load graphy db location...");
		}
		this.dbdir = new File(loc).getAbsolutePath();
	}

	/**
	 * Parses a single ROI row from the ResultSet into a context map.
	 * Shared by loadRoiContext, loadRoiContextFromInstance, loadRoiContextFromPatient, loadRoiContextFromSeries.
	 */
	private HashMap<String, Object> parseRoiRow(ResultSet rset) throws SQLException {
		HashMap<String, Object> roiCon = new HashMap<>();
		roiCon.put("RoiID", rset.getString("RoiID"));
		roiCon.put("Name", rset.getString("Name"));
		roiCon.put("RoiType", rset.getInt("RoiType"));
		roiCon.put("OriginX", rset.getInt("OriginX"));
		roiCon.put("OriginY", rset.getInt("OriginY"));
		roiCon.put("Width", rset.getInt("Width"));
		roiCon.put("Height", rset.getInt("Height"));
		roiCon.put("PointX", doubleArr2floatArr(blob2DoubleArray(rset.getBlob("PointX"))));
		roiCon.put("PointY", doubleArr2floatArr(blob2DoubleArray(rset.getBlob("PointY"))));
		roiCon.put("Shape", doubleArr2floatArr(blob2DoubleArray(rset.getBlob("Shape"))));
		roiCon.put("InstanceNo", rset.getInt("InstanceNo"));
		roiCon.put("RoiGroup", rset.getInt("RoiGroup"));
		roiCon.put("RoiLabel", rset.getString("RoiLabel"));
		roiCon.put("ObjectType", rset.getString("ObjectType"));
		roiCon.put("Organ", rset.getString("Organ"));
		roiCon.put("Description", rset.getString("Description"));
		java.sql.Date sd = rset.getDate(RoiDBKey.StudyDate.name());
		if (sd != null) {
			roiCon.put(RoiDBKey.StudyDate.name(), new SimpleDateFormat("yyyy/MM/dd").format(sd));
		} else {
			roiCon.put(RoiDBKey.StudyDate.name(), null);
		}
		roiCon.put(RoiDBKey.CrossSection.name(), rset.getString(RoiDBKey.CrossSection.name()));
		String jsonProperties = rset.getString(RoiDBKey.RoiMetaProperties.name());
		if (jsonProperties != null) {
			Gson gson = new Gson();
			java.lang.reflect.Type type = new TypeToken<HashMap<String, String>>() {}.getType();
			roiCon.put(RoiDBKey.RoiMetaProperties.name(), gson.<Map<String, String>>fromJson(jsonProperties, type));
		}
		roiCon.put("PatientID", rset.getString("PatientID"));
		roiCon.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
		roiCon.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
		roiCon.put("SOPInstanceUID", rset.getString("SOPInstanceUID"));
		return roiCon;
	}

	public HashMap<String, Object> loadRoiContext(String roiId, String pid, String studyUid, String seriesUid,
			String sopUid) {
		String statement = "SELECT * FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=? AND RoiID=?";
		try (Connection conn = openConnection();
				PreparedStatement pstmt = conn.prepareStatement(statement)) {
			pstmt.setString(1, pid);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			pstmt.setString(4, sopUid);
			pstmt.setString(5, roiId);
			try (ResultSet rset = pstmt.executeQuery()) {
				if (rset.next()) {
					HashMap<String, Object> roiCon = parseRoiRow(rset);
					conn.commit();
					return roiCon;
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe("Database error in loadRoiContext: " + ex.getMessage());
		}
		return null;
	}

	public ArrayList<HashMap<String, Object>> loadRoiContextFromInstance(String pid, String studyUid, String seriesUid,
			String sopUid) {
		ArrayList<HashMap<String, Object>> set = new ArrayList<HashMap<String, Object>>();
		String statement = "SELECT * FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement)) {
			pstmt.setString(1, pid);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			pstmt.setString(4, sopUid);
			try (ResultSet rset = pstmt.executeQuery()) {
				while (rset.next()) {
					set.add(parseRoiRow(rset));
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return set;
	}

	public ArrayList<HashMap<String, Object>> loadRoiContextFromPatient(String pid) {
		if (pid == null) {
			return null;
		}
		ArrayList<HashMap<String, Object>> set = new ArrayList<>();
		String statement = "SELECT * FROM ROI WHERE PatientID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement)) {
			pstmt.setString(1, pid);
			try (ResultSet rset = pstmt.executeQuery()) {
				while (rset.next()) {
					set.add(parseRoiRow(rset));
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return set.isEmpty() ? null : set;
	}

	/**
	 * シリーズレベルでROIを一括取得します。多次元データのディスパッチに使用します。
	 */
	public ArrayList<HashMap<String, Object>> loadRoiContextFromSeries(String pid, String studyUid, String seriesUid) {
		ArrayList<HashMap<String, Object>> set = new ArrayList<>();
		String statement = "SELECT * FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement)) {
			pstmt.setString(1, pid);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			try (ResultSet rset = pstmt.executeQuery()) {
				while (rset.next()) {
					set.add(parseRoiRow(rset));
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return set;
	}

	public HashMap<String, Object> loadSeriesNodeMaterial(ResultSet seriesInfo, HashMap<String, Object> studyMaterial) {
		String modalityInStudy = (String) studyMaterial.get("ModalitiesInStudy");
		try {
			if (modalityInStudy == null || modalityInStudy.equals("")) {
				modalityInStudy = seriesInfo.getString("Modality");
			} else {
				String modality = seriesInfo.getString("Modality");
				if (!modalityInStudy.contains(modality)) {
					modalityInStudy = modalityInStudy + "," + seriesInfo.getString("Modality");
				}
			}
			// update
			studyMaterial.put("ModalitiesInStudy", modalityInStudy);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		HashMap<String, Object> seriesNodeMaterial = new HashMap<String, Object>();
		try {
			seriesNodeMaterial.put("level", 3);// DICOMNode.SERIES
			seriesNodeMaterial.put("PatientID", seriesInfo.getString("PatientID"));
			seriesNodeMaterial.put("SeriesDate", seriesInfo.getString("SeriesDate"));
			seriesNodeMaterial.put("SeriesDescription", seriesInfo.getString("SeriesDescription"));
			seriesNodeMaterial.put("Modality", seriesInfo.getString("Modality"));
			seriesNodeMaterial.put("InstitutionName", seriesInfo.getString("InstitutionName"));
			seriesNodeMaterial.put("ModelName", seriesInfo.getString("ModelName"));
			seriesNodeMaterial.put("SeriesNumber", seriesInfo.getString("SeriesNumber"));
			seriesNodeMaterial.put("NumOfInstanceInSeries",
					String.valueOf(getNumOfInstanceInSeries(seriesInfo.getString("PatientID"),
							seriesInfo.getString("StudyInstanceUID"), seriesInfo.getString("SeriesInstanceUID"))));
			seriesNodeMaterial.put("StudyInstanceUID", seriesInfo.getString("StudyInstanceUID"));
			seriesNodeMaterial.put("SeriesInstanceUID", seriesInfo.getString("SeriesInstanceUID"));

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return seriesNodeMaterial;
	}

	public ArrayList<DicomCommunicationNode> loadServerList() {
		// get server materials
		ArrayList<HashMap<String, Object>> serverMaterials = getCommunicationServerList();
		ArrayList<DicomCommunicationNode> serverList = new ArrayList<DicomCommunicationNode>();
		for (HashMap<String, Object> nodeMaterials : serverMaterials) {
			serverList.add(new DicomCommunicationNode(nodeMaterials));
		}
		return serverList;
	}

	public HashMap<String, Object> loadStudyNodeMaterial(ResultSet patientInfo, ResultSet studyInfo) {
		HashMap<String, Object> studyNodeMaterial = new HashMap<String, Object>();
		try {
			studyNodeMaterial.put("level", 2);// DICOMNode.Study
			studyNodeMaterial.put("PatientName", patientInfo.getString("PatientName"));
			studyNodeMaterial.put("PatientID", patientInfo.getString("PatientID"));
			studyNodeMaterial.put("StudyDate", studyInfo.getString("StudyDate"));
			studyNodeMaterial.put("StudyTime", studyInfo.getString("StudyTime"));
			studyNodeMaterial.put("StudyDescription", studyInfo.getString("StudyDescription"));
			studyNodeMaterial.put("ModalitiesInStudy", studyInfo.getString("ModalitiesInStudy"));
			studyNodeMaterial.put("PatientSex", patientInfo.getString("PatientSex"));
			studyNodeMaterial.put("PatientBirthDate", patientInfo.getString("PatientBirthDate"));
			// Age
			String age_str = studyInfo.getString("PatientAge");
			age_str = (age_str != null && !age_str.equals("-1")) ? age_str : "";
			String studyUID = studyInfo.getString("StudyInstanceUID");
			studyNodeMaterial.put("PatientAge", age_str);// get from study info
			studyNodeMaterial.put("AccessionNumber", studyInfo.getString("AccessionNumber"));
			studyNodeMaterial.put("NumOfSeriesInStudy", String.valueOf(getNumOfSeriesInStudy(studyUID)));
			studyNodeMaterial.put("NumOfInstancesInStudy", String.valueOf(getNumOfInstancesInStudy(studyUID)));
			studyNodeMaterial.put("StudyInstanceUID", studyUID);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return studyNodeMaterial;
	}

	private EmbeddedDataSource initDataSource(String graphydir, boolean create) throws Throwable {
		derby = new EmbeddedDataSource();
		derby.setDatabaseName(graphydir + File.separator + databasename);
		derby.setUser(username);
		derby.setPassword(password);
		if (create) {
			derby.setCreateDatabase("create");
		}
		return derby;
	}

	/**
	 * Acquire a connection to the database and set it to manual commit mode.
	 *
	 * @return connection
	 * @throws SQLException
	 */
	private Connection openConnection() throws SQLException {
		if (derby == null) {
			throw new SQLException("The database data source has not been initialized.");
		}
		Connection conn = derby.getConnection(); // 接続失敗時はここでSQLExceptionがスローされる
		try {
			// トランザクション分離レベルを設定
			// アプリケーションの要件でこのレベルが必要か確認してください。
			// Derbyのデフォルトは TRANSACTION_READ_COMMITTED です。
			conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			// connの取得には成功したが、その後の設定で失敗した場合のクリーンアップ
			try {
				conn.close();
			} catch (SQLException closeEx) {
				e.addSuppressed(closeEx); // close時の例外を元の例外に添付
			}
			throw e;
		}
		return conn;
	}

	public boolean overWriteSavedAsLinkRecord(DicomObject ds, boolean saveAsLinkWillImport) {
		String studyUID = ds.getString(Tag.Study​Instance​UID);
		String seriesUID = ds.getString(Tag.Series​Instance​UID);
		String sopUID = ds.getString(Tag.SOP​Instance​UID);
		/* check already exists */
		if (checkImageRecordExists(studyUID, seriesUID, sopUID)) {
			/* check existing data is savedAsLink? */
			if (isInstanceSavedAsLink(studyUID, seriesUID, sopUID)) {
				/* Is this import try to save local? */
				if (!saveAsLinkWillImport) {
					return true;
				}
			}
		}
		return false;
	}

	private void readDBTable(Set<String> set, DatabaseMetaData dbmeta, String searchCriteria, String schema)
			throws SQLException {
		ResultSet rs = dbmeta.getTables(null, schema, null, new String[] { searchCriteria });
		while (rs.next()) {
			set.add(rs.getString("TABLE_NAME"));
		}
		rs.close();
	}

	public ArrayList<DefaultMutableTreeNode> selectStudiesWithSearchKeys(String patID, String from, String to,
			ArrayList<String> modalities) {
		ArrayList<String> keys = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		if (patID == null && from == null && to == null) {
			sb.append("SELECT * FROM STUDY");
		} else {
			sb.append("SELECT * FROM STUDY WHERE");
		}
		int basicStateLen = sb.length();

		if (patID != null) {
			sb.append(" PatientID=?");
			keys.add(patID);
		}

		if (from != null && to != null) {
			if (sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" BETWEEN");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
				sb.append(" AND");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			} else {
				sb.append(" StudyDate");
				sb.append(" BETWEEN");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
				sb.append(" AND");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}
		} else if (from != null && to == null) {
			if (sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" >=");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
			} else {
				sb.append(" StudyDate");
				sb.append(" >=");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
			}
		} else if (from == null && to != null) {
			if (sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" <");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			} else {
				sb.append(" StudyDate");
				sb.append(" <");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}
		}
		// sample
		// select * from table where pid=? and pname=? and studydate between ? and ?
		// select * from table where pid=? and pname=? and studydate >= ?
		// select * from table where pid=? and pname=? and studydate <
		// ?(e.g,'2006-11-30')
		ArrayList<DefaultMutableTreeNode> studiesList = new ArrayList<DefaultMutableTreeNode>();
		String statement = sb.toString();
		// study level search
		try (Connection conn = openConnection(); PreparedStatement pstmtStudy = conn.prepareStatement(statement);) {
			for (int i = 0; i < keys.size(); i++) {
				pstmtStudy.setString((i + 1), keys.get(i));
			}
			ResultSet studyInfo = pstmtStudy.executeQuery();
			// loop all study
			while (studyInfo.next()) {
				String patIDInRecord = studyInfo.getString("PatientID");
				String studyUID = studyInfo.getString("StudyInstanceUID");
				/*
				 * This query will retrieve any patients studies. Get patient info one by one
				 * from study.
				 */
				ResultSet patientInfo = conn.createStatement()
						.executeQuery("select * from patient where PatientID='" + patIDInRecord + "'");
				// get patient info only once
				if (patientInfo.next()) {
					HashMap<String, Object> studyMaterial = loadStudyNodeMaterial(patientInfo, studyInfo);
					DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(studyMaterial, true);
					String stmSeries = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?";
					if (modalities != null && modalities.size() > 0) {
						for (int i = 0; i < modalities.size(); i++) {
							String stmSeries_ = stmSeries + " AND " + "Modality=?";
							stmSeries_ = stmSeries_ + " order by SeriesNumber";
							PreparedStatement pstmtSeries = conn.prepareStatement(stmSeries_);
							pstmtSeries.setString(1, patIDInRecord);
							pstmtSeries.setString(2, studyUID);
							pstmtSeries.setString(3, modalities.get(i));
							ResultSet seriesInfo = pstmtSeries.executeQuery();
							while (seriesInfo.next()) {
								HashMap<String, Object> seriesMaterial = loadSeriesNodeMaterial(seriesInfo,
										studyMaterial);
								DefaultMutableTreeNode series = new DefaultMutableTreeNode(seriesMaterial, true);
								String stmImage = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber";
								PreparedStatement pstmtImage = conn.prepareStatement(stmImage);
								pstmtImage.setString(1, patIDInRecord);
								pstmtImage.setString(2, seriesInfo.getString("StudyInstanceUID"));
								pstmtImage.setString(3, seriesInfo.getString("SeriesInstanceUID"));
								ResultSet imageInfo = pstmtImage.executeQuery();
								while (imageInfo.next()) {
									HashMap<String, Object> imageMaterial = loadImageNodeMaterials(imageInfo);
									DefaultMutableTreeNode image = new DefaultMutableTreeNode(imageMaterial, false);
									series.add(image);
								}
								if (series.getChildCount() > 0) {
									studyNode.add(series);
								}
								imageInfo.close();
								pstmtImage.close();
							} // series loop-end
							if (studyNode.getChildCount() > 0) {
								if (!studiesList.contains(studyNode)) {
									studiesList.add(studyNode);
								}
							}
							seriesInfo.close();
							pstmtSeries.close();
						}
					} else {
						stmSeries = stmSeries + " order by SeriesNumber";
						PreparedStatement pstmtSeries = conn.prepareStatement(stmSeries);
						pstmtSeries.setString(1, patIDInRecord);
						pstmtSeries.setString(2, studyUID);
						ResultSet seriesInfo = pstmtSeries.executeQuery();
						while (seriesInfo.next()) {
							HashMap<String, Object> seriesMaterial = loadSeriesNodeMaterial(seriesInfo, studyMaterial);
							DefaultMutableTreeNode series = new DefaultMutableTreeNode(seriesMaterial, true);
							String stmImage = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber";
							PreparedStatement pstmtImage = conn.prepareStatement(stmImage);
							pstmtImage.setString(1, patIDInRecord);
							pstmtImage.setString(2, seriesInfo.getString("StudyInstanceUID"));
							pstmtImage.setString(3, seriesInfo.getString("SeriesInstanceUID"));
							ResultSet imageInfo = pstmtImage.executeQuery();
							while (imageInfo.next()) {
								HashMap<String, Object> imageMaterial = loadImageNodeMaterials(imageInfo);
								DefaultMutableTreeNode image = new DefaultMutableTreeNode(imageMaterial, false);
								series.add(image);
							}
							if (series.getChildCount() > 0) {
								studyNode.add(series);
							}
							imageInfo.close();
							pstmtImage.close();
						} // series loop-end
						if (studyNode.getChildCount() > 0) {
							studiesList.add(studyNode);
						}
						seriesInfo.close();
						pstmtSeries.close();
					}
				}
			}
			studyInfo.close();
			// pstmtStudy.close(); -> auto closing by try-with-resource
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return studiesList;// DO NOT return NULL.
	}

	/**
	 * add patient name's query compared to selectStudiesWithSearchKeys1.
	 * 名前によるあいまい検索を許容する
	 * 
	 * @param patID
	 * @param patName
	 * @param from
	 * @param to
	 * @param modalities
	 * @return
	 */
	public ArrayList<DefaultMutableTreeNode> selectStudiesWithSearchKeys2(String patID, String patName, String from,
			String to, ArrayList<String> modalities) {
		/* Construct STUDY Query Statement */
		ArrayList<String> keys = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		if (patID == null && from == null && to == null) {
			sb.append("SELECT * FROM STUDY");
		} else {
			sb.append("SELECT * FROM STUDY WHERE");
		}
		int basicStateLen = sb.length();

		if (patID != null) {
			sb.append(" PatientID=?");
			keys.add(patID);
		}

		if (from != null && to != null) {
			if (sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" BETWEEN");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
				sb.append(" AND");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			} else {
				sb.append(" StudyDate");
				sb.append(" BETWEEN");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
				sb.append(" AND");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}
		} else if (from != null && to == null) {
			if (sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" >=");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
			} else {
				sb.append(" StudyDate");
				sb.append(" >=");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
			}
		} else if (from == null && to != null) {
			if (sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" <");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			} else {
				sb.append(" StudyDate");
				sb.append(" <");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}
		}
		ArrayList<DefaultMutableTreeNode> studiesList = new ArrayList<DefaultMutableTreeNode>();
		String statement = sb.toString();

		try (Connection conn = openConnection();) {
			PreparedStatement psPat = null;

			if (patID == null && patName == null) {
				return selectStudiesWithSearchKeys(patID, from, to, modalities);
			} else if (patID != null && patName == null) {
				return selectStudiesWithSearchKeys(patID, from, to, modalities);
			} else if (patID == null && patName != null) {
				String patQueryStatement = "select * from patient where PatientName LIKE ?";
				psPat = conn.prepareStatement(patQueryStatement);
				psPat.setString(1, patName + "%");
			} else if (patID != null && patName != null) {
				String patQueryStatement = "select * from patient where PatientID=? and PatientName LIKE ?";
				psPat = conn.prepareStatement(patQueryStatement);
				psPat.setString(1, patID);
				psPat.setString(2, patName + "%");
			}
			// このロジックの場合、PatientInfoが取得できる
			ResultSet patientInfo = psPat.executeQuery();
			// あいまい検索を許容する
			while (patientInfo.next()) {
				/* all study related keys use here. */
				PreparedStatement pstmtStudy = conn.prepareStatement(statement);
				for (int i = 0; i < keys.size(); i++) {
					pstmtStudy.setString((i + 1), keys.get(i));
				}
				String patIdInRecord = patientInfo.getString("PatientID");
				ResultSet studyInfo = pstmtStudy.executeQuery();
				while (studyInfo.next()) {
					String studyUID = studyInfo.getString("StudyInstanceUID");
					HashMap<String, Object> studyMaterial = loadStudyNodeMaterial(patientInfo, studyInfo);
					DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(studyMaterial, true);
					String stmSeries = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?";
					if (modalities != null && modalities.size() > 0) {
						for (int i = 0; i < modalities.size(); i++) {
							if (i == 0) {
								stmSeries += " AND Modality IN (?";// , ?, ?, ...)
							} else {
								stmSeries += ",?";
							}
							if (i == modalities.size() - 1) {
								stmSeries += ")";
							}
						}
					}

					stmSeries = stmSeries + " order by SeriesNumber";
					PreparedStatement pstmtSeries = conn.prepareStatement(stmSeries);
					pstmtSeries.setString(1, patIdInRecord);// DO NOT USE patID that already inputed.
					pstmtSeries.setString(2, studyUID);
					if (modalities != null && modalities.size() > 0) {
						for (int i = 0; i < modalities.size(); i++) {
							pstmtSeries.setString(i + 3, modalities.get(i));
						}
					}
					ResultSet seriesInfo = pstmtSeries.executeQuery();
					while (seriesInfo.next()) {
						HashMap<String, Object> seriesMaterial = loadSeriesNodeMaterial(seriesInfo, studyMaterial);
						DefaultMutableTreeNode series = new DefaultMutableTreeNode(seriesMaterial, true);
						String stmImage = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber";
						PreparedStatement pstmtImage = conn.prepareStatement(stmImage);
						pstmtImage.setString(1, patIdInRecord);
						pstmtImage.setString(2, seriesInfo.getString("StudyInstanceUID"));
						pstmtImage.setString(3, seriesInfo.getString("SeriesInstanceUID"));
						ResultSet imageInfo = pstmtImage.executeQuery();
						while (imageInfo.next()) {
							HashMap<String, Object> imageMaterial = loadImageNodeMaterials(imageInfo);
							DefaultMutableTreeNode image = new DefaultMutableTreeNode(imageMaterial, false);
							series.add(image);
						}
						if (series.getChildCount() > 0) {
							studyNode.add(series);
						}
						imageInfo.close();
						pstmtImage.close();
					}
					if (studyNode.getChildCount() > 0) {
						studiesList.add(studyNode);
					}
					seriesInfo.close();
					pstmtSeries.close();
				}
				studyInfo.close();
				pstmtStudy.close();
			}
			patientInfo.close();
			psPat.close();
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return studiesList;
	}

	public void setSaveAsLinkState(boolean bool) {
		this.saveAsLink = bool;
	}

	/**
	 * Derbyデータベースと関連サービスを安全にシャットダウンします。
	 */
	public void shutdownDB() {
		// シャットダウン対象が何もなければ即時終了
		if (derby == null && dcmqrscp == null) {
			return;
		}
		try {
			// 1. Derbyのシャットダウン処理のみを try ブロックに記述
			if (derby != null) {
				derby.setShutdownDatabase("shutdown");
				derby.getConnection();
				// 2. 例外がスローされなかった = 失敗。このケースを警告ログとして記録。
				logger.warning("Derbyのシャットダウンコマンドが例外をスローしませんでした。シャットダウンに失敗した可能性があります。");
			}
		} catch (SQLException e) {
			// 3. 正常なシャットダウンで期待される例外(SQLState)かどうかを判定
			if ("XJ015".equals(e.getSQLState()) || "08006".equals(e.getSQLState())) {
				// これは期待通りの「成功の例外」なので、INFOレベルでログ出力
				logger.info("Derbyデータベースは正常にシャットダウンしました: " + databasename);
			} else {
				// 予期せぬSQLExceptionの場合は、エラーとして詳細をログ出力
				logger.log(Level.SEVERE, "Derbyのシャットダウン中に予期せぬSQL例外が発生しました。", e);
			}
		} finally {
			// 4. 成功・失敗・例外の有無にかかわらず、必ず実行したいクリーンアップ処理
			if (dcmqrscp != null) {
				try {
					dcmqrscp.stop();
					logger.info("dcmqrscp サービスを停止しました。");
				} catch (Exception stopEx) {
					// サービスの停止処理自体が失敗する可能性も考慮
					logger.log(Level.SEVERE, "dcmqrscp サービスの停止中にエラーが発生しました。", stopEx);
				}
			}
			if (dicomWebServer != null) {
				try {
					dicomWebServer.stop();
					logger.info("DICOMwebサーバーを停止しました。");
				} catch (Exception stopEx) {
					logger.log(Level.SEVERE, "DICOMwebサーバーの停止中にエラーが発生しました。", stopEx);
				}
			}
			// データベース参照を解放
			derby = null;
			dcmqrscp = null;
			dicomWebServer = null;
		}
	}

	public boolean startingUp() throws SQLException {

		try {
			loadLocalDBLocation();
			Log.logger.info("Current DB location: " + dbdir);
			System.setProperty("derby.system.home", dbdir);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		if (derby == null) {
			try {
				initDataSource(dbdir, true);
			} catch (Throwable e) {
				e.printStackTrace();
				return false;
			}
		}

		boolean dbExists = checkDBExists();
		if (dbExists == false) {
			try {
				createTables();
				insertDefaultListenerDetails();
				insertDefaultPresets();
				insertDefaultLocales();
				insertDefaultTextAnnotationList();
			} catch (SQLException e) {
				logger.severe("Can not start DB because can not read SQL files ...");
				return false;
			}
		}
		// ★ 既存DBにDICOMweb用の列が無ければ追加する(新規DBは既にLISTENER.sqlに列があるためno-op)
		migrateListenerTableIfNeeded();

		try {
			initDicomServer();
		} catch (IOException e) {
			// Walk the cause chain to detect BindException (port already in use → GRAPHY already running)
			Throwable cause = e;
			while (cause != null) {
				if (cause instanceof java.net.BindException) {
					String msg = "GRAPHY is already working!\n\nThe DICOM server port (" + defaultPort + ") is already in use.\nPlease close the existing GRAPHY window before starting a new one.";
					JOptionPane.showMessageDialog(null, msg, "GRAPHY", JOptionPane.WARNING_MESSAGE);
					Log.logger.warning("DICOM server port " + defaultPort + " is already in use. GRAPHY may already be running.");
					return false;
				}
				cause = cause.getCause();
			}
			Log.logger.severe("Can not start DcmQRSCP: " + e.getMessage());
			e.printStackTrace();
			return false;
		} catch (SQLException e) {
			Log.logger.severe("Can not start DcmQRSCP...");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
     * Writes the DicomImage objects in memory to a temporary directory, sends them to the DB, and automatically cleans up afterwards.
     * * @param dcmImages A map of DicomImages generated by GDicomTools.imagePlusToDcm, etc.
     */
	public void storeDicomImagesToDb(HashMap<Integer, DicomImage> dcmImages) {
		if (dcmImages == null || dcmImages.isEmpty()) {
			Log.logger.warning("Target DicomImage map is empty. Aborting process.");
			return;
		}
		Path tempPath = null;

		try {
			tempPath = Files.createTempDirectory("graphy_store_ready_dicom_");
			File tempDir = tempPath.toFile();

			Log.logger.info("Created a secure temporary output directory: " + tempDir.getAbsolutePath());

			// Loop through all DicomImages in the map (all slices and channels)
			for (Integer key : dcmImages.keySet()) {
				DicomImage dcmImg = dcmImages.get(key);
				if (dcmImg == null)
					continue;

				// Get SOPInstanceUID from the header to ensure a unique filename
				if (dcmImg.getHeader() == null) {
					logger.warning("storeDicomImagesToDb: DicomImage header is null for key=" + key + ", skipping.");
					continue;
				}
				String sopUID = dcmImg.getHeader().getString(Tag.SOPInstanceUID);
				if (sopUID == null || sopUID.trim().isEmpty()) {
					sopUID = "slice_" + key;
				} else {
					sopUID = sopUID.trim();
				}

				// Determine the full output path (with .dcm extension)
				String outFilePath = tempDir.getAbsolutePath() + File.separator + sopUID + ".dcm";

				// [Write] Physically write the in-memory object to a temporary file
				DicomWriter.newDicomWriter().write(dcmImg.getHeader(), UID.ImplicitVRLittleEndian.uid(), outFilePath);

				File dcmFile = new File(outFilePath);

				// [Send] Send to PACS/DB
				if (dcmFile.exists()) {
					Log.logger.info("Sending to DB/PACS: " + dcmFile.getName());
//					DimseUtilities.sendFile(dcmFile);
					DimseUtilities.store(outFilePath, false);
				} else {
					Log.logger.warning("Failed to create temporary file. Skipping transmission: " + outFilePath);
				}
			}

			Log.logger.info("Completed DB transmission for all visualization map images.");

		} catch (IOException e) {
			Log.logger.log(Level.SEVERE, "Failed to create temporary directory or file I/O error occurred", e);
		} catch (Exception e) {
			Log.logger.log(Level.SEVERE, "An exception occurred during the DB storage process for visualization maps", e);
		} finally {
			if (tempPath != null && Files.exists(tempPath)) {
				try {
					File tempDir = tempPath.toFile();
					File[] files = tempDir.listFiles();
					if (files != null) {
						for (File f : files) {
							if (f.exists()) {
								f.delete(); // Delete the file inside
							}
						}
					}
					boolean dirDeleted = tempDir.delete();
					if (dirDeleted) {
						Log.logger.info("Successfully cleaned up the temporary directory.");
					} else {
						Log.logger.warning("Failed to delete the temporary directory: " + tempDir.getAbsolutePath());
					}
				} catch (Exception e) {
					Log.logger.log(Level.WARNING, "An error occurred during the cleanup process", e);
				}
			}
		}
	}
    
    public void setCurrentLocale(String localeid) {
		try (Connection conn = openConnection();) {
			conn.createStatement().execute("update locale set status=false");
			conn.createStatement().execute("update locale set status=true where localeid='" + localeid + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void initDicomServer() throws IOException, SQLException {

		if (dcmqrscp != null) {
			dcmqrscp.stop();
			dcmqrscp = null;
		}

		// check configuration file
		File recFac = new File(ConfigInfo.getPath(ConfigInfo.SERVER_RecordFactory_Props));
		if (!recFac.exists()) {
			File defRecFac = Resources.RecordFactory.tempFile();
			new File(ConfigInfo.getPath(ConfigInfo.ConfDirName)).mkdirs();
			Path src = Paths.get(defRecFac.toURI());
			Path out = Paths.get(recFac.toURI());
			Files.copy(src, out);
			recFac = new File(ConfigInfo.getPath(ConfigInfo.SERVER_RecordFactory_Props));
			if (!recFac.exists()) {
				throw new IOException("SERVER_RecordFactory_Prop file not found...");
			}
		}

		File ae_prop = new File(ConfigInfo.getPath(ConfigInfo.SERVER_AE_Props));
		if (!ae_prop.exists()) {
			File defAE = Resources.AE_Properties.tempFile();
			Path src = Paths.get(defAE.toURI());
			Path out = Paths.get(ae_prop.toURI());
			Files.copy(src, out);
			ae_prop = new File(ConfigInfo.getPath(ConfigInfo.SERVER_AE_Props));
			if (!ae_prop.exists()) {
				throw new IOException("SERVER_AE_Prop file not found...");
			}
		}
		// start qrscp
		String[] details = getListenerDetails();
		if (details == null || details.length < 4) {
			logger.severe("DB:initDicomServer():: getListenerDetails() returned null or incomplete data.");
			throw new IOException("Cannot initialize DICOM server: listener details not found in database.");
		}
		String currentAet = details[0];
		String currentHost = details[1];
		String currentPort = details[2];
		String currentStorageDirPath = details[3];
		/**
		 * properties file's database path is primary.
		 */
		String dir_validation = new File(currentStorageDirPath).getAbsolutePath();
		if (!dbdir.equals(dir_validation)) {
			updateListener(currentAet, currentHost, currentPort, dbdir);
		}
		dcmqrscp = new DcmQRSCP();
		if (!useDicomDir) {
			String args[] = { "-b", currentAet + "@" + currentHost + ":" + currentPort,
//							"--all-storage",
					"--graphy-storage-dir", dbdir,
					/* FindSCU response value settings */
					// https://groups.google.com/forum/#!searchin/dcm4che/findscu%7Csort:date/dcm4che/fTqRuXhIGjU/dazOWsUvEQAJ
					"--record-config", recFac.getAbsolutePath(), "--ae-config", ae_prop.getAbsolutePath() };
			dcmqrscp.start(args);
		} else {
			/* dicomdir mode : debug purpose */
			String dicomDirPath = dbdir + File.separator + "DICOMDIR";
			String args[] = { "-b", currentAet + "@" + currentHost + ":" + currentPort,
//							"--all-storage",
					"--dicomdir", dicomDirPath, "--ae-config", aeProp };
			dcmqrscp.start(args);
		}
		// Windows only, best-effort: open the firewall for whatever port is actually
		// configured here (the user can change it via PACSConnectionPrefs, so this must
		// not be hardcoded to the 11112 default).
		com.vis.core.util.FirewallConfigurator.ensureDicomPortOpen(currentPort);

		// ★ DICOMweb(QIDO-RS/WADO-RS/STOW-RS)サーバー。DIMSEとは別ポート、明示的な有効化が必要(既定は無効)。
		if (dicomWebServer != null) {
			dicomWebServer.stop();
			dicomWebServer = null;
		}
		String[] webDetails = getDicomWebListenerDetails();
		boolean webEnabled = webDetails != null && Boolean.parseBoolean(webDetails[0]);
		if (webEnabled) {
			int webPort = Integer.parseInt(webDetails[1]);
			if (webPort > 0) {
				try {
					dicomWebServer = new com.vis.dicom.web.DicomWebServer(webPort, webDetails[2]);
					dicomWebServer.start();
					com.vis.core.util.FirewallConfigurator.ensureDicomPortOpen(String.valueOf(webPort));
				} catch (IOException e) {
					logger.log(Level.SEVERE, "DICOMwebサーバーの起動に失敗しました(port=" + webPort + ")。", e);
					dicomWebServer = null;
				}
			} else {
				logger.info("DICOMwebサーバーは無効化されています(ポート未設定)。");
			}
		} else {
			logger.fine("DICOMwebサーバーは設定で無効化されています(dicomweb_enabled=false)。");
		}
	}

	/**
	 * 
	 * @param pk          : primary key position in table. use :
	 *                    getPrimaryKeyIndexInTable().
	 * @param presetModel
	 */
	public void updatePreset(int pk, PresetModel presetModel) {
		String sql = "update presets set presetname=?, windowwidth=?, windowlevel=?, lut=?, modality=? where pk=?";
		try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, presetModel.getPresetName());
			ps.setBigDecimal(2, presetModel.getWW());
			ps.setBigDecimal(3, presetModel.getWL());
			ps.setString(4, presetModel.getLUT());
			ps.setString(5, presetModel.getModality().name());
			ps.setInt(6, pk);
			ps.executeUpdate();
//			conn.createStatement()
//					.execute("update presets set presetname='" + presetModel.getPresetName() + "',windowwidth="
//							+ presetModel.getWindowWidth() + ",windowlevel=" + presetModel.getWindowLevel()
//							+ " where pk=" + presetModel.getPk());
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	/**
	 * @deprecated Typo in method name. Use {@link #updateTextAnnotation(ArrayList)} instead.
	 */
	@Deprecated
	public void upadateTextAnnotation(ArrayList<Integer> tags) {
		updateTextAnnotation(tags);
	}

	public void updateTextAnnotation(ArrayList<Integer> tags) {
		String deleteSql = "DELETE FROM textannotation";
		String insertSql = "INSERT INTO textannotation(tag) VALUES(?)";
		try (Connection conn = openConnection()) {
			conn.createStatement().execute(deleteSql);
			try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
				for (Integer tag : tags) {
					ps.setInt(1, tag);
					ps.addBatch();
				}
				ps.executeBatch();
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe("updateTextAnnotation failed: " + ex.getMessage());
		}
	}

	public void update(String tableName, String fieldName, boolean fieldValue, String whereField, String whereValue) {
		try (Connection conn = openConnection();) {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "=" + fieldValue
					+ " where " + whereField + "='" + whereValue + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	/**
	 * @deprecated The {@code whereField} parameter is declared as {@code int} but is used as a
	 *             SQL column name, which will produce an invalid SQL statement (e.g. "WHERE 3='value'").
	 *             Use {@link #update(String, String, int, String, String)} instead.
	 */
	@Deprecated
	public void update(String tableName, String fieldName, int fieldValue, int whereField, String whereValue) {
		logger.warning("update() called with int whereField — this generates invalid SQL. Use update(String,String,int,String,String) instead.");
		try (Connection conn = openConnection();) {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "='" + fieldValue
					+ "' where " + whereField + "='" + whereValue + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void update(String tableName, String fieldName, int fieldValue, String whereField, String whereValue) {
		try (Connection conn = openConnection();) {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "=" + fieldValue
					+ " where " + whereField + "='" + whereValue + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void update(String tableName, String fieldName, java.sql.Date fieldDateValue, String whereField,
			String whereValue) {
		String statement = "UPDATE " + tableName + " SET " + fieldName + "=?" + " WHERE " + whereField + "=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setDate(1, fieldDateValue);
			pstmt.setString(2, whereValue);
			pstmt.executeUpdate();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void update(String tableName, String fieldName, String fieldValue, String whereField, String whereValue) {
		try (Connection conn = openConnection();) {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "='" + fieldValue
					+ "' where " + whereField + "='" + whereValue + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void deletePreset(PresetModel presetModel) {
		int pk = getPrimaryKeyIndexInTable("presets", "presetname", presetModel.getPresetName());
		if (pk == -1) {
			logger.info("This preset is not exists in record.");
		}
		try (Connection conn = openConnection()) {
			conn.createStatement().execute("delete from presets where pk=" + pk);
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void updateDynamicRetrieveTypeStatus(boolean allow) {
		try (Connection conn = openConnection();) {
			conn.createStatement().executeUpdate("update miscellaneous set AllowDynamicRetrieveType=" + allow);
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void updateImageInfo(DicomObject dataset, String filePath, String patientID, String studyUid,
			String seriesUid, boolean saveAsLink) throws Exception {

		String statement = "UPDATE IMAGE ";
		statement = statement + "SET FileStoreUrl=?, isLink=? ";
		statement = statement + "WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(statement);) {
			pstmt.setString(1, filePath);
			pstmt.setBoolean(2, saveAsLink);
			pstmt.setString(3, dataset.getString(Tag.Patient​ID));
			pstmt.setString(4, dataset.getString(Tag.Study​Instance​UID));
			pstmt.setString(5, dataset.getString(Tag.Series​Instance​UID));
			pstmt.setString(6, dataset.getString(Tag.SOP​Instance​UID));
			pstmt.executeUpdate();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe("DatabaseHandler - Unable to save instance information\n" + ex.getMessage());
			ex.printStackTrace();
		}
	}

	public void updateJNLPRetrieveType(String retrieveType) {
		try (Connection conn = openConnection();) {
			conn.createStatement().executeUpdate("update miscellaneous set JNLPRetrieveType='" + retrieveType + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

//	public void updateListener(String aetitle, String port) {
//		try (Connection conn = openConnection();){
//			try(ResultSet rs = conn.createStatement().executeQuery("select pk from listener");){
//				if(rs.next()) {
//					conn.createStatement().executeUpdate(
//							"update listener set aetitle='" + aetitle + "',port='" + port + "', where pk=" + rs.getInt("pk"));
//					conn.commit();
//				}
//			}
//		} catch (SQLException ex) {
//			logger.severe(ex.getMessage());
//		}
//	}

	/**
	 * Listener is the GRAPHY DCMQRSCP.
	 * 
	 * @param aetitle
	 * @param host
	 * @param port
	 * @param storagelocation
	 */
	public void updateListener(String aetitle, String host, String port, String storagelocation) throws SQLException {

		// 1. UPDATE文を修正 (whereの前にカンマは不要)
		// 「常に1行」という前提なので、WHERE句なしでテーブル全体(つまりその1行)を更新。
		// もし特定の行を更新したい場合は、"WHERE pk = 1" のようにWHERE句を追加してください。
		String sql = "UPDATE listener SET aetitle = ?, host = ?, port = ?, storagelocation = ?";

		// 2. try-with-resources でリソースを管理 (ネストを解消)
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			// 3. パラメータを正しい順序でセット
			pstmt.setString(1, aetitle);
			pstmt.setString(2, host);
			pstmt.setString(3, port);
			pstmt.setString(4, storagelocation);
			// 4. UPDATEを実行
			int affectedRows = pstmt.executeUpdate();
			if (affectedRows == 0) {
				// 更新対象のレコードが存在しなかった場合（想定外の状況）
				// 警告を出すか、エラーとして処理を中断させます。
				throw new SQLException("listenerテーブルの更新対象レコードが見つかりませんでした。");
			}
			// 5. トランザクションを一度だけコミット
			conn.commit();
			logger.fine("listenerテーブルを正常に更新しました。");
		} catch (SQLException e) {
			// 6. エラーをログに記録し、例外を再スローして呼び出し元に通知
			logger.log(Level.SEVERE, "listenerテーブルの更新中にエラーが発生しました。", e);
			throw e;
		}
	}

	/**
	 * GRAPHY自身のローカルDICOMweb(QIDO/WADO/STOW)サーバー設定を取得する。
	 * SERVERSテーブルのwadocontext/wadoport/wadoprotocol(リモートサーバー用、未使用)とは無関係。
	 *
	 * @return [enabled("true"/"false"), port, contextPath]。取得失敗時はnull。
	 */
	public String[] getDicomWebListenerDetails() {
		String statement = "SELECT dicomweb_enabled, dicomweb_port, dicomweb_contextpath FROM LISTENER";
		String[] detail = null;
		try (Connection conn = openConnection();
				PreparedStatement ps = conn.prepareStatement(statement);
				ResultSet rs = ps.executeQuery();) {
			if (rs.next()) {
				detail = new String[3];
				detail[0] = String.valueOf(rs.getBoolean("dicomweb_enabled"));
				int port = rs.getInt("dicomweb_port");
				detail[1] = rs.wasNull() ? "0" : String.valueOf(port);
				String contextPath = rs.getString("dicomweb_contextpath");
				detail[2] = contextPath != null ? contextPath : "/dicomweb";
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		return detail;
	}

	/**
	 * GRAPHY自身のローカルDICOMwebサーバー設定を更新する。
	 *
	 * @param enabled     trueならDICOMwebサーバーを起動する
	 * @param port        DICOMwebサーバーのポート(DIMSEのportとは別)
	 * @param contextPath 例: "/dicomweb"
	 */
	public void updateDicomWebListener(boolean enabled, int port, String contextPath) throws SQLException {
		String sql = "UPDATE listener SET dicomweb_enabled = ?, dicomweb_port = ?, dicomweb_contextpath = ?";
		try (Connection conn = openConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setBoolean(1, enabled);
			pstmt.setInt(2, port);
			pstmt.setString(3, contextPath);
			int affectedRows = pstmt.executeUpdate();
			if (affectedRows == 0) {
				throw new SQLException("listenerテーブルの更新対象レコードが見つかりませんでした。");
			}
			conn.commit();
			logger.fine("listenerテーブルのDICOMweb設定を正常に更新しました。");
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "listenerテーブルのDICOMweb設定更新中にエラーが発生しました。", e);
			throw e;
		}
	}

	public void updateModalitiesStatus(String modality, boolean status) {
		try (Connection conn = openConnection();) {
			conn.createStatement()
					.execute("update modality set status=" + status + " where shortname='" + modality + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	/**
	 * 
	 * @param roiId
	 * @param name
	 * @param roiType
	 * @param originX
	 * @param originY
	 * @param w
	 * @param h
	 * @param pointX
	 * @param pointY
	 * @param shapeArray
	 * @param instNo
	 * @param roiGroup
	 * @param roilbl
	 * @param objType
	 * @param organ
	 * @param desc
	 * @param studyDate
	 * @param crossSection
	 * @param jsonProperties String jsonProperties = "";
	 *                       if(roiCon.get(ContextKey.RoiProperties) != null)
	 *                       { @SuppressWarnings("unchecked") Map<String, String>
	 *                       metaAttributes = (Map<String,
	 *                       String>)roiCon.get(ContextKey.RoiProperties); // 3.
	 *                       Gsonを使って Map -> JSON文字列 に変換 Gson gson = new Gson();
	 *                       jsonProperties = gson.toJson(metaAttributes); }
	 * @param pid
	 * @param studyUid
	 * @param seriesUid
	 * @param sopUid
	 */
	public void updateRoiInfo(String roiId, String name, int roiType, int originX, int originY, int w, int h,
			double[] pointX, double[] pointY, double[] shapeArray, int instNo, int roiGroup, String roilbl,
			String objType, String organ, String desc, java.sql.Date studyDate, String crossSection,
			String jsonProperties, String pid, String studyUid, String seriesUid, String sopUid/*dummy*/) {

		// get as byte
		byte[] byteArrayX = null;
		byte[] byteArrayY = null;
		byte[] byteArrayShape = null;
		if (pointX != null) {
			ByteBuffer bbX = ByteBuffer.allocate(pointX.length * 8);
			for (int i = 0; i < pointX.length; i++) {
				bbX.putDouble(pointX[i]);
			}
			byteArrayX = bbX.array();
		}
		if (pointY != null) {
			ByteBuffer bbY = ByteBuffer.allocate(pointY.length * 8);
			for (int i = 0; i < pointY.length; i++) {
				bbY.putDouble(pointY[i]);
			}
			byteArrayY = bbY.array();
		}
		if (shapeArray != null) {
			ByteBuffer bbS = ByteBuffer.allocate(shapeArray.length * 8);
			for (int i = 0; i < shapeArray.length; i++) {
				bbS.putDouble(shapeArray[i]);
			}
			byteArrayShape = bbS.array();
		}
		try (Connection conn = openConnection();) {
			String statement = "UPDATE ROI ";// need space at end
			statement = statement
					+ "SET Name=?, RoiType=?, OriginX=?, OriginY=?, Width=?, Height=?, PointX=?, PointY=?, Shape=?, InstanceNo=?, Description=?, RoiGroup=?, RoiLabel=?, ObjectType=?, Organ=?, StudyDate=?, CrossSection=? , RoiMetaProperties=? "; // trailing space required
			statement = statement
					+ "WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND RoiID=?"; // Bug fix: missing space before WHERE caused SQL syntax error
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, name);
			pstmt.setInt(2, roiType);
			pstmt.setInt(3, originX);
			pstmt.setInt(4, originY);
			pstmt.setInt(5, w);
			pstmt.setInt(6, h);
			pstmt.setBlob(7, byteArrayX != null ? new ByteArrayInputStream(byteArrayX) : null,
					byteArrayX != null ? byteArrayX.length : 0);
			pstmt.setBlob(8, byteArrayY != null ? new ByteArrayInputStream(byteArrayY) : null,
					byteArrayY != null ? byteArrayY.length : 0);
			pstmt.setBlob(9, byteArrayShape != null ? new ByteArrayInputStream(byteArrayShape) : null,
					byteArrayShape != null ? byteArrayShape.length : 0);
			pstmt.setInt(10, instNo);
			pstmt.setString(11, desc);
			pstmt.setInt(12, roiGroup);
			pstmt.setString(13, roilbl);
			pstmt.setString(14, objType);
			pstmt.setString(15, organ);
			pstmt.setDate(16, studyDate);
			pstmt.setString(17, crossSection);
			pstmt.setString(18, jsonProperties);
			pstmt.setString(19, pid);
			pstmt.setString(20, studyUid);
			pstmt.setString(21, seriesUid);
//			pstmt.setString(x, sopUid);//利用しない（multi stack対応）
			pstmt.setString(22, roiId);
			com.vis.core.log.Log.logger.info("[DEBUG-3: DB] Executing UPDATE for RoiID=" + roiId);

			int affectedRows = pstmt.executeUpdate();
			pstmt.close();
			conn.commit();

			// --- 検証ログ 3-2 ---
			com.vis.core.log.Log.logger.info("[DEBUG-3: DB] UPDATE Success! Affected Rows=" + affectedRows);
			// -----------------
		} catch (SQLException ex) {
			logger.severe("DatabaseHandler - Unable to update roi information\n" + ex.getMessage());
            ex.printStackTrace(); // スタックトレースも出力して原因を特定
		}
	}

	public boolean updateServer(HashMap<String, Object> newServerModelMaterial, String prevNickName) {
		boolean duplicate = false;
		try (Connection conn = openConnection();) {
			conn.createStatement()
					.executeUpdate("update servers set logicalname='" + newServerModelMaterial.get("nickname")
							+ "',aetitle='" + newServerModelMaterial.get("aet") + "',hostname='"
							+ newServerModelMaterial.get("hostname") + "',port=" + newServerModelMaterial.get("port")
							+ ",retrievetype='" + newServerModelMaterial.get("retrievetype") + "',wadocontext='"
							+ newServerModelMaterial.get("wadocontext") + "',wadoport="
							+ newServerModelMaterial.get("wadoport") + ",wadoprotocol='"
							+ newServerModelMaterial.get("wadoprotocol") + "',retrievets='"
							+ newServerModelMaterial.get("retrievets") + "' where pk="
							+ getCommunicationServerPk(prevNickName));
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			duplicate = true;// fail safe
		}
		return duplicate;
	}

	public void updateStudy(String studyUid) {
		try (Connection conn = openConnection();) {
			conn.createStatement()
					.execute("update study set DownloadStatus=true,NoOfInstances=" + getNumOfInstancesInStudy(studyUid)
							+ ",NoOfSeries=" + getNumOfSeriesInStudy(studyUid) + " where StudyInstanceUID='"
							+ studyUid + "'"); // Bug fix: NoOfSeries was calling getNumOfInstancesInStudy
			conn.createStatement()
					.execute("update image set ThumbnailStatus=true where StudyInstanceUID='" + studyUid + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void updateTheme(String themeName) {
		try (Connection conn = openConnection();) {
			try (ResultSet activeInfo = conn.createStatement()
					.executeQuery("select name from theme where status=true");) {
				if (activeInfo.next()) {
					conn.createStatement().executeUpdate(
							"update theme set status=false where name='" + activeInfo.getString("name") + "'");
					conn.createStatement().executeUpdate("update theme set status=true where name='" + themeName + "'");
				}
			}
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}

	public void updateThumbnailStatus(String studyUid, String seriesUid, String sopUid) {
		try (Connection conn = openConnection();) {
			conn.createStatement().executeUpdate("update image set ThumbnailStatus=true where StudyInstanceUID='"
					+ studyUid + "' and SeriesInstanceUID='" + seriesUid + "' and SOPInstanceUID='" + sopUid + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
	}
	
	/**
	 * create study as new patient all UIDs are primary-key, so not allow same id
	 * already existing in DB. all UIDs are replaced.
	 * 
	 * @param noDuplicatedImages
	 * @param patInfoMap
	 * @param setNewInstanceUID
	 */
	public void updatePatientInformationAndStore2DB(ArrayList<String[]> noDuplicatedImages,
			HashMap<String, String> patInfoMap) {
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db == null) {
			return;
		}
		if (noDuplicatedImages == null || noDuplicatedImages.size() < 1) {
			return;
		}
		if (patInfoMap == null) {
			return;
		}

		Path tempParent = null;
		try {
			tempParent = Files.createTempDirectory(null);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Cannot create TempDir to create duplicate dcm files.");
			return;
		}
		File tempDir = tempParent.toFile();
		
		DICOMBackend backend = null;
		try {
			backend = DICOMBackend.getCurrent();
		} catch (Exception e1) {
			backend = DICOMBackend.DCM4CHE;
		}
		
		// collect studyUIDs
		String[] studyUIDs = getNoDuplicatedIDs(noDuplicatedImages, "STUDY");
		for (String studyUID : studyUIDs) {
			String newStudyUID = DBUtils.createNewUIDNoExistingInDB("STUDY");
			//collect seriesUIDs
			String[] seriesUIDs = getNoDuplicatedIDs(noDuplicatedImages, "SERIES");
			for (String seriesUID : seriesUIDs) {
				String newSeriesUID = DBUtils.createNewUIDNoExistingInDB("SERIES");
				for (String[] idset : noDuplicatedImages) {
					if (idset[1].equals(studyUID) && idset[2].equals(seriesUID)) {
						String newSopInstUID = DBUtils.createNewUIDNoExistingInDB("IMAGE");
						// org
						//String pid = idset[0];
						String sopUID = idset[3];

						String orgPath = db.getFileLocation(studyUID, seriesUID, sopUID);
						DicomReader dr = DicomReader.newDicomReader(backend); 
						dr.read(orgPath, true);// with pixel
						
						DicomObject orgDcm = dr.getHeader();
						String tsUID = dr.checkTSUID().uid();

						String newPID = patInfoMap.get("PatientID") != null ? patInfoMap.get("PatientID").trim() : "";
						String newPNAME = patInfoMap.get("PatientName") != null ? patInfoMap.get("PatientName").trim() : "";
						String newBOD = patInfoMap.get("PatientBirthDate") != null ? patInfoMap.get("PatientBirthDate").trim().replace("/", "") : "";
						String newSex = patInfoMap.get("PatientSex") != null ? patInfoMap.get("PatientSex").trim() : "";
						if (newPID.isEmpty()) {
							logger.warning("updatePatientInformationAndStore2DB: PatientID is null/empty in patInfoMap.");
						}
						
						orgDcm.setString(Tag.Patient​ID, VR.LO, newPID);
						orgDcm.setString(Tag.Patient​Name, VR.PN, newPNAME);
						orgDcm.setDate(Tag.Patient​Birth​Date, VR.DA, DateUtils.toSQLDateObj(newBOD));
						orgDcm.setString(Tag.Patient​Sex, VR.CS, newSex);
						orgDcm.setString(Tag.Study​Instance​UID, VR.UI, newStudyUID);
						orgDcm.setString(Tag.Series​Instance​UID, VR.UI, newSeriesUID);
						orgDcm.setString(Tag.SOP​Instance​UID, VR.UI, newSopInstUID);
						orgDcm.setString(Tag.Media​Storage​SOP​Instance​UID, VR.UI, newSopInstUID);
						/*
						 * write
						 */
						String dest = tempDir.getAbsolutePath() + File.separator + "dup_" + sopUID + ".dcm";
						DicomWriter writer = DicomWriter.newDicomWriter(backend);
						writer.write(orgDcm,  tsUID, dest);
						/*
						 * send to graphy refresh table load image
						 */
						DimseUtilities.store(dest, false/*deleteAfterStored*/);
						
					} else {
						continue;
					}
				}
			}
		}
		try {
			FileUtils.deleteDirectory(tempDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Window win = WindowManager.getMainScreen();
		if(win !=null) {
			MainScreen main = (MainScreen) win;
			main.loadLocalStudiesBySearchKey();
		}
	}
	
	/**
	 * 
	 * @param instanceUIDSets: array of [pid, studyUid, seriesUid, sopUid]
	 * @param dcmLevel: patient study series image
	 * @return
	 */
	static String[] getNoDuplicatedIDs(ArrayList<String[]> instanceUIDSets, String dcmLevel) {
		dcmLevel = dcmLevel.toLowerCase();
		HashSet<String> ids = new HashSet<>();
		// idset:pid,studyuid,seriesuid,sopuid
		for (String[] idset : instanceUIDSets) {
			if (dcmLevel.equals("patient")) {
				ids.add(idset[0]);
			} else if (dcmLevel.equals("study")) {
				ids.add(idset[1]);
			} else if (dcmLevel.equals("series")) {
				ids.add(idset[2]);
			} else if (dcmLevel.equals("image")) {
				ids.add(idset[3]);
			}
		}
		return ids.toArray(new String[ids.size()]);
	}

	public synchronized boolean writeDatasetInfo(DicomObject dataset, String filePath) {
		boolean overWrite = overWriteSavedAsLinkRecord(dataset, saveAsLink/* false */);
		if (!checkCanImport(dataset) && !overWrite) {
			return false;
		}
		try {
			insertPatientInfo(dataset);
			insertStudyInfo(dataset, saveAsLink, dataset.getString(Tag.Patient​ID));
			insertSeriesInfo(dataset, dataset.getString(Tag.Patient​ID), dataset.getString(Tag.Study​Instance​UID),
					saveAsLink);
			if (!overWrite) {
				insertImageInfo(dataset, filePath, dataset.getString(Tag.Patient​ID),
						dataset.getString(Tag.Study​Instance​UID), dataset.getString(Tag.Series​Instance​UID),
						saveAsLink);
			} else {
				updateImageInfo(dataset, filePath, dataset.getString(Tag.Patient​ID),
						dataset.getString(Tag.Study​Instance​UID), dataset.getString(Tag.Series​Instance​UID),
						saveAsLink);
			}
			return true;
		} catch (Exception e) {
			logger.severe("DatabaseHandler - Failed to update patient information\n" + e.getMessage());
		}
		return false;
	}
	
	public void writeToDB(HashMap<Integer, DicomImage> dcms) {
		
	}
}
