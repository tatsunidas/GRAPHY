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
import com.vis.dicom.Tag;
import com.vis.dicom.VR;

public class DicomWriterChe implements DicomWriter{

	@Override
	public synchronized void write(DicomObject dataset, String tsUID, String dest) {
		write(dataset, tsUID, dest, true);
	}

	@Override
	public synchronized void write(DicomObject dataset, String tsUID, String dest, boolean withDcmExtension) {
		dataset.setString(Tag.Manufacturer, VR.LO, "Visionary Imaging Services, Inc.");
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
			dos = new DicomOutputStream(new java.io.File(dest));
			dos.writeDataset(fmi, attr);//means writeFileMetaInformation()+writeTo()
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			SafeClose.close(dos);
		}
	}

	@Override
	public synchronized void writeDicomImage(DicomObject core, DicomObject fmi, String dest, boolean withDcmExtension) {
		core.setString(Tag.Manufacturer, VR.LO, "Visionary Imaging Services, Inc.");
		Attributes attr = (Attributes)core;
		Attributes fmi_ = (Attributes)fmi;
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
			dos = new DicomOutputStream(new java.io.File(dest));
			dos.writeDataset(fmi_, attr);//means writeFileMetaInformation()+writeTo()
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			SafeClose.close(dos);
		}
	}

}
