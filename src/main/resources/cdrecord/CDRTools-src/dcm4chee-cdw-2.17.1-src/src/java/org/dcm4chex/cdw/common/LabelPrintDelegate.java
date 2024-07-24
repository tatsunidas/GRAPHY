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

import javax.management.JMException;
import javax.management.ObjectName;

import org.jboss.system.ServiceMBeanSupport;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 23.06.2004
 *
 */
public class LabelPrintDelegate {

    private static final String PRINT = "print";

    private static final String IF_NOT_PRINTED = "IF_NOT_PRINTED";

    private boolean printLabel = false;

    private String keepLabelFiles = IF_NOT_PRINTED;

    private final ServiceMBeanSupport service;

    private ObjectName labelPrintName;

    public LabelPrintDelegate(ServiceMBeanSupport service) {
        this.service = service;
    }

    public final ObjectName getLabelPrintName() {
        return labelPrintName;
    }

    public final void setLabelPrintName(ObjectName spoolDirName) {
        this.labelPrintName = spoolDirName;
    }

    public final String getKeepLabelFiles() {
        return keepLabelFiles;
    }

    public final void setKeepLabelFiles(String s) {
        if (!(s.equals(IF_NOT_PRINTED) || Flag.isValid(s)))
                throw new IllegalArgumentException(s);
        this.keepLabelFiles = s;
    }

    public final boolean isPrintLabel() {
        return printLabel;
    }

    public final void setPrintLabel(boolean printLabel) {
        this.printLabel = printLabel;
    }

    public boolean print(MediaCreationRequest mcrq) {
        boolean keepSpoolFile = !Flag.isNO(keepLabelFiles);
        File f = mcrq.getLabelFile();
        try {
	        if (!printLabel) return false;
	        if (f == null) {
	            service.getLog()
	                    .warn("Failed to print label: No Label File created for "
	                            + mcrq);
	            return true;
	        }
            service.getServer().invoke(labelPrintName,
                    PRINT,
                    new Object[] { f},
                    new String[] { File.class.getName()});
            keepSpoolFile = Flag.isYES(keepLabelFiles);
            return true;
        } catch (JMException e) {
            service.getLog().warn("Failed to print label " + f, e);
            return false;
        } finally {
            if (keepSpoolFile)
                mcrq.setKeepLabelFile(true);
        }
    }

}