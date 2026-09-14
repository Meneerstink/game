package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Ferox multiloc repair (adjacent root cause from RCV-012 decision 3b).
 *
 * The Ferox world import encodes every modern LocType with [Rev667LocType.encodeFromModern], which drops varbit/varp transform tables.
 * An OSRS multiloc has no model or name of its own, so each such placement became an invisible, option-less loc (audit over the
 * asset map's "Ferox Enclave world import" loc_ids: 7 of 265; the Loot Chest was fixed by [FeroxLootChestTool]). The client shows
 * `transforms[varbit value]`; this server has none of those OSRS varbits, whose value is therefore 0, so the correct 667 placement is
 * `transforms[0]`. Each fix below imports that LocType (or reuses a rev-667 LocType with the same id, name and footprint, exactly like
 * the world import) and re-points every placement of the broken local id inside the Ferox regions.
 *
 * Usage: `plan|apply`.
 */
object FeroxMultilocTool {
    /** Broken local id -> OSRS multiloc it came from and that multiloc's transforms[0] (probed from cache 2499). */
    data class Fix(val brokenLocalId: Int, val multiloc: Int, val varbit: Int, val transform0: Int, val label: String)

    val FIXES =
        listOf(
            Fix(62366, 1729, 9578, 14513, "Tree (multiloc Tree / Handy portal / Tree)"),
            Fix(62370, 3834, 4621, 39687, "Bench (multiloc Bench / Supplies)"),
            Fix(62382, 7812, 4621, 39642, "Altar Pray-at (multiloc Altar / Altar with spellbooks)"),
            Fix(62422, 26813, 4337, 26797, "Poll booth (multiloc Poll booth / Poll booth)"),
            Fix(62551, 29068, 5304, 37382, "Supply chest (multiloc Supply chest / Supply chest Use)"),
        )

    /**
     * First run (tx-20260914-110705) imported transforms that the Ferox world import had already imported, because it only looked
     * for rev-667 ids: Tree 14513 (world import 62402), Bench 39687 (62458), Altar 39642 (62562). `dedupe` re-points those placements
     * to the world-import ids; the duplicate LocTypes stay in the cache unplaced. [feroxLocIds] now prevents the duplication.
     */
    val DEDUPE = mapOf(62584 to 62402, 62585 to 62458, 62586 to 62562)

    /**
     * modern loc id -> local id from the asset map: the "Ferox Enclave world import" loc_ids first, then this tool's own
     * "Ferox multiloc transforms[0] repair" blocks for ids the world import does not have (so a re-run plans nothing).
     */
    fun feroxLocIds(assetMap: File): Map<Int, Int> {
        val text = assetMap.readText()
        fun block(name: String): Map<Int, Int> {
            val out = LinkedHashMap<Int, Int>()
            var start = text.indexOf("name: $name")
            while (start >= 0) {
                val end = text.indexOf("\n  - name:", start + 10).let { if (it < 0) text.length else it }
                Regex("""\{ upstream_id: (\d+), local_id: (\d+), mode: \w+ }""").findAll(text.substring(start, end))
                    .forEach { out.putIfAbsent(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
                start = text.indexOf("name: $name", end)
            }
            return out
        }
        val result = LinkedHashMap(block("Ferox Enclave world import"))
        block("Ferox multiloc transforms[0] repair").forEach { (up, local) -> result.putIfAbsent(up, local) }
        return result
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode in setOf("plan", "apply", "dedupe-plan", "dedupe-apply")) { "Usage: plan|apply|dedupe-plan|dedupe-apply" }
        if (mode.startsWith("dedupe")) return dedupe(apply = mode == "dedupe-apply")
        val mutations = ArrayList<CacheMutation>()
        val dropped = ArrayList<String>()
        val locIdMap = LinkedHashMap<Int, Int>()
        val importedModels = LinkedHashMap<Int, Int>()
        val reused = ArrayList<Int>()
        ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
            val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
            try {
                val modernLocDefs = modern.files(ModernCacheReader.INDEX_CONFIG, 6)
                val localTypes = Rev667RegionProbeTool.locTypes(library)
                var nextLocId = (localTypes.keys.maxOrNull() ?: -1) + 1
                val census = ModelNamespaceCensusTool.census(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE, File(FeroxImportTool.ASSET_MAP))
                check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
                val free = census.provenFreeHoles.filter { it in 0..0xFFFF }.sorted().iterator()
                val worldImport = feroxLocIds(File(FeroxImportTool.ASSET_MAP))

                FIXES.forEach { fix ->
                    val multiloc = ModernObjectDef.decode(fix.multiloc, modernLocDefs[fix.multiloc] ?: error("modern loc ${fix.multiloc} missing"))
                    check(multiloc.name == "null" && multiloc.models.isEmpty()) { "${fix.multiloc} is not a model-less multiloc" }
                    check(multiloc.varbitId == fix.varbit && multiloc.transforms?.firstOrNull() == fix.transform0) {
                        "${fix.multiloc}: varbit ${multiloc.varbitId} transforms ${multiloc.transforms?.toList()} differ from the audit"
                    }
                    if (fix.transform0 in locIdMap) return@forEach
                    val def = ModernObjectDef.decode(fix.transform0, modernLocDefs[fix.transform0] ?: error("modern loc ${fix.transform0} missing"))
                    worldImport[fix.transform0]?.let { existing ->
                        locIdMap[fix.transform0] = existing
                        reused += fix.transform0
                        println("PLAN loc ${fix.transform0} '${def.name}' REUSE world-import local $existing")
                        return@forEach
                    }
                    val local = localTypes[fix.transform0]
                    if (local != null && local.name == def.name && local.sizeX == def.sizeX && local.sizeZ == def.sizeY && def.name != "null") {
                        locIdMap[fix.transform0] = fix.transform0
                        reused += fix.transform0
                        println("PLAN loc ${fix.transform0} '${def.name}' REUSE_AS_IS (rev-667 same name/footprint)")
                        return@forEach
                    }
                    def.models.filter { it !in importedModels }.forEach { m ->
                        check(free.hasNext()) { "Out of proven-free model ids." }
                        val localModel = free.next()
                        importedModels[m] = localModel
                        mutations += CacheMutation(FeroxImportTool.MODEL_INDEX, localModel, 0, OsrsModelConversion.convert(modern, m, dropped), "ferox multiloc model modern=$m -> local=$localModel")
                    }
                    val localId = nextLocId++
                    locIdMap[fix.transform0] = localId
                    val bytes = Rev667LocType.encodeFromModern(def, { importedModels.getValue(it) }, dropped)
                    check(Rev667LocType.decode(localId, bytes).allModels == def.models.map { importedModels.getValue(it) }) { "loc ${fix.transform0} encode/decode mismatch" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, localId ushr 8, localId and 0xFF, bytes, "ferox multiloc transform modern=${fix.transform0} -> local=$localId")
                    println("PLAN loc ${fix.transform0} '${def.name}' options=${def.options.toList()} -> $localId models=${def.models}")
                }

                val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
                val byBroken = FIXES.associate { it.brokenLocalId to locIdMap.getValue(it.transform0) }
                FeroxImportTool.REGIONS.forEach { regionId ->
                    val rx = regionId shr 8
                    val rz = regionId and 0xFF
                    val locName = "l${rx}_$rz"
                    val archive = library.index(FeroxImportTool.MAP_INDEX).archive(locName, keys[regionId]) ?: error("$locName missing")
                    val bytes = archive.file(0)!!.data!!
                    val locs = Rev667LocCodec.decode(bytes)
                    check(Rev667LocCodec.encode(locs).contentEquals(bytes)) { "$locName round-trip failed" }
                    var changed = 0
                    val newLocs =
                        locs.map { loc ->
                            val target = byBroken[loc.id] ?: return@map loc
                            changed++
                            println("PLACEMENT $locName ${loc.id} -> $target at ${rx * 64 + loc.localX},${rz * 64 + loc.localZ},${loc.plane} type=${loc.type} rot=${loc.rotation}")
                            Rev667Loc(target, loc.localX, loc.localZ, loc.plane, loc.type, loc.rotation)
                        }
                    if (changed == 0) return@forEach
                    val newBytes = Rev667LocCodec.encode(newLocs)
                    check(Rev667LocCodec.decode(newBytes).size == locs.size)
                    mutations +=
                        CacheMutation(
                            FeroxImportTool.MAP_INDEX,
                            archive.id,
                            0,
                            newBytes,
                            "ferox multiloc placements in $locName ($changed)",
                            CacheItemProbeTool.sha1(bytes),
                            xtea = keys[regionId],
                        )
                }
            } finally {
                library.close()
            }
        }
        dropped.distinct().forEach { println("DROPPED $it") }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
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
        println("VERIFY_OK both targets hold intended bytes")
        val block = StringBuilder()
        block.append("  - name: Ferox multiloc transforms[0] repair\n")
        block.append("    transaction: ${tx.id}\n")
        block.append("    models:\n")
        importedModels.forEach { (m, l) -> block.append("      - { upstream_id: $m, local_id: $l, role: loc_model }\n") }
        block.append("    loc_ids:\n")
        locIdMap.forEach { (m, l) -> block.append("      - { upstream_id: $m, local_id: $l, mode: ${if (m in reused) "REUSE_AS_IS" else "IMPORT"} }\n") }
        val assetMap = File(FeroxImportTool.ASSET_MAP)
        val text = assetMap.readText()
        assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
        println("ASSET_MAP appended multiloc repair entry")
    }

    private fun dedupe(apply: Boolean) {
        val mutations = ArrayList<CacheMutation>()
        val worldImport = feroxLocIds(File(FeroxImportTool.ASSET_MAP))
        check(worldImport[14513] == 62402 && worldImport[39687] == 62458 && worldImport[39642] == 62562) { "world-import mapping differs: $worldImport" }
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            val keys = Rev667RegionProbeTool.loadKeys(File(FeroxImportTool.XTEAS_FILE))
            FeroxImportTool.REGIONS.forEach { regionId ->
                val rx = regionId shr 8
                val rz = regionId and 0xFF
                val locName = "l${rx}_$rz"
                val archive = library.index(FeroxImportTool.MAP_INDEX).archive(locName, keys[regionId]) ?: error("$locName missing")
                val bytes = archive.file(0)!!.data!!
                val locs = Rev667LocCodec.decode(bytes)
                check(Rev667LocCodec.encode(locs).contentEquals(bytes)) { "$locName round-trip failed" }
                var changed = 0
                val newLocs =
                    locs.map { loc ->
                        val target = DEDUPE[loc.id] ?: return@map loc
                        changed++
                        println("DEDUPE $locName ${loc.id} -> $target at ${rx * 64 + loc.localX},${rz * 64 + loc.localZ},${loc.plane}")
                        Rev667Loc(target, loc.localX, loc.localZ, loc.plane, loc.type, loc.rotation)
                    }
                if (changed == 0) return@forEach
                mutations +=
                    CacheMutation(
                        FeroxImportTool.MAP_INDEX,
                        archive.id,
                        0,
                        Rev667LocCodec.encode(newLocs),
                        "ferox multiloc dedupe in $locName ($changed)",
                        CacheItemProbeTool.sha1(bytes),
                        xtea = keys[regionId],
                    )
            }
        } finally {
            library.close()
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (!apply) {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes")
    }
}
