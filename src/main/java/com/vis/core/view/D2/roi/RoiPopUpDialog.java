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
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D3.roi.SphereRoi3D;
import com.vis.db.DatabaseHandler;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageStatistics;

/**
 * ・スライス上でROIを右クリックして、Menuから表示できるようにします。(ROI専用の右クリックメニューにする)
 * ・ダイアログ及びパネルは半透明とし、ダイアログ特有のWindowの枠部分は非表示にします。 ・表示する内容は、RoiのTypeを条件分岐して作成します。
 * ・2D-ROIの場合は、Roiのタイプを識別して、以下の情報を表示します。 - 座標位置（Boundsのx, y） -
 * 計測値：Areaタイプの場合（AreaタイプROIなら、面積、平均値、中央値、最小値、最大値、IQR、SD、長軸、短軸） -
 * 計測値：Lineタイプの場合（長さ。ArrowもLineとして扱う） - 計測値：Angleタイプの場合（角度） -
 * 計測値：TextやImageRoiタイプの場合なにもしない ※抜けているものがあれば補足してください。
 * ・3D-ROIの場合は、Roiのタイプを識別して、以下の情報を表示します。 -
 * Area系のみでグループ化されている場合：体積、平均値、中央値、最小値、最大値、IQR、SD、長軸、短軸
 * ※剛体の球の場合の体積は、ボクセルベースに算出し直す？ - 線のみでグループ化されている場合：総合の長さ、長さの平均、長さの最小、長さの最大 -
 * Pointのみでグループ化されている場合：Point数、平均値、中央値、最小値、最大値、IQR、SD -
 * Angleのみでグループ化されている場合：平均角度、角度の中央値、角度の最小値、角度の最大値 -
 * 複合でグループ化されている場合：複合ROIのため、何も表示しないことを明示する
 * ・剛体3D-ROIのための「半径」数値変更UI:テキストボックスに新しい半径（例:
 * 15.0mm）を入力してApplyボタンを押すと、球の大きさが一発でGroupスライスで連動して変わる機能。ただし、3D Sphereのときのみ表示。
 * 
 * @author tatsunidas
 * 
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
		setBackground(new Color(0, 0, 0, 140)); // 背景は黒の半透明

		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setOpaque(false);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

		// ヘッダ（タイトルバー）
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

		// スムーズなドラッグ移動
		MouseAdapter dragListener = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				screenPressPoint = e.getLocationOnScreen();
				windowInitPoint = getLocation();
			}
			@Override
			public void mouseDragged(MouseEvent e) {
				if (screenPressPoint == null || windowInitPoint == null) return;
				Point currentScreen = e.getLocationOnScreen();
				int deltaX = currentScreen.x - screenPressPoint.x;
				int deltaY = currentScreen.y - screenPressPoint.y;
				setLocation(windowInitPoint.x + deltaX, windowInitPoint.y + deltaY);
			}
		};
		headerPanel.addMouseListener(dragListener);
		headerPanel.addMouseMotionListener(dragListener);

		mainPanel.add(headerPanel, BorderLayout.NORTH);

		// コンテンツパネル
		JPanel contentPanel = new JPanel(new GridLayout(0, 1, 5, 5));
		contentPanel.setOpaque(false);
		buildContent(contentPanel);
		mainPanel.add(contentPanel, BorderLayout.CENTER);

		add(mainPanel);
		pack();
		setLocation(screenLocation); 
	}

	private void buildContent(JPanel panel) {
		addInfoRow(panel, "Position (x,y):", String.format("%.1f, %.1f", targetRoi.getXBase(), targetRoi.getYBase()));

		String shape3D = targetRoi.getProperty(RoiMetaContextKey.Shape_3D_Type.name());
		boolean is3D = (shape3D != null && !shape3D.isEmpty());

		if (is3D) {
			build3DContent(panel, shape3D);
		} else {
			build2DContent(panel);
		}
	}

	// ==========================================================
	// ★ 2D情報の表示（RoiType Enumを使用）
	// ==========================================================
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
	// ★ 3D情報（グループ化）の表示（RoiType Enumを使用）
	// ==========================================================
	private void build3DContent(JPanel panel, String shape3D) {
		String groupId = targetRoi.getProperty(RoiDBKey.RoiGroup.name());
		addInfoRow(panel, "Mode:", "3D Volume (Group: " + groupId + ")");

		List<RoiObj> groupRois = new ArrayList<>();
		for (SlideGlass s : pp.getAllSlides().values()) {
			if (s == null) continue;
			for (RoiObj r : s.getRois()) {
				if (groupId.equals(r.getProperty(RoiDBKey.RoiGroup.name()))) {
					groupRois.add(r);
				}
			}
		}

		boolean isArea = true, isLine = true, isPoint = true, isAngle = true;
		for (RoiObj r : groupRois) {
			if (!r.isArea()) isArea = false;
			if (!(r.getType() == RoiType.LINE.id() || r.getType() == RoiType.POLYLINE.id() || r.getType() == RoiType.FREELINE.id())) isLine = false;
			if (r.getType() != RoiType.POINT.id() && r.getType() != RoiType.MULTIPOINT.id()) isPoint = false;
			if (r.getType() != RoiType.ANGLE.id()) isAngle = false;
		}

		boolean isMixed = !isArea && !isLine && !isPoint && !isAngle;

		if (isMixed) {
			addInfoRow(panel, "Warning:", "Mixed ROI types in Group. Cannot summarize.");
			return;
		}

		if (isArea) {
			if ("SPHERE".equals(shape3D)) {
				String rStr = targetRoi.getProperty(RoiMetaContextKey.Sphere_Radius_mm.name());
				if (rStr != null) {
					double radius = Double.parseDouble(rStr);
					double vol = (4.0 / 3.0) * Math.PI * Math.pow(radius, 3);
					addInfoRow(panel, "Volume (Exact):", String.format("%.2f mm³", vol));
					addSphereControlUI(panel, rStr);
				}
			} else {
				double totalVol = 0, totalArea = 0, sumMeanArea = 0;
				double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
				
				for (RoiObj r : groupRois) {
					ImageStatistics stats = getFreshStats(r);
					if (stats != null) {
						double thickness = getSliceThickness(r.getSlideGlass()); 
						totalVol += (stats.area * thickness);
						totalArea += stats.area;
						sumMeanArea += (stats.mean * stats.area);
						if (stats.min < min) min = stats.min;
						if (stats.max > max) max = stats.max;
					}
				}
				double avgMean = (totalArea > 0) ? sumMeanArea / totalArea : 0;
				addInfoRow(panel, "Volume (Integral):", String.format("%.2f mm³", totalVol));
				addInfoRow(panel, "Avg Mean:", String.format("%.2f", avgMean));
				addInfoRow(panel, "Global Min / Max:", String.format("%.2f / %.2f", min, max));
			}
			addInfoRow(panel, "Slices Count:", String.valueOf(groupRois.size()));

		} else if (isLine) {
			double totalLen = 0, minLen = Double.MAX_VALUE, maxLen = -Double.MAX_VALUE;
			for (RoiObj r : groupRois) {
				double l = getFreshLength(r);
				totalLen += l;
				if (l < minLen) minLen = l;
				if (l > maxLen) maxLen = l;
			}
			double avgLen = groupRois.isEmpty() ? 0 : totalLen / groupRois.size();
			addInfoRow(panel, "Total Length:", String.format("%.2f", totalLen));
			addInfoRow(panel, "Avg / Min / Max:", String.format("%.2f / %.2f / %.2f", avgLen, minLen, maxLen));
			addInfoRow(panel, "Lines Count:", String.valueOf(groupRois.size()));

		} else if (isPoint) {
			int totalPts = 0;
			for (RoiObj r : groupRois) {
				ij.process.FloatPolygon fp = r.getFloatPolygon();
				if (fp != null) totalPts += fp.npoints;
			}
			addInfoRow(panel, "Total Points:", String.valueOf(totalPts));
			addInfoRow(panel, "Point Sets Count:", String.valueOf(groupRois.size()));

		} else if (isAngle) {
			double totalAng = 0, minAng = Double.MAX_VALUE, maxAng = -Double.MAX_VALUE;
			for (RoiObj r : groupRois) {
				try {
					double a = Double.parseDouble(getFreshAngle(r));
					totalAng += a;
					if (a < minAng) minAng = a;
					if (a > maxAng) maxAng = a;
				} catch(Exception ignored) {}
			}
			double avgAng = groupRois.isEmpty() ? 0 : totalAng / groupRois.size();
			addInfoRow(panel, "Avg Angle:", String.format("%.2f°", avgAng));
			addInfoRow(panel, "Min / Max Angle:", String.format("%.2f° / %.2f°", minAng, maxAng));
			addInfoRow(panel, "Angles Count:", String.valueOf(groupRois.size()));
		}
	}

	private ImageStatistics getFreshStats(RoiObj roi) {
		ImagePlus imp = roi.getSlideGlass() != null ? roi.getSlideGlass().getOriginalImage() : null;
		if (imp != null) {
			Roi r = new RoiConverter().convert2Roi(roi);
			imp.setRoi(r);
			int mOptions = ij.measure.Measurements.AREA | ij.measure.Measurements.MEAN | 
						   ij.measure.Measurements.STD_DEV | ij.measure.Measurements.MIN_MAX | 
						   ij.measure.Measurements.MEDIAN | ij.measure.Measurements.ELLIPSE;
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
			if (degrees > 180.0) degrees = 360.0 - degrees;
			
			return String.format("%.2f", degrees);
		}
		return "0.00";
	}

	private double getSliceThickness(SlideGlass slide) {
		if (slide == null) return 1.0;
		ij.measure.Calibration cal = slide.getOriginalCalibration();
		if (cal != null && cal.pixelDepth > 0) return cal.pixelDepth;
		
		com.vis.dicom.DicomObject header = slide.getHeader();
		if (header != null) {
			return header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices, 
				   header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
		}
		return 1.0;
	}

	private void addSphereControlUI(JPanel panel, String currentRadius) {
		JPanel ctrlPanel = new JPanel(new BorderLayout(5, 0));
		ctrlPanel.setOpaque(false);
		
		JLabel lbl = new JLabel("Radius (mm):");
		lbl.setForeground(Color.ORANGE);
		ctrlPanel.add(lbl, BorderLayout.WEST);
		
		JTextField txtRadius = new JTextField(currentRadius, 5);
		ctrlPanel.add(txtRadius, BorderLayout.CENTER);
		
		JButton btnApply = new JButton("Apply");
		btnApply.addActionListener(e -> {
			try {
				double newR = Double.parseDouble(txtRadius.getText().trim());
				String groupId = targetRoi.getProperty(RoiDBKey.RoiGroup.name());
				
				RoiObj masterRoi = null;
				for (SlideGlass s : pp.getAllSlides().values()) {
					if (s == null) continue;
					for (RoiObj r : s.getRois()) {
						if (groupId.equals(r.getProperty(RoiDBKey.RoiGroup.name()))) {
							String isMaster = r.getProperty(RoiMetaContextKey.Is3D_Master.name());
							if ("true".equals(isMaster)) {
								masterRoi = r;
								break;
							}
						}
					}
					if (masterRoi != null) break;
				}

				if (masterRoi == null) masterRoi = targetRoi;

				double pxSpacingX = masterRoi.getSlideGlass().getPixelSpacingX() <= 0 ? 1.0 : masterRoi.getSlideGlass().getPixelSpacingX();
				double pxSpacingY = masterRoi.getSlideGlass().getPixelSpacingY() <= 0 ? 1.0 : masterRoi.getSlideGlass().getPixelSpacingY();

				double radiusPxX = newR / pxSpacingX;
				double radiusPxY = newR / pxSpacingY;

				java.awt.Rectangle currentBounds = masterRoi.getBounds();
				double cx = masterRoi.getXBase() + currentBounds.width / 2.0;
				double cy = masterRoi.getYBase() + currentBounds.height / 2.0;

				double newW = radiusPxX * 2.0;
				double newH = radiusPxY * 2.0;
				double newX = cx - radiusPxX;
				double newY = cy - radiusPxY;

				masterRoi.setBounds(new java.awt.geom.Rectangle2D.Double(newX, newY, newW, newH));
				masterRoi.setLocation(newX, newY);
				masterRoi.setProperty(RoiMetaContextKey.Sphere_Radius_mm.name(), String.valueOf(newR));

				DatabaseHandler.getInstance().insertRoi(masterRoi.readContext());
				new SphereRoi3D(masterRoi).updateFrom2D(masterRoi);

				pp.repaint();
				dispose(); 
				
			} catch (NumberFormatException ex) {
				txtRadius.setText("Error");
			}
		});
		ctrlPanel.add(btnApply, BorderLayout.EAST);
		
		panel.add(ctrlPanel);
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