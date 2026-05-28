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
package com.vis.core.view.D2.roi;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.vis.core.view.D2.ui.glasses.*;
import com.vis.db.DatabaseHandler;
import com.vis.configuration.ConfigInfo;
import com.vis.configuration.RoiDBKey;
import com.vis.configuration.GraphyProp;
import com.vis.configuration.Resources;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.OptionDialog;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.listener.RoiObjListener;
import com.vis.core.util.Platform;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.*;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.io.Opener;
import ij.io.RoiDecoder;
import ij.io.RoiEncoder;
import ij.io.SaveDialog;
import ij.process.ColorProcessor;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class RoiObjManager extends JFrame implements ActionListener, ItemListener, ListSelectionListener, Iterable<RoiObj>{
	
	boolean isDebug = Utils.isDebug;
	HashMap<String, JTextField> roiInfoFields;
	RoiObj currentRoi;//current only one selected roi
	HashMap<String, JTextField> multiDimFields; 
	
	enum Functions{
		Measure,
		Delete,
		LineAndColor,
		Update,
		Duplicate,
		GroupTo3D,
		Ungroup3D,
		Move,
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
	
	private static final int BUTTONS = 11;//num of functions
	private static String moreButtonLabel = "More "+'\u00bb';
	private JComboBox<String> patList;
	private DefaultComboBoxModel<String> patComboModel;
	private JPanel roiInfoPanel;
	private JPanel funcPanel;//list panel
	private static RoiObjManager instance;
	private JList<String> list;//roi obj list
	private DefaultListModel<String> listModel;//roi obj list model
	private HashMap<String, RoiObj> rois = new HashMap<>();//rois in listed
	private HashMap<String,RoiObj> selectedRois = new HashMap<>();//selected on list
	private JPopupMenu pm;
//	private JCheckBox labelsCheckbox = new JCheckBox("Labels", false);
	
	private static String errorMessage;
	
	/*
	 * Editable roi context info.
	 */
	final RoiDBKey[] roiInfo = new RoiDBKey[] {
			RoiDBKey.Name,
			RoiDBKey.Position,
			RoiDBKey.RoiGroup,
			RoiDBKey.RoiLabel,//lesion or lymph node
			RoiDBKey.ObjectType,//target or non target or findings
			RoiDBKey.Organ,//
			RoiDBKey.Description,
			RoiDBKey.StudyDate,
			RoiDBKey.CrossSection//axi,cor,sag
			};
	
	/*
	 * used for Viewer2DScreen
	 */
	private RoiObjManager() {
		super("Analysis Assistant");
		if (instance!=null) {
			return;
		}
		instance = this;
		errorMessage = null;
		setUp();
		
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				/*
				 * to avoid lost info caused by forgetting "save info".
				 */
				roiInfoLabeling();
				if(currentRoi != null) {
					currentRoi.setActiveOverlayRoi(false);
				}
			}
		});
		
		WindowManager.addWindow(this);
		setAlwaysOnTop(true);
	}
	
	private void setUp() {
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setIconImage(Resources.RoiObjManagerWinIcon.loadIconFromResource().getImage());
		setSize(650,300);
		setLayout(new BorderLayout());
		setLocationRelativeTo(Viewer2DScreen.getInstance());
		/*
		 * For WindowManager
		 */
		setName(ConfigInfo.RoiManager.toString());
		
		patList = new JComboBox<>();
		patComboModel = new DefaultComboBoxModel<>();
		patList.setModel(patComboModel);
		patList.setEditable(false);
		patList.addItemListener(this);
		add(patList, BorderLayout.NORTH);
		
		list = new JList<>();
		listModel = new DefaultListModel<>();
		list.setModel(listModel);
		list.addListSelectionListener(this);
		if (Platform.isLinux()) list.setBackground(Color.white);
		JScrollPane scrollPane = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);
		
		//Roi Info Panel
		roiInfoPanel = new JPanel();
		addRoiInfoFields();
		JScrollPane jsp = new JScrollPane(roiInfoPanel);
		jsp.setPreferredSize(new Dimension(320, 0));
		add(jsp, BorderLayout.WEST);
		
		//buttons
		funcPanel = new JPanel();
		int nButtons = BUTTONS;
		funcPanel.setLayout(new GridLayout(nButtons, 1, 3, 3));
		addMainFeatures();
		addPopupMenu();
		add(funcPanel, BorderLayout.EAST);
	}
	
	public static RoiObjManager getInstance() {
		if(RoiObjManager.instance == null) {
			RoiObjManager.instance = new RoiObjManager();
		}
		return RoiObjManager.instance;
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
		addButton(Functions.Measure.name());
		addButton(Functions.Delete.name());
		addButton(Functions.LineAndColor.name());
		addButton(Functions.Update.name());
		addButton(Functions.Duplicate.name());
		addButton(Functions.GroupTo3D.name());
		addButton(Functions.Ungroup3D.name());
		addButton(moreButtonLabel);
		if(isDebug) {
			addButton("Test");
		}
//		labelsCheckbox.addItemListener(this);
//		panel.add(labelsCheckbox);
	}

	void addPopupMenu() {
		pm = new JPopupMenu();
		//functional features
		addPopupItem(Functions.Save.name());
		addPopupItem(Functions.Open.name());
//		addPopupItem(RoiFunctions.Fill.name());//not tested
//		addPopupItem(RoiFunctions.Draw.name());//not tested
		addPopupItem(Functions.Capture.name());//not tested
		pm.addSeparator();
		
		//roi edit
		addPopupItem(Functions.Move.name()); // ★追加: Moveメニュー
		addPopupItem(Functions.OR_Combine.name());
		addPopupItem(Functions.Split.name());
		addPopupItem(Functions.AND.name());
		addPopupItem(Functions.XOR.name());
		addPopupItem(Functions.SplineFit.name());
		addPopupItem(Functions.ConvertToPolygon.name());
		
//		addPopupItem("Labels...");
//		addPopupItem("Interpolate ROIs");
//		addPopupItem("Translate...");
	}
	
	void addButton(String label) {
		JButton b = new JButton(label);
		b.addActionListener(this);
		if(funcPanel != null) {
			funcPanel.add(b);
		}
	}

	void addPopupItem(String s) {
		JMenuItem mi=new JMenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}
	
	void addRoiInfoFields() {
		roiInfoFields = new HashMap<>();
		multiDimFields = new HashMap<>(); // ★追加: マップの初期化

		GridBagLayout l = new GridBagLayout();
		roiInfoPanel.setLayout(l);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(5, 5, 5, 5);

		// 1. 既存のメインプロパティの生成
		for (int i = 0; i < roiInfo.length; i++) {
			JLabel lbl = new JLabel(roiInfo[i].name() + ":");
			JTextField tf = new JTextField(10);
			tf.setName(roiInfo[i].name());
			if (roiInfo[i] == RoiDBKey.StudyDate) {
				tf.setInputVerifier(new DateInputVerifier("yyyy/MM/dd"));
			}
			roiInfoFields.put(roiInfo[i].name(), tf);

			gbc.gridx = 0;
			gbc.gridy = i;
			gbc.weightx = 0.0;
			gbc.gridwidth = 1;
			gbc.fill = GridBagConstraints.NONE;
			gbc.anchor = GridBagConstraints.WEST;
			roiInfoPanel.add(lbl, gbc);

			gbc.gridx = 1;
			gbc.weightx = 1.0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			roiInfoPanel.add(tf, gbc);
		}

		// 2. ★追加: 多次元プロパティ用フィールド（Dim_C, Dim_Z, Dim_T）の生成と配置
		String[] dimKeys = { "Dim_C", "Dim_Z", "Dim_T" };
		String[] dimLabels = { "Dim_C (Channel, -1=ALL):", "Dim_Z (Slice, -1=ALL):", "Dim_T (Time, -1=ALL):" };

		for (int j = 0; j < dimKeys.length; j++) {
			JLabel lbl = new JLabel(dimLabels[j]);
			JTextField tf = new JTextField(10);
			tf.setName(dimKeys[j]);
			multiDimFields.put(dimKeys[j], tf);

			gbc.gridx = 0;
			gbc.gridy = roiInfo.length + j; // 既存プロパティの下に配置
			gbc.weightx = 0.0;
			gbc.gridwidth = 1;
			gbc.fill = GridBagConstraints.NONE;
			gbc.anchor = GridBagConstraints.WEST;
			roiInfoPanel.add(lbl, gbc);

			gbc.gridx = 1;
			gbc.weightx = 1.0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			roiInfoPanel.add(tf, gbc);
		}

		// 3. Save/Updateボタンの配置（位置を多次元フィールドの下に調整）
		gbc.gridx = 0;
		gbc.gridy = roiInfo.length + dimKeys.length; // ★修正
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.CENTER;

		JButton saveBtn = new JButton("Save/Update Info");
		saveBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (list.getSelectedIndices().length > 1) {
					PopUpMessage.showDialog(list, "Select one", "Please select a roi.", JOptionPane.OK_OPTION,
							JOptionPane.INFORMATION_MESSAGE);
					return;
				}
				String rid = list.getSelectedValue();
				RoiObj r = selectedRois.get(rid);
				if (r != null) {
					// 1. メインプロパティの取得
					HashMap<String, String> mainProps = new HashMap<>();
					for (RoiDBKey ck : roiInfo) {
						if (ck != RoiDBKey.Position) {
							mainProps.put(ck.name(), roiInfoFields.get(ck.name()).getText());
						}
					}

					// 2. 入力された多次元プロパティ（Dim_C, Z, T）のパース
					String dimCStr = multiDimFields.get("Dim_C").getText().trim();
					String dimZStr = multiDimFields.get("Dim_Z").getText().trim();
					String dimTStr = multiDimFields.get("Dim_T").getText().trim();

					int newC = dimCStr.isEmpty() ? -1 : Integer.parseInt(dimCStr);
					int newZ = dimZStr.isEmpty() ? -1 : Integer.parseInt(dimZStr);
					int newT = dimTStr.isEmpty() ? -1 : Integer.parseInt(dimTStr);

					Praparat pp = (r.getSlideGlass() != null) ? r.getSlideGlass().getPraparat() : null;
					DatabaseHandler db = DatabaseHandler.getInstance();

					// 3D-ROI グループの判定
					String groupId = r.getProperty(RoiDBKey.RoiGroup.name());
					String shape3D = r.getProperty(RoiMetaContextKey.Shape_3D_Type.name());
					boolean is3D = (groupId != null && !groupId.isEmpty() && shape3D != null && !shape3D.isEmpty());

					if (is3D && pp != null) {
						// ==========================================================
						// ★ 3D-ROI（一式連動更新）の場合
						// ==========================================================
						// グループに属する全スライスの全ROIを収集
						List<RoiObj> groupRois = new ArrayList<>();
						for (SlideGlass s : pp.getAllSlides().values()) {
							if (s == null) continue;
							for (RoiObj roiInSlice : s.getRois()) {
								if (groupId.equals(roiInSlice.getProperty(RoiDBKey.RoiGroup.name()))) {
									groupRois.add(roiInSlice);
								}
							}
						}

						// Zの移動差分（現在のZと新しい入力Zの差）を計算
						String oldZStr = r.getProperty("Dim_Z");
						int oldZ = (oldZStr != null && !oldZStr.isEmpty()) ? Integer.parseInt(oldZStr) : newZ;
						int diffZ = newZ - oldZ;

						// グループ内の全ROIのプロパティを更新
						for (RoiObj roi : groupRois) {
							// 共通のメインプロパティをコピー
							for (String k : mainProps.keySet()) {
								roi.setProperty(k, mainProps.get(k));
							}

							// C と T は一式すべてのプロパティを指定値に強制同期
							roi.setProperty("Dim_C", String.valueOf(newC));
							roi.setProperty("Dim_T", String.valueOf(newT));

							// Z軸は3D構造を壊さないように配慮して処理
							if (roi == r) {
								// 画面から直接操作されたターゲットROIは指定されたZに物理移動
								roi.setProperty("Dim_Z", String.valueOf(newZ));
							} else if ("FREEFORM".equals(shape3D) && diffZ != 0) {
								// FreeForm等の場合は、一式としてZ軸方向に平行移動（シフト）させる
								String gZStr = roi.getProperty("Dim_Z");
								int gZ = (gZStr != null && !gZStr.isEmpty()) ? Integer.parseInt(gZStr) : 0;
								roi.setProperty("Dim_Z", String.valueOf(Math.max(0, gZ + diffZ)));
							}

							// 1次元Positionの逆算同期
							int z = Integer.parseInt(roi.getProperty("Dim_Z"));
							int c = Integer.parseInt(roi.getProperty("Dim_C"));
							int t = Integer.parseInt(roi.getProperty("Dim_T"));
							int newZct = pp.calcZctIndex(new int[]{z, c, t});
							roi.setProperty(RoiDBKey.Position.name(), String.valueOf(newZct + 1));

							// DB保存
							if (db != null) db.insertRoi(roi.readContext());
						}

						// SPHERE の場合は、マスターのZ位置移動に合わせて、全Slaveの3D形状を自動再展開・再配置させる
						if ("SPHERE".equals(shape3D)) {
							// マスターを特定
							RoiObj masterRoi = null;
							for (RoiObj roi : groupRois) {
								if ("true".equals(roi.getProperty(RoiMetaContextKey.Is3D_Master.name()))) {
									masterRoi = roi;
									break;
								}
							}
							if (masterRoi == null) masterRoi = r;
							// 3Dモデル駆動による全Slave追従生成
							new com.vis.core.view.D3.roi.SphereRoi3D(masterRoi).updateFrom2D(masterRoi);
						}

						// グループ全体の所属SlideGlass（引越し）と描画をリフレッシュ
						for (RoiObj roi : groupRois) {
							if (pp != null) pp.redispatchRoi(roi.getProperty(RoiDBKey.RoiID.name()));
						}

					} else {
						// ==========================================================
						// ★ 2D-ROI（単独更新：従来通り）の場合
						// ==========================================================
						for (String k : mainProps.keySet()) {
							r.setProperty(k, mainProps.get(k));
						}
						r.setProperty("Dim_C", String.valueOf(newC));
						r.setProperty("Dim_Z", String.valueOf(newZ));
						r.setProperty("Dim_T", String.valueOf(newT));

						if (newC != -1 && newZ != -1 && newT != -1 && pp != null) {
							int newZctIndex = pp.calcZctIndex(new int[]{newZ, newC, newT});
							String calculatedPos = String.valueOf(newZctIndex + 1);
							r.setProperty(RoiDBKey.Position.name(), calculatedPos);
							roiInfoFields.get(RoiDBKey.Position.name()).setText(calculatedPos);
						} else {
							r.setProperty(RoiDBKey.Position.name(), "0");
							roiInfoFields.get(RoiDBKey.Position.name()).setText("0");
						}

						if (db != null) db.insertRoi(r.readContext());
						if (pp != null) pp.redispatchRoi(rid);
					}

					// 画面全体の再描画
					if (pp != null) {
						for (SlideGlass sg : pp.getAllSlides().values()) {
							sg.repaintCanvasGlass();
						}
					}
					updateState(); // リストの同期リフレッシュ
				}
			}
		});
		roiInfoPanel.add(saveBtn, gbc);
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
		if(isVisible()) {
			patList.revalidate();
			patList.repaint();
		}
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
		
		if(Viewer2DScreen.getInstance() != null) {
			StageView stage = Viewer2DScreen.getInstance().getStageViewAt(patID);
			if(stage != null) {
				List<Object[]> prapCons = stage.getAllPraparatContextInfoSet();
				for (Object[] uids : prapCons) {
					// get current praparat
					String studyUID = (String) uids[1];
					String seriesUID = (String) uids[2];
					String[] sopUIDSet = (String[]) uids[3];
					Praparat prap = stage.getEyepiece().getPraparatAt(patID, studyUID, seriesUID, sopUIDSet);
					ConcurrentHashMap<Integer,SlideGlass> slides = prap.getAllSlides();
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
			}
		}
		
		if(isVisible()) {
			list.repaint();
		}
	}
	
	/**
	 * re-load rois in patient
	 */
	public void updateState() {
		updatePatientList();
		//clear all info
		currentRoi = null;//IMPORTANT to avoid auto save by list selection
		resetRoiInfoFields();
		if(patList == null || patList.getItemCount()==0) {
			return;
		}
		String selectedPatID = patList.getItemAt(patList.getSelectedIndex());
		if (selectedPatID == null) {
			return;
		}
		updateRoiObjList(selectedPatID);// execute from change listener
	}
	
	private void resetRoiInfoFields() {
		for(RoiDBKey ck:roiInfo) {
			roiInfoFields.get(ck.name()).setText(null);
		}
		String[] dims = new String[] {RoiMetaContextKey.Dim_C.name(),RoiMetaContextKey.Dim_Z.name(),RoiMetaContextKey.Dim_T.name()};
		for(String d : dims) {
			multiDimFields.get(d).setText(null);
		}
	}
	
	public void addRoiObj(RoiObj roi) {
		String id = roi.getProperty(RoiDBKey.RoiID.name());
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
		Log.logger.fine(currentRoi.x+","+currentRoi.y);
	}
	
	public boolean inList(String roiID) {
		if(rois == null || rois.size() == 0) {
			return false;
		}
		return rois.containsKey(roiID);
	}
	
	/*
	 * old code
	 */
