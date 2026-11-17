package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId

/** DMM corrupted weapons are unrelated to native corrupt Ancient Warriors gear (N457). */
object CorruptedWeapons {
    const val SCYTHE_CAPACITY = 20_000
    /** Source cache2686 param14; dark bow=7 (param13 range6), resolving the contradictory wiki infobox. */
    fun attackSpeed(id: Int?): Int? = when {
        ItemId.VOIDWAKER.matches(id) || ItemId.CLAWS.matches(id) || ItemId.KHOPESH.matches(id) -> 4
        ItemId.GODSWORD.matches(id) || ItemId.TWISTED_BOW.matches(id) -> 6
        isScythe(id) || ItemId.SHADOW.matches(id) || ItemId.VOLATILE.matches(id) -> 5
        ItemId.DARK_BOW.matches(id) -> 7
        else -> null
    }
    fun specialBase(id: Int): Int = when {
        ItemId.VOIDWAKER.matches(id) -> Items.VOIDWAKER
        ItemId.CLAWS.matches(id) -> Items.DRAGON_CLAWS
        ItemId.GODSWORD.matches(id) -> Items.ARMADYL_GODSWORD
        ItemId.DARK_BOW.matches(id) -> Items.DARK_BOW
        ItemId.VOLATILE.matches(id) -> Items.VOLATILE_NIGHTMARE_STAFF
        else -> NigelRewardStore.active?.baseItem(Item(id))?.id ?: id
    }
    fun bowBase(id: Int?): Int? = when {
        ItemId.TWISTED_BOW.matches(id) -> Items.TWISTED_BOW
        ItemId.DARK_BOW.matches(id) -> Items.DARK_BOW
        else -> id?.let { NigelRewardStore.active?.baseItem(Item(it))?.id ?: it }
    }
    fun isScythe(id: Int?): Boolean = ItemId.SCYTHE.matches(id) || ItemId.SCYTHE_EMPTY.matches(id)
    /** Charged scythe hits a large target up to three times, each independent accuracy, max/2 rounded down at each step. */
    fun scytheMaxHits(max: Int, targetSize: Int): List<Int> = (0 until targetSize.coerceIn(1, 3)).map { max.coerceAtLeast(0) shr it }
    /** Wiki Scythe of Vitur rev15398553: three adjacent squares in the direction of the primary target. */
    fun scytheArc(origin: gg.rsmod.game.model.Tile, facing: gg.rsmod.game.model.Tile): List<gg.rsmod.game.model.Tile> {
        if (origin.height != facing.height) return emptyList()
        val dx = (facing.x - origin.x).coerceIn(-1, 1)
        val dz = (facing.z - origin.z).coerceIn(-1, 1)
        if (dx == 0 && dz == 0) return emptyList()
        return (-1..1).flatMap { x -> (-1..1).map { z -> x to z } }
            .filter { (x, z) -> x * dx + z * dz > 0 && kotlin.math.abs(x * dz - z * dx) <= 1 }
            .map { (x, z) -> origin.transform(x, z) }
    }

