package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * OSRS loc (scenery) type import into both rev-667 caches, for locs that are spawned at runtime rather than placed in a map square.
 * The model and LocType steps are the ones [FeroxImportTool] proved live: models go into census-proven free model holes with
 * textured faces flattened ([stripTextures]), the LocType is written by [Rev667LocType.encodeFromModern] after the next free loc id.
 * A loc animation is kept only when the OSRS sequence is a classic one already imported (asset map `seq:<id>`); skeletal ones are
 * dropped and reported, because rev 667 cannot represent them.
 *
 * Usage: `<batch> [--apply]` - without `--apply` only plan + preflight run.
 */
object OsrsLocImportTool {
    val BATCHES: Map<String, List<Int>> =
        mapOf(
            // Owner 2026-09-19 (exact OSRS Deadman breaches): OSRS Wiki "Breach (scenery)" id 49561 and "Boss Spawn" id 49563.
            "deadman-breach" to listOf(49561, 49563),
            // Owner 2026-10-10 ("de rejuvenation pool model van osrs importeren en die in de poh plaatsen"): OSRS loc 29241
            // "Ornate pool of Rejuvenation", the pool every house's Superior Garden holds (PlayerHouse.POOL). Its water
            // animation 7304 comes from OsrsFxImportTool batch "poh-pool", which must be applied first.
            "poh-pool" to listOf(29241),
            // Owner 2026-10-10 (POH: only teleports and PvP/PvM functions, OSRS pieces): Altar of the Occult - variants 31858
            // standard / 31859 ancient / 31860 lunar and their multiloc 29150 (variants first, the multiloc points at them) -
            // the crystalline Portal Nexus 33410, the Ornate Jewellery Box 37520 and the Spiritual fairy tree 29229.
            "poh-house" to listOf(31858, 31859, 31860, 29150, 33410, 37520, 29229),
            // Owner 2026-10-10 night run (build mode): the Superior Garden's theme space - the 2x2 "Theme space" 29130 (Build) and
            // the seven ground-edging "Theme space" spaces 29123-29129 (OSRS 2499 region 7534 chunk 5,0) - and the Otherworldly
            // theme built on them: the five "Otherworldly garden" edgings 29248-29252 and the "Otherworldly theme" 29253 (Remove).
            "poh-theme" to listOf(29130, 29123, 29124, 29125, 29126, 29127, 29128, 29129, 29248, 29249, 29250, 29251, 29252, 29253),
        )

    /**
     * Multilocs whose variant table is kept, by OSRS loc: the rev-667 varbit that replaces the OSRS one. 29150 Altar of the
     * Occult switches on OSRS varbit 4070 (the spellbook, 0 standard / 1 ancient / 2 lunar / 3 arceuus); the 667 client's own
     * spellbook varbit 357 has the same 0-2 values (api/Spellbook.kt). A variant the batch does not import (31861, Arceuus -
     * a spellbook this revision does not have) falls back to the table's default.
     */
    private val MULTILOC_VARBITS: Map<Int, Int> = mapOf(29150 to 357)

    /**
     * Construction build-mode options a batch drops: the house is furnished, never built, so "Remove"/"Upgrade" would be
     * dead menu entries (the same clean-up [PohObjectOptionTool] does for the rev-667 POH locs).
     */
    private val STRIPPED_OPTIONS: Map<String, Set<String>> =
        mapOf("poh-pool" to setOf("Remove", "Upgrade"), "poh-house" to setOf("Remove", "Upgrade", "Arceuus", "Configuration"))

    @JvmStatic
    fun main(args: Array<String>) {
        val batch = BATCHES[args.getOrNull(0)] ?: error("Usage: <${BATCHES.keys.joinToString("|")}> [--apply]")
        val stripped = STRIPPED_OPTIONS[args[0]].orEmpty()
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        val records = ArrayList<String>()
        val importedSeqs = OsrsFxImportTool.existingFx(assetMap)

        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val modernLocs = modern.files(ModernCacheReader.INDEX_CONFIG, 6)
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, assetMap)
                val free = census.provenFreeHoles.iterator()
                val modelIdMap = LinkedHashMap<Int, Int>()
                val locIdMap = LinkedHashMap<Int, Int>()

