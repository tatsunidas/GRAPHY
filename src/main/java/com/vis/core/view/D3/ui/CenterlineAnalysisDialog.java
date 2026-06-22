/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vis.core.centerline.CenterlineBranch;
import com.vis.core.centerline.CenterlineGraph;
import com.vis.core.centerline.CenterlineNode;
import com.vis.core.centerline.SkeletonGraphExtractor;
import com.vis.core.centerline.Skeletonizer3D;
import com.vis.core.slicer.Centerline3D;
import com.vis.core.slicer.Centerline3D.FrameMode;
import com.vis.core.slicer.CurvedReformatter;
import com.vis.core.slicer.StraightenedVolumeBuilder;
import com.vis.core.slicer.VolumeSampler;
import com.vis.core.view.D3.roi.FreeFormRoi3D;

import ij.process.ByteProcessor;

/**
 * General-purpose 3D centerline analysis: extracts a branching skeleton
 * from a {@link FreeFormRoi3D} mask (mesh sources funnel through
 * {@link MeshVoxelizer} the same way), lets the user pick either a single
 * branch or a node-to-node path across bifurcations (vascular CPR), and
 * shows the result as a 2D curved MPR/straighten reformat or as a fully
 * straightened 3D volume in a new {@link Viewer3DMain} window.
 *
 * "Pruning" here is non-destructive: {@link CenterlineGraph} always keeps
 * every branch; picking a branch/path just asks it for the matching
 * {@link Centerline3D} on demand (see {@link CenterlineGraph#extractBranch}
 * / {@link CenterlineGraph#extractPath}).
 */
@SuppressWarnings("serial")
public class CenterlineAnalysisDialog extends JDialog {

	private final GLCanvas canvas;

	private CenterlineGraph fullGraph; // as extracted, never modified - lets pruning be redone/reset
	private CenterlineGraph graph; // current working graph (== fullGraph, or a pruned derivative of it)
	private VolumeSampler mainSampler; // over canvas.getVolumeData(), used for rendering + CPR/Straighten sampling
	private Centerline3D selectedCurve;

	private JComboBox<RoiItem> roiCombo;
	private JSpinner simplifyEpsilonSpinner;
	private JButton extractButton;
	private JLabel statusLabel;
	private JSpinner pruneLengthSpinner;
	private DefaultListModel<BranchItem> branchListModel;
	private JList<BranchItem> branchList;
	private JComboBox<NodeItem> nodeACombo;
	private JComboBox<NodeItem> nodeBCombo;
	private JComboBox<FrameMode> frameModeCombo;
	private JButton show2DButton;
	private JButton show3DButton;

	public static void showDialog(GLCanvas canvas, Window owner) {
		CenterlineAnalysisDialog dlg = new CenterlineAnalysisDialog(canvas, owner);
		dlg.setVisible(true);
	}

	private CenterlineAnalysisDialog(GLCanvas canvas, Window owner) {
		super(owner, "Centerline Analysis", ModalityType.MODELESS);
		this.canvas = canvas;
		initComponents();
		populateRoiCombo();
		setLocationRelativeTo(owner);
	}

	private void initComponents() {
		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		root.add(buildSourcePanel(), BorderLayout.NORTH);

		branchListModel = new DefaultListModel<>();
		branchList = new JList<>(branchListModel);
		branchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		branchList.addListSelectionListener(e -> onBranchSelected());
		JScrollPane branchScroll = new JScrollPane(branchList);
		branchScroll.setBorder(BorderFactory.createTitledBorder("Branches (select one to prune to it)"));
		branchScroll.setPreferredSize(new Dimension(320, 240));
		root.add(branchScroll, BorderLayout.CENTER);

		root.add(buildPathAndActionsPanel(), BorderLayout.SOUTH);

		setContentPane(root);
		pack();
	}

	private JPanel buildSourcePanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0;
		gbc.gridy = 0;

		roiCombo = new JComboBox<>();
		panel.add(new JLabel("Source ROI:"), gbc);
		gbc.gridx++;
		panel.add(roiCombo, gbc);

		gbc.gridx++;
		simplifyEpsilonSpinner = new JSpinner(new SpinnerNumberModel(0.5, 0.0, 50.0, 0.1));
		panel.add(new JLabel("Simplify (mm):"), gbc);
		gbc.gridx++;
		panel.add(simplifyEpsilonSpinner, gbc);

		gbc.gridx++;
		extractButton = new JButton("Extract Centerline");
		extractButton.addActionListener(e -> runExtraction());
		panel.add(extractButton, gbc);

