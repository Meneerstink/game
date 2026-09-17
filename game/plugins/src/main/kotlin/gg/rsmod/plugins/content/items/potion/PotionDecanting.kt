package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * RCV-010 A3 (owner live 2026-09-13: potion on potion did nothing): decanting for every real four-dose
 * cache family. Drink effects remain in the deliberately narrower [Potion] table.
 *
 * Source: Novite 667 `Pots.mixPot` (pour, split into an empty vial, one-dose swap, full target refuses)
 * and its messages.
 */
object PotionDecanting {
    const val DOSES = 4
    const val POUR_MESSAGE = "You pour from one container into the other."
    const val SPLIT_MESSAGE = "You split the potion between the two vials."

    private val potionByItem: Map<Int, Potion> by lazy { Potion.values().associateBy { it.item } }
    // 667 names read "Super attack (4)"; the imported OSRS potions read "Super combat potion(4)" - no space. Requiring the space left every
    // OSRS potion without its Empty option and out of the decanting families (option census 2026-09-17c: 125 potions).
    private val doseSuffix = Regex("""^(.*?) ?\(([1-4])\)$""")

    /** Each family's item ids ordered 4, 3, 2, 1 doses. */
    val families: List<IntArray> by lazy {
        val replacements = Potion.values().map { it.replacement }.toSet()
        Potion.values()
            .filter { it.item !in replacements }
            .map { start ->
                generateSequence(start) { potionByItem[it.replacement] }.toList()
            }
            .filter { chain -> chain.size == DOSES && chain.last().replacement == Items.VIAL }
            .map { chain -> chain.map { it.item }.toIntArray() }
    }

    private val familyOf: Map<Int, IntArray> by lazy {
        families.flatMap { family -> family.map { it to family } }.toMap()
    }

    /**
     * Real cache-backed dose families, independent from the deliberately smaller [Potion] effect table.
     * The cache menu and exact `(1)`..`(4)` names are the same evidence used by Novite's 667 decanter.
     */
    fun cacheFamilies(definitions: DefinitionSet): List<IntArray> {
        val doseItems = definitions.getAll<ItemDef>(ItemDef::class.java).values
            .filterIsInstance<ItemDef>()
            .filter { def ->
                !def.noted &&
                    def.id !in RemovedPotions.itemIds &&
                    def.inventoryMenu.any { it.equals("Drink", ignoreCase = true) } &&
                    doseSuffix.matches(def.name)
            }

        return doseItems
            .groupBy { def -> doseSuffix.matchEntire(def.name)!!.groupValues[1].lowercase() }
            .mapNotNull { (_, variants) ->
                val byDose = variants.groupBy { def -> doseSuffix.matchEntire(def.name)!!.groupValues[2].toInt() }
                if ((1..DOSES).any { dose -> byDose[dose].isNullOrEmpty() }) return@mapNotNull null

                // Duplicate-name cache variants (for example minigame copies) are kept as parallel families
                // in stable id order, so decanting never silently changes one variant into another.
                val familyCount = (1..DOSES).minOf { dose -> byDose.getValue(dose).size }
                (0 until familyCount).map { variant ->
                    IntArray(DOSES) { index ->
                        val dose = DOSES - index
                        byDose.getValue(dose).sortedBy { it.id }[variant].id
                    }
                }
            }
            .flatten()
            .sortedBy { it.first() }
    }

    /** Every unnoted dose item whose real cache inventory menu exposes Empty. */
    fun emptyableDoseItems(definitions: DefinitionSet): IntArray =
        definitions.getAll<ItemDef>(ItemDef::class.java).values
            .filterIsInstance<ItemDef>()
            .filter { def ->
                !def.noted &&
                    def.id !in RemovedPotions.itemIds &&
                    doseSuffix.matches(def.name) &&
                    def.inventoryMenu.any { it.equals("Empty", ignoreCase = true) }
            }
            .map { it.id }
            .sorted()
            .toIntArray()

    fun doses(item: Int): Int {
        val family = familyOf[item] ?: return 0
        return DOSES - family.indexOf(item)
    }

    private fun idFor(family: IntArray, doses: Int): Int = family[DOSES - doses]

    /** Resolve the final empty container at the end of a drink replacement chain. */
    fun emptyContainer(item: Int): Int {
        var potion = potionByItem[item] ?: return -1
        val seen = mutableSetOf<Int>()
        while (seen.add(potion.item)) {
            val replacement = potion.replacement
            if (replacement == -1) return -1
            potion = potionByItem[replacement] ?: return replacement
        }
        return -1
    }

    /** Novite 667 `Decanting.emptyPotion`: replace the selected potion with its empty container. */
    fun empty(player: Player, slot: Int, forcedContainer: Int? = null): Boolean {
        val potion = player.inventory[slot] ?: return false
        val container = forcedContainer ?: emptyContainer(potion.id)
        if (container == -1) return false
        val name = player.world.definitions.get(ItemDef::class.java, potion.id).name.lowercase()
        player.inventory[slot] = Item(container)
        player.message("You empty the $name.")
        return true
    }

    /** Every unordered item pair that must be bound: same family (any doses) and potion + empty vial. */
    fun bindingPairs(sourceFamilies: List<IntArray> = families): List<Pair<Int, Int>> =
        sourceFamilies.flatMap { family ->
            val pairs = mutableListOf<Pair<Int, Int>>()
            for (i in family.indices) {
                for (j in i until family.size) pairs += family[i] to family[j]
                pairs += family[i] to Items.VIAL
            }
            pairs
        }

    /** [fromSlot] holds the item used, [toSlot] the item it was used on. */
    fun decant(player: Player, fromSlot: Int, toSlot: Int, sourceFamilies: List<IntArray> = families): Boolean {
        if (fromSlot == toSlot) return false
        val inventory = player.inventory
        val from = inventory[fromSlot] ?: return false
        val to = inventory[toSlot] ?: return false

        fun family(item: Int): IntArray? = sourceFamilies.firstOrNull { item in it }
        fun familyDoses(item: Int, itemFamily: IntArray): Int = DOSES - itemFamily.indexOf(item)

        if (from.id == Items.VIAL || to.id == Items.VIAL) {
            val potionSlot = if (from.id == Items.VIAL) toSlot else fromSlot
            val vialSlot = if (from.id == Items.VIAL) fromSlot else toSlot
            val potion = inventory[potionSlot] ?: return false
            val family = family(potion.id) ?: return false
            val doses = familyDoses(potion.id, family)
            if (doses == 1) {
                inventory.swap(fromSlot, toSlot)
                player.message(POUR_MESSAGE)
                return true
            }
            val vialDoses = doses / 2
            inventory[potionSlot] = Item(idFor(family, doses - vialDoses))
            inventory[vialSlot] = Item(idFor(family, vialDoses))
            player.message(SPLIT_MESSAGE)
            return true
        }

        val family = family(from.id) ?: return false
        if (to.id !in family) return false
        var toDoses = familyDoses(to.id, family)
        if (toDoses == DOSES) {
            player.message("Nothing interesting happens.")
            return false
        }
        toDoses += familyDoses(from.id, family)
        val remaining = if (toDoses > DOSES) toDoses - DOSES else 0
        toDoses -= remaining
        inventory[fromSlot] = Item(if (remaining > 0) idFor(family, remaining) else Items.VIAL)
        inventory[toSlot] = Item(idFor(family, toDoses))
        player.message(POUR_MESSAGE)
        return true
    }
}
