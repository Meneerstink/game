package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * N522 bundle 4a: the OSRS Silk Chasm with Venenatis, the shared Wilderness boss lair fee and the Escape Caves ([WildernessLairs],
 * [VenenatisCombat]).
 */

// ------------------------------------------------------------------ the ways in and out

WildernessLairs.SURFACE.forEach { p -> spawn_obj(obj = p.id, x = p.tile.x, z = p.tile.z, type = 10, rot = p.rot) }

// The two spider lairs: entrance -> its boss arena (Silk Chasm: Venenatis, Web Chasm: Spindel).
mapOf(WildernessLairs.SILK_CHASM_ENTRANCE to VenenatisCombat.VENENATIS_ARENA, WildernessLairs.WEB_CHASM_ENTRANCE to VenenatisCombat.SPINDEL_ARENA).forEach { (entrance, arena) ->
    on_obj_option(obj = entrance, option = "enter") {
        val arrival = WildernessLairs.LAIR_ARRIVAL.getValue(entrance)
        WildernessLairs.enter(player) { WildernessLairs.crawl(player, arrival) }
    }

    // "In order to peek inside the lair, the player must have at least 20 kills registered in their name" (OSRS Wiki); the messages: donor.
    on_obj_option(obj = entrance, option = "peek") {
        val kc = runCatching { player.getNpcKillCount(arena.npc) }.getOrDefault(0)
        if (kc < 20) {
            player.message(
                "You don't know this cave well enough to glean any useful information. You feel that you'll be more succesful once " +
                    "you've killed whatever is inside, ${20 - kc} times.",
            )
            return@on_obj_option
        }
        var count = 0
        world.players.forEach { if (arena.inChasm(it.tile)) count++ }
        player.message(
            if (count > 0) "You peek into the darkness and can make out some movement. There is activity inside."
            else "You peek into the darkness and can make out no movement. There is no activity inside.",
        )
    }
}

on_obj_option(obj = WildernessLairs.WEB_CHASM_EXIT, option = "exit") {
    WildernessLairs.crawl(player, WildernessLairs.WEB_CHASM_SURFACE_EXIT)
}

listOf(WildernessLairs.SILK_CHASM_ENTRANCE, WildernessLairs.WEB_CHASM_ENTRANCE, WildernessLairs.ESCAPE_CAVE_SURFACE).forEach { id ->
    on_obj_option(obj = id, option = "check-fee") {
        player.queue { itemMessageBox(WildernessLairs.checkFeeText(player), item = Items.COINS_995, amountOrZoom = 100) } // the 100 pile fits the box
    }
}

// The lair's exit: one of the three Escape Caves points (OSRS Wiki "Silk Chasm").
on_obj_option(obj = WildernessLairs.SILK_CHASM_EXIT, option = "exit") {
    val dest =
        if (WildernessLairs.ESCAPE_CAVES_OPEN) WildernessLairs.lairExitPoint(WildernessLairs.SILK_CHASM_EXIT, world.currentCycle) { world.random(WildernessLairs.LAIR_EXIT_POINTS.size - 1) }
        else WildernessLairs.SILK_CHASM_SURFACE_EXIT
    WildernessLairs.crawl(player, dest)
}

// The Escape Caves from the surface: the fee, then the prayer drain ("This debuff is not present if the player enters the caves via
// boss lairs" - OSRS Wiki "Escape Caves").
on_obj_option(obj = WildernessLairs.ESCAPE_CAVE_SURFACE, option = "enter") {
    val route = WildernessLairs.surfaceRoute(player.getInteractingGameObj().tile) ?: return@on_obj_option
    WildernessLairs.enter(player) {
        WildernessLairs.crawl(player, route.arrival) { player.attr[WildernessLairs.ESCAPE_DRAIN_TICKS] = 0 }
    }
}

listOf(WildernessLairs.ESCAPE_EXIT_1 to "exit", WildernessLairs.ESCAPE_EXIT_2 to "exit", WildernessLairs.ESCAPE_EXIT_3 to "climb-up").forEach { (id, option) ->
    on_obj_option(obj = id, option = option) {
        val route = WildernessLairs.exitRoute(player.getInteractingGameObj().tile) ?: return@on_obj_option
        player.attr.remove(WildernessLairs.ESCAPE_DRAIN_TICKS)
        WildernessLairs.crawl(player, WildernessLairs.surfaceLanding(route))
    }
}

