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
package com.vis.dicom;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class supports creation of a dicom_dict.properties.
 * 
 * 1. create copy data element from DICOM Standards (on html pages), then save it as txt. see, resources/DataElementCopyTxtFromStandards/. 
 * 2. execute txtToProperties(txtPath,propPath) 
 * 3. Last, aggregate all to dicom_dict.properties using aggregate() in this class.
 * 
 * @author tatsunidas
 *
 */
public class DicomDictTool {

	public static void main(String[] args) {
		
//		txtToProperties("/home/tatsunidas/デスクトップ/DataElementCopyTxtFromStandards/part6_6-1_RegistryofDICOMDataElements_20230628.txt",
//				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_6_1.properties", false);
//		
//		txtToProperties("/home/tatsunidas/デスクトップ/DataElementCopyTxtFromStandards/part6_7-1_RegistryofDICOMFileMetaElements.txt",
//				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_7_1.properties", false);
//		
//		txtToProperties("/home/tatsunidas/デスクトップ/DataElementCopyTxtFromStandards/part6_8-1_RegistryofDICOMDirectoryStructuringElements.txt",
//				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_8_1.properties", false);
//		
//		txtToProperties("/home/tatsunidas/デスクトップ/DataElementCopyTxtFromStandards/part7_E1-1_CommandFields.txt",
//				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_E1_1.properties", false);
//		
//		txtToProperties("/home/tatsunidas/デスクトップ/DataElementCopyTxtFromStandards/part7_E2-1_RetiredCommandFields.txt",
//				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_E2_1.properties", true);
		
		aggregate(
				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_6_1.properties",
				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_7_1.properties",
				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_8_1.properties",
				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_E1_1.properties",
				"/home/tatsunidas/デスクトップ/dicom_dict_no_validation_E2_1.properties",
				"/home/tatsunidas/デスクトップ/dicom_dict.properties"
				);
		
	}

	/**
	 * Create dicomdic.properties
	 * 
	 * 
	 * 
	 * @param pathToPart6CopyTxt : {@see DicomDictTool.part6_copy_sample}
	 * @param saveTo             : save destination of properties file.
	 * @param isE2_1 : all tags retired.
	 */
	static void txtToProperties(String pathToPart6CopyTxt, String saveTo, boolean isE2_1) {

		StringBuffer sb = new StringBuffer();
		ArrayList<String> tagProp = new ArrayList<>();
		try {
			Path path = Paths.get(pathToPart6CopyTxt);
			List<String> lines = Files.readAllLines(path);
			int total = lines.size();
			int itr = 0;
			boolean findTag = false;
			String v = "";
			int comp_cnt = 0;// 0:tag, 1:desc, 2:keyword, 3:VR, 4:VM
			for (String str : lines) {
				itr++;
				str = str.trim();
				if (str == null || str.length() < 1 || str.isBlank() || str.isEmpty() || str.equals("\n")) {
					continue;
				}

				if (findTag && str.contains("RET") && !str.startsWith("(") && !str.endsWith(")") && !str.contains(",")
						&& comp_cnt == 4) {
					v += str;
					findTag = false;
					tagProp.add(v);
					v = "";
					comp_cnt = 0;
					continue;
				} else if (findTag && str.contains("DICONDE") && !str.startsWith("(") && !str.endsWith(")")
						&& !str.contains(",") && comp_cnt == 4) {
					v += str;
					findTag = false;
					tagProp.add(v);
					v = "";
					comp_cnt = 0;
					continue;
				} else if (str.startsWith("(") && str.endsWith(")") && str.contains(",")) {
					if (findTag) {
						if(isE2_1) {
							v += "RET";
						}else {
							// init
							v = v.substring(0, v.length() - 1);// delete last ","
						}
						tagProp.add(v);
						v = "";
						comp_cnt = 0;
					}
					findTag = true;
					comp_cnt++;
					String tagString = str.replace("(", "");
					tagString = tagString.replace(")", "");
					tagString = tagString.replace(",", "");
					tagString = tagString.replace("x", "0");
					tagString = "0x" + tagString;
					v += tagString + "=";
					continue;
				}
				v += str + ",";
				comp_cnt++;
				if (total == itr) {
					// end of txt
					if (findTag) {
						if(isE2_1) {
							v += "RET";
						}else {
							// init
							v = v.substring(0, v.length() - 1);// delete last ","
						}
						tagProp.add(v);
					}
				}
			}

		} catch (java.io.IOException ioex) {
			ioex.printStackTrace();
		}
		int size = tagProp.size();
		int itr = 0;
		for (String s : tagProp) {
			itr++;
			if(itr != size) {
				sb.append(s + "\n");
			}else {
				sb.append(s);
			}
		}
		try (PrintWriter pw = new PrintWriter(
				new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveTo), "UTF-8")));) {
			pw.println(sb.toString());
			pw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// sort tags and write saveTo.
	static void aggregate(String txt_6_1, String txt_7_1, String txt_8_1, String txt_E1_1, String txt_E2_1, String saveTo) {
		StringBuilder sb = new StringBuilder();
		String arr[] = new String[] {txt_6_1, txt_7_1, txt_8_1, txt_E1_1, txt_E2_1};
		
		//sort tag
		ArrayList<String> tags = new ArrayList<>();
		for(String p : arr) {
			Path path = Paths.get(p);
			List<String> lines = null;
			try {
				lines = Files.readAllLines(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
			for (String str : lines) {
				str = str.trim();
				if (str == null || str.length() < 1 || str.isBlank() || str.isEmpty() || str.equals("\n")) {
					continue;
				}
				tags.add(str);
			}
		}
		Collections.sort(tags);
		int size = tags.size();
		int itr = 0;
		for(String s : tags) {
			itr++;
			if(itr == size) {
				sb.append(s);
			}else {
				sb.append(s + "\n");
			}
		}
		
		try (PrintWriter pw = new PrintWriter(
				new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveTo), "UTF-8")));) {
			pw.println(sb.toString());
			pw.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}


	static void createTagClassStrings() {

	}

	/**
	 * Part6CopyTxt is just copy of Part 6 Table 6-1 in HTML. This is example.
	 */
	static String part6_copy_sample = "(0008,0001)\n" + "\n" + "Length to End\n" + "\n" + "Length​To​End\n" + "\n"
			+ "UL\n" + "\n" + "1\n" + "\n" + "RET\n" + "\n" + "(0008,0005)\n" + "\n" + "Specific Character Set\n" + "\n"
			+ "Specific​Character​Set\n" + "\n" + "CS\n" + "\n" + "1-n\n" + "\n" + "\n" + "(0008,0006)\n" + "\n"
			+ "Language Code Sequence\n" + "\n" + "Language​Code​Sequence\n" + "\n" + "SQ\n" + "\n" + "1\n" + "\n"
			+ "\n" + "(0008,0008)\n" + "\n" + "Image Type\n" + "\n" + "Image​Type\n" + "\n" + "CS\n" + "\n" + "2-n";

}
