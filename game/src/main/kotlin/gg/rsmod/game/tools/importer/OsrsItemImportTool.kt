package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.File
import kotlin.math.roundToInt

/**
 * Imports complete OSRS item definitions (inventory, worn and head meshes, icon camera, menu text,
 * recolours, noted variants) from the pinned OpenRS2 2686 cache into both rev-667 caches
 * (`C:\RSPS\OSRS_IMPORT_CLAUDE_PLAN_2026-09-13.md`, writer claim OSRS-IMPORT).
 *
 * Every write goes through [ImportBatchOrchestrator] -> [CacheTransaction]. Meshes are allocated from
 * a fresh [ModelNamespaceCensusTool] run (proven-free holes above 32767 first, then the append range),
 * shared meshes are written once per batch. Nothing is authored by hand except the explicitly listed
 * rev-667 client params per [Spec], each of which must name its 667 evidence in the batch table.
 *
 * Usage: `<batch> [--apply] [--yml-out=<file>]`. Without `--apply` only plan + preflight run.
 */
object OsrsItemImportTool {
    const val SOURCE_CACHE = "C:\\RSPS\\import-source\\openrs2-2686\\cache"
    val TARGETS = listOf("C:\\RSPS\\game\\game\\data\\cache", "C:\\RSPS\\file-server\\cache")
    const val ASSET_MAP = "C:\\RSPS\\RSPS_IMPORT_ASSET_MAP.yml"

    /** Rev-667 bank-note template (item 1216 = `97=1215 98=799`). */
    const val NOTE_TEMPLATE = 799

    /** Spotanim model fields are signed shorts; keep ids <= this for future GFX/projectile meshes. */
    const val SPOTANIM_MODEL_LIMIT = 32767

    data class Spec(
        val upstreamId: Int,
        /** Also import the upstream noted variant (the item must carry an upstream notedId). */
        val noted: Boolean = false,
        /** Rev-667 client params (opcode 249); see [BATCHES] for the evidence of each id. */
        val rev667Params: Map<Int, Int> = emptyMap(),
        /** Server `weapon_type` (items.yml); -1 for non-weapons. */
        val weaponType: Int = -1,
        /** Server `attack_audio` (items.yml); -1 when none. */
        val attackAudio: Int = -1,
    )

