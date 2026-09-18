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
    // Owner 2026-09-18 ("still silent ... the second hit"). Jagex sound config names (gameval table, Alter-rework sound.rscm):
    // the swing plays OSRS 3869 godwars_godsword_special_attack, now imported as local 10271 - the earlier live test used 667 id
    // 3869, which is a different sound because OSRS and 667 synth ids diverge above ~3800. The delayed hit plays 2911
    // blood_sacrifice (the special's own name, same id in 667). Which OSRS special uses 3869 is not stated: ADAPTED, owner retest.
    player.playSound(gg.rsmod.plugins.content.items.osrs.OsrsSfx.GODWARS_GODSWORD_SPECIAL_ATTACK)
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
        // The Blood Sacrifice explosion: sound for both players (the attacker hears it wherever the victim is within 5 tiles).
        attacker.playSound(Sfx.BLOOD_SACRIFICE)
        if (victim is Player) victim.playSound(Sfx.BLOOD_SACRIFICE)
        victim.hit(damage = AncientGodsword.SACRIFICE_DAMAGE, type = HitType.REGULAR_HIT)
        val dealt = minOf(AncientGodsword.SACRIFICE_DAMAGE, before)
        val heal = AncientGodsword.heal(victim.getMaximumLifepoints(), victim is Player, dealt)
        if (heal > 0) attacker.heal(heal)
    }
}
