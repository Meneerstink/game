package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.audio.NpcCombatAudio
import gg.rsmod.plugins.content.combat.removeCombatTarget
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * N522 bundle 2 (owner 2026-11-01 "alle osrs wilderness bosses met hun locations in de wilderness volledig"): Scorpia, her offspring and
 * guardians in the OSRS Scorpion Pit (map square 12961, imported with OsrsRegionImportTool batch scorpia-pit, tx-20261101-202403).
 * Stats, drops: OSRS Wiki infoboxes and drop table (2026-11-01; hitpoints here are real HP x10, the DSL rule). Cave links and the 28
 * spawns: Kronos-184 (team/dossiers/N522-SCORPIA-DONOR.txt). Fight: [ScorpiaCombat]. The pit is multi-combat (OSRS; multi.yml 12961).
 */

// ------------------------------------------------------------------ Scorpia (OSRS Wiki infobox, id 6615)

set_combat_def(npc = Npcs.SCORPIA) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 14
    }
    aggro {
        radius = 8
        alwaysAggro()
    }
    stats {
        hitpoints = 2000
        attack = 250
        strength = 150
        defence = 180
        magic = 1
        ranged = 1
    }
    bonuses {
        attackBonus = 60
        defenceStab = 246
        defenceSlash = 284
        defenceCrush = 284
        defenceMagic = 44
        defenceRanged = 284
    }
    anims {
        attack = ScorpiaCombat.ANIM_ATTACK
        block = ScorpiaCombat.ANIM_DEFEND
        death = ScorpiaCombat.ANIM_DEATH
    }
}

// ------------------------------------------------------------------ Scorpia's offspring (OSRS Wiki infobox, id 6616)

set_combat_def(npc = Npcs.SCORPIAS_OFFSPRING) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.RANGED
        respawnDelay = 5
    }
    aggro {
        radius = 4
        alwaysAggro()
    }
    stats {
        hitpoints = 20
        attack = 1
        strength = 1
        defence = 1
        magic = 1
        ranged = 30
    }
    bonuses {
        attackRanged = 900
        rangedStrengthBonus = -30
        defenceMagic = -40
    }
    anims {
        attack = ScorpiaCombat.ANIM_SMALL_ATTACK
        block = ScorpiaCombat.ANIM_SMALL_DEFEND
        death = ScorpiaCombat.ANIM_SMALL_DEATH
    }
}

// ------------------------------------------------------------------ Scorpia's guardian (OSRS Wiki infobox, id 6617: never attacks)

set_combat_def(npc = Npcs.SCORPIAS_GUARDIAN) {
    configs {
        // The DSL requires a speed; the guardian never swings ([ScorpiaCombat] heal loop, the no-op combat plugin below).
        attackSpeed = 4
        attackStyle = StyleType.STAB
        // "Killed guardians don't respawn" (OSRS Wiki): 0 = no respawn; the spawner also sets respawns = false.
        respawnDelay = 0
    }
    aggro {
        neverAggro()
    }
    stats {
        hitpoints = 700
        attack = 1
        strength = 1
        defence = 60
        magic = 30
        ranged = 30
    }
    anims {
        attack = ScorpiaCombat.ANIM_SMALL_ATTACK
        block = ScorpiaCombat.ANIM_SMALL_DEFEND
        death = ScorpiaCombat.ANIM_SMALL_DEATH
    }
}

on_npc_combat(*ScorpiaCombat.ids) {
    npc.queue {
        ScorpiaCombat.handleSpecialCombat(this)
    }
}

on_npc_combat(*ScorpiaCombat.Offspring.ids) {
    npc.queue {
        ScorpiaCombat.Offspring.handleSpecialCombat(this)
    }
}

/** "Attack speed: does not attack" - a guardian that is attacked keeps healing and never fights back. */
on_npc_combat(*intArrayOf(Npcs.SCORPIAS_GUARDIAN)) {
    npc.resetFacePawn()
    npc.removeCombatTarget()
}

on_npc_spawn(Npcs.SCORPIA) {
    ScorpiaCombat.reset(npc)
}

