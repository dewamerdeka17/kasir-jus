# Jus Merdeka POS Android

Aplikasi Android native shell yang memuat deployment aman `https://jus-merdeka-jumbo-pos.vercel.app`.

## Build debug APK

```powershell
./gradlew.bat assembleDebug
```

APK dihasilkan di `app/build/outputs/apk/debug/app-debug.apk`. Build debug ditandatangani otomatis hanya untuk pengujian. Rilis Play Store harus memakai keystore milik pemilik aplikasi.
