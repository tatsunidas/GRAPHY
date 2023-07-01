/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License")), you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
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
 * This file is generated from Part 6 of the Standard Text Edition 2011.
 */
 
package com.vis.dicom;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author tatsunidas
 */
public enum UID {

//    private static final ResourceBundle rb(
//            ResourceBundle.getBundle("org.dcm4che3.data.UIDNames")),
//
//    public static String nameOf(String uid) {
//        try {
//            return rb.getString(uid)),
//        } catch (Exception e) {
//            return "?"),
//        }
//    }
//
//    public static String forName(String keyword) {
//        try {
//            return (String) UID.class.getField(keyword).get(null)),
//        } catch (Exception e) {
//            throw new IllegalArgumentException(keyword)),
//        }
//    }

    /** Verification SOP Class, SOPClass */
    Verification("1.2.840.10008.1.1"),

    /** Implicit VR Little Endian, TransferSyntax */
    ImplicitVRLittleEndian("1.2.840.10008.1.2"),

    /** Explicit VR Little Endian, TransferSyntax */
    ExplicitVRLittleEndian("1.2.840.10008.1.2.1"),

    /** Deflated Explicit VR Little Endian, TransferSyntax */
    DeflatedExplicitVRLittleEndian("1.2.840.10008.1.2.1.99"),

    /** Explicit VR Big Endian (Retired), TransferSyntax */
    ExplicitVRBigEndian("1.2.840.10008.1.2.2"),

    /** JPEG Baseline (Process 1), TransferSyntax */
    JPEGBaseline8Bit("1.2.840.10008.1.2.4.50"),

    /** JPEG Extended (Process 2 & 4), TransferSyntax */
    JPEGExtended12Bit("1.2.840.10008.1.2.4.51"),

    /** JPEG Extended (Process 3 & 5) (Retired), TransferSyntax */
    JPEGExtended35("1.2.840.10008.1.2.4.52"),

    /** JPEG Spectral Selection, Non-Hierarchical (Process 6 & 8) (Retired), TransferSyntax */
    JPEGSpectralSelectionNonHierarchical68("1.2.840.10008.1.2.4.53"),

    /** JPEG Spectral Selection, Non-Hierarchical (Process 7 & 9) (Retired), TransferSyntax */
    JPEGSpectralSelectionNonHierarchical79("1.2.840.10008.1.2.4.54"),

    /** JPEG Full Progression, Non-Hierarchical (Process 10 & 12) (Retired), TransferSyntax */
    JPEGFullProgressionNonHierarchical1012("1.2.840.10008.1.2.4.55"),

    /** JPEG Full Progression, Non-Hierarchical (Process 11 & 13) (Retired), TransferSyntax */
    JPEGFullProgressionNonHierarchical1113("1.2.840.10008.1.2.4.56"),

    /** JPEG Lossless, Non-Hierarchical (Process 14), TransferSyntax */
    JPEGLossless("1.2.840.10008.1.2.4.57"),

    /** JPEG Lossless, Non-Hierarchical (Process 15) (Retired), TransferSyntax */
    JPEGLosslessNonHierarchical15("1.2.840.10008.1.2.4.58"),

    /** JPEG Extended, Hierarchical (Process 16 & 18) (Retired), TransferSyntax */
    JPEGExtendedHierarchical1618("1.2.840.10008.1.2.4.59"),

    /** JPEG Extended, Hierarchical (Process 17 & 19) (Retired), TransferSyntax */
    JPEGExtendedHierarchical1719("1.2.840.10008.1.2.4.60"),

    /** JPEG Spectral Selection, Hierarchical (Process 20 & 22) (Retired), TransferSyntax */
    JPEGSpectralSelectionHierarchical2022("1.2.840.10008.1.2.4.61"),

    /** JPEG Spectral Selection, Hierarchical (Process 21 & 23) (Retired), TransferSyntax */
    JPEGSpectralSelectionHierarchical2123("1.2.840.10008.1.2.4.62"),

    /** JPEG Full Progression, Hierarchical (Process 24 & 26) (Retired), TransferSyntax */
    JPEGFullProgressionHierarchical2426("1.2.840.10008.1.2.4.63"),

    /** JPEG Full Progression, Hierarchical (Process 25 & 27) (Retired), TransferSyntax */
    JPEGFullProgressionHierarchical2527("1.2.840.10008.1.2.4.64"),

    /** JPEG Lossless, Hierarchical (Process 28) (Retired), TransferSyntax */
    JPEGLosslessHierarchical28("1.2.840.10008.1.2.4.65"),

    /** JPEG Lossless, Hierarchical (Process 29) (Retired), TransferSyntax */
    JPEGLosslessHierarchical29("1.2.840.10008.1.2.4.66"),

    /** JPEG Lossless, Non-Hierarchical, First-Order Prediction (Process 14 [Selection Value 1]), TransferSyntax */
    JPEGLosslessSV1("1.2.840.10008.1.2.4.70"),

    /** JPEG-LS Lossless Image Compression, TransferSyntax */
    JPEGLSLossless("1.2.840.10008.1.2.4.80"),

    /** JPEG-LS Lossy (Near-Lossless) Image Compression, TransferSyntax */
    JPEGLSNearLossless("1.2.840.10008.1.2.4.81"),

    /** JPEG 2000 Image Compression (Lossless Only), TransferSyntax */
    JPEG2000Lossless("1.2.840.10008.1.2.4.90"),

    /** JPEG 2000 Image Compression, TransferSyntax */
    JPEG2000("1.2.840.10008.1.2.4.91"),

    /** JPEG 2000 Part 2 Multi-component Image Compression (Lossless Only), TransferSyntax */
    JPEG2000MCLossless("1.2.840.10008.1.2.4.92"),

    /** JPEG 2000 Part 2 Multi-component Image Compression, TransferSyntax */
    JPEG2000MC("1.2.840.10008.1.2.4.93"),

    /** JPIP Referenced, TransferSyntax */
    JPIPReferenced("1.2.840.10008.1.2.4.94"),

    /** JPIP Referenced Deflate, TransferSyntax */
    JPIPReferencedDeflate("1.2.840.10008.1.2.4.95"),

    /** MPEG2 Main Profile / Main Level, TransferSyntax */
    MPEG2MPML("1.2.840.10008.1.2.4.100"),

    /** MPEG2 Main Profile / High Level, TransferSyntax */
    MPEG2MPHL("1.2.840.10008.1.2.4.101"),

    /** MPEG-4 AVC/H.264 High Profile / Level 4.1, TransferSyntax */
    MPEG4HP41("1.2.840.10008.1.2.4.102"),

    /** MPEG-4 AVC/H.264 BD-compatible High Profile / Level 4.1, TransferSyntax */
    MPEG4HP41BD("1.2.840.10008.1.2.4.103"),

    /** MPEG-4 AVC/H.264 High Profile / Level 4.2 For 2D Video, TransferSyntax */
    MPEG4HP422D("1.2.840.10008.1.2.4.104"),

    /** MPEG-4 AVC/H.264 High Profile / Level 4.2 For 3D Video, TransferSyntax */
    MPEG4HP423D("1.2.840.10008.1.2.4.105"),

    /** MPEG-4 AVC/H.264 Stereo High Profile / Level 4.2, TransferSyntax */
    MPEG4HP42STEREO("1.2.840.10008.1.2.4.106"),

    /** HEVC/H.265 Main Profile / Level 5.1, TransferSyntax */
    HEVCMP51("1.2.840.10008.1.2.4.107"),

    /** HEVC/H.265 Main 10 Profile / Level 5.1, TransferSyntax */
    HEVCM10P51("1.2.840.10008.1.2.4.108"),

    /** RLE Lossless, TransferSyntax */
    RLELossless("1.2.840.10008.1.2.5"),

    /** RFC 2557 MIME encapsulation (Retired), TransferSyntax */
    RFC2557MIMEEncapsulation("1.2.840.10008.1.2.6.1"),

    /** XML Encoding (Retired), TransferSyntax */
    XMLEncoding("1.2.840.10008.1.2.6.2"),

    /** SMPTE ST 2110-20 Uncompressed Progressive Active Video, TransferSyntax */
    SMPTEST211020UncompressedProgressiveActiveVideo("1.2.840.10008.1.2.7.1"),

    /** SMPTE ST 2110-20 Uncompressed Interlaced Active Video, TransferSyntax */
    SMPTEST211020UncompressedInterlacedActiveVideo("1.2.840.10008.1.2.7.2"),

    /** SMPTE ST 2110-30 PCM Digital Audio, TransferSyntax */
    SMPTEST211030PCMDigitalAudio("1.2.840.10008.1.2.7.3"),

    /** Media Storage Directory Storage, SOPClass */
    MediaStorageDirectoryStorage("1.2.840.10008.1.3.10"),

    /** Talairach Brain Atlas Frame of Reference, WellKnownFrameOfReference */
    TalairachBrainAtlas("1.2.840.10008.1.4.1.1"),

    /** SPM2 T1 Frame of Reference, WellKnownFrameOfReference */
    SPM2T1("1.2.840.10008.1.4.1.2"),

    /** SPM2 T2 Frame of Reference, WellKnownFrameOfReference */
    SPM2T2("1.2.840.10008.1.4.1.3"),

    /** SPM2 PD Frame of Reference, WellKnownFrameOfReference */
    SPM2PD("1.2.840.10008.1.4.1.4"),

    /** SPM2 EPI Frame of Reference, WellKnownFrameOfReference */
    SPM2EPI("1.2.840.10008.1.4.1.5"),

    /** SPM2 FIL T1 Frame of Reference, WellKnownFrameOfReference */
    SPM2FILT1("1.2.840.10008.1.4.1.6"),

    /** SPM2 PET Frame of Reference, WellKnownFrameOfReference */
    SPM2PET("1.2.840.10008.1.4.1.7"),

    /** SPM2 TRANSM Frame of Reference, WellKnownFrameOfReference */
    SPM2TRANSM("1.2.840.10008.1.4.1.8"),

    /** SPM2 SPECT Frame of Reference, WellKnownFrameOfReference */
    SPM2SPECT("1.2.840.10008.1.4.1.9"),

    /** SPM2 GRAY Frame of Reference, WellKnownFrameOfReference */
    SPM2GRAY("1.2.840.10008.1.4.1.10"),

    /** SPM2 WHITE Frame of Reference, WellKnownFrameOfReference */
    SPM2WHITE("1.2.840.10008.1.4.1.11"),

