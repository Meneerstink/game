package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PointBalance
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue

/** OSRS Deadman Reward Store + Transcript:Nigel, source revisions pinned in docs/dmm2-research/wiki-manifest.json.
 * V3 = existing Deadman Points; no blood money/breach migration here. UI/session/NPC validation belongs to Claude.
 */
class NigelRewardStore(contract: Contract) {
    val contract = contract.copy(rewards = contract.rewards.toMap(), skins = contract.skins.map { it.copy(acceptedBases = it.acceptedBases.toSet()) })
    enum class Reward(val sourceId: Int, val price: Int, val key: String?) {
        ARMAGEDDON_WEAPONS(29625, 12000, "dmm2_armageddon_weapons"),
        CAPE_FABRIC(29628, 6000, "dmm2_armageddon_capes"),
        ARMAGEDDON_TELEPORT(29622, 9000, "dmm2_armageddon_teleport"),
        RUG(29619, 3000, null),
        ANNIHILATION_WEAPONS(33012, 12000, "dmm2_annihilation_weapons"),
        BLUEPRINTS(33015, 15000, "dmm2_wilderness_house"),
        ANNIHILATION_TELEPORT(33018, 10000, "dmm2_annihilation_teleport");
        val unlock: AttributeKey<Int>? = key?.let { AttributeKey<Int>(it) }
    }

    /** One exact charged/inactive state per pair. Never flatten charged and uncharged forms into one base. */
    data class Skin(val unlock: Reward, val base: Int, val cosmetic: Int, val acceptedBases: Set<Int> = setOf(base)) {
        init {
            require(unlock in setOf(Reward.ARMAGEDDON_WEAPONS, Reward.ANNIHILATION_WEAPONS, Reward.CAPE_FABRIC))
            require(base > 0 && cosmetic > 0 && base in acceptedBases && cosmetic !in acceptedBases && acceptedBases.all { it > 0 })
        }
    }

    data class Contract(val rewards: Map<Reward, Int>, val skins: List<Skin>, val exitBlueprint: Int?) {
        init {
            require(rewards.values.all { it > 0 } && rewards.values.distinct().size == rewards.size)
            require(exitBlueprint == null || exitBlueprint > 0)
            require(skins.map { it.cosmetic }.distinct().size == skins.size)
            val bases = skins.flatMap { it.acceptedBases }
            require(bases.distinct().size == bases.size)
            require(skins.none { it.cosmetic in bases })
        }
    }

    enum class Result { OK, NOT_READY, INVALID_AMOUNT, INVALID_SLOT, ITEM_CHANGED, NOT_ENOUGH_POINTS,
        NO_SPACE, POINT_OVERFLOW, ALREADY_UNLOCKED, LOCKED, UNSUPPORTED, NOTED_OR_MODIFIED, LAST_TELEPORT_ON }

    companion object {
        private val POINTS = StoreCatalogue.Currency.DEADMAN.attr
        /** Before 18-11 (Nigel 'Home Teleport animation'): ONE chosen style; only read to seed [TELEPORT_TOGGLES]. */
        val TELEPORT_SELECTION = AttributeKey<Int>("dmm2_home_teleport_selection")
        /** Bit (1 shl Teleport.value) per style toggled on (spellbook 'Animation', DeadmanHomeTeleport.toggleMenu). */
        val TELEPORT_TOGGLES = AttributeKey<Int>("dmm2_home_teleport_toggles")
        @Volatile var active: NigelRewardStore? = null
            private set
        fun install(contract: Contract) { active = NigelRewardStore(contract) }
        fun balance(player: Player): Int = PointBalance.of(player.attr, POINTS)
        fun unlocked(player: Player, reward: Reward): Boolean = reward.unlock?.let { player.attr[it] == 1 } == true
    }

    /** Unlimited stock; the UI must submit the chosen enum, not a client-supplied price/id. All-or-nothing. */
    fun buy(player: Player, reward: Reward, amount: Int = 1): Result {
        val id = contract.rewards[reward] ?: return Result.NOT_READY
        if (amount <= 0) return Result.INVALID_AMOUNT
        val cost = amount.toLong() * reward.price
        if (cost > balance(player).toLong()) return Result.NOT_ENOUGH_POINTS
        val inventory = ItemContainer(player.inventory)
        if (!DeadmanInventory.add(inventory, Item(id, amount))) return Result.NO_SPACE
        if (!PointBalance.take(player.attr, POINTS, cost.toInt())) return Result.NOT_ENOUGH_POINTS
        DeadmanInventory.publish(inventory, player.inventory)
        player.persistNow()
        return Result.OK
    }

