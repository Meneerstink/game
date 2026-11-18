package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import io.mockk.*
import java.io.File
import kotlin.test.*

class ClueNpcAssetsTests {
    private val profiles = JsonParser().parse(File(RspsPaths.data("cfg/clue/npc-assets.json")).readText()).asJsonObject
    private val mapping = profiles.entrySet().mapIndexed { index, row -> row.key.toInt() to (30000 + index) }.toMap()
    private val text get() = mapping.entries.joinToString("\n", "# NPC fixture; applied=true\n", "\n") { "npc\t${it.key}\t${it.value}" }

    @Test fun `unapplied partial duplicate or colliding NPC plans cannot create runtime bindings`() {
        assertEquals(mapping, ClueNpcs.parse(text))
        assertFailsWith<IllegalArgumentException> { ClueNpcs.parse(text.replace("applied=true", "applied=false")) }
        assertFailsWith<IllegalArgumentException> { ClueNpcs.parse(text + "npc\t7306\t29999\n") }
        assertFailsWith<IllegalArgumentException> { ClueNpcs.parse(text.replace("\t30001", "\t30000")) }
        assertFailsWith<IllegalArgumentException> { ClueNpcs.parse(text.lines().dropLast(2).joinToString("\n")) }
    }

    @Test fun `mapping from an applied import still requires correct loaded menus portraits and source identities`() {
        val definitions = mapping.mapValues { (source, id) -> profiles[source.toString()].asJsonObject.let { row ->
            NpcDef(id).also {
                it.name = row["name"].asString; it.combatLevel = row["level"].asInt; it.basId = 1
                it.options = row["options"].asJsonArray.map { value -> if (value.isJsonNull) null else value.asString }.toTypedArray()
                it.chatheadModels = IntArray(row["heads"].asInt)
            }
        } }.entries.associate { it.value.id to it.value }
        val defs = mockk<DefinitionSet>()
        every { defs.get(NpcDef::class.java, any()) } answers { definitions.getValue(secondArg()) }
        val world = mockk<World>()
        every { world.definitions } returns defs
        ClueNpcs.validate(world, mapping)
        val falo = definitions.getValue(mapping.getValue(7306))
        falo.options[0] = "Attack"
        assertFailsWith<IllegalStateException> { ClueNpcs.validate(world, mapping) }
        falo.options[0] = "Talk-to"; falo.chatheadModels = null
        assertFailsWith<IllegalStateException> { ClueNpcs.validate(world, mapping) }
        falo.chatheadModels = intArrayOf(1); falo.name = "Another bard"
        assertFailsWith<IllegalStateException> { ClueNpcs.validate(world, mapping) }
    }
}
