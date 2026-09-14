package gg.rsmod.plugins.content.mechanics.poison

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.math.ceil
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS Wiki "Poison" weapon poisoning table and chances, over every poisoned item in the 667 cache. */
class WeaponPoisonTests {
    private val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())

    @AfterTest
    fun close() {
        library.close()
    }

    private fun firstHit(severity: Int) = ceil(severity / 5.0).toInt()

    @Test
    fun `severities reproduce the wiki initial damage for melee and ranged`() {
        // Weapon poison 4 / 2, (+) 5 / 3, (++) 6 / 4, karambwan paste 6 melee, Abyssal tentacle 4.
        assertEquals(listOf(4, 5, 6, 6), listOf("Bronze dagger(p)", "Bronze dagger(p+)", "Bronze dagger(p++)", "Bronze spear (kp)").map { firstHit(WeaponPoison.severityForName(it)) })
        assertEquals(
            listOf(2, 3, 4),
            listOf("Bronze arrow(p)", "Bronze arrow(p+)", "Bronze arrow(p++)").map { firstHit(WeaponPoison.appliedSeverity(WeaponPoison.severityForName(it), ranged = true)) },
        )
        assertEquals(0, WeaponPoison.severityForName("Bronze dagger"))
        assertEquals(listOf(4, 8, 2), listOf(WeaponPoison.chanceDenominator(false, false), WeaponPoison.chanceDenominator(true, false), WeaponPoison.chanceDenominator(false, true)))
    }

    @Test
    fun `every poisoned item in the cache carries a severity`() {
        val definitions = DefinitionSet()
        definitions.load(library, ItemDef::class.java)
        @Suppress("UNCHECKED_CAST")
        val items = (definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>).values.filter { !it.noted }
        val marked = items.filter { Regex("""\((p|p\+|p\+\+|kp)\)""").containsMatchIn(it.name) }
        assertTrue(marked.size >= 472, "poisoned roster ${marked.size}")
        marked.forEach { assertTrue(WeaponPoison.severity(definitions, it.id) >= 20, "${it.id} ${it.name}") }
        assertEquals(20, WeaponPoison.severity(definitions, Items.ABYSSAL_TENTACLE))
    }

    @Test
    fun `every player hit and every special attack goes through the hook`() {
        val pawnExt = File("src/main/kotlin/gg/rsmod/plugins/content/combat/PawnExt.kt").readText()
        assertTrue("WeaponPoison.onPlayerHit(this, target, pawnHit, hitType)" in pawnExt)
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/SpecialAttacks.kt").readText()
        assertTrue("WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] = true" in specials)
    }
}
