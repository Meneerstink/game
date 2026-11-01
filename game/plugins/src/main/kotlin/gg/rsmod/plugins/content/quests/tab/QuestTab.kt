package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.message.impl.IfCloseSubMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.tools.importer.N108InterfaceImportTool
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.DP
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.I
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.LP
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.QP
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.TP
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestIds
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.quests.foundation.ChoiceGroup
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.foundation.QuestGuideUi
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.tab.QuestTabInfo.Subject
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The quest tab with four tabs (owner design 2026-10-24 GOEDGEKEURD 05, spec section C): 1 Quests (N103: only the Magic / Prayer /
 * Gear groups with their quests and requirements - every other quest is out of the list), 2 Deadman (N109: bracket, protection,
 * skull, kill grace, the three point balances, emblem, breach, chest), 3 Deadman Tasks (N104), 4 Drops & Collection Log. Every value
 * is read live from the game; while a panel shows, [refresh] runs every tick and sends only what changed (timers count down).
 */
object QuestTab {
    const val CONTAINER = N108InterfaceImportTool.CONTAINER
    const val QUEST_LIST_INTERFACE = 190

    enum class Tab(val panel: Int) {
        QUESTS(N108InterfaceImportTool.QUESTS),
        DEADMAN(N108InterfaceImportTool.DEADMAN),
        TASKS(N108InterfaceImportTool.TASKS),
        DROPS(N108InterfaceImportTool.DROPS_LOG),
        ;

        val index get() = ordinal
    }

    val SELECTED = AttributeKey<String>(persistenceKey = "quest_tab_selected")
    val DEFAULT = Tab.DEADMAN

    /** The classic quest list (190) is showing in the Quests tab instead of the groups. */
    private val CLASSIC = AttributeKey<Boolean>()
    private val OPEN_QUESTS = AttributeKey<MutableSet<String>>()
    private val QUEST_ROWS = AttributeKey<List<QuestRow>>()
    private val QUEST_PAGE = AttributeKey<Int>()
    private val QUEST_EVENTS = AttributeKey<List<Pair<Boolean, Boolean>>>()

    val REFRESH_TIMER = TimerKey()

    private const val EVENTS_OP1 = 0x2

    /** Old names (GROUPS, QUEST_LIST, DIARIES before 2026-10-24) open the Quests tab. */
    fun selected(player: Player): Tab =
        when (val name = player.attr[SELECTED]) {
            null -> DEFAULT
            "GROUPS", "QUEST_LIST", "DIARIES" -> Tab.QUESTS
            else -> Tab.values().firstOrNull { it.name == name } ?: DEFAULT
        }

    fun isMounted(player: Player): Boolean = player.interfaces.isVisible(CONTAINER)

    fun select(player: Player, tab: Tab) {
        // A second click on Quests while the classic list shows goes back to the groups.
        if (tab == Tab.QUESTS && selected(player) == Tab.QUESTS) player.attr.remove(CLASSIC)
        player.attr[SELECTED] = tab.name
        if (isMounted(player)) mount(player)
    }

    fun mount(player: Player) {
        player.attr.remove(QUEST_EVENTS)
        val tab = selected(player)
        N108Ui.reset(player, CONTAINER)
        Tab.values().forEach { t ->
            val base = Q.tab(t.index)
            player.setInterfaceEvents(CONTAINER, base, -1..-1, EVENTS_OP1)
            N108Ui.hidden(player, CONTAINER, base + 1, t == tab)
            N108Ui.hidden(player, CONTAINER, base + 2, t != tab)
        }
        closeSlot(player, Q.BOX)
        Tab.values().forEach { N108Ui.reset(player, it.panel) }
        if (tab == Tab.QUESTS && player.attr[CLASSIC] == true) {
            player.openInterface(CONTAINER, Q.BOX, QUEST_LIST_INTERFACE, type = 1)
            player.setEvents(interfaceId = QUEST_LIST_INTERFACE, component = 18, to = 399, setting = 30)
        } else {
            player.openInterface(CONTAINER, Q.BOX, tab.panel, type = 1)
            when (tab) {
                Tab.QUESTS -> player.setInterfaceEvents(tab.panel, 0, -1..-1, 0)
                Tab.DEADMAN -> {
                    player.setInterfaceEvents(tab.panel, DP.INFO, -1..-1, EVENTS_OP1)
                    player.setInterfaceEvents(tab.panel, DP.EMBLEM_HIT, -1..-1, EVENTS_OP1)
                    for (r in 0 until DP.POINTS) player.setInterfaceEvents(tab.panel, DP.point(r) + 4, -1..-1, EVENTS_OP1)
                    for (e in 0 until 2) player.setInterfaceEvents(tab.panel, DP.event(e) + 5, -1..-1, EVENTS_OP1)
                }
                Tab.TASKS -> for (t in 0 until 4) player.setInterfaceEvents(tab.panel, TP.tier(t) + 7, -1..-1, EVENTS_OP1)
                Tab.DROPS -> {
                    player.setInterfaceEvents(tab.panel, LP.VIEWER_HIT, -1..-1, EVENTS_OP1)
                    player.setInterfaceEvents(tab.panel, LP.LOG_HIT, -1..-1, EVENTS_OP1)
                    for (s in 0 until 5) player.setInterfaceEvents(tab.panel, LP.slot(s) + 1, -1..-1, EVENTS_OP1)
                }
            }
        }
        refresh(player)
        player.timers[REFRESH_TIMER] = 1
    }

