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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.ObjectName;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4che.util.Executer;
import org.dcm4che.util.MD5Utils;
import org.dcm4chex.cdw.common.ConfigurationException;
import org.dcm4chex.cdw.common.ExecutionStatus;
import org.dcm4chex.cdw.common.ExecutionStatusInfo;
import org.dcm4chex.cdw.common.Flag;
import org.dcm4chex.cdw.common.JMSDelegate;
import org.dcm4chex.cdw.common.MediaCreationException;
import org.dcm4chex.cdw.common.MediaCreationRequest;
import org.dcm4chex.cdw.common.SpoolDirDelegate;
import org.jboss.system.ServiceMBeanSupport;
import org.jboss.system.server.ServerConfig;
import org.jboss.system.server.ServerConfigLocator;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 15528 $ $Date: 2011-06-01 13:39:26 +0200 (Mi, 01. Jun 2011) $
 * @since 25.06.2004
 *
 */
public class MediaComposerService extends ServiceMBeanSupport {

    private static final long MIN_MEDIA_CAPACITY = 1000000L;

    private static final int MIN_BUFFER_SIZE = 512;

    private static final long BLOCK_SIZE = 2048L;

    private SpoolDirDelegate spoolDir = new SpoolDirDelegate(this);

    private DirRecordFactory dirRecordFactory;
    
    private LabelCreator labelCreator;
    
    private ObjectName makeIsoImageServiceName;

    private final File xmlFile;

    private File configDir;
    
    private File mergeDir;

    private File mergeDirViewer;

    private File mergeDirWeb;
    
    private String configDirPath;
    
    private String mergeDirPath;
    
    private String mergeDirViewerPath;
    
    private String mergeDirWebPath;

    private long mediaCapacity = 700 * MD5Utils.MEGA;

    private int bufferSize = 512;

    private String fileSetDescriptorFile;
    
    private String queueName;

    private String charsetOfFileSetDescriptorFile = "ISO_IR 100";

    private String defaultStudyID = "S{0,number,0000000000}";

    private String defaultStudyDate = "19000101";

    private String defaultStudyTime = "00000000";

    private String defaultModality = "UNKOWN";

    private int defaultSeriesNumber = 0;

    private int defaultInstanceNumber = 0;

    private boolean keepSpoolFiles = false;

    //    private boolean createIcon = true;

    private String hyperlinkCase = "UPPER";

    private int iconWidth = 64;

    private int iconHeight = 64;

    private int jpegWidth = 512;

    private int jpegHeight = 512;

    private boolean includeDisplayApplicationOnAllMedia = true;
    
    private boolean putNewestStudyOnFirstMedia = true;

    private boolean makeIsoImage = true;

    private boolean logXml = false;
    
    private Set valuesForIncludeWeb = new HashSet();

    private Set valuesForIncludeMd5Sums = new HashSet();

    private Set valuesForCreateIcons = new HashSet();

    private Set valuesForIndexFile = new HashSet();
    
    private final ImageReader imageReader;

    private final MessageListener listener = new MessageListener() {

        public void onMessage(Message msg) {
            ObjectMessage objmsg = (ObjectMessage) msg;
            try {
                MediaComposerService.this.process((MediaCreationRequest) objmsg
                        .getObject());
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
            }
        }

    };
    
    private File resolve(File dir) {
        if (dir.isAbsolute()) return dir;
        File dataDir = ServerConfigLocator.locate().getServerHomeDir();
        return new File(dataDir, dir.getPath());
    }
    
    private void checkDir(File dir) {
        if (dir.mkdirs()) log.debug("M-WRITE " + dir);
        if (!dir.isDirectory() || !dir.canWrite())
                throw new IllegalArgumentException(dir
                        + " is not a writable directory!");
    }
    
    public final String getConfigDir() {
		return configDirPath;
        }

    public final void setConfigDir(String path) {
            File dir = resolve(new File(path));            
            this.configDir = dir;
            this.configDirPath = path;          
           }
      
    public final String getMergeDirPath() {
    	return mergeDirPath;
    }

