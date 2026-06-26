package com.vis.core.reporting.measurement;

import com.vis.core.reporting.KeyImageRef;

/**
 * The geometry a measurement was taken from — a DICOM {@code SCOORD} (2D, image
 * pixel space) or {@code SCOORD3D} (3D, patient / frame-of-reference space)
 * content item.
 * <p>
 * Two flavours, distinguished by {@link #threeD}:
 * <ul>
 *   <li><b>2D ({@code SCOORD})</b>: {@link #data} are interleaved column/row pixel
 *       coordinates {@code [c0,r0,c1,r1,...]}; the geometry is anchored to a single
 *       image via {@link #image} (emitted as a {@code SELECTED FROM} IMAGE child).</li>
 *   <li><b>3D ({@code SCOORD3D})</b>: {@link #data} are interleaved x/y/z millimetre
 *       coordinates {@code [x0,y0,z0,...]} in the frame of reference named by
 *       {@link #frameOfReferenceUID}; no per-image anchor.</li>
 * </ul>
 * {@link #graphicType} is the DICOM Graphic Type code (CS): {@code POINT},
 * {@code MULTIPOINT}, {@code POLYLINE}, {@code CIRCLE}, {@code ELLIPSE} for SCOORD;
 * {@code POINT}, {@code MULTIPOINT}, {@code POLYLINE}, {@code POLYGON},
 * {@code ELLIPSE}, {@code ELLIPSOID} for SCOORD3D.
 *
 * @author tatsunidas
 */
public class SpatialCoordinate {

	public static final String POINT = "POINT";
	public static final String MULTIPOINT = "MULTIPOINT";
	public static final String POLYLINE = "POLYLINE";
	public static final String POLYGON = "POLYGON";
	public static final String CIRCLE = "CIRCLE";
	public static final String ELLIPSE = "ELLIPSE";
	public static final String ELLIPSOID = "ELLIPSOID";

	private boolean threeD;
	private String graphicType;
	private float[] data = new float[0];

	// 2D (SCOORD): anchored to a single image instance.
	private KeyImageRef image;
	// 3D (SCOORD3D): defined in a frame of reference.
	private String frameOfReferenceUID;

	public SpatialCoordinate() {
	}

	/** Build a 2D image-anchored SCOORD. {@code data} are interleaved column/row pixels. */
	public static SpatialCoordinate scoord(String graphicType, float[] data, KeyImageRef image) {
		SpatialCoordinate s = new SpatialCoordinate();
		s.threeD = false;
		s.graphicType = graphicType;
		s.data = data == null ? new float[0] : data;
		s.image = image;
		return s;
	}

	/** Build a 3D SCOORD3D. {@code data} are interleaved x/y/z millimetres in {@code frameOfReferenceUID}. */
	public static SpatialCoordinate scoord3d(String graphicType, float[] data, String frameOfReferenceUID) {
		SpatialCoordinate s = new SpatialCoordinate();
		s.threeD = true;
		s.graphicType = graphicType;
		s.data = data == null ? new float[0] : data;
		s.frameOfReferenceUID = frameOfReferenceUID;
		return s;
	}

	public boolean isThreeD() {
		return threeD;
	}

	public void setThreeD(boolean threeD) {
		this.threeD = threeD;
	}

	public String getGraphicType() {
		return graphicType;
	}

	public void setGraphicType(String graphicType) {
		this.graphicType = graphicType;
	}

	public float[] getData() {
		return data;
	}

	public void setData(float[] data) {
		this.data = data == null ? new float[0] : data;
	}

	public KeyImageRef getImage() {
		return image;
	}

	public void setImage(KeyImageRef image) {
		this.image = image;
	}

	public String getFrameOfReferenceUID() {
		return frameOfReferenceUID;
	}

	public void setFrameOfReferenceUID(String frameOfReferenceUID) {
		this.frameOfReferenceUID = frameOfReferenceUID;
	}

	/** @return number of points (2 floats each for 2D, 3 for 3D). */
	public int pointCount() {
		int per = threeD ? 3 : 2;
		return data.length / per;
	}
}