    /** SPM2 CSF Frame of Reference, WellKnownFrameOfReference */
    SPM2CSF("1.2.840.10008.1.4.1.12"),

    /** SPM2 BRAINMASK Frame of Reference, WellKnownFrameOfReference */
    SPM2BRAINMASK("1.2.840.10008.1.4.1.13"),

    /** SPM2 AVG305T1 Frame of Reference, WellKnownFrameOfReference */
    SPM2AVG305T1("1.2.840.10008.1.4.1.14"),

    /** SPM2 AVG152T1 Frame of Reference, WellKnownFrameOfReference */
    SPM2AVG152T1("1.2.840.10008.1.4.1.15"),

    /** SPM2 AVG152T2 Frame of Reference, WellKnownFrameOfReference */
    SPM2AVG152T2("1.2.840.10008.1.4.1.16"),

    /** SPM2 AVG152PD Frame of Reference, WellKnownFrameOfReference */
    SPM2AVG152PD("1.2.840.10008.1.4.1.17"),

    /** SPM2 SINGLESUBJT1 Frame of Reference, WellKnownFrameOfReference */
    SPM2SINGLESUBJT1("1.2.840.10008.1.4.1.18"),

    /** ICBM 452 T1 Frame of Reference, WellKnownFrameOfReference */
    ICBM452T1("1.2.840.10008.1.4.2.1"),

    /** ICBM Single Subject MRI Frame of Reference, WellKnownFrameOfReference */
    ICBMSingleSubjectMRI("1.2.840.10008.1.4.2.2"),

    /** IEC 61217 Fixed Coordinate System Frame of Reference, WellKnownFrameOfReference */
    IEC61217FixedCoordinateSystem("1.2.840.10008.1.4.3.1"),

    /** Standard Robotic-Arm Coordinate System Frame of Reference, WellKnownFrameOfReference */
    StandardRoboticArmCoordinateSystem("1.2.840.10008.1.4.3.2"),

    /** SRI24 Frame of Reference, WellKnownFrameOfReference */
    SRI24("1.2.840.10008.1.4.4.1"),

    /** Colin27 Frame of Reference, WellKnownFrameOfReference */
    Colin27("1.2.840.10008.1.4.5.1"),

    /** LPBA40/AIR Frame of Reference, WellKnownFrameOfReference */
    LPBA40AIR("1.2.840.10008.1.4.6.1"),

    /** LPBA40/FLIRT Frame of Reference, WellKnownFrameOfReference */
    LPBA40FLIRT("1.2.840.10008.1.4.6.2"),

    /** LPBA40/SPM5 Frame of Reference, WellKnownFrameOfReference */
    LPBA40SPM5("1.2.840.10008.1.4.6.3"),

    /** Hot Iron Color Palette SOP Instance, WellKnownSOPInstance */
    HotIronPalette("1.2.840.10008.1.5.1"),

    /** PET Color Palette SOP Instance, WellKnownSOPInstance */
    PETPalette("1.2.840.10008.1.5.2"),

    /** Hot Metal Blue Color Palette SOP Instance, WellKnownSOPInstance */
    HotMetalBluePalette("1.2.840.10008.1.5.3"),

    /** PET 20 Step Color Palette SOP Instance, WellKnownSOPInstance */
    PET20StepPalette("1.2.840.10008.1.5.4"),

    /** Spring Color Palette SOP Instance, WellKnownSOPInstance */
    SpringPalette("1.2.840.10008.1.5.5"),

    /** Summer Color Palette SOP Instance, WellKnownSOPInstance */
    SummerPalette("1.2.840.10008.1.5.6"),

    /** Fall Color Palette SOP Instance, WellKnownSOPInstance */
    FallPalette("1.2.840.10008.1.5.7"),

    /** Winter Color Palette SOP Instance, WellKnownSOPInstance */
    WinterPalette("1.2.840.10008.1.5.8"),

    /** Basic Study Content Notification SOP Class (Retired), SOPClass */
    BasicStudyContentNotification("1.2.840.10008.1.9"),

    /** Papyrus 3 Implicit VR Little Endian (Retired), TransferSyntax */
    Papyrus3ImplicitVRLittleEndian("1.2.840.10008.1.20"),

    /** Storage Commitment Push Model SOP Class, SOPClass */
    StorageCommitmentPushModel("1.2.840.10008.1.20.1"),

    /** Storage Commitment Push Model SOP Instance, WellKnownSOPInstance */
    StorageCommitmentPushModelInstance("1.2.840.10008.1.20.1.1"),

    /** Storage Commitment Pull Model SOP Class (Retired), SOPClass */
    StorageCommitmentPullModel("1.2.840.10008.1.20.2"),

    /** Storage Commitment Pull Model SOP Instance (Retired), WellKnownSOPInstance */
    StorageCommitmentPullModelInstance("1.2.840.10008.1.20.2.1"),

    /** Procedural Event Logging SOP Class, SOPClass */
    ProceduralEventLogging("1.2.840.10008.1.40"),

    /** Procedural Event Logging SOP Instance, WellKnownSOPInstance */
    ProceduralEventLoggingInstance("1.2.840.10008.1.40.1"),

    /** Substance Administration Logging SOP Class, SOPClass */
    SubstanceAdministrationLogging("1.2.840.10008.1.42"),

    /** Substance Administration Logging SOP Instance, WellKnownSOPInstance */
    SubstanceAdministrationLoggingInstance("1.2.840.10008.1.42.1"),

    /** DICOM UID Registry, DICOMUIDsAsACodingScheme */
    DCMUID("1.2.840.10008.2.6.1"),

    /** DICOM Controlled Terminology, CodingScheme */
    DCM("1.2.840.10008.2.16.4"),

    /** Adult Mouse Anatomy Ontology, CodingScheme */
    MA("1.2.840.10008.2.16.5"),

    /** Uberon Ontology, CodingScheme */
    UBERON("1.2.840.10008.2.16.6"),

    /** Integrated Taxonomic Information System (ITIS) Taxonomic Serial Number (TSN), CodingScheme */
    ITIS_TSN("1.2.840.10008.2.16.7"),

    /** Mouse Genome Initiative (MGI), CodingScheme */
    MGI("1.2.840.10008.2.16.8"),

    /** PubChem Compound CID, CodingScheme */
    PUBCHEM_CID("1.2.840.10008.2.16.9"),

    /** Dublin Core, CodingScheme */
    DC("1.2.840.10008.2.16.10"),

    /** New York University Melanoma Clinical Cooperative Group, CodingScheme */
    NYUMCCG("1.2.840.10008.2.16.11"),

    /** Mayo Clinic Non-radiological Images Specific Body Structure Anatomical Surface Region Guide, CodingScheme */
    MAYONRISBSASRG("1.2.840.10008.2.16.12"),

    /** Image Biomarker Standardisation Initiative, CodingScheme */
    IBSI("1.2.840.10008.2.16.13"),

    /** Radiomics Ontology, CodingScheme */
    RO("1.2.840.10008.2.16.14"),

    /** RadElement, CodingScheme */
    RADELEMENT("1.2.840.10008.2.16.15"),

    /** ICD-11, CodingScheme */
    I11("1.2.840.10008.2.16.16"),

    /** DICOM Application Context Name, ApplicationContextName */
    DICOMApplicationContext("1.2.840.10008.3.1.1.1"),

    /** Detached Patient Management SOP Class (Retired), SOPClass */
    DetachedPatientManagement ("1.2.840.10008.3.1.2.1.1"),

    /** Detached Patient Management Meta SOP Class (Retired), MetaSOPClass */
    DetachedPatientManagementMeta("1.2.840.10008.3.1.2.1.4"),

    /** Detached Visit Management SOP Class (Retired), SOPClass */
    DetachedVisitManagement("1.2.840.10008.3.1.2.2.1"),

    /** Detached Study Management SOP Class (Retired), SOPClass */
    DetachedStudyManagement ("1.2.840.10008.3.1.2.3.1"),

    /** Study Component Management SOP Class (Retired), SOPClass */
    StudyComponentManagement ("1.2.840.10008.3.1.2.3.2"),

    /** Modality Performed Procedure Step SOP Class, SOPClass */
    ModalityPerformedProcedureStep("1.2.840.10008.3.1.2.3.3"),

    /** Modality Performed Procedure Step Retrieve SOP Class, SOPClass */
    ModalityPerformedProcedureStepRetrieve("1.2.840.10008.3.1.2.3.4"),

    /** Modality Performed Procedure Step Notification SOP Class, SOPClass */
    ModalityPerformedProcedureStepNotification("1.2.840.10008.3.1.2.3.5"),

    /** Detached Results Management SOP Class (Retired), SOPClass */
    DetachedResultsManagement("1.2.840.10008.3.1.2.5.1"),

    /** Detached Results Management Meta SOP Class (Retired), MetaSOPClass */
    DetachedResultsManagementMeta("1.2.840.10008.3.1.2.5.4"),

    /** Detached Study Management Meta SOP Class (Retired), MetaSOPClass */
    DetachedStudyManagementMeta("1.2.840.10008.3.1.2.5.5"),

    /** Detached Interpretation Management SOP Class (Retired), SOPClass */
    DetachedInterpretationManagement("1.2.840.10008.3.1.2.6.1"),

    /** Storage Service Class, ServiceClass */
    Storage("1.2.840.10008.4.2"),

    /** Basic Film Session SOP Class, SOPClass */
    BasicFilmSession("1.2.840.10008.5.1.1.1"),

    /** Basic Film Box SOP Class, SOPClass */
    BasicFilmBox("1.2.840.10008.5.1.1.2"),

    /** Basic Grayscale Image Box SOP Class, SOPClass */
    BasicGrayscaleImageBox("1.2.840.10008.5.1.1.4"),

    /** Basic Color Image Box SOP Class, SOPClass */
    BasicColorImageBox("1.2.840.10008.5.1.1.4.1"),

    /** Referenced Image Box SOP Class (Retired), SOPClass */
    ReferencedImageBox("1.2.840.10008.5.1.1.4.2"),

    /** Basic Grayscale Print Management Meta SOP Class, MetaSOPClass */
    BasicGrayscalePrintManagementMeta("1.2.840.10008.5.1.1.9"),

    /** Referenced Grayscale Print Management Meta SOP Class (Retired), MetaSOPClass */
    ReferencedGrayscalePrintManagementMeta("1.2.840.10008.5.1.1.9.1"),

