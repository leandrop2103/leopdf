# PdfCraft no Android

Guia rápido para compilação, desenvolvimento e execução do **PdfCraft** no Android.

---

## Estrutura do Suporte a Android

O suporte a Android no PdfCraft é composto por dois elementos principais:

1. **Biblioteca Nativa Rust (`apps/pdfcraft-android`)**:
   - Crate nativo que compila como `cdylib` (`libpdfcraft_android.so`).
   - Baseado exclusivamente nos crates pure-Rust do PdfCraft (`pdfcraft-render` e `pdfcraft-cos`), dispensando compiladores C no pipeline de parsing e renderização.
   - Fornece bindings JNI e C FFI de alto desempenho com exportação direta de buffers de pixels RGBA para `android.graphics.Bitmap`.

2. **Aplicativo Android (`android/`)**:
   - Projeto Android Studio completo em Kotlin (Material 3, AndroidX, Gradle 8.5, SDK 34).
   - Localizado em [`android/`](file:///d:/src/pdfcraft/android) (e também em [`packaging/android/`](file:///d:/src/pdfcraft/packaging/android)).
   - Contém:
     - [`PdfCraftCore.kt`](file:///d:/src/pdfcraft/android/app/src/main/java/ai/storyteller/pdfcraft/PdfCraftCore.kt): Wrapper de lifecycle e renderização JNI.
     - [`PdfCraftView.kt`](file:///d:/src/pdfcraft/android/app/src/main/java/ai/storyteller/pdfcraft/PdfCraftView.kt): View customizada para exibição de páginas com gestos (pinch-to-zoom, pan, duplo toque).
     - [`MainActivity.kt`](file:///d:/src/pdfcraft/android/app/src/main/java/ai/storyteller/pdfcraft/MainActivity.kt): Leitor com seletor de arquivos de sistema (Storage Access Framework) e paginação.

---

## Pré-requisitos

1. **Alvos Android do Rust**:
   ```bash
   rustup target add aarch64-linux-android x86_64-linux-android
   ```

2. **Ferramenta `cargo-ndk`**:
   ```bash
   cargo install cargo-ndk
   ```

3. **Android Studio & NDK**:
   - Android Studio instalado.
   - Android NDK (versão 25.x ou 26.x, instalável pelo *SDK Manager > SDK Tools > NDK (Side by side)*).

---

## Compilação Rápida

### 1. Compilar as bibliotecas nativas `.so`:

No Windows (PowerShell):
```powershell
.\android\build.ps1
```

No Linux / macOS (Bash):
```bash
./android/build.sh
```

As bibliotecas compartilhadas serão geradas em:
- `android/app/src/main/jniLibs/arm64-v8a/libpdfcraft_android.so`
- `android/app/src/main/jniLibs/x86_64/libpdfcraft_android.so`

### 2. Gerar o APK instalável:

```powershell
.\android\build.ps1 -Apk
```
O APK final será gerado em:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## Verificação e Testes

Para testar o crate Android na sua máquina de desenvolvimento:
```bash
cargo test -p pdfcraft-android
```

Para validar a compilação cruzada para as arquiteturas Android sem precisar de emulador conectado:
```bash
cargo check -p pdfcraft-android --target aarch64-linux-android
cargo check -p pdfcraft-android --target x86_64-linux-android
```
