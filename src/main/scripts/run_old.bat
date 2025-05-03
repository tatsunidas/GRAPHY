@echo off

REM USE ANSI format for windows.
REM Use windows line break

REM run script (Windows)

REM Get the script's own directory (ends with a backslash)
SET "SCRIPT_DIR=%~dp0"
echo GRAPHY running on : %SCRIPT_DIR%

REM --- Detect Architecture ---
REM PROCESSOR_ARCHITECTURE can be AMD64, IA64, ARM64, x86
SET "ARCH_NAME=%PROCESSOR_ARCHITECTURE%"
echo Detected OS: Windows, Arch: %ARCH_NAME%

REM --- Determine native_opencv library path suffix ---
SET "NATIVE_LIB_SUFFIX=windows-x86-64"

IF /I "%ARCH_NAME%" == "AMD64" (
    echo AMD64 was selected
    SET "NATIVE_LIB_SUFFIX=windows-x86-64"
) ELSE IF /I "%ARCH_NAME%" == "x86" (
    SET "NATIVE_LIB_SUFFIX=windows-x86"
) ELSE IF /I "%ARCH_NAME%" == "ARM64" (
    SET "NATIVE_LIB_SUFFIX=windows-arm64"
) ELSE (
    REM keep default
)

IF NOT DEFINED NATIVE_LIB_SUFFIX (
    echo Error: Could not determine native library path suffix for opencv.
    exit /b 1
)

REM Construct full path for native_opencv (using backslashes for Windows)
SET "NATIVE_LIB_PATH=%SCRIPT_DIR%lib\native_opencv\%NATIVE_LIB_SUFFIX%"

echo native_opencv : %NATIVE_LIB_PATH%

REM --- Determine native_cdrtools library path suffix ---
REM For Windows, assume a single 'windows' suffix
SET "NATIVE_CDRTOOL_SUFFIX=windows"
SET "NATIVE_CDR_LIB_PATH=%SCRIPT_DIR%lib\native_cdrtools\%NATIVE_CDRTOOL_SUFFIX%"

echo native_cdrtools : %NATIVE_CDR_LIB_PATH%

REM --- Combine library paths using semicolon (;) ---
SET "COMBINED_LIB_PATH=%NATIVE_LIB_PATH%;%NATIVE_CDR_LIB_PATH%"

REM --- Set JVM Options ---
REM Note: Use %COMBINED_LIB_PATH% for java.library.path
SET "JVM_OPTS=-Djava.library.path=%COMBINED_LIB_PATH%"
REM For Java3D
SET "JVM_OPTS=%JVM_OPTS% -Dj3d.allowNullGraphicsConfig=true"
SET "JVM_OPTS=%JVM_OPTS% --add-exports java.base/java.lang=ALL-UNNAMED"
SET "JVM_OPTS=%JVM_OPTS% --add-exports java.desktop/sun.awt=ALL-UNNAMED"
SET "JVM_OPTS=%JVM_OPTS% --add-exports java.desktop/sun.java2d=ALL-UNNAMED"
SET "JVM_OPTS=%JVM_OPTS% -Xms512m"
SET "JVM_OPTS=%JVM_OPTS% -Xmx9216m"

REM --- Execute Java Application ---
echo Using native library path: %COMBINED_LIB_PATH%
echo Starting application with options: %JVM_OPTS%

SET "APP_JAR=%SCRIPT_DIR%@project.build.finalName@.jar"
SET "LIB_JAR=%SCRIPT_DIR%jars\*"
SET "CLASSPATH=%APP_JAR%;%LIB_JAR%"

REM Use the correct placeholder for Maven filtering: graphy-0.0.1-SNAPSHOT
java %JVM_OPTS% -cp %CLASSPATH% com.vis.core.launcher.Launcher %*

REM Exit with the same code as Java (optional)
exit /b %ERRORLEVEL%