package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.skills.farming.data.Seed
import gg.rsmod.plugins.content.skills.herblore.HerbData
import gg.rsmod.game.model.container.ItemTransaction

/**
 * PROJECT48 DMM-ITEMS I-A07 / I-A08: Herb sack and Seed box (OSRS Wiki 'Herb sack' + 'Seed box', read 21-11).
 * - Herb sack (closed 25472 / open 25473): 30 of each grimy herb. Fill takes the grimy herbs from the inventory, Empty gives them back
 *   starting with the lowest-level herb, Check lists them ("You look in your herb sack and see:" / "The herb sack is empty."). Open: every grimy
 *   herb that is picked up, dropped by a monster or harvested goes in by itself while there is room. Cleaned or noted herbs do not go in.
 * - Seed box (closed 25474 / open 25475): up to six different seed types, any amount. Same options; an open box takes picked up, dropped and
 *   harvested seeds. Planting straight from the box and the reorder interface are NOT built (the 'Check' option lists the seeds).
 * - The contents are kept on the PLAYER (persistent attribute), not on the item: "herbs stay in the sack upon death" (wiki), and a sack that is
 *   destroyed or replaced takes its contents with it (Destroy clears them: the wiki says the herbs are lost).
 * SOURCE_GAP (ADAPTED): the wiki does not give the 'fill' failure messages or the seed-box Check text; the messages below are our own.
 */
object Sacks {
    const val HERB_SACK = 25472
    const val HERB_SACK_OPEN = 25473
    const val SEED_BOX = 25474
    const val SEED_BOX_OPEN = 25475
    const val HERB_LIMIT = 30
    const val SEED_TYPES = 6

    val HERB_CONTENTS = AttributeKey<String>(persistenceKey = "p48_herb_sack")
    val SEED_CONTENTS = AttributeKey<String>(persistenceKey = "p48_seed_box")

    /** The grimy herbs in level order (the sack empties from the lowest level). */
    val HERBS: List<Int> by lazy { HerbData.values().sortedBy { it.levelRequirement }.map { it.grimy }.distinct() }
    val SEEDS: Set<Int> by lazy { Seed.values().map { it.seedId }.toSet() }

    private fun read(player: Player, key: AttributeKey<String>): MutableMap<Int, Int> {
        val map = LinkedHashMap<Int, Int>()
        player.attr[key]?.split(',')?.forEach {
            val (id, n) = it.split(':').let { p -> (p.getOrNull(0)?.toIntOrNull() ?: return@forEach) to (p.getOrNull(1)?.toIntOrNull() ?: return@forEach) }
            if (n > 0) map[id] = n
        }
        return map
    }

    private fun write(player: Player, key: AttributeKey<String>, map: Map<Int, Int>) {
        val text = map.filter { it.value > 0 }.entries.joinToString(",") { "${it.key}:${it.value}" }
        if (text.isEmpty()) player.attr.remove(key) else player.attr[key] = text
    }

    fun herbs(player: Player): Map<Int, Int> = read(player, HERB_CONTENTS)

    fun seeds(player: Player): Map<Int, Int> = read(player, SEED_CONTENTS)

    private fun name(player: Player, id: Int): String = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name

    // ---------------------------------------------------------------- herb sack

    /** How many of [item] the herb sack can still take (0 for a non-herb). */
    private fun herbRoom(player: Player, id: Int): Int = if (id !in HERBS) 0 else HERB_LIMIT - (herbs(player)[id] ?: 0)

    /** Does [id] fit in the seed box (a seed type it already holds, or a free one of the six)? */
    private fun seedAccepted(player: Player, id: Int): Boolean = id in SEEDS && (seeds(player).containsKey(id) || seeds(player).size < SEED_TYPES)

    private fun has(player: Player, ids: IntArray): Boolean = player.inventory.any { it != null && it.id in ids }

    fun hasOpenHerbSack(player: Player): Boolean = has(player, intArrayOf(HERB_SACK_OPEN))

    fun hasOpenSeedBox(player: Player): Boolean = has(player, intArrayOf(SEED_BOX_OPEN))

