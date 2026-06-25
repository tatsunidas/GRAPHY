package com.vis.core.view.D2.ui;

import java.awt.*;
import javax.swing.*;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import com.vis.configuration.Resources;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.glasses.WwWlState;

public class WwWlAdjusterDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static WwWlAdjusterDialog instance;

    private Praparat currentPraparat;
    private int currentChannel = -1; 

    private WwWlContrastPlot contrastPlot; 
    private JSlider wlSlider, wwSlider;
    private JLabel wlValueLabel, wwValueLabel;
    private JComboBox<String> channelChoice;
    private JPanel channelPanel;
    private JButton autoBtn, resetBtn;

    // Direct numeric input of WW/WL on the calibrated (physical) brightness scale
    private JTextField wlInput, wwInput;
    private JButton setBtn;

    private static final int SLIDER_MAX = 1000;
    private boolean isUpdatingUI = false;

    // ★追加: スライダーの可動域を固定するためのベースレンジ
    private double baseMinRange = 0.0;
    private double baseMaxRange = 255.0;
    private double baseMaxWW = 255.0;
    
    //ヒストグラムの形を保持するキャッシュ
    private ImageStatistics cachedStats;
    private ImagePlus cachedImp;

    public static void showDialog(Praparat praparat, Window owner) {
        if (instance == null) {
            instance = new WwWlAdjusterDialog(owner);
        }
        instance.setPraparat(praparat);
        instance.setVisible(true);
    }

    private WwWlAdjusterDialog(Window owner) {
        super(owner, Resources.i18n("WwWlAdjusterDialog.title.default"), ModalityType.MODELESS);
        initComponents();
        setupListeners();
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    private void setPraparat(Praparat praparat) {
        this.currentPraparat = praparat;
        SlideGlass sg = praparat.getCurrentSlide();
        if (sg != null && sg.isRGB()) {
            channelPanel.setVisible(true);
            setTitle(Resources.i18n("WwWlAdjusterDialog.title.colorBalance"));
        } else {
            channelPanel.setVisible(false);
            setTitle(Resources.i18n("WwWlAdjusterDialog.title.windowLevel"));
            currentChannel = -1;
        }
        // ★追加: 画像が切り替わった時にスライダーの絶対的な可動域を計算する
        calculateBaseRange(sg);
        
        pack();
        updateUIFromModel();
    }

    /**
     * ★追加: スライダーが振り切らないように、データの最大幅から余裕を持った可動域を定義する
     */
    private void calculateBaseRange(SlideGlass sg) {
        if (sg == null || sg.getOriginalImage() == null) return;
        ImageStatistics stats = getTargetStatistics(sg.getOriginalImage());
        if (stats == null) return;

        double dataMin = stats.min;
        double dataMax = stats.max;
        double dataRange = dataMax - dataMin;
        if (dataRange <= 0) dataRange = 1.0;

        // 現在設定されているWW/WLもスライダー内に収まるように考慮する
        double minmax[] =  sg.getCurrentWindowMinMax();
        double curMin = minmax[0];
        double curMax = minmax[1];
        
        // WLの可動域：データの端からデータ幅の半分だけさらに余裕を持たせる
        this.baseMinRange = Math.min(dataMin, curMin) - (dataRange * 0.5);
        this.baseMaxRange = Math.max(dataMax, curMax) + (dataRange * 0.5);
        
        // WWの可動域：最大のデータ幅の「3倍」または「現在のWWの1.5倍」の大きい方を上限とする
        double currentWW = curMax - curMin;
        this.baseMaxWW = Math.max(dataRange * 3.0, currentWW * 1.5);
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 12, 8, 12);
        int y = 0;

        contrastPlot = new WwWlContrastPlot();
        c.gridy = y++;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.NONE;
        add(contrastPlot, c);
        
        c.fill = GridBagConstraints.HORIZONTAL; 

        channelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        channelPanel.add(new JLabel(Resources.i18n("WwWlAdjusterDialog.label.channel")));
        channelChoice = new JComboBox<>(new String[]{
                Resources.i18n("WwWlAdjusterDialog.channel.all"),
                Resources.i18n("WwWlAdjusterDialog.channel.red"),
                Resources.i18n("WwWlAdjusterDialog.channel.green"),
                Resources.i18n("WwWlAdjusterDialog.channel.blue")});
        channelPanel.add(channelChoice);
        c.gridy = y++;
        add(channelPanel, c);

        c.gridy = y++;
        JPanel wlTextPanel = new JPanel(new BorderLayout());
        wlTextPanel.add(new JLabel(Resources.i18n("WwWlAdjusterDialog.label.windowCenter")), BorderLayout.WEST);
        wlValueLabel = new JLabel("0.0", JLabel.RIGHT);
        wlTextPanel.add(wlValueLabel, BorderLayout.EAST);
        add(wlTextPanel, c);

        wlSlider = new JSlider(0, SLIDER_MAX, SLIDER_MAX / 2);
        c.gridy = y++;
        add(wlSlider, c);

        c.gridy = y++;
        JPanel wwTextPanel = new JPanel(new BorderLayout());
        wwTextPanel.add(new JLabel(Resources.i18n("WwWlAdjusterDialog.label.windowWidth")), BorderLayout.WEST);
        wwValueLabel = new JLabel("0.0", JLabel.RIGHT);
        wwTextPanel.add(wwValueLabel, BorderLayout.EAST);
        add(wwTextPanel, c);

        wwSlider = new JSlider(0, SLIDER_MAX, SLIDER_MAX / 2);
        c.gridy = y++;
        add(wwSlider, c);

        // Direct numeric input row: enter WW/WL on the calibrated brightness scale and apply with Set
        JPanel directInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        directInputPanel.add(new JLabel(Resources.i18n("WwWlAdjusterDialog.label.wl")));
        wlInput = new JTextField(7);
        directInputPanel.add(wlInput);
        directInputPanel.add(new JLabel(Resources.i18n("WwWlAdjusterDialog.label.ww")));
        wwInput = new JTextField(7);
        directInputPanel.add(wwInput);
        setBtn = new JButton(Resources.i18n("WwWlAdjusterDialog.button.set"));
        directInputPanel.add(setBtn);
        c.gridy = y++;
        add(directInputPanel, c);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        autoBtn = new JButton(Resources.i18n("WwWlAdjusterDialog.button.auto"));
        resetBtn = new JButton(Resources.i18n("WwWlAdjusterDialog.button.reset"));
        btnPanel.add(autoBtn);
        btnPanel.add(resetBtn);

        c.gridy = y++;
        c.insets = new Insets(16, 12, 12, 12);
        add(btnPanel, c);
    }

    private void setupListeners() {
        channelChoice.addActionListener(e -> {
            int idx = channelChoice.getSelectedIndex();
            currentChannel = (idx == 0) ? -1 : idx - 1;
            updateUIFromModel();
        });

        javax.swing.event.ChangeListener sliderEvent = e -> {
            if (isUpdatingUI || currentPraparat == null) return;
            SlideGlass sg = currentPraparat.getCurrentSlide();
            if (sg == null || sg.getOriginalImage() == null) return;

            double wlPct = wlSlider.getValue() / (double) SLIDER_MAX;
            double wwPct = wwSlider.getValue() / (double) SLIDER_MAX;

            // ★修正: 固定されたベースレンジから絶対値を逆算する
            double targetWidth = wwPct * baseMaxWW;
            if (targetWidth < 1.0) targetWidth = 1.0;
            
            double wlRange = baseMaxRange - baseMinRange;
            double targetCenter = baseMinRange + (wlPct * wlRange);

            double min = targetCenter - (targetWidth / 2.0);
            double max = targetCenter + (targetWidth / 2.0);

            // DICOMの物理値へ変換してラベルに表示
            ij.measure.Calibration cal = sg.getOriginalCalibration();
            double physMin = (cal != null && cal.calibrated()) ? cal.getCValue(min) : min;
            double physMax = (cal != null && cal.calibrated()) ? cal.getCValue(max) : max;
            double physWidth = physMax - physMin;
            double physCenter = physMin + (physWidth / 2.0);

            wlValueLabel.setText(String.format("%.1f", physCenter));
            wwValueLabel.setText(String.format("%.1f", Math.abs(physWidth)));
            // Keep the direct-input fields in sync while dragging the sliders
            wlInput.setText(String.format("%.1f", physCenter));
            wwInput.setText(String.format("%.1f", Math.abs(physWidth)));

            currentPraparat.updateSliderContrast(currentPraparat.getCurrentSlidePos(), currentChannel, min, max);
            
            // 毎回 getTargetStatistics() を計算せず、キャッシュされた形を使って「線と色」だけを更新する
            if (cachedStats != null && cachedImp != null) {
                contrastPlot.setHistogramData(cachedImp, cachedStats, min, max);
            }
        };

        wlSlider.addChangeListener(sliderEvent);
        wwSlider.addChangeListener(sliderEvent);

		autoBtn.addActionListener(e -> {
			if (currentPraparat != null) {
				SlideGlass sg = currentPraparat.getCurrentSlide();
				if (sg != null) {
					ImagePlus imp = sg.getOriginalImage();

					// ★修正: 対象のチャンネルだけを正確に狙い撃ちしてストレッチをかける
					if (sg.isRGB() && currentChannel == -1) {
						// RGB画像で「All」選択時は、R・G・Bそれぞれを個別に最大ストレッチしてホワイトバランスを自動補正する
						for (int c = 0; c <= 2; c++) {
							ImageStatistics stats = getTargetStatistics(imp, c);
							if (stats != null) {
								currentPraparat.updateSliderContrast(currentPraparat.getCurrentSlidePos(), c, stats.min,
										stats.max);
							}
						}
					} else {
						// モノクロ画像、または特定のカラーチャンネル選択時
						ImageStatistics stats = getTargetStatistics(imp, currentChannel);
						if (stats != null) {
							currentPraparat.updateSliderContrast(currentPraparat.getCurrentSlidePos(), currentChannel,
									stats.min, stats.max);
						}
					}
					updateUIFromModel();
				}
			}
		});

        resetBtn.addActionListener(e -> {
            if (currentPraparat != null) {
                currentPraparat.resetWindow();
                updateUIFromModel();
            }
        });

        setBtn.addActionListener(e -> applyDirectInput());

        // Allow pressing Enter inside either input field to apply
        java.awt.event.ActionListener enterToApply = e -> applyDirectInput();
        wlInput.addActionListener(enterToApply);
        wwInput.addActionListener(enterToApply);
    }

    /**
     * Apply the WW/WL typed directly by the user. The input is interpreted on the
     * calibrated (physical) brightness scale and converted back to raw pixel values
     * before being applied to the model, so it matches the values shown in the labels.
     */
    private void applyDirectInput() {
        if (currentPraparat == null) return;
        SlideGlass sg = currentPraparat.getCurrentSlide();
        if (sg == null || sg.getOriginalImage() == null) return;

        double wl, ww;
        try {
            wl = Double.parseDouble(wlInput.getText().trim());
            ww = Double.parseDouble(wwInput.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    Resources.i18n("WwWlAdjusterDialog.error.invalidInput"),
                    Resources.i18n("dialog.title.inputWarning"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Window width must be positive to avoid a zero/negative display range
        if (ww < 1.0) ww = 1.0;

        // Derive the calibrated min/max from center (WL) and width (WW)
        double physMin = wl - (ww / 2.0);
        double physMax = wl + (ww / 2.0);

        // Convert calibrated values back to raw pixel values for the model
        ij.measure.Calibration cal = sg.getOriginalCalibration();
        double rawMin = (cal != null && cal.calibrated()) ? cal.getRawValue(physMin) : physMin;
        double rawMax = (cal != null && cal.calibrated()) ? cal.getRawValue(physMax) : physMax;

        // A negative-slope calibration (e.g. inverted LUT mapping) can flip the order
        if (rawMax < rawMin) {
            double tmp = rawMin;
            rawMin = rawMax;
            rawMax = tmp;
        }

        currentPraparat.updateSliderContrast(currentPraparat.getCurrentSlidePos(), currentChannel, rawMin, rawMax);
        updateUIFromModel();
    }

 // 1. getTargetStatistics メソッドを、引数でチャンネルを受け取れるようにオーバーロードします。
    private ImageStatistics getTargetStatistics(ImagePlus imp) {
        return getTargetStatistics(imp, currentChannel);
    }

    private ImageStatistics getTargetStatistics(ImagePlus imp, int targetChannel) {
        if (imp == null || imp.getProcessor() == null) return null;
        ImageProcessor ip = imp.getProcessor();
        
        ImageProcessor targetIp = ip;
        if (ip instanceof ij.process.ColorProcessor && targetChannel >= 0) {
            ij.process.ColorProcessor cp = (ij.process.ColorProcessor) ip;
            byte[] r = new byte[cp.getWidth() * cp.getHeight()];
            byte[] g = new byte[r.length];
            byte[] b = new byte[r.length];
            cp.getRGB(r, g, b);
            byte[] targetPixels = (targetChannel == 0) ? r : ((targetChannel == 1) ? g : b);
            targetIp = new ij.process.ByteProcessor(cp.getWidth(), cp.getHeight(), targetPixels, null);
        }

        ImageStatistics rawStats = ImageStatistics.getStatistics(targetIp, ImageStatistics.MIN_MAX, null);
        
        if (targetIp.getBitDepth() > 8) {
            ImagePlus tempImp = new ImagePlus("temp", targetIp);
            return new ij.process.StackStatistics(tempImp, 256, rawStats.min, rawStats.max);
        }
        
        return rawStats;
    }

    private void updateUIFromModel() {
        if (currentPraparat == null) return;
        SlideGlass sg = currentPraparat.getCurrentSlide();
        if (sg == null || sg.getOriginalImage() == null) return;

        isUpdatingUI = true;

        ImagePlus imp = sg.getOriginalImage();
        ImageStatistics stats = getTargetStatistics(imp);
        
        // ★追加: 取得した統計情報をキャッシュに保存しておく
        this.cachedStats = stats;
        this.cachedImp = imp;
        
        WwWlState state = currentPraparat.getWwWlState(currentPraparat.getCurrentSlidePos());

        double currentMin = state.getMin(currentChannel);
        double currentMax = state.getMax(currentChannel);

        double width = currentMax - currentMin;
        double center = currentMin + (width / 2.0);

        // ★修正: 固定されたベースレンジからスライダーの位置(%)を計算する
        double wlRange = baseMaxRange - baseMinRange;
        int wlValue = (int) (((center - baseMinRange) / wlRange) * SLIDER_MAX);
        int wwValue = (int) ((width / baseMaxWW) * SLIDER_MAX);

        wlSlider.setValue(Math.max(0, Math.min(SLIDER_MAX, wlValue)));
        wwSlider.setValue(Math.max(0, Math.min(SLIDER_MAX, wwValue)));

        ij.measure.Calibration cal = sg.getOriginalCalibration();
        double physMin = (cal != null && cal.calibrated()) ? cal.getCValue(currentMin) : currentMin;
        double physMax = (cal != null && cal.calibrated()) ? cal.getCValue(currentMax) : currentMax;
        double physWidth = physMax - physMin;
        double physCenter = physMin + (physWidth / 2.0);

        wlValueLabel.setText(String.format("%.1f", physCenter));
        wwValueLabel.setText(String.format("%.1f", Math.abs(physWidth)));
        // Prefill the direct-input fields with the current calibrated WW/WL
        wlInput.setText(String.format("%.1f", physCenter));
        wwInput.setText(String.format("%.1f", Math.abs(physWidth)));

        contrastPlot.setHistogramData(imp, stats, currentMin, currentMax);

        // ★検証ログの出力
        System.out.println("=== WwWl Adjuster State & Histogram Validation ===");
        System.out.println("Raw Data Min: " + stats.min + ", Raw Data Max: " + stats.max);
        System.out.println("Base Range (WL 可動域): " + baseMinRange + " to " + baseMaxRange);
        System.out.println("Base Max WW (WW 可動域上限): " + baseMaxWW);
        System.out.println("Current Min: " + currentMin + ", Current Max: " + currentMax);
        System.out.println("Histogram Max Peak Value: " + stats.maxCount);

        isUpdatingUI = false;
    }
}