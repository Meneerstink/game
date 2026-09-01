package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * R07.3 (Beast of Burden storage): extra-inventory-space container for the 3 named BoB
 * familiars - Pack Yak 30 / War Tortoise 18 / Spirit Terrorbird 12 slots, exact capacities
 * given by the work order itself, not guessed.
 *
 * Reuses the existing generic [Player.containers]/[ContainerKey]/`register_container_key`
 * persistence mechanism ([gg.rsmod.game.service.serializer.json.JsonPlayerSerializer] already
 * saves/loads any registered container generically) rather than adding bespoke fields to
 * [Player] - the same pattern [gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY] already
 * establishes for a plugin-owned secondary container.
 *
 * No graphical deposit/withdraw interface is built here: real RS's BoB interface component
 * layout is a specific 667-era cache interface this session has not located or verified an ID
 * for, and the work order explicitly forbids guessing interface/component IDs. Deposit is
 * instead exposed the same real, non-graphical way as [gg.rsmod.plugins.content.mechanics.death.DeathRecoveryService]
 * exposes death-recovery: using an inventory item on the familiar deposits it (matching real
 * RS's own "drag item onto your pack animal" mechanic - wired in `familiar.plugin.kts`), and
 * the familiar's existing Renew/Dismiss/Cancel interact menu gains a "Withdraw-all" option.
 */
object BeastOfBurden {
    data class Storage(val key: ContainerKey, val essenceOnly: Boolean = false)

    val THORNY_SNAIL_KEY = ContainerKey("bob_thorny_snail", capacity = 3, stackType = ContainerStackType.NORMAL)
    val SPIRIT_KALPHITE_KEY = ContainerKey("bob_spirit_kalphite", capacity = 6, stackType = ContainerStackType.NORMAL)
    val BULL_ANT_KEY = ContainerKey("bob_bull_ant", capacity = 9, stackType = ContainerStackType.NORMAL)
    val SPIRIT_TERRORBIRD_KEY = ContainerKey("bob_spirit_terrorbird", capacity = 12, stackType = ContainerStackType.NORMAL)
    val ABYSSAL_PARASITE_KEY = ContainerKey("bob_abyssal_parasite", capacity = 7, stackType = ContainerStackType.NORMAL)
    val ABYSSAL_LURKER_KEY = ContainerKey("bob_abyssal_lurker", capacity = 7, stackType = ContainerStackType.NORMAL)
    val WAR_TORTOISE_KEY = ContainerKey("bob_war_tortoise", capacity = 18, stackType = ContainerStackType.NORMAL)
    val ABYSSAL_TITAN_KEY = ContainerKey("bob_abyssal_titan", capacity = 7, stackType = ContainerStackType.NORMAL)
    val PACK_YAK_KEY = ContainerKey("bob_pack_yak", capacity = 30, stackType = ContainerStackType.NORMAL)

    /**
     * Not a real carry-capacity BoB familiar - reused for the Albino Rat's Cheese Feast special
     * (real RS generates cheese "in the albino rat's inventory", a per-familiar personal store
     * with the same shape as BoB storage). Registering it here is the smallest way to reuse the
     * existing container/persistence machinery; the one inauthentic side effect is that using an
     * item on the rat will also deposit it here same as a real BoB familiar, which real RS does
     * not allow for this familiar - harmless, so not worth a parallel container system for.
     */
    val ALBINO_RAT_KEY = ContainerKey("bob_albino_rat", capacity = 4, stackType = ContainerStackType.NORMAL)

    private val storageByPouch = mapOf(
        SummoningPouchData.THORNY_SNAIL to Storage(THORNY_SNAIL_KEY),
        SummoningPouchData.SPIRIT_KALPHITE to Storage(SPIRIT_KALPHITE_KEY),
        SummoningPouchData.BULL_ANT to Storage(BULL_ANT_KEY),
        SummoningPouchData.SPIRIT_TERRORBIRD to Storage(SPIRIT_TERRORBIRD_KEY),
        SummoningPouchData.ABYSSAL_PARASITE to Storage(ABYSSAL_PARASITE_KEY, essenceOnly = true),
        SummoningPouchData.ABYSSAL_LURKER to Storage(ABYSSAL_LURKER_KEY, essenceOnly = true),
        SummoningPouchData.WAR_TORTOISE to Storage(WAR_TORTOISE_KEY),
        SummoningPouchData.ABYSSAL_TITAN to Storage(ABYSSAL_TITAN_KEY, essenceOnly = true),
        SummoningPouchData.PACK_YAK to Storage(PACK_YAK_KEY),
        SummoningPouchData.ALBINO_RAT to Storage(ALBINO_RAT_KEY),
    )

    val allKeys = storageByPouch.values.map { it.key }

    private fun storage(player: Player): Storage? {
        val npc = Familiar.current(player) ?: return null
        val pouch = SummoningPouchData.values.firstOrNull { it.npc == npc.id } ?: return null
        return storageByPouch[pouch]
    }
    /** True if [npcId] is one of the 3 real BoB familiars' summoned npc id. */
    fun isBobNpc(npcId: Int): Boolean = storageByPouch.keys.any { it.npc == npcId }

    /** The active familiar inventory key, if the summoned familiar can carry items. */
    fun activeKey(player: Player): ContainerKey? = storage(player)?.key

    private fun container(
        player: Player,
        key: ContainerKey,
    ): ItemContainer = player.containers.getOrPut(key) { ItemContainer(player.world.definitions, key) }

    /**
     * Deposits [item] from [player]'s inventory into the active BoB container, best-effort
     * (whatever fits). Returns the amount actually moved; 0 (with a message) if no BoB
     * familiar is out or nothing fit.
     */
    fun deposit(
        player: Player,
        item: Item,
    ): Int {
        val storage = storage(player) ?: run {
            player.message("You need an active Beast of Burden familiar out to store items with it.")
            return 0
        }
        val key = storage.key
        val essence = item.id == Items.RUNE_ESSENCE || item.id == Items.PURE_ESSENCE
        if (storage.essenceOnly != essence) {
            player.message("Your familiar can't carry that item.")
            return 0
        }
        val transaction = container(player, key).add(item.id, item.amount, assureFullInsertion = false)
        if (transaction.completed <= 0) {
            player.message("Your familiar can't carry any more of that.")
            return 0
        }
        player.inventory.remove(Item(item.id, transaction.completed), assureFullRemoval = true)
        return transaction.completed
    }

    /**
     * Adds [item] directly into the active familiar's storage without taking it from the
     * player's inventory first - for specials that generate items in the familiar itself
     * (e.g. Cheese Feast) rather than depositing something the player already carries.
     * Returns the amount actually added.
     */
    fun grant(player: Player, item: Item): Int {
        val key = activeKey(player) ?: return 0
        return container(player, key).add(item.id, item.amount, assureFullInsertion = false).completed
    }

    /** Withdraws everything from the active BoB container into the inventory, best-effort. */
    fun withdrawAll(player: Player): Int {
        val key = activeKey(player) ?: return 0
        val container = container(player, key)
        var withdrawn = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            val transaction = player.inventory.add(item.id, item.amount, assureFullInsertion = false)
            if (transaction.completed <= 0) continue
            container[slot] = if (transaction.completed == item.amount) null else Item(item.id, item.amount - transaction.completed)
            withdrawn += transaction.completed
        }
        return withdrawn
    }

    /** Withdraws [amount] of whatever occupies [slot] in the active BoB container, best-effort. */
    fun withdraw(
        player: Player,
        slot: Int,
        amount: Int,
    ): Int {
        val key = activeKey(player) ?: return 0
        val container = container(player, key)
        val item = container[slot] ?: return 0
        val take = minOf(amount, item.amount)
        val transaction = player.inventory.add(item.id, take, assureFullInsertion = false)
        if (transaction.completed <= 0) return 0
        container[slot] = if (transaction.completed == item.amount) null else Item(item.id, item.amount - transaction.completed)
        return transaction.completed
    }

    /** Deposits every permitted inventory item into the active familiar, best-effort. */
    fun depositAll(player: Player): Int {
        if (activeKey(player) == null) {
            player.message("You need an active Beast of Burden familiar out to store items with it.")
            return 0
        }
        var deposited = 0
        for (slot in 0 until player.inventory.capacity) {
            val item = player.inventory[slot] ?: continue
            deposited += deposit(player, item)
        }
        return deposited
    }
    /**
     * A dismissed, expired or replaced familiar drops its stored items at the familiar's tile.
     * Logout deliberately does not call this: the same familiar and items return on login.
     */
    fun release(player: Player, tile: Tile) {
        val key = activeKey(player) ?: return
        val held = container(player, key)
        for (slot in 0 until held.capacity) {
            val item = held[slot] ?: continue
            player.world.spawn(GroundItem(item, tile, player))
            held[slot] = null
        }
    }
    /**
     * Empties the active BoB container without dropping anything. Used only by
     * [Familiar.ownerDeath], where this revision loses the cargo outright rather than dropping it
     * - see that function for the sourcing. Every other despawn path uses [release].
     */
    fun discard(player: Player) {
        val key = activeKey(player) ?: return
        val held = container(player, key)
        for (slot in 0 until held.capacity) {
            held[slot] = null
        }
    }

    /** Non-null (slot, item) pairs in the active BoB container, for building a withdraw-selection prompt. */
    fun contents(player: Player): List<IndexedValue<Item>> {
        val key = activeKey(player) ?: return emptyList()
        val container = container(player, key)
        return (0 until container.capacity).mapNotNull { slot -> container[slot]?.let { IndexedValue(slot, it) } }
    }
}
