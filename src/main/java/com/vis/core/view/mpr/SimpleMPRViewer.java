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
/* ***** BEGIN LICENSE BLOCK ***** * Version: MPL 1.1/GPL 2.0/LGPL 2.1 
 * * (ライセンスヘッダは省略せずそのまま使用してください)
 * * ***** END LICENSE BLOCK ***** */
package com.vis.core.view.mpr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

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

/**
 * * * @author tatsunidas
 */
public class SimpleMPRViewer extends JFrame {

	// debug
	@SuppressWarnings("unused")
	public static void main(String[] arags) {
		String ax_path = "/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151";
		String sag_path = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
//		ImagePlus imp = FolderOpener.open(ax_path);
		ImagePlus imp = FolderOpener.open(sag_path);
		new SimpleMPRViewer(imp);
	}

	private static final long serialVersionUID = 1L;

	// 画像とキャンバスの保持
	private ImagePlus axialImp, sagittalImp, coronalImp;
	private Praparat axialCanvas, sagittalCanvas, coronalCanvas;

	// 現在の交点
	/**
	 * baseVolumeがどの向きであろうと「その元画像の 横(X), 縦(Y), スライス(Z)」を意味します。
	 * 1. baseVolume が Axial の場合
	 * currentX = axX ＝ 絶対X（左右）
	 * currentY = axY ＝ 絶対Y（前後）
	 * currentZ = axZ ＝ 絶対Z（頭足）(すべてが一致するため、一番考えやすい状態です)
	 * 2. baseVolume が Sagittal の場合
	 * currentX = sagX ＝ 絶対Y（前後）
	 * currentY = sagY ＝ 絶対Z（頭足）
	 * currentZ = sagZ ＝ 絶対X（左右）
	 * 3. baseVolume が Coronal の場合
	 * currentX = corX ＝ 絶対X（左右）
	 * currentY = corY ＝ 絶対Z（頭足）
	 * currentZ = corZ ＝ 絶対Y（前後）
	 */
	private int currentX, currentY, currentZ;

	// 元となるベーススタック
	private ImagePlus baseVolume;
	private CutSurface basePlane;
	
	private java.awt.event.MouseAdapter sharedMouseHandler;

	public SimpleMPRViewer(ImagePlus inputImage) {
		this.baseVolume = inputImage;
		if(baseVolume == null || baseVolume.getNSlices() == 0) {
			throw new IllegalArgumentException("Invalid image stack input...");
		}
		System.out.println(baseVolume.getNSlices() + " were loaded.");
		
		Calibration cal = baseVolume.getCalibration();
		cal.pixelDepth = GDicomTools.getVoxelDepth(baseVolume);
		
		setTitle("Simple MPR Viewer");
		setSize(1000, 1000);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		init();
	}

	private void init() {
		this.basePlane = determineBasePlane(baseVolume);

		standardizeStackOrientation();

		// 1. 各断面のImagePlusを生成（ボクセルサイズを考慮したマトリクス生成）
		reconstructCenterOrthogonal();

		// 2. GUIの構築（メニューと4パネル）
		buildGUI();

		// 3. マウスイベントの登録
		setupMouseListeners();

		setVisible(true);
	}

	/**
	 * * show starting-up orthogonals
	 */
	private void reconstructCenterOrthogonal() {
		// 中心座標の初期化（baseVolumeのピクセルベース）
		currentX = baseVolume.getWidth() / 2;
		currentY = baseVolume.getHeight() / 2;
		currentZ = baseVolume.getNSlices() / 2;

		// basePlaneに応じて、どれを元画像とし、どれを再構成するか決定する
		if (basePlane == CutSurface.AXIAL) {
			this.axialImp = baseVolume;
			this.sagittalImp = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
			this.coronalImp = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
		} else if (basePlane == CutSurface.SAGITTAL) {
			this.axialImp = extractPlane(CutSurface.AXIAL, currentX, currentY, currentZ);
			this.sagittalImp = baseVolume;
			this.coronalImp = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
		} else if (basePlane == CutSurface.CORONAL) {
			this.axialImp = extractPlane(CutSurface.AXIAL, currentX, currentY, currentZ);
			this.sagittalImp = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
			this.coronalImp = baseVolume;
		}
	}

