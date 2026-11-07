# PS2 RE Studio

A mobile-first web workbench for inspecting PlayStation 2 homebrew executables and building a repeatable reverse-engineering workflow.

## Current milestone: ELF Analyzer
- Upload .elf, .irx, and object files up to 32 MB.
- Parses ELF32/ELF64 headers and load segments.
- Identifies MIPS ELF32 little-endian files as **PS2 candidates**, not guaranteed-compatible binaries.
- Responsive browser UI and automated parser tests.

## Run locally
Requires Python 3.11+.
```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
PYTHONPATH=. uvicorn app.main:app --host 0.0.0.0 --port 8000
```
Open frontend/index.html. The UI defaults to http://localhost:8000. Configure window.PS2_API_URL for a deployed API and restrict CORS to the frontend origin before production.

## API
- GET /health
- POST /api/analyze (multipart field: file)

## Roadmap
1. ELF metadata analyzer (current)
2. Symbol and section inspection
3. MIPS disassembly and function discovery
4. Ghidra project integration
5. PS2Recomp workflow experiments
6. Isolated build workers and artifact reports

## Legal and safety
Use homebrew, open-source samples, or game data you are legally entitled to use. This project does not download commercial game images, BIOS files, or copyrighted assets. Uploaded bytes are processed in memory by this initial API and are not written to disk by the application.
