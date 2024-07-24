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

package org.dcm4chex.cdw.cdrecord;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import org.dcm4chex.cdw.common.MediaCreationException;
import org.dcm4chex.cdw.common.MediaWriterServiceSupport;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 22.06.2004
 *
 */
public class CDRecordService extends MediaWriterServiceSupport {

	private static final int MIN_GRACE_TIME = 2;

    private static final String TAO = "tao";

    private static final String DAO = "dao";

    private static final String SAO = "sao";

    private static final String RAW = "raw";

    private static final String RAW96R = "raw96r";

    private static final String RAW96P = "raw96p";

    private static final String RAW16 = "raw16";

    private static final String[] WRITE_MODES = { TAO, DAO, SAO, RAW, RAW96R,
            RAW96P, RAW16};

    private static final String DATA = "data";

    private static final String MODE2 = "mode2";

    private static final String XA = "xa";

    private static final String XA1 = "xa1";

    private static final String XA2 = "xa2";

    private static final String XAMIX = "xamix";

    private static final String[] TRACK_TYPES = { DATA, MODE2, XA, XA1, XA2,
            XAMIX};

    private String device = "0,0,0";

    private String writeMode = null;

    private String trackType = DATA;

    private boolean padding = false;

    private int graceTime = MIN_GRACE_TIME;

    public final int getGraceTime() {
        return graceTime;
    }

    public final void setGraceTime(int graceTime) {
        if (graceTime < MIN_GRACE_TIME)
                throw new IllegalArgumentException("graceTime: " + graceTime);
        this.graceTime = graceTime;
    }
    public final boolean isPadding() {
        return padding;
    }

    public final void setPadding(boolean padding) {
        this.padding = padding;
    }

    public final String getTrackType() {
        return trackType;
    }

    public final void setTrackType(String trackType) {
        if (Arrays.asList(TRACK_TYPES).indexOf(trackType) == -1)
                throw new IllegalArgumentException("trackType:" + trackType);
        this.trackType = trackType;
    }

    public final String getWriteMode() {
        return writeMode;
    }

    public final void setWriteMode(String writeMode) {
        if (Arrays.asList(WRITE_MODES).indexOf(writeMode) == -1)
                throw new IllegalArgumentException("writeMode:" + writeMode);
        this.writeMode = writeMode;
    }

    public final String getDevice() {
        return device;
    }

    public final void setDevice(String device) {
        this.device = device;
    }

    public boolean checkDrive() throws MediaCreationException {
        String[] cmdarray = { executable, "dev=" + device, "-checkdrive"};
        return super.check(cmdarray, "Supported modes");
    }

    public boolean checkDisk() throws MediaCreationException {
        String[] cmdarray = { executable, "dev=" + device, "-atip"};
        return super.check(cmdarray, "Manufacturer");
    }

    public boolean hasTOC() throws MediaCreationException {
        String[] cmdarray = { executable, "dev=" + device, "-toc"};
        return super.check(cmdarray, "track");
    }
    
    protected String[] makeBurnCmd(File isoImageFile) {
        ArrayList cmd = new ArrayList();
        cmd.add(executable);
        cmd.add("dev=" + device);
        if (writeSpeed >= 0) cmd.add("speed=" + writeSpeed);
        cmd.add("gracetime=" + graceTime);
        if (multiSession) cmd.add("-multi");
        if (simulate) cmd.add("-dummy");
        // eject for verify on windows
        if (verify ? !mount : eject) cmd.add("-eject");
        if (writeMode != null) cmd.add("-" + writeMode);
        cmd.add("-s");
      	cmd.add(padding ? "-pad" : "-nopad");
        cmd.add("-" + trackType);
        cmd.add(isoImageFile.getAbsolutePath());
        return (String[]) cmd.toArray(new String[cmd.size()]);
    }

    protected String[] makeLoadCmd() {
        return new String[]{ executable, "dev=" + device, "-load"};
    }

    protected String[] makeEjectCmd() {
        return new String[]{ executable, "dev=" + device, "-eject"};
    }
}