package gg.rsmod.plugins.content.skills.hunter

ImplingData.JAR_TIERS.forEach { tier ->
    val option = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, tier.jarItem)
        .inventoryMenu.indexOfFirst { it.equals("loot", ignoreCase = true) } + 1
    if (option > 0 && !world.plugins.hasItemOption(tier.jarItem, option)) on_item_option(item = tier.jarItem, option = "loot") {
        Impling.openJar(player, tier.jarItem)
    }
}
