package com.vis.dicom;

import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

import com.vis.dicom.dcm4cheImpl.DicomObjectChe;

/**
 * 
 * DicomObject API to handle multiple dicom libraries.
 * 
 * 1. Be simple 
 * 2. keep (almost) read-only 
 * 3. if you want complex building dicom, do it in impl class, do not here.)
 * 
 * @author tatsunidas
 *
 */
public interface DicomObject {

	public static DicomObject newDicomObject() {
		DICOMBackend backend = DICOMBackend.getCurrent();
		if (backend == DICOMBackend.DCM4CHE) {
			return (DicomObject) new DicomObjectChe(false);
		} else if (backend == DICOMBackend.DCMTK) {
			// TODO
		}
		return null;
	}
	
	public static DicomObject newDicomObject(DicomObject dcm, DICOMBackend backend) {
		if (backend == null || backend == DICOMBackend.DCM4CHE) {
			return (DicomObject) new DicomObjectChe((DicomObjectChe)dcm);
		} else if (backend == DICOMBackend.DCMTK) {
			// TODO
		}
		return null;
	}

	public DicomObject getNestedDataset(int sequenceTag);

	public DicomObject getNestedDataset(int sequenceTag, int itemIndex);

	public DicomObject getNestedDataset(String privateCreator, int sequenceTag);

	public DicomObject getNestedDataset(String privateCreator, int sequenceTag, int itemIndex);

	public DicomObject getNestedDataset(ItemPointer... itemPointers);

	public DicomObject getNestedDataset(Object /*List<ItemPointer>*/ listedItemPointers);

	public DicomObject/*DicomObject*/ getFunctionGroup(int sequenceTag, int frameIndex);

	/**
	 * resolves to the actual private tag, given a private tag with placeholers
	 * (like 0011,xx13)
	 */
	public int tagOf(String privateCreator, int tag);
	
	public int[] tags();

	public boolean contains(int tag);

	public boolean contains(String privateCreator, int tag);

	public boolean containsValue(int tag);

	public boolean containsValue(String privateCreator, int tag);

	/**
	 * Test whether at least one tag within the given range is contained.
	 * 
	 * @param firstTag first tag (inclusive)
	 * @param lastTag  last tag (inclusive)
	 * @return whether at least one tag within the given range is contained
	 */
	public boolean containsTagInRange(int firstTag, int lastTag);

	public String privateCreatorOf(int tag);
	
	public byte[] getEncapsulatedPixelData();
	
	public void setEncapsulatedPixelData(byte[] data);

	public Object getValue(int tag);

	public Object getValue(int tag, VR.Holder vr_holder);

	public Object getValue(String privateCreator, int tag);

	public Object getValue(String privateCreator, int tag, VR.Holder vr_holder);

	/**
	 * Method name "getVR(tag)" is conflict to Attributes.getVR(tag).
	 * 
	 * @param tag
	 * @return
	 */
	public VR getVROn(int tag);

	public VR getVROn(String privateCreator, int tag);

	// retrun Sequence object
	public Object getSequence(int tag);

	// retrun Sequence object
	public Object getSequence(String privateCreator, int tag);

	public byte[] getBytes(int tag) throws IOException;

	public byte[] getBytes(String privateCreator, int tag) throws IOException;

	public byte[] getSafeBytes(int tag);

	public byte[] getSafeBytes(String privateCreator, int tag);

	public String getString(int tag);

	public String getString(int tag, String defVal);

	public String getString(int tag, int valueIndex);

	public String getString(int tag, int valueIndex, String defVal);

	public String getString(String privateCreator, int tag);

	public String getString(String privateCreator, int tag, String defVal);

	public String getString(String privateCreator, int tag, VR vr);

	public String getString(String privateCreator, int tag, VR vr, String defVal);

	public String getString(String privateCreator, int tag, int valueIndex);

	public String getString(String privateCreator, int tag, int valueIndex, String defVal);

	public String getString(String privateCreator, int tag, VR vr, int valueIndex);

	public String getString(String privateCreator, int tag, VR vr, int valueIndex, String defVal);

	public String[] getStrings(int tag);

