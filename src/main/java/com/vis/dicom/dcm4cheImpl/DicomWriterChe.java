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

import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.SafeClose;

import java.io.IOException;

import org.dcm4che3.data.Attributes;

import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomWriter;

public class DicomWriterChe implements DicomWriter{

    private DicomOutputStream stream; // マルチフレーム出力保持用
    private boolean useItems;         // 4GB超え時のItem分割フラグ
    private long expectedLength;      // パディング計算用

	@Override
	public synchronized void write(DicomObject dataset, String tsUID, String dest) {
		write(dataset, tsUID, dest, true);
	}

	@Override
	public synchronized void write(DicomObject dataset, String tsUID, String dest, boolean withDcmExtension) {
		Attributes attr = (Attributes)dataset;
		Attributes fmi = attr.createFileMetaInformation(tsUID);
		DicomOutputStream dos = null;
		try {
			if(withDcmExtension) {
				if (!dest.endsWith(".dcm")) {
					dest = dest + ".dcm";
				}
			}else {
				if (dest.endsWith(".dcm")) {
					dest = dest.substring(0, dest.lastIndexOf(".dcm"));
				}
			}
			
			// ★ Fileを渡すコンストラクタを使うことで、初期状態が安全な FMI 用のモードになる
			dos = new DicomOutputStream(new java.io.File(dest));
			dos.writeDataset(fmi, attr);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			SafeClose.close(dos);
		}
	}

	@Override
	public synchronized void writeDicomImage(DicomObject core, DicomObject fmi, String dest, boolean withDcmExtension) {
		Attributes attr = (Attributes)core;
		
        String tsUid = null;
        if (fmi != null) tsUid = ((Attributes)fmi).getString(org.dcm4che3.data.Tag.TransferSyntaxUID);
        if (tsUid == null) tsUid = attr.getString(org.dcm4che3.data.Tag.TransferSyntaxUID);
        if (tsUid == null) tsUid = org.dcm4che3.data.UID.ExplicitVRLittleEndian;
        
        Attributes safeFmi = attr.createFileMetaInformation(tsUid);
        if (safeFmi == null) {
            safeFmi = new Attributes();
        }
        
        safeFmi.setString(org.dcm4che3.data.Tag.TransferSyntaxUID, org.dcm4che3.data.VR.UI, tsUid);
        if (!safeFmi.contains(org.dcm4che3.data.Tag.MediaStorageSOPClassUID)) {
            safeFmi.setString(org.dcm4che3.data.Tag.MediaStorageSOPClassUID, org.dcm4che3.data.VR.UI, attr.getString(org.dcm4che3.data.Tag.SOPClassUID, "1.2.840.10008.5.1.4.1.1.7"));
        }
        if (!safeFmi.contains(org.dcm4che3.data.Tag.MediaStorageSOPInstanceUID)) {
            safeFmi.setString(org.dcm4che3.data.Tag.MediaStorageSOPInstanceUID, org.dcm4che3.data.VR.UI, attr.getString(org.dcm4che3.data.Tag.SOPInstanceUID, "1.2.276.0.7230010.3.1.4.1"));
        }

		DicomOutputStream dos = null;
		try {
			if(withDcmExtension) {
				if (!dest.endsWith(".dcm")) {
					dest = dest + ".dcm";
				}
			}else {
				if (dest.endsWith(".dcm")) {
					dest = dest.substring(0, dest.lastIndexOf(".dcm"));
				}
			}
			
			// ★ Fileを渡すコンストラクタを使うことで、初期状態が安全な FMI 用のモードになる
			dos = new DicomOutputStream(new java.io.File(dest));
			dos.writeDataset(safeFmi, attr);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			SafeClose.close(dos);
		}
	}

    // =========================================================================
    // マルチフレーム ストリーム書き出し実装
    // =========================================================================

