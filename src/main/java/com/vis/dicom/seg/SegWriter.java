package com.vis.dicom.seg;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vis.core.log.Log;
import com.vis.dicom.Sequence;
import com.vis.core.view.D2.ui.glasses.Praparat;
import com.vis.core.view.D2.ui.glasses.SlideGlass;
import com.vis.core.view.D3.roi.FreeFormRoi3D;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;
import com.vis.dicom.UIDUtils;
import com.vis.dicom.VR;

import ij.process.ByteProcessor;

/**
 * Writes the editable {@link FreeFormRoi3D} segmentation objects of a reference
 * series as a single multi-segment DICOM Segmentation (SEG) instance
 * (SOP Class 1.2.840.10008.5.1.4.1.1.66.4, SegmentationType BINARY, 1 bit/pixel).
 *
 * <p>The geometry (orientation/spacing/origin) is taken from the segmentation
 * volumes (which were copied from the reference series at creation) and new
 * UIDs are generated. Structure follows the standard SEG IOD: shared
 * PlaneOrientation/PixelMeasures functional groups, one per-frame group per
 * (segment, occupied slice) carrying FrameContent/PlanePosition/SegmentIdentification,
 * plus DimensionOrganization/DimensionIndex.
 *
 * @author tatsunidas
 */
public final class SegWriter {

	// SOP / transfer syntax
	private static final String SEG_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.66.4";
	private static final String EXPLICIT_VR_LE = "1.2.840.10008.1.2.1";

	// Tags used (integer hex literals; robust against named-constant quirks).
	private static final int SpecificCharacterSet = 0x00080005;
	private static final int ImageType = 0x00080008;
	private static final int SOPClassUID = 0x00080016;
	private static final int SOPInstanceUID = 0x00080018;
	private static final int StudyDate = 0x00080020;
	private static final int SeriesDate = 0x00080021;
	private static final int ContentDate = 0x00080023;
	private static final int StudyTime = 0x00080030;
	private static final int SeriesTime = 0x00080031;
	private static final int ContentTime = 0x00080033;
	private static final int AccessionNumber = 0x00080050;
	private static final int Modality = 0x00080060;
	private static final int Manufacturer = 0x00080070;
	private static final int ReferringPhysicianName = 0x00080090;
	private static final int StudyDescription = 0x00081030;
	private static final int SeriesDescription = 0x0008103E;
	private static final int ManufacturerModelName = 0x00081090;
	private static final int ReferencedSeriesSequence = 0x00081115;
	private static final int ReferencedInstanceSequence = 0x0008114A;
	private static final int ReferencedSOPClassUID = 0x00081150;
	private static final int ReferencedSOPInstanceUID = 0x00081155;
	private static final int PatientName = 0x00100010;
	private static final int PatientID = 0x00100020;
	private static final int PatientBirthDate = 0x00100030;
	private static final int PatientSex = 0x00100040;
	private static final int StudyInstanceUID = 0x0020000D;
	private static final int SeriesInstanceUID = 0x0020000E;
	private static final int StudyID = 0x00200010;
	private static final int SeriesNumber = 0x00200011;
	private static final int InstanceNumber = 0x00200013;
	private static final int ImagePositionPatient = 0x00200032;
	private static final int ImageOrientationPatient = 0x00200037;
	private static final int FrameOfReferenceUID = 0x00200052;
	private static final int PositionReferenceIndicator = 0x00201040;
	private static final int DimensionOrganizationSequence = 0x00209221;
	private static final int DimensionIndexSequence = 0x00209222;
	private static final int DimensionOrganizationUID = 0x00209164;
	private static final int DimensionIndexPointer = 0x00209165;
	private static final int FunctionalGroupPointer = 0x00209167;
	private static final int DimensionDescriptionLabel = 0x00209421;
	private static final int DimensionIndexValues = 0x00209157;
	private static final int SamplesPerPixel = 0x00280002;
	private static final int PhotometricInterpretation = 0x00280004;
	private static final int NumberOfFrames = 0x00280008;
	private static final int Rows = 0x00280010;
	private static final int Columns = 0x00280011;
	private static final int PixelSpacing = 0x00280030;
	private static final int BitsAllocated = 0x00280100;
	private static final int BitsStored = 0x00280101;
	private static final int HighBit = 0x00280102;
	private static final int PixelRepresentation = 0x00280103;
	private static final int SliceThickness = 0x00180050;
	private static final int SpacingBetweenSlices = 0x00180088;
	private static final int LossyImageCompression = 0x00282110;
	private static final int SegmentationType = 0x00620001;
	private static final int SegmentSequence = 0x00620002;
	private static final int SegmentedPropertyCategoryCodeSequence = 0x00620003;
	private static final int SegmentNumber = 0x00620004;
	private static final int SegmentLabel = 0x00620005;
	private static final int SegmentAlgorithmType = 0x00620008;
	private static final int RecommendedDisplayCIELabValue = 0x0062000D;
	private static final int SegmentedPropertyTypeCodeSequence = 0x0062000F;
	private static final int SegmentIdentificationSequence = 0x0062000A;
	private static final int ReferencedSegmentNumber = 0x0062000B;
	private static final int CodeValue = 0x00080100;
	private static final int CodingSchemeDesignator = 0x00080102;
	private static final int CodeMeaning = 0x00080104;
	private static final int ContentLabel = 0x00700080;
	private static final int ContentDescription = 0x00700081;
	private static final int ContentCreatorName = 0x00700084;
	private static final int SharedFunctionalGroupsSequence = 0x52009229;
	private static final int PerFrameFunctionalGroupsSequence = 0x52009230;
	private static final int PlaneOrientationSequence = 0x00209116;
	private static final int PixelMeasuresSequence = 0x00289110;
	private static final int FrameContentSequence = 0x00209111;
	private static final int PlanePositionSequence = 0x00209113;
	private static final int PixelData = 0x7FE00010;