    public final void setMergeDirPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.mergeDir = dir;
        this.mergeDirPath = path;
    }
    
    public final String getMergeDirViewerPath() {
      	return mergeDirViewerPath;
    }

    public final void setMergeDirViewerPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.mergeDirViewer = dir;
        this.mergeDirViewerPath = path;
    }
  
    public final String getMergeDirWebPath() {
      	return mergeDirWebPath;
    }

    public final void setMergeDirWebPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.mergeDirWeb = dir;
        this.mergeDirWebPath = path;
    }    
    
    public MediaComposerService() {
        ServerConfig config = ServerConfigLocator.locate();
        labelCreator = new LabelCreator ();
        File homedir = config.getServerHomeDir();
        xmlFile = new File(homedir, "log" + File.separatorChar + "dicomdir.xml");
        Iterator it = ImageIO.getImageReadersByFormatName("DICOM");
        if (!it.hasNext())
                throw new ConfigurationException("DICOM Image Reader not found");
        imageReader = (ImageReader) it.next();
    }

    private void checkExists(File file) {
        if (!file.exists())
                throw new ConfigurationException("missing " + file);
    }

    public final ObjectName getMakeIsoImageServiceName() {
        return makeIsoImageServiceName;
    }
    
    public final void setMakeIsoImageServiceName(
            ObjectName makeIsoImageServiceName) {
        this.makeIsoImageServiceName = makeIsoImageServiceName;
    }
    
    public final String getQueueName() {
        return queueName;
    }

    public final void setQueueName(String queueName) {
        this.queueName = queueName;
    }
    
    public final boolean isIncludeDisplayApplicationOnAllMedia() {
        return includeDisplayApplicationOnAllMedia;
    }

    public final void setIncludeDisplayApplicationOnAllMedia(boolean enable) {
        this.includeDisplayApplicationOnAllMedia = enable;
    }

    public final boolean isPutNewestStudyOnFirstMedia() {
        return putNewestStudyOnFirstMedia;
    }

    public final void setPutNewestStudyOnFirstMedia(boolean enable) {
        this.putNewestStudyOnFirstMedia = enable;
    }
    
    public final String[] getValuesForIncludeMd5Sums() {
        return (String[]) valuesForIncludeMd5Sums
                .toArray(new String[valuesForIncludeMd5Sums.size()]);
    }

    public final void setValuesForIncludeMd5Sums(String[] values) {
        valuesForIncludeMd5Sums.clear();
        valuesForIncludeMd5Sums.addAll(Arrays.asList(values));
    }

    public final String[] getValuesForIncludeWeb() {
        return (String[]) valuesForIncludeWeb
                .toArray(new String[valuesForIncludeWeb.size()]);
    }

    public final void setValuesForIncludeWeb(String[] values) {
        valuesForIncludeWeb.clear();
        valuesForIncludeWeb.addAll(Arrays.asList(values));
    }

    public final String[] getValuesForCreateIcons() {
        return (String[]) valuesForCreateIcons
                .toArray(new String[valuesForCreateIcons.size()]);
    }

    public final void setValuesForCreateIcons(String[] values) {
        valuesForCreateIcons.clear();
        valuesForCreateIcons.addAll(Arrays.asList(values));
    }

    public final String[] getValuesForIndexFile() {
        return (String[]) valuesForIndexFile
                .toArray(new String[valuesForIndexFile.size()]);
    }

    public final void setValuesForIndexFile(String[] values) {
        valuesForIndexFile.clear();
        valuesForIndexFile.addAll(Arrays.asList(values));
    }

    final DirRecordFactory getDirRecordFactory() {
        return dirRecordFactory;
    }
    
    final DirRecordFactory setDirRecordFactory() {
    	
        return new DirRecordFactory(
        		new File(configDir.toString() 
        				+ File.separatorChar 
        				+ "dicomdir-records.xml").toURI().toString()
        				);
    }
        
    final ImageReader getImageReader() {
        return imageReader;
    }

    public final String getMediaCapacity() {
        return MD5Utils.formatSize(mediaCapacity);
    }

    public final void setMediaCapacity(String mediaCapacity) {
        this.mediaCapacity = MD5Utils.parseSize(mediaCapacity,
                MIN_MEDIA_CAPACITY);
    }

    public final int getBufferSize() {
        return bufferSize;
    }

    public final void setBufferSize(int bufferSize) {
        if (bufferSize < MIN_BUFFER_SIZE)
                throw new IllegalArgumentException("bufferSize:" + bufferSize);
        this.bufferSize = bufferSize;
    }

    public final boolean isKeepSpoolFiles() {
        return keepSpoolFiles;
    }

    public final void setKeepSpoolFiles(boolean keepSpoolFiles) {
        this.keepSpoolFiles = keepSpoolFiles;
    }

    public final String getCharsetOfFileSetDescriptorFile() {
        return charsetOfFileSetDescriptorFile;
    }

    public final void setCharsetOfFileSetDescriptorFile(
            String charsetOfFileSetDescriptorFile) {
        this.charsetOfFileSetDescriptorFile = charsetOfFileSetDescriptorFile;
    }

    public final String getFileSetDescriptorFile() {
        return fileSetDescriptorFile == null ? "NO" : fileSetDescriptorFile;
    }

    public final void setFileSetDescriptorFile(String fname) {
        if ("NO".equals(fname))
            this.fileSetDescriptorFile = null;
        else {
            checkCS(fname);
            checkExists(new File(mergeDir, fname));
            this.fileSetDescriptorFile = fname;
        }
    }

    public final String getDefaultStudyID() {
        return defaultStudyID.replace("S{0,number,", "S{");
    }

    public final void setDefaultStudyID(String defaultStudyID) {
        this.defaultStudyID = defaultStudyID.replace("S{", "S{0,number,");
    }

    public final String getDefaultStudyDate() {
        return defaultStudyDate;
    }

    public final void setDefaultStudyDate(String defaultStudyDate) {
        checkDateFormat(defaultStudyDate);
        this.defaultStudyDate = defaultStudyDate;
    }

    private static void checkDateFormat(String s) {
        checkFormat(new SimpleDateFormat("yyyyMMdd"), s);
    }

    private static void checkFormat(SimpleDateFormat format, String s) {
        try {
            format.setLenient(false);
            format.parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(s);
        }
    }

    public final String getDefaultStudyTime() {
        return defaultStudyTime;
    }

    public final void setDefaultStudyTime(String defaultStudyTime) {
        checkTime(defaultStudyTime);
        this.defaultStudyTime = defaultStudyTime;
    }

    private static void checkTime(String s) {
        String pattern = "HHmmss.SSS".substring(0, s.length());
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        checkFormat(format, s);
    }

    public final String getDefaultModality() {
        return defaultModality;
    }

    public final void setDefaultModality(String defaultModality) {
        this.defaultModality = defaultModality;
    }

    public final int getDefaultSeriesNumber() {
        return defaultSeriesNumber;
    }

    public final void setDefaultSeriesNumber(int defaultSeriesNumber) {
        this.defaultSeriesNumber = defaultSeriesNumber;
    }

    public final int getDefaultInstanceNumber() {
        return defaultInstanceNumber;
    }

    public final void setDefaultInstanceNumber(int defaultInstanceNumber) {
        this.defaultInstanceNumber = defaultInstanceNumber;
    }

    private static void checkCS(String s) {
        if (s.length() > 16)
                throw new IllegalArgumentException("Illegal CS:" + s);
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            char c = a[i];
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ' ' || c == '_')) { throw new IllegalArgumentException(
                    "Illegal CS:" + s); }
        }
    }

    public final String getHyperlinkCase() {
        return hyperlinkCase;
    }

    public final void setHyperlinkCase(String s) {
        this.hyperlinkCase = s.equalsIgnoreCase("lower") ? "lower" : "UPPER";
    }

    public final int getIconHeight() {
        return iconHeight;
    }

    public final void setIconHeight(int iconHeight) {
        this.iconHeight = iconHeight;
    }

    public final int getIconWidth() {
        return iconWidth;
    }

    public final void setIconWidth(int iconWidth) {
        this.iconWidth = iconWidth;
    }

    public final int getJpegHeight() {
        return jpegHeight;
    }

    public final void setJpegHeight(int jpegHeight) {
        this.jpegHeight = jpegHeight;
    }

    public final int getJpegWidth() {
        return jpegWidth;
    }

    public final void setJpegWidth(int jpegWidth) {
        this.jpegWidth = jpegWidth;
    }

    public String getLabelFileFormat() {
        return labelCreator.getLabelFileFormat();
    }

    public void setLabelFileFormat(String format) {
        labelCreator.setLabelFileFormat(format);
    }

    public float getLabelResolution() {
        return labelCreator.getTargetResolution();
    }

    public void setLabelResolution(float dpi) {
        labelCreator.setTargetResolution(dpi);
    }

    public final boolean isMakeIsoImage() {
        return makeIsoImage;
    }

    public final void setMakeIsoImage(boolean makeIsoImage) {
        this.makeIsoImage = makeIsoImage;
    }

    public final boolean isLogXml() {
        return logXml;
    }

    public final void setLogXml(boolean logXml) {
        this.logXml = logXml;
    }

    public final ObjectName getSpoolDirName() {
        return spoolDir.getSpoolDirName();
    }

    public final void setSpoolDirName(ObjectName spoolDirName) {
        spoolDir.setSpoolDirName(spoolDirName);
    }

    final SpoolDirDelegate getSpoolDir() {
        return spoolDir;
    }

    protected void startService() throws Exception {
        log.info("Initialize " + DicomDirDOM.class.getName());
        JMSDelegate.startListening(queueName, listener);
    }

    protected void stopService() throws Exception {
        JMSDelegate.stopListening(queueName);
    }

    protected void process(MediaCreationRequest rq) {
    	dirRecordFactory = setDirRecordFactory();
    	labelCreator.setFopBaseURL(configDir.toString());
        boolean cleanup = true;
        Dataset attrs = null;
        try {
            log.info("Start Composing media for " + rq);
            if (rq.isCanceled()) {
                log.info("" + rq + " was canceled");
                return;
            }
            attrs = rq.readAttributes(log);
            attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.PENDING);
            attrs.putCS(Tags.ExecutionStatusInfo, ExecutionStatusInfo.BUILDING);
            rq.writeAttributes(attrs, log);
            try {
                FilesetBuilder builder = new FilesetBuilder(this, rq, attrs);
                log.debug("Building Fileset");
                builder.buildFileset();
                log.debug("Building DicomDir");
                DicomDirDOM dom = null;
                if (isDOMNeeded(builder)) {
	                dom = new DicomDirDOM(this, rq, attrs, configDir.toString());	               
	                dom.insertModalitiesInStudy();
                }
                File fsDir = rq.getFilesetDir();
                spoolDir.copy(mergeDir, fsDir, builder.getBuffer());
                if (builder.isViewer()) {
                    spoolDir.copy(mergeDirViewer, fsDir, builder.getBuffer());
                }
                if (builder.isWeb()) {
                    dom.createWeb(rq);
                    spoolDir.copy(mergeDirWeb, fsDir, builder.getBuffer());
                }
                final long fsSize = sizeOf(fsDir);
                if (fsSize > mediaCapacity) {
                    List rqList = splitMedia(rq, attrs, builder, dom, fsSize);
                    if (logXml) dom.toXML(xmlFile);
                    for (int i = 0, n = rqList.size(); i < n; ++i) {
                        MediaCreationRequest rq1 = (MediaCreationRequest) rqList
                                .get(i);
                        File fsDir1 = rq1.getFilesetDir();
                        if (i > 0) {
                            spoolDir
                                    .copy(mergeDir, fsDir1, builder.getBuffer());
                            if (includeDisplayApplicationOnAllMedia
                                    && builder.isViewer()) {
                                spoolDir.copy(mergeDirViewer, fsDir1, builder
                                        .getBuffer());
                            }
                        }
                        finishFileset(builder, dom, rq1);
                        if (!forward(rq1)) return;
                    }
                } else {
                    if (logXml && dom != null) dom.toXML(xmlFile);
                    finishFileset(builder, dom, rq);
                    if (!forward(rq)) return;
                }
                cleanup = false;
            } catch (MediaCreationException e) {
                if (rq.isCanceled()) {
                    log.info("" + rq + " was canceled");
                    return;
                }
                log.error("Failed to compose media for " + rq, e);
                attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.FAILURE);
                attrs.putCS(Tags.ExecutionStatusInfo, e.getStatusInfo());
                rq.writeAttributes(attrs, log);
            }
        } catch (IOException e) {
            // error already logged
        } finally {
            if (cleanup && !keepSpoolFiles) {
                if (attrs != null) spoolDir.deleteRefInstances(attrs);
                rq.cleanFiles(spoolDir);
            }
        }
    }

    private boolean isDOMNeeded(FilesetBuilder builder) {
        return builder.isIndexFile() || builder.isWeb() || labelCreator.isActive();
    }

    private void finishFileset(FilesetBuilder builder, DicomDirDOM dom, MediaCreationRequest rq) throws MediaCreationException {
        if (builder.isIndexFile())
            dom.createIndex(rq);
        builder.createMd5Sums(rq);
        if (labelCreator.isActive()) {
            File f = spoolDir.getLabelFile(rq.getFilesetUID(), labelCreator.getLabelFileFormat());
            rq.setLabelFile(f);
            labelCreator.createLabel(rq, dom);
        }
    }

    private List splitMedia(MediaCreationRequest rq, Dataset attrs,
            FilesetBuilder builder, DicomDirDOM dom, final long fsSize)
            throws MediaCreationException, IOException {
        if (!Flag.isYES(attrs.getString(Tags.AllowMediaSplitting))) { throw new MediaCreationException(
                ExecutionStatusInfo.SET_OVERSIZED, "File-set size: "
                        + MD5Utils.formatSize(fsSize)
                        + " exceeds Media Capacity: "
                        + MD5Utils.formatSize(mediaCapacity)); }
        final long sizeOfNonDicomContent = fsSize
                - builder.sizeOfDicomContent();
        if (sizeOfNonDicomContent > mediaCapacity) { throw new MediaCreationException(
                ExecutionStatusInfo.SET_OVERSIZED,
                "Size of Non-DICOM Content: "
                        + MD5Utils.formatSize(sizeOfNonDicomContent)
                        + " exceeds Media Capacity: "
                        + MD5Utils.formatSize(mediaCapacity)); }
        long freeSizeFirst = mediaCapacity - sizeOfNonDicomContent;
        long freeSizeOther = mediaCapacity - rq.getDicomDirFile().length();
        if (includeDisplayApplicationOnAllMedia && builder.isViewer())
                freeSizeOther -= sizeOf(mergeDirViewer);
        return builder.splitMedia(freeSizeFirst, freeSizeOther, dom);
    }

    private boolean forward(MediaCreationRequest rq) throws IOException,
            MediaCreationException {
        if (rq.isCanceled()) {
            log.info("" + rq + " was canceled");
            return false;
        }
        Dataset attrs = rq.readAttributes(log);
        String status = attrs.getString(Tags.ExecutionStatus);
        if (ExecutionStatus.FAILURE.equals(status)) {
            log.info("" + rq + " already failed");
            return false;
        }
        if (rq.getVolsetSeqno() == 1) {
            attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.PENDING);
            attrs.putCS(Tags.ExecutionStatusInfo,
                    makeIsoImage ? ExecutionStatusInfo.QUEUED_MKISOFS
                            : ExecutionStatusInfo.QUEUED);
            rq.writeAttributes(attrs, log);
        }
        try {
            log.info("Finished Composing media for " + rq);
            if (makeIsoImage)
                JMSDelegate.queue("MakeIsoImage",
                        "Schedule Creating ISO image for " + rq, log, rq, 0L);
            else
                JMSDelegate.queue(rq.getMediaWriterName(),
                        "Schedule Creating Media for " + rq, log, rq, 0L);
            
        } catch (JMSException e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        }
        return true;
    }

    private long sizeOf(File file) throws MediaCreationException {
        try {
            String[] cmdarray = { getMkisofsExecutable(), "-quiet", "-print-size",
                    file.getAbsolutePath()};
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            Executer ex = new Executer(cmdarray, stdout, null);
            int exit = ex.waitFor();
            String result = stdout.toString();
            if (log.isDebugEnabled())
                    log.debug("mkisofs -print-size: " + result);
            return Long.parseLong(result.trim()) * BLOCK_SIZE;
        } catch (Exception e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        }
    }

    private String getMkisofsExecutable() throws Exception {
        return (String) server.getAttribute(makeIsoImageServiceName,
                "Executable");
    }

    final boolean includeWeb(String value) {
        return valuesForIncludeWeb.contains(value);
    }

    final boolean includeMd5Sums(String value) {
        return valuesForIncludeMd5Sums.contains(value);
    }

    final boolean createIcons(String value) {
        return valuesForCreateIcons.contains(value);
    }

    final boolean indexFile(String value) {
        return valuesForIndexFile.contains(value);
    }

    final boolean isArchiveHighWater() {
        return spoolDir.isArchiveHighWater();
    }

    void logMemoryUsage() {
        Runtime rt = Runtime.getRuntime();
        final long total = rt.totalMemory();
        final long free = rt.freeMemory();
        log.debug("Memory total:" + (total/1000000L)
                + "MB, free:" + (free/1000000L)
                + "MB, used:" + ((total - free)/1000000L)
                + "MB");
    }
}