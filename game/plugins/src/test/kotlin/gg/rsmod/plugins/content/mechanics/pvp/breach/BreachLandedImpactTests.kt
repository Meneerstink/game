package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.DragonfireTable
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
    fun `the actual hit roll preserves accurate melee effects through protection without turning misses into hits`() {
        val world = mockk<World>(relaxed = true)
        val npc = mockk<Npc>(relaxed = true)
        val target = mockk<Pawn>(relaxed = true)
        every { npc.id } returns -7
        every { npc.world } returns world
        every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy()
        every { npc.attr } returns AttributeMap()
        every { world.randomDouble() } returns 0.5
        val hit = NpcAttacks::class.java.getDeclaredMethod(
            "hit", Npc::class.java, Pawn::class.java, NpcAttacks.HitDef::class.java, Int::class.javaPrimitiveType,
            DragonfireTable.Type::class.java, String::class.java, kotlin.jvm.functions.Function1::class.java,
        ).apply { isAccessible = true }
        mockkObject(MeleeCombatFormula, MagicCombatFormula, RangedCombatFormula)
        mockkStatic("gg.rsmod.plugins.content.combat.PawnExtKt")
        try {
            every { npc.dealHit(
                target = target, minHit = any(), maxHit = any(), landHit = any(), delay = any(), onHit = any(),
                hitType = any(), bonusDamage = any(), applyDeflectProtection = any(), sourceSpellId = any(),
            ) } returns mockk(relaxed = true)
            var unprotected = 0.9
            every { MeleeCombatFormula.getAccuracy(npc, target, any()) } returns 0.0
            every { MagicCombatFormula.getAccuracy(npc, target, any()) } returns 0.0
            every { RangedCombatFormula.getAccuracy(npc, target, any()) } returns 0.0
            every { MeleeCombatFormula.getUnprotectedAccuracy(npc, target) } answers { unprotected }
            every { MagicCombatFormula.getUnprotectedAccuracy(npc, target) } answers { unprotected }
            every { RangedCombatFormula.getUnprotectedAccuracy(npc, target) } answers { unprotected }
            for ((offense, defence, style) in listOf(
                Triple("slash", "slash", StyleType.SLASH), Triple("magic", "magic", StyleType.MAGIC),
                Triple("range", "range", StyleType.RANGED), Triple("melee", "magic", StyleType.MAGIC_MELEE),
            )) {
                var roll: NpcAttacks.HitRoll? = null
                val capture: (NpcAttacks.HitRoll) -> Unit = { roll = it }
                fun swing(combatDef: String = "breach_dscim") {
                    hit.invoke(NpcAttacks, npc, target, NpcAttacks.HitDef(offense = offense, defence = defence, max = 210), 0,
                        DragonfireTable.Type.CHROMATIC, combatDef, capture)
                }
                unprotected = 0.9
                swing()
                assertEquals(style, npc.combatDef.attackStyleType, "$offense against $defence uses the correct formula style")
                assertEquals(false, roll!!.landHit, "$offense damage remains blocked")
                assertEquals(true, roll!!.landsIgnoringPrayer, "$offense accurate effect still lands")
                if (offense == "slash") {
                    swing("ordinary_melee")
                    assertEquals(false, roll!!.landsIgnoringPrayer, "unrelated melee effects keep their protection behavior")
                }
                unprotected = 0.1
                swing()
                assertEquals(false, roll!!.landsIgnoringPrayer, "$offense true miss must apply no effect")
            }
        } finally {
            unmockkStatic("gg.rsmod.plugins.content.combat.PawnExtKt")
            unmockkObject(MeleeCombatFormula, MagicCombatFormula, RangedCombatFormula)
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
