package com.vis.core.view.D2.ui.glasses;

import javax.swing.JPanel;

import com.vis.core.view.D2.ui.orientation.ImageOrientation;
import com.vis.core.view.D2.ui.orientation.SubjectOrientation;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomObject;
import com.vis.dicom.Tag;

import java.awt.Graphics;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JLabel;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;

import org.joml.Vector3d;

@SuppressWarnings("serial")
public class TextOverlayGlass extends JPanel{

//	private TextOverlayParam textOverlayParam;
	private boolean textOverlay = true;
	
	String font = "Arial";
	int fontSize = 14;
	java.awt.Color lblColor = new java.awt.Color(255,255,255);//white
	java.awt.Color lblColorInvert = new java.awt.Color(50,50,50);//gray
	java.awt.Color directionLabelColor = new java.awt.Color(255,20,20);
	
	int parentWidth;
	int parentHeight;
	
	DicomObject header;
	
	//upper left
	private JLabel lbl1_1;
	private JLabel lbl1_2;
	private JLabel lbl1_3;
	private JLabel lbl1_4;
	private JLabel lbl1_5;
	//upper right
	private JLabel lbl2_1;
	private JLabel lbl2_2;
	private JLabel lbl2_3;
	private JLabel lbl2_4;
	private JLabel lbl2_5;
	//lower left
	private JLabel lbl3_1;
	private JLabel lbl3_2;
	private JLabel lbl3_3;
	private JLabel lbl3_4;
	private JLabel lbl3_5;
	//lower right
	private JLabel lbl4_1;
	private JLabel lbl4_2;
	private JLabel lbl4_3;
	private JLabel lbl4_4;
	private JLabel lbl4_5;
	private ArrayList<JLabel> listOfLabels;
	
	private HashMap<String, JLabel> directions = null;
	boolean invert = false;
	
	// debug
	public static void main(String args[]) {
//		DicomObject header = new DicomObject(path, false);
//		JLayeredPane p = new JLayeredPane();
//		JPanel p1 = new JPanel();
//		p1.setBackground(Color.BLACK);
//		p1.setSize(502, 482);//MUST
//		p.add(p1);
//		p.setLayer(p1, 0);
//		TextOverlayGlass to = new TextOverlayGlass(header);
//		p.add(to);
//		p.setLayer(to, 1);
//		JFrame f = new JFrame();
//		f.setSize(512,512);//MUST
//		f.setContentPane(p);
//		f.setVisible(true);
//		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		System.out.println(f.getContentPane().getSize().getWidth());
//		System.out.println(f.getContentPane().getSize().getHeight());
//		JFrame f2 = new JFrame();
//		f2.add(p.getComponentsInLayer(0)[0]);
//		f2.setSize(512,512);//MUST
//		f2.setContentPane(p);
//		f2.setVisible(true);
	}

	public TextOverlayGlass(DicomObject header) {
		this.header = header;
		initComponents();
//		this.textOverlayParam = new TextOverlayParam();//future work
		loadAnnotationList(header);
		loadDirection(header);
		setOpaque(false);
	}
	
