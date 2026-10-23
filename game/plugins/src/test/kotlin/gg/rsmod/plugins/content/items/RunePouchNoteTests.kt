package gg.rsmod.plugins.content.items

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.home.QuartermasterSupplies
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N190 (owner 2026-10-22): the Quartermaster sells a Rune pouch note at its OSRS GE price (OSRS item 24587) instead of a 10,000-coin
 * rune pouch; a banker or Grand Exchange clerk turns the note into a rune pouch.
 */
class RunePouchNoteTests {
    @Test
    fun `the note is in the cache, sold by the Quartermaster at its OSRS price`() {
        val def = DEFINITIONS.get(ItemDef::class.java, Items.RUNE_POUCH_NOTE)
        assertEquals("Rune pouch note", def.name)
        assertTrue(QuartermasterSupplies.stock.any { it.first == Items.RUNE_POUCH_NOTE }, "sold on page 1")
        assertTrue(QuartermasterSupplies.stock.none { it.first == Items.RUNE_POUCH }, "no 10k rune pouch any more")
        val price = OsrsGuidePrices.osrsPrice(def)
        assertTrue(price != null && price > 1_000_000, "the OSRS GE price of a Rune pouch note: $price")
    }

    @Test
    fun `a note becomes one rune pouch, the last note frees its own slot`() {
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val player = player(inventory)
        assertFalse(RunePouchNote.exchange(player), "no note")

        inventory.add(Items.RUNE_POUCH_NOTE, 2)
        repeat(27) { inventory.add(Items.BRONZE_DAGGER) }
        assertFalse(RunePouchNote.exchange(player), "two notes and no free slot: nothing happens")
        assertEquals(2, inventory.getItemCount(Items.RUNE_POUCH_NOTE))

        inventory.remove(Items.BRONZE_DAGGER, 1)
        assertTrue(RunePouchNote.exchange(player))
        assertEquals(1, inventory.getItemCount(Items.RUNE_POUCH_NOTE))
        assertEquals(1, inventory.getItemCount(Items.RUNE_POUCH))

        assertTrue(RunePouchNote.exchange(player), "the last note's slot takes the pouch")
        assertEquals(0, inventory.getItemCount(Items.RUNE_POUCH_NOTE))
        assertEquals(2, inventory.getItemCount(Items.RUNE_POUCH))
    }

    @Test
    fun `N218 - a successful exchange stamps (bank_stamp sound) with the OSRS line, a refused one is silent`() {
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val player = player(inventory)
        val sent = mutableListOf<Any>()
        every { player.write(*anyVararg<gg.rsmod.game.message.Message>()) } answers { sent.addAll((args[0] as Array<*>).filterNotNull()) }
        RunePouchNote.exchange(player)
        assertTrue(sent.none { it is gg.rsmod.game.message.impl.SynthSoundMessage }, "no note: no sound")
        inventory.add(Items.RUNE_POUCH_NOTE, 1)
        assertTrue(RunePouchNote.exchange(player))
        val sound = sent.filterIsInstance<gg.rsmod.game.message.impl.SynthSoundMessage>().single()
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.BANK_STAMP, sound.sound)
        assertTrue(sent.filterIsInstance<gg.rsmod.game.message.impl.MessageGameMessage>().any { it.message == "You exchange the promissory note." })
    }

    private fun player(inventory: ItemContainer): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.inventory } returns inventory
        every { p.attr } returns AttributeMap()
        return p
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
