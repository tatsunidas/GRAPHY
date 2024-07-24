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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.ObjectName;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4che.util.Executer;
import org.dcm4chex.cdw.common.ExecutionStatus;
import org.dcm4chex.cdw.common.ExecutionStatusInfo;
import org.dcm4chex.cdw.common.JMSDelegate;
import org.dcm4chex.cdw.common.MediaCreationRequest;
import org.dcm4chex.cdw.common.SpoolDirDelegate;
import org.jboss.system.ServiceMBeanSupport;
import org.jboss.system.server.ServerConfigLocator;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 15528 $ $Date: 2011-06-01 13:39:26 +0200 (Mi, 01. Jun 2011) $
 * @since 25.06.2004
 *
 */
public class MakeIsoImageService extends ServiceMBeanSupport {

    
    private static final String _ISO = ".iso";

    private static final String _SORT = ".sort";
    
    private String executable = "mkisofs";

    private boolean keepSpoolFiles = false;
    
    private int isoLevel = 1;

    private boolean rockRidge = false;

    private boolean joliet = false;

    private boolean udf = false;
    
    private boolean volsetInfoEnabled = false;
    
    private boolean padding = false;    

    private boolean logEnabled = false;

    private final File logFile;

    private boolean sortEnabled = false;

    private File sortFile;
    
    private File configDir;
    
    private String configDirPath;
    
    private SpoolDirDelegate spoolDir = new SpoolDirDelegate(this);

