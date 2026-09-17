package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.OsmumtensFang

/**
 * OSRS-IMPORT Osmumten's fang - Eviscerate (OSRS Wiki raw wikitext + osrs-dps-calc, 2026-09-14): 25% energy, "a 50%
 * increase to accuracy for the next hit" (the fang's stab accuracy formula still applies), and the damage roll uses the
 * fang's true max hit (between 15% and 100%). Look: the imported OSRS sequence WEAPON_SWORD_OSMUMTEN03_SPECIAL, spotanim
 * SPOTANIM_WEAPON_SWORD_OSMUMTEN_SPECIAL and the fang's own sounds (OSRS Wiki sound list a_r_osmumtens_fang_sword_*).
 */
SpecialAttacks.register(OsmumtensFang.SPECIAL_ENERGY, Items.OSMUMTENS_FANG) {
    val victim = target
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.WEAPON_SWORD_OSMUMTEN03_SPECIAL)
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.OSMUMTEN_SPECIAL)
    player.playSound(gg.rsmod.plugins.content.items.osrs.OsrsSfx.OSMUMTENS_FANG_METALLIC_WOOSH)
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim)
    val (minimum, maximum) = OsmumtensFang.damageRange(maxHit, special = true)
    val landHit = MeleeCombatFormula.getAccuracy(player, victim, specialAttackMultiplier = OsmumtensFang.SPECIAL_ACCURACY) >= world.randomDouble()
    player.dealHit(
        target = victim,
        minHit = OsmumtensFang.minHitArgument(minimum),
        maxHit = maximum.toDouble(),
        landHit = landHit,
        delay = 1,
        hitType = HitType.MELEE,
    )
}
