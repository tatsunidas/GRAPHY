package com.vis.core.log;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

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

import com.vis.configuration.Resources;

import com.vis.configuration.ConfigInfo;
import com.vis.core.util.Utils;

/**
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class Log extends JFrame{
	
	private static Log logWin;
	public final String save_log_file_name = ConfigInfo.LogFileName.toString();
	JTextArea logTextArea = null;
	JScrollPane pane = null;
	
	public static Logger logger = Logger.getLogger(Log.class.getName());
	static CustomLogHandler logFileHandler;
	private static boolean append_mode = false;
	
	boolean isDebug = Utils.isDebug;
	
	// ANSI escape code
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
	
	private Log() {
		
		if(logger == null) {
			logger = Logger.getLogger(Log.class.getName());
		}

		initContents();
		
		File logDir = new File(ConfigInfo.getPath(ConfigInfo.LogDirName));
		if (!logDir.exists()) {
			logDir.mkdirs();
		}

		// add handler
		try {
			logFileHandler = new CustomLogHandler(ConfigInfo.LogFilePath.toString(),
					Integer.parseInt(ConfigInfo.LogFileLimit.toString()),
					Integer.parseInt(ConfigInfo.LogFileCount.toString()), append_mode);
			logFileHandler.setTextArea(logTextArea);
			logger.addHandler(logFileHandler);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to initialize logging", e);
		}
		
		if (!isDebug) {
			logger.setLevel(Level.INFO);// out greater or equal to INFO level
		} else {
			logger.setLevel(Level.ALL);
		}
		logger.setUseParentHandlers(false);
	}
	
	public static synchronized Log getInstance() {
	    if (logWin == null) {
	        logWin = new Log();
	    }
	    return logWin;
	}
	
	private void initContents() {
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
	}
	
	public void saveText(String titleWithoutExtension) {
		if (logTextArea != null) {
			Document text = logTextArea.getDocument();
			if (text.getLength() == 0) {
				JOptionPane.showMessageDialog(this, Resources.i18n("Log.error.emptyText"),
						Resources.i18n("dialog.title.information"), JOptionPane.INFORMATION_MESSAGE);
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
					logger.warning("Can not find destination file location, can not save text file.");
				}
			}
		}
	}
	
	public void clearText() {
		if(logTextArea !=null) {
			logTextArea.setText(null);
		}
	}
	
	class CustomLogHandler extends FileHandler {

		public CustomLogHandler(String pattern, long limit, int count, boolean append) throws IOException {
			super(pattern, limit, count, append);
			if (!isDebug) {
				setLevel(Level.INFO);// out greater or equal to INFO level
			} else {
				setLevel(Level.ALL);
			}
			setFormatter(new SimpleFormatter());
		}

		private JTextArea textArea;

		void setTextArea(JTextArea textArea) {
			this.textArea = textArea;
		}
		
		@Override
		public void publish(LogRecord record) {
		    if (!isLoggable(record)) {
		        return;
		    }
		    // (1) ファイルには元のフォーマットで書き込む
		    super.publish(record);
		    
		    String originalMessage = getFormatter().format(record);
		    
		    // (2) Output colored message to console (intentional System.out for developer visibility)
		    System.out.println(message(record.getLevel(), originalMessage)); // NOSONAR: intentional console output for log display
		    
		    // (3) JTextAreaには元のメッセージを追記する
		    SwingUtilities.invokeLater(() -> {
		        textArea.append(originalMessage);
		        textArea.setCaretPosition(textArea.getDocument().getLength());
		    });
		}

		@Override
		public void flush() {
			super.flush();
		}

		@Override
		public void close() throws SecurityException {
			super.close();
		}
	}
	
	/**
	 * @param lv logger level
	 * @param message message text string
	 * @return
	 */
	private static String message(Level lv, String message) {
		if(message == null || message.length() < 1) {
			return message;
		}
//		if(Locale.getDefault() == Locale.JAPAN) {
//			return message;//to avoid garbled characters
//		}
		String msg = null;
		if(lv == Level.CONFIG || lv == Level.FINE) {
			msg = ANSI_GREEN + message.toString() + ANSI_RESET;
		}else if(lv == Level.INFO) {
			msg = ANSI_BLACK + message.toString() + ANSI_RESET;
		}else if(lv == Level.WARNING) {
			msg = ANSI_YELLOW + message.toString() + ANSI_RESET;
		}else if(lv == Level.SEVERE) {
			msg = ANSI_RED + message.toString() + ANSI_RESET;
		}else {
			msg = ANSI_BLACK + message.toString() + ANSI_RESET;
		}
		return msg;
	}

}
