@echo off
rem Builds the project, then runs the self test.

cd /d "%~dp0"

call "%~dp0build.bat"
if errorlevel 1 exit /b 1

if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)

echo.
"%JAVA%" -cp out docsearch.SelfTest
