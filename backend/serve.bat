@echo off
rem Builds the project, then starts the web server and the frontend.
rem Usage: serve.bat [port]      (the default port is 8080)

cd /d "%~dp0"

call "%~dp0build.bat"
if errorlevel 1 exit /b 1

if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)

echo.
"%JAVA%" -cp out docsearch.Main web %*
