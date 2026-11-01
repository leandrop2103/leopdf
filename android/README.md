# PdfCraft Android Project

Complete Android application and Gradle project for **PdfCraft**.

---

## Estrutura do Projeto

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/ai/storyteller/pdfcraft/
│       │   ├── MainActivity.kt        # Interface de abertura e navegação
│       │   ├── PdfCraftCore.kt        # Ponte JNI e wrapper Kotlin
│       │   └── PdfCraftView.kt        # View com zoom (pinch) e pan
│       ├── res/                       # Layouts, cores, temas e strings
│       └── jniLibs/                   # Binários libpdfcraft_android.so (.gitignore/gerados)
│           ├── arm64-v8a/
│           └── x86_64/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── build.ps1                          # Script de build para Windows
├── build.sh                           # Script de build para Linux / macOS
└── README.md
```

---

## Como Abrir e Rodar no Android Studio

1. Abra o **Android Studio**.
2. Clique em **Open** e selecione a pasta:
   ```
   pdfcraft/android
   ```
3. Aguarde o Gradle sincronizar as dependências.
4. Para compilar as bibliotecas nativas `.so`, execute no terminal ou no PowerShell:
   ```powershell
   .\android\build.ps1
   ```
5. Conecte seu dispositivo ou inicie um emulador Android e clique em **Run** (`Shift + F10`).

---

## Gerar APK Direto pelo Terminal

No PowerShell:
```powershell
.\android\build.ps1 -Apk
```

Ou no Linux / macOS:
```bash
./android/build.sh --apk
```

O arquivo `.apk` final será gerado em:
`android/app/build/outputs/apk/debug/app-debug.apk`