	private SegWriter() {
	}

	/** A frame to emit: (1-based segment number, occupied volume-Z index, source object). */
	private static final class FrameRef {
		final int segNumber;
		final int dimIndex; // 1-based slice dimension index (the displayed slice)
		final double[] ipp; // ImagePositionPatient for this frame
		final FreeFormRoi3D seg;
		final int volumeK; // segment volume index for this slice (-1 = no mask plane)

		FrameRef(int segNumber, int dimIndex, double[] ipp, FreeFormRoi3D seg, int volumeK) {
			this.segNumber = segNumber;
			this.dimIndex = dimIndex;
			this.ipp = ipp;
			this.seg = seg;
			this.volumeK = volumeK;
		}
	}

	/**
	 * Builds a multi-segment SEG dataset from the given segmentation objects.
	 *
	 * @return the SEG DicomObject, or null if there is nothing to write
	 */
	public static DicomObject build(Praparat pp, List<FreeFormRoi3D> segments) {
		if (segments == null || segments.isEmpty()) {
			return null;
		}
		// Keep only segments that have at least one occupied slice, geometry-ordered by number.
		List<FreeFormRoi3D> usable = new ArrayList<>();
		for (FreeFormRoi3D s : segments) {
			if (s != null && s.isInitialized() && !s.getOccupiedSliceIndices().isEmpty()) {
				usable.add(s);
			}
		}
		if (usable.isEmpty()) {
			return null;
		}

		SlideGlass refSg = (pp != null) ? pp.getFirstNoEmptySlide() : null;
		DicomObject ref = (refSg != null) ? refSg.getHeader() : null;

		// Default (fallback) geometry from the first segment's own volume. Used only
		// when there is no reference series (e.g. headless tests).
		FreeFormRoi3D geom0 = usable.get(0);
		int[] dim0 = geom0.getDimensions();
		int cols = dim0[0];
		int rows = dim0[1];
		double[] iop = geom0.getIop();
		double[] spacing = geom0.getSpacing(); // [sx, sy, sz]

		// Anchor the SEG to the REFERENCE series slice grid so the output lands on the
		// same slices (e.g. 43) the masks were edited on. Each segment is then written
		// across every reference slice -> a regular (slices x channels) grid.
		java.util.List<double[]> sliceIpps = new ArrayList<>();
		boolean anchored = false;
		if (pp != null && refSg != null && pp.getAllSlides() != null && !pp.getAllSlides().isEmpty()) {
			cols = refSg.getOriginalImageSize().width;
			rows = refSg.getOriginalImageSize().height;
			int fIdx0 = pp.isMultiFrame() ? refSg.getHeader().getInt(InstanceNumber, 1) - 1 : 0;
			double[] refIop = pp.getSafeIOP(refSg.getHeader(), fIdx0);
			if (refIop != null && refIop.length == 6) {
				iop = refIop;
			}
			double sxr = refSg.getPixelSpacingX() <= 0 ? 1.0 : refSg.getPixelSpacingX();
			double syr = refSg.getPixelSpacingY() <= 0 ? 1.0 : refSg.getPixelSpacingY();
			int[] zct = pp.getZCTArray(refSg);
			int c0 = (zct != null && zct[1] >= 0) ? zct[1] : 0;
			int t0 = (zct != null && zct[2] >= 0) ? zct[2] : 0;
			int maxZ = 0;
			for (Integer key : pp.getAllSlides().keySet()) {
				int z = pp.calcZCTArrayFromIndex(key)[0];
				if (z > maxZ) {
					maxZ = z;
				}
			}
			for (int z = 0; z <= maxZ; z++) {
				SlideGlass sgz = pp.getSlideGlassAt(pp.calcZctIndex(new int[] { z, c0, t0 }));
				if (sgz == null) {
					continue;
				}
				int fIdx = pp.isMultiFrame() ? sgz.getHeader().getInt(InstanceNumber, 1) - 1 : 0;
				double[] ippz = pp.getSafeIPP(sgz.getHeader(), fIdx);
				if (ippz != null && ippz.length >= 3) {
					sliceIpps.add(ippz);
				}
			}
			double szr = refSg.getHeader().getDouble(SpacingBetweenSlices,
					refSg.getHeader().getDouble(SliceThickness, 0.0));
			if (szr <= 0 && sliceIpps.size() >= 2) {
				double[] a0 = sliceIpps.get(0), a1 = sliceIpps.get(1);
				szr = Math.sqrt((a1[0] - a0[0]) * (a1[0] - a0[0]) + (a1[1] - a0[1]) * (a1[1] - a0[1])
						+ (a1[2] - a0[2]) * (a1[2] - a0[2]));
			}
			if (szr <= 0) {
				szr = geom0.getSpacing()[2];
			}
			spacing = new double[] { sxr, syr, szr };
			anchored = !sliceIpps.isEmpty();
		}

		// (B) Drop segments that have no content on the reference grid (e.g. a chest SEG
		// imported onto an abdominal series), so they never become empty channels.
		if (anchored) {
			List<FreeFormRoi3D> onGrid = new ArrayList<>();
			for (FreeFormRoi3D s : usable) {
				boolean has = false;
				for (double[] ippz : sliceIpps) {
					int vk = s.getZIndexForSlice(ippz);
					if (vk >= 0 && s.getMaskAsBytes(vk) != null) {
						has = true;
						break;
					}
				}
				if (has) {
					onGrid.add(s);
				}
			}
			usable = onGrid;
			if (usable.isEmpty()) {
				return null;
			}
		}

		DicomObject seg = DicomObject.newDicomObject();
		String newSeriesUID = UIDUtils.createUID();
		String newSopUID = UIDUtils.createUID();
		String dimOrgUID = UIDUtils.createUID();

		// ---- General / Patient / Study (copied from the reference) ----
		seg.setString(SpecificCharacterSet, VR.CS, "ISO_IR 100");
		seg.setString(ImageType, VR.CS, "DERIVED", "PRIMARY");
		seg.setString(SOPClassUID, VR.UI, SEG_SOP_CLASS);
		seg.setString(SOPInstanceUID, VR.UI, newSopUID);
		seg.setString(Modality, VR.CS, "SEG");
		seg.setString(Manufacturer, VR.LO, "GRAPHY");
		seg.setString(ManufacturerModelName, VR.LO, "GRAPHY Segmentation");
		seg.setString(SeriesDescription, VR.LO, "Segmentation");
		copy(ref, seg, StudyDate, VR.DA);
		copy(ref, seg, StudyTime, VR.TM);
		copy(ref, seg, AccessionNumber, VR.SH);
		copy(ref, seg, ReferringPhysicianName, VR.PN);
		copy(ref, seg, StudyDescription, VR.LO);
		copy(ref, seg, PatientName, VR.PN);
		copy(ref, seg, PatientID, VR.LO);
		copy(ref, seg, PatientBirthDate, VR.DA);
		copy(ref, seg, PatientSex, VR.CS);
		copy(ref, seg, StudyInstanceUID, VR.UI);
		copy(ref, seg, StudyID, VR.SH);
		copy(ref, seg, FrameOfReferenceUID, VR.UI);
		// Content/Series date-time mirror the study date when available.
		String sd = (ref != null) ? ref.getString(StudyDate) : null;
		if (sd != null) {
			seg.setString(SeriesDate, VR.DA, sd);
			seg.setString(ContentDate, VR.DA, sd);
		}
		seg.setString(SeriesInstanceUID, VR.UI, newSeriesUID);
		seg.setString(SeriesNumber, VR.IS, "300");
		seg.setString(InstanceNumber, VR.IS, "1");
		seg.setString(PositionReferenceIndicator, VR.LO, "");
		seg.setString(ContentLabel, VR.CS, "SEGMENTATION");
		seg.setString(ContentDescription, VR.LO, "Segmentation");
		seg.setString(ContentCreatorName, VR.PN, "GRAPHY");

		// ---- Referenced Series (link to the source images) ----
		writeReferencedSeries(seg, pp, ref);

		// ---- Dimension Organization / Index ----
		Sequence dimOrg = (Sequence) seg.newDicomSequence(DimensionOrganizationSequence, 1);
		DicomObject dimOrgItem = DicomObject.newDicomObject();
		dimOrgItem.setString(DimensionOrganizationUID, VR.UI, dimOrgUID);
		dimOrg.add(dimOrgItem);

		Sequence dimIdx = (Sequence) seg.newDicomSequence(DimensionIndexSequence, 2);
		dimIdx.add(dimIndexItem(dimOrgUID, ReferencedSegmentNumber, SegmentIdentificationSequence,
				"ReferencedSegmentNumber"));
		dimIdx.add(dimIndexItem(dimOrgUID, ImagePositionPatient, PlanePositionSequence,
				"ImagePositionPatient"));

		// ---- Segment Sequence (one item per object) ----
		Sequence segSeq = (Sequence) seg.newDicomSequence(SegmentSequence, usable.size());
		for (int si = 0; si < usable.size(); si++) {
			FreeFormRoi3D s = usable.get(si);
			// Always assign a fresh, unique 1-based number so mixing objects from
			// different sources (drawn + imported) can never collide in the SEG.
			int number = si + 1;
			segSeq.add(segmentItem(number, s));
		}

		// ---- Frame plan (segment-major). Unique 1-based segment numbers avoid any
		// collision when mixing objects from different sources (drawn + imported). ----
		List<FrameRef> frames = new ArrayList<>();
		for (int si = 0; si < usable.size(); si++) {
			FreeFormRoi3D s = usable.get(si);
			int number = si + 1;
			if (anchored) {
				// Dense: every segment across every reference slice -> regular grid, so
				// the SEG displays as (reference slices x channels), e.g. 43 x N.
				for (int zi = 0; zi < sliceIpps.size(); zi++) {
					double[] ippz = sliceIpps.get(zi);
					int vk = s.getZIndexForSlice(ippz);
					frames.add(new FrameRef(number, zi + 1, ippz, s, vk));
				}
			} else {
				// Fallback (no reference): sparse over the segment's own volume grid.
				for (int k : s.getOccupiedSliceIndices()) {
					frames.add(new FrameRef(number, k + 1, volumeFrameIpp(s, k), s, k));
				}
			}
		}

		// ---- Image / Pixel module ----
		seg.setInt(SamplesPerPixel, VR.US, 1);
		seg.setString(PhotometricInterpretation, VR.CS, "MONOCHROME2");
		seg.setString(NumberOfFrames, VR.IS, String.valueOf(frames.size()));
		seg.setInt(Rows, VR.US, rows);
		seg.setInt(Columns, VR.US, cols);
		seg.setInt(BitsAllocated, VR.US, 1);
		seg.setInt(BitsStored, VR.US, 1);
		seg.setInt(HighBit, VR.US, 0);
		seg.setInt(PixelRepresentation, VR.US, 0);
		seg.setString(LossyImageCompression, VR.CS, "00");
		seg.setString(SegmentationType, VR.CS, "BINARY");
		// Also retain voxel-size image attributes at the top level (in addition to the
		// IOD-correct PixelMeasures functional group) so simpler readers can find them.
		seg.setDouble(PixelSpacing, VR.DS, spacing[1], spacing[0]);
		seg.setDouble(SliceThickness, VR.DS, spacing[2]);
		seg.setDouble(SpacingBetweenSlices, VR.DS, spacing[2]);

		// ---- Shared Functional Groups (orientation + pixel measures) ----
		Sequence shared = (Sequence) seg.newDicomSequence(SharedFunctionalGroupsSequence, 1);
		DicomObject sharedItem = DicomObject.newDicomObject();
		Sequence planeOrient = (Sequence) sharedItem.newDicomSequence(PlaneOrientationSequence, 1);
		DicomObject planeOrientItem = DicomObject.newDicomObject();
		planeOrientItem.setDouble(ImageOrientationPatient, VR.DS,
				iop[0], iop[1], iop[2], iop[3], iop[4], iop[5]);
		planeOrient.add(planeOrientItem);
		Sequence pixMeas = (Sequence) sharedItem.newDicomSequence(PixelMeasuresSequence, 1);
		DicomObject pixMeasItem = DicomObject.newDicomObject();
		pixMeasItem.setDouble(SliceThickness, VR.DS, spacing[2]);
		pixMeasItem.setDouble(SpacingBetweenSlices, VR.DS, spacing[2]);
		// PixelSpacing = [row spacing (Y), column spacing (X)].
		pixMeasItem.setDouble(PixelSpacing, VR.DS, spacing[1], spacing[0]);
		pixMeas.add(pixMeasItem);
		shared.add(sharedItem);

		// ---- Per-frame Functional Groups ----
		Sequence perFrame = (Sequence) seg.newDicomSequence(PerFrameFunctionalGroupsSequence, frames.size());
		for (FrameRef fr : frames) {
			perFrame.add(perFrameItem(fr));
		}

		// ---- Pixel Data (continuous LSB-first 1-bit packing across frames) ----
		seg.setBytes(PixelData, VR.OB, packBits(frames, rows, cols));

		return seg;
	}

