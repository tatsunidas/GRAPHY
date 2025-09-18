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
 *
 * The Initial Developer of the Original Code is
 * Raster Images
 * Portions created by the Initial Developer are Copyright (C) 2014
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Babu Hussain A
 * Devishree V
 * Meer Asgar Hussain B
 * Prakash J
 * Suresh V
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
package com.vis.dicom.dimse;

import java.util.ArrayList;
import com.vis.core.log.Log;

/**
 *@author tatsunidas
 */
public class EchoImpl{
	
	private Long delay;//msec

	public EchoImpl(Long delay4Wait) {
		delay = delay4Wait;
	}

	/**
	 * 
	 * @param appName
	 * @param appHost
	 * @param localPort
	 * @param remoteAET: destination target AE title
	 * @param remoteHost
	 * @param remotePort
	 * @return
	 */
	public boolean echo(String appName, String appHost, String localPort, String remoteAET, String remoteHost, String remotePort) {
		
		// localAE(self), <aet>@<host>:<port>
		ArrayList<String> argList = new ArrayList<String>();
		argList.add("-b");
		String localAET = appName;
		String localHost = appHost;
		if(localPort == null || localPort.equals("")) {
			localPort = "22221";
		}
		argList.add(localAET + "@" + localHost + ":" + localPort);
		Log.logger.info("C-ECHO:localDcmURL:" + localAET + "@" + localHost + ":" + localPort);
		argList.add("-c");
		argList.add(remoteAET + "@" + remoteHost + ":" + remotePort);
		if(delay!=null) {
			argList.add("--accept-timeout");
			argList.add(String.valueOf(delay));
		}
		String[] args = argList.toArray(new String[argList.size()]);
		
		return StoreSCU.echo(args);
	}
	
	public void setDelay(long delay) {
		this.delay = delay;
	}
}