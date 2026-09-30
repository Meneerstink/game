package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.tools.importer.QuestTabInterfaceImportTool as L
import gg.rsmod.plugins.api.ext.PaneMountHooks
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.Block

/*
 * The quest tab container (QuestTab): the tab row, the Deadman Info buttons, the Info window and the live refresh. Every
 * handler checks that its interface is the one showing; the button handler itself already drops clicks on interfaces that
 * are not open.
 */

PaneMountHooks.register(QuestTab.CONTAINER) { player -> QuestTab.mount(player) }

QuestTab.Tab.values().forEach { tab ->
    on_button(interfaceId = QuestTab.CONTAINER, component = L.tabBase(tab.index)) {
        if (!QuestTab.isMounted(player)) return@on_button
        if (QuestTab.selected(player) != tab) QuestTab.select(player, tab)
    }
}

Block.values().forEachIndexed { i, block ->
    on_button(interfaceId = L.DEADMAN, component = L.blockBase(i) + L.BLOCK_INFO) {
        QuestTab.openInfo(player, block)
    }
}

// Drop Viewer (owner 2026-09-30): the Deadman panel's "Drops" button and the window's own controls.
on_button(interfaceId = L.DEADMAN, component = L.DROPS_BUTTON) {
    DropViewer.open(player)
}

on_button(interfaceId = DropViewer.INTERFACE, component = L.I_CLOSE) {
    if (DropViewer.isOpen(player)) player.closeInterface(DropViewer.INTERFACE)
}

on_button(interfaceId = DropViewer.INTERFACE, component = L.DV_PREVIOUS) {
    DropViewer.page(player, -1)
}

on_button(interfaceId = DropViewer.INTERFACE, component = L.DV_NEXT) {
    DropViewer.page(player, 1)
}

for (i in 0 until L.DV_RESULTS) {
    on_button(interfaceId = DropViewer.INTERFACE, component = L.DV_RESULT_FIRST + i) {
        DropViewer.result(player, i)
    }
}

fun dropSearch(player: gg.rsmod.game.model.entity.Player, byDrop: Boolean) {
    player.queue {
        val query = inputString(if (byDrop) "Enter the name of the item:" else "Enter the name of the NPC:")
        val found = if (byDrop) DropViewer.findDrop(player, query) else DropViewer.findNpc(player, query)
        if (!DropViewer.isOpen(player)) DropViewer.open(player)
        DropViewer.showResults(player, found)
        when {
            found.isEmpty() -> player.message(if (byDrop) "No monster drops an item called '$query'." else "No monster with a drop table is called '$query'.")
            else -> {
                DropViewer.show(player, found.first(), 0)
                if (found.size > L.DV_RESULTS) player.message("${found.size} matches; the first ${L.DV_RESULTS} are listed.")
            }
        }
    }
}

on_button(interfaceId = DropViewer.INTERFACE, component = L.DV_FIND_DROP) {
    dropSearch(player, byDrop = true)
}

on_button(interfaceId = DropViewer.INTERFACE, component = L.DV_FIND_NPC) {
    dropSearch(player, byDrop = false)
}

on_command("drops") {
    DropViewer.open(player)
}

on_button(interfaceId = QuestTab.INFO, component = L.I_CLOSE) {
    if (player.interfaces.isVisible(QuestTab.INFO)) player.closeInterface(QuestTab.INFO)
}

on_timer(QuestTab.REFRESH_TIMER) {
    val tab = QuestTab.selected(player)
    if ((tab == QuestTab.Tab.DEADMAN || tab == QuestTab.Tab.GROUPS) && QuestTab.refresh(player)) {
        player.timers[QuestTab.REFRESH_TIMER] = 1
    }
}

/*
 * `questtabshots`: screenshots of the quest tab for layout reviews (owner mission 2026-09-26). Opens every tab and every Info
 * window in turn and, like `screenshot`, asks the RSPS client to save the game canvas to C:/RSPS/screens after each step.
 * Ends on the tab that was chosen before.
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
        QuestTab.select(player, QuestTab.Tab.DEADMAN)
        wait(1)
        Block.values().forEach { block ->
            QuestTab.openInfo(player, block)
            wait(2)
            player.message("${SHOT_PREFIX}questtab_${mode}_info_${block.name.lowercase()}", type = ChatMessageType.CONSOLE)
            wait(1)
            // The player may have closed it during the wait already (closing twice logged a warning).
            if (player.interfaces.isVisible(QuestTab.INFO)) player.closeInterface(QuestTab.INFO)
        }
        QuestTab.select(player, before)
        player.message("Quest tab screenshots saved to C:/RSPS/screens.")
    }
}
