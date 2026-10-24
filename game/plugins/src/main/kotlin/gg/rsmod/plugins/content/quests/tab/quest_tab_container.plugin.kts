package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.tools.importer.N108InterfaceImportTool as N
import gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool as L
import gg.rsmod.plugins.api.ext.PaneMountHooks
import gg.rsmod.plugins.content.quests.tab.QuestTabInfo.Subject

/*
 * The quest tab with four tabs, the Drop Viewer, the Collection Log and the Deadman Tasks window (night job 2026-10-24, N108 / N104 /
 * N103 / N109). Every handler checks that its interface is the one showing; the button handler itself already drops clicks on
 * interfaces that are not open.
 */

PaneMountHooks.register(QuestTab.CONTAINER) { player -> QuestTab.mount(player) }

QuestTab.Tab.values().forEach { tab ->
    on_button(interfaceId = QuestTab.CONTAINER, component = N.Q.tab(tab.index)) {
        if (!QuestTab.isMounted(player)) return@on_button
        QuestTab.select(player, tab)
    }
}

// ---- 1 Quests
for (r in 0 until N.QP.ROWS) {
    on_button(interfaceId = N.QUESTS, component = N.QP.row(r) + 4) { QuestTab.questClick(player, r) }
}

// ---- 2 Deadman
on_button(interfaceId = N.DEADMAN, component = N.DP.INFO) { QuestTab.openInfo(player) }
for (c in 0 until N.DP.CELLS) {
    on_button(interfaceId = N.DEADMAN, component = N.DP.cell(c) + 5) { QuestTab.cellClick(player, c) }
}
for (e in 0 until 2) {
    on_button(interfaceId = N.DEADMAN, component = N.DP.event(e) + 5) { QuestTab.eventMap(player, e) }
}
Subject.values().forEachIndexed { i, subject ->
    on_button(interfaceId = QuestTab.INFO, component = L.subjectBase(i)) { QuestTab.showSubject(player, subject) }
}
on_button(interfaceId = QuestTab.INFO, component = L.I_CLOSE) {
    if (player.interfaces.isVisible(QuestTab.INFO)) player.closeInterface(QuestTab.INFO)
}

// ---- 3 Deadman Tasks
for (t in 0 until 4) {
    on_button(interfaceId = N.TASKS, component = N.TP.tier(t) + 7) { DeadmanTasksUi.open(player, t + 1) }
}
on_button(interfaceId = DeadmanTasksUi.INTERFACE, component = N.W.CLOSE) {
    if (DeadmanTasksUi.isOpen(player)) player.closeInterface(DeadmanTasksUi.INTERFACE)
}
for (t in 0 until 5) {
    on_button(interfaceId = DeadmanTasksUi.INTERFACE, component = N.W.tier(t) + 8) { DeadmanTasksUi.tier(player, t) }
}
for (f in 0 until 3) {
    on_button(interfaceId = DeadmanTasksUi.INTERFACE, component = N.W.filter(f) + 2) { DeadmanTasksUi.filter(player, f) }
}
on_button(interfaceId = DeadmanTasksUi.INTERFACE, component = N.W.TYPE_HIT) { DeadmanTasksUi.nextType(player) }

// ---- 4 Drops & Collection Log
on_button(interfaceId = N.DROPS_LOG, component = N.LP.VIEWER_HIT) { DropViewer.open(player) }
on_button(interfaceId = N.DROPS_LOG, component = N.LP.LOG_HIT) { CollectionLogUi.open(player) }
for (r in 0 until 3) {
    on_button(interfaceId = N.DROPS_LOG, component = N.LP.recent(r) + 4) { QuestTab.recentClick(player, r) }
}
for (s in 0 until 5) {
    on_button(interfaceId = N.DROPS_LOG, component = N.LP.slot(s) + 1) {
        val entry = CollectionLog.recent(player).getOrNull(s) ?: return@on_button
        val page = CollectionLogPages.all(player).firstOrNull { entry.source in it.sources && entry.item in it.items } ?: CollectionLogPages.all(player).firstOrNull { entry.item in it.items }
        CollectionLogUi.open(player, page?.key)
    }
}

// ---- Drop Viewer
on_button(interfaceId = DropViewer.INTERFACE, component = N.V.CLOSE) {
    if (DropViewer.isOpen(player)) player.closeInterface(DropViewer.INTERFACE)
}
on_button(interfaceId = DropViewer.INTERFACE, component = N.V.SEARCH_HIT) {
    // The client focuses the field itself (RspsUi); nothing to do here.
}
on_button(interfaceId = DropViewer.INTERFACE, component = N.V.STAR_ON) { DropViewer.toggleFavourite(player) }
on_button(interfaceId = DropViewer.INTERFACE, component = N.V.STAR_OFF) { DropViewer.toggleFavourite(player) }
on_button(interfaceId = DropViewer.INTERFACE, component = N.V.MAP) { DropViewer.showOnMap(player) }
for (r in 0 until N.V.ROW_SLOTS) {
    on_button(interfaceId = DropViewer.INTERFACE, component = N.V.row(r) + N.V.R_TOGGLE) { DropViewer.rowClick(player, r) }
}
for (s in 0 until N.V.LIST_SLOTS) {
    on_button(interfaceId = DropViewer.INTERFACE, component = N.V.slot(s) + N.V.L_VIEW) { DropViewer.listClick(player, s, toggle = false) }
    on_button(interfaceId = DropViewer.INTERFACE, component = N.V.slot(s) + N.V.L_TOGGLE) { DropViewer.listClick(player, s, toggle = true) }
}

