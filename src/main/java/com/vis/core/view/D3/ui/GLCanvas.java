/* ***** BEGIN LICENSE BLOCK ***** * Version: MPL 1.1/GPL 2.0/LGPL 2.1 
 * * The contents of this file are subject to the Mozilla Public License Version 
 * 1.1 (the "License"); you may not use this file except in compliance with 
 * the License. You may obtain a copy of the License at 
 * http://www.mozilla.org/MPL/ 
 * * Software distributed under the License is distributed on an "AS IS" basis, 
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License 
 * for the specific language governing rights and limitations under the 
 * License. 
 * * The Original Code is part of graphy, hosted at https://github.com/graphy. 
 * * The Initial Developer of the Original Code is 
 * Visionary Imaging Services, Inc. 
 * Portions created by the Initial Developer are Copyright (C) 2015 
 * the Initial Developer. All Rights Reserved. 
 * * Contributor(s): 
 * See @authors listed below 
 * * Alternatively, the contents of this file may be used under the terms of 
 * either the GNU General Public License Version 2 or later (the "GPL"), or 
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), 
 * in which case the provisions of the GPL or the LGPL are applicable instead 
 * of those above. If you wish to allow use of your version of this file only 
 * under the terms of either the GPL or the LGPL, and not to allow others to 
 * use your version of this file under the terms of the MPL, indicate your 
 * decision by deleting the provisions above and replace them with the notice 
 * and other provisions required by the GPL or the LGPL. If you do not delete 
 * the provisions above, a recipient may use your version of this file under 
 * the terms of any one of the MPL, the GPL or the LGPL. 
 * * ***** END LICENSE BLOCK ***** */
package com.vis.core.view.D3.ui;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.GLData;

import com.vis.core.view.D3.roi.FreeFormRoi3D;

import static org.lwjgl.opengl.GL33.*;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;
import org.lwjgl.opengl.awt.AWTGLCanvas;

/**
 * 
 * @author tatsunidas
 *
 */
public class GLCanvas extends AWTGLCanvas {
	private VolumeRenderer volumeRenderer;
	private VolumeData pendingVolume = null;

	Camera camera = new Camera();

	// マウス操作用
	private int lastX, lastY;

	// スライダーの値 (初期値は中心 0.5)
	private float sliceX = 0.5f;
	private float sliceY = 0.5f;
	private float sliceZ = 0.5f;

	// 表示モード (trueならOrtho, falseならVolume)
	private boolean isOrthoMode = false;

	private AxesGizmo axesGizmo; // ★追加

	// Undo/Redo
	private UndoManager undoManager = new UndoManager();

	// Contour (カッティング用の線)
	private List<java.awt.Point> currentPath = new ArrayList<>();
	private boolean isCuttingMode = false; // 右ドラッグ等をカットモードにするか

	// 編集対象のデータ参照
	private VolumeData currentVolumeData;
	
	private byte[] pendingRoiMask = null;
	
	private static final long serialVersionUID = 1L;

	public GLCanvas(GLData data) {
		super(data);
	}

	// 最初に1回だけ呼ばれる（初期化用）
	@Override
	public void initGL() {
		System.out.println("OpenGL init in Swing!");

		// LWJGLの機能を有効化 (これがないとGL関数が使えません)
		GL.createCapabilities();

		// ★ドライバ情報を出力して、GPUが認識されているか確認
		String version = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION);
		String vendor = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
		System.out.println("OpenGL Version: " + version);
		System.out.println("OpenGL Vendor: " + vendor);

		// 背景色設定など
		glClearColor(0.2f, 0.3f, 0.3f, 1.0f);
		glEnable(GL_DEPTH_TEST);

		volumeRenderer = new VolumeRenderer();
		volumeRenderer.init();
		volumeRenderer.initSliceRenderer();

		axesGizmo = new AxesGizmo();
		axesGizmo.init();