    /**
     * Rev-667 client params, evidenced from real 667 item bytes (ItemOpcodeProbe, 2026-09-13):
     * 644 = render animation (Rune sword 1289: 1381), 686 = weapon style interface (sword 5,
     * dagger 2, bow 16), 749/750 = wield requirement skill/level shown client side (Rune sword 0/40,
     * Dragon dagger 0/60, the magic-shortbow-derived Twisted bow 4/50).
     */
    val BATCHES: Map<String, List<Spec>> =
        mapOf(
            "pilot" to
                listOf(
                    Spec(12002, noted = true, rev667Params = mapOf(749 to 6, 750 to 70)), // Occult necklace
                    Spec(19720, rev667Params = mapOf(749 to 6, 750 to 70)), // Occult necklace (or)
                    Spec(20065, noted = true), // Occult ornament kit
                    Spec(22477, noted = true), // Avernic defender hilt
                    Spec(22322), // Avernic defender (two requirements: server-side only, like Dragon defender 20072)
                    Spec(22441), // Avernic defender (broken)
                    // Belle's folly: one-handed stab sword (Accurate/Lunge/Slash/Block = 667 sword style set 5).
                    // OSRS uses Ghrazi rapier animations, which do not exist in 667 -> Rune sword render anim
                    // 1381 and sword attack audio 2500 (ADAPTED_TO_667_SWORD_CLASS, recorded in the matrix).
                    Spec(31248, noted = true, rev667Params = mapOf(644 to 1381, 686 to 5, 749 to 0, 750 to 65), weaponType = 5, attackAudio = 2500),
                    Spec(31245, noted = true), // Belle's folly (tarnished)
                ),
            // Batch 2: zenyte jewellery, ornaments, Rancour, Ferocious gloves, Zaryte vambraces, Masori.
            // Client requirement params only where the item has exactly one wield requirement.
            "equipment2" to
                listOf(
                    Spec(19547, noted = true, rev667Params = mapOf(749 to 3, 750 to 75)), // Necklace of anguish
                    Spec(22246, noted = true), // Anguish ornament kit
                    Spec(22249, rev667Params = mapOf(749 to 3, 750 to 75)), // Necklace of anguish (or)
                    Spec(19553, noted = true, rev667Params = mapOf(749 to 3, 750 to 75)), // Amulet of torture
                    Spec(20062, noted = true), // Torture ornament kit
                    Spec(20366, rev667Params = mapOf(749 to 3, 750 to 75)), // Amulet of torture (or)
                    Spec(19544, noted = true, rev667Params = mapOf(749 to 3, 750 to 75)), // Tormented bracelet
                    Spec(23348, noted = true), // Tormented ornament kit
                    Spec(23444, rev667Params = mapOf(749 to 3, 750 to 75)), // Tormented bracelet (or)
                    Spec(29801, noted = true, rev667Params = mapOf(749 to 3, 750 to 90)), // Amulet of rancour
                    Spec(29804), // Amulet of rancour (s)
                    Spec(33534, noted = true), // Etched araxyte fang
                    Spec(22981), // Ferocious gloves
                    Spec(22983, noted = true), // Hydra leather
                    Spec(26235, noted = true), // Zaryte vambraces
                    Spec(19529, noted = true), // Zenyte shard
                    Spec(19496, noted = true), // Uncut zenyte
                    Spec(19493, noted = true), // Zenyte
                    Spec(27226, noted = true), // Masori mask
                    Spec(27229, noted = true), // Masori body
                    Spec(27232, noted = true), // Masori chaps
                    Spec(27235, noted = true), // Masori mask (f)
                    Spec(27238, noted = true), // Masori body (f)
                    Spec(27241, noted = true), // Masori chaps (f)
                    Spec(27269, noted = true), // Armadylean plate
                ),
            // Batch 3: Infernal cape, Mage Arena II imbued god capes, Wilderness rings and their (i), Ring of suffering.
            // Canonical ids only (no Deadman/LMS copies carrying params 59/403).
            "capesrings" to
                listOf(
                    Spec(21295), // Infernal cape
                    Spec(21287), // Infernal cape (broken)
                    Spec(24224), // Infernal cape (l)
                    Spec(21791, rev667Params = mapOf(749 to 6, 750 to 75)), // Imbued saradomin cape
                    Spec(21793, rev667Params = mapOf(749 to 6, 750 to 75)), // Imbued guthix cape
                    Spec(21795, rev667Params = mapOf(749 to 6, 750 to 75)), // Imbued zamorak cape
                    Spec(24236), // Imbued saradomin cape (broken)
                    Spec(24240), // Imbued guthix cape (broken)
                    Spec(24244), // Imbued zamorak cape (broken)
                    Spec(12601, noted = true), // Ring of the gods
                    Spec(12603, noted = true), // Tyrannical ring
                    Spec(12605, noted = true), // Treasonous ring
                    Spec(13202), // Ring of the gods (i)
                    Spec(12691), // Tyrannical ring (i)
                    Spec(12692), // Treasonous ring (i)
                    Spec(19550, noted = true, rev667Params = mapOf(749 to 3, 750 to 75)), // Ring of suffering
                    Spec(19710, rev667Params = mapOf(749 to 3, 750 to 75)), // Ring of suffering (i)
                    Spec(20655, rev667Params = mapOf(749 to 3, 750 to 75)), // Ring of suffering (r)
                    Spec(20657, rev667Params = mapOf(749 to 3, 750 to 75)), // Ring of suffering (ri)
                ),
            // Lightbearer: no stats or requirements (item page); special energy regeneration lives in SpecialEnergyRegen.
            "lightbearer" to
                listOf(
                    Spec(25975, noted = true), // Lightbearer
                ),
            // Crossbows and dragon bolts. Client params follow the 667 Rune crossbow 9185 (644 render anim 175, 686 crossbow
            // style set 17, 749/750 requirement) and 667 bolts (23/749/750 requirement); OSRS param 23 carries the same
            // requirement level upstream. Weapon type 17 and attack audio 2700 as on the Rune crossbow (ADAPTED_TO_667).
            "crossbows" to
                listOf(
                    Spec(11785, noted = true, rev667Params = crossbowParams(70), weaponType = 17, attackAudio = 2700), // Armadyl crossbow
                    Spec(26374, noted = true, rev667Params = crossbowParams(80), weaponType = 17, attackAudio = 2700), // Zaryte crossbow
                    Spec(21902, noted = true, rev667Params = crossbowParams(64), weaponType = 17, attackAudio = 2700), // Dragon crossbow
                    Spec(21921, noted = true), // Dragon crossbow (u)
                    Spec(21918, noted = true), // Dragon limbs
                    Spec(21952, noted = true), // Magic stock
                    Spec(26372, noted = true), // Nihil horn
                    Spec(26231), // Nihil shard
                    Spec(21905, rev667Params = boltParams(64)), // Dragon bolts
                    Spec(21930), // Dragon bolts (unf)
                ) +
                    (21955..21973 step 2).map { Spec(it, rev667Params = boltParams(64)) } + // gem-tipped dragon bolts
                    (21932..21950 step 2).map { Spec(it, rev667Params = boltParams(64)) }, // dragon bolts (e)
            // Heavy ballista and its javelins. The ballista follows the 667 Hand cannon 15241, the only 667 two-handed
            // crossbow-class weapon with a special (644 render anim 1603, 686 style set 17, 687 special bar, 23/749/750
            // requirement); weapon type 17 and attack audio 2700 as the imported crossbows (ADAPTED_TO_667). The OSRS
            // javelins reuse the upstream ids of the 667 thrown javelins but are imported as separate ammo items; their
            // upstream param 23 is not a wield requirement (Dragon javelin 23 = 60, wiki: none), so no client params.
            "ballista" to
                listOf(
                    Spec(19481, noted = true, rev667Params = mapOf(644 to 1603, 686 to 17, 687 to 1, 23 to 75, 749 to 4, 750 to 75), weaponType = 17, attackAudio = 2700),
                    Spec(19589, noted = true), // Heavy frame
                    Spec(19592, noted = true), // Ballista limbs
                    Spec(19601, noted = true), // Ballista spring
                    Spec(19610, noted = true), // Monkey tail
                    Spec(19584), // Javelin shaft
                ) +
                    (19570..19582 step 2).map { Spec(it) } + Spec(21352) + // javelin tips bronze..dragon, amethyst
                    (825..830).flatMap { tier -> listOf(tier, tier + 6, tier + 4817, tier + 4823) }.map { Spec(it) } + // bronze..rune (p)(p+)(p++)
                    listOf(21318, 21320, 21322, 21324, 19484, 19486, 19488, 19490).map { Spec(it) }, // amethyst, dragon
            // Toxic blowpipe. Client params follow the 667 Rune dart 811 (686 thrown style set 18, 23/749/750 requirement,
            // no render anim param) plus 687 = 1 for the special bar (Hand cannon). Weapon type 18 as the 667 darts.
            // The empty blowpipe has no Wield option upstream and gets no client params.
            "blowpipe" to
                listOf(
                    Spec(12926, rev667Params = mapOf(686 to 18, 687 to 1, 23 to 75, 749 to 4, 750 to 75), weaponType = 18), // Toxic blowpipe
                    Spec(12924, noted = true), // Toxic blowpipe (empty)
                    Spec(12922, noted = true), // Tanzanite fang
                    Spec(12934), // Zulrah's scales
                    Spec(25849, rev667Params = mapOf(686 to 18, 23 to 50, 749 to 4, 750 to 50), weaponType = 18), // Amethyst dart
                ),
            // Dragon hunter weapons, each following a 667 weapon of the same class (ItemOpcodeProbe 2026-09-14):
            // crossbow = Rune crossbow 9185 (644 175, 686 17), lance = Zamorakian spear 11716 (644 1581, 686 14; Lunge/
            // Swipe/Pound/Block as the wiki), warhammer = Rune warhammer 1347 (644 1430, 686 10, audio 2504; Pound/Pummel/
            // Block as the wiki) plus 687 special bar. Lance requirement 78 Attack: wiki item page ("now requires level 78
            // Attack"), the cache's 75 is SOURCE_CONFLICT (item page wins; items.yml corrected after generation).
            "dragonhunter" to
                listOf(
                    Spec(21012, noted = true, rev667Params = mapOf(644 to 175, 686 to 17, 23 to 70, 749 to 4, 750 to 70), weaponType = 17, attackAudio = 2700), // Dragon hunter crossbow
                    Spec(22978, noted = true, rev667Params = mapOf(644 to 1581, 686 to 14, 749 to 0, 750 to 78), weaponType = 14), // Dragon hunter lance
                    Spec(13576, noted = true, rev667Params = mapOf(644 to 1430, 686 to 10, 687 to 1, 749 to 2, 750 to 60), weaponType = 10, attackAudio = 2504), // Dragon warhammer
                ),
            // Hunters' sunlight crossbow and its antler bolts. Client params follow the 667 Hunters' crossbow 10156 (644 175,
            // 686 17, 23/749/750 requirement; weapon type 17, attack audio 2700). The client shows the 66 Ranged requirement;
            // the wiki's second requirement (50 Hunter) is server-side only in items.yml. The bolts' upstream param 23 = 50 is
            // not a wield requirement (wiki: 66 Ranged "effectively", through the crossbow), so they carry no client params.
            "sunlight" to
                listOf(
                    Spec(28869, noted = true, rev667Params = mapOf(644 to 175, 686 to 17, 23 to 66, 749 to 4, 750 to 66), weaponType = 17, attackAudio = 2700), // Hunters' sunlight crossbow
                    Spec(28872), // Sunlight antler bolts
                    Spec(28878), // Moonlight antler bolts
                    Spec(28884, noted = true), // Sunlight antler
                    Spec(28887, noted = true), // Moonlight antler
                ),
        )

