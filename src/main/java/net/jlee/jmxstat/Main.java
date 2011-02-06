package net.jlee.jmxstat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Main {
    
    public static int MAX_RETRY = 5;
    
    private static class MbeanAttr {
        ObjectName name;
        String attr;
        String subAttr;
        public MbeanAttr(ObjectName name, String attr) { this(name, attr, null); }
        public MbeanAttr(ObjectName name, String attr, String subAttr) { this.name = name; this.attr = attr; this.subAttr = subAttr; }
    }

    private static void error(String msg, int err) {
        System.err.println(msg);
        System.exit(err);
    }

    private static void usage() {
        error("Usage:\n" +
              "   java -jar jmxstat.jar <host:port> [mbean.name[attribute.field], ...] [interval]", 
              1);
    }

    // Parse the list of mbean attributes from the command line (arguments 2 and on)   
    private static List<MbeanAttr> parseMbeans(String[] args) throws MalformedObjectNameException, NullPointerException {
        List<MbeanAttr> attrList = new ArrayList<MbeanAttr>();
        for (int i = 1; i < args.length; i++) {
            String def = args[i];
            int startIdx = def.indexOf("[");
            int endIdx = def.lastIndexOf("]");
            if (startIdx >= 0 && endIdx > startIdx) {
                ObjectName objectName = new ObjectName(def.substring(0, startIdx));
                String[] attrs = def.substring(startIdx+1, endIdx).split(",");
                for (String attr : attrs) {
                    int subIdx = attr.indexOf(".");
                    if (subIdx >= 0) {
                        attrList.add(new MbeanAttr(objectName, attr.substring(0, subIdx), attr.substring(subIdx+1)));
                    } else {
                        attrList.add(new MbeanAttr(objectName, attr));
                    }
                }
            }
        }
        return attrList;
    }
    
    private static MBeanServerConnection connect(String url) throws IOException {
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(jmxUrl, null);
        return jmxc.getMBeanServerConnection();
    }

    public static void main(String[] args) throws Exception {

        boolean retryEnabled = false;
        int retry = 0;
        int refresh = 5;
        List<MbeanAttr> attrs = null;
        
        // Parse arguments
        if (args.length < 1) { usage(); }
        String url = args[0];
        try {
            refresh = Integer.parseInt(args[args.length-1]);
        } catch (Exception e) { /* nop */ }
        try {
            attrs = parseMbeans(args);
        } catch (Exception e) {
            error("Invalid mbean attribute list - " + e.toString(), 1);
        }
        
        for (;;) {
            try {
                // Connect to JMX server
                MBeanServerConnection mbsc = connect(url);
                retryEnabled = true;  // only allow retries after first successful connection
            
                for (;;) {
                    StringBuilder out = new StringBuilder(); 

                    // Read each mbean attribute specified on the command line
                    for (MbeanAttr attr : attrs) {
                        out.append(attr.attr);
                        Object val = mbsc.getAttribute(attr.name, attr.attr);

                        // Read fields from CompositeData attributes if user specified a sub-attribute 
                        // via dot notation (e.g. java.lang:type=Memory[HeapMemoryUsage.max])
                        if (attr.subAttr != null && val instanceof CompositeData) {
                            out.append(".").append(attr.subAttr);
                            val = ((CompositeData) val).get(attr.subAttr);
                        }
                        
                        out.append("=").append(val).append(" ");
                    }
                    
                    System.out.println(out.toString());
                    Thread.sleep(refresh * 1000);
                    retry = 0;  // reset exponential retry back-off after a successful read 
                }
            } catch (IOException e) {
                if (retryEnabled && retry < MAX_RETRY) {
                    // Exponential retry back-off
                    int sleep = Math.min(30, (int) Math.pow(2, ++retry));
                    System.err.println("Communcation error. Retry [" + retry + "/" + MAX_RETRY + "] in " + sleep + "s.\n" + e);
                    Thread.sleep(sleep * 1000);
                } else {
                    error("Error reading from " + url + "\n" + e, 2);
                }
            }
        }
    }
}