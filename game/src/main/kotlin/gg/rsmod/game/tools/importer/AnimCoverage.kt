package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Does a sequence actually move a model? (breach audit 2026-09-27, cause C1-C3.)
 *
 * The rev-667 client animates a model by walking the frame's AnimBase groups and transforming the vertices whose
 * vertex label is in the group (`AnimFrame` / `Model_Sub2`). It says nothing when that finds no vertex: a missing
 * frameset, a base the frame does not parse against, or a model whose labels are not the labels of the base's groups
 * (an OSRS model with only skeletal "animaya" skinning converts with no classic labels) all leave the model standing
 * perfectly still. [AnimSkeletons.fits][gg.rsmod.plugins] only compares base ids and answers "true" when either side is
 * unknown, so none of those were ever caught. This check reads the real bytes of the cache and reports, per
 * (models, sequence) pair, whether each frame parses and whether the frame's animated groups reach any vertex.
 */
object AnimCoverage {
    const val INDEX_FRAMES = 0
    const val INDEX_BASES = 1
    const val INDEX_MODELS = 7
    const val INDEX_NPCS = 18
    const val INDEX_SEQ = 20
    const val INDEX_SPOTANIM = 21

    private const val TYPE_ORIGIN = 0
    private const val TYPE_ALPHA = 5

    class Base(val types: IntArray, val labels: List<IntArray>)

    class Labels(val vertex: Set<Int>, val face: Set<Int>, val missing: List<Int>)

    /** One sequence against one model set. [moves] false = the client draws it perfectly still. */
    class Result(
        val seq: Int,
        val frames: Int,
        val problems: List<String>,
        /** Animated (non-origin, non-alpha) groups over all frames, and how many of them reach a vertex of the model. */
        val animatedGroups: Int,
        val reachedGroups: Int,
    ) {
        val moves: Boolean get() = reachedGroups > 0
        val ok: Boolean get() = problems.isEmpty() && (animatedGroups == 0 || moves)

        override fun toString(): String =
            "seq=$seq frames=$frames groups=$reachedGroups/$animatedGroups" + (if (problems.isEmpty()) "" else " PROBLEMS=$problems")
    }

    fun decodeBase(bytes: ByteArray): Base {
        var p = 0
        fun u8() = bytes[p++].toInt() and 0xFF
        val count = u8()
        val types = IntArray(count) { u8() }
        p += count * 3
        val sizes = IntArray(count) { u8() }
        return Base(types, sizes.map { size -> IntArray(size) { u8() } })
    }

    fun modelLabels(
        library: CacheLibrary,
        models: List<Int>,
    ): Labels {
        val vertex = HashSet<Int>()
        val face = HashSet<Int>()
        val missing = ArrayList<Int>()
        for (id in models) {
            val data = library.data(INDEX_MODELS, id, 0)
            if (data == null) {
                missing += id
                continue
            }
            val model = runCatching { Rev667ModelDecoder.decode(data) }.getOrNull()
            if (model == null) {
                missing += id
                continue
            }
            model.vertexLabel?.forEach { vertex += it }
            model.faceLabel?.forEach { face += it }
        }
        return Labels(vertex, face, missing)
    }

    /** Sequence frame ids (opcode 1) of a rev-667 sequence, or null when the sequence does not exist. */
    fun seqFrames(
        library: CacheLibrary,
        seq: Int,
    ): IntArray? {
        val data = library.data(INDEX_SEQ, seq ushr 7, seq and 0x7F) ?: return null
        return runCatching { OsrsFxImportTool.decode667SeqFrames(data) }.getOrElse { IntArray(0) }
    }

    fun check(
        library: CacheLibrary,
        labels: Labels,
        seq: Int,
    ): Result {
        val problems = ArrayList<String>()
        val frames = seqFrames(library, seq)
        if (frames == null) return Result(seq, 0, listOf("sequence missing"), 0, 0)
        if (frames.isEmpty()) return Result(seq, 0, listOf("sequence has no frames or does not decode"), 0, 0)
        val bases = HashMap<Int, Base?>()
        var animated = 0
        var reached = 0
        for (frame in frames.distinct()) {
            val bytes = library.data(INDEX_FRAMES, frame ushr 16, frame and 0xFFFF)
            if (bytes == null || bytes.size < 4) {
                problems += "frame ${frame ushr 16}:${frame and 0xFFFF} missing"
                continue
            }
            val baseId = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
            val baseBytes = library.data(INDEX_BASES, baseId, 0)
            val base =
                bases.getOrPut(baseId) { baseBytes?.let { runCatching { decodeBase(it) }.getOrNull() } }
            if (base == null || baseBytes == null) {
                problems += "base $baseId of frame ${frame ushr 16}:${frame and 0xFFFF} missing"
                continue
            }
            val parse = runCatching { OsrsFxImportTool.check667Frame(bytes, baseBytes) }
            if (parse.isFailure) {
                problems += "frame ${frame ushr 16}:${frame and 0xFFFF} does not parse on base $baseId: ${parse.exceptionOrNull()?.message}"
                continue
            }
            val length = bytes[3].toInt() and 0xFF
            for (i in 0 until length) {
                val flags = bytes[4 + i].toInt() and 0xFF
                if (flags == 0 || i >= base.types.size) continue
                val type = base.types[i]
                if (type == TYPE_ORIGIN) continue
                animated++
                val space = if (type == TYPE_ALPHA) labels.face else labels.vertex
                if (base.labels[i].any { it in space }) reached++
            }
        }
        if (labels.missing.isNotEmpty()) problems += "models missing/undecodable ${labels.missing}"
        if (labels.vertex.isEmpty()) problems += "models carry no vertex labels"
        return Result(seq, frames.size, problems, animated, reached)
    }

