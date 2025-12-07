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
package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.ui.main.TabDock;
import com.vis.core.ui.qr.QRUpdater;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class TreeTableDockManager extends JTabbedPane {

	TreeTableDockManager dttm = this;
	HashMap<String, TabDock> docks;// qrNode
	ButtonGroup keepTopChckGroup = new ButtonGroup();
	String currentAnchor = "";// HOME or nickname
	String topTabNickname = "";//for floating
	QRUpdater updater;
	
	private boolean dragging = false;
	private Image tabImage = null;
	private Point currentMouseLocation = null;
	private int draggedTabIndex = 0;
	
	public static final String homeTabName = "HOME";

	/*
	 * When TabDock is floating, its Dock does not stay in tabbedpane(but keep manage with this manager).
	 * To handle TabDocks, use "docks".
	 */
	public TreeTableDockManager() {
		docks = new HashMap<String, TabDock>();
		currentAnchor = loadKeepTopTreeTableNickName();
		addContainerListener(new TabbedPaneContainerListener());
		addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if(dragging) {
					return;
				}
				int selIndex = getSelectedIndex();
				if (selIndex < 0) {
					return;
				}
				topTabNickname = getTitleAt(selIndex);
			}
		});
		
		addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int selectedIndex = getSelectedIndex();
				String title = getTitleAt(selectedIndex);
				if (title != null && title.equals(homeTabName)) {
					// do nothing
				} else {
					MainScreen screen = WindowManager.getMainScreen();
					screen.resetBirdsEyeView(null);
				}
			}
		});

		addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent e) {
				if (!dragging) {
					// Gets the tab index based on the mouse position
					int tabNumber = getUI().tabForCoordinate(TreeTableDockManager.this, e.getX(), e.getY());

					if (tabNumber >= 0) {
						draggedTabIndex = tabNumber;
						Rectangle bounds = getUI().getTabBounds(TreeTableDockManager.this, tabNumber);
						// Paint the tabbed pane to a buffer
						Image totalImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
						Graphics totalGraphics = totalImage.getGraphics();
						totalGraphics.setClip(bounds);
						// Don't be double buffered when painting to a static image.
						setDoubleBuffered(false);
						paintComponent(totalGraphics);

						// Paint just the dragged tab to the buffer
						tabImage = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
						Graphics graphics = tabImage.getGraphics();
						graphics.drawImage(totalImage, 0, 0, bounds.width, bounds.height, bounds.x, bounds.y,
								bounds.x + bounds.width, bounds.y + bounds.height, TreeTableDockManager.this);

						dragging = true;
						repaint();
					}
				} else {
					currentMouseLocation = e.getPoint();
					// Need to repaint
					repaint();
				}
				super.mouseDragged(e);
			}
		});

		addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				if (dragging) {
					int tabNumber = getUI().tabForCoordinate(TreeTableDockManager.this, e.getX(), 10);
					if (tabNumber >= 0) {
						Component comp = getComponentAt(draggedTabIndex);
						String title = getTitleAt(draggedTabIndex);
						removeTabAt(draggedTabIndex);
						ImageIcon icon = null;
						if(title.equals(TreeTableDockManager.homeTabName)) {
							icon = Resources.LocalIcon.loadIconFromResource();
						}else {
							icon = Resources.QRIcon.loadIconFromResource();
						}
						insertTab(title, icon, comp, null, tabNumber);
						setSelectedIndex(tabNumber);
					}
				}
				dragging = false;
				tabImage = null;
			}
		});
	}
	
	
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		// Are we dragging?
		if (dragging && currentMouseLocation != null && tabImage != null) {
			// Draw the dragged tab
			g.drawImage(tabImage, currentMouseLocation.x, currentMouseLocation.y, this);
		}
	}

	private String loadKeepTopTreeTableNickName() {
		String title = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle);
		if(title == null || title.length()==0) {
			title = homeTabName;
			PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle, title);
		}
		return title;
	}

	private void addOrUpdateDocks(boolean home, String newNickname, TabDock newNodeDock) throws URISyntaxException {
		//check already exists
		if (docks.size() > 0) {
			boolean found = false;
			for (String key : docks.keySet()) {
				if (key.equals(newNickname)) {
					found = true;
					break;
				}
			}
			if(found) {
				TabDock target = getDockStayInTabbedPane(newNickname);
				remove(target);
				docks.replace(newNickname, newNodeDock);
				if(home) {
					addTab(newNickname, Resources.LocalIcon.loadIconFromResource(), newNodeDock);
				}else {
					addTab(newNickname, Resources.QRIcon.loadIconFromResource(), newNodeDock);
				}
				revalidate();
				repaint();
				return;
			}else {
				docks.put(newNickname, newNodeDock);
				if(home) {
					addTab(newNickname, Resources.LocalIcon.loadIconFromResource(), newNodeDock);
				}else {
					addTab(newNickname, Resources.QRIcon.loadIconFromResource(), newNodeDock);
				}
			}
		//when initialization
		} else {
			docks.put(newNickname, newNodeDock);
			if(home) {
				addTab(newNickname, Resources.LocalIcon.loadIconFromResource(), newNodeDock);
			}else {
				addTab(newNickname, Resources.QRIcon.loadIconFromResource(), newNodeDock);
			}
		}		
	}
	
	/*
	 * localtreetable is "HOME"
	 */
	public void addTreeTable(boolean home, String nickname, DICOMTreeTable treeTable) throws URISyntaxException {
		if(home && nickname.equals(homeTabName)) {
			//ok
		}else if(home && !nickname.equals(homeTabName)){
			Log.logger.warning("HOME TabDock must have [HOME] as nickname. This modification automatically performed.\n"+nickname+ "is changed to HOME.");
			nickname = homeTabName;
		}else if(!home && nickname.equals(homeTabName)){
			Log.logger.warning("HOME nickname must to set for [HOME] TabDock. This TabDock will handle as HOME Dock.");
			home = true;
		}else if(!home && !nickname.equals(homeTabName)){
			//ok
		}
		JScrollPane tableScroll = new JScrollPane();
		tableScroll.setViewportView(treeTable);
		JCheckBox keepTopChck = new JCheckBox("anchor", Resources.AnchorIcon.loadIconFromResource());
		keepTopChck.setName(nickname);
		keepTopChck.setSelectedIcon(Resources.FlagIcon.loadIconFromResource());
		keepTopChck.addItemListener(new KeepTopChckItemListener());
		keepTopChckGroup.add(keepTopChck);
		if(nickname.equals(currentAnchor)) {
			keepTopChck.setSelected(true);
		}
		TabDock dock = new TabDock(nickname,keepTopChck, tableScroll, this);
		dock.setOrientation(javax.swing.SwingConstants.VERTICAL);
		addOrUpdateDocks(home,nickname,dock);
		showKeepTop();
		docks.put(nickname, dock);
	}
	
	private void showKeepTop(){
		if(getComponentCount()<1) {
			return;
		}
		for(String nickname:docks.keySet()) {
			if(nickname.equals(currentAnchor)) {
				setSelectedComponent(docks.get(nickname));
				if (Utils.isDebug) System.out.println("Keep Top: "+nickname);
				break;
			}
		}
	}
	
	/**
	 * Set to top tab, and update currentAnchor.
	 * @param nickname
	 */
	public void setToTopTab(String nickname) {
		/* NEED THIS (this is not ae.properties) */
		PropertiesUtil.setPropertyAt("conf/graphy.properties", "MainTreeTableKeepTopTitle",nickname);
		if (getComponentCount() < 1) {
			return;
		}
		//jdk8
		for (Enumeration<AbstractButton> e = keepTopChckGroup.getElements(); e.hasMoreElements();) {
			JCheckBox chck = (JCheckBox) e.nextElement();
			if (chck.getName().equals(nickname)) {
				chck.setSelected(true);
				break;
			}
		}		
		//jdk11
//		while (keepTopChckGroup.getElements().asIterator().hasNext()) {
//			JCheckBox chck = (JCheckBox) keepTopChckGroup.getElements().nextElement();
//			if (chck.getName().equals(nickname)) {
//				chck.setSelected(true);
//				break;
//			}
//		}
		currentAnchor = nickname;
		showKeepTop();
	}
	
	public boolean isHomeTop() {
		String top = getTopTabNickname();
		if(top == null) {
			return true;
		}
		if(top.equals(homeTabName)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Attention: if floating all docks, return null
	 */
	public TabDock getCurrentTopDockStayInTabbedPane() {
		return (TabDock) getSelectedComponent();
	}
	
	public TabDock getHomeDock() {
		return getDock(homeTabName);
	}
	
	public String getTopTabNickname() {
		return topTabNickname;
	}
	
	/**
	 * 
	 * @param nickname
	 * @return TabDock managed in TreeTableDockManager.
	 */
	public TabDock getDock(String nickname) {
		return docks.get(nickname);
	}
	
	public String getCurrentAnchorTitle() {
		return currentAnchor;
	}
	
	//when floating return null
	public TabDock getDockStayInTabbedPane(String nickname) {
		int num = getComponentCount();
		for(int i=0;i<num;i++) {
			TabDock dock = (TabDock)getComponentAt(i);
			if(dock.getNickname().equals(nickname)) {
				return dock;
			}
		}
		return null;
	}
	
	public Set<String> getAllNicknamesFromDocks() {
		return docks.keySet();
	}
	
	public boolean stayInDocks(String nickname) {
		for(int i=1;i<getTabCount();i++) {
			TabDock item = (TabDock)getTabComponentAt(i);
			String name = item.getNickname();
			if(name.equals(nickname)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Remove remote server from current docks without HOME dock.
	 * @param nickname
	 */
	public void removeDockAt(String nickname) {
		if(nickname.equals(homeTabName)) {
			Log.logger.severe("Cannot remove HOME treetable from docks.");
			return;
		}
		for(int i=0;i<getTabCount();i++) {
			TabDock item = (TabDock)getComponentAt(i);
//			TabDock item = (TabDock)getTabComponentAt(i);//DO NOT USE
			String name = item.getNickname();
			if(name.equals(nickname)) {
				remove(i);
				revalidate();
				repaint();
				docks.remove(nickname);
				break;
			}
		}
	}
	
	public void startRefreshQRTableTimer() {
		boolean refreshOn = Boolean.parseBoolean(PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn));
		if(this.updater == null && refreshOn && docks.size() > 1) {
			this.updater = new QRUpdater();
			this.updater.start(3000, 20000);//run
		}else if (this.updater != null && refreshOn){
			stopRefreshQRTableTimer();
			startRefreshQRTableTimer();
		}
	}
	
	public void stopRefreshQRTableTimer() {
		if(this.updater == null) {
			return;
		}else {
			this.updater.cancel();
			this.updater = null;
		}
	}
	
	
	public class KeepTopChckItemListener implements ItemListener{
		
		@Override
		public void itemStateChanged(ItemEvent ie) {
			JCheckBox chck = (JCheckBox) ie.getSource();
			if(chck.isSelected()) {
				PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle, chck.getName());
				currentAnchor = chck.getName();
			}
		}
	}
	
	/*
	 * Maybe useful for doing something in future ?
	 */
	public class TabbedPaneContainerListener implements ContainerListener{
		@Override
		public void componentAdded(ContainerEvent ce) {
			if (Utils.isDebug) System.out.println("treetable added");
		}

		@Override
		public void componentRemoved(ContainerEvent ce) {
			if (Utils.isDebug) System.out.println("treetable removed");
		}
	}
}
