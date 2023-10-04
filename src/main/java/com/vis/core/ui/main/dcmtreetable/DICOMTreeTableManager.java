package com.vis.core.ui.main.dcmtreetable;

import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.ui.main.TabDock;
import com.vis.core.util.PropertiesUtil;
import com.vis.core.util.Utils;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class DICOMTreeTableManager extends JTabbedPane {

	DICOMTreeTableManager dttm = this;
	HashMap<String, TabDock> docks;// qrNode
	ButtonGroup keepTopChckGroup = new ButtonGroup();
	String currentAnchor = "";// HOME or nickname
	String topTabNickname = "";//for floating
	QRTreeTableUpdater updater;

	/*
	 * TabDockのフローティング時は、
	 * タブペインからコンポーネントが無くなるので、
	 * NULLに注意。
	 * TabDockのやりとりはなるべくdocks = new HashMapをつかう。
	 */
	public DICOMTreeTableManager() {
		docks = new HashMap<String, TabDock>();
		currentAnchor  = loadWhichTreeTableKeepTop();
		addContainerListener(new TabbedPaneContainerListener());
//		setAndStartRefreshQRTableTimer();//see, mainscreen::constructMainTreeTables()
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
	}

	private String loadWhichTreeTableKeepTop() {
		return PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.MainTreeTableKeepTopTitle);
	}

	public void addOrUpdateDocks(boolean home, String newNickname, TabDock newNodeDock) throws URISyntaxException {
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
				TabDock target = getParticularDock(newNickname);
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
		TabDock dock = new TabDock(home,nickname,keepTopChck, tableScroll, this);
		dock.setOrientation(javax.swing.SwingConstants.VERTICAL);
		addOrUpdateDocks(home,nickname,dock);
		showKeepTop();
		docks.put(nickname, dock);
	}
	
	void showKeepTop(){
		if(getComponentCount()<1) {
			return;
		}
		for(String aetKey:docks.keySet()) {
			if(aetKey.equals(currentAnchor)) {
				setSelectedComponent(docks.get(aetKey));
				if (Utils.isDebug) System.out.println("Keep Top: "+aetKey);
				break;
			}
		}
	}
	
	public void setTopTab(String nickname) {
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
	
	/*Attention if floating, return null*/
	public TabDock getCurrentTopDock() {
		return (TabDock) getSelectedComponent();
	}
	
	public TabDock getHomeDock() {
		return getParticularDockFromMap("HOME");
	}
	
	public String getTopTabNickname() {
		return topTabNickname;
	}
	
	public TabDock getParticularDockFromMap(String nickname) {
		return docks.get(nickname);
	}
	
	public String getCurrentAnchorTitle() {
		return currentAnchor;
	}
	
	//when floating return null
	public TabDock getParticularDock(String nickname) {
		int num = getComponentCount();
		for(int i=0;i<num;i++) {
			TabDock dock = (TabDock)getComponentAt(i);
			if(dock.getTitle().equals(nickname)) {
				return dock;
			}
		}
		return null;
	}
	
	public Set<String> getNicknameCurrentDocks() {
		return docks.keySet();
	}
	
	public boolean stayDocks(String nickname) {
		for(int i=1;i<getTabCount();i++) {
			TabDock item = (TabDock)getTabComponentAt(i);
			String name = item.getTitle();
			if(name.equals(nickname)) {
				return true;
			}
		}
		return false;
	}
	
	public void removeDockAt(String nickname) {
		if(getTabCount() < 1) {//do not remove home tab
			return;
		}
		for(int i=0;i<getTabCount();i++) {
			TabDock item = (TabDock)getComponentAt(i);
//			TabDock item = (TabDock)getTabComponentAt(i);//DO NOT USE
			String name = item.getTitle();
			if(name.equals(nickname)) {
				remove(i);
				revalidate();
				repaint();
				docks.remove(nickname);
				break;
			}
		}
	}
	
	public void setAndStartRefreshQRTableTimer() {
		boolean refreshOn = Boolean.parseBoolean(PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn));
		if(this.updater == null && refreshOn && docks.size() > 1) {
			this.updater = new QRTreeTableUpdater();//run
		}else if (this.updater != null && refreshOn){
			stopRefreshQRTableTimer();
			setAndStartRefreshQRTableTimer();
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
				PropertiesUtil.setPropertyAt("conf/graphy.properties", "MainTreeTableKeepTopTitle", chck.getName());
				currentAnchor = chck.getName();
			}
		}
	}
	
	/*
	 * 何かに使えるかも。
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
