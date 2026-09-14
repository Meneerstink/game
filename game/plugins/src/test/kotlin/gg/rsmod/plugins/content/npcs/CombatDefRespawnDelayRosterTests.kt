package gg.rsmod.plugins.content.npcs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * NpcCombatDsl throws "Respawn delay must be set." while the plugins load, which aborts the whole server boot (found at boot
 * 2026-09-14 16:52 for the_mimic_combat.plugin.kts). Every set_combat_def block in the plugin sources must therefore set respawnDelay.
 */
class CombatDefRespawnDelayRosterTests {
    @Test
    fun `every combat definition sets respawnDelay`() {
        val root = File("src/main/kotlin")
        val offenders = mutableListOf<String>()
        var total = 0
        root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kts") || it.name.endsWith(".kt")) }.forEach { file ->
            val text = file.readText()
            val defs = Regex("""(?m)^\s*set_combat_def\(""").findAll(text).count()
            if (defs == 0) return@forEach
            total += defs
            val delays = Regex("""respawnDelay\s*=""").findAll(text).count()
            if (delays < defs) offenders += "${file.relativeTo(root)} defs=$defs respawnDelay=$delays"
        }
        assertTrue(total >= 226, "combat definitions found: $total")
        assertTrue(offenders.isEmpty(), "combat definitions without respawnDelay: $offenders")
    }
}