    fun fillHerbs(player: Player) {
        val map = read(player, HERB_CONTENTS)
        var moved = 0
        for (id in HERBS) {
            val room = HERB_LIMIT - (map[id] ?: 0)
            val carried = player.inventory.getItemCount(id)
            val n = minOf(room, carried)
            if (n <= 0 || !player.inventory.remove(id, n).hasSucceeded()) continue
            map[id] = (map[id] ?: 0) + n
            moved += n
        }
        write(player, HERB_CONTENTS, map)
        player.message(if (moved > 0) "You put $moved grimy herbs in your herb sack." else "You have no grimy herbs the sack can take.")
    }

    fun emptyHerbs(player: Player) {
        val map = read(player, HERB_CONTENTS)
        if (map.isEmpty()) return player.message("The herb sack is empty.")
        var moved = 0
        for (id in HERBS) {
            val have = map[id] ?: continue
            val tx = player.inventory.add(id, have, assureFullInsertion = false)
            val taken = tx.completed
            if (taken > 0) {
                moved += taken
                map[id] = have - taken
            }
            if (taken < have) break
        }
        write(player, HERB_CONTENTS, map)
        player.message(if (moved > 0) "You take $moved herbs out of your herb sack." else "You have no room in your inventory.")
    }

    fun checkHerbs(player: Player) {
        val map = read(player, HERB_CONTENTS)
        if (map.isEmpty()) return player.message("The herb sack is empty.")
        player.message("You look in your herb sack and see:")
        HERBS.forEach { id -> map[id]?.let { player.message("${name(player, id)}: $it") } }
    }

    // ---------------------------------------------------------------- seed box

    fun fillSeeds(player: Player) {
        val map = read(player, SEED_CONTENTS)
        var moved = 0
        for (slot in 0 until player.inventory.capacity) {
            val item = player.inventory[slot] ?: continue
            if (item.id !in SEEDS) continue
            if (!map.containsKey(item.id) && map.size >= SEED_TYPES) continue
            val n = player.inventory.getItemCount(item.id)
            if (n <= 0 || !player.inventory.remove(item.id, n).hasSucceeded()) continue
            map[item.id] = (map[item.id] ?: 0) + n
            moved += n
        }
        write(player, SEED_CONTENTS, map)
        player.message(if (moved > 0) "You put $moved seeds in your seed box." else "You have no seeds the box can take.")
    }

