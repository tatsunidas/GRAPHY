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

import java.io.IOException;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.ObjectName;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 15529 $ $Date: 2011-06-01 13:41:30 +0200 (Mi, 01. Jun 2011) $
 * @since 22.06.2004
 *
 */
public abstract class AbstractMediaWriterService extends ServiceMBeanSupport {

    protected SpoolDirDelegate spoolDir = new SpoolDirDelegate(this);

    protected boolean keepSpoolFiles = false;

    protected final MessageListener listener = new MessageListener() {

        public void onMessage(Message msg) {
            ObjectMessage objmsg = (ObjectMessage) msg;
            try {
                AbstractMediaWriterService.this
                        .process((MediaCreationRequest) objmsg.getObject());
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
            }
        }

    };

    public final ObjectName getSpoolDirName() {
        return spoolDir.getSpoolDirName();
    }

    public final void setSpoolDirName(ObjectName spoolDirName) {
        spoolDir.setSpoolDirName(spoolDirName);
    }

    public final boolean isKeepSpoolFiles() {
        return keepSpoolFiles;
    }

    public final void setKeepSpoolFiles(boolean keepSpoolFiles) {
        this.keepSpoolFiles = keepSpoolFiles;
    }
    
    protected void startService() throws Exception {
        JMSDelegate.startListening(serviceName.getKeyProperty("name"), listener);
    }

    protected void stopService() throws Exception {
        JMSDelegate.stopListening(serviceName.getKeyProperty("name"));
    }

    protected void process(MediaCreationRequest rq) {
        boolean cleanup = true;
        try {
            log.info("Start Creating Media for " + rq);
            if (rq.isCanceled()) {
                log.info("" + rq + " was canceled");
                return;
            }
            Dataset attrs = rq.readAttributes(log);
            String status = attrs.getString(Tags.ExecutionStatus);
            if (ExecutionStatus.FAILURE.equals(status)) {
                log.info("" + rq + " already failed");
                return;
            }
            rq.updateStatus(ExecutionStatus.CREATING, ExecutionStatusInfo.NORMAL, log);
            try {
                cleanup = handle(rq, attrs);
            } catch (MediaCreationException e) {
                log.error("Failed to process " + rq, e);
                rq.updateStatus(ExecutionStatus.FAILURE, e.getStatusInfo(), log);
            }
        } catch (IOException e) {
            // error already logged
        } finally {
            if (cleanup && !keepSpoolFiles) rq.cleanFiles(spoolDir);
        }
    }

    /**
     * @param mcrq Media Creation Request
     * @param attrs Attributes of Media Creation Request
     * @return signals, if File Set and ISO image shall/can be deleted after
     *  this method returns: <code>true</code> = delete, <code>false</code> = keep
     * @throws MediaCreationException if the Media Creation fails.
     * @throws IOException if an i/o error accesing the Media Creation Request occurs.
     */
    protected abstract boolean handle(MediaCreationRequest mcrq, Dataset attrs)
            throws MediaCreationException, IOException;
}
