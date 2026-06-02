/**
 * copyright visionary imaging services, inc.
 */
package com.vis.core.view.D3.roi;

import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.NoninvertibleTransformException;

/**
 * @author tatsunidas
 */
public class SphereRoi3D extends RoiObj {

    private static final long serialVersionUID = 1L;

    public static final String Shape_3D_Type = RoiMetaContextKey.Shape_3D_SPHERE.name();

    private double radiusMm;
    private double cx, cy, cz;
    private int targetC = -1;
    private int targetT = -1;

    // ドラッグ追跡用 (画像ピクセル座標)
    private int lastDragImageX = -1;
    private int lastDragImageY = -1;

    public SphereRoi3D(int x, int y, int width, int height, SlideGlass sg) {
        super(x, y, width, height, sg);
        this.type = RoiType.SPHERE_3D.id();
        setProperty(RoiMetaContextKey.Shape_3D_Type.name(), Shape_3D_Type);
    }

    /** ROI復元用 (DB読み込み時などに呼ばれる) */
    public void initFromProperties() {
        String radiusStr = getProperty(RoiMetaContextKey.Sphere_Radius_mm.name());
        String centerIppStr = getProperty(RoiMetaContextKey.Sphere_Center_IPP.name());

        if (radiusStr != null) {
            this.radiusMm = Double.parseDouble(radiusStr);
        }
        if (centerIppStr != null) {
            String[] parts = centerIppStr.split(",");
            if (parts.length == 3) {
                this.cx = Double.parseDouble(parts[0].trim());
                this.cy = Double.parseDouble(parts[1].trim());
                this.cz = Double.parseDouble(parts[2].trim());
            }
        }

        String cStr = getProperty(RoiMetaContextKey.Dim_C.name());
        String tStr = getProperty(RoiMetaContextKey.Dim_T.name());
        this.targetC = (cStr != null && !cStr.isEmpty()) ? Integer.parseInt(cStr) : -1;
        this.targetT = (tStr != null && !tStr.isEmpty()) ? Integer.parseInt(tStr) : -1;
    }

    public double getRadiusMm() {
        return radiusMm;
    }

    public void setRadiusMm(double radiusMm) {
        this.radiusMm = radiusMm;
        setProperty(RoiMetaContextKey.Sphere_Radius_mm.name(), String.valueOf(radiusMm));
    }

