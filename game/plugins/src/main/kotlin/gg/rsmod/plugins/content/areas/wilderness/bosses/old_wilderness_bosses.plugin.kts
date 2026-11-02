package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * N522 bundle 5: the classic (pre-2023) Callisto and Vet'ion on the surface ([OldWildernessBosses]). Stats and spawns: Kronos-184 (a
 * pre-2023 OSRS server); drop tables: the current OSRS Wiki tables (owner decision pending - so the Voidwaker hilt and blade can be had).
 */

// ------------------------------------------------------------------ Callisto (Kronos-184 data/npcs/combat/Callisto.json)

set_combat_def(npc = OldWildernessBosses.CALLISTO) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 50
    }
    aggro {
        radius = 8
        alwaysAggro()
    }
    stats {
        hitpoints = 2550
        attack = 350
        strength = 370
        defence = 440
        magic = 1
        ranged = 1
    }
    bonuses {
        defenceStab = 135
        defenceSlash = 104
        defenceCrush = 175
        defenceMagic = 900
        defenceRanged = 230
    }
    anims {
        attack = OldWildernessBosses.CALLISTO_ATTACK
        block = OldWildernessBosses.CALLISTO_DEFEND
        death = OldWildernessBosses.CALLISTO_DEATH
    }
}

spawn_npc(npc = OldWildernessBosses.CALLISTO, x = OldWildernessBosses.CALLISTO_SPAWN.x, z = OldWildernessBosses.CALLISTO_SPAWN.z, walkRadius = 8)

on_npc_combat(*OldWildernessBosses.CallistoCombat.ids) {
    npc.queue { OldWildernessBosses.CallistoCombat.handleSpecialCombat(this) }
}

// ------------------------------------------------------------------ Vet'ion and Vet'ion Reborn (Kronos-184 data/npcs/combat/Vet'ion.json)

listOf(OldWildernessBosses.VETION, OldWildernessBosses.VETION_REBORN).forEach { id ->
    set_combat_def(npc = id) {
        configs {
            attackSpeed = 4
            attackStyle = if (id == OldWildernessBosses.VETION) StyleType.STAB else StyleType.CRUSH
            respawnDelay = Int.MAX_VALUE
        }
        aggro {
            radius = 10
            alwaysAggro()
        }
        stats {
            hitpoints = 2550
            attack = 430
            strength = 430
            defence = 395
            magic = 300
            ranged = 1
        }
        bonuses {
            defenceStab = 201
            defenceSlash = 200
            defenceCrush = -10
            defenceMagic = 250
            defenceRanged = 270
        }
        anims {
            attack = OldWildernessBosses.VETION_ATTACK
            block = OldWildernessBosses.VETION_DEFEND
            // Vet'ion does not fall: he rises as Vet'ion Reborn at once (Kronos startDeath -> transform); only Reborn plays the death.
            death = if (id == OldWildernessBosses.VETION) -1 else OldWildernessBosses.VETION_DEATH
        }
    }
    on_npc_spawn(id) {
        npc.respawns = false
        npc.attr[OldWildernessBosses.DOGS_CALLED] = false
    }
}

spawn_npc(npc = OldWildernessBosses.VETION, x = OldWildernessBosses.VETION_SPAWN.x, z = OldWildernessBosses.VETION_SPAWN.z, walkRadius = 10)

on_npc_combat(*OldWildernessBosses.VetionCombat.ids) {
    npc.queue { OldWildernessBosses.VetionCombat.handleSpecialCombat(this) }
}

on_npc_death(OldWildernessBosses.VETION) {
    OldWildernessBosses.removeDogs(npc)
    val killer = npc.damageMap.getMostDamage()
    val reborn = gg.rsmod.game.model.entity.Npc(OldWildernessBosses.VETION_REBORN, Tile(npc.tile), world)
    reborn.respawns = false
    if (world.spawn(reborn)) {
        reborn.forceChat("Now do it again!!")
        if (killer != null) reborn.attack(killer)
    }
}

on_npc_death(OldWildernessBosses.VETION_REBORN) {
    OldWildernessBosses.removeDogs(npc)
    val killer = npc.damageMap.getMostDamage() as? Player
    if (killer != null) {
        // The drop table and pet are Vet'ion's (one Vet'ion kill = both forms).
        DropTableFactory.getDrop(world, killer, OldWildernessBosses.VETION, npc.tile)
        WildernessLairs.onTopDamageKill(killer)
    }
    world.queue {
        wait(OldWildernessBosses.VETION_RESPAWN_TICKS)
        val vetion = gg.rsmod.game.model.entity.Npc(OldWildernessBosses.VETION, Tile(OldWildernessBosses.VETION_SPAWN), world)
        vetion.walkRadius = 10
        world.spawn(vetion)
    }
}

// ------------------------------------------------------------------ the skeleton hellhounds (Kronos-184 data/npcs/combat)

