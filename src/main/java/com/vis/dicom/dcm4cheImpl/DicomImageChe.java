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

import java.io.IOException;

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
	/**
	 * header, basically without pixels.
	 * After calling ensurePixelLoaded(),
	 * header may have pixels bulk.
	 */
	DicomObject header = null;
	DicomObject fmi = null;
	UID tsuid;
	boolean decompressed = false;
	final String filePath;
	
	public DicomImageChe(String path, DicomObject header, DicomObject fmi, UID tsUID) {
		this.filePath = path;//null-able
		if(header == null ) {
			return;
		}
		if(fmi == null) {
			fmi = (DicomObject)header.createFileMetaInformation(tsUID.uid());
		}
		this.header = header;
		this.fmi = fmi;
		this.tsuid = tsUID;
	}
	
	public DicomImageChe(String path, boolean withPixel) {
		DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
		reader.read(path, withPixel);
		this.filePath = path;//null-able
		this.header = reader.getHeader();
		this.fmi = reader.getFileMetaInfomation();
		this.tsuid = reader.checkTSUID();
		reader = null;
	}

	@Override
	public void decompressed(boolean decompressed) {
		this.decompressed = decompressed;
	}

	@Override
	public int getBitsAllocated() {
		return header.getInt(Tag.BitsAllocated, -1);
	}

	@Override
	public int getBitsStored() {
		return header.getInt(Tag.BitsStored, getBitsAllocated());
	}

	@Override
	public DicomObject getHeader() {
		return header;
	}

	@Override
	public DicomObject getFileMetaInfo() {
		return fmi;
	}

	@Override
	public int getHeight() {
		return header.getInt(Tag.Rows, 0);
	}

	/**
	 * return imageprocessor (without calibration)
	 * if you want calibrated imageplus, see also ImagePlusDicomTagTools.dcmImgToImagePlus. 
	 * 0 to N-1
	 */
	@Override
	public ImageProcessor getImageProcessor(int frame) {
		if(Codec.isCompressed(getTSUID())) {
			if(!isDecompressed()) {
				Log.logger.warning("do decompress before getImageProcessor()...");
				return null;
			}
		}
		byte[] raw = getPixelData(frame);
		if(raw == null) {
			return null;
		}
		PixelDataDecoder pdec = new PixelDataDecoder(this);
		return pdec.decode(raw);
	}
	
	@Override
	public int getNumOfFrames() {
		return header.getInt(Tag.NumberOfFrames, 1);
	}
	
	@Override
	public PhotometricInterpretation getPhotometricInterpletation() {
		return PhotometricInterpretation
				.fromString(header.getString(Tag.PhotometricInterpretation, "MONOCHROME2"));
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
		
		if(frame < 0 || frame > getNumOfFrames()) {
			return null;
		}
		
		int bitsAllocated = getBitsAllocated();
		int samples = getSamples();
		int w = getWidth();
		int h = getHeight();
		//byte array length in single frame.
		int length = w*h*samples*bitsAllocated/8;
		Object bulk = null;
		byte[] pixels = null;
		
		if(bitsAllocated == 32 && samples == 1) {
			bulk = this.header.getValue(Tag.FloatPixelData);
			if(bulk == null) {
				this.header.getValue(Tag.PixelData);
			}
		}else if(bitsAllocated == 64 && samples == 1) {
			bulk = this.header.getValue(Tag.DoubleFloatPixelData);
			if(bulk == null) {
				this.header.getValue(Tag.PixelData);
			}
		}else {
			bulk = this.header.getValue(Tag.PixelData);
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
			if(isMultiFrame()) {
				byte[] dest = new byte[length];
				System.arraycopy(pixels, frame * length, dest, 0, length);
				pixels = dest;
			}
		}else if(bulk instanceof BulkData) {
			BulkData bd = (BulkData)bulk;
			try {
				pixels = bd.toBytes(((Attributes)this.header).getVR(Tag.PixelData), bd.bigEndian());
				if(isMultiFrame()) {
					byte[] dest = new byte[length];
					System.arraycopy(pixels, frame * length, dest, 0, length);
					pixels = dest;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else if(bulk instanceof org.dcm4che3.data.Value) {
			//decompressed pixel
			org.dcm4che3.data.Value decom_val = (org.dcm4che3.data.Value)bulk;
			try {
				pixels = decom_val.toBytes(org.dcm4che3.data.VR.OW, bigEndian());
				if(isMultiFrame()) {
					byte[] dest = new byte[length];
					System.arraycopy(pixels, frame * length, dest, 0, length);
					pixels = dest;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return pixels;
	}

	@Override
	public int getPixel​Representation() {
		return header.getInt(Tag.PixelRepresentation, -1);
	}

	@Override
	public int getSamples() {
		return header.getInt(Tag.SamplesPerPixel, 1);
	}

	@Override
	public UID getSopClassUID() {
		String sopClassUID = fmi.getString(Tag.SOPClassUID);
		if(sopClassUID == null) {
			return null;
		}
		return UID.valueOf(sopClassUID);
	}

	@Override
	public UID getTSUID() {
		return tsuid;
	}

	@Override
	public int getWidth() {
		return header.getInt(Tag.Columns, 0);
	}
	
	@Override
	public boolean isBanded() {
		return header.getInt(Tag.PlanarConfiguration, 0) != 0;
	}

	@Override
	public boolean isColor() {
		return getSamples() == 3;
	}

	@Override
	public boolean isDecompressed() {
		return decompressed;
	}

	@Override
	public boolean isMultiFrame() {
		int frames = this.header.getInt(Tag.NumberOfFrames, 1);
		return frames == 1 ? false:true;
	}

	@Override
	public boolean isPDF() {
		return getSopClassUID() == UID.EncapsulatedPDFStorage;
	}

	@Override
	public boolean isSigned() {
		return header.getInt(Tag.PixelRepresentation, 0) != 0;
	}
	
	@Override
	public boolean ensurePixelDataLoaded() {
		if(this.filePath != null) {
			// read from file dicom image
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
			reader.read(filePath, true/*load pixel bulk*/);
			DicomObject full = reader.getHeader();
			if(full != null) {
				setHeader(full);
				return true;
			}
		}else {
			// building-up dicom image
			int samples = getSamples();
			int bitsAllocated = getBitsAllocated();
			// load from original bitsAllocated
			if(bitsAllocated == 32 && samples == 1) {
				return header.getValue(Tag.FloatPixelData) != null;
			}else if(bitsAllocated == 64 && samples == 1) {
				return  header.getValue(Tag.DoubleFloatPixelData) != null;
			}else {
				return header.getValue(Tag.PixelData) != null;
			}
		}
		return false;
	}
	
	@Override
	public void releasePixelBulkFromHeader() {
		if (this.filePath != null) {
			// read from file dicom image
			DicomReader reader = DicomReader.newDicomReader(DICOMBackend.DCM4CHE);
			reader.read(filePath, false/* load pixel bulk */);
			DicomObject header = reader.getHeader();
			if (header != null) {
				this.header = null;
				setHeader(header);
			}
		}
	}

	@Override
	public void setHeader(DicomObject attr) {
		this.header = attr;
		updateFileMetaInfo(tsuid);
	}
	
	@Override
	public void setFileMetaInfo(DicomObject fmi) {
		this.fmi = fmi;
	}
	
	@Override
	public void setPixelData(int frame/*0 base*/, int w, int h, int samples, int bitsPerPixelSample, Object pixels) {
		
		byte[] pixelsByte = null;
		if(pixels instanceof byte[]) {
			pixelsByte = (byte[])pixels;
		}else if(pixels instanceof short[]) {
			short[] pixels_ = (short[])pixels;
			pixelsByte = ByteUtils.shortToBytes(pixels_);
		}else if(pixels instanceof float[]) {
			float[] pixels_ = (float[])pixels;
			pixelsByte = ByteUtils.floatToBytes(pixels_);
		}else if(pixels instanceof double[]) {
			double[] pixels_ = (double[])pixels;
			pixelsByte = ByteUtils.doubleToBytes(pixels_);
		}else if(pixels instanceof int[] && samples == 3) {//RGB
			int[] pixels_ = (int[])pixels;
			pixelsByte = ByteUtils.intToBytes(pixels_, true/*ignore alpha*/);
		}
		
		if(frame < 0 || frame > getNumOfFrames()) {
			throw new IllegalArgumentException("num of frames is invalid...");
		}
		if(w != getWidth() || h != getHeight() || samples != getSamples()) {
			throw new IllegalArgumentException("num of pixels does not match...");
		}
		
		int bitsAllocated = getBitsAllocated();
		
		Object bulk = null;
		// load from original bitsAllocated
		if(bitsAllocated == 32 && samples == 1) {
			bulk = header.getValue(Tag.FloatPixelData);
		}else if(bitsAllocated == 64 && samples == 1) {
			bulk = header.getValue(Tag.DoubleFloatPixelData);
		}else {
			bulk = header.getValue(Tag.PixelData);
		}

		if (bulk instanceof Fragments) {
			Fragments frags = (Fragments) bulk;
			Object frag = frags.get(frame + 1);// frame number count from 1
			if (frag instanceof byte[]) {
				frags.set(frame + 1, pixels);
			}
		} else if (bulk instanceof byte[] || bulk == null/* from scratch */) {
			if (bitsAllocated == 32 && samples == 1) {
				header.setBytes(Tag.FloatPixelData, VR.OF, pixelsByte);

				BufferedImageUtils.toImagePixelModule(samples, getPhotometricInterpletation().name(), getHeight(),
						getWidth(), pixelsByte, header);

			} else if (bitsAllocated == 64 && samples == 1) {
				header.setBytes(Tag.DoubleFloatPixelData, VR.OD, pixelsByte);
			} else if (bitsAllocated > 8 && bitsAllocated <= 16) {
				header.setBytes(Tag.PixelData, VR.OW, pixelsByte);
			} else {
				header.setBytes(Tag.PixelData, VR.OB, pixelsByte);
			}
		}
	}
	
	@Override
	public void updateFileMetaInfo(com.vis.dicom.UID tsuid) {
		this.fmi = (DicomObject) header.createFileMetaInformation(tsuid.uid());
		this.tsuid = tsuid;
	}
}