package com.vis.core.view.mpr;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.NumberFormat;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;

import ij.util.Tools;

@SuppressWarnings("serial")
public class MPRControlPanel extends JPanel implements ItemListener, KeyListener{
	
	//mode (Match with MPRViewerWindow viewType index.)
	final static String[] modeType = new String[] {"Orthogonal","Reslice"};
	String currentMode = modeType[0];
	//functions of Orthogonal mode
	final String crossViewMode = "Cross View Mode";
	final String showCrossLineMode = "Show Cross Lines";
	//recon mode
	final static String[] reconType = new String[] {"SLICECUT","MEAN"};
	String currentReconType = reconType[0];
	CutSurface targetSlicePlane = CutSurface.AXIAL;
	JFormattedTextField stText;//thickness, 0d >
	JFormattedTextField sgText;//gap, 0d >=
	JFormattedTextField snText;//num of slice, 1 >=
	JComboBox<String> reconSelect;//names "RECON"
	JComboBox<String> targetSlicePlaneSelect;
	JCheckBox crossViewChk;
	JCheckBox showCrossLineChk;
//	double defaultGap;//gap is not specified on xz,yz planes at initilized
	double defaultThickness;
	double currentThickness;
	double currentGap;
	int currentNumOfSlice = 1;
	
	MPRViewerWindow mprWin;
	
	public MPRControlPanel(MPRViewerWindow mprWin, double originalThickness) {
		this.mprWin = mprWin;
		defaultThickness = originalThickness;
		setContents();
	}
	
	void setContents() {
		setLayout(new BorderLayout());
		JComboBox<String> modeSelect = new JComboBox<>(modeType);
		modeSelect.setSize(100, 12);
		modeSelect.setSelectedIndex(0);
		modeSelect.setName("MODE_SELECT");
		modeSelect.addItemListener(this);
		add(modeSelect, BorderLayout.WEST);
		add(constructSettingsPanel((String)modeSelect.getSelectedItem()), BorderLayout.CENTER);
	}
	
	/**
	 * change control panel
	 * @param mode
	 * @return
	 */
	JPanel constructSettingsPanel(String mode) {
		if(mode.equals(modeType[MPRViewerWindow.CROSS_MODE])) {
			return createOrthogonalSettings();
		}else if(mode.equals(modeType[MPRViewerWindow.SLICE_MODE])) {
			return createResliceSettings();
		}else {
			return new JPanel();
		}
	}
	
	JPanel createOrthogonalSettings() {
		/*
		 * TODO interpolation mode enable: select interpolation method to reslice.
		 */
		JPanel p = new JPanel();
		FlowLayout layout = (FlowLayout) p.getLayout();
		layout.setAlignment(FlowLayout.LEFT );
		/*
		 * Enable auto slice position mouse dragging auto 
		 */
		crossViewChk = new JCheckBox(crossViewMode);
		crossViewChk.setName(crossViewMode);
		crossViewChk.setSelected(mprWin.crossViewMode);
		crossViewChk.addItemListener(this);
		p.add(crossViewChk);
		
		showCrossLineChk = new JCheckBox(showCrossLineMode);
		showCrossLineChk.setName(showCrossLineMode);
		showCrossLineChk.setSelected(mprWin.showCrossLine);
		showCrossLineChk.addItemListener(this);
		p.add(showCrossLineChk);
		return p;
	}
	
