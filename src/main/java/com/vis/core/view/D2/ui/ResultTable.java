package com.vis.core.view.D2.ui;

import java.awt.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import java.awt.datatransfer.*;
import ij.io.SaveDialog;
import ij.measure.*;
import ij.gui.*;


/**
This is an unlimited size text panel with tab-delimited,
labeled and resizable columns. It is based on the hGrid
class at
    http://www.lynx.ch/contacts/~/thomasm/Grid/index.html.
*/
@SuppressWarnings("serial")
public class ResultTable implements MouseListener, KeyListener,  ClipboardOwner, ActionListener, TableModelListener {

	static final int DOUBLE_CLICK_THRESHOLD = 650;
	// data
	Vector<String> sColHead;
	Vector<?> vData;
	// scrolling
	JScrollPane scrollPane;
	int iSbWidth,iSbHeight;
	boolean bDrag;
	int iXDrag,iColDrag;

	boolean headings = true;//add header
	String title = "";
	String labels;
	
	KeyListener keyListener;
		
  	JTable table;
  	JPopupMenu pm;
	long mouseDownTime;
    String filePath;
    boolean unsavedLines;
    String searchString;
    JMenu fileMenu, editMenu;
    boolean menusExtended;
    boolean saveAsCSV;
    String[] defaultColNames = new String[] {"A","B","C","D","E","F","G"};


	/** Constructs a new TextPanel. */
	private ResultTable(int initHeaderNum) {
		if (initHeaderNum < 1) {
			initHeaderNum = 30;
		}
		DefaultTableModel tableModel = new DefaultTableModel(createInitialCols(initHeaderNum), 0);
		table = new JTable(tableModel);
		table.setAutoCreateRowSorter(true);
		table.getModel().addTableModelListener(this);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		// set gap between rows, api in JTable
		table.setRowMargin(2);
		// set gap between columns, api in TableColumnModel
//		table.getColumnModel().setColumnMargin(int);
		// convenience for setting both row and column gaps
//		table.setIntercellSpacing(Dimension)
		for(int c=0;c<initHeaderNum;c++) {
			table.getColumnModel().getColumn(c).setMinWidth(30);
		}
		table.setAutoResizeMode( JTable.AUTO_RESIZE_OFF);//need, see also updateDisplay
		scrollPane = new JScrollPane(table);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.validate();
		JScrollBar vertical = scrollPane.getVerticalScrollBar();
		InputMap im = vertical.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		im.put(KeyStroke.getKeyStroke("DOWN"), "positiveUnitIncrement");
		im.put(KeyStroke.getKeyStroke("UP"), "negativeUnitIncrement");
		
		addPopupMenu();
		addMouseListener(this);
		addKeyListener(this);
	}

	/** Constructs a new TextPanel. */
	public ResultTable(String title, int initHeaderNum) {
		this(initHeaderNum);
		this.title = title;
	}
	
	private String[] createInitialCols(int n) {
		String[] initHeader = new String[n];
		for(int i=0;i<n;i++) {
			initHeader[i] = "";
		}
		return initHeader;
	}
	
	public JScrollPane getPane() {
		return scrollPane;
	}

	void addPopupMenu() {
		pm = new JPopupMenu();
		addPopupItem("Save As...");
		pm.addSeparator();
//		addPopupItem("Cut");
		addPopupItem("Copy");
		addPopupItem("Clear");
		addPopupItem("Select All");
		pm.addSeparator();
//		addPopupItem("Clear Results");
		addPopupItem("Summarize");
		addPopupItem("Distribution...");
		//addPopupItem("Set Measurements...");
		table.add(pm);
	}
	
//	private void extendMenus() {
//	pm.addSeparator();
//	addPopupItem("Rename...");
//	addPopupItem("Duplicate...");
//	addPopupItem("Apply Macro...");
//	addPopupItem("Sort...");
//	addPopupItem("Plot...");
//	if (fileMenu!=null) {
//		fileMenu.add("Rename...");
//		fileMenu.add("Duplicate...");
//	}
//	if (editMenu!=null) {
//		editMenu.addSeparator();
//		editMenu.add("Apply Macro...");
//	}
//	menusExtended = true;
//}

