package gg.rsmod.plugins.content.npcs.bulk

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import java.io.File

/**
 * Registers the bulk npc combat definition table as fallbacks (see [BulkNpcCombatDefs]).
 * Registration happens at script load so the repository can merge the table before any static
 * spawn copies its combat def; hand-written `set_combat_def` blocks always take precedence.
 */
val file = File(BulkNpcCombatDefs.DEFAULT_PATH)
if (file.exists()) {
    val result = BulkNpcCombatDefs.load(world.definitions, file)
    result.defs.forEach { (npc, def) -> set_combat_def_fallback(npc, def) }
    logger.info(
        "Bulk npc combat defs: loaded {} rows by source {} ({} rows for ids missing from the cache skipped, " +
            "{} animation ids absent from the cache dropped).",
        result.defs.size,
        result.bySource,
        result.skippedUnknownNpc,
        result.droppedAnimations,
    )
} else {
    logger.warn("Bulk npc combat defs file not found: {}", file.absolutePath)
}

/**
 * Npcs whose definition says they fight at range use the ranged strategy (ranged level, ranged
 * bonuses, 7-tile range, projectile from the def when one is sourced). Only npcs a hand-written
 * combat script does not already control are affected, since a script's `prepareAttack` sets
 * the class itself on every attack.
 */
on_global_npc_spawn {
    if (npc.combatDef.attackStyleType == StyleType.RANGED && !world.plugins.hasNpcCombatPlugin(npc.id)) {
        npc.combatClass = CombatClass.RANGED
    }
}
