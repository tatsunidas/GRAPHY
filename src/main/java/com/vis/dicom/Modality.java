/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
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
 */
package com.vis.dicom;

/**
 * Defined Terms for the Modality (0008,0060) tag
 * @author tatsunidas
 *
 */
public enum Modality {
	CR,// "Computed Radiography"
	CT,//	"Computed Tomography"
	MR,//	"Magnetic Resonance"
	US,//	"Ultrasound"
	OT,//	"Other"
	BI,//	"Biomagnetic imaging"
	CD,//	"Color flow Doppler"
	DD,//	"Duplex Doppler"
	DG,//	"Diaphanography"
	ES,//	"Endoscopy"
	LS,//	"Laser surface scan"
	PT,//	"Positron emission tomography"
	RG,//	"Radiographic imaging (conventional film/screen)
	ST,//	"Single-photon emission computed tomography (SPECT)
	TG,//	"Thermography
	XA,//	"X-Ray Angiography
	RF,//	"Radio Fluoroscopy
	RTIMAGE,//	"Radiotherapy Image
	RTDOSE,//	"Radiotherapy Dose
	RTSTRUCT,//	"Radiotherapy Structure Set
	RTPLAN,//	"Radiotherapy Plan
	RTRECORD,//	"RT Treatment Record
	HC,//	"Hard Copy
	DX,//	"Digital Radiography
	NM,//	"Nuclear Medicine
	MG,//	"Mammography
	IO,//	"Intra-oral Radiography
	PX,//	"Panoramic X-Ray
	GM,//	"General Microscopy
	SM,//	"Slide Microscopy
	XC,//	"External-camera Photography
	PR,//	"Presentation State
	AU,//	"Audio ECG
	EPS,//	"Cardiac Electrophysiology
	HD,//	"Hemodynamic Waveform
	SR,//	"Structured Report
	IVUS,//	"Intravascular Ultrasound
	OP,//	"Ophthalmic Photography
	SMR,//	"Stereometric Relationship
	UNKNOWN,
	;
	
	public static Modality is(DicomObject dcm) {
		if(dcm != null) {
			String m = dcm.getString(Tag.Modality);
			for(Modality m_ : values()) {
				if(m_.toString().equals(m)) {
					return m_;
				}
			}
		}
		return UNKNOWN;
	}
	
	public static Modality is(String modalityString) {
		if(modalityString != null) {
			String m = modalityString;
			for (Modality m_ : values()) {
				if (m_.toString().equals(m)) {
					return m_;
				}
			}
		}
		return UNKNOWN;
	}
}
