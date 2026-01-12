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
package com.vis.imageio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.ByteUtils;

import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DicomReader;
import com.vis.dicom.dcm4cheImpl.DecompressorChe;
import com.vis.dicom.dcm4cheImpl.DicomImageChe;
import com.vis.dicom.dcm4cheImpl.DicomObjectChe;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

public class TestDecompression {

	public static void main(String[] args) {
		
		String p = "/home/tatsunidas/graphy-workspace3/graphy/src/test/resources/dicom_samples/JIRA_DICOM/CR_JPG_IR87a.dcm";
		DicomReader reader = DicomReader.newDicomReader(DICOMBackend.getCurrent());
		reader.read(p, true);
		DicomObjectChe dcm = (DicomObjectChe) reader.getHeader();
		DecompressorChe decom = new DecompressorChe(dcm, reader.getFileMetaInfomation().getString(Tag.TransferSyntaxUID));
		decom.decompress();
		Object bulk = dcm.getValue(Tag.PixelData);
		System.out.println(bulk instanceof org.dcm4che3.data.Value);
		System.out.println(decom.dataset.getValue(Tag.PixelData).getClass().getName());
		int w = dcm.getInt(Tag.Columns, 0);
		int h = dcm.getInt(Tag.Rows, 0);
		try {
			byte[] pix = ((org.dcm4che3.data.Value)bulk).toBytes(VR.OB, false);
			System.out.println("w: "+w+", h: "+h);
			System.out.println(w*h);
			System.out.println(pix.length);
			short[] sp = new short[pix.length/2];
			com.vis.core.util.ByteUtils.bytesToShorts(pix,sp,0, sp.length,false);
//			System.out.println(pix.length);
			new ImagePlus("", new ShortProcessor(w,h,sp,null)).show();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	static short[] bytesToShortArray(byte[] pix) {
		short[] s = new short[pix.length/2];
		int ind = 0;
		for(int i=0; i<pix.length; i+=2) {
			ByteBuffer bb = ByteBuffer.allocate(2);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			bb.put(pix[i]);
			bb.put(pix[i+1]);
			short shortVal = bb.getShort(0);
			s[ind++] = shortVal;
		}
		return s;
	}
	
	static byte[] byteInterleave(byte[] array){
		byte[] b_ = new byte[array.length/2];
		int j = 0;
		for(int i=0; i<array.length; i+=2) {
			b_[j++] = array[i];
		}
		return b_;
	}

}
