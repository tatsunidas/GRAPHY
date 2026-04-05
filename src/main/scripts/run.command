#!/bin/bash

# ==========================================
# GRAPHY Run Script for macOS
# ==========================================

# Get the script's own directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
echo "GRAPHY running on : $SCRIPT_DIR"

# --- Check for bundled JRE ---
# MacのJREは通常 Contents/Home/bin/java という構造になりますが、
# Windowsのように直接 bin/java が配置されているケースも考慮して両方探します。
JAVA_COMMAND="java"
BUNDLED_JRE_MAC_STANDARD="$SCRIPT_DIR/jre/Contents/Home/bin/java"
BUNDLED_JRE_FLAT="$SCRIPT_DIR/jre/bin/java"

if [ -f "$BUNDLED_JRE_MAC_STANDARD" ]; then
    echo "Found bundled JRE at: $SCRIPT_DIR/jre/Contents/Home"
    JAVA_COMMAND="$BUNDLED_JRE_MAC_STANDARD"
elif [ -f "$BUNDLED_JRE_FLAT" ]; then
    echo "Found bundled JRE at: $SCRIPT_DIR/jre"
    JAVA_COMMAND="$BUNDLED_JRE_FLAT"
else
    echo "Bundled JRE not found. Attempting to use system Java."
    # Check if system java is available
    if ! command -v java >/dev/null 2>&1; then
        echo "Error: System Java command 'java' not found in PATH."
        echo "Please install Java or ensure the bundled JRE is present."
        exit 1
    fi
fi

# --- Detect Architecture ---
# macOSでは uname -m コマンドでアーキテクチャを取得します (x86_64 または arm64)
ARCH_NAME=$(uname -m)
echo "Detected OS: macOS, Arch: $ARCH_NAME"

# --- Determine native_opencv library path suffix ---
# ※注意: 以下のサフィックスは実際の native フォルダ内の名前に合わせて修正してください。
NATIVE_LIB_SUFFIX="macosx-x86-64"

if [ "$ARCH_NAME" = "x86_64" ]; then
    echo "x86_64 (Intel) was selected"
    NATIVE_LIB_SUFFIX="macosx-x86-64"
elif [ "$ARCH_NAME" = "arm64" ] || [ "$ARCH_NAME" = "aarch64" ]; then
    echo "arm64 (Apple Silicon) was selected"
    # opencvに合わせる
    NATIVE_LIB_SUFFIX="macosx-aarch64"
else
    echo "Warning: Unknown architecture $ARCH_NAME. Using default."
fi

# Construct full path for native_opencv
NATIVE_LIB_PATH="$SCRIPT_DIR/lib/native_opencv/$NATIVE_LIB_SUFFIX"
echo "native_opencv : $NATIVE_LIB_PATH"

# --- Determine native_cdrtools library path suffix ---
NATIVE_CDRTOOL_SUFFIX="mac"

fi
NATIVE_CDR_LIB_PATH="$SCRIPT_DIR/lib/native_cdrtools/$NATIVE_CDRTOOL_SUFFIX"
echo "native_cdrtools : $NATIVE_CDR_LIB_PATH"


# --- Combine library paths using colon (:) ---
# Mac/Linuxではパスの区切り文字はコロン(:)を使用します。
COMBINED_LIB_PATH=""
if [ -d "$NATIVE_LIB_PATH" ]; then
    COMBINED_LIB_PATH="$NATIVE_LIB_PATH"
fi

if [ -d "$NATIVE_CDR_LIB_PATH" ]; then
    if [ -n "$COMBINED_LIB_PATH" ]; then
        COMBINED_LIB_PATH="$COMBINED_LIB_PATH:$NATIVE_CDR_LIB_PATH"
    else
        COMBINED_LIB_PATH="$NATIVE_CDR_LIB_PATH"
    fi
fi

# --- Set JVM Options ---
# 配列を使用してオプションを管理すると、空白を含むパスなどの処理が安全になります。
JVM_OPTS=()
if [ -n "$COMBINED_LIB_PATH" ]; then
    JVM_OPTS+=("-Djava.library.path=$COMBINED_LIB_PATH")
fi

JVM_OPTS+=("-Dj3d.allowNullGraphicsConfig=true")
JVM_OPTS+=("-Xms512m")
JVM_OPTS+=("-Xmx9216m")
JVM_OPTS+=("--add-exports=java.base/java.lang=ALL-UNNAMED")
JVM_OPTS+=("--add-exports=java.desktop/sun.awt=ALL-UNNAMED")
JVM_OPTS+=("--add-exports=java.desktop/sun.java2d=ALL-UNNAMED")
JVM_OPTS+=("--add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED")
JVM_OPTS+=("--add-opens=java.base/java.io=ALL-UNNAMED")

# --- Execute Java Application ---
echo "Using Java command: $JAVA_COMMAND"
if [ -n "$COMBINED_LIB_PATH" ]; then
    echo "Using native library path: $COMBINED_LIB_PATH"
else
    echo "No native library path specified or found."
fi
echo "Starting application..."

# クラスパスの区切りもコロン(:)になります
APP_JAR="$SCRIPT_DIR/@project.build.finalName@.jar"
LIB_JAR="$SCRIPT_DIR/jars/*"
CLASSPATH="$APP_JAR:$LIB_JAR"

# Check if Application JAR exists
if [ ! -f "$APP_JAR" ]; then
    echo "Error: Application JAR not found at $APP_JAR"
    echo "Please check if the placeholder '@project.build.finalName@' was correctly replaced."
    exit 1
fi

# Change current directory to the script directory
cd "$SCRIPT_DIR" || exit 1

# Execute Java
"$JAVA_COMMAND" "${JVM_OPTS[@]}" -cp "$CLASSPATH" com.vis.core.launcher.Launcher "$@"