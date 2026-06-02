/**
 * Copyright visionary imaging services, inc.
 */
package com.vis.core.view.D3.roi;

import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.roi.ShapeRoi;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;
import ij.process.ByteProcessor;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.NoninvertibleTransformException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D フリーフォーム ROI。
 * マスクは long[] ビットパック形式で保持し、ByteProcessor より 8 倍メモリ効率が高い。
 * 描画時はビットワード単位でゼロスキップして輪郭ピクセルだけを抽出する。
 * 
 * @author tatsunidas
 */
@SuppressWarnings("serial")
public class FreeFormRoi3D extends RoiObj implements Editable3D {

    public static final String Shape_3D_Type = RoiMetaContextKey.Shape_3D_FREEFORM.name();

    // ボリューム原点 (mm, IPP)
    private double originX, originY, originZ;
    // 方向ベクトル (IOP: rowX,rowY,rowZ, colX,colY,colZ)
    private double[] iop = new double[6];
    // ボクセルサイズ (mm)
    private double spacingX, spacingY, spacingZ;
    // ボリューム次元 (ボクセル数)
    private int dimX, dimY, dimZ;
    // 1 行あたりの long 数 = ⌈dimX / 64⌉
    private int rowStride;

    // バイナリマスクスタック: Z-index → long[] ビットパック (1 bit/pixel)
    private Map<Integer, long[]> maskStack = new ConcurrentHashMap<>();

    // ドラッグ追跡 (画像ピクセル座標)
    private int lastDragImageX = -1;
    private int lastDragImageY = -1;

    // ========== コンストラクタ ==========

    public FreeFormRoi3D(int x, int y, int width, int height, SlideGlass sg) {
        super(x, y, width, height, sg);
        this.type = RoiType.FREEROI.id();
        setProperty(RoiMetaContextKey.Shape_3D_Type.name(), Shape_3D_Type);
    }

    // ========== ボリューム初期設定 ==========

    public void initVolume(double[] originIpp, double[] orientationIop,
                           double[] spacing, int[] dimensions) {
        originX = originIpp[0]; originY = originIpp[1]; originZ = originIpp[2];
        System.arraycopy(orientationIop, 0, iop, 0, 6);
        spacingX = spacing[0]; spacingY = spacing[1]; spacingZ = spacing[2];
        dimX = dimensions[0]; dimY = dimensions[1]; dimZ = dimensions[2];
        rowStride = (dimX + 63) >> 6;
        maskStack.clear();
        persistVolumeMetadata();
    }

    /** DB 読み込み後、プロパティからボリュームメタデータを復元する */
    public void initFromProperties() {
        String originStr  = getProperty("FreeForm3D_Origin");
        String iopStr     = getProperty("FreeForm3D_IOP");
        String spacingStr = getProperty("FreeForm3D_Spacing");
        String dimStr     = getProperty("FreeForm3D_Dim");

        if (originStr != null) {
            String[] p = originStr.split(",");
            if (p.length == 3) {
                originX = Double.parseDouble(p[0].trim());
                originY = Double.parseDouble(p[1].trim());
                originZ = Double.parseDouble(p[2].trim());
            }
        }
        if (iopStr != null) {
            String[] p = iopStr.split(",");
            if (p.length == 6)
                for (int i = 0; i < 6; i++) iop[i] = Double.parseDouble(p[i].trim());
        }
        if (spacingStr != null) {
            String[] p = spacingStr.split(",");
            if (p.length == 3) {
                spacingX = Double.parseDouble(p[0].trim());
                spacingY = Double.parseDouble(p[1].trim());
                spacingZ = Double.parseDouble(p[2].trim());
            }
        }
        if (dimStr != null) {
            String[] p = dimStr.split(",");
            if (p.length == 3) {
                dimX = Integer.parseInt(p[0].trim());
                dimY = Integer.parseInt(p[1].trim());
                dimZ = Integer.parseInt(p[2].trim());
                rowStride = (dimX + 63) >> 6;
            }
        }
    }

    private void persistVolumeMetadata() {
        setProperty("FreeForm3D_Origin",  originX + "," + originY + "," + originZ);
        setProperty("FreeForm3D_IOP",     iop[0]+","+iop[1]+","+iop[2]+","+iop[3]+","+iop[4]+","+iop[5]);
        setProperty("FreeForm3D_Spacing", spacingX + "," + spacingY + "," + spacingZ);
        setProperty("FreeForm3D_Dim",     dimX + "," + dimY + "," + dimZ);
    }

    public boolean isInitialized() { return dimX > 0 && dimY > 0 && dimZ > 0 && rowStride > 0; }