    @Override
    public void openStream(DicomObject core, DicomObject fmi, String dest, String tsUID, 
                           int numFrames, int bitsAllocated, int samples, int width, int height) throws IOException {
        
        Attributes attr = new Attributes((Attributes) core);
        
        String actualTsUid = tsUID != null ? tsUID : org.dcm4che3.data.UID.ExplicitVRLittleEndian;
        Attributes safeFmi = attr.createFileMetaInformation(actualTsUid);

        if (safeFmi == null) {
            safeFmi = new Attributes();
        }
        
        safeFmi.setString(org.dcm4che3.data.Tag.TransferSyntaxUID, org.dcm4che3.data.VR.UI, actualTsUid);
        if (!safeFmi.contains(org.dcm4che3.data.Tag.MediaStorageSOPClassUID)) {
            safeFmi.setString(org.dcm4che3.data.Tag.MediaStorageSOPClassUID, org.dcm4che3.data.VR.UI, attr.getString(org.dcm4che3.data.Tag.SOPClassUID, "1.2.840.10008.5.1.4.1.1.7"));
        }
        if (!safeFmi.contains(org.dcm4che3.data.Tag.MediaStorageSOPInstanceUID)) {
            safeFmi.setString(org.dcm4che3.data.Tag.MediaStorageSOPInstanceUID, org.dcm4che3.data.VR.UI, attr.getString(org.dcm4che3.data.Tag.SOPInstanceUID, "1.2.276.0.7230010.3.1.4.1"));
        }

        attr.remove(org.dcm4che3.data.Tag.PixelData);
        attr.remove(org.dcm4che3.data.Tag.FloatPixelData);
        attr.remove(org.dcm4che3.data.Tag.DoubleFloatPixelData);

        if (!dest.endsWith(".dcm")) {
            dest = dest + ".dcm";
        }

        // ★ DicomOutputStream を File を引数にして初期化！
        // (こうすることで初期状態が FMI書き込み用の ExplicitVR になり、例外が起きません)
        stream = new DicomOutputStream(new java.io.File(dest));

        // writeDataset の中でFMIが書き込まれた直後、safeFmiの中のTSUIDを読み取って
        // 自動的に ImplicitVR などの動画用モードにストリームが切り替わります！
        stream.writeDataset(safeFmi, attr);

        long bytesPerFrame = (long) width * height * samples * (bitsAllocated / 8);
        this.expectedLength = bytesPerFrame * numFrames;
        
        long writeLength = this.expectedLength;
        if (writeLength % 2 != 0) {
            writeLength++; 
        }
        
        org.dcm4che3.data.VR pixelVr = (bitsAllocated > 8) ? org.dcm4che3.data.VR.OW : org.dcm4che3.data.VR.OB;
        int pixelTag = org.dcm4che3.data.Tag.PixelData;
        if (bitsAllocated == 32 && samples == 1) {
            pixelVr = org.dcm4che3.data.VR.OF;
            pixelTag = org.dcm4che3.data.Tag.FloatPixelData;
        } else if (bitsAllocated == 64 && samples == 1) {
            pixelVr = org.dcm4che3.data.VR.OD;
            pixelTag = org.dcm4che3.data.Tag.DoubleFloatPixelData;
        }

        long MAX_DICOM_LENGTH = 0xFFFFFFFEL; 
        this.useItems = this.expectedLength > MAX_DICOM_LENGTH;

        if (this.useItems) {
            stream.writeHeader(pixelTag, pixelVr, -1);
            stream.writeHeader(org.dcm4che3.data.Tag.Item, null, 0);
        } else {
            stream.writeHeader(pixelTag, pixelVr, (int) writeLength);
        }
    }

    @Override
    public void writeFrame(byte[] frameBytes) throws IOException {
        if (stream != null) {
            if (useItems) {
                int frameLen = frameBytes.length;
                boolean pad = (frameLen % 2 != 0);
                stream.writeHeader(org.dcm4che3.data.Tag.Item, null, frameLen + (pad ? 1 : 0));
                stream.write(frameBytes);
                if (pad) {
                    stream.write(new byte[]{0}); 
                }
            } else {
                stream.write(frameBytes);
            }
        }
    }

    @Override
    public void closeStream() throws IOException {
        if (stream != null) {
            if (useItems) {
                stream.writeHeader(org.dcm4che3.data.Tag.SequenceDelimitationItem, null, 0);
            } else {
                if (this.expectedLength % 2 != 0) {
                    stream.write(new byte[]{0});
                }
            }
            stream.close();
            stream = null;
            useItems = false;
            expectedLength = 0;
        }
    }
}