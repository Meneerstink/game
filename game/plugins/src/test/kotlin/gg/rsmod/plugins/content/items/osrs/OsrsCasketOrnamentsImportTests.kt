package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.combine.CombinationData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT casket sub-batch "casket-ornaments" against the OSRS Wiki kit and ornamented item pages: every kit attaches to its base, the
 * ornamented item "can be dismantled anytime, returning the tradeable [base] and ornament kit", and it behaves like the base in combat.
 */
class OsrsCasketOrnamentsImportTests {
    private val rows =
        listOf(
            Triple(Items.ARMADYL_GODSWORD_OR, Items.ARMADYL_GODSWORD, Items.ARMADYL_GODSWORD_ORNAMENT_KIT),
            Triple(Items.BANDOS_GODSWORD_OR, Items.BANDOS_GODSWORD, Items.BANDOS_GODSWORD_ORNAMENT_KIT),
            Triple(Items.SARADOMIN_GODSWORD_OR, Items.SARADOMIN_GODSWORD, Items.SARADOMIN_GODSWORD_ORNAMENT_KIT),
            Triple(Items.ZAMORAK_GODSWORD_OR, Items.ZAMORAK_GODSWORD, Items.ZAMORAK_GODSWORD_ORNAMENT_KIT),
            Triple(Items.DRAGON_CHAINBODY_G, Items.DRAGON_CHAINBODY, Items.DRAGON_CHAINBODY_ORNAMENT_KIT),
            Triple(Items.DRAGON_PLATELEGS_G, Items.DRAGON_PLATELEGS, Items.DRAGON_LEGS_SKIRT_ORNAMENT_KIT),
            Triple(Items.DRAGON_PLATESKIRT_G, Items.DRAGON_PLATESKIRT, Items.DRAGON_LEGS_SKIRT_ORNAMENT_KIT),
            Triple(Items.DRAGON_FULL_HELM_G, Items.DRAGON_FULL_HELM, Items.DRAGON_FULL_HELM_ORNAMENT_KIT),
            Triple(Items.DRAGON_SQ_SHIELD_G, Items.DRAGON_SQ_SHIELD, Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT),
            Triple(Items.DRAGON_KITESHIELD_G, Items.DRAGON_KITESHIELD, Items.DRAGON_KITESHIELD_ORNAMENT_KIT),
            Triple(Items.DRAGON_PLATEBODY_G, Items.DRAGON_PLATEBODY, Items.DRAGON_PLATEBODY_ORNAMENT_KIT),
            Triple(Items.DRAGON_DEFENDER_T, Items.DRAGON_DEFENDER, Items.DRAGON_DEFENDER_ORNAMENT_KIT),
            Triple(Items.DRAGON_SCIMITAR_OR, Items.DRAGON_SCIMITAR, Items.DRAGON_SCIMITAR_ORNAMENT_KIT),
        )

    @Test
    fun `every casket kit attaches to its base and dismantles back`() {
        val combos = CombinationData.values().associateBy { it.resultItem }
        rows.forEach { (ornamented, base, kit) ->
            val row = OsrsOrnamentKits.forOrnamented(ornamented) ?: error("$ornamented has no kit row")
            assertEquals(base to kit, row.base to row.kit, "kit row of $ornamented")
            val combo = combos[ornamented] ?: error("$ornamented has no combination")
            assertEquals(setOf(kit, base), combo.items.toSet(), "combination of $ornamented")
            assertEquals(0.0, combo.experience)
        }
        // "Items Kept on Death": tradeable base + tradeable kit to the killer; the Dragon defender (t) follows the defender rule instead
        // ("will become broken", repaired at Perdu), which is not modelled for any defender here - default death handling.
        rows.filter { it.first != Items.DRAGON_DEFENDER_T }.forEach { assertTrue(OsrsOrnamentKits.forPvpConversion(it.first) != null, "${it.first}") }
        assertEquals(null, OsrsOrnamentKits.forPvpConversion(Items.DRAGON_DEFENDER_T))
    }

    @Test
    fun `ornamented weapons and defender share the base combat wiring`() {
        val configs = File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText()
        listOf("Items.ARMADYL_GODSWORD_OR", "Items.BANDOS_GODSWORD_OR", "Items.SARADOMIN_GODSWORD_OR", "Items.ZAMORAK_GODSWORD_OR", "Items.DRAGON_DEFENDER_T")
            .forEach { assertTrue(it in configs, "CombatConfigs lists $it") }
        assertTrue("Items.DRAGON_DEFENDER_T" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatAnimation.kt").readText())
        val specials = "src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons"
        mapOf(
            "armadyl_godsword.plugin.kts" to "SpecialAttacks.register(50, Items.ARMADYL_GODSWORD, Items.ARMADYL_GODSWORD_OR)",
            "bandos_godsword.plugin.kts" to "SpecialAttacks.register(50, Items.BANDOS_GODSWORD, Items.BANDOS_GODSWORD_OR)",
            "saradomin_godsword.plugin.kts" to "SpecialAttacks.register(50, Items.SARADOMIN_GODSWORD, Items.SARADOMIN_GODSWORD_OR)",
            "zamorak_godsword.plugin.kts" to "SpecialAttacks.register(50, Items.ZAMORAK_GODSWORD, Items.ZAMORAK_GODSWORD_OR)",
            "melee_specials.plugin.kts" to "SpecialAttacks.register(55, Items.DRAGON_SCIMITAR, Items.DRAGON_SCIMITAR_OR)",
        ).forEach { (file, registration) -> assertTrue(registration in File("$specials/$file").readText(), registration) }
    }
}
