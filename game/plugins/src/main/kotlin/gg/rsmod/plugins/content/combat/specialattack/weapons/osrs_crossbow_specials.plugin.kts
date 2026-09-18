package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.adjacentTargets
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.rangedShot
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts

/**
 * OSRS-IMPORT crossbow specials (OSRS Wiki item pages, 2026-09-13). OSRS special graphics are not portable to the 667
 * spotanim table, so these use the normal crossbow animation, sound and bolt projectile (ADAPTED_TO_667, recorded in
 * `C:\RSPS\OSRS_IMPORT_MASTER.yml`).
 */

/* Armadyl crossbow - Armadyl Eye: 50%, doubled accuracy and double the base chance of the enchanted bolt effect. */
SpecialAttacks.register(50, Items.ARMADYL_CROSSBOW) {
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.ACB_SPECIALATTACK) // OSRS ACB_SPECIALATTACK (fxpilot)
    player.playSound(Sfx.CROSSBOW)
    // OSRS Wiki "List of sound IDs": 3892 godwars_armadyl_avatar_attack is the Armadyl crossbow special's bird cry (same id in the 667 cache).
    player.playSound(3892)
    rangedShot(player, target, accuracy = 2.0, boltSpecial = EnchantedBolts.Special.ARMADYL_EYE)
}

/* Zaryte crossbow - Evoke: 75%, doubled accuracy; a successful hit guarantees the enchanted bolt effect (stronger values). */
SpecialAttacks.register(75, Items.ZARYTE_CROSSBOW) {
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.ZCB_SPECIALATTACK) // OSRS ZCB_SPECIALATTACK (fxpilot)
    player.playSound(Sfx.CROSSBOW)
    rangedShot(player, target, accuracy = 2.0, boltSpecial = EnchantedBolts.Special.ZARYTE_EVOKE)
}

/*
 * Dragon crossbow - Annihilate: 60%; the primary target takes 20% extra damage and up to 9 other targets in the 3x3 area
 * around it take 20% less (multicombat only). Enchanted bolt effects cannot activate, so no bolt special is passed.
 */
SpecialAttacks.register(60, Items.DRAGON_CROSSBOW) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    // Owner 2026-09-18 "dragon crossbow special heeft geen sound". Zenyte OSRS server SpecialAttackScript ANNIHILATE_START_SOUND /
    // ANNIHILATE_END_SOUND: 1080 DTTD_bone_crossbow_sa on the shot (replaces the plain crossbow cue) and 163 firewave_hit, the
    // explosion, when the bolt lands (OSRS Wiki "List of sound IDs" names; both ids are below the range where OSRS and 667 match).
    player.playSound(1080)
    if (rangedShot(player, victim, damage = 1.2) < 0) return@register
    val delay = 1 + Math.ceil(player.tile.getDistance(victim.tile) * 0.3).toInt()
    player.playSound(163, delay = delay * 30)
    (victim as? Player)?.playSound(163, delay = delay * 30)
    adjacentTargets(player, victim, radius = 1).take(9).forEach { other ->
        val maxHit = RangedCombatFormula.getMaxHit(player, other, specialAttackMultiplier = 0.8)
        val landHit = RangedCombatFormula.getAccuracy(player, other) >= world.randomDouble()
        player.dealHit(target = other, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType.RANGE)
    }
}
