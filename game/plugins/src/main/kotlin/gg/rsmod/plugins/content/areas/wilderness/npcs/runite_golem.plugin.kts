package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.plugins.content.combat.removeCombatTarget
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 10.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 6600, doelvariant.
set_combat_def(npc = Npcs.RUNITE_GOLEM_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 500
    }
    aggro {
        neverAggro()
    }
    stats {
        hitpoints = 1200
        attack = 140
        strength = 150
        defence = 165
        magic = 1
        ranged = 1
    }
    bonuses {
        attackBonus = 0
        strengthBonus = 0
        attackMagic = 0
        magicDamageBonus = 0
        attackRanged = 0
        rangedStrengthBonus = 0
        defenceStab = 0
        defenceSlash = 0
        defenceCrush = 0
        defenceMagic = 0
        defenceRanged = 0
    }
    anims {
        attack = 16289
        block = 16290
        death = 16291
    }
}
ElementalWeakness.idOverrides[Npcs.RUNITE_GOLEM_OSRS] = ElementalWeakness.Weakness(ElementalWeakness.Element.EARTH, 60)

val runitegolemDrops = DropTableFactory.build {
    guaranteed {
    }
}
DropTableFactory.register(runitegolemDrops, Npcs.RUNITE_GOLEM_OSRS)
// N522 lifecycle wiring

on_npc_spawn(Npcs.RUNITE_GOLEM_OSRS) {
    npc.respawns = false // Report: five-minute respawn anchor unresolved; no guessed schedule.
    npc.hitModifier = { WildernessNpcMechanics.golemArmour(it) }
}
set_combat_def(Npcs.RUNITE_ROCKS_OSRS) {
    configs { attackSpeed = 4; respawnDelay = 0; poisonImmune = true; venomImmune = true }
    aggro { neverAggro() }
    stats { hitpoints = 10 }
    anims { death = -1 } // static rocks have no animation (report section 10)
}
on_npc_spawn(Npcs.RUNITE_ROCKS_OSRS) { WildernessNpcMechanics.protectResource(npc) }
on_npc_combat(*intArrayOf(Npcs.RUNITE_ROCKS_OSRS)) {
    npc.resetFacePawn()
    npc.removeCombatTarget()
}
on_npc_death(Npcs.RUNITE_GOLEM_OSRS) { WildernessNpcMechanics.golemDied(npc) }
on_npc_option(Npcs.RUNITE_ROCKS_OSRS, "mine") {
    WildernessNpcMechanics.mine(player.getInteractingNpc(), player)
}
