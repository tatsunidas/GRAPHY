package com.vis.db;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.swing.tree.DefaultMutableTreeNode;

import org.apache.derby.jdbc.EmbeddedDataSource;
import com.vis.core.log.Log;
import com.vis.core.util.DateUtils;
import com.vis.core.util.Platform;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomCommunicationNode;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.dimse.DcmQRSCP;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;

/**
 * 
 * DatabaseHandler is a GRAPHY DB.
 * DatabaseHandler has main two servers.
 * - derby : local db used to any tables and communicate with dcmqrscp.
 * - DicomServer : dcmqrscp.
 *  
 * @author tatsunidas
 */
public class DatabaseHandler {
	
	/*
	 * unit test
	 */
	public static void main(String[] args) {
		
		String testDir = "/home/tatsunidas/デスクトップ/graphy/";
		DatabaseHandler db = new DatabaseHandlerBuilder().build();
		db.setDatabaseFolderPath(testDir);
		db.startingUp();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		db.shutdownDB();
	}
	
	//singleton
	private static DatabaseHandler datbaseRef;
	
	//location
	private String dbdir = "";//keep blank. db folder, without databasename.

	// derby
	private EmbeddedDataSource derby;
	private final String protocol = "jdbc:derby:";//connectionURL
	private final String driverName = "org.apache.derby.jdbc.EmbeddedDriver";
	private final String	databasename = "graphydb";//will become db folder name
	private final String username = "graphy";
	private final String password = "graphy-mtfbwy";
	
	// dcmqrscp
	private DicomServer dcmqrscp;
	public final String defaultAET = "GRAPHY";
	public final String defaultHost = "localhost";
	public final String defaultPort = "4891";//for dimse, 
	private boolean useDicomDir = false;
	private String recordFactoryPath = new File("./conf/RecordFactory.xml").getAbsolutePath();
    /* ae.properties for dicomdir mode */
    private String aeProp = new File("./conf/ae.properties").getAbsolutePath();
	
		
	/*
	 * DICOM "TM" format consists of a string of characters of the format hhmmss.frac;
	 * kkmmss.SSS ->  see, https://docs.oracle.com/javase/jp/8/docs/api/java/time/format/DateTimeFormatter.html
	 * where hh contains hours (range "00" - "23"), mm contains minutes (range "00" - "59"), 
	 * ss contains seconds (range "00" - "59"), 
	 * and frac contains a fractional part of a second as small as 1 millionth of a second (range 000000 - 999999).
	 */
	private DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
	//use kk instead HH for represent 24 hour.
	//DICOM TM format represent time in kk:mm:ss.SSS000. Here, last "000" is padding vales to represent milisec in 6 digits.
	//DO NOT USE kkmmss.SSSSSS. This occurs 000SSS nor SSS000(DICOM form).
	//see also DateUtils.
	private DateFormat timeFormat = new SimpleDateFormat("kk:mm:ss.SSS");//use kk instead HH for represent 24 hour.
	
	private boolean saveAsLink = false;
	
	private java.util.logging.Logger logger = Log.logger;
	
	/**
	 * Builder
	 * 
	 * Here, we would like to use just embedded data source.
	 * EmbeddedDataSource does not need ip:port properties.
	 * 
	 * @author tatsunidas
	 *
	 */
	public static class DatabaseHandlerBuilder{
		
		public DatabaseHandlerBuilder() {}
		
		public DatabaseHandler build() {
			return new DatabaseHandler(this);
		}
	}
	
	private DatabaseHandler(DatabaseHandlerBuilder builder) {
		datbaseRef = this;
	}
	
	/* to use when starting graphy */
	public static DatabaseHandler getInstance() {
		return datbaseRef;
	}
	
	public void setSaveAsLinkState(boolean bool) {
		this.saveAsLink = bool;
	}
	
	public String getUserName() {
		return username;
	}
	
	public String getPassword() {
		return password;
	}
	
	public String getProtocolName() {
		return protocol;
	}
	
	public String getDriverName() {
		return driverName;
	}
	
	public String getDatabaseName() {
		return databasename;
	}
	
	public EmbeddedDataSource getEmbeddedDataSource() {
		return derby;
	}
	
	public String getDatabaseFolderPath(boolean withDatabaseNameFolder) {
		if(withDatabaseNameFolder) {
			return this.dbdir + File.separator + databasename;
		}else {
			return this.dbdir;
		}
	}
	
	/**
	 * 
	 * @param p : parent folder path of db (without databasename).
	 */
	private void setDatabaseFolderPath(String p) {
		this.dbdir = p;
	}
	
	private EmbeddedDataSource makeDataSource(String graphydir, boolean create) throws Throwable {
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
	 * 
	 * @param dbdir : parent folder path of db (without databasename).
	 * @return exist or not
	 */
	public boolean checkDBExists(String dbdir) {
		Connection con = openConnection();
		if (con != null) {
			try (con) {
				con.close();
			} catch (SQLException e) {
				logger.severe("connection can not established...");
			}
			return true;
		} else {
			return false;
		}
	}

	public void loadLocalDBLocation() throws Exception {
		if(dbdir != null && !dbdir.isBlank()) {
			return;
		}
		try {
			Properties prop = PropertiesUtil.loadProperties(ConfigInfo.GRAPHY_Props.toString());
			if(prop == null) {
				throw new Exception("Can not load graphy.properties...");
			}else {
				String loc = prop.getProperty(GraphyProp.LocalDBLocation.name());
				if(loc == null || loc.isBlank()) {
					loc = Platform.getGraphyDirectory().getAbsolutePath();
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.LocalDBLocation.name(), loc);
				}
				setDatabaseFolderPath(loc);
			}
		} catch (Exception e) {
			logger.severe("can not find graphy.properties::DatabaseHandler::loadDBLocationFromProp");
			return;
		} 
	}
	
	private Set<String> getDBTable(Connection targetDBConn) throws SQLException {
		Set<String> set = new HashSet<String>();
		DatabaseMetaData dbmeta = targetDBConn.getMetaData();
		readDBTable(set, dbmeta, "TABLE", null);
		return set;
	}

	private void readDBTable(Set<String> set, DatabaseMetaData dbmeta, String searchCriteria, String schema)
			throws SQLException {
		ResultSet rs = dbmeta.getTables(null, schema, null, new String[] { searchCriteria });
		while (rs.next()) {
			set.add(rs.getString("TABLE_NAME"));
		}
	}
	
