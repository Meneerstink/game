package gg.rsmod.plugins.content.magic.lunar

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.N72Fixture
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.skills.farming.constants.CompostState
import gg.rsmod.plugins.content.skills.farming.data.Patch
import gg.rsmod.plugins.content.skills.farming.data.Seed
import io.mockk.clearMocks
import io.mockk.verify
import kotlin.test.*

class GeomancyTests {
    @Test fun `cast charges exact runes and60XP once and repeated open is free`() {
        N72Fixture(Spellbook.LUNAR).use { f ->
            f.runes(SpellbookData.GEOMANCY, 2)
            assertTrue(Geomancy.cast(f.player))
            assertTrue(f.player.isInterfaceVisible(Geomancy.INTERFACE))
            SpellbookData.GEOMANCY.runes.forEach { assertEquals(it.amount, f.inventory.getItemCount(it.id)) }
            assertFalse(Geomancy.cast(f.player))
            verify(exactly = 1) { f.player.addXp(Skills.MAGIC, 60.0) }
            verify { f.player.animate(Geomancy.ANIMATION) }
        }
    }

    @Test fun `wrong book level or runes refuses before payment or window`() {
        for (failure in 0..2) N72Fixture(if (failure == 0) Spellbook.STANDARD else Spellbook.LUNAR).use { f ->
            f.runes(SpellbookData.GEOMANCY)
            if (failure == 1) f.skills.setCurrentLevel(Skills.MAGIC, 64)
            if (failure == 2) f.inventory.remove(SpellbookData.GEOMANCY.runes.first())
            val before = f.inventory.toMap()
            assertFalse(Geomancy.cast(f.player))
            assertEquals(before, f.inventory.toMap())
            assertFalse(f.player.isInterfaceVisible(Geomancy.INTERFACE))
            verify(exactly = 0) { f.player.addXp(any(), any<Double>()) }
        }
    }

    @Test fun `live overview reads own real farming states and never changes the patch`() {
        N72Fixture(Spellbook.LUNAR).use { a -> N72Fixture(Spellbook.LUNAR).use { b ->
            val patch = Patch.FaladorHerb
            val state = a.player.farmingManager().getPatchManager(patch).state
            a.varps.setState(patch.id, 3)
            state.compost(CompostState.SuperCompost)
            state.plantSeed(Seed.Guam)
            state.protect()
            val before = listOf(state.seed, state.growthStage, state.compostState, state.livesLeft)
            val view = Geomancy.view(state)
            assertEquals("Guam", view.crop)
            assertTrue(view.protectedFromDisease)
            assertEquals("Supercompost", view.compost)
            assertTrue(view.yield >= 3)
            assertEquals(before, listOf(state.seed, state.growthStage, state.compostState, state.livesLeft))
            assertTrue(Geomancy.cards(a.player, 1).first().contains("Guam"))
            assertFalse(Geomancy.cards(b.player, 1).first().contains("Guam"))
            state.disease()
            assertEquals("Diseased", Geomancy.view(state).status)
            state.die()
            assertEquals("Dead", Geomancy.view(state).status)
            assertEquals(0, Geomancy.view(state).yield)
        } }
    }

    @Test fun `refresh and tab changes cost nothing and do not resend unchanged card data`() {
        N72Fixture(Spellbook.LUNAR).use { f ->
            f.runes(SpellbookData.GEOMANCY)
            assertTrue(Geomancy.cast(f.player))
            clearMocks(f.player, answers = false, recordedCalls = true)
            Geomancy.refresh(f.player)
            verify(exactly = 0) { f.player.write(any()) }
            Geomancy.select(f.player, 1)
            assertEquals(0, f.inventory.getItemCount(SpellbookData.GEOMANCY.runes.first().id))
            verify(exactly = 0) { f.player.addXp(any(), any<Double>()) }
            assertTrue(Geomancy.cards(f.player, 5).any { it.contains("compost bin") })
        }
    }
}
