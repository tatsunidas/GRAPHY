package com.vis.core.anonymize;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import com.vis.configuration.ContextKey;
import com.vis.core.log.Log;
import com.vis.core.ui.listener.RoiObjListener;
import com.vis.core.ui.main.dcmtreetable.DICOMNode;
import com.vis.core.view.D2.roi.RoiObj;
import com.vis.core.view.D2.roi.RoiType;
import com.vis.core.view.D2.ui.Viewer2DToolBar;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.Tag;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

@SuppressWarnings("serial")
public class PixelAnonymizerPanel extends JPanel {
	
    private StudyCheckBoxTree studyTree;
    
    private Praparat praparatViewer; 
    private ButtonGroup toolButtonGroup; // ツールボタンの排他制御用
    
    private JPanel seriesDisplayPanel; // Praparatを配置するパネル
    private JPanel maskRoiListPanel;   // ROIパネルを並べるリスト
    private AttributeAnonymizerPanel attrAnonPanel;
    
    private JProgressBar progressBar;
    private JButton btnExecute;
    
 // 追跡用のリスト
    private final List<RoiObj> tempRois = new ArrayList<>();
    
    private boolean executing = false;

    public PixelAnonymizerPanel() {
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        // ==========================================
        // 上部 (Center): ツリー、画像ビューワ、ROIリスト
        // ==========================================
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.2); // 左ペインの比率
        mainSplit.setContinuousLayout(true); // バーをドラッグ中も中身をリアルタイムに再描画する
        mainSplit.setDividerSize(8);         // バーの幅を少し太くして掴みやすくする（デフォルトは細すぎる場合があります）
        mainSplit.setOneTouchExpandable(true); // バーにワンタッチで折りたたむ矢印ボタンを付ける（便利です）
        
        // 右: ビューワとROIリストの分割
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        rightSplit.setResizeWeight(0.7); // ビューワの比率を大きく

        // 中央: シリーズ表示パネル (Praparat)
        seriesDisplayPanel = new JPanel(new BorderLayout());
        seriesDisplayPanel.setBorder(new TitledBorder("Series Display"));
        // TODO: ここに Praparat のインスタンスと ROIツールバーを add する
        praparatViewer = new Praparat(ViewMode.SingleGrid);
        setupPraparatRoiListener();
        seriesDisplayPanel.add(praparatViewer, BorderLayout.CENTER);
        seriesDisplayPanel.setMinimumSize(new Dimension(300, 200));
        
        JToolBar roiToolBar = new JToolBar();
        roiToolBar.setFloatable(false); // ツールバーを固定
        toolButtonGroup = new ButtonGroup();
        roiToolBar.add(createToolButton("Pointer", Viewer2DToolBar.Windowing, true)); // デフォルト
        roiToolBar.addSeparator();
        roiToolBar.add(createToolButton("Rectangle", RoiType.RECTANGLE.id(), false));
        roiToolBar.add(createToolButton("Polygon", RoiType.POLYGON.id(), false));
        roiToolBar.add(createToolButton("Oval", RoiType.OVAL.id(), false));
        seriesDisplayPanel.add(roiToolBar, BorderLayout.NORTH);
        rightSplit.setLeftComponent(seriesDisplayPanel);
        
        // 左: スタディツリー
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        studyTree = new StudyCheckBoxTree(root, this, praparatViewer);
        JScrollPane treeScroll = new JScrollPane(studyTree);
        treeScroll.setBorder(new TitledBorder("Study / Series (Uncheck to Exclude)"));
        treeScroll.setMinimumSize(new Dimension(200, 0));
        mainSplit.setLeftComponent(treeScroll);
        
        // 右端: マスクROI管理リストパネル
        JPanel roiPanel = createMaskRoiListPanel();
        roiPanel.setMinimumSize(new Dimension(250, 0));
        rightSplit.setRightComponent(roiPanel);

        mainSplit.setRightComponent(rightSplit);
//        add(mainSplit, BorderLayout.CENTER);

        // ==========================================
        // 下部 (South): 属性匿名化パネル ＆ 実行パネル
        // ==========================================
        JPanel bottomContainer = new JPanel(new BorderLayout());

