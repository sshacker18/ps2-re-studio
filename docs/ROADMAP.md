# PS2 RE Studio — Engineering Phases

Target: standalone Android APK, validated on Redmi A15 5G / Android 16 (4 GB RAM).

## Engineering rules

- Offline-first: selected files stay on-device; no required hosting or account.
- Bounded memory: stream file reads where practical, enforce size limits, and avoid duplicate full-file buffers.
- Evidence-aware output: distinguish confirmed metadata, heuristic candidates, and unknown behavior.
- Compatibility: preserve unknown instruction words rather than guessing mnemonics.
- Runtime separation: static analysis is not game execution; playback is enabled only after a real compatible core is integrated and tested.

## Phase 1 — Static analysis foundation (in progress)

- [x] Keep Android-native APK workflow.
- [x] Separate instruction formatting from the activity.
- [x] Add conservative MIPS III / R5900 decoder coverage for common integer, branch, load/store, and coprocessor transfer instructions.
- [x] Preserve unverified MMI and VU encodings as raw words.
- [x] Add known-vector unit tests for stack setup, return, branch target, and unknown-word preservation.
- [x] Validate APK workflow baseline and compare the analyzer against the uploaded dungeon_game.elf report (4.39 MB ELF32 little-endian MIPS EXEC; entry 0x1000D8; 59 named STT_FUNC records including _root at 0x10021C).
- [x] Add symbol-aware function records restricted to executable sections, HIGH confidence for ELF STT_FUNC, and explicit heuristic labels for prologue-derived candidates.
- [ ] Install the latest APK on Redmi A15 5G / Android 16 and verify the same ELF end-to-end on-device.

## Phase 2 — Function and control-flow recovery

- [x] Recover functions from ELF STT_FUNC symbols and executable sections; infer only missing sizes from the next symbol/section boundary.
- [x] Resolve direct J/JAL and conditional branch references.
- [x] Build bounded basic-block/control-flow graph candidates over up to 256 KiB of .text.
- [x] Track delay-slot addresses and distinguish call, branch, return, indirect-call, and unresolved edges.
- [x] Export compact, searchable text reports including function confidence and CFG edges.
- [ ] Validate CFG accuracy against a known-good disassembler and the complete dungeon_game.elf on device; indirect jumps remain unresolved.

## Phase 3 — PS2 subsystem intelligence

- Identify EE/R5900-specific instructions and privileged operations.
- Detect likely VU, GIF, DMA, GS, SIF, and IOP interaction sites without overclaiming semantics.
- Add string, asset, texture, and audio-reference exploration.
- Correlate debug symbols and source paths when present.

## Phase 4 — Native mobile workbench

- Provide separate Game Deck, Disassembly, Functions, Call Graph, Memory Map, and Game Lab screens.
- Keep logs out of the main console; provide explicit text-report export.
- Use background work, cancellation, progress, and bounded result windows.
- Preserve the current working APK package identity to avoid Android package-conflict reinstall failures.

## Phase 5 — Execution and playback research

- Evaluate legally usable PS2 emulator/recompiler cores and their Android integration requirements.
- Verify licensing, ABI support, native build requirements, graphics/audio/input interfaces, and memory footprint.
- Integrate only after a reproducible compatibility test exists.
- Keep PLAY disabled or clearly marked unavailable until a game actually boots and renders correctly.

## Current limitations

The current decoder is a partial static disassembler, not a complete R5900 decoder, decompiler, emulator, or recompiler. MMI/VU instruction semantics and PS2 hardware behavior require verified implementation and test vectors. ELF acceptance does not prove that an executable will boot on a PS2 runtime.
