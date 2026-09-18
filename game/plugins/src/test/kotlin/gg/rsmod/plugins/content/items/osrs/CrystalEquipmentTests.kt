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
        // Owner 2026-09-18: drawn at 96 like every other arrow drawback in RangedProjectile (Void's 60 is in Void units).
        assertTrue("OsrsGfx.FAERDHINEN_ARROW_LAUNCH else 250, 96)" in strategy)
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

    @Test
    fun `singing bowl creation recipes match the owner's cRYSTAL document (OSRS Wiki quoted verbatim)`() {
        // Enumerates all three armour pieces, not one exemplar - a wrong entry names itself in the assertion message.
        val expected =
            mapOf(
                Items.CRYSTAL_HELM to CrystalEquipment.CreationRecipe(Items.CRYSTAL_HELM, shards = 50, seeds = 1, smithing = 70, crafting = 70, xp = 2500.0, npcFeeShards = 60, startCharges = 2500),
                Items.CRYSTAL_LEGS to CrystalEquipment.CreationRecipe(Items.CRYSTAL_LEGS, shards = 100, seeds = 2, smithing = 72, crafting = 72, xp = 5000.0, npcFeeShards = 60, startCharges = 2500),
                Items.CRYSTAL_BODY to CrystalEquipment.CreationRecipe(Items.CRYSTAL_BODY, shards = 150, seeds = 3, smithing = 74, crafting = 74, xp = 7500.0, npcFeeShards = 60, startCharges = 2500),
            )
        expected.forEach { (active, recipe) ->
            assertEquals(recipe, CrystalEquipment.ARMOUR_CREATION[active], "recipe mismatch for item $active")
        }
        assertEquals(expected.keys, CrystalEquipment.ARMOUR_CREATION.keys)

        assertEquals(100, CrystalEquipment.BowfaCreation.SHARDS)
        assertEquals(82, CrystalEquipment.BowfaCreation.SMITHING)
        assertEquals(82, CrystalEquipment.BowfaCreation.CRAFTING)
        assertEquals(5000.0, CrystalEquipment.BowfaCreation.XP)
        assertEquals(50, CrystalEquipment.BowfaCreation.NPC_FEE_SHARDS)
        assertEquals(10_000, CrystalEquipment.BowfaCreation.START_CHARGES)
        assertEquals(2000, CrystalEquipment.BowfaCreation.CORRUPT_SHARDS)
        assertEquals(1000, CrystalEquipment.BowfaCreation.CORRUPT_NPC_FEE_SHARDS)
        assertEquals(250, CrystalEquipment.BowfaCreation.REVERT_SHARDS)
    }

    @Test
    fun `singing bowl, corruption, bow seed-revert and revert warnings are wired in`() {
        val bowlPlugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/crystal_singing_bowl.plugin.kts").readText()
        assertTrue("check(singingBowls.size == 50)" in bowlPlugin, "the real cache singing bowl roster must be enumerated, not one hardcoded id")
        assertTrue("Items.CRYSTAL_ARMOUR_SEED, obj = bowl" in bowlPlugin)
        assertTrue("Items.ENHANCED_CRYSTAL_WEAPON_SEED, obj = bowl" in bowlPlugin)
        assertTrue("fun corruptBow(" in bowlPlugin && "CORRUPT_SHARDS" in bowlPlugin)
        assertTrue("fun revertBowToSeed(" in bowlPlugin && "REVERT_SHARDS" in bowlPlugin)
        assertTrue("Npcs.REESE" in bowlPlugin, "Conwenna is absent from this cache; only Reese's assist route is wired")
        assertTrue("QuestStubs.songOfTheElvesCompleted()" in bowlPlugin, "bow creation must check the Song of the Elves stub")

        val questStub = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/QuestStubs.kt").readText()
        assertTrue("fun songOfTheElvesCompleted(): Boolean = true" in questStub)

        // "crystal items need to give a warning when your try to revert" (owner instruction) - every Revert path confirms first.
        val crystalPlugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/crystal.plugin.kts").readText()
        val revertBlockStart = crystalPlugin.indexOf("REVERT_SEED.forEach")
        val revertAssignment = crystalPlugin.indexOf("player.inventory[slot] = gg.rsmod.game.model.item.Item(seed, 1)")
        val revertConfirm = crystalPlugin.indexOf("options(", revertBlockStart)
        assertTrue(revertBlockStart in 0 until revertConfirm && revertConfirm in 0 until revertAssignment, "armour Revert must ask before executing")
        val bowsPlugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/osrs_bows.plugin.kts").readText()
        assertTrue("Revert this item back into a crystal seed?" in bowsPlugin, "classic crystal bow Revert must also warn")
    }
}
