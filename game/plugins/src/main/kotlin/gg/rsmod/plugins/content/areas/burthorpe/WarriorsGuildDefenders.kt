package gg.rsmod.plugins.content.areas.burthorpe

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items

/**
 * N374 (owner 2026-10-24: "de cyclops dropte gelijk een rune defender zonder rode melding - het moet van bronze naar iron
 * etcetera gaan"): the Warriors' Guild cyclopes drop the defender after the best one the player has, OSRS Wiki "Defender":
 * "at a rate of 1/50 for bronze through rune and 1/100 for dragon"; "Multiple defenders of the same metal can be obtained by
 * having the next lowest defender while killing cyclopes", and the next one keeps dropping after the earned one was banked.
 * The bulk drop table gave every defender at random (drop-tables.json rows 4292/6079/6081, now the normal cyclops loot).
 *
 * ADAPTED: this server has no Warriors' Guild basement, so the dragon defender (OSRS: basement cyclopes after showing Lorelai a
 * rune defender) comes from the level 76 cyclopes of the top floor once the rune defender was earned.
 */
object WarriorsGuildDefenders {
    val ORDER: List<Int> =
        listOf(
            Items.BRONZE_DEFENDER, Items.IRON_DEFENDER, Items.STEEL_DEFENDER, Items.BLACK_DEFENDER, Items.MITHRIL_DEFENDER,
            Items.ADAMANT_DEFENDER, Items.RUNE_DEFENDER, Items.DRAGON_DEFENDER,
        )

    /** Index in [ORDER] of the best defender this player has earned (persisted, like OSRS's Kamfreena). */
    val EARNED = AttributeKey<Int>(persistenceKey = "wg_defender_earned")

    const val CHANCE = 50
    const val DRAGON_CHANCE = 100

    /** The cyclopes on the Warriors' Guild top floor (spawns_11319, height 2); the 76s may give the dragon defender. */
    val CYCLOPES = setOf(4291, 4292, 6078, 6079, 6080, 6081)
    val STRONG_CYCLOPES = setOf(4292, 6079, 6081)

    fun inGuild(tile: Tile): Boolean = tile.height == 2 && tile.x in 2836..2879 && tile.z in 3528..3560

    /** The best defender [player] has worn, carried, banked or earned, as an index in [ORDER] (-1: none). */
    fun best(player: Player): Int {
        val held =
            ORDER.indices.lastOrNull { i ->
                val id = ORDER[i]
                player.inventory.contains(id) || player.equipment.contains(id) || player.bank.contains(id)
            } ?: -1
        return maxOf(held, player.attr[EARNED] ?: -1)
    }

    /** The defender a kill of [cyclops] may drop for [player], or null when there is no next one here. */
    fun next(
        player: Player,
        cyclops: Int,
    ): Int? {
        val index = best(player) + 1
        if (index >= ORDER.size) return null
        if (ORDER[index] == Items.DRAGON_DEFENDER && cyclops !in STRONG_CYCLOPES) return null
        return ORDER[index]
    }

    fun chance(defender: Int): Int = if (defender == Items.DRAGON_DEFENDER) DRAGON_CHANCE else CHANCE

    /** Records [defender] as earned. */
    fun earn(
        player: Player,
        defender: Int,
    ) {
        player.attr[EARNED] = maxOf(player.attr[EARNED] ?: -1, ORDER.indexOf(defender))
    }
}
