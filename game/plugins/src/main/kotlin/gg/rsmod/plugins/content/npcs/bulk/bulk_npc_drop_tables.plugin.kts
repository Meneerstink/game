package gg.rsmod.plugins.content.npcs.bulk

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.plugins.content.drops.DropTableFactory
import java.io.File

/**
 * Registers the bulk drop tables (see [BulkNpcDropTables]) for every npc that has no hand-written
 * table, and rolls them when such an npc is killed by a player.
 *
 * Registration runs in the late world-init phase so every hand-written `DropTableFactory.register`
 * (all of which run at script load) is visible first. Rolling uses `on_npc_killed`, the only death
 * hook that is safe to add globally without displacing an npc's own `on_npc_death` handler; the
 * loot is delayed by the npc's death animation so it appears when the corpse vanishes, exactly
 * when hand-written `on_npc_death` tables drop theirs.
 */
val bulkDropIds = HashSet<Int>()

on_world_init_late {
    val file = File(BulkNpcDropTables.DEFAULT_PATH)
    if (!file.exists()) {
        logger.warn("Bulk npc drop tables file not found: {}", file.absolutePath)
        return@on_world_init_late
    }
    val result = BulkNpcDropTables.load(world.definitions, file)
    var handWritten = 0
    result.tables.forEach { (npc, table) ->
        if (DropTableFactory.hasTable(npc)) {
            handWritten++
        } else {
            DropTableFactory.register(table, npc)
            bulkDropIds += npc
        }
    }
    logger.info(
        "Bulk npc drop tables: registered {} tables ({} ids kept their hand-written table; {} rows for unknown npcs " +
            "skipped, {} unknown item refs and {} un-notable item refs dropped).",
        bulkDropIds.size,
        handWritten,
        result.skippedUnknownNpc,
        result.droppedUnknownItem,
        result.droppedUnnotable,
    )
}

on_npc_killed { killer, npc ->
    if (npc.id !in bulkDropIds) {
        return@on_npc_killed
    }
    val tile = npc.tile
    val npcId = npc.id
    val delay =
        npc.combatDef.deathAnimation
            .filter { it >= 0 }
            .sumOf { anim ->
                val def = world.definitions.get(AnimDef::class.java, anim)
                if (def.cycleLength >= 6) def.cycleLength - 4 else def.cycleLength
            } + npc.combatDef.deathDelay.coerceAtLeast(0)
    if (delay <= 0) {
        DropTableFactory.getDrop(world, killer, npcId, tile)
    } else {
        world.queue {
            wait(delay)
            DropTableFactory.getDrop(world, killer, npcId, tile)
        }
    }
}
