package com.vis.dicom;

public interface DicomWriter {
	
	public void write(DicomObject dataset, String tsUID, String dest);
	public void write(DicomObject dataset, String dest, String tsUID, boolean withDcmExtension);
	public void writeDicomImage(DicomObject core, DicomObject fmi, String dest, boolean withDcmExtension); 

}
