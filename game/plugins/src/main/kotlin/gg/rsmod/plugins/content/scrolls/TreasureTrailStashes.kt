package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.api.ext.addXp
import java.io.File

/** Wiki STASH revision/hash and all object placements are pinned in stashes.json. */
class TreasureTrailStashCatalogue private constructor(val units: List<Unit>) {
    data class Material(val amount: Int, val alternatives: List<Int>)
    data class Recipe(val level: Int, val experience: Int, val ticks: Int,
        val tools: List<List<TreasureTrailStepCatalogue.Equipment>>, val crystalSaw: TreasureTrailStepCatalogue.Equipment, val materials: List<Material>)
    data class Unit(val sourceId: Int, val step: TreasureTrailStepCatalogue.Step, val recipe: Recipe) {
        // Source scenery identity survives local cache reallocation. Never use a list index as a save key.
        val saved = AttributeKey<String>(persistenceKey = "clue_stash_$sourceId")
        val equipment get() = step.equipment
    }

    companion object {
        val current by lazy { parse(File(RspsPaths.data("cfg/clue/stashes.json")).readText(), TreasureTrailStepCatalogue.current) }
        fun parse(json: String, steps: TreasureTrailStepCatalogue): TreasureTrailStashCatalogue {
            val root = JsonParser().parse(json).asJsonObject
            fun equipment(value: com.google.gson.JsonElement) = value.asJsonObject.let {
                TreasureTrailStepCatalogue.Equipment(it["osrsId"].asInt, it["existingLocal"].takeUnless { id -> id.isJsonNull }?.asInt)
            }
            val recipes = root["recipes"].asJsonObject.entrySet().associate { (tier, value) ->
                val row = value.asJsonObject
                tier to Recipe(row["level"].asInt, row["experience"].asInt, row["ticks"].asInt,
                    row["tools"].asJsonArray.map { group -> group.asJsonArray.map(::equipment) }, equipment(row["crystalSaw"]),
                    row["materials"].asJsonArray.map { material -> material.asJsonObject.let {
                        Material(it["amount"].asInt, it["alternatives"].asJsonArray.map { alt -> alt.asJsonObject["existingLocal"].asInt })
                    } })
            }
            val units = root["units"].asJsonArray.map { value ->
                val row = value.asJsonObject
                val step = steps.rows.single { it.key == row["key"].asString }
                require(step.action == TreasureTrailStepCatalogue.Action.EMOTE && step.equipment.isNotEmpty())
                require(step.tier.name.equals(row["tier"].asString, true))
                Unit(row["osrsId"].asInt, step, recipes.getValue(row["tier"].asString))
            }
            require(units.map { it.sourceId }.distinct().size == units.size)
            require(units.all { unit -> unit.recipe.let { it.level > 0 && it.experience > 0 && it.ticks > 0 &&
                it.materials.all { material -> material.amount > 0 && material.alternatives.isNotEmpty() } } })
            return TreasureTrailStashCatalogue(units)
        }
    }
}

/**
 * Full sets only; inventory first; equipped items only with the explicit setting.
 * Both containers and the saved contents change without suspending or making an
 * intermediate player save. Items retain charge/ornament attributes across relog.
 * Scene placement/selector import is a separate prerequisite, not inferred here.
 */
object TreasureTrailStashes {
    val TAKE_EQUIPPED = AttributeKey<Boolean>(persistenceKey = "clue_stash_take_equipped")
    private val gson = Gson()
    enum class Result { BUILT, STORED, WITHDRAWN, ALREADY_BUILT, NOT_BUILT, LEVEL, TOOLS, MATERIALS, INCOMPLETE_SET, NO_SPACE, UNEQUIP_BLOCKED, CORRUPT }

    fun built(player: Player, unit: TreasureTrailStashCatalogue.Unit): Boolean = player.attr[unit.saved] != null

    /** A null return is corrupt storage, distinct from a valid empty built unit. */
    fun contents(player: Player, unit: TreasureTrailStashCatalogue.Unit): List<Item>? {
        val raw = player.attr[unit.saved] ?: return emptyList()
        return try {
            val items = gson.fromJson(raw, Array<Item>::class.java)?.toList() ?: return null
            if (items.isEmpty()) return items
            if (items.size != unit.equipment.size || items.any { it.amount != 1 || it.id < 0 }) return null
            // Match each group once; do not accept several copies of the first group.
            if (items.indices.any { index -> unit.equipment[index].none { it.local() == items[index].id } }) null else items
        } catch (_: RuntimeException) { null }
    }

