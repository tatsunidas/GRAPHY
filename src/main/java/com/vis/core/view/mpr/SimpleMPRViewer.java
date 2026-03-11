/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
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
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.view.mpr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.NoninvertibleTransformException;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.vis.core.view.D2.ui.glasses.CanvasGlass;
import com.vis.core.view.D2.ui.glasses.EventGlass;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * 
 * @author tatsunidas
 *
 */
public class SimpleMPRViewer extends JFrame{
	
	//debug
	public static void main(String[] arags) {
		String ax_path = "/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151";
		ImagePlus imp = FolderOpener.open(ax_path);
		new SimpleMPRViewer(imp);
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

    // 画像とキャンバスの保持
    private ImagePlus axialImp, sagittalImp, coronalImp;
    private Praparat axialCanvas, sagittalCanvas, coronalCanvas;
    
    // 現在の交点（3D空間上のボクセルインデックス or LPS座標）
    private int currentX, currentY, currentZ;
    
    // 元となるベーススタック
    private ImagePlus baseVolume;
    private CutSurface basePlane;

    public SimpleMPRViewer(ImagePlus inputImage) {
        this.baseVolume = inputImage;
        setTitle("Simple MPR Viewer (LPS Space)");
        setSize(1000, 1000);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        init();
    }
    
    private void init() {
        this.basePlane = determineBasePlane(baseVolume);
        
        // 1. 各断面のImagePlusを生成（ボクセルサイズを考慮したマトリクス生成）
        reconstructCenterOrthogonal();
        
        ImageStack test = new ImageStack(baseVolume.getWidth(), baseVolume.getHeight());
//        ImageStack baseStack = baseVolume.getStack();
//        int s = baseVolume.getNSlices();
//        for(int i=0;i<s; i++) {
//        	baseVolume.setSlice(i+1);
//        	String sliceLabel = baseStack.getSliceLabel(i+1);
//        	ImageProcessor ip = baseStack.getProcessor(i+1);
//        	ImageProcessor fp = (FloatProcessor) ip.convertToFloat();
//        	test.addSlice(sliceLabel, fp);
//        }
//        ImagePlus imp2 = new ImagePlus("32-bit", test);
////        imp2.setCalibration(baseVolume.getCalibration());
        
//        JFrame f = new JFrame();
//        sagittalCanvas = new Praparat(sagittalImp, Color.GREEN, ViewMode.MPR);
//        sagittalCanvas = new Praparat(imp2, Color.GREEN, ViewMode.MPR);
                
//        f.add(sagittalCanvas);
//        f.setSize(new Dimension(300, 300));
//        f.setVisible(true);

//        // 2. GUIの構築（メニューと4パネル）
        buildGUI();
//
//        // 3. マウスイベントの登録
        setupMouseListeners();
//        
        setVisible(true);
    }

    /**
     * show starting-up orthogonals
     */
	private void reconstructCenterOrthogonal() {

		// 中心座標の初期化（ピクセルベース）
		currentX = baseVolume.getWidth() / 2;
		currentY = baseVolume.getHeight() / 2;
		currentZ = baseVolume.getNSlices() / 2;

		Calibration cal = baseVolume.getCalibration();
		double px = cal.pixelWidth;
		double py = cal.pixelHeight;
		double pz = cal.pixelDepth;

		// ※ここにLPS空間でのサンプリングロジックが入ります。
		// 以下は「AXIALが入力された場合」の、スケールを考慮したマトリクス生成の概念です。

		if (basePlane == CutSurface.AXIAL) {
			// サジタルの再構成（Y軸 × Z軸）
			// Z軸方向のピクセル数は、pz / py 等の比率を掛けてマトリクスサイズを合わせる
			int scaledZ_forSagittal = (int) Math.round(baseVolume.getNSlices() * (pz / py));
			this.sagittalImp = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);

			// コロナルの再構成（X軸 × Z軸）
			int scaledZ_forCoronal = (int) Math.round(baseVolume.getNSlices() * (pz / px));
			this.coronalImp = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
		}
		// SAGITTAL, CORONALが入力の場合も同様に分岐して生成します
	}

