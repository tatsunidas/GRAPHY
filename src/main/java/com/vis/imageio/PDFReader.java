package com.vis.imageio;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.dcm4che3.io.DicomOutputStream;

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
 * @author tatsunidas
 *
 */
public class PDFReader{

	private static final long MAX_FILE_SIZE = 0x7FFFFFFE;
	
	final File pdf;
	int dpi = 600;
	final boolean isDcm;
	
	/**
	 * @param pdfOrDcm
	 */
	public PDFReader(File pdfOrDcm) {
		pdf = pdfOrDcm;
		if(pdf == null || !pdf.exists()) {
			Log.logger.warning("PDF file is null, or this PDF file does not exists...");
			throw new IllegalArgumentException("PDF file is null, or this PDF file does not exists...");
		}
		this.isDcm = DicomUtilities.isDicomFile(pdf);
		if(isDcm) {
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(pdf.toURI(), false);
			DicomObject dcm = reader.getHeader();
			String sopUID = dcm.getString(Tag.SOP​Class​UID);
			if(!sopUID.equals(UID.EncapsulatedPDFStorage.uid())) {
				Log.logger.warning("PDFReader:This dicom file does not have PDF ...");
				throw new IllegalArgumentException("PDFReader:This dicom file does not have PDF ...");
			}
		}
	}
	
	public static boolean isValidPDF(File file/*pure pdf, not dcm*/) {
		PDDocument document = null;
		try {
			// Attempt to load the document. This will throw an exception if the file
			// is not a valid or readable PDF document.
			document = PDDocument.load(file);
			// Optional: Perform additional checks, e.g., read some metadata to ensure full
			// parsing
			PDDocumentInformation info = document.getDocumentInformation();
			@SuppressWarnings("unused")
			String title = info.getTitle(); // Accessing info forces some parsing
			return true;
		} catch (IOException e) {
			// An exception occurred, meaning the file is likely not a valid PDF
			Log.logger.warning("File is not a valid PDF or is corrupted: " + e.getMessage());
			return false;
		} finally {
			// Ensure the document is closed in all cases
			if (document != null) {
				try {
					document.close();
				} catch (IOException e) {
					System.err.println("Error closing document: " + e.getMessage());
				}
			}
		}
	}

	
	/**
	 * @param srcPDF/Dcm
	 * @return
	 */
	public PDDocument loadDocument() {
		if(!isDcm) {
			return loadDocumentFromPurePDF(pdf);
		}else {
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
			reader.read(pdf.toURI(), true/*read fully*/);
			DicomObject dcm = reader.getHeader();
			try {
				byte[] pdfBulk = dcm.getBytes(Tag.Encapsulated​Document);
				return PDDocument.load(pdfBulk);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		}
	}
	
	public static PDDocument loadDocumentFromPurePDF(File pdf) {
		PDDocument doc = null;
		try {
			doc = PDDocument.load(pdf);
		} catch (IOException e) {
			Log.logger.warning("PDFReader:This PDF not readable, return null...\n"+e.getMessage());
			return null;
		}
		if(doc.isEncrypted()) {
			Log.logger.warning("PDFReader:This PDF is encrypted..., return null...");
			// if you want set password for decryption
//          StandardDecryptionMaterial decryptionMaterial = new StandardDecryptionMaterial(password);
//          doc.openProtection(decryptionMaterial);
			return null;
		}
		return doc;
	}
	
	// 指定ページを画像化
	public BufferedImage renderPDFPage(int pageIndex) {
		try (PDDocument doc = loadDocument()) {
			PDFRenderer renderer = new PDFRenderer(doc);
			// DPIは画面表示用に調整（150〜200程度が綺麗です）
			return renderer.renderImageWithDPI(pageIndex, dpi);
		} catch (IOException e) {
			return null;
		}
	}
	
	public byte[] getPDFPageRaw(int pageIndex) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

			BufferedImage page = renderPDFPage(pageIndex);

			if (page == null) {
				Log.logger.severe("decompressFrame が null を返しました。");
				return null;
			}

			// 2. 解凍後の Raster から生ピクセルデータを抽出して OutputStream (baos) へ書き出し
			// クラス内の既存メソッド writeTo(Raster, OutputStream) を再利用します。
			writeTo(page.getRaster(), baos);

			return baos.toByteArray();

		} catch (IOException e) {
			Log.logger.severe("decompressFrameToBytes で例外が発生しました: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * 
	 * create pdf pages as ImagePlus(stacked)
	 * 
	 */
	public ImagePlus pdf2ImageStack() {
		PDDocument doc = loadDocument();
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
	
	// ページ数を取得
	public int getPDFPageCount() {
		try (PDDocument doc = PDDocument.load(pdf)) {
			return doc.getNumberOfPages();
		} catch (IOException e) {
			return 0;
		}
	}

	// 指定ページを画像化
	public BufferedImage renderPDFPage(byte[] pdfBytes, int pageIndex) {
	    try (PDDocument doc = PDDocument.load(pdfBytes)) {
	        PDFRenderer renderer = new PDFRenderer(doc);
	        // DPIは画面表示用に調整（150〜200程度が綺麗です）
	        return renderer.renderImageWithDPI(pageIndex, 150);
	    } catch (IOException e) {
	        return null;
	    }
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
	public void show(int pageIndex) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JFrame frame = new JFrame("Simple PDF View");
				frame.setSize(700,500);
				frame.add(new JScrollPane(getViewPanel(pageIndex)));
//				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setVisible(true);
			}
		});
	}

