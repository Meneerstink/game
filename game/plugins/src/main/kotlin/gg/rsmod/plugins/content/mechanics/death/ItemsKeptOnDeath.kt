package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
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
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Kept as Layout
import gg.rsmod.game.model.entity.zoneTile

/**
 * The OSRS "Items Kept on Death" screen (OSRS interface 4, built as 667 interface [INTERFACE_ID] by
 * `DeathsOfficeInterfaceImportTool`; owner 2026-09-26: "OSRS-scherm bouwen"). The server fills it from [preview], which runs
 * the very same rules as a real death ([DeathResolver] with [DeathRules.keepCount], [UntradeableDeathProtection],
 * [PvpDeathBreakables], [QuiverDeathRules] and loot keys), so the screen cannot disagree with a death.
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
        // Owner 2026-10-24 (N353): "lost", not "deleted" (the OSRS screen says DELETED).
        DELETED("Items that are <col=ff0000>LOST</col>:"),
    }

    data class Entry(val section: Section, val item: Item, val message: String)

    data class Preview(val entries: List<Entry>, val graveFee: Int, val riskValue: Long)

    /**
     * The "what if" toggles (N503, owner 2026-11-01 D1/D2/D18): skulled, killed by a player (unticked = the "PvM death" box ticked),
     * deep Wilderness. Protect Item has none: it does not work in Deadman Mode (D1), so its toggle is hidden.
     */
    data class Toggles(val skulled: Boolean, val killedByPlayer: Boolean, val deepWilderness: Boolean)

    /**
     * Owner 2026-10-04: the notice in the free space of the right panel ("a message telling players that above level 20 Wilderness
     * some items need a Trouver parchment ... and that a PvM death is safe", as professional as possible). 138 px wide, 8 lines.
     */
    const val NOTICE_TEXT =
        "<col=ffffff>Killed by a monster:</col> unskulled you keep your 3 most valuable items; the rest goes to your gravestone.<br>" +
            "<col=ffffff>Killed by a player:</col> you keep nothing. Protect Item does not work in Deadman Mode."

    /** The "Protect Item prayer enabled" toggle (deathkeep_right_initbutton index 0), hidden - see [open]. */
    private const val PROTECT_TOGGLE = 0

    /** N503 D18: toggle 2 reads "PvM death" (OSRS), ticked = a monster kill; the screen opens unticked = killed by a player. */
    private const val PVM_TOGGLE = 2
    const val PVM_TOGGLE_TEXT = "PvM death"

    /** Inside a toggle look layer: its text (DeathsOfficeInterfaceImportTool: look + 11). */
    private const val TOGGLE_TEXT = 11

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
                skulled = toggles.skulled,
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
            // Owner 2026-10-04: on a death against a monster a lockable untradeable always stays (and stays locked).
            val always = !pvp && UntradeableDeathProtection.lockable(it.item.id)
            entries += Entry(Section.KEPT, it.item, if (always) line(it.item, "You always keep this item when a monster kills you.", "You always keep these items when a monster kills you.") else line(it.item, "Unskulled, you keep your 3 most valuable items when a monster kills you.", "Unskulled, you keep your 3 most valuable items when a monster kills you."))
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
                UntradeableFate.LOST_FOR_COINS ->
                    entries += Entry(Section.LOST, item, line(item, "This untradeable item is lost; the player who kills you receives $coins coins. Perdu can sell it back.", "These untradeable items are lost; the player who kills you receives $coins coins. Perdu can sell them back."))
                UntradeableFate.DESTROYED ->
                    entries += Entry(Section.DELETED, item, line(item, "This item will be lost; the player who kills you receives $coins coins.", "These items will be lost; the player who kills you receives $coins coins."))
            }
        }
        // Beast-of-burden cargo follows the death like everything else: to the killer (PvP) or into the gravestone (PvM). N503 D17: an
        // upgraded pet keeps its items on a monster death (they stay in its own pack); D13: the pet that is out goes to the gravestone.
        val bob = gg.rsmod.plugins.content.skills.summoning.BeastOfBurden
        val upgradedPet = bob.isUpgradedPetOut(player)
        val cargo = bob.activeContainer(player)?.rawItems?.filterNotNull()?.map { Item(it) } ?: emptyList()
        val pets = gg.rsmod.plugins.content.skills.summoning.Pets
        pets.itemOf(player)?.let { item -> pets.variantOfItem(item)?.let { pets.shownItem(player, it) } ?: item }?.let { follower ->
            val keeps = if (upgradedPet && cargo.isNotEmpty() && !pvp) " It keeps the items it carries." else ""
            entries += Entry(Section.GRAVESTONE, Item(follower), "${name(player, follower)}: Your pet goes to your gravestone.$keeps")
        }
        var risk = 0L
        if (pvp) {
            val lost =
                result.itemRisk.lost.map { it.item } + lostAmmo + cargo
            lost.forEach { item ->
                risk += value.getValue(item.id) * item.amount
                entries += Entry(Section.LOST, item, line(item, "This item will be lost to the player who kills you.", "These items will be lost to the player who kills you."))
            }
            // Converting items show what the killer really receives (PvpDeathBreakables.killerItems, the same call the death makes):
            // Barrows/Moon armour as its broken version, an ornamented item as the base item plus its kit, and so on.
            converting.forEach { slot ->
                val from = name(player, slot.item.id)
                val loot = PvpDeathBreakables.killerItems(slot.item, value, player)
                // Owner Q14=A (2026-10-06): an imbue refund and the victim's gravestone share come from the same table as the death.
                val refund =
                    PvpDeathBreakables.imbueRefund(slot.item.id)?.let {
                        val total = it.points.toLong() * slot.item.amount
                        " Your imbue is refunded: ${spaced(total)} ${if (total == 1L) it.currency.singular else it.currency.plural}."
                    } ?: ""
                // K01 deel 5: a carried ornament kit is never lost - it goes to the victim's gravestone, nothing for the killer.
                if (gg.rsmod.plugins.content.items.osrs.OrnamentKitRules.isKit(slot.item.id)) {
                    entries += Entry(Section.GRAVESTONE, slot.item, line(slot.item, "You never lose an ornament kit: it goes to your gravestone.", "You never lose an ornament kit: they go to your gravestone."))
                    return@forEach
                }
                // N503 D13/D14: a pet or a burden trinket is never the killer's - it goes to your gravestone.
                if (PvpDeathBreakables.petOrTrinket(slot.item.id)) {
                    entries += Entry(Section.GRAVESTONE, slot.item, line(slot.item, "This item goes to your gravestone.", "These items go to your gravestone."))
                    return@forEach
                }
                when {
                    loot == null -> entries += Entry(Section.KEPT, slot.item, line(slot.item, "You'll keep this item.", "You'll keep these items."))
                    loot.isEmpty() -> deleted(player, entries, slot.item)
                    else -> loot.forEach { item ->
                        risk += value.getValue(item.id) * item.amount
                        entries += Entry(Section.LOST, item, line(item, "The player who kills you receives this from your $from.$refund", "The player who kills you receives these from your $from.$refund"))
                    }
                }
                if (loot != null) {
                    PvpDeathBreakables.victimGraveItems(slot.item).forEach { item ->
                        entries += Entry(Section.GRAVESTONE, item, line(item, "This part of your $from will be sent to your gravestone.", "These parts of your $from will be sent to your gravestone."))
                    }
                }
            }
            UntradeableDeathProtection.killerLoot(untradeables).forEach { risk += value.getValue(it.id) * it.amount }
        } else {
            // Owner 2026-09-26: everything lost on a PvM death goes to the gravestone, and taking it back is free.
            val toGrave = result.itemRisk.lost.map { it.item }.filterNot { LootKeys.isKey(it.id) || DeathRules.isEmblem(it.id) } + (if (upgradedPet) emptyList() else cargo)
            toGrave.forEach { item ->
                risk += value.getValue(item.id) * item.amount
                entries += Entry(Section.GRAVESTONE, item, line(item, "This item will be sent to your gravestone.", "These items will be sent to your gravestone."))
            }
            result.itemRisk.lost.map { it.item }.filter { LootKeys.isKey(it.id) || DeathRules.isEmblem(it.id) }.forEach { deleted(player, entries, it) }
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
                    "${spaced(item.amount.toLong())} x ${name(player, item.id)}: These items will be lost."
                } else {
                    "${name(player, item.id)}: This item will be lost."
                },
            )
    }

    /**
     * The toggles start at the player's real skull and tile (deathkeep_init arguments). N503 D18 (owner 2026-10-31 23:4x / 11-01): this
     * is a Deadman server, so the screen always opens on "Killed by a player" - the "PvM death" box unticked - wherever the player
     * stands (it used to open the PvM view outside dangerous zones).
     */
    fun currentToggles(player: Player): Toggles =
        Toggles(
            skulled = PvpSkull.isSkulled(player),
            killedByPlayer = true,
            deepWilderness = DeathRules.deepWilderness(player.zoneTile()),
        )

    fun open(player: Player) {
        player.attr[TOGGLES] = currentToggles(player)
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, CLOSE, -1..-1, EVENTS_OP1)
        for (i in 0 until 4) if (i != PROTECT_TOGGLE) player.setInterfaceEvents(INTERFACE_ID, Layout.TOGGLE_FIRST + i * Layout.TOGGLE_STRIDE, -1..-1, EVENTS_OP1)
        // N503 D1: Protect Item does not work in Deadman Mode - its toggle is hidden and the three below it move up. The skull
        // counts again (D2: unskulled keeps 3 on a monster death), so "PK Skull active" is back (hidden since 2026-10-04).
        player.setComponentHidden(INTERFACE_ID, Layout.TOGGLE_FIRST + PROTECT_TOGGLE * Layout.TOGGLE_STRIDE, hidden = true)
        player.setComponentHidden(INTERFACE_ID, Layout.TOGGLE_FIRST + 1 * Layout.TOGGLE_STRIDE, hidden = false)
        // D18: "Killed by a player" becomes the OSRS "PvM death" box (the PvP view is the default).
        val pvm = Layout.TOGGLE_FIRST + PVM_TOGGLE * Layout.TOGGLE_STRIDE
        player.setComponentText(INTERFACE_ID, pvm + Layout.LOOK_ON + TOGGLE_TEXT, PVM_TOGGLE_TEXT)
        player.setComponentText(INTERFACE_ID, pvm + Layout.LOOK_OFF + TOGGLE_TEXT, PVM_TOGGLE_TEXT)
        player.setComponentText(INTERFACE_ID, Layout.NOTICE, NOTICE_TEXT)
        var y = 30
        for (i in PROTECT_TOGGLE + 1 until 4) {
            player.setComponentPosition(INTERFACE_ID, Layout.TOGGLE_FIRST + i * Layout.TOGGLE_STRIDE, 10, y)
            y += Layout.TOGGLE_HEIGHTS[i] + 5
        }
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
        player.attr[TOGGLES] = toggled(t, index)
        refresh(player)
    }

    /**
     * N241 (owner 2026-10-23: "Wilderness beyond level 20 lijkt niets te doen"): the level-20 rule exists only for a player kill -
     * ticking "Wilderness beyond level 20" unticks "PvM death", and ticking "PvM death" unticks it, so the screen never shows a
     * combination whose answer cannot differ. Index 1 is the skull (N503 D2), index 2 the "PvM death" box (D18).
     */
    fun toggled(
        t: Toggles,
        index: Int,
    ): Toggles =
        when (index) {
            1 -> t.copy(skulled = !t.skulled)
            PVM_TOGGLE -> if (t.killedByPlayer) t.copy(killedByPlayer = false, deepWilderness = false) else t.copy(killedByPlayer = true)
            3 -> if (t.deepWilderness) t.copy(deepWilderness = false) else t.copy(deepWilderness = true, killedByPlayer = true)
            else -> t
        }

    /**
     * The entries in the order [refresh] puts them in the item slots: section by section (KEPT, GRAVESTONE, LOST, DELETED). N241:
     * "Check" read the unsorted list, so a click on one item printed another item's line.
     */
    fun slotOrder(entries: List<Entry>): List<Entry> = Section.values().flatMap { section -> entries.filter { it.section == section } }

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
        val shown = slotOrder(preview.entries).take(Layout.SLOTS)
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
        val states = booleanArrayOf(false, toggles.skulled, !toggles.killedByPlayer, toggles.deepWilderness)
        states.forEachIndexed { i, on ->
            val base = Layout.TOGGLE_FIRST + i * Layout.TOGGLE_STRIDE
            player.setComponentHidden(INTERFACE_ID, base + Layout.LOOK_ON, hidden = !on)
            player.setComponentHidden(INTERFACE_ID, base + Layout.LOOK_OFF, hidden = on)
        }
        player.setComponentText(INTERFACE_ID, Layout.VALUE, "Guide risk value:<br>${spaced(preview.riskValue)}")
    }
}
