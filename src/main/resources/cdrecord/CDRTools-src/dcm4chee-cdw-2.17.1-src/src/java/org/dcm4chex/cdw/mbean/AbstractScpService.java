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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.management.ObjectName;

import org.dcm4che.dict.UIDs;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.DcmService;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.server.DcmHandler;
import org.dcm4chex.cdw.common.SpoolDirDelegate;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4282 $ $Date: 2006-05-02 11:35:22 +0200 (Di, 02. Mai 2006) $
 * @since 22.06.2004
 *
 */
public abstract class AbstractScpService extends ServiceMBeanSupport {
        
    private static final String GET_DCM_HANDLER = "dcmHandler";

    protected Map tsuidMap = new LinkedHashMap();

    protected ObjectName dcmServerName;
    
    protected SpoolDirDelegate spoolDir = new SpoolDirDelegate(this);

    protected DcmHandler dcmHandler;

    public final ObjectName getDcmServerName() {
        return dcmServerName;
    }

    public final void setDcmServerName(ObjectName dcmServerName) {
        this.dcmServerName = dcmServerName;
    }

    public final ObjectName getSpoolDirName() {
        return spoolDir.getSpoolDirName();
    }

    public final void setSpoolDirName(ObjectName spoolDirName) {
        spoolDir.setSpoolDirName(spoolDirName);
    }

    final SpoolDirDelegate getSpoolDir() {
        return spoolDir;
    }

    public String getAcceptedTransferSyntax() {
        return toString(tsuidMap);
    }

    public void setAcceptedTransferSyntax(String s) {
        updateAcceptedTransferSyntax(tsuidMap, s);
    }
       
    protected void updateAcceptedTransferSyntax(Map tsuidMap, String newval) {
        Map tmp = parseUIDs(newval);
        if (tsuidMap.keySet().equals(tmp.keySet())) return;
        tsuidMap.clear();
        tsuidMap.putAll(tmp);
        updatePresContextsIfRunning();
    }
    
    protected String toString(Map uids) {
        if ( uids == null || uids.isEmpty() ) return "";
        StringBuffer sb = new StringBuffer();
        Iterator iter = uids.keySet().iterator();
        while ( iter.hasNext() ) {
            sb.append(iter.next()).append("\r\n");
        }
        return sb.toString();
    }
    
    protected static Map parseUIDs(String uids) {
        StringTokenizer st = new StringTokenizer(uids, " \t\r\n;");
        String uid,name;
        Map map = new LinkedHashMap();
        while ( st.hasMoreTokens() ) {
            uid = st.nextToken().trim();
            name = uid;
            
            if (isDigit(uid.charAt(0))) {
                if ( ! UIDs.isValid(uid) ) 
                    throw new IllegalArgumentException("UID "+uid+" isn't a valid UID!");
            } else {
                uid = UIDs.forName( name );
            }
            map.put(name,uid);
        }
        return map;
    }
    
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    protected void updateAcceptedSOPClass(Map cuidMap, String newval, DcmService scp) {
        Map tmp = parseUIDs(newval);
        if (cuidMap.keySet().equals(tmp.keySet())) return;
        removePresContextsIfRunning();
        if (scp != null)
            unbindAll(valuesToStringArray(cuidMap));
        cuidMap.clear();
        cuidMap.putAll(tmp);
        if (scp != null)
            bindAll(valuesToStringArray(cuidMap), scp);
        updatePresContextsIfRunning();
    }
    
    protected static String[] valuesToStringArray(Map tsuid) {
         return (String[]) tsuid.values().toArray(new String[tsuid.size()]);
    }

    protected void bindAll(String[] cuids, DcmService scp) {
        if ( dcmHandler == null ) return; //nothing to do!
        DcmServiceRegistry services = dcmHandler.getDcmServiceRegistry();
        for (int i = 0; i < cuids.length; i++) {
            services.bind(cuids[i], scp);
        }
    }

    protected void unbindAll(String[]  cuids) {
        if ( dcmHandler == null ) return; //nothing to do!
        DcmServiceRegistry services = dcmHandler.getDcmServiceRegistry();
        for (int i = 0; i < cuids.length; i++) {
            services.unbind(cuids[i]);
        }
    }
   
    protected void startService() throws Exception {
        dcmHandler = (DcmHandler) server.invoke(dcmServerName, GET_DCM_HANDLER,
                null, null);
        bindDcmServices();
        updatePresContexts();
    }

    protected void stopService() throws Exception {
        removePresContexts();
        unbindDcmServices();
        dcmHandler = null;
    }

    protected abstract void bindDcmServices();

    protected abstract void unbindDcmServices();

    protected abstract void updatePresContexts();

    protected abstract void removePresContexts();

    protected void bindDcmServices(String[] cuids, DcmService service) {
        DcmServiceRegistry reg = dcmHandler.getDcmServiceRegistry();
        for (int i = 0; i < cuids.length; i++)
            reg.bind(cuids[i], service);
    }

    protected void unbindDcmServices(String[] cuids) {
        DcmServiceRegistry reg = dcmHandler.getDcmServiceRegistry();
        for (int i = 0; i < cuids.length; i++)
            reg.unbind(cuids[i]);
    }

    protected void putPresContexts(String[] asuids, String[] tsuids) {
        AcceptorPolicy policy = dcmHandler.getAcceptorPolicy();
        for (int i = 0; i < asuids.length; i++)
            policy.putPresContext(asuids[i], tsuids);
    }

    protected void updatePresContextsIfRunning() {
        if (getState() == STARTED)
            updatePresContexts();
    }

    protected void removePresContextsIfRunning() {
        if (getState() == STARTED)
            removePresContexts();
    }
}
