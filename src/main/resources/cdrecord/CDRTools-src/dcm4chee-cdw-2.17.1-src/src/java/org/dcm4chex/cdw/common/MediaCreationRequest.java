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

package org.dcm4chex.cdw.common;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.FileFormat;
import org.dcm4che.dict.Tags;
import org.jboss.logging.Logger;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 9823 $ $Date: 2009-02-17 16:24:31 +0100 (Di, 17. Feb 2009) $
 * @since 25.06.2004
 *
 */
public class MediaCreationRequest implements Serializable {

    private static final long serialVersionUID = 3340629480873666008L;

    private final File requestFile;
    
    private final String requestAET;

    private final Date timestamp;

    private String mediaWriterName;

    private String priority = Priority.LOW;

    private int remainingCopies = 1;

    private File filesetDir;

    private File isoImageFile;

    private File labelFile;

    private String medium;

    private String filesetID = "";

    private String volsetID = "";

    private int volsetSeqno = 1;

    private int volsetSize = 1;

    private int retries = 0;
    
    private boolean keepLabelFile = false;
    
    private boolean failed = false;

    private boolean done = false;

    public MediaCreationRequest(File requestFile, String requestAET) {
        this.requestFile = requestFile;
        this.requestAET = requestAET;
        this.timestamp = new Date();
    }

    public MediaCreationRequest(MediaCreationRequest other) {
        this.requestFile = other.requestFile;
        this.requestAET = other.requestAET;
        this.timestamp = other.timestamp;
        this.mediaWriterName = other.mediaWriterName;
        this.priority = other.priority;
        this.remainingCopies = other.remainingCopies;
        this.filesetDir = other.filesetDir;
        this.isoImageFile = other.isoImageFile;
        this.labelFile = other.labelFile;
        this.medium = other.medium;
        this.filesetID = other.filesetID;
        this.volsetID = other.volsetID;
        this.volsetSeqno = other.volsetSeqno;
        this.volsetSize = other.volsetSize;
        this.retries = other.retries;
        this.keepLabelFile = other.keepLabelFile;
        this.failed = other.failed;
        this.done = other.done;
    }

    public final File getDicomDirFile() {
        if (filesetDir == null)
                throw new IllegalStateException(
                        "FilesetDir not yet initialized");
        return new File(filesetDir, "DICOMDIR");
    }

    public final File getRequestFile() {
        return requestFile;
    }

    public final String getRequestAET() {
        return requestAET;
    }

    public final String getMediaWriterName() {
        return mediaWriterName;
    }

    public final Date getTimestamp() {
        return timestamp;
    }
    
    public final void setMediaWriterName(String mediaWriterName) {
        this.mediaWriterName = mediaWriterName;
    }

    public final int getRemainingCopies() {
        return remainingCopies;
    }

    public final void setRemainingCopies(int copies) {
        this.remainingCopies = copies;
    }

    public final String getMedium() {
        return medium;
    }

    public final void setMedium(String medium) {
        this.medium = medium;
    }

    public final String getPriority() {
        return priority;
    }

    public final void setPriority(String priority) {
        this.priority = priority;
    }

    public final String getFilesetUID() {
        return filesetDir != null ? filesetDir.getName() : null;
    }

    public final String getFilesetID() {
        return filesetID;
    }

    public final void setFilesetID(String filesetID) {
        this.filesetID = filesetID != null ? filesetID : "";
    }

    public final File getIsoImageFile() {
        return isoImageFile;
    }

    public final void setIsoImageFile(File isoImageFile) {
        this.isoImageFile = isoImageFile;
    }

    public final File getLabelFile() {
        return labelFile;
    }

    public final void setLabelFile(File labelFile) {
        this.labelFile = labelFile;
    }

    public final File getFilesetDir() {
        return filesetDir;
    }

    public final void setFilesetDir(File filesetDir) {
        this.filesetDir = filesetDir;
    }

    public final void setRootDir(File filesetDir) {
        this.filesetDir = filesetDir;
    }

    public final String getVolsetID() {
        return volsetID;
    }

    public final void setVolsetID(String volsetID) {
        this.volsetID = volsetID != null ? volsetID : "";
    }

    public final int getVolsetSeqno() {
        return volsetSeqno;
    }

    public final void setVolsetSeqno(int volsetSeqno) {
        this.volsetSeqno = volsetSeqno;
    }

    public final int getVolsetSize() {
        return volsetSize;
    }

