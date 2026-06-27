package com.vis.core.reporting.sr;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;

import com.vis.core.reporting.KeyImageRef;
import com.vis.core.reporting.ReportDocument;

/**
 * Builds a DICOM Key Object Selection (KO) document from the key images
 * embedded in a {@link ReportDocument}.
 * <p>
 * SOP Class: 1.2.840.10008.5.1.4.1.1.88.59 (Key Object Selection Document
 * Storage). The KO is stored separately from the free-text SR — it groups the
 * same key images in the format PACS viewers expect for quick key-image
 * navigation.
 *
 * @author tatsunidas
 */
public class KeyObjectWriter {

    private static final String KO_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.88.59";

    /**
     * @param ref reference instance dataset for patient/study identity inheritance.
     * @param doc finalized report whose key images populate the KO.
     * @return a complete KO dataset, or {@code null} if there are no key images.
     */
    public Attributes build(Attributes ref, ReportDocument doc) {
        List<KeyImageRef> keyImages = doc.getKeyImages();
        if (keyImages == null || keyImages.isEmpty()) {
            return null;
        }

        Attributes ko = new Attributes();
        SrCommon.inheritIdentity(ko, ref);
        SrCommon.fillSrHeader(ko, KO_SOP_CLASS, new Date(), "902");

        // KO uses Modality "KO", not "SR"
        ko.setString(Tag.Modality, VR.CS, "KO");

        // SR Document Content module — root CONTAINER
        ko.setString(Tag.ValueType, VR.CS, "CONTAINER");
        SrCommon.setConceptName(ko, SRCodes.KEY_IMAGE); // (113000, DCM, "Of Interest")
        ko.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");

        // Author / verifier / participant attribution (with job roles)
        SrCommon.addObservers(ko, doc.getParticipantsForSr(), new Date());

        // IMAGE content items
        Sequence content = ko.newSequence(Tag.ContentSequence, keyImages.size());
        for (KeyImageRef imgRef : keyImages) {
            if (imgRef.getSopUID() == null || imgRef.getSopClassUID() == null) {
                continue;
            }
            Attributes ci = new Attributes();
            ci.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
            ci.setString(Tag.ValueType, VR.CS, "IMAGE");
            // No ConceptName on IMAGE items in KO (optional per DICOM PS3.3 A.35.4)
            Sequence refSop = ci.newSequence(Tag.ReferencedSOPSequence, 1);
            Attributes sopItem = new Attributes();
            sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, imgRef.getSopClassUID());
            sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, imgRef.getSopUID());
            if (imgRef.getFrame() > 0) {
                sopItem.setInt(Tag.ReferencedFrameNumber, VR.IS, imgRef.getFrame());
            }
            refSop.add(sopItem);
            content.add(ci);
        }

        // CurrentRequestedProcedureEvidenceSequence — required for KO
        addEvidence(ko, doc, keyImages);

        return ko;
    }

    private void addEvidence(Attributes ko, ReportDocument doc, List<KeyImageRef> keyImages) {
        Sequence evidence = ko.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);

        Map<String, List<KeyImageRef>> bySeries = new LinkedHashMap<>();
        for (KeyImageRef ref : keyImages) {
            if (ref.getSeriesUID() == null || ref.getSopUID() == null || ref.getSopClassUID() == null) {
                continue;
            }
            bySeries.computeIfAbsent(ref.getSeriesUID(), k -> new ArrayList<>()).add(ref);
        }
        if (bySeries.isEmpty()) {
            return;
        }

        Attributes studyItem = new Attributes();
        studyItem.setString(Tag.StudyInstanceUID, VR.UI, doc.getStudyUID());
        Sequence seriesSeq = studyItem.newSequence(Tag.ReferencedSeriesSequence, bySeries.size());

        for (Map.Entry<String, List<KeyImageRef>> e : bySeries.entrySet()) {
            Attributes seriesItem = new Attributes();
            seriesItem.setString(Tag.SeriesInstanceUID, VR.UI, e.getKey());
            Sequence sopSeq = seriesItem.newSequence(Tag.ReferencedSOPSequence, e.getValue().size());
            for (KeyImageRef ref : e.getValue()) {
                Attributes refSop = new Attributes();
                refSop.setString(Tag.ReferencedSOPClassUID, VR.UI, ref.getSopClassUID());
                refSop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ref.getSopUID());
                sopSeq.add(refSop);
            }
            seriesSeq.add(seriesItem);
        }

        evidence.add(studyItem);
    }
}
