JAVAC ?= javac
JAVAC_FLAGS ?= --release 11 -encoding UTF-8 -g
JAR ?= jar
all:
	$(JAVAC) $(JAVAC_FLAGS) *.java
	$(JAR) cfm CookSeeZulegerTACAS2013Fig7a.jar MANIFEST.MF .

clean:
	rm -f *.class
	rm -f *.jar