	/**
	 * 
	 * @param willCreatePlane
	 * @param x : baseVolume voxel indexX
	 * @param y : baseVolume voxel indexY
	 * @param z : baseVolume voxel indexZ
	 * @return
	 */
	private ImagePlus extractPlane(CutSurface willCreatePlane, int x, int y, int z) {
        if (willCreatePlane == basePlane) {
            ij.ImageStack stack = baseVolume.getStack();
            int sliceIndex = Math.max(1, Math.min(z + 1, stack.getSize())); // 1-based index
            return new ImagePlus(willCreatePlane.name(), stack.getProcessor(sliceIndex).duplicate());
        }

        ImagePlus targetBlank = blankImage(willCreatePlane);
        if (targetBlank == null) return null;

        int targetW = targetBlank.getWidth();
        int targetH = targetBlank.getHeight();

        int w = baseVolume.getWidth();
        int h = baseVolume.getHeight();
        ij.ImageStack stack = baseVolume.getStack();
        int d = stack.getSize();

        double px = 0.0, py = 0.0, pz = 0.0;
        Calibration baseCal = baseVolume.getCalibration();

        ij.process.ImageProcessor extractedIp = null;

        if (basePlane == CutSurface.AXIAL) {
            if (willCreatePlane == CutSurface.SAGITTAL) {
                extractedIp = new ij.process.FloatProcessor(h, d);
                for (int zIdx = 1; zIdx <= d; zIdx++) {
                    ij.process.ImageProcessor slice = stack.getProcessor(zIdx);
                    for (int yIdx = 0; yIdx < h; yIdx++) {
                        extractedIp.putPixelValue(yIdx, zIdx - 1, slice.getPixelValue(x, yIdx));
                    }
                }
                px = baseCal.pixelHeight; py = (baseCal.pixelDepth * d) / targetH; pz = baseCal.pixelWidth;
            } else if (willCreatePlane == CutSurface.CORONAL) {
                extractedIp = new ij.process.FloatProcessor(w, d);
                for (int zIdx = 1; zIdx <= d; zIdx++) {
                    ij.process.ImageProcessor slice = stack.getProcessor(zIdx);
                    for (int xIdx = 0; xIdx < w; xIdx++) {
                        extractedIp.putPixelValue(xIdx, zIdx - 1, slice.getPixelValue(xIdx, y));
                    }
                }
                px = baseCal.pixelWidth; py = (baseCal.pixelDepth * d) / targetH; pz = baseCal.pixelHeight;
            }
        } else if (basePlane == CutSurface.SAGITTAL) {
            if (willCreatePlane == CutSurface.AXIAL) {
                extractedIp = new ij.process.FloatProcessor(d, w);
                for (int xIdx = 1; xIdx <= d; xIdx++) {
                    ij.process.ImageProcessor slice = stack.getProcessor(xIdx);
                    for (int yIdx = 0; yIdx < w; yIdx++) {
                        // SagittalベースでAxialを抽出: 固定するのは解剖学的Z (高さ=y)
                        extractedIp.putPixelValue(xIdx - 1, yIdx, slice.getPixelValue(yIdx, y));
                    }
                }
                px = baseCal.pixelDepth; py = baseCal.pixelWidth; pz = baseCal.pixelHeight;
            } else if (willCreatePlane == CutSurface.CORONAL) {
                extractedIp = new ij.process.FloatProcessor(d, h);
                for (int xIdx = 1; xIdx <= d; xIdx++) {
                    ij.process.ImageProcessor slice = stack.getProcessor(xIdx);
                    for (int zIdx = 0; zIdx < h; zIdx++) {
                        // SagittalベースでCoronalを抽出: 固定するのは解剖学的Y (幅=x)
                        extractedIp.putPixelValue(xIdx - 1, zIdx, slice.getPixelValue(x, zIdx));
                    }
                }
                px = baseCal.pixelDepth; py = baseCal.pixelHeight; pz = baseCal.pixelWidth;
            }
        } else if (basePlane == CutSurface.CORONAL) {
            if (willCreatePlane == CutSurface.AXIAL) {
                extractedIp = new ij.process.FloatProcessor(w, d);
                for (int yIdx = 1; yIdx <= d; yIdx++) {
                    ij.process.ImageProcessor slice = stack.getProcessor(yIdx);
                    for (int xIdx = 0; xIdx < w; xIdx++) {
                        // CoronalベースでAxialを抽出: 固定するのは解剖学的Z (高さ=y)
                        extractedIp.putPixelValue(xIdx, yIdx - 1, slice.getPixelValue(xIdx, y));
                    }
                }
                px = baseCal.pixelWidth; py = baseCal.pixelDepth; pz = baseCal.pixelHeight;
            } else if (willCreatePlane == CutSurface.SAGITTAL) {
                extractedIp = new ij.process.FloatProcessor(d, h);
                for (int yIdx = 1; yIdx <= d; yIdx++) {
                    ij.process.ImageProcessor slice = stack.getProcessor(yIdx);
                    for (int zIdx = 0; zIdx < h; zIdx++) {
                        // CoronalベースでSagittalを抽出: 固定するのは解剖学的X (幅=x)
                        extractedIp.putPixelValue(yIdx - 1, zIdx, slice.getPixelValue(x, zIdx));
                    }
                }
                px = baseCal.pixelDepth; py = baseCal.pixelHeight; pz = baseCal.pixelWidth;
            }
        }

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

		double[] baseIop = GDicomTools.getImageOrientationPatient(base, 1);
		org.joml.Vector3d baseRow = new org.joml.Vector3d(baseIop[0], baseIop[1], baseIop[2]);
		org.joml.Vector3d baseCol = new org.joml.Vector3d(baseIop[3], baseIop[4], baseIop[5]);

		double[] ipp1Arr = GDicomTools.getImagePositionPatient(base, 1);
		org.joml.Vector3d ipp1 = new org.joml.Vector3d(ipp1Arr[0], ipp1Arr[1], ipp1Arr[2]);
		org.joml.Vector3d stackDir = new org.joml.Vector3d();

		if (base.getNSlices() > 1) {
			double[] ipp2Arr = GDicomTools.getImagePositionPatient(base, 2);
			org.joml.Vector3d ipp2 = new org.joml.Vector3d(ipp2Arr[0], ipp2Arr[1], ipp2Arr[2]);
			stackDir.set(ipp2).sub(ipp1).normalize();
		} else {
			baseRow.cross(baseCol, stackDir).normalize();
		}

		org.joml.Vector3d ipp = new org.joml.Vector3d();
		double[] iop = new double[6];
		Calibration baseCal = base.getCalibration();

		// 9パターンのメタデータマッピング
		if (basePlane == CutSurface.AXIAL) {
			if (reconPlane == CutSurface.AXIAL) {
				ipp.set(ipp1);
				iop = baseIop.clone();
			} else if (reconPlane == CutSurface.SAGITTAL) {
				iop[0] = baseCol.x;
				iop[1] = baseCol.y;
				iop[2] = baseCol.z;
				iop[3] = stackDir.x;
				iop[4] = stackDir.y;
				iop[5] = stackDir.z;
				ipp.set(baseRow).mul(currentX * baseCal.pixelWidth).add(ipp1);
			} else if (reconPlane == CutSurface.CORONAL) {
				iop[0] = baseRow.x;
				iop[1] = baseRow.y;
				iop[2] = baseRow.z;
				iop[3] = stackDir.x;
				iop[4] = stackDir.y;
				iop[5] = stackDir.z;
				ipp.set(baseCol).mul(currentY * baseCal.pixelHeight).add(ipp1);
			}
		} else if (basePlane == CutSurface.SAGITTAL) {
			if (reconPlane == CutSurface.SAGITTAL) {
				ipp.set(ipp1);
				iop = baseIop.clone();
			} else if (reconPlane == CutSurface.AXIAL) {
				iop[0] = stackDir.x;
				iop[1] = stackDir.y;
				iop[2] = stackDir.z;
				iop[3] = baseRow.x;
				iop[4] = baseRow.y;
				iop[5] = baseRow.z;
				ipp.set(baseCol).mul(currentY * baseCal.pixelHeight).add(ipp1);
			} else if (reconPlane == CutSurface.CORONAL) {
				iop[0] = stackDir.x;
				iop[1] = stackDir.y;
				iop[2] = stackDir.z;
				iop[3] = baseCol.x;
				iop[4] = baseCol.y;
				iop[5] = baseCol.z;
				ipp.set(baseRow).mul(currentX * baseCal.pixelWidth).add(ipp1);
			}
		} else if (basePlane == CutSurface.CORONAL) {
			if (reconPlane == CutSurface.CORONAL) {
				ipp.set(ipp1);
				iop = baseIop.clone();
			} else if (reconPlane == CutSurface.AXIAL) {
				iop[0] = baseRow.x;
				iop[1] = baseRow.y;
				iop[2] = baseRow.z;
				iop[3] = stackDir.x;
				iop[4] = stackDir.y;
				iop[5] = stackDir.z;
				ipp.set(baseCol).mul(currentY * baseCal.pixelHeight).add(ipp1);
			} else if (reconPlane == CutSurface.SAGITTAL) {
				iop[0] = stackDir.x;
				iop[1] = stackDir.y;
				iop[2] = stackDir.z;
				iop[3] = baseCol.x;
				iop[4] = baseCol.y;
				iop[5] = baseCol.z;
				ipp.set(baseRow).mul(currentX * baseCal.pixelWidth).add(ipp1);
			}
		}

		GDicomTools.setTag(recon, 1, "0010,0020", pid);
		GDicomTools.setTag(recon, 1, "0020,000D", studyUid);
		GDicomTools.setTag(recon, 1, "0020,000E", seriesUid);
		GDicomTools.setTag(recon, 1, "0008,0018", sopUid);
		GDicomTools.setDoubles(recon, 1, "0020,0032", new double[] { ipp.x, ipp.y, ipp.z });
		GDicomTools.setDoubles(recon, 1, "0020,0037", iop);
		GDicomTools.setDoubles(recon, 1, "0028,0030", new double[] { py, px });
		GDicomTools.setTag(recon, 1, "0018,0050", String.valueOf(pz));

		Calibration reconCal = base.getCalibration().copy();
		reconCal.pixelWidth = px;
		reconCal.pixelHeight = py;
		reconCal.pixelDepth = pz;
		recon.setCalibration(reconCal);
	}

