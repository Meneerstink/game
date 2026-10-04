package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.attr.COMPLETED_MAGE_ARENA
import gg.rsmod.game.model.attr.prayedAtStatue
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport

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

val ids = intArrayOf(Npcs.KOLODION_907)

val table = DropTableFactory
val wizard =
    table.build {
        guaranteed {
            obj(Items.BONES)
        }
    }

table.register(wizard, *ids)

on_npc_death(humanForm) {
    world.spawn(Npc(ogreForm, npc.tile, world))
    npc.graphic(Gfx.MAGEARENA_TELEPORT_BALL, 100)
}

on_npc_death(ogreForm) {
    world.spawn(Npc(trollForm, npc.tile, world))
}

on_npc_death(trollForm) {
    world.spawn(Npc(darkBeastForm, npc.tile, world))
}

on_npc_death(darkBeastForm) {
    world.spawn(Npc(demonForm, npc.tile, world))
}

on_npc_death(demonForm) {
    val p = npc.killer() ?: return@on_npc_death
    val playerSpawnTile = Tile(x = 2541, z = 4715)
    p.teleport(playerSpawnTile, TeleportType.MODERN)
    p.attr[COMPLETED_MAGE_ARENA] = true
}

set_combat_def(humanForm) {
    configs {
        attackSpeed = 7
        spell = 503
        respawnDelay = 10
    }
    stats {
        hitpoints = 30
        attack = 0
        strength = 0
        defence = 0
        magic = 60
        ranged = 0
    }
    bonuses {
        attackStab = 0
        attackCrush = 0
        defenceStab = 99999
        defenceSlash = 9999
        defenceCrush = 9999
        defenceMagic = 0
        defenceRanged = 9999
    }
    anims {
        attack = 811
        death = 2888
        block = 404
    }
    aggro {
        radius = 10
    }
}

set_combat_def(ogreForm) {
    configs {
        attackSpeed = 7
        respawnDelay = 0
    }
    stats {
        hitpoints = 650
        attack = 85
        strength = 98
        defence = 105
        magic = 80
        ranged = 1
    }
    bonuses {
        attackStab = 0
        attackCrush = 0
        defenceStab = 99999
        defenceSlash = 9999
        defenceCrush = 9999
        defenceMagic = 0
        defenceRanged = 9999
    }
    anims {
        attack = 8577
        death = 8576
        block = 8578
    }
    aggro {
        radius = 10
    }
}

set_combat_def(trollForm) {
    configs {
        attackSpeed = 7
        respawnDelay = 0
    }
    stats {
        hitpoints = 650
        attack = 85
        strength = 98
        defence = 105
        magic = 80
        ranged = 1
    }
    bonuses {
        attackStab = 0
        attackCrush = 0
        defenceStab = 99999
        defenceSlash = 9999
        defenceCrush = 9999
        defenceMagic = 0
        defenceRanged = 9999
    }
    anims {
        attack = 13790
        death = 13785
        block = 13787
    }
    aggro {
        radius = 10
    }
}

set_combat_def(darkBeastForm) {
    configs {
        attackSpeed = 7
        respawnDelay = 0
    }
    stats {
        hitpoints = 780
        attack = 85
        strength = 98
        defence = 105
        magic = 80
        ranged = 1
    }
    bonuses {
        attackStab = 0
        attackCrush = 0
        defenceStab = 99999
        defenceSlash = 9999
        defenceCrush = 9999
        defenceMagic = 0
        defenceRanged = 9999
    }
    anims {
        attack = 2731
        death = 2733
        block = 2732
    }
    aggro {
        radius = 10
    }
}

set_combat_def(demonForm) {
    configs {
        attackSpeed = 4
        respawnDelay = 0
    }
    stats {
        hitpoints = 1070
        attack = 85
        strength = 98
        defence = 105
        magic = 80
        ranged = 1
    }
    bonuses {
        attackStab = 0
        attackCrush = 0
        defenceStab = 99999
        defenceSlash = 9999
        defenceCrush = 9999
        defenceMagic = 0
        defenceRanged = 9999
    }
    anims {
        attack = 64
        death = 67
        block = 65
    }
    aggro {
        radius = 10
    }
}

