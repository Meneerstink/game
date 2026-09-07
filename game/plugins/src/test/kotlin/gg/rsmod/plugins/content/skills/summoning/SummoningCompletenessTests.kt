package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.Skills
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The completeness gates the Summoning run brief asks for: an option must never be shown without
 * a handler behind it, and a capability must never be claimed without data behind it.
 *
 * These are deliberately data-driven rather than a list of expected familiars, so adding a
 * familiar - or changing one's data - is checked automatically instead of silently producing a
 * button that does nothing.
 */
class SummoningCompletenessTests {
    /**
     * The whole ledger, including the two gates added for this run
     * ([SummoningLedger] validates special-move completeness and interface capabilities). This is
     * the same call the server makes at boot, so a data regression fails here first.
     */
    @Test
    fun `the summoning ledger validates`() {
        SummoningFamiliarDefinitions.validate()
        SummoningCombatDefinitions.validate()
        SummoningSpecialMoves.validate()
        SummoningLedger.validate()
    }

    /**
     * The census the brief asks for: every familiar in the roster is accounted for, and the three
     * capability answers the UI is drawn from agree with the ledger for every one of them.
     */
    @Test
    fun `every familiar has a complete capability record`() {
        assertEquals(78, SummoningPouchData.values().size)
        SummoningPouchData.values().forEach { pouch ->
            val entry = SummoningCatalogue[pouch]
            assertEquals(pouch, entry.pouch)
            assertTrue(entry.categories.isNotEmpty(), "${pouch.name} has no category")
            assertEquals(
                entry.inventory.kind != FamiliarInventoryKind.NONE,
                SummoningUi.carries(pouch.npc),
                "${pouch.name}: carrying capability disagrees with the ledger",
            )
            assertEquals(
                SummoningCombatDefinitions.get(pouch).isExecutable,
                SummoningUi.canFight(pouch.npc),
                "${pouch.name}: fighting capability disagrees with its combat row",
            )
        }
    }

    /** A non-carrier must never be offered a beast-of-burden action, on either surface. */
    @Test
    fun `non carriers are never offered beast of burden actions`() {
        listOf(
            SummoningPouchData.UNICORN_STALLION,
            SummoningPouchData.STEEL_TITAN,
            SummoningPouchData.SPIRIT_WOLF,
            SummoningPouchData.DREADFOWL,
        ).forEach { pouch ->
            assertTrue(!SummoningUi.carries(pouch.npc), "${pouch.name} must not be offered Take BoB")
        }
        listOf(
            SummoningPouchData.PACK_YAK,
            SummoningPouchData.WAR_TORTOISE,
            SummoningPouchData.SPIRIT_TERRORBIRD,
        ).forEach { pouch ->
            assertTrue(SummoningUi.carries(pouch.npc), "${pouch.name} must be offered Take BoB")
        }
    }

    /**
     * Every unique right-click option a familiar npc really carries in the cache must resolve to
     * a special move, because that is what `familiar.plugin.kts` binds them all to. An option with
     * no special behind it would be a menu entry that does nothing - the exact fault the owner
     * reported for "Cure Unicorn stallion".
     */
    @Test
    fun `every unique familiar ability option has a special move behind it`() {
        val carryOptions = setOf("interact", "store", "withdraw")
        var checked = 0
        SummoningPouchData.values().forEach { pouch ->
            val def = DEFINITIONS.get(NpcDef::class.java, pouch.npc)
            val abilities =
                def.options.filterNotNull().map { it.lowercase() }
                    .filter { it.isNotBlank() && it !in carryOptions }
            if (abilities.isEmpty()) return@forEach
            val binding = SummoningSpecialMoves.bindings.firstOrNull { b -> b.scrolls.any { pouch.npc in it.familiars } }
            assertNotEquals(
                null,
                binding,
                "${pouch.name} carries the option(s) $abilities but has no special move to run",
            )
            checked++
        }
        // Cure / Drain x7 / Burrow / Cannon / Special / Fireball / Drown / Ash-blast / Flames / Strike
        assertEquals(16, checked, "the cache's unique familiar ability census changed")
    }

