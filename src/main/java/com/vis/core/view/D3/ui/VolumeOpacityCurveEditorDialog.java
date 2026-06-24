/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.vis.core.view.D3.ui.OpacityCurvePanel.ControlPoint;

/**
 * Lets the user manually shape the opacity-vs-value curve applied to the
 * volume in VR/MIP/Ortho, against a histogram of the currently loaded
 * volume's voxel values. Color is handled separately by the Control Panel's
 * "Color Map (LUT)" picker; this dialog only edits opacity.
 *
 * The control point list passed in is mutated in place and shared with the
 * caller (Viewer3DMain), so reopening the dialog within the same viewer
 * session shows the last edited curve instead of resetting every time.
 */
@SuppressWarnings("serial")
public class VolumeOpacityCurveEditorDialog extends JDialog {

	private final GLCanvas canvas;
	private final OpacityCurvePanel curvePanel;

	public VolumeOpacityCurveEditorDialog(Frame owner, GLCanvas canvas, List<ControlPoint> sharedPoints) {
		super(owner, "Volume Opacity Curve", false);
		this.canvas = canvas;

		if (sharedPoints.isEmpty()) {
			sharedPoints.add(new ControlPoint(0, 0.0f));
			sharedPoints.add(new ControlPoint(255, 1.0f));
		}

		VolumeData vol = canvas.getVolumeData();
		int[] histogram = (vol != null) ? vol.computeHistogram(256) : new int[256];
		float dataMin = (vol != null) ? vol.minVal : 0f;
		float dataMax = (vol != null) ? vol.maxVal : 255f;
		ij.measure.Calibration calibration = (vol != null) ? vol.calibration : null;

		curvePanel = new OpacityCurvePanel(histogram, dataMin, dataMax, sharedPoints, calibration);
		curvePanel.setOnChange(this::applyCurve);

		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel hint = new JLabel(
				"<html>Drag a point to move it &middot; Double-click empty space to add a point &middot; "
						+ "Right-click a point to remove it</html>",
				SwingConstants.LEFT);
		root.add(hint, BorderLayout.NORTH);
		root.add(curvePanel, BorderLayout.CENTER);

		JPanel buttons = new JPanel();
		JButton reset = new JButton("Reset");
		reset.addActionListener(e -> resetToLinear(sharedPoints));
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		buttons.add(reset);
		buttons.add(close);
		root.add(buttons, BorderLayout.SOUTH);

		setContentPane(root);
		pack();
		setLocationRelativeTo(owner);

		// Apply once on open so the view matches what the editor shows,
		// even if sharedPoints was just defaulted above.
		applyCurve();
	}

	private void resetToLinear(List<ControlPoint> points) {
		points.clear();
		points.add(new ControlPoint(0, 0.0f));
		points.add(new ControlPoint(255, 1.0f));
		curvePanel.repaint();
		applyCurve();
	}

	private void applyCurve() {
		canvas.applyOpacityCurve(curvePanel.resolveTo256());
	}
}
