package com.vis.core.view.D2.ui;

import ij.*;
import ij.io.*;
import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.vis.configuration.Resources;

/** 
 * 
 * Uses a TextPanel to displays text in a window.
	@see TextPanel
*/
@SuppressWarnings("serial")
public class ResultWindow extends JFrame implements ActionListener, ItemListener {
	
	//debug
	public static void main(String[] args) {
		String header = "a	b	c	d	e	f	g";
		String testRow1 = "1	2	3	4	5	6	7";
		String testRow2 = "10	20	30	40	50	60	70";
		String testRow3 = "100	200	300	400	500	600	700";
		ResultWindow win = new ResultWindow("Test", header, 400, 200);
		win.append(testRow1);
		win.append(testRow2);
		win.append(testRow3);
	}

	ResultTable rtable;
    CheckboxMenuItem antialiased;
	int[] sizes = {9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72};
	int fontSize = 14;
	private static Font font;
 
	/**
	* Opens a new single-column text window.
	* @param title	the title of the window
	* @param text		the text initially displayed in the window
	* @param width	the width of the window in pixels
	* @param height	the height of the window in pixels
	*/
	public ResultWindow(String title, int width, int height) {
		this(title, "", width, height);
	}

	/**
	* Opens a new multi-column text window.
	* @param title	title of the window
	* @param headings	tab-delimited column headings
	* @param text		ArrayList containing the text to be displayed in the window
	* @param width	width of the window in pixels
	* @param height	height of the window in pixels
	*/
	public ResultWindow(String title, String headings, int width, int height) {
		super(title);
		if (headings.endsWith("\t")) {
			headings = headings.substring(0, headings.length()-1);
		}
		int headerNum = headings.split("\t").length;
		rtable = new ResultTable(title, headerNum);
		rtable.setColumnHeadings(headings);
		setContents(title, rtable, width, height);
		rtable.getPane().getColumnHeader().setVisible(true);//show header
		rtable.getPane().revalidate();
	}

	private void setContents(String title, ResultTable rtable, int width, int height) {
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		if (IJ.isLinux()) setBackground(ImageJ.backgroundColor);
		add("Center", rtable.getPane());
		addKeyListener(rtable);
		setIconImage(Resources.ResultWinIcon.loadIconFromResource().getImage());
 		addMenuBar();
		setFont();
		setSize(width, height);
		setLocationRelativeTo(null);//todo :: Viewer2DFrame.getIntance()
		pack();
		setVisible(true);
	}

	/**
	* Opens a new text window containing the contents of a text file.
	* @param path		the path to the text file
	* @param width	the width of the window in pixels
	* @param height	the height of the window in pixels
	*/
	public ResultWindow(String csvFile, boolean headerIncluded, int width, int height) {
		super("");
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		rtable = new ResultTable("",200);
		add(BorderLayout.CENTER, rtable.getPane());
		if (openFile(csvFile)) {
			setSize(width, height);
			setVisible(true);
			setLocationRelativeTo(null);
		} else {
			dispose();
		}
	}
	
