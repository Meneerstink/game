package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.content.combat.attack.NpcAttacks

/** OSRS Wiki Barrows brother set effects on the shared npc attack model (see [BarrowsSetEffects] for the quoted rules). */
NpcAttacks.onHitRoll(BarrowsSetEffects.DHAROK) { npc, _, roll ->
    roll.maxHit = BarrowsSetEffects.dharokMaxHit(roll.maxHit, npc.getCurrentLifepoints(), npc.getMaximumLifepoints())
}

NpcAttacks.onHitRoll(BarrowsSetEffects.VERAC) { npc, target, roll ->
    if (!BarrowsSetEffects.rolls(npc.world, BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT)) return@onHitRoll
    roll.landHit = true
    if (target.isProtectedFrom(CombatClass.MELEE)) {
        roll.maxHit = minOf(roll.maxHit, BarrowsSetEffects.VERAC_PROTECTED_MAX_HIT)
    }
}

NpcAttacks.onHitDealt(BarrowsSetEffects.GUTHAN) { npc, _, roll, damage ->
    if (roll.landHit && damage > 0 && BarrowsSetEffects.rolls(npc.world, BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT)) {
        npc.setCurrentLifepoints(minOf(npc.getMaximumLifepoints(), npc.getCurrentLifepoints() + damage))
    }
}

NpcAttacks.onHitDealt(BarrowsSetEffects.TORAG) { npc, target, roll, _ ->
    if (target is Player && roll.landHit && BarrowsSetEffects.rolls(npc.world, BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT)) {
        target.runEnergy = BarrowsSetEffects.toragEnergyAfter(target.runEnergy)
        target.sendRunEnergy(target.runEnergy.toInt())
    }
}

NpcAttacks.onHitDealt(BarrowsSetEffects.KARIL) { npc, target, roll, _ ->
    if (target is Player && roll.landsIgnoringPrayer && BarrowsSetEffects.rolls(npc.world, BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT)) {
        val drain = BarrowsSetEffects.karilAgilityDrain(target.skills.getCurrentLevel(Skills.AGILITY))
        if (drain > 0) target.skills.decrementCurrentLevel(Skills.AGILITY, drain, capped = false)
    }
}

NpcAttacks.onHitDealt(BarrowsSetEffects.AHRIM) { npc, target, roll, _ ->
    if (target is Player && roll.landsIgnoringPrayer && BarrowsSetEffects.rolls(npc.world, BarrowsSetEffects.AHRIM_CHANCE_PERCENT)) {
        target.skills.decrementCurrentLevel(Skills.STRENGTH, BarrowsSetEffects.AHRIM_STRENGTH_DRAIN, capped = false)
    }
}
