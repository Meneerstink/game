package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.hud.BossHpHud
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

/**
 * N606: the OSRS boss HP HUD (interface 303) per ToA encounter, against the Kaoz Entry guide (screens/N606/hpbar-osrs/strips-*.png):
 * Kephri's shield cyan down to 0 then her final health green/red; a P2 Warden's damage bar yellow; the exposed core's real health
 * green/red; the HUD keeps the empty bar a few ticks after the boss died, then goes.
 */
class ToaHpHudTests {
    @Test fun `Kephri shows her shield in the shield colour and her final health in the standard colour`() {
        val f = ToaTestFixture()
        val s = KephriCombat.Session(f.npc, ToaTestContext())
        val bar = assertNotNull(s.hpHud())
        assertEquals(BossHpHud.Style.SHIELD, bar.style)
        assertEquals(s.shield, bar.hp)
        assertEquals(bar.max, s.shield, "a fresh shield is full")
        s.shield = 0
        assertEquals(0, s.hpHud()!!.hp, "the recharge shows 0 / max like OSRS")
        s.phase = KephriCombat.Phase.FINAL
        every { f.npc.getCurrentLifepoints() } returns 77
        every { f.npc.getMaximumLifepoints() } returns 130
        val final = s.hpHud()!!
        assertEquals(BossHpHud.Style.STANDARD, final.style)
        assertEquals(77 to 130, final.hp to final.max)
    }

    @Test fun `Wardens P2 shows the damage bar in yellow and the exposed core the Warden's real health`() {
        val f = ToaTestFixture()
        val s = WardensCombat.Session(f.npc, ToaTestContext())
        assertEquals(BossHpHud.Style.STANDARD, s.hpHud()!!.style, "the Obelisk is green/red")
        s.phase = WardensCombat.Phase.P2
        every { f.npc.getCurrentLifepoints() } returns 106
        every { f.npc.getMaximumLifepoints() } returns 225
        val p2 = s.hpHud()!!
        assertEquals(BossHpHud.Style.DAMAGE, p2.style)
        assertEquals(106 to 225, p2.hp to p2.max)
        s.phase = WardensCombat.Phase.CORE
        s.coreHealth = 60
        val core = s.hpHud()!!
        assertEquals(BossHpHud.Style.STANDARD, core.style)
        assertEquals(60 to s.coreMaximum, core.hp to core.max)
    }

    @Test fun `the empty bar stays a few ticks after the boss died then the HUD closes`() {
        val f = ToaTestFixture()
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        var cycle = 100
        every { player.world } returns f.world
        every { f.world.currentCycle } answers { cycle }
        val npc: Npc = f.npc
        every { npc.index } returns 42
        var bar: BossHpHud.Bar? = BossHpHud.Bar(npc, 5, 130, name = "Kephri")
        val provider: (Player) -> BossHpHud.Bar? = { bar }
        BossHpHud.providers += provider
        try {
            assertEquals("hpbar|42|0|5|130|Kephri", BossHpHud.payload(player))
            bar = null
            every { npc.isDead() } returns true
            cycle += 1
            assertEquals("hpbar|42|0|0|130|Kephri", BossHpHud.payload(player), "OSRS keeps 'Kephri 0 / 130' after the kill")
            cycle += BossHpHud.DEATH_HOLD_TICKS
            assertEquals("hpbar|none", BossHpHud.payload(player))
            // Leaving the room while the boss lives closes it at once.
            bar = BossHpHud.Bar(npc, 5, 130, name = "Kephri"); every { npc.isDead() } returns false
            BossHpHud.payload(player); bar = null; cycle += 1
            assertEquals("hpbar|none", BossHpHud.payload(player))
        } finally {
            BossHpHud.providers -= provider
        }
    }

    @Test fun `names lose their colour tags like OSRS param 510`() {
        val f = ToaTestFixture()
        every { f.npc.getTransmogId() } returns -1
        every { f.npc.id } returns 15174
        every { f.world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, 15174) } returns
            gg.rsmod.game.fs.def.NpcDef(15174).apply { name = "<col=00ffff>Obelisk</col>" }
        assertEquals("Obelisk", BossHpHud.nameOf(f.npc))
    }
}
