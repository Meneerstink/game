package gg.rsmod.plugins.content.skills

// K03 N410 (owner 2026-10-29): "Spellbook" (5 changes a day) and "Check" on the Magic cape, the Magic cape (t) and the plain max
// cape, in the inventory and worn (SkillcapeSpellbook). Bound only where the cache menu carries the option (MaxCapeMenuTool).
listOf(Items.MAGIC_CAPE, Items.MAGIC_CAPE_T, gg.rsmod.plugins.content.items.osrs.MaxCapes.MAX_CAPE).forEach { item ->
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item)
    if (def.inventoryMenu.any { it?.lowercase() == "spellbook" }) on_item_option(item, option = "spellbook") { SkillcapeSpellbook.choose(player) }
    if (def.equipmentMenu.any { it?.lowercase() == "spellbook" }) on_equipment_option(item, option = "spellbook") { SkillcapeSpellbook.choose(player) }
    if (def.inventoryMenu.any { it?.lowercase() == "check" }) on_item_option(item, option = "check") { SkillcapeSpellbook.check(player) }
    if (def.equipmentMenu.any { it?.lowercase() == "check" }) on_equipment_option(item, option = "check") { SkillcapeSpellbook.check(player) }
}
