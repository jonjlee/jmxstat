package net.jlee.jmxstat;

import java.io.IOException;
import java.lang.management.MemoryMXBean;
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
    
    private static class MbeanAttr {
        ObjectName name;
        String attr;
        String subAttr;
        public MbeanAttr(ObjectName name, String attr) { this(name, attr, null); }
        public MbeanAttr(ObjectName name, String attr, String subAttr) { this.name = name; this.attr = attr; this.subAttr = subAttr; }
    }

    private static void error(String msg, int err) {
        System.out.println(msg);
        System.exit(err);
    }

    private static void usage() {
        error("Usage:\n" +
              "   java -jar jmxstat.jar <host:port> [mbean.name[attribute.field], ...] [interval]", 
              1);
    }

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
    
    public static void main(String[] args) throws Exception {

        int refresh = 5;
        MBeanServerConnection mbsc = null;
        List<MbeanAttr> attrs = null;
        MemoryMXBean mbean = null;
        
        // Parse arguments
        if (args.length < 1) { usage(); }
        try {
            refresh = Integer.parseInt(args[args.length-1]);
        } catch (Exception e) { /* nop */ }
        try {
            attrs = parseMbeans(args);
        } catch (Exception e) {
            error("Invalid mbean attribute list - " + e.toString(), 1);
        }
        
        // Connect to JMX server
        try {
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + args[0] + "/jmxrmi");
            JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
            mbsc = jmxc.getMBeanServerConnection();
        } catch (IOException e) {
            error("Error connecting to " + args[0] + ": " + e.toString(), 2);
        }
 
        // Poll mbean attributes
        for (;;) {
            StringBuilder out = new StringBuilder(); 
            for (MbeanAttr attr : attrs) {
                out.append(attr.attr);
                Object val = mbsc.getAttribute(attr.name, attr.attr);
                if (attr.subAttr != null && val instanceof CompositeData) {
                    out.append(".").append(attr.subAttr);
                    val = ((CompositeData) val).get(attr.subAttr);
                }
                out.append("=").append(val).append(" ");
            }
            System.out.println(out.toString());
            Thread.sleep(refresh * 1000);
        }
    }
}