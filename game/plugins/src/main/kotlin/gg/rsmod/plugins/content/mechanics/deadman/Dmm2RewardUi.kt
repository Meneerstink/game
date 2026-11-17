package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.tools.importer.Dmm2WindowPort as W
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.tab.N108Ui
import java.util.Locale

/** Cache2686 OMNISHOP 7142/7144/7147/7245/7248/7254/7255, DBrow929/table39.
 * Currency row1206 is Deadman Points (not item13307 Blood money), as approved by V3.
 * Every purchase/refund goes through the NPC/session gate and the existing atomic store service.
 */
object Dmm2RewardUi : Dmm2Interactions.RewardUi {
    const val MAIN_SOURCE = 819
    const val SIDE_SOURCE = 806
    private const val EVENTS = 0x43e
    private const val HELP = "This panel will show further information about an item when selected from the left-hand menu.<br><br>" +
        "Once selected, you may then choose to purchase the item using the buttons below.<br><br>" +
        "You may also use the options menu to purchase multiples of an item."
    // The source row929 product order; enum ordinals are never supplied by a packet.
    val PRODUCTS = listOf(NigelRewardStore.Reward.ARMAGEDDON_WEAPONS, NigelRewardStore.Reward.CAPE_FABRIC,
        NigelRewardStore.Reward.ARMAGEDDON_TELEPORT, NigelRewardStore.Reward.RUG,
        NigelRewardStore.Reward.ANNIHILATION_WEAPONS, NigelRewardStore.Reward.BLUEPRINTS,
        NigelRewardStore.Reward.ANNIHILATION_TELEPORT)
    private class View(val main: Int, val side: Int) {
        var selected: NigelRewardStore.Reward? = null
        var selling: Int? = null
        var carried: List<Item?> = emptyList()
    }
    private val VIEW = AttributeKey<View>(resetOnDeath = true)
    private fun number(value: Int) = String.format(Locale.ENGLISH, "%,d", value)
    fun ready() = Dmm2Ids.interfaceId(MAIN_SOURCE) != null && Dmm2Ids.interfaceId(SIDE_SOURCE) != null

    override fun open(player: Player, store: NigelRewardStore) {
        val main = Dmm2Ids.interfaceId(MAIN_SOURCE) ?: return
        val side = Dmm2Ids.interfaceId(SIDE_SOURCE) ?: return
        player.openInterface(main, InterfaceDestination.MAIN_SCREEN)
        player.openInterface(side, InterfaceDestination.TAB_AREA)
        player.attr[VIEW] = View(main, side)
        player.setInterfaceEvents(main, W.REWARD_CLOSE, -1..-1, 2)
        PRODUCTS.indices.forEach { player.setInterfaceEvents(main, W.REWARD_FIRST + it*W.REWARD_STRIDE, -1..-1, EVENTS) }
        repeat(28) { player.setInterfaceEvents(side, W.SIDE_FIRST + it, -1..-1, EVENTS) }
        (26..29).forEach { player.setInterfaceEvents(main, it, -1..-1, 2) }
        (8..11).forEach { player.setInterfaceEvents(side, it, -1..-1, 2) }
        player.setInterfaceEvents(side, 3, -1..-1, 2)
        player.setComponentText(main, 45, "Deadman Points")
        player.setComponentText(main, 32, "Deadman Points")
        // No shop filters exist (enum4988); hide the source's unported view/filter controls.
        player.setComponentHidden(main, 11, true)
        player.setComponentHidden(main, 12, true)
        scroll(player, main, 38, 39, 311)
        scroll(player, main, 35, 36, 300)
        scroll(player, side, 6, 7, 160)
        refresh(player, store)
    }

    override fun visible(player: Player) = player.attr[VIEW]?.let {
        player.interfaces.isVisible(it.main) && player.interfaces.isVisible(it.side)
    } == true

    private fun scroll(player: Player, root: Int, layer: Int, bar: Int, height: Int) {
        player.runClientScript(DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT, (root shl 16) or layer, (root shl 16) or bar, height)
    }

