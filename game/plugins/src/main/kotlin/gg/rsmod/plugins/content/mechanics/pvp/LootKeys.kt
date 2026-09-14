package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import gg.rsmod.plugins.content.items.food.Food
import gg.rsmod.plugins.content.items.potion.Potion
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices

/**
 * RCV-012 decision 3b: Wilderness loot keys (OSRS Wiki "Loot key", "Skully", "Loot Chest", Skully transcript).
 *
 * Sourced rules implemented here:
 * - A killer who paid Skully 1,000,000 coins and has the enchantment on receives the victim's dropped loot as a loot key instead of a
 *   ground pile (food and potions can be sent to the floor instead); no loot, no key; with a full inventory the key drops to the ground
 *   for the killer.
 * - At most 5 keys: a killer already holding 5 gets the loot on the ground and "You have reached the limit of 5 loot keys.". The new loot
 *   key takes a slot first, then the victim's own keys transfer (even to a killer without the enchantment) and any key over the limit
 *   is deleted.
 * - A killer who is dying or dead: the victim's keys are destroyed and the loot drops to the floor.
 * - Keys are always lost on death (Protect Item / skull do not apply to them); on a PvM death they are removed.
 * - Destroying a key worth 1,000,000 coins or more inside a dangerous area fails with the sourced message.
 *
 * One OSRS item id per key (26651-26655 -> [KEY_IDS]); key i's loot lives in persistent slot i. Values are OSRS guide prices
 * ([OsrsGuidePrices]). ADAPTED: the OSRS "Wilderness Loot Key" interface is not in the 667 cache, so the chest shows the loot on the
 * price-checker grid (interface 206; withdraw with its item ops; the loot stays stored when the screen closes); with several keys the
 * chest opens the first one (the key-choice prompt text is not sourced; using a key on the chest picks it). BLOCKED / SOURCE_GAP:
 * skull key-count icons (OSRS sprites), valuable-item threshold (no sourced default), bank-note and bank buttons, key "Check" text,
 * the chest's "no keys" Skully line, the 30-tick disengage rule.
 */
object LootKeys {
    val KEY_IDS = intArrayOf(Items.LOOT_KEY, Items.LOOT_KEY_23697, Items.LOOT_KEY_23698, Items.LOOT_KEY_23699, Items.LOOT_KEY_23700)
    const val MAX_KEYS = 5
    const val UNLOCK_COST = 1_000_000
    const val DESTROY_VALUE_LIMIT = 1_000_000L
    const val LIMIT_MESSAGE = "You have reached the limit of 5 loot keys."
    const val DESTROY_TOO_VALUABLE_MESSAGE = "The loot is worth too much for you to destroy it here. Go somewhere safe first."

    val UNLOCKED = AttributeKey<Boolean>(persistenceKey = "loot_keys_unlocked")
    val ENABLED = AttributeKey<Boolean>(persistenceKey = "loot_keys_enabled")
    val FOOD_TO_FLOOR = AttributeKey<Boolean>(persistenceKey = "loot_keys_food_to_floor")
    val SLOTS = AttributeKey<MutableList<String>>(persistenceKey = "loot_keys_slots")

    /** Counters are stored as strings: persisted numbers come back from JSON as doubles. */
    val CLAIMED_KEYS = AttributeKey<String>(persistenceKey = "loot_keys_claimed_keys")
    val CLAIMED_VALUE = AttributeKey<String>(persistenceKey = "loot_keys_claimed_value")
    val DESTROYED_VALUE = AttributeKey<String>(persistenceKey = "loot_keys_destroyed_value")

    private val CHEST_SLOT = AttributeKey<Int>()
    private val CHEST_CONTAINER = AttributeKey<ItemContainer>()

    fun isKey(itemId: Int): Boolean = itemId in KEY_IDS

    fun keyIndex(itemId: Int): Int = KEY_IDS.indexOf(itemId)

    private val foodAndPotions: Set<Int> by lazy { (Food.values().map { it.item } + Potion.values().map { it.item }).toSet() }

