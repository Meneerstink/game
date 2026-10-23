package gg.rsmod.plugins.content.items.drop

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.World
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N215 (owner 2026-10-23): a drop warning for untradeables and (OSRS) for a drop worth 30,000 coins or more. */
class DropWarningTests {
    private val world: World = mockk<World>(relaxed = true).also { every { it.definitions } returns DEFINITIONS }

    @Test
    fun `untradeables and valuable stacks ask first, cheap tradeables drop at once`() {
        val price = mapOf(Items.LOBSTER to 150, Items.ABYSSAL_WHIP to 1_500_000, Items.COINS_995 to 1)
        val value = { id: Int -> price[id] ?: 0 }
        assertTrue(DropWarning.needsWarning(world, Item(Items.INFERNAL_MAX_CAPE), value), "the owner's Infernal max cape (untradeable)")
        assertTrue(DropWarning.needsWarning(world, Item(Items.ABYSSAL_WHIP), value), "1.5M whip")
        assertTrue(DropWarning.needsWarning(world, Item(Items.COINS_995, 30_000), value), "30k coins")
        assertFalse(DropWarning.needsWarning(world, Item(Items.COINS_995, 29_999), value))
        assertFalse(DropWarning.needsWarning(world, Item(Items.LOBSTER), value), "a lobster drops straight away")
    }

    @Test
    fun `the engine asks the hook before a plain drop, never before a plugin's own drop (pets)`() {
        val handler = File("../src/main/kotlin/gg/rsmod/game/message/handler/OpHeld5Handler.kt").readText()
        assertTrue("if (!world.plugins.hasDropHandler(item.id) && confirm?.invoke(client, item, slot) == true) return" in handler)
        val drop = handler.substringAfter("fun drop(").substringBefore("override fun handle")
        assertTrue("if (client.inventory[slot]?.id != item.id) return" in drop, "a confirmed drop only takes the item that is still in the slot")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun load() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            // items.yml decides tradeability at boot (the bare cache marks nothing tradeable): these are tradeable, the cape is not.
            listOf(Items.LOBSTER, Items.ABYSSAL_WHIP, Items.COINS_995).forEach { DEFINITIONS.get(gg.rsmod.game.fs.def.ItemDef::class.java, it).tradeable = true }
            DEFINITIONS.get(gg.rsmod.game.fs.def.ItemDef::class.java, Items.INFERNAL_MAX_CAPE).tradeable = false
        }
    }
}
