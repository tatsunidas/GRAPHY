package com.vis.core.view.D2.ui;

import java.awt.*;
import java.awt.image.IndexColorModel;
import javax.swing.*;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public class WwWlContrastPlot extends JPanel {
    private static final long serialVersionUID = 1L;
    
    private static final int WIDTH = 166;
    private static final int HEIGHT = 83;

    private int[] histogram;
    private Color[] hColors;
    private int hmax;
    
    private double defaultMin = 0.0;
    private double defaultMax = 255.0;
    private double currentMin = 0.0;
    private double currentMax = 255.0;

    public WwWlContrastPlot() {
        setBackground(Color.WHITE);
        Dimension dim = new Dimension(WIDTH + 1, HEIGHT + 1);
        setPreferredSize(dim);
        setMinimumSize(dim);
    }

    public void setHistogramData(ImagePlus imp, ImageStatistics stats, double currentMin, double currentMax) {
        if (stats == null || imp == null) {
            this.histogram = null;
            repaint();
            return;
        }

        // ヒストグラムの描画幅は、常にデータの実際の範囲に固定
        this.defaultMin = stats.min;
        this.defaultMax = stats.max;
        
        this.currentMin = currentMin;
        this.currentMax = currentMax;
        
        this.histogram = new int[256];
        System.arraycopy(stats.histogram, 0, this.histogram, 0, 256);

        // 外れ値を除外するための ImageJ ベースのピーク補正
        int maxCount = 0;
        int mode = 0;
        for (int i = 0; i < 256; i++) {
            if (histogram[i] > maxCount) {
                maxCount = histogram[i];
                mode = i;
            }
        }
        int maxCount2 = 0;
        for (int i = 0; i < 256; i++) {
            if ((histogram[i] > maxCount2) && (i != mode)) {
                maxCount2 = histogram[i];
            }
        }
        this.hmax = stats.maxCount;
        if ((hmax > (maxCount2 * 2)) && (maxCount2 != 0)) {
            this.hmax = (int) (maxCount2 * 1.5);
            this.histogram[mode] = hmax;
        }

        this.hColors = new Color[256];
        ImageProcessor ip = imp.getProcessor();
        if (ip != null && ip.getColorModel() instanceof IndexColorModel) {
            IndexColorModel icm = (IndexColorModel) ip.getColorModel();
            if (icm.getMapSize() == 256) {
                byte[] red = new byte[256];
                byte[] green = new byte[256];
                byte[] blue = new byte[256];
                icm.getReds(red);
                icm.getGreens(green);
                icm.getBlues(blue);
                for (int i = 0; i < 256; i++) {
                    hColors[i] = new Color(red[i] & 0xff, green[i] & 0xff, blue[i] & 0xff);
                }
            }
        } else {
            for (int i = 0; i < 256; i++) {
                hColors[i] = new Color(110, 110, 150);
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth() - 1;
        int h = getHeight() - 1;

        // 1. ヒストグラムの描画
        if (histogram != null && hmax != 0) {
            double scaleX = w / 256.0;
            
            int j1 = (int) ((currentMin - defaultMin) / (defaultMax - defaultMin) * 255.0);
            int j2 = (int) ((currentMax - defaultMin) / (defaultMax - defaultMin) * 255.0);
            double colscale = (j2 > j1) ? 255.0 / (j2 - j1) : 1.0;

            for (int i = 0; i < 256; i++) {
                int x = (int) (i * scaleX);
                int j = (int) ((i - j1) * colscale);
                if (i < j1) j = 0;
                if (i > j2) j = 255;
                j = Math.max(0, Math.min(255, j));

                if (hColors != null && hColors[j] != null) {
                    g2.setColor(hColors[j]);
                }
                int barHeight = (int) ((h * histogram[i]) / hmax);
                g2.drawLine(x, h, x, h - barHeight);
            }
        }

        // 2. コントラスト直線の描画（完全な数学的クリッピング）
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));

        double totalRange = defaultMax - defaultMin;
        if (totalRange <= 0) totalRange = 1.0;
        
        // グラフの枠（0〜w）をベースに、MinとMaxがどの位置にくるかを算出（負の値やw超えも許容）
        double rawX1 = (currentMin - defaultMin) / totalRange * w;
        double rawX2 = (currentMax - defaultMin) / totalRange * w;

        // コントラスト幅が0（Min=Max）の場合は垂直線を引いて終了
        if (currentMin == currentMax) {
            int x = (int) rawX1;
            if (x >= 0 && x <= w) {
                g2.drawLine(x, h, x, 0);
            }
        } else {
            // Y座標は Min位置で h (下端), Max位置で 0 (上端)
            // 線の方程式: y - h = ((0 - h) / (rawX2 - rawX1)) * (x - rawX1)
            double slope = -h / (rawX2 - rawX1);
            
            // 描画枠 (0 <= x <= w) で直線をクリップする
            double drawX1 = Math.max(0, rawX1);
            double drawX2 = Math.min(w, rawX2);

            // クリップされたX座標に対応するY座標を計算
            double drawY1 = h + slope * (drawX1 - rawX1);
            double drawY2 = h + slope * (drawX2 - rawX1);

            // 描画範囲内に線が存在する場合のみ描画する
            if (drawX1 <= drawX2) {
                g2.drawLine((int) drawX1, (int) drawY1, (int) drawX2, (int) drawY2);
            }
            
            // 枠外にはみ出ている部分の「水平な天井・床」を描画
            if (rawX1 > 0) {
                g2.drawLine(0, h, (int) Math.min(w, rawX1), h); // 左側の床
            }
            if (rawX2 < w) {
                g2.drawLine((int) Math.max(0, rawX2), 0, w, 0); // 右側の天井
            }
        }

        g2.drawRect(0, 0, w, h);
    }
}