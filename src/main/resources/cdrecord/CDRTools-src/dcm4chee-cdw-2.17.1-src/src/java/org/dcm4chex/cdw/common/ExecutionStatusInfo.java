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


/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 23.06.2004
 *
 */
public class ExecutionStatusInfo {

    public static final String NORMAL = "NORMAL";
    
    public static final String CHECK_MCD_OP = "CHECK_MCD_OP";
    
    public static final String CHECK_MCD_SRV = "CHECK_MCD_SRV";
    
    public static final String DIR_PROC_ERR = "DIR_PROC_ERR";
    
    public static final String DUPL_REF_INST = "DUPL_REF_INST";
    
    public static final String INST_AP_CONFLICT = "INST_AP_CONFLICT";
    
    public static final String INST_OVERSIZED = "INST_OVERSIZED";
    
    public static final String INSUFFIC_MEMORY = "INSUFFIC_MEMORY";
    
    public static final String MCD_BUSY = "MCD_BUSY";
    
    public static final String MCD_FAILURE = "MCD_FAILURE";
    
    public static final String NO_INSTANCE = "NO_INSTANCE";
    
    public static final String NOT_SUPPORTED = "NOT_SUPPORTED";
    
    public static final String OUT_OF_SUPPLIES = "OUT_OF_SUPPLIES";
    
    public static final String PROC_FAILURE = "PROC_FAILURE";
    
    public static final String QUEUED = "QUEUED";
    
    public static final String SET_OVERSIZED = "SET_OVERSIZED";
    
    public static final String UNKNOWN = "UNKNOWN";

    // defined by TIANI 
    public static final String QUEUED_BUILD = "QUEUED_BUILD";

    public static final String BUILDING = "BUILDING";

    public static final String QUEUED_MKISOFS = "QUEUED_MKISOFS";

    public static final String MKISOFS = "MKISOFS";
    
    private ExecutionStatusInfo() {}

}
