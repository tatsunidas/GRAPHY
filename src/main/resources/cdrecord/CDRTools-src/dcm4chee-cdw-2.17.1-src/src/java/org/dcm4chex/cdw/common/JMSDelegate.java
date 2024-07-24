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
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
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
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chex.cdw.common;

import java.util.Hashtable;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.logging.Logger;

/**
 * @author gunter.zeilinter@tiani.com
 * @version $Revision: 4265 $ $Date: 2005-10-06 22:01:30 +0200 (Do, 06. Okt 2005) $
 * @since 23.06.2004
 *
 */
public class JMSDelegate {

    static final String CONNECTION_FACTORY = "java:ConnectionFactory";

    /**
     * JBoss-vendor specific property for scheduling a JMS message. In
     * milliseconds since January 1, 1970.
     */
    public static final String PROPERTY_SCHEDULED_DELIVERY = "JMS_JBOSS_SCHEDULED_DELIVERY";
    
    public static void startListening(String name, MessageListener listener) {
        if (map.containsKey(name))
            throw new IllegalStateException("Already listening on queue " + name);
        map.put(name, new JMSDelegate(name, listener));
    }

    public static void stopListening(String name) {
        JMSDelegate jms = (JMSDelegate) map.remove(name);
        if (jms == null)
            throw new IllegalStateException("No listener on queue " + name);
        jms.close();
    }
    
    public static void queue(String name, String prompt, Logger log, 
            MediaCreationRequest rq, long scheduledTime) throws JMSException {
        JMSDelegate jms = (JMSDelegate) map.get(name);
        if (jms == null)
            throw new IllegalStateException("No listener on queue " + name);
        jms.queue(prompt, log, rq, scheduledTime);
    }
    
    
    static int toJMSPriority(String dcmPriority) {
        if (dcmPriority.equals(Priority.LOW)) return 3;
        if (dcmPriority.equals(Priority.HIGH)) return 5;
        return 4;
    }

    private static Hashtable map = new Hashtable();

    private final QueueConnection conn;

    private final Queue queue;

    private JMSDelegate(String name, MessageListener listener) {
        InitialContext iniCtx = null;
        QueueConnectionFactory qcf = null;
        try {
            iniCtx = new InitialContext();
	        qcf = (QueueConnectionFactory) iniCtx.lookup(CONNECTION_FACTORY);
            queue = (Queue) iniCtx.lookup("queue/" + name);
            conn = qcf.createQueueConnection();
        } catch (NamingException e) {
            throw new ConfigurationException(e);
        } catch (JMSException e) {
            throw new ConfigurationException(e);
        } finally {
            if (iniCtx != null) {
                try {
                    iniCtx.close();
                } catch (Exception ignore) {
                }
            }
        }
        try {
            QueueSession session = conn.createQueueSession(false,
                    QueueSession.AUTO_ACKNOWLEDGE);
            QueueReceiver receiver = session.createReceiver(queue);
            receiver.setMessageListener(listener);
            conn.start();
        } catch (JMSException e) {
            close();
        }
    }
    
    private void close() {
        try {
            conn.close();
        } catch (Exception ignore) {
        }
    }

    private void queue(String prompt, Logger log, MediaCreationRequest rq, long scheduledTime)
            throws JMSException {
        QueueSession session = null;
        QueueSender send = null;
        log.info(prompt);
        try {
            session = conn.createQueueSession(false,
                    QueueSession.AUTO_ACKNOWLEDGE);
            send = session.createSender(queue);
            ObjectMessage msg = session.createObjectMessage(rq);
            if (scheduledTime > 0L)
                msg.setLongProperty(PROPERTY_SCHEDULED_DELIVERY, scheduledTime);
            send.send(msg, DeliveryMode.PERSISTENT, toJMSPriority(rq
                    .getPriority()), 0);
        } catch (JMSException e) {
            log.error("Failed: " + prompt, e);
            throw e;
        } finally {
            if (send != null) {
                try {
                    send.close();
                } catch (Exception ignore) {
                }
            }
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
