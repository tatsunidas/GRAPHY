/**
 * © Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D3.ui; // パッケージ名は環境に合わせてください

import com.vis.core.view.D3.roi.FreeFormRoi3D;

public class VolumeData {

	// データ型を定義する列挙型（DOUBLEはFLOATとして扱う）
	public enum DataType {
		BYTE, SHORT, FLOAT, RGB
	}

	public int width;
	public int height;
	public int depth; // スライス枚数
	public double pixelSpacingX;
	public double pixelSpacingY;
	public double sliceThickness;
	// ==========================================================
    // ★追加: このボリュームの3D空間における絶対的な幾何情報（World Coordinates）
    // ==========================================================
    public double[] startIpp; // ボリュームの原点（最初のボクセルの物理座標）
    public double[] iop;      // 方向ベクトル (Row X,Y,Z, Col X,Y,Z)
    public double[] stepZ;    // スライス進行方向のベクトル

	// どの型のデータかを保持
	public DataType dataType;

	// 汎用的に保持するため Object 型に変更（実際には各プリミティブ配列が入る）
	public Object data;

	// float型データにも対応できるよう、最小・最大値は float に変更
	public float minVal;
	public float maxVal;

	// ==========================================
	// コンストラクタのオーバーロード
	// ==========================================

	// 1. 8-bit Grayscale (Byte)
	public VolumeData(int w, int h, int d, byte[] data) {
		init(w, h, d, data, DataType.BYTE);
	}

	// 2. 16-bit Grayscale (Short)
	public VolumeData(int w, int h, int d, short[] data) {
		init(w, h, d, data, DataType.SHORT);
	}

	// 3. 32-bit Float
	public VolumeData(int w, int h, int d, float[] data) {
		init(w, h, d, data, DataType.FLOAT);
	}

	// 4. RGB (ImageJのColorProcessorは int[] のPacked ARGB/RGB)
	public VolumeData(int w, int h, int d, int[] data) {
		init(w, h, d, data, DataType.RGB);
	}

	// 64-bit Double が来た場合は float に変換して処理する
	public VolumeData(int w, int h, int d, double[] inData) {
		System.out.println("Processing double array as float array.");

		float[] floatData = new float[inData.length];
		for (int i = 0; i < inData.length; i++) {
			floatData[i] = (float) inData[i];
		}

		// 変換した float 配列を使って初期化
		init(w, h, d, floatData, DataType.FLOAT);
	}

	// --- 共通の初期化処理 ---
	private void init(int w, int h, int d, Object data, DataType type) {
		this.width = w;
		this.height = h;
		this.depth = d;
		this.data = data;
		this.dataType = type;
		calculateMinMax();
	}

	// ==========================================
	// 最小値・最大値の計算
	// ==========================================
	private void calculateMinMax() {
		if (dataType == DataType.RGB) {
			minVal = 0f;
			maxVal = 255f;
			System.out.println("Data Type: RGB (Packed int), Range ignored.");
			return;
		}

		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;

		switch (dataType) {
		case BYTE:
			byte[] byteArr = (byte[]) data;
			for (byte b : byteArr) {
				float val = b & 0xFF; // Unsigned byte (0~255)
				if (val < min)
					min = val;
				if (val > max)
					max = val;
			}
			break;

		case SHORT:
			short[] shortArr = (short[]) data;
			for (short s : shortArr) {
				float val = s & 0xFFFF; // Unsigned short (0~65535)
				if (val < min)
					min = val;
				if (val > max)
					max = val;
			}
			break;

		case FLOAT:
			float[] floatArr = (float[]) data;
			for (float f : floatArr) {
				if (!Float.isNaN(f)) {
					if (f < min)
						min = f;
					if (f > max)
						max = f;
				}
			}
			break;

		default:
			break;
		}

		this.minVal = min;
		this.maxVal = max;
		System.out.println("Data Type: " + dataType + " | Range: " + minVal + " ~ " + maxVal);
	}
	
	public static byte[] createRoiMask(VolumeData vol, FreeFormRoi3D roi, double[] volumeOriginIpp, double[] volumeIop) {
	    int w = vol.width;
	    int h = vol.height;
	    int d = vol.depth;
	    byte[] mask = new byte[w * h * d];
	    
	    // IOP (Row/Colベクトル) と 法線 (Zベクトル) を計算
	    double[] n = new double[3];
	    n[0] = volumeIop[1]*volumeIop[5] - volumeIop[2]*volumeIop[4];
	    n[1] = volumeIop[2]*volumeIop[3] - volumeIop[0]*volumeIop[5];
	    n[2] = volumeIop[0]*volumeIop[4] - volumeIop[1]*volumeIop[3];

	    int index = 0;
	    for (int z = 0; z < d; z++) {
	        for (int y = 0; y < h; y++) {
	            for (int x = 0; x < w; x++) {
	                // ボクセルから物理座標 (mm) を計算
	                double px = volumeOriginIpp[0] + volumeIop[0]*(x * vol.pixelSpacingX) + volumeIop[3]*(y * vol.pixelSpacingY) + n[0]*(z * vol.sliceThickness);
	                double py = volumeOriginIpp[1] + volumeIop[1]*(x * vol.pixelSpacingX) + volumeIop[4]*(y * vol.pixelSpacingY) + n[1]*(z * vol.sliceThickness);
	                double pz = volumeOriginIpp[2] + volumeIop[2]*(x * vol.pixelSpacingX) + volumeIop[5]*(y * vol.pixelSpacingY) + n[2]*(z * vol.sliceThickness);
	                
	                // ROIに含まれているかチェック
	                if (roi.containsPhysicalPoint(px, py, pz)) {
	                    mask[index] = (byte) 255;
	                } else {
	                    mask[index] = 0;
	                }
	                index++;
	            }
	        }
	    }
	    return mask;
	}
}