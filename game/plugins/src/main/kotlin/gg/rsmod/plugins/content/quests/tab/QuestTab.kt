package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.message.impl.IfCloseSubMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
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
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.quests.foundation.ChoiceGroup
import gg.rsmod.plugins.content.quests.foundation.QuestBrackets
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.quests.foundation.QuestGuideUi
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.tab.QuestTabInfo.Subject

/**
 * The quest tab with four tabs (owner design 2026-10-24 GOEDGEKEURD 05, spec section C): 1 Quests (N103: only the Magic / Prayer /
 * Gear groups with their quests and requirements - every other quest is out of the list), 2 Deadman (N109: bracket, protection,
 * skull, kill grace, the three point balances, emblem, breach, chest), 3 Deadman Tasks (N104), 4 Drops & Collection Log. Every value
 * is read live from the game; while a panel shows, [refresh] runs every tick and sends only what changed (timers count down).
 */
object QuestTab {
    const val CONTAINER = N108InterfaceImportTool.CONTAINER
    const val QUEST_LIST_INTERFACE = 190

    /**
     * DIARIES-OSRS D2 (owner 27-11, ontwerp v4): left to right Deadman, Drops & Collection Log, Quests, Achievement Diaries (the green
     * gem). Deadman Tasks moved to the gameframe Tasks tab ([TASKS_TAB], pane 1056). The new tab is called DIARY because the saved name
     * "DIARIES" of before 2026-10-24 still means the Quests tab.
     */
    enum class Tab(val panel: Int) {
        DEADMAN(N108InterfaceImportTool.DEADMAN),
        DROPS(N108InterfaceImportTool.DROPS_LOG),
        QUESTS(N108InterfaceImportTool.QUESTS),
        DIARY(gg.rsmod.plugins.content.diaries.DiaryUi.INTERFACE),
        ;

        val index get() = ordinal
    }

    val SELECTED = AttributeKey<String>(persistenceKey = "quest_tab_selected")
    val DEFAULT = Tab.DEADMAN

    /** The classic quest list (190) is showing in the Quests tab instead of the groups. */
    private val CLASSIC = AttributeKey<Boolean>()
    private val QUEST_ROWS = AttributeKey<List<QuestRow>>()
    private val QUEST_EVENTS = AttributeKey<List<Pair<Boolean, Boolean>>>()

    val REFRESH_TIMER = TimerKey()

    private const val EVENTS_OP1 = 0x2

    /** Old names (GROUPS, QUEST_LIST, DIARIES before 2026-10-24) open the Quests tab. */
    fun selected(player: Player): Tab =
        when (val name = player.attr[SELECTED]) {
            null -> DEFAULT
            "GROUPS", "QUEST_LIST", "DIARIES" -> Tab.QUESTS
            "TASKS" -> Tab.DEADMAN // DIARIES-OSRS D2: Deadman Tasks has its own gameframe tab now
            else -> Tab.values().firstOrNull { it.name == name } ?: DEFAULT
        }

    fun isMounted(player: Player): Boolean = player.interfaces.isVisible(CONTAINER)

