package com.sshacker18.ps2restudio

/** Confidence-labelled function candidates; ELF symbols outrank heuristics. */
internal object FunctionRecovery {
    enum class Confidence { HIGH, LOW }
    data class Function(val address: Long, val name: String, val size: Long, val confidence: Confidence, val source: String)
    data class Symbol(val name: String, val address: Long, val size: Long, val type: Int, val sectionIndex: Int)

    fun fromSymbols(symbols: List<Symbol>, executableSectionIndices: Set<Int>): List<Function> =
        symbols.asSequence()
            .filter { it.type == 2 && it.sectionIndex in executableSectionIndices && it.address >= 0L }
            .distinctBy { it.address to it.name }
            .sortedWith(compareBy<Symbol> { it.address }.thenBy { it.name })
            .map { Function(it.address, it.name, it.size, Confidence.HIGH, "ELF STT_FUNC") }
            .toList()

    fun mergeCandidates(symbols: List<Function>, heuristicAddresses: List<Long>): List<Function> {
        val byAddress = linkedMapOf<Long, Function>()
        symbols.forEach { byAddress[it.address] = it }
        heuristicAddresses.distinct().sorted().forEach { address ->
            if (address >= 0L && address !in byAddress) byAddress[address] =
                Function(address, "sub_" + java.lang.Long.toHexString(address), 0L, Confidence.LOW, "MIPS prologue heuristic")
        }
        return byAddress.values.sortedBy { it.address }
    }

    /** Infer missing sizes only from the next known function boundary. */
    fun inferMissingSizes(functions: List<Function>, executableEnd: Long): List<Function> {
        val sorted = functions.sortedBy { it.address }
        return sorted.mapIndexed { index, function ->
            if (function.size > 0L) function else {
                val next = sorted.getOrNull(index + 1)?.address ?: executableEnd
                function.copy(size = (next - function.address).coerceAtLeast(0L))
            }
        }
    }
}
