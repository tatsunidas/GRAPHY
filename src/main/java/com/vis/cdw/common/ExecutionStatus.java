package com.vis.cdw.common;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 23.06.2004
 *
 */
public class ExecutionStatus {

    public static final String IDLE = "IDLE";

    public static final String PENDING = "PENDING";

    public static final String CREATING = "CREATING";

    public static final String DONE = "DONE";

    public static final String FAILURE = "FAILURE";

    private ExecutionStatus() {
    }
}
