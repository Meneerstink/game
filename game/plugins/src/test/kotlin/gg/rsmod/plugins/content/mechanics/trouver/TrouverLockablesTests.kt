package gg.rsmod.plugins.content.mechanics.trouver

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.RepairPrices
import gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** K01 2026-10-25 (N408): the lock list - defenders from mithril up, Void, Fighter torso, Infernal max cape - and Perdu's refusals. */
class TrouverLockablesTests {
    @Before
    fun register() {
        TrouverRegistry.clear()
        TrouverLockables.registerAll()
    }

    @After
    fun clearRegistry() {
        TrouverRegistry.clear()
    }

    @Test
    fun `defenders from mithril up, void, the fighter torso and the infernal max cape lock`() {
        val expected =
            mapOf(
                Items.MITHRIL_DEFENDER to Items.MITHRIL_DEFENDER_L, Items.ADAMANT_DEFENDER to Items.ADAMANT_DEFENDER_L,
                Items.RUNE_DEFENDER to Items.RUNE_DEFENDER_L, Items.DRAGON_DEFENDER to Items.DRAGON_DEFENDER_L,
                Items.AVERNIC_DEFENDER to Items.AVERNIC_DEFENDER_L, Items.RUNE_DEFENDER_T to Items.RUNE_DEFENDER_LT,
                Items.DRAGON_DEFENDER_T to Items.DRAGON_DEFENDER_LT, Items.FIGHTER_TORSO to Items.FIGHTER_TORSO_L,
                Items.VOID_KNIGHT_TOP to Items.VOID_KNIGHT_TOP_L, Items.ELITE_VOID_KNIGHT_TOP to Items.ELITE_VOID_TOP_L,
                Items.VOID_KNIGHT_ROBE to Items.VOID_KNIGHT_ROBE_L, Items.ELITE_VOID_KNIGHT_ROBE to Items.ELITE_VOID_ROBE_L,
                Items.VOID_KNIGHT_GLOVES to Items.VOID_KNIGHT_GLOVES_L, Items.VOID_MAGE_HELM to Items.VOID_MAGE_HELM_L,
                Items.VOID_RANGER_HELM to Items.VOID_RANGER_HELM_L, Items.VOID_MELEE_HELM to Items.VOID_MELEE_HELM_L,
                Items.INFERNAL_MAX_CAPE to Items.INFERNAL_MAX_CAPE_L,
                // the 667 duplicates lock as the main item
                Items.VOID_KNIGHT_TOP_10611 to Items.VOID_KNIGHT_TOP_L, Items.VOID_MELEE_HELM_11676 to Items.VOID_MELEE_HELM_L,
                Items.ELITE_VOID_KNIGHT_ROBE_19790 to Items.ELITE_VOID_ROBE_L,
            )
        expected.forEach { (base, locked) -> assertEquals(locked, TrouverRegistry.lockableFor(base)?.lockedItemId, "base $base") }
        assertEquals(Items.VOID_KNIGHT_TOP, TrouverRegistry.lockableFor(Items.VOID_KNIGHT_TOP_10611)?.baseItemId, "an alias unlocks into the main item")
    }

    @Test
    fun `every locked item but the rune pouches has its own broken and mangled form that repairs back to the locked item`() {
        // N416: the older locked items too (Fire cape (l), assemblers, imbued capes, quivers) - no hidden broken mark any more.
        TrouverLockables.ALL.filter { it.baseItemId != Items.RUNE_POUCH && it.baseItemId != Items.DIVINE_RUNE_POUCH }.forEach { pair ->
            val broken = assertNotNull(pair.brokenItemId, "${pair.lockedItemId} broken")
            val mangled = assertNotNull(pair.mangledItemId, "${pair.lockedItemId} mangled")
            assertEquals(pair.lockedItemId, UntradeableDeathProtection.repaired(Item(broken)).id)
            assertEquals(pair.lockedItemId, UntradeableDeathProtection.repaired(Item(mangled)).id)
            assertEquals(pair.baseItemId, RepairPrices.baseItem(broken))
            assertTrue(UntradeableDeathProtection.lockable(pair.lockedItemId))
        }
    }

