package gg.rsmod.plugins.content.inter.bank

import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * E7 (owner 2026-10-03, "onze bank moet precies functioneren als osrs bank"): the OSRS bank controls the revision-667 bank
 * never had - the quantity toggle (1 / 5 / 10 / X / All, left-click quantity of withdrawals and deposits), bank placeholders
 * ('Placeholder', 'Always set placeholders', 'Release', 'Release all placeholders', tab 'Remove placeholders').
 *
 * Every string and every menu layout below is read from the pinned OSRS cache (import-source/openrs2-2686, read-only):
 * - script 669 builds a bank item's ops: op1 "Withdraw-" + the default quantity (script 2579: "1" / "5" / "10" /
 *   max(1, custom) / "All"), op2 "Withdraw-1" only when the default is not 1, op3 "Withdraw-5", op4 "Withdraw-10",
 *   op5 "Withdraw-<custom>" only once a custom amount is set, op6 "Withdraw-X", op7 "Withdraw-All", op8
 *   "Withdraw-All-but-1", then "Placeholder" while 'Always set placeholders' is off;
 * - script 278: a placeholder is the item with count 0, transparency 120, op8 "Release" and "Examine";
 * - script 172 (bank side inventory): op1 the default quantity, then "Deposit-1" / "Deposit-5" / "Deposit-10" /
 *   "Deposit-X" / "Deposit-All", the default's own entry left out (except X);
 * - interface 12:29-37 "Default quantity: 1" / "5" / "10" / "All" and "Set custom quantity", 12:40
 *   "<col=ff9040>Always set placeholders</col>", script 1272 "Release all placeholders", scripts 506 / 507 tab
 *   "Remove placeholders".
 * The 667 interface has no components for them, so the 78 client builds the buttons ([SCRIPT_BUTTON], RspsBank.java) and
 * patches the cache's 1453 (bank item ops) and 1540 (bank side inventory ops) with the same layout as [withdrawAction] /
 * [depositAction]. The server names the state on the side channel `__RSPS_BANK__:state|<mode>|<always placeholder>`.
 */
object BankOptions {
    const val MODE_ONE = 0
    const val MODE_FIVE = 1
    const val MODE_TEN = 2
    const val MODE_X = 3
    const val MODE_ALL = 4

    /** Persisted like OSRS (varbit 6590 'quantity' and 3755 'always set placeholders' both survive logout). */
    val QUANTITY_MODE_ATTR = AttributeKey<Int>("bank_quantity_mode")
    val ALWAYS_PLACEHOLDER_ATTR = AttributeKey<Int>("bank_always_placeholder")

    /** Side channel to the 78 client (console line, like `__RSPS_GE__:`). */
    const val CLIENT_PREFIX = "__RSPS_BANK__:"

    /** RspsBank.SCRIPT_BUTTON: ints (layer, firstChild, x, y, width, height, yMode, selected, background, font), strings (label, op1, op2, opBase). */
    const val SCRIPT_BUTTON = 40110

    /** RspsGrandExchange.SCRIPT_SET_OP: (component, op index; text) on a static component. */
    const val SCRIPT_SET_OP = 40104

    /** Cache 1451: the bank's own throttled rebuild (onInvTransmit of 762:93), which runs 1453 per item. */
    const val SCRIPT_REBUILD_BANK = 1451

    /** Cache 1448 (onInvTransmit of 763:0): rebuilds the bank side inventory through 1540. */
    const val SCRIPT_REBUILD_SIDE_INVENTORY = 1448

    /** IF_BUTTON opcodes of ops 1..10 (client ClientProt IF_BUTTON1..10). */
    const val OP1 = 61
    const val OP2 = 64
    const val OP3 = 4
    const val OP4 = 52
    const val OP5 = 81
    const val OP6 = 91
    const val OP7 = 18
    const val OP8 = 10
    const val OP9 = 20
    const val OP10 = 25

    /** 762:93 item events: ops 1-10 (0x7FE) plus the cache's drag bits 0x80000 / 0x200000. Ops 8 and 9 are new. */
    const val BANK_ITEM_EVENTS = 0x2807FE

    /** IF_SETEVENTS bits of a client-built button label: op1 = 2, op2 = 4. */
    const val BUTTON_EVENTS = 2 or 4

