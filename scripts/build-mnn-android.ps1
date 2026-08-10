# Build MNN for Android arm64-v8a (LLM-enabled, CPU-only, text-only trim)
# Usage: powershell -File scripts/build-mnn-android.ps1 [-Clean]
# Requires: NDK 25.x+ and Android SDK CMake (with bundled ninja), no make needed.

param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

# ---------- Paths (edit here if SDK/NDK location differs) ----------
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$SdkRoot  = "$env:LOCALAPPDATA\Android\Sdk"
$NdkRoot  = Join-Path $SdkRoot "ndk\25.1.8937393"
$CMakeExe = Join-Path $SdkRoot "cmake\3.22.1\bin\cmake.exe"
$NinjaExe = Join-Path $SdkRoot "cmake\3.22.1\bin\ninja.exe"
$Toolchain = Join-Path $NdkRoot "build\cmake\android.toolchain.cmake"

$SrcDir   = Join-Path $RepoRoot "vendor\MNN"
$BuildDir = Join-Path $SrcDir   "project\android\build_64"

foreach ($p in @($CMakeExe, $NinjaExe, $Toolchain, (Join-Path $SrcDir "CMakeLists.txt"))) {
    if (-not (Test-Path $p)) { throw "Required path not found: $p" }
}

if ($Clean -and (Test-Path $BuildDir)) {
    Write-Host "Cleaning previous build dir: $BuildDir"
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

$Jobs = [Environment]::ProcessorCount
Write-Host "Repo:    $RepoRoot"
Write-Host "NDK:     $NdkRoot"
Write-Host "Build:   $BuildDir"
Write-Host "Jobs:    $Jobs"

# ---------- Configure ----------
# Note: MNN_CPU_WEIGHT_DEQUANT_GEMM has been removed in MNN master; kept here
# harmlessly for compatibility with the documented flag set (CMake ignores it).
# Vision / audio / diffusion / OpenCL are intentionally left OFF to keep the
# library text-only and small.
& $CMakeExe `
    -S $SrcDir `
    -B $BuildDir `
    -G Ninja `
    "-DCMAKE_TOOLCHAIN_FILE=$Toolchain" `
    "-DCMAKE_MAKE_PROGRAM=$NinjaExe" `
    -DCMAKE_BUILD_TYPE=Release `
    -DANDROID_ABI=arm64-v8a `
    -DANDROID_STL=c++_static `
    -DANDROID_NATIVE_API_LEVEL=android-21 `
    -DMNN_BUILD_FOR_ANDROID_COMMAND=true `
    -DMNN_LOW_MEMORY=true `
    -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true `
    -DMNN_BUILD_LLM=true `
    -DMNN_SUPPORT_TRANSFORMER_FUSE=true `
    -DMNN_ARM82=true `
    -DMNN_USE_LOGCAT=true `
    -DMNN_SEP_BUILD=OFF `
    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384" `
    "-DCMAKE_INSTALL_PREFIX=$BuildDir"
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed (exit $LASTEXITCODE)" }

# ---------- Build ----------
& $CMakeExe --build $BuildDir -j $Jobs
if ($LASTEXITCODE -ne 0) { throw "CMake build failed (exit $LASTEXITCODE)" }

# ---------- Install ----------
& $CMakeExe --install $BuildDir
if ($LASTEXITCODE -ne 0) { throw "CMake install failed (exit $LASTEXITCODE)" }

# ---------- Report artifact ----------
$Artifact = Get-ChildItem -Recurse -Filter libMNN.so -Path $BuildDir | Select-Object -First 1
if (-not $Artifact) { throw "libMNN.so not found under $BuildDir" }
Write-Host ""
Write-Host "SUCCESS: $($Artifact.FullName) ($([math]::Round($Artifact.Length / 1MB, 2)) MB)"
