package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.File

/**
 * Read-only rev-667 model-id namespace/capacity census (`RSPS_CURRENT_SPRINT.json` Phase B -
 * "MODEL NAMESPACE/CAPACITY CENSUS", owner-approved unattended engineering run 2026-09-04).
 *
 * This answers, from real cache/code evidence and nothing else, the question every future
 * modern-model import (Twisted Bow was the first; more gear batches are the explicitly-planned next
 * phase) needs answered before it can safely pick a local model id: *which ids can this project
 * actually write a new model into without silently aliasing or corrupting something that already
 * exists or is already referenced?*
 *
 * Never writes anything. Every fact below is read directly from the two real runtime cache
 * directories and this project's own proven decoders - nothing here is guessed.
 *
 * ### Namespace shape
 * Rev-667 model ids are unsigned shorts everywhere this codebase's own decoders read one (
 * [gg.rsmod.game.fs.def.ItemDef]'s worn-model opcodes 23/25 use `readUnsignedShort()`), so the valid
 * range is `0..65535` inclusive (65536 total ids). One proven exception:
 * [gg.rsmod.game.fs.def.SpotAnimDef] reads its model id opcode (1) as a *signed* short - i.e. a
 * spotanim can only reference model ids `0..32767`; negative values decode as "no model". This
 * project's own append-range allocations (65517-65519, the Twisted Bow meshes) are therefore already
 * outside what a spotanim could ever legally reference, which is a fact this tool surfaces rather
 * than assumes.
 *
 * ### What "referenced" means here
 * This tool traces model references only through decoders this project has already proven correct
 * against the real client format: item worn/inventory model opcodes (1, 23, 24, 25, 26 - all
 * unsigned shorts, per [ItemDefCodec]'s field-width table, itself transcribed from
 * [gg.rsmod.game.fs.def.ItemDef.decode]) and the spotanim model opcode (1, signed short, per
 * [gg.rsmod.game.fs.def.SpotAnimDef.decode]). **NPC, object and identity-kit model references are
 * NOT traced** - this project's [gg.rsmod.game.fs.def.NpcDef] does not decode model data at all, and
 * [gg.rsmod.game.fs.def.ObjectDef] only skips past its model-id lists ([gg.rsmod.game.fs.def.ObjectDef.skipReadModelIds])
 * without retaining them; there is no local, already-proven decoder for identity kits at all. Adding
 * one would mean guessing or re-deriving an unproven nested opcode format, which this project's
 * source-and-provenance rule forbids doing blind. This is reported explicitly as
 * [Report.untracedReferenceTypes] rather than silently treated as "no references of that kind exist".
 *
 * Because of that gap, this tool draws the safe/unsafe line conservatively: only the contiguous run
 * of ids strictly ABOVE the highest id this tool can prove is physically present, referenced, or
 * mapped, in EITHER target cache, is ever reported as [Report.safeFreeRange]. An id in that trailing
 * range was never part of the original leaked cache's content at all (nothing above the proven
 * ceiling is physically present in either target), so it cannot possibly be a stale reference from
 * an untraced type either - untraced types can only reference ids that exist. Holes BELOW the
 * ceiling (ids with no physical data but inside the range where real content lives) are reported
 * separately as [Report.unverifiedHolesBelowCeiling] and are deliberately never counted as free
 * capacity, because this tool cannot rule out an NPC/object/identity-kit reference pointing at one.
 *
 * ### Usage
 * `<gameCachePath> <fileServerCachePath> [--asset-map=<path to OSRS_IMPORT_MASTER.yml>]`
 */
object ModelNamespaceCensusTool {
    const val MODEL_INDEX = ModelConvertTool.MODEL_INDEX
    const val MIN_MODEL_ID = 0
    const val MAX_MODEL_ID = 0xFFFF

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { USAGE }
        val gameCache = args[0]
        val fileServerCache = args[1]
        val assetMapPath =
            args.drop(2).firstOrNull { it.startsWith("--asset-map=") }?.substringAfter("=")
                ?: DEFAULT_ASSET_MAP_PATH

