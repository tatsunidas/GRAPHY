package com.vis.core.view.D2.ui.glasses;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Hashtable;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vis.configuration.Resources;

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
	private int currentSliceIndex = -1;//0 to n-1

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
				// TODO Auto-generated method stub
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

	public int getCurrentSliceIndex() {
		//slider value is 1 to n
		//slice index is 0 to n-1
		return slider.getValue()-1;
	}

	protected void setSlice(int ind) {
		if(currentSliceIndex == ind){
			return;
		}
		if (ind >= pp.getNumberOfImages()) {
			ind = 0;
		} else if (ind < 0) {
			ind = pp.getNumberOfImages() - 1;
		}
		int sliderPos = ind + 1;//slider value is 1 to n
		slider.setValue(sliderPos);
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

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public CineSliderHelper() {
		}

		public void initContext() {
			if (pp.getNumberOfImages() < 0) {
				return;
			} else {
				if (getChangeListeners() != null) {
					removeChangeListener(this);
				}
//				setMajorTickSpacing(10);//DO NOT USE
				setLabelTable(null);//needed to update slider ui
				setMinorTickSpacing(1);
				setMinimum(1);
				setMaximum(pp.getNumberOfImages());
				createLabelTableAndSet(10,pp.getNumberOfImages());
				setPaintTicks(true);
				setPaintLabels(true);
				setSnapToTicks(true);
				if(!isVisible()) {
					setValue(1);
					addChangeListener(this);
				}else {
					addChangeListener(this);
					setValue(1);//execute from listener
				}
				revalidate();
				repaint();
			}
		}
		
		private void createLabelTableAndSet(int majorTickSpacing, int numOfSlices){
			// Create the label table
			Hashtable<Integer,JLabel> labelTable = new Hashtable<Integer,JLabel>();
			labelTable.put(Integer.valueOf(1), new JLabel("1"));//start
			labelTable.put(Integer.valueOf(numOfSlices), new JLabel(String.valueOf(numOfSlices)));//end
			if(numOfSlices < majorTickSpacing) {
				setLabelTable(labelTable);
				return;
			}else {
				double numOfTick = numOfSlices/majorTickSpacing;
				BigDecimal bd = new BigDecimal(String.valueOf(numOfTick));
				BigDecimal bd1 = bd.setScale(0, RoundingMode.DOWN);//cut off under decimal point.
				int numOfTickInt = bd1.intValue();
				for(int i=1;i<=numOfTickInt;i++) {
					labelTable.put(Integer.valueOf(i*majorTickSpacing), new JLabel(String.valueOf(i*majorTickSpacing)));
				}
				setLabelTable(labelTable);
			}
		}
		
		/*
		 * calling paging by paging
		 */
		@Override
		public void stateChanged(ChangeEvent e) {
			if (pp.getNumberOfImages() < 0) {
				return;
			}
			JSlider source = (JSlider) e.getSource();
			int nextpos = (int) source.getValue();// 1 to n
			int currentPos = currentSliceIndex+1;
			if(nextpos != currentPos) {
				currentSliceIndex = nextpos-1;
				pp.setImagePosition(currentSliceIndex);//should be use "setImagePosition"
				pp.callBackLocalizer();
			}
		}
	}
}
