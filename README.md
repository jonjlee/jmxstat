NAME

    jmxstat - Poll JMX attributes and more

SYNOPSIS

    jmxstat <host:port> [--peformGC|--contention|--disable-contention] [mbean.name[attribute.field], ...] [interval [count]] 

        or

    java -jar jmxstat-VERSION.jar <host:port> [--peformGC|--contention|--disable-contention] [mbean.name[attribute.field], ...] [interval [count]]

DESCRIPTION

    jmxstat connects to a remote JMX server via RMI and reads MBean attributes
    at a regular interval. Authentication is not supported.
    
    jmxstat supports these arguments:
    
    host:port            Host and port of the JMX enabled process to connect to
    
    mbean-attributes     Space separated list of mbean names and attributes to
                         query in the following format:
    
                            mbeanDomain:mbeanKey=mbeanValues,...[mbeanAttribute1[.field],...]

    --contention         Render blockedCount and blockedTime for all threads
                         * blockedCount is the total number of times threads
                           blocked to enter or reenter a monitor
                         * blockedTime is the time elapsed in milliseconds for
                           all threads blocked to enter or reenter a monitor

    --disable-contention Disable the blocked time monitoring activated by the 
                         --contention options

    --performGC          Perform a full GC

    interval             Pause _interval_ seconds between each query (default 5)

    count                Select count records at interval second intervals

EXAMPLES

    **Precondition**. These examples assume a JMX enabled process is listening
    on localhost:9999. To use jmxstat itself as the monitored process:
    
        java -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -jar target/jmxstat-*.jar localhost:9999 java.lang:type=Runtime[Uptime]

    Display the number of loaded classes (every 5 seconds, by default):
    
        jmxstat localhost:9999 java.lang:type=ClassLoading[LoadedClassCount]
    
    Display heap usage and thread count every 2 seconds 3 times:
    
        jmxstat localhost:9999 java.lang:type=Memory[HeapMemoryUsage.max,HeapMemoryUsage.committed,HeapMemoryUsage.used] java.lang:type=Threading[ThreadCount] 2 3

    Display thread count and contention information:

        jmxstat localhost:9999 --contention java.lang:type=Threading[ThreadCount] 2 3
 
    Perform a full garbage collection:

        jmxstat localhost:9999 --performGC

INSTALL/DOWNLOAD

    Get the latest package from: 

        http://public.dev.nuxeo.com/~ben/jmxstat
 
    Then install:

        tar xzvf jmxstat-VERSION.tgz
        jmxstat-VERSION/install.sh
