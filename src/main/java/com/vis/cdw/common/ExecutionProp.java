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
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
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
 * ***** END LICENSE BLOCK *****
 */
package com.vis.cdw.common;

import java.io.File;

/**
 * 
 * @author tatsunidas
 *
 */
public class ExecutionProp {
	
	private static boolean isArchType64() {
		String arch = System.getenv("PROCESSOR_ARCHITECTURE");
		String wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432");

		String realArch = arch != null && arch.endsWith("64")
		                  || wow64Arch != null && wow64Arch.endsWith("64")
		                      ? "64" : "32";
		return realArch.equals("64") ? true : false;
	}
		
	public static File loadCdrecordExecution() {
		String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("mac")) {
        	return new File("cdrtools/mac/cdrecord");
        } else if (osName.startsWith("windows")) {
        	if(isArchType64()) {
//        		return new File("cdrtools/windows-cdrtools-3.02a01_mingw/win64/cdrecord.exe");//bug, can not run command. do not use
        		return new File("cdrtools/windows/win32/cdrecord.exe");
        	}else {
        		return new File("cdrtools/windows/win32/cdrecord.exe");
        	}
        } else if (osName.startsWith("linux") || osName.startsWith("solaris")) {
        	return new File("cdrtools/linux/cdrecord");
        }else {
        	System.err.println("Graphy can not create media on UnknownOS..., return");
        	return null;
        }
	}
	
	public static File loadMakeIsoFsExecution() {
		String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("mac")) {
        	return new File("cdrtools/mac/mkisofs");
        } else if (osName.startsWith("windows")) {
        	if(isArchType64()) {
//        		return new File("cdrtools/windows-cdrtools-3.02a01_mingw/win64/mkisofs.exe");//bug, can not runn command. do not use
        		return new File("cdrtools/windows/win32/mkisofs.exe");
        	}else {
        		return new File("cdrtools/windows/win32/mkisofs.exe");
        	}
        } else if (osName.startsWith("linux") || osName.startsWith("solaris")) {
        	return new File("cdrtools/linux/mkisofs");
        }else {
        	System.err.println("Graphy can not create media on This UnknownOS..., return");
        	return null;
        }
	}
	
}