    public double[] getOriginIpp() { return new double[]{originX, originY, originZ}; }
    public double[] getIop()       { return iop.clone(); }
    public double[] getSpacing()   { return new double[]{spacingX, spacingY, spacingZ}; }
    public int[]    getDimensions(){ return new int[]{dimX, dimY, dimZ}; }

    // ========== バイナリマスク操作 ==========

    private boolean getBit(long[] mask, int i, int j) {
        if (i < 0 || i >= dimX || j < 0 || j >= dimY) return false;
        return (mask[j * rowStride + (i >> 6)] & (1L << (i & 63))) != 0;
    }

    private void setBit(long[] mask, int i, int j, boolean val) {
        if (i < 0 || i >= dimX || j < 0 || j >= dimY) return;
        int idx = j * rowStride + (i >> 6);
        long bit = 1L << (i & 63);
        if (val) mask[idx] |= bit;
        else     mask[idx] &= ~bit;
    }

    private long[] newEmptyMask() { return new long[rowStride * dimY]; }

    // OR 演算 (ブラシ追加) — 64 bit 一括処理
    private static void orMasks(long[] dst, long[] src) {
        int n = Math.min(dst.length, src.length);
        for (int i = 0; i < n; i++) dst[i] |= src[i];
    }

    // AND NOT 演算 (ブラシ削除) — 64 bit 一括処理
    private static void andNotMasks(long[] dst, long[] src) {
        int n = Math.min(dst.length, src.length);
        for (int i = 0; i < n; i++) dst[i] &= ~src[i];
    }

    private static boolean isEmptyMask(long[] mask) {
        for (long b : mask) if (b != 0) return false;
        return true;
    }

    /** 描画・統計用に ByteProcessor に変換 (一時使用のみ) */
    public ByteProcessor getMaskAsBytes(int k) {
        if (!isInitialized()) return null;
        long[] mask = maskStack.get(k);
        if (mask == null) return null;
        byte[] data = new byte[dimX * dimY];
        for (int j = 0; j < dimY; j++) {
            int rowOff = j * rowStride;
            for (int iw = 0; iw < rowStride; iw++) {
                long w = mask[rowOff + iw];
                if (w == 0) continue;
                long ww = w;
                while (ww != 0) {
                    int bit = Long.numberOfTrailingZeros(ww);
                    int i = (iw << 6) | bit;
                    if (i < dimX) data[j * dimX + i] = (byte) 255;
                    ww &= ww - 1;
                }
            }
        }
        return new ByteProcessor(dimX, dimY, data, null);
    }

    // ========== 法線・座標変換ヘルパー ==========

    private double[] getNormalUnit() {
        double nx = iop[1]*iop[5] - iop[2]*iop[4];
        double ny = iop[2]*iop[3] - iop[0]*iop[5];
        double nz = iop[0]*iop[4] - iop[1]*iop[3];
        double len = Math.sqrt(nx*nx + ny*ny + nz*nz);
        if (len < 1e-10) return null;
        return new double[]{nx/len, ny/len, nz/len};
    }

    /**
     * 現在スライスに対応する Z インデックスを返す。
     * 範囲外 or 計算不能なら -1。
     */
    public int getZIndexForSlice(double[] sliceIpp) {
        if (!isInitialized() || sliceIpp == null) return -1;
        double[] n = getNormalUnit();
        if (n == null) return -1;
        double vx = sliceIpp[0] - originX;
        double vy = sliceIpp[1] - originY;
        double vz = sliceIpp[2] - originZ;
        double zDist = n[0]*vx + n[1]*vy + n[2]*vz;
        if (spacingZ <= 0) return -1;
        int k = (int) Math.round(zDist / spacingZ);
        return (k >= 0 && k < dimZ) ? k : -1;
    }

