package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.items.osrs.NightmareStaves

/*
 * OSRS-IMPORT step 4 Nightmare staff specials (rules and sources in NightmareStaves). Looks: the imported OSRS sequence
 * NIGHTMARE_STAFF_SPECIAL with the NIGHTMARE_STAFF_VOLATILE / ELDRITCH _CAST spotanim on the caster and _HIT on the target when the
 * hit lands (Zenyte-lineage special table: cast 1760 / 1762). No runes are used.
 */

/** Rolls a Nightmare staff special hit with its own spell [base]; the autocast spell's effects are hidden meanwhile. */
fun nightmareHit(
    player: Player,
    victim: Pawn,
    base: Int,
    accuracy: Double,
    castGfx: Int,
    hitGfx: Int,
    onLanded: (Int) -> Unit = {},
) {
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.NIGHTMARE_STAFF_SPECIAL)
    player.graphic(castGfx)
    player.attr[NightmareStaves.SPECIAL_BASE_MAX_HIT] = base
    val maxHit: Double
    val landHit: Boolean
    try {
        maxHit = MagicCombatFormula.getMaxHit(player, victim)
        landHit = MagicCombatFormula.getAccuracy(player, victim, accuracy) >= world.randomDouble()
    } finally {
        player.attr.remove(NightmareStaves.SPECIAL_BASE_MAX_HIT)
    }
    val delay = MagicCombatStrategy.getHitDelay(player.getCentreTile(), victim.getCentreTile())
    val pawnHit = player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType.MAGIC)
    if (landHit) {
        val damage = pawnHit.hit.hitmarks.sumOf { it.damage }
        pawnHit.hit.addAction {
            victim.graphic(hitGfx)
            onLanded(damage)
        }
    }
}

/* Volatile Nightmare staff - Immolate: 55 %, accuracy x1.5, spell max hit min(58, trunc((99 + 58 x Magic) / 99)). */
SpecialAttacks.register(NightmareStaves.SPECIAL_ENERGY, Items.VOLATILE_NIGHTMARE_STAFF) {
    val base = NightmareStaves.immolateBase(player.skills.getCurrentLevel(Skills.MAGIC))
    nightmareHit(player, target, base, NightmareStaves.IMMOLATE_ACCURACY, gg.rsmod.plugins.content.items.osrs.OsrsGfx.NIGHTMARE_STAFF_VOLATILE_CAST, gg.rsmod.plugins.content.items.osrs.OsrsGfx.NIGHTMARE_STAFF_VOLATILE_HIT)
}

/* Eldritch Nightmare staff - Invocate: 55 %, spell max hit min(44, trunc((99 + 44 x Magic) / 99)); Prayer +50 % of the damage, up to 120. */
SpecialAttacks.register(NightmareStaves.SPECIAL_ENERGY, Items.ELDRITCH_NIGHTMARE_STAFF) {
    val base = NightmareStaves.invocateBase(player.skills.getCurrentLevel(Skills.MAGIC))
    nightmareHit(player, target, base, 1.0, gg.rsmod.plugins.content.items.osrs.OsrsGfx.NIGHTMARE_STAFF_ELDRITCH_CAST, gg.rsmod.plugins.content.items.osrs.OsrsGfx.NIGHTMARE_STAFF_ELDRITCH_HIT) { damage ->
        val cap = NightmareStaves.INVOCATE_PRAYER_CAP - player.getMaximumPrayerPoints()
        if (cap > 0) player.restorePrayer(NightmareStaves.invocatePrayer(damage), capValue = cap)
    }
}
