package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems

/**
 * Kalphite Queen - see `KalphiteQueenCombatScript.kt` for combat-stat sourcing and the front/back
 * form identification method (this cache's own headicon field, not a guess).
 *
 * World placement (SOURCE_BLOCKED, deliberately not guessed): the only cache placement anywhere
 * named "Kalphite Queen" (`ObjectPlacementProbeTool ... name kalphite`, single hit across all 1765
 * regions) is object 15490 at tile 1763,4937,0, region 6989 - but that region also contains
 * "Natural historian", "Orlando Smith", and a dozen other exhibit-named objects (Dragon display,
 * Wyvern display, Mole display, Terrorbird display, ...), i.e. this is the **Varrock Museum**, and
 * 15490 is a static display exhibit of her, not her lair. Her real lair is "north-east of Bedabin
 * Camp" per the OSRS Wiki, west of Shantay Pass (cache-confirmed at 3303,3116) - but nothing in this
 * cache is named "Kalphite"/"Bedabin"/"lair" near there, so the exact spawn tile is not derivable
 * from cache text search alone (would need a manual region-by-region terrain walk of the desert,
 * out of scope for this pass). She is fully implemented (combat, both forms, transformation,
 * drops) and reachable for retest via the existing `::add_npc`/spawnnpc admin command
 * (Npcs.KALPHITE_QUEEN = 1158) - the same documented pattern already used for other npcs with no
 * confirmed world spawn yet (see the comment in `guard_level_21.plugin.kts`). Queued follow-up: find
 * her real spawn tile via a terrain walk and add a `spawn_npc` entry.
 */
val FIRST_FORM = Npcs.KALPHITE_QUEEN // 1158 - crawling, magic/ranged-dominant
val TRANSITION = Npcs.KALPHITE_QUEEN_1159 // 1159 - ~20-tick legs-shedding frame, not attackable
val SECOND_FORM = Npcs.KALPHITE_QUEEN_1160 // 1160 - airborne, melee-dominant

/** 20 game ticks (12 seconds): OSRS Wiki-stated duration of the legs-shedding transformation. */
val TRANSFORM_TICKS = 20

/** 2000 game ticks (20 minutes): OSRS Wiki-stated duration the second form can survive without
 * dying before reverting to the first form, keeping its remaining HP. */
val REVERT_TICKS = 2000

/** 50 game ticks (30 seconds): OSRS Wiki infobox respawn time, applied once the second (true kill)
 * form actually dies, before a fresh first form appears. */
val RESPAWN_TICKS = 50

private val TRANSFORM_TIMER =
    TimerKey(persistenceKey = "kalphite_queen_transform", tickOffline = false, resetOnDeath = false)
private val REVERT_TIMER =
    TimerKey(persistenceKey = "kalphite_queen_revert", tickOffline = false, resetOnDeath = true)
private val RESPAWN_TIMER =
    TimerKey(persistenceKey = "kalphite_queen_respawn", tickOffline = false, resetOnDeath = false)

val table = DropTableFactory
val drops =
    table.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }

        // Consumables (OSRS Wiki: 1/9 each). Dark crab, Super combat potion(2) and
        // Superantipoison(2) are not present anywhere in this cache's items.yml (checked directly,
        // not guessed) so are omitted rather than substituted with an unsourced id.
        table("Consumables") {
            total(6)
            obj(Items.MONKFISH, quantity = 3, slots = 1)
            obj(Items.SHARK, quantity = 2, slots = 1)
            obj(Items.SARADOMIN_BREW_4, quantity = 1, slots = 1)
            obj(Items.PRAYER_POTION_4, quantity = 2, slots = 1)
            obj(Items.SUPER_RESTORE_4, quantity = 1, slots = 1)
            obj(Items.RANGING_POTION_3, quantity = 1, slots = 1)
        }

        // Main table, OSRS Wiki rates converted to their own stated /126 denominator.
        main {
            total(126)
            obj(Items.BATTLESTAFF_NOTED, quantity = 10, slots = 5)
            obj(Items.RUNE_CHAINBODY, quantity = 1, slots = 4)
            obj(Items.RED_DHIDE_BODY, quantity = 1, slots = 4)
            obj(Items.LAVA_BATTLESTAFF, quantity = 1, slots = 2)
            obj(Items.DEATH_RUNE, quantity = 150, slots = 6)
            obj(Items.BLOOD_RUNE, quantity = 100, slots = 6)
            obj(Items.MITHRIL_ARROW, quantity = 500, slots = 5)
            obj(Items.RUNE_ARROW, quantity = 250, slots = 3)
            obj(Items.GRIMY_TOADFLAX_NOTED, quantity = 25, slots = 2)
            obj(Items.GRIMY_SNAPDRAGON_NOTED, quantity = 25, slots = 2)
            obj(Items.GRIMY_TORSTOL_NOTED, quantity = 25, slots = 2)
            obj(Items.TORSTOL_SEED, quantity = 2, slots = 4)
            obj(Items.WATERMELON_SEED, quantity = 25, slots = 3)
            obj(Items.PAPAYA_TREE_SEED, quantity = 2, slots = 3)
            obj(Items.PALM_TREE_SEED, quantity = 2, slots = 3)
            obj(Items.MAGIC_SEED, quantity = 2, slots = 3)
            obj(Items.RUNE_BAR, quantity = 3, slots = 5)
            obj(Items.BUCKET_OF_SAND_NOTED, quantity = 100, slots = 4)
            obj(Items.GOLD_ORE_NOTED, quantity = 250, slots = 4)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 60, slots = 4)
            obj(Items.UNCUT_EMERALD_NOTED, quantity = 25, slots = 3)
            obj(Items.UNCUT_RUBY_NOTED, quantity = 25, slots = 3)
            obj(Items.UNCUT_DIAMOND_NOTED, quantity = 25, slots = 3)
            obj(Items.WINE_OF_ZAMORAK_NOTED, quantity = 60, slots = 10)
            obj(Items.POTATO_CACTUS_NOTED, quantity = 100, slots = 8)
            obj(Items.COINS_995, quantityRange = 15000..20000, slots = 5)
            obj(Items.GRAPES_NOTED, quantity = 100, slots = 5)
            obj(Items.CACTUS_SPINE_NOTED, quantity = 10, slots = 3)

            table(Gems.gemTable, slots = 3)

            nothing(4)
        }

        // Rare Drop Table - the same shared, unchanged-since-classic mechanic every other boss in
        // this project already rolls into.
        table("Tertiary") {
            total(10000)
            obj(Items.CLUE_SCROLL_ELITE, slots = 100) // 1/100
            obj(Items.KQ_HEAD, slots = 78) // 1/128
            obj(Items.DRAGON_CHAINBODY, slots = 78) // 1/128
            obj(Items.DRAGON_2H_SWORD, slots = 39) // 1/256
            obj(Items.DRAGON_PICKAXE, slots = 25) // 1/400
            nothing(9680)
        }
    }

