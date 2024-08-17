package com.vis.core.view.D2.roi;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLayer;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.vis.core.view.D2.ui.glasses.*;
import com.vis.db.DatabaseHandler;
import com.vis.configuration.ContextKey;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.*;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.io.SaveDialog;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import ij.process.ColorProcessor;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

@SuppressWarnings("serial")
public class RoiObjManager extends JFrame implements ActionListener, ItemListener, MouseListener, MouseWheelListener, ListSelectionListener, Iterable<RoiObj>{

	// debug
	public static void main(String[] args) {
		ij.plugin.frame.RoiManager rm ;//to check src
		ij.gui.ShapeRoi s;
		RoiObjManager rom = new RoiObjManager();
		rom.setVisible(true);
	}
	
	boolean isDebug = Utils.isDebug;
	
	public enum RoiFunctions{
		Measure,
		RoiInfoLabeling,
		Delete,
		LineAndColor,
		Update,
		
		//add more
		Open,
		Save,
		Fill,
		Draw,
		Capture,
		AND,
		OR_Combine,
		XOR,
		Split,
		SplineFit,
		ConvertToPolygon;
	}
	
	public enum StatsType{
		AREA(ImageStatistics.AREA),
		MEAN(Measurements.MEAN),
		MEDIAN(Measurements.MEDIAN),
		STD_DEV(Measurements.STD_DEV),
		MODE(Measurements.MODE),
		MIN_MAX(Measurements.MIN_MAX),
		ANGLE(3), // be careful, bit-wise procedure do not use
		CENTROID(Measurements.CENTROID),
		CENTER_OF_MASS(Measurements.CENTER_OF_MASS),
		PERIMETER(Measurements.PERIMETER),
		FERET(Measurements.FERET),//feret diameter
		INTEGRATED_DENSITY(Measurements.INTEGRATED_DENSITY),
		AREA_FRACTION(Measurements.AREA_FRACTION),
		SKEWNESS(Measurements.SKEWNESS),
		KURTOSIS(Measurements.KURTOSIS);
		
		private int id;
		
		private StatsType(int id) {
			this.id = id;
		}
				
		public int id() {
			return id;
		}
		
		public static String findType(int id) {
			for(StatsType fof : StatsType.values()) {
				if(fof.id() == id) {
					return fof.name();
				}
			}
			return null;
		}
	}
	
	private static final int BUTTONS = 11;//num of functions
//	private static final int DRAW=0, FILL=1, LABEL=2;
//	private static final int SHOW_ALL=0, SHOW_NONE=1, LABELS=2, NO_LABELS=3;
//	private static final int MENU=0, COMMAND=1;
//	private static final int IGNORE_POSITION=-999;  // ignore the ROI's built in position
//	private static final int CHANNEL=0, SLICE=1, FRAME=2, SHOW_DIALOG=3;
	private static String moreButtonLabel = "More "+'\u00bb';
	private JComboBox<String> patList;
	private DefaultComboBoxModel<String> patComboModel;
	private JPanel panel;//list panel
	private static RoiObjManager instance;
	private JList<String> list;//roi obj list
	private DefaultListModel<String> listModel;//roi obj list model
	private HashMap<String, RoiObj> rois = new HashMap<>();//rois in listed
	private HashMap<String,RoiObj> selectedRois = new HashMap<>();//selected on list
	private JPopupMenu pm;
	private JButton moreButton;//, colorButton;
	private JCheckBox labelsCheckbox = new JCheckBox("Labels", false);
//	private Overlay overlayTemplate;

//	private static boolean onePerSlice = true;
//	private static boolean restoreCentered;
//	private int prevID;
//	private boolean noUpdateMode;
//	private int defaultLineWidth = 1;
//	private Color defaultColor;
//	private boolean firstTime = true;
//	private boolean appendResults;
//	private static ResultsTable mmResults, mmResults2;
//	private int imageID;
//	private boolean allowRecording;
//	private boolean recordShowAll = true;
//	private boolean allowDuplicates;
//	private double translateX = 10.0;
//	private double translateY = 10.0;
//	
//	private boolean multiCropShow = true;
//	private boolean multiCropSave;
//	private int multiCropFormatIndex;
	
	private static String errorMessage;
//	ResultsTable rt = null;
	
	/*
	 * used for Viewer2DScreen
	 */
	public RoiObjManager() {
		super("Analysis Assistant");
		if (instance!=null) {
			return;
		}
		instance = this;
		errorMessage = null;
		setUp();
	}
	
	private void setUp() {
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setIconImage(Resources.RoiObjManagerWinIcon.loadIconFromResource().getImage());
		setSize(400,300);
		addMouseListener(this);
		addMouseWheelListener(this);
		setLayout(new BorderLayout());
		setLocationRelativeTo(Viewer2DScreen.getInstance());
		
		patList = new JComboBox<>();
		patComboModel = new DefaultComboBoxModel<>();
		patList.setModel(patComboModel);
		patList.setEditable(false);
		patList.addItemListener(this);
		add(patList, BorderLayout.NORTH);
		
		list = new JList<>();
		listModel = new DefaultListModel<>();
		list.setModel(listModel);
//		list.setPrototypeCellValue("0000-0000-0000");
		list.addListSelectionListener(this);
		list.addMouseListener(this);
		list.addMouseWheelListener(this);
		if (IJ.isLinux()) list.setBackground(Color.white);
		JScrollPane scrollPane = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);
		
