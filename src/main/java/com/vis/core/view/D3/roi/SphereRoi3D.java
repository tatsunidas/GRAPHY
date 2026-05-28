package com.vis.core.view.D3.roi; // パッケージ名は環境に合わせてください

import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.log.Log;
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
import java.util.Map;

public class SphereRoi3D extends AbstractRoi3D {
	
	public final String Shape_3D_Type = "SPHERE";

    private double radiusMm;
    private double cx, cy, cz;
    private int targetC = -1;
    private int targetT = -1;

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
        String tStr = masterRoi.getProperty(RoiMetaContextKey.Dim_T.name());
        this.targetC = (cStr != null && !cStr.isEmpty()) ? Integer.parseInt(cStr) : -1;
        this.targetT = (tStr != null && !tStr.isEmpty()) ? Integer.parseInt(tStr) : -1;
    }

    @Override
    public void updateFrom2D(RoiObj modifiedRoi) {
        Praparat pp = modifiedRoi.getSlideGlass().getPraparat();
        if (pp == null) return;

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
        if (pp == null) return;

        for (Map.Entry<Integer, SlideGlass> entry : pp.getAllSlides().entrySet()) {
            SlideGlass sg = entry.getValue();

            // マスターがいるCanvasの処理は updateFrom2D で完了しているのでスキップ
            // (本来はIDで判定するのがより安全ですが、今回は簡易的にスライドで判定)
            int[] zct = pp.getZCTArray(sg);
            if (targetC != -1 && targetC != zct[1]) continue;
            if (targetT != -1 && targetT != zct[2]) continue;

            DicomObject header = sg.getHeader();
            int frameIdx = pp.isMultiFrame() ? header.getInt(Tag.InstanceNumber, 1) - 1 : 0;

            double[] sliceIpp = pp.getSafeIPP(header, frameIdx);
            double[] sliceIop = pp.getSafeIOP(header, frameIdx);

            if (sliceIpp == null || sliceIop == null) continue;

            double nx = sliceIop[1] * sliceIop[5] - sliceIop[2] * sliceIop[4];
            double ny = sliceIop[2] * sliceIop[3] - sliceIop[0] * sliceIop[5];
            double nz = sliceIop[0] * sliceIop[4] - sliceIop[1] * sliceIop[3];

            double vx = cx - sliceIpp[0];
            double vy = cy - sliceIpp[1];
            double vz = cz - sliceIpp[2];

            double d = Math.abs(vx * nx + vy * ny + vz * nz);

            // 交差判定
            if (d < radiusMm) {
                // 距離が0（マスター自身が乗っているスライス）の場合は新規作成をスキップ
                if (d < 1e-3) continue;

                double r_mm = Math.sqrt(radiusMm * radiusMm - d * d);
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
                slaveRoi.setProperty(RoiMetaContextKey.Dim_C.name(), String.valueOf(zct[1]));
                slaveRoi.setProperty(RoiMetaContextKey.Dim_Z.name(), String.valueOf(zct[0]));
                slaveRoi.setProperty(RoiMetaContextKey.Dim_T.name(), String.valueOf(zct[2]));
                slaveRoi.setProperty(RoiMetaContextKey.Is3D_Slave.name(), "true");

                CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
                if (cg != null) {
                    cg.addRoi(slaveRoi);
                    sg.repaintCanvasGlass();
                }
            }
        }
        
        // UI更新
        if (com.vis.core.facade.WindowManager.getWindow(com.vis.configuration.ConfigInfo.RoiManager) != null) {
            com.vis.core.view.D2.roi.RoiObjManager.getInstance().updateState();
        }
    }
}