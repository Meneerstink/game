package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "nightmare" against the OSRS Wiki staff pages and the wiki DPS calculator. */
class OsrsNightmareImportTests {
    @Test
    fun `Immolate and Invocate spell max hits follow the wiki formula at every Magic level`() {
        (1..120).forEach { level ->
            val immolate = minOf(floor(58.0 * level / 99 + 1).toInt(), 58)
            val invocate = minOf(floor(44.0 * level / 99 + 1).toInt(), 44)
            assertEquals(immolate, NightmareStaves.immolateBase(level), "Immolate at $level")
            assertEquals(invocate, NightmareStaves.invocateBase(level), "Invocate at $level")
        }
        assertEquals(58, NightmareStaves.immolateBase(99))
        assertEquals(44, NightmareStaves.invocateBase(97), "Invocate stops scaling at 97")
        assertEquals(55, NightmareStaves.SPECIAL_ENERGY)
        assertEquals(1.5, NightmareStaves.IMMOLATE_ACCURACY)
        assertEquals(20, NightmareStaves.invocatePrayer(41))
        assertEquals(120, NightmareStaves.INVOCATE_PRAYER_CAP)
    }

    @Test
    fun `requirements, orbs, PvP death and staff rules are wired`() {
        val yml = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.NIGHTMARE_STAFF..Items.ELDRITCH_ORB_NOTED }.associateBy { it.path("id").asInt() }
        fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(mapOf(6 to 72, 3 to 50), reqs(Items.NIGHTMARE_STAFF))
        listOf(Items.HARMONISED_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF).forEach {
            assertEquals(mapOf(6 to 82, 3 to 50), reqs(it))
            assertEquals(15.0, yml.getValue(it).path("equipment").path("magic_damage").asDouble())
        }
        mapOf(Items.HARMONISED_NIGHTMARE_STAFF to Items.HARMONISED_ORB, Items.VOLATILE_NIGHTMARE_STAFF to Items.VOLATILE_ORB, Items.ELDRITCH_NIGHTMARE_STAFF to Items.ELDRITCH_ORB)
            .forEach { (staff, orb) ->
                val row = OsrsOrnamentKits.forPvpConversion(staff)
                assertEquals(Items.NIGHTMARE_STAFF, row?.base, "the killer receives the staff")
                assertEquals(orb, row?.kit, "and the orb")
            }
        val configs = File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText()
        assertTrue("AutocastWeapons.spellAttackSpeed(" in configs && gg.rsmod.plugins.content.combat.magic.AutocastWeapons.spellAttackSpeed(Items.HARMONISED_NIGHTMARE_STAFF, gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.FIRE_SURGE, true) == 4 && gg.rsmod.plugins.content.combat.magic.AutocastWeapons.spellAttackSpeed(Items.HARMONISED_NIGHTMARE_STAFF, gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.FIRE_SURGE, false) == 5)
        // Harmonised: standard spells only - it is not in the central Ancient Magicks autocast set.
        assertTrue(Items.HARMONISED_NIGHTMARE_STAFF !in gg.rsmod.plugins.content.combat.magic.AutocastWeapons.ANCIENT_WEAPONS)
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MagicCombatFormula.kt").readText()
        assertTrue("NightmareStaves.SPECIAL_BASE_MAX_HIT" in formula && "roll = Math.floor(roll * specialAttackMultiplier)" in formula)
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/nightmare_staves.plugin.kts").readText()
        assertTrue("Items.VOLATILE_NIGHTMARE_STAFF" in specials && "Items.ELDRITCH_NIGHTMARE_STAFF" in specials)
    }
}
