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
package com.vis.core.ui.main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class InformationBar extends JPanel {

	private JPanel contentPanel;
	private JProgressBar progress;
	
	private final String progressName = "MainWindowProgress";

	public InformationBar() {
		
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(10, 20));

		JLabel triangle = new JLabel(new TriangleSquareWindowsCornerIcon());
		triangle.setOpaque(false);

		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.setOpaque(false);
		rightPanel.add(triangle, BorderLayout.SOUTH);
		add(rightPanel, BorderLayout.EAST);

		contentPanel = new JPanel();//keep flow layout
		contentPanel.setOpaque(false);
		FlowLayout l = (FlowLayout) contentPanel.getLayout();
		l.setAlignment(FlowLayout.LEADING);
		l.setVgap(2);
		l.setHgap(3);
		
		DateTimeLabel dateTimeLabel = new DateTimeLabel();
       dateTimeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
       contentPanel.add(dateTimeLabel);
       
		add(contentPanel, BorderLayout.CENTER);
		
	}
	
	public void initProgressBar(int taskTotalSize) {
		progress  = new JProgressBar(1, taskTotalSize);
		progress.setName(progressName);
		progress.setForeground(Color.lightGray);
	}
	
	public void setProgressValue(int currentInd/*0 to n-1*/) {
		progress.setValue(currentInd+1);
		if(progress.getMaximum()==(currentInd+1)) {
			showProgressBar(false);
		}else {
			repaint();
		}
	}
	
	public void showProgressBar(boolean show) {
		if(show) {
			for(Component con : contentPanel.getComponents()) {
				if(con == progress) {
					return;
				}
				if(con instanceof JProgressBar && con.getName().equals(progressName)) {
					return;
				}
			}
			contentPanel.add(progress);
		}else {
			contentPanel.remove(progress);
		}
		repaint();
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

class DateTimeLabel extends JLabel {

    private static final long serialVersionUID = -597784514373046752L;
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd (E) HH:mm:ss");

    public DateTimeLabel() {
        updateDateTime();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateDateTime();
            }
        }, 0, 500); // 0.5秒ごとに更新
    }

    private void updateDateTime() {
        Date now = new Date();
        String formattedDateTime = dateFormat.format(now);
        setText(formattedDateTime);
        repaint();
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
		return HEIGHT;
	}

	@Override
	public int getIconWidth() {
		return WIDTH;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
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