    fun isFoodOrPotion(itemId: Int): Boolean = itemId in foodAndPotions

    // ---- slot storage ----

    fun encode(items: List<Item>): String = items.joinToString(",") { "${it.id}:${it.amount}" }

    fun decode(value: String): List<Item> =
        if (value.isBlank()) emptyList() else value.split(',').map { part -> part.split(':').let { Item(it[0].toInt(), it[1].toInt()) } }

    fun slots(player: Player): MutableList<String> {
        val stored = player.attr[SLOTS]?.map { it.toString() }?.toMutableList() ?: mutableListOf()
        while (stored.size < MAX_KEYS) stored += ""
        player.attr[SLOTS] = stored
        return stored
    }

    fun slotItems(
        player: Player,
        index: Int,
    ): List<Item> = decode(slots(player)[index])

    private fun setSlot(
        player: Player,
        index: Int,
        items: List<Item>,
    ) {
        val all = slots(player)
        all[index] = encode(items)
        player.attr[SLOTS] = all
    }

    private fun addCounter(
        player: Player,
        key: AttributeKey<String>,
        amount: Long,
    ) {
        player.attr[key] = ((player.attr[key]?.toLongOrNull() ?: 0L) + amount).toString()
    }

    fun counter(
        player: Player,
        key: AttributeKey<String>,
    ): Long = player.attr[key]?.toLongOrNull() ?: 0L

    fun heldKeyIndexes(player: Player): List<Int> = KEY_IDS.indices.filter { player.inventory.contains(KEY_IDS[it]) }

    /** A key slot is free when it stores nothing and its key is not in the inventory. */
    fun freeSlots(player: Player): List<Int> = KEY_IDS.indices.filter { slots(player)[it].isEmpty() && !player.inventory.contains(KEY_IDS[it]) }

    fun receivesKeys(player: Player): Boolean = player.attr[UNLOCKED] == true && player.attr[ENABLED] == true

    // ---- value ----

    fun value(
        definitions: DefinitionSet,
        items: List<Item>,
    ): Long =
        items.sumOf { item ->
            val unnoted = Item(item).toUnnoted(definitions)
            OsrsGuidePrices.seed(definitions.get(ItemDef::class.java, unnoted.id)).toLong() * item.amount
        }

    // ---- death ----

    /** The decision for one PvP kill, free of game state so every rule above is testable. */
    data class Plan(
        val keysToGive: List<List<Item>>,
        val ground: List<Item>,
        val destroyedKeys: Int,
        val limitReached: Boolean,
    )

    fun plan(
        killerFreeSlots: Int,
        killerReceivesKeys: Boolean,
        killerDyingOrDead: Boolean,
        victimKeyContents: List<List<Item>>,
        loot: List<Item>,
        foodToFloor: Boolean,
        isFood: (Int) -> Boolean,
    ): Plan {
        if (killerDyingOrDead) return Plan(emptyList(), loot, victimKeyContents.size, false)
        val keys = mutableListOf<List<Item>>()
        val ground = mutableListOf<Item>()
        var free = killerFreeSlots
        var limitReached = false
        if (killerReceivesKeys && loot.isNotEmpty()) {
            if (free <= 0) {
                ground += loot
                limitReached = true
            } else {
                val (toFloor, toKey) = loot.partition { foodToFloor && isFood(it.id) }
                ground += toFloor
                if (toKey.isNotEmpty()) {
                    keys += toKey
                    free--
                }
            }
        } else {
            ground += loot
        }
        val transferred = victimKeyContents.take(free.coerceAtLeast(0))
        keys += transferred
        return Plan(keys, ground, victimKeyContents.size - transferred.size, limitReached)
    }

