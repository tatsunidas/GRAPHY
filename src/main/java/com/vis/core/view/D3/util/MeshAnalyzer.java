package com.vis.core.view.D3.util;

import com.vis.core.view.D3.ui.MeshData;
import com.vis.core.view.D3.ui.MeshMeasureResult;

/**
 * Computes geometric statistics (surface area, volume, principal diameters)
 * for a MeshData whose vertices are in mm (i.e. the raw mesh before AlignMesh).
 */
public class MeshAnalyzer {

    public static MeshMeasureResult analyze(MeshData rawMesh) {
        MeshMeasureResult r = new MeshMeasureResult();
        if (rawMesh == null || rawMesh.vertices == null || rawMesh.indices == null
                || rawMesh.indices.length < 3) {
            return r;
        }
        r.surfaceAreaMm2 = computeSurfaceArea(rawMesh);
        r.volumeMm3      = computeVolume(rawMesh);
        double[] d = computePrincipalDiameters(rawMesh);
        r.longDiameterMm  = d[0];
        r.midDiameterMm   = d[1];
        r.shortDiameterMm = d[2];
        return r;
    }

    // -------------------------------------------------------
    // Surface area: sum of triangle areas
    // -------------------------------------------------------
    private static double computeSurfaceArea(MeshData mesh) {
        float[] v = mesh.vertices;
        int[]  idx = mesh.indices;
        double area = 0.0;
        for (int i = 0; i < idx.length; i += 3) {
            int i0 = idx[i]*3, i1 = idx[i+1]*3, i2 = idx[i+2]*3;
            double ax = v[i1]-v[i0], ay = v[i1+1]-v[i0+1], az = v[i1+2]-v[i0+2];
            double bx = v[i2]-v[i0], by = v[i2+1]-v[i0+1], bz = v[i2+2]-v[i0+2];
            double cx = ay*bz - az*by, cy = az*bx - ax*bz, cz = ax*by - ay*bx;
            area += Math.sqrt(cx*cx + cy*cy + cz*cz) * 0.5;
        }
        return area;
    }

    // -------------------------------------------------------
    // Volume: signed-tetrahedron decomposition (divergence theorem)
    // -------------------------------------------------------
    private static double computeVolume(MeshData mesh) {
        float[] v = mesh.vertices;
        int[]  idx = mesh.indices;
        double vol = 0.0;
        for (int i = 0; i < idx.length; i += 3) {
            int i0 = idx[i]*3, i1 = idx[i+1]*3, i2 = idx[i+2]*3;
            double v0x=v[i0], v0y=v[i0+1], v0z=v[i0+2];
            double v1x=v[i1], v1y=v[i1+1], v1z=v[i1+2];
            double v2x=v[i2], v2y=v[i2+1], v2z=v[i2+2];
            vol += v0x*(v1y*v2z - v1z*v2y)
                 + v1x*(v2y*v0z - v2z*v0y)
                 + v2x*(v0y*v1z - v0z*v1y);
        }
        return Math.abs(vol) / 6.0;
    }

    // -------------------------------------------------------
    // PCA via classical Jacobi sweeps on the 3x3 covariance matrix.
    // Returns [longDiam, midDiam, shortDiam] in mm.
    // -------------------------------------------------------
    private static double[] computePrincipalDiameters(MeshData mesh) {
        float[] v = mesh.vertices;
        int n = v.length / 3;
        if (n < 2) return new double[]{0, 0, 0};

        double cx=0, cy=0, cz=0;
        for (int i = 0; i < v.length; i+=3) { cx+=v[i]; cy+=v[i+1]; cz+=v[i+2]; }
        cx/=n; cy/=n; cz/=n;

        double mxx=0,myy=0,mzz=0,mxy=0,mxz=0,myz=0;
        for (int i = 0; i < v.length; i+=3) {
            double dx=v[i]-cx, dy=v[i+1]-cy, dz=v[i+2]-cz;
            mxx+=dx*dx; myy+=dy*dy; mzz+=dz*dz;
            mxy+=dx*dy; mxz+=dx*dz; myz+=dy*dz;
        }
        mxx/=n; myy/=n; mzz/=n; mxy/=n; mxz/=n; myz/=n;

        double[][] A = {{mxx,mxy,mxz},{mxy,myy,myz},{mxz,myz,mzz}};
        double[][] V = {{1,0,0},{0,1,0},{0,0,1}};

        for (int sweep = 0; sweep < 60; sweep++) {
            double offDiag = Math.abs(A[0][1]) + Math.abs(A[0][2]) + Math.abs(A[1][2]);
            if (offDiag < 1e-14) break;
            for (int p = 0; p < 3; p++) {
                for (int q = p+1; q < 3; q++) {
                    double apq = A[p][q];
                    if (Math.abs(apq) < 1e-15) continue;
                    double theta = (A[q][q] - A[p][p]) / (2.0 * apq);
                    double t = (theta == 0.0) ? 1.0
                             : Math.signum(theta) / (Math.abs(theta) + Math.sqrt(1.0 + theta*theta));
                    double c = 1.0 / Math.sqrt(1.0 + t*t);
                    double s = t * c;
                    jacobiRotate(A, V, p, q, c, s);
                }
            }
        }

        double[] diams = new double[3];
        for (int k = 0; k < 3; k++) {
            double ax=V[0][k], ay=V[1][k], az=V[2][k];
            double minP=Double.MAX_VALUE, maxP=-Double.MAX_VALUE;
            for (int i = 0; i < v.length; i+=3) {
                double p = (v[i]-cx)*ax + (v[i+1]-cy)*ay + (v[i+2]-cz)*az;
                if (p < minP) minP=p;
                if (p > maxP) maxP=p;
            }
            diams[k] = maxP - minP;
        }
        java.util.Arrays.sort(diams);
        return new double[]{diams[2], diams[1], diams[0]};
    }

    private static void jacobiRotate(double[][] A, double[][] V,
                                     int p, int q, double c, double s) {
        double App=A[p][p], Aqq=A[q][q], Apq=A[p][q];
        A[p][p] = c*c*App - 2*s*c*Apq + s*s*Aqq;
        A[q][q] = s*s*App + 2*s*c*Apq + c*c*Aqq;
        A[p][q] = A[q][p] = 0.0;
        for (int r = 0; r < 3; r++) {
            if (r != p && r != q) {
                double Arp=A[r][p], Arq=A[r][q];
                A[r][p]=A[p][r]= c*Arp - s*Arq;
                A[r][q]=A[q][r]= s*Arp + c*Arq;
            }
            double Vrp=V[r][p], Vrq=V[r][q];
            V[r][p]= c*Vrp - s*Vrq;
            V[r][q]= s*Vrp + c*Vrq;
        }
    }
}
