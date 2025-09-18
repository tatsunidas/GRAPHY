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

package com.vis.core.util;

import java.time.LocalDate;
import java.time.Period;
import java.util.Calendar;
import java.util.Properties;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.UIDUtils;

public class DBUtils {
	
	public static final String defaultDBLocation() {
		return ConfigInfo.DefaultDBLocation.toString();
	}
	
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
	
	/**
	 * 
	 */
	public static void updateAEProperties(String aet, String host, String port, String CipherSeq /*Cipher1:Cipher2:... format*/) {
		PropertiesUtil.setPropertyAt("./"+ConfigInfo.SERVER_AE_Props.toString(), aet, host+":"+port+":"+CipherSeq);
	}
	
	public static void deleteAEProperties(String aet) {
		PropertiesUtil.deletePropertyAt("./"+ConfigInfo.SERVER_AE_Props.toString(), aet);
	}
	
	/**
	 * Use DatabaseHandler.getLocalDBLocation
	 * @return
	 */
	@Deprecated
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
