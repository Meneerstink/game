package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.audio.NpcCombatAudio
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * N522 nachtrun deel 1 (owner 2026-11-02 15:4x "Chaos Elemental VOLLEDIG OSRS (drops + mechanics)"): the OSRS Chaos Elemental, west of the
 * Rogues' Castle (multi-combat, map square 12861 in multi.yml). Stats, location and drops: OSRS Wiki "Chaos Elemental" (2026-11-04; hitpoints
 * here are real HP x10, the DSL rule). Fight: [ChaosElementalCombat]. Pet chaos elemental (1/300): data/cfg/pets/pet_tiers.tsv, rolled by
 * the shared on_npc_killed pet roll. Replaces the 667/Void version (npc 3200, npcs/definitions/other/chaos_elemental_drops).
 */

set_combat_def(npc = Npcs.CHAOS_ELEMENTAL) {
    configs {
            waitFullDeathAnimation = true
        attackSpeed = 4
        attackStyle = StyleType.MAGIC
        // Infobox respawn 14 ticks (= 8.4 seconds, OSRS Wiki changelog April 2024).
        respawnDelay = 14
        // "Venom resistance: Poison" - venom never sticks.
        venomImmune = true
    }
    aggro {
        radius = ChaosElementalCombat.ATTACK_RANGE
        alwaysAggro()
    }
    stats {
        hitpoints = 2500
        attack = 270
        strength = 270
        defence = 270
        magic = 270
        ranged = 270
    }
    bonuses {
        defenceStab = 70
        defenceSlash = 70
        defenceCrush = 70
        defenceMagic = 70
        defenceRanged = 70
    }
    anims {
        attack = ChaosElementalCombat.ANIM_ATTACK
        // OSRS has no defend sequence for the Chaos Elemental: it keeps standing (ready 3144).
        block = -1
        death = ChaosElementalCombat.ANIM_DEATH
    }
}

on_npc_combat(*ChaosElementalCombat.ids) {
    npc.queue {
        ChaosElementalCombat.handleSpecialCombat(this)
    }
}

// Hit / death: OSRS synths CHAOS_ELEMENTAL_HIT 351 / CHAOS_ELEMENTAL_DEATH 347 (Void chaos_elemental.combat.toml defend_sound / death_sound).
NpcCombatAudio.register(
    NpcCombatAudio.Row(id = Npcs.CHAOS_ELEMENTAL, name = "Chaos Elemental", defend = Sfx.CHAOS_ELEMENTAL_HIT, death = Sfx.CHAOS_ELEMENTAL_DEATH),
)

// OSRS Wiki LocLine (3261,3927) is the centre of the 3x3 elemental: south-west tile (3260,3926). Wander radius as the 667 spawn.
spawn_npc(npc = Npcs.CHAOS_ELEMENTAL, x = 3260, z = 3926, walkRadius = 10, direction = Direction.WEST)

// ------------------------------------------------------------------ drops (OSRS Wiki drop table, 2026-11-04)
// Pre-roll: a dragon pickaxe replaces the major drop (DropTableFactory "pre_roll"). Minor drops come with every kill (1 of 5).
// Not ported: Wilderness crabs teleport (2/128 - no such tablet here, left as nothing so every other rate stays the wiki rate),
// Brimstone key (Konar only), Wilderness Slayer tertiaries (Krystilia task drops - nachtrun deel 3), looting bag (out of the game,
// N424 / N561, owner 2026-10-29 + 2026-11-04).

val chaosElementalDrops =
    DropTableFactory.build {
        table("pre_roll") {
            total(256)
            obj(Items.DRAGON_PICKAXE, slots = 1)
            nothing(255)
        }
        main {
            total(128)
            // weapons and armour
            obj(Items.RUNE_DART, quantity = 100, slots = 5)
            obj(Items.RUNE_PLATELEGS, slots = 4)
            obj(Items.RUNE_PLATESKIRT, slots = 4)
            obj(Items.RUNE_2H_SWORD, slots = 3)
            obj(Items.RUNE_BATTLEAXE, slots = 3)
            obj(Items.RUNE_FULL_HELM, slots = 3)
            obj(Items.RUNE_KITESHIELD, slots = 3)
            obj(Items.MYSTIC_AIR_STAFF, slots = 3)
            obj(Items.MYSTIC_WATER_STAFF, slots = 3)
            obj(Items.MYSTIC_EARTH_STAFF, slots = 3)
            obj(Items.MYSTIC_FIRE_STAFF, slots = 3)
            obj(Items.DRAGON_DAGGER, slots = 2)
            obj(Items.DRAGON_2H_SWORD, slots = 2)
            obj(Items.DRAGON_PLATELEGS, slots = 2)
            obj(Items.DRAGON_PLATESKIRT, slots = 2)
            // runes and ammunition
            obj(Items.CHAOS_RUNE, 300..500, slots = 8)
            obj(Items.BLOOD_RUNE, 100..250, slots = 8)
            obj(Items.RUNE_ARROW, quantity = 150, slots = 5)
            // herbs
            obj(Items.GRIMY_RANARR_NOTED, 5..8, slots = 4)
            obj(Items.GRIMY_SNAPDRAGON_NOTED, 5..8, slots = 4)
            obj(Items.GRIMY_AVANTOE_NOTED, 5..8, slots = 3)
            obj(Items.GRIMY_KWUARM_NOTED, 5..8, slots = 3)
            // resources
            obj(Items.COAL_NOTED, 75..150, slots = 5)
            obj(Items.MAHOGANY_PLANK_NOTED, 8..16, slots = 5)
            obj(Items.RUNE_BAR_NOTED, 3..5, slots = 4)
            obj(Items.ADAMANT_BAR_NOTED, 8..12, slots = 4)
            // materials
            obj(Items.COINS_995, 20005..29995, slots = 7)
            obj(Items.BLIGHTED_ANCIENT_ICE_SACK, 20..40, slots = 5)
            obj(Items.BLIGHTED_KARAMBWAN_NOTED, 15..25, slots = 4)
            obj(Items.BLIGHTED_ANGLERFISH_NOTED, 10..15, slots = 4)
            nothing(2) // Wilderness crabs teleport (not in this game)
            // rare drop table
            table(Rare.rareTable, slots = 8)
        }
        table("minor") {
            total(5)
            obj(Items.BLIGHTED_ANGLERFISH, quantity = 2, slots = 1)
            obj(Items.BLIGHTED_KARAMBWAN, quantity = 3, slots = 1)
            obj(Items.BLIGHTED_SUPER_RESTORE4, slots = 1)
            obj(Items.DRAGON_BONES, slots = 1)
            obj(Items.SUPER_COMBAT_POTION_1, slots = 1)
        }
        table("tertiary") {
            total(128)
            obj(Items.WEAPON_POISON_5940, slots = 10)
            nothing(118)
        }
        table("elite_clue") {
            val denominator = WildernessBossClues.denominator(player, 200)
            total(denominator)
            obj(Items.CLUE_SCROLL_ELITE, slots = 1)
            nothing(denominator - 1)
        }
    }

DropTableFactory.register(chaosElementalDrops, Npcs.CHAOS_ELEMENTAL)

on_npc_death(Npcs.CHAOS_ELEMENTAL) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
