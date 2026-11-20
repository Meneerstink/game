package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.areas.poh.PlayerHouse

/** Server state/materials for imported POH rewards. No invented object ids, house chunks or client layouts.
 * Claude's POH adapter registers actual hotspots, reads installed() to render them, and validates proximity/room existence.
 */
class NigelHouseRewards(
    private val store: NigelRewardStore,
    slots: Set<Slot>,
    private val validHotspot: (Player, Slot) -> Boolean,
    private val otherExitPortals: (Player) -> Int,
) {
    enum class Kind { RUG, EXIT_PORTAL }
    data class Slot(val key: String, val kind: Kind, val room: String = key) {
        init { require(key.matches(Regex("[a-z0-9_]{1,48}")) && room.matches(Regex("[a-z0-9_]{1,48}"))) }
        val builtKey = AttributeKey<Int>("dmm2_poh_${kind.name.lowercase()}_$key")
    }
    private val slots = slots.toSet().also { require(it.map { s -> s.key }.distinct().size == it.size) }
    private data class Build(val slot: Slot, val cycle: Int, val tile: Tile)
    private val pending = AttributeKey<Build>(resetOnDeath = true)
    enum class Result { OK, INVALID_HOTSPOT, NOT_OWNER, NOT_BUILD_MODE, OCCUPIED, EMPTY, LEVEL, MATERIALS,
        TOO_EARLY, NOT_STARTED, NO_SPACE, LAST_PORTAL, NOT_READY }

    fun installed(player: Player, slot: Slot): Boolean = slot in slots && player.attr[slot.builtKey] == 1
    private fun access(player: Player, slot: Slot): Result {
        if (slot !in slots || player.isDead() || !validHotspot(player, slot)) return Result.INVALID_HOTSPOT
        if (!PlayerHouse.ownsHouseAt(player)) return Result.NOT_OWNER
        if (!PlayerHouse.isBuildMode(player)) return Result.NOT_BUILD_MODE
        return Result.OK
    }
    private fun tools(player: Player): Boolean = player.inventory.contains(Items.HAMMER) && player.inventory.contains(Items.SAW)

    /** Source Deadman rug rev15122909: boosted Construction28, 4 cloth + rug, saw/hammer, 5 ticks, 283 XP. */
    fun beginRug(player: Player, slot: Slot): Result {
        cancel(player)
        val check = access(player, slot)
        if (check != Result.OK) return check
        if (slot.kind != Kind.RUG) return Result.INVALID_HOTSPOT
        if (installed(player, slot)) return Result.OCCUPIED
        if (player.skills.getCurrentLevel(Skills.CONSTRUCTION) < 28) return Result.LEVEL
        val rug = store.contract.rewards[NigelRewardStore.Reward.RUG] ?: return Result.NOT_READY
        if (!tools(player) || player.inventory.getItemCount(Items.BOLT_OF_CLOTH) < 4 || !player.inventory.contains(rug)) return Result.MATERIALS
        player.attr[pending] = Build(slot, player.world.currentCycle, player.tile)
        return Result.OK
    }
    fun cancel(player: Player) { player.attr.remove(pending) }

    /** Caller waits in the existing interruptible construction queue, then invokes this with no client-supplied materials/XP. */
    fun finishRug(player: Player): Result {
        val build = player.attr[pending] ?: return Result.NOT_STARTED
        if (player.world.currentCycle - build.cycle < 5) return Result.TOO_EARLY
        cancel(player)
        val check = access(player, build.slot)
        if (check != Result.OK) return check
        if (player.tile != build.tile) return Result.INVALID_HOTSPOT
        if (installed(player, build.slot)) return Result.OCCUPIED
        if (player.skills.getCurrentLevel(Skills.CONSTRUCTION) < 28) return Result.LEVEL
        val rug = store.contract.rewards[NigelRewardStore.Reward.RUG] ?: return Result.NOT_READY
        if (!tools(player)) return Result.MATERIALS
        val inventory = ItemContainer(player.inventory)
        if (!inventory.remove(Items.BOLT_OF_CLOTH, 4, assureFullRemoval = true).hasSucceeded() ||
            !inventory.remove(rug, assureFullRemoval = true).hasSucceeded()) return Result.MATERIALS
        DeadmanInventory.publish(inventory, player.inventory)
        player.attr[build.slot.builtKey] = 1
        player.addXp(Skills.CONSTRUCTION, 283.0)
        player.persistNow()
        return Result.OK
    }

    /** Source exit-blueprints: any house style, 10 iron bars + blueprints + inventory saw/hammer. */
    fun buildExitPortal(player: Player, slot: Slot): Result {
        val check = access(player, slot)
        if (check != Result.OK) return check
        if (slot.kind != Kind.EXIT_PORTAL) return Result.INVALID_HOTSPOT
        if (installed(player, slot)) return Result.OCCUPIED
        val blueprint = store.contract.exitBlueprint ?: return Result.NOT_READY
        if (!tools(player)) return Result.MATERIALS
        val inventory = ItemContainer(player.inventory)
        if (!inventory.remove(Items.IRON_BAR, 10, assureFullRemoval = true).hasSucceeded() ||
            !inventory.remove(blueprint, assureFullRemoval = true).hasSucceeded()) return Result.MATERIALS
        DeadmanInventory.publish(inventory, player.inventory)
        player.attr[slot.builtKey] = 1
        player.persistNow()
        return Result.OK
    }

    private fun exitCount(player: Player): Int = otherExitPortals(player).coerceAtLeast(0) + slots.count {
        it.kind == Kind.EXIT_PORTAL && installed(player, it)
    }

    /** Removing furniture returns only the rug; deleting its room returns nothing. Never remove the last exit portal. */
    fun remove(player: Player, slot: Slot, deletingRoom: Boolean = false): Result {
        if (deletingRoom) return deleteRoom(player, slot.room)
        val check = access(player, slot)
        if (check != Result.OK) return check
        if (!installed(player, slot)) return Result.EMPTY
        if (slot.kind == Kind.EXIT_PORTAL && exitCount(player) <= 1) return Result.LAST_PORTAL
        val inventory = ItemContainer(player.inventory)
        if (slot.kind == Kind.RUG && !deletingRoom) {
            val rug = store.contract.rewards[NigelRewardStore.Reward.RUG] ?: return Result.NOT_READY
            if (!DeadmanInventory.add(inventory, Item(rug))) return Result.NO_SPACE
        }
        DeadmanInventory.publish(inventory, player.inventory)
        player.attr.remove(slot.builtKey)
        player.persistNow()
        return Result.OK
    }

    /** Preflight the WHOLE registered room before removing anything; no partial last-portal deletion. */
    fun deleteRoom(player: Player, room: String): Result {
        val affected = slots.filter { it.room == room }
        if (affected.isEmpty()) return Result.INVALID_HOTSPOT
        for (slot in affected) {
            val check = access(player, slot)
            if (check != Result.OK) return check
        }
        val exits = affected.count { it.kind == Kind.EXIT_PORTAL && installed(player, it) }
        if (exits > 0 && exitCount(player) - exits < 1) return Result.LAST_PORTAL
        affected.forEach { player.attr.remove(it.builtKey) }
        if (player.attr[pending]?.slot?.room == room) cancel(player)
        player.persistNow()
        return Result.OK
    }
}