		//buttons
		panel = new JPanel();
		int nButtons = BUTTONS;
		panel.setLayout(new GridLayout(nButtons, 1, 3, 3));
		addMainFeatures();
		addPopupMenu();
		add(panel, BorderLayout.EAST);
	}
	
	public RoiObjManager getInstance() {
		return instance;
	}
	
	@Override
	public void setVisible(boolean show) {
		super.setVisible(show);
		if(show) {
			updateState();
		}
	}
	
	/*
	 * Measure
	 * Labeling
	 * Delete
	 * Line&Color
	 */
	void addMainFeatures() {
		addButton(RoiFunctions.Measure.name());
		addButton(RoiFunctions.RoiInfoLabeling.name());
		addButton(RoiFunctions.Delete.name());
		addButton(RoiFunctions.LineAndColor.name());
		addButton(RoiFunctions.Update.name());
		addButton(moreButtonLabel);
		if(isDebug) {
			addButton("Test");
		}
//		labelsCheckbox.addItemListener(this);
//		panel.add(labelsCheckbox);
	}

	void addPopupMenu() {
		pm = new JPopupMenu();
		addPopupItem(RoiFunctions.Open.name());
		addPopupItem(RoiFunctions.Save.name());
		addPopupItem(RoiFunctions.Fill.name());
		addPopupItem(RoiFunctions.Draw.name());
		addPopupItem(RoiFunctions.Capture.name());
		addPopupItem(RoiFunctions.Split.name());
		addPopupItem(RoiFunctions.AND.name());
		addPopupItem(RoiFunctions.OR_Combine.name());
		addPopupItem(RoiFunctions.XOR.name());
		addPopupItem(RoiFunctions.SplineFit.name());
		addPopupItem(RoiFunctions.ConvertToPolygon.name());
//		addPopupItem("Multi Measure");
//		addPopupItem("Multi Plot");
//		addPopupItem("Multi Crop");
//		addPopupItem("Sort");
//		addPopupItem("Specify...");
//		addPopupItem("Remove Positions...");
//		addPopupItem("Labels...");
//		addPopupItem("List");
//		addPopupItem("Interpolate ROIs");
//		addPopupItem("Translate...");
//		addPopupItem("Help");
//		addPopupItem("Options...");
	}
	
	void addButton(String label) {
		JButton b = new JButton(label);
		b.addActionListener(this);
		b.addMouseListener(this);
		if (label.equals(moreButtonLabel)) moreButton = b;
		if(panel != null) {
			panel.add(b);
		}
	}

	void addPopupItem(String s) {
		JMenuItem mi=new JMenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}
	
	public void updatePatientList() {
		if(patComboModel == null || patList == null) {
			return;
		}
		String[] lists = Viewer2DScreen.getInstance().getPatientsListOnViewer();
		if(lists == null) {
			//reset
			patComboModel = new DefaultComboBoxModel<>();
			patList.setModel(patComboModel);
		}else {
			patComboModel.removeAllElements();
			for(int i=0;i<lists.length;i++) {
				patComboModel.addElement(lists[i]);
			}
		}
		patList.revalidate();
		patList.repaint();
	}
	
	/**
	 * re-construct roi list, which viewing on stage and selected patient by combo.
	 */
	public void updateRoiObjList(String patID) {
		listModel.removeAllElements();
		if(rois != null) {
			rois.clear();
		}else {
			rois = new HashMap<>();//init
		}
		StageView stage = Viewer2DScreen.getInstance().getStageViewAt(patID);
		ArrayList<Object[]> prapCons = stage.getAllPraparatContextInfoSet();
		for (Object[] uids : prapCons) {
			// get current praparat
			String studyUID = (String) uids[1];
			String seriesUID = (String) uids[2];
			String[] sopUIDSet = (String[]) uids[3];
			Praparat prap = stage.getEyepiece().getPraparatAt(patID, studyUID, seriesUID, sopUIDSet);
			HashMap<Integer,SlideGlass> slides = prap.getAllSlides();
			for (Integer readPos : slides.keySet()) {
				SlideGlass sg = slides.get(readPos);
				ArrayList<RoiObj> rois = sg.getRois();
				if (rois != null && rois.size() > 0) {
					for(RoiObj r:rois) {
						addRoiObj(r);//add to manager
					}
				}
			}
		}
		list.repaint();
	}
	
	public void updateState() {
		/*
		 * re-load patient
		 * load all roi
		 */
		updatePatientList();
//		updateRoiObjList();// execute from change listener
	}
	
	public void addRoiObj(RoiObj roi) {
//		String name = roi.getName();//show name//do not use
		String id = roi.getProperty(ContextKey.RoiID.name());
		if(id == null || id.trim().length() == 0) {
			return;
		}
		//alreasy exists, return.
		if(inList(id)) {
			return;
		}
		//rois always control with RoiID.
		rois.put(id, roi);
		//list model show ROI name as roi nickname.
		listModel.addElement(id);
		list.repaint();
	}
	
	private void test() {
		/*
		 * get slide and roi test
		 */
//		String patID = Viewer2DScreen.getInstance().getStageInAction();
//		StageView stage = Viewer2DScreen.getInstance().getStageViewAt(patID);
//		ArrayList<Object[]> prapCons = stage.getAllPraparatContextInfoSet();
//		for (Object[] uids : prapCons) {
//			// これらは一つのグループ。表示中の画像セット。
//			String studyUID = (String) uids[1];
//			String seriesUID = (String) uids[2];
//			String[] sopUIDSet = (String[]) uids[3];
//			Praparat prap = stage.getEyepiece().getPraparatAt(patID, studyUID, seriesUID, sopUIDSet);
//			HashMap<Integer,JLayer<SlideGlass>> slides = prap.getAllSlides();
//			for (Integer instNo : slides.keySet()) {
//				SlideGlass sg = slides.get(instNo).getView();
////				RoiObj r = new com.vis.viewer2d.roi.OvalRoi(50, 50, 50, 50, sg);
//				RoiObj r = new com.vis.viewer2d.roi.RoiObj(50, 50, 50, 50, 0, sg);
//				com.vis.viewer2d.roi.ShapeRoi sr = new com.vis.viewer2d.roi.ShapeRoi(r);
//				sg.addRoi(sr);
////				sg.addRoi(r);
//			}
//		}
		
		/*
		 * show result table test
		 */
//		ResultsTable rt = ResultsTable.getResultsTable();
//		rt.addRow();
//		rt.addValue("test1", 123);
//		rt.addValue("test2", 456);
//		rt.show("Measure");
		
		/*
		 * roi rotation test
		 */
//		for(String k : selectedRois.keySet()) {
//			RoiObj roiObj = selectedRois.get(k);
//			if(roiObj instanceof com.vis.viewer2d.roi.Line) {
//				com.vis.viewer2d.roi.Line lineObj = (com.vis.viewer2d.roi.Line) roiObj;
////				lineObj.rotateLine(15);
//				Point[] p1p2 = lineObj.rotatePoints(lineObj, 15);
//				lineObj.updateCoordinates(p1p2[0].x, p1p2[0].y, p1p2[1].x, p1p2[1].y);
//			}
//		}
	}
	
	public boolean inList(String roiID) {
		if(rois == null || rois.size() == 0) {
			return false;
		}
//		return listModel.contains(roiName);//listModel allow same name rois (but no duplicate roiObj.)
		return rois.containsKey(roiID);
	}
	
	private void measure() {
		if(selectedRois == null || selectedRois.size() < 1) {
			return;
		}
		/*
		 * Results win name is "Results" as default.
		 */
		ResultsTable rt = new ResultsTable(1);
		for(String k : selectedRois.keySet()) {
			RoiObj roiObj = selectedRois.get(k);
			//if null ?
			ImagePlus imp = roiObj.getSlideGlass().getOriginalImage();
			ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roiObj);
			imp.deleteRoi();//fail safe
			imp.setRoi(ijRoi);//and do setImage to Roi in this methods
			ImageStatistics stats = imp.getAllStatistics();
			/*
			 * first iteration, table does not have header.
			 */
			boolean hasHeader = rt.getColumnHeading(0) != null;
			int row = rt.size();
			if(!hasHeader) {
				rt.addValue(ContextKey.RoiID.name(), roiObj.getProperty(ContextKey.RoiID.name()));
			}else {
				rt.setValue(ContextKey.RoiID.name(), row, roiObj.getProperty(ContextKey.RoiID.name()));
			}
			for(StatsType stat_type : StatsType.values()) {
				if(stat_type.id() == Measurements.AREA) {
					Double val = stats.area;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.MEAN){
					Double val = stats.mean;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.MEDIAN){
					Double val = stats.median;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.STD_DEV){
					Double val = stats.stdDev;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.MODE){
					Double val = stats.dmode;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.MIN_MAX){
					Double val_min = stats.min;
					Double val_max = stats.max;
					if(!hasHeader) {
						rt.addValue("MIN", val_min);
						rt.addValue("MAX", val_max);
					}else {
						rt.setValue("MIN", row, val_min);
						rt.setValue("MAX", row, val_max);
					}
				}else if(stat_type.id() == 3){//ANGLE
					Double val = ijRoi.getAngle();
					if(!hasHeader) {
						rt.addValue("ANGLE", val);
					}else {
						rt.setValue("ANGLE", row, val);
					}
				}else if(stat_type.id() == Measurements.CENTROID){
					Double val_x = stats.xCentroid;
					Double val_y = stats.yCentroid;
					if(!hasHeader) {
						rt.addValue("CENTROID_X", val_x);
						rt.addValue("CENTROID_Y", val_y);
					}else {
						rt.setValue("CENTROID_X", row, val_x);
						rt.setValue("CENTROID_Y", row, val_y);
					}
				}else if(stat_type.id() == Measurements.CENTER_OF_MASS){
					Double val_x = stats.xCenterOfMass;
					Double val_y = stats.yCenterOfMass;
					if(!hasHeader) {
						rt.addValue("CENTER_OF_MASS_X", val_x);
						rt.addValue("CENTER_OF_MASS_Y", val_y);
					}else {
						rt.setValue("CENTROID_X", row, val_x);
						rt.setValue("CENTROID_Y", row, val_y);
					}
				}else if(stat_type.id() == Measurements.PERIMETER){
					Double val = ijRoi.getLength();
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.FERET){
					double[] feretRes = ijRoi.getFeretValues();
					if(!hasHeader) {
						rt.addValue(stat_type.name()+"_LongAxis", feretRes != null ? feretRes[0]:0);
						rt.addValue(stat_type.name()+"_ShortAxis", feretRes != null ? feretRes[2]:0);
						rt.addValue(stat_type.name()+"_ANGLE", feretRes != null ? feretRes[1]:0);
					}else {
						rt.setValue(stat_type.name()+"_LongAxis", row, feretRes != null ? feretRes[0]:0);
						rt.setValue(stat_type.name()+"_ShortAxis", row, feretRes != null ? feretRes[2]:0);
						rt.setValue(stat_type.name()+"_ANGLE", row, feretRes != null ? feretRes[1]:0);
					}
				}else if(stat_type.id() == Measurements.INTEGRATED_DENSITY){
//					Double val = new FirstOrderFeatures(imp, ijRoi, null, null).calculate(RadiomicsJ.FirstOrderFeatureTypes.IntegratedDensity.id());
					double val = stats.area*stats.mean;
					//if you want raw intden
//					double val = stats.pixelCount*stats.umean
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.SKEWNESS){
					Double val = stats.skewness;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.KURTOSIS){
					Double val = stats.kurtosis;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}else if(stat_type.id() == Measurements.AREA_FRACTION){
					Double val = stats.areaFraction;
					if(!hasHeader) {
						rt.addValue(stat_type.name(), val);
					}else {
						rt.setValue(stat_type.name(), row, val);
					}
				}
			}
		}
		String header = rt.getColumnHeadings();
		ResultWindow win = new ResultWindow("Measure", header, 400, 350);
		win.setLocationRelativeTo(Viewer2DScreen.getInstance());
		int n = rt.size();
		if (n > 0) {
			for (int i = 0; i < n; i++) {
				win.append(rt.getRowAsString(i));
			}
		}
		//reset result table
		rt = null;
	}
	
	/**
	 * can not show contents
	 * see, measure() in Analyser.
	 * result is always reset at last if-state if resulttable is not IJ.ResultWindow.
	 * @param dummy
	 */
	@Deprecated
	private void measure(boolean dummy) {
		if(selectedRois == null || selectedRois.size() < 1) {
			return;
		}
		/*
		 * Results win name is "Results" as default.
		 */
		ResultsTable rt = new ResultsTable();
		
		int measurements = Analyzer.getMeasurements();
		//set calculate all
		measurements |= 
				Measurements.AREA+
				Measurements.MEAN+
				Measurements.STD_DEV+
				Measurements.MODE+
				Measurements.MIN_MAX+
				Measurements.CENTROID+
				Measurements.CENTER_OF_MASS+
				Measurements.PERIMETER+
				Measurements.RECT+
				Measurements.ELLIPSE+
				Measurements.SHAPE_DESCRIPTORS+
				Measurements.FERET+
				Measurements.INTEGRATED_DENSITY+
				Measurements.MEDIAN+
				Measurements.SKEWNESS+
				Measurements.KURTOSIS+
				Measurements.AREA_FRACTION;
//				Measurements.STACK_POSITION+
//				Measurements.LIMIT+
//				Measurements.LABELS+
//				Measurements.INVERT_Y+
//				Measurements.SCIENTIFIC_NOTATION+
//				Measurements.ADD_TO_OVERLAY+
//				Measurements.NaN_EMPTY_CELLS;
		Analyzer a = null;
		for(String k : selectedRois.keySet()) {
			RoiObj roiObj = selectedRois.get(k);
			//if null ?
			ImagePlus imp = roiObj.getSlideGlass().getOriginalImage();
			ij.gui.Roi ijRoi = new RoiConverter().convert2Roi(roiObj);
			imp.deleteRoi();//fail safe
			imp.setRoi(ijRoi);//and do setImage to Roi in this methods
			a = new Analyzer(imp, measurements, rt);
			a.measure();
		}
//		/*
//		 * DO NOT SET "Results"
//		 */
		SwingUtilities.invokeLater(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				rt.show("Measure");
			}
		});
