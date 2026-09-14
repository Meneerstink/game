package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "ammo2" against the OSRS Wiki item pages and the build-240 params. */
class OsrsAmmo2ImportTests {
    private val yml by lazy {
        ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.AMETHYST_BROAD_BOLTS..Items.SEEKING_BROAD_ARROWS }.associateBy { it.path("id").asInt() }
    }

    private fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `wiki requirements and bonuses`() {
        assertEquals(60, reqs(Items.DRAGON_KNIFE)[4], "Dragon knife: 60 Ranged")
        assertEquals(30, yml.getValue(Items.DRAGON_KNIFE).path("equipment").path("ranged_strength").asInt())
        assertEquals(61, reqs(Items.DRAGON_THROWNAXE)[4], "Dragon thrownaxe: 61 Ranged")
        assertEquals(65, reqs(Items.BLACK_CHINCHOMPA)[4], "Black chinchompa: 65 Ranged")
        assertEquals(49, yml.getValue(Items.SEEKING_RUNE_ARROW).path("equipment").path("ranged_strength").asInt())
        assertEquals(20, yml.getValue(Items.SEEKING_RUNE_ARROW).path("equipment").path("attack_ranged").asInt(), "+20 ranged attack")
        assertEquals(122, yml.getValue(Items.OSRS_DRAGON_BOLTS_P).path("equipment").path("ranged_strength").asInt())
    }

    @Test
    fun `ammunition is fired by the weapons the wiki names`() {
        val msb = RangedAmmo.validAmmo(Items.MAGIC_SHORTBOW)!!
        assertTrue(Items.AMETHYST_ARROW in msb && Items.SEEKING_AMETHYST_ARROW in msb && Items.SEEKING_RUNE_ARROW in msb)
        assertTrue(Items.SEEKING_DRAGON_ARROW !in msb, "a magic shortbow fires no dragon arrows")
        assertTrue(Items.SEEKING_DRAGON_ARROW in RangedAmmo.validAmmo(Items.TWISTED_BOW)!!)
        assertTrue(Items.AMETHYST_ARROW !in RangedAmmo.validAmmo(Items.YEW_SHORTBOW)!!)
        listOf(Items.RUNE_CROSSBOW, Items.DRAGON_HUNTER_CROSSBOW, Items.DRAGON_CROSSBOW, Items.ARMADYL_CROSSBOW, Items.ZARYTE_CROSSBOW).forEach {
            assertTrue(Items.AMETHYST_BROAD_BOLTS in RangedAmmo.validAmmo(it)!!, "$it fires amethyst broad bolts")
        }
        assertTrue(Items.OSRS_DRAGON_BOLTS_P in RangedAmmo.validAmmo(Items.ARMADYL_CROSSBOW)!!)
        assertTrue(Items.OSRS_DRAGON_BOLTS_P !in RangedAmmo.validAmmo(Items.RUNE_CROSSBOW)!!)
        listOf(Items.DRAGON_KNIFE, Items.DRAGON_THROWNAXE, Items.BLACK_CHINCHOMPA, Items.AMETHYST_DART_P, Items.SEEKING_BROAD_ARROWS).forEach { id ->
            assertTrue(RangedProjectile.values.any { id in it.items }, "$id has a projectile")
        }
        assertEquals(3, Arrows.SEEKING_MIN_HIT)
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/osrs_thrown_specials.plugin.kts").readText()
        assertTrue("SpecialAttacks.register(25, *Knives.DRAGON_KNIVES.toIntArray())" in specials && "repeat(2)" in specials)
        assertTrue("SpecialAttacks.registerInstant(25, Items.DRAGON_THROWNAXE)" in specials && "RangedProjectile.DRAGON_THROWNAXE, 1.25" in specials)
    }
}
