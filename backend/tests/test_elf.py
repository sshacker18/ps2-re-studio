import struct

import pytest

from app.main import analyze_elf


def sample(*, phoff=52, phentsize=32, phnum=0, shoff=52, shentsize=40, shnum=0):
    ident = b"\x7fELF" + bytes([1, 1, 1]) + bytes(9)
    header = struct.pack(
        "<HHIIIIIHHHHHH",
        2, 8, 1, 0x00100000, phoff, shoff, 0, 52,
        phentsize, phnum, shentsize, shnum, 0,
    )
    payload = bytearray(ident + header)
    if phnum and phoff == 52:
        payload.extend(bytes(max(0, phoff + phentsize * phnum - len(payload))))
    if shnum:
        payload.extend(bytes(max(0, shoff + shentsize * shnum - len(payload))))
    return payload


def test_detects_mips_elf32():
    result = analyze_elf(sample(), "sample.elf")
    assert result["format"] == "ELF32"
    assert result["machine"] == "MIPS"
    assert result["ps2_candidate"] is True
    assert result["entry_point"] == "0x100000"
    assert result["sections"] == []


def test_rejects_non_elf():
    with pytest.raises(ValueError, match="Not a valid ELF"):
        analyze_elf(b"not an elf")


def test_rejects_truncated_elf64_header():
    ident = b"\x7fELF" + bytes([2, 1, 1]) + bytes(9)
    header = struct.pack("<HHIQQQIHHHHHH", 2, 8, 1, 0, 64, 0, 0, 64, 56, 0, 64, 0, 0)
    with pytest.raises(ValueError, match="Truncated ELF64"):
        analyze_elf((ident + header)[:-1])


def test_rejects_invalid_program_header_entry_size():
    with pytest.raises(ValueError, match="Program header entry size"):
        analyze_elf(sample(phentsize=8, phnum=1))


def test_rejects_program_header_table_outside_file():
    with pytest.raises(ValueError, match="Program header table"):
        analyze_elf(sample(phoff=1000, phnum=1))


def test_rejects_invalid_section_header_entry_size():
    with pytest.raises(ValueError, match="Section header entry size"):
        analyze_elf(sample(shentsize=8, shnum=1))


def test_rejects_section_name_index_out_of_range():
    data = bytearray(sample(shnum=1))
    struct.pack_into("<H", data, 50, 2)
    with pytest.raises(ValueError, match="string table index"):
        analyze_elf(bytes(data))


def test_rejects_bad_segment_file_range():
    data = bytearray(sample(phnum=1))
    struct.pack_into("<IIIIIIII", data, 52, 1, 4096, 0x1000, 0, 16, 16, 5, 4)
    with pytest.raises(ValueError, match="segment #0 extends"):
        analyze_elf(bytes(data))


def test_rejects_segment_memory_smaller_than_file():
    data = bytearray(sample(phnum=1))
    struct.pack_into("<IIIIIIII", data, 52, 1, 0, 0x1000, 0, 16, 8, 5, 4)
    with pytest.raises(ValueError, match="memory size smaller"):
        analyze_elf(bytes(data))
