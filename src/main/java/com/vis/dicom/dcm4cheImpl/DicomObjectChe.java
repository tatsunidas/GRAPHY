package com.vis.dicom.dcm4cheImpl;

import java.io.IOException;
import java.util.Date;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.TagUtils;

import com.vis.dicom.DatePrecision;
import com.vis.dicom.DatePrecisions;
import com.vis.dicom.DateRange;
import com.vis.dicom.DicomObject;
import com.vis.dicom.ItemPointer;
import com.vis.dicom.VR;
import com.vis.dicom.VR.Holder;

/**
 * DicomObject implementation class.
 * 
 * @author tatsunidas
 *
 */

public class DicomObjectChe extends Attributes implements DicomObject{
	
	/*
	 * way to success
	 * 
	 * do re-implemented private variables and methods in Attributes
	 */
	
	private static final long serialVersionUID = -3556564730921871109L;
	
	private static final int INIT_CAPACITY = 16;
//	private static final int TO_STRING_LIMIT = 50;
//	private static final int TO_STRING_WIDTH = 78;
	private transient Attributes parent;
//	private transient String parentSequencePrivateCreator;
//	private transient int parentSequenceTag;
//	private transient int[] tags;
//	private transient VR[] vrs;
//	private transient Object[] values;
//	private transient int size;
//	private transient SpecificCharacterSet cs;
//	private transient TimeZone tz;
//	private transient int length = -1;
//	private transient int[] groupLengths;
//	private transient int groupLengthIndex0;
//
	private final boolean bigEndian;
//	private long itemPosition = -1;
//	private boolean containsSpecificCharacterSet;
//	private boolean containsTimezoneOffsetFromUTC;
//	private Map<String, Object> properties;
//	private TimeZone defaultTimeZone;
//	private volatile boolean readOnly;

	public DicomObjectChe() {
		this(false);
	}
	
	public DicomObjectChe(boolean bigEndian) {
		this(bigEndian, INIT_CAPACITY);
	}

	public DicomObjectChe(int initialCapacity) {
		this(false, initialCapacity);
	}

	public DicomObjectChe(boolean bigEndian, int initialCapacity) {
		super(bigEndian, initialCapacity);
		this.parent = super.getParent();
		this.bigEndian = bigEndian;
	}

	public DicomObjectChe(Attributes other) {
		this(other, other.bigEndian());
	}

	public DicomObjectChe(Attributes other, boolean bigEndian) {
		super(other, other.bigEndian());
		this.parent = super.getParent();
		this.bigEndian = bigEndian;
	}

	public DicomObjectChe(Attributes other, boolean bigEndian, int... selection) {
		super(other, bigEndian, selection);
		this.parent = super.getParent();
		this.bigEndian = bigEndian;
	}

    public DicomObjectChe(Attributes other, boolean bigEndian, Attributes selection) {
    	super(other, bigEndian, selection);
    	this.parent = super.getParent();
    	this.bigEndian = bigEndian;
    }
   
    /**
     * read-only
     */
    
    public DicomObjectChe getNestedDataset(int sequenceTag) {
    	Attributes ds = super.getNestedDataset(sequenceTag);
		return new DicomObjectChe(ds);
    }

	public DicomObjectChe getNestedDataset(int sequenceTag, int itemIndex) {
		Attributes ds = super.getNestedDataset(sequenceTag, itemIndex);
		return new DicomObjectChe(ds);
	}

	public DicomObjectChe getNestedDataset(String privateCreator, int sequenceTag) {
		Attributes ds = super.getNestedDataset(privateCreator, sequenceTag);
		return new DicomObjectChe(ds);
	}

	public DicomObjectChe getNestedDataset(String privateCreator, int sequenceTag, int itemIndex) {
		Attributes ds = super.getNestedDataset(privateCreator, sequenceTag, itemIndex);
		return new DicomObjectChe(ds);
	}
    
