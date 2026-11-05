package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.combat.audio.NpcCombatAudio
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters

/**
 * N522 (owner 2026-11-01 "alle osrs wilderness bosses met hun locations in de wilderness volledig"): the OSRS Wilderness bosses,
 * bundle 1 - Chaos Fanatic and Crazy archaeologist (no lair: both stand in the open Wilderness, single-way combat).
 * Stats, locations and drops: OSRS Wiki infobox / LocLine / drop tables, 2026-11-01 (hitpoints here are real HP x10, the DSL rule).
 * Fights: [ChaosFanaticCombat], [CrazyArchaeologistCombat]. Pet chaos elemental from the fanatic: data/cfg/pets/pet_tiers.tsv.
 */

// ------------------------------------------------------------------ Chaos Fanatic (OSRS Wiki infobox, id 6619)

set_combat_def(npc = Npcs.CHAOS_FANATIC) {
    configs {
            waitFullDeathAnimation = true
        attackSpeed = 2
        attackStyle = StyleType.MAGIC
        respawnDelay = 15
    }
    aggro {
        radius = ChaosFanaticCombat.ATTACK_RANGE
        alwaysAggro()
    }
    stats {
        hitpoints = 2250
        attack = 1
        strength = 1
        defence = 220
        magic = 200
        ranged = 1
    }
    bonuses {
        attackRanged = 75
        rangedStrengthBonus = -20
        defenceStab = 260
        defenceSlash = 260
        defenceCrush = 250
        defenceMagic = 280
        // OSRS "dstandard" (rev 667 has one ranged defence; brothers.plugin.kts / BreachMonsters precedent).
        defenceRanged = 50
    }
    anims {
        attack = ChaosFanaticCombat.ANIM_ATTACK
        block = ChaosFanaticCombat.ANIM_DEFEND
        death = ChaosFanaticCombat.ANIM_DEATH
    }
    // N522 fix 3: OSRS Wiki "Chaos Fanatic" infobox: slayxp 253 (Krystilia boss task; 0 XP before).
    slayer { experience = 253.0 }
}

// ------------------------------------------------------------------ Crazy archaeologist (OSRS Wiki infobox, id 6618)

set_combat_def(npc = Npcs.CRAZY_ARCHAEOLOGIST) {
    configs {
            waitFullDeathAnimation = true
        attackSpeed = 3
        attackStyle = StyleType.RANGED
        respawnDelay = 15
    }
    aggro {
        radius = CrazyArchaeologistCombat.ATTACK_RANGE
        alwaysAggro()
    }
    stats {
        hitpoints = 2250
        attack = 160
        strength = 90
        defence = 240
        magic = 1
        ranged = 180
    }
    bonuses {
        attackBonus = 250
        strengthBonus = 25
        attackRanged = 75
        rangedStrengthBonus = -15
        defenceStab = 5
        defenceSlash = 5
        defenceCrush = 30
        defenceMagic = 250
        defenceRanged = 250
    }
    anims {
        attack = CrazyArchaeologistCombat.ANIM_THROW
        block = CrazyArchaeologistCombat.ANIM_DEFEND
        death = CrazyArchaeologistCombat.ANIM_DEATH
    }
    // N522 fix 3: OSRS Wiki "Crazy archaeologist" infobox: slayxp 275 (Krystilia boss task; 0 XP before).
    slayer { experience = 275.0 }
}

on_npc_combat(*ChaosFanaticCombat.ids) {
    npc.queue {
        ChaosFanaticCombat.handleSpecialCombat(this)
    }
}

on_npc_combat(*CrazyArchaeologistCombat.ids) {
    npc.queue {
        CrazyArchaeologistCombat.handleSpecialCombat(this)
    }
}

/** OSRS Wiki "Transcript:Crazy archaeologist": "When dying - Ow!". */
on_npc_pre_death(Npcs.CRAZY_ARCHAEOLOGIST) {
    npc.forceChat(CrazyArchaeologistCombat.SHOUT_DEATH)
}

