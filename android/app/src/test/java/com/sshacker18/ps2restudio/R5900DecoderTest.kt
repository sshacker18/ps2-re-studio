package com.sshacker18.ps2restudio

import org.junit.Assert.assertEquals
import org.junit.Test

class R5900DecoderTest {
    @Test fun decodesStackSetup() {
        assertEquals("addiu \\$sp, \\$sp, -32", R5900Decoder.decode(0x27BDFFE0L, 0x100000L))
    }

    @Test fun decodesReturn() {
        assertEquals("jr \\$ra", R5900Decoder.decode(0x03E00008L, 0x10005CL))
    }

    @Test fun decodesConditionalBranchTarget() {
        assertEquals("bne \\$v1, \\$zero, 0x100054", R5900Decoder.decode(0x1460000FL, 0x100014L))
    }

    @Test fun preservesUnknownWords() {
        assertEquals(".word 0xFFBF0010", R5900Decoder.decode(0xFFBF0010L, 0x100008L))
    }
}
