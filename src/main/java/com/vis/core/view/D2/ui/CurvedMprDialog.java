/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D2.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import com.vis.core.slicer.Centerline3D;
import com.vis.core.slicer.Centerline3D.FrameMode;
import com.vis.core.slicer.CurvedReformatter;
import com.vis.core.slicer.CurvedReformatter.ProjectionMode;
import com.vis.core.slicer.VolumeSampler;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D3.ui.VolumeData;
import com.vis.core.view.D3.ui.VolumeLoader;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Lets the user trace a centerline on one reference slice of the active
 * Praparat and reformats the volume along that curve (Curved MPR / curved
 * planar reformation) - e.g. a dental panoramic-style unfold (fixed-Z second
 * axis) or a vessel cross-section stretch (rotation-minimizing second axis).
 *
 * Uses {@code VolumeLoader.loadVolumeData(Praparat)} (not the GL-mirrored
 * {@code loadDicom}), so the curve the user draws and the reformatted output
 * keep the same left/right orientation as the rest of the 2D viewer.
 */
@SuppressWarnings("serial")
public class CurvedMprDialog extends JDialog {

	private static CurvedMprDialog instance;

	private VolumeData volume;
	private VolumeSampler sampler;
	private Centerline3D curve;
	private int sliceZIndex;

	// DICOM identifiers captured once from the source series, reused when tagging the result.
	private String sourcePatientId;
	private String sourceStudyInstanceUid;
	private String sourceSopClassUid;
	private String sourceModality;
	private String sourceWindowCenter;
	private String sourceWindowWidth;

	private CurvedMprCurvePanel curvePanel;
	private JPanel curveHost;
	private JLabel previewLabel;

	private JComboBox<FrameMode> frameModeCombo;
	private JComboBox<ProjectionMode> projectionModeCombo;
	private JSpinner bandHalfWidthSpinner;
	private JSpinner secondAxisMinSpinner;
	private JSpinner secondAxisMaxSpinner;
	private JButton showResultButton;

	public static void showDialog(Praparat praparat, Window owner) {
		if (instance == null) {
			instance = new CurvedMprDialog(owner);
		}
		instance.setPraparat(praparat);
		instance.setVisible(true);
	}

	private CurvedMprDialog(Window owner) {
		super(owner, "Curved MPR", ModalityType.MODELESS);
		initComponents();
		setLocationRelativeTo(owner);
	}

	private void initComponents() {
		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel hint = new JLabel(
				"<html>Double-click empty space to add a centerline point &middot; Drag a point to move it &middot; "
						+ "Right-click a point to remove it (minimum 2 points)</html>",
				SwingConstants.LEFT);
		root.add(hint, BorderLayout.NORTH);

		curveHost = new JPanel(new BorderLayout());
		previewLabel = new JLabel("", SwingConstants.CENTER);
		JScrollPane previewScroll = new JScrollPane(previewLabel);
		previewScroll.setPreferredSize(new Dimension(420, 420));

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, curveHost, previewScroll);
		split.setResizeWeight(0.6);
		root.add(split, BorderLayout.CENTER);

		root.add(buildControls(), BorderLayout.SOUTH);

