package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.diaries.Diaries.Diary
import gg.rsmod.plugins.content.diaries.Diaries.Tier

/**
 * DIARIES-OSRS D3: claiming at the taskmaster (OSRS Wiki Achievement_Diary): a tier is claimed after all its tasks are done and all
 * lower tiers are claimed; each tier gives its wearable item and an antique lamp (2,500 / 7,500 / 15,000 / 50,000 xp in a skill of
 * at least level 30 / 40 / 50 / 70). A lost item comes back from the taskmaster - only the highest claimed one, and only when the
 * player has none of that diary's items anywhere (inventory, equipment, bank): never two.
 */
object DiaryRewards {
    /** The antique lamps of the tiers - the 667 Seers' diary lamps, set to the OSRS values in ExperienceLamps. */
    val LAMPS = intArrayOf(14633, 14634, 14635, 19764)

    /** Item names per diary (resolved to ids by name at first use: the OSRS imports get their ids from the importer). */
    private val ITEM_NAMES = mapOf(
        Diary.KANDARIN to (1..4).map { "Kandarin headgear $it" },
        Diary.WESTERN to (1..4).map { "Western banner $it" },
        Diary.WILDERNESS to (1..4).map { "Wilderness sword $it" },
        Diary.VARROCK to (1..4).map { "Varrock armour $it" },
        Diary.ARDOUGNE to (1..4).map { "Ardougne cloak $it" },
    )

    internal var ids: Map<Diary, IntArray>? = null

    fun items(world: gg.rsmod.game.model.World, diary: Diary): IntArray {
        ids?.let { return it[diary]!! }
        val defs = world.definitions
        val byName = HashMap<String, Int>()
        for (i in 0 until defs.getCount(ItemDef::class.java)) {
            val d = runCatching { defs.get(ItemDef::class.java, i) }.getOrNull() ?: continue
            if (!d.noted && d.name.isNotEmpty()) byName.putIfAbsent(d.name.lowercase(), i)
        }
        val m = ITEM_NAMES.mapValues { (_, names) -> names.map { byName[it.lowercase()] ?: -1 }.toIntArray() }
        ids = m
        return m[diary]!!
    }

    fun owns(player: Player, id: Int): Boolean =
        player.inventory.contains(id) || player.equipment.contains(id) || player.bank.contains(id)

    /** Tier a player may claim now, or null. */
    fun claimable(player: Player, diary: Diary): Tier? {
        val c = Diaries.claimed(player, diary)
        if (c >= 4) return null
        val tier = Tier.values()[c]
        return if (Diaries.tierDone(player, diary, tier)) tier else null
    }

    /** Claims one tier: item + lamp into the inventory (2 free slots needed). Returns the message for the dialogue. */
    fun claim(player: Player, diary: Diary): String {
        val tier = claimable(player, diary) ?: return "You have no rewards to claim."
        if (player.inventory.freeSlotCount < 2) return "You need two free inventory spaces to claim your reward."
        val item = items(player.world, diary)[tier.ordinal]
        Diaries.setClaimed(player, diary, tier.ordinal + 1)
        if (item >= 0) player.inventory.add(item, 1)
        player.inventory.add(LAMPS[tier.ordinal], 1)
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) { "diary-claim ${player.username} ${diary.name} ${tier.name} item=$item lamp=${LAMPS[tier.ordinal]}" }
        return "Here is your reward. You've earned it!"
    }

    /** OSRS: a lost diary item is returned for free - the highest claimed tier, only when none of them is owned. */
    fun reclaim(player: Player, diary: Diary): String {
        val c = Diaries.claimed(player, diary)
        if (c == 0) return "You haven't claimed any rewards from me yet."
        val all = items(player.world, diary)
        if (all.any { it >= 0 && owns(player, it) }) return "You already have one. I can only give you another if you lose it."
        if (player.inventory.isFull) return "You need a free inventory space for it."
        val item = all[c - 1]
        if (item < 0) return "I don't have one for you right now."
        player.inventory.add(item, 1)
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) { "diary-reclaim ${player.username} ${diary.name} item=$item" }
        return "Here you go. Try not to lose it again."
    }
}
