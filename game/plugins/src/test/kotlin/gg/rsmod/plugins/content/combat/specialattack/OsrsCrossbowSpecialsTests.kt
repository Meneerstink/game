package gg.rsmod.plugins.content.combat.specialattack

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Wiring of the OSRS crossbow specials (OSRS Wiki item pages): energy costs, the bolt-effect mode each special passes to
 * [SpecialAttackSupport.rangedShot], and Annihilate's damage split without bolt effects.
 */
class OsrsCrossbowSpecialsTests {
    private val script =
        File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/osrs_crossbow_specials.plugin.kts").readText()

    private fun block(header: String): String {
        val start = script.indexOf(header)
        assertTrue(start >= 0, "missing registration: $header")
        val end = script.indexOf("SpecialAttacks.register(", start + header.length).let { if (it < 0) script.length else it }
        return script.substring(start, end)
    }

    @Test
    fun `Armadyl Eye costs 50 percent, doubles accuracy and uses the doubled bolt chance`() {
        val body = block("SpecialAttacks.register(50, Items.ARMADYL_CROSSBOW)")
        assertTrue("accuracy = 2.0" in body && "EnchantedBolts.Special.ARMADYL_EYE" in body)
    }

    @Test
    fun `Evoke costs 75 percent, doubles accuracy and guarantees bolt effects on a hit`() {
        val body = block("SpecialAttacks.register(75, Items.ZARYTE_CROSSBOW)")
        assertTrue("accuracy = 2.0" in body && "EnchantedBolts.Special.ZARYTE_EVOKE" in body)
    }

    @Test
    fun `Annihilate costs 60 percent, hits the primary at 120 percent and up to 9 others at 80 percent without bolt effects`() {
        val body = block("SpecialAttacks.register(60, Items.DRAGON_CROSSBOW)")
        assertTrue("damage = 1.2" in body)
        assertTrue("adjacentTargets(player, victim, radius = 1).take(9)" in body)
        assertTrue("specialAttackMultiplier = 0.8" in body)
        assertTrue("boltSpecial" !in body, "enchanted bolt effects cannot activate on Annihilate")
    }

    @Test
    fun `Concentrated Shot costs 65 percent and raises accuracy and damage by 25 percent`() {
        val ballista = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/heavy_ballista.plugin.kts").readText()
        // The (or) ornament variant shares the special (casket-ornaments batch), so both ids are registered together.
        val start = ballista.indexOf("SpecialAttacks.register(65, Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_OR)")
        assertTrue(start >= 0, "missing Heavy ballista + (or) registration")
        assertTrue("rangedShot(player, target, accuracy = 1.25, damage = 1.25)" in ballista.substring(start))
    }
}