		setContentPane(root);
	}

	private JPanel buildControls() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0;
		gbc.gridy = 0;

		frameModeCombo = new JComboBox<>(FrameMode.values());
		frameModeCombo.addActionListener(e -> recomputePreview());
		panel.add(new JLabel("Second axis:"), gbc);
		gbc.gridx++;
		panel.add(frameModeCombo, gbc);

		gbc.gridx++;
		projectionModeCombo = new JComboBox<>(ProjectionMode.values());
		projectionModeCombo.addActionListener(e -> recomputePreview());
		panel.add(new JLabel("Projection:"), gbc);
		gbc.gridx++;
		panel.add(projectionModeCombo, gbc);

		gbc.gridx++;
		bandHalfWidthSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 0.5));
		bandHalfWidthSpinner.addChangeListener(e -> recomputePreview());
		panel.add(new JLabel("Band half-width (mm):"), gbc);
		gbc.gridx++;
		panel.add(bandHalfWidthSpinner, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		secondAxisMinSpinner = new JSpinner(new SpinnerNumberModel(-50.0, -1000.0, 1000.0, 1.0));
		secondAxisMinSpinner.addChangeListener(e -> recomputePreview());
		panel.add(new JLabel("2nd axis min (mm):"), gbc);
		gbc.gridx++;
		panel.add(secondAxisMinSpinner, gbc);

		gbc.gridx++;
		secondAxisMaxSpinner = new JSpinner(new SpinnerNumberModel(50.0, -1000.0, 1000.0, 1.0));
		secondAxisMaxSpinner.addChangeListener(e -> recomputePreview());
		panel.add(new JLabel("2nd axis max (mm):"), gbc);
		gbc.gridx++;
		panel.add(secondAxisMaxSpinner, gbc);

		gbc.gridx++;
		JButton resetButton = new JButton("Reset Curve");
		resetButton.addActionListener(e -> resetCurve());
		panel.add(resetButton, gbc);

		gbc.gridx++;
		showResultButton = new JButton("Show Full Result...");
		showResultButton.addActionListener(e -> showFullResult());
		panel.add(showResultButton, gbc);

		gbc.gridx++;
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		panel.add(close, gbc);

		return panel;
	}

	private void setPraparat(Praparat praparat) {
		ImagePlus source = praparat.getImagePlus(-1, -1);
		if (source == null) {
			return;
		}
		// Capture identifiers from the source series once, before VolumeLoader
		// consumes/closes this ImagePlus, so the result can stay linked to the
		// same patient/study without re-reading the whole stack a second time.
		this.sourcePatientId = GDicomTools.getTag(source, "0010,0020");
		this.sourceStudyInstanceUid = GDicomTools.getTag(source, "0020,000D");
		this.sourceSopClassUid = GDicomTools.getTag(source, "0008,0016");
		this.sourceModality = GDicomTools.getTag(source, "0008,0060");
		this.sourceWindowCenter = GDicomTools.getTag(source, "0028,1050");
		this.sourceWindowWidth = GDicomTools.getTag(source, "0028,1051");

		this.volume = VolumeLoader.loadVolumeData(source);
		if (volume == null) {
			return;
		}
		this.sampler = new VolumeSampler(volume);
		this.curve = new Centerline3D();
		this.sliceZIndex = volume.depth / 2;

		BufferedImage bg = renderSliceImage(volume, sliceZIndex, volume.minVal, volume.maxVal);
		curvePanel = new CurvedMprCurvePanel(bg, sampler, curve, sliceZIndex);
		curvePanel.setOnChange(this::recomputePreview);

		curveHost.removeAll();
		curveHost.add(curvePanel, BorderLayout.CENTER);
		curveHost.revalidate();

		// Sensible default second-axis range for the fixed-Z (panoramic) case: the full
		// craniocaudal extent of the stack, centered on the reference slice.
		double zExtentMm = Math.sqrt(volume.stepZ[0] * volume.stepZ[0] + volume.stepZ[1] * volume.stepZ[1]
				+ volume.stepZ[2] * volume.stepZ[2]) * (volume.depth - 1);
		secondAxisMinSpinner.setValue(-zExtentMm / 2.0);
		secondAxisMaxSpinner.setValue(zExtentMm / 2.0);

		pack();
		recomputePreview();
	}

	private void resetCurve() {
		if (curve == null) return;
		curve.clear();
		curvePanel.repaint();
		recomputePreview();
	}

	private CurvedReformatter.Params currentParams() {
		CurvedReformatter.Params params = new CurvedReformatter.Params();
		params.frameMode = (FrameMode) frameModeCombo.getSelectedItem();
		params.projectionMode = (ProjectionMode) projectionModeCombo.getSelectedItem();
		params.bandHalfWidthMm = (Double) bandHalfWidthSpinner.getValue();
		params.secondAxisMinMm = (Double) secondAxisMinSpinner.getValue();
		params.secondAxisMaxMm = (Double) secondAxisMaxSpinner.getValue();
		double inPlaneSpacing = (volume.pixelSpacingX + volume.pixelSpacingY) / 2.0;
		params.arcStepMm = Math.max(0.1, inPlaneSpacing);
		// Keep output pixels isotropic (square) regardless of the source's native Z spacing.
		// The native slice spacing (often several mm for CT) is just the source data's
		// sampling limit, not the right row pitch for the *output* raster: using it directly
		// would make rows cover more physical distance than columns, squashing the displayed/
		// exported image vertically since neither the preview JLabel nor SeriesWindow apply
		// non-square pixel aspect correction. VolumeSampler already supports sub-slice
		// trilinear interpolation, so sampling the second axis at the same fine step as the
		// arc length is valid - it just interpolates between native slices.
		params.secondAxisStepMm = params.arcStepMm;
		params.outOfBoundsValue = volume.minVal;
		return params;
	}

	private void recomputePreview() {
		boolean ready = curve != null && curve.size() >= 2;
		showResultButton.setEnabled(ready);
		if (!ready) {
			previewLabel.setIcon(null);
			previewLabel.setText("Add at least 2 centerline points to preview");
			return;
		}
		CurvedReformatter.Result result = CurvedReformatter.reformat(curve, sampler, currentParams());
		BufferedImage preview = renderGray(result.pixels, result.width, result.height, volume.minVal, volume.maxVal);
		previewLabel.setText("");
		previewLabel.setIcon(new ImageIcon(preview));
	}

	/**
	 * Shows the full-resolution result through this app's own SeriesWindow
	 * (Praparat/SlideGlass), not ij.gui.ImageWindow/ImagePlus.show() - this
	 * app never displays plain ImageJ AWT windows anywhere else, and mixing
	 * that vanilla heavyweight window/canvas into this app's own windowing
	 * left the result invisible.
	 */
	private void showFullResult() {
		if (curve == null || curve.size() < 2) return;
		CurvedReformatter.Params params = currentParams();
		CurvedReformatter.Result result = CurvedReformatter.reformat(curve, sampler, params);
		ImagePlus imp = buildResultImagePlus(result, params);
		// sortZCT=false: a single derived slice with no IPP, nothing to sort by position.
		Praparat resultPrap = new Praparat(imp, java.awt.Color.CYAN, Praparat.ViewMode.Normal, false);
		new SeriesWindow(resultPrap);
	}

	/**
	 * Builds the result ImagePlus with DICOM tags edited to reflect what it
	 * actually is: a derived/secondary, non-planar reformat.
	 * <ul>
	 * <li>ImagePositionPatient/ImageOrientationPatient are intentionally left
	 * unset - a curved/flattened reformat has no single planar position or
	 * orientation in patient space, so a fabricated value would be actively
	 * misleading (e.g. to any code that re-derives geometry from them).</li>
	 * <li>PatientID/StudyInstanceUID are copied from the source series so the
	 * result still links back to the same patient/study; SeriesInstanceUID
	 * and SOPInstanceUID are freshly minted, since this is a new, distinct
	 * derived series.</li>
	 * <li>PixelSpacing reflects the reformat's actual (anisotropic) sampling:
	 * arc-length spacing vs second-axis spacing.</li>
	 * <li>BitsAllocated/Stored/HighBit/PixelRepresentation describe exactly
	 * how the pixel array below is packed (unsigned, matching VolumeData's
	 * own raw-value convention).</li>
	 * <li>RescaleSlope/Intercept are copied as-is from the source volume's
	 * calibration, since the sampled pixel values are still in that same raw
	 * unit space (see VolumeSampler#sampleTrilinear) - no extra shift needed
	 * because PixelRepresentation is kept unsigned just like VolumeData.</li>
	 * <li>ImageType=DERIVED\SECONDARY flags this as post-processed, not an
	 * original acquisition - standard DICOM practice for this kind of
	 * reformat.</li>
	 * </ul>
	 */
	private ImagePlus buildResultImagePlus(CurvedReformatter.Result result, CurvedReformatter.Params params) {
		ImageProcessor ip = toProcessor(result);
		ImagePlus imp = new ImagePlus("Curved MPR", ip);

		// Voxel size: explicit, since the geometry can't be expressed via IPP/IOP here.
		GDicomTools.setTag(imp, 1, "0028,0030", result.pixelSpacingY + "\\" + result.pixelSpacingX); // PixelSpacing
		double sliceThicknessMm = params.bandHalfWidthMm > 0 ? 2 * params.bandHalfWidthMm : 0.0;
		GDicomTools.setTag(imp, 1, "0018,0050", String.valueOf(sliceThicknessMm)); // SliceThickness

		// Identifiers: keep patient/study linkage, mint new series/instance UIDs.
		if (sourcePatientId != null) GDicomTools.setTag(imp, 1, "0010,0020", sourcePatientId); // PatientID
		if (sourceStudyInstanceUid != null) GDicomTools.setTag(imp, 1, "0020,000D", sourceStudyInstanceUid); // StudyInstanceUID
		GDicomTools.setTag(imp, 1, "0020,000E", UIDUtils.createUID()); // SeriesInstanceUID
		GDicomTools.setTag(imp, 1, "0008,0018", UIDUtils.createUID()); // SOPInstanceUID
		if (sourceSopClassUid != null) GDicomTools.setTag(imp, 1, "0008,0016", sourceSopClassUid); // SOPClassUID

		// Bit depth / pixel representation, matching exactly how toProcessor() packed the pixels.
		boolean eightBit = ip instanceof ByteProcessor;
		GDicomTools.setTag(imp, 1, "0028,0100", eightBit ? "8" : "16"); // BitsAllocated
		GDicomTools.setTag(imp, 1, "0028,0101", eightBit ? "8" : "16"); // BitsStored
		GDicomTools.setTag(imp, 1, "0028,0102", eightBit ? "7" : "15"); // HighBit
		GDicomTools.setTag(imp, 1, "0028,0103", "0"); // PixelRepresentation: unsigned

		// Rescale: same raw unit space as the source volume (see class javadoc above).
		if (volume.calibration != null) {
			double[] coeff = volume.calibration.getCoefficients();
			if (coeff != null && coeff.length >= 2) {
				GDicomTools.setTag(imp, 1, "0028,1052", String.valueOf(coeff[0])); // RescaleIntercept
				GDicomTools.setTag(imp, 1, "0028,1053", String.valueOf(coeff[1])); // RescaleSlope
			}
		}

		// Recommended extras.
		GDicomTools.setTag(imp, 1, "0008,0008", "DERIVED\\SECONDARY"); // ImageType
		if (sourceModality != null) GDicomTools.setTag(imp, 1, "0008,0060", sourceModality); // Modality
		GDicomTools.setTag(imp, 1, "0008,103E", "Curved MPR (" + params.frameMode + ")"); // SeriesDescription
		GDicomTools.setTag(imp, 1, "0020,0011", "9901"); // SeriesNumber
		GDicomTools.setTag(imp, 1, "0020,0013", "1"); // InstanceNumber
		if (sourceWindowCenter != null) GDicomTools.setTag(imp, 1, "0028,1050", sourceWindowCenter); // WindowCenter
		if (sourceWindowWidth != null) GDicomTools.setTag(imp, 1, "0028,1051", sourceWindowWidth); // WindowWidth

		Calibration cal = CurvedReformatter.buildCalibration(volume, result);
		imp.setCalibration(cal);
		return imp;
	}

	/** Packs the result into the same raw-value width as the source volume (8-bit vs 16-bit unsigned). */
	private ImageProcessor toProcessor(CurvedReformatter.Result result) {
		if (volume.dataType == VolumeData.DataType.BYTE) {
			byte[] bytes = new byte[result.pixels.length];
			for (int i = 0; i < bytes.length; i++) {
				int v = Math.round(result.pixels[i]);
				bytes[i] = (byte) Math.max(0, Math.min(255, v));
			}
			return new ByteProcessor(result.width, result.height, bytes, null);
		}
		short[] shorts = new short[result.pixels.length];
		for (int i = 0; i < shorts.length; i++) {
			int v = Math.round(result.pixels[i]);
			v = Math.max(0, Math.min(65535, v));
			shorts[i] = (short) v; // ShortProcessor treats the bit pattern as unsigned 0-65535
		}
		return new ShortProcessor(result.width, result.height, shorts, null);
	}

	// --- slice / preview rendering helpers ---------------------------------

	private static BufferedImage renderSliceImage(VolumeData vol, int z, float winMin, float winMax) {
		float[] values = extractSlice(vol, z);
		return renderGray(values, vol.width, vol.height, winMin, winMax);
	}

	private static float[] extractSlice(VolumeData vol, int z) {
		int w = vol.width, h = vol.height;
		int sliceSize = w * h;
		int base = z * sliceSize;
		float[] out = new float[sliceSize];
		switch (vol.dataType) {
		case BYTE: {
			byte[] d = (byte[]) vol.data;
			for (int i = 0; i < sliceSize; i++) out[i] = d[base + i] & 0xFF;
			break;
		}
		case SHORT: {
			short[] d = (short[]) vol.data;
			for (int i = 0; i < sliceSize; i++) out[i] = d[base + i] & 0xFFFF;
			break;
		}
		case FLOAT: {
			float[] d = (float[]) vol.data;
			System.arraycopy(d, base, out, 0, sliceSize);
			break;
		}
		default:
			break;
		}
		return out;
	}

	private static BufferedImage renderGray(float[] values, int w, int h, float winMin, float winMax) {
		byte[] bytes = new byte[w * h];
		float range = Math.max(1e-6f, winMax - winMin);
		for (int i = 0; i < values.length; i++) {
			float t = (values[i] - winMin) / range;
			int v = Math.round(Math.max(0f, Math.min(1f, t)) * 255f);
			bytes[i] = (byte) v;
		}
		ByteProcessor bp = new ByteProcessor(w, h, bytes, null);
		return bp.getBufferedImage();
	}
}
