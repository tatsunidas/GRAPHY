package com.vis.dicom.dcm4cheImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.BulkData;
import org.dcm4che3.data.DatePrecision;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.Value;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;

import com.vis.dicom.DICOMBackend;
import com.vis.dicom.DatePrecisions;
import com.vis.dicom.DicomObject;
import com.vis.dicom.ItemPointer;

/**
 * DicomObject implementation class.
 * This class role is encapsulate "org.dcm4che3.data.Attributes".
 * 
 * @author tatsunidas
 *
 */

public class DicomObjectChe extends org.dcm4che3.data.Attributes implements DicomObject{
	
	/*
	 * DicomObject constructed by attributes and fmi.
	 * fmi examle
	 * Attributes fmi = new Attributes();
	 * fmi.setString(Tag.ImplementationVersionName, VR.SH, "DCM4CHE3"); 
	 * fmi.setString(Tag.ImplementationClassUID, VR.UI, UIDUtils.createUID());
	 * fmi.setString(Tag.TransferSyntaxUID, VR.UI, transferSyntax);
	 * fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, transferSyntax);
	 * fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI,UIDUtils.createUID());
	 * fmi.setString(Tag.FileMetaInformationVersion, VR.OB, "1");
	 * fmi.setInt(Tag.FileMetaInformationGroupLength, VR.UL, dicom.size()+fmi.size());
	 */
	
	private static final long serialVersionUID = -3556564730921871109L;
	private DicomObjectChe attr = null;
	private DicomObjectChe fmi = null;
	
	private static final int INIT_CAPACITY = 16;
    private static final int TO_STRING_LIMIT = 50;
    private static final int TO_STRING_WIDTH = 78;
    private transient Attributes parent;
    private transient String parentSequencePrivateCreator;
    private transient int parentSequenceTag;
    private transient int[] tags;
    private transient VR[] vrs;
    private transient Object[] values;
    private transient int size;
    private transient SpecificCharacterSet cs;
    private transient TimeZone tz;
    private transient int length = -1;
    private transient int[] groupLengths;
    private transient int groupLengthIndex0;

//    private long itemPosition = -1;
    private boolean containsSpecificCharacterSet;
    private boolean containsTimezoneOffsetFromUTC;
    private Map<String, Object> properties;
    private TimeZone defaultTimeZone;
    private volatile boolean readOnly;

	public DicomObjectChe(boolean bigEndian) {
		super(bigEndian, INIT_CAPACITY);
	}

	public DicomObjectChe(int initialCapacity) {
		super(false, initialCapacity);
	}

	public DicomObjectChe(boolean bigEndian, int initialCapacity) {
		init(initialCapacity);
	}

	public DicomObjectChe(DicomObjectChe other) {
		this(other, other.bigEndian());
	}

	public DicomObjectChe(Attributes other) {
		super(other, other.bigEndian());
	}

	public DicomObjectChe(DicomObjectChe other, boolean bigEndian) {
		this(bigEndian, other.size);
		if (other.properties != null)
			properties = new HashMap<String, Object>(other.properties);
		super.addAll(other);
	}

	public DicomObjectChe(DicomObjectChe other, int... selection) {
		this(other, other.bigEndian(), selection);
	}

	public DicomObjectChe(DicomObjectChe other, boolean bigEndian, int... selection) {
		this(bigEndian, selection.length);
		if (other.properties != null)
			properties = new HashMap<String, Object>(other.properties);
		super.addSelected(other, selection);
	}

    public DicomObjectChe(DicomObjectChe other, boolean bigEndian, DicomObjectChe selection) {
        this(bigEndian, selection.size());
        if (other.properties != null)
            properties = new HashMap<>(other.properties);
        super.addSelected(other, selection);
    }
	
    /**
     * for DicomReader
     * @param ds
     * @param fmi
     */
	DicomObjectChe(Attributes ds, Attributes fmi) {
		this.attr = new DicomObjectChe(ds);
		this.fmi = new DicomObjectChe(fmi);
	}
	
