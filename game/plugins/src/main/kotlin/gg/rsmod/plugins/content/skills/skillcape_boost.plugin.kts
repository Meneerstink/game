package gg.rsmod.plugins.content.skills

// K03 N410 part 4: the skillcape Boost on the worn cape's "Boost" option and when the cape is put on (SkillcapeBoost).
Skillcapes.values().forEach { cape ->
    listOf(cape.untrimmedCape, cape.trimmedCape).forEach { item ->
        val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item)
        if (def.equipmentMenu.any { it?.lowercase() == "boost" }) {
            on_equipment_option(item, option = "boost") { SkillcapeBoost.apply(player, cape) }
        }
        // Construction joins the option binding above once Claude adds its missing cache Boost param.
        on_item_equip(item) { SkillcapeBoost.equip(player, item) }
        on_item_unequip(item) { SkillcapeBoost.remove(player, item) }
    }
}