    /** Print Job SOP Class, SOPClass */
    PrintJob("1.2.840.10008.5.1.1.14"),

    /** Basic Annotation Box SOP Class, SOPClass */
    BasicAnnotationBox("1.2.840.10008.5.1.1.15"),

    /** Printer SOP Class, SOPClass */
    Printer("1.2.840.10008.5.1.1.16"),

    /** Printer Configuration Retrieval SOP Class, SOPClass */
    PrinterConfigurationRetrieval("1.2.840.10008.5.1.1.16.376"),

    /** Printer SOP Instance, WellKnownPrinterSOPInstance */
    PrinterInstance("1.2.840.10008.5.1.1.17"),

    /** Printer Configuration Retrieval SOP Instance, WellKnownPrinterSOPInstance */
    PrinterConfigurationRetrievalInstance("1.2.840.10008.5.1.1.17.376"),

    /** Basic Color Print Management Meta SOP Class, MetaSOPClass */
    BasicColorPrintManagementMeta("1.2.840.10008.5.1.1.18"),

    /** Referenced Color Print Management Meta SOP Class (Retired), MetaSOPClass */
    ReferencedColorPrintManagementMeta("1.2.840.10008.5.1.1.18.1"),

    /** VOI LUT Box SOP Class, SOPClass */
    VOILUTBox("1.2.840.10008.5.1.1.22"),

    /** Presentation LUT SOP Class, SOPClass */
    PresentationLUT("1.2.840.10008.5.1.1.23"),

    /** Image Overlay Box SOP Class (Retired), SOPClass */
    ImageOverlayBox("1.2.840.10008.5.1.1.24"),

    /** Basic Print Image Overlay Box SOP Class (Retired), SOPClass */
    BasicPrintImageOverlayBox("1.2.840.10008.5.1.1.24.1"),

    /** Print Queue SOP Instance (Retired), WellKnownPrintQueueSOPInstance */
    PrintQueue ("1.2.840.10008.5.1.1.25"),

    /** Print Queue Management SOP Class (Retired), SOPClass */
    PrintQueueManagement ("1.2.840.10008.5.1.1.26"),

    /** Stored Print Storage SOP Class (Retired), SOPClass */
    StoredPrintStorage("1.2.840.10008.5.1.1.27"),

    /** Hardcopy Grayscale Image Storage SOP Class (Retired), SOPClass */
    HardcopyGrayscaleImageStorage("1.2.840.10008.5.1.1.29"),

    /** Hardcopy Color Image Storage SOP Class (Retired), SOPClass */
    HardcopyColorImageStorage ("1.2.840.10008.5.1.1.30"),

    /** Pull Print Request SOP Class (Retired), SOPClass */
    PullPrintRequest ("1.2.840.10008.5.1.1.31"),

    /** Pull Stored Print Management Meta SOP Class (Retired), MetaSOPClass */
    PullStoredPrintManagementMeta ("1.2.840.10008.5.1.1.32"),

    /** Media Creation Management SOP Class UID, SOPClass */
    MediaCreationManagement("1.2.840.10008.5.1.1.33"),

    /** Display System SOP Class, SOPClass */
    DisplaySystem("1.2.840.10008.5.1.1.40"),

    /** Display System SOP Instance, WellKnownSOPInstance */
    DisplaySystemInstance("1.2.840.10008.5.1.1.40.1"),

    /** Computed Radiography Image Storage, SOPClass */
    ComputedRadiographyImageStorage("1.2.840.10008.5.1.4.1.1.1"),

    /** Digital X-Ray Image Storage - For Presentation, SOPClass */
    DigitalXRayImageStorageForPresentation("1.2.840.10008.5.1.4.1.1.1.1"),

    /** Digital X-Ray Image Storage - For Processing, SOPClass */
    DigitalXRayImageStorageForProcessing("1.2.840.10008.5.1.4.1.1.1.1.1"),

    /** Digital Mammography X-Ray Image Storage - For Presentation, SOPClass */
    DigitalMammographyXRayImageStorageForPresentation("1.2.840.10008.5.1.4.1.1.1.2"),

    /** Digital Mammography X-Ray Image Storage - For Processing, SOPClass */
    DigitalMammographyXRayImageStorageForProcessing("1.2.840.10008.5.1.4.1.1.1.2.1"),

    /** Digital Intra-Oral X-Ray Image Storage - For Presentation, SOPClass */
    DigitalIntraOralXRayImageStorageForPresentation("1.2.840.10008.5.1.4.1.1.1.3"),

    /** Digital Intra-Oral X-Ray Image Storage - For Processing, SOPClass */
    DigitalIntraOralXRayImageStorageForProcessing("1.2.840.10008.5.1.4.1.1.1.3.1"),

    /** CT Image Storage, SOPClass */
    CTImageStorage("1.2.840.10008.5.1.4.1.1.2"),

    /** Enhanced CT Image Storage, SOPClass */
    EnhancedCTImageStorage("1.2.840.10008.5.1.4.1.1.2.1"),

    /** Legacy Converted Enhanced CT Image Storage, SOPClass */
    LegacyConvertedEnhancedCTImageStorage("1.2.840.10008.5.1.4.1.1.2.2"),

    /** Ultrasound Multi-frame Image Storage (Retired), SOPClass */
    UltrasoundMultiFrameImageStorageRetired("1.2.840.10008.5.1.4.1.1.3"),

    /** Ultrasound Multi-frame Image Storage, SOPClass */
    UltrasoundMultiFrameImageStorage("1.2.840.10008.5.1.4.1.1.3.1"),

    /** MR Image Storage, SOPClass */
    MRImageStorage("1.2.840.10008.5.1.4.1.1.4"),

    /** Enhanced MR Image Storage, SOPClass */
    EnhancedMRImageStorage("1.2.840.10008.5.1.4.1.1.4.1"),

    /** MR Spectroscopy Storage, SOPClass */
    MRSpectroscopyStorage("1.2.840.10008.5.1.4.1.1.4.2"),

    /** Enhanced MR Color Image Storage, SOPClass */
    EnhancedMRColorImageStorage("1.2.840.10008.5.1.4.1.1.4.3"),

    /** Legacy Converted Enhanced MR Image Storage, SOPClass */
    LegacyConvertedEnhancedMRImageStorage("1.2.840.10008.5.1.4.1.1.4.4"),

    /** Nuclear Medicine Image Storage (Retired), SOPClass */
    NuclearMedicineImageStorageRetired("1.2.840.10008.5.1.4.1.1.5"),

    /** Ultrasound Image Storage (Retired), SOPClass */
    UltrasoundImageStorageRetired("1.2.840.10008.5.1.4.1.1.6"),

    /** Ultrasound Image Storage, SOPClass */
    UltrasoundImageStorage("1.2.840.10008.5.1.4.1.1.6.1"),

    /** Enhanced US Volume Storage, SOPClass */
    EnhancedUSVolumeStorage("1.2.840.10008.5.1.4.1.1.6.2"),

    /** Secondary Capture Image Storage, SOPClass */
    SecondaryCaptureImageStorage("1.2.840.10008.5.1.4.1.1.7"),

    /** Multi-frame Single Bit Secondary Capture Image Storage, SOPClass */
    MultiFrameSingleBitSecondaryCaptureImageStorage("1.2.840.10008.5.1.4.1.1.7.1"),

    /** Multi-frame Grayscale Byte Secondary Capture Image Storage, SOPClass */
    MultiFrameGrayscaleByteSecondaryCaptureImageStorage("1.2.840.10008.5.1.4.1.1.7.2"),

    /** Multi-frame Grayscale Word Secondary Capture Image Storage, SOPClass */
    MultiFrameGrayscaleWordSecondaryCaptureImageStorage("1.2.840.10008.5.1.4.1.1.7.3"),

    /** Multi-frame True Color Secondary Capture Image Storage, SOPClass */
    MultiFrameTrueColorSecondaryCaptureImageStorage("1.2.840.10008.5.1.4.1.1.7.4"),

    /** Standalone Overlay Storage (Retired), SOPClass */
    StandaloneOverlayStorage("1.2.840.10008.5.1.4.1.1.8"),

    /** Standalone Curve Storage (Retired), SOPClass */
    StandaloneCurveStorage("1.2.840.10008.5.1.4.1.1.9"),

    /** Waveform Storage - Trial (Retired), SOPClass */
    WaveformStorageTrial("1.2.840.10008.5.1.4.1.1.9.1"),

    /** 12-lead ECG Waveform Storage, SOPClass */
    TwelveLeadECGWaveformStorage("1.2.840.10008.5.1.4.1.1.9.1.1"),

    /** General ECG Waveform Storage, SOPClass */
    GeneralECGWaveformStorage("1.2.840.10008.5.1.4.1.1.9.1.2"),

    /** Ambulatory ECG Waveform Storage, SOPClass */
    AmbulatoryECGWaveformStorage("1.2.840.10008.5.1.4.1.1.9.1.3"),

    /** Hemodynamic Waveform Storage, SOPClass */
    HemodynamicWaveformStorage("1.2.840.10008.5.1.4.1.1.9.2.1"),

    /** Cardiac Electrophysiology Waveform Storage, SOPClass */
    CardiacElectrophysiologyWaveformStorage("1.2.840.10008.5.1.4.1.1.9.3.1"),

    /** Basic Voice Audio Waveform Storage, SOPClass */
    BasicVoiceAudioWaveformStorage("1.2.840.10008.5.1.4.1.1.9.4.1"),

    /** General Audio Waveform Storage, SOPClass */
    GeneralAudioWaveformStorage("1.2.840.10008.5.1.4.1.1.9.4.2"),

    /** Arterial Pulse Waveform Storage, SOPClass */
    ArterialPulseWaveformStorage("1.2.840.10008.5.1.4.1.1.9.5.1"),

    /** Respiratory Waveform Storage, SOPClass */
    RespiratoryWaveformStorage("1.2.840.10008.5.1.4.1.1.9.6.1"),

    /** Multi-channel Respiratory Waveform Storage, SOPClass */
    MultichannelRespiratoryWaveformStorage("1.2.840.10008.5.1.4.1.1.9.6.2"),

    /** Routine Scalp Electroencephalogram Waveform Storage, SOPClass */
    RoutineScalpElectroencephalogramWaveformStorage("1.2.840.10008.5.1.4.1.1.9.7.1"),

