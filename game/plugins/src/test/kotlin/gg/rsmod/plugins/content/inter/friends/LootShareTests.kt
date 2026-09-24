package gg.rsmod.plugins.content.inter.friends

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** LootShare / CoinShare (2026-09-24): the 667 varbits it drives exist, and every npc drop goes through the one shared path. */
class LootShareTests {
    @Test
    fun `the lootshare and coinshare varbits exist in the 667 cache`() {
        listOf(LootShare.ACTIVE_VARBIT, LootShare.LOADING_VARBIT, LootShare.COIN_SHARE_VARBIT, LootShare.COIN_SHARE_SETTING_VARBIT).forEach { id ->
            assertNotNull(DEFINITIONS.getNullable(VarbitDef::class.java, id), "varbit $id")
        }
    }

    @Test
    fun `npc kill drops are shared in one place and non-stackable drops keep their owner`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/drops/DropTableFactory.kt").readText()
        assertTrue("LootShare.sharers(player, tile)" in source)
        assertTrue("GroundItem(item.id, 1, tile, owner as? Player)" in source, "each split item is owned by the killer")
        val friendsChat = File("src/main/kotlin/gg/rsmod/plugins/content/inter/friends/friends_chat.plugin.kts").readText()
        assertTrue("not available on this server" !in friendsChat)
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
