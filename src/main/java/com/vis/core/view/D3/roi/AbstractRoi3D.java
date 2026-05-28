package com.vis.core.view.D3.roi; // パッケージ名は環境に合わせてください

import com.vis.configuration.RoiDBKey;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.CanvasGlass;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.db.DatabaseHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public abstract class AbstractRoi3D {
    protected String groupId;

    public AbstractRoi3D(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * 2D画面での操作(移動やサイズ変更)を親玉に伝達し、3D空間座標を再計算・更新します。
     */
    public abstract void updateFrom2D(RoiObj modifiedRoi);

    /**
     * 3Dモデルに基づいて、対象となる全スライスに2D断面(スレイブ)を展開します。
     */
    public abstract void generateCrossSections(Praparat pp);

    /**
     * Praparat.java にあった一括削除ロジックをここに集約。
     * この3D-ROIに属するすべての2D ROI(マスター・スレイブ問わず)を画面とDBから削除します。
     */
    public void deleteGroup(Praparat pp) {
        if (groupId == null || pp == null) return;
        DatabaseHandler db = DatabaseHandler.getInstance();

        for (SlideGlass sg : pp.getAllSlides().values()) {
            if (sg == null) continue;
            CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
            if (cg != null) {
                ArrayList<RoiObj> roiset = sg.getRois();
                if (roiset != null) {
                    Iterator<RoiObj> it = roiset.iterator();
                    boolean removed = false;
                    while (it.hasNext()) {
                        RoiObj r = it.next();
                        String gId = r.getProperty(RoiDBKey.RoiGroup.name());
                        if (groupId.equals(gId)) {
                            it.remove();
                            HashMap<RoiDBKey, String> uids = r.getUIDs();
                            if (db != null) {
                                db.deleteRoi(
                                    uids.get(RoiDBKey.PatientID),
                                    uids.get(RoiDBKey.StudyInstanceUID),
                                    uids.get(RoiDBKey.SeriesInstanceUID),
                                    uids.get(RoiDBKey.SOPInstanceUID),
                                    uids.get(RoiDBKey.RoiID)
                                );
                            }
                            removed = true;
                        }
                    }
                    if (removed) cg.repaint();
                }
            }
        }
    }
}