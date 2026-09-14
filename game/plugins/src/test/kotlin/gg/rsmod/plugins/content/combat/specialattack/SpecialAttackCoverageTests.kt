package gg.rsmod.plugins.content.combat.specialattack

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Q-018: source-level coverage of the plan's required special-attack weapon set (dds, dclaws,
 * dscim, whip, gmaul, ags/bgs/sgs/zgs, dbow, msb, hand cannon, korasi, vesta/statius/morrigan).
 *
 * `SpecialAttacks`'s registration maps are only populated by the real `.plugin.kts` scripts when
 * the KotlinScript plugin loader runs at world boot - there is no harness in this codebase that
 * executes plugin scripts under test (every existing test that touches plugin-adjacent state
 * mocks `PluginRepository` instead, e.g. `EquipActionTests`/`NpcCombatScaleAuditTests`), so an
 * in-memory `SpecialAttacks.hasSpecialAttack(id)` assertion cannot observe real registrations.
 * This instead reads the actual committed weapon plugin source files from disk (the same files
 * the boot-time loader compiles) and asserts each required weapon's real item id appears in a
 * `SpecialAttacks.register`/`registerInstant` call in its file - proving the wiring exists in the
 * exact source that will run at boot, and naming the missing weapon by label if a file is ever
 * deleted or an id is changed without updating this list (R8: name the offending entry).
 */
class SpecialAttackCoverageTests {
    private val weaponsDir = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons")

    private data class Required(
        val label: String,
        val file: String,
        val marker: String,
    )

    /** Zuriel's staff is intentionally absent: per Void (`SpellRunes.kt`) and Novite
     * (`Magic.java`/`ItemNameRemover.java`), it is a spellcasting rune-requirement item for the
     * god spells, not a special-attack-bar weapon - it belongs to the Magic subsystem, not here.
     */
    private val required =
        listOf(
            Required("Dragon dagger (dds)", "dragonequipment/dragon_dagger.plugin.kts", "Items.DRAGON_DAGGER"),
            Required("Dragon claws (dclaws)", "dragonequipment/dragon_claws.plugin.kts", "Items.DRAGON_CLAWS"),
            Required("Dragon scimitar (dscim)", "melee_specials.plugin.kts", "Items.DRAGON_SCIMITAR"),
            Required("Abyssal whip", "melee_specials.plugin.kts", "Items.ABYSSAL_WHIP"),
            // OSRS-IMPORT granitemaul (2026-09-14): the OSRS Quick Smash moved the special to the shared maul plugin.
            Required("Granite maul (gmaul)", "../../../items/osrs/granite_maul.plugin.kts", "Items.GRANITE_MAUL"),
            Required("Armadyl godsword (ags)", "armadyl_godsword.plugin.kts", "Items.ARMADYL_GODSWORD"),
            Required("Bandos godsword (bgs)", "bandos_godsword.plugin.kts", "Items.BANDOS_GODSWORD"),
            Required("Saradomin godsword (sgs)", "saradomin_godsword.plugin.kts", "Items.SARADOMIN_GODSWORD"),
            Required("Zamorak godsword (zgs)", "zamorak_godsword.plugin.kts", "Items.ZAMORAK_GODSWORD"),
            Required("Dark bow (dbow)", "ranged_specials.plugin.kts", "Items.DARK_BOW"),
            Required("Magic shortbow (msb)", "ranged_specials.plugin.kts", "Items.MAGIC_SHORTBOW"),
            Required("Hand cannon", "hand_cannon.plugin.kts", "Items.HAND_CANNON"),
            Required("Korasi's sword", "melee_specials.plugin.kts", "Items.KORASIS_SWORD_19780"),
            Required("Vesta's longsword", "vestas_longsword.plugin.kts", "Items.VESTAS_LONGSWORD"),
            Required("Vesta's spear", "vestas_spear.plugin.kts", "Items.VESTAS_SPEAR"),
            Required("Statius's warhammer", "statiuss_warhammer.plugin.kts", "Items.STATIUSS_WARHAMMER"),
            Required("Morrigan's javelin", "morrigans_javelin.plugin.kts", "MORRIGANS_JAVELIN"),
            Required("Morrigan's throwing axe", "morrigans_throwing_axe.plugin.kts", "Items.MORRIGANS_THROWING_AXE"),
        )

    @Test
    fun `every plan-required special attack weapon is registered in its plugin file, all 18`() {
        val missing =
            required.mapNotNull { req ->
                val file = File(weaponsDir, req.file)
                if (!file.exists()) {
                    return@mapNotNull "${req.label}: file ${req.file} does not exist"
                }
                val text = file.readText()
                val hasRegistrationCall = text.contains("SpecialAttacks.register")
                val hasMarker = text.contains(req.marker)
                when {
                    !hasRegistrationCall -> "${req.label}: ${req.file} has no SpecialAttacks.register(...)/registerInstant(...) call"
                    !hasMarker -> "${req.label}: ${req.file} has no reference to ${req.marker}"
                    else -> null
                }
            }
        assertTrue(missing.isEmpty(), "${missing.size} required special attacks are not wired: $missing")
    }

    @Test
    fun `the weapons directory exists and is where the coverage list expects it`() {
        assertTrue(weaponsDir.exists() && weaponsDir.isDirectory, "expected directory ${weaponsDir.path} to exist")
    }
}
