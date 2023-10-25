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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.stream.FileImageInputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Tag;

import com.vis.core.log.Log;
import com.vis.core.util.ByteUtils;
import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.UID;
import com.vis.dicom.VR;
import com.vis.dicom.image.BufferedImageUtils;
import com.vis.dicom.image.DicomImage;
import com.vis.dicom.image.PhotometricInterpretation;
import com.vis.imageio.Codec;
import com.vis.imageio.PixelDataDecoder;

import ij.process.ImageProcessor;

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
	boolean decompressed = false;
	
	public DicomImageChe(DicomObject core, UID tsUID) {
		this(core, null, tsUID);
	}
	
	public DicomImageChe(DicomObject core, DicomObject fmi, UID tsUID) {
		if(core == null ) {
			return;
		}
		if(fmi == null) {
			core.createFileMetaInformation(tsUID.uid());
		}
		this.core = core;
		this.fmi = fmi;
		this.tsuid = tsUID;
	}
	
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
		updateFileMetaInfo(tsuid);
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
	public void updateFileMetaInfo(com.vis.dicom.UID tsuid) {
		this.fmi = (DicomObject) core.createFileMetaInformation(tsuid.uid());
	}

	@Override
	/**
	 * if you want decompressed pixels, do decompress before getPixelData().
	 */
	public byte[] getPixelData(int frame) {
		if(getBitsAllocated() == -1) {
			//this core does not have pixel
			return null;
		}
		
		if(frame < 0) {
			return null;
		}
		
		if(frame > getNumOfFrames()) {
			return null;
		}
		
		int bitsAllocated = getBitsAllocated();
		int samples = getSamples();
		
		Object bulk = null;
		byte[] pixels = null;
		
		if(bitsAllocated == 32 && samples == 1) {
			bulk = this.core.getValue(Tag.FloatPixelData);
			if(bulk == null) {
				this.core.getValue(Tag.PixelData);
			}
		}else if(bitsAllocated == 64 && samples == 1) {
			bulk = this.core.getValue(Tag.DoubleFloatPixelData);
			if(bulk == null) {
				this.core.getValue(Tag.PixelData);
			}
		}else {
			bulk = this.core.getValue(Tag.PixelData);
		}
		
		if(bulk instanceof Fragments) {
			Fragments frags = (Fragments)bulk;
			Object frag = frags.get(frame+1);//frame number count from 1
			if (frag instanceof BulkData) {
				try {
					pixels = ((BulkData) frag).toBytes(org.dcm4che3.data.VR.OB, false);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else if(frag instanceof byte[]) {
				pixels = (byte[])frag;
			}
		}else if(bulk instanceof byte[]) {
			pixels = (byte[])bulk;
		}else if(bulk instanceof BulkData) {
			BulkData bd = (BulkData)bulk;
			try {
				pixels = bd.toBytes(((Attributes)this.core).getVR(Tag.PixelData), bd.bigEndian());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else if(bulk instanceof org.dcm4che3.data.Value) {
			//decompressed pixel
			org.dcm4che3.data.Value decom_val = (org.dcm4che3.data.Value)bulk;
			try {
				pixels = decom_val.toBytes(org.dcm4che3.data.VR.OW, bigEndian());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return pixels;
	}
	
	@Override
	public void setPixelData(int frame, int w, int h, int samples, int bitsPerPixelSample, Object newFrame) {
		
		//byte[], short[], float[] or int[]
		byte[] pixels = null;
		if(newFrame instanceof byte[]) {
			pixels = (byte[])newFrame;
		}else if(newFrame instanceof short[]) {
			short[] newFrame_ = (short[])newFrame;
			pixels = ByteUtils.shortToBytes(newFrame_);
		}else if(newFrame instanceof float[]) {
			float[] newFrame_ = (float[])newFrame;
			pixels = ByteUtils.floatToBytes(newFrame_);
		}else if(newFrame instanceof double[]) {
			double[] newFrame_ = (double[])newFrame;
			pixels = ByteUtils.doubleToBytes(newFrame_);
		}else if(newFrame instanceof int[]) {
			int[] newFrame_ = (int[])newFrame;
			pixels = ByteUtils.intToBytes(newFrame_);
		}
		
		if(isPDF()) {
			//TODO
//			setPDF(newFrame);
		}
		
		if(frame > getNumOfFrames()) {
			return;
		}
		if(frame < 0) {
			return;
		}
		if(w != getWidth() || h != getHeight() || samples != getSamples()) {
			return;
		}
		
		int bitsAllocated = getBitsAllocated();
		
		Object bulk = null;
		// load from original bitsAllocated
		if(bitsAllocated == 32 && samples == 1) {
			bulk = this.core.getValue(Tag.FloatPixelData);
		}else if(bitsAllocated == 64 && samples == 1) {
			bulk = this.core.getValue(Tag.DoubleFloatPixelData);
		}else {
			bulk = this.core.getValue(Tag.PixelData);
		}
		
		if(bulk instanceof Fragments) {
			Fragments frags = (Fragments)bulk;
			Object frag = frags.get(frame+1);//frame number count from 1
			if(frag instanceof byte[]) {
				frags.set(frame+1, newFrame);
			}
//			core.setValue(tag, vr, fragments)
		}else if(bulk instanceof byte[]) {
			if(bitsAllocated == 32 && samples == 1) {
				core.setBytes(Tag.FloatPixelData, VR.OF, pixels);
				
				BufferedImageUtils.toImagePixelModule(samples, getPhotometricInterpletation().name(), getHeight(),  getWidth(),
			            pixels, core); 
				
			}else if(bitsAllocated == 64 && samples == 1) {
				core.setBytes(Tag.DoubleFloatPixelData, VR.OD, pixels);
			}else {
				if(bitsAllocated > 8 && bitsAllocated <= 16) {
					core.setBytes(Tag.PixelData, VR.OW, pixels);
				}else {
					core.setBytes(Tag.PixelData, VR.OB, pixels);
				}
			}
		}
	}
	
	public void setPDF(byte[] pdfByteArray) {
		
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
	public UID getSopUID() {
		return this.sopUID;
	}

	@Override
	public int getWidth() {
		return core.getInt(Tag.Columns, 0);
	}

	@Override
	public int getHeight() {
		return core.getInt(Tag.Rows, 0);
	}

	@Override
	public PhotometricInterpretation getPhotometricInterpletation() {
		return PhotometricInterpretation
				.fromString(core.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
	}
	
	@Override
	public int getPixel​Representation() {
		return core.getInt(Tag.PixelRepresentation, -1);
	}

	@Override
	public int getBitsAllocated() {
		return core.getInt(Tag.BitsAllocated, -1);
	}

	@Override
	public int getBitsStored() {
		return core.getInt(Tag.BitsStored, getBitsAllocated());
	}

	@Override
	public boolean isColor() {
		return getSamples() == 3;
	}

	@Override
	public boolean isBanded() {
		return core.getInt(Tag.PlanarConfiguration, 0) != 0;
	}

	@Override
	public boolean isSigned() {
		return core.getInt(Tag.PixelRepresentation, 0) != 0;
	}

	@Override
	public int getSamples() {
		return core.getInt(Tag.SamplesPerPixel, 1);
	}
	
	@Override
	public int getNumOfFrames() {
		return core.getInt(Tag.NumberOfFrames, 1);
	}
	
	@Override
	public ImageProcessor getImageProcessor(int frame) {
		if(Codec.isCompressed(getTSUID())) {
			Log.logger.warning("do decompress before getImageProcessor()...");
//			return null;
		}
		byte[] raw = getPixelData(frame);
		if(raw == null) {
			return null;
		}
		PixelDataDecoder pdec = new PixelDataDecoder(this);
		return pdec.decode(raw).getProcessor();
	}
}