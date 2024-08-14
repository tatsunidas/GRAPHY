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
package com.vis.core.view.D2.ui;

import java.awt.Component;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.vis.configuration.Resources;

public class PresenceCellRenderer extends DefaultTableCellRenderer{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ImageIcon openEye;
	public final static int DEFAULT = 0;
	public final static int SHARIN = 1;
	public final static int HORUS = 2;
	
	public PresenceCellRenderer(int iconType) {
		if(iconType == SHARIN) {
			openEye = Resources.PresenceCellSharinIcon.loadIconFromResource();
			openEye = new ImageIcon(openEye.getImage().getScaledInstance(16, 16, Image.SCALE_FAST));
		}else if(iconType == HORUS) {
			openEye = Resources.PresenceCellHorusIcon.loadIconFromResource();
			openEye = new ImageIcon(openEye.getImage().getScaledInstance(20, 16, Image.SCALE_SMOOTH));
		}else {
			openEye = Resources.PresenceCellStandardIcon.loadIconFromResource();
			openEye = new ImageIcon(openEye.getImage().getScaledInstance(16, 16, Image.SCALE_FAST));
		}
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		label.setHorizontalAlignment(JLabel.CENTER);
		if(isSelected) {
			label.setForeground(table.getSelectionForeground());
			label.setBackground(table.getSelectionBackground());
		}else {
			label.setForeground(table.getForeground());
			label.setBackground(table.getBackground());
		}
		
		if(value instanceof Boolean) {
			boolean onEye = (Boolean) value;
			label.setText("");
			label.setOpaque(true);// important
			label.setIcon(null);// reset
			if (onEye) {
				label.setIcon(openEye);
			}
		}
		return label;
	}
}
