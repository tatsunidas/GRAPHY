package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.ToolBarUI;
import javax.swing.plaf.basic.BasicToolBarUI;

import com.vis.core.view.D2.ui.glasses.Eyepiece;
import com.vis.core.view.D2.ui.glasses.EyepieceUI;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.PraparatShelf;

public class StageView extends JToolBar implements AncestorListener, ContainerListener{

	/**
	 * viewer component to manage patient level images.
	 */
	private static final long serialVersionUID = 1L;
	//component bases
	private JPanel basePanel;
	private JPanel towerBase;
	private JPanel eyepieceBase; // views
	//contents
	private DataInfoTower twr = null;
	private Eyepiece eye = null;
	//praparat context
	private ArrayList<PraparatShelf.PraparatContext> praps = null;
	HashMap<String, String> patInfoSet;

	
	public StageView(HashMap<String, String> patInfoSet, String studyUID, String seriesUID,
			String[] sopUIDs, String refUID) {
		setLayout(new BorderLayout());
		this.patInfoSet = patInfoSet;
		addAncestorListener(this);
		addContainerListener(this);
		basePanel = new JPanel();
		basePanel.setLayout(new BorderLayout());
		add(basePanel, BorderLayout.CENTER);
		constructStage(patInfoSet, studyUID, seriesUID, sopUIDs, refUID);
	}
	
	public HashMap<String, String> getPatientInfo() {
		return patInfoSet;
	}

	public void constructStage(HashMap<String, String> patInfoSet, String studyUID, String seriesUID,
			String[] sopUIDs, String refUID) {
		initDataInfoTower(patInfoSet, studyUID, seriesUID, sopUIDs);
		initEyepiece(patInfoSet, studyUID, seriesUID, sopUIDs, refUID);
		JSplitPane towerAndEye = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		towerAndEye.setOneTouchExpandable(true);
		towerAndEye.setLeftComponent(towerBase);
		towerAndEye.setRightComponent(eyepieceBase);
		// set all
//		StageToolBar toolBar = new StageToolBar(this);
//		add(toolBar,BorderLayout.NORTH);
		basePanel.add(towerAndEye, BorderLayout.CENTER);
		praps = eye.getAllPraparatContext();
		updateDataInfoTower();
	}
	
	public void initDataInfoTower(HashMap<String, String> patInfoSet, String studyUID, String seriesUID, String[] sopUIDs) {
		// intit DataInfoTower
		towerBase = new JPanel();
		towerBase.setLayout(new BorderLayout());
		twr = new DataInfoTower(patInfoSet, studyUID, seriesUID, sopUIDs);
		towerBase.add(twr, BorderLayout.CENTER);
	}
	
	public void updateDataInfoTower() {
		// intit DataInfoTower
		praps = eye.getAllPraparatContext();
		twr.linkWithEyepiece(praps);
		twr.repaint();
	}
	
	public ArrayList<Object[]> getAllPraparatContextInfoSet(){
		/*
		 * PraparatContextは、表示中の画像セットをグループ情報としてまとめたもの。
		 * 一つのスタディ、一つのシリーズ、それに付随するインスタンスセットをまとめている。
		 */
		praps = eye.getAllPraparatContext();
		ArrayList<Object[]> praparatInfoSet = new ArrayList<>();
		for(PraparatShelf.PraparatContext prap:praps) {
			Object uids[] = prap.getContextUIDs();
//			studyUIDSet.add((String)uids[1]);
//			seriesUIDSet.add((String)uids[2]);
//			sopUIDSet.add((String[])uids[3]);
			praparatInfoSet.add(uids);
		}
		return praparatInfoSet;
	}
		
	public boolean isFloating() {
		BasicToolBarUI ui = (BasicToolBarUI) getUI();
		return ui.isFloating();
	}
	
	/**
	 * see also "ancestorAdded"
	 * @param location : floating dialog location
	 */
	public void startFloating(Point location) {
		BasicToolBarUI ui = (BasicToolBarUI) getUI();
		if (location == null) {
			Point p = getParent().getLocationOnScreen();
			ui.setFloating(true, new Point(p.x + 50, p.y + 50));
		} else {
			ui.setFloating(true, new Point(location.x, location.y));
		}
	}
	
	/**
	 * see also "ancestorAdded"
	 */
	public void endFloating() {
		BasicToolBarUI ui = (BasicToolBarUI) getUI();
		ui.setFloating(false, null);
	}

	public void initEyepiece(HashMap<String, String> patInfoSet, String studyUID, String seriesUID, String[] sopUIDs, String refUID) {
		//init Eyepiece 
		eyepieceBase = new JPanel();
		eyepieceBase.setLayout(new BorderLayout());
		eye = new Eyepiece(patInfoSet.get("PatientID"), studyUID, seriesUID, sopUIDs, refUID);
		eye.autoLayout();
		EyepieceUI layerUI = new EyepieceUI(eye);
		JLayer<JComponent> eyeHolder = new JLayer<JComponent>(eye, layerUI);
		eyepieceBase.add(eyeHolder, BorderLayout.CENTER);
	}
	
	public void addPraparatOnStage(String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID) {
		if(eye == null) {
			return;
		}
		eye.addPraparat(patID, studyUID, seriesUID, sopUIDs, refUID, eye.allocateStudyColor());
		eye.autoLayout();
		updateDataInfoTower();
	}
	
