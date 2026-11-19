package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import java.util.concurrent.ConcurrentHashMap

/**
 * TOA-1911 punt 24: OSRS Wiki Zebak "He is notably immune to all life leech effects." One switch for every attacker-side heal that is
 * a share of the damage dealt: Amulet of blood fury, Sanguinesti staff, blood spells, Soul Split, onyx bolts (e) 'Life leech' and
 * Guthan's 'Infestation'. The hit itself (and charges/drains) stays; only the heal is skipped. Npcs register their local ids at load.
 */
object LifeLeech {
    private val immuneNpcs: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun registerImmune(npcId: Int) {
        immuneNpcs += npcId
    }

    /** True when healing from damage dealt to [target] is not allowed. */
    fun immune(target: Pawn?): Boolean = target is Npc && target.id in immuneNpcs
}