	private boolean patientTableAlreadyExists() {
		if(derby == null) {
			checkDBExists(dbdir);
		}
		try {
			Set<String> tbl = getDBTable(openConnection());
			if(tbl.size() == 0) {
				tbl = null;
				return false;
			}else {
				tbl = null;
				return true;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public void startingUp() {
		
		try {
			loadLocalDBLocation();
			logger.info("Current DB location: "+dbdir);
			System.setProperty("derby.system.home", dbdir);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(derby == null) {
			try {
				makeDataSource(dbdir, true);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		
		boolean dbExists = checkDBExists(dbdir);
		try {
			if (dbExists && !patientTableAlreadyExists()) {
				createTables();
				insertDefaultListenerDetails();
				insertModalities();
				insertDefaultPresets();
				insertDefaultLocales();
				insertDefaultTextAnnotationList();
			}
		} catch (SQLException e) {
			logger.severe("Can not start DB because can not read SQL files ...");
			shutdownDB();
		}
		
		//check configuration file
		File recFac = new File(ConfigInfo.getPath(ConfigInfo.RecordFactory));
		if(!recFac.exists()) {
			try {
				File defRecFac = new File(Resources.RecordFactory.toURL().toURI());
				new File(ConfigInfo.getPath(ConfigInfo.ConfDirName)).mkdirs();
				Path src = Paths.get(defRecFac.toURI());
				Path out = Paths.get(recFac.toURI());
				Files.copy(src, out);
				recFac = new File(ConfigInfo.getPath(ConfigInfo.RecordFactory));
			} catch (URISyntaxException | IOException e1) {
				e1.printStackTrace();
				logger.severe(e1.getMessage());
				shutdownDB();
				return;
			}
		}
		
		//start qrscp
		String details[] = getListenerDetails();
    	String currentAet = details[0];
    	String currentHost = details[1];
    	String currentPort = details[2];
    	String currentStorageDirPath = details[3];
		try {
			dcmqrscp = new DcmQRSCP();
			if (!useDicomDir) {
				String args[] = { "-b", currentAet + "@" + currentHost + ":" + currentPort,
//						"--all-storage",
						"--graphy-storage-dir", currentStorageDirPath,
						/* FindSCU response value settings */
						// https://groups.google.com/forum/#!searchin/dcm4che/findscu%7Csort:date/dcm4che/fTqRuXhIGjU/dazOWsUvEQAJ
						"--record-config", recFac.getAbsolutePath()};
				dcmqrscp.start(args);
			} else {
				/* dicomdir mode : debug purpose */
				String dicomDirPath = details[3];
				String args[] = { "-b", currentAet + "@" + currentHost + ":" + currentPort,
//						"--all-storage",
						"--dicomdir", dicomDirPath,
						"--ae-config", aeProp };
				dcmqrscp.start(args);
			}
		} catch (IOException e) {
			e.printStackTrace();
			shutdownDB();
		}
	}
	
	/**
	 * auto commit basis handling
	 * @return Connection
	 */
	private Connection openConnection() {
		if(derby == null) {
			// logger.warning("Should be makeDataSource() first before open Connection.");
			return null;
		}
		/* Open connection */
		try {
			Connection conn = derby.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			conn.setAutoCommit(true);//auto commit basis handling
			return conn;
		} catch (SQLException e) {
			logger.severe("connection can not established...");
			// exitApp();
		}
		return null;
	}
	
	public boolean isConnectionStillActive(Connection conn) throws Exception {
		try {
			if(conn == null || conn.isClosed()) {
				return false;
			}else {
				return true;
			}
		} catch (SQLException e) {
			logger.severe(e.getMessage());
			throw new Exception(e.getMessage());
		}
	}
	
	public void safeClose(Connection conn){
		if(conn == null) return;
		try(conn){
			if(!isConnectionStillActive(conn)) {
				return;
			}else {
				try {
					conn.commit();
				} catch (SQLException e) {
					conn.rollback();
					throw e;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void shutdownDB() {
		if(derby == null && dcmqrscp == null) {
			return;
		}
		try {
			derby.setShutdownDatabase("shutdown");
			derby.getConnection();
		} catch (SQLException e) {
			boolean gotSQLExc = false;
			if (e.getSQLState().equals("XJ015") || "08006".equals(e.getSQLState())) {
				gotSQLExc = true;
			}
			if (!gotSQLExc) {
				logger.info(getClass().getName() + ":Invalid shutdown...");
			} else {
				logger.info(getClass().getName() + ": shutdown correctly, " + databasename);
			}
			derby = null;
			if(dcmqrscp != null) {
				dcmqrscp.stop();
				dcmqrscp = null;
			}
		}
	}

	private void createTables() {
		Connection conn = openConnection();
		if(conn == null) {
			return;
		}
		try (Statement statement = conn.createStatement(); conn) {
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_PATIENT.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_STUDY.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_SERIES.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_IMAGE.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_LISTENER.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_AE.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_THEME.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_MODALITY.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_PRESET.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_LOCALE.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_MISCELLANEOUS.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_TEXTANNOTATION.toURL().getPath())
					.get(0));
			statement.executeUpdate(new SQLReader()
					.createQueries(Resources.SQL_ROI.toURL().getPath())
					.get(0));
		} catch (SQLException ex) {
			logger.severe("DatabaseHandler, can not read SQL correctly..\n"+ex.getMessage());
		}
	}

	public boolean checkRecordExists(String tablename, String fieldname, String compareWith) {
		Connection conn = openConnection();
		boolean found = false;
		try {
//			String sql = "SELECT COUNT(*) FROM ? WHERE "+fieldname+"=?";
			String sql = "select count(" + fieldname + ") from " + tablename
					+ " where " + fieldname + " = '" + compareWith.trim() + "'";
			PreparedStatement pstmt = conn.prepareStatement(sql);
//			ResultSet rs = conn.createStatement().executeQuery("select count(" + fieldname + ") from " + tablename
//					+ " where " + fieldname + " = '" + compareWith.trim() + "'");
			ResultSet rs = pstmt.executeQuery();
			rs.next();
			if (rs.getInt(1) > 0) {
				found = true;
			}
			pstmt.close();
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return found;
		}finally {
			safeClose(conn);
		}
		return found;
	}
	
	public boolean checkStudyRecordExists(String patID, String studyIUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			String statement = "SELECT * FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);
			if(rset.next()) {
				return true;
			}
		} catch (Exception e) {
			return false;
		}finally {
			if(rset != null) {
				try {
					rset.close();
					pstmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			safeClose(conn);
		}
		return false;
	}
	
	public boolean checkSeriesRecordExists(String patID, String studyIUID, String seriesIUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			String statement = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, seriesIUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);
			if(rset.next()) {
				return true;
			}
		} catch (Exception e) {
			return false;
		}finally {
			if(rset != null) {
				try {
					pstmt.close();
					rset.close();
					safeClose(conn);
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}
	
	public boolean checkImageRecordExists(String patID, String studyIUID, String seriesIUID, String sopIUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, seriesIUID);
			pstmt.setString(4, sopIUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);
			if(rset.next()) {
				return true;
			}
		} catch (Exception e) {
			return false;
		}finally {
			if(rset != null) {
				try {
					pstmt.close();
					rset.close();
					safeClose(conn);
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return false;
	}
	
	private String getLocalDBLocation() {
		return dbdir;
	}
	
	private String getArchiveDirectory() {
		return getLocalDBLocation() + File.separator + "archive";
	}

	private void insertDefaultListenerDetails() {
		Connection conn = openConnection();
		String sql = "insert into listener(aetitle,host,port,storagelocation) values( ? , ? , ? , ?)";
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, defaultAET);
			pstmt.setString(2, defaultHost);
			pstmt.setString(3, defaultPort);
			pstmt.setString(4, getArchiveDirectory());
			pstmt.executeUpdate();//int num = pstmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}finally {
			try {
				pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			safeClose(conn);
		}
	}

	private void insertModalities() throws SQLException {
		Connection conn = openConnection();
		String modality[] = { "CT", "MR", "XA", "CR", "SC", "NM", "RF", "DX", "US", "PX", "OT", "DR", "SR", "MG",
				"RG" };
		for (int i = 0; i < modality.length; i++) {
			conn.createStatement().execute(
					"insert into modality(logicalname,shortname,status) values('Dummy','" + modality[i] + "',true)");
		}
		safeClose(conn);
	}

	private void insertDefaultPresets() throws SQLException {
		Connection conn = openConnection();
		ResultSet rs = conn.createStatement().executeQuery("select pk from modality where shortname='CT'");
		rs.next();
		int pk = rs.getInt("pk");
		conn.createStatement().execute(
				"insert into presets(presetname,windowwidth,windowlevel,modality_fk)values('CT Abdomen',40,350," + pk
						+ ")");
		conn.createStatement().execute(
				"insert into presets(presetname,windowwidth,windowlevel,modality_fk)values('CT Lung',1500,-600," + pk
						+ ")");
		conn.createStatement()
				.execute("insert into presets(presetname,windowwidth,windowlevel,modality_fk)values('CT Brain',80,40,"
						+ pk + ")");
		conn.createStatement()
				.execute("insert into presets(presetname,windowwidth,windowlevel,modality_fk)values('CT Bone',2500,480,"
						+ pk + ")");
		conn.createStatement().execute(
				"insert into presets(presetname,windowwidth,windowlevel,modality_fk)values('CT Head/Neck',350,90," + pk
						+ ")");
		rs.close();
		safeClose(conn);
	}

	public void insertServer(String nickname, String aet, String hostname, int port, String ciphers) {
		Connection conn = openConnection();
		String statement = "INSERT INTO SERVERS (pk,logicalname,aetitle,hostname,port,ciphers,retrievetype,wadocontext,wadoport,wadoprotocol,retrievets) VALUES (default,?,?,?,?,?,?,?,?,?,?)";
		PreparedStatement insertStmt = null;
		try {
			insertStmt = conn.prepareStatement(statement,Statement.RETURN_GENERATED_KEYS);
			insertStmt.setString(1, nickname);
			insertStmt.setString(2, aet);
			insertStmt.setString(3, hostname);
			insertStmt.setInt(4, port);
			insertStmt.setString(5, ciphers);
			insertStmt.setString(6, null);//RetrieveType()
			insertStmt.setString(7, null);//getWadoURL():wadocontext
			insertStmt.setInt(8, -1);//getWadoPort():-1 is default no port number
			insertStmt.setString(9, null);//getWadoProtocol()
			insertStmt.setString(10, null);//getRetrieveTransferSyntax()
			insertStmt.execute();
			insertStmt.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe("DatabaseHandler:can not read sql...\n"+ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

//	public void insertButton(ButtonsModel buttonsModel) {
//		Connection conn = openConnection();
//		try {
//			ResultSet noInfo = conn.createStatement().executeQuery("select max(buttonno) from buttons");
//			noInfo.next();
//			conn.createStatement().execute(
//					"insert into buttons(buttonno,description,modality,datecriteria,timecriteria,iscustomdate,iscustomtime) values("
//							+ (noInfo.getInt(1) + 1) + ",'" + buttonsModel.getButtonlable() + "','"
//							+ buttonsModel.getModality() + "','" + buttonsModel.getStudyDate() + "','"
//							+ buttonsModel.getStudyTime() + "','" + buttonsModel.isCustomDate() + "','"
//							+ buttonsModel.isCustomTime() + "')");
//			conn.commit();
//			noInfo.close();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			if(open) {
//				safeClose(conn);
//			}
//		}
//	}

//	public void insertPreset(PresetModel presetModel, String modality) {
//		openConnection();
//		try {
//			ResultSet modalityInfo = conn.createStatement()
//					.executeQuery("select pk from modality where shortname='" + modality + "'");
//			modalityInfo.next();
//			conn.createStatement()
//					.execute("insert into presets(presetname,windowwidth,windowlevel,modality_fk)values('"
//							+ presetModel.getPresetName() + "'," + presetModel.getWindowWidth() + ","
//							+ presetModel.getWindowLevel() + "," + modalityInfo.getInt("pk") + ")");
//			modalityInfo.close();
//			conn.commit();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//	}

	public void insertDefaultLocales() throws SQLException {
		Connection conn = openConnection();
		conn.createStatement().execute(
				"insert into locale (countrycode,country,languagecode,language,localeid,status) values('GB','United Kingdom','en','English','en_GB',true)");
		addNewLocale("ta_IN");
		addNewLocale("it_IT");
		conn.commit();
		safeClose(conn);
	}

	private void addNewLocale(String localeid) throws SQLException {
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

	private void insertLocale(String language, String country, String languagecode, String countrycode, String localeid)
			throws SQLException {
		Connection conn = openConnection();
		conn.createStatement()
		.execute("insert into locale(countrycode,country,languagecode,language,localeid,status) values('"
				+ countrycode + "','" + country + "','" + languagecode + "','" + language + "','" + localeid
				+ "',false)");
		safeClose(conn);
	}
	
	public Locale getCurrentLocale() {
		String[] appLocale = getActiveLanguage();
		return new Locale(appLocale[2], appLocale[0]);
	}

	@SuppressWarnings("unused")
	private void insertMiscellaneous() throws SQLException {
		Connection conn = openConnection();
		//tatsu
//		if(conn ==null || conn.isClosed()) {
//			
//			// C-GET to C-MOVE
//			conn.createStatement().execute(
//					"insert into miscellaneous(Loopback,JNLPRetrieveType,AllowDynamicRetrieveType) values(true,'C-MOVE',false)");
//			conn.commit();
//			
//		}else {
//			conn.createStatement().execute(
//					"insert into miscellaneous(Loopback,JNLPRetrieveType,AllowDynamicRetrieveType) values(true,'C-MOVE',false)");
//			conn.commit();
//		}
		safeClose(conn);
	}

	private void insertDefaultTextAnnotationList() throws SQLException {
		Connection conn = openConnection();
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
		for (Integer tag : tags) {
			String sql = "insert into textannotation(tag) values(";
			sql = sql + String.valueOf(tag) + ")";
			conn.createStatement().execute(sql);
			conn.commit();
		}
		safeClose(conn);
	}
	
	public synchronized void insertRoi(HashMap<String,Object> roiCon){
		insertRoi(
				(String)roiCon.get("RoiID"),
				(String)roiCon.get("Name"),
				Integer.parseInt((String)roiCon.get("RoiType")),
				(int)roiCon.get("OriginX"),
				(int)roiCon.get("OriginY"),
				(int)roiCon.get("Width"),
				(int)roiCon.get("Height"),
				(double[])roiCon.get("PointX"),
				(double[])roiCon.get("PointY"),
				(double[])roiCon.get("Shape"),
				Integer.parseInt((String)roiCon.get("InstanceNo")),
				roiCon.get("RoiGroup") == null ? -1:Integer.parseInt((String)roiCon.get("RoiGroup")),
				(String)roiCon.get("RoiLabel"),
				(String)roiCon.get("ObjectType"),
				(String)roiCon.get("Organ"),
				(String)roiCon.get("Description"),
				(String)roiCon.get("PatientID"),
				(String)roiCon.get("StudyInstanceUID"),
				(String)roiCon.get("SeriesInstanceUID"),
				(String)roiCon.get("SOPInstanceUID"));
	}
	
	public void insertRoi(
			String roiId,
			String name,
			int roiType,
			int originX,
			int originY,
			int w,
			int h,
			double[] pointX,
			double[] pointY,
			double[] shapeArray,
			int instNo,
			int roiGroup,
			String roilbl,
			String objType,
			String organ,
			String txt,//description
			String pid,
			String studyUid,
			String seriesUid,
			String sopUid) {
		if(pointX != null && pointY != null) {
			if(pointX.length != pointY.length) {
				System.out.println(getClass().getName()+":Can not save roi, pointXY is incorrect(count mismatch).");
				return;
			}
		}
		if (!(checkRecordExists("roi", "RoiID", roiId))) {
			Connection conn = openConnection();
			//get as byte
			byte[] byteArrayX = null;
			byte[] byteArrayY = null;
			byte[] byteArrayShape = null;
			if (pointX != null) {
				ByteBuffer bbX = ByteBuffer.allocate(pointX.length * 8);
				for (int i = 0; i < pointX.length; i++) {
					bbX.putDouble(pointX[i]);
				}
				//get as byte
				byteArrayX = bbX.array();
			}
			if(pointY != null) {
				ByteBuffer bbY = ByteBuffer.allocate(pointY.length * 8);
				for (int i = 0; i < pointY.length; i++) {
					bbY.putDouble(pointY[i]);
				}
				//get as byte
				byteArrayY = bbY.array();
			}
			if(shapeArray != null) {
				ByteBuffer bbS = ByteBuffer.allocate(shapeArray.length * 8);
				for (int i = 0; i < shapeArray.length; i++) {
					bbS.putDouble(shapeArray[i]);
				}
				//get as byte
				byteArrayShape = bbS.array();
			}
			
			try {
				/*
				 * RoiID varchar(255) NOT NULL CONSTRAINT RoiID_pk PRIMARY KEY, 
				 * Roi name,
				 * RoiType integer,
				 * OriginX integer, 
				 * OriginY integer, 
				 * Width integer, 
				 * Height integer, 
				 * PointX blob,
				 * PointY blob, 
				 * Shape blob,
				 * InstanceNo integer,
				 * int roiGroup,
				 * String roilbl,
				 * String objType,
				 * String organ,
				 * Description
				 * PatientID varchar(255),
				 * StudyInstanceUID varchar(255), 
				 * SeriesInstanceUID varchar(255), 
				 * SOPInstanceUID varchar(255), 
				 */
				PreparedStatement insertStmt = conn.prepareStatement("insert into roi values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
				insertStmt.setString(1, roiId);
				insertStmt.setString(2, name);
				insertStmt.setInt(3, roiType);
				insertStmt.setInt(4, originX);
				insertStmt.setInt(5, originY);
				insertStmt.setInt(6, w);
				insertStmt.setInt(7, h);
				insertStmt.setBlob(8, byteArrayX == null ? null:new ByteArrayInputStream(byteArrayX), byteArrayX == null ? 0: byteArrayX.length);
				insertStmt.setBlob(9, byteArrayY == null ? null:new ByteArrayInputStream(byteArrayY), byteArrayY == null ? 0: byteArrayY.length);
				insertStmt.setBlob(10, byteArrayShape == null ? null:new ByteArrayInputStream(byteArrayShape), byteArrayShape == null ? 0: byteArrayShape.length);
				insertStmt.setInt(11, instNo);
				insertStmt.setInt(12, roiGroup);
				insertStmt.setString(13, roilbl);
				insertStmt.setString(14, objType);
				insertStmt.setString(15, organ);
				insertStmt.setString(16, txt);
				insertStmt.setString(17, pid);
				insertStmt.setString(18, studyUid);
				insertStmt.setString(19, seriesUid);
				insertStmt.setString(20, sopUid);
				insertStmt.execute();
				conn.commit();
				insertStmt.close();
			} catch (SQLException ex) {
				logger.severe("DatabaseHandler - Unable to save patient information\n"+ex.getMessage());
			}finally {
					safeClose(conn);
			}
		//already exists
		}else {
			//updation
			updateRoiInfo(
					roiId,
					name,
					roiType,
					originX,
					originY,
					w, h,
					pointX,
					pointY,
					shapeArray,
					instNo,
					roiGroup,
					roilbl,
					objType,
					organ,
					txt,
					pid, studyUid, seriesUid, sopUid);
		}
	}
		
	public boolean checkCanImport(DicomObject ds) {
		boolean canImport = false;
		String patID = ds.getString(Tag.Patient​ID);
		String studyUID = ds.getString(Tag.Study​Instance​UID);
		String seriesUID = ds.getString(Tag.Series​Instance​UID);
		String sopUID = ds.getString(Tag.SOP​Instance​UID);
		/* check already exists */
		if(checkImageRecordExists(patID, studyUID, seriesUID, sopUID)) {
			return canImport;
		}else {
			canImport = true;
			return canImport;
		}
	}
	
	public boolean overWriteSavedAsLinkRecord(DicomObject ds,boolean saveAsLinkWillImport) {
		boolean overWrite = false;
		String patID = ds.getString(Tag.Patient​ID);
		String studyUID = ds.getString(Tag.Study​Instance​UID);
		String seriesUID = ds.getString(Tag.Series​Instance​UID);
		String sopUID = ds.getString(Tag.SOP​Instance​UID);
		/* check already exists */
		if(checkImageRecordExists(patID, studyUID, seriesUID, sopUID)) {
			/* check existing data is savedAsLink? */
			if(isInstanceSavedAsLink(patID, studyUID, seriesUID, sopUID)) {
				/*Is this import try to save local? */
				if(!saveAsLinkWillImport) {
					overWrite = true;
					return overWrite;
				}
			}
		}else {
			return overWrite;
		}
		return overWrite;
	}

	public synchronized void writeDatasetInfo(DicomObject dataset, String filePath) {
		boolean overWrite = overWriteSavedAsLinkRecord(dataset,saveAsLink);
		if(!checkCanImport(dataset) && !overWrite) {
			return;
		}
		try {
			insertPatientInfo(dataset);
			insertStudyInfo(dataset, saveAsLink, dataset.getString(Tag.Patient​ID));
			insertSeriesInfo(dataset, dataset.getString(Tag.Patient​ID), dataset.getString(Tag.Study​Instance​UID),
					saveAsLink);
			if(!overWrite) {
				insertImageInfo(dataset, filePath, dataset.getString(Tag.Patient​ID),
						dataset.getString(Tag.Study​Instance​UID), dataset.getString(Tag.Series​Instance​UID),saveAsLink);
			}else {
				updateImageInfo(dataset, filePath, dataset.getString(Tag.Patient​ID),
						dataset.getString(Tag.Study​Instance​UID), dataset.getString(Tag.Series​Instance​UID),saveAsLink);
			}
		} catch (Exception e) {
			logger.severe( "DatabaseHandler - Failed to update patient information\n"+e.getMessage());
		}
	}

	private void insertPatientInfo(DicomObject dataset) {
		if (!(checkRecordExists("PATIENT", "PatientID", dataset.getString(Tag.Patient​ID)))) {
			Connection conn = openConnection();
			java.util.Date bod = dataset.getDate(Tag.Patient​Birth​Date);
			java.sql.Date sqlBod = DateUtils.toSQLDateObj(bod);
			try {
				PreparedStatement insertStmt = conn.prepareStatement("insert into patient values(?,?,?,?)");
				insertStmt.setString(1, dataset.getString(Tag.Patient​ID));
				insertStmt.setString(2, dataset.getString(Tag.Patient​Name));
				insertStmt.setDate(3, sqlBod);
				insertStmt.setString(4, dataset.getString(Tag.Patient​Sex));
				insertStmt.execute();
				insertStmt.close();
				conn.commit();//fail safe
			} catch (SQLException ex) {
				logger.severe("DatabaseHandler - Unable to save patient information\n"+ex.getMessage());
			}finally {
				safeClose(conn);
			}
		}
	}

	private void insertStudyInfo(DicomObject dataset, boolean saveAsLink, String patientID) {
		if (!checkRecordExists("STUDY", "StudyInstanceUID", dataset.getString(Tag.Study​Instance​UID))) {
			Connection conn = openConnection();
			try {
				/* Study date */
				java.util.Date date = dataset.getDate(Tag.Study​Date);
				java.sql.Date sqlDate = DateUtils.toSQLDateObj(date);
				
		       /* Study Time */
				java.util.Date time = dataset.getDate(Tag.Study​Time);
				/*ignore milliseconds in db, but it remains in dataset.*/
				java.sql.Time sqlTime = DateUtils.toSQLTime(time);

				java.util.Date birthOfDate = dataset.getDate(Tag.Patient​Birth​Date);
				Integer age = Utils.calculateAge(birthOfDate, date);
				
				String accessionNo = (dataset.getString(Tag.Accession​Number) != null
						&& dataset.getString(Tag.Accession​Number).length() > 0) ? dataset.getString(Tag.Accession​Number)
								: "";
				String refName = (dataset.getString(Tag.Referring​Physician​Name) != null
						&& dataset.getString(Tag.Referring​Physician​Name).length() > 0)
								? dataset.getString(Tag.Referring​Physician​Name)
								: "";
				String retAe = (dataset.getString(Tag.Retrieve​AE​Title) != null
						&& dataset.getString(Tag.Retrieve​AE​Title).length() > 0) ? dataset.getString(Tag.Retrieve​AE​Title)
								: "";
				String studyDesc = (dataset.getString(Tag.Study​Description) != null
						&& dataset.getString(Tag.Study​Description).length() > 0)
								? dataset.getString(Tag.Study​Description)
								: "";
				String studyId = (dataset.getString(Tag.Study​ID) != null
										&& dataset.getString(Tag.Study​ID).length() > 0)
												? dataset.getString(Tag.Study​ID)
												: "";
				int numOfSeries = getNumOfSeries(patientID, dataset.getString(Tag.Study​Instance​UID))+1;
				int numOfInst = getNumOfInstancesInStudy(patientID, dataset.getString(Tag.Study​Instance​UID))+1;
				
				// 15 state, be careful
				PreparedStatement insertStmt = conn
						.prepareStatement("insert into study values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
				insertStmt.setString(1, dataset.getString(Tag.Study​Instance​UID));
				insertStmt.setDate(2, sqlDate);
				insertStmt.setTime(3, sqlTime);
				insertStmt.setString(4, accessionNo);
				insertStmt.setString(5, refName);//deprecated
				insertStmt.setString(6, studyDesc);
				insertStmt.setString(7, studyId);
				insertStmt.setString(8, dataset.getString(Tag.Modalities​In​Study));
				insertStmt.setInt(9, numOfSeries);
				insertStmt.setInt(10, numOfInst);
				insertStmt.setInt(11, 0);//RecdImgCnt//deprecated
				insertStmt.setInt(12, 0);//SendImgCnt//deprecated
				insertStmt.setString(13, retAe);//deprecated
				insertStmt.setBoolean(14, false);//DownloadStatus
				insertStmt.setInt(15,age);//deprecated
				insertStmt.setString(16, patientID);
				insertStmt.execute();
				insertStmt.close();
				conn.commit();
			} catch (SQLException ex) {
				logger.severe("DatabaseHandler - Unable to save study information\n"+ex.getMessage());
			} finally {
				safeClose(conn);
			}
		}
	}

	private void insertSeriesInfo(final DicomObject dataset, String patientId, String studyUid, boolean saveAsLink) {
		if (!checkRecordExists("SERIES", "SeriesInstanceUID", dataset.getString(Tag.Series​Instance​UID))) {
			Connection conn = openConnection();
			/* Series Date */
			java.util.Date date = dataset.getDate(Tag.Series​Date);
			java.sql.Date sqlDate = DateUtils.toSQLDateObj(date);
			/* Series Time */
			java.util.Date time = dataset.getDate(Tag.Series​Time);
			/*ignore milliseconds*/
			java.sql.Time sqlTime = DateUtils.toSQLTime(time);
			
			int numImages = getNumOfInstanceInSeries(patientId, studyUid, dataset.getString(Tag.Series​Instance​UID))+1;

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
					&& dataset.getString(Tag.Series​Description).length() > 0) ? dataset.getString(Tag.Series​Description)
							: "";
			String bodyPartExamined = (dataset.getString(Tag.Body​Part​Examined) != null
					&& dataset.getString(Tag.Body​Part​Examined).length() > 0) ? dataset.getString(Tag.Body​Part​Examined)
							: "";
			try {
				PreparedStatement insertStmt = conn
						.prepareStatement("insert into series values(?,?,?,?,?,?,?,?,?,?,?,?)");
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
				insertStmt.execute();
				insertStmt.close();
				conn.commit();
			} catch (SQLException ex) {
				logger.severe( "DatabaseHandler - Unable to save series information\n"+ex.getMessage());
			} finally {
				update("study", "NoOfSeries", getNumOfSeries(patientId,studyUid), "StudyInstanceUID", studyUid);
				safeClose(conn);
			}
		}
	}

	private void insertImageInfo(DicomObject dataset, String filePath, String patientID, String studyUid,
			String seriesUid, boolean saveAsLink) throws Exception {
		
			Connection conn = openConnection();
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
				totalFrame = dataset.getInt(Tag.Number​Of​Frames,-1);
			}
			String acquisitionNo = dataset.getString(Tag.Acquisition​Number) != null
					? dataset.getString(Tag.Acquisition​Number)
					: "";
			java.util.Date acqDateTime = dataset.getDate(Tag.Acquisition​Date​Time);
			java.sql.Time sqlAcqDateTime = DateUtils.toSQLTime(acqDateTime);			

			String frameOfRefUid = dataset.getString(Tag.Frame​Of​Reference​UID) != null
					? dataset.getString(Tag.Frame​Of​Reference​UID)
					: "";
			String imgPos = dataset.getBytes(Tag.Image​Position) != null
					? new String(dataset.getBytes(Tag.Image​Position))
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
			try {
				PreparedStatement insertStmt = conn
						.prepareStatement("insert into image values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
				insertStmt.setString(1, dataset.getString(Tag.SOP​Instance​UID));
				insertStmt.setString(2, dataset.getString(Tag.SOP​Class​UID));
				insertStmt.setInt(3, dataset.getInt(Tag.Instance​Number, 1));
				insertStmt.setString(4, acquisitionNo);
				insertStmt.setBoolean(5, multiframe);
				insertStmt.setInt(6, totalFrame);
				insertStmt.setString(7, "partial");//deprecated
				insertStmt.setTime(8, sqlAcqDateTime);
				insertStmt.setTime(9, null);//TODO ForwardDateTime//deprecated
				insertStmt.setTime(10, null);//TODO ReceivedDateTime
				insertStmt.setString(11, "partial");//ReceiveStatus//deprecated
				insertStmt.setString(12, filePath);
				insertStmt.setBoolean(13, saveAsLink);
				insertStmt.setInt(14, Integer.parseInt(sliceLoc));
				insertStmt.setBoolean(15, encapsulatedPDF);
				insertStmt.setBoolean(16, false);//ThumbnailStatus//deprecated
				insertStmt.setString(17, frameOfRefUid);//deprecated
				insertStmt.setString(18, imgPos);//deprecated
				insertStmt.setString(19, imgOrientation);//deprecated
				insertStmt.setString(20, image_type);//deprecated
				insertStmt.setString(21, pixelSpacing);//deprecated
				insertStmt.setString(22, sliceThickness);//deprecated
				insertStmt.setInt(23, row);//deprecated
				insertStmt.setInt(24, columns);//deprecated
				insertStmt.setString(25, referSopInsUid.trim());//deprecated
				insertStmt.setString(26, tsUID);
				insertStmt.setString(27, patientID);
				insertStmt.setString(28, studyUid);
				insertStmt.setString(29, seriesUid);
				insertStmt.execute();
				insertStmt.close();
				conn.commit();
			} catch (SQLException ex) {
				logger.severe( "DatabaseHandler - Unable to save instance information\n"+ex.getMessage());
				ex.printStackTrace();
			}finally {
				safeClose(conn);
			}
	}
	
	public void updateImageInfo(DicomObject dataset, String filePath, String patientID, String studyUid,
			String seriesUid, boolean saveAsLink) throws Exception {

		Connection conn = openConnection();

		try {
			String statement = "UPDATE IMAGE ";
			statement = statement + "SET FileStoreUrl=?, isLink=? ";
			statement = statement
					+ "WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, filePath);
			pstmt.setBoolean(2, saveAsLink);
			pstmt.setString(3, dataset.getString(Tag.Patient​ID));
			pstmt.setString(4, dataset.getString(Tag.Study​Instance​UID));
			pstmt.setString(5, dataset.getString(Tag.Series​Instance​UID));
			pstmt.setString(6, dataset.getString(Tag.SOP​Instance​UID));
			pstmt.executeUpdate();
			pstmt.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe("DatabaseHandler - Unable to save instance information\n" + ex.getMessage());
			ex.printStackTrace();
		} finally {
			safeClose(conn);
		}
	}
	
	public void updateRoiInfo(
			String roiId,
			String name,
			int roiType,
			int originX,
			int originY,
			int w, int h,
			double[] pointX,
			double[] pointY,
			double[] shapeArray,
			int instNo,
			int roiGroup,
			String roilbl,
			String objType,
			String organ,
			String txt,
			String pid, String studyUid, String seriesUid, String sopUid) {
		
		Connection conn = openConnection();
		
		//get as byte
		byte[] byteArrayX = null;
		byte[] byteArrayY = null;
		byte[] byteArrayShape = null;
		if(pointX!=null) {
			ByteBuffer bbX = ByteBuffer.allocate(pointX.length * 8);
			for (int i = 0; i < pointX.length; i++) {
				bbX.putDouble(pointX[i]);
			}
			byteArrayX = bbX.array();
		}
		if(pointY!=null) {
			ByteBuffer bbY = ByteBuffer.allocate(pointY.length * 8);
			for (int i = 0; i < pointY.length; i++) {
				bbY.putDouble(pointY[i]);
			}
			byteArrayY = bbY.array();
		}
		if(shapeArray!=null) {
			ByteBuffer bbS = ByteBuffer.allocate(shapeArray.length * 8);
			for (int i = 0; i < shapeArray.length; i++) {
				bbS.putDouble(shapeArray[i]);
			}
			byteArrayShape = bbS.array();
		}
		try {
			String statement = "UPDATE ROI ";//need space at end
			statement = statement + "SET Name=?, RoiType=?, OriginX=?, OriginY=?, Width=?, Height=?, PointX=?, PointY=?, Shape=?, InstanceNo=?, Description=?, RoiGroup=?, RoiLabel=?, ObjectType=?, Organ=? ";
			statement = statement + "WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=? AND RoiID=?";
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, name);
			pstmt.setInt(2, roiType);
			pstmt.setInt(3, originX);
			pstmt.setInt(4, originY);
			pstmt.setInt(5, w);
			pstmt.setInt(6, h);
			pstmt.setBlob(7, byteArrayX != null ? new ByteArrayInputStream(byteArrayX):null, byteArrayX!=null ? byteArrayX.length:0);
			pstmt.setBlob(8, byteArrayY != null ? new ByteArrayInputStream(byteArrayY):null, byteArrayY!=null ? byteArrayY.length:0);
			pstmt.setBlob(9, byteArrayShape != null ? new ByteArrayInputStream(byteArrayShape):null, byteArrayShape!=null ? byteArrayShape.length:0);
			pstmt.setInt(10, instNo);
			pstmt.setString(11, txt);
			pstmt.setInt(12, roiGroup);
			pstmt.setString(13, roilbl);
			pstmt.setString(14, objType);
			pstmt.setString(15, organ);
			pstmt.setString(16, pid);
			pstmt.setString(17, studyUid);
			pstmt.setString(18, seriesUid);
			pstmt.setString(19, sopUid);
			pstmt.setString(20, roiId);
			pstmt.executeUpdate();
			pstmt.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe( "DatabaseHandler - Unable to update roi information\n"+ex.getMessage());
		}finally {
				safeClose(conn);
		}
	}

	// Accessing Graphy node info
	//when starting up, use prop file.
	public String[] getListenerDetails() {
		Connection conn = openConnection();
		String detail[] = new String[4];
		try {
			String statement="select * from listener";
			PreparedStatement pstmt = conn.prepareStatement(statement);
			ResultSet listenerInfo = pstmt.executeQuery();
			if (listenerInfo.next()) {
				detail[0] = listenerInfo.getString("aetitle");
				detail[1] = listenerInfo.getString("host");
				detail[2] = listenerInfo.getString("port");
				detail[3] = listenerInfo.getString("storagelocation");
			}
			listenerInfo.close();
			pstmt.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return detail;
	}

	public ArrayList<HashMap<String,Object>> getCommunicationServerList() {
		Connection conn = openConnection();
		ArrayList<HashMap<String,Object>> serverMaterialsList = new ArrayList<HashMap<String,Object>>();
		try {
			ResultSet serverInfo = conn.createStatement().executeQuery("select * from servers");
			while (serverInfo.next()) {
				HashMap<String,Object> nodeMaterials = new HashMap<>();
				String logicalname = serverInfo.getString("logicalname") != null ? serverInfo.getString("logicalname"):"";
				String aetitle = serverInfo.getString("aetitle") != null ? serverInfo.getString("aetitle"):"";
				String hostname = serverInfo.getString("hostname") != null ? serverInfo.getString("hostname"):"";
				Object port = Integer.valueOf(serverInfo.getInt("port")) != null ? serverInfo.getInt("port"):null;
				String ciphers = serverInfo.getString("ciphers") != null ? serverInfo.getString("ciphers"):"";
				String retrievetype = serverInfo.getString("retrievetype") != null ? serverInfo.getString("retrievetype"):"";
				String wadocontext = serverInfo.getString("wadocontext") != null ? serverInfo.getString("wadocontext"):"";
				Object wadoport = Integer.valueOf(serverInfo.getInt("wadoport")) != null ? serverInfo.getInt("wadoport"):null;
				String wadoprotocol = serverInfo.getString("wadoprotocol") != null ? serverInfo.getString("wadoprotocol"):"";
				String retTS = serverInfo.getString("retrievets") != null ? serverInfo.getString("retrievets"):"";
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
			serverInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return serverMaterialsList;
	}
	
	
	public ArrayList<DicomCommunicationNode> loadServerList() {
		// get server materials
		ArrayList<HashMap<String,Object>>  serverMaterials= getCommunicationServerList();
		ArrayList<DicomCommunicationNode> serverList = new ArrayList<DicomCommunicationNode>();
		for(HashMap<String,Object> nodeMaterials:serverMaterials) {
			System.out.println((String)nodeMaterials.get("logicalname"));
			serverList.add(new DicomCommunicationNode(nodeMaterials));
		}
		return serverList;
	}
	
	//move to another class
//	public ArrayList<DicomCommunicationNode> getCommunicationableServers() {
//		Connection conn = openConnection();
//		ArrayList<DicomCommunicationNode> serverList = new ArrayList<DicomCommunicationNode>();
//		try {
//			ResultSet serverInfo = conn.createStatement().executeQuery("select * from servers");
//			while (serverInfo.next()) {
//				DicomCommunicationNode remote = new DicomCommunicationNode(
//						serverInfo.getString("logicalname") != null ? serverInfo.getString("logicalname"):"",
//						serverInfo.getString("aetitle") != null ? serverInfo.getString("aetitle"):"",
//						serverInfo.getString("hostname") != null ? serverInfo.getString("hostname"):"",
//						Integer.valueOf(serverInfo.getInt("port")) != null ? serverInfo.getInt("port"):null,
//						serverInfo.getString("ciphers") != null ? serverInfo.getString("ciphers"):"",
//						serverInfo.getString("retrievetype") != null ? serverInfo.getString("retrievetype"):"",
//						serverInfo.getString("wadocontext") != null ? serverInfo.getString("wadocontext"):"",
//						Integer.valueOf(serverInfo.getInt("wadoport")) != null ? serverInfo.getInt("wadoport"):null,
//						serverInfo.getString("wadoprotocol") != null ? serverInfo.getString("wadoprotocol"):"",
//						serverInfo.getString("retrievets") != null ? serverInfo.getString("retrievets"):"");
//				boolean connect = new EchoDelegate(null).echo(remote);
//				if(connect) {
//					serverList.add(remote);
//				}
//			}
//			serverInfo.close();
//			conn.commit();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			if(open) {
//				safeClose(conn);
//			}
//		}
//		return serverList;
//	}

	public String getRetrieveType(String serverName) {
		Connection conn = openConnection();
		String retType = null;
		try {
			ResultSet serverNameInfo = conn.createStatement()
					.executeQuery("select retrievetype from servers where logicalname='" + serverName + "'");
			serverNameInfo.next();
			retType = serverNameInfo.getString("retrievetype");
			serverNameInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			return null;
		}finally {
				safeClose(conn);
		}
		return retType;
	}

	public boolean isPreviewsEnabled(String serverName) {
		Connection conn = openConnection();
		boolean preview = false;
		try {
			ResultSet serverInfo = conn.createStatement()
					.executeQuery("select showpreviews from servers where logicalname='" + serverName + "'");
			serverInfo.next();
			preview = serverInfo.getBoolean("showpreviews");
			serverInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			return false;
		}finally {
				safeClose(conn);
		}
		return preview;
	}

	public boolean isDownloadPending(String studyUid) {
		Connection conn = openConnection();
		boolean pending = false;
		try {
			ResultSet pendingInfo = conn.createStatement()
					.executeQuery("select DownloadStatus from study where StudyInstanceUID='" + studyUid + "'");
			if (pendingInfo.next()) {
				pending = pendingInfo.getBoolean("DownloadStatus");
			}
			pendingInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			//force close ?
			safeClose(conn);
		}
		return pending;
	}

	public HashMap<String,Object> getServerNamed(String nickname) {
		Connection conn = openConnection();
		HashMap<String,Object> nodeMaterials = null;
		try {
			ResultSet serverInfo = conn.createStatement()
					.executeQuery("select * from servers where logicalname='" + nickname + "'");
			if (serverInfo.next()) {
				nodeMaterials = new HashMap<>();
				String logicalname = serverInfo.getString("logicalname") != null ? serverInfo.getString("logicalname"):"";
				String aetitle = serverInfo.getString("aetitle") != null ? serverInfo.getString("aetitle"):"";
				String hostname = serverInfo.getString("hostname") != null ? serverInfo.getString("hostname"):"";
				Object port = Integer.valueOf(serverInfo.getInt("port")) != null ? serverInfo.getInt("port"):null;
				String ciphers = serverInfo.getString("ciphers") != null ? serverInfo.getString("ciphers"):"";
				String retrievetype = serverInfo.getString("retrievetype") != null ? serverInfo.getString("retrievetype"):"";
				String wadocontext = serverInfo.getString("wadocontext") != null ? serverInfo.getString("wadocontext"):"";
				Object wadoport = Integer.valueOf(serverInfo.getInt("wadoport")) != null ? serverInfo.getInt("wadoport"):null;
				String wadoprotocol = serverInfo.getString("wadoprotocol") != null ? serverInfo.getString("wadoprotocol"):"";
				String retTS = serverInfo.getString("retrievets") != null ? serverInfo.getString("retrievets"):"";
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
			serverInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			return null;
		}finally {
				safeClose(conn);
		}
		return nodeMaterials;
	}
	
	public boolean isAlreadyRegisteredServer(String identicalNickname) {
		if(identicalNickname == null) {
			return false;
		}
		int pk = getCommunicationServerPk(identicalNickname);
		if(pk == -1) {
			return false;
		}else {
			return true;
		}
	}
	

//	public ArrayList<ButtonsModel> getAllQueryButtons() {
//		openConnection();
//		ArrayList<ButtonsModel> buttons = new ArrayList<ButtonsModel>();
//		try {
//			ResultSet buttonsInfo = conn.createStatement().executeQuery("select * from buttons order by buttonno");
//			while (buttonsInfo.next()) {
//				buttons.add(new ButtonsModel(buttonsInfo.getString("description"), buttonsInfo.getString("modality"),
//						buttonsInfo.getString("datecriteria"), buttonsInfo.getString("timecriteria"),
//						buttonsInfo.getBoolean("iscustomdate"), buttonsInfo.getBoolean("iscustomtime")));
//			}
//			buttonsInfo.close();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//		return buttons;
//	}

	public ArrayList<String> getAllButtonNames() {
		Connection conn = openConnection();
		ArrayList<String> buttonNames = new ArrayList<String>();
		try {
			ResultSet buttonsInfo = conn.createStatement()
					.executeQuery("select description from buttons order by buttonno");
			while (buttonsInfo.next()) {
				buttonNames.add(buttonsInfo.getString("description"));
			}
			buttonsInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return buttonNames;
	}

//	public ButtonsModel getButtonDetails(String description) {
//		openConnection();
//		try {
//			ResultSet buttonInfo = conn.createStatement()
//					.executeQuery("select * from buttons where description='" + description + "'");
//			buttonInfo.next();
//			return new ButtonsModel(buttonInfo.getString("description"), buttonInfo.getString("modality"),
//					buttonInfo.getString("datecriteria"), buttonInfo.getString("timecriteria"),
//					buttonInfo.getBoolean("iscustomdate"), buttonInfo.getBoolean("iscustomtime"));
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//		return null;
//	}

	public String getActiveTheme() {
		Connection conn = openConnection();
		try {
			ResultSet activeThemeInfo = conn.createStatement().executeQuery("select name from theme where status=true");
			activeThemeInfo.next();
			return activeThemeInfo.getString("name");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return null;
	}

	public ArrayList<String> getThemes() {
		Connection conn = openConnection();
		ArrayList<String> themeNames = new ArrayList<String>();
		try {
			ResultSet themeInfo = conn.createStatement().executeQuery("select name from theme");
			while (themeInfo.next()) {
				if (!themeInfo.getString("name").equals("System")) {
					themeNames.add(themeInfo.getString("name"));
				} else {
					themeNames.add(System.getProperty("os.name"));
				}
			}
			themeInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return themeNames;
	}

	public ArrayList<String> getActiveModalities() {
		Connection conn = openConnection();
		ArrayList<String> modalities = new ArrayList<String>();
		try {
			ResultSet modalityInfo = conn.createStatement()
					.executeQuery("select shortname from modality where status=true");
			while (modalityInfo.next()) {
				modalities.add(modalityInfo.getString("shortname"));
			}
			modalityInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return modalities;
	}

	public boolean isModalityActive(String shortname) {
		Connection conn = openConnection();
		try {
			ResultSet isActive = conn.createStatement()
					.executeQuery("select status from modality where shortname='" + shortname + "'");
			isActive.next();
			return isActive.getBoolean("status");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return false;
	}

//	public ArrayList<PresetModel> getPresetsForModality(String modality) {
//		openConnection();
//		ArrayList<PresetModel> presets = new ArrayList<PresetModel>();
//		try {
//			ResultSet modalityInfo = conn.createStatement()
//					.executeQuery("select pk from modality where shortname='" + modality + "'");
//			modalityInfo.next();
//			ResultSet presetInfo = conn.createStatement()
//					.executeQuery("select * from presets where modality_fk=" + modalityInfo.getInt("pk"));
//			while (presetInfo.next()) {
//				PresetModel preset = new PresetModel(presetInfo.getInt("pk"), modality,
//						presetInfo.getString("presetname"), presetInfo.getString("windowwidth"),
//						presetInfo.getString("windowlevel"));
//				presets.add(preset);
//			}
//			modalityInfo.close();
//			presetInfo.close();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//		return presets;
//	}

	public String[] getActiveLanguage() {
		Connection conn = openConnection();
		try {
			ResultSet resultSet = conn.createStatement().executeQuery("select * from locale where status=true");
			while (resultSet.next()) {
				return new String[] { resultSet.getString("countrycode"), resultSet.getString("country"),
						resultSet.getString("languagecode"), resultSet.getString("language"),
						resultSet.getString("localeid") };
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return null;
	}

	public String[] getCountryList() {
		Connection conn = openConnection();
		try {
			ResultSet count = conn.createStatement().executeQuery("select count(distinct country) from locale");
			count.next();
			String[] countryList = new String[count.getInt(1)];
			int index = 0;
			ResultSet result = conn.createStatement().executeQuery("select distinct country from locale");
			while (result.next()) {
				countryList[index] = result.getString("country");
				index++;
			}
			count.close();
			result.close();
			return countryList;
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return null;
	}

	public String[] getLanguagesOfCountry(String country) {
		Connection conn = openConnection();
		try {
			ResultSet count = conn.createStatement()
					.executeQuery("select count(distinct language) from locale where country='" + country + "'");
			count.next();
			String languageList[] = new String[count.getInt(1)];
			ResultSet result = conn.createStatement()
					.executeQuery("select distinct language from locale where country='" + country + "'");
			int index = 0;
			while (result.next()) {
				languageList[index] = result.getString("language");
				index++;
			}
			count.close();
			result.close();
			return languageList;
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return null;
	}

	public String[] getLocaleIDForCountryAndLanguage(String country, String language) {
		Connection conn = openConnection();
		try {
			ResultSet count = conn.createStatement().executeQuery("select count(localeid) from locale where country='"
					+ country + "' and language='" + language + "'");
			count.next();
			String[] localeId = new String[count.getInt(1)];
			ResultSet result = conn.createStatement().executeQuery(
					"select localeid from locale where country='" + country + "' and language='" + language + "'");
			int index = 0;
			while (result.next()) {
				localeId[index] = result.getString("localeid");
				index++;
			}
			count.close();
			result.close();
			return localeId;
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}

		return null;
	}

	public ArrayList<Integer> getTextAnnotationList() {
		Connection conn = openConnection();
		ArrayList<Integer> tagList = new ArrayList<>();
		try {
			Statement stmt = conn.createStatement();
			String sql = "SELECT * FROM textannotation";
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				tagList.add(rs.getInt("tag"));
			}
			conn.commit();
			rs.close();
		} catch (SQLException e) {
			logger.severe(e.getMessage());
		}finally {
				safeClose(conn);
		}
		return tagList;
	}
	
	/*
	 * 
	 */
	public void upadateTextAnnotation(ArrayList<Integer> tags) {
		// first, delete textannotation record
		Connection conn = openConnection();
		try {
			conn.createStatement().execute("delete from textannotation");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}
		// set new list
		for (Integer tag : tags) {
			String sql = "insert into textannotation(tag) values(";
			sql = sql + String.valueOf(tag) + ")";
			try {
				conn.createStatement().execute(sql);
				conn.commit();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				logger.severe(e.getMessage());
			}
		}
		safeClose(conn);
	}
	
	
	//move to another...
//	public ArrayList<DICOMNode> getSeriesList(String patID, DICOMNode studyNode) {
//		Connection conn = openConnection();
//		ArrayList<DICOMNode> seriesList = new ArrayList<DICOMNode>();
//		String studyUid = studyNode.getData(DICOMNode.StudyInstanceUID);
//		ResultSet RS_series = null;
//		ResultSet RS_images = null;
//		try {
//			String statement = "select * from series where PatientID='"
//					+ patID + "' and StudyInstanceUID='"
//							+ studyUid +"' order by SeriesNumber";
//			RS_series = conn.createStatement().executeQuery(statement);
//			while (RS_series.next()) {
//				DICOMNode series = constructSeriesNode(RS_series, studyNode);
//				try {
//					/*
//					 * if ignore multiframe,
//					 * use "and multiframe=false"
//					 */
//					RS_images = conn.createStatement()
//							.executeQuery("select * from image where PatientID='"+patID+"' and StudyInstanceUID='" + studyUid
//									+ "' and SeriesInstanceUID='" + RS_series.getString("SeriesInstanceUID")
//									+ "' order by InstanceNumber");
//				}catch(Exception ee) {
//					ApplicationContext.logger.debug("This series does not have image yet.");
//					return seriesList;
//				}
//				ArrayList<DICOMNode> imageList = new ArrayList<>();
//				while (RS_images.next()) {
//					DICOMNode image = constructImageNode(RS_images);
//					imageList.add(image);
//				}
//				series.setChildren(imageList);
//				seriesList.add(series);
//			}
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			try {
//				if(RS_series != null) {
//					RS_series.close();
//					if(RS_images != null) {
//						RS_images.close();
//					}
//				}
//			} catch (SQLException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			if(open) {
//				safeClose(conn);
//			}
//		}
//		return seriesList;
//	}
	
	/*
	 * 項目になければNULLとして扱う
	 */
	/*
	 * nodeにマテリアルを持たせて、
	 * DICOMNodeBuilder(将来作るであろう)に渡す。
	 */
	public HashMap<String,Object> loadStudyNodeMaterial(ResultSet patientInfo, ResultSet studyInfo) {
		Connection conn = openConnection();
		HashMap<String,Object> studyNodeMaterial = new HashMap<String,Object>();
		try {
			studyNodeMaterial.put("level", 2);//DICOMNode.Study
			studyNodeMaterial.put("PatientName", patientInfo.getString("PatientName"));
			studyNodeMaterial.put("PatientID", patientInfo.getString("PatientID"));
			studyNodeMaterial.put("StudyDate", studyInfo.getString("StudyDate"));
			studyNodeMaterial.put("StudyTime", studyInfo.getString("StudyTime"));
			studyNodeMaterial.put("StudyDescription", studyInfo.getString("StudyDescription"));
			studyNodeMaterial.put("ModalitiesInStudy", studyInfo.getString("ModalitiesInStudy"));
			studyNodeMaterial.put("PatientSex", patientInfo.getString("PatientSex"));
			studyNodeMaterial.put("PatientBirthDate", patientInfo.getString("PatientBirthDate"));
			studyNodeMaterial.put("PatientAge", studyInfo.getString("PatientAge"));//get from study info
			studyNodeMaterial.put("AccessionNumber", studyInfo.getString("AccessionNumber"));
			studyNodeMaterial.put("NumOfSeriesInStudy", String.valueOf(getNumOfSeriesInStudy(patientInfo.getString("PatientID"), studyInfo.getString("StudyInstanceUID"))));
			studyNodeMaterial.put("NumOfInstancesInStudy", String.valueOf(getNumOfInstancesInStudy(patientInfo.getString("PatientID"), studyInfo.getString("StudyInstanceUID"))));
			studyNodeMaterial.put("StudyInstanceUID", studyInfo.getString("StudyInstanceUID"));
		} catch (SQLException e) {
			e.printStackTrace();
		}finally {
				safeClose(conn);
		}
		return studyNodeMaterial;
	}
	
	public HashMap<String,Object> loadSeriesNodeMaterial(ResultSet seriesInfo, HashMap<String,Object> studyMaterial) {
		Connection conn = openConnection();
		String modalityInStudy = (String) studyMaterial.get("ModalitiesInStudy");
		try {
			if(modalityInStudy == null || modalityInStudy.equals("")) {
				modalityInStudy = seriesInfo.getString("Modality");
			}else {
				String modality = seriesInfo.getString("Modality");
				if(!modalityInStudy.contains(modality)) {
					modalityInStudy = modalityInStudy+","+seriesInfo.getString("Modality");
				}
			}
			studyMaterial.put("ModalitiesInStudy", modalityInStudy);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		HashMap<String,Object> seriesNodeMaterial = new HashMap<String,Object>();
		try {
			seriesNodeMaterial.put("level", 3);//DICOMNode.SERIES
			seriesNodeMaterial.put("PatientID", seriesInfo.getString("PatientID"));
			seriesNodeMaterial.put("SeriesDate", seriesInfo.getString("SeriesDate"));
			seriesNodeMaterial.put("SeriesDescription", seriesInfo.getString("SeriesDescription"));
			seriesNodeMaterial.put("Modality", seriesInfo.getString("Modality"));
			seriesNodeMaterial.put("InstitutionName", seriesInfo.getString("InstitutionName"));
			seriesNodeMaterial.put("ModelName", seriesInfo.getString("ModelName"));
			seriesNodeMaterial.put("SeriesNumber", seriesInfo.getString("SeriesNumber"));
			seriesNodeMaterial.put("NumOfInstanceInSeries", String.valueOf(getNumOfInstanceInSeries(seriesInfo.getString("PatientID"), seriesInfo.getString("StudyInstanceUID"), seriesInfo.getString("SeriesInstanceUID"))));
			seriesNodeMaterial.put("StudyInstanceUID", seriesInfo.getString("StudyInstanceUID"));
			seriesNodeMaterial.put("SeriesInstanceUID", seriesInfo.getString("SeriesInstanceUID"));
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			safeClose(conn);//DO NOT OPEN/CLOSE Connection
		}
		return seriesNodeMaterial;
	}
	
	public HashMap<String,Object> loadImageNodeMaterials(ResultSet imageInfo) {
		Connection conn = openConnection();
		HashMap<String,Object> nodeMaterial = new HashMap<String, Object>();
		try {
			nodeMaterial.put("level", 4);
			nodeMaterial.put("PatientID", imageInfo.getString("PatientID"));
			nodeMaterial.put("AcquisitionDateTime", imageInfo.getString("AcquisitionDateTime"));
			nodeMaterial.put("AcquisitionNumber", imageInfo.getString("AcquisitionNumber"));
			nodeMaterial.put("InstanceNumber", imageInfo.getString("InstanceNumber"));
			nodeMaterial.put("StudyInstanceUID", imageInfo.getString("StudyInstanceUID"));
			nodeMaterial.put("SeriesInstanceUID", imageInfo.getString("SeriesInstanceUID"));
			nodeMaterial.put("SOPInstanceUID", imageInfo.getString("SOPInstanceUID"));
		} catch (SQLException e) {
			e.printStackTrace();
		}finally {
				safeClose(conn);
		}
		return nodeMaterial;
	}

	/**
	 * 
	 * move to DICOMNodeBuilder...
	 * 
	 * return whole number of study level DICOM Nodes
	 * @return
	 */
//	public ArrayList<DICOMNode> listAllLocalStudies() {
//		Connection conn = openConnection();
//		ArrayList<DICOMNode> studiesList = new ArrayList<DICOMNode>();
//		ResultSet studyInfo = null;
//		ResultSet patientInfo = null;
//		try {
//			studyInfo = conn.createStatement().executeQuery("select * from study");
//			while (studyInfo.next()) {
//				patientInfo = conn.createStatement()
//						.executeQuery("select * from patient where PatientID='"
//								+ studyInfo.getString("PatientID") + "'");
//				patientInfo.next();
//				DICOMNode studyNode = constructStudyNode(patientInfo, studyInfo);
//				studiesList.add(studyNode);
//			}
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			try {
//				if(patientInfo!=null) {
//					patientInfo.close();
//				}
//				studyInfo.close();
//			} catch (SQLException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			if(open) {
//				safeClose(conn);
//			}
//		}
//		return studiesList;
//	}
	
//	public ArrayList<DICOMNode> getSeriesList(String patID, DICOMNode studyNode) {
//		Connection conn = openConnection();
//		ArrayList<DICOMNode> seriesList = new ArrayList<DICOMNode>();
//		String studyUid = studyNode.getData(DICOMNode.StudyInstanceUID);
//		ResultSet RS_series = null;
//		ResultSet RS_images = null;
//		try {
//			String statement = "select * from series where PatientID='"
//					+ patID + "' and StudyInstanceUID='"
//							+ studyUid +"' order by SeriesNumber";
//			RS_series = conn.createStatement().executeQuery(statement);
//			while (RS_series.next()) {
//				DICOMNode series = constructSeriesNode(RS_series, studyNode);
//				try {
//					/*
//					 * if ignore multiframe,
//					 * use "and multiframe=false"
//					 */
//					RS_images = conn.createStatement()
//							.executeQuery("select * from image where PatientID='"+patID+"' and StudyInstanceUID='" + studyUid
//									+ "' and SeriesInstanceUID='" + RS_series.getString("SeriesInstanceUID")
//									+ "' order by InstanceNumber");
//				}catch(Exception ee) {
//					ApplicationContext.logger.debug("This series does not have image yet.");
//					return seriesList;
//				}
//				ArrayList<DICOMNode> imageList = new ArrayList<>();
//				while (RS_images.next()) {
//					DICOMNode image = constructImageNode(RS_images);
//					imageList.add(image);
//				}
//				series.setChildren(imageList);
//				seriesList.add(series);
//			}
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			try {
//				if(RS_series != null) {
//					RS_series.close();
//					if(RS_images != null) {
//						RS_images.close();
//					}
//				}
//			} catch (SQLException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			if(open) {
//				safeClose(conn);
//			}
//		}
//		return seriesList;
//	}
	
	public boolean getLoopbackStatus() {
		Connection conn = openConnection();
		try {
			ResultSet loopBackStatus = conn.createStatement().executeQuery("select Loopback from miscellaneous");
			loopBackStatus.next();
			return loopBackStatus.getBoolean("Loopback");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return false;
	}
	
	public int getNumOfSeriesInStudy(String patID, String studyUid) {
		Connection conn = openConnection();
		try {
			ResultSet totalInstancesInfo = conn.createStatement()
					.executeQuery("select count(*) from series where PatientID='"+patID+"'"+" and StudyInstanceUID='" + studyUid + "'");
			totalInstancesInfo.next();
			return totalInstancesInfo.getInt(1);
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return 0;
	}

	public int getNumOfInstancesInStudy(String patID, String studyUid) {
		Connection conn = openConnection();
		try {
			ResultSet totalInstancesInfo = conn.createStatement()
					.executeQuery("select count(*) from image where PatientID='"+patID+"'"+" and StudyInstanceUID='" + studyUid + "'");
			totalInstancesInfo.next();
			return totalInstancesInfo.getInt(1);
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return 0;
	}
	
	public int getNumOfInstancesInStudy(String studyUid) {
		Connection conn = openConnection();
		try {
			ResultSet totalInstancesInfo = conn.createStatement()
					.executeQuery("select count(*) from image where StudyInstanceUID='" + studyUid + "'");
			totalInstancesInfo.next();
			return totalInstancesInfo.getInt(1);
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return 0;
	}

	public int getNumOfAllStudiesinDB() {
		Connection conn = openConnection();
		try {
			ResultSet totalStudiesInfo = conn.createStatement().executeQuery("select count(*) from study");
			totalStudiesInfo.next();
			return totalStudiesInfo.getInt(1);
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return 0;
	}

	/*
	 * get saveAsLink information from instance
	 */
	public boolean isInstanceSavedAsLink(String patID, String studyUID,String seriesUID, String sopUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		boolean res = false;
		try {
			String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";		
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			pstmt.setString(4, sopUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);
			if(rset.next()) {
				res = rset.getBoolean("isLink");
			}
			rset.close();
			pstmt.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return res;
	}



//	public ArrayList<SeriesNode> getMultiframeSeries(String studyUID, String seriesUID, String seriesNo,
//			String bodyPart, String seriesDate, String seriesTime) {
//		ArrayList<SeriesNode> arr = new ArrayList();
//		try {
//			ResultSet rs1 = null;
//			String sql1 = "select totalframe,SOPInstanceUID,SOPClassUID from image where StudyInstanceUID='" + studyUID
//					+ "' AND " + "SeriesInstanceUID='" + seriesUID + "'" + " AND multiframe=true"
//					+ " order by InstanceNumber asc";
//			rs1 = conn.createStatement().executeQuery(sql1);
//			while (rs1.next()) {
//				int totalFrames = Integer.parseInt(rs1.getString("totalFrame"));
//				SeriesNode series = new SeriesNode(studyUID, seriesUID, seriesNo, null, bodyPart, seriesDate,
//						seriesTime, true, rs1.getString("SOPInstanceUID"), totalFrames);
//				if (rs1.getString("SOPClassUID") != null
//						&& (rs1.getString("SOPClassUID").equals(UID.VideoEndoscopicImageStorage)
//								|| rs1.getString("SOPClassUID").equals(UID.VideoMicroscopicImageStorage)
//								|| rs1.getString("SOPClassUID").equals(UID.VideoPhotographicImageStorage))) {
//					series.setVideoStatus(true);
//					series.setSeriesDesc("Video:" + totalFrames + " Frames");
//				} else {
//					series.setSeriesDesc("Multiframe:" + totalFrames + " Frames");
//				}
//				series.setInstanceUIDIfMultiframe(rs1.getString("SOPInstanceUID"));
//				arr.add(series);
//			}
//			rs1.close();
//		} catch (SQLException e) {
//			ApplicationContext.logger.severe( "DatabaseHandler", e);
//		}
//		return arr;
//	}

//	public ArrayList<String> getLocationsBasedOnSeries(String studyUid, String seriesUid) {
//		openConnection();
//		ArrayList<String> locations = new ArrayList<String>();
//		try {
//			ResultSet locationInfo = conn.createStatement()
//					.executeQuery("select FileStoreUrl,SOPInstanceUID from image where StudyInstanceUID='" + studyUid
//							+ "' and SeriesInstanceUID='" + seriesUid + "' and SOPClassUID not in('"
//							+ UID.VideoEndoscopicImageStorage + "','" + UID.VideoMicroscopicImageStorage + "','"
//							+ UID.VideoPhotographicImageStorage + "')");
//			while (locationInfo.next()) {
//				locations.add(locationInfo.getString("FileStoreUrl") + "," + locationInfo.getString("SOPInstanceUID"));
//			}
//			locationInfo.close();
//		} catch (SQLException ex) {
//			logger.severe("DatabaseHandler", ex);
//		}finally {
//			safeClose(conn);
//		}
//		return locations;
//	}

	public int getSeriesLevelInstance(String studyUid, String seriesUid) {
		Connection conn = openConnection();
		int totalInstance = 0;
		try {
			ResultSet rs = conn.createStatement().executeQuery("select count(*) from image where StudyInstanceUID='"
					+ studyUid + "' AND " + "SeriesInstanceUID='" + seriesUid + "'");// AND multiframe = false");
			rs.next();
			totalInstance = rs.getInt(1);
			rs.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return totalInstance;
	}

	public String getInstanceUIDBasedOnSliceLocation(String studyUid, String seriesUid, String sliceLocation,
			String sliceThickness) {
		Connection conn = openConnection();
		String fileStoreUrl = null;
		try {
			double sliceLocTemp = 0, sliceThicknessTemp = 0;
			if (sliceLocation != null && !sliceLocation.equals("")) {
				sliceLocTemp = Double.parseDouble(sliceLocation);
				if (!sliceThickness.equals("")) {
					sliceThicknessTemp = Double.parseDouble(sliceThickness);
				}
			}
			String sql = "select SOPInstanceUID,SliceLocation from image where StudyInstanceUID='" + studyUid
					+ "' and SeriesInstanceUID='" + seriesUid + "' and SliceLocation between "
					+ (sliceLocTemp - sliceThicknessTemp) + " and " + (sliceLocTemp + sliceThicknessTemp);
			ResultSet rs = conn.createStatement().executeQuery(sql);
			if (rs.next()) {
				fileStoreUrl = rs.getString("SOPInstanceUID");
			}
			rs.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return fileStoreUrl;
	}

	public ArrayList<String> getSeriesInstancesLocation(String studyUid) {
		Connection conn = openConnection();
		ArrayList<String> locations = new ArrayList<>();
		try {
			ResultSet seriesInfo = conn.createStatement().executeQuery(
					"select SeriesInstanceUID from Series where StudyInstanceUID='" + studyUid + "' order by SeriesNumber");
			while (seriesInfo.next()) {
				ResultSet location = conn.createStatement()
						.executeQuery("select FileStoreUrl from image where StudyInstanceUID='" + studyUid
								+ "' and SeriesInstanceUID='" + seriesInfo.getString("SeriesInstanceUID")
								+ "' and multiframe=false" + " order by InstanceNumber asc");
				ResultSet multiframesInfo = conn.createStatement()
						.executeQuery("select FileStoreUrl from image where StudyInstanceUID='" + studyUid
								+ "' and SeriesInstanceUID='" + seriesInfo.getString("SeriesInstanceUID")
								+ "' and multiframe=true" + " order by InstanceNumber asc");
				if (location.next()) {
					locations.add(location.getString("FileStoreUrl"));
				}
				while (multiframesInfo.next()) {
					locations.add(multiframesInfo.getString("FileStoreUrl"));
				}
				location.close();
				multiframesInfo.close();
			}
			seriesInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return locations;
	}

	public String getFirstInstanceLocation(String studyUid, String seriesInstanceUid) {
		Connection conn = openConnection();
		try {
			ResultSet locationInfo = conn.createStatement()
					.executeQuery("select FileStoreUrl from image where StudyInstanceUID='" + studyUid
							+ "' and SeriesInstanceUID='" + seriesInstanceUid + "'" + " order by InstanceNumber asc");
			locationInfo.next();
			return locationInfo.getString("FileStoreUrl");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return null;
	}

	public String getFirstInstanceLocation(String studyUid) {
		Connection conn = openConnection();
		try {
			ResultSet locationInfo = conn.createStatement().executeQuery(
					"select FileStoreUrl from image where StudyInstanceUID='" + studyUid + "' order by InstanceNumber asc");
			locationInfo.next();
			return locationInfo.getString("FileStoreUrl");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return null;
	}

	public String getFileLocation(String patID, String studyUID, String seriesUID, String sopUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		String loc = null;
		try {
			String statement = "SELECT FileStoreUrl FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			pstmt.setString(4, sopUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if (rset.next()) {
				loc = rset.getString("FileStoreUrl");
			}
			pstmt.close();
			rset.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return loc;
	}

	public String getFileLocation(String patID, String studyUID, String seriesUID, int instanceNo) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		String url = null;
		try {
			String statement = "SELECT FileStoreUrl FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND InstanceNumber=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			pstmt.setInt(4, instanceNo);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if (rset.next()) {
				url = rset.getString("FileStoreUrl");
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return url;
	}
	
	public ArrayList<String> getFileLocationOrderByInstNo(String patID, String studyUID, String seriesUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		ArrayList<String> imagePaths = new ArrayList<String>();
		try {
			String statement = "SELECT FileStoreUrl FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? ORDER BY InstanceNumber";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			while (rset.next()) {
				imagePaths.add(rset.getString("FileStoreUrl"));
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return imagePaths;
	}
	
	public int getInstanceNo(String patID, String studyUid, String seriesUid, String sopUid) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			pstmt.setString(4, sopUid);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if (rset.next()) {
				return rset.getInt("InstanceNumber");
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return -1;
	}
	
	public String getParticularInfoFromPatient(String targetColName, String patID) {
		Connection conn = openConnection();
		String result = null;
		ResultSet rset = null;
		try {
			String statement = "SELECT * FROM PATIENT WHERE PatientID=?";
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if (rset.next()) {
				result = rset.getString(targetColName);
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				conn.commit();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public String getParticularInfoFromStudy(String targetColName, String patID, String studyUid) {
		Connection conn = openConnection();
		ResultSet rset = null;
		String something = null;
		try {
			String statement = "SELECT * FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?";
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUid);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if (rset.next()) {
				something = rset.getString(targetColName);
			}
			pstmt.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return something;
	}
	
	public String getParticularInfoFromSeries(String targetColName, String patID, String studyUid, String seriesUid) {
		Connection conn = openConnection();
		ResultSet rset = null;
		String something = null;
		try {
			String statement = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if (rset.next()) {
				something = rset.getString(targetColName);
			}
			pstmt.close();
			rset.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return something;
	}
	
	public String getParticularInfoFromImage(String targetColName,String patID, String studyUID, String seriesUID, String sopUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		String something = null;
		try {
			String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			pstmt.setString(4, sopUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if (rset.next()) {
				something = rset.getString(targetColName);
			}
			conn.commit();
			pstmt.close();
			rset.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return something;
	}

	public ArrayList<HashMap<String,String>> listStudies(String patientName, String patientID, String dob, String accNo,
		String studyDate, String studyDesc, String modality) {
		Connection conn = openConnection();
		ArrayList<HashMap<String,String>> result = new ArrayList<>();
		HashMap<String,String> matchingStudies = new HashMap<String,String>();
		ResultSet matchingInfo = null;
		try {
			matchingInfo = conn.createStatement().executeQuery(
					"select * from patient inner join study on patient.PatientID=study.PatientID where upper(patient.PatientID) like '"
							+ patientID + "' and upper(patient.PatientName) like '" + patientName
							+ "' and patient.PatientBirthDate like '" + dob + "' and upper(study.AccessionNumber) like '"
							+ accNo + "' and study.StudyDate like '" + studyDate
							+ "' and upper(study.StudyDescription) like '" + studyDesc
							+ "' and upper(study.ModalitiesInStudy) like '" + modality + "'");
			while (matchingInfo.next()) {
				matchingStudies.put("PatientID", matchingInfo.getString("PatientName"));
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
			conn.commit();
			matchingInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		} catch (NumberFormatException nfe) {
			logger.severe(nfe.getMessage());
		}finally {
				safeClose(conn);
		}
		return result;
	}

	public String getJNLPRetrieveType() {
		Connection conn = openConnection();
		String JNLPRetrieveType = null;
		try {
			ResultSet retrieveInfo = conn.createStatement().executeQuery("select JNLPRetrieveType from miscellaneous");
			retrieveInfo.next();
			JNLPRetrieveType = retrieveInfo.getString("JNLPRetrieveType");
			conn.commit();
			retrieveInfo.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return JNLPRetrieveType;
	}

	public boolean getDynamicRetrieveTypeStatus() {
//		Connection conn = openConnection();
//		try {
//			ResultSet retrieveInfo = conn.createStatement()
//					.executeQuery("select AllowDynamicRetrieveType from miscellaneous");
//			retrieveInfo.next();
//			return retrieveInfo.getBoolean("AllowDynamicRetrieveType");
//		} catch (SQLException ex) {
//			logger.severe("DatabaseHandler", ex);
//		}finally {
//			conn.commit();
//			if(open) {
//				safeClose(conn);
//			}
//		}
		return false;
	}

	// Added for Memory Handling
	public ArrayList<String> getInstanceUidList(String patID, String studyUid, String seriesUid) {
		Connection conn = openConnection();
		ArrayList<String> sopUids = new ArrayList<String>();
		try {
			ResultSet imageLocations = conn.createStatement().executeQuery(
					"select SOPInstanceUID from image where PatientID='"+patID+"' and StudyInstanceUID='" + studyUid + "' and SeriesInstanceUID='"
							+ seriesUid + "' order by InstanceNumber, FileStoreUrl");
			//もしマルチフレームを必要としなければ。
//			ResultSet imageLocations = conn.createStatement().executeQuery(
//					"select SOPInstanceUID from image where StudyInstanceUID='" + studyUid + "' and SeriesInstanceUID='"
//							+ seriesUid + "' and multiframe=false order by InstanceNumber,FileStoreUrl");
			while (imageLocations.next()) {
				sopUids.add(imageLocations.getString("SOPInstanceUID"));
			}
			imageLocations.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return sopUids;
	}
	
	public ArrayList<String> getStudyUidList(String patID) {
		Connection conn = openConnection();
		ArrayList<String> studyUids = new ArrayList<String>();
		try {
			ResultSet rset = conn.createStatement()
					.executeQuery("select StudyInstanceUID from STUDY where PatientID='" + patID + "'");
			while (rset.next()) {
				studyUids.add(rset.getString("StudyInstanceUID"));
			}
			rset.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		} finally {
				safeClose(conn);
		}
		return studyUids;
	}
	
	public ArrayList<String> getSeriesUidList(String patID, String studyUID) {
		Connection conn = openConnection();
		ArrayList<String> seriesUids = new ArrayList<String>();
		try {
			ResultSet rset = conn.createStatement()
					.executeQuery("select SeriesInstanceUID from SERIES where PatientID='" + patID + "'" + "and StudyInstanceUID='" + studyUID+"'");
			while (rset.next()) {
				seriesUids.add(rset.getString("SeriesInstanceUID"));
			}
			rset.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		} finally {
				safeClose(conn);
		}
		return seriesUids;
	}
	
	public HashMap<String,String> getInfoset(String patID, String studyUID, String seriesUID) {
		HashMap<String,String> infoset = new HashMap<String,String>();
		infoset.put("PatientID", patID);
		infoset.put("PatientName", getParticularInfoFromPatient("PatientName",patID));
		String studyDate = getParticularInfoFromStudy("StudyDate", patID, studyUID);
		infoset.put("StudyDate", studyDate);
		ArrayList<String> seriesUids = getSeriesUidList(patID, studyUID);
		HashSet<String> modalities = new HashSet<>();
		String modalitiesString = "";
		for(String seUid:seriesUids) {
			String m = getParticularInfoFromSeries("Modality", patID, studyUID, seUid);
			if(m != null) {
				modalities.add(m);
			}
		}
		for(Object m_ :modalities.toArray()) {
			modalitiesString += (String)m_ + ",";
		}
		modalitiesString = modalitiesString.substring(0, modalitiesString.length()-1);
//		infoset.put("Modality", getParticularInfoFromStudy("Modality", patID, studyUID));//, seriesUID));
		infoset.put("Modality", modalitiesString);
		String bod = getParticularInfoFromPatient("PatientBirthDate",patID);
		infoset.put("PatientBirthDate", bod);
		infoset.put("PatientSex", getParticularInfoFromPatient("PatientSex",patID));
		//calc age when study performed.
		Integer age = Utils.calculateAge(bod, studyDate);
		infoset.put("PatientAge", age == null ? null : String.valueOf(age));
		return infoset;
	}
	
	public ArrayList<String> getStudyAndSeriesUID(String sopUid) {
		Connection conn = openConnection();
		ArrayList<String> uids = new ArrayList<>();
//		StudyUID
		try {
			ResultSet rs = conn.createStatement().executeQuery("select StudyInstanceUID" + " from IMAGE"
					+ " where SOPInstanceUID" + " = '" + sopUid.trim() + "'");
			if(rs.next()) {
				uids.add(rs.getString("StudyInstanceUID"));
			}
			rs.close();
			conn.commit();
		} catch (Exception e) {
			return null;
		}
//		SeriesUID
		try {
			ResultSet rs = conn.createStatement().executeQuery("select SeriesInstanceUID" + " from IMAGE"
					+ " where SOPInstanceUID" + " = '" + sopUid.trim() + "'");
			if(rs.next()) {
				uids.add(rs.getString("SeriesInstanceUID"));
			}
			rs.close();
		} catch (Exception e) {
			return null;
		}finally {
			safeClose(conn);
		}
		return uids;
	}
	
	public ArrayList<String> getStudyAndSeriesDescription(String studyUid, String seriesUid) {
		Connection conn = openConnection();
		ArrayList<String> descriptions = new ArrayList<>();
//		StudyUID
		try {
			ResultSet rs = conn.createStatement().executeQuery("select StudyDescription" + " from STUDY"
					+ " where StudyInstanceUID" + " = '" + studyUid.trim() + "'");
			if(rs.next()) {
				descriptions.add(rs.getString("StudyDescription"));
			}
			rs.close();
			conn.commit();
		} catch (Exception e) {
			return null;
		}
//		SeriesUID
		try {
			ResultSet rs = conn.createStatement().executeQuery("select SeriesDescription" + " from SERIES"
					+ " where SeriesInstanceUID" + " = '" + seriesUid.trim() + "'");
			if(rs.next()) {
				descriptions.add(rs.getString("SeriesDescription"));
			}
			rs.close();
		} catch (Exception e) {
			return null;
		}finally {
			safeClose(conn);
		}
		return descriptions;
	}
	
	public String getSeriesNo(String seriesUid) {
		Connection conn = openConnection();
		String SeriesNumber = null;
		try {
			ResultSet rs = conn.createStatement().executeQuery("select SeriesNumber" + " from SERIES"
					+ " where SeriesInstanceUID" + " = '" + seriesUid.trim() + "'");
			if(rs.next()) {
				SeriesNumber = rs.getString("SeriesNumber");
			}
			conn.commit();
			rs.close();
		} catch (Exception e) {
			return null;
		}finally {
			safeClose(conn);
		}
		return SeriesNumber;
	}

//	public ScoutLineInfoModel[] getFirstAndLastInstances(String studyUid, String seriesUid) {
//		openConnection();
//		ScoutLineInfoModel[] borderLines = new ScoutLineInfoModel[2];
//		try {
//			ResultSet scoutDetails = conn.createStatement().executeQuery(
//					"select ImagePosition,ImageOrientation,PixelSpacing,NoOfRows,NoOfColumns,FrameOfReferenceUID,ReferencedSOPInstanceUID,ImageType,SliceLocation from Image where StudyInstanceUID='"
//							+ studyUid + "' and SeriesInstanceUID='" + seriesUid
//							+ "' and InstanceNumber in(select min(InstanceNumber) from image where StudyInstanceUID='"
//							+ studyUid + "' and SeriesInstanceUID='" + seriesUid
//							+ "' and ImageType not in('LOCALIZER'))");
//			scoutDetails.next();
//			borderLines[0] = new ScoutLineInfoModel(scoutDetails.getString("ImagePosition"),
//					scoutDetails.getString("ImageOrientation"), scoutDetails.getString("PixelSpacing"),
//					scoutDetails.getInt("NoOfRows"), scoutDetails.getInt("NoOfColumns"),
//					scoutDetails.getString("FrameOfReferenceUID"), scoutDetails.getString("ReferencedSOPInstanceUID"),
//					scoutDetails.getString("ImageType"), scoutDetails.getString("SliceLocation"));
//			scoutDetails = conn.createStatement().executeQuery(
//					"select ImagePosition,ImageOrientation,PixelSpacing,NoOfRows,NoOfColumns,FrameOfReferenceUID,ReferencedSOPInstanceUID,ImageType,SliceLocation from image where StudyInstanceUID='"
//							+ studyUid + "' and SeriesInstanceUID='" + seriesUid
//							+ "' and InstanceNumber in(select max(InstanceNumber) from image where StudyInstanceUID='"
//							+ studyUid + "' and SeriesInstanceUID='" + seriesUid
//							+ "') and ImageType not in('LOCALIZER')");
//			scoutDetails.next();
//			borderLines[1] = new ScoutLineInfoModel(scoutDetails.getString("ImagePosition"),
//					scoutDetails.getString("ImageOrientation"), scoutDetails.getString("PixelSpacing"),
//					scoutDetails.getInt("NoOfRows"), scoutDetails.getInt("NoOfColumns"),
//					scoutDetails.getString("FrameOfReferenceUID"), scoutDetails.getString("ReferencedSOPInstanceUID"),
//					scoutDetails.getString("ImageType"), scoutDetails.getString("SliceLocation"));
//			scoutDetails.close();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//		return borderLines;
//	}

//	public ScoutLineInfoModel getScoutLineDetails(String studyUid, String seriesUid, String instanceUid) {
//		openConnection();
//		try {
//			ResultSet scoutDetails = conn.createStatement().executeQuery(
//					"select ImagePosition,ImageOrientation,PixelSpacing,NoOfRows,NoOfColumns,FrameOfReferenceUID,ReferencedSOPInstanceUID,ImageType,SliceLocation from Image where StudyInstanceUID='"
//							+ studyUid + "' and SeriesInstanceUID='" + seriesUid + "' and SOPInstanceUID='" + instanceUid
//							+ "'");
//			while (scoutDetails.next()) {
//				return new ScoutLineInfoModel(scoutDetails.getString("ImagePosition"),
//						scoutDetails.getString("ImageOrientation"), scoutDetails.getString("PixelSpacing"),
//						scoutDetails.getInt("NoOfRows"), scoutDetails.getInt("NoOfColumns"),
//						scoutDetails.getString("FrameOfReferenceUID"), scoutDetails.getString("ReferencedSOPInstanceUID"),
//						scoutDetails.getString("ImageType"), scoutDetails.getString("SliceLocation"));
//			}
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//		return null;
//	}

	public String getSlicePosition(String studyUid, String seriesUid, String instanceUid) {
		Connection conn = openConnection();
		ResultSet sliceInfo = null;
		String pos = null;
		try {
			sliceInfo = conn.createStatement().executeQuery("select SliceLocation from image where StudyInstanceUID='"
					+ studyUid + "' and SeriesInstanceUID='" + seriesUid + "' and SOPInstanceUID='" + instanceUid + "'");
			sliceInfo.next();
			pos = sliceInfo.getString("SliceLocation");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		} finally {
			try {
				sliceInfo.close();
			} catch (SQLException ex) {
				logger.severe(ex.getMessage());
			} catch (NullPointerException ex) {
				// ignore
			}finally {
				safeClose(conn);
			}
		}
		return pos;
	}

	public String getThumbnailLocation(String studyUid, String seriesUid) {
		Connection conn = openConnection();
		ResultSet info = null;
		try {
			info = conn.createStatement()
					.executeQuery("select FileStoreUrl,StudyInstanceUID from image where StudyInstanceUID='" + studyUid
							+ "' and SeriesInstanceUID='" + seriesUid + "'");
			info.next();
			if (info.getString("FileStoreUrl").contains(getLocalDBLocation())) {
				String location = new File(info.getString("FileStoreUrl")).getParent() + File.separator + "Thumbnails";
				return location;
			} else {
				return getLocalDBLocation() + File.separator + "Thumbnails" + File.separator
						+ info.getString("StudyInstanceUID");
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				info.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			safeClose(conn);
		}
		return null;
	}
	
	public int getNumOfStudyParticularPatient(String patID) {
		Connection conn = openConnection();
//		"select count(*) from series where PatientID='"+patID+"'"+" and StudyInstanceUID='" + studyUid + "'");
//		String statement = "SELECT COUNT(StudyInstanceUID) FROM STUDY WHERE PatientID='"+"?'";
		String statement = "SELECT COUNT(StudyInstanceUID) FROM STUDY WHERE PatientID=?";
		ResultSet studyCount = null;
		int count = -1;
		try {
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			studyCount = pstmt.executeQuery();
			if(studyCount.next()) {
				count = studyCount.getInt(1);
			}
			studyCount.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return count;
	}

	public int getNumOfSeries(String patID, String studyUID) {
		Connection conn = openConnection();
		String statement = "SELECT COUNT(SeriesInstanceUID) FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?";
		ResultSet rset = null;
		int count = -1;
		try {
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			rset = pstmt.executeQuery();
			if(rset.next()){
				count = rset.getInt(1);
			}
			rset.close();
			pstmt.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return count;
	}
	
	public int getNumOfInstanceInSeries(String patID, String studyUID, String seriesUID) {
		Connection conn = openConnection();
		int size = 0;
		String statement = "SELECT COUNT(SOPInstanceUID) FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		//it is also OK.
//		String statement = "SELECT COUNT(SOPInstanceUID) FROM IMAGE WHERE PatientID='"+patID+"'"+" AND StudyInstanceUID='"+studyUID+"'"+" AND SeriesInstanceUID='"+seriesUID+"'";
		ResultSet rset = null;
		try {
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			rset = pstmt.executeQuery();
			while (rset.next()) {
//			    size += 1;//DO NOT USE
				size = rset.getInt(1);
			}
			rset.close();
			pstmt.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return size;
	}
	
	public int getNumOfInstanceStudy(String patID, String studyUID) {
		Connection conn = openConnection();
		String statement = "SELECT COUNT(SOPInstanceUID) FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=?";
		ResultSet rset = null;
		int count = -1;
		try {
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			rset = pstmt.executeQuery();
			if(rset.next()){
				count = rset.getInt(1);
			}
			pstmt.close();
			rset.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return count;
	}
	
	public int getNumOfInstancePatient(String patID) {
		Connection conn = openConnection();
		String statement = "SELECT COUNT(SOPInstanceUID) FROM IMAGE WHERE PatientID=?";
		ResultSet rset = null;
		int count = -1;
		try {
			PreparedStatement pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			rset = pstmt.executeQuery();
			if(rset.next()){
				count = rset.getInt(1);
			}
			rset.close();
			pstmt.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return count;
	}

	/*
	 * マテリアルに書き換える
	 * 
	 */
//	public DicomCommunicationNode getServersToSend(String nickname) {
//		Connection conn = openConnection();
//		DicomCommunicationNode svr = null;
//		String statement = "SELECT * FROM SERVERS WHERE logicalname=?";
//		ResultSet rset = null;
//		try {
//			PreparedStatement pstmt = conn.prepareStatement(statement);
//			pstmt.setString(1, nickname);
//			rset = pstmt.executeQuery();
//			if (rset.next()) {
//				svr = new DicomCommunicationNode(
//						rset.getString("logicalname"),
//						rset.getString("aetitle"), 
//						rset.getString("hostname"),
//						rset.getInt("port"),
//						rset.getString("ciphers")
//						);
//			}
//			rset.close();
//		} catch (SQLException ex) {
//			logger.severe("DatabaseHandler", ex);
//		}finally {
//			if(opened) {
//				safeClose(conn);
//			}
//		}
//		return svr;
//	}

	/**
	 * 
	 * @param studyUid
	 * @param seriesUid
	 * @param multiframe
	 * @return FileStoreUrl list
	 */
	public ArrayList<String> getInstances(String studyUid, String seriesUid, String multiframe) {
		Connection conn = openConnection();
		ArrayList<String> instances = new ArrayList<String>();
		String sql = "select FileStoreUrl from image where StudyInstanceUID='" + studyUid + "'";
		if (seriesUid != null) {
			sql += " and SeriesInstanceUID='" + seriesUid + "'";
		}
		if (multiframe != null) {
			sql += " and multiframe=" + multiframe;
		}
		sql += " order by InstanceNumber";
		try {
			ResultSet rs = conn.createStatement().executeQuery(sql);
			while (rs.next()) {
				instances.add(rs.getString("FileStoreUrl"));
			}
			rs.close();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return instances;
	}

	public ArrayList<String> getInstancesLoc(String studyUid, String seriesUid) {
		Connection conn = openConnection();
		ArrayList<String> locations = new ArrayList<String>();
		try {
			ResultSet instanceInfo = conn.createStatement()
					.executeQuery("select FileStoreUrl,SOPInstanceUID from image where StudyInstanceUID='" + studyUid
							+ "' and SeriesInstanceUID='" + seriesUid + "'" + " order by InstanceNumber asc");
			while (instanceInfo.next()) {
				locations.add(instanceInfo.getString("FileStoreUrl"));
			}
			instanceInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return locations;
	}
	
	public ArrayList<String> getFileLocations(String patID, String studyUid, String seriesUid) {
		Connection conn = openConnection();
		ArrayList<String> locations = new ArrayList<String>();
		try {
			ResultSet instanceInfo = conn.createStatement()
					.executeQuery("select FileStoreUrl from image where PatientID='"+patID+"' and StudyInstanceUID='" + studyUid
							+ "' and SeriesInstanceUID='" + seriesUid + "'" + " order by InstanceNumber");
			while (instanceInfo.next()) {
				locations.add(instanceInfo.getString("FileStoreUrl"));
			}
			instanceInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return locations;
	}
	
	/**
	 * return all instances file locations with specified patienid.
	 * @param patID
	 * @return
	 */
	public List<String> getFileLocationsByPid(String patID) {
		if (patID == null) {
			return null;
		}
		Connection conn = openConnection();
		List<String> locs = new ArrayList<String>();
		ResultSet rset = null;
		try {
			PreparedStatement pstmt = conn.prepareStatement("SELECT FileStoreUrl FROM IMAGE WHERE PatientID=?");
			pstmt.setString(1, patID);//start from 1
			rset = pstmt.executeQuery();
			while(rset.next()) {
				locs.add(rset.getString("FileStoreUrl"));
			}
			rset.close();
			pstmt.close();
			conn.commit();			
			if(locs.isEmpty()) {
				return null;
			}else {
				List<String> noduplicate = new ArrayList<String>(new HashSet<>(locs));
				return noduplicate;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return null;
	}
	
	public List<String[]> getUIDsByFileLocations(ArrayList<String> fileLocs) {
		if (fileLocs == null) {
			return null;
		}
		
		List<String[]> idsetList = new ArrayList<String[]>();
		for(int i=0;i<fileLocs.size();i++) {
			Connection conn = openConnection();
			ResultSet rset = null;
			try {
				PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM IMAGE WHERE FileStoreUrl=?");
				pstmt.setString(1, fileLocs.get(i));//start from 1
				rset = pstmt.executeQuery();
				while(rset.next()) {
					String idset[] = new String[4];
					idset[0] = rset.getString("PatientID");
					idset[1] = rset.getString("StudyInstanceUID");
					idset[2] = rset.getString("SeriesInstanceUID");
					idset[3] = rset.getString("SOPInstanceUID");
					idsetList.add(idset);
				}
				rset.close();
				pstmt.close();
				conn.commit();
			} catch (SQLException ex) {
				logger.severe(ex.getMessage());
			}finally {
					safeClose(conn);
			}
		}
		return idsetList;
	}

	public HashMap<String, String> getInstancesLocMap(String studyUid, String seriesUid) {
		Connection conn = openConnection();
		HashMap<String, String> locationsMap = new HashMap<>();
		try {
			ResultSet instanceInfo = conn.createStatement()
					.executeQuery("select FileStoreUrl,SOPInstanceUID from image where StudyInstanceUID='" + studyUid
							+ "' and SeriesInstanceUID='" + seriesUid + "'" + " order by InstanceNumber asc");
			while (instanceInfo.next()) {
				locationsMap.put(instanceInfo.getString("FileStoreUrl"), instanceInfo.getString("SOPInstanceUID"));
			}
			instanceInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return locationsMap;
	}

	public ArrayList<String> getSeriesFileURL(String studyUid) {
		Connection conn = openConnection();
		ArrayList<String> seriesFileURLs = new ArrayList<String>();
		try {
			ResultSet seriesInfo = conn.createStatement()
					.executeQuery("select SeriesInstanceUID from series where StudyInstanceUID='" + studyUid + "'");
			while (seriesInfo.next()) { // Series Iteration
				ResultSet imageInfo = conn.createStatement()
						.executeQuery("select FileStoreUrl from image where StudyInstanceUID='" + studyUid
								+ "' and SeriesInstanceUID='" + seriesInfo.getString("SeriesInstanceUID")
								+ "' and multiframe=false order by InstanceNumber");
				if (imageInfo.next()) {
					seriesFileURLs.add(imageInfo.getString("FileStoreUrl"));
				}
				imageInfo.close();
				conn.commit();
				ResultSet multiframeImageInfo = conn.createStatement()
						.executeQuery("select FileStoreUrl from image where StudyInstanceUID='" + studyUid
								+ "' and SeriesInstanceUID='" + seriesInfo.getString("SeriesInstanceUID")
								+ "' and multiframe=true order by InstanceNumber");
				if (multiframeImageInfo.next()) {
					seriesFileURLs.add(multiframeImageInfo.getString("FileStoreUrl"));
				}
				multiframeImageInfo.close();
				conn.commit();
			}
			seriesInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return seriesFileURLs;
	}
	
	public List<String> getModalitiesInStudyRealatedAllSeries(String patID,String studyUID) {
		if (getNumOfSeries(patID, studyUID) < 1 ) {
			return null;
		}
		Connection conn = openConnection();
		List<String> modalities = new ArrayList<String>();
		ResultSet rset = null;
		try {
			PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?");
			pstmt.setString(1, patID);//start from 1
			pstmt.setString(2, studyUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(1000);
			while(rset.next()) {
				modalities.add(rset.getString("Modality"));
			}
			rset.close();
			pstmt.close();
			conn.commit();			
			if(modalities.isEmpty()) {
				return null;
			}else {
				List<String> noduplicate = new ArrayList<String>(new HashSet<>(modalities));
				return noduplicate;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
				safeClose(conn);
		}
		return null;
	}
	
	public ArrayList<String> getAllInstanceUIDsFromSTUDY(String studyUid){
		Connection conn = openConnection();
		ArrayList<String> instanceUIDs = new ArrayList<>();
		try {
			ResultSet seriesInfo = conn.createStatement()
					.executeQuery("select SeriesInstanceUID from series where StudyInstanceUID='" + studyUid + "'");
			while (seriesInfo.next()) { // Series Iteration				
				ArrayList<String> images = new ArrayList<String>();
				try {
					ResultSet imageLocations = conn.createStatement().executeQuery(
							"select SOPInstanceUID from image where StudyInstanceUID='" + studyUid + "' and SeriesInstanceUID='"
									+ seriesInfo.getString("SeriesInstanceUID") + "' order by InstanceNumber, FileStoreUrl");
					//もしマルチフレームを必要としなければ。
//					ResultSet imageLocations = conn.createStatement().executeQuery(
//							"select SOPInstanceUID from image where StudyInstanceUID='" + studyUid + "' and SeriesInstanceUID='"
//									+ seriesUid + "' and multiframe=false order by InstanceNumber,FileStoreUrl");
					while (imageLocations.next()) {
						images.add(imageLocations.getString("SOPInstanceUID"));
					}
					conn.commit();
					imageLocations.close();
				} catch (SQLException ex) {
					logger.severe(ex.getMessage());
				}
				instanceUIDs.addAll(images);
			}
			seriesInfo.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return instanceUIDs;
	}

	public int getTotalInstancesInStudy(String studyUid) {
		Connection conn = openConnection();
		int total = 0;
		try {
			ResultSet totalInstancesInfo = conn.createStatement().executeQuery(
					"select count(SOPInstanceUID) from image where StudyInstanceUID='" + studyUid + "' and multiframe=false");
			totalInstancesInfo.next();
			total = totalInstancesInfo.getInt(1);
			totalInstancesInfo = conn.createStatement().executeQuery(
					"select count(SOPInstanceUID) from image where StudyInstanceUID='" + studyUid + "' and multiframe=true");
			totalInstancesInfo.next();
			total += totalInstancesInfo.getInt(1);
			totalInstancesInfo.close();
			conn.commit();
			return total;
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
		return 0;
	}
	
	public String getInstancePathUsingSOPInstanceUID(String sopIUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(
					"SELECT * FROM IMAGE WHERE SOPInstanceUID=?");
			pstmt.setString(1, sopIUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(5);//but, always only have one row.
			if(rset.next()) {
				return rset.getString("FileStoreUrl");
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				pstmt.close();
				rset.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public ArrayList<HashMap<String,String>> getImageInstanceInfo(String pid,String studyIUID,String seriesIUID, String sopIUID) {
		Connection conn = openConnection();
		String statement = "SELECT * FROM IMAGE WHERE";
		HashMap<Integer,String> keymap = new HashMap<Integer, String>();
		int pos = 1;
		if(pid != null) {
			statement = statement+" PatientID=?";
			keymap.put(pos, pid);
			pos++;
		}
		if(studyIUID != null) {
			if(pos == 1) {
				statement = statement+" StudyInstanceUID=?";
			}else {
				statement = statement+" AND StudyInstanceUID=?";
			}
			keymap.put(pos, studyIUID);
			pos++;
		}
		if(seriesIUID != null) {
			if(pos == 1) {
				statement = statement+" SeriesInstanceUID=?";
			}else {
				statement = statement+" AND SeriesInstanceUID=?";
			}
			keymap.put(pos, seriesIUID);
			pos++;
		}
		if(sopIUID != null) {
			if(pos == 1) {
				statement = statement+" SOPInstanceUID=?";
			}else {
				statement = statement+" AND SOPInstanceUID=?";
			}
			keymap.put(pos, studyIUID);
			pos++;
		}		
		
		//get result
		ArrayList<HashMap<String,String>> result = new ArrayList<>();
		HashMap<String,String> map = null;
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			for(int keypos:keymap.keySet()) {
				pstmt.setString(keypos, keymap.get(keypos));
			}
			rset = pstmt.executeQuery();
			rset.setFetchSize(10000);//limitation
			while(rset.next()) {
//				String cuid = instRec.getString(Tag.ReferencedSOPClassUIDInFile);
//				String iuid = instRec.getString(Tag.ReferencedSOPInstanceUIDInFile);
//				String tsuid = instRec.getString(Tag.ReferencedTransferSyntaxUIDInFile);
//				String uri = ddr.toFile(fileIDs).toURI().toString();
				map = new HashMap<String, String>();
				map.put("URI", new File(rset.getString("FileStoreUrl")).toURI().toString());
				map.put("SOPInstanceUID", rset.getString("SOPInstanceUID"));
				map.put("SOPClassUID", rset.getString("SOPClassUID"));
				map.put("TransferSyntaxUID", rset.getString("TransferSyntaxUID"));
				if (!map.isEmpty()) {
					result.add(map);
				} 
			}
			if(!result.isEmpty()) {
				return result;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	//see, ***QueryTaskUsingDB
	public ArrayList<HashMap<String,String>> getAllCandidate4PatientQuery(String[] patIDs){
		ArrayList<HashMap<String,String>> patCandidate = new ArrayList<>();
		if(patIDs != null && patIDs.length != 0) {
			/* search pid && othersPatientRelatedInfo */
			for(int i=0;i<patIDs.length;i++) {
				HashMap<String,String> patInfo = getPatientInfoByPatID(patIDs[i]);
				if(patInfo != null) {
					patCandidate.add(patInfo);
				}
			}
		}
		if(patCandidate != null && !patCandidate.isEmpty()) {
			return patCandidate;
		}
		return null;
	}
	
	/*
	 * return following info
	 * (0010,0010) PN [LGG-203] PatientName
	 * (0010,0020) LO [LGG-203] PatientID
	 * (0010,0030) DA [] PatientBirthDate
	 * (0010,0040) CS [M] PatientSex
	 */
	public HashMap<String, String> getPatientInfoByPatID(String patID) {
		if(patID == null) {
			return null;
		}
		Connection conn = openConnection();
		HashMap<String, String> map = new HashMap<>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement("SELECT * FROM PATIENT WHERE PatientID=?");
			pstmt.setString(1, patID);//start from 1
			rset = pstmt.executeQuery();
			if(rset.next()) {
				map.put("PatientID", patID);
				map.put("PatientName", rset.getString("PatientName"));
				map.put("PatientBirthDate", rset.getString("PatientBirthDate"));
				map.put("PatientSex", rset.getString("PatientSex"));
			}
			if(map.isEmpty()) {
				return null;
			}else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
//	
//	public ArrayList<HashMap<String, String>> findPatientRecordWithoutPatID(String pname, String bod/*yyyy/MM/dd*/, String sex/*M,F,O*/) {
//		Connection conn = openConnection();
//		ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String,String>>();
//		StringBuilder builder = new StringBuilder();
//		String statement = "SELECT * FROM PATIENT WHERE";
//		builder.append(statement);
//		HashMap<Integer,String> keymap = new HashMap<Integer, String>();
//		int keypos = 1;
//		builder.append(" PatientName LIKE ?");
//		keymap.put(keypos++, pname+"%");
//		builder.append(" PatientBirthDate=?");
//		keymap.put(keypos++, bod);
//		builder.append(" PatientSex=?");
//		keymap.put(keypos, sex);
//		
//		ResultSet rset = null;
//		PreparedStatement pstmt = null;
//		try {
//			pstmt = conn.prepareStatement(builder.toString());
//			for(int pos :keymap.keySet()) {
//				pstmt.setString(pos, keymap.get(pos));
//			}
//			rset = pstmt.executeQuery();
//			while(rset.next()) {
//				HashMap<String, String> map = new HashMap<>();
//				map.put("PatientID", rset.getString("PatientID"));
//				map.put("PatientName", rset.getString("PatientName"));
//				map.put("PatientBirthDate", rset.getString("PatientBirthDate"));
//				map.put("PatientSex", rset.getString("PatientSex"));
//				result.add(map);
//			}
//			if(result.isEmpty()) {
//				return null;
//			}else {
//				return result;
//			}
//		} catch (SQLException ex) {
//			logger.severe(ex.getMessage());
//		}finally {
//			try {
//				rset.close();
//				pstmt.close();
//				safeClose(conn);
//			} catch (SQLException e) {
//				e.printStackTrace();
//			}
//		}
//		return null;
//	}
	
	public ArrayList<HashMap<String,String>> getAllCandidate4StudyQuery(String patID, String[] studyIUIDs){
		ArrayList<HashMap<String,String>> studyCandidate = new ArrayList<>();
		if(studyIUIDs != null && studyIUIDs.length != 0) {
			/* search by studyIUID && othersStudyRelatedInfo */
			for(int i=0;i<studyIUIDs.length;i++) {
				HashMap<String,String> studyInfo = findStudyRecordByStudyIUID(patID, studyIUIDs[i]);
				if(studyInfo != null) {
					studyCandidate.add(studyInfo);
				}
			}
		}
		if(studyCandidate != null && !studyCandidate.isEmpty()) {
			return studyCandidate;
		}
		return null;
	}
	
	/*
	 * map.put("StudyInstanceUID", studyIUID); -> primary key of study level.
	 * map.put("StudyDate", rset.getString("StudyDate"));
	 * map.put("StudyTime", rset.getString("StudyTime"));
	 * map.put("AccessionNumber", rset.getString("AccessionNumber"));
	 * map.put("ReferringPhysicianName", rset.getString("ReferringPhysicianName"));
	 * map.put("StudyDescription", rset.getString("StudyDescription"));
	 * map.put("StudyID", rset.getString("StudyID"));
	 */
	public HashMap<String, String> findStudyRecordByStudyIUID(String patID/*null-able*/, String studyIUID) {
		Connection conn = openConnection();
		String statement = null;
		if(patID == null) {
			statement = "SELECT * FROM STUDY WHERE StudyInstanceUID=?";
		}else {
			statement = "SELECT * FROM STUDY WHERE PatientID="+patID+" StudyInstanceUID=?";
		}
		HashMap<String, String> map = new HashMap<>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, studyIUID);//start from 1
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//when studyIUID specified, will get only 1 study.
//			while(rset.next()) {//always return 1 data.
			if(rset.next()) { 
				map.put("StudyInstanceUID", studyIUID);
				map.put("StudyDate", rset.getString("StudyDate"));
				map.put("StudyTime", rset.getString("StudyTime"));
				map.put("AccessionNumber", rset.getString("AccessionNumber"));
				map.put("ReferringPhysicianName", rset.getString("ReferringPhysicianName"));
				map.put("StudyDescription", rset.getString("StudyDescription"));
				map.put("StudyID", rset.getString("StudyID"));
			}
			if(map.isEmpty()) {
				return null;
			}else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	
	/*
	 * input only one patient and one study
	 */
	public ArrayList<HashMap<String,String>> getAllCandidate4SeriesQuery(String patID, String studyIUID, String[] seriesIUIDs){
		ArrayList<HashMap<String,String>> seriesCandidate = new ArrayList<>();
		if(seriesIUIDs != null && seriesIUIDs.length != 0) {
			/* search by seriesIUID && othersSeriesRelatedInfo */
			for(int i=0;i<seriesIUIDs.length;i++) {
				HashMap<String,String> seriesInfo = findSeriesRecordWithSeriesIUIDAnd(patID,studyIUID,seriesIUIDs[i]);
				if(seriesInfo != null) {
					seriesCandidate.add(seriesInfo);
				}
			}
		}
		if(seriesCandidate != null && !seriesCandidate.isEmpty()) {
			return seriesCandidate;
		}
		return null;
	}
	
	/*
	 * input only one patient and one study
	 * 
	 * map.put("SeriesInstanceUID", seriesIUID);
	 * map.put("Modaity", rset.getString("Modality"));
	 * map.put("SeriesNumber", rset.getString("SeriesNumber"));
	 */
	public HashMap<String, String> findSeriesRecordWithSeriesIUIDAnd(String patID,String studyIUID, String seriesIUID) {
		Connection conn = openConnection();
		String statement = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		HashMap<String, String> map = new HashMap<>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, seriesIUID);
			rset = pstmt.executeQuery();
			if(rset.next()) { 
				map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
				map.put("Modality", rset.getString("Modality"));
				map.put("SeriesNumber", rset.getString("SeriesNumber"));
			}
			if(map.isEmpty()) {
				return null;
			}else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				conn.commit();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally {
				safeClose(conn);
			}
		}
		return null;
	}
	
//	public ArrayList<HashMap<String, String>> findSeriesRecordWithoutSeriesIUID(String patID, String studyIUID, Attributes keys) {
//		Connection conn = openConnection();
//		String statement = "SELECT * FROM SERIES WHERE";
//		HashMap<Integer,String> keymap = new HashMap<>();
//		int pos = 1;
//		if(patID == null) {
//			if(studyIUID == null) {
//				// do nothing
//			}else {
//				statement = statement+" StudyInstanceUID=?";
//				keymap.put(pos, studyIUID);
//				pos++;
//			}
//		}else {
//			if(studyIUID == null) {
//				statement = " PatientID=?";
//				keymap.put(pos, patID);
//				pos++;
//			}else {
//				statement = statement+" PatientID=? AND StudyInstanceUID=?";
//				keymap.put(pos, patID);
//				pos++;
//				keymap.put(pos, studyIUID);
//				pos++;
//			}
//		}
//		ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String,String>>();
//		/* check item in keys */
//		StringBuilder builder = new StringBuilder();
//		builder.append(statement);
//		for(int tag:keys.tags()) {
//			if(tag == Tag.Modality){
//				if(pos != 1) {
//					builder.append(" AND "+"Modality=?");
//				}else {
//					builder.append(" Modality=?");
//				}
//				keymap.put(pos,keys.getString(Tag.Modality));
//				pos++;
//			}
//			if(tag == Tag.SeriesNumber) {
//				if(pos != 1) {
//					builder.append(" AND "+"SeriesNumber=?");
//				}else {
//					builder.append(" SeriesNumber=?");
//				}
//				keymap.put(pos,keys.getString(Tag.SeriesNumber));
//				pos++;
//			}
//		}
//		HashMap<String, String> map = null;
//		ResultSet rset = null;
//		PreparedStatement pstmt = null;
//		try {
//			pstmt = conn.prepareStatement(builder.toString());
//			for(int keypos :keymap.keySet()) {
//				pstmt.setString(keypos, keymap.get(keypos));
//			}
//			rset = pstmt.executeQuery();
//			/* limit 30 series */
//			rset.setFetchSize(30);
//			while(rset.next()) {
//				map = new HashMap<>();
//				map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
//				map.put("Modality", rset.getString("Modality"));
//				map.put("SeriesNumber", rset.getString("SeriesNumber"));
//				/* もし追加したければここに増やす。 */
//				if(map.isEmpty()) {
//					continue;
//				}else {
//					result.add(map);
//				}
//			}
//			if(result.isEmpty()) {
//				return null;
//			}else {
//				return result;
//			}
//		} catch (SQLException ex) {
//			logger.severe(ex.getMessage());
//		}finally {
//			try {
//				rset.close();
//				pstmt.close();
//				safeClose(conn);
//			} catch (SQLException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		return null;
//	}
	
	public ArrayList<HashMap<String,String>> getAllCandidate4InstanceQuery(String patID, String studyIUID, String seriesIUID, String[] sopIUIDs){
		ArrayList<HashMap<String,String>> instCandidate = new ArrayList<>();
		if(sopIUIDs != null && sopIUIDs.length != 0) {
			/* search by sopIUID && othersInstanceRelatedInfo */
			for(int i=0;i<sopIUIDs.length;i++) {
				HashMap<String,String> instInfo = findImageRecordBySopIUID(patID,studyIUID,seriesIUID,sopIUIDs[i]);
				if(instInfo != null) {
					instCandidate.add(instInfo);
				}
			}
		}
		if(instCandidate != null && !instCandidate.isEmpty()) {
			return instCandidate;
		}
		return null;
	}
	
//	(0004,1500) CS [DICOM\6EFD8DF8\FF3A35F6\4C11115A] ReferencedFileID
//	(0004,1510) UI [1.2.840.10008.5.1.4.1.1.4] ReferencedSOPClassUIDInFile//same as SOPClassUID
//	(0004,1511) UI [1.3.6.1.4.1.14519.5.2.1.3344.2526.3991481572793857949648742095//same as SOP Instance UID
//	(0004,1512) UI [1.2.840.10008.1.2] ReferencedTransferSyntaxUIDInFile//same as TransferSyntaxUID
//	(0020,0013) IS [6] InstanceNumber//mandatory for directory record
	public HashMap<String, String> findImageRecordBySopIUID(String patID,String studyIUID, String seriesIUID, String sopIUID) {
		/* check item in keys */
		Connection conn = openConnection();
		String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		HashMap<String, String> map = new HashMap<>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, seriesIUID);
			pstmt.setString(4, sopIUID);
			rset = pstmt.executeQuery();
			if(rset.next()) { 
				map.put("ReferencedFileID", DicomUtilities.convertAbsPath2ReferencedFileID(rset.getString("FileStoreUrl"), rset.getBoolean("isLink")));
				map.put("SOPInstanceUID", sopIUID);
				map.put("SOPClassUID", rset.getString("SOPClassUID"));
				map.put("TransferSyntaxUID", rset.getString("TransferSyntaxUID"));
				map.put("InstanceNumber", rset.getString("InstanceNumber"));
			}
			if(map.isEmpty()) {
				return null;
			}else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	
	/*
	 * for study node builder
	 */
	public HashMap<String, String> getStudyInfoByUIDs(String patID,String studyUID) {
		Connection conn = openConnection();
		HashMap<String, String> map = new HashMap<>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			//study info
			pstmt = conn.prepareStatement("SELECT * FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?");
			pstmt.setString(1, patID);//start from 1
			pstmt.setString(2, studyUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);//but, always only have one row.
			if(rset.next()) {
				map.put("PatientID", patID);
				map.put("PatientAge", rset.getString("PatientAge"));//age is study level, not pat level.
				map.put("StudyDate", rset.getString("StudyDate"));
				map.put("StudyTime", rset.getString("StudyTime"));
				map.put("StudyID", rset.getString("StudyID"));
				map.put("StudyDescription", rset.getString("StudyDescription"));
				map.put("ModalitiesInStudy", rset.getString("ModalitiesInStudy"));
				map.put("AccessionNumber", rset.getString("AccessionNumber"));
				map.put("NumOfSeriesInStudy", String.valueOf(getNumOfSeriesInStudy(rset.getString("PatientID"), rset.getString("StudyInstanceUID"))));
				map.put("NumOfInstancesInStudy", String.valueOf(getNumOfInstancesInStudy(rset.getString("PatientID"), rset.getString("StudyInstanceUID"))));
				map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
			}
			
			if(map.isEmpty()) {
				return null;
			}else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public List<HashMap<String, String>> getSeriesInfoByUIDs(String patID,String studyUID) {
		if (getNumOfSeries(patID, studyUID) < 1 ) {
			return null;
		}
		Connection conn = openConnection();
		List<HashMap<String, String>> seriesInfoList = new ArrayList<HashMap<String,String>>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement("SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? order by SeriesNumber");
			pstmt.setString(1, patID);//start from 1
			pstmt.setString(2, studyUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(1000);
			while(rset.next()) {
				HashMap<String, String> map = new HashMap<>();
				map.put("PatientID", patID);
				map.put("SeriesDate", rset.getString("SeriesDate"));
				map.put("SeriesDescription", rset.getString("SeriesDescription"));
				map.put("Modality", rset.getString("Modality"));
				map.put("InstitutionName", rset.getString("InstitutionName"));
				map.put("ModelName", rset.getString("ModelName"));
				map.put("SeriesNumber", rset.getString("SeriesNumber"));
				map.put("NumOfInstanceInSeries", String.valueOf(getNumOfInstanceInSeries(rset.getString("PatientID"), rset.getString("StudyInstanceUID"), rset.getString("SeriesInstanceUID"))));
				map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
				map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
				seriesInfoList.add(map);
			}
			if(seriesInfoList.isEmpty()) {
				return null;
			}else {
				return seriesInfoList;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				conn.commit();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public HashMap<String, String> getSeriesInfoByUIDs(String patID,String studyUID,String seriesUID) {
		if (getNumOfSeries(patID, studyUID) < 1 ) {
			return null;
		}
		Connection conn = openConnection();
		HashMap<String, String> map = new HashMap<String,String>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement("SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?");
			pstmt.setString(1, patID);//start from 1
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);
			if(rset.next()) {
				map.put("PatientID", patID);
				map.put("SeriesDate", rset.getString("SeriesDate"));
				map.put("SeriesDescription", rset.getString("SeriesDescription"));
				map.put("Modality", rset.getString("Modality"));
				map.put("InstitutionName", rset.getString("InstitutionName"));
				map.put("ModelName", rset.getString("ModelName"));
				map.put("SeriesNumber", rset.getString("SeriesNumber"));
				map.put("NumOfInstanceInSeries", String.valueOf(getNumOfInstanceInSeries(rset.getString("PatientID"), rset.getString("StudyInstanceUID"), rset.getString("SeriesInstanceUID"))));
				map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
				map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
			}
			if(map.isEmpty()) {
				return null;
			}else {
				return map;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				conn.commit();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public List<HashMap<String, String>> getImagesInfoByUIDs(String patID,String studyUID, String seriesUID) {
		if (getNumOfInstanceInSeries(patID, studyUID, seriesUID) < 1 ) {
			return null;
		}
		Connection conn = openConnection();
		List<HashMap<String, String>> imageInfoList = new ArrayList<HashMap<String,String>>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement("SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber");
			pstmt.setString(1, patID);//start from 1
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			rset = pstmt.executeQuery();
//			rset.setFetchSize(9999); //need ?
			while(rset.next()) {
				HashMap<String, String> map = new HashMap<>();
				map.put("PatientID", patID);
				map.put("AcquisitionDateTime", rset.getString("AcquisitionDateTime"));
				map.put("AcquisitionNumber", rset.getString("AcquisitionNumber"));
				map.put("InstanceNumber", rset.getString("InstanceNumber"));
				//?NumOfInstanceInSeries? -> should check...
				map.put("NumOfInstanceInSeries", String.valueOf(getNumOfInstanceInSeries(rset.getString("PatientID"), rset.getString("StudyInstanceUID"), rset.getString("SeriesInstanceUID"))));
				map.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
				map.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
				map.put("SOPInstanceUID", rset.getString("SOPInstanceUID"));
				imageInfoList.add(map);
			}
			if(imageInfoList.isEmpty()) {
				return null;
			}else {
				return imageInfoList;
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public String getStudyIUID(String patID,String seriesIUID,String sopIUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		String studyUID = null;
		try {
			String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, seriesIUID);
			pstmt.setString(3, sopIUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);
			if(rset.next()) {
				studyUID = rset.getString("StudyInstanceUID");
			}
			return studyUID;
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public ArrayList<HashMap<String,Object>> loadRoiContextFromInstance(String pid, String studyUid, String seriesUid, String sopUid) {
		Connection conn = openConnection();
		ArrayList<HashMap<String,Object>> set = new ArrayList<HashMap<String,Object>>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			String statement = "SELECT * FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, pid);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			pstmt.setString(4, sopUid);
			rset = pstmt.executeQuery();
			while(rset.next()) {
				HashMap<String, Object> roiCon = new HashMap<>();
				roiCon.put("RoiID", rset.getString("RoiID"));
				roiCon.put("Name", rset.getString("Name"));
				roiCon.put("RoiType", rset.getInt("RoiType"));//int
				roiCon.put("OriginX", rset.getInt("OriginX"));
				roiCon.put("OriginY", rset.getInt("OriginY"));
				roiCon.put("Width", rset.getInt("Width"));
				roiCon.put("Height", rset.getInt("Height"));
				roiCon.put("PointX", doubleArr2floatArr(blob2DoubleArray(rset.getBlob("PointX"))));
				roiCon.put("PointY", doubleArr2floatArr(blob2DoubleArray(rset.getBlob("PointY"))));
				roiCon.put("Shape", doubleArr2floatArr(blob2DoubleArray(rset.getBlob("Shape"))));
				roiCon.put("InstanceNo", rset.getInt("InstanceNo"));//int
				roiCon.put("RoiGroup", rset.getInt("RoiGroup"));//int
				roiCon.put("RoiLabel", rset.getString("RoiLabel"));
				roiCon.put("ObjectType", rset.getString("ObjectType"));
				roiCon.put("Organ", rset.getString("Organ"));
				roiCon.put("Description", rset.getString("Description"));
				roiCon.put("PatientID", rset.getString("PatientID"));
				roiCon.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
				roiCon.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
				roiCon.put("SOPInstanceUID", rset.getString("SOPInstanceUID"));
				set.add(roiCon);
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(set.size() > 0) {
			return set;
		}else {
			return null;
		}
	}
	
	private double[] blob2DoubleArray(Blob b) {
		if(b == null) {
			return null;
		}
		try {
			int blobLength = (int) b.length();
			byte[] blobAsBytes = b.getBytes(1, blobLength);
			ByteBuffer bb2Back = ByteBuffer.wrap(blobAsBytes);
			double[] res = new double[blobAsBytes.length / 8];
			for(int i = 0; i < res.length; i++) {
			    res[i] = bb2Back.getDouble();
			}
			b.free();
			return res;
		} catch (SQLException e) {
			b = null;
			return null;
		}
	}
	
	private float[] doubleArr2floatArr(double[] da) {
		if(da == null) {
			return null;
		}
		float[] fa = new float[da.length];
		for(int i=0;i<da.length;i++) {
			fa[i] = (float)da[i];
		}
		return fa;
	}
	
	public HashMap<String, Object> loadRoiContext(String roiId, String pid, String studyUid, String seriesUid, String sopUid) {
		Connection conn = openConnection();
		HashMap<String, Object> roiCon = new HashMap<>();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			String statement = "SELECT * FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=? AND RoiID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, pid);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			pstmt.setString(4, sopUid);
			pstmt.setString(5, roiId);
			rset = pstmt.executeQuery();
			if(rset.next()) {
				roiCon.put("RoiID", rset.getString("RoiID"));
				roiCon.put("RoiType", rset.getInt("RoiType"));
				roiCon.put("OriginX", rset.getInt("OriginX"));
				roiCon.put("OriginY", rset.getInt("OriginY"));
				roiCon.put("Width", rset.getInt("Width"));
				roiCon.put("Height", rset.getInt("Height"));
				roiCon.put("PointX", rset.getBlob("PointX"));
				roiCon.put("PointY", rset.getBlob("PointY"));
				roiCon.put("InstanceNo", rset.getInt("InstanceNo"));
				roiCon.put("RoiGroup", rset.getInt("RoiGroup"));
				roiCon.put("RoiLabel", rset.getString("RoiLabel"));
				roiCon.put("ObjectType", rset.getString("ObjectType"));
				roiCon.put("Organ", rset.getString("Organ"));
				roiCon.put("Description", rset.getString("Description"));
				roiCon.put("PatientID", rset.getString("PatientID"));
				roiCon.put("StudyInstanceUID", rset.getString("StudyInstanceUID"));
				roiCon.put("SeriesInstanceUID", rset.getString("SeriesInstanceUID"));
				roiCon.put("SOPInstanceUID", rset.getString("SOPInstanceUID"));
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(roiCon.size() > 0) {
			return roiCon;
		}else {
			return null;
		}
	}
	
	public String getSeriesIUID(String patID,String studyIUID,String sopIUID) {
		Connection conn = openConnection();
		ResultSet rset = null;
		PreparedStatement pstmt = null;
		try {
			String statement = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SOPInstanceUID=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyIUID);
			pstmt.setString(3, sopIUID);
			rset = pstmt.executeQuery();
			rset.setFetchSize(3);
			if(rset.next()) {
				return rset.getString("SeriesInstanceUID");
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rset.close();
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}

	// Updations
	public void update(String tableName, String fieldName, int fieldValue, String whereField, String whereValue) {
		Connection conn = openConnection();
		try {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "=" + fieldValue
					+ " where " + whereField + "='" + whereValue + "'");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void update(String tableName, String fieldName, boolean fieldValue, String whereField, String whereValue) {
		Connection conn = openConnection();
		try {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "=" + fieldValue
					+ " where " + whereField + "='" + whereValue + "'");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void update(String tableName, String fieldName, String fieldValue, String whereField, String whereValue) {
		Connection conn = openConnection();
		try {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "='" + fieldValue
					+ "' where " + whereField + "='" + whereValue + "'");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}
	
	public void update(String tableName, String fieldName, int fieldValue, int whereField, String whereValue) {
		Connection conn = openConnection();
		try {
			conn.createStatement().executeUpdate("update " + tableName + " set " + fieldName + "='" + fieldValue
					+ "' where " + whereField + "='" + whereValue + "'");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}
	
	public void update(String tableName, String fieldName, java.sql.Date fieldDateValue, String whereField, String whereValue) {
		Connection conn = openConnection();
		PreparedStatement pstmt = null;
		try {
			String statement = 
					"UPDATE " + tableName +
					" SET "+fieldName+"=?"+
					" WHERE "+whereField +"=?";
			pstmt = conn.prepareStatement(statement);
			pstmt.setDate(1, fieldDateValue);
			pstmt.setString(2, whereValue);
			pstmt.executeUpdate();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void addInstanceCount(String studyUid, String seriesUid) {
		Connection conn = openConnection();
		ResultSet studyLevelInstances = null;
		ResultSet seriesLevelInstances = null;
		try {
			studyLevelInstances = conn.createStatement()
					.executeQuery("select NoOfInstances from study where StudyInstanceUID='" + studyUid + "'");
			studyLevelInstances.next();
			seriesLevelInstances = conn.createStatement()
					.executeQuery("select NoOfSeriesRelatedInstances from series where StudyInstanceUID='" + studyUid
							+ "' and SeriesInstanceUID='" + seriesUid + "'");
			seriesLevelInstances.next();
			conn.createStatement().executeUpdate("update study set NoOfInstances=" + (studyLevelInstances.getInt(1) + 1)
					+ "where StudyInstanceUID='" + studyUid + "'");
			conn.createStatement()
					.executeUpdate("update series set NoOfSeriesRelatedInstances="
							+ (seriesLevelInstances.getInt("NoOfSeriesRelatedInstances") + 1)
							+ "where StudyInstanceUID='" + studyUid + "' and SeriesInstanceUID='" + seriesUid + "'");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		} finally {
			try {
				seriesLevelInstances.close();
				studyLevelInstances.close();
				safeClose(conn);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public int getCommunicationServerPk(String nickname) {
		Connection conn = openConnection();		
		ResultSet svrInfo = null;
		int res = -1;
		try {
			svrInfo = conn.createStatement()
					.executeQuery("select * from servers where logicalname='" + nickname + "'");
			if(svrInfo.next()) {
				res = svrInfo.getInt("pk");
			}
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			return res;
		} finally {
			try {
				svrInfo.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return res;
	}

	public void updateThumbnailStatus(String studyUid, String seriesUid, String sopUid) {
		Connection conn = openConnection();
		try {
			conn.createStatement().executeUpdate("update image set ThumbnailStatus=true where StudyInstanceUID='"
					+ studyUid + "' and SeriesInstanceUID='" + seriesUid + "' and SOPInstanceUID='" + sopUid + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void updateListener(String aetitle, String port) {
		Connection conn = openConnection();
		ResultSet rs = null;
		try {
			rs = conn.createStatement().executeQuery("select pk from listener");
			rs.next();
			conn.createStatement().executeUpdate(
					"update listener set aetitle='" + aetitle + "',port='" + port + "', where pk=" + rs.getInt("pk"));
			
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				rs.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally {
				safeClose(conn);
			}
		}
	}
	
	public void updateListener(String aetitle, String host,String port, String storagelocation) {
		Connection conn = openConnection();
		ResultSet rs = null;
		try {
			rs = conn.createStatement().executeQuery("select * from listener");
			rs.next();
			conn.createStatement().executeUpdate(
					"update listener set aetitle='" + aetitle + "',host='" + host +  "',port='" + port + "',storagelocation='"+storagelocation+ " where pk=" + rs.getInt("pk"));
			rs.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void updateTheme(String themeName) {
		Connection conn = openConnection();
		ResultSet activeInfo = null;
		try {
			activeInfo = conn.createStatement().executeQuery("select name from theme where status=true");
			activeInfo.next();
			conn.createStatement()
					.executeUpdate("update theme set status=false where name='" + activeInfo.getString("name") + "'");
			conn.createStatement().executeUpdate("update theme set status=true where name='" + themeName + "'");
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				activeInfo.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void reArrangeButtons(String buttonToMove, String buttonToReplace) {
		Connection conn = openConnection();
		String selectQuery = "select buttonno from buttons where description='";
		try {
			ResultSet buttonToMoveInfo = conn.createStatement().executeQuery(selectQuery + buttonToMove + "'");
			buttonToMoveInfo.next();
			ResultSet buttonToReplaceInfo = conn.createStatement().executeQuery(selectQuery + buttonToReplace + "'");
			buttonToReplaceInfo.next();
			conn.createStatement().executeUpdate("update buttons set buttonno=" + buttonToMoveInfo.getInt("buttonno")
					+ " where description='" + buttonToReplace + "'");
			conn.createStatement().executeUpdate("update buttons set buttonno=" + buttonToReplaceInfo.getInt("buttonno")
					+ " where description='" + buttonToMove + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	/*
	 * 更新する。このメソッドに合わせて。
	 */
	public boolean updateServer(HashMap<String, Object> newServerModelMaterial, String prevNickName) {
		Connection conn = openConnection();
		boolean duplicate = false;
		try {
			conn.createStatement().executeUpdate(
					"update servers set logicalname='" + newServerModelMaterial.get("nickname")
					+ "',aetitle='" + newServerModelMaterial.get("aet") 
					+ "',hostname='" + newServerModelMaterial.get("hostname")
					+ "',port="+ newServerModelMaterial.get("port")
					+ ",retrievetype='" + newServerModelMaterial.get("retrievetype")
					+ "',wadocontext='" + newServerModelMaterial.get("wadocontext")
					+ "',wadoport="+ newServerModelMaterial.get("wadoport")
					+ ",wadoprotocol='" + newServerModelMaterial.get("wadoprotocol")
					+ "',retrievets='"+ newServerModelMaterial.get("retrievets")
					+ "' where pk=" + getCommunicationServerPk(prevNickName));
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
			duplicate = true;//fail safe
		}finally {
			safeClose(conn);
		}
		return duplicate;
	}

//	public void updatePreset(PresetModel presetModel) {
//		openConnection();
//		try {
//			conn.createStatement()
//					.execute("update presets set presetname='" + presetModel.getPresetName() + "',windowwidth="
//							+ presetModel.getWindowWidth() + ",windowlevel=" + presetModel.getWindowLevel()
//							+ " where pk=" + presetModel.getPk());
//			conn.commit();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//	}

	public void updateModalitiesStatus(String modality, boolean status) {
		Connection conn = openConnection();
		try {
			conn.createStatement()
					.execute("update modality set status=" + status + " where shortname='" + modality + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void setAllModalitiesIdle() {
		Connection conn = openConnection();
		try {
			conn.createStatement().execute("update modality set status=false");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void updateDefaultLocale(String localeName) {
		Connection conn = openConnection();
		try {
			conn.createStatement().execute("update locale set status=false");
			conn.createStatement().execute("update locale set status=true where localeid='" + localeName + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

//	public void updateLoopBack(boolean isLoopback) {
//		openConnection();
//		try {
//			conn.createStatement().executeUpdate("update miscellaneous set Loopback=" + isLoopback);
//			conn.commit();
//		} catch (SQLException ex) {
//			logger.severe("DatabaseHandler", ex);
//		}finally {
//			safeClose(conn);
//		}
//	}

	public void updateJNLPRetrieveType(String retrieveType) {
		Connection conn = openConnection();
		try {
			conn.createStatement().executeUpdate("update miscellaneous set JNLPRetrieveType='" + retrieveType + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void updateDynamicRetrieveTypeStatus(boolean allow) {
		Connection conn = openConnection();
		try {
			conn.createStatement().executeUpdate("update miscellaneous set AllowDynamicRetrieveType=" + allow);
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	public void updateStudy(String studyUid) {
		Connection conn = openConnection();
		try {
			conn.createStatement()
					.execute("update study set DownloadStatus=true,NoOfInstances=" + getNumOfInstancesInStudy(studyUid)
							+ ",NoOfSeries=" + getNumOfInstancesInStudy(studyUid) + " where StudyInstanceUID='" + studyUid
							+ "'");
			conn.createStatement()
					.execute("update image set ThumbnailStatus=true where StudyInstanceUID='" + studyUid + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	// Deletions
	public void deleteButton(String description) {
		Connection conn = openConnection();
		try {
			conn.createStatement().execute("delete from buttons where description='" + description + "'");
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

//	public void deletePreset(PresetModel presetModel) {
//		openConnection();
//		try {
//			conn.createStatement().execute("delete from presets where pk=" + presetModel.getPk());
//			conn.commit();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}finally {
//			safeClose(conn);
//		}
//	}

	public void deleteServer(String nickname) {
		Connection conn = openConnection();
		String statement = "delete from servers where pk=?";
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setInt(1, getCommunicationServerPk(nickname));
			pstmt.execute();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			try {
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/*
	 * where use it ?
	 */
//	public void rebuild() {
//		deleteRows();
//		mediator.deleteDir(new File(mediator.listenerDetails[2]));
//	}

	/**
	 * delete all tables for rebuild.
	 */
	@SuppressWarnings("unused")
	private void deleteAllRecord() {
		Connection conn = openConnection();
		try {
			Statement statement = conn.createStatement();
			statement.execute("delete from image");
			statement.execute("delete from series");
			statement.execute("delete from study");
			statement.execute("delete from patient");
			statement.execute("delete from roi");
			/*
			 * add more...
			 */
			statement.close();
			conn.commit();
		} catch (SQLException ex) {
			logger.severe(ex.getMessage());
		}finally {
			safeClose(conn);
		}
	}

	//old
//	public void deleteLinkStudies() {
//		try {
//			ResultSet linkStudies = conn.createStatement()
//					.executeQuery("select StudyInstanceUID from study where StudyType='link'");
//			while (linkStudies.next()) {
//				ResultSet linkSeries = conn.createStatement()
//						.executeQuery("select SeriesInstanceUID from series where StudyInstanceUID='"
//								+ linkStudies.getString("StudyInstanceUID") + "'");
//				while (linkSeries.next()) {
//					ResultSet linkInstances = conn.createStatement()
//							.executeQuery("select SOPInstanceUID from image where StudyInstanceUID='"
//									+ linkStudies.getString("StudyInstanceUID") + "' and SeriesInstanceUID='"
//									+ linkSeries.getString("SeriesInstanceUID") + "'");
//					while (linkInstances.next()) {
//						deleteRow("image", "SOPInstanceUID", linkInstances.getString("SOPInstanceUID"));
//					}
//					deleteRow("series", "SeriesInstanceUID", linkSeries.getString("SeriesInstanceUID"));
//				}
//				deleteRow("study", "StudyInstanceUID", linkStudies.getString("StudyInstanceUID"));
//			}
//			conn.commit();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}
//	}

	public void deleteRow(String tableName, String whereFiled, String whereValue) throws SQLException {
		Connection conn = openConnection();
		conn.createStatement().execute("delete from " + tableName + " where " + whereFiled + "='" + whereValue + "'");
//		conn.commit();
		safeClose(conn);
	}
	
	
	/*
	 * delete record and file
	 */
	public void deleteInstance(String patID, String studyUID, String seriesUID, String sopUID) throws SQLException {
		
		Connection conn = openConnection();
		/*
		 * if link, just only delete record ,
		 * else if saving file, and if last one file, delete parent directory folder after delete image file.
		 */
		boolean saveAsLink = isInstanceSavedAsLink(patID, studyUID, seriesUID, sopUID);
		String storeURI = getFileLocation(patID,studyUID, seriesUID, sopUID);
		
		String statement = "DELETE FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=?";
		
		PreparedStatement pstmt = conn.prepareStatement(statement);
		pstmt.setString(1, patID);
		pstmt.setString(2, studyUID);
		pstmt.setString(3, seriesUID);
		pstmt.setString(4, sopUID);
		if(saveAsLink) {
			if (pstmt.executeUpdate()==1) {
//				conn.commit();//fail safe
				// check whether dicom tree delete
				if (getNumOfInstanceInSeries(patID, studyUID, seriesUID) == 0) {
					if(deleteSeriesRecord(patID, studyUID, seriesUID)) {
						if(getNumOfSeries(patID, studyUID)==0) {
							if(deleteStudyRecord(patID, studyUID)) {
								if(getNumOfStudyParticularPatient(patID)==0) {
									deletePatientRecord(patID);
								}
							}
						}
					}
				}
			}else {
				//nothing in db
				pstmt.close();
				safeClose(conn);
				return;
			}
		}else {			
			if (pstmt.executeUpdate()==1) {
//				conn.commit();
				//delete file
				File instance = new File(storeURI);
				File parent = instance.getParentFile();
				instance.delete();
				// check whether dicom tree delete
				if (getNumOfInstanceInSeries(patID, studyUID, seriesUID) == 0) {
					File seriesDir = new File(parent.getAbsolutePath());
					parent = seriesDir.getParentFile();
					seriesDir.delete();
					if(deleteSeriesRecord(patID, studyUID, seriesUID)) {
						if(getNumOfSeries(patID, studyUID)==0) {
							File studyDir = new File(parent.getAbsolutePath());
							parent = studyDir.getParentFile();
							studyDir.delete();
							if(deleteStudyRecord(patID, studyUID)) {
								if(getNumOfStudyParticularPatient(patID)==0) {
									parent.delete();
									deletePatientRecord(patID);
								}
							}
						}
					}
				}
			}else {
				pstmt.close();
				safeClose(conn);
				return;
			}
		}
		if(!pstmt.isClosed()) {
			pstmt.close();
		}
		safeClose(conn);
	}
	
	public void deletePatientRecord(String patID) {
		Connection conn = openConnection();
		if(getNumOfStudyParticularPatient(patID)>0) {
			logger.info("DB:deletePatientRecord::This Patient has study record, can not delete record.return");
			return;
		}
		String statement = "DELETE FROM PATIENT WHERE PatientID=?";
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.executeUpdate();
			pstmt.close();
			conn.commit();
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}finally {
			safeClose(conn);
		}
	}
	
	public boolean deleteStudyRecord(String patID, String studyUID){
		Connection conn = openConnection();
		boolean success = false;
		if(getNumOfSeries(patID, studyUID)>0) {
			logger.severe("DB:deleteStudyRecord::This Study has series record, can not delete record.return");
			return false;
		}
		String statement = "DELETE FROM STUDY WHERE PatientID=? AND StudyInstanceUID=?";
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.executeUpdate();
			pstmt.close();
			conn.commit();
			success = true;
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}finally {
			safeClose(conn);
		}
		return success;
	}
	
	public boolean deleteSeriesRecord(String patID, String studyUID, String seriesUID){
		Connection conn = openConnection();
		boolean success = false;
		if(getNumOfInstanceInSeries(patID, studyUID, seriesUID)>0) {
			logger.severe("DB:deleteSeriesRecord::This Series has instance record, can not delete record.return");
			return false;
		}
		String statement = "DELETE FROM SERIES WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=?";
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUID);
			pstmt.setString(3, seriesUID);
			pstmt.executeUpdate();
			pstmt.close();
			conn.commit();
			success = true;
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}finally {
			safeClose(conn);
		}
		return success;
	}
	
	public void forceDeleteLocalStudy(String patientID, String studyInstanceUID) {
		/*
		 * Idea 0603
		 */
		//get all instances by using String patientID, String studyInstanceUID from IMAGE record.
		//correct instance info :patID studyuid seriesuid instuid storeurl isLink
		//delete instace record
			//then validate series-study-patient record whether delete of each.
		
		/*
		 * old deprecate
		 */
//		try {
//			ResultSet fileInfo = conn.createStatement()
//					.executeQuery("SELECT FileStoreUrl FROM IMAGE WHERE StudyInstanceUID='" + studyInstanceUID + "'");
//			if (fileInfo.next() && fileInfo.getString("FileStoreUrl").contains(ApplicationContext.DBDirectory)
//					&& !fileInfo.getString("FileStoreUrl").contains("tmp")) {
//				mediator.deleteDir(new File(fileInfo.getString("FileStoreUrl")).getParentFile().getParentFile());
//			}
//			fileInfo.close();
//			conn.createStatement().execute("delete from image where StudyInstanceUID='" + studyInstanceUID + "'");
//			conn.createStatement().execute("delete from series where StudyInstanceUID='" + studyInstanceUID + "'");
//			conn.createStatement().execute("delete from study where StudyInstanceUID='" + studyInstanceUID + "'");
//
//			if (!checkRecordExists("study", "PatientID", patientID)) {
//				conn.createStatement().execute("delete from patient where PatientID='" + patientID + "'");
//			}
//			conn.commit();
//		} catch (SQLException ex) {
//			ApplicationContext.logger.severe(ex.getMessage());
//		}
	}
	
	public void deleteMissingLinkedFiles() {
		Connection conn = openConnection();
		String statement = "SELECT * FROM IMAGE WHERE isLink=?";
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setBoolean(1, true);
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				String url = rs.getString("FileStoreUrl");
				String patID = rs.getString("PatientID");
				String studyUID = rs.getString("StudyInstanceUID");
				String seriesUID = rs.getString("SeriesInstanceUID");
				String sopUID = rs.getString("SOPInstanceUID");
				if(!new File(url).exists()) {
					deleteInstance(patID, studyUID, seriesUID, sopUID);
				}
			}
			rs.close();
			pstmt.close();
			conn.commit();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			safeClose(conn);
		}
		/*
		 * TODO
		 */
//		ApplicationContext.getInstance().getMainScreen().loadLocalStudiesBySearchKey();
	}
	
	
	
	public synchronized void deleteRoi(String patID,String studyUid,String seriesUid, String sopUid,String roiId) {
		Connection conn = openConnection();
		String statement = "DELETE FROM ROI WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? AND SOPInstanceUID=? AND RoiID=?";
		PreparedStatement pstmt = null;
		try {
			pstmt = conn.prepareStatement(statement);
			pstmt.setString(1, patID);
			pstmt.setString(2, studyUid);
			pstmt.setString(3, seriesUid);
			pstmt.setString(4, sopUid);
			pstmt.setString(5, roiId);
			pstmt.executeUpdate();//if(pstmt.executeUpdate()==1) {}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			try {
				pstmt.close();
				safeClose(conn);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/*
	 * nodeにuserObjectとして各マテリアルをもたせる。
	 */
	public ArrayList<DefaultMutableTreeNode> selectStudiesWithSearchKeys(String patID, String from, String to, ArrayList<String> modalities){
		
		Connection conn = openConnection();
		
		ArrayList<String> keys = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		
		if(patID == null && from ==null && to==null) {
			sb.append("SELECT * FROM STUDY");
		}else {
			sb.append("SELECT * FROM STUDY WHERE");
		}
		int basicStateLen = sb.length();
		
		if(patID != null) {
			sb.append(" PatientID=?");
			keys.add(patID);
		}

		if(from != null && to != null) {
			if(sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" BETWEEN");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
				sb.append(" AND");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}else {
				sb.append(" StudyDate");
				sb.append(" BETWEEN");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
				sb.append(" AND");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}
		}else if(from != null && to == null) {
			if(sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" >=");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
			}else {
				sb.append(" StudyDate");
				sb.append(" >=");
				sb.append(" ?");
				keys.add(from.replace("/", "-"));
			}
		}else if(from == null && to != null) {
			if(sb.length() > basicStateLen) {
				sb.append(" AND");
				sb.append(" StudyDate");
				sb.append(" <");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}else {
				sb.append(" StudyDate");
				sb.append(" <");
				sb.append(" ?");
				keys.add(to.replace("/", "-"));
			}
		}
		//sample
		//select * from table where pid=? and pname=? and studydate between ? and ?
		//select * from table where pid=? and pname=? and studydate >= ?
		//select * from table where pid=? and pname=? and studydate < ?(e.g,'2006-11-30')
		ArrayList<DefaultMutableTreeNode> studiesList = new ArrayList<DefaultMutableTreeNode>();
		String statement = sb.toString();
		ResultSet studyInfo = null;
		ResultSet patientInfo = null;
		ResultSet seriesInfo = null;
		ResultSet imageInfo = null;
		try {
			PreparedStatement pstmtStudy = conn.prepareStatement(statement);
			for(int i=0;i<keys.size();i++) {
				pstmtStudy.setString((i+1), keys.get(i));//the first parameter is 1
			}
			studyInfo = pstmtStudy.executeQuery();
			while(studyInfo.next()) {
				String patIDInRecord = studyInfo.getString("PatientID");
				String studyUID = studyInfo.getString("StudyInstanceUID");
				patientInfo = conn.createStatement()
							.executeQuery("select * from patient where PatientID='"
									+ patIDInRecord + "'");
				/*
				 * here,
				 * if patID is null,
				 * we need get patient info from studyinfo.
				 */
				if(patientInfo.next()) {
					HashMap<String, Object> studyMaterial = loadStudyNodeMaterial(patientInfo, studyInfo);
					DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(studyMaterial,true);
					String stmSeries = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?";
					if(modalities != null && modalities.size()>0) {
						for(int i=0; i<modalities.size();i++) {
							String stmSeries_ = stmSeries + " AND " + "Modality=?";
							stmSeries_ = stmSeries_ + " order by SeriesNumber";
							PreparedStatement pstmtSeries = conn.prepareStatement(stmSeries_);
							pstmtSeries.setString(1, patIDInRecord);
							pstmtSeries.setString(2, studyUID);
							pstmtSeries.setString(3, modalities.get(i));
							seriesInfo = pstmtSeries.executeQuery();
							while (seriesInfo.next()) {
								HashMap<String,Object> seriesMaterial = loadSeriesNodeMaterial(seriesInfo, studyMaterial);
								DefaultMutableTreeNode series = new DefaultMutableTreeNode(seriesMaterial,true);
								String stmImage = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber";
								PreparedStatement pstmtImage = conn.prepareStatement(stmImage);
								pstmtImage.setString(1, patIDInRecord);
								pstmtImage.setString(2, seriesInfo.getString("StudyInstanceUID"));
								pstmtImage.setString(3, seriesInfo.getString("SeriesInstanceUID"));
								imageInfo = pstmtImage.executeQuery();
								while (imageInfo.next()) {
									HashMap<String,Object> imageMaterial = loadImageNodeMaterials(imageInfo);
									DefaultMutableTreeNode image = new DefaultMutableTreeNode(imageMaterial,false);
									series.add(image);
								}
								if(series.getChildCount() > 0) {
									studyNode.add(series);
								}
								pstmtImage.close();
							}
							if(studyNode.getChildCount() > 0) {
								if(!studiesList.contains(studyNode)) {
									studiesList.add(studyNode);
								}
							}
							pstmtSeries.close();
						}
					}else {
						stmSeries = stmSeries + " order by SeriesNumber";
						PreparedStatement pstmtSeries = conn.prepareStatement(stmSeries);
						pstmtSeries.setString(1, patIDInRecord);
						pstmtSeries.setString(2, studyUID);
						seriesInfo = pstmtSeries.executeQuery();
						while (seriesInfo.next()) {
							HashMap<String,Object> seriesMaterial = loadSeriesNodeMaterial(seriesInfo, studyMaterial);
							DefaultMutableTreeNode series = new DefaultMutableTreeNode(seriesMaterial,true);
							String stmImage = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber";
							PreparedStatement pstmtImage = conn.prepareStatement(stmImage);
							pstmtImage.setString(1, patIDInRecord);
							pstmtImage.setString(2, seriesInfo.getString("StudyInstanceUID"));
							pstmtImage.setString(3, seriesInfo.getString("SeriesInstanceUID"));
							imageInfo = pstmtImage.executeQuery();
							while (imageInfo.next()) {
								HashMap<String,Object> imageMaterial = loadImageNodeMaterials(imageInfo);
								DefaultMutableTreeNode image = new DefaultMutableTreeNode(imageMaterial,false);
								series.add(image);
							}
							if(series.getChildCount() > 0) {
								studyNode.add(series);
							}
							pstmtImage.close();
						}
						if(studyNode.getChildCount() > 0) {
							studiesList.add(studyNode);
						}
						pstmtSeries.close();
					}
				}
			}
			pstmtStudy.close();
			if(imageInfo != null) {
				imageInfo.close();
			}
			if(seriesInfo != null) {
				seriesInfo.close();
			}
			if(studyInfo != null) {
				studyInfo.close();
			}
			if(patientInfo != null) {
				patientInfo.close();
			}
			conn.commit();//fail safe
		} catch (SQLException e) {
			e.printStackTrace();
		}finally {
			safeClose(conn);
		}
		return studiesList;//DO NOT return NULL.
	}
	
	public ArrayList<DefaultMutableTreeNode> selectStudiesWithSearchKeysUsingPatName(String patID, String patName, String from,
			String to, ArrayList<String> modalities) {
		
		//debug
//		logger.info("Search performed...");
//		System.out.println(patID);
//		System.out.println(patName);
//		System.out.println(from);
//		System.out.println(to);
//		System.out.println(modalities);
		
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
		// sample
		// select * from table where pid=? and pname=? and studydate between ? and ?
		// select * from table where pid=? and pname=? and studydate >= ?
		// select * from table where pid=? and pname=? and studydate <
		// ?(e.g,'2006-11-30')
		ArrayList<DefaultMutableTreeNode> studiesList = new ArrayList<DefaultMutableTreeNode>();
		Connection conn = openConnection();
		String statement = sb.toString();
		ResultSet patientInfo = null;
		ResultSet studyInfo = null;
		ResultSet seriesInfo = null;
		ResultSet imageInfo = null;
		try {
			String patQueryStatement = null;
			PreparedStatement psPat = null;
			if (patID == null && patName == null) {
				return selectStudiesWithSearchKeys(patID, from, to, modalities);
			} else if (patID != null && patName == null) {
				return selectStudiesWithSearchKeys(patID, from, to, modalities);
			} else if (patID == null && patName != null) {
				patQueryStatement = "select * from patient where PatientName LIKE ?";
				psPat = conn.prepareStatement(patQueryStatement);
				psPat.setString(1, patName + "%");
			} else if (patID != null && patName != null) {
				patQueryStatement = "select * from patient where PatientID=? and PatientName LIKE ?";
				psPat = conn.prepareStatement(patQueryStatement);
				psPat.setString(1, patID);
				psPat.setString(2, patName + "%");
			}
			patientInfo = psPat.executeQuery();
			while (patientInfo.next()) {
				/* all study related keys use here. */
				PreparedStatement pstmtStudy = conn.prepareStatement(statement);// here
				for (int i = 0; i < keys.size(); i++) {
					pstmtStudy.setString((i + 1), keys.get(i));
				}
				String patIdInRecord = patientInfo.getString("PatientID");
				studyInfo = pstmtStudy.executeQuery();
				while (studyInfo.next()) {
					String studyUID = studyInfo.getString("StudyInstanceUID");
					HashMap<String,Object> studyMaterial = loadStudyNodeMaterial(patientInfo, studyInfo);
					DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(studyMaterial,true);
					String stmSeries = "SELECT * FROM SERIES WHERE PatientID=? AND StudyInstanceUID=?";
					if (modalities != null && modalities.size() > 0) {
						for (int i = 0; i < modalities.size(); i++) {
							if (i == 0) {
								stmSeries = stmSeries + " AND " + "Modality=?";
							} else {
								stmSeries = stmSeries + " OR " + "Modality=?";
							}
						}
					}
					stmSeries = stmSeries + " order by SeriesNumber";
					PreparedStatement pstmtSeries = conn.prepareStatement(stmSeries);
					pstmtSeries.setString(1, patIdInRecord);//DO NOT USE patID that already inputed.
					pstmtSeries.setString(2, studyUID);
					if (modalities != null && modalities.size() > 0) {
						for (int i = 0; i < modalities.size(); i++) {
							pstmtSeries.setString(i + 3, modalities.get(i));
						}
					}
					seriesInfo = pstmtSeries.executeQuery();
					while (seriesInfo.next()) {
						HashMap<String,Object> seriesMaterial = loadSeriesNodeMaterial(seriesInfo, studyMaterial);
						DefaultMutableTreeNode series = new DefaultMutableTreeNode(seriesMaterial, true);
						String stmImage = "SELECT * FROM IMAGE WHERE PatientID=? AND StudyInstanceUID=? AND SeriesInstanceUID=? order by InstanceNumber";
						PreparedStatement pstmtImage = conn.prepareStatement(stmImage);
						pstmtImage.setString(1, patIdInRecord);
						pstmtImage.setString(2, seriesInfo.getString("StudyInstanceUID"));
						pstmtImage.setString(3, seriesInfo.getString("SeriesInstanceUID"));
						imageInfo = pstmtImage.executeQuery();
						while (imageInfo.next()) {
							HashMap<String, Object> imageMaterial = loadImageNodeMaterials(imageInfo);
							DefaultMutableTreeNode image = new DefaultMutableTreeNode(imageMaterial,false);
							series.add(image);
						}
						if(series.getChildCount() > 0) {
							studyNode.add(series);
						}
						pstmtImage.close();
					}
					if(studyNode.getChildCount() > 0) {
						studiesList.add(studyNode);
					}
					pstmtSeries.close();
				}
				pstmtStudy.close();
			}
			if (imageInfo != null) {
				imageInfo.close();
			}
			if (seriesInfo != null) {
				seriesInfo.close();
			}
			if (studyInfo != null) {
				studyInfo.close();
			}
			if (patientInfo != null) {
				patientInfo.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			safeClose(conn);
		}
		return studiesList;
	}
	
	/*
	 * 別のクラスへ移動！->QR class
	 */
//	public void queryAndUpadateTreeTableByTextSearch(String patID, String patName, String from, String to, ArrayList<String> modalities){
//		String anchorTreeTableTitle = ApplicationContext.dcmTTManager.getCurrentAnchorTitle();
//		if(anchorTreeTableTitle.equals("HOME")) {
//			System.out.println("run search on HOME");
//			ArrayList<DICOMNode> selectedStudies = selectStudiesWithSearchKeysUsingPatName(patID, patName, from, to, modalities);
//			ApplicationContext.getInstance().getMainScreen().constructTreeTable(new DICOMNode(true, selectedStudies));
//		}else {
//			System.out.println("run search on QR");
//			
//			TabDock anchorDock = ApplicationContext.dcmTTManager.getParticularDockFromMap(anchorTreeTableTitle);
//			String nickname = anchorTreeTableTitle;
//			/* root */
//			DICOMNode queryResults = new QueryRetrieve().querySimpleSearchKeys(nickname, patID, patName, from, to, modalities);
//			anchorDock.updateTreeTable(queryResults);
//		}
//	}
}
