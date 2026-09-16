package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT Bow of Faerdhinen and crystal armour against the wiki and the wiki DPS calculator. */
class CrystalEquipmentTests {
    @Test
    fun `crystal armour gives 5, 10 and 15 percent accuracy and half that damage per piece to crystal bows only`() {
        assertEquals(listOf(1, 2, 3), CrystalEquipment.ARMOUR.map { it.weight }, "helm 1, legs 2, body 3")
        // Wiki: helm 5 % / 2.5 %, legs 10 % / 5 %, body 15 % / 7.5 %, full set 30 % / 15 %.
        assertEquals(1050.0, CrystalEquipment.accuracy(1000.0, 1))
        assertEquals(1300.0, CrystalEquipment.accuracy(1000.0, 6))
        assertEquals(1025.0, CrystalEquipment.damage(1000.0, 1))
        assertEquals(1150.0, CrystalEquipment.damage(1000.0, 6))
        assertEquals(36.0, CrystalEquipment.damage(32.0, 5), "trunc(32 × 45 / 40) = 36 (calculator in-game test)")
        assertTrue(CrystalEquipment.isCrystalBow(Items.BOW_OF_FAERDHINEN))
        assertTrue(CrystalEquipment.isCrystalBow(Items.BOW_OF_FAERDHINEN_INACTIVE), "calculator: any Bow of Faerdhinen name")
        assertTrue(CrystalEquipment.isCrystalBow(Items.BOW_OF_FAERDHINEN_C))
        assertTrue(CrystalEquipment.isCrystalBow(Items.NEW_CRYSTAL_BOW), "the 667 crystal bow also counts")
        assertFalse(CrystalEquipment.isCrystalBow(Items.TWISTED_BOW), "no bonus on other ranged weapons")
        assertFalse(CrystalEquipment.isCrystalBow(null))
    }

    @Test
    fun `shards add 100 charges up to 20000 and items go inactive at 0`() {
        assertEquals(100, CrystalEquipment.CHARGES_PER_SHARD)
        assertEquals(20_000, CrystalEquipment.MAX_CHARGES)
        val inactive = Item(Items.BOW_OF_FAERDHINEN_INACTIVE)
        assertEquals(200, CrystalEquipment.shardsToAdd(inactive, 500))
        val charged = CrystalEquipment.withCharges(inactive, 250)
        assertEquals(Items.BOW_OF_FAERDHINEN, charged.id)
        assertEquals(250, CrystalEquipment.charges(charged))
        assertEquals(197, CrystalEquipment.shardsToAdd(charged, 500))
        assertEquals(Items.BOW_OF_FAERDHINEN_INACTIVE, CrystalEquipment.withCharges(charged, 0).id)
        assertEquals(Items.CRYSTAL_BODY, CrystalEquipment.withCharges(Item(Items.CRYSTAL_BODY_INACTIVE), 1).id)
        assertEquals(Items.CRYSTAL_HELM_INACTIVE, CrystalEquipment.withCharges(Item(Items.CRYSTAL_HELM), 0).id)
        assertNull(CrystalEquipment.INACTIVE_FOR[Items.BOW_OF_FAERDHINEN_C], "the corrupted bow never uses charges")
    }

    @Test
    fun `formula, ammo, projectile, charge use and PvP death are wired in`() {
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/RangedCombatFormula.kt").readText()
        assertTrue(formula.indexOf("CrystalEquipment.applyDamage(player, hit)") in 0 until formula.indexOf("TargetModifiers.rangedDamageMultiplier(player, target)"))
        assertTrue(formula.indexOf("CrystalEquipment.applyAccuracy(player, hit)") in 0 until formula.indexOf("TargetModifiers.rangedAccuracyMultiplier(player, target)"))
        assertTrue("CrystalEquipment.isCrystalBow(player.getEquipment(EquipmentType.WEAPON)?.id)) return null" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/ranged/RangedAmmo.kt").readText())
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        // Crystal bows keep the 667 special arrow 249/250; the Bow of Faerdhinen uses the imported OSRS arrow (owner decision (e)).
        assertTrue("OsrsGfx.FAERDHINEN_ARROW_TRAVEL else 249, ProjectileType.ARROW)" in strategy && "CrystalEquipment.afterBowShot(pawn)" in strategy)
        assertTrue("OsrsGfx.FAERDHINEN_ARROW_LAUNCH else 250, 60)" in strategy)
        assertTrue("CrystalEquipment.onHitReceived(target)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/PawnExt.kt").readText())
        assertTrue("Item(Items.BOW_OF_FAERDHINEN_INACTIVE, 1)" in File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakables.kt").readText())
        assertTrue(Items.BOW_OF_FAERDHINEN in gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows.CRYSTAL_BOWS, "range 10")
    }

    @Test
    fun `Revert returns exactly one crystal armour seed per piece, active or inactive, and nothing else`() {
        // Wiki "Crystal equipment#Reverting": "Dismantling a crystal armour piece will return the crystal armour seeds to
        // the player but all crystal shard charges that were previously loaded will be lost."
        val expectedIds =
            setOf(
                Items.CRYSTAL_HELM,
                Items.CRYSTAL_HELM_INACTIVE,
                Items.CRYSTAL_BODY,
                Items.CRYSTAL_BODY_INACTIVE,
                Items.CRYSTAL_LEGS,
                Items.CRYSTAL_LEGS_INACTIVE,
            )
        assertEquals(expectedIds, CrystalEquipment.REVERT_SEED.keys)
        assertTrue(CrystalEquipment.REVERT_SEED.values.all { it == Items.CRYSTAL_ARMOUR_SEED }, "every piece reverts to the same generic seed")
        // The classic Crystal bow's inactive Revert is already bound in osrs_bows.plugin.kts; its charged Revert and the
        // Bow of Faerdhinen's Uncharge are unsourced (not the armour's one-seed shape) and must stay out of this map.
        assertTrue(Items.CRYSTAL_BOW_OSRS !in CrystalEquipment.REVERT_SEED)
        assertTrue(Items.CRYSTAL_BOW_OSRS_INACTIVE !in CrystalEquipment.REVERT_SEED)
        assertTrue(Items.BOW_OF_FAERDHINEN !in CrystalEquipment.REVERT_SEED)
    }
}