    /** OSRS since 14 August 2024: full-price refund for an unconsumed physical reward, not a permanent unlock. */
    fun sellBack(player: Player, slot: Int, expectedId: Int, amount: Int = 1): Result {
        if (amount <= 0) return Result.INVALID_AMOUNT
        val inventory = ItemContainer(player.inventory)
        if (slot !in 0 until inventory.capacity) return Result.INVALID_SLOT
        val item = inventory[slot] ?: return Result.ITEM_CHANGED
        if (item.id != expectedId || item.amount <= 0) return Result.ITEM_CHANGED
        val base = item.toUnnoted(player.world.definitions).id
        val reward = contract.rewards.entries.firstOrNull { it.value == base }?.key ?: return Result.UNSUPPORTED
        if (item.hasAnyAttr()) return Result.NOTED_OR_MODIFIED
        val slots = inventory.rawItems.indices.filter { inventory[it]?.let { i -> i.id == expectedId && !i.hasAnyAttr() && i.amount > 0 } == true }
        if (slots.sumOf { inventory[it]!!.amount.toLong() } < amount.toLong()) return Result.ITEM_CHANGED
        val refund = amount.toLong() * reward.price
        if (balance(player).toLong() + refund > PointBalance.MAX || balance(player) < 0) return Result.POINT_OVERFLOW
        // Sale-X may span non-stackable copies of the exact selected id. Notes refund their base reward's price.
        var remaining = amount
        for (index in slots) {
            if (remaining == 0) break
            val held = inventory[index]!!
            val taken = minOf(remaining, held.amount)
            inventory[index] = if (held.amount == taken) null else Item(held, held.amount - taken)
            remaining -= taken
        }
        DeadmanInventory.publish(inventory, player.inventory)
        PointBalance.add(player.attr, POINTS, refund.toInt())
        player.persistNow()
        return Result.OK
    }

    /** Read scroll / give fabric to Nigel after its confirmation. Blueprints belong to the estate-agent route below. */
    fun unlock(player: Player, reward: Reward, slot: Int, expectedId: Int): Result {
        if (reward == Reward.RUG || reward == Reward.BLUEPRINTS) return Result.UNSUPPORTED
        return redeem(player, reward, slot, expectedId, replacement = null)
    }

    /** Estate agent calls only once the real Wilderness style is available. Replacement and unlock share one snapshot. */
    fun unlockWildernessStyle(player: Player, slot: Int, expectedId: Int): Result {
        val exit = contract.exitBlueprint ?: return Result.NOT_READY
        return redeem(player, Reward.BLUEPRINTS, slot, expectedId, Item(exit))
    }

