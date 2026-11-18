package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player

/** The surface island's two cache map squares; the underground dungeon is explicitly excluded (Wiki Entrana). */
object ClueCasketLocation {
    fun entrana(tile: Tile): Boolean = tile.regionId in setOf(11060, 11316)
    fun entrana(player: Player): Boolean {
        val source = player.world.instanceAllocator.getMap(player.tile)?.sourceTile(player.tile) ?: player.tile
        return entrana(source)
    }
    fun permitted(player: Player, tier: ClueScrollTier): Boolean = tier != ClueScrollTier.MASTER || !entrana(player)
    fun catalogue(player: Player): ClueRewardCatalogue = if (entrana(player)) ClueRewardCatalogue.entrana else ClueRewardCatalogue.current
}