	private CutSurface determineBasePlane(ImagePlus imp) {
		return PlanarSupport.planarOf(imp);
	}

	private void buildGUI() {
		/*
		 * Menu bar is Future work.
		 */
//		JMenuBar menuBar = new JMenuBar();
//		JMenu fileMenu = new JMenu("File");
//		JMenuItem exportItem = new JMenuItem("Export DICOM Series...");
//		exportItem.addActionListener(e -> showExportDialog());
//		fileMenu.add(exportItem);
//		menuBar.add(fileMenu);
//		setJMenuBar(menuBar);

		JPanel mainPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		mainPanel.setBackground(Color.BLACK);

		axialCanvas = new Praparat(axialImp, Color.BLUE, ViewMode.MPR);
		sagittalCanvas = new Praparat(sagittalImp, Color.GREEN, ViewMode.MPR);
		coronalCanvas = new Praparat(coronalImp, Color.RED, ViewMode.MPR);

		axialCanvas.setShowCrossLineMode(true);
		sagittalCanvas.setShowCrossLineMode(true);
		coronalCanvas.setShowCrossLineMode(true);

		// スライスの初期表示を合わせる
		if (basePlane == CutSurface.AXIAL) {
			axialCanvas.setImagePositionUsingSlider(currentZ);
		} else if (basePlane == CutSurface.SAGITTAL) {
			sagittalCanvas.setImagePositionUsingSlider(currentZ);
		} else if (basePlane == CutSurface.CORONAL) {
			coronalCanvas.setImagePositionUsingSlider(currentZ);
		}

		mainPanel.add(wrapCanvas(axialCanvas, "Axial", Color.BLUE));
		mainPanel.add(wrapCanvas(sagittalCanvas, "Sagittal", Color.GREEN));
		mainPanel.add(wrapCanvas(coronalCanvas, "Coronal", Color.RED));

		JPanel spacer = new JPanel();
		spacer.setBackground(Color.DARK_GRAY);
		mainPanel.add(spacer);

		add(mainPanel, BorderLayout.CENTER);
	}

