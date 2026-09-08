package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * S4, 2026-09-03: Twisted Bow ammunition/gating coverage for [RangedCombatStrategy.canAttack].
 *
 * S3 deliberately left the Twisted Bow (local item [Items.TWISTED_BOW]) unregistered in
 * [BowType], so `bow = BowType.values.firstOrNull { it.item == weapon?.id }` returned `null` and
 * the ammo-compatibility gate at `RangedCombatStrategy.kt` was skipped entirely - any ammo, or no
 * ammo at all, was silently accepted. S4 registers it as an ordinary [BowType] entry (see
 * `BowType.kt`) so it goes through the exact same generic gate every other bow uses.
 *
 * Ammo tier is sourced from the OSRS Wiki "Twisted bow" page: "can fire any type of arrow,
 * including dragon arrows" / comparison table "Uses arrows as ammunition up to and including
 * dragon" - the same bronze-to-dragon-plus-broad tier the Dark bow family already uses in
 * [BowType].
 *
 * `RangedCombatStrategy.attack()`'s ammo-slot resolution, projectile lookup, consumption/break
 * chance, and Ava preservation/drop logic (lines below the `canAttack` gate) contain no
 * bow-identity or item-specific branching at all - they key only off `ammo.id`, the generic
 * `WeaponType`, and the equipped Ava cape. Since the Twisted Bow is `weapon_type: 16` (BOW) like
 * every other bow, it reaches that identical generic path with zero new code; a dedicated
 * mocked end-to-end `attack()` test would need a full combat/world harness this test suite
 * doesn't set up (see `HandCannonTests.kt`'s own note for the same limitation), so that reuse is
 * verified here by direct source inspection rather than by a redundant heavyweight test.
 */
class TwistedBowAmmoIntegrationTests {
    @Test
    fun `Twisted Bow is classified through BowType with the sourced bronze-to-dragon-plus-broad ammo tier`() {
        val bow = BowType.values.firstOrNull { it.item == Items.TWISTED_BOW }
        assertNotNull(bow, "Twisted Bow must be registered in BowType")
        assertTrue(Items.DRAGON_ARROW in bow.ammo, "Twisted Bow must accept dragon arrows")
        assertTrue(Items.BRONZE_ARROW in bow.ammo, "Twisted Bow must accept bronze arrows")
        assertTrue(Items.BROAD_ARROW in bow.ammo, "Twisted Bow must accept broad arrows")
        assertFalse(Items.RUNITE_BOLTS in bow.ammo, "Twisted Bow must not accept crossbow bolts")
    }

    @Test
    fun `Twisted Bow with a compatible dragon arrow is allowed to attack`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW, ammo = Items.DRAGON_ARROW)

        assertTrue(RangedCombatStrategy.canAttack(player, target()))
        verify(exactly = 0) { player.resetFacePawn() }
    }

    @Test
    fun `Twisted Bow with incompatible ammunition is rejected through the bow ammo gate`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW, ammo = Items.RUNITE_BOLTS)

        assertFalse(RangedCombatStrategy.canAttack(player, target()))
        verify {
            player.write(
                MessageGameMessage(
                    type = ChatMessageType.GAME_MESSAGE.id,
                    message = "You can't use that ammo with your bow.",
                    username = null,
                ),
            )
        }
    }

    @Test
    fun `Twisted Bow with no ammo equipped is rejected`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW, ammo = null)

        assertFalse(RangedCombatStrategy.canAttack(player, target()))
        verify {
            player.write(
                MessageGameMessage(
                    type = ChatMessageType.GAME_MESSAGE.id,
                    message = "There is no ammo left in your quiver.",
                    username = null,
                ),
            )
        }
    }

    @Test
    fun `Magic Longbow keeps accepting rune arrows unchanged`() {
        val player = newPlayer(weapon = Items.MAGIC_LONGBOW, ammo = Items.RUNE_ARROW)

        assertTrue(RangedCombatStrategy.canAttack(player, target()))
    }

    @Test
    fun `Magic Longbow keeps rejecting dragon arrows unlike the Twisted Bow`() {
        val player = newPlayer(weapon = Items.MAGIC_LONGBOW, ammo = Items.DRAGON_ARROW)

        assertFalse(RangedCombatStrategy.canAttack(player, target()))
    }

    @Test
    fun `Rune Crossbow does not inherit Twisted Bow or generic bow arrow rules`() {
        val validBolts = newPlayer(weapon = Items.RUNE_CROSSBOW, ammo = Items.RUNITE_BOLTS)
        assertTrue(RangedCombatStrategy.canAttack(validBolts, target()))
        assertTrue(Items.RUNE_CROSSBOW !in BowType.values.map { it.item }, "a crossbow must not be a BowType entry")

        val bowArrows = newPlayer(weapon = Items.RUNE_CROSSBOW, ammo = Items.DRAGON_ARROW)
        assertFalse(RangedCombatStrategy.canAttack(bowArrows, target()))
        verify {
            bowArrows.write(
                MessageGameMessage(
                    type = ChatMessageType.GAME_MESSAGE.id,
                    message = "You can't use that ammo with your crossbow.",
                    username = null,
                ),
            )
        }
    }

    @Test
    fun `Twisted Bow ammo tier matches the existing Dark bow tier reused rather than duplicated`() {
        val twistedBow = BowType.values.first { it.item == Items.TWISTED_BOW }
        val darkBow = BowType.values.first { it.item == Items.DARK_BOW }

        assertTrue(twistedBow.ammo.toSet() == darkBow.ammo.toSet())
    }

    private fun newPlayer(
        weapon: Int,
        ammo: Int?,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        equipment[EquipmentType.WEAPON.id] = Item(weapon)
        if (ammo != null) {
            equipment[EquipmentType.AMMO.id] = Item(ammo)
        }
        every { player.equipment } returns equipment
        return player
    }

    private fun target(): Npc = mockk(relaxed = true)

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
