#!/usr/bin/env bash
set -euo pipefail

# Build native binary for client/daemon using GraalVM 25 in OracleLinux 8 container (glibc 2.28 compatible)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Building daemon native binary in Oracle Linux 8 container (glibc 2.28 compatible)..."

ANDROID_SDK_HOST="${ANDROID_HOME:-}"
if [ -z "$ANDROID_SDK_HOST" ] && [ -d "$HOME/Android/Sdk" ]; then
    ANDROID_SDK_HOST="$HOME/Android/Sdk"
elif [ -z "$ANDROID_SDK_HOST" ] && [ -d "/usr/local/lib/android/sdk" ]; then
    ANDROID_SDK_HOST="/usr/local/lib/android/sdk"
fi

SDK_MOUNTS=()
if [ -n "$ANDROID_SDK_HOST" ] && [ -d "$ANDROID_SDK_HOST" ]; then
    SDK_MOUNTS=(
        -v "$ANDROID_SDK_HOST:$ANDROID_SDK_HOST:ro"
        -v "$ANDROID_SDK_HOST:/opt/android-sdk:ro"
        -e "ANDROID_HOME=$ANDROID_SDK_HOST"
    )
fi

docker run --rm \
    --entrypoint /usr/bin/java \
    -v "$HOME/.gradle:/root/.gradle" \
    "${SDK_MOUNTS[@]}" \
    -v "$PROJECT_ROOT:/workspace:rw" \
    -w /workspace \
    -e "GRAALVM_HOME=/usr/lib64/graalvm/graalvm-community-java25" \
    -e "JAVA_HOME=/usr/lib64/graalvm/graalvm-community-java25" \
    ghcr.io/graalvm/native-image-community:25-ol8 \
    -jar /workspace/gradle/wrapper/gradle-wrapper.jar :daemon:nativeCompile --no-daemon --no-configuration-cache

docker run --rm -v "$PROJECT_ROOT:/workspace" alpine chown -R "$(id -u):$(id -g)" /workspace/core/build /workspace/daemon/build /workspace/build 2>/dev/null || true

chmod +x "$PROJECT_ROOT/daemon/build/native/nativeCompile/whitertc-daemon" 2>/dev/null || true

echo "==> Build complete: $PROJECT_ROOT/daemon/build/native/nativeCompile/whitertc-daemon"
