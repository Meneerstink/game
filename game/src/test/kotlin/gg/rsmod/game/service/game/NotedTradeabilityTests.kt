package gg.rsmod.game.service.game

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.util.ServerProperties
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N509 (owner 2026-11-01, bug 20261101-000732): items.yml marks every 667 note "tradeable: false"; a note must be exactly as tradeable
 * as the item it notes, or the death rules, Perdu, ground items, staking and shops treat a noted potion as an untradeable.
 */
class NotedTradeabilityTests {
    private val world: World by lazy {
        val world = World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true))
        world.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        world.definitions.loadAll(world.filestore)
        ItemMetadataService().init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "data", "cfg", "items.yml").toString())))
        world
    }

    private fun def(id: Int) = world.definitions.get(ItemDef::class.java, id)

    @Test
    fun `the noted ranging potion from the owner's report is tradeable like the potion`() {
        assertTrue(def(169).tradeable, "Ranging potion (3)")
        assertTrue(def(170).noted)
        assertTrue(def(170).tradeable, "noted Ranging potion (3)")
    }

    @Test
    fun `every note has its item's tradeability`() {
        val count = world.definitions.getCount(ItemDef::class.java)
        val wrong =
            (0 until count).map { def(it) }.filter { it.noted && it.noteLinkId in 1 until count }
                .filter { it.tradeable != def(it.noteLinkId).tradeable }
                .map { "${it.id} ${it.name}" }
        assertEquals(emptyList(), wrong)
    }
}
