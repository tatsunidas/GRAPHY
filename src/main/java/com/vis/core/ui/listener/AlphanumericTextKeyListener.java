package com.vis.core.ui.listener;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

import javax.swing.JTextArea;
import javax.swing.JTextField;

public class AlphanumericTextKeyListener implements KeyListener{
	
	private int max = Integer.MAX_VALUE;
	private ArrayList<String> acceptableString = new ArrayList<>();
	private boolean accept_all = false;
	
	public static String[] pname_acceptables = new String[] {"_","^","-"};
	public static String[] pid_acceptables = new String[] {"_","-"};
	//see, DateTextKeyListener
//	public static String[] bod_acceptables = new String[] {"/"};//birth of date
	
	public AlphanumericTextKeyListener(Integer maximumTextLength) {
		this(maximumTextLength, null);//
	}
	
	public AlphanumericTextKeyListener(Integer maximumTextLength, String[] acceptableStrings) {
		if(maximumTextLength != null) {
			max = maximumTextLength;
		}
		
		if(acceptableStrings != null) {
			for(String s:acceptableStrings) {
				if(s == null || s.equals("")) {
					continue;
				}
				if(!acceptableString.contains(s)) {
					acceptableString.add(s);
				}
			}
			if(acceptableString.size() == 0) {
				accept_all = true;
			}
		}else {
			accept_all = true;
		}
	}
	
	@Override
	public void keyTyped(KeyEvent evt) {
		char c = evt.getKeyChar();
		if (Character.isLetter(c)) {
			// accept
//			System.out.println("letter:" + true);
		} else if (Character.isDigit(c)) {
			// accept
//			System.out.println("digit:" + true);
		} else{
			String in = String.valueOf(evt.getKeyChar());
			if(!accept_all) {
				if (acceptableString.contains(in)) {
					// accept input
				}else {
					evt.consume();// Ignore input character
				}
			}else {
				//all accept
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		Object obj = e.getSource();
		if(obj instanceof JTextArea) {
			
		}else if(obj instanceof JTextField) {
			JTextField textField = (JTextField)obj;
			if(textField.getText() == null || textField.getText().equals("")) {
				return;
			}
			if(textField.getText().length()>max) {
				textField.setText(textField.getText().substring(0, max));
			}
		}
	}
}
