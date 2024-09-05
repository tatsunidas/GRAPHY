/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of graphy, hosted at https://github.com/graphy.
 *
 * The Initial Developer of the Original Code is
 * Visionary Imaging Services, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2015
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */
package com.vis.core.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import com.vis.dicom.DatePrecision;


public class DateUtils {
	
	/*
	 * DICOM "TM" format consists of a string of characters of the format hhmmss.frac;
	 * kkmmss.SSS ->  see, https://docs.oracle.com/javase/jp/8/docs/api/java/time/format/DateTimeFormatter.html
	 * where hh contains hours (range "00" - "23"), mm contains minutes (range "00" - "59"), 
	 * ss contains seconds (range "00" - "59"), 
	 * and frac contains a fractional part of a second as small as 1 millionth of a second (range 000000 - 999999).
	 */
	private static DateFormat sqlDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	//use kk instead HH for represent 24 hour.
	//DICOM TM format represent time in kk:mm:ss.SSS000. Here, last "000" is padding vales to represent milisec in 6 digits.
	//DO NOT USE kkmmss.SSSSSS. This occurs 000SSS nor SSS000(DICOM form).
	//see also DateUtils.
	private static DateFormat timeFormat = new SimpleDateFormat("kk:mm:ss.SSS");//use kk instead HH for represent 24 hour.
	

    public static final Date[] EMPTY_DATES = {};

    private static TimeZone cachedTimeZone;

    private static Calendar cal(TimeZone tz) {
        Calendar cal = (tz != null)
                ? new GregorianCalendar(tz)
                : new GregorianCalendar();
        cal.clear();
        return cal;
    }

    private static Calendar cal(TimeZone tz, Date date) {
        Calendar cal = (tz != null)
                ? new GregorianCalendar(tz)
                : new GregorianCalendar();
        cal.setTime(date);
        return cal;
    }

    private static void ceil(Calendar cal, int field) {
        cal.add(field, 1);
        cal.add(Calendar.MILLISECOND, -1);
    }

    public static String formatDA(TimeZone tz, Date date) {
        return formatDA(tz, date, new StringBuilder(8)).toString();
    }

    public static StringBuilder formatDA(TimeZone tz, Date date,
            StringBuilder toAppendTo) {
        return formatDT(cal(tz, date), toAppendTo, Calendar.DAY_OF_MONTH);
    }

    public static String formatTM(TimeZone tz, Date date) {
        return formatTM(tz, date, new DatePrecision());
    }

    public static String formatTM(TimeZone tz, Date date, DatePrecision precision) {
        return formatTM(cal(tz, date), new StringBuilder(10),
                precision.lastField).toString();
    }

    private static StringBuilder formatTM(Calendar cal, 
            StringBuilder toAppendTo, int lastField) {
        appendXX(cal.get(Calendar.HOUR_OF_DAY), toAppendTo);
        if (lastField > Calendar.HOUR_OF_DAY) {
            appendXX(cal.get(Calendar.MINUTE), toAppendTo);
            if (lastField > Calendar.MINUTE) {
                appendXX(cal.get(Calendar.SECOND), toAppendTo);
                if (lastField > Calendar.SECOND) {
                    toAppendTo.append('.');
                    appendXXX(cal.get(Calendar.MILLISECOND), toAppendTo);
                }
            }
        }
        return toAppendTo;
    }

    public static String formatDT(TimeZone tz, Date date) {
        return formatDT(tz, date, new DatePrecision());
    }

    public static String formatDT(TimeZone tz, Date date, DatePrecision precision) {
        return formatDT(tz, date, new StringBuilder(23), precision).toString();
    }

    public static StringBuilder formatDT(TimeZone tz, Date date,
            StringBuilder toAppendTo, DatePrecision precision) {
        Calendar cal = cal(tz, date);
        formatDT(cal, toAppendTo, precision.lastField);
        if (precision.includeTimezone) {
            int offset = cal.get(Calendar.ZONE_OFFSET)
                    + cal.get(Calendar.DST_OFFSET);
            appendZZZZZ(offset, toAppendTo);
        }
        return toAppendTo;
    }