	JPanel createResliceSettings() {
		JPanel p = new JPanel();
		FlowLayout layout = (FlowLayout) p.getLayout();
		layout.setAlignment(FlowLayout.LEFT );
		
		JLabel l0 = new JLabel("SlicePlane");
		p.add(l0);
		targetSlicePlaneSelect = new JComboBox<>( new String[] {CutSurface.AXIAL.name(), CutSurface.CORONAL.name(), CutSurface.SAGITTAL.name()});
		targetSlicePlaneSelect.setName("TargetSlicePlaneSelect");
		targetSlicePlaneSelect.setSize(100, 12);
		targetSlicePlaneSelect.setSelectedIndex(0);
		targetSlicePlaneSelect.addItemListener(this);
		p.add(targetSlicePlaneSelect);
				
		JLabel l1 = new JLabel("SliceThickness");
		p.add(l1);
		if(stText == null) {
			stText = createDoubleField();
			stText.setColumns(7);
			stText.setValue(defaultThickness);
		}else {
			stText.setValue(currentThickness);
		}
		stText.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				mprWin.updateReferenceLineMPR();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {}

			@Override
			public void changedUpdate(DocumentEvent e) {
				mprWin.updateReferenceLineMPR();
			}
		});
		stText.addKeyListener(this);
		p.add(stText);
		JLabel l2 = new JLabel("Slice gap");
		p.add(l2);
		if(sgText == null) {
			sgText = createDoubleField();
			sgText.setColumns(5);
			sgText.setValue(0d);
		}else {
			sgText.setValue(currentGap);
		}
		sgText.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				mprWin.updateReferenceLineMPR();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {}

			@Override
			public void changedUpdate(DocumentEvent e) {
				mprWin.updateReferenceLineMPR();
			}
		});
		sgText.addKeyListener(this);
		p.add(sgText);
		JLabel l3 = new JLabel("Num of slices");
		p.add(l3);
		if(snText == null) {
			snText = createIntegerField();
			snText.setColumns(5);
			snText.setValue(currentNumOfSlice);
		}else {
			snText.setValue(currentNumOfSlice);
		}
		snText.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				mprWin.updateReferenceLineMPR();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {}

			@Override
			public void changedUpdate(DocumentEvent e) {
				mprWin.updateReferenceLineMPR();
			}
		});
		snText.addKeyListener(this);
		p.add(snText);
		//calc btn
		JButton resliceBtn = new JButton("Run");
		resliceBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				mprWin.updateReferenceLineMPR();
				mprWin.resliceAndShow();
			}
		});
		p.add(resliceBtn);
		
		reconSelect = new JComboBox<>(reconType);
		reconSelect.setName("RECON");
		reconSelect.setSize(100, 12);
		reconSelect.setSelectedIndex(0);
		reconSelect.addItemListener(this);
		p.add(reconSelect);
		return p;
	}
	
	JPanel createObliqueSettings() {
		return new JPanel();
	}
	
	void updateSettingsPanel(String mode) {
		if(mode.equals(currentMode)) {
			return;
		}
		BorderLayout layout = (BorderLayout)getLayout();
		remove(layout.getLayoutComponent(BorderLayout.CENTER));
		add(constructSettingsPanel(mode), BorderLayout.CENTER);
		currentMode = mode;
		currentThickness = getSliceThickness();
		currentGap = getSliceGap();
		currentNumOfSlice = getNumberOfSlices();
		mprWin.initState(Arrays.asList(modeType).indexOf(mode));
		Log.logger.fine(""+Arrays.asList(modeType).indexOf(mode));
		Log.logger.fine("ViewMode changed: "+mode);
		/*
		 * I know, revalidate and repaint were already done in updateSate.
		 * But sometimes not repainting, re-run it.
		 */
		revalidate();
		repaint();
	}
	
	void updateSliceTargetPlane() {
		mprWin.setSliceTargetPlane(targetSlicePlane);
	}
	
	public Double getSliceThickness() {
		if(stText == null) {
			return -1d;
		}
		if(stText.getText().length() == 0 || stText.getText().equals("")) {
			return -1d;
		}
		return (Double)stText.getValue();
	}
	
	public Double getSliceGap() {
		return (Double)Tools.parseDouble(sgText.getText());
	}
	
	public Integer getNumberOfSlices() {
		if(snText == null) {
			return -1;
		}
		if(snText.getText().length() == 0 || snText.getText().equals("")) {
			return -1;
		}
		return (Integer)snText.getValue();
	}
	
	String getReconType() {
		if(reconSelect == null || !currentMode.equals(modeType[1])) {
			return null;
		}
		return (String)reconSelect.getSelectedItem();
	}
	
	public CutSurface getTargetSlicePlane() {
		return targetSlicePlane;
	}
		
	public String getCurrentMode() {
		return currentMode;
	}
	
	JFormattedTextField createDoubleField() {
		NumberFormat format = NumberFormat.getInstance();
	    NumberFormatter formatter = new NumberFormatter(format);
	    formatter.setValueClass(Double.class);
//	    formatter.setMinimum(0d);
	    formatter.setMaximum(Double.MAX_VALUE);
	    formatter.setAllowsInvalid(false);
	    // If you want the value to be committed on each keystroke instead of focus lost
	    formatter.setCommitsOnValidEdit(true);
	    JFormattedTextField jtf = new JFormattedTextField(formatter);
	    jtf.setHorizontalAlignment(JTextField.RIGHT);
	    return jtf;
	}
	
	JFormattedTextField createIntegerField() {
		NumberFormat format = NumberFormat.getInstance();
	    NumberFormatter formatter = new NumberFormatter(format);
	    formatter.setValueClass(Integer.class);
//	    formatter.setMinimum(0);
	    formatter.setMaximum(Integer.MAX_VALUE);
	    formatter.setAllowsInvalid(false);
	    // If you want the value to be committed on each keystroke instead of focus lost
	    formatter.setCommitsOnValidEdit(true);
	    JFormattedTextField jtf = new JFormattedTextField(formatter);
	    jtf.setHorizontalAlignment(JTextField.RIGHT);
	    return jtf;
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		Object obj = e.getSource();
		if(obj instanceof JComboBox<?>) {
			@SuppressWarnings("unchecked")
			JComboBox<String> cb = (JComboBox<String>)obj;
			String name = cb.getName();
			if(name.equals("MODE_SELECT")) {
				updateSettingsPanel((String)cb.getSelectedItem());
			}else if(name.equals("RECON")){
				String reconType = (String)cb.getSelectedItem();
				this.currentReconType = reconType;
			}else if(name.equals("TargetSlicePlaneSelect")) {
				String plane = (String)cb.getSelectedItem();
				if(plane.equals("AXIAL")) {
					this.targetSlicePlane = CutSurface.AXIAL;
				}else if(plane.equals("CORONAL")) {
					this.targetSlicePlane = CutSurface.CORONAL;
				}else if(plane.equals("SAGITTAL")) {
					this.targetSlicePlane = CutSurface.SAGITTAL;
				}
				updateSliceTargetPlane();
			}
		}else if(obj instanceof JCheckBox) {
			JCheckBox cb = (JCheckBox)obj;
			String name = cb.getName();
			if(name.equals(crossViewMode)) {
				mprWin.crossViewModeOn(cb.isSelected());
			}else if(name.equals(showCrossLineMode)) {
				mprWin.showCrossLine(cb.isSelected());
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void keyPressed(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_ENTER){
			if(currentMode.equals(modeType[1])) {
				mprWin.updateReferenceLineMPR();
			}
        }
	}

	@Override
	public void keyReleased(KeyEvent e) {}
	
}
