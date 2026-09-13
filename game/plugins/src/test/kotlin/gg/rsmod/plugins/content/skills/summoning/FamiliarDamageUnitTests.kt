package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.content.combat.dealHit
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-010 A1 (owner live 2026-09-13: "targets die very quickly to familiars").
 *
 * Root cause: familiar damage figures are on Void's x10 life-point unit but were passed straight into
 * the 1:1 [dealHit]. These tests pin the single conversion boundary for the whole roster.
 */
class FamiliarDamageUnitTests {
    private val summoningMain = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning")
    private val voidCombatToml = File("C:/RSPS/Donors/void/data/skill/summoning/summoning.combat.toml")

    @Test
    fun `no familiar damage bypasses the ledger to hitpoint boundary`() {
        assertTrue(summoningMain.isDirectory, "run from the plugins module: ${summoningMain.absolutePath}")
        val offenders = summoningMain.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (Regex("""\.dealHit\(""").containsMatchIn(code)) "${file.name}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        // The only raw dealHit is the one inside FamiliarCombat.dealLedgerHit itself.
        assertEquals(1, offenders.size, "raw dealHit calls in Summoning:\n" + offenders.joinToString("\n"))
        assertTrue(offenders.single().startsWith("FamiliarCombat.kt:"), offenders.single())
    }

    @Test
    fun `every fighting familiar ledger max hit is Void's x10 target_hit max`() {
        assertTrue(voidCombatToml.isFile, "Void donor data missing: $voidCombatToml")
        val maxesByFamiliar = mutableMapOf<String, MutableSet<Int>>()
        var section: String? = null
        voidCombatToml.readLines().forEach { raw ->
            val line = raw.trim()
            Regex("""^\[([a-z0-9_]+)_familiar\.[a-z_]+]$""").find(line)?.let { section = it.groupValues[1] }
            Regex("""target_hit\s*=.*max\s*=\s*(\d+)""").find(line)?.let { m ->
                section?.let { maxesByFamiliar.getOrPut(it) { mutableSetOf() }.add(m.groupValues[1].toInt()) }
            }
        }
        val compared = mutableListOf<String>()
        val mismatches = SummoningCombatDefinitions.executableCombatValues.mapNotNull { definition ->
            val key = definition.pouch.name.lowercase()
            val voidMaxes = maxesByFamiliar[key] ?: return@mapNotNull null
            compared += key
            if (definition.maxHit in voidMaxes) null else "${definition.pouch.name}: ledger ${definition.maxHit}, Void $voidMaxes"
        }
        println("FamiliarDamageUnitTests: compared ${compared.size} familiars with Void target_hit; unmatched names: " +
            SummoningCombatDefinitions.executableCombatValues.map { it.pouch.name.lowercase() }.filter { it !in maxesByFamiliar })
        assertTrue(compared.size >= 40, "too few familiars matched Void names: ${compared.size}")
        assertTrue(mismatches.isEmpty(), "ledger max hit is not on Void's x10 unit:\n" + mismatches.joinToString("\n"))
    }

    @Test
    fun `dealLedgerHit hands dealHit one tenth of the ledger max for every fighting familiar`() {
        mockkStatic("gg.rsmod.plugins.content.combat.PawnExtKt")
        try {
            val familiar = mockk<Npc>(relaxed = true)
            val target = mockk<Npc>(relaxed = true)
            every { familiar.dealHit(target, any(), any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
            SummoningCombatDefinitions.executableCombatValues.forEach { definition ->
                FamiliarCombat.dealLedgerHit(familiar, target, definition.maxHit.toDouble(), true, 1, HitType.MELEE)
                verify(atLeast = 1) { familiar.dealHit(target, 0.1, definition.maxHit / 10.0, true, 1, any(), HitType.MELEE) }
            }
            // Vampyre bat 40 -> 4 HP, Pack yak 125 -> 12.5 HP (the owner-reported pair).
            assertEquals(4.0, FamiliarCombat.ledgerToHitpoints(SummoningCombatDefinitions.get(SummoningPouchData.VAMPYRE_BAT).maxHit.toDouble()))
            assertEquals(12.5, FamiliarCombat.ledgerToHitpoints(SummoningCombatDefinitions.get(SummoningPouchData.PACK_YAK).maxHit.toDouble()))
            // A ledger max below one real hitpoint (full-health Phoenix rebirth) never lands and never throws.
            FamiliarCombat.dealLedgerHit(familiar, target, 0.0, true, 2, HitType.MAGIC)
            verify { familiar.dealHit(target, 0.1, 1.0, false, 2, any(), HitType.MAGIC) }
        } finally {
            unmockkStatic("gg.rsmod.plugins.content.combat.PawnExtKt")
        }
    }
}
