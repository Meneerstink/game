package gg.rsmod.plugins.content.combat.strategy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: effect-only magic callbacks must not mutate stale actors after impact delay. */
class MagicDelayedEffectTests {
    @Test
    fun `effect-only callback rejects offline target and caster`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        val effectQueue = source.substringAfter("if (!spell.damaging)").substringBefore("val maxHit")

        assertTrue("target is Player && !target.isOnline" in effectQueue)
        assertTrue("pawn is Player && !pawn.isOnline" in effectQueue)
        assertTrue(effectQueue.indexOf("isOnline") < effectQueue.indexOf("applyEffect"))
    }
}
