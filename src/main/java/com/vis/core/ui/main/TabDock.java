package com.vis.core.ui.main;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTableModel;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTableModelAdapter;
import com.vis.core.ui.main.dcmtreetable.TableColumnResizer;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class TabDock extends JToolBar implements AncestorListener{
	
	public boolean home = false;
	public JTabbedPane pane;
	public String title = "";
	private JPanel basePanel;
	private JPanel treeInfoPanel;
	private JCheckBox keepTopChck;
	private JScrollPane tableScroll;
	public boolean floating = false;
	private JLabel studyCountLbl;

	/*
	 * Tab index was changed when dock/redock, so do not use it to control.
	 */
	public TabDock(boolean home, String nickname, JCheckBox keepTop, JScrollPane tableScroll, JTabbedPane pane) {
		super(nickname);//setName
		this.title = nickname;
		this.home = home;
		if(home) {
			this.title = "HOME";
		}
		this.tableScroll = tableScroll;
		this.pane = pane;
//		setPreferredSize(new Dimension(pane.getWidth(), pane.getHeight()));//DO NOT USE
		addAncestorListener(this);
		setUpBasePanel(keepTop);
		add(tableScroll);
	}
	
	private void setUpBasePanel(JCheckBox keepTop) {
		keepTopChck = keepTop;
		basePanel = new JPanel();
		basePanel.setLayout(new BorderLayout());
//		basePanel.setPreferredSize(new Dimension(pane.getWidth(), pane.getHeight()));//DO NOT USE
		treeInfoPanel = new JPanel();
		treeInfoPanel.setLayout(new BorderLayout());
		treeInfoPanel.add(keepTopChck,BorderLayout.EAST);
		studyCountLbl = new JLabel("- studies");
		treeInfoPanel.add(studyCountLbl, BorderLayout.WEST);
		basePanel.add(treeInfoPanel,BorderLayout.NORTH);
	}
	
	public void writeKeepTopState() {
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle, title);
	}
	
	public void setKeepTopSate(boolean isTop) {
		keepTopChck.setSelected(isTop);
		keepTopChck.repaint();
	}
	
	public boolean isHomeTab() {
		return home;
	}
	
	public String getTitle() {
		return super.getName();
	}
	
	public DICOMTreeTable getDICOMTreeTable() {
		JViewport viewport = tableScroll.getViewport(); 
		return (DICOMTreeTable)viewport.getView(); 
	}
	
	public synchronized void updateTreeTable(DICOMNode newRoot) {
		JViewport viewport = tableScroll.getViewport(); 
		DICOMTreeTable treeTable = (DICOMTreeTable)viewport.getView();
		this.studyCountLbl.setText(getStudyCount(newRoot) + " studies");
		treeInfoPanel.repaint();
		int[] selectedRows = treeTable.getSelectedRows();//using table no good	
		//get already opened tree node locations
		ArrayList<Integer> willExpand = treeTable.getExpandedRowsPos();
		((DICOMTreeTableModel) treeTable.getTree().getModel()).setRoot((Object)newRoot);
		((DICOMTreeTableModel) treeTable.getTree().getModel()).reload(newRoot);
		TableColumnResizer.adjustColumnPreferredWidths(treeTable);
		((DICOMTreeTableModelAdapter)treeTable.getModel()).fireTableDataChanged();
//		treeTable.repaint();
		//re-expand tree nodes
		for (int i = 0; i < willExpand.size(); i++) {
			treeTable.getTree().expandRow(willExpand.get(i));
		}
		//re-select node
		//table approach
		for(int row:selectedRows) {
			treeTable.changeSelection(row, 0, true, false);//row,col,toggle,extend
			//treeTable.selectRow(selectedRows);//DO NOT USE
		}
		treeTable.setLastColumnOrder();
//		ApplicationContext.getInstance().getMainScreen().constructQRTreeTable(treeTable,newRoot);
	}
	
	@SuppressWarnings("unused")
	private int getStudyCount(DICOMNode root) {
		if(root == null || root.getChildCount() < 1) {
			return 0;
		}
		int numOfPt = 0;
		int numOfStudy = 0;
		int numOfSeries = 0;
		int numOfImg = 0;
		List<DICOMNode> childs = root.getChildren();
		for (int i = 0; i < childs.size(); i++) {
			if(childs.get(i).getLevel() == DICOMNode.PATIENT) {
				numOfPt++;
			}else if (childs.get(i).getLevel() == DICOMNode.STUDY) {
				numOfStudy ++;
			}else if(childs.get(i).getLevel() == DICOMNode.SERIES) {
				numOfSeries ++;
			}else if(childs.get(i).getLevel() == DICOMNode.IMAGE) {
				numOfImg ++;
			}
		}
		return numOfStudy;
	}
	
	@Override
	public Component add(Component comp) {
//		super.setPreferredSize(new Dimension(10, 10));
		basePanel.setPreferredSize(new Dimension(10,10));
		basePanel.add(comp,BorderLayout.CENTER);
		super.add(basePanel);
		return basePanel;
	}

	@Override
	public void ancestorAdded(AncestorEvent arg0) {
		if(pane.getComponentCount()<0) {
			return;
		}
		if (SwingUtilities.getWindowAncestor(this) == WindowManager.getWindow(ConfigInfo.MainScreen.toString())) {
			if(Utils.isDebug) {
				Log.logger.info("debug : In Main Frame:"+ " "+title);
			}
			// tab icon and name rebuild
			/*
			 * フローティングするとタブが消失する。
			 */
			if(!floating) {
				//do nothing
			}else {
				//when re-docking, replaced last tab automatically.
				int pos = pane.getComponentCount()-1;
				if (home) {
					pane.setTitleAt(pos, title);
					pane.setIconAt(pos, Resources.LocalIcon.loadIconFromResource());
				} else {
					pane.setTitleAt(pos, title);
					pane.setIconAt(pos, Resources.QRIcon.loadIconFromResource());
				}
				floating = false;
			}
			
		} else {
			if(Utils.isDebug) {
				Log.logger.info("debug : Maybe floating:"+ " "+title);
			}
			Component win = SwingUtilities.getWindowAncestor(this);
			if (win instanceof JDialog) {
				/* OK */
				JDialog floatingFrame = (JDialog) SwingUtilities.getWindowAncestor(this);
				if(!floatingFrame.isResizable()) {
					floatingFrame.setResizable(true);
				}
				int w = pane.getWidth();
				int h = pane.getHeight();
				System.out.println(w +" "+h);
				if(w < 100) {
					w = 150;
				}
				if(h < 100) {
					h = 150;
				}
				/* to avoid floating dialog minimize */
				/*
				 * Maybe, it is occurred when table data empty.
				 * Component resized correctly by re-opened floating frame.
				 */
				if(floatingFrame.getWidth() < w) {
					floatingFrame.setPreferredSize(new Dimension(w, h));
					floatingFrame.revalidate();
					floatingFrame.repaint();
				}
				floating = true;
				/* Then, catch removed act by pane in componentlistener in manager*/

				//following code is just memo,,,
				/* cannot do this */
//				floatingFrame.setVisible(false);
//				floatingFrame.setUndecorated(true);
//				floatingFrame.getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
				/* cannot do this end */

//				JFrame shadow = new JFrame(floatingFrame.getTitle());
//				shadow.setContentPane(floatingFrame.getContentPane());
//				shadow.pack();
//				shadow.setVisible(true);
//				
//				shadow.createBufferStrategy(4);
//				BufferStrategy bs = shadow.getBufferStrategy();
//				bs = floatingFrame.getBufferStrategy();
//				/*
//				 * javax.swing.plaf.basic.BasicToolBarUI$FrameListener
//				 * javax.swing.BufferStrategyPaintManager$BufferInfo
//				 */
//				WindowListener[] wls = floatingFrame.getWindowListeners();
//				for(WindowListener wl:wls) {
////					System.out.println(wl.getClass().getName());
//					shadow.addWindowListener(wl);
//				}
			}
		}
	}

	@Override
	public void ancestorMoved(AncestorEvent arg0) {}

	@Override
	public void ancestorRemoved(AncestorEvent arg0) {}
	
}