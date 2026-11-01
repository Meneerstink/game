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
            PlayerHouse.POOL to mapOf("drink" to "player_house.plugin.kts"),
            13523 to mapOf("rub" to "player_house.plugin.kts"),
            PlayerHouse.NEXUS to mapOf("teleport" to "poh_furniture.plugin.kts", "teleport menu" to "poh_furniture.plugin.kts"),
            13715 to mapOf("repair" to "poh_furniture.plugin.kts"),
            PlayerHouse.JEWELLERY_BOX to listOf("teleport menu", JewelleryBoxRecent.OPTION.lowercase())
                .associateWith { "poh_furniture.plugin.kts" },
            // A22: the cache gained these options together with the Wilderness/house handlers.
            14826 to listOf("activate", "teleport to destination", "set destination")
                .associateWith { "wilderness_obelisk.plugin.kts" },
            PlayerHouse.SPIRITUAL_FAIRY_TREE to
                mapOf(
                    "tree" to "poh_furniture.plugin.kts",
                    "ring-zanaris" to "poh_furniture.plugin.kts",
                    "ring-configure" to "poh_furniture.plugin.kts",
                    "ring-last-destination" to "poh_furniture.plugin.kts",
                ),
            PlayerHouse.TABLE_SPACE to mapOf("build" to "poh_build.plugin.kts"),
            PlayerHouse.BELL_PULL to mapOf("ring" to "poh_build.plugin.kts", "remove" to "poh_build.plugin.kts"),
            PlayerHouse.THEME_SPACE to mapOf("build" to "poh_build.plugin.kts"),
            PlayerHouse.OTHERWORLDLY_THEME to mapOf("remove" to "poh_build.plugin.kts"),
        ) + PlayerHouse.OCCULT_ALTAR_VARIANTS.associateWith { OCCULT } +
            PohBuild.DINING_TABLES.associate { it.obj to mapOf("remove" to "poh_build.plugin.kts") }

    /** The Altar of the Occult variants: Venerate and the two other spellbooks. */
    private val OCCULT
        get() = listOf("venerate", "standard", "ancient", "lunar").associateWith { "poh_furniture.plugin.kts" }

    @Test
    fun `every option of every loc the house places has a route`() {
        val missing = mutableListOf<String>()
        // A multiloc (the Altar of the Occult) shows its variants' menus, so those are checked instead of the empty base.
        val shown = PlayerHouse.PLACED_OBJECTS.flatMap { id -> DEFINITIONS.get(ObjectDef::class.java, id).transforms?.filter { it >= 0 }?.distinct() ?: listOf(id) }
        shown.forEach { id ->
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
        val obelisk = files.getValue("wilderness_obelisk.plugin.kts").readText()
        routes.getValue(14826).keys.forEach { option ->
            assertTrue("on_obj_option(obeliskID, option = \"$option\")" in obelisk, "house obelisk: $option")
        }
        listOf("tree", "ring-configure", "ring-zanaris", "ring-last-destination").forEach {
            assertTrue("on_obj_option(obj = PlayerHouse.SPIRITUAL_FAIRY_TREE, option = \"$it\")" in furniture, it)
        }
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
