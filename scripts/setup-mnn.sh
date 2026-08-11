#!/usr/bin/env bash
# Prepare the gitignored MNN prerequisites required by the :mnn module:
#   1. vendor/MNN            - MNN source tree pinned to a known-good commit (headers + cmake project)
#   2. mnn-prebuilt/arm64-v8a/libMNN.so - prebuilt runtime used for linking and packaging
#
# Bash equivalent of scripts/setup-mnn.ps1, used by CI (ubuntu runners).
# Usage: ./scripts/setup-mnn.sh [--skip-build]
#
# Requires: git; for the build step also NDK 25.1.8937393 + cmake + ninja.
# SDK root resolution order: $MNN_ANDROID_SDK, $ANDROID_HOME, $ANDROID_SDK_ROOT, ~/Android/Sdk.
# A missing NDK is installed on a best-effort basis via sdkmanager.

set -euo pipefail

SKIP_BUILD=0
if [ "${1:-}" = "--skip-build" ]; then SKIP_BUILD=1; fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENDOR_DIR="$REPO_ROOT/vendor/MNN"
MNN_REPO_URL="https://github.com/alibaba/MNN.git"
# Pinned upstream commit (keep in sync with scripts/setup-mnn.ps1).
PINNED_COMMIT="1d535d728362d0ee8a4cc6d854b970c8d7f94e02"
NDK_VERSION="25.1.8937393"

# ---------- 1. Ensure vendor/MNN at the pinned commit ----------
checkout_pinned() {
  if [ ! -d "$VENDOR_DIR/.git" ]; then
    mkdir -p "$VENDOR_DIR"
    git init --quiet "$VENDOR_DIR"
    git -C "$VENDOR_DIR" remote add origin "$MNN_REPO_URL"
  fi
  echo "Fetching alibaba/MNN @ ${PINNED_COMMIT:0:7} (shallow)..."
  git -C "$VENDOR_DIR" fetch --depth 1 origin "$PINNED_COMMIT"
  git -C "$VENDOR_DIR" checkout --quiet --detach "$PINNED_COMMIT"
}

if [ -f "$VENDOR_DIR/CMakeLists.txt" ] && [ "$(git -C "$VENDOR_DIR" rev-parse HEAD 2>/dev/null || true)" = "$PINNED_COMMIT" ]; then
  echo "vendor/MNN already at pinned commit ${PINNED_COMMIT:0:7}, skipping clone."
else
  checkout_pinned
fi

[ -f "$VENDOR_DIR/CMakeLists.txt" ] || { echo "ERROR: vendor/MNN checkout is incomplete: $VENDOR_DIR" >&2; exit 1; }

if [ "$SKIP_BUILD" = "1" ]; then
  echo "--skip-build set: not rebuilding mnn-prebuilt/libMNN.so"
  exit 0
fi

# ---------- 2. Resolve Android SDK / NDK / cmake / ninja ----------
SDK_ROOT="${MNN_ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}}"
NDK_ROOT="$SDK_ROOT/ndk/$NDK_VERSION"
TOOLCHAIN="$NDK_ROOT/build/cmake/android.toolchain.cmake"

if [ ! -f "$TOOLCHAIN" ]; then
  SDKMANAGER=""
  for c in "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "$SDK_ROOT/tools/bin/sdkmanager" sdkmanager; do
    if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then SDKMANAGER="$c"; break; fi
  done
  if [ -z "$SDKMANAGER" ]; then
    echo "ERROR: NDK $NDK_VERSION not found at $NDK_ROOT and no sdkmanager available to install it." >&2
    exit 1
  fi
  echo "Installing NDK $NDK_VERSION via sdkmanager..."
  yes | "$SDKMANAGER" "ndk;$NDK_VERSION" >/dev/null
  [ -f "$TOOLCHAIN" ] || { echo "ERROR: NDK installation failed: $NDK_ROOT" >&2; exit 1; }
fi

# Prefer the SDK-bundled cmake/ninja (same versions as the Gradle externalNativeBuild),
# fall back to the host toolchain (CI runners ship recent cmake + ninja).
CMAKE_EXE="$SDK_ROOT/cmake/3.22.1/bin/cmake"
NINJA_EXE="$SDK_ROOT/cmake/3.22.1/bin/ninja"
[ -x "$CMAKE_EXE" ] || CMAKE_EXE="$(command -v cmake || true)"
[ -x "$NINJA_EXE" ] || NINJA_EXE="$(command -v ninja || true)"
[ -n "$CMAKE_EXE" ] || { echo "ERROR: cmake not found (install it or the Android SDK cmake package)" >&2; exit 1; }
[ -n "$NINJA_EXE" ] || { echo "ERROR: ninja not found (install it or the Android SDK cmake package)" >&2; exit 1; }

# ---------- 3. Build libMNN.so (same flags as scripts/build-mnn-android.ps1) ----------
BUILD_DIR="$VENDOR_DIR/project/android/build_64"
mkdir -p "$BUILD_DIR"
JOBS="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"

echo "SDK:     $SDK_ROOT"
echo "NDK:     $NDK_ROOT"
echo "Build:   $BUILD_DIR"
echo "Jobs:    $JOBS"

# Vision / audio / diffusion / OpenCL are intentionally left OFF to keep the
# library text-only and small.
"$CMAKE_EXE" \
  -S "$VENDOR_DIR" \
  -B "$BUILD_DIR" \
  -G Ninja \
  "-DCMAKE_TOOLCHAIN_FILE=$TOOLCHAIN" \
  "-DCMAKE_MAKE_PROGRAM=$NINJA_EXE" \
  -DCMAKE_BUILD_TYPE=Release \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_STL=c++_static \
  -DANDROID_NATIVE_API_LEVEL=android-21 \
  -DMNN_BUILD_FOR_ANDROID_COMMAND=true \
  -DMNN_LOW_MEMORY=true \
  -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true \
  -DMNN_BUILD_LLM=true \
  -DMNN_SUPPORT_TRANSFORMER_FUSE=true \
  -DMNN_ARM82=true \
  -DMNN_USE_LOGCAT=true \
  -DMNN_SEP_BUILD=OFF \
  "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384" \
  "-DCMAKE_INSTALL_PREFIX=$BUILD_DIR"

"$CMAKE_EXE" --build "$BUILD_DIR" -j "$JOBS"
"$CMAKE_EXE" --install "$BUILD_DIR"

# ---------- 4. Publish the artifact to mnn-prebuilt/ ----------
ARTIFACT="$(find "$BUILD_DIR" -name libMNN.so -type f | head -n 1)"
[ -n "$ARTIFACT" ] || { echo "ERROR: libMNN.so not found under $BUILD_DIR" >&2; exit 1; }
PREBUILT_DIR="$REPO_ROOT/mnn-prebuilt/arm64-v8a"
mkdir -p "$PREBUILT_DIR"
cp -f "$ARTIFACT" "$PREBUILT_DIR/libMNN.so"
echo ""
echo "SUCCESS: published libMNN.so to $PREBUILT_DIR"
