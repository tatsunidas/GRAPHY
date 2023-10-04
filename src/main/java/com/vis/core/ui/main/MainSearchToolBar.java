package com.vis.core.ui.main;

import java.util.ArrayList;
import javax.swing.JToolBar;

public class MainSearchToolBar extends JToolBar{
	
	private static final long serialVersionUID = -3695328732859476153L;
	ModalitySelect modalitySelection;
	SearchKeyPanel ts;
	PatientInfoPanel infoPanel;
	
	public MainSearchToolBar() {
		super();
		setContents();
	}

	private void setContents() {
		
//		infoPanel = new PatientInfoPanel();
		modalitySelection = new ModalitySelect();//toolbar
		ts = new SearchKeyPanel(modalitySelection);//extends toolbar
		//sort components
		add(ts);
		add(modalitySelection);
//		add(infoPanel);
		
	}
	
	public SearchKeyPanel getTextSearchObject() {
		return ts;
	}
	
	public ArrayList<String> selectedModalities(){
		return modalitySelection.selectedModalities();
	}
	
	public PatientInfoPanel getInfoPanel() {
		return this.infoPanel;
	}
	
	public String getPatientID() {
		return ts.getPatID();
	}
	
	public String getStudyDateFrom() {
		return ts.getTermFrom();
	}
	
	public String getStudyDateTo() {
		return ts.getTermTo();
	}
	
	public boolean isTodaySelected() {
		return ts.isTodaySelected();
	}
}