	/**
	 * for DicomReader
	 * @param path
	 * @param withPixel
	 */
	DicomObjectChe(String path, boolean withPixel) {
		DicomReaderChe reader = new DicomReaderChe(path, withPixel);
		Attributes attr = reader.getCoreDataset();
		Attributes fmi = reader.getFileMetaInfomation();
		this.attr = new DicomObjectChe(attr);
		this.fmi = new DicomObjectChe(fmi);
	}
	
	private void init(int initialCapacity) {
        this.tags = new int[initialCapacity];
        this.vrs = new VR[initialCapacity];
        this.values = new Object[initialCapacity];
    }
	
//	private void ensureModifiable() {
//        if (readOnly) {
//            throw new UnsupportedOperationException("read-only");
//        }
//    }
	
	DicomObjectChe setParent(DicomObjectChe parent, String parentSequencePrivateCreator, int parentSequenceTag) {
        if (parent != null) {
            if (parent.bigEndian() != super.bigEndian())
                throw new IllegalArgumentException(
                    "Endian of Item must match Endian of parent Data Set");
            if (this.parent != null)
                throw new IllegalArgumentException(
                    "Item already contained by Sequence");
            if (!containsSpecificCharacterSet)
                this.cs = null;
            if (!containsTimezoneOffsetFromUTC)
            	  this.tz = null;
        }
        this.parent = parent;
        this.parentSequencePrivateCreator = parentSequencePrivateCreator;
        this.parentSequenceTag = parentSequenceTag;
        return this;
    }
	
//    private void decodeStringValuesUsingSpecificCharacterSet() {
//        Object value;
//        VR vr;
//        SpecificCharacterSet cs = getSpecificCharacterSet();
//        for (int i = 0; i < size; i++) {
//            value = values[i];
//            if (value instanceof Sequence) {
//                for (Attributes item : (Sequence) value)
//                    item.decodeStringValuesUsingSpecificCharacterSet();
//            } else if ((vr = vrs[i]).useSpecificCharacterSet())
//                if (value instanceof byte[])
//                    values[i] =
//                        vr.toStrings((byte[]) value, bigEndian, cs);
//        }
//    }
	
//	private void ensureCapacity(int minCapacity) {
//        int oldCapacity = tags.length;
//        if (minCapacity > oldCapacity) {
//            int newCapacity = Math.max(minCapacity, oldCapacity << 1);
//            tags = Arrays.copyOf(tags, newCapacity);
//            vrs = Arrays.copyOf(vrs, newCapacity);
//            values = Arrays.copyOf(values, newCapacity);
//        }
//    }
//	
//	private int indexForInsertOf(int tag) {
//        return size == 0 ? -1
//                : tags[size-1] < tag ? -(size+1)
//                        : indexOf(tag);
//    }

//    private int indexOf(int tag) {
//        return Arrays.binarySearch(tags, 0, size, tag);
//    }

//    private int indexOf(String privateCreator, int tag) {
//        if (privateCreator != null) {
//            int creatorTag = creatorTagOf(privateCreator, tag, false);
//            if (creatorTag == -1)
//                return -1;
//            tag = TagUtils.toPrivateTag(creatorTag, tag);
//        }
//        return indexOf(tag);
//    }
    
//    private int creatorTagOf(String privateCreator, int tag, boolean reserve) {
//        if (!TagUtils.isPrivateGroup(tag))
//            throw new IllegalArgumentException(TagUtils.toString(tag)
//                    + " is not a private Data Element");
//
//        int group = tag & 0xffff0000;
//        int creatorTag = group | 0x10;
//        int index = indexOf(creatorTag);
//        if (index < 0)
//            index = -index-1;
//        while (index < size && (tags[index] & 0xffffff00) == group) {
//            creatorTag = tags[index];
//            if (vrs[index].isStringType()) {
//                Object creatorID = decodeStringValue(index);
//                if (privateCreator.equals(creatorID))
//                    return creatorTag;
//            }
//            index++;
//            creatorTag++;
//        }
//        if (!reserve)
//            return -1;
//
//        if ((creatorTag & 0xff00) != 0)
//            throw new IllegalStateException("No free block for Private Element "
//                    + TagUtils.toString(tag));
//        setString(creatorTag, VR.LO, privateCreator);
//        return creatorTag;
//    }

//    private Object decodeStringValue(int index) {
//        Object value = loadBulkData(values[index]);
//        if (value instanceof byte[]) {
//            value = vrs[index].toStrings((byte[]) value, super.bigEndian(),
//                    getSpecificCharacterSet(vrs[index]));
//            if (value instanceof String && ((String) value).isEmpty())
//                value = Value.NULL;
//            values[index] = value;
//        }
//        return value;
//    }

//    private Object loadBulkData(int index) {
//        return values[index] = loadBulkData(values[index]);
//    }