	void addPopupItem(String s) {
		JMenuItem mi = new JMenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	/**
	Clears this TextPanel and sets the column headings to
	those in the tab-delimited 'headings' String. Set 'headings'
	to "" to use a single column with no headings.
	*/
	public void setColumnHeadings(String labels) {
//		if(labels.equals(this.labels)) {
//			return;
//		}
		if(labels == null || labels.equals("")) {
			return;
		}
		
		if (labels.endsWith("\t")) {
			labels = labels.substring(0, labels.length()-1);
		}
		this.labels = labels;
		sColHead = new Vector<>();
		Collections.addAll(sColHead, this.labels.split("\t"));
		JTableHeader th = table.getTableHeader();
		TableColumnModel tcm = th.getColumnModel();
		int pos = 0;
		for (String h : sColHead) {
			if(tcm.getColumnCount()<sColHead.size()) {
				tcm.addColumn(new TableColumn(pos));
			}
			TableColumn tc = tcm.getColumn(pos++);
			tc.setHeaderValue(h);
		}
		//delete initial dummy column
		for(int i=0;i<tcm.getColumnCount();i++) {
			if(i >= sColHead.size()) {
				tcm.removeColumn(tcm.getColumn(i));
			}
		}
		DefaultTableModel m = (DefaultTableModel) table.getModel();
		m.fireTableDataChanged();
//		m.fireTableStructureChanged();//be careful, header colapse...
		th.repaint();
		table.repaint();
	}

	/** Returns the column headings as a tab-delimited string. */
	public String getColumnHeadingsAsString() {
		return labels==null ? "":labels;
	}
	
	public String[] getColumnHeadings() {
		if(labels==null && sColHead.size() < 1){
			return null;
		}
		return sColHead.toArray(new String[sColHead.size()]);
	}

	public void updateColumnHeadings(String labels) {
		setColumnHeadings(labels);
	}

	public void setFont(Font font) {
		table.setFont(font);
		updateDisplay();
	}
	
	public void scrollToTop() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				scrollPane.getVerticalScrollBar().setValue(0);
			}
		});
	}
	
	public boolean isNumeric(String strNum) {
        try {
           Double.parseDouble(strNum);
        } catch (NumberFormatException | NullPointerException nfe) {
            return false;
        }
        return true;
    }

	/** Adds strings contained in an ArrayList to the end of this Table. */
	/*
	 * see, ResultWindow:append(String)
	 */
	public void appendRow(ArrayList<?> row) {
		if (row==null || row.size() < 1) {
			return;
		}
		Object[] rowVals = row.toArray(new String[row.size()]);
		DefaultTableModel model = (DefaultTableModel) table.getModel();
		model.addRow(rowVals);
//		model.insertRow(model.getRowCount(), rowVals);
		
//		for (int i=0; i<sColHead.size(); i++) {
//			int lastRow = table.getRowCount();
//			String val = String.valueOf(row.get(i));
//			table.getModel().setValueAt(val, lastRow, i);
//			/*
//			 * if you need case-by-case
//			 */
////			if(isNumeric(val)) {
////				table.getModel().setValueAt(String.valueOf(val), lastRow, i);
////			}else {
////				table.getModel().setValueAt(val, lastRow, i);
////			}
//			
//		}
		updateDisplay();
	}
	
	public void updateRow(int row, ArrayList<?> rowVals) {
		if (rowVals==null || rowVals.size() < 1) {
			return;
		}
		for (int i=0; i<sColHead.size(); i++) {
			String val = String.valueOf(rowVals.get(i));
			table.getModel().setValueAt(val, row, i);
			/*
			 * if you need case-by-case
			 */
//			if(isNumeric(val)) {
//				table.getModel().setValueAt(String.valueOf(val), lastRow, i);
//			}else {
//				table.getModel().setValueAt(val, lastRow, i);
//			}
			
		}
		updateDisplay();
	}

	public void updateDisplay() {
		DefaultTableModel m = (DefaultTableModel) table.getModel();
		m.fireTableDataChanged();
//		m.fireTableStructureChanged();//be careful, table header collapse...
//		table.setPreferredSize(new Dimension(table.getColumnCount()*2, table.getRowCount()*10));
//		Insets posInFrame = scrollPane.getParent().getInsets();
//		table.setBounds(posInFrame.left, posInFrame.top, table.getColumnCount()*20, table.getRowCount()*10);
		table.repaint();
		scrollPane.revalidate();
	}

	String getValue(int row, int col) {
		if (col<0||col>=sColHead.size()||row<0||row>=table.getRowCount())
			return null;
		return (String)table.getValueAt(row, col);
	}
	


	/** Unused keyPressed and keyTyped events will be passed to 'listener'.*/
	public void addKeyListener(KeyListener listener) {
		keyListener = listener;
	}

	public void addMouseListener(MouseListener listener) {
		table.addMouseListener(listener);
	}

	public void keyPressed(KeyEvent e) {
		int key = e.getKeyCode();
		if (key==KeyEvent.VK_BACK_SPACE || key==KeyEvent.VK_DELETE)
			clearSelection();
//		else if (key==KeyEvent.VK_UP)//DO NOT USE
//		else if (key==KeyEvent.VK_DOWN)
		else if (keyListener!=null&&key!=KeyEvent.VK_S&& key!=KeyEvent.VK_C && key!=KeyEvent.VK_X
		&& key!=KeyEvent.VK_A && key!=KeyEvent.VK_F && key!=KeyEvent.VK_G)
			keyListener.keyPressed(e);
	}

	public void keyReleased (KeyEvent e) {}

	public void keyTyped (KeyEvent e) {
		if (keyListener!=null)
			keyListener.keyTyped(e);
	}

	public void actionPerformed (ActionEvent e) {
		String cmd = e.getActionCommand();
		doCommand(cmd);
	}

 	void doCommand(String cmd) {
 		if (cmd==null) {
 			return;
 		}
		if (cmd.equals("Save As...")) {
			saveAsCSV();
//		}else if (cmd.equals("Cut")) {
//			cutSelection();
		}else if (cmd.equals("Copy")) {
			copySelection();
		}else if (cmd.equals("Clear")) {
			doClear();
		}else if (cmd.equals("Select All")) {
			selectAll();
		}else if (cmd.equals("Find...")) {
			find(null);
		}else if (cmd.equals("Summarize")) {
			summarize();
//			IJ.doCommand("Summarize");
		}else if (cmd.equals("Distribution...")) {
			distribution();
//			IJ.doCommand("Distribution...");
		}
			
	}

	private void find(String s) {
		if (s==null) {
			GenericDialog gd = new GenericDialog("Find (Perfect matching)...", null);
			gd.addStringField("Find: ", searchString, 20);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			s = gd.getNextString();//.trim()
		}
		if (s.equals("")) {
			return;
		}
			
		boolean found = false;
		int rows = table.getRowCount();
		int cols = table.getColumnCount();
		/*
		 * CellRenderer was shared in Column, not cell by cell
		 */
		for (int c=0; c<cols; c++) {
			CustomTableCellRenderer renderer = new CustomTableCellRenderer(s);
			for (int r=0; r<rows; r++) {
				String v = (String) table.getValueAt(r, c);
				if(v.equals(s)) {
					renderer.setRowColor(r, Color.cyan);
	                found = true;
				}else {
//					CustomTableCellRenderer renderer = new CustomTableCellRenderer(s);
//					renderer.setRowColor(r,null);
	                table.getColumnModel().getColumn(c).setCellRenderer(null);
//	                found = false;
				}
			}
			table.getColumnModel().getColumn(c).setCellRenderer(renderer);
		}
		if (!found) {
			Toolkit.getDefaultToolkit().beep();
		}
		updateDisplay();
	}
	
	/**
	 * TODO
	 * @return
	 */
	public ResultsTable summarize() {
		return null;
	}
	
	/**
	 * TODO
	 * @return
	 */
	public ResultsTable distribution() {
		return null;
	}

    /** Converts a y coordinate in pixels into a row index. */
    public int rowIndex(int x, int y) {
    	if(table.getRowCount() < 1) {
    		return -1;
    	}
    	return table.rowAtPoint(new Point(x,y));
    }
    
    private void selectRows(JTable table, int start, int end) {
        // Use this mode to demonstrate the following examples
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        // Needs to be set or rows cannot be selected
        table.setRowSelectionAllowed(true);
        // Select rows from start to end if start is 0 we change to 1 or leave it (used to preserve coloums headers)
        table.setRowSelectionInterval(start, end - 1);
    }
    
    private void selectAll() {
    	table.setRowSelectionInterval(0, table.getModel().getRowCount()-1);
    }

	/**
	Copies the current selection to the system clipboard.
	Returns the number of characters copied.
	*/
	public void copySelection() {
		StringBuffer buffer = new StringBuffer();
	    int numCols = table.getSelectedColumnCount();
	    int numRows = table.getSelectedRowCount();
	    int[] rowsSelected = table.getSelectedRows();
	    int[] colsSelected = table.getSelectedColumns();
		
	    for(int i = 0; i < numRows; i++) {
            for(int j = 0; j < numCols; j++) {
                // copy val all cell
                buffer.append(table.getValueAt(rowsSelected[i], colsSelected[j]));
                if(j < numCols-1) {
                	buffer.append("\t");
                }
            }
            buffer.append("\n");
        }
	    //send to clip
        StringSelection ss = new StringSelection(buffer.toString());
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        clip.setContents(ss, ss);
	}

	/** Implements the Clear command. */
	public void doClear() {
		if(table.getSelectedRowCount() == 0) {
			selectAll();
			clearSelection();
		}else {
			clearSelection();
		}
	}

	/** Deletes the selected lines. */
	public void clearSelection() {
		int[] selectedRows = table.getSelectedRows();
		for(int i=0;i<selectedRows.length;i++) {
			((DefaultTableModel)table.getModel()).removeRow(i);
		}
		table.repaint();
	}

	/** Creates a selection and insures that it is visible. */
	public void setSelection (int startLine, int endLine) {
		selectRows(table, startLine, endLine);
	}

	/** Writes all the text in this Table to a file. */
	public void save(PrintWriter pw) {
		int rowCount = table.getRowCount();
		int colCount = table.getColumnCount();
		if (labels!=null && !labels.equals("")) {
			String header = labels;
			header = header.replaceAll("\t",",");
			pw.println(header);
		}
		for (int i=0; i<rowCount; i++) {
			String rowValues = "";
			for (int j=0; j<colCount; j++) {
				String v = (String)table.getValueAt(i, j);
				if(j < colCount-1) {
					rowValues = rowValues + v + ",";
				}else {
					rowValues = rowValues + v;
				}
			}
			pw.println(rowValues);
		}
	}

	/** Saves the text in this TextPanel to a file. Set 'path' to "" to
	 * display a "save as" dialog. Returns 'false' if the user cancels
	 * the dialog.
	*/
	public boolean saveAsCSV() {
		SaveDialog sd = new SaveDialog("Save Table", title, ".csv");
		String fileName = sd.getFileName();
		if (fileName==null)
			return false;
		String path = sd.getDirectory() + fileName;
		PrintWriter pw = null;
		try {
			FileOutputStream fos = new FileOutputStream(path);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			pw = new PrintWriter(bos);
			save(pw);
			pw.close();
		}
		catch (IOException e) {
			System.out.println("Save As>Text Error..."+"\n"+e.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public void tableChanged(TableModelEvent e) {}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents) {}

	@Override
	public void mouseClicked(MouseEvent e) {}
	
	@Override
	public void mousePressed (MouseEvent e) {
		int x=e.getX(), y=e.getY();
		if (e.isPopupTrigger() || e.isMetaDown()) {
			pm.show(e.getComponent(),x,y);
		}else {
 			handleDoubleClick();
 		}
	}

	void handleDoubleClick() {
		//do something
	}

	@Override
	public void mouseReleased(MouseEvent e) {
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

}