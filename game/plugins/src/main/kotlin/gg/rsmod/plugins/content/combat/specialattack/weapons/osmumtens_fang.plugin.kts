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
    // OSRS Wiki sound list: special attack part 1 = 9367 woosh_02, part 2 = 9366 stab_01, part 3 = 9365 metallic_woosh_01
    // (File pages, 2026-09-18). Neither OSRS sequence carries frame sounds, so the three parts are server cues; the spacing
    // over the 57-client-tick sequence is ADAPTED (order sourced, offsets not published).
    player.playSound(gg.rsmod.plugins.content.items.osrs.OsrsSfx.OSMUMTENS_FANG_WOOSH_02)
    player.playSound(gg.rsmod.plugins.content.items.osrs.OsrsSfx.OSMUMTENS_FANG_STAB, delay = 19)
    player.playSound(gg.rsmod.plugins.content.items.osrs.OsrsSfx.OSMUMTENS_FANG_METALLIC_WOOSH, delay = 38)
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
