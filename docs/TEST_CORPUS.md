# ELF Validation Corpus

This document records the four PS2 homebrew ELF fixtures supplied for local validation of PS2 RE Studio. The binary files are retained in the user's upload/session, not committed to this repository; the hashes below identify the exact test inputs.

## Fixture inventory

| Fixture | Size (bytes) | ELF | Sections | .text bytes | Symbols/debug |
|---|---:|---|---:|---:|---|
| `console_demo.elf` | 233,856 | ELF32, little-endian, MIPS | 13 | 149,416 | Stripped; no readable symbol names |
| `cubes_demo.elf` | 243,288 | ELF32, little-endian, MIPS | 13 | 158,104 | Stripped; no readable symbol names |
| `dungeon_game.elf` | 4,386,402 | ELF32, little-endian, MIPS | 25 | 220,640 | Not stripped; symbol table and DWARF sections present |
| `play_adpcm_demo.elf` | 1,144,640 | ELF32, little-endian, MIPS | 14 | 144,184 | Stripped; no readable symbol names |

All four files are statically linked MIPS ELF32 little-endian executables with entry point `0x1000D8` and one program header. Host `file`/ `readelf` confirmed the ELF headers and section metadata. The host `objdump` available in the validation environment could not disassemble this MIPS architecture, so this is not an independent instruction-decoding oracle.

## SHA-256

| Fixture | SHA-256 |
|---|---|
| `console_demo.elf` | `c1fa06a58e3d3625c8d3afc1d2ac3a1744cf9da4a6b8a53840b98ef89c3cad7d` |
| `cubes_demo.elf` | `5af35384a6ed7ea880a0370a6490bd178a7e53a9b1a6179c75638c406613a8e8` |
| `dungeon_game.elf` | `25faee2f98483f7f86d0dd7043e7b506c998728989347c8a38ae8af49c0a1af4` |
| `play_adpcm_demo.elf` | `4309a93ed8f446205b47148f619e73fec1e60adae71bdfa8097cc7e981de9c93` |

## Validation coverage

- **Stripped fixtures** (`console_demo`, `cubes_demo`, `play_adpcm_demo`): exercise symbol-less function discovery and low-confidence heuristic labeling.
- **Debug fixture** (`dungeon_game`): exercises named `STT_FUNC` symbols and the presence of DWARF `.debug_info`, `.debug_line`, `.debug_frame`, `.debug_pubnames`, `.debug_aranges`, and `.debug_ranges` sections.
- All four provide real ELF32/MIPS section and program-header layouts for parser regression checks.

## Limitations / next checks

A matching ELF header is only a PS2-format candidate signal; it does not prove the file runs on PS2 hardware or an emulator. The app still needs on-device regression testing with these exact binaries. DWARF parsing and source-line recovery are not implied by the presence of DWARF sections. Control-flow output should be compared with a MIPS/R5900-capable disassembler before being treated as authoritative.