	/** Builds the SEG and writes it to {@code destPath} (Explicit VR LE). */
	public static boolean writeToFile(Praparat pp, List<FreeFormRoi3D> segments, String destPath) {
		return writeToFile(build(pp, segments), destPath);
	}

	/** Writes a prebuilt SEG dataset to {@code destPath} (Explicit VR LE). */
	public static boolean writeToFile(DicomObject seg, String destPath) {
		if (seg == null) {
			return false;
		}
		try {
			DicomWriter.newDicomWriter().write(seg, EXPLICIT_VR_LE, destPath);
			return true;
		} catch (Exception e) {
			Log.logger.warning("SegWriter: failed to write SEG: " + e);
			return false;
		}
	}

	// ========== helpers ==========

	private static void copy(DicomObject ref, DicomObject dst, int tag, VR vr) {
		if (ref == null) {
			return;
		}
		String v = ref.getString(tag);
		if (v != null) {
			dst.setString(tag, vr, v);
		}
	}

	private static DicomObject dimIndexItem(String dimOrgUID, int indexPointer, int fgPointer, String label) {
		DicomObject item = DicomObject.newDicomObject();
		item.setString(DimensionOrganizationUID, VR.UI, dimOrgUID);
		item.setInt(DimensionIndexPointer, VR.AT, indexPointer);
		item.setInt(FunctionalGroupPointer, VR.AT, fgPointer);
		item.setString(DimensionDescriptionLabel, VR.LO, label);
		return item;
	}