    /**
     * ボリューム (i,j,k) → 現在スライス画像ピクセル座標 (x,y) のアフィン変換係数を返す。
     * [a00, a01, tx, a10, a11, ty]
     *   imageX = a00*i + a01*j + tx
     *   imageY = a10*i + a11*j + ty
     */
    private double[] calcVolumeToImageTransform(double[] sliceIpp, double[] sliceIop,
                                                 double spX, double spY, int k) {
        double[] n = getNormalUnit();
        if (n == null) return null;
        // k スライス分移動したボリューム原点
        double ox = originX + n[0]*k*spacingZ;
        double oy = originY + n[1]*k*spacingZ;
        double oz = originZ + n[2]*k*spacingZ;
        double dvx = ox - sliceIpp[0];
        double dvy = oy - sliceIpp[1];
        double dvz = oz - sliceIpp[2];
        double tx = (dvx*sliceIop[0] + dvy*sliceIop[1] + dvz*sliceIop[2]) / spX;
        double ty = (dvx*sliceIop[3] + dvy*sliceIop[4] + dvz*sliceIop[5]) / spY;
        double a00 = (iop[0]*sliceIop[0]+iop[1]*sliceIop[1]+iop[2]*sliceIop[2]) * spacingX / spX;
        double a01 = (iop[3]*sliceIop[0]+iop[4]*sliceIop[1]+iop[5]*sliceIop[2]) * spacingY / spX;
        double a10 = (iop[0]*sliceIop[3]+iop[1]*sliceIop[4]+iop[2]*sliceIop[5]) * spacingX / spY;
        double a11 = (iop[3]*sliceIop[3]+iop[4]*sliceIop[4]+iop[5]*sliceIop[5]) * spacingY / spY;
        return new double[]{a00, a01, tx, a10, a11, ty};
    }

    /**
     * 画像ピクセル座標 (imgX, imgY) → ボリューム格子インデックス (i, j)。
     * 範囲外なら null。
     */
    private int[] imageToVolumeIndex(double imgX, double imgY,
                                      double[] sliceIpp, double[] sliceIop,
                                      double spX, double spY, int k) {
        double[] n = getNormalUnit();
        if (n == null) return null;
        // 画像 mm 座標
        double mmX = imgX * spX;
        double mmY = imgY * spY;
        // 3D 物理座標
        double wx = sliceIpp[0] + sliceIop[0]*mmX + sliceIop[3]*mmY;
        double wy = sliceIpp[1] + sliceIop[1]*mmX + sliceIop[4]*mmY;
        double wz = sliceIpp[2] + sliceIop[2]*mmX + sliceIop[5]*mmY;
        // k スライス分移動した原点からのオフセット
        double ox = originX + n[0]*k*spacingZ;
        double oy = originY + n[1]*k*spacingZ;
        double oz = originZ + n[2]*k*spacingZ;
        double vx = wx - ox;
        double vy = wy - oy;
        double vz = wz - oz;
        int i = (int) Math.round((iop[0]*vx + iop[1]*vy + iop[2]*vz) / spacingX);
        int j = (int) Math.round((iop[3]*vx + iop[4]*vy + iop[5]*vz) / spacingY);
        return (i >= 0 && i < dimX && j >= 0 && j < dimY) ? new int[]{i, j} : null;
    }

    // ========== Editable3D ==========

    @Override
    public void editWithBrush(ShapeRoi brushSnapshot, boolean isAdd) {
        if (slide == null || !isInitialized()) return;
        Praparat pp = slide.getPraparat();
        DicomObject header = slide.getHeader();
        int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
        double[] sliceIop = pp.getSafeIOP(header, frameIdx);
        if (sliceIpp == null || sliceIop == null) return;

        int k = getZIndexForSlice(sliceIpp);
        if (k < 0) return;

        double spX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
        double spY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();

        // ブラシの境界ボックス内でビットマスクを構築
        long[] brushMask = newEmptyMask();
        java.awt.Rectangle bb = brushSnapshot.getBounds();
        for (int bx = bb.x; bx < bb.x + bb.width; bx++) {
            for (int by = bb.y; by < bb.y + bb.height; by++) {
                if (!brushSnapshot.contains(bx, by)) continue;
                int[] v = imageToVolumeIndex(bx, by, sliceIpp, sliceIop, spX, spY, k);
                if (v != null) setBit(brushMask, v[0], v[1], true);
            }
        }

        if (isAdd) {
            long[] existing = maskStack.computeIfAbsent(k, _k -> newEmptyMask());
            orMasks(existing, brushMask);
        } else {
            long[] existing = maskStack.get(k);
            if (existing != null) {
                andNotMasks(existing, brushMask);
                if (isEmptyMask(existing)) maskStack.remove(k);
            }
        }
    }

    // ========== contains ==========

    @Override
    public boolean contains(int x, int y) {
        if (slide == null || !isInitialized()) return false;
        Praparat pp = slide.getPraparat();
        int frameIdx = pp.isMultiFrame() ? slide.getHeader().getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(slide.getHeader(), frameIdx);
        double[] sliceIop = pp.getSafeIOP(slide.getHeader(), frameIdx);
        if (sliceIpp == null || sliceIop == null) return false;
        int k = getZIndexForSlice(sliceIpp);
        if (k < 0) return false;
        long[] mask = maskStack.get(k);
        if (mask == null) return false;
        double spX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
        double spY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();
        int[] v = imageToVolumeIndex(x, y, sliceIpp, sliceIop, spX, spY, k);
        return v != null && getBit(mask, v[0], v[1]);
    }