    /** Called at the end of the sourced three-tick build; every precondition is rechecked then. */
    fun build(player: Player, unit: TreasureTrailStashCatalogue.Unit): Result {
        if (built(player, unit)) return Result.ALREADY_BUILT
        // Crystal saw Wiki: the invisible +3 stacks with visible boosts on STASH,
        // and building a STASH consumes no crystal-saw charge.
        val crystal = unit.recipe.crystalSaw.local()?.let { player.inventory.contains(it) } == true
        if (player.skills.getCurrentLevel(Skills.CONSTRUCTION) + (if (crystal) 3 else 0) < unit.recipe.level) return Result.LEVEL
        if (unit.recipe.tools.any { group -> group.none { tool -> tool.local()?.let { player.inventory.contains(it) || player.equipment.contains(it) } == true } }) return Result.TOOLS
        val inventory = ItemContainer(player.inventory)
        for (material in unit.recipe.materials) {
            // An alternative is a complete stack (ten nails of ONE metal), never a mixed sum.
            val id = material.alternatives.firstOrNull { inventory.getItemCount(it) >= material.amount } ?: return Result.MATERIALS
            if (!inventory.remove(id, material.amount, assureFullRemoval = true).hasSucceeded()) return Result.MATERIALS
        }
        commit(inventory, player.inventory)
        player.attr[unit.saved] = "[]"
        player.addXp(Skills.CONSTRUCTION, unit.recipe.experience.toDouble())
        return Result.BUILT
    }

    fun search(player: Player, unit: TreasureTrailStashCatalogue.Unit): Result {
        if (!built(player, unit)) return Result.NOT_BUILT
        val stored = contents(player, unit) ?: return corrupt(unit)
        return if (stored.isEmpty()) deposit(player, unit) else withdraw(player, unit, stored)
    }

    private fun deposit(player: Player, unit: TreasureTrailStashCatalogue.Unit): Result {
        val inventory = ItemContainer(player.inventory)
        val equipment = ItemContainer(player.equipment)
        val saved = mutableListOf<Item>()
        val removedEquipment = mutableListOf<Pair<Int, Int>>()
        for (group in unit.equipment) {
            val allowed = group.mapNotNull { it.local() }.toSet()
            val inventorySlot = inventory.rawItems.indexOfFirst { it != null && it.id in allowed }
            if (inventorySlot >= 0) {
                saved += takeOne(inventory, inventorySlot)
                continue
            }
            if (player.attr[TAKE_EQUIPPED] != true) return Result.INCOMPLETE_SET
            val equipmentSlot = equipment.rawItems.indexOfFirst { it != null && it.id in allowed }
            if (equipmentSlot < 0) return Result.INCOMPLETE_SET
            if (!player.world.plugins.canUnequipSlot(player, equipmentSlot)) return Result.UNEQUIP_BLOCKED
            saved += takeOne(equipment, equipmentSlot)
            removedEquipment += equipmentSlot to saved.last().id
        }
        // Serialise before spending anything, so malformed items cannot cause a partial transaction.
        val encoded = gson.toJson(saved)
        commit(inventory, player.inventory)
        commit(equipment, player.equipment)
        player.attr[unit.saved] = encoded
        removedEquipment.forEach { (slot, id) -> EquipAction.onItemUnequip(player, id, slot) }
        return Result.STORED
    }

    private fun withdraw(player: Player, unit: TreasureTrailStashCatalogue.Unit, stored: List<Item>): Result {
        val inventory = ItemContainer(player.inventory)
        for (item in stored) {
            if (!inventory.addPreservingAttr(Item(item), assureFullInsertion = true).hasSucceeded()) return Result.NO_SPACE
        }
        commit(inventory, player.inventory)
        player.attr[unit.saved] = "[]"
        return Result.WITHDRAWN
    }

    private fun takeOne(container: ItemContainer, slot: Int): Item {
        val item = container[slot]!!
        container[slot] = if (item.amount == 1) null else Item(item, item.amount - 1)
        return Item(item, 1)
    }

    private fun commit(from: ItemContainer, to: ItemContainer) {
        for (slot in 0 until from.capacity) {
            val old = to[slot]; val next = from[slot]
            if (old?.id != next?.id || old?.amount != next?.amount || old?.attr != next?.attr) to[slot] = next
        }
    }

    private fun corrupt(unit: TreasureTrailStashCatalogue.Unit): Result {
        println("PROBLEM CLUE unreadable STASH ${unit.sourceId}; contents retained")
        return Result.CORRUPT
    }
}
