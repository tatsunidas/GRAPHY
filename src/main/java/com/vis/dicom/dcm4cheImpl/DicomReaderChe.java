package com.vis.dicom.dcm4cheImpl;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomStreamException;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.SafeClose;

import com.vis.core.log.Log;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.TransferSyntaxType;

import java.util.logging.*;

public class DicomReaderChe implements DicomReader{
	
	Logger logger = Log.logger;
	
	DicomObject dataset4che = null;
	DicomObject fmi4che = null;
	String tsuid;
	com.vis.dicom.TransferSyntaxType tstype4che;
	
	public DicomReaderChe() {}
	
	public DicomReaderChe(String path, boolean withPixel) {
		read(path, withPixel);
	}

	@Override
	public DicomObject read(String path) {
		return read(path, true);
	}

	@Override
	public DicomObject read(URI path) {
		return read(new File(path).getAbsolutePath(), true);
	}

	@Override
	public DicomObject read(String path, boolean withPixel) {
		if(!DicomUtilities.isDicomFile(new File(path))) {
			return null;
		}
		DicomInputStream dis = null;
		try {
			dis = new DicomInputStream(new File(path));
			dis.setIncludeBulkData(IncludeBulkData.URI);
			Attributes fmi4che = dis.readFileMetaInformation();
			tsuid = dis.getTransferSyntax();
			tstype4che = TransferSyntaxType.forUID(tsuid);
			Attributes dataset4che = null;
			if (!withPixel) {
				dataset4che = dis.readDatasetUntilPixelData();
			} else {//read full
				dataset4che = dis.readDataset(-1, o -> false);
			}
			this.dataset4che = new DicomObjectChe(dataset4che);
			this.fmi4che = new DicomObjectChe(fmi4che);
		}catch(DicomStreamException dse) {
			logger.severe("Reading dicom file...:getDicomAttribute\n"+dse.getMessage());
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Reading dicom file...:getDicomAttribute\n"+e.getMessage());
			return null;
		}finally {
			SafeClose.close(dis);
		}
		return null;
	}
	
	@Override
	public DicomObject getCore() {
		return dataset4che;
	}
	
	@Override
	public DicomObject getFileMetaInfomation() {
		return fmi4che;
	}
	
	public Object[] getFmiAndCore() {
		return new Object[] {getFileMetaInfomation(),getCore()};
	}
	
	public String[] checkUIDs() {
		if(dataset4che == null) {
			return null;
		}
		String[] uids = new String[4];
		uids[0] = dataset4che.getString(Tag.PatientID);
		uids[1] = dataset4che.getString(Tag.StudyInstanceUID);
		uids[2] = dataset4che.getString(Tag.SeriesInstanceUID);
		uids[3] = dataset4che.getString(Tag.SOPInstanceUID);
		return uids;
	}
	
	public String checkTSUID() {
		return tsuid;
	}
	
	public String checkTSType() {
		if(tstype4che == null) {
			return null;
		}
		return tstype4che.name();//if unknown, maybe, not image obj.
	}

	
}
