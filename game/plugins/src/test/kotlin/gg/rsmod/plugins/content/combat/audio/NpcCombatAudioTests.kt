package gg.rsmod.plugins.content.combat.audio

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RCV-005 NPC audio root cause: the shared NPC combat path had no audio data or dispatch, so only
 * npcs with a hand-written table or script made a sound. These tests prove the one shared table
 * (`data/cfg/npcs/combat-sounds.json`, generated from Void by `tools/npc-combat-sounds/generate.js`)
 * against the real rev-667 cache for every row, the Void dispatch rules for attack/defend/death, and
 * that no hand-written death block still plays a second sound for an npc the table covers.
 */
class NpcCombatAudioTests {
    private val tablePath = Paths.get("..", "..", "data", "cfg", "npcs", "combat-sounds.json").toFile()

    private fun loadReal() = NpcCombatAudio.load(tablePath)

    private fun loadRows(json: String) {
        val file = File.createTempFile("npc-combat-sounds", ".json").apply { deleteOnExit() }
        file.writeText(json)
        NpcCombatAudio.load(file)
    }

    @Test
    fun `every row names its 667 npc and every sound id exists in the synth sound index`() {
        assertTrue(loadReal() > 0)
        val failures = mutableListOf<String>()
        NpcCombatAudio.rows().forEach { row ->
            val def = definitions.getNullable(NpcDef::class.java, row.id)
            if (def == null || def.name != row.name) failures += "npc ${row.id} (${row.voidKey}): cache name '${def?.name}' != '${row.name}'"
            val ids = row.attack.map { it.id } + listOf(row.defend, row.death).filter { it >= 0 }
            ids.forEach { id ->
                val data = store.data(SYNTH_SOUNDS_INDEX, id, 0)
                if (data == null || data.isEmpty()) failures += "npc ${row.id} (${row.voidKey}): sound $id absent from the cache"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `table rows follow the Void dispatch rules`() {
        loadReal()
        val failures = mutableListOf<String>()
        NpcCombatAudio.rows().forEach { row ->
            row.attack.forEach { s ->
                if (s.on != "npc" && s.on != "target") failures += "${row.id}: on=${s.on}"
                if (s.radius !in 0..15) failures += "${row.id}: radius ${s.radius}"
                // Void Attack.kt: an attacker sound without a radius is Character.sound, a no-op for an npc.
                if (s.on == "npc" && s.radius <= 0) failures += "${row.id}: npc sound without radius"
            }
            if (row.attackBlocked != null && row.attack.isNotEmpty()) failures += "${row.id}: blocked row carries an attack sound"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `God Wars bodyguards and the King Black Dragon match Void`() {
        loadReal()
        // Void bandos.combat.toml: [sergeant_strongstack] goblin_defend/goblin_death, melee target_sound goblin_attack.
        NpcCombatAudio.rowFor(Npcs.SERGEANT_STRONGSTACK)!!.let {
            assertEquals(listOf(469 to "target"), it.attack.map { s -> s.id to s.on })
            assertEquals(472, it.defend)
            assertEquals(471, it.death)
        }
        // [sergeant_steelwill] magic target_sound sergeant_steelwill_attack (direct, not area);
        // death_sound sergeant_steelwill_death has no id in any Void sounds.toml, so none is sent.
        NpcCombatAudio.rowFor(Npcs.SERGEANT_STEELWILL)!!.let {
            assertEquals(listOf(Triple(3870, 0, "target")), it.attack.map { s -> Triple(s.id, s.radius, s.on) })
            assertEquals(3874, it.defend)
            assertEquals(-1, it.death)
        }
        // [king_black_dragon]: several attack sections with different sounds -> its script owns attack audio.
        NpcCombatAudio.rowFor(Npcs.KING_BLACK_DRAGON)!!.let {
            assertTrue(it.attack.isEmpty())
            assertNotNull(it.attackBlocked)
            assertEquals(410, it.defend)
            assertEquals(409, it.death)
        }
    }

    private class Fixture(npcId: Int) {
        val world = mockk<World>(relaxed = true)
        val npc = mockk<Npc>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sounds = mutableListOf<Int>()
        val areas = mutableListOf<AreaSound>()

        init {
            every { npc.id } returns npcId
            every { npc.world } returns world
            every { npc.attr } returns AttributeMap()
            every { npc.tile } returns Tile(3200, 3200, 0)
            every { player.world } returns world
            every { player.tile } returns Tile(3201, 3200, 0)
            every { world.spawn(any<AreaSound>()) } answers { areas += firstArg<AreaSound>() }
            every { player.write(*varargAll<Message> { m -> (m as? SynthSoundMessage)?.let { sounds += it.sound }; true }) } just Runs
        }
    }

    private val dispatchRows =
        """
        [
         {"id": 1, "attack": [{"id": 469, "radius": 0, "on": "target"}], "defend": 472, "death": 471},
         {"id": 2, "attack": [{"id": 3877, "radius": 10, "on": "npc"}], "defend": -1, "death": -1}
        ]
        """.trimIndent()

    @Test
    fun `attack sound reaches the player target once per tick`() {
        loadRows(dispatchRows)
        val f = Fixture(npcId = 1)
        every { f.world.currentCycle } returns 7
        NpcCombatAudio.onAttack(f.npc, f.player)
        NpcCombatAudio.onAttack(f.npc, f.player) // second hit of the same attack in the same tick
        assertEquals(listOf(469), f.sounds)

        every { f.world.currentCycle } returns 8
        NpcCombatAudio.onAttack(f.npc, f.player)
        assertEquals(listOf(469, 469), f.sounds)
    }

    @Test
    fun `an npc sound with a radius is an area sound at the npc`() {
        loadRows(dispatchRows)
        val f = Fixture(npcId = 2)
        NpcCombatAudio.onAttack(f.npc, f.player)
        assertEquals(1, f.areas.size)
        assertEquals(3877, f.areas[0].id)
        assertEquals(10, f.areas[0].radius)
        assertEquals(Tile(3200, 3200, 0), f.areas[0].tile)
        assertTrue(f.sounds.isEmpty())
    }

    @Test
    fun `defend reaches only an attacking player and death reaches the killer`() {
        loadRows(dispatchRows)
        val f = Fixture(npcId = 1)
        NpcCombatAudio.onDefend(f.player, f.npc)
        NpcCombatAudio.onDefend(mockk<Npc>(relaxed = true), f.npc) // npc attacker: Void Character.sound is a no-op
        NpcCombatAudio.onDeath(f.player, f.npc)
        assertEquals(listOf(472, 471), f.sounds)
        assertTrue(NpcCombatAudio.hasDeathSound(1))
        assertTrue(!NpcCombatAudio.hasDeathSound(2))
        assertNull(NpcCombatAudio.rowFor(3))
    }

    private fun npcConst(name: String): Int? = runCatching { Npcs::class.java.getField(name).getInt(null) }.getOrNull()

    private fun resolve(
        text: String,
        expr: String,
        depth: Int = 0,
    ): List<Int>? {
        val e = expr.trim().removePrefix("*")
        if (depth > 6 || e.isEmpty()) return null
        if (e.contains('+')) return e.split('+').map { resolve(text, it, depth + 1) ?: return null }.flatten()
        e.toIntOrNull()?.let { return listOf(it) }
        if (e.startsWith("Npcs.")) return npcConst(e.removePrefix("Npcs."))?.let { listOf(it) }
        Regex("""^intArrayOf\(([\s\S]*)\)$""").find(e)?.let { m ->
            return m.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { resolve(text, it, depth + 1) ?: return null }.flatten()
        }
        if (Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(e)) {
            val decls = Regex("""\bva[lr] $e\s*=\s*(intArrayOf\([\s\S]*?\)|[^\n]+)""").findAll(text).toList()
            if (decls.size != 1) return null
            return resolve(text, decls[0].groupValues[1], depth + 1)
        }
        return null
    }

    @Test
    fun `no hand-written death block plays a second sound for an npc the shared table covers`() {
        loadReal()
        val content = File("src/main/kotlin/gg/rsmod/plugins/content")
        val block = Regex("""on_npc_(?:pre_)?death\(([^)]*)\)\s*\{([\s\S]*?)\n}""")
        val failures = mutableListOf<String>()
        content.walk().filter { it.isFile && it.name.endsWith(".kts") }.forEach { file ->
            val text = file.readText()
            block.findAll(text).forEach { m ->
                val body = m.groupValues[2]
                if (!body.contains("playSound(") || body.contains("hasDeathSound")) return@forEach
                val ids = resolve(text, m.groupValues[1].split(',').joinToString("+"))
                if (ids == null) {
                    failures += "${file.name}: unresolvable death block (${m.groupValues[1]}) plays a sound without NpcCombatAudio.hasDeathSound"
                } else {
                    val covered = ids.filter { NpcCombatAudio.hasDeathSound(it) }
                    if (covered.isNotEmpty()) failures += "${file.name}: death sound also played by the shared table for $covered"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    companion object {
        private const val SYNTH_SOUNDS_INDEX = 4
        private val definitions = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
        }

        @AfterClass
        @JvmStatic
        fun closeCache() {
            store.close()
        }
    }
}