		// Mouse Adapter (クリック、リリース)
		this.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					// 左クリック: カメラ回転開始
					lastX = e.getX();
					lastY = e.getY();
				} else if (SwingUtilities.isRightMouseButton(e) && e.isControlDown()) {
					// ★ Ctrl + 右クリック: カット開始
					isCuttingMode = true;
					currentPath.clear();
					currentPath.add(e.getPoint());
				}
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				if (isCuttingMode) {
					// ★ カット実行
					performCut();
					isCuttingMode = false;
					currentPath.clear();
					repaint(); // 線を消すために再描画
				}
			}
		});

		// Mouse Motion Adapter (ドラッグ)
		this.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override
			public void mouseDragged(java.awt.event.MouseEvent e) {
				if (isCuttingMode) {
					// ★ 線を記録して描画
					currentPath.add(e.getPoint());
					repaint(); // paintComponentを呼んで線を書かせる
				} else if (SwingUtilities.isLeftMouseButton(e)) {
					// カメラ回転
					float dx = (e.getX() - lastX) * 0.5f;
					float dy = (e.getY() - lastY) * 0.5f;
					camera.rotate(dx, dy);
					lastX = e.getX();
					lastY = e.getY();
					repaint();
				} else if (SwingUtilities.isRightMouseButton(e)) {
					// Window/Level (Ctrlなしの場合)
					if (!e.isControlDown()) {
						float dx = (e.getX() - lastX) * 0.005f;
						float dy = (e.getY() - lastY) * 0.005f;
						float w = volumeRenderer.getWindowWidth();
						float c = volumeRenderer.getWindowCenter();
						volumeRenderer.setWindowLevel(c + dy, w + dx);
						lastX = e.getX();
						lastY = e.getY();
						repaint();
					}
				}
			}
		});

		// キーボードショートカット (Undo/Redo)
		this.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				// Ctrl + Z (Undo)
				if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
					undoManager.undo();
					repaint();
					System.out.println("Undo");
				}
				// Ctrl + Y (Redo)
				if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Y) {
					undoManager.redo();
					repaint();
					System.out.println("Redo");
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}
		});

		// ホイールズーム
		this.addMouseWheelListener(e -> {
			camera.zoom((float) e.getWheelRotation());
			repaint();
		});
	}

	public void setVolumeData(VolumeData vol) {
		this.currentVolumeData = vol;
		this.pendingVolume = vol;

		// ★追加1: 古いUndo履歴を消去し、メモリリークと誤作動を防ぐ
		if (this.undoManager != null) {
			this.undoManager.clear();
		}

		// ★追加2: 断面位置（スライダー）を中心に戻す
		this.sliceX = 0.5f;
		this.sliceY = 0.5f;
		this.sliceZ = 0.5f;

		// (補足) もしコントラスト(Window/Level)も初期化したい場合は、
		// volumeRenderer.setWindowLevel(0.5f, 1.0f); などもここに入れます。

		this.repaint();
	}
	
	// GLCanvas.java の ROIロードメソッド（前回提案した setRoiData メソッド内）
	public void setRoiData(java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> rois, double[] origin, double[] iop) {
	    if (currentVolumeData == null) return;
	    if (rois == null || rois.isEmpty()) return;
	    setMultiRoiData(rois, origin, iop);
	}
	
	public void setMultiRoiData(java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> rois, double[] origin, double[] iop) {
	    if (currentVolumeData == null || rois.isEmpty()) return;
	    
	    // とりあえず代表して最初のROIの色を使う
	    java.awt.Color roiColor = rois.get(0).getStrokeColor();
	    if (roiColor == null) roiColor = java.awt.Color.CYAN;
	    setRoiColor(roiColor, 0.5f);
	    
	    new Thread(() -> {
	        // 全部のROIを1つのマスクに焼き付ける
	        byte[] mask = createMergedRoiMask(currentVolumeData, rois, origin, iop);
	        this.pendingRoiMask = mask; 
	        SwingUtilities.invokeLater(this::repaint);
	    }).start();
	}

	// 複数ROI用の合体マスク生成
	private byte[] createMergedRoiMask(VolumeData vol, java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> rois, double[] volumeOriginIpp, double[] volumeIop) {
	    int w = vol.width; int h = vol.height; int d = vol.depth;
	    byte[] mask = new byte[w * h * d];
	    
	    double[] n = new double[3];
	    n[0] = volumeIop[1]*volumeIop[5] - volumeIop[2]*volumeIop[4];
	    n[1] = volumeIop[2]*volumeIop[3] - volumeIop[0]*volumeIop[5];
	    n[2] = volumeIop[0]*volumeIop[4] - volumeIop[1]*volumeIop[3];

	    int index = 0;
	    for (int z = 0; z < d; z++) {
	        double zOffX = n[0] * (z * vol.sliceThickness);
	        double zOffY = n[1] * (z * vol.sliceThickness);
	        double zOffZ = n[2] * (z * vol.sliceThickness);

	        for (int y = 0; y < h; y++) {
	            double yOffX = volumeIop[3] * (y * vol.pixelSpacingY);
	            double yOffY = volumeIop[4] * (y * vol.pixelSpacingY);
	            double yOffZ = volumeIop[5] * (y * vol.pixelSpacingY);

	            for (int x = 0; x < w; x++) {
	                double xOffX = volumeIop[0] * (x * vol.pixelSpacingX);
	                double xOffY = volumeIop[1] * (x * vol.pixelSpacingX);
	                double xOffZ = volumeIop[2] * (x * vol.pixelSpacingX);

	                double px = volumeOriginIpp[0] + xOffX + yOffX + zOffX;
	                double py = volumeOriginIpp[1] + xOffY + yOffY + zOffY;
	                double pz = volumeOriginIpp[2] + xOffZ + yOffZ + zOffZ;
	                
	                // いずれかのROIに含まれていればマスクを255にする
	                boolean isInside = false;
	                for (com.vis.core.view.D3.roi.FreeFormRoi3D roi : rois) {
	                    if (roi.containsPhysicalPoint(px, py, pz)) {
	                        isInside = true;
	                        break;
	                    }
	                }
	                
	                mask[index] = isInside ? (byte) 255 : 0;
	                index++;
	            }
	        }
	    }
	    return mask;
	}

	// GLCanvas.java に追加
	public void setShowVolume(boolean show) {
	    if (volumeRenderer != null) {
	        volumeRenderer.setVolumeVisible(show);
	        repaint(); // 設定を変更したら再描画
	    }
	}

	public void setShowRoi(boolean show) {
	    if (volumeRenderer != null) {
	        volumeRenderer.setRoiVisible(show);
	        repaint();
	    }
	}

	// java.awt.Color を受け取って OpenGL用の 0.0~1.0 に変換する便利なメソッド
	public void setRoiColor(java.awt.Color color, float alpha) {
	    if (volumeRenderer != null && color != null) {
	        float r = color.getRed() / 255.0f;
	        float g = color.getGreen() / 255.0f;
	        float b = color.getBlue() / 255.0f;
	        volumeRenderer.setRoiColor(r, g, b, alpha);
	        repaint();
	    }
	}

	// セッター (スライダーから呼ぶ)
	public void setSlicePos(float x, float y, float z) {
		this.sliceX = x;
		this.sliceY = y;
		this.sliceZ = z;
		repaint();
	}

	public void setOrthoMode(boolean enable) {
		this.isOrthoMode = enable;
		repaint();
	}

	public void setMIPMode(boolean isMIP) {
		// MIPなら0、DVRなら1
		volumeRenderer.setRenderMode(isMIP ? 0 : 1);
		repaint();
	}

	public void resetCamera() {
		camera.reset();
	}

	public void loadLut(java.io.File file) {
		// スレッドセーフにするため invokeLater 推奨ですが、
		// テクスチャ転送は描画スレッドで行う必要があるため、
		// pendingTask のような仕組みを使うか、render内で処理させるのが安全です。

		// 簡易実装:
		volumeRenderer.loadLut(file);
		repaint();
	}

	// --- 追加: ボクセルサイズに基づくスケール行列を計算するメソッド ---
	private org.joml.Matrix4f calculateModelMatrix() {
		float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
		if (currentVolumeData != null) {
			// 物理サイズ（ピクセル数 × ピクセル間隔）
			float physX = currentVolumeData.width * (float) currentVolumeData.pixelSpacingX;
			float physY = currentVolumeData.height * (float) currentVolumeData.pixelSpacingY;
			float physZ = currentVolumeData.depth * (float) currentVolumeData.sliceThickness;

			// 最大の辺を 1.0 として正規化
			float maxDim = Math.max(physX, Math.max(physY, physZ));
			scaleX = physX / maxDim;
			scaleY = physY / maxDim;
			scaleZ = physZ / maxDim;
		}
		return new org.joml.Matrix4f().identity().scale(scaleX, scaleY, scaleZ);
	}

	// --- カット実行処理 ---
	private void performCut() {
		if (currentVolumeData == null || currentPath.size() < 3)
			return;

		int w = getWidth();
		int h = getHeight();

		if (h == 0)
			h = 1;
		float aspect = (float) w / h;
		org.joml.Matrix4f proj = new org.joml.Matrix4f().setPerspective((float) Math.toRadians(45.0f), aspect, 0.01f,
				100.0f);
		org.joml.Matrix4f view = camera.getViewMatrix();

		// ★ 修正: カット投影計算時にも正しいスケール行列を適用する
		org.joml.Matrix4f model = calculateModelMatrix();
		org.joml.Matrix4f mvp = new org.joml.Matrix4f(proj).mul(view).mul(model);

		// 計算実行 (少し時間がかかるかもしれない)
		System.out.println("Calculating cut...");

		// ★修正: 新しい VolumeEditor のメソッドシグネチャに合わせて呼び出し
		// 返り値も Map<Integer, Number> に変更
		Map<Integer, Number> changes = VolumeEditor.calculateCut(currentVolumeData, currentPath, mvp, w, h);

		if (!changes.isEmpty()) {
			// ★修正: CutCommandのコンストラクタに VolumeData オブジェクトそのものを渡す
			VolumeEditor.CutCommand cmd = new VolumeEditor.CutCommand(currentVolumeData, changes, () -> {
				this.pendingVolume = currentVolumeData;
				this.repaint();
			});

			undoManager.addCommand(cmd);
			System.out.println("Cut finished. Modified " + changes.size() + " voxels.");
		} else {
			System.out.println("No voxels inside contour.");
		}
	}

	// --- Swingのオーバーレイ描画 (線を引く) ---
	// GLCanvasはAWTコンポーネントなので、paint()をオーバーライドして
	// OpenGL描画の上に2D描画を重ねることができます（環境によってはチラつくことがありますが簡便です）
	@Override
	public void paint(Graphics g) {
		super.paint(g); // これがOpenGL描画を呼び出す(paintGL)

		// その上に線を引く
		if (isCuttingMode && currentPath.size() > 1) {
			g.setColor(Color.YELLOW);
			for (int i = 0; i < currentPath.size() - 1; i++) {
				Point p1 = currentPath.get(i);
				Point p2 = currentPath.get(i + 1);
				g.drawLine(p1.x, p1.y, p2.x, p2.y);
			}
			// 閉じた線にする場合
			Point start = currentPath.get(0);
			Point end = currentPath.get(currentPath.size() - 1);
			g.drawLine(end.x, end.y, start.x, start.y);
		}
	}

	// 描画のたびに呼ばれる
	@Override
	public void paintGL() {
		// 1. ボリュームデータの転送 (既存)
		if (pendingVolume != null) {
			volumeRenderer.uploadTexture(pendingVolume);
			pendingVolume = null;
		}

		// ==========================================
		// ★ ここで pendingRoiMask を使います！
		// ==========================================
		if (pendingRoiMask != null && currentVolumeData != null) {
			// GPUへのアップロードを実行
			volumeRenderer.uploadRoiTexture(pendingRoiMask, currentVolumeData.width, currentVolumeData.height,
					currentVolumeData.depth);
			// 転送が終わったらnullにして、毎フレーム転送されるのを防ぐ
			pendingRoiMask = null;
		}

		int w = getWidth();
		int h = getHeight();
		if (w == 0 || h == 0)
			return;

		// ★追加: Windows等のHigh-DPI(ディスプレイ拡大率)を取得
		double scaleX = 1.0;
		double scaleY = 1.0;
		if (getGraphicsConfiguration() != null) {
			java.awt.geom.AffineTransform t = getGraphicsConfiguration().getDefaultTransform();
			scaleX = t.getScaleX();
			scaleY = t.getScaleY();
		}

		// ★追加: 実際の物理ピクセル数を計算
		int physW = (int) Math.round(w * scaleX);
		int physH = (int) Math.round(h * scaleY);

		// ★修正: メイン描画のためにビューポートを「物理ピクセル」で全画面に設定
		glViewport(0, 0, physW, physH);

		// 2. 画面クリア
		org.lwjgl.opengl.GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		org.lwjgl.opengl.GL11
				.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);

		// 3. 行列計算
		// ★修正: アスペクト比も物理ピクセルから計算する
		float aspect = (float) physW / physH;

		org.joml.Matrix4f proj = new org.joml.Matrix4f().setPerspective((float) Math.toRadians(45.0f), aspect, 0.01f,
				100.0f);
		org.joml.Matrix4f view = camera.getViewMatrix();

		// (以前追加した calculateModelMatrix() を呼び出す)
		org.joml.Matrix4f model = calculateModelMatrix();

		org.joml.Matrix4f mvp = new org.joml.Matrix4f(proj).mul(view).mul(model);

		if (isOrthoMode) {
			org.joml.Matrix4f scaledProjView = new org.joml.Matrix4f(proj).mul(view).mul(model);
			volumeRenderer.renderOrthoSlices(scaledProjView, sliceX, sliceY, sliceZ);
		} else {
			org.joml.Matrix4f modelViewInv = new org.joml.Matrix4f(view).mul(model).invert();
			org.joml.Vector3f camPosLocal = new org.joml.Vector3f();
			modelViewInv.getTranslation(camPosLocal);

			volumeRenderer.render(mvp, camPosLocal);
		}

		// ★修正: 最後にGizmoを描画 (右下にオーバーレイ)
		// Gizmoにも物理ピクセルサイズを渡さないと、位置がズレたり小さくなったりします
		if (axesGizmo != null) {
			axesGizmo.render(camera.getViewMatrix(), physW, physH);
		}

		org.lwjgl.opengl.GL11.glFlush();
	}
}