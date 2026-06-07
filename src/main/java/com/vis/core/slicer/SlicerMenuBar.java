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
package com.vis.core.slicer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import com.vis.configuration.Resources;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.SaveImage;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D2.ui.orientation.ImageOrientation.CutSurface;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.image.DicomImage;

import ij.ImagePlus;
import ij.util.DicomTools;

/**
 * 
 * @author tatsunidas
 *
 */
@SuppressWarnings("serial")
public class SlicerMenuBar extends JMenuBar implements ActionListener{
	
	SlicerWindow win;
	
	private final String patIDTag = "0010,0020";
	private final String studyUIDTag = "0020,000D";
	private final String seriesUIDTag = "0020,000E";
//	private final String refUIDTag = "0020,0052";
//	private final String sopClassUIDTag = "0008,0016";
//	private final String sopInstUIDTag = "0008,0018";	
//	private final String ipp = "0020,0032";//image position patient
//	private final String iop = "0020,0037";//image orientation patient
	
	public SlicerMenuBar(SlicerWindow win) {
		
		this.win = win;
		
		JMenu mainMenu = new JMenu("File");
		
		JMenuItem saveItem1 = new JMenuItem("Save reslice images as TIF");
		saveItem1.setName("Save reslice images as TIF");
		mainMenu.add(saveItem1);
		saveItem1.addActionListener(this);
		
		JMenuItem saveItem2 = new JMenuItem("Save reslice images as dicom format");
		saveItem2.setName("Save reslice images as dicom format");
		mainMenu.add(saveItem2);
		saveItem2.addActionListener(this);
		
		JMenuItem saveItem3 = new JMenuItem("Save reslice images to DB");
		saveItem3.setName("Save reslice images to DB");
		mainMenu.add(saveItem3);
		saveItem3.addActionListener(this);
		
		add(mainMenu);
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object obj = e.getSource();
		if(!(obj instanceof JMenuItem)) {
			return;
		}
		JMenuItem item = (JMenuItem)obj;
		if(item.getName().equals("Save reslice images as TIF")) {
			ImagePlus recon = win.reconImage();
			if(recon != null && recon.getNSlices() > 0) {
				String title = "Save reslice images";
				String defaultDir = System.getProperty("user.home");
				String defaultName = "recon_mpr";
				String extensionWithDot = ".tif";
				SaveImage.save(recon, title, defaultDir, defaultName, extensionWithDot);
			}else {
				JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.error.cannotSave"),
						Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}
		}else if(item.getName().equals("Save reslice images as dicom format")) {
			ImagePlus recon = win.reconImage();
			if(recon != null) {
				Praparat pp = win.getPraparatAt(CutSurface.OBLIQUE);
				String pid = (String)pp.getUIDs()[0];
				String studyUID = (String)pp.getUIDs()[1];
				String seriesUID = (String)pp.getUIDs()[2];
				if(pid == null || studyUID == null || seriesUID == null) {
					JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.error.noDicomAttr"),
							Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
					return;
				}

				JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home")));
				chooser.setDialogType(JFileChooser.SAVE_DIALOG);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setMultiSelectionEnabled(false);
				chooser.setDialogTitle("Dicom save to...");
				int res = chooser.showSaveDialog(win);
				File dest = null;
				if(res == JFileChooser.APPROVE_OPTION) {
					dest = chooser.getSelectedFile();
				}else {
					return;
				}
				if(dest == null) {
					Log.logger.fine("Interupted save dicom files...");
					return;
				}
				//Series number
				Integer seriesNo = 100;
				DatabaseHandler db = DatabaseHandler.getInstance();
				if(db != null) {
					seriesNo = db.getNumOfSeries(pid, studyUID);
					if (seriesNo != -1) {
						seriesNo += 1;
					}
				}
				File destChi = new File(dest.getAbsoluteFile()+File.separator+seriesUID);
				if(!destChi.exists()) {
					destChi.mkdirs();
				}
				ConcurrentHashMap<Integer,SlideGlass> images = pp.getAllSlides();
				DicomWriter writer = DicomWriter.newDicomWriter();
				for(Integer i : images.keySet()) {
					String sopUID = images.get(i).getDicomImage().getHeader().getString(Tag.SOP​Instance​UID);
					if(sopUID == null || sopUID.length() == 0) {
						throw new IllegalArgumentException("SOP Instance UID is null, Cannot save file as dicom.");
					}
					DicomImage dcm = images.get(i).getDicomImage();
					writer.write(dcm.getHeader(), UID.ImplicitVRLittleEndian.uid(), destChi.getAbsolutePath()+File.separator+sopUID);
				}
				JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.info.resliceSaved"),
						Resources.i18n("dialog.title.information"), JOptionPane.INFORMATION_MESSAGE);
			}else {
				JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.error.resliceFirst"),
						Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}
		}else if(item.getName().equals("Save reslice images to DB")) {
			DatabaseHandler db = DatabaseHandler.getInstance();
			if(db == null) {
				JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.error.dbNotReady"),
						Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}
			ImagePlus recon = win.reconImage();
			if(recon != null) {
				String pid = DicomTools.getTag(recon, patIDTag);
				String studyUID = DicomTools.getTag(recon, studyUIDTag);
				String seriesUID = DicomTools.getTag(recon, seriesUIDTag);
				if(pid == null || studyUID == null || seriesUID == null) {
					JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.error.noDicomAttr"),
							Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
					return;
				}
				try {
					Praparat reslice = win.getPraparatAt(CutSurface.OBLIQUE);
					if (reslice == null) {
						Log.logger.warning("Reslice praparat is null.");
						return;
					}
					HashMap<Integer, DicomImage> dcmImages = reslice.getDicomImages();
					if(dcmImages == null) {
						JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.error.emptyImages"),
								Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
						return;
					}
					db.storeDicomImagesToDb(dcmImages);
				} catch (Exception e1) {
					Log.logger.severe("Failed to save reslice series to DB: " + e1.getMessage());
					e1.printStackTrace();
				}
				JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.done.saveReslice"),
						Resources.i18n("dialog.title.complete"), JOptionPane.INFORMATION_MESSAGE);
			}else {
				JOptionPane.showMessageDialog(this, Resources.i18n("SlicerMenuBar.error.resliceFirst"),
						Resources.i18n("dialog.title.error"), JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
	}
}
