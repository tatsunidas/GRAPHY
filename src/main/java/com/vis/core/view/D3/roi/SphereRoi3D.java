/**
 * Copyright visionary imaging services, inc.
 */
package com.vis.core.view.D3.roi; // パッケージ名は環境に合わせてください

import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.view.D2.roi.OvalRoi;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.CanvasGlass;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

import java.util.HashMap;
import java.util.Iterator;

/**
 * TODO
 * 
 * 将来的には、SphereRoi3Dは、RoiObjの子クラスとして、各種メソッドをOverrideさせる。
 * 中心座標と、ボリュームの左上座標（IPP）、IOP、半径を保持させる。
 * 位置検出は、この球の断面内にマウスがあるかどうかで判定させる。
 * drawで、この球とスライス断面が接する線を、スライス上に描画すればよい。
 * ただし、SlideGlass単位のROIの管理を、Praparatで行うように改修する必要がある。
 * 
 * @author tatsunidas
 */
public class SphereRoi3D extends AbstractRoi3D {
	
	public final String Shape_3D_Type = "SPHERE";

    private double radiusMm;
    private double cx, cy, cz;
    private int targetC = -1;
    private int targetT = -1;
    private int masterZ = -1; // ★追加: MasterのZインデックスを保持

    /**
     * マスターとなるRoiObjを渡すことで、内部状態(中心座標や半径)を初期化します。
     */
    public SphereRoi3D(RoiObj masterRoi) {
        super(masterRoi.getProperty(RoiDBKey.RoiGroup.name()));
        
        String radiusStr = masterRoi.getProperty(RoiMetaContextKey.Sphere_Radius_mm.name());
        String centerIppStr = masterRoi.getProperty(RoiMetaContextKey.Sphere_Center_IPP.name());
        
        if (radiusStr != null) this.radiusMm = Double.parseDouble(radiusStr);
        if (centerIppStr != null) {
            String[] parts = centerIppStr.split(",");
            this.cx = Double.parseDouble(parts[0].trim());
            this.cy = Double.parseDouble(parts[1].trim());
            this.cz = Double.parseDouble(parts[2].trim());
        }
        
        String cStr = masterRoi.getProperty(RoiMetaContextKey.Dim_C.name());
        String zStr = masterRoi.getProperty(RoiMetaContextKey.Dim_Z.name()); // ★追加
        String tStr = masterRoi.getProperty(RoiMetaContextKey.Dim_T.name());
        
        this.targetC = (cStr != null && !cStr.isEmpty()) ? Integer.parseInt(cStr) : -1;
        this.masterZ = (zStr != null && !zStr.isEmpty()) ? Integer.parseInt(zStr) : -1;
        this.targetT = (tStr != null && !tStr.isEmpty()) ? Integer.parseInt(tStr) : -1;
    }

    @Override
    public void updateFrom2D(RoiObj modifiedRoi) {
        Praparat pp = modifiedRoi.getSlideGlass().getPraparat();
        if (pp == null) return;

        // ★追加: Masterの最新Zインデックスを更新
        String zStr = modifiedRoi.getProperty(RoiMetaContextKey.Dim_Z.name());
        this.masterZ = (zStr != null && !zStr.isEmpty()) ? Integer.parseInt(zStr) : -1;

        // 1. 自分以外のグループ内ROIを一括削除
        for (SlideGlass sg : pp.getAllSlides().values()) {
            if (sg == null) continue;
            CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
            if (cg != null) {
                java.util.ArrayList<RoiObj> roiset = sg.getRois();
                if (roiset != null) {
                    Iterator<RoiObj> it = roiset.iterator();
                    while (it.hasNext()) {
                        RoiObj r = it.next();
                        if (r != modifiedRoi && groupId.equals(r.getProperty(RoiDBKey.RoiGroup.name()))) {
                            it.remove();
                            HashMap<RoiDBKey, String> uids = r.getUIDs();
                            DatabaseHandler.getInstance().deleteRoi(
                                uids.get(RoiDBKey.PatientID), uids.get(RoiDBKey.StudyInstanceUID),
                                uids.get(RoiDBKey.SeriesInstanceUID), uids.get(RoiDBKey.SOPInstanceUID),
                                uids.get(RoiDBKey.RoiID)
                            );
                        }
                    }
                    cg.repaint();
                }
            }
        }

        // 2. この modifiedRoi を「真のマスター」として3D空間座標(IPP)を再計算
        SlideGlass sg = modifiedRoi.getSlideGlass();
        double pixelSpacingX = sg.getPixelSpacingX() <= 0 ? 1.0 : sg.getPixelSpacingX();
        double pixelSpacingY = sg.getPixelSpacingY() <= 0 ? 1.0 : sg.getPixelSpacingY();

        double imageX = modifiedRoi.getXBase() + modifiedRoi.getBounds().width / 2.0;
        double imageY = modifiedRoi.getYBase() + modifiedRoi.getBounds().height / 2.0;

        DicomObject header = sg.getHeader();
        int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;
        
        double[] currentIpp = pp.getSafeIPP(header, frameIdx);
        double[] iop = pp.getSafeIOP(header, frameIdx);

        if (currentIpp != null && currentIpp.length == 3 && iop != null && iop.length == 6) {
            this.cx = currentIpp[0] + iop[0] * imageX * pixelSpacingX + iop[3] * imageY * pixelSpacingY;
            this.cy = currentIpp[1] + iop[1] * imageX * pixelSpacingX + iop[4] * imageY * pixelSpacingY;
            this.cz = currentIpp[2] + iop[2] * imageX * pixelSpacingX + iop[5] * imageY * pixelSpacingY;
            modifiedRoi.setProperty(RoiMetaContextKey.Sphere_Center_IPP.name(), cx + "," + cy + "," + cz);
        }

        modifiedRoi.setProperty(RoiMetaContextKey.Is3D_Master.name(), "true");
        modifiedRoi.setProperty(RoiMetaContextKey.Is3D_Slave.name(), null);

        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db != null) {
            db.insertRoi(modifiedRoi.readContext());
        }