//		if(a != null) a.displayResults();
	}
	
	private void delete() {
		if(selectedRois == null || selectedRois.size() < 1) {
			return;
		}
		for(String k : selectedRois.keySet()) {
			RoiObj r = selectedRois.get(k);
			if(r != null) {
				SlideGlass slide = r.getSlideGlass();
				if(slide != null) {
					slide.deleteRoi(r);
					/*
					 * avoid error 
					 */
//					selectedRois.remove(k);
//					rois.remove(k);-> updateRoiObjList
				}else {
					HashMap<ContextKey, String> uids = r.getUIDs();
					String patID = uids.get(ContextKey.PatientID);
					String studyUID = uids.get(ContextKey.StudyInstanceUID);
					String seriesUID = uids.get(ContextKey.SeriesInstanceUID);
					String sopUID = uids.get(ContextKey.SOPInstanceUID);
					String roiID = uids.get(ContextKey.RoiID);
					DatabaseHandler.getInstance().deleteRoi(patID, studyUID, seriesUID, sopUID, roiID);
//					selectedRois.remove(k);
//					rois.remove(k);-> updateRoiObjList
				}
			}
		}
		selectedRois = null;
		updateRoiObjList(patList.getItemAt(patList.getSelectedIndex()));
	}
	
	private void roiInfoLabeling() {
		//only 1 roi selected...
		if(selectedRois == null || selectedRois.size() == 0 || selectedRois.size() > 1) {
			return;
		}
		String key = selectedRois.keySet().iterator().next();
		RoiObj roi = selectedRois.get(key);
		if(roi == null) {
			return;
		}
		/*
		 * null-able.
		 */
		String prev_name = roi.getProperty(ContextKey.Name.name());
		String prev_group = roi.getProperty(ContextKey.RoiGroup.name());
		String prev_lbl = roi.getProperty(ContextKey.RoiLabel.name());
		String prev_type = roi.getProperty(ContextKey.ObjectType.name());
		String prev_organ = roi.getProperty(ContextKey.Organ.name());
		String prev_desc = roi.getProperty(ContextKey.Description.name());
		/*
		 * set RoiContextKeySet values
		 * 
		 * Name,
		 * RoiGroup, //int
		 * RoiLabel, //string
		 * ObjectType,//string target object type, e.g., target lesions.
		 * Organ,//string
		 * Description;//for textroi and any context.string
		 */
		GenericDialog gd = new GenericDialog("Roi key information", this);
		for(ContextKey conkey : ContextKey.values()) {
			if(conkey.name().equals("Name")) {
				gd.addStringField(conkey.name(), prev_name, 15);
			}else if(conkey.name().equals("RoiGroup")) {
				gd.addNumericField(conkey.name(), prev_group == null ? Integer.valueOf(0) : Integer.valueOf(prev_group), 0);
			}else if(conkey.name().equals("RoiLabel")) {
				gd.addStringField(conkey.name(),prev_lbl,15);
			}else if(conkey.name().equals("ObjectType")) {
				gd.addStringField(conkey.name(),prev_type,15);
			}else if(conkey.name().equals("Organ")) {
				gd.addStringField(conkey.name(),prev_organ,15);
			}else if(conkey.name().equals("Description")) {
				gd.addStringField(conkey.name(),prev_desc,15);
			}
		}
		gd.showDialog();
		if (gd.wasCanceled()) return;
		String name = gd.getNextString();
		int group = (int)gd.getNextNumber();
		String roiLabel = gd.getNextString();
		String objType = gd.getNextString();
		String organ = gd.getNextString();
		String desc = gd.getNextString();
		DatabaseHandler db = DatabaseHandler.getInstance();
		roi.setProperty("Name", name);
		roi.setProperty("RoiGroup", String.valueOf(group));
		roi.setProperty("RoiLabel", roiLabel);
		roi.setProperty("ObjectType", objType);
		roi.setProperty("Organ", organ);
		roi.setProperty("Description", desc);
		if(db != null) {
			db.insertRoi(roi.readContext());//update
		}
	}
	
	private void lineAndColor() {
		//only 1 roi selected...
		if(selectedRois == null || selectedRois.size() == 0 || selectedRois.size() > 1) {
			return;
		}
		//get roi
		RoiObj roi = null;
		String key = selectedRois.keySet().iterator().next();
		roi = selectedRois.get(key);
		if(roi == null) {
			return;
		}
		/*
		 * see, RoiPrefs in MainScreen:Settings
		 */
		/*
		 * here, do not change pref settings, only change current roi state.
		 */
//		String currentSettingsStrokeWidth = Util4Viewer2D.getPropValueFrom(Util4Viewer2D.GRAPHY_Props, "RoiStrokeWidth");
//		String currentSettingsStrokeColor = Util4Viewer2D.getPropValueFrom(Util4Viewer2D.GRAPHY_Props, "RoiStrokeColor");
//		String currentSettingsFillColor = Util4Viewer2D.getPropValueFrom(Util4Viewer2D.GRAPHY_Props, "RoiFillColor");
		String strokeColorString = GraphyProp.findColorNameByColor(roi.getStrokeColor());
		String fillColorString = GraphyProp.findColorNameByColor(roi.getFillColor());
		GenericDialog gd = new GenericDialog("Line & Color", this);
		gd.addNumericField("Stroke Width", roi.getStrokeWidth(),0);
		gd.addChoice("Stroke Color", new String[]{"white", "blue", "orange", "yellow","red","pink","magenta","green","black"}, strokeColorString);
		gd.addChoice("Fill Color", new String[]{"white", "blue", "orange", "yellow","red","pink","magenta","green","black"}, fillColorString);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		int w = (int)gd.getNextNumber();
		String sc = gd.getNextChoice();
		String fc = gd.getNextChoice();
		if(w < 1) {
			w = 1;
		}else if(w > 50) {
			w = 50;
		}
		roi.setStrokeWidth((double)w);
		roi.setStrokeColor(roi.colorFromString(sc, Color.orange));
		roi.setFillColor(roi.colorFromString(fc, Color.orange));
	}
	
	void open(String path) {
		String selectedPatID = patList.getItemAt(patList.getSelectedIndex());
		if (selectedPatID == null) {
			JOptionPane.showMessageDialog(this,
					"1.Open the image first to load the ROI and select a subject from the patient list.+\n"
							+ "2.then, select image to decide where to load ROI.");
			return;
		}
		Eyepiece eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere(selectedPatID);
		if (eye == null) {
			return;
		}
		ArrayList<Praparat> praps = eye.getSelectingPraparats();
		if(praps.size() == 0) {
			JOptionPane.showConfirmDialog(this, "Please select(Shift+Left Click) Series to load rois.");
			return;
		}
		String name = null;
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Roi (.roi/.zip)...", "");
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name==null) {
				return;
			}
			path = directory + name;
		}
		if (path.endsWith(".zip")) {
			openZip(path,selectedPatID, praps);
			return;
		}
		Opener o = new Opener();
		if (name==null) name = o.getName(path);
		Roi roi = o.openRoi(path);
		if (roi!=null) {
			loadRoi2Slide(roi, selectedPatID, praps);
		} else {
			JOptionPane.showConfirmDialog(this, "Unable to open ROI at "+path);
		}
	}

	// Modified on 2005/11/15 by Ulrik Stervbo to only read .roi files and to not empty the current list
	void openZip(String path, String selectedPatID, ArrayList<Praparat> praps) {
		ZipInputStream in = null;
		ByteArrayOutputStream out = null;
		int nRois = 0;
		errorMessage = null;
		try {
			in = new ZipInputStream(new FileInputStream(path));
			byte[] buf = new byte[1024];
			int len;
			ZipEntry entry = in.getNextEntry();
			while (entry!=null) {
				String name = entry.getName();
				if (name.endsWith(".roi")) {
					out = new ByteArrayOutputStream();
					while ((len = in.read(buf)) > 0) {
						out.write(buf, 0, len);
					}
					out.close();
					byte[] bytes = out.toByteArray();
					RoiDecoder rd = new RoiDecoder(bytes, name);
					Roi roi = rd.getRoi();
					if (roi!=null) {
						loadRoi2Slide(roi, selectedPatID, praps);
						nRois++;
					}
				}
				entry = in.getNextEntry();
			}
			in.close();
		} catch (IOException e) {
			errorMessage = e.toString();
		} finally {
			if (in!=null)
				try {in.close();} catch (IOException e) {}
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		if (nRois==0 && errorMessage==null) {
			errorMessage = "This ZIP archive does not contain \".roi\" files: " + path;
		}
//		updateShowAll();
	}
	
	void loadRoi2Slide(Roi roi, String selectedPatID, ArrayList<Praparat> praps) {
		if (roi == null || praps == null || praps.size() == 0) {
			return;
		}
		// convert to roiobj
		RoiObj roiObj = new RoiConverter().convert2RoiObj(roi);
		for (Praparat prap : praps) {
			// set roi to this series
			int frames = prap.getAllSlides().size();
			String roiInstNoString = roiObj.getProperty(ContextKey.InstanceNo.name());
			int roiFrameNo = 0;// dicom instance No
			if (roiInstNoString != null) {
				roiFrameNo = Integer.parseInt(roiInstNoString);
			}
			if (roiFrameNo > frames) {
				return;
			}
			// FrameNo means instanceNo, which is not slice position.
			// here, roi set to slide which has same instanceNo.
			Set<Integer> keys = prap.getAllSlides().keySet();
			for (Integer readNumber : keys) {
				SlideGlass s = prap.getAllSlides().get(readNumber);
				if (s.getInstanceNo() == roiFrameNo) {
					// set image and attributes
					roiObj.setSlideGlass(s);
					// add roi to slide and insert to db
					s.addRoi(roiObj);
				}
			}
		}
		/*
		 * update
		 */
		updateRoiObjList(selectedPatID);
	}
	
	void save() {
		if (rois.size()==0) {
			System.out.println("The selection list is empty.");
			return;
		}
		/*
		 * select only one roi
		 * no selected or multi select -> save all
		 */
		if(selectedRois.size() == 1) {
			saveRoi(selectedRois.get(selectedRois.keySet().iterator().next()));
		}else {
			//set save dest
			SaveDialog sd = new SaveDialog("Save ROIs...", "RoiSet", ".zip");
			String name = sd.getFileName();
			if (name == null)
				return;
			if (!(name.endsWith(".zip") || name.endsWith(".ZIP")))
				name = name + ".zip";
			String dir = sd.getDirectory();
			String path = dir+name;
			DataOutputStream out = null;
			long t0 = System.currentTimeMillis();
			//save all
			if(selectedRois.size() == 0) {
				try {
					ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
					out = new DataOutputStream(new BufferedOutputStream(zos));
					String[] keys = rois.keySet().toArray(new String[rois.size()]);
					RoiEncoder re = new RoiEncoder(out);
					for (int i=0; i<keys.length; i++) {
						RoiObj roiObj = rois.get(keys[i]);
						String label = roiObj.getProperty(ContextKey.RoiID.name());
						Roi roi = new RoiConverter().convert2Roi(roiObj);
						if(roi == null) {
							continue;
						}
						if (!label.endsWith(".roi")) label += ".roi";
						zos.putNextEntry(new ZipEntry(label));
						re.write(roi);
						out.flush();
					}
					out.close();
				} catch (IOException e) {
					errorMessage = ""+e;
					System.out.println(errorMessage);
					return;
				} finally {
					if (out!=null)
						try {out.close();} catch (IOException e) {}
				}
//				double time = (System.currentTimeMillis()-t0)/1000.0;
			//save selected rois
			}else {
				try {
					ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
					out = new DataOutputStream(new BufferedOutputStream(zos));
					String[] keys = selectedRois.keySet().toArray(new String[selectedRois.size()]);
					RoiEncoder re = new RoiEncoder(out);
					for (int i=0; i<keys.length; i++) {
						RoiObj roiObj = rois.get(keys[i]);
						String label = roiObj.getProperty(ContextKey.RoiID.name());
						Roi roi = new RoiConverter().convert2Roi(roiObj);
						if(roi == null) {
							continue;
						}
						if (!label.endsWith(".roi")) label += ".roi";
						zos.putNextEntry(new ZipEntry(label));
						re.write(roi);
						out.flush();
					}
					out.close();
				} catch (IOException e) {
					errorMessage = ""+e;
					System.out.println(errorMessage);
					return;
				} finally {
					if (out!=null)
						try {out.close();} catch (IOException e) {}
				}
//				double time = (System.currentTimeMillis()-t0)/1000.0;
			}
		}
		
	}

	void saveRoi(RoiObj roiObj) {
		if (roiObj == null) {
			return;
		}
		Roi roi = new RoiConverter().convert2Roi(roiObj);
		if(roi == null) {
			return;
		}
		String path = null;
		String name = roiObj.getProperty(ContextKey.RoiID.name());
		SaveDialog sd = new SaveDialog("Save Roi...", name, ".roi");
		String name2 = sd.getFileName();
		if (name2 == null) {
			return;
		}
		String dir = sd.getDirectory();
		if (!name2.endsWith(".roi")) name2 = name2+".roi";
		path = dir+name2;
		RoiEncoder re = new RoiEncoder(path);
		try {
			re.write(roi);
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return;
		}
	}
	
	/**
	 * draw roi on pixel without saving with keep calibaration and bit-depth.
	 * 
	 * but, do you need this in image processing ?
	 * (I do not think so.)
	 * 
	 */
	@Deprecated
	void paintRoiOnImage() {
		if(selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select roi first...");
			return;
		}else {
			int res = JOptionPane.showConfirmDialog(this, "Do you wnat to paint roi to image ? (You can re-start by reopening)");
			if(res != JOptionPane.OK_OPTION) {
				return;
			}
		}
		
		Set<String> keys = selectedRois.keySet();
		for(String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi==null) continue;
			SlideGlass slide = roi.getSlideGlass();
			if(slide == null) {continue;}
			ImagePlus imp = slide.getOriginalImage();
			imp.deleteRoi();
			ImageProcessor ip = imp.getProcessor();
//			ip.setColor(roi.getStrokeColor());//this needs convert colorprocessor. 
			ip.snapshot();//backup
			roi.drawPixels(ip);
		}
	}
	
	/*
	 * General use.
	 * create RGB capture image.
	 */
	void capture() {
		if(selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select roi first...");
			return;
		}
		boolean hasSameImp = reffereingSameImage(selectedRois);
		if(!hasSameImp) {
			JOptionPane.showConfirmDialog(this, "Select rois on same image...");
			return;
		}
		Set<String> keys = selectedRois.keySet();
		ImagePlus firstImp = null;
		ImagePlus dup = null;
		for(String key:keys) {
			RoiObj roi = selectedRois.get(key);
			if(firstImp == null) {
				firstImp = roi.getImage();
				dup = firstImp.duplicate();
				ColorProcessor cp = dup.getProcessor().convertToColorProcessor();
				dup.setProcessor(cp);
				dup.setTitle("captured");
			}
			dup = captureRoi(roi,dup);
		}
		IJ.saveAsTiff(dup, null);
	}
	
	/**
	 * keep calibration and bit-depth
	 * 
	 */
	void captureWithKeepImageContext() {
		if(selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select roi first...");
			return;
		}
		boolean hasSameImp = true;
		Set<String> keys = selectedRois.keySet();
		ImagePlus firstImp = null;
		for(String key:keys) {
			if(firstImp == null) {
				firstImp = selectedRois.get(key).getImage();
				continue;
			}
			ImagePlus imp = selectedRois.get(key).getImage();
			if(!imp.equals(firstImp)) hasSameImp = false;
		}
		if(!hasSameImp) {
			JOptionPane.showConfirmDialog(this, "Select rois on same image...");
			return;
		}
		ImagePlus dup = firstImp.duplicate();
		for(String key:keys) {
			RoiObj roi = selectedRois.get(key);
			dup = captureRoi(roi,dup);
		}
		IJ.saveAsTiff(dup, null);
	}
	
	ImagePlus captureRoi(RoiObj roi, ImagePlus imp) {
		if (roi==null) return null;
		imp.deleteRoi();
		ImageProcessor ip = imp.getProcessor();
		boolean isRGB = ip!=null && ip.getNChannels()==3; 
		if(isRGB) {
			ip.setColor(roi.getStrokeColor());//Sets the default fill/draw value
		}
		roi.drawPixels(ip);
		return imp;
	}
	
	void fill() {
		if(selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select roi first...");
			return;
		}
		Set<String> keys = selectedRois.keySet();
		for(String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi==null) continue;
			if(!roi.isArea()) {
				continue;
			}
			SlideGlass slide = roi.getSlideGlass();
			if(slide == null) {continue;}
			ImagePlus imp = slide.getOriginalImage();
			imp.deleteRoi();
			ImageProcessor ip = imp.getProcessor();
			ip.snapshot();//backup
			boolean isRGB = ip!=null && ip.getNChannels()==3; 
			if(!isRGB) {
//				ip.setColor(Color.black);//java.lang.ClassCastException, 16-bit or more images.
			}else {
				ip.setColor(roi.getFillColor());
			}
			Roi fillerRoi = new RoiConverter().convert2Roi(roi);
			ip.fill(fillerRoi);
			imp.setProcessor(ip);
			imp.updateImage();
			imp.deleteRoi();
			slide.setDisplayImage(imp);
			slide.repaint();
		}
	}
	
	void splineFit() {
		if(selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select polygon roi first...");
			return;
		}
		Set<String> keys = selectedRois.keySet();
		for(String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi==null) continue;
			SlideGlass s = roi.getSlideGlass();
			int type = roi.getType();
			if(type != RoiType.RECTANGLE.id() && type!=RoiType.POLYGON.id() && type!=RoiType.COMPOSITE.id()) {
				continue;
			}
			if(type == RoiType.RECTANGLE.id()) {
				FloatPolygon fpg = roi.getFloatPolygon();
//				int numOfP = fpg.npoints;
//				float[] fpx = fpg.xpoints;
//				float[] fpy = fpg.ypoints;
				PolygonRoi polyRoi = new PolygonRoi(fpg, RoiType.POLYGON.id(), s);
				polyRoi.fitSpline(20);
				String rid = roi.getProperty(ContextKey.RoiID.name());
				polyRoi.setProperty(ContextKey.RoiID.name(), rid);
				if(s != null) {
					s.replaceRoi(roi.getUIDs(), polyRoi);
				}
			}else if(type == RoiType.POLYGON.id()) {
				PolygonRoi polyRoi = (PolygonRoi)roi;
				polyRoi.fitSpline(100);
				String rid = roi.getProperty(ContextKey.RoiID.name());
				polyRoi.setProperty(ContextKey.RoiID.name(), rid);
				if(s != null) {
					s.replaceRoi(roi.getUIDs(), polyRoi);
				}
			}else if(type == RoiType.COMPOSITE.id()) {
				ShapeRoi sRoi = (ShapeRoi)roi;
				Polygon poly = sRoi.getPolygon();
				int num = poly.npoints;
				int[] xps = poly.xpoints;
				int[] yps = poly.ypoints;
				int originShiftX;
				int originShiftY;
				if(!s.panningFlag) {
					originShiftX = sRoi.x;
					originShiftY = sRoi.y;
				}else {
					originShiftX = (int)(sRoi.x * s.getScaleFactor()[0]);
					originShiftY = (int)(sRoi.y * s.getScaleFactor()[1]);
				}
				for(int i=0; i<num; i++) {
					xps[i] = xps[i]-originShiftX;
					yps[i] = yps[i]-originShiftY;
				}
				PolygonRoi polyRoi = new PolygonRoi(new Polygon(xps, yps, num), RoiType.POLYGON.id(), s);
				polyRoi.fitSpline(100);
				String rid = roi.getProperty(ContextKey.RoiID.name());
				polyRoi.setProperty(ContextKey.RoiID.name(), rid);
				s.replaceRoi(roi.getUIDs(), polyRoi);
			}
			updateState();
		}
	}
	
	void convert2Polygon() {
		if(selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select roi first...");
			return;
		}
		Set<String> keys = selectedRois.keySet();
		for(String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi==null) continue;
			SlideGlass s = roi.getSlideGlass();
			if(s == null) continue;
			int type = roi.getType();
			if(type == RoiType.POLYGON.id()) {
				continue;
			}
			if(!roi.isArea()) {
				continue;
			}
			
			if(type != RoiType.COMPOSITE.id()) {
				FloatPolygon fpg = roi.getFloatPolygon();
//				int numOfP = fpg.npoints;
//				float[] fpx = fpg.xpoints;
//				float[] fpy = fpg.ypoints;
				PolygonRoi polyRoi = new PolygonRoi(fpg, RoiType.POLYGON.id(), s);
				polyRoi.fitSpline(25);
				String rid = roi.getProperty(ContextKey.RoiID.name());
				polyRoi.setProperty(ContextKey.RoiID.name(), rid);
				if(s != null) {
					s.replaceRoi(roi.getUIDs(), polyRoi);
				}
			}else{
				ShapeRoi sRoi = (ShapeRoi)roi;
				Polygon poly = sRoi.getPolygon();//see, ShapeRoi::getFloatPolygon, shape is always shited baseXY
				int num = poly.npoints;
				int[] xps = poly.xpoints;
				int[] yps = poly.ypoints;
				int originShiftX = (int)sRoi.getXBase();
				int originShiftY = (int)sRoi.getYBase();
				for(int i=0; i<num; i++) {
					xps[i] = xps[i]-originShiftX;
					yps[i] = yps[i]-originShiftY;
				}
				PolygonRoi polyRoi = new PolygonRoi(new Polygon(xps, yps, num), RoiType.POLYGON.id(), s);
				polyRoi.fitSpline(100);
				String rid = roi.getProperty(ContextKey.RoiID.name());
				polyRoi.setProperty(ContextKey.RoiID.name(), rid);
				s.replaceRoi(roi.getUIDs(), polyRoi);
			}
			updateState();
		}
	}
	
	private void combine() {
		if (selectedRois.size() < 2) {
			JOptionPane.showConfirmDialog(this, "Select rois first...");
			return;
		}
		RoiObj res = null;
		if (countPointRois(selectedRois)==selectedRois.size()) {
			res = combinePoints(selectedRois);
		}else {
			res = combineRois(selectedRois);
		}
		//save to db
		if (res.getSlideGlass() !=null) {
			DatabaseHandler db = DatabaseHandler.getInstance();
			db.insertRoi(res.readContext());
			res.getSlideGlass().loadRoiFromDB();
			updateState();
		}else {
			System.out.println("RoiObjManager:combine() result does not have slideglass(i.e, image), cancel register to db");
		}
	}
	
	private int countPointRois(HashMap<String,RoiObj> rois) {
		int nPointRois = 0;
		for (String roiid : rois.keySet()) {
			RoiObj r = rois.get(roiid);
			if (r.getType()==RoiType.POINT.id()) {
				nPointRois++;
			}
		}
		return nPointRois;
	}

	private RoiObj combineRois(HashMap<String,RoiObj> rois) {
		if (rois.size()==1) {
			return null;
		}
		com.vis.core.view.D2.roi.ShapeRoi s1=null, s2=null;
		for(String key : rois.keySet()) {
			RoiObj roi = rois.get(key);
			if (!roi.isArea() && roi.getType()!=RoiType.POINT.id()) {
				roi = RoiObj.convertLineToArea(roi);
			}
			//first time loop
			if (s1==null) {
				if (roi instanceof com.vis.core.view.D2.roi.ShapeRoi) {
					s1 = (com.vis.core.view.D2.roi.ShapeRoi)roi;
				}else {
					s1 = new com.vis.core.view.D2.roi.ShapeRoi(roi);
				}
			} else {//second or more loop
				if (roi instanceof com.vis.core.view.D2.roi.ShapeRoi) {
					s2 = (com.vis.core.view.D2.roi.ShapeRoi)roi;
				}else {
					s2 = new com.vis.core.view.D2.roi.ShapeRoi(roi);
				}
				s1.or(s2);
			}
		}
		//finally, s1 was become result of combined all rois.
		return s1;
	}

	RoiObj combinePoints(HashMap<String,RoiObj> rois) {
		SlideGlass slide = null;
		FloatPolygon fp = new FloatPolygon();
		for(String key:rois.keySet()) {
			RoiObj roi = rois.get(key);
			if(slide == null) {
				slide = roi.getSlideGlass();
			}
			FloatPolygon fpi = roi.getFloatPolygon();
			for (int i=0; i<fpi.npoints; i++) {
				fp.addPoint(fpi.xpoints[i], fpi.ypoints[i]);
			}
		}
		return new com.vis.core.view.D2.roi.PointRoi(fp,slide);
	}
	
	/*
	 * Split
	 * AND
	 * XOR
	 */
	
	void split(){
		if(selectedRois.size() != 1) {
			JOptionPane.showConfirmDialog(this, "Select composite roi first...");
			return;
		}
		String key = selectedRois.keySet().iterator().next();
		RoiObj roi = selectedRois.get(key);
		if (roi==null) return;
		SlideGlass slide = roi.getSlideGlass();
		if(slide==null) return;
		int type = roi.getType();
		if(type != RoiType.COMPOSITE.id()) return;
		RoiObj[] roiBlobs = ((ShapeRoi)roi).getRois();
		for (int i=0; i<roiBlobs.length; i++) {
			//TODO ! shift XY
			roiBlobs[i].x -= roi.getXBase();
			roiBlobs[i].y -= roi.getYBase();
			slide.addRoi(roiBlobs[i]);
		}
		updateState();
	}
	
	/** calculates the intersection of area, line and point selections.
	 *  If there is one PointRoi in the list of selected Rois, the points inside all selected area rois are kept.
	 *  If more than one PointRoi is selected, the PointRois get converted to area rois with each pixel containing
	 *  at least one point selected. */
	void and() {
		if(selectedRois.size() <= 1) {
			JOptionPane.showConfirmDialog(this, "Select rois first...");
			return;
		}
		if(!reffereingSameImage(selectedRois)) {
			JOptionPane.showConfirmDialog(this, "Select rois from same image...");
			return;
		}
		int nPointRois = countPointRois(selectedRois);
		ShapeRoi s1=null;
		com.vis.core.view.D2.roi.PointRoi pointRoi = null;
		Set<String> keys = selectedRois.keySet();
		SlideGlass slide = null;
		for (String key : keys) {
			RoiObj roi = selectedRois.get(key);
			if (roi==null)
				continue;
			if (s1==null) {
				slide = roi.getSlideGlass();
				if (nPointRois==1 && roi.getType() == Roi.POINT) {
					pointRoi = (PointRoi)roi;
					continue;  //PointRoi will be handled at the end
				}
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) continue;
			} else {
				if (nPointRois==1 && roi.getType()==Roi.POINT) {
					pointRoi = (PointRoi)roi;
					continue;  //PointRoi will be handled at the end
				}
				ShapeRoi s2 = null;
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.and(s2);
			}
		}
		if (s1==null) return;
