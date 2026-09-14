package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "granitemaul": the ornate-handle maul matches the Granite maul and carries the special bar. */
class OsrsGraniteMaulImportTests {
    @Test
    fun `the ornate handle maul has the Granite maul stats and requirements`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in setOf(Items.GRANITE_MAUL, Items.GRANITE_MAUL_ORNATE_HANDLE, Items.ORNATE_MAUL_HANDLE) }.associateBy { it.path("id").asInt() }
        val base = yml.getValue(Items.GRANITE_MAUL).path("equipment")
        val ornate = yml.getValue(Items.GRANITE_MAUL_ORNATE_HANDLE).path("equipment")
        // Wiki: "+81" crush, "+79" strength, speed 7, 50 Attack and 50 Strength for both versions.
        listOf(base, ornate).forEach { eq ->
            assertEquals(81, eq.path("attack_crush").asInt())
            assertEquals(79, eq.path("melee_strength").asInt())
            assertEquals(7, eq.path("attack_speed").asInt())
            assertEquals(10, eq.path("weapon_type").asInt())
            assertEquals(mapOf(0 to 50, 2 to 50), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        }
        assertTrue(yml.getValue(Items.ORNATE_MAUL_HANDLE).path("equipment").isNull, "the handle is not wearable")
    }

    @Test
    fun `the cache gives the ornate handle maul the 667 maul class, the special bar and Revert`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            val def = definitions.get(ItemDef::class.java, Items.GRANITE_MAUL_ORNATE_HANDLE)
            assertEquals("Granite maul", def.name)
            assertEquals(27, def.params[644])
            assertEquals(10, def.params[686])
            assertEquals(1, def.params[687], "special attack bar")
            assertTrue("Revert" in def.inventoryMenu.toList())
            assertEquals("Ornate maul handle", definitions.get(ItemDef::class.java, Items.ORNATE_MAUL_HANDLE).name)
        } finally {
            store.close()
        }
    }
}