    static Object loadBulkData(Object value) {
        try {
            return (value instanceof BulkData)
                    ? ((BulkData) value).toBytes(null, ((BulkData) value).bigEndian())
                    : value;
        } catch (Exception e) {
//            logger.info("Failed to load {}", value);
            return Value.NULL;
        }
    }
    
//    private double[] decodeDSValue(int index) {
//        Object value = index < 0 ? Value.NULL : values[index];
//        if (value == Value.NULL)
//            return ByteUtils.EMPTY_DOUBLES;
//
//        if (value instanceof double[])
//            return (double[]) value;
//
//        double[] ds;
//        if (value instanceof byte[])
//            value = vrs[index].toStrings((byte[]) value, super.bigEndian(),
//                    SpecificCharacterSet.ASCII);
//        if (value instanceof String) {
//            String s = (String) value;
//            if (s.isEmpty()) {
//                values[index] = Value.NULL;
//                return ByteUtils.EMPTY_DOUBLES;
//            }
//            ds = new double[] { StringUtils.parseDS(s) };
//        } else { // value instanceof String[]
//            String[] ss = (String[]) value;
//            ds = new double[ss.length];
//            for (int i = 0; i < ds.length; i++) {
//                String s = ss[i];
//                ds[i] = (s != null && !s.isEmpty())
//                        ? StringUtils.parseDS(s)
//                        : Double.NaN;
//            }
//        }
//        values[index] = ds;
//        return ds;
//    }
//
//    private long[] decodeISValue(int index) {
//        Object value = index < 0 ? Value.NULL : values[index];
//        if (value == Value.NULL)
//            return ByteUtils.EMPTY_LONGS;
//
//        if (value instanceof long[])
//            return (long[]) value;
//
//        long[] ls;
//        if (value instanceof byte[])
//            value = vrs[index].toStrings((byte[]) value, super.bigEndian(),
//                    SpecificCharacterSet.ASCII);
//        if (value instanceof String) {
//            String s = (String) value;
//            if (s.isEmpty()) {
//                values[index] = Value.NULL;
//                return ByteUtils.EMPTY_LONGS;
//            }
//            ls = new long[] { StringUtils.parseIS(s) };
//        } else { // value instanceof String[]
//            String[] ss = (String[]) value;
//            ls = new long[ss.length];
//            for (int i = 0; i < ls.length; i++) {
//                String s = ss[i];
//                ls[i] = (s != null && !s.isEmpty())
//                            ? StringUtils.parseIS(s)
//                            : Long.MIN_VALUE;
//            }
//        }
//        values[index] = ls;
//        return ls;
//    }
//
//    private void updateVR(int index, VR vr) {
//        VR prev = vrs[index];
//        if (vr == prev)
//            return;
//
//        Object value = values[index];
//        if (!(value == Value.NULL
//                || value instanceof byte[]
//                || vr.isStringType() 
//                    && (value instanceof String 
//                    || value instanceof String[])))
//            throw new IllegalStateException("value instanceof " + value.getClass());
//
//        vrs[index] = vr;
//    }
//
//    private static boolean isEmpty(Object value) {
//        return (value instanceof Value) && ((Value) value).isEmpty();
//    }
//    
//    private String privateCreatorAt(int index) {
//        Object value;
//        return (index < 0 || !vrs[index].isStringType() || (value = decodeStringValue(index)) == Value.NULL)
//            ? null
//            : VR.LO.toString(value, false, 0, null);
//    }
//    
//    private static String[] toStrings(Object val) {
//        return (val instanceof String) 
//                ? new String[] { (String) val }
//                : (String[]) val;
//    }
//    
//    private DateRange toDateRange(String s, VR vr) {
//        String[] range = splitRange(s);
//        TimeZone tz = getTimeZone();
//        DatePrecision precision = new DatePrecision();
//        Date start = range[0] == null ? null
//                : vr.toDate(range[0], tz, 0, false, null, precision);
//        Date end = range[1] == null ? null
//                : vr.toDate(range[1], tz, 0, true, null, precision);
//        return new DateRange(start, end);
//    }
    
//    private static String[] splitRange(String s) {
//        String[] range = new String[2];
//        int delim = s.indexOf('-');
//        if (delim == -1)
//            range[0] = range[1] = s;
//        else {
//            if (delim > 0)
//                range[0] =  s.substring(0, delim);
//            if (delim < s.length() - 1)
//                range[1] =  s.substring(delim+1);
//        }
//        return range;
//    }

