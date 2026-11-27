package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C4, owner 2026-11-27 "alles identiek aan osrs"): the costume room's storage on the pieces a player
 * BUILDS. The owner took the old fixed costume room out on 2026-10-10 ([PohStorage.returnToBank]); the built costume room brings
 * the same six containers back ([PohStorage], interface 467 [PohStorageUi]), each piece with its own OSRS capacity.
 *
 * Capacities (OSRS Wiki "Costume room", 27-11; armour case and cape rack as the 2009scape donor `StorageOptionPlugin`, the wiki
 * gives no tier limit there):
 * - Treasure chest: oak = beginner/easy (low) rewards, teak + medium, mahogany = all levels (2009scape `Storable` TREASURE_CHEST_*).
 * - Fancy dress box: oak 2 costumes, teak 4, mahogany all. Armour case: oak 2 sets, teak 4, mahogany all.
 * - Magic wardrobe: 7 / 14 / 21 / 28 / 35 / 42 mage sets (oak .. gilded), marble all.
 * - Cape rack: oak = no skillcapes (the other capes all), teak 1 skillcape, mahogany 5, gilded 10, marble and magical all.
 * - Toy box: all.
 * Only the owner opens the storage (OSRS: visitors may only look; here the storage is the owner's own record). A piece that holds
 * items can't be removed (OSRS: "Furniture that stores items can only be removed from the room if it is empty"), nor the costume room
 * itself; an Upgrade keeps what is stored and costs the OSRS upgrade row ([upgradeCost]).
 */
object PohCostume {
    class Box(
        val family: PohStorage.Family,
        /** The most sets it holds (for the cape rack: skillcape sets), [Int.MAX_VALUE] = all. */
        val limit: Int,
        /** Treasure chest only: 1 low, 2 + medium, 3 all. */
        val tier: Int = 3,
    )

    private const val ALL = Int.MAX_VALUE

    val BOXES: Map<Int, Box> =
        mapOf(
            18804 to Box(PohStorage.Family.TREASURE_CHEST, ALL, 1),
            18806 to Box(PohStorage.Family.TREASURE_CHEST, ALL, 2),
            18808 to Box(PohStorage.Family.TREASURE_CHEST, ALL, 3),
            18772 to Box(PohStorage.Family.FANCY_DRESS, 2),
            18774 to Box(PohStorage.Family.FANCY_DRESS, 4),
            18776 to Box(PohStorage.Family.FANCY_DRESS, ALL),
            18778 to Box(PohStorage.Family.ARMOUR_CASE, 2),
            18780 to Box(PohStorage.Family.ARMOUR_CASE, 4),
            18782 to Box(PohStorage.Family.ARMOUR_CASE, ALL),
            18784 to Box(PohStorage.Family.MAGIC_WARDROBE, 7),
            18786 to Box(PohStorage.Family.MAGIC_WARDROBE, 14),
            18788 to Box(PohStorage.Family.MAGIC_WARDROBE, 21),
            18790 to Box(PohStorage.Family.MAGIC_WARDROBE, 28),
            18792 to Box(PohStorage.Family.MAGIC_WARDROBE, 35),
            18794 to Box(PohStorage.Family.MAGIC_WARDROBE, 42),
            18796 to Box(PohStorage.Family.MAGIC_WARDROBE, ALL),
            18766 to Box(PohStorage.Family.CAPE_RACK, 0),
            18767 to Box(PohStorage.Family.CAPE_RACK, 1),
            18768 to Box(PohStorage.Family.CAPE_RACK, 5),
            18769 to Box(PohStorage.Family.CAPE_RACK, 10),
            18770 to Box(PohStorage.Family.CAPE_RACK, ALL),
            18771 to Box(PohStorage.Family.CAPE_RACK, ALL),
            18798 to Box(PohStorage.Family.TOY_BOX, ALL),
            18800 to Box(PohStorage.Family.TOY_BOX, ALL),
            18802 to Box(PohStorage.Family.TOY_BOX, ALL),
        )

    /** 2009scape `Storable` TREASURE_CHEST_MED item ids (every other treasure-chest item is low level unless in [HIGH_IDS]). */
    val MED_IDS: Set<Int> =
        setOf(
            2577, 2599, 2601, 2603, 2605, 2607, 2609, 2611, 2613, 2645, 2647, 2649, 3474, 3475, 7319, 7321, 7323, 7325, 7327, 7334,
            7340, 7346, 7352, 7358, 7370, 7372, 7378, 7380, 10296, 10298, 10300, 10302, 10304, 10364, 10400, 10402, 10420, 10422,
            10436, 10438, 13097, 13103, 13107, 13109, 13111, 13113, 13115,
        )

    /** 2009scape `Storable` TREASURE_CHEST_HIGH item ids. */
    val HIGH_IDS: Set<Int> =
        setOf(
            2581, 2615, 2617, 2619, 2621, 2623, 2625, 2627, 2629, 2639, 2641, 2643, 2651, 2653, 2655, 2657, 2659, 2661, 2663, 2665,
            2667, 2669, 2671, 2673, 2675, 3476, 3477, 3478, 3479, 3480, 3481, 3483, 3485, 3486, 3488, 7336, 7342, 7348, 7354, 7360,
            7374, 7376, 7382, 7384, 7398, 7399, 7400, 10286, 10288, 10290, 10292, 10294, 10362, 10368, 10370, 10372, 10374, 10376,
            10378, 10380, 10382, 10384, 10386, 10388, 10390, 10440, 10442, 10444, 10446, 10448, 10450, 10452, 10454, 10456, 10458,
            10460, 10462, 10464, 10466, 10468, 10470, 10472, 10474, 11280, 13099, 13101,
        )

    /** 2009scape `Storable` CAPE_RACK_SKILL: the skillcapes (trimmed and hoods) and the quest/achievement-style capes counted against a rack. */
    val SKILLCAPE_IDS: Set<Int> = ((9747..9814) + listOf(9948, 9949, 9950, 12169, 12170, 12171)).toSet()

    fun handles(id: Int): Boolean = id in BOXES

    fun tierOf(set: PohStorageSets.StorageSet): Int =
        when {
            set.ids.any { it in HIGH_IDS } -> 3
            set.ids.any { it in MED_IDS } -> 2
            else -> 1
        }

    fun isSkillcape(set: PohStorageSets.StorageSet): Boolean = set.ids.any { it in SKILLCAPE_IDS }

    /** The sets [box] shows: a treasure chest only its tiers; every other piece its whole family. */
    fun sets(box: Box): List<PohStorageSets.StorageSet> =
        PohStorageSets.of(box.family).filter { box.family != PohStorage.Family.TREASURE_CHEST || tierOf(it) <= box.tier }

    /** The sets of [box]'s family that count against its limit and are stored now. */
    fun storedCount(
        player: Player,
        box: Box,
    ): Int =
        PohStorageSets.of(box.family)
            .filter { box.family != PohStorage.Family.CAPE_RACK || isSkillcape(it) }
            .count { PohStorageUi.isStored(player, it) }

    /** Null when [set] may go into [box] now, else the line the player gets. */
    fun refusal(
        player: Player,
        box: Box,
        set: PohStorageSets.StorageSet,
    ): String? {
        if (box.family == PohStorage.Family.TREASURE_CHEST && tierOf(set) > box.tier) return "This treasure chest can't hold rewards of that level."
        val counts = box.family != PohStorage.Family.CAPE_RACK || isSkillcape(set)
        if (!counts || box.limit == ALL || PohStorageUi.isStored(player, set)) return null
        if (storedCount(player, box) < box.limit) return null
        return when (box.family) {
            PohStorage.Family.CAPE_RACK -> if (box.limit == 0) "This cape rack can't hold skillcapes." else "You cannot store any more skillcapes in this cape rack."
            else -> "This ${box.family.displayName.lowercase()} is full; upgrade it to store more."
        }
    }

    fun handle(
        player: Player,
        obj: GameObject,
        option: String,
    ): Boolean {
        val box = BOXES[obj.id] ?: return false
        if (option != "open" && option != "search") return false
        if (!PlayerHouse.ownsHouseAt(player)) {
            player.message("Only the owner of this house can use that.")
            return true
        }
        PohStorageUi.open(player, box)
        return true
    }

    /** The costume-room families the player has a built piece for. */
    fun builtFamilies(player: Player): Set<PohStorage.Family> =
        PohRooms.furniture(player).values.mapNotNull { v ->
            PohFurniture.DECOS[PohRooms.decoKey(v)]?.objs?.firstOrNull()?.let { BOXES[it]?.family }
        }.toSet()

    /** True when the built piece [decoKey] is a costume-room piece whose family still holds items. */
    fun holdsItems(
        player: Player,
        decoKey: String,
    ): Boolean {
        val family = PohFurniture.DECOS[decoKey]?.objs?.firstOrNull()?.let { BOXES[it]?.family } ?: return false
        return PohStorage.stored(player, family).isNotEmpty()
    }

    fun anyStored(player: Player): Boolean = PohStorage.Family.values().any { PohStorage.stored(player, it).isNotEmpty() }

    /**
     * The OSRS upgrade row from [from] to [to] (both costume pieces): every piece here is built at the OSRS total cost, so the
     * upgrade is the difference of the two totals - materials and experience - which is exactly the wiki's "(Upgrade)" row.
     */
    fun upgradeCost(
        from: PohFurniture.Deco,
        to: PohFurniture.Deco,
    ): PohBuild.Piece? {
        if (from.objs.firstOrNull() !in BOXES || to.objs.firstOrNull() !in BOXES) return null
        val have = from.materials.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val need =
            to.materials.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                .map { (item, qty) -> item to qty - (have[item] ?: 0) }
                .filter { it.second > 0 }
        return PohBuild.Piece(to.objs.first(), to.name, to.level, (to.xp - from.xp).coerceAtLeast(0.0), need)
    }
}
