@echo off
rem Compiles every .java file in src into the out folder.
rem Needs a JDK (not just a JRE). It uses JAVA_HOME if set, otherwise javac from PATH.

cd /d "%~dp0"

if defined JAVA_HOME (
  set "JAVAC=%JAVA_HOME%\bin\javac.exe"
) else (
  set "JAVAC=javac"
)

"%JAVAC%" -version >nul 2>&1
if errorlevel 1 (
  echo javac was not found. Install a JDK or set JAVA_HOME to one.
  exit /b 1
)

if not exist out mkdir out
dir /s /b src\*.java > out\sources.txt

rem -source 8 -target 8 makes the classes run on Java 8 even if a newer JDK compiles them.
"%JAVAC%" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -d out @out\sources.txt
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)
echo Build finished.
