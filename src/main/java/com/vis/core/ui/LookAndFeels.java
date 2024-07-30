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
package com.vis.core.ui;

import java.awt.Component;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import javax.swing.UnsupportedLookAndFeelException;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.util.PropertiesUtil;

public final class LookAndFeels {

	// debug
	public static void main(String[] args) {
		new LookAndFeels();
	}

	private ArrayList<String> names;
	private HashMap<String, String> lafmap;
	private String currentLAF;

	public static final String defaultLAF = "javax.swing.plaf.metal.MetalLookAndFeel";

	public LookAndFeels() {
		installSubstanceLookAndFeels();
		currentLAF = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(),
				GraphyProp.LookAndFeels.name());
		if (currentLAF == null || currentLAF.isBlank() || !names.contains(currentLAF)) {
			currentLAF = defaultLAF.toString();
		}
		setLookAndFeel(currentLAF);
	}

	private void installSubstanceLookAndFeels() {

		names = new ArrayList<String>();
		lafmap = new HashMap<>();

		final String resourceName = "org.pushingpixels.substance.api.skin".replace('.', '/');
		final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		final URL root = classLoader.getResource(resourceName);

		/*
		 * load substance laf
		 */
		try (JarFile jarFile = ((JarURLConnection) root.openConnection()).getJarFile()) {
			Iterator<JarEntry> entries = jarFile.entries().asIterator();
			while (entries.hasNext()) {
				JarEntry je = entries.next();
				String name = je.getName();
				if (name.startsWith(resourceName) && name.endsWith(".class")) {
					String fullname = name.replace('/', '.').replaceAll(".class$", "");
					try {
						Class<?> c = classLoader.loadClass(fullname);
						String sname = c.getSimpleName();
						/*
						 * Class loading fails for LAFs other than Substance.
						 */
						if (sname.startsWith("Substance")) {
							names.add(sname);
							lafmap.put(c.getSimpleName(), c.getName());
						}
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
						return;
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		List<LookAndFeelInfo> tmp = new ArrayList<>();
		List<String> systemDefault = new ArrayList<>();
		for (LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
			systemDefault.add(i.getName());
			lafmap.put(i.getName(), i.getClassName());
			tmp.add(new ReadableLookAndFeelInfo(i.getName(), i.getClassName()));
		}
		systemDefault.addAll(names);
		names = (ArrayList<String>) systemDefault;
		for (int i = 0; i < lafmap.size(); i++) {
			tmp.add(new ReadableLookAndFeelInfo(names.get(i), lafmap.get(names.get(i))));
		}
		UIManager.setInstalledLookAndFeels(tmp.toArray(new LookAndFeelInfo[tmp.size()]));
	}

	private static class ReadableLookAndFeelInfo extends LookAndFeelInfo {

		public ReadableLookAndFeelInfo(String name, String className) {
			super(name, className);
		}

		@Override
		public String toString() {
			return getName();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof LookAndFeelInfo)) {
				return false;
			}
			LookAndFeelInfo other = (LookAndFeelInfo) obj;
			return getClassName().equals(other.getClassName());
		}

		@Override
		public int hashCode() {
			return getClassName().hashCode();
		}
	}

	/**
	 * set current LAF and save properties
	 * @param LAF
	 */
	public void setLookAndFeel(String LAF) {
		if (LAF == null || LAF.isBlank()) {
			setDefaultTheme();
			return;
		}
		this.currentLAF = LAF;
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.LookAndFeels, LAF);
	}

	// for default
	public void setDefaultTheme() {
		setLookAndFeel(defaultLAF);
	}

	public String getCurrentLAF() {
		if (this.currentLAF == null) {
			this.currentLAF = defaultLAF;
		}
		return this.currentLAF;
	}
	
	public String getShortName(String full_laf_name) {
		for(String shortName : lafmap.keySet()) {
			if(lafmap.get(shortName).equals(full_laf_name)) {
				return shortName;
			}
		}
		return null;
	}
	
	/**
	 * 
	 * @param short_name look and feels nick name
	 * @return look and feels class name
	 */
	public String getLongName(String short_name) {
		return lafmap.get(short_name);
	}

	public ArrayList<String> getAllInstalledLAFShortName() {
		return this.names;
	}

	public boolean isInstalled(String laf /* full laf name */) {
		return getInstalledLAFMap().get(laf) != null;
	}

	public HashMap<String, String> getInstalledLAFMap() {
		return this.lafmap;
	}

	public void updateLookAndFeels(Component con) {
		if (currentLAF == null) {
			setDefaultTheme();
		}
		updateLookAndFeels(currentLAF, con);
	}

	private void updateLookAndFeels(String laf, Component con) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(laf);
					SwingUtilities.updateComponentTreeUI(con);
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
						| UnsupportedLookAndFeelException e) {
					e.printStackTrace();
					return;
				}
			}
		});
	}
}
