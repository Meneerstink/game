package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import gg.rsmod.plugins.content.magic.MagicStaves
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSRS import batch "kits" (2026-09-17): every ornamented item behaves exactly like its base item. */
class OsrsKitsImportTests {
    private val yml =
        ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile()).associateBy { it.path("id").asInt() }

    private val combatFields =
        listOf(
            "equip_slot", "equip_type", "weapon_type", "attack_speed", "attack_stab", "attack_slash", "attack_crush", "attack_magic",
            "attack_ranged", "defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged", "melee_strength",
            "prayer", "ranged_strength", "attack_audio", "skill_reqs",
        )

    private fun combat(id: Int) = combatFields.associateWith { yml.getValue(id).path("equipment").path(it).toString() }

    private val pairs =
        OsrsOrnamentKits.ALL.filter { it.ornamented in Items.DRAGON_BOOTS_G..Items.MYSTIC_STEAM_STAFF_OR }.map { Triple(it.ornamented, it.base, it.kit) } +
            OsrsOrnamentKits.CONSUMED.map { Triple(it.ornamented, it.base, it.kit) }

    @Test
    fun `every ornamented item has the combat metadata of its base item and is untradeable`() {
        assertEquals(18, pairs.size, "7 dismantle rows + 11 used-up kits (batches kits and kits2)")
        pairs.forEach { (ornamented, base, kit) ->
            assertEquals(combat(base), combat(ornamented), "ornamented $ornamented vs base $base")
            assertFalse(yml.getValue(ornamented).path("tradeable").asBoolean(), "$ornamented untradeable")
            assertTrue(yml.getValue(kit).path("tradeable").asBoolean(), "kit $kit tradeable")
            val combo = CombinationData.values().single { it.resultItem == ornamented }
            assertEquals(setOf(base, kit), combo.items.toSet(), "combine $ornamented")
        }
    }

    @Test
    fun `combat consumers treat ornamented items like the base`() {
        assertTrue(Items.LAVA_BATTLESTAFF_OR in MagicStaves.FIRE_RUNE.staves && Items.LAVA_BATTLESTAFF_OR in MagicStaves.EARTH_RUNE.staves)
        assertTrue(Items.STEAM_BATTLESTAFF_OR in MagicStaves.FIRE_RUNE.staves && Items.STEAM_BATTLESTAFF_OR in MagicStaves.WATER_RUNE.staves)
        assertTrue(Items.MYSTIC_STEAM_STAFF_OR in MagicStaves.FIRE_RUNE.staves && Items.MYSTIC_STEAM_STAFF_OR in MagicStaves.WATER_RUNE.staves)
        val formula = File("src/main/kotlin/gg/rsmod/plugins/content/combat/formula/MeleeCombatFormula.kt").readText()
        assertTrue("Items.TZHAAR_KET_OM_T" in formula && "Items.BERSERKER_NECKLACE, Items.BERSERKER_NECKLACE_OR" in formula)
        assertTrue("Items.RUNE_DEFENDER_T" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText())
        assertTrue("Items.RUNE_DEFENDER_T" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatAnimation.kt").readText())
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/melee_specials.plugin.kts").readText()
        assertTrue("Items.ABYSSAL_WHIP, Items.FROZEN_ABYSSAL_WHIP, Items.VOLCANIC_ABYSSAL_WHIP" in specials)
        val actions = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/osrs_pilot_items.plugin.kts").readText()
        assertTrue("OsrsOrnamentKits.CONSUMED.forEach" in actions && "Items.CLEANING_CLOTH" in actions)
        assertEquals(setOf(Items.FROZEN_ABYSSAL_WHIP, Items.VOLCANIC_ABYSSAL_WHIP, Items.DARK_BOW_GREEN, Items.DARK_BOW_BLUE, Items.DARK_BOW_YELLOW, Items.DARK_BOW_WHITE), OsrsOrnamentKits.CONSUMED.filter { it.cleaningCloth }.map { it.ornamented }.toSet())
        assertEquals(listOf(Items.DRAGON_PICKAXE_OR), OsrsOrnamentKits.CONSUMED.filter { it.returnsKit }.map { it.ornamented }, "only the Zalcano shard is returned")
        val pickaxes = gg.rsmod.plugins.content.skills.mining.PickaxeType.values.associateBy { it.item }
        listOf(Items.DRAGON_PICKAXE_OR_UPGRADED, Items.DRAGON_PICKAXE_OR).forEach { assertEquals(pickaxes.getValue(Items.DRAGON_PICKAXE).level, pickaxes.getValue(it).level) }
        val instant = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/instant_specials.plugin.kts").readText()
        assertTrue("Items.DRAGON_PICKAXE_OR_UPGRADED, Items.DRAGON_PICKAXE_OR" in instant)
        val bowTypes = gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType.values.associateBy { it.item }
        val rangedStrategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows.DARK_BOWS.forEach {
            assertTrue(bowTypes.getValue(it).ammo.contentEquals(bowTypes.getValue(Items.DARK_BOW).ammo), "dark bow ammo for $it")
        }
        // Audit round 2026-09-17b: OSRS Wiki "Dark bow" - "attackrange = 10", "the maximum possible attack range of
        // 10, so the longrange attack style will not increase its attack range" - every dark bow variant, not the
        // 9-tile value the earlier kits2 fix used.
        assertTrue("in Bows.CRYSTAL_BOWS, in Bows.DARK_BOWS -> 10" in rangedStrategy, "every dark bow variant has the sourced 10-tile range")
    }
}
