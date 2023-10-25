package com.vis.core.ui.settings;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

import javax.swing.JPanel;
import java.awt.GridBagLayout;
import javax.swing.JCheckBox;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.awt.Insets;
import javax.swing.SwingConstants;

import com.vis.configuration.ConfigInfo;
import com.vis.configuration.GraphyProp;
import com.vis.core.facade.ApplicationFacade;
import com.vis.core.facade.WindowManager;
import com.vis.core.ui.LookAndFeels;
import com.vis.core.ui.main.dcmtreetable.TreeTableDockManager;
import com.vis.core.util.PropertiesUtil;

import java.awt.Component;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class GeneralPrefs extends JPanel{
	
	/**
	 * TODO 20231008 needed entire codes checking.
	 */
	private static final long serialVersionUID = 5828583604993199622L;
	
	LookAndFeels laf;
	int currentTextSize = 12;
	String currentLAF = LookAndFeels.defaultLAF;
	JPanel genPrefPanel = this;
	public boolean refreshOn = true;

	private DefaultComboBoxModel<String> lafmodel;

	private JComboBox<String> comboBoxLAF;

	private JCheckBox chckbxNewCheckBox; 

	public GeneralPrefs() {
		
		laf = ApplicationFacade.getCurrentLookAndFeels();
		currentLAF = laf.getCurrentLAF();
		String fontSizeString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.FontSize);
		if(fontSizeString != null && fontSizeString.length() !=0) {
			try {
				currentTextSize = Integer.parseInt(fontSizeString);
			}catch(NumberFormatException e) {
				// do nothing
			}
		}
		
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		Component verticalStrut = Box.createVerticalStrut(20);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 1;
		gbc_verticalStrut.gridy = 0;
		add(verticalStrut, gbc_verticalStrut);
		
		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut.gridx = 0;
		gbc_horizontalStrut.gridy = 1;
		add(horizontalStrut, gbc_horizontalStrut);
		
		JLabel lblGeneral = new JLabel("General");
		GridBagConstraints gbc_lblGeneral = new GridBagConstraints();
		gbc_lblGeneral.anchor = GridBagConstraints.WEST;
		gbc_lblGeneral.insets = new Insets(0, 0, 5, 5);
		gbc_lblGeneral.gridx = 1;
		gbc_lblGeneral.gridy = 1;
		add(lblGeneral, gbc_lblGeneral);
		Integer[] fontSizes = { 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 48, 64 };
		DefaultComboBoxModel<Integer> fontCombModel = new DefaultComboBoxModel<>(fontSizes);
		
		Component horizontalStrut_2 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_2 = new GridBagConstraints();
		gbc_horizontalStrut_2.gridwidth = 7;
		gbc_horizontalStrut_2.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_2.gridx = 2;
		gbc_horizontalStrut_2.gridy = 1;
		add(horizontalStrut_2, gbc_horizontalStrut_2);
		
		Component horizontalGlue = Box.createHorizontalGlue();
		GridBagConstraints gbc_horizontalGlue = new GridBagConstraints();
		gbc_horizontalGlue.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalGlue.gridx = 0;
		gbc_horizontalGlue.gridy = 2;
		add(horizontalGlue, gbc_horizontalGlue);
		
		JLabel lblFontSize = new JLabel("Set Font Size");
		lblFontSize.setHorizontalAlignment(SwingConstants.LEFT);
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel.gridx = 1;
		gbc_lblNewLabel.gridy = 2;
		add(lblFontSize, gbc_lblNewLabel);
		
		lafmodel = new DefaultComboBoxModel<String>();
		ArrayList<String> names = laf.getInstalledLAF();
		for(String laf_name:names) {
			lafmodel.addElement(laf_name);
		}
//		/* Get current LAF and set item location */
//		String currentLAF = ApplicationContext.activeTheme;
//		for(String key:LookAndFeels.lafmap.keySet()) {
//			if(currentLAF.contains(key)) {
//				int pos = lafmodel.getIndexOf(key);
//				if(pos != -1) {
//					comboBoxLAF.setSelectedIndex(pos);
//				}
//			}
//		}
		/*
		 * SHOULD be add listener AFTER comboBoxLAF.setSelectedIndex.
		 */
		
		setMinimumSize(new Dimension(150,100));
		
		JComboBox<Integer> comboBoxFontSize = new JComboBox<>();
		GridBagConstraints gbc_comboBox = new GridBagConstraints();
		gbc_comboBox.insets = new Insets(0, 0, 5, 5);
		gbc_comboBox.anchor = GridBagConstraints.WEST;
		gbc_comboBox.gridx = 2;
		gbc_comboBox.gridy = 2;
		add(comboBoxFontSize, gbc_comboBox);
		comboBoxFontSize.setModel((ComboBoxModel<Integer>) fontCombModel);
		comboBoxFontSize.setSelectedItem(currentTextSize);
		comboBoxFontSize.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent ie) {
				 Font f = new Font(Font.SANS_SERIF,Font.PLAIN,(Integer)ie.getItem());//name style size
				 com.vis.core.ui.FontSettings.changeFontAllComps(f);
			}
		});
		
		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_1.gridwidth = 4;
		gbc_horizontalStrut_1.gridx = 5;
		gbc_horizontalStrut_1.gridy = 2;
		add(horizontalStrut_1, gbc_horizontalStrut_1);
		
		Component horizontalStrut_3 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_3 = new GridBagConstraints();
		gbc_horizontalStrut_3.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_3.gridx = 0;
		gbc_horizontalStrut_3.gridy = 3;
		add(horizontalStrut_3, gbc_horizontalStrut_3);
		
		JLabel lblLAF = new JLabel("Look and Feels");
		GridBagConstraints gbc_lblNewLabel_1 = new GridBagConstraints();
		gbc_lblNewLabel_1.anchor = GridBagConstraints.WEST;
		gbc_lblNewLabel_1.insets = new Insets(0, 0, 5, 5);
		gbc_lblNewLabel_1.gridx = 1;
		gbc_lblNewLabel_1.gridy = 3;
		add(lblLAF, gbc_lblNewLabel_1);
		
		comboBoxLAF = new JComboBox<>();
		GridBagConstraints gbc_comboBox_1 = new GridBagConstraints();
		gbc_comboBox_1.insets = new Insets(0, 0, 5, 5);
		gbc_comboBox_1.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBox_1.gridx = 2;
		gbc_comboBox_1.gridy = 3;
		add(comboBoxLAF, gbc_comboBox_1);
		comboBoxLAF.setModel(lafmodel);
		comboBoxLAF.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				String select = (String) e.getItem();
				laf.setLookAndFeel(laf.getInstalledLAFMap().get(select));
				WindowManager.getMainScreen().refreshLookAndFeels();
				PreferencesWin.refreshOwnLookAndFeels();
			}
		});
		
		JButton btnBackToDefault = new JButton("back to default");
		btnBackToDefault.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				laf.setDefaultTheme();
				/* update all component */
				laf.updateLookAndFeels(WindowManager.getMainScreen());
				/* own component reflech here */
				PreferencesWin.refreshOwnLookAndFeels();
			}
		});
		GridBagConstraints gbc_btnBackToDefault = new GridBagConstraints();
		gbc_btnBackToDefault.insets = new Insets(0, 0, 5, 5);
		gbc_btnBackToDefault.gridx = 4;
		gbc_btnBackToDefault.gridy = 3;
		add(btnBackToDefault, gbc_btnBackToDefault);
		
		Component horizontalStrut_4 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_4 = new GridBagConstraints();
		gbc_horizontalStrut_4.insets = new Insets(0, 0, 5, 0);
		gbc_horizontalStrut_4.gridwidth = 3;
		gbc_horizontalStrut_4.gridx = 6;
		gbc_horizontalStrut_4.gridy = 3;
		add(horizontalStrut_4, gbc_horizontalStrut_4);
		
		chckbxNewCheckBox = new JCheckBox("Refresh QR Table (every 20 sec)");
		String refreshOnString = PropertiesUtil.getPropValueFrom(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn);
		refreshOn = false;
		if(refreshOnString !=null) {
			refreshOn = Boolean.parseBoolean(refreshOnString);
		}
		chckbxNewCheckBox.setSelected(refreshOn);
		GridBagConstraints gbc_chckbxNewCheckBox = new GridBagConstraints();
		gbc_chckbxNewCheckBox.anchor = GridBagConstraints.WEST;
		gbc_chckbxNewCheckBox.gridwidth = 4;
		gbc_chckbxNewCheckBox.insets = new Insets(0, 0, 0, 5);
		gbc_chckbxNewCheckBox.gridx = 1;
		gbc_chckbxNewCheckBox.gridy = 4;
		add(chckbxNewCheckBox, gbc_chckbxNewCheckBox);
		chckbxNewCheckBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn, "true");
					WindowManager.getMainScreen().qrAutoRefreshOn = true;
					TreeTableDockManager dttm = WindowManager.getMainScreen().getCurrentTreeTableManager();
					dttm.setAndStartRefreshQRTableTimer();
				} else {// checkbox has been deselected
					PropertiesUtil.setPropertyAt(ConfigInfo.GRAPHY_Props, GraphyProp.RefreshQRTreeTableOn, "false");
					WindowManager.getMainScreen().qrAutoRefreshOn = false;
					TreeTableDockManager dttm = WindowManager.getMainScreen().getCurrentTreeTableManager();
					dttm.stopRefreshQRTableTimer();
				}
			}
		});
		
		initSetting();
	}
	
	void initSetting(){
		/* Get current LAF and set item location */
		String currentLAF = laf.getCurrentLAF();
		for(String key:laf.getInstalledLAFMap().keySet()) {
			if(currentLAF.contains(key)) {
				int pos = lafmodel.getIndexOf(key);
				if(pos != -1) {
					comboBoxLAF.setSelectedIndex(pos);
				}
			}
		}
	}
}
