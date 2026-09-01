package gg.rsmod.plugins.content.skills.summoning

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins every one of the 67 scrolls' sourced numbers - required Summoning level, the Summoning
 * experience for transforming a pouch into 10 scrolls, the experience for activating one, and the
 * special-move point cost - so a future edit to [SummoningScrollData] cannot silently drift.
 *
 * The table is the revision-667-era official Summoning Knowledge Base scroll table, cross-checked
 * row by row against the RuneScape Wiki. Where the two disagreed the value kept here is the one
 * two independent sources agree on, and the six rows that needed correcting carry a comment in
 * [SummoningScrollData] explaining which source won:
 *
 *  - Tireless Run transform 0.5 (was 0.8, unsupported by either source).
 *  - Rending 6 points and Goad 3 points (the two were transposed).
 *  - Abyssal Stealth 1.2/1.2 (1.9 is the modern rebalanced value).
 *  - Rise from the Ashes activation 5 (was 8, unsupported by either source).
 *  - Swamp Plague transform 4.2 (was 4.1).
 *
 * Two knowledge-base cells were rejected as corrupt because a second source contradicted them and
 * the neighbouring row explains the value: Abyssal Drain activation (the KB prints 5.5, which is
 * the Dissolve row below it; the wiki and this table both give 1.1) and Generate Compost
 * activation (KB 0.5, wiki 0.6).
 */
class SummoningScrollDataTests {
    private data class Sourced(val level: Int, val createXp: Double, val useXp: Double, val points: Int)

