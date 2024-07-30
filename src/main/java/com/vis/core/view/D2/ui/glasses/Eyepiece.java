package com.vis.core.view.D2.ui.glasses;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;

import javax.swing.JLayeredPane;
import javax.swing.JPanel;

import com.vis.configuration.Resources;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.db.DatabaseHandler;

@SuppressWarnings("serial")
public class Eyepiece extends JLayeredPane implements ComponentListener{
	
	/**
	 * Eyepiece is a StudyManager
	 */
	
	DatabaseHandler db = null;
	PraparatShelf prapShelf = null;
	GridLayout gridLayout;
	JPanel base ;
	byte goneOutStudyColorPos = 0;

	public Eyepiece(String patID, String studyUID, String seriesUID, String[] sopUIDs, String frameOfRefUID) {
		init();
		addPraparat(patID, studyUID, seriesUID, sopUIDs, frameOfRefUID, allocateStudyColor());
//		DropTarget dt = new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
		new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
	}
	
	private void init() {
		addComponentListener(this);
//		setDoubleBuffered(true);
//		setLayout(new BorderLayout());//DO NOT SET
		setPreferredSize(new Dimension(getWidth(), getHeight()));
		/*************************************************************************************/
		setBounds(0, 0, getWidth(), getHeight());//MUST, set pane size and position.this is not image position
		/*************************************************************************************/
		base = new JPanel();
		base.setDoubleBuffered(true);
//		base.setLayout(new BorderLayout());//see, autolayout()
		add(base, JLayeredPane.DEFAULT_LAYER, 0);
		db = DatabaseHandler.getInstance();
		prapShelf = new PraparatShelf();
		gridLayout = new GridLayout();
	}
	
	private void refreshEye() {
		remove(base);
		base = new JPanel();
		base.setLayout(gridLayout);
		base.setPreferredSize(new Dimension(getWidth(), getHeight()));
		/*************************************************************************************/
		base.setBounds(0, 0, getWidth(), getHeight());//MUST, set pane size and position.this is not image position
		/*************************************************************************************/
		add(base, JLayeredPane.DEFAULT_LAYER, 0);
		revalidate();
	}
	
	private Praparat buildPraparat(String patID, String studyUID, String seriesUID,String[] sopUIDs, Color studyColor) {
		if(seriesUID == null) {
			return null;
		}
		if(sopUIDs == null || sopUIDs.length == 1) {
			//load all sopUID of instances in series
			ArrayList<String> sopUIDList = db.getInstanceUidList(patID,studyUID, seriesUID);
			sopUIDs = sopUIDList.toArray(new String[sopUIDList.size()]);
//			images = db.getFileLocations(patID, studyUID, seriesUID);
		}
		//load particular instances
		ArrayList<String> p2images = new ArrayList<String>();
		for(String sopUID:sopUIDs) {
			String p2img = db.getFileLocation(studyUID, seriesUID, sopUID);
			if(p2img != null) {
				p2images.add(p2img);
			}
		}
		Praparat prap = new Praparat(patID, studyUID, seriesUID, sopUIDs, p2images,this, studyColor,ViewMode.Normal);
		return prap;
	}
	
	public JPanel loadEyepieceBase() {
		return base;
	}
	
	public Praparat getPraparatAt(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		return prapShelf.getPraparat(patID, studyUID, seriesUID, sopUIDs);
	}
	
	public Praparat getPraparatOnEyeAt(java.awt.Point p) {
		Component prap = loadEyepieceBase().getComponentAt(p);
		if(prap instanceof Praparat) {
			return (Praparat) prap;
		}else {
			return null;
		}
	}
	
	public void removePraparat(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		if(seriesUID == null) {
			return;
		}
		prapShelf.removePraparat(patID, studyUID, seriesUID, sopUIDs);
	}
	
	public void removePraparat(Praparat pp) {
		prapShelf.removePraparat(pp);
	}
	
	public void removeSelectedPraparats() {
		ArrayList<Praparat> candidates = getSelectingPraparats();
		for(Praparat pp:candidates) {
			removePraparat(pp);
		}
	}
	
