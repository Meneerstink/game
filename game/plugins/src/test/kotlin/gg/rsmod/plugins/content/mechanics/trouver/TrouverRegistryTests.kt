package gg.rsmod.plugins.content.mechanics.trouver

import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for [TrouverRegistry]: the data-driven store of base<->locked item pairs the [Trouver]
 * engine plugs into. [TrouverRegistry] is a global singleton, so every test clears it afterwards to
 * avoid leaking registrations into other test classes (`trouver.plugin.kts` registers its own
 * pair(s) only at real server boot, never during tests).
 */
class TrouverRegistryTests {
    @After
    fun clearRegistry() {
        TrouverRegistry.clear()
    }

    @Test
    fun `a registered pair is found by both its base and locked id`() {
        TrouverRegistry.register(TrouverLockable(baseItemId = 1, lockedItemId = 2))

        assertEquals(TrouverLockable(1, 2), TrouverRegistry.lockableFor(1))
        assertEquals(TrouverLockable(1, 2), TrouverRegistry.entryForLocked(2))
        assertTrue(TrouverRegistry.isLocked(2))
        assertFalse(TrouverRegistry.isLocked(1))
    }

    @Test
    fun `an unregistered id resolves to nothing`() {
        assertNull(TrouverRegistry.lockableFor(1))
        assertNull(TrouverRegistry.entryForLocked(2))
        assertFalse(TrouverRegistry.isLocked(1))
    }

    @Test
    fun `registering the same base id twice throws`() {
        TrouverRegistry.register(TrouverLockable(baseItemId = 1, lockedItemId = 2))

        assertFailsWith<IllegalArgumentException> {
            TrouverRegistry.register(TrouverLockable(baseItemId = 1, lockedItemId = 3))
        }
    }

    @Test
    fun `registering the same locked id twice throws`() {
        TrouverRegistry.register(TrouverLockable(baseItemId = 1, lockedItemId = 2))

        assertFailsWith<IllegalArgumentException> {
            TrouverRegistry.register(TrouverLockable(baseItemId = 4, lockedItemId = 2))
        }
    }

    @Test
    fun `registering a pair whose base and locked ids are the same throws`() {
        assertFailsWith<IllegalArgumentException> {
            TrouverRegistry.register(TrouverLockable(baseItemId = 1, lockedItemId = 1))
        }
    }

    @Test
    fun `clear removes every registration`() {
        TrouverRegistry.register(TrouverLockable(baseItemId = 1, lockedItemId = 2))
        TrouverRegistry.clear()

        assertNull(TrouverRegistry.lockableFor(1))
        assertTrue(TrouverRegistry.all().isEmpty())
    }
}
