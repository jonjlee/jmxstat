package net.jlee.jmxstat;

import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

public class Options {

    public static final int DEFAULT_INTERVAL = 5;
    public static final String CONTENTION_ARG = "--contention";
    public static final String DISABLE_CONTENTION_ARG = "--disable-contention";
    public static final String PERFORM_GC_ARG = "--performGC";

    public String error = null;
    public String url = null;
    public int interval = DEFAULT_INTERVAL;
    public int count = 0;
    public boolean contention = false;
    public boolean disableContention = false;
    public boolean performGC = false;
    public List<MbeanAttr> attrs = new ArrayList<MbeanAttr>();

    public static Options parse(String[] args) {
        Options options = new Options();

        if (args.length < 1) {
            options.error = "Java process URL must be specified.";
            return options;
        }

        options.url = args[0];

        // Check for the last 2 args if they are interval/count
        Integer i = null, c = null;
        try {
            c = Integer.parseInt(args[args.length - 1]);
            i = Integer.parseInt(args[args.length - 2]);
        } catch (Exception e) { /* nop */}
        if (i != null) {
            options.interval = i;
            options.count = c;
        } else if (c != null) {
            options.interval = c;
        }

        for (i = 1; i < args.length; i++) {
            String def = args[i];

            if (CONTENTION_ARG.equals(def)) {
                options.contention = true;
                options.disableContention = false;
                continue;
            } else if (DISABLE_CONTENTION_ARG.equals(def)) {
                options.disableContention = true;
                options.contention = false;
                continue;
            } else if (PERFORM_GC_ARG.equals(def)) {
                options.performGC = true;
                continue;
            }

            // Try to parse argument as an mbean attribute
            int startIdx = def.indexOf("[");
            int endIdx = def.lastIndexOf("]");
            if (startIdx >= 0 && endIdx > startIdx) {
                ObjectName objectName;
                try {
                    objectName = new ObjectName(def.substring(0, startIdx));
                } catch (Exception e) {
                    options.error = "Invalid mbean attribute list - " + e.toString() + "\n";
                    return options;
                }
                String[] items = def.substring(startIdx + 1, endIdx).split(",");
                for (String attr : items) {
                    int subIdx = attr.indexOf(".");
                    if (subIdx >= 0) {
                        options.attrs.add(new MbeanAttr(objectName, attr.substring(0, subIdx),
                                                        attr.substring(subIdx + 1)));
                    } else {
                        options.attrs.add(new MbeanAttr(objectName, attr));
                    }
                }
            }
        }

        return options;
    }
}