    /** Electromyogram Waveform Storage, SOPClass */
    ElectromyogramWaveformStorage("1.2.840.10008.5.1.4.1.1.9.7.2"),

    /** Electrooculogram Waveform Storage, SOPClass */
    ElectrooculogramWaveformStorage("1.2.840.10008.5.1.4.1.1.9.7.3"),

    /** Sleep Electroencephalogram Waveform Storage, SOPClass */
    SleepElectroencephalogramWaveformStorage("1.2.840.10008.5.1.4.1.1.9.7.4"),

    /** Body Position Waveform Storage, SOPClass */
    BodyPositionWaveformStorage("1.2.840.10008.5.1.4.1.1.9.8.1"),

    /** Standalone Modality LUT Storage (Retired), SOPClass */
    StandaloneModalityLUTStorage("1.2.840.10008.5.1.4.1.1.10"),

    /** Standalone VOI LUT Storage (Retired), SOPClass */
    StandaloneVOILUTStorage("1.2.840.10008.5.1.4.1.1.11"),

    /** Grayscale Softcopy Presentation State Storage, SOPClass */
    GrayscaleSoftcopyPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.1"),

    /** Color Softcopy Presentation State Storage, SOPClass */
    ColorSoftcopyPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.2"),

    /** Pseudo-Color Softcopy Presentation State Storage, SOPClass */
    PseudoColorSoftcopyPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.3"),

    /** Blending Softcopy Presentation State Storage, SOPClass */
    BlendingSoftcopyPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.4"),

    /** XA/XRF Grayscale Softcopy Presentation State Storage, SOPClass */
    XAXRFGrayscaleSoftcopyPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.5"),

    /** Grayscale Planar MPR Volumetric Presentation State Storage, SOPClass */
    GrayscalePlanarMPRVolumetricPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.6"),

    /** Compositing Planar MPR Volumetric Presentation State Storage, SOPClass */
    CompositingPlanarMPRVolumetricPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.7"),

    /** Advanced Blending Presentation State Storage, SOPClass */
    AdvancedBlendingPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.8"),

    /** Volume Rendering Volumetric Presentation State Storage, SOPClass */
    VolumeRenderingVolumetricPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.9"),

    /** Segmented Volume Rendering Volumetric Presentation State Storage, SOPClass */
    SegmentedVolumeRenderingVolumetricPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.10"),

    /** Multiple Volume Rendering Volumetric Presentation State Storage, SOPClass */
    MultipleVolumeRenderingVolumetricPresentationStateStorage("1.2.840.10008.5.1.4.1.1.11.11"),

    /** X-Ray Angiographic Image Storage, SOPClass */
    XRayAngiographicImageStorage("1.2.840.10008.5.1.4.1.1.12.1"),

    /** Enhanced XA Image Storage, SOPClass */
    EnhancedXAImageStorage("1.2.840.10008.5.1.4.1.1.12.1.1"),

    /** X-Ray Radiofluoroscopic Image Storage, SOPClass */
    XRayRadiofluoroscopicImageStorage("1.2.840.10008.5.1.4.1.1.12.2"),

    /** Enhanced XRF Image Storage, SOPClass */
    EnhancedXRFImageStorage("1.2.840.10008.5.1.4.1.1.12.2.1"),

    /** X-Ray Angiographic Bi-Plane Image Storage (Retired), SOPClass */
    XRayAngiographicBiPlaneImageStorage("1.2.840.10008.5.1.4.1.1.12.3"),

    /** Zeiss OPT File (Retired), SOPClass */
    ZeissOPTFile("1.2.840.10008.5.1.4.1.1.12.77"),

    /** X-Ray 3D Angiographic Image Storage, SOPClass */
    XRay3DAngiographicImageStorage("1.2.840.10008.5.1.4.1.1.13.1.1"),

    /** X-Ray 3D Craniofacial Image Storage, SOPClass */
    XRay3DCraniofacialImageStorage("1.2.840.10008.5.1.4.1.1.13.1.2"),

    /** Breast Tomosynthesis Image Storage, SOPClass */
    BreastTomosynthesisImageStorage("1.2.840.10008.5.1.4.1.1.13.1.3"),

    /** Breast Projection X-Ray Image Storage - For Presentation, SOPClass */
    BreastProjectionXRayImageStorageForPresentation("1.2.840.10008.5.1.4.1.1.13.1.4"),

    /** Breast Projection X-Ray Image Storage - For Processing, SOPClass */
    BreastProjectionXRayImageStorageForProcessing("1.2.840.10008.5.1.4.1.1.13.1.5"),

    /** Intravascular Optical Coherence Tomography Image Storage - For Presentation, SOPClass */
    IntravascularOpticalCoherenceTomographyImageStorageForPresentation("1.2.840.10008.5.1.4.1.1.14.1"),

    /** Intravascular Optical Coherence Tomography Image Storage - For Processing, SOPClass */
    IntravascularOpticalCoherenceTomographyImageStorageForProcessing("1.2.840.10008.5.1.4.1.1.14.2"),

    /** Nuclear Medicine Image Storage, SOPClass */
    NuclearMedicineImageStorage("1.2.840.10008.5.1.4.1.1.20"),

    /** Parametric Map Storage, SOPClass */
    ParametricMapStorage("1.2.840.10008.5.1.4.1.1.30"),

    /** MR Image Storage Zero Padded (Retired), SOPClass */
    MRImageStorageZeroPadded("1.2.840.10008.5.1.4.1.1.40"),

    /** Raw Data Storage, SOPClass */
    RawDataStorage("1.2.840.10008.5.1.4.1.1.66"),

    /** Spatial Registration Storage, SOPClass */
    SpatialRegistrationStorage("1.2.840.10008.5.1.4.1.1.66.1"),

    /** Spatial Fiducials Storage, SOPClass */
    SpatialFiducialsStorage("1.2.840.10008.5.1.4.1.1.66.2"),

    /** Deformable Spatial Registration Storage, SOPClass */
    DeformableSpatialRegistrationStorage("1.2.840.10008.5.1.4.1.1.66.3"),

    /** Segmentation Storage, SOPClass */
    SegmentationStorage("1.2.840.10008.5.1.4.1.1.66.4"),

    /** Surface Segmentation Storage, SOPClass */
    SurfaceSegmentationStorage("1.2.840.10008.5.1.4.1.1.66.5"),

    /** Tractography Results Storage, SOPClass */
    TractographyResultsStorage("1.2.840.10008.5.1.4.1.1.66.6"),

    /** Real World Value Mapping Storage, SOPClass */
    RealWorldValueMappingStorage("1.2.840.10008.5.1.4.1.1.67"),

    /** Surface Scan Mesh Storage, SOPClass */
    SurfaceScanMeshStorage("1.2.840.10008.5.1.4.1.1.68.1"),

    /** Surface Scan Point Cloud Storage, SOPClass */
    SurfaceScanPointCloudStorage("1.2.840.10008.5.1.4.1.1.68.2"),

    /** VL Image Storage - Trial (Retired), SOPClass */
    VLImageStorageTrial ("1.2.840.10008.5.1.4.1.1.77.1"),

    /** VL Multi-frame Image Storage - Trial (Retired), SOPClass */
    VLMultiFrameImageStorageTrial("1.2.840.10008.5.1.4.1.1.77.2"),

    /** VL Endoscopic Image Storage, SOPClass */
    VLEndoscopicImageStorage("1.2.840.10008.5.1.4.1.1.77.1.1"),

    /** Video Endoscopic Image Storage, SOPClass */
    VideoEndoscopicImageStorage("1.2.840.10008.5.1.4.1.1.77.1.1.1"),

    /** VL Microscopic Image Storage, SOPClass */
    VLMicroscopicImageStorage("1.2.840.10008.5.1.4.1.1.77.1.2"),

    /** Video Microscopic Image Storage, SOPClass */
    VideoMicroscopicImageStorage("1.2.840.10008.5.1.4.1.1.77.1.2.1"),

    /** VL Slide-Coordinates Microscopic Image Storage, SOPClass */
    VLSlideCoordinatesMicroscopicImageStorage("1.2.840.10008.5.1.4.1.1.77.1.3"),

    /** VL Photographic Image Storage, SOPClass */
    VLPhotographicImageStorage("1.2.840.10008.5.1.4.1.1.77.1.4"),

    /** Video Photographic Image Storage, SOPClass */
    VideoPhotographicImageStorage("1.2.840.10008.5.1.4.1.1.77.1.4.1"),

    /** Ophthalmic Photography 8 Bit Image Storage, SOPClass */
    OphthalmicPhotography8BitImageStorage("1.2.840.10008.5.1.4.1.1.77.1.5.1"),

    /** Ophthalmic Photography 16 Bit Image Storage, SOPClass */
    OphthalmicPhotography16BitImageStorage("1.2.840.10008.5.1.4.1.1.77.1.5.2"),

    /** Stereometric Relationship Storage, SOPClass */
    StereometricRelationshipStorage("1.2.840.10008.5.1.4.1.1.77.1.5.3"),

    /** Ophthalmic Tomography Image Storage, SOPClass */
    OphthalmicTomographyImageStorage("1.2.840.10008.5.1.4.1.1.77.1.5.4"),

    /** Wide Field Ophthalmic Photography Stereographic Projection Image Storage, SOPClass */
    WideFieldOphthalmicPhotographyStereographicProjectionImageStorage("1.2.840.10008.5.1.4.1.1.77.1.5.5"),

    /** Wide Field Ophthalmic Photography 3D Coordinates Image Storage, SOPClass */
    WideFieldOphthalmicPhotography3DCoordinatesImageStorage("1.2.840.10008.5.1.4.1.1.77.1.5.6"),

    /** Ophthalmic Optical Coherence Tomography En Face Image Storage, SOPClass */
    OphthalmicOpticalCoherenceTomographyEnFaceImageStorage("1.2.840.10008.5.1.4.1.1.77.1.5.7"),

    /** Ophthalmic Optical Coherence Tomography B-scan Volume Analysis Storage, SOPClass */
    OphthalmicOpticalCoherenceTomographyBscanVolumeAnalysisStorage("1.2.840.10008.5.1.4.1.1.77.1.5.8"),

    /** VL Whole Slide Microscopy Image Storage, SOPClass */
    VLWholeSlideMicroscopyImageStorage("1.2.840.10008.5.1.4.1.1.77.1.6"),