    private static StringBuilder appendZZZZZ(int offset, StringBuilder sb) {
        if (offset < 0) {
            offset = -offset;
            sb.append('-');
        } else
            sb.append('+');
        int min = offset / 60000;
        appendXX(min / 60, sb);
        appendXX(min % 60, sb);
        return sb;
    }


    /**
     * Returns Timezone Offset From UTC in format {@code (+|i)HHMM} of specified
     * Timezone without concerning Daylight saving time (DST).
     *
     * @param tz Timezone
     * @return Timezone Offset From UTC in format {@code (+|i)HHMM}
     */
    public static String formatTimezoneOffsetFromUTC(TimeZone tz) {
        return appendZZZZZ(tz.getRawOffset(), new StringBuilder(5)).toString();
    }

    /**
     * Returns Timezone Offset From UTC in format {@code (+|i)HHMM} of specified
     * Timezone on specified date. If no date is specified, DST is considered
     * for the current date.
     *
     * @param tz Timezone
     * @param date Date or {@code null}
     * @return Timezone Offset From UTC in format {@code (+|i)HHMM}
     */
    public static String formatTimezoneOffsetFromUTC(TimeZone tz, Date date) {
        return appendZZZZZ(tz.getOffset(date == null 
                ? System.currentTimeMillis() : date.getTime()), 
                new StringBuilder(5)).toString();
    }

    private static StringBuilder formatDT(Calendar cal, StringBuilder toAppendTo,
            int lastField) {
        appendXXXX(cal.get(Calendar.YEAR), toAppendTo);
        if (lastField > Calendar.YEAR) {
            appendXX(cal.get(Calendar.MONTH) + 1, toAppendTo);
            if (lastField > Calendar.MONTH) {
                appendXX(cal.get(Calendar.DAY_OF_MONTH), toAppendTo);
                if (lastField > Calendar.DAY_OF_MONTH) {
                    formatTM(cal, toAppendTo, lastField);
                }
            }
        }
        return toAppendTo;
    }

    private static void appendXXXX(int i, StringBuilder toAppendTo) {
        if (i < 1000)
            toAppendTo.append('0');
        appendXXX(i, toAppendTo);
    }

    private static void appendXXX(int i, StringBuilder toAppendTo) {
        if (i < 100)
            toAppendTo.append('0');
        appendXX(i, toAppendTo);
    }

    private static void appendXX(int i, StringBuilder toAppendTo) {
        if (i < 10)
            toAppendTo.append('0');
        toAppendTo.append(i);
    }

    public static Date parseDA(TimeZone tz, String s) {
        return parseDA(tz, s, false);
    }

    public static Date parseDA(TimeZone tz, String s, boolean ceil) {
        Calendar cal = cal(tz);
        int length = s.length();
        if (!(length == 8 || length == 10 && !Character.isDigit(s.charAt(4))))
            throw new IllegalArgumentException(s);
        try {
            int pos = 0;
            cal.set(Calendar.YEAR,
                    Integer.parseInt(s.substring(pos, pos + 4)));
            pos += 4;
            if (!Character.isDigit(s.charAt(pos)))
                pos++;
            cal.set(Calendar.MONTH,
                    Integer.parseInt(s.substring(pos, pos + 2)) - 1);
            pos += 2;
            if (!Character.isDigit(s.charAt(pos)))
                pos++;
            cal.set(Calendar.DAY_OF_MONTH,
                    Integer.parseInt(s.substring(pos)));
            if (ceil)
                ceil(cal, Calendar.DAY_OF_MONTH);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(s);
        }
        return cal.getTime();
    }

    public static Date parseTM(TimeZone tz, String s, DatePrecision precision) {
        return parseTM(tz, s, false, precision);
    }

    public static Date parseTM(TimeZone tz, String s, boolean ceil,
            DatePrecision precision) {
        return parseTM(cal(tz), truncateTimeZone(s), ceil, precision);
    }

    private static String truncateTimeZone(String s) {
        int length = s.length();
        if (length > 4) {
            char sign = s.charAt(length - 5);
            if (sign == '+' || sign == '-') {
                return s.substring(0, length - 5);
            }
        }
        return s;
    }

