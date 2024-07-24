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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;

import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.log4j.Logger;
import org.dcm4chex.cdw.common.ExecutionStatusInfo;
import org.dcm4chex.cdw.common.MediaCreationException;
import org.dcm4chex.cdw.common.MediaCreationRequest;

/**
 * @author gunterze@gmail.com
 * @version $Revision: 15528 $ $Date: 2011-06-01 13:39:26 +0200 (Mi, 01. Jun 2011) $
 * @since 15.08.2004
 *
 */
class LabelCreator {

    enum FileFormat {
        NO(null),
        PS(MimeConstants.MIME_POSTSCRIPT),
        PDF(MimeConstants.MIME_PDF),
        PNG(MimeConstants.MIME_PNG),
        TIFF(MimeConstants.MIME_TIFF);
        
        final String mimeConstant;

        FileFormat(String mimeConstant) {
            this.mimeConstant = mimeConstant;
        }
    }
    private final FopFactory fopFactory;

    private FileFormat fileFormat = FileFormat.NO;

    private final Logger log = Logger.getLogger(LabelCreator.class);

    public LabelCreator() {
        fopFactory = FopFactory.newInstance();
    }
    
    public final void setFopBaseURL(String configpath) {
        try {
            fopFactory.setBaseURL(new File(configpath + File.separatorChar).toURL().toString());
            log.debug("FOP Base URL " + fopFactory.getBaseURL());
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    public final boolean isActive() {
        return fileFormat != FileFormat.NO;
    }

    public final String getLabelFileFormat() {
        return fileFormat.toString();
    }

    public final void setLabelFileFormat(String format) {
        fileFormat = FileFormat.valueOf(format);
    }

    public float getTargetResolution() {
        return fopFactory.getTargetResolution();
    }

    public void setTargetResolution(float dpi) {
        fopFactory.setTargetResolution(dpi);
    }

    public void createLabel(MediaCreationRequest rq, DicomDirDOM dom)
            throws MediaCreationException {
        if (!isActive()) return;
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(rq
                    .getLabelFile()));
            Fop fop = fopFactory.newFop(fileFormat.mimeConstant, out);
            dom.createLabel(rq, fop.getDefaultHandler());
        } catch (Exception e) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    e);
        } finally {
            if (out != null)
                try {
                    out.close();
                } catch (IOException ignore) {
                }
        }
    }
}