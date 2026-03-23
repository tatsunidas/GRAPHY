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
		if (currentVolumeData == null || currentPath.size() < 3) return;

        int w = getWidth();
        int h = getHeight();

        if (h == 0) h = 1;
        float aspect = (float) w / h;
        org.joml.Matrix4f proj = new org.joml.Matrix4f().setPerspective((float) Math.toRadians(45.0f), aspect, 0.01f, 100.0f);
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
		// 1. データ転送（未処理のボリュームがあればGPUに送る）
		if (pendingVolume != null) {
			volumeRenderer.uploadTexture(pendingVolume);
			pendingVolume = null;
		}

		int w = getWidth();
		int h = getHeight();
		if (w == 0 || h == 0) {
			System.out.println("Warning: Canvas size is 0x0. Skipping render.");
			return;
		}

		// ★重要: メイン描画のためにビューポートを「全画面」に設定
		// (Gizmo描画でビューポートが変わっているため、毎回リセットが必要)
		glViewport(0, 0, w, h);

		// 2. 画面クリア（黒に戻します）
		// 赤いままだとレントゲン写真が見にくいので黒(0,0,0)にします
		org.lwjgl.opengl.GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		org.lwjgl.opengl.GL11
				.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);

		// 3. 行列計算
		if (h == 0)
			h = 1;
		float aspect = (float) w / h;

		org.joml.Matrix4f proj = new org.joml.Matrix4f().setPerspective((float) Math.toRadians(45.0f), aspect, 0.01f,
				100.0f);
		org.joml.Matrix4f view = camera.getViewMatrix();

		// ★ 修正: 物理サイズに基づく Model 行列を取得
		org.joml.Matrix4f model = calculateModelMatrix();

		org.joml.Matrix4f mvp = new org.joml.Matrix4f(proj).mul(view).mul(model);

		if (isOrthoMode) {
			// ★3断面表示モード: スケール済みの行列を渡す
			org.joml.Matrix4f scaledProjView = new org.joml.Matrix4f(proj).mul(view).mul(model);
			volumeRenderer.renderOrthoSlices(scaledProjView, sliceX, sliceY, sliceZ);
		} else {
			// ★既存のボリュームレンダリング
			// スケールされた空間にカメラ位置を正しく逆算する
			org.joml.Matrix4f modelViewInv = new org.joml.Matrix4f(view).mul(model).invert();
			org.joml.Vector3f camPosLocal = new org.joml.Vector3f();
			modelViewInv.getTranslation(camPosLocal);

			volumeRenderer.render(mvp, camPosLocal);
		}

		// ★最後にGizmoを描画 (右下にオーバーレイ)
		if (axesGizmo != null) {
			axesGizmo.render(camera.getViewMatrix(), w, h);
		}

		org.lwjgl.opengl.GL11.glFlush();
	}
}