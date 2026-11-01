# PdfCraft for Android

This directory contains the packaging tooling and complete Android Studio project for building and running **PdfCraft** on Android devices and emulators.

---

## Architecture Overview

PdfCraft on Android is structured for maximum performance, security, and battery efficiency:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android Application (Kotlin)                │
│                                                                 │
│  MainActivity ───► PdfCraftView (Gestures, Pinch-to-Zoom, Pan)  │
│        │                                                        │
│        ▼                                                        │
│  PdfCraftCore (Kotlin wrapper, AutoCloseable Document lifecycle)│
└───────────────────────────────┬─────────────────────────────────┘
                                │ JNI FFI Bridge
┌───────────────────────────────▼─────────────────────────────────┐
│                   apps/pdfcraft-android (Rust cdylib)          │
│                                                                 │
│  • Memory-safe document registry & handle table                 │
│  • Bounded memory allocations (never crashes on malformed files)│
│  • Zero-copy RGBA buffer writing into Android Bitmaps           │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Pure-Rust Engine Pipeline
┌───────────────────────────────▼─────────────────────────────────┐
│     pdfcraft-render ◄──► pdfcraft-cos ◄──► hayro / vello_cpu    │
│                                                                 │
│  • Full PDF 2.0 object syntax parsing                           │
│  • Hardware-independent rasterization & vector graphics         │
│  • Standard 14 metrics & open OFL font rendering                │
└─────────────────────────────────────────────────────────────────┘
```

- **Zero C-Toolchain in Rust Engine**: `pdfcraft-render` and `pdfcraft-cos` are 100% pure Rust, requiring no external C dependencies.
- **Direct Bitmap Integration**: The native renderer directly outputs 32-bit row-major RGBA buffers, loaded into Android `Bitmap.Config.ARGB_8888` via `Bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))` without intermediate allocations.
- **Fail-Safe & Never Crash**: Enforces strict error bounds (`Result<T, AndroidError>`), handles lock poisoning gracefully, and contains untrusted PDF inputs safely.

---

## Prerequisites

1. **Rust Toolchain**:
   - Rust 1.85+ (or current workspace toolchain)
   - Android targets:
     ```bash
     rustup target add aarch64-linux-android x86_64-linux-android
     ```
2. **Android SDK & NDK**:
   - Android Studio Iguana+ (or command-line tools)
   - Android SDK API 34+
   - Android NDK (version 25.x or 26.x, installed via Android Studio SDK Manager: *Tools > SDK Manager > SDK Tools > NDK (Side by side)*)
3. **`cargo-ndk`**:
   ```bash
   cargo install cargo-ndk
   ```

---

## Quick Start: Building

### 1. Build Native Libraries (`libpdfcraft_android.so`)

From PowerShell on Windows:
```powershell
.\packaging\android\build.ps1
```

Or from Bash (Linux / macOS):
```bash
./packaging/android/build.sh
```

This compiles `apps/pdfcraft-android` into:
- `packaging/android/project/app/src/main/jniLibs/arm64-v8a/libpdfcraft_android.so`
- `packaging/android/project/app/src/main/jniLibs/x86_64/libpdfcraft_android.so`

### 2. Open & Run in Android Studio

1. Launch Android Studio.
2. Select **Open**, and navigate to:
   ```
   pdfcraft/packaging/android/project
   ```
3. Wait for Gradle sync to complete.
4. Select a connected device (physical phone or emulator) and click **Run** (`Shift + F10`).

### 3. Build APK Headlessly

```powershell
.\packaging\android\build.ps1 -Apk
```
The debug APK will be generated at:
`packaging/android/project/app/build/outputs/apk/debug/app-debug.apk`

---

## Testing

Run unit tests for the Android engine on your host workstation:
```bash
cargo test -p pdfcraft-android
```

Verify cross-compilation for Android ABIs without needing an attached device:
```bash
cargo check -p pdfcraft-android --target aarch64-linux-android
cargo check -p pdfcraft-android --target x86_64-linux-android
```
