package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "bows" against the OSRS Wiki item pages and the wiki DPS calculator. */
class OsrsBowsImportTests {
    @Test
    fun `revenant bows - activation, ether cap, uncharge and Swarm`() {
        assertEquals(0, RevenantBows.etherToTake(Item(Items.CRAWS_BOW_U), 999), "activation needs 1,000 ether")
        assertEquals(1_000, RevenantBows.etherToTake(Item(Items.CRAWS_BOW_U), 1_000))
        assertEquals(17_000, RevenantBows.etherToTake(Item(Items.CRAWS_BOW_U), 30_000), "1,000 activation + 16,000 ammo")
        val charged = RevenantBows.charge(Item(Items.WEBWEAVER_BOW_U), 17_000)
        assertEquals(Items.WEBWEAVER_BOW, charged.id)
        assertEquals(16_000, RevenantBows.ether(charged))
        assertEquals(0, RevenantBows.etherToTake(charged, 500))
        val (uncharged, returned) = RevenantBows.uncharge(charged)
        assertEquals(Items.WEBWEAVER_BOW_U, uncharged.id)
        assertEquals(17_000, returned)
        assertEquals(12.0, RevenantBows.swarmMaxHit(30.0), "40 % of 30, rounded up")
        assertEquals(13.0, RevenantBows.swarmMaxHit(31.0), "40 % of 31 = 12.4, rounded up")
    }

    @Test
    fun `venator bow, tonalztics and scorching bow values`() {
        assertEquals(26.0, VenatorBow.bounceMaxHit(40.0))
        assertEquals(Items.VENATOR_BOW_UNCHARGED, VenatorBow.withEssence(Item(Items.VENATOR_BOW), 0).id)
        assertEquals(50_000, VenatorBow.essence(VenatorBow.withEssence(Item(Items.VENATOR_BOW_UNCHARGED), 60_000)))
        assertEquals(30.0, Tonalztics.maxHit(40.0))
        assertEquals(31, Tonalztics.divisionDrain(250))
        assertEquals(2, Tonalztics.hits(Tonalztics.withCharges(Item(Items.TONALZTICS_OF_RALOS_UNCHARGED), 5)))
        assertEquals(1, Tonalztics.hits(Item(Items.TONALZTICS_OF_RALOS_UNCHARGED)))
        assertEquals(30, ScorchingBow.DEMONBANE_PERCENT)
        assertEquals(20, ScorchingBow.BIND_TICKS)
        assertEquals(5, ScorchingBow.BURN_HITS)
    }

    @Test
    fun `requirements, rosters and combat wiring`() {
        val yml = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.CRYSTAL_BOW_OSRS..Items.HEAVY_BALLISTA_OR }.associateBy { it.path("id").asInt() }
        fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(mapOf(4 to 70, 16 to 50), reqs(Items.CRYSTAL_BOW_OSRS))
        assertEquals(mapOf(4 to 60), reqs(Items.CRAWS_BOW_U))
        assertEquals(mapOf(4 to 70), reqs(Items.WEBWEAVER_BOW_U))
        assertEquals(mapOf(4 to 80), reqs(Items.VENATOR_BOW))
        assertEquals(mapOf(4 to 77), reqs(Items.SCORCHING_BOW))
        assertEquals(mapOf(4 to 75), reqs(Items.TONALZTICS_OF_RALOS))
        assertEquals(mapOf(4 to 75), reqs(Items.HEAVY_BALLISTA_OR))
        assertTrue(Items.CRYSTAL_BOW_OSRS in Bows.CRYSTAL_BOWS && CrystalEquipment.INACTIVE_FOR[Items.CRYSTAL_BOW_OSRS] == Items.CRYSTAL_BOW_OSRS_INACTIVE)
        assertTrue(BowType.values.first { it.item == Items.SCORCHING_BOW }.ammo.contentEquals(BowType.TWISTED_BOW.ammo))
        assertTrue(OsrsOrnamentKits.forPvpConversion(Items.HEAVY_BALLISTA_OR) == null)
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("RevenantBows.NO_ETHER_MESSAGE" in strategy && "VenatorBow.bounceMaxHit(maxHit)" in strategy && "Tonalztics.afterThrow" in strategy)
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/RangedCombatFormula.kt").readText()
        assertEquals(2, Regex("RevenantBows.wildernessBuff").findAll(formula).count(), "accuracy and max hit")
        assertEquals(2, Regex("ScorchingBow.demonbane").findAll(formula).count(), "accuracy and max hit")
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/osrs_bow_specials.plugin.kts").readText()
        assertTrue("Items.WEBWEAVER_BOW" in specials && "Items.SCORCHING_BOW" in specials && "Tonalztics.DIVISION_ENERGY" in specials)
    }
}
