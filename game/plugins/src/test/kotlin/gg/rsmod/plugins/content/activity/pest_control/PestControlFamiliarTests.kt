package gg.rsmod.plugins.content.activity.pest_control

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N205 (owner 2026-10-22: "Pest Control: summoning familiars moeten kunnen meehelpen" - every combat familiar attacks pests and
 * portals). The lander took no follower and the game banned summoning (Darkan); both are gone. A familiar fights through the
 * owner's canAttack, so a shielded portal (an npc without options) stays out of reach; the island instance is multi-way.
 */
class PestControlFamiliarTests {
    private val dir = File("src/main/kotlin/gg/rsmod/plugins/content/activity/pest_control")

    @Test
    fun `a familiar boards the lander and may be summoned during the game`() {
        val handler = File(dir, "PestControlHandler.kt").readText()
        val plugin = File(dir, "pest_control.plugin.kts").readText()
        assertFalse("You can't take a follower on the lander." in handler)
        assertFalse("RestrictedAction.SUMMON" in plugin)
        assertFalse("keep your Familiar away" in plugin)
    }

    @Test
    fun `the island is a multi-way area, so familiars join every fight there`() {
        val multi = File("../../data/cfg/areas/multi.yml").readText()
        assertTrue(Regex("""\b10536\b""").containsMatchIn(multi), "region 10536 (the island copied into every game) is multi")
    }

    @Test
    fun `a familiar attacks only what its owner may attack`() {
        val combat = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/FamiliarCombat.kt").readText()
        assertTrue("player.world.plugins.canAttack(player, target)" in combat)
    }
}
