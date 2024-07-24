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

package org.dcm4chex.cdw.nerocmd;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.dcm4che.util.Executer;
import org.dcm4chex.cdw.common.ExecutionStatusInfo;
import org.dcm4chex.cdw.common.MediaCreationException;
import org.dcm4chex.cdw.common.MediaWriterServiceSupport;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 9822 $ $Date: 2009-02-17 15:54:07 +0100 (Di, 17. Feb 2009) $
 * @since 22.06.2004
 *
 */
public class NeroCmdService extends MediaWriterServiceSupport {

    private String mediaType;

    private String driveLetter;
    
    private String driveId;

    public final String getDriveLetter() {
        return driveLetter;
    }

    public final void setDriveLetter(String driveLetter) {
        this.driveLetter = driveLetter;
        super.setDriveLetterOrMountDirectory(driveLetter + ':');
    }

    public boolean checkDrive() throws MediaCreationException {
        String[] cmdarray = { executable, "--info", "drive", "--drive", driveId};
        return super.check(cmdarray, "Device");
    }

    public boolean checkDisk() throws MediaCreationException {
        String[] cmdarray = { executable, "--info", "disc", "--drive", driveId};
        return super.check(cmdarray, "writeable");
    }

    public boolean hasTOC() throws MediaCreationException {
        String[] cmdarray = { executable, "--info", "disc", "--drive", driveId};
        return super.check(cmdarray, "not writeable");
    }

    protected String[] makeBurnCmd(File isoImageFile) {
        ArrayList cmd = new ArrayList();
        cmd.add(executable);
        cmd.add("--write");
        cmd.add("--drive");
        cmd.add(driveId);
        if (simulate) cmd.add("--simulate");
        if (verify) cmd.add("--verify");
        if(!mediaType.equals("cd")) cmd.add("--" + mediaType);
        cmd.add("--image");
        cmd.add(isoImageFile.getAbsolutePath());
        if (writeSpeed >= 0) {
	        cmd.add("--speed");
	        cmd.add(String.valueOf(writeSpeed));
        }
        if (!multiSession) cmd.add("--close-session");
        return (String[]) cmd.toArray(new String[cmd.size()]);
    }

    protected String[] makeEjectCmd() {
        return new String[] { executable, "--eject", "--drive", 
                driveId};
    }

    protected String[] makeLoadCmd() {
        return new String[] { executable, "--load", "--drive",
                driveId};
    }

    public final String getMediaType() {
        return mediaType;
    }

    public final void setMediaType(String mediaType) {
        this.mediaType = mediaType.toLowerCase();
    }

    public final String getDriveId() {
        return driveId;
    }

    public final void setDriveId(String driveId) {
        this.driveId = driveId;
    }
}