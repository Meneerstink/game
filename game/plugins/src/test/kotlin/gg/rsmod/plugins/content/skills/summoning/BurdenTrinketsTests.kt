package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * N423 Beast of burden trinkets (design v6, GOEDGEKEURDE-ONTWERPEN punt 8): the five items in the production cache and items.yml,
 * the per-pet binding record, and the player-facing texts.
 */
class BurdenTrinketsTests {
    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()

        @BeforeClass
        @JvmStatic
        fun load() {
            Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv"), File(DIR, "pets_667.tsv"))
        }

        private const val TRINKET_OF_FAIRIES = 23822
    }

    @Test
    fun `the five trinkets are in the cache - name, Bind Check Unbind, no Drop or Destroy, own mesh, fairy-trinket camera`() {
        val defs = SummoningTestCache.definitions
        val fairies = defs.get(ItemDef::class.java, TRINKET_OF_FAIRIES)
        assertEquals(listOf(24232, 24233, 24234, 24235, 24236), BurdenTrinkets.Tier.values().map { it.item })
        assertEquals(listOf(6, 9, 12, 18, 30), BurdenTrinkets.Tier.values().map { it.slots })
        val raw = com.displee.cache.CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        val meshes = mutableSetOf<Int>()
        try {
            BurdenTrinkets.Tier.values().forEach { t ->
                val def = defs.get(ItemDef::class.java, t.item)
                assertEquals(t.trinketName, def.name)
                val ops = def.inventoryMenu.toList().map { it?.takeIf { s -> s.isNotBlank() } }
                assertEquals(listOf("Bind", "Check", "Unbind", null, null), ops, "${t.trinketName} ops")
                assertTrue(def.inventoryMenu[4] != null, "${t.trinketName}: op 5 must be set (empty) - unset is the client's default Drop")
                assertTrue(!def.grandExchange, "${t.trinketName} is on the Grand Exchange")
                // The bytes are exactly the tool's: the fairy trinket's icon camera, the trinket's own mesh.
                val bytes = requireNotNull(raw.data(19, t.item ushr 8, t.item and 0xFF)) { "item ${t.item} missing" }
                val mesh = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
                assertTrue(java.util.Arrays.equals(gg.rsmod.game.tools.importer.BurdenTrinketTool.itemBytes(t.trinketName, mesh, 10_000 * t.slots), bytes), "${t.trinketName} bytes")
                assertNotEquals(gg.rsmod.game.tools.importer.BurdenTrinketTool.TRINKET_OF_FAIRIES_MODEL, mesh, "${t.trinketName} reuses the fairy trinket mesh")
                assertTrue(meshes.add(mesh), "${t.trinketName} shares a mesh")
                assertTrue(raw.data(7, mesh, 0) != null, "${t.trinketName}: mesh $mesh missing")
            }
        } finally {
            raw.close()
        }
        assertTrue(fairies.name == "Trinket of fairies")
    }

    @Test
    fun `items yml - untradeable, examine 'The spirit of a (kind), sealed in stone'`() {
        val text = File("../../data/cfg/items.yml").readText().replace("\r\n", "\n")
        BurdenTrinkets.Tier.values().forEach { t ->
            val block = text.substringAfter("- id: ${t.item}\n").substringBefore("- id: ")
            assertTrue("name: \"${t.trinketName}\"" in block, "${t.item} name")
            assertTrue("examine: \"${BurdenTrinkets.examine(t)}\"" in block, "${t.item} examine")
            assertTrue("tradeable: false" in block, "${t.item} tradeable")
        }
        assertEquals("The spirit of a pack yak, sealed in stone.", BurdenTrinkets.examine(BurdenTrinkets.Tier.PACK_YAK))
    }

    @Test
    fun `bindings are kept per pet and survive a round trip, junk is dropped`() {
        val bound = mapOf("Beef" to 24236, "Ikkle Hydra" to 24234, "Cat (1555)" to 24232)
        assertEquals(bound, BurdenTrinkets.decode(BurdenTrinkets.encode(bound)))
        assertEquals(mapOf("Beef" to 24236), BurdenTrinkets.decode("Beef=24236;Nothing=995;=24232;broken"))
        assertEquals(emptyMap(), BurdenTrinkets.decode(null))
    }

    @Test
    fun `a binding counts only while enough trinkets of its kind are owned`() {
        val bound = mapOf("Beef" to 24236, "Abyssal orphan" to 24236, "Ikkle Hydra" to 24234)
        assertEquals(bound, BurdenTrinkets.effective(bound) { 2 })
        // One Pack yak trinket left: the first pet by name keeps it.
        assertEquals(mapOf("Abyssal orphan" to 24236, "Ikkle Hydra" to 24234), BurdenTrinkets.effective(bound) { if (it == 24236) 1 else 1 })
        assertEquals(mapOf("Ikkle Hydra" to 24234), BurdenTrinkets.effective(bound) { if (it == 24236) 0 else 1 })
    }

    @Test
    fun `the bind question is the approved text`() {
        val ok = BurdenTrinkets.BindCheck.Ok("Beef", "Beef", null)
        assertEquals(
            "Bind this trinket to your Beef? It will carry 30 items for you - just like a Pack yak. You keep the trinket and can unbind it at any time.",
            BurdenTrinkets.bindQuestion(BurdenTrinkets.Tier.PACK_YAK, ok),
        )
        val bigger = BurdenTrinkets.BindCheck.Ok("Beef", "Beef", BurdenTrinkets.Tier.KALPHITE)
        assertTrue("It replaces the Kalphite burden trinket, which stays yours." in BurdenTrinkets.bindQuestion(BurdenTrinkets.Tier.PACK_YAK, bigger))
    }

    @Test
    fun `Check names the upgraded pet, or says the trinket is free`() {
        val p = newPlayer()
        p.inventory.add(24236, 1)
        assertEquals("Your Pack yak burden trinket is not bound to a pet.", BurdenTrinkets.checkText(p, BurdenTrinkets.Tier.PACK_YAK))
        p.attr[BurdenTrinkets.BOUND_ATTR] = "Beef=24236"
        assertEquals("Your Pack yak burden trinket is bound to your Upgraded Beef (30 storage).", BurdenTrinkets.checkText(p, BurdenTrinkets.Tier.PACK_YAK))
        // The trinket in the bank counts the same.
        p.inventory.remove(24236, 1)
        p.bank.add(24236, 1)
        assertEquals(BurdenTrinkets.Tier.PACK_YAK, BurdenTrinkets.upgradeOf(p, "Beef"))
        // Gone everywhere: the upgrade waits for Perdu, who gives the kind back for free.
        BurdenTrinkets.recordOwned(p)
        p.bank.remove(24236, 1)
        assertEquals(null, BurdenTrinkets.upgradeOf(p, "Beef"))
        assertEquals(listOf(BurdenTrinkets.Tier.PACK_YAK), BurdenTrinkets.lost(p))
    }

    @Test
    fun `binding needs the pet to be following and no combat`() {
        val p = newPlayer()
        p.inventory.add(24232, 1)
        val check = BurdenTrinkets.canBind(p, BurdenTrinkets.Tier.KALPHITE)
        assertEquals(BurdenTrinkets.BindCheck.Refused("A burden trinket can only be bound to the pet that is following you."), check)
    }

    @Test
    fun `a trinket is always kept on death, like a pet`() {
        BurdenTrinkets.Tier.values().forEach { t ->
            val fate = gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection.fateOf(SummoningTestCache.definitions, Item(t.item), deepWilderness = true)
            assertEquals(gg.rsmod.plugins.content.mechanics.death.UntradeableFate.UNCHANGED, fate.fate, t.trinketName)
        }
    }

    private fun newPlayer(): Player {
        val defs = SummoningTestCache.definitions
        val player = mockk<Player>(relaxed = true)
        val inventory = ItemContainer(defs, INVENTORY_KEY)
        val equipment = ItemContainer(defs, EQUIPMENT_KEY)
        val bank = ItemContainer(defs, BANK_KEY)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.equipment } returns equipment
        every { player.bank } returns bank
        every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, EQUIPMENT_KEY to equipment, BANK_KEY to bank)
        every { player.world.definitions } returns defs
        return player
    }
}
