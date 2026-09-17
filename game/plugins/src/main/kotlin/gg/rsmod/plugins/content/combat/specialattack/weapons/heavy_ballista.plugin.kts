package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.rangedShot
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * OSRS-IMPORT Heavy ballista - Concentrated Shot (OSRS Wiki "Heavy ballista", 2026-09-14): 65% special energy, 25%
 * increased accuracy and damage. Look: the imported OSRS sequences BALLISTA_SPECIAL_ATTACK (against players) /
 * BALLISTA_SPECIAL_ATTACK_PVN (against npcs), spotanim BALLISTA_SPECIAL and sound 2536 (Zenyte-lineage CONCENTRATED_SHOT_SOUND).
 */
SpecialAttacks.register(65, Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_OR) {
    player.animate(if (target is Npc) gg.rsmod.plugins.content.items.osrs.OsrsSeq.BALLISTA_SPECIAL_ATTACK_PVN else gg.rsmod.plugins.content.items.osrs.OsrsSeq.BALLISTA_SPECIAL_ATTACK)
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.BALLISTA_SPECIAL) // OSRS BALLISTA_SPECIAL (fxpilot)
    player.playSound(Sfx.POWERSHOT)
    rangedShot(player, target, accuracy = 1.25, damage = 1.25)
}
