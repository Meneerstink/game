package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS rune pouch and divine rune pouch (OSRS Wiki raw wikitext "Rune pouch", "Divine rune pouch", read 2026-09-17): "store 16,000 of
 * three types of runes"; the divine pouch "holds four rune types with each type supporting up to 16,000"; "Players can cast spells using
 * the runes stored in the pouch"; "Only runes can be stored"; "Players cannot own more than one rune pouch" (either kind); the divine
 * pouch is made by "combining a rune pouch with a Thread of Elidinis using a needle" at 75 Crafting (boostable, no experience); its
 * Revert option turns it back into a rune pouch. Death: "If players die a PvP death while carrying a rune pouch in any level Wilderness
 * ... they will lose the rune pouch"; "If players die a PvM death anywhere ... the rune pouch is always sent to a gravestone".
 *
 * Contents live on the pouch item itself ([ItemAttribute.RUNE_POUCH_ID_1] ..), so bank, death recovery, relog and restart carry them.
 */
object RunePouch {
    const val SLOT_CAPACITY = 16_000
    const val DIVINE_CRAFTING_LEVEL = 75

    val POUCHES = intArrayOf(Items.RUNE_POUCH, Items.DIVINE_RUNE_POUCH)

    private val ID_KEYS = listOf(ItemAttribute.RUNE_POUCH_ID_1, ItemAttribute.RUNE_POUCH_ID_2, ItemAttribute.RUNE_POUCH_ID_3, ItemAttribute.RUNE_POUCH_ID_4)
    private val AMOUNT_KEYS =
        listOf(ItemAttribute.RUNE_POUCH_AMOUNT_1, ItemAttribute.RUNE_POUCH_AMOUNT_2, ItemAttribute.RUNE_POUCH_AMOUNT_3, ItemAttribute.RUNE_POUCH_AMOUNT_4)

    /** Every rune of this server (standard, combination and imported OSRS runes); nothing else fits in a pouch. */
    val RUNES =
        setOf(
            Items.AIR_RUNE, Items.WATER_RUNE, Items.EARTH_RUNE, Items.FIRE_RUNE, Items.MIND_RUNE, Items.BODY_RUNE, Items.COSMIC_RUNE,
            Items.CHAOS_RUNE, Items.NATURE_RUNE, Items.LAW_RUNE, Items.DEATH_RUNE, Items.BLOOD_RUNE, Items.SOUL_RUNE, Items.ASTRAL_RUNE,
            Items.ARMADYL_RUNE, Items.MIST_RUNE, Items.DUST_RUNE, Items.MUD_RUNE, Items.SMOKE_RUNE, Items.STEAM_RUNE, Items.LAVA_RUNE,
            Items.WRATH_RUNE, Items.AETHER_RUNE,
        )

    fun isPouch(itemId: Int): Boolean = itemId == Items.RUNE_POUCH || itemId == Items.DIVINE_RUNE_POUCH

    fun slots(pouchId: Int): Int = if (pouchId == Items.DIVINE_RUNE_POUCH) 4 else 3

    /** Stored runes in slot order (empty slots omitted). */
    fun contents(pouch: Item): List<Item> =
        (0 until slots(pouch.id)).mapNotNull { slot ->
            val id = pouch.attr[ID_KEYS[slot]] ?: return@mapNotNull null
            val amount = pouch.attr[AMOUNT_KEYS[slot]] ?: 0
            if (amount > 0) Item(id, amount) else null
        }

    fun count(
        pouch: Item,
        runeId: Int,
    ): Int = contents(pouch).filter { it.id == runeId }.sumOf { it.amount }

    /** A copy of [pouch] holding [runes] (at most [slots] kinds, capped at 16,000 each). */
    fun withContents(
        pouch: Item,
        runes: List<Item>,
    ): Item {
        val result = Item(pouch.id, pouch.amount).copyAttr(pouch)
        ID_KEYS.forEach { result.attr.remove(it) }
        AMOUNT_KEYS.forEach { result.attr.remove(it) }
        runes.filter { it.amount > 0 }.take(slots(pouch.id)).forEachIndexed { slot, rune ->
            result.attr[ID_KEYS[slot]] = rune.id
            result.attr[AMOUNT_KEYS[slot]] = rune.amount.coerceAtMost(SLOT_CAPACITY)
        }
        return result
    }

    /** How many of [amount] [runeId] fit into [pouch]: an existing slot up to 16,000, otherwise a free slot. 0 for non-runes. */
    fun space(
        pouch: Item,
        runeId: Int,
        amount: Int,
    ): Int {
        if (runeId !in RUNES || amount <= 0) return 0
        val contents = contents(pouch)
        val existing = contents.firstOrNull { it.id == runeId }
        return when {
            existing != null -> minOf(amount, SLOT_CAPACITY - existing.amount).coerceAtLeast(0)
            contents.size < slots(pouch.id) -> minOf(amount, SLOT_CAPACITY)
            else -> 0
        }
    }

