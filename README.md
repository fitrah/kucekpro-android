# KucekPro Android

Native Android shell for KucekPro Laundry.

The app loads `https://laundry.proyek.org` at runtime through Capacitor and keeps the native Android layer separate from the main web/backend repo.

## Requirements

- Node.js 22+
- npm
- Android Studio or Android SDK
- JDK 21

## Setup

```bash
npm install
npm run sync
```

## Build Debug APK

```bash
npm run android:build:debug
```

Debug APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Open In Android Studio

```bash
npm run android:open
```

## Notes

- Package ID: `id.co.proyek.kucekpro`
- App name: `KucekPro`
- Production web URL: `https://laundry.proyek.org`
- Native Bluetooth thermal printer bridge lives in `android/app/src/main/java/id/co/proyek/kucekpro/ThermalPrinterPlugin.java`.