    /** Bank tabs 762:46..60 (cache events 0x200006: op1, op2, drop target) get op3 "Remove placeholders". */
    val TAB_COMPONENTS = intArrayOf(46, 48, 50, 52, 54, 56, 58, 60)
    const val TAB_EVENTS = 0x200006 or 8

    /** 762:62 'View all' (cache events 0x200002) gets op2 "Remove placeholders". */
    const val VIEW_ALL_COMPONENT = 62
    const val VIEW_ALL_EVENTS = 0x200002 or 4

    const val REMOVE_PLACEHOLDERS = "Remove placeholders"
    const val RELEASE_ALL_PLACEHOLDERS = "Release all placeholders"
    const val SET_CUSTOM_QUANTITY = "Set custom quantity"
    const val PLACEHOLDER_OPBASE = "<col=ff9040>Always set placeholders</col>"

    enum class WithdrawAction { DEFAULT, ONE, FIVE, TEN, CUSTOM, X, ALL, ALL_BUT_ONE, PLACEHOLDER, RELEASE, EXAMINE }

    enum class DepositAction { DEFAULT, ONE, FIVE, TEN, X, ALL, EXAMINE }

    fun quantityMode(player: Player): Int = ((player.attr[QUANTITY_MODE_ATTR] as Number?)?.toInt() ?: MODE_ONE).takeIf { it in MODE_ONE..MODE_ALL } ?: MODE_ONE

    fun alwaysPlaceholder(player: Player): Boolean = ((player.attr[ALWAYS_PLACEHOLDER_ATTR] as Number?)?.toInt() ?: 0) == 1

    fun customQuantity(player: Player): Int = player.getVarp(Bank.LAST_X_INPUT)

    /** The bank item op (762:93) behind [opcode]; a placeholder (count 0) only has "Release" (op8) and "Examine". */
    fun withdrawAction(
        opcode: Int,
        placeholder: Boolean,
    ): WithdrawAction? {
        if (placeholder) {
            return when (opcode) {
                OP8 -> WithdrawAction.RELEASE
                OP10 -> WithdrawAction.EXAMINE
                else -> null
            }
        }
        return when (opcode) {
            OP1 -> WithdrawAction.DEFAULT
            OP2 -> WithdrawAction.ONE
            OP3 -> WithdrawAction.FIVE
            OP4 -> WithdrawAction.TEN
            OP5 -> WithdrawAction.CUSTOM
            OP6 -> WithdrawAction.X
            OP7 -> WithdrawAction.ALL
            OP8 -> WithdrawAction.ALL_BUT_ONE
            OP9 -> WithdrawAction.PLACEHOLDER
            OP10 -> WithdrawAction.EXAMINE
            else -> null
        }
    }

    /** The bank side inventory op (763:0) behind [opcode] (OSRS script 172 layout). */
    fun depositAction(opcode: Int): DepositAction? =
        when (opcode) {
            OP1 -> DepositAction.DEFAULT
            OP2 -> DepositAction.ONE
            OP3 -> DepositAction.FIVE
            OP4 -> DepositAction.TEN
            OP5 -> DepositAction.X
            OP6 -> DepositAction.ALL
            OP10 -> DepositAction.EXAMINE
            else -> null
        }

    /**
     * The amount the left-click (op1) moves: 1 / 5 / 10 / the custom amount (at least 1, OSRS script 2579 max(1, n)) / all
     * of [available].
     */
    fun defaultAmount(
        mode: Int,
        custom: Int,
        available: Int,
    ): Int =
        when (mode) {
            MODE_FIVE -> 5
            MODE_TEN -> 10
            MODE_X -> maxOf(1, custom)
            MODE_ALL -> available
            else -> 1
        }

    /** The withdraw menu exactly as the patched 1453 draws it (index 0 = op1); "" = no such op. */
    fun withdrawMenu(
        mode: Int,
        custom: Int,
        alwaysPlaceholder: Boolean,
    ): List<String> =
        listOf(
            "Withdraw-" + quantityText(mode, custom),
            if (mode == MODE_ONE) "" else "Withdraw-1",
            "Withdraw-5",
            "Withdraw-10",
            if (custom > 0) "Withdraw-$custom" else "",
            "Withdraw-X",
            "Withdraw-All",
            "Withdraw-All-but-1",
            if (alwaysPlaceholder) "" else "Placeholder",
            "Examine",
        )

