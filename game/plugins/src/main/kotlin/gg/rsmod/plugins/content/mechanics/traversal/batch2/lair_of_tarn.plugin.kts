package gg.rsmod.plugins.content.mechanics.traversal.batch2

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.walkToTile

/**
 * Lair of Tarn Razorlor (reachable: ring of slaying / POH "Tarn's Lair" 3187,4601) - the Jump-to ledges and pillars over the pits.
 * Obstacle batch 2, owner priority #1 2026-10-03. The passageways and stairs of the lair are data: data/cfg/object-teleports/batch-2.json.
 *
 * Source: 2009scape LairOfTarnListeners.kt (no Void port of this area): a ledge / pillar is used from two tiles away
 * (setDest "Jump-to" keeps the player where he stands when he is exactly two tiles out on one axis, else walks him onto the loc), the
 * jump is one forced move onto the loc's own tile with HUMAN_JUMP 5355 (75 cycles). The hanging logs (20904, 20905) knock a player
 * who jumps onto pillar 20872 / 20879 / 20889 down one floor (5 damage, "The hanging log knocks you down to the level below.") unless
 * the floor trap beside it (20966 / 20968, Search: "You try to disarm the trap..." / "...You hear a click.") was disarmed; reaching the
 * ledge at the far end re-arms it.
 *
 * Map (ObjectPlacementProbeTool 2026-10-03): every chain is ledge - pillar(s) - ledge, two tiles apart on one axis, over pit tiles, so
 * each jump is a straight two-tile slide between two standable loc tiles - never through scenery. The pit fall is a plane change to
 * 2009scape's landing tile (no slide into a wall).
 */

val HUMAN_JUMP = 5355
val HUMAN_DISARM_TRAP = 2286
val JUMP_CYCLES = 75

val LEDGES = 20890..20901
val PILLARS = 20872..20889

val LOG_20904_DISARMED = AttributeKey<Boolean>()
val LOG_20905_DISARMED = AttributeKey<Boolean>()

/** Pillar -> (log that guards it, where the log knocks the player) - 2009scape LairOfTarnListeners. */
val LOG_TRAPS =
    mapOf(
        20879 to Pair(LOG_20904_DISARMED, Tile(3145, 4597, 0)),
        20872 to Pair(LOG_20904_DISARMED, Tile(3185, 4560, 0)),
        20889 to Pair(LOG_20905_DISARMED, Tile(3145, 4574, 1)),
    )

/** Reaching these ledges resets the log (2009scape: 20895 and 20890/20891 reset 20904, 20900/20901 reset 20905). */
val RESETS = mapOf(20895 to LOG_20904_DISARMED, 20890 to LOG_20904_DISARMED, 20891 to LOG_20904_DISARMED, 20900 to LOG_20905_DISARMED, 20901 to LOG_20905_DISARMED)

fun bindJump(id: Int) {
    val def = world.definitions.getNullable(ObjectDef::class.java, id)
    val slot = def?.options?.indexOfFirst { it?.lowercase() == "jump-to" } ?: -1
    if (slot == -1 || (slot + 1) in world.plugins.boundObjectOptions(id)) {
        logger.info("Lair of Tarn: object {} 'Jump-to' missing or already bound; skipped.", id)
        return
    }
    // The pillars stand in the pit: the click is used from two tiles away (2009scape setDest), never by walking onto them.
    on_obj_option(obj = id, option = "Jump-to", lineOfSightDistance = 2) {
        obstacle { target ->
            val dx = target.tile.x - player.tile.x
            val dz = target.tile.z - player.tile.z
            val jump = player.tile.height == target.tile.height &&
                ((dx == 0 && Math.abs(dz) == 2) || (dz == 0 && Math.abs(dx) == 2))
            if (!jump) {
                if (id in LEDGES && !player.tile.sameAs(target.tile)) {
                    // 2009scape: from anywhere else the player walks onto the ledge (normal, collision-respecting walk).
                    walkToTile(player, target.tile)
                } else {
                    player.message("I can't reach that!")
                }
                return@obstacle
            }
            val direction = Direction.between(player.tile, target.tile)
            player.faceTile(target.tile)
            player.animate(HUMAN_JUMP)
            exactMove(player, Tile(target.tile), delay = JUMP_CYCLES, direction = direction)
            RESETS[id]?.let { player.attr.remove(it) }
            val trap = LOG_TRAPS[id] ?: return@obstacle
            if (player.attr[trap.first] == true) return@obstacle
            player.moveTo(trap.second)
            player.hit(5)
            player.message("The hanging log knocks you down to the level below.")
        }
    }
}

LEDGES.forEach { bindJump(it) }
PILLARS.forEach { bindJump(it) }

mapOf(20966 to LOG_20904_DISARMED, 20968 to LOG_20905_DISARMED).forEach { (id, key) ->
    val def = world.definitions.getNullable(ObjectDef::class.java, id)
    if (def?.options?.any { it?.lowercase() == "search" } != true || 1 + def.options.indexOfFirst { it?.lowercase() == "search" } in world.plugins.boundObjectOptions(id)) {
        logger.info("Lair of Tarn: floor {} 'Search' missing or already bound; skipped.", id)
        return@forEach
    }
    on_obj_option(obj = id, option = "Search") {
        player.attr[key] = true
        player.message("You try to disarm the trap...")
        player.animate(HUMAN_DISARM_TRAP)
        player.message("...You hear a click.")
    }
}
