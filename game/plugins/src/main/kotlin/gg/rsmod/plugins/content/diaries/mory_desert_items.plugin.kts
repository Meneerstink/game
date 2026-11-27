package gg.rsmod.plugins.content.diaries

import gg.rsmod.plugins.content.diaries.Diaries.Diary

/**
 * DIARIES-OSRS Morytania + Desert (27-11): Morytania legs and Desert amulet teleports (option names from the imported OSRS items).
 * One generic option ("Teleport"/"Operate") on legs 3-4 or amulet 4 asks where to go, like OSRS.
 */
val legs = DiaryRewards.items(world, Diary.MORYTANIA)
val amulets = DiaryRewards.items(world, Diary.DESERT)

fun legsTele(p: gg.rsmod.game.model.entity.Player, tier: Int, opt: String) {
    when {
        opt.contains("burgh", true) -> DiaryItems.moryLegsBurgh(p, tier)
        opt.contains("ecto", true) || opt.contains("slime", true) || tier < 3 -> DiaryItems.moryLegsSlime(p, tier)
        else -> p.queue {
            when (options("Ectofuntus slime pit", "Burgh de Rott", title = "Where would you like to teleport to?")) {
                1 -> DiaryItems.moryLegsSlime(p, tier)
                2 -> DiaryItems.moryLegsBurgh(p, tier)
            }
        }
    }
}

fun amuletTele(p: gg.rsmod.game.model.entity.Player, tier: Int, opt: String) {
    when {
        opt.contains("kalphite", true) -> DiaryItems.desertAmuletKalphite(p, tier)
        opt.contains("nardah", true) || tier < 4 -> DiaryItems.desertAmuletNardah(p, tier)
        else -> p.queue {
            when (options("Nardah", "Kalphite Cave", title = "Where would you like to teleport to?")) {
                1 -> DiaryItems.desertAmuletNardah(p, tier)
                2 -> DiaryItems.desertAmuletKalphite(p, tier)
            }
        }
    }
}

val isTele = { o: String -> listOf("teleport", "operate", "ecto", "slime", "burgh", "nardah", "kalphite").any { o.contains(it, true) } }

legs.forEachIndexed { i, id ->
    if (id < 0) return@forEachIndexed
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id)
    def.inventoryMenu.filterNotNull().distinct().filter(isTele).forEach { opt -> on_item_option(item = id, option = opt) { legsTele(player, i + 1, opt) } }
    def.equipmentMenu.filterNotNull().distinct().filter(isTele).forEach { opt -> on_equipment_option(id, option = opt) { legsTele(player, i + 1, opt) } }
}
amulets.forEachIndexed { i, id ->
    if (id < 0) return@forEachIndexed
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id)
    def.inventoryMenu.filterNotNull().distinct().filter(isTele).forEach { opt -> on_item_option(item = id, option = opt) { amuletTele(player, i + 1, opt) } }
    def.equipmentMenu.filterNotNull().distinct().filter(isTele).forEach { opt -> on_equipment_option(id, option = opt) { amuletTele(player, i + 1, opt) } }
}