    private fun closeSlot(player: Player, component: Int) {
        val hash = (CONTAINER shl 16) or component
        if (player.interfaces.isOccupied(CONTAINER, component)) {
            player.interfaces.close(CONTAINER, component)
            player.write(IfCloseSubMessage(hash))
        }
    }

    /** Sends what changed on the panel that is showing. False when the quest tab is not mounted. */
    fun refresh(player: Player): Boolean {
        if (!isMounted(player)) return false
        val tab = selected(player)
        if (player.interfaces.isVisible(INFO)) drawInfo(player)
        if (!player.interfaces.isVisible(tab.panel)) return true
        when (tab) {
            Tab.QUESTS -> drawQuests(player)
            Tab.DEADMAN -> drawDeadman(player)
            Tab.TASKS -> drawTasks(player)
            Tab.DROPS -> drawDrops(player)
        }
        return true
    }

    fun refreshDropsPanel(player: Player) {
        if (isMounted(player) && selected(player) == Tab.DROPS && player.interfaces.isVisible(Tab.DROPS.panel)) drawDrops(player)
    }

    // ================================================================== 1 Quests

    /**
     * The quests each choice quest asks for in OSRS / RS (wiki "Requirements"), shown under it when it is folded open - information
     * only (owner design): they turn green once the quest itself is done.
     */
    val REQUIREMENTS: Map<String, List<String>> =
        mapOf(
            "Desert Treasure" to listOf("The Dig Site", "Temple of Ikov", "The Tourist Trap", "Troll Stronghold", "Priest in Peril", "Waterfall Quest"),
            "Lunar Diplomacy" to listOf("The Fremennik Trials", "Lost City", "Rune Mysteries", "Shilo Village"),
            "Desert Treasure II" to listOf("Desert Treasure", "Secrets of the North", "Enakhra's Lament", "Temple of the Eye", "The Garden of Death", "Below Ice Mountain"),
            "Temple at Senntisten" to listOf("The Curse of Arrav", "Devious Minds", "Desert Treasure"),
            "King's Ransom" to listOf("Black Knights' Fortress", "Holy Grail", "Murder Mystery", "One Small Favour"),
            "Dragon Slayer II" to listOf("Legends' Quest", "Dream Mentor", "A Tail of Two Cats", "Animal Magnetism", "Ghosts Ahoy", "Bone Voyage", "Client of Kourend"),
            "Song of the Elves" to listOf("Mourning's End Part II", "Making History"),
            "Monkey Madness II" to listOf("Monkey Madness I", "Enlightened Journey", "The Eyes of Glouphrie", "Recipe for Disaster", "Troll Stronghold", "Watchtower"),
        )

    class QuestRow(val kind: Int, val key: String, val icon: String?, val text: String, val right: String?, val arrow: String?, val star: Boolean = false)

    private const val KIND_GROUP = 0
    private const val KIND_QUEST = 1
    private const val KIND_SUB = 2
    private const val KIND_LINK = 3
    private const val PREVIOUS_PAGE = "previous-page"
    private const val NEXT_PAGE = "next-page"

