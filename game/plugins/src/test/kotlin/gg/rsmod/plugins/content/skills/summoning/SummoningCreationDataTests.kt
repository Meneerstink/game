package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Phase O of the re-audit: pouch and scroll **creation** data, across the whole roster.
 *
 * `SummoningCatalogueTests` covers the catalogue's relationships and `SummoningScrollDataTests`
 * checks each scroll's level, experience and special-move cost. Neither looks at the ingredients -
 * the charm, the shard count and the tertiary items - or at whether any of the item ids involved
 * are real items in this cache. A pouch pointing at a tertiary that does not exist would be
 * uncraftable, and nothing would have failed.
 *
 * Every assertion here is either an internal-consistency rule or a check against the production
 * cache. No authentic quantity is asserted, because the quantities are what the sourced tables
 * already carry; the question is whether they are *usable*.
 */
class SummoningCreationDataTests {
    private val roster = SummoningPouchData.values().toList()

    private fun name(itemId: Int): String = DEFINITIONS.get(ItemDef::class.java, itemId).name.orEmpty()

    /**
     * Every pouch is made with a charm, and the charm has to be a charm. Read from the cache's own
     * item names rather than against a hardcoded list of the four charm ids, so this keeps working
     * if the ids ever move.
     */
    @Test
    fun `every pouch is made with a real charm, all 78`() {
        val faults =
            roster.mapNotNull { pouch ->
                val charmName = name(pouch.charm)
                when {
                    charmName.isBlank() -> "${pouch.name} charm ${pouch.charm} is not an item in this cache"
                    !charmName.lowercase().endsWith("charm") -> "${pouch.name} charm ${pouch.charm} is '$charmName'"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} pouches have an unusable charm: $faults")
    }

    /** Spirit shards are consumed by every pouch; a count of zero would make one free. */
    @Test
    fun `every pouch costs spirit shards, all 78`() {
        val faults = roster.filter { it.shards <= 0 }.map { "${it.name} costs ${it.shards} shards" }
        assertTrue(faults.isEmpty(), "${faults.size} pouches cost no shards: $faults")
    }

    /**
     * Tertiary ingredients are optional - some pouches have none - but every one that *is* listed
     * has to be a real item, or the pouch simply cannot be made.
     */
    @Test
    fun `every tertiary ingredient is a real item, all 78`() {
        val faults =
            roster.flatMap { pouch ->
                pouch.tertiaries.filter { name(it).isBlank() }.map { "${pouch.name} tertiary $it is not an item" }
            }
        assertTrue(faults.isEmpty(), "${faults.size} tertiary ingredients do not exist: $faults")
    }

    /** The pouch itself, and the greyed variant the creation interface draws before it is made. */
    @Test
    fun `every pouch and its greyed variant are real items, all 78`() {
        val faults =
            roster.mapNotNull { pouch ->
                when {
                    name(pouch.pouch).isBlank() -> "${pouch.name} pouch item ${pouch.pouch} does not exist"
                    !name(pouch.pouch).lowercase().contains("pouch") ->
                        "${pouch.name} pouch item ${pouch.pouch} is '${name(pouch.pouch)}'"
                    pouch.greyedPouch != 0 && name(pouch.greyedPouch).isBlank() ->
                        "${pouch.name} greyed pouch ${pouch.greyedPouch} does not exist"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} pouches have an unusable item id: $faults")
    }

    /** Creating and summoning both award experience; zero on either would be a silent dead end. */
    @Test
    fun `every pouch awards experience for both creating and summoning it, all 78`() {
        val faults =
            roster.mapNotNull { pouch ->
                when {
                    pouch.creationExperience <= 0.0 -> "${pouch.name} awards no creation experience"
                    pouch.summonExperience <= 0.0 -> "${pouch.name} awards no summon experience"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} pouches award no experience: $faults")
    }

    /** A Summoning level outside 1..99 could never be reached, or would never gate anything. */
    @Test
    fun `every pouch has a reachable Summoning level, all 78`() {
        val faults = roster.filterNot { it.level in 1..99 }.map { "${it.name} requires level ${it.level}" }
        assertTrue(faults.isEmpty(), "${faults.size} pouches have an unreachable level: $faults")
    }

    /**
     * The cross-link that makes scroll creation work at all: every familiar's scroll must name that
     * familiar's npc, and the scroll must name that familiar's pouch. A scroll that names neither
     * is unreachable from the pouch the player actually holds.
     *
     * The five familiars with no special move still have scroll data, so this covers all 78.
     */
    @Test
    fun `every pouch's scroll names that pouch and its npc, all 78`() {
        val faults =
            roster.mapNotNull { pouch ->
                val scroll =
                    SummoningScrollData.values().firstOrNull { pouch.npc in it.familiars }
                        ?: return@mapNotNull "${pouch.name} has no scroll naming its npc ${pouch.npc}"
                when {
                    pouch.pouch !in scroll.pouches ->
                        "${pouch.name}'s scroll ${scroll.name} does not name pouch ${pouch.pouch}"
                    name(scroll.scroll).isBlank() -> "${scroll.name} scroll item ${scroll.scroll} does not exist"
                    !name(scroll.scroll).lowercase().contains("scroll") ->
                        "${scroll.name} scroll item ${scroll.scroll} is '${name(scroll.scroll)}'"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} pouch/scroll links are broken: $faults")
    }

    /**
     * Two familiars must not share a pouch item, and two pouches must not share a scroll item.
     * Either would make one of the pair unreachable, and the roster-wide capability model would
     * silently resolve both to whichever was declared first.
     */
    @Test
    fun `pouch and scroll item ids are unique across the roster`() {
        val duplicatePouches =
            roster.groupBy { it.pouch }.filterValues { it.size > 1 }
                .map { (item, pouches) -> "pouch item $item is shared by ${pouches.map { it.name }}" }
        assertTrue(duplicatePouches.isEmpty(), "duplicate pouch items: $duplicatePouches")

        val duplicateScrolls =
            SummoningScrollData.values().groupBy { it.scroll }.filterValues { it.size > 1 }
                .map { (item, scrolls) -> "scroll item $item is shared by ${scrolls.map { it.name }}" }
        assertTrue(duplicateScrolls.isEmpty(), "duplicate scroll items: $duplicateScrolls")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
