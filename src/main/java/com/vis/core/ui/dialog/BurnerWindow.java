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
import com.vis.cdw.common.FileDelete;
import com.vis.cdw.common.MediaCreationException;

import java.awt.FlowLayout;
import javax.swing.JCheckBox;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

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
		new BurnerWindow(new File("graphy_tmp/DICOM-CD-TEST"), true);
	}

	/*
	 * weasis is 32MB. 
	 * how to embed weasis in cdr
	 * https://groups.google.com/g/dcm4che/c/9HIr2lyR9Os
	 * 
	 * burnFile-DICOM and DICOMDIR (without weasis)
	 */
	public BurnerWindow(File burnFileInTemp, boolean debug) {
		super("Burn to CD/DVD");

		this.simulate = debug;

		if (!burnFileInTemp.exists()) {
			JOptionPane.showConfirmDialog(null, "Could not found the burn target folder, please re-try.",
					"Something strange about burn media..?? return null", JOptionPane.WARNING_MESSAGE);
			return;
		}

		setSize(400, 400);
//		getContentPane().setPreferredSize(new Dimension(300,400));

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
		DefaultTreeModel treeModel = new DefaultTreeModel(buildTree(burnFileInTemp), false);
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
				if(anonymizer == null) {
					anonymizer = new DcmAnonymizer2();
				}else {
					anonymizer.setVisible(true);
				}
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
					anonymizer.mtranscode(burnFileInTemp, new File(burnFileInTemp.getAbsolutePath()+"_deident"));
					startBurinig(new File(burnFileInTemp.getAbsolutePath()+"_deident"), simulate);
				}else {
					startBurinig(burnFileInTemp, simulate);
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
			speed = 5;// DVD, DVDRAM,BD, smallest number...
		}
		String device = getDeviveScsi((String) comboBoxDrive.getSelectedItem());
		boolean eject = chckbxEject.isSelected();
		if (withViewer()) {
			
			try {
				FileUtils.copyDirectory(burnFileInTemp, new File("weasis-portable"));
				//DO NOT USE
//				java.nio.file.Files.copy(new File("weasis-portable").toPath(), burnFileInTemp.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				System.out.println(e);
				JOptionPane.showConfirmDialog(null, "Could not create the burn target folder, please re-try.",
						"Something wrong ..?? return null", JOptionPane.WARNING_MESSAGE);
				return;
			}
		}
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				BurnCD burner = new BurnCD(device, speed, eject, false, debug);
				if (!debug) {
					burner.setSimulate(false);
					try {
						burner.burn(burnFileInTemp, new File("graphy_tmp/" + burnFileInTemp.getName() + ".iso"));// isoRoot,
																											// iso,
																											// which iso
																											// , need
																											// end with
																											// .iso...
																											// please
																											// check. i
																											// do not
																											// have time
					} catch (MediaCreationException e) {
						e.printStackTrace();
					}
				} else {
					burner.setSimulate(true);
					try {
						burner.burn(burnFileInTemp, new File("graphy_tmp/" + burnFileInTemp.getName() + ".iso"));
					} catch (MediaCreationException e) {
						e.printStackTrace();
					}
				}
				cleanUp();
			}
		});
	}

	private void cancel() {
		
		// delete burnFileInTemp
		File[] files = new File("graphy_tmp").listFiles();
		if (files != null && files.length > 0) {
			for (File del : files) {
				new FileDelete().deleteDir(del);
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