    /**
     * OSRS Wiki "Seed box" (fetched 2026-11-21): "Using the box on a patch will automatically attempt to plant the first seed suitable for
     * that patch." Takes the seeds for ONE planting out of the box into the inventory and returns that seed, or null (with a message)
     * when the box holds no seed that fits [patch] at the player's Farming level or the inventory has no room.
     */
    fun takeSeedForPatch(player: Player, patch: gg.rsmod.plugins.content.skills.farming.data.Patch): Seed? {
        val map = read(player, SEED_CONTENTS)
        val farming = player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.FARMING)
        val seed =
            map.entries.firstNotNullOfOrNull { (id, have) ->
                Seed.fromId(id)?.takeIf { it.seedType in patch.seedTypes && it.plant.level <= farming && have >= it.amountToPlant() }
            }
        if (seed == null) {
            player.message("Your seed box has no seeds that you can plant in this patch.")
            return null
        }
        val tool = seed.seedType.plant.plantingTool
        if (!player.inventory.contains(tool.id)) {
            player.message(tool.messageWhenMissing)
            return null
        }
        val n = seed.amountToPlant()
        if (!player.inventory.add(seed.seedId, n).hasSucceeded()) {
            player.message("You have no room in your inventory.")
            return null
        }
        map[seed.seedId] = (map[seed.seedId] ?: n) - n
        write(player, SEED_CONTENTS, map)
        return seed
    }

    fun emptySeeds(player: Player) {
        val map = read(player, SEED_CONTENTS)
        if (map.isEmpty()) return player.message("The seed box is empty.")
        var moved = 0
        for ((id, have) in map.toList()) {
            val tx = player.inventory.add(id, have, assureFullInsertion = false)
            val taken = tx.completed
            if (taken > 0) {
                moved += taken
                map[id] = have - taken
            }
            if (taken < have) break
        }
        write(player, SEED_CONTENTS, map)
        player.message(if (moved > 0) "You take $moved seeds out of your seed box." else "You have no room in your inventory.")
    }

    fun checkSeeds(player: Player) {
        val map = read(player, SEED_CONTENTS)
        if (map.isEmpty()) return player.message("The seed box is empty.")
        player.message("You look in your seed box and see:")
        map.forEach { (id, n) -> player.message("${name(player, id)}: $n") }
    }

    // ---------------------------------------------------------------- open / close / destroy

    /** Swaps the sack or box in inventory slot [slot] for its open or closed form. */
    fun toggle(player: Player, slot: Int, open: Boolean) {
        val item = player.inventory[slot] ?: return
        val to =
            when (item.id) {
                HERB_SACK -> if (open) HERB_SACK_OPEN else return
                HERB_SACK_OPEN -> if (!open) HERB_SACK else return
                SEED_BOX -> if (open) SEED_BOX_OPEN else return
                SEED_BOX_OPEN -> if (!open) SEED_BOX else return
                else -> return
            }
        player.inventory[slot] = Item(to, item.amount).copyAttr(item)
        val what = if (to == HERB_SACK || to == HERB_SACK_OPEN) "herb sack" else "seed box"
        player.message(if (open) "You open your $what. It will collect what you pick up." else "You close your $what.")
    }

    /** The sack or box was destroyed: what was inside is gone (wiki). */
    fun destroyed(player: Player, itemId: Int) {
        if (itemId == HERB_SACK || itemId == HERB_SACK_OPEN) player.attr.remove(HERB_CONTENTS) else player.attr.remove(SEED_CONTENTS)
    }

    // ---------------------------------------------------------------- automatic storing

    /**
     * Stores up to [amount] of [id] in an open sack / box the player carries and returns how many went in (0 when nothing is open, the item is
     * not a grimy herb / seed, or there is no room). Cleaned or noted herbs never match the grimy ids.
     */
    fun autoStore(player: Player, id: Int, amount: Int): Int {
        if (amount <= 0) return 0
        if (id in HERBS && hasOpenHerbSack(player)) {
            val map = read(player, HERB_CONTENTS)
            val n = minOf(amount, HERB_LIMIT - (map[id] ?: 0))
            if (n <= 0) return 0
            map[id] = (map[id] ?: 0) + n
            write(player, HERB_CONTENTS, map)
            player.filterableMessage("Your herb sack collects the ${name(player, id).lowercase()}.")
            return n
        }
        if (id in SEEDS && hasOpenSeedBox(player) && seedAccepted(player, id)) {
            val map = read(player, SEED_CONTENTS)
            map[id] = (map[id] ?: 0) + amount
            write(player, SEED_CONTENTS, map)
            player.filterableMessage("Your seed box collects the ${name(player, id).lowercase()}.")
            return amount
        }
        return 0
    }

    /** A pick-up: the item has just been added to the inventory; moves what an open sack / box takes back out of it. */
    fun afterPickup(player: Player, id: Int, amount: Int) {
        if (amount <= 0 || (id !in HERBS && id !in SEEDS)) return
        val room = if (id in HERBS) herbRoom(player, id) else amount
        val n = minOf(amount, room, player.inventory.getItemCount(id))
        if (n <= 0) return
        if (!(id in HERBS && hasOpenHerbSack(player)) && !(id in SEEDS && hasOpenSeedBox(player) && seedAccepted(player, id))) return
        if (player.inventory.remove(id, n).hasSucceeded()) autoStore(player, id, n)
    }

    /** Harvested / gathered items: through the sack first, the rest to the inventory. */
    fun add(player: Player, id: Int, amount: Int): ItemTransaction {
        val stored = autoStore(player, id, amount)
        if (stored >= amount) return ItemTransaction(amount, amount, emptyList())
        return player.inventory.add(id, amount - stored)
    }
}
