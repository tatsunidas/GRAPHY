package com.vis.dicom;

import com.vis.dicom.dcm4cheImpl.DicomWriterChe;

public interface DicomWriter {
	
	public static DicomWriter newDicomWriter() {
		return newDicomWriter(null);
	}
	
	public static DicomWriter newDicomWriter(DICOMBackend backend) {
		if(backend == null || backend == DICOMBackend.DCM4CHE) {
			return (DicomWriter) new DicomWriterChe();
		}
//		else if(backend == DICOMBackend.DCMTK){
//			//TODO
//		}
		return null;
	}
	
	public void write(DicomObject dataset, String tsUID, String dest);
	public void write(DicomObject dataset, String dest, String tsUID, boolean withDcmExtension);
	public void writeDicomImage(DicomObject core, DicomObject fmi, String dest, boolean withDcmExtension); 

}
