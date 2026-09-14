package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * OSRS spotanim (graphic / projectile) import into both rev-667 caches (owner decision (e) 2026-09-14).
 *
 * One OSRS spotanim brings its model (index 7, id <= 32767 because the spotanim model field is a signed short), its sequence
 * (index 20), every frameset group and base the sequence uses (indexes 0 and 1) and every synth sound its frames play (index 4).
 * The byte conversions are proven against spotanims that exist in both caches (`OsrsFxProbeTool`, OSRS_IMPORT_STATUS.md):
 * frames get a leading `0x01` and per-base-type value scaling (origin/translate x4, rotation x16), bases get the 667 boolean and
 * part-mask arrays, spotanim opcode 3 (int model) becomes opcode 1.
 *
 * Sequence/spotanim ids are appended after the current maximum (the 667 client sizes both lists from the last group); frameset,
 * base and synth groups take the next ids after their index maximum. Sequences that use OSRS skeletal (animaya) data cannot be
 * represented and are refused per spotanim, never guessed.
 *
 * Usage: `<batch> [--apply]` - without `--apply` only plan + preflight run.
 */
object OsrsFxImportTool {
    val TARGETS = OsrsItemImportTool.TARGETS
    const val INDEX_FRAMES = 0
    const val INDEX_BASES = 1
    const val INDEX_SYNTH = 4
    const val INDEX_SEQ = 20
    const val INDEX_SPOTANIM = 21

    /**
     * Spotanims named after imported items in RuneLite `gameval/SpotanimID.java` (Jagex names from the cache).
     */
    val BATCHES: Map<String, List<Int>> =
        mapOf(
            "fxpilot" to
                listOf(
                    1228, 1229, // SOTD_SPECIAL_START / EXTRA (Power of Death)
                    1539, 1540, 1541, 1542, // SANGUINESTI_STAFF_TRAVEL / CASTING / IMPACT / HEAL
                    665, 1040, 1042, // TOXIC_TOTS_CASTING / PROJECTILE / IMPACT (Trident of the swamp)
                    1250, 1251, 1252, 1253, // SLAYER_TOTS_CHARGE / CASTING / PROJECTILE / IMPACT (Trident of the seas)
                    1043, // TOXIC_BLOWPIPE_SPECIALATTACK
                    301, 1995, 1181, 1468, // ACB_SPECIALATTACK, ZCB_SPECIALATTACK, ACB_CROSSBOWBOLT_TRAVEL, DRAGON_CROSSBOWBOLT_TRAVEL
                    344, 1301, 1386, // BALLISTA_SPECIAL, DRAGON_JAVELIN_TRAVEL, AMETHYST_JAVELIN_TRAVEL
                    1283, 1292, // ABYSSAL_DAGGER_SPECIAL_SPOTANIM, DRAGON_WARHAMMER_SA_SPOTANIM
                    2363, 2834, 2930, // FX_VOIDWAKER_IMPACT, FX_VOIDWAKER02_SPECIAL, VFX_NOXIOUS_HALBERD_SPEC
                    1887, 1888, 1936, 1937, // SP_ATTACK_ARROW_TRAVEL/LAUNCH_FAERDHINEN, AMETHYST_DART_TRAVEL/LAUNCH
                ),
        )

    // ---- smart values ---------------------------------------------------------------------------

    class Cursor(val data: ByteArray, var pos: Int = 0) {
        fun u8(): Int = data[pos++].toInt() and 0xFF

        fun s8(): Int = data[pos++].toInt()

        fun u16(): Int = (u8() shl 8) or u8()

        fun i32(): Int = (u16() shl 16) or u16()

        fun smarts(): Int {
            val first = data[pos].toInt() and 0xFF
            return if (first < 128) u8() - 64 else u16() - 49152
        }

        fun string(): String {
            val start = pos
            while (data[pos].toInt() != 0) pos++
            return String(data, start, pos++ - start, Charsets.ISO_8859_1)
        }

        val remaining: Int get() = data.size - pos
    }

    private fun ByteArrayOutputStream.u8(v: Int) = write(v and 0xFF)

