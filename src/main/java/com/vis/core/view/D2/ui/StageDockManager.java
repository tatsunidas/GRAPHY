package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class StageDockManager extends JTabbedPane implements ChangeListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	HashMap<String, StageView> docks = new HashMap<>();
	int lastSelectedTabIndex = 0;//when open frame, selected 0 always
	

	public StageDockManager() {
		super();
		addChangeListener(this);
	}
	
	public void addStage(String patID, StageView stage){
		if(!existsInDock(patID)) {
			super.addTab(patID, stage);
			docks.put(patID, stage);
			int pos = getTabPosition(patID);
			super.setTabComponentAt(pos, buildTabComponent(patID));
		}else {
			//show on top
			Component[] stageInTabs = getComponents();
			for (Component s : stageInTabs) {
				if (s instanceof StageView) {
					s = (StageView)s;
					if(((StageView) s).getPatientInfo().get("PatientID").equals(patID)) {
						setSelectedComponent(s);
					}
				}
			}
		}
	}
	
	public StageView getStage(String patID) {
		return docks.get(patID);
	}
	
	public StageView getStageAt(int tabIndex) {
		String patID = null;
		Component stage= getComponentAt(tabIndex);
		if (stage instanceof StageView) {
			patID = ((StageView)stage).getPatientInfo().get("PatientID");
		}
		return getStage(patID);
	}
	
	//delete from dock
	public void deleteStage(String patID){
		if(docks == null || docks.size() < 1) {
			return;
		}
		docks.remove(patID);
		super.remove(getTabPosition(patID));
		super.fireStateChanged();
		/*
		 * update RoiObjManager
		 */
		com.vis.core.view.D2.roi.RoiObjManager roiManager = Viewer2DScreen.getRoiObjManager();
		if(roiManager.isVisible()) {
			roiManager.updateState();
		}
	}
	
	public boolean existsInDock(String patID) {
		return docks.containsKey(patID);
	}
	
	public int getTabPosition(String patID) {
		/*
		 * TODO 
		 * when floating ??
		 */
		int pos = -1;
		//BasicTabComponentが含まれるので避ける
		Component[] stageInTabs = getComponents();
		for (int i=0;i<stageInTabs.length;i++) {
			Component s = stageInTabs[i];
			if (s instanceof StageView) {
				pos++;
				if(((StageView) s).getPatientInfo().get("PatientID").equals(patID)) {
					break;
				}
			}
		}
		return pos;
	}
	
	public String[] getAllPatientList() {
		if(docks == null || docks.size() < 1) {
			return null;
		}
		ArrayList<String> list = new ArrayList<>(docks.size());
		for(String pat : docks.keySet()) {
			list.add(pat);
		}
		return (String[])list.toArray(new String[list.size()]);
	}
	
	public String getPatIdAt(int tabIndex) {
		String patID = null;
		Component stage = getComponentAt(tabIndex);
		if (stage instanceof StageView) {
			patID = ((StageView)stage).getPatientInfo().get("PatientID");
		}
		return patID;
	}
	
	public int getCurrentTabIndex() {
		return getSelectedIndex();
	}
	
	//whether is dock floating
	public boolean isFloatingStage(String patID) {
		boolean seaDay = false;
		StageView sv = docks.get(patID);
		if(sv != null) {
			return sv.isFloating();
		}else {
			return seaDay;
		}
	}
	
	/*
	 * 全Stageがfloatingしているかどうか
	 */
	public boolean isAllDocksFloating() {
		boolean atSeaDay = false;
		int count = docks.size();
		int numOfSeaDay = 0;
		for(String pid:docks.keySet()) {
			if(isFloatingStage(pid)) {
				numOfSeaDay = numOfSeaDay + 1;
			}
		}
		if(count == numOfSeaDay) {
			atSeaDay = true;
		}
		return atSeaDay;
	}
	
	protected JPanel buildTabComponent(String patID) {
		com.vis.core.ui.CloseIcon closeIcon = new com.vis.core.ui.CloseIcon(1, Color.black, null, 14, 14);
		JButton closeBtn = new JButton(closeIcon);
		closeBtn.setOpaque(false);
		closeBtn.setContentAreaFilled(false);
		closeBtn.setBorderPainted(false);
		closeBtn.setMargin(new java.awt.Insets(0, 2, 0, 2));
		closeBtn.setName(patID);
		closeBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteStage(closeBtn.getName());
			}
		});
		
		JPanel tabTag = new JPanel();
		tabTag.setOpaque(false);
		tabTag.setLayout(new BorderLayout());
		JLabel tagName = new JLabel(patID);
		tagName.setOpaque(false);
		tabTag.add(tagName, BorderLayout.CENTER);
		tabTag.add(closeBtn, BorderLayout.EAST);
		return tabTag;
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		if(e.getSource() instanceof JTabbedPane) {
			JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
			if(docks == null || docks.size() < 1) {
				return;
			}
			if(getTabCount()<=1) {//if all docks floating, tab count is zero.
				lastSelectedTabIndex = 0;
				/*** do nothing ***/
//				Viewer2DScreen.getInstance().setStageInAction();
			}else {
				lastSelectedTabIndex = tabbedPane.getSelectedIndex();
				Viewer2DScreen.getInstance().setStageInAction(getPatIdAt(lastSelectedTabIndex));
			}
		}
	}
}
