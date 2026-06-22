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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

import javax.swing.JPanel;

import com.vis.core.histogram.HistogramData;

/**
 * Draws a histogram's bars and lets the user click a bar to select its bin.
 * Display-only beyond that single click interaction - no dragging/editing.
 */
public class HistogramPlotPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int MARGIN_LEFT = 60;
	private static final int MARGIN_RIGHT = 14;
	private static final int MARGIN_TOP = 12;
	private static final int MARGIN_BOTTOM = 28;

	private HistogramData data;
	private int selectedBin = -1;
	private int displayMaxCount = 1; // peak-clipped, for bar scaling
	private IntConsumer onBinSelected;

	public HistogramPlotPanel() {
		setBackground(Color.BLACK);
		setPreferredSize(new Dimension(560, 240));
		installMouseHandlers();
	}

	public void setOnBinSelected(IntConsumer onBinSelected) {
		this.onBinSelected = onBinSelected;
	}

	public void setData(HistogramData data) {
		this.data = data;
		this.selectedBin = -1;
		computeDisplayMax();
		repaint();
	}

	public void setSelectedBin(int bin) {
		this.selectedBin = bin;
		repaint();
	}

	public int getSelectedBin() {
		return selectedBin;
	}

	private void computeDisplayMax() {
		if (data == null || data.counts.length == 0) {
			displayMaxCount = 1;
			return;
		}
		long maxCount = 0, secondMaxCount = 0;
		int mode = -1;
		for (int i = 0; i < data.counts.length; i++) {
			if (data.counts[i] > maxCount) {
				maxCount = data.counts[i];
				mode = i;
			}
		}
		for (int i = 0; i < data.counts.length; i++) {
			if (i != mode && data.counts[i] > secondMaxCount) {
				secondMaxCount = data.counts[i];
			}
		}
		// A single dominant bin (e.g. background air/black) would otherwise flatten every other bin to nothing.
		if (secondMaxCount != 0 && maxCount > secondMaxCount * 2) {
			displayMaxCount = (int) (secondMaxCount * 1.5);
		} else {
			displayMaxCount = (int) Math.max(1, maxCount);
		}
	}

	private int plotLeft() { return MARGIN_LEFT; }
	private int plotRight() { return getWidth() - MARGIN_RIGHT; }
	private int plotTop() { return MARGIN_TOP; }
	private int plotBottom() { return getHeight() - MARGIN_BOTTOM; }

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (data == null) return;

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int left = plotLeft(), right = plotRight(), top = plotTop(), bottom = plotBottom();
		int plotW = Math.max(1, right - left);
		int plotH = Math.max(1, bottom - top);
		int n = data.counts.length;
		double scaleX = plotW / (double) n;

		for (int i = 0; i < n; i++) {
			int barH = (int) ((double) plotH * data.counts[i] / displayMaxCount);
			barH = Math.min(barH, plotH);
			int x = left + (int) (i * scaleX);
			int barW = Math.max(1, (int) Math.ceil(scaleX));
			g2.setColor(i == selectedBin ? Color.RED : new Color(90, 140, 200));
			g2.fillRect(x, bottom - barH, barW, barH);
		}

		g2.setColor(Color.GRAY);
		g2.drawRect(left, top, plotW, plotH);
		g2.setColor(Color.LIGHT_GRAY);
		for (int i = 0; i <= 4; i++) {
			int x = left + plotW * i / 4;
			double val = data.binStart + (data.binCount * data.binWidth) * i / 4.0;
			g2.drawLine(x, bottom, x, bottom + 4);
			String label = formatValue(val);
			int labelW = g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, Math.max(0, x - labelW / 2), bottom + 18);
		}
		g2.drawString(String.valueOf(displayMaxCount), 4, top + 10);
		g2.drawString("0", 4, bottom);

		if (selectedBin >= 0 && selectedBin < n) {
			String sel = formatValue(data.binLow(selectedBin)) + " - " + formatValue(data.binHigh(selectedBin))
					+ " (" + data.counts[selectedBin] + " px)";
			g2.setColor(Color.RED);
			g2.drawString(sel, left, top + 10);
		}
	}

	private String formatValue(double v) {
		if (Math.abs(v) >= 1000 || (v != 0 && Math.abs(v) < 1)) {
			return String.format("%.0f", v);
		}
		return String.valueOf((int) v);
	}

	private void installMouseHandlers() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (data == null || data.counts.length == 0) return;
				int plotW = Math.max(1, plotRight() - plotLeft());
				int x = e.getX() - plotLeft();
				if (x < 0) x = 0;
				if (x >= plotW) x = plotW - 1;
				int bin = x * data.counts.length / plotW;
				bin = Math.max(0, Math.min(data.counts.length - 1, bin));
				selectedBin = bin;
				repaint();
				if (onBinSelected != null) {
					onBinSelected.accept(bin);
				}
			}
		});
	}
}
