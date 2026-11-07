package com.sshacker18.ps2restudio

/**
 * Conservative MIPS III / R5900 instruction formatter.
 *
 * This is a static decoder, not an emulator. Unknown, reserved, and
 * PS2-specific encodings are deliberately preserved as raw words.
 */
internal object R5900Decoder {
    private val registers = arrayOf(
        "zero", "at", "v0", "v1", "a0", "a1", "a2", "a3",
        "t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7",
        "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
        "t8", "t9", "k0", "k1", "gp", "sp", "fp", "ra"
    )

    private fun r(index: Int) = "$" + registers[index and 31]
    private fun hex(value: Long) = "0x" + java.lang.Long.toHexString(value).uppercase()
    private fun raw(word: Long) = ".word " + hex(word)
    private fun signed16(word: Long) = (word and 0xffff).toShort().toInt()
    private fun target(pc: Long, immediate: Int) = pc + 4L + (immediate.toLong() shl 2)

    fun decode(word: Long, pc: Long): String {
        val op = ((word ushr 26) and 0x3f).toInt()
        val rs = ((word ushr 21) and 31).toInt()
        val rt = ((word ushr 16) and 31).toInt()
        val rd = ((word ushr 11) and 31).toInt()
        val sa = ((word ushr 6) and 31).toInt()
        val fn = (word and 0x3f).toInt()
        val imm = signed16(word)
        val uimm = word and 0xffff
        val index = word and 0x03ffffff

        return when (op) {
            0x00 -> decodeSpecial(word, rs, rt, rd, sa, fn)
            0x01 -> decodeRegimm(rs, rt, imm, pc)
            0x02 -> "j " + hex(((pc + 4) and -0x10000000L) or (index shl 2))
            0x03 -> "jal " + hex(((pc + 4) and -0x10000000L) or (index shl 2))
            0x04 -> "beq " + r(rs) + ", " + r(rt) + ", " + hex(target(pc, imm))
            0x05 -> "bne " + r(rs) + ", " + r(rt) + ", " + hex(target(pc, imm))
            0x06 -> if (rt == 0) "blez " + r(rs) + ", " + hex(target(pc, imm)) else raw(word)
            0x07 -> if (rt == 0) "bgtz " + r(rs) + ", " + hex(target(pc, imm)) else raw(word)
            0x08 -> "addi " + r(rt) + ", " + r(rs) + ", " + imm
            0x09 -> "addiu " + r(rt) + ", " + r(rs) + ", " + imm
            0x0a -> "slti " + r(rt) + ", " + r(rs) + ", " + imm
            0x0b -> "sltiu " + r(rt) + ", " + r(rs) + ", " + imm
            0x0c -> "andi " + r(rt) + ", " + r(rs) + ", " + hex(uimm)
            0x0d -> "ori " + r(rt) + ", " + r(rs) + ", " + hex(uimm)
            0x0e -> "xori " + r(rt) + ", " + r(rs) + ", " + hex(uimm)
            0x0f -> "lui " + r(rt) + ", " + hex(uimm)
            0x10 -> decodeCop0(word, rs, rt, rd, sa, fn)
            0x11 -> decodeCop1(word, rs, rt, rd, sa, fn)
            0x12 -> decodeCop2(word, rs, rt, rd, sa, fn)
            0x14 -> "beql " + r(rs) + ", " + r(rt) + ", " + hex(target(pc, imm))
            0x15 -> "bnel " + r(rs) + ", " + r(rt) + ", " + hex(target(pc, imm))
            0x16 -> if (rt == 0) "blezl " + r(rs) + ", " + hex(target(pc, imm)) else raw(word)
            0x17 -> if (rt == 0) "bgtzl " + r(rs) + ", " + hex(target(pc, imm)) else raw(word)
            0x18 -> "daddi " + r(rt) + ", " + r(rs) + ", " + imm
            0x19 -> "daddiu " + r(rt) + ", " + r(rs) + ", " + imm
            0x1c -> decodeMmi(word, rs, rt, rd, sa, fn)
            0x20 -> mem("lb", rt, rs, imm)
            0x21 -> mem("lh", rt, rs, imm)
            0x23 -> mem("lw", rt, rs, imm)
            0x24 -> mem("lbu", rt, rs, imm)
            0x25 -> mem("lhu", rt, rs, imm)
            0x27 -> mem("lwu", rt, rs, imm)
            0x28 -> mem("sb", rt, rs, imm)
            0x29 -> mem("sh", rt, rs, imm)
            0x2b -> mem("sw", rt, rs, imm)
            0x2c -> mem("sdl", rt, rs, imm)
            0x2d -> mem("sdr", rt, rs, imm)
            0x2f -> mem("cache", rt, rs, imm)
            0x30 -> mem("ll", rt, rs, imm)
            0x34 -> mem(" lld", rt, rs, imm).trimStart()
            0x37 -> mem("ld", rt, rs, imm)
            0x38 -> mem("sc", rt, rs, imm)
            0x3c -> mem("scd", rt, rs, imm)
            0x3f -> mem("sd", rt, rs, imm)
            else -> raw(word)
        }
    }

    private fun mem(name: String, rt: Int, rs: Int, imm: Int) =
        name + " " + r(rt) + ", " + imm + "(" + r(rs) + ")"

