package gg.rsmod.plugins.content.npcs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 (Q-040..Q-045) close-out: the plan's required "one BossRosterTests that enumerates a
 * boss table (npc id, region, script bound, drop table non-empty) and names the failing entry."
 *
 * There is no harness in this codebase that boots the real `PluginRepository`/`World` under test
 * (every plugin-adjacent test mocks it - see `SpecialAttackCoverageTests`'s own note), so "script
 * bound" and "drop table non-empty" are proven by scanning the real committed source tree (the
 * same files the boot-time loader compiles) for the npc's real constant name co-occurring with a
 * combat-binding or drop-registration call in the same file - not by executing the game. The bulk,
 * data-sourced drop table (`data/cfg/npcs/drop-tables.json`) is checked directly for npc ids that
 * rely on it rather than a hand-written table (Dagannoth Kings).
 *
 * Failing entries are named, not hidden: [KNOWN_GAPS] pins the exact set found so far, so a
 * regression anywhere else fails loudly and a gap being silently fixed without updating this list
 * also fails. WildyWyrm's missing drop table and Bork's missing implementation (both first
 * recorded under Q-042) are both now closed - see the register's WildyWyrm-drops/Bork batch entry.
 */
class BossRosterTests {
    private data class Boss(
        val label: String,
        val constant: String,
        val requireDropTable: Boolean = true,
        val requireCombatScript: Boolean = true,
    )

    private val roster =
        listOf(
            Boss("King Black Dragon", "KING_BLACK_DRAGON"),
            Boss("Kalphite Queen (form 1)", "KALPHITE_QUEEN"),
            Boss("Kalphite Queen (form 2)", "KALPHITE_QUEEN_1160"),
            Boss("Chaos Elemental", "CHAOS_ELEMENTAL"),
            Boss("Corporeal Beast", "CORPOREAL_BEAST"),
            Boss("Dagannoth Rex", "DAGANNOTH_REX", requireDropTable = false), // bulk JSON table, see below
            Boss("Dagannoth Prime", "DAGANNOTH_PRIME", requireDropTable = false),
            Boss("Dagannoth Supreme", "DAGANNOTH_SUPREME", requireDropTable = false),
            Boss("Tormented Demon", "TORMENTED_DEMON"),
            Boss("Glacor", "GLACOR"),
            Boss("WildyWyrm", "WILDYWYRM"),
            // Bork was fully ported in a later, undocumented batch (portal/cooldown/spawn, Ork
            // Legion escort at 60% hp, Dagon'hai elite, cavern collapse, drop table in
            // areas/wilderness/bork.plugin.kts) - the Q-042 "not implemented at all" note this
            // roster used to cite was stale by the time this batch found it; corrected here.
            Boss("Bork", "BORK"),
            // Barrows brothers don't drop loot on death - the reward is chest-based
            // (`Barrows.reward()`, tested separately in `BarrowsRewardTests`), not a per-npc
            // drop table registration, so `requireDropTable` is false here deliberately.
            Boss("Ahrim the Blighted", "AHRIM_THE_BLIGHTED", requireDropTable = false),
            Boss("Dharok the Wretched", "DHAROK_THE_WRETCHED", requireDropTable = false),
            Boss("Guthan the Infested", "GUTHAN_THE_INFESTED", requireDropTable = false),
            Boss("Karil the Tainted", "KARIL_THE_TAINTED", requireDropTable = false),
            Boss("Torag the Corrupted", "TORAG_THE_CORRUPTED", requireDropTable = false),
            Boss("Verac the Defiled", "VERAC_THE_DEFILED", requireDropTable = false),
            Boss("Nex", "NEX"),
            Boss("General Graardor", "GENERAL_GRAARDOR"),
            Boss("Kree'arra", "KREEARRA"),
            Boss("Commander Zilyana", "COMMANDER_ZILYANA"),
            Boss("K'ril Tsutsaroth", "KRIL_TSUTSAROTH"),
            // Jad's reward is a direct Fire cape award in FightCaves.kt's own leave() flow, not a
            // registered drop table - a deliberate special-encounter reward, not a gap.
            Boss("TzTok-Jad", "TZTOKJAD", requireDropTable = false),
        )