	@Override
	public DicomObjectChe getNestedDataset(ItemPointer... itemPointers) {
		Attributes ds = super.getNestedDataset(Interpreter.itemPointersChe(itemPointers));
		return new DicomObjectChe(ds);
	}

	@Override
	public DicomObjectChe getNestedDataset(Object listedItemPointers) {
		if ( listedItemPointers instanceof java.util.List<?>) {
			if(((java.util.List<?>) listedItemPointers).get(0) instanceof com.vis.dicom.ItemPointer) {
				@SuppressWarnings("unchecked")
				java.util.List<com.vis.dicom.ItemPointer> ips = (java.util.List<com.vis.dicom.ItemPointer>)listedItemPointers;
				Attributes ds = super.getNestedDataset(Interpreter.itemPointersChe(ips));
				return new DicomObjectChe(ds);
			}
		}
		return null;
	}
	
	@Override
	public DicomObjectChe getFunctionGroup(int sequenceTag, int frameIndex) {
		Attributes fg = super.getFunctionGroup(sequenceTag, frameIndex);
		return new DicomObjectChe(fg);
	}

	@Override
	public Object getSpecificCharacterSet(VR vr) {
		org.dcm4che3.data.SpecificCharacterSet s = super.getSpecificCharacterSet(Interpreter.vrChe(vr));
		return Interpreter.specificCharacterSet(s);
	}

	@Override
	public Object getValue(int tag, Holder vr_holder) {
		return super.getValue(tag, Interpreter.vrHolderChe(vr_holder));
	}

	@Override
	public Object getValue(String privateCreator, int tag, Holder vr_holder) {
		return super.getValue(privateCreator, tag, Interpreter.vrHolderChe(vr_holder));
	}
	
	@Override
	public com.vis.dicom.VR getVROn(int tag){
		org.dcm4che3.data.VR vrChe = super.getVR(tag);
		return Interpreter.vr(vrChe);
	}
	
