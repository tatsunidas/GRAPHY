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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;
import org.dcm4che3.media.RecordFactory;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicQueryTask;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;

import com.vis.db.DatabaseHandler;

/**
 * 
 * @author Gunter
 * @author tatsunidas
 *
 */

class PatientQueryTaskUsingDB extends BasicQueryTask {

    protected final String[] patIDs;
    protected final DatabaseHandler db = DatabaseHandler.getInstance();
    protected final RecordFactory recFact;
    protected final String availability;
    protected final boolean ignoreCaseOfPN;
    protected final boolean matchNoValue;
    protected final int delayCFind;
    protected Attributes patRec;
    
    private int total = 0;//will find patients
    private int process = 0;//current loop
    private ArrayList<HashMap<String,String>>patCandidate = null; 

    /*
     * keysはあくまで1患者1クエリ。
     * ただし、一致するものはすべて抽出する。
     * このDBは患者IDは一意とする。
     * そうすれば、生年月日や性別のみで検索されても候補のOIDリストが無くなるまで、
     * 再帰検索するアルゴリズムで行ける。
     * 
     * 最初に、keysにマッチするPIDをリストする。
     * 途中まで消してしまったけど、Studyのアルゴリズムをここで使うようにしてみよう。
     * 
     * できなければ、当面はDicomDirを使う。
     */
    public PatientQueryTaskUsingDB(Association as, PresentationContext pc, Attributes rq, Attributes keys, DcmQRSCP qrscp)
            throws DicomServiceException {
        super(as, pc, rq, keys);
        /* padIDsはキーに指定されていないと空のまま。なお、ここに追加されるわけでもない。途中の計算用 */
        this.patIDs = StringUtils.maskNull(keys.getStrings(Tag.PatientID));
        this.recFact = qrscp.getRecordFactory();
        this.availability = qrscp.getInstanceAvailability();
        this.ignoreCaseOfPN = qrscp.isIgnoreCaseOfPN();
        this.matchNoValue = qrscp.isMatchNoValue();
        this.delayCFind = qrscp.getDelayCFind();
        /*
         * get all candidate
         * 1.if patientID(PrimaryKey) in keys,search pid & others.
         * 2. esle , find all dataset matched in keys. 
         */
        patCandidate = db.getAllCandidate4PatientQuery(patIDs);
        if(patCandidate == null) {
        	total = 0;
        }else {
        	total = patCandidate.size();
        }
        wrappedFindNextPatient();
    }

    @Override
    public boolean hasMoreMatches() throws DicomServiceException {
        return patRec != null;
    }

    @Override
    public Attributes nextMatch() throws DicomServiceException {
        Attributes tmp = patRec;
        wrappedFindNextPatient();
        return tmp;
    }

    @Override
    protected Attributes adjust(Attributes match) {
        Attributes adjust = super.adjust(match);
        adjust.remove(Tag.DirectoryRecordType);
        if (keys.contains(Tag.SOPClassUID))
             adjust.setString(Tag.SOPClassUID, VR.UI,
                     match.getString(Tag.ReferencedSOPClassUIDInFile));
        if (keys.contains(Tag.SOPInstanceUID))
             adjust.setString(Tag.SOPInstanceUID, VR.UI,
                     match.getString(Tag.ReferencedSOPInstanceUIDInFile));
        adjust.setString(Tag.QueryRetrieveLevel, VR.CS,
                keys.getString(Tag.QueryRetrieveLevel));
        adjust.setString(Tag.RetrieveAETitle, VR.AE, as.getCalledAET());
        if (availability != null) {
        	adjust.setString(Tag.InstanceAvailability, VR.CS, availability);
        }
        /*
         * UserDefinedなタグなので、コメントアウト。
         * 計算の仕方がいまいちわからない。
         */
//        adjust.setString(Tag.StorageMediaFileSetID, VR.SH, ddr.getFileSetID());
//        adjust.setString(Tag.StorageMediaFileSetUID, VR.UI, ddr.getFileSetUID());
        match.setString(Tag.SOPClassUID, VR.UI,
                match.getString(Tag.ReferencedSOPClassUIDInFile));
        match.setString(Tag.SOPInstanceUID, VR.UI,
                match.getString(Tag.ReferencedSOPInstanceUIDInFile));
        if (delayCFind > 0)
            try {
                Thread.sleep(delayCFind);
            } catch (InterruptedException ignore) {}
        return adjust;
    }

    private void wrappedFindNextPatient() throws DicomServiceException {
        try {
            findNextPatient();
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }
    
	/*
	 * at this time,
	 * return only mandatory infomation .
	 * see, dicomdir usecase::TestStorageCommitment.java
	 */
//	(0010,0010) PN [LGG-203] PatientName
//	(0010,0020) LO [LGG-203] PatientID
//	(0010,0030) DA [] PatientBirthDate
//	(0010,0040) CS [M] PatientSex
    private Attributes constructInfo(HashMap<String, String> patInfo)  {
		Attributes attrs = new Attributes(4);
		attrs.setString(Tag.PatientID, VR.LO, patInfo.get("PatientId"));//Id in sql
		attrs.setString(Tag.PatientName, VR.PN, patInfo.get("PatientName"));
		attrs.setString(Tag.PatientBirthDate, VR.DA, patInfo.get("PatientBirthDate"));
		attrs.setString(Tag.PatientSex, VR.CS, patInfo.get("PatientSex"));
		
		System.out.println(attrs.toString());
		
		return attrs;
	}

	protected boolean findNextPatient() throws IOException {
		if(process == total) {
			patRec = null;
			return false;
		}
		HashMap<String, String> patInfo = patCandidate.get(process);
		patRec = constructInfo(patInfo);
		process++;
		return patRec != null;
	}
}