//	private void measure() {
//		if(selectedRois == null || selectedRois.size() < 1) {
//			return;
//		}
//		for(String k : selectedRois.keySet()) {
//			RoiObj roiObj = selectedRois.get(k);
//			RoiAnalyzer ana = new RoiAnalyzer(roiObj);
//			List<HashMap<Measurements/*enum*/, Double>> res = ana.measure();
//			for(HashMap<Measurements/*enum*/, Double> r : res) {
//				ana.showInResultWindow(r);
//			}
//		}
//	}
	
	private void measure() {
	    if(selectedRois == null || selectedRois.size() < 1) {
	        return;
	    }

	    // 1. ROIをグループIDごとに仕分けるマップ
	    // キー: RoiGroup (グループ化されていないものは個別のユニークID等をキーにするか別リストへ)
	    HashMap<Integer, List<RoiObj>> groupedRois = new HashMap<>();
	    List<RoiObj> singleRois = new ArrayList<>();

	    for(String k : selectedRois.keySet()) {
	        RoiObj roiObj = selectedRois.get(k);
	        String groupStr = roiObj.getProperty(RoiDBKey.RoiGroup.name());
	        
	        int groupId = -1;
	        try {
	            if(groupStr != null && !groupStr.isEmpty()) {
	                groupId = Integer.parseInt(groupStr);
	            }
	        } catch (NumberFormatException e) {
	            groupId = -1;
	        }

	        // グループIDが設定されている（1以上など）場合はグループへ、それ以外は単独へ
	        if (groupId > 0) {
	            groupedRois.computeIfAbsent(groupId, val -> new ArrayList<>()).add(roiObj);
	        } else {
	            singleRois.add(roiObj);
	        }
	    }

	    // 2. 単独ROIの計測（従来の処理）
	    for (RoiObj roiObj : singleRois) {
	        RoiAnalyzer ana = new RoiAnalyzer(roiObj);
	        List<HashMap<Measurements, Double>> res = ana.measure();
	        for(HashMap<Measurements, Double> r : res) {
	            ana.showInResultWindow(r);
	        }
	    }

	    // 3. グループ化されたROI（3D-ROI）の体積計測
	    for (Map.Entry<Integer, List<RoiObj>> entry : groupedRois.entrySet()) {
	        int groupId = entry.getKey();
	        List<RoiObj> groupList = entry.getValue();

	        // 複数スライスにまたがっている場合のみ体積計算
	        if (groupList.size() > 1) {
	            measureVolume(groupId, groupList);
	        } else {
	            // グループ設定されているが1枚しかない場合は2Dとして計算
	            RoiAnalyzer ana = new RoiAnalyzer(groupList.get(0));
	            List<HashMap<Measurements, Double>> res = ana.measure();
	            for(HashMap<Measurements, Double> r : res) {
	                ana.showInResultWindow(r);
	            }
	        }
	    }
	}
	
	/*
	 * 参考メソッド簡易版
	 * TODO：マスクからメッシュで計算する
	 */
	private void measureVolume(int groupId, List<RoiObj> groupList) {
	    double totalVolume = 0.0;
	    
	    for (RoiObj roi : groupList) {
	        SlideGlass sg = roi.getSlideGlass();
	        if (sg == null) continue;

	        // 1. 各ROIの統計情報を取得（ここで物理面積 Area が計算される）
	        ImageStatistics stats = roi.getStatistics();
	        if (stats == null) continue;

	        // stats.area は Calibration (ピクセル幅×ピクセル高) が考慮された面積 (mm^2)
	        double area = stats.area;

	        // 2. スライスの厚み（Z方向の深さ）を取得
	        ij.measure.Calibration cal = sg.getOriginalCalibration();
	        double sliceThickness = 1.0;
	        if (cal != null && cal.pixelDepth > 0) {
	            sliceThickness = cal.pixelDepth;
	        } else {
	            // フォールバック: CalibrationにpixelDepthがない場合はDICOMヘッダから直接取得
	            com.vis.dicom.DicomObject header = sg.getHeader();
	            if (header != null) {
	                sliceThickness = header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices, 
	                                 header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
	            }
	        }

	        // 3. 体積を積算: 体積(mm^3) = 面積(mm^2) × 厚み(mm)
	        totalVolume += (area * sliceThickness);
	    }

	    // 4. 結果の出力（ここでは仮にコンソールとメッセージダイアログに出力）
	    // ※ 実際は RoiAnalyzer や Measurement Table に新しい行として追加するロジックに繋ぎます
	    String msg = String.format("3D-ROI Group [%d] Volume: %.2f mm³", groupId, totalVolume);
	    com.vis.core.log.Log.logger.info(msg);
	    javax.swing.JOptionPane.showMessageDialog(this, msg, "Volume Measurement", javax.swing.JOptionPane.INFORMATION_MESSAGE);
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
					/*
					 * save undo
					 * notify to listener will done in canvas glass.
					 */
					slide.deleteRoi(r);
				}else {
					HashMap<RoiDBKey, String> uids = r.getUIDs();
					String patID = uids.get(RoiDBKey.PatientID);
					String studyUID = uids.get(RoiDBKey.StudyInstanceUID);
					String seriesUID = uids.get(RoiDBKey.SeriesInstanceUID);
					String sopUID = uids.get(RoiDBKey.SOPInstanceUID);
					String roiID = uids.get(RoiDBKey.RoiID);
					DatabaseHandler.getInstance().deleteRoi(patID, studyUID, seriesUID, sopUID, roiID);
					r.notifyListeners(RoiObjListener.DELETED);
				}
			}
		}
		updateState();
	}
	
	// ★ 追加：安全にROIを複製するメソッド
	private void duplicate() {
		if (selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select roi first...");
			return;
		}

		for (String roiID : selectedRois.keySet()) {
			RoiObj originalRoi = selectedRois.get(roiID);
			if (originalRoi == null || originalRoi.getSlideGlass() == null)
				continue;

			// 1. 形状のクローンを作成
			RoiObj newRoi = (RoiObj) originalRoi.clone();

			// 2. プロパティのディープコピー
			java.util.Properties newProps = new java.util.Properties();
			if (originalRoi.getProperties() != null) {
				for (Object key : originalRoi.getProperties().keySet()) {
					newProps.put(key, originalRoi.getProperties().get(key));
				}
			}
			newRoi.props = newProps;

			// 3. 新しいユニークな RoiID を生成してセット
			String newRoiId = RoiObj.createRoiIndex();
			newRoi.setProperty(RoiDBKey.RoiID.name(), newRoiId);

			// 4. 名前を分かりやすく「- Copy」にする
			String oldName = originalRoi.getName();
			if (oldName != null) {
				newRoi.setName(oldName + " - Copy");
			}

			// 6. スライドに追加（ここでDBにも自動保存される）
			originalRoi.getSlideGlass().addRoi(newRoi);
		}

		// リストを更新して新しいROIを表示
		updateState();
	}
	
	private void groupTo3d() {
		// リストで選択されている複数のROIを取得
		int[] selectedIndices = list.getSelectedIndices();
		if (selectedIndices.length < 2) {
			PopUpMessage.showDialog(list, "Select multiple", "Please select at least 2 ROIs to bundle into 3D.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// 新しい共通のグループIDを生成
		int uniqueGroupId = (int) (System.currentTimeMillis() % 1000000000L);
		String newGroupId = String.valueOf(uniqueGroupId);
		
		DatabaseHandler db = DatabaseHandler.getInstance();

		for (int index : selectedIndices) {
			String rid = list.getModel().getElementAt(index);
			RoiObj r = selectedRois.get(rid);
			if (r != null) {
				// 3D FreeForm としてのアイデンティティを付与
				r.setProperty(RoiDBKey.RoiGroup.name(), newGroupId);
				r.setProperty(RoiMetaContextKey.Shape_3D_Type.name(), "FREEFORM");
				r.setProperty(RoiMetaContextKey.Is3D_Master.name(), "true"); // FreeFormは全スライスが主役（マスター）
				
				// DBに上書き保存
				if (db != null) {
					db.insertRoi(r.readContext());
				}
			}
		}
		
		updateState(); // リストの更新
		PopUpMessage.showDialog(list, "Success", "Selected ROIs are successfully bundled into 1 FreeForm 3D ROI.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
	}
	
	/**
	 * グループ全体の解散（Ungroup）: リストからグループ化されたROIをすべて選択してボタンを押すと、グループが完全に解散し、バラバラの2D
	 * ROIに戻ります。
	 * 
	 * 特定のROIだけの離脱（Detach）:
	 * グループ化されたROIのうち、特定の1枚だけを選択してボタンを押すと、そのROIだけがグループから抜け（2Dになり）、残りのメンバーは引き続き3Dグループとして維持されます。
	 */
	private void ungroup3d() {
		int[] selectedIndices = list.getSelectedIndices();
		if (selectedIndices.length == 0) {
			PopUpMessage.showDialog(list, "Select ROIs", "Please select ROIs to ungroup.", JOptionPane.OK_OPTION,
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		DatabaseHandler db = DatabaseHandler.getInstance();
		boolean updated = false;

		for (int index : selectedIndices) {
			String rid = list.getModel().getElementAt(index);
			RoiObj r = selectedRois.get(rid);
			if (r != null) {
				// 3Dグループに属しているか確認
				String shape3D = r.getProperty(RoiDBKey.RoiMetaProperties.name()); // fallback
				if (shape3D == null)
					shape3D = r.getProperty(RoiMetaContextKey.Shape_3D_Type.name());

				if ("FREEFORM".equals(shape3D) || "SPHERE".equals(shape3D)) {
					// 1. 新しい独自のグループIDを付与して、元のグループから切り離す
					int uniqueGroupId = (int) (System.currentTimeMillis() % 1000000000L) + index;
					r.setProperty(RoiDBKey.RoiGroup.name(), String.valueOf(uniqueGroupId));

					// 2. 3Dとしてのアイデンティティ（メタデータ）をすべて消去し、ただの2D ROIに戻す
					r.setProperty(RoiMetaContextKey.Shape_3D_Type.name(), null);
					r.setProperty(RoiMetaContextKey.Is3D_Master.name(), null);
					r.setProperty(RoiMetaContextKey.Is3D_Slave.name(), null);
					r.setProperty(RoiMetaContextKey.Sphere_Radius_mm.name(), null);
					r.setProperty(RoiMetaContextKey.Sphere_Center_IPP.name(), null);

					// 3. DBに上書き保存
					if (db != null) {
						db.insertRoi(r.readContext());
					}
					updated = true;

					// 画面の再描画
					if (r.getSlideGlass() != null) {
						r.getSlideGlass().repaintCanvasGlass();
					}
				}
			}
		}

		if (updated) {
			updateState(); // リストの更新
			PopUpMessage.showDialog(list, "Success", "Selected ROIs have been detached and reverted to 2D ROIs.",
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
		} else {
			PopUpMessage.showDialog(list, "Info", "Selected ROIs are already standard 2D ROIs.", JOptionPane.OK_OPTION,
					JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	// ==========================================================
	// ★ 追加・修正: リアルタイムプレビュー対応の ROI XYZ 微調整機能
	// ==========================================================
	private void moveRois() {
		if (selectedRois == null || selectedRois.size() < 1) {
			JOptionPane.showMessageDialog(this, "Select ROI(s) first.");
			return;
		}

		// 1. カスタムダイアログの作成（モーダル）
		JDialog dialog = new JDialog(this, "Interactive Move ROI(s)", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setLayout(new BorderLayout());

		JPanel inputPanel = new JPanel(new GridLayout(3, 2, 5, 5));
		inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// スピンボタン（微調整しやすいように JSpinner を採用）
		javax.swing.JSpinner xSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(0, -9999, 9999, 1));
		javax.swing.JSpinner ySpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(0, -9999, 9999, 1));
		javax.swing.JSpinner zSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(0, -9999, 9999, 1));

		inputPanel.add(new JLabel("X shift (pixels):"));
		inputPanel.add(xSpinner);
		inputPanel.add(new JLabel("Y shift (pixels):"));
		inputPanel.add(ySpinner);
		inputPanel.add(new JLabel("Z shift (slices):"));
		inputPanel.add(zSpinner);

		dialog.add(inputPanel, BorderLayout.CENTER);

		// ボタンパネル
		JPanel btnPanel = new JPanel();
		JButton btnOk = new JButton("OK / Apply");
		JButton btnCancel = new JButton("Cancel / Revert");
		btnPanel.add(btnOk);
		btnPanel.add(btnCancel);
		dialog.add(btnPanel, BorderLayout.SOUTH);

		// 状態管理用（これまでに何ピクセル動かしたかを記録）
		final int[] lastShifts = { 0, 0, 0 }; // [0]=X, [1]=Y, [2]=Z
		final boolean[] isCanceled = { false };

		// 2. リアルタイム反映ロジック（スピンボタンを動かすたびに発動）
		// ※ DBアクセスが重くならないよう、150msのディレイ（デバウンス）をかける
		Timer debounceTimer = new Timer(150, e -> {
			int curX = (int) xSpinner.getValue();
			int curY = (int) ySpinner.getValue();
			int curZ = (int) zSpinner.getValue();

			// 前回の位置からの「差分」だけを適用する
			int diffX = curX - lastShifts[0];
			int diffY = curY - lastShifts[1];
			int diffZ = curZ - lastShifts[2];

			if (diffX == 0 && diffY == 0 && diffZ == 0)
				return;

			applyMoveShift(diffX, diffY, diffZ);

			lastShifts[0] = curX;
			lastShifts[1] = curY;
			lastShifts[2] = curZ;
		});
		debounceTimer.setRepeats(false);

		javax.swing.event.ChangeListener changeListener = e -> debounceTimer.restart();
		xSpinner.addChangeListener(changeListener);
		ySpinner.addChangeListener(changeListener);
		zSpinner.addChangeListener(changeListener);

		// 3. OK と Cancel の挙動
		btnOk.addActionListener(e -> dialog.dispose()); // OKならそのまま閉じる（DB保存は既に完了済み）

		btnCancel.addActionListener(e -> {
			isCanceled[0] = true;
			// これまで動かした分を逆向きにシフトしてロールバック（元に戻す）
			applyMoveShift(-lastShifts[0], -lastShifts[1], -lastShifts[2]);
			dialog.dispose();
		});

		// ×ボタンで閉じられた時もCancel扱いにする
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				if (!isCanceled[0]) {
					isCanceled[0] = true;
					applyMoveShift(-lastShifts[0], -lastShifts[1], -lastShifts[2]);
					dialog.dispose();
				}
			}
		});

		dialog.pack();
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true); // ダイアログが閉じるまでここでブロックされる

		if (!isCanceled[0]) {
			updateState(); // 最終的なリストの更新
		}
	}

	// ==========================================================
	// 実際の移動処理エンジン (3D-ROI 一式連動 & ゴースト消去対応)
	// ==========================================================
	private void applyMoveShift(int diffX, int diffY, int diffZ) {
		if (diffX == 0 && diffY == 0 && diffZ == 0)
			return;
		DatabaseHandler db = DatabaseHandler.getInstance();

		List<String> keys = new ArrayList<>(selectedRois.keySet());
		List<String> processedGroups = new ArrayList<>(); // 二重処理防止用
		int scrollTargetZct = -1; // Z移動時のオートスクロール先

		for (String key : keys) {
			RoiObj r = selectedRois.get(key);
			if (r == null)
				continue;

			Praparat pp = (r.getSlideGlass() != null) ? r.getSlideGlass().getPraparat() : null;
			if (pp == null)
				continue;

			String shape3D = r.getProperty(RoiMetaContextKey.Shape_3D_Type.name());
			boolean is3D = (shape3D != null && !shape3D.isEmpty());

			if (is3D) {
				// --- 3D ROI の場合: グループ(一式)として処理 ---
				String groupId = r.getProperty(RoiDBKey.RoiGroup.name());
				if (processedGroups.contains(groupId))
					continue; // すでに処理済みのグループならスキップ
				processedGroups.add(groupId);

				if ("SPHERE".equals(shape3D)) {
					// SPHERE はマスター1つを動かして再展開
					RoiObj masterRoi = findMasterRoi(pp, groupId);
					if (masterRoi == null)
						masterRoi = r;

					// 1. 古いスレーブ（残像）を全スライドから完全消去
					purgeGroupSlaves(pp, groupId, masterRoi.getProperty(RoiDBKey.RoiID.name()));

					// 2. マスターのXY移動
					if (diffX != 0 || diffY != 0) {
						masterRoi.setLocation(masterRoi.getXBase() + diffX, masterRoi.getYBase() + diffY);
					}
					// 3. マスターのZ移動
					if (diffZ != 0) {
						scrollTargetZct = shiftRoiZ(masterRoi, pp, diffZ);
					}

					// 4. DB保存とスレーブの再展開
					if (db != null)
						db.insertRoi(masterRoi.readContext());
					new com.vis.core.view.D3.roi.SphereRoi3D(masterRoi).updateFrom2D(masterRoi);

				} else {
					// FREEFORM 等: グループ内の全ROI（全スライス）を一斉に物理移動させる
					List<RoiObj> allGroupRois = findAllRoisInGroup(pp, groupId);
					for (RoiObj gRoi : allGroupRois) {
						if (diffX != 0 || diffY != 0) {
							gRoi.setLocation(gRoi.getXBase() + diffX, gRoi.getYBase() + diffY);
						}
						if (diffZ != 0) {
							int newZct = shiftRoiZ(gRoi, pp, diffZ);
							if (scrollTargetZct == -1)
								scrollTargetZct = newZct;
						}
						if (db != null)
							db.insertRoi(gRoi.readContext());
					}
				}
			} else {
				// --- 2D ROI の場合: 単独処理 ---
				if (diffX != 0 || diffY != 0) {
					r.setLocation(r.getXBase() + diffX, r.getYBase() + diffY);
				}
				if (diffZ != 0) {
					int newZct = shiftRoiZ(r, pp, diffZ);
					if (scrollTargetZct == -1)
						scrollTargetZct = newZct;
				}
				if (db != null)
					db.insertRoi(r.readContext());
			}
		}

		// --- 画面の同期と再描画 ---
		if (keys.size() > 0) {
			RoiObj firstR = selectedRois.get(keys.get(0));
			Praparat pp = firstR.getSlideGlass().getPraparat();
			if (pp != null) {
				// Z移動があった場合は、対象のスライスへオートスクロール
				if (diffZ != 0 && scrollTargetZct != -1) {
					pp.setImagePositionUsingSlider(scrollTargetZct);
				}
				// 3Dの残像消去や引越しを反映するため、全スライドを一斉再描画
				for (SlideGlass sg : pp.getAllSlides().values()) {
					sg.repaintCanvasGlass();
				}
			}
		}
	}

	// ==========================================================
	// ★ ヘルパーメソッド群
	// ==========================================================

	/**
	 * ROIのZインデックスを更新し、所属するSlideGlassを物理的に引越しさせる
	 */
	private int shiftRoiZ(RoiObj roi, Praparat pp, int diffZ) {
		String dimZStr = roi.getProperty("Dim_Z");
		int currentZ = (dimZStr != null && !dimZStr.isEmpty()) ? Integer.parseInt(dimZStr) : -1;
		if (currentZ == -1)
			return -1;

		int newZ = Math.max(0, currentZ + diffZ);
		roi.setProperty("Dim_Z", String.valueOf(newZ));

		String dimCStr = roi.getProperty("Dim_C");
		String dimTStr = roi.getProperty("Dim_T");
		int currentC = (dimCStr != null && !dimCStr.isEmpty()) ? Integer.parseInt(dimCStr) : -1;
		int currentT = (dimTStr != null && !dimTStr.isEmpty()) ? Integer.parseInt(dimTStr) : -1;

		int newZctIndex = pp.calcZctIndex(new int[] { newZ, currentC, currentT });
		roi.setProperty(RoiDBKey.Position.name(), String.valueOf(newZctIndex + 1));

		SlideGlass oldSg = roi.getSlideGlass();
		SlideGlass newSg = pp.getAllSlides().get(newZctIndex);

		if (newSg != null && oldSg != newSg) {
			if (oldSg != null)
				oldSg.getRois().remove(roi);
			roi.setSlideGlass(newSg, false);
			if (!newSg.getRois().contains(roi))
				newSg.getRois().add(roi);
		}
		return newZctIndex;
	}

	/**
	 * Sphere移動時に残像（ゴースト）を防ぐため、マスター以外の全スレーブをキャンバスから消去する
	 */
	private void purgeGroupSlaves(Praparat pp, String groupId, String masterId) {
		if (pp == null || groupId == null || masterId == null)
			return;
		for (SlideGlass s : pp.getAllSlides().values()) {
			if (s == null)
				continue;
			List<RoiObj> toRemove = new ArrayList<>();
			for (RoiObj r : s.getRois()) {
				if (groupId.equals(r.getProperty(RoiDBKey.RoiGroup.name()))
						&& !masterId.equals(r.getProperty(RoiDBKey.RoiID.name()))) {
					toRemove.add(r);
				}
			}
			s.getRois().removeAll(toRemove);
		}
	}

	/**
	 * 指定グループに属するすべてのROIをリストアップする
	 */
	private List<RoiObj> findAllRoisInGroup(Praparat pp, String groupId) {
		List<RoiObj> list = new ArrayList<>();
		if (pp == null || groupId == null)
			return list;
		for (SlideGlass s : pp.getAllSlides().values()) {
			if (s == null)
				continue;
			for (RoiObj r : s.getRois()) {
				if (groupId.equals(r.getProperty(RoiDBKey.RoiGroup.name()))) {
					list.add(r);
				}
			}
		}
		return list;
	}

	/**
	 * グループIDからマスターROIを探し出す
	 */
	private RoiObj findMasterRoi(Praparat pp, String groupId) {
		if (pp == null || groupId == null)
			return null;
		for (SlideGlass s : pp.getAllSlides().values()) {
			if (s == null)
				continue;
			for (RoiObj r : s.getRois()) {
				if (groupId.equals(r.getProperty(RoiDBKey.RoiGroup.name()))
						&& "true".equals(r.getProperty(RoiMetaContextKey.Is3D_Master.name()))) {
					return r;
				}
			}
		}
		return null;
	}
	
	/**
	 * db.insertRoi(roi.readContext());で新規作成または、存在していれば上書きを促す。
	 * SlideGlass>CanvasGlassからの処理は、CanavasGlassのZCTを強制上書きするので注意。
	 */
	private void saveRoi2DB(RoiObj roi) {
		//save or update
//		SlideGlass slide = roi.getSlideGlass();
//		if(slide != null) {
//			slide.addRoi(roi);//update if already exist
//		}else {
			//save new or update
			DatabaseHandler db = DatabaseHandler.getInstance();
			if(db != null) {
				db.insertRoi(roi.readContext());
			}
//		}
	}
	
	private void roiInfoLabeling() {
		//only 1 roi ...
		if(selectedRois == null || selectedRois.size() == 0 || selectedRois.size() > 1) {
			return;
		}
		if(currentRoi != null) {
			//add properties
			for(RoiDBKey ck : roiInfo) {
				currentRoi.setProperty(ck.name(), roiInfoFields.get(ck.name()).getText());
			}
			//save or update
			saveRoi2DB(currentRoi);
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
		 * here, only change current roi state.
		 */
		String strokeColorString = GraphyProp.findColorNameByColor(roi.getStrokeColor());
		String fillColorString = GraphyProp.findColorNameByColor(roi.getFillColor());
		
		OptionDialog gd = new OptionDialog("Line & Color", this);
		gd.addNumericField("Stroke Width", roi.getStrokeWidth(), 2, 7 /*cols*/, "pixel"/*unit*/);
		gd.addChoice("Stroke Color", new String[]{"white", "blue", "orange", "yellow","red","pink","magenta","green","black"}, strokeColorString);
		gd.addChoice("Fill Color", new String[]{"white", "blue", "orange", "yellow","red","pink","magenta","green","black"}, fillColorString);
		gd.pack();
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
		roi.setStrokeColor(roi.colorFromString(sc, Color.YELLOW));
		roi.setFillColor(roi.colorFromString(fc, Color.WHITE));
	}
	
	/**
	 * If a roi is consistent in any slices, it is considered consistent.
	 * @param pp
	 * @param roi
	 * @return
	 */
	boolean hasConsistency(Praparat pp, RoiObj roi) {
		String pid = roi.getProperty(RoiDBKey.PatientID.name());
		String studyUID = roi.getProperty(RoiDBKey.StudyInstanceUID.name());
		String seriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());
		String sopUID = roi.getProperty(RoiDBKey.SOPInstanceUID.name());
		if(pid == null || studyUID == null || seriesUID == null || sopUID == null) {
			return false;
		}
		ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
		for(Integer pos : slides.keySet()) {
			boolean consistent = true;
			SlideGlass sg = slides.get(pos);
			String[] UIDs = sg.getUIDs();
			if(!UIDs[0].equals(pid)) consistent = false;
			if(!UIDs[1].equals(studyUID)) consistent = false;
			if(!UIDs[2].equals(seriesUID)) consistent = false;
			if(!UIDs[3].equals(sopUID)) consistent = false;
			if(consistent) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * If a roi is consistent in any slices, it is considered consistent.
	 * @param pp
	 * @param roi
	 * @return
	 */
	boolean hasConsistency(Praparat pp, ij.gui.Roi roi) {
		String pid = roi.getProperty(RoiDBKey.PatientID.name());
		String studyUID = roi.getProperty(RoiDBKey.StudyInstanceUID.name());
		String seriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());
		String sopUID = roi.getProperty(RoiDBKey.SOPInstanceUID.name());
		if(pid == null || studyUID == null || seriesUID == null || sopUID == null) {
			return false;
		}
		ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
		for(Integer pos : slides.keySet()) {
			boolean consistent = true;
			SlideGlass sg = slides.get(pos);
			String[] UIDs = sg.getUIDs();
			if(!UIDs[0].equals(pid)) consistent = false;
			if(!UIDs[1].equals(studyUID)) consistent = false;
			if(!UIDs[2].equals(seriesUID)) consistent = false;
			if(!UIDs[3].equals(sopUID)) consistent = false;
			if(consistent) {
				return true;
			}
		}
		return false;
	}
	
	int getSlicePosition(Praparat pp, ij.gui.Roi roi) {
		String pid = roi.getProperty(RoiDBKey.PatientID.name());
		String studyUID = roi.getProperty(RoiDBKey.StudyInstanceUID.name());
		String seriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());
		String sopUID = roi.getProperty(RoiDBKey.SOPInstanceUID.name());
		if(pid == null || studyUID == null || seriesUID == null || sopUID == null) {
			return -1;
		}
		ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
		for(Integer pos : slides.keySet()) {
			boolean consistent = true;
			SlideGlass sg = slides.get(pos);
			String[] UIDs = sg.getUIDs();
			if(!UIDs[0].equals(pid)) consistent = false;
			if(!UIDs[1].equals(studyUID)) consistent = false;
			if(!UIDs[2].equals(seriesUID)) consistent = false;
			if(!UIDs[3].equals(sopUID)) consistent = false;
			if(consistent) {
				return pos;
			}
		}
		return -1;
	}
	
	void openToGraphy(String path) {
		if(patList.getItemCount()==0) {
			JOptionPane.showMessageDialog(this,
					"Open images on 2D Viewer to load ROIs and select a subject from the patient list.");
			return;
		}
		String selectedPatID = patList.getItemAt(patList.getSelectedIndex());
		if (selectedPatID == null) {
			return;
		}
		Eyepiece eye = Viewer2DScreen.getInstance().getEyepieceOnStageWhere(selectedPatID);
		if (eye == null) {
			return;
		}
		ArrayList<Praparat> praps = eye.getSelectingPraparats();
		if(praps.size() == 0) {
			JOptionPane.showConfirmDialog(this, "Please select series to load rois by (Shift + Left Click).");
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
		ij.gui.Roi roi = o.openRoi(path);
		if (roi!=null) {
			loadRoi2Slide(roi, selectedPatID, praps);
		} else {
			JOptionPane.showConfirmDialog(this, "Unable to open ROI at "+path);
		}
	}
	
	public static List<Roi> open(String path) {
		if(path == null || path.length() == 0) {
			return null;
		}
		List<Roi> rois = new ArrayList<>();
		if (path.endsWith(".zip")) {
			List<Roi> rois_ = openZip(path);
			if(rois != null) {
				rois.addAll(rois_);
			}
		}else {
			Opener o = new Opener();
			ij.gui.Roi roi = o.openRoi(path);
			if(roi != null) {
				rois.add(roi);
			}
		}
		return rois;
	}
	
	public static List<Roi> openZip(String path) {
		if(path == null || path.endsWith("zip")) {
			return null;
		}
		ZipInputStream in = null;
		ByteArrayOutputStream out = null;
		int nRois = 0;
		errorMessage = null;
		List<Roi> rois = new ArrayList<>();
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
						rois.add(roi);
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
		return rois;
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
	}
	
	void loadRoi2Slide(Roi roi, String selectedPatID, ArrayList<Praparat> praps) {
		if (roi == null || praps == null || praps.size() == 0) {
			return;
		}
		// convert to roiobj
		RoiObj roiObj = new RoiConverter().convert2RoiObj(roi);
		if(roiObj == null) {
			Log.logger.fine("Cannot import roi...");
			return;
		}
		/*
		 * Load on all selected series. 
		 * If consistent, load only that slide. 
		 * If there is no consistency, priority is given to the instance number. 
		 * If there is no instance number, load to the currently displayed slide.
		 */
		for (Praparat prap : praps) {
			// set roi to series
			int roiFramePos = getSlicePosition(prap, roi);
			int instNo = -1;
			if(roiFramePos >= 0) {
				SlideGlass s = prap.getAllSlides().get(roiFramePos);
				roiObj.setSlideGlass(s, false);
				s.addRoi(roiObj);
			}else {//no consistent roi
				//escape by InstNo
				String roiInstNoString = roiObj.getProperty(RoiDBKey.InstanceNo.name());
				if (roiInstNoString != null) {
					try {
						instNo = Integer.parseInt(roiInstNoString);
					}catch(NumberFormatException e) {
						//do nothing
					}
				}
				if(instNo >= 0) {
					/*
					 * Keys on the slide are not instance numbers, but numbers in reading order
					 */
					Set<Integer> keys = prap.getAllSlides().keySet();
					for (Integer readingOrder : keys) {
						SlideGlass s = prap.getAllSlides().get(readingOrder);
						if (s.getInstanceNo() == instNo) {
							roiObj.setSlideGlass(s, false);
							s.addRoi(roiObj);
						}
					}
				}else {
					//set current slide
					SlideGlass s = prap.getCurrentSlide();
					roiObj.setSlideGlass(s, false);
					s.addRoi(roiObj);
				}
			}
		}
		/*
		 * update
		 */
		updateRoiObjList(selectedPatID);
	}
	
	/**
	 * Save roi file
	 */
	void save() {
		if (rois.size()==0) {
			Log.logger.info("Rois in selection list is empty.");
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
			//save all
			if(selectedRois.size() == 0) {
				try {
					ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
					out = new DataOutputStream(new BufferedOutputStream(zos));
					String[] keys = rois.keySet().toArray(new String[rois.size()]);
					RoiEncoder re = new RoiEncoder(out);
					for (int i=0; i<keys.length; i++) {
						RoiObj roiObj = rois.get(keys[i]);
						String label = roiObj.getProperty(RoiDBKey.RoiID.name());
						// ファイル名の先頭に "0005_" のようにポジションを付与する
						String posStr = roiObj.getProperty(RoiDBKey.Position.name());
						if (posStr != null && !posStr.isEmpty() && !posStr.equals("0")) {
							try {
								int pos = Integer.parseInt(posStr);
								// ゼロ埋め（例: 000012_RoiID.roi）にしてソートしやすくする
								label = String.format("%06d", pos) + "_" + label;
							} catch (NumberFormatException e) {}
						}
						//in here, convert2Roi do roi.setPosition(pos)
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
					Log.logger.warning(errorMessage);
					return;
				} finally {
					if (out!=null)
						try {out.close();} catch (IOException e) {}
				}
			//save selected rois
			}else {
				try {
					ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
					out = new DataOutputStream(new BufferedOutputStream(zos));
					String[] keys = selectedRois.keySet().toArray(new String[selectedRois.size()]);
					RoiEncoder re = new RoiEncoder(out);
					for (int i=0; i<keys.length; i++) {
						RoiObj roiObj = rois.get(keys[i]);
						String label = roiObj.getProperty(RoiDBKey.RoiID.name());
						
						// ファイル名の先頭に "0005_" のようにポジションを付与する
						String posStr = roiObj.getProperty(RoiDBKey.Position.name());
						if (posStr != null && !posStr.isEmpty() && !posStr.equals("0")) {
							try {
								int pos = Integer.parseInt(posStr);
								// ゼロ埋め（例: 000012_RoiID.roi）にしてソートしやすくする
								label = String.format("%06d", pos) + "_" + label;
							} catch (NumberFormatException e) {}
						}
						//roi.setPosition(pos) was done in this.
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
					Log.logger.warning(errorMessage);
					return;
				} finally {
					if (out!=null)
						try {out.close();} catch (IOException e) {}
				}
			}
		}
	}

	/**
	 * Save roi to file.
	 * @param roiObj
	 */
	void saveRoi(RoiObj roiObj) {
		if (roiObj == null) {
			return;
		}
		String name = roiObj.getProperty(RoiDBKey.RoiID.name());
		// ファイル名の先頭に "0005_" のようにポジションを付与する
		String posStr = roiObj.getProperty(RoiDBKey.Position.name());
		if (posStr != null && !posStr.isEmpty() && !posStr.equals("0")) {
			try {
				int pos = Integer.parseInt(posStr);
				// ゼロ埋め（例: 000012_RoiID.roi）にしてソートしやすくする
				name = String.format("%06d", pos) + "_" + name;
			} catch (NumberFormatException e) {}
		}
		//in here, convert2Roi do roi.setPosition(pos)
		Roi roi = new RoiConverter().convert2Roi(roiObj);
		if(roi == null) {
			Log.logger.log(Level.SEVERE, "Roi conversion was failed. Cannot save rois...");
			return;
		}
		String path = null;
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
			Log.logger.warning(e.getMessage());
			return;
		}
	}
	
	/**
	 * save roi to dest.
	 * 
	 * This method does not rename by adding a position string(e.g., 0001) in head of file name.
	 * 
	 * @param roiObj
	 * @param dest : ./folder/roi_instance.roi
	 */
	public static void saveRoi(RoiObj roiObj, String dest) {
		
		if (roiObj == null) {
			return;
		}
		Roi roi = new RoiConverter().convert2Roi(roiObj);
		if(roi == null) {
			Log.logger.log(Level.SEVERE, "Roi conversion was failed. Cannot save rois...");
			return;
		}
		
		if (!dest.endsWith(".roi")) dest = dest+".roi";
		RoiEncoder re = new RoiEncoder(dest);
		try {
			re.write(roi);
		} catch (IOException e) {
			Log.logger.warning(e.getMessage());
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
			int res = JOptionPane.showConfirmDialog(this, "Do you wnat to paint roi to image ?");
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
				dup = firstImp.duplicate();//duplicate image pixels
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
	
	/**
	 * TODO 20251125
	 * When after filled Image, how to save new series ?
	 * Display Image is just DISPLAY. 
	 * If repainted, filled images will vanish... current code...
	 */
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
			if(isRGB) {
				ip.setColor(roi.getFillColor());
			}
			Roi fillerRoi = new RoiConverter().convert2Roi(roi);
			ip.fill(fillerRoi);// draw to image
			imp.setProcessor(ip);
			imp.updateAndDraw();
			imp.deleteRoi();
			slide.setImage(imp);
			slide.repaint();
		}
	}
	
	void splineFit() {
		if(selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, "Select roi first...");
			return;
		}
		
		//undo save
		SlideGlass targetSlide = selectedRois.values().iterator().next().getSlideGlass();
		if (targetSlide != null) targetSlide.saveUndoState();
		
		Set<String> keys = selectedRois.keySet();
		for(String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi==null) continue;
			SlideGlass s = roi.getSlideGlass();
			int type = roi.getType();
			if(!roi.isArea()) continue;
			
			// PolygonRoi の場合は双方向トグル（スイッチ）にする
			if(roi instanceof PolygonRoi) {
				PolygonRoi polyRoi = (PolygonRoi)roi;
				if (polyRoi.isSplineFit()) {
					// すでに滑らかなら、元のカクカクに戻す
					polyRoi.removeSplineFit();
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
				} else {
					// カクカクなら、滑らかにする
					polyRoi.fitSpline(type == RoiType.TRACED_ROI.id() ? 20 : 100);
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
				}
				if(s != null) {
					s.replaceRoi(roi.getUIDs(), polyRoi); // DB保存と描画更新
				}
				continue;
			}
			
			// 以下は元のロジック（RECTANGLEやCOMPOSITEをPolygonRoiに変換して滑らかにする）
			if(type == RoiType.RECTANGLE.id()) {
				FloatPolygon fpg = roi.getFloatPolygon();
				PolygonRoi polyRoi = new PolygonRoi(fpg, RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s, false);
				polyRoi.fitSpline(100);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
				if(s != null) s.replaceRoi(roi.getUIDs(), polyRoi);
				
			}else if(type == RoiType.COMPOSITE.id()) {
				ShapeRoi sRoi = (ShapeRoi)roi;
				Polygon poly = sRoi.getPolygon();
				int num = poly.npoints;
				int[] xps = poly.xpoints;
				int[] yps = poly.ypoints;
				
				if (num > 30) {
					int sparse_points = 10;
					int interval = (int) num / sparse_points;
					int newSize = (int) Math.ceil((double) num / interval);
					int[] sparseX = new int[newSize];
					int[] sparseY = new int[newSize];
					for (int i = 0, j = 0; i < num; i += interval, j++) {
						sparseX[j] = xps[i];
						sparseY[j] = yps[i];
					}
					PolygonRoi polyRoi = new PolygonRoi(new Polygon(sparseX, sparseY, newSize), RoiType.POLYGON.id(), null);
					polyRoi.setSlideGlass(s,false);
					polyRoi.fitSpline(100);
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
					if(s != null) s.replaceRoi(roi.getUIDs(), polyRoi);
					continue;
				}
				PolygonRoi polyRoi = new PolygonRoi(new Polygon(xps, yps, num), RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s,false);
				polyRoi.fitSpline(100);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
				if(s != null) s.replaceRoi(roi.getUIDs(), polyRoi);
			}
		}
		updateState();
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
			
			// ★ 修正: すでにPolygonRoiの場合でも、SplineFitされていればカクカクに解除する
			if(roi instanceof PolygonRoi) {
				PolygonRoi polyRoi = (PolygonRoi)roi;
				if (polyRoi.isSplineFit()) {
					polyRoi.removeSplineFit();
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
					s.replaceRoi(roi.getUIDs(), polyRoi);
				}
				continue;
			}
			
			if(!roi.isArea()) {
				continue;
			}
			
			if(type != RoiType.COMPOSITE.id()) {
				FloatPolygon fpg = roi.getFloatPolygon();
				PolygonRoi polyRoi = new PolygonRoi(fpg, RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s,false);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
				s.replaceRoi(roi.getUIDs(), polyRoi);
			}else{
				ShapeRoi sRoi = (ShapeRoi)roi;
				Polygon poly = sRoi.getPolygon();
				int num = poly.npoints;
				int[] xps = poly.xpoints;
				int[] yps = poly.ypoints;
				PolygonRoi polyRoi = new PolygonRoi(new Polygon(xps, yps, num), RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s,false);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
				s.replaceRoi(roi.getUIDs(), polyRoi);
			}
		}
		updateState();
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
		if (res != null && res.getSlideGlass() !=null) {
			res.getSlideGlass().addRoi(res);
//			DatabaseHandler db = DatabaseHandler.getInstance();
//			db.insertRoi(res.readContext());
//			res.getSlideGlass().loadRoiFromDB();
			updateState();
		}else {
			Log.logger.fine("RoiObjManager:combine() result does not have slideglass(i.e, image), cancel register to db");
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
			RoiObj roi_ = null;
			if (!roi.isArea() && (roi.getType()!=RoiType.POINT.id() && roi.getType()!=RoiType.MULTIPOINT.id())) {
				roi_ = RoiObj.convertLineToArea(roi);
			}else {
				roi_ = roi;
			}
			//first time loop
			if (s1==null) {
				//set new RoiId
				s1 = new com.vis.core.view.D2.roi.ShapeRoi(roi_);
			} else {//second or more loop
				s2 = new com.vis.core.view.D2.roi.ShapeRoi(roi_);
				s1.or(s2);
			}
		}
		//finally, s1 was combined all rois.
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
			JOptionPane.showConfirmDialog(this, "Select a composite roi first...");
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
				s1 = new ShapeRoi(roi);
			} else {
				if (nPointRois==1 && roi.getType()==Roi.POINT) {
					pointRoi = (PointRoi)roi;
					continue;  //PointRoi will be handled at the end
				}
				ShapeRoi s2 = new ShapeRoi(roi);
				s1.and(s2);
			}
		}
		if (s1==null) return;
		if (pointRoi!=null) {
			slide.addRoi(pointRoi.containedPoints(s1));
		}else {
			slide.addRoi(s1.trySimplify());
		}
		updateState();
	}

	/**
	 * 
	 */
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
			roi2.getSlideGlass().addRoi(roi2);
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
		toFront();
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
		}else if (command.equals(Functions.Measure.name())) {
			measure();
		}else if (command.equals(Functions.Delete.name())) {
			delete();
		}else if (command.equals(Functions.LineAndColor.name())) {
			SwingUtilities.invokeLater(()->lineAndColor());
		}else if (command.equals(Functions.Update.name())) {
			updateState();
		}else if (command.equals(Functions.Duplicate.name())) {
			duplicate();
		}else if (command.equals(Functions.GroupTo3D.name())) {
			groupTo3d();
		}else if (command.equals(Functions.Ungroup3D.name())) {
			ungroup3d();
		}else if (command.equals(Functions.Move.name())) { // ★追加: Moveアクション
			moveRois();
			
		//more functions
		}else if (command.equals(moreButtonLabel)) {
			JButton btn = (JButton)e.getSource();//more btn
			int patListW = patList.getWidth();
			int patListH = patList.getHeight();
			Point bloc = btn.getLocation();
			//location XY is RoiObjManager coordinates basis.
			pm.show(this, patListW, patListH+bloc.y+btn.getHeight()+3);
		}else if (command.equals(Functions.Open.name())) {
			openToGraphy(null);
		}else if (command.equals(Functions.Save.name())) {
			SwingUtilities.invokeLater(()->save());
		}else if (command.equals(Functions.SplineFit.name())) {
			splineFit();
		}else if(command.equals(Functions.ConvertToPolygon.name())) {
			convert2Polygon();
		}else if (command.equals(Functions.Fill.name())) {
			fill();
		}else if (command.equals(Functions.Draw.name())) {
			paintRoiOnImage();
		}else if (command.equals(Functions.Capture.name())) {
			capture();
		}else if (command.equals(Functions.OR_Combine.name())) {
			combine();
		} else if (command.equals(Functions.Split.name())) {
			split();
		}else if (command.equals(Functions.AND.name())) {
			and();
		}else if (command.equals(Functions.XOR.name())) {
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
	
	private Integer intValue(String intStr) {
		if(intStr == null) {
			return null;
		}
		try {
			int v = Integer.parseInt(intStr);
			return v;
		}catch(NumberFormatException e) {
			return null;
		}
	}
	
	private boolean isIgnoreValue(Integer v) {
		if(v == null) {
			return true;
		}else if(v == Integer.MIN_VALUE) {
			return true;
		}
		return false;
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
		if (e.getSource() instanceof JList && e.getValueIsAdjusting()) {

			roiInfoLabeling(); // バックアップ
			resetRoiInfoFields(); // 既存フィールドのクリア

			// ★追加: 多次元用フィールドのクリア
			if (multiDimFields != null) {
				for (JTextField tf : multiDimFields.values()) {
					tf.setText("");
				}
			}

			for (String roiID : rois.keySet()) {
				rois.get(roiID).setActiveOverlayRoi(false);
			}
			selectedRois = new HashMap<>();
			JList<String> roiList = (JList<String>) e.getSource();
			List<String> selected = roiList.getSelectedValuesList();
			if (selected != null && selected.size() > 0) {
				for (String id : selected) {
					rois.get(id).setActiveOverlayRoi(true);
					selectedRois.put(id, rois.get(id));
				}
			}
			int selected_size = selectedRois.size();
			if (selected_size == 1) {
				Set<String> key = selectedRois.keySet();
				String k = key.iterator().next();
				currentRoi = rois.get(k);

				Praparat pp = currentRoi.getSlideGlass().getPraparat();
				String posStr = currentRoi.getProperty(RoiDBKey.Position.name());

				if (posStr != null && !posStr.isEmpty() && !posStr.equals("0")) {
					try {
						int targetIndex = Integer.parseInt(posStr) - 1;
						pp.setImagePositionUsingSlider(targetIndex);
					} catch (NumberFormatException ex) {
						pp.setImagePositionTo(currentRoi.getSlideGlass());
					}
				} else {
					pp.setImagePositionTo(currentRoi.getSlideGlass());
				}
				toFront();

				// メインプロパティのロード
				for (RoiDBKey ck : roiInfo) {
					String v = currentRoi.getProperty(ck);
					if (ck == RoiDBKey.InstanceNo || ck == RoiDBKey.RoiGroup) {
						Integer v_ = intValue(v);
						if (!isIgnoreValue(v_)) {
							roiInfoFields.get(ck.name()).setText(v);
						}
					} else {
						roiInfoFields.get(ck.name()).setText(v);
					}
				}

				// ★追加: 多次元プロパティ（Dim_C, Dim_Z, Dim_T）をUIフィールドにロード
				if (currentRoi != null && multiDimFields != null) {
					multiDimFields.get("Dim_C").setText(currentRoi.getProperty("Dim_C"));
					multiDimFields.get("Dim_Z").setText(currentRoi.getProperty("Dim_Z"));
					multiDimFields.get("Dim_T").setText(currentRoi.getProperty("Dim_T"));
				}
			}
			Log.logger.fine("update Selected rois:" + selectedRois.size());
		}
	}

	/**
	 * run when drop down patient list changed
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getSource() instanceof JComboBox) {
			JComboBox<String> patCombo = (JComboBox<String>) e.getSource();
			String selectedPatID = patCombo.getItemAt(patCombo.getSelectedIndex());
			if (selectedPatID != null) {
				updateRoiObjList(patCombo.getItemAt(patCombo.getSelectedIndex()));
			}
		}
	}

	static class DateInputVerifier extends InputVerifier {
		private final SimpleDateFormat dateFormat;

		public DateInputVerifier(String dateFormatPattern) {
			this.dateFormat = new SimpleDateFormat(dateFormatPattern);
			this.dateFormat.setLenient(false);
		}

		@Override
		public boolean verify(JComponent input) {
			JTextField textField = (JTextField) input;
			String text = textField.getText();

			if (text.isEmpty() || text.length() == 0) {
				return true;
			}

			try {
				dateFormat.parse(text);
				return true;
			} catch (ParseException e) {
				JOptionPane.showMessageDialog(input, "Date is formatted by " + dateFormat.toPattern() + ".");
				return false;
			}
		}
	}
	
}
