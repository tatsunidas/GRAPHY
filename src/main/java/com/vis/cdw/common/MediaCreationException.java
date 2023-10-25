package com.vis.cdw.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 28.06.2004
 *
 */
public class MediaCreationException extends Exception {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final String statusInfo;
    private final List<String> failedSOPInstances;

    public MediaCreationException(String statusInfo) {
        super();
        this.statusInfo = statusInfo;
        this.failedSOPInstances = null;
    }

    public MediaCreationException(String statusInfo, String message) {
        super(message);
        this.statusInfo = statusInfo;
        this.failedSOPInstances = null;
    }

    public MediaCreationException(String statusInfo, List<String> failedSOPInstances) {
        super();
        this.statusInfo = statusInfo;
        this.failedSOPInstances = Collections.unmodifiableList(new ArrayList(failedSOPInstances));
    }

    public MediaCreationException(String statusInfo, String message, List<String> failedSOPInstances) {
        super(message);
        this.statusInfo = statusInfo;
        this.failedSOPInstances = Collections.unmodifiableList(new ArrayList(failedSOPInstances));
    }

    public MediaCreationException(String statusInfo, Throwable cause) {
        super(cause);
        this.statusInfo = statusInfo;
        this.failedSOPInstances = null;
    }

    public MediaCreationException(String statusInfo, String message,
            Throwable cause) {
        super(message, cause);
        this.statusInfo = statusInfo;
        this.failedSOPInstances = null;
    }

    public final String getStatusInfo() {
        return statusInfo;
    }

    public final List<String> getFailedSOPInstances() {
        return failedSOPInstances;
    }
}