        // 3. 再展開を指示
        generateCrossSections(pp);
    }

    @Override
    public void generateCrossSections(Praparat pp) {
        if (pp == null || masterZ == -1) return;

        // ★ Masterスライドからスライス厚（Slice Thickness / Spacing Between Slices）を取得
        int masterZctIdx = pp.calcZctIndex(new int[]{masterZ, targetC, targetT});
        SlideGlass masterSg = pp.getAllSlides().get(masterZctIdx);
        if (masterSg == null) return;
        
        DicomObject masterHeader = masterSg.getHeader();
        double sliceThickness = 1.0;
        if (masterHeader != null) {
            // スライス間隔を優先し、無ければスライス厚を使用。どちらも無ければ1.0mmとする
            sliceThickness = masterHeader.getDouble(Tag.SpacingBetweenSlices, 
                             masterHeader.getDouble(Tag.SliceThickness, 1.0));
            if (sliceThickness <= 0) sliceThickness = 1.0; // フェイルセーフ
        }

        // ★ Masterを中心に上下何スライスまで描画するかを整数で計算（切り捨て）
        int maxSlicesAway = (int) Math.floor(radiusMm / sliceThickness);

        // ★ Masterを中心に、完全対称なインデックスループを回す
        for (int diff = -maxSlicesAway; diff <= maxSlicesAway; diff++) {
            
            // Master自身は描画済みなのでスキップ
            if (diff == 0) continue;

            int targetZ = masterZ + diff;
            int zctIdx = pp.calcZctIndex(new int[]{targetZ, targetC, targetT});
            SlideGlass sg = pp.getAllSlides().get(zctIdx);
            
            // スライドが存在しない（範囲外）ならスキップ
            if (sg == null) continue;

            // ★ 距離(d)は「スライス枚数差分 × スライス厚」で厳密に計算（IPP計算誤差を排除）
            double d = Math.abs(diff) * sliceThickness;
            
            // 念のための安全チェック（半径を超えていないか）
            if (d >= radiusMm) continue;

            // 断面の半径をピタゴラスの定理で算出
            double r_mm = Math.sqrt(radiusMm * radiusMm - d * d);

            // ----- ここから下は、XYの座標を対象スライスに正確に投影する処理（変更なし） -----
            DicomObject header = sg.getHeader();
            int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;

            double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
            double[] sliceIop = pp.getSafeIOP(header, frameIdx);
            if (sliceIpp == null || sliceIop == null) continue;

            double vx = cx - sliceIpp[0];
            double vy = cy - sliceIpp[1];
            double vz = cz - sliceIpp[2];

            // 投影座標（スライスのXY平面上の位置）
            double projX_mm = vx * sliceIop[0] + vy * sliceIop[1] + vz * sliceIop[2];
            double projY_mm = vx * sliceIop[3] + vy * sliceIop[4] + vz * sliceIop[5];

            double pxSpacingX = sg.getPixelSpacingX() <= 0 ? 1.0 : sg.getPixelSpacingX();
            double pxSpacingY = sg.getPixelSpacingY() <= 0 ? 1.0 : sg.getPixelSpacingY();

            double pixelX = projX_mm / pxSpacingX;
            double pixelY = projY_mm / pxSpacingY;

            double radiusPxX = r_mm / pxSpacingX;
            double radiusPxY = r_mm / pxSpacingY;

            int startX = (int) (pixelX - radiusPxX);
            int startY = (int) (pixelY - radiusPxY);
            int width = (int) (radiusPxX * 2.0);
            int height = (int) (radiusPxY * 2.0);

            OvalRoi slaveRoi = new OvalRoi(startX, startY, width, height, sg);
            slaveRoi.setState(RoiObj.NORMAL);

            slaveRoi.setProperty(RoiDBKey.RoiGroup.name(), groupId);
            slaveRoi.setProperty(RoiMetaContextKey.Shape_3D_Type.name(), Shape_3D_Type);
            slaveRoi.setProperty(RoiMetaContextKey.Sphere_Radius_mm.name(), String.valueOf(radiusMm));
            slaveRoi.setProperty(RoiMetaContextKey.Dim_C.name(), String.valueOf(targetC));
            slaveRoi.setProperty(RoiMetaContextKey.Dim_Z.name(), String.valueOf(targetZ));
            slaveRoi.setProperty(RoiMetaContextKey.Dim_T.name(), String.valueOf(targetT));
            slaveRoi.setProperty(RoiMetaContextKey.Is3D_Slave.name(), "true");

            CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
            if (cg != null) {
                cg.addRoi(slaveRoi);
                sg.repaintCanvasGlass();
            }
        }
        
        // UI更新
        if (com.vis.core.facade.WindowManager.getWindow(com.vis.configuration.ConfigInfo.RoiManager) != null) {
            com.vis.core.view.D2.roi.RoiObjManager.getInstance().updateState();
        }
    }
}