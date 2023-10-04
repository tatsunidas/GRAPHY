package com.vis.core.ui.dialog;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JOptionPane;

/**
 * TODO
 * for use default icon ?
 * @author tatsunidas
 *
 */
public class PopUpMessage {
	
	static Icon questionIcon = null;
	static Icon errorIcon = null;
	static Icon warinigIcon = null;
	static Icon confirmIcon = null;
	
	public PopUpMessage() {}
	
	public static int showDialog(Component parent, String title, String msg, int optionType, int msgType) {
    	return JOptionPane.showOptionDialog(parent, msg, title, optionType, msgType,  loadIcon(msgType), null, null);
	}
	
	public static Icon loadIcon(int msgType){
		if(msgType == JOptionPane.ERROR_MESSAGE) {
			return errorIcon;
		}else if(msgType == JOptionPane.INFORMATION_MESSAGE) {
			return confirmIcon;
		}else if(msgType == JOptionPane.WARNING_MESSAGE) {
			return warinigIcon;
		}else if(msgType == JOptionPane.QUESTION_MESSAGE) {
			return questionIcon;
		}else {
			return null;
		}
	}
	
}
