package com.vis.core.log;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class LogWindow extends JFrame{
	
	//debug
	public static void main (String[] args) {
		LogWindow lw = new LogWindow();
		lw.setVisible(true);
		Log.logger.info(Log.message(Level.INFO, "Information message"));
		Log.logger.warning(Log.message(Level.WARNING, "Information message2"));
		Log.logger.severe(Log.message(Level.SEVERE, "Information message3"));
	}
	
	private static LogWindow logWin = new LogWindow();
	public final String save_log_file_name = "graphy_log_file";
	Log logUtil;
	JTextArea logTextArea = null;
	JScrollPane pane = null;
	
	private LogWindow() {
		
		if(Log.logger == null) {
			logUtil = new Log();
		}
		
		logTextArea = new JTextArea();
		logTextArea.setEditable(false);
		
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
		pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		getContentPane().add(pane, java.awt.BorderLayout.CENTER);
		setSize(400, 300);
		setTitle("Log");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		 // 標準出力と標準エラー出力をリダイレクト
        PrintStream printStream = new PrintStream(new CustomOutputStream(logTextArea));
        System.setOut(printStream);
        System.setErr(printStream);

        // ロガーの出力をリダイレクト
        Handler consoleHandler = new CustomLogHandler(logTextArea);
        Log.logger.addHandler(consoleHandler);
        Log.logger.setLevel(Level.ALL);
        consoleHandler.setLevel(Level.ALL);
	}
	
	public static LogWindow getInstance() {
		if(LogWindow.logWin == null) {
			LogWindow.logWin = new LogWindow();
		}
		return LogWindow.logWin;
	}
	
	public void saveText(String titleWithoutExtension) {
		if (logTextArea != null) {
			Document text = logTextArea.getDocument();
			if (text.getLength() == 0) {
				JOptionPane.showConfirmDialog(this, "There is empty text...");
				return;
			}
			String log = null;
			try {
				log = text.getText(0, text.getLength());
			} catch (BadLocationException e1) {
				e1.printStackTrace();
				Log.logger.warning("can not save log file...");
				return;
			}

			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int res = chooser.showSaveDialog(this);
			if (res == JFileChooser.APPROVE_OPTION) {
				File dest = chooser.getSelectedFile();
				String dest_path = dest.getAbsolutePath() + File.separator + titleWithoutExtension + ".log";
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
	
	class CustomOutputStream extends OutputStream {
        private JTextArea textArea;
        public CustomOutputStream(JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void write(int b) {
            textArea.append(String.valueOf((char) b));
            textArea.setCaretPosition(textArea.getDocument().getLength());
        }

        @Override
        public void write(byte[] b, int off, int len) {
            textArea.append(new String(b, off, len));
            textArea.setCaretPosition(textArea.getDocument().getLength());
        }
    }
	
	class CustomLogHandler extends ConsoleHandler {
        private JTextArea textArea;
        public CustomLogHandler(JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            String message = getFormatter().format(record);
            SwingUtilities.invokeLater(() -> {
                textArea.append(message);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }

}
