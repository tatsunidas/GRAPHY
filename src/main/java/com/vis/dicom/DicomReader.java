package com.vis.dicom;

import java.net.URI;
import com.vis.dicom.dcm4cheImpl.*;

public interface DicomReader {
	
	public static DicomReader newDicomReader(DICOMBackend backend) {
		if(backend == DICOMBackend.DCM4CHE) {
			return (DicomReader) new DicomReaderChe();
		}else if(backend == DICOMBackend.DCMTK){
			//TODO
		}
		return null;
	}

	DicomObject read(String path);
	DicomObject read(URI path);
	DicomObject read(String path, boolean withPixel);
	
}
