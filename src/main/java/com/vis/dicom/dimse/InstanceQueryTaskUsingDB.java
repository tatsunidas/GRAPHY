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
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
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
 * ***** END LICENSE BLOCK ***** */

package com.vis.dicom.dimse;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

class InstanceQueryTaskUsingDB extends SeriesQueryTaskUsingDB {

    protected final String[] sopIUIDs;
    protected Attributes instRec;
    private int total = 0;
    private int process = 0;
    private ArrayList<HashMap<String,String>>instCandidate = null; 

    public InstanceQueryTaskUsingDB(Association as, PresentationContext pc, Attributes rq, Attributes keys, DcmQRSCP qrscp)
            throws DicomServiceException {
        super(as, pc, rq, keys, qrscp);
        sopIUIDs = StringUtils.maskNull(keys.getStrings(Tag.SOPInstanceUID));
        String patID = patRec.getString(Tag.PatientID);
        String studyUID = studyRec.getString(Tag.StudyInstanceUID);
        String seriesUID = seriesRec.getString(Tag.SeriesInstanceUID);
        instCandidate = db.getAllCandidate4InstanceQuery(patID,studyUID,seriesUID,sopIUIDs);
        this.total = instCandidate.size();
        wrappedFindNextInstance();
    }

    @Override
    public boolean hasMoreMatches() throws DicomServiceException {
        return instRec != null;
    }

    @Override
    public Attributes nextMatch() throws DicomServiceException {
        Attributes ret = new Attributes(patRec.size()
                + studyRec.size()
                + seriesRec.size()
                + instRec.size());
        ret.addAll(patRec);
        ret.addAll(studyRec);
        ret.addAll(seriesRec);
        ret.addAll(instRec);
        wrappedFindNextInstance();
        return ret;
    }

    private void wrappedFindNextInstance() throws DicomServiceException {
        try {
            findNextInstance();
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }
    /*
     * ReferencedFileIDは、
     * リンクのときはフルパス、DB内にあるときは相対パスを渡している。->未検証。
     */
//	(0004,1500) CS [DICOM\6EFD8DF8\FF3A35F6\4C11115A] ReferencedFileID
//	(0004,1510) UI [1.2.840.10008.5.1.4.1.1.4] ReferencedSOPClassUIDInFile//same as SOPClassUID
//	(0004,1511) UI [1.3.6.1.4.1.14519.5.2.1.3344.2526.3991481572793857949648742095//same as SOP Instance UID
//	(0004,1512) UI [1.2.840.10008.1.2] ReferencedTransferSyntaxUIDInFile//same as TransferSyntaxUID
//	(0020,0013) IS [6] InstanceNumber//mandatory for directory record
    private Attributes constructInfo(HashMap<String, String> instanceInfo)  {
		Attributes attrs = new Attributes(5);
		attrs.setString(Tag.ReferencedFileID, VR.CS, instanceInfo.get("ReferencedFileID"));
		attrs.setString(Tag.ReferencedSOPClassUIDInFile, VR.UI, instanceInfo.get("SOPClassUID"));
		attrs.setString(Tag.ReferencedSOPInstanceUIDInFile, VR.UI, instanceInfo.get("SOPInstanceUID"));
		attrs.setString(Tag.ReferencedTransferSyntaxUIDInFile, VR.UI, instanceInfo.get("TransferSyntaxUID"));
		attrs.setString(Tag.InstanceNumber, VR.IS, instanceInfo.get("InstanceNo"));
		return attrs;
	}

    protected boolean findNextInstance() throws IOException {
    	if (seriesRec == null) {
    		instRec = null;
    		return false;
    	}
		/* keyでヒットした件数すべての検索が完了したら終了 */
		if (this.process == this.total) {
			instRec = null;
		} else {
			/* レコードを取得 */
			HashMap<String, String> instInfo = instCandidate.get(process);
			instRec = constructInfo(instInfo);
			this.process++;
		}
		/* 親クラスのループを更新。スタディループを初期化する */
		while (instRec == null && super.findNextSeries()) {
			this.process = 0;
			/* この時点で、seriesRecは新しく更新されている。存在しない場合はNullを返す */
			String[] sopIUIDs = StringUtils.maskNull(keys.getStrings(Tag.SOPInstanceUID));
	        String patID = patRec.getString(Tag.PatientID);
	        String studyUID = studyRec.getString(Tag.StudyInstanceUID);
	        String seriesUID = seriesRec.getString(Tag.SeriesInstanceUID);
	        instCandidate = db.getAllCandidate4InstanceQuery(patID,studyUID,seriesUID,sopIUIDs);
			if (instCandidate != null && instCandidate.size() > 0) {
				instRec = constructInfo(instCandidate.get(process));
			}
			this.process++;
			/* 最後の時点でseriesRecがNullにならなければ、次の実行で最初からの流れになる */
		}
       return instRec != null;
    }
}