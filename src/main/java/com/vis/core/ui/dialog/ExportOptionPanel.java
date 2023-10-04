package com.vis.core.ui.dialog;

import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

import java.awt.Dimension;
import java.awt.Font;

//see, ImageExporter class
public class ExportOptionPanel extends JPanel {
	/*
	 * DICOMDIRは必ず含める
	 * TODO：ROI、レポートも一緒に出力するオプション
	 * ファイルのツリーはDICOM階層かフラットかを選べるように
	 * 出力方法：ASIS、解凍
	 * 
	 * 出力方法について補足
	 * JPEGとかいろいろあるけども、
	 * 白黒なのかカラーなのか、マルチフレームなのか、
	 * 色んなパターンが考えられるので、
	 * これはDICOMを基本とするGRAPHYの役割ではないと思う。
	 * 少なくとも、このエクスポータではなく、別の画像ファイル保存で実行すべきかと。
	 * 
	 * TODO：解凍
	 * フォルダ名（スタディレベル）：患者名とする
	 * ZIP圧縮。パスワードあり。
	 * 
	 */
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 6422530919874485231L;
	ButtonGroup folderStructureGroup;
	ButtonGroup formatGroup;
	
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
//		formatGroup.add(rdbtnJpegFormat);
	}
	
	String[] getSelectedButtonsName(){
		String names[] = new String[2];
		names[0] = folderStructureGroup.getSelection().getActionCommand();
		names[1] = formatGroup.getSelection().getActionCommand();
		return names;
	}
}
