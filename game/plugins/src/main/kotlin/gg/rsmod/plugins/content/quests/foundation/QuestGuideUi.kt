package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool as L
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * "Choose your path" (interface [L.INTERFACE_ID], built by QuestGuideInterfaceImportTool): the permanent quest choices as
 * one window instead of chatbox menus (owner mission 2026-09-26). It only shows; every check stays in [QuestChoices]
 * (the one choice path, idempotent per group), so a double click or a click on a closed window can never claim twice.
 *
 * [open] is the one entry point (the Quest Guide now, the intro later).
 */
object QuestGuideUi {
    const val INTERFACE_ID = L.INTERFACE_ID

    private const val EVENTS_OP1 = 0x2

    /** The card picked in each row (quest name), before Confirm. */
    private val PICKS = AttributeKey<String>()

    /** The row whose confirmation is showing. */
    private val CONFIRMING = AttributeKey<Int>()

    private val OPEN = AttributeKey<Boolean>()

    /** Row order: MAGIC, PRAYER, GEAR. */
    val ROWS: List<ChoiceGroup> = listOf(ChoiceGroup.MAGIC, ChoiceGroup.PRAYER, ChoiceGroup.GEAR)

    /** Card icons: the item that stands for each unlock. */
    val ICON: Map<ShortQuest, Int> =
        mapOf(
            DesertTreasure to Items.ANCIENT_STAFF,
            LunarDiplomacy to Items.LUNAR_STAFF,
            DesertTreasureII to Items.ANCIENT_SCEPTRE,
            TempleAtSenntisten to Items.ANCIENT_HYMNAL,
            KingsRansom to Items.HOLY_SYMBOL,
            DragonSlayerII to Items.AVAS_ASSEMBLER,
            SongOfTheElves to Items.BOW_OF_FAERDHINEN,
            MonkeyMadnessII to Items.ZENYTE,
        )

    /** One or two short lines per card: what the quest unlocks. */
    val UNLOCKS: Map<ShortQuest, String> =
        mapOf(
            DesertTreasure to "Ancient Magicks<br>spellbook",
            LunarDiplomacy to "Lunar spellbook<br>and Vengeance",
            DesertTreasureII to "Ancient sceptres<br>and Magus ring",
            TempleAtSenntisten to "Ancient Curses<br>prayer book",
            KingsRansom to "Piety and<br>Chivalry",
            DragonSlayerII to "Ava's assembler<br>and Myths' Guild",
            SongOfTheElves to "Bow of Faerdhinen<br>and crystal armour",
            MonkeyMadnessII to "Zenyte jewellery<br>and heavy ballista",
        )

    val NAMES: Map<ShortQuest, String> =
        mapOf(
            DesertTreasure to "Desert Treasure",
            LunarDiplomacy to "Lunar Diplomacy",
            DesertTreasureII to "Desert Treasure II",
            TempleAtSenntisten to "Temple at Senntisten",
            KingsRansom to "King's Ransom",
            DragonSlayerII to "Dragon Slayer II",
            SongOfTheElves to "Song of the Elves",
            MonkeyMadnessII to "Monkey Madness II",
        )

    private const val GREY = "<col=8a8a8a>"
    private const val END = "</col>"

