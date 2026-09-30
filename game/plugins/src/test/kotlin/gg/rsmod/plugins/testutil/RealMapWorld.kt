package gg.rsmod.plugins.testutil

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.World
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.util.ServerProperties
import io.mockk.mockk
import java.nio.file.Paths

/**
 * A [World] with the real map of the game cache: definitions, XTEA keys, and every region's terrain and locs loaded on demand by
 * [World.collision] - the same [gg.rsmod.game.fs.DefinitionSet.createRegion] the server runs. For tests that must see where a player
 * can really walk (teleport landings, climbs).
 */
object RealMapWorld {
    val world: World by lazy {
        val world = World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true))
        world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        world.definitions.loadAll(world.filestore)
        val xteas = XteaKeyService()
        xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
        // World.services is internal to the game module; the map loader finds the keys through it.
        @Suppress("UNCHECKED_CAST")
        val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
        services += xteas
        world
    }
}
