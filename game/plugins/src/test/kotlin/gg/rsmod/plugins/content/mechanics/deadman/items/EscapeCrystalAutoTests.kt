package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * ESCAPE-NULHIT (wiki Escape crystal): auto-afk activates "upon taking damage (including zeros)" after the set inactivity,
 * and not while the player is moving - then it activates once the player stops.
 */
class EscapeCrystalAutoTests {
    private val attributes = AttributeMap()
    private val world = mockk<World>(relaxed = true)
    private val player = mockk<Player>(relaxed = true)
    private var cycle = 1000
    private var hp = 99
    private var moving = false

    @BeforeTest
    fun setUp() {
        every { player.world } returns world
        every { player.attr } returns attributes
        every { world.currentCycle } answers { cycle }
        every { player.getCurrentLifepoints() } answers { hp }
        every { player.hasMoveDestination() } answers { moving }
        mockkObject(DmmItems)
        every { DmmItems.escapeCrystal(any()) } just runs
        attributes[DmmItems.AUTOTELE_ON] = 1
        attributes[DmmItems.AUTOTELE_SECS] = 10
        attributes[LAST_ACTIVE_CYCLE_ATTR] = cycle
        DmmItems.autoTeleTick(player) // remembers the hitpoints
    }

    @AfterTest
    fun tearDown() = unmockkObject(DmmItems)

    @Test
    fun `a zero hit after the inactivity teleports`() {
        cycle += 20 // 12 s idle
        DmmItems.onHitTaken(player)
        DmmItems.autoTeleTick(player)
        verify(exactly = 1) { DmmItems.escapeCrystal(player) }
    }

    @Test
    fun `a zero hit while still active does nothing, also not later`() {
        cycle += 5 // 3 s idle
        DmmItems.onHitTaken(player)
        DmmItems.autoTeleTick(player)
        cycle += 30
        DmmItems.autoTeleTick(player)
        verify(exactly = 0) { DmmItems.escapeCrystal(any()) }
    }

    @Test
    fun `lost hitpoints without a hitsplat (poison) still count`() {
        cycle += 20
        hp = 95
        DmmItems.autoTeleTick(player)
        verify(exactly = 1) { DmmItems.escapeCrystal(player) }
    }

    @Test
    fun `while moving the crystal waits and activates when the player stops`() {
        cycle += 20
        moving = true
        DmmItems.onHitTaken(player)
        DmmItems.autoTeleTick(player)
        verify(exactly = 0) { DmmItems.escapeCrystal(any()) }
        moving = false
        cycle += 1
        DmmItems.autoTeleTick(player)
        verify(exactly = 1) { DmmItems.escapeCrystal(player) }
    }

    @Test
    fun `no hit and no damage never teleports`() {
        cycle += 200
        DmmItems.autoTeleTick(player)
        verify(exactly = 0) { DmmItems.escapeCrystal(any()) }
    }
}
