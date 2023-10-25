package com.vis.core.view.D2.ui.glasses;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

import ij.ImagePlus;

@SuppressWarnings("serial")
public class ImageSpecimenGlass extends JPanel{
	
	/**
	 * This class shows image on slideglass.
	 * Keep simple.
	 */
	
	int originX;
	int originY;
	ImagePlus displayImg;
	boolean transparent = true;
	float alpha = 1.0f;
	
	//fusion settings
	boolean composite = false;
	float compositeAlpha = 0.0f;
	
	public ImageSpecimenGlass() {
		setOpaque(false);
	}
	
	/*
	 * without component(prapview) scale
	 */
	public void updateImage(int originX, int originY, ImagePlus displayImp) {
		this.displayImg = displayImp;
		this.originX = originX;
		this.originY = originY;
		repaint();//call paintComponent() and show img.
	}
	
	/*
	 * with component(prapview) scale
	 */
	public void updateImage(int originX, int originY, double scale, ImagePlus displayImp) {
		this.displayImg = displayImp;
		this.originX = (int)((double)originX * scale);
		this.originY = (int)((double)originY * scale);
		repaint();//call paintComponent() and show img.
	}
	
	@Override
	protected void paintComponent(Graphics g) {
	    Graphics2D g2d = (Graphics2D) g.create();
	    if(transparent) {
	    	g2d.setComposite(AlphaComposite.getInstance(
		            AlphaComposite.SRC_OVER, alpha));
		    g2d.drawImage(displayImg.getImage(), originX, originY, this);
	    }else {
	    	g2d.drawImage(displayImg.getImage(), originX, originY, this);
	    }
	    
	    if(composite) {
		    Dimension dim = getPreferredSize();
		    int w = dim.width;
		    int h = dim.height;
		    g2d.setComposite(AlphaComposite.getInstance(
		            AlphaComposite.SRC_OVER, compositeAlpha));
//		    g2d.setPaint(new GradientPaint(0, 0, Color.yellow, 0, h, Color.red));//example
//		    g2d.drawImage(displayImg.getImage(), originX, originY, this);//if you want fusion
		    g2d.fillRect(0, 0, w, h);
	    }

	    g2d.dispose();
	}
}
