package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs

/**
 * N462 / SKELETAL-ONDERZOEK route A (2026-11-06): exports the REAL OSRS skeletal data of one npc - the rig (index 1 framemap with its
 * skeletal tail), the animaya clips (index 22) and the per-vertex bone weights + bind positions of its body model(s) - for the 667
 * client's skeletal player (`com.jagex.graphics.skeletal` in the client). Nothing is baked: the client decodes the rig and the clip
 * curves and skins every vertex itself, like the OSRS client. Read-only on the OSRS cache.
 *
 * Every piece has a NAME (`rig_<id>`, `clip_<group>_<file>`, `skin_<localNpc>`) and the index lists, per local 667 sequence,
 * `seq.<localSeq>=<clip>|<rig>|<skin>|<start>|<end>|<bone mask, comma list or empty>|<osrsSeq>`.
 *  - `dir <outDir> ...`: writes `<name>.bin` files and appends to `<outDir>/skeletal.properties` (test client -Prsps.skeletalDir).
 *  - `cache plan|apply ...`: index 10 (BINARY) groups `skel_<name>` + the index group `skel_index`, through [CacheTransaction] on both
 *    caches (the game server and file server must be stopped for apply). Old clients never ask for these groups.
 *
 * Usage: `./gradlew :game:runSkeletalSidecarExportTool --args="dir <outDir> <localNpc> <osrsModel>[,...] <localSeq>=<osrsSeq> ..."`
 *        `./gradlew :game:runSkeletalSidecarExportTool --args="cache plan|apply <localNpc> <osrsModel>[,...] <localSeq>=<osrsSeq> ..."`
 */
object SkeletalSidecarExportTool {
    const val BINARY_INDEX = 10
    const val INDEX_GROUP = "skel_index"
    const val GROUP_PREFIX = "skel_"

