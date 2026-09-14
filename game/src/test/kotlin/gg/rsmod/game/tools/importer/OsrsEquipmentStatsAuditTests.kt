package gg.rsmod.game.tools.importer

import org.junit.BeforeClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT owner requirement 2026-09-14: every imported item carries exactly the OSRS equipment stats of the pinned
 * build-240 definition (attack/defence 0-9, melee strength 10, prayer 11, ranged strength 189 else 12, magic damage 299
 * in tenths of a percent, weapon speed 14), and every other `items.yml` item that is an OSRS item carries the OSRS ranged
 * strength and magic damage. Failures name each offending item and field.
 */
class OsrsEquipmentStatsAuditTests {
    @Test
    fun `every imported item matches the OSRS cache params on every stat`() {
        val mismatches =
            OsrsEquipmentStatsAudit.auditImported(YML, SOURCE, MAPPING).filterNot { m ->
                // Nameless OSRS stack-count variants are cache-only definitions without an items.yml entry.
                (m.field == "entry" && SOURCE.getValue(m.upstreamId).name == "null") ||
                    m.localId in IMPORTED_EXCEPTIONS.keys && m.field == "equipment"
            }
        assertTrue(mismatches.isEmpty(), "imported items differ from OSRS:\n" + mismatches.joinToString("\n"))
    }

    @Test
    fun `OSRS items keep the OSRS ranged strength and magic damage`() {
        val ambiguous = mutableListOf<String>()
        val offenders =
            OsrsEquipmentStatsAudit.auditByName(YML, SOURCE, MAPPING.keys, ambiguous)
                .flatMap { it.diffs }
                .filter { it.field in OsrsEquipmentStatsAudit.RANGED_MAGIC_FIELDS && it.localId !in OsrsEquipmentStatsAudit.RANGED_MAGIC_EXCLUDED }
        assertTrue(offenders.isEmpty(), "ranged strength / magic damage differ from OSRS:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `OSRS items keep every OSRS stat except the recorded 667 exclusions`() {
        val ambiguous = mutableListOf<String>()
        val offenders =
            OsrsEquipmentStatsAudit.auditByName(YML, SOURCE, MAPPING.keys, ambiguous)
                .flatMap { it.diffs }
                .filter { it.localId !in OsrsEquipmentStatsAudit.ALL_FIELDS_EXCLUDED && it.osrs != null }
        assertTrue(offenders.isEmpty(), "stats differ from OSRS (owner decision (d)):\n" + offenders.joinToString("\n"))
        // Owner decisions (f)/(g): salamanders and 667 thrown javelins stay excluded.
        assertTrue((10146..10149).all { it in OsrsEquipmentStatsAudit.ALL_FIELDS_EXCLUDED } && (825..830).all { it in OsrsEquipmentStatsAudit.ALL_FIELDS_EXCLUDED })
    }

    @Test
    fun `magic damage keeps OSRS tenth-of-a-percent precision`() {
        val byId = YML.associateBy { it.path("id").asInt() }
        // Occult necklace 5 %, Seers ring (i) 0.5 %, Ahrim's staff 5 % (build 240 params 50 / 5 / 50).
        assertEquals(5.0, byId.getValue(22328).path("equipment").path("magic_damage").asDouble())
        assertEquals(5.0, byId.getValue(4710).path("equipment").path("magic_damage").asDouble())
        assertEquals("0.5", OsrsEquipmentStatsAudit.format("magic_damage", 5))
    }

    companion object {
        /** OSRS Wiki "Toxic blowpipe": the blowpipe "cannot be wielded if it is uncharged". */
        private val IMPORTED_EXCEPTIONS = mapOf(22624 to "Toxic blowpipe (empty)")

        private lateinit var YML: List<com.fasterxml.jackson.databind.JsonNode>
        private lateinit var SOURCE: Map<Int, ModernItemDef>
        private lateinit var MAPPING: Map<Int, Int>

        @BeforeClass
        @JvmStatic
        fun load() {
            YML = OsrsEquipmentStatsAudit.loadYml(File("../data/cfg/items.yml"))
            SOURCE = OsrsEquipmentStatsAudit.loadSource()
            MAPPING = OsrsEquipmentStatsAudit.importedMapping()
        }
    }
}
