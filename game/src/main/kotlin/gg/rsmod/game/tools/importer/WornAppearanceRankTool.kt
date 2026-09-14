package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef

/**
 * Read-only diagnostic that replicates the client's `PlayerEntity.initWornObjIds()` algorithm
 * (2011scape-client `PlayerEntity.java:77-93`) against a real cache: at boot the client scans
 * every item id from 0 upward and appends any id whose own definition has `manwear >= 0` (opcode
 * 23) or `womanwear >= 0` (opcode 25) into a compact `wornObjIds[]` list, in ascending-id order.
 * The server's outgoing appearance/equipment update block does not send an item id for equipped
 * items - it sends `0x8000 + ItemDef.appearanceId`
 * ([gg.rsmod.game.sync.segment.PlayerUpdateBlockSegment]), and the client treats that value as an
 * INDEX into `wornObjIds[]`, not as an item id. So a correct `appearance_id` for a newly wearable
 * item is exactly that item's 0-based position within the ascending-id list of every item with
 * opcode 23 or 25 present - never a guessed or copied value.
 *
 * PREVIOUS IMPLEMENTATION AND WHY IT WAS REPLACED (2026-09-04 WornAppearanceRankTool gate,
 * `RSPS_CURRENT_SPRINT.json`): the earlier version classified wearability with its own byte-level
 * re-walk of the raw archive ([ItemDefCodec.describeOpcodes]) - a second, parallel decoder that
 * exists only for this diagnostic and is never exercised by real server boot. This version instead
 * loads every item through the same production [DefinitionSet]/[ItemDef] path
 * [gg.rsmod.game.service.game.ItemMetadataService] uses at real server boot, so this tool's
 * wearability classification and the server's are, by construction, the exact same code - no
 * parallel re-implementation left to diverge, regardless of any subtle bug the old byte-walker may
 * have had.
 *
 * VALIDATION AND AN OPEN SOURCE_CONFLICT: an exhaustive opcode-by-opcode field-width cross-check
 * between [ItemDefCodec] and the real [ItemDef.decode] found zero divergence, and this production-
 * decoder version reproduces the exact same rank the old tool did for item 22326 (Twisted bow):
 * 5651, not the 6293 recorded as its `appearance_id` in `data/cfg/items.yml`. Before concluding the
 * algorithm itself was at fault, it was cross-checked against two low/mid-range vanilla items whose
 * recorded `appearance_id` is unrelated to any of this project's own imports and therefore trusted:
 * item 35 (Excalibur) computes rank 0, item 1127 (Rune platebody) computes rank 240 - both exact
 * matches against `items.yml`, proving the algorithm is sound against real, unmodified production
 * data. A likely explanation for Twisted bow's own mismatch was then found nearby: item 22266
 * carries a recorded `appearance_id` of 6244 (implying it was wearable when that value was
 * assigned) but currently decodes with `manwear=-1, womanwear=-1` in this cache - i.e. its worn-
 * model opcodes are gone now, which would shift every higher item's true rank downward relative to
 * whatever it was when 22266's (and, plausibly, Twisted bow's own) `appearance_id` was last
 * computed. This is recorded as an open SOURCE_CONFLICT rather than silently resolved: this tool
 * does not overwrite `items.yml`, and Twisted bow's own live `appearance_id` was deliberately left
 * unmodified this run (already-verified working state; changing it needs fresh runtime regression
 * evidence, not a static recomputation, per this run's own standing instructions).
 *
 * Usage: `./gradlew :game:runWornAppearanceRankTool --args="<cachePath> <targetItemId>"`
 *
 * Prints the rank (count of qualifying items with id < targetItemId) plus confirmation of
 * whether targetItemId itself currently qualifies (has `manwear >= 0` or `womanwear >= 0`).
 */
object WornAppearanceRankTool {
    /** The item's own worn models - `manwear >= 0 || womanwear >= 0`. */
    fun isWearable(def: ItemDef): Boolean = def.maleWornModel >= 0 || def.maleWornModel2 >= 0

