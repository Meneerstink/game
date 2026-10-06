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

    private fun tradeable(id: Int) = ItemDef(id).apply { tradeable = true }

    private fun notedOf(id: Int) =
        ItemDef(id + 1).apply {
            tradeable = true
            noteTemplateId = 799
            noteLinkId = id
        }

    @Test
    fun `every Pernix and Virtus id is unobtainable but not purged from saves`() {
        assertEquals(removed, RemovedItems.UNOBTAINABLE_IDS)
        // Copies already in saves are the owner's call: the login purge only takes RemovedItems.IDS.
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
        listOf(Items.TORVA_FULL_HELM, Items.TORVA_PLATEBODY, Items.TORVA_PLATELEGS, Items.ZARYTE_BOW).forEach { id ->
            assertTrue(GrandExchangeInterface.exchangeable(tradeable(id)), "$id must stay exchangeable")
        }
        val commands = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/grand_exchange.plugin.kts").readText()
        assertEquals(2, Regex("RemovedItems\\.isUnobtainable\\(geDef\\)").findAll(commands).count(), "::ge_buy and ::ge_sell refuse them too")
    }

    @Test
    fun `Nex drops no Pernix or Virtus and every other row keeps its donor rate`() {
        val nex = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/godwars/nex.plugin.kts").readText()
        assertFalse(Regex("Items\\.(PERNIX|VIRTUS)_").containsMatchIn(nex), "Nex must not drop Pernix or Virtus")
        // Darkan data/npcs/drops/nex.json: each unique its own 1/384 roll = 10 of the declared 3840 slots.
        assertTrue("total(3840)" in nex, "the roll denominator stays 3840, so no other rate moves")
        listOf("TORVA_FULL_HELM", "TORVA_PLATEBODY", "TORVA_PLATELEGS", "ZARYTE_BOW").forEach {
            assertTrue("obj(Items.$it, slots = 10)" in nex, "$it keeps 1/384")
        }
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
