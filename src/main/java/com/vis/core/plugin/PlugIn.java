package com.vis.core.plugin;

public interface PlugIn {
	/** This method is called when the plugin is loaded.*/ 
	public void run(String arg[]);
	public String toString();
}
