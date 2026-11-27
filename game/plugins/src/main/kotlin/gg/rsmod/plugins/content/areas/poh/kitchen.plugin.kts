package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.def.ItemDef

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C5): the kitchen's beer barrels and the tea chain ([PohKitchen]).
 */

// A beer glass on a barrel: that barrel's drink.
PohKitchen.BARRELS.forEach { (barrel, drink) ->
    on_item_on_obj(obj = barrel, item = PohKitchen.BEER_GLASS) {
        if (!PohKitchen.inHouse(player)) return@on_item_on_obj
        if (PohKitchen.replace(player, PohKitchen.BEER_GLASS, drink)) {
            player.animate(832)
            player.message("You fill the glass from the barrel.")
        }
    }
}

// The kettle on the sink or a pump.
PohKitchen.WATER.filter { world.definitions.getNullable(gg.rsmod.game.fs.def.ObjectDef::class.java, it) != null }.forEach { water ->
    on_item_on_obj(obj = water, item = PohKitchen.KETTLE) {
        if (!PohKitchen.inHouse(player)) return@on_item_on_obj
        if (PohKitchen.replace(player, PohKitchen.KETTLE, PohKitchen.FULL_KETTLE)) {
            player.animate(832)
            player.message("You fill the kettle.")
        }
    }
}

// The full kettle on a stove with a hook or better.
PohKitchen.STOVES.filter { world.definitions.getNullable(gg.rsmod.game.fs.def.ObjectDef::class.java, it) != null }.forEach { stove ->
    on_item_on_obj(obj = stove, item = PohKitchen.FULL_KETTLE) {
        if (!PohKitchen.inHouse(player)) return@on_item_on_obj
        if (PohKitchen.replace(player, PohKitchen.FULL_KETTLE, PohKitchen.HOT_KETTLE)) {
            player.animate(896)
            player.message("You boil the kettle of water.")
        }
    }
}
on_item_on_obj(obj = 13528, item = PohKitchen.FULL_KETTLE) {
    player.message("You need a firepit with a hook to hang the kettle over.")
}

// Tea leaves in a teapot, the hot kettle on the teapot with leaves.
PohKitchen.POTS.forEach { pot ->
    on_item_on_item(item1 = PohKitchen.TEA_LEAVES, item2 = pot.empty) {
        if (player.inventory.remove(PohKitchen.TEA_LEAVES, 1).hasSucceeded() && PohKitchen.replace(player, pot.empty, pot.leaves)) {
            player.message("You add the tea leaves to the pot.")
        }
    }
    on_item_on_item(item1 = PohKitchen.HOT_KETTLE, item2 = pot.leaves) {
        if (!player.inventory.contains(pot.leaves)) return@on_item_on_item
        if (PohKitchen.replace(player, PohKitchen.HOT_KETTLE, PohKitchen.KETTLE) && PohKitchen.replace(player, pot.leaves, pot.doses[0])) {
            player.message("You pour the boiling water into the teapot.")
        }
    }
    pot.doses.forEach { dose ->
        PohKitchen.CUPS.forEach { cup ->
            on_item_on_item(item1 = dose, item2 = cup.empty) { PohKitchen.pour(player, dose, cup.empty) }
        }
    }
}

// Milk in a cup of tea (look only), then drinking.
PohKitchen.CUPS.forEach { cup ->
    on_item_on_item(item1 = PohKitchen.MILK, item2 = cup.tea) {
        if (player.inventory.contains(cup.tea) && PohKitchen.replace(player, PohKitchen.MILK, PohKitchen.BUCKET) && PohKitchen.replace(player, cup.tea, cup.milky)) {
            player.message("You add some milk to the tea.")
        }
    }
}
(PohKitchen.DRINKS + PohKitchen.CUPS.flatMap { listOf(it.tea, it.milky) }).forEach { drink ->
    val menu = world.definitions.get(ItemDef::class.java, drink).inventoryMenu
    if (menu.any { it.equals("drink", true) }) {
        on_item_option(item = drink, option = "drink") { PohKitchen.drink(player, drink) }
    }
}

PohKitchen.CUPS.forEach { cup ->
    listOf(cup.tea, cup.milky).forEach { tea ->
        if (world.definitions.get(ItemDef::class.java, tea).inventoryMenu.any { it.equals("empty", true) }) {
            on_item_option(item = tea, option = "empty") {
                if (PohKitchen.replace(player, tea, cup.empty)) player.message("You empty the cup.")
            }
        }
    }
}

on_timer(PohKitchen.LEAVE_TIMER) { PohKitchen.check(player) }

// A player who logs in outside a house (or into a rebuilt one) keeps none of it either.
on_login { PohKitchen.watch(player) }
