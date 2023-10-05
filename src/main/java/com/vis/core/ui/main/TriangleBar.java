package com.vis.core.ui.main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class TriangleBar extends JPanel {

	private JPanel contentPanel;

	public TriangleBar() {
		
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(10, 20));

		JLabel resizeIconLabel = new JLabel(new TriangleSquareWindowsCornerIcon());
		resizeIconLabel.setOpaque(false);

		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.setOpaque(false);
		rightPanel.add(resizeIconLabel, BorderLayout.SOUTH);
		add(rightPanel, BorderLayout.EAST);

		contentPanel = new JPanel();//keep flow layout
		contentPanel.setOpaque(false);
		FlowLayout l = (FlowLayout) contentPanel.getLayout();
		l.setAlignment(FlowLayout.LEADING);
		l.setVgap(2);
		l.setHgap(3);
		add(contentPanel, BorderLayout.CENTER);
		
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		int y = 0;
		g.setColor(new Color(156, 154, 140));
		g.drawLine(0, y, getWidth(), y);
		y++;
		g.setColor(new Color(196, 194, 183));
		g.drawLine(0, y, getWidth(), y);
		y++;
		g.setColor(new Color(218, 215, 201));
		g.drawLine(0, y, getWidth(), y);
		y++;
		g.setColor(new Color(223, 231, 217));
		g.drawLine(0, y, getWidth(), y);
		y = getHeight() - 3;
		g.setColor(new Color(223, 232, 218));
		g.drawLine(0, y, getWidth(), y);
		y++;
		g.setColor(new Color(223, 231, 216));
		g.drawLine(0, y, getWidth(), y);
		y = getHeight() - 1;
		g.setColor(new Color(221, 221, 220));
		g.drawLine(0, y, getWidth(), y);
	}

}

class TriangleSquareWindowsCornerIcon implements Icon {

	private final int WIDTH = 12;
	private final int HEIGHT = 12;

	final Color SQUARE_COLOR_LEFT = new Color(184, 180, 163);
	final Color SQUARE_COLOR_TOP_RIGHT = new Color(184, 180, 161);
	final Color SQUARE_COLOR_BOTTOM_RIGHT = new Color(184, 181, 161);
	final Color D3_EFFECT_COLOR = new Color(254, 254, 254);

	private void drawSquare(Graphics g, int x, int y) {
		Color oldColor = g.getColor();
		g.setColor(SQUARE_COLOR_LEFT);
		g.drawLine(x, y, x, y + 1);
		g.setColor(SQUARE_COLOR_TOP_RIGHT);
		g.drawLine(x + 1, y, x + 1, y + 1);
		g.setColor(SQUARE_COLOR_BOTTOM_RIGHT);
		g.drawLine(x + 1, y + 1, x + 1, y + 1);
		g.setColor(oldColor);
	}

	private void draw3DSquare(Graphics g, int x, int y) {
		Color oldColor = g.getColor();
		g.setColor(D3_EFFECT_COLOR);
		g.fillRect(x, y, 2, 2);
		g.setColor(oldColor);
	}

	@Override
	public int getIconHeight() {
		// TODO Auto-generated method stub
		return HEIGHT;
	}

	@Override
	public int getIconWidth() {
		// TODO Auto-generated method stub
		return WIDTH;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		// TODO Auto-generated method stub
		int rowDif = 4;// 2 square + 1 3D effect + 1 white space
		int columnDif = 4;
		int firstRow = 0;
		int firstColumn = 0;
		int secondRow = firstRow + rowDif;
		int secondColumn = firstColumn + columnDif;
		int thirdRow = secondRow + rowDif;// 2 square + 1 3D effect + 1 white space
		int thirdColumn = secondColumn + columnDif;
		// set 3d shadow
		// first col
		draw3DSquare(g, firstColumn + 1, thirdRow + 1);
		// second col
		draw3DSquare(g, secondColumn + 1, secondRow + 1);
		draw3DSquare(g, secondColumn + 1, thirdRow + 1);
		// third col
		draw3DSquare(g, thirdColumn + 1, firstRow + 1);
		draw3DSquare(g, thirdColumn + 1, secondRow + 1);
		draw3DSquare(g, thirdColumn + 1, thirdRow + 1);
		// set gray square
		// first col
		drawSquare(g, firstColumn, thirdRow);
		// second col
		drawSquare(g, secondColumn, secondRow);
		drawSquare(g, secondColumn, thirdRow);
		// third col
		drawSquare(g, thirdColumn, firstRow);
		drawSquare(g, thirdColumn, secondRow);
		drawSquare(g, thirdColumn, thirdRow);
	}
}