    public boolean containsPhysicalPoint(double px, double py, double pz) {
        if (!isInitialized()) return false;
        double[] n = getNormalUnit();
        if (n == null) return false;
        double vx = px - originX;
        double vy = py - originY;
        double vz = pz - originZ;
        int k = (int) Math.round((n[0]*vx + n[1]*vy + n[2]*vz) / spacingZ);
        if (k < 0 || k >= dimZ) return false;
        long[] mask = maskStack.get(k);
        if (mask == null) return false;
        int i = (int) Math.round((iop[0]*vx + iop[1]*vy + iop[2]*vz) / spacingX);
        int j = (int) Math.round((iop[3]*vx + iop[4]*vy + iop[5]*vz) / spacingY);
        return getBit(mask, i, j);
    }

    // ========== setVoxel ==========

    public void setVoxel(double px, double py, double pz, boolean value) {
        if (!isInitialized()) return;
        double[] n = getNormalUnit();
        if (n == null) return;
        double vx = px - originX;
        double vy = py - originY;
        double vz = pz - originZ;
        int k = (int) Math.round((n[0]*vx + n[1]*vy + n[2]*vz) / spacingZ);
        if (k < 0 || k >= dimZ) return;
        int i = (int) Math.round((iop[0]*vx + iop[1]*vy + iop[2]*vz) / spacingX);
        int j = (int) Math.round((iop[3]*vx + iop[4]*vy + iop[5]*vz) / spacingY);
        if (value) {
            setBit(maskStack.computeIfAbsent(k, _k -> newEmptyMask()), i, j, true);
        } else {
            long[] mask = maskStack.get(k);
            if (mask != null) {
                setBit(mask, i, j, false);
                if (isEmptyMask(mask)) maskStack.remove(k);
            }
        }
    }

    // ========== マウス操作 (剛体移動) ==========

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
     * 剛体移動: origin 座標だけを更新。マスクデータは一切変更しない。
     */
    @Override
    public void mouseDrag(int dragSX, int dragSY, int flags) {
        if (slide == null || !isInitialized() || getState() != MOVING) return;
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

        Praparat pp = slide.getPraparat();
        int frameIdx = pp.isMultiFrame() ? slide.getHeader().getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIop = pp.getSafeIOP(slide.getHeader(), frameIdx);
        if (sliceIop == null) return;

        double spX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
        double spY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();
        double dX_mm = dix * spX;
        double dY_mm = diy * spY;

        originX += sliceIop[0]*dX_mm + sliceIop[3]*dY_mm;
        originY += sliceIop[1]*dX_mm + sliceIop[4]*dY_mm;
        originZ += sliceIop[2]*dX_mm + sliceIop[5]*dY_mm;

        setProperty("FreeForm3D_Origin", originX + "," + originY + "," + originZ);
    }

    @Override
    public void handleMouseUp(int screenX, int screenY) {
        lastDragImageX = -1;
        lastDragImageY = -1;
        super.handleMouseUp(screenX, screenY);
    }

    // ========== 描画 ==========

