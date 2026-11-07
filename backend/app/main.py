from __future__ import annotations

import struct
from typing import Any

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="PS2 RE Studio API", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Restrict this to the deployed frontend origin before production.
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

MAX_UPLOAD_BYTES = 32 * 1024 * 1024
ELF_MAGIC = b"\x7fELF"


def analyze_elf(data: bytes, filename: str = "upload.elf") -> dict[str, Any]:
    if len(data) < 52 or data[:4] != ELF_MAGIC:
        raise ValueError("Not a valid ELF file or file is too small.")
    elf_class, encoding, version = data[4], data[5], data[6]
    if elf_class not in (1, 2):
        raise ValueError("Unsupported ELF class.")
    if encoding not in (1, 2):
        raise ValueError("Unsupported ELF byte order.")
    endian = "<" if encoding == 1 else ">"
    if elf_class == 1:
        if len(data) < 52:
            raise ValueError("Truncated ELF32 header.")
        header = struct.unpack_from(endian + "HHIIIIIHHHHHH", data, 16)
        e_type, machine, _, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx = header
        bits = 32
    else:
        if len(data) < 64:
            raise ValueError("Truncated ELF64 header.")
        header = struct.unpack_from(endian + "HHIQQQIHHHHHH", data, 16)
        e_type, machine, _, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx = header
        bits = 64

    machine_names = {8: "MIPS", 3: "Intel 80386", 40: "ARM", 62: "x86-64", 183: "AArch64"}
    type_names = {0: "NONE", 1: "REL", 2: "EXEC", 3: "DYN", 4: "CORE"}
    if phnum and (phoff + phentsize * phnum > len(data)):
        raise ValueError("Program header table extends beyond the uploaded file.")
    if shnum and (shoff + shentsize * shnum > len(data)):
        raise ValueError("Section header table extends beyond the uploaded file.")

    segments = []
    for i in range(min(phnum, 128)):
        off = phoff + i * phentsize
        if elf_class == 1:
            p_type, p_offset, p_vaddr, _, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from(endian + "IIIIIIII", data, off)
        else:
            p_type, p_flags, p_offset, p_vaddr, _, p_filesz, p_memsz, p_align = struct.unpack_from(endian + "IIQQQQQQ", data, off)
        segments.append({
            "index": i, "type": p_type, "offset": p_offset,
            "virtual_address": f"0x{p_vaddr:X}", "file_size": p_filesz,
            "memory_size": p_memsz, "flags": p_flags, "alignment": p_align,
        })

    return {
        "filename": filename,
        "file_size": len(data),
        "format": f"ELF{bits}",
        "endianness": "little" if encoding == 1 else "big",
        "version": version,
        "type": type_names.get(e_type, f"Unknown ({e_type})"),
        "machine": machine_names.get(machine, f"Unknown ({machine})"),
        "machine_id": machine,
        "entry_point": f"0x{entry:X}",
        "flags": f"0x{flags:X}",
        "program_header_count": phnum,
        "section_header_count": shnum,
        "segments": segments,
        "ps2_candidate": bits == 32 and encoding == 1 and machine == 8,
        "note": "MIPS ELF32 little-endian is consistent with a common PS2 executable format; this check alone does not prove PS2 compatibility.",
    }


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ps2-re-studio-api"}


@app.post("/api/analyze")
async def analyze(file: UploadFile = File(...)) -> dict[str, Any]:
    name = file.filename or "upload.elf"
    if not name.lower().endswith((".elf", ".o", ".irx")):
        raise HTTPException(status_code=400, detail="Upload an ELF, object, or IRX file.")
    data = await file.read(MAX_UPLOAD_BYTES + 1)
    await file.close()
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="File exceeds the 32 MB upload limit.")
    try:
        return analyze_elf(data, name)
    except (ValueError, struct.error) as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
