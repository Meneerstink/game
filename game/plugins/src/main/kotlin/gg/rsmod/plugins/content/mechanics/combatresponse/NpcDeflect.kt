package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.ext.hit

/**
 * RCV-011 Q-043-c: deflect overheads on npcs. [gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.onIncomingHit]
 * only reflects for players, so an npc showing a Deflect overhead (Nex's 13449 form) protected but never reflected.
 * Novite 667 `Nex.handleIngoingHit`: a hit of the deflected style sends 10 % of its damage back to the attacker, with
 * no chance roll. The damage reduction itself is the shared protection check
 * in the combat formulas ([PrayerIcon.protects]).
 */
object NpcDeflect {
    fun reflected(
        prayerIcon: Int,
        style: CombatClass,
        damage: Int,
    ): Int {
        val icon = PrayerIcon.byId(prayerIcon) ?: return 0
        if (!icon.name.startsWith("DEFLECT_") || style !in icon.protects) return 0
        return (damage * 0.10).toInt()
    }

    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        style: CombatClass,
        damage: Int,
    ) {
        if (target !is Npc || damage <= 0) return
        val reflected = reflected(target.prayerIcon, style, damage)
        if (reflected > 0) attacker.hit(damage = reflected)
    }
}
