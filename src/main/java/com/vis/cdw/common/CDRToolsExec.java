package com.vis.cdw.common;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.logging.*;

import javax.swing.JFrame;
import javax.swing.JProgressBar;

import org.apache.commons.io.IOUtils;

public class CDRToolsExec {
	
	static Logger log = Logger.getLogger(CDRToolsExec.class.getName());
	
	public static int execAndShowProgress(String[] cmd, String warning, boolean verbose) {
		int exit = 0;
		java.lang.Process p = null;
		JFrame f = null;
		try {
			java.lang.Runtime rt = java.lang.Runtime.getRuntime();
			f = new JFrame("Now burning cd/dvd...");
			f.setLocationRelativeTo(null);
			f.setSize(330, 60);
			f.setBackground(Color.LIGHT_GRAY);
			JProgressBar progressBar = new JProgressBar();
			progressBar.setIndeterminate(true);
			progressBar.setForeground(Color.cyan);
			f.add(progressBar, BorderLayout.CENTER);
			f.setVisible(true);
    		p = rt.exec(cmd);
            exit = p.waitFor();
			if (exit == 0) {
				if(verbose) {
					stderrAndstdout(p);
				}
			}else {
				if(verbose) {
					stderrAndstdout(p);
				}
				log.info(warning + " exit(" + exit + ")");
			}
			progressBar.setIndeterminate(false);
			return exit;
		} catch (Exception e) {
			log.info(warning+"\n"+e);
			return -1;
		}finally {
			if(p != null && p.isAlive()) {
				p.destroy();
			}
			if(f != null && f.isShowing()) {
				f.dispose();
			}
		}
	}
	
	public static int exec(String[] cmd, String warning, boolean verbose) {
		int exit = 0;
		java.lang.Process p = null;
		try {
			java.lang.Runtime rt = java.lang.Runtime.getRuntime();
    		p = rt.exec(cmd);
            exit = p.waitFor();
			if (exit == 0) {
				if(verbose) {
					stderrAndstdout(p);
				}
			}else {
				if(verbose) {
					stderrAndstdout(p);
				}
				log.severe(warning + " exit(" + exit + ")");
			}
			return exit;
		} catch (Exception e) {
			log.severe(warning+"\n"+e);
			return -1;
		}finally {
			if(p != null && p.isAlive()) {
				p.destroy();
			}
		}
	}
	
	public static void execAndEndup(String[] cmd, String warning, boolean verbose) {
		int exit = 0;
		java.lang.Process p = null;
		try {
			java.lang.Runtime rt = java.lang.Runtime.getRuntime();
    		p = rt.exec(cmd);
    		
            exit = p.waitFor();
			if (exit == 0) {
				if(verbose) {
					stderrAndstdout(p);
				}
			}else {
				if(verbose) {
					stderrAndstdout(p);
				}
				log.log(Level.WARNING, " exit(" + exit + ")");
			}
		} catch (Exception e) {
			log.log(Level.WARNING, e.getMessage());
		}finally {
			if(p != null && p.isAlive()) {
				p.destroy();
			}
		}
	}
	
	private static void stderrAndstdout(java.lang.Process p) {
		String stdout;
		try {
			stdout = IOUtils.toString(p.getInputStream(), "UTF-8");
			System.out.println(stdout);
			String stderr = IOUtils.toString(p.getErrorStream(), "UTF-8");
			System.out.println(stderr);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("***************finished***************");
	}
	
}