    /**
     * Carries out [plan] for a Wilderness PvP death: [lost] are the victim's removed stacks. Returns the items that must still drop as
     * ground loot for the killer.
     */
    fun onWildernessPvpDeath(
        world: World,
        victim: Player,
        killer: Player?,
        lost: List<Item>,
    ): List<Item> {
        val victimKeys = lost.filter { isKey(it.id) }.map { keyIndex(it.id) }.distinct()
        val victimKeyContents = victimKeys.map { slotItems(victim, it) }.filter { it.isNotEmpty() }
        victimKeys.forEach { setSlot(victim, it, emptyList()) }
        val loot = lost.filterNot { isKey(it.id) }
        if (killer == null) return loot
        val decision =
            plan(
                killerFreeSlots = freeSlots(killer).size,
                killerReceivesKeys = receivesKeys(killer),
                killerDyingOrDead = killer.isDead(),
                victimKeyContents = victimKeyContents,
                loot = loot,
                foodToFloor = killer.attr[FOOD_TO_FLOOR] == true,
                isFood = ::isFoodOrPotion,
            )
        decision.keysToGive.forEach { items ->
            val index = freeSlots(killer).firstOrNull() ?: return@forEach
            setSlot(killer, index, items)
            if (!killer.inventory.add(KEY_IDS[index], 1).hasSucceeded()) {
                world.spawn(GroundItem(KEY_IDS[index], 1, victim.tile, killer))
            }
        }
        if (decision.limitReached) killer.message(LIMIT_MESSAGE)
        return decision.ground
    }

    /** PvM death: the removed keys and their loot are gone. */
    fun removeKeys(
        victim: Player,
        lostKeyIds: List<Int>,
    ) {
        lostKeyIds.filter { isKey(it) }.forEach { setSlot(victim, keyIndex(it), emptyList()) }
    }

    // ---- destroy ----

    fun canDestroyHere(
        value: Long,
        inDangerousArea: Boolean,
    ): Boolean = !inDangerousArea || value < DESTROY_VALUE_LIMIT

    fun destroyed(
        player: Player,
        index: Int,
    ) {
        addCounter(player, DESTROYED_VALUE, value(player.world.definitions, slotItems(player, index)))
        setSlot(player, index, emptyList())
    }

    // ---- chest (ADAPTED onto the price-checker grid) ----

    fun chestContainer(player: Player): ItemContainer? = player.attr[CHEST_CONTAINER]

    fun openChest(
        player: Player,
        index: Int,
    ) {
        player.attr[CHEST_SLOT] = index
        player.attr[CHEST_CONTAINER] = ItemContainer(player.world.definitions, PriceChecker.CAPACITY, ContainerStackType.NORMAL)
        PriceChecker.openGrid(player)
        refreshChest(player)
    }

    private fun refreshChest(player: Player) {
        val index = player.attr[CHEST_SLOT] ?: return
        val container = chestContainer(player) ?: return
        container.removeAll()
        slotItems(player, index).take(PriceChecker.CAPACITY).forEach { container.add(it.id, it.amount, assureFullInsertion = false) }
        PriceChecker.sendGrid(player, container)
    }

    fun withdraw(
        player: Player,
        gridSlot: Int,
        amount: Int,
    ) {
        val index = player.attr[CHEST_SLOT] ?: return
        val shown = chestContainer(player)?.get(gridSlot) ?: return
        val stored = slotItems(player, index).toMutableList()
        val at = stored.indexOfFirst { it.id == shown.id }
        if (at < 0 || amount <= 0) return
        val take = minOf(amount, stored[at].amount)
        val added = player.inventory.add(shown.id, take, assureFullInsertion = false)
        val moved = take - added.getLeftOver()
        if (moved <= 0) {
            player.message("You don't have enough inventory space.")
            return
        }
        addCounter(player, CLAIMED_VALUE, value(player.world.definitions, listOf(Item(shown.id, moved))))
        if (moved == stored[at].amount) stored.removeAt(at) else stored[at] = Item(shown.id, stored[at].amount - moved)
        setSlot(player, index, stored)
        if (stored.isEmpty()) {
            player.inventory.remove(KEY_IDS[index], 1)
            addCounter(player, CLAIMED_KEYS, 1)
            player.closeInterface(PriceChecker.INTERFACE_ID)
            return
        }
        refreshChest(player)
    }

    fun closeChest(player: Player) {
        player.attr.remove(CHEST_SLOT)
        player.attr.remove(CHEST_CONTAINER)
    }
}
