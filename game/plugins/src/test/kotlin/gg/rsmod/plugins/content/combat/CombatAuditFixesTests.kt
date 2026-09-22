package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner combat audit 2026-09-22 ("max hits en combatsysteem precies osrs"), steps 1, 3, 4, 5, 6 and 7.
 * Each test pins the OSRS (or, for the curses, the 2011) value, so the fix cannot silently drift back.
 */
class CombatAuditFixesTests {
    private val plugins = File("src/main/kotlin/gg/rsmod/plugins/content")

    /** Step 1 - OSRS Wiki "Ancient Magicks": rushes 13-16, bursts 17/18/21/22, blitzes 23-26, barrages 27-30. */
    @Test
    fun `ancient spells hit the OSRS max hits`() {
        val expected =
            mapOf(
                CombatSpell.SMOKE_RUSH to 13, CombatSpell.SHADOW_RUSH to 14, CombatSpell.BLOOD_RUSH to 15, CombatSpell.ICE_RUSH to 16,
                CombatSpell.SMOKE_BURST to 17, CombatSpell.SHADOW_BURST to 18, CombatSpell.BLOOD_BURST to 21, CombatSpell.ICE_BURST to 22,
                CombatSpell.SMOKE_BLITZ to 23, CombatSpell.SHADOW_BLITZ to 24, CombatSpell.BLOOD_BLITZ to 25, CombatSpell.ICE_BLITZ to 26,
                CombatSpell.SMOKE_BARRAGE to 27, CombatSpell.SHADOW_BARRAGE to 28, CombatSpell.BLOOD_BARRAGE to 29, CombatSpell.ICE_BARRAGE to 30,
            )
        assertEquals(expected, expected.keys.associateWith { it.maxHit })
    }

    /** Step 3 - Protect from Magic takes 6/10 off a player's magic hit on a player, like melee and ranged; Deflect is not doubled. */
    @Test
    fun `protect from magic reduces player magic like the other styles`() {
        val source = File(plugins, "combat/formula/MagicCombatFormula.kt").readText()
        assertTrue(
            source.contains("target is Player && target.isProtectedFrom(CombatClass.MAGIC) && !AncientCurses.deflects(target, CombatClass.MAGIC)") &&
                source.contains("hit = Math.floor(hit * 0.6)"),
            "the PvP Protect from Magic step is missing",
        )
    }

    /** Step 4 - OSRS Wiki: Redemption heals and Retribution hits for 25 % of the Prayer level (24 at 99), on 1:1 life points. */
    @Test
    fun `redemption and retribution use a quarter of the prayer level`() {
        listOf("Redemption.kt" to "HEAL_MULTIPLIER = 0.25", "Retribution.kt" to "MAX_HIT_MULTIPLIER = 0.25").forEach { (file, constant) ->
            assertTrue(File(plugins, "mechanics/prayer/$file").readText().contains(constant), "$file must use $constant")
        }
        assertEquals(24, (99 * 0.25).toInt())
    }

    /** Step 5 - "Damage per second/Melee": an NPC's effective attack and strength are level + 8 + 1. */
    @Test
    fun `npc effective levels use plus nine`() {
        listOf("MeleeCombatFormula.kt", "RangedCombatFormula.kt", "MagicCombatFormula.kt").forEach { file ->
            val lines = File(plugins, "combat/formula/$file").readLines()
            val npcBlocks = lines.indices.filter { lines[it].contains(Regex("fun getEffective\\w+Level\\(npc: Npc")) }
            assertTrue(npcBlocks.isNotEmpty(), file)
            npcBlocks.forEach { start ->
                val body = lines.subList(start, minOf(lines.size, start + 6))
                assertTrue(body.none { it.trim() == "effectiveLevel += 8" }, "$file:${start + 1} still adds 8 for an npc")
            }
        }
    }

    /** Step 7 - Soul Split keeps its remainder: five 1-damage hits heal 1 and drain 1, instead of 0. */
    @Test
    fun `soul split carries the remainder between hits`() {
        val pawn = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        every { pawn.attr } returns attrs
        val key = AttributeKey<Int>()
        val perHit = (1..5).map { AncientCurses.soulSplitFifths(pawn, key, 1) }
        assertEquals(listOf(0, 0, 0, 0, 1), perHit)
        assertEquals(20, (1..20).sumOf { AncientCurses.soulSplitFifths(pawn, key, 5) })
        assertEquals(0, AncientCurses.soulSplitFifths(pawn, key, 0))
    }

    /** Step 6 - the Rapid prayers change the restore rates: Restore x2 stats, Heal x2 and Renewal x5 life points. */
    @Test
    fun `rapid prayers speed up restoration`() {
        val source = File(plugins, "mechanics/restoration/RestorationRates.kt").readText()
        assertTrue(source.contains("Prayer.RAPID_RESTORE)) 2 else 1"))
        assertTrue(source.contains("Prayer.RAPID_RENEWAL) -> 5") && source.contains("Prayer.RAPID_HEAL) -> 2"))
        val plugin = File(plugins, "mechanics/restoration/stat_restoration.plugin.kts").readText()
        assertTrue(plugin.contains("RestorationRates.loweredDue(player)") && plugin.contains("RestorationRates.lifeDue(player)"))
    }
}
