package com.sshacker18.ps2restudio

/**
 * Lightweight control-flow edge recovery for standard MIPS control-transfer
 * instructions. This does not emulate branch-likely or PS2 coprocessor state.
 */
internal object MipsControlFlow {
    data class Edge(val from: Long, val to: Long?, val kind: String)
    data class Result(val blockStarts: List<Long>, val edges: List<Edge>)

    fun analyze(words: List<Long>, baseAddress: Long): Result {
        if (words.isEmpty()) return Result(emptyList(), emptyList())
        val end = baseAddress + words.size * 4L
        val starts = sortedSetOf(baseAddress)
        val edges = mutableListOf<Edge>()

        words.forEachIndexed { index, word ->
            val pc = baseAddress + index * 4L
            val op = ((word ushr 26) and 0x3f).toInt()
            val rs = ((word ushr 21) and 31).toInt()
            val rt = ((word ushr 16) and 31).toInt()
            val fn = (word and 0x3f).toInt()
            val imm = (word and 0xffff).toShort().toInt()
            val fallThrough = pc + 8L // MIPS branch/jump delay slot

            fun addTarget(target: Long, kind: String) {
                edges.add(Edge(pc, target, kind))
                if (target in baseAddress until end && (target - baseAddress) % 4L == 0L) starts.add(target)
            }

            when {
                op in setOf(0x04, 0x05, 0x06, 0x07, 0x14, 0x15, 0x16, 0x17) -> {
                    addTarget(pc + 4L + (imm.toLong() shl 2), "conditional")
                    if (fallThrough < end) {
                        edges.add(Edge(pc, fallThrough, "fallthrough"))
                        starts.add(fallThrough)
                    }
                }
                op == 0x01 && rt in setOf(0, 1, 2, 3, 16, 17, 18, 19) -> {
                    addTarget(pc + 4L + (imm.toLong() shl 2), "regimm")
                    if (fallThrough < end) {
                        edges.add(Edge(pc, fallThrough, "fallthrough"))
                        starts.add(fallThrough)
                    }
                }
                op == 0x02 || op == 0x03 -> {
                    val index = word and 0x03ffffff
                    val destination = ((pc + 4L) and -0x10000000L) or (index shl 2)
                    addTarget(destination, if (op == 0x03) "call" else "jump")
                    if (op == 0x03 && fallThrough < end) {
                        edges.add(Edge(pc, fallThrough, "return-site"))
                        starts.add(fallThrough)
                    }
                }
                op == 0 && fn == 0x08 -> {
                    edges.add(Edge(pc, null, if (rs == 31) "return" else "indirect-jump"))
                }
            }
        }
        return Result(starts.toList(), edges)
    }
}
