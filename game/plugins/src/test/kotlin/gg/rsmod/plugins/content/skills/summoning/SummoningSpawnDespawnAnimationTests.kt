package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.AnimDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Roster-wide ledger check for [SummoningSpawnDespawnAnimations]: every familiar's sourced
 * spawn/despawn id must actually decode as an `AnimDef` in the real 667 production cache (loaded
 * once via [SummoningTestCache.definitions]), the same cache the running server reads. A missing
 * id here would mean the client is sent an animation that does not exist, rather than a genuine
 * source gap - this is the automated half of the verification `runSeqSoundProbeTool` already did
 * by hand for the whole table (see `SummoningSpawnDespawnAnimations`'s doc comment).
 */
class SummoningSpawnDespawnAnimationTests {
    @Test
    fun `every familiar has a spawn and despawn animation entry`() {
        assertEquals(78, SummoningPouchData.values.size)
        SummoningPouchData.values.forEach { pouch ->
            // Accessing both is the completeness check: a pouch missing from the backing map
            // throws NoSuchElementException here and names the offending entry via the assertion
            // context, rather than one exemplar standing in for the whole roster.
            SummoningSpawnDespawnAnimations.spawnAnim(pouch)
            SummoningSpawnDespawnAnimations.despawnAnim(pouch)
        }
    }

    @Test
    fun `every sourced spawn and despawn id exists as a real AnimDef in the 667 cache`() {
        val definitions = SummoningTestCache.definitions
        SummoningPouchData.values.forEach { pouch ->
            val spawn = SummoningSpawnDespawnAnimations.spawnAnim(pouch)
            val despawn = SummoningSpawnDespawnAnimations.despawnAnim(pouch)
            if (spawn != -1) {
                assertNotNull(definitions.getNullable(AnimDef::class.java, spawn), "${pouch.name} spawn anim $spawn missing from 667 cache")
            }
            if (despawn != -1) {
                assertNotNull(definitions.getNullable(AnimDef::class.java, despawn), "${pouch.name} despawn anim $despawn missing from 667 cache")
            }
        }
    }

    @Test
    fun `albino rat is the one recorded source blocker, with no animation played`() {
        assertEquals(-1, SummoningSpawnDespawnAnimations.spawnAnim(SummoningPouchData.ALBINO_RAT))
        assertEquals(-1, SummoningSpawnDespawnAnimations.despawnAnim(SummoningPouchData.ALBINO_RAT))
        // Darkan's own ids for Albino rat (16080/16081) genuinely do not exist in this cache -
        // confirms the blocker is real, not just an unresolved lookup.
        val definitions = SummoningTestCache.definitions
        assertNull(definitions.getNullable(AnimDef::class.java, 16080))
        assertNull(definitions.getNullable(AnimDef::class.java, 16081))
    }

    @Test
    fun `no other familiar shares albino rat's blocked state`() {
        val blocked =
            SummoningPouchData.values.filter {
                SummoningSpawnDespawnAnimations.spawnAnim(it) == -1 || SummoningSpawnDespawnAnimations.despawnAnim(it) == -1
            }
        assertEquals(listOf(SummoningPouchData.ALBINO_RAT), blocked)
    }
}