        // 属性匿名化パネル（PIXEL_MODEで初期化し、入力フォルダ指定を隠す）
        attrAnonPanel = new AttributeAnonymizerPanel(AttributeAnonymizerPanel.Mode.PIXEL_MODE);
        attrAnonPanel.setPreferredSize(new Dimension(800, 300));
        bottomContainer.add(attrAnonPanel, BorderLayout.CENTER);

        // 実行パネル (プログレスバー + Executeボタン)
        JPanel execPanel = new JPanel(new BorderLayout(10, 0));
        execPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        
        btnExecute = new JButton("Execute Pixel & Attribute Anonymization");
        btnExecute.setPreferredSize(new Dimension(250, 40));
        btnExecute.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        btnExecute.addActionListener(this::onExecuteClicked);

        execPanel.add(progressBar, BorderLayout.CENTER);
        execPanel.add(btnExecute, BorderLayout.EAST);

        bottomContainer.add(execPanel, BorderLayout.SOUTH);
        
        JSplitPane rootVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rootVerticalSplit.setTopComponent(mainSplit);       // 上に画像エリア
        rootVerticalSplit.setBottomComponent(bottomContainer); // 下に属性設定エリア
        rootVerticalSplit.setResizeWeight(0.6);             // 初期状態で上のエリアを60%にする
        rootVerticalSplit.setContinuousLayout(true);
        rootVerticalSplit.setDividerSize(10);
        rootVerticalSplit.setOneTouchExpandable(true);      // 折りたたみボタンを有効化