// Hit / death sounds: OSRS human_hit / human_death (the imported OSRS synths the breach humans use); attacks play their own sounds.
listOf(Npcs.CHAOS_FANATIC to "Chaos Fanatic", Npcs.CRAZY_ARCHAEOLOGIST to "Crazy archaeologist").forEach { (id, name) ->
    NpcCombatAudio.register(NpcCombatAudio.Row(id = id, name = name, defend = BreachMonsters.SFX_HUMAN_HIT, death = BreachMonsters.SFX_HUMAN_DEATH))
}

// ------------------------------------------------------------------ spawns (OSRS Wiki LocLine)

spawn_npc(npc = Npcs.CHAOS_FANATIC, x = 2979, z = 3846, walkRadius = 5, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.CRAZY_ARCHAEOLOGIST, x = 2977, z = 3702, walkRadius = 5, direction = Direction.SOUTH)
// OSRS Wiki "Miniature chaotic clouds" (6620): non-interactive scenery that wanders round the Chaos Fanatic, map (2992,3843).
spawn_npc(npc = Npcs.MINIATURE_CHAOTIC_CLOUDS, x = 2992, z = 3843, walkRadius = 8, direction = Direction.SOUTH)

// ------------------------------------------------------------------ drops (OSRS Wiki drop tables, 2026-11-01)
// Each named table is rolled on every kill and every hit is kept (DropTableFactory "all" semantics, chaos_elemental_drops precedent).
// Not ported: Brimstone key (Konar tasks only - no Konar here) and the Wilderness Slayer tertiaries (no Krystilia boss tasks here).

val chaosFanaticDrops =
    DropTableFactory.build {
        guaranteed {
            obj(Items.BONES)
        }
        table("pre_roll") {
            total(256)
            obj(Items.ODIUM_SHARD_1, slots = 1)
            obj(Items.MALEDICTION_SHARD_1, slots = 1)
            nothing(254)
        }
        main {
            total(128)
            // weapons and armour
            obj(Items.BATTLESTAFF_NOTED, quantity = 5, slots = 5)
            obj(Items.SPLITBARK_BODY, slots = 5)
            obj(Items.SPLITBARK_LEGS, slots = 5)
            // Zamorak monk top + bottom (667 "Zamorak robe" 1035 / 1033) are dropped together.
            objs(Item(Items.ZAMORAK_ROBE_1035), Item(Items.ZAMORAK_ROBE), slots = 4)
            obj(Items.ANCIENT_STAFF, slots = 1)
            // runes
            obj(Items.FIRE_RUNE, quantity = 250, slots = 4)
            obj(Items.SMOKE_RUNE, quantity = 30, slots = 4)
            obj(Items.CHAOS_RUNE, quantity = 175, slots = 4)
            obj(Items.BLOOD_RUNE, quantity = 50, slots = 4)
            // consumables
            obj(Items.MONKFISH, quantity = 3, slots = 8)
            obj(Items.SHARK, slots = 8)
            obj(Items.PRAYER_POTION_4, slots = 8)
            obj(Items.ANCHOVY_PIZZA_NOTED, quantity = 8, slots = 4)
            // other
            obj(Items.COINS_995, 499..3998, slots = 18)
            obj(Items.GRIMY_LANTADYME_NOTED, quantity = 4, slots = 8)
            obj(Items.RING_OF_LIFE, slots = 7)
            obj(Items.CHAOS_TALISMAN, slots = 6)
            obj(Items.WINE_OF_ZAMORAK_NOTED, quantity = 10, slots = 6)
            objs(Item(Items.UNCUT_EMERALD_NOTED, 6), Item(Items.UNCUT_SAPPHIRE_NOTED, 4), slots = 5)
            obj(Items.SINISTER_KEY, slots = 4)
            obj(Items.PURE_ESSENCE_NOTED, quantity = 250, slots = 2)
            // rare and gem drop tables
            table(Rare.rareTable, slots = 4)
            table(Gems.gemTable, slots = 4)
        }
        // N424 / N561 (owner 2026-10-29 + 2026-11-04): no looting bag - it is out of the game (OSRS: 1/3 here).
        table("hard_clue") {
            val denominator = WildernessBossClues.denominator(player, 128)
            total(denominator)
            obj(Items.CLUE_SCROLL_HARD, slots = 1)
            nothing(denominator - 1)
        }
    }

