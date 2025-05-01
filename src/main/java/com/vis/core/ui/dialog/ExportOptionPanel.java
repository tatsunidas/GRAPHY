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

import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JCheckBox;

/**
 * 
 * @author tatsunidas
 *
 */
public class ExportOptionPanel extends JPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 6422530919874485231L;
	ButtonGroup folderStructureGroup;
	ButtonGroup formatGroup;
	private JCheckBox chckbxWithViewer;
	
	public ExportOptionPanel() {
		SpringLayout springLayout = new SpringLayout();
		setLayout(springLayout);
		
		setPreferredSize(new Dimension(175,235));//MUST
		
		JLabel lblNewLabel = new JLabel("Settings:");
		lblNewLabel.setFont(new Font("Dialog", Font.BOLD, 12));
		springLayout.putConstraint(SpringLayout.NORTH, lblNewLabel, 10, SpringLayout.NORTH, this);
		springLayout.putConstraint(SpringLayout.WEST, lblNewLabel, 10, SpringLayout.WEST, this);
		add(lblNewLabel);
		
		JLabel lblOutputStructure = new JLabel("Output structure");
		springLayout.putConstraint(SpringLayout.NORTH, lblOutputStructure, 6, SpringLayout.SOUTH, lblNewLabel);
		springLayout.putConstraint(SpringLayout.WEST, lblOutputStructure, 0, SpringLayout.WEST, lblNewLabel);
		add(lblOutputStructure);
		
		JRadioButton rdbtnHierarchical = new JRadioButton("Hierarchical");
		rdbtnHierarchical.setSelected(true);
		rdbtnHierarchical.setActionCommand("hierarchical");
		springLayout.putConstraint(SpringLayout.NORTH, rdbtnHierarchical, 6, SpringLayout.SOUTH, lblOutputStructure);
		springLayout.putConstraint(SpringLayout.WEST, rdbtnHierarchical, 30, SpringLayout.WEST, this);
		add(rdbtnHierarchical);
		
		JRadioButton rdbtnFlatRadioButton = new JRadioButton("Flat");
		rdbtnFlatRadioButton.setActionCommand("flat");
		springLayout.putConstraint(SpringLayout.NORTH, rdbtnFlatRadioButton, 0, SpringLayout.SOUTH, rdbtnHierarchical);
		springLayout.putConstraint(SpringLayout.WEST, rdbtnFlatRadioButton, 30, SpringLayout.WEST, this);
		add(rdbtnFlatRadioButton);
		
		folderStructureGroup = new ButtonGroup();
		folderStructureGroup.add(rdbtnHierarchical);
		folderStructureGroup.add(rdbtnFlatRadioButton);
		
		JLabel lblCompression = new JLabel("Compression");
		springLayout.putConstraint(SpringLayout.NORTH, lblCompression, 6, SpringLayout.SOUTH, rdbtnFlatRadioButton);
		springLayout.putConstraint(SpringLayout.WEST, lblCompression, 0, SpringLayout.WEST, lblNewLabel);
		add(lblCompression);
		
		JRadioButton rdbtnAsis = new JRadioButton("AS-IS");
		rdbtnAsis.setSelected(true);
		rdbtnAsis.setActionCommand("asis");
		springLayout.putConstraint(SpringLayout.NORTH, rdbtnAsis, 4, SpringLayout.SOUTH, lblCompression);
		springLayout.putConstraint(SpringLayout.WEST, rdbtnAsis, 0, SpringLayout.WEST, rdbtnHierarchical);
		add(rdbtnAsis);
		
		JRadioButton rdbtnDecompress = new JRadioButton("Decompress");
		rdbtnDecompress.setActionCommand("decompress");
		springLayout.putConstraint(SpringLayout.NORTH, rdbtnDecompress, 0, SpringLayout.SOUTH, rdbtnAsis);
		springLayout.putConstraint(SpringLayout.WEST, rdbtnDecompress, 30, SpringLayout.WEST, this);
		add(rdbtnDecompress);
		
//		JRadioButton rdbtnJpegFormat = new JRadioButton("JPEG(8bit)");//if color, RGB. if PDF AS-IS. if multifalme JPEG.
//		rdbtnJpegFormat.setActionCommand("jpeg");
//		springLayout.putConstraint(SpringLayout.NORTH, rdbtnJpegFormat, 0, SpringLayout.SOUTH, rdbtnDecompress);
//		springLayout.putConstraint(SpringLayout.WEST, rdbtnJpegFormat, 30, SpringLayout.WEST, this);
//		add(rdbtnJpegFormat);
		
		formatGroup = new ButtonGroup();
		formatGroup.add(rdbtnAsis);
		formatGroup.add(rdbtnDecompress);
		
		chckbxWithViewer = new JCheckBox("With viewer");
		chckbxWithViewer.setSelected(true);
		springLayout.putConstraint(SpringLayout.NORTH, chckbxWithViewer, 6, SpringLayout.SOUTH, rdbtnDecompress);
		springLayout.putConstraint(SpringLayout.WEST, chckbxWithViewer, 0, SpringLayout.WEST, lblNewLabel);
		add(chckbxWithViewer);
//		formatGroup.add(rdbtnJpegFormat);
	}
	
	/**
	 * [0] hierarchical or flat
	 * [1] asis or decompress
	 * 
	 * settings string array
	 * @return
	 */
	String[] getSelectedButtonsName(){
		String names[] = new String[2];
		names[0] = folderStructureGroup.getSelection().getActionCommand();
		names[1] = formatGroup.getSelection().getActionCommand();
		return names;
	}
	
	public boolean withViewer() {
		return chckbxWithViewer.isSelected();
	}
}
