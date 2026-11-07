# PS2 RE Studio

A native, offline-first Android workbench for inspecting PlayStation 2 homebrew ELF executables.

## Android app

- Select ELF files with Android's system document picker; files are read locally.
- Parse ELF32/ELF64 headers, program segments, section headers, and symbol tables.
- Identify MIPS ELF32 little-endian files as **PS2 format candidates**, never as proof of compatibility.
- Disassemble a bounded window of standard MIPS instructions using the conservative R5900 decoder.
- Recover symbol-backed function records with HIGH confidence labels and keep prologue heuristics explicitly low-confidence.
- Infer missing function sizes only from the next known symbol or executable-section boundary.
- Recover branch/jump edges, delay-slot addresses, basic-block candidates, and bounded per-function CFG summaries over up to 256 KiB of `.text`.
- Search the generated report, copy it to the clipboard, or export it as a text file.
- Enforce a 32 MB input limit and display an explicit notice that playback is not yet connected.

The app is offline-first. The optional FastAPI prototype is separate; the Android analyzer does not upload selected files.

## Build the APK

Open the repository's **Actions** tab and select the latest **Android APK** run. Download the `ps2-re-studio-debug-apk` artifact and extract `app-debug.apk`, then install it on your Android device.

For a local build, install JDK 17 and Gradle 8.9, then run:
```bash
cd android
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

## Optional backend

The FastAPI service validates ELF header sizes, program/section table bounds, segment file ranges, and section links. It is a prototype API, not required by the Android app.

```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload
pytest -q
```

## Current limitations

- This is a static analysis workbench, not a PS2 emulator.
- MMI, VU, GIF, DMA, GS, SIF, and IOP-specific decoding are not yet implemented.
- Static control-flow recovery cannot resolve all indirect jumps or prove function boundaries.
- ELF signature and MIPS machine metadata do not establish that a file is a valid or runnable PS2 game.

## Legal use

Use homebrew, open-source samples, or game data you are legally entitled to use. The app does not download commercial game images, BIOS files, or copyrighted assets.