    /** The deposit menu exactly as the patched 1540 draws it (index 0 = op1); "" = no such op. */
    fun depositMenu(
        mode: Int,
        custom: Int,
    ): List<String> =
        listOf(
            "Deposit-" + quantityText(mode, custom),
            if (mode == MODE_ONE) "" else "Deposit-1",
            if (mode == MODE_FIVE) "" else "Deposit-5",
            if (mode == MODE_TEN) "" else "Deposit-10",
            "Deposit-X",
            if (mode == MODE_ALL) "" else "Deposit-All",
        )

    fun quantityText(
        mode: Int,
        custom: Int,
    ): String =
        when (mode) {
            MODE_FIVE -> "5"
            MODE_TEN -> "10"
            MODE_X -> maxOf(1, custom).toString()
            MODE_ALL -> "All"
            else -> "1"
        }

    /** One client-built button (RspsBank.SCRIPT_BUTTON): a background graphic at [firstChild], the clickable label at [firstChild] + 1. */
    data class Button(
        val firstChild: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val yMode: Int,
        val selected: Boolean,
        val background: Boolean,
        val font: Int,
        val label: String,
        val op1: String,
        val op2: String = "",
        val opBase: String = "",
    ) {
        val labelChild: Int get() = firstChild + 1

        fun args(): Array<Any> =
            arrayOf(
                BankOptions.ROOT,
                firstChild,
                x,
                y,
                width,
                height,
                yMode,
                if (selected) 1 else 0,
                if (background) 1 else 0,
                font,
                label,
                op1,
                op2,
                opBase,
            )
    }

    /** Every button is a child of the bank's root layer 762:0 (a click arrives as IF_BUTTON 762:0, slot = the label child). */
    const val ROOT = Bank.BANK_INTERFACE_ID shl 16

    /** From the bottom edge, like the 667 bar buttons (762:15 / 17 / 19 / 33-37 are y 12 from the bottom, 35 high). */
    private const val Y_FROM_BOTTOM = 2

    /**
     * Quantity buttons in the free part of the bottom bar, between Search (762:17, x 64..99) and the item/note toggle
     * (762:19, centred: x 238..273): five 25 x 22 buttons, centred on the bar's 35-pixel height.
     */
    private const val QUANTITY_X = 104
    private const val QUANTITY_STEP = 26
    private const val QUANTITY_Y = 18
    private const val QUANTITY_W = 25
    private const val QUANTITY_H = 22
    private const val FONT_QUANTITY = 495 // OSRS 12:30-38 use font 495 for the quantity labels
    private const val FONT_SMALL = 494 // the 667 bank's own small font (762:25-32)

    const val QUANTITY_FIRST_CHILD = 0
    const val PLACEHOLDER_FIRST_CHILD = 10

    private val QUANTITY_LABELS = listOf("1", "5", "10", "X", "All")
    private val QUANTITY_OPS = listOf("Default quantity: 1", "Default quantity: 5", "Default quantity: 10", "Default quantity: X", "Default quantity: All")

    fun buttons(
        mode: Int,
        alwaysPlaceholder: Boolean,
    ): List<Button> {
        val quantity =
            QUANTITY_LABELS.mapIndexed { index, label ->
                Button(
                    firstChild = QUANTITY_FIRST_CHILD + index * 2,
                    x = QUANTITY_X + index * QUANTITY_STEP,
                    y = QUANTITY_Y,
                    width = QUANTITY_W,
                    height = QUANTITY_H,
                    yMode = Y_FROM_BOTTOM,
                    selected = index == mode,
                    background = true,
                    font = FONT_QUANTITY,
                    label = label,
                    op1 = QUANTITY_OPS[index],
                    op2 = if (index == MODE_X) SET_CUSTOM_QUANTITY else "",
                )
            }
        // The title row left of "Bank of RuneScape" (762:45 is centred in x 118..391; tabs start at y 33) is free.
        val placeholder =
            Button(
                firstChild = PLACEHOLDER_FIRST_CHILD,
                x = 22,
                y = 21,
                width = 110,
                height = 13,
                yMode = 0,
                selected = alwaysPlaceholder,
                background = false,
                font = FONT_SMALL,
                label = if (alwaysPlaceholder) "Placeholders: On" else "Placeholders: Off",
                op1 = if (alwaysPlaceholder) "Disable" else "Enable",
                op2 = RELEASE_ALL_PLACEHOLDERS,
                opBase = PLACEHOLDER_OPBASE,
            )
        return quantity + placeholder
    }

