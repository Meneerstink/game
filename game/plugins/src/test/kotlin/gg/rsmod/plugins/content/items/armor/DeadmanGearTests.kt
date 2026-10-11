package gg.rsmod.plugins.content.items.armor

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.removed.RemovedItems
import io.mockk.every
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner 2026-10-11 (dossier 4a-1, OSRS Wiki): Deadman gear and Torva never degrade, corrupt versions do not exist - every held
 * degraded / corrupt / used / broken copy becomes the normal item at login, in every container, and nothing offers them again.
 */
class DeadmanGearTests {
    @Test
    fun `every degraded, corrupt, used and broken id converts to the cache's own normal piece`() {
        val expectedSources =
            CorruptArmor.values().flatMap { listOf(it.degradedId) + if (it.name.startsWith("CORRUPT_")) listOf(it.newId) else emptyList() } +
                NexArmour.TORVA.flatMap { listOf(it.usedId, it.brokenId) } +
                listOf(Items.CORRUPT_MORRIGANS_JAVELIN, Items.C_MORRIGANS_THROWING_AXE)
        val missing = expectedSources.filter { it !in DeadmanGear.CONVERT }
        assertTrue(missing.isEmpty(), "not converted: $missing")
        fun base(name: String) =
            name.lowercase().replace("corrupt ", "").replace("c. ", "").replace(" (deg)", "").replace(" (broken)", "").replace("statius' ", "statius's ").trim()
        val wrong =
            DeadmanGear.CONVERT.mapNotNull { (from, to) ->
                val a = DEFINITIONS.get(ItemDef::class.java, from)
                val b = DEFINITIONS.get(ItemDef::class.java, to)
                if (b.noted) return@mapNotNull "$from -> $to noted"
                if (base(a.name) == base(b.name)) null else "$from '${a.name}' -> $to '${b.name}'"
            }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        // The normal items never convert further, and the Zaryte bow is not touched (it keeps degrading).
        assertTrue(DeadmanGear.NORMAL.none { it in DeadmanGear.CONVERT })
        assertNull(DeadmanGear.CONVERT[Items.ZARYTE_BOW_20173])
        assertEquals(Items.TORVA_PLATEBODY, DeadmanGear.normal(Items.TORVA_PLATEBODY_BROKEN))
        assertEquals(Items.ABYSSAL_WHIP, DeadmanGear.normal(Items.ABYSSAL_WHIP))
    }

    @Test
    fun `login migration converts every container, merges bank stacks and keeps the tabs`() {
        val player = player()
        // Inventory: a (deg) piece, a corrupt piece, a noted corrupt stack next to a noted normal stack, an untouched item.
        player.inventory[0] = Item(Items.VESTAS_LONGSWORD_DEG)
        player.inventory[1] = Item(Items.CORRUPT_STATIUSS_PLATEBODY)
        player.inventory[2] = Item(Items.CORRUPT_DRAGON_MED_HELM_NOTED, 3)
        player.inventory[3] = Item(Items.DRAGON_MED_HELM_NOTED, 2)
        player.inventory[4] = Item(Items.ABYSSAL_WHIP)
        player.inventory[5] = Item(Items.C_MORRIGANS_THROWING_AXE, 40)
        // Worn: used Torva, a normal Vesta's with a stale charge count.
        player.equipment[4] = Item(Items.TORVA_PLATEBODY_20141)
        player.equipment[3] = Item(Items.VESTAS_SPEAR).apply { attr[ItemAttribute.CHARGES] = 1234 }
        // Bank: (deg) next to the normal piece (merge), a (deg) alone (in place), a broken Torva.
        player.bank[0] = Item(Items.ZURIELS_ROBE_TOP, 1)
        player.bank[1] = Item(Items.ZURIELS_ROBE_TOP_DEG, 1)
        player.bank[2] = Item(Items.MORRIGANS_COIF_DEG, 1)
        player.bank[3] = Item(Items.TORVA_FULL_HELM_BROKEN, 1)
        player.gravestone[0] = Item(Items.CORRUPT_ZURIELS_STAFF_DEG)
        player.deathRecovery[0] = Item(Items.STATIUS_WARHAMMER_DEG)

        val changed = DeadmanGear.migrate(player)

        assertEquals(Items.VESTAS_LONGSWORD, player.inventory[0]?.id)
        assertEquals(Items.STATIUSS_PLATEBODY, player.inventory[1]?.id)
        assertNull(player.inventory[2], "the converted noted stack joined the existing noted stack")
        assertEquals(Item(Items.DRAGON_MED_HELM_NOTED, 5).let { it.id to it.amount }, player.inventory[3]!!.let { it.id to it.amount })
        assertEquals(Items.ABYSSAL_WHIP, player.inventory[4]?.id)
        assertEquals(Items.MORRIGANS_THROWING_AXE to 40, player.inventory[5]!!.let { it.id to it.amount })
        assertEquals(Items.TORVA_PLATEBODY, player.equipment[4]?.id)
        assertEquals(Items.VESTAS_SPEAR, player.equipment[3]?.id)
        assertTrue(player.equipment[3]!!.attr.isEmpty(), "stale charges removed")
        // Bank: merged into the existing robe top (2), closed up, coif and Torva in order.
        assertEquals(listOf(Items.ZURIELS_ROBE_TOP to 2, Items.MORRIGANS_COIF to 1, Items.TORVA_FULL_HELM to 1),
            (0 until 3).map { player.bank[it]!!.let { i -> i.id to i.amount } })
        assertNull(player.bank[3])
        assertEquals(Items.ZURIELS_STAFF, player.gravestone[0]?.id)
        assertEquals(Items.STATIUSS_WARHAMMER, player.deathRecovery[0]?.id)
        assertEquals(11, changed)
        // Running it again changes nothing.
        assertEquals(0, DeadmanGear.migrate(player))
        // The purge that follows at login keeps every converted (normal) item.
        assertEquals(0, RemovedItems.purge(player))
        assertEquals(Items.STATIUSS_PLATEBODY, player.inventory[1]?.id)
    }

