package gg.rsmod.plugins.content.cmd.conformance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType

/** N521: resolve identities before comparing stats; a shared display name is not an item identity. */
internal object EquipmentReferences {
    private val pages = mapOf(
        540 to "Druid's robe top", 544 to "Monk's robe top", 546 to "Shade robe top",
        740 to "Blue hat (Legends' Quest)",
        11283 to "Dragonfire shield#Charged", 11284 to "Dragonfire shield#Uncharged",
    )

    /** Build-240 definitions retain wearPos=3 but have no Wield option (same rule as osrsWearable). */
    private val retiredTools = ((466..478 step 2) + (492..506 step 2)).toSet()

    fun exclusion(def: ItemDef): String? = when {
        def.id in retiredTools -> "retired broken tool/handle: pinned OSRS build 240 has no Wield option"
        def.name == "Picture" && def.maleWornModel < 0 && def.maleWornModel2 < 0 ->
            "667 Picture placeholder: Wear menu but no worn models; no OSRS equipment identity"
        else -> null
    }

    fun resolve(id: Int, name: String, byName: Map<String, JsonNode>): JsonNode? {
        val page = pages[id]
        val ref = byName[(page ?: name).lowercase()] ?: return null
        if (id !in 825..830) return ref
        // Owner (g), 2026-09-14: these are 667 THROWN weapons, not OSRS ballista ammo.
        // Frozen pre-N521 667 values, also excluded by OsrsEquipmentStatsAudit.ALL_FIELDS_EXCLUDED.
        val copy = ref.deepCopy<ObjectNode>()
        copy.put("source", "owner (g): 667 thrown javelins")
        val bonuses = copy["bonuses"] as ObjectNode
        bonuses.put("equipment_slot", "weapon")
        bonuses.put("range_attack_bonus", listOf(5, 8, 12, 17, 24, 38)[id - 825])
        bonuses.put("ranged_strength_bonus", listOf(6, 10, 12, 18, 28, 42)[id - 825])
        return copy
    }

    /** A legal loadout for every registered bow/crossbow, including the lowest tiers. */
    fun registeredAmmo(id: Int): Int? =
        CrossbowType.values().firstOrNull { it.item == id }?.ammo?.firstOrNull()
            ?: BowType.values().firstOrNull { it.item == id }?.ammo?.firstOrNull()
}
