@echo off
setlocal

rem Prefer the JDK 17 installations available on this machine over PATH's Java 8.
if exist "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe" set "JAVA_HOME=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
if exist "%ProgramFiles%\Java\jdk-17.0.19+10\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Java\jdk-17.0.19+10"
if defined MINIMAP_JAVA_HOME set "JAVA_HOME=%MINIMAP_JAVA_HOME%"

call "%~dp0gradlew.bat" -p "%~dp0." run --console=plain %*
if errorlevel 1 pause
endlocal
