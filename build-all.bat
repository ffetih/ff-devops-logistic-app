@echo off
REM ============================================================================
REM  Builds every project in dependency order after a fresh clone.
REM  Needs a JDK 25 on the PATH (or JAVA_HOME); Maven is downloaded by mvnw.
REM
REM  There is no root pom.xml. Each step installs what the next one compiles
REM  against. Projects are added here in the sprint that introduces them
REM  (see docs\backlog.md).
REM
REM  Usage:  build-all.bat                 full build with tests
REM          build-all.bat -DskipTests     extra arguments go to every project
REM                                        after the BOM and the parent
REM ============================================================================
setlocal
pushd "%~dp0"
set MVN=call "%~dp0mvnw.cmd" -B

echo [1/4] Base BOM and parent
%MVN% -q -f base\ff-base-bom\pom.xml install || goto failed
%MVN% -q -f base\ff-base-parent\pom.xml install || goto failed

echo [2/4] Base libraries
for %%L in (tenant web persistence) do (
  echo     - ff-base-%%L
  %MVN% -f base\ff-base-%%L\pom.xml install %* || goto failed
)

echo [3/4] Platform: logistics app
%MVN% -f platform\ff-logistics\ff-logistics-app\pom.xml install %* || goto failed

echo [4/4] Done.
popd
endlocal
exit /b 0

:failed
echo.
echo BUILD FAILED. See the Maven output above.
popd
endlocal
exit /b 1