	public String[] getStrings(String privateCreator, int tag);

	public String[] getStrings(String privateCreator, int tag, VR vr);

	public int getInt(int tag, int defVal);

	public int getInt(int tag, int valueIndex, int defVal);

	public int getInt(String privateCreator, int tag, int defVal);

	public int getInt(String privateCreator, int tag, VR vr, int defVal);

	public int getInt(String privateCreator, int tag, int valueIndex, int defVal);

	public int getInt(String privateCreator, int tag, VR vr, int valueIndex, int defVal);

	public int[] getInts(int tag);

	public int[] getInts(String privateCreator, int tag);

	public int[] getInts(String privateCreator, int tag, VR vr);

	public long getLong(int tag, long defVal);

	public long getLong(int tag, int valueIndex, long defVal);

	public long getLong(String privateCreator, int tag, long defVal);

	public long getLong(String privateCreator, int tag, VR vr, long defVal);

	public long getLong(String privateCreator, int tag, int valueIndex, long defVal);

	public long getLong(String privateCreator, int tag, VR vr, int valueIndex, long defVal);

	public long[] getLongs(int tag);

	public long[] getLongs(String privateCreator, int tag);

	public long[] getLongs(String privateCreator, int tag, VR vr);

	public float getFloat(int tag, float defVal);

	public float getFloat(int tag, int valueIndex, float defVal);

	public float getFloat(String privateCreator, int tag, float defVal);

	public float getFloat(String privateCreator, int tag, VR vr, float defVal);

	public float getFloat(String privateCreator, int tag, int valueIndex, float defVal);

	public float getFloat(String privateCreator, int tag, VR vr, int valueIndex, float defVal);

	public float[] getFloats(int tag);

	public float[] getFloats(String privateCreator, int tag);

	public float[] getFloats(String privateCreator, int tag, VR vr);

	public double getDouble(int tag, double defVal);

	public double getDouble(int tag, int valueIndex, double defVal);

	public double getDouble(String privateCreator, int tag, double defVal);

	public double getDouble(String privateCreator, int tag, VR vr, double defVal);

	public double getDouble(String privateCreator, int tag, int valueIndex, double defVal);

	public double getDouble(String privateCreator, int tag, VR vr, int valueIndex, double defVal);

	public double[] getDoubles(int tag);

	public double[] getDoubles(String privateCreator, int tag);

	public double[] getDoubles(String privateCreator, int tag, VR vr);

	public Date getDate(int tag);

	// Object is DatePrecision
	public Date getDate(int tag, DatePrecision precision);

	public Date getDate(int tag, Date defVal);

	// DatePrecision
	public Date getDate(int tag, Date defVal, DatePrecision precision);

	public Date getDate(int tag, int valueIndex);

	// DatePrecision
	public Date getDate(int tag, int valueIndex, DatePrecision precision);

	public Date getDate(int tag, int valueIndex, Date defVal);

	// DatePrecision
	public Date getDate(int tag, int valueIndex, Date defVal, DatePrecision precision);

	public Date getDate(String privateCreator, int tag);

	// DatePrecision
	public Date getDate(String privateCreator, int tag, DatePrecision precision);

	// DatePrecision
	public Date getDate(String privateCreator, int tag, Date defVal, DatePrecision precision);

	public Date getDate(String privateCreator, int tag, VR vr);

	// DatePrecision
	public Date getDate(String privateCreator, int tag, VR vr, DatePrecision precision);

	public Date getDate(String privateCreator, int tag, VR vr, Date defVal);

	// DatePrecision
	public Date getDate(String privateCreator, int tag, VR vr, Date defVal, DatePrecision precision);

	public Date getDate(String privateCreator, int tag, int valueIndex);

	// DatePrecision
	public Date getDate(String privateCreator, int tag, int valueIndex, DatePrecision precision);

	public Date getDate(String privateCreator, int tag, int valueIndex, Date defVal);

