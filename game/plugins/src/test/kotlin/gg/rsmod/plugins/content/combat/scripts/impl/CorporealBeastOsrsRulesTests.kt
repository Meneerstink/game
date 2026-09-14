package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.plugins.api.HitType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Q-042: Corporeal Beast rules from the OSRS Wiki "Corporeal Beast/Strategies" and "Corpbane weapons" pages (2026-09-14). */
class CorporealBeastOsrsRulesTests {
    private val script = CorporealBeastCombatScript

    @Test
    fun `only melee and ranged damage from a non-Corpbane-on-stab attack is halved`() {
        assertTrue(script.halvesDamage(HitType.MELEE, corpbaneOnStab = false))
        assertTrue(script.halvesDamage(HitType.RANGE, corpbaneOnStab = false))
        assertFalse(script.halvesDamage(HitType.MELEE, corpbaneOnStab = true))
        assertFalse(script.halvesDamage(HitType.MAGIC, corpbaneOnStab = false))
    }

    @Test
    fun `Corpbane list is the wiki list with poisoned and degraded forms`() {
        assertEquals(29, script.CORPBANE_WEAPONS.size)
        script.CORPBANE_WEAPONS.forEach { assertTrue(script.isCorpbaneWeapon(it), it) }
        listOf("Dragon spear (p++)", "Rune spear (kp)", "Black spear (p+)", "Iron spear (p)", "Guthan's warspear 75", "Vesta's spear (deg)")
            .forEach { assertTrue(script.isCorpbaneWeapon(it), it) }
        listOf("Zamorakian hasta", "Rune hasta", "Novite spear", "Primal spear (p++) (b)", "Corrupt vesta's spear", "Halberd", "Anger spear",
            "Dragon longsword", "Crystal halberd (inactive)")
            .forEach { assertFalse(script.isCorpbaneWeapon(it), it) }
    }

    @Test
    fun `every item named like a wiki Corpbane weapon in items yml is classified`() {
        val names = Regex("""name: "([^"]+)"""").findAll(File("../../data/cfg/items.yml").readText()).map { it.groupValues[1] }.toSet()
        val present = script.CORPBANE_WEAPONS.filter { it in names }
        assertTrue(present.size >= 20, "wiki Corpbane names present in items.yml: $present")
        val variants = names.filter { n -> script.CORPBANE_WEAPONS.any { n.startsWith("$it ") && !n.contains("(b)") } }
        variants.forEach { assertTrue(script.isCorpbaneWeapon(it), it) }
    }

    @Test
    fun `prayer, drain, stomp and melee values equal the wiki`() {
        assertEquals(2.0 / 3.0, script.MAGIC_PRAYER_MULTIPLIER)
        assertEquals(1, script.DRAIN_MIN)
        assertEquals(2, script.DRAIN_MAX_POINTS)
        val src = File("src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/CorporealBeastCombatScript.kt").readText()
        listOf("private const val MELEE_MAX = 33.0", "private const val STOMP_MIN = 30.0", "private const val STOMP_MAX = 51.0",
            "maxHit = STOMP_MAX, landHit = true", "private const val MAGIC_MAX = 65.0", "private const val DRAIN_MAX = 55.0")
            .forEach { assertTrue(it in src, it) }
        assertFalse("Skills.SUMMONING" in src)
    }

    @Test
    fun `regeneration and stomp share the 7-tick timer with the wiki heal values`() {
        assertEquals(7, script.REGEN_INTERVAL)
        assertEquals(listOf(75, 85, 95, 105), (0..3).map { script.emptyLairHeal(it) })
        assertEquals(300, script.FULL_HEAL_AFTER_TICKS)
        val src = File("src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/CorporealBeastCombatScript.kt").readText()
        val regen = src.substringAfter("fun startRegeneration(").substringBefore("private fun isUnder(")
        val attack = src.substringAfter("override suspend fun handleSpecialCombat(").substringBefore("fun modifyIncomingDamage(")
        listOf("stomp(npc, underneath)", "emptyLairHeal(emptyStep)", "sinceAttack >= FULL_HEAL_AFTER_TICKS", "emptyStep = 0")
            .forEach { assertTrue(it in regen, it) }
        assertFalse("stomp(" in attack)
        assertTrue("npc.attr[LAST_ATTACKED_CYCLE] = npc.world.currentCycle" in src)
    }
}
