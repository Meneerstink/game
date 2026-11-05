package gg.rsmod.plugins.content.areas.deathsoffice

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.*
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.*

/** N591 owner V6a supersedes N202's refusal: a standing grave can be collected early for 15%. */
class DeathCollectGravestoneTests {
    private val value = ItemRiskValueProvider { 1_000_000L }
    @Test fun `an early quote leaves the grave and all payment sources untouched until confirmed`() {
        val p = player()
        p.gravestone[0] = Item(Items.ABYSSAL_WHIP)
        assertEquals(150_000L, EarlyDeathCollection.quote(p, value)!!.fee)
        assertEquals(1, p.gravestone.getItemCount(Items.ABYSSAL_WHIP))
        assertEquals(200_000, p.bank.getItemCount(Items.COINS_995))
        assertTrue(p.deathRecovery.isEmpty)
    }
    @Test fun `a standing grave is available even when the office already contains older items`() {
        val p = player()
        p.gravestone[0] = Item(Items.ABYSSAL_WHIP)
        p.deathRecovery[0] = Item(Items.TELEPORT_TO_HOUSE, 100)
        val quote = EarlyDeathCollection.quote(p, value)!!
        assertEquals(EarlyDeathCollection.Outcome.COLLECTED, EarlyDeathCollection.collect(p, quote))
        assertEquals(1, p.deathRecovery.getItemCount(Items.ABYSSAL_WHIP))
        assertEquals(100, p.deathRecovery.getItemCount(Items.TELEPORT_TO_HOUSE))
        assertFalse(Gravestone.exists(p))
        val paid = p.deathRecovery.items.filterNotNull().single { it.id == Items.ABYSSAL_WHIP }
        assertEquals(0L, DeathFees.officeFee(paid, 1, value))
    }
    @Test fun `an expired grave uses the existing five percent office path`() {
        val p = player()
        p.gravestone[0] = Item(Items.ABYSSAL_WHIP)
        p.attr[GRAVESTONE_TICKS_ATTR] = 0
        assertNull(EarlyDeathCollection.quote(p, value))
        assertTrue(Gravestone.collapse(p))
        val stored = p.deathRecovery.items.filterNotNull().single()
        assertEquals(50_000L, DeathFees.officeFee(stored, 1, value))
    }
    private fun player(): Player {
        val p = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { p.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        every { p.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { p.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        p.bank[0] = Item(Items.COINS_995, 200_000)
        p.attr[GRAVESTONE_TILE_ATTR] = Tile(3113,9841).as30BitInteger
        p.attr[GRAVESTONE_TICKS_ATTR] = 1500
        return p
    }
    companion object {
        private val DEFINITIONS = DefinitionSet()
        @BeforeClass @JvmStatic fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..", "..", "data", "cache").toString()))
        }
    }
}
