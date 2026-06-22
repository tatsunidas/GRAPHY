/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D2.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

/**
 * Display-only panel: shows a simple 8-bit grayscale rendering of one slice,
 * with an optional semi-transparent red overlay marking voxels that match
 * the histogram bin currently selected in {@link HistogramPlotPanel}. No
 * interaction of its own - it just reflects whatever base image/mask it is
 * given.
 */
public class HistogramMaskOverlayPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int OVERLAY_ALPHA = 140;

	private BufferedImage baseImage;
	private BufferedImage overlayImage; // same size as baseImage, transparent except matched pixels

	public HistogramMaskOverlayPanel() {
		setBackground(Color.BLACK);
		setPreferredSize(new Dimension(420, 420));
	}

	/** 8-bit grayscale base image for the slice currently being previewed. */
	public void setBaseImage(BufferedImage base) {
		this.baseImage = base;
		this.overlayImage = null;
		repaint();
	}

	/** mask is row-major, length == width*height of the current base image; true = highlight red. */
	public void setMask(boolean[] mask) {
		if (baseImage == null || mask == null) {
			overlayImage = null;
			repaint();
			return;
		}
		int w = baseImage.getWidth();
		int h = baseImage.getHeight();
		BufferedImage overlay = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		int redArgb = new Color(255, 0, 0, OVERLAY_ALPHA).getRGB();
		int[] pixels = new int[w * h];
		for (int i = 0; i < pixels.length && i < mask.length; i++) {
			pixels[i] = mask[i] ? redArgb : 0;
		}
		overlay.setRGB(0, 0, w, h, pixels, 0, w);
		this.overlayImage = overlay;
		repaint();
	}

	public void clearMask() {
		this.overlayImage = null;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (baseImage == null) return;

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int panelW = Math.max(1, getWidth());
		int panelH = Math.max(1, getHeight());
		double scale = Math.min((double) panelW / baseImage.getWidth(), (double) panelH / baseImage.getHeight());
		int dw = (int) Math.round(baseImage.getWidth() * scale);
		int dh = (int) Math.round(baseImage.getHeight() * scale);

		g2.drawImage(baseImage, 0, 0, dw, dh, null);
		if (overlayImage != null) {
			g2.drawImage(overlayImage, 0, 0, dw, dh, null);
		}
	}
}
