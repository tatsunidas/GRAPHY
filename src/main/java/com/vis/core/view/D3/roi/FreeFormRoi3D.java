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
 * 3D フリーフォーム ROI。 マスクは long[] ビットパック形式で保持(ByteProcessor より 8 倍メモリ効率が高い)
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
		this.type = RoiType.FREEFOAM_3D.id();
		setProperty(RoiMetaContextKey.Shape_3D_Type.name(), Shape_3D_Type);
	}

	// ========== ボリューム初期設定 ==========

	public void initVolume(double[] originIpp, double[] orientationIop, double[] spacing, int[] dimensions) {
		originX = originIpp[0];
		originY = originIpp[1];
		originZ = originIpp[2];
		System.arraycopy(orientationIop, 0, iop, 0, 6);
		spacingX = spacing[0];
		spacingY = spacing[1];
		spacingZ = spacing[2];
		dimX = dimensions[0];
		dimY = dimensions[1];
		dimZ = dimensions[2];
		rowStride = (dimX + 63) >> 6;
		maskStack.clear();
		persistVolumeMetadata();
	}

	/** DB 読み込み後、プロパティからボリュームメタデータを復元する */
	public void initFromProperties() {
		String originStr = getProperty("FreeForm3D_Origin");
		String iopStr = getProperty("FreeForm3D_IOP");
		String spacingStr = getProperty("FreeForm3D_Spacing");
		String dimStr = getProperty("FreeForm3D_Dim");

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
				for (int i = 0; i < 6; i++)
					iop[i] = Double.parseDouble(p[i].trim());
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
		maskStack.clear();
		if (this.props != null) {
			for (Object keyObj : this.props.keySet()) {
				String key = keyObj.toString();
				if (key.startsWith("FreeForm3D_Mask_")) {
					try {
						int zIndex = Integer.parseInt(key.replace("FreeForm3D_Mask_", ""));
						String base64 = this.props.getProperty(key);
						byte[] bytes = java.util.Base64.getDecoder().decode(base64);
						java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
						long[] mask = new long[bytes.length / 8];
						for (int i = 0; i < mask.length; i++) {
							mask[i] = bb.getLong();
						}
						maskStack.put(zIndex, mask);
					} catch (Exception e) {
						com.vis.core.log.Log.logger.warning("Failed to decode FreeForm mask data: " + key);
					}
				}
			}
		}
	}

	private void persistVolumeMetadata() {
		setProperty("FreeForm3D_Origin", originX + "," + originY + "," + originZ);
		setProperty("FreeForm3D_IOP",
				iop[0] + "," + iop[1] + "," + iop[2] + "," + iop[3] + "," + iop[4] + "," + iop[5]);
		setProperty("FreeForm3D_Spacing", spacingX + "," + spacingY + "," + spacingZ);
		setProperty("FreeForm3D_Dim", dimX + "," + dimY + "," + dimZ);
	}

	public boolean isInitialized() {
		return dimX > 0 && dimY > 0 && dimZ > 0 && rowStride > 0;
	}

	public double[] getOriginIpp() {
		return new double[] { originX, originY, originZ };
	}

	public double[] getIop() {
		return iop.clone();
	}

	public double[] getSpacing() {
		return new double[] { spacingX, spacingY, spacingZ };
	}

	public int[] getDimensions() {
		return new int[] { dimX, dimY, dimZ };
	}
	
	private SlideGlass getActiveSlideContext() {
        if (slide == null) return null;
        Praparat pp = slide.getPraparat();
        if (pp != null) {
            SlideGlass currentSg = pp.getCurrentSlide();
            if (currentSg != null) return currentSg;
        }
        return slide;
    }

	// ========== バイナリマスク操作 ==========

	private boolean getBit(long[] mask, int i, int j) {
		if (i < 0 || i >= dimX || j < 0 || j >= dimY)
			return false;
		return (mask[j * rowStride + (i >> 6)] & (1L << (i & 63))) != 0;
	}
	

	private void setBit(long[] mask, int i, int j, boolean val) {
		if (i < 0 || i >= dimX || j < 0 || j >= dimY)
			return;
		int idx = j * rowStride + (i >> 6);
		long bit = 1L << (i & 63);
		if (val)
			mask[idx] |= bit;
		else
			mask[idx] &= ~bit;
	}

	private long[] newEmptyMask() {
		return new long[rowStride * dimY];
	}

	// OR 演算 (ブラシ追加) — 64 bit 一括処理
	private static void orMasks(long[] dst, long[] src) {
		int n = Math.min(dst.length, src.length);
		for (int i = 0; i < n; i++)
			dst[i] |= src[i];
	}

	// AND NOT 演算 (ブラシ削除) — 64 bit 一括処理
	private static void andNotMasks(long[] dst, long[] src) {
		int n = Math.min(dst.length, src.length);
		for (int i = 0; i < n; i++)
			dst[i] &= ~src[i];
	}

	private static boolean isEmptyMask(long[] mask) {
		for (long b : mask)
			if (b != 0)
				return false;
		return true;
	}

	/** 描画・統計用に ByteProcessor に変換 (一時使用のみ) */
	public ByteProcessor getMaskAsBytes(int k) {
		if (!isInitialized())
			return null;
		long[] mask = maskStack.get(k);
		if (mask == null)
			return null;
		byte[] data = new byte[dimX * dimY];
		for (int j = 0; j < dimY; j++) {
			int rowOff = j * rowStride;
			for (int iw = 0; iw < rowStride; iw++) {
				long w = mask[rowOff + iw];
				if (w == 0)
					continue;
				long ww = w;
				while (ww != 0) {
					int bit = Long.numberOfTrailingZeros(ww);
					int i = (iw << 6) | bit;
					if (i < dimX)
						data[j * dimX + i] = (byte) 255;
					ww &= ww - 1;
				}
			}
		}
		return new ByteProcessor(dimX, dimY, data, null);
	}

	// ========== 法線・座標変換ヘルパー ==========

	private double[] getNormalUnit() {
		double nx = iop[1] * iop[5] - iop[2] * iop[4];
		double ny = iop[2] * iop[3] - iop[0] * iop[5];
		double nz = iop[0] * iop[4] - iop[1] * iop[3];
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-10)
			return null;
		return new double[] { nx / len, ny / len, nz / len };
	}

	/**
	 * 現在スライスに対応する Z インデックスを返す。 範囲外 or 計算不能なら -1。
	 */
	public int getZIndexForSlice(double[] sliceIpp) {
		if (!isInitialized() || sliceIpp == null)
			return -1;
		double[] n = getNormalUnit();
		if (n == null)
			return -1;
		double vx = sliceIpp[0] - originX;
		double vy = sliceIpp[1] - originY;
		double vz = sliceIpp[2] - originZ;
		double zDist = n[0] * vx + n[1] * vy + n[2] * vz;
		if (spacingZ <= 0)
			return -1;
		int k = (int) Math.round(zDist / spacingZ);
		return (k >= 0 && k < dimZ) ? k : -1;
	}

	/**
	 * ボリューム (i,j,k) → 現在スライス画像ピクセル座標 (x,y) のアフィン変換係数を返す。 [a00, a01, tx, a10, a11,
	 * ty] imageX = a00*i + a01*j + tx imageY = a10*i + a11*j + ty
	 */
	private double[] calcVolumeToImageTransform(double[] sliceIpp, double[] sliceIop, double spX, double spY, int k) {
		double[] n = getNormalUnit();
		if (n == null)
			return null;
		// k スライス分移動したボリューム原点
		double ox = originX + n[0] * k * spacingZ;
		double oy = originY + n[1] * k * spacingZ;
		double oz = originZ + n[2] * k * spacingZ;
		double dvx = ox - sliceIpp[0];
		double dvy = oy - sliceIpp[1];
		double dvz = oz - sliceIpp[2];
		double tx = (dvx * sliceIop[0] + dvy * sliceIop[1] + dvz * sliceIop[2]) / spX;
		double ty = (dvx * sliceIop[3] + dvy * sliceIop[4] + dvz * sliceIop[5]) / spY;
		double a00 = (iop[0] * sliceIop[0] + iop[1] * sliceIop[1] + iop[2] * sliceIop[2]) * spacingX / spX;
		double a01 = (iop[3] * sliceIop[0] + iop[4] * sliceIop[1] + iop[5] * sliceIop[2]) * spacingY / spX;
		double a10 = (iop[0] * sliceIop[3] + iop[1] * sliceIop[4] + iop[2] * sliceIop[5]) * spacingX / spY;
		double a11 = (iop[3] * sliceIop[3] + iop[4] * sliceIop[4] + iop[5] * sliceIop[5]) * spacingY / spY;
		return new double[] { a00, a01, tx, a10, a11, ty };
	}

	/**
	 * 画像ピクセル座標 (imgX, imgY) → ボリューム格子インデックス (i, j)。 範囲外なら null。
	 */
	private int[] imageToVolumeIndex(double imgX, double imgY, double[] sliceIpp, double[] sliceIop, double spX,
			double spY, int k) {
		double[] n = getNormalUnit();
		if (n == null)
			return null;
		// 画像 mm 座標
		double mmX = imgX * spX;
		double mmY = imgY * spY;
		// 3D 物理座標
		double wx = sliceIpp[0] + sliceIop[0] * mmX + sliceIop[3] * mmY;
		double wy = sliceIpp[1] + sliceIop[1] * mmX + sliceIop[4] * mmY;
		double wz = sliceIpp[2] + sliceIop[2] * mmX + sliceIop[5] * mmY;
		// k スライス分移動した原点からのオフセット
		double ox = originX + n[0] * k * spacingZ;
		double oy = originY + n[1] * k * spacingZ;
		double oz = originZ + n[2] * k * spacingZ;
		double vx = wx - ox;
		double vy = wy - oy;
		double vz = wz - oz;
		int i = (int) Math.round((iop[0] * vx + iop[1] * vy + iop[2] * vz) / spacingX);
		int j = (int) Math.round((iop[3] * vx + iop[4] * vy + iop[5] * vz) / spacingY);
		return (i >= 0 && i < dimX && j >= 0 && j < dimY) ? new int[] { i, j } : null;
	}

	// ========== Editable3D ==========

	@Override
	public void editWithBrush(ShapeRoi brushSnapshot, boolean isAdd) {
		if (slide == null || !isInitialized())
			return;
		Praparat pp = slide.getPraparat();
		DicomObject header = slide.getHeader();
		int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;
		double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
		double[] sliceIop = pp.getSafeIOP(header, frameIdx);
		if (sliceIpp == null || sliceIop == null)
			return;

		int k = getZIndexForSlice(sliceIpp);
		if (k < 0)
			return;

		double spX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
		double spY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();

		// ブラシの境界ボックス内でビットマスクを構築
		long[] brushMask = newEmptyMask();
		java.awt.Rectangle bb = brushSnapshot.getBounds();
		for (int bx = bb.x; bx < bb.x + bb.width; bx++) {
			for (int by = bb.y; by < bb.y + bb.height; by++) {
				if (!brushSnapshot.contains(bx, by))
					continue;
				int[] v = imageToVolumeIndex(bx, by, sliceIpp, sliceIop, spX, spY, k);
				if (v != null)
					setBit(brushMask, v[0], v[1], true);
			}
		}

		if (isAdd) {
			long[] existing = maskStack.computeIfAbsent(k, _k -> newEmptyMask());
			orMasks(existing, brushMask);
		} else {
			long[] existing = maskStack.get(k);
			if (existing != null) {
				andNotMasks(existing, brushMask);
				if (isEmptyMask(existing))
					maskStack.remove(k);
			}
		}
	}

	public boolean containsPhysicalPoint(double px, double py, double pz) {
		if (!isInitialized())
			return false;
		double[] n = getNormalUnit();
		if (n == null)
			return false;
		double vx = px - originX;
		double vy = py - originY;
		double vz = pz - originZ;
		int k = (int) Math.round((n[0] * vx + n[1] * vy + n[2] * vz) / spacingZ);
		if (k < 0 || k >= dimZ)
			return false;
		long[] mask = maskStack.get(k);
		if (mask == null)
			return false;
		int i = (int) Math.round((iop[0] * vx + iop[1] * vy + iop[2] * vz) / spacingX);
		int j = (int) Math.round((iop[3] * vx + iop[4] * vy + iop[5] * vz) / spacingY);
		return getBit(mask, i, j);
	}

	// ========== setVoxel ==========

	public void setVoxel(double px, double py, double pz, boolean value) {
		if (!isInitialized())
			return;
		double[] n = getNormalUnit();
		if (n == null)
			return;
		double vx = px - originX;
		double vy = py - originY;
		double vz = pz - originZ;
		int k = (int) Math.round((n[0] * vx + n[1] * vy + n[2] * vz) / spacingZ);
		if (k < 0 || k >= dimZ)
			return;
		int i = (int) Math.round((iop[0] * vx + iop[1] * vy + iop[2] * vz) / spacingX);
		int j = (int) Math.round((iop[3] * vx + iop[4] * vy + iop[5] * vz) / spacingY);
		if (value) {
			setBit(maskStack.computeIfAbsent(k, _k -> newEmptyMask()), i, j, true);
		} else {
			long[] mask = maskStack.get(k);
			if (mask != null) {
				setBit(mask, i, j, false);
				if (isEmptyMask(mask))
					maskStack.remove(k);
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
		if (slide == null || !isInitialized() || getState() != MOVING)
			return;
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
		if (sliceIop == null)
			return;

		double spX = slide.getPixelSpacingX() <= 0 ? 1.0 : slide.getPixelSpacingX();
		double spY = slide.getPixelSpacingY() <= 0 ? 1.0 : slide.getPixelSpacingY();
		double dX_mm = dix * spX;
		double dY_mm = diy * spY;

		originX += sliceIop[0] * dX_mm + sliceIop[3] * dY_mm;
		originY += sliceIop[1] * dX_mm + sliceIop[4] * dY_mm;
		originZ += sliceIop[2] * dX_mm + sliceIop[5] * dY_mm;

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
        SlideGlass contextSg = getActiveSlideContext();
        if (contextSg == null || g == null || !isInitialized())
            return;
        Praparat pp = contextSg.getPraparat();
        DicomObject header = contextSg.getHeader();
        int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
        double[] sliceIop = pp.getSafeIOP(header, frameIdx);
        if (sliceIpp == null || sliceIop == null)
            return;

        int k = getZIndexForSlice(sliceIpp);
        if (k < 0)
            return;
        long[] mask = maskStack.get(k);
        if (mask == null || isEmptyMask(mask))
            return;

        double spX = contextSg.getPixelSpacingX() <= 0 ? 1.0 : contextSg.getPixelSpacingX();
        double spY = contextSg.getPixelSpacingY() <= 0 ? 1.0 : contextSg.getPixelSpacingY();
        double[] tf = calcVolumeToImageTransform(sliceIpp, sliceIop, spX, spY, k);
        if (tf == null)
            return;

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
                if (word == 0) continue;
                long ww = word;
                while (ww != 0) {
                    int bit = Long.numberOfTrailingZeros(ww);
                    int i = (iw << 6) | bit;
                    if (i < dimX) {
                        // 4 近傍チェック — いずれか未設定ならエッジ
                        if (!getBit(mask, i - 1, j) || !getBit(mask, i + 1, j) || !getBit(mask, i, j - 1) || !getBit(mask, i, j + 1)) {
                            int imgX = (int) Math.round(a00 * i + a01 * j + tx);
                            int imgY = (int) Math.round(a10 * i + a11 * j + ty);
                            g2.drawRect(imgX, imgY, 1, 1);
                        }
                    }
                    ww &= ww - 1; 
                }
            }
        }

        // ハンドル描画 (省略)
        if (isActiveOverlayRoi() || isSelected()) {
            Rectangle b = getBoundsOnSlice(mask, tf);
            if (b != null && b.width > 0) {
                drawHandle(g, b.x, b.y);
                drawHandle(g, b.x + b.width / 2, b.y);
                drawHandle(g, b.x + b.width, b.y);
                drawHandle(g, b.x, b.y + b.height / 2);
                drawHandle(g, b.x + b.width, b.y + b.height / 2);
                drawHandle(g, b.x, b.y + b.height);
                drawHandle(g, b.x + b.width / 2, b.y + b.height);
                drawHandle(g, b.x + b.width, b.y + b.height);
            }
        }
    }

    // ========== contains ==========

    @Override
    public boolean contains(int x, int y) {
        SlideGlass contextSg = getActiveSlideContext();
        if (contextSg == null || !isInitialized())
            return false;
        Praparat pp = contextSg.getPraparat();
        int frameIdx = pp.isMultiFrame() ? contextSg.getHeader().getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(contextSg.getHeader(), frameIdx);
        double[] sliceIop = pp.getSafeIOP(contextSg.getHeader(), frameIdx);
        if (sliceIpp == null || sliceIop == null)
            return false;
        int k = getZIndexForSlice(sliceIpp);
        if (k < 0)
            return false;
        long[] mask = maskStack.get(k);
        if (mask == null)
            return false;
        double spX = contextSg.getPixelSpacingX() <= 0 ? 1.0 : contextSg.getPixelSpacingX();
        double spY = contextSg.getPixelSpacingY() <= 0 ? 1.0 : contextSg.getPixelSpacingY();
        int[] v = imageToVolumeIndex(x, y, sliceIpp, sliceIop, spX, spY, k);
        return v != null && getBit(mask, v[0], v[1]);
    }

    // ========== getBounds ==========

    @Override
    public Rectangle getBounds() {
        SlideGlass contextSg = getActiveSlideContext();
        if (contextSg == null || !isInitialized())
            return new Rectangle(x, y, width, height);
        Praparat pp = contextSg.getPraparat();
        DicomObject header = contextSg.getHeader();
        int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;
        double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
        double[] sliceIop = pp.getSafeIOP(header, frameIdx);
        if (sliceIpp == null || sliceIop == null)
            return new Rectangle(0, 0, 0, 0);
        int k = getZIndexForSlice(sliceIpp);
        if (k < 0)
            return new Rectangle(0, 0, 0, 0);
        long[] mask = maskStack.get(k);
        if (mask == null)
            return new Rectangle(0, 0, 0, 0);
        double spX = contextSg.getPixelSpacingX() <= 0 ? 1.0 : contextSg.getPixelSpacingX();
        double spY = contextSg.getPixelSpacingY() <= 0 ? 1.0 : contextSg.getPixelSpacingY();
        double[] tf = calcVolumeToImageTransform(sliceIpp, sliceIop, spX, spY, k);
        if (tf == null)
            return new Rectangle(0, 0, 0, 0);
        Rectangle r = getBoundsOnSlice(mask, tf);
        return r != null ? r : new Rectangle(0, 0, 0, 0);
    }

	public void drawHandle(Graphics g, int x, int y) {
		int sz = 5;
		g.setColor(RoiObj.defaultHandleColor);
		g.fillRect(x - sz / 2, y - sz / 2, sz, sz);
		g.setColor(Color.black);
		g.drawRect(x - sz / 2, y - sz / 2, sz, sz);
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
				if (word == 0)
					continue;
				long ww = word;
				while (ww != 0) {
					int bit = Long.numberOfTrailingZeros(ww);
					int i = (iw << 6) | bit;
					if (i < dimX) {
						int ix = (int) (a00 * i + a01 * j + tx);
						int iy = (int) (a10 * i + a11 * j + ty);
						if (ix < minX)
							minX = ix;
						if (ix > maxX)
							maxX = ix;
						if (iy < minY)
							minY = iy;
						if (iy > maxY)
							maxY = iy;
					}
					ww &= ww - 1;
				}
			}
		}
		if (minX == Integer.MAX_VALUE)
			return null;
		return new Rectangle(minX, minY, maxX - minX, maxY - minY);
	}
	
	@Override
	public java.util.HashMap<String, Object> readContext() {
		// 基本のメタデータをプロパティに反映
		persistVolumeMetadata();

		// ★ 追加: maskStack (ボクセルデータ) を Base64 エンコードしてプロパティに退避
		for (java.util.Map.Entry<Integer, long[]> entry : maskStack.entrySet()) {
			long[] mask = entry.getValue();
			java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(mask.length * 8);
			for (long l : mask) {
				bb.putLong(l);
			}
			String base64 = java.util.Base64.getEncoder().encodeToString(bb.array());
			setProperty("FreeForm3D_Mask_" + entry.getKey(), base64);
		}

		return super.readContext();
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

	// ==========================================================
	// ★ ファクトリメソッド & バリデーション
	// ==========================================================

	/**
	 * 2D ROIのリストをボクセル化し、1つの FreeFormRoi3D にパッキングして生成します。 * @param pp 空間基準となる
	 * Praparat
	 * 
	 * @param rois    3D化する対象の RoiObj リスト
	 * @param groupId 新しく割り当てるグループID
	 * @return 生成された FreeFormRoi3D
	 * @throws IllegalArgumentException 非対応のROIが含まれている場合
	 */
	public static FreeFormRoi3D createFrom2DRois(Praparat pp, java.util.List<RoiObj> rois, String groupId) {
		if (pp == null || pp.getAllSlides().isEmpty() || rois == null || rois.isEmpty()) {
			return null;
		}

		// 1. バリデーションの実行（不適合なROIがあれば例外を投げる）
		validateRoisFor3D(rois);

		// 2. 空間の基準となる情報の取得（Praparatの先頭スライスを利用）
		SlideGlass firstSg = pp.getAllSlides().get(0);
		com.vis.dicom.DicomObject header = firstSg.getHeader();
		int frameIdx = pp.isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;
		double[] originIpp = pp.getSafeIPP(header, frameIdx);
		double[] iop = pp.getSafeIOP(header, frameIdx);

		double spX = firstSg.getPixelSpacingX() <= 0 ? 1.0 : firstSg.getPixelSpacingX();
		double spY = firstSg.getPixelSpacingY() <= 0 ? 1.0 : firstSg.getPixelSpacingY();
		double spZ = header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices,
				header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
		if (spZ <= 0)
			spZ = 1.0;

		int dimX = firstSg.getOriginalImageSize().width;
		int dimY = firstSg.getOriginalImageSize().height;
		int dimZ = pp.getAllSlides().size(); // 全スライス数

		// 3. FreeFormRoi3D インスタンスの生成とボリューム初期化
		FreeFormRoi3D roi3d = new FreeFormRoi3D(0, 0, dimX, dimY, firstSg);
		roi3d.setProperty(com.vis.configuration.RoiDBKey.RoiGroup.name(), groupId);
		roi3d.initVolume(originIpp, iop, new double[] { spX, spY, spZ }, new int[] { dimX, dimY, dimZ });

		// 4. リスト内の各2D ROIをボクセル化してマスクに焼き付ける
		for (RoiObj r : rois) {
			SlideGlass sg = r.getSlideGlass();
			if (sg != null) {
				// ★ 既存の編集ロジック（editWithBrush）を流用するためのコンテキスト注入
				roi3d.setSlideGlass(sg, false);

				// ShapeRoi に変換（PointやLineもこれでピクセル単位の領域として扱えるようになる）
				com.vis.core.view.D2.roi.ShapeRoi shape = (r instanceof com.vis.core.view.D2.roi.ShapeRoi)
						? (com.vis.core.view.D2.roi.ShapeRoi) r
						: new com.vis.core.view.D2.roi.ShapeRoi(r);

				// マスクに加算 (isAdd = true)
				roi3d.editWithBrush(shape, true);
			}
		}

		return roi3d;
	}

	/**
	 * 3Dボリューム化の対象として適切なROIタイプか検証します。 Text, Arrow, Image
	 * 等の「アノテーション」系ROIが含まれる場合は例外をスローします。
	 */
	public static void validateRoisFor3D(java.util.List<RoiObj> rois) {
		for (RoiObj r : rois) {
			int type = r.getType();
			if (type == RoiType.TEXT.id() || r instanceof com.vis.core.view.D2.roi.TextRoi
					|| r instanceof com.vis.core.view.D2.roi.ImageRoi || r instanceof com.vis.core.view.D2.roi.Arrow) {

				throw new IllegalArgumentException(
						"Validation Error: Text, Arrow, and Image ROIs are annotations and cannot be converted to a 3D volume mask.");
			}
		}
	}

	// ==========================================================
	// ★ ブーリアン演算 (3D Voxel Boolean Operations)
	// ==========================================================

	/** 空間情報（次元や原点）が完全に一致しているか検証します */
	private boolean isSameVolumeSpace(FreeFormRoi3D other) {
		if (this.dimX != other.dimX || this.dimY != other.dimY || this.dimZ != other.dimZ)
			return false;
		if (this.rowStride != other.rowStride)
			return false;
		// 厳密には origin や spacing の float 誤差チェックも必要ですが、
		// 同じ Praparat から生成された前提であれば次元チェックで十分機能します。
		return true;
	}

	/** OR (Combine): 両方のボクセルを合成します */
	public void or(FreeFormRoi3D other) {
		if (!isSameVolumeSpace(other))
			return;
		for (Map.Entry<Integer, long[]> entry : other.maskStack.entrySet()) {
			int z = entry.getKey();
			long[] srcMask = entry.getValue();
			long[] dstMask = this.maskStack.computeIfAbsent(z, k -> newEmptyMask());
			orMasks(dstMask, srcMask);
		}
	}

	/** AND: 両方に共通するボクセルだけを残します */
	public void and(FreeFormRoi3D other) {
		if (!isSameVolumeSpace(other))
			return;
		// 共通していないZスライスは結果が空になるため削除
		this.maskStack.keySet().retainAll(other.maskStack.keySet());

		for (Map.Entry<Integer, long[]> entry : this.maskStack.entrySet()) {
			int z = entry.getKey();
			long[] dstMask = entry.getValue();
			long[] srcMask = other.maskStack.get(z);

			if (srcMask == null) {
				this.maskStack.remove(z);
			} else {
				// AND演算: a & b
				int n = Math.min(dstMask.length, srcMask.length);
				for (int i = 0; i < n; i++)
					dstMask[i] &= srcMask[i];
				if (isEmptyMask(dstMask))
					this.maskStack.remove(z);
			}
		}
	}

	/** XOR: 重なっている部分をくり抜き、重なっていない部分を残します */
	public void xor(FreeFormRoi3D other) {
		if (!isSameVolumeSpace(other))
			return;
		for (Map.Entry<Integer, long[]> entry : other.maskStack.entrySet()) {
			int z = entry.getKey();
			long[] srcMask = entry.getValue();
			long[] dstMask = this.maskStack.computeIfAbsent(z, k -> newEmptyMask());

			// XOR演算: a ^ b
			int n = Math.min(dstMask.length, srcMask.length);
			for (int i = 0; i < n; i++)
				dstMask[i] ^= srcMask[i];
			if (isEmptyMask(dstMask))
				this.maskStack.remove(z);
		}
	}
	
	/**
	 * SphereRoi3D をボクセル化し、FreeFormRoi3D に変換します。
	 */
	public static FreeFormRoi3D createFromSphere(Praparat pp, com.vis.core.view.D3.roi.SphereRoi3D sphere, String groupId) {
		if (pp == null || sphere == null || pp.getAllSlides().isEmpty()) return null;

		SlideGlass firstSg = pp.getAllSlides().get(0);
		com.vis.dicom.DicomObject header = firstSg.getHeader();
		int frameIdx = pp.isMultiFrame() ? header.getInt(com.vis.dicom.Tag.InstanceNumber, 1) - 1 : 0;
		double[] originIpp = pp.getSafeIPP(header, frameIdx);
		double[] iop = pp.getSafeIOP(header, frameIdx);

		double spX = firstSg.getPixelSpacingX() <= 0 ? 1.0 : firstSg.getPixelSpacingX();
		double spY = firstSg.getPixelSpacingY() <= 0 ? 1.0 : firstSg.getPixelSpacingY();
		double spZ = header.getDouble(com.vis.dicom.Tag.SpacingBetweenSlices, header.getDouble(com.vis.dicom.Tag.SliceThickness, 1.0));
		if (spZ <= 0) spZ = 1.0;

		int dimX = firstSg.getOriginalImageSize().width;
		int dimY = firstSg.getOriginalImageSize().height;
		int dimZ = pp.getAllSlides().size();

		FreeFormRoi3D roi3d = new FreeFormRoi3D(0, 0, dimX, dimY, firstSg);
		roi3d.setProperty(com.vis.configuration.RoiDBKey.RoiGroup.name(), groupId);
		roi3d.initVolume(originIpp, iop, new double[]{spX, spY, spZ}, new int[]{dimX, dimY, dimZ});

		// 球のパラメータ
		double cx = sphere.getCenterX(), cy = sphere.getCenterY(), cz = sphere.getCenterZ();
		double r2 = sphere.getRadiusMm() * sphere.getRadiusMm();

		// 法線ベクトル
		double[] n = roi3d.getNormalUnit();
		if (n == null) return null;

		// 全ボクセルをスキャンして球の内部にあるか判定して焼き付ける
		for (int k = 0; k < dimZ; k++) {
			double oz = originIpp[2] + n[2] * k * spZ;
			double oy = originIpp[1] + n[1] * k * spZ;
			double ox = originIpp[0] + n[0] * k * spZ;

			boolean sliceHasData = false;
			long[] mask = roi3d.newEmptyMask();

			for (int j = 0; j < dimY; j++) {
				for (int i = 0; i < dimX; i++) {
					// ボクセルの物理座標 (mm)
					double px = ox + iop[0] * (i * spX) + iop[3] * (j * spY);
					double py = oy + iop[1] * (i * spX) + iop[4] * (j * spY);
					double pz = oz + iop[2] * (i * spX) + iop[5] * (j * spY);

					double dx = px - cx, dy = py - cy, dz = pz - cz;
					if ((dx*dx + dy*dy + dz*dz) <= r2) {
						roi3d.setBit(mask, i, j, true);
						sliceHasData = true;
					}
				}
			}
			if (sliceHasData) roi3d.maskStack.put(k, mask);
		}
		return roi3d;
	}
	
	// ==========================================================
	// ★ Split: 3D連結成分ラベリング (Connected Component Labeling)
	// ==========================================================

	public java.util.List<FreeFormRoi3D> splitIntoConnectedComponents() {
		java.util.List<FreeFormRoi3D> components = new java.util.ArrayList<>();
		if (!isInitialized() || maskStack.isEmpty())
			return components;

		// 1. 作業用にマスクの完全なコピーを作成（探索済みのボクセルを削っていくため）
		Map<Integer, long[]> workStack = new java.util.HashMap<>();
		for (Map.Entry<Integer, long[]> e : this.maskStack.entrySet()) {
			workStack.put(e.getKey(), e.getValue().clone());
		}

		SlideGlass sg = this.getSlideGlass();
		if (sg == null)
			return components; // フォールバック

		// 2. 全ボクセルをスキャンしてシード（起点）を探す
		for (int z = 0; z < dimZ; z++) {
			long[] maskZ = workStack.get(z);
			if (maskZ == null)
				continue;

			for (int j = 0; j < dimY; j++) {
				for (int i = 0; i < dimX; i++) {
					if (getBit(maskZ, i, j)) {
						// 3. シードを発見！新しい塊(FreeFormRoi3D)を作成して探索開始
						FreeFormRoi3D component = (FreeFormRoi3D) this.clone();
						component.maskStack.clear(); // 中身を空にする
						component.setProperty(com.vis.configuration.RoiDBKey.RoiID.name(), RoiObj.createRoiIndex());

						// 幅優先探索 (BFS) で繋がっているボクセルを全て回収
						java.util.Queue<int[]> queue = new java.util.LinkedList<>();
						queue.add(new int[] { i, j, z });
						setBit(maskZ, i, j, false); // 作業用マスクから消去

						while (!queue.isEmpty()) {
							int[] p = queue.poll();
							int cx = p[0], cy = p[1], cz = p[2];

							// 塊の方にボクセルを登録
							long[] compMask = component.maskStack.computeIfAbsent(cz, k -> newEmptyMask());
							setBit(compMask, cx, cy, true);

							// 6近傍 (上下左右前後) を探索
							int[][] neighbors = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
									{ 0, 0, -1 } };

							for (int[] dir : neighbors) {
								int nx = cx + dir[0], ny = cy + dir[1], nz = cz + dir[2];
								if (nx >= 0 && nx < dimX && ny >= 0 && ny < dimY && nz >= 0 && nz < dimZ) {
									long[] nMask = workStack.get(nz);
									if (nMask != null && getBit(nMask, nx, ny)) {
										setBit(nMask, nx, ny, false); // 消去してキューへ
										queue.add(new int[] { nx, ny, nz });
									}
								}
							}
						}
						// 塊の回収完了
						components.add(component);
					}
				}
			}
		}
		return components;
	}
}