    /** A special move that is offered must be fully specified - see [SummoningLedger]. */
    @Test
    fun `every bound special move is fully specified`() {
        SummoningSpecialMoves.bindings.forEach { binding ->
            binding.scrolls.forEach { scroll ->
                assertTrue(scroll.scroll > 0, "${scroll.name} has no scroll item")
                assertTrue(scroll.specialPoints in 1..SummoningSpecialMoves.MAX_PANEL_COST, "${scroll.name} cost")
                assertNotEquals(null, SummoningSpecialMoveText[scroll], "${scroll.name} has no panel text")
                assertTrue(scroll.familiars.isNotEmpty(), "${scroll.name} is bound to no familiar")
            }
        }
    }

    /**
     * The invisible skilling boosts are parsed out of the knowledge base text the catalogue
     * stores verbatim, so this checks the parser still recognises it rather than re-listing the
     * values. The percentage Defence entries must stay excluded: they are a different mechanic.
     */
    @Test
    fun `invisible skilling boosts are parsed from the sourced ability text`() {
        assertTrue(SummoningBoosts.boostedFamiliars.isNotEmpty())
        assertTrue(SummoningPouchData.DESERT_WYRM.npc in SummoningBoosts.boostedFamiliars)
        assertTrue(SummoningPouchData.GRANITE_CRAB.npc in SummoningBoosts.boostedFamiliars)
        // Wolpertinger carries both a flat "Hunter boost (5) - invisible" and a percentage
        // Defence bonus. Only the flat one is a level, so it is parsed and the percentage is not.
        assertTrue(SummoningPouchData.WOLPERTINGER.npc in SummoningBoosts.boostedFamiliars)
        // Every familiar the parser accepted must really carry an "- invisible" boost line, so a
        // percentage entry can never be mistaken for a level.
        SummoningBoosts.boostedFamiliars.forEach { npc ->
            val pouch = SummoningPouchData.values().first { it.npc == npc }
            assertTrue(
                SummoningCatalogue[pouch].abilities.any { it.endsWith("- invisible") && it.contains("(") },
                "${pouch.name} was parsed as boosted but has no flat invisible boost text",
            )
        }
    }

    /**
     * Interact must never be a silent no-op. A familiar either has a sourced transcript or is a
     * carrier whose Interact opens its real Familiar Inventory; anything else would be a menu
     * option with nothing behind it. The count is asserted so a future data change is deliberate.
     */
    @Test
    fun `every familiar interact resolves to a transcript or a carrier window`() {
        val unresolved =
            SummoningPouchData.values().filter { pouch ->
                SummoningDialogueData.conversationsFor(pouch).isEmpty() && !SummoningUi.carries(pouch.npc)
            }
        assertEquals(
            UNRESOLVED_INTERACT,
            unresolved.size,
            "familiars with no transcript and no carrier window: ${unresolved.map { it.name }}",
        )
    }

    companion object {
        /**
         * Familiars that still have neither a sourced transcript nor a carrier window. Recorded as
         * a number rather than hidden: it may only go down. See RSPS_SUMMONING_2011_SPEC.md for
         * which transcripts were deliberately not encoded and why (corrupted source strings,
         * quest-gated conversations, sequential-state conversations, and lyric derivatives).
         */
        private const val UNRESOLVED_INTERACT = 2

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        /**
         * Only the npc definitions, not `loadAll`: this class needs the familiars' baked option
         * strings and nothing else, and several other test classes in this module already hold a
         * full cache-backed [DefinitionSet] each in the same JVM.
         */
        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.load(store, NpcDef::class.java)
            assertNotEquals(DEFINITIONS.getCount(NpcDef::class.java), 0)
        }
    }
}
