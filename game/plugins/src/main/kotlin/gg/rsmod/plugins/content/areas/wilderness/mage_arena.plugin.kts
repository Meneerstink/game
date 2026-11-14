package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.attr.prayedAtStatue
import gg.rsmod.plugins.content.skills.agility.darkanForceMove

val humanForm = Npcs.KOLODION_907
val ogreForm = Npcs.KOLODION_908
val trollForm = Npcs.KOLODION_11301
val darkBeastForm = Npcs.KOLODION_11302
val demonForm = Npcs.KOLODION_911

on_npc_spawn(npc = humanForm) {
    npc.forceChat("You must prove yourself... now!")
    npc.graphic(Gfx.MAGEARENA_TRANSFORM_POOF, 100)
}

on_npc_spawn(npc = ogreForm) {
    npc.forceChat("This is only the beginning; you can't beat me!")
    npc.graphic(Gfx.MAGEARENA_TRANSFORM_POOF, 100)
}

on_npc_spawn(npc = trollForm) {
    npc.forceChat("Foolish mortal; I am unstoppable.")
    npc.graphic(Gfx.MAGEARENA_TRANSFORM_POOF, 100)
}

on_npc_spawn(npc = darkBeastForm) {
    npc.forceChat("Now you feel it... The dark energy.")
    npc.graphic(Gfx.MAGEARENA_TRANSFORM_POOF, 100)
}

on_npc_spawn(npc = demonForm) {
    npc.forceChat("Aaaaaaaarrgghhhh! The power!")
    npc.graphic(Gfx.MAGEARENA_TRANSFORM_POOF, 100)
}

// Each transformation belongs to one player's duel; there is no ordinary monster loot/respawn.
MageArena.FORMS.forEachIndexed { index, form ->
    on_npc_death(form.npc) { MageArena.killed(npc) }
    set_combat_def(form.npc) {
        configs {
            attackSpeed = form.speed
            spell = 503 // Magic fallback only; NpcAttacks retains all three existing god-spell sections for these ids.
            respawnDelay = 0
            completesEncounter = index == MageArena.FORMS.lastIndex
        }
        stats {
            hitpoints = form.hp * 10 // NpcCombatDsl accepts 667 authored HP units, then converts to real HP.
            attack = form.attack
            strength = form.strength
            defence = form.defence
            magic = form.magic
            ranged = 1
        }
        anims {
            val existing = listOf(Triple(811, 2888, 404), Triple(8577, 8576, 8578),
                Triple(13790, 13785, 13787), Triple(2731, 2733, 2732), Triple(64, 67, 65))[index]
            attack = existing.first
            death = existing.second
            block = existing.third
        }
        aggro { radius = 0 } // The duel explicitly targets its owner after landing/each transformation.
        if (index == MageArena.FORMS.lastIndex) {
            // OSRS Wiki Kolodion: only the final demon counts for black demon assignments,107 Slayer XP.
            species { +NpcSpecies.DEMON }
            slayer { assignment = SlayerAssignment.BLACK_DEMON; experience = 107.0 }
        }
    }
}

/**
 * Darkan Wilderness.java:124-136 (handleSparklingPool 2878 / 2879): "You step into the pool of sparkling water. You feel the energy
 * rush through your veins."; from the route tile forceMove(pool tile (2542,4720) | (2509,4687), anim 13842, 0, 60), then anim -1 and
 * tele to the other chamber (2509,4689) | (2542,4718). Replaces the Void walk-to-edge + waits + 7269 slide + splash ([start] is no
 * longer walked to: Darkan starts where the route to the pool ended). The splash sound/graphic of the Void port are kept at the end.
 */
fun jumpIntoPool(
    p: Player,
    start: Tile,
    pool: Tile,
    destination: Tile,
) {
    p.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
        p.message("You step into the pool of sparkling water. You feel the energy rush through your veins.")
        darkanForceMove(p, pool, startCycles = 0, speedCycles = 60, anim = 13842)
        p.graphic(Gfx.GIANT_WATER_SPLASH)
        p.playSound(SFX_WATER_SPLASH)
        p.animate(-1)
        p.moveTo(destination)
    }
}

/** Void mage_arena sounds: sparkling_pool_step / water_splash. */
val SFX_SPARKLING_POOL_STEP = 2467
val SFX_WATER_SPLASH = 2496

fun stepIntoPool(
    p: Player,
    obj: GameObject,
): Boolean {
    jumpIntoPool(p, start = Tile(2542, 4718, 0), pool = Tile(2542, 4720, 0), destination = Tile(2509, 4689, 0))
    return true
}

fun stepIntoPool2(
    p: Player,
    obj: GameObject,
): Boolean {
    jumpIntoPool(p, start = Tile(2509, 4689, 0), pool = Tile(2509, 4687, 0), destination = Tile(2542, 4718, 0))
    return true
}

