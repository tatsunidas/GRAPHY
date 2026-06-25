/**
 * copyright Visionary Imaging Services, Inc.
 * @author tatsunidas
 */
package com.vis.configuration;

/**
 * ContextKey : main properties for roi.
 * RoiMetaContextKey : sub-meta properties for roi manipulations.
 */
public enum RoiMetaContextKey {
	isSplineFit,
	ReferenceImagePositionPatient,//ipp of dcm image that provide it's roi.
	Dim_C,
	Dim_Z,
	Dim_T,
	Shape_3D_Type,// "Shape_3D_SPHERE" または "Shape_3D_FREEFORM"
	Shape_3D_SPHERE,
	Shape_3D_FREEFORM,
	Sphere_Center_IPP,
	Sphere_Radius_mm,
	// Segmentation object attributes (a FreeFormRoi3D used as an editable mask/segment).
	Is_Segmentation,// "true" when this 3D ROI is an editable segmentation object
	Segment_Number,// 1-based segment identity used when exporting to DICOM SEG
	Segment_Color,// display color as "r,g,b" (persisted per object, unlike transient strokeColor)
	;
}
