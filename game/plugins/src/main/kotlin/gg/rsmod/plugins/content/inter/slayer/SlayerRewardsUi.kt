package gg.rsmod.plugins.content.inter.slayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.BROAD_FLETCHING
import gg.rsmod.game.model.attr.CRAFT_ROS
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.SlayerRewardsInterfaceImportTool as T
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.skills.slayer.Krystilia

/**
 * N522 2e: the OSRS "Slayer Rewards" screen (interface [T.INTERFACE_ID], built by [T]) - Unlock, Extend and Buy as in OSRS
 * (RuneStar slayer_rewards_* scripts, OSRS dbtable 117). The Tasks tab still hands over to the 667 assignment screen (161) until
 * its OSRS layout is built.
 *
 * Rules (slayer_rewards_unlock_confirm / script413): a locked reward shows options_radio_buttons 4 and "Unlock"; an unlocked one 6,
 * with "Disable" only when the reward can be switched off (dbtable column 5) - "Really disable it? You will not get your points
 * back."; every choice goes through the confirm panel ("Pay N points?").
 */
object SlayerRewardsUi {
    private const val EVENTS_OP1 = 0x2
    private const val EVENTS_BUY = 0x2 or 0x4 or 0x8 or 0x10 or 0x20 or (1 shl 10)

    const val TAB_UNLOCK = 0
    const val TAB_EXTEND = 1
    const val TAB_BUY = 2
    const val TAB_TASKS = 3

    private val TAB = AttributeKey<Int>()
    private val PENDING = AttributeKey<Pending>()

    /** What the confirm panel will do. */
    data class Pending(val kind: Kind, val index: Int, val cost: Int)

    enum class Kind { UNLOCK, DISABLE, EXTEND, DISABLE_EXTEND, EXTEND_ALL }

    /** Buy tab: [T.BUY] (the tool bakes the same list's menus). */
    private val BUY get() = T.BUY

    // ---- reward state ----
    private val UNLOCK_KEYS: Map<Int, AttributeKey<Boolean>> =
        mapOf(7 to BROAD_FLETCHING, 6 to CRAFT_ROS, 17 to Krystilia.AVIANSIES, 19 to Krystilia.BOSSES, 52 to Krystilia.MORE_SLAYER)

    fun unlocked(player: Player, reward: T.Reward): Boolean = player.attr[UNLOCK_KEYS.getValue(reward.id)] == true

    private fun identifier(extend: T.Extend): String =
        gg.rsmod.game.model.combat.SlayerAssignment.valueOf(extend.assignment).identifier

    fun extended(player: Player, extend: T.Extend): Boolean = identifier(extend) in player.attr[Krystilia.EXTENDED].orEmpty()

