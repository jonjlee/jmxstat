/*
 * Copyright (c) 2011 Jonathan Lee
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Contributors: 
 *   Jonathan Lee 
 *   Benoit Delbosc
 */
package net.jlee.jmxstat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.management.Attribute;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Main {

    private static final int MAX_RETRY = 5;

    private static final SimpleDateFormat DATE_FORMAT_NOW = new SimpleDateFormat("HH:mm:ss");
    private static final String THREADING_OBJ_NAME = "java.lang:type=Threading";
    private static final String MEMORY_OBJ_NAME = "java.lang:type=Memory";
    private static final String GC = "gc";
    private static final String CONTENTION_ATTR = "ThreadContentionMonitoringEnabled";
    private static final String DUMP_ALL_THREADS = "dumpAllThreads";
    private static final String BLOCKED_TIME = "blockedTime";
    private static final String BLOCKED_COUNT = "blockedCount";

    private static class ContentionInfo {
        long blockedTime = 0;
        long blockedCount = 0;
    }

    private static void error(String msg, int err) {
        System.err.println(msg);
        System.exit(err);
    }

    private static void usage(String err) {
        error(err + "\n" +
              "\n" +
              "Usage:\n" +
              "\n" +
              "   jmxstat <host:port> [--peformGC|--contention|--disable-contention] [mbean.name[attribute.field], ...] [interval [count]]"
           , 1);
    }

    private static String now() {
        return DATE_FORMAT_NOW.format(Calendar.getInstance().getTime());
    }

    private static MBeanServerConnection connect(String url) throws IOException {
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(jmxUrl, null);
        return jmxc.getMBeanServerConnection();
    }

    /** Invoke garbage collection function */
    private static void performGC(MBeanServerConnection mbsc) throws Exception {
        mbsc.invoke(new ObjectName(MEMORY_OBJ_NAME), GC, null, null);
    }

    /** Enable or disable the thread contention monitoring. */
    private static void setContentionMonitoring(MBeanServerConnection mbsc, Boolean value) throws Exception {
        ObjectName objectName = new ObjectName(THREADING_OBJ_NAME);
        Object val = mbsc.getAttribute(objectName, CONTENTION_ATTR);
        if (val != null && value.toString().equals(val.toString()))
            return;

        Attribute attribute = new Attribute(CONTENTION_ATTR, value);
        mbsc.setAttribute(objectName, attribute);
    }

    /**
     * Return sum of blockedCount and blockedTime for all threads.
     * 
     * @param blockedCount
     *            Returns the total number of times that the thread associated
     *            with this ThreadInfo blocked to enter or reenter a monitor.
     * 
     * @param blockedTime
     *            Returns the approximate accumulated elapsed time (in
     *            milliseconds) that the thread associated with this ThreadInfo
     *            has blocked to enter or reenter a monitor since thread
     *            contention monitoring was enabled.
     */
    private static ContentionInfo readContentionInfo(MBeanServerConnection mbsc) throws Exception {
        ObjectName objectName = new ObjectName(THREADING_OBJ_NAME);
        Object[] params = { new Boolean(true), new Boolean(true) };
        String[] signature = { "boolean", "boolean" };
        Object[] threads = (Object[]) mbsc.invoke(objectName, DUMP_ALL_THREADS, params, signature);
        ContentionInfo ci = new ContentionInfo();
        for (Object thread : threads) {
            ci.blockedTime += (Long) ((CompositeData) thread).get(BLOCKED_TIME);
            ci.blockedCount += (Long) ((CompositeData) thread).get(BLOCKED_COUNT);
        }
        return ci;
    }

    public static void main(String[] args) throws Exception {

        // Parse command line args
        Options options = Options.parse(args);
        if (options.error != null)
            usage(options.error);

        // Connect to JMX server
        MBeanServerConnection mbsc = null;
        try {
            mbsc = connect(options.url);
        } catch (Exception e) {
            error("Error connecting to " + options.url + "\n" + e, 2);
        }

        // Invoke garbage collection
        if (options.performGC) {
            try {
                performGC(mbsc);
                System.out.println(now());
            } catch (Exception e) {
                error("Error performing GC\n" + e, 3);
            }
        }

        // Enable/disable thread contention monitoring
        if (options.contention || options.disableContention) {
            try {
                setContentionMonitoring(mbsc, options.contention);
            } catch (Exception e) {
                error("Error setting contention montoring\n" + e, 3);
            }
        }

        if (!options.contention && options.attrs.size() == 0) {
            return;
        }

        // Print column headers
        StringBuilder header = new StringBuilder();
        header.append("time");
        for (MbeanAttr attr : options.attrs) {
            header.append("\t").append(attr.attr);
            if (attr.subAttr != null) {
                header.append(".").append(attr.subAttr);
            }
        }
        if (options.contention) {
            header.append("\tblockedCount\tblockedTimeMs");
        }
        System.out.println(header.toString());

        // Poll mbean values
        int retry = 0;
        int i = 0;
        for (;;) {
            StringBuilder line = new StringBuilder(now());

            try {
                // Read each mbean attribute specified on the command line
                for (MbeanAttr attr : options.attrs) {
                    Object val = mbsc.getAttribute(attr.name, attr.attr);

                    // Read fields from CompositeData attributes if user specified a sub-attribute
                    // via dot notation (e.g. java.lang:type=Memory[HeapMemoryUsage.max])
                    if (attr.subAttr != null && val instanceof CompositeData) {
                        val = ((CompositeData) val).get(attr.subAttr);
                    }

                    line.append('\t').append(val);
                }

                // Aggregate and print thread contention information
                if (options.contention) {
                    ContentionInfo ci = readContentionInfo(mbsc);
                    line.append("\t").append(ci.blockedCount).append("\t").append(ci.blockedTime);
                }

                System.out.println(line.toString());

                if (options.count > 0 && i++ >= options.count-1)
                    return;

                Thread.sleep(options.interval * 1000);
                retry = 0; // reset exponential retry back-off after a successful read

            } catch (IOException e) {
                if (retry < MAX_RETRY) {
                    // Exponential retry back-off
                    int sleep = Math.min(30, (int) Math.pow(2, ++retry));
                    System.err.println("Communcation error. Retry [" + retry + "/" + MAX_RETRY + "] in " + sleep +
                                       "s.\n" + e);
                    Thread.sleep(sleep * 1000);
                } else {
                    error("Error reading from " + options.url + "\n" + e, 2);
                }
            }
        }
    }
}
