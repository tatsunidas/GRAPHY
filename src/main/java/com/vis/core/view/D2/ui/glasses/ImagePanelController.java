package com.vis.core.view.D2.ui.glasses;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

//public class ImagePanelController implements Runnable{

	/*
	 * 画像の圧縮タイプにも対応させたい。
	 * 今は非圧縮のみ対応。
	 */
//	SlideGlassView_old panel = null;
//	ArrayList<String> pathToImages = null;
//	ArrayList<BufferedImage> imageSet = null;
//	public int currentSlice = -1;
//	int totalSlice = -1;
//	
//	public ImagePanelController(SlideGlassView_old panel) {
//		this.panel = panel;
//	}
//	
//	public ImagePanelController(SlideGlassView_old panel,ArrayList<String> pathToImages) {
//		this.panel = panel;
//		this.pathToImages = pathToImages;
//	}
//	
//	public void setImageList(ArrayList<String> pathToImages) {
//		if(this.pathToImages != null) {
//			this.pathToImages = new ArrayList<>();
//		}
//		this.pathToImages = pathToImages;
//		this.currentSlice = 1;
//		this.totalSlice = pathToImages.size();
//		imageSet = new ArrayList<>();
//		//please check it ... tatsu
////		for(String path:this.pathToImages) {
////			try {
////				imageSet.add(DicomImageReader.readDicomFile(new File(path)));
////			} catch (IOException e) {
////				// TODO Auto-generated catch block
////				e.printStackTrace();
////			}
////		}
//	}
//	
//	/*
//	 * move to processing package??
//	 */
//	class SetImageLayerHandler implements Runnable{
//		ImagePanelController controller = null;
//		int sliceNum = 0;
//		public SetImageLayerHandler(ImagePanelController controller, int sliceNum) {
//			// TODO Auto-generated constructor stub
//			this.controller = controller;
//			this.sliceNum = sliceNum;
//		}
//		@Override
//		public void run() {
//			// TODO Auto-generated method stub
//			try {
//				this.controller.setLayers(sliceNum);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//	}
//	
//	class PagingHandler implements Runnable{
//		ImagePanelController controller = null;
//		boolean forward = true;
//		int rotation = 0;
//		public PagingHandler(ImagePanelController controller,  boolean forward, int rotation) {
//			// TODO Auto-generated constructor stub
//			this.controller = controller;
//			this.forward = forward;
//			this.rotation = rotation;
//		}
//		@Override
//		public void run() {
//			// TODO Auto-generated method stub
//			if(forward) {
//				this.controller.forwardPage(rotation);
//			}else {
//				this.controller.backwardPage(rotation);
//			}
//		}
//	}
//
//	public void paging(boolean forward, int rotation) {
//		// TODO Auto-generated method stub
//		Thread task = new Thread(new PagingHandler(this,forward,rotation));
//		task.start();
//		// ThreadTestクラスの処理が終了するまで待機の指示
//		try {
//			task.join();
//		} catch (InterruptedException e) {
//			// 例外処理
//			e.printStackTrace();
//		}
//	}
//	
//	public void setImage(int sliceNum) {
//		// TODO Auto-generated method stub
//		Thread task = new Thread(new SetImageLayerHandler(this,sliceNum));
//		task.start();
//		try {
//			task.join();
//		} catch (InterruptedException e) {
//			// 例外処理
//			e.printStackTrace();
//		}
//	}
//
//	public void backwardPage(int rotation) {
//		// TODO Auto-generated method stub
//		int num = currentSlice+rotation;
//		if(num <= 0) {
//			num = totalSlice;
//		}
//		try {
//			setLayers(num);
//			currentSlice = num;
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//
//	public void forwardPage(int rotation) {
//		// TODO Auto-generated method stub
//		int num = currentSlice+rotation;
//		if(num >= totalSlice+1) {
//			num = 1;
//		}
//		try {
//			setLayers(num);
//			currentSlice = num;
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//
//	/**
//	 * show layer contains image, annotations and text
//	 * @param sliceNum: equal or higher than 1 //not start 0.
//	 * @throws IOException
//	 */
//	LayeredCanvas2 l2 = null;
//	public synchronized void setLayers(int sliceNum) throws IOException {
//		// TODO Auto-generated method stub
////		System.out.println(pathToImages.get(sliceNum));
//		if(pathToImages == null) {
//			return;
//		}
//		this.panel.resetView();
////		this.panel.add(l2);
//		this.panel.repaint();
//		this.panel.revalidate();
//		currentSlice = sliceNum;
//  }
//	
//	@Override
//	public void run() {
//		// TODO Auto-generated method stub
//		//do nothing
//	}
//}
