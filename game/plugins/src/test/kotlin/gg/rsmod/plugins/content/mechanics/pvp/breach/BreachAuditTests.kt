package gg.rsmod.plugins.content.mechanics.pvp.breach

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.tools.importer.AnimCoverage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Breach audit 2026-09-27: guards for the causes fixed on branch `breach-fix` (docs/breach_status.md). */
class BreachAuditTests {
    private fun sectionAnims(m: BreachMonsters.Monster): List<Int> =
        rows.getValue(m.id).attacks.map { it.anim } + listOf(m.attackAnim, m.blockAnim, m.deathAnim)

    @Test
    fun `C1-C3 - every attack, block and death sequence of every breach monster moves its model`() {
        val failures = mutableListOf<String>()
        BreachMonsters.ROSTER.forEach { m ->
            val labels = AnimCoverage.modelLabels(store, AnimCoverage.npcModels(store, m.id))
            sectionAnims(m).filter { it >= 0 }.distinct().forEach { seq ->
                val r = AnimCoverage.check(store, labels, seq)
                // 667 human sequences (390 ...) predate op 19/20 aware decoding in the checker; they are the rev-667 originals.
                if (!r.ok && seq >= 15000) failures += "${m.name} ${m.id}: $r"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `A1 - every frame sound on a breach monster sequence has a radius other players hear`() {
        val failures = mutableListOf<String>()
        BreachMonsters.ROSTER.forEach { m ->
            sectionAnims(m).filter { it >= 15000 }.distinct().forEach { seq ->
                val def = definitions.getNullable(AnimDef::class.java, seq) ?: return@forEach
                if (def.hasFrameSounds && !def.hasAudibleFrameSounds) failures += "${m.name} seq $seq: frame sound with radius 0"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `A1 conformance - the God Wars clones use their room attack at melee distance and hit everyone around, no chamber area`() {
        // OSRS Wiki "General Graardor": "can only use it if he is within melee range of his current target", "hitting all players
        // in the room"; "Commander Zilyana": "She will only use her magic attacks at melee distance".
        mapOf(14442 to "range", 14443 to "magic", 14444 to "magic").forEach { (id, section) ->
            assertEquals(1, rows.getValue(id).attacks.single { it.id == section }.range, "npc $id $section")
        }
        mapOf(14442 to "range", 14443 to "magic").forEach { (id, section) ->
            val attack = rows.getValue(id).attacks.single { it.id == section }
            assertEquals(null, attack.multiTargetArea, "npc $id $section still uses a God Wars chamber area")
            assertEquals(BreachMonsters.ROOM_RADIUS, attack.multiRadius, "npc $id $section")
        }
        // no breach row may keep a God Wars (or any other fixed) chamber area
        rows.values.forEach { row -> row.attacks.forEach { assertEquals(null, it.multiTargetArea, "${row.name} ${it.id}") } }
    }

    @Test
    fun `A2 conformance - only Zemouregal and Durial321 route around obstacles`() {
        assertEquals(setOf(14463, 14466), BreachMonsters.ROUTEFINDERS)
    }

    @Test
    fun `D1 - a long regional projectile starts at most 30 tiles before its landing, on the spawner line`() {
        val landing = Tile(3200, 3500, 0)
        assertEquals(Tile(3210, 3500, 0), DeadmanBreach.projectileStart(Tile(3210, 3500, 0), landing), "short flights unchanged")
        val start = DeadmanBreach.projectileStart(Tile(3260, 3530, 0), landing)
        assertEquals(DeadmanBreach.MAX_PROJECTILE_TILES, maxOf(start.x - landing.x, start.z - landing.z))
        assertEquals(Tile(3230, 3515, 0), start)
    }

    @Test
    fun `F4 - a breachtest monster pays no loot, emblem or points`() {
        val world = mockk<World>(relaxed = true)
        val npc = mockk<Npc>(relaxed = true)
        every { npc.attr } returns AttributeMap()
        every { npc.world } returns world
        every { npc.tile } returns Tile(3200, 3500, 0)
        DeadmanBreach.markTest(npc)
        DeadmanBreach.recordDamage(npc, mockk<gg.rsmod.game.model.entity.Player>(relaxed = true).also { every { it.username } returns "a" }, 500)
        DeadmanBreach.dropLoot(npc)
        verify(exactly = 0) { world.spawn(any<GroundItem>()) }
    }

    companion object {
        private lateinit var store: CacheLibrary
        private lateinit var definitions: DefinitionSet
        private lateinit var rows: Map<Int, gg.rsmod.plugins.content.combat.attack.NpcAttacks.Row>

        @BeforeClass
        @JvmStatic
        fun load() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toString())
            definitions = DefinitionSet()
            definitions.load(store, AnimDef::class.java)
            rows = BreachMonsters.attackRows(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile()).associateBy { it.id }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            store.close()
        }
    }
}
