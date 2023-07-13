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
package com.vis.dicom.dcm4cheImpl;

import java.util.List;

import org.dcm4che3.data.ItemPointer;
import org.dcm4che3.io.DicomEncodingOptions;

/**
 * @author tatsunidas
 */
public class Interpreter {
	
	//============================================
	//vis to che
	//============================================
	
	static org.dcm4che3.data.DatePrecision datePrecisionChe(com.vis.dicom.DatePrecision dp){
		org.dcm4che3.data.DatePrecision dpChe = new org.dcm4che3.data.DatePrecision(dp.lastField, dp.includeTimezone);
		return dpChe;
	}
	
	static org.dcm4che3.data.DatePrecisions datePrecisionsChe(com.vis.dicom.DatePrecisions dps){
		com.vis.dicom.DatePrecision[] dpArr = dps.precisions;
		if(dpArr == null || dpArr.length == 0) {
			return new org.dcm4che3.data.DatePrecisions();
		}else {
			org.dcm4che3.data.DatePrecision[] dpsCheArr = new org.dcm4che3.data.DatePrecision[dpArr.length];
			int i = 0;
			for(com.vis.dicom.DatePrecision dp : dpArr) {
				dpsCheArr[i++] = new org.dcm4che3.data.DatePrecision(dp.lastField, dp.includeTimezone);
			}
			org.dcm4che3.data.DatePrecisions dpsChe = new org.dcm4che3.data.DatePrecisions();
			dpsChe.precisions = dpsCheArr;
			return dpsChe;
		}
	}

	static org.dcm4che3.data.VR vrChe(com.vis.dicom.VR vr){
		return org.dcm4che3.data.VR.valueOf(vr.code());
	}
	
	static org.dcm4che3.data.VR.Holder vrHolderChe(com.vis.dicom.VR.Holder vr){
		if(vr == null) {
			return null;
		}
		org.dcm4che3.data.VR.Holder holder = new org.dcm4che3.data.VR.Holder();
		holder.vr = vrChe(vr.vr);
		return holder;
	}
	
	static org.dcm4che3.data.SpecificCharacterSet specificCharacterSetChe(com.vis.dicom.SpecificCharacterSet scs){
		return org.dcm4che3.data.SpecificCharacterSet.valueOf(scs.toDicomCodes());
	}
	
	static String uidChe(com.vis.dicom.UID uid){
		return org.dcm4che3.data.UID.forName(uid.name());
	}
	
	static org.dcm4che3.data.ItemPointer itemPointerChe(com.vis.dicom.ItemPointer ip){
		String pc = ip.privateCreator;
		int seqTag = ip.sequenceTag;
		int itmInd = ip.itemIndex;
		return new ItemPointer(pc, seqTag, itmInd);
	}
	
	static org.dcm4che3.data.ItemPointer[] itemPointersChe(com.vis.dicom.ItemPointer[] ips){
		org.dcm4che3.data.ItemPointer[] list = new org.dcm4che3.data.ItemPointer[ips.length];
		int i = 0;
		for(com.vis.dicom.ItemPointer ip : ips) {
			list[i++] = itemPointerChe(ip);
		}
		return list;
	}
	
	static List<org.dcm4che3.data.ItemPointer> itemPointersChe(List<com.vis.dicom.ItemPointer> ips){
		List<org.dcm4che3.data.ItemPointer> list = new java.util.ArrayList<>();
		for(com.vis.dicom.ItemPointer ip : ips) {
			list.add(itemPointerChe(ip));
		}
		return list;
	}
	
	static org.dcm4che3.data.DateRange dateRangeChe(com.vis.dicom.DateRange dr){
		return new org.dcm4che3.data.DateRange(dr.getStartDate(), dr.getEndDate());
	}
	
//	static org.dcm4che3.data.Attributes.UpdatePolicy updatePolicyChe(com.vis.dicom.DicomObject.UpdatePolicy policy){
//		return org.dcm4che3.data.Attributes.UpdatePolicy.valueOf(policy.name());
//	}
	
	static org.dcm4che3.io.DicomEncodingOptions dicomEncodingOpsChe(com.vis.dicom.DicomEncodingOptions ops){
		boolean groupLength = ops.groupLength;
		boolean undefSeqLength = ops.undefSequenceLength;
		boolean undefEmptySeqLength = ops.undefEmptySequenceLength;
		boolean undefItemLength = ops.undefItemLength;
		boolean undefEmptyItemLength = ops.undefEmptyItemLength;
		org.dcm4che3.io.DicomEncodingOptions deoChe = new DicomEncodingOptions(groupLength, undefSeqLength, undefEmptySeqLength, undefItemLength, undefEmptyItemLength);
		return deoChe;
	}

