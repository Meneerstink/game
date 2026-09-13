package gg.rsmod.plugins.content.areas.godwars

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.tools.importer.ObjectPlacementProbeTool
import gg.rsmod.plugins.api.cfg.Objs
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.assertTrue

/**
 * RCV-010 D8 root cause (Frozen door unbound because the donor id 75089 is not a 667 object): every object id bound by
 * the God Wars plugins is checked against the real 667 map placements (or a varbit/varp transform child of a placed
 * object) in BOTH the production cache and the pristine openrs2 667 cache. The bound roster is read from the plugin
 * sources, not hand-listed.
 *
 * - Placed in the pristine 667 map but not in production → production cache damage → fails.
 * - Absent from both maps → the object does not exist on the revision-667 map; its binding must say so with a
 *   `REVISION_ABSENT` note in the plugin source, otherwise the test fails.
 */
class GodWarsCacheProbeTests {
    private fun reachable(cache: String, xtea: String): Set<Int> {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer))
        try {
            ObjectPlacementProbeTool.main(arrayOf(cache, xtea, "name", ""))
        } finally {
            System.setOut(original)
        }
        val defs = DefinitionSet().also { it.loadAll(CacheLibrary(cache)) }
        val placed = Regex("""PLACEMENT id=(\d+) """).findAll(buffer.toString()).map { it.groupValues[1].toInt() }.toSet()
        return placed + placed.flatMap { id -> defs.getNullable(ObjectDef::class.java, id)?.transforms?.filter { it >= 0 } ?: emptyList() }
    }

    @Test
    fun `every object the God Wars plugins bind is reachable on the 667 map`() {
        val sources = File("src/main/kotlin/gg/rsmod/plugins/content/areas/godwars").walkTopDown().filter { it.isFile }.toList()
        assertTrue(sources.isNotEmpty(), "no God Wars plugin sources found")
        val objs = Objs::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .associate { it.name to it.getInt(null) }
        val bound = mutableMapOf<Int, String>()
        val documentedAbsent = mutableSetOf<Int>()
        sources.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                val ids = mutableListOf<Int>()
                Regex("""on_obj_option\(\s*obj\s*=\s*Objs\.([A-Z0-9_]+)""").find(line)?.let { m -> objs[m.groupValues[1]]?.let { ids += it } }
                Regex("""on_obj_option\(\s*obj\s*=\s*(\d+)""").find(line)?.let { ids += it.groupValues[1].toInt() }
                Regex("""(?:doorId|altarId|rockId|ropeId)\s*=\s*(?:Objs\.([A-Z0-9_]+)|(\d+))""").findAll(line).forEach { m ->
                    (if (m.groupValues[1].isNotEmpty()) objs[m.groupValues[1]] else m.groupValues[2].toIntOrNull())?.let { ids += it }
                }
                ids.forEach { id ->
                    bound[id] = "${file.name}:${index + 1}"
                    if (lines.subList(maxOf(0, index - 6), index).any { it.contains("REVISION_ABSENT") }) documentedAbsent += id
                }
            }
        }
        println("GWD_BOUND ${bound.size} ${bound.keys.sorted()}")

        val xtea = File("../../data").walkTopDown().maxDepth(3).first { it.isFile && it.name.contains("xtea", ignoreCase = true) }.path
        val production = reachable(File("../../data/cache").path, xtea)
        val pristine = reachable("C:/RSPS/reference/openrs2_667/cache", xtea)

        val damaged = bound.filterKeys { it in pristine && it !in production }.map { (id, where) -> "$id ($where)" }
        val absent = bound.filterKeys { it !in pristine && it !in production }
        val undocumented = absent.filterKeys { it !in documentedAbsent }.map { (id, where) -> "$id ($where)" }
        println("GWD_REVISION_ABSENT ${absent.map { (id, where) -> "$id ($where)" }}")
        assertTrue(damaged.isEmpty(), "placed in the pristine 667 map but missing from production:\n" + damaged.joinToString("\n"))
        assertTrue(undocumented.isEmpty(), "bound objects absent from the 667 map without a REVISION_ABSENT note:\n" + undocumented.joinToString("\n"))
    }
}
