package com.vis.core.view.D3.ui;

public class MeshMeasureResult {
    public double surfaceAreaMm2;
    public double volumeMm3;
    public double longDiameterMm;
    public double midDiameterMm;
    public double shortDiameterMm;

    public String toShortString() {
        return String.format("%.1f cm²  /  %.2f mL",
                surfaceAreaMm2 / 100.0, volumeMm3 / 1000.0);
    }
}
