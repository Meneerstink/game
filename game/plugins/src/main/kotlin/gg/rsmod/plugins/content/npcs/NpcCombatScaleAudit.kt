package gg.rsmod.plugins.content.npcs

import gg.rsmod.game.model.World

/**
 * Every [gg.rsmod.game.model.combat.NpcCombatDef.lifepoints] value in this codebase is entered as
 * the authentic real-world hitpoints total multiplied by 10, because
 * [gg.rsmod.plugins.content.combat.PawnExt.dealHit] scales every rolled hit by that same factor
 * for player and npc targets alike, and `Npc.getMaximumLifepoints()` returns `combatDef.lifepoints`
 * with no further scaling. A combat def whose lifepoints is not a positive multiple of 10 was
 * entered in the wrong unit - either a real wiki value that was never converted (as found for the
 * Ardougne Watchmen roster and the Barrows brothers), or a corrupted digit (as found for Kree'arra,
 * `2255` instead of `2250`) - and the affected npc would take roughly ten times too much or too
 * little damage per hit relative to its own health pool. Fails the boot rather than leaving this as
 * a silent balance bug a player has to notice and report.
 */
object NpcCombatScaleAudit {
    fun validate(world: World) {
        val offenders =
            world.plugins
                .allNpcCombatDefs()
                .entries
                .filter { (_, def) -> def.lifepoints <= 0 || def.lifepoints % 10 != 0 }
                .map { (npcId, def) -> "$npcId (lifepoints=${def.lifepoints})" }
        check(offenders.isEmpty()) {
            "NpcCombatDef.lifepoints must be a positive multiple of 10 (real HP * 10): ${offenders.joinToString()}"
        }
    }
}