	public void updatePraparatOnStage(Praparat target, String patID, String studyUID, String seriesUID, String[] sopUIDs, String refUID) {
		if(eye == null) {
			return;
		}
		eye.updatePraparat(target, patID, studyUID, seriesUID, sopUIDs, refUID);
		updateDataInfoTower();
	}
	
	public void removePraparatOnSatage(String patID, String studyUID, String seriesUID, String[] sopUIDs) {
		if(eye == null) {
			return;
		}
		eye.removePraparat(patID,studyUID,seriesUID,sopUIDs);
		eye.autoLayout();
		praps = eye.getAllPraparatContext();
		updateDataInfoTower();
	}
	
	public void removePraparatOnSatage(Praparat pp) {
		if(eye == null) {
			return;
		}
		eye.removePraparat(pp);
		eye.autoLayout();
		praps = eye.getAllPraparatContext();
		updateDataInfoTower();
	}
	
	public void removeSelectedAllPraparatOnSatage() {
		if(eye == null) {
			return;
		}
		eye.removeSelectedPraparats();
		praps = eye.getAllPraparatContext();
		eye.autoLayout();
		updateDataInfoTower();
	}
	
	public Eyepiece getEyepiece() {
		return eye;
	}

	@Override
	public void ancestorAdded(AncestorEvent arg0) {
		/*
		 * ((BasicToolBarUI) toolbar.getUI()).setFloatingLocation(300, 200);
		 * ((BasicToolBarUI) toolbar.getUI()).setFloating(true, null);//こちらを使ったほうがいいかも
		 */
		StageDockManager sdm = Viewer2DScreen.getInstance().getStageDockManager();
		String patID = patInfoSet.get("PatientID");
		if(sdm == null || sdm.getComponentCount()<0) {
			return;
		}
		if (SwingUtilities.getWindowAncestor(this) == Viewer2DScreen.getInstance()) {
			// tab icon and name rebuildStageView currentStage = (StageView) sdm.getComponentAt(sdm.getCurrentTabIndex());
			int selectedTabInd = sdm.getCurrentTabIndex();
			if(selectedTabInd == -1) {
				// nobody docking
				int pos = 0;
				sdm.setTabComponentAt(pos, sdm.buildTabComponent(patID));//tab tag component!
				sdm.setSelectedIndex(pos);//show top in dock
				sdm.revalidate();
				sdm.repaint();
				return;
			}
			StageView currentStage = (StageView) sdm.getComponentAt(selectedTabInd);
			if(currentStage == null) {
				return;
			}
			ToolBarUI ui = currentStage.getUI();
			boolean floating = ui instanceof BasicToolBarUI && ((BasicToolBarUI) ui).isFloating();			
			if(!floating) {
				System.out.println("...StageDock still stay in dock:"+ " "+patID);
				int pos = sdm.getTabPosition(patID);
				sdm.setTabComponentAt(pos, sdm.buildTabComponent(patID));//tab tag component!
				sdm.setSelectedIndex(sdm.lastSelectedTabIndex);//show top in dock
				sdm.revalidate();
				sdm.repaint();
			}else {
				System.out.println("...StageDock make a homeward voyage:"+ " "+patID);
				//when re-docking, add tab at last.
//				int pos = sdm.getComponentCount()-1;//too many ?
				int pos = sdm.getTabCount()-1;//get last tab pos
//				sdm.setTitleAt(pos, patID);
				sdm.setTabComponentAt(pos, sdm.buildTabComponent(patID));//tab tag component!
				sdm.setSelectedIndex(pos);//show top in dock
				sdm.revalidate();
				sdm.repaint();
			}
			
		} else {
			System.out.println(patID+" StageDock is floating...");
			Viewer2DScreen.getInstance().setStageInAction(patID);
			Component win = SwingUtilities.getWindowAncestor(this);
			if (win instanceof JDialog) {
				/* OK */
				JDialog floatingFrame = (JDialog) SwingUtilities.getWindowAncestor(this);
				if(!floatingFrame.isResizable()) {
					floatingFrame.setResizable(true);
				}
				floatingFrame.addComponentListener(new FloatingDialogWindowListener());
				floatingFrame.setName(patID);
				floatingFrame.setTitle(patID);
				int w = sdm.getWidth();
				int h = sdm.getHeight();
//				System.out.println(w +" "+h);
				if(w < 100) {
					w = 150;
				}
				if(h < 100) {
					h = 150;
				}
				// to avoid floating dialog minimize */
				floatingFrame.setPreferredSize(new Dimension(w, h));
				floatingFrame.setBounds(sdm.getLocationOnScreen().x+10, sdm.getLocationOnScreen().y+10, w, h);
				floatingFrame.revalidate();
				floatingFrame.repaint();
				sdm.revalidate();
				sdm.repaint();
			}
		}
	}

	@Override
	public void ancestorMoved(AncestorEvent arg0) {
//		System.out.println("stage view moved");
	}

	@Override
	public void ancestorRemoved(AncestorEvent arg0) {}

	@Override
	public void componentAdded(ContainerEvent e) {
		// TODO Auto-generated method stub
		System.out.println("added !!!");
	}

	@Override
	public void componentRemoved(ContainerEvent e) {
		// TODO Auto-generated method stub
		System.out.println("removed !!!");
	}
}
