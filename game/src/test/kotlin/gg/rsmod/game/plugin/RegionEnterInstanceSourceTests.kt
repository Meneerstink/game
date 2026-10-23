package gg.rsmod.game.plugin

import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAllocator
import gg.rsmod.game.model.instance.InstancedMapAttribute
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.EnumSet
import kotlin.test.Test

/**
 * N15 (LC02 10-14: "region 25600 has no enter handler - no region music plays here" at every house entry): an instance region's
 * dynamic id has no handlers, so it is entered like the real region its chunk was copied from (the house: template region 7503,
 * Home Sweet Home in music_by_region.yaml; the Pest Control island: 10536).
 */
class RegionEnterInstanceSourceTests {
    @Test
    fun `an instance tile runs the enter handlers of the region its chunk was copied from`() {
        val world = World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true))
        @Suppress("UNCHECKED_CAST")
        val maps = InstancedMapAllocator::class.java.getDeclaredField("maps").apply { isAccessible = true }.get(world.instanceAllocator) as MutableList<InstancedMap>
        val chunks = InstancedChunkSet.Builder().set(0, 0, 0, 0, Tile(1856, 5056, 0)).build()
        val ctor = InstancedMap::class.java.declaredConstructors.first().apply { isAccessible = true }
        maps += ctor.newInstance(Area(6400, 0, 6463, 63), chunks, Tile(3222, 3222), null, EnumSet.noneOf(InstancedMapAttribute::class.java)) as InstancedMap

        val repo = PluginRepository(world)
        val houseMusic: Plugin.() -> Unit = {}
        repo.bindRegionEnter(Tile(1856, 5056).regionId, houseMusic)

        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.tile } returns Tile(6403, 3, 0)
        repo.executeRegionEnter(player, Tile(6403, 3).regionId)
        verify(exactly = 1) { player.executePlugin(houseMusic) }

        val outside = mockk<Player>(relaxed = true)
        every { outside.world } returns world
        every { outside.tile } returns Tile(6403, 300, 0)
        repo.executeRegionEnter(outside, Tile(6403, 300).regionId)
        verify(exactly = 0) { outside.executePlugin(houseMusic) }
    }
}