    /**
     * 687 = 1 shows the special attack bar (CS2 1136 / interface 884:19, proven at the Twisted bow gate; the 667 Hand
     * cannon, which has a special, carries it) - every imported weapon with a special needs it.
     */
    private fun crossbowParams(requiredRanged: Int) = mapOf(644 to 175, 686 to 17, 687 to 1, 23 to requiredRanged, 749 to 4, 750 to requiredRanged)

    private fun boltParams(requiredRanged: Int) = mapOf(23 to requiredRanged, 749 to 4, 750 to requiredRanged)

    /** Worn in-game only when a Wear/Wield option exists; OSRS materials such as Magic stock carry a wearPos without one. */
    fun isWearable(def: ModernItemDef): Boolean = def.wearPos1 >= 0 && def.inventoryOptions.any { it == "Wear" || it == "Wield" }

    private class Entry(
        val identity: String,
        val name: String,
        val def: ModernItemDef,
        val spec: Spec,
        val notedOf: Entry? = null,
        /** A nameless OSRS count (stack-size) variant referenced by a parent's opcodes 100-109. */
        val isCount: Boolean = false,
    ) {
        var localId = -1
        var noted: Entry? = null
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val batchName = args.firstOrNull { !it.startsWith("--") } ?: error("Usage: <batch> [--apply] [--yml-out=<file>]")
        val specs = BATCHES[batchName] ?: error("Unknown batch '$batchName' (known: ${BATCHES.keys}).")
        val apply = "--apply" in args
        val ymlOut = args.firstOrNull { it.startsWith("--yml-out=") }?.substringAfter('=')
        val dropped = mutableListOf<String>()

        ModernCacheReader(File(SOURCE_CACHE)).use { reader ->
            val itemFiles = reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
            fun decodeItem(id: Int) = ModernItemDefDecoder.decode(id, itemFiles[id] ?: error("upstream item $id missing"))

            val entries = mutableListOf<Entry>()
            val countUpstreamIds = mutableSetOf<Int>()
            specs.forEach { spec ->
                val def = decodeItem(spec.upstreamId)
                // Count variants are real OSRS item definitions (their own stack meshes); they are imported as
                // nameless local items before their parent so the parent's opcodes 100-109 can point at them.
                def.countObj.filter { it != 0 && countUpstreamIds.add(it) }.forEach { countId ->
                    val countDef = decodeItem(countId)
                    entries += Entry("upstream_item:$countId", "${def.name} count $countId", countDef, Spec(countId), isCount = true)
                }
                val base = Entry("upstream_item:${spec.upstreamId}", def.name, def, spec)
                entries += base
                if (spec.noted) {
                    check(def.notedId >= 0) { "${def.name} (${spec.upstreamId}) has no upstream noted variant" }
                    val notedDef = decodeItem(def.notedId)
                    check(notedDef.notedTemplate >= 0) { "upstream ${def.notedId} is not a note of ${spec.upstreamId}" }
                    val note = Entry("upstream_item:${def.notedId}", "${def.name} (noted)", notedDef, spec, notedOf = base)
                    base.noted = note
                    entries += note
                }
            }

            val census = ModelNamespaceCensusTool.census(TARGETS[0], TARGETS[1], File(ASSET_MAP))
            check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
            check(census.untracedReferenceTypes.isEmpty()) { "untraced model reference types: ${census.untracedReferenceTypes}" }
            val candidates = census.provenFreeHoles.filter { it > SPOTANIM_MODEL_LIMIT }.sorted() + census.safeFreeRange.toList()
            println("CENSUS proven_free_holes=${census.provenFreeHoles.size} item_candidates=${candidates.size} safe_free=${census.safeFreeCount}")

            val existing = ImportBatchOrchestrator.readExistingMapping(File(ASSET_MAP))
            var next = ImportBatchOrchestrator.nextFreeItemId(TARGETS)
            entries.forEach { it.localId = existing.itemLocalIdBySourceIdentity[it.identity] ?: next++ }
            // Re-running a batch rewrites already-imported items in place: the orchestrator REPLACEs only when the live
            // cache still holds exactly the bytes read here, identical rebuilds are NO_OPs.
            val currentItemSha1 =
                CacheLibrary(TARGETS[0]).let { library ->
                    try {
                        entries.filter { existing.itemLocalIdBySourceIdentity.containsKey(it.identity) }
                            .associate { entry -> entry.identity to CacheItemProbeTool.itemData(library, entry.localId)?.let { CacheItemProbeTool.sha1(it) } }
                    } finally {
                        library.close()
                    }
                }

            val convertedModels = mutableMapOf<Int, ByteArray>()
            fun convertModel(modelId: Int): ByteArray =
                convertedModels.getOrPut(modelId) {
                    val bytes = reader.file(ModernCacheReader.INDEX_MODEL, modelId, 0) ?: error("upstream model $modelId missing")
                    val raw =
                        runCatching { ModernModelDecoder.decode(bytes) }.getOrElse { strict ->
                            // Complex (cylindrical/spherical/cube) mappings only position texture
                            // coordinates; the flattening below discards textures anyway.
                            dropped += "model $modelId: complex texture mappings discarded (${strict.message})"
                            ModernModelDecoder.decode(bytes, flattenTextures = true)
                        }
                    val texFaces = raw.faceTexture?.count { it.toInt() != -1 } ?: 0
                    val source =
                        if (texFaces == 0 && raw.texSpaceCount == 0) {
                            raw
                        } else {
                            dropped += "model $modelId: $texFaces textured faces flattened to the modern textures' average colour"
                            stripTextures(raw) { tex ->
                                val t = reader.file(ModernCacheReader.INDEX_TEXTURE, 0, tex) ?: error("modern texture $tex missing")
                                ((t[0].toInt() and 0xFF) shl 8) or (t[1].toInt() and 0xFF)
                            }
                        }
                    if (raw.droppedFaceZOffsets) dropped += "model $modelId: face z-offsets (not representable in 667)"
                    if (raw.droppedAnimayaSkinning) dropped += "model $modelId: animaya skinning (not representable in 667)"
                    val converted = Rev667ModelEncoder.encode(source)
                    val differences = ModelConvertTool.compare(source, Rev667ModelDecoder.decode(converted))
                    check(differences.isEmpty()) { "model $modelId conversion mismatch: $differences" }
                    converted
                }

            val batch =
                entries.map { entry ->
                    val base = entry.notedOf
                    val expectedSha1 = currentItemSha1[entry.identity]
                    if (base != null) {
                        ImportBatchOrchestrator.ItemSource(entry.identity, entry.name, emptyList(), expectedSha1) { _ -> notedBytes(base.localId) }
                    } else {
                        val modelIds = entry.def.modelDependencies().values.distinct()
                        val models = modelIds.map { ImportBatchOrchestrator.ModelSource("upstream_model:$it", "model", convertModel(it)) }
                        ImportBatchOrchestrator.ItemSource(entry.identity, entry.name, models, expectedSha1) { local ->
                            encodeItem(
                                entry.def,
                                { upstream -> local.getValue("upstream_model:$upstream") },
                                entry.noted?.localId,
                                entry.spec.rev667Params,
                                dropped,
                                countItem = { upstream -> entries.first { it.identity == "upstream_item:$upstream" }.localId },
                            )
                        }
                    }
                }

            val result =
                ImportBatchOrchestrator.orchestrate(
                    targets = TARGETS,
                    batch = batch,
                    assetMapFile = File(ASSET_MAP),
                    modelSafeFreeStart = census.safeFreeRange.first,
                    modelSafeFreeEnd = census.safeFreeRange.last,
                    apply = apply,
                    modelCandidates = candidates,
                ) { plan ->
                    plan.items.zip(entries).forEach { (planned, entry) ->
                        check(planned.localId == entry.localId) { "${entry.name}: planned ${planned.localId} != expected ${entry.localId}" }
                        println(
                            "PLAN item ${planned.localId} '${entry.name}' <- ${entry.identity} bytes=${planned.bytes.size} " +
                                "models=${planned.models.map { "${it.source.sourceIdentity}->${it.localId}" }}",
                        )
                        println("  OPCODES ${ItemDefCodec.describeOpcodes(planned.bytes).joinToString(" ")}")
                    }
                }
            dropped.distinct().forEach { println("DROPPED $it") }
            println("TRANSACTION ${result.transactionId} applied=${result.applyResult != null} finalized=${result.newlyFinalized.size} recovered=${result.recoveredAndFinalized.size}")

            if (apply && ymlOut != null) {
                val library = CacheLibrary(TARGETS[0])
                val ranks =
                    try {
                        entries.filter { it.notedOf == null && !it.isCount && isWearable(it.def) }
                            .associate { it.localId to WornAppearanceRankTool.rank(library, it.localId).rank }
                    } finally {
                        library.close()
                    }
                // Count variants are client-side stack visuals, not player-facing items: no items.yml metadata.
                File(ymlOut).writeText(entries.filterNot { it.isCount }.joinToString("") { itemsYml(it, ranks[it.localId]) })
                println("YML_WRITTEN $ymlOut ranks=$ranks")
            }
        }
    }

