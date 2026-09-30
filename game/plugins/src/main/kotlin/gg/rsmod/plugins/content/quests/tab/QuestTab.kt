package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.message.impl.IfCloseSubMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool as L
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.Block

/**
 * The quest tab (owner mission 2026-09-26, part 1): the container [L.CONTAINER] takes the quest tab slot, a row of four
 * tabs picks what opens in it - Deadman Groups, Quest List (the unchanged 667 quest list 190), Achievement Diaries and
 * Deadman. The server decides everything shown; the client only draws it.
 *
 * - [mount] runs right after the container is mounted (login, display-mode switch - [gg.rsmod.plugins.api.ext.PaneMountHooks])
 *   and puts the chosen tab back.
 * - The chosen tab is stored on the account ([SELECTED]); a new account starts on Deadman.
 * - While Deadman or Deadman Groups is showing, [refresh] runs every tick and sends only what changed, so a purchase, new
 *   points, an emblem picked up or dropped, a group choice or a finished quest shows at once, and the breach time every
 *   minute.
 */
object QuestTab {
    const val CONTAINER = L.CONTAINER
    const val QUEST_LIST_INTERFACE = 190

    enum class Tab(val panel: Int) {
        GROUPS(L.GROUPS),
        QUEST_LIST(QUEST_LIST_INTERFACE),
        DIARIES(L.DIARIES),
        DEADMAN(L.DEADMAN),
        ;

        val index get() = ordinal
        val tooltip get() = L.Tab.values()[ordinal].tooltip
    }

    val SELECTED = AttributeKey<String>(persistenceKey = "quest_tab_selected")
    val DEFAULT = Tab.DEADMAN

    /** What was last sent per component while the current panel is mounted (text / signature), so a tick sends only changes. */
    private val SENT = AttributeKey<HashMap<Int, String>>()

    val REFRESH_TIMER = TimerKey()

    private const val EVENTS_OP1 = 0x2

    private const val GREEN = "<col=0dc10d>"
    private const val RED = "<col=ff0000>"
    private const val GREY = "<col=9f9f9f>"

    fun selected(player: Player): Tab = player.attr[SELECTED]?.let { name -> Tab.values().firstOrNull { it.name == name } } ?: DEFAULT

    fun isMounted(player: Player): Boolean = player.interfaces.isVisible(CONTAINER)

    /** Stores [tab] as the player's quest tab choice and shows it. */
    fun select(
        player: Player,
        tab: Tab,
    ) {
        player.attr[SELECTED] = tab.name
        if (isMounted(player)) mount(player)
    }

    /** Draws the tab row for the chosen tab and opens its panel in the container. */
    fun mount(player: Player) {
        val tab = selected(player)
        Tab.values().forEach { t ->
            val base = L.tabBase(t.index)
            val on = t == tab
            player.setInterfaceEvents(CONTAINER, base, -1..-1, EVENTS_OP1)
            player.setComponentSprite(CONTAINER, base + L.TAB_LEFT, if (on) L.TAB_SIDE_SELECTED else L.TAB_SIDE_NORMAL)
            player.setComponentSprite(CONTAINER, base + L.TAB_MIDDLE, if (on) L.TAB_MIDDLE_SELECTED else L.TAB_MIDDLE_NORMAL)
            player.setComponentSprite(CONTAINER, base + L.TAB_RIGHT, if (on) L.TAB_SIDE_SELECTED else L.TAB_SIDE_NORMAL)
        }
        closeSlot(player, L.BOX)
        closeSlot(player, L.QUEST_MOUNT)
        val questList = tab == Tab.QUEST_LIST
        player.setComponentHidden(CONTAINER, L.BOX, hidden = questList)
        player.setComponentHidden(CONTAINER, L.QUEST_SCROLL, hidden = !questList)
        // 190 fits the space under the tab row (QUEST_LIST_FIT) and scrolls its own list: no outer scrollbar.
        player.setComponentHidden(CONTAINER, L.QUEST_SCROLLBAR, hidden = true)
        player.attr[SENT] = HashMap()
        if (questList) {
            player.openInterface(CONTAINER, L.QUEST_MOUNT, QUEST_LIST_INTERFACE, type = 1)
            // The quest list's own ops (quest_tab.plugin.kts), re-sent because the list is opened again here.
            player.setEvents(interfaceId = QUEST_LIST_INTERFACE, component = 18, to = 399, setting = 30)
        } else {
            player.openInterface(CONTAINER, L.BOX, tab.panel, type = 1)
            when (tab) {
                Tab.DEADMAN -> {
                    Block.values().forEachIndexed { i, block ->
                        player.setComponentText(L.DEADMAN, L.blockBase(i) + L.BLOCK_TITLE, block.title)
                        player.setInterfaceEvents(L.DEADMAN, L.blockBase(i) + L.BLOCK_INFO, -1..-1, EVENTS_OP1)
                    }
                    // Drop Viewer (owner 2026-09-30).
                    player.setInterfaceEvents(L.DEADMAN, L.DROPS_BUTTON, -1..-1, EVENTS_OP1)
                }
                else -> Unit
            }
        }
        refresh(player)
        if (tab == Tab.DEADMAN || tab == Tab.GROUPS) player.timers[REFRESH_TIMER] = 1 else player.timers.remove(REFRESH_TIMER)
    }

    private fun closeSlot(
        player: Player,
        component: Int,
    ) {
        val hash = (CONTAINER shl 16) or component
        if (player.interfaces.isOccupied(CONTAINER, component)) {
            player.interfaces.close(CONTAINER, component)
            player.write(IfCloseSubMessage(hash))
        }
    }