    class Export(val files: LinkedHashMap<String, ByteArray>, val indexLines: List<String>)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 5) { "usage: dir <outDir> | cache plan|apply, then <localNpc> <osrsModel,...> <localSeq>=<osrsSeq> ..." }
        val mode = args[0]
        val localNpc = args[2].toInt()
        val models = args[3].split(',').map { it.trim().toInt() }
        val seqPairs = args.drop(4).map { a -> a.split('=').let { it[0].toInt() to it[1].toInt() } }
        val export = export(localNpc, models, seqPairs)
        when (mode) {
            "dir" -> {
                val out = File(args[1]).also { it.mkdirs() }
                export.files.forEach { (name, bytes) -> File(out, "$name.bin").writeBytes(bytes) }
                File(out, "skeletal.properties").appendText(export.indexLines.joinToString("") { "$it\n" })
                println("DIR ${out.absolutePath}: ${export.files.keys}")
            }
            "cache" -> cache(args[1], export)
            else -> error("mode must be dir or cache")
        }
    }

    fun export(localNpc: Int, models: List<Int>, seqPairs: List<Pair<Int, Int>>): Export {
        val files = LinkedHashMap<String, ByteArray>()
        val lines = ArrayList<String>()
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val raws = models.map { m -> ModernModelDecoder.decode(reader.file(7, m, 0) ?: error("model $m missing"), flattenTextures = true) }
            val dropped = ArrayList<String>()
            val converted = models.map { m -> Rev667ModelDecoder.decode(OsrsModelConversion.convert(reader, m, dropped)) }
            raws.indices.forEach { i -> check(converted[i].vertexCount == raws[i].vertexCount) { "model ${models[i]} changed vertex count in conversion" } }
            // Client units per OSRS unit (same as SkeletalBakeTool.bakeSet): the client upscales version<13 meshes on load.
            val up = SkeletalBakeTool.clientUpscale(converted[0])
            var bi = 0
            var bv = 0
            var best = -1
            raws.indices.forEach { mi ->
                (0 until raws[mi].vertexCount).forEach { v ->
                    val s = abs(raws[mi].vertexX[v]) + abs(raws[mi].vertexY[v]) + abs(raws[mi].vertexZ[v])
                    if (s > best) { best = s; bi = mi; bv = v }
                }
            }
            val k = up * (abs(converted[bi].vertexX[bv]) + abs(converted[bi].vertexY[bv]) + abs(converted[bi].vertexZ[bv])).toDouble() / best.coerceAtLeast(1)
            val skinName = "skin_$localNpc"
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { o ->
                o.writeInt(0x534B4E31) // 'SKN1'
                o.writeFloat(k.toFloat())
                o.writeShort(raws.sumOf { it.vertexCount })
                raws.forEach { m ->
                    for (v in 0 until m.vertexCount) {
                        o.writeInt(m.vertexX[v]); o.writeInt(m.vertexY[v]); o.writeInt(m.vertexZ[v])
                        val b = m.animayaBones?.getOrNull(v) ?: IntArray(0)
                        val w = m.animayaWeights?.getOrNull(v) ?: IntArray(0)
                        o.writeByte(b.size)
                        b.indices.forEach { j -> o.writeShort(b[j]); o.writeByte(w[j]) }
                    }
                }
            }
            files[skinName] = bytes.toByteArray()
            println("skin $skinName: models=$models vertices=${raws.sumOf { it.vertexCount }} k=$k (upscale $up)")
            for ((localSeq, osrsSeq) in seqPairs) {
                val s = SkeletalBakeTool.decodeSkeletalSeq(reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, osrsSeq) ?: error("seq $osrsSeq missing"))
                    ?: error("seq $osrsSeq is not skeletal")
                val g = s.animaya ushr 16
                val f = s.animaya and 0xFFFF
                val clipBytes = reader.file(SkeletalBakeTool.INDEX_ANIMAYA, g, f) ?: error("animaya ${s.animaya} missing")
                val clip = SkeletalBakeTool.decodeClip(clipBytes)
                val rigBytes = reader.file(SkeletalBakeTool.INDEX_SKELETONS, clip.baseId, 0) ?: error("rig ${clip.baseId} missing")
                val clipName = "clip_${g}_$f"
                val rigName = "rig_${clip.baseId}"
                files[clipName] = clipBytes
                files[rigName] = rigBytes
                val start = if (s.start >= 0) s.start else 0
                val end = if (s.end > start) s.end else clip.lastTick + 1
                lines += "seq.$localSeq=$clipName|$rigName|$skinName|$start|$end|${s.mask?.joinToString(",").orEmpty()}|$osrsSeq"
                println("seq $localSeq <- OSRS $osrsSeq animaya ${s.animaya} ($clipName, ${clipBytes.size} bytes) rig ${clip.baseId} ticks $start..$end sounds=${s.sounds.size} mask=${s.mask?.size ?: "-"}")
            }
        }
        return Export(files, lines)
    }

    private fun cache(mode: String, export: Export) {
        require(mode == "plan" || mode == "apply") { "cache plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val index = library.index(BINARY_INDEX)
            var next = (index.archiveIds().maxOrNull() ?: -1) + 1
            fun mutation(name: String, bytes: ByteArray, label: String): CacheMutation {
                val existing = index.archive(name)
                return if (existing != null) {
                    val current = library.data(BINARY_INDEX, existing.id, 0)
                    CacheMutation(BINARY_INDEX, existing.id, 0, bytes, label, expectedCurrentSha1 = current?.let { CacheItemProbeTool.sha1(it) })
                } else {
                    CacheMutation(BINARY_INDEX, next++, 0, bytes, label, groupName = name)
                }
            }
            export.files.forEach { (name, bytes) -> mutations += mutation(GROUP_PREFIX + name, bytes, "skeletal $name (${bytes.size} bytes)") }
            // The index group: existing lines kept, the exported sequences replaced.
            val old = index.archive(INDEX_GROUP)?.let { library.data(BINARY_INDEX, it.id, 0) }?.toString(Charsets.UTF_8).orEmpty()
            val merged = LinkedHashMap<String, String>()
            old.lines().filter { it.contains('=') }.forEach { merged[it.substringBefore('=').trim()] = it.trim() }
            export.indexLines.forEach { merged[it.substringBefore('=').trim()] = it }
            val indexBytes = merged.values.joinToString("") { "$it\n" }.toByteArray(Charsets.UTF_8)
            mutations += mutation(INDEX_GROUP, indexBytes, "skeletal index (${merged.size} sequences)")
        } finally {
            library.close()
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        mutations.forEach { println("  ${it.describeLocation()} ${it.groupName ?: "(existing)"} ${it.label}") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes and names")
    }
}
