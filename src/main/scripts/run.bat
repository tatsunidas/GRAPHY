@echo off
setlocal enabledelayedexpansion

REM === スクリプト自身のディレクトリを取得 ===
REM %~dp0 はドライブ文字とパス (末尾に \ が付く)
set "SCRIPT_DIR=%~dp0"
REM 必要であれば末尾の \ を削除する場合 (通常は cd コマンドでは不要)
rem set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

REM === OSとアーキテクチャを判別 (Windowsを前提) ===
REM %OS% 環境変数は通常 'Windows_NT'
set "OS_NAME=Windows"
REM PROCESSOR_ARCHITECTURE は AMD64, x86, ARM64 などを返す
set "ARCH_NAME=%PROCESSOR_ARCHITECTURE%"

REM 64bit OS上で32bitプロセスとして動作している場合、ARCH_NAMEを上書き
IF DEFINED PROCESSOR_ARCHITEW6432 (
    set "ARCH_NAME=x86"
)

REM === ネイティブライブラリのSuffixを決定 ===
set "NATIVE_LIB_SUFFIX="
set "NATIVE_CDRTOOL_SUFFIX=windows" REM CDRToolsはWindows共通と仮定 (要確認)

REM 検出結果を表示 (任意)
echo Detected OS: %OS_NAME%, Arch: %ARCH_NAME%

REM アーキテクチャに基づいてライブラリパスのsuffixを決定
REM !!! 注意: "win-x86-64" 等は仮のディレクトリ名です。実際のWindows用ライブラリパスに合わせてください !!!
if /I "%ARCH_NAME%"=="AMD64" (
    set "NATIVE_LIB_SUFFIX=windows-x86-64"
    echo Info: Using Windows x64 natives.
) else if /I "%ARCH_NAME%"=="x86" (
    set "NATIVE_LIB_SUFFIX=windows-x86"
    echo Info: Using Windows x86 natives.
REM) else if /I "%ARCH_NAME%"=="ARM64" (
REM    set "NATIVE_LIB_SUFFIX=win-arm64"
REM    echo Info: Using Windows ARM64 natives.
) else (
    echo Warning: Unsupported Windows architecture '%ARCH_NAME%'. Falling back to x64.
    set "NATIVE_LIB_SUFFIX=win-x86-64" REM フォールバック (要確認)
)

REM suffixが決定できなかった場合
if "%NATIVE_LIB_SUFFIX%"=="" (
    echo Error: Could not determine native library path suffix for %OS_NAME% / %ARCH_NAME%
    exit /b 1
)

REM === ネイティブライブラリのフルパスを構築 ===
set "NATIVE_LIB_PATH=%SCRIPT_DIR%lib\native_opencv\%NATIVE_LIB_SUFFIX%"
set "NATIVE_CDR_LIB_PATH=%SCRIPT_DIR%lib\native_cdrtools\%NATIVE_CDRTOOL_SUFFIX%"

REM === Javaを実行する前にスクリプトのあるディレクトリに移動 ===
echo Changing directory to: "%SCRIPT_DIR%"
cd /d "%SCRIPT_DIR%"
if errorlevel 1 (
    echo Error: Failed to change directory to "%SCRIPT_DIR%"
    exit /b 1
)

REM === アプリケーションJARとクラスパスを設定 ===
set "APP_JAR=@project.build.finalName@.jar"
REM 依存ライブラリのディレクトリ 'jars'
set "LIB_DIR=jars"

REM クラスパスを構築 (Windowsの区切り文字はセミコロン ';')
set "CLASS_PATH=%APP_JAR%;%LIB_DIR%\*"

REM === JVMオプションを設定 ===
REM -Djava.library.path の区切り文字セミコロン ';'
set "JVM_OPTS=-Djava.library.path=%NATIVE_LIB_PATH%;%NATIVE_CDR_LIB_PATH%"
set "JVM_OPTS=%JVM_OPTS% -Dj3d.allowNullGraphicsConfig=true"
set "JVM_OPTS=%JVM_OPTS% -Xms512m"
set "JVM_OPTS=%JVM_OPTS% -Xmx9216m"

REM === 実行コマンド ===
echo Using native library path: %NATIVE_LIB_PATH%;%NATIVE_CDR_LIB_PATH%
echo Starting application with options: %JVM_OPTS%
echo Using classpath: %CLASS_PATH%

REM Javaを実行 (メインクラスを指定し、バッチへの引数を %* ですべて渡す)
java %JVM_OPTS% -cp "%CLASS_PATH%" com.vis.core.launcher.Launcher %*

REM スクリプトの終了コードをJavaプロセスの終了コードに合わせる
exit /b %errorlevel%
