package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.OverlayLayout;
import javax.swing.RepaintManager;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.html.HTMLDocument.HTMLReader.IsindexAction;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.function.DicomImporter;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.Utils;
import com.vis.db.DatabaseHandler;

public class LocalDBStateCellRendererableEditor extends DefaultCellEditor implements TableCellRenderer, TableCellEditor{

	private static final long serialVersionUID = -4324960997246613616L;
		
	int mode = -1;
	int state = -1;
	
	private JLabel localLabel = new JLabel("",SwingConstants.CENTER);
	private JLabel linkLabel = new JLabel("",SwingConstants.CENTER);
	ArrayList<ImportingStateContext> importingList = new ArrayList<>();
	ArrayList<ImportingStateContext> completionList = new ArrayList<>();
	int progress, total;
	JTable table;
	
	public LocalDBStateCellRendererableEditor(JTable table, JTextField holder) {
		super(holder);
		this.table = table;
		localLabel.setIcon(Resources.ArchivedIcon.loadIconFromResource());
		localLabel.setEnabled(false);// NEED for double click repainting
		localLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent me) {
				// TODO Auto-generated method stub
				fireEditingStopped();
			}

			@Override
			public void mouseClicked(MouseEvent me) {
				// do something ??
				fireEditingStopped();
			}
		});
		linkLabel.setIcon(Resources.LinkIcon.loadIconFromResource());
		linkLabel.setEnabled(false);// NEED for double click repainting
	}

	/*
	 * (non-Javadoc)
	 * @see javax.swing.DefaultCellEditor#getTableCellEditorComponent(javax.swing.JTable, java.lang.Object, boolean, int, int)
	 * if clicked(when enter edit mode in table), execute it.
	 */
	@Override
	public Component getTableCellEditorComponent(JTable table, Object obj, boolean selected, int row, int col) {
//		String lbl = (obj==null) ? "":obj.toString();
		ImportingStateContext isc = getStateImportingCellAt(row, col);
		if (isc != null) {
			isc.getSuspendButton().addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					// suspend current import thread
					isc.getDicomImporter().setSuspended(true);
					int res = JOptionPane.showConfirmDialog(table, "Would you cancel this import ?", "Cancel Importing",
							JOptionPane.YES_NO_OPTION);
					if (res == JOptionPane.YES_OPTION) {
						// stop
						isc.getDicomImporter().setStopped(true);
						isc.getDicomImporter().stopImport();
						importInterupted(isc);
					} else {
						// resume
						isc.getDicomImporter().resumeImport();
						fireEditingStopped();
					}
				}
			});
			isc.getSuspendButton().setText("Suspend");// NEEDED
			return isc.getProgressBar();// keep return progressbar which added cancelBtn.
		} else {// just wait as local
			return localLabel;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see javax.swing.table.TableCellRenderer#getTableCellRendererComponent(javax.swing.JTable, java.lang.Object, boolean, boolean, int, int)
	 * This is set face component that just present.do not set action.
	 */
	@Override
	public Component getTableCellRendererComponent(JTable table, Object obj, boolean selected, boolean focused, int row,
			int col) {
		ImportingStateContext isc = null;
		if (WindowManager.getMainScreen().getLocalTreeTable().getArchivedColumnPosition() == col) {
			isc = getStateImportingCellAt(row, col);
		}
		if (isc != null) {
			return isc.getProgressBar();
		} else {// just wait as local
			DICOMTreeTable tbl = (DICOMTreeTable) table;
			DICOMNode node = tbl.nodeForRow(row);
			if (node == null) {
				return localLabel;
			}
			if (node.getLevel() == DICOMNode.IMAGE) {
				if (DatabaseHandler.getInstance().isInstanceSavedAsLink(node.getData(DICOMNode.PatientID),
						node.getData(DICOMNode.StudyInstanceUID), node.getData(DICOMNode.SeriesInstanceUID),
						node.getData(DICOMNode.SOPInstanceUID))) {
					return linkLabel;
				}
			}
			return localLabel;
		}
	}
	
	private Icon getIcon(String iconName) {
		InputStream stream = getClass().getResourceAsStream(iconName);
//		FileInputStream stream = null;
//		try {
//			stream = new FileInputStream(new java.io.File(iconName));
//		} catch (FileNotFoundException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		ImageIcon icon = null;
		try {
			icon = new ImageIcon(ImageIO.read(stream));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return icon;
	}
	
	public ImportingStateContext getParticularImportingStateContext(String suid) {
		for(ImportingStateContext isc:importingList) {
			if(isc.getSuid().equals(suid)) {
				return isc;
			}
		}
		return null;
	}
	
	public void setCellStateLocationInCurrentTableView(String suid, int importingRow,int importingCol) {
		if(importingList.size()<1) {
			Log.logger.info("please do addImportingState first.");
			return;
		}
		for(ImportingStateContext isc:importingList) {
			if(isc.getSuid().equals(suid)) {
				isc.setImportingRow(importingRow);
				isc.setImportingCol(importingCol);
			}
		}
		super.fireEditingStopped();
	}
	
	public void addImportingState(String suid, int total, DicomImporter importer) {
		ImportingStateContext isc = new ImportingStateContext(suid,importer);
		isc.setTotal(total);
		importingList.add(isc);
	}
	
	public void setProgressAt(String suid, int row, int col,int progress) {
		ImportingStateContext context = getParticularImportingStateContext(suid);
		if(context == null) {
			return;
		}
		context.setImportingRow(row);
		context.setImportingCol(col);
		if (progress == context.getTotal() - 1) {
			importingIsDone(suid);
			return;
		}
		//see, DicomImporter::updateProgress
//		context.getProgressBar().setValue(progress);
//		JButton btn = (JButton) context.getProgressBar().getComponent(0);
//		btn.setText((progress) + " / " + context.getTotal());
//		btn.repaint();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				context.getProgressBar().setValue(progress);
				JButton btn = (JButton) context.getProgressBar().getComponent(0);
				btn.setText((progress) + " / " + context.getTotal());
				btn.repaint();
				context.getProgressBar().repaint();
			}
		});
	}
	
	private ImportingStateContext getStateImportingCellAt(int row, int col) {
		if(importingList.size() == 0) {
			return null;
		}
		for (int i = 0; i < importingList.size(); i++) {
			ImportingStateContext isc = importingList.get(i);
			int r = isc.getImportingRow();
			int c = isc.getImportingCol();
			if (r == row && c == col) {
				return isc;
			}
		}
		return null;
	}
	
