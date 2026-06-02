package com.vis.core.view.D3.roi;

import com.vis.core.view.D2.roi.ShapeRoi;

public interface Editable3D {
    /**
     * 3D ROIをブラシツールで編集するためのインターフェース
     * @param brushSnapshot ブラシの形状 (2D)
     * @param isAdd 追加ならtrue, 削除ならfalse
     */
    void editWithBrush(ShapeRoi brushSnapshot, boolean isAdd);
}
