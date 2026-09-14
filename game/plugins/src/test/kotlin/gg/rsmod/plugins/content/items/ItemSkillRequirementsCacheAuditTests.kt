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
 * as param pairs 749/750 .. 757/758 (skill id, level). The 2026-09-14 regression audit found native 667 equipment without the cache
 * requirement (1,423, e.g. Dragon platebody 60 Defence), with skill and level swapped (126, e.g. Third-age range top) and 29 value
 * conflicts. The conflicts were decided per OSRS Wiki item page (decision (d)): Mystic robes "require 40 Magic and 20 Defence",
 * pickaxes/axes need only Attack to wield ("41 Mining to use, and 40 Attack to equip"), Spiny helmet "5 Defence", lit bug lantern
 * "33 Slayer to wield" - all equal to the cache - while the Dagon'hai robes ("70 Magic and 40 Defence") and the Abyssal tentacle
 * ("75 Attack") differ from the 2011 cache and keep the OSRS values below, as does the param-less Dragon defender ("60 Defence").
 * OSRS item params 434-437 are not used: on 667 items they are production levels (Fire battlestaff 12 = 62 Crafting).
 */
class ItemSkillRequirementsCacheAuditTests {
    private val osrsOverrides: Map<Int, Map<Int, Int>> =
        mapOf(
            14497 to mapOf(1 to 40, 6 to 70), 14499 to mapOf(1 to 40, 6 to 70), 14501 to mapOf(1 to 40, 6 to 70),
            14732 to mapOf(1 to 40, 6 to 70), 14733 to mapOf(1 to 40, 6 to 70), 14734 to mapOf(1 to 40, 6 to 70),
            Items.ABYSSAL_TENTACLE to mapOf(0 to 75),
            Items.DRAGON_DEFENDER to mapOf(1 to 60),
        )

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
    fun `every native equipment item requires exactly its cache wield requirements or its sourced OSRS override`() {
        val definitions = DefinitionSet()
        definitions.load(library, ItemDef::class.java)
        val imported = Regex("(?m)^  - local_item_id: (\\d+)").findAll(File("C:/RSPS/RSPS_IMPORT_ASSET_MAP.yml").readText()).map { it.groupValues[1].toInt() }.toSet()
        val blocks = ymlBlocks()
        @Suppress("UNCHECKED_CAST")
        val items = definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>
        var withRequirements = 0
        val offenders = mutableListOf<String>()
        items.values.sortedBy { it.id }.forEach { def ->
            if (def.noted || def.id >= 22753 || def.id in imported) return@forEach
            val block = blocks[def.id]?.takeIf { it.contains("    equip_slot:") } ?: return@forEach
            val yml = ymlRequirements(block)
            val expected = osrsOverrides[def.id] ?: cacheRequirements(def)?.takeIf { it.isNotEmpty() } ?: return@forEach
            withRequirements++
            if (yml != expected) offenders += "${def.id} ${def.name}: yml=$yml expected=$expected"
        }
        assertTrue(withRequirements > 2500, "native equipment with requirements: $withRequirements")
        assertTrue(offenders.isEmpty(), "${offenders.size} items differ, first: ${offenders.take(15)}")
    }

    @Test
    fun `the OSRS overrides are present in items yml`() {
        val blocks = ymlBlocks()
        osrsOverrides.forEach { (id, reqs) -> assertEquals(reqs, ymlRequirements(blocks.getValue(id)), "item $id") }
    }
}