    /** Dermoscopic Photography Image Storage, SOPClass */
    DermoscopicPhotographyImageStorage("1.2.840.10008.5.1.4.1.1.77.1.7"),

    /** Lensometry Measurements Storage, SOPClass */
    LensometryMeasurementsStorage("1.2.840.10008.5.1.4.1.1.78.1"),

    /** Autorefraction Measurements Storage, SOPClass */
    AutorefractionMeasurementsStorage("1.2.840.10008.5.1.4.1.1.78.2"),

    /** Keratometry Measurements Storage, SOPClass */
    KeratometryMeasurementsStorage("1.2.840.10008.5.1.4.1.1.78.3"),

    /** Subjective Refraction Measurements Storage, SOPClass */
    SubjectiveRefractionMeasurementsStorage("1.2.840.10008.5.1.4.1.1.78.4"),

    /** Visual Acuity Measurements Storage, SOPClass */
    VisualAcuityMeasurementsStorage("1.2.840.10008.5.1.4.1.1.78.5"),

    /** Spectacle Prescription Report Storage, SOPClass */
    SpectaclePrescriptionReportStorage("1.2.840.10008.5.1.4.1.1.78.6"),

    /** Ophthalmic Axial Measurements Storage, SOPClass */
    OphthalmicAxialMeasurementsStorage("1.2.840.10008.5.1.4.1.1.78.7"),

    /** Intraocular Lens Calculations Storage, SOPClass */
    IntraocularLensCalculationsStorage("1.2.840.10008.5.1.4.1.1.78.8"),

    /** Macular Grid Thickness and Volume Report Storage, SOPClass */
    MacularGridThicknessAndVolumeReportStorage("1.2.840.10008.5.1.4.1.1.79.1"),

    /** Ophthalmic Visual Field Static Perimetry Measurements Storage, SOPClass */
    OphthalmicVisualFieldStaticPerimetryMeasurementsStorage("1.2.840.10008.5.1.4.1.1.80.1"),

    /** Ophthalmic Thickness Map Storage, SOPClass */
    OphthalmicThicknessMapStorage("1.2.840.10008.5.1.4.1.1.81.1"),

    /** Corneal Topography Map Storage, SOPClass */
    CornealTopographyMapStorage("1.2.840.10008.5.1.4.1.1.82.1"),

    /** Text SR Storage - Trial (Retired), SOPClass */
    TextSRStorageTrial("1.2.840.10008.5.1.4.1.1.88.1"),

    /** Audio SR Storage - Trial (Retired), SOPClass */
    AudioSRStorageTrial ("1.2.840.10008.5.1.4.1.1.88.2"),

    /** Detail SR Storage - Trial (Retired), SOPClass */
    DetailSRStorageTrial ("1.2.840.10008.5.1.4.1.1.88.3"),

    /** Comprehensive SR Storage - Trial (Retired), SOPClass */
    ComprehensiveSRStorageTrial("1.2.840.10008.5.1.4.1.1.88.4"),

    /** Basic Text SR Storage, SOPClass */
    BasicTextSRStorage("1.2.840.10008.5.1.4.1.1.88.11"),

    /** Enhanced SR Storage, SOPClass */
    EnhancedSRStorage("1.2.840.10008.5.1.4.1.1.88.22"),

    /** Comprehensive SR Storage, SOPClass */
    ComprehensiveSRStorage("1.2.840.10008.5.1.4.1.1.88.33"),

    /** Comprehensive 3D SR Storage, SOPClass */
    Comprehensive3DSRStorage("1.2.840.10008.5.1.4.1.1.88.34"),

    /** Extensible SR Storage, SOPClass */
    ExtensibleSRStorage("1.2.840.10008.5.1.4.1.1.88.35"),

    /** Procedure Log Storage, SOPClass */
    ProcedureLogStorage("1.2.840.10008.5.1.4.1.1.88.40"),

    /** Mammography CAD SR Storage, SOPClass */
    MammographyCADSRStorage("1.2.840.10008.5.1.4.1.1.88.50"),

    /** Key Object Selection Document Storage, SOPClass */
    KeyObjectSelectionDocumentStorage("1.2.840.10008.5.1.4.1.1.88.59"),

    /** Chest CAD SR Storage, SOPClass */
    ChestCADSRStorage("1.2.840.10008.5.1.4.1.1.88.65"),

    /** X-Ray Radiation Dose SR Storage, SOPClass */
    XRayRadiationDoseSRStorage("1.2.840.10008.5.1.4.1.1.88.67"),

    /** Radiopharmaceutical Radiation Dose SR Storage, SOPClass */
    RadiopharmaceuticalRadiationDoseSRStorage("1.2.840.10008.5.1.4.1.1.88.68"),

    /** Colon CAD SR Storage, SOPClass */
    ColonCADSRStorage("1.2.840.10008.5.1.4.1.1.88.69"),

    /** Implantation Plan SR Storage, SOPClass */
    ImplantationPlanSRStorage("1.2.840.10008.5.1.4.1.1.88.70"),

    /** Acquisition Context SR Storage, SOPClass */
    AcquisitionContextSRStorage("1.2.840.10008.5.1.4.1.1.88.71"),

    /** Simplified Adult Echo SR Storage, SOPClass */
    SimplifiedAdultEchoSRStorage("1.2.840.10008.5.1.4.1.1.88.72"),

    /** Patient Radiation Dose SR Storage, SOPClass */
    PatientRadiationDoseSRStorage("1.2.840.10008.5.1.4.1.1.88.73"),

    /** Planned Imaging Agent Administration SR Storage, SOPClass */
    PlannedImagingAgentAdministrationSRStorage("1.2.840.10008.5.1.4.1.1.88.74"),

    /** Performed Imaging Agent Administration SR Storage, SOPClass */
    PerformedImagingAgentAdministrationSRStorage("1.2.840.10008.5.1.4.1.1.88.75"),

    /** Content Assessment Results Storage, SOPClass */
    ContentAssessmentResultsStorage("1.2.840.10008.5.1.4.1.1.90.1"),

    /** Encapsulated PDF Storage, SOPClass */
    EncapsulatedPDFStorage("1.2.840.10008.5.1.4.1.1.104.1"),

    /** Encapsulated CDA Storage, SOPClass */
    EncapsulatedCDAStorage("1.2.840.10008.5.1.4.1.1.104.2"),

    /** Encapsulated STL Storage, SOPClass */
    EncapsulatedSTLStorage("1.2.840.10008.5.1.4.1.1.104.3"),

    /** Encapsulated OBJ Storage, SOPClass */
    EncapsulatedOBJStorage("1.2.840.10008.5.1.4.1.1.104.4"),

    /** Encapsulated MTL Storage, SOPClass */
    EncapsulatedMTLStorage("1.2.840.10008.5.1.4.1.1.104.5"),

    /** Positron Emission Tomography Image Storage, SOPClass */
    PositronEmissionTomographyImageStorage("1.2.840.10008.5.1.4.1.1.128"),

    /** Legacy Converted Enhanced PET Image Storage, SOPClass */
    LegacyConvertedEnhancedPETImageStorage("1.2.840.10008.5.1.4.1.1.128.1"),

    /** Standalone PET Curve Storage (Retired), SOPClass */
    StandalonePETCurveStorage("1.2.840.10008.5.1.4.1.1.129"),

    /** Enhanced PET Image Storage, SOPClass */
    EnhancedPETImageStorage("1.2.840.10008.5.1.4.1.1.130"),

    /** Basic Structured Display Storage, SOPClass */
    BasicStructuredDisplayStorage("1.2.840.10008.5.1.4.1.1.131"),

    /** CT Defined Procedure Protocol Storage, SOPClass */
    CTDefinedProcedureProtocolStorage("1.2.840.10008.5.1.4.1.1.200.1"),

    /** CT Performed Procedure Protocol Storage, SOPClass */
    CTPerformedProcedureProtocolStorage("1.2.840.10008.5.1.4.1.1.200.2"),

    /** Protocol Approval Storage, SOPClass */
    ProtocolApprovalStorage("1.2.840.10008.5.1.4.1.1.200.3"),

    /** Protocol Approval Information Model - FIND, SOPClass */
    ProtocolApprovalInformationModelFind("1.2.840.10008.5.1.4.1.1.200.4"),

    /** Protocol Approval Information Model - MOVE, SOPClass */
    ProtocolApprovalInformationModelMove("1.2.840.10008.5.1.4.1.1.200.5"),

    /** Protocol Approval Information Model - GET, SOPClass */
    ProtocolApprovalInformationModelGet("1.2.840.10008.5.1.4.1.1.200.6"),

    /** XA Defined Procedure Protocol Storage, SOPClass */
    XADefinedProcedureProtocolStorage("1.2.840.10008.5.1.4.1.1.200.7"),

    /** XA Performed Procedure Protocol Storage, SOPClass */
    XAPerformedProcedureProtocolStorage("1.2.840.10008.5.1.4.1.1.200.8"),

    /** RT Image Storage, SOPClass */
    RTImageStorage("1.2.840.10008.5.1.4.1.1.481.1"),

    /** RT Dose Storage, SOPClass */
    RTDoseStorage("1.2.840.10008.5.1.4.1.1.481.2"),

    /** RT Structure Set Storage, SOPClass */
    RTStructureSetStorage("1.2.840.10008.5.1.4.1.1.481.3"),

    /** RT Beams Treatment Record Storage, SOPClass */
    RTBeamsTreatmentRecordStorage("1.2.840.10008.5.1.4.1.1.481.4"),

    /** RT Plan Storage, SOPClass */
    RTPlanStorage("1.2.840.10008.5.1.4.1.1.481.5"),

    /** RT Brachy Treatment Record Storage, SOPClass */
    RTBrachyTreatmentRecordStorage("1.2.840.10008.5.1.4.1.1.481.6"),

    /** RT Treatment Summary Record Storage, SOPClass */
    RTTreatmentSummaryRecordStorage("1.2.840.10008.5.1.4.1.1.481.7"),

    /** RT Ion Plan Storage, SOPClass */
    RTIonPlanStorage("1.2.840.10008.5.1.4.1.1.481.8"),

    /** RT Ion Beams Treatment Record Storage, SOPClass */
    RTIonBeamsTreatmentRecordStorage("1.2.840.10008.5.1.4.1.1.481.9"),

    /** RT Physician Intent Storage, SOPClass */
    RTPhysicianIntentStorage("1.2.840.10008.5.1.4.1.1.481.10"),