    // ---- open / draw ----
    fun open(player: Player, tab: Int = TAB_UNLOCK) {
        player.openInterface(T.INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(T.INTERFACE_ID, gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.CLOSE, -1..-1, EVENTS_OP1)
        T.TAB_NAMES.indices.forEach { player.setInterfaceEvents(T.INTERFACE_ID, T.TAB_FIRST + 2 * it, -1..-1, EVENTS_OP1) }
        (T.UNLOCKS.indices).forEach { i -> enableBox(player, T.UNLOCK_FIRST_BOX + i * T.BOX_STRIDE) }
        (T.EXTENDS.indices).forEach { i -> enableBox(player, T.EXTEND_FIRST_BOX + i * T.BOX_STRIDE) }
        player.setInterfaceEvents(T.INTERFACE_ID, T.EXTEND_BAR, -1..-1, EVENTS_OP1)
        BUY.indices.forEach { player.setInterfaceEvents(T.INTERFACE_ID, T.BUY_FIRST + it * 2, -1..-1, EVENTS_BUY) }
        player.setInterfaceEvents(T.INTERFACE_ID, T.CONFIRM_BACK, -1..-1, EVENTS_OP1)
        T.CONFIRM_OPS.indices.forEach { player.setInterfaceEvents(T.INTERFACE_ID, T.CONFIRM_OPS_FIRST + it, -1..-1, EVENTS_OP1) }
        player.attr.remove(PENDING)
        player.setComponentHidden(T.INTERFACE_ID, T.CONFIRM, true)
        drawBuy(player)
        refresh(player)
        selectTab(player, tab)
    }

    private fun enableBox(player: Player, first: Int) {
        player.setInterfaceEvents(T.INTERFACE_ID, first + T.PART_UNLOCK_OP, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(T.INTERFACE_ID, first + T.PART_DISABLE_OP, -1..-1, EVENTS_OP1)
    }

    fun selectTab(player: Player, tab: Int) {
        if (tab == TAB_TASKS) {
            // The OSRS Tasks tab is a later step: the 667 assignment screen keeps Cancel / Block working.
            openAssignment(player)
            return
        }
        player.attr[TAB] = tab
        T.TAB_NAMES.indices.forEach { player.setComponentSprite(T.INTERFACE_ID, T.TAB_FIRST + 2 * it, if (it == tab) T.TAB_ON else T.TAB_OFF) }
        player.setComponentHidden(T.INTERFACE_ID, T.UNLOCK_LAYER, tab != TAB_UNLOCK)
        player.setComponentHidden(T.INTERFACE_ID, T.EXTEND_LAYER, tab != TAB_EXTEND)
        player.setComponentHidden(T.INTERFACE_ID, T.BUY_LAYER, tab != TAB_BUY)
        player.setComponentHidden(T.INTERFACE_ID, T.TASKS_LAYER, true)
        val (scroll, scrollbar, height) =
            when (tab) {
                TAB_UNLOCK -> Triple(T.UNLOCK_SCROLL, T.UNLOCK_SCROLLBAR, unlockHeight)
                TAB_EXTEND -> Triple(T.EXTEND_SCROLL, T.EXTEND_SCROLLBAR, extendHeight)
                else -> Triple(T.BUY_SCROLL, T.BUY_SCROLLBAR, buyHeight)
            }
        player.runClientScript(
            gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
            (T.INTERFACE_ID shl 16) or scroll, (T.INTERFACE_ID shl 16) or scrollbar, height,
        )
    }

    /** Scroll heights: slayer_rewards_unlock_draw's total (0 when it fits, like if_setscrollsize(0, 0)). */
    private val unlockHeight: Int by lazy { contentHeight(T.UNLOCKS, T.CONTENT_HEIGHT) }
    private val extendHeight: Int by lazy { contentHeight(T.EXTENDS.map { it.reward }, T.EXTEND_SCROLL_HEIGHT) }
    private val buyHeight: Int get() = ((BUY.size + 4) / 5 * (32 + 35) - 35 + 15).let { if (it > T.CONTENT_HEIGHT) it else 0 }

    private fun contentHeight(rewards: List<T.Reward>, visible: Int): Int {
        // The tool baked the boxes with the cache's p11 metrics; the last box's bottom is the content height.
        val first = if (rewards === T.UNLOCKS) T.UNLOCK_FIRST_BOX else T.EXTEND_FIRST_BOX
        val heights = boxBottoms.getValue(first)
        return if (heights > visible) heights else 0
    }

    /** Bottom of the last box per list, filled by [bake] from the same arithmetic the tool used. */
    private val boxBottoms = HashMap<Int, Int>()

    /** Called once at plugin load with the cache's p11 metrics (so the server's scroll heights equal the baked layout). */
    fun bake(p11Lines: (String, Int) -> Int) {
        boxBottoms[T.UNLOCK_FIRST_BOX] = T.place(T.UNLOCKS, T.UNLOCK_FIRST_BOX, p11Lines).second
        boxBottoms[T.EXTEND_FIRST_BOX] = T.place(T.EXTENDS.map { it.reward }, T.EXTEND_FIRST_BOX, p11Lines).second
    }

    fun refresh(player: Player) {
        player.setComponentText(T.INTERFACE_ID, T.POINTS, "Reward points: ${"%,d".format(java.util.Locale.ENGLISH, player.getSlayerPointsCount())}")
        T.UNLOCKS.forEachIndexed { i, r -> drawBox(player, T.UNLOCK_FIRST_BOX + i * T.BOX_STRIDE, r, unlocked(player, r)) }
        T.EXTENDS.forEachIndexed { i, e -> drawBox(player, T.EXTEND_FIRST_BOX + i * T.BOX_STRIDE, e.reward, extended(player, e)) }
        drawExtendAll(player)
    }

    private fun drawBox(player: Player, first: Int, reward: T.Reward, unlocked: Boolean) {
        player.setComponentItem(T.INTERFACE_ID, first + T.PART_ICON, reward.icon, 1)
        player.setComponentSprite(T.INTERFACE_ID, first + T.PART_STATUS, if (unlocked) T.STATUS_UNLOCKED else T.STATUS_LOCKED)
        player.setComponentHidden(T.INTERFACE_ID, first + T.PART_UNLOCK_OP, unlocked)
        player.setComponentHidden(T.INTERFACE_ID, first + T.PART_DISABLE_OP, !(unlocked && reward.canDisable))
    }

    /** slayer_rewards_etcetera_draw: the remaining extensions, 5% off when more than one. */
    private fun remainingExtends(player: Player): Pair<Int, Int> {
        val left = T.EXTENDS.filterNot { extended(player, it) }
        var cost = left.sumOf { it.reward.points }
        if (left.size > 1) cost = (cost * 95 / 100).coerceAtLeast(2)
        return left.size to cost
    }

    private fun drawExtendAll(player: Player) {
        val (count, cost) = remainingExtends(player)
        val text =
            when {
                count > 1 && cost > 0 -> "Extend remaining $count tasks: ${"%,d".format(java.util.Locale.ENGLISH, cost)} points"
                count == 1 && cost > 0 -> "Extend remaining task: ${"%,d".format(java.util.Locale.ENGLISH, cost)} points"
                else -> "Nothing more to extend"
            }
        player.setComponentText(T.INTERFACE_ID, T.EXTEND_BAR_TEXT, if (count > 0 && cost > 0) "<col=ff981f>$text</col>" else "<col=7f4c0f>$text</col>")
    }

    private fun drawBuy(player: Player) {
        for (slot in 0 until T.BUY_SLOTS) {
            val item = BUY.getOrNull(slot)
            player.setComponentHidden(T.INTERFACE_ID, T.BUY_FIRST + slot * 2, item == null)
            player.setComponentHidden(T.INTERFACE_ID, T.BUY_FIRST + slot * 2 + 1, item == null)
            if (item == null) continue
            player.setComponentItem(T.INTERFACE_ID, T.BUY_FIRST + slot * 2, item.item, item.amount)
            player.setComponentText(T.INTERFACE_ID, T.BUY_FIRST + slot * 2 + 1, "%,d".format(java.util.Locale.ENGLISH, item.points))
        }
    }

    // ---- clicks ----
    /**
     * The menu entry (op1..op10) a 667 IF_BUTTON packet carries, by its opcode (IfButton1Handler FIRST_OPTION 61, SECOND 64, THIRD 4,
     * FOURTH 18, FIFTH 10; 25 = op10 "Examine", testclient 2026-11-05: the packet's option index is not the op number).
     */
    fun opOf(opcode: Int): Int =
        when (opcode) {
            61 -> 1
            64 -> 2
            4 -> 3
            18 -> 4
            10 -> 5
            25 -> 10
            else -> 0
        }

    fun button(player: Player, component: Int, option: Int) {
        when {
            component == gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.CLOSE -> player.closeInterface(T.INTERFACE_ID)
            component in T.TAB_FIRST until T.TAB_FIRST + 2 * T.TAB_NAMES.size -> selectTab(player, (component - T.TAB_FIRST) / 2)
            component == T.EXTEND_BAR -> askExtendAll(player)
            component in T.BUY_FIRST until T.BUY_FIRST + 2 * T.BUY_SLOTS -> buy(player, (component - T.BUY_FIRST) / 2, option)
            component == T.CONFIRM_BACK -> back(player)
            component in T.CONFIRM_OPS_FIRST until T.CONFIRM_OPS_FIRST + T.CONFIRM_OPS.size -> confirm(player)
            component >= T.UNLOCK_FIRST_BOX && component < T.EXTEND_FIRST_BOX -> boxClick(player, (component - T.UNLOCK_FIRST_BOX) / T.BOX_STRIDE, extend = false)
            component >= T.EXTEND_FIRST_BOX && component < T.COMPONENT_COUNT -> boxClick(player, (component - T.EXTEND_FIRST_BOX) / T.BOX_STRIDE, extend = true)
        }
    }

    private fun boxClick(player: Player, index: Int, extend: Boolean) {
        val reward = if (extend) T.EXTENDS[index].reward else T.UNLOCKS[index]
        val have = if (extend) extended(player, T.EXTENDS[index]) else unlocked(player, reward)
        val text: String
        val pending: Pending
        if (!have) {
            text = "<u=ff981f>${reward.name}</u><br><br>${reward.description}<br><br><col=ff0000>Pay ${"%,d".format(java.util.Locale.ENGLISH, reward.points)} points?</col>"
            pending = Pending(if (extend) Kind.EXTEND else Kind.UNLOCK, index, reward.points)
        } else if (reward.canDisable) {
            text = "<u=ff981f>${reward.name}</u><br><br>Really disable it?<br><br><col=ff0000>You will not get your points back.</col>"
            pending = Pending(if (extend) Kind.DISABLE_EXTEND else Kind.DISABLE, index, 0)
        } else {
            return
        }
        showConfirm(player, text, pending, if (pending.kind == Kind.DISABLE || pending.kind == Kind.DISABLE_EXTEND) "Disable" else "Unlock")
    }

    private fun askExtendAll(player: Player) {
        val (count, cost) = remainingExtends(player)
        if (count == 0 || cost == 0) return
        val text = "<u=ff981f>Extend everything</u><br><br>All task extensions will be unlocked. You can re-lock them individually later, but will not receive a refund.<br><br><col=ff0000>Pay $cost points?</col>"
        showConfirm(player, text, Pending(Kind.EXTEND_ALL, -1, cost), "Confirm")
    }

    /** slayer_rewards_buy_op Info: name, the OSRS description and the cost; only Back, moved to the middle (92, 165). */
    private fun showInfo(player: Player, item: T.BuyItem) {
        player.attr.remove(PENDING)
        val cost = if (item.amount > 1) "Cost: ${item.points} points for ${item.amount}" else "Cost: ${item.points} points each"
        player.setComponentText(T.INTERFACE_ID, T.CONFIRM_TEXT, "<u=ff981f>${item.name}</u><br><br>${item.info}<br><br>$cost")
        player.setComponentHidden(T.INTERFACE_ID, T.CONFIRM_OK, true)
        player.setComponentPosition(T.INTERFACE_ID, T.CONFIRM_BACK, 92, 165)
        player.setComponentHidden(T.INTERFACE_ID, T.PANEL, true)
        player.setComponentHidden(T.INTERFACE_ID, T.CONFIRM, false)
    }

    private fun showConfirm(player: Player, text: String, pending: Pending, op: String) {
        player.setComponentHidden(T.INTERFACE_ID, T.CONFIRM_OK, false)
        player.setComponentPosition(T.INTERFACE_ID, T.CONFIRM_BACK, 33, 165)
        player.attr[PENDING] = pending
        player.setComponentText(T.INTERFACE_ID, T.CONFIRM_TEXT, text)
        T.CONFIRM_OPS.forEachIndexed { i, name -> player.setComponentHidden(T.INTERFACE_ID, T.CONFIRM_OPS_FIRST + i, name != op) }
        player.setComponentHidden(T.INTERFACE_ID, T.PANEL, true)
        player.setComponentHidden(T.INTERFACE_ID, T.CONFIRM, false)
    }

    private fun back(player: Player) {
        player.attr.remove(PENDING)
        player.setComponentHidden(T.INTERFACE_ID, T.CONFIRM, true)
        player.setComponentHidden(T.INTERFACE_ID, T.PANEL, false)
    }

    private fun confirm(player: Player) {
        val pending = player.attr[PENDING] ?: return back(player)
        player.attr.remove(PENDING)
        if (pending.cost > 0 && !player.subtractSlayerPoints(pending.cost)) {
            player.message("You need ${pending.cost} points to unlock this.")
            return back(player)
        }
        when (pending.kind) {
            Kind.UNLOCK -> player.attr[UNLOCK_KEYS.getValue(T.UNLOCKS[pending.index].id)] = true
            Kind.DISABLE -> player.attr.remove(UNLOCK_KEYS.getValue(T.UNLOCKS[pending.index].id))
            Kind.EXTEND -> setExtended(player, T.EXTENDS[pending.index], true)
            Kind.DISABLE_EXTEND -> setExtended(player, T.EXTENDS[pending.index], false)
            Kind.EXTEND_ALL -> T.EXTENDS.forEach { setExtended(player, it, true) }
        }
        back(player)
        refresh(player)
    }

    private fun setExtended(player: Player, extend: T.Extend, on: Boolean) {
        val list = (player.attr[Krystilia.EXTENDED] ?: mutableListOf()).toMutableList()
        val id = identifier(extend)
        if (on && id !in list) list += id
        if (!on) list.remove(id)
        player.attr[Krystilia.EXTENDED] = list
    }

    /** Buy: op 1 Info, 2-5 Buy 1 / 5 / 10 / 50 (sets for the bolts and arrows), 10 Examine. */
    private fun buy(player: Player, slot: Int, option: Int) {
        val item = BUY.getOrNull(slot) ?: return
        val name = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.item).name
        when (option) {
            1 -> showInfo(player, item)
            10 -> player.message(player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.item).examine ?: name)
            in 2..5 -> {
                if (item.singleOnly && option != 2) return
                val wanted = intArrayOf(1, 5, 10, 50)[option - 2]
                var bought = 0
                for (i in 0 until wanted) {
                    if (player.getSlayerPointsCount() < item.points) break
                    // The item first (all or nothing), then the points; a failed payment takes the item back.
                    if (player.inventory.add(item.item, item.amount, assureFullInsertion = true).hasFailed()) break
                    if (!player.subtractSlayerPoints(item.points)) {
                        player.inventory.remove(item.item, item.amount, assureFullRemoval = true)
                        break
                    }
                    bought++
                }
                if (bought == 0) {
                    player.message(if (player.getSlayerPointsCount() < item.points) "You need ${item.points} points to buy this." else "You don't have enough inventory space.")
                }
                refresh(player)
            }
        }
    }

    private fun openAssignment(player: Player) {
        SlayerRewardsBridge.openAssignment?.invoke(player)
    }
}

/** The 667 assignment screen lives in slayer_rewards.plugin.kts; the script registers its opener here. */
object SlayerRewardsBridge {
    var openAssignment: ((Player) -> Unit)? = null
}
