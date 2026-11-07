package com.sshacker18.ps2restudio

/** Static MIPS control-flow recovery with explicit branch/jump delay-slot metadata. */
internal object MipsControlFlow {
    data class Edge(val from: Long, val to: Long?, val kind: String, val delaySlot: Long? = null)
    data class Result(val blockStarts: List<Long>, val edges: List<Edge>, val delaySlots: Set<Long>)

    fun analyze(words: List<Long>, baseAddress: Long): Result {
        if (words.isEmpty()) return Result(emptyList(), emptyList(), emptySet())
        val end = baseAddress + words.size * 4L
        val starts = sortedSetOf(baseAddress)
        val edges = mutableListOf<Edge>()
        val delaySlots = linkedSetOf<Long>()

        words.forEachIndexed { index, word ->
            val pc = baseAddress + index * 4L
            val op = ((word ushr 26) and 0x3f).toInt()
            val rs = ((word ushr 21) and 31).toInt()
            val rt = ((word ushr 16) and 31).toInt()
            val fn = (word and 0x3f).toInt()
            val imm = (word and 0xffff).toShort().toInt()
            val slot = pc + 4L
            val afterSlot = pc + 8L
            val hasSlot = slot < end

            fun addTransfer(target: Long?, kind: String) {
                if (hasSlot) delaySlots.add(slot)
                edges.add(Edge(pc, target, kind, if (hasSlot) slot else null))
                if (target != null && target in baseAddress until end && (target - baseAddress) % 4L == 0L) starts.add(target)
            }
            fun addFallthrough(kind: String = "fallthrough") {
                if (afterSlot < end) {
                    edges.add(Edge(pc, afterSlot, kind, if (hasSlot) slot else null))
                    starts.add(afterSlot)
                }
            }

            when {
                op in setOf(0x04, 0x05, 0x06, 0x07, 0x14, 0x15, 0x16, 0x17) -> {
                    addTransfer(pc + 4L + (imm.toLong() shl 2), "conditional")
                    addFallthrough()
                }
                op == 0x01 && rt in setOf(0, 1, 2, 3, 16, 17, 18, 19) -> {
                    addTransfer(pc + 4L + (imm.toLong() shl 2), "regimm")
                    addFallthrough()
                }
                op == 0x02 || op == 0x03 -> {
                    val destination = ((pc + 4L) and -0x10000000L) or ((word and 0x03ffffff) shl 2)
                    addTransfer(destination, if (op == 0x03) "call" else "jump")
                    if (op == 0x03) addFallthrough("return-site")
                }
                op == 0 && (fn == 0x08 || fn == 0x09) -> {
                    if (hasSlot) delaySlots.add(slot)
                    addTransfer(null, if (fn == 0x08 && rs == 31) "return" else if (fn == 0x09) "indirect-call" else "indirect-jump")
                    if (fn == 0x09) addFallthrough("return-site")
                }
            }
        }
        return Result(starts.toList(), edges, delaySlots)
    }
}
