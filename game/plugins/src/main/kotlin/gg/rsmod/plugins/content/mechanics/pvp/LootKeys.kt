package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
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
 * ([OsrsGuidePrices]). The chest is the OSRS "Loot keys" screen ([LootKeyChest], 667 interface 1149 built from OSRS interface 742 and its
 * sprites): key tabs, loot grid with Withdraw ops, Item/Note mode, Withdraw all to inventory/bank, Destroy with confirmation. The loot
 * stays stored when the screen closes. SOURCE_GAP: valuable-item threshold (no sourced default), the chest's "no keys" Skully line,
 * the 30-tick disengage rule.
 */
object LootKeys {
    val KEY_IDS = intArrayOf(Items.LOOT_KEY, Items.LOOT_KEY_23697, Items.LOOT_KEY_23698, Items.LOOT_KEY_23699, Items.LOOT_KEY_23700)
    const val MAX_KEYS = 5
    const val UNLOCK_COST = 1_000_000
    const val DESTROY_VALUE_LIMIT = 1_000_000L
    const val LIMIT_MESSAGE = "You have reached the limit of 5 loot keys."

    /**
     * Owner 2026-09-18 sounds (ADAPTED - OSRS Deadman publishes none of these; ids are the 667 cache's
     * own named synth sounds, `Sfx`): a key landing in the inventory, destroying a key's loot, and
     * sending a key's loot to the bank.
     */
    const val KEY_RECEIVED_SOUND = gg.rsmod.plugins.api.cfg.Sfx.COINS_JINGLE_1
    const val KEY_DESTROYED_SOUND = gg.rsmod.plugins.api.cfg.Sfx.DESTROY_OBJECT
    const val KEY_BANKED_SOUND = gg.rsmod.plugins.api.cfg.Sfx.BANK_DRAWER
    const val DESTROY_TOO_VALUABLE_MESSAGE = "The loot is worth too much for you to destroy it here. Go somewhere safe first."

    val UNLOCKED = AttributeKey<Boolean>(persistenceKey = "loot_keys_unlocked")
    val ENABLED = AttributeKey<Boolean>(persistenceKey = "loot_keys_enabled")
    val FOOD_TO_FLOOR = AttributeKey<Boolean>(persistenceKey = "loot_keys_food_to_floor")
    val SLOTS = AttributeKey<MutableList<String>>(persistenceKey = "loot_keys_slots")

    /** Counters are stored as strings: persisted numbers come back from JSON as doubles. */
    val CLAIMED_KEYS = AttributeKey<String>(persistenceKey = "loot_keys_claimed_keys")
    val CLAIMED_VALUE = AttributeKey<String>(persistenceKey = "loot_keys_claimed_value")
    val DESTROYED_VALUE = AttributeKey<String>(persistenceKey = "loot_keys_destroyed_value")


    fun isKey(itemId: Int): Boolean = itemId in KEY_IDS

    fun keyIndex(itemId: Int): Int = KEY_IDS.indexOf(itemId)

    private val foodAndPotions: Set<Int> by lazy { (Food.values().map { it.item } + Potion.values().map { it.item }).toSet() }

    fun isFoodOrPotion(itemId: Int): Boolean = itemId in foodAndPotions

    /**
     * Skully's "food and potions to the floor" classifier (owner 2026-09-18: the toggle must work
     * exactly). The 2011 [Food]/[Potion] tables plus anything the cache itself marks edible or
     * drinkable (an "Eat" / "Drink" inventory option), so OSRS-imported food and potions - divine
     * potions, brews, anglerfish - drop to the floor too instead of silently landing in the key.
     */
    fun isFoodOrPotion(
        definitions: DefinitionSet,
        itemId: Int,
    ): Boolean {
        val unnoted = Item(itemId, 1).toUnnoted(definitions).id
        if (isFoodOrPotion(unnoted)) return true
        val def = definitions.getNullable(ItemDef::class.java, unnoted) ?: return false
        return def.inventoryMenu.any { it.equals("Eat", ignoreCase = true) || it.equals("Drink", ignoreCase = true) }
    }