    @Test
    fun `nothing offers a corrupt or degraded piece any more`() {
        // Grand Exchange (player offers and the house): RemovedItems.isUnobtainable.
        DeadmanGear.CONVERT.keys.forEach { id ->
            assertTrue(RemovedItems.isUnobtainable(DEFINITIONS.get(ItemDef::class.java, id)), "$id still obtainable")
        }
        DeadmanGear.NORMAL.forEach { id ->
            assertTrue(!RemovedItems.isUnobtainable(DEFINITIONS.get(ItemDef::class.java, id)), "normal $id must stay obtainable")
        }
        // Drop tables: no npc drops a corrupt piece.
        val npcs = Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content", "npcs").toFile()
        val corrupt = Regex("""\bItems\.(CORRUPT_(?!VINE)|C_(VESTAS|STATIUSS|MORRIGANS|DRAGON))""")
        val offenders = npcs.walkTopDown().filter { it.isFile && (it.name.endsWith(".kts") || it.name.endsWith(".kt")) }
            .filter { f -> f.readLines().any { corrupt.containsMatchIn(it) && !it.trimStart().startsWith("//") } }.map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "corrupt drops left in: $offenders")
    }

    @Test
    fun `the examine text is the OSRS Deadman Mode text, never the old degrading text`() {
        val yml = File(Paths.get("..", "..", "data", "cfg", "items.yml").toString()).readText()
        val examines = Regex("""\n- id: (\d+)\r?\n  name: "[^"]*"\r?\n  examine: "([^"]*)"""").findAll(yml).associate { it.groupValues[1].toInt() to it.groupValues[2] }
        val wrong = (Items.ZURIELS_ROBE_TOP..Items.CORRUPT_MORRIGANS_COIF_DEG).filter { examines[it]?.contains("degrades") == true }
        assertTrue(wrong.isEmpty(), "still the degrading examine: $wrong")
        assertEquals("A powerful longsword.", examines[Items.VESTAS_LONGSWORD])
        assertEquals("A powerful helmet.", examines[Items.STATIUSS_FULL_HELM])
        assertEquals("A powerful pair of leather chaps.", examines[Items.MORRIGANS_LEATHER_CHAPS])
        assertEquals("A vicious javelin.", examines[Items.MORRIGANS_JAVELIN])
        assertEquals("An ancient warrior's platebody.", examines[Items.TORVA_PLATEBODY])
    }

    private fun player(): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.varps } returns VarpSet((0 until 8000).toSet())
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        val bank = ItemContainer(DEFINITIONS, BANK_KEY)
        val gravestone = ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        val death = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.inventory } returns inventory
        every { player.equipment } returns equipment
        every { player.bank } returns bank
        every { player.gravestone } returns gravestone
        every { player.deathRecovery } returns death
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
