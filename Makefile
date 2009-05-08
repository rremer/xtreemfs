ifeq "$(JAVA_HOME)" ""
	JAVAC_BIN = /usr/bin/javac
else
	JAVAC_BIN = $(JAVA_HOME)/bin/javac
endif

ifeq "$(ANT_HOME)" ""
        ANT_BIN = /usr/bin/ant
else
        ANT_BIN = $(ANT_HOME)/bin/ant
endif

WHICH_GPP = $(shell which g++)

TARGETS = client server
.PHONY:	clean distclean

# Some toplevel configuration
XTFS_BINDIR = $(shell pwd)/bin
export XTFS_BINDIR

all: check $(TARGETS)

clean: check $(patsubst %,%_clean,$(TARGETS))

distclean: check $(patsubst %,%_distclean,$(TARGETS))

check:
	@if [ ! -e $(JAVAC_BIN) ]; then echo "javac not found! Make sure a JDK is installed and set JAVA_HOME."; exit 1; fi;
	@if [ $(shell $(JAVAC_BIN) -version 2>&1 | head -n1 | cut -d" " -f2 | cut -d. -f2) -lt 6 ]; then echo "java version >= 1.6.0 required!"; exit 1; fi;
	@echo "java ok"

	@if [ ! -e $(ANT_BIN) ]; then echo "ant not found! Make sure ant is installed and set ANT_HOME."; exit 1; fi;
	@echo "ant ok"

	@if [ ! $(WHICH_GPP) ]; then echo "g++ not found";exit 1; fi;
	@echo "g++ ok"

	@if [[ $(shell python -V 2>&1 | head -n1 | cut -d" " -f2 | cut -d. -f2) -lt 5 && $(shell python -V 2>&1 | head -n1 | cut -d" " -f2 | cut -d. -f1) -lt 3 ]]; then echo "python >= 2.5 required!"; exit 1; fi;
	@echo "python ok"
	
.PHONY:	client client_clean client_distclean
client:
	python src/client/scons.py -C src/client
client_clean:
	python src/client/scons.py -C src/client -c
client_distclean:
	python src/client/scons.py -C src/client -c

.PHONY: server server_clean server_distclean
server: check
	$(ANT_BIN)  -f src/servers/build.xml jar
server_clean: check
	$(ANT_BIN)  -f src/servers/build.xml clean || exit 1;
server_distclean: check
	$(ANT_BIN)  -f src/servers/build.xml clean || exit 1;
