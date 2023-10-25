package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * @author tatsunidas
 */
public class StageToolBar extends JToolBar {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private StageView stage; 
	/*
	 * buttons design https://material.io/tools/icons/?style=outline
	 */
	ArrayList<String> buttonLabels = new ArrayList<String>();
	ArrayList<String> keys = new ArrayList<>();

	public StageToolBar(StageView stage) {
		this.stage = stage;
		loadBtnKeys();
		loadButtons(keys, initButtonList());
	}

	private void loadBtnKeys() {
		keys.add("delete");
		keys.add("invert");
//		keys.add("export");
//		keys.add("browseDB");
//		keys.add("burnCD");// OS dependent, it is not default.
//		keys.add("metadata");
//		keys.add("send");
//		keys.add("query");//do not need
//		keys.add("2dviewer");
//		keys.add("3dviewer");
//		keys.add("settings");
	}

	public void loadButtons(ArrayList<String> keys, HashMap<String, String> buttonLabels) {
		removeAll();
		for (String key : keys) {
			JButton btn = new JButton(key, new ImageIcon(getClass().getResource(buttonLabels.get(key))));
			btn.setName(key);
			btn.setFocusPainted(true);
			btn.setVerticalTextPosition(SwingConstants.BOTTOM);
			btn.setHorizontalTextPosition(SwingConstants.CENTER);
			setAction(btn);
			add(btn);
		}
		repaint();
	}

	private void setAction(JButton btn) {
		// TODO Auto-generated method stub
		switch (btn.getName()) {
		case "delete":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					stage.removeSelectedAllPraparatOnSatage();
				}
			});
			break;
		case "invert":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
//					StageView activeSatge = 
				}
			});
			break;