    // ---- slot storage ----

    /**
     * "id:amount" per stack; audit X-06 appends ":ATTR=value;ATTR=value" when the stack carries item
     * attributes (charges, stored runes), so looted items are not handed over "fresh". [decode] still
     * reads the old two-field form.
     */
    fun encode(items: List<Item>): String =
        items.joinToString(",") { item ->
            val base = "${item.id}:${item.amount}"
            if (item.attr.isEmpty()) base else base + ":" + item.attr.entries.joinToString(";") { "${it.key.name}=${it.value}" }
        }

    fun decode(value: String): List<Item> =
        if (value.isBlank()) {
            emptyList()
        } else {
            value.split(',').map { part ->
                val fields = part.split(':')
                val item = Item(fields[0].toInt(), fields[1].toInt())
                if (fields.size > 2) {
                    fields[2].split(';').forEach { pair ->
                        val (name, v) = pair.split('=').let { (it.getOrNull(0) ?: "") to it.getOrNull(1)?.toIntOrNull() }
                        val key = ItemAttribute.values().firstOrNull { it.name == name }
                        if (key != null && v != null) item.attr[key] = v
                    }
                }
                item
            }
        }

    fun slots(player: Player): MutableList<String> {
        val stored = player.attr[SLOTS]?.map { it.toString() }?.toMutableList() ?: mutableListOf()
        while (stored.size < MAX_KEYS) stored += ""
        releaseDespawnedKeys(player, stored)
        player.attr[SLOTS] = stored
        return stored
    }

    /**
     * Audit X-12: when a key drops to the floor (full inventory) and despawns unclaimed, its loot is gone
     * with it - the slot must not stay occupied forever. "index:deadlineMs" per key on the floor.
     */
    val GROUND_KEY_DEADLINES = AttributeKey<String>(persistenceKey = "loot_keys_ground_deadlines")

    /** Ground items are private for 100 ticks and gone at 200 (GroundItem); a small margin on top. */
    private const val GROUND_KEY_LIFETIME_MS = 210L * 600L

    private fun releaseDespawnedKeys(
        player: Player,
        stored: MutableList<String>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val raw = player.attr[GROUND_KEY_DEADLINES] ?: return
        val remaining = mutableListOf<String>()
        raw.split(',').filter { it.isNotBlank() }.forEach { entry ->
            val index = entry.substringBefore(':').toIntOrNull() ?: return@forEach
            val deadline = entry.substringAfter(':').toLongOrNull() ?: return@forEach
            when {
                index !in 0 until MAX_KEYS -> Unit
                player.inventory.contains(KEY_IDS[index]) -> Unit // picked up again
                deadline < nowMs -> stored[index] = ""
                else -> remaining += entry
            }
        }
        if (remaining.isEmpty()) player.attr.remove(GROUND_KEY_DEADLINES) else player.attr[GROUND_KEY_DEADLINES] = remaining.joinToString(",")
    }

