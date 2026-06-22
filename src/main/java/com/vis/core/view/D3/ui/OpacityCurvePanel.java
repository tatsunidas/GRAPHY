/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JPanel;

import ij.measure.Calibration;

/**
 * Draws the voxel value histogram of the current volume and an editable
 * opacity-vs-value curve on top of it. The curve is defined by a small list
 * of draggable control points (value 0-255, opacity 0-1); values in between
 * are linearly interpolated when the curve is resolved to a 256-entry byte
 * array for upload to the GPU LUT texture.
 *
 * Histogram bins and control point values are always in raw voxel units;
 * only the axis tick labels are converted through {@code calibration} (when
 * present) so the displayed numbers match calibrated units (e.g. CT/HU)
 * instead of the underlying raw pixel value.
 */
public class OpacityCurvePanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public static class ControlPoint {
		public int value; // 0-255, position along the LUT
		public float opacity; // 0.0-1.0

		public ControlPoint(int value, float opacity) {
			this.value = value;
			this.opacity = opacity;
		}
	}

	private static final int POINT_RADIUS = 5;
	private static final int HIT_RADIUS = 9;
	private static final int MARGIN_LEFT = 50;
	private static final int MARGIN_RIGHT = 14;
	private static final int MARGIN_TOP = 12;
	private static final int MARGIN_BOTTOM = 26;

	private final int[] histogram; // 256 raw bin counts
	private final float dataMin;
	private final float dataMax;
	private final Calibration calibration; // may be null (no value calibration available)
	private int histogramDisplayMax = 1; // peak-clipped, for bar scaling

	private final List<ControlPoint> points;
	private int draggingIndex = -1;
	private Runnable onChange;

	public OpacityCurvePanel(int[] histogram, float dataMin, float dataMax, List<ControlPoint> initialPoints) {
		this(histogram, dataMin, dataMax, initialPoints, null);
	}

	public OpacityCurvePanel(int[] histogram, float dataMin, float dataMax, List<ControlPoint> initialPoints,
			Calibration calibration) {
		this.histogram = histogram;
		this.dataMin = dataMin;
		this.dataMax = dataMax;
		this.calibration = calibration;
		this.points = initialPoints;
		computeHistogramDisplayMax();
		setBackground(Color.BLACK);
		setPreferredSize(new Dimension(560, 260));
		installMouseHandlers();
	}

	/** Converts a raw axis value to calibrated units (e.g. HU) when a value calibration is available. */
	private float toDisplayValue(float rawValue) {
		if (calibration != null && calibration.calibrated()) {
			return (float) calibration.getCValue(rawValue);
		}
		return rawValue;
	}

	public void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	public List<ControlPoint> getPoints() {
		return points;
	}

	/**
	 * Resolve the current control points into a dense 256-entry byte array
	 * (0-255), linearly interpolating opacity between consecutive points.
	 */
	public byte[] resolveTo256() {
		byte[] out = new byte[256];
		List<ControlPoint> sorted = sortedPoints();
		for (int i = 0; i < sorted.size() - 1; i++) {
			ControlPoint a = sorted.get(i);
			ControlPoint b = sorted.get(i + 1);
			int span = Math.max(1, b.value - a.value);
			for (int v = a.value; v <= b.value; v++) {
				float t = (float) (v - a.value) / span;
				float op = a.opacity + (b.opacity - a.opacity) * t;
				out[v] = (byte) Math.round(Math.max(0f, Math.min(1f, op)) * 255f);
			}
		}
		// Fill outside the first/last control point with their edge opacity.
		if (!sorted.isEmpty()) {
			byte first = (byte) Math.round(Math.max(0f, Math.min(1f, sorted.get(0).opacity)) * 255f);
			byte last = (byte) Math.round(Math.max(0f, Math.min(1f, sorted.get(sorted.size() - 1).opacity)) * 255f);
			for (int v = 0; v < sorted.get(0).value; v++) out[v] = first;
			for (int v = sorted.get(sorted.size() - 1).value; v < 256; v++) out[v] = last;
		}
		return out;
	}

	private void computeHistogramDisplayMax() {
		if (histogram == null || histogram.length == 0) {
			histogramDisplayMax = 1;
			return;
		}
		int maxCount = 0, mode = 0;
		for (int i = 0; i < histogram.length; i++) {
			if (histogram[i] > maxCount) {
				maxCount = histogram[i];
				mode = i;
			}
		}
		int maxCount2 = 0;
		for (int i = 0; i < histogram.length; i++) {
			if (histogram[i] > maxCount2 && i != mode) {
				maxCount2 = histogram[i];
			}
		}
		// Same trick as WwWlContrastPlot: a single dominant bin (typically
		// air/background) would otherwise flatten every other bin to nothing.
		if (maxCount2 != 0 && maxCount > maxCount2 * 2) {
			histogramDisplayMax = (int) (maxCount2 * 1.5);
		} else {
			histogramDisplayMax = Math.max(1, maxCount);
		}
	}

	// --- coordinate mapping -------------------------------------------------

	private int plotLeft() { return MARGIN_LEFT; }
	private int plotRight() { return getWidth() - MARGIN_RIGHT; }
	private int plotTop() { return MARGIN_TOP; }
	private int plotBottom() { return getHeight() - MARGIN_BOTTOM; }

	private int valueToX(int value) {
		int w = Math.max(1, plotRight() - plotLeft());
		return plotLeft() + Math.round(value / 255f * w);
	}

	private int opacityToY(float opacity) {
		int h = Math.max(1, plotBottom() - plotTop());
		return plotBottom() - Math.round(Math.max(0f, Math.min(1f, opacity)) * h);
	}

	private int xToValue(int x) {
		int w = Math.max(1, plotRight() - plotLeft());
		int v = Math.round((x - plotLeft()) / (float) w * 255f);
		return Math.max(0, Math.min(255, v));
	}

	private float yToOpacity(int y) {
		int h = Math.max(1, plotBottom() - plotTop());
		float op = (plotBottom() - y) / (float) h;
		return Math.max(0f, Math.min(1f, op));
	}

	// --- painting -------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int left = plotLeft(), right = plotRight(), top = plotTop(), bottom = plotBottom();
		int plotW = Math.max(1, right - left);
		int plotH = Math.max(1, bottom - top);

		// Histogram bars.
		if (histogram != null) {
			g2.setColor(new Color(90, 140, 200));
			double scaleX = plotW / (double) histogram.length;
			for (int i = 0; i < histogram.length; i++) {
				int barH = (int) ((double) plotH * histogram[i] / histogramDisplayMax);
				barH = Math.min(barH, plotH);
				int x = left + (int) (i * scaleX);
				int barW = Math.max(1, (int) Math.ceil(scaleX));
				g2.fillRect(x, bottom - barH, barW, barH);
			}
		}

		// Axes.
		g2.setColor(Color.GRAY);
		g2.drawRect(left, top, plotW, plotH);
		g2.setColor(Color.LIGHT_GRAY);
		for (int i = 0; i <= 4; i++) {
			int x = left + plotW * i / 4;
			float val = dataMin + (dataMax - dataMin) * i / 4f;
			g2.drawLine(x, bottom, x, bottom + 4);
			String label = formatValue(toDisplayValue(val));
			int labelW = g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, Math.max(0, x - labelW / 2), bottom + 18);
		}
		g2.drawString("1.0", 4, top + 8);
		g2.drawString("0.0", 4, bottom);

		// Curve.
		List<ControlPoint> sorted = sortedPoints();
		g2.setColor(Color.YELLOW);
		g2.setStroke(new BasicStroke(1.8f));
		for (int i = 0; i < sorted.size() - 1; i++) {
			ControlPoint a = sorted.get(i);
			ControlPoint b = sorted.get(i + 1);
			g2.drawLine(valueToX(a.value), opacityToY(a.opacity), valueToX(b.value), opacityToY(b.opacity));
		}
		for (ControlPoint p : sorted) {
			int px = valueToX(p.value);
			int py = opacityToY(p.opacity);
			g2.setColor(Color.ORANGE);
			g2.fillOval(px - POINT_RADIUS, py - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
			g2.setColor(Color.BLACK);
			g2.drawOval(px - POINT_RADIUS, py - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
		}
	}

	private String formatValue(float v) {
		if (Math.abs(v) >= 1000 || (v != 0 && Math.abs(v) < 1)) {
			return String.format("%.0f", v);
		}
		return String.valueOf((int) v);
	}

	// --- interaction ------------------------------------------------------

	private int findPointAt(int x, int y) {
		for (int i = 0; i < points.size(); i++) {
			ControlPoint p = points.get(i);
			int px = valueToX(p.value);
			int py = opacityToY(p.opacity);
			int dx = px - x, dy = py - y;
			if (dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * points is kept in insertion order (new points are appended), not value
	 * order, so "first/last" must be resolved by sorted position - never by
	 * raw list index, which only reflects creation order.
	 */
	private List<ControlPoint> sortedPoints() {
		List<ControlPoint> sorted = new ArrayList<>(points);
		Collections.sort(sorted, Comparator.comparingInt(p -> p.value));
		return sorted;
	}

	private void installMouseHandlers() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				int idx = findPointAt(e.getX(), e.getY());
				if (e.getButton() == MouseEvent.BUTTON3) {
					// Right-click an existing interior point removes it; the
					// two endpoints (first/last by value, not by list index)
					// are kept so the curve always spans the full value range.
					if (idx >= 0) {
						List<ControlPoint> sorted = sortedPoints();
						int sortedIdx = sorted.indexOf(points.get(idx));
						if (sortedIdx > 0 && sortedIdx < sorted.size() - 1) {
							points.remove(idx);
							repaint();
							fireChange();
						}
					}
					return;
				}
				if (idx >= 0) {
					draggingIndex = idx;
				} else if (e.getClickCount() == 2) {
					int value = xToValue(e.getX());
					float opacity = yToOpacity(e.getY());
					points.add(new ControlPoint(value, opacity));
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
				ControlPoint p = points.get(draggingIndex);

				// Bound the drag by this point's neighbors *by value*, not by
				// its position in the (creation-ordered) points list.
				List<ControlPoint> sorted = sortedPoints();
				int sortedIdx = sorted.indexOf(p);
				boolean isFirst = sortedIdx <= 0;
				boolean isLast = sortedIdx >= sorted.size() - 1;
				int minValue = isFirst ? 0 : sorted.get(sortedIdx - 1).value + 1;
				int maxValue = isLast ? 255 : sorted.get(sortedIdx + 1).value - 1;
				if (isFirst) minValue = maxValue = 0; // first point pinned at value 0
				if (isLast) minValue = maxValue = 255; // last point pinned at 255
				int newValue = Math.max(minValue, Math.min(maxValue, xToValue(e.getX())));
				p.value = newValue;
				p.opacity = yToOpacity(e.getY());
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
