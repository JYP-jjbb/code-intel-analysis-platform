@ECHO OFF
setlocal
set BASEDIR=%~dp0
set JAVA_EXE=java
if not "%JAVA_HOME%"=="" (
  if exist "%JAVA_HOME%\bin\java.exe" set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
)
set MAVEN_PROJECTBASEDIR=%BASEDIR%
%JAVA_EXE% -Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR% -cp "%BASEDIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
