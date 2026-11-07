from __future__ import annotations

import struct
from typing import Any

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="PS2 RE Studio API", version="0.2.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)
MAX_UPLOAD_BYTES = 32 * 1024 * 1024
ELF_MAGIC = b"\x7fELF"


def analyze_elf(data: bytes, filename: str = "upload.elf") -> dict[str, Any]:
    if len(data) < 52 or data[:4] != ELF_MAGIC:
        raise ValueError("Not a valid ELF file or file is too small.")

    elf_class, encoding, ident_version = data[4], data[5], data[6]
    if elf_class not in (1, 2) or encoding not in (1, 2):
        raise ValueError("Unsupported ELF class or byte order.")
    if ident_version != 1:
        raise ValueError("Unsupported ELF identification version.")

    bits = 32 if elf_class == 1 else 64
    header_size = 52 if elf_class == 1 else 64
    if len(data) < header_size:
        raise ValueError(f"Truncated ELF{bits} header.")

    endian = "<" if encoding == 1 else ">"
    header_format = endian + ("HHIIIIIHHHHHH" if elf_class == 1 else "HHIQQQIHHHHHH")
    (
        e_type, machine, version, entry, phoff, shoff, flags, ehsize,
        phentsize, phnum, shentsize, shnum, shstrndx,
    ) = struct.unpack_from(header_format, data, 16)

    if version != 1:
        raise ValueError("Unsupported ELF header version.")
    if ehsize < header_size or ehsize > len(data):
        raise ValueError("ELF header size is invalid.")

    expected_ph = 32 if elf_class == 1 else 56
    expected_sh = 40 if elf_class == 1 else 64
    if phnum and phentsize < expected_ph:
        raise ValueError("Program header entry size is invalid.")
    if shnum and shentsize < expected_sh:
        raise ValueError("Section header entry size is invalid.")
    if phnum and (phoff < ehsize or phoff + phentsize * phnum > len(data)):
        raise ValueError("Program header table extends beyond the uploaded file.")
    if shnum and (shoff < ehsize or shoff + shentsize * shnum > len(data)):
        raise ValueError("Section header table extends beyond the uploaded file.")
    if shnum and shstrndx not in (0, 0xFFFF) and shstrndx >= shnum:
        raise ValueError("Section-name string table index is out of range.")

    segments = []
    for i in range(min(phnum, 128)):
        off = phoff + i * phentsize
        if elf_class == 1:
            p_type, p_offset, p_vaddr, _, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from(
                endian + "IIIIIIII", data, off
            )
        else:
            p_type, p_flags, p_offset, p_vaddr, _, p_filesz, p_memsz, p_align = struct.unpack_from(
                endian + "IIQQQQQQ", data, off
            )
        if p_offset + p_filesz > len(data):
            raise ValueError(f"Program segment #{i} extends beyond the uploaded file.")
        if p_memsz < p_filesz:
            raise ValueError(f"Program segment #{i} has memory size smaller than file size.")
        segments.append({
            "index": i, "type": p_type, "offset": p_offset,
            "virtual_address": f"0x{p_vaddr:X}", "file_size": p_filesz,
            "memory_size": p_memsz, "flags": p_flags, "alignment": p_align,
        })

    sections = []
    for i in range(min(shnum, 4096)):
        off = shoff + i * shentsize
        if elf_class == 1:
            sh_name, sh_type, sh_flags, sh_addr, sh_offset, sh_size, sh_link, sh_info, sh_addralign, sh_entsize = struct.unpack_from(
                endian + "IIIIIIIIII", data, off
            )
        else:
            sh_name, sh_type = struct.unpack_from(endian + "II", data, off)
            sh_flags, sh_addr, sh_offset, sh_size = struct.unpack_from(endian + "QQQQ", data, off + 8)
            sh_link, sh_info = struct.unpack_from(endian + "II", data, off + 40)
            sh_addralign, sh_entsize = struct.unpack_from(endian + "QQ", data, off + 48)
        if sh_type != 8 and sh_offset + sh_size > len(data):  # SHT_NOBITS has no file payload
            raise ValueError(f"Section #{i} extends beyond the uploaded file.")
        if sh_link and sh_link >= shnum:
            raise ValueError(f"Section #{i} has an invalid link index.")
        sections.append({
            "index": i, "name_offset": sh_name, "type": sh_type,
            "address": f"0x{sh_addr:X}", "offset": sh_offset, "size": sh_size,
            "flags": f"0x{sh_flags:X}", "link": sh_link, "info": sh_info,
            "alignment": sh_addralign, "entry_size": sh_entsize,
        })

    machines = {8: "MIPS", 3: "Intel 80386", 40: "ARM", 62: "x86-64", 183: "AArch64"}
    types = {0: "NONE", 1: "REL", 2: "EXEC", 3: "DYN", 4: "CORE"}
    return {
        "filename": filename, "file_size": len(data), "format": f"ELF{bits}",
        "endianness": "little" if encoding == 1 else "big", "version": version,
        "type": types.get(e_type, f"Unknown ({e_type})"),
        "machine": machines.get(machine, f"Unknown ({machine})"), "machine_id": machine,
        "entry_point": f"0x{entry:X}", "flags": f"0x{flags:X}",
        "program_header_count": phnum, "section_header_count": shnum,
        "segments": segments, "sections": sections,
        "ps2_candidate": bits == 32 and encoding == 1 and machine == 8,
        "note": (
            "MIPS ELF32 little-endian is consistent with a common PS2 executable format; "
            "this check alone does not prove PS2 compatibility."
        ),
    }


@app.get("/health")
def health():
    return {"status": "ok", "service": "ps2-re-studio-api"}


@app.post("/api/analyze")
async def analyze(file: UploadFile = File(...)):
    name = file.filename or "upload.elf"
    if not name.lower().endswith((".elf", ".o", ".irx")):
        raise HTTPException(400, "Upload an ELF, object, or IRX file.")
    data = await file.read(MAX_UPLOAD_BYTES + 1)
    await file.close()
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "File exceeds the 32 MB upload limit.")
    try:
        return analyze_elf(data, name)
    except (ValueError, struct.error) as exc:
        raise HTTPException(422, str(exc)) from exc
