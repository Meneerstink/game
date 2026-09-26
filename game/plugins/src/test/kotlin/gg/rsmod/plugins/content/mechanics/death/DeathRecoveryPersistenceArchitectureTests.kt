package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.DEATHS_OFFICE_RETURN_ATTR
import gg.rsmod.game.model.attr.DEATH_COFFER_ATTR
import gg.rsmod.game.model.attr.DEATH_LOOT_RESOLVED_ATTR
import gg.rsmod.game.model.attr.DEATH_TUTORIAL_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_ANGEL_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_TICKS_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_TILE_ATTR
import gg.rsmod.game.model.attr.SKULL_ICON_ATTR
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
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
 *  - the gravestone, coffer and tutorial state are stored under [AttributeKey]s that
 *    declare a [persistenceKey], not left as transient/in-memory-only state.
 *
 * [DEATH_LOOT_RESOLVED_ATTR] is persisted (audit X-11) so a death replayed on
 * login after a force-logout or crash-save can't resolve the items twice, and
 * it is marked `resetOnDeath` so it never outlives the death that set it.
 */
class DeathRecoveryPersistenceArchitectureTests {
    @Test
    fun `death's office (unlimited) and the gravestone (120 slots) are real persisted containers`() {
        assertEquals("death_recovery", DEATH_RECOVERY_KEY.name, "the save key of Death's Office stays the old one: saved items keep loading")
        assertEquals(gg.rsmod.game.model.container.key.DEATH_RECOVERY_CAPACITY, DEATH_RECOVERY_KEY.capacity, "owner 2026-09-26: Death's Office is unlimited")
        assertTrue(DEATH_RECOVERY_KEY.capacity >= 4_000)
        assertEquals(ContainerStackType.NORMAL, DEATH_RECOVERY_KEY.stackType)
        assertEquals("gravestone", GRAVESTONE_KEY.name)
        assertEquals(120, GRAVESTONE_KEY.capacity, "OSRS Wiki Grave: a gravestone functions similarly to a bank with 120 slots")
        assertEquals(ContainerStackType.NORMAL, GRAVESTONE_KEY.stackType, "unstackables take a slot each (the 28 / 56 rules)")
    }

    @Test
    fun `gravestone, coffer and tutorial state survive a logout and a death`() {
        listOf(GRAVESTONE_TILE_ATTR, GRAVESTONE_TICKS_ATTR, GRAVESTONE_ANGEL_ATTR, DEATH_COFFER_ATTR, DEATH_TUTORIAL_ATTR, DEATHS_OFFICE_RETURN_ATTR).forEach {
            assertNotNull(it.persistenceKey, "$it must be saved")
            assertFalse(it.resetOnDeath, "$it must not be cleared by the next death")
        }
    }

    @Test
    fun `skull state is persisted so it survives a reconnect`() {
        assertNotNull(SKULL_ICON_ATTR.persistenceKey)
    }

    @Test
    fun `the death-loot resolution guard survives a crash mid-death but never outlives that death`() {
        assertEquals(
            "death_loot_resolved",
            DEATH_LOOT_RESOLVED_ATTR.persistenceKey,
            "audit X-11: a death replayed on login must see that its items were already resolved",
        )
        assertTrue(
            DEATH_LOOT_RESOLVED_ATTR.resetOnDeath,
            "must be cleared by PlayerDeathAction's own resetOnDeath sweep so it only guards the death that set it",
        )
    }
}
