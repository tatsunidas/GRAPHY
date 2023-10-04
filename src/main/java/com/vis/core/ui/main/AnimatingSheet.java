package com.vis.core.ui.main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.LineBorder;

import com.vis.configuration.ConfigInfo;
import com.vis.core.facade.WindowManager;

public class AnimatingSheet implements ActionListener, PropertyChangeListener {

	/* DialogAsSheet */
	public static final int INCOMING = 1;
	public static final int OUTGOING = -1;
	public static final float ANIMATION_DURATION = 500f;
	public static final int ANIMATION_SLEEP = 50;
	JComponent sheet;
	JPanel glass;
	AnimatingSheetPanel animatingSheetPanel;
	boolean animating;
	int animationDirection;
	Timer animationTimer;
	long animationStart;
	BufferedImage offscreenImage;
	JFrame mainFrame = (JFrame)WindowManager.getWindow(ConfigInfo.MainScreen.toString());

	public AnimatingSheet(String msg, int msgType) {
		JOptionPane optionPane = new JOptionPane(msg, msgType);
		optionPane.addPropertyChangeListener(this);
		JDialog dialog = optionPane.createDialog(mainFrame, "irrelevant");
		initAnimationSheet();
		showJDialogAsSheet(dialog);
	}

	private void initAnimationSheet() {
		glass = (JPanel) mainFrame.getGlassPane();
		glass.setLayout(new GridBagLayout());
		animatingSheetPanel = new AnimatingSheetPanel();
		animatingSheetPanel.setBorder(new LineBorder(Color.black, 1));
	}

	public JComponent showJDialogAsSheet(JDialog dialog) {
		sheet = (JComponent) dialog.getContentPane();
		sheet.setBorder(new LineBorder(Color.black, 1));
		/* 連続してインポートする際などは一度リセットされる。メインのグラスペインは1つしか無いので */
		glass.removeAll();
		animationDirection = INCOMING;
		startAnimation();
		return sheet;
	}

	public void hideSheet() {
		animationDirection = OUTGOING;
		startAnimation();
		// glass.setVisible(false);
	}

	private void startAnimation() {
		glass.repaint();
		// clear glasspane and set up animatingSheet
		animatingSheetPanel.setSource(sheet);
		glass.removeAll();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.NORTH;
		glass.add(animatingSheetPanel, gbc);
		gbc.gridy = 1;
		gbc.weighty = Integer.MAX_VALUE;
		glass.add(Box.createGlue(), gbc);
		glass.setVisible(true);

		// start animation timer
		animationStart = System.currentTimeMillis();
		if (animationTimer == null)
			animationTimer = new Timer(ANIMATION_SLEEP, this);
		animating = true;
		animationTimer.start();
	}

	private void stopAnimation() {
		animationTimer.stop();
		animating = false;
	}

	private void finishShowingSheet() {
		glass.removeAll();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.NORTH;
		glass.add(sheet, gbc);
		gbc.gridy = 1;
		gbc.weighty = Integer.MAX_VALUE;
		glass.add(Box.createGlue(), gbc);
		// glass.setVisible(true);
		glass.revalidate();
		glass.repaint();

		/* auto close */
		try {
			Thread.sleep(600);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		hideSheet();
	}

	class AnimatingSheetPanel extends JPanel {
		private static final long serialVersionUID = -8239731742856800730L;
		Dimension animatingSize = new Dimension(0, 1);
		JComponent source;
		BufferedImage offscreenImage;

		public AnimatingSheetPanel() {
			super();
			setOpaque(true);
		}

		public void setSource(JComponent source) {
			this.source = source;
			animatingSize.width = source.getWidth();
			makeOffscreenImage(source);
		}

		public void setAnimatingHeight(int height) {
			animatingSize.height = height;
			setSize(animatingSize);
		}

		private void makeOffscreenImage(JComponent source) {
			GraphicsConfiguration gfxConfig = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
					.getDefaultConfiguration();
			offscreenImage = gfxConfig.createCompatibleImage(source.getWidth(), source.getHeight());
			Graphics2D offscreenGraphics = (Graphics2D) offscreenImage.getGraphics();
			source.paint(offscreenGraphics);
		}

		public Dimension getPreferredSize() {
			return animatingSize;
		}

		public Dimension getMinimumSize() {
			return animatingSize;
		}

		public Dimension getMaximumSize() {
			return animatingSize;
		}

		public void paint(Graphics g) {
			// get the bottommost n pixels of source and
			// paint them into g, where n is height

			BufferedImage fragment = offscreenImage.getSubimage(0, offscreenImage.getHeight() - animatingSize.height,
					source.getWidth(), animatingSize.height);
			g.drawImage(fragment, 0, 0, this);
		}
	}

	@Override
	public void actionPerformed(ActionEvent act) {
		// TODO Auto-generated method stub
		if (animating) {
			// calculate height to show
			float animationPercent = (System.currentTimeMillis() - animationStart) / ANIMATION_DURATION;
			animationPercent = Math.min(1.0f, animationPercent);
			int animatingHeight = 0;
			if (animationDirection == INCOMING) {
				animatingHeight = (int) (animationPercent * sheet.getHeight());
			} else {
				animatingHeight = (int) ((1.0f - animationPercent) * sheet.getHeight());
			}
			// clip off that much from sheet and blit it
			// into animatingSheet
			animatingSheetPanel.setAnimatingHeight(animatingHeight);
			animatingSheetPanel.repaint();

			if (animationPercent >= 1.0f) {
				stopAnimation();
				if (animationDirection == INCOMING) {
					finishShowingSheet();
				} else {
					glass.removeAll();
					glass.setVisible(false);
				}
			}
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent pce) {
		if (pce.getPropertyName().equals(JOptionPane.VALUE_PROPERTY)) {
			System.out.println("Selected option " + pce.getNewValue());
			hideSheet();
		}
	}
}
