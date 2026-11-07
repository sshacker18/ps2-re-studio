package com.sshacker18.ps2restudio

import org.junit.Assert.*
import org.junit.Test

class FunctionRecoveryTest {
    @Test fun filtersToExecutableFunctionSymbolsAndMarksHighConfidence() {
        val result = FunctionRecovery.fromSymbols(listOf(
            FunctionRecovery.Symbol("_root", 0x1000, 0, 2, 1),
            FunctionRecovery.Symbol("texture", 0x1100, 0x40, 2, 2),
            FunctionRecovery.Symbol("global", 0x1200, 4, 1, 1)
        ), setOf(1))
        assertEquals(1, result.size)
        assertEquals("_root", result.single().name)
        assertEquals(FunctionRecovery.Confidence.HIGH, result.single().confidence)
    }

    @Test fun heuristicDoesNotOverrideSymbolAndHasLowConfidence() {
        val symbol = FunctionRecovery.Function(0x1000, "_root", 0x20, FunctionRecovery.Confidence.HIGH, "ELF STT_FUNC")
        val result = FunctionRecovery.mergeCandidates(listOf(symbol), listOf(0x1000, 0x1040))
        assertEquals(2, result.size)
        assertEquals(FunctionRecovery.Confidence.HIGH, result[0].confidence)
        assertEquals(FunctionRecovery.Confidence.LOW, result[1].confidence)
    }

    @Test fun infersMissingSizeFromNextFunction() {
        val fs = listOf(
            FunctionRecovery.Function(0x1000, "a", 0, FunctionRecovery.Confidence.HIGH, "ELF"),
            FunctionRecovery.Function(0x1080, "b", 0x20, FunctionRecovery.Confidence.HIGH, "ELF")
        )
        assertEquals(0x80L, FunctionRecovery.inferMissingSizes(fs, 0x1200)[0].size)
        assertEquals(0x20L, FunctionRecovery.inferMissingSizes(fs, 0x1200)[1].size)
    }
}