    /** Models (opcode 1) of a rev-667 npc definition. */
    fun npcModels(
        library: CacheLibrary,
        npc: Int,
    ): List<Int> {
        val bytes = library.data(INDEX_NPCS, npc ushr 7, npc and 0x7F) ?: return emptyList()
        if (bytes.isEmpty() || (bytes[0].toInt() and 0xFF) != 1) return emptyList()
        val n = bytes[1].toInt() and 0xFF
        return (0 until n).map { ((bytes[2 + it * 2].toInt() and 0xFF) shl 8) or (bytes[3 + it * 2].toInt() and 0xFF) }
    }

    /** (model, sequence) of a rev-667 spotanim (opcodes 1 and 2, the first ones every encoder writes). */
    fun spotanim(
        library: CacheLibrary,
        id: Int,
    ): Pair<Int, Int>? {
        val bytes = library.data(INDEX_SPOTANIM, id ushr 8, id and 0xFF) ?: return null
        var p = 0
        var model = -1
        var seq = -1
        fun u8() = bytes[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        while (p < bytes.size) {
            when (u8()) {
                0 -> break
                1 -> model = u16()
                2 -> seq = u16()
                else -> break
            }
        }
        return model to seq
    }

    /** Stand / walk / run / crawl and the random idles of an npc's BAS. */
    fun basSeqs(
        definitions: gg.rsmod.game.fs.DefinitionSet,
        npc: Int,
    ): List<Int> {
        val basId = definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, npc)?.basId ?: return emptyList()
        val bas = definitions.getNullable(gg.rsmod.game.fs.def.BasDef::class.java, basId) ?: return emptyList()
        return listOf(bas.ready, bas.walk, bas.run, bas.crawl) + bas.readyAnimations.toList()
    }

    /**
     * Usage: `<cachePath> npc <npcId>[:seq,seq...] ... | spot <spotId> ...` (mixed). An npc's BAS sequences are the
     * caller's to pass; the tool checks exactly the sequences named.
     */
    /** Opcodes present in a rev-667 sequence (walk as `SeqType.decode`); null when it does not decode. */
    fun seqOpcodes(bytes: ByteArray): Set<Int>? {
        val c = OsrsFxImportTool.Cursor(bytes)
        val ops = HashSet<Int>()
        var count = 0
        return runCatching {
            while (true) {
                val op = c.u8()
                if (op == 0) break
                ops += op
                when (op) {
                    1 -> {
                        count = c.u16()
                        repeat(count * 3) { c.u16() }
                    }
                    2 -> c.u16()
                    3 -> repeat(c.u8()) { c.u8() }
                    5, 8, 9, 10, 11 -> c.u8()
                    6, 7 -> c.u16()
                    12 -> repeat(c.u8() * 2) { c.u16() }
                    13 -> repeat(c.u16()) {
                        val options = c.u8()
                        if (options > 0) {
                            c.u8(); c.u16()
                            repeat(options - 1) { c.u16() }
                        }
                    }
                    19 -> repeat(count) { c.u8() }
                    20 -> repeat(count) { c.u16(); c.u16() }
                    4, 14, 15, 16, 18 -> Unit
                    else -> error("op $op")
                }
            }
            ops
        }.getOrNull()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.getOrNull(1) == "seqops") {
            // <cache> seqops <from> <to>: how many sequences in the range carry each opcode.
            val library = CacheLibrary(args[0])
            val tally = sortedMapOf<Int, Int>()
            var total = 0
            var bad = 0
            for (seq in args[2].toInt()..args[3].toInt()) {
                val bytes = library.data(INDEX_SEQ, seq ushr 7, seq and 0x7F) ?: continue
                val ops = seqOpcodes(bytes)
                if (ops == null) { bad++; continue }
                total++
                ops.forEach { tally[it] = (tally[it] ?: 0) + 1 }
            }
            println("SEQOPS ${args[2]}..${args[3]} sequences=$total undecodable=$bad opcode->count=$tally")
            library.close()
            return
        }
        val library = CacheLibrary(args[0])
        val definitions = gg.rsmod.game.fs.DefinitionSet()
        definitions.load(library, gg.rsmod.game.fs.def.NpcDef::class.java)
        definitions.load(library, gg.rsmod.game.fs.def.BasDef::class.java)
        var mode = "npc"
        var bad = 0
        for (arg in args.drop(1)) {
            if (arg == "npc" || arg == "spot") {
                mode = arg
                continue
            }
            if (mode == "spot") {
                val id = arg.toInt()
                val spot = spotanim(library, id)
                if (spot == null) {
                    println("SPOT $id MISSING"); bad++; continue
                }
                val labels = modelLabels(library, listOf(spot.first))
                val result = if (spot.second >= 0) check(library, labels, spot.second) else null
                if (result != null && !result.ok) bad++
                println("SPOT $id model=${spot.first} vertexLabels=${labels.vertex.size} max=${labels.vertex.maxOrNull()} $result")
            } else {
                val npc = arg.substringBefore(':').toInt()
                val extra = arg.substringAfter(':', "").split(',').filter { it.isNotBlank() }.map { it.toInt() }
                val seqs = (basSeqs(definitions, npc) + extra).filter { it >= 0 }.distinct()
                val models = npcModels(library, npc)
                val labels = modelLabels(library, models)
                println("NPC $npc models=$models vertexLabels=${labels.vertex.size} max=${labels.vertex.maxOrNull()} missing=${labels.missing}")
                seqs.forEach { s ->
                    val result = check(library, labels, s)
                    if (!result.ok) bad++
                    println("  ${if (result.ok) "OK " else "BAD"} $result")
                }
            }
        }
        println("AnimCoverage: $bad problem(s)")
        library.close()
    }
}
