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
    fun `forms cycle on every third Zaros attack like Darkan, soul split first`() {
        // C4: Darkan NexCombat.java:138-139 + Nex.switchPrayers (13447 -> 13448 -> 13449 -> 13447) on attack counts 0, 3, 6, ...
        // (was Novite's 36-tick timer).
        var form = Npcs.NEX
        val seen = ArrayList<Int>()
        for (count in 0 until 10) {
            if (gg.rsmod.plugins.content.areas.godwars.nex.NexDarkanRules.zarosSwitch(count)) form = NexZarosForms.next(form)
            seen += form
        }
        assertEquals(Npcs.NEX_13448, seen[0], "the first Zaros attack turns Nex into her Soul Split form")
        assertEquals(
            listOf(Npcs.NEX_13448, Npcs.NEX_13448, Npcs.NEX_13448, Npcs.NEX_13449, Npcs.NEX_13449, Npcs.NEX_13449, Npcs.NEX, Npcs.NEX, Npcs.NEX, Npcs.NEX_13448),
            seen,
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
        assertTrue(encounter.contains("NexZarosForms.DEATH_FORM"))
        assertTrue(script.contains("NexDarkanRules.zarosSwitch(attackCount)"), "C4: the form switches on the attack counter")
        val response = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/combatresponse/DamageResponse.kt").readText()
        assertTrue(response.contains("NpcDeflect.onIncomingHit("))
    }

    @Test
    fun `nex pull owns its delayed player lock and rejects stale victims`() {
        val script =
            File(
                "src/main/kotlin/gg/rsmod/plugins/content/areas/godwars/nex/NexCombatScript.kt",
            ).readText()

        assertTrue(script.contains("player.lockingQueue(priority = TaskPriority.STRONG)"))
        assertTrue(script.contains("!npc.isSpawned() || npc.isDead() || !player.isOnline || player.isDead() || !NexEncounter.inArena(player.tile)"))
        assertFalse(script.contains("player.lock()"), "pull attack must not create an unowned world-queue lock")
        assertFalse(script.contains("player.unlock()"), "player-owned lockingQueue releases its lock on completion/termination")
    }

    @Test
    fun `delayed sacrifice and ice prison callbacks reject stale players`() {
        val script =
            File(
                "src/main/kotlin/gg/rsmod/plugins/content/areas/godwars/nex/NexCombatScript.kt",
            ).readText()

        assertTrue(
            script.contains(
                "if (!NexEncounter.fightActive || !player.isOnline || player.isDead() || !npc.isSpawned() || npc.isDead()) return@queue",
            ),
        )
        assertTrue(script.contains("if (!player.isOnline || player.isDead()) {") )
        assertTrue(script.contains("prison.forEach { world.remove(it) }"))
    }
}
