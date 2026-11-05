package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.areas.wilderness.npcs.WildernessNpcDrops

/** Each listed boss's Wiki DropsLineClue explicitly gives a Ring of wealth (i) Wilderness modifier. KBD has none. */
object WildernessBossClues {
    val NPCS = setOf(Npcs.CALLISTO_OLD, Npcs.VETION_OLD, Npcs.VENENATIS_OSRS, Npcs.SPINDEL_OSRS,
        Npcs.ARTIO, Npcs.CALVARION, Npcs.SCORPIA, Npcs.CHAOS_FANATIC, Npcs.CRAZY_ARCHAEOLOGIST, Npcs.CHAOS_ELEMENTAL)

    fun denominator(player: Player, normal: Int): Int =
        if (player.tile.getWildernessLevel() > 0) WildernessNpcDrops.clueDenominator(player, normal) else normal
}
