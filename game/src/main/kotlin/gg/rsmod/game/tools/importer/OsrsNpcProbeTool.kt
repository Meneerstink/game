package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Read-only probe for the OSRS NPC import (RCV-012 decision 3, Ferox npcs).
 *
 * `osrs <npcId>...` decodes OSRS npc types from the pinned source cache with the RuneLite `NpcLoader` opcode table (rev 233) and reports
 * every field the 667 `NPCType` / `BASType` could carry, plus whether each movement sequence is a classic frame sequence (importable
 * through [OsrsFxImportTool]) or skeletal. `bas667 <basId>...` dumps the raw opcodes of 667 BASType entries (config group 32) so the
 * OSRS turn fields can be mapped onto 667 turn opcodes from a proven pair instead of by name. `npc667 <npcId>...` prints a 667 npc's
 * basId.
 */
object OsrsNpcProbeTool {
    const val CONFIG_GROUP_NPC = 9

    class OsrsNpc(val id: Int) {
        var name = "null"
        var models = IntArray(0)
        var chatheads = IntArray(0)
        var size = 1
        var stand = -1
        var walk = -1
        var idleTurnLeft = -1
        var idleTurnRight = -1
        var walk180 = -1
        var walkLeft = -1
        var walkRight = -1
        var run = -1
        var run180 = -1
        var runLeft = -1
        var runRight = -1
        var crawl = -1
        var crawl180 = -1
        var crawlLeft = -1
        var crawlRight = -1
        val ops = arrayOfNulls<String>(5)
        var recolorFind = IntArray(0)
        var recolorReplace = IntArray(0)
        var retextures = 0
        var minimap = true
        var combat = -1
        var widthScale = 128
        var heightScale = 128
        var renderPriority = 0
        var ambient = 0
        var contrast = 0
        var rotationSpeed = 32
        var interactable = true
        var rotationFlag = true
        var follower = false
        var height = -1
        var footprint = -1
        var multiNpc = false
        val dropped = mutableListOf<String>()
    }

    /** RuneLite `NpcLoader.decodeValues` (rev210 head icons, rev233), field for field. */
    fun decodeOsrs(
        id: Int,
        bytes: ByteArray,
    ): OsrsNpc {
        val c = OsrsFxImportTool.Cursor(bytes)
        val n = OsrsNpc(id)
        while (true) {
            when (val op = c.u8()) {
                0 -> return n
                1 -> n.models = IntArray(c.u8()) { c.u16() }
                2 -> n.name = c.string()
                12 -> n.size = c.u8()
                13 -> n.stand = c.u16()
                14 -> n.walk = c.u16()
                15 -> n.idleTurnLeft = c.u16()
                16 -> n.idleTurnRight = c.u16()
                17 -> {
                    n.walk = c.u16()
                    n.walk180 = c.u16()
                    n.walkLeft = c.u16()
                    n.walkRight = c.u16()
                }
                18 -> n.dropped += "category ${c.u16()}"
                in 30..34 -> n.ops[op - 30] = c.string()
                40 -> {
                    val k = c.u8()
                    n.recolorFind = IntArray(k)
                    n.recolorReplace = IntArray(k)
                    for (i in 0 until k) {
                        n.recolorFind[i] = c.u16()
                        n.recolorReplace[i] = c.u16()
                    }
                }
                41 -> {
                    val k = c.u8()
                    repeat(k) { c.u16(); c.u16() }
                    n.retextures = k
                }
                60 -> n.chatheads = IntArray(c.u8()) { c.u16() }
                61 -> n.models = IntArray(c.u8()) { c.i32() }
                62 -> n.chatheads = IntArray(c.u8()) { c.i32() }
                in 74..79 -> n.dropped += "stat ${op - 74} = ${c.u16()}"
                93 -> n.minimap = false
                95 -> n.combat = c.u16()
                97 -> n.widthScale = c.u16()
                98 -> n.heightScale = c.u16()
                99 -> n.renderPriority = 1
                100 -> n.ambient = c.s8()
                101 -> n.contrast = c.s8()
                102 -> {
                    val bitfield = c.u8()
                    var bits = bitfield
                    var i = 0
                    while (bits != 0) {
                        if (bitfield and (1 shl i) != 0) {
                            bigSmart2(c)
                            shortSmartMinusOne(c)
                        }
                        bits = bits shr 1
                        i++
                    }
                    n.dropped += "head icons (OSRS sprite archives)"
                }
                103 -> n.rotationSpeed = c.u16()
                106, 118 -> {
                    c.u16()
                    c.u16()
                    if (op == 118) c.u16()
                    val k = c.u8()
                    repeat(k + 1) { c.u16() }
                    n.multiNpc = true
                }
                107 -> n.interactable = false
                109 -> n.rotationFlag = false
                111 -> n.renderPriority = 2
                114 -> n.run = c.u16()
                115 -> {
                    n.run = c.u16()
                    n.run180 = c.u16()
                    n.runLeft = c.u16()
                    n.runRight = c.u16()
                }
                116 -> n.crawl = c.u16()
                117 -> {
                    n.crawl = c.u16()
                    n.crawl180 = c.u16()
                    n.crawlLeft = c.u16()
                    n.crawlRight = c.u16()
                }
                122 -> n.follower = true
                123 -> n.dropped += "low priority follower ops"
                124 -> n.height = c.u16()
                126 -> n.footprint = c.u16()
                129 -> n.dropped += "unknown1"
                130 -> n.dropped += "idle anim restart"
                145 -> n.dropped += "can hide for overlap"
                146 -> n.dropped += "overlap tint ${c.u16()}"
                147 -> n.dropped += "zbuf false"
                249 -> {
                    val k = c.u8()
                    repeat(k) {
                        val isString = c.u8() == 1
                        c.pos += 3
                        if (isString) c.string() else c.i32()
                    }
                    n.dropped += "params ($k)"
                }
                251 -> {
                    c.u8(); c.u8(); c.string()
                    n.dropped += "sub op"
                }
                252 -> {
                    c.u8(); c.u16(); c.u16(); c.i32(); c.i32(); c.string()
                    n.dropped += "conditional op"
                }
                253 -> {
                    c.u8(); c.u16(); c.u16(); c.u16(); c.i32(); c.i32(); c.string()
                    n.dropped += "conditional sub op"
                }
                else -> error("npc $id: unknown OSRS npc opcode $op")
            }
        }
    }

