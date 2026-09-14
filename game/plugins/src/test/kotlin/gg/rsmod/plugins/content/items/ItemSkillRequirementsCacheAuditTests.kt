package gg.rsmod.plugins.content.items

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Equip requirements come from `items.yml` `skill_reqs` (ItemMetadataService); the 667 cache carries the client's wield requirements
 * as param pairs 749/750 .. 757/758 (skill id, level). The 2026-09-14 regression audit found native 667 equipment whose yml had no
 * requirement although the cache has one (e.g. Dragon platebody 60 Defence) and entries with skill and level values swapped (e.g.
 * Third-age range top: yml 65 Defence / 45 Ranged, cache 45 Defence / 65 Ranged). Both are fixed and enforced here over the whole
 * roster. Entries that differ in value (Mystic 20 vs 40 Magic, Dagon'hai from the OSRS Wiki, pickaxe Mining gates, ...) need a
 * per-item source decision and are not rewritten from the cache (HANDOFF "REQUIREMENT FIX"). OSRS imports keep their own sourced
 * requirements. OSRS item params 434-437 are not used: on 667 items they are production levels (Fire battlestaff 12 = 62 Crafting).
 */
class ItemSkillRequirementsCacheAuditTests {
    private val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())

    @AfterTest
    fun close() {
        library.close()
    }

    private fun ymlBlocks(): Map<Int, String> {
        val yml = File("../../data/cfg/items.yml").readText()
        val starts = Regex("(?m)^- id: (\\d+)$").findAll(yml).map { it.groupValues[1].toInt() to it.range.first }.toList()
        return starts.indices.associate { i -> starts[i].first to yml.substring(starts[i].second, if (i + 1 < starts.size) starts[i + 1].second else yml.length) }
    }

    private fun ymlRequirements(block: String): Map<Int, Int> =
        Regex("'skill': (\\d+), 'level': (\\d+)").findAll(block).associate { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    /** The cache wield requirements, or null when a pair is malformed (skill outside 0-24 or level outside 1-120). */
    private fun cacheRequirements(def: ItemDef): Map<Int, Int>? {
        val out = LinkedHashMap<Int, Int>()
        for (k in 749..757 step 2) {
            val skill = def.params[k]
            val level = def.params[k + 1]
            if (skill == null && level == null) continue
            if (skill !is Int || level !is Int || skill !in 0..24 || level !in 1..120) return null
            out[skill] = level
        }
        return out
    }

    @Test
    fun `no native equipment item lacks or swaps its cache wield requirements`() {
        val definitions = DefinitionSet()
        definitions.load(library, ItemDef::class.java)
        val imported = Regex("(?m)^  - local_item_id: (\\d+)").findAll(File("C:/RSPS/RSPS_IMPORT_ASSET_MAP.yml").readText()).map { it.groupValues[1].toInt() }.toSet()
        val blocks = ymlBlocks()
        @Suppress("UNCHECKED_CAST")
        val items = definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>
        var withRequirements = 0
        val missing = mutableListOf<String>()
        val swapped = mutableListOf<String>()
        items.values.sortedBy { it.id }.forEach { def ->
            if (def.noted || def.id >= 22753 || def.id in imported) return@forEach
            val block = blocks[def.id]?.takeIf { it.contains("    equip_slot:") } ?: return@forEach
            val cache = cacheRequirements(def)?.takeIf { it.isNotEmpty() } ?: return@forEach
            withRequirements++
            val yml = ymlRequirements(block)
            if (yml.isEmpty()) missing += "${def.id} ${def.name}: cache=$cache"
            if (yml.isNotEmpty() && yml != cache && yml.keys == cache.keys && yml.values.sorted() == cache.values.sorted()) swapped += "${def.id} ${def.name}: yml=$yml cache=$cache"
        }
        assertTrue(withRequirements > 2500, "native equipment with cache requirements: $withRequirements")
        assertTrue(missing.isEmpty(), "${missing.size} items without their cache requirement, first: ${missing.take(15)}")
        assertTrue(swapped.isEmpty(), "${swapped.size} items with skill/level swapped, first: ${swapped.take(15)}")
    }

    @Test
    fun `the Dragon defender carries the OSRS Wiki Defence requirement`() {
        // No cache params; OSRS Wiki "Dragon defender": "It requires level 60 Defence to equip."
        assertEquals(mapOf(1 to 60), ymlRequirements(ymlBlocks().getValue(Items.DRAGON_DEFENDER)))
    }
}