    fun deposit(
        pouch: Item,
        runeId: Int,
        amount: Int,
    ): Item {
        val added = space(pouch, runeId, amount)
        if (added <= 0) return pouch
        val contents = contents(pouch).toMutableList()
        val index = contents.indexOfFirst { it.id == runeId }
        if (index >= 0) contents[index] = Item(runeId, contents[index].amount + added) else contents += Item(runeId, added)
        return withContents(pouch, contents)
    }

    fun withdraw(
        pouch: Item,
        runeId: Int,
        amount: Int,
    ): Item {
        val contents = contents(pouch).map { if (it.id == runeId) Item(it.id, (it.amount - amount).coerceAtLeast(0)) else it }
        return withContents(pouch, contents)
    }

    /** The first pouch slot of [container] holding a pouch, or -1. */
    fun pouchSlot(container: ItemContainer): Int = (0 until container.capacity).firstOrNull { container[it]?.id?.let(::isPouch) == true } ?: -1

    /** Runes of [runeId] available to [player] from the carried pouch. */
    fun carried(
        player: Player,
        runeId: Int,
    ): Int {
        val slot = pouchSlot(player.inventory)
        return if (slot < 0) 0 else count(player.inventory[slot]!!, runeId)
    }

    /** Removes up to [amount] [runeId] from the carried pouch; returns how many were taken. */
    fun take(
        player: Player,
        runeId: Int,
        amount: Int,
    ): Int {
        val slot = pouchSlot(player.inventory)
        if (slot < 0 || amount <= 0) return 0
        val pouch = player.inventory[slot]!!
        val taken = minOf(amount, count(pouch, runeId))
        if (taken > 0) player.inventory[slot] = withdraw(pouch, runeId, taken)
        return taken
    }

    /** "Players cannot own more than one rune pouch": any pouch in the inventory, equipment or bank. */
    fun ownsPouch(player: Player): Boolean =
        listOf(player.inventory, player.equipment, player.bank).any { container -> POUCHES.any { container.contains(it) } }
}

/**
 * The shared rune payment of every spell ([MagicSpells.canCast] / [MagicSpells.removeRunes]).
 *
 * Order (OSRS Wiki "Combination rune": they "simultaneously count as one of each type of the runes required to make them. When the player
 * casts a spell with combination runes in their inventory, it will prioritise using those runes first, even while having sufficient
 * runes otherwise"; "Mist rune": "any spell requiring one water rune, one air rune, or both will spend only one mist rune"; Void 634
 * `SpellRunes.removeItems` implements the same model): 1. runes an equipped staff or tome supplies are free; 2. combination runes (mist,
 * dust, mud, smoke, steam, lava, aether) cover both of their elements at once, `max` of the two covered amounts spent; 3. the remaining
 * runes. Sources: inventory first, then the carried rune pouch (SOURCE_GAP: the inventory/pouch order is not stated; the total is what
 * matters for every requirement).
 */
object RunePayment {
    /** Combination runes and the two runes each counts as, in the Void 634 order. */
    val COMBINATIONS =
        listOf(
            Triple(Items.MIST_RUNE, Items.AIR_RUNE, Items.WATER_RUNE),
            Triple(Items.DUST_RUNE, Items.AIR_RUNE, Items.EARTH_RUNE),
            Triple(Items.MUD_RUNE, Items.WATER_RUNE, Items.EARTH_RUNE),
            Triple(Items.SMOKE_RUNE, Items.AIR_RUNE, Items.FIRE_RUNE),
            Triple(Items.STEAM_RUNE, Items.WATER_RUNE, Items.FIRE_RUNE),
            Triple(Items.LAVA_RUNE, Items.EARTH_RUNE, Items.FIRE_RUNE),
            Triple(Items.AETHER_RUNE, Items.COSMIC_RUNE, Items.SOUL_RUNE),
        )

    /**
     * The runes to spend for [required] (rune id -> amount), given [available] (rune id -> amount carried) and [free] (rune ids an
     * equipped staff/tome supplies); `null` when the player cannot pay.
     */
    fun plan(
        required: Map<Int, Int>,
        available: (Int) -> Int,
        free: (Int) -> Boolean,
    ): Map<Int, Int>? {
        val needed = required.filter { it.value > 0 && !free(it.key) }.toMutableMap()
        val spend = mutableMapOf<Int, Int>()
        for ((combo, first, second) in COMBINATIONS) {
            if (!needed.containsKey(first) && !needed.containsKey(second)) continue
            val count = available(combo)
            if (count <= 0) continue
            val used = maxOf(needed.take(first, count), needed.take(second, count))
            if (used > 0) spend[combo] = used
        }
        for ((rune, amount) in needed) {
            if (available(rune) < amount) return null
            spend[rune] = (spend[rune] ?: 0) + amount
        }
        return spend
    }

    private fun MutableMap<Int, Int>.take(
        key: Int,
        count: Int,
    ): Int {
        val need = this[key] ?: return 0
        val covered = minOf(need, count)
        if (need - covered <= 0) remove(key) else this[key] = need - covered
        return covered
    }
}