    /** RT Segment Annotation Storage, SOPClass */
    RTSegmentAnnotationStorage("1.2.840.10008.5.1.4.1.1.481.11"),

    /** RT Radiation Set Storage, SOPClass */
    RTRadiationSetStorage("1.2.840.10008.5.1.4.1.1.481.12"),

    /** C-Arm Photon-Electron Radiation Storage, SOPClass */
    CArmPhotonElectronRadiationStorage("1.2.840.10008.5.1.4.1.1.481.13"),

    /** Tomotherapeutic Radiation Storage, SOPClass */
    TomotherapeuticRadiationStorage("1.2.840.10008.5.1.4.1.1.481.14"),

    /** Robotic-Arm Radiation Storage, SOPClass */
    RoboticArmRadiationStorage("1.2.840.10008.5.1.4.1.1.481.15"),

    /** RT Radiation Record Set Storage, SOPClass */
    RTRadiationRecordSetStorage("1.2.840.10008.5.1.4.1.1.481.16"),

    /** RT Radiation Salvage Record Storage, SOPClass */
    RTRadiationSalvageRecordStorage("1.2.840.10008.5.1.4.1.1.481.17"),

    /** Tomotherapeutic Radiation Record Storage, SOPClass */
    TomotherapeuticRadiationRecordStorage("1.2.840.10008.5.1.4.1.1.481.18"),

    /** C-Arm Photon-Electron Radiation Record Storage, SOPClass */
    CArmPhotonElectronRadiationRecordStorage("1.2.840.10008.5.1.4.1.1.481.19"),

    /** Robotic Radiation Record Storage, SOPClass */
    RoboticRadiationRecordStorage("1.2.840.10008.5.1.4.1.1.481.20"),

    /** DICOS CT Image Storage, SOPClass */
    DICOSCTImageStorage("1.2.840.10008.5.1.4.1.1.501.1"),

    /** DICOS Digital X-Ray Image Storage - For Presentation, SOPClass */
    DICOSDigitalXRayImageStorageForPresentation("1.2.840.10008.5.1.4.1.1.501.2.1"),

    /** DICOS Digital X-Ray Image Storage - For Processing, SOPClass */
    DICOSDigitalXRayImageStorageForProcessing("1.2.840.10008.5.1.4.1.1.501.2.2"),

    /** DICOS Threat Detection Report Storage, SOPClass */
    DICOSThreatDetectionReportStorage("1.2.840.10008.5.1.4.1.1.501.3"),

    /** DICOS 2D AIT Storage, SOPClass */
    DICOS2DAITStorage("1.2.840.10008.5.1.4.1.1.501.4"),

    /** DICOS 3D AIT Storage, SOPClass */
    DICOS3DAITStorage("1.2.840.10008.5.1.4.1.1.501.5"),

    /** DICOS Quadrupole Resonance (QR) Storage, SOPClass */
    DICOSQuadrupoleResonanceStorage("1.2.840.10008.5.1.4.1.1.501.6"),

    /** Eddy Current Image Storage, SOPClass */
    EddyCurrentImageStorage("1.2.840.10008.5.1.4.1.1.601.1"),

    /** Eddy Current Multi-frame Image Storage, SOPClass */
    EddyCurrentMultiFrameImageStorage("1.2.840.10008.5.1.4.1.1.601.2"),

    /** Patient Root Query/Retrieve Information Model - FIND, SOPClass */
    PatientRootQueryRetrieveInformationModelFind("1.2.840.10008.5.1.4.1.2.1.1"),

    /** Patient Root Query/Retrieve Information Model - MOVE, SOPClass */
    PatientRootQueryRetrieveInformationModelMove("1.2.840.10008.5.1.4.1.2.1.2"),

    /** Patient Root Query/Retrieve Information Model - GET, SOPClass */
    PatientRootQueryRetrieveInformationModelGet("1.2.840.10008.5.1.4.1.2.1.3"),

    /** Study Root Query/Retrieve Information Model - FIND, SOPClass */
    StudyRootQueryRetrieveInformationModelFind("1.2.840.10008.5.1.4.1.2.2.1"),

    /** Study Root Query/Retrieve Information Model - MOVE, SOPClass */
    StudyRootQueryRetrieveInformationModelMove("1.2.840.10008.5.1.4.1.2.2.2"),

    /** Study Root Query/Retrieve Information Model - GET, SOPClass */
    StudyRootQueryRetrieveInformationModelGet("1.2.840.10008.5.1.4.1.2.2.3"),

    /** Patient/Study Only Query/Retrieve Information Model - FIND (Retired), SOPClass */
    PatientStudyOnlyQueryRetrieveInformationModelFind("1.2.840.10008.5.1.4.1.2.3.1"),

    /** Patient/Study Only Query/Retrieve Information Model - MOVE (Retired), SOPClass */
    PatientStudyOnlyQueryRetrieveInformationModelMove("1.2.840.10008.5.1.4.1.2.3.2"),

    /** Patient/Study Only Query/Retrieve Information Model - GET (Retired), SOPClass */
    PatientStudyOnlyQueryRetrieveInformationModelGet("1.2.840.10008.5.1.4.1.2.3.3"),

    /** Composite Instance Root Retrieve - MOVE, SOPClass */
    CompositeInstanceRootRetrieveMove("1.2.840.10008.5.1.4.1.2.4.2"),

    /** Composite Instance Root Retrieve - GET, SOPClass */
    CompositeInstanceRootRetrieveGet("1.2.840.10008.5.1.4.1.2.4.3"),

    /** Composite Instance Retrieve Without Bulk Data - GET, SOPClass */
    CompositeInstanceRetrieveWithoutBulkDataGet("1.2.840.10008.5.1.4.1.2.5.3"),

    /** Defined Procedure Protocol Information Model - FIND, SOPClass */
    DefinedProcedureProtocolInformationModelFind("1.2.840.10008.5.1.4.20.1"),

    /** Defined Procedure Protocol Information Model - MOVE, SOPClass */
    DefinedProcedureProtocolInformationModelMove("1.2.840.10008.5.1.4.20.2"),

    /** Defined Procedure Protocol Information Model - GET, SOPClass */
    DefinedProcedureProtocolInformationModelGet("1.2.840.10008.5.1.4.20.3"),

    /** Modality Worklist Information Model - FIND, SOPClass */
    ModalityWorklistInformationModelFind("1.2.840.10008.5.1.4.31"),

    /** General Purpose Worklist Management Meta SOP Class (Retired), MetaSOPClass */
    GeneralPurposeWorklistManagementMeta("1.2.840.10008.5.1.4.32"),

    /** General Purpose Worklist Information Model - FIND (Retired), SOPClass */
    GeneralPurposeWorklistInformationModelFind("1.2.840.10008.5.1.4.32.1"),

    /** General Purpose Scheduled Procedure Step SOP Class (Retired), SOPClass */
    GeneralPurposeScheduledProcedureStep("1.2.840.10008.5.1.4.32.2"),

    /** General Purpose Performed Procedure Step SOP Class (Retired), SOPClass */
    GeneralPurposePerformedProcedureStep("1.2.840.10008.5.1.4.32.3"),

    /** Instance Availability Notification SOP Class, SOPClass */
    InstanceAvailabilityNotification("1.2.840.10008.5.1.4.33"),

    /** RT Beams Delivery Instruction Storage - Trial (Retired), SOPClass */
    RTBeamsDeliveryInstructionStorageTrial("1.2.840.10008.5.1.4.34.1"),

    /** RT Conventional Machine Verification - Trial (Retired), SOPClass */
    RTConventionalMachineVerificationTrial("1.2.840.10008.5.1.4.34.2"),

    /** RT Ion Machine Verification - Trial (Retired), SOPClass */
    RTIonMachineVerificationTrial("1.2.840.10008.5.1.4.34.3"),

    /** Unified Worklist and Procedure Step Service Class - Trial (Retired), ServiceClass */
    UnifiedWorklistAndProcedureStepTrial ("1.2.840.10008.5.1.4.34.4"),

    /** Unified Procedure Step - Push SOP Class - Trial (Retired), SOPClass */
    UnifiedProcedureStepPushTrial ("1.2.840.10008.5.1.4.34.4.1"),

    /** Unified Procedure Step - Watch SOP Class - Trial (Retired), SOPClass */
    UnifiedProcedureStepWatchTrial("1.2.840.10008.5.1.4.34.4.2"),

    /** Unified Procedure Step - Pull SOP Class - Trial (Retired), SOPClass */
    UnifiedProcedureStepPullTrial("1.2.840.10008.5.1.4.34.4.3"),

    /** Unified Procedure Step - Event SOP Class - Trial (Retired), SOPClass */
    UnifiedProcedureStepEventTrial("1.2.840.10008.5.1.4.34.4.4"),

    /** UPS Global Subscription SOP Instance, WellKnownSOPInstance */
    UPSGlobalSubscriptionInstance("1.2.840.10008.5.1.4.34.5"),

    /** UPS Filtered Global Subscription SOP Instance, WellKnownSOPInstance */
    UPSFilteredGlobalSubscriptionInstance("1.2.840.10008.5.1.4.34.5.1"),

    /** Unified Worklist and Procedure Step Service Class, ServiceClass */
    UnifiedWorklistAndProcedureStep("1.2.840.10008.5.1.4.34.6"),

    /** Unified Procedure Step - Push SOP Class, SOPClass */
    UnifiedProcedureStepPush("1.2.840.10008.5.1.4.34.6.1"),

    /** Unified Procedure Step - Watch SOP Class, SOPClass */
    UnifiedProcedureStepWatch("1.2.840.10008.5.1.4.34.6.2"),

    /** Unified Procedure Step - Pull SOP Class, SOPClass */
    UnifiedProcedureStepPull("1.2.840.10008.5.1.4.34.6.3"),

    /** Unified Procedure Step - Event SOP Class, SOPClass */
    UnifiedProcedureStepEvent("1.2.840.10008.5.1.4.34.6.4"),

    /** Unified Procedure Step - Query SOP Class, SOPClass */
    UnifiedProcedureStepQuery("1.2.840.10008.5.1.4.34.6.5"),

    /** RT Beams Delivery Instruction Storage, SOPClass */
    RTBeamsDeliveryInstructionStorage("1.2.840.10008.5.1.4.34.7"),

