package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.game.fs.def.NpcDef

/*
 * Farming tool store ("Exchange" on every tool leprechaun): interface 125 (store) with 126 (inventory side). Component ids, ops and
 * the per-tool varbits 1435-1443/1778/1848 are the 667 cache's own (Void donor, content/skill/farming/FarmingEquipmentStore.kt); the
 * cache's onVarTransmit hooks draw the stored counts from those varbits. Before this the cache offered "Exchange" on 13 leprechauns
 * with nothing bound.
 */
val STORE = 125
val SIDE = 126

private val OP1 = 61
private val OP2 = 64
private val OP3 = 4
private val OP4 = 52

/** store component, side component, varbit, most that fits, the item(s) it holds. */
data class Tool(val store: Int, val side: Int, val varbit: Int, val limit: Int, val items: List<Int>)

/** Stored watering can: varbit 1 = empty can, 2..9 = can (1)..(8). */
val WATERING_CANS: List<Int> = listOf(Items.WATERING_CAN, Items.WATERING_CAN_6797) + (5333..5340).toList()

val TOOLS =
    listOf(
        Tool(21, 4, 1435, 1, listOf(Items.RAKE)),
        Tool(19, 7, 1436, 1, listOf(Items.SEED_DIBBER)),
        Tool(17, 10, 1437, 1, listOf(Items.SPADE)),
        Tool(13, 13, 1438, 1, listOf(Items.SECATEURS, Items.MAGIC_SECATEURS)),
        Tool(11, 16, 1439, 1, WATERING_CANS),
        Tool(15, 19, 1440, 1, listOf(Items.GARDENING_TROWEL)),
        Tool(7, 22, 1441, 31, listOf(Items.BUCKET)),
        Tool(9, 25, 1778, 4, listOf(Items.SCARECROW)),
        Tool(5, 28, 1442, 255, listOf(Items.COMPOST)),
        Tool(3, 31, 1443, 255, listOf(Items.SUPERCOMPOST)),
    )

val MAGIC_SECATEURS_VARBIT = 1848

fun canValue(itemId: Int): Int = if (itemId in 5333..5340) itemId - 5333 + 2 else 1

fun canItem(value: Int): Int = if (value >= 2) 5333 + value - 2 else Items.WATERING_CAN

val LEPRECHAUNS =
    world.definitions.getAllKeys(NpcDef::class.java).filter { id ->
        val def = world.definitions.get(NpcDef::class.java, id)
        def.name.equals("Tool leprechaun", ignoreCase = true) && def.options.any { it.equals("Exchange", ignoreCase = true) }
    }

LEPRECHAUNS.forEach { npc ->
    on_npc_option(npc = npc, option = "exchange") {
        openStore(player)
    }
}

fun openStore(player: Player) {
    player.openInterface(STORE, InterfaceDestination.MAIN_SCREEN)
    player.openInterface(SIDE, InterfaceDestination.TAB_AREA)
}

on_interface_close(interfaceId = STORE) {
    if (player.interfaces.isVisible(SIDE)) player.closeInterface(interfaceId = SIDE)
    player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
    player.inventory.dirty = true
}

on_button(interfaceId = STORE, component = 34) {
    player.closeInterface(STORE)
}

TOOLS.forEach { tool ->
    on_button(interfaceId = STORE, component = tool.store) {
        val stored = player.getVarbit(tool.varbit)
        val opcode = player.getInteractingOpcode()
        if (stored == 0) return@on_button
        when (opcode) {
            OP1 -> withdraw(player, tool, 1)
            OP2 -> withdraw(player, tool, 5)
            OP3 -> withdraw(player, tool, stored)
            OP4 -> player.queue { withdraw(player, tool, inputInt("Enter amount:")) }
        }
    }

    on_button(interfaceId = SIDE, component = tool.side) {
        when (player.getInteractingOpcode()) {
            OP1 -> store(player, tool, 1)
            OP2 -> store(player, tool, 5)
            OP3 -> store(player, tool, Int.MAX_VALUE)
            OP4 -> player.queue { store(player, tool, inputInt("Enter amount:")) }
        }
    }
}

fun withdraw(player: Player, tool: Tool, requested: Int) {
    val stored = player.getVarbit(tool.varbit)
    if (stored == 0 || requested <= 0) return
    if (player.inventory.isFull) {
        player.message("You don't have room to hold that.")
        return
    }
    when (tool.varbit) {
        1439 -> {
            if (player.inventory.add(canItem(stored)).hasSucceeded()) player.setVarbit(tool.varbit, 0)
        }
        1438 -> {
            val item = if (player.getVarbit(MAGIC_SECATEURS_VARBIT) == 1) Items.MAGIC_SECATEURS else Items.SECATEURS
            if (player.inventory.add(item).hasSucceeded()) {
                player.setVarbit(tool.varbit, 0)
                player.setVarbit(MAGIC_SECATEURS_VARBIT, 0)
            }
        }
        else -> {
            val amount = minOf(requested, stored, player.inventory.freeSlotCount)
            val added = player.inventory.add(tool.items.first(), amount).completed
            player.setVarbit(tool.varbit, stored - added)
        }
    }
}

fun store(player: Player, tool: Tool, requested: Int) {
    val stored = player.getVarbit(tool.varbit)
    if (requested <= 0) return
    if (stored >= tool.limit || (tool.limit == 1 && stored > 0)) {
        player.message(
            when (tool.varbit) {
                1441 -> "You cannot store more than ${tool.limit} buckets in here."
                1778 -> "You cannot store more than ${tool.limit} scarecrows in here."
                1442 -> "You cannot store that much compost in here."
                1443 -> "You cannot store that much supercompost in here."
                1438 -> "You cannot store more than one pair of secateurs in here."
                else -> "You cannot store more than one ${player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, tool.items.first()).name.lowercase()} in here."
            },
        )
        return
    }
    val item = tool.items.firstOrNull { player.inventory.contains(it) }
    if (item == null) {
        player.message("You haven't got a ${player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, tool.items.first()).name.lowercase()} to store.")
        return
    }
    when (tool.varbit) {
        1439 -> {
            // The fullest can goes in first.
            val can = tool.items.filter { player.inventory.contains(it) }.maxByOrNull(::canValue)!!
            if (player.inventory.remove(can).hasSucceeded()) player.setVarbit(tool.varbit, canValue(can))
        }
        1438 -> {
            val pair = if (player.inventory.contains(Items.MAGIC_SECATEURS)) Items.MAGIC_SECATEURS else Items.SECATEURS
            if (player.inventory.remove(pair).hasSucceeded()) {
                player.setVarbit(tool.varbit, 1)
                player.setVarbit(MAGIC_SECATEURS_VARBIT, if (pair == Items.MAGIC_SECATEURS) 1 else 0)
            }
        }
        else -> {
            val amount = minOf(requested, tool.limit - stored, player.inventory.getItemCount(item))
            val removed = player.inventory.remove(item, amount).completed
            player.setVarbit(tool.varbit, stored + removed)
        }
    }
}
