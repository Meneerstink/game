package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.isInterfaceVisible
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * The CoX points overlay (OSRS 513 raids_overlay; tsps 'The points overlay (513) is on the HUD: varbit 5431 the total and varp 4609
 * your points'). Lines word for word from osrs-dumps [proc,raids_overlay_databox] 1511: labels "Total:" / "<name>:" / "Time:", values
 * the team total and your points with ',' thousands separators ("<col=ffa82f>Lots!</col>" from 2,000,000,000) and the raid time.
 * Rebuilt components (OsrsInterfacePortTool group 513): 7 labels, 8 values; the dodger 513:2 moves 23 px down like the ToA HUD
 * ([proc,interface_dodger_init](dodger, data, 65256, left, top) - bit 10 = below the mouse-over text row on desktop).
 * Opened once (re-opening every tick froze the client in ToA, 07-11); texts are sent only when they change (U5).
 */
object CoxPointsOverlay {
    private const val DODGER = 2
    private const val DODGER_MOUSEOVER_OFFSET = 23
    private const val LABELS = 7
    private const val VALUES = 8
    private val LAST = AttributeKey<String>()

    fun open(player: Player) {
        val iface = CoxInterfaces.of(CoxInterfaces.POINTS) ?: return
        if (player.isInterfaceVisible(iface)) return
        player.openInterface(iface, InterfaceDestination.PVP_OVERLAY)
        player.setComponentPosition(iface, DODGER, 0, DODGER_MOUSEOVER_OFFSET)
        player.setComponentText(iface, LABELS, "Total:<br>${player.username}:<br>Time:")
        player.attr.remove(LAST)
    }

    fun close(player: Player) {
        CoxInterfaces.of(CoxInterfaces.POINTS)?.let { player.closeInterface(it) }
        player.attr.remove(LAST)
    }

    fun spaced(n: Int): String = "%,d".format(n)

    fun values(total: Int, mine: Int, time: String): String =
        "${spaced(total)}<br>${if (mine < 2_000_000_000) spaced(mine) else "<col=ffa82f>Lots!</col>"}<br>$time"

    fun update(player: Player, raid: CoxRaid) {
        val iface = CoxInterfaces.of(CoxInterfaces.POINTS) ?: return
        val text = values(raid.totalPoints(), raid.points[player.username.lowercase()] ?: 0, CoxRaids.duration(raid))
        if (player.attr[LAST] == text) return
        player.attr[LAST] = text
        player.setComponentText(iface, VALUES, text)
    }
}