    /** RT Conventional Machine Verification, SOPClass */
    RTConventionalMachineVerification("1.2.840.10008.5.1.4.34.8"),

    /** RT Ion Machine Verification, SOPClass */
    RTIonMachineVerification("1.2.840.10008.5.1.4.34.9"),

    /** RT Brachy Application Setup Delivery Instruction Storage, SOPClass */
    RTBrachyApplicationSetupDeliveryInstructionStorage("1.2.840.10008.5.1.4.34.10"),

    /** General Relevant Patient Information Query, SOPClass */
    GeneralRelevantPatientInformationQuery("1.2.840.10008.5.1.4.37.1"),

    /** Breast Imaging Relevant Patient Information Query, SOPClass */
    BreastImagingRelevantPatientInformationQuery("1.2.840.10008.5.1.4.37.2"),

    /** Cardiac Relevant Patient Information Query, SOPClass */
    CardiacRelevantPatientInformationQuery("1.2.840.10008.5.1.4.37.3"),

    /** Hanging Protocol Storage, SOPClass */
    HangingProtocolStorage("1.2.840.10008.5.1.4.38.1"),

    /** Hanging Protocol Information Model - FIND, SOPClass */
    HangingProtocolInformationModelFind("1.2.840.10008.5.1.4.38.2"),

    /** Hanging Protocol Information Model - MOVE, SOPClass */
    HangingProtocolInformationModelMove("1.2.840.10008.5.1.4.38.3"),

    /** Hanging Protocol Information Model - GET, SOPClass */
    HangingProtocolInformationModelGet("1.2.840.10008.5.1.4.38.4"),

    /** Color Palette Storage, SOPClass */
    ColorPaletteStorage("1.2.840.10008.5.1.4.39.1"),

    /** Color Palette Query/Retrieve Information Model - FIND, SOPClass */
    ColorPaletteQueryRetrieveInformationModelFind("1.2.840.10008.5.1.4.39.2"),

    /** Color Palette Query/Retrieve Information Model - MOVE, SOPClass */
    ColorPaletteQueryRetrieveInformationModelMove("1.2.840.10008.5.1.4.39.3"),

    /** Color Palette Query/Retrieve Information Model - GET, SOPClass */
    ColorPaletteQueryRetrieveInformationModelGet("1.2.840.10008.5.1.4.39.4"),

    /** Product Characteristics Query SOP Class, SOPClass */
    ProductCharacteristicsQuery("1.2.840.10008.5.1.4.41"),

    /** Substance Approval Query SOP Class, SOPClass */
    SubstanceApprovalQuery("1.2.840.10008.5.1.4.42"),

    /** Generic Implant Template Storage, SOPClass */
    GenericImplantTemplateStorage("1.2.840.10008.5.1.4.43.1"),

    /** Generic Implant Template Information Model - FIND, SOPClass */
    GenericImplantTemplateInformationModelFind("1.2.840.10008.5.1.4.43.2"),

    /** Generic Implant Template Information Model - MOVE, SOPClass */
    GenericImplantTemplateInformationModelMove("1.2.840.10008.5.1.4.43.3"),

    /** Generic Implant Template Information Model - GET, SOPClass */
    GenericImplantTemplateInformationModelGet("1.2.840.10008.5.1.4.43.4"),

    /** Implant Assembly Template Storage, SOPClass */
    ImplantAssemblyTemplateStorage("1.2.840.10008.5.1.4.44.1"),

    /** Implant Assembly Template Information Model - FIND, SOPClass */
    ImplantAssemblyTemplateInformationModelFind("1.2.840.10008.5.1.4.44.2"),

    /** Implant Assembly Template Information Model - MOVE, SOPClass */
    ImplantAssemblyTemplateInformationModelMove("1.2.840.10008.5.1.4.44.3"),

    /** Implant Assembly Template Information Model - GET, SOPClass */
    ImplantAssemblyTemplateInformationModelGet("1.2.840.10008.5.1.4.44.4"),

    /** Implant Template Group Storage, SOPClass */
    ImplantTemplateGroupStorage("1.2.840.10008.5.1.4.45.1"),

    /** Implant Template Group Information Model - FIND, SOPClass */
    ImplantTemplateGroupInformationModelFind("1.2.840.10008.5.1.4.45.2"),

    /** Implant Template Group Information Model - MOVE, SOPClass */
    ImplantTemplateGroupInformationModelMove("1.2.840.10008.5.1.4.45.3"),

    /** Implant Template Group Information Model - GET, SOPClass */
    ImplantTemplateGroupInformationModelGet("1.2.840.10008.5.1.4.45.4"),

    /** Native DICOM Model, ApplicationHostingModel */
    NativeDICOMModel("1.2.840.10008.7.1.1"),

    /** Abstract Multi-Dimensional Image Model, ApplicationHostingModel */
    AbstractMultiDimensionalImageModel("1.2.840.10008.7.1.2"),

    /** DICOM Content Mapping Resource, MappingResource */
    DICOMContentMappingResource("1.2.840.10008.8.1.1"),

    /** Video Endoscopic Image Real-Time Communication, SOPClass */
    VideoEndoscopicImageRealTimeCommunication("1.2.840.10008.10.1"),

    /** Video Photographic Image Real-Time Communication, SOPClass */
    VideoPhotographicImageRealTimeCommunication("1.2.840.10008.10.2"),

    /** Audio Waveform Real-Time Communication, SOPClass */
    AudioWaveformRealTimeCommunication("1.2.840.10008.10.3"),

    /** Rendition Selection Document Real-Time Communication, SOPClass */
    RenditionSelectionDocumentRealTimeCommunication("1.2.840.10008.10.4"),

    /** dicomDeviceName, LDAPOID */
    dicomDeviceName("1.2.840.10008.15.0.3.1"),

    /** dicomDescription, LDAPOID */
    dicomDescription("1.2.840.10008.15.0.3.2"),

    /** dicomManufacturer, LDAPOID */
    dicomManufacturer("1.2.840.10008.15.0.3.3"),

    /** dicomManufacturerModelName, LDAPOID */
    dicomManufacturerModelName("1.2.840.10008.15.0.3.4"),

    /** dicomSoftwareVersion, LDAPOID */
    dicomSoftwareVersion("1.2.840.10008.15.0.3.5"),

    /** dicomVendorData, LDAPOID */
    dicomVendorData("1.2.840.10008.15.0.3.6"),

    /** dicomAETitle, LDAPOID */
    dicomAETitle("1.2.840.10008.15.0.3.7"),

    /** dicomNetworkConnectionReference, LDAPOID */
    dicomNetworkConnectionReference("1.2.840.10008.15.0.3.8"),

    /** dicomApplicationCluster, LDAPOID */
    dicomApplicationCluster("1.2.840.10008.15.0.3.9"),

    /** dicomAssociationInitiator, LDAPOID */
    dicomAssociationInitiator("1.2.840.10008.15.0.3.10"),

    /** dicomAssociationAcceptor, LDAPOID */
    dicomAssociationAcceptor("1.2.840.10008.15.0.3.11"),

    /** dicomHostname, LDAPOID */
    dicomHostname("1.2.840.10008.15.0.3.12"),

    /** dicomPort, LDAPOID */
    dicomPort("1.2.840.10008.15.0.3.13"),

    /** dicomSOPClass, LDAPOID */
    dicomSOPClass("1.2.840.10008.15.0.3.14"),

    /** dicomTransferRole, LDAPOID */
    dicomTransferRole("1.2.840.10008.15.0.3.15"),

    /** dicomTransferSyntax, LDAPOID */
    dicomTransferSyntax("1.2.840.10008.15.0.3.16"),

    /** dicomPrimaryDeviceType, LDAPOID */
    dicomPrimaryDeviceType("1.2.840.10008.15.0.3.17"),

    /** dicomRelatedDeviceReference, LDAPOID */
    dicomRelatedDeviceReference("1.2.840.10008.15.0.3.18"),

    /** dicomPreferredCalledAETitle, LDAPOID */
    dicomPreferredCalledAETitle("1.2.840.10008.15.0.3.19"),

    /** dicomTLSCyphersuite, LDAPOID */
    dicomTLSCyphersuite("1.2.840.10008.15.0.3.20"),

    /** dicomAuthorizedNodeCertificateReference, LDAPOID */
    dicomAuthorizedNodeCertificateReference("1.2.840.10008.15.0.3.21"),

    /** dicomThisNodeCertificateReference, LDAPOID */
    dicomThisNodeCertificateReference("1.2.840.10008.15.0.3.22"),

    /** dicomInstalled, LDAPOID */
    dicomInstalled("1.2.840.10008.15.0.3.23"),

    /** dicomStationName, LDAPOID */
    dicomStationName("1.2.840.10008.15.0.3.24"),

    /** dicomDeviceSerialNumber, LDAPOID */
    dicomDeviceSerialNumber("1.2.840.10008.15.0.3.25"),

    /** dicomInstitutionName, LDAPOID */
    dicomInstitutionName("1.2.840.10008.15.0.3.26"),

    /** dicomInstitutionAddress, LDAPOID */
    dicomInstitutionAddress("1.2.840.10008.15.0.3.27"),

    /** dicomInstitutionDepartmentName, LDAPOID */
    dicomInstitutionDepartmentName("1.2.840.10008.15.0.3.28"),

    /** dicomIssuerOfPatientID, LDAPOID */
    dicomIssuerOfPatientID("1.2.840.10008.15.0.3.29"),

    /** dicomPreferredCallingAETitle, LDAPOID */
    dicomPreferredCallingAETitle("1.2.840.10008.15.0.3.30"),

    /** dicomSupportedCharacterSet, LDAPOID */
    dicomSupportedCharacterSet("1.2.840.10008.15.0.3.31"),

    /** dicomConfigurationRoot, LDAPOID */
    dicomConfigurationRoot("1.2.840.10008.15.0.4.1"),

    /** dicomDevicesRoot, LDAPOID */
    dicomDevicesRoot("1.2.840.10008.15.0.4.2"),

    /** dicomUniqueAETitlesRegistryRoot, LDAPOID */
    dicomUniqueAETitlesRegistryRoot("1.2.840.10008.15.0.4.3"),

    /** dicomDevice, LDAPOID */
    dicomDevice("1.2.840.10008.15.0.4.4"),

    /** dicomNetworkAE, LDAPOID */
    dicomNetworkAE("1.2.840.10008.15.0.4.5"),

