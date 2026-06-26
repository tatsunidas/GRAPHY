package com.vis.core.view.D3.util;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.vis.core.view.D3.ui.MeshData;

/**
 * Möller–Trumbore ray–triangle intersection against a MeshData in render space.
 * The ray is derived by unprojecting the screen coordinate through the inverse MVP.
 */
public class RayMeshIntersector {

    /**
     * @param mesh    Rendered mesh whose vertices are in GL render space (–0.5 … 0.5).
     * @param mvp     The same MVP (proj * view * model) used when rendering.
     * @param screenX Mouse X in logical (AWT) pixels, origin top-left.
     * @param screenY Mouse Y in logical (AWT) pixels, origin top-left.
     * @param width   Canvas logical width.
     * @param height  Canvas logical height.
     * @return Hit point in render space, or {@code null} if no triangle is hit.
     */
    public static float[] intersect(MeshData mesh, Matrix4f mvp,
                                    int screenX, int screenY,
                                    int width, int height) {
        if (mesh == null || mesh.vertices == null || mesh.indices == null) return null;

        float ndcX =  (2.0f * screenX / width)  - 1.0f;
        float ndcY = -(2.0f * screenY / height) + 1.0f;

        Matrix4f inv = new Matrix4f(mvp).invert();

        Vector4f nearH = new Vector4f(ndcX, ndcY, -1.0f, 1.0f).mul(inv);
        nearH.div(nearH.w);
        Vector4f farH  = new Vector4f(ndcX, ndcY,  1.0f, 1.0f).mul(inv);
        farH.div(farH.w);

        Vector3f orig = new Vector3f(nearH.x, nearH.y, nearH.z);
        Vector3f dir  = new Vector3f(farH.x - nearH.x,
                                     farH.y - nearH.y,
                                     farH.z - nearH.z).normalize();

        return moellerTrumbore(mesh.vertices, mesh.indices, orig, dir);
    }

    private static float[] moellerTrumbore(float[] verts, int[] idx,
                                            Vector3f orig, Vector3f dir) {
        final float EPS = 1e-7f;
        float tMin = Float.MAX_VALUE;
        float[] hit = null;

        for (int i = 0; i < idx.length; i += 3) {
            int i0 = idx[i] * 3, i1 = idx[i + 1] * 3, i2 = idx[i + 2] * 3;

            float e1x = verts[i1]   - verts[i0],
                  e1y = verts[i1+1] - verts[i0+1],
                  e1z = verts[i1+2] - verts[i0+2];
            float e2x = verts[i2]   - verts[i0],
                  e2y = verts[i2+1] - verts[i0+1],
                  e2z = verts[i2+2] - verts[i0+2];

            float hx = dir.y * e2z - dir.z * e2y;
            float hy = dir.z * e2x - dir.x * e2z;
            float hz = dir.x * e2y - dir.y * e2x;

            float det = e1x * hx + e1y * hy + e1z * hz;
            if (Math.abs(det) < EPS) continue;

            float inv = 1.0f / det;
            float sx = orig.x - verts[i0],
                  sy = orig.y - verts[i0+1],
                  sz = orig.z - verts[i0+2];
            float u = (sx * hx + sy * hy + sz * hz) * inv;
            if (u < 0 || u > 1) continue;

            float qx = sy * e1z - sz * e1y;
            float qy = sz * e1x - sx * e1z;
            float qz = sx * e1y - sy * e1x;
            float wv = (dir.x * qx + dir.y * qy + dir.z * qz) * inv;
            if (wv < 0 || u + wv > 1) continue;

            float t = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (t > EPS && t < tMin) {
                tMin = t;
                hit = new float[]{
                    orig.x + t * dir.x,
                    orig.y + t * dir.y,
                    orig.z + t * dir.z
                };
            }
        }
        return hit;
    }
}
