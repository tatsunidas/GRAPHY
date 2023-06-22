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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;

class StudyQueryTaskUsingDB extends PatientQueryTaskUsingDB {

    protected final String[] studyIUIDs;
    protected Attributes studyRec;
    private int total = 0;
    private int process = 0;
    private ArrayList<HashMap<String,String>>studyCandidate = null; 

    public StudyQueryTaskUsingDB(Association as, PresentationContext pc, Attributes rq, Attributes keys, DcmQRSCP qrscp)
            throws DicomServiceException {
        super(as, pc, rq, keys, qrscp);
        /* if not specified studyiuid, this length is 0 */
        studyIUIDs = StringUtils.maskNull(keys.getStrings(Tag.StudyInstanceUID));
//        System.out.println(studyIUIDs.length);
        /* obtain all studies with keys*/
        studyCandidate = db.getAllCandidate4StudyQuery(patRec,keys);
        this.total = studyCandidate.size();
        wrappedFindNextStudy();
    }

    @Override
    public boolean hasMoreMatches() throws DicomServiceException {
        return studyRec != null;
    }

    @Override
    public Attributes nextMatch() throws DicomServiceException {
        Attributes ret = new Attributes(patRec.size() + studyRec.size());
        ret.addAll(patRec);
        ret.addAll(studyRec);
        wrappedFindNextStudy();
        return ret;
    }

    private void wrappedFindNextStudy() throws DicomServiceException {
        try {
            findNextStudy();
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }
    
// (0008,0005) CS [ISO_IR 100] SpecificCharacterSet
//	(0008,0020) DA [19970806] StudyDate
//	(0008,0030) TM [105625] StudyTime
//	(0008,0050) SH [9760491563916689] AccessionNumber
//	(0008,0090) PN [] ReferringPhysicianName
//	(0008,1030) LO [MRI Hd wo&w] StudyDescription
//	(0020,000D) UI [1.3.6.1.4.1.14519.5.2.1.3344.2526.8727360284436712626153837408
//	(0020,0010) SH [] StudyID
    private Attributes constructInfo(HashMap<String, String> studyInfo)  {
		Attributes attrs = new Attributes(8);
		attrs.setString(Tag.SpecificCharacterSet, VR.CS, new String[] {"ISO_IR","87"});//Fixing(固定)
		attrs.setString(Tag.StudyDate, VR.DA, studyInfo.get("StudyDate"));
		attrs.setString(Tag.StudyTime, VR.TM, studyInfo.get("StudyTime"));
		attrs.setString(Tag.AccessionNumber, VR.SH, studyInfo.get("AccessionNo"));
		attrs.setString(Tag.ReferringPhysicianName, VR.PN, studyInfo.get("ReferringPhysicianName"));
		attrs.setString(Tag.StudyDescription, VR.LO, studyInfo.get("StudyDescription"));
		attrs.setString(Tag.StudyInstanceUID, VR.UI, studyInfo.get("StudyInstanceUID"));
		attrs.setString(Tag.StudyID, VR.SH, studyInfo.get("StudyID"));
		return attrs;
	}

    protected boolean findNextStudy() throws IOException {
    	/* 親クラスのpatRecがNULLなら検索は終了 */
    	/* 初回patient検索を除いて、必ずpatRecはある状態になる */
    	if (patRec == null) {
    		studyRec = null;
    		return false;
    	}
    	/* keyでヒットした件数すべての検索が完了したらSTUDY検索を終了 */
		if (this.process == this.total) {
			studyRec = null;
		}else {
			/* レコードを取得 */
			HashMap<String, String> studyInfo = studyCandidate.get(process);
			studyRec = constructInfo(studyInfo);
			this.process++;
		}
    	/* 親クラスのループを更新。スタディループを初期化する */
    	while (studyRec == null && super.findNextPatient()) {
    		this.process = 0;
    		/* この時点で、patRecは新しく更新されている。存在しない場合はNullを返す */
    		studyCandidate = db.getAllCandidate4StudyQuery(patRec,keys);
    		if (studyCandidate != null && studyCandidate.size()>0) {
    			studyRec = constructInfo(studyCandidate.get(process));
    		} 
    		this.process++;
    		/* 最後の時点でstudyRecがNullにならなければ、次の実行で最初からの流れになる */
    	}
       return studyRec != null;
    }
}