package com.vis.viewer2d;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;

/**
 * Layer 2: real DICOM file loading tests.
 *
 * Verifies that DicomReader can parse actual DICOM files and that key tags
 * (dimensions, UIDs) are present and sane. Tests are skipped when the external
 * sample directory is absent so CI never fails for missing test data.
 */
public class DicomFileLoadingTest {

    private static final String CT_FILE =
        "/home/tatsunidas/graphy_sample_images/dicom_samples/JIRA_DICOM/CT_LEE_IR87a.dcm";

    private static final String MR_FILE =
        "/home/tatsunidas/graphy_sample_images/dicom_samples/LGG-104_small" +
        "/06-26-2000-MRI Hd wow-05523/4-Gad Ax T2 Straight-38151/000000.dcm";

    private static boolean ctAvailable;
    private static boolean mrAvailable;

    @BeforeClass
    public static void checkFiles() {
        ctAvailable = new File(CT_FILE).isFile();
        mrAvailable = new File(MR_FILE).isFile();
        if (!ctAvailable) System.out.println("[WARN] CT file not found, CT tests will be skipped.");
        if (!mrAvailable) System.out.println("[WARN] MR file not found, MR tests will be skipped.");
    }

    // -----------------------------------------------------------------------
    // CT file tests
    // -----------------------------------------------------------------------

    @Test
    public void ct_readsWithoutException() {
        Assume.assumeTrue("CT file required", ctAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(CT_FILE, false);
        DicomObject header = reader.getHeader();
        assertNotNull("header must not be null", header);
    }

    @Test
    public void ct_hasDimensions() {
        Assume.assumeTrue("CT file required", ctAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(CT_FILE, false);
        DicomObject h = reader.getHeader();
        int rows = h.getInt(org.dcm4che3.data.Tag.Rows, 0);
        int cols = h.getInt(org.dcm4che3.data.Tag.Columns, 0);
        assertTrue("Rows > 0", rows > 0);
        assertTrue("Columns > 0", cols > 0);
    }

    @Test
    public void ct_hasSOPInstanceUID() {
        Assume.assumeTrue("CT file required", ctAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(CT_FILE, false);
        String uid = reader.getHeader().getString(org.dcm4che3.data.Tag.SOPInstanceUID);
        assertNotNull("SOPInstanceUID must be present", uid);
        assertFalse("SOPInstanceUID must not be empty", uid.isEmpty());
    }

    @Test
    public void ct_hasStudyAndSeriesUID() {
        Assume.assumeTrue("CT file required", ctAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(CT_FILE, false);
        DicomObject h = reader.getHeader();
        assertNotNull("StudyInstanceUID", h.getString(org.dcm4che3.data.Tag.StudyInstanceUID));
        assertNotNull("SeriesInstanceUID", h.getString(org.dcm4che3.data.Tag.SeriesInstanceUID));
    }

    @Test
    public void ct_modalityIsct() {
        Assume.assumeTrue("CT file required", ctAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(CT_FILE, false);
        String modality = reader.getHeader().getString(org.dcm4che3.data.Tag.Modality);
        assertEquals("Modality should be CT", "CT", modality);
    }

    // -----------------------------------------------------------------------
    // MR file tests
    // -----------------------------------------------------------------------

    @Test
    public void mr_readsWithoutException() {
        Assume.assumeTrue("MR file required", mrAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(MR_FILE, false);
        DicomObject header = reader.getHeader();
        assertNotNull("header must not be null", header);
    }

    @Test
    public void mr_hasDimensions() {
        Assume.assumeTrue("MR file required", mrAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(MR_FILE, false);
        DicomObject h = reader.getHeader();
        int rows = h.getInt(org.dcm4che3.data.Tag.Rows, 0);
        int cols = h.getInt(org.dcm4che3.data.Tag.Columns, 0);
        assertTrue("Rows > 0", rows > 0);
        assertTrue("Columns > 0", cols > 0);
    }

    @Test
    public void mr_hasImagePositionPatient() {
        Assume.assumeTrue("MR file required", mrAvailable);
        DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
        reader.read(MR_FILE, false);
        double[] ipp = reader.getHeader().getDoubles(org.dcm4che3.data.Tag.ImagePositionPatient);
        assertNotNull("ImagePositionPatient must be present for MR", ipp);
        assertEquals("IPP has 3 elements", 3, ipp.length);
    }
}
