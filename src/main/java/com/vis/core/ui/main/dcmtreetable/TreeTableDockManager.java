package com.vis.core.ui.main.dcmtreetable;

import java.awt.Component;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.ui.main.QRUpdater;
import com.vis.core.ui.main.TabDock;
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
	
	private int dragTabIndex = -1;
	
	public static final String homeTabName = "HOME";

	/*
	 * When TabDock is floating, its Dock does not stay in tabbedpane(but keep manage with this manager).
	 * To handle TabDocks, use "docks".
	 */
	public TreeTableDockManager() {
		docks = new HashMap<String, TabDock>();
		currentAnchor  = loadKeepTopTreeTableNickName();
		addContainerListener(new TabbedPaneContainerListener());
		addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int selIndex = getSelectedIndex();
				if(selIndex < 0) {
					return;
				}
				topTabNickname = getTitleAt(selIndex);
			}
		});
		
		/*
		 * Tabのスワップ。非実装。
		 */
//		addMouseListener(new MouseAdapter() {
//            @Override
//            public void mousePressed(MouseEvent e) {
//                dragTabIndex = indexAtLocation(e.getX(), e.getY());
//            }
//        });
//
//        addMouseMotionListener(new MouseAdapter() {
//            @Override
//            public void mouseDragged(MouseEvent e) {
//                if (getTabCount() <= 1) return; // タブが1つしかない場合は処理しない
//                
//                int targetIndex = indexAtLocation(e.getX(), e.getY());
//                if (dragTabIndex >= 0 && targetIndex >= 0 && dragTabIndex != targetIndex) {
//                    swapTabs(dragTabIndex, targetIndex);
//                    dragTabIndex = targetIndex;
//                }
//            }
//        });
	}

	private String loadKeepTopTreeTableNickName() {
		return PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle);
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
	
	private void swapTabs(int index1, int index2) {
        if (index1 < 0 || index1 >= getTabCount() || index2 < 0 || index2 >= getTabCount()) {
            return; // 範囲外の場合は処理しない
        }

        Component comp1 = getComponentAt(index1);
        Component comp2 = getComponentAt(index2);
        String title1 = getTitleAt(index1);
        String title2 = getTitleAt(index2);
        Icon icon1 = getIconAt(index1);
        Icon icon2 = getIconAt(index2);
        String tip1 = getToolTipTextAt(index1);
        String tip2 = getToolTipTextAt(index2);

        // タブ情報を入れ替え
        setComponentAt(index1, comp2);
        setComponentAt(index2, comp1);
        setTitleAt(index1, title2);
        setTitleAt(index2, title1);
        setIconAt(index1, icon2);
        setIconAt(index2, icon1);
        setToolTipTextAt(index1, tip2);
        setToolTipTextAt(index2, tip1);

        setSelectedIndex(index2);
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