		gbc.gridx++;
		statusLabel = new JLabel(" ");
		panel.add(statusLabel, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		pruneLengthSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.0, 200.0, 0.5));
		panel.add(new JLabel("Prune spurs shorter than (mm):"), gbc);
		gbc.gridx++;
		panel.add(pruneLengthSpinner, gbc);

		gbc.gridx++;
		JButton pruneButton = new JButton("Apply Pruning");
		pruneButton.addActionListener(e -> applyPruning());
		panel.add(pruneButton, gbc);

		gbc.gridx++;
		JButton resetPruneButton = new JButton("Reset Pruning");
		resetPruneButton.addActionListener(e -> resetPruning());
		panel.add(resetPruneButton, gbc);

		return panel;
	}

	private void applyPruning() {
		if (fullGraph == null) {
			JOptionPane.showMessageDialog(this, "Extract a centerline first.");
			return;
		}
		double minLengthMm = (Double) pruneLengthSpinner.getValue();
		graph = (minLengthMm > 0) ? fullGraph.pruneShortLeafBranches(minLengthMm) : fullGraph;
		refreshGraphUi();
	}

	private void resetPruning() {
		if (fullGraph == null) return;
		graph = fullGraph;
		refreshGraphUi();
	}

	/** Re-syncs the branch list/node combos/3D overlay/selection state with whatever `graph` currently is. */
	private void refreshGraphUi() {
		canvas.setCenterlineGraph(graph, mainSampler);
		populateBranchList();
		populateNodeCombos();
		selectedCurve = null;
		canvas.setSelectedCenterlineCurve(null);
		canvas.setSelectedCenterlineBranches(new HashSet<>());
		canvas.setSelectedCenterlineNodes(new HashSet<>());
		show2DButton.setEnabled(false);
		show3DButton.setEnabled(false);
		statusLabel.setText("Showing " + graph.getBranches().size() + " branches.");
	}

	private JPanel buildPathAndActionsPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0;
		gbc.gridy = 0;

		nodeACombo = new JComboBox<>();
		nodeBCombo = new JComboBox<>();
		panel.add(new JLabel("Path node A:"), gbc);
		gbc.gridx++;
		panel.add(nodeACombo, gbc);
		gbc.gridx++;
		panel.add(new JLabel("node B:"), gbc);
		gbc.gridx++;
		panel.add(nodeBCombo, gbc);

		gbc.gridx++;
		JButton extractPathButton = new JButton("Extract Path");
		extractPathButton.addActionListener(e -> onExtractPath());
		panel.add(extractPathButton, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		frameModeCombo = new JComboBox<>(FrameMode.values());
		frameModeCombo.setSelectedItem(FrameMode.ROTATION_MINIMIZING);
		panel.add(new JLabel("Frame mode:"), gbc);
		gbc.gridx++;
		panel.add(frameModeCombo, gbc);

		gbc.gridx++;
		show2DButton = new JButton("Show CPR / Straighten (2D)");
		show2DButton.setEnabled(false);
		show2DButton.addActionListener(e -> show2D());
		panel.add(show2DButton, gbc);

		gbc.gridx++;
		show3DButton = new JButton("Show Straightened Volume (3D)");
		show3DButton.setEnabled(false);
		show3DButton.addActionListener(e -> show3D());
		panel.add(show3DButton, gbc);

		gbc.gridx++;
		JButton close = new JButton("Close");
		close.addActionListener(e -> {
			canvas.clearCenterlineGraph();
			dispose();
		});
		panel.add(close, gbc);

		return panel;
	}

	private void populateRoiCombo() {
		roiCombo.removeAllItems();
		List<FreeFormRoi3D> rois = canvas.getAllRois();
		if (rois == null) return;
		for (int i = 0; i < rois.size(); i++) {
			roiCombo.addItem(new RoiItem(i, rois.get(i)));
		}
	}

	private void runExtraction() {
		RoiItem item = (RoiItem) roiCombo.getSelectedItem();
		VolumeData mainVol = canvas.getVolumeData();
		if (item == null || mainVol == null) {
			JOptionPane.showMessageDialog(this, "Load a volume and at least one ROI first.");
			return;
		}

		extractButton.setEnabled(false);
		statusLabel.setText("Extracting centerline...");
		double epsilon = (Double) simplifyEpsilonSpinner.getValue();
		FreeFormRoi3D roi = item.roi;

		// Skeletonization cost scales with voxel count, and getVolumeDataForMesh() sizes its
		// grid to the whole parent series rather than the drawn ROI - crop to the ROI's own
		// bounding box first (cheap) so the expensive thinning step only sees what it needs to.
		// Runs off the EDT so the dialog doesn't freeze while this is in progress.
		new Thread(() -> {
			CenterlineGraph builtGraph = null;
			VolumeSampler builtMainSampler = null;
			Exception failure = null;
			try {
				VolumeData roiVol = buildRoiVolumeData(roi).cropToOccupiedBoundingBox(2);
				VolumeSampler roiSampler = new VolumeSampler(roiVol);
				byte[] skeleton = Skeletonizer3D.skeletonizeMask((byte[]) roiVol.data, roiVol.width, roiVol.height,
						roiVol.depth);
				builtGraph = SkeletonGraphExtractor.extract(skeleton, roiVol.width, roiVol.height, roiVol.depth,
						roiSampler, epsilon);
				builtMainSampler = new VolumeSampler(mainVol);
			} catch (Exception ex) {
				failure = ex;
			}

			final CenterlineGraph finalGraph = builtGraph;
			final VolumeSampler finalMainSampler = builtMainSampler;
			final Exception finalFailure = failure;
			SwingUtilities.invokeLater(() -> {
				extractButton.setEnabled(true);
				if (finalFailure != null) {
					finalFailure.printStackTrace();
					statusLabel.setText("Failed.");
					JOptionPane.showMessageDialog(this, "Centerline extraction failed: " + finalFailure);
					return;
				}
				fullGraph = finalGraph;
				graph = finalGraph;
				mainSampler = finalMainSampler;
				refreshGraphUi();
			});
		}, "CenterlineExtraction").start();
	}

	/** Fills in the spatial calibration getVolumeDataForMesh() doesn't set, from the ROI's own RoiObj3D geometry. */
	private VolumeData buildRoiVolumeData(FreeFormRoi3D roi) {
		VolumeData vol = roi.getVolumeDataForMesh();
		vol.startIpp = roi.getOriginIpp();
		vol.iop = roi.getIop();
		double[] spacing = roi.getSpacing();
		double[] iop = vol.iop;
		double nx = iop[1] * iop[5] - iop[2] * iop[4];
		double ny = iop[2] * iop[3] - iop[0] * iop[5];
		double nz = iop[0] * iop[4] - iop[1] * iop[3];
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		double sliceSpacing = (spacing != null && spacing.length >= 3) ? spacing[2] : vol.sliceThickness;
		vol.stepZ = len > 1e-9
				? new double[] { nx / len * sliceSpacing, ny / len * sliceSpacing, nz / len * sliceSpacing }
				: new double[] { 0, 0, sliceSpacing };
		return vol;
	}

	private void populateBranchList() {
		branchListModel.clear();
		for (CenterlineBranch b : graph.getBranches()) {
			branchListModel.addElement(new BranchItem(b));
		}
	}

	private void populateNodeCombos() {
		nodeACombo.removeAllItems();
		nodeBCombo.removeAllItems();
		for (CenterlineNode n : graph.getNodes()) {
			NodeItem item = new NodeItem(n);
			nodeACombo.addItem(item);
			nodeBCombo.addItem(item);
		}
	}

	private void onBranchSelected() {
		BranchItem item = branchList.getSelectedValue();
		if (item == null || graph == null) return;
		selectedCurve = graph.extractBranch(item.branch.getId());
		Set<Integer> branchIds = new HashSet<>();
		branchIds.add(item.branch.getId());
		canvas.setSelectedCenterlineBranches(branchIds);
		Set<Integer> nodeIds = new HashSet<>();
		nodeIds.add(item.branch.getStartNodeId());
		nodeIds.add(item.branch.getEndNodeId());
		canvas.setSelectedCenterlineNodes(nodeIds);
		canvas.setSelectedCenterlineCurve(selectedCurve);
		show2DButton.setEnabled(true);
		show3DButton.setEnabled(true);
	}

	private void onExtractPath() {
		NodeItem a = (NodeItem) nodeACombo.getSelectedItem();
		NodeItem b = (NodeItem) nodeBCombo.getSelectedItem();
		if (a == null || b == null || graph == null) return;
		if (a.node.getId() == b.node.getId()) {
			JOptionPane.showMessageDialog(this, "Node A and node B must be different.");
			return;
		}
		try {
			Centerline3D path = graph.extractPath(a.node.getId(), b.node.getId());
			if (path.size() < 2) {
				JOptionPane.showMessageDialog(this, "No path between the selected nodes.");
				return;
			}
			selectedCurve = path;
			branchList.clearSelection();
			Set<Integer> nodeIds = new HashSet<>();
			nodeIds.add(a.node.getId());
			nodeIds.add(b.node.getId());
			canvas.setSelectedCenterlineNodes(nodeIds);
			canvas.setSelectedCenterlineBranches(new HashSet<>());
			canvas.setSelectedCenterlineCurve(selectedCurve);
			show2DButton.setEnabled(true);
			show3DButton.setEnabled(true);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "No path between the selected nodes: " + ex.getMessage());
		}
	}

	private CurvedReformatter.Params build2DParams() {
		CurvedReformatter.Params params = new CurvedReformatter.Params();
		params.frameMode = (FrameMode) frameModeCombo.getSelectedItem();
		VolumeData vol = canvas.getVolumeData();
		double inPlaneSpacing = (vol.pixelSpacingX + vol.pixelSpacingY) / 2.0;
		params.arcStepMm = Math.max(0.1, inPlaneSpacing);
		params.secondAxisStepMm = params.arcStepMm;
		params.secondAxisMinMm = -30.0;
		params.secondAxisMaxMm = 30.0;
		params.outOfBoundsValue = vol.minVal;
		return params;
	}

	private void show2D() {
		if (!hasUsableSelectedCurve()) return;
		CurvedReformatter.Result result = CurvedReformatter.reformat(selectedCurve, mainSampler, build2DParams());
		VolumeData vol = canvas.getVolumeData();
		BufferedImage image = renderGray(result.pixels, result.width, result.height, vol.minVal, vol.maxVal);

		JDialog win = new JDialog(this, "Centerline CPR / Straighten", ModalityType.MODELESS);
		JLabel label = new JLabel(new ImageIcon(image));
		JScrollPane scroll = new JScrollPane(label);
		scroll.setPreferredSize(new Dimension(Math.min(image.getWidth() + 40, 1200),
				Math.min(image.getHeight() + 40, 900)));
		win.setContentPane(scroll);
		win.pack();
		win.setLocationRelativeTo(this);
		win.setVisible(true);
	}

	/** Defense in depth: the UI should already prevent a <2-point curve from being selected. */
	private boolean hasUsableSelectedCurve() {
		if (selectedCurve == null || selectedCurve.size() < 2 || mainSampler == null) {
			JOptionPane.showMessageDialog(this, "Select a branch or extract a path first.");
			return false;
		}
		return true;
	}

	private void show3D() {
		if (!hasUsableSelectedCurve()) return;
		StraightenedVolumeBuilder.Params params = new StraightenedVolumeBuilder.Params();
		params.frameMode = (FrameMode) frameModeCombo.getSelectedItem();
		VolumeData sourceVol = canvas.getVolumeData();
		double inPlaneSpacing = (sourceVol.pixelSpacingX + sourceVol.pixelSpacingY) / 2.0;
		params.arcStepMm = Math.max(0.1, inPlaneSpacing);
		params.radialStepMm = params.arcStepMm;
		params.radiusMm = 30.0;
		params.outOfBoundsValue = sourceVol.minVal;

		VolumeData straightened = StraightenedVolumeBuilder.build(selectedCurve, mainSampler, sourceVol, params);

		Viewer3DMain frame = new Viewer3DMain();
		frame.setVisible(true);
		frame.revalidate();
		frame.repaint();

		javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
			if (frame.canvas != null && frame.canvas.isDisplayable() && frame.canvas.isShowing()) {
				frame.canvas.render();
				frame.canvas.repaint();
			}
		});
		timer.setRepeats(true);
		timer.start();

		frame.canvas.setVolumeData(straightened);
	}

	private static BufferedImage renderGray(float[] values, int w, int h, float winMin, float winMax) {
		byte[] bytes = new byte[w * h];
		float range = Math.max(1e-6f, winMax - winMin);
		for (int i = 0; i < values.length; i++) {
			float t = (values[i] - winMin) / range;
			bytes[i] = (byte) Math.round(Math.max(0f, Math.min(1f, t)) * 255f);
		}
		return new ByteProcessor(w, h, bytes, null).getBufferedImage();
	}

	private static final class RoiItem {
		final int index;
		final FreeFormRoi3D roi;

		RoiItem(int index, FreeFormRoi3D roi) {
			this.index = index;
			this.roi = roi;
		}

		@Override
		public String toString() {
			return String.format("ROI #%d (%.1f mm³)", index, roi.getCalculatedVolumeMm3());
		}
	}

	private static final class BranchItem {
		final CenterlineBranch branch;

		BranchItem(CenterlineBranch branch) {
			this.branch = branch;
		}

		@Override
		public String toString() {
			return String.format("Branch #%d: node %d -> node %d (%.1f mm)", branch.getId(), branch.getStartNodeId(),
					branch.getEndNodeId(), branch.getLengthMm());
		}
	}

	private static final class NodeItem {
		final CenterlineNode node;

		NodeItem(CenterlineNode node) {
			this.node = node;
		}

		@Override
		public String toString() {
			String kind = node.getDegree() == 1 ? "endpoint" : node.getDegree() >= 3 ? "bifurcation" : "mid";
			return String.format("Node #%d (%s, deg=%d)", node.getId(), kind, node.getDegree());
		}
	}
}
