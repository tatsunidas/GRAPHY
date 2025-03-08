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
package com.vis.core.ui.main;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicToolBarUI;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.ui.main.dcmtreetable.DICOMTreeTable;
import com.vis.core.ui.main.dcmtreetable.TableColumnResizer;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.util.PropertiesUtil;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class TabDock extends JToolBar{ //implements AncestorListener{
	
	private boolean isHome = false;
	private final String title;//nickname
	private JPanel treeInfoPanel;
	private JCheckBox keepTopChck;
	private JScrollPane tableScroll;
	
	private boolean floating = false;
	JDialog floatingDialog;
	TabDock thisDock;
	
	private JLabel studyCountLbl;
	
//	private TreeTableDockManager manager;

	/*
	 * Tab index was changed when dock/redock, so do not use it to control.
	 */
	public TabDock(String nickname, JCheckBox keepTop, JScrollPane tableScroll, TreeTableDockManager manager) {
		super(nickname);//setName
		this.title = nickname;
		this.isHome = nickname.equals(TreeTableDockManager.homeTabName);
		this.tableScroll = tableScroll;
//		this.manager = manager;
//		addAncestorListener(this);
		JPanel base = setUpBasePanel(keepTop);
		setFloatable(true);
		setLayout(new BorderLayout());
		add(base);
		
		thisDock = this;
		
		addPropertyChangeListener("ancestor", evt -> {
			if (((BasicToolBarUI) getUI()).isFloating()) {
				System.out.println("drag out tabdock");
				updateFloatingStatus(true);
				Component win = SwingUtilities.getWindowAncestor(this);
				if (win instanceof JDialog) {
					if (floatingDialog == null) {
						floatingDialog = (JDialog) win;
						floatingDialog.setResizable(true);
					}
				}
			} else {
				// IMPORTANT
				if (floatingDialog == null || !isFloating()) {
					return;
				}
				if (isFloating()) {
					System.out.println("back to dock");
				}
				// then set false
				updateFloatingStatus(false);
				floatingDialog = null;
				// when re-docking, replaced last tab automatically.
				int count = manager.getComponentCount();
				final int pos = count;
				/*
				 * IMPORTANT use SwingUtilities.invokeLater
				 */
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						manager.setTitleAt(pos, title);
						if (isHome) {
							manager.setIconAt(pos, Resources.LocalIcon.loadIconFromResource());
						} else {
							manager.setIconAt(pos, Resources.QRIcon.loadIconFromResource());
						}
					}
				});
			}
		});
	}
	
	private JPanel setUpBasePanel(JCheckBox keepTop) {
		keepTopChck = keepTop;
		JPanel basePanel = new JPanel();
		basePanel.setLayout(new BorderLayout());
		treeInfoPanel = new JPanel();
		treeInfoPanel.setLayout(new BorderLayout());
		treeInfoPanel.add(keepTopChck,BorderLayout.EAST);
		studyCountLbl = new JLabel("- studies");
		treeInfoPanel.add(studyCountLbl, BorderLayout.WEST);
		basePanel.add(treeInfoPanel,BorderLayout.NORTH);
		basePanel.add(tableScroll,BorderLayout.CENTER);
		return basePanel;
	}
	
	public void writeKeepTopState() {
		PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle, title);
	}
	
	public void setKeepTopSate(boolean isTop) {
		keepTopChck.setSelected(isTop);
	}
	
	public boolean isHomeTab() {
		return isHome;
	}
	
	public boolean isFloating() {
		return floating;
	}
	
	public String getNickname() {
		return super.getName();
	}
	
	public DICOMTreeTable getDICOMTreeTable() {
		JViewport viewport = tableScroll.getViewport(); 
		return (DICOMTreeTable)viewport.getView(); 
	}
	
	private void updateFloatingStatus(boolean bool) {
		this.floating = bool;
	}
	
	public synchronized void updateTreeTable(DICOMNode newRoot) {
		JViewport viewport = tableScroll.getViewport(); 
		DICOMTreeTable treeTable = (DICOMTreeTable)viewport.getView();
		this.studyCountLbl.setText(getStudyCount(newRoot) + " studies");
		treeInfoPanel.repaint();
		int[] selectedRows = treeTable.getSelectedRows();//using table no good	
		//get already opened tree node locations
		ArrayList<Integer> willExpand = treeTable.getExpandedRowsPos();
		treeTable.reload(newRoot);
		TableColumnResizer.adjustColumnPreferredWidths(treeTable);
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
	
//	@Override
//	public void ancestorAdded(AncestorEvent arg0) {
//		if(manager.getComponentCount()<0) {
//			return;
//		}
//		if (SwingUtilities.getWindowAncestor(this) == WindowManager.getWindow(ConfigInfo.MainScreen.toString())) {
//			if(Utils.isDebug) {
//				Log.logger.info("debug : Floating is end and back to Main Screen:"+ " "+title);
//			}
//			// rebuild tab icon and title.
//			/*
//			 * フローティングするとタブが消失する。
//			 */
//			if(!floating) {
//				//do nothing
//			}else {
//				//when re-docking, replaced last tab automatically.
//				int pos = manager.getComponentCount()-1;
//				if (isHome) {
//					manager.setTitleAt(pos, title);
//					manager.setIconAt(pos, Resources.LocalIcon.loadIconFromResource());
//				} else {
//					manager.setTitleAt(pos, title);
//					manager.setIconAt(pos, Resources.QRIcon.loadIconFromResource());
//				}
//				floatingDialog = null;
//				floating = false;
//			}
//			
//		} else {
//			if(Utils.isDebug) {
//				Log.logger.info("debug : floating:"+ " "+title);
//			}
//			Component win = SwingUtilities.getWindowAncestor(this);
//			if (win instanceof JDialog) {
//				/* OK */
//				if(floatingDialog == null) {
//					floatingDialog = (JDialog) SwingUtilities.getWindowAncestor(this);
//					thisDock.setPreferredSize(null);
//					floatingDialog.setResizable(true);
//					floatingDialog.setLayout(new BorderLayout());
//					floatingDialog.doLayout();
////					floatingDialog.add(this, BorderLayout.CENTER); // JToolBar を中央に配置
//					floatingDialog.revalidate();
//					floatingDialog.repaint();
////					floatingDialog.addWindowListener(new WindowAdapter() {
////						@Override
////						public void windowOpened(WindowEvent e) {
////							
////						}
//	//
////						@Override
////						public void windowClosing(WindowEvent e) {
////							updateFloatingStatus(false);
////						}
////					});
//					// **リサイズ時に pack() を呼ぶ**
////					floatingDialog.addComponentListener(new ComponentAdapter() {
////						@Override
////						public void componentResized(ComponentEvent e) {
////							Dimension d = floatingDialog.getSize();
////							System.out.println("floating dialog resized !!!");
////							thisDock.setPreferredSize(d);
////							thisDock.setBounds(0, 0, d.width, d.height);
////							floatingDialog.revalidate();
////							floatingDialog.repaint();
////						}
////					});
//					updateFloatingStatus(true);
//				}
//				
//			}
//		}
//	}
//
//	@Override
//	public void ancestorMoved(AncestorEvent ae) {
//		if(ae.getSource() instanceof TabDock) {
//			System.out.println("moved");
//		}
//	}
//
//	@Override
//	public void ancestorRemoved(AncestorEvent ae) {}
	
}