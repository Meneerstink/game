package gg.rsmod.plugins.content.mechanics.removed

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-10-06: "remove pernix and virtus completely from our rsps the rest needs to be degraded".
 * Nothing may offer Pernix or Virtus any more (Nex, the Grand Exchange and its house); Torva and the rest keep dropping and degrading.
 */
class PernixVirtusRemovedTests {
    private val removed =
        setOf(
            Items.PERNIX_COWL, Items.PERNIX_COWL_20149, Items.PERNIX_COWL_BROKEN,
            Items.PERNIX_BODY, Items.PERNIX_BODY_20153, Items.PERNIX_BODY_BROKEN,
            Items.PERNIX_CHAPS, Items.PERNIX_CHAPS_20157, Items.PERNIX_CHAPS_BROKEN,
            Items.VIRTUS_MASK, Items.VIRTUS_MASK_20161, Items.VIRTUS_MASK_BROKEN,
            Items.VIRTUS_ROBE_TOP, Items.VIRTUS_ROBE_TOP_20165, Items.VIRTUS_ROBE_TOP_BROKEN,
            Items.VIRTUS_ROBE_LEGS, Items.VIRTUS_ROBE_LEGS_20169, Items.VIRTUS_ROBE_LEGS_BROKEN,
        )

    /** N464 (owner 2026-10-30): "zaryte bow moet volledig verwijderd worden", held copies "Weg bij inloggen". */
    private val zaryteBow = setOf(Items.ZARYTE_BOW, Items.ZARYTE_BOW_20173, Items.ZARYTE_BOW_BROKEN)

    private fun tradeable(id: Int) = ItemDef(id).apply { tradeable = true }

    private fun notedOf(id: Int) =
        ItemDef(id + 1).apply {
            tradeable = true
            noteTemplateId = 799
            noteLinkId = id
        }

    @Test
    fun `every Pernix and Virtus id is unobtainable and taken by the login purge`() {
        // Owner A13 (night run 2026-10-12): the non-corrupt Deadman (deg) / Torva used-broken ids are unobtainable too - converted to
        // the normal item at login (DeadmanGear.migrate) before this purge runs; every other unobtainable id is Pernix / Virtus.
        // N457: corrupt copies are deleted without replacement (CorruptRemovedItemsTests).
        // N424: the two looting bag ids are unobtainable too (LootingBagRemovedTests).
        // T8 (2026-11-06): owner 2026-11-04 15:1x took the Forinthry bracelet (5 charge ids) out of the game the same way.
        val forinthry = setOf(Items.FORINTHRY_BRACE_1, Items.FORINTHRY_BRACE_2, Items.FORINTHRY_BRACE_3, Items.FORINTHRY_BRACE_4, Items.FORINTHRY_BRACE_5)
        assertEquals(removed + zaryteBow, RemovedItems.UNOBTAINABLE_IDS - gg.rsmod.plugins.content.items.armor.DeadmanGear.CONVERT.keys - gg.rsmod.plugins.content.mechanics.pvp.LootingBag.IDS - RemovedItems.CORRUPT_IDS - forinthry)
        // Owner 2026-10-06 "Delete on login": the login purge takes them through isUnobtainable; IDS stays the removed runes.
        assertTrue(RemovedItems.UNOBTAINABLE_IDS.none { it in RemovedItems.IDS })
        removed.forEach { id ->
            assertTrue(RemovedItems.isUnobtainable(tradeable(id)), "$id")
            assertTrue(RemovedItems.isUnobtainable(notedOf(id)), "noted $id")
            assertFalse(RemovedItems.isRemoved(tradeable(id)), "$id must not be purged at login")
        }
    }

    @Test
    fun `the Grand Exchange and its house refuse Pernix and Virtus, Torva stays exchangeable`() {
        removed.forEach { id -> assertFalse(GrandExchangeInterface.exchangeable(tradeable(id)), "$id still exchangeable") }
        // N464 (owner 2026-10-30): the Zaryte bow is gone from the Grand Exchange too; the Zaryte crossbow stays.
        zaryteBow.forEach { id ->
            assertFalse(GrandExchangeInterface.exchangeable(tradeable(id)), "$id still exchangeable")
            assertTrue(RemovedItems.isUnobtainable(notedOf(id)), "noted $id")
        }
        listOf(Items.TORVA_FULL_HELM, Items.TORVA_PLATEBODY, Items.TORVA_PLATELEGS, Items.ZARYTE_CROSSBOW).forEach { id ->
            assertTrue(GrandExchangeInterface.exchangeable(tradeable(id)), "$id must stay exchangeable")
        }
        val commands = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/grand_exchange.plugin.kts").readText()
        assertEquals(2, Regex("RemovedItems\\.isExchangeBlocked\\(geDef\\)").findAll(commands).count(), "::ge_buy and ::ge_sell refuse them too")
    }

    @Test
    fun `Nex drops no Pernix or Virtus and every other row keeps its donor rate`() {
        val nex = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/godwars/nex.plugin.kts").readText()
        assertFalse(Regex("Items\\.(PERNIX|VIRTUS)_").containsMatchIn(nex), "Nex must not drop Pernix or Virtus")
        // Darkan data/npcs/drops/nex.json: each unique its own 1/384 roll = 10 of the declared 3840 slots.
        assertTrue("total(3840)" in nex, "the roll denominator stays 3840, so no other rate moves")
        listOf("TORVA_FULL_HELM", "TORVA_PLATEBODY", "TORVA_PLATELEGS").forEach {
            assertTrue("obj(Items.$it, slots = 10)" in nex, "$it keeps 1/384")
        }
        // N272 (owner 2026-10-24): Nex drops the OSRS Zaryte crossbow at 15/3840 = 1/256 instead of the 2011 Zaryte bow.
        assertTrue("obj(Items.ZARYTE_CROSSBOW, slots = 15)" in nex, "the Zaryte crossbow drops at 1/256")
        assertFalse("Items.ZARYTE_BOW" in nex, "the Zaryte bow is no longer a Nex drop")
    }

    @Test
    fun `no shop, store or drop data in the server offers a Pernix or Virtus id`() {
        val ids = removed.map { it.toString() }.toSet()
        val idPattern = Regex("\\b(${ids.joinToString("|")})\\b")
        val offenders = mutableListOf<String>()
        listOf(File("../../data/cfg/shops"), File("../../data/cfg/npcs")).filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                if (idPattern.containsMatchIn(file.readText())) offenders += file.path
            }
        }
        File("src/main/kotlin/gg/rsmod/plugins/content").walkTopDown().filter { it.isFile }.forEach { file ->
            val text = file.readText()
            if (Regex("(table|shop|store|reward|stock)", RegexOption.IGNORE_CASE).containsMatchIn(file.name) &&
                Regex("Items\\.(PERNIX|VIRTUS)_").containsMatchIn(text)
            ) {
                offenders += file.path
            }
        }
        assertTrue(offenders.isEmpty(), offenders.joinToString())
    }
}
