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
            // N522 nachtrun deel 2: Artio's / Callisto's bear trap - RuneLite gameval ObjectID CALLISTO_TRAP_LOC 47146 (Near Reality
            // Callisto.addTrapsAtCoords spawns it; its idle 10000 / snap 9999 animations are skeletal and dropped).
            "artio" to listOf(47146),
            // N522 nachtrun deel 3: Larran's small chest 33343 (closed) / 34828 (open) and Larran's big chest 34829 / 34830 (OSRS Wiki
            // infobox ids; Unlock / Check on the closed ones).
            "larran" to listOf(33343, 34828, 34829, 34830),
            // N462 ToA: every TOA_ / WARDENS / AKKHA / KEPHRI loc of RuneLite gameval ObjectID that the 'toa-map' region import did not
            // bring (not placed on the map - the raid places/swaps them: open/closed path doors, Wardens door open 46168, vault chests and
            // sarcophagi, Het mirrors/barriers/statues, Scabaras tiles/buttons, Kephri dung, Zebak water, Wardens platforms/warning tiles,
            // lobby NOOP/OP variants). Leagues/Trailblazer-reloaded/voucher/loot-tracker dummies left out. 382 ids (2026-11-06).
            "toa-locs" to
                listOf(
                    41696, 44008, 44009, 44010, 44417, 44418, 44419, 44449, 44450, 44470, 44471, 44472, 44473, 44786,
                    44787, 44788, 44789, 44790, 44791, 44792, 44793, 44825, 44826, 44934, 44935, 44938, 44939, 44996,
                    44997, 44998, 45001, 45011, 45012, 45015, 45016, 45019, 45021, 45025, 45026, 45034, 45035, 45036,
                    45037, 45042, 45044, 45045, 45048, 45049, 45050, 45051, 45052, 45053, 45054, 45056, 45057, 45058,
                    45059, 45060, 45061, 45062, 45063, 45064, 45065, 45066, 45067, 45068, 45069, 45070, 45071, 45072,
                    45073, 45074, 45075, 45076, 45077, 45078, 45079, 45080, 45081, 45082, 45083, 45084, 45086, 45088,
                    45089, 45093, 45094, 45102, 45103, 45104, 45105, 45106, 45110, 45111, 45119, 45130, 45132, 45134,
                    45136, 45137, 45138, 45145, 45146, 45161, 45162, 45163, 45168, 45169, 45186, 45188, 45189, 45194,
                    45195, 45196, 45197, 45198, 45199, 45200, 45201, 45202, 45203, 45204, 45269, 45272, 45279, 45283,
                    45289, 45290, 45312, 45313, 45336, 45338, 45339, 45340, 45341, 45342, 45344, 45345, 45346, 45347,
                    45348, 45349, 45350, 45351, 45352, 45353, 45354, 45355, 45356, 45357, 45358, 45359, 45360, 45361,
                    45362, 45363, 45364, 45365, 45366, 45367, 45368, 45369, 45370, 45371, 45372, 45373, 45374, 45384,
                    45385, 45386, 45387, 45388, 45389, 45390, 45391, 45392, 45393, 45394, 45395, 45399, 45403, 45414,
                    45421, 45426, 45429, 45430, 45434, 45436, 45442, 45443, 45444, 45455, 45456, 45457, 45458, 45459,
                    45460, 45461, 45462, 45463, 45464, 45465, 45466, 45467, 45469, 45470, 45471, 45472, 45473, 45474,
                    45475, 45476, 45477, 45478, 45479, 45480, 45481, 45482, 45483, 45484, 45485, 45486, 45487, 45488,
                    45489, 45490, 45491, 45493, 45495, 45503, 45504, 45507, 45508, 45515, 45521, 45531, 45544, 45570,
                    45571, 45572, 45573, 45574, 45575, 45576, 45577, 45578, 45580, 45581, 45602, 45604, 45606, 45607,
                    45608, 45609, 45610, 45611, 45612, 45613, 45614, 45615, 45616, 45617, 45618, 45619, 45620, 45621,
                    45622, 45623, 45624, 45625, 45626, 45637, 45638, 45729, 45733, 45739, 45740, 45741, 45742, 45743,
                    45744, 45745, 45746, 45748, 45749, 45750, 45751, 45753, 45755, 45777, 45779, 45783, 45784, 45856,
                    45868, 45869, 45870, 45871, 45981, 45982, 46016, 46017, 46038, 46060, 46061, 46062, 46063, 46064,
                    46065, 46066, 46067, 46069, 46072, 46074, 46075, 46076, 46079, 46081, 46082, 46083, 46084, 46085,
                    46086, 46088, 46090, 46093, 46096, 46097, 46098, 46099, 46101, 46102, 46137, 46156, 46157, 46159,
                    46160, 46162, 46163, 46165, 46166, 46168, 46173, 46174, 46175, 46176, 46180, 46181, 46182, 46183,
                    46184, 46185, 46186, 46187, 46188, 46189, 46196, 46229, 46230, 46231, 46238, 46239, 49568, 49569,
                    49570, 49571, 49572, 49573, 49574, 49575, 49576, 49577, 49578, 49579, 49580, 49581, 49582, 49583,
                    58525, 62519, 62520, 62521,
                ),
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
                // N462 (toa-locs 2026-11-06): the ToA boss import used up the holes below the ceiling; after the holes the census's safe range
                // above every physical, referenced and mapped model id (ModelNamespaceCensusTool.safeFreeRange) is just as proven free.
                val free = (census.provenFreeHoles.asSequence() + census.safeFreeRange.asSequence()).iterator()
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
