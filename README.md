

NAME

    jmxstat - Poll JMX attributes

SYNOPSIS

    java -jar jmxstat.jar host:port [mbean-attributes...] [interval]

DESCRIPTION

    jmxstat connects to a remote JMX server via RMI and reads MBean attributes
    at a regular interval. Authentication is not supported.
    
    jmxstat supports these arguments:
    
    host:port           Host and port of the JMX enabled process to connect to
    
    mbean-attributes    Space separated list of mbean names and attributes to
                        query in the following format:
    
                            mbeanDomain:mbeanKey=mbeanValues,...[mbeanAttribute1[.field],...]

    interval            Pause _interval_ seconds between each query

EXAMPLES

    **Precondition**. These examples assume a JMX enabled process is listening
    on localhost:9999. To use jmxstat itself as the monitored process:
    
        java -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authentice=false -Dcom.sun.management.jmxremote.ssl=false -jar jmxstat.jar localhost:9999

    Display the number of loaded classes (every 5 seconds, by default):
    
        java -jar jmxstat.jar localhost:9999 java.lang:type=ClassLoading[LoadedClassCount]
    
    Display heap usage and thread count every 2 seconds:
    
        java -jar jmxstat.jar localhost:9999 java.lang:type=Memory[HeapMemoryUsage.max,HeapMemoryUsage.committed,HeapMemoryUsage.used] java.lang:type=Threading[ThreadCount] 2