package com.vis.dicom;

import java.util.Date;

//import com.vis.dicom.dcm4che.DicomObjectDcm4che;

/**
 * 
 * Wrapper object for mutiple dicom libraries
 * If you need imageplus, use GImageReader instead.
 * 
 * @author tatsunidas
 *
 */
public class DicomObject{// implements AbstractDicomObject{
	
	String backend = "dcm4che";
//	DicomObjectDcm4che dcmche = null;
	Object dcmtk = null;//TODO
	
//	public DicomObject() {
//		if (backend.equals("dcm4che")) {
////			dcmche = new com.vis.dicom.dcm4che.DicomObjectDcm4che();
//		}else {
//			//dcmtk
//		}
//	}
//	
//	public DicomObject(String path, boolean withPixel) {
//		if (backend.equals("dcm4che")) {
////			dcmche = new com.vis.dicom.dcm4che.DicomObjectDcm4che(path,withPixel);
//		}else {
//			//dcmtk
//		}
//	}
//	
//	public String whatIsBackend() {
//		return backend;
//	}
//	
//	public Object getCore() {
//		if(backend.equals("dcm4che")) {
//			return dcmche;
//		}else {
//			return null;
//		}
//	}
//	
//	public void setCore(Object coreDcmObj) {
//		if(backend.equals("dcm4che")) {
//			if(coreDcmObj instanceof DicomObjectDcm4che) {
//				DicomObjectDcm4che core = (DicomObjectDcm4che) coreDcmObj;
//				this.dcmche = core;
//			}
//		}else {
//			//TODO
//		}
//	}
//	
//	public void updateFileMetaInfo() {
//		if(backend.equals("dcm4che")) {
//			this.dcmche.updateFileMetaInformation();
//		}else {
//			//TODO
//		}
//	}
//
//	@Override
//	public String getString(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getString(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	public String getString(int tag, String defaultVal) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getString(tag, defaultVal);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	@Override
//	public String[] getStrings(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getStrings(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//
//	@Override
//	public Integer getInt(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getInt(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	@Override
//	public Integer getInt(int tag, int padding) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getInt(tag, padding);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	@Override
//	public double getDouble(int tag, int padding) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getDouble(tag, padding);
//		}else {
//			//dcmtk
//		}
//		return Double.MAX_VALUE;//error value
//	}
//	
//	@Override
//	public double[] getDoubles(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getDoubles(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//
//	@Override
//	public Date getDate(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getDate(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	@Override
//	public byte[] getBytes(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getBytes(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//
//	@Override
//	public Object getValue(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getValue(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//
//	@Override
//	public Object getNestedDataset(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getNestedDataset(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//
//	@Override
//	public boolean contains(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.contains(tag);
//		}else {
//			//dcmtk
//		}
//		return false;
//	}
//
//	@Override
//	public int[] tags() {
//		if (backend.equals("dcm4che")) {
//			return dcmche.tags();
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//
//	@Override
//	public void setString(int tag, String vr_dtype, String val) {
//		if (backend.equals("dcm4che")) {
//			dcmche.setString(tag, vr_dtype, val);
//		}else {
//			//dcmtk
//		}
//	}
//	
//	public void setString(int tag, String vr_dtype, String... val) {
//		if (backend.equals("dcm4che")) {
//			dcmche.setString(tag, vr_dtype, val);
//		}else {
//			//dcmtk
//		}
//	}
//	
//	public void setDate(int tag, String vr_dtype, java.util.Date date) {
//		if (backend.equals("dcm4che")) {
//			dcmche.setDate(tag, vr_dtype, date);
//		}else {
//			//dcmtk
//		}
//	}
//	
//	@Override
//	public void setBytes(int tag, String vr_dtype, byte[] val) {
//		if (backend.equals("dcm4che")) {
//			dcmche.setBytes(tag, vr_dtype, val);
//		}else {
//			//dcmtk
//		}
//	}
//
//	@Override
//	public void setInt(int tag, String vr_dtype, int... val) {
//		if (backend.equals("dcm4che")) {
//			dcmche.setInt(tag, vr_dtype, val);
//		}else {
//			//dcmtk
//		}
//	}
//	
//	public String getTransferSyntaxUID() {
//		if (backend.equals("dcm4che")) {
//			return dcmche.getTransferSyntaxUID();
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	public String privateCreatorOf(int tag) {
//		if (backend.equals("dcm4che")) {
//			return dcmche.privateCreatorOf(tag);
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	public boolean isMultiFrame() {
//		boolean multiframe = false;
//		if (backend.equals("dcm4che")) {
//			multiframe = dcmche.isMultiFrame();
//		}else {
//			//dcmtk
//		}
//		return multiframe;
//	}
//
//	public boolean isPDF() {
//		boolean isPDF = false;
//		if (backend.equals("dcm4che")) {
//			isPDF = dcmche.isPDF();
//		}else {
//			//dcmtk
//		}
//		return isPDF;
//	}
//	
//	public DicomObject duplicate() {
//		if (backend.equals("dcm4che")) {
//			DicomObjectDcm4che dcmche_dup = dcmche.duplicate();
//			DicomObject dcm = new DicomObject();
//			dcm.setCore(dcmche_dup);
//			return dcm;
//		}else {
//			//dcmtk
//		}
//		return null;
//	}
//	
//	public void write(String dest) {
//		DicomWriter.write(this, dest, true);
//	}
}
