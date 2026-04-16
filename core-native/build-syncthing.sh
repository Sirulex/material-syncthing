#!/usr/bin/env bash
# Cross-compiles Syncthing for Android.
# Requires: Go 1.22+, Android NDK r26+
# Usage: SYNCTHING_VERSION=v1.29.2 NDK_HOME=/path/to/ndk ./build-syncthing.sh
set -euo pipefail

VERSION="${SYNCTHING_VERSION:?Set SYNCTHING_VERSION (e.g. v1.29.2)}"
NDK="${NDK_HOME:?Set NDK_HOME to Android NDK root}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${SCRIPT_DIR}/src/main/jniLibs"

echo "==> Cloning syncthing $VERSION"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
git clone --depth 1 --branch "$VERSION" https://github.com/syncthing/syncthing "$WORK/syncthing"
cd "$WORK/syncthing"

# NDK toolchain
TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [ ! -d "$TOOLCHAIN" ]; then
  # macOS host
  TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin"
fi

declare -A ABIS=(
  [arm64-v8a]="aarch64-linux-android21-clang arm64"
  [armeabi-v7a]="armv7a-linux-androideabi21-clang arm"
  [x86_64]="x86_64-linux-android21-clang amd64"
)

for abi in "${!ABIS[@]}"; do
  read -r cc goarch <<< "${ABIS[$abi]}"
  echo "==> Building for $abi (GOARCH=$goarch)"
  mkdir -p "$OUT/$abi"
  CGO_ENABLED=1 \
  CC="${TOOLCHAIN}/${cc}" \
  GOOS=android \
  GOARCH="$goarch" \
    go run build.go -no-upgrade build
  mv syncthing "$OUT/$abi/libsyncthingnative.so"
  echo "==> $abi done: $OUT/$abi/libsyncthingnative.so"
done

echo "==> All ABIs built successfully"