	private static DicomObject segmentItem(int number, FreeFormRoi3D s) {
		DicomObject item = DicomObject.newDicomObject();
		// Generic property codes (label carries the user-facing name).
		Sequence cat = (Sequence) item.newDicomSequence(SegmentedPropertyCategoryCodeSequence, 1);
		cat.add(codeItem("123037004", "SCT", "Anatomical Structure"));
		item.setInt(SegmentNumber, VR.US, number);
		String label = s.getName();
		if (label == null || label.trim().isEmpty()) {
			label = "Segment " + number;
		}
		item.setString(SegmentLabel, VR.LO, label);
		item.setString(SegmentAlgorithmType, VR.CS, "MANUAL");
		int[] lab = rgbToDicomCieLab(s.getSegmentColor());
		item.setInt(RecommendedDisplayCIELabValue, VR.US, lab[0], lab[1], lab[2]);
		Sequence type = (Sequence) item.newDicomSequence(SegmentedPropertyTypeCodeSequence, 1);
		type.add(codeItem("85756007", "SCT", "Tissue"));
		return item;
	}

	private static DicomObject codeItem(String value, String designator, String meaning) {
		DicomObject item = DicomObject.newDicomObject();
		item.setString(CodeValue, VR.SH, value);
		item.setString(CodingSchemeDesignator, VR.SH, designator);
		item.setString(CodeMeaning, VR.LO, meaning);
		return item;
	}

