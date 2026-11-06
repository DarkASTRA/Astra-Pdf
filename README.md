# Astra PDF

Astra PDF is an offline, read-only PDF viewer for Android 8.0 and newer. Open a PDF from a file manager or share it from another app to read, zoom, rotate the view, or find text.

## Download

Get the APK from [GitHub Releases](https://github.com/DarkASTRA/Astra-Pdf/releases). After installing, open a PDF and select Astra PDF in Android's app chooser.

## Project structure

| Directory | Contents |
| --- | --- |
| `android/` | Kotlin and Jetpack Compose viewer |
| `astra-pdf-bridge/` | Rust rendering and search bridge |
| `upstream/pdfcraft/` | PdfCraft dependency pinned as a Git submodule |
| `test-fixtures/` | Sample PDFs for native tests |
| `scripts/` | Test fixture generator |

## Build

Install JDK 17, Android SDK Platform 34, Android NDK 27.0.12077973, Rust, and `cargo-ndk`. Set `ANDROID_HOME` to the SDK directory and `ANDROID_NDK_HOME` to the NDK directory.

```powershell
git clone --recurse-submodules https://github.com/DarkASTRA/Astra-Pdf.git
cd Astra-Pdf/astra-pdf-bridge
rustup target add aarch64-linux-android x86_64-linux-android
cargo test --locked
cargo ndk -t arm64-v8a -t x86_64 --platform 26 -o ../android/app/src/main/jniLibs build --release --locked
cd ../android
.\gradlew.bat testDebugUnitTest assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. On macOS or Linux, use `./gradlew`.

## Attribution

Rendering uses [PdfCraft](https://github.com/storytold/pdfcraft) by the ArtCraft Team and contributors, available under MIT or Apache-2.0. See the [third-party notices](android/app/src/main/assets/licenses/) and the licenses in the pinned submodule.