    /** dicomNetworkConnection, LDAPOID */
    dicomNetworkConnection("1.2.840.10008.15.0.4.6"),

    /** dicomUniqueAETitle, LDAPOID */
    dicomUniqueAETitle("1.2.840.10008.15.0.4.7"),

    /** dicomTransferCapability, LDAPOID */
    dicomTransferCapability("1.2.840.10008.15.0.4.8"),

    /** Universal Coordinated Time, SynchronizationFrameOfReference */
    UTC("1.2.840.10008.15.1.1"),

    /** Private Agfa Basic Attribute Presentation State, SOPClass */
    PrivateAgfaBasicAttributePresentationState("1.2.124.113532.3500.7"),

    /** Private Agfa Arrival Transaction, SOPClass */
    PrivateAgfaArrivalTransaction("1.2.124.113532.3500.8.1"),

    /** Private Agfa Dictation Transaction, SOPClass */
    PrivateAgfaDictationTransaction("1.2.124.113532.3500.8.2"),

    /** Private Agfa Report Transcription Transaction, SOPClass */
    PrivateAgfaReportTranscriptionTransaction("1.2.124.113532.3500.8.3"),

    /** Private Agfa Report Approval Transaction, SOPClass */
    PrivateAgfaReportApprovalTransaction("1.2.124.113532.3500.8.4"),

    /** Private TomTec Annotation Storage, SOPClass */
    PrivateTomTecAnnotationStorage("1.2.276.0.48.5.1.4.1.1.7"),

    /** Private Toshiba US Image Storage, SOPClass */
    PrivateToshibaUSImageStorage("1.2.392.200036.9116.7.8.1.1.1"),

    /** Private Fuji CR Image Storage, SOPClass */
    PrivateFujiCRImageStorage("1.2.392.200036.9125.1.1.2"),

    /** Private GE Collage Storage, SOPClass */
    PrivateGECollageStorage("1.2.528.1.1001.5.1.1.1"),

    /** Private ERAD Practice Builder Report Text Storage, SOPClass */
    PrivateERADPracticeBuilderReportTextStorage("1.2.826.0.1.3680043.293.1.0.1"),

    /** Private ERAD Practice Builder Report Dictation Storage, SOPClass */
    PrivateERADPracticeBuilderReportDictationStorage("1.2.826.0.1.3680043.293.1.0.2"),

    /** Private Philips HP Live 3D 01 Storage, SOPClass */
    PrivatePhilipsHPLive3D01Storage("1.2.840.113543.6.6.1.3.10001"),

    /** Private Philips HP Live 3D 02 Storage, SOPClass */
    PrivatePhilipsHPLive3D02Storage("1.2.840.113543.6.6.1.3.10002"),

    /** Private GE 3D Model Storage, SOPClass */
    PrivateGE3DModelStorage("1.2.840.113619.4.26"),

    /** Private GE Dicom CT Image Info Object, SOPClass */
    PrivateGEDicomCTImageInfoObject("1.2.840.113619.4.3"),

    /** Private GE Dicom Display Image Info Object, SOPClass */
    PrivateGEDicomDisplayImageInfoObject("1.2.840.113619.4.4"),

    /** Private GE Dicom MR Image Info Object, SOPClass */
    PrivateGEDicomMRImageInfoObject("1.2.840.113619.4.2"),

    /** Private GE eNTEGRA Protocol or NM Genie Storage, SOPClass */
    PrivateGEeNTEGRAProtocolOrNMGenieStorage("1.2.840.113619.4.27"),

    /** Private GE PET Raw Data Storage, SOPClass */
    PrivateGEPETRawDataStorage("1.2.840.113619.4.30"),

    /** Private GE RT Plan Storage, SOPClass */
    PrivateGERTPlanStorage("1.2.840.113619.4.5.249"),

    /** Private PixelMed Legacy Converted Enhanced CT Image Storage, SOPClass */
    PrivatePixelMedLegacyConvertedEnhancedCTImageStorage("1.3.6.1.4.1.5962.301.1"),

    /** Private PixelMed Legacy Converted Enhanced MR Image Storage, SOPClass */
    PrivatePixelMedLegacyConvertedEnhancedMRImageStorage("1.3.6.1.4.1.5962.301.2"),

    /** Private PixelMed Legacy Converted Enhanced PET Image Storage, SOPClass */
    PrivatePixelMedLegacyConvertedEnhancedPETImageStorage("1.3.6.1.4.1.5962.301.3"),

    /** Private PixelMed Floating Point Image Storage, SOPClass */
    PrivatePixelMedFloatingPointImageStorage("1.3.6.1.4.1.5962.301.9"),

    /** Private Siemens CSA Non Image Storage, SOPClass */
    PrivateSiemensCSANonImageStorage("1.3.12.2.1107.5.9.1"),

    /** Private Siemens CT MR Volume Storage, SOPClass */
    PrivateSiemensCTMRVolumeStorage("1.3.12.2.1107.5.99.3.10"),

    /** Private Siemens AX Frame Sets Storage, SOPClass */
    PrivateSiemensAXFrameSetsStorage("1.3.12.2.1107.5.99.3.11"),

    /** Private Philips Specialised XA Storage, SOPClass */
    PrivatePhilipsSpecialisedXAStorage("1.3.46.670589.2.3.1.1"),

    /** Private Philips CX Image Storage, SOPClass */
    PrivatePhilipsCXImageStorage("1.3.46.670589.2.4.1.1"),

    /** Private Philips 3D Presentation State Storage, SOPClass */
    PrivatePhilips3DPresentationStateStorage("1.3.46.670589.2.5.1.1"),

    /** Private Philips VRML Storage, SOPClass */
    PrivatePhilipsVRMLStorage("1.3.46.670589.2.8.1.1"),

    /** Private Philips Volume Set Storage, SOPClass */
    PrivatePhilipsVolumeSetStorage("1.3.46.670589.2.11.1.1"),

    /** Private Philips Volume Storage (Retired), SOPClass */
    PrivatePhilipsVolumeStorageRetired("1.3.46.670589.5.0.1"),

    /** Private Philips Volume Storage, SOPClass */
    PrivatePhilipsVolumeStorage("1.3.46.670589.5.0.1.1"),

    /** Private Philips 3D Object Storage (Retired), SOPClass */
    PrivatePhilips3DObjectStorageRetired("1.3.46.670589.5.0.2"),

    /** Private Philips 3D Object Storage, SOPClass */
    PrivatePhilips3DObjectStorage("1.3.46.670589.5.0.2.1"),

    /** Private Philips Surface Storage (Retired), SOPClass */
    PrivatePhilipsSurfaceStorageRetired("1.3.46.670589.5.0.3"),

    /** Private Philips Surface Storage, SOPClass */
    PrivatePhilipsSurfaceStorage("1.3.46.670589.5.0.3.1"),

    /** Private Philips Composite Object Storage, SOPClass */
    PrivatePhilipsCompositeObjectStorage("1.3.46.670589.5.0.4"),

    /** Private Philips MR Cardio Profile Storage, SOPClass */
    PrivatePhilipsMRCardioProfileStorage("1.3.46.670589.5.0.7"),

    /** Private Philips MR Cardio Storage (Retired), SOPClass */
    PrivatePhilipsMRCardioStorageRetired("1.3.46.670589.5.0.8"),

    /** Private Philips MR Cardio Storage, SOPClass */
    PrivatePhilipsMRCardioStorage("1.3.46.670589.5.0.8.1"),

    /** Private Philips CT Synthetic Image Storage, SOPClass */
    PrivatePhilipsCTSyntheticImageStorage("1.3.46.670589.5.0.9"),

    /** Private Philips MR Synthetic Image Storage, SOPClass */
    PrivatePhilipsMRSyntheticImageStorage("1.3.46.670589.5.0.10"),

    /** Private Philips MR Cardio Analysis Storage (Retired), SOPClass */
    PrivatePhilipsMRCardioAnalysisStorageRetired("1.3.46.670589.5.0.11"),

    /** Private Philips MR Cardio Analysis Storage, SOPClass */
    PrivatePhilipsMRCardioAnalysisStorage("1.3.46.670589.5.0.11.1"),

    /** Private Philips CX Synthetic Image Storage, SOPClass */
    PrivatePhilipsCXSyntheticImageStorage("1.3.46.670589.5.0.12"),

    /** Private Philips Perfusion Storage, SOPClass */
    PrivatePhilipsPerfusionStorage("1.3.46.670589.5.0.13"),

    /** Private Philips Perfusion Image Storage, SOPClass */
    PrivatePhilipsPerfusionImageStorage("1.3.46.670589.5.0.14"),

    /** Private Philips X-Ray MF Storage, SOPClass */
    PrivatePhilipsXRayMFStorage("1.3.46.670589.7.8.1618510091"),

    /** Private Philips Live Run Storage, SOPClass */
    PrivatePhilipsLiveRunStorage("1.3.46.670589.7.8.1618510092"),

    /** Private Philips Run Storage, SOPClass */
    PrivatePhilipsRunStorage("1.3.46.670589.7.8.16185100129"),

    /** Private Philips Reconstruction Storage, SOPClass */
    PrivatePhilipsReconstructionStorage("1.3.46.670589.7.8.16185100130"),

    /** Private Philips MR Spectrum Storage, SOPClass */
    PrivatePhilipsMRSpectrumStorage("1.3.46.670589.11.0.0.12.1"),

    /** Private Philips MR Series Data Storage, SOPClass */
    PrivatePhilipsMRSeriesDataStorage("1.3.46.670589.11.0.0.12.2"),

    /** Private Philips MR Color Image Storage, SOPClass */
    PrivatePhilipsMRColorImageStorage("1.3.46.670589.11.0.0.12.3"),

    /** Private Philips MR Examcard Storage, SOPClass */
    PrivatePhilipsMRExamcardStorage("1.3.46.670589.11.0.0.12.4"),

    /** Private PMOD Multi-frame Image Storage, SOPClass */
    PrivatePMODMultiFrameImageStorage("2.16.840.1.114033.5.1.4.1.1.130");
	
	private final String uid;

	UID(String uid){
		this.uid = uid;
	}
	
	public static String nameOf(String uid) {
		for (UID u : UID.values()) {
			if (u.uid.equals(uid)) {
				return u.name();
			}
		}
		return null;
	}
	
	public static String forName(String keyword) {
		return UID.valueOf(keyword).uid;
	}
}