    private final MessageListener listener = new MessageListener() {

        public void onMessage(Message msg) {
            ObjectMessage objmsg = (ObjectMessage) msg;
            try {
                MakeIsoImageService.this.process((MediaCreationRequest) objmsg
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
    
    public final String getConfigDir() {
        return configDirPath;
    }

    public final void setConfigDir(String path) {
        this.configDir = resolve(new File(path));            
        this.configDirPath = path;
        sortFile = new File(configDir, "mkisofs.sort");
    }
      
    public MakeIsoImageService() {
        File homedir = ServerConfigLocator.locate().getServerHomeDir();   
        logFile = new File(homedir, "log" + File.separatorChar + "mkisofs.log");
    }

    public final ObjectName getSpoolDirName() {
        return spoolDir.getSpoolDirName();
    }

    public final void setSpoolDirName(ObjectName spoolDirName) {
        spoolDir.setSpoolDirName(spoolDirName);
    }

    public final String getExecutable() {
        return executable;
    }

    public final void setExecutable(String executable) {
        this.executable = executable;
    }

    public final boolean isKeepSpoolFiles() {
        return keepSpoolFiles;
    }

    public final void setKeepSpoolFiles(boolean keepSpoolFiles) {
        this.keepSpoolFiles = keepSpoolFiles;
    }

    public final boolean isVolsetInfoEnabled() {
        return volsetInfoEnabled;
    }

    public final void setVolsetInfoEnabled(boolean volsetInfoEnabled) {
        this.volsetInfoEnabled = volsetInfoEnabled;
    }

    public final int getIsoLevel() {
        return isoLevel;
    }

    public final void setIsoLevel(int isoLevel) {
        this.isoLevel = isoLevel;
    }

    public final boolean isJoliet() {
        return joliet;
    }

    public final void setJoliet(boolean joliet) {
        this.joliet = joliet;
    }

    public final boolean isUdf() {
        return udf;
    }

    public final void setUdf(boolean udf) {
        this.udf = udf;
    }
    
    public final boolean isPadding() {
        return padding;
    }

    public final void setPadding(boolean padding) {
        this.padding = padding;
    }

    public final boolean isLogEnabled() {
        return logEnabled;
    }

    public final void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public final boolean isRockRidge() {
        return rockRidge;
    }

    public final void setRockRidge(boolean rockRidge) {
        this.rockRidge = rockRidge;
    }

    public final boolean isSortEnabled() {
        return sortEnabled;
    }

    public final void setSortEnabled(boolean sortEnabled) {
        this.sortEnabled = sortEnabled;
    }

    public void makeIsoImage(File srcDir, File isoImageFile, String volId,
            String volsetId, int volsetSeqno, int volsetSize)
            throws IOException {
        File tmpSortFile = null;
        int exitCode;
        try {
            ArrayList cmd = new ArrayList();
            cmd.add(executable);
            cmd.add("-f"); // follow symbolic links
            cmd.add("-iso-level");
            cmd.add(String.valueOf(isoLevel));
            if (rockRidge) cmd.add("-r");
            if (joliet) cmd.add("-J");
            if (udf) cmd.add("-udf");
            if (volId != null && volId.length() != 0) {
                cmd.add("-V");
                cmd.add(volId);
            }
            if (volsetInfoEnabled) {
                if (volsetId.length() != 0) {
                    cmd.add("-volset");
                    cmd.add(volsetId);
                }
                cmd.add("-volset-seqno");
                cmd.add(String.valueOf(volsetSeqno));
                cmd.add("-volset-size");
                cmd.add(String.valueOf(volsetSize));
            }
            if (logEnabled) {
                cmd.add("-log-file");
                cmd.add(logFile.getAbsolutePath());
            } else {
                cmd.add("-q");
            }
            if (sortEnabled) {
                tmpSortFile = makeSortFile(srcDir);
                cmd.add("-sort");
                cmd.add(tmpSortFile.getAbsolutePath());
            }
            cmd.add(padding ? "-pad" : "-no-pad");
            cmd.add("-o");
            cmd.add(isoImageFile.getAbsolutePath());
            cmd.add(srcDir.getAbsolutePath());
            String[] cmdarray = (String[]) cmd.toArray(new String[cmd.size()]);
            exitCode = new Executer(cmdarray).waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage());
        } finally {
            if (tmpSortFile != null) tmpSortFile.delete();
            spoolDir.register(isoImageFile);
        }
        if (exitCode != 0) { throw new IOException("mkisofs " + srcDir
                        + " returns " + exitCode); }
    }

    private File makeSortFile(File srcDir) throws IOException {
        File tmpSortFile = new File(srcDir.getParent(), srcDir.getName()
                + _SORT);
        String prefix = srcDir.getAbsolutePath() + File.separatorChar;
        BufferedReader r = new BufferedReader(new FileReader(sortFile));
        try {
            BufferedWriter w = new BufferedWriter(new FileWriter(tmpSortFile));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    w.write(prefix);
                    w.write(line);
                    w.newLine();
                }
            } finally {
                try {
                    w.close();
                } catch (IOException e) {
                }
            }
        } finally {
            try {
                r.close();
            } catch (IOException e) {
            }
        }
        return tmpSortFile;
    }
    

    protected void startService() throws Exception {
        JMSDelegate.startListening("MakeIsoImage", listener);
    }

    protected void stopService() throws Exception {
        JMSDelegate.stopListening("MakeIsoImage");
    }

    protected void process(MediaCreationRequest rq) {
        boolean cleanup = true;
        try {
            log.info("Start Creating ISO 9660 image for " + rq);
            if (rq.isCanceled()) {
                log.info("" + rq + " was canceled");
                return;
            }
            Dataset attrs;
            try {
                attrs = rq.readAttributes(log);
            } catch (IOException e1) {
                // error already logged
                return;
            }
            String status = attrs.getString(Tags.ExecutionStatus);
            if (ExecutionStatus.FAILURE.equals(status)) {
                log.info("" + rq + " already failed");
                return;
            }
            if (rq.getVolsetSeqno() == 1) {
                attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.PENDING);
                attrs.putCS(Tags.ExecutionStatusInfo,
                        ExecutionStatusInfo.MKISOFS);
                try {
                    rq.writeAttributes(attrs, log);
                } catch (IOException e) {
                    // error already logged
                    return;
                }
            }
            try {
                rq.setIsoImageFile(new File(rq.getFilesetDir().getParent(), rq
                        .getFilesetDir().getName()
                        + _ISO));
                makeIsoImage(rq.getFilesetDir(), rq.getIsoImageFile(), rq
                        .getFilesetID(), rq.getVolsetID(), rq.getVolsetSeqno(),
                        rq.getVolsetSize());
                if (rq.isCanceled()) {
                    log.info("" + rq + " was canceled");
                    return;
                }
                try {
                    attrs = rq.readAttributes(log);
                } catch (IOException e) {
                    // error already logged
                    return;
                }
                status = attrs.getString(Tags.ExecutionStatus);
                if (ExecutionStatus.FAILURE.equals(status)) {
                    log.info("" + rq + " already failed");
                    return;
                }
                if (rq.getVolsetSeqno() == 1) {
                    attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.PENDING);
                    attrs.putCS(Tags.ExecutionStatusInfo,
                            ExecutionStatusInfo.QUEUED);
                    try {
                        rq.writeAttributes(attrs, log);
                    } catch (IOException e) {
                        // error already logged
                        return;
                    }
                }
                log.info("Finished Creating ISO 9660 image for " + rq);
                JMSDelegate.queue(rq.getMediaWriterName(),
                        "Schedule Creating Media for " + rq, log, rq, 0L);
                cleanup = false;
            } catch (Exception e) {
                if (rq.isCanceled()) {
                    log.info("" + rq + " was canceled");
                    return;
                }
                log.error("Failed to create ISO 9660 image for " + rq, e);
                attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.FAILURE);
                attrs.putCS(Tags.ExecutionStatusInfo, ExecutionStatusInfo.PROC_FAILURE);
                try {
                    rq.writeAttributes(attrs, log);
                } catch (IOException e1) {
                    // error already logged
                    return;
                }
            }
        } finally {
            if (cleanup && !keepSpoolFiles) rq.cleanFiles(spoolDir);
        }
    }

}
