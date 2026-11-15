package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import java.io.File

/** Source groups 73/203; the cache tool writes the mapping only after verification. */
object ClueInterfaces {
    private val RECEIPT = AttributeKey<List<Int>>()
    private val mapping by lazy {
        val file = File(RspsPaths.data("cfg/clue/interfaces.tsv"))
        if (file.isFile) file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
            .filter { it[0] == "interface" }.associate { it[1].toInt() to it[2].toInt() } else emptyMap()
    }
    fun of(source: Int): Int? = mapping[source]
    const val REWARD_FIRST = 5
    const val REWARD_SLOTS = 12 // OSRS cache inventory 141; source proc 2090.


    fun requireRewardScreen(player: Player): Boolean {
        if (of(73) != null) return true
        println("PROBLEM CLUE reward screen not imported; casket retained")
        player.message("This reward casket is temporarily unavailable.")
        return false
    }

    /** The reward already belongs to the player; this window is a receipt, with no Take/Bank action. */
    fun rewards(player: Player, rewards: List<Item>) {
        val iface = of(73) ?: return
        val rows = rewards.groupBy { it.id }.flatMap { (id, items) ->
            var amount = items.sumOf { it.amount.toLong() }
            buildList { while (amount > 0) { val count = amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); add(Item(id, count)); amount -= count } }
        }
        check(rows.size <= REWARD_SLOTS) { "CLUE reward receipt exceeds source inventory 141: ${rows.size}" }
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.attr[RECEIPT] = rows.map { it.id }
        player.setInterfaceEvents(iface, 4, -1..-1, 0x2)
        repeat(REWARD_SLOTS) { slot ->
            val row = rows.getOrNull(slot)
            if (row != null) player.setComponentItem(iface, REWARD_FIRST + slot, row.id, row.amount)
            player.setComponentHidden(iface, REWARD_FIRST + slot, row == null)
            if (row != null) player.setInterfaceEvents(iface, REWARD_FIRST + slot, -1..-1, 0x2)
        }
    }

    fun examine(player: Player, component: Int) {
        val iface = of(73) ?: return
        if (!player.interfaces.isVisible(iface)) return
        val item = player.attr[RECEIPT]?.getOrNull(component - REWARD_FIRST) ?: return
        player.world.sendExamine(player, item, ExamineEntityType.ITEM)
    }

    fun text(player: Player, hint: String): Boolean {
        val iface = of(203) ?: run {
            println("PROBLEM CLUE text interface not imported")
            player.message("This clue scroll is temporarily unavailable.")
            return false
        }
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(iface, 2, hint)
        player.setInterfaceEvents(iface, 1, -1..-1, 0x2)
        return true
    }
}
