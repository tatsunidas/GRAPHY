package com.vis.core.log;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.logging.Level;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * TODO
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class LogWindow extends JFrame{
	
	public String save_log_file_name = "graphy_log_file";
	
	public static void main (String[] args) {
		LogWindow lw = new LogWindow();
		lw.setVisible(true);
		Log.logger.info(Log.message(Level.INFO, "Information message"));
		Log.logger.warning(Log.message(Level.WARNING, "Information message2"));
		Log.logger.severe(Log.message(Level.SEVERE, "Information message3"));
	}
	
	public Log logUtil;
	JTextArea logTextArea = null;
	JScrollPane pane = null;
	
	public LogWindow() {
		
		if(Log.logger == null) {
			logUtil = new Log();
		}
		
		logTextArea = new JTextArea();
		LogTextAreaHandler textHandler = new LogTextAreaHandler();
		textHandler.setTextArea(logTextArea);
		
		JMenuBar menubar = new JMenuBar();
		JMenu menu = new JMenu("File");
		JMenuItem saveItem = new JMenuItem("Save");
		saveItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveText(save_log_file_name);
			}
		});
		JMenuItem clearItem = new JMenuItem("Clear");
		clearItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearText();
			}
		});
		menu.add(saveItem);
		menu.add(clearItem);
		menubar.add(menu);
		setJMenuBar(menubar);
		pane = new JScrollPane(logTextArea);
		add(pane);
		setSize(400, 300);
		setTitle("Log");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		validate();
		repaint();
	}
	
	public void saveText(String titleWithoutExtension) {
		if(logTextArea !=null) {
			Document text = logTextArea.getDocument();
			if(text.getLength() == 0) {
				JOptionPane.showConfirmDialog(this, "There is empty text...");
				return;
			}
			String log = null;
			try {
				log = text.getText(0,text.getLength());
			} catch (BadLocationException e1) {
				e1.printStackTrace();
				Log.logger.warning("can not save log file...");
				return;
			}
			
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);//no file name mode
			int res = chooser.showSaveDialog(this);
			if(res == JFileChooser.APPROVE_OPTION) {
				File dest = chooser.getSelectedFile();
				String dest_path = dest.getAbsolutePath() + File.separator + titleWithoutExtension + ".txt";
				try (PrintWriter out = new PrintWriter(dest_path)) {
					out.println(log);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					Log.logger.warning(Log.message(Level.WARNING,
							"Can not find destination file location, can not save text file."));
				}
			}
			chooser = null;
			text = null;
		}
	}
	
	public void clearText() {
		if(logTextArea !=null) {
			logTextArea.setText(null);
		}
	}

}
