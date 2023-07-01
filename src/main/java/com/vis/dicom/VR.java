package com.vis.dicom;

import java.util.Date;
import java.util.TimeZone;

public interface VR {
	
	public enum Type{
		/**
		 * Application Entity
		 */
		AE,

		/**
		 * Age String
		 */
		AS,

		/**
		 * Attribute Tag
		 */
		AT,

		/**
		 * Code String
		 */
		CS,

		/**
		 * Date
		 */
		DA,

		/**
		 * Decimal String
		 */
		DS,

		/**
		 * Date Time
		 */
		DT,

		/**
		 * Floating Point Double
		 */
		FD,

		/**
		 * Floating Point Single
		 */
		FL,

		/**
		 * Integer String
		 */
		IS,

		/**
		 * Long String
		 */
		LO,

		/**
		 * Long Text
		 */
		LT,

		/**
		 * Other Byte
		 */
		OB,

		/**
		 * Other Double
		 */
		OD,

		/**
		 * Other Float
		 */
		OF,

		/**
		 * Other Long
		 */
		OL,

		/**
		 * Other 64-bit Very Long
		 */
		OV,

		/**
		 * Other Word
		 */
		OW,

		/**
		 * Person Name
		 */
		PN,

		/**
		 * Short String
		 */
		SH,

		/**
		 * Signed Long
		 */
		SL,

		/**
		 * Sequence of Items
		 */
		SQ,

		/**
		 * Signed Short
		 */
		SS,

		/**
		 * Short Text
		 */
		ST,

		/**
		 * Signed 64-bit Long
		 */
		SV,

		/**
		 * Time
		 */
		TM,

		/**
		 * Unlimited Characters
		 */
		UC,

		/**
		 * Unique Identifier (UID)
		 */
		UI,

		/**
		 * Unsigned Long
		 */
		UL,

		/**
		 * Unknown
		 */
		UN,

		/**
		 * Universal Resource Identifier or Universal Resource Locator (URI/URL)
		 */
		UR,

		/**
		 * Unsigned Short
		 */
		US,

		/**
		 * Unlimited Text
		 */
		UT,

		/**
		 * Unsigned 64-bit Long
		 */
		UV;
	}
	
	public String vrName();
	
	public int code();

	public int headerLength();

	public int paddingByte();

	public boolean isTemporalType();

	public boolean isStringType();

	public boolean useSpecificCharacterSet();

	public boolean isIntType();

	public boolean isInlineBinary();

	public int numEndianBytes();

	public byte[] toggleEndian(byte[] b, boolean preserve);

	public byte[] toBytes(Object val, SpecificCharacterSet cs);

	public Object toStrings(Object val, boolean bigEndian, SpecificCharacterSet cs);

	public String toString(Object val, boolean bigEndian, int valueIndex, String defVal);

	public int toInt(Object val, boolean bigEndian, int valueIndex, int defVal);

	public int[] toInts(Object val, boolean bigEndian);

	public long toLong(Object val, boolean bigEndian, int valueIndex, long defVal);

	public long[] toLongs(Object val, boolean bigEndian);

	public float toFloat(Object val, boolean bigEndian, int valueIndex, float defVal);

	public float[] toFloats(Object val, boolean bigEndian);

	public double toDouble(Object val, boolean bigEndian, int valueIndex, double defVal);

	public double[] toDoubles(Object val, boolean bigEndian);

	public Date toDate(Object val, TimeZone tz, int valueIndex, boolean ceil, Date defVal, DatePrecision precision);

	public Date[] toDates(Object val, TimeZone tz, boolean ceil, DatePrecisions precisions);

	public Object toValue(Date[] ds, TimeZone tz, DatePrecision precision);

	public boolean prompt(Object val, boolean bigEndian, SpecificCharacterSet cs, int maxChars, StringBuilder sb);

	public int vmOf(Object val);
}
