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
	Is3D_Master,
	Is3D_Slave,
	Is3D_Volume,
	Shape_3D_Type,// "SPHERE" または "FREEFORM"
	Sphere_Center_IPP,
	Sphere_Radius_mm,
	;
}
