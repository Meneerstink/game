package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner live buglist 2026-09-17c: the rules behind the fixed reports, each over its whole roster (not one exemplar).
 */
class OwnerBuglist20260917cTests {
    @Test
    fun `every breath-charged shield swaps between its charged and uncharged item with its charges`() {
        DragonfireShield.FAMILIES.forEach { family ->
            val charged = DragonfireShield.withCharges(Item(family.uncharged), 7)
            assertEquals(family.charged, charged.id, "${family.noun} ${family.uncharged} with charges is the charged item")
            assertEquals(7, DragonfireShield.charges(charged))
            val emptied = DragonfireShield.withCharges(charged, 0)
            assertEquals(family.uncharged, emptied.id, "${family.noun} ${family.charged} emptied is the uncharged item")
            assertEquals(0, DragonfireShield.charges(emptied))
            assertTrue(DragonfireShield.inspectMessage(charged).contains("7 charges"))
        }
        assertNotNull(DragonfireShield.familyOf(Items.DRAGONFIRE_WARD), "owner report: the ward was not handled at all")
        assertNotNull(DragonfireShield.familyOf(Items.DRAGONFIRE_WARD_UNCHARGED))
        assertNull(DragonfireShield.familyOf(Items.RUNE_KITESHIELD))
        // OSRS Wiki "Ancient wyvern shield": charged with fossils or numulites, never by breath.
        assertFalse(DragonfireShield.familyOf(Items.ANCIENT_WYVERN_SHIELD)!!.breathCharged)
        assertTrue(DragonfireShield.familyOf(Items.DRAGONFIRE_WARD)!!.breathCharged)
    }

    @Test
    fun `the assembler attraction table is the wiki table out of 2000`() {
        assertEquals(2000, AvasAssembler.TOTAL_WEIGHT)
        assertEquals(Items.MITHRIL_ARROW, AvasAssembler.attracted(0))
        assertEquals(Items.MITHRIL_ARROW, AvasAssembler.attracted(1974))
        assertEquals(Items.MITHRIL_DART, AvasAssembler.attracted(1975))
        assertEquals(Items.MITHRIL_BAR, AvasAssembler.attracted(1999))
        val counts = (0 until AvasAssembler.TOTAL_WEIGHT).groupingBy { AvasAssembler.attracted(it) }.eachCount()
        AvasAssembler.ATTRACTION.forEach { (item, weight) -> assertEquals(weight, counts[item], "weight of item $item") }
    }

    @Test
    fun `every weapon look plays an imported OSRS sequence on every style button`() {
        val imported = 15384..15465
        listOf(
            Items.ABYSSAL_DAGGER, Items.ABYSSAL_DAGGER_P, Items.ABYSSAL_DAGGER_P_PLUS, Items.ABYSSAL_DAGGER_P_PLUS_PLUS,
            Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_OR, Items.TOXIC_BLOWPIPE, Items.BLAZING_BLOWPIPE, Items.CAMPHOR_BLOWPIPE,
            Items.IRONWOOD_BLOWPIPE, Items.ROSEWOOD_BLOWPIPE, Items.DRAGON_HUNTER_LANCE, Items.DUAL_MACUAHUITL, Items.DRAGON_KNIFE,
            Items.DRAGON_KNIFE_P, Items.DRAGON_KNIFE_P_PLUS, Items.DRAGON_KNIFE_P_PLUS_PLUS, Items.ZARYTE_CROSSBOW, Items.VENATOR_BOW,
            Items.ECLIPSE_ATLATL, Items.TONALZTICS_OF_RALOS, Items.TONALZTICS_OF_RALOS_UNCHARGED, Items.NOXIOUS_HALBERD,
        ).forEach { weapon ->
            for (style in 0..3) {
                for (npc in listOf(true, false)) {
                    val seq = OsrsWeaponLooks.attackAnimation(weapon, style, againstNpc = npc, stabStyle = false)
                    assertNotNull(seq, "weapon $weapon style $style has no OSRS look")
                    assertTrue(seq in imported, "weapon $weapon style $style plays $seq, not an imported sequence")
                }
            }
        }
        // Heavy ballista: OSRS plays a different sequence against npcs (BALLISTA_ATTACK_PVN) than against players.
        assertEquals(OsrsSeq.BALLISTA_ATTACK_PVN, OsrsWeaponLooks.attackAnimation(Items.HEAVY_BALLISTA, 0, againstNpc = true, stabStyle = false))
        assertEquals(OsrsSeq.BALLISTA_ATTACK, OsrsWeaponLooks.attackAnimation(Items.HEAVY_BALLISTA, 0, againstNpc = false, stabStyle = false))
        // Osmumten's fang: only the stab has its own sequence; a native weapon has no OSRS look.
        assertEquals(OsrsSeq.HUMAN_OSMUMTENS_FANG, OsrsWeaponLooks.attackAnimation(Items.OSMUMTENS_FANG, 0, againstNpc = true, stabStyle = true))
        assertNull(OsrsWeaponLooks.attackAnimation(Items.OSMUMTENS_FANG, 2, againstNpc = true, stabStyle = false))
        assertNull(OsrsWeaponLooks.attackAnimation(Items.ABYSSAL_WHIP, 0, againstNpc = true, stabStyle = false))
        assertEquals(OsrsSeq.ABYSSAL_DAGGER_BLOCK, OsrsWeaponLooks.blockAnimation(Items.ABYSSAL_DAGGER_P))
    }
}
