package gg.rsmod.plugins.content.areas.poh

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-24: "poh alle objecten werken niet". The house placed locs whose cache options had no route (spirit tree Talk-to /
 * Inspect / Guide, lectern and infernal chart Study), so every click there said "Nothing interesting happens". This guard reads the
 * real options of every loc [PlayerHouse] places and demands a known route for each; a new house object fails here until it has one.
 */
class PohObjectRouteTests {
    /** loc -> (option -> the source file that binds it). */
    private val routes: Map<Int, Map<String, String>> =
        mapOf(
            13199 to mapOf("pray" to "player_house.plugin.kts"),
            13629 to mapOf("enter" to "player_house.plugin.kts"),
            13631 to mapOf("enter" to "player_house.plugin.kts"),
            13632 to mapOf("enter" to "player_house.plugin.kts"),
            13405 to mapOf("enter" to "player_house.plugin.kts"),
            13480 to mapOf("drink" to "player_house.plugin.kts"),
            13523 to mapOf("rub" to "player_house.plugin.kts"),
            13639 to mapOf("direct-portal" to "poh_furniture.plugin.kts", "scry" to "poh_furniture.plugin.kts"),
            12003 to mapOf("use" to "poh_furniture.plugin.kts"),
            13715 to mapOf("repair" to "poh_furniture.plugin.kts"),
            40173 to mapOf("teleport" to "poh_furniture.plugin.kts"),
            13658 to mapOf("observe" to "poh_furniture.plugin.kts"),
            13599 to mapOf("search" to "poh_furniture.plugin.kts"),
            13648 to mapOf("study" to "poh_furniture.plugin.kts"),
            13664 to mapOf("study" to "poh_furniture.plugin.kts"),
            14826 to mapOf("activate" to "wilderness_obelisk.plugin.kts"),
            8355 to mapOf("talk-to" to "spirit_tree.plugin.kts", "inspect" to "spirit_tree.plugin.kts", "guide" to "spirit_tree.plugin.kts", "teleport" to "spirit_tree.plugin.kts"),
            18771 to mapOf("search" to "poh_furniture.plugin.kts"),
            18796 to mapOf("open" to "poh_furniture.plugin.kts"),
            18802 to mapOf("open" to "poh_furniture.plugin.kts"),
            18808 to mapOf("open" to "poh_furniture.plugin.kts"),
            18776 to mapOf("open" to "poh_furniture.plugin.kts"),
            18782 to mapOf("open" to "poh_furniture.plugin.kts"),
        )

    @Test
    fun `every option of every loc the house places has a route`() {
        val missing = mutableListOf<String>()
        PlayerHouse.PLACED_OBJECTS.forEach { id ->
            val def = DEFINITIONS.get(ObjectDef::class.java, id)
            def.options.filterNotNull().filter { it.isNotBlank() && it.lowercase() != "examine" }.forEach { option ->
                if (routes[id]?.containsKey(option.lowercase()) != true) missing += "$id ${def.name}: $option"
            }
        }
        assertEquals(emptyList(), missing, "house locs with an unrouted option")
    }

    @Test
    fun `the listed routes exist in their files`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins/content")
        val files = root.walkTopDown().filter { it.isFile }.associateBy { it.name }
        routes.values.flatMap { it.values }.toSet().forEach { name -> assertTrue(files.containsKey(name), "$name missing") }
        val furniture = files.getValue("poh_furniture.plugin.kts").readText()
        assertTrue("on_obj_option(obj = Objs.LECTERN_13648, option = \"study\")" in furniture)
        assertTrue("on_obj_option(obj = Objs.INFERNAL_CHART_13664, option = \"study\")" in furniture)
        val tree = files.getValue("spirit_tree.plugin.kts").readText()
        listOf("Talk-to", "Guide", "Inspect").forEach { assertTrue("on_obj_option(obj = Objs.SPIRIT_TREE_8355, option = \"$it\")" in tree, it) }
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