    private fun bigSmart2(c: OsrsFxImportTool.Cursor): Int =
        if (c.data[c.pos].toInt() < 0) c.i32() and 0x7FFFFFFF else c.u16().let { if (it == 32767) -1 else it }

    private fun shortSmartMinusOne(c: OsrsFxImportTool.Cursor): Int =
        if ((c.data[c.pos].toInt() and 0xFF) < 128) c.u8() - 1 else c.u16() - 0x8001

    fun movementSeqs(n: OsrsNpc): Map<String, Int> =
        linkedMapOf(
            "stand" to n.stand, "walk" to n.walk, "idleTurnLeft" to n.idleTurnLeft, "idleTurnRight" to n.idleTurnRight,
            "walk180" to n.walk180, "walkLeft" to n.walkLeft, "walkRight" to n.walkRight,
            "run" to n.run, "run180" to n.run180, "runLeft" to n.runLeft, "runRight" to n.runRight,
            "crawl" to n.crawl, "crawl180" to n.crawl180, "crawlLeft" to n.crawlLeft, "crawlRight" to n.crawlRight,
        ).filterValues { it >= 0 }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: osrs <npcId>... | bas667 <basId>... | npc667 <npcId>..." }
        val ids = args.drop(1).map { it.toInt() }
        when (args[0]) {
            "osrs" ->
                ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                    val npcs = reader.files(ModernCacheReader.INDEX_CONFIG, CONFIG_GROUP_NPC)
                    ids.forEach { id ->
                        val bytes = npcs[id] ?: return@forEach println("OSRS_NPC_$id ABSENT")
                        val n = decodeOsrs(id, bytes)
                        println(
                            "OSRS_NPC_$id name='${n.name}' size=${n.size} combat=${n.combat} models=${n.models.toList()} chatheads=${n.chatheads.toList()} " +
                                "ops=${n.ops.toList()} recolor=${n.recolorFind.zip(n.recolorReplace)} retextures=${n.retextures} minimap=${n.minimap} " +
                                "scale=${n.widthScale}x${n.heightScale} ambient=${n.ambient} contrast=${n.contrast} rotSpeed=${n.rotationSpeed} " +
                                "interactable=${n.interactable} rotationFlag=${n.rotationFlag} renderPriority=${n.renderPriority} follower=${n.follower} " +
                                "height=${n.height} footprint=${n.footprint} multiNpc=${n.multiNpc} maxModel=${(n.models + n.chatheads).maxOrNull()}",
                        )
                        println("  MOVEMENT ${movementSeqs(n)}")
                        movementSeqs(n).values.distinct().forEach { seqId ->
                            val seqBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, seqId)
                            val kind =
                                if (seqBytes == null) {
                                    "ABSENT"
                                } else {
                                    runCatching { OsrsFxImportTool.decodeOsrsSeq(seqBytes) }.fold(
                                        { "frames=${it.frames.size} framesets=${it.frames.map { f -> f ushr 16 }.distinct()}" },
                                        { "REFUSED ${it.message}" },
                                    )
                                }
                            println("  SEQ $seqId $kind")
                        }
                        if (n.dropped.isNotEmpty()) println("  DROPPED ${n.dropped}")
                    }
                }
            "bas667" -> {
                val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
                try {
                    ids.forEach { id ->
                        val bytes = library.data(2, 32, id) ?: return@forEach println("BAS667_$id ABSENT")
                        println("BAS667_$id ${raw667Bas(bytes)}")
                    }
                } finally {
                    library.close()
                }
            }
            "loc2499" ->
                ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { reader ->
                    val locs = reader.files(ModernCacheReader.INDEX_CONFIG, 6)
                    ids.forEach { id ->
                        val bytes = locs[id] ?: return@forEach println("LOC2499_$id ABSENT")
                        val d = ModernObjectDef.decode(id, bytes)
                        println(
                            "LOC2499_$id name='${d.name}' options=${d.options.toList()} models=${d.models} types=${d.modelTypes} size=${d.sizeX}x${d.sizeY} " +
                                "interact=${d.interactType} anim=${d.animationId} varbit=${d.varbitId} varp=${d.varpId} transforms=${d.transforms?.toList()}",
                        )
                    }
                }
            // Every OSRS sequence that animates the same skeleton (frame base) as the npc's stand sequence: the only sequences its model
            // can play. Names come from RuneLite gameval AnimationID, matched outside this tool.
            "skeleton" ->
                ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                    val npcs = reader.files(ModernCacheReader.INDEX_CONFIG, CONFIG_GROUP_NPC)
                    val seqs = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE)
                    val baseOfFrameset = HashMap<Int, Int>()

                    fun base(frameset: Int): Int =
                        baseOfFrameset.getOrPut(frameset) {
                            val frame = reader.files(0, frameset).values.firstOrNull() ?: return@getOrPut -1
                            ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)
                        }

                    val seqBase = HashMap<Int, Int>()
                    seqs.forEach { (seqId, bytes) ->
                        val seq = runCatching { OsrsFxImportTool.decodeOsrsSeq(bytes) }.getOrNull() ?: return@forEach
                        val fs = seq.frames.firstOrNull()?.ushr(16) ?: return@forEach
                        seqBase[seqId] = base(fs)
                    }
                    ids.forEach { id ->
                        val n = decodeOsrs(id, npcs[id] ?: return@forEach println("SKELETON_$id ABSENT"))
                        val stand = movementSeqs(n)["stand"]
                        val b = stand?.let { seqBase[it] }
                        val matches = if (b == null) emptyList() else seqBase.filterValues { it == b }.keys.sorted()
                        println("SKELETON_$id name='${n.name}' stand=$stand base=$b seqs=$matches")
                    }
                }
            "npc667" -> {
                val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
                try {
                    ids.forEach { id ->
                        val def = BasTypeProbeTool.npcDef(library, id)
                        println("NPC667_$id name='${def?.name}' basId=${def?.basId}")
                    }
                } finally {
                    library.close()
                }
            }
            else -> error("unknown mode ${args[0]}")
        }
    }

    /** 667 `BASType.decode` movement opcodes (1-9, 38-42, 46-51) with their values; stops at the first other opcode. */
    fun raw667Bas(bytes: ByteArray): String {
        val c = OsrsFxImportTool.Cursor(bytes)
        val out = mutableListOf<String>()
        while (c.pos < bytes.size) {
            when (val op = c.u8()) {
                0 -> return out.joinToString(" ")
                1 -> out += "1:ready=${c.u16()},walk=${c.u16()}"
                in 2..9, in 38..42, in 46..51 -> out += "$op=${c.u16()}"
                else -> {
                    out += "op$op(stop)"
                    return out.joinToString(" ")
                }
            }
        }
        return out.joinToString(" ")
    }
}
