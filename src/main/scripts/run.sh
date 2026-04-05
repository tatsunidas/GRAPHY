#!/bin/bash
# run script (Linux/Mac)

# This file must be Unix format.

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

OS_NAME=$(uname -s)
ARCH_NAME=$(uname -m)

NATIVE_LIB_SUFFIX=""

echo "Detected OS: $OS_NAME, Arch: $ARCH_NAME"

# suffix for native lib
case "$OS_NAME" in
    Linux)
        case "$ARCH_NAME" in
            x86_64)
                NATIVE_LIB_SUFFIX="linux-x86-64"
                ;;
            X86)
                # Linux X86
                NATIVE_LIB_SUFFIX="linux-X86"
                echo "Info: Using Linux X86 natives."
                ;;
            aarch64)
                # Linux ARM64
                NATIVE_LIB_SUFFIX="linux-aarch64"
                echo "Info: Using Linux ARM64 natives."
                ;;
            armv7a)
                # Linux ARMv7
                NATIVE_LIB_SUFFIX="linux-armv7"
                echo "Info: Using Linux ARMv7 natives."
                ;;
            s390x)
                # Linux IBM Z
                NATIVE_LIB_SUFFIX="linux-s390x"
                echo "Info: Using Linux IBM Z natives."
                ;;

            *)
                echo "Warning: Unsupported Linux architecture '$ARCH_NAME'. Falling back to x86_64."
                NATIVE_LIB_SUFFIX="linux-x86-64"
                ;;
        esac
        ;;
    Darwin) # macOS
        case "$ARCH_NAME" in
            x86_64)
                # macOS Intel
                NATIVE_LIB_SUFFIX="macosx-x86-64"
                echo "Info: Using macOS Intel natives."
                ;;
            aarch64 | arm64)
                # macOS Apple Silicon
                NATIVE_LIB_SUFFIX="macosx-aarch64"
                echo "Info: Using macOS Apple Silicon natives."
                ;;
            *)
                echo "Warning: Unsupported macOS architecture '$ARCH_NAME'. Falling back to x86_64."
                NATIVE_LIB_SUFFIX="macos-x86-64"
                ;;
        esac
        ;;
    *)
        echo "Error: Unsupported Operating System '$OS_NAME'. Cannot determine native library path."
        exit 1
        ;;
esac

if [ -z "$NATIVE_LIB_SUFFIX" ]; then
    echo "Error: Could not determine native library path suffix for $OS_NAME / $ARCH_NAME"
    exit 1
fi

NATIVE_LIB_PATH="${SCRIPT_DIR}/lib/native_opencv/${NATIVE_LIB_SUFFIX}"

NATIVE_CDRTOOL_SUFFIX=""

case "$OS_NAME" in
    Linux)
        NATIVE_CDRTOOL_SUFFIX="linux"
        ;;
    Darwin) # macOS
        NATIVE_CDRTOOL_SUFFIX="mac"
        ;;
    *)
        echo "Error: Unsupported Operating System '$OS_NAME'. Cannot determine native cdrtools library path."
        exit 1
        ;;
esac

NATIVE_CDR_LIB_PATH="${SCRIPT_DIR}/lib/native_cdrtools/${NATIVE_CDRTOOL_SUFFIX}"

echo "Changing directory to: $SCRIPT_DIR"
cd "$SCRIPT_DIR"

# java
APP_JAR="${SCRIPT_DIR}/${project.build.finalName}.jar"
if [ ! -f "$APP_JAR" ]; then
    echo "Error: Application JAR file not found at $APP_JAR"
    exit 1
fi

LIB_DIR="${SCRIPT_DIR}/jars"

# Linux/macOS use ':'
CLASS_PATH="${APP_JAR}:${LIB_DIR}/*"

# JVM options
JVM_OPTS="-Djava.library.path=${NATIVE_LIB_PATH}:${NATIVE_CDR_LIB_PATH}"
JVM_OPTS="$JVM_OPTS -Dj3d.allowNullGraphicsConfig=true"
JVM_OPTS="$JVM_OPTS -Xms512m"
JVM_OPTS="$JVM_OPTS -Xmx9216m"
JVM_OPTS="$JVM_OPTS --add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED"
# For dcm4che imageio
JVM_OPTS="$JVM_OPTS --add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED"
JVM_OPTS="$JVM_OPTS --add-opens java.base/java.io=ALL-UNNAMED"

# jre
DEFAULT_JAVA_CMD="java"
JAVA_CMD="$DEFAULT_JAVA_CMD"

LOCAL_JRE_DIR="${SCRIPT_DIR}/jre"
LOCAL_JAVA_EXEC="${LOCAL_JRE_DIR}/bin/java"

if [ -d "$LOCAL_JRE_DIR" ]; then
    echo "Info: Found local JRE directory: $LOCAL_JRE_DIR"
    if [ -x "$LOCAL_JAVA_EXEC" ]; then
        echo "Info: Using Java executable from local JRE: $LOCAL_JAVA_EXEC"
        JAVA_CMD="$LOCAL_JAVA_EXEC"
    else
        # no jre/bin/java
        echo "Warning: Found local JRE directory, but cannot execute $LOCAL_JAVA_EXEC."
        echo "         Falling back to default Java command found in PATH: $DEFAULT_JAVA_CMD"
    fi
else
    echo "Info: No local JRE directory found at $LOCAL_JRE_DIR."
    echo "      Using default Java command found in PATH: $DEFAULT_JAVA_CMD"
fi

# check
echo "Using Java command: $JAVA_CMD" # どのJavaを使用するか表示
echo "Using native library path: $NATIVE_LIB_PATH"
echo "Using native cdrtools path: $NATIVE_CDR_LIB_PATH"
echo "Starting application with JVM options: $JVM_OPTS"
echo "Classpath: $CLASS_PATH"
echo "Main Class: com.vis.core.launcher.Launcher"
echo "Arguments: $@"
echo "---"

"$JAVA_CMD" $JVM_OPTS -cp "$CLASS_PATH" "com.vis.core.launcher.Launcher" "$@"

exit $?
