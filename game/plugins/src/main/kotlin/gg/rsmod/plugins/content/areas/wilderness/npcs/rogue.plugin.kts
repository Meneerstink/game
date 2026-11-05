package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 5.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 526, Level 15.
set_combat_def(npc = Npcs.ROGUE_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 50
    }
    aggro {
        neverAggro()
    }
    stats {
        hitpoints = 170
        attack = 13
        strength = 13
        defence = 13
        magic = 1
        ranged = 1
    }
    bonuses {
        attackBonus = 5
        strengthBonus = 5
        attackMagic = 0
        magicDamageBonus = 0
        attackRanged = 0
        rangedStrengthBonus = 0
        defenceStab = 6
        defenceSlash = 9
        defenceCrush = 11
        defenceMagic = 0
        defenceRanged = 0
    }
    slayer { experience = 17.0 }
    anims {
        attack = 386
        block = 424
        death = 836
    }
}

// OSRS 6603, Level 135.
set_combat_def(npc = Npcs.ROGUE_OSRS_135) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 50
    }
    aggro {
        neverAggro()
    }
    stats {
        hitpoints = 1250
        attack = 140
        strength = 140
        defence = 150
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
    slayer { experience = 125.0 }
    anims {
        attack = 422
        block = 424
        death = 836
    }
}

val rogueDrops = DropTableFactory.build {
    guaranteed {
        obj(Items.BONES, quantity = 1)
    }
    main {
        total(128)
        obj(Items.BRONZE_MED_HELM, quantity = 1, slots = 2)
        obj(Items.IRON_DAGGER, quantity = 1, slots = 1)
        obj(Items.BRONZE_BOLTS, quantityRange = 2..12, slots = 22)
        obj(Items.BRONZE_ARROW, quantity = 7, slots = 3)
        obj(Items.EARTH_RUNE, quantity = 4, slots = 2)
        obj(Items.FIRE_RUNE, quantity = 6, slots = 2)
        obj(Items.MIND_RUNE, quantity = 9, slots = 2)
        obj(Items.CHAOS_RUNE, quantity = 2, slots = 1)
        obj(Items.COINS_995, quantity = 3, slots = 38)
        obj(Items.COINS_995, quantity = 15, slots = 4)
        obj(Items.COINS_995, quantity = 25, slots = 1)
        obj(Items.ENERGY_POTION_3, quantity = 1, slots = 9)
        nothing(8)
        obj(Items.FISHING_BAIT, quantity = 1, slots = 5)
        obj(Items.COPPER_ORE, quantity = 1, slots = 2)
        obj(Items.EARTH_TALISMAN, quantity = 1, slots = 2)
        obj(Items.CABBAGE, quantity = 1, slots = 1)
        table(WildernessNpcDrops.herbs, slots = 23)
    }
    table("clue_scroll_beginner") {
        total(90)
        nothing(1) // Missing item: Clue scroll (beginner); source quantity 1, chance 1/90.
        nothing(89)
    }
    table("clue_scroll_easy") {
        total(128)
        obj(Items.CLUE_SCROLL_EASY, quantity = 1, slots = 1)
        nothing(127)
    }
}
DropTableFactory.register(rogueDrops, Npcs.ROGUE_OSRS)
val strongRogueDrops = DropTableFactory.build {
    guaranteed {
        obj(Items.BONES, quantity = 1)
        obj(Items.COINS_995, quantityRange = 1000..3000)
    }
    main {
        total(128)
    }
}
DropTableFactory.register(strongRogueDrops, Npcs.ROGUE_OSRS_135)

on_npc_death(Npcs.ROGUE_OSRS, Npcs.ROGUE_OSRS_135) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