                batch.forEach { osrsId ->
                    val src = ModernObjectDef.decode(osrsId, modernLocs[osrsId] ?: error("OSRS loc $osrsId missing"))
                    val anim = src.animationId
                    val keepAnim = anim >= 0 && importedSeqs.containsKey("seq:$anim")
                    if (anim >= 0 && !keepAnim) dropped += "loc $osrsId: animation $anim (not an imported classic sequence)"
                    if (!keepAnim) src.animationId = -1
                    src.options.indices.filter { src.options[it] in stripped }.forEach { src.options[it] = null }
                    val def = src
                    def.models.forEach { modernId ->
                        if (modelIdMap.containsKey(modernId)) return@forEach
                        // A model an earlier run of this tool already imported stays where it is (asset map `osrs_loc_model`).
                        importedSeqs["osrs_loc_model:$modernId"]?.let { modelIdMap[modernId] = it; return@forEach }
                        check(free.hasNext()) { "Out of proven-free model ids." }
                        val localId = free.next()
                        val raw = ModernModelDecoder.decode(modern.file(FeroxImportTool.MODEL_INDEX, modernId, 0) ?: error("model $modernId missing"))
                        val decoded =
                            if ((raw.faceTexture?.count { it.toInt() != -1 } ?: 0) == 0 && raw.texSpaceCount == 0) {
                                raw
                            } else {
                                dropped += "model $modernId: textured faces flattened to the textures' average colour"
                                stripTextures(raw) { tex ->
                                    val t = modern.file(9, 0, tex) ?: error("texture $tex missing")
                                    osrsTextureAverageHsl(t)
                                }
                            }
                        if (decoded.droppedAnimayaSkinning) dropped += "model $modernId: animaya skinning (no rev-667 equivalent)"
                        val converted = Rev667ModelEncoder.encode(decoded)
                        val back = Rev667ModelDecoder.decode(converted)
                        check(back.vertexCount == decoded.vertexCount && back.faceCount == decoded.faceCount) { "model $modernId lost geometry" }
                        modelIdMap[modernId] = localId
                        mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localId, 0, converted, "osrs loc model $modernId -> $localId")
                        records += "model|$modernId|$localId"
                    }
                    // A re-import rewrites the loc id the asset map already records, pinned to the bytes it replaces, so
                    // fixing a batch never leaves the first import behind as an orphan loc.
                    val previous = importedSeqs["osrs_loc_loc:$osrsId"]
                    val localId = previous ?: nextLocId++
                    locIdMap[osrsId] = localId
                    val multiloc =
                        MULTILOC_VARBITS[osrsId]?.let { varbit ->
                            val table = def.transforms ?: error("loc $osrsId has no transform table")
                            val default = table.last().let { if (it == -1) -1 else locIdMap[it] ?: error("loc $osrsId: default variant $it not in this batch") }
                            varbit to IntArray(table.size) { i -> if (i == table.size - 1 || table[i] == -1) default else locIdMap[table[i]] ?: default }
                        }
                    multiloc?.let { (varbit, dest) -> println("PLAN multiloc $osrsId varbit ${def.varbitId}->$varbit transforms=${def.transforms?.toList()}->${dest.toList()}") }
                    // The loc's idle animation points at the local id OsrsFxImportTool gave the OSRS sequence.
                    val bytes = Rev667LocType.encodeFromModern(def, { modelIdMap.getValue(it) }, dropped, multiloc) { importedSeqs["seq:$it"] }
                    check(Rev667LocType.decode(localId, bytes).allModels == def.models.map { modelIdMap.getValue(it) }) { "loc $osrsId model mismatch" }
                    val current = if (previous == null) null else library.data(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF)
                    mutations +=
                        CacheMutation(
                            FeroxImportTool.LOC_INDEX,
                            localId ushr 8,
                            localId and 0xFF,
                            bytes,
                            "osrs loc $osrsId '${def.name}' -> $localId",
                            expectedCurrentSha1 = current?.let { CacheItemProbeTool.sha1(it) },
                        )
                    if (previous == null) records += "loc|$osrsId|$localId"
                    println(
                        "PLAN loc $osrsId '${def.name}' -> $localId models=${def.models}->${def.models.map { modelIdMap[it] }} size=${def.sizeX}x${def.sizeY} " +
                            "interact=${def.interactType} anim=${if (keepAnim) anim else -1} options=${def.options.toList()}",
                    )
                }
            } finally {
                library.close()
            }
        }
        dropped.distinct().forEach { println("DROPPED $it") }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        blocking.forEach { println("  BLOCKING: $it") }
        if (!apply) {
            println("DRY_RUN records=${records.size}")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        val verify = tx.verify()
        if (verify.isNotEmpty()) {
            verify.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${tx.rollback()}")
            return
        }
        println("APPLIED transaction=${tx.id} writes=${result.applied} skipped=${result.skipped}")
        val block = StringBuilder()
        records.forEach { r ->
            val (kind, upstream, local) = r.split('|')
            block.append("  - fx_kind: osrs_loc_$kind\n    upstream_fx_id: $upstream\n    local_fx_id: $local\n    status: IMPORTED_BY_OSRS_LOC_TOOL\n    transaction: ${tx.id}\n")
        }
        val text = assetMap.readText()
        assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        println("ASSET_MAP appended ${records.size} loc import entries")
    }
}