    fun questRows(player: Player): List<QuestRow> {
        val open = player.attr[OPEN_QUESTS] ?: HashSet<String>().also { player.attr[OPEN_QUESTS] = it }
        val legacy = QuestChoices.isLegacy(player)
        val out = ArrayList<QuestRow>()
        for (group in QuestGuideUi.ROWS) {
            val chosen = QuestChoices.chosen(player, group)
            // N289 (owner 2026-10-24, option A): a group whose quests are all yours reads a green "Unlocked" (it said "All yours").
            val all = group.quests.all { legacy || it == chosen || DeadmanGroupsView.questFinished(player, it) }
            val right = when {
                all -> "<col=0dc10d>Unlocked</col>"
                chosen != null -> "<col=ffcf4a>Chosen</col>"
                else -> "<col=9a9083>Pick 1</col>"
            }
            out += QuestRow(KIND_GROUP, group.name, null, "<col=ffcf4a>${group.label}</col>", right, null)
            for (quest in group.quests) {
                val name = QuestGuideUi.NAMES[quest] ?: quest.name
                val done = legacy || quest == chosen || DeadmanGroupsView.questFinished(player, quest)
                val locked = !legacy && chosen != null && quest != chosen && !done
                val (icon, text) =
                    when {
                        quest == chosen -> "q_ck" to name
                        done -> "q_ck" to name
                        locked -> "q_lock" to "<col=8a8070>$name</col>"
                        else -> "q_dot" to name
                    }
                val reqs = REQUIREMENTS[name].orEmpty()
                val isOpen = name in open
                out += QuestRow(KIND_QUEST, name, icon, text, null, if (reqs.isEmpty()) null else if (isOpen) "q_two" else "q_tw", star = quest == chosen)
                if (isOpen) reqs.forEach { r -> out += QuestRow(KIND_SUB, r, if (done) "q_ck7" else "q_dot5", "<col=b3a78f>$r</col>", null, null) }
            }
        }
        return out
    }

    /** Existing cache has 30 static rows. Page overflow instead of silently losing the last groups/quests. */
    internal fun visibleQuestRows(player: Player): List<QuestRow> {
        val all = questRows(player)
        val pageSize = QP.ROWS - 2
        val pages = if (all.size <= QP.ROWS) 1 else (all.size + pageSize - 1) / pageSize
        val page = (player.attr[QUEST_PAGE] ?: 0).coerceIn(0, pages - 1)
        player.attr[QUEST_PAGE] = page
        if (pages == 1) return all
        return buildList {
            if (page > 0) add(QuestRow(KIND_LINK, PREVIOUS_PAGE, null, "Previous page", null, null))
            addAll(all.drop(page * pageSize).take(pageSize))
            if (page < pages - 1) add(QuestRow(KIND_LINK, NEXT_PAGE, null, "Next page", null, null))
        }
    }

    private fun drawQuests(player: Player) {
        val p = QuestTab.Tab.QUESTS.panel
        val rows = visibleQuestRows(player)
        val previousRows = player.attr[QUEST_ROWS]
        player.attr[QUEST_ROWS] = rows
        val chosen = QuestGuideUi.ROWS.count { QuestChoices.chosen(player, it) != null }
        N108Ui.text(player, p, N108InterfaceImportTool.P_RIGHT, if (QuestChoices.isLegacy(player)) "" else "<col=9a9083>Groups</col> $chosen/${QuestGuideUi.ROWS.size}")
        val previousEvents = player.attr[QUEST_EVENTS]
        val events = (0 until QP.ROWS).map { r -> rows.getOrNull(r).let { (it != null && it.kind != KIND_GROUP) to (it?.arrow != null) } }
        player.attr[QUEST_EVENTS] = events
        var y = 0
        val fonts = ArrayList<Pair<Int, String>>()
        for (r in 0 until QP.ROWS) {
            val base = QP.row(r)
            val row = rows.getOrNull(r)
            val clickable = row != null && row.kind != KIND_GROUP
            if (previousEvents?.getOrNull(r) != events[r]) {
                player.setInterfaceEvents(p, base + 4, -1..-1, if (clickable) EVENTS_OP1 else 0)
                player.setInterfaceEvents(p, base + 3, -1..-1, if (row?.arrow != null) EVENTS_OP1 else 0)
            }
            if (row == null) {
                for (c in 0 until QP.ROW_STRIDE) N108Ui.hidden(player, p, base + c, true)
                continue
            }
            val h = if (row.kind == KIND_GROUP) 15 else 14
            val textX = when (row.kind) {
                KIND_GROUP -> 0
                KIND_SUB -> 28
                KIND_LINK -> 2
                else -> 16
            }
            if (row.icon != null) {
                N108Ui.sprite(player, p, base, row.icon)
                val iconX = if (row.kind == KIND_SUB) 17 else 2
                val iconY = y + (h - N108Ui.width(row.icon).coerceAtMost(11)) / 2 - if (row.icon == "q_lock") 2 else 0
                N108Ui.pos(player, p, base, iconX, iconY)
            }
            N108Ui.hidden(player, p, base, row.icon == null)
            N108Ui.pos(player, p, base + 1, textX, y)
            fonts += (base + 1) to if (row.kind == KIND_GROUP) "b12_full" else "p11_full"
            N108Ui.text(player, p, base + 1, row.text)
            N108Ui.hidden(player, p, base + 1, false)
            N108Ui.hidden(player, p, base + 2, row.right == null)
            if (row.right != null) {
                N108Ui.pos(player, p, base + 2, 0, y)
                N108Ui.text(player, p, base + 2, row.right)
            }
            N108Ui.hidden(player, p, base + 3, row.arrow == null)
            if (row.arrow != null) {
                N108Ui.sprite(player, p, base + 3, row.arrow)
                if (previousEvents == null || previousRows?.getOrNull(r)?.arrow == null) N108Ui.op(player, p, base + 3, "Toggle requirements")
                N108Ui.pos(player, p, base + 3, if (row.arrow == "q_two") 151 else 152, y + if (row.arrow == "q_two") 5 else 4)
            }
            N108Ui.pos(player, p, base + 4, 0, y)
            N108Ui.size(player, p, base + 4, if (row.arrow != null) 145 else 160, h)
            if (previousEvents == null || previousRows?.getOrNull(r)?.kind != row.kind) {
                N108Ui.op(player, p, base + 4, if (row.kind == KIND_LINK) "View page" else "View quest overview")
            }
            N108Ui.hidden(player, p, base + 4, !clickable)
            // The chosen quest: the gold star right after its name (design 05).
            N108Ui.hidden(player, p, base + 5, !row.star)
            if (row.star) N108Ui.pos(player, p, base + 5, textX + N108Ui.p11(player.world, row.text) + 3, y - 2)
            y += h
        }
        N108Ui.fonts(player, p, fonts)
        N108Ui.scroll(player, p, QP.LIST, QP.SCROLLBAR, maxOf(y + 2, QP.LIST_H))
    }

