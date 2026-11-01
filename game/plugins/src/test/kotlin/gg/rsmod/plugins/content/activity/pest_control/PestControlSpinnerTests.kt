package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PestControlSpinnerTests {
    @Test
    fun `one successful heal scales for solo two and all team sizes on every lander`() {
        for (tier in PestControlTier.values()) {
            for (players in 1..25) {
                val f = fixture(players, tier.portalLifepoints)
                val before = f.hp
                PestControlMonsters.spinner(f.game, f.spinner, roll = 0)
                assertEquals(before + tier.portalLifepoints / 10 * minOf(players, 5) / 5, f.hp, "$tier $players players")
                if (players >= 5) assertEquals(tier.portalLifepoints / 10, f.hp - before)
            }
        }
    }

    @Test
    fun `heal chance stays two rolls in ten and multiple spinners each heal their own share`() {
        for (players in listOf(1, 2, 5, 25)) {
            val f = fixture(players)
            for (roll in 0..9) PestControlMonsters.spinner(f.game, f.spinner, roll)
            assertEquals(20 + 2 * (20 * minOf(players, 5) / 5), f.hp)
            val other = mockk<Npc>(relaxed = true)
            every { other.tile } returns f.spinner.tile
            f.game.spinnerPortal[other] = 0
            PestControlMonsters.spinner(f.game, other, roll = 1)
            assertEquals(20 + 3 * (20 * minOf(players, 5) / 5), f.hp)
        }
    }

    @Test
    fun `healing caps at maximum skips full dead absent and distant portals`() {
        val f = fixture(1)
        f.hp = 199
        PestControlMonsters.spinner(f.game, f.spinner, roll = 0)
        assertEquals(200, f.hp)
        PestControlMonsters.spinner(f.game, f.spinner, roll = 0)
        assertEquals(200, f.hp)
        f.hp = 0
        every { f.portal.isDead() } returns true
        PestControlMonsters.spinner(f.game, f.spinner, roll = 0)
        assertEquals(0, f.hp)
        every { f.portal.isDead() } returns false
        every { f.portal.isSpawned() } returns false
        f.hp = 20
        PestControlMonsters.spinner(f.game, f.spinner, roll = 0)
        assertEquals(20, f.hp)
        every { f.portal.isSpawned() } returns true
        every { f.spinner.tile } returns Tile(3300, 3300)
        PestControlMonsters.spinner(f.game, f.spinner, roll = 0)
        assertEquals(20, f.hp)
        val visibleTile = f.game.spinnerTile(0, f.spinner)!!
        verify { f.spinner.walkTo(visibleTile) }
    }

    @Test
    fun `exploding removes every spinner and its portal mapping`() {
        val f = fixture(2)
        // Player damage/poison is unchanged; keep players out of the explosion radius in this removal regression.
        f.game.players.forEach { every { it.tile } returns Tile(3400, 3400) }
        val spinners = listOf(f.spinner, mockk<Npc>(relaxed = true))
        for (spinner in spinners) {
            every { spinner.isSpawned() } returns true
            every { spinner.tile } returns Tile(3203, 3200)
            every { spinner.combatDef.deathAnimation } returns listOf(3912)
            f.game.spinnerPortal[spinner] = 0
            f.game.monsters.add(spinner)
        }
        spinners.forEach { PestControlMonsters.spinnerExplode(f.game, it) }
        assertTrue(f.game.monsters.isEmpty())
        assertTrue(f.game.spinnerPortal.isEmpty())
        val world = f.game.world
        spinners.forEach { spinner -> verify(exactly = 1) { world.remove(spinner) } }
    }

    @Test
    fun `portal death explodes all associated spinners and leaves another portal's spinner alone`() {
        val world = mockk<World>(relaxed = true)
        val map = mockk<InstancedMap>(relaxed = true)
        every { map.area.bottomLeft } returns Tile(3200, 3200)
        val game = PestControlGame(world, PestControlTier.NOVICE, map)
        val dead = mockk<Npc>(relaxed = true)
        val living = mockk<Npc>(relaxed = true)
        every { dead.isDead() } returns true
        every { living.isSpawned() } returns true
        game.portals[0] = dead
        game.portals[1] = living
        game.knight = mockk(relaxed = true)
        every { game.knight.getCurrentLifepoints() } returns 100
        val spinners = List(3) { mockk<Npc>(relaxed = true) }
        spinners.forEachIndexed { i, spinner ->
            every { spinner.isSpawned() } returns true
            every { spinner.combatDef.deathAnimation } returns listOf(3912)
            game.monsters += spinner
            game.spinnerPortal[spinner] = if (i < 2) 0 else 1
        }
        game.onPortalDeath(dead)
        assertEquals(listOf(spinners[2]), game.monsters)
        assertEquals(mapOf(spinners[2] to 1), game.spinnerPortal)
        spinners.take(2).forEach { spinner -> verify(exactly = 1) { world.remove(spinner) } }
        verify(exactly = 0) { world.remove(spinners[2]) }
        verify { game.knight.setCurrentLifepoints(150) }
    }

    private class Fixture(val game: PestControlGame, val portal: Npc, val spinner: Npc, var hp: Int)

    private fun fixture(players: Int, maximum: Int = 200): Fixture {
        val game = mockk<PestControlGame>(relaxed = true)
        val portal = mockk<Npc>(relaxed = true)
        val spinner = mockk<Npc>(relaxed = true)
        val f = Fixture(game, portal, spinner, 20)
        every { game.players } returns MutableList(players) { mockk<Player>(relaxed = true) }
        every { game.portals } returns arrayOf(portal, null, null, null)
        every { game.spinnerPortal } returns hashMapOf(spinner to 0)
        every { game.monsters } returns mutableListOf()
        every { portal.getCentreTile() } returns Tile(3200, 3200)
        every { portal.tile } returns Tile(3200, 3200)
        every { portal.getSize() } returns 3
        every { game.spinnerTile(0, spinner) } returns Tile(3203, 3200)
        every { portal.isSpawned() } returns true
        every { portal.getMaximumLifepoints() } returns maximum
        every { portal.getCurrentLifepoints() } answers { f.hp }
        every { portal.setCurrentLifepoints(any()) } answers { f.hp = firstArg() }
        every { spinner.tile } returns Tile(3203, 3200)
        return f
    }
}
