package gg.rsmod.plugins.content.items

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_ID
import gg.rsmod.game.model.attr.INTERACTING_SLOT_ATTR
import gg.rsmod.game.model.attr.INTERACTING_OPCODE_ATTR
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.testutil.BacklogBundleWorld
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class VoidRequirementsTests {
    private val world get() = BacklogBundleWorld.world
    private val items: List<ItemDef> get() = world.definitions.getAll(ItemDef::class.java).values.filterIsInstance<ItemDef>()
    private fun reqs(d: ItemDef): Map<Int, Int> = d.skillReqs?.entries?.associate { it.key.toInt() to it.value.toInt() } ?: emptyMap()
    private fun void(d: ItemDef) = Regex("(?i)^(elite )?void ((knight )?(top|robe|mace|gloves|deflector)|melee helm|ranger helm|mage helm)").containsMatchIn(d.name)

    @Test
    fun `all wearable Void parts including locked forms require seven wiki skills`() {
        val all = items.filter { it.equipSlot >= 0 && void(it) }
        assertTrue(all.size >= 27, "whole Void family: ${all.map { it.id }}")
        all.forEach { d ->
            assertEquals((0..6).associateWith { if (it == 5) 22 else 42 }, reqs(d), "${d.id} ${d.name}")
            for (skill in 0..6) {
                val p = BacklogBundleWorld.player()
                p.skills.setBaseLevel(skill, if (skill == 5) 21 else 41)
                assertEquals(EquipAction.Result.FAILED_REQUIREMENTS, EquipAction.equip(p, Item(d.id)), "${d.id} below required skill $skill")
            }
        }
    }

    @Test
    fun `deflector and locked deflector refuse Prayer 21 and accept 22 through equip and stats inventory`() {
        BacklogBundleWorld.bind("gg.rsmod.plugins.content.inter.equipmentstats.Equipment_stats_interface_plugin")
        listOf(19712, 24620).forEach { id ->
            for (prayer in listOf(21, 22)) for (stats in listOf(false, true)) {
                val p = BacklogBundleWorld.player()
                p.skills.setBaseLevel(5, prayer)
                val item = Item(id)
                p.inventory[0] = item
                if (stats) {
                    p.attr[INTERACTING_ITEM_ID] = id
                    p.attr[INTERACTING_SLOT_ATTR] = 0
                    p.attr[INTERACTING_OPCODE_ATTR] = 61
                    assertTrue(world.plugins.executeButton(p, 670, 0))
                } else {
                    assertEquals(if (prayer == 21) EquipAction.Result.FAILED_REQUIREMENTS else EquipAction.Result.SUCCESS, EquipAction.equip(p, item, 0))
                }
                if (prayer == 21) {
                    assertNull(p.equipment[5], "$id Prayer 21 stats=$stats")
                    assertEquals(id, p.inventory[0]?.id)
                } else assertEquals(id, p.equipment[5]?.id, "$id Prayer 22 stats=$stats")
            }
        }
    }

    @Test
    fun `report every wearable cache versus server difference including sixth pair and imported items`() {
        var checked = 0
        val differences = items.filter { d ->
            !d.noted && (d.equipSlot >= 0 || d.inventoryMenu.any { it?.lowercase() in setOf("wear", "wield", "equip") })
        }.sortedBy { it.id }.mapNotNull { d ->
            checked++
            val cache = linkedMapOf<Int, Int>()
            val malformed = mutableListOf<String>()
            for (k in 749..759 step 2) {
                val skill = d.params[k]
                val level = d.params[k + 1]
                if (skill == null && level == null) continue
                if (skill !is Int || level !is Int || skill !in 0..24 || level !in 1..120) malformed += "$k=$skill/${k + 1}=$level"
                else cache[skill] = level
            }
            if (cache == reqs(d) && malformed.isEmpty()) null
            else "${d.id} ${d.name}: cache=$cache yml=${reqs(d)}" + if (malformed.isEmpty()) "" else " malformed=$malformed"
        }
        val report = File("build/reports/N479-item-requirements.txt")
        report.parentFile.mkdirs()
        report.writeText("Wearable items checked=$checked; differences=${differences.size}; params 749-760; no id/import exclusions.\n" + differences.joinToString("\n"))
        println(report.readText())
        assertTrue(checked > 3000)
        val deflector = world.definitions.get(ItemDef::class.java, 19712)
        assertEquals(5, deflector.params[759])
        assertEquals(22, deflector.params[760])
    }
}
