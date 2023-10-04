package com.vis.core.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;

/**
 * The "missing icon" is a white box with a black border and a red x.
 * It's used to display something when there are issues loading an
 * icon from an external location.
 *
 * @author Collin Fagan
 * @author tatsuaki kobayashi
 */
public class CloseIcon implements Icon{

    private int width = 32;
    private int height = 32;
    private Color lineColor = Color.BLACK;
    private Color rectColor = Color.GRAY;
    private int strokeSize = 2;
    
    public CloseIcon(Color lineColor, int w, int h) {
    	if(lineColor != null) {
    		this.lineColor = lineColor;
    	}
    	this.width = w;
    	this.height = h;
    }
    
    public CloseIcon(int strokeSize, Color lineColor, Color rectColor, int w, int h) {
    	this.lineColor = lineColor;
    	this.rectColor = rectColor;
    	this.width = w;
    	this.height = h;
    	this.strokeSize = strokeSize;
    }
    
    /*
     * ImageIcon ii = closeIcon.createImageIcon(new JLabel())
     */
    public ImageIcon createImageIcon(JComponent standInComponent) {
		BufferedImage image = new BufferedImage(getIconWidth(), getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics g = image.createGraphics();
		try {
			paintIcon(standInComponent, g, 0, 0);
			return new ImageIcon(image);
		} finally {
			g.dispose();
		}
	}

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(x , y, this.width, this.height);

    	if(rectColor != null) {
			g2d.setColor(Color.GRAY);
			g2d.drawRect(x, y, this.width, this.height);
    	}
    	if(lineColor != null) {
    		g2d.setColor(lineColor);
    		g2d.setStroke(new BasicStroke(strokeSize));
            g2d.drawLine(x+5, y+5, x+this.width-5, y+this.height-5);
            g2d.drawLine(x+5, y+this.height-5, x+this.width-5, y+5);
    	}
        g2d.dispose();
    }

    public int getIconWidth() {
        return this.width;
    }

    public int getIconHeight() {
        return this.height;
    }
}