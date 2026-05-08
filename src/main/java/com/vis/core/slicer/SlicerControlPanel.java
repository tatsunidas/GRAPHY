package com.vis.core.slicer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.NumberFormat;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;

import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.dicom.image.GDicomTools;

import ij.util.Tools;

@SuppressWarnings("serial")
public class SlicerControlPanel extends JPanel implements ItemListener, KeyListener{
	
	//recon mode : See also Slicer's mode.
	final static String[] reconType = new String[] {"SLICECUT","MEAN", "MAX", "MIN", "MEDIAN","MODE"};
	String currentReconType = reconType[0];
	
	//Cut slices from.
	CutSurface targetSlicePlane = CutSurface.AXIAL;
	JFormattedTextField fovHText;//fov, height
	JFormattedTextField fovWText;//fov, width
	JFormattedTextField stText;//thickness, 0d >
	JFormattedTextField sgText;//gap, 0d >=
	JFormattedTextField snText;//num of slice, 1 >=
	JComboBox<String> reconSelect;//names "RECON"
	JComboBox<String> targetSlicePlaneSelect;
	double defaultGap = 0;
	double currentFOV_H;
	double currentFOV_W;
	double currentThickness;
	double currentGap;
	int currentNumOfSlice = 1;
	
	SlicerWindow mprWin;
	
	public SlicerControlPanel(SlicerWindow mprWin) {
		this.mprWin = mprWin;
		setContents();
	}
	
	void setContents() {
		setLayout(new BorderLayout());
		add(createResliceSettings(), BorderLayout.CENTER);
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
		
		JLabel lfov = new JLabel("FOV[mm] W & H ");
		p.add(lfov);
		
		if(fovWText == null) {
			fovWText = createDoubleField();
			fovWText.setColumns(4);
			fovWText.setValue(defaultFOV_W());
		}else {
			fovWText.setValue(currentFOV_W);
		}
		fovWText.getDocument().addDocumentListener(new DocumentListener() {
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
		fovWText.addKeyListener(this);
		p.add(fovWText);
		
		if(fovHText == null) {
			fovHText = createDoubleField();
			fovHText.setColumns(4);
			fovHText.setValue(defaultFOV_H());
		}else {
			fovHText.setValue(currentFOV_H);
		}
		fovHText.getDocument().addDocumentListener(new DocumentListener() {
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
		fovHText.addKeyListener(this);
		p.add(fovHText);
				
		JLabel l1 = new JLabel("SliceThickness");
		p.add(l1);
		if(stText == null) {
			stText = createDoubleField();
			stText.setColumns(4);
			stText.setValue(defaultThickness());
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
			sgText.setColumns(4);
			sgText.setValue(defaultGap);
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
			snText.setColumns(4);
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
	
	private double defaultFOV_H() {
		int h = mprWin.xyImage().getHeight();
		double py = mprWin.xyImage().getCalibration().pixelHeight;
		double fov_in_axi_h = PlanarSupport.truncate(h * py, 0);
		return fov_in_axi_h;
	}
	
	private double defaultFOV_W() {
		int w = mprWin.xyImage().getWidth();
		double px = mprWin.xyImage().getCalibration().pixelWidth;
		double fov_in_axi_w = PlanarSupport.truncate(w * px, 0);
		return fov_in_axi_w;
	}
	
	private double defaultThickness() {
		double pz = GDicomTools.getVoxelDepth(mprWin.xyImage());
		if(pz <= 0) {
			pz = 1;
		}
		return pz;
	}
	
	JPanel createObliqueSettings() {
		return new JPanel();
	}
	
	
	void updateSliceTargetPlane() {
		mprWin.updateReferenceLineMPR();
	}
	
	public Double getFOV() {
		if(fovHText == null || fovWText == null) {
			return Double.NaN;
		}
		if(fovHText.getText().length() == 0 || fovWText.getText().length()==0) {
			return Double.NaN;
		}
		return (Double)fovHText.getValue() * (Double)fovWText.getValue();
	}
	
	public Double getFOV_H() {
		if(fovHText == null || fovHText.getText().length() == 0) {
			return Double.NaN;
		}
		return (Double)fovHText.getValue();
	}
	
	public Double getFOV_W() {
		if(fovWText == null || fovWText.getText().length() == 0) {
			return Double.NaN;
		}
		return (Double)fovWText.getValue();
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
		return (String)reconSelect.getSelectedItem();
	}
	
	public CutSurface getTargetSlicePlane() {
		return targetSlicePlane;
	}
		
	public String getCurrentMode() { return "Reslice"; }
	
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
			if(name.equals("RECON")){
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
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_ENTER) {
			mprWin.updateReferenceLineMPR();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}
	
}
