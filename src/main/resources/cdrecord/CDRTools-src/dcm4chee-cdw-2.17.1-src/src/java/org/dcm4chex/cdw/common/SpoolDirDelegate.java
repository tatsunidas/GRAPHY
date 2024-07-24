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

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 18290 $ $Date: 2014-05-06 14:27:53 +0200 (Di, 06. Mai 2014) $
 * @since 23.06.2004
 *
 */
public class SpoolDirDelegate {

    private static final String ARCHIVE_HIGHWATER = "ArchiveHighWater";

    private static final String FILESET_HIGHWATER = "FilesetHighWater";

    private static final String REGISTER = "register";

    private static final String DELETE = "delete";

    private static final String COPY = "copy";

    private static final String MOVE = "move";

    private static final String DELETE_INSTANCE_FILE = "deleteInstanceFile";

    private static final String GET_MEDIA_FILESET_ROOT_DIR = "getMediaFilesetRootDir";

    private static final String GET_MEDIA_CREATION_REQUEST_FILE = "getMediaCreationRequestFile";

    private static final String GET_INSTANCE_FILE = "getInstanceFile";

    private static final String GET_EMULATE_REQUEST_FILE = "getEmulateRequestFile";

    private static final String GET_EMULATE_REQUEST_FILES = "getEmulateRequestFiles";

    private static final String GET_LABEL_FILE = "getLabelFile";

    private static final DcmObjectFactory dof = DcmObjectFactory.getInstance();

    private final ServiceMBeanSupport service;

    private ObjectName spoolDirName;

    public SpoolDirDelegate(ServiceMBeanSupport service) {
        this.service = service;
    }

    public final ObjectName getSpoolDirName() {
        return spoolDirName;
    }

    public final void setSpoolDirName(ObjectName spoolDirName) {
        this.spoolDirName = spoolDirName;
    }

    public File getInstanceFile(String iuid) {
        return getSpoolFile(GET_INSTANCE_FILE, iuid);
    }

    public File getMediaCreationRequestFile(String iuid) {
        return getSpoolFile(GET_MEDIA_CREATION_REQUEST_FILE, iuid);
    }

    public File getMediaFilesetRootDir(String iuid) {
        return getSpoolFile(GET_MEDIA_FILESET_ROOT_DIR, iuid);
    }

    private File getSpoolFile(String op, String iuid) {
        try {
            return (File) service.getServer().invoke(spoolDirName,
                    op,
                    new Object[] { iuid},
                    new String[] { String.class.getName()});
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    public File getLabelFile(String iuid, String format) {
        try {
            return (File) service.getServer().invoke(spoolDirName,
                    GET_LABEL_FILE,
                    new Object[] { iuid, format},
                    new String[] { String.class.getName(),
                            String.class.getName()});
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    public File getEmulateRequestFile(String aet,String pattern) {
        try {
            return (File) service.getServer().invoke(spoolDirName,
                    GET_EMULATE_REQUEST_FILE,
                    new Object[] { aet, pattern},
                    new String[] { String.class.getName(),
                            String.class.getName()});
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    public File[] getEmulateRequestFiles(String aet, long lastModifiedBefore) {
        try {
            return (File[]) service.getServer().invoke(spoolDirName,
                    GET_EMULATE_REQUEST_FILES,
                    new Object[] { aet, Long.valueOf(lastModifiedBefore)},
                    new String[] { String.class.getName(),
                                     long.class.getName() });
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    public void deleteRefInstances(Dataset rq) {
        if (Flag.isYES(rq
                .getString(Tags.PreserveCompositeInstancesAfterMediaCreation)))
                return;
        DcmElement refSOPs = rq.get(Tags.RefSOPSeq);
        for (int i = 0, n = refSOPs.countItems(); i < n; ++i) {
            Dataset item = refSOPs.getItem(i);
            deleteInstanceFile(item.getString(Tags.RefSOPInstanceUID));
        }
    }

    public void register(File f) {
        try {
            service.getServer().invoke(spoolDirName,
                    REGISTER,
                    new Object[] { f},
                    new String[] { File.class.getName()});
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    private boolean deleteInstanceFile(String iuid) {
        try {
            Boolean b = (Boolean) service.getServer().invoke(spoolDirName,
                    DELETE_INSTANCE_FILE,
                    new Object[] { iuid},
                    new String[] { String.class.getName()});
            return b.booleanValue();
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    public boolean delete(File f) {
        try {
            Boolean b = (Boolean) service.getServer().invoke(spoolDirName,
                    DELETE,
                    new Object[] { f},
                    new String[] { File.class.getName()});
            return b.booleanValue();
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    private boolean getBooleanAttribute(String attr) {
        try {
            Boolean b = (Boolean) service.getServer()
                    .getAttribute(spoolDirName, attr);
            return b.booleanValue();
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    public boolean isArchiveHighWater() {
        return getBooleanAttribute(ARCHIVE_HIGHWATER);
    }

    public boolean isFilesetHighWater() {
        return getBooleanAttribute(FILESET_HIGHWATER);
    }

    public boolean copy(File src, File dest, byte[] bbuf) {
        try {
            Boolean b = (Boolean) service.getServer().invoke(spoolDirName,
                    COPY,
                    new Object[] { src, dest, bbuf},
                    new String[] { File.class.getName(), File.class.getName(),
                            byte[].class.getName()});
            return b.booleanValue();
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

    public boolean move(File src, File dest) {
        try {
            Boolean b = (Boolean) service.getServer()
                    .invoke(spoolDirName,
                            MOVE,
                            new Object[] { src, dest},
                            new String[] { File.class.getName(),
                                    File.class.getName(),});
            return b.booleanValue();
        } catch (JMException e) {
            throw new ConfigurationException(e);
        }
    }

}