    /**
     * @param willCreatePlane
     * @param x: x axis position on pixel coordinates in ref stack.
     * @param y: y axis position on pixel coordinates in ref stack.
     * @param z: z axis position on pixel coordinates in ref (i.e., 0-base slice position).
     * @return
     */
	private ImagePlus extractPlane(CutSurface willCreatePlane, int x, int y, int z) {
		// 1. 同一平面の場合は、指定されたスライスをそのまま複製して返す
		if (willCreatePlane == basePlane) {
			ij.ImageStack stack = baseVolume.getStack();
			int sliceIndex = Math.max(1, Math.min(z, stack.getSize()));
			return new ImagePlus(willCreatePlane.name(), stack.getProcessor(sliceIndex).duplicate());
		}

		// 2. リサイズ先のターゲットサイズを blankImage メソッドから取得
		ImagePlus targetBlank = blankImage(willCreatePlane);
		if (targetBlank == null)
			return null;

		int targetW = targetBlank.getWidth();
		int targetH = targetBlank.getHeight();

		int w = baseVolume.getWidth();
		int h = baseVolume.getHeight();
		ij.ImageStack stack = baseVolume.getStack();
		int d = stack.getSize();
		
		double px = 0.0;
		double py = 0.0;
		double pz = 0.0;
		
		Calibration baseCal = baseVolume.getCalibration();

		// 等倍で抽出するためのプロセッサ
		ij.process.ImageProcessor extractedIp = null;

		// パフォーマンス向上のため、スライス(Z)を外側のループにして抽出する
		if (basePlane == CutSurface.AXIAL) {
			if (willCreatePlane == CutSurface.SAGITTAL) {
				// サジタル抽出: 固定X = x
				// 抽出サイズ: 幅=Y(h), 高さ=Z(d)
				extractedIp = new ij.process.FloatProcessor(h, d);
				for (int zIdx = 1; zIdx <= d; zIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(zIdx);
					for (int yIdx = 0; yIdx < h; yIdx++) {
						extractedIp.putPixelValue(yIdx, zIdx - 1, slice.getPixelValue(x, yIdx));
					}
				}
				px = baseCal.pixelHeight;
				py = (baseCal.pixelDepth * d)/targetH;
				pz = baseCal.pixelWidth;
			} else if (willCreatePlane == CutSurface.CORONAL) {
				// コロナル抽出: 固定Y = y
				// 抽出サイズ: 幅=X(w), 高さ=Z(d)
				extractedIp = new ij.process.FloatProcessor(w, d);
				for (int zIdx = 1; zIdx <= d; zIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(zIdx);
					for (int xIdx = 0; xIdx < w; xIdx++) {
						extractedIp.putPixelValue(xIdx, zIdx - 1, slice.getPixelValue(xIdx, y));
					}
				}
				px = baseCal.pixelWidth;
				py = (baseCal.pixelDepth * d)/targetH;
				pz = baseCal.pixelHeight;
			}
		} else if (basePlane == CutSurface.SAGITTAL) {
			if (willCreatePlane == CutSurface.AXIAL) {
				// アキシャル抽出: 固定Z = z
				extractedIp = new ij.process.FloatProcessor(d, w);
				for (int xIdx = 1; xIdx <= d; xIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(xIdx);
					for (int yIdx = 0; yIdx < w; yIdx++) {
						extractedIp.putPixelValue(xIdx - 1, yIdx, slice.getPixelValue(yIdx, z)); // ※SAGITTAL上のZは引数のz
					}
				}
			} else if (willCreatePlane == CutSurface.CORONAL) {
				// コロナル抽出: 固定Y = x
				extractedIp = new ij.process.FloatProcessor(d, h);
				for (int xIdx = 1; xIdx <= d; xIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(xIdx);
					for (int zIdx = 0; zIdx < h; zIdx++) {
						extractedIp.putPixelValue(xIdx - 1, zIdx, slice.getPixelValue(x, zIdx));
					}
				}
			}
		} else if (basePlane == CutSurface.CORONAL) {
			if (willCreatePlane == CutSurface.AXIAL) {
				// アキシャル抽出: 固定Z = z
				extractedIp = new ij.process.FloatProcessor(w, d);
				for (int yIdx = 1; yIdx <= d; yIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(yIdx);
					for (int xIdx = 0; xIdx < w; xIdx++) {
						extractedIp.putPixelValue(xIdx, yIdx - 1, slice.getPixelValue(xIdx, z));
					}
				}
			} else if (willCreatePlane == CutSurface.SAGITTAL) {
				// サジタル抽出: 固定X = x
				extractedIp = new ij.process.FloatProcessor(d, h);
				for (int yIdx = 1; yIdx <= d; yIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(yIdx);
					for (int zIdx = 0; zIdx < h; zIdx++) {
						extractedIp.putPixelValue(yIdx - 1, zIdx, slice.getPixelValue(x, zIdx));
					}
				}
			}
		}

		// 3. 抽出した面をImageJ標準機能でリサイズしてセット
		if (extractedIp != null) {
			extractedIp.setInterpolationMethod(ij.process.ImageProcessor.BILINEAR);
			ij.process.ImageProcessor resizedIp = extractedIp.resize(targetW, targetH);
			ImagePlus ortho = new ImagePlus("orthogonal", resizedIp);
			copyImageMetaInformation(baseVolume, ortho, willCreatePlane, px, py, pz);
			return ortho;
		}
		return targetBlank;
	}
	
	
	private void copyImageMetaInformation(ImagePlus base, ImagePlus recon, CutSurface reconPlane, double px, double py,
			double pz) {

		String pid = GDicomTools.getTag(base, "0010,0020");
		String studyUid = GDicomTools.getTag(base, "0020,000D");
		String seriesUid = UIDUtils.createUID();
		String sopUid = UIDUtils.createUID();

		// 1. Base画像のベクトル情報を取得
		double[] baseIop = GDicomTools.getImageOrientationPatient(base, 1);
		org.joml.Vector3d baseRow = new org.joml.Vector3d(baseIop[0], baseIop[1], baseIop[2]);
		org.joml.Vector3d baseCol = new org.joml.Vector3d(baseIop[3], baseIop[4], baseIop[5]);

		// 2. Base画像のスライス進行方向（Stack Direction）ベクトルを計算
		double[] ipp1Arr = GDicomTools.getImagePositionPatient(base, 1);
		org.joml.Vector3d ipp1 = new org.joml.Vector3d(ipp1Arr[0], ipp1Arr[1], ipp1Arr[2]);

		org.joml.Vector3d stackDir = new org.joml.Vector3d();

		if (base.getNSlices() > 1) {
			// 複数スライスがある場合は、1枚目と2枚目のIPPの差分から実際の進行方向を算出
			double[] ipp2Arr = GDicomTools.getImagePositionPatient(base, 2);
			org.joml.Vector3d ipp2 = new org.joml.Vector3d(ipp2Arr[0], ipp2Arr[1], ipp2Arr[2]);
			stackDir.set(ipp2).sub(ipp1).normalize();
		} else {
			// スライスが1枚しかない場合は、RowとColの外積（Cross Product）から法線ベクトルを算出
			// jomlのcrossメソッドを使用して baseRow × baseCol を計算し、stackDirに格納します
			baseRow.cross(baseCol, stackDir/*dest*/).normalize();
		}

		org.joml.Vector3d ipp = new org.joml.Vector3d();
		double[] iop = new double[6];

		/*
		 * BaseがAxialの場合の再構成メタデータ計算
		 */
		if (reconPlane == CutSurface.AXIAL) {
			// Axial -> Axial (複製)
			ipp.set(ipp1);
			iop = baseIop.clone();
		} else if (reconPlane == CutSurface.SAGITTAL) {
			// SagittalのRowはBaseのCol(A-P)、ColはBaseのStack(S-I)
			iop[0] = baseCol.x;
			iop[1] = baseCol.y;
			iop[2] = baseCol.z;
			iop[3] = stackDir.x;
			iop[4] = stackDir.y;
			iop[5] = stackDir.z;

			// Sagittalの左上座標 = IPP1から、X軸方向にcurrentXピクセル分進んだ位置
			ipp.set(baseRow).mul(currentX * px).add(ipp1);

		} else if (reconPlane == CutSurface.CORONAL) {
			// CoronalのRowはBaseのRow(R-L)、ColはBaseのStack(S-I)
			iop[0] = baseRow.x;
			iop[1] = baseRow.y;
			iop[2] = baseRow.z;
			iop[3] = stackDir.x;
			iop[4] = stackDir.y;
			iop[5] = stackDir.z;

			// Coronalの左上座標 = IPP1から、Y軸方向にcurrentYピクセル分進んだ位置
			ipp.set(baseCol).mul(currentY * py).add(ipp1);
		}

		// 値のセット
		GDicomTools.setTag(recon, 1/* always 1 */, "0010,0020", pid);
		GDicomTools.setTag(recon, 1/* always 1 */, "0020,000D", studyUid);
		GDicomTools.setTag(recon, 1/* always 1 */, "0020,000E", seriesUid);
		GDicomTools.setTag(recon, 1/* always 1 */, "0008,0018", sopUid);

		// IPP
		GDicomTools.setDoubles(recon, 1, "0020,0032", new double[] { ipp.x, ipp.y, ipp.z });
		// IOP
		GDicomTools.setDoubles(recon, 1, "0020,0037", iop);

		// Pixel Spacing
		GDicomTools.setDoubles(recon, 1, "0028,0030", new double[] { py, px }); // {Row spacing, Col spacing}
		// Slice Thickness (抽出された面における奥行き)
		GDicomTools.setTag(recon, 1, "0018,0050", String.valueOf(pz));
		
		Calibration reconCal = base.getCalibration().copy();
		reconCal.pixelWidth = px;
		reconCal.pixelHeight = py;
		reconCal.pixelDepth = pz;
		recon.setCalibration(reconCal);
	}
    
