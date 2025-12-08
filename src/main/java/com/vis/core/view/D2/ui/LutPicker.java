package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;

import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings({ "serial"})
public class LutPicker extends JDialog implements WindowListener{
	
	HashMap<String,LUT> luts;
	LUT selectedLUT = null;
	JLabel selectedLUTName;
	boolean disposeFromOK = false;
	
	public LutPicker() {
		super(Viewer2DScreen.getInstance(),"LUT Picker",true);
		luts = Resources.loadAllLUT();
		setContents();
//		setVisible(true);//see, run() 
	}

	private void setContents() {
//		setSize(300, 400);
		setName(getTitle());
		setBackground(Color.black);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		JPanel base = new JPanel();
		int[] rowAndCol = calcRowAndCol();
		GridLayout gl = new GridLayout(rowAndCol[0], rowAndCol[1]);
		base.setLayout(gl);
		base.setBackground(Color.black);
		for(String type:luts.keySet()) {
			LUTCellPanel cell = new LUTCellPanel(luts.get(type),type);
			base.add(cell);
		}
		add(base,BorderLayout.CENTER);
		JPanel btnPanel = new JPanel();
		FlowLayout fl = new FlowLayout(FlowLayout.RIGHT);
		btnPanel.setLayout(fl);
		selectedLUTName = new JLabel();
		JButton ok = new JButton("OK");
		ok.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				disposeFromOK = true;
				dispose();
			}
		});
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				disposeFromOK = false;
				selectedLUT = null;
				dispose();
			}
		});
		btnPanel.add(selectedLUTName);
		btnPanel.add(ok);
		btnPanel.add(cancel);
		add(btnPanel,BorderLayout.SOUTH);
		pack();
	}
	
	private int[] calcRowAndCol() {
		int col = 5;
		int row = -1;
		if(luts.size()%col > 0) {
			row = luts.size()/col + 1;
		}else {
			row = luts.size()/col;
		}
		return new int[] {row,col};
	}
	
	public LUT run() {
		setVisible(true);
		return selectedLUT;
	}
	
	class LUTCellPanel extends JLabel implements MouseListener{
		
		final LUT lut;
		final String name;
		
		private LUTCellPanel(LUT lut, String name) {
			this.lut = lut;
			this.name = name;
//			setPreferredSize(new Dimension(50,15));
			addMouseListener(this);
			BufferedImage bi = createAndSetColorBar();
			setIcon(new ImageIcon(bi));
			setForeground(Color.LIGHT_GRAY);
			setText(name);
			setToolTipText(name);
		}
		
		private BufferedImage createAndSetColorBar() {
			//create image
			byte[] red = new byte[256];
			byte[] green = new byte[256];
			byte[] blue = new byte[256];
			lut.getReds(red);
			lut.getGreens(green);
			lut.getBlues(blue);
			ColorProcessor cp = new ColorProcessor(256,1);
			cp.setRGB(red, green, blue);
			ImagePlus imp = new ImagePlus("",cp);
			ImageProcessor ip = imp.getProcessor();
			ip.setInterpolationMethod(ImageProcessor.NONE);
			ip = ip.resize(256, 50);
			ip.setInterpolationMethod(ImageProcessor.NONE);
			ip = ip.resize(70, 20);
			imp = new ImagePlus("",ip);
			return imp.getBufferedImage();
		}
		
		/*
		 * LutCellPanel Mouse listeners
		 */
		@Override
		public void mouseClicked(MouseEvent me) {
			if(SwingUtilities.isLeftMouseButton(me)) {
				Log.logger.fine("LUT Picker: "+name + " is selected.");
				selectedLUT = this.lut;
				selectedLUTName.setText(this.name);
			}
			return;
		}

		@Override
		public void mouseEntered(MouseEvent arg0) {}

		@Override
		public void mouseExited(MouseEvent arg0) {}

		@Override
		public void mousePressed(MouseEvent arg0) {}

		@Override
		public void mouseReleased(MouseEvent arg0) {}
	}

	/*
	 * JDialog window listeners
	 */
	@Override
	public void windowActivated(WindowEvent arg0) {}

	@Override
	public void windowClosed(WindowEvent arg0) {}

	@Override
	public void windowClosing(WindowEvent arg0) {
		if(!disposeFromOK) {
			selectedLUT = null;
		}
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {}

	@Override
	public void windowDeiconified(WindowEvent arg0) {}

	@Override
	public void windowIconified(WindowEvent arg0) {}

	@Override
	public void windowOpened(WindowEvent arg0) {}	
}
