@echo off

REM USE ANSI format for windows.
REM Use windows line break

REM run script (Windows)

REM Get the script's own directory (ends with a backslash)
SET "SCRIPT_DIR=%~dp0"
echo GRAPHY runnning on : %SCRIPT_DIR%

REM --- Check for bundled JRE ---
REM JRE folder
SET "BUNDLED_JRE_DIR=%SCRIPT_DIR%jre"

REM Default to system java command
SET "JAVA_COMMAND=java"

IF EXIST "%BUNDLED_JRE_DIR%\bin\java.exe" (
    echo Found bundled JRE at: %BUNDLED_JRE_DIR%
    SET "JAVA_COMMAND=%BUNDLED_JRE_DIR%\bin\java.exe"
) ELSE (
    echo Bundled JRE not found in %BUNDLED_JRE_DIR%. Attempting to use system Java.
    REM Check if system java is available
    WHERE java >nul 2>nul
    IF %ERRORLEVEL% NEQ 0 (
        echo Error: System Java command 'java' not found in PATH. Please install Java or ensure the bundled JRE is present.
        pause
        exit /b 1
    )
)

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
SET "NATIVE_CDRTOOL_SUFFIX=windows\win64"
SET "NATIVE_CDR_LIB_PATH=%SCRIPT_DIR%lib\native_cdrtools\%NATIVE_CDRTOOL_SUFFIX%"

echo native_cdrtools : %NATIVE_CDR_LIB_PATH%

REM --- CUDA(NVRTC) redistributable DLLs for Cinematic Rendering, if bundled ---
REM 無ければCinematicGpuDetectorが検出に失敗してOpenGL実装に自動フォールバックするだけなので、
REM native_opencv/native_cdrtoolsと同じくIF EXISTで任意扱いにする。
SET "NATIVE_CUDA_LIB_PATH=%SCRIPT_DIR%lib\native_cuda\windows-x86-64"

echo native_cuda : %NATIVE_CUDA_LIB_PATH%

REM --- Combine library paths using semicolon (;) ---
REM Ensure paths exist before adding to avoid unnecessary semicolons or errors if optional
SET "COMBINED_LIB_PATH="
IF EXIST "%NATIVE_LIB_PATH%" (
    SET "COMBINED_LIB_PATH=%NATIVE_LIB_PATH%;"
)
IF EXIST "%NATIVE_CDR_LIB_PATH%" (
    SET "COMBINED_LIB_PATH=%COMBINED_LIB_PATH%%NATIVE_CDR_LIB_PATH%;"
)
IF EXIST "%NATIVE_CUDA_LIB_PATH%" (
    SET "COMBINED_LIB_PATH=%COMBINED_LIB_PATH%%NATIVE_CUDA_LIB_PATH%;"
)
REM Remove trailing semicolon if exists
IF "%COMBINED_LIB_PATH:~-1%"==";" SET "COMBINED_LIB_PATH=%COMBINED_LIB_PATH:~0,-1%"


REM --- Set JVM Options ---
REM Note: Use "%COMBINED_LIB_PATH%" for java.library.path, quoting handles spaces
SET "JVM_OPTS="
IF DEFINED COMBINED_LIB_PATH (
    SET "JVM_OPTS=-Djava.library.path=%COMBINED_LIB_PATH%"
)
REM For Java3D
SET "JVM_OPTS=%JVM_OPTS% -Dj3d.allowNullGraphicsConfig=true"
SET "JVM_OPTS=%JVM_OPTS% -Xms512m"
SET "JVM_OPTS=%JVM_OPTS% -Xmx9216m"
SET "JVM_OPTS=%JVM_OPTS% --add-exports java.base/java.lang=ALL-UNNAMED"
SET "JVM_OPTS=%JVM_OPTS% --add-exports java.desktop/sun.awt=ALL-UNNAMED"
SET "JVM_OPTS=%JVM_OPTS% --add-exports java.desktop/sun.java2d=ALL-UNNAMED"
SET "JVM_OPTS=%JVM_OPTS% --add-exports java.base/sun.security.action=ALL-UNNAMED"
REM For dcm4che imageio
SET "JVM_OPTS=%JVM_OPTS% --add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED"
SET "JVM_OPTS=%JVM_OPTS% --add-opens java.base/java.io=ALL-UNNAMED"

REM --- Execute Java Application ---
echo Using Java command: "%JAVA_COMMAND%"
IF DEFINED COMBINED_LIB_PATH (
    echo Using native library path: "%COMBINED_LIB_PATH%"
) ELSE (
    echo No native library path specified or found.
)
echo Starting application with options: %JVM_OPTS%

REM Ensure placeholders are handled correctly
REM @project.build.finalName@ should be replaced by your build process (e.g., Maven resource filtering)
SET "APP_JAR=%SCRIPT_DIR%@project.build.finalName@.jar"
SET "LIB_JAR=%SCRIPT_DIR%jars\*"
SET "CLASSPATH=%APP_JAR%;%LIB_JAR%"

REM Check if Application JAR exists
IF NOT EXIST "%APP_JAR%" (
    echo Error: Application JAR not found at %APP_JAR%
    echo Please check if the placeholder '@project.build.finalName@' was correctly replaced during the build.
    pause
    exit /b 1
)

REM Change current directory to the script directory
cd /d "%SCRIPT_DIR%"

REM Use quotes around paths and classpath arguments for robustness
"%JAVA_COMMAND%" %JVM_OPTS% -cp "%CLASSPATH%" com.vis.core.launcher.Launcher %*

REM Exit with the same code as Java (optional)
exit /b %ERRORLEVEL%