table.register(drops, SECOND_FORM)

on_npc_pre_death(SECOND_FORM) {
    val p = npc.damageMap.getMostDamage() as? Player
    p?.filterableMessage("The Kalphite Queen finally falls.")
}

/**
 * [TRANSITION] (npc 1159, no options - never a real combat target) is reused as a plain marker
 * that survives past a real npc's own removal, exactly so a [TimerKey] set on it can fire later.
 * A timer set directly on a dying npc is a no-op: [gg.rsmod.game.action.NpcDeathAction.death]
 * calls `world.remove(npc)` immediately after `on_npc_death` returns (both forms have
 * `respawnDelay = 0`, so `npc.respawns` is false and no wait/reset happens first), which stops
 * that npc from ever being ticked again.
 */
fun spawnMarker(
    world: World,
    tile: Tile,
): Npc = Npc(TRANSITION, tile, world).also { it.respawnOverride = false; world.spawn(it) }

/** Real kill: the second form's own death. Loot, then a fresh first form after the real 50-tick
 * respawn delay - not the standard engine respawn cycle, since both forms have `respawnDelay = 0`
 * (this plugin owns every transition manually; see class-level comment). */
on_npc_death(SECOND_FORM) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
    spawnMarker(world, npc.tile).timers[RESPAWN_TIMER] = RESPAWN_TICKS
}

on_timer(RESPAWN_TIMER) {
    if (npc.id == TRANSITION) {
        val tile = npc.tile
        world.remove(npc)
        world.spawn(Npc(FIRST_FORM, tile, world).also { it.respawnOverride = false })
    }
}

/** First form "death" is really the transformation trigger, not a real kill: no loot, no real
 * respawn cycle (respawnDelay = 0 on her combat def), just the ~20-tick legs-shedding frame then
 * the second form spawns with full HP. */
on_npc_death(FIRST_FORM) {
    spawnMarker(world, npc.tile).timers[TRANSFORM_TIMER] = TRANSFORM_TICKS
}

on_timer(TRANSFORM_TIMER) {
    if (npc.id == TRANSITION) {
        val tile = npc.tile
        world.remove(npc)
        world.spawn(Npc(SECOND_FORM, tile, world).also { it.respawnOverride = false })
    }
}

on_npc_spawn(SECOND_FORM) {
    npc.timers[REVERT_TIMER] = REVERT_TICKS
}

/** "If the second form survives over 20 minutes without dying, she reverts to the first form,
 * keeping the second form's remaining HP" (OSRS Wiki). [REVERT_TIMER.resetOnDeath] cancels this
 * automatically on a real kill, since the timer lives on the second form itself throughout - unlike
 * [TRANSFORM_TIMER]/[RESPAWN_TIMER] it does not need the marker-npc workaround. */
on_timer(REVERT_TIMER) {
    if (npc.id == SECOND_FORM && npc.isSpawned()) {
        val remaining = npc.getCurrentLifepoints()
        val tile = npc.tile
        world.remove(npc)
        val first = Npc(FIRST_FORM, tile, world).also { it.respawnOverride = false }
        world.spawn(first)
        first.setCurrentLifepoints(remaining.coerceAtLeast(1))
    }
}

set_combat_def(npc = FIRST_FORM) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.MAGIC
        respawnDelay = 0
    }
    stats {
        hitpoints = 2550
        attack = 300
        strength = 300
        defence = 300
        magic = 150
        ranged = 1
    }
    bonuses {
        defenceStab = 50
        defenceSlash = 50
        defenceCrush = 10
        defenceMagic = 100
        defenceRanged = 100
    }
    anims {
        // first (crawling) form: melee 6241, ranged/magic 6240. Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 6241
        block = 6232
        death = 6242
    }
    aggro {
        radius = 15
        // The Queen is aggressive; keep her active after the map-build grace period.
        alwaysAggro()
    }
}

set_combat_def(npc = SECOND_FORM) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.STAB
        respawnDelay = 0
    }
    stats {
        hitpoints = 2550
        attack = 300
        strength = 300
        defence = 300
        magic = 150
        ranged = 1
    }
    bonuses {
        defenceStab = 50
        defenceSlash = 50
        defenceCrush = 10
        defenceMagic = 100
        defenceRanged = 100
    }
    anims {
        // second (airborne) form: melee 6235, ranged/magic 6234. Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 6235
        block = 6237
        death = 6233
    }
    aggro {
        radius = 15
        // Both forms inherit the same aggressive boss behaviour.
        alwaysAggro()
    }
}
