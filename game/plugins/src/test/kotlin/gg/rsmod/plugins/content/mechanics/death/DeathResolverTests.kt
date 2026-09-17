package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.GameContext
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M1 PvP/PvM death policy: proves [DeathResolver.resolveContext] follows the
 * actual credited cause rather than the victim's Wilderness coordinates.
 *
 * `skulled` is deliberately left at its default (`victim.hasSkullIcon(SkullIcon.RED)`)
 * in every test here, which currently always evaluates to `false`: nothing in
 * this codebase yet grants [gg.rsmod.plugins.api.SkullIcon.RED] on attack.
 * `Player.skull(icon, durationCycles)` and its removal timer
 * (`SKULL_ICON_DURATION_TIMER`) already exist end-to-end, but *when* and *for
 * how long* a Wilderness attacker should skull is exactly the "detailed
 * Protect Item/skull interactions" PROJECT_PLAN.md §11/§23 lists as an open,
 * undecided product question - not something to invent here. See the
 * milestone final report for this deferral.
 */
class DeathResolverTests {
    @Test
    fun `pvp aggressor attribution is cleared by the player death lifecycle`() {
        assertTrue(PVP_AGGRESSOR_ATTR.resetOnDeath)
    }

    @Test
    fun `a player-caused death outside the wilderness resolves as PvP loot`() {
        val home = Tile(3140, 3640, 0)
        val victim = newPlayer(tile = Tile(3200, 3200), home = home)
        val killer = mockk<Player>(relaxed = true)
        // Four distinct stacks so the unskulled keep-3 cap is actually
        // exercised (protectedItemCount is capped by available stacks, not
        // just the allowance, so a single-stack fixture can't tell them apart).
        victim.inventory[0] = Item(995, 1)
        victim.inventory[1] = Item(996, 1)
        victim.inventory[2] = Item(997, 1)
        victim.inventory[3] = Item(998, 1)

        val result = DeathResolver.resolve(victim, killer, valueProvider = { _ -> 1L })

        assertEquals(DeathContext.WILDERNESS_PVP, result.context)
        assertEquals(3, result.itemRisk.protectedItemCount, "unskulled Wilderness deaths keep the 3 most valuable stacks")
        assertEquals(1, result.itemRisk.lost.size, "the 4th, lowest-ranked stack must be lost")
    }

    @Test
    fun `a monster-caused death in the wilderness resolves as PvM recovery`() {
        val home = Tile(3140, 3640, 0)
        val victim = newPlayer(tile = Tile(3040, 3528), home = home)

        val result = DeathResolver.resolve(victim, killer = null, valueProvider = { _ -> 1L })

        assertEquals(DeathContext.PVM_SAFE, result.context)
    }

    @Test
    fun `a recent player aggressor keeps an NPC last hit in PvP loot context`() {
        val home = Tile(3140, 3640, 0)
        val victim = newPlayer(tile = Tile(3040, 3528), home = home)
        val aggressor = mockk<Player>(relaxed = true)
        every { victim.timers.has(PVP_AGGRESSOR_WINDOW_TIMER) } returns true
        victim.attr[PVP_AGGRESSOR_ATTR] = WeakReference(aggressor)

        val result = DeathResolver.resolve(victim, killer = null, valueProvider = { _ -> 1L })

        assertEquals(DeathContext.WILDERNESS_PVP, result.context)
    }

    @Test
    fun `a death inside the safe home hub resolves as a PvM safe death even at wilderness coordinates`() {
        val home = Tile(3140, 3640, 0)
        val victim = newPlayer(tile = home, home = home)

        val result = DeathResolver.resolve(victim, killer = null, valueProvider = { _ -> 1L })

        assertEquals(DeathContext.PVM_SAFE, result.context)
    }

    private fun newPlayer(
        tile: Tile,
        home: Tile,
    ): Player {
        val gameContext = mockk<GameContext>(relaxed = true)
        every { gameContext.home } returns home

        val world = mockk<World>(relaxed = true)
        every { world.gameContext } returns gameContext

        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.tile } returns tile
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        return player
    }

    companion object {
        private val DEFINITIONS = gg.rsmod.game.fs.DefinitionSet()
    }
}
