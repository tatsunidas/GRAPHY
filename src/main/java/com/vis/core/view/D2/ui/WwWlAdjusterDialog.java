package com.vis.core.view.D2.ui;

import java.awt.*;
import javax.swing.*;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
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
        super(owner, "Window / Level & Color Balance", ModalityType.MODELESS);
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
            setTitle("Color Balance Adjuster");
        } else {
            channelPanel.setVisible(false);
            setTitle("Window / Level Adjuster");
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
        channelPanel.add(new JLabel("Channel: "));
        channelChoice = new JComboBox<>(new String[]{"All", "Red", "Green", "Blue"});
        channelPanel.add(channelChoice);
        c.gridy = y++;
        add(channelPanel, c);

        c.gridy = y++;
        JPanel wlTextPanel = new JPanel(new BorderLayout());
        wlTextPanel.add(new JLabel("Window Center (Level):"), BorderLayout.WEST);
        wlValueLabel = new JLabel("0.0", JLabel.RIGHT);
        wlTextPanel.add(wlValueLabel, BorderLayout.EAST);
        add(wlTextPanel, c);

        wlSlider = new JSlider(0, SLIDER_MAX, SLIDER_MAX / 2);
        c.gridy = y++;
        add(wlSlider, c);

        c.gridy = y++;
        JPanel wwTextPanel = new JPanel(new BorderLayout());
        wwTextPanel.add(new JLabel("Window Width:"), BorderLayout.WEST);
        wwValueLabel = new JLabel("0.0", JLabel.RIGHT);
        wwTextPanel.add(wwValueLabel, BorderLayout.EAST);
        add(wwTextPanel, c);

        wwSlider = new JSlider(0, SLIDER_MAX, SLIDER_MAX / 2);
        c.gridy = y++;
        add(wwSlider, c);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        autoBtn = new JButton("Auto");
        resetBtn = new JButton("Reset");
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