        val report = census(gameCache, fileServerCache, File(assetMapPath))
        printMachineReadable(report)
        printHumanSummary(report)
    }

    fun census(
        gameCachePath: String,
        fileServerCachePath: String,
        assetMapFile: File,
    ): Report {
        val gamePhysical = physicalModelIds(gameCachePath)
        val fileServerPhysical = physicalModelIds(fileServerCachePath)
        val physicalUnion = gamePhysical + fileServerPhysical
        val physicalDivergent = (gamePhysical union fileServerPhysical) - (gamePhysical intersect fileServerPhysical)

        // Both target caches are read independently for referenced-id evidence too, and any
        // divergence between them is reported rather than silently trusted from just one.
        val gameReferenced = referencedModelIds(gameCachePath)
        val fileServerReferenced = referencedModelIds(fileServerCachePath)
        val referencedUnion = gameReferenced.byId.keys + fileServerReferenced.byId.keys
        val referencedDivergent =
            (gameReferenced.byId.keys union fileServerReferenced.byId.keys) -
                (gameReferenced.byId.keys intersect fileServerReferenced.byId.keys)

        val mapped = mappedModelIds(assetMapFile)

        val highestPhysical = physicalUnion.maxOrNull() ?: -1
        val highestReferenced = referencedUnion.maxOrNull() ?: -1
        val highestMapped = mapped.keys.maxOrNull() ?: -1
        val provenCeiling = maxOf(highestPhysical, highestReferenced, highestMapped)

        val referencedButMissing = referencedUnion.filter { it !in physicalUnion }.sorted()
        val physicalButUnreferenced = physicalUnion.filter { it !in referencedUnion && it !in mapped.keys }
        val holesBelowCeiling =
            if (provenCeiling < MIN_MODEL_ID) {
                emptyList()
            } else {
                (MIN_MODEL_ID..provenCeiling).filter { it !in physicalUnion }
            }

        // A hole below the ceiling is provably free once every model-bearing definition type has been
        // walked and none of them (nor a durable mapping) references it.
        val provenFreeHoles = holesBelowCeiling.filter { it !in referencedUnion && it !in mapped.keys }

        val safeFreeStart = provenCeiling + 1
        val safeFreeEnd = MAX_MODEL_ID
        val safeFreeCount = if (safeFreeStart > safeFreeEnd) 0 else safeFreeEnd - safeFreeStart + 1

        return Report(
            validRangeMin = MIN_MODEL_ID,
            validRangeMax = MAX_MODEL_ID,
            gamePhysicalCount = gamePhysical.size,
            fileServerPhysicalCount = fileServerPhysical.size,
            physicalDivergentIds = physicalDivergent.sorted(),
            referencedCount = referencedUnion.size,
            referencedByRole = (gameReferenced.byRole.keys + fileServerReferenced.byRole.keys).associateWith { role ->
                (gameReferenced.byRole[role].orEmpty() + fileServerReferenced.byRole[role].orEmpty()).size
            },
            referencedDivergentIds = referencedDivergent.sorted(),
            mappedCount = mapped.size,
            mappedIds = mapped,
            highestPhysicalId = highestPhysical,
            highestReferencedId = highestReferenced,
            highestMappedId = highestMapped,
            provenCeiling = provenCeiling,
            referencedButMissing = referencedButMissing,
            physicalButUnreferencedCount = physicalButUnreferenced.size,
            unverifiedHolesBelowCeiling = holesBelowCeiling,
            provenFreeHoles = provenFreeHoles,
            safeFreeRange = if (safeFreeCount > 0) safeFreeStart..safeFreeEnd else IntRange.EMPTY,
            safeFreeCount = safeFreeCount,
            // Graphics defaults are traced above. Skybox configs carry no model field: the 667 client's
            // SkyBoxType decodes texture + sphere ids and SkyBoxSphereType only numeric parameters.
            untracedReferenceTypes = emptyList(),
        )
    }

    /**
     * Model id in the graphics defaults file (archive 28 group 3, opcode 2 `profilingModel`), walked
     * with the exact field widths of the 667 client's `GraphicsDefaults.decode`. The client downloads
     * this model during loading, so it must never be treated as a free hole.
     */
    fun graphicsDefaultsProfilingModel(library: CacheLibrary): Int? {
        // Synthetic test caches have no defaults index; every real 667 cache does.
        if (!library.exists(DEFAULTS_INDEX)) return null
        val data = library.data(DEFAULTS_INDEX, GRAPHICS_DEFAULTS_GROUP, 0) ?: return null
        val buf: ByteBuf = Unpooled.wrappedBuffer(data)
        var hitmarks = 4
        var model: Int? = null
        try {
            while (true) {
                when (val code = buf.readUnsignedByte().toInt()) {
                    0 -> break
                    1 -> buf.skipBytes(hitmarks * 4)
                    2 -> model = buf.readUnsignedShort()
                    3 -> hitmarks = buf.readUnsignedByte().toInt()
                    4, 8, 10 -> {}
                    5, 6 -> buf.skipBytes(3)
                    7 ->
                        repeat(10) {
                            repeat(4) {
                                buf.skipBytes(2)
                                buf.skipBytes(buf.readUnsignedShort() * 2)
                            }
                        }
                    9, 11 -> buf.skipBytes(1)
                    else -> throw IllegalArgumentException("Unknown graphics defaults opcode $code")
                }
            }
        } finally {
            buf.release()
        }
        return model
    }

    const val DEFAULTS_INDEX = 28
    const val GRAPHICS_DEFAULTS_GROUP = 3

    // ---- physical presence -------------------------------------------------------------------

    private fun physicalModelIds(cachePath: String): Set<Int> {
        val library = CacheLibrary(cachePath)
        try {
            return library.index(MODEL_INDEX).archiveIds().toSet()
        } finally {
            library.close()
        }
    }

    // ---- referenced ids ------------------------------------------------------------------------

    class ReferencedIds(
        val byId: Map<Int, MutableSet<String>>,
        val byRole: Map<String, MutableSet<Int>>,
    )

    private fun referencedModelIds(cachePath: String): ReferencedIds {
        val byId = mutableMapOf<Int, MutableSet<String>>()
        val byRole = mutableMapOf<String, MutableSet<Int>>()
        fun record(
            id: Int,
            role: String,
        ) {
            // Opcode-absent worn-model fields are never emitted by ItemDefCodec.describeOpcodes in
            // the first place (a TLV stream simply omits an opcode it doesn't carry), and this
            // project's own ItemDef.kt uses -1, never 0, as its "no model" sentinel default - so a
            // genuinely present opcode value of 0 is real cache data, not an absence marker, and is
            // recorded like any other id.
            byId.getOrPut(id) { mutableSetOf() }.add(role)
            byRole.getOrPut(role) { mutableSetOf() }.add(id)
        }

        val library = CacheLibrary(cachePath)
        try {
            val itemIndex = library.index(gg.rsmod.game.fs.ArchiveType.ITEM.id)
            itemIndex.archiveIds().forEach { group ->
                val archive = itemIndex.archive(group) ?: return@forEach
                archive.fileIds().forEach { file ->
                    val data = archive.file(file)?.data ?: return@forEach
                    val itemId = (group shl 8) or file
                    ItemDefCodec.describeOpcodes(data).forEach { entry ->
                        val eq = entry.indexOf('=')
                        if (eq == -1) return@forEach
                        val opcode = entry.substring(0, eq).toIntOrNull() ?: return@forEach
                        val role =
                            when (opcode) {
                                1 -> "item_inventory_model"
                                23 -> "item_male_worn_model"
                                24 -> "item_female_worn_model"
                                25 -> "item_male_worn_model_2"
                                 26 -> "item_female_worn_model_2"

                                 78 -> "item_male_worn_model_3"

                                 79 -> "item_female_worn_model_3"

                                 90 -> "item_male_head_model"

                                 91 -> "item_female_head_model"

                                 92 -> "item_male_head_model_2"

                                 93 -> "item_female_head_model_2"
                                else -> null
                            } ?: return@forEach
                        val value = entry.substring(eq + 1).toIntOrNull() ?: return@forEach
                        record(value, role)
                    }
                }
            }

            val spotanimIndex = library.index(gg.rsmod.game.fs.ArchiveType.SPOTANIM.id)
            spotanimIndex.archiveIds().forEach { group ->
                val archive = spotanimIndex.archive(group) ?: return@forEach
                archive.fileIds().forEach { file ->
                    val data = archive.file(file)?.data ?: return@forEach
                    val modelId = spotAnimModelId(data)
                    if (modelId != null && modelId >= 0) record(modelId, "spotanim_model")
                }
            }

            // Strategy B tracing: loc, npc, identity-kit and interface model references, each through a
            // walker that hard-fails on an unknown opcode (Rev667ModelReferenceWalkers / InterfaceModelReferences).
            Rev667ModelReferenceWalkers.locModelIds(library) { id, role -> record(id, role) }
            Rev667ModelReferenceWalkers.npcModelIds(library) { id, role -> record(id, role) }
            Rev667ModelReferenceWalkers.idkModelIds(library) { id, role -> record(id, role) }
            InterfaceModelReferences.collect(library) { id, role -> record(id, role) }
            graphicsDefaultsProfilingModel(library)?.let { record(it, "graphics_defaults_profiling_model") }
        } finally {
            library.close()
        }
        return ReferencedIds(byId, byRole)
    }

    /**
     * Minimal, single-field walk of a spotanim definition's opcode stream, reading only opcode 1
     * (the model id, a *signed* short) and skipping every other opcode by exactly the width
     * [gg.rsmod.game.fs.def.SpotAnimDef.decode] itself uses for that opcode, so this never has to
     * guess a field width the server's own proven decoder doesn't already establish.
     */
    private fun spotAnimModelId(data: ByteArray): Int? {
        val buf: ByteBuf = Unpooled.wrappedBuffer(data)
        var modelId: Int? = null
        try {
            while (true) {
                val opcode = buf.readUnsignedByte().toInt()
                if (opcode == 0) break
                when (opcode) {
                    1 -> modelId = buf.readShort().toInt()
                    2, 4, 5, 6 -> buf.readShort()
                    7, 8 -> buf.readByte()
                    9, 10, 11, 12, 13 -> {}
                    14 -> buf.readByte()
                    15 -> buf.readShort()
                    16 -> buf.readInt()
                    40, 41 -> {
                        val count = buf.readUnsignedByte().toInt()
                        repeat(count) {
                            buf.readShort()
                            buf.readShort()
                        }
                    }
                    else ->
                        throw IllegalArgumentException(
                            "Unknown spotanim opcode $opcode - this scanner's field-width table is " +
                                "transcribed from SpotAnimDef.decode() and must be extended there and " +
                                "here together before this cache revision can be scanned safely.",
                        )
                }
            }
        } finally {
            buf.release()
        }
        return modelId
    }

    // ---- durable mappings ----------------------------------------------------------------------

    /** local model id -> "sourceIdentity|role" describing what already-committed import claimed it. */
    private fun mappedModelIds(assetMapFile: File): Map<Int, String> {
        if (!assetMapFile.isFile) return emptyMap()
        val mapper = ObjectMapper(YAMLFactory())
        val root: JsonNode = mapper.readTree(assetMapFile) ?: return emptyMap()
        val mapped = mutableMapOf<Int, String>()
        root.path("imports").forEach { import ->
            val itemName = import.path("name").asText("?")
            import.path("models").forEach { model ->
                val localId = model.path("local_id")
                if (localId.isInt) {
                    val role = model.path("role").asText("?")
                    mapped[localId.asInt()] = "$itemName|$role"
                }
            }
        }
        return mapped
    }

    // ---- reporting ----------------------------------------------------------------------------

    class Report(
        val validRangeMin: Int,
        val validRangeMax: Int,
        val gamePhysicalCount: Int,
        val fileServerPhysicalCount: Int,
        val physicalDivergentIds: List<Int>,
        val referencedCount: Int,
        val referencedByRole: Map<String, Int>,
        val referencedDivergentIds: List<Int>,
        val mappedCount: Int,
        val mappedIds: Map<Int, String>,
        val highestPhysicalId: Int,
        val highestReferencedId: Int,
        val highestMappedId: Int,
        val provenCeiling: Int,
        val referencedButMissing: List<Int>,
        val physicalButUnreferencedCount: Int,
        val unverifiedHolesBelowCeiling: List<Int>,
        /** Holes below the ceiling that no traced definition type and no durable mapping references. */
        val provenFreeHoles: List<Int>,
        val safeFreeRange: IntRange,
        val safeFreeCount: Int,
        val untracedReferenceTypes: List<String>,
    )

    private fun printMachineReadable(r: Report) {
        println("VALID_RANGE=${r.validRangeMin}..${r.validRangeMax} (unsigned short, ${r.validRangeMax - r.validRangeMin + 1} ids)")
        println("PHYSICAL_GAME_CACHE=${r.gamePhysicalCount}")
        println("PHYSICAL_FILE_SERVER_CACHE=${r.fileServerPhysicalCount}")
        println("PHYSICAL_DIVERGENCE_COUNT=${r.physicalDivergentIds.size}")
        if (r.physicalDivergentIds.isNotEmpty()) println("PHYSICAL_DIVERGENT_IDS=${r.physicalDivergentIds.take(50)}")
        println("REFERENCED_COUNT=${r.referencedCount}")
        r.referencedByRole.toSortedMap().forEach { (role, count) -> println("  REFERENCED_BY_ROLE $role=$count") }
        println("REFERENCED_DIVERGENCE_COUNT=${r.referencedDivergentIds.size}")
        println("MAPPED_COUNT=${r.mappedCount}")
        r.mappedIds.toSortedMap().forEach { (id, desc) -> println("  MAPPED $id=$desc") }
        println("HIGHEST_PHYSICAL_ID=${r.highestPhysicalId}")
        println("HIGHEST_REFERENCED_ID=${r.highestReferencedId}")
        println("HIGHEST_MAPPED_ID=${r.highestMappedId}")
        println("PROVEN_CEILING=${r.provenCeiling}")
        println("REFERENCED_BUT_MISSING_COUNT=${r.referencedButMissing.size}")
        if (r.referencedButMissing.isNotEmpty()) println("REFERENCED_BUT_MISSING_IDS=${r.referencedButMissing.take(50)}")
        println("PHYSICAL_BUT_UNREFERENCED_COUNT=${r.physicalButUnreferencedCount}")
        println("UNVERIFIED_HOLES_BELOW_CEILING_COUNT=${r.unverifiedHolesBelowCeiling.size}")
        println("PROVEN_FREE_HOLES_COUNT=${r.provenFreeHoles.size}")
        println("PROVEN_FREE_HOLES=${r.provenFreeHoles}")
        println("SAFE_FREE_RANGE=${if (r.safeFreeRange.isEmpty()) "NONE" else "${r.safeFreeRange.first}..${r.safeFreeRange.last}"}")
        println("SAFE_FREE_COUNT=${r.safeFreeCount}")
        println("UNTRACED_REFERENCE_TYPES=${r.untracedReferenceTypes}")
    }

    private fun printHumanSummary(r: Report) {
        println()
        println("SUMMARY:")
        println(
            " Valid model-id range 0..65535 (65536 ids). Physically present: ${r.gamePhysicalCount} " +
                "(game cache), ${r.fileServerPhysicalCount} (file-server cache), " +
                "${if (r.physicalDivergentIds.isEmpty()) "no divergence" else "${r.physicalDivergentIds.size} DIVERGENT ids"}.",
        )
        println(
            " Proven ceiling (highest of physical/referenced/mapped, either cache) = ${r.provenCeiling}. " +
                "Below that ceiling, ${r.unverifiedHolesBelowCeiling.size} ids are physically absent but " +
                "NOT treated as free capacity - NPC/object/identity-kit references are not traced by this " +
                "tool (see ${r.untracedReferenceTypes}), so a hole in already-real content cannot be proven " +
                "unreferenced.",
        )
        println(
            " Only ids strictly above the proven ceiling are ever reported SAFE_FREE, because that range " +
                "was never part of the original cache's content at all: " +
                "${if (r.safeFreeRange.isEmpty()) "NONE" else "${r.safeFreeRange.first}..${r.safeFreeRange.last}"} " +
                "(${r.safeFreeCount} ids).",
        )
        if (r.referencedButMissing.isNotEmpty()) {
            println(" ANOMALY: ${r.referencedButMissing.size} item/spotanim-referenced model id(s) have no physical data in either cache.")
        }
    }

    private const val DEFAULT_ASSET_MAP_PATH = "C:\\RSPS\\OSRS_IMPORT_MASTER.yml"
    private const val USAGE =
        "Usage: <gameCachePath> <fileServerCachePath> [--asset-map=<path>]"
}
