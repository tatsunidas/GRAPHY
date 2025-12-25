/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.D2.ui;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.io.*;
import ij.measure.ResultsTable;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.undo.UndoableEdit;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.Resources;
import com.vis.core.log.Log;

/**
 * 
 * @author tatsunidas
 * 
 */
@SuppressWarnings("serial")
public class ResultWindow extends JFrame implements ActionListener, ItemListener, MouseListener, KeyListener {

	final String name = ConfigInfo.ResultWindow.name();
	static final int DOUBLE_CLICK_THRESHOLD = 650;
	final String roiIndCol = "Row #";
	private static Font font;
	public static void setFont(String name, int style, int size) {
		font = new Font(name, style, size);
	}
	Vector<String> headers;
	boolean showHeaders = true;
	boolean showRowIndex;
	JTable table;
	JPopupMenu pm;

	String filePath;
	String searchString;

	JMenu fileMenu, editMenu;
	long mouseDownTime;

	int fontSize = 14;
	int decimalPlace =3;
	
	//action commands
	final String SAVE_AS_CSV = "Save As CSV";
	final String COPY = "Copy";
	final String CLEAR = "Clear";
	final String SELECT_ALL = "Sellect All";
	final String FIND = "Find...";
	final String TEXT_SMALL = "Make Text Smaller";
	final String TEXT_LARGE = "Make Text Larger";
	
//	UndoManager undoManager = new UndoManager();

	/**
	 * Opens a new text window containing the contents of a text file.
	 * 
	 * @param path   the path to the text file
	 * @param width  the width of the window in pixels
	 * @param height the height of the window in pixels
	 */
	public ResultWindow(String csvFile, boolean headerIncluded, int width, int height) {
		super("");
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		initContents(null);
		if (openCSV(csvFile)) {
			setSize(width, height);
			setVisible(true);
			setLocationRelativeTo(null);
		} else {
			dispose();
		}
	}

	/**
	 * Opens a new single-column text window.
	 * 
	 * @param title  the title of the window
	 * @param text   the text initially displayed in the window
	 * @param width  the width of the window in pixels
	 * @param height the height of the window in pixels
	 */
	public ResultWindow(String title, int width, int height) {
		this(title, null, width, height, true);
	}

	/**
	 * Opens a new multi-column text window.
	 * 
	 * @param title    title of the window
	 * @param headings tab-delimited column headings
	 * @param text     ArrayList containing the text to be displayed in the window
	 * @param width    width of the window in pixels
	 * @param height   height of the window in pixels
	 */
	public ResultWindow(String title, String[] headings, int width, int height, boolean showRowIndex) {
		super(title);
		setName(title);// for window manager
		this.showRowIndex = showRowIndex;
		setSize(width, height);
		setLocationRelativeTo(Viewer2DScreen.getInstance());
		initContents(headings);
	}

	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();
		if (cmd.equals(TEXT_LARGE)) {
			changeFontSize(true);
		} else if (cmd.equals(TEXT_SMALL)) {
			changeFontSize(false);
		} else {
			doCommand(cmd);
		}
	}

	void addMenuBar() {
		JMenuBar mb = new JMenuBar();
		mb.setFont(font);
		/** File **/
		JMenu fileMenu = new JMenu("File");
		JMenuItem saveAsItem = new JMenuItem(SAVE_AS_CSV);
		KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_S, 0);
		saveAsItem.setAccelerator(ks);
		fileMenu.add(saveAsItem);
		saveAsItem.addActionListener(this);
		mb.add(fileMenu);
		/** Edit **/
		JMenu editMenu = new JMenu("Edit");
