package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * N635 (Claude 18-11): Salarin the twisted (Yanille Agility dungeon). OSRS Wiki 'Salarin the twisted' (raw 18-11): "Salarin can only be damaged
 * by Strike spells, ring of recoil damage, venom damage (Toxic Staff of the Dead / serpentine helm), ghostly thralls, and dynamite(p). He will
 * treat Strike spells as Bolt spells ... the spells will always deal 9, 10, 11, or 12 damage, depending on the highest tier of bolt spell
 * unlocked, as long as the player does not splash. This damage is not affected by items that would normally increase spell damage ... The sole
 * exception is the Elemental amulet" (not in our game). Melee, ranged and every other spell: no damage.
 * Below 17 Magic no Bolt is unlocked: the Strike keeps its own damage (advice chosen, the wiki names no lower rule).
 * Venom ticks and thralls do not pass this combat-hit rule, so they keep working; dynamite(p) does not exist here.
 */
object SalarinTheTwisted {
    val STRIKES = setOf(CombatSpell.WIND_STRIKE, CombatSpell.WATER_STRIKE, CombatSpell.EARTH_STRIKE, CombatSpell.FIRE_STRIKE).map { it.uniqueId }.toSet()

    // The overhead 'Your pitiful attacks cannot hurt me!' (wiki trivia: 'sometimes') is left out: no source for when/how often.

    fun isSalarin(npc: Npc) = npc.id == Npcs.SALARIN_THE_TWISTED

    /**
     * The damage Salarin takes from [attacker]'s hit: [rolled] is the normal roll (0 on a splash), [landed] whether the accuracy roll hit.
     */
    fun modifyIncomingDamage(target: Npc, attacker: Pawn, type: HitType, rolled: Int, landed: Boolean, spellId: Int?): Int {
        if (!isSalarin(target)) return rolled
        if (type == HitType.REFLECTED) return rolled
        val magic = type == HitType.MAGIC || type == HitType.CRIT_MAGIC
        if (!magic || spellId == null || spellId !in STRIKES) return 0
        if (!landed) return 0
        val level = (attacker as? Player)?.skills?.getCurrentLevel(Skills.MAGIC) ?: return rolled
        if (level < 17) return rolled
        return CombatSpell.baseMaxHit(CombatSpell.WIND_BOLT, level)
    }
}
