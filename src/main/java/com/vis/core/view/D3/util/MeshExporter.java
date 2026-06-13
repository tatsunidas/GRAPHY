package com.vis.core.view.D3.util;

import com.vis.core.view.D3.ui.MeshData;
import com.vis.core.log.Log;
import org.joml.Vector3f;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 3Dメッシュデータを外部ファイル（STLなど）に書き出すユーティリティクラス
 */
public class MeshExporter {

    /**
     * メッシュデータを標準的なバイナリSTL形式としてエクスポートします。
     * * @param file 保存先のファイルオブジェクト
     * @param mesh エクスポート対象のMeshData（★必ずalign前の実寸mmメッシュを渡すこと）
     * @throws IOException ファイル書き込みエラーが発生した場合
     */
    public static void exportToBinarySTL(File file, MeshData mesh) throws IOException {
        if (mesh == null || mesh.vertices == null || mesh.indices == null) {
            throw new IllegalArgumentException("MeshData is incomplete or null.");
        }

        float[] vertices = mesh.vertices;
        int[] indices = mesh.indices;
        int numTriangles = indices.length / 3;

        Log.logger.info("Exporting " + numTriangles + " triangles to binary STL: " + file.getAbsolutePath());

        // 大量のデータを高速に書き出すため、BufferedOutputStreamを使用
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            
            // 1. ヘッダーの書き込み (80バイトのカスタムテキスト、またはゼロクリア)
            byte[] header = new byte[80];
            String headerStr = "VIS 3D Viewer Generated STL. Scale: mm.";
            byte[] headerBytes = headerStr.getBytes("UTF-8");
            System.arraycopy(headerBytes, 0, header, 0, Math.min(headerBytes.length, header.length));
            out.write(header);

            // 2. 三角形総数の書き込み (4バイト、リトルエンディアンの int)
            ByteBuffer numBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            numBuffer.putInt(numTriangles);
            out.write(numBuffer.array());

            // 3. 三角形データの書き込み (1つの三角形あたり 50 バイトの固定長ループ)
            // [法線(12バイト:f*3)] + [頂点1(12バイト:f*3)] + [頂点2(12バイト:f*3)] + [頂点3(12バイト:f*3)] + [属性(2バイト)]
            ByteBuffer triBuffer = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < indices.length; i += 3) {
                int i0 = indices[i] * 3;
                int i1 = indices[i + 1] * 3;
                int i2 = indices[i + 2] * 3;

                // 3つの頂点座標を取得
                Vector3f v0 = new Vector3f(vertices[i0], vertices[i0 + 1], vertices[i0 + 2]);
                Vector3f v1 = new Vector3f(vertices[i1], vertices[i1 + 1], vertices[i1 + 2]);
                Vector3f v2 = new Vector3f(vertices[i2], vertices[i2 + 1], vertices[i2 + 2]);

                // 面法線をその場で簡易計算（もし事前計算された法線配列 mesh.normals を使いたい場合は、そこの値を流用してもOK）
                Vector3f edge1 = new Vector3f();
                v1.sub(v0, edge1);
                Vector3f edge2 = new Vector3f();
                v2.sub(v0, edge2);
                Vector3f normal = new Vector3f();
                edge1.cross(edge2, normal);
                if (normal.lengthSquared() > 0) {
                    normal.normalize();
                }

                // バッファをクリアして1つの三角形データを詰める
                triBuffer.clear();

                // 法線ベクトル (Facet Normal)
                triBuffer.putFloat(normal.x);
                triBuffer.putFloat(normal.y);
                triBuffer.putFloat(normal.z);

                // 頂点1, 2, 3 (Vertex 1, 2, 3)
                triBuffer.putFloat(v0.x); triBuffer.putFloat(v0.y); triBuffer.putFloat(v0.z);
                triBuffer.putFloat(v1.x); triBuffer.putFloat(v1.y); triBuffer.putFloat(v1.z);
                triBuffer.putFloat(v2.x); triBuffer.putFloat(v2.y); triBuffer.putFloat(v2.z);

                // 属性バイト (Attribute byte count - バイナリSTLでは通常常に 0)
                triBuffer.putShort((short) 0);

                // ストリームに一気に書き出す
                out.write(triBuffer.array());
            }

            out.flush();
            Log.logger.info("STL Export completed successfully.");
        }
    }
}