    public void setCenterIpp(double cx, double cy, double cz) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        setProperty(RoiMetaContextKey.Sphere_Center_IPP.name(), cx + "," + cy + "," + cz);
    }

    public double getCenterX() { return cx; }
    public double getCenterY() { return cy; }
    public double getCenterZ() { return cz; }

    /**
     * 現在スライスと球の交差円を計算する。
     * 戻り値: [centerX_px, centerY_px, radiusX_px, radiusY_px] (画像ピクセル座標)。
     * 交差しない場合は null。
     */
    private double[] calcIntersectionOnCurrentSlice(SlideGlass currentSg) {
        if (currentSg == null) return null;

        Praparat pp = currentSg.getPraparat();
        DicomObject header = currentSg.getHeader();
        int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;

        double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
        double[] sliceIop = pp.getSafeIOP(header, frameIdx);
        if (sliceIpp == null || sliceIop == null) return null;

        // スライス平面の法線ベクトル (row × col)、正規化する
        double nx = sliceIop[1] * sliceIop[5] - sliceIop[2] * sliceIop[4];
        double ny = sliceIop[2] * sliceIop[3] - sliceIop[0] * sliceIop[5];
        double nz = sliceIop[0] * sliceIop[4] - sliceIop[1] * sliceIop[3];
        double normalLen = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (normalLen < 1e-10) return null;
        nx /= normalLen;
        ny /= normalLen;
        nz /= normalLen;

        // 球の中心からスライスIPPへのベクトル
        double vx = cx - sliceIpp[0];
        double vy = cy - sliceIpp[1];
        double vz = cz - sliceIpp[2];

        // 球の中心からスライス平面への絶対距離 (mm)
        double d = Math.abs(nx * vx + ny * vy + nz * vz);
        if (d >= radiusMm) return null;

        // 交差円の半径 (mm)
        double r_mm = Math.sqrt(radiusMm * radiusMm - d * d);

        // 球の中心をスライス平面に投影した点の、IPP基準の2D座標 (mm)
        double projX_mm = vx * sliceIop[0] + vy * sliceIop[1] + vz * sliceIop[2];
        double projY_mm = vx * sliceIop[3] + vy * sliceIop[4] + vz * sliceIop[5];

        double pxSpacingX = currentSg.getPixelSpacingX() <= 0 ? 1.0 : currentSg.getPixelSpacingX();
        double pxSpacingY = currentSg.getPixelSpacingY() <= 0 ? 1.0 : currentSg.getPixelSpacingY();

        double pixelX = projX_mm / pxSpacingX;
        double pixelY = projY_mm / pxSpacingY;
        double radiusPxX = r_mm / pxSpacingX;
        double radiusPxY = r_mm / pxSpacingY;

        return new double[]{pixelX, pixelY, radiusPxX, radiusPxY};
    }

    /**
     * 画像ピクセル座標 (x, y) を 3D 実空間に変換し、球の内側かどうかを判定する。
     * OvalRoi は使わず、実空間での距離比較で判定する。
     */
    @Override
    public boolean contains(int x, int y) {
        if (slide == null) return false;
        Praparat pp = slide.getPraparat();
        int frameIdx = pp.isMultiFrame() ? slide.getHeader().getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(slide.getHeader(), frameIdx);
        double[] sliceIop = pp.getSafeIOP(slide.getHeader(), frameIdx);
        if (sliceIpp == null || sliceIop == null) return false;

        double spacingX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
        double spacingY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();

        // 画像ピクセル座標 → 3D 物理座標 (mm)
        double px = sliceIpp[0] + sliceIop[0] * x * spacingX + sliceIop[3] * y * spacingY;
        double py = sliceIpp[1] + sliceIop[1] * x * spacingX + sliceIop[4] * y * spacingY;
        double pz = sliceIpp[2] + sliceIop[2] * x * spacingX + sliceIop[5] * y * spacingY;

        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return (dx * dx + dy * dy + dz * dz) <= (radiusMm * radiusMm);
    }

    @Override
    public void mouseDown(MouseEvent e) {
        if (slide != null) {
            try {
                Point p = slide.offScreenCoordinate(e.getX(), e.getY());
                lastDragImageX = p.x;
                lastDragImageY = p.y;
            } catch (NoninvertibleTransformException ex) {
                lastDragImageX = -1;
                lastDragImageY = -1;
            }
        }
        super.mouseDown(e);
    }

    /**
     * マウスドラッグで球の中心を3D空間上で移動する。
     * dragSX, dragSY はスライドガラス上のスクリーン座標。
     */
    @Override
    public void mouseDrag(int dragSX, int dragSY, int flags) {
        if (slide == null || getState() != MOVING) return;
        Point p;
        try {
            p = slide.offScreenCoordinate(dragSX, dragSY);
        } catch (NoninvertibleTransformException e) {
            return;
        }
        if (lastDragImageX < 0) {
            lastDragImageX = p.x;
            lastDragImageY = p.y;
            return;
        }
        int dix = p.x - lastDragImageX;
        int diy = p.y - lastDragImageY;
        lastDragImageX = p.x;
        lastDragImageY = p.y;

        double spacingX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
        double spacingY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();

        Praparat pp = slide.getPraparat();
        int frameIdx = pp.isMultiFrame() ? slide.getHeader().getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIop = pp.getSafeIOP(slide.getHeader(), frameIdx);
        if (sliceIop == null) return;

        // ピクセルデルタ → mm → 3D ベクトルデルタ
        double dX_mm = dix * spacingX;
        double dY_mm = diy * spacingY;
        cx += sliceIop[0] * dX_mm + sliceIop[3] * dY_mm;
        cy += sliceIop[1] * dX_mm + sliceIop[4] * dY_mm;
        cz += sliceIop[2] * dX_mm + sliceIop[5] * dY_mm;

        setProperty(RoiMetaContextKey.Sphere_Center_IPP.name(), cx + "," + cy + "," + cz);
    }

    @Override
    public void handleMouseUp(int screenX, int screenY) {
        lastDragImageX = -1;
        lastDragImageY = -1;
        super.handleMouseUp(screenX, screenY);
    }

    @Override
    public void draw(Graphics g) {
        if (slide == null || g == null) return;
        double[] intersection = calcIntersectionOnCurrentSlice(slide);
        if (intersection == null) return;

        double icx = intersection[0];
        double icy = intersection[1];
        double rx = intersection[2];
        double ry = intersection[3];

        // 画像ピクセル座標で描画 (CanvasGlass の AffineTransform により画面座標へ自動変換される)
        int xInt = (int) Math.round(icx - rx);
        int yInt = (int) Math.round(icy - ry);
        int wInt = (int) Math.round(rx * 2);
        int hInt = (int) Math.round(ry * 2);

        // カレントROI (ホバー・ドラッグ中) → Cyan、選択中 → Magenta、それ以外 → strokeColor
        java.awt.Color drawColor;
        if (isActiveOverlayRoi() || getState() == MOVING) {
            drawColor = java.awt.Color.CYAN;
        } else if (isSelected()) {
            drawColor = java.awt.Color.MAGENTA;
        } else {
            drawColor = getStrokeColor() != null ? getStrokeColor() : RoiObj.getColor();
        }
        g.setColor(drawColor);
        g.drawOval(xInt, yInt, wInt, hInt);

        if (isActiveOverlayRoi() || isSelected()) {
            drawHandle(g, xInt,            yInt);
            drawHandle(g, xInt + wInt / 2, yInt);
            drawHandle(g, xInt + wInt,     yInt);
            drawHandle(g, xInt,            yInt + hInt / 2);
            drawHandle(g, xInt + wInt,     yInt + hInt / 2);
            drawHandle(g, xInt,            yInt + hInt);
            drawHandle(g, xInt + wInt / 2, yInt + hInt);
            drawHandle(g, xInt + wInt,     yInt + hInt);
        }
    }

    public void drawHandle(Graphics g, int x, int y) {
        int size = 5;
        g.setColor(RoiObj.defaultHandleColor);
        g.fillRect(x - size / 2, y - size / 2, size, size);
        g.setColor(java.awt.Color.black);
        g.drawRect(x - size / 2, y - size / 2, size, size);
    }

    @Override
    public Rectangle getBounds() {
        if (slide == null) return new Rectangle(x, y, width, height);
        double[] intersection = calcIntersectionOnCurrentSlice(slide);
        if (intersection == null) return new Rectangle(0, 0, 0, 0);

        int xInt = (int) Math.round(intersection[0] - intersection[2]);
        int yInt = (int) Math.round(intersection[1] - intersection[3]);
        int wInt = (int) Math.round(intersection[2] * 2);
        int hInt = (int) Math.round(intersection[3] * 2);
        return new Rectangle(xInt, yInt, wInt, hInt);
    }

    @Override
    public synchronized Object clone() {
        SphereRoi3D r = (SphereRoi3D) super.clone();
        r.radiusMm = this.radiusMm;
        r.cx = this.cx;
        r.cy = this.cy;
        r.cz = this.cz;
        r.targetC = this.targetC;
        r.targetT = this.targetT;
        r.lastDragImageX = -1;
        r.lastDragImageY = -1;
        return r;
    }
}
