package gg.rsmod.plugins.content.npcs.bulk

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.ext.NPC_STRENGTH_BONUS_INDEX
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Loads the real `data/cfg/npcs/combat-defs.json` against the real rev-667 cache. This is the
 * whole-roster check the project's completeness rule asks for: every row must be a cache npc,
 * every source lifepoints value must be real HP * 10 and is converted to 1:1 runtime HP, every animation
 * left on a definition must exist in the cache, and the table must actually cover the
 * attackable npcs it was generated for rather than a handful of exemplars.
 */
class BulkNpcCombatDefsTests {
    @Test
    fun `the whole generated table loads against the real cache`() {
        val result = BulkNpcCombatDefs.load(definitions, TABLE)

        assertEquals(0, result.skippedUnknownNpc, "every row must refer to an npc id present in the 667 cache")
        assertTrue(result.defs.size > 4000, "expected the table to cover thousands of attackable npcs, got ${result.defs.size}")

        val badLifepoints = result.defs.filter { (_, def) -> def.lifepoints <= 0 }
        assertTrue(badLifepoints.isEmpty(), "runtime lifepoints must be positive: ${badLifepoints.keys.take(10)}")

        val badAnims =
            result.defs.filter { (_, def) ->
                listOf(def.attackAnimation, def.blockAnimation, *def.deathAnimation.toTypedArray())
                    .any { it > -1 && definitions.getNullable(AnimDef::class.java, it) == null }
            }
        assertTrue(badAnims.isEmpty(), "animation ids must exist in the cache: ${badAnims.keys.take(10)}")

        val badStats = result.defs.filter { (_, def) -> def.stats.size != 5 || def.stats.any { it < 1 } }
        assertTrue(badStats.isEmpty(), "every stat must be at least 1: ${badStats.keys.take(10)}")
    }

    @Test
    fun `sourced rows reproduce the donor max hit through this codebase's melee formula`() {
        // Fire giant (110): OSRS/2007 stats hp 111, str 65, max hit 11; Matrix anims 4652/4651/4653.
        val def = BulkNpcCombatDefs.load(definitions, TABLE).defs.getValue(110)
        assertEquals(111, def.lifepoints)
        assertEquals(65, def.stats[NpcSkills.STRENGTH])
        assertEquals(4652, def.attackAnimation)
        assertEquals(4651, def.blockAnimation)
        assertEquals(listOf(4653), def.deathAnimation)
        assertEquals(StyleType.SLASH, def.attackStyleType)
        assertTrue(NpcSpecies.FIERY in def.species)
        assertTrue(def.aggressiveRadius > 0, "OSRS marks the Fire giant aggressive")

        val strength = def.stats[NpcSkills.STRENGTH].toDouble()
        val bonus = def.bonuses[NPC_STRENGTH_BONUS_INDEX]
        val maxHit = Math.floor(0.5 + strength * (bonus + 64.0) / 640.0).toInt()
        assertEquals(11, maxHit, "strength bonus must be solved so the formula yields the sourced max hit")
    }

    @Test
    fun `hand-written definitions are not in the table`() {
        // Kree'arra, Barrows brothers and the Ardougne roster have set_combat_def blocks; the generator
        // must leave them to those blocks so a fallback can never shadow curated data.
        val defs = BulkNpcCombatDefs.load(definitions, TABLE).defs
        listOf(6222, 6260, 50, 2030).forEach { id ->
            assertTrue(id !in defs, "npc $id has a hand-written combat def and must not appear in the bulk table")
        }
    }

    @Test
    fun `animation ids absent from the cache are dropped, not passed through`() {
        val rows = listOf(row(id = 110, attackAnim = 999_999, blockAnim = -1, deathAnim = 4653))
        val result = BulkNpcCombatDefs.build(rows, definitions)
        val def = result.defs.getValue(110)
        assertEquals(-1, def.attackAnimation)
        assertEquals(listOf(4653), def.deathAnimation)
        assertEquals(1, result.droppedAnimations)
    }

    @Test
    fun `rows for npc ids missing from the cache are skipped`() {
        val result = BulkNpcCombatDefs.build(listOf(row(id = 999_999)), definitions)
        assertTrue(result.defs.isEmpty())
        assertEquals(1, result.skippedUnknownNpc)
    }

    @Test
    fun `a lifepoints value that is not real HP times ten is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BulkNpcCombatDefs.build(listOf(row(id = 110, lifepoints = 111)), definitions)
        }
    }

    @Test
    fun `magic style npcs without a spell use their magic level for the melee-strategy attack roll`() {
        val def = BulkNpcCombatDefs.build(listOf(row(id = 110, style = "MAGIC", attack = 1, magic = 90)), definitions).defs.getValue(110)
        assertEquals(90, def.stats[NpcSkills.ATTACK])
        assertEquals(90, def.stats[NpcSkills.MAGIC])
    }

    private fun row(
        id: Int,
        lifepoints: Int = 100,
        attack: Int = 10,
        magic: Int = 1,
        style: String = "CRUSH",
        attackAnim: Int = -1,
        blockAnim: Int = -1,
        deathAnim: Int = -1,
    ) = BulkNpcCombatDefs.Row(
        id = id,
        name = "test",
        lifepoints = lifepoints,
        attack = attack,
        strength = 10,
        defence = 10,
        magic = magic,
        ranged = 1,
        style = style,
        attackAnim = attackAnim,
        blockAnim = blockAnim,
        deathAnim = deathAnim,
        source = "test",
    )

    companion object {
        private val TABLE = Paths.get("..", "..", "data", "cfg", "npcs", "combat-defs.json").toFile()

        private val definitions = DefinitionSet()

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
            assertNotEquals(0, definitions.getCount(NpcDef::class.java))
            assertNotEquals(0, definitions.getCount(AnimDef::class.java))
            assertTrue(TABLE.exists(), "missing ${TABLE.absolutePath}")
        }
    }
}
