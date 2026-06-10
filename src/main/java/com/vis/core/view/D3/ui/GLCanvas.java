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

import com.vis.core.log.Log;

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
	
	private java.util.List<String> currentRoiGroupNames = new java.util.ArrayList<>();
	private Runnable onRoiLoadedCallback; // UIにロード完了を通知するためのコールバック

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
	
	private java.util.List<java.awt.Color> currentRoiColors = new java.util.ArrayList<>();
	private float currentRoiAlpha = 0.5f;
	
	public enum OrthoRoiMode {
		NONE,
		SLICE_2D,
		FLOAT_3D,
		EMBEDDED_3D // ★新規追加: 埋め込みモード
	}
	
	private OrthoRoiMode orthoRoiMode = OrthoRoiMode.SLICE_2D;
	
	private static final long serialVersionUID = 1L;

	public GLCanvas(GLData data) {
		super(data);
	}

	// 最初に1回だけ呼ばれる（初期化用）
	@Override
	public void initGL() {
		Log.logger.fine("OpenGL init in Swing!");

		// LWJGLの機能を有効化 (これがないとGL関数が使えません)
		GL.createCapabilities();

		// ★ドライバ情報を出力して、GPUが認識されているか確認
		String version = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION);
		String vendor = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
		Log.logger.fine("OpenGL Version: " + version);
		Log.logger.fine("OpenGL Vendor: " + vendor);

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
					Log.logger.fine("Undo");
				}
				// Ctrl + Y (Redo)
				if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Y) {
					undoManager.redo();
					repaint();
					Log.logger.fine("Redo");
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
	
	public VolumeData getVolumeData() {
		return this.currentVolumeData;
	}

	// ==========================================
	// グループを解釈して色とIDを割り振る setRoiData
	// 利用する側は、roiを渡すだけで良い。
	// ==========================================
	public void setRoiData(java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> rois, double[] startIpp, double[] iop, double[] stepZ) {
	    if (currentVolumeData == null || rois.isEmpty()) return;
	    
	    java.util.Map<String, Integer> groupToIdMap = new java.util.HashMap<>();
	    java.util.Map<Integer, java.awt.Color> idToColorMap = new java.util.TreeMap<>();
	    java.util.Map<Integer, String> idToNameMap = new java.util.TreeMap<>(); // ★追加: ID->グループ名マッピング
	    
	    int nextId = 1;
	    int[] mappedIds = new int[rois.size()]; 
	    
	    for (int i = 0; i < rois.size(); i++) {
	        com.vis.core.view.D3.roi.FreeFormRoi3D roi = rois.get(i);
	        
	        String groupId = roi.getProperty(com.vis.configuration.RoiDBKey.RoiGroup.name());
	        if (groupId == null || groupId.isEmpty()) {
	            groupId = roi.getProperty(com.vis.configuration.RoiDBKey.RoiID.name());
	            if (groupId == null || groupId.isEmpty()) {
	                groupId = String.valueOf(roi.hashCode());
	            }
	        }
	        
	        if (!groupToIdMap.containsKey(groupId)) {
	            if (nextId < 32) {
	                groupToIdMap.put(groupId, nextId);
	                java.awt.Color c = roi.getStrokeColor();
	                if (c == null) c = java.awt.Color.CYAN;
	                idToColorMap.put(nextId, c);
	                idToNameMap.put(nextId, "Group: " + groupId); // ★追加: 名前を保存
	                nextId++;
	            } else {
	                groupToIdMap.put(groupId, 31); 
	            }
	        }
	        mappedIds[i] = groupToIdMap.get(groupId);
	    }
	    
	    currentRoiColors.clear();
	    currentRoiGroupNames.clear(); // ★追加
	    for (int i = 1; i < nextId; i++) {
	        currentRoiColors.add(idToColorMap.get(i));
	        currentRoiGroupNames.add(idToNameMap.get(i)); // ★追加
	    }
	    
	    // UI側にロード完了を通知
	    if (onRoiLoadedCallback != null) {
	        SwingUtilities.invokeLater(() -> onRoiLoadedCallback.run());
	    }
	    
	    if (volumeRenderer != null) {
	        volumeRenderer.setRoiColors(currentRoiColors, currentRoiAlpha);
	    }
	    
	    new Thread(() -> {
	        byte[] mask = createMergedRoiMask(currentVolumeData, rois, mappedIds, startIpp, iop, stepZ);
	        this.pendingRoiMask = mask; 
	        SwingUtilities.invokeLater(this::repaint);
	    }).start();
	}

	// ==========================================
	// 改良版: 事前計算された mappedIds を使ってマスクを描き込む
	// ==========================================
	private byte[] createMergedRoiMask(VolumeData vol, java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> rois,
			int[] mappedIds, double[] startIpp, double[] iop, double[] stepZ) {
		int w = vol.width;
		int h = vol.height;
		int d = vol.depth;
		byte[] mask = new byte[w * h * d];

		Log.logger.fine("=== Mask Generation Started (Group-Aware ID mode) ===");

		int index = 0;
		for (int z = 0; z < d; z++) {
			double zOffX = z * stepZ[0];
			double zOffY = z * stepZ[1];
			double zOffZ = z * stepZ[2];

			for (int y = 0; y < h; y++) {
				double yOffX = iop[3] * (y * vol.pixelSpacingY);
				double yOffY = iop[4] * (y * vol.pixelSpacingY);
				double yOffZ = iop[5] * (y * vol.pixelSpacingY);

				for (int x = 0; x < w; x++) {
					double xOffX = iop[0] * (x * vol.pixelSpacingX);
					double xOffY = iop[1] * (x * vol.pixelSpacingX);
					double xOffZ = iop[2] * (x * vol.pixelSpacingX);

					double px = startIpp[0] + xOffX + yOffX + zOffX;
					double py = startIpp[1] + xOffY + yOffY + zOffY;
					double pz = startIpp[2] + xOffZ + yOffZ + zOffZ;

					int roiId = 0; // 0 は空気

					// 登録されたすべてのROIに対して判定
					for (int i = 0; i < rois.size(); i++) {
						if (rois.get(i).containsPhysicalPoint(px, py, pz)) {
							// ★ 事前計算した配列からIDを引き当てる (超高速)
							roiId = mappedIds[i];
							break; // 複数のグループが重なっている場合はリストの前方を優先
						}
					}

					mask[index] = (byte) roiId;
					index++;
				}
			}
		}
		Log.logger.fine("=== Mask Generation Completed ===");
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
	
	public void setOrthoRoiMode(OrthoRoiMode mode) {
		this.orthoRoiMode = mode;
		repaint();
	}

	public void setMIPMode(boolean isMIP) {
		// MIPなら0、DVRなら1
		volumeRenderer.setRenderMode(isMIP ? 0 : 1);
		repaint();
	}
	
	public void setRoiAlpha(float alpha) {
	    this.currentRoiAlpha = alpha;
	    if (volumeRenderer != null) {
	        // パレット配列だけを更新
	        volumeRenderer.setRoiColors(currentRoiColors, currentRoiAlpha);
	        // GPUに再描画を指示（マスク再生成は不要！）
	        repaint();
	    }
	}
	
	public void updateRoiColors(java.util.List<java.awt.Color> newColors) {
	    this.currentRoiColors = newColors;
	    if (volumeRenderer != null) {
	        volumeRenderer.setRoiColors(currentRoiColors, currentRoiAlpha);
	        repaint();
	    }
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
		Log.logger.fine("Calculating cut...");

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
			Log.logger.fine("Cut finished. Modified " + changes.size() + " voxels.");
		} else {
			Log.logger.fine("No voxels inside contour.");
		}
	}
	
	public void setOnRoiLoadedCallback(Runnable callback) {
	    this.onRoiLoadedCallback = callback;
	}

	public java.util.List<String> getRoiGroupNames() { return currentRoiGroupNames; }
	public java.util.List<java.awt.Color> getRoiColors() { return currentRoiColors; }

	// ★追加: 特定のインデックス(グループ)の色だけを変更するメソッド
	public void setRoiGroupColor(int index, java.awt.Color newColor) {
	    if (index >= 0 && index < currentRoiColors.size()) {
	        currentRoiColors.set(index, newColor);
	        if (volumeRenderer != null) {
	            volumeRenderer.setRoiColors(currentRoiColors, currentRoiAlpha);
	            repaint(); // 再描画
	        }
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

		if (pendingRoiMask != null && currentVolumeData != null) {
			// GPUへのアップロードを実行
			volumeRenderer.uploadRoiTexture(pendingRoiMask, currentVolumeData.width, currentVolumeData.height,
					currentVolumeData.depth);
			
			// ★ 修正: ここで色情報を確実にレンダラーに渡す（ここでは volumeRenderer は絶対に初期化されている）
			volumeRenderer.setRoiColors(currentRoiColors, currentRoiAlpha);
			
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
			volumeRenderer.setOrthoShowRoi(orthoRoiMode == OrthoRoiMode.SLICE_2D);
			volumeRenderer.renderOrthoSlices(scaledProjView, sliceX, sliceY, sliceZ);

			// ★修正: FLOAT_3D または EMBEDDED_3D の場合
			if ((orthoRoiMode == OrthoRoiMode.FLOAT_3D || orthoRoiMode == OrthoRoiMode.EMBEDDED_3D) && currentVolumeData != null) {
				boolean tempVolVisible = volumeRenderer.isVolumeVisible();
				boolean tempRoiVisible = volumeRenderer.isRoiVisible();

				volumeRenderer.setVolumeVisible(false);
				volumeRenderer.setRoiVisible(true);

				org.joml.Matrix4f modelViewInv = new org.joml.Matrix4f(view).mul(model).invert();
				org.joml.Vector3f camPosLocal = new org.joml.Vector3f();
				modelViewInv.getTranslation(camPosLocal);

				// モード判定と、引数への追加
				boolean isEmbedded = (orthoRoiMode == OrthoRoiMode.EMBEDDED_3D);
				
				// ★修正: renderメソッドに isEmbedded フラグと、スライスの位置(X, Y, Z) を渡す
				volumeRenderer.render(mvp, camPosLocal, isEmbedded, sliceX, sliceY, sliceZ);

				volumeRenderer.setVolumeVisible(tempVolVisible);
				volumeRenderer.setRoiVisible(tempRoiVisible);
			}
		} else {
			// 通常の3D描画
			org.joml.Matrix4f modelViewInv = new org.joml.Matrix4f(view).mul(model).invert();
			org.joml.Vector3f camPosLocal = new org.joml.Vector3f();
			modelViewInv.getTranslation(camPosLocal);

			// ★修正: 通常モードでは埋め込み処理は不要なので false, 0, 0, 0 を渡す
			volumeRenderer.render(mvp, camPosLocal, false, 0f, 0f, 0f);
		}

		// ★修正: 最後にGizmoを描画 (右下にオーバーレイ)
		// Gizmoにも物理ピクセルサイズを渡さないと、位置がズレたり小さくなったりします
		if (axesGizmo != null) {
			axesGizmo.render(camera.getViewMatrix(), physW, physH);
		}

		org.lwjgl.opengl.GL11.glFlush();
	}
}