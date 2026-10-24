package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.message.impl.IfCloseSubMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.tools.importer.N108InterfaceImportTool
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.DP
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.LP
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.Q
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.QP
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.TP
import gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool as L
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
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
    private val QUEST_ROWS = AttributeKey<List<String>>()

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
                    for (c in 0 until DP.CELLS) player.setInterfaceEvents(tab.panel, DP.cell(c) + 5, -1..-1, EVENTS_OP1)
                    for (e in 0 until 2) player.setInterfaceEvents(tab.panel, DP.event(e) + 5, -1..-1, EVENTS_OP1)
                }
                Tab.TASKS -> for (t in 0 until 4) player.setInterfaceEvents(tab.panel, TP.tier(t) + 7, -1..-1, EVENTS_OP1)
                Tab.DROPS -> {
                    player.setInterfaceEvents(tab.panel, LP.VIEWER_HIT, -1..-1, EVENTS_OP1)
                    player.setInterfaceEvents(tab.panel, LP.LOG_HIT, -1..-1, EVENTS_OP1)
                    for (r in 0 until 3) player.setInterfaceEvents(tab.panel, LP.recent(r) + 4, -1..-1, EVENTS_OP1)
                    for (s in 0 until 5) player.setInterfaceEvents(tab.panel, LP.slot(s) + 1, -1..-1, EVENTS_OP1)
                }
            }
            if (tab == Tab.QUESTS) for (r in 0 until QP.ROWS) player.setInterfaceEvents(tab.panel, QP.row(r) + 4, -1..-1, EVENTS_OP1)
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

    fun questRows(player: Player): List<QuestRow> {
        val open = player.attr[OPEN_QUESTS] ?: HashSet<String>().also { player.attr[OPEN_QUESTS] = it }
        val legacy = QuestChoices.isLegacy(player)
        val out = ArrayList<QuestRow>()
        for (group in QuestGuideUi.ROWS) {
            val chosen = QuestChoices.chosen(player, group)
            val right = when {
                legacy -> "<col=0dc10d>All yours</col>"
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
                if (isOpen) reqs.forEach { r -> out += QuestRow(KIND_SUB, name, if (done) "q_ck7" else "q_dot5", "<col=b3a78f>$r</col>", null, null) }
            }
        }
        return out
    }

    private fun drawQuests(player: Player) {
        val p = QuestTab.Tab.QUESTS.panel
        val rows = questRows(player)
        player.attr[QUEST_ROWS] = rows.map { if (it.kind == KIND_QUEST || it.kind == KIND_LINK) it.key else "" }
        val chosen = QuestGuideUi.ROWS.count { QuestChoices.chosen(player, it) != null }
        N108Ui.text(player, p, N108InterfaceImportTool.P_RIGHT, if (QuestChoices.isLegacy(player)) "" else "<col=9a9083>Groups</col> $chosen/${QuestGuideUi.ROWS.size}")
        var y = 0
        val fonts = ArrayList<Pair<Int, String>>()
        for (r in 0 until QP.ROWS) {
            val base = QP.row(r)
            val row = rows.getOrNull(r)
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
                N108Ui.pos(player, p, base + 3, if (row.arrow == "q_two") 151 else 152, y + if (row.arrow == "q_two") 5 else 4)
            }
            N108Ui.pos(player, p, base + 4, 0, y)
            N108Ui.hidden(player, p, base + 4, row.kind != KIND_QUEST && row.kind != KIND_LINK)
            // The chosen quest: the gold star right after its name (design 05).
            N108Ui.hidden(player, p, base + 5, !row.star)
            if (row.star) N108Ui.pos(player, p, base + 5, textX + N108Ui.p11(player.world, row.text) + 3, y - 2)
            y += h
        }
        N108Ui.fonts(player, p, fonts)
        N108Ui.scroll(player, p, QP.LIST, QP.SCROLLBAR, maxOf(y + 2, QP.LIST_H))
    }

    fun questClick(player: Player, row: Int) {
        val key = player.attr[QUEST_ROWS]?.getOrNull(row)?.takeIf { it.isNotEmpty() } ?: return
        if (key == "classic") {
            player.attr[CLASSIC] = true
            mount(player)
            return
        }
        val open = player.attr[OPEN_QUESTS] ?: HashSet<String>().also { player.attr[OPEN_QUESTS] = it }
        if (!open.remove(key)) open += key
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

    private fun drawDeadman(player: Player) {
        val p = Tab.DEADMAN.panel
        val world = player.world
        val tier = AnnihilationTier.of(player)
        N108Ui.text(player, p, DP.BADGE, tier.label)
        fun x(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
        N108Ui.text(player, p, DP.L1, "${x(tier.combatXp)}x combat XP")
        N108Ui.text(player, p, DP.L2, "${x(tier.dropRate)}x rare drops")
        val range = AreaState.bracketOf(player.combatLevel)
        N108Ui.text(player, p, DP.L3, "Fights lvl <col=ffffff>${range.first} - ${range.last}</col>")
        // Row 1: protection (only while he has it), skull, kill grace.
        class Cell(val icon: String, val value: String, val label: String)
        val row1 = ArrayList<Cell>()
        if (BeginnerProtection.isProtected(player)) row1 += Cell("dm_shield", "<col=56e156>${clock(player.timers[NEW_PLAYER_PROTECTION_TIMER])}</col>", "Protection")
        val skull = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.hasSkullTimer(player)
        val skullTicks = if (skull) runCatching { player.timers[gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER] }.getOrDefault(0) else 0
        row1 += Cell("dm_skull", if (skull) "<col=ff5252>${clock(skullTicks)}</col>" else "<col=9a9083>-</col>", "Skull")
        val grace = player.timers.has(KillGrace.GRACE_TIMER)
        row1 += Cell("dm_clock", if (grace) "<col=ffffff>${clock(player.timers[KillGrace.GRACE_TIMER])}</col>" else "<col=9a9083>-</col>", "Kill grace")
        val row2 =
            listOf(
                Cell("dm_star", DeadmanTabValues.number(DeadmanTabValues.balance(player, StoreCatalogue.Currency.LOYALTY)), "Loyalty"),
                Cell("dm_coins", DeadmanTabValues.number(DeadmanTabValues.balance(player, StoreCatalogue.Currency.DONATOR)), "Donator"),
                Cell("dm_redskull14", DeadmanTabValues.number(DeadmanTabValues.balance(player, StoreCatalogue.Currency.DEADMAN)), "Deadman"),
            )
        fun drawRow(cells: List<Cell>, first: Int, y: Int) {
            val w = if (cells.size == 3) 56 else 85
            val gap = 3
            cells.forEachIndexed { k, cell ->
                val base = DP.cell(first + k)
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
                N108Ui.pos(player, p, base + 4, cx, y + 17)
                N108Ui.text(player, p, base + 4, cell.label)
                N108Ui.hidden(player, p, base + 4, false)
                N108Ui.pos(player, p, base + 5, cx, y)
                N108Ui.hidden(player, p, base + 5, first < 3)
            }
            for (k in cells.size until 3) for (c in 0 until DP.CELL_STRIDE) N108Ui.hidden(player, p, DP.cell(first + k) + c, true)
        }
        drawRow(row1, 0, 77)
        drawRow(row2, 3, 113)
        // Emblem.
        val emblemTier = runCatching { DeadmanEmblem.ownedTier(player) }.getOrDefault(0)
        if (emblemTier > 0) {
            N108Ui.item(player, p, DP.EMBLEM_ITEM, DeadmanEmblem.FIRST_EMBLEM + emblemTier - 1, 1)
            N108Ui.hidden(player, p, DP.EMBLEM_ITEM, false)
        } else {
            N108Ui.item(player, p, DP.EMBLEM_ITEM, DeadmanEmblem.FIRST_EMBLEM, 1)
            N108Ui.hidden(player, p, DP.EMBLEM_ITEM, false)
        }
        N108Ui.text(player, p, DP.EMBLEM_VALUE, if (emblemTier > 0) "Tier $emblemTier" else "<col=9a9083>None</col>")
        // Breach.
        val b = DP.event(0)
        val active = DeadmanBreach.active
        if (active != null) {
            val left = active.openedAtCycle + DeadmanBreach.SPAWN_WINDOW_TICKS - world.currentCycle
            N108Ui.text(player, p, b + 2, "Breach <col=ff5252>active</col>")
            N108Ui.text(player, p, b + 3, active.sites.joinToString(", ") { it.name })
            N108Ui.text(player, p, b + 4, if (left > 0) clock(left) else "")
        } else {
            val seconds = Duration.between(ZonedDateTime.now(ZoneOffset.UTC), DeadmanBreach.nextOpening).seconds
            N108Ui.text(player, p, b + 2, "Next breach")
            N108Ui.text(player, p, b + 3, "Random location")
            N108Ui.text(player, p, b + 4, clock((seconds * 10 / 6).toInt().coerceAtLeast(0)))
        }
        N108Ui.hidden(player, p, b + 5, active == null)
        N108Ui.hidden(player, p, b + 6, active == null)
        // Deadman's Chest.
        val c = DP.event(1)
        val chest = DeadmanChestWorld.activeTimed()
        N108Ui.text(player, p, c + 2, "Deadman's Chest")
        when {
            DeadmanChestIds.closedLoc <= 0 -> {
                N108Ui.text(player, p, c + 3, "Unavailable")
                N108Ui.text(player, p, c + 4, "")
            }
            chest != null -> {
                N108Ui.text(player, p, c + 3, chest.name.replaceFirstChar { it.titlecase() })
                N108Ui.text(player, p, c + 4, "<col=56e156>Active</col>")
            }
            DeadmanChestWorld.ticksUntilTimed >= 0 -> {
                N108Ui.text(player, p, c + 3, "Random spot")
                N108Ui.text(player, p, c + 4, clock(DeadmanChestWorld.ticksUntilTimed))
            }
            else -> {
                N108Ui.text(player, p, c + 3, "Unavailable")
                N108Ui.text(player, p, c + 4, "")
            }
        }
        N108Ui.hidden(player, p, c + 5, chest == null)
        N108Ui.hidden(player, p, c + 6, chest == null)
    }

    /** A click on a points cell: where those points are spent. */
    fun cellClick(player: Player, cell: Int) {
        val shop =
            when (cell) {
                3 -> StoreCatalogue.Shop.LOYALTY
                4 -> StoreCatalogue.Shop.DONATOR
                5 -> StoreCatalogue.Shop.DEADMAN
                else -> return
            }
        gg.rsmod.plugins.content.mechanics.store.StoreUi.open(player, shop)
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
            val fill = if (total == 0) 0 else 58 * done / total
            N108Ui.pos(player, p, base + 4, fill - 58, 0)
        }
        val closest = DeadmanTasks.closest(player)
        for (k in 0 until 4) {
            val base = TP.card(k)
            val task = closest.getOrNull(k)
            for (c in 0 until TP.CARD_STRIDE) if (c != 7) N108Ui.hidden(player, p, base + c, task == null)
            if (task == null) continue
            N108Ui.sprite(player, p, base, "tk_card_${task.tier.name.lowercase()}")
            N108Ui.item(player, p, base + 2, task.icon, 1)
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
        val recent = DropViewer.recent(player).mapNotNull { DropSources.byKey(player, it) }
        val models = ArrayList<N108Ui.Model>()
        for (r in 0 until 3) {
            val base = LP.recent(r)
            val s = recent.getOrNull(r)
            for (c in 0 until LP.RECENT_STRIDE) N108Ui.hidden(player, p, base + c, s == null)
            if (s == null) continue
            N108Ui.text(player, p, base + 2, s.name)
            N108Ui.text(player, p, base + 3, "KC ${N108Ui.number(KillCounts.get(player, s.key))}")
            if (s.modelNpc >= 0) models += N108Ui.Model(N108Ui.hash(p, base + 1), s.modelNpc, DropViewer.thumbZoom(player, s.modelNpc), 0, false) else N108Ui.hidden(player, p, base + 1, true)
        }
        if (models.isNotEmpty()) N108Ui.models(player, models)
        val fresh = CollectionLog.recent(player)
        val tips = ArrayList<Pair<Int, String>>()
        for (k in 0 until 5) {
            val base = LP.slot(k)
            val entry = fresh.getOrNull(k)
            N108Ui.hidden(player, p, base + 1, entry == null)
            N108Ui.hidden(player, p, base + 2, entry == null)
            if (entry == null) continue
            N108Ui.item(player, p, base + 1, entry.item, 1)
            N108Ui.text(player, p, base + 2, "KC ${N108Ui.number(entry.kc)}")
            val source = DropSources.byKey(player, entry.source)?.name
            tips += N108Ui.hash(p, base + 1) to listOfNotNull(DropSources.itemName(player.world, entry.item), source, "KC ${N108Ui.number(entry.kc)}").joinToString(" - ")
        }
        if (tips.isNotEmpty()) N108Ui.tooltips(player, tips)
    }

    fun recentClick(player: Player, row: Int) {
        val key = DropViewer.recent(player).getOrNull(row) ?: return
        DropViewer.open(player, key)
    }

    // ------------------------------------------------------------------ Info window (unchanged, 1175)

    const val INFO = L.INFO

    fun subjectLabel(subject: Subject, selected: Subject): String =
        if (subject == selected) "<col=ffffff>${QuestTabInfo.title(subject)}</col>" else QuestTabInfo.title(subject)

    fun openInfo(player: Player, subject: Subject = Subject.LOYALTY) {
        if (selected(player) != Tab.DEADMAN || !player.interfaces.isVisible(Tab.DEADMAN.panel)) return
        player.openInterface(INFO, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INFO, L.I_CLOSE, -1..-1, EVENTS_OP1)
        player.setComponentText(INFO, L.I_TITLE, "Deadman Info")
        Subject.values().forEachIndexed { i, _ -> player.setInterfaceEvents(INFO, L.subjectBase(i), -1..-1, EVENTS_OP1) }
        showSubject(player, subject)
    }

    fun showSubject(player: Player, subject: Subject) {
        if (!player.interfaces.isVisible(INFO)) return
        Subject.values().forEachIndexed { i, s -> player.setComponentText(INFO, L.subjectBase(i) + 1, subjectLabel(s, subject)) }
        player.setComponentText(INFO, L.I_TEXT, QuestTabInfo.text(subject))
    }

    @Suppress("unused")
    private fun unused(g: ChoiceGroup, q: ShortQuest) = Unit
}
