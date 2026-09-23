package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool as Layout
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * The OSRS "Loot keys" chest screen (OSRS interface 742) on revision-667 interface [INTERFACE_ID],
 * which `LootKeyInterfaceImportTool` builds into the cache with the OSRS sprites. Every control is
 * static, so this object drives all of it with plain interface packets:
 *
 *  - the five key tabs: [TAB_FIRST] + 3i = tab background (selected / unselected sprite),
 *    +1 = the key item, +2 = the loot value under it ("53k");
 *  - [SLOT_FIRST]..+27: the viewed key's loot, one item per slot (noted form in Note mode when the
 *    item has one), with Withdraw-1/5/10/All/X and Examine;
 *  - "This chest is now empty." over the grid when the viewed key holds nothing;
 *  - Destroy (with the OSRS confirm dialog), Withdraw as Item/Note, Withdraw all to inventory /
 *    bank, and the bank space counter between them.
 *
 * Loot storage itself stays in [LootKeys] (persistent slots keyed by the key item); this is only
 * the view and the button handling.
 */
object LootKeyChest {
    const val INTERFACE_ID = Layout.INTERFACE_ID

    const val CLOSE = Layout.CLOSE
    const val TAB_FIRST = Layout.TAB_FIRST
    const val TAB_STRIDE = Layout.TAB_STRIDE
    const val EMPTY_LAYER = Layout.EMPTY_LAYER
    const val SLOT_FIRST = Layout.SLOT_FIRST
    const val SLOT_COUNT = Layout.SLOT_COUNT
    const val DESTROY_BUTTON = Layout.DESTROY_BUTTON
    const val ITEM_LAYER = Layout.ITEM_LAYER
    const val NOTE_LAYER = Layout.NOTE_LAYER
    const val INVENTORY_BUTTON = Layout.INVENTORY_BUTTON
    const val BANK_BUTTON = Layout.BANK_BUTTON
    const val CONFIRM_LAYER = Layout.CONFIRM_LAYER
    const val CONFIRM_BUTTON = Layout.CONFIRM_BUTTON
    const val CANCEL_BUTTON = Layout.CANCEL_BUTTON

    /** 667 sprite ids of the OSRS sprites the import tool wrote (1079 selected tab, 1080 empty tab, 1229-1234 button caps). */
    private val SPRITE_TAB_SELECTED = Layout.sprite(1079)
    private val SPRITE_TAB_UNSELECTED = Layout.sprite(1080)
    private val SPRITE_GREY_LEFT = Layout.sprite(1229)
    private val SPRITE_GREY_MIDDLE = Layout.sprite(1230)
    private val SPRITE_GREY_RIGHT = Layout.sprite(1231)
    private val SPRITE_RED_LEFT = Layout.sprite(1232)
    private val SPRITE_RED_MIDDLE = Layout.sprite(1233)
    private val SPRITE_RED_RIGHT = Layout.sprite(1234)
    /** IF_SETEVENTS masks: op n is bit n (op1 = 0x2). */
    private const val EVENTS_OP1 = 0x2
    private const val EVENTS_SLOT = 0x2 or 0x4 or 0x8 or 0x10 or 0x20 or 0x400

    const val EMPTY_INVENTORY_MESSAGE = "You don't have enough inventory space."
    const val EMPTY_BANK_MESSAGE = "You don't have enough bank space."
    const val ALL_TO_INVENTORY_MESSAGE = "All the loot has been withdrawn to your inventory."
    const val ALL_TO_BANK_MESSAGE = "All the loot has been sent to your bank."
    const val PARTIAL_INVENTORY_MESSAGE = "Some of the loot didn't fit in your inventory - the rest is still in the key."
    const val PARTIAL_BANK_MESSAGE = "Some of the loot didn't fit in your bank - the rest is still in the key."

    val VIEWED_INDEX = AttributeKey<Int>()
    val NOTE_MODE = AttributeKey<Boolean>(persistenceKey = "loot_key_chest_note_mode")
    private val LAST_SHOWN = AttributeKey<List<Item>>()

    fun isOpen(player: Player): Boolean = player.attr[VIEWED_INDEX] != null

    fun viewedIndex(player: Player): Int? = player.attr[VIEWED_INDEX]

    fun noteMode(player: Player): Boolean = player.attr[NOTE_MODE] == true

    /** The loot slot shown in [component], or -1 when the component is not a loot slot. */
    fun slotOf(component: Int): Int = if (component in SLOT_FIRST until SLOT_FIRST + SLOT_COUNT) component - SLOT_FIRST else -1

    /** The key tab index of [component] (background, item or text), or -1. */
    fun tabOf(component: Int): Int =
        if (component in TAB_FIRST until TAB_FIRST + LootKeys.MAX_KEYS * TAB_STRIDE) (component - TAB_FIRST) / TAB_STRIDE else -1

    /** OSRS tab value text: 1234 -> "1234", 53_400 -> "53k", 2_600_000 -> "2M". */
    fun valueText(value: Long): String =
        when {
            value >= 1_000_000L -> "${value / 1_000_000L}M"
            value >= 1_000L -> "${value / 1_000L}k"
            else -> value.toString()
        }

