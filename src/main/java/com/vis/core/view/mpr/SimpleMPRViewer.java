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
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import org.joml.Vector3d;

import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.glasses.CanvasGlass;
import com.vis.core.view.D2.ui.glasses.EventGlass;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.core.view.D3.ui.GantryTiltCorrector;
import com.vis.core.view.D2.ui.orientation.PlanarSupport;
import com.vis.dicom.Modality;
import com.vis.dicom.Tag;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.GDicomTools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.process.ImageProcessor;

/**
 * @author tatsunidas
 */
public class SimpleMPRViewer extends JFrame {

	// debug
	@SuppressWarnings("unused")
	public static void main(String[] arags) {
		String ax_path = "/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151";
		String mra = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/MRA";
		String sag_path = "/home/tatsunidas/graphy_sample_images/dicom_samples/3DFLAIR/3D-FLAIR";
		ImagePlus imp = FolderOpener.open(ax_path);
//		ImagePlus imp = FolderOpener.open(sag_path);
//		ImagePlus imp = FolderOpener.open(mra);
		// 実際の処理に沿わせる
		Praparat p = new Praparat(imp, Color.YELLOW, ViewMode.SingleGrid, false);
		ImagePlus replica = p.getImagePlus();
		new SimpleMPRViewer(replica);
	}

	private static final long serialVersionUID = 1L;

	// 画像とキャンバスの保持
	private ImagePlus axialImp, sagittalImp, coronalImp;
	private Praparat axialCanvas, sagittalCanvas, coronalCanvas;

	// 現在の交点
	/**
	 * baseVolumeがどの向きであろうと「その元画像の 横(X), 縦(Y), スライス(Z)」を意味します。 1. baseVolume が Axial
	 * の場合 currentX = axX ＝ 絶対X（左右） currentY = axY ＝ 絶対Y（前後） currentZ = axZ ＝
	 * 絶対Z（頭足）(すべてが一致するため、一番考えやすい状態です) 2. baseVolume が Sagittal の場合 currentX = sagX
	 * ＝ 絶対Y（前後） currentY = sagY ＝ 絶対Z（頭足） currentZ = sagZ ＝ 絶対X（左右） 3. baseVolume が
	 * Coronal の場合 currentX = corX ＝ 絶対X（左右） currentY = corY ＝ 絶対Z（頭足） currentZ =
	 * corZ ＝ 絶対Y（前後）
	 */
	private int currentX, currentY, currentZ;

	// 元となるベーススタック
	private ImagePlus baseVolume;
	private CutSurface basePlane;

	private java.awt.event.MouseAdapter sharedMouseHandler;

	public SimpleMPRViewer(ImagePlus inputImage) {

		if (inputImage == null || inputImage.getNSlices() == 0) {
			throw new IllegalArgumentException("Invalid image stack input...");
		}
		this.baseVolume = normalizeBaseVolume(inputImage);
		
//		for(int i=0; i< baseVolume.getNSlices(); i++) {
//			baseVolume.setPosition(GDicomTools.getRealIndex(baseVolume, i+1));
//			System.out.println("baseVoluem IOP:"+GDicomTools.getTag(baseVolume, Tag.ImagePositionPatient));
//		}
		
		String msg = "Starting SimpleMPRViewer, \n";
		msg += "[C, S, T]="+baseVolume.getNChannels()+","+baseVolume.getNSlices()+","+baseVolume.getNFrames()+ " images were loaded.";
		Log.logger.log(Level.INFO, msg);

		setTitle("Simple MPR Viewer");
		setSize(1000, 1000);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		init();
	}
	