    /**
     * Item opcode 114 (contrast). OSRS stores the signed byte as-is and lights the item model with `contrast + 768`
     * (RuneLite cache `ItemLoader`: `def.contrast = stream.readByte()`; `ItemSpriteFactory`: `item.contrast + 768`). The
     * 667 client multiplies the byte by 5 before the same term (2011scape-client `ObjType.decode`: `contrast =
     * packet.g1b() * 5`; `createModel(..., this.ambient + 64, this.contrast + 768)`). The 667 byte is therefore the OSRS
     * value divided by 5, rounded to the nearest integer and kept inside a signed byte.
     */
    fun rev667Contrast(osrsContrast: Int): Int = (osrsContrast / 5.0).roundToInt().coerceIn(-128, 127)

    private fun notedBytes(baseLocalId: Int): ByteArray =
        byteArrayOf(97, (baseLocalId ushr 8).toByte(), baseLocalId.toByte(), 98, (NOTE_TEMPLATE ushr 8).toByte(), NOTE_TEMPLATE.toByte(), 0)

    /** Rev-667 ObjType stream (2011scape-client `ObjType.decode`) from a decoded OSRS definition. */
    fun encodeItem(
        def: ModernItemDef,
        model: (Int) -> Int,
        notedLocalId: Int?,
        rev667Params: Map<Int, Int>,
        dropped: MutableList<String>,
        countItem: (Int) -> Int = { error("count variant $it has no local item") },
    ): ByteArray {
        val out: ByteBuf = Unpooled.buffer()
        fun u16(code: Int, value: Int) {
            out.writeByte(code)
            out.writeShort(value and 0xFFFF)
        }
        fun str(code: Int, value: String) {
            out.writeByte(code)
            out.writeBytes(value.toByteArray(Charsets.ISO_8859_1))
            out.writeByte(0)
        }

        check(def.textureFind.isEmpty()) { "${def.name}: retexture tables use OSRS texture ids and are not mapped yet" }

        if (def.inventoryModel > 0) u16(1, model(def.inventoryModel))
        str(2, def.name)
        u16(4, def.zoom2d)
        u16(5, def.xan2d)
        u16(6, def.yan2d)
        if (def.zan2d != 0) u16(95, def.zan2d)
        u16(7, def.xOffset2d)
        u16(8, def.yOffset2d)
        when (def.stackable) {
            0 -> {}
            1 -> out.writeByte(11)
            // ModernItemDefDecoder stores OSRS opcode 160 as stackable=2. Its meaning is not established (it appears on
            // Deadman/LMS copies and on Ring of suffering (r)/(ri), which do not stack in-game), so it is not encoded.
            2 -> dropped += "${def.name}: OSRS opcode 160 (meaning unverified) not encoded"
            else -> error("${def.name}: stackable=${def.stackable} has no 667 equivalent")
        }
        out.writeByte(12)
        out.writeInt(def.cost)
        if (def.members) out.writeByte(16)
        listOf(
            23 to def.maleModel0, 24 to def.maleModel1, 25 to def.femaleModel0, 26 to def.femaleModel1,
            78 to def.maleModel2, 79 to def.femaleModel2, 90 to def.maleHeadModel, 91 to def.femaleHeadModel,
            92 to def.maleHeadModel2, 93 to def.femaleHeadModel2,
        ).filter { it.second >= 0 }.forEach { (code, id) -> u16(code, model(id)) }
        def.groundOptions.forEachIndexed { i, op -> if (op != null) str(30 + i, op) }
        def.inventoryOptions.forEachIndexed { i, op -> if (op != null) str(35 + i, op) }
        if (def.colorFind.isNotEmpty()) {
            out.writeByte(40)
            out.writeByte(def.colorFind.size)
            def.colorFind.indices.forEach {
                out.writeShort(def.colorFind[it])
                out.writeShort(def.colorReplace[it])
            }
        }
        // Opcodes 100-109 (ObjType.decode: countobj u16 + countco u16) -> local ids of the imported count variants.
        def.countObj.indices.filter { def.countObj[it] != 0 }.forEach { i ->
            out.writeByte(100 + i)
            out.writeShort(countItem(def.countObj[i]))
            out.writeShort(def.countCo[i])
        }
        if (def.geTradeable) out.writeByte(65)
        if (notedLocalId != null) u16(97, notedLocalId)
        if (def.resizeX != 128) u16(110, def.resizeX)
        if (def.resizeY != 128) u16(111, def.resizeY)
        if (def.resizeZ != 128) u16(112, def.resizeZ)
        if (def.ambient != 0) {
            out.writeByte(113)
            out.writeByte(def.ambient)
        }
        if (def.contrast != 0) {
            val contrast = rev667Contrast(def.contrast)
            if (contrast * 5 != def.contrast) dropped += "${def.name}: contrast ${def.contrast} rounded to ${contrast * 5}"
            if (contrast != 0) {
                out.writeByte(114)
                out.writeByte(contrast)
            }
        }
        if (def.team != 0) {
            out.writeByte(115)
            out.writeByte(def.team)
        }
        // OSRS translates the worn mesh by (0, offset, 0); 667 stores x/y/z offsets in quarter units.
        listOf(125 to def.maleOffset, 126 to def.femaleOffset).filter { it.second != 0 }.forEach { (code, offset) ->
            val quarters = (offset / 4.0).roundToInt()
            if (quarters * 4 != offset) dropped += "${def.name}: opcode $code y-offset $offset rounded to ${quarters * 4}"
            out.writeByte(code)
            out.writeByte(0)
            out.writeByte(quarters)
            out.writeByte(0)
        }
        if (isWearable(def)) {
            // Cursor opcodes every wearable 667 item carries (Rune sword, Amulet of fury, Dragon defender).
            out.writeByte(127)
            out.writeByte(2)
            out.writeShort(45)
            out.writeByte(129)
            out.writeByte(1)
            out.writeShort(51)
        }
        if (rev667Params.isNotEmpty()) {
            out.writeByte(249)
            out.writeByte(rev667Params.size)
            rev667Params.forEach { (id, value) ->
                out.writeByte(0)
                out.writeMedium(id)
                out.writeInt(value)
            }
        }
        out.writeByte(0)
        val bytes = ByteArray(out.readableBytes())
        out.readBytes(bytes)
        out.release()
        return bytes
    }

