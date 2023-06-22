package com.vis.db;

import com.vis.dicom.DICOMBackend;

/**
 * DCMQRSCP API
 * @author tatsunidas
 *
 */
public interface DicomServer {
	
	public boolean start(String[] args);
	
	public void stop();
	
	public DICOMBackend backendCheck(DICOMBackend backend);

}
