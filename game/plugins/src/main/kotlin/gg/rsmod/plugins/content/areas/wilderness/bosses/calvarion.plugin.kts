package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * N522 nachtrun deel 2 (owner 2026-11-04: Calvar'ion via route B): Calvar'ion in the Skeletal Tomb (map square 7604, plane 1, singles-plus).
 * Stats, drops: OSRS Wiki "Calvar'ion" (2026-11-05; hitpoints here are real HP x10, the DSL rule; the enraged form has no own infobox, so
 * the normal stats with attack speed 5). Fight: [CalvarionCombat]. One kill = both forms; the drop table, kill count and pet (Vet'ion Jr.
 * 1/2,800, pet_tiers.tsv) are the normal form's ([OldWildernessBosses.rewardNpc]). Collection log: "Vet'ion and Calvar'ion".
 */

listOf(CalvarionCombat.NORMAL to 6, CalvarionCombat.ENRAGED to 5).forEach { (id, speed) ->
    set_combat_def(npc = id) {
        configs {
            attackSpeed = speed
            completesEncounter = id == CalvarionCombat.ENRAGED
            attackStyle = StyleType.SLASH
            respawnDelay = Int.MAX_VALUE
            deathDelay = if (id == CalvarionCombat.NORMAL) 0 else 1
            poisonImmune = true
            venomImmune = true
        }
        aggro {
            radius = 10
            alwaysAggro()
        }
        stats {
            hitpoints = 1500
            attack = 250
            strength = 250
            defence = 225
            magic = 178
            ranged = 1
        }
        bonuses {
            defenceStab = 130
            defenceSlash = 128
            defenceCrush = -10
            defenceMagic = 198
            defenceRanged = 211
        }
        anims {
            attack = if (id == CalvarionCombat.NORMAL) CalvarionCombat.ANIM_MAGIC else CalvarionCombat.ANIM_MAGIC_ENRAGED
            // The first form does not fall: the enraged form rises at once (Near Reality sendDeath -> setTransformation).
            death = if (id == CalvarionCombat.NORMAL) -1 else CalvarionCombat.ANIM_DEATH
        }
        // Wiki 15375801: 322.5 XP for the complete fight, not twice that amount.
        slayer { experience = 161.25 }
    }
    on_npc_spawn(id) {
        val boss = npc
        boss.hitModifier = { hit -> CalvarionCombat.modifyIncomingHit(boss, hit) }
        npc.respawns = false
        npc.attr[CalvarionCombat.HOUNDS_CALLED] = false
        if (id == CalvarionCombat.NORMAL) npc.animate(CalvarionCombat.ANIM_SPAWN)
    }
}

on_npc_combat(*CalvarionCombat.ids) {
    npc.queue { CalvarionCombat.handleSpecialCombat(this) }
}

// Near Reality SkeletalTomb: Calvar'ion at (1887,11546,1) facing south, wander radius 3.
spawn_npc(npc = CalvarionCombat.NORMAL, x = CalvarionCombat.SPAWN.x, z = CalvarionCombat.SPAWN.z, height = CalvarionCombat.SPAWN.height, walkRadius = 3, direction = Direction.SOUTH)

on_npc_death(CalvarionCombat.NORMAL) {
    CalvarionCombat.enrage(npc)
}

on_npc_death(CalvarionCombat.ENRAGED) {
    CalvarionCombat.removeHounds(npc)
    CalvarionCombat.shout(npc, CalvarionCombat.DEATH_SHOUTS.random())
    val killer = npc.damageMap.getMostDamage() as? Player
    if (killer != null) {
        DropTableFactory.getDrop(world, killer, CalvarionCombat.NORMAL, npc.tile)
        WildernessLairs.onTopDamageKill(killer)
    }
    world.queue {
        // Infobox respawn 14 ticks.
        wait(14)
        val boss = gg.rsmod.game.model.entity.Npc(CalvarionCombat.NORMAL, Tile(CalvarionCombat.SPAWN), world)
        boss.walkRadius = 3
        world.spawn(boss)
    }
}

// ------------------------------------------------------------------ the hellhounds (OSRS Wiki infoboxes 12107 / 12108)

mapOf(
    CalvarionCombat.HOUND to listOf(300, 110, 150, 95, 76, 73, 10, 126, 155),
    CalvarionCombat.GREATER_HOUND to listOf(300, 150, 170, 110, 95, 101, 15, 156, 184),
).forEach { (id, v) ->
    set_combat_def(npc = id) {
        configs {
            attackSpeed = 4
            attackStyle = StyleType.CRUSH
            respawnDelay = Int.MAX_VALUE
        }
        aggro {
            radius = 10
            alwaysAggro()
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
            attack = CalvarionCombat.HOUND_ATTACK
            block = CalvarionCombat.HOUND_DEFEND
            death = CalvarionCombat.HOUND_DEATH
        }
    }
}

on_npc_combat(*CalvarionCombat.HoundCombat.ids) {
    npc.queue { CalvarionCombat.HoundCombat.handleSpecialCombat(this) }
}

// The hounds' only drop here is Bones (OSRS Wiki; looting bag out of the game N561, Skeleton champion scroll not in this game).
val calvarionHoundDrops =
    DropTableFactory.build {
        guaranteed { obj(Items.BONES) }
    }
DropTableFactory.register(calvarionHoundDrops, CalvarionCombat.HOUND, CalvarionCombat.GREATER_HOUND)

on_npc_death(CalvarionCombat.HOUND, CalvarionCombat.GREATER_HOUND) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}

