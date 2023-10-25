package com.vis.core.ui.settings;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import java.awt.GridBagConstraints;
import javax.swing.JButton;
import java.awt.Insets;
import javax.swing.JTextField;

import com.vis.core.facade.WindowManager;
import com.vis.core.util.DBUtils;
import com.vis.core.util.PropertiesUtil;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;

import java.awt.event.ActionListener;
import java.io.File;
import java.awt.event.ActionEvent;
import java.awt.Component;
import javax.swing.Box;

public class LocalDBPrefs extends JPanel{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2928122624579246835L;
	private JTextField textField;
	JCheckBox chckbxUseDefault;
	private JButton btnSelect;
	
	public LocalDBPrefs() {
		setLayout(new BorderLayout(0, 0));
		JPanel panel = new JPanel();
		add(panel, BorderLayout.NORTH);
		
		JPanel panel_1 = new JPanel();
		add(panel_1, BorderLayout.SOUTH);
		
		JPanel panel_2 = new JPanel();
		add(panel_2, BorderLayout.CENTER);
		GridBagLayout gbl_panel_2 = new GridBagLayout();
		gbl_panel_2.columnWidths = new int[]{0, 34, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		gbl_panel_2.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0};
		gbl_panel_2.columnWeights = new double[]{0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_panel_2.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		panel_2.setLayout(gbl_panel_2);
		
		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 2;
		gbc_verticalStrut.gridy = 0;
		panel_2.add(verticalStrut, gbc_verticalStrut);
		
		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 1;
		panel_2.add(horizontalStrut, gbc_horizontalStrut);
		
		JLabel lblLocalDb = new JLabel("Local DB");
		GridBagConstraints gbc_lblLocalDb = new GridBagConstraints();
		gbc_lblLocalDb.anchor = GridBagConstraints.SOUTHWEST;
		gbc_lblLocalDb.gridwidth = 5;
		gbc_lblLocalDb.insets = new Insets(0, 0, 5, 5);
		gbc_lblLocalDb.gridx = 1;
		gbc_lblLocalDb.gridy = 1;
		panel_2.add(lblLocalDb, gbc_lblLocalDb);
		
		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_1.gridx = 0;
		gbc_horizontalStrut_1.gridy = 2;
		panel_2.add(horizontalStrut_1, gbc_horizontalStrut_1);
		
		textField = new JTextField();
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.gridwidth = 11;
		gbc_textField.insets = new Insets(0, 0, 5, 5);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 2;
		panel_2.add(textField, gbc_textField);
		textField.setColumns(10);
		
		btnSelect = new JButton("Select");
		btnSelect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(chckbxUseDefault.isSelected()) {
					return;
				}
				/*show file chooser*/
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int res = chooser.showOpenDialog(WindowManager.getMainScreen());
				if(res == JFileChooser.APPROVE_OPTION) {
					File selectDir = chooser.getSelectedFile();
					String selectPath = selectDir.getAbsolutePath();
					String dbDir = DBUtils.getCurrentDBLocation();
					if(selectPath.equals(dbDir)) {
						return;
					}else {
						int res_sub = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "You will need restart GRAPHY. \nWould you change Local DB Location?");
						if(res_sub==JOptionPane.OK_OPTION) {
							PropertiesUtil.setPropertyAt("conf/graphy.properties", "LocalDBLocation", selectPath);
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							System.exit(0);
						}else {
							return;
						}
					}
				}
			}
		});
		GridBagConstraints gbc_btnSelect = new GridBagConstraints();
		gbc_btnSelect.insets = new Insets(0, 0, 5, 5);
		gbc_btnSelect.gridx = 12;
		gbc_btnSelect.gridy = 2;
		panel_2.add(btnSelect, gbc_btnSelect);
		
		Component horizontalStrut_3 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_3 = new GridBagConstraints();
		gbc_horizontalStrut_3.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_3.gridx = 13;
		gbc_horizontalStrut_3.gridy = 2;
		panel_2.add(horizontalStrut_3, gbc_horizontalStrut_3);
		
		Component horizontalStrut_2 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_2 = new GridBagConstraints();
		gbc_horizontalStrut_2.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_2.gridx = 0;
		gbc_horizontalStrut_2.gridy = 3;
		panel_2.add(horizontalStrut_2, gbc_horizontalStrut_2);
		
		chckbxUseDefault = new JCheckBox("Use default");
		GridBagConstraints gbc_chckbxUseDefault = new GridBagConstraints();
		gbc_chckbxUseDefault.anchor = GridBagConstraints.WEST;
		gbc_chckbxUseDefault.gridwidth = 4;
		gbc_chckbxUseDefault.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxUseDefault.gridx = 1;
		gbc_chckbxUseDefault.gridy = 3;
		panel_2.add(chckbxUseDefault, gbc_chckbxUseDefault);
		chckbxUseDefault.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean selected = chckbxUseDefault.isSelected();
				String defaultDBLoc = PropertiesUtil.getPropValueFrom("conf/graphy.properties", "DefaultLocalDBLocation");
				String current = DBUtils.getCurrentDBLocation();
				if(!current.equals(defaultDBLoc)) {
					/*initialize location to default*/
					if(!chckbxUseDefault.isSelected()) {
						btnSelect.setEnabled(true);
						return;
					}
					int res_sub = JOptionPane.showConfirmDialog(WindowManager.getMainScreen(), "You will need restart GRAPHY. \nWould you change Local DB Location?");
					if(res_sub==JOptionPane.OK_OPTION) {
						PropertiesUtil.setPropertyAt("conf/graphy.properties", "UseDefaultLocalDBLocation", String.valueOf(selected));
						PropertiesUtil.setPropertyAt("conf/graphy.properties", "LocalDBLocation", defaultDBLoc);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException ie) {
							// TODO Auto-generated catch block
							ie.printStackTrace();
							return;
						}
						System.exit(0);
					}else {
						return;
					}
				}
				if(!selected) {
					textField.setEditable(false);
					btnSelect.setEnabled(true);
				}else {
					textField.setEditable(false);
					btnSelect.setEnabled(false);
				}
				PropertiesUtil.setPropertyAt("conf/graphy.properties", "UseDefaultLocalDBLocation", String.valueOf(selected));
			}
		});
		
		JLabel lbluseHomegraphy = new JLabel("(use home/username/.GRAPHY)");
		GridBagConstraints gbc_lbluseHomegraphy = new GridBagConstraints();
		gbc_lbluseHomegraphy.gridwidth = 5;
		gbc_lbluseHomegraphy.insets = new Insets(0, 0, 5, 5);
		gbc_lbluseHomegraphy.gridx = 5;
		gbc_lbluseHomegraphy.gridy = 3;
		panel_2.add(lbluseHomegraphy, gbc_lbluseHomegraphy);
		
		Component horizontalStrut_4 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_4 = new GridBagConstraints();
		gbc_horizontalStrut_4.gridwidth = 2;
		gbc_horizontalStrut_4.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_4.gridx = 12;
		gbc_horizontalStrut_4.gridy = 3;
		panel_2.add(horizontalStrut_4, gbc_horizontalStrut_4);
		
		setState();
	}
	
	void setState(){
		textField.setText(DBUtils.getCurrentDBLocation());
		//load prop, check defaultLocalDB using.
		String selectedString = PropertiesUtil.getPropValueFrom("conf/graphy.properties", "UseDefaultLocalDBLocation");
		boolean selected = Boolean.parseBoolean(selectedString);
		if(selected) {
			textField.setEditable(false);
			btnSelect.setEnabled(false);
			chckbxUseDefault.setSelected(true);
		}else {
			textField.setEditable(false);
			btnSelect.setEnabled(true);
			chckbxUseDefault.setSelected(false);
		}
	}
}
