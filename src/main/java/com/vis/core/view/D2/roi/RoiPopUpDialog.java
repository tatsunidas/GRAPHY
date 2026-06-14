/**
 * copyright visionary imaging services, inc.
 */
package com.vis.core.view.D2.roi;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.vis.configuration.RoiDBKey;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D3.roi.SphereRoi3D;
import com.vis.db.DatabaseHandler;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageStatistics;

/**
 * ROI の情報を表示する半透明ポップアップダイアログ。 SphereRoi3D については 3D中心IPP・半径・体積を表示し、半径の編集に対応する。
 * FreeFormRoi3D についてはボクセルカウントによる実体積を表示する。 2D ROI については面積・長さ・角度などの計測値を表示する。
 *
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class RoiPopUpDialog extends JDialog {

	private Point screenPressPoint;
	private Point windowInitPoint;

	private RoiObj targetRoi;
	private Praparat pp;

	public RoiPopUpDialog(SlideGlass sg, RoiObj targetRoi, Point screenLocation) {
		super(SwingUtilities.getWindowAncestor(sg), "ROI Info", ModalityType.MODELESS);
		this.pp = sg.getPraparat();
		this.targetRoi = targetRoi;

		setUndecorated(true);
		setBackground(new Color(0, 0, 0, 140));

		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setOpaque(false);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

		// ヘッダ
		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setOpaque(false);
		JLabel titleLabel = new JLabel(" ROI Information ");
		titleLabel.setForeground(Color.CYAN);
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		headerPanel.add(titleLabel, BorderLayout.WEST);

		JButton closeBtn = new JButton("X");
		closeBtn.setOpaque(false);
		closeBtn.setContentAreaFilled(false);
		closeBtn.setForeground(Color.WHITE);
		closeBtn.setBorderPainted(false);
		closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		closeBtn.addActionListener(e -> dispose());
		headerPanel.add(closeBtn, BorderLayout.EAST);

		MouseAdapter dragListener = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				screenPressPoint = e.getLocationOnScreen();
				windowInitPoint = getLocation();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (screenPressPoint == null || windowInitPoint == null)
					return;
				Point cur = e.getLocationOnScreen();
				setLocation(windowInitPoint.x + cur.x - screenPressPoint.x,
						windowInitPoint.y + cur.y - screenPressPoint.y);
			}
		};
		headerPanel.addMouseListener(dragListener);
		headerPanel.addMouseMotionListener(dragListener);

		mainPanel.add(headerPanel, BorderLayout.NORTH);

		JPanel contentPanel = new JPanel(new GridLayout(0, 1, 5, 5));
		contentPanel.setOpaque(false);
		buildContent(contentPanel);
		mainPanel.add(contentPanel, BorderLayout.CENTER);

		add(mainPanel);
		pack();
		setLocation(screenLocation);
	}

	// ------------------------------------------------------------------
	// コンテンツ振り分け
	// ------------------------------------------------------------------

	private void buildContent(JPanel panel) {
		boolean isSphere3D = (targetRoi instanceof SphereRoi3D);
		boolean isFreeForm3D = (targetRoi instanceof com.vis.core.view.D3.roi.FreeFormRoi3D);
		String groupId = targetRoi.getProperty(RoiDBKey.RoiGroup.name());

		if (isSphere3D) {
			// SphereRoi3D
			SphereRoi3D s = (SphereRoi3D) targetRoi;
			addInfoRow(panel, "Center IPP:",
					String.format("(%.2f, %.2f, %.2f)", s.getCenterX(), s.getCenterY(), s.getCenterZ()));
			buildSphere3DContent(panel, s);

		} else if (isFreeForm3D) {
			// ==========================================================
			// ★ 新アーキテクチャ: FreeFormRoi3D
			// ==========================================================
			com.vis.core.view.D3.roi.FreeFormRoi3D ff = (com.vis.core.view.D3.roi.FreeFormRoi3D) targetRoi;
			double[] origin = ff.getOriginIpp();
			if (origin != null && origin.length >= 3) {
				addInfoRow(panel, "Origin IPP:", String.format("(%.2f, %.2f, %.2f)", origin[0], origin[1], origin[2]));
			}
			buildFreeForm3DContent(panel, ff);

		} else if (groupId != null && !groupId.isEmpty()) {
			// ==========================================================
			// レガシーな 2D グループ ROI
			// ==========================================================
			addInfoRow(panel, "Position (x,y):",
					String.format("%.1f, %.1f", targetRoi.getXBase(), targetRoi.getYBase()));
			buildGroup3DContent(panel, groupId);

		} else {
			// 通常の 2D ROI
			addInfoRow(panel, "Position (x,y):",
					String.format("%.1f, %.1f", targetRoi.getXBase(), targetRoi.getYBase()));
			build2DContent(panel);
		}
	}

	// ------------------------------------------------------------------
	// SphereRoi3D 専用コンテンツ
	// ------------------------------------------------------------------

	private void buildSphere3DContent(JPanel panel, SphereRoi3D sphere) {
		addInfoRow(panel, "Mode:", "3D Sphere");

		double radius = sphere.getRadiusMm();
		double vol = (4.0 / 3.0) * Math.PI * radius * radius * radius;

		addInfoRow(panel, "Radius:", String.format("%.2f mm", radius));
		addInfoRow(panel, "Volume (Exact):", String.format("%.2f mm³", vol));

		addSphereControlUI(panel, radius);
	}

	private void addSphereControlUI(JPanel panel, double currentRadius) {
		JPanel ctrlPanel = new JPanel(new BorderLayout(5, 0));
		ctrlPanel.setOpaque(false);

		JLabel lbl = new JLabel("New Radius (mm):");
		lbl.setForeground(Color.ORANGE);
		ctrlPanel.add(lbl, BorderLayout.WEST);

		JTextField txtRadius = new JTextField(String.format("%.2f", currentRadius), 6);
		ctrlPanel.add(txtRadius, BorderLayout.CENTER);

		JButton btnApply = new JButton("Apply");
		btnApply.addActionListener(e -> {
			try {
				double newR = Double.parseDouble(txtRadius.getText().trim());
				if (newR <= 0) {
					txtRadius.setText("Error: > 0");
					return;
				}
				if (!(targetRoi instanceof SphereRoi3D))
					return;

				SphereRoi3D sphere = (SphereRoi3D) targetRoi;
				sphere.setRadiusMm(newR);

				if (DatabaseHandler.getInstance() != null) {
					DatabaseHandler.getInstance().insertRoi(sphere.readContext());
				}
				if (pp != null)
					pp.repaint();
				dispose();

			} catch (NumberFormatException ex) {
				txtRadius.setText("Error");
			}
		});
		ctrlPanel.add(btnApply, BorderLayout.EAST);

		panel.add(ctrlPanel);
	}

	// ------------------------------------------------------------------
	// ★ 新規追加: FreeFormRoi3D 専用コンテンツ (ボクセルカウント)
	// ------------------------------------------------------------------

	private void buildFreeForm3DContent(JPanel panel, com.vis.core.view.D3.roi.FreeFormRoi3D ff) {
		String groupId = ff.getProperty(RoiDBKey.RoiGroup.name());
		addInfoRow(panel, "Mode:", "3D FreeForm (Group: " + (groupId != null ? groupId : "N/A") + ")");

		// 1ボクセルあたりの体積 (mm³)
		double[] sp = ff.getSpacing();
		double voxelVolume = sp[0] * sp[1] * sp[2];

		int[] dims = ff.getDimensions();
		long voxelCount = 0;

		// Zスライスごとに有効ボクセルをカウント
		for (int k = 0; k < dims[2]; k++) {
			ij.process.ByteProcessor bp = ff.getMaskAsBytes(k);
			if (bp != null) {
				byte[] pixels = (byte[]) bp.getPixels();
				for (byte b : pixels) {
					if (b != 0)
						voxelCount++;
				}
			}
		}

		double totalVolume = voxelCount * voxelVolume;

		addInfoRow(panel, "Volume:", String.format("%.2f mm³", totalVolume));
		addInfoRow(panel, "Voxel Count:", String.valueOf(voxelCount));
		addInfoRow(panel, "Volume Dim:", String.format("%d x %d x %d", dims[0], dims[1], dims[2]));
		addInfoRow(panel, "Spacing:", String.format("%.2f x %.2f x %.2f", sp[0], sp[1], sp[2]));
		// ==========================================================
		// ★ 追記: インターフェースを実装しているため、キャストして渡すだけで動きます
		// ==========================================================
		if (ff instanceof com.vis.core.view.D3.roi.RoiObj3D) {
			addMeshStatsSection(panel, (com.vis.core.view.D3.roi.RoiObj3D) ff);
		}
	}

	// ------------------------------------------------------------------
	// レガシーな グループベース 3D ROI
	// ------------------------------------------------------------------

	private void buildGroup3DContent(JPanel panel, String groupId) {
		addInfoRow(panel, "Mode:", "Legacy 2D Bundle (Group: " + groupId + ")");

		List<RoiObj> groupRois = new ArrayList<>();
		if (groupId != null && pp != null && pp.getAllSlides() != null) {
			for (SlideGlass s : pp.getAllSlides().values()) {
				if (s == null)
					continue;
				for (RoiObj r : s.getRois()) {
					if (groupId.equals(r.getProperty(RoiDBKey.RoiGroup.name()))) {
						groupRois.add(r);
					}
				}
			}
		}

		if (groupRois.isEmpty()) {
			addInfoRow(panel, "Info:", "No group ROIs found on slices.");
			return;
		}

		boolean isArea = true, isLine = true, isPoint = true, isAngle = true;
		for (RoiObj r : groupRois) {
			if (!r.isArea())
				isArea = false;
			if (!(r.getType() == RoiType.LINE.id() || r.getType() == RoiType.POLYLINE.id()
					|| r.getType() == RoiType.FREELINE.id()))
				isLine = false;
			if (r.getType() != RoiType.POINT.id() && r.getType() != RoiType.MULTIPOINT.id())
				isPoint = false;
			if (r.getType() != RoiType.ANGLE.id())
				isAngle = false;
		}

		boolean isMixed = !isArea && !isLine && !isPoint && !isAngle;
		if (isMixed) {
			addInfoRow(panel, "Warning:", "Mixed ROI types in Group.");
			return;
		}

		if (isArea) {
			double totalVol = 0, totalArea = 0, sumMeanArea = 0;
			double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
			for (RoiObj r : groupRois) {
				ImageStatistics stats = getFreshStats(r);
				if (stats != null) {
					double thickness = getSliceThickness(r.getSlideGlass());
					totalVol += stats.area * thickness;
					totalArea += stats.area;
					sumMeanArea += stats.mean * stats.area;
					if (stats.min < min)
						min = stats.min;
					if (stats.max > max)
						max = stats.max;
				}
			}
			double avgMean = (totalArea > 0) ? sumMeanArea / totalArea : 0;
			addInfoRow(panel, "Volume (Integral):", String.format("%.2f mm³", totalVol));
			addInfoRow(panel, "Avg Mean:", String.format("%.2f", avgMean));
			addInfoRow(panel, "Global Min / Max:", String.format("%.2f / %.2f", min, max));
			addInfoRow(panel, "Slices Count:", String.valueOf(groupRois.size()));

		} else if (isLine) {
			double totalLen = 0, minLen = Double.MAX_VALUE, maxLen = -Double.MAX_VALUE;
			for (RoiObj r : groupRois) {
				double l = getFreshLength(r);
				totalLen += l;
				if (l < minLen)
					minLen = l;
				if (l > maxLen)
					maxLen = l;
			}
			double avgLen = groupRois.isEmpty() ? 0 : totalLen / groupRois.size();
			addInfoRow(panel, "Total Length:", String.format("%.2f", totalLen));
			addInfoRow(panel, "Avg / Min / Max:", String.format("%.2f / %.2f / %.2f", avgLen, minLen, maxLen));
			addInfoRow(panel, "Lines Count:", String.valueOf(groupRois.size()));

		} else if (isPoint) {
			int totalPts = 0;
			for (RoiObj r : groupRois) {
				ij.process.FloatPolygon fp = r.getFloatPolygon();
				if (fp != null)
					totalPts += fp.npoints;
			}
			addInfoRow(panel, "Total Points:", String.valueOf(totalPts));
			addInfoRow(panel, "Point Sets Count:", String.valueOf(groupRois.size()));

		} else if (isAngle) {
			double totalAng = 0, minAng = Double.MAX_VALUE, maxAng = -Double.MAX_VALUE;
			for (RoiObj r : groupRois) {
				try {
					double a = Double.parseDouble(getFreshAngle(r));
					totalAng += a;
					if (a < minAng)
						minAng = a;
					if (a > maxAng)
						maxAng = a;
				} catch (Exception ignored) {
				}
			}
			double avgAng = groupRois.isEmpty() ? 0 : totalAng / groupRois.size();
			addInfoRow(panel, "Avg Angle:", String.format("%.2f°", avgAng));
			addInfoRow(panel, "Min / Max Angle:", String.format("%.2f° / %.2f°", minAng, maxAng));
			addInfoRow(panel, "Angles Count:", String.valueOf(groupRois.size()));
		}
	}

	// ------------------------------------------------------------------
	// 2D ROI コンテンツ
	// ------------------------------------------------------------------

	private void build2DContent(JPanel panel) {
		addInfoRow(panel, "Mode:", "2D Single Slice");
		int type = targetRoi.getType();

		if (targetRoi.isArea()) {
			ImageStatistics stats = getFreshStats(targetRoi);
			if (stats != null) {
				addInfoRow(panel, "Area:", String.format("%.2f", stats.area));
				addInfoRow(panel, "Mean / Median:", String.format("%.2f / %.2f", stats.mean, stats.median));
				addInfoRow(panel, "Min / Max:", String.format("%.2f / %.2f", stats.min, stats.max));
				addInfoRow(panel, "StdDev (SD):", String.format("%.2f", stats.stdDev));
				addInfoRow(panel, "Major / Minor Axis:", String.format("%.2f / %.2f", stats.major, stats.minor));
			}
		} else if (type == RoiType.LINE.id() || type == RoiType.POLYLINE.id() || type == RoiType.FREELINE.id()) {
			addInfoRow(panel, "Length:", String.format("%.2f", getFreshLength(targetRoi)));
		} else if (type == RoiType.ANGLE.id()) {
			addInfoRow(panel, "Angle:", getFreshAngle(targetRoi) + "°");
		} else if (type == RoiType.POINT.id() || type == RoiType.MULTIPOINT.id()) {
			ij.process.FloatPolygon fp = targetRoi.getFloatPolygon();
			int pts = (fp != null) ? fp.npoints : 0;
			addInfoRow(panel, "Points Count:", String.valueOf(pts));
		} else {
			addInfoRow(panel, "Info:", "No calculable stats for this ROI.");
		}
	}

	// ==========================================================
	// ★ 追加: RoiObj3D を用いて非同期でメッシュ解析を行う共通UIセクション
	// ==========================================================
	private void addMeshStatsSection(JPanel panel, com.vis.core.view.D3.roi.RoiObj3D roi3d) {
		int[] dims = roi3d.getDimensions();
		if (dims[0] == 0)
			return;

		JPanel meshPanel = new JPanel(new BorderLayout(5, 5));
		meshPanel.setOpaque(false);
		meshPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.DARK_GRAY)); // 上部に区切り線

		JLabel lblMeshResult = new JLabel(" Mesh Stats: Not Calculated");
		lblMeshResult.setForeground(Color.LIGHT_GRAY);
		lblMeshResult.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

		JButton btnCalcMesh = new JButton("Calc Mesh Volume");
		btnCalcMesh.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		btnCalcMesh.addActionListener(e -> {
			btnCalcMesh.setEnabled(false);
			btnCalcMesh.setText("Calculating...");

			new javax.swing.SwingWorker<double[], Void>() {
				@Override
				protected double[] doInBackground() throws Exception {
					// インターフェース経由で、どのROIからでも一発で VolumeData を取得
					com.vis.core.view.D3.ui.VolumeData vData = roi3d.getVolumeDataForMesh();
					if (vData == null)
						return null;

					// Marching Cubes でポリゴン化
					com.vis.core.view.D3.ui.MeshData mesh = com.vis.core.view.D3.ui.MarchingCubes.generateMesh(vData,
							127.5f);
					if (mesh == null || mesh.vertices == null)
						return null;

					// ラプラシアンスムージングを適用
					com.vis.core.view.D3.ui.MarchingCubes.applyLaplacianSmoothing(mesh, 2, 0.5f);

					// 体積と表面積を計算
					return calculateMeshVolumeAndSurfaceArea(mesh);
				}

				@Override
				protected void done() {
					try {
						double[] result = get();
						if (result != null) {
							lblMeshResult.setText(
									String.format("<html>Mesh Vol: <b>%.2f mm³</b><br>Surface Area: %.2f mm²</html>",
											result[0], result[1]));
							lblMeshResult.setForeground(Color.CYAN); // 計算成功時はシアン色で強調
						} else {
							lblMeshResult.setText(" Mesh Stats: Failed");
						}
					} catch (Exception ex) {
						lblMeshResult.setText(" Mesh Stats: Error");
					}
					btnCalcMesh.setVisible(false); // 計算完了後はボタンを非表示にしてスッキリさせる
				}
			}.execute();
		});

		meshPanel.add(lblMeshResult, BorderLayout.CENTER);
		meshPanel.add(btnCalcMesh, BorderLayout.EAST);
		panel.add(meshPanel);
	}

	// ==========================================================
	// ★ 追加: メッシュの三角形ポリゴンから正確な体積と表面積を算出する
	// ==========================================================
	private double[] calculateMeshVolumeAndSurfaceArea(com.vis.core.view.D3.ui.MeshData mesh) {
		float[] v = mesh.vertices;
		int[] ind = mesh.indices;
		double volume = 0.0;
		double surfaceArea = 0.0;

		for (int i = 0; i < ind.length; i += 3) {
			int i1 = ind[i] * 3;
			int i2 = ind[i + 1] * 3;
			int i3 = ind[i + 2] * 3;

			double x1 = v[i1], y1 = v[i1 + 1], z1 = v[i1 + 2];
			double x2 = v[i2], y2 = v[i2 + 1], z2 = v[i2 + 2];
			double x3 = v[i3], y3 = v[i3 + 1], z3 = v[i3 + 2];

			// 1. ダイバージェンス定理（符号付き四面体体積の総和）
			volume += (x1 * y2 * z3 - x1 * y3 * z2 - x2 * y1 * z3 + x2 * y3 * z1 + x3 * y1 * z2 - x3 * y2 * z1);

			// 2. 外積による三角形の外表面積の計算
			double v1x = x2 - x1, v1y = y2 - y1, v1z = z2 - z1;
			double v2x = x3 - x1, v2y = y3 - y1, v2z = z3 - z1;
			double cx = v1y * v2z - v1z * v2y;
			double cy = v1z * v2x - v1x * v2z;
			double cz = v1x * v2y - v1y * v2x;
			surfaceArea += Math.sqrt(cx * cx + cy * cy + cz * cz);
		}

		return new double[] { Math.abs(volume) / 6.0, surfaceArea / 2.0 };
	}

	// ------------------------------------------------------------------
	// ユーティリティ
	// ------------------------------------------------------------------

	private ImageStatistics getFreshStats(RoiObj roi) {
		ImagePlus imp = roi.getSlideGlass() != null ? roi.getSlideGlass().getOriginalImage() : null;
		if (imp != null) {
			Roi r = new RoiConverter().convert2Roi(roi);
			imp.setRoi(r);
			int mOptions = ij.measure.Measurements.AREA | ij.measure.Measurements.MEAN | ij.measure.Measurements.STD_DEV
					| ij.measure.Measurements.MIN_MAX | ij.measure.Measurements.MEDIAN
					| ij.measure.Measurements.ELLIPSE;
			return imp.getStatistics(mOptions);
		}
		return roi.getStatistics();
	}

	private double getFreshLength(RoiObj roi) {
		return roi.getLength();
	}

	private String getFreshAngle(RoiObj roi) {
		ij.process.FloatPolygon fp = roi.getFloatPolygon();
		if (fp != null && fp.npoints >= 3) {
			double dx1 = fp.xpoints[0] - fp.xpoints[1];
			double dy1 = fp.ypoints[0] - fp.ypoints[1];
			double dx2 = fp.xpoints[2] - fp.xpoints[1];
			double dy2 = fp.ypoints[2] - fp.ypoints[1];
			double angle1 = Math.atan2(dy1, dx1) * 180.0 / Math.PI;
			double angle2 = Math.atan2(dy2, dx2) * 180.0 / Math.PI;
			double degrees = Math.abs(angle1 - angle2);
			if (degrees > 180.0)
				degrees = 360.0 - degrees;
			return String.format("%.2f", degrees);
		}
		return "0.00";
	}

	private double getSliceThickness(SlideGlass slide) {
		if (slide == null)
			return 1.0;
		ij.measure.Calibration cal = slide.getOriginalCalibration();
		if (cal != null && cal.pixelDepth > 0)
			return cal.pixelDepth;
		com.vis.dicom.DicomObject header = slide.getHeader();
		if (header != null) {
			return header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices,
					header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
		}
		return 1.0;
	}

	private void addInfoRow(JPanel panel, String label, String value) {
		JPanel row = new JPanel(new BorderLayout(10, 0));
		row.setOpaque(false);
		JLabel lbl = new JLabel(label);
		lbl.setForeground(Color.LIGHT_GRAY);
		JLabel val = new JLabel(value);
		val.setForeground(Color.WHITE);
		row.add(lbl, BorderLayout.WEST);
		row.add(val, BorderLayout.EAST);
		panel.add(row);
	}
}