package com.vis.core.ui.function;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;

import com.vis.core.facade.WindowManager;
import com.vis.core.log.Log;
import com.vis.core.ui.dialog.PopUpMessage;
import com.vis.core.ui.main.MainScreen;
import com.vis.core.util.Utils;
import com.vis.dicom.DicomFileCollection;
import com.vis.dicom.DicomUtilities;

/**
 * Extract dropped zip file(s), keep only real (non-DICOMDIR) DICOM entries, then
 * import them study by study using the existing {@link DicomImporter} pipeline.
 *
 * Drives MainScreen's overall status-bar progress bar while extracting/classifying
 * (a phase that otherwise has no progress feedback at all), while the per-study
 * progress bars in DICOMTreeTable keep working as-is via DicomImporter/TaskManager.
 *
 * @author tatsunidas
 */
public class ZipDicomImporter {

	private final List<File> zipFiles;

	public ZipDicomImporter(List<File> zipFiles) {
		this.zipFiles = zipFiles;
	}

	public void start() {
		Thread t = new Thread(this::perform, "ZipDicomImporter");
		t.start();
	}

	private void perform() {
		File tempDir = Utils.createNewDirInTemp();
		MainScreen ms = WindowManager.getMainScreen();
		boolean progressBarStarted = false;
		try {
			long totalEntries = 0;
			for (File zip : zipFiles) {
				totalEntries += countFileEntries(zip);
			}
			if (totalEntries <= 0) {
				notifyNoDicomFound();
				return;
			}

			final int totalEntriesFinal = (int) totalEntries;
			if (ms != null) {
				SwingUtilities.invokeLater(() -> ms.startProgressBar(totalEntriesFinal));
				progressBarStarted = true;
			}

			ArrayList<File> dicomCandidates = new ArrayList<>();
			int itr = 0;
			for (File zip : zipFiles) {
				try (ZipFile zf = new ZipFile(zip)) {
					Enumeration<? extends ZipEntry> entries = zf.entries();
					while (entries.hasMoreElements()) {
						ZipEntry entry = entries.nextElement();
						if (entry.isDirectory()) {
							continue;
						}
						final int counter = itr++;
						if (ms != null) {
							SwingUtilities.invokeLater(() -> ms.setProgressValue(counter));
						}

						File extracted = extractEntry(zf, entry, tempDir);
						if (extracted == null) {
							continue;
						}
						if (!DicomUtilities.isDicomFile(extracted) || DicomUtilities.isDICOMDIR(extracted)) {
							extracted.delete();
							continue;
						}
						dicomCandidates.add(extracted);
					}
				} catch (IOException e) {
					Log.logger.warning(
							"ZipDicomImporter: failed to read zip " + zip.getAbsolutePath() + ". " + e.getMessage());
				}
			}

			if (dicomCandidates.isEmpty()) {
				notifyNoDicomFound();
				return;
			}

			DicomFileCollection collec = new DicomFileCollection(dicomCandidates.toArray(new File[0]));
			collec.collectCandidates();
			if (collec.getNumOfTotalDcmFiles() <= 0) {
				notifyNoDicomFound();
				return;
			}

			ArrayList<DicomImporter> importers = new ArrayList<>();
			for (String willImportStudyUID : collec.getNoSubstituteStudyUIDList()) {
				ArrayList<String> candidateList = collec.selectCandidateUsingStudyUID(willImportStudyUID);
				DicomImporter importer = new DicomImporter(candidateList, willImportStudyUID);
				importers.add(importer);
				importer.start();
			}

			// keep the temp dir alive until every per-study DicomImporter spawned from it
			// has actually finished reading from it (each runs on its own thread).
			for (DicomImporter importer : importers) {
				while (!importer.isCompleted()) {
					try {
						Thread.sleep(300);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
		} finally {
			if (ms != null && progressBarStarted) {
				SwingUtilities.invokeLater(ms::removeProgressBar);
			}
			try {
				FileUtils.deleteDirectory(tempDir);
			} catch (IOException e) {
				Log.logger.warning(
						"ZipDicomImporter: failed to delete temp dir " + tempDir.getAbsolutePath() + ". " + e.getMessage());
			}
		}
	}

	private long countFileEntries(File zip) {
		try (ZipFile zf = new ZipFile(zip)) {
			return zf.stream().filter(e -> !e.isDirectory()).count();
		} catch (IOException e) {
			Log.logger.warning("ZipDicomImporter: failed to open zip " + zip.getAbsolutePath() + ", skip. "
					+ e.getMessage());
			return 0;
		}
	}

	/**
	 * Extracts a single zip entry under destDir, guarding against zip-slip
	 * (entry names containing "../" that would otherwise escape destDir).
	 */
	private File extractEntry(ZipFile zf, ZipEntry entry, File destDir) {
		try {
			File outFile = new File(destDir, entry.getName());
			String destDirCanonical = destDir.getCanonicalPath() + File.separator;
			String outFileCanonical = outFile.getCanonicalPath();
			if (!outFileCanonical.startsWith(destDirCanonical)) {
				Log.logger.warning("ZipDicomImporter: blocked a zip entry escaping the extraction directory: "
						+ entry.getName());
				return null;
			}
			File parent = outFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			try (InputStream in = zf.getInputStream(entry); OutputStream out = new FileOutputStream(outFile)) {
				byte[] buf = new byte[8192];
				int len;
				while ((len = in.read(buf)) != -1) {
					out.write(buf, 0, len);
				}
			}
			return outFile;
		} catch (IOException e) {
			Log.logger.warning("ZipDicomImporter: failed to extract entry " + entry.getName() + ". " + e.getMessage());
			return null;
		}
	}

	private void notifyNoDicomFound() {
		SwingUtilities.invokeLater(() -> PopUpMessage.showDialog(WindowManager.getMainScreen(), "No DICOM files found",
				"The dropped zip file(s) did not contain any importable DICOM files.", JOptionPane.OK_OPTION,
				JOptionPane.INFORMATION_MESSAGE));
	}
}
