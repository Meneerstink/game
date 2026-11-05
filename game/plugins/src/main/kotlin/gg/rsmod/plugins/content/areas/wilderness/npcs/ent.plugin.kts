package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.plugins.content.combat.removeCombatTarget
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 7.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 6594, doelvariant.
set_combat_def(npc = Npcs.ENT_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 116
        poisonImmune = true
        venomImmune = true
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 1050
        attack = 80
        strength = 95
        defence = 75
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
        defenceStab = 50
        defenceSlash = 70
        defenceCrush = 70
        defenceMagic = 40
        defenceRanged = 30
    }
    slayer { experience = 107.5 }
    anims {
        attack = 16283
        block = 16281
        death = 16285
    }
}
ElementalWeakness.idOverrides[Npcs.ENT_OSRS] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 40)

val entDrops = DropTableFactory.build {
    guaranteed {
    }
}
DropTableFactory.register(entDrops, Npcs.ENT_OSRS)
// N522 lifecycle wiring
on_npc_spawn(15099) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}

on_npc_spawn(Npcs.ENT_OSRS) { npc.respawns = false }
set_combat_def(Npcs.ENT_TRUNK_OSRS) {
    configs { attackSpeed = 4; respawnDelay = 0; poisonImmune = true; venomImmune = true }
    aggro { neverAggro() }
    stats { hitpoints = 10 }
    anims { death = -1 } // noncombat resource; explicit no death sequence satisfies the combat-definition builder
}
on_npc_spawn(Npcs.ENT_TRUNK_OSRS) { WildernessNpcMechanics.protectResource(npc) }
on_npc_combat(*intArrayOf(Npcs.ENT_TRUNK_OSRS)) {
    npc.resetFacePawn()
    npc.removeCombatTarget()
}
on_npc_death(Npcs.ENT_OSRS) { WildernessNpcMechanics.entDied(npc) }
on_npc_option(Npcs.ENT_TRUNK_OSRS, "chop") {
    val trunk = player.getInteractingNpc()
    player.queue { WildernessNpcMechanics.chop(this, trunk) }
}
