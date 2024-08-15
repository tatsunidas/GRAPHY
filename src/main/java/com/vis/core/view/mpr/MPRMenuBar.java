package com.vis.core.view.mpr;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import com.vis.core.facade.ApplicationFacade;
import com.vis.core.ui.dialog.SaveImage;
import com.vis.core.ui.function.DicomDuplicator;
import com.vis.core.view.D2.processing.ImagePlusDicomTagTools;
import com.vis.db.DatabaseHandler;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.Tag;
import com.vis.dicom.TagDict;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.ImagePlusToDicomImage;

import ij.ImagePlus;

@SuppressWarnings("serial")
public class MPRMenuBar extends JMenuBar implements ActionListener{
	
	MPRViewerWindow mpr_win;
	
	private final String ipp = "0020,0032";//image position patient
	private final String iop = "0020,0037";//image orientation patient
	
	public MPRMenuBar(MPRViewerWindow win) {
		
		this.mpr_win = win;
		
		JMenu mainMenu = new JMenu("File");
		
		JMenuItem saveItem1 = new JMenuItem("Save reslice images as general format");
		saveItem1.setName("Save reslice images as general format");
		mainMenu.add(saveItem1);
		saveItem1.addActionListener(this);
		
		JMenuItem saveItem2 = new JMenuItem("Save reslice images as dicom format");
		saveItem2.setName("Save reslice images as dicom format");
		mainMenu.add(saveItem2);
		saveItem2.addActionListener(this);
		
//		JMenuItem saveItem3 = new JMenuItem("Save reslice images to DB");
//		saveItem3.setName("Save reslice images to DB");
//		mainMenu.add(saveItem3);
//		saveItem3.addActionListener(this);
		
		add(mainMenu);
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object obj = e.getSource();
		if(!(obj instanceof JMenuItem)) {
			return;
		}
		JMenuItem item = (JMenuItem)obj;
		if(item.getName().equals("Save reslice images as general format")) {
			ImagePlus recon = mpr_win.reconImage();
			if(recon != null && recon.getNSlices() > 0) {
				String title = "Save reslice images";
				String defaultDir = System.getProperty("user.home");
				String defaultName = "recon_mpr";
				String extensionWithDot = ".tif";
				SaveImage.save(recon, title, defaultDir, defaultName, extensionWithDot);
			}else {
				JOptionPane.showMessageDialog(this, "Can not save it, do reslice first.");
				return;
			}
		}else if(item.getName().equals("Save reslice images as dicom format")) {
			ImagePlus recon = mpr_win.reconImage();
			if(recon != null) {
				String pid = mpr_win.getPatientID();
				String studyUID = mpr_win.getStudyUID();
				//String seriesUID = mpr_win.getSeriesUID();
				if(pid == null || studyUID == null) {
					JOptionPane.showMessageDialog(this, "Can not create new series, this images does not have dicom attributes.");
					return;
				}
				
				JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home")));
				chooser.setDialogType(JFileChooser.SAVE_DIALOG);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setDialogTitle("Dicom save to...");
				int res = chooser.showSaveDialog(mpr_win);
				File dest = null;
				if(res == JFileChooser.APPROVE_OPTION) {
					dest = chooser.getSelectedFile();
				}else {
					return;
				}
				if(dest == null) {
					dest = new File(System.getProperty("user.home"));
					System.out.println("dicom file will saving to home dir.");
				}
				ImagePlusToDicomImage itd = new ImagePlusToDicomImage();
				DicomObject refDcm = mpr_win.getSampleReferenceDcm();
				ImagePlusDicomTagTools tool = new ImagePlusDicomTagTools();
				int size = recon.getNSlices();
				String seriesUID = UIDUtils.createUID();
				Integer seriesNo = refDcm.getInt(Tag.Series​Number, 1);
				seriesNo = seriesNo == null ? 100:100+seriesNo; 
				String frameOfReferenceUID = refDcm.getString(Tag.Frame​Of​Reference​UID, null);
				File destChi = new File(dest.getAbsoluteFile()+File.separator+seriesUID);
				if(!destChi.exists()) {
					destChi.mkdirs();
				}
				HashMap<Integer,DicomImage> images = ImagePlusToDicomImage.imagePlusToDcm(recon, true);
				for(int i=0;i<images.size();i++) {
					DicomWriter writer = DicomWriter.newDicomWriter();
					String sopUID = images.get(i).getCore().getString(Tag.SOP​Instance​UID);
					if(sopUID == null || sopUID.length() == 0) {
						sopUID = ""+i;
					}
					writer.write(images.get(i).getCore(), UID.ImplicitVRLittleEndian.uid(), destChi.getAbsolutePath()+File.separator+sopUID);
				}
				JOptionPane.showMessageDialog(this, "Saving reslice series was done !");
			}else {
				JOptionPane.showMessageDialog(this, "Can not create new series, do reslice first.");
				return;
			}
		}else if(item.getName().equals("Save reslice images to DB")) {
			DatabaseHandler db = DatabaseHandler.getInstance();
			if(db == null) {
				JOptionPane.showMessageDialog(this, "GRAPHY DB does not ready, can not save to DB.");
				return;
			}
			ImagePlus recon = mpr_win.reconImage();
			if(recon != null) {
				String pid = mpr_win.getPatientID();
				String studyUID = mpr_win.getStudyUID();
				String seriesUID = mpr_win.getSeriesUID();
				if(pid == null || studyUID == null || seriesUID == null) {
					JOptionPane.showMessageDialog(this, "Can not create new series, this images does not have dicom attributes.");
					return;
				}
				boolean retainIOPIPP = true;
				try {
					//TODO 20240815
					DicomDuplicator.createNewSeriesAndStore2DB(null, true, retainIOPIPP);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				JOptionPane.showMessageDialog(this, "Done, save reslice series.");
			}else {
				JOptionPane.showMessageDialog(this, "Can not create new series, do reslice first.");
				return;
			}
		}
	}
}
