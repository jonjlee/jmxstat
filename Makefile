# $Id: $
#
.PHONY: clean build install distrib

TARGET=gateway:/opt/public-dev/public/ben/jmxstat

build:
	mvn clean package

clean:
	mvn clean
	-find . "(" -name "*~" -or  -name ".#*" -or -name "#*" ")" -print0 | xargs -0 rm -f

install:
	tar -C target -zxvf target/jmxstat-*-dist.tar.gz
	target/jmxstat-*/install.sh

distrib:
	-scp ./target/jmxstat-*-dist.tar.gz $(TARGET)/