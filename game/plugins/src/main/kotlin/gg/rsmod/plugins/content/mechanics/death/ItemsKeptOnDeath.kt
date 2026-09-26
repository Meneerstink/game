package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.pvp.LootingBag
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Kept as Layout

/**
 * The OSRS "Items Kept on Death" screen (OSRS interface 4, built as 667 interface [INTERFACE_ID] by
 * `DeathsOfficeInterfaceImportTool`; owner 2026-09-26: "OSRS-scherm bouwen"). The server fills it from [preview], which runs
 * the very same rules as a real death ([DeathResolver] with [DeathRules] keep 1, [UntradeableDeathProtection],
 * [PvpDeathBreakables], [QuiverDeathRules], loot keys and the looting bag), so the screen cannot disagree with a death.
 *
 * Sections and texts are OSRS clientscript `deathkeep_left_redraw` (974): "Items that are KEPT:", "Items that go to your
 * GRAVESTONE: (Fee: ...)", "Items LOST to the player who kills you:", "Items that are DELETED:"; every item has "Check"
 * with the OSRS line for its section (deathkeep_left_* 976-980, 1695). The right panel holds the four toggles of
 * `deathkeep_right_initbutton` (3453): they start at the player's real state and let the player ask "what if".
 */
object ItemsKeptOnDeath {
    const val INTERFACE_ID = DeathsOfficeInterfaceImportTool.KEPT
    const val CLOSE = DeathsOfficeInterfaceImportTool.CLOSE

    private const val EVENTS_OP1 = 0x2

    enum class Section(val header: String) {
        KEPT("Items that are <col=ffffff>KEPT</col>:"),
        GRAVESTONE("Items that go to your <col=ffffff>GRAVESTONE</col>:"),
        LOST("Items <col=ff0000>LOST</col> to the player who kills you:"),
        DELETED("Items that are <col=ff0000>DELETED</col>:"),
    }

    data class Entry(val section: Section, val item: Item, val message: String)

    data class Preview(val entries: List<Entry>, val graveFee: Int, val riskValue: Long)

    /** The four "what if" toggles (protect item, skulled, killed by a player, deep Wilderness). */
    data class Toggles(val protectItem: Boolean, val skulled: Boolean, val killedByPlayer: Boolean, val deepWilderness: Boolean)

    private val TOGGLES = AttributeKey<Toggles>()
    private val SHOWN = AttributeKey<List<Entry>>()

    fun isOpen(player: Player): Boolean = player.attr[TOGGLES] != null

    private fun name(player: Player, itemId: Int): String = player.world.definitions.get(ItemDef::class.java, itemId).name

    private fun spaced(value: Long): String = String.format(java.util.Locale.US, "%,d", value)

