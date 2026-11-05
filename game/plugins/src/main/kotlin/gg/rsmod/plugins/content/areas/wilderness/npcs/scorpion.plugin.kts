package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 2.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 3024, Level 14.
set_combat_def(npc = Npcs.SCORPION_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 25
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 170
        attack = 11
        strength = 12
        defence = 11
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
        defenceStab = 5
        defenceSlash = 15
        defenceCrush = 15
        defenceMagic = 0
        defenceRanged = 5
    }
    slayer { experience = 17.0 }
    anims {
        attack = 16122
        block = 16123
        death = 16124
    }
}
ElementalWeakness.idOverrides[Npcs.SCORPION_OSRS] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 25)

val scorpionDrops = DropTableFactory.build {
    guaranteed {
    }
    table("ensouled_scorpion_head") {
        total(25)
        nothing(1) // Missing item: Ensouled scorpion head; source quantity 1, chance 1/25.
        nothing(24)
    }
    table("clue_scroll_beginner") {
        total(100)
        nothing(1) // Missing item: Clue scroll (beginner); source quantity 1, chance 1/100.
        nothing(99)
    }
}
DropTableFactory.register(scorpionDrops, Npcs.SCORPION_OSRS)

on_npc_death(Npcs.SCORPION_OSRS) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
on_npc_spawn(15089) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}
