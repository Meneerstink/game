package gg.rsmod.plugins.content.items.armor

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.removed.RemovedItems
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RCV-011 Q-019: one degrade table for every combat-degrading worn item, proven over the whole roster. */
class EquipmentDegradationTests {
    /** Ticks an item from [start] until it stops degrading; returns ticks and the final id (null = dust). */
    private fun lifetime(start: Int): Pair<Int, Int?> {
        var id = start
        var charges: Int? = null
        var ticks = 0
        while (true) {
            val step = DegradeTable.step(id, charges, "x") ?: return ticks to id
            ticks++
            if (step.charges != null) {
                charges = step.charges
            } else if (step.destroy) {
                return ticks to null
            } else {
                id = step.replaceWith!!
                charges = null
            }
        }
    }

    @Test
    fun `table covers every family once and ids are unique`() {
        // OSRS-IMPORT moons: nine moon armour pieces, each new -> degraded -> broken.
        val expected =
            // Barrows, Moon armour and the Amulet of the damned left this table for the OSRS points (CombatPoints, owner 2026-10-04).
            // Owner 2026-10-11 (OSRS Deadman Mode, DeadmanGear): Vesta/Statius/Zuriel/Morrigan never degrade (corrupt is removed) and no Torva rows (OSRS Torva never degrades); the Zaryte bow (and the removed Pernix/Virtus) keep theirs.
            ChaoticWeapon.values().size + (NexArmour.values().size - NexArmour.TORVA.size) * 2
        assertEquals(9, MoonArmour.values().size)
        assertEquals(expected, DegradeTable.rows.size)
        assertEquals(DegradeTable.rows.size, DegradeTable.rows.map { it.id }.toSet().size, "one row per item id")
        assertEquals(10, NexArmour.values().size)
        assertNull(DegradeTable.step(4151, null, "Abyssal whip"), "items outside the table never degrade")
    }

    @Test
    fun `every row and its next item exist in the 667 cache as the same item`() {
        fun base(name: String) = name.lowercase().replace(Regex("\\b(deg|degraded|broken|used|\\d+)\\b"), "").replace(Regex("[^a-z]"), "")
        fun sub(
            small: String,
            big: String,
        ): Boolean {
            var i = 0
            for (c in big) if (i < small.length && small[i] == c) i++
            return i == small.length
        }
        val wrong =
            DegradeTable.rows.mapNotNull { row ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, row.id) ?: return@mapNotNull "${row.id} absent"
                if (def.noted) return@mapNotNull "${row.id} noted"
                if (row.next == DegradeRow.DESTROY) return@mapNotNull null
                val next = DEFINITIONS.getNullable(ItemDef::class.java, row.next) ?: return@mapNotNull "${row.id} -> ${row.next} absent"
                val a = base(def.name)
                val b = base(next.name)
                if (next.noted) "${row.id} -> ${row.next} noted" else if (sub(a, b) || sub(b, a)) null else "${row.id} '${def.name}' -> ${row.next} '${next.name}'"
            }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        NexArmour.values().forEach {
            assertTrue(DEFINITIONS.get(ItemDef::class.java, it.brokenId).name.lowercase().contains("broken"), "${it.name} broken id")
            assertEquals(it.pristineId + 2, it.usedId)
            assertEquals(it.pristineId + 3, it.brokenId)
        }
    }

    @Test
    fun `lifetimes of every family match their source values`() {
        BarrowsPiece.values.forEach { piece ->
            assertEquals(null, DegradeTable.rowFor(piece.baseId), "${piece.name}: OSRS points (CombatPoints)")
        }
        // N457: corrupt has no charge handling; retained Deadman gear still never degrades.
        (DeadmanGear.CONVERT.keys + DeadmanGear.NORMAL + RemovedItems.CORRUPT_IDS).forEach { id ->
            assertEquals(0 to id, lifetime(id), "$id")
        }
        ChaoticWeapon.values().forEach { weapon ->
            assertEquals(ChaoticWeaponCharges.MAX_CHARGES to weapon.brokenId, lifetime(weapon.chargedId), weapon.name)
        }
        NexArmour.values().forEach { armour ->
            if (armour in NexArmour.TORVA) {
                assertEquals(0 to armour.pristineId, lifetime(armour.pristineId), "${armour.name}: OSRS Torva never degrades")
            } else {
                assertEquals(1 + DegradeTable.NEX_USED_CHARGES to armour.brokenId, lifetime(armour.pristineId), armour.name)
            }
        }
        // "3000 degradable points": one point on entering combat, the other 2,999 at 90 combat ticks each.
        MoonArmour.values().forEach { armour ->
            assertEquals(null, DegradeTable.rowFor(armour.newId), "${armour.name}: OSRS points (CombatPoints)")
        }
    }

    @Test
    fun `saved charges continue and transitions carry the family message`() {
        val piece = BarrowsPiece.DHAROKS_HELM
        val stage25 = gg.rsmod.game.model.item.Item(piece.idForStage(4), 1).apply { attr[gg.rsmod.game.model.item.ItemAttribute.DEGRADE_POINTS] = 1 }
        val spent = CombatPoints.spend(stage25)!!
        assertEquals(piece.idForStage(BarrowsPiece.BROKEN), spent.item!!.id)
        assertEquals("<col=ff0000>Your Dharok's helm has degraded completely and needs repairing.", spent.message)
        assertEquals(piece.idForStage(BarrowsPiece.FULL), CombatPoints.spend(gg.rsmod.game.model.item.Item(piece.baseId, 1))!!.item!!.id)
        // Owner 2026-10-11: Vesta's and Torva no longer have a degrade step at all.
        assertNull(DegradeTable.step(Items.VESTAS_LONGSWORD, null, "Vesta's longsword"))
        assertNull(DegradeTable.step(NexArmour.TORVA_PLATEBODY.pristineId, null, "Torva platebody"))
        val bow = NexArmour.ZARYTE_BOW
        assertEquals(DegradeStep(null, bow.usedId, false, "Your Zaryte bow degraded."), DegradeTable.step(bow.pristineId, null, "Zaryte bow"))
        assertNull(DegradeTable.step(bow.brokenId, null, "Zaryte bow (broken)"), "broken is terminal")
    }

    @Test
    fun `no per-family degrade timer is left besides the shared one`() {
        val dir = Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content", "items", "armor").toFile()
        val timers = dir.listFiles { f: File -> f.name.endsWith(".kt") }!!.filter { it.readText().contains("TimerKey()") }.map { it.name }
        assertEquals(listOf("EquipmentDegradation.kt"), timers)
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..", "..", "data", "cache").toString())
            DEFINITIONS.loadAll(LIBRARY)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
