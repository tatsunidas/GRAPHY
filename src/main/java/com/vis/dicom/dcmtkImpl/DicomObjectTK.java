package com.vis.dicom.dcmtkImpl;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DatePrecision;
import com.vis.dicom.DatePrecisions;
import com.vis.dicom.DateRange;
import com.vis.dicom.DicomObject;
import com.vis.dicom.ItemPointer;
import com.vis.dicom.SpecificCharacterSet;
import com.vis.dicom.VR;

/**
 * Sample/Example class for DCMTK.
 * you may set extends *** to handle dcmObj in DCMTK.
 * 
 * @author tatsunidas
 *
 */

public class DicomObjectTK /*extends dcmtkdcmObj*/ implements DicomObject{

	@Override
	public DICOMBackend whatIsBackend() {
		return DICOMBackend.DCMTK;
	}

	@Override
	public Object getCore() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setCore(Object attr) {
		// TODO Auto-generated method stub
	}

	@Override
	public Object getFileMetaInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setFileMetaInfo(Object fmi) {
		// TODO Auto-generated method stub
	}

	@Override
	public void updateFileMetaInfo() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getString(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getStrings(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

//	@Override
//	public Integer getInt(int tag) {
//		// TODO Auto-generated method stub
//		return null;
//	}

	@Override
	public int getInt(int tag, int padding) {
		// TODO Auto-generated method stub
		return -1;
	}

//	@Override
//	public double getDouble(int tag, int padding) {
//		// TODO Auto-generated method stub
//		return 0;
//	}

	@Override
	public double[] getDoubles(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] getBytes(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getValue(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean contains(int tag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int[] tags() {
		// TODO Auto-generated method stub
		return null;
	}

//	@Override
//	public Object getNestedDataset(int tag) {
//		// TODO Auto-generated method stub
//		return null;
//	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isReadOnly() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setReadOnly() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Map<String, Object> getProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setProperties(Map<String, Object> properties) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object getProperty(String key, Object defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setProperty(String key, Object value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object clearProperty(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getParentSequencePrivateCreator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getParentSequenceTag() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ItemPointer[] itemPointers() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int itemIndex() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void trimToSize() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void trimToSize(boolean recursive) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void internalizeStringValues(boolean decode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DicomObject getNestedDataset(int sequenceTag, int itemIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DicomObject getNestedDataset(String privateCreator, int sequenceTag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DicomObject getNestedDataset(String privateCreator, int sequenceTag, int itemIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DicomObject getNestedDataset(ItemPointer... itemPointers) {
		// TODO Auto-generated method stub
		return null;
	}

	//List<com.vis.dicom.ItemPointer>
	public DicomObject getNestedDataset(Object itemPointers) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DicomObject getFunctionGroup(int sequenceTag, int frameIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int tagOf(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public SpecificCharacterSet getSpecificCharacterSet(Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean contains(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean containsValue(int tag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean containsValue(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean containsTagInRange(int firstTag, int lastTag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String privateCreatorOf(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getValue(int tag, Object vr_holde) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getValue(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getValue(String privateCreator, int tag, Object vr_holder) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public VR getVR(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public VR getVR(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getSequence(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getSequence(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] getBytes(String privateCreator, int tag) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] getSafeBytes(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] getSafeBytes(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(int tag, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(int tag, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(int tag, int valueIndex, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getString(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getString(String privateCreator, int tag, Object vr, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, int valueIndex, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getString(String privateCreator, int tag, Object vr, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getString(String privateCreator, int tag, Object vr, int valueIndex, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getStrings(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getStrings(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getInt(int tag, int valueIndex, int defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(String privateCreator, int tag, int defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(String privateCreator, int tag, Object vr, int defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(String privateCreator, int tag, int valueIndex, int defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(String privateCreator, int tag, Object vr, int valueIndex, int defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int[] getInts(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] getInts(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int[] getInts(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getLong(int tag, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(int tag, int valueIndex, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(String privateCreator, int tag, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(String privateCreator, int tag, Object vr, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(String privateCreator, int tag, int valueIndex, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(String privateCreator, int tag, Object vr, int valueIndex, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long[] getLongs(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long[] getLongs(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long[] getLongs(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public float getFloat(int tag, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(int tag, int valueIndex, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(String privateCreator, int tag, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(String privateCreator, int tag, Object vr, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(String privateCreator, int tag, int valueIndex, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(String privateCreator, int tag, Object vr, int valueIndex, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float[] getFloats(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public float[] getFloats(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public float[] getFloats(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getDouble(int tag, double defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble(int tag, int valueIndex, double defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble(String privateCreator, int tag, double defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble(String privateCreator, int tag, Object vr, double defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble(String privateCreator, int tag, int valueIndex, double defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble(String privateCreator, int tag, Object vr, int valueIndex, double defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double[] getDoubles(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double[] getDoubles(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, Object precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, Date defVal, Object precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, int valueIndex, Object precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, int valueIndex, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, int valueIndex, Date defVal, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, Date defVal, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, Date defVal, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex, Date defVal, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, Date defVal, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(long tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(long tag, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(long tag, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(long tag, Date defVal, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, long tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, long tag, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, long tag, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, long tag, Date defVal, DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(int tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, VR vr, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(long tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(long tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, long tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, long tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(int tag, DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, int tag, DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, int tag, VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, int tag, VR vr, DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(long tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(long tag, DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, long tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, long tag, DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setSpecificCharacterSet(String... codes) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SpecificCharacterSet getSpecificCharacterSet() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDefaultTimeZone(TimeZone tz) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public TimeZone getDefaultTimeZone() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TimeZone getTimeZone() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setTimezoneOffsetFromUTC(String utcOffset) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setTimezone(TimeZone tz) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getPrivateCreator(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object remove(int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object remove(String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setNull(int tag, VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setNull(String privateCreator, int tag, VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setBytes(int tag, VR vr, byte[] b) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setBytes(String privateCreator, int tag, VR vr, byte[] b) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(int tag, VR vr, String s) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(String privateCreator, int tag, VR vr, String s) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(int tag, VR vr, String... ss) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(String privateCreator, int tag, VR vr, String... ss) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setInt(int tag, VR vr, int... is) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setInt(String privateCreator, int tag, VR vr, int... is) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setLong(int tag, VR vr, long... ls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setLong(String privateCreator, int tag, VR vr, long... ls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setFloat(int tag, VR vr, float... fs) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setFloat(String privateCreator, int tag, VR vr, float... fs) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDouble(int tag, VR vr, double... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDouble(String privateCreator, int tag, VR vr, double... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(int tag, VR vr, Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(int tag, VR vr, DatePrecision precision, Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(String privateCreator, int tag, VR vr, Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(String privateCreator, int tag, VR vr, DatePrecision precision, Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDate(long tag, Date dt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDate(long tag, DatePrecision precision, Date dt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDate(String privateCreator, long tag, Date dt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDate(String privateCreator, long tag, DatePrecision precision, Date dt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object setDateRange(int tag, VR vr, DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDateRange(int tag, VR vr, DatePrecision precision, DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDateRange(String privateCreator, int tag, VR vr, DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDateRange(String privateCreator, int tag, VR vr, DatePrecision precision, DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDateRange(long tag, DateRange range) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDateRange(String privateCreator, long tag, DateRange range) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object setValue(int tag, VR vr, Object value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setValue(String privateCreator, int tag, VR vr, Object value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object newSequence(int tag, int initialCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object newSequence(String privateCreator, int tag, int initialCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object ensureSequence(int tag, int initialCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object ensureSequence(String privateCreator, int tag, int initialCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object newFragments(int tag, VR vr, int initialCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object newFragments(String privateCreator, int tag, VR vr, int initialCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean addAll(DicomObject other) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAll(DicomObject other, boolean mergeOriginalAttributesSequence) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, DicomObject selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, int[] selection, int fromIndex, int toIndex) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addNotSelected(DicomObject other, int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addNotSelected(DicomObject other, int[] selection, int fromIndex, int toIndex) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean update(UpdatePolicy updatePolicy, DicomObject newAttrs, DicomObject modified) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean update(UpdatePolicy updatePolicy, boolean mergeOriginalAttributesSequence, DicomObject newAttrs,
			DicomObject modified) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean testUpdate(UpdatePolicy updatePolicy, DicomObject newAttrs, DicomObject modified) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateSelected(UpdatePolicy updatePolicy, DicomObject newAttrs, DicomObject modified,
			int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean testUpdateSelected(UpdatePolicy updatePolicy, DicomObject newAttrs, DicomObject modified,
			int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateNotSelected(UpdatePolicy updatePolicy, DicomObject newAttrs, DicomObject modified,
			int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean testUpdateNotSelected(UpdatePolicy updatePolicy, DicomObject newAttrs, DicomObject modified,
			int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public DicomObject addOriginalAttributes(String sourceOfPreviousValues, Date modificationDateTime,
			String reasonForModification, String modifyingSystem, DicomObject originalAttributes) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean equalValues(DicomObject other, int tag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean equalValues(DicomObject other, String privateCreator, int tag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String toString(int limit, int maxWidth) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public StringBuilder toStringBuilder(StringBuilder sb) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public StringBuilder toStringBuilder(int limit, int maxWidth, StringBuilder sb) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int calcLength(Object dicomEncodingOptions, boolean explicitVR) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeTo(Object dicomOutputStream) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void writePostPixelDataTo(Object dicomOutputStream) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void writeItemTo(Object dicomOutputStream) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean accept(Object visitor, boolean visitNestedDatasets) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void writeGroupTo(Object dicomOutputStream, int groupLengthTag) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DicomObject createFileMetaInformation(String tsuid) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DicomObject createFileMetaInformation(String tsuid, boolean includeImplementationVersionName) {
		// TODO Auto-generated method stub
		return null;
	}

//	@Override
//	public DicomObject createFileMetaInformation(String iuid, String cuid, String tsuid) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public DicomObject createFileMetaInformation(String iuid, String cuid, String tsuid,
//			boolean includeImplementationVersionName) {
//		// TODO Auto-generated method stub
//		return null;
//	}

	@Override
	public boolean matches(DicomObject keys, boolean ignorePNCase, boolean matchNoValue) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object validate(Object iod) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void validate(Object dataElement, Object validationResult) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DicomObject getModified(DicomObject other, DicomObject result) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DicomObject getRemovedOrModified(DicomObject other) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int diff(DicomObject other, int[] selection, DicomObject diff) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int diff(DicomObject other, int[] selection, DicomObject diff, boolean onlyModified) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void unifyCharacterSets(DicomObject attrsList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int removeAllBulkData() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int removePrivateAttributes(String privateCreator, int groupNumber) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int removePrivateAttributes() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void removeSelected(int... selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void replaceSelected(DicomObject others, int... selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void replaceUIDSelected(int... selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int removeCurveData() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int removeOverlayData() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public DicomObject getNestedDataset(int sequenceTag) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr, int valueIndex, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

}
