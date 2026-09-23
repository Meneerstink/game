package gg.rsmod.game.model.interf

import gg.rsmod.game.model.interf.listener.InterfaceListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceSetTests {
    private class RecordingListener : InterfaceListener {
        val opened = mutableListOf<Int>()
        val closed = mutableListOf<Int>()

        override fun onInterfaceOpen(interfaceId: Int) {
            opened += interfaceId
        }

        override fun onInterfaceClose(interfaceId: Int) {
            closed += interfaceId
        }
    }

    @Test
    fun `opening a modal on another destination retires the previous modal hash`() {
        val listener = RecordingListener()
        val interfaces = InterfaceSet(listener)
        val fixedHash = (548 shl 16) or 9

        assertEquals(-1, interfaces.openModal(548, 9, 667))
        assertEquals(fixedHash, interfaces.openModal(746, 12, 762))

        assertFalse(interfaces.isOccupied(548, 9))
        assertTrue(interfaces.isOccupied(746, 12))
        assertEquals(762, interfaces.getModal())
        assertEquals(listOf(667), listener.closed)
    }

    @Test
    fun `closing a modal by destination clears the modal marker`() {
        val listener = RecordingListener()
        val interfaces = InterfaceSet(listener)

        interfaces.openModal(548, 9, 667)
        assertEquals(667, interfaces.close(548, 9))

        assertEquals(-1, interfaces.getModal())
        assertFalse(interfaces.isOccupied(548, 9))
        assertEquals(listOf(667), listener.closed)
    }

    @Test
    fun `display rebuild closes its modal and removes every old gameframe entry`() {
        val listener = RecordingListener()
        val interfaces = InterfaceSet(listener)

        interfaces.setVisible(548, 0, true)
        interfaces.open(548, 208, 679)
        interfaces.open(548, 199, 670)
        interfaces.openModal(548, 9, 667)

        interfaces.clearDisplay(548)

        assertFalse(interfaces.isOccupied(548, 0))
        assertFalse(interfaces.isOccupied(548, 208))
        assertFalse(interfaces.isOccupied(548, 199))
        assertFalse(interfaces.isOccupied(548, 9))
        assertEquals(-1, interfaces.getModal())
        assertEquals(listOf(667), listener.closed)
    }
}
