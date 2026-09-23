package gg.rsmod.game.model.combat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the kill-credit rule `PlayerDeathAction` depends on.
 *
 * A damage map has no notion of "this fight": it accumulates for as long as it lives, so the
 * untimed [DamageMap.getMostDamage] returns the highest *lifetime* damage dealer. Two things keep
 * that from turning a PvM death into a PvP one - the map is cleared on every player death (an npc's
 * already was, through `Npc.reset`), and the killer snapshot only considers damage dealt inside the
 * PvP aggressor window. Both are asserted on the source, in the same style as
 * `PlayerDeathHookIsolationTests`, because a [gg.rsmod.game.model.entity.Pawn] key cannot be built
 * in this module's tests without a full `World`.
 */
class DamageMapTests {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun `most damage can be restricted to a recent time frame`() {
        val source = source("src/main/kotlin/gg/rsmod/game/model/combat/DamageMap.kt")

        assertTrue("fun getMostDamage(timeFrameMs: Long? = null): Pawn?" in source)
        assertTrue("System.currentTimeMillis() - it.value.lastHit < timeFrameMs" in source)
        assertTrue("fun reset()" in source)
    }

    @Test
    fun `a player death clears the damage map and credits only recent damage`() {
        val source = source("src/main/kotlin/gg/rsmod/game/action/PlayerDeathAction.kt")

        assertTrue("player.damageMap.getMostDamage(KILL_CREDIT_WINDOW_MS)" in source)
        assertTrue("private const val KILL_CREDIT_WINDOW_MS = 60_000L" in source)
        assertTrue("player.damageMap.reset()" in source)
    }

    @Test
    fun `an npc death still clears its own damage map`() {
        val source = source("src/main/kotlin/gg/rsmod/game/action/NpcDeathAction.kt")

        assertTrue("damageMap.reset()" in source)
    }
}
