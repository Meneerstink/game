package gg.rsmod.plugins.content.mechanics.traversal.batch13

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.walkToTile

/**
 * River Lum grapple between the strong trees 241 (Lumbridge, 3246,3180) and 244 (Al Kharid, 3256,3180) - obstacle batch 13, owner
 * priority #1 2026-10-03. A plain Kotlin object so that every script binding either tree runs the same crossing: 241 is bound in
 * batch13/misthalin_kharid.plugin.kts; 244 is bound by batch12_obstacles.plugin.kts (its Karamja placements), which has to hand the
 * River Lum placement (3256,3180) to [cross] instead of the object-teleport fallback (see the batch 13 report).
 *
 * Sources: RS3 Wiki "Strong tree (Lumbridge/Al Kharid)" (ids 241 / 244, shortcut either side of the River Lum, 8 Agility / 37 Ranged /
 * 19 Strength, crossbow + mithril grapple, no experience, "takes the player from one side of the river to the other"); OSRS Wiki
 * "Broken Raft" agility box (the same 8 / 19 / 37). The 2011 map has no raft (17068 is placed nowhere, placements_667.tsv): the
 * crossing goes tree to tree over the river x 3249-3255 on row 3180 (MapProbe 2026-10-03), between the banks 3248,3180 (west, 56 tiles
 * reachable) and 3256,3180 (east, 37); the trees are not solid (blockwalk 0). Sequence = the raft-to-bank half of Void Grapple.kt
 * (lumbridge_broken_raft): face across, crossbow_accurate 4230 + grapple_shoot 2928, 3 ticks, "You successfully grapple the tree on the
 * opposite bank.", grapple_exit_water 4467 + grapple_splash 2929, the exact move to the far bank (Void 160 cycles for 6 tiles, 8 here).
 * Requirement messages: Void hasRequirements / Weapon.hasGrapple.
 */
object RiverLumGrapple {
    val WEST_BANK = Tile(3248, 3180, 0)
    val EAST_BANK = Tile(3256, 3180, 0)
    private const val MITH_GRAPPLE = 9419
    private val CROSSBOWS = listOf(9174, 9176, 9177, 9179, 9181, 9183, 9185)
    private const val ANIM_CROSSBOW_ACCURATE = 4230
    private const val ANIM_GRAPPLE_EXIT_WATER = 4467
    private const val SOUND_GRAPPLE_SHOOT = 2928
    private const val SOUND_GRAPPLE_SPLASH = 2929

    /** True on the River Lum placements of the two strong-tree ids. */
    fun isRiverLum(tile: Tile): Boolean = tile.height == 0 && tile.z == 3180 && (tile.x == 3246 || tile.x == 3256)

    /** Void hasRequirements + Weapon.hasGrapple; answers the player and returns false when he cannot grapple. */
    fun ready(player: Player): Boolean {
        if (player.skills.getCurrentLevel(Skills.RANGED) < 37 ||
            player.skills.getCurrentLevel(Skills.AGILITY) < 8 ||
            player.skills.getCurrentLevel(Skills.STRENGTH) < 19
        ) {
            player.queue { messageBox("You need at least 37 Ranged, 8 Agility and 19 Strength to do that.") }
            return false
        }
        if (player.equipment[EquipmentType.AMMO.id]?.id != MITH_GRAPPLE) {
            player.message("You need a mithril grapple tipped bolt with a rope to do that.")
            return false
        }
        if (player.equipment[EquipmentType.WEAPON.id]?.id !in CROSSBOWS) {
            player.message("You need a crossbow equipped to do that.")
            return false
        }
        return true
    }

    /** The crossing itself, inside an obstacle task: from the bank the player is on to the other bank. */
    suspend fun cross(
        task: QueueTask,
        player: Player,
    ) {
        val east = player.tile.x < 3252
        val start = if (east) WEST_BANK else EAST_BANK
        val landing = if (east) EAST_BANK else WEST_BANK
        val direction = if (east) Direction.EAST else Direction.WEST
        task.walkToTile(player, start)
        task.face(player, direction)
        task.wait(2)
        player.animate(ANIM_CROSSBOW_ACCURATE)
        player.playSound(SOUND_GRAPPLE_SHOOT)
        task.wait(3)
        player.filterableMessage("You successfully grapple the tree on the opposite bank.")
        task.wait(1)
        player.animate(ANIM_GRAPPLE_EXIT_WATER)
        player.playSound(SOUND_GRAPPLE_SPLASH)
        task.exactMove(player, landing, delay = 213, direction = direction)
    }
}
