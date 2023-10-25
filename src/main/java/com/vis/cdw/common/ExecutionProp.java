package com.vis.cdw.common;

import java.io.File;

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
//        		return new File("cdrtools/windows-cdrtools-3.02a01_mingw/win64/cdrecord.exe");//bug, can not runn command. do not use
        		return new File("cdrtools/windows/win32/cdrecord.exe");
        	}else {
        		return new File("cdrtools/windows/win32/cdrecord.exe");
        	}
        } else if (osName.startsWith("linux") || osName.startsWith("solaris")) {
        	return new File("cdrtools/linux/cdrecord");
        }else {
        	System.err.println("Graphy can not create media on This UnknownOS..., return");
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