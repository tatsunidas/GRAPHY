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
import com.vis.core.view.D3.endo.EndoCamera;
import com.vis.core.view.D3.endo.EndoCommands;
import com.vis.core.view.D3.endo.EndoPath3D;
import com.vis.core.view.D3.endo.EndoPathPicker;

import static org.lwjgl.opengl.GL33.*;

import java.awt.Graphics;
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
	
	// ==========================================================
	// ★ 修正: OpenGL上のメッシュリソースをカプセル化する構造体
	// ==========================================================
	private static class MeshGLResource {
		MeshData meshData;
		int vao = 0;
		int vboVertices = 0;
		int vboNormals = 0;
		int vboColors = 0; // ★ 追加: 頂点カラー用VBO
		int ibo = 0;
		int indexCount = 0;
		boolean needsUpload = true;
		boolean visible = true;
		java.awt.Color color = new java.awt.Color(200, 200, 200); // ★追加: 個別のメッシュ色
	}

	// 複数のメッシュを名前（グループ名）で管理するスレッドセーフなMap
	private final java.util.Map<String, MeshGLResource> glMeshMap = new java.util.concurrent.ConcurrentHashMap<>();

	// 現在アクティブ（選択中）なメッシュの名前
	private String activeMeshName = null;
	
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

	// シネマティック・レンダリング（モンテカルロ・パストレーシング）モード
	private boolean isCinematicMode = false;
	private com.vis.core.view.D3.ui.cinematic.CinematicRenderer cinematicRenderer;
	private final com.vis.core.view.D3.ui.cinematic.CinematicParams cinematicParams =
			new com.vis.core.view.D3.ui.cinematic.CinematicParams();
	// 直前フレームの状態フィンガープリント。カメラ操作やW/L・LUT・ライト設定の変化を検知して
	// 蓄積バッファをリセットするために使う（静止中だけノイズが収束していくプログレッシブ表示のため）。
	private String cinematicLastFingerprint = null;
	// invalidateAccumulation()はGL呼び出しを含むため、paintGL()の外（別スレッドのボタン
	// リスナーやEDT上のスライダーリスナー）から直接呼ぶとコンテキストが無く失敗する/効かない。
	// pendingLutUpdate等と同じく、フラグだけ立てて実際の呼び出しはpaintGL()内で行う。
	private volatile boolean cinematicResetRequested = false;

	private AxesGizmo axesGizmo; // ★追加
	
	private MeshRenderer meshRenderer;
	private MeshData pendingMesh = null;
	private float currentMeshAlpha = 1.0f;
	private boolean isMeshVisible = true;
	
	private final LegendConfig legendConfig = new LegendConfig();

	// Undo/Redo
	private UndoManager undoManager = new UndoManager();

	// Contour (カッティング用の線)
	private List<java.awt.Point> currentPath = new ArrayList<>();
	private boolean isCuttingMode = false; // 右ドラッグ等をカットモードにするか
	private CutLineRenderer cutLineRenderer; // ★カット輪郭線をGL側で描画する（Graphics2Dオーバーレイは表示されないため）

	// 編集対象のデータ参照
	private VolumeData currentVolumeData;

	// 中心線解析（CenterlineAnalysisDialog）のグラフ・オーバーレイ表示
	private CenterlineGraphRenderer centerlineGraphRenderer;
	private com.vis.core.centerline.CenterlineGraph currentCenterlineGraph;
	private com.vis.core.slicer.VolumeSampler centerlineRenderSampler;
	private java.util.Set<Integer> selectedCenterlineBranchIds = new java.util.HashSet<>();
	private java.util.Set<Integer> selectedCenterlineNodeIds = new java.util.HashSet<>();
	private com.vis.core.slicer.Centerline3D selectedCenterlineCurve;

	private byte[] pendingRoiMask = null;
	/**
	 * AWTGLCanvas only has a valid/current GL context inside paintGL(); GL
	 * calls made directly from the EDT (e.g. a combo box or dialog listener)
	 * race against that and can silently no-op. LUT/opacity updates are
	 * deferred here and applied at the top of paintGL(), the same way
	 * pendingVolume/pendingRoiMask already are.
	 */
	private Runnable pendingLutUpdate = null;

	private java.util.List<java.awt.Color> currentRoiColors = new java.util.ArrayList<>();
	private float currentRoiAlpha = 0.5f;
	
	private java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> currentRois = new java.util.ArrayList<>();
	
	public enum OrthoRoiMode {
		NONE,
		SLICE_2D,
		FLOAT_3D,
		EMBEDDED_3D // ★新規追加: 埋め込みモード
	}
	
	private OrthoRoiMode orthoRoiMode = OrthoRoiMode.SLICE_2D;

	// 仮想内視鏡（フライスルー）モード
	private boolean endoscopyMode = false;
	// 常にnon-null。EndoCameraが同一インスタンスへの参照を保持し続けるため再代入しない。
	private final EndoPath3D endoPath = new EndoPath3D();
	private final EndoCamera endoCamera = new EndoCamera(endoPath);
	private EndoPathRenderer endoPathRenderer; // initGL()で生成
	private boolean showEndoPath = true;
	private EndoOrientationIndicator endoOrientationIndicator; // initGL()で生成

	// endoPathの元になった中心線（CenterlineAnalysisDialog由来）。解析ダイアログを閉じてCenterline
	// Analysisのオーバーレイ（currentCenterlineGraph等）が消えた後も、また内視鏡視点(endoscopyMode)中も
	// 常に強調曲線として描画し続けるための参照（setEndoPathFromCenterlineで設定）。
	private com.vis.core.slicer.Centerline3D endoPathSourceCurve;
	private com.vis.core.slicer.VolumeSampler endoPathSourceSampler;
	// CenterlineAnalysisDialogの「現在選択中」のライブカーブ(白)と同時に描画されても見分けられるように
	private static final org.joml.Vector3f ENDO_PATH_SOURCE_CURVE_COLOR = new org.joml.Vector3f(1f, 0.55f, 0f);

	// パス編集モード
	private boolean endoPathEditMode = false;
	private int selectedEndoPointIndex = -1;
	private int draggingEndoPointIndex = -1;
	private float draggingEndoPointDepth = 0f;
	private org.joml.Vector3f dragOriginalPosition = null;
	private final org.joml.Matrix4f lastMvp = new org.joml.Matrix4f(); // paintGL()で毎フレーム更新、ピッキングに使う
	// 内視鏡モード中のマウスルック感度（既存のCamera.rotate()内の感度と合わせる）
	private static final float ENDO_LOOK_SENSITIVITY = 0.005f;

	/**
	 * ダブルバッファの自動スワップを行う
	 */
	private boolean autoSwapBuffer = true;
	
	private static final long serialVersionUID = 1L;

	public GLCanvas(GLData data) {
		super(data);
		// ★Undo/Redo等のキーボードショートカットがKeyListenerで効くようにフォーカス可能にする
		setFocusable(true);
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

		// ★Phase 1: 常にOpenGL実装。CUDA対応GPUが検出できる場合の高速版は将来追加予定。
		cinematicRenderer = new com.vis.core.view.D3.ui.cinematic.CinematicRendererGL();
		cinematicRenderer.init();

		axesGizmo = new AxesGizmo();
		axesGizmo.init();

		cutLineRenderer = new CutLineRenderer();
		cutLineRenderer.init();

		endoPathRenderer = new EndoPathRenderer();
		endoPathRenderer.init();

		endoOrientationIndicator = new EndoOrientationIndicator();
		endoOrientationIndicator.init();

		centerlineGraphRenderer = new CenterlineGraphRenderer();
		centerlineGraphRenderer.init();

		// Mouse Adapter (クリック、リリース)
		this.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				// ★Undo/Redoのキー入力(Ctrl+Z等)を受け取れるよう、操作開始時にフォーカスを取得する
				requestFocusInWindow();

				if (SwingUtilities.isLeftMouseButton(e)) {
					if (endoPathEditMode && e.isControlDown()) {
						// ★ Ctrl+左クリック: パスに新規点を追加して即座にコマンド発行・選択
						org.joml.Vector3f hit = EndoPathPicker.computeAddPointPosition(lastMvp, e.getX(), e.getY(),
								getWidth(), getHeight());
						if (hit != null) {
							int insertIndex = endoPath.size();
							undoManager.addCommand(new EndoCommands.InsertPointCommand(endoPath, insertIndex, hit));
							selectedEndoPointIndex = insertIndex;
							repaint();
						}
					} else if (endoPathEditMode) {
						// ★ 既存点のクリック: 選択してドラッグ開始（外れたら通常のオービット回転にフォールバック）
						int hitIdx = EndoPathPicker.hitTestNearestPoint(endoPath, lastMvp, e.getX(), e.getY(),
								getWidth(), getHeight(), 10f);
						if (hitIdx >= 0) {
							selectedEndoPointIndex = hitIdx;
							draggingEndoPointIndex = hitIdx;
							draggingEndoPointDepth = EndoPathPicker.computeForwardDepth(lastMvp,
									endoPath.getPoint(hitIdx).getPosition());
							dragOriginalPosition = endoPath.getPoint(hitIdx).getPosition();
							// lastX/lastYは更新しない -> mouseDraggedでオービット回転に落ちないようにする
						} else {
							lastX = e.getX();
							lastY = e.getY();
						}
					} else {
						// 左クリック: カメラ回転開始
						lastX = e.getX();
						lastY = e.getY();
					}
				} else if (SwingUtilities.isRightMouseButton(e) && e.isControlDown() && !endoscopyMode
						&& !endoPathEditMode) {
					// ★ Ctrl + 右クリック: カット開始（内視鏡モード・パス編集モード中は、表示中のビューと
					// performCut()が再計算するビューが食い違うため、そもそもカットモードに入らせない）
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
				} else if (draggingEndoPointIndex >= 0) {
					// ★ パス点のドラッグ確定: ライブプレビューで書き換わった値を一旦「変更前」に戻してから
					// MovePointCommandを構築する（コンストラクタがこの時点のpathの値を"old"として捕捉するため）
					int idx = draggingEndoPointIndex;
					org.joml.Vector3f currentLivePos = endoPath.getPoint(idx).getPosition();
					if (!currentLivePos.equals(dragOriginalPosition, 1e-6f)) {
						endoPath.setPointPosition(idx, dragOriginalPosition);
						undoManager.addCommand(new EndoCommands.MovePointCommand(endoPath, idx, currentLivePos));
					}
					draggingEndoPointIndex = -1;
					dragOriginalPosition = null;
					repaint();
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
				} else if (draggingEndoPointIndex >= 0) {
					// ★ パス点のライブプレビュー（コマンドはmouseReleasedで1回だけ発行する）
					org.joml.Vector3f hit = EndoPathPicker.computeDragPosition(lastMvp, e.getX(), e.getY(),
							getWidth(), getHeight(), draggingEndoPointDepth);
					if (hit != null) {
						endoPath.setPointPosition(draggingEndoPointIndex, hit);
						repaint();
					}
				} else if (SwingUtilities.isLeftMouseButton(e)) {
					if (endoscopyMode) {
						// ★マイクラ風マウスルック: 内視鏡視点中は見ている方向だけをドラッグで変える
						float dx = (e.getX() - lastX) * ENDO_LOOK_SENSITIVITY;
						float dy = (e.getY() - lastY) * ENDO_LOOK_SENSITIVITY;
						endoCamera.addLookDelta(dx, -dy);
						lastX = e.getX();
						lastY = e.getY();
						repaint();
					} else {
						// カメラ回転
						float dx = (e.getX() - lastX) * 0.5f;
						float dy = (e.getY() - lastY) * 0.5f;
						camera.rotate(dx, dy);
						lastX = e.getX();
						lastY = e.getY();
						repaint();
					}
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
				if (e.getKeyCode() == KeyEvent.VK_Z && e.isControlDown() && e.isShiftDown()) {
					// Ctrl + Shift + Z (Redo)
					undoManager.redo();
					repaint();
					Log.logger.fine("Redo");
				} else if (e.getKeyCode() == KeyEvent.VK_Z && e.isControlDown()) {
					// Ctrl + Z (Undo)
					undoManager.undo();
					repaint();
					Log.logger.fine("Undo");
				} else if (e.getKeyCode() == KeyEvent.VK_Y && e.isControlDown()) {
					// Ctrl + Y (Redo、互換用の別ショートカット)
					undoManager.redo();
					repaint();
					Log.logger.fine("Redo");
				} else if (e.getKeyCode() == KeyEvent.VK_DELETE && endoPathEditMode && selectedEndoPointIndex >= 0) {
					// ★パス編集モード中、選択中の点をDeleteキーで削除
					undoManager.addCommand(new EndoCommands.RemovePointCommand(endoPath, selectedEndoPointIndex));
					selectedEndoPointIndex = -1;
					repaint();
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
		
		meshRenderer = new MeshRenderer();
		meshRenderer.init();
	}
	
	public void setAutoSwapBuffer(boolean auto) {
        this.autoSwapBuffer = auto;
    }

	public void setVolumeData(VolumeData vol) {
		this.currentVolumeData = vol;
		this.pendingVolume = vol;

		// ★追加1: 古いUndo履歴を消去し、メモリリークと誤作動を防ぐ
		if (this.undoManager != null) {
			this.undoManager.clear();
		}

		// ★追加: 内視鏡パスはボクセル次元基準のローカル座標を保持しているため、
		// ボリュームが変わると無意味になる。新しいボリュームに合わせてリセットする。
		this.endoPath.clear();
		this.endoPath.setLinearInterpolation(false);
		this.endoCamera.setU(0f);
		this.endoPathSourceCurve = null;
		this.endoPathSourceSampler = null;

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
	
	public com.vis.core.view.D3.ui.Camera getCamera() {
		return this.camera;
	}

	public org.joml.Matrix4f getModelMatrix() {
		return calculateModelMatrix();
	}

	// ==========================================
	// 仮想内視鏡（フライスルー）モード
	// ==========================================
	public void setEndoscopyMode(boolean enabled) {
		this.endoscopyMode = enabled;
		if (enabled && endoPathEditMode) {
			setEndoPathEditMode(false); // 相互排他 + 選択状態のクリーンアップを再利用
		}
		repaint();
	}

	public boolean isEndoscopyMode() {
		return endoscopyMode;
	}

	public EndoPath3D getEndoPath() {
		return endoPath;
	}

	public EndoCamera getEndoCamera() {
		return endoCamera;
	}

	/** EndoCommands等をpushするための既存Undo履歴への導線（カット機能と共用） */
	public UndoManager getUndoManager() {
		return undoManager;
	}

	public void setShowEndoPath(boolean show) {
		this.showEndoPath = show;
		repaint();
	}

	public void setEndoPathEditMode(boolean enabled) {
		this.endoPathEditMode = enabled;
		if (enabled) {
			this.endoscopyMode = false;
		} else {
			this.selectedEndoPointIndex = -1;
			this.draggingEndoPointIndex = -1;
			this.dragOriginalPosition = null;
		}
		repaint();
	}

	public boolean isEndoPathEditMode() {
		return endoPathEditMode;
	}

	private static final double ENDO_PATH_FROM_CENTERLINE_STEP_MM = 1.0;

	/**
	 * Replaces the virtual endoscopy path with a dense resampling of
	 * {@code curve} (physical LPS mm, as produced by
	 * {@link CenterlineAnalysisDialog}), each sample converted into this
	 * canvas's local render-space cube via {@code sampler} - the same
	 * conversion {@link CenterlineGraphRenderer} uses to overlay the curve.
	 *
	 * Resamples in physical space first rather than handing {@code curve}'s
	 * own (sparse) control points straight to {@link EndoPath3D}: the local
	 * cube normalizes each axis independently (x/width, y/height, z/depth),
	 * which is anisotropic relative to true mm distances whenever the
	 * volume's physical extents differ per axis (slice spacing vs. in-plane
	 * spacing) - the canvas's model matrix corrects this back to true
	 * proportions for already-computed point positions, but not for the
	 * Centripetal Catmull-Rom math EndoPath3D itself does between control
	 * points.
	 *
	 * Dense sampling alone only shrinks that drift, it doesn't remove it -
	 * Catmull-Rom's chord-length-based knot parameterization is still
	 * evaluated in the skewed space, so high-curvature stretches (tight
	 * turns near a bifurcation) still pull the camera measurably off the
	 * true centerline between points. So the path is also switched to plain
	 * linear interpolation ({@link EndoPath3D#setLinearInterpolation}):
	 * with ~1mm spacing a straight segment between consecutive points is
	 * visually indistinguishable from a curve, and unlike Catmull-Rom it
	 * can never deviate from the (correctly placed) points themselves -
	 * matching the straight-segment polyline {@link CenterlineGraphRenderer}
	 * already draws for the same dense samples.
	 *
	 * Also remembers {@code curve}/{@code sampler} so the source centerline
	 * keeps being drawn (highlighted, the same way {@link CenterlineAnalysisDialog}
	 * shows it) regardless of camera mode and even after that dialog is
	 * closed - otherwise there'd be no visual confirmation a path is loaded
	 * once the analysis overlay disappears.
	 */
	public void setEndoPathFromCenterline(com.vis.core.slicer.Centerline3D curve,
			com.vis.core.slicer.VolumeSampler sampler) {
		if (curve == null || sampler == null || curve.size() < 2) return;

		double length = curve.getTotalLength();
		int samples = Math.max(2, (int) Math.ceil(length / ENDO_PATH_FROM_CENTERLINE_STEP_MM) + 1);

		this.endoPath.clear();
		for (int i = 0; i < samples; i++) {
			double s = (samples == 1) ? 0 : length * i / (samples - 1);
			org.joml.Vector3d physical = curve.positionAt(s);
			org.joml.Vector3d local = sampler.toLocalRenderSpace(physical);
			this.endoPath.addPoint(new org.joml.Vector3f((float) local.x, (float) local.y, (float) local.z));
		}
		this.endoPath.setLinearInterpolation(true);
		this.endoCamera.setU(0f);
		this.endoCamera.resetLook();
		this.selectedEndoPointIndex = -1;
		this.draggingEndoPointIndex = -1;
		this.dragOriginalPosition = null;
		this.endoPathSourceCurve = curve;
		this.endoPathSourceSampler = sampler;
		repaint();
	}

	// ==========================================
	// グループを解釈して色とIDを割り振る setRoiData
	// 利用する側は、roiを渡すだけで良い。
	// ==========================================
	public void setRoiData(java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> rois) {
	    if (currentVolumeData == null || rois.isEmpty()) return;
	    
	    this.currentRois = new java.util.ArrayList<>(rois);
	    
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
	        byte[] mask = createMergedRoiMask(currentVolumeData, rois, mappedIds);
	        this.pendingRoiMask = mask; 
	        SwingUtilities.invokeLater(this::repaint);
	    }).start();
	}
	
	// ==========================================
	// ★追加: 特定のグループに属するROIのリストを取得する
	// ==========================================
	public java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> getRoisByGroup(String targetGroupName) {
		java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> result = new java.util.ArrayList<>();
		if (currentRois == null || targetGroupName == null)
			return result;

		for (com.vis.core.view.D3.roi.FreeFormRoi3D roi : currentRois) {
			// GLCanvas内でのグループID決定ロジックと同じ方法でIDを取得
			String groupId = roi.getProperty(com.vis.configuration.RoiDBKey.RoiGroup.name());
			if (groupId == null || groupId.isEmpty()) {
				groupId = roi.getProperty(com.vis.configuration.RoiDBKey.RoiID.name());
				if (groupId == null || groupId.isEmpty()) {
					groupId = String.valueOf(roi.hashCode());
				}
			}

			// プレーンなID（例: "123"）、またはUI表示用の名前（例: "Group: 123"）のどちらでもヒットするようにする
			String groupDisplayName = "Group: " + groupId;
			if (targetGroupName.equals(groupId) || targetGroupName.equals(groupDisplayName)) {
				result.add(roi);
			}
		}
		return result;
	}

	// （おまけ）保持しているすべてのROIを取得したい場合用
	public java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> getAllRois() {
		return currentRois;
	}

	// ==========================================
	// 改良版: 事前計算された mappedIds を使ってマスクを描き込む
	// ==========================================
	private byte[] createMergedRoiMask(VolumeData vol, java.util.List<com.vis.core.view.D3.roi.FreeFormRoi3D> rois,
			int[] mappedIds) {
		int w = vol.width;
		int h = vol.height;
		int d = vol.depth;
		byte[] mask = new byte[w * h * d];
		
		double[] startIpp = vol.startIpp;
		double[] iop = vol.iop;
		double[] stepZ = vol.stepZ;

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

	public void setCinematicMode(boolean enable) {
		this.isCinematicMode = enable;
		if (enable) {
			cinematicResetRequested = true; // 実際のリセットはpaintGL()内で行う（GLコンテキストが必要なため）
		}
		repaint();
	}

	public boolean isCinematicMode() {
		return isCinematicMode;
	}

	public com.vis.core.view.D3.ui.cinematic.CinematicParams getCinematicParams() {
		return cinematicParams;
	}

	/** Call after changing fields on {@link #getCinematicParams()} so progressive accumulation restarts. */
	public void invalidateCinematicAccumulation() {
		cinematicResetRequested = true; // 実際のリセットはpaintGL()内で行う（GLコンテキストが必要なため）
		repaint();
	}

	public String getCinematicBackendName() {
		return cinematicRenderer != null ? cinematicRenderer.getBackendName() : "-";
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
		// Deferred to paintGL() - see pendingLutUpdate.
		pendingLutUpdate = () -> volumeRenderer.loadLut(file);
		cinematicResetRequested = true; // 新しいLUTを即座に反映させる（蓄積済みの古いLUTの絵と混ざらないように）
		repaint();
	}

	/**
	 * Apply an already-resolved LUT/color model (e.g. from
	 * Resources.loadLUT(name)) without re-reading it from disk. Used by the
	 * Control Panel's "Color Map (LUT)" picker.
	 */
	public void applyLut(java.awt.image.IndexColorModel cm) {
		pendingLutUpdate = () -> volumeRenderer.applyLut(cm);
		cinematicResetRequested = true;
		repaint();
	}

	/**
	 * Switch to one of the built-in procedural LUTs generated by
	 * VolumeRenderer.generateLUT(int) (0 = grayscale ramp, anything else =
	 * the built-in hot/rainbow-style ramp).
	 */
	public void setLutType(int type) {
		pendingLutUpdate = () -> volumeRenderer.generateLUT(type);
		cinematicResetRequested = true;
		repaint();
	}

	/**
	 * Replace the opacity-vs-value curve (256 entries) used across VR/MIP/
	 * Ortho, independently of the current color map. Used by the volume
	 * opacity curve editor dialog.
	 */
	public void applyOpacityCurve(byte[] opacity256) {
		// Deferred to paintGL() - see pendingLutUpdate. A direct call here
		// would race the GL context the same way loadLut()/applyLut() did.
		pendingLutUpdate = () -> volumeRenderer.applyOpacityCurve(opacity256);
		cinematicResetRequested = true;
		repaint();
	}

	public byte[] getCurrentOpacityCurve() {
		return volumeRenderer.getCurrentOpacityCurve();
	}

	// ==========================================================
	// 中心線解析（CenterlineAnalysisDialog）のオーバーレイ表示
	// ==========================================================
	public void setCenterlineGraph(com.vis.core.centerline.CenterlineGraph graph,
			com.vis.core.slicer.VolumeSampler renderSampler) {
		this.currentCenterlineGraph = graph;
		this.centerlineRenderSampler = renderSampler;
		repaint();
	}

	public void clearCenterlineGraph() {
		this.currentCenterlineGraph = null;
		this.centerlineRenderSampler = null;
		this.selectedCenterlineBranchIds.clear();
		this.selectedCenterlineNodeIds.clear();
		this.selectedCenterlineCurve = null;
		repaint();
	}

	public void setSelectedCenterlineBranches(java.util.Set<Integer> branchIds) {
		this.selectedCenterlineBranchIds = branchIds != null ? branchIds : new java.util.HashSet<>();
		repaint();
	}

	public void setSelectedCenterlineNodes(java.util.Set<Integer> nodeIds) {
		this.selectedCenterlineNodeIds = nodeIds != null ? nodeIds : new java.util.HashSet<>();
		repaint();
	}

	/**
	 * The curve currently being previewed for CPR/Straighten - a single
	 * branch's curve, or a multi-branch path - drawn live on top of the
	 * graph overlay regardless of which (if any) existing branch IDs it
	 * corresponds to.
	 */
	public void setSelectedCenterlineCurve(com.vis.core.slicer.Centerline3D curve) {
		this.selectedCenterlineCurve = curve;
		repaint();
	}

	/**
	 * シネマティック・レンダリングの蓄積バッファを継続/リセットすべきか判定するための、
	 * 「見た目に影響するすべての状態」の文字列フィンガープリント。カメラ・W/L・LUT・
	 * ライト設定のいずれかが前フレームと変われば別の文字列になり、呼び出し側で
	 * invalidateAccumulation()のトリガーに使う。samplesPerFrameは蓄積済みの値の
	 * 有効性に影響しないので意図的に含めていない。
	 */
	private String cinematicFingerprint(org.joml.Matrix4f mvp) {
		StringBuilder sb = new StringBuilder(160);
		float[] m = new float[16];
		mvp.get(m);
		for (float v : m) {
			sb.append(Float.floatToIntBits(v)).append(',');
		}
		sb.append(volumeRenderer.getWindowCenter()).append(',');
		sb.append(volumeRenderer.getWindowWidth()).append(',');
		sb.append(volumeRenderer.getLutGeneration()).append(',');
		sb.append(cinematicParams.lightAzimuth).append(',');
		sb.append(cinematicParams.lightElevation).append(',');
		sb.append(cinematicParams.lightIntensity).append(',');
		sb.append(cinematicParams.ambientIntensity).append(',');
		sb.append(cinematicParams.scatteringAnisotropy).append(',');
		sb.append(cinematicParams.lightAngularRadius);
		// uExposure is intentionally excluded: it's a post-process multiplier applied in the
		// present pass only, so changing it doesn't invalidate already-accumulated radiance.
		return sb.toString();
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
	
	// ==========================================
	// ★追加: コントラスト（Window Level）の自動最適化
	// ==========================================
	public void optimizeContrast() {
		if (currentVolumeData != null && volumeRenderer != null) {
			float minN = currentVolumeData.minVal;
			float maxN = currentVolumeData.maxVal;

			// OpenGLの 0.0 ~ 1.0 空間のスケールに合わせる
			if (currentVolumeData.dataType == VolumeData.DataType.SHORT) {
				minN /= 65535.0f;
				maxN /= 65535.0f;
			} else if (currentVolumeData.dataType == VolumeData.DataType.BYTE) {
				minN /= 255.0f;
				maxN /= 255.0f;
			}

			// データの最小〜最大値のど真ん中を中心（Center）にする
			float center = (minN + maxN) / 2.0f;
			// データの幅をそのまま Window Width にする（少しだけ余裕を持たせる）
			float width = (maxN - minN) * 1.1f;

			if (width < 0.001f)
				width = 1.0f; // ゼロ除算防止

			volumeRenderer.setWindowLevel(center, width);
			repaint();
		}
	}
	
	// --- メッシュ制御用の新しいメソッドを追加 ---
	// ==========================================================
	// ★ 新設: メッシュの追加・更新
	// ==========================================================
	public void addOrUpdateMesh(String name, MeshData mesh) {
		if (mesh == null)
			return;

		// 既存のメッシュがあれば取得、なければ新規作成
		MeshGLResource res = glMeshMap.computeIfAbsent(name, k -> new MeshGLResource());
		res.meshData = mesh;
		res.indexCount = mesh.indices.length;
		res.needsUpload = true; // 描画スレッド側でVBOを再生成させる

		this.activeMeshName = name;
	}

	// ★ 新設: 特定のメッシュの色を個別に変更する
	public void setMeshColor(String name, java.awt.Color color) {
		MeshGLResource res = glMeshMap.get(name);
		if (res != null && color != null) {
			res.color = color;
			repaint();
		}
	}

	// ★ 新設: 特定のメッシュの現在の色を取得する
	public java.awt.Color getMeshColor(String name) {
		MeshGLResource res = glMeshMap.get(name);
		return res != null ? res.color : new java.awt.Color(200, 200, 200);
	}

	// ★ 新設: アクティブなメッシュの指定
	public void setActiveMeshName(String name) {
		this.activeMeshName = name;
	}

	// ★ 新設: 特定のメッシュの削除（OpenGLリソースの解放を伴うため重要）
	public void removeMesh(String name) {
		MeshGLResource res = glMeshMap.remove(name);
		if (res != null) {
			// OpenGLスレッド以外からDelを呼ぶと危険なため、一時的にフラグ等で処理するか、
			// 描画ループの中でまとめて glDeleteX を呼ぶのが安全です（後述の描画処理で対応）
			res.needsUpload = false;
			// 簡易的に、次の描画時にコンテキスト上で消去するためにマークするなどの処理
		}
	}

	public void setMeshVisible(boolean visible) {
	    this.isMeshVisible = visible;
	    repaint();
	}

	public void setMeshAlpha(float alpha) {
	    this.currentMeshAlpha = alpha;
	    repaint();
	}
	
	// ==========================================================
	// ★ 新設: 汎用カラーバーレジェンドの追加・設定
	// ==========================================================
	public void addLegend(double min, double max, String title, LegendPosition pos, ij.process.LUT lut) {
		this.legendConfig.minVal = min;
		this.legendConfig.maxVal = max;
		this.legendConfig.title = title;
		this.legendConfig.position = pos;
		this.legendConfig.lut = lut; // 受け取ったLUTをセット
		this.legendConfig.visible = true;
		repaint();
	}

	public void setLegendVisible(boolean visible) {
		this.legendConfig.visible = visible;
		repaint();
	}

	// --- Swingのオーバーレイ描画 (線を引く) ---
	// GLCanvasはAWTコンポーネントなので、paint()をオーバーライドして
	// OpenGL描画の上に2D描画を重ねることができます（環境によってはチラつくことがありますが簡便です）
	@Override
	public void paint(Graphics g) {
		super.paint(g); // これがOpenGL描画を呼び出す(paintGL)
		drawOverlay(g);
	}
	
	public void drawOverlay(Graphics g) {
		// ★カット輪郭線はpaintGL()内でGL描画するため、ここでは描画しない
		// (Timerからrender()を直接呼ぶ構成のため、Graphics2Dでの描画はpaint()経由でしか効かず
		//  次のTimer Tickで即座に上書きされてしまい表示されなかった)

		if (legendConfig.visible) {
			java.awt.Graphics2D g2d = (java.awt.Graphics2D) g.create();
			g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
					java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int canvasW = getWidth();
			int canvasH = getHeight();

			int barWidth = 16;
			int barHeight = 160;
			int margin = 30; // 画面端からの余白

			// 選択された配置位置(Position)に応じて始点座標(startX, startY)を自動計算
			int startX = 0;
			int startY = 0;

			switch (legendConfig.position) {
			case TOP_LEFT:
				startX = margin + 15;
				startY = margin + 30;
				break;
			case TOP_RIGHT:
				startX = canvasW - margin - barWidth - 50;
				startY = margin + 30;
				break;
			case BOTTOM_LEFT:
				startX = margin + 15;
				startY = canvasH - margin - barHeight - 20;
				break;
			case BOTTOM_RIGHT:
			default:
				startX = canvasW - margin - barWidth - 50;
				startY = canvasH - margin - barHeight - 20;
				break;
			}

			// 1. 半透明の黒い背景パネル
			g2d.setColor(new java.awt.Color(0, 0, 0, 160));
			g2d.fillRoundRect(startX - 15, startY - 30, barWidth + 65, barHeight + 50, 10, 10);

			// 2. グラデーションバーの描画
			for (int i = 0; i < barHeight; i++) {
				float norm = 1.0f - ((float) i / barHeight);
				int lutIndex = Math.max(0, Math.min(255, (int) (norm * 255.0f))); // 0〜255の階調にマッピング

				if (legendConfig.lut != null) {
					// ★ LUTが指定されている場合は、そこからRGBを引いてくる
					int r_ = legendConfig.lut.getRed(lutIndex);
					int g_ = legendConfig.lut.getGreen(lutIndex);
					int b_ = legendConfig.lut.getBlue(lutIndex);
					g2d.setColor(new java.awt.Color(r_, g_, b_));
				} else {
					// フォールバック: LUTが無い場合は従来の青〜赤のグラデーション
					float hue = (float) ((1.0 - norm) * 240.0 / 360.0);
					g2d.setColor(java.awt.Color.getHSBColor(hue, 1.0f, 1.0f));
				}

				g2d.drawLine(startX, startY + i, startX + barWidth, startY + i);
			}

			// 3. 外枠
			g2d.setColor(java.awt.Color.WHITE);
			g2d.drawRect(startX, startY, barWidth, barHeight);

			// 4. テキストラベル（引数の値とタイトルを動的描画）
			g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));

			// タイトル
			g2d.drawString(legendConfig.title, startX - 10, startY - 12);

			// 最大値 (上側)
			g2d.drawString(String.format("%.2f+", legendConfig.maxVal), startX + barWidth + 6, startY + 8);

			// 中間値
			double midVal = (legendConfig.minVal + legendConfig.maxVal) / 2.0;
			g2d.drawLine(startX + barWidth, startY + barHeight / 2, startX + barWidth + 4, startY + barHeight / 2);
			g2d.drawString(String.format("%.2f", midVal), startX + barWidth + 6, startY + barHeight / 2 + 5);

			// 最小値 (下側)
			g2d.drawString(String.format("%.2f", legendConfig.minVal), startX + barWidth + 6, startY + barHeight);

			g2d.dispose();
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

		// LUT/不透明度カーブの更新 (GLコンテキストが有効なここで初めて反映する)
		if (pendingLutUpdate != null) {
			pendingLutUpdate.run();
			pendingLutUpdate = null;
		}

		// ★追加: メッシュデータの転送
	    if (pendingMesh != null) {
	        meshRenderer.uploadMesh(pendingMesh);
	        pendingMesh = null;
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

		// (以前追加した calculateModelMatrix() を呼び出す)
		org.joml.Matrix4f model = calculateModelMatrix();

		// ★内視鏡モード中（パスが2点以上ある場合のみ）は内視鏡カメラのビュー行列を使う。
		// パスが未定義の間は自動的にオービットカメラにフォールバックする。
		org.joml.Matrix4f view = (endoscopyMode && endoPath.size() >= 2) ? endoCamera.getViewMatrix(model)
				: camera.getViewMatrix();

		org.joml.Matrix4f mvp = new org.joml.Matrix4f(proj).mul(view).mul(model);
		lastMvp.set(mvp); // ★パス編集のピッキング(EndoPathPicker)が使う最新のmvpを保持

		// ==========================================================
		// ★修正: カメラ位置の計算を if文の外に引き上げる（メッシュ描画でも共通で使うため）
		// ==========================================================
		org.joml.Matrix4f modelViewInv = new org.joml.Matrix4f(view).mul(model).invert();
		org.joml.Vector3f camPosLocal = new org.joml.Vector3f();
		modelViewInv.getTranslation(camPosLocal);

		if (isCinematicMode && cinematicRenderer != null) {
			cinematicRenderer.resize(physW, physH);
			String fingerprint = cinematicFingerprint(mvp);
			boolean fingerprintChanged = !fingerprint.equals(cinematicLastFingerprint);
			if (fingerprintChanged || cinematicResetRequested) {
				cinematicRenderer.invalidateAccumulation();
				cinematicLastFingerprint = fingerprint;
				cinematicResetRequested = false;
			}
			cinematicRenderer.render(mvp, camPosLocal, volumeRenderer, cinematicParams);
		} else if (isOrthoMode) {
			org.joml.Matrix4f scaledProjView = new org.joml.Matrix4f(proj).mul(view).mul(model);
			volumeRenderer.setOrthoShowRoi(orthoRoiMode == OrthoRoiMode.SLICE_2D);
			volumeRenderer.renderOrthoSlices(scaledProjView, sliceX, sliceY, sliceZ);

			if ((orthoRoiMode == OrthoRoiMode.FLOAT_3D || orthoRoiMode == OrthoRoiMode.EMBEDDED_3D) && currentVolumeData != null) {
				boolean tempVolVisible = volumeRenderer.isVolumeVisible();
				boolean tempRoiVisible = volumeRenderer.isRoiVisible();

				volumeRenderer.setVolumeVisible(false);
				volumeRenderer.setRoiVisible(true);

				boolean isEmbedded = (orthoRoiMode == OrthoRoiMode.EMBEDDED_3D);
				volumeRenderer.render(mvp, camPosLocal, isEmbedded, sliceX, sliceY, sliceZ);

				volumeRenderer.setVolumeVisible(tempVolVisible);
				volumeRenderer.setRoiVisible(tempRoiVisible);
			}
		} else {
			// 通常の3D描画
			volumeRenderer.render(mvp, camPosLocal, false, 0f, 0f, 0f);
		}
		
		// ==========================================================
		// ★ 修正・完全版: 登録されているすべてのメッシュをループ描画する
		// ==========================================================
		if (isMeshVisible && meshRenderer != null) { 

			for (java.util.Map.Entry<String, MeshGLResource> entry : glMeshMap.entrySet()) {
				String name = entry.getKey();
				MeshGLResource res = entry.getValue();

				if (!res.visible) continue;

				// 【A】メインスレッドから送られてきた新規データをGPUに安全に転送する処理
				if (res.needsUpload) {
					if (res.vao != 0) {
						org.lwjgl.opengl.GL30.glDeleteVertexArrays(res.vao);
						org.lwjgl.opengl.GL15.glDeleteBuffers(res.vboVertices);
						org.lwjgl.opengl.GL15.glDeleteBuffers(res.vboNormals);
						if (res.vboColors != 0) org.lwjgl.opengl.GL15.glDeleteBuffers(res.vboColors); // ★ 追加
						org.lwjgl.opengl.GL15.glDeleteBuffers(res.ibo);
					}

					res.vao = org.lwjgl.opengl.GL30.glGenVertexArrays();
					res.vboVertices = org.lwjgl.opengl.GL15.glGenBuffers();
					res.vboNormals = org.lwjgl.opengl.GL15.glGenBuffers();
					res.ibo = org.lwjgl.opengl.GL15.glGenBuffers();

					org.lwjgl.opengl.GL30.glBindVertexArray(res.vao);

					// 1. 頂点バッファ (Location 0)
					org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, res.vboVertices);
					java.nio.FloatBuffer verticesBuffer = org.lwjgl.system.MemoryUtil.memAllocFloat(res.meshData.vertices.length);
					verticesBuffer.put(res.meshData.vertices).flip();
					org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, verticesBuffer, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
					org.lwjgl.opengl.GL20.glVertexAttribPointer(0, 3, org.lwjgl.opengl.GL11.GL_FLOAT, false, 0, 0);
					org.lwjgl.opengl.GL20.glEnableVertexAttribArray(0);
					org.lwjgl.system.MemoryUtil.memFree(verticesBuffer);

					// 2. 法線バッファ (Location 1)
					org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, res.vboNormals);
					java.nio.FloatBuffer normalsBuffer = org.lwjgl.system.MemoryUtil.memAllocFloat(res.meshData.normals.length);
					normalsBuffer.put(res.meshData.normals).flip();
					org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, normalsBuffer, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
					org.lwjgl.opengl.GL20.glVertexAttribPointer(1, 3, org.lwjgl.opengl.GL11.GL_FLOAT, false, 0, 0);
					org.lwjgl.opengl.GL20.glEnableVertexAttribArray(1);
					org.lwjgl.system.MemoryUtil.memFree(normalsBuffer);

					// ==========================================================
					// ★ 追加: 3. 頂点カラーバッファ (Location 2)
					// ==========================================================
					if (res.meshData.colors != null) {
						res.vboColors = org.lwjgl.opengl.GL15.glGenBuffers();
						org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, res.vboColors);
						java.nio.FloatBuffer colorsBuffer = org.lwjgl.system.MemoryUtil.memAllocFloat(res.meshData.colors.length);
						colorsBuffer.put(res.meshData.colors).flip();
						org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, colorsBuffer, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
						org.lwjgl.opengl.GL20.glVertexAttribPointer(2, 4, org.lwjgl.opengl.GL11.GL_FLOAT, false, 0, 0);
						org.lwjgl.opengl.GL20.glEnableVertexAttribArray(2);
						org.lwjgl.system.MemoryUtil.memFree(colorsBuffer);
					}

					// 4. インデックスバッファ
					org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER, res.ibo);
					java.nio.IntBuffer indicesBuffer = org.lwjgl.system.MemoryUtil.memAllocInt(res.meshData.indices.length);
					indicesBuffer.put(res.meshData.indices).flip();
					org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
					org.lwjgl.system.MemoryUtil.memFree(indicesBuffer);

					org.lwjgl.opengl.GL30.glBindVertexArray(0);
					res.needsUpload = false;
				}

				// 【B】実際の描画処理
				if (res.vao != 0 && res.indexCount > 0) {
					java.awt.Color renderColor = res.color;
					
					if (name.equals(activeMeshName)) {
						int r = Math.min(255, (int)(renderColor.getRed() * 1.3));
						int g = Math.min(255, (int)(renderColor.getGreen() * 1.3));
						int b = Math.min(255, (int)(renderColor.getBlue() * 1.3));
						renderColor = new java.awt.Color(r, g, b, renderColor.getAlpha());
					}

					// ★ 修正: 頂点カラー配列を持っているか(colors != null)の判定フラグを追加して renderMesh に渡す
					boolean hasVertexColors = (res.meshData.colors != null);
					meshRenderer.renderMesh(res.vao, res.indexCount, mvp, model, camPosLocal, renderColor, currentMeshAlpha, hasVertexColors);
				}
			}
		}

		// 中心線解析グラフのオーバーレイ（枝＋端点/分岐点）＋選択中の曲線のLive表示
		if (centerlineRenderSampler != null && centerlineGraphRenderer != null) {
			centerlineGraphRenderer.render(currentCenterlineGraph, centerlineRenderSampler, mvp,
					selectedCenterlineBranchIds, selectedCenterlineNodeIds, selectedCenterlineCurve);
		}

		// 内視鏡パスの元になった中心線：解析ダイアログを閉じた後も、内視鏡視点(endoscopyMode)中も、
		// 常に強調曲線として描画する（モードを問わずFly-Through対象が見える状態を保つ）。
		// ダイアログが開いたまま別の枝/パスが選択されていると、そちらのライブカーブ(白)と同時に
		// 表示されることがあるため、混同しないよう専用の色(オレンジ)を使う。
		if (endoPathSourceCurve != null && endoPathSourceSampler != null && centerlineGraphRenderer != null) {
			centerlineGraphRenderer.render(null, endoPathSourceSampler, mvp, null, null, endoPathSourceCurve,
					ENDO_PATH_SOURCE_CURVE_COLOR);
		}

		// 最後にGizmoを描画 (右下にオーバーレイ)

		// 最後にGizmoを描画 (右下にオーバーレイ)
		// Gizmoにも物理ピクセルサイズを渡さないと、位置がズレたり小さくなったりします
		if (axesGizmo != null) {
			// ★修正: camera.getViewMatrix()を再計算せず、上で使った現在の view（内視鏡モード中はそちら）を再利用する。
			// 以前は常にオービットカメラの向きを表示しており、内視鏡モードでも実際の視点と食い違っていた。
			axesGizmo.render(view, physW, physH);
		}

		// ★4.1: 内視鏡モード中のみ、ミニ方位インジケーター（ワールドの上方向）を表示
		if (endoscopyMode && endoOrientationIndicator != null) {
			endoOrientationIndicator.render(view, physW, physH);
		}

		// ★カット中の輪郭線を最前面に描画（論理ピクセル基準。MouseEventの座標系と一致させる）
		if (isCuttingMode && currentPath.size() > 1 && cutLineRenderer != null) {
			cutLineRenderer.render(currentPath, w, h);
		}

		// ★内視鏡パス（曲線＋制御点）とカメラ位置・向きマーカーを描画
		if (showEndoPath && endoPathRenderer != null && !endoPath.isEmpty()) {
			endoPathRenderer.render(endoPath, mvp, selectedEndoPointIndex);
			endoPathRenderer.renderCameraMarker(endoCamera, mvp); // ★4.2
		}

		if (autoSwapBuffer) {
			// 親クラス単体で起動している場合（ダブルバッファ環境）
			try {
				swapBuffers(); // swapBuffersが内部で自動的にフラッシュを兼ねるため、直前のglFlushは不要
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			// 子クラス（AneurysmGLCanvas）が後から描き足す場合
			// ダブルバッファ（doubleBuffer=true）環境なので、ここでglFlush()を呼ばなくても
			// コマンドは裏画面のバッファに安全に蓄積されます。
			// 最終的に子クラスの最末尾で描き終わった後に一括してスワップ・出力されるため、
			// 親クラスのこの場所では、glFlush()すら「完全に何も書かない」のが最も効率的で綺麗です。
		}
	}
}