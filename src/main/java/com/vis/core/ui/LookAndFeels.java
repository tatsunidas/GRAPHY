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
	
	//debug
	public static void main(String[] args) {
		new LookAndFeels();
	}

	private ArrayList<String> names;
	private HashMap<String, String> lafmap;
	private String currentLAF;
	
	public static final String defaultLAF = "javax.swing.plaf.metal.MetalLookAndFeel";
	
	public LookAndFeels() {
		installSubstanceLookAndFeels();
		currentLAF = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.LookAndFeels.name());
		setLookAndFeel(currentLAF);
	}

	private void installSubstanceLookAndFeels() {

		names = new ArrayList<String>();
		lafmap = new HashMap<>();

		final String resourceName = "org.pushingpixels.substance.api.skin".replace('.', '/');
		final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		final URL root = classLoader.getResource(resourceName);

		try (JarFile jarFile = ((JarURLConnection) root.openConnection()).getJarFile()) {
			Iterator<JarEntry> entries = jarFile.entries().asIterator();
			while (entries.hasNext()) {
				JarEntry je = entries.next();
				String name = je.getName();
				if (name.startsWith(resourceName) && name.endsWith(".class")) {
					String fullname = name.replace('/', '.').replaceAll(".class$", "");
					try {
						Class<?> c = classLoader.loadClass(fullname);
						names.add(c.getSimpleName());
						lafmap.put(c.getSimpleName(), c.getName());
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
		for (LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
			tmp.add(new ReadableLookAndFeelInfo(i.getName(), i.getClassName()));
		}
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

	public void setLookAndFeel(String LAF) {
		if(LAF == null || LAF.isBlank()) {
			setDefaultTheme();
			return;
		}
		this.currentLAF = LAF;
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props.toString(), GraphyProp.LookAndFeels.name(), LAF);
	}

	// for default
	public void setDefaultTheme() {
		setLookAndFeel(defaultLAF);
	}
	
	public String getCurrentLAF() {
		if(this.currentLAF == null) {
			this.currentLAF = defaultLAF;
		}
		return this.currentLAF;
	}
	
	public ArrayList<String> getInstalledLAF(){
		return this.names;
	}
	
	public HashMap<String, String> getInstalledLAFMap(){
		return this.lafmap;
	}
	
	public void updateLookAndFeels(Component con) {
		if(currentLAF == null) {
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
