import struct
from app.main import analyze_elf

def sample():
    ident=b"\x7fELF"+bytes([1,1,1])+bytes(9)
    return ident+struct.pack("<HHIIIIIHHHHHH",2,8,1,0x00100000,52,0,0,52,32,0,40,0,0)

def test_detects_mips_elf32():
    r=analyze_elf(sample(),"sample.elf")
    assert r["format"]=="ELF32" and r["machine"]=="MIPS"
    assert r["ps2_candidate"] is True
    assert r["entry_point"]=="0x100000"

def test_rejects_non_elf():
    try: analyze_elf(b"not an elf")
    except ValueError as e: assert "Not a valid ELF" in str(e)
    else: raise AssertionError("Expected invalid ELF to be rejected")