    fun questClick(player: Player, row: Int, toggle: Boolean = false) {
        if (selected(player) != Tab.QUESTS || !player.interfaces.isVisible(Tab.QUESTS.panel)) return
        if (row !in 0 until QP.ROWS) return
        val clicked = player.attr[QUEST_ROWS]?.getOrNull(row) ?: return
        if (clicked.kind == KIND_GROUP) return
        if (clicked.kind == KIND_LINK) {
            val delta = if (clicked.key == NEXT_PAGE) 1 else -1
            player.attr[QUEST_PAGE] = (player.attr[QUEST_PAGE] ?: 0) + delta
            N108Ui.scrollTop(player, Tab.QUESTS.panel, QP.LIST, QP.SCROLLBAR, QP.LIST_H)
            drawQuests(player)
        } else if (toggle) {
            if (clicked.kind != KIND_QUEST || clicked.arrow == null) return
            val open = player.attr[OPEN_QUESTS] ?: HashSet<String>().also { player.attr[OPEN_QUESTS] = it }
            if (!open.remove(clicked.key)) open += clicked.key
            drawQuests(player)
        } else {
            QuestTabDetails.open(player, clicked.key)
        }
    }

    // ================================================================== 2 Deadman

    /** m:ss of [ticks] game cycles (0.6 s). */
    fun clock(ticks: Int): String {
        val seconds = (ticks.coerceAtLeast(0) * 6L + 9) / 10
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "$h:${"%02d".format(m)}:${"%02d".format(s)}" else "$m:${"%02d".format(s)}"
    }