	/**
	 * set information labels
	 */
	private void initComponents() {
		// TODO Auto-generated method stub
		lbl1_1 = new JLabel("");
		lbl1_1.setHorizontalAlignment(SwingConstants.LEFT);

		lbl1_2 = new JLabel("");
		lbl1_2.setHorizontalAlignment(SwingConstants.LEFT);

		lbl1_3 = new JLabel("");
		lbl1_3.setHorizontalAlignment(SwingConstants.LEFT);

		lbl1_4 = new JLabel("");
		lbl1_4.setHorizontalAlignment(SwingConstants.LEFT);

		lbl1_5 = new JLabel("");
		lbl1_5.setHorizontalAlignment(SwingConstants.LEFT);

		lbl2_1 = new JLabel("");
		lbl2_1.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl2_2 = new JLabel("");
		lbl2_2.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl2_3 = new JLabel("");
		lbl2_3.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl2_4 = new JLabel("");
		lbl2_4.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl2_5 = new JLabel("");
		lbl2_5.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl3_5 = new JLabel("");
		lbl3_5.setHorizontalAlignment(SwingConstants.LEFT);

		lbl3_4 = new JLabel("");
		lbl3_4.setHorizontalAlignment(SwingConstants.LEFT);

		lbl3_3 = new JLabel("");
		lbl3_3.setHorizontalAlignment(SwingConstants.LEFT);

		lbl3_2 = new JLabel("");
		lbl3_2.setHorizontalAlignment(SwingConstants.LEFT);

		lbl3_1 = new JLabel("");
		lbl3_1.setHorizontalAlignment(SwingConstants.LEFT);

		lbl4_5 = new JLabel("");
		lbl4_5.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl4_4 = new JLabel("");
		lbl4_4.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl4_3 = new JLabel("");
		lbl4_3.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl4_2 = new JLabel("");
		lbl4_2.setHorizontalAlignment(SwingConstants.RIGHT);

		lbl4_1 = new JLabel("");
		lbl4_1.setHorizontalAlignment(SwingConstants.RIGHT);
		GroupLayout groupLayout = new GroupLayout(this);
		groupLayout.setHorizontalGroup(groupLayout.createParallelGroup(Alignment.LEADING).addGroup(groupLayout
				.createSequentialGroup().addContainerGap()
				.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl1_1)
								.addPreferredGap(ComponentPlacement.RELATED, 394, Short.MAX_VALUE).addComponent(lbl2_1))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl1_2)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl2_2))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl1_3)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl2_3))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl1_4)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl2_4))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl1_5)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl2_5))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl3_5)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl4_5))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl3_4)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl4_4))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl3_3)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl4_3))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl3_2)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE).addComponent(lbl4_2))
						.addGroup(groupLayout.createSequentialGroup().addComponent(lbl3_1)
								.addPreferredGap(ComponentPlacement.RELATED, 350, Short.MAX_VALUE)
								.addComponent(lbl4_1)))
				.addContainerGap()));
		groupLayout.setVerticalGroup(groupLayout.createParallelGroup(Alignment.LEADING).addGroup(groupLayout
				.createSequentialGroup().addContainerGap()
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl1_1).addComponent(lbl2_1))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl1_2).addComponent(lbl2_2))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl1_3).addComponent(lbl2_3))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl1_4).addComponent(lbl2_4))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl1_5).addComponent(lbl2_5))
				.addPreferredGap(ComponentPlacement.RELATED, 260, Short.MAX_VALUE)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl3_1).addComponent(lbl4_1))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl3_2).addComponent(lbl4_2))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl3_3).addComponent(lbl4_3))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl3_4).addComponent(lbl4_4))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lbl3_5).addComponent(lbl4_5))
				.addContainerGap()));
		setLayout(groupLayout);

		listOfLabels = new ArrayList<>();
		listOfLabels.add(lbl1_1);
		listOfLabels.add(lbl1_2);
		listOfLabels.add(lbl1_3);
		listOfLabels.add(lbl1_4);
		listOfLabels.add(lbl1_5);
		listOfLabels.add(lbl2_1);
		listOfLabels.add(lbl2_2);
		listOfLabels.add(lbl2_3);
		listOfLabels.add(lbl2_4);
		listOfLabels.add(lbl2_5);
		listOfLabels.add(lbl3_1);
		listOfLabels.add(lbl3_2);
		listOfLabels.add(lbl3_3);
		listOfLabels.add(lbl3_4);
		listOfLabels.add(lbl3_5);
		listOfLabels.add(lbl4_1);
		listOfLabels.add(lbl4_2);
		listOfLabels.add(lbl4_3);
		listOfLabels.add(lbl4_4);
		listOfLabels.add(lbl4_5);
		for (JLabel lbl : listOfLabels) {
			lbl.setOpaque(false);
			lbl.setFont(new java.awt.Font(font, 0, fontSize));
			if(!invert) {
				lbl.setForeground(lblColor);
			}else {
				lbl.setForeground(lblColorInvert);
			}
		}
	}

	private void loadAnnotationList(DicomObject header) {
		if(header == null) {
			return;
		}
		ArrayList<Integer> tags = null;
		DatabaseHandler db = DatabaseHandler.getInstance();
		if(db == null) {
			tags = loadDefaultTextAnnotationTags();
		}else {
			tags = db.getTextAnnotationList();
		}
		if(tags != null && tags.size() > 0) {
			for(int i=0;i<tags.size();i++) {
				String value = header.getString(tags.get(i));
				if(value == null) {
					listOfLabels.get(i).setText("NULL");
				}else {
					listOfLabels.get(i).setText(value);
				}
			}
		}
	}
	
	private ArrayList<Integer> loadDefaultTextAnnotationTags(){
		ArrayList<Integer> tags = new ArrayList<>();
		tags.add(Tag.Patient​Name);
		tags.add(Tag.Patient​ID);
		tags.add(Tag.Patient​Birth​Date);
		tags.add(Tag.Patient​Sex);
		tags.add(Tag.Patient​Weight);
		
		tags.add(Tag.Study​Date);
		tags.add(Tag.Series​Number);
		tags.add(Tag.Study​Description);
		tags.add(Tag.Series​Description);
		tags.add(Tag.Instance​Number);
		
		tags.add(Tag.Pixel​Spacing);
		tags.add(Tag.Spacing​Between​Slices);
		
		return tags;
	}
	
	private void loadDirection(DicomObject dcm) {
		Vector3d row_vec = ImageOrientation.getRowImagePosition(dcm);
		Vector3d col_vec = ImageOrientation.getColumnImagePosition(dcm);
		if(row_vec == null || col_vec == null) {
			return;
		}
		//get orientation
		boolean biped = SubjectOrientation.isBiped(dcm);
		String xAxis_right_side_orientation = ImageOrientation.getOrientation(row_vec, !biped).substring(0,1);
		String xAxis_left_side_orientation = ImageOrientation.getImageOrientationOpposite(xAxis_right_side_orientation, !biped);
		String yAxis_lower_side_orientation = ImageOrientation.getOrientation(col_vec, !biped).substring(0, 1);
		String yAxis_upper_side_orientation = ImageOrientation.getImageOrientationOpposite(yAxis_lower_side_orientation, !biped);
		directions = new HashMap<>(4);
		directions.put("LEFT", new JLabel(xAxis_left_side_orientation));
		directions.put("RIGHT", new JLabel(xAxis_right_side_orientation));
		directions.put("TOP", new JLabel(yAxis_upper_side_orientation));
		directions.put("BOTTOM", new JLabel(yAxis_lower_side_orientation));
		directions.get("LEFT").setHorizontalAlignment(SwingConstants.LEFT);
		directions.get("LEFT").setVerticalAlignment(SwingConstants.CENTER);
		directions.get("RIGHT").setHorizontalAlignment(SwingConstants.RIGHT);
		directions.get("RIGHT").setVerticalAlignment(SwingConstants.CENTER);
		directions.get("TOP").setHorizontalAlignment(SwingConstants.CENTER);
		directions.get("TOP").setVerticalAlignment(SwingConstants.TOP);
		directions.get("BOTTOM").setHorizontalAlignment(SwingConstants.CENTER);
		directions.get("BOTTOM").setVerticalAlignment(SwingConstants.BOTTOM);
		setDirectionLabelsFacade();
	}
	
	public void setDirectionLabelsFacade() {
		if(directions == null) {
			return;
		}
		for(String k:directions.keySet()) {
			directions.get(k).setOpaque(false);
			directions.get(k).setFont(new java.awt.Font(font, 0, fontSize));
			directions.get(k).setForeground(directionLabelColor);
		}
	}
	
	public void setInvertState(boolean invert) {
		if (this.invert == false) {//if current state is no invert, set invert color
			if (listOfLabels != null) {
				for (JLabel lbl : listOfLabels) {
					lbl.setForeground(lblColorInvert);
				}
			}
		} else {//else set normal color
			if (listOfLabels != null) {
				for (JLabel lbl : listOfLabels) {
					lbl.setForeground(lblColor);
				}
			}
		}
		this.invert = invert;
	}

	private void setTextOverlayToNull() {
		
	}

	private void setTextOverlay() {
	}
	
	public void resizeHandler() {
        repaint();
    }
	
	private void drawDirection(Graphics g) {
		if(directions == null) {
			return;
		}
		int margin = 3;
		java.awt.Rectangle rect = new java.awt.Rectangle(0,0, getWidth(), getHeight());
		Font font_direc = new java.awt.Font(font, Font.BOLD,fontSize);
	    FontMetrics metrics = g.getFontMetrics(font_direc);// Get the FontMetrics after setFont
		g.setColor(directionLabelColor);
		g.setFont(font_direc);
		// LEFT
	    String direction = directions.get("LEFT").getText();
	    int x = rect.x + margin;
	    int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
	    g.drawString(direction, x, y);
	    //RIGHT
	    direction = directions.get("RIGHT").getText();
	    x = rect.x + (rect.width - metrics.stringWidth(direction) - margin);
	    y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
	    g.drawString(direction, x, y);
	    // TOP
	    direction = directions.get("TOP").getText();
	    x = rect.x + (rect.width - metrics.stringWidth(direction)) / 2;
	    y = rect.y + margin + metrics.getAscent();
	    g.drawString(direction, x, y);
	    // BOTTOM
	    direction = directions.get("BOTTOM").getText();
	    x = rect.x + (rect.width - metrics.stringWidth(direction)) / 2;
	    y = rect.y + rect.height - metrics.getHeight() + metrics.getAscent();;
	    g.drawString(direction, x, y);
	    
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		if (textOverlay) {
			setTextOverlay();
			drawDirection(g);
		} else {
			setTextOverlayToNull();
		}
	}
}
