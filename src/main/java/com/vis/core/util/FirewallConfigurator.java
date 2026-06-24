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
package com.vis.core.util;

import java.util.logging.Level;

import com.vis.core.log.Log;

/**
 * Windows/Linux only: ensures the inbound firewall rule for DICOM
 * communication exists for the currently configured listener port (see
 * {@code DatabaseHandler.getListenerDetails()} / the LISTENER table — the
 * port is user-configurable via PACSConnectionPrefs, it is NOT always 11112).
 *
 * Elevation for the single command involved is requested just once, on
 * demand, instead of running the whole application elevated/as root:
 * Windows via "netsh advfirewall" through a UAC prompt (PowerShell
 * Start-Process -Verb RunAs), Linux via "pkexec" (PolicyKit auth dialog,
 * requires a desktop session with a polkit agent running).
 *
 * macOS is intentionally not handled here: its Application Firewall, when
 * enabled, already prompts the user per-app the first time it accepts an
 * incoming connection, so no pre-configuration is needed.
 *
 * Called from DatabaseHandler.initDicomServer(), which is re-invoked whenever
 * the listener port changes (e.g. via PACSConnectionPrefs), so this stays in
 * sync with the actually configured port rather than a fixed default.
 */
public class FirewallConfigurator {

	private static final String RULE_NAME_PREFIX = "GRAPHY DICOM";

	/**
	 * @param port the currently configured local DICOM listener port (from the
	 *             LISTENER table, NOT necessarily 11112).
	 */
	public static void ensureDicomPortOpen(String port) {
		if (port == null || port.isEmpty()) {
			return;
		}
		if (Platform.isWindows()) {
			new Thread(() -> ensureDicomPortOpenWindows(port), "firewall-configurator").start();
		} else if (Platform.isLinux()) {
			new Thread(() -> ensureDicomPortOpenLinux(port), "firewall-configurator").start();
		}
		// macOS: no-op, see class javadoc.
	}

	// ---------------------------------------------------------------- Windows

	private static void ensureDicomPortOpenWindows(String port) {
		try {
			String ruleName = RULE_NAME_PREFIX + " (" + port + ")";
			Process show = new ProcessBuilder(
					"netsh", "advfirewall", "firewall", "show", "rule", "name=" + ruleName)
					.redirectErrorStream(true)
					.start();
			if (show.waitFor() == 0) {
				return; // already exists
			}
			// Run only "netsh" elevated via UAC (one prompt, once), not the whole JVM.
			String netshArgs = "advfirewall firewall add rule name=\\\"" + ruleName
					+ "\\\" dir=in action=allow protocol=TCP localport=" + port;
			String psCommand = "Start-Process -FilePath netsh -ArgumentList '" + netshArgs + "' -Verb RunAs -Wait";
			Process add = new ProcessBuilder(
					"powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psCommand)
					.redirectErrorStream(true)
					.start();
			if (add.waitFor() == 0) {
				Log.logger.info("Added Windows firewall rule for DICOM (port " + port + ").");
			} else {
				Log.logger.warning("User declined or failed to add the Windows firewall rule for DICOM (port "
						+ port + "). It can be added manually later.");
			}
		} catch (Exception e) {
			// Best-effort only: never block/crash startup over firewall configuration.
			Log.logger.log(Level.WARNING, "Failed to configure Windows firewall rule for DICOM (port " + port + ")."
					+ " The user may need to open it manually.", e);
		}
	}

	// ------------------------------------------------------------------ Linux

	private static void ensureDicomPortOpenLinux(String port) {
		try {
			if (commandExists("ufw") && isActive("ufw", "status") ) {
				if (!run("ufw", "status").contains(port + "/tcp")) {
					runElevated("ufw", "allow", port + "/tcp");
				}
				return;
			}
			if (commandExists("firewall-cmd") && run("firewall-cmd", "--state").trim().equals("running")) {
				Process query = new ProcessBuilder("firewall-cmd", "--query-port=" + port + "/tcp")
						.redirectErrorStream(true).start();
				if (query.waitFor() != 0) {
					runElevated("sh", "-c",
							"firewall-cmd --permanent --add-port=" + port + "/tcp && firewall-cmd --reload");
				}
			}
			// Neither ufw nor firewalld active: nothing to open, no-op.
		} catch (Exception e) {
			Log.logger.log(Level.WARNING, "Failed to configure Linux firewall rule for DICOM (port " + port + ")."
					+ " The user may need to open it manually.", e);
		}
	}

	private static boolean commandExists(String cmd) {
		try {
			Process p = new ProcessBuilder("which", cmd).redirectErrorStream(true).start();
			return p.waitFor() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** "ufw status" returns 0 regardless of active/inactive, so check the text. */
	private static boolean isActive(String cmd, String... args) throws Exception {
		java.util.List<String> cmdLine = new java.util.ArrayList<>();
		cmdLine.add(cmd);
		cmdLine.addAll(java.util.Arrays.asList(args));
		String out = run(cmdLine.toArray(new String[0]));
		return out.contains("Status: active");
	}

	private static String run(String... cmd) throws Exception {
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		String out = new String(p.getInputStream().readAllBytes());
		p.waitFor();
		return out;
	}

	private static void runElevated(String... cmd) throws Exception {
		java.util.List<String> pkexecCmd = new java.util.ArrayList<>();
		pkexecCmd.add("pkexec");
		pkexecCmd.addAll(java.util.Arrays.asList(cmd));
		Process p = new ProcessBuilder(pkexecCmd).redirectErrorStream(true).start();
		int exit = p.waitFor();
		if (exit == 0) {
			Log.logger.info("Added Linux firewall rule for DICOM via: " + String.join(" ", cmd));
		} else {
			Log.logger.warning("User declined or failed to add the Linux firewall rule for DICOM ("
					+ String.join(" ", cmd) + "). It can be added manually later.");
		}
	}
}
