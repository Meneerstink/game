package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 1.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 6606, Level 145.
set_combat_def(npc = Npcs.DARK_WARRIOR_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.SLASH
        respawnDelay = 50
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 1650
        attack = 75
        strength = 75
        defence = 55
        magic = 1
        ranged = 1
    }
    bonuses {
        attackBonus = 80
        strengthBonus = 76
        attackMagic = 0
        magicDamageBonus = 0
        attackRanged = 0
        rangedStrengthBonus = 0
        defenceStab = 106
        defenceSlash = 109
        defenceCrush = 139
        defenceMagic = 0
        defenceRanged = 0
    }
    slayer { experience = 181.5 }
    anims {
        attack = 390
        block = 388
        death = 836
    }
}

val darkwarriorDrops = DropTableFactory.build {
    guaranteed {
        obj(Items.BONES, quantity = 1)
    }
    main {
        total(128)
        obj(Items.ADAMANT_MED_HELM, quantity = 1, slots = 3)
        obj(Items.BLACK_MACE, quantity = 1, slots = 1)
        obj(Items.BLACK_MED_HELM, quantity = 1, slots = 1)
        obj(Items.MITHRIL_MACE, quantity = 1, slots = 1)
        obj(Items.MIND_RUNE, quantity = 12, slots = 3)
        obj(Items.WATER_RUNE, quantity = 12, slots = 2)
        obj(Items.EARTH_RUNE, quantity = 12, slots = 1)
        obj(Items.BRONZE_ARROW, quantity = 28, slots = 4)
        obj(Items.CHAOS_RUNE, quantity = 8, slots = 1)
        obj(Items.NATURE_RUNE, quantity = 9, slots = 2)
        obj(Items.COINS_995, quantity = 100, slots = 31)
        obj(Items.COINS_995, quantity = 20, slots = 20)
        obj(Items.COINS_995, quantity = 60, slots = 20)
        obj(Items.COINS_995, quantity = 130, slots = 7)
        obj(Items.COINS_995, quantity = 200, slots = 6)
        obj(Items.COINS_995, quantity = 90, slots = 2)
        nothing(16)
        nothing(2) // Missing item: Dark fishing bait; source quantity 10-24, chance 2/128.
        obj(Items.IRON_ORE, quantity = 3, slots = 1)
        obj(Items.SARDINE, quantity = 3, slots = 1)
        table(WildernessNpcDrops.herbs, slots = 3)
    }
    table("clue_scroll_hard") {
        val denominator = WildernessNpcDrops.clueDenominator(player, 128)
        total(denominator)
        obj(Items.CLUE_SCROLL_HARD, quantity = 1, slots = 1)
        nothing(denominator - 1)
    }
}
DropTableFactory.register(darkwarriorDrops, Npcs.DARK_WARRIOR_OSRS)

on_npc_death(Npcs.DARK_WARRIOR_OSRS) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
on_npc_spawn(15088) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}
