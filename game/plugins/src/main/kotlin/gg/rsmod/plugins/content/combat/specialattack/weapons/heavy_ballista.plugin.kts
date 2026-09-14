package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.rangedShot
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * OSRS-IMPORT Heavy ballista - Concentrated Shot (OSRS Wiki "Heavy ballista", 2026-09-14): 65% special energy, 25%
 * increased accuracy and damage. The OSRS ballista animation and graphics are not in the 667 tables, so the shot uses the
 * normal crossbow animation, sound and javelin projectile (ADAPTED_TO_667, recorded in `C:\RSPS\OSRS_IMPORT_STATUS.md`).
 */
SpecialAttacks.register(65, Items.HEAVY_BALLISTA) {
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.BALLISTA_SPECIAL) // OSRS BALLISTA_SPECIAL (fxpilot)
    player.playSound(Sfx.CROSSBOW)
    rangedShot(player, target, accuracy = 1.25, damage = 1.25)
}