    @Test
    fun `every scroll matches its sourced level, experience and special move cost`() {
        val sourced =
            mapOf(
            SummoningScrollData.HOWL_SCROLL to Sourced(1, 0.1, 0.1, 3),
            SummoningScrollData.DREADFOWL_STRIKE_SCROLL to Sourced(4, 0.1, 0.1, 3),
            SummoningScrollData.EGG_SPAWN_SCROLL to Sourced(10, 0.2, 0.2, 6),
            SummoningScrollData.SLIME_SPRAY_SCROLL to Sourced(13, 0.2, 0.2, 3),
            SummoningScrollData.STONY_SHELL_SCROLL to Sourced(16, 0.2, 0.2, 12),
            SummoningScrollData.PESTER_SCROLL to Sourced(17, 0.5, 0.5, 3),
            SummoningScrollData.ELECTRIC_LASH_SCROLL to Sourced(18, 0.4, 0.4, 6),
            SummoningScrollData.VENOM_SHOT_SCROLL to Sourced(19, 0.9, 1.0, 6),
            SummoningScrollData.FIREBALL_ASSAULT_SCROLL to Sourced(22, 1.1, 1.1, 6),
            SummoningScrollData.CHEESE_FEAST_SCROLL to Sourced(23, 2.3, 2.3, 6),
            SummoningScrollData.SANDSTORM_SCROLL to Sourced(25, 2.5, 2.5, 6),
            SummoningScrollData.GENERATE_COMPOST_SCROLL to Sourced(28, 0.6, 0.6, 12),
            SummoningScrollData.EXPLODE_SCROLL to Sourced(29, 2.9, 2.9, 3),
            SummoningScrollData.VAMPIRE_TOUCH_SCROLL to Sourced(31, 1.5, 1.6, 4),
            SummoningScrollData.INSANE_FEROCITY_SCROLL to Sourced(32, 1.6, 1.6, 12),
            SummoningScrollData.MULTICHOP_SCROLL to Sourced(33, 0.7, 0.7, 3),
            SummoningScrollData.CALL_TO_ARMS_SCROLL to Sourced(34, 0.7, 0.7, 3),
            SummoningScrollData.BRONZE_BULL_RUSH_SCROLL to Sourced(36, 3.6, 3.6, 6),
            SummoningScrollData.UNBURDEN_SCROLL to Sourced(40, 0.6, 0.6, 12),
            SummoningScrollData.HERBCALL_SCROLL to Sourced(41, 0.8, 0.8, 12),
            SummoningScrollData.EVIL_FLAMES_SCROLL to Sourced(42, 2.1, 2.1, 6),
            SummoningScrollData.PETRIFYING_GAZE_SCROLL to Sourced(43, 0.9, 0.9, 3),
            SummoningScrollData.IRON_BULL_RUSH_SCROLL to Sourced(46, 4.6, 4.6, 6),
            SummoningScrollData.IMMENSE_HEAT_SCROLL to Sourced(46, 2.3, 2.3, 6),
            SummoningScrollData.THIEVING_FINGERS_SCROLL to Sourced(47, 0.9, 0.9, 12),
            SummoningScrollData.BLOOD_DRAIN_SCROLL to Sourced(49, 2.4, 2.5, 6),
            SummoningScrollData.TIRELESS_RUN_SCROLL to Sourced(52, 0.5, 0.8, 8),
            SummoningScrollData.ABYSSAL_DRAIN_SCROLL to Sourced(54, 1.1, 1.1, 6),
            SummoningScrollData.DISSOLVE_SCROLL to Sourced(55, 5.5, 5.5, 6),
            SummoningScrollData.FISH_RAIN_SCROLL to Sourced(56, 1.1, 1.1, 12),
            SummoningScrollData.STEEL_BULL_RUSH_SCROLL to Sourced(56, 5.6, 5.6, 6),
            SummoningScrollData.AMBUSH_SCROLL to Sourced(57, 5.7, 5.7, 3),
            SummoningScrollData.RENDING_SCROLL to Sourced(57, 5.7, 5.7, 6),
            SummoningScrollData.GOAD_SCROLL to Sourced(57, 5.7, 5.7, 3),
            SummoningScrollData.DOOMSPHERE_SCROLL to Sourced(58, 5.8, 5.8, 3),
            SummoningScrollData.DUST_CLOUD_SCROLL to Sourced(61, 3.0, 3.1, 6),
            SummoningScrollData.ABYSSAL_STEALTH_SCROLL to Sourced(62, 1.2, 1.2, 20),
            SummoningScrollData.OPHIDIAN_INCUBATION_SCROLL to Sourced(63, 3.1, 3.2, 3),
            SummoningScrollData.POISONOUS_BLAST_SCROLL to Sourced(64, 3.2, 3.2, 6),
            SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL to Sourced(66, 6.6, 6.6, 6),
            SummoningScrollData.TOAD_BARK_SCROLL to Sourced(66, 1.0, 1.0, 6),
            SummoningScrollData.TESTUDO_SCROLL to Sourced(67, 0.7, 0.7, 20),
            SummoningScrollData.SWALLOW_WHOLE_SCROLL to Sourced(68, 1.4, 1.4, 3),
            SummoningScrollData.FRUITFALL_SCROLL to Sourced(69, 1.4, 1.4, 6),
            SummoningScrollData.FAMINE_SCROLL to Sourced(70, 1.5, 1.5, 12),
            SummoningScrollData.ARCTIC_BLAST_SCROLL to Sourced(71, 1.1, 1.1, 6),
            SummoningScrollData.RISH_FROM_THE_ASHES_SCROLL to Sourced(72, 8.0, 5.0, 12),
            SummoningScrollData.VOLCANIC_STRENGTH_SCROLL to Sourced(73, 7.3, 7.3, 12),
            SummoningScrollData.CRUSHING_CLAW_SCROLL to Sourced(74, 3.7, 3.7, 6),
            SummoningScrollData.MANTIS_STRIKE_SCROLL to Sourced(75, 3.7, 3.8, 6),
            SummoningScrollData.INFERNO_SCROLL to Sourced(76, 1.5, 1.5, 6),
            SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL to Sourced(76, 7.6, 7.6, 6),
            SummoningScrollData.DEADLY_CLAW_SCROLL to Sourced(77, 11.4, 11.7, 6),
            SummoningScrollData.ACORN_MISSILE_SCROLL to Sourced(78, 1.6, 1.6, 6),
            SummoningScrollData.TITANS_CONSTITUTION_SCROLL to Sourced(79, 7.9, 7.9, 20),
            SummoningScrollData.REGROWTH_SCROLL to Sourced(80, 1.6, 1.6, 6),
            SummoningScrollData.SPIKE_SHOT_SCROLL to Sourced(83, 4.1, 4.2, 6),
            SummoningScrollData.EBON_THUNDER_SCROLL to Sourced(83, 8.3, 8.3, 4),
            SummoningScrollData.SWAMP_PLAGUE_SCROLL to Sourced(85, 4.2, 4.2, 6),
            SummoningScrollData.RUNE_BULL_RUSH_SCROLL to Sourced(86, 8.6, 8.6, 6),
            SummoningScrollData.HEALING_AURA_SCROLL to Sourced(88, 1.8, 1.8, 20),
            SummoningScrollData.BOIL_SCROLL to Sourced(89, 8.9, 8.9, 6),
            SummoningScrollData.MAGIC_FOCUS_SCROLL to Sourced(92, 4.6, 4.6, 20),
            SummoningScrollData.ESSENCE_SHIPMENT_SCROLL to Sourced(93, 1.9, 1.9, 6),
            SummoningScrollData.IRON_WITHIN_SCROLL to Sourced(95, 4.7, 4.8, 12),
            SummoningScrollData.WINTER_STORAGE_SCROLL to Sourced(96, 4.8, 4.8, 12),
            SummoningScrollData.STEEL_OF_LEGENDS_SCROLL to Sourced(99, 4.9, 5.0, 12),
            )
        assertEquals(
            "sourced table must cover every scroll",
            SummoningScrollData.values.size,
            sourced.size,
        )
        SummoningScrollData.values.forEach { scroll ->
            val expected = sourced.getValue(scroll)
            assertEquals("${scroll.name} level", expected.level, scroll.level)
            assertEquals("${scroll.name} creation xp", expected.createXp, scroll.creationExperience, 0.0001)
            assertEquals("${scroll.name} use xp", expected.useXp, scroll.useExperience, 0.0001)
            assertEquals("${scroll.name} special move points", expected.points, scroll.specialPoints)
        }
    }
}