    @Override
    public void draw(Graphics g) {
        if (slide == null || g == null || !isInitialized()) return;
        Praparat pp = slide.getPraparat();
        DicomObject header = slide.getHeader();
        int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
        double[] sliceIop = pp.getSafeIOP(header, frameIdx);
        if (sliceIpp == null || sliceIop == null) return;

        int k = getZIndexForSlice(sliceIpp);
        if (k < 0) return;
        long[] mask = maskStack.get(k);
        if (mask == null || isEmptyMask(mask)) return;

        double spX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
        double spY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();
        double[] tf = calcVolumeToImageTransform(sliceIpp, sliceIop, spX, spY, k);
        if (tf == null) return;

        // 状態別カラー
        Color drawColor;
        if (isActiveOverlayRoi() || getState() == MOVING) {
            drawColor = Color.CYAN;
        } else if (isSelected()) {
            drawColor = Color.MAGENTA;
        } else {
            drawColor = getStrokeColor() != null ? getStrokeColor() : RoiObj.getColor();
        }

        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(drawColor);
        g2.setStroke(new BasicStroke(1.0f));

        double a00 = tf[0], a01 = tf[1], tx = tf[2];
        double a10 = tf[3], a11 = tf[4], ty = tf[5];

        // ビットワード単位でゼロスキップしながらエッジピクセルを描画
        for (int j = 0; j < dimY; j++) {
            int rowOff = j * rowStride;
            for (int iw = 0; iw < rowStride; iw++) {
                long word = mask[rowOff + iw];
                if (word == 0) continue; // 64 ピクセルを一括スキップ
                long ww = word;
                while (ww != 0) {
                    int bit = Long.numberOfTrailingZeros(ww);
                    int i = (iw << 6) | bit;
                    if (i < dimX) {
                        // 4 近傍チェック — いずれか未設定ならエッジ
                        if (!getBit(mask, i-1, j) || !getBit(mask, i+1, j)
                                || !getBit(mask, i, j-1) || !getBit(mask, i, j+1)) {
                            int imgX = (int) Math.round(a00*i + a01*j + tx);
                            int imgY = (int) Math.round(a10*i + a11*j + ty);
                            g2.drawRect(imgX, imgY, 1, 1);
                        }
                    }
                    ww &= ww - 1; // 最低ビットをクリア
                }
            }
        }

        // ハンドル (アクティブ / 選択時)
        if (isActiveOverlayRoi() || isSelected()) {
            Rectangle b = getBoundsOnSlice(mask, tf);
            if (b != null && b.width > 0) {
                drawHandle(g, b.x,            b.y);
                drawHandle(g, b.x + b.width/2, b.y);
                drawHandle(g, b.x + b.width,   b.y);
                drawHandle(g, b.x,            b.y + b.height/2);
                drawHandle(g, b.x + b.width,   b.y + b.height/2);
                drawHandle(g, b.x,            b.y + b.height);
                drawHandle(g, b.x + b.width/2, b.y + b.height);
                drawHandle(g, b.x + b.width,   b.y + b.height);
            }
        }
    }

    public void drawHandle(Graphics g, int x, int y) {
        int sz = 5;
        g.setColor(RoiObj.defaultHandleColor);
        g.fillRect(x - sz/2, y - sz/2, sz, sz);
        g.setColor(Color.black);
        g.drawRect(x - sz/2, y - sz/2, sz, sz);
    }

    // ========== getBounds ==========

    @Override
    public Rectangle getBounds() {
        if (slide == null || !isInitialized()) return new Rectangle(x, y, width, height);
        Praparat pp = slide.getPraparat();
        DicomObject header = slide.getHeader();
        int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
        double[] sliceIop = pp.getSafeIOP(header, frameIdx);
        if (sliceIpp == null || sliceIop == null) return new Rectangle(0, 0, 0, 0);
        int k = getZIndexForSlice(sliceIpp);
        if (k < 0) return new Rectangle(0, 0, 0, 0);
        long[] mask = maskStack.get(k);
        if (mask == null) return new Rectangle(0, 0, 0, 0);
        double spX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
        double spY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();
        double[] tf = calcVolumeToImageTransform(sliceIpp, sliceIop, spX, spY, k);
        if (tf == null) return new Rectangle(0, 0, 0, 0);
        Rectangle r = getBoundsOnSlice(mask, tf);
        return r != null ? r : new Rectangle(0, 0, 0, 0);
    }

    private Rectangle getBoundsOnSlice(long[] mask, double[] tf) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        double a00 = tf[0], a01 = tf[1], tx = tf[2];
        double a10 = tf[3], a11 = tf[4], ty = tf[5];
        for (int j = 0; j < dimY; j++) {
            int rowOff = j * rowStride;
            for (int iw = 0; iw < rowStride; iw++) {
                long word = mask[rowOff + iw];
                if (word == 0) continue;
                long ww = word;
                while (ww != 0) {
                    int bit = Long.numberOfTrailingZeros(ww);
                    int i = (iw << 6) | bit;
                    if (i < dimX) {
                        int ix = (int)(a00*i + a01*j + tx);
                        int iy = (int)(a10*i + a11*j + ty);
                        if (ix < minX) minX = ix;
                        if (ix > maxX) maxX = ix;
                        if (iy < minY) minY = iy;
                        if (iy > maxY) maxY = iy;
                    }
                    ww &= ww - 1;
                }
            }
        }
        if (minX == Integer.MAX_VALUE) return null;
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    // ========== clone ==========

    @Override
    public synchronized Object clone() {
        FreeFormRoi3D r = (FreeFormRoi3D) super.clone();
        r.iop = this.iop.clone();
        r.maskStack = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, long[]> e : this.maskStack.entrySet()) {
            r.maskStack.put(e.getKey(), e.getValue().clone());
        }
        r.lastDragImageX = -1;
        r.lastDragImageY = -1;
        return r;
    }
}
