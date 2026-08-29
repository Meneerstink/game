package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.DEATH_LOOT_RESOLVED_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.attr.SKULL_ICON_ATTR
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the *architecture* that lets PvM death-recovery state (and skull
 * state) survive a logout/reconnect, rather than simulating a full
 * save/load round-trip (no core-module test harness for that exists at this
 * time).
 *
 * The player save/load system (see [gg.rsmod.game.model.attr.AttributeMap]
 * and the world's player-persistence service) persists any attribute whose
 * [gg.rsmod.game.model.attr.AttributeKey.persistenceKey] is non-null, and
 * persists item containers registered against the player by their
 * [gg.rsmod.game.model.container.key.ContainerKey]. So an unreclaimed
 * PvM death's state survives a reconnect if and only if:
 *  - the death-recovery item container itself is a real, named, persisted
 *    [gg.rsmod.game.model.container.key.ContainerKey] (as opposed to a
 *    transient/derived list), and
 *  - the expiry and fee attached to it are stored under [AttributeKey]s that
 *    declare a [persistenceKey], not left as transient/in-memory-only state.
 *
 * Conversely, [DEATH_LOOT_RESOLVED_ATTR] must NOT survive a reconnect (or
 * even survive past the death that set it) - it's a same-call re-entrancy
 * guard, not saved state - so it must have no [persistenceKey] and must be
 * marked `resetOnDeath`.
 */
class DeathRecoveryPersistenceArchitectureTests {
    @Test
    fun `death-recovery container is a real persisted container distinct from inventory and equipment`() {
        assertEquals("death_recovery", DEATH_RECOVERY_KEY.name)
        assertEquals(42, DEATH_RECOVERY_KEY.capacity, "must fit every inventory + equipment slot lost in one death")
        assertEquals(ContainerStackType.NORMAL, DEATH_RECOVERY_KEY.stackType)
    }

    @Test
    fun `death-recovery expiry and fee attributes are persisted`() {
        assertNotNull(DEATH_RECOVERY_EXPIRY_ATTR.persistenceKey, "expiry must survive logout or it can never expire correctly")
        assertNotNull(DEATH_RECOVERY_FEE_ATTR.persistenceKey, "the exact fee charged must survive logout")
        assertFalse(DEATH_RECOVERY_EXPIRY_ATTR.resetOnDeath, "a later, unrelated death must not silently clear a pending recovery")
        assertFalse(DEATH_RECOVERY_FEE_ATTR.resetOnDeath)
    }

    @Test
    fun `skull state is persisted so it survives a reconnect`() {
        assertNotNull(SKULL_ICON_ATTR.persistenceKey)
    }

    @Test
    fun `the death-loot resolution guard is transient and self-clearing, never persisted`() {
        assertNull(
            DEATH_LOOT_RESOLVED_ATTR.persistenceKey,
            "this is a same-death re-entrancy guard only; persisting it would permanently block death loot after a save/load",
        )
        assertTrue(
            DEATH_LOOT_RESOLVED_ATTR.resetOnDeath,
            "must be cleared by PlayerDeathAction's own resetOnDeath sweep so it only guards the death that set it",
        )
    }
}