DropTableFactory.register(chaosFanaticDrops, Npcs.CHAOS_FANATIC)

val crazyArchaeologistDrops =
    DropTableFactory.build {
        guaranteed {
            obj(Items.BONES)
        }
        table("pre_roll") {
            total(256)
            obj(Items.ODIUM_SHARD_2, slots = 1)
            obj(Items.MALEDICTION_SHARD_2, slots = 1)
            nothing(254)
        }
        main {
            total(128)
            // weapons and armour
            obj(Items.AMULET_OF_POWER, slots = 7)
            obj(Items.RUNE_CROSSBOW, quantity = 2, slots = 5)
            obj(Items.RED_DHIDE_BODY, slots = 4)
            obj(Items.RUNE_KNIFE, quantity = 10, slots = 4)
            // runes and ammunition (OSRS "Steel cannonball" = 667 "Cannonball" 2)
            obj(Items.MUD_RUNE, quantity = 30, slots = 4)
            obj(Items.CANNONBALL, quantity = 150, slots = 4)
            obj(Items.DRAGON_ARROW, quantity = 75, slots = 1)
            // consumables
            obj(Items.SHARK, slots = 8)
            obj(Items.POTATO_WITH_CHEESE, quantity = 3, slots = 8)
            obj(Items.PRAYER_POTION_4, slots = 8)
            obj(Items.ANCHOVY_PIZZA_NOTED, quantity = 8, slots = 4)
            // other
            obj(Items.COINS_995, 499..3998, slots = 18)
            obj(Items.GRIMY_DWARF_WEED_NOTED, quantity = 4, slots = 8)
            obj(Items.WHITE_BERRIES_NOTED, quantity = 10, slots = 6)
            obj(Items.SILVER_ORE_NOTED, quantity = 40, slots = 6)
            objs(Item(Items.UNCUT_EMERALD_NOTED, 6), Item(Items.UNCUT_SAPPHIRE_NOTED, 4), slots = 5)
            obj(Items.RED_DRAGONHIDE_NOTED, quantity = 10, slots = 5)
            obj(Items.RUSTY_SWORD, slots = 4)
            obj(Items.MUDDY_KEY, slots = 4)
            obj(Items.ONYX_BOLT_TIPS, quantity = 12, slots = 4)
            obj(Items.LONG_BONE, slots = 2)
            // rare and gem drop tables
            table(Rare.rareTable, slots = 4)
            table(Gems.gemTable, slots = 4)
            nothing(1) // the OSRS Wiki table sums to 127/128
        }
        // N424 / N561 (owner 2026-10-29 + 2026-11-04): no looting bag - it is out of the game (OSRS: 1/3 here).
        table("tertiary") {
            total(128)
            obj(Items.FEDORA, slots = 1)
            nothing(127)
        }
        table("hard_clue") {
            val denominator = WildernessBossClues.denominator(player, 128)
            total(denominator)
            obj(Items.CLUE_SCROLL_HARD, slots = 1)
            nothing(denominator - 1)
        }
    }

DropTableFactory.register(crazyArchaeologistDrops, Npcs.CRAZY_ARCHAEOLOGIST)

on_npc_death(Npcs.CHAOS_FANATIC, Npcs.CRAZY_ARCHAEOLOGIST) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}

// Fedora (Crazy archaeologist drop): the worn option "Tip" plays OSRS FEDORA_TIP 2418, imported as 16220 (fedora-tip, tx-20261102-105038).
on_equipment_option(item = Items.FEDORA, option = "tip") {
    player.animate(16220)
}
