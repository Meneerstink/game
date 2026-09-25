package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.plugins.content.activity.casino.CasinoFlower

/**
 * Mithril and Adamant seeds "Plant" (2026-09-22 item-option census: advertised, never handled - and the seeds are sold in the home
 * supplies shop, owner: "do not add unsupported/broken items").
 *
 * OSRS Wiki "Mithril seeds": the flowers grow on the planter's tile and "the player will move one adjacent square west if
 * possible; if there's an obstacle preventing movement to the west ... east first, then south, then north". Colour
 * odds out of 1,001: mixed, red, yellow, blue, orange 150 each, purple 148, assorted 100, black 2, white 1.
 * The rev-667 flower objects (2980-2988, the ids [CasinoFlower] already maps) carry no options, so the flowers are
 * scenery, as in this cache. ADAPTED: how long a patch stands is not on the page - [FLOWER_TICKS] (one minute).
 */
val FLOWER_TICKS = 100

val FLOWER_WEIGHTS =
    listOf(
        CasinoFlower.MIXED to 150, CasinoFlower.RED to 150, CasinoFlower.YELLOW to 150, CasinoFlower.BLUE to 150,
        CasinoFlower.ORANGE to 150, CasinoFlower.PURPLE to 148, CasinoFlower.ASSORTED to 100, CasinoFlower.BLACK to 2,
        CasinoFlower.WHITE to 1,
    )

fun rollFlower(roll: Int): CasinoFlower {
    var left = roll
    for ((flower, weight) in FLOWER_WEIGHTS) {
        if (left < weight) return flower
        left -= weight
    }
    return CasinoFlower.MIXED
}


/*
 * Adamant seeds (owner 2026-09-24, OSRS import 23863 <- 29458). OSRS Wiki "Adamant seeds": "the player will move one adjacent square
 * east if possible; if there's an obstacle preventing movement to the east, the flowers will attempt to move the player to the west
 * first, then south, then north" - the mirror of the Mithril seeds; same flowers and odds. Used while frozen in PvP: the move ignores
 * a bind, exactly as the seed's purpose on the wiki.
 */
val SEED_STEPS =
    mapOf(
        Items.MITHRIL_SEEDS to listOf(Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH),
        Items.ADAMANT_SEEDS to listOf(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH),
    )

SEED_STEPS.forEach { (seed, order) ->
    on_item_option(item = seed, option = "Plant") {
        val tile = player.tile
        if (world.getObject(tile, ObjectType.INTERACTABLE) != null) {
            player.message("You can't plant flowers here.")
            return@on_item_option
        }
        val step = order.firstOrNull { world.collision.canTraverse(tile, it, projectile = false, water = false) }
        if (step == null) {
            player.message("You need some space around you to plant flowers.")
            return@on_item_option
        }
        if (!player.inventory.remove(seed, 1).hasSucceeded()) return@on_item_option
        val flower = rollFlower(world.random(1_000))
        world.spawnTemporaryObject(DynamicObject(flower.objectId, 10, 0, tile), FLOWER_TICKS)
        player.moveTo(tile.step(step))
        player.faceTile(tile)
        player.message("You plant the seed and some ${flower.displayName.lowercase()} flowers appear.")
    }
}