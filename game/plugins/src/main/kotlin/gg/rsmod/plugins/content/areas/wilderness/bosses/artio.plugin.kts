package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * N522 nachtrun deel 2 (owner 2026-11-04: Artio via route B): Artio in Hunter's End (map square 7092, singles-plus like the Web Chasm).
 * Stats, location and drops: OSRS Wiki "Artio" (2026-11-05; hitpoints here are real HP x10, the DSL rule). Fight: [ArtioCombat].
 * Pet: Callisto cub 1/2,800 (data/cfg/pets/pet_tiers.tsv, the shared on_npc_killed roll). Collection log: "Callisto and Artio".
 */

set_combat_def(npc = ArtioCombat.NPC) {
    configs {
            waitFullDeathAnimation = true
        attackSpeed = 5
        attackStyle = StyleType.CRUSH
        respawnDelay = 11
        poisonImmune = true
        venomImmune = true
    }
    aggro {
        radius = 10
        alwaysAggro()
    }
    stats {
        hitpoints = 4500
        attack = 250
        strength = 270
        defence = 150
        magic = 90
        ranged = 120
    }
    bonuses {
        attackRanged = 60
        rangedStrengthBonus = 18
        defenceStab = 125
        defenceSlash = 110
        defenceCrush = 110
        defenceMagic = 0
        defenceRanged = 40
    }
    anims {
        attack = ArtioCombat.ANIM_MELEE
        death = ArtioCombat.ANIM_DEATH
    }
    // N522 fix 3: OSRS Wiki "Artio" infobox: slayxp 518 (Krystilia boss task; 0 XP before).
    slayer { experience = 518.0 }
}

on_npc_combat(*ArtioCombat.ids) {
    npc.queue {
        ArtioCombat.handleSpecialCombat(this)
    }
}

// Near Reality HuntersEnd: Artio at (1757,11543) facing south, wander radius 3; she roars when she (re)spawns (Callisto.spawn).
spawn_npc(npc = ArtioCombat.NPC, x = ArtioCombat.SPAWN.x, z = ArtioCombat.SPAWN.z, height = ArtioCombat.SPAWN.height, walkRadius = 3, direction = Direction.SOUTH)

on_npc_spawn(ArtioCombat.NPC) {
    ArtioCombat.reset(npc)
    npc.animate(ArtioCombat.ANIM_SPAWN)
}

// ------------------------------------------------------------------ drops (OSRS Wiki drop table, 2026-11-05)
// Not ported: Dark fishing bait (1/128 - not in this game, left as nothing), Brimstone key (Konar only), Wilderness Slayer tertiaries
// (Krystilia, nachtrun deel 3), looting bag (out of the game, N424 / N561).

val artioDrops =
    DropTableFactory.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }
        // At most one unique a kill: dragon 2h 1/358, dragon pickaxe 1/358, claws 1/618, tyrannical ring 1/716, voidwaker hilt 1/912
        // over the common denominator 16,814,544 (the same singles rates as Spindel).
        table("unique") {
            total(16_814_544)
            obj(Items.DRAGON_2H_SWORD, slots = 46_968)
            obj(Items.DRAGON_PICKAXE, slots = 46_968)
            obj(Items.CLAWS_OF_CALLISTO, slots = 27_208)
            obj(Items.TYRANNICAL_RING, slots = 23_484)
            obj(Items.VOIDWAKER_HILT, slots = 18_437)
            nothing(16_814_544 - 163_065)
        }
        main {
            total(128)
            // food and potions
            obj(Items.DARK_CRAB, quantity = 9, slots = 5)
            obj(Items.SUPER_RESTORE_4, quantity = 3, slots = 5)
            // weapons and armour
            obj(Items.RUNE_2H_SWORD, slots = 3)
            obj(Items.RUNE_PICKAXE, slots = 12)
            // runes and ammunition (OSRS "Steel cannonball" = 667 "Cannonball" 2)
            obj(Items.CHAOS_RUNE, quantity = 300, slots = 7)
            obj(Items.DEATH_RUNE, quantity = 220, slots = 7)
            obj(Items.BLOOD_RUNE, quantity = 140, slots = 7)
            obj(Items.SOUL_RUNE, quantity = 150, slots = 5)
            obj(Items.CANNONBALL, quantity = 190, slots = 4)
            // materials
            obj(Items.UNCUT_RUBY_NOTED, quantity = 17, slots = 4)
            obj(Items.UNCUT_DIAMOND_NOTED, quantity = 7, slots = 3)
            obj(Items.UNCUT_DRAGONSTONE, slots = 2)
            obj(Items.LIMPWURT_ROOT_NOTED, quantity = 20, slots = 5)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 60, slots = 5)
            obj(Items.MAHOGANY_LOGS_NOTED, quantity = 200, slots = 6)
            obj(Items.DRAGON_BONES_NOTED, quantity = 25, slots = 3)
            obj(Items.GRIMY_TOADFLAX_NOTED, quantity = 40, slots = 1)
            obj(Items.COCONUT_NOTED, quantity = 30, slots = 2)
            obj(Items.RED_DRAGONHIDE_NOTED, quantity = 55, slots = 3)
            // other
            obj(Items.COINS_995, quantity = 12_000, slots = 21)
            nothing(1) // Dark fishing bait (not in this game)
            obj(Items.SUPERCOMPOST_NOTED, quantity = 60, slots = 4)
            obj(Items.YEW_SEED, slots = 1)
            obj(Items.MAGIC_SEED, slots = 1)
            obj(Items.RANARR_SEED, quantity = 3, slots = 5)
            obj(Items.SNAPDRAGON_SEED, slots = 5)
            obj(Items.PALM_TREE_SEED, slots = 1)
        }
        // 1/3 a supply drop next to the regular drop, each of the six 1/18.
        table("supplies") {
            total(18)
            obj(Items.BLIGHTED_ANGLERFISH, 5..6, slots = 1)
            obj(Items.BLIGHTED_KARAMBWAN, 5..6, slots = 1)
            obj(Items.BLIGHTED_SUPER_RESTORE3, 3..4, slots = 1)
            obj(Items.BLIGHTED_SUPER_RESTORE4, 3..4, slots = 1)
            obj(Items.RANGING_POTION_2, 2..3, slots = 1)
            obj(Items.SUPER_COMBAT_POTION_2, 2..3, slots = 1)
            nothing(12)
        }
        table("long_bone") {
            total(400)
            obj(Items.LONG_BONE, slots = 1)
            nothing(399)
        }
        table("curved_bone") {
            total(5000) // Wiki Long bone: only rolled if the 1/400 long-bone roll failed.
            obj(Items.CURVED_BONE, slots = 1)
            nothing(4999)
        }
        table("elite_clue") {
            val denominator = WildernessBossClues.denominator(player, 100)
            total(denominator)
            obj(Items.CLUE_SCROLL_ELITE, slots = 1)
            nothing(denominator - 1)
        }
    }

DropTableFactory.register(artioDrops, ArtioCombat.NPC)

on_npc_death(ArtioCombat.NPC) {
    ArtioCombat.reset(npc)
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
