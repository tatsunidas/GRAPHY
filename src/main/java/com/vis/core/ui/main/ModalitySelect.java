package com.vis.core.ui.main;

import javax.swing.JPanel;
import javax.swing.JCheckBox;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;

public class ModalitySelect extends JToolBar{
	
	private static final long serialVersionUID = -3182846760894276044L;
	private JCheckBox chckbxXp;
	private JCheckBox chckbxPt;
	private JCheckBox chckbxEcg;
	private JCheckBox chckbxCt;
	private JCheckBox chckbxDr;
	private JCheckBox chckbxNm;
	private JCheckBox chckbxEs;
	private JCheckBox chckbxUs;
	private JCheckBox chckbxXa;
	private JCheckBox chckbxSc;
	private JCheckBox chckbxMr;
	private JCheckBox chckbxMG;
	private JCheckBox chckbxDx;
	private JCheckBox chckbxRf;
	private JCheckBox chckbxBmd;
	private JCheckBox chckbxBdus;

	public ModalitySelect() {
		
		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);
		
		JPanel panel = new JPanel();
		scrollPane.setViewportView(panel);
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[]{43, 42, 44, 52, 0, 0, 0, 0, 0};
		gbl_panel.rowHeights = new int[]{26, 26};
		gbl_panel.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_panel.rowWeights = new double[]{0.0, 0.0};
		panel.setLayout(gbl_panel);
		
		chckbxMr = new JCheckBox("MR");
		GridBagConstraints gbc_chckbxMr = new GridBagConstraints();
		gbc_chckbxMr.anchor = GridBagConstraints.WEST;
		gbc_chckbxMr.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxMr.gridx = 0;
		gbc_chckbxMr.gridy = 0;
		panel.add(chckbxMr, gbc_chckbxMr);
		
		chckbxXp = new JCheckBox("CR");
		GridBagConstraints gbc_chckbxXp = new GridBagConstraints();
		gbc_chckbxXp.anchor = GridBagConstraints.WEST;
		gbc_chckbxXp.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxXp.gridx = 1;
		gbc_chckbxXp.gridy = 0;
		panel.add(chckbxXp, gbc_chckbxXp);
		
		chckbxUs = new JCheckBox("US");
		GridBagConstraints gbc_chckbxUs = new GridBagConstraints();
		gbc_chckbxUs.anchor = GridBagConstraints.WEST;
		gbc_chckbxUs.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxUs.gridx = 2;
		gbc_chckbxUs.gridy = 0;
		panel.add(chckbxUs, gbc_chckbxUs);
		
		chckbxSc = new JCheckBox("OT");
		GridBagConstraints gbc_chckbxSc = new GridBagConstraints();
		gbc_chckbxSc.anchor = GridBagConstraints.WEST;
		gbc_chckbxSc.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxSc.gridx = 3;
		gbc_chckbxSc.gridy = 0;
		panel.add(chckbxSc, gbc_chckbxSc);
		
		chckbxDx = new JCheckBox("DX");
		GridBagConstraints gbc_chckbxDx = new GridBagConstraints();
		gbc_chckbxDx.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxDx.anchor = GridBagConstraints.WEST;
		gbc_chckbxDx.gridx = 4;
		gbc_chckbxDx.gridy = 0;
		panel.add(chckbxDx, gbc_chckbxDx);
		
		chckbxBmd = new JCheckBox("BMD");
		GridBagConstraints gbc_chckbxBmd = new GridBagConstraints();
		gbc_chckbxBmd.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxBmd.anchor = GridBagConstraints.WEST;
		gbc_chckbxBmd.gridx = 5;
		gbc_chckbxBmd.gridy = 0;
		panel.add(chckbxBmd, gbc_chckbxBmd);
		
		chckbxPt = new JCheckBox("PT");
		GridBagConstraints gbc_chckbxPt = new GridBagConstraints();
		gbc_chckbxPt.anchor = GridBagConstraints.WEST;
		gbc_chckbxPt.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxPt.gridx = 6;
		gbc_chckbxPt.gridy = 0;
		panel.add(chckbxPt, gbc_chckbxPt);
		
		chckbxBdus = new JCheckBox("BDUS");
		GridBagConstraints gbc_chckbxBdus = new GridBagConstraints();
		gbc_chckbxBdus.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxBdus.anchor = GridBagConstraints.WEST;
		gbc_chckbxBdus.gridx = 7;
		gbc_chckbxBdus.gridy = 0;
		panel.add(chckbxBdus, gbc_chckbxBdus);
		