    /** Everything a death with [toggles] would do to [player]'s items, without touching them. */
    fun preview(
        player: Player,
        toggles: Toggles,
        value: ItemRiskValueProvider = DeathRules.rankValue(player.world),
    ): Preview {
        val definitions = player.world.definitions
        val pvp = toggles.killedByPlayer
        val resolved =
            DeathResolver.resolve(
                victim = player,
                killer = null,
                itemProtectionActive = toggles.protectItem,
                valueProvider = value,
                contextOverride = if (pvp) DeathContext.WILDERNESS_PVP else DeathContext.PVM_SAFE,
            )
        val (stripped, lostAmmo) = QuiverDeathRules.stripLost(resolved)
        val (afterConversions, converting) = PvpDeathBreakables.split(stripped)
        val (result, untradeables) = UntradeableDeathProtection.splitPvp(definitions, afterConversions, pvp && toggles.deepWilderness)
        val entries = mutableListOf<Entry>()
        fun line(item: Item, one: String, many: String) =
            if (item.amount > 1) "${spaced(item.amount.toLong())} x ${name(player, item.id)}: $many" else "${name(player, item.id)}: $one"
        result.itemRisk.protected.forEach {
            entries += Entry(Section.KEPT, it.item, line(it.item, "You'll protect your most valuable item.", "You'll protect your most valuable item."))
        }
        // PvP untradeables (owner 2026-09-26, OSRS level-20 rule): broken, mangled or emptied ones stay with the player.
        untradeables.forEach { outcome ->
            val item = outcome.slotItem.item
            val coins = spaced(outcome.killerCoins)
            when (outcome.fate) {
                UntradeableFate.BROKEN ->
                    entries += Entry(Section.KEPT, item, line(item, "This item will break. You keep it; the player who kills you receives $coins coins.", "These items will break. You keep them; the player who kills you receives $coins coins."))
                UntradeableFate.MANGLED ->
                    entries += Entry(Section.KEPT, item, line(item, "This locked item will be mangled. You keep it; the player who kills you receives $coins coins.", "These locked items will be mangled. You keep them; the player who kills you receives $coins coins."))
                UntradeableFate.POUCH_EMPTIED ->
                    entries += Entry(Section.KEPT, item, line(item, "You keep the empty pouch; its runes are lost to the player who kills you.", "You keep the empty pouches; their runes are lost to the player who kills you."))
                UntradeableFate.UNCHANGED ->
                    entries += Entry(Section.KEPT, item, line(item, "You'll keep this item.", "You'll keep these items."))
                UntradeableFate.DESTROYED ->
                    entries += Entry(Section.DELETED, item, line(item, "This item will be destroyed; the player who kills you receives $coins coins.", "These items will be destroyed; the player who kills you receives $coins coins."))
            }
        }
        val bag = LootingBag.peekContents(player)
        // Beast-of-burden cargo follows the death like everything else: to the killer (PvP) or into the gravestone (PvM).
        val cargo = gg.rsmod.plugins.content.skills.summoning.BeastOfBurden.activeContainer(player)?.rawItems?.filterNotNull()?.map { Item(it) } ?: emptyList()
        var risk = 0L
        if (pvp) {
            val lost =
                result.itemRisk.lost.map { it.item }.filterNot { LootingBag.isBag(it.id) } + converting.map { it.item } +
                    bag.filterNot { LootingBag.destroyedOnPvpDeath(player, it) } + lostAmmo + cargo
            lost.forEach { item ->
                risk += value.getValue(item.id) * item.amount
                entries += Entry(Section.LOST, item, line(item, "This item will be lost to the player who kills you.", "These items will be lost to the player who kills you."))
            }
            UntradeableDeathProtection.killerLoot(untradeables).forEach { risk += value.getValue(it.id) * it.amount }
            (result.itemRisk.lost.map { it.item }.filter { LootingBag.isBag(it.id) } + bag.filter { LootingBag.destroyedOnPvpDeath(player, it) }).forEach { deleted(player, entries, it) }
        } else {
            // Owner 2026-09-26: everything lost on a PvM death goes to the gravestone, and taking it back is free.
            val toGrave = result.itemRisk.lost.map { it.item }.filterNot { LootKeys.isKey(it.id) || LootingBag.isBag(it.id) } + bag + cargo
            toGrave.forEach { item ->
                risk += value.getValue(item.id) * item.amount
                entries += Entry(Section.GRAVESTONE, item, line(item, "This item will be sent to your gravestone.", "These items will be sent to your gravestone."))
            }
            result.itemRisk.lost.map { it.item }.filter { LootKeys.isKey(it.id) || LootingBag.isBag(it.id) }.forEach { deleted(player, entries, it) }
        }
        return Preview(entries, 0, risk)
    }
    private fun deleted(
        player: Player,
        entries: MutableList<Entry>,
        item: Item,
    ) {
        entries +=
            Entry(
                Section.DELETED, item,
                if (item.amount > 1) {
                    "${spaced(item.amount.toLong())} x ${name(player, item.id)}: These items will be deleted."
                } else {
                    "${name(player, item.id)}: This item will be deleted."
                },
            )
    }

    /** The toggles start at the player's real situation (deathkeep_init arguments). */
    fun currentToggles(player: Player): Toggles =
        Toggles(
            protectItem = player.attr[PROTECT_ITEM_ATTR] == true,
            skulled = PvpSkull.isSkulled(player),
            killedByPlayer = false,
            deepWilderness = DeathRules.deepWilderness(player.tile),
        )

