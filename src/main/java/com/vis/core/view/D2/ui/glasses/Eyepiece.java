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

package com.vis.core.view.D2.ui.glasses;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.view.D2.ui.StageDockManager;
import com.vis.core.view.D2.ui.Viewer2DScreen;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.PraparatShelf.PraparatContext;
import com.vis.db.DatabaseHandler;

import ij.ImagePlus;

@SuppressWarnings("serial")
public class Eyepiece extends JPanel{
	
	/**
	 * Eyepiece is a view of all praps
	 */
	final String patID;
	DatabaseHandler db = DatabaseHandler.getInstance();
	PraparatShelf prapShelf = null;
	
	HashMap<String, Color> studyColors;
	
	private final int gap = 3;
	GridLayout gridLayout = new GridLayout(1, 0, gap, gap);
	
	//MPR functions
	public boolean MPRViewMode;
	
	private Component draggingComponent = null; // 現在ドラッグ中のコンポーネント
	private int insertionIndex = -1; // ドロップ予定のインデックス
	
	private Component fusionTargetComponent = null;
	
	public Eyepiece(String patID) {
		this.patID = patID;
		init();
//		DropTarget dt = new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
		new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE,new ImageDropTargetListener());
	}
	
	private void init() {
		prapShelf = new PraparatShelf();
		setLayout(gridLayout);
		studyColors = new HashMap<>();
		if(db !=null) {
			ArrayList<String> studyUIDs = db.getStudyUidList(patID);
			int colorInd = 0;
			for(String studyUID : studyUIDs) {
				studyColors.put(studyUID, allocateStudyColor(colorInd));
				colorInd+=10;
			}
		}
		setMinimumSize(new Dimension(64,64));
		setOpaque(false);
	}
	
	/**
	 * If sopUIDs is null, load all instances automatically.
	 * 
	 * @param patID
	 * @param studyUID
	 * @param seriesUID
	 * @param sopUIDs
	 * @param studyColor
	 * @return
	 */
	private Praparat buildPraparat(String patID, String studyUID, String seriesUID,String[] sopUIDs, String refUID) {
		if(seriesUID == null) {
			Log.logger.fine("SeriesUID is NUll, return null.");
			return null;
		}
		//load instances locations
		ArrayList<String> p2images = new ArrayList<String>();
		for(String sopUID:sopUIDs) {
			String p2img = db.getFileLocation(studyUID, seriesUID, sopUID);
			if(p2img != null) {
				p2images.add(p2img);
			}
		}
		Praparat prap = new Praparat(patID, studyUID, seriesUID, sopUIDs, p2images, refUID, this, studyColors.get(studyUID), ViewMode.Normal);
		return prap;
	}
	
	public Praparat getPraparatAt(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		return prapShelf.getPraparat(patID, studyUID, seriesUID, sopUIDs);
	}
	
	public Praparat getPraparatOnEyeAt(java.awt.Point p) {
		Component prap = getComponentAt(p);
		if(prap instanceof Praparat) {
			return (Praparat) prap;
		}else {
			return null;
		}
	}
	
	public void removePraparat(String patID,String studyUID,String seriesUID,String[] sopUIDs) {
		if(seriesUID == null) {
			return;
		}
		prapShelf.removePraparat(patID, studyUID, seriesUID, sopUIDs);
	}
	
	public void removePraparat(Praparat pp) {
		prapShelf.removePraparat(pp);
	}
	
	public void removeSelectedPraparats() {
		ArrayList<Praparat> candidates = getSelectingPraparats();
		for(Praparat pp:candidates) {
			removePraparat(pp);
		}
	}
	
	/**
	 * load images from db
	 * @param patID
	 * @param studyUID
	 * @param seriesUID
	 * @param sopUIDs
	 * @param refUID
	 */
	public void addPraparat(String patID, String studyUID, String seriesUID,
			String[] sopUIDs, String refUID) {
		if(patID == null || studyUID == null) {
			return;
		}
		if(seriesUID == null) {
			//load all series to eyepiece
			ArrayList<String> seriesList = db.getSeriesUidList(patID, studyUID);
			for (String seUID : seriesList) {
				//get sopUIDs
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID,studyUID, seUID);
				prapShelf.addPraparat(buildPraparat(patID, studyUID, seUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]), refUID));
			}
		}else {
			//select instances to show
			if(sopUIDs != null) {
				//only show specified instances
				prapShelf.addPraparat(buildPraparat(patID, studyUID, seriesUID, sopUIDs, refUID));
			}else {
				//show all instances in particular series
				ArrayList<String> sopUIDInSeries = db.getInstanceUidList(patID, studyUID, seriesUID);
				prapShelf.addPraparat(buildPraparat(patID, studyUID, seriesUID, sopUIDInSeries.toArray(new String[sopUIDInSeries.size()]), refUID));
			}
		}
	}
	
	public void addPraparat(Praparat pp) {
		prapShelf.addPraparat(pp);
		pp.setEyepiece(this);
	}
	
	public Color allocateStudyColor(int index) {
		ij.process.LUT studyColors = Resources.LUT_FIRE.loadLUT();
 		byte ind = (byte) index;//convert range to -128 ~ 127
		int location = (int)((int)ind + 128);//0 ~ 255
		int r = studyColors.getRed(location);
		int g = studyColors.getGreen(location);
		int b = studyColors.getBlue(location);
		return new Color(r,g,b);
	}
	
	public void updatePraparat(Praparat prevPrap, String newPatID, String newStudyUID, String newSeriesUID, String[] newSopUIDs, String newRefUID) {
		Praparat pp = buildPraparat(newPatID, newStudyUID, newSeriesUID, newSopUIDs, newRefUID);
		prapShelf.updatePraparatContext(prevPrap, pp);
	}
	
	public List<Praparat> getPraparatAmbiguously(String patID, String studyUID, String seriesUID) {
		List<PraparatContext> praps = getAllPraparatContext();
		ArrayList<Praparat> result = new ArrayList<Praparat>();
		for(PraparatContext pcon:praps) {
			Object[] con = pcon.getContextUIDs();
			String patID_ = (String)con[0];
			String studyUID_ = (String)con[1];
			String seriesUID_ = (String)con[2];
			if(patID_.equals(patID) && studyUID_.equals(studyUID) && seriesUID_.equals(seriesUID)) {
				result.add(pcon.getPraparat());
			}
		}
		return result;
	}
	
	public List<Praparat> getAllPraparatByFrameOfReferenceUID(String patID, String studyUID, String refUID) {
		List<PraparatContext> praps = getAllPraparatContext();
		List<Praparat> result = new ArrayList<Praparat>();
		for(PraparatContext pcon:praps) {
			Object[] con = pcon.getContextUIDs();
			String patID_ = (String)con[0];
			String studyUID_ = (String)con[1];
			// String seriesUID_ = (String)con[2];//do not need .
			String frameOfRefUID_ = (String)con[4];
			if(patID_.equals(patID) && studyUID_.equals(studyUID) && frameOfRefUID_.equals(refUID)) {
				result.add(pcon.getPraparat());
			}
		}
		return result;
	}
	
	public void autoLayout() {
		int numOfPrap = prapShelf.howManyPraparat();
		int rows = (int) Math.sqrt(numOfPrap);
		int cols = (int) Math.ceil((double) numOfPrap / rows);
		if(numOfPrap < 1) {
			gridLayout.setRows(1);
			gridLayout.setColumns(1);
			removeAll();
			Viewer2DScreen d2 = Viewer2DScreen.getInstance();
			StageDockManager sdm = d2.getStageDockManager();
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					sdm.deleteStage(patID);
					revalidate();
					repaint();
				}
			});
		}else {
			updateLayout(rows, cols);
		}
	}
	
	/**
     * 指定されたM×Nでレイアウトを更新します。
     * @param rows 行数 (M) - 最小 1
     * @param cols 列数 (N) - 最小 1
     */
	public void updateLayout(int rows, int cols) {
		if (rows < 1 || cols < 1) {
			Log.logger.warning("Row and Col shoud be > 0.");
			return;
		}

		int numOfPrap = prapShelf.howManyPraparat();

		if (numOfPrap == 0) {
			gridLayout = new GridLayout(1, 1, gap, gap);
			setLayout(gridLayout);
			removeAll();
			return;
		}

		// 2. update
		// GridLayout(int rows, int cols, int hgap, int vgap)
		gridLayout = new GridLayout(rows, cols, 0, 0);
		setLayout(gridLayout);
		removeAll();
		// add praps
		for (PraparatContext pcon : prapShelf.getAllShelfContents()) {
			add(pcon.getPraparat());
		}
		revalidate();
		repaint();
	}
	
	/**
	 * マウス座標から挿入すべきインデックスを計算するロジック
	 */
	public void updateInsertionIndex(Point p) {
	    int count = getComponentCount();
	    int closestIndex = -1;
	    double minDistance = Double.MAX_VALUE;
	    
	    // 毎回リセット
	    fusionTargetComponent = null;

	    // 全コンポーネントを走査
	    for (int i = 0; i < count; i++) {
	        Component c = getComponent(i);
	        Rectangle b = c.getBounds();

	        // ★新規追加: マウスがこのコンポーネント内にあるか？
	        if (b.contains(p) && c != draggingComponent) {
	            // 中心50%の領域を「フュージョン・ホットスポット」とする
	            Rectangle hotspot = new Rectangle(
	                b.x + (int)(b.width * 0.25),
	                b.y + (int)(b.height * 0.25),
	                (int)(b.width * 0.5),
	                (int)(b.height * 0.5)
	            );
	            
	            if (hotspot.contains(p)) {
	                fusionTargetComponent = c;
	                insertionIndex = -1; // 挿入（タイリング）は無効化
	                return; // 判定完了、ループを抜ける
	            }
	        }

	        // 以降は既存の「最も近いコンポーネントを探す」処理
	        Point center = new Point(b.x + b.width / 2, b.y + b.height / 2);
	        double dist = p.distance(center);
	        if (dist < minDistance) {
	            minDistance = dist;
	            closestIndex = i;
	        }
	    }

	    if (closestIndex != -1) {
	        Component target = getComponent(closestIndex);
	        Rectangle b = target.getBounds();
	        if (p.x < b.x + b.width / 2) {
	            insertionIndex = closestIndex;
	        } else {
	            insertionIndex = closestIndex + 1;
	        }
	    } else {
	        insertionIndex = count;
	    }
	}
	
    /**
     * 実際の並べ替え処理（Insert）
     */
	public void performReorder() {
		if (draggingComponent == null) return;
		
		if (fusionTargetComponent != null && draggingComponent instanceof Praparat && fusionTargetComponent instanceof Praparat) {
	        Praparat bgPrap = (Praparat) fusionTargetComponent;
	        Praparat fgPrap = (Praparat) draggingComponent;
	        
	        int[] fg_zct_arr = fgPrap.calcZCTArrayFromIndex(fgPrap.getCurrentSlideZCTIndex());
	        int[] bg_zct_arr = bgPrap.calcZCTArrayFromIndex(bgPrap.getCurrentSlideZCTIndex());
	        
	        bgPrap.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            // ==========================================================
	        // ★ 極めて重要：AWT/Swingのデッドロックを防ぐため、
            // 画像の構築(getImagePlus)は必ずこの UIスレッド上 で済ませておく！
            // ==========================================================
            com.vis.core.log.Log.logger.info("Extracting ImagePlus on UI Thread...");
	        ImagePlus fgImp = fgPrap.getImagePlus(fg_zct_arr[1], fg_zct_arr[2]);
	        ImagePlus bgImp = bgPrap.getImagePlus(bg_zct_arr[1], bg_zct_arr[2]);

            if (fgImp == null || bgImp == null) {
                com.vis.core.log.Log.logger.severe("Failed to extract ImagePlus. Aborting fusion.");
                bgPrap.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                fusionTargetComponent = null;
                return;
            }

	        // ★ 重い「3Dリサンプリング・空間アライメント」処理のみをバックグラウンドへ逃がす
	        javax.swing.SwingWorker<ImagePlus, Void> worker = new javax.swing.SwingWorker<ImagePlus, Void>() {
	            @Override
	            protected ImagePlus doInBackground() throws Exception {
                    // Praparatではなく、純粋な ImagePlus をエンジンに渡す
	                return com.vis.core.fusion.ImagePairingEngine.alignVolumeStatic(fgImp, bgImp);
	            }

	            @Override
	            protected void done() {
	                bgPrap.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	                try {
	                    ImagePlus alignedOverlay = get();
	                    if (alignedOverlay != null) {
	                    	
	                    	ij.process.ImageStatistics stats = alignedOverlay.getStatistics();
	                        com.vis.core.log.Log.logger.info("[Fusion Render] Overlay Stats -> Min: " + stats.min + ", Max: " + stats.max + ", Mean: " + stats.mean);
	                        if (stats.max == 0) {
	                             com.vis.core.log.Log.logger.severe("[Fusion Render] WARNING: Overlay image is completely BLACK (Max=0).");
	                        }
	                    	
	                        bgPrap.enableFusionMode(alignedOverlay);
	                        /*
	                         * Ghost（ドラッグ中の残像）は、SlideGlassMouseListener の mouseReleased で
	                         * 無条件に ggp.setVisible(false); される。
	                         */
	                        
	                    } else {
	                        javax.swing.JOptionPane.showMessageDialog(bgPrap, "Fusion alignment failed.");
	                    }
	                } catch (Exception ex) {
	                    com.vis.core.log.Log.logger.log(java.util.logging.Level.SEVERE, "Error during fusion", ex);
	                }
	            }
	        };
	        
	        worker.execute();
	        
	        fusionTargetComponent = null;
	        draggingComponent = null;
	        return; 
	    }
		
		// 現在のインデックスを取得
		int currentIndex = -1;
		for (int i = 0; i < getComponentCount(); i++) {
			if (getComponent(i) == draggingComponent) {
				currentIndex = i;
				break;
			}
		}

		if (currentIndex == -1) {
			return;
		}
		
		// 削除してから挿入するため、インデックスのズレを補正
		// (自分より後ろに挿入する場合、削除によってインデックスが1つ減るため)
		if (insertionIndex > currentIndex) {
			insertionIndex--;
		}

		// 同じ場所なら何もしない
		if (insertionIndex == currentIndex)
			return;

		// スワップではなく「挿入」:
		// SwingのContainer.add(comp, index) は、既存の要素をシフトしてくれる
		remove(draggingComponent);
		add(draggingComponent, insertionIndex);

		revalidate(); // レイアウト計算しなおし
	}
	
	public ArrayList<Praparat> getSelectingPraparats() {
		ArrayList<Praparat> praps = new ArrayList<Praparat>();
		for(PraparatContext pcon:prapShelf.getAllShelfContents()) {
			Praparat prap = pcon.getPraparat();
			if(prap.isSelected()) {
				praps.add(prap);
			}
		}
		return praps;
	}
	
	public List<Praparat> getAllPraparat(){
		List<PraparatContext> pCons = getAllPraparatContext();
		List<Praparat> praps = new ArrayList<>();
		for(PraparatContext pcon: pCons) {
			praps.add(pcon.getPraparat());
		}
		if(praps.isEmpty()) {
			return null;
		}
		return praps;
	}
	
	public List<PraparatContext> getAllPraparatContext(){
		return prapShelf.getAllShelfContents();
	}
	
	public PraparatContext getPraparatContextOf(String pid, String studyUID, String seriesUID, String[] sopUIDs) {
		return prapShelf.getPraparatContext(pid, studyUID, seriesUID, sopUIDs);
	}
	
	public void lostAllPraparatFocusGained() {
		List<PraparatContext> pcons = getAllPraparatContext();
		for(PraparatContext pcon:pcons) {
			Praparat pp = pcon.getPraparat();
			pp.setFocusGained(false);
		}
	}
	
	public void setDraggingComponent(Component draggingSlide) {
		this.draggingComponent = draggingSlide;
	}
	
    // 挿入位置のライン（キャレット）を描画
	@Override
	protected void paintChildren(Graphics g) {
	    super.paintChildren(g); // 子コンポーネントを描画

	    Graphics2D g2 = (Graphics2D) g;

	    // ★新規追加: フュージョンのホットスポットに入っている場合、青くハイライト
	    if (fusionTargetComponent != null && draggingComponent != null) {
	        Rectangle bounds = fusionTargetComponent.getBounds();
	        g2.setColor(new Color(0, 120, 215, 80)); // 半透明の青
	        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
	        g2.setColor(new Color(0, 120, 215, 200));
	        g2.setStroke(new BasicStroke(4f));
	        g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
	        return; // 赤い挿入ラインは描画しない
	    }
        if (draggingComponent != null && insertionIndex >= 0) {
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(4f));

            // 挿入位置の座標計算
            Rectangle bounds;
            int x, y, h;

            int componentCount = getComponentCount();
            
            if (componentCount == 0) return;

            // 末尾への追加か、既存要素の前への挿入か
            if (insertionIndex < componentCount) {
                // 既存のコンポーネントの「左側」に描画
                Component target = getComponent(insertionIndex);
                bounds = target.getBounds();
                x = bounds.x;
                y = bounds.y;
                h = bounds.height;
            } else {
                // 最後のコンポーネントの「右側」に描画
                Component last = getComponent(componentCount - 1);
                bounds = last.getBounds();
                x = bounds.x + bounds.width;
                y = bounds.y;
                h = bounds.height;
            }

            // グリッドの隙間(gap)を考慮して少し調整
            int gapAdjustment = ((GridLayout)getLayout()).getHgap() / 2;
            g2.drawLine(x - gapAdjustment, y, x - gapAdjustment, y + h);
        }
    }
}
