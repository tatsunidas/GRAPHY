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

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;
import org.dcm4che3.media.DicomDirReader;
import org.dcm4che3.media.RecordFactory;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicQueryTask;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;

class PatientQueryTaskUsingDcmDir extends BasicQueryTask {

    protected final String[] patIDs;
    protected final DicomDirReader ddr;
    protected final RecordFactory recFact;
    protected final String availability;
    protected final boolean ignoreCaseOfPN;
    protected final boolean matchNoValue;
    protected final int delayCFind;
    protected Attributes patRec;

    /*
     * pc : 通信の確立状況。resultは0が成功。それ以外は何らかの理由でリジェクト
     * 
     * PresentationContext[id: 1　result: 0 - acceptance　ts: 1.2.840.10008.1.2 - Implicit VR Little Endian]
     * 
     * rq : reqests, e.g,
     * (0000,0002) UI [1.2.840.10008.5.1.4.1.2.2.1] AffectedSOPClassUID
     * (0000,0100) US [32] CommandField
     * (0000,0110) US [1] MessageID
     * (0000,0700) US [0] Priority
     * (0000,0800) US [0] CommandDataSetType
     * 
     * keys : find query keys, e.g,
     * (0008,0052) CS [STUDY] QueryRetrieveLevel
     * (0010,0010) PN [LGG-203] PatientName
     * 
     */
    public PatientQueryTaskUsingDcmDir(Association as, PresentationContext pc, Attributes rq, Attributes keys, DcmQRSCP qrscp)
            throws DicomServiceException {
        super(as, pc, rq, keys);
       
        //debug
//        System.out.println(pc.toString());
//        System.out.println(rq.toString());
//        System.out.println(keys.toString());
        
        /* keysに含まれない場合は空の配列 */
        this.patIDs = StringUtils.maskNull(keys.getStrings(Tag.PatientID));
        this.ddr = qrscp.getDicomDirReader();
        this.recFact = qrscp.getRecordFactory();
        this.availability = qrscp.getInstanceAvailability();
        this.ignoreCaseOfPN = qrscp.isIgnoreCaseOfPN();
        this.matchNoValue = qrscp.isMatchNoValue();//qrscpの検索モードの一つ
        this.delayCFind = qrscp.getDelayCFind();
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

    /*
     * (non-Javadoc)
     * @see org.dcm4che3.net.service.BasicQueryTask#adjust(org.dcm4che3.data.Attributes)
     * 
     * matchにはnextMatchで新しく見つかった各レベルの**Recが再帰的に代入される
     */
    @Override
    protected Attributes adjust(Attributes match) {
    	
    	//sample
//    	System.out.println(match.toString());
//    	(0004,1400) UL [0] OffsetOfTheNextDirectoryRecord
//    	(0004,1410) US [65535] RecordInUseFlag
//    	(0004,1420) UL [1524] OffsetOfReferencedLowerLevelDirectoryEntity
//    	(0004,1430) CS [STUDY] DirectoryRecordType
//    	(0008,0005) CS [ISO_IR 100] SpecificCharacterSet
//    	(0008,0020) DA [19970806] StudyDate
//    	(0008,0030) TM [105625] StudyTime
//    	(0008,0050) SH [9760491563916689] AccessionNumber
//    	(0008,0090) PN [] ReferringPhysicianName
//    	(0008,1030) LO [MRI Hd wo&w] StudyDescription
//    	(0010,0010) PN [LGG-203] PatientName
//    	(0010,0020) LO [LGG-203] PatientID
//    	(0010,0030) DA [] PatientBirthDate
//    	(0010,0040) CS [M] PatientSex
//    	(0020,000D) UI [1.3.6.1.4.1.14519.5.2.1.3344.2526.8727360284436712626153837408
//    	(0020,0010) SH [] StudyID
    	
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
        if (availability != null)
            adjust.setString(Tag.InstanceAvailability, VR.CS, availability);
        adjust.setString(Tag.StorageMediaFileSetID, VR.SH, ddr.getFileSetID());
        adjust.setString(Tag.StorageMediaFileSetUID, VR.UI, ddr.getFileSetUID());
        match.setString(Tag.SOPClassUID, VR.UI,
                match.getString(Tag.ReferencedSOPClassUIDInFile));
        match.setString(Tag.SOPInstanceUID, VR.UI,
                match.getString(Tag.ReferencedSOPInstanceUIDInFile));
        
        //sample 
//        System.out.println(adjust.toString());
//        (0008,0005) CS [ISO_IR 100] SpecificCharacterSet
//        (0008,0052) CS [STUDY] QueryRetrieveLevel
//        (0008,0054) AE [GRAPHY] RetrieveAETitle
//        (0010,0010) PN [LGG-203] PatientName
//        (0088,0130) SH [] StorageMediaFileSetID
//        (0088,0140) UI [2.25.186947028510613112149268043176713034233] StorageMediaFile
        
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
     * patRec example
     * (0004,1400) UL [0] OffsetOfTheNextDirectoryRecord
     * (0004,1410) US [65535] RecordInUseFlag
     * (0004,1420) UL [1280] OffsetOfReferencedLowerLevelDirectoryEntity
     * (0004,1430) CS [PATIENT] DirectoryRecordType
     * (0008,0005) CS [ISO_IR 100] SpecificCharacterSet
     * (0010,0010) PN [LGG-203] PatientName
     * (0010,0020) LO [LGG-203] PatientID
     * (0010,0030) DA [] PatientBirthDate
     * (0010,0040) CS [M] PatientSex
     * 
     * あくまで1クエリ/1患者の単位
     * keysに複数患者は指定しない。たとえば、PIDを2つ入れても、後者PIDのみが認識される。
     * ただし、マッチする患者はすべて抽出する。
     */
    protected boolean findNextPatient() throws IOException {
        if (patRec == null)
            patRec = ddr.findPatientRecord(keys, recFact, ignoreCaseOfPN, matchNoValue);
        else if (patIDs.length == 1)//もし1つしか無い場合は、2回目のループでNULL扱いにする
            patRec = null;
        else//patRecがNullでなく、初期化時に2つ以上のPatIDを渡されている場合
            patRec = ddr.findNextPatientRecord(patRec, keys, recFact, ignoreCaseOfPN, matchNoValue);

        return patRec != null;
    }
}