// ------------------------------------------------------------------ Venenatis (OSRS Wiki infobox, id 6610)

set_combat_def(npc = VenenatisCombat.NPC) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 10
        poisonImmune = true
        venomImmune = true
    }
    aggro {
        radius = VenenatisCombat.REACH
        alwaysAggro()
    }
    stats {
        hitpoints = 8500
        attack = 300
        strength = 200
        defence = 321
        magic = 300
        ranged = 350
    }
    bonuses {
        defenceStab = 100
        defenceSlash = 100
        defenceCrush = 10
        defenceMagic = 300
        defenceRanged = 150
    }
    anims {
        attack = VenenatisCombat.ANIM_MELEE
        death = VenenatisCombat.ANIM_DEATH
    }
}

spawn_npc(npc = VenenatisCombat.NPC, x = VenenatisCombat.SPAWN.x, z = VenenatisCombat.SPAWN.z, height = VenenatisCombat.SPAWN.height, walkRadius = 0)

listOf(VenenatisCombat.NPC, VenenatisCombat.SPINDEL).forEach { boss -> on_npc_spawn(boss) { VenenatisCombat.onSpawn(npc) } }

on_npc_combat(*VenenatisCombat.Combat.ids) {
    npc.queue { VenenatisCombat.Combat.handleSpecialCombat(this) }
}

// Venenatis' / Spindel's Spiderling (OSRS Wiki infoboxes, ids 12000 / 12001 - the same): 5 hitpoints, max 3, attack speed 4.
listOf(VenenatisCombat.SPIDERLING, VenenatisCombat.SPINDEL_SPIDERLING).forEach { spiderlingId -> set_combat_def(npc = spiderlingId) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = Int.MAX_VALUE
    }
    aggro {
        radius = VenenatisCombat.REACH
        alwaysAggro()
    }
    stats {
        hitpoints = 50
        attack = 15
        strength = 20
        defence = 1
        magic = 10
        ranged = 20
    }
    anims {
        attack = 16185 // SMALL_SPIDER_UPDATE_ATTACK 6249
        block = 16186 // _DEFEND 6250
        death = 16187 // _DEATH 6251
    }
} }

on_npc_combat(*VenenatisCombat.SpiderlingCombat.ids) {
    npc.queue { VenenatisCombat.SpiderlingCombat.handleSpecialCombat(this) }
}

// ------------------------------------------------------------------ drops (OSRS Wiki "Venenatis", 2026-11-02)
// The top damager gets the drop (as the other bosses here; OSRS splits a group kill by contribution). Not ported: Wilderness crabs
// teleport (3/126 - no such tablet here, left as nothing so every other rate stays the wiki rate), Brimstone key (Konar only),
// Wilderness Slayer tertiaries (no Krystilia).

