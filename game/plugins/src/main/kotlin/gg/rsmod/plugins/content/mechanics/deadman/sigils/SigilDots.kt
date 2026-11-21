package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.hit

/**
 * PROJECT48 SIGILS - the damage-over-time sigils (OSRS Wiki Deadman: Armageddon/Sigils): S54 the Ruthless Ranger ("a 10% chance to cripple the
 * target's movement: 15 damage over 6 seconds, drains 15 run energy, doubled against targets with no run energy") and S52 the Menacing Mage
 * ("a 10% chance to curse the target: 18 damage over 6 seconds, heals you for the same amount"). A target can only suffer one crippling effect
 * and one curse at a time. The 3-50 bracket values (10 / 12) are not modelled: the bracket of the world is not known here (dossier SIGILS).
 */
object SigilDots {
    private val CRIPPLED = AttributeKey<Boolean>()
    private val CURSED = AttributeKey<Boolean>()
    private const val CHANCE = 0.10
    private const val STEPS = 5
    private const val STEP_TICKS = 2

    /** Called for a landed hit with a ranged / magic damage type. */
    fun onLanded(
        attacker: Player,
        target: Pawn,
        type: HitType,
    ) {
        when (SigilCombat.styleOf(type)) {
            SigilCombat.Style.RANGED ->
                if (Sigils.effective(attacker, Sigil.THE_RUTHLESS_RANGER) && roll(attacker)) cripple(attacker, target)
            SigilCombat.Style.MAGIC ->
                if (Sigils.effective(attacker, Sigil.THE_MENACING_MAGE) && roll(attacker)) curse(attacker, target)
            else -> {}
        }
    }

    private fun roll(attacker: Player): Boolean = attacker.world.randomDouble() < CHANCE

    private fun cripple(
        attacker: Player,
        target: Pawn,
    ) {
        if (target.attr[CRIPPLED] == true) return
        target.attr[CRIPPLED] = true
        val noEnergy = target is Player && target.runEnergy <= 0.0
        val total = if (noEnergy) 30 else 15
        if (target is Player) target.runEnergy = Math.max(0.0, target.runEnergy - 15.0)
        run(attacker, target, total, CRIPPLED, heal = false)
    }

    private fun curse(
        attacker: Player,
        target: Pawn,
    ) {
        if (target.attr[CURSED] == true) return
        target.attr[CURSED] = true
        run(attacker, target, 18, CURSED, heal = true)
    }

    /** Deals [total] damage in [STEPS] hits (the remainder spread over the steps), one every [STEP_TICKS] ticks (10 ticks = 6 seconds in all), then clears [flag]. */
    private fun run(
        attacker: Player,
        target: Pawn,
        total: Int,
        flag: AttributeKey<Boolean>,
        heal: Boolean,
    ) {
        attacker.world.queue {
            try {
                repeat(STEPS) { i ->
                    val per = total * (i + 1) / STEPS - total * i / STEPS
                    wait(STEP_TICKS)
                    if (target.isDead() || (target is Player && !target.isOnline)) return@queue
                    target.hit(per, HitType.REGULAR_HIT, source = attacker)
                    if (heal && !attacker.isDead()) attacker.heal(per)
                }
            } finally {
                target.attr.remove(flag)
            }
        }
    }
}
