package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.api.ext.message
import kotlin.random.Random

/** Bag mechanics live independently of interface778, so the UI card can operate actual slots. */
class ToaSupplyBag(items: List<Int>) {
    private val slots = items.map<Int, Int?> { it }.toMutableList()
    fun contents(): List<Int?> = slots.toList()
    val empty: Boolean get() = slots.all { it == null }
    fun withdraw(slot: Int, accept: (id: Int, last: Boolean) -> Boolean): Boolean {
        val item = slots.getOrNull(slot) ?: return false
        if (!accept(item, slots.count { it != null } == 1)) return false
        slots[slot] = null
        return true
    }
    fun first(): Int? = slots.indices.firstOrNull { slots[it] != null }
    fun replace(slot: Int, id: Int?) { require(slot in slots.indices); slots[slot] = id }
    fun put(id: Int): Boolean {
        val slot = slots.indexOfFirst { it == null }
        if (slot < 0) return false
        slots[slot] = id
        return true
    }
}

/** Sources: Helpful_Spirit + Supplies_(Tombs_of_Amascut) pinned wiki snapshots, 21-supplies-dood.tsv. */
class ToaSupplies(private val raid: ToaRaid) {
    private var sequence = 0
    private val bags = hashMapOf<Pair<PlayerUID, Int>, ToaSupplyBag>()
    private val rolled = hashMapOf<Pair<PlayerUID, Int>, Map<Choice, List<Int>>>()

    private fun offers(uid: PlayerUID): Map<Choice, List<Int>> = rolled.getOrPut(uid to raid.checkpoints.stage) {
        Choice.values().associateWith { bundle(it, raid.settings, Random.Default) }
    }

    fun offers(player: Player): Map<Choice, List<Item>> {
        if (!raid.contains(player) || !raid.checkpoints.offered(player.uid)) return emptyMap()
        return offers(player.uid).mapValues { (_, ids) -> ids.mapNotNull { source -> ToaIds.item(source)?.let(::Item) } }
    }

    fun claim(player: Player, choice: Choice): Boolean {
        if (!raid.contains(player) || player.isDead() || ToaMap.source(raid.map, player.tile)?.regionId != 14160) return false
        return raid.checkpoints.claim(player.uid) deliver@ {
            val bagId = ToaIds.item(BAG_SOURCE) ?: return@deliver false
            val sourceItems = offers(player.uid).getValue(choice)
            val items = sourceItems.map { ToaIds.item(it) ?: return@deliver false }
            val token = sequence + 1
            val item = Item(bagId).putAttr(ItemAttribute.TOA_SUPPLY_BAG_TOKEN, token)
            if (!player.inventory.addPreservingAttr(item).hasSucceeded()) return@deliver false
            sequence = token
            bags[player.uid to token] = ToaSupplyBag(items)
            true
        }
    }

    fun bag(player: Player, item: Item): ToaSupplyBag? = item.getAttr(ItemAttribute.TOA_SUPPLY_BAG_TOKEN)
        ?.let { bags[player.uid to it] }

    fun withdraw(player: Player, inventorySlot: Int, supplySlot: Int? = null, all: Boolean = false): Boolean {
        if (!raid.contains(player) || player.isDead()) return false
        val item = player.inventory[inventorySlot] ?: return false
        val token = item.getAttr(ItemAttribute.TOA_SUPPLY_BAG_TOKEN) ?: return false
        val bag = bag(player, item) ?: return false
        var moved = false
        do {
            val slot = supplySlot ?: bag.first() ?: break
            val success = bag.withdraw(slot) { id, last ->
                if (last) {
                    // The final supply can replace the bag itself even when all 28 inventory slots are occupied.
                    player.inventory[inventorySlot] = Item(id)
                    true
                } else player.inventory.add(id).hasSucceeded()
            }
            if (!success) break
            moved = true
        } while (all && !bag.empty)
        if (bag.empty) {
            bags.remove(player.uid to token)
            player.message("The bag disintegrates.") // Supplies wiki exact game message.
        }
        return moved
    }