val venenatisDrops =
    DropTableFactory.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }
        // "Odds of hitting the unique table ~1/56.67", at most one per kill: fangs 1/196, dragon 2h 1/256, dragon pickaxe 1/256,
        // voidwaker gem 1/360, treasonous ring 1/512 over the common denominator 1,128,960.
        table("unique") {
            total(1_128_960)
            obj(Items.FANGS_OF_VENENATIS, slots = 5760)
            obj(Items.DRAGON_2H_SWORD, slots = 4410)
            obj(Items.DRAGON_PICKAXE, slots = 4410)
            obj(Items.VOIDWAKER_GEM, slots = 3136)
            obj(Items.TREASONOUS_RING, slots = 2205)
            nothing(1_128_960 - 19_921)
        }
        main {
            total(126)
            // weapons and armour
            obj(Items.MYSTIC_AIR_STAFF_NOTED, quantity = 4, slots = 2)
            obj(Items.RUNE_PICKAXE_NOTED, quantity = 5, slots = 8)
            obj(Items.RUNE_DART, quantity = 150, slots = 3)
            obj(Items.RUNE_KNIFE, quantity = 150, slots = 5)
            obj(Items.RUNE_PLATELEGS_NOTED, quantity = 4, slots = 2)
            obj(Items.RUNE_SQ_SHIELD_NOTED, quantity = 4, slots = 2)
            obj(Items.DRAGON_DAGGER_NOTED, quantity = 6, slots = 2)
            // runes and ammunition
            obj(Items.CHAOS_RUNE, quantity = 500, slots = 7)
            obj(Items.DEATH_RUNE, quantity = 700, slots = 7)
            obj(Items.BLOOD_RUNE, quantity = 900, slots = 7)
            obj(Items.DIAMOND_BOLTS_E, quantity = 300, slots = 5)
            obj(Items.CANNONBALL, quantity = 600, slots = 4)
            // materials
            obj(Items.UNCUT_RUBY_NOTED, quantity = 50, slots = 1)
            obj(Items.UNCUT_DIAMOND_NOTED, quantity = 25, slots = 8)
            obj(Items.GOLD_ORE_NOTED, quantity = 675, slots = 6)
            obj(Items.ONYX_BOLT_TIPS, quantity = 150, slots = 5)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 225, slots = 5)
            obj(Items.LIMPWURT_ROOT_NOTED, quantity = 100, slots = 5)
            obj(Items.RED_SPIDERS_EGGS_NOTED, quantity = 500, slots = 3)
            obj(Items.UNICORN_HORN_NOTED, quantity = 225, slots = 1)
            obj(Items.UNCUT_DRAGONSTONE_NOTED, quantity = 5, slots = 2)
            obj(Items.GRIMY_RANARR_NOTED, quantity = 45, slots = 1)
            obj(Items.GRIMY_SNAPDRAGON_NOTED, quantity = 100, slots = 1)
            obj(Items.GRIMY_TOADFLAX_NOTED, quantity = 45, slots = 1)
            obj(Items.BATTLESTAFF_NOTED, quantity = 12, slots = 2)
            // other
            obj(Items.COINS_995, quantity = 50_000, slots = 10)
            obj(Items.SUPERCOMPOST_NOTED, quantity = 225, slots = 4)
            obj(Items.ANTIPOISON_4_NOTED_5953, quantity = 20, slots = 3) // OSRS "Antidote++(4)" = the 667 Antipoison++ (4)
            obj(Items.SUPER_RESTORE_4_NOTED, quantity = 10, slots = 5)
            obj(Items.DARK_CRAB_NOTED, quantity = 50, slots = 5)
            obj(Items.BLIGHTED_ANGLERFISH_NOTED, quantity = 100, slots = 1)
            nothing(3) // Wilderness crabs teleport (not in this game)
        }
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
        // N424 / N561 (owner 2026-10-29 + 2026-11-04): no looting bag - it is out of the game (OSRS: 1/3 here).
        table("strange_fruit") {
            total(5)
            obj(Items.STRANGE_FRUIT, slots = 1)
            nothing(4)
        }
        table("elite_clue") {
            total(100)
            obj(Items.CLUE_SCROLL_ELITE, slots = 1)
            nothing(99)
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
    }

DropTableFactory.register(venenatisDrops, VenenatisCombat.NPC)

on_npc_death(VenenatisCombat.NPC, VenenatisCombat.SPINDEL) {
    VenenatisCombat.onDeath(npc)
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    if (npc.id == VenenatisCombat.NPC) WildernessBossLoot.drop(npc)
    else DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
    WildernessLairs.onTopDamageKill(killer)
}

// ------------------------------------------------------------------ Spindel (N522 bundle 4b; OSRS Wiki infobox, id 11998)

set_combat_def(npc = VenenatisCombat.SPINDEL) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 9
        poisonImmune = true
        venomImmune = true
    }
    aggro {
        radius = VenenatisCombat.REACH
        alwaysAggro()
    }
    stats {
        hitpoints = 5150
        attack = 200
        strength = 130
        defence = 225
        magic = 235
        ranged = 286
    }
    bonuses {
        defenceStab = 70
        defenceSlash = 70
        defenceCrush = 10
        defenceMagic = 205
        defenceRanged = 103
    }
    anims {
        attack = VenenatisCombat.ANIM_MELEE
        death = VenenatisCombat.ANIM_DEATH
    }
}

spawn_npc(npc = VenenatisCombat.SPINDEL, x = VenenatisCombat.SPINDEL_ARENA.spawn.x, z = VenenatisCombat.SPINDEL_ARENA.spawn.z, height = VenenatisCombat.SPINDEL_ARENA.spawn.height, walkRadius = 0)