/** The in-window text field (client RspsUi) sends what is typed; never shown in the chat. */
on_command("uisearch") {
    val args = player.attr[gg.rsmod.game.model.attr.COMMAND_ARGS_ATTR] ?: emptyArray()
    DropViewer.search(player, args.joinToString(" "))
}

on_command("drops") { DropViewer.open(player) }

/** `::dvzoom <thumb base> <thumb per tile> <head base> <head per tile>`: tunes the npc model zoom of the Drop Viewer live. */
on_command("dvzoom", Privilege.ADMIN_POWER) {
    val a = player.attr[gg.rsmod.game.model.attr.COMMAND_ARGS_ATTR]?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    if (a.size == 4) {
        DropViewer.THUMB_BASE = a[0]
        DropViewer.THUMB_PER = a[1]
        DropViewer.HEAD_BASE = a[2]
        DropViewer.HEAD_PER = a[3]
    }
    player.message("Drop Viewer zoom: thumb ${DropViewer.THUMB_BASE}+${DropViewer.THUMB_PER}/tile, head ${DropViewer.HEAD_BASE}+${DropViewer.HEAD_PER}/tile")
    if (DropViewer.isOpen(player)) DropViewer.open(player)
}
on_command("collectionlog") { CollectionLogUi.open(player) }
on_command("deadmantasks") { DeadmanTasksUi.open(player) }

// ---- Collection Log
on_button(interfaceId = CollectionLogUi.INTERFACE, component = N.C.CLOSE) {
    if (CollectionLogUi.isOpen(player)) player.closeInterface(CollectionLogUi.INTERFACE)
}
for (c in 0 until N.C.CATS) {
    on_button(interfaceId = CollectionLogUi.INTERFACE, component = N.C.cat(c) + 4) { CollectionLogUi.category(player, c) }
}
for (e in 0 until N.C.ENTRIES) {
    on_button(interfaceId = CollectionLogUi.INTERFACE, component = N.C.entry(e) + 2) { CollectionLogUi.entry(player, e) }
}
for (s in 0 until N.C.SLOTS) {
    on_button(interfaceId = CollectionLogUi.INTERFACE, component = N.C.slot(s)) { CollectionLogUi.inspect(player, s) }
    on_button(interfaceId = CollectionLogUi.INTERFACE, component = N.C.slot(s) + 1) { CollectionLogUi.inspect(player, s) }
}

// ---- live refresh and the tasks' own counters
on_timer(QuestTab.REFRESH_TIMER) {
    if (QuestTab.refresh(player)) player.timers[QuestTab.REFRESH_TIMER] = 1
}

val TASK_MINUTE = TimerKey()

on_login {
    player.timers[TASK_MINUTE] = 100
    DropSources.warmUp(player)
    // Level and emblem tasks that are already reached complete at once.
    runCatching { DeadmanTasks.check(player) }.onFailure { gg.rsmod.game.model.Trace.crash("deadman tasks login", it) }
}

on_timer(TASK_MINUTE) {
    player.timers[TASK_MINUTE] = 100
    runCatching {
        if (DeadmanTasks.skulledMinuteCounts(player)) DeadmanTasks.onSkulledMinute(player) else DeadmanTasks.check(player)
    }.onFailure { gg.rsmod.game.model.Trace.crash("deadman tasks minute", it) }
}

on_npc_killed { killer, npc ->
    runCatching { DeadmanTasks.onNpcKilled(killer, npc.def.name) }.onFailure { gg.rsmod.game.model.Trace.crash("deadman tasks kill", it) }
}

/*
 * `questtabshots`: screenshots of the quest tab for layout reviews (owner mission 2026-09-26). Opens every tab in turn and, like
 * `screenshot`, asks the RSPS client to save the game canvas to C:/RSPS/screens after each step. Ends on the tab chosen before.
 */
val SHOT_PREFIX = "__RSPS_SHOT__:"

on_command("questtabshots", Privilege.ADMIN_POWER) {
    val before = QuestTab.selected(player)
    val mode = if (player.interfaces.displayMode == DisplayMode.FIXED) "fixed" else "resizable"
    player.message("Quest tab screenshots ($mode): keep the game window visible for a few seconds.")
    player.queue {
        player.focusTab(3)
        for (tab in QuestTab.Tab.values()) {
            QuestTab.select(player, tab)
            wait(2)
            player.message("${SHOT_PREFIX}questtab_${mode}_${tab.name.lowercase()}", type = ChatMessageType.CONSOLE)
            wait(1)
        }
        QuestTab.select(player, before)
        player.message("Quest tab screenshots saved to C:/RSPS/screens.")
    }
}
