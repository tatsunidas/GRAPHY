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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObject;
import org.dcm4che.data.DcmValueException;
import org.dcm4che.data.SpecificCharacterSet;
import org.dcm4che.dict.Tags;
import org.dcm4che.media.DirBuilderFactory;
import org.dcm4che.media.DirReader;
import org.dcm4che.media.DirRecord;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.cdw.common.ConfigurationException;
import org.dcm4chex.cdw.common.ExecutionStatusInfo;
import org.dcm4chex.cdw.common.MediaCreationException;
import org.dcm4chex.cdw.common.MediaCreationRequest;
import org.jboss.logging.Logger;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 15528 $ $Date: 2011-06-01 13:39:26 +0200 (Mi, 01. Jun 2011) $
 * @since 12.07.2004
 *
 */
class DicomDirDOM {

    private static final String ROOT_ELM = "dicomdir";

    private static final String DICOMDIR = "DICOMDIR";

    private static final String SEQNO = "seqno";
    
    private static final String IHE_PDI = "IHE_PDI";

    private static final String INDEX_HTM = "INDEX.HTM";

    private static final String ITEM = "item";

    private static final String INST_UID = "(0004,1511)";

    private static final String SERIES_UID = "(0020,000E)";

    private static final String STUDY_UID = "(0020,000D)";

    private static final String PATIENT_ID = "(0010,0020)";

    private static final String MODALITY = "(0008,0060)";

    private static final String MODALITIES_IN_STUDY = "(0008,0061)";

    private static final String ATTR = "attr";

    private static final String TAG = "tag";

    private static final String RECORD = "record";

    private static final String TYPE = "type";

    private static final DirBuilderFactory dbf = DirBuilderFactory
            .getInstance();

    private static final TransformerFactory tf = TransformerFactory
            .newInstance();

    private static DOMImplementation dom;

    private static Templates indexTpl;

    private static Templates webTpl;

    private static Templates labelTpl;

    private final Document doc;

    private final MediaComposerService service;

    private final Logger log;
    
    private final boolean debug;
    