    private static Date parseTM(Calendar cal, String s, boolean ceil,
            DatePrecision precision) {
        int length = s.length();
        int pos = 0;
        if (pos + 2 > length)
            throw new IllegalArgumentException(s);

        try {
            cal.set(precision.lastField = Calendar.HOUR_OF_DAY,
                    Integer.parseInt(s.substring(pos, pos + 2)));
            pos += 2;
            if (pos < length) {
                if (!Character.isDigit(s.charAt(pos)))
                    pos++;
                if (pos + 2 > length)
                    throw new IllegalArgumentException(s);

                cal.set(precision.lastField = Calendar.MINUTE,
                        Integer.parseInt(s.substring(pos, pos + 2)));
                pos += 2;
                if (pos < length) {
                    if (!Character.isDigit(s.charAt(pos)))
                        pos++;
                    if (pos + 2 > length)
                        throw new IllegalArgumentException(s);
                    cal.set(precision.lastField = Calendar.SECOND,
                            Integer.parseInt(s.substring(pos, pos + 2)));
                    pos += 2;
                    if (pos < length) {
                        float f = Float.parseFloat(s.substring(pos));
                        if (f >= 1 || f < 0)
                            throw new IllegalArgumentException(s);
                        cal.set(precision.lastField = Calendar.MILLISECOND, 
                                (int) (f * 1000));
                        return cal.getTime();
                    }
                }
            }
            if (ceil)
                ceil(cal, precision.lastField);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(s);
        }
        return cal.getTime();
    }

    public static Date parseDT(TimeZone tz, String s, DatePrecision precision) {
        return parseDT(tz, s, false, precision);
    }

    public static TimeZone timeZone(String s) {
        TimeZone tz;
        if (s.length() != 5 || (tz = safeTimeZone(s)) == null)
            throw new IllegalArgumentException("Illegal Timezone Offset: " + s);
        return tz;
    }

    private static TimeZone safeTimeZone(String s) {
        String tzid = tzid(s);
        if (tzid == null)
            return null;

        TimeZone tz = cachedTimeZone;
        if (tz == null || !tz.getID().equals(tzid))
            cachedTimeZone = tz = TimeZone.getTimeZone(tzid);

        return tz;
    }

    private static String tzid(String s) {
        int length = s.length();
        if (length > 4) {
            char[] tzid = { 'G', 'M', 'T', 0, 0, 0, ':', 0, 0 };
            s.getChars(length-5, length-2, tzid, 3);
            s.getChars(length-2, length, tzid, 7);
            if ((tzid[3] == '+' || tzid[3] == '-')
                    && Character.isDigit(tzid[4])
                    && Character.isDigit(tzid[5])
                    && Character.isDigit(tzid[7])
                    && Character.isDigit(tzid[8])) {
                return new String(tzid);
            }
        }
        return null;
    }