    private fun markKeyOnGround(
        player: Player,
        index: Int,
    ) {
        val deadline = System.currentTimeMillis() + GROUND_KEY_LIFETIME_MS
        val existing = player.attr[GROUND_KEY_DEADLINES]?.split(',')?.filter { it.isNotBlank() && !it.startsWith("$index:") } ?: emptyList()
        player.attr[GROUND_KEY_DEADLINES] = (existing + "$index:$deadline").joinToString(",")
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

    /**
     * Deadman Mode (owner 2026-09-17: "1 kill = 1 key ... 5 kill = 5 keys"): every player receives a
     * loot key for every kill - there is no enchantment to buy any more. Skully can still switch the
     * keys off ([ENABLED] false) or send food to the floor; [UNLOCKED] is kept only for old saves.
     */
    fun receivesKeys(player: Player): Boolean = player.attr[ENABLED] != false

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

    /**
     * Owner 2026-09-18 (#6): a kill gives a loot key OR ground loot, never both - loot lies on the
     * floor next to a key only when the killer already holds the maximum 5 keys, or for food and
     * potions when the killer has explicitly chosen Skully's OSRS "food and potions to the floor"
     * option ([FOOD_TO_FLOOR]; owner: "if a person triggers at Skully drop food and potions to
     * ground it works exactly"). Everything else that drops for the killer - including converted
     * loot such as repair coins or an uncharged staff - goes into the key.
     */
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
        // Audit D-01: every stored key's loot is at risk on a PvP death, wherever the key item itself is.
        val victimKeys = (lost.filter { isKey(it.id) }.map { keyIndex(it.id) } + KEY_IDS.indices.filter { slotItems(victim, it).isNotEmpty() }).distinct()
        val victimKeyContents = victimKeys.map { slotItems(victim, it) }.filter { it.isNotEmpty() }
        victimKeys.forEach { setSlot(victim, it, emptyList()) }
        val loot = lost.filterNot { isKey(it.id) }
        // No player killer (a skulled player killed by a guard): the keys' loot joins the public ground loot, never deleted.
        if (killer == null) return loot + victimKeyContents.flatten()
        val decision =
            plan(
                killerFreeSlots = freeSlots(killer).size,
                killerReceivesKeys = receivesKeys(killer),
                killerDyingOrDead = killer.isDead(),
                victimKeyContents = victimKeyContents,
                loot = loot,
                foodToFloor = killer.attr[FOOD_TO_FLOOR] == true,
                isFood = { isFoodOrPotion(world.definitions, it) },
            )
        decision.keysToGive.forEach { items ->
            val index = freeSlots(killer).firstOrNull() ?: return@forEach
            setSlot(killer, index, items)
            if (!killer.inventory.add(KEY_IDS[index], 1).hasSucceeded()) {
                // Owner 2026-09-17: a full inventory never loses the key - it lands on the victim's tile.
                world.spawn(GroundItem(KEY_IDS[index], 1, victim.tile, killer))
                markKeyOnGround(killer, index)
                killer.message("Your inventory is full, so your loot key has dropped on the ground.")
            } else {
                // Owner 2026-09-18: "a sound when u get a lootkey in ur inventory". ADAPTED: OSRS Deadman
                // has no published loot-key jingle; the 667 cache's own coin jingle (Sfx.COINS_JINGLE_1).
                killer.playSound(KEY_RECEIVED_SOUND)
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

    // ---- chest (the OSRS "Loot keys" screen, LootKeyChest) ----

    /** Removes up to [amount] of [itemId] from key slot [index]'s stored loot and books the claimed value. */
    fun takeFromSlot(
        player: Player,
        index: Int,
        itemId: Int,
        amount: Int,
    ): Int {
        val stored = slotItems(player, index).toMutableList()
        val at = stored.indexOfFirst { it.id == itemId }
        if (at < 0 || amount <= 0) return 0
        val moved = minOf(amount, stored[at].amount)
        addCounter(player, CLAIMED_VALUE, value(player.world.definitions, listOf(Item(itemId, moved))))
        if (moved == stored[at].amount) stored.removeAt(at) else stored[at] = Item(stored[at], stored[at].amount - moved)
        setSlot(player, index, stored)
        return moved
    }

    /** An emptied (or destroyed) key is used up: it leaves the inventory and counts as claimed. */
    fun consumeKey(
        player: Player,
        index: Int,
    ) {
        setSlot(player, index, emptyList())
        if (player.inventory.remove(KEY_IDS[index], 1).hasSucceeded()) {
            addCounter(player, CLAIMED_KEYS, 1)
        }
    }

    fun openChest(
        player: Player,
        index: Int,
    ) {
        LootKeyChest.open(player, index)
    }

    fun withdraw(
        player: Player,
        gridSlot: Int,
        amount: Int,
    ) {
        LootKeyChest.withdraw(player, gridSlot, amount)
    }

    fun closeChest(player: Player) {
        LootKeyChest.close(player)
    }
}
