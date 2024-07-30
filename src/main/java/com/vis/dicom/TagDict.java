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
import com.vis.core.util.StringUtils;

/**
 * If you want to re-build dicom dictionary, see DicomDictTool.class.
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
	
	public enum Type {
        STANDARD, PRIVATE, PRIVATE_CREATOR;
        public static Type typeOf(int tag) {
            return (tag & 0x00010000) != 0 ? (tag & 0x0000FF00) != 0 ? PRIVATE : PRIVATE_CREATOR : STANDARD;
        }
    }

    private static char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final byte INV_HEX_DIGITS[] = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
             0,  1,  2,  3,  4,  5,  6,  7,  8,  9, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15
    };

    public static String shortToHexString(int n) {
        char[] s = {
                HEX_DIGITS[(n >>> 12) & 0xF],
                HEX_DIGITS[(n >>> 8) & 0xF],
                HEX_DIGITS[(n >>> 4) & 0xF],
                HEX_DIGITS[(n >>> 0) & 0xF] };
        return new String(s);
    }

    public static String toHexString(int tag) {
        char[] s = {
                HEX_DIGITS[(tag >>> 28)],
                HEX_DIGITS[(tag >>> 24) & 0xF],
                HEX_DIGITS[(tag >>> 20) & 0xF],
                HEX_DIGITS[(tag >>> 16) & 0xF],
                HEX_DIGITS[(tag >>> 12) & 0xF],
                HEX_DIGITS[(tag >>> 8) & 0xF],
                HEX_DIGITS[(tag >>> 4) & 0xF],
                HEX_DIGITS[(tag >>> 0) & 0xF] };
        return new String(s);
    }
	
	private static HashMap<Integer, TagContext> loadDict() {
		rejectedTag = new HashMap<>();
		final Properties tagDictProp = PropertiesUtil.loadProperties(Resources.DicomDict.tempFile().getAbsolutePath());
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
			
			TagContext con = toContext(g4e4, oxggggeeee, tagDictProp.getProperty(oxggggeeee));
			if(con != null) {
				temp.put(g4e4, toContext(g4e4, oxggggeeee, tagDictProp.getProperty(oxggggeeee)));
			}
		}
		return temp;
	}
	
	// TODO Anatomic​Structure​Space​Or​Region​Sequence
	
	private static TagContext toContext(int ggggeeee, String oxggggeeee, String statement) {
		//0x60004000=Overlay Comments,Overlay​Comments,LT,1,RET
		String context[] = statement.split(",");
		
		if(context.length < 4) {
			rejectedTag.put(String.valueOf(ggggeeee), statement);
			return null;
		}
		
		String desc = context[0].trim();
		String keyword = context[1].trim();
		VR[] vrs = toVRs(context[2].trim());
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
		TagContext con = new TagContext(ggggeeee, oxggggeeee, keyword, vrs, vm, ret, desc);
		return con;
	}
	
	private static VR[] toVRs(String VRs){
		if (VRs.contains(" or ")) {
			VRs = VRs.replace(" or ", ",");
		}
		ArrayList<VR> vrs = new ArrayList<>();
		String[] candidates = VRs.split(",");
		for(String c : candidates) {
			for (VR t : VR.values()) {
				if(t.name().equals(c)) {
					vrs.add(t);
				}
			}
		}
		return (VR[])vrs.toArray(new VR[vrs.size()]);
		
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
	
	public static String tagHexStringOf(String keyword) {
		Set<Integer> tags = tagDict.keySet();
		for(Integer t:tags) {
			TagContext con = tagDict.get(t);
			if(con != null && con.keyword.equals(keyword)) {
				return con.oxggggeeee;
			}
		}
		return null;
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
	
	public static VR[] vrType(int tag){
		TagContext con = tagDict.get(tag);
		if(con != null) {
			return (VR[])con.vrTypes;
		}
		return null;
	}
	
	public static String vrTypeToString(int tag) {
		VR[] vrs = vrType(tag);
		String str = "";
		int s = vrs.length;
		
		if(s == 0) {
			return null;
		}
		
		int i = 0;
		for(VR v: vrs) {
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
	
	public static HashMap<String, String> getTagAndKeywordSet(){
		HashMap<String, String> set = new HashMap<>();
		Set<Integer> tags = tagDict.keySet();
		for(int tag : tags) {
			String keyword = keyword(tag);
			String tagInHex = tagHexStringOf(keyword);
			set.put(tagInHex, keyword);
		}
		return set;
	}
	
	// TagUtils
	
	

    public static String[] toHexStrings(int[] vals) {
        int n = vals.length;
        String[] ss = new String[n];
        for (int i = 0; i < n; i++)
            ss[i] = toHexString(vals[i]);

        return ss;
    }

    public static String toHexString(byte[] b) {
        char[] s = new char[b.length << 1];
        for (int i = 0, j = 0; i < b.length; i++) {
            s[j++] = HEX_DIGITS[(b[i] >>> 4) & 0xF];
            s[j++] = HEX_DIGITS[b[i] & 0xF];
        }
        return new String(s);
    }

    public static byte[] fromHexString(String s) {
        char[] chars = s.toCharArray();
        byte[] b = new byte[chars.length / 2];
        try {
            for (int i = 0, j = 0; i < b.length; i++)
                b[i] = (byte) ((INV_HEX_DIGITS[chars[j++]] << 4) | INV_HEX_DIGITS[chars[j++]]);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(s);
        }
        return b;
    }

    public static int intFromHexString(String s) {
        char[] chars = s.toCharArray();
        int val = 0;
        for (int i = 0; i < chars.length; i++)
            val = (val << 4) | INV_HEX_DIGITS[chars[i]];

        return val;
    }

    public static int[] fromHexStrings(String[] ss) {
        int n = ss.length;
        int[] vals = new int[n];
        for (int i = 0; i < n; i++)
            vals[i] = intFromHexString(ss[i]);

        return vals;
    }

    public static String toString(int tag) {
        char[] s = {
                '(',
                HEX_DIGITS[(tag >>> 28)],
                HEX_DIGITS[(tag >>> 24) & 0xF],
                HEX_DIGITS[(tag >>> 20) & 0xF],
                HEX_DIGITS[(tag >>> 16) & 0xF],
                ',',
                HEX_DIGITS[(tag >>> 12) & 0xF],
                HEX_DIGITS[(tag >>> 8) & 0xF],
                HEX_DIGITS[(tag >>> 4) & 0xF],
                HEX_DIGITS[(tag >>> 0) & 0xF],
                ')'};
        return new String(s);
    }

    public static int groupNumber(int tag) {
        return tag >>> 16;
    }

    public static int elementNumber(int tag) {
        return tag & 0xFFFF;
    }

    public static boolean isGroupLength(int tag) {
        return elementNumber(tag) == 0;
    }

    public static boolean isPrivateCreator(int tag) {
        return (tag & 0x00010000) != 0
            && (tag & 0x0000FF00) == 0
            && (tag & 0x000000F0) != 0;
    }

    public static boolean isPrivateGroup(int tag) {
        return (tag & 0x00010000) != 0;
    }

    public static boolean isPrivateTag(int tag) {
        return (tag & 0x00010000) != 0
            && (tag & 0x0000FF00) != 0;
    }

    public static int toTag(int groupNumber, int elementNumber) {
        return groupNumber << 16 | elementNumber;
    }

    public static int toPrivateTag(int creatorTag, int elementNumber) {
        return (creatorTag & 0xffff0000) 
             | ((creatorTag & 0xff) << 8
             | (elementNumber & 0xff));
    }

    public static int creatorTagOf(int tag) {
        return (tag & 0xffff0000) | ((tag >>> 8) & 0xff);
    }

    public static int groupLengthTagOf(int tag) {
        return tag & 0xffff0000;
    }

    public static boolean isItem(int tag) {
        return tag == Tag.Item
            || tag == Tag.Item​Delimitation​Item
            || tag == Tag.Sequence​Delimitation​Item;
    }

    public static boolean isFileMetaInformation(int tag) {
        return (tag & 0xffff0000) == 0x00020000;
    }

    public static int normalizeRepeatingGroup(int tag) {
        int gg000000 = tag & 0xffe00000;
        return (gg000000 == 0x50000000
             || gg000000 == 0x60000000)
                    ? tag & 0xffe0ffff
                    : tag;
    }

    public static int forName(String name) {
        try {
            return Integer.parseInt(name, 16);
        } catch (NumberFormatException nfe) {
            try {
                return Tag.class.getField(name).getInt(null);
            } catch (Exception e) {
                return -1;
            }
        }
    }

    public static int[] parseTagPath(String tagPath) {
        String[] names = StringUtils.split(tagPath, '.');
        int[] tags = new int[names.length];
        for (int i = 0; i < tags.length; i++)
            if ((tags[i] = forName(names[i])) == -1)
                throw new IllegalArgumentException("tagPath: " + tagPath);
        return tags;
    }
	
	private static class TagContext {
		final int ggggeeee;
		final String oxggggeeee;
		final String keyword;
		final VR[] vrTypes;
		final String vm;//1 or 2-2n etc.
		final boolean ret;
		final String description;
		
		private TagContext(int ggggeeee, String oxggggeeee, String keyword, VR[] vrTypes, String vm, boolean ret, String description){
			this.ggggeeee = ggggeeee;
			this.oxggggeeee = oxggggeeee;//0xggggeeee format
			this.keyword = keyword;
			this.vrTypes = vrTypes;
			this.vm = vm;
			this.ret = ret;
			this.description = description;
		}
	}
}
