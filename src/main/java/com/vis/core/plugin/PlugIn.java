package com.vis.core.plugin;

/**
 * 
 * @author tatsunidas
 *
 */
public interface PlugIn {
	/** This method is called when the plugin is loaded.*/ 
	public void run(String arg[]);
	public String toString();
}