		chckbxCt = new JCheckBox("CT");
		GridBagConstraints gbc_chckbxCt = new GridBagConstraints();
		gbc_chckbxCt.anchor = GridBagConstraints.WEST;
		gbc_chckbxCt.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxCt.gridx = 0;
		gbc_chckbxCt.gridy = 1;
		panel.add(chckbxCt, gbc_chckbxCt);
		
		chckbxDr = new JCheckBox("DR");
		GridBagConstraints gbc_chckbxDr = new GridBagConstraints();
		gbc_chckbxDr.anchor = GridBagConstraints.WEST;
		gbc_chckbxDr.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxDr.gridx = 1;
		gbc_chckbxDr.gridy = 1;
		panel.add(chckbxDr, gbc_chckbxDr);
		
		chckbxNm = new JCheckBox("NM");
		GridBagConstraints gbc_chckbxNm = new GridBagConstraints();
		gbc_chckbxNm.anchor = GridBagConstraints.WEST;
		gbc_chckbxNm.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxNm.gridx = 2;
		gbc_chckbxNm.gridy = 1;
		panel.add(chckbxNm, gbc_chckbxNm);
		
		chckbxXa = new JCheckBox("XA");
		GridBagConstraints gbc_chckbxXa = new GridBagConstraints();
		gbc_chckbxXa.anchor = GridBagConstraints.WEST;
		gbc_chckbxXa.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxXa.gridx = 3;
		gbc_chckbxXa.gridy = 1;
		panel.add(chckbxXa, gbc_chckbxXa);
		
		chckbxMG = new JCheckBox("MG");
		GridBagConstraints gbc_chckbxMG = new GridBagConstraints();
		gbc_chckbxMG.anchor = GridBagConstraints.WEST;
		gbc_chckbxMG.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxMG.gridx = 4;
		gbc_chckbxMG.gridy = 1;
		panel.add(chckbxMG, gbc_chckbxMG);
		
		chckbxEcg = new JCheckBox("ECG");
		GridBagConstraints gbc_chckbxEcg = new GridBagConstraints();
		gbc_chckbxEcg.anchor = GridBagConstraints.WEST;
		gbc_chckbxEcg.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxEcg.gridx = 5;
		gbc_chckbxEcg.gridy = 1;
		panel.add(chckbxEcg, gbc_chckbxEcg);
		
		chckbxEs = new JCheckBox("ES");
		GridBagConstraints gbc_chckbxEs = new GridBagConstraints();
		gbc_chckbxEs.anchor = GridBagConstraints.WEST;
		gbc_chckbxEs.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxEs.gridx = 6;
		gbc_chckbxEs.gridy = 1;
		panel.add(chckbxEs, gbc_chckbxEs);
		
		chckbxRf = new JCheckBox("RF");
		GridBagConstraints gbc_chckbxRf = new GridBagConstraints();
		gbc_chckbxRf.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxRf.anchor = GridBagConstraints.WEST;
		gbc_chckbxRf.gridx = 7;
		gbc_chckbxRf.gridy = 1;
		panel.add(chckbxRf, gbc_chckbxRf);
		
	}
	
	public ArrayList<String> selectedModalities(){
		ArrayList<String> selected = new ArrayList<String>();
		selected.add(chckbxMr.isSelected() == true ? chckbxMr.getText():null);
		selected.add(chckbxCt.isSelected() == true ? chckbxCt.getText():null);
		selected.add(chckbxDr.isSelected() == true ? chckbxDr.getText():null);
		selected.add(chckbxEcg.isSelected() == true ? chckbxEcg.getText():null);
		selected.add(chckbxEs.isSelected() == true ? chckbxEs.getText():null);
		selected.add(chckbxNm.isSelected() == true ? chckbxNm.getText():null);
		selected.add(chckbxPt.isSelected() == true ? chckbxPt.getText():null);
		selected.add(chckbxSc.isSelected() == true ? chckbxSc.getText():null);
		selected.add(chckbxUs.isSelected() == true ? chckbxUs.getText():null);
		selected.add(chckbxXa.isSelected() == true ? chckbxXa.getText():null);
		selected.add(chckbxXp.isSelected() == true ? chckbxXp.getText():null);
		selected.add(chckbxMG.isSelected() == true ? chckbxMG.getText():null);
		selected.add(chckbxDx.isSelected() == true ? chckbxDx.getText():null);
		selected.add(chckbxRf.isSelected() == true ? chckbxRf.getText():null);
		selected.add(chckbxBmd.isSelected() == true ? chckbxBmd.getText():null);
		selected.add(chckbxBdus.isSelected() == true ? chckbxBdus.getText():null);
		
		selected.removeAll(Collections.singleton(null));
		return selected;
	}
}
