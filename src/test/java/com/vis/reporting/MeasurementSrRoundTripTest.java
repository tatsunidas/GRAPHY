package com.vis.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.Test;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.measurement.Measurement;
import com.vis.core.reporting.measurement.MeasurementGroup;
import com.vis.core.reporting.measurement.MeasurementReport;
import com.vis.core.reporting.measurement.SpatialCoordinate;
import com.vis.core.reporting.sr.ContentItem;
import com.vis.core.reporting.sr.SRCodes;
import com.vis.core.reporting.sr.SRReader;
import com.vis.core.reporting.sr.SRtoHtml;
import com.vis.core.reporting.sr.Tid1500Writer;
import com.vis.dicom.UID;

/**
 * Layer-1 pure-logic tests for the TID 1500 measurement SR pipeline: build a
 * {@link MeasurementReport}, serialize it with {@link Tid1500Writer}, read it back
 * with {@link SRReader}, and verify values / units / spatial coordinates survive
 * the round-trip and render to HTML.
 */
public class MeasurementSrRoundTripTest {

	private Attributes referenceInstance() {
		Attributes ref = new Attributes();
		ref.setSpecificCharacterSet("ISO_IR 192");
		ref.setString(Tag.PatientName, VR.PN, "Yamada^Taro");
		ref.setString(Tag.PatientID, VR.LO, "PID-001");
		ref.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.study");
		ref.setString(Tag.StudyDate, VR.DA, "20260101");
		return ref;
	}

	private MeasurementReport sample2dReport() {
		MeasurementReport report = new MeasurementReport("PID-001", "1.2.3.study", "CT Measurements");
		MeasurementGroup g = new MeasurementGroup("Lesion 1");
		KeyImageRef img = new KeyImageRef("1.2.3.study", "1.2.3.series", "1.2.3.sop",
				UID.CTImageStorage.uid(), "slice");
		g.setImage(img);
		g.setRegion(SpatialCoordinate.scoord(SpatialCoordinate.POLYLINE,
				new float[] { 10f, 20f, 110f, 20f }, img));
		g.add(new Measurement(SRCodes.LENGTH, 42.35, SRCodes.U_MM));
		g.add(new Measurement(SRCodes.MEAN, 48.0, SRCodes.U_HU));
		report.add(g);
		return report;
	}

	@Test
	public void writer2dProducesComprehensiveSrWithMeasurementTree() {
		Attributes sr = new Tid1500Writer().build(referenceInstance(), sample2dReport());

		// no 3D content -> plain Comprehensive SR; patient/study inherited
		assertEquals(UID.ComprehensiveSRStorage.uid(), sr.getString(Tag.SOPClassUID));
		assertEquals("PID-001", sr.getString(Tag.PatientID));
		assertEquals("SR", sr.getString(Tag.Modality));

		ContentItem root = SRReader.read(sr);
		assertEquals("CONTAINER", root.getValueType());
		assertEquals("Imaging Measurement Report", root.conceptMeaning());

		ContentItem measurements = findContainer(root, "Imaging Measurements");
		assertNotNull("Imaging Measurements container present", measurements);
		ContentItem group = findContainer(measurements, "Measurement Group");
		assertNotNull("Measurement Group container present", group);

		// tracking id, region SCOORD and two NUM measurements all survive
		boolean trackingId = false, scoord = false;
		double length = Double.NaN, mean = Double.NaN;
		String lengthUnit = null;
		for (ContentItem c : group.getChildren()) {
			if ("TEXT".equals(c.getValueType()) && "Lesion 1".equals(c.getTextValue())) {
				trackingId = true;
			}
			if ("SCOORD".equals(c.getValueType())) {
				scoord = true;
				assertEquals(SpatialCoordinate.POLYLINE, c.getGraphicType());
				assertEquals(4, c.getGraphicData().length);
				// SCOORD anchors to image via SELECTED FROM IMAGE child
				assertTrue(hasImageChild(c, "1.2.3.sop"));
			}
			if ("NUM".equals(c.getValueType())) {
				String m = c.conceptMeaning();
				if ("Length".equals(m)) {
					length = Double.parseDouble(c.getNumericValue());
					lengthUnit = c.getUnit() == null ? null : c.getUnit().getCodeValue();
				} else if ("Mean".equals(m)) {
					mean = Double.parseDouble(c.getNumericValue());
				}
			}
		}
		assertTrue("tracking identifier", trackingId);
		assertTrue("region SCOORD", scoord);
		assertEquals(42.35, length, 1e-6);
		assertEquals(48.0, mean, 1e-6);
		assertEquals("mm", lengthUnit);
	}

	@Test
	public void writer3dUsesComprehensive3dSrAndKeepsScoord3d() {
		MeasurementReport report = new MeasurementReport("PID-001", "1.2.3.study", "3D Measurements");
		MeasurementGroup g = new MeasurementGroup("Distance A");
		g.setRegion(SpatialCoordinate.scoord3d(SpatialCoordinate.POLYLINE,
				new float[] { 0f, 0f, 0f, 30f, 40f, 0f }, "1.2.3.for"));
		g.add(new Measurement(SRCodes.LENGTH, 50.0, SRCodes.U_MM));
		report.add(g);

		assertTrue(report.hasSpatial3D());
		Attributes sr = new Tid1500Writer().build(referenceInstance(), report);
		assertEquals(UID.Comprehensive3DSRStorage.uid(), sr.getString(Tag.SOPClassUID));

		ContentItem root = SRReader.read(sr);
		ContentItem group = findContainer(findContainer(root, "Imaging Measurements"), "Measurement Group");
		ContentItem coord = null;
		for (ContentItem c : group.getChildren()) {
			if ("SCOORD3D".equals(c.getValueType())) {
				coord = c;
			}
		}
		assertNotNull("SCOORD3D region present", coord);
		assertEquals(SpatialCoordinate.POLYLINE, coord.getGraphicType());
		assertEquals(6, coord.getGraphicData().length);
		assertEquals("1.2.3.for", coord.getReferencedFrameOfReferenceUID());
	}

	@Test
	public void measurementSrRendersToHtml() {
		Attributes sr = new Tid1500Writer().build(referenceInstance(), sample2dReport());
		String html = SRtoHtml.toHtml(sr);
		assertTrue(html.contains("Imaging Measurement Report"));
		assertTrue(html.contains("Measurement Group"));
		assertTrue("length value rendered", html.contains("42.35"));
		assertTrue("unit rendered", html.contains("mm"));
		assertTrue("region rendered", html.contains("region"));
		assertTrue(html.contains("Yamada"));
	}

	@Test
	public void numberFormattingTrimsAndBounds() {
		assertEquals("5", Tid1500Writer.formatNum(5.0));
		assertEquals("42.35", Tid1500Writer.formatNum(42.35));
		assertEquals("-3.5", Tid1500Writer.formatNum(-3.5));
		assertTrue(Tid1500Writer.formatNum(1.0 / 3.0).length() <= 16);
	}

	// --- helpers ---------------------------------------------------------------

	private static ContentItem findContainer(ContentItem parent, String conceptMeaning) {
		if (parent == null) {
			return null;
		}
		for (ContentItem c : parent.getChildren()) {
			if ("CONTAINER".equals(c.getValueType()) && conceptMeaning.equals(c.conceptMeaning())) {
				return c;
			}
		}
		return null;
	}

	private static boolean hasImageChild(ContentItem scoord, String sopUID) {
		for (ContentItem c : scoord.getChildren()) {
			if ("IMAGE".equals(c.getValueType()) && sopUID.equals(c.getRefSopInstanceUID())) {
				return true;
			}
		}
		return false;
	}
}