	public void addPraparat(String patID, String studyUID, String seriesUID,
			String[] sopUIDs, String refUID, Color studyColor) {
		if(patID == null || studyUID == null) {
			return;
		}
		if(seriesUID == null) {
			//load all series to eyepiece
			ArrayList<String> wholeSeriesList = db.getSeriesUidList(patID, studyUID);
			for (String seUID : wholeSeriesList) {
				//get sopUIDs
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID,studyUID, seUID);
				prapShelf.addPraparat(patID,studyUID,seUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),refUID,buildPraparat(patID, studyUID, seUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),studyColor));
			}
		}else {
			//select instances to show
			if(sopUIDs != null) {
				//only show specified instances
				prapShelf.addPraparat(patID,studyUID,seriesUID, sopUIDs,refUID,buildPraparat(patID, studyUID, seriesUID, sopUIDs,studyColor));
			}else {
				//show all instances in particular series
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID, studyUID, seriesUID);
				prapShelf.addPraparat(patID,studyUID,seriesUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),refUID,buildPraparat(patID, studyUID, seriesUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]),studyColor));
			}
		}
	}
	
	public void updatePraparat(Praparat prevPrap, String newPatID, String newStudyUID, String newSeriesUID, String[] newSopUIDs, String refUID) {
		System.out.println(prevPrap.getUIDs()[2]+"  "+newSeriesUID);
		prapShelf.updatePraparatContext(prevPrap, newPatID, newStudyUID, newSeriesUID, newSopUIDs, refUID);
	}
	
	public ArrayList<Praparat> getPraparatAmbiguously(String patID, String studyUID, String seriesUID) {
		ArrayList<PraparatContext> praps = getAllPraparatContext();
		ArrayList<Praparat> result = new ArrayList<Praparat>();
		for(PraparatContext pcon:praps) {
			Object[] con = pcon.getContextUIDs();
			String patID_ = (String)con[0];
			String studyUID_ = (String)con[1];
			String seriesUID_ = (String)con[2];
			if(patID_.equals(patID) && studyUID_.equals(studyUID) && seriesUID_.equals(seriesUID)) {
				result.add(pcon.getPraparat());
			}
		}
		return result;
	}
	
	public ArrayList<Praparat> getAllPraparatByFrameOfReferenceUID(String patID, String studyUID, String refUID) {
		ArrayList<PraparatContext> praps = getAllPraparatContext();
		ArrayList<Praparat> result = new ArrayList<Praparat>();
		for(PraparatContext pcon:praps) {
			Object[] con = pcon.getContextUIDs();
			String patID_ = (String)con[0];
			String studyUID_ = (String)con[1];
			// String seriesUID_ = (String)con[2];//do not need .
			String frameOfRefUID_ = (String)con[4];
			if(patID_.equals(patID) && studyUID_.equals(studyUID) && frameOfRefUID_.equals(refUID)) {
				result.add(pcon.getPraparat());
			}
		}
		return result;
	}
	
	public void autoLayout() {
		//get eye layout.
		//...set and add , show eye
//		for(Praparat praparat:praparats) {
//			//show eye ()
//		}
		if(prapShelf.howManyPraparat() > 1) {
			int num = prapShelf.howManyPraparat();
			int row = -1;
			int col = -1;
			if(num == 2) {
				row = 1;
				col = 2;
			}else if(num == 3 || num ==4){
				row = 2;
				col = 2;
			}else {
				int s = 3;
				while(s*s < num) {
					s = s+ 1;
				}
				row = s;
				col = s;
			}
			gridLayout.setRows(row);
			gridLayout.setColumns(col);
		}else {
			gridLayout.setRows(1);
			gridLayout.setColumns(1);
		}
		refreshEye();
		for(PraparatContext pcon:prapShelf.getAllShelfContents()) {
			base.add(pcon.getPraparat());
		}
//		Viewer2DScreen.getInstance().pack();//DO NOT USE to avoid auto resizing
//		if(Viewer2DScreen.getInstance().isVisible()) {
			base.setBounds(0, 0, getWidth(), getHeight());
//		}
	}
	
	public ArrayList<Praparat> getSelectingPraparats() {
		ArrayList<Praparat> praps = new ArrayList<Praparat>();
		for(PraparatContext pcon:prapShelf.getAllShelfContents()) {
			Praparat prap = pcon.getPraparat();
			if(prap.isSelected()) {
				praps.add(prap);
			}
		}
		return praps;
	}
	
	public ArrayList<PraparatContext> getAllPraparatContext(){
		return prapShelf.getAllShelfContents();
	}
	
	public PraparatContext getPraparatContextOf(String pid, String studyUID, String seriesUID, String[] sopUIDs) {
		return prapShelf.getPraparatContext(pid, studyUID, seriesUID, sopUIDs);
	}
	
	public void lostAllPraparatFocusGained() {
		ArrayList<PraparatContext> pcons = getAllPraparatContext();
		for(PraparatContext pcon:pcons) {
			Praparat pp = pcon.getPraparat();
			pp.setFocusGained(false);
		}
	}
	
	public Color allocateStudyColor() {
		ij.process.LUT studyColors = Resources.LUT_FIRE.loadLUT();
		byte index = goneOutStudyColorPos;
		byte increment = 10;
 		index = (byte) (index + increment);//-128 ~ 127
		int location = (int)((int)index + 128);//0 ~ 255
		int r = studyColors.getRed(location);
		int g = studyColors.getGreen(location);
		int b = studyColors.getBlue(location);
		goneOutStudyColorPos = (byte)location;
		return new Color(r,g,b);
	}

	@Override
	public void componentHidden(ComponentEvent arg0) {}

	@Override
	public void componentMoved(ComponentEvent arg0) {}

	@Override
	public void componentResized(ComponentEvent arg0) {
		setPreferredSize(new Dimension(getWidth(), getHeight()));
		/*************************************************************************************/
		setBounds(0, 0, getWidth(), getHeight());//MUST, set pane size and position.this is not image position
		/*************************************************************************************/
		base.setPreferredSize(new Dimension(getWidth(), getHeight()));
		base.setBounds(0, 0, getWidth(), getHeight());
		base.revalidate();//important
		base.repaint();//important
	}

	@Override
	public void componentShown(ComponentEvent arg0) {}
}