/**
 * Void MageArenaChamber `jumpIn`: walk to the pool's edge (with arrival), face the pool, step sound, the jump animation sliding
 * into the water, splash, then out at the other pool. The port teleported three times (onto the edge from wherever the click
 * ended, into the pool, out) - audit 2026-10-04 section 7. Chamber edge 2509,4689 as Void (ours had 2507,4687).
 */
fun jumpIntoPool(
    p: Player,
    start: Tile,
    pool: Tile,
    destination: Tile,
) {
    p.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
        p.message("You step into the pool of sparkling water. You feel the energy rush through your veins.")
        try {
            gg.rsmod.plugins.content.skills.agility.walkToTileFrom(this, p, start)
        } catch (e: gg.rsmod.plugins.content.skills.agility.ObstacleApproachFailed) {
            p.message("I can't reach that!")
            return@lockingQueue
        }
        wait(1)
        p.faceTile(pool)
        wait(1)
        p.playSound(SFX_SPARKLING_POOL_STEP)
        val distance = p.tile.getDistance(pool)
        p.animate(Anims.MA_JUMP_POOL)
        gg.rsmod.plugins.content.skills.agility.exactMoveFrom(this, p, pool, delay = distance * 30)
        if (distance > 1) wait(distance - 1)
        p.graphic(Gfx.GIANT_WATER_SPLASH)
        p.playSound(SFX_WATER_SPLASH)
        wait(2)
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
                    val SARADOMIN_CAPE = GroundItem(Items.SARADOMIN_CAPE, 1, Tile(x = 2500, z = 4720, 0))
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
                    val GUTHIX_CAPE = GroundItem(Items.GUTHIX_CAPE, 1, Tile(x = 2507, z = 4723, 0))
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
                    val ZAMORAK_CAPE = GroundItem(Items.ZAMORAK_CAPE, 1, Tile(x = 2516, z = 4720, 0))
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
            if (!player.attr.has(COMPLETED_MAGE_ARENA)) {
                messageBox("Guthix is disappointed in your resolve...")
                return@queue
            }
            if (player.hasItem(Items.GUTHIX_CAPE)) {
                messageBox("You have already received a blessing from Guthix.")
                return@queue
            }
            if (player.hasItem(Items.ZAMORAK_CAPE) || player.hasItem(Items.SARADOMIN_CAPE)) {
                messageBox("You have already received a blessing from another God.")
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
            if (!player.attr.has(COMPLETED_MAGE_ARENA)) {
                messageBox("Zamorak is disappointed in your resolve...")
                return@queue
            }
            if (player.hasItem(Items.ZAMORAK_CAPE)) {
                messageBox("You have already received a blessing from Zamorak.")
                return@queue
            }
            if (player.hasItem(Items.SARADOMIN_CAPE) || player.hasItem(Items.GUTHIX_CAPE)) {
                messageBox("You have already received a blessing from another God.")
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
            if (!player.attr.has(COMPLETED_MAGE_ARENA)) {
                messageBox("Saradomin is disappointed in your resolve...")
                return@queue
            }
            if (player.hasItem(Items.SARADOMIN_CAPE)) {
                messageBox("You have already received a blessing from Guthix.")
                return@queue
            }
            if (player.hasItem(Items.ZAMORAK_CAPE) || player.hasItem(Items.GUTHIX_CAPE)) {
                messageBox("You have already received a blessing from another God.")
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
            if (!player.attr.has(COMPLETED_MAGE_ARENA)) {
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
            if (!player.attr.has(COMPLETED_MAGE_ARENA)) {
                messageBox("A magical force stops you from stepping into the pool.")
                return@queue
            }
            stepIntoPool2(player, obj)
        }
    }
}