	private JPanel wrapCanvas(JPanel canvas, String title, Color color) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(Color.BLACK);
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(color), title, 0, 0, null,
				Color.WHITE));
		panel.add(canvas, BorderLayout.CENTER);
		return panel;
	}

	private java.awt.event.MouseAdapter buildMouseListeners() {
		java.awt.event.MouseAdapter mouseHandler = new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!SwingUtilities.isRightMouseButton(e)) {
					return;
				}
				handleMouse(e);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (!SwingUtilities.isRightMouseButton(e)) {
					return;
				}
				handleMouse(e);
			}

			private void handleMouse(MouseEvent e) {
				Object source = e.getSource();
				if (source instanceof EventGlass) {
					EventGlass eg = (EventGlass) source;
					SlideGlass sg = (SlideGlass) eg.getParent();
					Praparat pp = sg.getPraparat();

					try {
						Point p = pp.getCurrentSlide().offScreenCoordinate(e.getX(), e.getY());
						int cx = p.x;
						int cy = p.y;
						int cz = pp.getCurrentSlidePos();
						/*
						 * newX,newY,newZは、baseVolumeでのピクセル座標
						 */
						int newX = currentX, newY = currentY, newZ = currentZ;
						int d = baseVolume.getNSlices();

						if (pp == getBaseCanvas()) {
							// ベースキャンバスは複数スライスを持つため、cz が奥行きとして使える
							newX = cx;
							newY = cy;
							newZ = cz;
						} else {
							// 再構成キャンバスの場合、czは常に0なので使わず、見えない軸は現在値を維持する
							if (basePlane == CutSurface.AXIAL) {
								if (pp == sagittalCanvas) {
									newY = cx;
									newZ = unscaleZForDisplay(cy, d, sagittalImp.getHeight());
									newX = currentX; // Sagittalでは左右(X)が見えないので維持
								} else if (pp == coronalCanvas) {
									newX = cx;
									newZ = unscaleZForDisplay(cy, d, coronalImp.getHeight());
									newY = currentY; // Coronalでは前後(Y)が見えないので維持
								}
							} else if (basePlane == CutSurface.CORONAL) {
								// CORONAL Base: X=左右, Y=上下(Z), Z=前後(スライス)
								if (pp == axialCanvas) {
									newX = cx;
									newZ = unscaleZForDisplay(cy, d, axialImp.getHeight());
									newY = currentY; // Axialでは上下(Y)が見えないので維持
								} else if (pp == sagittalCanvas) {
									newZ = unscaleZForDisplay(cx, d, sagittalImp.getWidth());
									newY = cy;
									newX = currentX; // Sagittalでは左右(X)が見えないので維持
								}
							} else if (basePlane == CutSurface.SAGITTAL) {
								// SAGITTAL Base: X=前後, Y=上下(Z), Z=左右(スライス)
								if (pp == axialCanvas) {
									newZ = unscaleZForDisplay(cx, d, axialImp.getWidth());
									newX = cy;
									newY = currentY; // Axialでは上下(Y)が見えないので維持
								} else if (pp == coronalCanvas) {
									newZ = unscaleZForDisplay(cx, d, coronalImp.getWidth());
									newY = cy;
									newX = currentX; // Coronalでは前後(X)が見えないので維持
								}
							}
						}
						updateCrosshairs(newX, newY, newZ, pp);
					} catch (Exception e1) {
						// ドラッグ中のコンポーネント破棄エラー回避
					}
				}
			}
		};
		return mouseHandler;
	}
	
	private void setupMouseListeners() {
		
		this.sharedMouseHandler = buildMouseListeners();

		axialCanvas.addMouseListener(sharedMouseHandler);
		axialCanvas.addMouseMotionListener(sharedMouseHandler);
		sagittalCanvas.addMouseListener(sharedMouseHandler);
		sagittalCanvas.addMouseMotionListener(sharedMouseHandler);
		coronalCanvas.addMouseListener(sharedMouseHandler);
		coronalCanvas.addMouseMotionListener(sharedMouseHandler);
	}

	private Praparat getBaseCanvas() {
		if (basePlane == CutSurface.AXIAL)
			return axialCanvas;
		if (basePlane == CutSurface.SAGITTAL)
			return sagittalCanvas;
		return coronalCanvas;
	}

	private void updateCrosshairs(int x, int y, int z, Praparat activeCanvas) {
		if (x < 0 || x > baseVolume.getWidth() || y < 0 || y > baseVolume.getHeight() || z < 0
				|| z >= baseVolume.getNSlices()) {
			return;
		}

		/*
		 * DICOM RCS coordinates.
		 * NOT baseVolume coords.
		 */
		this.currentX = x;
		this.currentY = y;
		this.currentZ = z;

		reconstructOrthogonalPlanes(activeCanvas);
		updateCrosslineDisplay();
	}
	
	private void reconstructOrthogonalPlanes(Praparat activeCanvas) {
        // ★ basePlane（元画像）以外の面について、自分が操作中(activeCanvas)でなければ再構築・リロードする
		if (basePlane != CutSurface.AXIAL && activeCanvas != axialCanvas) {
			ImagePlus newAxial = extractPlane(CutSurface.AXIAL, currentX, currentY, currentZ);
			axialImp.setProcessor(newAxial.getProcessor());
			axialCanvas.reloadSlideGlasses(axialImp);
			// リロードで消えたリスナーを再登録
			axialCanvas.addMouseListener(sharedMouseHandler);
			axialCanvas.addMouseMotionListener(sharedMouseHandler);
		}

		if (basePlane != CutSurface.SAGITTAL && activeCanvas != sagittalCanvas) {
			ImagePlus newSagittal = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
			sagittalImp.setProcessor(newSagittal.getProcessor());
			sagittalCanvas.reloadSlideGlasses(sagittalImp);
			// リロードで消えたリスナーを再登録
			sagittalCanvas.addMouseListener(sharedMouseHandler);
			sagittalCanvas.addMouseMotionListener(sharedMouseHandler);
		}

		if (basePlane != CutSurface.CORONAL && activeCanvas != coronalCanvas) {
			ImagePlus newCoronal = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
			coronalImp.setProcessor(newCoronal.getProcessor());
			coronalCanvas.reloadSlideGlasses(coronalImp);
			// リロードで消えたリスナーを再登録
			coronalCanvas.addMouseListener(sharedMouseHandler);
			coronalCanvas.addMouseMotionListener(sharedMouseHandler);
		}
		
		getBaseCanvas().setImagePositionUsingSlider(currentZ);
    }

	private void updateCrosslineDisplay() {
        int axX = 0, axY = 0, sagX = 0, sagY = 0, corX = 0, corY = 0;

        // 奥行き(スライス数)のみスケール変換の基準として使用する
        int d = baseVolume.getNSlices();

        if (basePlane == CutSurface.AXIAL) {
            // Axial(ベース): 面内はそのまま
            axX = currentX; 
            axY = currentY;
            // Sagittal: 横軸=前後(Y), 縦軸=頭足(Zスライスをスケール)
            sagX = currentY; 
            sagY = scaleZForDisplay(currentZ, d, sagittalImp.getHeight());
            // Coronal: 横軸=左右(X), 縦軸=頭足(Zスライスをスケール)
            corX = currentX; 
            corY = scaleZForDisplay(currentZ, d, coronalImp.getHeight());
            
        } else if (basePlane == CutSurface.CORONAL) {
            // Coronal(ベース): 面内はそのまま
            corX = currentX; 
            corY = currentY;
            // Axial: 横軸=左右(X), 縦軸=前後(Zスライスをスケール)
            axX = currentX; 
            axY = scaleZForDisplay(currentZ, d, axialImp.getHeight());
            // Sagittal: 横軸=前後(Zスライスをスケール), 縦軸=頭足(Yは面内なのでそのまま)
            sagX = scaleZForDisplay(currentZ, d, sagittalImp.getWidth());
            sagY = currentY; 
            
        } else if (basePlane == CutSurface.SAGITTAL) {
            // Sagittal(ベース): 面内はそのまま
            sagX = currentX; 
            sagY = currentY;
            // Axial: 横軸=左右(Zスライスをスケール), 縦軸=前後(Xは面内なのでそのまま)
            axX = scaleZForDisplay(currentZ, d, axialImp.getWidth());
            axY = currentX;
            // Coronal: 横軸=左右(Zスライスをスケール), 縦軸=頭足(Yは面内なのでそのまま)
            corX = scaleZForDisplay(currentZ, d, coronalImp.getWidth());
            corY = currentY; 
        }

        drawCrosshairOnPraparat(axialCanvas, axX, axY);
        drawCrosshairOnPraparat(sagittalCanvas, sagX, sagY);
        drawCrosshairOnPraparat(coronalCanvas, corX, corY);
    }

	private void drawCrosshairOnPraparat(Praparat prap, int x, int y) {
		if (prap == null || prap.getCurrentSlide() == null)
			return;
		SlideGlass sg = prap.getCurrentSlide();
		CanvasGlass cg = (CanvasGlass) sg.getGlassAt(SlideGlass.ROI_CANVAS_LAYER);
		if (cg != null) {
			cg.createCross(x, y);
			cg.repaint();
		}
	}

	// スケール逆算（Canvas上のピクセル -> baseVolumeのZインデックス）
	private int unscaleZForDisplay(int scaledPos, int originalMax, int scaledMax) {
		if (scaledMax == 0)
			return 0;
		return (int) Math.round((double) scaledPos * originalMax / scaledMax);
	}

	// スケール計算（baseVolumeのZインデックス -> Canvas上のピクセル）
	private int scaleZForDisplay(int originalPos, int originalMax, int scaledMax) {
		if (originalMax == 0)
			return 0;
		return (int) Math.round((double) originalPos * scaledMax / originalMax);
	}

	private void standardizeStackOrientation() {
		int nSlices = baseVolume.getNSlices();
		if (nSlices < 2)
			return;

		boolean isHeadFirst = PlanarSupport.isHeadFirst(baseVolume);

		double[] ipp1 = GDicomTools.getImagePositionPatient(baseVolume, 1);
		double[] ippN = GDicomTools.getImagePositionPatient(baseVolume, nSlices);
		if (ipp1 == null || ippN == null)
			return;

		boolean needsReversal = false;

		if (basePlane == CutSurface.AXIAL) {
			boolean isCurrentlyDescending = ippN[2] < ipp1[2];
			boolean targetDescending = isHeadFirst;
			if (isCurrentlyDescending != targetDescending) {
				needsReversal = true;
			}
		} else if (basePlane == CutSurface.CORONAL) {
			boolean isCurrentlyAscending = ippN[1] > ipp1[1];
			boolean targetAscending = isHeadFirst;
			if (isCurrentlyAscending != targetAscending) {
				needsReversal = true;
			}
		} else if (basePlane == CutSurface.SAGITTAL) {
			boolean isCurrentlyAscending = ippN[0] > ipp1[0];
			boolean targetAscending = isHeadFirst;
			if (isCurrentlyAscending != targetAscending) {
				needsReversal = true;
			}
		}

		if (needsReversal) {
			reverseStack(baseVolume);
			System.out.println("Stack order reversed to match standard anatomical orientation.");
		}
	}

	private void reverseStack(ImagePlus imp) {
		ImageStack stack = imp.getStack();
		int n = stack.getSize();
		ImageStack reversedStack = new ImageStack(stack.getWidth(), stack.getHeight());

		for (int i = n; i >= 1; i--) {
			reversedStack.addSlice(stack.getSliceLabel(i), stack.getProcessor(i));
		}
		imp.setStack(reversedStack);
	}

	// TODO
	@SuppressWarnings("unused")
	private void showExportDialog() {
		// 既存のまま
	}

	//TODO
	@SuppressWarnings("unused")
	private void exportDicomSeries(CutSurface targetPlane, double sliceThickness) {
		// 既存のまま
	}

	private ImagePlus blankImage(CutSurface surface) {
        Calibration cal = baseVolume.getCalibration();
        double pw = cal.pixelWidth;
        double ph = cal.pixelHeight;
        double pd = cal.pixelDepth;

        int w = baseVolume.getWidth();
        int h = baseVolume.getHeight();
        int d = baseVolume.getNSlices();

        int targetWidth = 0;
        int targetHeight = 0;

        if (basePlane == CutSurface.AXIAL) {
            if (surface == CutSurface.AXIAL) {
                targetWidth = w;
                targetHeight = h;
            } else if (surface == CutSurface.SAGITTAL) {
                targetWidth = h;
                targetHeight = (int) Math.round(d * (pd / ph)); // Z軸を引き伸ばす
            } else if (surface == CutSurface.CORONAL) {
                targetWidth = w;
                targetHeight = (int) Math.round(d * (pd / pw)); // Z軸を引き伸ばす
            }
        } else if (basePlane == CutSurface.SAGITTAL) {
            if (surface == CutSurface.AXIAL) {
                targetWidth = (int) Math.round(d * (pd / pw)); // スライス(LR)を引き伸ばす
                targetHeight = w; // A-Pは高解像度なので維持
            } else if (surface == CutSurface.SAGITTAL) {
                targetWidth = w;
                targetHeight = h;
            } else if (surface == CutSurface.CORONAL) {
                targetWidth = (int) Math.round(d * (pd / ph)); // スライス(LR)を引き伸ばす
                targetHeight = h; // S-Iは高解像度なので維持
            }
        } else if (basePlane == CutSurface.CORONAL) {
            if (surface == CutSurface.AXIAL) {
                targetWidth = w; // L-Rは高解像度なので維持
                targetHeight = (int) Math.round(d * (pd / pw)); // スライス(AP)を引き伸ばす
            } else if (surface == CutSurface.SAGITTAL) {
                targetWidth = (int) Math.round(d * (pd / ph)); // スライス(AP)を引き伸ばす
                targetHeight = h; // S-Iは高解像度なので維持
            } else if (surface == CutSurface.CORONAL) {
                targetWidth = w;
                targetHeight = h;
            }
        }

        if (targetWidth > 0 && targetHeight > 0) {
            return new ImagePlus(surface.name() + "_blank", ij.gui.NewImage
                    .createFloatImage("", targetWidth, targetHeight, 1, ij.gui.NewImage.FILL_BLACK).getProcessor());
        }
        return null;
    }
}
