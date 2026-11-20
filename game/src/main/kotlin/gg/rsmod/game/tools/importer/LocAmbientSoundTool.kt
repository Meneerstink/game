package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * TOA-1911 nachtrun 20-11: the OSRS region/loc import DROPS loc ambient sounds (Rev667WorldCodec: "loc N: ambient sound"), so the ToA
 * rooms were silent where OSRS has frogs, wind, water drips, bats, rumble... (TOA_FROG / TOA_WIND / TOA_WATER_DROP ... in RuneLite gameval).
 * The rev-667 client reads loc opcode 78 (sound id u16, range u8) and opcode 79 (min/max delay u16, range u8, count u8, ids u16...) -
 * the OSRS opcode 79 has one extra "retain" byte that 667 does not.
 *
 * For every imported ToA loc (data/cfg/toa/ids.tsv `loc <osrs> <local>`): decode the OSRS definition; when it carries op 78 / op 79,
 * append the same opcode to the local definition (OSRS synth ids translated through ids.tsv `synth <osrs> <local>`; a synth that is not
 * imported yet is reported as MISSING and the loc is skipped - import it first with OsrsFxImportTool toa-ambient-synth).
 * Never touches a loc that already has op 78/79.
 *
 * Usage: `LocAmbientSoundTool plan|apply`  (apply = both caches, one CacheTransaction; testserver + fileserver stopped).
 */
object LocAmbientSoundTool {
    private val IDS = File("C:/RSPS/game/game/data/cfg/toa/ids.tsv")

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty() && args[0] in setOf("plan", "apply")) { "Usage: plan|apply" }
        val locMap = LinkedHashMap<Int, Int>()
        val synthMap = HashMap<Int, Int>()
        IDS.readLines().forEach { line ->
            val p = line.split('\t')
            if (p.size >= 3 && p[0] == "loc") locMap[p[1].toInt()] = p[2].toInt()
            if (p.size >= 3 && p[0] == "synth") synthMap[p[1].toInt()] = p[2].toInt()
        }
        val mutations = ArrayList<CacheMutation>()
        val missing = sortedMapOf<Int, MutableList<String>>()
        val perSound = sortedMapOf<Int, Int>()
        var withSound = 0
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache").toString())).use { modern ->
                val defs = modern.files(2, 6)
                for ((osrs, local) in locMap) {
                    val raw = defs[osrs] ?: continue
                    val def = try { ModernObjectDef.decode(osrs, raw) } catch (e: Exception) { println("UNDECODABLE loc $osrs: ${e.message}"); continue }
                    val single = def.ambientSoundId != -1
                    val random = def.randomAmbient
                    if (!single && random == null) continue
                    withSound++
                    val data = library.data(FeroxImportTool.LOC_INDEX, local ushr 8, local and 0xFF) ?: error("loc $local missing")
                    val localDef = Rev667LocType.decode(local, data)
                    if (78 in localDef.opcodes || 79 in localDef.opcodes) { println("SKIP loc $local (osrs $osrs): already has an ambient sound"); continue }
                    require(data.last().toInt() == 0) { "loc $local does not end with opcode 0" }
                    val add = java.io.ByteArrayOutputStream()
                    fun u16(v: Int) { add.write(v ushr 8); add.write(v) }
                    var ok = true
                    if (single) {
                        val id = synthMap[def.ambientSoundId]
                        if (id == null) { missing.getOrPut(def.ambientSoundId) { mutableListOf() }.add("loc $osrs"); ok = false }
                        else {
                            add.write(78); u16(id); add.write(def.ambientSoundRadius)
                            perSound.merge(def.ambientSoundId, 1, Int::plus)
                        }
                    } else if (random != null) {
                        val ids = random.drop(4)
                        val mapped = ids.map { synthMap[it] }
                        ids.filterIndexed { i, _ -> mapped[i] == null }.forEach { missing.getOrPut(it) { mutableListOf() }.add("loc $osrs") }
                        if (mapped.any { it == null }) ok = false
                        else {
                            add.write(79); u16(random[0]); u16(random[1]); add.write(random[2]); add.write(mapped.size)
                            mapped.forEach { u16(it!!) }
                            ids.forEach { perSound.merge(it, 1, Int::plus) }
                        }
                    }
                    if (!ok) continue
                    val bytes = data.copyOf(data.size - 1) + add.toByteArray() + byteArrayOf(0)
                    Rev667LocType.decode(local, bytes) // full opcode walk: throws on a malformed definition
                    println("ADD loc $local (osrs $osrs '${def.name}'): " + (if (single) "op78 sound ${def.ambientSoundId} range ${def.ambientSoundRadius} retain ${def.ambientSoundRetain}" else "op79 ${random!!.toList()}"))
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, local ushr 8, local and 0xFF, bytes, "loc $local ambient sound (osrs $osrs)", CacheItemProbeTool.sha1(data))
                }
            }
        } finally {
            library.close()
        }
        println("SUMMARY locs with ambient sound in OSRS: $withSound, to add: ${mutations.size}")
        println("SOUNDS used (osrs id x locs): $perSound")
        println("MISSING synths (osrs id: locs): " + missing.mapValues { it.value.size })
        if (missing.isNotEmpty()) println("MISSING_IDS " + missing.keys.joinToString(","))
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (args[0] == "plan") return
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes")
    }
}