        add(rootVerticalSplit, BorderLayout.CENTER);
    }

	private JPanel createMaskRoiListPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new TitledBorder("Mask ROIs"));

		// North: オプションボタン群
		JPanel optionsPanel = new JPanel(new GridLayout(3, 1, 2, 2));

		JButton btnApplyAll = new JButton("Apply to All Series");
		btnApplyAll.addActionListener(e -> {
			if (tempRois.isEmpty()) {
				JOptionPane.showMessageDialog(this, "No ROIs to apply.", "Information",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			DatabaseHandler db = DatabaseHandler.getInstance();
			if (db == null) {
				JOptionPane.showMessageDialog(this, "Databse no visible, please use it from GRAPHY.", "Warinig",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			// ツリーから「チェックがONの全シリーズ」を取得する（前回作成したメソッド）
			List<HashMap<String, String>> targetSeriesList = getTargetSeriesList();
			if (targetSeriesList.isEmpty())
				return;

			// 現在 Praparat で表示中のシリーズUIDを取得（スキップ用）
			Object[] uids = praparatViewer.getUIDs();
			if(uids == null) return;
			String currentSeriesUID = (String) uids[2];
			if(currentSeriesUID == null) return;

			int copiedCount = 0;

			for (HashMap<String, String> series : targetSeriesList) {
				String pid = series.get(ContextKey.PatientID.name());
				String studyUID = series.get(ContextKey.StudyInstanceUID.name());
				String seUID = series.get(ContextKey.SeriesInstanceUID.name());

				// 現在表示中のシリーズには既に描いてあるのでスキップ
				if (seUID.equals(currentSeriesUID)) {
					continue;
				}

				/*
				 * 画像の順番を保証する
				 */
				Praparat se = new Praparat(ViewMode.SingleGrid);
				se.loadSeries(pid, studyUID, seUID, null/* load all slice */);

				// カレントの各ROIをコピーして別シリーズに割り当てる
				List<RoiObj> temp = new ArrayList<RoiObj>(tempRois);
				for (RoiObj originalRoi : temp) {
					try {
						SlideGlass sg = originalRoi.getSlideGlass();
						if (sg == null) {
							continue;
						}
						int zctIdx = praparatViewer.getSlidePositionOnZCTIndex(sg);
						RoiObj clonedRoi = (RoiObj) originalRoi.clone();
						// 2. 所属するUIDsをターゲットのものに書き換え,DBに保存
						SlideGlass sg2 = se.getAllSlides().get(zctIdx);
						if (sg2 != null) {
							clonedRoi.setSlideGlass(sg2, true);
							sg2.addRoi(clonedRoi);
						}
						// 4. ダイアログ中断時のクリーンアップ対象（tempRois）にも追加しておく
						tempRois.add(clonedRoi);
						addMaskRoiPanel(clonedRoi, se);
						copiedCount++;
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}

			JOptionPane.showMessageDialog(
					this, "Masks successfully applied to " + (targetSeriesList.size() - 1) + " other series.\n"
							+ "(Total " + copiedCount + " ROIs copied)",
					"Apply to All", JOptionPane.INFORMATION_MESSAGE);
		});
        
		JToggleButton btnTogglePreview = new JToggleButton("Preview Mask as Blackout");
		btnTogglePreview.addActionListener(e -> {
			boolean isPreviewMode = btnTogglePreview.isSelected();
			for (RoiObj roi : tempRois) {
				if (isPreviewMode) {
					// ★ 黒塗りに変更（メソッド名は適宜変更してください）
					roi.setStrokeColor(Color.BLACK);
					// 塗りつぶしを黒＆不透明(Alpha:255)に設定する例
					roi.setFillColor(new Color(0, 0, 0, 255));
				} else {
					// ★ 元の色（半透明など）に戻す
					roi.setStrokeColor(null); // 元のデフォルト色
					// 塗りつぶしを半透明に戻す例
					roi.setFillColor(new Color(255, 255, 0, 50));
				}
			}

			// Praparat全体を再描画して色変更を反映
			if (praparatViewer != null) {
				praparatViewer.repaint();
			}
		});
        
        JButton btnClearAll = new JButton("Clear All ROIs");
        btnClearAll.addActionListener(e -> {
            if (tempRois.isEmpty()) return;

            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to clear all ROIs in the current series?",
                    "Clear All ROIs",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (result == JOptionPane.YES_OPTION) {
                // 現在 Praparat で表示中のシリーズUIDを取得
            	Object[] uids = praparatViewer.getUIDs();
            	if(uids == null) {
            		return;
            	}
                String currentSeriesUID = (String) uids[2];
                if (currentSeriesUID == null) return;

                // 削除対象となるROIを一時的に集めるリスト
                List<RoiObj> roisToRemove = new ArrayList<>();

                // 1. tempRois の中から、カレントシリーズに属するROIだけを抽出
                for (RoiObj roi : tempRois) {
                    String roiSeriesUID = roi.getProperty(ContextKey.SeriesInstanceUID.name());
                    
                    // UIDが一致する場合（または未設定の場合も現在のものとみなす安全策）
                    if (roiSeriesUID == null || roiSeriesUID.equals(currentSeriesUID)) {
                        roisToRemove.add(roi);
                    }
                }

                // 2. 抽出したROIを画面(SlideGlass)、DB、そして追跡リストから削除
                for (RoiObj roi : roisToRemove) {
                    SlideGlass sg = roi.getSlideGlass();
                    if (sg != null) {
                        sg.deleteRoi(roi); // CanvasGlass/SlideGlassから消す
                    }
                    // deleteRoiFromDatabase(roi); // DBからも消す
                    
                    tempRois.remove(roi); // 追跡リストから除外
                }
                
                // 3. UIリストの再構築（現在のシリーズのものだけ表示するため）
                updateMaskRoiListForCurrentSeries(); 
            }
        });

        optionsPanel.add(btnApplyAll);
        optionsPanel.add(btnTogglePreview);
        optionsPanel.add(btnClearAll);
        panel.add(optionsPanel, BorderLayout.NORTH);

        // Center: ROIパネルを追加していくコンテナ
        maskRoiListPanel = new JPanel();
        maskRoiListPanel.setLayout(new BoxLayout(maskRoiListPanel, BoxLayout.Y_AXIS));
        
        JScrollPane scrollPane = new JScrollPane(maskRoiListPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
	
	/**
     * 現在表示中のシリーズに属するROIだけを右側のリスト(maskRoiListPanel)に表示する
     */
    private void updateMaskRoiListForCurrentSeries() {
        if (maskRoiListPanel == null || praparatViewer == null) return;
        
        Object[] uids = praparatViewer.getUIDs();
    	if(uids == null) {
    		return;
    	}
        String currentSeriesUID = (String) uids[2];
        if (currentSeriesUID == null) return;
        
        maskRoiListPanel.removeAll();
        
        for (RoiObj roi : tempRois) {
            String roiSeriesUID = roi.getProperty(ContextKey.SeriesInstanceUID.name());
            if (roiSeriesUID == null || roiSeriesUID.equals(currentSeriesUID)) {
                // UIパネルを再生成して追加する
                addMaskRoiPanel(roi, praparatViewer); 
            }
        }
        
        maskRoiListPanel.revalidate();
        maskRoiListPanel.repaint();
    }

    private void onExecuteClicked(ActionEvent e) {
    	this.executing = true;
    	List<HashMap<String,String>> targets = getTargetSeriesList();
        // TODO: バッチ処理パイプラインの実行
        // 1. ツリーから Exclude されていないシリーズのリストを取得
        // 2. Tempフォルダへ画像をコピーし、ROI座標を元にピクセルを0埋め (E.3.1)
        // 3. attrAnonPanel から出力先と設定 (currentConfig) を取得
        // 4. DicomAnonymizerEngine.transcodeDirectory() を Temp -> Dest で実行
    }
    
    /**
     * DBなどから取得したスタディ情報を受け取り、ツリーを構築する
     * @param study 入力された1つのスタディ
     */
    public void loadStudyData(DICOMNode study) {
        if (study == null) return;
        
        DatabaseHandler db = DatabaseHandler.getInstance();
        if(db == null) {
        	Log.logger.log(Level.SEVERE, "Graphy DB cannot found !");
        	return;
        }
        
        String pid = study.getData(DICOMNode.PatientID);
        String studyUID = study.getData(DICOMNode.StudyInstanceUID);
        
        if(db.getNumOfSeries(pid, studyUID) <= 0) {
        	Log.logger.log(Level.SEVERE, "This study does not have any series... please check DB records !");
        	return;
        };

        // 1. Rootノードを作成（UI上は非表示にします）
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

        // 2. スタディノードの作成
        String studyLabel = String.format("Study: %s (%s,%s)", study.getData(DICOMNode.PatientName), study.getData(DICOMNode.StudyDate), study.getData(DICOMNode.Modality));
        StudyCheckBoxTree.CheckBoxNode studyNode = new StudyCheckBoxTree.CheckBoxNode(studyLabel, null);

        // 3. シリーズノードの作成と追加
        List<HashMap<String, String>> seriesList = db.getSeriesInfoByUIDs(pid, studyUID);
        for (HashMap<String, String> seriesInfo : seriesList) {
            String seriesLabel = String.format("Series %s: %s [%s] (%s imgs)", 
                    seriesInfo.get("SeriesNumber"), seriesInfo.get("SeriesDescription"), seriesInfo.get("Modality"), seriesInfo.get("NumOfInstanceInSeries"));
            
            StudyCheckBoxTree.CheckBoxNode seriesNode = new StudyCheckBoxTree.CheckBoxNode(seriesLabel, seriesInfo);
            studyNode.add(seriesNode);
        }

        root.add(studyNode);

        // 4. ツリーモデルを更新
        DefaultTreeModel model = new DefaultTreeModel(root);
        studyTree.setModel(model);

        // 5. Rootを隠して、スタディを最上位として見せる
        studyTree.setRootVisible(false);
        studyTree.setShowsRootHandles(true);

        // 全ノードを展開状態にする
        expandAllNodes(studyTree, 0, studyTree.getRowCount());
    }
    
    protected void loadSeriesToPraparat(HashMap<String, String> seriesInfo) {
        // 1. Praparat にシリーズの画像データをセットする
    	String pid = seriesInfo.get("PatientID");
		String studyUid = seriesInfo.get("StudyInstanceUID");
		String seriesUid = seriesInfo.get("SeriesInstanceUID");
		if(!praparatViewer.isLoaded(pid, studyUid, seriesUid)) {
			praparatViewer.loadSeries(pid, studyUid, seriesUid, null);
			SwingUtilities.invokeLater(()->{
//				praparatViewer.setImagePositionUsingSlider(0);
//				praparatViewer.updateViewPanel();
				praparatViewer.doSingleGridLayout();
				praparatViewer.loadRoisFromDB();
			});
		}
		
        // 2. ロード直後はツールをデフォルト（ポインターなど）に戻す
        toolButtonGroup.clearSelection();
        // praparatViewer.setToolMode(Praparat.ToolMode.POINTER);
        
        // 3. （オプション）以前描画したROIがこのシリーズ用にあればリストを更新する
        // updateMaskRoiListForCurrentSeries();
        
    }

    // ツリーをすべて展開するヘルパーメソッド
    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; ++i) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }
    
    /**
     * ROIツールバーのボタンを作成するヘルパーメソッド
     */
	private JToggleButton createToolButton(String name, int toolType, boolean selected) {
		JToggleButton btn = new JToggleButton(name, selected);
		toolButtonGroup.add(btn);

		btn.addActionListener(e -> {
			if (btn.isSelected()) {
				System.out.println("ToolType: "+toolType);
				praparatViewer.setLocalToolType(toolType);
			}
		});
		// 初期選択状態のボタンなら、生成時にツールをセットしておく
		if (selected) {
			praparatViewer.setLocalToolType(toolType);
		}
		return btn;
	}
    
    /**
     * Praparat 上で ROI が作成/削除された時のリスナーを設定する
     */
    private void setupPraparatRoiListener() {
    	
    	RoiObjListener listener = new RoiObjListener() {
			
    		@Override
            public void roiModified(SlideGlass sg , int actionId) {
            	RoiObj currentRoi = sg.getActiveRoi();
            	
//            	System.out.println("Listen !");
            	if(currentRoi == null) {
            		return;
            	}
            	
            	/*
				 * どのアクションであっても、念の為追跡する
				 * 追跡リストに記録
				 */
            	if(currentRoi != null) {
            		if(!tempRois.contains(currentRoi)) {
            			tempRois.add(currentRoi);
            		}
            	}
            	
            	if (currentRoi.getState() == RoiObj.CONSTRUCTING) {
                    return;
                }
            	
            	if(actionId == RoiObjListener.MODIFIED || actionId == RoiObjListener.COMPLETED) {
            		SwingUtilities.invokeLater(() -> {
                        addMaskRoiPanel(currentRoi, praparatViewer);
                        currentRoi.setFillColor(new Color(255, 255, 0, 50));
                        currentRoi.setFillState(true);
                    });
            	}
            	
				if (actionId == RoiObjListener.SELECTED) {
					SwingUtilities.invokeLater(() -> {
				        for (Component comp : maskRoiListPanel.getComponents()) {
				            // コンポーネントが MaskRoiPanel クラスであるか確認
				            if (comp instanceof MaskRoiPanel) {
				                MaskRoiPanel panel = (MaskRoiPanel) comp;
				                panel.setBackground(null);
				            }
				        }
						MaskRoiPanel panel = getMaskRoiPanelByRoi(currentRoi);
						if (panel != null) {
							// 例: 背景色を変えたり、ボーダーを太くして目立たせる
							panel.setBackground(Color.YELLOW);
							// スクロールバーを自動で動かして、そのパネルを見える位置に持ってくる
							maskRoiListPanel.scrollRectToVisible(panel.getBounds());
						}
					});
				}
            	
            	if(actionId == RoiObjListener.DELETED) {
            		tempRois.remove(currentRoi);
                    SwingUtilities.invokeLater(() -> {
                        removeMaskRoiPanelByRoi(currentRoi);
                    });
            	}
            }
		};
		
		RoiObj.addRoiListener(listener);
    }

	
	private void addMaskRoiPanel(RoiObj targetRoi, Praparat pp) {
		
		if(targetRoi == null) {
			return;
		}
		
		if(getMaskRoiPanelByRoi(targetRoi) != null) {
			//already added
			return;
		}
		
		if(pp.getCurrentSlide() == null) {
			return;
		}
		
		String sNo = "Unknown";
		String sDesc = "NoSeDesc";
		
		if(pp.getCurrentSlide().getHeader() != null) {
			sNo = pp.getCurrentSlide().getHeader().getString(Tag.SeriesNumber);
	        sDesc = pp.getCurrentSlide().getHeader().getString(Tag.SeriesDescription);
		}
		
        if (sNo == null) sNo = "Unknown";
        String seriesLabel = "Series " + sNo + (sDesc != null ? ": " + sDesc : "");

        // 座標(ZCT)から現在のスライス番号を取得 (先程のサブタスクを活用)
        int currentSlice = pp.getCurrentSlideZCTIndex();
        // 改良した MaskRoiPanel を生成
        MaskRoiPanel roiPanel = new MaskRoiPanel(targetRoi, seriesLabel, currentSlice, new MaskRoiPanel.MaskRoiPanelListener() {
        	@Override
			public void onRemoveRequested(MaskRoiPanel panel) {
        		RoiObj r = panel.getAttachedRoi();
        		SlideGlass sg = r.getSlideGlass();
        		if(sg != null) {
        			sg.deleteRoi(r);
        			sg.repaint();
        		}

				// 2. リストUIからこのパネル自身を削除
				maskRoiListPanel.remove(panel);
				maskRoiListPanel.revalidate();
				maskRoiListPanel.repaint();

				// 3. (必要なら) 一時追跡リストから削除＆DBクリーンアップ
				tempRois.remove(panel.getAttachedRoi());
			}
            @Override
            public void onRangeChanged(MaskRoiPanel panel) {
                // 変更ロジック...
            }
        });

        maskRoiListPanel.add(roiPanel);
        maskRoiListPanel.revalidate();
        maskRoiListPanel.repaint();
	}
    
    private void removeMaskRoiPanelByRoi(RoiObj targetRoi) {
        MaskRoiPanel targetPanel = getMaskRoiPanelByRoi(targetRoi);
        if (targetPanel != null) {
            maskRoiListPanel.remove(targetPanel);
            maskRoiListPanel.revalidate();
            maskRoiListPanel.repaint();
        }
    }
    
    /**
     * ツリーから「チェックがON」になっている SeriesEntity のリストを抽出する
     */
    public List<HashMap<String,String>> getTargetSeriesList() {
        List<HashMap<String,String>> targetSeries = new ArrayList<>();
        
        DefaultTreeModel model = (DefaultTreeModel) studyTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        if (root == null || root.getChildCount() == 0) return targetSeries;

        // スタディノード（Rootの子）を取得
        StudyCheckBoxTree.CheckBoxNode studyNode = (StudyCheckBoxTree.CheckBoxNode) root.getChildAt(0);

        // ※もしスタディ全体のチェックが外れていれば、対象シリーズはゼロとする仕様の場合
        if (!studyNode.isSelected()) {
            return targetSeries;
        }

        // シリーズノードをループ
        for (int i = 0; i < studyNode.getChildCount(); i++) {
            StudyCheckBoxTree.CheckBoxNode seriesNode = (StudyCheckBoxTree.CheckBoxNode) studyNode.getChildAt(i);
            
            if (seriesNode.isSelected()) {
                // CheckBoxNode に保持させておいたエンティティを取り出す
				HashMap<String,String> entity = (HashMap<String,String>) seriesNode.seriesInfo;
                targetSeries.add(entity);
            }
        }
        
        return targetSeries;
    }
    
    /**
     * 指定されたRoiオブジェクトに紐づくMaskRoiPanelをリストから探して取得する
     * * @param targetRoi 探したいRoiオブジェクト
     * @return 見つかった場合はそのMaskRoiPanel、見つからない場合はnull
     */
    private MaskRoiPanel getMaskRoiPanelByRoi(RoiObj targetRoi) {
        // パネルがまだない、または引数がnullの場合は早期リターン
        if (targetRoi == null || maskRoiListPanel == null) {
            return null;
        }

		// maskRoiListPanel に追加されているすべてのコンポーネントを走査
		for (Component comp : maskRoiListPanel.getComponents()) {
			// コンポーネントが MaskRoiPanel クラスであるか確認
			if (comp instanceof MaskRoiPanel) {
				MaskRoiPanel panel = (MaskRoiPanel) comp;
				RoiObj roi = panel.getAttachedRoi();
				// パネルが保持している Roi と、引数の Roi のインスタンスが同一か判定
				if ( roi == targetRoi) {
					return panel; // 見つかったら返す
				}
				
				if(roi.equals(targetRoi)) {
					return panel;
				}
				
			}
		}
        return null; // 最後まで見つからなかった場合
    }
    
    /**
     * このパネルで作成された一時ROIをすべてDBから削除する
     */
    public void cleanupTemporaryRois() {
        for (RoiObj roi : tempRois) {
            try {
            	SlideGlass sg = roi.getSlideGlass();
            	if(sg != null) {
            		sg.deleteRoi(roi);
            	}
            } catch (Exception ex) {
                System.err.println("Failed to delete temp ROI: " + ex.getMessage());
            }
        }
        tempRois.clear(); // 削除完了したらリストを空にする
    }
    
    public boolean isExecuting() {
        return executing;
    }

    public void stopProcess() {
        // SwingWorkerなどをキャンセルするロジック
        // if (worker != null) worker.cancel(true);
        executing = false;
    }
}