    /** Sends what changed on the panel that is showing. Returns false when the quest tab is not mounted. */
    fun refresh(player: Player): Boolean {
        if (!isMounted(player)) return false
        val sent = player.attr[SENT] ?: HashMap<Int, String>().also { player.attr[SENT] = it }
        when (selected(player)) {
            Tab.DEADMAN -> {
                if (!player.interfaces.isVisible(L.DEADMAN)) return true
                Block.values().forEachIndexed { i, block ->
                    val component = L.blockBase(i) + L.BLOCK_VALUE
                    val text = DeadmanTabValues.value(player, block)
                    if (sent[component] != text) {
                        sent[component] = text
                        player.setComponentText(L.DEADMAN, component, text)
                    }
                }
            }
            Tab.GROUPS -> {
                if (!player.interfaces.isVisible(L.GROUPS)) return true
                val view = DeadmanGroupsView.view(player)
                val signature = signature(view)
                if (sent[-1] != signature) {
                    sent[-1] = signature
                    drawGroups(player, view)
                }
            }
            else -> Unit
        }
        return true
    }

    /** Everything the Groups panel shows, as one string: redraw only when it changes. */
    fun signature(view: DeadmanGroupsView.View): String =
        buildString {
            append(view.showNote)
            view.groups.forEach { g ->
                append('|').append(g.label)
                g.options.forEach { o -> append(';').append(o.name).append('=').append(o.state).append(':').append(o.status) }
            }
        }

    /** Vertical layout of the Groups panel: the note, then per group its header and options. */
    class GroupsLayout(val noteY: Int, val headerY: List<Int>, val optionY: List<List<Int>>, val height: Int)

    fun layout(view: DeadmanGroupsView.View): GroupsLayout {
        var y = 2
        val noteY = y
        if (view.showNote) y += L.NOTE_HEIGHT + 4
        val headers = ArrayList<Int>()
        val options = ArrayList<List<Int>>()
        view.groups.forEach { g ->
            headers += y
            y += L.HEADER_HEIGHT
            val ys = ArrayList<Int>()
            g.options.forEach { _ ->
                ys += y
                y += L.OPTION_HEIGHT + 2
            }
            options += ys
            y += 6
        }
        return GroupsLayout(noteY, headers, options, y)
    }

    private fun drawGroups(
        player: Player,
        view: DeadmanGroupsView.View,
    ) {
        val id = L.GROUPS
        val layout = layout(view)
        player.setComponentHidden(id, L.G_NOTE, hidden = !view.showNote)
        if (view.showNote) {
            player.setComponentText(id, L.G_NOTE, DeadmanGroupsView.CHOOSE_NOTE)
            player.setComponentPosition(id, L.G_NOTE, 2, layout.noteY)
        }
        view.groups.forEachIndexed { g, group ->
            player.setComponentText(id, L.groupBase(g) + L.G_HEADER, group.label)
            player.setComponentPosition(id, L.groupBase(g) + L.G_HEADER, 2, layout.headerY[g])
            for (o in 0 until L.MAX_OPTIONS) {
                val base = L.optionBase(g, o)
                val option = group.options.getOrNull(o)
                val y = layout.optionY[g].getOrNull(o) ?: 0
                val chosen = option?.state == DeadmanGroupsView.State.CHOSEN
                L.boxOffsets(L.OPTION_WIDTH, L.OPTION_HEIGHT).forEachIndexed { piece, (dx, dy) ->
                    player.setComponentHidden(id, base + L.OPTION_BOX + piece, hidden = !chosen)
                    if (chosen) player.setComponentPosition(id, base + L.OPTION_BOX + piece, 2 + dx, y + dy)
                }
                player.setComponentHidden(id, base + L.OPTION_NAME, hidden = option == null)
                player.setComponentHidden(id, base + L.OPTION_STATUS, hidden = option == null)
                if (option == null) continue
                val name =
                    when (option.state) {
                        DeadmanGroupsView.State.LOCKED -> GREY + option.name
                        else -> option.name
                    }
                val status =
                    when (option.state) {
                        DeadmanGroupsView.State.LOCKED -> RED + option.status
                        else -> GREEN + option.status
                    }
                player.setComponentText(id, base + L.OPTION_NAME, name)
                player.setComponentText(id, base + L.OPTION_STATUS, status)
                player.setComponentPosition(id, base + L.OPTION_NAME, 2 + L.OPTION_TEXT_X, y + L.OPTION_NAME_Y)
                player.setComponentPosition(id, base + L.OPTION_STATUS, 2 + L.OPTION_TEXT_X, y + L.OPTION_STATUS_Y)
            }
        }
        player.runClientScript(
            DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
            (id shl 16) or L.G_SCROLL,
            (id shl 16) or L.G_SCROLLBAR,
            maxOf(layout.height, L.CONTENT_HEIGHT),
        )
    }

    // ------------------------------------------------------------------ Info window

    const val INFO = L.INFO

    /** Opens the Info window for [block]; ignored unless the Deadman panel is the one showing. */
    fun openInfo(
        player: Player,
        block: Block,
    ) {
        if (selected(player) != Tab.DEADMAN || !player.interfaces.isVisible(L.DEADMAN)) return
        player.openInterface(INFO, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INFO, L.I_CLOSE, -1..-1, EVENTS_OP1)
        player.setComponentText(INFO, L.I_TITLE, QuestTabInfo.title(block))
        player.setComponentText(INFO, L.I_TEXT, QuestTabInfo.text(block))
    }
}
