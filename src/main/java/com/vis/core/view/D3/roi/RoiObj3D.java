package com.vis.core.view.D3.roi;

import ij.process.ByteProcessor;
import com.vis.core.view.D3.ui.VolumeData;

public interface RoiObj3D {
    
    /** ボリュームの原点座標(IPP) [x, y, z] を返します */
    public double[] getOriginIpp();

    /** ボリュームの方向ベクトル(IOP) [rowX..Z, colX..Z] を返します */
    public double[] getIop();

    /** ボクセルサイズ(Spacing) [x, y, z] (mm) を返します */
    public double[] getSpacing();

    /** ボリュームのサイズ(Dimension) [幅, 高さ, 奥行き] (ボクセル数) を返します */
    public int[] getDimensions();

    /** 指定したZスライスのバイナリマスク(占有領域)を返します。範囲外ならnull */
    public ByteProcessor getMaskAsBytes(int z);

    /** メッシュ化(Marching Cubes)用に、全スライスのマスクを結合したVolumeDataを生成して返します */
    public VolumeData getVolumeDataForMesh();

    /** ボクセルまたは数式ベースの体積 (mm³) を返します */
    public double getCalculatedVolumeMm3();
}