mapOf(OldWildernessBosses.HELLHOUND to listOf(1100, 210, 250, 150, 101, 103, 10, 180, 266), OldWildernessBosses.GREATER_HELLHOUND to listOf(1900, 240, 310, 220, 150, 163, 20, 210, 275)).forEach { (id, v) ->
    set_combat_def(npc = id) {
        configs {
            attackSpeed = 4
            attackStyle = StyleType.CRUSH
            respawnDelay = Int.MAX_VALUE
        }
        stats {
            hitpoints = v[0]
            attack = v[1]
            strength = v[2]
            defence = v[3]
            magic = 1
            ranged = 1
        }
        bonuses {
            defenceStab = v[4]
            defenceSlash = v[5]
            defenceCrush = v[6]
            defenceMagic = v[7]
            defenceRanged = v[8]
        }
        anims {
            attack = OldWildernessBosses.HOUND_ATTACK
            block = OldWildernessBosses.HOUND_DEFEND
            death = OldWildernessBosses.HOUND_DEATH
        }
    }
}

on_npc_combat(*OldWildernessBosses.HoundCombat.ids) {
    npc.queue { OldWildernessBosses.HoundCombat.handleSpecialCombat(this) }
}

// ------------------------------------------------------------------ drops (OSRS Wiki "Callisto" / "Vet'ion", 2026-11-02)
// Not ported: Wilderness crabs teleport (3/126, left as nothing), Brimstone key (Konar), Wilderness Slayer tertiaries, the Skeleton
// champion scroll (not in this game). Uniques at most one a kill: 1/196, 1/256, 1/256, 1/360, 1/512 over 1,128,960. Pets: pet_tiers.tsv.

fun spiderLikeTertiary(builder: gg.rsmod.plugins.content.drops.DropTableBuilder) {
    builder.table("supplies") {
        total(18)
        obj(Items.BLIGHTED_ANGLERFISH, 5..6, slots = 1)
        obj(Items.BLIGHTED_KARAMBWAN, 5..6, slots = 1)
        obj(Items.BLIGHTED_SUPER_RESTORE3, 3..4, slots = 1)
        obj(Items.BLIGHTED_SUPER_RESTORE4, 3..4, slots = 1)
        obj(Items.RANGING_POTION_2, 2..3, slots = 1)
        obj(Items.SUPER_COMBAT_POTION_2, 2..3, slots = 1)
        nothing(12)
    }
    builder.table("looting_bag") {
        total(3)
        obj(Items.LOOTING_BAG, slots = 1)
        nothing(2)
    }
    builder.table("long_bone") {
        total(400)
        obj(Items.LONG_BONE, slots = 1)
        nothing(399)
    }
    builder.table("curved_bone") {
        total(10025)
        obj(Items.CURVED_BONE, slots = 2)
        nothing(10023)
    }
}

val callistoDrops =
    DropTableFactory.build {
        guaranteed { obj(Items.BIG_BONES) }
        table("unique") {
            total(1_128_960)
            obj(Items.CLAWS_OF_CALLISTO, slots = 5760)
            obj(Items.DRAGON_2H_SWORD, slots = 4410)
            obj(Items.DRAGON_PICKAXE, slots = 4410)
            obj(Items.VOIDWAKER_HILT, slots = 3136)
            obj(Items.TYRANNICAL_RING, slots = 2205)
            nothing(1_128_960 - 19_921)
        }
        main {
            total(126)
            obj(Items.MYSTIC_EARTH_STAFF_NOTED, quantity = 4, slots = 2)
            obj(Items.MYSTIC_ROBE_TOP_NOTED, quantity = 4, slots = 2)
            obj(Items.MYSTIC_ROBE_BOTTOM_NOTED, quantity = 4, slots = 2)
            obj(Items.RUNE_PICKAXE_NOTED, quantity = 5, slots = 8)
            obj(Items.RUNE_2H_SWORD_NOTED, quantity = 3, slots = 3)
            obj(Items.RUNE_KITESHIELD_NOTED, quantity = 4, slots = 2)
            obj(Items.RUNE_PLATEBODY_NOTED, quantity = 4, slots = 2)
            obj(Items.BLOOD_RUNE, quantity = 500, slots = 7)
            obj(Items.DEATH_RUNE, quantity = 700, slots = 7)
            obj(Items.CHAOS_RUNE, quantity = 900, slots = 7)
            obj(Items.SOUL_RUNE, quantity = 450, slots = 5)
            obj(Items.CANNONBALL, quantity = 600, slots = 4)
            obj(Items.UNCUT_RUBY_NOTED, quantity = 50, slots = 4)
            obj(Items.UNCUT_DIAMOND_NOTED, quantity = 25, slots = 3)
            obj(Items.UNCUT_DRAGONSTONE_NOTED, quantity = 5, slots = 2)
            obj(Items.LIMPWURT_ROOT_NOTED, quantity = 100, slots = 5)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 225, slots = 5)
            obj(Items.MAHOGANY_LOGS_NOTED, quantity = 600, slots = 6)
            obj(Items.RED_DRAGONHIDE_NOTED, quantity = 170, slots = 3)
            obj(Items.COCONUT_NOTED, quantity = 135, slots = 2)
            obj(Items.GRIMY_TOADFLAX_NOTED, quantity = 100, slots = 1)
            obj(Items.GRIMY_DWARF_WEED_NOTED, quantity = 45, slots = 1)
            obj(Items.GRIMY_RANARR_NOTED, quantity = 45, slots = 1)
            obj(Items.GRIMY_SNAPDRAGON_NOTED, quantity = 45, slots = 1)
            obj(Items.RANARR_SEED, quantity = 11, slots = 5)
            obj(Items.SNAPDRAGON_SEED, quantity = 5, slots = 5)
            obj(Items.COINS_995, quantity = 50_000, slots = 10)
            obj(Items.DARK_CRAB_NOTED, quantity = 50, slots = 5)
            obj(Items.SUPER_RESTORE_4_NOTED, quantity = 10, slots = 5)
            obj(Items.SUPERCOMPOST_NOTED, quantity = 225, slots = 4)
            obj(Items.DRAGON_BONES_NOTED, quantity = 75, slots = 3)
            obj(Items.BLIGHTED_ANGLERFISH_NOTED, quantity = 100, slots = 1)
            nothing(3) // Wilderness crabs teleport (not in this game)
        }
        spiderLikeTertiary(this)
    }

