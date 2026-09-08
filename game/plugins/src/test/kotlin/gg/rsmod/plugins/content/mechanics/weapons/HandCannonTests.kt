package gg.rsmod.plugins.content.mechanics.weapons

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [HandCannon] (P8, 2026-09-02 autonomous run - see `HandCannon.kt`'s own KDoc and
 * `RSPS_DECISIONS.md` for the full sourcing note, including which numeric constants are
 * inferred placeholders rather than sourced facts). Only the data-independent logic is tested
 * here: the explosion-chance curve, the roll, and the destroy/self-damage/message mechanics of
 * [HandCannon.explode] - not the `RangedCombatStrategy`/special-attack wiring, which needs a
 * live combat/world harness this test suite doesn't set up.
 */
class HandCannonTests {
    @Test
    fun `explosion chance at the low Firemaking anchor matches the sourced anchor value`() {
        assertEquals(0.0275, HandCannon.explosionChance(firemakingLevel = 61, isSpecialAttack = false), 1e-9)
    }

    @Test
    fun `explosion chance at the high Firemaking anchor matches the sourced anchor value`() {
        assertEquals(0.0039, HandCannon.explosionChance(firemakingLevel = 99, isSpecialAttack = false), 1e-9)
    }

    @Test
    fun `explosion chance is clamped below the low anchor`() {
        assertEquals(0.0275, HandCannon.explosionChance(firemakingLevel = 1, isSpecialAttack = false), 1e-9)
    }

    @Test
    fun `explosion chance is clamped above the high anchor`() {
        // Firemaking can't actually exceed 99, but the function must not extrapolate past it.
        assertEquals(0.0039, HandCannon.explosionChance(firemakingLevel = 120, isSpecialAttack = false), 1e-9)
    }

    @Test
    fun `explosion chance interpolates linearly at the midpoint Firemaking level`() {
        val midLevel = (61 + 99) / 2
        val expected = (0.0275 + 0.0039) / 2
        assertEquals(expected, HandCannon.explosionChance(firemakingLevel = midLevel, isSpecialAttack = false), 1e-9)
    }

    @Test
    fun `special attack doubles the normal-attack chance and stays within 0 to 1`() {
        val normal = HandCannon.explosionChance(firemakingLevel = 61, isSpecialAttack = false)
        val special = HandCannon.explosionChance(firemakingLevel = 61, isSpecialAttack = true)
        assertEquals(normal * 2, special, 1e-9)
        assertTrue(special in 0.0..1.0)
    }

    @Test
    fun `roll explodes when the world roll lands below the chance`() {
        val world = mockk<World>()
        every { world.randomDouble() } returns 0.0

        assertTrue(HandCannon.rollExplodes(world, firemakingLevel = 61, isSpecialAttack = false))
    }

    @Test
    fun `roll does not explode when the world roll lands at or above the chance`() {
        val world = mockk<World>()
        every { world.randomDouble() } returns 0.99

        assertFalse(HandCannon.rollExplodes(world, firemakingLevel = 61, isSpecialAttack = false))
    }

    @Test
    fun `explode is a no-op when no hand cannon is equipped`() {
        val player = newPlayer(handCannonEquipped = false)

        HandCannon.explode(player)

        assertEquals(null, player.getEquipment(EquipmentType.WEAPON))
        verify(exactly = 0) { player.addHit(any()) }
    }

    @Test
    fun `explode destroys the hand cannon, self-damages the wielder, and messages them`() {
        val player = newPlayer(handCannonEquipped = true)
        every { player.getCurrentLifepoints() } returns 100

        HandCannon.explode(player)

        assertEquals(null, player.getEquipment(EquipmentType.WEAPON))

        val hitSlot = slot<Hit>()
        verify { player.addHit(capture(hitSlot)) }
        // floor(100 * 0.10) = 10
        assertEquals(10, hitSlot.captured.hitmarks.sumOf { it.damage })

        verify {
            player.write(
                MessageGameMessage(type = ChatMessageType.GAME_MESSAGE.id, message = "Your hand cannon explodes in your hands!", username = null),
            )
        }
    }

    @Test
    fun `explode deals a minimum of 1 self-damage even at very low lifepoints`() {
        val player = newPlayer(handCannonEquipped = true)
        every { player.getCurrentLifepoints() } returns 1

        HandCannon.explode(player)

        val hitSlot = slot<Hit>()
        verify { player.addHit(capture(hitSlot)) }
        assertEquals(1, hitSlot.captured.hitmarks.sumOf { it.damage })
    }

    private fun newPlayer(handCannonEquipped: Boolean): Player {
        val player = mockk<Player>(relaxed = true)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (handCannonEquipped) {
            equipment[EquipmentType.WEAPON.id] = Item(Items.HAND_CANNON)
        }
        every { player.equipment } returns equipment
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
