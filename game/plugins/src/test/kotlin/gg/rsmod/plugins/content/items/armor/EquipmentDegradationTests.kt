package gg.rsmod.plugins.content.items.armor

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
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
        val expected = BarrowsPiece.values.size * 5 + CorruptArmor.values().size * 2 + ChaoticWeapon.values().size + NexArmour.values().size * 2
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
            assertEquals(1 + 4 * BarrowsDegradation.STAGE_CYCLES to piece.idForStage(BarrowsPiece.BROKEN), lifetime(piece.baseId), piece.name)
            assertEquals(3 * BarrowsDegradation.STAGE_CYCLES to piece.idForStage(BarrowsPiece.BROKEN), lifetime(piece.idForStage(2)), "${piece.name} from 75")
        }
        CorruptArmor.values().forEach { armour ->
            assertEquals(1 + armour.maxCharges to null, lifetime(armour.newId), armour.name)
        }
        ChaoticWeapon.values().forEach { weapon ->
            assertEquals(ChaoticWeaponCharges.MAX_CHARGES to weapon.brokenId, lifetime(weapon.chargedId), weapon.name)
        }
        NexArmour.values().forEach { armour ->
            assertEquals(1 + DegradeTable.NEX_USED_CHARGES to armour.brokenId, lifetime(armour.pristineId), armour.name)
        }
    }

    @Test
    fun `saved charges continue and transitions carry the family message`() {
        val piece = BarrowsPiece.DHAROKS_HELM
        assertEquals(DegradeStep(4, null, false, null), DegradeTable.step(piece.idForStage(BarrowsPiece.FULL), 5, "Dharok's helm 100"))
        assertEquals(
            DegradeStep(null, piece.idForStage(BarrowsPiece.BROKEN), false, "<col=ff0000>Your Dharok's helm has degraded completely and needs repairing."),
            DegradeTable.step(piece.idForStage(4), 1, "Dharok's helm 25"),
        )
        assertEquals(DegradeStep(null, piece.idForStage(BarrowsPiece.FULL), false, null), DegradeTable.step(piece.baseId, null, "Dharok's helm"))
        val vesta = CorruptArmor.VESTAS_LONGSWORD
        assertEquals(DegradeStep(null, null, true, "<col=ff0000>Your Vesta's longsword (deg) has degraded into dust."), DegradeTable.step(vesta.degradedId, 1, "Vesta's longsword (deg)"))
        val torva = NexArmour.TORVA_PLATEBODY
        assertEquals(DegradeStep(null, torva.usedId, false, "Your Torva platebody degraded."), DegradeTable.step(torva.pristineId, null, "Torva platebody"))
        assertNull(DegradeTable.step(torva.brokenId, null, "Torva platebody (broken)"), "broken is terminal")
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
