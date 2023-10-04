package com.vis.core.ui.listener;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JTextArea;
import javax.swing.JTextField;

/*
 * DateOfBirth Text yyyy/MM/dd 
 */
public class DateTextKeyListener implements KeyListener {
	
	
	int date_txt_length = new String("yyyy/MM/dd").length();
	
	@Override
	public void keyTyped(KeyEvent evt) {
		// TODO Auto-generated method stub
		char c = evt.getKeyChar();
		if (Character.isLetter(c)) {
			//Letter
			evt.consume();// Ignore character
		} else if (Character.isDigit(c)) {
			//accept number string
		} else{
			if(String.valueOf(c).equals("/")) {
				// proceed "/"
			}else {
				// Ignore this character//何も入力されない
				evt.consume();
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {
		Object obj = e.getSource();
		if(obj instanceof JTextArea) {
			
		}else if(obj instanceof JTextField) {
			JTextField textField = (JTextField)obj;
			if(textField.getText().length() > date_txt_length) {
				textField.setText(textField.getText().substring(0, date_txt_length));
			}
			setComponentColor(e, dateValidation(textField.getText()), textField);
		}
	}
	
	public boolean dateValidation(String inputDateString){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        dateFormat.setLenient(false);
        Date parsedDate = null;
        try {
            parsedDate = dateFormat.parse(inputDateString);
        } catch (ParseException e) {
            return false;
        }
        return dateFormat.format(parsedDate).equals(inputDateString);
    }
	
	private void setComponentColor(KeyEvent e, boolean correct, JTextField txt) {
		if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
			if(txt.getText()==null || txt.getText().equals("")) {
				txt.setBackground(Color.white);
				System.out.println("return");
				return;
			}
		}
		if(!correct) {
			if(txt.getText()==null || txt.getText().equals("")) {
				txt.setBackground(Color.white);
				return;
			}
			txt.setBackground(Color.PINK);
		}else {
			txt.setBackground(Color.white);
		}
	}
}