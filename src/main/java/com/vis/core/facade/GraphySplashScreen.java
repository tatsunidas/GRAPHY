package com.vis.core.facade;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.vis.core.log.Log;

/**
 * 
 * From java 6, anybody can use default SplashScreen.class . 
 * https://docs.oracle.com/javase/tutorial/uiswing/misc/splashscreen.html
 * 
 * But, it requires jvm option or Manifest option.
 * 
 * So, in graphy, use JFrame basis original Splash Screen.
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings({"serial", "unused"})
public class GraphySplashScreen extends JFrame {
	
	//test
	public static void main (String args[]) {
        new GraphySplashScreen();
    }
	
	JProgressBar progress;

	public GraphySplashScreen() {
		setLayout(new BorderLayout());
		setUndecorated(true);// title bar no visible
		BufferedImage splash = null;
		try {
			splash = ImageIO.read(getClass().getResource("/icon/splash.png"));
		} catch (IOException e) {
			// skip showing splash
			dispose();
			return;
		}

		SplashPanel sp = new SplashPanel(splash);
		add(sp, BorderLayout.CENTER);
		
		progress = new JProgressBar();
		progress.setMaximum(0);//default
		progress.setMaximum(99);//default
		progress.setStringPainted(true);
		progress.setString("Ready to start ...");
		add(progress, BorderLayout.SOUTH);//System.out.println(progress.getHeight());//here, 0 height yet.
		
		//https://stackoverflow.com/questions/19869751/get-size-of-jpanel-before-setvisible-called
		// adjust and fix sizes of components.
		pack();
		
		//then, set sizes
		setSize(new Dimension(splash.getWidth(),splash.getHeight()+progress.getHeight()));
		
		setLocationRelativeTo(null);
		setVisible(true);
		toFront();
		
	}
	
	public void startProgressAndClose(String progressPrefix, int max) {
		new Thread(){
	        public void run(){
	        	progress.setMaximum(max);
				progress.setString("[" + progressPrefix + "]:" + "Ready to start GRAPHY");
				progress.repaint();
				for (int i = 0; i < max; i++) {
					progress.setValue(i++);
					try {
						Thread.sleep(76);
					} catch (InterruptedException e) {
						e.printStackTrace();
						Log.logger.log(Level.SEVERE, e.getMessage());
					}
				}
				progress.setString("GRAPHY start ...");
				progress.repaint();
				try {
					Thread.sleep( max < 5 ? 2000:1500);
				} catch (InterruptedException e) {
					e.printStackTrace();
					Log.logger.log(Level.SEVERE, e.getMessage());
				}
				dispose();
	        }
	    }.start();
	}
	
	private class SplashPanel extends JPanel{
    	private BufferedImage bg;
		int w;
    	int h;
    	SplashPanel(BufferedImage bg){
    		this.bg = bg;
    		this.w = bg.getWidth();
    		this.h = bg.getHeight();
//    		this.setSize(w, h);
    	}
    	
		@Override
		public void paintComponent(Graphics g) {
			/*
			 * if you want to resizing
			 */
//			BufferedImage resizedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
//			Graphics2D graphics2D = resizedImage.createGraphics();
//			graphics2D.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
//			graphics2D.dispose();
//			g.drawImage(resizedImage, 0, 0, null);
			
			//here, simply show image with an original image size.
			g.drawImage(bg, 0, 0, null);
		}
    }
}
