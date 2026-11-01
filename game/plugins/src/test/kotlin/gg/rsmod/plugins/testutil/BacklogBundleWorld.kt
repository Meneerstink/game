package gg.rsmod.plugins.testutil

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.game.ItemMetadataService
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import java.io.File
import java.net.URLClassLoader

/** Tests only: external cache/XTEAs may be read without copying or changing them in an isolated worktree. */
object BacklogBundleWorld {
    val world: World by lazy {
        val ctx = mockk<GameContext>(relaxed = true)
        every { ctx.home } returns Tile(3164, 3495, 0)
        every { ctx.skillCount } returns 25
        every { ctx.playerLimit } returns 64
        every { ctx.npcStatCount } returns gg.rsmod.game.model.entity.Npc.Stats.DEFAULT_NPC_STAT_COUNT
        val w = World(ctx, mockk<DevContext>(relaxed = true))
        w.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        w.filestore = CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: "../../data/cache")
        w.definitions.loadAll(w.filestore)
        val xteas = XteaKeyService()
        xteas.init(mockk(relaxed = true), w, ServerProperties().loadMap(mapOf("path" to (System.getenv("RSPS_TEST_XTEAS") ?: "../../data/xteas"))))
        @Suppress("UNCHECKED_CAST")
        val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(w) as MutableList<Service>
        services += xteas
        ItemMetadataService().init(mockk(relaxed = true), w, ServerProperties().loadMap(mapOf("path" to "../../data/cfg/items.yml")))
        World::class.java.declaredMethods.first { it.name.startsWith("loadUpdateBlocks") }.apply { isAccessible = true }
            .invoke(w, File("../../data/blocks.yml"))
        w
    }

    fun player(): Client = Client(EmbeddedChannel(), world).apply {
        username = "backlogfixture"
        loginUsername = username
        tile = Tile(3164, 3495, 0)
        gameSystem = mockk(relaxed = true)
        (0..24).forEach { skills.setBaseLevel(it, 99) }
        setCurrentLifepoints(99)
    }

    fun bind(className: String) {
        val classes = File("scripts/build/classes/kotlin/main")
        val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), javaClass.classLoader)
        loader.loadClass(className).getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
            .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
    }
}
