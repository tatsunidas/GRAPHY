package com.vis.dicom;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import com.vis.core.util.ByteUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class UIDUtils {

    /**
     * UID root for UUIDs (Universally Unique Identifiers) generated in
     * accordance with Rec. ITU-T X.667 | ISO/IEC 9834-8.
     * @see &lt;a href="http://www.oid-info.com/get/2.25">OID repository {joint-iso-itu-t(2) uuid(25)}$lt;/a>
     */
    private static final String UUID_ROOT = "2.25";

    private static final Pattern PATTERN =
            Pattern.compile("[012]((\\.0)|(\\.[1-9]\\d*))+");

    private static final Charset ASCII = Charset.forName("US-ASCII");

    private static String root = UUID_ROOT;

    public static final String getRoot() {
        return root;
    }

    public static final void setRoot(String root) {
        checkRoot(root);
        UIDUtils.root = root;
    }

    private static void checkRoot(String root) {
        if (root.length() > 24)
            throw new IllegalArgumentException("root length > 24");
        if (!isValid(root))
            throw new IllegalArgumentException(root);
    }

    public static boolean isValid(String uid) {
        return uid.length() <= 64 && PATTERN.matcher(uid).matches();
    }

    public static String createUID() {
        return randomUID(root);
    }

    public static String createNameBasedUID(byte[] name) {
        return nameBasedUID(name, root);
    }

    public static String createNameBasedUID(byte[] name, String root) {
        checkRoot(root);
        return nameBasedUID(name, root);
    }

    public static String createUID(String root) {
        checkRoot(root);
        return randomUID(root);
    }

    public static String createUIDIfNull(String uid) {
        return uid == null ? randomUID(root) : uid;
    }

    public static String createUIDIfNull(String uid, String root) {
        checkRoot(root);
        return uid == null ? randomUID(root) : uid;
    }

    public static String remapUID(String uid) {
        return nameBasedUID(uid.getBytes(ASCII), root);
    }

    public static String remapUID(String uid, String root) {
        checkRoot(root);
        return nameBasedUID(uid.getBytes(ASCII), root);
    }

    private static String randomUID(String root) {
        return toUID(root, UUID.randomUUID());
    }

    private static String nameBasedUID(byte[] name, String root) {
        return toUID(root, UUID.nameUUIDFromBytes(name));
    }

    private static String toUID(String root, UUID uuid) {
        byte[] b17 = new byte[17];
        ByteUtils.longToBytesBE(uuid.getMostSignificantBits(), b17, 1);
        ByteUtils.longToBytesBE(uuid.getLeastSignificantBits(), b17, 9);
        String uuidStr = new BigInteger(b17).toString();
        int rootlen = root.length();
        int uuidlen = uuidStr.length();
        char[] cs = new char[rootlen + uuidlen + 1];
        root.getChars(0, rootlen, cs, 0);
        cs[rootlen] = '.';
        uuidStr.getChars(0, uuidlen, cs, rootlen + 1);
        return new String(cs);
    }

    public static StringBuilder promptTo(String uid, StringBuilder sb) {
        return sb.append(uid).append(" - ").append(UID.nameOf(uid));
    }

    public static String[] findUIDs(String regex) {
        Pattern p = Pattern.compile(regex);
        Field[] fields = UID.class.getFields();
        String[] uids = new String[fields.length];
        int j = 0;
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (p.matcher(field.getName()).matches())
                try {
                    uids[j++] = (String) field.get(null);
                } catch (Exception ignore) { }
        }
        return Arrays.copyOf(uids, j);
    }
}

