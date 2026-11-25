package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** OSRS-NPC-VERVANGEN: native 667 identity/animations, existing N522 stats/loot.
 * N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 3.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 3027, doelvariant.
set_combat_def(npc = Npcs.KING_SCORPION) {
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
        hitpoints = 300
        attack = 30
        strength = 29
        defence = 23
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
    slayer { assignment = SlayerAssignment.SCORPION; experience = 30.0 }
    anims {
        attack = Anims.SCORPION_ATTACK
        block = Anims.SCORPION_BLOCK
        death = Anims.SCORPION_DEATH
    }
}
ElementalWeakness.idOverrides[Npcs.KING_SCORPION] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 20)

val kingscorpionDrops = DropTableFactory.build {
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
DropTableFactory.register(kingscorpionDrops, Npcs.KING_SCORPION)

on_npc_death(Npcs.KING_SCORPION) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
on_npc_spawn(Npcs.KING_SCORPION) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}
