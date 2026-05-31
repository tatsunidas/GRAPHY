package com.vis.core.view.D2.ui.glasses;

import java.util.HashMap;
import java.util.Map;

/**
 * 1つのスライス（ZCT）におけるWindow/LevelおよびColor Balanceの状態を保持するクラス。
 * 画像がメモリから解放されても、このオブジェクトが設定を記憶します。
 */
public class WwWlState {
    // モノクロ画像、またはRGB全体のデフォルトMin/Max
    private double defaultMin = 0.0;
    private double defaultMax = 255.0;
    
    // チャンネルごとの調整値 (Key: チャンネルID (例: 0=Red, 1=Green, 2=Blue, -1=All))
    private final Map<Integer, Double> channelMins = new HashMap<>();
    private final Map<Integer, Double> channelMaxs = new HashMap<>();

    public WwWlState(double defaultMin, double defaultMax) {
        this.defaultMin = defaultMin;
        this.defaultMax = defaultMax;
        // 初期状態はデフォルト値
        setValues(-1, defaultMin, defaultMax); // All
        setValues(0, 0.0, 255.0);  // Red
        setValues(1, 0.0, 255.0);  // Green
        setValues(2, 0.0, 255.0);  // Blue
    }

    public synchronized void setValues(int channel, double min, double max) {
        channelMins.put(channel, min);
        channelMaxs.put(channel, max);
    }

    public synchronized double getMin(int channel) {
        return channelMins.getOrDefault(channel, defaultMin);
    }

    public synchronized double getMax(int channel) {
        return channelMaxs.getOrDefault(channel, defaultMax);
    }

    public double getDefaultMin() { return defaultMin; }
    public double getDefaultMax() { return defaultMax; }
    
    public synchronized void resetToDefault() {
        // Allの初期化
        setValues(-1, defaultMin, defaultMax);
        // カラーチャンネルの初期化（8-bitプレーンのデフォルト 0〜255）
        setValues(0, 0.0, 255.0); // Red
        setValues(1, 0.0, 255.0); // Green
        setValues(2, 0.0, 255.0); // Blue
    }
}
