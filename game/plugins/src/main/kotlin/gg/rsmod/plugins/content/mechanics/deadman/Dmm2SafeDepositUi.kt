package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*

/** Cache2686 scripts1151/1155/1156,1158/1161. Extra static children replace OSRS CC_CREATE;
 * OsrsInterfacePortTool dmm2-ui --reuse supplies them. Root ids always come from Claude's mapping.
 */
object Dmm2SafeDepositUi : Dmm2Interactions.SafeUi {
    const val MAIN_SOURCE = 230
    const val SIDE_SOURCE = 232
    const val TITLE = 18
    const val CLOSE = 30
    const val FIRST_SLOT = 31
    const val SIDE_FIRST_SLOT = 3
    const val DEPOSIT_INVENTORY = 9
    const val DEPOSIT_WORN = 11
    private const val EVENTS = 0x43e // ops1..5 + op10 Examine, no drag/drop
    private class View(val main: Int, val side: Int) {
        var revision = 0
        var stored: List<Item?> = emptyList()
        var carried: List<Item?> = emptyList()
    }
    private val VIEW = AttributeKey<View>(resetOnDeath = true)

    fun ready(): Boolean = Dmm2Ids.interfaceId(MAIN_SOURCE) != null && Dmm2Ids.interfaceId(SIDE_SOURCE) != null
    override fun open(player: Player, items: List<Item?>) {
        val main = Dmm2Ids.interfaceId(MAIN_SOURCE) ?: return
        val side = Dmm2Ids.interfaceId(SIDE_SOURCE) ?: return
        player.openInterface(main, InterfaceDestination.MAIN_SCREEN)
        player.openInterface(side, InterfaceDestination.TAB_AREA)
        player.attr[VIEW] = View(main, side)
        player.setComponentText(main, TITLE, "Safe Deposit Box")
        listOf(CLOSE, DEPOSIT_INVENTORY, DEPOSIT_WORN).forEach { player.setInterfaceEvents(main, it, -1..-1, 2) }
        repeat(10) { player.setInterfaceEvents(main, FIRST_SLOT + it, -1..-1, EVENTS) }
        repeat(28) { player.setInterfaceEvents(side, SIDE_FIRST_SLOT + it, -1..-1, EVENTS) }
        refresh(player, items)
    }

    override fun visible(player: Player): Boolean = player.attr[VIEW]?.let {
        player.interfaces.isVisible(it.main) && player.interfaces.isVisible(it.side)
    } == true

    override fun refresh(player: Player, items: List<Item?>) {
        val view = player.attr[VIEW] ?: return
        view.revision++
        view.stored = items.map { it?.let(::Item) }
        view.carried = player.inventory.map { it?.let(::Item) }
        fun draw(root: Int, first: Int, slots: List<Item?>) = slots.forEachIndexed { index, item ->
            player.setComponentHidden(root, first + index, item == null)
            if (item != null) player.setComponentItem(root, first + index, item.id, item.amount)
        }
        draw(view.main, FIRST_SLOT, view.stored)
        draw(view.side, SIDE_FIRST_SLOT, view.carried)
        player.setComponentText(view.main, 13, items.count { it != null }.toString())
        player.setComponentText(view.main, 16, "10")
        player.inventory.dirty = true
        player.equipment.dirty = true
    }

    fun close(player: Player) {
        val view = player.attr[VIEW] ?: return
        player.attr.remove(VIEW)
        Dmm2Interactions.closeSafe(player)
        if (player.interfaces.isVisible(view.main)) player.closeInterface(view.main)
        if (player.interfaces.isVisible(view.side)) player.closeInterface(view.side)
        player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
        player.closeInputDialog()
    }

    /** Preserve the shown slot and exact object while an X prompt is pending; reopening invalidates the continuation. */
    fun click(player: Player, stored: Boolean, slot: Int, option: Int, packetItem: Int) {
        val view = player.attr[VIEW] ?: return
        if (!visible(player)) return
        val shown = (if (stored) view.stored else view.carried).getOrNull(slot) ?: return
        if (packetItem != shown.id) return
        if (option == 10) { player.world.sendExamine(player, shown.id, ExamineEntityType.ITEM); return }
        if (option !in 1..5) return
        val held = if (stored) null else player.inventory.rawItems.getOrNull(slot)
        if (!stored && (held == null || held.id != shown.id || held.amount != shown.amount || held.attr != shown.attr)) return
        val revision = view.revision
        if (option == 4) {
            player.queue(TaskPriority.WEAK) {
                val amount = inputInt(if (stored) "Enter amount to withdraw:" else "Enter amount to deposit:")
                if (player.attr[VIEW] === view && view.revision == revision && (stored || player.inventory.rawItems.getOrNull(slot) === held))
                    transfer(player, stored, slot, shown.id, amount)
            }
        } else transfer(player, stored, slot, shown.id, when (option) { 1 -> 1; 2 -> 5; 3 -> 10; else -> Int.MAX_VALUE })
    }

    private fun transfer(player: Player, stored: Boolean, slot: Int, id: Int, amount: Int) {
        if (amount <= 0) return
        if (!stored) {
            val snapshot = player.attr[VIEW]?.carried.orEmpty()
            val slots = listOf(slot) + snapshot.indices.filter { it != slot && snapshot[it]?.id == id }
            var remaining = amount
            for (index in slots) {
                val result = Dmm2Interactions.deposit(player, index, id, remaining)
                if (result.status != SafeDepositBox.Status.OK) { feedback(player, result.status); break }
                remaining -= result.moved
                if (remaining == 0) break
            }
        } else {
            // Ten physical slots; visit a fixed snapshot once, never a newly inserted replacement.
            val slots = player.attr[VIEW]?.stored.orEmpty().mapIndexedNotNull { index, item ->
                if (item?.id == id) index else null
            }.let { listOf(slot) + it.filter { index -> index != slot } }.take(amount.coerceAtMost(10))
            for (index in slots) {
                val result = Dmm2Interactions.withdraw(player, index, id)
                if (result.status != SafeDepositBox.Status.OK) { feedback(player, result.status); break }
            }
        }
    }

    fun depositAll(player: Player, worn: Boolean) {
        if (!visible(player)) return
        // Script1152 -> synth2266; source/local payload equality is pinned in phase6-sources/button-sound.json.
        player.playSound(gg.rsmod.plugins.api.cfg.Sfx.INTERFACE_SELECT1)
        val source = if (worn) player.equipment else player.inventory
        val snapshot = source.mapIndexedNotNull { slot, item -> item?.let { Triple(slot, it.id, it.amount) } }
        for ((slot, id, amount) in snapshot) {
            val result = if (worn) Dmm2Interactions.depositWorn(player, slot, id, amount) else Dmm2Interactions.deposit(player, slot, id, amount)
            if (result.status in setOf(SafeDepositBox.Status.SKULLED, SafeDepositBox.Status.NOT_OPEN, SafeDepositBox.Status.UNAVAILABLE, SafeDepositBox.Status.BOX_FULL)) {
                feedback(player, result.status); break
            }
        }
    }

    private fun feedback(player: Player, status: SafeDepositBox.Status) {
        if (status == SafeDepositBox.Status.SKULLED) player.message(SafeDepositBox.SKULL_MESSAGE)
        // Other refusal wording has no proven transcript; state is refreshed without invented OSRS dialogue.
    }
}
