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
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
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

package org.dcm4chex.cdw.mbean;

import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.SAXParserFactory;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.media.DirRecord;
import org.dcm4che.srom.Content;
import org.dcm4chex.cdw.common.ConfigurationException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 18573 $ $Date: 2016-04-27 13:48:52 +0200 (Mi, 27. Apr 2016) $
 * @since 05.07.2004
 */
class DirRecordFactory {

    private static final DcmObjectFactory dof = DcmObjectFactory.getInstance();

    private HashMap filterForType = new HashMap();

    private String privateRecordUID;

    private class MyHandler extends DefaultHandler {

        private static final String TYPE = "type";

        private static final String PRIVATE_RECORD_UID = "privateRecordUID";

        private static final String RECORD = "record";

        private static final String TAG = "tag";

        private static final String ATTR = "attr";

        private String type;

        private ArrayList attrs = new ArrayList();

        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            if (qName.equals(ATTR)) {
                attrs.add(attributes.getValue(TAG));
            } else if (qName.equals(RECORD)) {
                type = attributes.getValue(TYPE);
                if (type.equals(DirRecord.PRIVATE)) {
                    privateRecordUID = attributes.getValue(PRIVATE_RECORD_UID);
                }
            }
        }

        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (qName.equals(RECORD)) {
                int[] filter = new int[attrs.size()];
                for (int i = 0; i < filter.length; i++)
                    filter[i] = Tags.valueOf((String) attrs.get(i));
                filterForType.put(type, filter);
                attrs.clear();
            }
        }

    }

    public DirRecordFactory(String uri) throws ConfigurationException {
        try {
            SAXParserFactory.newInstance().newSAXParser().parse(uri,
                    new MyHandler());
        } catch (Exception e) {
            throw new ConfigurationException(
                    "Failed to load record filter from " + uri, e);
        }
    }

    public Dataset makeRecord(String type, Dataset obj) {
        int[] filter = (int[]) filterForType.get(type);
        if (filter == null && type != "SEGMENTATION") throw new IllegalArgumentException("type: " + type);
        Dataset keys = dof.newDataset();
        keys.putAll(obj.subSet(filter));
        DcmElement srcSq = obj.get(Tags.ContentSeq);
        if (srcSq != null) {
            DcmElement dstSq = keys.putSQ(Tags.ContentSeq);
            for (int i = 0, n = srcSq.countItems(); i < n; ++i) {
                Dataset item = srcSq.getItem(i);
                if (Content.RelationType.HAS_CONCEPT_MOD.equals(item
                        .getString(Tags.RelationshipType))) {
                    dstSq.addItem(item);
                }
            }
            if (dstSq.isEmpty()) keys.remove(Tags.ContentSeq);
        }
        if (type.equals(DirRecord.PRIVATE)) {
            keys.putUI(Tags.PrivateRecordUID, privateRecordUID);
        }
        return keys;
    }
}