    /**
     * Whether the real client appends this item to `wornObjIds[]`. The client's `ObjTypeList.list` post-processes every
     * definition before `initWornObjIds` reads it: `genLent(list(lentlink), list(lenttemplate))` and
     * `genBought(list(boughttemplate), list(boughtlink))` both copy `manwear` / `womanwear` from the original item
     * (2011scape-client `ObjType.genLent` / `genBought`). Lent (opcodes 121/122) and bought (139/140) variants of a wearable
     * item are therefore wearable in the client too. Ignoring them undercounted every rank above the first lent item (owner
     * report 2026-09-14: all imported items invisible when worn; Twisted bow, whose rank was set from the live client, works).
     */
    fun isWearableInClient(
        def: ItemDef,
        items: Map<Int, ItemDef>,
    ): Boolean {
        if (isWearable(def)) return true
        if (def.lendTemplateId > 0) items[def.lendId]?.let { if (isWearable(it)) return true }
        if (def.recolourTemplateId > 0) items[def.recolourId]?.let { if (isWearable(it)) return true }
        return false
    }

    data class Rank(
        val itemDefinitionCount: Int,
        val targetManwear: Int?,
        val targetWomanwear: Int?,
        val targetQualifies: Boolean,
        val rank: Int,
    )

    /**
     * Computes [targetId]'s `appearance_id` (its 0-based position among every lower-id item the
     * real client would consider wearable) by loading [library] through the exact same production
     * [DefinitionSet]/[ItemDef] path the server itself boots with - see the class doc for why that,
     * rather than a second hand-written decoder, is what makes this answer trustworthy.
     */
    fun rank(
        library: CacheLibrary,
        targetId: Int,
    ): Rank {
        val definitions = DefinitionSet()
        definitions.load(library, ItemDef::class.java)
        @Suppress("UNCHECKED_CAST")
        val items = definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>

        var rank = 0
        var targetQualifies = false
        var targetManwear: Int? = null
        var targetWomanwear: Int? = null
        for (id in items.keys.sorted()) {
            if (id >= targetId) {
                if (id == targetId) {
                    val def = items.getValue(id)
                    targetManwear = def.maleWornModel
                    targetWomanwear = def.maleWornModel2
                    targetQualifies = isWearableInClient(def, items)
                }
                continue
            }
            if (isWearableInClient(items.getValue(id), items)) {
                rank++
            }
        }
        return Rank(items.size, targetManwear, targetWomanwear, targetQualifies, rank)
    }

    /** Every client-wearable item id >= [fromId] mapped to its `appearance_id` rank, from one cache load. */
    fun ranksFrom(
        library: CacheLibrary,
        fromId: Int,
    ): Map<Int, Int> {
        val definitions = DefinitionSet()
        definitions.load(library, ItemDef::class.java)
        @Suppress("UNCHECKED_CAST")
        val items = definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>
        val result = linkedMapOf<Int, Int>()
        var rank = 0
        for (id in items.keys.sorted()) {
            val wearable = isWearableInClient(items.getValue(id), items)
            if (id >= fromId && wearable) result[id] = rank
            if (wearable) rank++
        }
        return result
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size == 3 && args[1] == "from") {
            val library = CacheLibrary(args[0])
            try {
                ranksFrom(library, args[2].toInt()).forEach { (id, rank) -> println("RANK $id=$rank") }
            } finally {
                library.close()
            }
            return
        }
        require(args.size == 2) { "Usage: <cachePath> <targetItemId> | <cachePath> from <firstItemId>" }
        val cachePath = args[0]
        val targetId = args[1].toInt()

        val library = CacheLibrary(cachePath)
        try {
            val result = rank(library, targetId)
            println("CACHE=$cachePath")
            println("ITEM_DEFINITION_COUNT=${result.itemDefinitionCount}")
            println("TARGET_ITEM_ID=$targetId")
            println("TARGET_MANWEAR(opcode23)=${result.targetManwear}")
            println("TARGET_WOMANWEAR(opcode25)=${result.targetWomanwear}")
            println("TARGET_QUALIFIES_AS_WEARABLE=${result.targetQualifies}")
            println("CORRECT_APPEARANCE_ID_RANK=${result.rank}")
        } finally {
            library.close()
        }
    }
}
