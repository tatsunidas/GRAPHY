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
package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JComboBox;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.apache.commons.io.FileUtils;

import com.vis.cdw.cdrecord.BurnCD;
import com.vis.cdw.common.CDRToolsProperties;
import com.vis.cdw.common.DriveUtil;
import com.vis.cdw.common.MediaCreationException;
import com.vis.configuration.ConfigInfo;
import com.vis.core.log.Log;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomUtilities;

import java.awt.FlowLayout;
import javax.swing.JCheckBox;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * 
 * @author tatsunidas
 *
 */

@SuppressWarnings("serial")
public class BurnerWindow extends JFrame implements WindowListener{

	private JComboBox<String> comboBoxDrive;
	private JTree treeBurnList;
	private JCheckBox chckbxViewer;
	private JCheckBox chckbxEject;
	private JComboBox<Integer> comboBoxSpeed;
	private JButton btnAnonymize;

	private boolean simulate = false;
	private int speed;
	
	private DcmAnonymizer2 anonymizer = null;

	// debug
	public static void main(String[] args) {
		//new BurnerWindow(new File("temp/DICOM-CD-TEST"), true);
		
		//resource copy test
		
		
	}

	/*
	 * weasis is 32MB. 
	 * how to embed weasis in cdr
	 * https://groups.google.com/g/dcm4che/c/9HIr2lyR9Os
	 * 
	 * burnFile-DICOM and DICOMDIR and weasis-portable's files
	 */
	public BurnerWindow(File burnDestDirInTemp, boolean debug) {
		super("Burn to CD/DVD");

		this.simulate = debug;

		if (!burnDestDirInTemp.exists()) {
			JOptionPane.showConfirmDialog(MainScreen.getInstance(), "Could not find the burn target folder, please re-try.",
					"Something strange about burn target media..?? return", JOptionPane.WARNING_MESSAGE);
			return;
		}

		setSize(400, 400);

		JPanel panelSelectDrive = new JPanel();
		getContentPane().add(panelSelectDrive, BorderLayout.NORTH);
		panelSelectDrive.setLayout(new BorderLayout(0, 0));

		JLabel lblDrive = new JLabel("Select Drive");
		panelSelectDrive.add(lblDrive, BorderLayout.NORTH);

		String[] drives = new DriveUtil().grubAliveDevicesByNickName();
		if (drives == null || drives.length < 1) {
			JOptionPane.showConfirmDialog(null, "GRAPHY can not detect any drive devices, please check device.");
			return;
		}
		comboBoxDrive = new JComboBox<String>(new DriveUtil().grubAliveDevicesByNickName());
		panelSelectDrive.add(comboBoxDrive, BorderLayout.SOUTH);

		JScrollPane scrollPaneBurnList = new JScrollPane();
		getContentPane().add(scrollPaneBurnList, BorderLayout.CENTER);

		treeBurnList = new JTree();
		DefaultTreeModel treeModel = new DefaultTreeModel(buildTree(burnDestDirInTemp), false);
		treeBurnList.setModel(treeModel);
		for (int i = 0; i < treeBurnList.getRowCount(); i++) {
		    treeBurnList.expandRow(i);
		}
		scrollPaneBurnList.setViewportView(treeBurnList);

		JPanel panelSettings = new JPanel();
		getContentPane().add(panelSettings, BorderLayout.EAST);
		GridBagLayout gbl_panelSettings = new GridBagLayout();
		gbl_panelSettings.columnWidths = new int[] { 83, 0 };
		gbl_panelSettings.rowHeights = new int[] { 21, 21, 0, 0, 0, 0, 0, 0, 0, 0 };
		gbl_panelSettings.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
		gbl_panelSettings.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		panelSettings.setLayout(gbl_panelSettings);

		chckbxViewer = new JCheckBox("with viewer");
		chckbxViewer.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_chckbxViewer = new GridBagConstraints();
		gbc_chckbxViewer.anchor = GridBagConstraints.WEST;
		gbc_chckbxViewer.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxViewer.gridx = 0;
		gbc_chckbxViewer.gridy = 0;
		panelSettings.add(chckbxViewer, gbc_chckbxViewer);

		chckbxEject = new JCheckBox("auto eject");
		GridBagConstraints gbc_chckbxEject = new GridBagConstraints();
		gbc_chckbxEject.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxEject.anchor = GridBagConstraints.WEST;
		gbc_chckbxEject.gridx = 0;
		gbc_chckbxEject.gridy = 1;
		panelSettings.add(chckbxEject, gbc_chckbxEject);

		JLabel lblSpeed = new JLabel(" Speed");
		GridBagConstraints gbc_lblSpeed = new GridBagConstraints();
		gbc_lblSpeed.anchor = GridBagConstraints.WEST;
		gbc_lblSpeed.insets = new Insets(0, 0, 5, 0);
		gbc_lblSpeed.gridx = 0;
		gbc_lblSpeed.gridy = 2;
		panelSettings.add(lblSpeed, gbc_lblSpeed);

		comboBoxSpeed = new JComboBox<Integer>(new Integer[] { 1, 2, 4, 5, 8, 12, 16, 24 });
		GridBagConstraints gbc_comboBoxSpeed = new GridBagConstraints();
		gbc_comboBoxSpeed.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBoxSpeed.insets = new Insets(0, 0, 5, 0);
		gbc_comboBoxSpeed.gridx = 0;
		gbc_comboBoxSpeed.gridy = 3;
		panelSettings.add(comboBoxSpeed, gbc_comboBoxSpeed);
		
		btnAnonymize = new JButton("Anonymize");
		GridBagConstraints gbc_btnAnonymize = new GridBagConstraints();
		gbc_btnAnonymize.insets = new Insets(0, 0, 5, 0);
		gbc_btnAnonymize.anchor = GridBagConstraints.WEST;
		gbc_btnAnonymize.gridx = 0;
		gbc_btnAnonymize.gridy = 4;
		panelSettings.add(btnAnonymize, gbc_btnAnonymize);
		btnAnonymize.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
//				if(anonymizer == null) {
//					anonymizer = new DcmAnonymizer2();
//				}else {
//					anonymizer.setVisible(true);
//				}
				JOptionPane.showConfirmDialog(null, "Anonymize function is under development...");
			}
		});

		JPanel panelOKCancel = new JPanel();
		FlowLayout flowLayout = (FlowLayout) panelOKCancel.getLayout();
		flowLayout.setAlignment(FlowLayout.RIGHT);
		getContentPane().add(panelOKCancel, BorderLayout.SOUTH);

		JButton btnOK = new JButton("OK");
		btnOK.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// BURN
				if(anonymizer != null && anonymizer.isProceeded()) {
					anonymizer.mtranscode(burnDestDirInTemp, new File(burnDestDirInTemp.getAbsolutePath()+"_deident"));
					startBurinig(new File(burnDestDirInTemp.getAbsolutePath()+"_deident"), simulate);
				}else {
					startBurinig(burnDestDirInTemp, simulate);
				}
			}
		});
		panelOKCancel.add(btnOK);

		JButton btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// close window and delete temp files
				cancel();
			}
		});
		btnCancel.setHorizontalAlignment(SwingConstants.RIGHT);
		panelOKCancel.add(btnCancel);

		loadSettings();

		java.awt.Dimension dim = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		setLocation(dim.width / 2 - this.getSize().width / 2, dim.height / 2 - this.getSize().height / 2);
		setVisible(true);
	}

	private TreeNode buildTree(File burnFileInTemp) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("DICOM");
		File dcmFolder = new File(burnFileInTemp.getAbsolutePath() + File.separator + "DICOM");
		if (!dcmFolder.exists()) {
			return null;
		}
		File[] plist = dcmFolder.listFiles();
		if (plist != null) {
			for (File patDir : plist) {
				DefaultMutableTreeNode patNode = new DefaultMutableTreeNode(patDir.getName());
				root.add(patNode);
				File[] studyList = patDir.listFiles();
				if (studyList != null) {
					for (File study : studyList) {
						DefaultMutableTreeNode studyNode = new DefaultMutableTreeNode(study.getName());
						patNode.add(studyNode);
						File[] seriesList = study.listFiles();
						if (seriesList != null) {
							for (File series : seriesList) {
								DefaultMutableTreeNode seriesNode = new DefaultMutableTreeNode(series.getName());
								studyNode.add(seriesNode);
								File[] imageList = series.listFiles();
								if (imageList != null) {
									for (File image : imageList) {
										seriesNode.add(new DefaultMutableTreeNode(image.getName()));
									}
								}
							}
						}
					}
				}
			}
		}
		return root;
	}

	private void loadSettings() {
		// set eject
		setCurrentEjectState();
		// set with viewer
		setCurrentWithViewerState();
		// set speed
		setCurrentSpeed2Combo();
	}

	// write speed
	private void setCurrentSpeed2Combo() {
		Integer speed = CDRToolsProperties.loadBurnSpeed();
		comboBoxSpeed.setSelectedItem(speed);
		comboBoxSpeed.revalidate();
	}

	private void setCurrentEjectState() {
		if (CDRToolsProperties.loadEjectAfterBurn()) {
			chckbxEject.setSelected(true);
		} else {
			chckbxEject.setSelected(false);
		}
		chckbxEject.revalidate();
	}

	private void setCurrentWithViewerState() {
		if (CDRToolsProperties.loadWithViewer()) {
			chckbxViewer.setSelected(true);
		} else {
			chckbxViewer.setSelected(false);
		}
		chckbxViewer.revalidate();
	}

	private boolean withViewer() {
		return chckbxViewer.isSelected();
	}

	private String getDeviveScsi(String nickname) {
		return nickname.substring(nickname.lastIndexOf("_") + 1);
	}

	/*
	 * this is not good idea, TODO
	 */
	private boolean isCD(String nickname) {
		return nickname.contains("CD");
	}

	private void startBurinig(File burnFileInTemp, boolean debug) {
		speed = (Integer) comboBoxSpeed.getSelectedItem();
		if (!isCD((String) comboBoxDrive.getSelectedItem())) {
			// see, BurnCD::setWriteSpeed().
			Log.logger.info("WriteSpeed changed to 8X for wrinting Non CD-R media.");
			speed = 8;// DVD, DVDRAM,BD, fastest number...
		}
		String device = getDeviveScsi((String) comboBoxDrive.getSelectedItem());
		boolean eject = chckbxEject.isSelected();
		if (withViewer()) {
			try {
				DicomUtilities.attachDICOMDIRTo(burnFileInTemp.getAbsolutePath());
				Utils.copyDirectory(ConfigInfo.WEASIS.toString(), burnFileInTemp.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				BurnCD burner = new BurnCD(device, speed, eject, false);
				burner.setSimulate(false);
				try {
					burner.burn(burnFileInTemp, new File(burnFileInTemp.getAbsolutePath() + ".iso"));
				} catch (MediaCreationException e) {
					e.printStackTrace();
				}
				cleanUp();
			}
		});
	}

	private void cancel() {
		// delete burnFileInTemp
		File[] files = new File(ConfigInfo.getPath(ConfigInfo.TemporalDirName)).listFiles();
		if (files != null && files.length > 0) {
			for (File del : files) {
				try {
					FileUtils.deleteDirectory(del);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if(anonymizer != null) {
			anonymizer.dispose();
		}
		dispose();
	}

	private void cleanUp() {
		// update state
		CDRToolsProperties.setPropertiesAndSave("SPEED", String.valueOf((int) comboBoxSpeed.getSelectedItem()));
		CDRToolsProperties.setPropertiesAndSave("EJECT", String.valueOf(chckbxEject.isSelected() ? 1 : 0));
		CDRToolsProperties.setPropertiesAndSave("WITH_VIEWER", String.valueOf(chckbxViewer.isSelected() ? 1 : 0));
		cancel();// clean up tmp dir and dispose
	}

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowClosing(WindowEvent e) {
		cleanUp();
	}

	@Override
	public void windowClosed(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}
}