    fun open(
        player: Player,
        index: Int,
    ) {
        player.attr[VIEWED_INDEX] = index
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, EVENTS_OP1)
        for (tab in 0 until LootKeys.MAX_KEYS) {
            player.setInterfaceEvents(INTERFACE_ID, TAB_FIRST + tab * TAB_STRIDE, -1..-1, EVENTS_OP1)
        }
        for (slot in 0 until SLOT_COUNT) {
            player.setInterfaceEvents(INTERFACE_ID, SLOT_FIRST + slot, -1..-1, EVENTS_SLOT)
        }
        intArrayOf(DESTROY_BUTTON, ITEM_LAYER, NOTE_LAYER, INVENTORY_BUTTON, BANK_BUTTON, CONFIRM_BUTTON, CANCEL_BUTTON).forEach {
            player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, EVENTS_OP1)
        }
        player.setComponentHidden(INTERFACE_ID, CONFIRM_LAYER, hidden = true)
        refresh(player)
    }

    fun close(player: Player) {
        player.attr.remove(VIEWED_INDEX)
        player.attr.remove(LAST_SHOWN)
    }

    /** The item shown for a stored loot item: its note in Note mode when the item has one. */
    fun shown(
        definitions: DefinitionSet,
        item: Item,
        noted: Boolean,
    ): Item = if (noted) item.toNoted(definitions) else item

    /** Re-sends everything that can have changed: tabs, values, slots, empty text, buttons, bank counter. */
    fun refresh(player: Player) {
        val index = viewedIndex(player) ?: return
        val definitions = player.world.definitions
        val noted = noteMode(player)

        for (tab in 0 until LootKeys.MAX_KEYS) {
            val base = TAB_FIRST + tab * TAB_STRIDE
            val held = player.inventory.contains(LootKeys.KEY_IDS[tab])
            player.setComponentSprite(INTERFACE_ID, base, if (tab == index) SPRITE_TAB_SELECTED else SPRITE_TAB_UNSELECTED)
            player.setComponentItem(INTERFACE_ID, base + 1, if (held) LootKeys.KEY_IDS[tab] else -1, 1)
            val value = if (held) LootKeys.value(definitions, LootKeys.slotItems(player, tab)) else 0L
            player.setComponentText(INTERFACE_ID, base + 2, if (held) valueText(value) else "")
        }

        val stored = LootKeys.slotItems(player, index).take(SLOT_COUNT)
        player.attr[LAST_SHOWN] = stored
        for (slot in 0 until SLOT_COUNT) {
            // Owner 2026-09-18 (#2): Note mode never changes the grid - the loot is shown as it is
            // stored; only the item withdrawn to the inventory is noted (moveToInventory).
            val item = stored.getOrNull(slot)
            player.setComponentItem(INTERFACE_ID, SLOT_FIRST + slot, item?.id ?: -1, item?.amount ?: 0)
        }
        player.setComponentHidden(INTERFACE_ID, EMPTY_LAYER, hidden = stored.isNotEmpty())

        player.setComponentSprite(INTERFACE_ID, Layout.ITEM_LEFT, if (noted) SPRITE_GREY_LEFT else SPRITE_RED_LEFT)
        player.setComponentSprite(INTERFACE_ID, Layout.ITEM_MIDDLE, if (noted) SPRITE_GREY_MIDDLE else SPRITE_RED_MIDDLE)
        player.setComponentSprite(INTERFACE_ID, Layout.ITEM_RIGHT, if (noted) SPRITE_GREY_RIGHT else SPRITE_RED_RIGHT)
        player.setComponentSprite(INTERFACE_ID, Layout.NOTE_LEFT, if (noted) SPRITE_RED_LEFT else SPRITE_GREY_LEFT)
        player.setComponentSprite(INTERFACE_ID, Layout.NOTE_MIDDLE, if (noted) SPRITE_RED_MIDDLE else SPRITE_GREY_MIDDLE)
        player.setComponentSprite(INTERFACE_ID, Layout.NOTE_RIGHT, if (noted) SPRITE_RED_RIGHT else SPRITE_GREY_RIGHT)

        player.setComponentText(INTERFACE_ID, Layout.BANK_USED_TEXT, player.bank.occupiedSlotCount.toString())
        player.setComponentText(INTERFACE_ID, Layout.BANK_SIZE_TEXT, player.bank.capacity.toString())
    }

    /** Clicking a key tab views that key; an empty tab does nothing. */
    fun select(
        player: Player,
        tab: Int,
    ) {
        if (tab !in 0 until LootKeys.MAX_KEYS || !player.inventory.contains(LootKeys.KEY_IDS[tab])) return
        player.attr[VIEWED_INDEX] = tab
        refresh(player)
    }

    fun setNoteMode(
        player: Player,
        noted: Boolean,
    ) {
        player.attr[NOTE_MODE] = noted
        refresh(player)
    }

    /** The stored item behind a shown slot, resolved through what was last sent so a stale click cannot hit the wrong item. */
    fun storedAt(
        player: Player,
        slot: Int,
    ): Item? = player.attr[LAST_SHOWN]?.getOrNull(slot)

    /**
     * Withdraws up to [amount] of the item in [slot] to the inventory (noted in Note mode when the
     * item can be noted), the same way the bank does; a key whose last item leaves is used up.
     */
    fun withdraw(
        player: Player,
        slot: Int,
        amount: Int,
    ) {
        val index = viewedIndex(player) ?: return
        val target = storedAt(player, slot) ?: return
        if (amount <= 0) return
        val moved = moveToInventory(player, index, target.id, minOf(amount, target.amount))
        if (moved <= 0) {
            player.message(EMPTY_INVENTORY_MESSAGE)
        }
        finishWithdraw(player, index)
    }

    /**
     * "Withdraw all to inventory": every item in turn, stopping at the first that no longer fits.
     * Owner 2026-09-18 (#4): always reports in the chatbox what happened (OSRS Loot key: "All items
     * have been withdrawn." / the inventory-full message when some had to stay).
     */
    fun withdrawAllToInventory(player: Player) {
        val index = viewedIndex(player) ?: return
        var movedAny = false
        var blocked = false
        for (item in LootKeys.slotItems(player, index)) {
            val moved = moveToInventory(player, index, item.id, item.amount)
            if (moved > 0) movedAny = true
            if (moved < item.amount) {
                blocked = true
                break
            }
        }
        when {
            blocked && !movedAny -> player.message(EMPTY_INVENTORY_MESSAGE)
            blocked -> player.message(PARTIAL_INVENTORY_MESSAGE)
            movedAny -> player.message(ALL_TO_INVENTORY_MESSAGE)
        }
        finishWithdraw(player, index)
    }

    /** "Withdraw all to bank": items go to the bank unnoted, like a bank deposit. Chat feedback as above (#4). */
    fun withdrawAllToBank(player: Player) {
        val index = viewedIndex(player) ?: return
        val definitions = player.world.definitions
        var movedAny = false
        var blocked = false
        for (item in LootKeys.slotItems(player, index)) {
            val unnoted = item.toUnnoted(definitions)
            val added = player.bank.add(unnoted.id, unnoted.amount, assureFullInsertion = false)
            val moved = unnoted.amount - added.getLeftOver()
            if (moved > 0) {
                movedAny = true
                LootKeys.takeFromSlot(player, index, item.id, moved)
            }
            if (moved < item.amount) {
                blocked = true
                break
            }
        }
        when {
            blocked && !movedAny -> player.message(EMPTY_BANK_MESSAGE)
            blocked -> player.message(PARTIAL_BANK_MESSAGE)
            movedAny -> player.message(ALL_TO_BANK_MESSAGE)
        }
        if (movedAny) player.playSound(LootKeys.KEY_BANKED_SOUND) // owner 2026-09-18: "the sound when u bank it"
        finishWithdraw(player, index)
    }

    private fun moveToInventory(
        player: Player,
        index: Int,
        itemId: Int,
        amount: Int,
    ): Int {
        val definitions = player.world.definitions
        val give = shown(definitions, Item(itemId, amount), noteMode(player))
        val added = player.inventory.add(give.id, give.amount, assureFullInsertion = false)
        val moved = give.amount - added.getLeftOver()
        if (moved > 0) LootKeys.takeFromSlot(player, index, itemId, moved)
        return moved
    }

    private fun finishWithdraw(
        player: Player,
        index: Int,
    ) {
        if (LootKeys.slotItems(player, index).isEmpty()) {
            LootKeys.consumeKey(player, index)
        }
        refresh(player)
    }

    // ---- destroy ----

    fun promptDestroy(player: Player) {
        val index = viewedIndex(player) ?: return
        if (LootKeys.slotItems(player, index).isEmpty()) return
        val value = LootKeys.value(player.world.definitions, LootKeys.slotItems(player, index))
        if (!LootKeys.canDestroyHere(value, AreaState.isDangerous(player.tile))) {
            player.message(LootKeys.DESTROY_TOO_VALUABLE_MESSAGE)
            return
        }
        player.setComponentHidden(INTERFACE_ID, CONFIRM_LAYER, hidden = false)
    }

    fun cancelDestroy(player: Player) {
        player.setComponentHidden(INTERFACE_ID, CONFIRM_LAYER, hidden = true)
    }

    fun confirmDestroy(player: Player) {
        val index = viewedIndex(player) ?: return
        player.setComponentHidden(INTERFACE_ID, CONFIRM_LAYER, hidden = true)
        if (LootKeys.slotItems(player, index).isNotEmpty()) {
            LootKeys.destroyed(player, index)
            LootKeys.consumeKey(player, index)
            player.playSound(LootKeys.KEY_DESTROYED_SOUND) // owner 2026-09-18: "the sound when u destroy a key"
        }
        refresh(player)
    }
}
