package gg.rsmod.plugins.content.magic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N43 (LC16 2026-10-16): a spell teleport from a Safe zone to a Dangerous spot never opened the Dangerous-area warning (Pawn.teleport
 * ends in teleportTo, which the MoveGate never sees). The spell now asks first - before the runes are used - and casts on "Enter".
 */
class SpellTeleportDangerWarningTests {
    @Test
    fun `a teleport spell asks before it uses runes`() {
        val s = File("src/main/kotlin/gg/rsmod/plugins/content/magic/teleports/teleport_spells.plugin.kts").readText()
        val body = s.substringAfter("fun Player.teleport(\n    type: TeleportType,")
        val ask = body.indexOf("warning.needsWarning(this, tile, endTile)")
        val runes = body.indexOf("MagicSpells.removeRunes(")
        assertTrue(ask in 0 until runes, "the warning comes before the runes")
        assertTrue("warning.allowNextCrossing(self)" in body && "self.teleport(type, endTile, xp, data)" in body, "Enter casts the spell")
    }
}
