#!/bin/bash
# run script (Linux/Mac)

# This file must be Unix format.
# Open TextEditor>Save with another name>改行文字（Unix）

# スクリプト自身のディレクトリを取得
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# OSとアーキテクチャを判別
OS_NAME=$(uname -s)
ARCH_NAME=$(uname -m)

# デフォルトのネイティブライブラリパスのsuffixを初期化
NATIVE_LIB_SUFFIX=""

# 検出結果を表示 (任意)
echo "Detected OS: $OS_NAME, Arch: $ARCH_NAME"

# OSとアーキテクチャに基づいてライブラリパスのsuffixを決定
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
                NATIVE_LIB_SUFFIX="linux-x86-64" # フォールバック (必要に応じて変更)
                ;;
        esac
        ;;
    Darwin) # macOS
        case "$ARCH_NAME" in
            x86_64)
                # 例: macOS Intel用 (実際のディレクトリ名に合わせてください)
                NATIVE_LIB_SUFFIX="macosx-x86-64"
                echo "Info: Using macOS Intel natives."
                ;;
            aarch64 | arm64) # aarch64 または arm64 (Apple Silicon Mシリーズ)
                # 例: macOS Apple Silicon用 (実際のディレクトリ名に合わせてください)
                NATIVE_LIB_SUFFIX="macosx-aarch64"
                echo "Info: Using macOS Apple Silicon natives."
                ;;
            *)
                echo "Warning: Unsupported macOS architecture '$ARCH_NAME'. Falling back to x86_64."
                NATIVE_LIB_SUFFIX="macos-x86-64" # フォールバック (必要に応じて変更)
                ;;
        esac
        ;;
    *)
        echo "Error: Unsupported Operating System '$OS_NAME'. Cannot determine native library path."
        exit 1 # サポート外OSの場合はエラー終了
        ;;
esac

# suffixが決定できなかった場合（通常は上記の*で捕捉されるが念のため）
if [ -z "$NATIVE_LIB_SUFFIX" ]; then
    echo "Error: Could not determine native library path suffix for $OS_NAME / $ARCH_NAME"
    exit 1
fi

# 完全なネイティブライブラリパスを構築
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
        exit 1 # サポート外OSの場合はエラー終了
        ;;
esac

# 完全なネイティブCDRTOOLSライブラリパスを構築
NATIVE_CDR_LIB_PATH="${SCRIPT_DIR}/lib/native_cdrtools/${NATIVE_CDRTOOL_SUFFIX}"

# Javaを実行する前にAppディレクトリに移動
echo "Changing directory to: $SCRIPT_DIR"
cd "$SCRIPT_DIR"

# アプリケーションJARファイルのパス
APP_JAR="${SCRIPT_DIR}/${project.build.finalName}.jar"

# 依存ライブラリが格納されているディレクトリ
LIB_DIR="${SCRIPT_DIR}/jars"

# クラスパスを構築 (アプリケーションJARとlibフォルダ内の全JAR)
# Linux/macOS では区切り文字はコロン ':'
CLASS_PATH="${APP_JAR}:${LIB_DIR}/*"

# JVMオプションを設定
JVM_OPTS="-Djava.library.path=${NATIVE_LIB_PATH}:${NATIVE_CDR_LIB_PATH}"
JVM_OPTS="$JVM_OPTS -Dj3d.allowNullGraphicsConfig=true"
JVM_OPTS="$JVM_OPTS -Xms512m"
JVM_OPTS="$JVM_OPTS -Xmx9216m"

# 実行コマンド
echo "Using native library path: $NATIVE_LIB_PATH" # 使用するパスを表示 (任意)
echo "Starting application with options: $JVM_OPTS"
java $JVM_OPTS -cp "$CLASS_PATH" "com.vis.core.launcher.Launcher" "$@"

# スクリプトの終了コードをJavaプロセスの終了コードに合わせる
exit $?