	void addMenuBar() {
		JMenuBar mb = new JMenuBar();
		mb.setFont(font);
		/** File **/
		JMenu fileMenu = new JMenu("File");
		JMenuItem saveAsItem = new JMenuItem("Save As...");
		KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_S, 0);
		saveAsItem.setAccelerator(ks);
		fileMenu.add(saveAsItem);
		saveAsItem.addActionListener(this);
		mb.add(fileMenu);
		/** Edit **/
		JMenu editMenu = new JMenu("Edit");
//		m.add(new MenuItem("Cut", new MenuShortcut(KeyEvent.VK_X)));
		JMenuItem copyItem = new JMenuItem("Copy");
		KeyStroke kc = KeyStroke.getKeyStroke(KeyEvent.VK_C, 0);
		copyItem.setAccelerator(kc);
		copyItem.addActionListener(this);
		editMenu.add(copyItem);
		JMenuItem clearItem = new JMenuItem("Clear");
		editMenu.add(clearItem);
		clearItem.addActionListener(this);
		JMenuItem selectAllItem = new JMenuItem("Select All");
		KeyStroke ka = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);
		selectAllItem.setAccelerator(ka);
		selectAllItem.addActionListener(this);
		editMenu.add(selectAllItem);
		editMenu.addSeparator();
		JMenuItem findItem = new JMenuItem("Find...");
		KeyStroke kf = KeyStroke.getKeyStroke(KeyEvent.VK_F, 0);
		findItem.setAccelerator(kf);
		findItem.addActionListener(this);
		editMenu.add(findItem);
		mb.add(editMenu);
		JMenu fontMenu = new JMenu("Font");
		JMenuItem textSmallerItem = new JMenuItem("Make Text Smaller");
		textSmallerItem.addActionListener(this);
		fontMenu.add(textSmallerItem);
		JMenuItem textLargerItem = new JMenuItem("Make Text Larger");
		textLargerItem.addActionListener(this);
		fontMenu.add(textLargerItem);
		mb.add(fontMenu);
		setJMenuBar(mb);
	}

	/**
	Adds one or more lines of text to the window.
	@param text		The text to be appended. Multiple
					lines should be separated by \n.
	*/
	public void append(String textline) {
		if(textline == null || textline.equals("")) {
			return;
		}
		if(textline.endsWith("\t") || textline.endsWith("\n")) {
			textline = textline.substring(0, textline.length()-1);
		}
		String[] valsArray = textline.split("\t");
		System.out.println(valsArray.length);
		ArrayList<String> vals = new ArrayList<>(Arrays.asList(valsArray));
		rtable.appendRow(vals);
	}
	
	public void updateRow(int row, String textline) {
		if(textline == null || textline.equals("")) {
			return;
		}
		if(textline.endsWith("\t") || textline.endsWith("\n")) {
			textline = textline.substring(0, textline.length()-1);
		}
		String[] valsArray = textline.split("\t");
		ArrayList<String> vals = new ArrayList<>(Arrays.asList(valsArray));
		rtable.updateRow(row,vals);
	}
	
	void setFont() {
		if (font!=null)
       		rtable.setFont(font);
       	else
       		rtable.setFont(new Font("SanSerif", Font.PLAIN, fontSize));
		revalidate();
		repaint();
	}
	
	boolean openFile(String path) {
		OpenDialog od = new OpenDialog("Open Text File...", path);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return false;
		path = directory + name;
		
		IJ.showStatus("Opening: " + path);
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory + name));
			load(r);
			r.close();
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return false;
		}
		setTitle(name);
		IJ.showStatus("");
		return true;
	}
	
	/** Returns a reference to this TextWindow's TextPanel. */
	public ResultTable getTextPanel() {
		return rtable;
	}


	/** Appends the text in the specified file to the end of this TextWindow. */
	public void load(BufferedReader in) throws IOException {
		while (true) {
			String s=in.readLine();
			if (s==null) break;
			append(s);
		}
	}

	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();
		System.out.println(cmd);
		if (cmd.equals("Make Text Larger")) {
			changeFontSize(true);
		}else if (cmd.equals("Make Text Smaller")) {
			changeFontSize(false);
//		}else if (cmd.equals("Save Settings")) {
//			saveSettings();
		}else {
			rtable.doCommand(cmd);
		}
	}

	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		int id = e.getID();
		if (id==WindowEvent.WINDOW_CLOSING) {
			close();
		}
	}

	public void itemStateChanged(ItemEvent e) {
        setFont();
	}

	public void close() {
		close(true);
	}
	
	/** Closes this TextWindow. Display a "save changes" dialog
		if this is the "Results" window and 'showDialog' is true. */
	public void close(boolean showDialog) {
		dispose();
	}
	
	boolean saveContents() {
		rtable.saveAsCSV();
		return true;
	}
	
	void changeFontSize(boolean larger) {
        if (larger) {
            fontSize++;
            if (fontSize>=28)
                fontSize = 28;
        } else {
            fontSize--;
            if (fontSize<0)
                fontSize = 0;
        }
        font = null;
        setFont();
    }
    
    public static void setFont(String name, int style, int size) {
    	font = new Font(name,style,size);
    }

    /*
     * todo
     */
	void saveSettings() {
//		Prefs.set(FONT_SIZE, fontSize);
//		Prefs.set(FONT_ANTI, antialiased.getState());
//		IJ.showStatus("Font settings saved (size="+sizes[fontSize]+", antialiased="+antialiased.getState()+")");
	}

}