	private void init() {
		// --- 1. モダンなスプラッシュスクリーン（プログレスダイアログ）の作成 ---
		JDialog progressDialog = new JDialog(this, "Loading...", false);
		progressDialog.setUndecorated(true); // ウィンドウの枠を消してモダンでスタイリッシュに
		progressDialog.setSize(450, 100);
		progressDialog.setLocationRelativeTo(null); // 画面中央に配置

		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.setBackground(new Color(40, 44, 52)); // ダークテーマ風の背景
		panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(97, 175, 239), 2), // アクセントカラーの枠線
				BorderFactory.createEmptyBorder(15, 20, 15, 20)));

		JLabel statusLabel = new JLabel("Initializing...");
		statusLabel.setForeground(Color.WHITE);
		statusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

		JProgressBar progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setForeground(new Color(97, 175, 239)); // プログレスバーの色（水色）
		progressBar.setBackground(new Color(33, 37, 43));
		progressBar.setBorderPainted(false);

		panel.add(statusLabel, BorderLayout.NORTH);
		panel.add(progressBar, BorderLayout.CENTER);
		progressDialog.add(panel);

		progressDialog.setVisible(true);

		// --- 2. バックグラウンドで重い処理を実行（SwingWorker） ---
		SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {

			@Override
			protected Void doInBackground() throws Exception {
				// "進捗率:メッセージ" の形式で publish し、UIを安全に更新する

				publish("10:Checking Modality and Gantry Tilt...");
				Modality m = Modality.is(GDicomTools.getTag(baseVolume, Tag.Modality));
				CutSurface plane = PlanarSupport.planarOf(baseVolume);

				if (m == Modality.CT && plane == CutSurface.AXIAL) {
					GantryTiltCorrector gtc = new GantryTiltCorrector();
					double tiltAngle = GDicomTools.getDouble(baseVolume, 1, "0018,1120"/* Gantry/Detector Tilt */);
					double pixelSpacingY = baseVolume.getCalibration().pixelHeight;
					double sliceSpacing = GDicomTools.getVoxelDepth(baseVolume);
					double reconSliceSpacing = sliceSpacing < 1d ? sliceSpacing : 1d;

					publish("20:Correcting Gantry Tilt (This may take a while)...");
					baseVolume = gtc.correctVolume3D(baseVolume/* 16-bit image required */, tiltAngle, pixelSpacingY,
							sliceSpacing, reconSliceSpacing);
				}

				publish("40:Determining Base Plane...");
				basePlane = determineBasePlane(baseVolume);
				System.out.println("Base volume slice plane is " + basePlane);

				publish("50:Checking Spatial Calibration...");
				checkSpatialCalibration();

				publish("60:Standardizing Stack Orientation...");
				PlanarSupport.standardizeStackOrientation(baseVolume);

				publish("70:Converting Base Volume to 32-bit Float...");
				convertBaseVolumeToFloat();

				publish("85:Reconstructing Orthogonal Planes...");
				// 1. 各断面のImagePlusを生成（ボクセルサイズを考慮したマトリクス生成）
				reconstructCenterOrthogonal();

				return null;
			}

			@Override
			protected void process(java.util.List<String> chunks) {
				// publish で送られた最新のメッセージを取得してプログレスバーを更新
				String lastMessage = chunks.get(chunks.size() - 1);
				String[] parts = lastMessage.split(":", 2);
				if (parts.length == 2) {
					int progress = Integer.parseInt(parts[0]);
					String text = parts[1];
					progressBar.setValue(progress);
					statusLabel.setText(text);
				}
			}

			@Override
			protected void done() {
				try {
					get(); // バックグラウンドで発生した例外をここでキャッチする

					// --- 3. UIコンポーネントの構築は必ずここで（UIスレッドで）行う ---
					progressBar.setValue(95);
					statusLabel.setText("Building GUI...");
					buildGUI(); // 2. GUIの構築（メニューと4パネル）

					statusLabel.setText("Setting up Mouse Listeners...");
					setupMouseListeners(); // 3. マウスイベントの登録

					progressBar.setValue(100);
					statusLabel.setText("Ready.");

					// 100%の画面をほんの一瞬（300ミリ秒）見せてからダイアログを閉じ、メイン画面を表示
					Timer timer = new Timer(300, e -> {
						progressDialog.dispose();
						setVisible(true);
					});
					timer.setRepeats(false);
					timer.start();

				} catch (Exception e) {
					e.printStackTrace();
					progressDialog.dispose();
				}
			}
		};

		worker.execute(); // バックグラウンド処理を開始
	}
	
	private void checkSpatialCalibration() {
		Calibration cal = baseVolume.getCalibration();
		double[] pixelspacing = GDicomTools.getDoubles(baseVolume, "0028,0030");
		if (cal.pixelWidth == 1.0 && pixelspacing != null) {
			cal.pixelWidth = pixelspacing[1];
		}

		if (cal.pixelHeight == 1.0 && pixelspacing != null) {
			cal.pixelHeight = pixelspacing[0];
		}
		// update depth
		cal.pixelDepth = GDicomTools.getVoxelDepth(baseVolume);
		String msg = "Voxel size (x,y,z):";
		msg += cal.pixelWidth+","+cal.pixelHeight+","+cal.pixelDepth;
		Log.logger.log(Level.FINE, msg);
	}
	
	private ImagePlus normalizeBaseVolume(ImagePlus rawBaseVolume) {
		int w = rawBaseVolume.getWidth();
		int h = rawBaseVolume.getHeight();
		int d = rawBaseVolume.getNSlices();
		Calibration cal = rawBaseVolume.getCalibration();
		
		CutSurface basePlane = PlanarSupport.planarOf(rawBaseVolume);
		if (basePlane == CutSurface.UNKNOWN || basePlane == CutSurface.OBLIQUE) {
			return rawBaseVolume;
		}

		// 目標とする標準LPSの方向ベクトル
		Vector3d idealRow = new Vector3d();
		Vector3d idealCol = new Vector3d();
		if (basePlane == CutSurface.AXIAL) {
			idealRow.set(1, 0, 0); idealCol.set(0, 1, 0); // X:R->L, Y:A->P
		} else if (basePlane == CutSurface.CORONAL) {
			idealRow.set(1, 0, 0); idealCol.set(0, 0, -1); // X:R->L, Y:S->I
		} else if (basePlane == CutSurface.SAGITTAL) {
			idealRow.set(0, 1, 0); idealCol.set(0, 0, -1); // X:A->P, Y:S->I
		}

		ImageStack normalizedStack = new ImageStack(w, h);

		for (int z = 1; z <= d; z++) {
			ImageProcessor ip = rawBaseVolume.getStack().getProcessor(z).duplicate();
			double[] oldIop = GDicomTools.getImageOrientationPatient(rawBaseVolume, z);
			double[] oldIpp = GDicomTools.getImagePositionPatient(rawBaseVolume, z);

			// ★ 2. 補正後の(0,0)ピクセルに対応する「元の空間座標」を計算して新しいIPPとする
			Vector3d tl = new Vector3d(oldIpp[0], oldIpp[1], oldIpp[2]);
			Vector3d rowVec = new Vector3d(oldIop[0], oldIop[1], oldIop[2]).mul((w - 1) * cal.pixelWidth);
			Vector3d colVec = new Vector3d(oldIop[3], oldIop[4], oldIop[5]).mul((h - 1) * cal.pixelHeight);
			
			Vector3d tr = new Vector3d(tl).add(rowVec);
			Vector3d bl = new Vector3d(tl).add(colVec);
			Vector3d br = new Vector3d(tl).add(rowVec).add(colVec);

			Vector3d[] corners = {tl, tr, bl, br};
			Vector3d newIpp = corners[0];
			double minDot = Double.MAX_VALUE;
			
			// 新しい画像の「右下」方向ベクトル
			Vector3d targetDir = new Vector3d(idealRow).add(idealCol).normalize();
			
			// 新しい「右下方向」に対して、最も「根元（投影値が最小）」にある角が、新しい(0,0)＝IPP
			for (Vector3d c : corners) {
				double dot = c.dot(targetDir);
				if (dot < minDot) {
					minDot = dot;
					newIpp = c;
				}
			}
			newIpp = PlanarSupport.truncate(newIpp, 6);

			// ★ 3. メタデータを一切壊さずに上書き
			ImageStack tempStack = new ImageStack(w, h);
			tempStack.addSlice(rawBaseVolume.getStack().getSliceLabel(z), ip);
			ImagePlus tempImp = new ImagePlus("temp", tempStack);
			
			GDicomTools.setDoubles(tempImp, 1, "0020,0032", new double[]{newIpp.x, newIpp.y, newIpp.z});
			GDicomTools.setDoubles(tempImp, 1, "0020,0037", new double[]{idealRow.x, idealRow.y, idealRow.z, idealCol.x, idealCol.y, idealCol.z});

			normalizedStack.addSlice(tempImp.getStack().getSliceLabel(1), ip);
		}

		ImagePlus normalizedVolume = new ImagePlus("Normalized_Base", normalizedStack);
		normalizedVolume.setCalibration(cal);
		normalizedVolume.setProperty("Info", normalizedStack.getSliceLabel(1));
		
		return normalizedVolume;
	}

	/**
	 * * show starting-up orthogonals
	 */
