package gg.rsmod.plugins.content.magic

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item

/**
 * Rune pouch / divine rune pouch actions (rules and sources in [RunePouch]).
 *
 * ADAPTED_TO_667: the OSRS pouch interface (and its bank load-outs / Configure) does not exist in the 667 client, so Open lists the
 * stored runes in a dialogue with the OSRS quantities 1 / 5 / X / All, and a rune used on the pouch offers the same quantities. The
 * "Revert" warning follows the item page ("if the player has a rune in the 4th slot they will be warned all of the runes in that slot will
 * be lost"); the wording of every message is not sourced.
 */

fun runeName(runeId: Int): String = world.definitions.get(ItemDef::class.java, runeId).name

fun usedPouchSlot(player: Player): Int? {
    val first = player.attr[INTERACTING_ITEM_SLOT] ?: return null
    val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return null
    return listOf(first, second).firstOrNull { player.inventory[it]?.id?.let(RunePouch::isPouch) == true }
}

suspend fun gg.rsmod.game.model.queue.QueueTask.chooseAmount(
    verb: String,
    max: Int,
): Int {
    if (max <= 0) return 0
    return when (options("$verb 1", "$verb 5", "$verb X", "$verb All", title = "How many?")) {
        1 -> 1
        2 -> 5
        3 -> inputInt("Enter amount:")
        4 -> max
        else -> 0
    }.coerceIn(0, max)
}

fun deposit(
    player: Player,
    pouchSlot: Int,
    runeId: Int,
    amount: Int,
) {
    val pouch = player.inventory[pouchSlot]?.takeIf { RunePouch.isPouch(it.id) } ?: return
    val added = RunePouch.space(pouch, runeId, minOf(amount, player.inventory.getItemCount(runeId)))
    if (added <= 0) {
        player.message(if (runeId in RunePouch.RUNES) "Your pouch has no space for that rune." else "You can only store runes in the pouch.")
        return
    }
    player.inventory.remove(runeId, added)
    player.inventory[pouchSlot] = RunePouch.deposit(pouch, runeId, added)
}

RunePouch.POUCHES.forEach { pouchId ->
    RunePouch.RUNES.forEach { runeId ->
        on_item_on_item(item1 = runeId, item2 = pouchId) {
            val slot = usedPouchSlot(player) ?: return@on_item_on_item
            player.queue {
                val amount = chooseAmount("Store", player.inventory.getItemCount(runeId))
                deposit(player, slot, runeId, amount)
            }
        }
    }

    on_item_option(item = pouchId, option = "Open") {
        val slot = player.getInteractingItemSlot()
        val pouch = player.inventory[slot]?.takeIf { it.id == pouchId } ?: return@on_item_option
        val contents = RunePouch.contents(pouch)
        if (contents.isEmpty()) {
            player.message("Your pouch is empty. Use runes on it to store them.")
            return@on_item_option
        }
        // Owner live report 2026-09-17c ("open on rune pouches doesnt show the runes its holding"): the contents are always listed in
        // the chat box, and the dialogue gets a closing line - a 667 options dialogue with a single line (one stored rune type) showed nothing.
        player.message("Your pouch holds: " + contents.joinToString(", ") { "${it.amount} x ${runeName(it.id)}" } + ".")
        player.queue {
            val labels = contents.map { "${runeName(it.id)} (${it.amount})" } + "Close"
            val choice = options(*labels.toTypedArray(), title = "Withdraw which rune?")
            val rune = contents.getOrNull(choice - 1) ?: return@queue
            val current = player.inventory[slot]?.takeIf { it.id == pouchId } ?: return@queue
            val stored = RunePouch.count(current, rune.id)
            val amount = chooseAmount("Withdraw", stored)
            if (amount <= 0) return@queue
            val latest = player.inventory[slot]?.takeIf { it.id == pouchId } ?: return@queue
            val taken = minOf(amount, RunePouch.count(latest, rune.id))
            val added = player.inventory.add(rune.id, taken).completed
            if (added <= 0) {
                player.message("You don't have enough inventory space.")
                return@queue
            }
            player.inventory[slot] = RunePouch.withdraw(latest, rune.id, added)
        }
    }

    on_item_option(item = pouchId, option = "Empty") {
        val slot = player.getInteractingItemSlot()
        var pouch = player.inventory[slot]?.takeIf { it.id == pouchId } ?: return@on_item_option
        for (rune in RunePouch.contents(pouch)) {
            val added = player.inventory.add(rune.id, rune.amount).completed
            if (added > 0) pouch = RunePouch.withdraw(pouch, rune.id, added)
            if (added < rune.amount) {
                player.message("You don't have enough inventory space to empty the pouch.")
                break
            }
        }
        player.inventory[slot] = pouch
    }
}

// Divine rune pouch: thread of Elidinis + rune pouch with a needle, 75 Crafting (boostable), no experience; runes stay in the pouch.
listOf(Items.THREAD_OF_ELIDINIS to Items.RUNE_POUCH, Items.NEEDLE to Items.RUNE_POUCH).forEach { (used, pouch) ->
    on_item_on_item(item1 = used, item2 = pouch) {
        if (!player.inventory.contains(Items.NEEDLE) || !player.inventory.contains(Items.THREAD_OF_ELIDINIS)) {
            player.message("You need a needle and a thread of Elidinis to upgrade the rune pouch.")
            return@on_item_on_item
        }
        if (player.skills.getCurrentLevel(Skills.CRAFTING) < RunePouch.DIVINE_CRAFTING_LEVEL) {
            player.message("You need a Crafting level of ${RunePouch.DIVINE_CRAFTING_LEVEL} to do that.")
            return@on_item_on_item
        }
        val slot = RunePouch.pouchSlot(player.inventory).takeIf { it >= 0 && player.inventory[it]?.id == Items.RUNE_POUCH } ?: return@on_item_on_item
        val old = player.inventory[slot]!!
        player.inventory.remove(Items.THREAD_OF_ELIDINIS, 1)
        player.inventory[slot] = RunePouch.withContents(Item(Items.DIVINE_RUNE_POUCH), RunePouch.contents(old))
        player.message("You stitch the thread of Elidinis into your rune pouch.")
    }
}

// Revert: "returning the thread and pouch"; runes in the 4th slot are lost (warned first).
on_item_option(item = Items.DIVINE_RUNE_POUCH, option = "Revert") {
    val slot = player.getInteractingItemSlot()
    if (player.inventory[slot]?.id != Items.DIVINE_RUNE_POUCH) return@on_item_option
    player.queue {
        val pouch = player.inventory[slot]?.takeIf { it.id == Items.DIVINE_RUNE_POUCH } ?: return@queue
        val contents = RunePouch.contents(pouch)
        if (contents.size >= 4) {
            val lost = contents[3]
            if (options("Yes", "No", title = "All ${lost.amount} ${runeName(lost.id)}s in the fourth slot will be lost. Revert?") != 1) return@queue
        }
        if (player.inventory.freeSlotCount < 1) {
            player.message("You need a free inventory space to revert the pouch.")
            return@queue
        }
        val current = player.inventory[slot]?.takeIf { it.id == Items.DIVINE_RUNE_POUCH } ?: return@queue
        player.inventory[slot] = RunePouch.withContents(Item(Items.RUNE_POUCH), RunePouch.contents(current).take(3))
        player.inventory.add(Items.THREAD_OF_ELIDINIS, 1)
    }
}
