package gg.rsmod.plugins.content.npcs

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Q-040: the six Barrows brothers equal the OSRS Wiki infoboxes (raw wikitext 2026-09-14). */
class BarrowsBrothersOsrsTests {
    private val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/barrows/brothers.plugin.kts").readText()

    @Test
    fun `every brother row carries the wiki style, speed, levels, bonuses and defences`() {
        listOf(
            "BrotherConfig(Npcs.AHRIM_THE_BLIGHTED, StyleType.MAGIC, speed = 6, atk = 1, str = 1, def = 100, mag = 100, rng = 1, attbns = 0, strbns = 68, amagic = 73, mbns = 0, arange = -19, rngbns = 0, dstab = 103, dslash = 85, dcrush = 117, dmagic = 73, dranged = 0,",
            "BrotherConfig(Npcs.DHAROK_THE_WRETCHED, StyleType.SLASH, speed = 7, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 105, amagic = -58, mbns = 0, arange = -18, rngbns = 0, dstab = 252, dslash = 250, dcrush = 244, dmagic = -11, dranged = 249,",
            "BrotherConfig(Npcs.GUTHAN_THE_INFESTED, StyleType.CRUSH, speed = 5, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 75, amagic = -50, mbns = 0, arange = -19, rngbns = 0, dstab = 259, dslash = 257, dcrush = 241, dmagic = -11, dranged = 250,",
            "BrotherConfig(Npcs.KARIL_THE_TAINTED, StyleType.RANGED, speed = 4, atk = 1, str = 1, def = 100, mag = 1, rng = 100, attbns = 0, strbns = 0, amagic = -26, mbns = 0, arange = 134, rngbns = 55, dstab = 79, dslash = 71, dcrush = 90, dmagic = 106, dranged = 100,",
            "BrotherConfig(Npcs.TORAG_THE_CORRUPTED, StyleType.CRUSH, speed = 5, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 72, amagic = -33, mbns = 0, arange = -11, rngbns = 0, dstab = 221, dslash = 235, dcrush = 222, dmagic = 0, dranged = 221,",
            "BrotherConfig(Npcs.VERAC_THE_DEFILED, StyleType.STAB, speed = 5, atk = 100, str = 100, def = 100, mag = 1, rng = 1, attbns = 0, strbns = 72, amagic = -42, mbns = 0, arange = -14, rngbns = 0, dstab = 227, dslash = 230, dcrush = 221, dmagic = 0, dranged = 225,",
        ).forEach { assertTrue(it in plugin, it.substringBefore(",")) }
    }

    @Test
    fun `hitpoints are 100 real and every bonus reaches the definition`() {
        assertTrue("val BROTHER_HITPOINTS = 100" in plugin)
        assertTrue("hitpoints = BROTHER_HITPOINTS * 10" in plugin)
        assertFalse("cfg.hp * 10" in plugin)
        listOf(
            "attackSpeed = cfg.speed", "attackBonus = cfg.attbns", "strengthBonus = cfg.strbns", "attackMagic = cfg.amagic",
            "magicDamageBonus = cfg.mbns", "attackRanged = cfg.arange", "rangedStrengthBonus = cfg.rngbns", "defenceStab = cfg.dstab",
            "defenceSlash = cfg.dslash", "defenceCrush = cfg.dcrush", "defenceMagic = cfg.dmagic", "defenceRanged = cfg.dranged",
        ).forEach { assertTrue(it in plugin, it) }
    }

    @Test
    fun `live attack table max hits equal the wiki`() {
        val rows = ObjectMapper().readTree(File("../../data/cfg/npcs/npc-attacks.json"))
        mapOf(2025 to 200, 2026 to 290, 2027 to 240, 2028 to 200, 2029 to 230, 2030 to 230).forEach { (id, max) ->
            val row = rows.first { it["id"].asInt() == id }
            assertEquals(max, row["attacks"][0]["hits"][0]["max"].asInt(), "npc $id")
        }
    }
}
