package gg.rsmod.game.model

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.TimerMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N127 (server memory: 1.6 million static objects, 4.9 million HashMaps): an AttributeMap / TimerMap nothing writes to holds no
 * map at all, and behaves exactly like an empty one; the first write makes it.
 */
class LazyEntityMapsTests {
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(target)

    @Test
    fun `an unused attribute map holds no map and reads as empty`() {
        val attr = AttributeMap()
        val key = AttributeKey<Int>()
        assertNull(field(attr, "stored"), "no HashMap before the first write")
        assertNull(attr[key])
        assertEquals(7, attr.getOrDefault(key, 7))
        assertFalse(attr.has(key))
        attr.remove(key)
        attr.clear()
        attr.removeIf { true }
        assertTrue(attr.toPersistentMap().isEmpty())
        assertNull(field(attr, "stored"), "reads and removes never make the map")
        attr[key] = 3
        assertEquals(3, attr[key])
        assertTrue(attr.has(key))
        attr.remove(key)
        assertFalse(attr.has(key))
    }

    @Test
    fun `an unused timer map holds no map and reads as empty`() {
        val timers = TimerMap()
        val key = TimerKey()
        assertNull(field(timers, "stored"))
        assertNull(field(timers, "pausedKeys"))
        assertFalse(timers.has(key))
        assertFalse(timers.exists(key))
        assertFalse(timers.isNotEmpty)
        assertFalse(timers.isPaused(key))
        timers.remove(key)
        timers.clear()
        timers.resume(key)
        assertTrue(timers.toPersistentTimers().isEmpty())
        assertNull(field(timers, "stored"), "reads and removes never make the map")
        assertNull(field(timers, "pausedKeys"))
        timers[key] = 5
        assertTrue(timers.has(key) && timers[key] == 5 && timers.isNotEmpty)
        timers.pause(key)
        assertTrue(timers.isPaused(key))
        // The cycle pass changes values through the live map.
        timers.getTimers().entries.forEach { it.setValue(it.value - 1) }
        assertEquals(4, timers[key])
        timers.remove(key)
        assertFalse(timers.exists(key) || timers.isPaused(key))
    }
}
