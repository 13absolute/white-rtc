#!/usr/bin/env bash
set -euo pipefail

# Build native binary for client/daemon using GraalVM 25 in OracleLinux 8 container (glibc 2.28 compatible)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Building daemon native binary in Oracle Linux 8 container (glibc 2.28 compatible)..."

docker run --rm \
    --entrypoint /usr/bin/java \
    -v "$HOME/.gradle:/root/.gradle" \
    -v "$PROJECT_ROOT:/workspace:rw" \
    -w /workspace \
    -e "GRAALVM_HOME=/usr/lib64/graalvm/graalvm-community-java25" \
    -e "JAVA_HOME=/usr/lib64/graalvm/graalvm-community-java25" \
    ghcr.io/graalvm/native-image-community:25-ol8 \
    -jar /workspace/gradle/wrapper/gradle-wrapper.jar :daemon:nativeCompile --no-daemon --no-configuration-cache

docker run --rm -v "$PROJECT_ROOT:/workspace" alpine chown -R "$(id -u):$(id -g)" /workspace/core/build /workspace/daemon/build /workspace/build 2>/dev/null || true

chmod +x "$PROJECT_ROOT/daemon/build/native/nativeCompile/whitertc-daemon" 2>/dev/null || true

echo "==> Build complete: $PROJECT_ROOT/daemon/build/native/nativeCompile/whitertc-daemon"
