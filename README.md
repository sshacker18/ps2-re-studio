# PS2 RE Studio

A native, offline-first Android workbench for inspecting PlayStation 2 homebrew ELF executables.

## Android APK (current milestone)
The Android app runs fully on-device. It does not need a hosted backend, account, or network connection to inspect files.

- Select an ELF using Android's system file picker.
- Parses ELF32/ELF64 headers and load segments.
- Identifies MIPS ELF32 little-endian files as **PS2 format candidates**, not guaranteed-compatible executables.
- Enforces a 32 MB file-size limit.

## Build the APK
Open the repository's **Actions** tab and select the latest **Android APK** run. Download the `ps2-re-studio-debug-apk` artifact and extract `app-debug.apk), then install it on your Android device. GitHub Actions builds the APK on every push to main and on manual workflow dispatch.

For a local build, install JDK 17 and Gradle 8.9, then run:
```bash
cd android
gradle :app:assembleDebug
```

## Roadmap
1. Offline ELF metadata analyzer (current)
2. Symbol and section inspection
3. MIPS disassembly and function discovery
4. Optional Ghidra integration
5. PS2Recomp workflow experiments

## Legal and safety
Use homebrew, open-source samples, or game data you are legally entitled to use. The app does not download commercial game images, BIOS files, or copyrighted assets. Selected files are read locally and are not uploaded.
