package com.vis.imageio;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.vis.core.log.Log;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Tag;
import com.vis.dicom.UID;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

/**
 * Please close this instance using close(). 
 * @author tatsunidas
 *
 */
public class PDFReader implements Closeable, KeyListener{

	private static final long MAX_FILE_SIZE = 0x7FFFFFFE;
	int currentPage = 0;
	int pageMax = -1;
	PDDocument doc = null;
	int dpi = 300;
	
	/**
	 * @param pdfOrDcm
	 */
	public PDFReader(File pdfOrDcm) {
		if(pdfOrDcm == null || !pdfOrDcm.exists()) {
			Log.logger.warning(getClass().getName()+" : This PDF file does not exists.return null...");
			return;
		}
		boolean isDcm = DicomUtilities.isDicomFile(pdfOrDcm);
		if(isDcm) {
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(pdfOrDcm.toURI());
			DicomObject dcm = reader.getCore();
			String sopUID = dcm.getString(Tag.SOP​Class​UID);
			if(!sopUID.equals(UID.EncapsulatedPDFStorage.uid())) {
				Log.logger.warning("PDFReader:This dicom file is not PDF, return ...");
				return;
			}
			this.doc = dcm2pdf(dcm);
			if(doc != null) {
				pageMax = doc.getNumberOfPages();
			}
		}else {
			doc = loadFromFile(pdfOrDcm);
			if(doc == null) {
				return;
			}
			pageMax = doc.getNumberOfPages();
		}
	}
	
	/**
	 * load pdf file.(No dicom.)
	 * @param srcPDF
	 * @return
	 */
	public static PDDocument loadFromFile(File srcPDF) {
		PDDocument doc = null;
		try {
			doc = PDDocument.load(srcPDF);
		} catch (IOException e) {
			Log.logger.warning("PDFReader:This PDF not readable, return null...\n"+e.getMessage());
			return null;
		}
		if(doc.isEncrypted()) {
			System.out.println("PDFReader:This PDF is encrypted..., return null...");
			// if you want set password for decryption
//          StandardDecryptionMaterial decryptionMaterial = new StandardDecryptionMaterial(password);
//          doc.openProtection(decryptionMaterial);
			return null;
		}
		return doc;
	}
	
