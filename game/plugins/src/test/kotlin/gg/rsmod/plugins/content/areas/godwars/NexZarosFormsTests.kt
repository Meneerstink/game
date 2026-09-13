package gg.rsmod.plugins.content.areas.godwars

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.godwars.nex.NexZarosForms
import gg.rsmod.plugins.content.mechanics.combatresponse.NpcDeflect
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCV-011 Q-043-c: Nex's Zaros-phase forms (667 cache icons + Novite cycle) and the shared npc deflect. */
class NexZarosFormsTests {
    @Test
    fun `every form's overhead equals its 667 cache headIcon`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val defs = DefinitionSet().also { it.loadAll(library) }
            (NexZarosForms.CYCLE.toList() + NexZarosForms.DEATH_FORM).forEach { form ->
                assertEquals(defs.get(NpcDef::class.java, form).headIcon, NexZarosForms.prayerIcon(form), "form $form")
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `forms cycle like Novite, 36 ticks each, soul split first`() {
        var form = Npcs.NEX
        var ticks = 0
        val seen = ArrayList<Int>()
        repeat(36 * 6 + 1) {
            val (next, left) = NexZarosForms.step(form, ticks)
            form = next
            ticks = left
            seen += form
        }
        assertEquals(Npcs.NEX_13448, seen[0], "the phase's first tick turns Nex into her Soul Split form")
        val changes = seen.indices.filter { it == 0 || seen[it] != seen[it - 1] }
        assertEquals(listOf(0, 36, 72, 108, 144, 180, 216), changes)
        assertEquals(
            listOf(Npcs.NEX_13448, Npcs.NEX_13449, Npcs.NEX, Npcs.NEX_13448, Npcs.NEX_13449, Npcs.NEX, Npcs.NEX_13448),
            changes.map { seen[it] },
        )
        assertTrue(NexZarosForms.soulSplitActive(Npcs.NEX_13448))
        assertFalse(NexZarosForms.soulSplitActive(Npcs.NEX))
        assertFalse(NexZarosForms.soulSplitActive(Npcs.NEX_13449))
    }

    @Test
    fun `npc deflect reflects ten percent only for the deflected style, over every icon`() {
        PrayerIcon.values().forEach { icon ->
            CombatClass.values().forEach { style ->
                val expected = if (icon.name.startsWith("DEFLECT_") && style in icon.protects) 10 else 0
                assertEquals(expected, NpcDeflect.reflected(icon.id, style, 100), "$icon vs $style")
            }
        }
        assertEquals(0, NpcDeflect.reflected(-1, CombatClass.MELEE, 100))
        assertEquals(0, NpcDeflect.reflected(PrayerIcon.DEFLECT_MELEE.id, CombatClass.MELEE, 9), "10 % rounds down")
        assertTrue(PrayerIcon.protectsAgainst(NexZarosForms.prayerIcon(Npcs.NEX_13449), CombatClass.MELEE), "13449 protects from melee")
    }

    @Test
    fun `nex sources use the forms instead of a phase-wide soul split`() {
        val nex = File("src/main/kotlin/gg/rsmod/plugins/content/areas/godwars/nex")
        val script = File(nex, "NexCombatScript.kt").readText()
        assertFalse(script.contains("PrayerIcon.SOUL_SPLIT.id else -1"), "no static Soul Split overhead")
        assertTrue(script.contains("NexZarosForms.soulSplitActive("))
        val encounter = File(nex, "NexEncounter.kt").readText()
        assertTrue(encounter.contains("tickZarosForm(") && encounter.contains("NexZarosForms.DEATH_FORM"))
        val response = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/combatresponse/DamageResponse.kt").readText()
        assertTrue(response.contains("NpcDeflect.onIncomingHit("))
    }
}
