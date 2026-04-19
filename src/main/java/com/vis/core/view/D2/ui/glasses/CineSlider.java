/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.core.view.D2.ui.glasses;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Hashtable;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;

/**
 * 
 * Cine slider and color bar.
 * 
 * @author tatsunidas
 *
 */
public class CineSlider extends JPanel implements ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private CineSliderHelper slider;
	private JCheckBox check;
	private Praparat pp = null;
	private Timer timer;
	private int frame = 100;
	private ColorBar colorBar;
	private int currentSliceIndex = -1;//1 to n, -1 is needed to initialize.

	public CineSlider(Praparat pp) {
		super();
		setLayout(new BorderLayout());
		timer = new Timer(this.frame, this);
		timer.setCoalesce(true);
		this.pp = pp;
		check = new JCheckBox(Resources.CineStartIcon.loadIconFromResource());
		check.setSelectedIcon(Resources.CineStopIcon.loadIconFromResource());
		check.setSelected(false);
		check.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (check.isSelected()) {
					timer.setDelay(frame);
					startAnimation();
				} else {
					stopAnimation();
				}
			}
		});
		add(check, BorderLayout.WEST);
		slider = new CineSliderHelper();
		add(slider, BorderLayout.CENTER);
		// color bar
		colorBar = new ColorBar(pp,128, 10);
		add(colorBar, BorderLayout.NORTH);
	}

	/**
	 * 1 to n.
	 * @return
	 */
	int getCurrentSliceIndex() {
		//slider value is 1 to n
		return slider.getValue();
	}

	protected void setSlice(int ind/*0 to n-1*/) {
		if (ind >= pp.getNumberOfImages()) {
			ind = 0;
		} else if (ind < 0) {
			ind = pp.getNumberOfImages()-1;
		}
		int sliderPos = ind + 1;//slider value is 1 to n
		slider.setValue(sliderPos);
		pp.repaint();
	}

	public void initContext() {
		slider.initContext();
	}

	public void startAnimation() {
		// Start (or restart) animating!
		if (timer == null) {
			return;
		}
		if (timer.isRunning()) {
			return;
		}
		timer.start();
	}

	public void stopAnimation() {
		// Stop the animating thread.
		if (!timer.isRunning()) {
			return;
		}
		timer.stop();
	}

	// Fired when called Timer.
	@Override
	public void actionPerformed(ActionEvent e) {
		int nextSlice = getCurrentSliceIndex() + 1;
		setSlice(nextSlice);//0 to n-1
	}

	class CineSliderHelper extends JSlider implements ChangeListener {

		private static final long serialVersionUID = 1L;
		
		boolean initializing = false;

		public CineSliderHelper() {
			addChangeListener(this);
		}

		public void initContext() {
			if (pp.getNumberOfImages() < 0) {
				if (initializing) {
					initializing = false;
				}
				return;
			}
			initializing = true;// to ignore fire state changed when starting-up.
			setLabelTable(null);// needed to update slider ui
			setMinimum(1);
			
			try {
				setMaximum(pp.getNumberOfImages());
			}catch(java.lang.NullPointerException e) {
				// continue
				Log.logger.fine(e.getLocalizedMessage());
			}
			
			int majorTickSpacing = pp.getNumberOfImages() / 10;
			/*
			 * MinorTickSpace is linked to the count of wheel moves.
			 * One wheel move is one slice move.
			 */
			int minorTickSpacing = 1;
			if (majorTickSpacing <= 1) {
				majorTickSpacing = 1;
			}
			setMajorTickSpacing(majorTickSpacing);
			setMinorTickSpacing(minorTickSpacing);

			createLabelTableAndSet(majorTickSpacing, pp.getNumberOfImages());
			setPaintTicks(true);
			setPaintLabels(true);
			setSnapToTicks(true);
			initializing = false;
			/* if slider inputed same index, not fire state change. */
			currentSliceIndex = -1;
			setValue(currentSliceIndex);
		}
		
		private void createLabelTableAndSet(int majorTickSpacing, int numOfSlices) {
			if (numOfSlices <= 0) {
				return;
			}
			// Create the label table
			Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
			labelTable.put(Integer.valueOf(1), new JLabel("1"));// start
			labelTable.put(Integer.valueOf(numOfSlices), new JLabel(String.valueOf(numOfSlices)));// end
			if (numOfSlices < majorTickSpacing) {
				setLabelTable(labelTable);
			} else {
				// this case means num of slices less than 5.
				double numOfTick = numOfSlices / majorTickSpacing;
				BigDecimal bd = new BigDecimal(String.valueOf(numOfTick));
				BigDecimal bd1 = bd.setScale(0, RoundingMode.DOWN);// cut off under decimal point.
				int numOfTickInt = bd1.intValue();
				for (int i = 1; i <= numOfTickInt; i++) {
					labelTable.put(Integer.valueOf(i * majorTickSpacing),
							new JLabel(String.valueOf(i * majorTickSpacing)));
				}
				setLabelTable(labelTable);
			}
		}
		
		
		/*
		 * paging
		 */
		@Override
		public void stateChanged(ChangeEvent e) {
			if (pp.getNumberOfImages() < 1) {
				return;
			}
			if(initializing) {
				return;
			}
			JSlider source = (JSlider) e.getSource();
			
			// ★ ここがポイント：ドラッグ中（調整中）はスキップし、動かし終わった時だけ処理する
			if (!source.getValueIsAdjusting()) {
				int nextpos = (int) source.getValue();// 1 to n
				/*
				 * When switch GridView, as possible same slice position.
				 */
				currentSliceIndex = nextpos;
				pp.setImagePosition(currentSliceIndex-1);//0 to n-1
				pp.callBackLocalizer();
			}
		}
	}
}