    /** Names the state to the 78 client (it bakes it into the patched 1453 / 1540) - before the bank is drawn. */
    fun sendState(player: Player) {
        val ph = if (alwaysPlaceholder(player)) 1 else 0
        player.write(MessageGameMessage(type = 99, message = "${CLIENT_PREFIX}state|${quantityMode(player)}|$ph", username = null))
    }

    /** Lays out the buttons and the tab ops; called after 762 is open. */
    fun sendControls(player: Player) {
        buttons(quantityMode(player), alwaysPlaceholder(player)).forEach { button ->
            player.runClientScript(SCRIPT_BUTTON, *button.args())
            player.setInterfaceEvents(
                interfaceId = Bank.BANK_INTERFACE_ID,
                component = 0,
                range = button.labelChild..button.labelChild,
                setting = BUTTON_EVENTS,
            )
        }
        TAB_COMPONENTS.forEach { component ->
            player.runClientScript(SCRIPT_SET_OP, ROOT or component, 3, REMOVE_PLACEHOLDERS)
            player.setInterfaceEvents(interfaceId = Bank.BANK_INTERFACE_ID, component = component, range = -1..-1, setting = TAB_EVENTS)
        }
        player.runClientScript(SCRIPT_SET_OP, ROOT or VIEW_ALL_COMPONENT, 2, REMOVE_PLACEHOLDERS)
        player.setInterfaceEvents(interfaceId = Bank.BANK_INTERFACE_ID, component = VIEW_ALL_COMPONENT, range = -1..-1, setting = VIEW_ALL_EVENTS)
    }

    /** After a state change: new state, redrawn buttons, and both item menus rebuilt with the new layout. */
    fun refresh(player: Player) {
        sendState(player)
        if (!player.interfaces.isVisible(Bank.BANK_INTERFACE_ID)) return
        sendControls(player)
        player.runClientScript(SCRIPT_REBUILD_BANK)
        player.runClientScript(SCRIPT_REBUILD_SIDE_INVENTORY, Bank.INV_INTERFACE_ID shl 16)
    }

    /**
     * N137: the OSRS bank scripts click (interface_select1) on their buttons - bankmain_quantity_op (2225) only when the quantity
     * really changes, bankmain_toggleplaceholders_op (1269) on every toggle, bankmain_itemnote_op (291) / bankmain_swapinsert_op
     * (288) on every switch, bankmain_switchtab (504) when the tab really changes, bankmain_viewbuttons (3275). The 667 bank
     * scripts (762/763) have no sound op, so the server sends it.
     */
    const val CLICK_SOUND = gg.rsmod.plugins.api.cfg.Sfx.INTERFACE_SELECT1

    fun click(player: Player) = player.playSound(CLICK_SOUND)

    fun setQuantityMode(
        player: Player,
        mode: Int,
    ) {
        if (mode !in MODE_ONE..MODE_ALL) return
        if (quantityMode(player) != mode) click(player)
        player.attr[QUANTITY_MODE_ATTR] = mode
        refresh(player)
    }

    fun setAlwaysPlaceholder(
        player: Player,
        on: Boolean,
    ) {
        click(player)
        player.attr[ALWAYS_PLACEHOLDER_ATTR] = if (on) 1 else 0
        refresh(player)
    }

    /** What a click on a client-built button (IF_BUTTON 762:0, slot [child]) does; null when [child] is no button. */
    enum class ButtonPress { QUANTITY, SET_CUSTOM, TOGGLE_PLACEHOLDER, RELEASE_ALL }

    fun pressed(
        child: Int,
        opcode: Int,
    ): Pair<ButtonPress, Int>? {
        val index = (child - 1 - QUANTITY_FIRST_CHILD) / 2
        if (child in (QUANTITY_FIRST_CHILD + 1)..(QUANTITY_FIRST_CHILD + 9) && (child - QUANTITY_FIRST_CHILD) % 2 == 1) {
            return when {
                opcode == OP1 -> ButtonPress.QUANTITY to index
                opcode == OP2 && index == MODE_X -> ButtonPress.SET_CUSTOM to MODE_X
                else -> null
            }
        }
        if (child == PLACEHOLDER_FIRST_CHILD + 1) {
            return when (opcode) {
                OP1 -> ButtonPress.TOGGLE_PLACEHOLDER to 0
                OP2 -> ButtonPress.RELEASE_ALL to 0
                else -> null
            }
        }
        return null
    }
}
