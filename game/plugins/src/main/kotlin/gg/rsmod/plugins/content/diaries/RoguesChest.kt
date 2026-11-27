package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.diaries.Diaries.Diary
import gg.rsmod.plugins.content.diaries.Diaries.Tier

/**
 * DIARIES-OSRS: the Rogues' Castle chests (667 loc 14773, three in the courtyard), OSRS Wiki "Chest (Rogues' Castle)" 27-11:
 *  - "They require 84 Thieving to open. Players must use the 'Search for traps' option"; "Upon successfully unlocking a chest, the
 *    player is granted 701.7 Thieving experience"; each chest takes 34 ticks to reset; "Once a chest is opened, the rogues will all
 *    attack the player".
 *  - Loot only with the medium Wilderness diary ("Completion of the Medium Wilderness Diary allows loot to be obtained"), the hard
 *    diary table gives about 20% more (both tables below, rarity /66; hard clue 1/99 - a prayer potion(2) when the player cannot hold
 *    another hard clue, as the wiki note says). Not ported: the ring of wealth (i) 2/99 clue rate.
 * Items this cache lacks (blighted supplies, vile ashes) are resolved by name and skipped with a trace when missing.
 */
object RoguesChest {
    const val CHEST = 14773
    const val LEVEL = 84
    const val XP = 701.7
    const val RESET_TICKS = 34

    /** name, medium amount, hard amount, noted, weight (/66). */
    private class Line(val name: String, val med: Int, val hard: Int, val noted: Boolean, val weight: Int)

    private val TABLE = listOf(
        Line("Nature rune", 40, 50, false, 11),
        Line("Red spiders' eggs", 6, 7, true, 10),
        Line("Law rune", 40, 50, false, 10),
        Line("Coal", 20, 25, true, 8),
        Line("Coins", 4500, 5625, false, 6),
        Line("Vile ashes", 15, 18, true, 3),
        Line("Uncut diamond", 3, 3, true, 3),
        Line("Uncut diamond", 5, 6, true, 2),
        Line("Uncut emerald", 10, 12, true, 2),
        Line("Blighted ancient ice sack", 13, 16, false, 2),
        Line("Iron ore", 40, 50, true, 1),
        Line("Chaos rune", 60, 75, false, 1),
        Line("Death rune", 50, 62, false, 1),
        Line("Blighted manta ray", 20, 25, true, 1),
        Line("Blighted anglerfish", 15, 18, true, 1),
        Line("Uncut sapphire", 15, 18, true, 1),
        Line("Prayer potion(2)", 1, 1, false, 2),
        Line("Dragonstone", 2, 2, true, 1),
    )

    private var byName: Map<String, Int>? = null

    private fun id(player: Player, name: String, noted: Boolean): Int? {
        val defs = player.world.definitions
        val m = byName ?: HashMap<String, Int>().also { map ->
            for (i in 0 until defs.getCount(ItemDef::class.java)) {
                val d = runCatching { defs.get(ItemDef::class.java, i) }.getOrNull() ?: continue
                if (!d.noted && d.name.isNotEmpty()) map.putIfAbsent(d.name.lowercase(), i)
            }
            byName = map
        }
        val base = m[name.lowercase()] ?: return null
        if (!noted) return base
        val link = defs.get(ItemDef::class.java, base).noteLinkId
        return if (link > 0) link else base
    }

    /** The loot of one opening for this player (empty without the medium diary). */
    fun roll(player: Player, random: kotlin.random.Random = kotlin.random.Random): List<Item> {
        if (!Diaries.hasClaimed(player, Diary.WILDERNESS, Tier.MEDIUM)) return emptyList()
        val hard = Diaries.hasClaimed(player, Diary.WILDERNESS, Tier.HARD)
        val out = ArrayList<Item>()
        if (random.nextInt(99) == 0) {
            val tier = gg.rsmod.plugins.content.scrolls.ClueScrollTier.HARD
            val box = gg.rsmod.plugins.content.scrolls.ClueContainers.rewardId(player, tier)
            val room = gg.rsmod.plugins.content.scrolls.ClueContainers.count(player, tier) <
                gg.rsmod.plugins.content.scrolls.ClueContainers.capacity(player, tier)
            if (box != null && room) out += Item(box, 1) else id(player, "Prayer potion(2)", false)?.let { out += Item(it, 1) }
        }
        var r = random.nextInt(TABLE.sumOf { it.weight })
        val line = TABLE.first { r -= it.weight; r < 0 }
        val item = id(player, line.name, line.noted)
        if (item == null) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.ITEM) { "rogues chest: item '${line.name}' not in this cache - no loot this roll" }
        } else {
            out += Item(item, if (hard) line.hard else line.med)
        }
        return out
    }

    fun give(player: Player, items: List<Item>) {
        items.forEach { it ->
            val added = player.inventory.add(it.id, it.amount)
            val left = it.amount - added.completed
            if (left > 0) player.world.spawn(GroundItem(it.id, left, player.tile, player))
        }
    }

    /** "Once a chest is opened, the rogues will all attack the player." */
    fun rogues(player: Player) {
        player.world.npcs.entries.filterNotNull()
            .filter { it.def.name.equals("Rogue", ignoreCase = true) && it.tile.isWithinRadius(player.tile, 12) && !it.isDead() }
            .forEach { (it as Npc).attack(player) }
    }

    fun canOpen(player: Player): Boolean = player.skills.getCurrentLevel(Skills.THIEVING) >= LEVEL
}
