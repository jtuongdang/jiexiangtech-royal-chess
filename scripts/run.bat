@echo off
setlocal
cd /d "%~dp0.."
set "ROYAL_JAR="
for %%F in (chess-desktop\target\royal-chess-*-all.jar) do if exist "%%F" set "ROYAL_JAR=%%F"
if not defined ROYAL_JAR (
  echo Build with mvn clean verify before launching. 1>&2
  exit /b 1
)
set "ROYAL_JAVA=java"
if defined JAVA_HOME set "ROYAL_JAVA=%JAVA_HOME%\bin\java.exe"
"%ROYAL_JAVA%" -jar "%ROYAL_JAR%" %*
exit /b %ERRORLEVEL%