    private fun ByteArrayOutputStream.u16(v: Int) {
        write(v ushr 8 and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.smarts(v: Int) {
        when (v) {
            in -64..63 -> u8(v + 64)
            in -16384..16383 -> u16(v + 49152)
            else -> error("value $v does not fit a signed smart")
        }
    }

    // ---- bases and frames ----------------------------------------------------------------------

    /** Base types of an OSRS framemap (count, types...). */
    fun baseTypes(osrsBase: ByteArray): IntArray {
        val c = Cursor(osrsBase)
        val count = c.u8()
        return IntArray(count) { c.u8() }
    }

    /** OSRS framemap -> rev-667 AnimBase: count, types, `count` booleans 0, `count` part masks 0xFFFF, sizes, maps. */
    fun convertBase(osrsBase: ByteArray): ByteArray {
        val c = Cursor(osrsBase)
        val count = c.u8()
        val types = IntArray(count) { c.u8() }
        types.forEach { check(it in SCALE_BY_TYPE) { "unsupported base type $it" } }
        val out = ByteArrayOutputStream()
        out.u8(count)
        types.forEach { out.u8(it) }
        repeat(count) { out.u8(0) }
        repeat(count) { out.u16(0xFFFF) }
        // Sizes and maps exactly as RuneLite FramemapLoader reads them; the newer OSRS framemap carries trailing bytes after the
        // maps that neither RuneLite's loader nor the 667 AnimBase reads (proven by OsrsFxConversionTests against base 871).
        val sizes = IntArray(count) { c.u8() }
        sizes.forEach { out.u8(it) }
        sizes.forEach { size -> repeat(size) { out.u8(c.u8()) } }
        return out.toByteArray()
    }

    /** Proven per-type value scale (origin/translate x4, rotation x16, scale and alpha unchanged). */
    val SCALE_BY_TYPE = mapOf(0 to 4, 1 to 4, 2 to 16, 3 to 1, 5 to 1)

    /** OSRS frame -> rev-667 frame for [localBaseId]; [types] are the OSRS base's types. */
    fun convertFrame(
        osrsFrame: ByteArray,
        types: IntArray,
        localBaseId: Int,
    ): ByteArray {
        val c = Cursor(osrsFrame)
        c.u16()
        val length = c.u8()
        val flags = IntArray(length) { c.u8() }
        val out = ByteArrayOutputStream()
        out.u8(1)
        out.u16(localBaseId)
        out.u8(length)
        flags.forEach { out.u8(it) }
        for (i in 0 until length) {
            if (flags[i] == 0) continue
            val scale = SCALE_BY_TYPE[types.getOrElse(i) { -1 }] ?: error("frame group $i has unsupported base type ${types.getOrNull(i)}")
            for (bit in 0..2) {
                if (flags[i] and (1 shl bit) != 0) out.smarts(c.smarts() * scale)
            }
        }
        check(c.remaining == 0) { "frame has ${c.remaining} trailing bytes" }
        return out.toByteArray()
    }

    // ---- sequences -----------------------------------------------------------------------------

    class Seq(
        var frameDurations: IntArray = IntArray(0),
        var frames: IntArray = IntArray(0),
        var loopOffset: Int? = null,
        var blend: IntArray? = null,
        var priority: Int? = null,
        var maxLoops: Int? = null,
        var animatingPrecedence: Int? = null,
        var walkingPrecedence: Int? = null,
        var replayMode: Int? = null,
        var secondaryFrames: IntArray? = null,
        val sounds: MutableMap<Int, Pair<Int, Int>> = sortedMapOf(),
        val dropped: MutableList<String> = mutableListOf(),
    )

    /** OSRS rev-226+ sequence (RuneLite `SequenceLoader` with rev226 = true). */
    fun decodeOsrsSeq(bytes: ByteArray): Seq {
        val c = Cursor(bytes)
        val seq = Seq()
        while (true) {
            when (val op = c.u8()) {
                0 -> return seq
                1 -> {
                    val n = c.u16()
                    seq.frameDurations = IntArray(n) { c.u16() }
                    val lo = IntArray(n) { c.u16() }
                    seq.frames = IntArray(n) { lo[it] + (c.u16() shl 16) }
                }
                2 -> seq.loopOffset = c.u16()
                3 -> {
                    val n = c.u8()
                    seq.blend = IntArray(n) { c.u8() }
                }
                4 -> seq.dropped += "stretches flag (no 667 equivalent)"
                5 -> seq.priority = c.u8()
                6, 7 -> seq.dropped += "hand item ${c.u16()} (OSRS item id)"
                8 -> seq.maxLoops = c.u8()
                9 -> seq.animatingPrecedence = c.u8()
                10 -> seq.walkingPrecedence = c.u8()
                11 -> seq.replayMode = c.u8()
                12 -> {
                    val n = c.u8()
                    val lo = IntArray(n) { c.u16() }
                    seq.secondaryFrames = IntArray(n) { lo[it] + (c.u16() shl 16) }
                }
                13 -> error("skeletal (animaya) sequence ${c.i32()} cannot be represented in 667")
                14 -> {
                    val n = c.u16()
                    repeat(n) {
                        val frame = c.u16()
                        val id = c.u16()
                        c.u8() // weight
                        val loops = c.u8()
                        c.u8() // location
                        c.u8() // retain
                        if (id >= 1 && loops >= 1) seq.sounds[frame] = id to loops
                    }
                    seq.dropped += "frame sound location/retain/weight (no 667 fields)"
                }
                15 -> error("skeletal (animaya) frame range ${c.u16()}-${c.u16()} cannot be represented in 667")
                16 -> seq.dropped += "vertical offset ${c.s8()}"
                17 -> {
                    val n = c.u8()
                    repeat(n) { c.u8() }
                    error("skeletal (animaya) masks cannot be represented in 667")
                }
                18 -> seq.dropped += "debug name ${c.string()}"
                19 -> seq.dropped += "sounds cross world view flag"
                else -> error("unknown OSRS sequence opcode $op")
            }
        }
    }

    /** Rev-667 `SeqType.decode` stream; frame ids and sound ids already local. */
    fun encode667Seq(seq: Seq): ByteArray {
        val out = ByteArrayOutputStream()
        val n = seq.frames.size
        out.u8(1)
        out.u16(n)
        seq.frameDurations.forEach { out.u16(it) }
        seq.frames.forEach { out.u16(it and 0xFFFF) }
        seq.frames.forEach { out.u16(it ushr 16) }
        seq.loopOffset?.let { out.u8(2); out.u16(it) }
        seq.blend?.let { b -> out.u8(3); out.u8(b.size); b.forEach { out.u8(it) } }
        seq.priority?.let { out.u8(5); out.u8(it) }
        seq.maxLoops?.let { out.u8(8); out.u8(it) }
        seq.animatingPrecedence?.let { out.u8(9); out.u8(it) }
        seq.walkingPrecedence?.let { out.u8(10); out.u8(it) }
        seq.replayMode?.let { out.u8(11); out.u8(it) }
        seq.secondaryFrames?.let { s ->
            out.u8(12)
            out.u8(s.size)
            s.forEach { out.u16(it and 0xFFFF) }
            s.forEach { out.u16(it ushr 16) }
        }
        if (seq.sounds.isNotEmpty()) {
            out.u8(13)
            out.u16(n)
            for (frame in 0 until n) {
                val sound = seq.sounds[frame]
                if (sound == null) {
                    out.u8(0)
                } else {
                    out.u8(1)
                    val packed = (sound.first shl 8) or (sound.second.coerceAtMost(7) shl 5)
                    out.u8(packed ushr 16)
                    out.u16(packed and 0xFFFF)
                }
            }
        }
        out.u8(0)
        return out.toByteArray()
    }

    // ---- spotanims -----------------------------------------------------------------------------

    class Spot(var model: Int = -1, var seq: Int = -1, val keep: ByteArrayOutputStream = ByteArrayOutputStream(), val dropped: MutableList<String> = mutableListOf())

    /** OSRS spotanim (RuneLite `SpotAnimLoader`); opcodes 4-8 and 40 are copied verbatim, 41 retextures are dropped. */
    fun decodeOsrsSpot(bytes: ByteArray): Spot {
        val c = Cursor(bytes)
        val spot = Spot()
        while (true) {
            val start = c.pos
            when (val op = c.u8()) {
                0 -> return spot
                1 -> spot.model = c.u16()
                2 -> spot.seq = c.u16()
                3 -> spot.model = c.i32()
                4, 5, 6 -> {
                    c.u16()
                    spot.keep.write(bytes, start, 3)
                }
                7, 8 -> {
                    c.u8()
                    spot.keep.write(bytes, start, 2)
                }
                9 -> spot.dropped += "debug name ${c.string()}"
                40 -> {
                    val n = c.u8()
                    repeat(n) { c.u16(); c.u16() }
                    spot.keep.write(bytes, start, 2 + n * 4)
                }
                41 -> {
                    val n = c.u8()
                    repeat(n) { c.u16(); c.u16() }
                    spot.dropped += "retexture of $n OSRS textures (textures are flattened into the mesh colours)"
                }
                else -> error("unknown OSRS spotanim opcode $op")
            }
        }
    }

    fun encode667Spot(
        spot: Spot,
        localModel: Int,
        localSeq: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        if (localModel >= 0) {
            check(localModel <= OsrsItemImportTool.SPOTANIM_MODEL_LIMIT) { "spotanim model $localModel above the signed-short limit" }
            out.u8(1)
            out.u16(localModel)
        }
        if (localSeq >= 0) {
            out.u8(2)
            out.u16(localSeq)
        }
        out.write(spot.keep.toByteArray())
        out.u8(0)
        return out.toByteArray()
    }

    // ---- strict 667 re-decoders used after the write -------------------------------------------

    /** Walks a 667 sequence exactly like `SeqType.decode`; returns the frame ids. */
    fun decode667SeqFrames(bytes: ByteArray): IntArray {
        val c = Cursor(bytes)
        var frames = IntArray(0)
        while (true) {
            when (val op = c.u8()) {
                0 -> {
                    check(c.remaining == 0) { "sequence has trailing bytes" }
                    return frames
                }
                1 -> {
                    val n = c.u16()
                    repeat(n) { c.u16() }
                    val lo = IntArray(n) { c.u16() }
                    frames = IntArray(n) { lo[it] + (c.u16() shl 16) }
                }
                2 -> c.u16()
                3 -> repeat(c.u8()) { c.u8() }
                5, 8, 9, 10, 11 -> c.u8()
                6, 7 -> c.u16()
                12 -> {
                    val n = c.u8()
                    repeat(n * 2) { c.u16() }
                }
                13 -> {
                    val n = c.u16()
                    repeat(n) {
                        val options = c.u8()
                        if (options > 0) {
                            c.u8()
                            c.u16()
                            repeat(options - 1) { c.u16() }
                        }
                    }
                }
                14, 15, 16, 18 -> Unit
                else -> error("opcode $op is not a 667 sequence opcode")
            }
        }
    }

    /** Decodes a 667 frame against its 667 base exactly like `AnimFrame` (without its silent catch). */
    fun check667Frame(
        frame: ByteArray,
        base: ByteArray,
    ) {
        val baseCursor = Cursor(base)
        val count = baseCursor.u8()
        val types = IntArray(count) { baseCursor.u8() }
        val c = Cursor(frame, 3)
        val length = c.u8()
        check(length <= count) { "frame length $length exceeds base count $count" }
        val data = Cursor(frame, c.pos + length)
        for (i in 0 until length) {
            val flags = c.u8()
            if (flags <= 0) continue
            check(i < types.size)
            for (bit in 0..2) if (flags and (1 shl bit) != 0) data.smarts()
        }
        check(data.remaining == 0) { "frame data does not end where the flags say" }
        check(base.size == 1 + count * 4 + (base.size - 1 - count * 4)) // arrays present
    }

    // ---- asset map -----------------------------------------------------------------------------

    /** `kind:upstream` -> local id for fx entries already recorded in the asset map (`fx_kind` entries of `imports:`). */
    fun existingFx(assetMap: File): Map<String, Int> {
        val root = ObjectMapper(YAMLFactory()).readTree(assetMap) ?: return emptyMap()
        val out = mutableMapOf<String, Int>()
        root.path("imports").forEach { entry ->
            val kind = entry.path("fx_kind")
            if (kind.isTextual && entry.path("upstream_fx_id").isInt && entry.path("local_fx_id").isInt) {
                out["${kind.asText()}:${entry.path("upstream_fx_id").asInt()}"] = entry.path("local_fx_id").asInt()
            }
        }
        return out
    }

    // ---- main ----------------------------------------------------------------------------------

    @JvmStatic
    fun main(args: Array<String>) {
        val batchName = args.firstOrNull { !it.startsWith("--") } ?: error("Usage: <batch> [--apply]")
        val spotIds = BATCHES[batchName] ?: error("Unknown batch '$batchName' (known: ${BATCHES.keys})")
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val existing = existingFx(assetMap)
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(TARGETS[0])
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val refused = mutableListOf<String>()
        try {
            fun next(index: Int): Int = (library.index(index).archiveIds().maxOrNull() ?: -1) + 1
            fun nextPaged(index: Int, shift: Int): Int {
                val group = library.index(index).archiveIds().maxOrNull() ?: return 0
                val maxFile = library.index(index).archive(group)?.fileIds()?.maxOrNull() ?: -1
                return (group shl shift) + maxFile + 1
            }
            var nextSpot = nextPaged(INDEX_SPOTANIM, 8)
            var nextSeq = nextPaged(INDEX_SEQ, 7)
            var nextFrameset = next(INDEX_FRAMES)
            var nextBase = next(INDEX_BASES)
            var nextSynth = next(INDEX_SYNTH)

            val census = ModelNamespaceCensusTool.census(TARGETS[0], TARGETS[1], assetMap)
            check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
            check(census.untracedReferenceTypes.isEmpty()) { "untraced model reference types: ${census.untracedReferenceTypes}" }
            val modelHoles = census.provenFreeHoles.filter { it in 1..OsrsItemImportTool.SPOTANIM_MODEL_LIMIT }.sorted().toMutableList()
            println("CENSUS spotanim_model_holes=${modelHoles.size} next spot=$nextSpot seq=$nextSeq frameset=$nextFrameset base=$nextBase synth=$nextSynth")

            val localIds = existing.toMutableMap()
            fun local(kind: String, upstream: Int, allocate: () -> Int): Int =
                localIds.getOrPut("$kind:$upstream") { allocate().also { records += "$kind|$upstream|$it" } }

            fun sha1At(index: Int, group: Int, file: Int): String? = library.data(index, group, file)?.let { CacheItemProbeTool.sha1(it) }

            fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
                val current = sha1At(index, group, file)
                mutations += CacheMutation(index, group, file, bytes, label, expectedCurrentSha1 = current?.takeIf { it != CacheItemProbeTool.sha1(bytes) })
            }

            for (spotId in spotIds) {
                val spotBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SPOTANIM, spotId) ?: error("OSRS spotanim $spotId missing")
                val staged = mutableListOf<() -> Unit>()
                // A refused spotanim must leave no allocation, record or mutation behind.
                val recordMark = records.size
                val idsBefore = localIds.toMap()
                val holesBefore = modelHoles.toList()
                val countersBefore = listOf(nextSpot, nextSeq, nextFrameset, nextBase, nextSynth)
                try {
                    val spot = decodeOsrsSpot(spotBytes)
                    var localSeq = -1
                    if (spot.seq >= 0) {
                        val seqBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, spot.seq) ?: error("OSRS sequence ${spot.seq} missing")
                        val seq = decodeOsrsSeq(seqBytes)
                        dropped += seq.dropped.map { "seq ${spot.seq}: $it" }
                        val framesets = (seq.frames.map { it ushr 16 } + (seq.secondaryFrames?.map { it ushr 16 } ?: emptyList())).distinct()
                        val framesetMap = framesets.associateWith { fs -> local("frameset", fs) { nextFrameset++ } }
                        framesets.forEach { fs ->
                            reader.files(INDEX_FRAMES, fs).forEach { (file, frameBytes) ->
                                val osrsBase = ((frameBytes[0].toInt() and 0xFF) shl 8) or (frameBytes[1].toInt() and 0xFF)
                                val baseBytes = reader.file(INDEX_BASES, osrsBase, 0) ?: error("OSRS base $osrsBase missing")
                                val localBase = local("base", osrsBase) { nextBase++ }
                                val converted = convertFrame(frameBytes, baseTypes(baseBytes), localBase)
                                val base667 = convertBase(baseBytes)
                                check667Frame(converted, base667)
                                staged += { put(INDEX_BASES, localBase, 0, base667, "osrs base $osrsBase") }
                                staged += { put(INDEX_FRAMES, framesetMap.getValue(fs), file, converted, "osrs frame $fs:$file") }
                            }
                        }
                        seq.frames = IntArray(seq.frames.size) { (framesetMap.getValue(seq.frames[it] ushr 16) shl 16) or (seq.frames[it] and 0xFFFF) }
                        seq.secondaryFrames = seq.secondaryFrames?.let { s -> IntArray(s.size) { (framesetMap.getValue(s[it] ushr 16) shl 16) or (s[it] and 0xFFFF) } }
                        val soundMap = seq.sounds.values.map { it.first }.distinct().associateWith { id ->
                            val synth = reader.file(INDEX_SYNTH, id, 0) ?: error("OSRS synth $id missing")
                            local("synth", id) { nextSynth++ }.also { localId -> staged += { put(INDEX_SYNTH, localId, 0, synth, "osrs synth $id") } }
                        }
                        seq.sounds.replaceAll { _, sound -> soundMap.getValue(sound.first) to sound.second }
                        localSeq = local("seq", spot.seq) { nextSeq++ }
                        val seq667 = encode667Seq(seq)
                        decode667SeqFrames(seq667)
                        staged += { put(INDEX_SEQ, localSeq ushr 7, localSeq and 0x7F, seq667, "osrs seq ${spot.seq}") }
                    }
                    var localModel = -1
                    if (spot.model >= 0) {
                        val modelBytes = OsrsModelConversion.convert(reader, spot.model, dropped)
                        localModel = local("spotanim_model", spot.model) { modelHoles.removeAt(0) }
                        staged += { put(ModelConvertTool.MODEL_INDEX, localModel, 0, modelBytes, "osrs spotanim model ${spot.model}") }
                    }
                    dropped += spot.dropped.map { "spotanim $spotId: $it" }
                    val localSpot = local("spotanim", spotId) { nextSpot++ }
                    val spot667 = encode667Spot(spot, localModel, localSeq)
                    staged += { put(INDEX_SPOTANIM, localSpot ushr 8, localSpot and 0xFF, spot667, "osrs spotanim $spotId") }
                    staged.forEach { it() }
                    println("PLAN spotanim $spotId -> $localSpot model ${spot.model}->$localModel seq ${spot.seq}->$localSeq")
                } catch (e: RuntimeException) {
                    refused += "spotanim $spotId: ${e.javaClass.simpleName}: ${e.message}"
                    while (records.size > recordMark) records.removeAt(records.size - 1)
                    localIds.clear()
                    localIds.putAll(idsBefore)
                    modelHoles.clear()
                    modelHoles.addAll(holesBefore)
                    nextSpot = countersBefore[0]
                    nextSeq = countersBefore[1]
                    nextFrameset = countersBefore[2]
                    nextBase = countersBefore[3]
                    nextSynth = countersBefore[4]
                }
            }
        } finally {
            library.close()
            reader.close()
        }
        refused.forEach { println("REFUSED $it") }
        dropped.distinct().forEach { println("DROPPED $it") }
        val unique = mutations.distinctBy { it.describeLocation() }
        val transaction = CacheTransaction(targets = TARGETS, mutations = unique)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${unique.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN records=${records.size}")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
        if (records.isNotEmpty()) {
            val block = StringBuilder()
            records.forEach { r ->
                val (kind, upstream, localId) = r.split('|')
                block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_OSRS_FX_TOOL\n    transaction: ${transaction.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} fx entries")
        }
    }
}
