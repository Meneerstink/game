package gg.rsmod.plugins.content.npcs

import gg.rsmod.game.model.World

/**
 * Every runtime [gg.rsmod.game.model.combat.NpcCombatDef.lifepoints] value is a positive real
 * hitpoints total. The bulk table and historical hand-written DSL still carry their sourced x10
 * values, but both convert at their runtime boundary before definitions are published. Fails the
 * boot on an invalid runtime value rather than leaving a silent health/damage mismatch.
 */
object NpcCombatScaleAudit {
    fun validate(world: World) {
        val offenders =
            world.plugins
                .allNpcCombatDefs()
                .entries
                .filter { (_, def) -> def.lifepoints <= 0 }
                .map { (npcId, def) -> "$npcId (lifepoints=${def.lifepoints})" }
        check(offenders.isEmpty()) {
            "NpcCombatDef.lifepoints must be a positive real hitpoints value: ${offenders.joinToString()}"
        }
    }
}
