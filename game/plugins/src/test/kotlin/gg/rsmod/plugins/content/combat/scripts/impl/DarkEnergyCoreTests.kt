package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.Tile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Q-042: the Dark energy core follows the OSRS Wiki "Dark energy core" page (raw wikitext 2026-09-14). */
class DarkEnergyCoreTests {
    private val script = CorporealBeastCombatScript

    @Test
    fun `spawn, damage and heal values equal the wiki`() {
        assertEquals(8, script.CORE_SPAWN_CHANCE)
        assertEquals(32, script.CORE_SPAWN_HIT_THRESHOLD)
        assertEquals(1000, script.CORE_ATTACK_SPAWN_BELOW_LIFEPOINTS)
        assertEquals(5, script.CORE_DAMAGE_MIN)
        assertEquals(13, script.CORE_DAMAGE_MAX)
        assertEquals(2, script.CORE_ATTACK_SPEED)
        (5..13).forEach { assertEquals(it / 2, script.coreHeal(it)) }
        assertEquals(6, script.coreHeal(13))
    }

    @Test
    fun `range is the 3x3 square around the core`() {
        val core = Tile(2985, 4385, 2)
        for (dx in -2..2) for (dz in -2..2) {
            val inside = dx in -1..1 && dz in -1..1
            assertEquals(inside, script.inCoreRange(core, Tile(2985 + dx, 4385 + dz, 2)), "$dx,$dz")
        }
        assertFalse(script.inCoreRange(core, Tile(2985, 4385, 1)))
    }

    @Test
    fun `jump prefers the northernmost player, then the east side`() {
        val centre = Tile(2987, 4384, 2)
        assertEquals(1, script.jumpTargetIndex(centre, listOf(Tile(2990, 4386, 2), Tile(2980, 4390, 2), Tile(2999, 4380, 2))))
        assertEquals(1, script.jumpTargetIndex(centre, listOf(Tile(2990, 4380, 2), Tile(2995, 4384, 2), Tile(2980, 4370, 2))))
        assertNull(script.jumpTargetIndex(centre, listOf(Tile(2980, 4380, 2), Tile(2987, 4384, 2))))
    }

    @Test
    fun `roll is a true 1 in 8, attack spawn uses the 1000 hp rule and a mid-jump kill disables the core`() {
        val src = File("src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/CorporealBeastCombatScript.kt").readText()
        listOf(
            "world.random(CORE_SPAWN_CHANCE - 1) != 0",
            "npc.getCurrentLifepoints() < CORE_ATTACK_SPAWN_BELOW_LIFEPOINTS",
            "if (npc.attr[CORE_DISABLED] == true) return",
            "if (core.attr[CORE_JUMPING] == true) {\n            beast.attr[CORE_DISABLED] = true",
            "coreHeal(damage)",
            "world.random(CORE_DAMAGE_MIN..CORE_DAMAGE_MAX)",
        ).forEach { assertTrue(it in src, it) }
        assertFalse("CORE_DRAIN_M" in src)
    }
}