DropTableFactory.register(callistoDrops, OldWildernessBosses.CALLISTO)

val vetionDrops =
    DropTableFactory.build {
        guaranteed { obj(Items.BIG_BONES) }
        table("unique") {
            total(1_128_960)
            obj(Items.SKULL_OF_VETION, slots = 5760)
            obj(Items.DRAGON_2H_SWORD, slots = 4410)
            obj(Items.DRAGON_PICKAXE, slots = 4410)
            obj(Items.VOIDWAKER_BLADE, slots = 3136)
            obj(Items.RING_OF_THE_GODS, slots = 2205)
            nothing(1_128_960 - 19_921)
        }
        main {
            total(126)
            obj(Items.MYSTIC_FIRE_STAFF_NOTED, quantity = 4, slots = 2)
            obj(Items.MYSTIC_WATER_STAFF_NOTED, quantity = 4, slots = 2)
            obj(Items.MYSTIC_ROBE_TOP_NOTED, quantity = 4, slots = 2)
            obj(Items.MYSTIC_ROBE_BOTTOM_NOTED, quantity = 4, slots = 2)
            obj(Items.RUNE_FULL_HELM_NOTED, quantity = 4, slots = 2)
            obj(Items.RUNE_PICKAXE_NOTED, quantity = 5, slots = 8)
            obj(Items.RUNE_DART, quantity = 150, slots = 3)
            obj(Items.RUNE_KNIFE, quantity = 150, slots = 3)
            obj(Items.CHAOS_RUNE, quantity = 900, slots = 7)
            obj(Items.DEATH_RUNE, quantity = 700, slots = 7)
            obj(Items.BLOOD_RUNE, quantity = 500, slots = 7)
            obj(Items.CANNONBALL, quantity = 550, slots = 4)
            obj(Items.UNCUT_RUBY_NOTED, quantity = 50, slots = 4)
            obj(Items.UNCUT_DIAMOND_NOTED, quantity = 25, slots = 3)
            obj(Items.UNCUT_DRAGONSTONE_NOTED, quantity = 5, slots = 2)
            obj(Items.GOLD_ORE_NOTED, quantity = 675, slots = 6)
            obj(Items.LIMPWURT_ROOT_NOTED, quantity = 60, slots = 5)
            obj(Items.WINE_OF_ZAMORAK_NOTED, quantity = 100, slots = 5)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 225, slots = 5)
            obj(Items.OAK_PLANK_NOTED, quantity = 400, slots = 5)
            obj(Items.DRAGON_BONES_NOTED, quantity = 150, slots = 3)
            obj(Items.MORT_MYRE_FUNGUS_NOTED, quantity = 450, slots = 2)
            obj(Items.GRIMY_RANARR_NOTED, quantity = 100, slots = 1)
            obj(Items.GRIMY_DWARF_WEED_NOTED, quantity = 45, slots = 1)
            obj(Items.GRIMY_SNAPDRAGON_NOTED, quantity = 45, slots = 1)
            obj(Items.GRIMY_TOADFLAX_NOTED, quantity = 45, slots = 1)
            obj(Items.COINS_995, quantity = 50_000, slots = 10)
            obj(Items.SUPERCOMPOST_NOTED, quantity = 225, slots = 4)
            obj(Items.SUPER_RESTORE_4_NOTED, quantity = 10, slots = 5)
            obj(Items.BLIGHTED_ANGLERFISH_NOTED, quantity = 100, slots = 1)
            obj(Items.SANFEW_SERUM_4_NOTED, quantity = 20, slots = 5)
            obj(Items.DARK_CRAB_NOTED, quantity = 50, slots = 5)
            nothing(3) // Wilderness crabs teleport (not in this game)
        }
        spiderLikeTertiary(this)
    }

DropTableFactory.register(vetionDrops, OldWildernessBosses.VETION)

on_npc_death(OldWildernessBosses.CALLISTO) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
    WildernessLairs.onTopDamageKill(killer)
}
