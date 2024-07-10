package com.vis.core.util;

import java.time.LocalDate;
import java.time.Period;
import java.util.Calendar;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.UIDUtils;

public class DBUtils {
	
	public static boolean isAlreadyRegisteredServer(DatabaseHandler db, String identicalNickname) {
		if(identicalNickname == null) {
			return false;
		}
		int pk = db.getCommunicationServerPk(identicalNickname);
		if(pk == -1) {
			return false;
		}else {
			return true;
		}
	}
	
	public static String getCurrentDBLocation() {
		return PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.LocalDBLocation);
	}
	
	/**
	 * see {@link com.vis.core.Utils#calculateAge(java.util.Date)}
	 * @deprecated
	 * @param birthOfDate
	 * @return
	 */
	public static String calculateAge(java.util.Date birthOfDate) {
		if(birthOfDate == null) {
			return "";
		}
//		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
		Calendar c = Calendar.getInstance();
		c.setTime(birthOfDate);
		int year = c.get(Calendar.YEAR);
		int month = c.get(Calendar.MONTH) + 1;
		int date = c.get(Calendar.DATE);
		LocalDate l1 = LocalDate.of(year, month, date);
		LocalDate now1 = LocalDate.now();
		Period diff1 = Period.between(l1, now1);
//		System.out.println("age:" + diff1.getYears() + "years");
		return String.valueOf(diff1.getYears());
	}
	
	/**
	 * return new uid which not exists in DB
	 * tableLevel : study, series, image
	 */
	public static String createNewUIDNoExistingInDB(String tableLevel) {
		if(tableLevel == null) {
			return null;
		}
		tableLevel = tableLevel.toLowerCase();
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			return null;
		}
		String uid = UIDUtils.createUID();
		boolean exists = true;
		if(tableLevel.equals("study")) {
			while(exists) {
				if(!db.checkRecordExists("STUDY", "StudyInstanceUID", uid)) {
					exists = false;
				}else {
					uid = UIDUtils.createUID();
				}
			}
		}else if(tableLevel.equals("series")) {
			while(exists) {
				if(!db.checkRecordExists("SERIES", "SeriesInstanceUID", uid)) {
					exists = false;
				}else {
					uid = UIDUtils.createUID();
				}
			}
		}else if(tableLevel.equals("image")) {
			while(exists) {
				if(!db.checkRecordExists("IMAGE", "SOPInstanceUID", uid)) {
					exists = false;
				}else {
					uid = UIDUtils.createUID();
				}
			}
		}else {
			return null;
		}
		return uid;
	}
	
}