// Sounds: the OSRS scorpion synths every scorpion here already uses (data/cfg/npcs/combat-sounds.json: attack 3611, hit 3612, death 3610).
listOf(Npcs.SCORPIA to "Scorpia", Npcs.SCORPIAS_OFFSPRING to "Scorpia's offspring", Npcs.SCORPIAS_GUARDIAN to "Scorpia's guardian").forEach { (id, name) ->
    NpcCombatAudio.register(
        NpcCombatAudio.Row(id = id, name = name, attack = listOf(NpcCombatAudio.Sound(id = Sfx.SCORPION_ATTACK)), defend = Sfx.SCORPION_HIT, death = Sfx.SCORPION_DEATH),
    )
}

// ------------------------------------------------------------------ the pit (Kronos-184 spawns, OSRS Wiki Scorpia LocLine (3233,10341))

spawn_npc(npc = Npcs.SCORPIA, x = 3234, z = 10340, walkRadius = 8, direction = Direction.SOUTH)

listOf(
    3221 to 10336, 3221 to 10339, 3244 to 10333,
).forEach { (x, z) -> spawn_npc(npc = Npcs.SCORPIAS_OFFSPRING, x = x, z = z, walkRadius = 1, direction = Direction.SOUTH) }

listOf(
    3221 to 10344, 3221 to 10349, 3223 to 10333, 3223 to 10347, 3224 to 10336, 3225 to 10349, 3229 to 10333, 3230 to 10349, 3231 to 10344,
    3233 to 10333, 3233 to 10348, 3234 to 10339, 3237 to 10333, 3237 to 10349, 3238 to 10344, 3241 to 10333, 3241 to 10349,
    3242 to 10336, 3242 to 10347, 3244 to 10349, 3244 to 10337, 3244 to 10340, 3244 to 10344, 3244 to 10347,
).forEach { (x, z) -> spawn_npc(npc = Npcs.SCORPIAS_OFFSPRING, x = x, z = z, walkRadius = 2, direction = Direction.SOUTH) }

// ------------------------------------------------------------------ cave entrances (Kronos-184: anim 2796 HUMAN_LONGCRAWL, 2 ticks)

/** OSRS loc 26762 "Cavern" (imported as [ScorpiaPit.CAVERN]) on the surface, placed by the server: (tile, rotation) -> pit arrival. */
ScorpiaPit.CAVERNS.forEach { c -> spawn_obj(obj = ScorpiaPit.CAVERN, x = c.tile.x, z = c.tile.z, type = 10, rot = c.rot) }

on_obj_option(obj = ScorpiaPit.CAVERN, option = "enter") {
    val dest = ScorpiaPit.cavernExit(player.getInteractingGameObj().tile) ?: return@on_obj_option
    ScorpiaPit.crawl(player, dest)
}

on_obj_option(obj = ScorpiaPit.CREVICE, option = "use") {
    val dest = ScorpiaPit.creviceExit(player.getInteractingGameObj().tile) ?: return@on_obj_option
    ScorpiaPit.crawl(player, dest)
}

// ------------------------------------------------------------------ drops (OSRS Wiki drop tables, 2026-11-01)
// Not ported: Ensouled scorpion head (no Arceuus magic here), Wilderness crabs teleport (2/128 - no such tablet or destination here;
// left as nothing so every other rate stays the wiki rate), Brimstone key (Konar only), Wilderness Slayer tertiaries (no Krystilia).

