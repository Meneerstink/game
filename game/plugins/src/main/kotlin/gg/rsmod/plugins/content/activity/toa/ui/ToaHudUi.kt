package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.isInterfaceVisible
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentPosition
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * N462: the OSRS ToA encounter HUD (toa_hud 481) - party status column, "Level: N", raid timer and path levels - driven by the server
 * the way CS2 toa_hud_draw / toa_hud_statusrefresh (6579 / 6583) draw it from the %toa_client_* vars:
 *  - status 0: slot hidden; 30: dead (red name, empty filler); 31: full (orb_filler,16, white); otherwise filler height min(26, status - 1)
 *    with orb_filler,15 (orb_filler,17 for the primary); the name is the first letter of the member, "Me" for the player himself.
 *  - "Level: <raid level>" (481:42), timer mm:ss (481:46), path level of the current path (481:45) / every path at the Nexus (49/51/53/55).
 * 667 has no server IF_SETSIZE: the filler graphic (26x26) slides down by 26 - height inside its clipping 26x26 layer.
 * Component ids: OSRS 481:0..55; per slot i the port tool added text 56 + 2i and filler 57 + 2i.
 */
object ToaHudUi {
    const val LEVEL = 42
    const val PATH_LEVELS = 43
    const val PATH_LEVEL = 45
    const val TIMER = 46
    const val NEXUS_LEVELS = 47
    val NEXUS_LEVEL = intArrayOf(49, 51, 53, 55) // scabaras, akkha/het, apmeken, crondis (481 symbols: nexus_*_level)
    private const val SLOT_TEXT = 56
    private const val ORB_FILLER = 3574
    private const val ORB_FILLER_FULL = 3575
    private const val ORB_FILLER_PRIMARY = 3576

    class Slot(val name: String, val status: Int, val primary: Boolean = false)

    fun open(player: Player) {
        val iface = ToaInterfaces.of(ToaInterfaces.HUD) ?: return
        // 07-11 testclient: the raid redraws every tick (timer) - reopening the overlay each time closed+opened it every tick
        // (client freezes, U5 packet spam). Open once; later draws only set texts.
        if (player.isInterfaceVisible(iface)) return
        player.openInterface(iface, InterfaceDestination.PVP_OVERLAY)
    }

    fun close(player: Player) {
        ToaInterfaces.of(ToaInterfaces.HUD)?.let { player.closeInterface(it) }
    }

    /** [slots] in party order (index = party slot), [self] = this player's slot. */
    fun drawParty(player: Player, slots: List<Slot?>, self: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.HUD) ?: return
        for (i in 0 until 8) {
            val slot = slots.getOrNull(i)
            val container = 6 + 4 * i
            if (slot == null || slot.status == 0) { player.setComponentHidden(iface, container, true); continue }
            player.setComponentHidden(iface, container, false)
            val dead = slot.status == 30
            val label = if (i == self) "Me" else (slot.name.firstOrNull()?.toString() ?: "-")
            player.setComponentText(iface, SLOT_TEXT + 2 * i, if (dead) "<col=ff0000>$label</col>" else label)
            val filler = SLOT_TEXT + 2 * i + 1
            val sprite = when { slot.status == 31 -> ORB_FILLER_FULL; slot.primary -> ORB_FILLER_PRIMARY; else -> ORB_FILLER }
            ToaInterfaces.sprite(sprite)?.let { player.setComponentSprite(iface, filler, it) }
            val height = when (slot.status) { 31 -> 26; 30 -> 0; else -> minOf(26, slot.status - 1) }
            player.setComponentPosition(iface, filler, 0, 26 - height)
        }
    }

    fun drawRaid(player: Player, raidLevel: Int, elapsedTicks: Int, currentPath: Int, pathLevels: IntArray) {
        val iface = ToaInterfaces.of(ToaInterfaces.HUD) ?: return
        player.setComponentText(iface, LEVEL, "Level: $raidLevel")
        val seconds = elapsedTicks * 3 / 5
        player.setComponentText(iface, TIMER, "%02d:%02d".format(seconds / 60, seconds % 60))
        val inNexus = currentPath !in 1..4
        player.setComponentHidden(iface, NEXUS_LEVELS, !inNexus)
        player.setComponentHidden(iface, PATH_LEVELS, inNexus)
        if (inNexus) NEXUS_LEVEL.forEachIndexed { p, c -> player.setComponentText(iface, c, (pathLevels.getOrNull(p) ?: 0).toString()) }
        else player.setComponentText(iface, PATH_LEVEL, (pathLevels.getOrNull(currentPath - 1) ?: 0).toString())
    }
}