    /**
     * The Deadman tab, design A3 (N288): the bracket box (combat XP and rare drops, no Fights line), protection / skull / kill grace
     * (the skull time exactly the DMM HUD's, N286), the three balances as rows with the whole number (N287) - no store opens from
     * here (N280) -, the Archaic Emblem row (a click = Info on the emblem), breach and chest.
     */
    private fun drawDeadman(player: Player) {
        val p = Tab.DEADMAN.panel
        val world = player.world
        val tier = AnnihilationTier.of(player)
        N108Ui.text(player, p, DP.BADGE, tier.label)
        fun x(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
        N108Ui.text(player, p, DP.L1, "Combat XP <col=ffffff>${x(tier.combatXp)}x</col>")
        N108Ui.text(player, p, DP.L2, "Rare drops <col=ffffff>${x(tier.dropRate)}x</col>")
        // The cells: protection (only while he has it), skull, kill grace.
        class Cell(val icon: String, val value: String, val label: String)
        val cells = ArrayList<Cell>()
        if (BeginnerProtection.isProtected(player)) cells += Cell("dm_shield", "<col=56e156>${clock(player.timers[NEW_PLAYER_PROTECTION_TIMER])}</col>", "Protection")
        val skull = skullTime(player)
        cells += Cell("dm_skull", if (skull != null) "<col=ff5252>$skull</col>" else "<col=b5a891>-</col>", "Skull")
        val grace = player.timers.has(KillGrace.GRACE_TIMER)
        cells += Cell("dm_clock", if (grace) "<col=ffffff>${clock(player.timers[KillGrace.GRACE_TIMER])}</col>" else "<col=b5a891>-</col>", "Kill grace")
        val w = if (cells.size == 3) 56 else 85
        val gap = 3
        val y = DP.CELL_Y
        cells.forEachIndexed { k, cell ->
            val base = DP.cell(k)
            val cx = 5 + k * (w + gap) + if (cells.size == 2 && k == 1) 1 else 0
            N108Ui.hidden(player, p, base, cells.size != 2)
            N108Ui.hidden(player, p, base + 1, cells.size != 3)
            N108Ui.pos(player, p, base, cx, y)
            N108Ui.pos(player, p, base + 1, cx, y)
            val iconW = N108Ui.width(cell.icon)
            val valueW = N108Ui.p11(world, cell.value)
            val start = cx + (w - iconW - 3 - valueW) / 2
            N108Ui.sprite(player, p, base + 2, cell.icon)
            N108Ui.pos(player, p, base + 2, start, y + 3)
            N108Ui.hidden(player, p, base + 2, false)
            N108Ui.pos(player, p, base + 3, start + iconW + 3, y + 2)
            N108Ui.text(player, p, base + 3, cell.value)
            N108Ui.hidden(player, p, base + 3, false)
            // The label box is as wide as its cell, so the label is centred under its value.
            N108Ui.pos(player, p, base + 4, cx, y + 16)
            N108Ui.size(player, p, base + 4, w, 15)
            N108Ui.text(player, p, base + 4, cell.label)
            N108Ui.hidden(player, p, base + 4, false)
        }
        for (k in cells.size until DP.CELLS) for (c in 0 until DP.CELL_STRIDE) N108Ui.hidden(player, p, DP.cell(k) + c, true)
        // The balances, whole (up to 2,147,483,647 they fit beside the name).
        POINT_CURRENCIES.forEachIndexed { i, currency -> N108Ui.text(player, p, DP.point(i) + 3, DeadmanTabValues.number(DeadmanTabValues.balance(player, currency))) }
        // Emblem.
        val emblemTier = runCatching { DeadmanEmblem.ownedTier(player) }.getOrDefault(0)
        N108Ui.item(player, p, DP.EMBLEM_ITEM, DeadmanEmblem.FIRST_EMBLEM + (emblemTier - 1).coerceAtLeast(0))
        N108Ui.hidden(player, p, DP.EMBLEM_ITEM, false)
        // Owner 2026-10-24 (A): the row is named like the item, "Deadman Emblem" (the cache text said "Archaic Emblem").
        N108Ui.text(player, p, DP.EMBLEM_TEXT, DeadmanTabValues.Row.EMBLEM.title)
        N108Ui.text(player, p, DP.EMBLEM_VALUE, if (emblemTier > 0) "Tier $emblemTier" else "<col=b5a891>None</col>")
        // Breach.
        val active = DeadmanBreach.active
        if (active != null) {
            val left = active.openedAtCycle + DeadmanBreach.SPAWN_WINDOW_TICKS - world.currentCycle
            drawEvent(player, 0, "Breach <col=ff5252>active</col>", active.sites.joinToString(", ") { it.name }, if (left > 0) clock(left) else "", map = true)
        } else {
            val seconds = Duration.between(ZonedDateTime.now(ZoneOffset.UTC), DeadmanBreach.nextOpening).seconds
            drawEvent(player, 0, "Next breach", "Random location", clock((seconds * 10 / 6).toInt().coerceAtLeast(0)), map = false)
        }
        // Deadman's Chest: while one stands, the Map button shows where (and the world map's blue arrow, N273).
        val chest = DeadmanChestWorld.activeTimed()
        when {
            DeadmanChestIds.closedLoc <= 0 -> drawEvent(player, 1, "Deadman's Chest", "Unavailable", "", map = false)
            chest != null -> drawEvent(player, 1, "Deadman's Chest", chest.name.replaceFirstChar { it.titlecase() }, "", map = true)
            DeadmanChestWorld.ticksUntilTimed >= 0 -> drawEvent(player, 1, "Deadman's Chest", "Random spot", clock(DeadmanChestWorld.ticksUntilTimed), map = false)
            else -> drawEvent(player, 1, "Deadman's Chest", "Unavailable", "", map = false)
        }
    }

    /** The point rows' currencies, top to bottom (design A3). */
    val POINT_CURRENCIES = listOf(StoreCatalogue.Currency.LOYALTY, StoreCatalogue.Currency.DONATOR, StoreCatalogue.Currency.DEADMAN)

    /** N286: the skull time exactly as the DMM HUD shows it ([DeadmanHud.skullText]), null without a skull timer. */
    fun skullTime(player: Player): String? = gg.rsmod.plugins.content.mechanics.pvp.DeadmanHud.skullText(player)

    /** Panel x where an event's text starts, its right edge, and the Map button's x. */
    private const val EVENT_TEXT_X = 28
    private const val EVENT_RIGHT = 173
    private const val EVENT_MAP_X = 141

    /**
     * One event row (N278): the title on the first line; with a place to show, the Map button next to it and the place on the second
     * line with the whole width (a long place reads as its short form, else is cut with "..."; the button's tooltip has it in
     * full) and the time at its right; without one, the time at the right of the first line.
     */
    private fun drawEvent(player: Player, event: Int, title: String, place: String, time: String, map: Boolean) {
        val p = Tab.DEADMAN.panel
        val world = player.world
        val base = DP.event(event)
        val y = DP.EVENT_Y + event * DP.EVENT_STEP
        N108Ui.text(player, p, base + 2, title)
        // N518 class: measured with every digit as the widest one, so the place line keeps its size while the clock ticks (it was
        // re-sized - a size hook to the client - nearly every second).
        val timeW = if (time.isEmpty()) 0 else N108Ui.p11(world, time.replace(Regex("[0-9]"), "8"))
        if (map) {
            N108Ui.pos(player, p, base + 4, EVENT_RIGHT - 53, y + 11)
            N108Ui.pos(player, p, base + 5, EVENT_MAP_X, y + 1)
            N108Ui.pos(player, p, base + 6, EVENT_MAP_X, y)
        } else {
            N108Ui.pos(player, p, base + 4, EVENT_RIGHT - 53, y - 1)
        }
        val room = EVENT_RIGHT - EVENT_TEXT_X - if (map && timeW > 0) timeW + 6 else 0
        N108Ui.size(player, p, base + 3, room, 14)
        N108Ui.text(player, p, base + 3, placeLabel(world, place, room))
        N108Ui.text(player, p, base + 4, time)
        N108Ui.hidden(player, p, base + 5, !map)
        N108Ui.hidden(player, p, base + 6, !map)
        if (map) N108Ui.tooltips(player, listOf(N108Ui.hash(p, base + 5) to "Show on map: $place"))
    }

    private val DIRECTIONS = mapOf("north" to "N", "south" to "S", "east" to "E", "west" to "W", "north-east" to "NE", "north-west" to "NW", "south-east" to "SE", "south-west" to "SW")
    private val DIRECTION_PLACE = Regex("(?i)^(north-east|north-west|south-east|south-west|north|south|east|west) of (the )?(.+)$")
    private val OUTSIDE_PLACE = Regex("(?i)^outside (the )?(.+)$")

    /**
     * [place] as it fits [room] px of p11: as it is; else its short form ("East of the Rimmington mine" -> "Rimmington mine (E)",
     * "Outside the Al Kharid bank" -> "Al Kharid bank"); else cut with "...".
     */
    fun placeLabel(world: gg.rsmod.game.model.World, place: String, room: Int): String {
        if (N108Ui.p11(world, place) <= room) return place
        val short =
            DIRECTION_PLACE.find(place)?.let { m -> "${m.groupValues[3].replaceFirstChar { it.titlecase() }} (${DIRECTIONS.getValue(m.groupValues[1].lowercase())})" }
                ?: OUTSIDE_PLACE.find(place)?.let { m -> m.groupValues[2].replaceFirstChar { it.titlecase() } }
                ?: place
        return N108Ui.fitP11(world, short, room)
    }

    /** The Deadman Emblem row (design A3; named like the item since the owner chose A 2026-10-24 - it read "Archaic Emblem"): the Info window on the emblem - tier, value and more. */
    fun emblemClick(player: Player) = openInfo(player, Subject.EMBLEM)

    /** The Info subjects of the point rows, top to bottom ([POINT_CURRENCIES]). */
    val POINT_SUBJECTS = listOf(Subject.LOYALTY, Subject.DONATOR, Subject.DEADMAN)

    /** A point row (owner 2026-10-24: "ja"): the Info window on those points - what they are for and how you earn them. */
    fun pointClick(player: Player, row: Int) {
        openInfo(player, POINT_SUBJECTS.getOrNull(row) ?: return)
    }

    fun eventMap(player: Player, event: Int) {
        val tile =
            if (event == 0) {
                DeadmanBreach.active?.sites?.firstOrNull()?.spawners?.firstOrNull()
            } else {
                DeadmanChestWorld.activeTimed()?.tile
            } ?: return
        WorldMapPoint.open(player, tile)
    }

    // ================================================================== 3 Deadman Tasks

    private fun drawTasks(player: Player) {
        val p = Tab.TASKS.panel
        val world = player.world
        val earned = DeadmanTasks.earned(player)
        val right = "${N108Ui.number(earned)} DP"
        N108Ui.text(player, p, N108InterfaceImportTool.P_RIGHT, right)
        N108Ui.pos(player, p, TP.SKULL, 179 - N108Ui.p11(world, right) - 3 - N108Ui.width("dm_redskull13"), 4)
        DeadmanTasks.Tier.values().forEachIndexed { i, tier ->
            val base = TP.tier(i)
            val total = DeadmanTasks.tierTasks(tier).size
            val done = DeadmanTasks.tierDone(player, tier)
            N108Ui.text(player, p, base + 5, "$done/$total")
            val fill = if (total == 0) 0 else N108InterfaceImportTool.TIER_FILL * done / total
            N108Ui.pos(player, p, base + 4, fill - N108InterfaceImportTool.TIER_FILL, 0)
        }
        val closest = DeadmanTasks.closest(player)
        for (k in 0 until 4) {
            val base = TP.card(k)
            val task = closest.getOrNull(k)
            for (c in 0 until TP.CARD_STRIDE) if (c != 7) N108Ui.hidden(player, p, base + c, task == null)
            if (task == null) continue
            N108Ui.sprite(player, p, base, "tk_card_${task.tier.name.lowercase()}")
            N108Ui.item(player, p, base + 2, task.icon)
            N108Ui.text(player, p, base + 3, task.name)
            N108Ui.text(player, p, base + 4, "+${task.dp} <col=8fc78a>DP</col>")
            val have = DeadmanTasks.progress(player, task)
            N108Ui.text(player, p, base + 8, "$have/${task.target}")
            N108Ui.pos(player, p, base + 7, 108 * have / task.target - 108, 0)
        }
        N108Ui.hidden(player, p, TP.EMPTY, closest.isNotEmpty())
        if (closest.isEmpty()) N108Ui.text(player, p, TP.EMPTY, "Every Deadman Task is done!")
    }

    // ================================================================== 4 Drops & Collection Log

    private fun drawDrops(player: Player) {
        val p = Tab.DROPS.panel
        val total = runCatching { CollectionLogPages.allItems(player).size }.getOrDefault(0)
        val got = runCatching { CollectionLog.totalObtained(player) }.getOrDefault(0)
        N108Ui.text(player, p, LP.LOG_SUB, "Obtained <col=ffffff>$got</col> / $total")
        N108Ui.pos(player, p, LP.LOG_FILL, if (total == 0) -120 else 120 * got / total - 120, 0)
        val fresh = CollectionLog.recent(player)
        val tips = ArrayList<Pair<Int, String>>()
        for (k in 0 until 5) {
            val base = LP.slot(k)
            val entry = fresh.getOrNull(k)
            N108Ui.hidden(player, p, base + 1, entry == null)
            N108Ui.hidden(player, p, base + 2, entry == null)
            if (entry == null) continue
            N108Ui.item(player, p, base + 1, entry.item)
            N108Ui.text(player, p, base + 2, "KC ${N108Ui.number(entry.kc)}")
            val source = DropSources.byKey(player, entry.source)?.name
            tips += N108Ui.hash(p, base + 1) to listOfNotNull(DropSources.itemName(player.world, entry.item), source, "KC ${N108Ui.number(entry.kc)}").joinToString(" - ")
        }
        if (tips.isNotEmpty()) N108Ui.tooltips(player, tips)
    }

    // ------------------------------------------------------------------ Deadman Info window (N274, 1175)

    const val INFO = N108InterfaceImportTool.INFO

    private val INFO_SUBJECT = AttributeKey<Subject>()

    fun subjectLabel(subject: Subject, selected: Subject): String =
        if (subject == selected) "<col=ffffff>${QuestTabInfo.title(subject)}</col>" else QuestTabInfo.title(subject)

    fun openInfo(player: Player, subject: Subject = Subject.LOYALTY) {
        if (selected(player) != Tab.DEADMAN || !player.interfaces.isVisible(Tab.DEADMAN.panel)) return
        player.openInterface(INFO, InterfaceDestination.MAIN_SCREEN)
        N108Ui.reset(player, INFO)
        player.setInterfaceEvents(INFO, I.CLOSE, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INFO, I.MAP, -1..-1, EVENTS_OP1)
        Subject.values().forEachIndexed { i, _ -> player.setInterfaceEvents(INFO, I.subject(i) + 3, -1..-1, EVENTS_OP1) }
        showSubject(player, subject)
    }

    fun showSubject(player: Player, subject: Subject) {
        if (!player.interfaces.isVisible(INFO)) return
        player.attr[INFO_SUBJECT] = subject
        drawInfo(player)
    }

    /** The open Info window: the list with the chosen subject lit, and its page (redrawn every tick: only changes are sent). */
    fun drawInfo(player: Player) {
        val subject = player.attr[INFO_SUBJECT] ?: Subject.LOYALTY
        val i = INFO
        Subject.values().forEachIndexed { k, s ->
            N108Ui.hidden(player, i, I.subject(k), s != subject)
            N108Ui.text(player, i, I.subject(k) + 2, subjectLabel(s, subject))
        }
        val page = QuestTabInfo.page(player, subject)
        N108Ui.sprite(player, i, I.BIG, "zi_big_${N108InterfaceImportTool.INFO_ICONS[subject.ordinal]}")
        N108Ui.text(player, i, I.NAME, subject.title)
        N108Ui.text(player, i, I.TAG, page.tag)
        // Fact rows (label left, value right) or the table (four columns, the player's own row lit); stripes on every other row.
        val table = page.table
        val rows = table?.size ?: page.facts.size
        for (r in 0 until I.ROWS) {
            val base = I.row(r)
            val y = I.ROW_Y + r * I.ROW_H
            if (r >= rows) {
                for (c in 0 until I.ROW_STRIDE) N108Ui.hidden(player, i, base + c, true)
                continue
            }
            val stripe = when {
                table != null && r == 0 -> "zi_th"
                table != null && r == page.highlight -> "zi_me"
                r % 2 == 0 -> "zi_stripe"
                else -> null
            }
            N108Ui.hidden(player, i, base, stripe == null)
            if (stripe != null) N108Ui.sprite(player, i, base, stripe)
            if (table != null) {
                val cells = table[r]
                val colour = when {
                    r == 0 -> "ff981f"
                    r == page.highlight -> "ffcf4a"
                    else -> "ffffff"
                }
                for (c in 0 until 4) {
                    val x = I.ROW_X + 6 + TABLE_COLUMNS[c]
                    N108Ui.pos(player, i, base + 1 + c, x, y)
                    N108Ui.size(player, i, base + 1 + c, (if (c < 3) TABLE_COLUMNS[c + 1] else I.ROW_W - 6) - TABLE_COLUMNS[c], I.ROW_H)
                    N108Ui.text(player, i, base + 1 + c, "<col=$colour>${cells.getOrElse(c) { "" }}</col>")
                    N108Ui.hidden(player, i, base + 1 + c, false)
                }
                N108Ui.hidden(player, i, base + I.R_VALUE, true)
            } else {
                val fact = page.facts[r]
                N108Ui.pos(player, i, base + 1, I.ROW_X + 6, y)
                N108Ui.size(player, i, base + 1, 200, I.ROW_H)
                N108Ui.text(player, i, base + 1, fact.label)
                N108Ui.hidden(player, i, base + 1, false)
                N108Ui.hidden(player, i, base + 2, true)
                N108Ui.hidden(player, i, base + 3, true)
                N108Ui.hidden(player, i, base + 4, true)
                N108Ui.text(player, i, base + I.R_VALUE, fact.value)
                N108Ui.hidden(player, i, base + I.R_VALUE, false)
            }
        }
        // The text under the rows, one empty row apart: in p12 when it fits the room left (N285, readability), else p11.
        val textY = I.ROW_Y + rows * I.ROW_H + 6
        N108Ui.pos(player, i, I.TEXT, I.TEXT_X, textY)
        N108Ui.size(player, i, I.TEXT, I.TEXT_W, I.TEXT_BOTTOM - textY)
        N108Ui.text(player, i, I.TEXT, page.text)
        N108Ui.fonts(player, i, listOf(I.TEXT to infoTextFont(player.world, page.text, I.TEXT_BOTTOM - textY)))
        // The live line and its map button.
        N108Ui.text(player, i, I.BAR_TEXT, page.bar)
        N108Ui.size(player, i, I.BAR_TEXT, if (page.map != null) I.MAP_X - I.BAR_X - 12 else I.ROW_W - 16, 26)
        N108Ui.hidden(player, i, I.MAP, page.map == null)
        N108Ui.hidden(player, i, I.MAP_TEXT, page.map == null)
    }

    /** The Info text's font: p12 when its lines (15 px each) fit [room], else p11. */
    fun infoTextFont(world: gg.rsmod.game.model.World, text: String, room: Int): String =
        if (N108Ui.lines(world, "p12_full", text, I.TEXT_W) * 15 <= room) "p12_full" else "p11_full"

    /** Column x (from the row's text start) of the Brackets table: Level, Combat XP, Skills XP, Drops. */
    private val TABLE_COLUMNS = intArrayOf(0, 112, 196, 270)

    /** "Show on map" in the Info window: the live place of the subject shown. */
    fun infoMap(player: Player) {
        if (!player.interfaces.isVisible(INFO)) return
        val subject = player.attr[INFO_SUBJECT] ?: return
        val tile = QuestTabInfo.page(player, subject).map ?: return
        WorldMapPoint.open(player, tile)
    }

    @Suppress("unused")
    private fun unused(g: ChoiceGroup, q: ShortQuest) = Unit
}
