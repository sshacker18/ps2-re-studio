package com.sshacker18.ps2restudio

import org.junit.Assert.*
import org.junit.Test

class MipsControlFlowTest {
    @Test fun findsConditionalBranchFallthroughAndDelaySlot() {
        val result = MipsControlFlow.analyze(listOf(0x14600003L, 0L, 0L, 0L, 0L), 0x1000L)
        assertTrue(result.edges.any { it.kind == "conditional" && it.to == 0x1010L && it.delaySlot == 0x1004L })
        assertTrue(result.edges.any { it.kind == "fallthrough" && it.to == 0x1008L })
        assertTrue(result.blockStarts.contains(0x1010L))
        assertTrue(result.delaySlots.contains(0x1004L))
    }

    @Test fun identifiesDirectCallIndirectReturnAndDelaySlots() {
        val result = MipsControlFlow.analyze(listOf(0x0C000400L, 0L, 0x03E00008L, 0L), 0x1000L)
        assertTrue(result.edges.any { it.kind == "call" && it.delaySlot == 0x1004L })
        assertTrue(result.edges.any { it.kind == "return" && it.to == null && it.delaySlot == 0x100cL })
    }

    @Test fun emptyInputProducesEmptyGraph() {
        val result = MipsControlFlow.analyze(emptyList(), 0x1000L)
        assertTrue(result.blockStarts.isEmpty())
        assertTrue(result.edges.isEmpty())
        assertTrue(result.delaySlots.isEmpty())
    }
}
