package com.vis.core.util;

import java.io.IOException;

/**
 * Launches the operating system's own default memory/system monitor
 * application. This class does not implement any monitoring itself, it only
 * starts the native tool already provided by the OS:
 * Windows Task Manager, Ubuntu/GNOME System Monitor, macOS Activity Monitor.
 *
 * @author tatsunidas
 */
public class MemoryMonitorLauncher {

	private MemoryMonitorLauncher() {
	}

	/**
	 * @throws IOException        if the native monitor could not be started
	 *                             (e.g. command not found on this Linux desktop
	 *                             environment).
	 * @throws UnsupportedOperationException if the current OS is none of
	 *                             Windows/macOS/Linux.
	 */
	public static void launch() throws IOException {
		if (Platform.isWindows()) {
			new ProcessBuilder("taskmgr").start();
		} else if (Platform.isMac()) {
			new ProcessBuilder("open", "-a", "Activity Monitor").start();
		} else if (Platform.isLinux()) {
			new ProcessBuilder("gnome-system-monitor").start();
		} else {
			throw new UnsupportedOperationException("No known default memory monitor for this OS.");
		}
	}
}
