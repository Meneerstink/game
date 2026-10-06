package gg.rsmod.plugins.content.npcs

import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.Brother
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Owner bugs 2026-10-06 (162809 door brothers, 162933 Ahrim): Darkan's Barrows rules for the whole set. */
class BarrowsOwnerFixesTests {
    private fun player(hidden: Brother, vararg killed: Brother): Player {
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        every { player.attr } returns attrs
        attrs[Barrows.SELECTED_BROTHER] = hidden.key
        killed.forEach { attrs[it.killedAttr] = true }
        return player
    }

    private fun reachable(player: Player, collapsing: Boolean): Set<Brother> =
        (0 until Brother.values().size).mapNotNull { Barrows.randomDoorBrother(player, collapsing, it) }.toSet()

    @Test
    fun `a tunnel door can summon every remaining brother at random, the hidden one too`() {
        val p = player(Brother.AHRIM, Brother.DHAROK)
        // Owner 2026-10-06: the hidden ("last") brother can also come from a door, before and after looting.
        assertEquals(Brother.values().toSet() - Brother.DHAROK, reachable(p, collapsing = false))
        assertEquals(Brother.values().toSet() - Brother.DHAROK, reachable(p, collapsing = true))
        // Not always the first unkilled brother in enum order (the old rule): different rolls give different brothers.
        assertTrue(reachable(p, collapsing = false).size > 1)
        assertNull(Barrows.randomDoorBrother(player(Brother.AHRIM, *Brother.values()), collapsing = true, 0), "all killed: no brother")
    }

    @Test
    fun `the collapse shake runs only inside the tunnels and stops on leaving them`() {
        val p = player(Brother.AHRIM)
        Barrows.collapseShake(p, inTunnels = true)
        assertNull(p.attr[Barrows.SHAKING], "no collapse yet: no shake")
        p.attr[Barrows.COLLAPSE_TICKS] = 0
        Barrows.collapseShake(p, inTunnels = true)
        assertEquals(true, p.attr[Barrows.SHAKING], "collapsing in the tunnels: shaking")
        Barrows.collapseShake(p, inTunnels = false)
        assertNull(p.attr[Barrows.SHAKING], "climbed into a crypt / out: shake stopped (owner 2026-10-06)")
        Barrows.collapseShake(p, inTunnels = true)
        assertEquals(true, p.attr[Barrows.SHAKING], "back in while it still collapses: shaking again")
    }

    @Test
    fun `Ahrim casts Fire Wave on every attack with Darkan's cast gfx, projectile and a one in three Strength drain`() {
        val ahrim = ObjectMapper().readTree(File("../../data/cfg/npcs/npc-attacks.json")).first { it["id"].asInt() == 2025 }
        val selectable = ahrim["attacks"].filter { it["chance"].asInt() > 0 }
        assertEquals(listOf("magic"), selectable.map { it["id"].asText() }, "only Fire Wave can be picked")
        val magic = selectable.single()
        assertEquals(14223, magic["anim"].asInt())
        assertEquals(listOf(2741), magic["gfx"].map { it["id"].asInt() })
        assertEquals(listOf(2735), magic["projectiles"].map { it["id"].asInt() })
        val drain = magic["drains"].single()
        assertEquals("strength", drain["skill"].asText())
        assertEquals(5, drain["min"].asInt())
        assertEquals(3, drain["chance"].asInt())
        assertEquals(400, drain["gfx"].asInt())
    }
}
