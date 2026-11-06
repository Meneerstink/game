package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * N462: OSRS toa_raid_summary (482) "Raid Summary" after the Wardens - room times per boss and path, challenge and overall totals, raid
 * level and the number of active invocations. OSRS sets these texts from the server (no clientscript writes them; 6586 only draws the
 * Paths/Bosses tabs). Time text mm:ss like the HUD timer - ONZEKER: the exact OSRS summary time format (centiseconds?) is not in the
 * sources (N462 80-vragen T23 summary). Component ids: OSRS 482:0..76 (symbols toa_raid_summary:*); steelborder of 482:1 follows.
 */
object ToaSummaryUi {
    const val WARDENS = 9
    const val BABA = 11
    const val ZEBAK = 14
    const val AKKHA = 16
    const val KEPHRI = 18
    const val CRONDIS = 25
    const val APMEKEN = 28
    const val HET = 30
    const val SCABARAS = 72
    const val RAID_LEVEL = 58
    const val ACTIVE = 59
    const val CHALLENGE_TOTAL = 66
    const val OVERALL_TOTAL = 67

    class Times(
        val kephri: Int?, val akkha: Int?, val baba: Int?, val zebak: Int?, val wardens: Int?,
        val scabaras: Int?, val het: Int?, val apmeken: Int?, val crondis: Int?, val challengeTotal: Int?, val overall: Int?,
    )

    fun time(ticks: Int?): String {
        if (ticks == null) return "-"
        val seconds = ticks * 3 / 5
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    fun open(player: Player, times: Times, raidLevel: Int, activeInvocations: Int) {
        val iface = ToaInterfaces.of(ToaInterfaces.SUMMARY) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        mapOf(KEPHRI to times.kephri, AKKHA to times.akkha, BABA to times.baba, ZEBAK to times.zebak, WARDENS to times.wardens,
            SCABARAS to times.scabaras, HET to times.het, APMEKEN to times.apmeken, CRONDIS to times.crondis,
            CHALLENGE_TOTAL to times.challengeTotal, OVERALL_TOTAL to times.overall,
        ).forEach { (c, t) -> player.setComponentText(iface, c, time(t)) }
        player.setComponentText(iface, RAID_LEVEL, raidLevel.toString())
        player.setComponentText(iface, ACTIVE, activeInvocations.toString())
        closeComponent?.let { player.setInterfaceEvents(iface, it, -1..-1, 0x2) }
    }

    /** The steelborder close button: OSRS 482 has 77 components; the port tool adds 482:1's steelborder first (77..88, title 78, close 88),
     *  the thinboxes of 5/8/24/32/37/57/62/65/70 after it - checked against 94-toa-interfaces-claude.tsv after the import. */
    val closeComponent: Int? get() = 88

    fun close(player: Player) { ToaInterfaces.of(ToaInterfaces.SUMMARY)?.let { player.closeInterface(it) } }
}