    /** Opens the screen (the Quest Guide, later the intro). */
    fun open(player: Player) {
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.attr[OPEN] = true
        player.attr.remove(PICKS)
        player.attr.remove(CONFIRMING)
        val clickable = mutableListOf(DeathsOfficeInterfaceImportTool.CLOSE, L.YES, L.NO)
        for (r in 0 until L.ROWS) {
            clickable += L.rowBase(r) + L.ROW_CONFIRM
            for (c in 0 until L.CARDS) clickable += L.cardBase(r, c)
        }
        clickable.forEach { player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, EVENTS_OP1) }
        draw(player)
    }

    fun isOpen(player: Player): Boolean = player.attr[OPEN] == true

    fun onClosed(player: Player) {
        player.attr.remove(OPEN)
        player.attr.remove(PICKS)
        player.attr.remove(CONFIRMING)
    }

    private fun picks(player: Player): MutableMap<Int, String> =
        player.attr[PICKS]?.split(';')?.filter { it.contains('=') }?.associate { it.substringBefore('=').toInt() to it.substringAfter('=') }?.toMutableMap()
            ?: mutableMapOf()

    private fun setPicks(player: Player, picks: Map<Int, String>) {
        player.attr[PICKS] = picks.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    private fun locked(player: Player, group: ChoiceGroup): Boolean = QuestChoices.isLegacy(player) || QuestChoices.chosen(player, group) != null

    fun draw(player: Player) {
        val picks = picks(player)
        ROWS.forEachIndexed { r, group ->
            val base = L.rowBase(r)
            val chosen = QuestChoices.chosen(player, group)
            val rowLocked = locked(player, group)
            player.setComponentText(INTERFACE_ID, base + L.ROW_LABEL, group.label.uppercase())
            val pick = picks[r]?.let { name -> group.quests.firstOrNull { it.name == name } }
            val canConfirm = !rowLocked && pick != null && QuestChoices.available(player, group).contains(pick)
            player.setComponentHidden(INTERFACE_ID, base + L.CONFIRM_ACTIVE, hidden = !canConfirm)
            player.setComponentHidden(INTERFACE_ID, base + L.CONFIRM_DISABLED, hidden = canConfirm)
            for (c in 0 until L.CARDS) {
                val card = L.cardBase(r, c)
                val quest = group.quests.getOrNull(c)
                if (quest == null) {
                    player.setComponentHidden(INTERFACE_ID, card, hidden = true)
                    continue
                }
                player.setComponentHidden(INTERFACE_ID, card, hidden = false)
                player.setComponentPosition(INTERFACE_ID, card, L.cardX(c, group.quests.size), L.rowY(r))
                val finished = quest.isFinished(player)
                val isChosen = quest == chosen
                val lockedOut = rowLocked && !isChosen && !finished
                val selected = !rowLocked && quest == pick
                player.setComponentHidden(INTERFACE_ID, card + L.CARD_NORMAL, hidden = lockedOut)
                player.setComponentHidden(INTERFACE_ID, card + L.CARD_DOWN, hidden = !lockedOut)
                val frame = isChosen || selected
                player.setComponentHidden(INTERFACE_ID, card + L.CARD_FRAME_OUTER, hidden = !frame)
                player.setComponentHidden(INTERFACE_ID, card + L.CARD_FRAME_INNER, hidden = !frame)
                player.setComponentHidden(INTERFACE_ID, card + L.CARD_TICK, hidden = !(isChosen || finished))
                player.setComponentHidden(INTERFACE_ID, card + L.CARD_LOCK, hidden = !lockedOut)
                player.setComponentItem(INTERFACE_ID, card + L.CARD_ICON, ICON[quest] ?: quest.reward.icon, 1)
                val name = NAMES[quest] ?: quest.name
                player.setComponentText(INTERFACE_ID, card + L.CARD_NAME, if (lockedOut) "$GREY$name$END" else name)
                val desc =
                    when {
                        lockedOut -> "${GREY}Complete the quest<br>to unlock$END"
                        else -> UNLOCKS[quest] ?: ""
                    }
                player.setComponentText(INTERFACE_ID, card + L.CARD_DESC, desc)
            }
        }
        val confirming = player.attr[CONFIRMING]
        player.setComponentHidden(INTERFACE_ID, L.DIM, hidden = confirming == null)
        player.setComponentHidden(INTERFACE_ID, L.DIALOG, hidden = confirming == null)
    }

    fun clickCard(player: Player, row: Int, card: Int) {
        if (!isOpen(player) || player.attr[CONFIRMING] != null) return
        val group = ROWS.getOrNull(row) ?: return
        val quest = group.quests.getOrNull(card) ?: return
        if (locked(player, group) || quest.isFinished(player)) return
        val picks = picks(player)
        picks[row] = quest.name
        setPicks(player, picks)
        draw(player)
    }

    fun clickConfirm(player: Player, row: Int) {
        if (!isOpen(player) || player.attr[CONFIRMING] != null) return
        val group = ROWS.getOrNull(row) ?: return
        val pick = picks(player)[row]?.let { name -> group.quests.firstOrNull { it.name == name } } ?: return
        if (locked(player, group) || !QuestChoices.available(player, group).contains(pick)) return
        player.attr[CONFIRMING] = row
        draw(player)
    }

    fun clickYes(player: Player) {
        if (!isOpen(player)) return
        val row = player.attr[CONFIRMING] ?: return
        player.attr.remove(CONFIRMING)
        val group = ROWS.getOrNull(row) ?: return draw(player)
        val picks = picks(player)
        val pick = picks[row]?.let { name -> group.quests.firstOrNull { it.name == name } } ?: return draw(player)
        when (QuestChoices.choose(player, group, pick)) {
            QuestChoices.Result.CHOSEN -> {
                picks.remove(row)
                setPicks(player, picks)
                // FoundationRewards.complete shows the quest-complete scroll (and its jingle); this window closes for it.
                onClosed(player)
                return
            }
            QuestChoices.Result.ALREADY_CHOSEN -> player.message("Your ${group.label.lowercase()} choice is already recorded.")
            QuestChoices.Result.ALREADY_COMPLETE -> player.message("You have completed ${pick.name} already.")
            QuestChoices.Result.LEGACY_ACCOUNT -> player.message("Your quests were all recorded long before these choices existed.")
            QuestChoices.Result.NOT_IN_GROUP -> Unit
        }
        draw(player)
    }

    fun clickNo(player: Player) {
        if (!isOpen(player)) return
        player.attr.remove(CONFIRMING)
        draw(player)
    }

    fun close(player: Player) {
        player.closeInterface(INTERFACE_ID)
        onClosed(player)
    }
}
