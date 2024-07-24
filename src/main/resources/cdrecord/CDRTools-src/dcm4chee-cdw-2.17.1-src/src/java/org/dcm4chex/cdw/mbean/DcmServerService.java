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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.dcm4che.net.AETFilter;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.server.DcmHandler;
import org.dcm4che.server.Server;
import org.dcm4che.server.ServerFactory;
import org.jboss.system.ServiceMBeanSupport;


/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 21.06.2004
 *
 */
public class DcmServerService extends ServiceMBeanSupport {

    private static final String ANY = "ANY";
    
    private Properties aetMap = new Properties();
    
    private ServerFactory sf = ServerFactory.getInstance();

    private AssociationFactory af = AssociationFactory.getInstance();

    private AcceptorPolicy policy = af.newAcceptorPolicy();

    private DcmServiceRegistry services = af.newDcmServiceRegistry();

    private DcmHandler handler = sf.newDcmHandler(policy, services);

    private Server dcmsrv = sf.newServer(handler);
    
    private final AETFilter aetFilter = new AETFilter() {

        public boolean accept(String aet) {
            return aetMap.containsKey(aet);
        }
        
    };

    private static String[] nullToAny(String[] aets) {
        return aets == null || aets.length == 0 ? new String[]{ ANY } : aets;
    }
    
    public String lookupMediaWriterName(String aet) {
        return aetMap.getProperty(aet);
    }
    
    public void setCallingAETs(String[] callingAETs) {
        policy.setCallingAETs(anyToNull(callingAETs));
    }

    public String[] getCallingAETs() {
        return nullToAny(policy.getCallingAETs());
    }
    
    private String[] anyToNull(String[] aets) {
        return Arrays.asList(aets).indexOf(ANY) == -1 ? aets : null;
    }

    public void setAETofMediaWriter(String s) {
        Properties p = new Properties();
        try {
            p.load(new ByteArrayInputStream(s.replace(',','\n').getBytes()));
        } catch (IOException e) {
            throw new IllegalArgumentException(s);
        }
        aetMap = p;
    }

    public String getAETofMediaWriter() {
        StringBuffer sb = new StringBuffer();
        Iterator it = aetMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            sb.append(entry.getKey());
            sb.append('=');
            sb.append(entry.getValue());
            sb.append(',');
        }
        return sb.toString();
    }
    
    protected void startService() throws Exception {
        policy.setCalledAETFilter(aetFilter);    
        dcmsrv.start();
    }

    protected void stopService() throws Exception {
        dcmsrv.stop();
    }

    public int getMaxClients() {
        return dcmsrv.getMaxClients();
    }

    public int getNumClients() {
        return dcmsrv.getNumClients();
    }

    public int getPort() {
        return dcmsrv.getPort();
    }

    public void setMaxClients(int max) {
        dcmsrv.setMaxClients(max);
    }

    public void setPort(int port) throws Exception {
        if (getPort() == port) return;
        dcmsrv.setPort(port);
        if (getState() == STARTED) {
            stop();
            Thread.sleep(5000L);
            start();
        }
    }

    public int getDimseTimeout() {
        return handler.getDimseTimeout();
    }

    public int getRqTimeout() {
        return handler.getRqTimeout();
    }

    public int getSoCloseDelay() {
        return handler.getSoCloseDelay();
    }

    public boolean isPackPDVs() {
        return handler.isPackPDVs();
    }

    public void setDimseTimeout(int dimseTimeout) {
        handler.setDimseTimeout(dimseTimeout);
    }

    public void setPackPDVs(boolean packPDVs) {
        handler.setPackPDVs(packPDVs);
    }

    public void setRqTimeout(int timeout) {
        handler.setRqTimeout(timeout);
    }

    public void setSoCloseDelay(int soCloseDelay) {
        handler.setSoCloseDelay(soCloseDelay);
    }

    public final DcmHandler dcmHandler() {
        return handler;
    }

    public int getMaxPDULength() {
        return policy.getMaxPDULength();
    }

    public void setMaxPDULength(int maxLength) {
        policy.setMaxPDULength(maxLength);
    }
    
}
