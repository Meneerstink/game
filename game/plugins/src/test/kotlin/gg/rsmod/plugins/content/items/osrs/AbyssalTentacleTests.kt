package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.mechanics.death.DeathContainerSource
import gg.rsmod.plugins.content.mechanics.death.DeathContext
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskResult
import gg.rsmod.plugins.content.mechanics.death.DeathResolutionResult
import gg.rsmod.plugins.content.mechanics.death.DeathSlotItem
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [AbyssalTentacle] (degradation audit 2026-10-04 section 6, OSRS Wiki "Abyssal tentacle"). */
class AbyssalTentacleTests {
    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.tile } returns Tile(3200, 3200, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        return player
    }

    private fun <T> withExt(block: () -> T): T {
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try {
            return block()
        } finally {
            unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        }
    }

    @Test
    fun `an old tentacle holds 10,000 charges and every attack uses one`() {
        val player = newPlayer()
        player.equipment[EquipmentType.WEAPON.id] = Item(Items.ABYSSAL_TENTACLE, 1)
        assertEquals(AbyssalTentacle.CREATED_CHARGES, AbyssalTentacle.charges(player.equipment[EquipmentType.WEAPON.id]!!))
        AbyssalTentacle.afterAttack(player)
        assertEquals(9_999, AbyssalTentacle.charges(player.equipment[EquipmentType.WEAPON.id]!!))
    }

    @Test
    fun `the last charge consumes the whip and leaves the kraken tentacle`() {
        val player = newPlayer()
        player.equipment[EquipmentType.WEAPON.id] = AbyssalTentacle.withCharges(Item(Items.ABYSSAL_TENTACLE, 1), 1)
        withExt {
            every { player.refreshBonuses() } just Runs
            AbyssalTentacle.afterAttack(player)
        }
        assertNull(player.equipment[EquipmentType.WEAPON.id])
        assertEquals(1, player.inventory.getItemCount(Items.KRAKEN_TENTACLE))
    }

    @Test
    fun `a lost tentacle becomes a kraken tentacle, a protected one stays`() {
        val player = newPlayer()
        player.equipment[EquipmentType.WEAPON.id] = Item(Items.ABYSSAL_TENTACLE, 1)
        player.inventory[0] = Item(Items.ABYSSAL_TENTACLE, 1)
        val lost = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, EquipmentType.WEAPON.id, Item(Items.ABYSSAL_TENTACLE, 1)))
        val protected = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(Items.ABYSSAL_TENTACLE, 1)))
        val result = AbyssalTentacle.degradeLost(DeathResolutionResult(DeathContext.PVM_SAFE, player, null, DeathItemRiskResult(1, protected, lost)))
        assertEquals(Items.KRAKEN_TENTACLE, result.itemRisk.lost.single().item.id)
        assertEquals(Items.KRAKEN_TENTACLE, player.equipment[EquipmentType.WEAPON.id]?.id)
        assertEquals(Items.ABYSSAL_TENTACLE, player.inventory[0]?.id)
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
