package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Breach audit 2026-09-28: teleblock (Justiciar Zachariah / Derwen), Durial321's Ice Barrage freeze, I DSCIM YOU's Sever and the
 * Sulphur Lizard knockback ran on every impact, splashes and misses included. They are registered with
 * [NpcAttacks.onLandedImpact], which runs only when the attack dealt damage; [NpcAttacks.onImpact] keeps running regardless.
 */
class BreachLandedImpactTests {
    private val impact =
        NpcAttacks::class.java.getDeclaredMethod(
            "impact", Npc::class.java, NpcAttacks.Row::class.java, Pawn::class.java, NpcAttacks.Attack::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }

    private fun fire(def: String, landed: Boolean, accurate: Boolean = landed) {
        val npc = mockk<Npc>(relaxed = true) { every { id } returns -7 }
        val target = mockk<Pawn>(relaxed = true)
        val row = NpcAttacks.Row(id = -7, name = "test", combatDef = def, attackRange = 1, attacks = emptyList())
        impact.invoke(NpcAttacks, npc, row, target, NpcAttacks.Attack(id = "a"), landed, accurate)
    }

    @Test
    fun `a landed-impact hook runs only when the accuracy roll succeeded, protection prayers left out`() {
        var calls = 0
        NpcAttacks.onLandedImpact("breach_test_landed", "a") { _, _ -> calls++; true }
        fire("breach_test_landed", landed = false)
        assertEquals(0, calls, "a splash or miss must not apply the effect")
        fire("breach_test_landed", landed = true)
        assertEquals(1, calls)
        // owner live test 2026-09-28: a Sever blocked to 0 by Deflect Melee still hit - the effect must apply (accuracy ignoring prayer)
        fire("breach_test_landed", landed = false, accurate = true)
        assertEquals(2, calls, "a hit that only protection prayer reduced to 0 still applies the effect")
        fire("breach_test_landed", landed = false, accurate = false)
        assertEquals(2, calls)
    }

    @Test
    fun `teleblock, Durial freeze, Sever and knockback are landed-only`() {
        val src =
            java.io.File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/breach/deadman_breach.plugin.kts").readText()
        listOf(
            "onLandedImpact(def, \"magic\")",
            "onLandedImpact(\n    \"breach_sulphur_lizard\"".replace("\n    ", ""),
            "onLandedImpact(\"breach_durial321\", \"ice_barrage\")",
            "onLandedImpact(\"breach_dscim\", \"sever\")",
        ).forEach { needle ->
            val flat = src.replace(Regex("\\(\\s+"), "(")
            kotlin.test.assertTrue(flat.contains(needle), "deadman_breach.plugin.kts must register $needle")
        }
    }

    @Test
    fun `a plain impact hook still runs on a miss`() {
        var calls = 0
        NpcAttacks.onImpact("breach_test_any", "a") { _, _ -> calls++; true }
        fire("breach_test_any", landed = false)
        fire("breach_test_any", landed = true)
        assertEquals(2, calls)
    }
}
