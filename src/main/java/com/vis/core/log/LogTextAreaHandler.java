package com.vis.core.log;

import java.util.logging.*;
import javax.swing.JTextArea;

/**
 * https://docs.oracle.com/javase/jp/12/core/java-logging-overview.html#GUID-84971801-F327-4F96-8F35-DA4D6737F857
 * @author tatsunidas
 *
 */
public class LogTextAreaHandler extends StreamHandler {
	
    JTextArea textArea = null;

    public void setTextArea(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();

        if (textArea != null) {
            textArea.append(getFormatter().format(record));
        }
    }
}
