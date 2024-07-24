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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.dcm4che.util.MD5Utils;
import org.jboss.system.ServiceMBeanSupport;
import org.jboss.system.server.ServerConfigLocator;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 18290 $ $Date: 2014-05-06 14:27:53 +0200 (Di, 06. Mai 2014) $
 * @since 22.06.2004
 *
 */
public class SpoolDirService extends ServiceMBeanSupport 
        implements NotificationListener {

    static final long MIN_HWM = 10000000L;

    static final long MS_PER_MINUTE = 60000L;

    static final long MS_PER_HOUR = MS_PER_MINUTE * 60;

    static final long MS_PER_DAY = MS_PER_HOUR * 24;

    private String archiveDirPath;

    private String emulateRequestDirPath;

    private String filesetDirPath;

    private String requestDirPath;

    private String labelDirPath;

    private File archiveDir;

    private File emulateRequestDir;

    private File filesetDir;

    private File requestDir;

    private File labelDir;

    private long archiveDiskUsage = 0L;

    private long archiveHighWaterMark = MIN_HWM;

    private long aduRefreshTime;

    private long aduRefreshInterval = MS_PER_HOUR;

    private long filesetDiskUsage = 0L;

    private long filesetHighWaterMark = MIN_HWM;

    private long fsduRefreshTime;

    private long fsduRefreshInterval = MS_PER_HOUR;

    private long purgeLabelDirAfter = MS_PER_DAY;

    private long purgeMediaCreationRequestsAfter = MS_PER_DAY;

    private long purgeArchiveDirAfter = MS_PER_DAY;

    private long purgeFilesetDirAfter = MS_PER_HOUR;

    private int numberOfArchiveBuckets = 37;

    private long purgeInterval = MS_PER_MINUTE;

    private Integer schedulerID;

    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }
    
    public final String getArchiveDirPath() {
        return archiveDirPath;
    }

    public void setArchiveDirPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.archiveDirPath = path;
        this.archiveDir = dir;
        aduRefreshTime = 0L;
    }

    public final String getEmulateRequestDirPath() {
        return emulateRequestDirPath;
    }

    public void setEmulateRequestDirPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.emulateRequestDirPath = path;
        this.emulateRequestDir = dir;
    }

    private File resolve(File dir) {
        if (dir.isAbsolute()) return dir;
        File dataDir = ServerConfigLocator.locate().getServerDataDir();
        return new File(dataDir, dir.getPath());
    }

    private void checkDir(File dir) {
        if (dir.mkdirs()) log.debug("M-WRITE " + dir);
        if (!dir.isDirectory() || !dir.canWrite())
                throw new IllegalArgumentException(dir
                        + " is not a writable directory!");
    }

    public final String getFilesetDirPath() {
        return filesetDirPath;
    }

    public void setFilesetDirPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.filesetDirPath = path;
        this.filesetDir = dir;
        fsduRefreshTime = 0L;
    }

    public final String getRequestDirPath() {
        return requestDirPath;
    }

    public void setRequestDirPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.requestDirPath = path;
        this.requestDir = dir;
    }

    public final String getLabelDirPath() {
        return labelDirPath;
    }

    public final void setLabelDirPath(String path) {
        File dir = resolve(new File(path));
        checkDir(dir);
        this.labelDirPath = path;
        this.labelDir = dir;
    }

    public final boolean isArchiveHighWater() {
        return archiveDiskUsage > archiveHighWaterMark;
    }

    public final String getArchiveHighWaterMark() {
        return MD5Utils.formatSize(archiveHighWaterMark);
    }

    public final void setArchiveHighWaterMark(String str) {
        this.archiveHighWaterMark = MD5Utils.parseSize(str, MIN_HWM);
    }

    public final String getArchiveDiskUsage() {
        return MD5Utils.formatSize(archiveDiskUsage);
    }

    public final boolean isFilesetHighWater() {
        return filesetDiskUsage > filesetHighWaterMark;
    }

    public final String getFilesetHighWaterMark() {
        return MD5Utils.formatSize(filesetHighWaterMark);
    }

    public final void setFilesetHighWaterMark(String str) {
        this.filesetHighWaterMark = MD5Utils.parseSize(str, MIN_HWM);
    }

    public final String getFilesetDiskUsage() {
        return MD5Utils.formatSize(filesetDiskUsage);
    }

    public String refreshArchiveDiskUsage() {
        log.info("Start Calculating Archive Disk Usage");
        archiveDiskUsage = 0L;
        register(archiveDir);
        log.info("Finished Calculating Archive Disk Usage: " + getArchiveDiskUsage());
        aduRefreshTime = System.currentTimeMillis();
        return getArchiveDiskUsage();        
    }

    public String refreshFilesetDiskUsage() {
        log.info("Start Calculating Fileset Disk Usage");
        filesetDiskUsage = 0L;
        register(filesetDir);
        log.info("Finished Calculating Fileset Disk Usage: " + getFilesetDiskUsage());
        fsduRefreshTime = System.currentTimeMillis();
        return getFilesetDiskUsage();
    }

    public void register(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            String[] ss = f.list();
            for (int i = 0; i < ss.length; i++)
                register(new File(f, ss[i]));
        } else {
            final String fpath = f.getPath();
            if (fpath.startsWith(archiveDir.getPath()))
                archiveDiskUsage += f.length();
            else if (fpath.startsWith(filesetDir.getPath()))
                    filesetDiskUsage += f.length();
        }
    }

    public final int getNumberOfArchiveBuckets() {
        return numberOfArchiveBuckets;
    }

    public final void setNumberOfArchiveBuckets(int numberOfArchiveBuckets) {
        if (numberOfArchiveBuckets < 1 || numberOfArchiveBuckets > 1000)
                throw new IllegalArgumentException("numberOfArchiveBuckets:"
                        + numberOfArchiveBuckets + " is not between 1 and 1000");
        this.numberOfArchiveBuckets = numberOfArchiveBuckets;
    }

    public final String getArchiveDiskUsageRefreshInterval() {
        return timeAsString(aduRefreshInterval);
    }

    public final void setArchiveDiskUsageRefreshInterval(String refreshInterval) {
        this.aduRefreshInterval = timeFromString(refreshInterval);
    }

    public final String getFilesetDiskUsageRefreshInterval() {
        return timeAsString(fsduRefreshInterval);
    }

    public final void setFilesetDiskUsageRefreshInterval(String refreshInterval) {
        this.fsduRefreshInterval = timeFromString(refreshInterval);
    }

    public final String getPurgeArchiveDirAfter() {
        return timeAsString(purgeArchiveDirAfter);
    }

    public final void setPurgeArchiveDirAfter(String s) {
        this.purgeArchiveDirAfter = timeFromString(s);
    }

    public final String getPurgeMediaCreationRequestsAfter() {
        return timeAsString(purgeMediaCreationRequestsAfter);
    }

    public final void setPurgeMediaCreationRequestsAfter(String s) {
        this.purgeMediaCreationRequestsAfter = timeFromString(s);
    }

    public final String getPurgeFilesetDirAfter() {
        return timeAsString(purgeFilesetDirAfter);
    }

    public final void setPurgeFilesetDirAfter(String s) {
        this.purgeFilesetDirAfter = timeFromString(s);
    }

    public final String getPurgeLabelDirAfter() {
        return timeAsString(purgeLabelDirAfter);
    }

    public final void setPurgeLabelDirAfter(String s) {
        this.purgeLabelDirAfter = timeFromString(s);
    }

    public final String getPurgeInterval() {
        return timeAsString(purgeInterval);
    }

    public void setPurgeInterval(String interval) throws Exception {
        this.purgeInterval = timeFromString(interval);
        if (getState() == STARTED) {
            scheduler.stopScheduler("PurgeSpoolDir", schedulerID, this);
            schedulerID = scheduler.startScheduler("PurgeSpoolDir",
                        purgeInterval, this);
        }
    }

    protected void startService() throws Exception {
        schedulerID = scheduler.startScheduler("PurgeSpoolDir",
                purgeInterval, this);
    }

    protected void stopService() throws Exception {
        scheduler.stopScheduler("PurgeSpoolDir", schedulerID, this);
    }
    
    public File getInstanceFile(String iuid) {
        final int i = (iuid.hashCode() & 0x7FFFFFFF) % numberOfArchiveBuckets;
        File bucket = new File(archiveDir, String.valueOf(i));
        if (bucket.mkdirs()) log.debug("Success: M-WRITE " + bucket);
        return new File(bucket, iuid);
    }

    public File getMediaCreationRequestFile(String iuid) {
        return new File(requestDir, iuid);
    }

    public File getLabelFile(String iuid, String ext) {
        return new File(labelDir, iuid + '.' + ext);
    }

    public File getMediaFilesetRootDir(String iuid) {
        return new File(filesetDir, iuid);
    }

    public File getEmulateRequestFile(String aet,String pattern) {
        try {
            return new File(new File(emulateRequestDir, aet), 
                    URLEncoder.encode(
                            pattern,
                            "US-ASCII"));
        } catch (UnsupportedEncodingException e) {
            // should never happen
            throw new RuntimeException(e);
        }
    }

    public File[] getEmulateRequestFiles(String aet,
            final long lastModifiedBefore) {
        File dir = new File(emulateRequestDir, aet);
        return maskNull(dir.listFiles(new FileFilter() {

            public boolean accept(File f) {
                return f.lastModified() < lastModifiedBefore;
            }}));
    }

    private static File[] NO_FILES = {};
    private static File[] maskNull(File[] files) {
        return files == null ? NO_FILES : files;
    }

    public void purge() {
        purgeExpiredMediaCreationRequests();
        purgeArchiveDir();
        purgeFilesetDir();
        purgeLabelDir();
        final long now = System.currentTimeMillis();
        if (now > aduRefreshTime + aduRefreshInterval)
                refreshArchiveDiskUsage();
        if (now > fsduRefreshTime + fsduRefreshInterval)
                refreshFilesetDiskUsage();
    }

    public void purgeExpiredMediaCreationRequests() {
        if (purgeArchiveDirAfter == 0) return;
        deleteFilesModifiedBefore(requestDir, System.currentTimeMillis()
                - purgeMediaCreationRequestsAfter);
    }

    public void purgeLabelDir() {
        if (purgeLabelDirAfter == 0) return;
        deleteFilesModifiedBefore(labelDir, System.currentTimeMillis()
                - purgeLabelDirAfter);
    }

    public void purgeArchiveDir() {
        if (purgeArchiveDirAfter == 0) return;
        String[] ss = archiveDir.list();
        if (ss == null) return;
        final long modifiedBefore = System.currentTimeMillis()
                - purgeArchiveDirAfter;
        for (int i = 0; i < ss.length; i++) {
            deleteFilesModifiedBefore(new File(archiveDir, ss[i]),
                    modifiedBefore);
        }
    }

    public void purgeFilesetDir() {
        if (purgeArchiveDirAfter == 0) return;
        deleteFilesModifiedBefore(filesetDir, System.currentTimeMillis()
                - purgeFilesetDirAfter);
    }

    private void deleteFilesModifiedBefore(File subdir, long modifiedBefore) {
        String[] ss = subdir.list();
        if (ss == null) return;
        for (int i = 0, n = ss.length; i < n; i++) {
            File f = new File(subdir, ss[i]);
            if (f.lastModified() <= modifiedBefore) {
                delete(f);
            }
        }
    }

    public boolean deleteInstanceFile(String iuid) {
        return delete(getInstanceFile(iuid));
    }

    public boolean delete(File f) {
        if (!f.exists()) return false;
        long length = 0L;
        if (f.isDirectory()) {
            String[] ss = f.list();
            for (int i = 0; i < ss.length; i++)
                delete(new File(f, ss[i]));
        } else {
            length = f.length();
        }
        log.debug("Success: M-DELETE " + f);
        boolean success = f.delete();
        if (success) {
            unregister(f, length);
        } else
            log.warn("Failed: M-DELETE " + f);
        return success;
    }

    private void unregister(File f, long length) {
        String fpath = f.getPath();
        if (fpath.startsWith(archiveDir.getPath())) {
            if ((archiveDiskUsage -= length) < 0L) {
                log.info("Correct negative ArchiveDiskUsage: "
                        + archiveDiskUsage);
                archiveDiskUsage = 0L;
            }
        } else if (fpath.startsWith(filesetDir.getPath())) {
            if ((filesetDiskUsage -= length) < 0L) {
                log.info("Correct negative FilesetDiskUsage: "
                        + filesetDiskUsage);
                filesetDiskUsage = 0L;
            }
        }
    }

    public boolean copy(File src, File dest, byte[] b) {
        if (src.isDirectory()) {
            String[] ss = src.list();
            for (int i = 0; i < ss.length; i++)
                if (!copy(new File(src, ss[i]), new File(dest, ss[i]), b))
                        return false;
            return true;
        }
        delete(dest);
        dest.getParentFile().mkdirs();
        try {
            FileInputStream fis = new FileInputStream(src);
            try {
                FileOutputStream fos = new FileOutputStream(dest);
                try {
                    int read;
                    while ((read = fis.read(b)) != -1)
                        fos.write(b, 0, read);
                } finally {
                    try {
                        fos.close();
                    } catch (IOException ignore) {
                    }
                    register(dest);
                }
            } finally {
                fis.close();
            }
            if (log.isDebugEnabled())
                    log.debug("Success: M-COPY " + src + " -> " + dest);
            return true;
        } catch (IOException e) {
            log.error("Failed: M-COPY " + src + " -> " + dest, e);
            return false;
        }
    }

    public boolean move(File src, File dest) {
        if (!src.exists()) return false;
        if (src.isDirectory()) {
            String[] ss = src.list();
            for (int i = 0; i < ss.length; i++)
                if (!move(new File(src, ss[i]), new File(dest, ss[i])))
                        return false;
            return true;
        }
        delete(dest);
        dest.getParentFile().mkdirs();
        if (src.renameTo(dest)) {
            unregister(src, dest.length());
            register(dest);
            if (log.isDebugEnabled())
                    log.debug("Success: M-MOVE " + src + " -> " + dest);
            return true;
        }
        log.error("Failed: M-MOVE " + src + " -> " + dest);
        return false;
    }

    static String timeAsString(long ms) {
        if (ms == 0) return "0";
        if (ms % MS_PER_DAY == 0) return "" + (ms / MS_PER_DAY) + 'd';
        if (ms % MS_PER_HOUR == 0) return "" + (ms / MS_PER_HOUR) + 'h';
        if (ms % MS_PER_MINUTE == 0) return "" + (ms / MS_PER_MINUTE) + 'm';
        if (ms % 1000 == 0) return "" + (ms / 1000) + 's';
        return "" + ms + "ms";
    }

    static long timeFromString(String str) {
        String s = str.trim().toLowerCase();
        try {
            if (s.endsWith("d"))
                    return Long.parseLong(s.substring(0, s.length() - 1))
                            * MS_PER_DAY;
            if (s.endsWith("h"))
                    return Long.parseLong(s.substring(0, s.length() - 1))
                            * MS_PER_HOUR;
            if (s.endsWith("m"))
                    return Long.parseLong(s.substring(0, s.length() - 1))
                            * MS_PER_MINUTE;
            if (s.endsWith("s"))
                    return Long.parseLong(s.substring(0, s.length() - 1)) * 1000;
            if (s.endsWith("ms"))
                    return Long.parseLong(s.substring(0, s.length() - 2));
            if (Long.parseLong(s) == 0L) return 0L;
        } catch (NumberFormatException e) {
        }
        throw new IllegalArgumentException(str);
    }

    public void handleNotification(Notification notif, Object handback) {
        purge();        
    }
}