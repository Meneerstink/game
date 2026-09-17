package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * Instant (no-target) special attacks: fire from the special bar click itself.
 *
 * Ported from the 2009scape Rampage/Excalibur/Clobber handlers and Matrix 718 Player.performInstantSpecial
 * (Excalibur/Enhanced Excalibur, Dragon battleaxe, Dragon hatchet/pickaxe); effects per the 2011 wiki.
 */

/* Excalibur - Sanctuary: 100%; Defence +8 (Enhanced: +15% and heals 200 life points over time). */
SpecialAttacks.registerInstant(100, Items.EXCALIBUR, Items.EXCALIBUR_8280) { p ->
    p.animate(1057)
    p.graphic(247)
    p.playSound(Sfx.SANCTUARY)
    val base = p.skills.getMaxLevel(Skills.DEFENCE)
    p.skills.alterCurrentLevel(Skills.DEFENCE, 8, capValue = 8)
    p.message("Your Defence is boosted to ${p.skills.getCurrentLevel(Skills.DEFENCE)} (base $base).")
    true
}

SpecialAttacks.registerInstant(100, Items.ENHANCED_EXCALIBUR) { p ->
    p.animate(1057)
    p.graphic(247)
    p.playSound(Sfx.SANCTUARY)
    val boost = (p.skills.getMaxLevel(Skills.DEFENCE) * 0.15).toInt()
    p.skills.alterCurrentLevel(Skills.DEFENCE, boost, capValue = boost)
    p.world.queue {
        repeat(10) {
            wait(4)
            if (p.isDead() || !p.isOnline) return@queue
            p.heal(20)
        }
    }
    true
}

/* Dragon battleaxe - Rampage: 100%; Strength +10% +10 (wiki: 10% + 10... 2011: floor(10 + 0.1 * level) then further), drains Attack/Defence/Ranged/Magic by 10%. */
SpecialAttacks.registerInstant(100, Items.DRAGON_BATTLEAXE) { p ->
    p.animate(1056)
    p.graphic(246)
    p.playSound(Sfx.RAMPAGE)
    p.forceChat("Raarrrrrgggggghhhhhhh!")
    listOf(Skills.ATTACK, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC).forEach { skill ->
        val drain = (p.skills.getCurrentLevel(skill) * 0.10).toInt()
        p.skills.setCurrentLevel(skill, (p.skills.getCurrentLevel(skill) - drain).coerceAtLeast(0))
    }
    val boost = 10 + (p.skills.getMaxLevel(Skills.STRENGTH) * 0.10).toInt()
    p.skills.alterCurrentLevel(Skills.STRENGTH, boost, capValue = boost)
    true
}

/*
 * Dragon hatchet - Clobber: 100%; Woodcutting +3 for a short time. OSRS-IMPORT: the 3rd Age axe shares the dragon axe special (OSRS Wiki
 * "Lumber Up": Woodcutting +3, 100 % energy); ADAPTED: the 667 visuals and message.
 */
SpecialAttacks.registerInstant(100, Items.DRAGON_HATCHET, Items.THIRDAGE_AXE) { p ->
    p.animate(2876)
    p.graphic(479, 96)
    p.playSound(Sfx.CLOBBER)
    p.skills.alterCurrentLevel(Skills.WOODCUTTING, 3, capValue = 3)
    p.message("Your Woodcutting level is boosted by 3.")
    true
}

/*
 * Dragon pickaxe - Rock Crusher: 100%; Mining +3 for a short time. OSRS-IMPORT: the 3rd Age pickaxe shares the dragon pickaxe special
 * (OSRS Wiki "Rock Knocker": Mining +3, 100 % energy); ADAPTED: the 667 visuals and message.
 */
SpecialAttacks.registerInstant(100, Items.DRAGON_PICKAXE, Items.DRAGON_PICKAXE_OR_UPGRADED, Items.DRAGON_PICKAXE_OR, Items.THIRDAGE_PICKAXE) { p ->
    p.animate(12031)
    p.graphic(2109)
    p.skills.alterCurrentLevel(Skills.MINING, 3, capValue = 3)
    p.message("Your Mining level is boosted by 3.")
    true
}
