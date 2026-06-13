package com.vis.core.view.D3.util;

import com.vis.core.view.D3.ui.VolumeData;

public class AlignMesh {

	// ==========================================================
	// メッシュの座標をGLCanvasのボリューム描画空間に合わせる
	// ==========================================================
	public static void alignMeshToVolume(com.vis.core.view.D3.ui.MeshData mesh, VolumeData vol) {
		float spacingX = (float) vol.pixelSpacingX;
		float spacingY = (float) vol.pixelSpacingY;
		float spacingZ = (float) vol.sliceThickness;

		float physX = vol.width * spacingX;
		float physY = vol.height * spacingY;
		float physZ = vol.depth * spacingZ;

		float cx = physX / 2.0f;
		float cy = physY / 2.0f;
		float cz = physZ / 2.0f;

		// ==========================================================
		// 半ボクセル分のズレを補正するためのオフセット値
		// ==========================================================
		float offsetX = spacingX * 0.5f;
		float offsetY = spacingY * 0.5f;
		float offsetZ = spacingZ * 0.5f;

		for (int i = 0; i < mesh.vertices.length; i += 3) {
			// 半ボクセル分足して位置を補正し、そこから中心(cx, cy, cz)を引く
			float x = (mesh.vertices[i] + offsetX - cx) / physX;
			float y = (mesh.vertices[i + 1] + offsetY - cy) / physY;
			float z = (mesh.vertices[i + 2] + offsetZ - cz) / physZ;

			mesh.vertices[i] = x;
			mesh.vertices[i + 1] = y;
			mesh.vertices[i + 2] = z;
		}
	}
}
