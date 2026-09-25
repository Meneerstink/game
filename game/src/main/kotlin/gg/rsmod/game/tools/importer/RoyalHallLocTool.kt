package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Raised copies of two roof locs for the Grand Exchange Royal Hall's tall hip roof (owner 2026-09-25: "het gebouw ziet er
 * niet echt uit als een paleis", "please dont stop untill its perfet").
 *
 * The Legends' Guild slate roof piece 41409 rises 70 (old model units) across one tile, and the client stacks roof rings
 * on higher levels, but a map square has only four. The hall's roof needs five slate rings, so rings 3-5 are copies of
 * 41409 whose vertical offset (loc opcode 71) lifts them onto the ring below: ring 2 is 41409 itself on level 3, ring k
 * sits 70 * (k - 2) higher. The small glass lantern on top is a copy of the glass roof piece 47841 lifted onto ring 5.
 * Nothing else about the definitions changes (models, shapes, recolours, no options), so they look and behave like the
 * originals. The hall's fountain is a byte-exact copy of the Grand Exchange fountain 47150 (owner 2026-09-25: the hall
 * moved onto the exchange's centre, where the owner's own world edit removes 47150 from that tile). Appended as new loc
 * ids after the last one in the cache; one [CacheTransaction] over both production caches, idempotent.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RoyalHallLocTool plan|apply`
 */
object RoyalHallLocTool {
    private const val GAME_CACHE = "C:/RSPS/game/game/data/cache"
    private const val FILE_SERVER_CACHE = "C:/RSPS/file-server/cache"
    private const val LOC_INDEX = 16

    const val SLATE = 41409
    const val GLASS = 47841
    const val FOUNTAIN = 47150

    /** The slate piece's own opcode-71 value; the rings above add their lift to it. */
    private const val SLATE_BASE_OFFSET = -6
    private const val SLATE_RISE = 70

    /** Glass slope faces start 16 above the loc; the lantern's base meets the top of ring 5 (216 + 70 above level 3). */
    private const val GLASS_OFFSET = -(3 * SLATE_RISE + SLATE_RISE - SLATE_BASE_OFFSET - 16)

    /** A copy of [source]; [offset] replaces its vertical offset (opcode 71), null keeps the definition byte-exact. */
    class Copy(val id: Int, val source: Int, val offset: Int?, val label: String)

    val COPIES =
        listOf(
            Copy(62754, SLATE, SLATE_BASE_OFFSET - SLATE_RISE, "Royal Hall roof ring 3 (slate 41409 raised 70)"),
            Copy(62755, SLATE, SLATE_BASE_OFFSET - 2 * SLATE_RISE, "Royal Hall roof ring 4 (slate 41409 raised 140)"),
            Copy(62756, SLATE, SLATE_BASE_OFFSET - 3 * SLATE_RISE, "Royal Hall roof ring 5 (slate 41409 raised 210)"),
            Copy(62757, GLASS, GLASS_OFFSET, "Royal Hall glass lantern (glass roof 47841 raised onto ring 5)"),
            Copy(62758, FOUNTAIN, null, "Royal Hall fountain (exact copy of the Grand Exchange fountain 47150)"),
        )

    /**
     * The Grand Exchange's visible paving in the centre is four 10 x 10 models (the plane-1 floor there is an invisible
     * walk surface, overlay 124). The hall's floor is cut out of copies of them: [newModel] is [model] with every face
     * whose centre lies on the hall's floor collapsed to a point, and loc [id] is [source] showing [newModel].
     * RoyalHallMapTool puts the copies in place of the originals at ([x], [z]).
     */
    class Cut(val id: Int, val source: Int, val model: Int, val newModel: Int, val x: Int, val z: Int)

    val CUTS =
        listOf(
            Cut(62759, 47606, 19802, 65428, 3155, 3492),
            Cut(62760, 47607, 19805, 65429, 3165, 3492),
            Cut(62761, 47909, 19817, 65430, 3155, 3482),
            Cut(62762, 47911, 19860, 65431, 3165, 3482),
        )
    private const val CUT_SIZE = 10
    private const val MODEL_INDEX = 7

    /**
     * [data] (a rev-667 mesh with the version byte, footer -1 -1) with the faces over the hall floor collapsed; returns
     * the new mesh and the number of faces cut. Version 13+ meshes use 512 units per tile (the client scales them by
     * 1/4), centred on the loc's footprint; +z is north.
     */
    fun cutModel(data: ByteArray, locX: Int, locZ: Int): Pair<ByteArray, Int> {
        val version = data[data.size - 24].toInt() and 0xFF
        check(data[data.size - 23 + 5].toInt() and 0x8 != 0 && version >= 13) { "expected a version-13+ mesh" }
        val model = Rev667ModelDecoder.decode(data)
        val unitsPerTile = 512.0
        var cut = 0
        for (f in 0 until model.faceCount) {
            val a = model.faceA[f]
            val b = model.faceB[f]
            val c = model.faceC[f]
            val cx = locX + CUT_SIZE / 2.0 + (model.vertexX[a] + model.vertexX[b] + model.vertexX[c]) / 3.0 / unitsPerTile
            val cz = locZ + CUT_SIZE / 2.0 + (model.vertexZ[a] + model.vertexZ[b] + model.vertexZ[c]) / 3.0 / unitsPerTile
            val onFloor =
                cx >= RoyalHallMapTool.FLOOR_MIN_X && cx < RoyalHallMapTool.FLOOR_MAX_X + 1 &&
                    cz >= RoyalHallMapTool.FLOOR_MIN_Z && cz < RoyalHallMapTool.FLOOR_MAX_Z + 1
            if (onFloor) {
                model.faceB[f] = a
                model.faceC[f] = a
                cut++
            }
        }
        // The encoder writes a version-12 trailer; put the version byte back in front of it and flag it (bit 3).
        val encoded = Rev667ModelEncoder.encode(model)
        val body = encoded.copyOf(encoded.size - 23)
        val trailer = encoded.copyOfRange(encoded.size - 23, encoded.size)
        trailer[5] = (trailer[5].toInt() or 0x8).toByte()
        return (body + byteArrayOf(version.toByte()) + trailer) to cut
    }

    /** [def] with every opcode-1 model id [from] replaced by [to]. */
    fun withModel(id: Int, def: ByteArray, from: Int, to: Int): ByteArray {
        val at = opcodePosition(id, def, 1)
        check(at >= 0) { "loc $id has no opcode 1" }
        val out = def.copyOf()
        var p = at + 1
        var replaced = 0
        repeat(out[p++].toInt() and 0xFF) {
            p++ // shape
            repeat(out[p++].toInt() and 0xFF) {
                val model = ((out[p].toInt() and 0xFF) shl 8) or (out[p + 1].toInt() and 0xFF)
                if (model == from) {
                    out[p] = (to shr 8).toByte()
                    out[p + 1] = to.toByte()
                    replaced++
                }
                p += 2
            }
        }
        check(replaced > 0) { "loc $id does not use model $from" }
        return out
    }

    /** Returns [def] with its opcode-71 (vertical offset) set to [offset], inserted before the terminator when absent. */
    fun withOffset(id: Int, def: ByteArray, offset: Int): ByteArray {
        val at = opcodePosition(id, def, 71)
        val value = byteArrayOf((offset shr 8).toByte(), offset.toByte())
        return if (at >= 0) {
            def.copyOf().also {
                it[at + 1] = value[0]
                it[at + 2] = value[1]
            }
        } else {
            check(def.last() == 0.toByte()) { "loc $id does not end with the opcode-0 terminator" }
            def.copyOf(def.size - 1) + byteArrayOf(71) + value + byteArrayOf(0)
        }
    }

    /**
     * Position of [wanted] in the opcode stream of a loc definition, or -1. Walks the stream with [Rev667LocType.decode]'s
     * operand sizes by decoding growing prefixes: the first prefix that ends right before an opcode byte equal to [wanted]
     * and decodes cleanly with that opcode's two operand bytes is the match.
     */
    private fun opcodePosition(id: Int, def: ByteArray, wanted: Int): Int {
        var pos = 0
        while (pos < def.size) {
            val op = def[pos].toInt() and 0xFF
            if (op == 0) return -1
            if (op == wanted) return pos
            pos += 1 + operandSize(id, def, pos)
        }
        return -1
    }

    private fun operandSize(id: Int, def: ByteArray, pos: Int): Int {
        val op = def[pos].toInt() and 0xFF
        fun u8(i: Int) = def[pos + 1 + i].toInt() and 0xFF
        return when (op) {
            1, 5 -> {
                var p = 0
                repeat(if (op == 5) 2 else 1) {
                    val shapes = u8(p)
                    p++
                    repeat(shapes) {
                        p++ // shape
                        val models = u8(p)
                        p += 1 + models * 2
                    }
                }
                p
            }
            2, in 30..34, in 150..154 -> {
                var p = 0
                while (def[pos + 1 + p] != 0.toByte()) p++
                p + 1
            }
            14, 15, 19, 28, 29, 39, 69, 75, 81, 101, 104, 162, 178 -> 1
            17, 18, 21, 22, 23, 27, 62, 64, 73, 74, 82, 88, 89, 90, 91, 94, 96, 97, 98, 103, 105, 168, 169, 177, 189 -> 0
            24, 65, 66, 67, 70, 71, 72, 93, 95, 102, 107, 164, 165, 166, 167 -> 2
            40, 41 -> 1 + u8(0) * 4
            42 -> 1 + u8(0)
            78 -> 3
            99, 100 -> 3
            163 -> 4
            173 -> 4
            else -> error("loc $id: opcode $op at $pos is not handled by RoyalHallLocTool")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(GAME_CACHE)
        try {
            COPIES.forEach { copy ->
                val source = library.data(LOC_INDEX, copy.source ushr 8, copy.source and 0xFF) ?: error("loc ${copy.source} missing")
                val wanted = copy.offset?.let { withOffset(copy.source, source, it) } ?: source.copyOf()
                val before = Rev667LocType.decode(copy.source, source)
                val after = Rev667LocType.decode(copy.id, wanted)
                check(after.allModels == before.allModels && after.modelsByShape.keys == before.modelsByShape.keys) { "copy ${copy.id} changed its models" }
                val current = library.data(LOC_INDEX, copy.id ushr 8, copy.id and 0xFF)
                when {
                    current == null -> println("CREATE loc ${copy.id} from ${copy.source} offset ${copy.offset}: ${copy.label}")
                    current.contentEquals(wanted) -> return@forEach println("LOC ${copy.id} already in place")
                    else -> error("loc ${copy.id} already exists with other content; refusing to overwrite it")
                }
                mutations += CacheMutation(LOC_INDEX, copy.id ushr 8, copy.id and 0xFF, wanted, copy.label, null)
            }
            CUTS.forEach { cut ->
                val source = library.data(MODEL_INDEX, cut.model, 0) ?: error("model ${cut.model} missing")
                val (mesh, faces) = cutModel(source, cut.x, cut.z)
                val back = Rev667ModelDecoder.decode(mesh)
                val orig = Rev667ModelDecoder.decode(source)
                check(back.vertexCount == orig.vertexCount && back.faceCount == orig.faceCount) { "model ${cut.newModel} changed its size" }
                val currentMesh = library.data(MODEL_INDEX, cut.newModel, 0)
                println("MODEL ${cut.newModel} = ${cut.model} with $faces of ${orig.faceCount} faces cut (${if (currentMesh == null) "create" else if (currentMesh.contentEquals(mesh)) "in place" else "REPLACE"})")
                if (currentMesh == null || !currentMesh.contentEquals(mesh)) {
                    mutations += CacheMutation(MODEL_INDEX, cut.newModel, 0, mesh, "Royal Hall floor cut from GE paving model ${cut.model}", currentMesh?.let { CacheItemProbeTool.sha1(it) })
                }
                val def = library.data(LOC_INDEX, cut.source ushr 8, cut.source and 0xFF) ?: error("loc ${cut.source} missing")
                val wanted = withModel(cut.source, def, cut.model, cut.newModel)
                val current = library.data(LOC_INDEX, cut.id ushr 8, cut.id and 0xFF)
                when {
                    current == null -> println("CREATE loc ${cut.id} = ${cut.source} showing model ${cut.newModel}")
                    current.contentEquals(wanted) -> return@forEach println("LOC ${cut.id} already in place")
                    else -> error("loc ${cut.id} already exists with other content; refusing to overwrite it")
                }
                mutations += CacheMutation(LOC_INDEX, cut.id ushr 8, cut.id and 0xFF, wanted, "Royal Hall paving ${cut.id} (${cut.source} without the hall floor)", null)
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