    public DicomDirDOM(MediaComposerService service, MediaCreationRequest rq,
            Dataset attrs, String configpath) throws MediaCreationException {
        this.service = service;
        this.log = service.getLog();
        this.debug = log.isDebugEnabled();
        
        try {
            dom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .getDOMImplementation();            
            indexTpl = tf.newTemplates(new StreamSource(new File(configpath + File.separatorChar + "index.xsl").toURI().toString()));           
            webTpl = tf.newTemplates(new StreamSource(new File(configpath + File.separatorChar + "web.xsl").toURI().toString()));            
            labelTpl = tf.newTemplates(new StreamSource(new File(configpath + File.separatorChar +  "label.xsl").toURI().toString()));            
        } catch (TransformerConfigurationException e) {
            throw new ConfigurationException(e);
        } catch (ParserConfigurationException e) {
            throw new ConfigurationException(e);
        } catch (FactoryConfigurationError e) {
            throw new ConfigurationException(e);
        }

        this.doc = dom.createDocument(null, ROOT_ELM, null);
        if (debug) {
            service.logMemoryUsage();
            log.debug("Creating DicomDirDOM for " + rq);
        }
        DirReader reader = null;
        try {
            reader = dbf.newDirReader(new File(rq.getFilesetDir(), DICOMDIR));
            Element root = doc.getDocumentElement();
            Dataset fsinfo = reader.getFileSetInfo();
            appendAttrs(root, fsinfo.getFileMetaInfo());
            appendAttrs(root, fsinfo);
            appendRecords(root, reader.getFirstRecord());
            appendAttrs(root, attrs.subSet(new int[] { Tags.RefSOPSeq}, true, true));
            if (debug) {
                service.logMemoryUsage();
                log.debug("Created DicomDirDOM for " + rq);
            }
        } catch (Throwable e) {
            log.error("Create DicomDirDOM failed:", e);
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }
   
    public void insertModalitiesInStudy() {
        Element root = doc.getDocumentElement();
        for (Node pat = root.getFirstChild(); pat != null; pat = pat
                .getNextSibling()) {
            if (RECORD.equals(pat.getNodeName())) {
                for (Node sty = pat.getFirstChild(); sty != null; sty = sty
                        .getNextSibling()) {
                    if (RECORD.equals(sty.getNodeName())) {
                        insertModalitiesInStudy(sty);
                    }
                }
            }
        }
    }

    private void insertModalitiesInStudy(Node sty) {
        HashSet set = new HashSet();
        for (Node ser = sty.getFirstChild(); ser != null; ser = ser
                .getNextSibling()) {
            if (RECORD.equals(ser.getNodeName())) {
                set.add(getModality(ser));
            }
        }
        Iterator it = set.iterator();
        if (!it.hasNext()) return;
        String mds = (String) it.next();
        if (it.hasNext()) {
            StringBuffer sb = new StringBuffer(mds);
            while (it.hasNext())
                sb.append('\\').append(it.next());
            mds = sb.toString();
        }
        Element elm = doc.createElement(ATTR);
        elm.setAttribute(TAG, MODALITIES_IN_STUDY);
        elm.appendChild(doc.createTextNode(mds));
        sty.appendChild(elm);
    }

    private String getModality(Node attrs) {
        for (Node attr = attrs.getFirstChild(); attr != null; attr = attr
                .getNextSibling()) {
            if (ATTR.equals(attr.getNodeName())) {
                if (MODALITY.equals(attr.getAttributes().getNamedItem(TAG)
                        .getNodeValue()))
                        return attr.getFirstChild().getNodeValue();
            }
        }
        return "";
    }

    public void setPatientSeqNo(String pid, int seqNo) {
        setSeqNo(findPatient(pid), String.valueOf(seqNo), 1);
    }

    public void setStudySeqNo(String pid, String suid, int seqNo) {
        setSeqNo(findStudy(pid, suid), String.valueOf(seqNo), 2);
    }

    public void setSeriesSeqNo(String pid, String suid, String seruid, int seqNo) {
        setSeqNo(findSeries(pid, suid, seruid), String.valueOf(seqNo), 3);
    }

    public void setInstanceSeqNo(String pid, String suid, String seruid,
            String iuid, int seqNo) {
        setSeqNo(findInstance(pid, suid, seruid, iuid),
                String.valueOf(seqNo),
                4);
    }

    private Element findPatient(String pid) {
        Element root = doc.getDocumentElement();
        return find(root, PATIENT_ID, pid);
    }

    private Element findStudy(String pid, String suid) {
        Element pat = findPatient(pid);
        return find(pat, STUDY_UID, suid);
    }

    private Element findSeries(String pid, String suid, String seruid) {
        Element study = findStudy(pid, suid);
        return find(study, SERIES_UID, seruid);
    }

    private Element findInstance(String pid, String suid, String seruid,
            String iuid) {
        Element series = findSeries(pid, suid, seruid);
        return find(series, INST_UID, iuid);
    }

    private Element find(Node parent, String tag, String val) {
        for (Node child = parent.getFirstChild(); child != null; child = child
                .getNextSibling()) {
            if (RECORD.equals(child.getNodeName())) {
                if (contains(child, tag, val)) return (Element) child;
            }
        }
        return null;
    }

    private boolean contains(Node attrs, String tag, String val) {
        for (Node attr = attrs.getFirstChild(); attr != null; attr = attr
                .getNextSibling()) {
            if (ATTR.equals(attr.getNodeName())) {
                if (tag.equals(attr.getAttributes().getNamedItem(TAG)
                        .getNodeValue()))
                        return val.equals(attr.getFirstChild().getNodeValue());
            }
        }
        return false;
    }

    private void setSeqNo(Element elm, String seqNo, int level) {
        if (level < 4)
            for (Node child = elm.getFirstChild(); child != null; child = child
                    .getNextSibling()) {
                if (RECORD.equals(child.getNodeName()))
                        setSeqNo((Element) child, seqNo, level + 1);
            }
        else
            // elm == instance record
            elm.setAttribute(SEQNO, seqNo);
    }

    private void appendRecords(Element node, DirRecord first)
            throws IOException {
        if (first != null)
            for (DirRecord rec = first; rec != null; rec = rec.getNextSibling())
                appendRecords(appendRecord(node, rec), rec.getFirstChild());
        else
            // node == instance record
            node.setAttribute(SEQNO, "1");
    }

    private Element appendRecord(Element parent, DirRecord rec)
            throws DcmValueException {
        Element elm = doc.createElement(RECORD);
        elm.setAttribute(TYPE, rec.getType());
        appendAttrs(elm, rec.getDataset());
        parent.appendChild(elm);
        return elm;
    }

    private void appendAttrs(Element parent, DcmObject ds)
            throws DcmValueException {
        SpecificCharacterSet cs = ds.getSpecificCharacterSet();
        for (Iterator it = ds.iterator(); it.hasNext();)
            appendAttr(parent, (DcmElement) it.next(), cs);
    }

    private void appendAttr(Element parent, DcmElement dcmElm, SpecificCharacterSet cs)
            throws DcmValueException {
        if (dcmElm.isEmpty() || dcmElm.tag() == Tags.IconImageSeq) return;
        Element elm = doc.createElement(ATTR);
        elm.setAttribute(TAG, Tags.toString(dcmElm.tag()));
        if (dcmElm.hasItems())
            for (int i = 0, n = dcmElm.countItems(); i < n; ++i) {
                Element item = doc.createElement(ITEM);
                appendAttrs(item, dcmElm.getItem(i));
                elm.appendChild(item);
            }
        else {
            String text = StringUtils.toString(dcmElm.getStrings(cs), '/');
            elm.appendChild(doc.createTextNode(text));
        }
        parent.appendChild(elm);
    }

    private void xslt(Templates tpl, Result result, MediaCreationRequest rq)
            throws MediaCreationException {
        try {
            Transformer t = tpl.newTransformer();
            t.setParameter("writer", rq.getMediaWriterName());
            t.setParameter("fsid", rq.getFilesetID());
            t.setParameter("seqno", String.valueOf(rq.getVolsetSeqno()));
            t.setParameter("size", String.valueOf(rq.getVolsetSize()));
            t.setParameter("today", new SimpleDateFormat("yyyyMMdd")
                    .format(rq.getTimestamp()));
            t.setParameter("linkcase", service.getHyperlinkCase());
            t.transform(new DOMSource(doc), result);
        } catch (TransformerConfigurationException e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        } catch (TransformerException e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        }
    }

    public void toXML(File out) throws MediaCreationException {
        try {
            Transformer tr = tf.newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.transform(new DOMSource(doc), new StreamResult(toSystemId(out)));
        } catch (TransformerConfigurationException e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        } catch (TransformerException e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        }
    }

    public void createIndex(MediaCreationRequest rq)
            throws MediaCreationException {
        File file = new File(rq.getFilesetDir(), INDEX_HTM);
        try {
            xslt(indexTpl, new StreamResult(toSystemId(file)), rq);
        } finally {
            service.getSpoolDir().register(file);
        }
    }

    public void createWeb(MediaCreationRequest rq)
            throws MediaCreationException {
        File dir = new File(rq.getFilesetDir(), IHE_PDI);
        dir.mkdir();
        try {
	        log.info("Start Creating HTML content for " + rq);
	        File indexFile = new File(rq.getFilesetDir(), IHE_PDI
	                + File.separatorChar + INDEX_HTM);
            xslt(webTpl, new StreamResult(toSystemId(indexFile)), rq);
	        log.info("Finished Creating HTML content for " + rq);
        } finally {
            service.getSpoolDir().register(dir);
        }
	        
    }

    private String toSystemId(File f) {
        String fpath=f.getAbsolutePath();
        if (File.separatorChar != '/') {
            fpath = fpath.replace(File.separatorChar, '/');
        }
        return (fpath.startsWith("/") ? "file://" : "file:///") + fpath;
    }

    public void createLabel(MediaCreationRequest rq, ContentHandler handler)
            throws MediaCreationException {
        log.info("Start Creating Label for " + rq);
        xslt(labelTpl, new SAXResult(handler), rq);
        log.info("Finished Creating Label for " + rq);
    }
}