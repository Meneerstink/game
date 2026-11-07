package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.activity.toa.ToaRecordStore
import gg.rsmod.plugins.content.activity.toa.core.ToaMode

/**
 * Cache2686 3/775, scripts 6771-6774: 85 Entry, 86 Normal, 87 Expert; 55+4r global overall (r=0..7).
 * Ported group 1192 keeps component ids. Steelborder 775:2 adds 88..99, close 99; thinbox 100..101.
 * Owner adaptation: the Personal columns show record holders instead of PBs. View on a time opens every holder's name.
 * CACHE FOLLOW-UP for Claude: time components 55/59/63/67/71/75/79/83 need op 1 "View"; mode tabs need static text
 * children (OSRS draws them with 3723), and 775:10/11 need scrolling (6771 uses scrollbar script31). No cache write here.
 */
object ToaScoreboardUi {
    const val CLOSE = 99
    private val MODE = AttributeKey<ToaMode>()
    val TABS = mapOf(85 to ToaMode.ENTRY, 86 to ToaMode.NORMAL, 87 to ToaMode.EXPERT)

    fun overallComponent(size: Int): Int {
        require(size in 1..8)
        return 55 + 4 * (size - 1)
    }

    fun time(ticks: Int?): String {
        if (ticks == null) return "-"
        val centiseconds = ticks.toLong() * 60
        return "%d:%02d.%02d".format(java.util.Locale.ROOT, centiseconds / 6000, centiseconds / 100 % 60, centiseconds % 100)
    }

    fun open(player: Player, mode: ToaMode = ToaMode.NORMAL) {
        val iface = ToaInterfaces.of(ToaInterfaces.STATISTICS) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(iface, CLOSE, -1..-1, 0x2)
        TABS.keys.forEach { player.setInterfaceEvents(iface, it, -1..-1, 0x2) }
        draw(player, mode)
    }

    private fun draw(player: Player, mode: ToaMode) {
        val iface = ToaInterfaces.of(ToaInterfaces.STATISTICS) ?: return
        player.attr[MODE] = mode
        player.setComponentText(iface, 89, "Tombs of Amascut Statistics - ${mode.label}")
        player.setComponentText(iface, 30, "Record holders")
        player.setComponentText(iface, 29, "Server records")
        // Counts/PBs and challenge records are outside the owner's server-overall-records decision.
        (listOf(31, 32, 33, 35, 36, 38) + (47..52)).forEach { player.setComponentHidden(iface, it, true) }
        for (size in 1..8) {
            val c = overallComponent(size)
            val record = ToaRecordStore.server.get(mode, size)
            player.setComponentText(iface, c, time(record?.ticks))
            player.setInterfaceEvents(iface, c, -1..-1, if (record == null) 0 else 0x2)
            // Two names fit the native two 78px columns. View on the time shows the whole original roster, including offline members.
            player.setComponentText(iface, c - 1, record?.names?.getOrNull(0).orEmpty())
            player.setComponentText(iface, c - 2, record?.names?.getOrNull(1).orEmpty())
            player.setComponentText(iface, c + 1, "")
        }
    }

    /** Returns a record for the names dialogue, or consumes a tab/close action. No client supplied size/mode is trusted. */
    fun click(player: Player, component: Int): ToaRecordStore.Record? {
        val iface = ToaInterfaces.of(ToaInterfaces.STATISTICS) ?: return null
        if (!player.interfaces.isVisible(iface)) return null
        if (component == CLOSE) { player.attr.remove(MODE); player.closeInterface(iface); return null }
        TABS[component]?.let { draw(player, it); return null }
        val size = (1..8).firstOrNull { overallComponent(it) == component } ?: return null
        return ToaRecordStore.server.get(player.attr[MODE] ?: return null, size)
    }
}