//		m.add(new MenuItem("Cut", new MenuShortcut(KeyEvent.VK_X)));
		JMenuItem copyItem = new JMenuItem(COPY);
		KeyStroke kc = KeyStroke.getKeyStroke(KeyEvent.VK_C, 0);
		copyItem.setAccelerator(kc);
		copyItem.addActionListener(this);
		editMenu.add(copyItem);
		JMenuItem clearItem = new JMenuItem(CLEAR);
		editMenu.add(clearItem);
		clearItem.addActionListener(this);
		JMenuItem selectAllItem = new JMenuItem(SELECT_ALL);
		KeyStroke ka = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);
		selectAllItem.setAccelerator(ka);
		selectAllItem.addActionListener(this);
		editMenu.add(selectAllItem);
		editMenu.addSeparator();
		JMenuItem findItem = new JMenuItem(FIND);
		KeyStroke kf = KeyStroke.getKeyStroke(KeyEvent.VK_F, 0);
		findItem.setAccelerator(kf);
		findItem.addActionListener(this);
		editMenu.add(findItem);
		mb.add(editMenu);
		/** Font **/
		JMenu fontMenu = new JMenu("Font");
		JMenuItem textSmallerItem = new JMenuItem(TEXT_SMALL);
		textSmallerItem.addActionListener(this);
		fontMenu.add(textSmallerItem);
		JMenuItem textLargerItem = new JMenuItem(TEXT_LARGE);
		textLargerItem.addActionListener(this);
		fontMenu.add(textLargerItem);
		mb.add(fontMenu);
		setJMenuBar(mb);
	}

	void addPopupItem(String s) {
		JMenuItem mi = new JMenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	void addPopupMenu() {
		pm = new JPopupMenu();
		addPopupItem(SAVE_AS_CSV);
		pm.addSeparator();
//		addPopupItem("Cut");
		addPopupItem(COPY);
		addPopupItem(CLEAR);
		addPopupItem(SELECT_ALL);
//		pm.addSeparator();
//		addPopupItem("Clear Results");
//		addPopupItem("Summarize");
//		addPopupItem("Distribution...");
		// addPopupItem("Set Measurements...");
		table.add(pm);
	}
	
	/**
	 * append or update values at column.
	 * @param columnName
	 * @param values
	 */
	public void appendColumn(String columnName, String[] values/*null-able*/) {
		if (columnName==null)
			throw new IllegalArgumentException("Column or values is null");
		if(values == null || values.length == 0) {
			//add blank col
			setValue(columnName, 0, null);
			int headerPos = table.getColumnModel().getColumnIndex(columnName);
			for (int i=1; i<getRowCount(); i++)
				setValue(headerPos, i, null);
			return;
		}
		//creates the column if required
		setValue(columnName, 0, values[0]);
		//add values
		int headerPos = table.getColumnModel().getColumnIndex(columnName);
		for (int i=1; i<values.length; i++)
			setValue(headerPos, i, values[i]);
	}

	public void appendRow(ArrayList<?> row) {
		if (row == null || row.size() < 1) {
			Object[] blank = null;
			appendRow(blank);
			return;
		}
		Object[] rowVals = row.toArray(new String[row.size()]);
		appendRow(rowVals);
	}
	
	private void appendRow(Object[] row/*null-able*/) {
		if (row == null) {
			Object[] blankRow = new Object[getColumnCount()];
			Arrays.fill(blankRow, null);
			if(showRowIndex) {
				blankRow[0] = String.valueOf(getRowCount()+1);
			}
			DefaultTableModel model = (DefaultTableModel) table.getModel();
			model.addRow(blankRow);
			return;
		}
		DefaultTableModel model = (DefaultTableModel) table.getModel();
		if(showRowIndex) {
			Object[] row2 = new Object[row.length+1];
			row2[0] = Integer.valueOf(getRowCount()+1);
			for(int i =1; i<row2.length; i++) {
				row2[i] = row[i-1];
			}
			model.addRow(row2);
		}else {
			model.addRow(row);
		}
	}

	void changeFontSize(boolean larger) {
		if (larger) {
			fontSize++;
			if (fontSize >= 28)
				fontSize = 28;
		} else {
			fontSize--;
			if (fontSize < 0)
				fontSize = 0;
		}
		font = null;
		setFont();
	}
	
	/**
	 * Copies the current selection to the system clipboard. Returns the number of
	 * characters copied.
	 */
	public void copySelection() {
		StringBuffer buffer = new StringBuffer();
		int numCols = table.getSelectedColumnCount();
		int numRows = table.getSelectedRowCount();
		int[] rowsSelected = table.getSelectedRows();
		int[] colsSelected = table.getSelectedColumns();

		for (int i = 0; i < numRows; i++) {
			for (int j = 0; j < numCols; j++) {
				// copy val all cell
				buffer.append(table.getValueAt(rowsSelected[i], colsSelected[j]));
				if (j < numCols - 1) {
					buffer.append("\t");
				}
			}
			buffer.append("\n");
		}
		// send to clip
		StringSelection ss = new StringSelection(buffer.toString());
		Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
		clip.setContents(ss, ss);
	}

	private String[] createInitialCols(String[] headers) {
		if (headers == null || headers.length == 0) {
			if(showRowIndex) {
				return new String[] {roiIndCol};
			}else {
				return null;
			}
		} else {
			if(showRowIndex) {
				String[] h2 = new String[headers.length+1];
				h2[0] = roiIndCol;
				for(int i=1; i<h2.length; i++) {
					h2[i] = headers[i-1];
				}
				return h2;
			}else {
				return headers;
			}
		}
	}

	/** Deletes the selected lines. */
	public void deleteSelectedRows() {
		int[] selectedRows = table.getSelectedRows();
		Integer[] srows = new Integer[selectedRows.length];
		int pos = 0;
		for(int v : selectedRows) {srows[pos++]=Integer.valueOf(v);}
		//delete from larger index row. 
		Arrays.sort(srows, Collections.reverseOrder());
		for (int i : srows) {
			int ind = table.convertRowIndexToModel(i);
			((DefaultTableModel) table.getModel()).removeRow(ind);
		}
		//update row indices
		int rows = getRowCount();
		for(int i=0; i<rows; i++) {
			setValue(roiIndCol, i, String.valueOf(i+1));
		}
		table.repaint();
	}

	/**
	 * TODO
	 * @return
	 */
	public ResultsTable distribution() {
		return null;
	}

	/** Implements the Clear command. */
	public void doClear() {
		if (table.getSelectedRowCount() == 0) {
			selectAll();
			deleteSelectedRows();
		} else {
			deleteSelectedRows();
		}
	}

	void doCommand(String cmd) {
		if (cmd == null) {
			return;
		}
		if (cmd.equals(SAVE_AS_CSV)) {
			saveAsCSV();
		} else if (cmd.equals(COPY)) {
			copySelection();
		} else if (cmd.equals(CLEAR)) {
			doClear();
		} else if (cmd.equals(SELECT_ALL)) {
			selectAll();
		} else if (cmd.equals(FIND)) {
			SwingUtilities.invokeLater(()->find(null));
		} 
//		else if (cmd.equals("Summarize")) {
//			summarize();
//		} else if (cmd.equals("Distribution...")) {
//			distribution();
//		}
	}

	private void find(String s) {
		if (s == null) {
			GenericDialog gd = new GenericDialog("Find (Perfect matching)...", null);
			gd.addStringField("Find: ", searchString, 20);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			s = gd.getNextString();// .trim()
		}
		if (s.equals("")) {
			return;
		}

		int rows = table.getRowCount();
		int cols = table.getColumnCount();
		/*
		 * CellRenderer was shared in Column, not cell by cell
		 */
		CustomTableCellRenderer renderer = new CustomTableCellRenderer(s, Color.CYAN);
		for (int c = 0; c < cols; c++) {
			boolean found = false;
			table.getColumnModel().getColumn(c).setCellRenderer(renderer);
			for (int r = 0; r < rows; r++) {
				String v = (String) table.getValueAt(r, c);
				if (v.equals(s)) {
					found = true;
				} else {
					found = false;
				}
			}
			if (!found) {
				table.getColumnModel().getColumn(c).setCellRenderer(null);
			}
		}
		table.repaint();
	}

	public void fireTableDataChanged() {
		DefaultTableModel m = (DefaultTableModel) table.getModel();
		m.fireTableDataChanged();
		table.repaint();
	}

	/**
	 * if showRoiInd is true, RoiIndex column is included to count. 
	 * @return
	 */
	public int getColumnCount() {
		return table.getColumnCount();
	}

	public String[] getColumnHeadings() {
		JTableHeader header = table.getTableHeader();
		int headers = header.getColumnModel().getColumnCount();
		String[] headings = new String[headers];
		for (int i = 0; i < headers; i++) {
			String columnName = header.getColumnModel().getColumn(i).getHeaderValue().toString();
			// System.out.println("Column " + (i + 1) + ": " + columnName);
			headings[i] = columnName;
		}
		return headings;
	}

	/** Returns the column headings as a tab-delimited string. */
	public String getColumnHeadingsAsString() {
		String headings = "";
		JTableHeader header = table.getTableHeader();
		int cols = header.getColumnModel().getColumnCount();
		for (int i = 0; i < cols; i++) {
			String columnName = header.getColumnModel().getColumn(i).getHeaderValue().toString();
			// System.out.println("Column " + (i + 1) + ": " + columnName);
			if (i == cols - 1) {
				headings += columnName;
			} else {
				headings += columnName + "\t";
			}
		}
		return headings;
	}

	/** Returns a reference to this TextWindow's TextPanel. */
	public JTable getResultTable() {
		return table;
	}
	
	public int getRowCount() {
		return table.getRowCount();
	}
	
	public String getValue(String columnName, int row) {
		int headerPos = table.getColumnModel().getColumnIndex(columnName);
		if(headerPos == -1) {
			return null;
		}
		return getValue(row, headerPos); 
	}

	public String getValue(int row, int col) {
		JTableHeader jth = table.getTableHeader();
		int cols = jth.getColumnModel().getColumnCount();
		if (col < 0 || col >= cols || row < 0 || row >= table.getRowCount())
			return null;
		return (String) table.getValueAt(row, col);
	}
	
	public String[] getColumnValues(int col) {
		int cols = table.getColumnCount();
		if(cols == 0 || col >= cols) {
			return null;
		}
		int rows = table.getRowCount();
		if(rows == 0) {
			return null;
		}
		String[] vals = new String[rows];
		for(int i=0; i<rows; i++) {
			vals[i] = getValue(i, col);
		}
		return vals;
	}
	
	public String[] getRowValues(int row) {
		int rows = table.getRowCount();
		if(rows == 0 || row>= rows) {
			return null;
		}
		int cols = table.getColumnCount();
		if(cols == 0) {
			return null;
		}
		String[] vals = new String[cols];
		for(int i=0; i<cols; i++) {
			vals[i] = getValue(row, i);
		}
		return vals;
	}
	
	public boolean hasHeader() {
		return getColumnHeadingsAsString().length() != 0;
	}

	void initContents(String[] headings) {
		DefaultTableModel tableModel = new UndoableTableModel(createInitialCols(headings), 0);
		table = new JTable(tableModel);
		table.setAutoCreateRowSorter(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setRowMargin(2);
		// int numCols = (headings == null || headings.length == 0) ? 30 :
		// headings.length;
		if (showRowIndex) {
			table.getColumnModel().getColumn(0).setWidth(10);
		}
		setColumnHeadings(headings);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);// need, see also updateDisplay
		table.getTableHeader().setVisible(true);
		table.setColumnSelectionAllowed(true);
		table.setRowHeight(25);
		table.addKeyListener(this);
		table.addMouseListener(this);
		table.setFocusable(true);

		table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK),
				"Undo");
		table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK),
				"Redo");

		table.getActionMap().put("Undo", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				UndoableTableModel model = (UndoableTableModel) table.getModel();
				UndoableEdit hist = model.getCurrentIndexEdit(true);
				hist.undo();
			}
		});

		table.getActionMap().put("Redo", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				UndoableTableModel model = (UndoableTableModel) table.getModel();
				UndoableEdit hist = model.getCurrentIndexEdit(false);
				hist.redo();
			}
		});

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		JScrollBar vertical = scrollPane.getVerticalScrollBar();
		InputMap im = vertical.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		im.put(KeyStroke.getKeyStroke("DOWN"), "positiveUnitIncrement");
		im.put(KeyStroke.getKeyStroke("UP"), "negativeUnitIncrement");

		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		add("Center", scrollPane);

		setIconImage(Resources.ResultWinIcon.loadIconFromResource().getImage());
		addMenuBar();
		addPopupMenu();
		setFont();
		pack();
	}

	public boolean isNumeric(String strNum) {
		try {
			Double.parseDouble(strNum);
		} catch (NumberFormatException | NullPointerException nfe) {
			return false;
		}
		return true;
	}

	public void itemStateChanged(ItemEvent e) {
		setFont();
	}

	@Override
	public void keyPressed(KeyEvent e) {
		int key = e.getKeyCode();
		if (key==KeyEvent.VK_BACK_SPACE || key==KeyEvent.VK_DELETE) {
			doClear();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub

	}

	public void load(BufferedReader in, String delimiter/*, or \t*/) throws IOException {
		while (true) {
			String s = in.readLine();
			if (s == null)
				break;
			if(delimiter.equals(",")) {
				String[] vals = s.split(",");
				appendRow(vals);
			}else if(delimiter.equals("\t")) {
				String[] vals = s.split("\t");
				appendRow(vals);
			}else {
				break;
			}
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub

	}
	
	boolean openCSV(String path) {
		OpenDialog od = new OpenDialog("Open CSV File...", path);
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name == null)
			return false;
		path = directory + name;
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory + name));
			load(r, ",");
			r.close();
		} catch (Exception e) {
			Log.logger.severe(e.getMessage());
			return false;
		}
		setTitle(name);
		return true;
	}
	
	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		int id = e.getID();
		if (id == WindowEvent.WINDOW_CLOSING) {
			//do something ?
		}
	}

	/** Converts a y coordinate in pixels into a row index. */
    public int rowIndex(int x, int y) {
    	if(table.getRowCount() < 1) {
    		return -1;
    	}
    	return table.rowAtPoint(new Point(x,y));
    }

	/** Writes all the text in this Table to a file. */
	public void save(PrintWriter pw) {
		int rowCount = table.getRowCount();
		int colCount = table.getColumnCount();
		String labels = getColumnHeadingsAsString();
		if (labels != null && !labels.equals("")) {
			String header = labels;
			header = header.replaceAll("\t", ",");
			pw.println(header);
		}
		for (int i = 0; i < rowCount; i++) {
			String rowValues = "";
			for (int j = 0; j < colCount; j++) {
				String v = (String) table.getValueAt(i, j);
				if (j < colCount - 1) {
					rowValues = rowValues + v + ",";
				} else {
					rowValues = rowValues + v;
				}
			}
			pw.println(rowValues);
		}
	}

	/**
	 * Saves the text in this TextPanel to a file. Set 'path' to "" to display a
	 * "save as" dialog. Returns 'false' if the user cancels the dialog.
	 */
	public boolean saveAsCSV() {
		SaveDialog sd = new SaveDialog("Save Results", "Measure", ".csv");
		String fileName = sd.getFileName();
		if (fileName == null)
			return false;
		String path = sd.getDirectory() + fileName;
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream(path);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
			save(pw);
			pw.close();
		} catch (IOException e) {
			Log.logger.severe("Save As>Text Error..." + "\n" + e.getMessage());
			return false;
		}
		return true;
	}

	/*
	 * todo
	 */
	void saveSettings() {
//		Prefs.set(FONT_SIZE, fontSize);
//		Prefs.set(FONT_ANTI, antialiased.getState());
//		IJ.showStatus("Font settings saved (size="+sizes[fontSize]+", antialiased="+antialiased.getState()+")");
	}

	public void scrollToTop() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				Component[] coms = getComponents();
				for (Component c : coms) {
					if (c instanceof JScrollPane) {
						JScrollPane sp = (JScrollPane) c;
						sp.getVerticalScrollBar().setValue(0);
						break;
					}
				}
			}
		});
	}

	public void selectAll() {
		table.setRowSelectionInterval(0, table.getModel().getRowCount() - 1);
       table.setColumnSelectionInterval(0, table.getColumnModel().getColumnCount() - 1);
	}
	
	public void selectCols(int start, int end) {
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setColumnSelectionAllowed(true);
		table.setColumnSelectionInterval(start, end - 1);
	}

	public void selectRows(int start, int end) {
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setRowSelectionAllowed(true);
		table.setRowSelectionInterval(start, end - 1);
	}

	void setColumnHeadings(String[] headers) {
		if (headers == null || headers.length == 0) {
			return;
		}
		JTableHeader th = table.getTableHeader();
		TableColumnModel tcm = th.getColumnModel();
		int pos = 0;
		for (String h : headers) {
			if (tcm.getColumnCount() < headers.length) {
				tcm.addColumn(new TableColumn(pos));
			}
			TableColumn tc = tcm.getColumn(pos++);
			tc.setHeaderValue(h);
		}
		DefaultTableModel m = (DefaultTableModel) table.getModel();
		m.fireTableDataChanged();
	}

	void setFont() {
		if (font != null)
			table.setFont(font);
		else
			table.setFont(new Font("SanSerif", Font.PLAIN, fontSize));
		fireTableDataChanged();
	}
	
	/** Sets the value of the given column and row, where
	where 0&lt;=row&lt;size(). If the specified column does 
	not exist, it is created.*/
	public void setValue(String columnName, int row, String value) {
		if (columnName == null)
			throw new IllegalArgumentException("Column is null");
		int col = -1;
		try {
			col = table.getColumnModel().getColumnIndex(columnName);
			setValue(row, col, value);
		} catch (IllegalArgumentException e) {
			// if not exists in headers
			DefaultTableModel model = (DefaultTableModel) table.getModel();
			model.addColumn(columnName);
			int headerPos = table.getColumnModel().getColumnIndex(columnName);
			setValue(row, headerPos, value);
		}
	}

	public void setValue(int row, int col, String v ) {
		if(getRowCount() == row) {
			Object[] blankRow = null;
			appendRow(blankRow);
		}
		v = adjustDecimalPlace(v);
		table.setValueAt(v, row, col);
	}
	
	/**
	 * Return adjusted decimal place value if string value is double.
	 * @param v
	 * @return
	 */
	String adjustDecimalPlace(String v) {
		if(v == null) {
			return null;
		}
		try {
		    Integer.parseInt(v);
		    //this is a int (not double)
		    return v;
		} catch (NumberFormatException e) {
			//do nothing, go next parceDouble
		}
		try {
		    double dv = Double.parseDouble(v);
		    //this is a double
		    return IJ.d2s(dv, decimalPlace);
		} catch (NumberFormatException e) {
			//other information (not integer or double)
			return v;
		}
	}
		
	/**
	 * TODO
	 * @return
	 */
	public ResultsTable summarize() {
		return null;
	}

	public void updateRow(int row, ArrayList<String> vals) {
		if (row < 0 || row > table.getRowCount() - 1) {
			return;
		}
		if (vals == null) {
			//set blank
			int cols = getColumnCount();
			if(showRowIndex) {
				for (int i = 1; i < cols; i++) {
					setValue(row, i, null);
				}
			}else {
				for (int i = 0; i < cols; i++) {
					setValue(row, i, null);
				}
			}
			return;
		}
		int cols = getColumnCount();
		if(showRowIndex) {
			for (int i = 1; i < cols; i++) {
				setValue(row, i, vals.get(i-1));
			}
		}else {
			for (int i = 0; i < cols; i++) {
				setValue(row, i, vals.get(i));
			}
		}
	}
}
