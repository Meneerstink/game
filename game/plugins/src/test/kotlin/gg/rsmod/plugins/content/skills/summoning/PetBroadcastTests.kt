package gg.rsmod.plugins.content.skills.summoning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N407 (owner 2026-10-31: "paarse wereldmelding bij een pet-drop: ja"). */
class PetBroadcastTests {
    @Test
    fun `a received pet is broadcast in the chest purple`() {
        assertEquals("<col=a53fff>Anudd has received a pet: Pet general graardor!</col>", Pets.broadcast("Anudd", "Pet general graardor"))
        listOf(Pets.Award.FOLLOWER, Pets.Award.BACKPACK, Pets.Award.BANK, Pets.Award.LOST).forEach {
            assertTrue(Pets.petBroadcast(it, purchased = false, source = "npc:6260"), "$it")
            assertTrue(Pets.petBroadcast(it, purchased = false, source = null), "$it skilling")
        }
    }

    @Test
    fun `no broadcast for a duplicate, a bought pet or a chest pet (the chest broadcasts its own reward)`() {
        assertFalse(Pets.petBroadcast(Pets.Award.DUPLICATE, purchased = false, source = "npc:6260"))
        assertFalse(Pets.petBroadcast(Pets.Award.FOLLOWER, purchased = true, source = null))
        assertFalse(Pets.petBroadcast(Pets.Award.FOLLOWER, purchased = false, source = "chest"))
    }
}
