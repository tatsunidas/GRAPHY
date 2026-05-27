/**
 * Copyright: visionary imaging services, inc.
 */
package com.vis.core.nuclearmedicine;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

/**
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class SUVCalibrationDialog extends JDialog {

	private Praparat praparat;
	private DicomObject header;

	// SUV計算用パラメータ
	private double patWeight = 0.0;
	private double patHeight = 0.0;
	private double totalDose = 0.0;
	private double halfLife = 0.0;
	private LocalTime injectionTime = null;
	private LocalTime seriesTime = null;
	private String patSex = "M"; // "M" or "F"
	private String radionuclideName = "Unknown";

	// ★ ベンダー固有のプライベートタグ用
	private double philipsSuvScaleFactor = 0.0;

	// 計算結果の係数
	private double suvFactor = 0.0;

	// UIコンポーネント
	private JComboBox<String> cmbSuvType;
	private JTextField txtWeight;
	private JTextField txtHeight;
	private JTextField txtDose;
	private JTextField txtHalfLife;
	private JTextField txtInjTime;
	private JTextField txtSerTime;
	private JComboBox<String> cmbSex;
	private JLabel lblRadionuclide;
	private JLabel lblPhilipsWarning;

	public SUVCalibrationDialog(Frame owner, Praparat praparat) {
		super(owner, "SUV Calibration", true);
		this.praparat = praparat;

		for (SlideGlass sg : praparat.getAllSlides().values()) {
			if (sg != null) {
				this.header = sg.getHeader();
				break;
			}
		}

		if (this.header == null) {
			JOptionPane.showMessageDialog(owner, "This series does not have any images.");
			SwingUtilities.invokeLater(() -> {
				dispose();
			});
		}
		extractDicomData();
	}

	/**
	 * UIの構築
	 */
	public void buildUI() {
		JPanel mainPanel = new JPanel(new GridBagLayout());
		mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		int row = 0;

		// ★ Philips警告ラベルの表示
		if (philipsSuvScaleFactor > 0) {
			lblPhilipsWarning = new JLabel(
					"<html><font color='blue'>Philips Private SUV Factor Detected.</font></html>");
			gbc.gridx = 0;
			gbc.gridy = row++;
			gbc.gridwidth = 2;
			mainPanel.add(lblPhilipsWarning, gbc);
			gbc.gridwidth = 1;
		}

		// ★ SUVタイプの選択 (バリエーションの網羅)
		String[] suvTypes = { "SUVbw (Body Weight)", "SUL (Lean Body Mass - James)", "SUL (Lean Body Mass - Janma)",
				"SUVbsa (Body Surface Area)" };
		cmbSuvType = new JComboBox<>(suvTypes);
		addRow(mainPanel, gbc, row++, "Calculation Type:", cmbSuvType);

		// 核種の表示 (要件4)
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 0.0;
		mainPanel.add(new JLabel("Radionuclide:"), gbc);
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		lblRadionuclide = new JLabel(radionuclideName);
		lblRadionuclide.setFont(lblRadionuclide.getFont().deriveFont(Font.BOLD));
		mainPanel.add(lblRadionuclide, gbc);
		row++;

		// 入力フィールドの初期化
		txtWeight = createNumericTextField(patWeight);
		txtHeight = createNumericTextField(patHeight);
		txtDose = createNumericTextField(totalDose);
		txtHalfLife = createNumericTextField(halfLife);

		DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
		txtInjTime = new JTextField(injectionTime != null ? injectionTime.format(timeFmt) : "");
		txtSerTime = new JTextField(seriesTime != null ? seriesTime.format(timeFmt) : "");

		cmbSex = new JComboBox<>(new String[] { "Male", "Female" });
		cmbSex.setSelectedItem("F".equals(patSex) ? "Female" : "Male");

		// コンポーネントをきれいに配置 (GridBagLayout)
		addRow(mainPanel, gbc, row++, "Patient Weight (kg):", txtWeight);
		addRow(mainPanel, gbc, row++, "Patient Height (m):", txtHeight);
		addRow(mainPanel, gbc, row++, "Patient Sex:", cmbSex);
		addRow(mainPanel, gbc, row++, "Total Dose (MBq):", txtDose);
		addRow(mainPanel, gbc, row++, "Half-life (min):", txtHalfLife);
		addRow(mainPanel, gbc, row++, "Injection Time (HH:mm:ss):", txtInjTime);
		addRow(mainPanel, gbc, row++, "Series Time (HH:mm:ss):", txtSerTime);

		// 垂直方向のスペーサー（余白をすべて下部に詰めるための役割）
		gbc.gridx = 0;
		gbc.gridy = row++;
		gbc.gridwidth = 2;
		gbc.weighty = 1.0;
		mainPanel.add(Box.createVerticalGlue(), gbc);

		// ボタン配置
		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton btnOk = new JButton("Apply");
		JButton btnCancel = new JButton("Cancel");
		btnOk.addActionListener(e -> applyAndClose());
		btnCancel.addActionListener(e -> dispose());
		btnPanel.add(btnOk);
		btnPanel.add(btnCancel);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		getContentPane().add(btnPanel, BorderLayout.SOUTH);

		pack();
		setMinimumSize(new Dimension(420, 400));
		setLocationRelativeTo(getOwner());
	}

	private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component comp) {
		gbc.weighty = 0.0;
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.weightx = 0.0;
		panel.add(new JLabel(label), gbc);
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		panel.add(comp, gbc);
	}

	/**
	 * 半角数字と小数点以外入力をブロックするTextFieldを生成する（要件2）
	 */
	private JTextField createNumericTextField(double value) {
		JTextField textField = new JTextField(value > 0 ? String.format("%.2f", value) : "");
		((AbstractDocument) textField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
		return textField;
	}

	/**
	 * ルート階層またはシーケンス階層からDICOM属性を安全に抽出する（要件1, 4, 5）
	 */
	private void extractDicomData() {
		if (header == null)
			return;

		// 基本情報の取得
		patWeight = header.getDouble(Tag.PatientWeight, 0.0);
		patHeight = header.getDouble(Tag.PatientSize, 0.0);
		patSex = header.getString(Tag.PatientSex, "M").toUpperCase();

		// 放射性医薬品シーケンス (0054,0016) の取得を試みる
		DicomObject item = null;
		try {
			// GRAPHYのDICOMライブラリの仕様に合わせてシーケンスから子要素を取得
			@SuppressWarnings("unchecked")
			java.util.List<DicomObject> seq = (java.util.List<DicomObject>) header
					.getSequence(Tag.RadiopharmaceuticalInformationSequence);
			if (seq != null && !seq.isEmpty()) {
				item = seq.get(0);
			}
		} catch (Exception e) {
			Log.logger.fine("No RadiopharmaceuticalInformationSequence found, searching root.");
		}

		// 投与量 (Bq -> MBq) の抽出（要件1: エスケープ処理）
		totalDose = (item != null && item.contains(Tag.RadionuclideTotalDose))
				? item.getDouble(Tag.RadionuclideTotalDose, 0.0)
				: header.getDouble(Tag.RadionuclideTotalDose, 0.0);
		if (totalDose > 1000000.0) {
			totalDose /= 1000000.0;
		}

		// 半減期 (秒 -> 分) の抽出（要件1: エスケープ処理）
		halfLife = (item != null && item.contains(Tag.RadionuclideHalfLife))
				? item.getDouble(Tag.RadionuclideHalfLife, 0.0)
				: header.getDouble(Tag.RadionuclideHalfLife, 0.0);
		if (halfLife > 0) {
			halfLife /= 60.0;
		}

		// 核種名の抽出（要件4）
		radionuclideName = (item != null && item.contains(Tag.Radionuclide))
				? item.getString(Tag.Radionuclide, "Unknown")
				: header.getString(Tag.Radionuclide, "Unknown");

		// もしコードシーケンス(0054,0100)にあれば、そちらから意味(CodeMeaning)を取る処理もエスケープとして追加可能
		if ("Unknown".equals(radionuclideName) && item != null) {
			try {
				@SuppressWarnings("unchecked")
				java.util.List<DicomObject> codeSeq = (java.util.List<DicomObject>) item
						.getSequence(Tag.RadionuclideCodeSequence);
				if (codeSeq != null && !codeSeq.isEmpty()) {
					radionuclideName = codeSeq.get(0).getString(Tag.CodeMeaning, "Unknown");
				}
			} catch (Exception e) {
			}
		}

		// 時刻の抽出とエスケープ（要件1）
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmmss");
		try {
			String injTimeStr = (item != null && item.contains(Tag.RadiopharmaceuticalStartTime))
					? item.getString(Tag.RadiopharmaceuticalStartTime, "")
					: header.getString(Tag.RadiopharmaceuticalStartTime, "");
			if (injTimeStr.length() >= 6) {
				injectionTime = LocalTime.parse(injTimeStr.substring(0, 6), formatter);
			}

			String acqTimeStr = header.getString(Tag.AcquisitionTime, "");
			String serTimeStr = header.getString(Tag.SeriesTime, "");
			String targetTimeStr = !acqTimeStr.isEmpty() ? acqTimeStr : serTimeStr;
			if (targetTimeStr.length() >= 6) {
				seriesTime = LocalTime.parse(targetTimeStr.substring(0, 6), formatter);
			}
		} catch (Exception e) {
			Log.logger.warning("Failed to parse time for SUV: " + e.getMessage());
		}

		// ★ Philipsプライベートタグのエスケープ処理 (7053, 1000 or 7053, 1009)
		// ※ GRAPHYのTag仕様で16進数指定が通らない場合は直接数値を指定してください
		try {
			philipsSuvScaleFactor = header.getDouble(0x70531000, 0.0);
			if (philipsSuvScaleFactor == 0.0) {
				philipsSuvScaleFactor = header.getDouble(0x70531009, 0.0);
			}
		} catch (Exception e) {
			Log.logger.fine("No Philips private SUV tags found.");
		}
	}

	/**
	 * 入力検証（レンジチェック）とデータの適用（要件2, 3）
	 */
	private void applyAndClose() {
		try {
			// 空白チェックとパース
			if (txtWeight.getText().trim().isEmpty() || txtDose.getText().trim().isEmpty()
					|| txtHalfLife.getText().trim().isEmpty()) {
				JOptionPane.showMessageDialog(this, "Weight, Total Dose, and Half-life are required.", "Input Warning",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			patWeight = Double.parseDouble(txtWeight.getText().trim());
			patHeight = txtHeight.getText().trim().isEmpty() ? 0.0 : Double.parseDouble(txtHeight.getText().trim());
			totalDose = Double.parseDouble(txtDose.getText().trim());
			halfLife = Double.parseDouble(txtHalfLife.getText().trim());

			DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
			injectionTime = LocalTime.parse(txtInjTime.getText().trim(), timeFmt);
			seriesTime = LocalTime.parse(txtSerTime.getText().trim(), timeFmt);
			patSex = "Female".equals(cmbSex.getSelectedItem()) ? "F" : "M";

			// 臨床的レンジチェック（要件3：明らかにおかしい値の排除）
			if (patWeight < 0.001 || patWeight > 250.0) {
				showValidationError("Patient Weight must be between 0.001 kg and 250.0 kg.");
				return;
			}
			if (patHeight != 0.0 && (patHeight < 0.4 || patHeight > 2.5)) {
				showValidationError("Patient Height must be between 0.4 m and 2.5 m.");
				return;
			}
			if (totalDose < 1.0 || totalDose > 1000.0) {
				showValidationError(
						"Total Dose must be between 1.0 MBq and 1000.0 MBq.\n(Typical F-18 dose is 100-400 MBq)");
				return;
			}
			if (halfLife < 1.0 || halfLife > 10000.0) {
				showValidationError("Half-life seems abnormal. Please verify.\n(F-18 is approx 109.8 min)");
				return;
			}

			// ★ Philipsプライベートタグが存在し、SUVbwが選ばれている場合のバイパス
			if (philipsSuvScaleFactor > 0 && cmbSuvType.getSelectedIndex() == 0) {
				this.suvFactor = 1.0 / philipsSuvScaleFactor;
				Log.logger.info("Using Philips Private SUV Scale Factor.");
				dispose();
				return;
			}

			// 時間軸の論理チェック
			long diffSeconds = ChronoUnit.SECONDS.between(injectionTime, seriesTime);
			if (diffSeconds < 0)
				diffSeconds += 24 * 60 * 60; // 日またぎ補正

			if (diffSeconds > 18000) { // 5時間以上離れている場合
				int choice = JOptionPane.showConfirmDialog(this,
						"The time difference between Injection and Scan is more than 5 hours.\nDo you want to proceed?",
						"Time Range Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (choice != JOptionPane.YES_OPTION)
					return;
			}

			// ★ 選択されたSUVタイプに応じたファクターの計算
			this.suvFactor = calculateAdvancedSUVFactor(diffSeconds, cmbSuvType.getSelectedIndex());
			if (this.suvFactor <= 0) {
				showValidationError("Could not calculate valid SUV factor. Check Height/Weight.");
				return;
			}
			
			praparat.setSUVFactor(suvFactor);

			Log.logger.info("SUV Factor successfully calibrated: " + this.suvFactor + " (Modality: PET, Nuclide: "
					+ radionuclideName + ", Type: " + cmbSuvType.getSelectedItem() + ")");
			dispose();

		} catch (java.time.format.DateTimeParseException ex) {
			JOptionPane.showMessageDialog(this, "Time format must be HH:mm:ss (e.g., 13:45:00).", "Format Error",
					JOptionPane.ERROR_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Invalid inputs: " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void showValidationError(String message) {
		JOptionPane.showMessageDialog(this, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * ★ SULやBSAを考慮した高度なSUV変換係数の計算
	 */
	private double calculateAdvancedSUVFactor(long diffSeconds, int typeIndex) {
		double decay = Math.exp(-Math.log(2.0) * diffSeconds / (halfLife * 60.0));

		// 崩壊補正済みの投与量 (Bq)
		double decayedDoseBq = totalDose * 1000000.0 * decay;

		double h_cm = patHeight * 100.0;
		double normBase = 0.0;

		switch (typeIndex) {
		case 0: // SUVbw (Body Weight)
			normBase = patWeight * 1000.0; // g
			break;

		case 1: // SUL (James formula)
			if (patHeight == 0)
				return 0.0;
			if ("M".equals(patSex)) {
				normBase = (1.10 * patWeight - 128.0 * Math.pow(patWeight / h_cm, 2)) * 1000.0;
			} else {
				normBase = (1.07 * patWeight - 148.0 * Math.pow(patWeight / h_cm, 2)) * 1000.0;
			}
			break;

		case 2: // SUL (Janmahasatian formula)
			if (patHeight == 0)
				return 0.0;
			double bmi = patWeight / Math.pow(patHeight, 2);
			if ("M".equals(patSex)) {
				normBase = ((9.27 * patWeight) / (6.68 + 0.216 * bmi)) * 1000.0;
			} else {
				normBase = ((9.27 * patWeight) / (8.78 + 0.244 * bmi)) * 1000.0;
			}
			break;

		case 3: // SUVbsa (DuBois formula)
			if (patHeight == 0)
				return 0.0;
			// 体表面積 (m^2)
			double bsa = 0.007184 * Math.pow(patWeight, 0.425) * Math.pow(h_cm, 0.725);
			normBase = bsa * 10000.0;
			break;
		}

		if (normBase <= 0.0)
			return 0.0;

		// Factor = 補正投与量 / 正規化ベース
		return decayedDoseBq / normBase;
	}

	public double getSuvFactor() {
		return this.suvFactor;
	}

	/**
	 * 半角数字と小数点のみ許可するドキュメントフィルター（要件2）
	 */
	private static class NumericDocumentFilter extends DocumentFilter {
		@Override
		public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
				throws BadLocationException {
			if (string == null)
				return;
			if (isValid(string))
				super.insertString(fb, offset, string, attr);
		}

		@Override
		public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
				throws BadLocationException {
			if (text == null)
				return;
			if (isValid(text))
				super.replace(fb, offset, length, text, attrs);
		}

		private boolean isValid(String text) {
			// 半角数字、または小数点のみ許可する正規表現
			return text.matches("^[0-9.]+$");
		}
	}
}