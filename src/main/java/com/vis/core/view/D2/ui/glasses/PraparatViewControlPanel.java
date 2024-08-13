package com.vis.core.view.D2.ui.glasses;

import java.awt.Component;
import java.awt.FlowLayout;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.vis.configuration.Resources;
import com.vis.core.facade.WindowManager;
import com.vis.core.util.ImageUtils;
import com.vis.core.util.Utils;
import com.vis.core.view.D2.ui.glasses.Praparat.ViewMode;

import ij.IJ;

import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

/*
 * Praparat north panel
 * show x,y pixel value
 * enable show text and roi
 * switch single view or film view
 */
public class PraparatViewControlPanel extends JPanel implements ItemListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = -4152267873732301107L;
	private JLabel stateInfoLabel;
	private final String pixelInfo[] = { "X:", "Y:", "Value:" };
	private JCheckBox showROI;
	private JCheckBox showInfo;
	private JCheckBox processSeries;
	private JButton filmGridBtn;
	private int currentColumnSize = -1;
	private final Praparat pp;

	public PraparatViewControlPanel(Praparat pp) {
		setContents();
		this.pp = pp;
	}

	private void setContents() {
		GridLayout layout = new GridLayout(1, 2);// row,col
		setLayout(layout);
		JPanel left = new JPanel();
		FlowLayout flLeft = new FlowLayout(FlowLayout.LEFT);
		left.setLayout(flLeft);
		JPanel right = new JPanel();
		FlowLayout flRight = new FlowLayout(FlowLayout.RIGHT);
		right.setLayout(flRight);
		stateInfoLabel = new JLabel(pixelInfo[0] + "-" + pixelInfo[1] + "-" + pixelInfo[2] + "-");
//		stateInfoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		ImageIcon overlayIcon = Resources.OverlayIcon.loadIconFromResource();
		Image overlayImg = ImageUtils.resize(overlayIcon.getImage(), 20, 20);
		showROI = new JCheckBox(new ImageIcon(overlayImg));
		showROI.setName("roi");
//		showROI.setDisabledIcon(icon...);//set opaque ?
		showROI.setSelected(true);
		showROI.addItemListener(this);
		showInfo = new JCheckBox("info");
		showInfo.setName("text");
//		showInfo.setDisabledIcon(icon...);//set opaque ?
		showInfo.setSelected(true);
		showInfo.addItemListener(this);
		filmGridBtn = new JButton(Resources.TileLayoutIcon.loadIconFromResource());
		filmGridBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				int newCol = setFilmGridLayout();
				if (newCol != -1) {
					pp.doFilmGridLayout(newCol);
				}else{
					//reset to single grid
					pp.doSingleGridLayout();
				}
			}
		});
		processSeries = new JCheckBox("series");
		processSeries.setSelected(true);
		processSeries.setName("series");
		processSeries.addItemListener(this);
		left.add(stateInfoLabel);
		right.add(processSeries);
		right.add(showROI);
		right.add(showInfo);
		right.add(filmGridBtn);
		add(left);
		add(right);
	}

	protected void setText2InfoLabel(int x, int y, String value, double scale, double mag, double rotate) {
		
		int decimalPlaces = 1;
		String xStg = String.valueOf(x);
		String yStg = String.valueOf(y);
		String valueStg = value;
		String scaleStg = String.valueOf(scale);
		String magStg = String.valueOf(mag);
		String rotateStg = String.valueOf(rotate);
		
		if(xStg.equals("-1")) {
			xStg = "-";
		}
		if(yStg.equals("-1")) {
			yStg = "-";
		}
		if(valueStg == null || valueStg.equals("-1")) {
			valueStg = "-";
		}
		if(scale == -1.) {
			scaleStg = "-";
		}else {
			scaleStg = IJ.d2s(scale, decimalPlaces);
		}
		if(mag == -1.) {
			magStg = "-";
		}else {
			magStg = IJ.d2s(mag, decimalPlaces);
		}
		if(rotate == -1.) {
			rotateStg = "-";
		}else {
//			rotateStg = IJ.d2s(rotate, decimalPlaces);
			rotateStg = IJ.d2s(rotate, 0);
		}
		ArrayList<String> info = new ArrayList<>();
		info.add(this.pixelInfo[0]);
		info.add(xStg + " ");// X
		info.add(this.pixelInfo[1]);
		info.add(yStg + " ");// Y
		info.add(this.pixelInfo[2]);
		info.add(valueStg + " ");// value
		info.add("s:");
		info.add(scaleStg + " ");
		info.add("z:");
		info.add(magStg + " ");
		info.add("r:");
		info.add(rotateStg + " ");
		StringBuilder infoLabel = new StringBuilder();
		for (String str : info) {
			infoLabel.append(str);
		}
		stateInfoLabel.setText(null);
		stateInfoLabel.setText(infoLabel.toString());
		stateInfoLabel.repaint();
	}

	public JButton getFilmGridBtn() {
		return filmGridBtn;
	}
	
	public void enableShowInfo(boolean enable) {
		showInfo.setEnabled(enable);
	}
	
	public void enableShowROI(boolean enable) {
		showROI.setEnabled(enable);
	}
	
	public void enableProcessSeries(boolean enable) {
		processSeries.setEnabled(enable);
	}
	
	public void showInfoText(boolean show) {
		showInfo.setSelected(show);
	}
	
	public void showROI(boolean show) {
		showROI.setSelected(show);
	}
	
	public void setProcessSeries(boolean on) {
		processSeries.setSelected(on);
	}

	public int setFilmGridLayout() {
		Object[] options1 = { "OK", "CLOSE" };
		JPanel panel = new JPanel();
		panel.add(new JLabel("Choose number of cols between 1 and 12"));
		JComboBox<String> num = null;
		if(pp.getViewMode() != ViewMode.FilmGrid) {
			num = new JComboBox<String>(new String[] {"1","2","3","4","5","6","7","8","9","10", "11", "12","Reset"});
		}else {
			num = new JComboBox<String>(new String[] {"1","2","3","4","5","6","7","8","9","10", "11", "12"});//remove reset
		}
		panel.add(num);
		int result = JOptionPane.showOptionDialog(WindowManager.getMainScreen(), panel, "Choose a Num of Columns", JOptionPane.YES_NO_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, options1, null);
		if (result == JOptionPane.YES_OPTION) {
			try{
				String selected = (String)num.getSelectedItem();
				currentColumnSize = Integer.parseInt(selected);
			}catch(NumberFormatException e) {
				//Reset was selected
				return -1;
			}
		}else if(result == JOptionPane.NO_OPTION) {
			//do nothing
		}
		return currentColumnSize;
	}

	public boolean isShowRoi() {
		return showROI.isSelected();
	}

	public boolean isShowInfo() {
		return showInfo.isSelected();
	}
	
	public boolean processSeries() {
		return processSeries.isSelected();
	}

	@Override
	public void itemStateChanged(ItemEvent ie) {
		String name = ((Component) ie.getSource()).getName();
		if(name.equals("roi")) {
			if(Utils.isDebug) System.out.println("roi item changed "+isShowRoi());
			pp.setAnnotationVisible(isShowRoi());
		}else if(name.equals("text")) {
			if(Utils.isDebug) System.out.println("text item changed "+isShowInfo());
			pp.setTextVisible(isShowInfo());
		}else if(name.equals("series")) {
			if(Utils.isDebug) System.out.println("process series changed "+processSeries());
			if(processSeries()) {
				pp.resetWindow();
			}
		}
	}
}
