# FunkLoad Makefile
# $Id: $
#
.PHONY: clean pkg install uninstall

VERSION=0.2.0
JMXSTAT_HOME=$(PREFIX)/jmxstat
TARGET=gateway:/opt/public-dev/public/ben/jmxstat

clean:
	-find . "(" -name "*~" -or  -name ".#*" -or -name "#*" ")" -print0 | xargs -0 rm -f
	-rm -rf ./dist/

build:
	mvn clean package

distrib:
	-scp ./dist/jmxstat-*.tgz $(TARGET)/

pkg: build
	-rm -rf ./dist/
	mkdir -p ./dist/jmxstat-$(VERSION)
	cp ./Makefile  ./dist/jmxstat-$(VERSION)/
	cp ./scripts/jmxstat ./dist/jmxstat-$(VERSION)/
	sed -i "s/VERSION$$/$(VERSION)/g" ./dist/jmxstat-$(VERSION)/jmxstat
	cp ./target/*.jar ./dist/jmxstat-$(VERSION)/
	(cd ./dist; tar czvf jmxstat-$(VERSION).tgz jmxstat-$(VERSION)/)

install:
	cp -r ../jmxstat-$(VERSION) /usr/local/
	-rm -f /usr/local/bin/jmxstat
	cp jmxstat /usr/local/bin/jmxstat
	chmod +x /usr/local/bin/jmxstat