	@Override
	public com.vis.dicom.VR getVROn(String privateCreator, int tag){
		org.dcm4che3.data.VR vrChe = super.getVR(privateCreator, tag);
		return Interpreter.vr(vrChe);
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getString(privateCreator, tag, vrChe);
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr, String defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getString(privateCreator, tag, vrChe, defVal);
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr, int valueIndex) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getString(privateCreator, tag, vrChe, valueIndex);
	}

	@Override
	public String getString(String privateCreator, int tag, VR vr, int valueIndex, String defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getString(privateCreator, tag, vrChe, valueIndex, defVal);
	}

	@Override
	public String[] getStrings(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getStrings(privateCreator, tag, vrChe);
	}

	@Override
	public int getInt(String privateCreator, int tag, VR vr, int defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getInt(privateCreator, tag, vrChe, defVal);
	}

	@Override
	public int getInt(String privateCreator, int tag, VR vr, int valueIndex, int defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getInt(privateCreator, tag, vrChe, valueIndex, defVal);
	}

	@Override
	public int[] getInts(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getInts(privateCreator, tag, vrChe);
	}

	@Override
	public long getLong(String privateCreator, int tag, VR vr, long defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getLong(privateCreator, tag, vrChe, defVal);
	}

	@Override
	public long getLong(String privateCreator, int tag, VR vr, int valueIndex, long defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getLong(privateCreator, tag, vrChe, valueIndex, defVal);
	}

	@Override
	public long[] getLongs(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getLongs(privateCreator, tag, vrChe);
	}

	@Override
	public float getFloat(String privateCreator, int tag, VR vr, float defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getFloat(privateCreator, tag, vrChe, defVal);
	}

	@Override
	public float getFloat(String privateCreator, int tag, VR vr, int valueIndex, float defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getFloat(privateCreator, tag, vrChe, valueIndex, defVal);
	}

	@Override
	public float[] getFloats(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getFloats(privateCreator, tag, vrChe);
	}

	@Override
	public double getDouble(String privateCreator, int tag, VR vr, double defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDouble(privateCreator, tag, vrChe, defVal);
	}

	@Override
	public double getDouble(String privateCreator, int tag, VR vr, int valueIndex, double defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDouble(privateCreator, tag, vrChe, valueIndex, defVal);
	}

	@Override
	public double[] getDoubles(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDoubles(privateCreator, tag, vrChe);
	}

	@Override
	public Date getDate(int tag, DatePrecision precision) {
		return super.getDate(tag, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(int tag, Date defVal, DatePrecision precision) {
		return super.getDate(tag, defVal, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(int tag, int valueIndex, DatePrecision precision) {
		return super.getDate(tag, valueIndex, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(int tag, int valueIndex, Date defVal, DatePrecision precision) {
		return super.getDate(tag, valueIndex, defVal, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, DatePrecision precision) {
		return super.getDate(privateCreator, tag, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, Date defVal, DatePrecision precision) {
		return super.getDate(privateCreator, tag, defVal,Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe);
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, DatePrecision precision) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, Date defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe, defVal);
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, Date defVal, DatePrecision precision) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe, defVal, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex, DatePrecision precision) {
		return super.getDate(privateCreator, tag, valueIndex, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex, Date defVal, DatePrecision precision) {
		return super.getDate(privateCreator, tag, valueIndex, defVal, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe, valueIndex);
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, DatePrecision precision) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe, valueIndex, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, Date defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe, valueIndex, defVal);
	}

	@Override
	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, Date defVal, DatePrecision precision) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDate(privateCreator, tag, vrChe, valueIndex, defVal, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(long tag, DatePrecision precision) {
		return super.getDate(tag, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(long tag, Date defVal, DatePrecision precision) {
		return super.getDate(tag, defVal, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, long tag, DatePrecision precision) {
		return super.getDate(privateCreator, tag, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date getDate(String privateCreator, long tag, Date defVal, DatePrecision precision) {
		return super.getDate(privateCreator, tag, defVal, Interpreter.datePrecisionChe(precision));
	}

	@Override
	public Date[] getDates(int tag, DatePrecisions precisions) {
		return super.getDates(tag, Interpreter.datePrecisionsChe(precisions));
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, DatePrecisions precisions) {
		return super.getDates(privateCreator, tag, Interpreter.datePrecisionsChe(precisions));
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDates(privateCreator, tag, vrChe);
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, VR vr, DatePrecisions precisions) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.getDates(privateCreator, tag, vrChe, Interpreter.datePrecisionsChe(precisions));
	}

	@Override
	public Date[] getDates(long tag, DatePrecisions precisions) {
		return super.getDates(tag, Interpreter.datePrecisionsChe(precisions));
	}

	@Override
	public Date[] getDates(String privateCreator, long tag, DatePrecisions precisions) {
		return super.getDates(privateCreator, tag, Interpreter.datePrecisionsChe(precisions));
	}

	@Override
	public com.vis.dicom.DateRange getDateRange(int tag, DateRange defVal) {
		org.dcm4che3.data.DateRange drChe = super.getDateRange(tag, Interpreter.dateRangeChe(defVal));
		return Interpreter.dateRange(drChe);
	}

	@Override
	public com.vis.dicom.DateRange getDateRange(String privateCreator, int tag, DateRange defVal) {
		org.dcm4che3.data.DateRange drChe = super.getDateRange(privateCreator, tag, Interpreter.dateRangeChe(defVal));
		return Interpreter.dateRange(drChe);
	}

	@Override
	public com.vis.dicom.DateRange getDateRange(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		org.dcm4che3.data.DateRange drChe = super.getDateRange(privateCreator, tag, vrChe);
		return Interpreter.dateRange(drChe);
	}

	@Override
	public com.vis.dicom.DateRange getDateRange(String privateCreator, int tag, VR vr, DateRange defVal) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		org.dcm4che3.data.DateRange drChe = super.getDateRange(privateCreator, tag, vrChe, Interpreter.dateRangeChe(defVal));
		return Interpreter.dateRange(drChe);
	}

	@Override
	public com.vis.dicom.DateRange getDateRange(long tag, DateRange defVal) {
		org.dcm4che3.data.DateRange drChe = super.getDateRange(tag, Interpreter.dateRangeChe(defVal));
		return Interpreter.dateRange(drChe);
	}

	@Override
	public com.vis.dicom.DateRange getDateRange(String privateCreator, long tag, DateRange defVal) {
		org.dcm4che3.data.DateRange drChe = super.getDateRange(privateCreator, tag, Interpreter.dateRangeChe(defVal));
		return Interpreter.dateRange(drChe);
	}

	@Override
	public Object setNull(int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setNull(tag, vrChe);
	}

	@Override
	public Object setNull(String privateCreator, int tag, VR vr) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setNull(privateCreator, tag, vrChe);
	}

	@Override
	public Object setBytes(int tag, VR vr, byte[] b) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setBytes(tag, vrChe, b);
	}

	@Override
	public Object setBytes(String privateCreator, int tag, VR vr, byte[] b) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setBytes(privateCreator, tag, vrChe, b);
	}

	@Override
	public Object setString(int tag, VR vr, String s) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setString(tag, vrChe, s);
	}

	@Override
	public Object setString(String privateCreator, int tag, VR vr, String s) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setString(privateCreator, tag, vrChe, s);
	}

	@Override
	public Object setString(int tag, VR vr, String... ss) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setString(tag, vrChe, ss);
	}

	@Override
	public Object setString(String privateCreator, int tag, VR vr, String... ss) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setString(privateCreator, tag, vrChe, ss);
	}

	@Override
	public Object setInt(int tag, VR vr, int... is) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setInt(tag, vrChe, is);
	}

	@Override
	public Object setInt(String privateCreator, int tag, VR vr, int... is) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setInt(privateCreator, tag, vrChe, is);
	}

	@Override
	public Object setLong(int tag, VR vr, long... ls) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setLong(tag,vrChe,ls);
	}

	@Override
	public Object setLong(String privateCreator, int tag, VR vr, long... ls) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setLong(privateCreator, tag, vrChe, ls);
	}

	@Override
	public Object setFloat(int tag, VR vr, float... fs) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setFloat(tag, vrChe, fs);
	}

	@Override
	public Object setFloat(String privateCreator, int tag, VR vr, float... fs) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setFloat(privateCreator, tag, vrChe, fs);
	}

	@Override
	public Object setDouble(int tag, VR vr, double... ds) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setDouble(tag, vrChe, ds);
	}

	@Override
	public Object setDouble(String privateCreator, int tag, VR vr, double... ds) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setDouble(privateCreator, tag, vrChe, ds);
	}

	@Override
	public Object setDate(int tag, VR vr, Date... ds) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setDate(tag, vrChe, ds);
	}

	@Override
	public Object setDate(int tag, VR vr, DatePrecision precision, Date... ds) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setDate(tag, vrChe, Interpreter.datePrecisionChe(precision), ds);
	}

	@Override
	public Object setDate(String privateCreator, int tag, VR vr, Date... ds) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setDate(privateCreator, tag, vrChe, ds);
	}

	@Override
	public Object setDate(String privateCreator, int tag, VR vr, DatePrecision precision, Date... ds) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setDate(privateCreator, tag, vrChe, Interpreter.datePrecisionChe(precision), ds);
	}

	@Override
	public void setDate(long tag, DatePrecision precision, Date dt) {
		super.setDate(tag, Interpreter.datePrecisionChe(precision), dt);
	}

	@Override
	public void setDate(String privateCreator, long tag, DatePrecision precision, Date dt) {
		super.setDate(privateCreator, tag, Interpreter.datePrecisionChe(precision), dt);
	}

	@Override
	public Object setDateRange(int tag, VR vr, DateRange range) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setDateRange(tag, vrChe, Interpreter.dateRangeChe(range));
	}

	@Override
	public Object setDateRange(int tag, VR vr, DatePrecision precision, DateRange range) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		org.dcm4che3.data.DatePrecision dpChe = Interpreter.datePrecisionChe(precision);
		org.dcm4che3.data.DateRange drChe = Interpreter.dateRangeChe(range);
		return super.setDateRange(tag, vrChe, dpChe, drChe);
	}

	@Override
	public Object setDateRange(String privateCreator, int tag, VR vr, DateRange range) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		org.dcm4che3.data.DateRange drChe = Interpreter.dateRangeChe(range);
		return super.setDateRange(privateCreator, tag, vrChe, drChe);
	}

	@Override
	public Object setDateRange(String privateCreator, int tag, VR vr, DatePrecision precision, DateRange range) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		org.dcm4che3.data.DatePrecision dpChe = Interpreter.datePrecisionChe(precision);
		org.dcm4che3.data.DateRange drChe = Interpreter.dateRangeChe(range);
		return super.setDateRange(privateCreator, tag, vrChe, dpChe, drChe);
	}

	@Override
	public void setDateRange(long tag, DateRange range) {
		org.dcm4che3.data.DateRange drChe = Interpreter.dateRangeChe(range);
		super.setDateRange(tag, drChe);
	}

	@Override
	public void setDateRange(String privateCreator, long tag, DateRange range) {
		org.dcm4che3.data.DateRange drChe = Interpreter.dateRangeChe(range);
		super.setDateRange(privateCreator, tag, drChe);
	}

	@Override
	public Object setValue(int tag, VR vr, Object value) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setValue(tag, vrChe, value);
	}

	@Override
	public Object setValue(String privateCreator, int tag, VR vr, Object value) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.setValue(privateCreator, tag, vrChe, value);
	}

	@Override
	public Object newFragments(int tag, VR vr, int initialCapacity) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.newFragments(tag, vrChe, initialCapacity);
	}

	@Override
	public Object newFragments(String privateCreator, int tag, VR vr, int initialCapacity) {
		org.dcm4che3.data.VR vrChe = Interpreter.vrChe(vr);
		return super.newFragments(privateCreator, tag, vrChe, initialCapacity);
	}

	@Override
	public boolean addAll(DicomObject other) {
		if(other instanceof Attributes) {
			return super.addAll((Attributes)other);
		}
		return false;
	}

	@Override
	public boolean addAll(DicomObject other, boolean mergeOriginalAttributesSequence) {
		if(other instanceof Attributes) {
			return super.addAll((Attributes)other, mergeOriginalAttributesSequence);
		}
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, DicomObject selection) {
		if(other instanceof Attributes && selection instanceof Attributes) {
			return super.addSelected((Attributes)other, (Attributes)selection);
		}
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, String privateCreator, int tag) {
		if(other instanceof Attributes) {
			return super.addSelected((Attributes)other, privateCreator, tag);
		}
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, int... selection) {
		if(other instanceof Attributes) {
			return super.addSelected((Attributes)other, selection);
		}
		return false;
	}

	@Override
	public boolean addSelected(DicomObject other, int[] selection, int fromIndex, int toIndex) {
		if(other instanceof Attributes) {
			return super.addSelected((Attributes)other, selection, fromIndex, toIndex);
		}
		return false;
	}

	@Override
	public boolean addNotSelected(DicomObject other, int... selection) {
		if(other instanceof Attributes) {
			return super.addNotSelected((Attributes)other, selection);
		}
		return false;
	}

	@Override
	public boolean addNotSelected(DicomObject other, int[] selection, int fromIndex, int toIndex) {
		if(other instanceof Attributes) {
			return super.addNotSelected((Attributes)other, selection, fromIndex, toIndex);
		}
		return false;
	}

	@Override
	public DicomObject addOriginalAttributes(String sourceOfPreviousValues, Date modificationDateTime,
			String reasonForModification, String modifyingSystem, DicomObject originalAttributes) {
		Attributes added = super.addOriginalAttributes(sourceOfPreviousValues, modificationDateTime, reasonForModification, modifyingSystem, (Attributes)originalAttributes);
		DicomObjectChe addedChe = (DicomObjectChe)added;
		return (DicomObject)addedChe;
	}

	@Override
	public boolean equalValues(DicomObject other, int tag) {
		return super.equalValues((Attributes)other, tag);
	}

	@Override
	public boolean equalValues(DicomObject other, String privateCreator, int tag) {
		return super.equalValues((Attributes)other, privateCreator, tag);
	}

	@Override
	public int calcLength(com.vis.dicom.DicomEncodingOptions dicomEncodingOptions, boolean explicitVR) {
		return super.calcLength(Interpreter.dicomEncodingOpsChe(dicomEncodingOptions), explicitVR);
	}

	@Override
	public void writeTo(Object dicomOutputStream) throws IOException {
		DicomOutputStream dos = (DicomOutputStream)dicomOutputStream;
		super.writeTo(dos);
	}

	@Override
	public void writePostPixelDataTo(Object dicomOutputStream) throws IOException {
		DicomOutputStream dos = (DicomOutputStream)dicomOutputStream;
		super.writePostPixelDataTo(dos);
	}

	@Override
	public void writeItemTo(Object dicomOutputStream) throws IOException {
		DicomOutputStream dos = (DicomOutputStream)dicomOutputStream;
		super.writeItemTo(dos);
	}

	@Override
	public void writeGroupTo(Object dicomOutputStream, int groupLengthTag) throws IOException {
		DicomOutputStream dos = (DicomOutputStream)dicomOutputStream;
		super.writeGroupTo(dos, groupLengthTag);
	}

	@Override
	public boolean matches(DicomObject keys, boolean ignorePNCase, boolean matchNoValue) {
		return super.matches((Attributes)keys, ignorePNCase, matchNoValue);
	}

	@Override
	public DicomObject getModified(DicomObject other, DicomObject result) {
		DicomObjectChe mod = (DicomObjectChe) super.getModified((DicomObjectChe)other, (DicomObjectChe)result);
		return (DicomObject)mod;
	}

	@Override
	public DicomObject getRemovedOrModified(DicomObject other) {
		DicomObjectChe mod = (DicomObjectChe) super.getRemovedOrModified((DicomObjectChe)other);
		return (DicomObject)mod;
	}

	@Override
	public int diff(DicomObject other, int[] selection, DicomObject diff) {
		return super.diff((DicomObjectChe)other, selection, (DicomObjectChe)diff);
	}

	@Override
	public int diff(DicomObject other, int[] selection, DicomObject diff, boolean onlyModified) {
		return super.diff((DicomObjectChe)other, selection, (DicomObjectChe)diff, onlyModified);
	}

	@Override
	public void unifyCharacterSets(DicomObject attrsList) {
		super.unifyCharacterSets((DicomObjectChe)attrsList);
	}

	@Override
	public void replaceSelected(DicomObject others, int... selection) {
		super.replaceSelected((DicomObjectChe)others, selection);
	}
	
	@Override
	public DicomObjectChe createFileMetaInformation(String tsuid) {
		Attributes fmi_ = super.createFileMetaInformation(tsuid);
		DicomObjectChe doc = new DicomObjectChe(fmi_);
		return doc;
	}
	
	public void checkInGroup(int i, int groupLengthTag, int[] tags) {
        int tag = tags[i];
        if (TagUtils.groupLengthTagOf(tag) != groupLengthTag)
            throw new IllegalStateException(TagUtils.toString(tag)
                    + " does not belong to group (" 
                    + TagUtils.shortToHexString(
                            TagUtils.groupNumber(groupLengthTag))
                    + ",eeee).");
        
    }
}