    fun select(player: Player, tab: Tab) {
        // A second click on Quests while the classic list shows goes back to the groups.
        if (tab == Tab.QUESTS && selected(player) == Tab.QUESTS) player.attr.remove(CLASSIC)
        player.attr[SELECTED] = tab.name
        player.attr.remove(POINTS_VIEW)
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
                Tab.QUESTS -> {
                    for (r in 0 until QP.ROWS) player.setInterfaceEvents(tab.panel, QP.row(r) + 1, -1..-1, EVENTS_OP1)
                    player.setInterfaceEvents(tab.panel, QP.FILTER, -1..-1, EVENTS_OP1)
                }
                Tab.DEADMAN -> listOf(DP.POINTS_HIT, DP.BRACKET_HIT, DP.BACK_HIT, DP.SIGILS_HIT).forEach { player.setInterfaceEvents(tab.panel, it, -1..-1, EVENTS_OP1) }
                Tab.DIARY -> for (r in 0 until gg.rsmod.plugins.content.diaries.DiaryUi.DY.ROWS) player.setInterfaceEvents(tab.panel, gg.rsmod.plugins.content.diaries.DiaryUi.DY.row(r) + 16, -1..-1, EVENTS_OP1)
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
            Tab.DIARY -> gg.rsmod.plugins.content.diaries.DiaryUi.draw(player)
            Tab.DROPS -> drawDrops(player)
        }
        return true
    }

    /** DIARIES-OSRS D2: the Deadman Tasks panel in the gameframe Tasks tab (pane 1056 slot, [gg.rsmod.plugins.api.InterfaceDestination.ACHIEVEMENTS_TAB]). */
    const val TASKS_TAB = N108InterfaceImportTool.TASKS

    /** Mount hook of the Tasks tab: the tier buttons' click events and the first draw. */
    fun mountTasksTab(player: Player) {
        N108Ui.reset(player, TASKS_TAB)
        for (t in 0 until 4) player.setInterfaceEvents(TASKS_TAB, TP.tier(t) + 7, -1..-1, EVENTS_OP1)
        refreshTasksTab(player)
    }

    /** Draws the Tasks tab when it is open (task progress, a finished task, the minute timer). */
    fun refreshTasksTab(player: Player) {
        if (player.interfaces.isVisible(TASKS_TAB)) drawTasks(player)
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

    /**
     * DMM-TAB D3 (owner design 2026-11-20): one Quest List row. [kind] says what a click opens: a bracket header or storyline row
     * the Combat Brackets window on that bracket, a quest its overview; gaps and the "Other Quests" header do nothing.
     */
    class QuestRow(val kind: Int, val key: String, val text: String, val bracket: Int = -1)

    const val ROW_GAP = 0
    const val ROW_HEAD = 1
    const val ROW_QUEST = 2
    const val ROW_STORY = 3
    const val ROW_OTHER_HEAD = 4

    private const val C_GREEN = "0dc10d"
    private const val C_YELLOW = "ffff00"
    private const val C_RED = "ff0000"
    private const val C_LATER = "968c78"
    private const val C_ORANGE = "ff981f"

    /** The gear button: hide the completed "Other Quests" (OSRS quest list filter). */
    internal val HIDE_DONE = AttributeKey<Boolean>(persistenceKey = "quest_list_hide_done")

    /** OSRS quest colours from the real quest state; a quest of a bracket not reached yet reads grey. */
    private fun questColour(player: Player, name: String, open: Boolean): String {
        val entry = gg.rsmod.plugins.content.quests.QuestList.ENTRIES.firstOrNull { it.name == name } ?: return if (open) C_GREEN else C_LATER
        return when (QuestStatus.state(player, entry.slot)) {
            QuestStatus.State.COMPLETED -> C_GREEN
            QuestStatus.State.IN_PROGRESS -> C_YELLOW
            QuestStatus.State.NOT_STARTED -> if (open) C_RED else C_LATER
        }
    }

    /** The photo's names ("Monkey Madness I", "Dragon Slayer I") for the list entries named without the numeral. */
    fun display(name: String) =
        when (name) {
            "Monkey Madness" -> "Monkey Madness I"
            "Dragon Slayer" -> "Dragon Slayer I"
            else -> name
        }

    fun questRows(player: Player): List<QuestRow> {
        val legacy = !QuestBrackets.applies(player)
        val out = ArrayList<QuestRow>()
        val inBrackets = HashSet<String>()
        // N646 (owner 22-11 'filter knop in quest list doet het niet'): the Filter hid completed quests only under Other Quests,
        // below the 36 bracket quests - the visible list never changed. It now hides them in the brackets too.
        val hide = player.attr[HIDE_DONE] == true
        DeadmanBrackets.Bracket.values().forEach { bracket ->
            val step = QuestBrackets.step(bracket)
            val open = legacy || CombatBracketsUi.reached(player, bracket)
            if (out.isNotEmpty()) out += QuestRow(ROW_GAP, "", "")
            out += QuestRow(ROW_HEAD, bracket.name, "<col=$C_ORANGE>Combat ${bracket.levels.first} - ${bracket.levels.last}</col>", bracket.ordinal)
            CombatBracketsUi.bracketQuests(bracket).forEach { q ->
                inBrackets += q
                val rfd = q.startsWith("RFD: ")
                val col = if (rfd) (if (open) C_GREEN else C_LATER) else questColour(player, q, open)
                if (hide && col == C_GREEN) return@forEach
                out += QuestRow(ROW_QUEST, if (rfd) "Recipe for Disaster" else q, "<col=$col>${display(q)}</col>", bracket.ordinal)
            }
            val chosen = QuestBrackets.chosenOption(player, bracket)
            step.options.forEachIndexed { k, story ->
                inBrackets += story.quests
                val col =
                    when {
                        legacy -> C_GREEN
                        !open -> C_LATER
                        chosen == null -> C_YELLOW
                        chosen == k + 1 -> C_GREEN
                        else -> C_RED
                    }
                out += QuestRow(ROW_STORY, story.label, "<col=$col>Story: ${story.label.substringBefore(" storyline")}</col>", bracket.ordinal)
            }
        }
        out += QuestRow(ROW_GAP, "", "")
        out += QuestRow(ROW_OTHER_HEAD, "", "<col=$C_ORANGE>Other Quests</col>")
        gg.rsmod.plugins.content.quests.QuestList.ENTRIES
            .filter { it.name !in inBrackets }
            .sortedBy { it.name.removePrefix("The ") }
            .forEach { e ->
                val state = QuestStatus.state(player, e.slot)
                if (hide && state == QuestStatus.State.COMPLETED) return@forEach
                val col =
                    when (state) {
                        QuestStatus.State.COMPLETED -> C_GREEN
                        QuestStatus.State.IN_PROGRESS -> C_YELLOW
                        QuestStatus.State.NOT_STARTED -> C_RED
                    }
                out += QuestRow(ROW_QUEST, e.name, "<col=$col>${e.name}</col>")
            }
        return out.take(QP.ROWS)
    }

    /** Bottom lines (owner design): what a DMM player wants to see first. */
    fun questSummary(player: Player): List<String> {
        val legacy = !QuestBrackets.applies(player)
        var have = 0
        var total = 0
        var stories = 0
        var storyTotal = 0
        DeadmanBrackets.Bracket.values().forEach { b ->
            val n = CombatBracketsUi.bracketQuests(b).size
            total += n
            if (legacy || CombatBracketsUi.reached(player, b)) have += n
            if (QuestBrackets.step(b).options.isNotEmpty()) {
                storyTotal++
                if (legacy || QuestBrackets.chosenOption(player, b) != null) stories++
            }
        }
        return listOf(
            "Your bracket: <col=ffffff>${bracketLabel(player)}</col>",
            "Unlocked: <col=ffffff>$have/$total</col>",
            "Storylines: <col=ffffff>$stories/$storyTotal</col>",
        )
    }

    private fun drawQuests(player: Player) {
        val p = Tab.QUESTS.panel
        val rows = questRows(player)
        player.attr[QUEST_ROWS] = rows
        val world = player.world
        val fonts = ArrayList<Pair<Int, String>>()
        for (r in 0 until QP.ROWS) {
            val base = QP.row(r)
            val row = rows.getOrNull(r)
            val text = row?.text.orEmpty()
            N108Ui.hidden(player, p, base, text.isEmpty())
            if (row != null && text.isNotEmpty()) {
                val head = row.kind == ROW_HEAD || row.kind == ROW_OTHER_HEAD
                val font = if (head) "b12_full" else "p12_full"
                fonts += base to font
                N108Ui.text(player, p, base, N108Ui.fit(world, font, text, 150))
            }
            N108Ui.hidden(player, p, base + 1, row == null || row.kind == ROW_GAP || row.kind == ROW_OTHER_HEAD)
        }
        N108Ui.fonts(player, p, fonts)
        questSummary(player).forEachIndexed { k, line -> N108Ui.text(player, p, QP.BOTTOM_FIRST + k, line) }
        N108Ui.scroll(player, p, QP.LIST, QP.SCROLLBAR, maxOf(8 + rows.size * QP.ROW_H, QP.LIST_H))
    }

    fun questClick(player: Player, row: Int) {
        if (selected(player) != Tab.QUESTS || !player.interfaces.isVisible(Tab.QUESTS.panel)) return
        val clicked = player.attr[QUEST_ROWS]?.getOrNull(row) ?: return
        when (clicked.kind) {
            ROW_HEAD, ROW_STORY -> CombatBracketsUi.open(player, clicked.bracket)
            ROW_QUEST -> QuestTabDetails.open(player, clicked.key)
        }
    }

    /** The gear button: hide / show the completed Other Quests. */
    fun questFilter(player: Player) {
        if (selected(player) != Tab.QUESTS || !player.interfaces.isVisible(Tab.QUESTS.panel)) return
        if (player.attr[HIDE_DONE] == true) player.attr.remove(HIDE_DONE) else player.attr[HIDE_DONE] = true
        player.message(if (player.attr[HIDE_DONE] == true) "Completed quests are now hidden." else "Completed quests are now shown.")
        drawQuests(player)
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
    /** DMM-TAB: the Deadman panel shows Points Info instead of the main state (owner design 2026-11-20). */
    private val POINTS_VIEW = AttributeKey<Boolean>()

    /** Points Info rows (owner design 2026-11-20): the balance, its name ("Donator Points" reads "78 Tokens") and how you earn it. */
    val POINTS_INFO =
        listOf(
            Triple(StoreCatalogue.Currency.DEADMAN, "Deadman Points", "PvP, bosses and tasks"),
            Triple(StoreCatalogue.Currency.DONATOR, "78 Tokens", "From the 78legacy.com store"),
            Triple(StoreCatalogue.Currency.LOYALTY, "Loyalty Points", "Daily login and objectives"),
        )

    /** "Next bracket: 84 / 91" and the bar's fill (0..[DP.FILL_W]); the last bracket reads "Max bracket", full. */
    fun nextBracket(player: Player): Pair<String, Int> {
        val bracket = DeadmanBrackets.of(player)
        val next = DeadmanBrackets.Bracket.values().getOrNull(bracket.ordinal + 1) ?: return "Max bracket" to DP.FILL_W
        val lo = bracket.levels.first
        val hi = next.levels.first
        val level = player.combatLevel.coerceIn(lo, hi)
        return "Next bracket: $level / $hi" to DP.FILL_W * (level - lo) / (hi - lo)
    }

    fun bracketLabel(player: Player): String = DeadmanBrackets.of(player).levels.let { "${it.first} - ${it.last}" }

    private fun drawDeadman(player: Player) {
        val p = Tab.DEADMAN.panel
        val info = player.attr[POINTS_VIEW] == true
        N108Ui.sprite(player, p, DP.BG, if (info) "zz_dm_points" else "zz_dm_main")
        N108Ui.text(player, p, DP.TITLE, if (info) "Points Info" else "78 Deadman")
        DP.MAIN.forEach { N108Ui.hidden(player, p, it, info) }
        DP.INFO.forEach { N108Ui.hidden(player, p, it, !info) }
        if (info) {
            POINTS_INFO.forEachIndexed { i, (currency, name, how) ->
                N108Ui.text(player, p, DP.info(i), name)
                N108Ui.text(player, p, DP.info(i) + 1, DeadmanTabValues.number(DeadmanTabValues.balance(player, currency)))
                N108Ui.text(player, p, DP.info(i) + 2, how)
            }
            return
        }
        N108Ui.text(player, p, DP.DP_VALUE, DeadmanTabValues.number(DeadmanTabValues.balance(player, StoreCatalogue.Currency.DEADMAN)))
        N108Ui.text(player, p, DP.BRK_VALUE, bracketLabel(player))
        val (bar, fill) = nextBracket(player)
        N108Ui.text(player, p, DP.BAR_TEXT, bar)
        N108Ui.pos(player, p, DP.FILL, fill - DP.FILL_W, 0)
    }

    /** The Points Info button / Back (owner: every button links through). */
    fun pointsInfo(player: Player, show: Boolean) {
        if (selected(player) != Tab.DEADMAN || !player.interfaces.isVisible(Tab.DEADMAN.panel)) return
        if (show) player.attr[POINTS_VIEW] = true else player.attr.remove(POINTS_VIEW)
        drawDeadman(player)
    }

    /** The Bracket Info button: the Combat Brackets window. */
    fun bracketInfo(player: Player) {
        if (selected(player) != Tab.DEADMAN || !player.interfaces.isVisible(Tab.DEADMAN.panel)) return
        CombatBracketsUi.open(player)
    }

    /** The Sigils Info button: the Attuned Sigils window (SIGIL-INFO-UI, owner design 21-11). */
    fun sigilsInfo(player: Player) {
        if (selected(player) != Tab.DEADMAN || !player.interfaces.isVisible(Tab.DEADMAN.panel)) return
        gg.rsmod.plugins.content.mechanics.deadman.sigils.AttunedSigilsUi.open(player)
    }

    /** The point rows' currencies, top to bottom (design A3). */
    val POINT_CURRENCIES = listOf(StoreCatalogue.Currency.LOYALTY, StoreCatalogue.Currency.DONATOR, StoreCatalogue.Currency.DEADMAN)

    /** N286: the skull time exactly as the DMM HUD shows it ([DeadmanHud.skullText]), null without a skull timer. */
    fun skullTime(player: Player): String? = gg.rsmod.plugins.content.mechanics.pvp.DeadmanHud.skullText(player)

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
        val p = TASKS_TAB
        // DMM-TASKS-OVERLAP: levels and emblem tiers reached since the last task event complete before the tab is drawn
        // (they showed as 1000/1000 under 'Closest to done' without points).
        runCatching { DeadmanTasks.check(player) }.onFailure { gg.rsmod.game.model.Trace.crash("deadman tasks draw", it) }
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