    /** The exact, named set of bosses still missing a drop table or combat script. Empty: none known. */
    private val knownDropTableGaps = emptySet<String>()
    private val knownCombatScriptGaps = emptySet<String>()

    @Test
    fun `every roster boss has a real npc constant, a combat script and a drop table, gaps named exactly`() {
        val dropTableGaps = mutableListOf<String>()
        val combatScriptGaps = mutableListOf<String>()
        val missingConstant = mutableListOf<String>()

        for (boss in roster) {
            val id = npcIdOf(boss.constant)
            if (id == null) {
                missingConstant.add(boss.label)
                continue
            }
            if (boss.requireCombatScript && !hasCombatScript(boss.constant)) {
                combatScriptGaps.add(boss.label)
            }
            if (boss.requireDropTable && !hasDropTable(boss.constant) && !bulkDropTableHasId(id)) {
                dropTableGaps.add(boss.label)
            }
        }

        assertTrue(missingConstant.isEmpty(), "roster names npc constants that don't exist: $missingConstant")
        assertEquals(knownCombatScriptGaps, combatScriptGaps.toSet(), "combat-script gaps changed")
        assertEquals(knownDropTableGaps, dropTableGaps.toSet(), "drop-table gaps changed")
    }

    /** Dagannoth Kings deliberately rely on the bulk table, not a hand-written one; confirm it's real. */
    @Test
    fun `dagannoth kings resolve through the bulk drop table since they have no hand-written one`() {
        for (constant in listOf("DAGANNOTH_REX", "DAGANNOTH_PRIME", "DAGANNOTH_SUPREME")) {
            val id = npcIdOf(constant)!!
            assertTrue(bulkDropTableHasId(id), "$constant (id $id) missing from the bulk drop table")
            assertTrue(!hasDropTable(constant), "$constant unexpectedly has a hand-written table now - update requireDropTable")
        }
    }

    private fun npcIdOf(constant: String): Int? {
        val marker = "const val $constant ="
        for (line in NPCS_SOURCE) {
            val idx = line.indexOf(marker)
            if (idx != -1) {
                return line.substring(idx + marker.length).trim().takeWhile { it.isDigit() }.toIntOrNull()
            }
        }
        return null
    }

    private fun hasCombatScript(constant: String): Boolean =
        CONTENT_FILES.any { text ->
            text.contains("Npcs.$constant") &&
                (text.contains("set_combat_def") || text.contains(": CombatScript") || text.contains("CombatStrategy"))
        }

    private fun hasDropTable(constant: String): Boolean =
        CONTENT_FILES.any { text ->
            text.contains("Npcs.$constant") && (text.contains("DropTableFactory.register") || text.contains("table.register"))
        }

    private fun bulkDropTableHasId(id: Int): Boolean = BULK_DROP_TABLE_TEXT.contains("\"id\":$id,")

    companion object {
        private val CONTENT_ROOT = File("src/main/kotlin/gg/rsmod/plugins/content")
        private val NPCS_FILE = File("src/main/kotlin/gg/rsmod/plugins/api/cfg/Npcs.kt")
        private val BULK_DROP_TABLE_FILE = File("../../data/cfg/npcs/drop-tables.json")

        private val NPCS_SOURCE: List<String> = NPCS_FILE.readLines()
        private val CONTENT_FILES: List<String> =
            CONTENT_ROOT
                .walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                .map { it.readText() }
                .toList()
        private val BULK_DROP_TABLE_TEXT: String = if (BULK_DROP_TABLE_FILE.exists()) BULK_DROP_TABLE_FILE.readText() else ""
    }
}