    private CutSurface determineBasePlane(ImagePlus imp) {
        CutSurface plane = PlanarSupport.planarOf(imp);
        return plane; 
    }
    
    private void buildGUI() {
        // メニューの構築
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export DICOM Series...");
        exportItem.addActionListener(e -> showExportDialog());
        fileMenu.add(exportItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // 2x2のパネルレイアウト
        JPanel mainPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        mainPanel.setBackground(Color.BLACK);

        // 各キャンバスの生成
        /**
         * sourceの断面に合わせること　TODO
         */
        axialCanvas = new Praparat(baseVolume, Color.BLUE, ViewMode.MPR);
        sagittalCanvas = new Praparat(sagittalImp, Color.GREEN, ViewMode.MPR);
        coronalCanvas = new Praparat(coronalImp, Color.RED, ViewMode.MPR);
        
        axialCanvas.setShowCrossLineMode(true);
        sagittalCanvas.setShowCrossLineMode(true);
        coronalCanvas.setShowCrossLineMode(true);

        // キャンバスを保持するパネル（中央寄せなどレイアウト調整用）
        mainPanel.add(wrapCanvas(axialCanvas, "Axial", Color.BLUE));       // 左上
        mainPanel.add(wrapCanvas(sagittalCanvas, "Sagittal", Color.GREEN)); // 右上
        mainPanel.add(wrapCanvas(coronalCanvas, "Coronal", Color.RED));   // 左下
        
        // 右下はスペーサー
        JPanel spacer = new JPanel();
        spacer.setBackground(Color.DARK_GRAY);
        mainPanel.add(spacer);

        add(mainPanel, BorderLayout.CENTER);
    }

    // キャンバスをJPanelでラップするユーティリティ
    private JPanel wrapCanvas(JPanel canvas, String title, Color color) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.BLACK);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(color), title, 0, 0, null, Color.WHITE));
        panel.add(canvas, BorderLayout.CENTER);
        return panel;
    }
    
    
	private void setupMouseListeners() {
		// ドラッグだけでなくクリック(Press)にも対応するため MouseAdapter を使用
		java.awt.event.MouseAdapter mouseHandler = new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				handleMouse(e);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				handleMouse(e);
			}

			private void handleMouse(MouseEvent e) {
				Object source = e.getSource();
				if(source instanceof EventGlass) {
					EventGlass eg = (EventGlass)source;
					SlideGlass sg = (SlideGlass)eg.getParent();
					Praparat pp = sg.getPraparat();
					if (pp == axialCanvas) {
						try {
							Point p = axialCanvas.getCurrentSlide().offScreenCoordinate(e.getX(), e.getY());
							// AxialでのZ座標は、現在表示されているスライス位置から取得（0ベース）
							int z = axialCanvas.getCurrentSlidePos();
							updateCrosshairs(p.x, p.y, z);
						} catch (NoninvertibleTransformException e1) {
							e1.printStackTrace();
						}
					}
					// ※ サジタル・コロナル側のドラッグ対応は、アキシャルが完成した後に実装します
				}
				
			}
		};

		axialCanvas.addMouseListener(mouseHandler);
		axialCanvas.addMouseMotionListener(mouseHandler);
		sagittalCanvas.addMouseListener(mouseHandler);
		sagittalCanvas.addMouseMotionListener(mouseHandler);
		coronalCanvas.addMouseListener(mouseHandler);
		coronalCanvas.addMouseMotionListener(mouseHandler);
	}
    
    /**
     * 座標系（X, Y, Z）を更新し、リスライスとクロスライン描画を実行します。
     * @param x : アキシャル上のX座標
     * @param y : アキシャル上のY座標
     * @param z : アキシャル上のZ座標（スライス位置）0-base
     */
    private void updateCrosshairs(int x, int y, int z) {
        // 画像範囲外のクリックを弾く
        if (x < 0 || x > baseVolume.getWidth() || y < 0 || y > baseVolume.getHeight() || z < 0 || z > baseVolume.getNSlices()) {
            return;
        }

        this.currentX = x;
        this.currentY = y;
        this.currentZ = z;

        // 1. サジタルとコロナルのリスライス処理（Orthogonal再構成）
        reconstructOrthogonalPlanes();

        // 2. 各パネルにクロスライン（十字線）を描画
        updateCrosslineDisplay();
    }

	/**
	 * カレント座標に基づいてサジタル、コロナルの断面画像を再抽出して画面を更新します。
	 */
	private void reconstructOrthogonalPlanes() {
		/*
		 * 一旦、参照画像（baseStack）がアキシャルとして考えます。
		 */

		// サジタル画像の再抽出と更新（currentX の位置でスライス）
		ImagePlus newSagittal = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
		sagittalImp.setProcessor(newSagittal.getProcessor());
		sagittalCanvas.reloadSlideGlasses(sagittalImp); // Praparatの表示を更新

		// コロナル画像の再抽出と更新（currentY の位置でスライス）
		ImagePlus newCoronal = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
		coronalImp.setProcessor(newCoronal.getProcessor());
		coronalCanvas.reloadSlideGlasses(coronalImp); // Praparatの表示を更新

		// add coronal sagittal basis...
	}

    /**
     * 3つのPraparatにクロスライン（十字線）を描画します（概念コード）。
     */
    private void updateCrosslineDisplay() {
        Calibration cal = baseVolume.getCalibration();
        double px = cal.pixelWidth;
        double py = cal.pixelHeight;
        double pz = cal.pixelDepth;

        // サジタルとコロナルの表示上（2D）での、スライス位置(Z)をスケール補正して計算
        int displayZ_forSagittal = (int) Math.round((currentZ - 1) * (pz / py));
        int displayZ_forCoronal = (int) Math.round((currentZ - 1) * (pz / px));

        // 1. アキシャル上のクロスライン位置（X: currentX, Y: currentY）
        drawCrosshairOnPraparat(axialCanvas, currentX, currentY);

        // 2. サジタル上のクロスライン位置（横軸: currentY, 縦軸: Z）
        drawCrosshairOnPraparat(sagittalCanvas, currentY, displayZ_forSagittal);

        // 3. コロナル上のクロスライン位置（横軸: currentX, 縦軸: Z）
        drawCrosshairOnPraparat(coronalCanvas, currentX, displayZ_forCoronal);
    }

    /**
     * Praparat の CanvasGlass レイヤー等を用いてクロスラインを描画する概念メソッドです。
     * お持ちの Praparat クラスの仕様に合わせて実装してください。
     */
    private void drawCrosshairOnPraparat(Praparat prap, int x, int y) {
        if (prap == null || prap.getCurrentSlide() == null) return;
        
        // ※ 以前のコード情報からの推測です。
        // PraparatのROI CanvasLayerにアクセスして、Pointを渡すことで
        // 十字線を描画するような仕組みがある場合は以下のように呼び出します。
        
        SlideGlass sg = prap.getCurrentSlide();
        CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
        if (cg != null) {
            cg.createCross(x,y); 
            cg.repaint();
        }
        
        // もし専用のメソッドがない場合は、PraparatのpaintComponentを
        // オーバーライドするか、GlassPaneを利用してラインを引く必要があります。
    }

    
    // スケールされたZ座標を参照スタックのZ座標に戻す
    private int unscaleZ(CutSurface plane, int scaledZ) {
    	if(plane == basePlane) {
    		
    	}
        return scaledZ; // 実際の縮尺比率で割り戻す計算を入れます
    }
    
    private void showExportDialog() {
        // ダイアログ用のUIコンポーネント
        JComboBox<CutSurface> planeComboBox = new JComboBox<>(CutSurface.values());
        JTextField thicknessField = new JTextField("3.0"); // デフォルト mm

        Object[] message = {
            "Select Output Plane:", planeComboBox,
            "Slice Thickness (mm):", thicknessField
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Export DICOM Series", JOptionPane.OK_CANCEL_OPTION);
        
        if (option == JOptionPane.OK_OPTION) {
            CutSurface selectedPlane = (CutSurface) planeComboBox.getSelectedItem();
            double thickness = 0.0;
            try {
                thickness = Double.parseDouble(thicknessField.getText());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid thickness value.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 実行時の概念的メソッドを呼び出す
            exportDicomSeries(selectedPlane, thickness);
        }
    }

    /**
     * 指定された断面とスライス厚で、LPS空間からリサンプリングし、
     * DICOMシリーズとして保存する（概念的メソッド）
     */
    private void exportDicomSeries(CutSurface targetPlane, double sliceThickness) {
        System.out.println("Exporting " + targetPlane + " series with thickness " + sliceThickness + "mm...");
        
        // 1. 指定された断面(targetPlane)の法線ベクトルをLPS空間上で計算
        // 2. 元のボリュームのバウンディングボックスと法線ベクトルから、必要なスライス枚数を計算
       // 3. sliceThickness（スライス間隔）ごとにサンプリング平面を移動
        // 4. 各平面でImageProcessorを生成（ピクセル値の3D補間）
        // 5. 各スライスに対して、新しいImagePositionPatientなどを計算してDICOMタグを付与
        // 6. 指定フォルダに.dcmファイルとして保存
        
        JOptionPane.showMessageDialog(this, "Export complete! (Conceptual)", "Success", JOptionPane.INFORMATION_MESSAGE);
    }
    
	private ImagePlus blankImage(CutSurface surface) {
		// 元スタックのサイズとピクセル解像度を取得
		Calibration cal = baseVolume.getCalibration();
		double pw = cal.pixelWidth;
		double ph = cal.pixelHeight;
		double pd = cal.pixelDepth; // スライス厚（またはスライス間隔）

		int w = baseVolume.getWidth();
		int h = baseVolume.getHeight();
		int d = baseVolume.getNSlices();

		int targetWidth = 0;
		int targetHeight = 0;

		if (basePlane == CutSurface.AXIAL) {
			// Base: 幅=X, 高さ=Y, スライス=Z
			if (surface == CutSurface.AXIAL) {
				targetWidth = w;
				targetHeight = h;
			} else if (surface == CutSurface.SAGITTAL) {
				targetWidth = h; // サジタルの幅はY
				targetHeight = (int) Math.round(d * (pd / ph)); // 高さはZ（Yの解像度に合わせてスケール）
			} else if (surface == CutSurface.CORONAL) {
				targetWidth = w; // コロナルの幅はX
				targetHeight = (int) Math.round(d * (pd / pw)); // 高さはZ（Xの解像度に合わせてスケール）
			}
		} else if (basePlane == CutSurface.SAGITTAL) {
			// Base: 幅=Y, 高さ=Z, スライス=X
			if (surface == CutSurface.AXIAL) {
				targetWidth = d; // アキシャルの幅はX
				targetHeight = (int) Math.round(w * (pw / pd)); // 高さはY
			} else if (surface == CutSurface.SAGITTAL) {
				targetWidth = w;
				targetHeight = h;
			} else if (surface == CutSurface.CORONAL) {
				targetWidth = d; // コロナルの幅はX
				targetHeight = (int) Math.round(h * (ph / pd)); // 高さはZ
			}
		} else if (basePlane == CutSurface.CORONAL) {
			// Base: 幅=X, 高さ=Z, スライス=Y
			if (surface == CutSurface.AXIAL) {
				targetWidth = w; // アキシャルの幅はX
				targetHeight = (int) Math.round(d * (pd / pw)); // 高さはY
			} else if (surface == CutSurface.SAGITTAL) {
				targetWidth = d; // サジタルの幅はY
				targetHeight = (int) Math.round(h * (ph / pd)); // 高さはZ
			} else if (surface == CutSurface.CORONAL) {
				targetWidth = w;
				targetHeight = h;
			}
		}

		// 算出されたサイズで黒塗りのFloatプロセッサを生成
		if (targetWidth > 0 && targetHeight > 0) {
			return new ImagePlus(surface.name() + "_blank", ij.gui.NewImage
					.createFloatImage("", targetWidth, targetHeight, 1, ij.gui.NewImage.FILL_BLACK).getProcessor());
		}

		return null;
	}

}
