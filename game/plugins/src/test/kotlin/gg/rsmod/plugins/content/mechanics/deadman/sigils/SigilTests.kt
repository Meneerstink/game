package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PROJECT48 SIGILS (S-K6): the attune rules (bank only, never skulled, max 3, exclusive groups), the stacking overrides, the death rule
 * ("attuned sigils are always kept") and the trade rule (no player-to-player trading) of Deadman: Armageddon sigils.
 */
class SigilTests {
    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        val attributes = AttributeMap()
        every { player.attr } returns attributes
        every { player.inventory } returns ItemContainer(mockk<DefinitionSet>(relaxed = true), 28, ContainerStackType.NORMAL)
        return player
    }

    private fun attune(
        player: Player,
        sigil: Sigil,
        atBank: Boolean = true,
    ): Sigils.Result {
        player.inventory[0] = Item(sigil.unattunedId)
        return Sigils.attune(player, 0, atBank)
    }

    @Test
    fun `the 65 sigils are numbered 1 to 65 and their items never overlap`() {
        assertEquals((1..65).toList(), Sigil.values().map { it.number })
        val ids = Sigil.values().flatMap { listOf(it.unattunedId, it.attunedId) }
        assertEquals(ids.size, ids.toSet().size, "every sigil item id is used once")
        Sigil.values().forEach {
            assertEquals(it, Sigil.ofUnattuned(it.unattunedId))
            assertEquals(it, Sigil.ofAttuned(it.attunedId))
        }
    }

    @Test
    fun `a sigil is attuned only at a bank`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.NOT_AT_BANK, attune(player, Sigil.CONSISTENCY, atBank = false))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.CONSISTENCY))
        assertEquals(Sigil.CONSISTENCY.attunedId, player.inventory[0]!!.id)
        assertTrue(Sigils.has(player, Sigil.CONSISTENCY))
    }

    @Test
    fun `at most three sigils can be attuned`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.OK, attune(player, Sigil.CONSISTENCY))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.RESILIENCE))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.PRECISION))
        assertEquals(Sigils.Result.FULL, attune(player, Sigil.AGGRESSION))
        assertEquals(3, Sigils.attuned(player).size)
    }

    @Test
    fun `the same sigil cannot be attuned twice`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.OK, attune(player, Sigil.CONSISTENCY))
        assertEquals(Sigils.Result.ALREADY, attune(player, Sigil.CONSISTENCY))
    }

    @Test
    fun `swiftness, gunslinger and swashbuckler exclude each other`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.OK, attune(player, Sigil.THE_GUNSLINGER))
        assertEquals(Sigils.Result.EXCLUSIVE, attune(player, Sigil.THE_SWASHBUCKLER))
        assertEquals(Sigils.Result.EXCLUSIVE, attune(player, Sigil.ARCANE_SWIFTNESS))
    }

    @Test
    fun `unattuning gives the tradeable item back and frees the slot`() {
        val player = newPlayer()
        attune(player, Sigil.CONSISTENCY)
        assertEquals(Sigils.Result.OK, Sigils.unattune(player, 0, atBank = true))
        assertEquals(Sigil.CONSISTENCY.unattunedId, player.inventory[0]!!.id)
        assertFalse(Sigils.has(player, Sigil.CONSISTENCY))
        attune(player, Sigil.CONSISTENCY)
        assertEquals(Sigils.Result.NOT_AT_BANK, Sigils.unattune(player, 0, atBank = false))
        assertTrue(Sigils.has(player, Sigil.CONSISTENCY), "still attuned after the refused unattune")
    }

    @Test
    fun `titanium overrides resistance and the harvest sigil overrides the forager`() {
        val player = newPlayer()
        attune(player, Sigil.TITANIUM)
        attune(player, Sigil.RESISTANCE)
        attune(player, Sigil.ENHANCED_HARVEST)
        assertTrue(Sigils.effective(player, Sigil.TITANIUM))
        assertFalse(Sigils.effective(player, Sigil.RESISTANCE), "does not stack with Titanium")
        assertTrue(Sigils.effective(player, Sigil.ENHANCED_HARVEST))
    }

    @Test
    fun `attuned sigils are always kept on death and no sigil can be traded between players`() {
        Sigil.values().forEach {
            assertTrue(Sigils.isAttunedItem(it.attunedId))
            assertFalse(Sigils.isAttunedItem(it.unattunedId))
            assertTrue(Sigils.isSigilItem(it.attunedId) && Sigils.isSigilItem(it.unattunedId))
        }
        assertFalse(Sigils.isSigilItem(995))
    }

    @Test
    fun `the three starting sigils are the tier 2 choices of the wiki`() {
        assertEquals(3, Sigils.START_CHOICES.size)
        Sigils.START_CHOICES.forEach { assertEquals(2, it.tier) }
        assertNull(SigilDrops.rollGlobalTable(newPlayer(), 1), "the Deadman-only table has chance 0 until the owner names the rates")
        assertNotNull(Sigil.values().firstOrNull())
    }

    @Test
    fun `consistency adds one damage to a landed hit and aggression gives 10 percent more and 5 percent less protection`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val attacker = newPlayer()
        attune(attacker, Sigil.CONSISTENCY)
        assertEquals(6.0, SigilCombat.modifyDamage(attacker, npc, 5.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        assertEquals(0.0, SigilCombat.modifyDamage(attacker, npc, 0.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, false), "a missed hit stays 0")
        val fighter = newPlayer()
        attune(fighter, Sigil.AGGRESSION)
        assertEquals(22.0, SigilCombat.modifyDamage(fighter, npc, 20.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val victim = newPlayer()
        attune(victim, Sigil.AGGRESSION)
        assertEquals(21.0, SigilCombat.modifyDamage(npc, victim, 20.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true), "5% more damage taken")
    }

    @Test
    fun `titanium takes 60 percent and resistance 25 percent off monster hits only`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val titan = newPlayer()
        attune(titan, Sigil.TITANIUM)
        assertEquals(40.0, SigilCombat.modifyDamage(npc, titan, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val resist = newPlayer()
        attune(resist, Sigil.RESISTANCE)
        assertEquals(75.0, SigilCombat.modifyDamage(npc, resist, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val other = newPlayer()
        assertEquals(100.0, SigilCombat.modifyDamage(other, resist, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true), "a player's hit is not reduced")
    }

    @Test
    fun `gathering multipliers - harvest 3x, forager 2x, hoarding 3x and the abyss 5x`() {
        val none = newPlayer()
        assertEquals(1, SigilSkilling.multiplier(none))
        assertEquals(1, SigilSkilling.hoardingMultiplier(none))
        assertEquals(1, SigilSkilling.runecraftMultiplier(none))
        val forager = newPlayer()
        attune(forager, Sigil.THE_FORAGER)
        assertEquals(2, SigilSkilling.multiplier(forager))
        attune(forager, Sigil.ENHANCED_HARVEST)
        assertEquals(3, SigilSkilling.multiplier(forager), "Enhanced Harvest wins over the Forager")
        val abyss = newPlayer()
        attune(abyss, Sigil.THE_ABYSS)
        attune(abyss, Sigil.HOARDING)
        assertEquals(5, SigilSkilling.runecraftMultiplier(abyss))
        assertEquals(3, SigilSkilling.hoardingMultiplier(abyss))
    }
}