// OSRS Wiki "Spindel" drops (2026-11-02). Not ported: Dark fishing bait (1/128 - not in this game, left as nothing), Brimstone key
// (Konar only), Wilderness Slayer tertiaries. The pet (Venenatis spiderling 1/2,800) is rolled by Pets (pet_tiers.tsv).
val spindelDrops =
    DropTableFactory.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }
        // At most one unique a kill: dragon 2h 1/358, dragon pickaxe 1/358, fangs 1/618, treasonous ring 1/716, voidwaker gem 1/912
        // over the common denominator 16,814,544.
        table("unique") {
            total(16_814_544)
            obj(Items.DRAGON_2H_SWORD, slots = 46_968)
            obj(Items.DRAGON_PICKAXE, slots = 46_968)
            obj(Items.FANGS_OF_VENENATIS, slots = 27_208)
            obj(Items.TREASONOUS_RING, slots = 23_484)
            obj(Items.VOIDWAKER_GEM, slots = 18_437)
            nothing(16_814_544 - 163_065)
        }
        main {
            total(128)
            // food and potions
            obj(Items.DARK_CRAB, quantity = 8, slots = 5)
            obj(Items.SUPER_RESTORE_4, quantity = 3, slots = 5)
            // weapons and armour
            obj(Items.RUNE_KNIFE, quantity = 30, slots = 5)
            obj(Items.RUNE_PICKAXE, slots = 12)
            obj(Items.RUNE_2H_SWORD, slots = 3)
            // runes and ammunition
            obj(Items.CHAOS_RUNE, quantity = 180, slots = 7)
            obj(Items.BLOOD_RUNE, quantity = 150, slots = 7)
            obj(Items.DEATH_RUNE, quantity = 220, slots = 7)
            obj(Items.CANNONBALL, quantity = 200, slots = 4)
            obj(Items.DIAMOND_BOLTS_E, quantity = 60, slots = 5)
            // materials
            obj(Items.UNCUT_DIAMOND_NOTED, quantity = 7, slots = 8)
            obj(Items.GOLD_ORE_NOTED, quantity = 180, slots = 6)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 60, slots = 5)
            obj(Items.LIMPWURT_ROOT_NOTED, quantity = 18, slots = 5)
            obj(Items.ONYX_BOLT_TIPS, quantity = 35, slots = 5)
            obj(Items.RED_SPIDERS_EGGS_NOTED, quantity = 250, slots = 3)
            obj(Items.UNCUT_DRAGONSTONE, slots = 2)
            obj(Items.UNCUT_RUBY_NOTED, quantity = 14, slots = 1)
            obj(Items.GRIMY_SNAPDRAGON_NOTED, quantity = 40, slots = 1)
            obj(Items.UNICORN_HORN_NOTED, quantity = 60, slots = 1)
            // other
            obj(Items.COINS_995, quantity = 14_000, slots = 21)
            obj(Items.SUPERCOMPOST_NOTED, quantity = 60, slots = 4)
            obj(Items.ANTIPOISON_4_NOTED_5953, quantity = 6, slots = 3) // OSRS "Antidote++(4)" = the 667 Antipoison++ (4)
            nothing(1) // Dark fishing bait (not in this game)
            obj(Items.MAGIC_SEED, slots = 1)
            obj(Items.PALM_TREE_SEED, slots = 1)
        }
        table("supplies") {
            total(54)
            obj(Items.BLIGHTED_ANGLERFISH, 5..6, slots = 5)
            obj(Items.BLIGHTED_KARAMBWAN, 5..6, slots = 5)
            obj(Items.BLIGHTED_SUPER_RESTORE3, 3..4, slots = 5)
            obj(Items.BLIGHTED_SUPER_RESTORE4, 3..4, slots = 5)
            obj(Items.RANGING_POTION_2, 2..3, slots = 5)
            obj(Items.SUPER_COMBAT_POTION_2, 2..3, slots = 5)
            nothing(24)
        }
        // N424 / N561 (owner 2026-10-29 + 2026-11-04): no looting bag - it is out of the game (OSRS: 1/3 here).
        table("strange_fruit") {
            total(5)
            obj(Items.STRANGE_FRUIT, slots = 1)
            nothing(4)
        }
        table("elite_clue") {
            total(100)
            obj(Items.CLUE_SCROLL_ELITE, slots = 1)
            nothing(99)
        }
    }

DropTableFactory.register(spindelDrops, VenenatisCombat.SPINDEL)

