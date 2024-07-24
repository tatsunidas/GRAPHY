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

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.imageio.plugins.DcmMetadata;
import org.dcm4che.media.DirBuilderFactory;
import org.dcm4che.media.DirReader;
import org.dcm4che.media.DirRecord;
import org.dcm4che.media.DirWriter;
import org.dcm4che.util.MD5Utils;
import org.dcm4che.util.UIDGenerator;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.cdw.common.ExecutionStatusInfo;
import org.dcm4chex.cdw.common.Flag;
import org.dcm4chex.cdw.common.MediaCreationException;
import org.dcm4chex.cdw.common.MediaCreationRequest;
import org.dcm4chex.cdw.common.SpoolDirDelegate;
import org.jboss.logging.Logger;

import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGImageEncoder;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 6394 $ $Date: 2008-06-10 13:02:41 +0200 (Di, 10. Jun 2008) $
 * @since 29.06.2004
 *
 */
class FilesetBuilder {

    private static final String EXT_MD5 = ".MD5";

    private static final String MD5_SUMS = "MD5_SUMS";

    private static final String DICOM = "DICOM";

    private static final String IHE_PDI = "IHE_PDI";

    private static final char[] HEX_DIGIT = { '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    
    private static final UIDGenerator uidgen = UIDGenerator.getInstance();

    private static final DcmObjectFactory dof = DcmObjectFactory.getInstance();

    private static final DirBuilderFactory dbf = DirBuilderFactory
            .getInstance();

    private final MediaComposerService service;

    private final SpoolDirDelegate spoolDir;

    private final DirRecordFactory recFact;

    private final MediaCreationRequest rq;

    private final Dataset attrs;

    private final Logger log;

    private final boolean debug;

    private final ImageReader reader;

    private final boolean preserveInstances;

    private final String nonDICOM;

    private final boolean viewer;

    private final boolean web;

    private final boolean md5sums;

    private final boolean icons;

    private final boolean indexFile;
    
    private BufferedImage imageBI;

    private BufferedImage jpegBI;

    private BufferedImage iconBI;

    private byte[] iconPixelData;

    private final FilesetComponent fsRoot = FilesetComponent
            .makeRootFilesetComponent();

    private final Dataset recFilter = dof.newDataset();

    private final byte[] bbuf;

    private static String toHex(int val) {
        char[] ch8 = new char[8];
        for (int i = 8; --i >= 0; val >>= 4)
            ch8[i] = HEX_DIGIT[val & 0xf];

        return String.valueOf(ch8);
    }

    private static String[] makeFileIDs(String pid, String suid, String seruid,
            String iuid) {
        return new String[] { DICOM, toHex(pid.hashCode()),
                toHex(suid.hashCode()), toHex(seruid.hashCode()),
                toHex(iuid.hashCode()),};
    }

    private static final class Pair {

        final DirRecord rec;

        final FilesetComponent comp;

        Pair(DirRecord rec, FilesetComponent comp) {
            this.rec = rec;
            this.comp = comp;
        }
    }

    public FilesetBuilder(MediaComposerService service,
            MediaCreationRequest rq, Dataset attrs) {
        this.service = service;
        this.spoolDir = service.getSpoolDir();
        this.recFact = service.getDirRecordFactory();
        this.log = service.getLog();
        this.debug = log.isDebugEnabled();
        this.reader = service.getImageReader();
        this.rq = rq;
        this.attrs = attrs;
        this.preserveInstances = Flag.isYES(attrs
                .getString(Tags.PreserveCompositeInstancesAfterMediaCreation))
                && !service.isArchiveHighWater();
        this.viewer = Flag.isYES(attrs
                .getString(Tags.IncludeDisplayApplication));
        this.nonDICOM = attrs.getString(Tags.IncludeNonDICOMObjects, "NO");
        this.web = service.includeWeb(nonDICOM);
        this.md5sums = service.includeMd5Sums(nonDICOM);
        this.indexFile = service.indexFile(nonDICOM);
        this.icons = service.createIcons(nonDICOM);
        this.bbuf = new byte[service.getBufferSize()];
    }

    final byte[] getBuffer() {
        return bbuf;
    }
    
    final boolean isWeb() {
        return web;
    }

    final boolean isMd5Sums() {
        return md5sums;
    }

    final boolean isViewer() {
        return viewer;
    }

    final boolean isIndexFile() {
        return indexFile;
    }
    
    public void buildFileset() throws MediaCreationException {
        try {
            File rootDir = mkRootDir(spoolDir, false);
            rq.setFilesetDir(rootDir);
            File readmeFile = new File(rootDir, service
                    .getFileSetDescriptorFile());
            DirWriter dirWriter = dbf.newDirWriter(rq.getDicomDirFile(),
                    rootDir.getName(),
                    rq.getFilesetID(),
                    readmeFile,
                    service.getCharsetOfFileSetDescriptorFile(),
                    null);
            HashMap patRecs = new HashMap();
            HashMap styRecs = new HashMap();
            HashMap serRecs = new HashMap();
            try {
                DcmElement refSOPs = attrs.get(Tags.RefSOPSeq);
                for (int i = 0, n = refSOPs.countItems(); i < n; ++i) {
                    addFile(rootDir,
                            refSOPs.getItem(i),
                            dirWriter,
                            patRecs,
                            styRecs,
                            serRecs);
                }

            } finally {
                try {
                    dirWriter.close();
                } catch (Exception ignore) {
                }
                spoolDir.register(rq.getDicomDirFile());
            }
        } catch (Throwable e) {
            log.error("buildFileset failed:", e);
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        }
    }

    public long sizeOfDicomContent() {
        return fsRoot.size();
    }

    private void addFile(File rootDir, Dataset item, DirWriter dirWriter,
            HashMap patRecs, HashMap styRecs, HashMap serRecs)
            throws IOException, MediaCreationException {
        String iuid = item.getString(Tags.RefSOPInstanceUID);
        String cuid = item.getString(Tags.RefSOPClassUID);
        File src = spoolDir.getInstanceFile(iuid);
        if (debug)
            log.debug("M-READ " + src);
        ImageInputStream in = new FileImageInputStream(src);
        String[] fileIDs;
        try {
            reader.setInput(in);
            DcmMetadata metadata = (DcmMetadata) reader.getStreamMetadata();
            Dataset ds = metadata.getDataset();
            FileMetaInfo fmi = ds.getFileMetaInfo();
            String tsuid = fmi.getTransferSyntaxUID();

            String suid = ds.getString(Tags.StudyInstanceUID);
            String seruid = ds.getString(Tags.SeriesInstanceUID);
            String pid = ds.getString(Tags.PatientID);
            if (pid == null) {
                pid = suid;
                ds.putLO(Tags.PatientID, suid);
                log.info("Missing Patient ID in instance[uid=" + iuid + 
                        "] - use Study Instance UID " + pid + " as Patient ID");
            }

            // ensure TYPE 1 Attributes in DICOMDIR record
            if (!ds.containsValue(Tags.StudyID)) {
                String sid = MessageFormat.format(service.getDefaultStudyID(),
                        Long.valueOf(suid.hashCode() & 0xFFFFFFFFL));
                ds.putSH(Tags.StudyID, sid);
            }
            if (!ds.containsValue(Tags.StudyDate)) {
                ds.putDA(Tags.StudyDate, service.getDefaultStudyDate());
            }
            if (!ds.containsValue(Tags.StudyTime)) {
                ds.putTM(Tags.StudyTime, service.getDefaultStudyTime());
            }
            if (!ds.containsValue(Tags.Modality)) {
                ds.putCS(Tags.Modality, service.getDefaultModality());
            }
            if (!ds.containsValue(Tags.SeriesNumber)) {
                ds.putIS(Tags.SeriesNumber, service.getDefaultSeriesNumber());
            }
            if (!ds.containsValue(Tags.InstanceNumber)) {
                ds.putIS(Tags.InstanceNumber, service.getDefaultInstanceNumber());
            }

            // ensure TYPE 2 Attributes in DICOMDIR record
            if (!ds.contains(Tags.AccessionNumber)) {
                ds.putLO(Tags.AccessionNumber); 
            }
            if (!ds.contains(Tags.StudyDescription)) {
                ds.putLO(Tags.StudyDescription); 
            }
            if (!ds.contains(Tags.PatientName)) {
                ds.putLO(Tags.PatientName); 
            }            

            fileIDs = makeFileIDs(pid, suid, seruid, iuid);
            Pair serRec = (Pair) serRecs.get(seruid);
            if (serRec == null) {
                Pair styRec = (Pair) styRecs.get(suid);
                if (styRec == null) {
                    Pair patRec = (Pair) patRecs.get(pid);
                    if (patRec == null) {
                        patRec = new Pair(dirWriter.add(null,
                                DirRecord.PATIENT,
                                recFact.makeRecord(DirRecord.PATIENT, ds)),
                                FilesetComponent
                                        .makePatientFilesetComponent(ds,
                                                toFilePath(fileIDs, 2)));
                        patRecs.put(pid, patRec);
                        fsRoot.addChild(patRec.comp);
                    }
                    styRec = new Pair(dirWriter.add(patRec.rec,
                            DirRecord.STUDY,
                            recFact.makeRecord(DirRecord.STUDY, ds)),
                            FilesetComponent.makeStudyFilesetComponent(ds,
                                    service.isPutNewestStudyOnFirstMedia(),
                                    toFilePath(fileIDs, 3)));
                    styRecs.put(suid, styRec);
                    patRec.comp.addChild(styRec.comp);
                }
                serRec = new Pair(dirWriter.add(styRec.rec,
                        DirRecord.SERIES,
                        recFact.makeRecord(DirRecord.SERIES, ds)),
                        FilesetComponent.makeSeriesFilesetComponent(ds,
                                toFilePath(fileIDs, 4)));
                serRecs.put(seruid, serRec);
                styRec.comp.addChild(serRec.comp);
            }
            FilesetComponent comp = FilesetComponent
                    .makeInstanceFilesetComponent(ds, toFilePath(fileIDs, 5));
            comp.incSize(src.length());
            serRec.comp.addChild(comp);
            String recType = DirBuilderFactory.getRecordType(cuid);
            Dataset rec = recFact.makeRecord(recType, ds);

            if (DirRecord.IMAGE.equals(recType)) {
                Dataset iconItem = item.getItem(Tags.IconImageSeq);
                if (iconItem == null && icons)
                    try {
                        iconItem = mkIconItem(ds, 
                                !UIDs.ImplicitVRLittleEndian.equals(tsuid) &&
                                !UIDs.ExplicitVRLittleEndian.equals(tsuid));
                    } catch (Exception e) {
                        log.warn("Failed to generate icon from " + src,
                                e);
                    }
                if (iconItem != null)
                        rec.putSQ(Tags.IconImageSeq).addItem(iconItem);
                if (web) {
                     try {
                        fileIDs[0] = IHE_PDI;
                        File jpegDir = new File(rq.getFilesetDir(),
                                StringUtils.toString(fileIDs,
                                        File.separatorChar));
                        fileIDs[0] = DICOM;
                        mkJpegs(jpegDir);
                    } catch (Exception e) {
                        log.warn("Failed to generate jpeg from " + src, e);
                    }
                }
            }
            dirWriter.add(serRec.rec, recType, rec, fileIDs, cuid, iuid, tsuid);
            if (debug)
                service.logMemoryUsage();            
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
            }
        }

        File dest = new File(rootDir, StringUtils.toString(fileIDs,
                File.separatorChar));
        mkParentDir(dest);
        if (preserveInstances) {
            spoolDir.copy(src, dest, bbuf);
            if (md5sums)
                    spoolDir.copy(MD5Utils.makeMD5File(src), MD5Utils
                            .makeMD5File(dest), bbuf);
        } else {
            spoolDir.move(src, dest);
            File md5src = MD5Utils.makeMD5File(src);
            if (md5sums)
                spoolDir.move(md5src, MD5Utils.makeMD5File(dest));
            else
                spoolDir.delete(md5src);
        }
    }

