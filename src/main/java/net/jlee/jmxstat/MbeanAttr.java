package net.jlee.jmxstat;

import javax.management.ObjectName;

public class MbeanAttr {
    public ObjectName name;
    public String attr;
    public String subAttr;

    public MbeanAttr(ObjectName name, String attr) {
        this(name, attr, null);
    }

    public MbeanAttr(ObjectName name, String attr, String subAttr) {
        this.name = name;
        this.attr = attr;
        this.subAttr = subAttr;
    }
}