	// DatePrecision
	public Date getDate(String privateCreator, int tag, int valueIndex, Date defVal, DatePrecision precision);

	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex);

	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, DatePrecision precision);

	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, Date defVal);

	public Date getDate(String privateCreator, int tag, VR vr, int valueIndex, Date defVal, DatePrecision precision);

	public Date getDate(long tag);

	public Date getDate(long tag, DatePrecision precision);

	public Date getDate(long tag, Date defVal);

	public Date getDate(long tag, Date defVal, DatePrecision precision);

	public Date getDate(String privateCreator, long tag);

	public Date getDate(String privateCreator, long tag, DatePrecision precision);

	public Date getDate(String privateCreator, long tag, Date defVal);

	public Date getDate(String privateCreator, long tag, Date defVal, DatePrecision precision);

	public Date[] getDates(int tag);

	// DatePrecisions
	public Date[] getDates(int tag, DatePrecisions precisions);

	public Date[] getDates(String privateCreator, int tag);

	public Date[] getDates(String privateCreator, int tag, DatePrecisions precisions);

	public Date[] getDates(String privateCreator, int tag, VR vr);

	public Date[] getDates(String privateCreator, int tag, VR vr, DatePrecisions precisions);

	public Date[] getDates(long tag);

	public Date[] getDates(long tag, DatePrecisions precisions);

	public Date[] getDates(String privateCreator, long tag);

	public Date[] getDates(String privateCreator, long tag, DatePrecisions precisions);

	// com.vis.dicom.DateRange
	public Object getDateRange(int tag);

	public Object getDateRange(int tag, DateRange defVal);

	public Object getDateRange(String privateCreator, int tag);

	public Object getDateRange(String privateCreator, int tag, DateRange defVal);

	public Object getDateRange(String privateCreator, int tag, VR vr);

	public Object getDateRange(String privateCreator, int tag, VR vr, DateRange defVal);

	public Object getDateRange(long tag);

	public Object getDateRange(long tag, DateRange defVal);

	public Object getDateRange(String privateCreator, long tag);

	public Object getDateRange(String privateCreator, long tag, DateRange defVal);

	/**
	 * Set Specific Character Set (0008,0005) to specified code(s) and re-encode
	 * contained LO, LT, PN, SH, ST, UT attributes accordingly.
	 * 
	 * @param codes new value(s) of Specific Character Set (0008,0005)
	 */
	public void setSpecificCharacterSet(String... codes);

	public Object getSpecificCharacterSet();

	public Object getSpecificCharacterSet(VR vr);

	public void setDefaultTimeZone(TimeZone tz);

	public TimeZone getDefaultTimeZone();

	public TimeZone getTimeZone();

	/**
	 * Set Timezone Offset From UTC (0008,0201) to specified value and adjust
	 * contained DA, DT and TM attributs accordingly
	 * 
	 * @param utcOffset offset from UTC as (+|-)HHMM
	 */
	public void setTimezoneOffsetFromUTC(String utcOffset);

	/**
	 * Set the Default Time Zone to specified value and adjust contained DA, DT and
	 * TM attributs accordingly. If the Time Zone does not use Daylight Saving Time,
	 * attribute Timezone Offset From UTC (0008,0201) will be also set accordingly.
	 * If the Time zone uses Daylight Saving Time, a previous existing attribute
	 * Timezone Offset From UTC (0008,0201) will be removed.
	 * 
	 * @param tz Time Zone
	 *
	 * @see #setDefaultTimeZone(TimeZone)
	 * @see #setTimezoneOffsetFromUTC(String)
	 */
	public void setTimezone(TimeZone tz);

	public String getPrivateCreator(int tag);

	public Object remove(int tag);

	public Object remove(String privateCreator, int tag);

	public Object setNull(int tag, VR vr);

	public Object setNull(String privateCreator, int tag, VR vr);

	public Object setBytes(int tag, VR vr, byte[] b);

	public Object setBytes(String privateCreator, int tag, VR vr, byte[] b);

	public Object setString(int tag, VR vr, String s);

	public Object setString(String privateCreator, int tag, VR vr, String s);

	public Object setString(int tag, VR vr, String... ss);

	public Object setString(String privateCreator, int tag, VR vr, String... ss);

	public Object setInt(int tag, VR vr, int... is);

	public Object setInt(String privateCreator, int tag, VR vr, int... is);

	public Object setLong(int tag, VR vr, long... ls);

	public Object setLong(String privateCreator, int tag, VR vr, long... ls);

	public Object setFloat(int tag, VR vr, float... fs);

	public Object setFloat(String privateCreator, int tag, VR vr, float... fs);

	public Object setDouble(int tag, VR vr, double... ds);

	public Object setDouble(String privateCreator, int tag, VR vr, double... ds);

	public Object setDate(int tag, VR vr, Date... ds);

	public Object setDate(int tag, VR vr, DatePrecision precision, Date... ds);

	public Object setDate(String privateCreator, int tag, VR vr, Date... ds);

	public Object setDate(String privateCreator, int tag, VR vr, DatePrecision precision, Date... ds);

	public void setDate(long tag, Date dt);

	public void setDate(long tag, DatePrecision precision, Date dt);

	public void setDate(String privateCreator, long tag, Date dt);

	public void setDate(String privateCreator, long tag, DatePrecision precision, Date dt);

	public Object setDateRange(int tag, VR vr, DateRange range);

	public Object setDateRange(int tag, VR vr, DatePrecision precision, DateRange range);

	public Object setDateRange(String privateCreator, int tag, VR vr, DateRange range);

	public Object setDateRange(String privateCreator, int tag, VR vr, DatePrecision precision, DateRange range);

	public void setDateRange(long tag, DateRange range);

	public void setDateRange(String privateCreator, long tag, DateRange range);

	public Object setValue(int tag, VR vr, Object value);

	public Object setValue(String privateCreator, int tag, VR vr, Object value);

	// return Sequence
	public Object newDicomSequence(int tag, int initialCapacity);

	public Object newDicomSequence(String privateCreator, int tag, int initialCapacity);

	public Object ensureSequence(int tag, int initialCapacity);

	public Object ensureSequence(String privateCreator, int tag, int initialCapacity);

	// return Fragments
	public Object newFragments(int tag, VR vr, int initialCapacity);

	// return Fragments
	public Object newFragments(String privateCreator, int tag, VR vr, int initialCapacity);

	public boolean addAll(DicomObject other);

	public boolean addAll(DicomObject other, boolean mergeOriginalAttributesSequence);

	public boolean addSelected(DicomObject other, DicomObject selection);

	public boolean addSelected(DicomObject other, String privateCreator, int tag);

	/**
	 * Add selected attributes from another Attributes object to this. The specified
	 * array of tag values must be sorted (as by the
	 * {@link java.util.Arrays#sort(int[])} method) prior to making this call.
	 * 
	 * @param other     the other Attributes object
	 * @param selection sorted tag values
	 * @return <tt>true</tt> if one ore more attributes were added
	 */
	public boolean addSelected(DicomObject other, int... selection);

	/**
	 * Add selected attributes from another Attributes object to this. The specified
	 * array of tag values must be sorted (as by the
	 * {@link java.util.Arrays#sort(int[], int, int)} method) prior to making this
	 * call.
	 * 
	 * @param other     the other Attributes object
	 * @param selection sorted tag values
	 * @param fromIndex the index of the first tag (inclusive)
	 * @param toIndex   the index of the last tag (exclusive)
	 * @return <tt>true</tt> if one ore more attributes were added
	 */
	public boolean addSelected(DicomObject other, int[] selection, int fromIndex, int toIndex);

	/**
	 * Add not selected attributes from another Attributes object to this. The
	 * specified array of tag values must be sorted (as by the
	 * {@link java.util.Arrays#sort(int[])} method) prior to making this call.
	 * 
	 * @param other     the other Attributes object
	 * @param selection sorted tag values
	 * @return <tt>true</tt> if one ore more attributes were added
	 */
	public boolean addNotSelected(DicomObject other, int... selection);

	/**
	 * Add not selected attributes from another Attributes object to this. The
	 * specified array of tag values must be sorted (as by the
	 * {@link java.util.Arrays#sort(int[])} method) prior to making this call.
	 * 
	 * @param other     the other Attributes object
	 * @param selection sorted tag values
	 * @param fromIndex the index of the first tag (inclusive)
	 * @param toIndex   the index of the last tag (exclusive)
	 * @return <tt>true</tt> if one ore more attributes were added
	 */
	public boolean addNotSelected(DicomObject other, int[] selection, int fromIndex, int toIndex);

	/**
	 * Append item to already existing or new added (0400,0561) Original Attributes
	 * Sequence.
	 *
	 * @param sourceOfPreviousValues
	 * @param modificationDateTime
	 * @param reasonForModification
	 * @param modifyingSystem
	 * @param originalAttributes
	 * @return the same Attributes instance
	 */
	public DicomObject addOriginalAttributes(String sourceOfPreviousValues, Date modificationDateTime,
			String reasonForModification, String modifyingSystem, DicomObject originalAttributes);

	public boolean equalValues(DicomObject other, int tag);

	public boolean equalValues(DicomObject other, String privateCreator, int tag);

	public String toString(int limit, int maxWidth);

	public StringBuilder toStringBuilder(StringBuilder sb);

	public StringBuilder toStringBuilder(int limit, int maxWidth, StringBuilder sb);

	public int calcLength(com.vis.dicom.DicomEncodingOptions dicomEncodingOptions, boolean explicitVR);

	public void writeTo(Object dicomOutputStream) throws IOException;

	public void writePostPixelDataTo(Object dicomOutputStream) throws IOException;

	public void writeItemTo(Object dicomOutputStream) throws IOException;

	public void writeGroupTo(Object dicomOutputStream, int groupLengthTag) throws IOException;

	/**
	 * Creates DICOM File Meta Information for this <i>Data Set</i> with given
	 * <i>Transfer Syntax UID (0002,0010)</i>, including optional <i>Implementation
	 * Version Name (0002,0013)</i>.
	 *
	 * @param tsuid <i>Transfer Syntax UID (0002,0010)</i>
	 * @return created DICOM File Meta Information
	 */
	public Object createFileMetaInformation(String tsuid);

	/**
	 * Creates DICOM File Meta Information for this <i>Data Set</i> with given
	 * <i>Transfer Syntax UID (0002,0010)</i>.
	 *
	 * @param tsuid                            <i>Transfer Syntax UID
	 *                                         (0002,0010)</i>
	 * @param includeImplementationVersionName <code>true</code> if the optional
	 *                                         <i>Implementation Version Name
	 *                                         (0002,0013)</i> is to be included;
	 *                                         <code>false</code> if it is to be
	 *                                         omitted.
	 * @return created DICOM File Meta Information
	 */
	public Object createFileMetaInformation(String tsuid, boolean includeImplementationVersionName);

	public boolean matches(DicomObject keys, boolean ignorePNCase, boolean matchNoValue);


	/**
	 * Add attributes of this data set which were replaced in the specified other
	 * data set into the result data set. If no result data set is passed, a new
	 * result set will be instantiated.
	 * 
	 * @param other  data set
	 * @param result data set or {@code null}
	 *
	 * @return result data set.
	 */
	public DicomObject getModified(DicomObject other, DicomObject result);

	/**
	 * Returns attributes of this data set which were removed or replaced in the
	 * specified other data set.
	 * 
	 * @param other data set
	 * @return attributes of this data set which were removed or replaced in the
	 *         specified other data set.
	 */
	public DicomObject getRemovedOrModified(DicomObject other);

	public int diff(DicomObject other, int[] selection, DicomObject diff);

	public int diff(DicomObject other, int[] selection, DicomObject diff, boolean onlyModified);

	public void unifyCharacterSets(DicomObject attrsList);

	public int removeAllBulkData();

	public int removePrivateAttributes(String privateCreator, int groupNumber);

	public int removePrivateAttributes();

	public void removeSelected(int... selection);

	public void replaceSelected(DicomObject others, int... selection);

	public void replaceUIDSelected(int... selection);

	/**
	 * In Attributes class, this method is "final".
	 * However, I add this here to remember.
	 * @return bigEndian
	 */
	public boolean bigEndian();

}
