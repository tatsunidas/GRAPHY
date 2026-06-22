/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D2.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import org.joml.Vector3d;

import com.vis.core.slicer.Centerline3D;
import com.vis.core.slicer.VolumeSampler;

/**
 * Draws one reference slice and an editable centerline (Curved MPR input)
 * on top of it. Control points are added/dragged in screen/image pixel
 * space but stored in {@link Centerline3D} as physical (LPS, mm)
 * coordinates - converted via {@link VolumeSampler#toVoxelIndex}/
 * {@link VolumeSampler#toPhysical} using this panel's fixed slice index for
 * the out-of-plane component, so the curve model itself never depends on
 * which slice/view a point was drawn from.
 */
public class CurvedMprCurvePanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int POINT_RADIUS = 5;
	private static final int HIT_RADIUS = 9;
	private static final int MIN_POINTS = 2;

	private final BufferedImage backgroundImage;
	private final VolumeSampler sampler;
	private final Centerline3D curve;
	private final int sliceZIndex;

	private double scale = 1.0;
	private int draggingIndex = -1;
	private Runnable onChange;

	public CurvedMprCurvePanel(BufferedImage backgroundImage, VolumeSampler sampler, Centerline3D curve, int sliceZIndex) {
		this.backgroundImage = backgroundImage;
		this.sampler = sampler;
		this.curve = curve;
		this.sliceZIndex = sliceZIndex;
		setPreferredSize(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()));
		installMouseHandlers();
	}

	public void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	public Centerline3D getCurve() {
		return curve;
	}

	// --- coordinate mapping -------------------------------------------------

	private void updateScale() {
		int w = Math.max(1, getWidth());
		int h = Math.max(1, getHeight());
		double sx = (double) w / backgroundImage.getWidth();
		double sy = (double) h / backgroundImage.getHeight();
		scale = Math.min(sx, sy);
	}

	private int screenXOfImageX(double imageX) {
		return (int) Math.round(imageX * scale);
	}

	private int screenYOfImageY(double imageY) {
		return (int) Math.round(imageY * scale);
	}

	private double imageXOfScreenX(int screenX) {
		return screenX / scale;
	}

	private double imageYOfScreenY(int screenY) {
		return screenY / scale;
	}

	/** Screen pixel position of a physical (mm) point, projected through this panel's slice. */
	private Vector3d screenPositionOf(Vector3d physicalPointMm) {
		double[] idx = sampler.toVoxelIndex(physicalPointMm);
		return new Vector3d(screenXOfImageX(idx[0]), screenYOfImageY(idx[1]), 0);
	}

	/** Physical (mm) point for a screen click, keeping the out-of-plane (Z) component fixed at this panel's slice. */
	private Vector3d physicalOf(int screenX, int screenY) {
		double i = imageXOfScreenX(screenX);
		double j = imageYOfScreenY(screenY);
		return sampler.toPhysical(i, j, sliceZIndex);
	}

	/** Physical (mm) point for a screen drag, preserving the point's existing out-of-plane component. */
	private Vector3d physicalOfKeepingDepth(int screenX, int screenY, Vector3d existing) {
		double[] existingIdx = sampler.toVoxelIndex(existing);
		double i = imageXOfScreenX(screenX);
		double j = imageYOfScreenY(screenY);
		return sampler.toPhysical(i, j, existingIdx[2]);
	}

	// --- painting -------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		updateScale();
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		int dw = (int) Math.round(backgroundImage.getWidth() * scale);
		int dh = (int) Math.round(backgroundImage.getHeight() * scale);
		g2.drawImage(backgroundImage, 0, 0, dw, dh, null);

		int n = curve.size();
		if (n == 0) {
			return;
		}

		g2.setColor(Color.YELLOW);
		g2.setStroke(new BasicStroke(1.8f));
		Vector3d prevScreen = null;
		for (int i = 0; i < n; i++) {
			Vector3d s = screenPositionOf(curve.getControlPoint(i));
			if (prevScreen != null) {
				g2.drawLine((int) prevScreen.x, (int) prevScreen.y, (int) s.x, (int) s.y);
			}
			prevScreen = s;
		}

		for (int i = 0; i < n; i++) {
			Vector3d s = screenPositionOf(curve.getControlPoint(i));
			int px = (int) s.x;
			int py = (int) s.y;
			g2.setColor(Color.ORANGE);
			g2.fillOval(px - POINT_RADIUS, py - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
			g2.setColor(Color.BLACK);
			g2.drawOval(px - POINT_RADIUS, py - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
		}
	}

	// --- interaction ------------------------------------------------------

	private int findPointAt(int sx, int sy) {
		int n = curve.size();
		for (int i = 0; i < n; i++) {
			Vector3d s = screenPositionOf(curve.getControlPoint(i));
			double dx = s.x - sx, dy = s.y - sy;
			if (dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS) {
				return i;
			}
		}
		return -1;
	}

	private void installMouseHandlers() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				int idx = findPointAt(e.getX(), e.getY());
				if (e.getButton() == MouseEvent.BUTTON3) {
					// Right-click removes a point, but a centerline always needs >= 2 points.
					if (idx >= 0 && curve.size() > MIN_POINTS) {
						curve.removeControlPoint(idx);
						repaint();
						fireChange();
					}
					return;
				}
				if (idx >= 0) {
					draggingIndex = idx;
				} else if (e.getClickCount() == 2) {
					// Append a new control point at the end of the path.
					curve.addControlPoint(physicalOf(e.getX(), e.getY()));
					repaint();
					fireChange();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				draggingIndex = -1;
			}
		});
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (draggingIndex < 0) return;
				Vector3d existing = curve.getControlPoint(draggingIndex);
				Vector3d updated = physicalOfKeepingDepth(e.getX(), e.getY(), existing);
				curve.setControlPoint(draggingIndex, updated);
				repaint();
				fireChange();
			}
		});
	}

	private void fireChange() {
		if (onChange != null) {
			onChange.run();
		}
	}
}
