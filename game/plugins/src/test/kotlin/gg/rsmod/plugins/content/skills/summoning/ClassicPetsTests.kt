package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N216 (owner 2026-10-23: a baby dragon or terrier from the store landed on the floor instead of following) and N243 (every way a pet
 * arrives gives the OSRS message and the "Getting pet" sound): the 667 pets follow like the OSRS pets, and a bought pet goes through
 * Pets.award - never a duplicate, since the player paid for it. N88 part 3: the generated voice table serves every voiced follower.
 */
class ClassicPetsTests {
    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()

        @BeforeClass
        @JvmStatic
        fun load() {
            assertTrue(Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv"), File(DIR, "pets_667.tsv")) > 180)
        }

        @AfterClass
        @JvmStatic
        fun reset() {
            Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv"), File("does-not-exist"))
        }
    }

    /** The item ids of the 78 Store's "Pets" listing (StoreCatalogue), read from its source like the store builds it. */
    private fun storePets(): List<Int> {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/StoreCatalogue.kt").readText()
        val block = source.substringBefore(".forEach { (item, price) -> list += itemEntry(l, item, price, \"Pets\") }").substringAfterLast("listOf(")
        return Regex("""Items\.(\w+) to""").findAll(block).map { gg.rsmod.plugins.api.cfg.Items::class.java.getField(it.groupValues[1]).getInt(null) }.toList()
    }

    @Test
    fun `every 667 pet item follows as its own npc with Pick-up, and none collides with an OSRS pet`() {
        val defs = SummoningTestCache.definitions
        val classic = Pets.variants.filter { it.classic }
        assertTrue(classic.size >= 190, "${classic.size} 667 pets")
        assertEquals(Pets.variants.size, Pets.variants.map { it.item }.toSet().size, "an item is two pets")
        assertEquals(Pets.variants.size, Pets.variants.map { it.npc }.toSet().size, "an npc follows for two pets")
        val wrong = mutableListOf<String>()
        classic.forEach { v ->
            val item = defs.getNullable(ItemDef::class.java, v.item)
            val npc = defs.getNullable(NpcDef::class.java, v.npc)
            if (item == null || item.name != v.name) wrong += "${v.pet}: item ${v.item} is '${item?.name}'"
            if (npc == null || npc.options.none { it.equals("Pick-up", ignoreCase = true) }) wrong += "${v.pet}: npc ${v.npc} has no Pick-up"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun `N216 - every pet the 78 Store sells comes out as a follower`() {
        val sold = storePets()
        assertTrue(sold.size >= 20, "store pets found: $sold")
        val notPets = sold.filterNot { Pets.isPetItem(it) }.map { "$it ${SummoningTestCache.definitions.get(ItemDef::class.java, it).name}" }
        // The Pet rock is an item to talk to, not a follower (667 and OSRS). N295 (owner 2026-10-24): the Baby icefiend follows like
        // every other pet - npc 7713 got Pick-up and Talk-to, the item Drop (cache), and it is in the 667 pet table.
        assertEquals(listOf("3695 Pet rock"), notPets)
    }

    @Test
    fun `N243 - a bought pet the player already owns is not a duplicate, it goes to the backpack with the OSRS message`() {
        val item = Pets.variants.first { it.classic && it.name == "Baby dragon" }.item
        val inventory = ItemContainer(SummoningTestCache.definitions, INVENTORY_KEY)
        inventory.add(item, 1)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns SummoningTestCache.definitions
        val player = mockk<Player>(relaxed = true)
        val messages = mutableListOf<gg.rsmod.game.message.Message>()
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.bank } returns ItemContainer(SummoningTestCache.definitions, 800, gg.rsmod.game.model.container.ContainerStackType.STACK)
        every { player.write(*anyVararg<gg.rsmod.game.message.Message>()) } answers { messages += (args[0] as Array<*>).filterIsInstance<gg.rsmod.game.message.Message>() }
        mockkObject(Pets)
        try {
            every { Pets.hasFollower(player) } returns true
            assertEquals(Pets.Award.DUPLICATE, Pets.awardItem(player, item), "a drop of an owned pet stays the OSRS duplicate")
            assertEquals(Pets.Award.BACKPACK, Pets.awardItem(player, item, purchased = true))
            assertEquals(2, inventory.getItemCount(item), "the bought one is in the backpack")
            val texts = messages.filterIsInstance<gg.rsmod.game.message.impl.MessageGameMessage>().map { it.message }
            assertTrue(texts.any { "sneaking into your backpack" in it }, "OSRS backpack message: $texts")
        } finally {
            unmockkObject(Pets)
        }
    }

    @Test
    fun `N88 part 3 - the voice table serves every voiced follower and Huberte's screech line`() {
        val rows = PetVoices.parse(File(DIR, "pet_voices.tsv").readLines())
        PetVoices.install(rows, Pets.variants)
        val nexling = Pets.variants.single { it.name == "Nexling" }
        assertEquals(rows.single { it.pet == "Nexling" }.localSynth, PetVoices.voiceOf(nexling.npc))
        val midnight = Pets.variants.single { it.name == "Midnight" }
        assertEquals(rows.single { it.variant == "Midnight" }.localSynth, PetVoices.voiceOf(midnight.npc), "a variant row wins over the pet's")
        assertEquals(null, PetVoices.voiceOf(Pets.variants.first { it.pet == "Pet Kraken" }.npc), "Pet Kraken stays silent")
        // N257: a 667 pet answers Talk-to with its species' sound (it had none).
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.VM_MONKEY, PetVoices.voiceOf(Pets.variants.first { it.classic && it.name == "Baby monkey" }.npc))
        val huberte = Pets.variants.single { it.pet == "Huberte" }
        assertEquals(rows.single { it.line == "*screeches*" }.localSynth, PetVoices.lineSound(huberte.npc, "*screeches*"))
        assertEquals(null, PetVoices.lineSound(huberte.npc, "*bird noises*"))
        val voiced = Pets.variants.filterNot { it.classic }.count { PetVoices.voiceOf(it.npc) != null }
        assertTrue(voiced >= 150, "$voiced of ${Pets.variants.count { !it.classic }} OSRS followers have a voice")
    }
}
