package gg.rsmod.plugins.content.activity.cox

/**
 * CoX supplies: Herblore in the raid (CoxHerblore) - Clean on the grimy herbs, any two ingredients used together mix.
 */
fun hasOp(item: Int, op: String): Boolean =
    world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).inventoryMenu.any { it == op }

CoxHerblore.HERBS.forEach { herb ->
    CoxIds.item(herb.grimy)?.takeIf { hasOp(it, "Clean") }?.let { id -> on_item_option(item = id, option = "Clean") { CoxHerblore.clean(player, herb) } }
}

val ingredients = CoxHerblore.INGREDIENTS.mapNotNull(CoxIds::item).distinct().sorted()
for (i in ingredients.indices) for (j in i + 1 until ingredients.size) {
    on_item_on_item(item1 = ingredients[i], item2 = ingredients[j]) { CoxHerblore.mix(player) }
}
