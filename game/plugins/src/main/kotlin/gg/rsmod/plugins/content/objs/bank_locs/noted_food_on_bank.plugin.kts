package gg.rsmod.plugins.content.objs.bank_locs

import gg.rsmod.game.fs.def.ObjectDef

/*
 * DMM-ARMAGEDDON D6 (owner 20-11-2026): noted food used on any bank (booth, chest, counter) is unnoted - NotedFoodOnBank.
 * Every bank loc of the cache is found by name, like the bank-object fallback; a loc that already has its own any-item handler
 * keeps it.
 */
val bankLocs =
    (0 until runCatching { world.definitions.getCount(ObjectDef::class.java) }.getOrDefault(0))
        .filter { id -> world.definitions.getNullable(ObjectDef::class.java, id)?.let(NotedFoodOnBank::isBank) == true }
        .toSet() + BankObjects.ALL

bankLocs.filterNot { world.plugins.hasAnyItemOnObjectHandler(it) }.forEach { obj ->
    on_any_item_on_obj(obj) {
        NotedFoodOnBank.use(player, player.getInteractingItem())
    }
}