	private static DicomObject perFrameItem(FrameRef fr) {
		DicomObject item = DicomObject.newDicomObject();

		// FrameContent: dimension index values [segmentNumber, sliceIndex(1-based)].
		Sequence fc = (Sequence) item.newDicomSequence(FrameContentSequence, 1);
		DicomObject fcItem = DicomObject.newDicomObject();
		fcItem.setInt(DimensionIndexValues, VR.UL, fr.segNumber, fr.dimIndex);
		fc.add(fcItem);

		// PlanePosition: this frame's ImagePositionPatient (reference slice when anchored).
		double[] ipp = fr.ipp;
		Sequence pp = (Sequence) item.newDicomSequence(PlanePositionSequence, 1);
		DicomObject ppItem = DicomObject.newDicomObject();
		ppItem.setDouble(ImagePositionPatient, VR.DS, ipp[0], ipp[1], ipp[2]);
		pp.add(ppItem);

		// SegmentIdentification: which segment this frame belongs to.
		Sequence sid = (Sequence) item.newDicomSequence(SegmentIdentificationSequence, 1);
		DicomObject sidItem = DicomObject.newDicomObject();
		sidItem.setInt(ReferencedSegmentNumber, VR.US, fr.segNumber);
		sid.add(sidItem);

		return item;
	}

