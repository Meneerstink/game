package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.random.Random

/** Wiki clue acquisition snapshots: the same tertiary roll for jars and barehanded loot. */
object ClueImplings {
    data class Source(val tier: ClueScrollTier, val denominator: Int)
    val sources = mapOf(
        Npcs.BABY_IMPLING to Source(ClueScrollTier.EASY, 100),
        Npcs.YOUNG_IMPLING to Source(ClueScrollTier.EASY, 50),
        Npcs.GOURMET_IMPLING to Source(ClueScrollTier.EASY, 25),
        Npcs.EARTH_IMPLING to Source(ClueScrollTier.MEDIUM, 100),
        Npcs.ESSENCE_IMPLING to Source(ClueScrollTier.MEDIUM, 50),
        Npcs.ECLECTIC_IMPLING to Source(ClueScrollTier.MEDIUM, 25),
        Npcs.NATURE_IMPLING to Source(ClueScrollTier.HARD, 100),
        Npcs.MAGPIE_IMPLING to Source(ClueScrollTier.HARD, 50),
        Npcs.NINJA_IMPLING to Source(ClueScrollTier.HARD, 25),
        Npcs.DRAGON_IMPLING to Source(ClueScrollTier.ELITE, 50),
    )

    fun roll(player: Player, npc: Int, random: Random = Random.Default): Item? {
        val source = sources[npc] ?: return null
        if (random.nextInt(source.denominator) != 0) return null
        return ClueContainers.rewardId(player, source.tier)?.let { Item(it) }
    }
}