    fun open(player: Player) {
        player.attr[TOGGLES] = currentToggles(player)
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, EVENTS_OP1)
        for (i in 0 until 4) player.setInterfaceEvents(INTERFACE_ID, Layout.TOGGLE_FIRST + i * Layout.TOGGLE_STRIDE, -1..-1, EVENTS_OP1)
        for (i in 0 until Layout.SLOTS) player.setInterfaceEvents(INTERFACE_ID, Layout.SLOT_FIRST + i, -1..-1, EVENTS_OP1)
        refresh(player)
    }

    fun close(player: Player) {
        player.attr.remove(TOGGLES)
        player.attr.remove(SHOWN)
    }

    fun toggle(
        player: Player,
        index: Int,
    ) {
        val t = player.attr[TOGGLES] ?: return
        player.attr[TOGGLES] =
            when (index) {
                0 -> t.copy(protectItem = !t.protectItem)
                1 -> t.copy(skulled = !t.skulled)
                2 -> t.copy(killedByPlayer = !t.killedByPlayer)
                3 -> t.copy(deepWilderness = !t.deepWilderness)
                else -> t
            }
        refresh(player)
    }

    /** "Check" on a listed item: its OSRS line in the chatbox (deathkeep_opitem). */
    fun check(
        player: Player,
        index: Int,
    ) {
        player.attr[SHOWN]?.getOrNull(index)?.let { player.message(it.message) }
    }

    fun refresh(player: Player) {
        val toggles = player.attr[TOGGLES] ?: return
        val preview = preview(player, toggles)
        val shown = preview.entries.take(Layout.SLOTS)
        player.attr[SHOWN] = shown
        // deathkeep_left_setsection: header 3 px from the section top, items from 17 px, 7 per row 45 px apart, rows 41 px;
        // an 8 px gap, the 2-line divider, and another 8 px between sections.
        var y = 0
        var header = 0
        var divider = 0
        var slot = 0
        for (section in Section.values()) {
            val items = shown.filter { it.section == section }
            if (items.isEmpty()) continue
            if (y > 0 && divider < Layout.DIVIDERS) {
                y += 8
                player.setComponentPosition(INTERFACE_ID, Layout.DIVIDER_FIRST + divider * 2, 0, y)
                player.setComponentPosition(INTERFACE_ID, Layout.DIVIDER_FIRST + divider * 2 + 1, 0, y + 1)
                player.setComponentHidden(INTERFACE_ID, Layout.DIVIDER_FIRST + divider * 2, hidden = false)
                player.setComponentHidden(INTERFACE_ID, Layout.DIVIDER_FIRST + divider * 2 + 1, hidden = false)
                divider++
                y += 8
            }
            val text = section.header

            player.setComponentText(INTERFACE_ID, Layout.HEADER_FIRST + header, text)
            player.setComponentPosition(INTERFACE_ID, Layout.HEADER_FIRST + header, 1, y + 3)
            player.setComponentHidden(INTERFACE_ID, Layout.HEADER_FIRST + header, hidden = false)
            header++
            items.forEachIndexed { i, entry ->
                val component = Layout.SLOT_FIRST + slot
                player.setComponentItem(INTERFACE_ID, component, entry.item.id, entry.item.amount)
                player.setComponentPosition(INTERFACE_ID, component, Layout.MARGIN + (i % Layout.COLUMNS) * Layout.COLUMN_STEP, y + 17 + (i / Layout.COLUMNS) * Layout.ROW_STEP)
                player.setComponentHidden(INTERFACE_ID, component, hidden = false)
                slot++
            }
            val rows = (items.size + Layout.COLUMNS - 1) / Layout.COLUMNS
            y += 17 + (rows - 1) * Layout.ROW_STEP + 32
        }
        for (i in header until Layout.HEADERS) player.setComponentHidden(INTERFACE_ID, Layout.HEADER_FIRST + i, hidden = true)
        for (i in divider until Layout.DIVIDERS) {
            player.setComponentHidden(INTERFACE_ID, Layout.DIVIDER_FIRST + i * 2, hidden = true)
            player.setComponentHidden(INTERFACE_ID, Layout.DIVIDER_FIRST + i * 2 + 1, hidden = true)
        }
        for (i in slot until Layout.SLOTS) player.setComponentHidden(INTERFACE_ID, Layout.SLOT_FIRST + i, hidden = true)
        player.runClientScript(
            DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
            (INTERFACE_ID shl 16) or Layout.ITEMS,
            (INTERFACE_ID shl 16) or Layout.SCROLLBAR,
            if (y > 0) y + 3 else 0,
        )
        val states = booleanArrayOf(toggles.protectItem, toggles.skulled, toggles.killedByPlayer, toggles.deepWilderness)
        states.forEachIndexed { i, on ->
            val base = Layout.TOGGLE_FIRST + i * Layout.TOGGLE_STRIDE
            player.setComponentHidden(INTERFACE_ID, base + Layout.LOOK_ON, hidden = !on)
            player.setComponentHidden(INTERFACE_ID, base + Layout.LOOK_OFF, hidden = on)
        }
        player.setComponentText(INTERFACE_ID, Layout.VALUE, "Guide risk value:<br>${spaced(preview.riskValue)}")
    }
}