    public static Date parseDT(TimeZone tz, String s, boolean ceil,
            DatePrecision precision) {
        int length = s.length();
        TimeZone tz1 = safeTimeZone(s);
        if (precision.includeTimezone = tz1 != null) {
            length -= 5;
            tz = tz1;
        }
        Calendar cal = cal(tz);
        try {
            int pos = 0;
            if (pos + 4 > length)
                throw new IllegalArgumentException(s);
            cal.set(precision.lastField = Calendar.YEAR,
                    Integer.parseInt(s.substring(pos, pos + 4)));
            pos += 4;
            if (pos < length) {
                if (!Character.isDigit(s.charAt(pos)))
                    pos++;
                if (pos + 2 > length)
                    throw new IllegalArgumentException(s);
                cal.set(precision.lastField = Calendar.MONTH,
                        Integer.parseInt(s.substring(pos,  pos + 2)) - 1);
                pos += 2;
                if (pos < length) {
                    if (!Character.isDigit(s.charAt(pos)))
                        pos++;
                    if (pos + 2 > length)
                        throw new IllegalArgumentException(s);
                    cal.set(precision.lastField = Calendar.DAY_OF_MONTH,
                            Integer.parseInt(s.substring(pos, pos + 2)));
                    pos += 2;
                    if (pos < length)
                        return parseTM(cal, s.substring(pos, length), ceil,
                                precision);
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(s);
        }
        if (ceil)
            ceil(cal, precision.lastField);
        return cal.getTime();
    }
    
    /**
	 * 
	 * @param ymd : yyyy/MM/dd or yyyy-MM-dd or yyyyMMdd
	 * @param splitter : / or -
	 * @return yyyy/MM/dd
	 */
	public static java.util.Date toDateObj(String ymd, String splitter) {
		if(ymd == null) {
			return null;
		}
		if(splitter == null) {
			splitter ="/";
		}
		if(!splitter.equals("/") && !splitter.equals("-")) {
			splitter = "/";
		}
		ymd = ymd.trim();
		if(ymd.length() == 8) {
			//in this case, ymd does not include splitter, (yyyyMMdd)
			//add to it
			String yyyy = ymd.substring(0, 4);
			String MM = ymd.substring(4, 6);
			String dd = ymd.substring(6);
			ymd = yyyy+splitter+MM+splitter+dd;
		}else {
			if (!ymd.contains(splitter)) {
				String splitter_ ;
				if(splitter.equals("/")) {
					splitter_ = "-";
				}else {
					splitter_ = "/";
				}
				ymd = ymd.replace(splitter, splitter_);
				splitter = splitter_;
			}
		}
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy"+splitter+"MM"+splitter+"dd");
			return sdf.parse(ymd);
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * 
	 * @param time : kkmmss.SSS000 or kk:mm:ss.SSS000
	 * @param splitter
	 * @return kk:mm:ss or kk:mm:ss.SSS
	 */
	public static java.util.Date toTimeObj(String time, String splitter) {
		if(time == null || time.length()==0) {
			return null;
		}
		if(splitter == null || !splitter.equals(":")) {
			splitter =":";
		}
		time = time.trim();
		String kms = null;
		String ms = "";
		if(time.contains(".")) {
			String timeAndMilisec[] = time.split(".");
			kms = timeAndMilisec[0];
			ms = timeAndMilisec[1];
		}else {
			kms = time;
		}
		if(kms.length() == 6 /*kkmmss*/) {
			String kk = kms.substring(0, 2);
			String mm = kms.substring(2, 4);
			String ss = kms.substring(4, 6);
			kms = kk+splitter+mm+splitter+ss;
		}else if(kms.length() == 8 /*kk?mm?ss*/){
			String kk = kms.substring(0, 2);
			String mm = kms.substring(3, 5);
			String ss = kms.substring(6, 8);
			kms = kk+splitter+mm+splitter+ss;
		}
		try {
			timeFormat.setLenient(false);
			return timeFormat.parse(kms+"."+ms);
		} catch (ParseException ex) {
			return null;
		}
	}
	
	/**
	 * for derbydb java.sql.Date object.
	 * @param ymd : must to yyyy-MM-dd format for sql.Date
	 * @return
	 */
	public static java.sql.Date toSQLDateObj(String ymd) {
		if(ymd == null) {
			return null;
		}
		try {
			java.util.Date date = toDateObj(ymd, "-"); 
			java.sql.Date sqlDate = null;
			if(date != null) {
				String str = sqlDateFormat.format(date);
				sqlDate = java.sql.Date.valueOf(str);
			}
			return sqlDate;
		} catch (Exception ex) {
			return null;
		}
	}
	
	public static java.sql.Date toSQLDateObj(java.util.Date ymd) {
		if(ymd == null) {
			return null;
		}
		try {
			String str = sqlDateFormat.format(ymd);
			java.sql.Date sqlDate = java.sql.Date.valueOf(str);
			return sqlDate;
		} catch (Exception ex) {
			return null;
		}
	}
	
	public static java.sql.Time toSQLTime(String kms/*kk:mm:ss.SSS*/, String splitter) {
		if(kms == null) {
			return null;
		}
		try {
			java.util.Date time = toTimeObj(kms, splitter);
			java.sql.Time sqlTime = null;
			if(time != null) {
				String str = timeFormat.format(time);
				sqlTime = java.sql.Time.valueOf(str);
			}
			return sqlTime;
		} catch (Exception ex) {
			return null;
		}
	}
	
	public static java.sql.Time toSQLTime(java.util.Date time) {
		if(time == null) {
			return null;
		}
		try {
			java.sql.Time sqlTime = new java.sql.Time(time.getTime());
			return sqlTime;
		} catch (Exception ex) {
			return null;
		}
	}
}
