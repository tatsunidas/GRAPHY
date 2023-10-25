package com.vis.cdw.common;


/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 25.06.2004
 *
 */
public class Priority {

    public static final String HIGH = "HIGH";

    public static final String MED = "MED";

    public static final String LOW = "LOW";

    public static boolean isValid(String s) {
        return s.equals(LOW) || s.equals(MED) || s.equals(HIGH);
    }
    
    private Priority() {};
}