    override fun refresh(player: Player, store: NigelRewardStore) {
        val view = player.attr[VIEW] ?: return
        view.carried = player.inventory.map { it?.let(::Item) }
        val points = number(NigelRewardStore.balance(player))
        player.setComponentText(view.main, 33, points)
        player.setComponentText(view.main, 46, points)
        PRODUCTS.forEachIndexed { index, reward ->
            val id = store.contract.rewards[reward] ?: return@forEachIndexed
            val first = W.REWARD_FIRST + index*W.REWARD_STRIDE
            player.setComponentItem(view.main, first+10, id, 1)
            player.setComponentText(view.main, first+11, Item(id).getName(player.world.definitions))
        }
        val selected = view.selected
        val id = selected?.let { store.contract.rewards[it] }
        player.setComponentText(view.main, W.REWARD_INFO_TITLE, if (id == null) "Welcome to the Deadman<br>Reward Store." else Item(id).getName(player.world.definitions))
        player.setComponentHidden(view.main, W.REWARD_INFO_ITEM, id == null)
        if (id != null) player.setComponentItem(view.main, W.REWARD_INFO_ITEM, id, 1)
        player.setComponentText(view.main, W.REWARD_INFO_STOCK, if (id == null) "" else "Unlimited stock")
        player.setComponentText(view.main, W.REWARD_INFO_COST, if (selected == null) "" else "Cost<br>${number(selected.price)} Deadman Points")
        player.setComponentText(view.main, W.REWARD_INFO_TEXT, if (id == null) HELP else "")
        // 7147 skips the icon/stock/cost blocks for Help; its paragraph begins immediately below the title.
        player.setComponentPosition(view.main, W.REWARD_INFO_TEXT,10,if (id == null) 52 else 137)
        val helpHeight = HELP.split("<br>").sumOf { N108Ui.lines(player.world,"p12_full",it,161) }*16
        scroll(player,view.main,35,36,if (id == null) 52+helpHeight+10 else 149)
        player.setComponentHidden(view.main, 23, selected == null)
        (26..29).forEach { player.setComponentHidden(view.main, it, selected == null) }
        val sold = view.selling?.let { slot -> view.carried.getOrNull(slot) }
        player.setComponentHidden(view.side, 1, sold == null)
        view.carried.forEachIndexed { slot, item ->
            player.setComponentHidden(view.side, W.SIDE_FIRST+slot, item == null || sold != null)
            if (item != null) player.setComponentItem(view.side, W.SIDE_FIRST+slot, item.id, item.amount)
        }
        if (sold != null) {
            val base = sold.toUnnoted(player.world.definitions).id
            val reward = PRODUCTS.firstOrNull { store.contract.rewards[it] == base }
            val count = available(player, sold.id)
            player.setComponentText(view.side, W.SIDE_TITLE, sold.getName(player.world.definitions))
            player.setComponentItem(view.side, W.SIDE_ITEM, sold.id, 1)
            player.setComponentText(view.side, W.SIDE_OWNED, "$count Owned")
            player.setComponentText(view.side, W.SIDE_VALUE, if (reward == null || sold.hasAnyAttr()) "No shop offer" else "Value<br>${number(reward.price)} Deadman Points")
            (8..11).forEach { player.setComponentHidden(view.side, it, reward == null || sold.hasAnyAttr()) }
        }
        player.inventory.dirty = true
    }

    /** Component determines the source row; the client never supplies a price, currency or purchased id. */
    fun product(player: Player, index: Int, option: Int) {
        val view = player.attr[VIEW] ?: return
        if (!visible(player)) return
        val reward = PRODUCTS.getOrNull(index) ?: return
        val store = NigelRewardStore.active ?: return
        if (option == 10) { store.contract.rewards[reward]?.let { player.world.sendExamine(player, it, ExamineEntityType.ITEM) }; return }
        if (option == 1) {
            player.playSound(gg.rsmod.plugins.api.cfg.Sfx.INTERFACE_SELECT1) // 9771
            view.selected = reward; refresh(player, store); return
        }
        val amount = when (option) { 2 -> 1; 3 -> 5; 4 -> 10; 5 -> 50; else -> return }
        Dmm2Interactions.buy(player, reward, amount)
    }

    fun buySelected(player: Player, button: Int) {
        val selected = player.attr[VIEW]?.selected ?: return
        val amount = listOf(1,5,10,50).getOrNull(button) ?: return
        if (!visible(player)) return
        player.playSound(gg.rsmod.plugins.api.cfg.Sfx.INTERFACE_SELECT1) // 9776
        Dmm2Interactions.buy(player, selected, amount)
    }

    private fun available(player: Player, id: Int) = player.inventory.filterNotNull()
        .filter { it.id == id && !it.hasAnyAttr() && it.amount > 0 }.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun inventory(player: Player, slot: Int, option: Int, packetItem: Int) {
        val view = player.attr[VIEW] ?: return
        if (!visible(player) || view.selling != null) return
        val shown = view.carried.getOrNull(slot) ?: return
        val live = player.inventory.rawItems.getOrNull(slot) ?: return
        if (packetItem != shown.id || live.id != shown.id || live.amount != shown.amount || live.attr != shown.attr) return
        if (option == 10) { player.world.sendExamine(player, shown.id, ExamineEntityType.ITEM); return }
        if (option !in 1..5) return
        player.playSound(gg.rsmod.plugins.api.cfg.Sfx.INTERFACE_SELECT1) // 7251
        if (option == 1) { view.selling = slot; NigelRewardStore.active?.let { refresh(player, it) }; return }
        val amount = when (option) { 2 -> 1; 3 -> 5; 4 -> 10; 5 -> 50; else -> return }
        val count = minOf(amount, available(player, shown.id))
        if (count > 0) Dmm2Interactions.sell(player, slot, shown.id, count)
    }

    fun sellSelected(player: Player, button: Int) {
        val view = player.attr[VIEW] ?: return
        val slot = view.selling ?: return
        val shown = view.carried.getOrNull(slot) ?: return
        val live = player.inventory.rawItems.getOrNull(slot) ?: return
        if (shown.id != live.id || shown.amount != live.amount || shown.attr != live.attr) return
        val amount = listOf(1,5,10,50).getOrNull(button) ?: return
        val count = minOf(amount, available(player, shown.id))
        if (count > 0) Dmm2Interactions.sell(player, slot, shown.id, count)
    }

    fun closeInfo(player: Player) {
        val view = player.attr[VIEW] ?: return
        view.selling = null
        NigelRewardStore.active?.let { refresh(player, it) }
    }

    fun close(player: Player) {
        val view = player.attr[VIEW] ?: return
        player.attr.remove(VIEW)
        Dmm2Interactions.closeRewards(player)
        if (player.interfaces.isVisible(view.main)) player.closeInterface(view.main)
        if (player.interfaces.isVisible(view.side)) player.closeInterface(view.side)
        player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
    }
}