// ------------------------------------------------------------------ the Escape Caves' monsters (OSRS Wiki infoboxes + LocLines)

// Grizzly bear (Escape Caves) 11989: level 83, 75 hitpoints, max 8 typeless melee, attack speed 4, aggressive.
set_combat_def(npc = Npcs.GRIZZLY_BEAR_ESCAPE_CAVES) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 25
    }
    aggro {
        radius = 6
        alwaysAggro()
    }
    stats {
        hitpoints = 750
        attack = 72
        strength = 72
        defence = 72
        magic = 1
        ranged = 1
    }
    anims {
        attack = 16188 // BEAR_REWORK_ATTACK 4925
        block = 16189 // _DEFEND 4927
        death = 16190 // _DEATH 4929
    }
}

// Poison spider (Escape Caves) 11990: level 48, 73 hitpoints, max 8 typeless ranged, attack speed 5, aggressive.
set_combat_def(npc = Npcs.POISON_SPIDER_ESCAPE_CAVES) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.RANGED
        respawnDelay = 25
    }
    aggro {
        radius = 6
        alwaysAggro()
    }
    stats {
        hitpoints = 730
        attack = 1
        strength = 1
        defence = 35
        magic = 1
        ranged = 45
    }
    bonuses {
        attackRanged = 63
        rangedStrengthBonus = 35
        defenceStab = 20
        defenceSlash = 17
        defenceCrush = 10
        defenceMagic = 14
        defenceRanged = 14
    }
    anims {
        attack = 16191 // SPIDER_UPDATE_ATTACK 5327
        block = 16192 // _DEFEND 5328
        death = 16193 // _DEATH 5329
    }
}

listOf(
    gg.rsmod.game.model.Tile(3333, 10269), gg.rsmod.game.model.Tile(3342, 10256), gg.rsmod.game.model.Tile(3346, 10292),
    gg.rsmod.game.model.Tile(3355, 10270), gg.rsmod.game.model.Tile(3363, 10247), gg.rsmod.game.model.Tile(3371, 10293),
    gg.rsmod.game.model.Tile(3376, 10256), gg.rsmod.game.model.Tile(3379, 10283),
).forEach { t -> if (WildernessLairs.ESCAPE_CAVES_OPEN) spawn_npc(npc = Npcs.GRIZZLY_BEAR_ESCAPE_CAVES, x = t.x, z = t.z, walkRadius = 4) }

listOf(
    3335 to 10264, 3336 to 10277, 3338 to 10282, 3339 to 10258, 3347 to 10251, 3349 to 10265, 3357 to 10248, 3357 to 10294,
    3358 to 10277, 3360 to 10286, 3365 to 10267, 3368 to 10248, 3369 to 10293, 3373 to 10262, 3375 to 10289, 3380 to 10261,
    3381 to 10265, 3382 to 10278,
).forEach { (x, z) -> if (WildernessLairs.ESCAPE_CAVES_OPEN) spawn_npc(npc = Npcs.POISON_SPIDER_ESCAPE_CAVES, x = x, z = z, walkRadius = 4) }

on_npc_combat(*EscapeCaveMonsters.ids) {
    npc.queue { EscapeCaveMonsters.handleSpecialCombat(this) }
}

// Test path for the test server: ::venenatis hp <hitpoints> sets the hitpoints of the Venenatis or Spindel nearest to the player.
on_command("venenatis", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val hp = args.getOrNull(1)?.toIntOrNull()
    if (args.firstOrNull() != "hp" || hp == null) return@on_command player.message("::venenatis hp <hitpoints>")
    var found: Npc? = null
    val bosses = setOf(VenenatisCombat.NPC, VenenatisCombat.SPINDEL, OldWildernessBosses.CALLISTO, OldWildernessBosses.VETION, OldWildernessBosses.VETION_REBORN, ArtioCombat.NPC, gg.rsmod.plugins.api.cfg.Npcs.WILDYWYRM)
    world.npcs.forEach { n -> if (n.id in bosses && n.tile.isWithinRadius(player.tile, 30)) found = n }
    val v = found ?: return@on_command player.message("No Venenatis, Spindel, Callisto or Vet'ion near you.")
    v.setCurrentLifepoints(hp.coerceIn(1, v.getMaximumLifepoints()))
    player.message("Boss hitpoints: ${v.getCurrentLifepoints()}.")
}
