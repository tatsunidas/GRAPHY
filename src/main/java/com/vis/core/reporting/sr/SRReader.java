package com.vis.core.reporting.sr;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;

/**
 * Parses an SR-family dataset into a generic {@link ContentItem} tree. Modality
 * agnostic, so it handles free-text SR, RDSR, KO, etc. with the same walk.
 *
 * @author tatsunidas
 */
public final class SRReader {

	private SRReader() {
	}

	/**
	 * @param sr a read SR dataset whose root attributes carry ValueType=CONTAINER
	 *           and the SR Document Content module.
	 * @return the root content item.
	 */
	public static ContentItem read(Attributes sr) {
		return parseItem(sr, null);
	}

	private static ContentItem parseItem(Attributes item, String relationship) {
		ContentItem ci = new ContentItem();
		ci.setRelationshipType(relationship);
		ci.setValueType(item.getString(Tag.ValueType));

		Attributes cn = item.getNestedDataset(Tag.ConceptNameCodeSequence);
		if (cn != null) {
			ci.setConceptName(new Code(cn));
		}

		String vt = ci.getValueType() == null ? "" : ci.getValueType();
		switch (vt) {
		case "TEXT":
			ci.setTextValue(item.getString(Tag.TextValue));
			break;
		case "CODE": {
			Attributes cc = item.getNestedDataset(Tag.ConceptCodeSequence);
			if (cc != null) {
				ci.setCode(new Code(cc));
			}
			break;
		}
		case "NUM": {
			Attributes mv = item.getNestedDataset(Tag.MeasuredValueSequence);
			if (mv != null) {
				ci.setNumericValue(mv.getString(Tag.NumericValue));
				Attributes u = mv.getNestedDataset(Tag.MeasurementUnitsCodeSequence);
				if (u != null) {
					ci.setUnit(new Code(u));
				}
			}
			break;
		}
		case "DATETIME":
			ci.setDateTime(item.getString(Tag.DateTime));
			break;
		case "DATE":
			ci.setDateTime(item.getString(Tag.Date));
			break;
		case "TIME":
			ci.setDateTime(item.getString(Tag.Time));
			break;
		case "UIDREF":
			ci.setUidRef(item.getString(Tag.UID));
			break;
		case "PNAME":
			ci.setPersonName(item.getString(Tag.PersonName));
			break;
		case "IMAGE":
		case "COMPOSITE":
		case "WAVEFORM": {
			Attributes rs = item.getNestedDataset(Tag.ReferencedSOPSequence);
			if (rs != null) {
				ci.setRefSopClassUID(rs.getString(Tag.ReferencedSOPClassUID));
				ci.setRefSopInstanceUID(rs.getString(Tag.ReferencedSOPInstanceUID));
			}
			break;
		}
		default:
			break;
		}

		Sequence cs = item.getSequence(Tag.ContentSequence);
		if (cs != null) {
			for (Attributes child : cs) {
				ci.getChildren().add(parseItem(child, child.getString(Tag.RelationshipType)));
			}
		}
		return ci;
	}
}
