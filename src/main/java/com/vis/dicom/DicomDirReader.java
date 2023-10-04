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

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import com.vis.core.log.Log;
import com.vis.dicom.dcm4cheImpl.DicomDirReaderChe;

public interface DicomDirReader {
	
	public static com.vis.dicom.DicomDirReader newDicomDirReader(File f) {
		DICOMBackend current = DICOMBackend.getCurrent();
		if(current == DICOMBackend.DCM4CHE) {
			try {
				return (com.vis.dicom.DicomDirReader)new DicomDirReaderChe(f);
			} catch (IOException e) {
				e.printStackTrace();
				Log.logger.log(Level.WARNING, "Can not read dicomdir file ! return null.");
				return null;
			}
		}else if(current == DICOMBackend.DCMTK) {
			
		}
		return null;
	}

	public File getFile();
	public DicomObject findFirstRootDirectoryRecord(boolean ignorePrivate);
	public DicomObject findLowerDirectoryRecord(DicomObject parent, boolean ignorePrivate);
	public DicomObject findNextDirectoryRecord(DicomObject parent, boolean ignorePrivate);
	
}
