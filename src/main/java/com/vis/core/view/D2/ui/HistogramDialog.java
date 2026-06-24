/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

import com.vis.core.histogram.BinSpec;
import com.vis.core.histogram.HistogramAnalyzer;
import com.vis.core.histogram.HistogramData;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Histogram analysis for the active Praparat's 5D (Z,C,T) stack: per-slice
 * or whole-stack histogram, user-chosen bin width or bin count, the usual
 * first-order statistics, and a simple display of which voxels in the
 * currently previewed slice fall in the selected bin.
 */
@SuppressWarnings("serial")
public class HistogramDialog extends JDialog {

	private static HistogramDialog instance;

	private Praparat praparat;

	private JSpinner zSpinner, cSpinner, tSpinner;
	private JRadioButton sliceRadio, stackRadio;
	private JRadioButton binWidthRadio, binCountRadio;
	private JSpinner binValueSpinner;
	private HistogramPlotPanel plotPanel;
	private HistogramMaskOverlayPanel overlayPanel;
	private JTextArea statsArea;

	public static void showDialog(Praparat praparat, Window owner) {
		if (instance == null) {
			instance = new HistogramDialog(owner);
		}
		instance.setPraparat(praparat);
		instance.setVisible(true);
	}

	private HistogramDialog(Window owner) {
		super(owner, "Histogram", ModalityType.MODELESS);
		initComponents();
		setLocationRelativeTo(owner);
	}

	private void initComponents() {
		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		root.add(buildTopControls(), BorderLayout.NORTH);

		plotPanel = new HistogramPlotPanel();
		plotPanel.setOnBinSelected(this::onBinSelected);

		overlayPanel = new HistogramMaskOverlayPanel();

		statsArea = new JTextArea();
		statsArea.setEditable(false);
		statsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		JPanel rightPanel = new JPanel(new BorderLayout(4, 4));
		rightPanel.add(overlayPanel, BorderLayout.CENTER);
		rightPanel.add(statsArea, BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, plotPanel, rightPanel);
		split.setResizeWeight(0.55);
		root.add(split, BorderLayout.CENTER);

		JPanel bottom = new JPanel();
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		bottom.add(close);
		root.add(bottom, BorderLayout.SOUTH);

		setContentPane(root);
	}

	private JPanel buildTopControls() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0;
		gbc.gridy = 0;

		zSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
		cSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
		tSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
		zSpinner.addChangeListener(e -> recompute());
		cSpinner.addChangeListener(e -> recompute());
		tSpinner.addChangeListener(e -> recompute());

		panel.add(new JLabel("Z:"), gbc); gbc.gridx++;
		panel.add(zSpinner, gbc); gbc.gridx++;
		panel.add(new JLabel("C:"), gbc); gbc.gridx++;
		panel.add(cSpinner, gbc); gbc.gridx++;
		panel.add(new JLabel("T:"), gbc); gbc.gridx++;
		panel.add(tSpinner, gbc); gbc.gridx++;

		sliceRadio = new JRadioButton("Slice histogram", true);
		stackRadio = new JRadioButton("Stack histogram", false);
		ButtonGroup scopeGroup = new ButtonGroup();
		scopeGroup.add(sliceRadio);
		scopeGroup.add(stackRadio);
		sliceRadio.addActionListener(e -> recompute());
		stackRadio.addActionListener(e -> recompute());
		panel.add(sliceRadio, gbc); gbc.gridx++;
		panel.add(stackRadio, gbc); gbc.gridx++;

		gbc.gridx = 0;
		gbc.gridy = 1;
		binWidthRadio = new JRadioButton("Bin width", true);
		binCountRadio = new JRadioButton("Bin count", false);
		ButtonGroup binGroup = new ButtonGroup();
		binGroup.add(binWidthRadio);
		binGroup.add(binCountRadio);
		binWidthRadio.addActionListener(e -> recompute());
		binCountRadio.addActionListener(e -> recompute());
		panel.add(binWidthRadio, gbc); gbc.gridx++;
		panel.add(binCountRadio, gbc); gbc.gridx++;

		binValueSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.01, 1000000.0, 1.0));
		binValueSpinner.addChangeListener(e -> recompute());
		panel.add(new JLabel("Value:"), gbc); gbc.gridx++;
		panel.add(binValueSpinner, gbc); gbc.gridx++;

		return panel;
	}

	private void setPraparat(Praparat praparat) {
		this.praparat = praparat;
		if (praparat == null) return;

		((SpinnerNumberModel) zSpinner.getModel()).setMaximum(Math.max(0, praparat.getNSlices() - 1));
		((SpinnerNumberModel) cSpinner.getModel()).setMaximum(Math.max(0, praparat.getNChannels() - 1));
		((SpinnerNumberModel) tSpinner.getModel()).setMaximum(Math.max(0, praparat.getNFrames() - 1));

		// Start on whichever slide is actually loaded and currently shown in the live
		// viewer, rather than guessing nSlices/2 - getSlideGlassAt() can legitimately
		// return null for a (Z,C,T) slot that hasn't been loaded, and the viewer's own
		// "current slide" is guaranteed non-null whenever any image is loaded at all.
		SlideGlass current = praparat.getCurrentSlide();
		int[] zct = current != null ? praparat.getZCTArray(current) : new int[] { 0, 0, 0 };
		zSpinner.setValue(clamp(zct[0], 0, praparat.getNSlices() - 1));
		cSpinner.setValue(clamp(zct[1], 0, praparat.getNChannels() - 1));
		tSpinner.setValue(clamp(zct[2], 0, praparat.getNFrames() - 1));

		pack();
		recompute();
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(Math.max(lo, hi), v));
	}

	private int currentZ() { return (Integer) zSpinner.getValue(); }
	private int currentC() { return (Integer) cSpinner.getValue(); }
	private int currentT() { return (Integer) tSpinner.getValue(); }

	private SlideGlass currentSlide() {
		if (praparat == null) return null;
		int idx = praparat.calcZctIndex(new int[] { currentZ(), currentC(), currentT() });
		// manageCache() unloads SlideGlasses outside its prefetch window as the live
		// viewer's own slice position moves; stepping through Z/C/T in this dialog
		// doesn't move that position, so a slide we land on here can be unloaded
		// (getOriginalImage()==null) even though the index itself is valid. Force it
		// back into memory before reading it.
		praparat.realizeImage(idx);
		return praparat.getSlideGlassAt(idx);
	}

	private BinSpec currentBinSpec() {
		double value = (Double) binValueSpinner.getValue();
		return binWidthRadio.isSelected() ? BinSpec.ofWidth(value) : BinSpec.ofCount((int) Math.round(value));
	}

	private void recompute() {
		if (praparat == null) return;
		SlideGlass slide = currentSlide();
		if (slide == null || slide.getOriginalImage() == null) {
			showStatus("No image loaded at Z=" + currentZ() + " C=" + currentC() + " T=" + currentT());
			plotPanel.setData(null);
			overlayPanel.setBaseImage(null);
			currentData = null;
			return;
		}

		try {
			ImageProcessor previewIp = slide.getOriginalImage().getProcessor();

			HistogramData data;
			if (stackRadio.isSelected()) {
				// Stack = aggregate over all Z for the currently selected C/T; the Z spinner
				// still independently picks which single slice gets previewed below.
				// Built via getImagePlus(C,T) rather than poking each SlideGlass directly -
				// it already falls back to the DicomImage's own pixel data when a slide has
				// been unloaded by manageCache(), which a manual per-slide loop did not.
				ImagePlus stackImp = praparat.getImagePlus(currentC(), currentT());
				List<ImageProcessor> slices = new ArrayList<>();
				if (stackImp != null) {
					ImageStack stack = stackImp.getStack();
					for (int i = 1; i <= stack.getSize(); i++) {
						slices.add(stack.getProcessor(i));
					}
				}
				Calibration stackCal = stackImp != null ? stackImp.getCalibration() : slide.getOriginalCalibration();
				data = HistogramAnalyzer.analyze(slices, stackCal, currentBinSpec());
			} else {
				data = HistogramAnalyzer.analyzeSlice(previewIp, slide.getOriginalCalibration(), currentBinSpec());
			}

			plotPanel.setData(data);
			overlayPanel.setBaseImage(renderGray(previewIp));
			overlayPanel.clearMask();
			updateStats(data);
		} catch (Exception e) {
			e.printStackTrace();
			showStatus("Histogram failed: " + e);
			plotPanel.setData(null);
			currentData = null;
		}
	}

	private void showStatus(String message) {
		statsArea.setText(message);
	}

	private void onBinSelected(int bin) {
		SlideGlass slide = currentSlide();
		if (slide == null || slide.getOriginalImage() == null || currentData == null) return;

		ImageProcessor ip = slide.getOriginalImage().getProcessor();
		Calibration cal = slide.getOriginalCalibration();
		double lo = currentData.binLow(bin);
		double hi = currentData.binHigh(bin);
		boolean[] mask = HistogramAnalyzer.computeBinMask(ip, cal, lo, hi);
		overlayPanel.setMask(mask);
	}

	private HistogramData currentData;

	private void updateStats(HistogramData data) {
		this.currentData = data;
		StringBuilder sb = new StringBuilder();
		sb.append("Count:    ").append(data.totalCount).append('\n');
		sb.append("Min:      ").append(fmt(data.min)).append(' ').append(data.valueUnit).append('\n');
		sb.append("Max:      ").append(fmt(data.max)).append(' ').append(data.valueUnit).append('\n');
		sb.append("Mean:     ").append(fmt(data.mean)).append('\n');
		sb.append("StdDev:   ").append(fmt(data.stdDev)).append('\n');
		sb.append("Variance: ").append(fmt(data.variance)).append('\n');
		sb.append("Mode:     ").append(fmt(data.mode)).append('\n');
		sb.append("Median:   ").append(fmt(data.median)).append('\n');
		sb.append("Skewness: ").append(fmt(data.skewness)).append('\n');
		sb.append("Kurtosis: ").append(fmt(data.kurtosis)).append('\n');
		sb.append("Entropy:  ").append(fmt(data.entropy)).append(" bits\n");
		sb.append("Bins:     ").append(data.binCount).append(" x ").append(fmt(data.binWidth)).append('\n');
		statsArea.setText(sb.toString());
	}

	private static String fmt(double v) {
		return String.format("%.3f", v);
	}

	/**
	 * ip.getMin()/getMax() reflect this processor's display (window/level)
	 * range, which can be a series-wide setting shared across every slide -
	 * not necessarily this particular slice's own value range. Using that
	 * for windowing left the preview looking unchanged across slices even
	 * though the underlying pixels (and therefore the histogram) did
	 * change. Scan the actual raw values of this slice instead.
	 */
	private static BufferedImage renderGray(ImageProcessor ip) {
		int w = ip.getWidth(), h = ip.getHeight();
		float[] raw = new float[w * h];
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float v = ip.getf(x, y);
				raw[y * w + x] = v;
				if (v < min) min = v;
				if (v > max) max = v;
			}
		}
		float range = Math.max(1e-6f, max - min);
		byte[] bytes = new byte[w * h];
		for (int i = 0; i < bytes.length; i++) {
			float t = (raw[i] - min) / range;
			bytes[i] = (byte) Math.round(Math.max(0f, Math.min(1f, t)) * 255f);
		}
		return new ByteProcessor(w, h, bytes, null).getBufferedImage();
	}
}
