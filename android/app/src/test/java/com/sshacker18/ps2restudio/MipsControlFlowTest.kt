package com.sshacker18.ps2restudio

import org.junit.Assert.assertTrue
import org.junit.Test

class MipsControlFlowTest {
    @Test fun findsConditionalBranchAndFallthrough() {
        val result = MipsControlFlow.analyze(listOf(0x14600003L, 0L, 0L, 0L, 0L), 0x1000L)
        assertTrue(result.edges.any { it.kind == "conditional" && it.to == 0x1010L })
        assertTrue(result.edges.any { it.kind == "fallthrough" && it.to == 0x1008L })
        assertTrue(result.blockStarts.contains(0x1010L))
    }

    @Test fun identifiesCallAndReturn() {
        val call = MipsControlFlow.analyze(listOf(0x0C000400L, 0L, 0x03E00008L), 0x1000L)
        assertTrue(call.edges.any { it.kind == "call" })
        assertTrue(call.edges.any { it.kind == "return" && it.to == null })
    }
}
