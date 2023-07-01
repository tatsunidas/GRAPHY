/* *****publi BEGIN LICENSE BLOCK *****
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
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import com.vis.configuration.Resources;
import com.vis.core.util.PropertiesUtil;

/**
 * @author tatsunidas
 */
public class TagDict {
	
	//test
	public static void main(String[] args) {
		String n = TagDict.keyword(0x00100010);
		System.out.println(n);
	}
	
	// load from properties
	private static HashMap<String, String> rejectedTag = new HashMap<>();
	private static final HashMap<Integer, TagContext> tagDict = loadDict();
	
	private static HashMap<Integer, TagContext> loadDict() {
		rejectedTag = new HashMap<>();
		final Properties tagDictProp = PropertiesUtil.loadProperties(Resources.DicomDict.toURL());
		HashMap<Integer, TagContext> temp = new HashMap<>();
		Set<Object> keys = tagDictProp.keySet();
		for(Object key : keys) {
			// 0xggggeeee format
			String oxggggeeee = (String)key;

			/*
			 * avoid java.lang.NumberFormatException
			 * 
			 * Integer.decode(0xggggeeee);
			 * Integer.parseInt(ggggeeee, 16);
			 * 
			 * 0xFFFAFFFA=Digital Signatures Sequence,Digital​Signatures​Sequence,SQ,1
			 * 0xFFFCFFFC=Data Set Trailing Padding,Data​Set​Trailing​Padding,OB,1
			 * 0xFFFEE000=Item,Item,See Note 2,1
			 * 0xFFFEE00D=Item Delimitation Item,Item​Delimitation​Item,See Note 2,1
			 * 0xFFFEE0DD=Sequence Delimitation Item,Sequence​Delimitation​Item,See Note 2,1
			 */
			
			int radix = 16;
			BigInteger tag_int = new BigInteger(oxggggeeee.split("x")[1], radix);
			int g4e4 = tag_int.intValue();
			
			TagContext con = toContext(g4e4, tagDictProp.getProperty(oxggggeeee));
			if(con != null) {
				temp.put(g4e4, toContext(g4e4, tagDictProp.getProperty(oxggggeeee)));
			}
		}
		return temp;
	}
	
	
	
	private static TagContext toContext(int ggggeeee, String statement) {
		//0x60004000=Overlay Comments,Overlay​Comments,LT,1,RET
		String context[] = statement.split(",");
		
		if(context.length < 4) {
			rejectedTag.put(String.valueOf(ggggeeee), statement);
			return null;
		}
		
		String desc = context[0].trim();
		String keyword = context[1].trim();
		VR.Type[] vrs = toVRTypes(context[2].trim());
		String vm = context[3].trim();
		boolean ret = false;
		if(context.length > 4) {
			/*
			 * Attention, E1-1 is not include Retired tags,
			 * By coincidence, there may be a description that begins with "RET" in E1-1 description,
			 * but so far there is not (2023), so I will leave it as is.
			 */
			ret = context[4].trim().startsWith("RET");
		}
		TagContext con = new TagContext(ggggeeee, keyword, vrs, vm, ret, desc);
		return con;
	}
	
	private static VR.Type[] toVRTypes(String VRs){
		if (VRs.contains(" or ")) {
			VRs = VRs.replace(" or ", ",");
		}
		ArrayList<VR.Type> vrs = new ArrayList<>();
		String[] candidates = VRs.split(",");
		for(String c : candidates) {
			for (VR.Type t : VR.Type.values()) {
				if(t.name().equals(c)) {
					vrs.add(t);
				}
			}
		}
		return (VR.Type[])vrs.toArray(new VR.Type[vrs.size()]);
		
	}
	
	public static int tagOf(String keyword) {
		Set<Integer> tags = tagDict.keySet();
		for(Integer t:tags) {
			TagContext con = tagDict.get(t);
			if(con != null && con.keyword.equals(keyword)) {
				return con.ggggeeee;
			}
		}
		return -1;
	}
	
	public static String keyword(int ggggeeee) {
		TagContext con = tagDict.get(ggggeeee);
		if(con != null) {
			return con.keyword;
		}
		return null;
	}
	
	public static String vmOf(String keyword) {
		int tag = tagOf(keyword);
		if(tag != -1) {
			TagContext con = tagDict.get(tag);
			if(con != null) {
				return con.vm;
			}
		}
		return null;
	}
	
	public static String vmOf(int tag) {
		TagContext con = tagDict.get(tag);
		if(con != null) {
			return con.vm;
		}
		return null;
	}
	
	public static VR.Type[] vrType(int tag){
		TagContext con = tagDict.get(tag);
		if(con != null) {
			return (VR.Type[])con.vrTypes;
		}
		return null;
	}
	
	public static String vrTypeToString(int tag) {
		VR.Type[] vrs = vrType(tag);
		String str = "";
		int s = vrs.length;
		
		if(s == 0) {
			return null;
		}
		
		int i = 0;
		for(VR.Type v: vrs) {
			if(i == s-1) {
				str = str + v.name();
			}else {
				str = str + v.name() + ",";
			}
			i++;
		}
		return str;
	}
	
	// description
	public static String description(int tag){
		TagContext con = tagDict.get(tag);
		if(con != null) {
			return con.description;
		}
		return null;
	}
	
	// retire or not
	public static boolean retire(int tag){
		TagContext con = tagDict.get(tag);
		if(con != null) {
			return con.ret;
		}
		return false;
	}
	
	private static class TagContext {
		final int ggggeeee;
		final String keyword;
		final Object vrTypes;//VR.Type[]
		final String vm;//1 or 2-2n etc.
		final boolean ret;
		final String description;
		
		private TagContext(int ggggeeee, String keyword, VR.Type[] vrTypes, String vm, boolean ret, String description){
			this.ggggeeee = ggggeeee;
			this.keyword = keyword;
			this.vrTypes = vrTypes;
			this.vm = vm;
			this.ret = ret;
			this.description = description;
		}
	}
}