//	private void reconstructCenterOrthogonal() {
//		// 中心座標の初期化（baseVolumeのピクセルベース）
//		currentX = baseVolume.getWidth() / 2;
//		currentY = baseVolume.getHeight() / 2;
//		currentZ = baseVolume.getNSlices() / 2;
//
//		// basePlaneに応じて、どれを元画像とし、どれを再構成するか決定する
//		if (basePlane == CutSurface.AXIAL) {
//			this.axialImp = baseVolume;
//			this.sagittalImp = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
//			this.coronalImp = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
//		} else if (basePlane == CutSurface.SAGITTAL) {
//			this.axialImp = extractPlane(CutSurface.AXIAL, currentX, currentY, currentZ);
//			this.sagittalImp = baseVolume;
//			this.coronalImp = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
//		} else if (basePlane == CutSurface.CORONAL) {
//			this.axialImp = extractPlane(CutSurface.AXIAL, currentX, currentY, currentZ);
//			this.sagittalImp = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
//			this.coronalImp = baseVolume;
//		}
//	}
	
	private void reconstructCenterOrthogonal() {
	    currentX = baseVolume.getWidth() / 2;
	    currentY = baseVolume.getHeight() / 2;
	    currentZ = baseVolume.getNSlices() / 2;

	    // 断面が特定できない(OBLIQUE含む)場合のフォールバックを組み込む
	    if (basePlane == CutSurface.SAGITTAL) {
	        this.axialImp = extractPlane(CutSurface.AXIAL, currentX, currentY, currentZ);
	        this.sagittalImp = baseVolume;
	        this.coronalImp = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
	    } else if (basePlane == CutSurface.CORONAL) {
	        this.axialImp = extractPlane(CutSurface.AXIAL, currentX, currentY, currentZ);
	        this.sagittalImp = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
	        this.coronalImp = baseVolume;
	    } else {
	        // AXIAL または OBLIQUE, null の場合は AXIAL として処理
	        if (basePlane != CutSurface.AXIAL) {
	            System.out.println("Warning: Forcing fallback from " + basePlane + " to AXIAL.");
	            basePlane = CutSurface.AXIAL;
	        }
	        this.axialImp = baseVolume;
	        this.sagittalImp = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
	        this.coronalImp = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
	    }
	}

	/**
	 * 
	 * @param willCreatePlane
	 * @param x               : baseVolume voxel indexX
	 * @param y               : baseVolume voxel indexY
	 * @param z               : baseVolume voxel indexZ
	 * @return
	 */
	private ImagePlus extractPlane(CutSurface willCreatePlane, int x, int y, int z) {
		if (willCreatePlane == basePlane) {
			ij.ImageStack stack = baseVolume.getStack();
			int sliceIndex = Math.max(1, Math.min(z + 1, stack.getSize())); // 1-based index
			return new ImagePlus(willCreatePlane.name(), stack.getProcessor(sliceIndex).duplicate());
		}

		ImagePlus targetBlank = blankImage(willCreatePlane);
		if (targetBlank == null)
			return null;

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
//			if (willCreatePlane == CutSurface.SAGITTAL) {
//				extractedIp = new ij.process.FloatProcessor(h, d);
//				for (int zIdx = 1; zIdx <= d; zIdx++) {
//					ij.process.ImageProcessor slice = stack.getProcessor(zIdx);
//					for (int yIdx = 0; yIdx < h; yIdx++) {
//						extractedIp.setf(yIdx, zIdx - 1, slice.getf(x, yIdx));
//					}
//				}
//				px = baseCal.pixelHeight;
//				py = (baseCal.pixelDepth * d) / targetH;
//				pz = baseCal.pixelWidth;
//			} else if (willCreatePlane == CutSurface.CORONAL) {
//				extractedIp = new ij.process.FloatProcessor(w, d);
//				for (int zIdx = 1; zIdx <= d; zIdx++) {
//					ij.process.ImageProcessor slice = stack.getProcessor(zIdx);
//					for (int xIdx = 0; xIdx < w; xIdx++) {
//						extractedIp.putPixelValue(xIdx, zIdx - 1, slice.getPixelValue(xIdx, y));
//					}
//				}
//				px = baseCal.pixelWidth;
//				py = (baseCal.pixelDepth * d) / targetH;
//				pz = baseCal.pixelHeight;
//			}
			if (willCreatePlane == CutSurface.SAGITTAL) {
				// Sagittal: 横軸は Axial Y (A->P), 縦軸は Axial Z (S->I)
				extractedIp = new ij.process.FloatProcessor(h, d);
				for (int zIdx = 1; zIdx <= d; zIdx++) {
					ImageProcessor slice = stack.getProcessor(zIdx);
					for (int yIdx = 0; yIdx < h; yIdx++) {
						// X = Axial Y, Y = Axial Z(反転)
						extractedIp.setf(yIdx, d - zIdx, slice.getf(x, yIdx));
					}
				}
				px = baseCal.pixelHeight;
				py = baseCal.pixelDepth;
				pz = baseCal.pixelWidth;
			} else if (willCreatePlane == CutSurface.CORONAL) {
				// Coronal: 横軸は Axial X (R->L), 縦軸は Axial Z (S->I)
				extractedIp = new ij.process.FloatProcessor(w, d);
				for (int zIdx = 1; zIdx <= d; zIdx++) {
					ImageProcessor slice = stack.getProcessor(zIdx);
					for (int xIdx = 0; xIdx < w; xIdx++) {
						// X = Axial X, Y = Axial Z(反転)
						extractedIp.setf(xIdx, d - zIdx, slice.getf(xIdx, y));
					}
				}
				px = baseCal.pixelWidth;
				py = baseCal.pixelDepth;
				pz = baseCal.pixelHeight;
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
				px = baseCal.pixelDepth;
				py = baseCal.pixelWidth;
				pz = baseCal.pixelHeight;
			} else if (willCreatePlane == CutSurface.CORONAL) {
				extractedIp = new ij.process.FloatProcessor(d, h);
				for (int xIdx = 1; xIdx <= d; xIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(xIdx);
					for (int zIdx = 0; zIdx < h; zIdx++) {
						// SagittalベースでCoronalを抽出: 固定するのは解剖学的Y (幅=x)
						extractedIp.putPixelValue(xIdx - 1, zIdx, slice.getPixelValue(x, zIdx));
					}
				}
				px = baseCal.pixelDepth;
				py = baseCal.pixelHeight;
				pz = baseCal.pixelWidth;
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
				px = baseCal.pixelWidth;
				py = baseCal.pixelDepth;
				pz = baseCal.pixelHeight;
			} else if (willCreatePlane == CutSurface.SAGITTAL) {
				extractedIp = new ij.process.FloatProcessor(d, h);
				for (int yIdx = 1; yIdx <= d; yIdx++) {
					ij.process.ImageProcessor slice = stack.getProcessor(yIdx);
					for (int zIdx = 0; zIdx < h; zIdx++) {
						// CoronalベースでSagittalを抽出: 固定するのは解剖学的X (幅=x)
						extractedIp.putPixelValue(yIdx - 1, zIdx, slice.getPixelValue(x, zIdx));
					}
				}
				px = baseCal.pixelDepth;
				py = baseCal.pixelHeight;
				pz = baseCal.pixelWidth;
			}
		}

		if (extractedIp != null) {
			extractedIp.setInterpolationMethod(ij.process.ImageProcessor.BILINEAR);
			ij.process.ImageProcessor resizedIp = extractedIp.resize(targetW, targetH);
			ImagePlus ortho = new ImagePlus("orthogonal", resizedIp);
			copyImageMetaInformation(baseVolume, ortho, willCreatePlane, px, py, pz);
			/*
			 * Do not set displayMinMax.
			 */
//			ortho.setDisplayRange(baseVolume.getDisplayRangeMin(), baseVolume.getDisplayRangeMax());
			return ortho;
		}
		return targetBlank;
	}

	
	private void copyImageMetaInformation(ImagePlus base, ImagePlus recon, CutSurface reconPlane, double px, double py, double pz) {
		String pid = GDicomTools.getTag(base, "0010,0020");
		String studyUid = GDicomTools.getTag(base, "0020,000D");
		String seriesUid = UIDUtils.createUID();
		String sopUid = UIDUtils.createUID();

		double[] baseIop = GDicomTools.getImageOrientationPatient(base, 1);
		Vector3d baseRow = new Vector3d(baseIop[0], baseIop[1], baseIop[2]);
		Vector3d baseCol = new Vector3d(baseIop[3], baseIop[4], baseIop[5]);

		double[] ipp1Arr = GDicomTools.getImagePositionPatient(base, 1);
		Vector3d ipp1 = new Vector3d(ipp1Arr[0], ipp1Arr[1], ipp1Arr[2]);

		// ★修正1: stackDir (スタック進行方向) を外積ではなく、実際のIPPの差分から正確に算出する
		Vector3d stackDir = new Vector3d();
		if (base.getNSlices() > 1) {
			double[] ipp2Arr = GDicomTools.getImagePositionPatient(base, 2);
			Vector3d ipp2 = new Vector3d(ipp2Arr[0], ipp2Arr[1], ipp2Arr[2]);
			stackDir.set(ipp2).sub(ipp1).normalize();
		} else {
			// スライスが1枚しかない場合のフォールバック
			if (basePlane == CutSurface.AXIAL) stackDir.set(0, 0, 1);
			else if (basePlane == CutSurface.CORONAL) stackDir.set(0, 1, 0);
			else if (basePlane == CutSurface.SAGITTAL) stackDir.set(1, 0, 0);
			else stackDir = PlanarSupport.crossProduct(baseRow, baseCol, true);
		}
		
		Vector3d ipp = new Vector3d();
		Vector3d row = new Vector3d();
		Vector3d col = new Vector3d();
		
		Calibration baseCal = base.getCalibration();
		int maxZ = base.getNSlices() - 1; 

		if (basePlane == CutSurface.AXIAL) {
			if (reconPlane == CutSurface.AXIAL) {
				ipp.set(ipp1); row.set(baseRow); col.set(baseCol);
			} else if (reconPlane == CutSurface.SAGITTAL) {
				row.set(baseCol); 
				col.set(stackDir).negate(); // ★ 上が頭、下が足なので -Z方向
				ipp.set(baseRow).mul(currentX * baseCal.pixelWidth)
				   .add(ipp1).add(new Vector3d(stackDir).mul(maxZ * baseCal.pixelDepth));
			} else if (reconPlane == CutSurface.CORONAL) {
				row.set(baseRow); 
				col.set(stackDir).negate(); // ★ 上が頭、下が足なので -Z方向
				ipp.set(baseCol).mul(currentY * baseCal.pixelHeight)
				   .add(ipp1).add(new Vector3d(stackDir).mul(maxZ * baseCal.pixelDepth));
			}
		} else if (basePlane == CutSurface.SAGITTAL) {
			if (reconPlane == CutSurface.SAGITTAL) {
				ipp.set(ipp1); row.set(baseRow); col.set(baseCol);
			} else if (reconPlane == CutSurface.AXIAL) {
				row.set(stackDir); col.set(baseRow);
				ipp.set(baseCol).mul(currentY * baseCal.pixelHeight).add(ipp1);
			} else if (reconPlane == CutSurface.CORONAL) {
				row.set(stackDir); col.set(baseCol);
				ipp.set(baseRow).mul(currentX * baseCal.pixelWidth).add(ipp1);
			}
		} else if (basePlane == CutSurface.CORONAL) {
			if (reconPlane == CutSurface.CORONAL) {
				ipp.set(ipp1); row.set(baseRow); col.set(baseCol);
			} else if (reconPlane == CutSurface.AXIAL) {
				row.set(baseRow); col.set(stackDir);
				ipp.set(baseCol).mul(currentY * baseCal.pixelHeight).add(ipp1);
			} else if (reconPlane == CutSurface.SAGITTAL) {
				row.set(stackDir); col.set(baseCol);
				ipp.set(baseRow).mul(currentX * baseCal.pixelWidth).add(ipp1);
			}
		}

		// 誤差クレンジングとタグの書き込み
		PlanarSupport.normalizeAndOrthogonalize(row, col);
		row = PlanarSupport.truncate(row, 6);
		col = PlanarSupport.truncate(col, 6);
		ipp = PlanarSupport.truncate(ipp, 6);

		double[] iop = new double[] { row.x, row.y, row.z, col.x, col.y, col.z };

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

		axialCanvas = new Praparat(axialImp, Color.BLUE, ViewMode.MPR, false);
		sagittalCanvas = new Praparat(sagittalImp, Color.GREEN, ViewMode.MPR, false);
		coronalCanvas = new Praparat(coronalImp, Color.RED, ViewMode.MPR, false);

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
							newX = cx; newY = cy; newZ = cz;
						} else {
							// ★ Axialベースの時はZ軸が画面と反転しているため、trueを渡す
							if (basePlane == CutSurface.AXIAL) {
								if (pp == sagittalCanvas) {
									newY = cx;
									newZ = unscaleZForDisplay(cy, d, sagittalImp.getHeight(), true);
									newX = currentX;
								} else if (pp == coronalCanvas) {
									newX = cx;
									newZ = unscaleZForDisplay(cy, d, coronalImp.getHeight(), true);
									newY = currentY; 
								}
							} else if (basePlane == CutSurface.CORONAL) {
								if (pp == axialCanvas) {
									newX = cx;
									newZ = unscaleZForDisplay(cy, d, axialImp.getHeight(), false);
									newY = currentY;
								} else if (pp == sagittalCanvas) {
									newZ = unscaleZForDisplay(cx, d, sagittalImp.getWidth(), false);
									newY = cy;
									newX = currentX;
								}
							} else if (basePlane == CutSurface.SAGITTAL) {
								if (pp == axialCanvas) {
									newZ = unscaleZForDisplay(cx, d, axialImp.getWidth(), false);
									newX = cy;
									newY = currentY;
								} else if (pp == coronalCanvas) {
									newZ = unscaleZForDisplay(cx, d, coronalImp.getWidth(), false);
									newY = cy;
									newX = currentX;
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
		 * DICOM RCS coordinates. NOT baseVolume coords.
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
			/*
			 * DO NOT SET display min max.
			 */
			//newAxial.setDisplayRange(baseVolume.getDisplayRangeMin(), baseVolume.getDisplayRangeMax());
			axialImp.setProcessor(newAxial.getProcessor());
			axialCanvas.reloadSlideGlasses(axialImp);
			// リロードで消えたリスナーを再登録
			axialCanvas.addMouseListener(sharedMouseHandler);
			axialCanvas.addMouseMotionListener(sharedMouseHandler);
		}

		if (basePlane != CutSurface.SAGITTAL && activeCanvas != sagittalCanvas) {
			ImagePlus newSagittal = extractPlane(CutSurface.SAGITTAL, currentX, currentY, currentZ);
			/*
			 * DO NOT SET display min max.
			 */
			//newSagittal.setDisplayRange(baseVolume.getDisplayRangeMin(), baseVolume.getDisplayRangeMax());
			sagittalImp.setProcessor(newSagittal.getProcessor());
			sagittalCanvas.reloadSlideGlasses(sagittalImp);
			// リロードで消えたリスナーを再登録
			sagittalCanvas.addMouseListener(sharedMouseHandler);
			sagittalCanvas.addMouseMotionListener(sharedMouseHandler);
		}

		if (basePlane != CutSurface.CORONAL && activeCanvas != coronalCanvas) {
			ImagePlus newCoronal = extractPlane(CutSurface.CORONAL, currentX, currentY, currentZ);
			/*
			 * DO NOT SET display min max.
			 */
			//newCoronal.setDisplayRange(baseVolume.getDisplayRangeMin(), baseVolume.getDisplayRangeMax());
			coronalImp.setProcessor(newCoronal.getProcessor());
			coronalCanvas.reloadSlideGlasses(coronalImp);
			// リロードで消えたリスナーを再登録
			coronalCanvas.addMouseListener(sharedMouseHandler);
			coronalCanvas.addMouseMotionListener(sharedMouseHandler);
		}

		getBaseCanvas().setImagePositionUsingSlider(currentZ);
	}

	// --- updateCrosslineDisplay() の内部 ---
	private void updateCrosslineDisplay() {
		int axX = 0, axY = 0, sagX = 0, sagY = 0, corX = 0, corY = 0;
		int d = baseVolume.getNSlices();

		if (basePlane == CutSurface.AXIAL) {
			axX = currentX;
			axY = currentY;
			sagX = currentY;
			sagY = scaleZForDisplay(currentZ, d, sagittalImp.getHeight(), true); // ★反転
			corX = currentX;
			corY = scaleZForDisplay(currentZ, d, coronalImp.getHeight(), true); // ★反転

		} else if (basePlane == CutSurface.CORONAL) {
			corX = currentX;
			corY = currentY;
			axX = currentX;
			axY = scaleZForDisplay(currentZ, d, axialImp.getHeight(), false);
			sagX = scaleZForDisplay(currentZ, d, sagittalImp.getWidth(), false);
			sagY = currentY;

		} else if (basePlane == CutSurface.SAGITTAL) {
			sagX = currentX;
			sagY = currentY;
			axX = scaleZForDisplay(currentZ, d, axialImp.getWidth(), false);
			axY = currentX;
			corX = scaleZForDisplay(currentZ, d, coronalImp.getWidth(), false);
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

	// ★修正2: invertフラグを追加
	// スケール逆算（Canvas上のピクセル -> baseVolumeのZインデックス）
	private int unscaleZForDisplay(int scaledPos, int originalMax, int scaledMax, boolean invertZ) {
		if (scaledMax == 0)
			return 0;
		int unscaled = (int) Math.round((double) scaledPos * originalMax / scaledMax);
		if (invertZ) {
			unscaled = (originalMax - 1) - unscaled;
		}
		return Math.max(0, Math.min(unscaled, originalMax - 1));
	}

	// スケール計算（baseVolumeのZインデックス -> Canvas上のピクセル）
	private int scaleZForDisplay(int originalPos, int originalMax, int scaledMax, boolean invertZ) {
		if (originalMax == 0)
			return 0;
		if (invertZ) {
			originalPos = (originalMax - 1) - originalPos;
		}
		return (int) Math.round((double) originalPos * scaledMax / originalMax);
	}
	
	/**
     * baseVolume convert to 32-bit FloatProcessor stack
     */
    private void convertBaseVolumeToFloat() {
        if (baseVolume.getType() == ImagePlus.GRAY32) {
            return;
        }

        double min = baseVolume.getDisplayRangeMin();
        double max = baseVolume.getDisplayRangeMax();

        ij.process.ImageConverter converter = new ij.process.ImageConverter(baseVolume);
        converter.convertToGray32();

        baseVolume.setDisplayRange(min, max);
        
        System.out.println("Converted baseVolume to 32-bit Float format. Memory unified.");
    }

	// TODO
	@SuppressWarnings("unused")
	private void showExportDialog() {
		// 既存のまま
	}

	// TODO
	@SuppressWarnings("unused")
	private void exportDicomSeries(CutSurface targetPlane, double sliceThickness) {
		// 既存のまま
	}

	private ImagePlus blankImage(CutSurface surface) {
		Calibration cal = baseVolume.getCalibration();
		double pw = cal.pixelWidth;
		double ph = cal.pixelHeight;
		double pd = cal.pixelDepth;
		
		Log.logger.log(Level.FINE, "BaseVolume px,py,pz: "+pw+","+ph+","+pd);

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
