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

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class RoiObjManager extends JFrame
		implements ActionListener, ItemListener, ListSelectionListener, Iterable<RoiObj> {

	boolean isDebug = Utils.isDebug;
	HashMap<String, JTextField> roiInfoFields;
	RoiObj currentRoi;// current only one selected roi
	HashMap<String, JTextField> multiDimFields;

	enum Functions {
		Measure, Delete, LineAndColor, Update, Duplicate, GroupTo3D, Ungroup3D, Move,
		// add more
		Open, Save, Fill, Capture, AND, OR_Combine, XOR, Split, SplineFit, ConvertToPolygon;
	}

	private static final int BUTTONS = 11;// num of functions
	private static String moreButtonLabel = "More " + '\u00bb';
	private JComboBox<String> patList;
	private DefaultComboBoxModel<String> patComboModel;
	private JPanel roiInfoPanel;
	private JPanel funcPanel;// list panel
	private static RoiObjManager instance;
	private JList<String> list;// roi obj list
	private DefaultListModel<String> listModel;// roi obj list model
	private HashMap<String, RoiObj> rois = new HashMap<>();// rois in listed
	private HashMap<String, RoiObj> selectedRois = new HashMap<>();// selected on list
	private JPopupMenu pm;
//	private JCheckBox labelsCheckbox = new JCheckBox("Labels", false);

	private boolean isUpdatingList = false;

	private static String errorMessage;

	/*
	 * Editable roi context info.
	 */
	final RoiDBKey[] roiInfo = new RoiDBKey[] { RoiDBKey.Name, RoiDBKey.Position, RoiDBKey.RoiGroup, RoiDBKey.RoiLabel, // lesion
																														// or
																														// lymph
																														// node
			RoiDBKey.ObjectType, // target or non target or findings
			RoiDBKey.Organ, //
			RoiDBKey.Description, RoiDBKey.StudyDate, RoiDBKey.CrossSection// axi,cor,sag
	};

	/*
	 * used for Viewer2DScreen
	 */
	private RoiObjManager() {
		super("Analysis Assistant");
		if (instance != null) {
			return;
		}
		instance = this;
		errorMessage = null;
		setUp();
		
		setAlwaysOnTop(true);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				/*
				 * to avoid lost info caused by forgetting "save info".
				 */
				roiInfoLabeling();
				if (currentRoi != null) {
					currentRoi.setActiveOverlayRoi(false);
				}
			}
		});

		WindowManager.addWindow(this);
		
		RoiObjListener rol = new RoiObjListener() {
			@Override
			public void roiModified(SlideGlass slide, int actionId) {
				SwingUtilities.invokeLater(() -> {
					// ROIが新規作成(CREATED/COMPLETED)された、または削除(DELETED)された場合
					if (actionId == RoiObjListener.CREATED || actionId == RoiObjListener.COMPLETED
							|| actionId == RoiObjListener.DELETED) {
						// リストと表示を自動的にリフレッシュ
						updateState();
					}

					// もし選択中のROIが移動(MOVED)や変形した場合は、プロパティパネルだけを最新化するなどの分岐も可能
					// else if (event == RoiObjListener.MOVED) { ... }
				});

			}
		};
		RoiObj.addRoiListener(rol);

	}

	private void setUp() {
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setIconImage(Resources.RoiObjManagerWinIcon.loadIconFromResource().getImage());
		setSize(650, 300);
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
		if (Platform.isLinux())
			list.setBackground(Color.white);
		JScrollPane scrollPane = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);

		// Roi Info Panel
		roiInfoPanel = new JPanel();
		addRoiInfoFields();
		JScrollPane jsp = new JScrollPane(roiInfoPanel);
		jsp.setPreferredSize(new Dimension(320, 0));
		add(jsp, BorderLayout.WEST);

		// buttons
		funcPanel = new JPanel();
		int nButtons = BUTTONS;
		funcPanel.setLayout(new GridLayout(nButtons, 1, 3, 3));
		addMainFeatures();
		addPopupMenu();
		add(funcPanel, BorderLayout.EAST);
	}

	public static RoiObjManager getInstance() {
		if (RoiObjManager.instance == null) {
			RoiObjManager.instance = new RoiObjManager();
		}
		return RoiObjManager.instance;
	}

	@Override
	public void setVisible(boolean show) {
		super.setVisible(show);
		if (show) {
			updateState();
		}
	}

	/*
	 * Measure Labeling Delete Line&Color
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
		if (isDebug) {
			addButton("Test");
		}
//		labelsCheckbox.addItemListener(this);
//		panel.add(labelsCheckbox);
	}

	void addPopupMenu() {
		pm = new JPopupMenu();
		// functional features
		addPopupItem(Functions.Save.name());
		addPopupItem(Functions.Open.name());
//		addPopupItem(RoiFunctions.Fill.name());//not tested
//		addPopupItem(RoiFunctions.Draw.name());//not tested
		addPopupItem(Functions.Capture.name());// not tested
		pm.addSeparator();

		// roi edit
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
		if (funcPanel != null) {
			funcPanel.add(b);
		}
	}

	void addPopupItem(String s) {
		JMenuItem mi = new JMenuItem(s);
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
						// ★ 3D-ROI（一式連動更新 ＆ 物理引越し対応）
						// ==========================================================
						List<RoiObj> groupRois = new ArrayList<>();
						for (SlideGlass s : pp.getAllSlides().values()) {
							if (s == null)
								continue;
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

						// ==========================================================
						// ★ SPHEREもFREEFORMも関係ない！形状を変えない純粋な一斉シフト
						// ==========================================================
						for (RoiObj roi : groupRois) {
							SlideGlass oldSg = roi.getSlideGlass();

							// 共通のメインプロパティとC/Tをコピー
							for (String k : mainProps.keySet()) {
								roi.setProperty(k, mainProps.get(k));
							}
							roi.setProperty("Dim_C", String.valueOf(newC));
							roi.setProperty("Dim_T", String.valueOf(newT));

							// Z軸のシフト
							if (roi == r) {
								// 操作対象のROIはそのまま入力値へ
								roi.setProperty("Dim_Z", String.valueOf(newZ));
							} else if (diffZ != 0) {
								// それ以外のグループROIは差分だけシフト
								String gZStr = roi.getProperty("Dim_Z");
								int gZ = (gZStr != null && !gZStr.isEmpty()) ? Integer.parseInt(gZStr) : 0;
								roi.setProperty("Dim_Z", String.valueOf(Math.max(0, gZ + diffZ)));
							}

							// Position逆算と手動引越し
							int z = Integer.parseInt(roi.getProperty("Dim_Z"));
							int newZct = pp.calcZctIndex(new int[] { z, newC, newT });
							roi.setProperty(RoiDBKey.Position.name(), String.valueOf(newZct + 1));

							SlideGlass newSg = pp.getAllSlides().get(newZct);
							if (newSg != null && oldSg != newSg) {
								if (oldSg != null)
									oldSg.getRois().remove(roi);
								roi.setSlideGlass(newSg, false);
								if (!newSg.getRois().contains(roi))
									newSg.getRois().add(roi);
							}

							// DB保存
							if (db != null)
								db.insertRoi(roi.readContext());
						}

					} else {
						// ==========================================================
						// ★ 2D-ROI（単独更新 ＆ 物理引越し対応）
						// ==========================================================
						SlideGlass oldSg = r.getSlideGlass();

						for (String k : mainProps.keySet()) {
							r.setProperty(k, mainProps.get(k));
						}
						r.setProperty("Dim_C", String.valueOf(newC));
						r.setProperty("Dim_Z", String.valueOf(newZ));
						r.setProperty("Dim_T", String.valueOf(newT));

						if (newC != -1 && newZ != -1 && newT != -1 && pp != null) {
							int newZctIndex = pp.calcZctIndex(new int[] { newZ, newC, newT });
							String calculatedPos = String.valueOf(newZctIndex + 1);
							r.setProperty(RoiDBKey.Position.name(), calculatedPos);
							roiInfoFields.get(RoiDBKey.Position.name()).setText(calculatedPos);

							// 2Dの引越し処理
							SlideGlass newSg = pp.getAllSlides().get(newZctIndex);
							if (newSg != null && oldSg != newSg) {
								if (oldSg != null)
									oldSg.getRois().remove(r);
								r.setSlideGlass(newSg, false);
								if (!newSg.getRois().contains(r))
									newSg.getRois().add(r);
							}
						} else {
							r.setProperty(RoiDBKey.Position.name(), "0");
							roiInfoFields.get(RoiDBKey.Position.name()).setText("0");
						}

						if (db != null)
							db.insertRoi(r.readContext());
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
		if (patComboModel == null || patList == null) {
			return;
		}
		String[] lists = Viewer2DScreen.getInstance().getPatientsListOnViewer();
		if (lists == null) {
			// reset
			patComboModel = new DefaultComboBoxModel<>();
			patList.setModel(patComboModel);
		} else {
			patComboModel.removeAllElements();
			for (int i = 0; i < lists.length; i++) {
				patComboModel.addElement(lists[i]);
			}
		}
		if (isVisible()) {
			patList.revalidate();
			patList.repaint();
		}
	}

	/**
	 * re-construct roi list, which viewing on stage and selected patient by combo.
	 */
	public void updateRoiObjList(String patID) {
		listModel.removeAllElements();
		if (rois != null) {
			rois.clear();
		} else {
			rois = new HashMap<>();// init
		}

		if (Viewer2DScreen.getInstance() != null) {
			StageView stage = Viewer2DScreen.getInstance().getStageViewAt(patID);
			if (stage != null) {
				List<Object[]> prapCons = stage.getAllPraparatContextInfoSet();
				for (Object[] uids : prapCons) {
					// get current praparat
					String studyUID = (String) uids[1];
					String seriesUID = (String) uids[2];
					String[] sopUIDSet = (String[]) uids[3];
					Praparat prap = stage.getEyepiece().getPraparatAt(patID, studyUID, seriesUID, sopUIDSet);
					ConcurrentHashMap<Integer, SlideGlass> slides = prap.getAllSlides();
					if(slides == null) {
						continue;
					}
					for (Integer readPos : slides.keySet()) {
						SlideGlass sg = slides.get(readPos);
						if(sg == null) {
							continue;
						}
						ArrayList<RoiObj> rois = sg.getRois();
						if (rois != null && rois.size() > 0) {
							for (RoiObj r : rois) {
								addRoiObj(r);// add to manager
							}
						}
					}
					// Praparat の 3D ROI リスト (SphereRoi3D など) も登録
					java.util.List<RoiObj> roi3DList = prap.getRoi3DList();
					if (roi3DList != null) {
						for (RoiObj roi3D : roi3DList) {
							if (roi3D != null)
								addRoiObj(roi3D);
						}
					}
				}
			}
		}

		if (isVisible()) {
			list.repaint();
		}
	}

	public void updateState() {
		if (isUpdatingList)
			return; // 二重呼び出し・無限ループを防止
		isUpdatingList = true;

		try {
			// ==========================================================
			// 1. リストがクリアされる前に、現在の選択状態をバックアップ
			// ==========================================================
			List<String> backupSelectedIds = new ArrayList<>();
			if (list != null && list.getSelectedValuesList() != null) {
				backupSelectedIds.addAll(list.getSelectedValuesList());
			}
			String backupCurrentRoiId = (currentRoi != null) ? currentRoi.getProperty(RoiDBKey.RoiID.name()) : null;

			updatePatientList();

			// 既存処理：情報を一旦クリア
			currentRoi = null;
			resetRoiInfoFields();

			if (patList == null || patList.getItemCount() == 0) {
				return;
			}
			String selectedPatID = patList.getItemAt(patList.getSelectedIndex());
			if (selectedPatID == null) {
				return;
			}

			// リストと rois マップの再構築（ここで以前のインスタンスは破棄され、選択が飛ぶ）
			updateRoiObjList(selectedPatID);

			// ==========================================================
			// 2. リスト再構築後、バックアップしておいた選択状態を復元する
			// ==========================================================
			selectedRois.clear(); // 選択マップをリセット
			if (!backupSelectedIds.isEmpty()) {
				List<Integer> indicesToSelect = new ArrayList<>();
				for (int i = 0; i < listModel.getSize(); i++) {
					String id = listModel.getElementAt(i);
					if (backupSelectedIds.contains(id)) {
						indicesToSelect.add(i);
						// 新しいインスタンスで selectedRois を再構築
						if (rois.containsKey(id)) {
							selectedRois.put(id, rois.get(id));
							rois.get(id).setActiveOverlayRoi(true);
						}
					}
				}
				if (!indicesToSelect.isEmpty()) {
					int[] indices = indicesToSelect.stream().mapToInt(i -> i).toArray();
					list.setSelectedIndices(indices); // UI上の選択ハイライトを復元
				}
			}

			// ==========================================================
			// 3. UIパネル（プロパティ）の表示を復元
			// ==========================================================
			if (backupCurrentRoiId != null && rois.containsKey(backupCurrentRoiId)) {
				currentRoi = rois.get(backupCurrentRoiId); // 新しいインスタンスにポインタを更新

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
				if (multiDimFields != null) {
					multiDimFields.get("Dim_C").setText(currentRoi.getProperty("Dim_C"));
					multiDimFields.get("Dim_Z").setText(currentRoi.getProperty("Dim_Z"));
					multiDimFields.get("Dim_T").setText(currentRoi.getProperty("Dim_T"));
				}
			}
		} finally {
			isUpdatingList = false; // フラグ解除
		}
	}

	private void resetRoiInfoFields() {
		for (RoiDBKey ck : roiInfo) {
			roiInfoFields.get(ck.name()).setText(null);
		}
		String[] dims = new String[] { RoiMetaContextKey.Dim_C.name(), RoiMetaContextKey.Dim_Z.name(),
				RoiMetaContextKey.Dim_T.name() };
		for (String d : dims) {
			multiDimFields.get(d).setText(null);
		}
	}

	public void addRoiObj(RoiObj roi) {
		String id = roi.getProperty(RoiDBKey.RoiID.name());
		if (id == null || id.trim().length() == 0) {
			return;
		}
		// alreasy exists, return.
		if (inList(id)) {
			return;
		}
		// rois always control with RoiID.
		rois.put(id, roi);
		// list model show ROI name as roi nickname.
		listModel.addElement(id);
		list.repaint();
	}

	private void test() {
		Log.logger.fine(currentRoi.x + "," + currentRoi.y);
	}

	public boolean inList(String roiID) {
		if (rois == null || rois.size() == 0) {
			return false;
		}
		return rois.containsKey(roiID);
	}

	private void measure() {
		if (selectedRois == null || selectedRois.size() < 1) {
			return;
		}

		// 1. ROIをグループIDごとに仕分けるマップ
		// キー: RoiGroup (グループ化されていないものは個別のユニークID等をキーにするか別リストへ)
		HashMap<Integer, List<RoiObj>> groupedRois = new HashMap<>();
		List<RoiObj> singleRois = new ArrayList<>();

		for (String k : selectedRois.keySet()) {
			RoiObj roiObj = selectedRois.get(k);
			String groupStr = roiObj.getProperty(RoiDBKey.RoiGroup.name());

			int groupId = -1;
			try {
				if (groupStr != null && !groupStr.isEmpty()) {
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
			for (HashMap<Measurements, Double> r : res) {
				ana.showInResultWindow(r);
			}
		}

		// 3. グループ化されたROI（3D-ROI）の体積計測
		for (Map.Entry<Integer, List<RoiObj>> entry : groupedRois.entrySet()) {
			int groupId = entry.getKey();
			List<RoiObj> groupList = entry.getValue();
			measureVolume(groupId, groupList);
		}
	}

	/*
	 * 参考メソッド簡易版 TODO：マスクからメッシュで計算する
	 */
	private void measureVolume(int groupId, List<RoiObj> groupList) {
		double totalVolume = 0.0;

		for (RoiObj roi : groupList) {
			
			if (roi instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
				// ==========================================================
				// 1. SphereRoi3D の体積計算 (幾何学公式)
				// ==========================================================
				com.vis.core.view.D3.roi.SphereRoi3D sphere = (com.vis.core.view.D3.roi.SphereRoi3D) roi;
				double r = sphere.getRadiusMm();
				// 球の体積 V = (4/3) * π * r^3
				double volume = (4.0 / 3.0) * Math.PI * Math.pow(r, 3);
				totalVolume += volume;
				
			} else if (roi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
				// ==========================================================
				// 2. FreeFormRoi3D の体積計算 (ボクセルカウント)
				// ==========================================================
				com.vis.core.view.D3.roi.FreeFormRoi3D ff = (com.vis.core.view.D3.roi.FreeFormRoi3D) roi;
				
				// 1ボクセルあたりの体積 (mm^3)
				double[] sp = ff.getSpacing();
				double voxelVolume = sp[0] * sp[1] * sp[2];
				
				int[] dims = ff.getDimensions();
				long voxelCount = 0;
				
				// 全Zスライスをスキャンして有効ボクセルをカウント
				for (int k = 0; k < dims[2]; k++) {
					ij.process.ByteProcessor bp = ff.getMaskAsBytes(k);
					if (bp != null) {
						byte[] pixels = (byte[]) bp.getPixels();
						for (byte b : pixels) {
							if (b != 0) {
								voxelCount++;
							}
						}
					}
				}
				totalVolume += (voxelCount * voxelVolume);
				
			} else {
				// ==========================================================
				// 3. 従来の2D-ROI群の体積計算 (面積 × スライス厚 の積算フォールバック)
				// ==========================================================
				SlideGlass sg = roi.getSlideGlass();
				if (sg == null) continue;

				ij.process.ImageStatistics stats = roi.getStatistics();
				if (stats == null) continue;

				double area = stats.area; // すでにCalibration済みの面積 (mm^2)

				ij.measure.Calibration cal = sg.getOriginalCalibration();
				double sliceThickness = 1.0;
				if (cal != null && cal.pixelDepth > 0) {
					sliceThickness = cal.pixelDepth;
				} else {
					com.vis.dicom.DicomObject header = sg.getHeader();
					if (header != null) {
						sliceThickness = header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices,
								header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
					}
				}
				totalVolume += (area * sliceThickness);
			}
		}

		// 結果の出力
		String msg = String.format("3D-ROI Group [%d] Volume: %.2f mm³", groupId, totalVolume);
		com.vis.core.log.Log.logger.info(msg);
		javax.swing.JOptionPane.showMessageDialog(this, msg, "Volume Measurement",
				javax.swing.JOptionPane.INFORMATION_MESSAGE);
	}

	private void delete() {
		if (selectedRois == null || selectedRois.size() < 1) {
			return;
		}
		List<RoiObj> rois = new ArrayList<>(selectedRois.values());
		for (RoiObj r : rois) {
			if (r != null) {
				SlideGlass roiSlide = r.getSlideGlass();
				Praparat roiPrap = roiSlide != null ? roiSlide.getPraparat() : null;
				boolean isIn3DList = roiPrap != null && roiPrap.getRoi3DList().contains(r);

				if (isIn3DList) {
					// 1. Praparatの3D管理リストから除外
					purgeRoiFromSystem(roiPrap, r);
					r.notifyListeners(RoiObjListener.DELETED);
					
					// 3D ROIが消えたので、全スライスを一斉再描画して他断面の残像を消す
					if (roiPrap != null) {
						for (SlideGlass sg : roiPrap.getAllSlides().values()) {
							if (sg != null) sg.repaintCanvasGlass();
						}
					}
				} else {
					// 従来の 2D ROI 削除処理
					SlideGlass slide = r.getSlideGlass();
					if (slide != null) {
						/*
						 * save undo notify to listener will done in canvas glass.
						 */
						slide.deleteRoi(r);
					} else {
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
		}
		updateState();
	}

	private void duplicate() {
		if (selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		boolean needGlobalRepaint = false;
		Praparat targetPp = null;

		for (String roiID : selectedRois.keySet()) {
			RoiObj originalRoi = selectedRois.get(roiID);
			if (originalRoi == null || originalRoi.getSlideGlass() == null) continue;

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

			// ==========================================================
			// ★ 修正: 3D-ROIと2D-ROIで登録先を分岐
			// ==========================================================
			if (newRoi instanceof com.vis.core.view.D3.roi.SphereRoi3D || 
				newRoi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
				
				targetPp = originalRoi.getSlideGlass().getPraparat();
				if (targetPp != null) {
					targetPp.addRoi3D(newRoi);
					DatabaseHandler db = DatabaseHandler.getInstance();
					if (db != null) db.insertRoi(newRoi.readContext());
					needGlobalRepaint = true;
				}
			} else {
				// 従来通りスライドに追加（ここでDBにも自動保存される）
				originalRoi.getSlideGlass().addRoi(newRoi);
			}
		}

		if (needGlobalRepaint && targetPp != null) {
			for (SlideGlass sg : targetPp.getAllSlides().values()) {
				if (sg != null) sg.repaintCanvasGlass();
			}
		}
		updateState();
	}

	private void groupTo3d() {
		// リストで選択されている複数のROIを取得
		int[] selectedIndices = list.getSelectedIndices();
		if (selectedIndices.length < 1) {
			PopUpMessage.showDialog(list, "Select ROI(s)", "Please select 2D-ROIs to bundle into 3D.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		
		if (contains3DRoi(selectedRois)) {
			PopUpMessage.showDialog(list, "Select ROI(s)", "Please select only 2D-ROIs, you are selecting 3D-ROI in list.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		List<RoiObj> targetRois = new ArrayList<>();
		String targetSeriesUID = null;
		Praparat targetPraparat = null;

		// 1. 同一SeriesInstanceUIDの検証と対象ROIのリストアップ
		for (int index : selectedIndices) {
			String rid = list.getModel().getElementAt(index);
			RoiObj r = selectedRois.get(rid);
			if (r != null) {
				// SeriesInstanceUID を取得
				String seriesUID = r.getProperty(RoiDBKey.SeriesInstanceUID.name());
				if (seriesUID == null) {
					HashMap<RoiDBKey, String> uids = r.getUIDs();
					if (uids != null) seriesUID = uids.get(RoiDBKey.SeriesInstanceUID);
				}

				// 初回ループで基準となるUIDとPraparatを保持
				if (targetSeriesUID == null) {
					targetSeriesUID = seriesUID;
					if (r.getSlideGlass() != null) {
						targetPraparat = r.getSlideGlass().getPraparat();
					}
				} 
				// 2回目以降でUIDが基準と一致するかチェック
				else if (seriesUID != null && !targetSeriesUID.equals(seriesUID)) {
					PopUpMessage.showDialog(list, "Series Mismatch", 
							"All selected ROIs must belong to the same Series (SeriesInstanceUID).\nOperation aborted.", 
							JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
					return;
				}

				targetRois.add(r);
			}
		}

		if (targetPraparat == null) {
			PopUpMessage.showDialog(list, "Error", "Cannot find the target Praparat for the selected ROIs.", JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
			return;
		}

		// 新しい共通のグループIDを生成
		int uniqueGroupId = (int) (System.currentTimeMillis() % 1000000000L);
		String newGroupId = String.valueOf(uniqueGroupId);
		
		try {
			// 2. ファクトリメソッドで 真の3D ROI を生成
			com.vis.core.view.D3.roi.FreeFormRoi3D roi3d = 
					com.vis.core.view.D3.roi.FreeFormRoi3D.createFrom2DRois(targetPraparat, targetRois, newGroupId);
			
			if (roi3d != null) {
				// ==========================================================
				// ★ 修正箇所：3D-ROIの中央スライスを算出し、代表Positionとしてセットする
				// ==========================================================
				int minZ = Integer.MAX_VALUE;
				int maxZ = Integer.MIN_VALUE;
				int targetC = 0, targetT = 0;
				boolean firstPropsSet = false;

				for (RoiObj r : targetRois) {
					String zStr = r.getProperty("Dim_Z");
					int z = (zStr != null && !zStr.isEmpty()) ? Integer.parseInt(zStr) : -1;
					if (z != -1) {
						minZ = Math.min(minZ, z);
						maxZ = Math.max(maxZ, z);
					}
					
					// 最初のROIから C, T の値を拾う
					if (!firstPropsSet) {
						String cStr = r.getProperty("Dim_C");
						String tStr = r.getProperty("Dim_T");
						targetC = (cStr != null && !cStr.isEmpty()) ? Integer.parseInt(cStr) : 0;
						targetT = (tStr != null && !tStr.isEmpty()) ? Integer.parseInt(tStr) : 0;
						firstPropsSet = true;
					}
				}

				if (minZ <= maxZ) {
					int centerZ = minZ + (maxZ - minZ) / 2; // 中央Zの算出
					int centerZct = targetPraparat.calcZctIndex(new int[]{centerZ, targetC, targetT});
					
					roi3d.setProperty("Dim_Z", String.valueOf(centerZ));
					roi3d.setProperty("Dim_C", String.valueOf(targetC));
					roi3d.setProperty("Dim_T", String.valueOf(targetT));
					roi3d.setProperty(RoiDBKey.Position.name(), String.valueOf(centerZct + 1));

					SlideGlass centerSg = targetPraparat.getAllSlides().get(centerZct);
					if (centerSg != null) {
						roi3d.setSlideGlass(centerSg, false);
					}
				}
				// ==========================================================

				// 3. 構築した3D ROIを対象のPraparatへ一元管理用に追加
				targetPraparat.addRoi3D(roi3d);

				// 4. 吸収された元の2D ROIをキャンバスおよびDBから完全消去
				for (RoiObj r : targetRois) {
					SlideGlass sg = r.getSlideGlass();
					if (sg != null) {
						sg.deleteRoi(r); // SlideGlassのdeleteRoiは内部でDBからも削除します
					}
				}

				// 5. 新しい 3D ROI をDBに保存
				DatabaseHandler db = DatabaseHandler.getInstance();
				if (db != null) {
					db.insertRoi(roi3d.readContext());
				}

				// 6. 画面の一斉再描画とリストの同期
				for (SlideGlass sg : targetPraparat.getAllSlides().values()) {
					if (sg != null) sg.repaintCanvasGlass();
				}
				updateState(); // UIリストのリフレッシュ
				
				PopUpMessage.showDialog(list, "Success", "Selected ROIs have been successfully bundled into a True 3D ROI.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (IllegalArgumentException e) {
			PopUpMessage.showDialog(list, "Validation Error", e.getMessage(), JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
		}
	}

	private void ungroup3d() {
		int[] selectedIndices = list.getSelectedIndices();
		if (selectedIndices.length == 0) {
			PopUpMessage.showDialog(list, "Select ROIs", "Please select 3D ROIs to ungroup.", JOptionPane.OK_OPTION,
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		boolean updated = false;
		Praparat pp = null;

		// ==========================================================
		// ★ 爆弾処理：JListのインデックスから安全なリストへ全対象を先にコピー
		// （ループ中に削除されると list.getModel() が狂って ArrayIndexOutOfBounds になるのを防ぐ）
		// ==========================================================
		List<RoiObj> safeTargets = new ArrayList<>();
		for (int index : selectedIndices) {
			if (index < list.getModel().getSize()) {
				String rid = list.getModel().getElementAt(index);
				RoiObj r = selectedRois.get(rid);
				if (r != null) safeTargets.add(r);
			}
		}

		for (RoiObj r : safeTargets) {
			pp = (r.getSlideGlass() != null) ? r.getSlideGlass().getPraparat() : null;
			if (pp == null)
				continue;

			// 新アーキテクチャの真の3Dオブジェクトかどうかを判定
			boolean isSphere = r instanceof com.vis.core.view.D3.roi.SphereRoi3D;
			boolean isFreeForm = r instanceof com.vis.core.view.D3.roi.FreeFormRoi3D;

			if (!isSphere && !isFreeForm) {
				continue; // 既に2D ROIの場合はスキップ
			}

			String originalName = r.getName() != null ? r.getName() : "Ungrouped";
			java.util.List<RoiObj> generated2DRois = new ArrayList<>();

			if (isSphere) {
				com.vis.core.view.D3.roi.SphereRoi3D sphere = (com.vis.core.view.D3.roi.SphereRoi3D) r;
				double R = sphere.getRadiusMm();
				double cx = sphere.getCenterX();
				double cy = sphere.getCenterY();
				double cz = sphere.getCenterZ();

				for (SlideGlass sg : pp.getAllSlides().values()) {
					if (sg == null)
						continue;
					com.vis.dicom.DicomObject header = sg.getHeader();
					int frameIdx = pp.isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;
					double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
					double[] sliceIop = pp.getSafeIOP(header, frameIdx);
					if (sliceIpp == null || sliceIop == null)
						continue;

					double nx = sliceIop[1] * sliceIop[5] - sliceIop[2] * sliceIop[4];
					double ny = sliceIop[2] * sliceIop[3] - sliceIop[0] * sliceIop[5];
					double nz = sliceIop[0] * sliceIop[4] - sliceIop[1] * sliceIop[3];
					double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
					nx /= len;
					ny /= len;
					nz /= len;

					double vx = cx - sliceIpp[0];
					double vy = cy - sliceIpp[1];
					double vz = cz - sliceIpp[2];
					double d = Math.abs(nx * vx + ny * vy + nz * vz);

					if (d < R) {
						double r_mm = Math.sqrt(R * R - d * d);
						double projX = vx * sliceIop[0] + vy * sliceIop[1] + vz * sliceIop[2];
						double projY = vx * sliceIop[3] + vy * sliceIop[4] + vz * sliceIop[5];
						double spX = sg.getPixelSpacingX() <= 0 ? 1.0 : sg.getPixelSpacingX();
						double spY = sg.getPixelSpacingY() <= 0 ? 1.0 : sg.getPixelSpacingY();

						double rxPx = r_mm / spX;
						double ryPx = r_mm / spY;
						double cxPx = projX / spX;
						double cyPx = projY / spY;

						int sx = (int) Math.round(cxPx - rxPx);
						int sy = (int) Math.round(cyPx - ryPx);
						int sw = (int) Math.round(rxPx * 2);
						int sh = (int) Math.round(ryPx * 2);

						com.vis.core.view.D2.roi.OvalRoi oval = new com.vis.core.view.D2.roi.OvalRoi(sx, sy, sw, sh,
								sg);
						generated2DRois.add(oval);
					}
				}
			} else if (isFreeForm) {
				com.vis.core.view.D3.roi.FreeFormRoi3D freeForm = (com.vis.core.view.D3.roi.FreeFormRoi3D) r;
				for (Map.Entry<Integer, SlideGlass> entry : pp.getAllSlides().entrySet()) {
					SlideGlass sg = entry.getValue();
					if (sg == null)
						continue;

					com.vis.dicom.DicomObject header = sg.getHeader();
					int frameIdx = pp.isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;
					double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
					if (sliceIpp == null)
						continue;

					int k = freeForm.getZIndexForSlice(sliceIpp);
					if (k < 0)
						continue;

					ij.process.ByteProcessor bp = freeForm.getMaskAsBytes(k);
					if (bp != null) {
						bp.setThreshold(255, 255, ij.process.ImageProcessor.NO_LUT_UPDATE);
						ij.ImagePlus tempImp = new ij.ImagePlus("", bp);
						ij.plugin.filter.ThresholdToSelection tts = new ij.plugin.filter.ThresholdToSelection();
						tts.setup("", tempImp);
						tts.run(bp);
						ij.gui.Roi ijRoi = tempImp.getRoi();

						if (ijRoi != null) {
							RoiObj shapeRoi = new RoiConverter().convert2RoiObj(ijRoi);
							if (shapeRoi != null) {
								shapeRoi.setSlideGlass(sg, false);
								generated2DRois.add(shapeRoi);
							}
						}
					}
				}
			}

			purgeRoiFromSystem(pp, r);

			for (RoiObj new2d : generated2DRois) {
				new2d.setName(originalName + " (Ungrouped)");
				new2d.setProperty(RoiMetaContextKey.Shape_3D_Type.name(), null);
				new2d.setProperty(RoiDBKey.RoiGroup.name(), null);

				new2d.setStrokeColor(r.getStrokeColor());
				new2d.setStrokeWidth(r.getStrokeWidth());

				int zctIndex = pp.getZCTIndex(new2d.getSlideGlass());
				new2d.setProperty(RoiDBKey.Position.name(), String.valueOf(zctIndex + 1));

				int[] zct = pp.calcZCTArrayFromIndex(zctIndex);				
				new2d.setProperty("Dim_Z", String.valueOf(zct[0]));
				new2d.setProperty("Dim_C", String.valueOf(zct[1]));
				new2d.setProperty("Dim_T", String.valueOf(zct[2]));
				
				new2d.getSlideGlass().addRoi(new2d);
			}
			updated = true;
		}

		if (updated) {
			updateState(); 
			PopUpMessage.showDialog(list, "Success",
					"Selected 3D ROIs have been disassembled into independent 2D ROIs for each slice.",
					JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);

			if (pp != null) {
				for (SlideGlass sg : pp.getAllSlides().values()) {
					if (sg != null) {
						sg.repaintCanvasGlass();
					}
				}
			}
		}
	}

	// ==========================================================
	// 新アーキテクチャ対応版: ROIのXYZ微調整機能 (リアルタイムプレビュー対応)
	// ==========================================================
	private void moveRois() {
		if (selectedRois == null || selectedRois.size() < 1) {
			JOptionPane.showMessageDialog(this, Resources.i18n("RoiObjManager.error.selectRoisFirst"), Resources.i18n("dialog.title.graphy"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// 1. 移動対象のROIを「2D」と「真の3D」に分けてリストアップ
		List<RoiObj> targets2D = new ArrayList<>();
		List<RoiObj> targets3D = new ArrayList<>();
		Praparat pp = null;

		for (RoiObj r : selectedRois.values()) {
			if (r == null)
				continue;
			if (pp == null && r.getSlideGlass() != null)
				pp = r.getSlideGlass().getPraparat();

			// 新アーキテクチャの真の3Dオブジェクトかどうかを判定
			if (r instanceof com.vis.core.view.D3.roi.SphereRoi3D
					|| r instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
				if (!targets3D.contains(r))
					targets3D.add(r);
			} else {
				if (!targets2D.contains(r))
					targets2D.add(r);
			}
		}

		if (pp == null || (targets2D.isEmpty() && targets3D.isEmpty()))
			return;
		final Praparat finalPp = pp;

		// 2. ダイアログの作成
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Move ROI(s)",
				java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setLayout(new BorderLayout());
		JPanel inputPanel = new JPanel(new GridLayout(3, 2, 5, 5));
		inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

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

		JPanel btnPanel = new JPanel();
		JButton btnOk = new JButton("OK / Apply");
		JButton btnCancel = new JButton("Cancel / Revert");
		btnPanel.add(btnOk);
		btnPanel.add(btnCancel);
		dialog.add(btnPanel, BorderLayout.SOUTH);

		// 状態管理
		final int[] lastShifts = { 0, 0, 0 };
		final boolean[] isConfirmed = { false };

		// 3. スピナー操作時のリアルタイムプレビュー（メモリ上でのシフト）
		Timer debounceTimer = new Timer(50, e -> {
			int curX = (int) xSpinner.getValue();
			int curY = (int) ySpinner.getValue();
			int curZ = (int) zSpinner.getValue();

			int diffX = curX - lastShifts[0];
			int diffY = curY - lastShifts[1];
			int diffZ = curZ - lastShifts[2];

			if (diffX == 0 && diffY == 0 && diffZ == 0)
				return;

			// 安全装置：Zのシフトで画像枚数の範囲外に飛び出さないかチェック
			boolean outOfBounds = false;
			if (diffZ != 0) {
				// 2D ROI の境界チェック
				for (RoiObj r : targets2D) {
					String zStr = r.getProperty("Dim_Z");
					int z = (zStr != null && !zStr.isEmpty()) ? Integer.parseInt(zStr) : 0;
					int c = Integer.parseInt(r.getProperty("Dim_C") != null ? r.getProperty("Dim_C") : "-1");
					int t = Integer.parseInt(r.getProperty("Dim_T") != null ? r.getProperty("Dim_T") : "-1");
					if (!finalPp.getAllSlides().containsKey(finalPp.calcZctIndex(new int[] { z + diffZ, c, t }))) {
						outOfBounds = true;
						break;
					}
				}
				// 3D ROI の境界チェック (ボリューム全体の限界)
				for (RoiObj r : targets3D) {
					String zStr = r.getProperty("Dim_Z");
					int z = (zStr != null && !zStr.isEmpty()) ? Integer.parseInt(zStr) : 0;
					int newZ = z + diffZ;
					if (newZ < 0 || newZ >= finalPp.getAllSlides().size()) {
						outOfBounds = true;
						break;
					}
				}
			}

			if (outOfBounds) {
				zSpinner.setValue(lastShifts[2]); // 無理だった場所から元に戻す
				diffZ = 0;
			}

			if (diffX == 0 && diffY == 0 && diffZ == 0)
				return;

			// メモリ上のシフトを適用
			applyMemoryShift(targets2D, targets3D, finalPp, diffX, diffY, diffZ);

			lastShifts[0] += diffX;
			lastShifts[1] += diffY;
			lastShifts[2] += diffZ;
		});
		debounceTimer.setRepeats(false);

		javax.swing.event.ChangeListener cl = e -> debounceTimer.restart();
		xSpinner.addChangeListener(cl);
		ySpinner.addChangeListener(cl);
		zSpinner.addChangeListener(cl);

		// 4. OK / Cancel ボタンの挙動
		btnOk.addActionListener(e -> {
			isConfirmed[0] = true;
			dialog.dispose();
		});
		btnCancel.addActionListener(e -> dialog.dispose());

		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				debounceTimer.stop();
				if (isConfirmed[0]) {
					// ★ OKが押された時のみDBにコミット
					DatabaseHandler db = DatabaseHandler.getInstance();
					if (db != null) {
						for (RoiObj r : targets2D)
							db.insertRoi(r.readContext());
						for (RoiObj r : targets3D)
							db.insertRoi(r.readContext());
					}
				} else {
					// ★ キャンセル時は逆向きにシフトしてロールバック
					applyMemoryShift(targets2D, targets3D, finalPp, -lastShifts[0], -lastShifts[1], -lastShifts[2]);
				}
				updateState(); // リストの更新
			}
		});

		dialog.pack();
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true); // ダイアログが閉じるまでブロック
	}

	/**
	 * ROIの物理シフト（2Dはキャンバス引越し、3DはIPP座標移動）を行うメソッド
	 */
	private void applyMemoryShift(List<RoiObj> targets2D, List<RoiObj> targets3D, Praparat pp, int diffX, int diffY,
			int diffZ) {
		int scrollTargetZct = -1;

		// ==========================================================
		// 1. 純粋な 2D ROI のシフト（引越し処理）
		// ==========================================================
		for (RoiObj r : targets2D) {
			if (diffX != 0 || diffY != 0) {
				r.setLocation(r.getXBase() + diffX, r.getYBase() + diffY);
			}
			if (diffZ != 0) {
				String dimCStr = r.getProperty("Dim_C");
				String dimZStr = r.getProperty("Dim_Z");
				String dimTStr = r.getProperty("Dim_T");
				int currentC = (dimCStr != null && !dimCStr.isEmpty()) ? Integer.parseInt(dimCStr) : -1;
				int currentZ = (dimZStr != null && !dimZStr.isEmpty()) ? Integer.parseInt(dimZStr) : -1;
				int currentT = (dimTStr != null && !dimTStr.isEmpty()) ? Integer.parseInt(dimTStr) : -1;

				if (currentZ != -1) {
					int newZ = currentZ + diffZ;
					r.setProperty("Dim_Z", String.valueOf(newZ));

					int newZctIndex = pp.calcZctIndex(new int[] { newZ, currentC, currentT });
					r.setProperty(RoiDBKey.Position.name(), String.valueOf(newZctIndex + 1));

					SlideGlass oldSg = r.getSlideGlass();
					SlideGlass newSg = pp.getAllSlides().get(newZctIndex);

					if (newSg != null && oldSg != newSg) {
						if (oldSg != null)
							oldSg.getRois().remove(r);
						r.setSlideGlass(newSg, false);
						if (!newSg.getRois().contains(r))
							newSg.getRois().add(r);
					}
					if (scrollTargetZct == -1)
						scrollTargetZct = newZctIndex;
				}
			}
		}

		// ==========================================================
		// 2. 真の 3D ROI のシフト（ボクセル空間での物理座標移動）
		// ==========================================================
		if (!targets3D.isEmpty() && !pp.getAllSlides().isEmpty()) {
			SlideGlass refSg = pp.getAllSlides().get(0);
			com.vis.dicom.DicomObject header = refSg.getHeader();
			int frameIdx = pp.isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;

			// スペーシング（ボクセルサイズ）の取得
			double spX = refSg.getPixelSpacingX() <= 0 ? 1.0 : refSg.getPixelSpacingX();
			double spY = refSg.getPixelSpacingY() <= 0 ? 1.0 : refSg.getPixelSpacingY();
			double spZ = header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices,
					header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
			if (spZ <= 0)
				spZ = 1.0;

			// 方向ベクトル（IOP）から法線ベクトル（Z軸）を算出
			double[] iop = pp.getSafeIOP(header, frameIdx);
			if (iop == null)
				iop = new double[] { 1, 0, 0, 0, 1, 0 };

			double nx = iop[1] * iop[5] - iop[2] * iop[4];
			double ny = iop[2] * iop[3] - iop[0] * iop[5];
			double nz = iop[0] * iop[4] - iop[1] * iop[3];
			double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
			nx /= len;
			ny /= len;
			nz /= len;

			// ピクセル量とスライス枚数を物理距離(mm)に変換
			double dX_mm = diffX * spX;
			double dY_mm = diffY * spY;
			double dZ_mm = diffZ * spZ;

			// 3D空間(IPP基準)における実際の移動ベクトルを算出
			double shiftX = iop[0] * dX_mm + iop[3] * dY_mm + nx * dZ_mm;
			double shiftY = iop[1] * dX_mm + iop[4] * dY_mm + ny * dZ_mm;
			double shiftZ = iop[2] * dX_mm + iop[5] * dY_mm + nz * dZ_mm;

			for (RoiObj r3d : targets3D) {
				// SphereRoi3D の原点移動
				if (r3d instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
					com.vis.core.view.D3.roi.SphereRoi3D sphere = (com.vis.core.view.D3.roi.SphereRoi3D) r3d;
					sphere.setCenterIpp(sphere.getCenterX() + shiftX, sphere.getCenterY() + shiftY,
							sphere.getCenterZ() + shiftZ);
				}
				// FreeFormRoi3D の原点移動
				else if (r3d instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
					com.vis.core.view.D3.roi.FreeFormRoi3D ff = (com.vis.core.view.D3.roi.FreeFormRoi3D) r3d;
					double[] orig = ff.getOriginIpp();
					ff.setProperty("FreeForm3D_Origin",
							(orig[0] + shiftX) + "," + (orig[1] + shiftY) + "," + (orig[2] + shiftZ));
					ff.initFromProperties(); // メモリに即時反映
				}

				// 概念的なZインデックスプロパティも同期しておく
				if (diffZ != 0) {
					String zStr = r3d.getProperty("Dim_Z");
					int oldZ = (zStr != null && !zStr.isEmpty()) ? Integer.parseInt(zStr) : 0;
					r3d.setProperty("Dim_Z", String.valueOf(Math.max(0, oldZ + diffZ)));
				}
			}
		}

		// ==========================================================
		// 3. 画面の一斉更新
		// ==========================================================
		if (diffZ != 0 && scrollTargetZct != -1) {
			pp.setImagePositionUsingSlider(scrollTargetZct);
		}
		for (SlideGlass sg : pp.getAllSlides().values()) {
			if (sg != null)
				sg.repaintCanvasGlass();
		}
	}

	/**
	 * 共通のGroupIDを持つ全ROIを取得するヘルパー
	 */
	public List<RoiObj> findAllRoisInGroup(Praparat pp, String groupId) {
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
	 * db.insertRoi(roi.readContext());で新規作成または、存在していれば上書きを促す。
	 * SlideGlass>CanvasGlassからの処理は、CanavasGlassのZCTを強制上書きするので注意。
	 */
	private void saveRoi2DB(RoiObj roi) {
		// save or update
//		SlideGlass slide = roi.getSlideGlass();
//		if(slide != null) {
//			slide.addRoi(roi);//update if already exist
//		}else {
		// save new or update
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) {
			db.insertRoi(roi.readContext());
		}
//		}
	}

	private void roiInfoLabeling() {
		// only 1 roi ...
		if (selectedRois == null || selectedRois.size() == 0 || selectedRois.size() > 1) {
			return;
		}
		if (currentRoi != null) {
			// add properties
			for (RoiDBKey ck : roiInfo) {
				currentRoi.setProperty(ck.name(), roiInfoFields.get(ck.name()).getText());
			}
			// save or update
			saveRoi2DB(currentRoi);
		}
	}

	private void lineAndColor() {
		// only 1 roi selected...
		if (selectedRois == null || selectedRois.size() == 0 || selectedRois.size() > 1) {
			return;
		}
		// get roi
		RoiObj roi = null;
		String key = selectedRois.keySet().iterator().next();
		roi = selectedRois.get(key);
		if (roi == null) {
			return;
		}
		/*
		 * here, only change current roi state.
		 */
		String strokeColorString = GraphyProp.findColorNameByColor(roi.getStrokeColor());
		String fillColorString = GraphyProp.findColorNameByColor(roi.getFillColor());

		OptionDialog gd = new OptionDialog("Line & Color", this);
		gd.addNumericField("Stroke Width", roi.getStrokeWidth(), 2, 7 /* cols */, "pixel"/* unit */);
		gd.addChoice("Stroke Color",
				new String[] { "white", "blue", "orange", "yellow", "red", "pink", "magenta", "green", "black" },
				strokeColorString);
		gd.addChoice("Fill Color",
				new String[] { "white", "blue", "orange", "yellow", "red", "pink", "magenta", "green", "black" },
				fillColorString);
		gd.pack();
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int w = (int) gd.getNextNumber();
		String sc = gd.getNextChoice();
		String fc = gd.getNextChoice();
		if (w < 1) {
			w = 1;
		} else if (w > 50) {
			w = 50;
		}
		roi.setStrokeWidth((double) w);
		roi.setStrokeColor(roi.colorFromString(sc, Color.YELLOW));
		roi.setFillColor(roi.colorFromString(fc, Color.WHITE));
		
		if (roi instanceof com.vis.core.view.D3.roi.SphereRoi3D
				|| roi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
			Praparat pp = roi.getSlideGlass().getPraparat();
			if (pp != null) {
				for (SlideGlass sg : pp.getAllSlides().values()) {
					if (sg != null)
						sg.repaintCanvasGlass();
				}
			}
		} else {
			if (roi.getSlideGlass() != null)
				roi.getSlideGlass().repaintCanvasGlass();
		}

		// DBへの保存もトリガーしておくのが親切です
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null)
			db.insertRoi(roi.readContext());
	}

	/**
	 * If a roi is consistent in any slices, it is considered consistent.
	 * 
	 * @param pp
	 * @param roi
	 * @return
	 */
	boolean hasConsistency(Praparat pp, RoiObj roi) {
		String pid = roi.getProperty(RoiDBKey.PatientID.name());
		String studyUID = roi.getProperty(RoiDBKey.StudyInstanceUID.name());
		String seriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());
		String sopUID = roi.getProperty(RoiDBKey.SOPInstanceUID.name());
		if (pid == null || studyUID == null || seriesUID == null || sopUID == null) {
			return false;
		}
		ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
		for (Integer pos : slides.keySet()) {
			boolean consistent = true;
			SlideGlass sg = slides.get(pos);
			String[] UIDs = sg.getUIDs();
			if (!UIDs[0].equals(pid))
				consistent = false;
			if (!UIDs[1].equals(studyUID))
				consistent = false;
			if (!UIDs[2].equals(seriesUID))
				consistent = false;
			if (!UIDs[3].equals(sopUID))
				consistent = false;
			if (consistent) {
				return true;
			}
		}
		return false;
	}

	/**
	 * If a roi is consistent in any slices, it is considered consistent.
	 * 
	 * @param pp
	 * @param roi
	 * @return
	 */
	boolean hasConsistency(Praparat pp, ij.gui.Roi roi) {
		String pid = roi.getProperty(RoiDBKey.PatientID.name());
		String studyUID = roi.getProperty(RoiDBKey.StudyInstanceUID.name());
		String seriesUID = roi.getProperty(RoiDBKey.SeriesInstanceUID.name());
		String sopUID = roi.getProperty(RoiDBKey.SOPInstanceUID.name());
		if (pid == null || studyUID == null || seriesUID == null || sopUID == null) {
			return false;
		}
		ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
		for (Integer pos : slides.keySet()) {
			boolean consistent = true;
			SlideGlass sg = slides.get(pos);
			String[] UIDs = sg.getUIDs();
			if (!UIDs[0].equals(pid))
				consistent = false;
			if (!UIDs[1].equals(studyUID))
				consistent = false;
			if (!UIDs[2].equals(seriesUID))
				consistent = false;
			if (!UIDs[3].equals(sopUID))
				consistent = false;
			if (consistent) {
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
		if (pid == null || studyUID == null || seriesUID == null || sopUID == null) {
			return -1;
		}
		ConcurrentHashMap<Integer, SlideGlass> slides = pp.getAllSlides();
		for (Integer pos : slides.keySet()) {
			boolean consistent = true;
			SlideGlass sg = slides.get(pos);
			String[] UIDs = sg.getUIDs();
			if (!UIDs[0].equals(pid))
				consistent = false;
			if (!UIDs[1].equals(studyUID))
				consistent = false;
			if (!UIDs[2].equals(seriesUID))
				consistent = false;
			if (!UIDs[3].equals(sopUID))
				consistent = false;
			if (consistent) {
				return pos;
			}
		}
		return -1;
	}

	void openToGraphy(String path) {
		if (patList.getItemCount() == 0) {
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
		if (praps.size() == 0) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectSeriesForRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		String name = null;
		if (path == null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Roi (.roi/.zip)...", "");
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name == null) {
				return;
			}
			path = directory + name;
		}
		if (path.endsWith(".zip")) {
			openZip(path, selectedPatID, praps);
			return;
		}
		Opener o = new Opener();
		if (name == null)
			name = o.getName(path);
		ij.gui.Roi roi = o.openRoi(path);
		if (roi != null) {
			loadRoi2Slide(roi, selectedPatID, praps);
		} else {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.loadRoiFailed") + " " + path, Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
		}
	}

	public static List<Roi> open(String path) {
		if (path == null || path.length() == 0) {
			return null;
		}
		List<Roi> rois = new ArrayList<>();
		if (path.endsWith(".zip")) {
			List<Roi> rois_ = openZip(path);
			if (rois != null) {
				rois.addAll(rois_);
			}
		} else {
			Opener o = new Opener();
			ij.gui.Roi roi = o.openRoi(path);
			if (roi != null) {
				rois.add(roi);
			}
		}
		return rois;
	}

	public static List<Roi> openZip(String path) {
		if (path == null || path.endsWith("zip")) {
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
			while (entry != null) {
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
					if (roi != null) {
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
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
				}
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
				}
		}
		if (nRois == 0 && errorMessage == null) {
			errorMessage = "This ZIP archive does not contain \".roi\" files: " + path;
		}
		return rois;
	}

	// Modified on 2005/11/15 by Ulrik Stervbo to only read .roi files and to not
	// empty the current list
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
			while (entry != null) {
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
					if (roi != null) {
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
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
				}
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
				}
		}
		if (nRois == 0 && errorMessage == null) {
			errorMessage = "This ZIP archive does not contain \".roi\" files: " + path;
		}
	}

	void loadRoi2Slide(Roi roi, String selectedPatID, ArrayList<Praparat> praps) {
		if (roi == null || praps == null || praps.size() == 0) {
			return;
		}
		// convert to roiobj
		RoiObj roiObj = new RoiConverter().convert2RoiObj(roi);
		if (roiObj == null) {
			Log.logger.fine("Cannot import roi...");
			return;
		}
		/*
		 * Load on all selected series. If consistent, load only that slide. If there is
		 * no consistency, priority is given to the instance number. If there is no
		 * instance number, load to the currently displayed slide.
		 */
		for (Praparat prap : praps) {
			// set roi to series
			int roiFramePos = getSlicePosition(prap, roi);
			int instNo = -1;
			if (roiFramePos >= 0) {
				SlideGlass s = prap.getAllSlides().get(roiFramePos);
				roiObj.setSlideGlass(s, false);
				s.addRoi(roiObj);
			} else {// no consistent roi
					// escape by InstNo
				String roiInstNoString = roiObj.getProperty(RoiDBKey.InstanceNo.name());
				if (roiInstNoString != null) {
					try {
						instNo = Integer.parseInt(roiInstNoString);
					} catch (NumberFormatException e) {
						// do nothing
					}
				}
				if (instNo >= 0) {
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
				} else {
					// set current slide
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
		if (rois.size() == 0) {
			Log.logger.info("Rois in selection list is empty.");
			return;
		}
		/*
		 * select only one roi no selected or multi select -> save all
		 */
		if (selectedRois.size() == 1) {
			saveRoi(selectedRois.get(selectedRois.keySet().iterator().next()));
		} else {
			// set save dest
			SaveDialog sd = new SaveDialog("Save ROIs...", "RoiSet", ".zip");
			String name = sd.getFileName();
			if (name == null)
				return;
			if (!(name.endsWith(".zip") || name.endsWith(".ZIP")))
				name = name + ".zip";
			String dir = sd.getDirectory();
			String path = dir + name;
			DataOutputStream out = null;
			// save all
			if (selectedRois.size() == 0) {
				try {
					ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
					out = new DataOutputStream(new BufferedOutputStream(zos));
					String[] keys = rois.keySet().toArray(new String[rois.size()]);
					RoiEncoder re = new RoiEncoder(out);
					for (int i = 0; i < keys.length; i++) {
						RoiObj roiObj = rois.get(keys[i]);
						// save() のループ内や saveRoi() の冒頭に追加
						if (roiObj instanceof com.vis.core.view.D3.roi.SphereRoi3D || 
							roiObj instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
							Log.logger.warning("3D ROIs cannot be exported as standard ImageJ .roi files. Skipping: " + roiObj.getName());
							continue;
						}
						String label = roiObj.getProperty(RoiDBKey.RoiID.name());
						// ファイル名の先頭に "0005_" のようにポジションを付与する
						String posStr = roiObj.getProperty(RoiDBKey.Position.name());
						if (posStr != null && !posStr.isEmpty() && !posStr.equals("0")) {
							try {
								int pos = Integer.parseInt(posStr);
								// ゼロ埋め（例: 000012_RoiID.roi）にしてソートしやすくする
								label = String.format("%06d", pos) + "_" + label;
							} catch (NumberFormatException e) {
							}
						}
						// in here, convert2Roi do roi.setPosition(pos)
						Roi roi = new RoiConverter().convert2Roi(roiObj);
						if (roi == null) {
							continue;
						}

						if (!label.endsWith(".roi"))
							label += ".roi";
						zos.putNextEntry(new ZipEntry(label));
						re.write(roi);
						out.flush();
					}
					out.close();
				} catch (IOException e) {
					errorMessage = "" + e;
					Log.logger.warning(errorMessage);
					return;
				} finally {
					if (out != null)
						try {
							out.close();
						} catch (IOException e) {
						}
				}
				// save selected rois
			} else {
				try {
					ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
					out = new DataOutputStream(new BufferedOutputStream(zos));
					String[] keys = selectedRois.keySet().toArray(new String[selectedRois.size()]);
					RoiEncoder re = new RoiEncoder(out);
					for (int i = 0; i < keys.length; i++) {
						RoiObj roiObj = rois.get(keys[i]);
						String label = roiObj.getProperty(RoiDBKey.RoiID.name());

						// ファイル名の先頭に "0005_" のようにポジションを付与する
						String posStr = roiObj.getProperty(RoiDBKey.Position.name());
						if (posStr != null && !posStr.isEmpty() && !posStr.equals("0")) {
							try {
								int pos = Integer.parseInt(posStr);
								// ゼロ埋め（例: 000012_RoiID.roi）にしてソートしやすくする
								label = String.format("%06d", pos) + "_" + label;
							} catch (NumberFormatException e) {
							}
						}
						// roi.setPosition(pos) was done in this.
						Roi roi = new RoiConverter().convert2Roi(roiObj);
						if (roi == null) {
							continue;
						}
						if (!label.endsWith(".roi"))
							label += ".roi";
						zos.putNextEntry(new ZipEntry(label));
						re.write(roi);
						out.flush();
					}
					out.close();
				} catch (IOException e) {
					errorMessage = "" + e;
					Log.logger.warning(errorMessage);
					return;
				} finally {
					if (out != null)
						try {
							out.close();
						} catch (IOException e) {
						}
				}
			}
		}
	}

	/**
	 * Save roi to file.
	 * 
	 * @param roiObj
	 */
	void saveRoi(RoiObj roiObj) {
		if (roiObj == null) {
			return;
		}
		// save() のループ内や saveRoi() の冒頭に追加
		if (roiObj instanceof com.vis.core.view.D3.roi.SphereRoi3D || 
			roiObj instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
			Log.logger.warning("3D ROIs cannot be exported as standard ImageJ .roi files. Skipping: " + roiObj.getName());
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
			} catch (NumberFormatException e) {
			}
		}
		// in here, convert2Roi do roi.setPosition(pos)
		Roi roi = new RoiConverter().convert2Roi(roiObj);
		if (roi == null) {
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
		if (!name2.endsWith(".roi"))
			name2 = name2 + ".roi";
		path = dir + name2;
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
	 * This method does not rename by adding a position string(e.g., 0001) in head
	 * of file name.
	 * 
	 * @param roiObj
	 * @param dest   : ./folder/roi_instance.roi
	 */
	public static void saveRoi(RoiObj roiObj, String dest) {

		if (roiObj == null) {
			return;
		}
		Roi roi = new RoiConverter().convert2Roi(roiObj);
		if (roi == null) {
			Log.logger.log(Level.SEVERE, "Roi conversion was failed. Cannot save rois...");
			return;
		}

		if (!dest.endsWith(".roi"))
			dest = dest + ".roi";
		RoiEncoder re = new RoiEncoder(dest);
		try {
			re.write(roi);
		} catch (IOException e) {
			Log.logger.warning(e.getMessage());
			return;
		}
	}

	/*
	 * General use. create RGB capture image.
	 */
	void capture() {
		if (selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		if (contains3DRoi(selectedRois)) {
			PopUpMessage.showDialog(this, "Not Supported", "This operation is for 2D ROIs only. 3D ROIs are not supported.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
			return;
		}

		boolean hasSameImp = reffereingSameImage(selectedRois);
		if (!hasSameImp) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectSameImage"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Set<String> keys = selectedRois.keySet();
		ImagePlus firstImp = null;
		ImagePlus dup = null;
		for (String key : keys) {
			RoiObj roi = selectedRois.get(key);
			if (firstImp == null) {
				firstImp = roi.getImage();
				dup = firstImp.duplicate();// duplicate image pixels
				ColorProcessor cp = dup.getProcessor().convertToColorProcessor();
				dup.setProcessor(cp);
				dup.setTitle("captured");
			}
			dup = captureRoi(roi, dup);
		}
		IJ.saveAsTiff(dup, null);
	}

	/**
	 * keep calibration and bit-depth
	 * 
	 */
	void captureWithKeepImageContext() {
		if (selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		if (contains3DRoi(selectedRois)) {
			PopUpMessage.showDialog(this, "Not Supported", "This operation is for 2D ROIs only. 3D ROIs are not supported.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
			return;
		}

		boolean hasSameImp = true;
		Set<String> keys = selectedRois.keySet();
		ImagePlus firstImp = null;
		for (String key : keys) {
			if (firstImp == null) {
				firstImp = selectedRois.get(key).getImage();
				continue;
			}
			ImagePlus imp = selectedRois.get(key).getImage();
			if (!imp.equals(firstImp))
				hasSameImp = false;
		}
		if (!hasSameImp) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectSameImage"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		ImagePlus dup = firstImp.duplicate();
		for (String key : keys) {
			RoiObj roi = selectedRois.get(key);
			dup = captureRoi(roi, dup);
		}
		IJ.saveAsTiff(dup, null);
	}

	ImagePlus captureRoi(RoiObj roi, ImagePlus imp) {
		if (roi == null)
			return null;
		if (roi instanceof com.vis.core.view.D3.roi.SphereRoi3D
				|| roi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
			return null;
		}
		imp.deleteRoi();
		ImageProcessor ip = imp.getProcessor();
		boolean isRGB = ip != null && ip instanceof ColorProcessor;
		if (isRGB) {
			ip.setColor(roi.getStrokeColor());// Sets the default fill/draw value
		}
		roi.drawPixels(ip);
		return imp;
	}
	
	// ★ ヘルパーメソッドを追加
	private boolean contains3DRoi(HashMap<String, RoiObj> rois) {
		for (RoiObj r : rois.values()) {
			if (r instanceof com.vis.core.view.D3.roi.SphereRoi3D
					|| r instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 */
	void fill() {
		if (selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (contains3DRoi(selectedRois)) {
			PopUpMessage.showDialog(this, "Not Supported", "This operation is for 2D ROIs only. 3D ROIs are not supported.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
			return;
		}
		Set<String> keys = selectedRois.keySet();
		for (String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi == null)
				continue;
			if (!roi.isArea()) {
				continue;
			}
			SlideGlass slide = roi.getSlideGlass();
			if (slide == null) {
				continue;
			}
			ImagePlus imp = slide.getOriginalImage();
			imp.deleteRoi();
			ImageProcessor ip = imp.getProcessor();
			boolean isRGB = ip != null && (ip instanceof ColorProcessor);
			if (isRGB) {
				ip.snapshot();// backup
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
		if (selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (contains3DRoi(selectedRois)) {
			PopUpMessage.showDialog(this, "Not Supported", "This operation is for 2D ROIs only. 3D ROIs are not supported.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
			return;
		}

		// undo save
		SlideGlass targetSlide = selectedRois.values().iterator().next().getSlideGlass();
		if (targetSlide != null)
			targetSlide.saveUndoState();

		Set<String> keys = selectedRois.keySet();
		for (String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi == null)
				continue;
			SlideGlass s = roi.getSlideGlass();
			int type = roi.getType();
			if (!roi.isArea())
				continue;

			// PolygonRoi の場合は双方向トグル（スイッチ）にする
			if (roi instanceof PolygonRoi) {
				PolygonRoi polyRoi = (PolygonRoi) roi;
				if (polyRoi.isSplineFit()) {
					// すでに滑らかなら、元のカクカクに戻す
					polyRoi.removeSplineFit();
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
				} else {
					// カクカクなら、滑らかにする
					polyRoi.fitSpline(type == RoiType.TRACED_ROI.id() ? 20 : 100);
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
				}
				if (s != null) {
					s.replaceRoi(roi.getUIDs(), polyRoi); // DB保存と描画更新
				}
				continue;
			}

			// 以下は元のロジック（RECTANGLEやCOMPOSITEをPolygonRoiに変換して滑らかにする）
			if (type == RoiType.RECTANGLE.id()) {
				FloatPolygon fpg = roi.getFloatPolygon();
				PolygonRoi polyRoi = new PolygonRoi(fpg, RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s, false);
				polyRoi.fitSpline(100);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
				if (s != null)
					s.replaceRoi(roi.getUIDs(), polyRoi);

			} else if (type == RoiType.COMPOSITE.id()) {
				ShapeRoi sRoi = (ShapeRoi) roi;
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
					PolygonRoi polyRoi = new PolygonRoi(new Polygon(sparseX, sparseY, newSize), RoiType.POLYGON.id(),
							null);
					polyRoi.setSlideGlass(s, false);
					polyRoi.fitSpline(100);
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
					if (s != null)
						s.replaceRoi(roi.getUIDs(), polyRoi);
					continue;
				}
				PolygonRoi polyRoi = new PolygonRoi(new Polygon(xps, yps, num), RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s, false);
				polyRoi.fitSpline(100);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "true");
				if (s != null)
					s.replaceRoi(roi.getUIDs(), polyRoi);
			}
		}
		updateState();
	}

	void convert2Polygon() {
		if (selectedRois.size() < 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (contains3DRoi(selectedRois)) {
			PopUpMessage.showDialog(this, "Not Supported", "This operation is for 2D ROIs only. 3D ROIs are not supported.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
			return;
		}
		Set<String> keys = selectedRois.keySet();
		for (String roiID : keys) {
			RoiObj roi = selectedRois.get(roiID);
			if (roi == null)
				continue;
			SlideGlass s = roi.getSlideGlass();
			if (s == null)
				continue;
			int type = roi.getType();

			// ★ 修正: すでにPolygonRoiの場合でも、SplineFitされていればカクカクに解除する
			if (roi instanceof PolygonRoi) {
				PolygonRoi polyRoi = (PolygonRoi) roi;
				if (polyRoi.isSplineFit()) {
					polyRoi.removeSplineFit();
					polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
					s.replaceRoi(roi.getUIDs(), polyRoi);
				}
				continue;
			}

			if (!roi.isArea()) {
				continue;
			}

			if (type != RoiType.COMPOSITE.id()) {
				FloatPolygon fpg = roi.getFloatPolygon();
				PolygonRoi polyRoi = new PolygonRoi(fpg, RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s, false);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
				s.replaceRoi(roi.getUIDs(), polyRoi);
			} else {
				ShapeRoi sRoi = (ShapeRoi) roi;
				Polygon poly = sRoi.getPolygon();
				int num = poly.npoints;
				int[] xps = poly.xpoints;
				int[] yps = poly.ypoints;
				PolygonRoi polyRoi = new PolygonRoi(new Polygon(xps, yps, num), RoiType.POLYGON.id(), null);
				polyRoi.setSlideGlass(s, false);
				polyRoi.setProperty(RoiMetaContextKey.isSplineFit.name(), "false");
				s.replaceRoi(roi.getUIDs(), polyRoi);
			}
		}
		updateState();
	}

	private void combine() {
		if (selectedRois.size() < 2) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRois"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// ★ 3D-ROI が含まれている場合の専用処理へ分岐
		if (contains3DRoi(selectedRois)) {
			combine3D(selectedRois);
			return;
		}

		// 既存の2Dロジック...
		RoiObj res = null;
		if (countPointRois(selectedRois) == selectedRois.size()) {
			res = combinePoints(selectedRois);
		} else {
			res = combineRois(selectedRois);
		}
		if (res != null && res.getSlideGlass() != null) {
			res.getSlideGlass().addRoi(res);
			updateState();
		}
	}

	
	private int countPointRois(HashMap<String, RoiObj> rois) {
		int nPointRois = 0;
		for (String roiid : rois.keySet()) {
			RoiObj r = rois.get(roiid);
			if (r.getType() == RoiType.POINT.id()) {
				nPointRois++;
			}
		}
		return nPointRois;
	}

	private RoiObj combineRois(HashMap<String, RoiObj> rois) {
		if (rois.size() == 1) {
			return null;
		}
		com.vis.core.view.D2.roi.ShapeRoi s1 = null, s2 = null;
		for (String key : rois.keySet()) {
			RoiObj roi = rois.get(key);
			RoiObj roi_ = null;
			if (!roi.isArea() && (roi.getType() != RoiType.POINT.id() && roi.getType() != RoiType.MULTIPOINT.id())) {
				roi_ = RoiObj.convertLineToArea(roi);
			} else {
				roi_ = roi;
			}
			// first time loop
			if (s1 == null) {
				// set new RoiId
				s1 = new com.vis.core.view.D2.roi.ShapeRoi(roi_);
			} else {// second or more loop
				s2 = new com.vis.core.view.D2.roi.ShapeRoi(roi_);
				s1.or(s2);
			}
		}
		// finally, s1 was combined all rois.
		return s1;
	}

	RoiObj combinePoints(HashMap<String, RoiObj> rois) {
		SlideGlass slide = null;
		FloatPolygon fp = new FloatPolygon();
		for (String key : rois.keySet()) {
			RoiObj roi = rois.get(key);
			if (slide == null) {
				slide = roi.getSlideGlass();
			}
			FloatPolygon fpi = roi.getFloatPolygon();
			for (int i = 0; i < fpi.npoints; i++) {
				fp.addPoint(fpi.xpoints[i], fpi.ypoints[i]);
			}
		}
		return new com.vis.core.view.D2.roi.PointRoi(fp, slide);
	}

	/*
	 * Split AND XOR
	 */
	void split() {
		if (selectedRois.size() != 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectCompositeRoi"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// ★ 3D-ROI の場合の専用処理へ分岐
		if (contains3DRoi(selectedRois)) {
			split3D(selectedRois);
			return;
		}
		String key = selectedRois.keySet().iterator().next();
		RoiObj roi = selectedRois.get(key);
		if (roi == null)
			return;
		SlideGlass slide = roi.getSlideGlass();
		if (slide == null)
			return;
		int type = roi.getType();
		if (type != RoiType.COMPOSITE.id())
			return;
		RoiObj[] roiBlobs = ((ShapeRoi) roi).getRois();
		for (int i = 0; i < roiBlobs.length; i++) {
			slide.addRoi(roiBlobs[i]);
		}
		updateState();
	}

	/**
	 * calculates the intersection of area, line and point selections. If there is
	 * one PointRoi in the list of selected Rois, the points inside all selected
	 * area rois are kept. If more than one PointRoi is selected, the PointRois get
	 * converted to area rois with each pixel containing at least one point
	 * selected.
	 */
	void and() {
		if (selectedRois.size() <= 1) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectRois"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// ★ 3D-ROI が含まれている場合の専用処理へ分岐
		if (contains3DRoi(selectedRois)) {
			and3D(selectedRois);
			return;
		}
		if (!reffereingSameImage(selectedRois)) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectSameImage"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int nPointRois = countPointRois(selectedRois);
		ShapeRoi s1 = null;
		com.vis.core.view.D2.roi.PointRoi pointRoi = null;
		Set<String> keys = selectedRois.keySet();
		SlideGlass slide = null;
		for (String key : keys) {
			RoiObj roi = selectedRois.get(key);
			if (roi == null)
				continue;
			if (s1 == null) {
				slide = roi.getSlideGlass();
				if (nPointRois == 1 && roi.getType() == Roi.POINT) {
					pointRoi = (PointRoi) roi;
					continue; // PointRoi will be handled at the end
				}
				s1 = new ShapeRoi(roi);
			} else {
				if (nPointRois == 1 && roi.getType() == Roi.POINT) {
					pointRoi = (PointRoi) roi;
					continue; // PointRoi will be handled at the end
				}
				ShapeRoi s2 = new ShapeRoi(roi);
				s1.and(s2);
			}
		}
		if (s1 == null)
			return;
		if (pointRoi != null) {
			slide.addRoi(pointRoi.containedPoints(s1));
		} else {
			slide.addRoi(s1.trySimplify());
		}
		updateState();
	}

	/**
	 * 
	 */
	void xor() {
		if (selectedRois.size() < 2) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.moreThanOne"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// ★ 3D-ROI が含まれている場合の専用処理へ分岐
		if (contains3DRoi(selectedRois)) {
			xor3D(selectedRois);
			return;
		}
		if (!reffereingSameImage(selectedRois)) {
			JOptionPane.showConfirmDialog(this, Resources.i18n("RoiObjManager.error.selectSameImage"), Resources.i18n("dialog.title.graphy"), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		RoiObj roi2 = RoiObj.xor(getSelectedRoisAsArray(selectedRois));
		if (roi2 != null) {
			roi2.getSlideGlass().addRoi(roi2);
		}
		updateState();
	}
	
	private void combine3D(HashMap<String, RoiObj> rois) {
		Praparat pp = null;
		List<com.vis.core.view.D3.roi.FreeFormRoi3D> target3Ds = new ArrayList<>();
		String newGroupId = String.valueOf((int) (System.currentTimeMillis() % 1000000000L));
		List<RoiObj> safeRoiList = new ArrayList<>(rois.values());

		// ★ 追加：同一Praparat (Series) であるかの厳密なチェック
		String targetSeriesUID = null;

		for (RoiObj r : safeRoiList) {
			String seriesUID = r.getProperty(RoiDBKey.SeriesInstanceUID.name());
			if (seriesUID == null && r.getUIDs() != null) seriesUID = r.getUIDs().get(RoiDBKey.SeriesInstanceUID);

			if (targetSeriesUID == null) {
				targetSeriesUID = seriesUID;
				if (r.getSlideGlass() != null) pp = r.getSlideGlass().getPraparat();
			} else if (seriesUID != null && !targetSeriesUID.equals(seriesUID)) {
				PopUpMessage.showDialog(list, "Mismatch", "All selected ROIs must belong to the same Praparat (Series).", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
				return;
			}

			if (r instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
				target3Ds.add((com.vis.core.view.D3.roi.FreeFormRoi3D) r.clone());
			} else if (r instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
				com.vis.core.view.D3.roi.FreeFormRoi3D converted = com.vis.core.view.D3.roi.FreeFormRoi3D.createFromSphere(pp, (com.vis.core.view.D3.roi.SphereRoi3D) r, newGroupId);
				if (converted != null) target3Ds.add(converted);
			} else {
				PopUpMessage.showDialog(list, "Invalid Selection", "Cannot combine 2D ROIs with 3D ROIs directly.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
				return;
			}
		}

		if (target3Ds.size() < 2 || pp == null) return;

		com.vis.core.view.D3.roi.FreeFormRoi3D result3D = target3Ds.get(0);
		result3D.setProperty(RoiDBKey.RoiGroup.name(), newGroupId);
		result3D.setProperty(RoiDBKey.RoiID.name(), RoiObj.createRoiIndex());
		result3D.setName("Combined 3D");

		for (int i = 1; i < target3Ds.size(); i++) {
			result3D.or(target3Ds.get(i));
		}

		update3DPositionToCenter(result3D, pp, safeRoiList.get(0));

		for (RoiObj r : safeRoiList) {
			purgeRoiFromSystem(pp, r);
		}

		pp.addRoi3D(result3D);
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) db.insertRoi(result3D.readContext());

		for (SlideGlass sg : pp.getAllSlides().values()) {
			if (sg != null) sg.repaintCanvasGlass();
		}
		updateState();
	}

	private void and3D(HashMap<String, RoiObj> rois) {
		Praparat pp = null;
		List<com.vis.core.view.D3.roi.FreeFormRoi3D> target3Ds = new ArrayList<>();
		String newGroupId = String.valueOf((int) (System.currentTimeMillis() % 1000000000L));
		List<RoiObj> safeRoiList = new ArrayList<>(rois.values());

		// ★ 追加：同一Praparat (Series) であるかの厳密なチェック
		String targetSeriesUID = null;

		for (RoiObj r : safeRoiList) {
			String seriesUID = r.getProperty(RoiDBKey.SeriesInstanceUID.name());
			if (seriesUID == null && r.getUIDs() != null) seriesUID = r.getUIDs().get(RoiDBKey.SeriesInstanceUID);

			if (targetSeriesUID == null) {
				targetSeriesUID = seriesUID;
				if (r.getSlideGlass() != null) pp = r.getSlideGlass().getPraparat();
			} else if (seriesUID != null && !targetSeriesUID.equals(seriesUID)) {
				PopUpMessage.showDialog(list, "Mismatch", "All selected ROIs must belong to the same Praparat (Series).", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
				return;
			}

			if (r instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
				target3Ds.add((com.vis.core.view.D3.roi.FreeFormRoi3D) r.clone());
			} else if (r instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
				com.vis.core.view.D3.roi.FreeFormRoi3D converted = com.vis.core.view.D3.roi.FreeFormRoi3D.createFromSphere(pp, (com.vis.core.view.D3.roi.SphereRoi3D) r, newGroupId);
				if (converted != null) target3Ds.add(converted);
			} else {
				PopUpMessage.showDialog(list, "Invalid Selection", "Cannot AND 2D ROIs with 3D ROIs directly.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
				return;
			}
		}

		if (target3Ds.size() < 2 || pp == null) return;

		com.vis.core.view.D3.roi.FreeFormRoi3D result3D = target3Ds.get(0);
		result3D.setProperty(RoiDBKey.RoiGroup.name(), newGroupId);
		result3D.setProperty(RoiDBKey.RoiID.name(), RoiObj.createRoiIndex());
		result3D.setName("AND 3D");

		for (int i = 1; i < target3Ds.size(); i++) {
			result3D.and(target3Ds.get(i));
		}

		update3DPositionToCenter(result3D, pp, safeRoiList.get(0));

		for (RoiObj r : safeRoiList) {
			purgeRoiFromSystem(pp, r);
		}

		pp.addRoi3D(result3D);
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) db.insertRoi(result3D.readContext());

		for (SlideGlass sg : pp.getAllSlides().values()) {
			if (sg != null) sg.repaintCanvasGlass();
		}
		updateState();
	}

	private void xor3D(HashMap<String, RoiObj> rois) {
		Praparat pp = null;
		List<com.vis.core.view.D3.roi.FreeFormRoi3D> target3Ds = new ArrayList<>();
		String newGroupId = String.valueOf((int) (System.currentTimeMillis() % 1000000000L));
		List<RoiObj> safeRoiList = new ArrayList<>(rois.values());

		// ★ 追加：同一Praparat (Series) であるかの厳密なチェック
		String targetSeriesUID = null;

		for (RoiObj r : safeRoiList) {
			String seriesUID = r.getProperty(RoiDBKey.SeriesInstanceUID.name());
			if (seriesUID == null && r.getUIDs() != null) seriesUID = r.getUIDs().get(RoiDBKey.SeriesInstanceUID);

			if (targetSeriesUID == null) {
				targetSeriesUID = seriesUID;
				if (r.getSlideGlass() != null) pp = r.getSlideGlass().getPraparat();
			} else if (seriesUID != null && !targetSeriesUID.equals(seriesUID)) {
				PopUpMessage.showDialog(list, "Mismatch", "All selected ROIs must belong to the same Praparat (Series).", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
				return;
			}

			if (r instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
				target3Ds.add((com.vis.core.view.D3.roi.FreeFormRoi3D) r.clone());
			} else if (r instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
				com.vis.core.view.D3.roi.FreeFormRoi3D converted = com.vis.core.view.D3.roi.FreeFormRoi3D.createFromSphere(pp, (com.vis.core.view.D3.roi.SphereRoi3D) r, newGroupId);
				if (converted != null) target3Ds.add(converted);
			} else {
				PopUpMessage.showDialog(list, "Invalid Selection", "Cannot XOR 2D ROIs with 3D ROIs directly.", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
				return;
			}
		}

		if (target3Ds.size() < 2 || pp == null) return;

		com.vis.core.view.D3.roi.FreeFormRoi3D result3D = target3Ds.get(0);
		result3D.setProperty(RoiDBKey.RoiGroup.name(), newGroupId);
		result3D.setProperty(RoiDBKey.RoiID.name(), RoiObj.createRoiIndex());
		result3D.setName("XOR 3D");

		for (int i = 1; i < target3Ds.size(); i++) {
			result3D.xor(target3Ds.get(i));
		}

		update3DPositionToCenter(result3D, pp, safeRoiList.get(0));

		for (RoiObj r : safeRoiList) {
			purgeRoiFromSystem(pp, r);
		}

		pp.addRoi3D(result3D);
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) db.insertRoi(result3D.readContext());

		for (SlideGlass sg : pp.getAllSlides().values()) {
			if (sg != null) sg.repaintCanvasGlass();
		}
		updateState();
	}

	private void split3D(HashMap<String, RoiObj> rois) {
		List<RoiObj> safeRoiList = new ArrayList<>(rois.values());
		RoiObj r = safeRoiList.get(0);
		Praparat pp = r.getSlideGlass() != null ? r.getSlideGlass().getPraparat() : null;
		if (pp == null) return;

		com.vis.core.view.D3.roi.FreeFormRoi3D targetFF = null;
		if (r instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
			targetFF = (com.vis.core.view.D3.roi.FreeFormRoi3D) r;
		} else if (r instanceof com.vis.core.view.D3.roi.SphereRoi3D) {
			targetFF = com.vis.core.view.D3.roi.FreeFormRoi3D.createFromSphere(pp, (com.vis.core.view.D3.roi.SphereRoi3D) r, "temp_split");
		}

		if (targetFF == null) return;

		List<com.vis.core.view.D3.roi.FreeFormRoi3D> components = targetFF.splitIntoConnectedComponents();
		
		if (components.isEmpty() || components.size() == 1) {
			PopUpMessage.showDialog(list, "Split 3D", "The selected 3D ROI is already a single connected component.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		purgeRoiFromSystem(pp, r);

		DatabaseHandler db = DatabaseHandler.getInstance();
		int count = 1;
		String originalName = r.getName() != null ? r.getName() : "Split 3D";
		
		for (com.vis.core.view.D3.roi.FreeFormRoi3D comp : components) {
			String newGroupId = String.valueOf((int) (System.currentTimeMillis() % 1000000000L) + count);
			comp.setProperty(RoiDBKey.RoiGroup.name(), newGroupId);
			comp.setName(originalName + " - Part " + count);
			
			comp.setStrokeColor(r.getStrokeColor());
			comp.setStrokeWidth(r.getStrokeWidth());

			// ★ 追加：分離されたコンポーネントごとの実際の中心Zを計算してPositionを更新
			update3DPositionToCenter(comp, pp, r);
			
			pp.addRoi3D(comp);
			if (db != null) db.insertRoi(comp.readContext());
			count++;
		}

		for (SlideGlass sg : pp.getAllSlides().values()) {
			if (sg != null) sg.repaintCanvasGlass();
		}
		updateState();
		PopUpMessage.showDialog(list, "Success", "3D ROI has been split into " + components.size() + " independent components.", JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
	}
	
	boolean reffereingSameImage(HashMap<String, RoiObj> selectedRois) {
		boolean hasSameImp = true;
		Set<String> keys = selectedRois.keySet();
		ImagePlus firstImp = null;
		for (String key : keys) {
			if (firstImp == null) {
				firstImp = selectedRois.get(key).getImage();
				continue;
			}
			ImagePlus imp = selectedRois.get(key).getImage();
			if (!imp.equals(firstImp))
				hasSameImp = false;
		}
		return hasSameImp;
	}
	
	/**
	 * 3D-ROI をメモリ、全スライドのキャンバス、および DB から完全に消去します。
	 * （描画コンテキストによる slide プロパティのズレを無効化する安全装置）
	 */
	private void purgeRoiFromSystem(Praparat pp, RoiObj r) {
		if (pp == null || r == null) return;
		
		// 1. Praparatの3D管理リストから除外
		pp.removeRoi3D(r);
		
		if(pp.getCurrentRoi() == r) {
			pp.setCurrentRoi(null);
		}
		
		// 2. 描画コンテキストのズレを考慮し、全スライドの2Dリストから総当たりで強制削除
		for (SlideGlass sg : pp.getAllSlides().values()) {
			if (sg != null) {
				sg.getRois().remove(r); // キャンバスから剥がす
				CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
				if (cg != null && cg.getCurrentRoi() == r) {
					cg.setCurrentRoi2NULL(); // マウスイベントのゴースト化を解除
				}
			}
		}
		
		// 3. DBからの確実な物理削除
		DatabaseHandler db = DatabaseHandler.getInstance();
		if (db != null) {
			HashMap<RoiDBKey, String> uids = r.getUIDs();
			if (uids != null) {
				db.deleteRoi(uids.get(RoiDBKey.PatientID), uids.get(RoiDBKey.StudyInstanceUID),
							 uids.get(RoiDBKey.SeriesInstanceUID), uids.get(RoiDBKey.SOPInstanceUID),
							 uids.get(RoiDBKey.RoiID));
			}
		}
	}
	
	/**
	 * 3D-ROI の実際のマスクデータから有効な Z 範囲を解析し、
	 * 塊の中央スライスを代表 Position として再計算・セットします。
	 */
	private void update3DPositionToCenter(com.vis.core.view.D3.roi.FreeFormRoi3D result3D, Praparat pp, RoiObj sourceRoi) {
		if (result3D == null || pp == null || sourceRoi == null) return;

		int[] dims = result3D.getDimensions();
		if (dims == null || dims.length < 3) return;
		int dimZ = dims[2];

		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		
		// 実際のマスクデータから有効なZ範囲を調べる
		for (int k = 0; k < dimZ; k++) {
			if (result3D.getMaskAsBytes(k) != null) {
				minZ = Math.min(minZ, k);
				maxZ = Math.max(maxZ, k);
			}
		}

		// 共通の次元（Channel, Time）をソースから引き継ぐ
		String cStr = sourceRoi.getProperty("Dim_C");
		String tStr = sourceRoi.getProperty("Dim_T");
		int targetC = (cStr != null && !cStr.isEmpty()) ? Integer.parseInt(cStr) : 0;
		int targetT = (tStr != null && !tStr.isEmpty()) ? Integer.parseInt(tStr) : 0;

		// マスクが存在した場合、中央のZを計算して反映
		if (minZ <= maxZ) {
			int centerZ = minZ + (maxZ - minZ) / 2;
			int centerZct = pp.calcZctIndex(new int[]{centerZ, targetC, targetT});
			
			result3D.setProperty("Dim_Z", String.valueOf(centerZ));
			result3D.setProperty("Dim_C", String.valueOf(targetC));
			result3D.setProperty("Dim_T", String.valueOf(targetT));
			result3D.setProperty(RoiDBKey.Position.name(), String.valueOf(centerZct + 1));
			
			SlideGlass centerSg = pp.getAllSlides().get(centerZct);
			if (centerSg != null) {
				result3D.setSlideGlass(centerSg, false);
			}
		}
	}

	RoiObj[] getSelectedRoisAsArray(HashMap<String, RoiObj> selectedRois) {
		RoiObj[] array = new RoiObj[selectedRois.size()];
		Set<String> keys = selectedRois.keySet();
		int pos = 0;
		for (String key : keys) {
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
		for (Field f : Color.class.getFields()) {
			Color sys_c = null;
			try {
				sys_c = (Color) f.get(null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
				return null;
			}
			if (sys_c == null) {
				continue;
			}
			int dif_r = Math.abs(c.getRed() - sys_c.getRed());
			int dif_g = Math.abs(c.getGreen() - sys_c.getGreen());
			int dif_b = Math.abs(c.getBlue() - sys_c.getBlue());
			int sum = dif_r + dif_g + dif_b;
			if (sum == 0) {
				return f.getName().trim().toLowerCase();
			} else {
				if (rgbDistance == -1) {
					rgbDistance = sum;
					candidateColorName = f.getName().trim().toLowerCase();
				}
				if (rgbDistance > sum) {
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
		} else if (command.equals(Functions.Measure.name())) {
			measure();
		} else if (command.equals(Functions.Delete.name())) {
			delete();
		} else if (command.equals(Functions.LineAndColor.name())) {
			SwingUtilities.invokeLater(() -> lineAndColor());
		} else if (command.equals(Functions.Update.name())) {
			updateState();
		} else if (command.equals(Functions.Duplicate.name())) {
			duplicate();
		} else if (command.equals(Functions.GroupTo3D.name())) {
			groupTo3d();
		} else if (command.equals(Functions.Ungroup3D.name())) {
			ungroup3d();
		} else if (command.equals(Functions.Move.name())) { // ★追加: Moveアクション
			moveRois();

			// more functions
		} else if (command.equals(moreButtonLabel)) {
			JButton btn = (JButton) e.getSource();// more btn
			int patListW = patList.getWidth();
			int patListH = patList.getHeight();
			Point bloc = btn.getLocation();
			// location XY is RoiObjManager coordinates basis.
			pm.show(this, patListW, patListH + bloc.y + btn.getHeight() + 3);
		} else if (command.equals(Functions.Open.name())) {
			openToGraphy(null);
		} else if (command.equals(Functions.Save.name())) {
			SwingUtilities.invokeLater(() -> save());
		} else if (command.equals(Functions.SplineFit.name())) {
			splineFit();
		} else if (command.equals(Functions.ConvertToPolygon.name())) {
			convert2Polygon();
		} else if (command.equals(Functions.Fill.name())) {
			fill();
		} else if (command.equals(Functions.Capture.name())) {
			capture();
		} else if (command.equals(Functions.OR_Combine.name())) {
			combine();
		} else if (command.equals(Functions.Split.name())) {
			split();
		} else if (command.equals(Functions.AND.name())) {
			and();
		} else if (command.equals(Functions.XOR.name())) {
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
		if (intStr == null) {
			return null;
		}
		try {
			int v = Integer.parseInt(intStr);
			return v;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private boolean isIgnoreValue(Integer v) {
		if (v == null) {
			return true;
		} else if (v == Integer.MIN_VALUE) {
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

				if (currentRoi == null) {
					Log.logger.warning("RoiObjManager: currentRoi is null, skipping.");
					return;
				}
				if (currentRoi.getSlideGlass() == null) {
					Log.logger.warning("RoiObjManager: currentRoi.getSlideGlass() is null, skipping.");
					return;
				}
				Praparat pp = currentRoi.getSlideGlass().getPraparat();
				if (currentRoi instanceof com.vis.core.view.D3.roi.SphereRoi3D
						|| currentRoi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D) {
					pp.setCurrentRoi(currentRoi);
				} else {
					pp.setCurrentRoi(null); // 2Dが選ばれたら3Dカレントはクリア
				}
				
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
				JOptionPane.showMessageDialog(input, String.format(Resources.i18n("RoiObjManager.info.dateFormat"), dateFormat.toPattern()), Resources.i18n("dialog.title.information"), JOptionPane.INFORMATION_MESSAGE);
				return false;
			}
		}
	}

}
