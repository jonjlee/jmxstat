/*
 * Copyright (c) 2011 Jonathan Lee
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *   Jonathan Lee
 *   Benoit Delbosc
 *
 */
package net.jlee.jmxstat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Main {

    private static final int DEFAULT_INTERVAL = 5;

    private static final int MAX_RETRY = 5;

    private static final String DATE_FORMAT_NOW = "HH:mm:ss";

    private static final String THREADING_OBJ_NAME = "java.lang:type=Threading";

    private static final String MEMORY_OBJ_NAME = "java.lang:type=Memory";

    private static final String GC = "gc";

    private static final String CONTENTION_ATTR = "ThreadContentionMonitoringEnabled";

    private static final String DUMP_ALL_THREADS = "dumpAllThreads";

    private static final String BLOCKED_TIME = "blockedTime";

    private static final String BLOCKED_COUNT = "blockedCount";

    private static class Options {

        private static final String CONTENTION_ARG = "--contention";

        private static final String DISABLE_CONTENTION_ARG = "--disable-contention";

        private static final Object PERFORM_GC_ARG = "--performGC";

        String url;

        int interval;

        int count = 0;

        boolean contention = false;

        boolean disableContention = false;

        boolean performGC = false;

        List<MbeanAttr> attrs = null;

        public Options(String[] args) {
            url = args[0];
            // Check for the last 2 args if they are interval/count
            int i = 0;
            int c = 0;
            try {
                c = Integer.parseInt(args[args.length - 1]);
                i = Integer.parseInt(args[args.length - 2]);
            } catch (Exception e) {
            }
            if (i > 0) {
                count = c;
                interval = i;
            } else if (c > 0) {
                interval = c;
            } else {
                interval = DEFAULT_INTERVAL;
            }

            attrs = new ArrayList<MbeanAttr>();
            for (i = 1; i < args.length; i++) {
                String def = args[i];
                if (CONTENTION_ARG.equals(def)) {
                    contention = true;
                    disableContention = false;
                    continue;
                }
                if (DISABLE_CONTENTION_ARG.equals(def)) {
                    disableContention = true;
                    contention = false;
                    continue;
                }
                else if (PERFORM_GC_ARG.equals(def)) {
                    performGC = true;
                    continue;
                }
                int startIdx = def.indexOf("[");
                int endIdx = def.lastIndexOf("]");
                if (startIdx >= 0 && endIdx > startIdx) {
                    ObjectName objectName;
                    try {
                        objectName = new ObjectName(def.substring(0, startIdx));
                    } catch (Exception e) {
                        error("Invalid mbean attribute list - " + e.toString(),
                                1);
                        return;
                    }
                    String[] items = def.substring(startIdx + 1, endIdx).split(
                            ",");
                    for (String attr : items) {
                        int subIdx = attr.indexOf(".");
                        if (subIdx >= 0) {
                            attrs.add(new MbeanAttr(objectName, attr.substring(
                                    0, subIdx), attr.substring(subIdx + 1)));
                        } else {
                            attrs.add(new MbeanAttr(objectName, attr));
                        }
                    }
                }
            }
        }
    }

    private static class MbeanAttr {
        ObjectName name;

        String attr;

        String subAttr;

        public MbeanAttr(ObjectName name, String attr) {
            this(name, attr, null);
        }

        public MbeanAttr(ObjectName name, String attr, String subAttr) {
            this.name = name;
            this.attr = attr;
            this.subAttr = subAttr;
        }
    }

    private static void error(String msg, int err) {
        System.err.println(msg);
        System.exit(err);
    }

    private static void usage() {
        error("Usage:\n" //
                + "java -jar jmxstat.jar <host:port> "
                + "[--peformGC] [--contention] [mbean.name[attribute.field], ...] [interval [count]]", 1);
    }

    private static String now() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        return sdf.format(cal.getTime());
    }

    private static MBeanServerConnection connect(String url) throws IOException {
        JMXServiceURL jmxUrl = new JMXServiceURL(
                "service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(jmxUrl, null);
        return jmxc.getMBeanServerConnection();

    }

    /**
     * Enable or disable the thread contention monitoring.
     *
     */
    private static void setContentionMonitoring(MBeanServerConnection mbsc,
            Boolean value) throws MalformedObjectNameException,
            NullPointerException, AttributeNotFoundException,
            InstanceNotFoundException, MBeanException, ReflectionException,
            IOException, InvalidAttributeValueException {
        ObjectName objectName = new ObjectName(THREADING_OBJ_NAME);
        Object val = mbsc.getAttribute(objectName, CONTENTION_ATTR);
        if (value.toString().equals(val.toString())) {
            // already set
            return;
        }
        Attribute attribute = new Attribute(CONTENTION_ATTR, value);
        mbsc.setAttribute(objectName, attribute);
        // System.out.println(CONTENTION_ATTR + ":" + val.toString() + " to "
        //        + value.toString());
    }

    /**
     * Write sum of blockedCount and blocketTime for all threads.
     *
     * blockedCount: Returns the total number of times that the thread
     * associated with this ThreadInfo blocked to enter or reenter a monitor.
     *
     * blockedTime: Returns the approximate accumulated elapsed time (in
     * milliseconds) that the thread associated with this ThreadInfo has blocked
     * to enter or reenter a monitor since thread contention monitoring is
     * enabled.
     *
     * */
    private static void writeContentionInfo(MBeanServerConnection mbsc,
            StringBuilder out) throws MalformedObjectNameException,
            NullPointerException, InstanceNotFoundException, MBeanException,
            ReflectionException, IOException {
        ObjectName objectName = new ObjectName(THREADING_OBJ_NAME);
        Object[] params = { new Boolean(true), new Boolean(true) };
        String[] signature = { "boolean", "boolean" };
        Object[] threads = (Object[]) mbsc.invoke(objectName, DUMP_ALL_THREADS,
                params, signature);
        Long blockedTime = 0L;
        Long blockedCount = 0L;
        for (Object thread : threads) {
            Long l = (Long) ((CompositeData) thread).get(BLOCKED_TIME);
            blockedTime += l;
            l = (Long) ((CompositeData) thread).get(BLOCKED_COUNT);
            blockedCount += l;
        }
        out.append(blockedCount.toString());
        out.append("\t");
        out.append(blockedTime.toString());
        out.append("\t");
    }

    private static void performGC(MBeanServerConnection mbsc)
            throws MalformedObjectNameException, NullPointerException,
            InstanceNotFoundException, MBeanException, ReflectionException,
            IOException {
        ObjectName objectName = new ObjectName(MEMORY_OBJ_NAME);
        Object[] threads = (Object[]) mbsc.invoke(objectName, GC, null, null);
    }

    public static void main(String[] args) throws Exception {
        boolean retryEnabled = false;
        int retry = 0;

        Options options = new Options(args);
        StringBuilder out = new StringBuilder();
        // header
        out.append("date\t");
        for (MbeanAttr attr : options.attrs) {
            out.append(attr.attr);
            if (attr.subAttr != null) {
                out.append(".").append(attr.subAttr);
            }
            out.append("\t");
        }
        if (options.contention) {
            out.append("blockedCount\tblockedTimeMs\t");
        }
        String header = out.toString();
        System.out.println(header);
        int i = 0;
        for (;;) {
            try {
                // Connect to JMX server
                MBeanServerConnection mbsc = connect(options.url);
                retryEnabled = true; // only allow retries after first
                if (options.contention) {
                    setContentionMonitoring(mbsc, true);
                }
                if (options.disableContention) {
                    setContentionMonitoring(mbsc, false);
                }
                if (options.performGC) {
                    performGC(mbsc);
                }
                // successful connection
                if (! options.contention && options.attrs.size() == 0) {
                    return;
                }
                for (;;) {
                    out = new StringBuilder();
                    out.append(now());
                    out.append("\t");
                    // Read each mbean attribute specified on the command line
                    for (MbeanAttr attr : options.attrs) {
                        Object val = mbsc.getAttribute(attr.name, attr.attr);

                        // Read fields from CompositeData attributes if user
                        // specified a sub-attribute
                        // via dot notation (e.g.
                        // java.lang:type=Memory[HeapMemoryUsage.max])
                        if (attr.subAttr != null
                                && val instanceof CompositeData) {
                            val = ((CompositeData) val).get(attr.subAttr);
                        }
                        out.append(val);
                        out.append("\t");
                    }
                    if (options.contention) {
                        writeContentionInfo(mbsc, out);
                    }
                    System.out.println(out.toString());
                    i++;
                    if (options.count > 0 && i >= options.count) {
                        return;
                    }
                    Thread.sleep(options.interval * 1000);
                    retry = 0; // reset exponential retry back-off after a
                    // successful read
                }
            } catch (IOException e) {
                if (retryEnabled && retry < MAX_RETRY) {
                    // Exponential retry back-off
                    int sleep = Math.min(30, (int) Math.pow(2, ++retry));
                    System.err.println("Communcation error. Retry [" + retry
                            + "/" + MAX_RETRY + "] in " + sleep + "s.\n" + e);
                    Thread.sleep(sleep * 1000);
                } else {
                    error("Error reading from " + options.url + "\n" + e, 2);
                }
            }
        }
    }
}