//	private JButton getParticularRowCancelButton(int row) {
//		for (int i = 0; i < importingList.size(); i++) {
//			ImportingStateContext isc = importingList.get(i);
//			if (isc.getImportingCellInfo()[0] == row) {
//				return isc.getSuspendButton();
//			}
//		}
//		return null;
//	}
	
	public void importInterupted(ImportingStateContext isc) {
//		importingList.remove(isc);//will get error ???//https://qiita.com/ukitiyan/items/adec43ea77cb78169e80
		completionList.add(isc);
		int sum = 0;
		for (ImportingStateContext completed : completionList) {
			if (importingList.contains(completed)) {
				sum++;
			}
		}
		//init
		if(importingList.size() == sum) {
			completionList = null;
			importingList = null;
			completionList = new ArrayList<ImportingStateContext>();
			importingList = new ArrayList<ImportingStateContext>();
		}
		//debug
		if(Utils.isDebug) {
			Log.logger.info("import interupted : " + isc.getSuid());
			Log.logger
					.info("After cleanup, still remain importing list size is " + importingList.size());
		}
		
		isc = null;//for renderer 
//		ApplicationContext.getInstance().getMainScreen().loadLocalStudies();
		fireEditingStopped();//repaint
		table.repaint();
	}
	
	public void importingIsDone(String suid) {
		ImportingStateContext isc = getParticularImportingStateContext(suid);
//		importingList.remove(isc);//will get error ???//https://qiita.com/ukitiyan/items/adec43ea77cb78169e80
		completionList.add(isc);
		int sum = 0;
		for (ImportingStateContext completed : completionList) {
			if (importingList.contains(completed)) {
				sum++;
			}
		}
		//init
		if(importingList.size() == sum) {
			completionList = null;
			importingList = null;
			completionList = new ArrayList<ImportingStateContext>();
			importingList = new ArrayList<ImportingStateContext>();
		}
		//debug
		if(Utils.isDebug) {
			Log.logger.info("import completed : " + suid);
			Log.logger
					.info("After cleanup, still remain importing task list size is " + importingList.size());
		}
		
		fireEditingStopped();
		table.repaint();
		
		MainScreen.importing = false;
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					//wait for next importing started
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(!MainScreen.importing) {
					DICOMTreeTable treeTable = WindowManager.getMainScreen().getLocalTreeTable();
					treeTable.getTableHeader().setEnabled(true);
				}
			}
		});
	}
}
