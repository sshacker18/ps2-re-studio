import struct

from app.main import analyze_elf


def minimal_mips_elf32() -> bytes:
    ident = b"\x7fELF" + bytes([1, 1, 1]) + bytes(9)
    header = struct.pack("<HHIIIIIHHHHHH", 2, 8, 1, 0x00100000, 52, 0, 0, 52, 32, 0, 40, 0, 0)
    return ident + header


def test_detects_mips_elf32():
    report = analyze_elf(minimal_mips_elf32(), "sample.elf")
    assert report["format"] == "ELF32"
    assert report["machine"] == "MIPS"
    assert report["ps2_candidate"] is True
    assert report["entry_point"] == "0x100000"


def test_rejects_non_elf():
    try:
        analyze_elf(b"not an elf")
    except ValueError as exc:
        assert "Not a valid ELF" in str(exc)
    else:
        raise AssertionError("Expected invalid ELF to be rejected")
