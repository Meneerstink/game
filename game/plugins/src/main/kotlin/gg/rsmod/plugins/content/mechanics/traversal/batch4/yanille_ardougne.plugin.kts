package gg.rsmod.plugins.content.mechanics.traversal.batch4

import gg.rsmod.plugins.content.skills.agility.*
import gg.rsmod.game.model.queue.QueueTask
import java.util.concurrent.ThreadLocalRandom

/*
 * Yanille wall grapple and the middle piece of the East Ardougne log (obstacle batch 4, owner priority #1 2026-10-03). Both towns are
 * reachable (teleports, walking).
 *
 * Yanille wall 215 (2556,3075) / 216 (2556,3073) "Grapple": in this 667 map the plane-0 grapple locs of the Yanille south-west wall are
 * 215/216 (type 0 walls on the south edge of their tile, rotation 3); Void's yanille_grapple_wall is 17047, which this map places
 * only in an unrelated copy at 2940,4672/4675 - the existing grapple_shortcuts 17047 binding never fires in Yanille. The top of the
 * wall, Void yanille_grapple_wall_jump 17048, is placed here at exactly 2556,3073 and 2556,3075 level 1 (bound in grapple_shortcuts).
 * Behaviour: Void content/skill/agility/shortcut/Grapple.kt objectOperate("Grapple", "yanille_grapple_wall"); OSRS Wiki (Shortcuts:
 * Yanille wall, 39 Agility / 38 Strength / 21 Ranged, mithril grapple and crossbow) agrees. From the tile north of the wall edge the
 * climb lands one tile south on level 1, from the tile south of it on the wall's own tile on level 1 (Void: dest = target, or
 * target + south when facing south). grapple_wall_climb 4455 + gfx 760, grapple_shoot 2928, 11 ticks, then the level change.
 * Requirement messages are Void hasRequirements (as in grapple_shortcuts.plugin.kts).
 *
 * East Ardougne log balance 35998 "Walk-across" (2600,3336): the middle of the three log locs 35997 (2599) / 35998 / 35999 (2601). The
 * two ends are bound in sourced_shortcuts (ardougneLog, Void LogBalance.kt, OSRS Wiki level 33); the middle one gets the same crossing
 * from the end on the player's own bank: in on that end log, over the middle, two tiles on to the far bank; failure falls into the
 * river and swims downstream exactly as ardougneLog does.
 */

val YANILLE_WALLS = listOf(215, 216)
val MITHRIL_GRAPPLE = 9419
val CROSSBOWS = listOf(9174, 9176, 9177, 9179, 9181, 9183, 9185)
val ANIM_GRAPPLE_WALL_CLIMB = 4455
val GFX_GRAPPLE_WALL_CLIMB = 760
val SOUND_GRAPPLE_SHOOT = 2928

val ARDOUGNE_LOG_MIDDLE = 35998
val ANIM_FALL_OFF_LOG_LEFT = 2581
val SOUND_STUMBLE_LOOP = 2493
val SOUND_POOL_PLOP = 1658

suspend fun QueueTask.grappleRequirements(player: Player): Boolean {
    if (player.skills.getCurrentLevel(Skills.RANGED) < 21 ||
        player.skills.getCurrentLevel(Skills.AGILITY) < 39 ||
        player.skills.getCurrentLevel(Skills.STRENGTH) < 38
    ) {
        messageBox("You need at least 21 Ranged, 39 Agility and 38 Strength to do that.")
        return false
    }
    if (player.equipment[EquipmentType.AMMO.id]?.id != MITHRIL_GRAPPLE) {
        player.message("You need a mithril grapple tipped bolt with a rope to do that.")
        return false
    }
    if (player.equipment[EquipmentType.WEAPON.id]?.id !in CROSSBOWS) {
        player.message("You need a crossbow equipped to do that.")
        return false
    }
    return true
}

on_world_init {
    YANILLE_WALLS.forEach { id ->
        shortcut(id, "Grapple") {
            obstacle { target ->
                val inside = player.tile.z >= target.tile.z
                val start = if (inside) target.tile else target.tile.add(Direction.SOUTH)
                val landing = if (inside) target.tile.add(Direction.SOUTH) else target.tile
                val direction = if (inside) Direction.SOUTH else Direction.NORTH
                walkToTile(player, Tile(start.x, start.z, target.tile.height))
                face(player, direction)
                wait(1)
                if (!grappleRequirements(player)) return@obstacle
                player.animate(ANIM_GRAPPLE_WALL_CLIMB)
                player.graphic(GFX_GRAPPLE_WALL_CLIMB)
                player.playSound(SOUND_GRAPPLE_SHOOT)
                wait(11)
                player.animate(-1)
                player.moveTo(Tile(landing.x, landing.z, 1))
            }
        }
    }

    shortcut(ARDOUGNE_LOG_MIDDLE, "Walk-across") {
        if (!player.hasAgility(33)) return@shortcut
        obstacle { target ->
            val direction = if (player.tile.x < target.tile.x) Direction.EAST else Direction.WEST
            val end = target.tile.add(direction.getOpposite())
            walkToTile(player, end.add(direction.getOpposite()))
            player.filterableMessage("You attempt to walk across the slippery log.")
            walkOverTile(player, end, Render.ROPE_BALANCE)
            val middle = end.add(direction)
            walkOverTile(player, middle, Render.ROPE_BALANCE)
            if (agilitySuccess(player, 90, 250)) {
                walkOverTile(player, middle.add(direction, 2), Render.ROPE_BALANCE)
                player.message("You make it across the log without any problems.")
                player.resetRenderAnimation()
                wait(1)
                player.addXp(Skills.AGILITY, 4.0)
            } else {
                player.message("You lose your footing and fall into the water.")
                player.animate(ANIM_FALL_OFF_LOG_LEFT)
                player.playSound(SOUND_STUMBLE_LOOP)
                exactMove(player, Tile(middle.x, middle.z - 1, middle.height), delay = 35, direction = direction, startDelay = 22)
                wait(1)
                player.setRenderAnimation(Render.SWIM)
                player.playSound(SOUND_POOL_PLOP)
                player.message("You're being washed down the river.")
                walkOverTile(player, Tile(middle.x, middle.z - 1, middle.height), Render.SWIM)
                player.filterableMessage("You feel like you're drowning...")
                walkOverTile(player, Tile(middle.x, middle.z - 6, middle.height), Render.SWIM)
                walkOverTile(player, Tile(middle.x, middle.z - 7, middle.height).add(direction), Render.SWIM)
                player.filterableMessage("You finally come to shore.")
                player.resetRenderAnimation()
                walkOverTile(player, Tile(middle.x, middle.z - 7, middle.height).add(direction, 2), Render.NONE)
                player.hit(ThreadLocalRandom.current().nextInt(2, 5))
                player.addXp(Skills.AGILITY, 2.0)
            }
        }
    }
}
