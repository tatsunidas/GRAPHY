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
package com.vis.dicom.dcm4cheImpl;

import org.dcm4che3.data.Tag;

import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.UID;
import com.vis.dicom.image.DicomImage;

/**
 * 
 * @author tatsunidas
 *
 */
public class DicomImageChe extends DicomObjectChe implements DicomImage{
	
	private static final long serialVersionUID = 1L;
	protected DicomObject core = null;
	protected DicomObject fmi = null;
	protected UID tsuid;
	protected UID sopUID;
	
	public DicomImageChe(String path, boolean withPixel) {
		DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
		reader.read(path, withPixel);
		this.core = reader.getCore();
		this.fmi = reader.getFileMetaInfomation();
		this.tsuid = reader.checkTSUID();
		reader = null;
	}

	@Override
	public void setCore(DicomObject attr) {
		this.core = attr;
	}

	@Override
	public DicomObject getCore() {
		return core;
	}

	@Override
	public void setFileMetaInfo(DicomObject fmi) {
		this.fmi = fmi;
	}

	@Override
	public DicomObject getFileMetaInfo() {
		return fmi;
	}

	@Override
	public UID getTSUID() {
		return tsuid;
	}

	@Override
	public void updateFileMetaInfo(com.vis.dicom.UID uid) {
		this.fmi = (DicomObject) core.createFileMetaInformation(uid.uid());
	}

	@Override
	public Object pixelData() {
		int bitsAllocated = this.core.getInt(Tag.BitsAllocated, -1);
		if(bitsAllocated == -1) {
			//this core does not have pixel
			return null;
		}
		if(bitsAllocated == 8 || bitsAllocated == 16) {
			return this.core.getValue(Tag.PixelData);
		}else if(bitsAllocated == 32) {
			return this.core.getValue(Tag.FloatPixelData);
		}else if(bitsAllocated == 64) {
			return this.core.getValue(Tag.DoubleFloatPixelData);
		}
		return null;
	}

	@Override
	public boolean isPDF() {
		return sopUID == 	UID.EncapsulatedPDFStorage ? true : false;
	}

	@Override
	public boolean isMultiFrame() {
		int frames = this.core.getInt(Tag.NumberOfFrames, 1);
		return frames == 1 ? false:true;
	}

	@Override
	public UID sopUID() {
		return this.sopUID;
	}

}