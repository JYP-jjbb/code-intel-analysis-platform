JAVAC ?= javac
JAVAC_FLAGS ?= --release 11 -encoding UTF-8 -g
JAR ?= jar
all:
	${JAVAC} ${JAVAC_FLAGS} *.java
	jar cfm DynExUpTo2VarsDisj.jar MANIFEST.MF .

clean:
	rm -rf *.class
	rm -rf *.jar
