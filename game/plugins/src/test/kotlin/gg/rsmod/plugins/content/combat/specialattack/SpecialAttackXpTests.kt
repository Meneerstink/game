package gg.rsmod.plugins.content.combat.specialattack

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Adjacent gap "special attacks get no XP" fixed once: every special attack hit dealt through dealHit while a special is executing gets the
 * normal strategy experience of its hit type (OSRS Wiki "Combat"). Roster over every special attack plugin.
 */
class SpecialAttackXpTests {
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    private fun specialPlugins() =
        content.walkTopDown().filter { it.isFile && it.name.endsWith(".kts") && "SpecialAttacks.register" in it.readText() }.toList()

    @Test
    fun `the shared hit path awards special attack experience per hit type`() {
        val pawnExt = File(content, "combat/PawnExt.kt").readText()
        assertTrue("SpecialAttackXp.award(this, target, hit.hitmarks.sumOf { it.damage }, hitType)" in pawnExt)
        val award = File(content, "combat/specialattack/SpecialAttackXp.kt").readText()
        listOf(
            "HitType.MELEE -> MeleeCombatStrategy.addCombatXp(player, target, damage)",
            "HitType.RANGE -> RangedCombatStrategy.addCombatXp(player, target, damage)",
            "HitType.MAGIC -> MagicCombatStrategy.addCombatXp(player, target, damage, baseXp = 0.0)",
            "player.attr[WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] != true",
        ).forEach { assertTrue(it in award, it) }
        val execute = File(content, "combat/specialattack/SpecialAttacks.kt").readText()
        assertTrue("player.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] = true" in execute)
    }

    @Test
    fun `no special attack plugin adds combat experience by hand`() {
        val combatSkills = Regex("""addXp\(\s*Skills\.(ATTACK|STRENGTH|DEFENCE|RANGED|MAGIC|CONSTITUTION|HITPOINTS)""")
        val plugins = specialPlugins()
        assertTrue(plugins.size >= 30, "special attack plugins found: ${plugins.size}")
        assertEquals(emptyList(), plugins.filter { combatSkills.containsMatchIn(it.readText()) }.map { it.name })
    }

    @Test
    fun `direct hits on the target outside dealHit either award the experience or are recorded effects`() {
        val direct = Regex("""(victim|target|other)\.hit\(\s*damage\s*=""")
        val withDirect = specialPlugins().filter { direct.containsMatchIn(it.readText()) }
        // OSRS import run 2026-09-17: every Dragon/Burning claws hitsplat now goes through dealHit (special experience included) instead
        // of the former direct follow-up hits.
        assertTrue("dragon_claws.plugin.kts" !in withDirect.map { it.name }, "claw hitsplats are no longer direct hits")
        val claws = specialPlugins().first { it.name == "dragon_claws.plugin.kts" }.readText()
        assertTrue("player.dealHit(" in claws, "claw hitsplats use the shared dealHit route")
        val offenders = withDirect.filter { "SpecialAttackXp.award(" !in it.readText() }.map { it.name }.sorted()
        // SOURCE_GAP (no experience, unchanged): Ancient godsword blood sacrifice, Abyssal vine whip vine hits. The Scorching bow's burn
        // left this list on 2026-09-17c: it is a normal burn stack now (Burns.apply), like the Burning claws and Eclipse burns.
        assertEquals(listOf("ancient_godsword.plugin.kts", "melee_specials.plugin.kts"), offenders)
    }
}