    private fun decodeSpecial(word: Long, rs: Int, rt: Int, rd: Int, sa: Int, fn: Int): String {
        val three = { name: String -> "$name ${r(rd)}, ${r(rs)}, ${r(rt)}" }
        val two = { name: String -> "$name ${r(rd)}, ${r(rt)}" }
        return when (fn) {
            0x00 -> if (word == 0L) "nop" else two("sll") + ", " + sa
            0x02 -> two("srl") + ", " + sa
            0x03 -> two("sra") + ", " + sa
            0x04 -> three("sllv")
            0x06 -> three("srlv")
            0x07 -> three("srav")
            0x08 -> "jr " + r(rs)
            0x09 -> "jalr " + r(rd) + ", " + r(rs)
            0x0a -> if (rt == 0) "movz " + r(rd) + ", " + r(rs) + ", " + r(rt) else raw(word)
            0x0b -> "movn " + r(rd) + ", " + r(rs) + ", " + r(rt)
            0x0c -> "syscall"
            0x0d -> "break"
            0x0f -> "sync"
            0x10 -> "mfhi " + r(rd)
            0x11 -> "mthi " + r(rs)
            0x12 -> "mflo " + r(rd)
            0x13 -> "mtlo " + r(rs)
            0x14 -> two("dsll") + ", " + sa
            0x16 -> two("dsrl") + ", " + sa
            0x17 -> two("dsra") + ", " + sa
            0x18 -> "mult " + r(rs) + ", " + r(rt)
            0x19 -> "multu " + r(rs) + ", " + r(rt)
            0x1a -> "div " + r(rs) + ", " + r(rt)
            0x1b -> "divu " + r(rs) + ", " + r(rt)
            0x1c -> "dmult " + r(rs) + ", " + r(rt)
            0x1d -> "dmultu " + r(rs) + ", " + r(rt)
            0x1e -> "ddiv " + r(rs) + ", " + r(rt)
            0x1f -> "ddivu " + r(rs) + ", " + r(rt)
            0x20 -> three("add")
            0x21 -> three("addu")
            0x22 -> three("sub")
            0x23 -> three("subu")
            0x24 -> three("and")
            0x25 -> three("or")
            0x26 -> three("xor")
            0x27 -> three("nor")
            0x2a -> three("slt")
            0x2b -> three("sltu")
            0x2c -> three("dadd")
            0x2d -> three("daddu")
            0x2e -> three("dsub")
            0x2f -> three("dsubu")
            0x38 -> two("dsll32") + ", " + sa
            0x3a -> two("dsrl32") + ", " + sa
            0x3b -> two("dsra32") + ", " + sa
            0x3c -> two("dsll") + ", " + (sa + 32)
            0x3e -> two("dsrl") + ", " + (sa + 32)
            0x3f -> two("dsra") + ", " + (sa + 32)
            else -> raw(word)
        }
    }

    private fun decodeRegimm(rs: Int, rt: Int, imm: Int, pc: Long): String {
        val name = when (rt) {
            0x00 -> "bltz"; 0x01 -> "bgez"; 0x02 -> "bltzl"; 0x03 -> "bgezl"
            0x10 -> "bltzal"; 0x11 -> "bgezal"; 0x12 -> "bltzall"; 0x13 -> "bgezall"
            else -> return ".word REGIMM"
        }
        return name + " " + r(rs) + ", " + hex(target(pc, imm))
    }

    private fun decodeCop0(word: Long, rs: Int, rt: Int, rd: Int, sa: Int, fn: Int): String =
        when (rs) {
            0x00 -> "mfc0 " + r(rt) + ", $c0" + rd
            0x04 -> "mtc0 " + r(rt) + ", $c0" + rd
            0x10 -> when (fn) {
                0x01 -> "tlbr"; 0x02 -> "tlbwi"; 0x06 -> "tlbwr"; 0x08 -> "tlbp"
                0x18 -> "eret"; 0x20 -> "wait"; else -> ".word COP0 " + hex(word)
            }
            else -> ".word COP0 " + hex(word)
        }

    private fun decodeCop1(word: Long, rs: Int, rt: Int, rd: Int, sa: Int, fn: Int): String =
        when (rs) {
            0x00 -> "mfc1 " + r(rt) + ", $f" + rd
            0x02 -> "cfc1 " + r(rt) + ", $f" + rd
            0x04 -> "mtc1 " + r(rt) + ", $f" + rd
            0x06 -> "ctc1 " + r(rt) + ", $f" + rd
            else -> ".word COP1 " + hex(word)
        }

    private fun decodeCop2(word: Long, rs: Int, rt: Int, rd: Int, sa: Int, fn: Int): String =
        when (rs) {
            0x00 -> "qmfc2 " + r(rt) + ", $vf" + rd
            0x02 -> "cfc2 " + r(rt) + ", $vi" + rd
            0x04 -> "qmtc2 " + r(rt) + ", $vf" + rd
            0x06 -> "ctc2 " + r(rt) + ", $vi" + rd
            0x08 -> "bc2" + if (rt and 1 == 0) "f " else "t " + hex(word and 0xffff)
            else -> ".word COP2/VU " + hex(word)
        }

    private fun decodeMmi(word: Long, rs: Int, rt: Int, rd: Int, sa: Int, fn: Int): String {
        val sub = ((word ushr 6) and 0x1f).toInt()
        val name = when (fn) {
            0x00 -> "madd"; 0x01 -> "maddu"; 0x04 -> "plzcw"
            0x08 -> "mmi0"; 0x09 -> "mmi2"; 0x10 -> "mmi1"; 0x11 -> "mmi3"
            0x18 -> "mult1"; 0x19 -> "multu1"; 0x20 -> "paddw"
            else -> return ".word MMI " + hex(word)
        }
        return name + " " + r(rd) + ", " + r(rs) + ", " + r(rt) + " [sub=" + sub + "]"
    }
}
