package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeathRecoveryServiceTests {
    @Test
    fun `expired recovery is purged and cannot be reclaimed`() {
        val player = newPlayer()
        player.deathRecovery[0] = Item(4151, 1)
        player.inventory[0] = Item(995, 1_000)
        player.attr[DEATH_RECOVERY_EXPIRY_ATTR] = 9_999L
        player.attr[DEATH_RECOVERY_FEE_ATTR] = 100

        val expired = DeathRecoveryService.expireIfNeeded(player, nowMs = 10_000L)

        assertTrue(expired)
        assertTrue(player.deathRecovery.isEmpty)
        assertNull(player.attr[DEATH_RECOVERY_EXPIRY_ATTR])
        assertNull(player.attr[DEATH_RECOVERY_FEE_ATTR])
        assertEquals(1_000, player.inventory.getItemCount(995))
        assertEquals(DeathReclaimOutcome.NothingToReclaim::class, DeathRecoveryService.reclaim(player)::class)
    }

    @Test
    fun `reclaim reports expiry and does not charge when deadline has passed`() {
        val player = newPlayer()
        player.deathRecovery[0] = Item(4151, 1)
        player.inventory[0] = Item(995, 1_000)
        player.attr[DEATH_RECOVERY_EXPIRY_ATTR] = 9_999L
        player.attr[DEATH_RECOVERY_FEE_ATTR] = 100

        val outcome = DeathRecoveryService.reclaim(player, nowMs = 10_000L)

        assertEquals(DeathReclaimOutcome.Expired::class, outcome::class)
        assertEquals(1_000, player.inventory.getItemCount(995))
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        return player
    }

    companion object {
        private val DEFINITIONS = gg.rsmod.game.fs.DefinitionSet()
    }
}