fun prayToSaradomin(
    p: Player,
    obj: GameObject,
): Boolean {
    p.lockingQueue {
        var ticks = 0
        while (true) {
            when (ticks) {
                1 -> p.moveTo(2500, 4719, 0)
                2 -> p.faceTile(obj.tile)
                3 -> {
                    messageBox("You kneel and begin to chant to Saradomin.")
                    p.animate(Anims.ALTAR_PRAY)
                }
                4 -> wait(1)
                5 -> {
                    val floorSpawnTile = Tile(x = 2500, z = 4720)
                    val SARADOMIN_CAPE = GroundItem(Items.SARADOMIN_CAPE, 1, Tile(x = 2500, z = 4720, 0), p)
                    player.world.spawn(SARADOMIN_CAPE)
                    player.world.spawn(TileGraphic(floorSpawnTile, id = Gfx.SMALL_SMOKE_POOF, height = 0))
                }
                6 -> {
                    p.unlock()
                    break
                }
            }
            ticks++
            wait(1)
        }
    }
    return true
}

fun prayToGuthix(
    p: Player,
    obj: GameObject,
): Boolean {
    p.lockingQueue {
        var ticks = 0
        while (true) {
            when (ticks) {
                1 -> p.moveTo(2507, 4722, 0)
                2 -> p.faceTile(obj.tile)
                3 -> {
                    messageBox("You kneel and begin to chant to Guthix.")
                    p.animate(Anims.ALTAR_PRAY)
                }
                4 -> wait(1)
                5 -> {
                    val floorSpawnTile = Tile(x = 2507, z = 4723)
                    val GUTHIX_CAPE = GroundItem(Items.GUTHIX_CAPE, 1, Tile(x = 2507, z = 4723, 0), p)
                    player.world.spawn(GUTHIX_CAPE)
                    player.world.spawn(TileGraphic(floorSpawnTile, id = Gfx.SMALL_SMOKE_POOF, height = 0))
                }
                6 -> {
                    p.unlock()
                    break
                }
            }
            ticks++
            wait(1)
        }
    }
    return true
}

fun prayToZamorak(
    p: Player,
    obj: GameObject,
): Boolean {
    p.lockingQueue {
        var ticks = 0
        while (true) {
            when (ticks) {
                1 -> p.moveTo(2516, 4719, 0)
                2 -> p.faceTile(obj.tile)
                3 -> {
                    messageBox("You kneel and begin to chant to Zamorak.")
                    p.animate(Anims.ALTAR_PRAY)
                }
                4 -> wait(1)
                5 -> {
                    val floorSpawnTile = Tile(x = 2516, z = 4720)
                    val ZAMORAK_CAPE = GroundItem(Items.ZAMORAK_CAPE, 1, Tile(x = 2516, z = 4720, 0), p)
                    player.world.spawn(ZAMORAK_CAPE)
                    player.world.spawn(TileGraphic(floorSpawnTile, id = Gfx.SMALL_SMOKE_POOF, height = 0))
                }
                6 -> {
                    p.unlock()
                    break
                }
            }
            ticks++
            wait(1)
        }
    }
    return true
}

on_obj_option(obj = Objs.STATUE_OF_GUTHIX, option = "pray at", lineOfSightDistance = 2) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile, 3, 3)
        player.queue {
            if (!MageArena.completed(player)) {
                messageBox("Guthix is disappointed in your resolve...")
                return@queue
            }
            prayToGuthix(player, obj)
            player.attr[prayedAtStatue] = true
        }
    }
}

on_obj_option(obj = Objs.STATUE_OF_ZAMORAK_2874, option = "pray at", lineOfSightDistance = 2) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile, 3, 3)
        player.queue {
            if (!MageArena.completed(player)) {
                messageBox("Zamorak is disappointed in your resolve...")
                return@queue
            }
            prayToZamorak(player, obj)
            player.attr[prayedAtStatue] = true
        }
    }
}

on_obj_option(obj = Objs.STATUE_OF_SARADOMIN_2873, option = "pray at", lineOfSightDistance = 2) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile, 3, 3)
        player.queue {
            if (!MageArena.completed(player)) {
                messageBox("Saradomin is disappointed in your resolve...")
                return@queue
            }
            prayToSaradomin(player, obj)
            player.attr[prayedAtStatue] = true
        }
    }
}

on_obj_option(obj = Objs.SPARKLING_POOL, option = "step-into", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile, 3, 3)
        player.queue {
            if (!MageArena.completed(player)) {
                messageBox("A magical force stops you from stepping into the pool.")
                return@queue
            }
            stepIntoPool(player, obj)
        }
    }
}

on_obj_option(obj = Objs.SPARKLING_POOL_2879, option = "step-into", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile, 3, 3)
        player.queue {
            if (!MageArena.completed(player)) {
                messageBox("A magical force stops you from stepping into the pool.")
                return@queue
            }
            stepIntoPool2(player, obj)
        }
    }
}