	@Override
	public DICOMBackend whatIsBackend() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setCore(Object attr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object getCore() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setFileMetaInfo(Object fmi) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object getFileMetaInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateFileMetaInfo() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DicomObject getNestedDataset(com.vis.dicom.ItemPointer... itemPointers) {
		// TODO Auto-generated method stub
		return null;
	}

	//List<com.vis.dicom.ItemPointer>
	public DicomObject getNestedDataset(Object itemPointers) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public com.vis.dicom.SpecificCharacterSet getSpecificCharacterSet(Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getValue(int tag, Object vr_holde) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getValue(String privateCreator, int tag, Object vr_holder) {
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

	public String getString(String privateCreator, int tag, Object vr, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getString(String privateCreator, int tag, Object vr, int valueIndex, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getStrings(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getInt(String privateCreator, int tag, Object vr, int defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(String privateCreator, int tag, Object vr, int valueIndex, int defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int[] getInts(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getLong(String privateCreator, int tag, Object vr, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(String privateCreator, int tag, Object vr, int valueIndex, long defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long[] getLongs(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public float getFloat(String privateCreator, int tag, Object vr, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(String privateCreator, int tag, Object vr, int valueIndex, float defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float[] getFloats(String privateCreator, int tag, Object vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getDouble(String privateCreator, int tag, Object vr, double defVal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getDouble(String privateCreator, int tag, Object vr, int valueIndex, double defVal) {
		// TODO Auto-generated method stub
		return 0;
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
	public Date getDate(int tag, Date defVal, Object precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, int valueIndex, Object precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(int tag, int valueIndex, Date defVal, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, Date defVal, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr, Date defVal,
			com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, int valueIndex, Date defVal,
			com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr, int valueIndex,
			com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr, int valueIndex, Date defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, int tag, com.vis.dicom.VR vr, int valueIndex, Date defVal,
			com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(long tag, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(long tag, Date defVal, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, long tag, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date getDate(String privateCreator, long tag, Date defVal, com.vis.dicom.DatePrecision precision) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(int tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, com.vis.dicom.VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, int tag, com.vis.dicom.VR vr, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(long tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date[] getDates(String privateCreator, long tag, DatePrecisions precisions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(int tag, com.vis.dicom.DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, int tag, com.vis.dicom.DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, int tag, com.vis.dicom.VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, int tag, com.vis.dicom.VR vr, com.vis.dicom.DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(long tag, com.vis.dicom.DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getDateRange(String privateCreator, long tag, com.vis.dicom.DateRange defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setNull(int tag, com.vis.dicom.VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setNull(String privateCreator, int tag, com.vis.dicom.VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setBytes(int tag, com.vis.dicom.VR vr, byte[] b) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setBytes(String privateCreator, int tag, com.vis.dicom.VR vr, byte[] b) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(int tag, com.vis.dicom.VR vr, String s) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(String privateCreator, int tag, com.vis.dicom.VR vr, String s) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(int tag, com.vis.dicom.VR vr, String... ss) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setString(String privateCreator, int tag, com.vis.dicom.VR vr, String... ss) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setInt(int tag, com.vis.dicom.VR vr, int... is) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setInt(String privateCreator, int tag, com.vis.dicom.VR vr, int... is) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setLong(int tag, com.vis.dicom.VR vr, long... ls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setLong(String privateCreator, int tag, com.vis.dicom.VR vr, long... ls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setFloat(int tag, com.vis.dicom.VR vr, float... fs) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setFloat(String privateCreator, int tag, com.vis.dicom.VR vr, float... fs) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDouble(int tag, com.vis.dicom.VR vr, double... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDouble(String privateCreator, int tag, com.vis.dicom.VR vr, double... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(int tag, com.vis.dicom.VR vr, Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(int tag, com.vis.dicom.VR vr, com.vis.dicom.DatePrecision precision, Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(String privateCreator, int tag, com.vis.dicom.VR vr, Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDate(String privateCreator, int tag, com.vis.dicom.VR vr, com.vis.dicom.DatePrecision precision,
			Date... ds) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDate(long tag, com.vis.dicom.DatePrecision precision, Date dt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDate(String privateCreator, long tag, com.vis.dicom.DatePrecision precision, Date dt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object setDateRange(int tag, com.vis.dicom.VR vr, com.vis.dicom.DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDateRange(int tag, com.vis.dicom.VR vr, com.vis.dicom.DatePrecision precision,
			com.vis.dicom.DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDateRange(String privateCreator, int tag, com.vis.dicom.VR vr, com.vis.dicom.DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setDateRange(String privateCreator, int tag, com.vis.dicom.VR vr,
			com.vis.dicom.DatePrecision precision, com.vis.dicom.DateRange range) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDateRange(long tag, com.vis.dicom.DateRange range) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDateRange(String privateCreator, long tag, com.vis.dicom.DateRange range) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object setValue(int tag, com.vis.dicom.VR vr, Object value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object setValue(String privateCreator, int tag, com.vis.dicom.VR vr, Object value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object newFragments(int tag, com.vis.dicom.VR vr, int initialCapacity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object newFragments(String privateCreator, int tag, com.vis.dicom.VR vr, int initialCapacity) {
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
	public boolean update(com.vis.dicom.DicomObject.UpdatePolicy updatePolicy, DicomObject newAttrs,
			DicomObject modified) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean update(com.vis.dicom.DicomObject.UpdatePolicy updatePolicy, boolean mergeOriginalAttributesSequence,
			DicomObject newAttrs, DicomObject modified) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean testUpdate(com.vis.dicom.DicomObject.UpdatePolicy updatePolicy, DicomObject newAttrs,
			DicomObject modified) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateSelected(com.vis.dicom.DicomObject.UpdatePolicy updatePolicy, DicomObject newAttrs,
			DicomObject modified, int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean testUpdateSelected(com.vis.dicom.DicomObject.UpdatePolicy updatePolicy, DicomObject newAttrs,
			DicomObject modified, int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateNotSelected(com.vis.dicom.DicomObject.UpdatePolicy updatePolicy, DicomObject newAttrs,
			DicomObject modified, int... selection) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean testUpdateNotSelected(com.vis.dicom.DicomObject.UpdatePolicy updatePolicy, DicomObject newAttrs,
			DicomObject modified, int... selection) {
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
	public void replaceSelected(DicomObject others, int... selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getString(String privateCreator, int tag, com.vis.dicom.VR vr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, com.vis.dicom.VR vr, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, com.vis.dicom.VR vr, int valueIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getString(String privateCreator, int tag, com.vis.dicom.VR vr, int valueIndex, String defVal) {
		// TODO Auto-generated method stub
		return null;
	}
}
