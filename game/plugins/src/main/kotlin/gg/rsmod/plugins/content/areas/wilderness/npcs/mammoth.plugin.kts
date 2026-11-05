package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 8.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 6604, doelvariant.
set_combat_def(npc = Npcs.MAMMOTH_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 70
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 1300
        attack = 55
        strength = 60
        defence = 50
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
    slayer { experience = 130.0 }
    anims {
        attack = 16286
        block = 16287
        death = 16288
    }
}

val mammothDrops = DropTableFactory.build {
    guaranteed {
        obj(Items.BIG_BONES, quantity = 1)
    }
    main {
        total(640)
        obj(Items.POTATO_SEED, quantity = 3, slots = 30)
        obj(Items.ONION_SEED, quantity = 3, slots = 20)
        obj(Items.CABBAGE_SEED, quantity = 3, slots = 16)
        obj(Items.TOMATO_SEED, quantity = 3, slots = 14)
        obj(Items.SWEETCORN_SEED, quantity = 3, slots = 10)
        obj(Items.STRAWBERRY_SEED, quantity = 3, slots = 8)
        obj(Items.WATERMELON_SEED, quantity = 3, slots = 2)
        obj(Items.REDBERRY_SEED, quantity = 2, slots = 30)
        obj(Items.CADAVABERRY_SEED, quantity = 2, slots = 20)
        obj(Items.DWELLBERRY_SEED, quantity = 2, slots = 16)
        obj(Items.JANGERBERRY_SEED, quantity = 2, slots = 14)
        obj(Items.WHITEBERRY_SEED, quantity = 2, slots = 10)
        obj(Items.POISON_IVY_SEED, quantity = 2, slots = 10)
        obj(Items.BARLEY_SEED, quantity = 4, slots = 30)
        obj(Items.HAMMERSTONE_SEED, quantity = 4, slots = 20)
        obj(Items.ASGARNIAN_SEED, quantity = 4, slots = 16)
        obj(Items.JUTE_SEED, quantity = 4, slots = 14)
        obj(Items.YANILLIAN_SEED, quantity = 4, slots = 10)
        obj(Items.KRANDORIAN_SEED, quantity = 4, slots = 8)
        obj(Items.WILDBLOOD_SEED, quantity = 4, slots = 2)
        obj(Items.APPLE_TREE_SEED, quantity = 1, slots = 18)
        obj(Items.BANANA_TREE_SEED, quantity = 1, slots = 12)
        obj(Items.ORANGE_TREE_SEED, quantity = 1, slots = 10)
        obj(Items.CURRY_TREE_SEED, quantity = 1, slots = 6)
        obj(Items.PINEAPPLE_SEED, quantity = 1, slots = 3)
        obj(Items.PAPAYA_TREE_SEED, quantity = 1, slots = 1)
        obj(Items.COINS_995, quantity = 30, slots = 65)
        obj(Items.COINS_995, quantity = 180, slots = 35)
        obj(Items.ACORN, quantity = 1, slots = 25)
        obj(Items.LIMPWURT_SEED, quantity = 2, slots = 25)
        obj(Items.PRAYER_POTION_1, quantity = 1, slots = 25)
        obj(Items.STEEL_ARROW, quantity = 15, slots = 25)
        obj(Items.LOBSTER, quantity = 2, slots = 15)
        nothing(10) // Missing item: Dark fishing bait; source quantity 12, chance 2/128.
        table(WildernessNpcDrops.herbs, slots = 40)
        table(WildernessNpcDrops.rare, slots = 10, label = "Rare drop table")
        table(WildernessNpcDrops.gems, slots = 15, label = "Gem drop table")
    }
    table("clue_scroll_medium") {
        val denominator = WildernessNpcDrops.clueDenominator(player, 128)
        total(denominator)
        obj(Items.CLUE_SCROLL_MEDIUM, quantity = 1, slots = 1)
        nothing(denominator - 1)
    }
    WildernessNpcDrops.longBones(this)
}
DropTableFactory.register(mammothDrops, Npcs.MAMMOTH_OSRS)

on_npc_death(Npcs.MAMMOTH_OSRS) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
on_npc_spawn(15101) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}
gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.register(
    gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.Row(
        id = Npcs.MAMMOTH_OSRS, name = "Mammoth",
        attack = listOf(gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.Sound(id = 623)), defend = 625, death = 624,
    ),
) // Report section 8: proved Wiki SFXLine/file-license IDs, native 667 synths.
