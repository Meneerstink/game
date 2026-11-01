package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*

/**
 * N522 bundle 2: the way in and out of the OSRS Scorpion Pit (map square 12961). OSRS locs imported with OsrsRegionImportTool batch
 * scorpia-pit (tx-20261101-202403): 26762 "Cavern" -> [CAVERN] (Enter, 4x2; the surface is not imported, so the server places it) and
 * 26763 "Crevice" -> [CREVICE] (Use; part of the imported pit). Links, rotations and the crawl: Kronos-184 (dossier N522-SCORPIA-DONOR):
 * anim 2796 (RuneLite AnimationID HUMAN_LONGCRAWL, the same seq in the rev-667 cache), 2 ticks, then the move.
 */
object ScorpiaPit {
    const val CAVERN = 62957 // OSRS 26762
    const val CREVICE = 62956 // OSRS 26763
    const val ANIM_CRAWL = 2796
    const val CRAWL_TICKS = 2

    data class Link(val tile: Tile, val rot: Int, val exit: Tile)

    /** Surface caverns: the loc's tile, its rotation and the pit tile it leads to. */
    val CAVERNS =
        listOf(
            Link(Tile(3231, 3936), 2, Tile(3233, 10332)),
            Link(Tile(3231, 3951), 0, Tile(3232, 10351)),
            Link(Tile(3241, 3949), 0, Tile(3243, 10351)),
        )

    /** Pit crevices: the loc's tile and the surface tile it leads to. */
    val CREVICES =
        listOf(
            Tile(3233, 10331) to Tile(3233, 3938),
            Tile(3232, 10352) to Tile(3233, 3950),
            Tile(3243, 10352) to Tile(3242, 3948),
        )

    fun cavernExit(loc: Tile): Tile? = CAVERNS.firstOrNull { it.tile.sameAs(loc) }?.exit

    fun creviceExit(loc: Tile): Tile? = CREVICES.firstOrNull { it.first.sameAs(loc) }?.second

    fun crawl(player: Player, dest: Tile) {
        player.lockingQueue(lockState = LockState.FULL) {
            player.animate(ANIM_CRAWL)
            wait(CRAWL_TICKS)
            player.moveTo(dest)
            player.animate(-1)
        }
    }
}