    @Test
    fun `the void knight deflector is untradeable, locks, breaks and repairs like the rest of void`() {
        // N472 (owner 2026-10-31: "net als Void").
        val tradeable = mutableMapOf<Int, Boolean>()
        var id = -1
        java.io.File(java.nio.file.Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                line.startsWith("  tradeable: ") -> tradeable[id] = line.removePrefix("  tradeable: ").trim() == "true"
            }
        }
        val forms =
            listOf(
                Items.VOID_KNIGHT_DEFLECTOR, Items.VOID_KNIGHT_DEFLECTOR_19712, Items.VOID_KNIGHT_DEFLECTOR_BROKEN,
                Items.VOID_KNIGHT_DEFLECTOR_L, Items.VOID_KNIGHT_DEFLECTOR_L_BROKEN, Items.VOID_KNIGHT_DEFLECTOR_L_MANGLED,
            )
        forms.forEach { assertEquals(false, tradeable[it], "items.yml tradeable of $it") }
        // the same as Void knight gloves: a lock pair with its own broken and mangled forms, and a broken form when unlocked
        assertEquals(Items.VOID_KNIGHT_DEFLECTOR_L, TrouverRegistry.lockableFor(Items.VOID_KNIGHT_DEFLECTOR_19712)?.lockedItemId)
        assertEquals(Items.VOID_KNIGHT_DEFLECTOR_L, TrouverRegistry.lockableFor(Items.VOID_KNIGHT_DEFLECTOR)?.lockedItemId, "old 19711 locks as 19712")
        assertEquals(Items.VOID_KNIGHT_DEFLECTOR_19712, TrouverRegistry.lockableFor(Items.VOID_KNIGHT_DEFLECTOR)?.baseItemId)
        val glovesBreak = gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables.breakableFor(Items.VOID_KNIGHT_GLOVES)!!
        listOf(Items.VOID_KNIGHT_DEFLECTOR_19712, Items.VOID_KNIGHT_DEFLECTOR).forEach {
            val b = assertNotNull(gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables.breakableFor(it), "breakable $it")
            assertEquals(Items.VOID_KNIGHT_DEFLECTOR_BROKEN, b.brokenId)
            assertEquals(glovesBreak.breaksAboveTwenty, b.breaksAboveTwenty)
            assertTrue(UntradeableDeathProtection.lockable(it))
            assertTrue(it in UntradeableDeathProtection.OSRS_LOCKABLE)
        }
        assertEquals(Items.VOID_KNIGHT_DEFLECTOR_19712, UntradeableDeathProtection.repaired(Item(Items.VOID_KNIGHT_DEFLECTOR_BROKEN)).id, "repairs into the wearable one")
        assertEquals(Items.VOID_KNIGHT_DEFLECTOR_L, UntradeableDeathProtection.repaired(Item(Items.VOID_KNIGHT_DEFLECTOR_L_MANGLED)).id)
        assertTrue(TrouverLockables.refusal(Items.VOID_KNIGHT_DEFLECTOR_L, "Void knight deflector (l)", tradeable = false)!!.contains("already locked"))
    }

    @Test
    fun `Perdu says why an item cannot be locked`() {
        assertNull(TrouverLockables.refusal(Items.MITHRIL_DEFENDER, "Mithril defender", tradeable = false))
        assertEquals(
            "I only lock defenders from mithril up. An iron defender isn't worth a Trouver parchment.",
            TrouverLockables.refusal(Items.IRON_DEFENDER, "Iron defender", tradeable = false),
        )
        assertTrue(TrouverLockables.refusal(Items.BRONZE_DEFENDER, "Bronze defender", tradeable = false)!!.contains("A bronze defender"))
        assertTrue(TrouverLockables.refusal(Items.VOID_KNIGHT_TOP_L, "Void knight top (l)", tradeable = false)!!.contains("already locked"))
        assertTrue(TrouverLockables.refusal(Items.VOID_KNIGHT_TOP_L_BROKEN, "Void knight top (l) (broken)", tradeable = false)!!.contains("repair"))
        assertTrue(TrouverLockables.refusal(Items.ABYSSAL_WHIP, "Abyssal whip", tradeable = true)!!.contains("Only untradeable items"))
        assertTrue(TrouverLockables.refusal(Items.GLOVES_7462, "Barrows gloves", tradeable = false)!!.endsWith(TrouverLockables.LOCKABLE_SUMMARY))
    }
}
