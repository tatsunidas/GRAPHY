package com.vis.core.ui.function;

import com.vis.dicom.DicomObject;

/**
 * TODO
 * @author tatsunidas
 *
 */
public class ClinicalTrialsTools {
	
	private ClinicalTrialsTools() {};
	
	public static void addClinicalTrialsAttributes() {
		
	}
	
	public static void removeClinicalTrialsAttributes() {
		
	}
	
	public static boolean isSafePrivateAttribute(int tag, DicomObject dcm) {
		// System.err.println("ClinicalTrialsAttributes.isSafePrivateAttribute(); // checking "+tag);
		boolean safe = false;
		//TagUtils.isPrivateGroup(tag)//check this tag is private
		//int creatorTagOf(String privateCreator, int tag, boolean reserve)
		//privateCreatorOf(int tag)//if null, this is not creator
		
		//isPrivateCreator
		//Private creator tags are those with odd-numbered groups with elements between 0x0001 and 0x00ff.
		if (dcm.privateCreatorOf(tag) != null) {
			safe = true; // keep all creators, since may need them, and are harmless (and need them to
							// check real private tags later)
		} else {
			return false;
		}
		// System.err.println("ClinicalTrialsAttributes.isSafePrivateAttribute():
		// safe="+safe);
		return safe;
	}
	
	//getNestedDatasetを使う。

}
