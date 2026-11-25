package gg.rsmod.plugins.content.skills

// N410: definitions without the corresponding menu remain unbound until Claude's paired cache update.
listOf(
    Triple(Skillcapes.AGILITY, "Stamina Boost", SkillcapeFeatures::stamina),
    Triple(Skillcapes.FLETCHING, "Search", SkillcapeFeatures::searchFletching),
    Triple(Skillcapes.HERBLORE, "Search", SkillcapeFeatures::searchHerblore),
).forEach { (cape, option, action) ->
    listOf(cape.untrimmedCape, cape.trimmedCape).forEach { item ->
        val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item)
        if (def.inventoryMenu.any { it.equals(option, ignoreCase = true) }) {
            on_item_option(item, option) { action(player) }
        }
        if (def.equipmentMenu.any { it.equals(option, ignoreCase = true) }) {
            on_equipment_option(item, option) { action(player) }
        }
    }
}

RangingSkillcape.CAPES.forEach { cape ->
    on_item_on_item(item1 = Items.VORKATHS_HEAD, item2 = cape) {
        val first = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM]?.get()
        val second = player.attr[gg.rsmod.game.model.attr.OTHER_ITEM_ATTR]?.get()
        if (first != null && second != null) RangingSkillcape.upgrade(player, first, second)
    }
}
