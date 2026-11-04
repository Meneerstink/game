package gg.rsmod.plugins.content.mechanics.moderation

import gg.rsmod.game.service.serializer.TeamTestAccounts
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T3: every team test account has a preset; the owner's sets and the 667 set are what team\accounts.txt promises. */
class TeamAccountPresetsTests {
    @Test
    fun `every team test account has exactly one preset`() {
        assertEquals(TeamTestAccounts.NAMES, TeamAccountPresets.ALL.map { it.name })
        for (name in TeamTestAccounts.NAMES) {
            assertTrue(TeamAccountPresets.forName(name.uppercase()) != null, name)
        }
    }

    @Test
    fun `two accounts carry Ancient Curses and the sets cover owner OSRS gear and plain 667 gear`() {
        assertEquals(listOf("teamtest2", "teamtest3"), TeamAccountPresets.ALL.filter { it.curses }.map { it.name })
        val worn = TeamAccountPresets.ALL.associate { it.name to it.preset.worn.map { (id, _) -> id } }
        // Owner 2026-11-04: teamtest1 = the max melee set, teamtest2 = the max range set (both approved in the test client).
        assertTrue(Items.OSMUMTENS_FANG in worn.getValue("teamtest1") && Items.ULTOR_RING in worn.getValue("teamtest1"))
        assertTrue(Items.ZARYTE_CROSSBOW in worn.getValue("teamtest2") && Items.DIAMOND_DRAGON_BOLTS_E in worn.getValue("teamtest2"))
        val owner = GearPresets.forCommand("gear_owner")!!.worn.map { it.first }
        assertTrue(Items.TOXIC_STAFF_OF_THE_DEAD in owner && Items.NEITIZNOT_FACEGUARD in owner)
        assertTrue(Items.DARK_BOW in GearPresets.forCommand("gear_ownerrange")!!.worn.map { it.first })
        assertTrue(Items.TORVA_PLATEBODY in worn.getValue("teamtest3"))
        assertTrue(Items.BANDOS_CHESTPLATE in worn.getValue("teamtest4") && Items.ABYSSAL_WHIP in worn.getValue("teamtest4"))
        // one piece per equipment slot is checked in game by EquipAction; here: no duplicate ids per set
        worn.forEach { (name, ids) -> assertEquals(ids.size, ids.toSet().size, name) }
    }
}
