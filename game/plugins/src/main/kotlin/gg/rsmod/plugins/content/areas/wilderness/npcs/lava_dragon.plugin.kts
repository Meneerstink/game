package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 6.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 6593, doelvariant.
set_combat_def(npc = Npcs.LAVA_DRAGON_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.SLASH
        respawnDelay = 14
        xpMultiplier = 1.05
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 2300
        attack = 240
        strength = 220
        defence = 220
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
        defenceStab = 20
        defenceSlash = 90
        defenceCrush = 90
        defenceMagic = 80
        defenceRanged = 70
    }
    species { +NpcSpecies.BASIC_DRAGON; +NpcSpecies.FIERY }
    slayer { experience = 248.0 }
    anims {
        attack = 16160
        block = 16161
        death = 15612
    }
}
ElementalWeakness.idOverrides[Npcs.LAVA_DRAGON_OSRS] = ElementalWeakness.Weakness(ElementalWeakness.Element.WATER, 50)

val lavadragonDrops = DropTableFactory.build {
    guaranteed {
        nothing(1) // Missing item: Lava dragon bones; source quantity 1, chance Always.
        obj(Items.BLACK_DRAGONHIDE, quantity = 1)
        nothing(1) // Missing item: Lava scale; source quantity 1, chance Always.
    }
    main {
        total(128)
        obj(Items.RUNE_DART, quantity = 12, slots = 6)
        obj(Items.RUNE_KNIFE, quantity = 8, slots = 4)
        obj(Items.LAVA_BATTLESTAFF, quantity = 1, slots = 3)
        obj(Items.ADAMANT_2H_SWORD, quantity = 1, slots = 2)
        obj(Items.ADAMANT_PLATEBODY, quantity = 1, slots = 2)
        obj(Items.RUNE_HATCHET, quantity = 1, slots = 2)
        obj(Items.RUNE_KITESHIELD, quantity = 1, slots = 2)
        obj(Items.RUNE_LONGSWORD, quantity = 1, slots = 2)
        obj(Items.RUNE_MED_HELM, quantity = 1, slots = 1)
        obj(Items.RUNE_FULL_HELM, quantity = 1, slots = 1)
        obj(Items.RUNE_JAVELIN, quantity = 20, slots = 10)
        obj(Items.FIRE_RUNE, quantity = 75, slots = 7)
        obj(Items.BLOOD_RUNE, quantity = 20, slots = 7)
        obj(Items.RUNITE_BOLTS, quantity = 30, slots = 6)
        obj(Items.DEATH_RUNE, quantity = 20, slots = 5)
        obj(Items.LAW_RUNE, quantity = 20, slots = 5)
        obj(Items.LAVA_RUNE, quantity = 15, slots = 4)
        obj(Items.LAVA_RUNE, quantity = 30, slots = 4)
        obj(Items.COINS_995, quantity = 66, slots = 15)
        if (!WildernessNpcDrops.monkeyMadnessII(player)) obj(Items.COINS_995, quantity = 2960, slots = 7)
        obj(Items.COINS_995, quantity = 690, slots = 1)
        obj(Items.FIRE_TALISMAN, quantity = 1, slots = 1)
        obj(Items.FIRE_ORB_NOTED, quantity = 15, slots = 5)
        obj(Items.CHOCOLATE_CAKE, quantity = 3, slots = 3)
        obj(Items.ADAMANT_BAR, quantity = 2, slots = 5)
        obj(Items.ONYX_BOLT_TIPS, quantity = 12, slots = 5)
        if (WildernessNpcDrops.monkeyMadnessII(player)) obj(Items.DRAGON_JAVELIN_TIPS, quantity = 15, slots = 7)
        table(WildernessNpcDrops.doubleHerbs, slots = 5)
        table(WildernessNpcDrops.rare, slots = 3, label = "Rare drop table")
        table(WildernessNpcDrops.gems, slots = 5, label = "Gem drop table")
    }
    table("ensouled_dragon_head") {
        total(18)
        nothing(1) // Missing item: Ensouled dragon head; source quantity 1, chance 1/18.
        nothing(17)
    }
    table("clue_scroll_elite") {
        val denominator = WildernessNpcDrops.clueDenominator(player, 250)
        total(denominator)
        obj(Items.CLUE_SCROLL_ELITE, quantity = 1, slots = 1)
        nothing(denominator - 1)
    }
    table("draconic_visage") {
        total(10000)
        obj(Items.DRACONIC_VISAGE, quantity = 1, slots = 1)
        nothing(9999)
    }
}
DropTableFactory.register(lavadragonDrops, Npcs.LAVA_DRAGON_OSRS)

on_npc_death(Npcs.LAVA_DRAGON_OSRS) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
on_npc_spawn(15098) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}
on_npc_combat(*intArrayOf(15098)) { npc.queue { WildernessNpcMechanics.lavaCombat(this) } }
