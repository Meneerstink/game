package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices
import gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "capes" against the OSRS Wiki max cape variant pages. */
class OsrsCapesImportTests {
    @Test
    fun `every max cape variant combines with the 667 max cape and keeps its component's effects`() {
        assertEquals(6, MaxCapes.VARIANTS.size, "3 imbued god, assembler, Masori assembler, Dizana's")
        assertEquals(Items.MAX_CAPE_20767, MaxCapes.MAX_CAPE)
        assertEquals(Items.MAX_HOOD_20768, MaxCapes.MAX_HOOD)
        assertEquals(Items.NEEDLE, MaxCapes.forCape(Items.MASORI_ASSEMBLER_MAX_CAPE)?.tool, "Masori recipes list tools = Needle")
        assertEquals(Items.BLESSED_DIZANAS_QUIVER, MaxCapes.forCape(Items.DIZANAS_MAX_CAPE)?.component)
        listOf(Items.ASSEMBLER_MAX_CAPE, Items.ASSEMBLER_MAX_CAPE_L, Items.MASORI_ASSEMBLER, Items.MASORI_ASSEMBLER_L, Items.MASORI_ASSEMBLER_MAX_CAPE, Items.MASORI_ASSEMBLER_MAX_CAPE_L)
            .forEach { assertTrue(it in AvasDevices.ASSEMBLERS, "$it acts as Ava's assembler") }
        assertEquals(0.80, gg.rsmod.plugins.content.items.osrs.Blowpipe.DART_SAVE_CHANCE[Items.ASSEMBLER_MAX_CAPE])
        assertTrue(Items.DIZANAS_MAX_CAPE in DizanasQuiver.BLESSED && Items.DIZANAS_MAX_CAPE_L in DizanasQuiver.AMMO_HOLDERS)
        assertTrue(Items.DIZANAS_MAX_CAPE !in DizanasQuiver.QUIVERS, "no worn Fill option: not one of the six quiver items")
    }

    @Test
    fun `stats, PvP breaking and Trouver locks`() {
        val yml = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.IMBUED_SARADOMIN_MAX_CAPE..Items.DIZANAS_MAX_CAPE_L_MANGLED }.associateBy { it.path("id").asInt() }
        assertEquals(2.0, yml.getValue(Items.IMBUED_ZAMORAK_MAX_CAPE).path("equipment").path("magic_damage").asDouble(), "imbued max capes keep 2 % magic damage")
        assertEquals(2, yml.getValue(Items.ASSEMBLER_MAX_CAPE).path("equipment").path("ranged_strength").asInt())
        assertEquals(3, yml.getValue(Items.DIZANAS_MAX_CAPE).path("equipment").path("ranged_strength").asInt())
        mapOf(
            Items.IMBUED_SARADOMIN_MAX_CAPE to Items.IMBUED_SARADOMIN_MAX_CAPE_BROKEN,
            Items.ASSEMBLER_MAX_CAPE to Items.ASSEMBLER_MAX_CAPE_BROKEN,
            Items.MASORI_ASSEMBLER to Items.MASORI_ASSEMBLER_BROKEN,
            Items.MASORI_ASSEMBLER_MAX_CAPE to Items.MASORI_ASSEMBLER_MAX_CAPE_BROKEN,
            Items.DIZANAS_MAX_CAPE to Items.DIZANAS_MAX_CAPE_BROKEN,
        ).forEach { (item, broken) -> assertEquals(broken, PvpDeathBreakables.breakableFor(item)?.brokenId, "$item breaks on a PvP death") }
        val trouver = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/trouver/trouver.plugin.kts").readText()
        listOf("IMBUED_ZAMORAK_CAPE_L", "IMBUED_GUTHIX_MAX_CAPE_L", "ASSEMBLER_MAX_CAPE_L", "MASORI_ASSEMBLER_L", "MASORI_ASSEMBLER_MAX_CAPE_L", "DIZANAS_MAX_CAPE_L")
            .forEach { assertTrue("lockedItemId = Items.$it" in trouver, it) }
        assertTrue("brokenItemId = Items.DIZANAS_MAX_CAPE_L_BROKEN" in trouver)
    }
}
