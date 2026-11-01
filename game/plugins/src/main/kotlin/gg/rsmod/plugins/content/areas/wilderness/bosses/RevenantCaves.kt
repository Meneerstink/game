package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.hasAgility

/**
 * N522 bundle 3 (owner 2026-11-01 "... ook de osrs rev cave"): the OSRS Revenant Caves, map squares 12701-12703 and 12957-12959
 * imported with OsrsRegionImportTool batch rev-caves (tx-20261101-230431; 12957 replaced rev-667 filler).
 *
 * Ways in and out - OSRS Wiki "Cavern (Revenant Caves)" (level 17 at 3073,3654 and level 40 at 3124,3831, one-way in since 2020) and
 * "Stairs (Revenant Caves)" (the exits: southern 3218,10058, northern 3244,10215). Arrival / exit tiles and the crawl (anim 2796,
 * 2 ticks, "You enter the cave." / "You exit the cave."): Kronos-184 activities/wilderness/RevCaves.java - the caverns are unchanged
 * since then; the exits became the two stairs in 2020 and come out at two surface trapdoors ([EXITS]). The level 26 "Crevice" (OSRS loc 40386, 3067,3740) is not built: no donor or wiki source says where it lands.
 *
 * Pillars - OSRS loc 31561 "Pillar" (Jump-to) at the five Kronos / OSRS map positions; Agility 65 / 75 / 89 per the OSRS Wiki
 * ("65 Agility: between the revenant goblin and the southern revenant ork", 75 west of the northern ork and the north-west
 * shortcuts, 89 between the northern ork and the eastern pyrefiend). The jump: Kronos jumpToPillar - anim 741 (HUMAN_SPOT_JUMP),
 * 2 tiles onto the pillar, a tick, anim 741 again, 2 tiles off; the refusal text is Kronos'.
 */
object RevenantCaves {
    const val CAVERN_17 = 62993 // OSRS 31555
    const val CAVERN_40 = 62994 // OSRS 31556
    const val STAIRS_SOUTH = 62975 // OSRS 31558
    const val STAIRS_NORTH = 62992 // OSRS 43868
    const val PILLAR = 62978 // OSRS 31561

    const val ANIM_CRAWL = 2796
    const val ANIM_JUMP = 741

    const val TRAPDOOR = 62995 // OSRS 31557

    /** Surface locs placed by the server, with their OSRS map rotation (OSRS squares 12345 / 12347, openrs2 2499). */
    data class Placed(val id: Int, val tile: Tile, val rot: Int)

    val SURFACE =
        listOf(
            Placed(CAVERN_17, Tile(3073, 3654), 0),
            Placed(CAVERN_40, Tile(3124, 3831), 3),
            Placed(TRAPDOOR, Tile(3103, 3655), 3),
            Placed(TRAPDOOR, Tile(3122, 3805), 1),
        )

    /** Surface cavern -> cave arrival (Kronos). */
    val ENTRANCES = mapOf(CAVERN_17 to Tile(3196, 10056), CAVERN_40 to Tile(3241, 10234))

    /**
     * Cave stairs -> surface: OSRS Wiki "Trapdoor (Revenant Caves)" - "Players will appear in level 17 Wilderness if taking the
     * southern stairs, and in level 36 Wilderness if taking the northern stairs", at the trapdoors (3103,3655 / 3122,3805). The 2x2
     * trapdoor blocks its own tiles, so the player appears on the tile south of it.
     */
    val EXITS = mapOf(STAIRS_SOUTH to Tile(3103, 3654), STAIRS_NORTH to Tile(3122, 3804))

    /** A pillar: its tile, the two take-off tiles two away on its axis, and the Agility level. */
    data class Pillar(val tile: Tile, val sideA: Tile, val sideB: Tile, val agility: Int)

    val PILLARS =
        listOf(
            Pillar(Tile(3200, 10136), Tile(3198, 10136), Tile(3202, 10136), 75), // south-west
            Pillar(Tile(3220, 10086), Tile(3220, 10084), Tile(3220, 10088), 65), // south
            Pillar(Tile(3241, 10145), Tile(3239, 10145), Tile(3243, 10145), 89), // south-east
            Pillar(Tile(3202, 10196), Tile(3200, 10196), Tile(3204, 10196), 75), // north
            Pillar(Tile(3180, 10209), Tile(3180, 10207), Tile(3180, 10211), 75), // north-west
        )

    fun pillarAt(tile: Tile): Pillar? = PILLARS.firstOrNull { it.tile.x == tile.x && it.tile.z == tile.z }

    /** The landing side for a player standing on one take-off tile; null when he stands on neither. */
    fun landing(pillar: Pillar, from: Tile): Tile? =
        when {
            from.x == pillar.sideA.x && from.z == pillar.sideA.z -> pillar.sideB
            from.x == pillar.sideB.x && from.z == pillar.sideB.z -> pillar.sideA
            else -> null
        }

    fun crawl(player: Player, dest: Tile, message: String) {
        player.lockingQueue(lockState = LockState.FULL) {
            player.animate(ANIM_CRAWL)
            wait(2)
            player.moveTo(dest)
            player.animate(-1)
            player.filterableMessage(message)
        }
    }

    fun jump(player: Player, pillar: Pillar) {
        val landing = landing(pillar, player.tile) ?: return
        if (!player.hasAgility(pillar.agility, "You need an agility level of ${pillar.agility} to jump across this pillar.")) return
        val dir = Direction.between(player.tile, landing)
        player.lockingQueue(lockState = LockState.FULL) {
            player.animate(ANIM_JUMP)
            exactMove(player, Tile(pillar.tile.x, pillar.tile.z, player.tile.height), delay = 35, direction = dir, startDelay = 5)
            wait(1)
            player.animate(ANIM_JUMP)
            exactMove(player, Tile(landing.x, landing.z, player.tile.height), delay = 35, direction = dir, startDelay = 5)
            wait(1)
        }
    }
}
