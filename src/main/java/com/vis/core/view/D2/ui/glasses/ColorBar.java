package com.vis.core.view.D2.ui.glasses;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import com.vis.core.view.D2.ui.LutPicker;

import ij.process.LUT;

@SuppressWarnings("serial")
public class ColorBar extends JLabel implements ComponentListener{
	
	private Praparat pp;
	private float[] steps;
	private Color[] colors;
	
	public ColorBar(Praparat pp, int w, int h) {
		super();
		this.pp = pp;
		setPreferredSize(new Dimension(w,h));//MUST
		setOpaque(true);
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if(praparat().getCurrentSlide().isRGB) {
					JOptionPane.showConfirmDialog(pp, "Cannot apply LUT to RGB images.", "LUT warning...", JOptionPane.OK_OPTION);
					return;
				}
				LutPicker p = new LutPicker();
				p.setLocationRelativeTo(praparat());
				Object[] lutAndName = p.run();
				praparat().setLUT(lutAndName[0]==null? null:(LUT)lutAndName[0], (String)lutAndName[1]);
			}
		});
		setLUT(null);
		addComponentListener(this);
	}
	
	public void setLUT(LUT lut) {
		if (lut == null) {
			// set default
			setColor(null, null);
		}else {
			steps = constructSteps();//0 to 1 range
			colors = new Color[256];
			byte[] red = new byte[256];
			byte[] green = new byte[256];
			byte[] blue = new byte[256];
			lut.getReds(red);
			lut.getGreens(green);
			lut.getBlues(blue);
			for(int i=0;i<256;i++) {
				int r = red[i] & 0xFF;
				int g = green[i] & 0xFF;
				int b = blue[i] & 0xFF;
				Color c = new Color(r, g, b);
				colors[i] = c;
			}
			setColor(steps, colors);
		}
	}
	
	/*
	 * Each length of steps and colors must be same.
	 */
	/*
     * Set to Point() for bar direction
     * For diagonal strokes both X and Y coords change
     * For horizontal strokes just X changes//new Point(0, getHeight()), 
     * For vertical strokes only Y changes//new Point(getWidth(), 0), 
     */
//  new float[]{0.142f, 0.284f, 0.426f, 0.568f, 0.71f, 0.852f, 1f}, 
//  new Color[]{Color.PINK, Color.MAGENTA, Color.BLUE, Color.GREEN, Color.YELLOW, Color.ORANGE, Color.RED}
	private void setColor(float[] steps, Color[] colors) {
		if (steps == null || colors == null) {
			// set default
			this.steps =  new float[] { 0f, 1f };
			this.colors = new Color[] { Color.BLACK, Color.WHITE };
		}else {
			this.steps = steps;
			this.colors = colors;
		}
		repaint();
	}
	
	//steps is 0 to 1
	private float[] constructSteps() {
		float[] steps = new float[256];
		float interval = 1f/256f;
		for(int i=0;i<256;i++) {
			if(i == 255) {
				steps[i] = 1f;
				break;
			}
			steps[i] = interval * i;
		}
		return steps;
	}
	
	private Praparat praparat() {
		return pp;
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g.create();
		LinearGradientPaint lgp = new LinearGradientPaint(
				new Point(0, 0), 
				new Point(getWidth(), 0), 
				this.steps, 
				this.colors
		);
		g2d.setPaint(lgp);
		g2d.fill(new Rectangle(0, 0, getWidth(), getHeight()));
	}

	@Override
	public void componentResized(ComponentEvent e) {
		int pw = getParent().getWidth();
		setPreferredSize(new Dimension(pw, this.getHeight()));
		setSize(new Dimension(pw, this.getHeight()));
		repaint();
	}

	@Override
	public void componentMoved(ComponentEvent e) {}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void componentHidden(ComponentEvent e) {}
}
