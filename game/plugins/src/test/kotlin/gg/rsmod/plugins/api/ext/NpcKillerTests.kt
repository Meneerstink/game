package gg.rsmod.plugins.api.ext

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the one shared way an npc script asks who killed it.
 *
 * `npc.damageMap.getMostDamage()!! as Player` throws whenever an npc dies without a player attacker
 * in its damage map - a scripted or environmental kill, a kill by another npc, or an npc that dealt
 * the most damage itself. `NpcDeathAction` isolates its hooks, so the throw was swallowed and the
 * npc silently dropped nothing and played no death sound instead of crashing visibly. Live proof:
 * `guard_level_21.plugin.kts` NullPointerException in the 2026-09-15 server log.
 */
class NpcKillerTests {
    private val scripts: List<File> =
        File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }
            .toList()

    @Test
    fun `the shared killer accessor is null-safe`() {
        val ext = File("src/main/kotlin/gg/rsmod/plugins/api/ext/NpcExt.kt").readText()

        assertTrue("fun Npc.killer(): Player? = damageMap.getMostDamage() as? Player" in ext)
    }

    @Test
    fun `no script force-casts the most damaging pawn to a player`() {
        val offenders =
            scripts
                // NpcExt.kt is the file that replaces the pattern; its documentation quotes it.
                .filterNot { it.name == "NpcExt.kt" }
                .filter { "getMostDamage()!!" in it.readText() }
                .map { it.path }

        assertEquals(emptyList(), offenders, "use npc.killer() instead of getMostDamage()!! as Player")
    }

    @Test
    fun `every killer lookup handles the no-player-killer case`() {
        val offenders =
            scripts
                .flatMap { file -> file.readLines().map { file.path to it } }
                .filter { (_, line) -> "npc.killer()" in line }
                .filterNot { (_, line) -> "?: return@on_npc_death" in line || "?: return@on_npc_pre_death" in line }
                .map { it.first }
                .distinct()

        assertEquals(emptyList(), offenders, "npc.killer() must be guarded with an elvis return")
    }
}
