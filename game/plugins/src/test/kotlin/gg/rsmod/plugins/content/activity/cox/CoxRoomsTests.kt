package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.plugins.content.activity.cox.rooms.CoxDuration
import gg.rsmod.plugins.content.activity.cox.rooms.CoxMonsters
import gg.rsmod.plugins.content.activity.cox.rooms.CoxScaling
import gg.rsmod.plugins.content.activity.cox.rooms.CoxSupplies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CoX rooms: the Wiki scaling (OSRS Wiki DPS calculator CoX scaling, via tsps CoxMonsters.js) and the 35-minute share (besluit 3a). */
class CoxRoomsTests {
    private val solo = CoxScaling(size = 1, maxCombat = 126, maxHitpoints = 99, averageMining = 99)

    @Test
    fun `solo max Tekton keeps his Wiki hitpoints before the 35-minute share`() {
        // 300 x 126/126 = 300, +0 for one raider; 55% = 165 (besluit 3a), levels 390 x 99/99.
        val t = CoxMonsters.scale(7540, solo)!!
        assertEquals(165, t.hp)
        assertEquals(390, t.stats[CoxMonsters.ATT])
        assertEquals(52, t.maxHit)
    }

    @Test
    fun `party size raises hitpoints by half per raider and offence by the isqrt formula`() {
        val five = CoxMonsters.scale(7540, solo.copy(size = 5))!!
        // hp: 300 + 300 x (5 x 50 / 100 = 2) = 900 -> 55% = 495; offence 390 x (100 + 7 x 2 + 4)% = 460
        assertEquals(495, five.hp)
        assertEquals(460, five.stats[CoxMonsters.ATT])
    }

    @Test
    fun `scavengers are trash - no 35-minute share - and scale as solo monsters`() {
        val s = CoxMonsters.scale(7548, solo.copy(size = 8))!!
        assertEquals(30, s.hp)
    }

    @Test
    fun `guardian hitpoints start at 151 plus the average Mining`() {
        val g = CoxMonsters.scale(7569, solo)!!
        // (151 + 99) x 126/126 = 250 -> 55% = 138 (rounded half up)
        assertEquals(138, g.hp)
    }

    @Test
    fun `the Olm's head and claws follow their own formula`() {
        assertEquals(CoxDuration.hp(800, true), CoxMonsters.scale(7554, solo)!!.hp)
        assertEquals(CoxDuration.hp(600, true), CoxMonsters.scale(7553, solo)!!.hp)
        // 9 raiders: k = 8 - 3 x 1 = 5 -> head 800 + 2000
        assertEquals(CoxDuration.hp(2800, true), CoxMonsters.scale(7554, solo.copy(size = 9))!!.hp)
    }

    @Test
    fun `a shortened boss pays the full OSRS points for the whole fight`() {
        val hp = CoxMonsters.scale(7540, solo)!!.hp
        val points = CoxDuration.pointsFor(hp, true)
        assertTrue(points in 1495..1505, "165 hp x 5 x 100/55 -> $points (OSRS 300 x 5 = 1500)")
        assertEquals(50, CoxDuration.pointsFor(10, false))
    }

    @Test
    fun `raid food heals 5 to 23 by tier and every raw has a cooked id`() {
        assertEquals(listOf(5, 8, 11, 14, 17, 20, 23), CoxSupplies.FISH.map { it.heal })
        assertEquals(CoxSupplies.FISH.map { it.heal }, CoxSupplies.BATS.map { it.heal })
        assertEquals(14, CoxSupplies.FOOD.size)
        assertEquals(20856, CoxSupplies.FISH[0].raw + 1)
    }

    @Test
    fun `potions cover 7 kinds x 3 strengths x 4 doses`() {
        assertEquals(84, CoxPotions.DOSES.size)
        assertEquals(CoxPotions.Kind.OVERLOAD, CoxPotions.DOSES.getValue(20996).kind)
        assertEquals(2, CoxPotions.DOSES.getValue(20996).tier)
        assertEquals(4, CoxPotions.DOSES.getValue(20996).doses)
    }
}
