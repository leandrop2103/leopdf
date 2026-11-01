<#
.SYNOPSIS
    Builds PdfCraft native libraries and Android application package (APK).
.DESCRIPTION
    Compiles pdfcraft-android cdylib for ARM64 and x86_64 architectures using cargo-ndk,
    copies .so libraries to app/src/main/jniLibs, and optionally packages the APK.
#>

param(
    [switch]$Apk,
    [string]$Configuration = "Release",
    [string]$TargetAbi = "all"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path "$PSScriptRoot\.."
$ProjectDir = "$PSScriptRoot"
$JniLibsDir = "$ProjectDir\app\src\main\jniLibs"

Write-Host "=== PdfCraft Android Build Tool ===" -ForegroundColor Cyan
Write-Host "Repository root: $RepoRoot"

# 1. Verify Rust targets
$targets = rustup target list --installed
if ($targets -notcontains "aarch64-linux-android") {
    Write-Host "Installing rust target aarch64-linux-android..."
    rustup target add aarch64-linux-android
}
if ($targets -notcontains "x86_64-linux-android") {
    Write-Host "Installing rust target x86_64-linux-android..."
    rustup target add x86_64-linux-android
}

# 2. Check cargo-ndk
$hasCargoNdk = Get-Command "cargo-ndk" -ErrorAction SilentlyContinue
if (-not $hasCargoNdk) {
    Write-Host "Installing cargo-ndk for Android NDK toolchain resolution..." -ForegroundColor Yellow
    cargo install cargo-ndk
}

# 3. Resolve Android NDK
$ndkPath = $env:ANDROID_NDK_HOME
if (-not $ndkPath -and $env:ANDROID_HOME) {
    $ndkDirs = Get-ChildItem "$env:ANDROID_HOME\ndk" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
    if ($ndkDirs) { $ndkPath = $ndkDirs[0].FullName }
}
if (-not $ndkPath -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk\ndk")) {
    $ndkDirs = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\ndk" -Directory | Sort-Object Name -Descending
    if ($ndkDirs) { $ndkPath = $ndkDirs[0].FullName }
}
if (-not $ndkPath -and (Test-Path "D:\android-sdk\ndk")) {
    $ndkDirs = Get-ChildItem "D:\android-sdk\ndk" -Directory | Sort-Object Name -Descending
    if ($ndkDirs) { $ndkPath = $ndkDirs[0].FullName }
}

if (-not $ndkPath) {
    Write-Warning "ANDROID_NDK_HOME or Android SDK NDK was not detected automatically."
    Write-Warning "Ensure Android NDK is installed via Android Studio SDK Manager (NDK & CMake)."
    Write-Warning "Or set: `$env:ANDROID_NDK_HOME = 'C:\path\to\android-ndk'"
} else {
    Write-Host "Using Android NDK: $ndkPath" -ForegroundColor Green
    $env:ANDROID_NDK_HOME = $ndkPath
}

# 4. Compile native libraries
Push-Location "$RepoRoot\apps\pdfcraft-android"
try {
    New-Item -ItemType Directory -Force -Path "$JniLibsDir\arm64-v8a" | Out-Null
    New-Item -ItemType Directory -Force -Path "$JniLibsDir\x86_64" | Out-Null

    $releaseFlag = if ($Configuration -eq "Release") { "--release" } else { "" }

    if ($TargetAbi -eq "all" -or $TargetAbi -eq "arm64-v8a") {
        Write-Host "Building cdylib for arm64-v8a (aarch64-linux-android)..." -ForegroundColor Cyan
        cargo ndk -t arm64-v8a -o "$JniLibsDir" build $releaseFlag
    }

    if ($TargetAbi -eq "all" -or $TargetAbi -eq "x86_64") {
        Write-Host "Building cdylib for x86_64 (x86_64-linux-android)..." -ForegroundColor Cyan
        cargo ndk -t x86_64 -o "$JniLibsDir" build $releaseFlag
    }

    Write-Host "Native libraries successfully compiled into: $JniLibsDir" -ForegroundColor Green
}
finally {
    Pop-Location
}

# 5. Optional APK Build
if ($Apk) {
    if (Test-Path "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot") {
        $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
        $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    }
    if (Test-Path "D:\android-sdk") {
        $env:ANDROID_HOME = "D:\android-sdk"
    }
    if (Test-Path "D:\android-sdk\gradle\gradle-8.7\bin") {
        $env:PATH = "D:\android-sdk\gradle\gradle-8.7\bin;$env:PATH"
    }

    Push-Location $ProjectDir
    try {
        Write-Host "Building Android APK..." -ForegroundColor Cyan
        if (Test-Path ".\gradlew.bat") {
            .\gradlew.bat assembleDebug
        } else {
            gradle assembleDebug
        }
        $apkFile = "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk"
        if (Test-Path $apkFile) {
            $mb = [math]::Round((Get-Item $apkFile).Length / 1MB, 2)
            Write-Host "APK build complete: $apkFile ($mb MB)" -ForegroundColor Green
        }
    }
    finally {
        Pop-Location
    }
}