    /** Dose-conserving refill of only partially used supplies already carried, never a new inventory item. */
    fun resupply(player: Player, bagSlot: Int) {
        if (!raid.contains(player) || player.isDead()) return
        val bagItem = player.inventory[bagSlot] ?: return
        val bag = bag(player, bagItem) ?: return
        for (slot in player.inventory.rawItems.indices) {
            val carried = player.inventory[slot] ?: continue
            val source = ToaIds.itemSource(carried.id) ?: continue
            val family = FAMILIES.firstOrNull { source in it.full..(it.full + (it.doses - 1) * 2) && (source - it.full) % 2 == 0 } ?: continue
            var doses = family.doses - (source - family.full) / 2
            if (doses == family.doses) continue
            for ((stockSlot, local) in bag.contents().withIndex()) {
                val stockSource = local?.let(ToaIds::itemSource) ?: continue
                if (stockSource !in family.full..(family.full + (family.doses - 1) * 2) || (stockSource - family.full) % 2 != 0) continue
                val available = family.doses - (stockSource - family.full) / 2
                val used = minOf(family.doses - doses, available)
                if (used == 0) break
                val newCarried = ToaIds.item(family.full + (family.doses - doses - used) * 2) ?: continue
                val remaining = available - used
                val newStock = if (remaining == 0) null else ToaIds.item(family.full + (family.doses - remaining) * 2) ?: continue
                player.inventory[slot] = Item(newCarried)
                bag.replace(stockSlot, newStock)
                doses += used
            }
        }
        if (bag.empty) destroy(player, bagSlot, disintegrated = true)
    }

    fun destroy(player: Player, bagSlot: Int, disintegrated: Boolean = false) {
        val item = player.inventory[bagSlot] ?: return
        val token = item.getAttr(ItemAttribute.TOA_SUPPLY_BAG_TOKEN) ?: return
        if (bags.remove(player.uid to token) == null) return
        player.inventory[bagSlot] = null
        if (disintegrated) player.message("The bag disintegrates.")
    }

    enum class Choice { LIFE, CHAOS, POWER }

    companion object {
        // RuneLite gameval ItemID.TOA_MIDRAIDLOOT_BAG and TOA_SUPPLY_*; mapping to LOCAL IDs happens at claim time.
        const val BAG_SOURCE = 27314
        private const val NECTAR = 27315
        private const val SILK = 27323
        private const val TEARS = 27327
        private const val SCARAB = 27335
        private const val ADRENALINE = 27339
        private const val SALTS = 27343
        private const val AMBROSIA = 27347
        private data class Family(val full: Int, val doses: Int)
        private val FAMILIES = listOf(Family(NECTAR, 4), Family(TEARS, 4), Family(SILK, 2), Family(SCARAB, 2),
            Family(ADRENALINE, 2), Family(SALTS, 2), Family(AMBROSIA, 2))

        fun bundle(choice: Choice, settings: ToaRaidSettings, random: Random): List<Int> {
            val amounts = when (choice) {
                Choice.LIFE -> linkedMapOf(NECTAR to 5, TEARS to 5, SILK to random.nextInt(1, 4),
                    SCARAB to random.nextInt(1, 4), AMBROSIA to random.nextInt(1, 3))
                Choice.POWER -> linkedMapOf(SALTS to 2, ADRENALINE to 1)
                // ONZEKER T28: published ranges, unpublished distribution. Uniform inside the Helpful Spirit ranges;
                // choose its adrenaline0..1 source over the conflicting Strategies0..2, pending owner/source review.
                Choice.CHAOS -> linkedMapOf(NECTAR to random.nextInt(1, 9), TEARS to random.nextInt(0, 7),
                    SALTS to random.nextInt(0, 3), AMBROSIA to random.nextInt(0, 2), ADRENALINE to random.nextInt(0, 2))
            }
            // ONZEKER T28 also applies to Life's random range distribution; guaranteed quantities are source-exact.
            if (choice == Choice.LIFE && "On a Diet" in settings.invocations) {
                amounts.remove(SILK)
                if (settings.invocations.none { it in REDUCTIONS }) {
                    amounts[SCARAB] = amounts.getValue(SCARAB) + 2
                    amounts[AMBROSIA] = amounts.getValue(AMBROSIA) + 1
                }
            }
            val percent = REDUCTIONS.entries.firstOrNull { it.key in settings.invocations }?.value ?: 100
            return amounts.flatMap { (id, amount) ->
                // ONZEKER T28: floor reduction; wiki guarantees at least one of each guaranteed item type.
                val guaranteed = choice != Choice.CHAOS || id == NECTAR
                List(maxOf(if (guaranteed && amount > 0) 1 else 0, amount * percent / 100)) { id }
            }
        }

        private val REDUCTIONS = mapOf("Need Some Help?" to 66, "Need Less Help?" to 33, "No Help Needed" to 10)

        fun cleanup(player: Player) {
            ToaConsumables.clear(player)
            // Raid-exclusive supplies cannot be exported by teleport/logout or the deposit pot.
            // ONZEKER T28: exact exit cleanup order needs OSRS comparison; remove the same imported family in all containers.
            val ids = (BAG_SOURCE..27351).mapNotNull(ToaIds::item).toSet()
            listOf(player.inventory, player.bank).forEach { container ->
                container.rawItems.forEachIndexed { slot, item -> if (item?.id in ids) container[slot] = null }
            }
        }
    }
}