// ------------------------------------------------------------------ drops (OSRS Wiki "Calvar'ion", 2026-11-05)
// Not ported: Dark fishing bait (1/128, left as nothing), Brimstone key (Konar), Skeleton champion scroll (not in this game),
// Wilderness Slayer tertiaries (Krystilia, deel 3), looting bag (out of the game, N424 / N561).

val calvarionDrops =
    DropTableFactory.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }
        // At most one unique a kill: dragon 2h 1/358, dragon pickaxe 1/358, skull 1/618, ring of the gods 1/716, voidwaker blade 1/912.
        table("unique") {
            total(16_814_544)
            obj(Items.DRAGON_2H_SWORD, slots = 46_968)
            obj(Items.DRAGON_PICKAXE, slots = 46_968)
            obj(Items.SKULL_OF_VETION, slots = 27_208)
            obj(Items.RING_OF_THE_GODS, slots = 23_484)
            obj(Items.VOIDWAKER_BLADE, slots = 18_437)
            nothing(16_814_544 - 163_065)
        }
        main {
            total(128)
            // food and potions
            obj(Items.DARK_CRAB, quantity = 8, slots = 5)
            obj(Items.SUPER_RESTORE_4, quantity = 3, slots = 5)
            // weapons and armour
            obj(Items.ANCIENT_STAFF, slots = 3)
            obj(Items.RUNE_2H_SWORD, slots = 3)
            obj(Items.RUNE_PICKAXE, slots = 12)
            // runes and ammunition (OSRS "Steel cannonball" = 667 "Cannonball" 2)
            obj(Items.CHAOS_RUNE, quantity = 220, slots = 7)
            obj(Items.DEATH_RUNE, quantity = 120, slots = 7)
            obj(Items.BLOOD_RUNE, quantity = 180, slots = 7)
            obj(Items.CANNONBALL, quantity = 180, slots = 4)
            // materials
            obj(Items.UNCUT_RUBY_NOTED, quantity = 17, slots = 4)
            obj(Items.UNCUT_DIAMOND_NOTED, quantity = 7, slots = 3)
            obj(Items.UNCUT_DRAGONSTONE, slots = 2)
            obj(Items.GOLD_ORE_NOTED, quantity = 200, slots = 6)
            obj(Items.LIMPWURT_ROOT_NOTED, quantity = 19, slots = 5)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 60, slots = 5)
            obj(Items.OAK_PLANK_NOTED, quantity = 220, slots = 5)
            obj(Items.DRAGON_BONES_NOTED, quantity = 60, slots = 3)
            obj(Items.MORT_MYRE_FUNGUS_NOTED, quantity = 120, slots = 2)
            obj(Items.WINE_OF_ZAMORAK_NOTED, quantity = 35, slots = 5)
            obj(Items.GRIMY_RANARR_NOTED, quantity = 60, slots = 1)
            // other
            obj(Items.COINS_995, quantity = 12_000, slots = 21)
            obj(Items.YEW_SEED, slots = 1)
            obj(Items.MAGIC_SEED, slots = 1)
            obj(Items.PALM_TREE_SEED, slots = 1)
            obj(Items.SUPERCOMPOST_NOTED, quantity = 60, slots = 4)
            obj(Items.SANFEW_SERUM_4_NOTED, quantity = 6, slots = 5)
            nothing(1) // Dark fishing bait (not in this game)
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

DropTableFactory.register(calvarionDrops, CalvarionCombat.NORMAL)
