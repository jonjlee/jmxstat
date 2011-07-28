#!/bin/sh
VERSION=${project.version}
cd `dirname $0`
mkdir -p /usr/local/jmxstat-$VERSION
cp -r . /usr/local/jmxstat-$VERSION
rm -f /usr/local/bin/jmxstat
ln -s /usr/local/jmxstat-$VERSION/jmxstat /usr/local/bin/jmxstat