val scorpiaDrops =
    DropTableFactory.build {
        table("pre_roll") {
            total(256)
            obj(Items.ODIUM_SHARD_3, slots = 1)
            obj(Items.MALEDICTION_SHARD_3, slots = 1)
            nothing(254)
        }
        main {
            total(128)
            // weapons and armour
            obj(Items.BATTLESTAFF_NOTED, 5..8, slots = 6)
            obj(Items.RUNE_2H_SWORD, slots = 5)
            obj(Items.RUNE_PICKAXE, slots = 5)
            obj(Items.RUNE_KITESHIELD, slots = 5)
            obj(Items.RUNE_CHAINBODY, slots = 4)
            obj(Items.RUNE_PLATELEGS, slots = 4)
            obj(Items.RUNE_SCIMITAR, slots = 4)
            obj(Items.RUNE_WARHAMMER, slots = 4)
            obj(Items.MYSTIC_EARTH_STAFF, slots = 4)
            obj(Items.MYSTIC_ROBE_TOP, slots = 1)
            obj(Items.MYSTIC_ROBE_BOTTOM, slots = 1)
            obj(Items.DRAGON_SCIMITAR, slots = 1)
            obj(Items.DRAGON_2H_SWORD, slots = 1)
            // runes
            obj(Items.DEATH_RUNE, 100..150, slots = 8)
            obj(Items.BLOOD_RUNE, 100..150, slots = 8)
            obj(Items.CHAOS_RUNE, 150..200, slots = 8)
            // herbs
            obj(Items.GRIMY_KWUARM_NOTED, 10..15, slots = 5)
            obj(Items.GRIMY_DWARF_WEED_NOTED, 10..15, slots = 5)
            obj(Items.GRIMY_TORSTOL_NOTED, 10..15, slots = 5)
            obj(Items.GRIMY_SNAPDRAGON_NOTED, 4..7, slots = 5)
            // materials
            obj(Items.UNCUT_RUBY_NOTED, 15..20, slots = 6)
            obj(Items.UNCUT_DIAMOND_NOTED, 10..15, slots = 4)
            obj(Items.RUNITE_ORE_NOTED, quantity = 3, slots = 4)
            obj(Items.DRAGON_JAVELIN_TIPS, 30..50, slots = 4)
            obj(Items.ONYX_BOLT_TIPS, 6..10, slots = 2)
            // other
            obj(Items.COINS_995, 25002..34962, slots = 7)
            obj(Items.BLIGHTED_ANGLERFISH_NOTED, 15..25, slots = 5)
            obj(Items.BLIGHTED_SUPER_RESTORE4_NOTED, quantity = 5, slots = 5)
            nothing(2) // Wilderness crabs teleport (not in this game)
        }
        table("looting_bag") {
            total(3)
            obj(Items.LOOTING_BAG, slots = 1)
            nothing(2)
        }
        table("hard_clue") {
            total(100)
            obj(Items.CLUE_SCROLL_HARD, slots = 1)
            nothing(99)
        }
    }

DropTableFactory.register(scorpiaDrops, Npcs.SCORPIA)

// Scorpia's offspring: Looting bag 1/10 in the Wilderness (OSRS Wiki). Not ported: Ensouled scorpion head, beginner clue (no beginner
// clues here), Krystilia tertiaries.
val offspringDrops =
    DropTableFactory.build {
        table("looting_bag") {
            total(10)
            obj(Items.LOOTING_BAG, slots = 1)
            nothing(9)
        }
    }

DropTableFactory.register(offspringDrops, Npcs.SCORPIAS_OFFSPRING)

// Wiki Scorpia's guardian: killing a guardian manually can give a looting bag (1/3).
DropTableFactory.register(DropTableFactory.build {
    table("looting_bag") {
        total(3)
        obj(Items.LOOTING_BAG, slots = 1)
        nothing(2)
    }
}, Npcs.SCORPIAS_GUARDIAN)

on_npc_death(Npcs.SCORPIA, Npcs.SCORPIAS_OFFSPRING, Npcs.SCORPIAS_GUARDIAN) {
    if (npc.id == Npcs.SCORPIA) ScorpiaCombat.removeGuardians(npc)
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    val items = DropTableFactory.getDrop(killer, npc.id, rate = DropTableFactory.killRate(killer)).orEmpty()
    DropTableFactory.giveDrop(world, killer, npc.id, npc.tile, items)
    if (npc.id == Npcs.SCORPIA && items.none { it.id == Items.ODIUM_SHARD_3 || it.id == Items.MALEDICTION_SHARD_3 }) {
        val pets = gg.rsmod.plugins.content.skills.summoning.Pets
        val info = pets.dropFor(npc.id)
        if (info != null && world.randomDouble() < (DropTableFactory.killRate(killer) / info.rateFor(npc.id)).coerceAtMost(1.0)) {
            pets.award(killer, info.pet, source = gg.rsmod.plugins.content.quests.tab.DropSources.forNpc(killer, npc.id)?.key)
        }
    }
}