	private JPanel getViewPanel(int pageIndex) {
		try (PDDocument doc = PDDocument.load(pdf);){
			final PDFRenderer renderer = new PDFRenderer(doc);
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
						renderer.renderPageToGraphics(pageIndex, (Graphics2D) g, scale);
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
			String studyDesc,
			java.util.Date contentDate,
			java.util.Date contentTime,
			String seriesDesc,
			Integer seriesNo,
			String studyUID,//if null setNew
			String seriesUID//if null setNew
			) {
		
		PDDocument doc = loadDocumentFromPurePDF(srcPDF);
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
		if(studyDesc == null) {
			attr.setNull(Tag.Study​Description, VR.LO);
		}else {
			attr.setString(Tag.Study​​Description, VR.LO, studyDesc);
		}
		//series
		attr.setString(Tag.SeriesDescription, VR.LO, seriesDesc);
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
		
		// ★PDF特有の必須タグ
		attr.setString(Tag.MIME​Type​Of​Encapsulated​Document, VR.LO, "application/pdf");
		attr.setString(Tag.Burned​In​Annotation, VR.CS, "YES");
//		attr.setString(Tag.ConversionType, VR.CS, "WSD");
		
		attr.setString(Tag.Modality, VR.CS, "OT");
		attr.setString(Tag.Manufacturer, VR.LO, "Visionary Imaging Services, Inc");
		
		attr.setInt(Tag.Instance​Number, VR.IS, 1);
		
		attr.setString(Tag.Document​Title, VR.ST, doc.getDocumentInformation().getTitle());
		attr.setInt(Tag.Number​Of​Frames, VR.IS, doc.getNumberOfPages());
		
		// --- SOP Common Module ---
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
	
	private void writeTo(Raster raster, OutputStream out) throws IOException {
        SampleModel sm = raster.getSampleModel();
        DataBuffer db = raster.getDataBuffer();
        switch (db.getDataType()) {
        case DataBuffer.TYPE_BYTE:
            writeTo(sm, ((DataBufferByte) db).getBankData(), out);
            break;
        case DataBuffer.TYPE_USHORT:
            writeTo(sm, ((DataBufferUShort) db).getData(), out);
            break;
        case DataBuffer.TYPE_SHORT:
            writeTo(sm, ((DataBufferShort) db).getData(), out);
            break;
        case DataBuffer.TYPE_INT:
            writeTo(sm, ((DataBufferInt) db).getData(), out);
            break;
        default:
            throw new UnsupportedOperationException(
                    "Unsupported Datatype: " + db.getDataType());
        }
    }
	
	private void writeTo(SampleModel sm, byte[][] bankData, OutputStream out) throws IOException {
		int h = sm.getHeight();
		int w = sm.getWidth();
		ComponentSampleModel csm = (ComponentSampleModel) sm;
		int len = w * csm.getPixelStride();
		int stride = csm.getScanlineStride();
		if (csm.getBandOffsets()[0] != 0)
			bgr2rgb(bankData[0]);
		int datatype = sm.getDataType();
		if (datatype == DataBuffer.TYPE_SHORT || datatype == DataBuffer.TYPE_USHORT) {
			byte[] buf = new byte[len << 1];
			int j0 = 0;
			if (out instanceof DicomOutputStream) {
				j0 = ((DicomOutputStream) out).isBigEndian() ? 1 : 0;
			}
			for (byte[] b : bankData)
				for (int y = 0, off = 0; y < h; ++y, off += stride) {
					out.write(to16BitsAllocated(b, off, len, buf, j0));
				}
		} else {
			for (byte[] b : bankData)
				for (int y = 0, off = 0; y < h; ++y, off += stride)
					out.write(b, off, len);
		}
	}
    
    private byte[] to16BitsAllocated(byte[] b, int off, int len, byte[] buf, int j0) {
        for (int i = 0, j = j0; i < len; i++, j++, j++) {
            buf[j] = b[off + i];
        }
        return buf;
    }

    private static void bgr2rgb(byte[] bs) {
        for (int i = 0, j = 2; j < bs.length; i += 3, j += 3) {
            byte b = bs[i];
            bs[i] = bs[j];
            bs[j] = b;
        }
    }

    private static void writeTo(SampleModel sm, short[] data, OutputStream out)
            throws IOException {
        int h = sm.getHeight();
        int w = sm.getWidth();
        int stride = ((ComponentSampleModel) sm).getScanlineStride();
        byte[] b = new byte[w * 2];
        for (int y = 0; y < h; ++y) {
            for (int i = 0, j = y * stride; i < b.length;) {
                short s = data[j++];
                b[i++] = (byte) s;
                b[i++] = (byte) (s >> 8);
            }
            out.write(b);
        }
    }

    private static void writeTo(SampleModel sm, int[] data, OutputStream out)
            throws IOException {
        int h = sm.getHeight();
        int w = sm.getWidth();
        int stride = ((SinglePixelPackedSampleModel) sm).getScanlineStride();
        byte[] b = new byte[w * 3];
        for (int y = 0; y < h; ++y) {
            for (int i = 0, j = y * stride; i < b.length;) {
                int s = data[j++];
                b[i++] = (byte) (s >> 16);
                b[i++] = (byte) (s >> 8);
                b[i++] = (byte) s;
            }
            out.write(b);
        }
    }
	
	public void close(PDDocument doc) {
		if(doc != null) {
			try {
				doc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

