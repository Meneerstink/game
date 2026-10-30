package gg.rsmod.plugins.content.items.combine

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * N444 (owner 2026-10-30: basilisk jaw on a helm of neitiznot "geeft ook geen melding van are you sure"). Rules in [AttachConfirm].
 */
class AttachConfirmTests {
    private fun def(vararg ops: String?): ItemDef = ItemDef(1).also { d -> ops.forEachIndexed { i, op -> d.inventoryMenu[i] = op } }

    @Test
    fun `a result that can be taken apart again asks, a charge-only or permanent one does not`() {
        assertTrue(AttachConfirm.undoable(def("Wear", null, "Dismantle")))
        assertTrue(AttachConfirm.undoable(def("Wear", "Check", "Disassemble")))
        assertTrue(AttachConfirm.undoable(def("Wield", "Revert")))
        assertFalse("Uncharge is a charge, not taking apart", AttachConfirm.undoable(def("Wield", "Check", "Uncharge")))
        assertFalse(AttachConfirm.undoable(def("Wield")))
        assertFalse(AttachConfirm.undoable(null))
    }

    @Test
    fun `a route is found either way round and words the question like the ornament kits`() {
        AttachConfirm.route(Items.BASILISK_JAW, Items.HELM_OF_NEITIZNOT, Items.NEITIZNOT_FACEGUARD)
        assertEquals(Items.NEITIZNOT_FACEGUARD, AttachConfirm.routeOf(Items.HELM_OF_NEITIZNOT, Items.BASILISK_JAW)?.result)
        assertEquals(Items.NEITIZNOT_FACEGUARD, AttachConfirm.routeOf(Items.BASILISK_JAW, Items.HELM_OF_NEITIZNOT)?.result)
        assertNull(AttachConfirm.routeOf(Items.BASILISK_JAW, Items.ABYSSAL_WHIP))
        assertEquals("Are you sure you want to attach the basilisk jaw to this item?", AttachConfirm.question("Basilisk jaw"))
        assertEquals("The basilisk jaw will be attached to this item.", AttachConfirm.detail("Basilisk jaw"))
    }

    @Test
    fun `every attach route whose result can be taken apart registers, the self-asking ones keep their own question`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins/content")
        val registered = mapOf(
            "items/osrs/serpentine_helm.plugin.kts" to "Items.NEITIZNOT_FACEGUARD",
            "items/osrs/osrs_bows.plugin.kts" to "Items.WEBWEAVER_BOW_U",
            "items/osrs/osrs_magegear.plugin.kts" to "Items.ELIDINIS_WARD_F",
            "items/osrs/osrs_max_capes.plugin.kts" to "Items.MASORI_ASSEMBLER",
            "items/osrs/osrs_sceptres.plugin.kts" to "AttachConfirm.route(quartz",
            "items/osrs/revenant_weapons.plugin.kts" to "upgrade.result",
            "items/osrs/staff_of_the_dead.plugin.kts" to "Items.TOXIC_STAFF_UNCHARGED",
            "items/osrs/blood_fury.plugin.kts" to "Items.AMULET_OF_BLOOD_FURY",
            "items/slayer_helmet.plugin.kts" to "Items.FULL_SLAYER_HELMET",
        )
        registered.forEach { (file, marker) ->
            val text = File(root, file).readText()
            assertTrue("$file registers AttachConfirm.route", "AttachConfirm.route(" in text && marker in text)
        }
        // Their own question already (no second one): the abyssal tentacle's warning, the ornate maul handle, max cape combining.
        assertFalse("AttachConfirm.route(" in File(root, "items/osrs/abyssal_tentacle.plugin.kts").readText())
        assertFalse("AttachConfirm.route(" in File(root, "items/osrs/granite_maul.plugin.kts").readText())
        val install = File(root, "items/item_action_guard.plugin.kts").readText()
        assertTrue("AttachConfirm.install(world.plugins)" in install)
    }
}
