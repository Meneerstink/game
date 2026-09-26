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
