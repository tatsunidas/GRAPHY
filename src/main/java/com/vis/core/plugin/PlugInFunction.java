package com.vis.core.plugin;

/*
 * this is just an example.
 * if you want separate use cases, add plugin interfaces like this.
 */
public interface PlugInFunction extends PlugIn{

	// let the application pass in a parameter
	public void setParameter (int param);

	// retrieve a result from the plugin
	public int getResult();

	// return the name of this plugin
	public String getPluginName();

	// can be called to determine whether the plugin
	// aborted execution due to an error condition
	public boolean hasError();
}