//		java.awt.Shape poly = s1.getPolygon();
		/*
		 * see, ShapeRoi::getFloatPolygon
		 * shape is always shifted baseXY.
		 */
		Polygon poly = s1.getPolygon();
		int nPoint = poly.npoints;
		int[] xps = poly.xpoints;
		int[] yps = poly.ypoints;
		for(int i=0; i<nPoint; i++) {
			xps[i] = xps[i]-(int)(s1.getXBase());
			yps[i] = yps[i]-(int)(s1.getYBase());
//			xps[i] = slide.onOriginalImageX(xps[i]-slide.onDisplayImageX(s1.x));
//			yps[i] = slide.onOriginalImageY(yps[i]-slide.onDisplayImageY(s1.y));
		}
		poly = new Polygon(xps, yps, nPoint);
//		ShapeRoi shiftOrigin = new ShapeRoi(new PolygonRoi(xps, yps, num, PolygonRoi.POLYGON, slide));
		s1 = new ShapeRoi(poly, slide);
		if (pointRoi!=null) {
			slide.addRoi(pointRoi.containedPoints(s1));
		}else {
			slide.addRoi(s1.trySimplify());
		}
		updateState();
	}

	void xor() {
		if (selectedRois.size() < 2) {
			JOptionPane.showConfirmDialog(this, "More than one roi must be selected");
			return;
		}
		if(!reffereingSameImage(selectedRois)) {
			JOptionPane.showConfirmDialog(this, "Select rois from same image...");
			return;
		}
		RoiObj roi2 = RoiObj.xor(getSelectedRoisAsArray(selectedRois));
		if (roi2!=null) {
			RoiObj firstRoi = selectedRois.get(selectedRois.keySet().iterator().next());
			SlideGlass slide = firstRoi.getSlideGlass();
			if(slide != null) {
				slide.addRoi(roi2);
			}
		}
		updateState();
	}
	
	boolean reffereingSameImage(HashMap<String,RoiObj> selectedRois) {
		boolean hasSameImp = true;
		Set<String> keys = selectedRois.keySet();
		ImagePlus firstImp = null;
		for(String key:keys) {
			if(firstImp == null) {
				firstImp = selectedRois.get(key).getImage();
				continue;
			}
			ImagePlus imp = selectedRois.get(key).getImage();
			if(!imp.equals(firstImp)) hasSameImp = false;
		}
		return hasSameImp;
	}
	
	RoiObj[] getSelectedRoisAsArray(HashMap<String,RoiObj> selectedRois) {
		RoiObj[] array = new RoiObj[selectedRois.size()];
		Set<String> keys = selectedRois.keySet();
		int pos = 0;
		for(String key:keys) {
			array[pos++] = selectedRois.get(key);
		}
		return array;
	}
	
	public void showTop() {
		toFront(); // brings to front without setAlwaysOnTop
//		requestFocus();
	}
	
	/*
	 * see also, RoiObj.class:findColorNameByColor
	 */
	public static String findColorNameByColor(Color c) {
		String candidateColorName = null;
		int rgbDistance = -1;
		for(Field f : Color.class.getFields()) {
			Color sys_c = null;
			try {
				sys_c = (Color) f.get(null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			if(sys_c == null) {
				continue;
			}
			int dif_r = Math.abs(c.getRed() - sys_c.getRed());
			int dif_g = Math.abs(c.getGreen() - sys_c.getGreen());
			int dif_b = Math.abs(c.getBlue() - sys_c.getBlue());
			int sum = dif_r+dif_g+dif_b;
			if(sum == 0) {
				return f.getName().trim().toLowerCase();
			}else {
				if (rgbDistance == -1) {
					rgbDistance = sum;
					candidateColorName = f.getName().trim().toLowerCase();
				}
				if(rgbDistance > sum) {
					rgbDistance = sum;
					candidateColorName = f.getName().trim().toLowerCase();
				}
			}
		}
		return candidateColorName;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		String command = e.getActionCommand();
		if (command.equals("Test")) {
			test();
		}else if (command.equals(RoiFunctions.Measure.name())) {
			measure();
		}else if (command.equals(RoiFunctions.RoiInfoLabeling.name())) {
			roiInfoLabeling();
		}else if (command.equals(RoiFunctions.Delete.name())) {
			delete();
		}else if (command.equals(RoiFunctions.LineAndColor.name())) {
			lineAndColor();
		}else if (command.equals(RoiFunctions.Update.name())) {
			updateState();
			
		//more functions
		}else if (command.equals(moreButtonLabel)) {
			Point ploc = panel.getLocation();
			Point bloc = ((JButton)e.getSource()).getLocation();
			pm.show(this, ploc.x, patList.getHeight()+bloc.y+moreButton.getHeight()+3);
		}else if (command.equals(RoiFunctions.Open.name())) {
			open(null);
		}else if (command.equals(RoiFunctions.Save.name())) {
			Thread t1 = new Thread(new Runnable() {
				public void run() {save();}
			});
			t1.start();
		}else if (command.equals(RoiFunctions.SplineFit.name())) {
			splineFit();
		}else if(command.equals(RoiFunctions.ConvertToPolygon.name())) {
			convert2Polygon();
		}else if (command.equals(RoiFunctions.Fill.name())) {
			fill();
		}else if (command.equals(RoiFunctions.Draw.name())) {
			paintRoiOnImage();
		}else if (command.equals(RoiFunctions.Capture.name())) {
			capture();
		}else if (command.equals(RoiFunctions.OR_Combine.name())) {
			combine();
		} else if (command.equals(RoiFunctions.Split.name())) {
			split();
		}else if (command.equals(RoiFunctions.AND.name())) {
			and();
		}else if (command.equals(RoiFunctions.XOR.name())) {
			xor();
//		}else if (command.equals("Add Particles")) {
//			//addParticles();
//		}else if (command.equals("Multi Measure")) {
////			multiMeasure("");
//		}else if (command.equals("Multi Plot")) {
////			multiPlot();
//		}else if (command.equals("Multi Crop")) {
////			multiCrop();
//		}else if (command.equals("Sort")) {
////			sort();
//		}else if (command.equals("Specify...")) {
////			specify();
//		}else if (command.equals("Remove Positions...")) {
////			removePositions(SHOW_DIALOG);
//		}else if (command.equals("Labels...")) {
////			labels();
//		}else if (command.equals("List")) {
////			listRois();
//		}else if (command.equals("Interpolate ROIs")) {
////			interpolateRois();
//		}else if (command.equals("Translate...")) {
////			translate();
//		}else if (command.equals("Help")) {
////			help();
//		}else if (command.equals("Options...")) {
////			options();
//		}else if (command.equals("\"Show All\" Color...")) {
////			setShowAllColor();
		}
	}

	@Override
	public Iterator<RoiObj> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * run when list selected
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void valueChanged(ListSelectionEvent e) {
		/*
		 * e.getValueIsAdjusting() = true :: mouse pressed
		 */
		if (e.getSource() instanceof JList && e.getValueIsAdjusting()) {
			for(String roiID : rois.keySet()) {
				rois.get(roiID).setActiveOverlayRoi(false);
			}
			selectedRois = new HashMap<>();
			JList<String> roiList = (JList<String>) e.getSource();
			List<String> selected = roiList.getSelectedValuesList();
			if(selected != null && selected.size() > 0) {
				for(String id:selected) {
					rois.get(id).setActiveOverlayRoi(true);
					selectedRois.put(id, rois.get(id));
				}
			}
			//show image 
			int selected_size = selectedRois.size();
			if( selected_size == 1) {
				Set<String> key = selectedRois.keySet();
				String k = key.iterator().next();
				RoiObj r = rois.get(k);
				r.getSlideGlass().getPraparat().setImagePositionTo(r.getSlideGlass());
				toFront();
			}
		}
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {}

	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	/**
	 * run when drop down patient list changed
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getSource() instanceof JComboBox) {
			JComboBox<String> patCombo = (JComboBox<String>) e.getSource();
			String selectedPatID = patCombo.getItemAt(patCombo.getSelectedIndex());
			if(selectedPatID != null) {
				updateRoiObjList(patCombo.getItemAt(patCombo.getSelectedIndex()));
			}
		}
	}

}
