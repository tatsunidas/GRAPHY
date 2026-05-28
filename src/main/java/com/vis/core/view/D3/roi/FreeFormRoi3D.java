/**
 * Copyright visionary imaging services, inc.
 */
package com.vis.core.view.D3.roi;

import com.vis.configuration.RoiDBKey;
import com.vis.configuration.RoiMetaContextKey;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.db.DatabaseHandler;

/**
 * @author tatsunidas
 */
public class FreeFormRoi3D extends AbstractRoi3D {
	
	public final String Shape_3D_Type = "FREEDOM";

    public FreeFormRoi3D(RoiObj masterRoi) {
        super(masterRoi.getProperty(RoiDBKey.RoiGroup.name()));
    }

    @Override
    public void updateFrom2D(RoiObj modifiedRoi) {
        // フリーフォームの場合、2D画面での変更（頂点の移動や形状変更）は、
        // 「そのスライス単体のローカルな編集」として扱うのが一般的です。
        // よって、変更された当該スライスのROIのみをDBに上書き保存します。
        
        modifiedRoi.setProperty(RoiMetaContextKey.Shape_3D_Type.name(), Shape_3D_Type);
        
        DatabaseHandler db = DatabaseHandler.getInstance();
        if (db != null) {
            db.insertRoi(modifiedRoi.readContext());
        }
        
        // ※ もし将来「1つのスライスを移動させたら、他の全スライスの断面も連動して平行移動させる(剛体移動)」
        // という仕様にしたい場合は、ここに移動量(dx, dy)を計算して他スライドを更新する処理を追加します。
    }

    @Override
    public void generateCrossSections(Praparat pp) {
        // フリーフォームは数式で他スライスの形状を推測・自動生成できないため、何もしません。
        // （ユーザーが手描きした実体のみで構成されるため）
        
        // ただし、Managerのリスト更新や画面の再描画を促すために使います。
        if (com.vis.core.facade.WindowManager.getWindow(com.vis.configuration.ConfigInfo.RoiManager) != null) {
            com.vis.core.view.D2.roi.RoiObjManager.getInstance().updateState();
        }
    }
}