    private fun redeem(player: Player, reward: Reward, slot: Int, expectedId: Int, replacement: Item?): Result {
        val id = contract.rewards[reward] ?: return Result.NOT_READY
        val key = reward.unlock ?: return Result.UNSUPPORTED
        if (unlocked(player, reward)) return Result.ALREADY_UNLOCKED
        if (slot !in 0 until player.inventory.capacity) return Result.INVALID_SLOT
        val item = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (expectedId != id || item.id != id || item.amount <= 0) return Result.ITEM_CHANGED
        if (item.hasAnyAttr() || item.getDef(player.world.definitions).noted) return Result.NOTED_OR_MODIFIED
        val inventory = ItemContainer(player.inventory)
        inventory[slot] = if (item.amount == 1) null else Item(item, item.amount - 1)
        if (replacement != null && !DeadmanInventory.add(inventory, replacement)) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory)
        player.attr[key] = 1
        player.persistNow()
        return Result.OK
    }

    /** Nigel's Add/Remove cosmetic: inventory item only, exact state, all charges/attributes retained without aliasing. */
    fun convert(player: Player, slot: Int, expectedId: Int): Result {
        if (slot !in 0 until player.inventory.capacity) return Result.INVALID_SLOT
        val item = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (item.id != expectedId || item.amount != 1) return Result.ITEM_CHANGED
        val skin = contract.skins.firstOrNull { item.id in it.acceptedBases || item.id == it.cosmetic } ?: return Result.UNSUPPORTED
        // Wiki Imbued ancient cape (Deadman) rev15403804: made with Ancient fabric, never by showing Nigel the plain cape.
        if (Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.matches(skin.cosmetic)) return Result.UNSUPPORTED
        if (!unlocked(player, skin.unlock)) return Result.LOCKED
        if (item.getDef(player.world.definitions).noted) return Result.NOTED_OR_MODIFIED
        player.inventory[slot] = Item(if (item.id == skin.cosmetic) skin.base else skin.cosmetic).copyAttr(item)
        player.persistNow()
        return Result.OK
    }

    /** Irreversible Ancient fabric + one of the THREE imbued Deadman god capes; no direct Nigel conversion. */
    fun craftAncientCape(player: Player, slot: Int, expectedId: Int): Result {
        val result = Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.local ?: return Result.NOT_READY
        val fabric = gg.rsmod.plugins.content.items.osrs.AncientCape.assets.fabric ?: return Result.NOT_READY
        val allowed = listOf(29613, 29615, 29617).mapNotNull { Dmm2Ids.item(it) }
        if (expectedId !in allowed) return Result.UNSUPPORTED
        if (slot !in 0 until player.inventory.capacity) return Result.INVALID_SLOT
        val item = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (item.id != expectedId || item.amount != 1) return Result.ITEM_CHANGED
        if (item.hasAnyAttr() || item.getDef(player.world.definitions).noted) return Result.NOTED_OR_MODIFIED
        val inventory = ItemContainer(player.inventory)
        if (!inventory.remove(fabric, assureFullRemoval = true).hasSucceeded()) return Result.ITEM_CHANGED
        inventory[slot] = Item(result)
        DeadmanInventory.publish(inventory, player.inventory)
        player.persistNow()
        return Result.OK
    }

    /** Death adapters can canonicalise cosmetics and then apply the EXISTING base-item D1-D20 rules. */
    fun baseItem(item: Item): Item? = contract.skins.firstOrNull { it.cosmetic == item.id }?.let {
        Item(it.base, item.amount).copyAttr(item)
    }

    fun reclaimExitBlueprints(player: Player): Result {
        if (!unlocked(player, Reward.BLUEPRINTS)) return Result.LOCKED
        val id = contract.exitBlueprint ?: return Result.NOT_READY
        val inventory = ItemContainer(player.inventory)
        if (!DeadmanInventory.add(inventory, Item(id))) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory)
        player.persistNow()
        return Result.OK
    }

    enum class Teleport(val value: Int, val reward: Reward?) {
        DEFAULT(0, null), ARMAGEDDON(1, Reward.ARMAGEDDON_TELEPORT), ANNIHILATION(2, Reward.ANNIHILATION_TELEPORT)
    }
    /** Default plus every unlocked style. */
    fun teleportStyles(player: Player): List<Teleport> = Teleport.values().filter { it.reward == null || unlocked(player, it.reward) }

    /** The styles toggled on (OSRS: at least one, the default counts); a player from before 18-11 keeps their Nigel choice. */
    fun teleportsOn(player: Player): List<Teleport> {
        val mask = player.attr[TELEPORT_TOGGLES] ?: (1 shl (player.attr[TELEPORT_SELECTION] ?: 0))
        return teleportStyles(player).filter { mask and (1 shl it.value) != 0 }.ifEmpty { listOf(Teleport.DEFAULT) }
    }

    /** Spellbook 'Animation' toggle: on <-> off; the last style on stays on (LAST_TELEPORT_ON). */
    fun toggleTeleport(player: Player, teleport: Teleport): Result {
        if (teleport.reward?.let { !unlocked(player, it) } == true) return Result.LOCKED
        val on = teleportsOn(player)
        val next = if (teleport in on) on - teleport else on + teleport
        if (next.isEmpty()) return Result.LAST_TELEPORT_ON
        player.attr[TELEPORT_TOGGLES] = next.fold(0) { mask, t -> mask or (1 shl t.value) }
        player.persistNow()
        return Result.OK
    }
}