//		case "burnCD":
//			btn.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					/*
//					 * できるようにはなった。 see, TestCDRecord 使うときは、cdrtoolsのバイナリを.GRAPHY/cdrtoolsに入れて使う。
//					 * ただ、Linux、Windows、Macでテストしないといけないので、 最終調整まで取っておこうと思う。
//					 */
//					JOptionPane.showConfirmDialog(ApplicationContext.getInstance().getMainScreen(),
//							"Under development... \n Sorry !");
//				}
//			});
//			break;
//		case "metadata":
//			btn.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					/*
//					 * only allow localtreetable
//					 */
//					ArrayList<DICOMNode> selected = ApplicationContext.getInstance().getMainScreen().getSelectedNode();
//					if (selected == null || selected.size() < 1) {
//						return;
//					}
//					DICOMNode focusNode = selected.get(0);
//					SwingUtilities.invokeLater(new Runnable() {
//						@Override
//						public void run() {
//							new DicomTagsViewer(focusNode);
//						}
//					});
//				}
//			});
//			break;
//		case "send":
//			btn.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					/*
//					 * only allow localtreetable
//					 */
//					ArrayList<DICOMNode> selected = ApplicationContext.getInstance().getMainScreen().getSelectedNode();
//					SwingUtilities.invokeLater(new Runnable() {
//						@Override
//						public void run() {
//							new DicomPostman(selected);
//						}
//					});
//				}
//			});
//			break;
//		case "2dviewer":
//			btn.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
//					ApplicationContext mediator = ApplicationContext.getInstance();
//					Viewer2DFrame viewer = mediator.getViewer2DFrame();
//					if (viewer == null) {
//						// do something ??
//						// but, this case never occur ?
//						System.out.println("Viewer2DWindow is NULL !! Please restart graphy.");
//						return;
//					}
//					if (!viewer.isVisible()) {
//						viewer.initContents();
//					}
//					ArrayList<DICOMNode> nodes = mediator.getMainScreen().getSelectedNode();
//					if (nodes == null || nodes.size() < 1) {
//						System.out.println("Viewer2DWindow is needed DICOMNode selection. return.");
//						return;
//					}
//					for (DICOMNode s : nodes) {
//						System.out.println(s.getLevelString());
//					}
//					/*
//					 * 以下の方法では、Studyレベルのノードが選択されていないと開かれない。 スタディでもなくても開くように、シリーズを軸にする。
//					 * かつ、特定のインスタンスのみを選んで、それのみを表示するのはやめる。
//					 * 用途が複雑になるので、なにか別の手立てが必要。ここで処理することではないような気がする。
//					 */
//					ArrayList<String> doneSeries = new ArrayList<String>();// ここに追加//future work
//					for (DICOMNode node : nodes) {
//						int level = node.getLevel();
//						String patID = node.getData(DICOMNode.PatientID);
//						if (level == DICOMNode.STUDY) {
//							String studyUID = node.getData(DICOMNode.StudyInstanceUID);
//							// search series
//							ArrayList<String> seriesUIDs = new ArrayList<String>();
//							for (DICOMNode chi : nodes) {
//								if (chi.getLevel() == DICOMNode.SERIES) {
//									String patIDchi = chi.getData("PatientID");
//									String studyUIDchi = chi.getData("StudyInstanceUID");
//									if (patID.equals(patIDchi) && studyUID.equals(studyUIDchi)) {
//										String seriesUID = chi.getData("SeriesInstanceUID");
//										seriesUIDs.add(seriesUID);
//										// search images
//										ArrayList<String> sopUIDs = new ArrayList<String>();
//										for (DICOMNode chichi : nodes) {
//											if (chichi.getLevel() == DICOMNode.IMAGE) {
//												String patIDchichi = chichi.getData("PatientID");
//												String studyUIDchichi = chichi.getData("StudyInstanceUID");
//												String seriesUIDchichi = chichi.getData(DICOMNode.SeriesInstanceUID);
//												if (patID.equals(patIDchichi) && studyUID.equals(studyUIDchichi)
//														&& seriesUID.equals(seriesUIDchichi)) {
//													sopUIDs.add(chichi.getData(DICOMNode.SOPInstanceUID));
//												}
//											}
//										}
////										System.out.println("Images: "+sopUIDs.size());//test
//										if (sopUIDs.size() > 0) {
//											viewer.loadImagesOnStage(patID, studyUID, seriesUID,
//													sopUIDs.toArray(new String[sopUIDs.size()]));
//										} else {
//											viewer.loadImagesOnStage(patID, studyUID, seriesUID, null);
//										}
//										doneSeries.add(patID + studyUID + seriesUID);
//									}
//								}
//							}
//							// select only study node
//							if (seriesUIDs.size() == 0) {
//								viewer.loadImagesOnStage(patID, studyUID, null, null);
//							}
////							System.out.println("Series: "+seriesUIDs.size());//test
//						}
//					} // end study level loop
//					// escape series level nodes
//					for (DICOMNode node : nodes) {
//						int level = node.getLevel();
//						String patID = node.getData(DICOMNode.PatientID);
//						if (level == DICOMNode.SERIES) {
//							String studyUID = node.getData(DICOMNode.StudyInstanceUID);
//							String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
//							// check already done
//							if (!doneSeries.contains(patID + studyUID + seriesUID)) {
//								// study level node does not selected
//								// search images in selected node
//								ArrayList<String> sopUIDs = new ArrayList<String>();
//								for (DICOMNode chi : nodes) {
//									if (chi.getLevel() == DICOMNode.IMAGE) {
//										String patIDchichi = chi.getData("PatientID");
//										String studyUIDchichi = chi.getData("StudyInstanceUID");
//										String seriesUIDchichi = chi.getData(DICOMNode.SeriesInstanceUID);
//										if (patID.equals(patIDchichi) && studyUID.equals(studyUIDchichi)
//												&& seriesUID.equals(seriesUIDchichi)) {
//											sopUIDs.add(chi.getData(DICOMNode.SOPInstanceUID));
//										}
//									}
//								}
//								if (sopUIDs.size() > 0) {
//									viewer.loadImagesOnStage(patID, studyUID, seriesUID,
//											sopUIDs.toArray(new String[sopUIDs.size()]));
//								} else {
//									viewer.loadImagesOnStage(patID, studyUID, seriesUID, null);
//								}
//								doneSeries.add(patID + studyUID + seriesUID);
//							}
//						}
//					}
//					// escape image level nodes
//					for (DICOMNode node : nodes) {
//						int level = node.getLevel();
//						String patID = node.getData(DICOMNode.PatientID);
//						if (level == DICOMNode.IMAGE) {
//							String studyUID = node.getData(DICOMNode.StudyInstanceUID);
//							String seriesUID = node.getData(DICOMNode.SeriesInstanceUID);
//							// check already done
//							if (!doneSeries.contains(patID + studyUID + seriesUID)) {
//								// study level node does not selected
//								// search images
//								ArrayList<String> sopUIDs = new ArrayList<String>();
//								for (DICOMNode chi : nodes) {
//									if (chi.getLevel() == DICOMNode.IMAGE) {
//										String patIDchichi = chi.getData("PatientID");
//										String studyUIDchichi = chi.getData("StudyInstanceUID");
//										String seriesUIDchichi = chi.getData(DICOMNode.SeriesInstanceUID);
//										if (patID.equals(patIDchichi) && studyUID.equals(studyUIDchichi)
//												&& seriesUID.equals(seriesUIDchichi)) {
//											sopUIDs.add(chi.getData(DICOMNode.SOPInstanceUID));
//										}
//									}
//								}
//								if (sopUIDs.size() > 0) {
//									viewer.loadImagesOnStage(patID, studyUID, seriesUID,
//											sopUIDs.toArray(new String[sopUIDs.size()]));
//								} else {
//									viewer.loadImagesOnStage(patID, studyUID, seriesUID, null);
//								}
//								doneSeries.add(patID + studyUID + seriesUID);
//							}
//						}
//					}
//					System.out.println("Selected series for 2DViewer: " + doneSeries.size());
//					viewer.setVisible(true);
//				}
//			});
//			break;
//		case "3dviewer":
//			btn.addActionListener(new ActionListener() {
//				@Override
//				public void actionPerformed(ActionEvent arg0) {
////					ArrayList<DICOMNode> nodes = ApplicationContext.mainScreenObj.getSelectedNode();
////					/*
////					 * DICOMファイルを取得しないとなあ。
////					 */
////					SwingUtilities.invokeLater(new Runnable(){
////			            public void run() {
////			            	new volumstudio3d();
////			            }
////			        });
//				}
//			});
//			break;
		case "settings":
			btn.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					/* if showing, show to top, else, create new window */
					Frame[] allFrames = Frame.getFrames();
					for (Frame fr : allFrames) {
						String specificFrameName = fr.getClass().getName();
						if (specificFrameName.equals("com.vis.environment.PreferencesWin")) {
							// close the frame
							if (fr.isShowing()) {
								fr.toFront();
								return;
							}
						}
					}
//					new PreferencesWin();
				}
			});
		default:
		}
	}

	public HashMap<String, String> initButtonList() {
		String sep = File.separator;
		HashMap<String, String> map = new HashMap<>();
		map.put("delete", "/icon" + sep + "ic_delete_black_48dp.png");
		map.put("invert", "/icon" + sep + "invert_colors-48px.png");
//		map.put("export", "/icon" + sep + "ic_save_black_48dp.png");
//		map.put("browseDB", "/icon/ic_view_list_black_48dp.png");
//		map.put("burnCD", "/icon" + sep + "ic_album_black_48dp.png");
//		
//		map.put("metadata", "/icon" + sep + "ic_art_track_black_48dp.png");
//		map.put("send", "/icon" + sep + "ic_send_black_48dp.png");
//		map.put("query", "/icon" + sep + "ic_import_export_black_48dp.png");
//		map.put("2dviewer", "/icon" + sep + "ic_desktop_windows_black_48dp.png");
//		map.put("3dviewer", "/icon" + sep + "ic_3d_rotation_black_48dp.png");
//		map.put("settings", "/icon" + sep + "ic_settings_black_48dp.png");
		return map;
	}
}