	//============================================
	//che to vis
	//============================================
	
	static com.vis.dicom.DatePrecision datePrecision(org.dcm4che3.data.DatePrecision dpChe){
		com.vis.dicom.DatePrecision dp = new com.vis.dicom.DatePrecision(dpChe.lastField, dpChe.includeTimezone);
		return dp;
	}
	
	static com.vis.dicom.DatePrecisions datePrecisions(org.dcm4che3.data.DatePrecisions dpsChe){
		org.dcm4che3.data.DatePrecision[] dpCheArr = dpsChe.precisions;
		if(dpCheArr == null || dpCheArr.length == 0) {
			return new com.vis.dicom.DatePrecisions();
		}else {
			com.vis.dicom.DatePrecision[] dpsArr = new com.vis.dicom.DatePrecision[dpCheArr.length];
			int i = 0;
			for(org.dcm4che3.data.DatePrecision dpChe : dpCheArr) {
				dpsArr[i++] = new com.vis.dicom.DatePrecision(dpChe.lastField, dpChe.includeTimezone);
			}
			com.vis.dicom.DatePrecisions dps = new com.vis.dicom.DatePrecisions();
			dps.precisions = dpsArr;
			return dps;
		}
	}
	
	static com.vis.dicom.VR vr(org.dcm4che3.data.VR vrChe){
		return com.vis.dicom.VR.valueOf(vrChe.code());
	}
	
	static com.vis.dicom.VR.Holder vrHolder(org.dcm4che3.data.VR.Holder vrChe){
		if(vrChe == null) {
			return null;
		}
		com.vis.dicom.VR.Holder holder = new com.vis.dicom.VR.Holder();
		holder.vr = vr(vrChe.vr);
		return holder;
	}
	
	static com.vis.dicom.SpecificCharacterSet specificCharacterSet(org.dcm4che3.data.SpecificCharacterSet scs){
		return com.vis.dicom.SpecificCharacterSet.valueOf(scs.toCodes());
	}
	
	static com.vis.dicom.UID uid(String uid){
		return com.vis.dicom.UID.uidOf(uid);
	}
	
	static com.vis.dicom.ItemPointer itemPointer(org.dcm4che3.data.ItemPointer ip){
		String pc = ip.privateCreator;
		int seqTag = ip.sequenceTag;
		int itmInd = ip.itemIndex;
		return new com.vis.dicom.ItemPointer(pc, seqTag, itmInd);
	}
	
	static com.vis.dicom.ItemPointer[] itemPointers(org.dcm4che3.data.ItemPointer[] ips){
		com.vis.dicom.ItemPointer[] list = new com.vis.dicom.ItemPointer[ips.length];
		int i = 0;
		for(org.dcm4che3.data.ItemPointer ip : ips) {
			list[i++] = itemPointer(ip);
		}
		return list;
	}
	
	static List<com.vis.dicom.ItemPointer> itemPointers(List<org.dcm4che3.data.ItemPointer> ips){
		List<com.vis.dicom.ItemPointer> list = new java.util.ArrayList<>();
		for(org.dcm4che3.data.ItemPointer ip : ips) {
			list.add(itemPointer(ip));
		}
		return list;
	}
	
	static com.vis.dicom.DateRange dateRange(org.dcm4che3.data.DateRange dr){
		return new com.vis.dicom.DateRange(dr.getStartDate(), dr.getEndDate());
	}
	
//	static com.vis.dicom.DicomObject.UpdatePolicy updatePolicy(org.dcm4che3.data.Attributes.UpdatePolicy policy){
//		return com.vis.dicom.DicomObject.UpdatePolicy.valueOf(policy.name());
//	}
	
	static com.vis.dicom.DicomEncodingOptions dicomEncodingOpsChe(org.dcm4che3.io.DicomEncodingOptions ops){
		boolean groupLength = ops.groupLength;
		boolean undefSeqLength = ops.undefSequenceLength;
		boolean undefEmptySeqLength = ops.undefEmptySequenceLength;
		boolean undefItemLength = ops.undefItemLength;
		boolean undefEmptyItemLength = ops.undefEmptyItemLength;
		com.vis.dicom.DicomEncodingOptions deo = new com.vis.dicom.DicomEncodingOptions(groupLength, undefSeqLength, undefEmptySeqLength, undefItemLength, undefEmptyItemLength);
		return deo;
	}
}
