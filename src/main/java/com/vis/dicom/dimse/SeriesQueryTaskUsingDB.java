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

class SeriesQueryTaskUsingDB extends StudyQueryTaskUsingDB {

    protected final String[] seriesIUIDs;
    protected Attributes seriesRec;
    private int total = 0;
    private int process = 0;
    private ArrayList<HashMap<String,String>>seriesCandidate = null; 

    public SeriesQueryTaskUsingDB(Association as, PresentationContext pc, Attributes rq, Attributes keys, DcmQRSCP qrscp)
            throws DicomServiceException {
        super(as, pc, rq, keys, qrscp);
        /* if specified by keys, set it. if not, length is 0. */
        seriesIUIDs = StringUtils.maskNull(
                keys.getStrings(Tag.SeriesInstanceUID));
        /* obtain all studies with keys*/
        /* レコードを検索（親クラスで参照しているPatID, StudyIUIDを検索条件に追加） */
        seriesCandidate = db.getAllCandidate4SeriesQuery(patRec,studyRec,keys);
        this.total = seriesCandidate.size();
        wrappedFindNextSeries();
   }

    @Override
    public boolean hasMoreMatches() throws DicomServiceException {
        return seriesRec != null;
    }

    @Override
    public Attributes nextMatch() throws DicomServiceException {
        Attributes ret = new Attributes(patRec.size()
                + studyRec.size()
                + seriesRec.size());
        ret.addAll(patRec);
        ret.addAll(studyRec);
        ret.addAll(seriesRec);
        wrappedFindNextSeries();
        return ret;
    }

    private void wrappedFindNextSeries() throws DicomServiceException {
        try {
            findNextSeries();
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }
    
//    (0008,0060) CS [MR] Modality
//    (0020,000E) UI [1.3.6.1.4.1.14519.5.2.1.3344.2526.2908431394482495694492453381
//    (0020,0011) IS [4] SeriesNumber
    private Attributes constructInfo(HashMap<String, String> seriesInfo)  {
		Attributes attrs = new Attributes(3);
		attrs.setString(Tag.Modality, VR.CS, seriesInfo.get("Modality"));
		attrs.setString(Tag.SeriesInstanceUID, VR.TM, seriesInfo.get("SeriesInstanceUID"));
		attrs.setString(Tag.SeriesNumber, VR.SH, seriesInfo.get("SeriesNo"));
		return attrs;
	}

    protected boolean findNextSeries() throws IOException {
    	/* studyRecがNullなら完了 */
		if (studyRec == null) {
			seriesRec = null;
			return false;
		}
		
		//getAllCandidate4SeriesQuery
		/* keyでヒットした件数すべての検索が完了したらSTUDY検索を終了 */
		if (this.process == this.total) {
			seriesRec = null;
		}else {
			/* レコードを取得 */
			HashMap<String, String> seriesInfo = seriesCandidate.get(process);
			seriesRec = constructInfo(seriesInfo);
			this.process++;
		}
    	/* 親クラスのループを更新。スタディループを初期化する */
    	while (seriesRec == null && super.findNextStudy()) {
    		this.process = 0;
    		/* この時点で、studyRecは新しく更新されている。存在しない場合はNullを返す */
    		seriesCandidate = db.getAllCandidate4SeriesQuery(patRec,studyRec,keys);
    		if (seriesCandidate != null && seriesCandidate.size()>0) {
    			seriesRec = constructInfo(seriesCandidate.get(process));
    		} 
    		this.process++;
    		/* 最後の時点でseriesRecがNullにならなければ、次の実行で最初からの流れになる */
    	}
       return seriesRec != null;
    }
}