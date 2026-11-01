package gg.rsmod.plugins.content.cmd.conformance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EquipmentReferencesTests {
    private val rows = ObjectMapper().readTree(File(RspsPaths.root("reference/weapons/osrs_weapons.json")))["weapons"]
    private val byName = HashMap<String, JsonNode>().apply {
        rows.forEach { w -> putIfAbsent(w["name"].asText().lowercase(), w); putIfAbsent(w["page"].asText().lowercase(), w) }
    }

    @Test
    fun `tops and bottoms select their own wiki identity despite identical 667 names`() {
        for ((id, name) in mapOf(538 to "Druid's robe", 540 to "Druid's robe", 542 to "Monk's robe", 544 to "Monk's robe", 546 to "Shade robe", 548 to "Shade robe")) {
            val ref = assertNotNull(EquipmentReferences.resolve(id, name, byName))
            assertEquals(id, ref["osrs_ids"].first().asInt(), "item $id")
            assertEquals(if (id in listOf(540, 544, 546)) "body" else "legs", ref["bonuses"]["equipment_slot"].asText())
        }
        // Missing explicit identity must never silently fall back to a different item with the same name.
        assertNull(EquipmentReferences.resolve(544, "Monk's robe", byName - "monk's robe top"))
    }

    @Test
    fun `quest blue hat keeps plus two while the regular hat keeps plus three`() {
        for ((id, name, bonus) in listOf(Triple(740, "Blue hat", 2), Triple(660, "Blue hat", 3), Triple(579, "Blue wizard hat", 2))) {
            val ref = assertNotNull(EquipmentReferences.resolve(id, name, byName))
            assertEquals(id, ref["osrs_ids"].first().asInt())
            assertEquals(bonus, ref["bonuses"]["magic_attack_bonus"].asInt())
            assertEquals(bonus, ref["bonuses"]["magic_defence_bonus"].asInt())
        }
    }

    @Test
    fun `all retired tools and model-less Picture placeholders are outside the equipment roster`() {
        for (id in (466..478 step 2) + (492..506 step 2)) assertNotNull(EquipmentReferences.exclusion(ItemDef(id)))
        for (id in listOf(547, 549, 599, 713, 762, 764, 766, 770)) {
            assertNotNull(EquipmentReferences.exclusion(ItemDef(id).apply { name = "Picture"; inventoryMenu[0] = "Wear" }))
        }
        assertNull(EquipmentReferences.exclusion(ItemDef(Items.BRONZE_PICKAXE)))
        assertNull(EquipmentReferences.exclusion(ItemDef(Items.BRONZE_HATCHET)))
        assertNull(EquipmentReferences.exclusion(ItemDef(1510).apply { name = "Picture"; maleWornModel = 100 }))
    }

    @Test
    fun `owner kept thrown javelins are not compared to ballista ammo and do not mutate the wiki`() {
        val names = listOf("Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune")
        for (i in names.indices) {
            val name = "${names[i]} javelin"
            val before = byName.getValue(name.lowercase()).toString()
            val ref = assertNotNull(EquipmentReferences.resolve(825 + i, name, byName))
            assertEquals("owner (g): 667 thrown javelins", ref["source"].asText())
            assertEquals("weapon", ref["bonuses"]["equipment_slot"].asText())
            assertEquals(listOf(5, 8, 12, 17, 24, 38)[i], ref["bonuses"]["range_attack_bonus"].asInt())
            assertEquals(listOf(6, 10, 12, 18, 28, 42)[i], ref["bonuses"]["ranged_strength_bonus"].asInt())
            assertEquals(before, byName.getValue(name.lowercase()).toString())
        }
    }

    @Test
    fun `every registered bow and crossbow gets ammunition it accepts including low tiers`() {
        for (bow in BowType.values()) {
            val ammo = EquipmentReferences.registeredAmmo(bow.item)
            if (bow.ammo.isEmpty()) assertNull(ammo, bow.name) else assertEquals(true, ammo in bow.ammo, bow.name)
        }
        for (bow in CrossbowType.values()) assertEquals(true, EquipmentReferences.registeredAmmo(bow.item) in bow.ammo, "${bow.name}")
        assertEquals(Items.BRONZE_BOLTS, EquipmentReferences.registeredAmmo(Items.PHOENIX_CROSSBOW))
        assertEquals(Items.BRONZE_BOLTS, EquipmentReferences.registeredAmmo(Items.CROSSBOW))
        for (id in listOf(Items.SHORTBOW, Items.LONGBOW, Items.OAK_SHORTBOW, Items.MAPLE_LONGBOW)) {
            assertEquals(Items.BRONZE_ARROW, EquipmentReferences.registeredAmmo(id))
        }
    }
}
