package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.AncientGodsword

/**
 * OSRS-IMPORT Ancient godsword - Blood Sacrifice (rules and sources in [AncientGodsword]). Look: the imported OSRS sequence
 * NGS_SPECIAL_PLAYER and spotanim NGS_SPECIAL_SPOTANIM. Sound: synth 2911, Jagex config name "blood_sacrifice" = the name of this
 * special (OSRS Wiki sound list; same id in the 667 cache). Owner live test: the generic godsword special sound 3869 was wrong.
 * The sacrifice hit uses the server's typeless hitsplat.
 */
SpecialAttacks.register(AncientGodsword.SPECIAL_ENERGY, Items.ANCIENT_GODSWORD) {
    val attacker = player
    val victim = target
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.NGS_SPECIAL_PLAYER)
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.NGS_SPECIAL)
    player.playSound(Sfx.BLOOD_SACRIFICE)
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = AncientGodsword.SPECIAL_DAMAGE)
    // Godsword specials roll against the target's slash defence (wiki DPS calculator `defenceStyle = 'slash'`).
    val landHit =
        MeleeCombatFormula.getAccuracyAgainst(
            player,
            victim,
            specialAttackMultiplier = AncientGodsword.SPECIAL_ACCURACY,
            defenceStyle = gg.rsmod.game.model.combat.StyleType.SLASH,
        ) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    if (!landHit) return@register
    attacker.world.queue {
        wait(AncientGodsword.MARK_TICKS)
        if (victim.isDead() || attacker.isDead() || (victim is Player && !victim.isOnline) || !attacker.isOnline) return@queue
        if (victim.tile.height != attacker.tile.height || victim.tile.getDistance(attacker.tile) >= AncientGodsword.ESCAPE_DISTANCE) return@queue
        val before = victim.getCurrentLifepoints()
        victim.hit(damage = AncientGodsword.SACRIFICE_DAMAGE, type = HitType.REGULAR_HIT)
        val dealt = minOf(AncientGodsword.SACRIFICE_DAMAGE, before)
        val heal = AncientGodsword.heal(victim.getMaximumLifepoints(), victim is Player, dealt)
        if (heal > 0) attacker.heal(heal)
    }
}