	/**
	 * 
	 * create pdf pages as ImagePlus(stacked)
	 * 
	 */
	public ImagePlus pdf2ImageStack() {
		if(doc == null) {
			return null;
		}
		/*get initial size*/
		PDPage page = doc.getPage(0);
		PDRectangle cropBox = page.getCropBox();
		boolean rot = false;
		if (page.getRotation() == 90 || page.getRotation() == 270) {
			rot = true;
		}
		// https://stackoverflow.com/questions/1106339/resize-image-to-fit-in-bounding-box
		float imgInitWidth = rot ? cropBox.getHeight() : cropBox.getWidth();
		float imgInitHeight = rot ? cropBox.getWidth() : cropBox.getHeight();
		/*init stack size*/
		ImageStack stack = new ImageStack((int)imgInitWidth, (int)imgInitHeight);//, doc.getNumberOfPages());
		/*add to stack*/
		PDFRenderer pdfRenderer = new PDFRenderer(doc);
		for(int i=0;i<doc.getNumberOfPages();i++) {
			page = doc.getPage(i);
			BufferedImage bImage = null;
			try {
				pdfRenderer.isSubsamplingAllowed();
//				bImage = pdfRenderer.renderImageWithDPI(i, dpi, org.apache.pdfbox.rendering.ImageType.RGB);//strange image size? cropped ?
				bImage = pdfRenderer.renderImage(i);//RGB
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
			cropBox = page.getCropBox();
			ImagePlus pageImp = new ImagePlus("", bImage);
			if(cropBox.getHeight() != imgInitHeight || cropBox.getWidth() != imgInitWidth) {
				ImageProcessor ip = pageImp.getProcessor().resize((int)imgInitWidth, (int)imgInitHeight);
				pageImp.setProcessor(ip);
			}
			stack.addSlice(pageImp.getProcessor());
		}
		return new ImagePlus("pdf", stack);
	}
	
	public PDDocument dcm2pdf(DicomObject pdfdcm) {
		PDDocument doc = null;
		try {
			byte[] contents = pdfdcm.getBytes(Tag.Encapsulated​Document);
			if(contents != null && contents.length > 0) {
				doc = PDDocument.load(contents);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return doc;
	}
	
	/**
	 * show pdf using OS default PDF viewer. almost, something browser.
	 * 1.save pdf to os default temp folder
	 * 2.show pdf by os default app
	 * @param pdfdcm:dicom pdf
	 */
	public void showPDF(DicomObject pdfdcm) {
		PDDocument doc = dcm2pdf(pdfdcm);
		File tempFile = null;
		try {
			tempFile = File.createTempFile("temp_consumer_pdf", ".pdf");
			doc.save(tempFile);
		} catch (IOException ex) {
			Log.logger.severe(ex.getMessage());
			return;
		}
		if(tempFile != null && tempFile.exists() && tempFile.getAbsolutePath().endsWith("pdf")) {
			tempFile.deleteOnExit();//delete when JVM turn off.
		}
		showPDF(tempFile);
	}
	
	/*
	 * show in browser
	 * https://stackoverflow.com/questions/5226212/how-to-open-the-default-webbrowser-using-java
	 * show using os app
	 * https://stackoverflow.com/questions/7024031/java-open-a-file-windows-mac
	 */
	public void showPDF(File pdf) {
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(pdf);
			}else {
				Log.logger.warning("Cannot show PDF, Desktop not supported.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * show pdf on JFrame as Image.
	 * @param pdfdcm
	 */
	public void showInFrame(DicomObject pdfdcm) {
		JFrame frame = new JFrame("Simple PDF View");
		frame.addKeyListener(this);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					byte[] contents = pdfdcm.getBytes(Tag.Encapsulated​Document);
					if(contents == null) {
						JOptionPane.showMessageDialog(null, "Can not read this PDF...Sorry...");
						frame.dispose();
						return;
					}
					frame.setSize(700,500);
					frame.add(new JScrollPane(getViewPanel(contents)));
//					frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
					frame.setVisible(true);
				} catch (IOException e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(null, "Can not read this PDF...Sorry...");
					return;
				}
			}
		});
		
	}

	private JPanel getViewPanel(byte[] cont) {
		try {
			PDDocument doc = PDDocument.load(cont);
			if(doc != null) {
				pageMax = doc.getNumberOfPages();
			}
			final PDFRenderer renderer = new PDFRenderer(doc);
			@SuppressWarnings("serial")
			JPanel panel = new JPanel() {
				@Override
				protected void paintComponent(Graphics g) {
					try {
						g.setColor(Color.white);
						g.fillRect(0, 0, getWidth(), getHeight());
						PDPage page = doc.getPage(0);
						PDRectangle cropBox = page.getCropBox();
						boolean rot = false;
						if (page.getRotation() == 90 || page.getRotation() == 270) {
							rot = true;
						}
						// https://stackoverflow.com/questions/1106339/resize-image-to-fit-in-bounding-box
						float imgWidth = rot ? cropBox.getHeight() : cropBox.getWidth();
						float imgHeight = rot ? cropBox.getWidth() : cropBox.getHeight();
						float xf = getWidth() / imgWidth;
						float yf = getHeight() / imgHeight;
						float scale = Math.min(xf, yf);
						if (yf < xf) {
							g.translate((int) ((getWidth() - imgWidth * yf) / 2), 0);
						} else {
							g.translate(0, (int) ((getHeight() - imgHeight * xf) / 2));
						}
						renderer.renderPageToGraphics(currentPage, (Graphics2D) g, scale);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
			return panel;
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return null;
	}

	public static DicomObject convert2DCM(
			File srcPDF, 
			String pname,
			String pid,
			java.util.Date dob,//1999/01/01 or 1999-01-01
			String sex,//M,F,O
			java.util.Date studyDate,
			java.util.Date studyTime,
			java.util.Date contentDate,
			java.util.Date contentTime,
			Integer seriesNo,
			String studyUID,//if null setNew
			String seriesUID//if null setNew
			) {
		
		PDDocument doc = loadFromFile(srcPDF);
		if(doc == null) {
			return null;
		}
		long fileLength = srcPDF.length();
		if (fileLength > MAX_FILE_SIZE) {
			throw new IllegalArgumentException("file-too-large");
		}
		DicomObject attr = DicomObject.newDicomObject();
		//patient
		attr.setString(Tag.Patient​Name, VR.PN, pname);
		attr.setString(Tag.Patient​ID, VR.LO, pid);
		attr.setString(Tag.Patient​Sex, VR.CS, sex);
		if(dob != null) {
			attr.setDate(Tag.Patient​Birth​Date, VR.DA, dob);
		}else {
			attr.setNull(Tag.Patient​Birth​Date, VR.DA);
		}
		//study
		if(studyDate == null) {
			attr.setNull(Tag.Study​Date, VR.DA);
		}else {
			attr.setDate(Tag.Study​Date, VR.DA, studyDate);
		}
		if(studyTime == null) {
			attr.setNull(Tag.Study​Time, VR.TM);
		}else {
			attr.setDate(Tag.Study​Time, VR.TM, studyTime);
		}
//		if(studyID == null) {
//			attr.setNull(Tag.Study​ID, VR.SH);
//		}else {
//			attr.setInt(Tag.Study​ID, VR.SH, studyID);
//		}
		//series
		attr.setInt(Tag.Series​Number, VR.IS, seriesNo);
		//instance
		if(contentDate == null) {
			attr.setNull(Tag.Content​Date, VR.DA);
		}else {
			attr.setDate(Tag.Content​Date, VR.DA, contentDate);
		}
		if(contentTime == null) {
			attr.setNull(Tag.Content​Time, VR.TM);
		}else {
			attr.setDate(Tag.Content​Time, VR.TM, contentTime);
		}
//		if(acquisitionDateTime == null) {
//			attr.setNull(Tag.Acquisition​Date​Time, VR.DT);
//		}else {
//			attr.setDate(Tag.Acquisition​Date​Time, VR.DT, acquisitionDateTime);
//		}
//		attr.setNull(Tag.Accession​Number, VR.SH);
//		attr.setNull(Tag.Referring​Physician​Name, VR.PN);
		attr.setString(Tag.Modality, VR.CS, "OT");
		attr.setString(Tag.Manufacturer, VR.LO, "Visionary Imaging Services, Inc");
//		attr.setString(Tag.Conversion​Type, VR.CS, "WSD");// workstation
		attr.setInt(Tag.Instance​Number, VR.IS, 1);
		attr.setString(Tag.Document​Title, VR.ST, doc.getDocumentInformation().getTitle());
		attr.setInt(Tag.Number​Of​Frames, VR.IS, doc.getNumberOfPages());
//		attr.setNull(Tag.Concept​Name​Code​Sequence, VR.SQ);
//		attr.setString(Tag.MIME​Type​Of​Encapsulated​Document, VR.LO, "application/pdf");
//		attr.setString(Tag.Burned​In​Annotation, VR.CS, burnedInAnnotation ? "YES":"NO");
//		//UID
		attr.setString(Tag.SOP​Class​UID, VR.UI, UID.EncapsulatedPDFStorage.uid());
		attr.setString(Tag.Study​Instance​UID, VR.UI, studyUID != null ? studyUID:UIDUtils.createUID());
		attr.setString(Tag.Series​Instance​UID, VR.UI, seriesUID != null ? seriesUID:UIDUtils.createUID());
		attr.setString(Tag.SOP​Instance​UID, VR.UI, UIDUtils.createUID());
		
		try {
			attr.setValue(Tag.Encapsulated​Document, VR.OB, Files.readAllBytes(srcPDF.toPath()));
			doc.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return attr;
	}
	
	public void close() {
		if(doc != null) {
			try {
				doc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void keyPressed(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_DOWN) {
			//upper page
			currentPage = currentPage+1;
			if(currentPage > pageMax-1) {
				currentPage = 0;
			}else if(currentPage < 0) {
				currentPage = pageMax-1;
			}
		}else if(e.getKeyCode() == KeyEvent.VK_UP) {
			currentPage = currentPage-1;
			if(currentPage > pageMax-1) {
				currentPage = 0;
			}else if(currentPage < 0) {
				currentPage = pageMax-1;
			}
		}
		Object obj = e.getSource();
		if(obj instanceof JFrame || obj instanceof JPanel) {
			JComponent comp = (JComponent)obj;
			Component chi = comp.getComponentAt(comp.getWidth()/2, comp.getHeight()/2);
			chi.repaint();
			comp.revalidate();
			comp.repaint();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}
}

