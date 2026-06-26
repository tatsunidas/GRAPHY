package com.vis.core.ui.main.dcmtreetable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Regression test for the tree Report column: {@link DICOMNode#setData(String, String)}
 * rejects any key absent from the node's backing map, so the {@code ReportState} /
 * {@code ReportCount} keys MUST be pre-seeded by the constructor — otherwise
 * {@code DICOMNodeBuilder} silently fails to stamp the report state and the column
 * renders blank (the symptom that motivated this test).
 */
public class DICOMNodeReportStateTest {

	private static DICOMNode newStudyNode() {
		return new DICOMNode(DICOMNode.STUDY, "Name", "PID", "20260101", null, null, null, "desc", null,
				"CT", null, null, null, null, null, null, null, null, "ACC", "1", "1",
				"1.2.3.study", null, null, null);
	}

	@Test
	public void reportStateKeyIsAcceptedBySetData() {
		DICOMNode study = newStudyNode();
		// keys pre-seeded -> null until stamped
		assertNull(study.getData(DICOMNode.ReportState));
		assertNull(study.getData(DICOMNode.ReportCount));

		study.setData(DICOMNode.ReportState, "report");
		study.setData(DICOMNode.ReportCount, "3");

		assertEquals("report", study.getData(DICOMNode.ReportState));
		assertEquals("3", study.getData(DICOMNode.ReportCount));
	}

	@Test
	public void copyConstructorPreservesReportState() {
		DICOMNode study = newStudyNode();
		study.setData(DICOMNode.ReportState, "draft");
		DICOMNode copy = new DICOMNode(study);
		assertEquals("draft", copy.getData(DICOMNode.ReportState));
	}
}
