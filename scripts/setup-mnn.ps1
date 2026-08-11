# Prepare the gitignored MNN prerequisites required by the :mnn module:
#   1. vendor/MNN            - MNN source tree pinned to a known-good commit (headers + cmake project)
#   2. mnn-prebuilt/arm64-v8a/libMNN.so - prebuilt runtime used for linking and packaging
#
# Usage: powershell -File scripts/setup-mnn.ps1 [-SkipBuild]
#   -SkipBuild  Only ensure the vendor/MNN checkout, do not rebuild libMNN.so
#
# Requires: git; for the build step also NDK 25.x + Android SDK CMake
# (scripts/build-mnn-android.ps1 resolves them from %LOCALAPPDATA%\Android\Sdk).

param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$RepoRoot     = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$VendorDir    = Join-Path $RepoRoot "vendor\MNN"
$MnnRepoUrl   = "https://github.com/alibaba/MNN.git"
# Pinned upstream commit (keep in sync with scripts/setup-mnn.sh).
$PinnedCommit = "1d535d728362d0ee8a4cc6d854b970c8d7f94e02"

function Test-MnnCheckout {
    if (-not (Test-Path (Join-Path $VendorDir "CMakeLists.txt"))) { return $false }
    $head = & git -C $VendorDir rev-parse HEAD 2>$null
    return ($LASTEXITCODE -eq 0 -and $head -eq $PinnedCommit)
}

function Invoke-PinnedCheckout {
    # Shallow-fetch exactly the pinned commit (GitHub serves fetch-by-full-sha).
    if (-not (Test-Path (Join-Path $VendorDir ".git"))) {
        New-Item -ItemType Directory -Force -Path $VendorDir | Out-Null
        & git init --quiet $VendorDir
        if ($LASTEXITCODE -ne 0) { throw "git init failed in $VendorDir" }
        & git -C $VendorDir remote add origin $MnnRepoUrl
        if ($LASTEXITCODE -ne 0) { throw "git remote add failed in $VendorDir" }
    }
    Write-Host "Fetching alibaba/MNN @ $($PinnedCommit.Substring(0, 7)) (shallow)..."
    & git -C $VendorDir fetch --depth 1 origin $PinnedCommit
    if ($LASTEXITCODE -ne 0) { throw "git fetch of pinned MNN commit failed" }
    & git -C $VendorDir checkout --quiet --detach $PinnedCommit
    if ($LASTEXITCODE -ne 0) { throw "git checkout of pinned MNN commit failed" }
}

# ---------- 1. Ensure vendor/MNN at the pinned commit ----------
if (Test-MnnCheckout) {
    Write-Host "vendor/MNN already at pinned commit $($PinnedCommit.Substring(0, 7)), skipping clone."
} elseif (Test-Path (Join-Path $VendorDir "CMakeLists.txt")) {
    # Existing checkout at a different commit: re-pin it in place.
    Write-Host "vendor/MNN exists but is not at the pinned commit; re-pinning..."
    Invoke-PinnedCheckout
} else {
    Invoke-PinnedCheckout
}

if (-not (Test-Path (Join-Path $VendorDir "CMakeLists.txt"))) {
    throw "vendor/MNN checkout is incomplete: $VendorDir"
}

# ---------- 2. Build libMNN.so and publish it to mnn-prebuilt/ ----------
if ($SkipBuild) {
    Write-Host "-SkipBuild set: not rebuilding mnn-prebuilt/libMNN.so"
    exit 0
}

& (Join-Path $PSScriptRoot "build-mnn-android.ps1")
if ($LASTEXITCODE -ne 0) { throw "scripts/build-mnn-android.ps1 failed" }

$Artifact = Get-ChildItem -Recurse -Filter libMNN.so -Path (Join-Path $VendorDir "project\android\build_64") |
    Select-Object -First 1
if (-not $Artifact) { throw "libMNN.so not found after the MNN Android build" }

$PrebuiltDir = Join-Path $RepoRoot "mnn-prebuilt\arm64-v8a"
New-Item -ItemType Directory -Force -Path $PrebuiltDir | Out-Null
Copy-Item $Artifact.FullName (Join-Path $PrebuiltDir "libMNN.so") -Force
Write-Host ""
Write-Host "SUCCESS: published $($Artifact.Length / 1MB) MB libMNN.so to $PrebuiltDir"