    private fun itemsYml(
        entry: Entry,
        appearanceId: Int?,
    ): String {
        val def = entry.def
        val sb = StringBuilder()
        sb.append("- id: ${entry.localId}\n")
        if (entry.notedOf != null) {
            sb.append("  name: \"${entry.notedOf.def.name}\"\n")
            sb.append("  examine: \"Swamp this note at any bank for the equivalent item\"\n")
            sb.append("  tradeable: ${entry.notedOf.def.tradeable}\n")
            sb.append("  weight: 0.0\n")
            sb.append("  equipment: null\n")
            return sb.toString()
        }
        fun p(id: Int) = (def.params[id] as? Int) ?: 0
        sb.append("  name: \"${def.name}\"\n")
        sb.append("  examine: \"${def.examine ?: ""}\"\n")
        sb.append("  tradeable: ${def.tradeable}\n")
        sb.append("  weight: ${def.weight / 1000.0}\n")
        if (!isWearable(def)) {
            sb.append("  equipment: null\n")
            return sb.toString()
        }
        check(p(299) % 10 == 0) { "${def.name}: magic damage ${p(299)} tenths of a percent is not a whole percent" }
        val reqs =
            listOf(434 to 436, 435 to 437).filter { def.params.containsKey(it.first) && def.params.containsKey(it.second) }
                .joinToString(", ") { "{'skill': ${p(it.first)}, 'level': ${p(it.second)}}" }
        sb.append("  equipment:\n")
        sb.append("    equip_slot: ${def.wearPos1}\n")
        // OSRS wearPos2/3 name the body parts a worn item hides: 5 = shield slot (two-handed, 667 equip_type 5 as on
        // the Twisted bow), 6 = arms, 8 = hair, 11 = jaw - the 667 remove_arms/remove_head/remove_beard flags
        // (Rune platebody: arms; Rune full helm: head + beard; Berserker helm: head).
        val hidden = setOf(def.wearPos2, def.wearPos3)
        sb.append("    equip_type: ${if (5 in hidden) 5 else -1}\n")
        sb.append("    appearance_id: ${checkNotNull(appearanceId)}\n")
        sb.append("    remove_head: ${8 in hidden}\n    remove_beard: ${11 in hidden}\n    remove_arms: ${6 in hidden}\n")
        sb.append("    weapon_type: ${entry.spec.weaponType}\n")
        sb.append("    attack_speed: ${if (def.params.containsKey(14)) p(14) else 4}\n")
        listOf("attack_stab", "attack_slash", "attack_crush", "attack_magic", "attack_ranged", "defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged")
            .forEachIndexed { i, key -> sb.append("    $key: ${p(i)}\n") }
        sb.append("    summoning: 0\n")
        sb.append("    melee_strength: ${p(10)}\n")
        sb.append("    prayer: ${p(11)}\n")
        // Gear carries ranged strength in param 189 (Twisted bow 20, Necklace of anguish 5); ammunition (wearPos 13) in
        // param 12 (Dragon bolts 122 = OSRS Wiki "Dragon bolts" +122).
        // Thrown weapons and the Toxic blowpipe carry it in param 12 on the weapon slot too (Dragon dart 35, Amethyst dart
        // 28, Toxic blowpipe 20 = OSRS Wiki item pages), so 189 wins only when present.
        val rangedStrength = if (def.params.containsKey(189)) p(189) else p(12)
        sb.append("    ranged_strength: $rangedStrength\n")
        sb.append("    magic_damage: ${p(299) / 10}\n")
        sb.append("    attack_audio: ${entry.spec.attackAudio}\n")
        if (reqs.isNotEmpty()) sb.append("    skill_reqs: [$reqs]\n")
        sb.append("    absorb_melee: 0\n    absorb_magic: 0\n    absorb_ranged: 0\n")
        return sb.toString()
    }
}