	private static double[] volumeFrameIpp(FreeFormRoi3D seg, int k) {
		double[] iop = seg.getIop();
		double nx = iop[1] * iop[5] - iop[2] * iop[4];
		double ny = iop[2] * iop[3] - iop[0] * iop[5];
		double nz = iop[0] * iop[4] - iop[1] * iop[3];
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len > 1e-12) {
			nx /= len;
			ny /= len;
			nz /= len;
		}
		double[] o = seg.getOriginIpp();
		double sz = seg.getSpacing()[2];
		return new double[] { o[0] + nx * k * sz, o[1] + ny * k * sz, o[2] + nz * k * sz };
	}

	private static void writeReferencedSeries(DicomObject seg, Praparat pp, DicomObject ref) {
		if (ref == null) {
			return;
		}
		String refSeriesUID = ref.getString(SeriesInstanceUID);
		if (refSeriesUID == null) {
			return;
		}
		Sequence refSeriesSeq = (Sequence) seg.newDicomSequence(ReferencedSeriesSequence, 1);
		DicomObject refSeriesItem = DicomObject.newDicomObject();
		refSeriesItem.setString(SeriesInstanceUID, VR.UI, refSeriesUID);

		Sequence refInstSeq = (Sequence) refSeriesItem.newDicomSequence(ReferencedInstanceSequence, 0);
		Set<String> seen = new LinkedHashSet<>();
		if (pp.getAllSlides() != null) {
			for (SlideGlass sg : pp.getAllSlides().values()) {
				if (sg == null) {
					continue;
				}
				String sop = sg.getSOPInstanceUID();
				if (sop == null || !seen.add(sop)) {
					continue;
				}
				String sopClass = sg.getHeader() != null ? sg.getHeader().getString(SOPClassUID) : null;
				DicomObject refInstItem = DicomObject.newDicomObject();
				refInstItem.setString(ReferencedSOPClassUID, VR.UI,
						sopClass != null ? sopClass : "1.2.840.10008.5.1.4.1.1.2");
				refInstItem.setString(ReferencedSOPInstanceUID, VR.UI, sop);
				refInstSeq.add(refInstItem);
			}
		}
		refSeriesSeq.add(refSeriesItem);
	}

	/**
	 * Packs the binary frames into one continuous bit stream, least-significant-bit
	 * first (DICOM 1-bit packing), as required for BitsAllocated=1.
	 */
	private static byte[] packBits(List<FrameRef> frames, int rows, int cols) {
		long totalBits = (long) frames.size() * rows * cols;
		int nBytes = (int) ((totalBits + 7) / 8);
		byte[] out = new byte[nBytes];
		long p = 0;
		byte[] empty = new byte[rows * cols];
		for (FrameRef fr : frames) {
			ByteProcessor bp = (fr.volumeK >= 0) ? fr.seg.getMaskAsBytes(fr.volumeK) : null;
			byte[] pix = empty;
			if (bp != null && bp.getWidth() == cols && bp.getHeight() == rows) {
				pix = (byte[]) bp.getPixels();
			}
			for (int idx = 0; idx < rows * cols; idx++, p++) {
				if (pix[idx] != 0) {
					out[(int) (p >> 3)] |= (1 << (int) (p & 7));
				}
			}
		}
		return out;
	}

	/**
	 * Converts an sRGB color to a DICOM RecommendedDisplayCIELabValue triplet
	 * (16-bit: L in 0..100 -> 0..65535; a*,b* in -128..127 -> 0..65535).
	 */
	private static int[] rgbToDicomCieLab(java.awt.Color c) {
		if (c == null) {
			c = java.awt.Color.YELLOW;
		}
		double r = srgbToLinear(c.getRed() / 255.0);
		double g = srgbToLinear(c.getGreen() / 255.0);
		double b = srgbToLinear(c.getBlue() / 255.0);
		// linear sRGB -> XYZ (D65)
		double x = r * 0.4124 + g * 0.3576 + b * 0.1805;
		double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
		double z = r * 0.0193 + g * 0.1192 + b * 0.9505;
		// normalize by D65 white
		double fx = labF(x / 0.95047);
		double fy = labF(y / 1.0);
		double fz = labF(z / 1.08883);
		double L = 116.0 * fy - 16.0;
		double a = 500.0 * (fx - fy);
		double bb = 200.0 * (fy - fz);
		int Li = clamp16((int) Math.round(L * 65535.0 / 100.0));
		int ai = clamp16((int) Math.round((a + 128.0) * 65535.0 / 255.0));
		int bi = clamp16((int) Math.round((bb + 128.0) * 65535.0 / 255.0));
		return new int[] { Li, ai, bi };
	}

	private static double srgbToLinear(double v) {
		return (v <= 0.04045) ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	private static double labF(double t) {
		return (t > 0.008856) ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
	}

	private static int clamp16(int v) {
		return Math.max(0, Math.min(65535, v));
	}
}