    public final void setVolsetSize(int volsetSize) {
        this.volsetSize = volsetSize;
    }

    public final int getRetries() {
        return retries;
    }

    public final void setRetries(int retries) {
        this.retries = retries;
    }

    public final boolean isKeepLabelFile() {
        return keepLabelFile;
    }
    
    public final void setKeepLabelFile(boolean keepLabelFile) {
        this.keepLabelFile = keepLabelFile;
    }
    
    public final boolean isFailed() {
        return failed;
    }

    public final boolean isDone() {
        return done;
    }

    public final boolean isCanceled() {
        return !requestFile.exists();
    }

    public String toString() {
        return "MCRQ[" + requestFile.getName() + "]-Disk#" + volsetSeqno
                + "/" + volsetSize + "[" + filesetID + "/"
                + (filesetDir == null ? "" : filesetDir.getName()) 
                + "]@" + mediaWriterName;
    }

    public Dataset readAttributes(Logger log) throws IOException {
        if (log.isDebugEnabled()) log.debug("M-READ " + requestFile);
        try {
            return readAttributes();
        } catch (IOException e) {
            log.error("Failed: M-READ " + requestFile, e);
            throw e;
        }
    }

    public Dataset readAttributes() throws IOException {
        Dataset ds = DcmObjectFactory.getInstance().newDataset();
        ds.readFile(requestFile, FileFormat.DICOM_FILE, Tags.PixelData);
        return ds;
    }
    
    public void updateStatus(String status, String info, Logger log)
            throws IOException {
        Dataset attrs = readAttributes(log);
        updateStatus(status, info, log, attrs);
    }

    private void updateStatus(String status, String info, Logger log, Dataset attrs) throws IOException {
        failed = status.equals(ExecutionStatus.FAILURE);
        done = status.equals(ExecutionStatus.DONE);
        if (info.equals(attrs.getString((Tags.ExecutionStatusInfo)))
                && status.equals(attrs.getString((Tags.ExecutionStatus))))
                return;
        attrs.putCS(Tags.ExecutionStatus, status);
        attrs.putCS(Tags.ExecutionStatusInfo, info);
        writeAttributes(attrs, log);
    }

    public void writeAttributes(Dataset attrs, Logger log) throws IOException {
        if (isCanceled()) {
            log.info("" + this + " was canceled");
            return;
        }
        if (log.isDebugEnabled()) log.debug("M-UPDATE " + requestFile);
        try {
            attrs.writeFile(requestFile, null);
        } catch (IOException e) {
            log.error("Failed M-UPDATE " + requestFile);
            throw e;
        }
    }
    
    /**
     * Reports one copy was done. Updates Media Creation Request.
     * 
     * @param attrs Media Creation Request attributes. 
     * @param log service logger
     * @return number of remaining copies to do.
     * @throws IOException if the update of the Media Creation Request fails. 
     */
    public int copyDone(Dataset attrs, Logger log) throws IOException {
	    attrs.putUS(Tags.TotalNumberOfPiecesOfMediaCreated,
	            attrs.getInt(Tags.TotalNumberOfPiecesOfMediaCreated, 0) + 1);
	    if (--remainingCopies <= 0) {
	        DcmElement sq = attrs.get(Tags.RefStorageMediaSeq);
	        if (sq == null) sq = attrs.putSQ(Tags.RefStorageMediaSeq);
	        Dataset item = sq.addNewItem();
	        item.putSH(Tags.StorageMediaFileSetID, filesetID);
	        item.putUI(Tags.StorageMediaFileSetUID, filesetDir.getName());
	        if (volsetSeqno == volsetSize) {
	            attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.DONE);
	            attrs.putCS(Tags.ExecutionStatusInfo,
	                    ExecutionStatusInfo.NORMAL);
                    done = true;
	        }
	        log.info("Finished Writing Media " + this);
	    }
        writeAttributes(attrs, log);
        return remainingCopies;
    }    

    public boolean cleanFiles(SpoolDirDelegate spoolDir) {
        boolean retval = true;
        if (!keepLabelFile && labelFile != null && labelFile.exists())
                retval = spoolDir.delete(labelFile);
        if (isoImageFile != null && isoImageFile.exists())
                retval = spoolDir.delete(isoImageFile) && retval;
        if (filesetDir != null && filesetDir.exists())
                retval = spoolDir.delete(filesetDir) && retval;
        return retval;
    }
}