    /** Primary first, up to three hit slots total; extras obey the same combat/multi/line-of-sight checks as the swing. */
    fun scytheTargets(player: Player, primary: gg.rsmod.game.model.entity.Pawn): List<gg.rsmod.game.model.entity.Pawn> {
        val out = MutableList(primary.getSize().coerceIn(1, 3)) { primary }
        if (out.size == 3) return out
        val facing = gg.rsmod.game.model.Tile(
            player.tile.x.coerceIn(primary.tile.x, primary.tile.x + primary.getSize() - 1),
            player.tile.z.coerceIn(primary.tile.z, primary.tile.z + primary.getSize() - 1), primary.tile.height)
        val arc = scytheArc(player.tile, facing)
        if (!player.tile.isMulti(player.world) || !primary.tile.isMulti(player.world)) return out
        val candidates: List<gg.rsmod.game.model.entity.Pawn> =
            if (primary is gg.rsmod.game.model.entity.Npc) player.world.npcs.entries.filterNotNull()
            else player.world.players.entries.filterNotNull().filter { it !== player }
        for (candidate in candidates) {
            if (candidate === primary || candidate.isDead() || candidate.tile.height != player.tile.height) continue
            if (candidate is gg.rsmod.game.model.entity.Npc && !candidate.isSpawned()) continue
            if (candidate is Player && !candidate.isOnline) continue
            if (!candidate.tile.isMulti(player.world)) continue
            val touched = arc.filter { it.x in candidate.tile.x until candidate.tile.x + candidate.getSize() &&
                it.z in candidate.tile.z until candidate.tile.z + candidate.getSize() }
            if (touched.isEmpty()) continue
            if (!gg.rsmod.plugins.content.combat.Combat.canEngage(player, candidate) ||
                touched.none { player.world.collision.raycast(player.tile, it, projectile = false) }) continue
            repeat(minOf(candidate.getSize(), 3 - out.size)) { out += candidate }
            if (out.size == 3) break
        }
        return out
    }
    fun scytheCharges(item: Item): Int = if (ItemId.SCYTHE.matches(item.id)) (item.attr[ItemAttribute.CHARGES] ?: 0).coerceIn(0, SCYTHE_CAPACITY) else 0
    fun consumeScythe(item: Item): Item {
        if (!ItemId.SCYTHE.matches(item.id)) return Item(item)
        val left = (scytheCharges(item) - 1).coerceAtLeast(0)
        return Item(if (left == 0) ItemId.SCYTHE_EMPTY.local ?: return Item(item) else item.id).copyAttr(item).also {
            if (left == 0) it.attr.remove(ItemAttribute.CHARGES) else it.attr[ItemAttribute.CHARGES] = left
        }
    }
    enum class Result { OK, NOT_READY, ITEM_CHANGED, INVALID_AMOUNT, NO_RUNES, NO_SPACE }
    /** Whole 100-charge batches, 300 blood runes each; no vial cost. */
    fun chargeScythe(player: Player, slot: Int, expectedId: Int, batches: Int): Result {
        val charged = ItemId.SCYTHE.local ?: return Result.NOT_READY
        val empty = ItemId.SCYTHE_EMPTY.local ?: return Result.NOT_READY
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val held = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (held.id != expectedId || held.id !in setOf(charged, empty) || held.amount != 1 || held.getDef(player.world.definitions).noted) return Result.ITEM_CHANGED
        val current = scytheCharges(held)
        if (batches <= 0 || batches.toLong() * 100 + current > SCYTHE_CAPACITY) return Result.INVALID_AMOUNT
        val inventory = ItemContainer(player.inventory)
        if (!inventory.remove(Items.BLOOD_RUNE, batches * 300).hasSucceeded()) return Result.NO_RUNES
        inventory[slot] = Item(charged).copyAttr(held).putAttr(ItemAttribute.CHARGES, current + batches * 100)
        DeadmanInventory.publish(inventory, player.inventory); player.persistNow()
        return Result.OK
    }
    fun unchargeScythe(player: Player, slot: Int, expectedId: Int, atWell: Boolean = false): Result {
        val empty = ItemId.SCYTHE_EMPTY.local ?: return Result.NOT_READY
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val held = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (!ItemId.SCYTHE.matches(held.id) || held.id != expectedId || held.amount != 1) return Result.ITEM_CHANGED
        val inventory = ItemContainer(player.inventory)
        inventory[slot] = Item(empty).copyAttr(held).also { it.attr.remove(ItemAttribute.CHARGES) }
        // Scythe wiki: outside a vyre well charges are destroyed; at a well only whole 100-charge batches return.
        val returned = scytheCharges(held) / 100 * 300
        if (atWell && returned > 0 && !DeadmanInventory.add(inventory, Item(Items.BLOOD_RUNE, returned))) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory); player.persistNow()
        return Result.OK
    }
}
