package com.vis.core.view.D2.ui.glasses;

import javax.swing.JComponent;
import javax.swing.plaf.LayerUI;

import com.vis.core.view.D2.ui.DataDropTargetListener;

public class EyepieceUI extends LayerUI<JComponent>{

	/**
	 * 何か特殊な可視化機能を実装するために使う。
	 * https://www.youtube.com/watch?v=6mQYsWCkx4g
	 */
	private static final long serialVersionUID = -5556938003296355095L;
	
	public EyepieceUI(Eyepiece eye) {
		new DataDropTargetListener(eye);
	}
	
//	@Override
//	  public void paint(Graphics g, JComponent c) {
//	    super.paint(g, c);
//	    Graphics2D g2 = (Graphics2D) g.create();
//	    int w = c.getWidth();
//	    int h = c.getHeight();
//	    g2.setComposite(AlphaComposite.getInstance(
//	            AlphaComposite.SRC_OVER, .5f));
//	    g2.setPaint(new GradientPaint(0, 0, Color.yellow, 0, h, Color.red));
//	    g2.fillRect(0, 0, w, h);
//
//	    g2.dispose();
//	  }
}
