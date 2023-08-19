package com.vis.dicom.dcm4cheImpl;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomStreamException;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.SafeClose;

import com.vis.core.log.Log;
import com.vis.dicom.DicomObject;
import com.vis.dicom.DicomReader;
import com.vis.dicom.DicomUtilities;
import com.vis.dicom.Tag;
import com.vis.dicom.TransferSyntaxType;

import java.util.logging.*;

public class DicomReaderChe implements DicomReader{
	
	Logger logger = Log.logger;
	
	DicomObject dataset4che = null;
	DicomObject fmi4che = null;
	com.vis.dicom.UID tsuid;
	com.vis.dicom.UID sopUID;
	com.vis.dicom.TransferSyntaxType tstype4che;
	boolean bigEndian = false;
	
	public DicomReaderChe() {}
	
	public DicomReaderChe(String path, boolean withPixel) {
		read(path, withPixel);
	}

	@Override
	public void read(String path) {
		read(path, true);
	}

	@Override
	public void read(URI path) {
		read(new File(path).getAbsolutePath(), true);
	}

	@Override
	public void read(String path, boolean withPixel) {
		if(!DicomUtilities.isDicomFile(new File(path))) {
			return;
		}
		DicomInputStream dis = null;
		try {
			dis = new DicomInputStream(new File(path));
			dis.setIncludeBulkData(IncludeBulkData.URI);
			Attributes fmi4che = dis.readFileMetaInformation();
			tsuid = com.vis.dicom.UID.uidOf(dis.getTransferSyntax());
			tstype4che = TransferSyntaxType.forUID(tsuid.uid());
			this.bigEndian = dis.bigEndian();
			Attributes dataset4che = null;
			if (!withPixel) {
				dataset4che = dis.readDatasetUntilPixelData();
			} else {//read full
				dataset4che = dis.readDataset(-1, o -> false);
			}
			this.dataset4che = new DicomObjectChe(dataset4che);
			this.fmi4che = new DicomObjectChe(fmi4che);
			this.sopUID = com.vis.dicom.UID.uidOf(this.fmi4che.getString(Tag.SOP​Class​UID));
		}catch(DicomStreamException dse) {
			logger.severe("Reading dicom file...:getDicomAttribute\n"+dse.getMessage());
			return;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Reading dicom file...:getDicomAttribute\n"+e.getMessage());
			return;
		}finally {
			SafeClose.close(dis);
		}
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
		uids[0] = dataset4che.getString(Tag.Patient​ID);
		uids[1] = dataset4che.getString(Tag.Study​Instance​UID);
		uids[2] = dataset4che.getString(Tag.Series​Instance​UID);
		uids[3] = dataset4che.getString(Tag.SOP​Instance​UID);
		return uids;
	}
	
	public com.vis.dicom.UID checkTSUID() {
		return tsuid;
	}
	
	public com.vis.dicom.TransferSyntaxType checkTSType() {
		if(tstype4che == null) {
			return null;
		}
		return tstype4che;//if unknown, maybe, not image obj.
	}

	@Override
	public com.vis.dicom.UID checkSopClassUID() {
		return this.sopUID;
	}

	@Override
	public boolean bigEndian() {
		return this.bigEndian;
	}

}