    private String toFilePath(String[] fileIDs, int n) {
        StringBuffer sb = new StringBuffer(fileIDs[0]);
        for (int i = 1; i < n; ++i)
            sb.append(File.separatorChar).append(fileIDs[i]);
        return sb.toString();
    }

    private void mkJpegs(File dir) throws IOException {
        dir.mkdirs();
        int w0 = reader.getWidth(0);
        int h0 = reader.getHeight(0);
        float ratio = reader.getAspectRatio(0);
        int w = Math.min(w0, service.getJpegWidth());
        int h = Math.min(h0, service.getJpegHeight());
        w = (int) Math.min(w, h * ratio);
        h = (int) Math.min(h, w / ratio);
        if (debug)
                log.debug("create jpegs[w=" + w + ",h=" + h + "] from image[w="
                        + w0 + ",h=" + h0 + "]");
        ImageReadParam param = reader.getDefaultReadParam();
        imageBI = checkReusable(imageBI, w0, h0);
        AffineTransformOp scaleOp = null;
        if (w != w0 || h != h0) {
            jpegBI = checkReusable(jpegBI, w, h);
            AffineTransform scale = AffineTransform.getScaleInstance((double) w
                    / w0, (double) h / h0);
            scaleOp = new AffineTransformOp(scale,
                    AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        }
        BufferedImage bi;
        for (int frame = 0, n = reader.getNumImages(false); frame < n; ++frame) {
            param.setDestination(imageBI);
            bi = imageBI = reader.read(frame, param);
            if (scaleOp != null) {
                // workaround for ImagingLib.filter(this, bi, dst) == null 
                // if bi.getSampleModel() instanceof BandedSampleModel 
                if (imageBI.getSampleModel() instanceof BandedSampleModel) {
                    log.debug("convert RGB plane to RGB pixel");
                    BufferedImage newbi = convertBI(imageBI,
                            BufferedImage.TYPE_INT_RGB);
                    imageBI.flush();
                    imageBI = newbi;
                    jpegBI = null;
                }
                bi = jpegBI = scaleOp.filter(imageBI, jpegBI);
                imageBI.flush();
            }
            File dest = new File(dir, "" + (frame + 1) + ".JPG");
            OutputStream out = new BufferedOutputStream(new FileOutputStream(
                    dest));
            try {
                JPEGImageEncoder enc = JPEGCodec.createJPEGEncoder(out);
                enc.encode(bi);
            } finally {
                out.close();
            }
            bi.flush();
        }
    }

    private BufferedImage convertBI(BufferedImage src, int imageType) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(),
                imageType);
        Graphics2D big = dst.createGraphics();
        try {
            big.drawImage(src, 0, 0, null);
        } finally {
            big.dispose();
        }
        return dst;
    }

    private Dataset mkIconItem(Dataset ds, boolean decompress) throws IOException {
        int frame = ds.getInt(Tags.RepresentativeFrameNumber, 1) - 1;
        frame = Math.max(0, frame);
        frame = Math.min(frame, ds.getInt(Tags.NumberOfFrames, 1) - 1);

        int w0 = reader.getWidth(0);
        int h0 = reader.getHeight(0);
        float ratio = reader.getAspectRatio(0);
        int w = Math.min(w0, service.getIconWidth());
        int h = Math.min(h0, service.getIconHeight());
        w = (int) Math.min(w, h * ratio);
        h = (int) Math.min(h, w / ratio);
        int xSubsampling = (w0 - 1) / w + 1;
        int ySubsampling = (h0 - 1) / h + 1;
        w = (w0 - 1) / xSubsampling + 1;
        h = (h0 - 1) / ySubsampling + 1;
        if (debug)
                log.debug("create icon[w=" + w + ",h=" + h + "] from image[w="
                        + w0 + ",h=" + h0 + "]");
        ImageReadParam param = reader.getDefaultReadParam();
        iconBI = checkReusable(iconBI, w, h);
        if (!decompress) {
	        param.setSourceSubsampling(xSubsampling, ySubsampling, 0, 0);
	        param.setDestination(iconBI);
	        iconBI = reader.read(frame, param);
        } else {
	        param.setDestination(checkReusable(imageBI, w0, h0));
            imageBI = reader.read(frame, param);
            AffineTransform scale = AffineTransform.getScaleInstance((double) w
                    / w0, (double) h / h0);
            AffineTransformOp scaleOp = new AffineTransformOp(scale,
                    AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            // workaround for ImagingLib.filter(this, bi, dst) == null 
            // if bi.getSampleModel() instanceof BandedSampleModel 
            if (imageBI.getSampleModel() instanceof BandedSampleModel) {
                log.debug("convert RGB plane to RGB pixel");
                BufferedImage newbi = convertBI(imageBI,
                        BufferedImage.TYPE_INT_RGB);
                imageBI.flush();
                imageBI = newbi;
                iconBI = null;
            }
            iconBI = scaleOp.filter(imageBI, iconBI);
            imageBI.flush();
        }
        if (w != iconBI.getWidth() || h != iconBI.getHeight()) {
            log.warn("created icon with unexpected dimension[w="
                    + iconBI.getWidth() + ",h=" + iconBI.getHeight()
                    + "] instead [w=" + w + ",h=" + h + "] from image[w=" + w0
                    + ",h=" + h0 + "]");
            w = iconBI.getWidth();
            h = iconBI.getHeight();
        }
        String pmi = ds.getString(Tags.PhotometricInterpretation);
        BufferedImage bi = iconBI;
        if ("RGB".equals(pmi)) {
            bi = convertBI(iconBI, BufferedImage.TYPE_BYTE_INDEXED);
            pmi = "PALETTE COLOR";
        }

        /*
         File dest = new File(ds.getString(Tags.SOPInstanceUID) + ".JPG");
         OutputStream out = new BufferedOutputStream(new FileOutputStream(
         dest));
         try {
         JPEGImageEncoder enc = JPEGCodec.createJPEGEncoder(out);
         enc.encode(bi);
         } finally {
         out.close();
         }
         */

        if (iconPixelData == null || iconPixelData.length != w * h)
                iconPixelData = new byte[w * h];
        Dataset iconItem = dof.newDataset();

        if ("PALETTE COLOR".equals(pmi)) {
            IndexColorModel cm = (IndexColorModel) bi.getColorModel();
            int[] lutDesc = { cm.getMapSize(), 0, 8};
            byte[] r = new byte[lutDesc[0]];
            byte[] g = new byte[lutDesc[0]];
            byte[] b = new byte[lutDesc[0]];
            cm.getReds(r);
            cm.getGreens(g);
            cm.getBlues(b);
            iconItem.putUS(Tags.RedPaletteColorLUTDescriptor, lutDesc);
            iconItem.putUS(Tags.GreenPaletteColorLUTDescriptor, lutDesc);
            iconItem.putUS(Tags.BluePaletteColorLUTDescriptor, lutDesc);
            iconItem.putOW(Tags.RedPaletteColorLUTData, ByteBuffer.wrap(r)
                    .order(ByteOrder.LITTLE_ENDIAN));
            iconItem.putOW(Tags.GreenPaletteColorLUTData, ByteBuffer.wrap(g)
                    .order(ByteOrder.LITTLE_ENDIAN));
            iconItem.putOW(Tags.BluePaletteColorLUTData, ByteBuffer.wrap(b)
                    .order(ByteOrder.LITTLE_ENDIAN));

            Raster raster = bi.getRaster();
            for (int y = 0, i = 0; y < h; ++y)
                for (int x = 0; x < w; ++x, ++i)
                    iconPixelData[i] = (byte) raster.getSample(x, y, 0);
        } else {
            pmi = "MONOCHROME2";
            for (int y = 0, i = 0; y < h; ++y)
                for (int x = 0; x < w; ++x, ++i)
                    iconPixelData[i] = (byte) bi.getRGB(x, y);
        }
        iconItem.putCS(Tags.PhotometricInterpretation, pmi);
        iconItem.putUS(Tags.Rows, h);
        iconItem.putUS(Tags.Columns, w);
        iconItem.putUS(Tags.SamplesPerPixel, 1);
        iconItem.putUS(Tags.BitsAllocated, 8);
        iconItem.putUS(Tags.BitsStored, 8);
        iconItem.putUS(Tags.HighBit, 7);
        iconItem.putOB(Tags.PixelData, iconPixelData);
        return iconItem;
    }

    private BufferedImage checkReusable(BufferedImage bi, int w, int h)
            throws IOException {
        if (bi == null || bi.getWidth() != w || bi.getHeight() != h)
                return null;
        ImageTypeSpecifier t1 = reader.getRawImageType(0);
        ImageTypeSpecifier t2 = ImageTypeSpecifier.createFromRenderedImage(bi);
        if (!t1.equals(t2)) return null;
        if (debug) log.debug("Reuse BufferedImage[w=" + w + ",h=" + h + "]");
        return bi;
    }

    private File mkRootDir(SpoolDirDelegate spoolDir, boolean newuid)
            throws IOException {
        String uid = newuid ? null : attrs
                .getString(Tags.StorageMediaFileSetUID);
        if (uid == null) uid = uidgen.createUID();

        File d;
        while ((d = spoolDir.getMediaFilesetRootDir(uid)).exists()) {
            log.warn("Duplicate file set UID:" + uid + " - generate new uid");
            uid = uidgen.createUID();
        }
        if (!d.mkdir()) throw new IOException("Failed to mkdir " + d);
        return d;
    }

    public ArrayList splitMedia(long freeSizeFirst, long freeSizeOther,
            DicomDirDOM dom) throws MediaCreationException, IOException {
        File ddFile = rq.getDicomDirFile();
        ArrayList fsList = new ArrayList();
        split(fsRoot, freeSizeFirst, freeSizeOther, fsList);
        rq.setVolsetSize(fsList.size());
        String fsIDPrefix = getFilesetIDPrefix();
        File oldDDFile = new File(rq.getFilesetDir(), "DICOMDIR.old");
        ddFile.renameTo(oldDDFile);
        DirReader dirReader = dbf.newDirReader(oldDDFile);
        ArrayList rqList = new ArrayList();
        try {
            for (int i = 0, n = fsList.size(); i < n; ++i) {
                FilesetComponent comp = (FilesetComponent) fsList.get(i);
                File rootDir = rq.getFilesetDir();
                MediaCreationRequest newrq = rq;
                if (i > 0) {
                    rootDir = mkRootDir(spoolDir, true);
                    moveComponents(comp.childs(), rootDir);
                    newrq = new MediaCreationRequest(rq);
                    newrq.setFilesetDir(rootDir);
                    newrq.setVolsetSeqno(i + 1);
                    if (dom != null)
                        setVolsetSeqno(comp.childs(), i + 1, dom);
                }
                rqList.add(newrq);
                if (fsIDPrefix != null)
                        newrq.setFilesetID(fsIDPrefix + (i + 1));
                File newReadmeFile = new File(rootDir, service
                        .getFileSetDescriptorFile());
                DirWriter dirWriter = dbf.newDirWriter(newrq.getDicomDirFile(),
                        rootDir.getName(),
                        newrq.getFilesetID(),
                        newReadmeFile,
                        service.getCharsetOfFileSetDescriptorFile(),
                        null);
                try {
                    copyPatientDirRecords(comp.root().childs(),
                            dirWriter,
                            dirReader);
                } finally {
                    try {
                        dirWriter.close();
                    } catch (Exception ignore) {
                    }
                    spoolDir.register(newrq.getDicomDirFile());
                }
            }
        } finally {
            dirReader.close();
            spoolDir.delete(oldDDFile);
        }
        return rqList;
    }

    private void setVolsetSeqno(List comps, int seqNo, DicomDirDOM dom) {
        for (int i = 0, n = comps.size(); i < n; ++i) {
            FilesetComponent comp = (FilesetComponent) comps.get(i);
            switch (comp.level()) {
            case FilesetComponent.PATIENT:
                dom.setPatientSeqNo(comp.id(), seqNo);
                break;
            case FilesetComponent.STUDY:
                dom.setStudySeqNo(comp.parent().id(), comp.id(), seqNo);
                break;
            case FilesetComponent.SERIES:
                dom.setSeriesSeqNo(comp.parent().parent().id(), comp.parent()
                        .id(), comp.id(), seqNo);
                break;
            case FilesetComponent.INSTANCE:
                dom.setInstanceSeqNo(comp.parent().parent().parent().id(),
                        comp.parent().parent().id(),
                        comp.parent().id(),
                        comp.id(),
                        seqNo);
                break;
            }
        }
    }

    private void split(FilesetComponent src, long freeSizeFirst,
            long freeSizeOther, ArrayList fsList) throws MediaCreationException {
        if (debug) log.debug("split " + src);
        List childs = src.childs();
        while (!childs.isEmpty()) {
            FilesetComponent comp = src.takeChilds(
                    fsList.isEmpty() ? freeSizeFirst : freeSizeOther);
            if (comp.isEmpty()) {
                if (src.level() == FilesetComponent.SERIES)
                        throw new MediaCreationException(
                                ExecutionStatusInfo.INST_OVERSIZED, "Instance "
                                        + ((FilesetComponent) childs.get(0)).id()
                                        + " does not fit on media");
                FilesetComponent child = (FilesetComponent) childs.get(0);
                split(child, freeSizeFirst, freeSizeOther, fsList);
                src.removeChild(child);
                continue;
            }
            fsList.add(comp);
            if (debug)
                    log.debug(" into " + comp + " on Media #" + fsList.size());
        }
    }

    private String getFilesetIDPrefix() {
        if (rq.getFilesetID().length() == 0) return null;
        String prefix = rq.getFilesetID() + '_';
        int maxSuffixLen = String.valueOf(rq.getVolsetSize()).length();
        return prefix
                .substring(0, Math.min(prefix.length(), 16 - maxSuffixLen));
    }

    private void moveComponents(List comps, File newRootDir) throws IOException {
        for (int i = 0, n = comps.size(); i < n; ++i) {
            FilesetComponent comp = (FilesetComponent) comps.get(i);
            moveComponent(comp.getFilePath(), newRootDir);
        }
    }

    private void moveComponent(String filepath, File newRootDir) throws IOException {
        File src = new File(rq.getFilesetDir(), filepath);
        if (!src.exists()) return;
        File dest = new File(newRootDir, filepath);
        mkParentDir(dest);
        src.renameTo(dest);
        // purge empty directories
        File dir = src.getParentFile();
        while (dir.delete()) dir = src.getParentFile();
    }

    private boolean mkParentDir(File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent.exists()) return false;
        if (!parent.mkdirs())
                throw new IOException("Failed to mkdirs " + parent);
        return true;
    }

    private void copyPatientDirRecords(List pats, DirWriter dirWriter,
            DirReader dirReader) throws IOException {
        for (int i = 0, n = pats.size(); i < n; ++i) {
            FilesetComponent pat = (FilesetComponent) pats.get(i);
            recFilter.clear();
            recFilter.putLO(Tags.PatientID, pat.id());
            DirRecord srcPatRec = dirReader.getFirstRecordBy(null,
                    recFilter,
                    false);
            DirRecord dstPatRec = dirWriter.add(null,
                    srcPatRec.getType(),
                    srcPatRec.getDataset());
            copyStudyDirRecords(pat.childs(), dirWriter, srcPatRec, dstPatRec);
        }
    }

    private void copyStudyDirRecords(List stys, DirWriter dirWriter,
            DirRecord srcPatRec, DirRecord dstPatRec) throws IOException {
        for (int i = 0, n = stys.size(); i < n; ++i) {
            FilesetComponent sty = (FilesetComponent) stys.get(i);
            recFilter.clear();
            recFilter.putUI(Tags.StudyInstanceUID, sty.id());
            DirRecord srcRec = srcPatRec
                    .getFirstChildBy(null, recFilter, false);
            DirRecord dstRec = dirWriter.add(dstPatRec,
                    srcRec.getType(),
                    srcRec.getDataset());
            copySeriesDirRecords(sty.childs(), dirWriter, srcRec, dstRec);
        }
    }

    private void copySeriesDirRecords(List sers, DirWriter dirWriter,
            DirRecord srcStyRec, DirRecord dstStyRec) throws IOException {
        for (int i = 0, n = sers.size(); i < n; ++i) {
            FilesetComponent ser = (FilesetComponent) sers.get(i);
            recFilter.clear();
            recFilter.putUI(Tags.SeriesInstanceUID, ser.id());
            DirRecord srcRec = srcStyRec
                    .getFirstChildBy(null, recFilter, false);
            DirRecord dstRec = dirWriter.add(dstStyRec,
                    srcRec.getType(),
                    srcRec.getDataset());
            copyInstanceDirRecords(ser.childs(), dirWriter, srcRec, dstRec);
        }
    }

    private void copyInstanceDirRecords(List insts, DirWriter dirWriter,
            DirRecord srcSerRec, DirRecord dstSerRec) throws IOException {
        for (int i = 0, n = insts.size(); i < n; ++i) {
            FilesetComponent inst = (FilesetComponent) insts.get(i);
            recFilter.clear();
            recFilter.putUI(Tags.RefSOPInstanceUIDInFile, inst.id());
            DirRecord srcRec = srcSerRec
                    .getFirstChildBy(null, recFilter, false);
            Dataset ds = srcRec.getDataset();
            DirRecord dstRec = dirWriter.add(dstSerRec,
                    srcRec.getType(),
                    ds,
                    ds.getStrings(Tags.RefFileID),
                    ds.getString(Tags.RefSOPClassUIDInFile),
                    ds.getString(Tags.RefSOPInstanceUIDInFile),
                    ds.getString(Tags.RefSOPTransferSyntaxUIDInFile));
        }
    }

    void createMd5Sums(MediaCreationRequest rq) throws MediaCreationException {
        if (!md5sums) return;
        try {
            char[] cbuf = new char[32];
            MessageDigest digest = MessageDigest.getInstance("MD5");
            File rootDir = rq.getFilesetDir();
            String[] subDirs = rootDir.list();
            File md5sums = new File(rootDir, MD5_SUMS);
            int strip = rootDir.toURI().toString().length();
            log.info("Start Creating MD5 sums for " + rq);
            Writer out = new BufferedWriter(new FileWriter(md5sums));
            try {
                for (int i = 0; i < subDirs.length; i++) {
                    writeMd5Sums(out,
                            new File(rootDir, subDirs[i]),
                            strip,
                            digest,
                            cbuf);
                }
            } finally {
                try {
                    out.close();
                } catch (Exception ignore) {
                }
                spoolDir.register(md5sums);
            }
        } catch (Exception e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        }
        log.info("Finished Creating MD5 sums for " + rq);
    }

    private void writeMd5Sums(Writer out, File fileOrDir, int strip,
            MessageDigest digest, char[] cbuf) throws IOException {
        if (fileOrDir.isDirectory()) {
            String[] files = fileOrDir.list();
            for (int i = 0; i < files.length; i++) {
                String f = files[i];
                writeMd5Sums(out, new File(fileOrDir, f), strip, digest, cbuf);
            }
        } else {
            String fname = fileOrDir.getName();
            if (fname.endsWith(EXT_MD5)) return;
            File md5file = new File(fileOrDir.getParent(), fname + EXT_MD5);
            if (md5file.exists()) {
                Reader in = new FileReader(md5file);
                try {
                    in.read(cbuf);
                } finally {
                    in.close();
                }
                spoolDir.delete(md5file);
            } else {
                MD5Utils.md5sum(fileOrDir, cbuf, digest, bbuf);
            }
            out.write(cbuf);
            out.write(' ');
            out.write(' ');
            String uri = fileOrDir.toURI().toString();
            out.write(uri, strip, uri.length() - strip);
            out.write('\r');
            out.write('\n');
        }

    }

}