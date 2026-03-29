#!/bin/sh

BASEDIR=$(cd "$(dirname "$0")" && pwd)
JAVA_EXE="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

MAVEN_PROJECTBASEDIR="$BASEDIR"
export MAVEN_PROJECTBASEDIR

exec "$JAVA_EXE" -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" -cp "$BASEDIR/.mvn/wrapper/maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain "$@"
