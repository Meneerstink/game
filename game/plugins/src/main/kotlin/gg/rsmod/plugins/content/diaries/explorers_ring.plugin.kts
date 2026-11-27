package gg.rsmod.plugins.content.diaries

/**
 * DIARIES-OSRS Lumbridge & Draynor (27-11): Explorer's ring run restore and the cabbage-patch teleport (OSRS Wiki "Explorer's ring").
 * The option names come from the 667 cache: every option with "run"/"energy" restores run energy, "teleport"/"cabbage" teleports.
 */
val rings = intArrayOf(Items.EXPLORERS_RING_1, Items.EXPLORERS_RING_2, Items.EXPLORERS_RING_3, Items.EXPLORERS_RING_4)
val isRun = { o: String -> o.contains("run", ignoreCase = true) || o.contains("energy", ignoreCase = true) }
val isAlch = { o: String -> o.contains("alch", ignoreCase = true) }
// OSRS "Alchemy"/"Check": the 667 ring option tells the free casts left (casting itself goes through the spellbook alchemy spells).
fun alchInfo(player: gg.rsmod.game.model.entity.Player, ring: Int) = player.message(
    if (DiaryItems.explorerTier(ring) < 2) "Your ring needs to be upgraded to cast free alchemy."
    else "Your ring has ${DiaryItems.explorerAlchLeft(player)} free ${if (DiaryItems.explorerTier(ring) >= 4) "low or high" else "low"} alchemy casts left today. Cast the spell without runes to use one.",
)
val isTele = { o: String -> o.contains("teleport", ignoreCase = true) || o.contains("cabbage", ignoreCase = true) }
fun cabbage(player: gg.rsmod.game.model.entity.Player, ring: Int) = DiaryItems.cabbageTeleport(player, ring)
rings.forEach { ring ->
    val def = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, ring)
    def.inventoryMenu.filterNotNull().distinct().forEach { opt ->
        if (isRun(opt)) on_item_option(item = ring, option = opt) { player.message(DiaryItems.explorerRunRestore(player, ring)) }
        else if (isTele(opt)) on_item_option(item = ring, option = opt) { cabbage(player, ring) }
        else if (isAlch(opt)) on_item_option(item = ring, option = opt) { alchInfo(player, ring) }
    }
    def.equipmentMenu.filterNotNull().distinct().forEach { opt ->
        if (isRun(opt)) on_equipment_option(ring, option = opt) { player.message(DiaryItems.explorerRunRestore(player, ring)) }
        else if (isTele(opt)) on_equipment_option(ring, option = opt) { cabbage(player, ring) }
        else if (isAlch(opt)) on_equipment_option(ring, option = opt) { alchInfo(player, ring) }
    }
}
