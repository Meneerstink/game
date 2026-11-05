package gg.rsmod.game.model.combat

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc

/** Per-life completion identity. NPC reset clears it; phase replacement carries earned Slayer XP. */
object NpcEncounter {
    private val COMPLETED = AttributeKey<Boolean>()
    private val PREVIOUS_SLAYER_XP = AttributeKey<Map<Int, Double>>()

    fun claim(npc: Npc): Boolean {
        if (!npc.combatDef.completesEncounter || npc.attr[COMPLETED] == true) return false
        npc.attr[COMPLETED] = true
        return true
    }

    fun slayerXp(npc: Npc): Double =
        ((npc.attr[PREVIOUS_SLAYER_XP] ?: emptyMap()) + (npc.id to npc.combatDef.slayerXp)).values.sum()

    fun transfer(from: Npc, to: Npc) {
        to.attr[PREVIOUS_SLAYER_XP] = if (from.combatDef.completesEncounter) {
            // A timed-out final form is replaced, not killed. Its unearned XP must not accumulate.
            from.attr[PREVIOUS_SLAYER_XP] ?: emptyMap()
        } else (from.attr[PREVIOUS_SLAYER_XP] ?: emptyMap()) + (from.id to from.combatDef.slayerXp)
    }
}
