package gg.rsmod.plugins.content.magic

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item

/**
 * Rune pouch / divine rune pouch actions (rules and sources in [RunePouch]).
 *
 * Open shows the pouch window ([RunePouchInterface]: Withdraw-1/5/All/X from its slots, Store-1/5/All/X from the backpack); a rune
 * used on the pouch offers the same quantities in a dialogue. ADAPTED_TO_667: OSRS bank load-outs / Configure are not ported. The
 * "Revert" warning follows the item page ("if the player has a rune in the 4th slot they will be warned all of the runes in that slot will
 * be lost"); the wording of every message is not sourced.
 */

/** Client-only inventory of the carried pouch's runes (client RspsRunePouch.INV). */
val RUNE_POUCH_CLIENT_INV = 30001

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
    gg.rsmod.plugins.content.mechanics.ItemMoveSounds.putIn(player) // N64: storing runes makes a sound
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

    // Owner 2026-10-04 024434: Open shows the pouch window (RunePouchInterface) - Withdraw from its slots, Store from the backpack.
    on_item_option(item = pouchId, option = "Open") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != pouchId) return@on_item_option
        RunePouchInterface.open(player, slot)
    }

    on_item_option(item = pouchId, option = "Empty") {
        val slot = player.getInteractingItemSlot()
        var pouch = player.inventory[slot]?.takeIf { it.id == pouchId } ?: return@on_item_option
        if (RunePouch.contents(pouch).isNotEmpty()) gg.rsmod.plugins.content.mechanics.ItemMoveSounds.takeOut(player) // N64
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
        if (!gg.rsmod.plugins.content.mechanics.assist.AssistSystem.meets(player, Skills.CRAFTING, RunePouch.DIVINE_CRAFTING_LEVEL)) {
            player.message("You need a Crafting level of ${RunePouch.DIVINE_CRAFTING_LEVEL} to do that.")
            return@on_item_on_item
        }
        val slot = RunePouch.pouchSlot(player.inventory).takeIf { it >= 0 && player.inventory[it]?.id == Items.RUNE_POUCH } ?: return@on_item_on_item
        // Owner 2026-09-18: "use thread on ... when creating the rune pouch should also give an interface": the item warning GUI
        // first, then the item dialogue with the finished pouch. OSRS Wiki "Divine rune pouch": the stitching takes 3 game ticks.
        player.queue {
            if (!confirmItemAction(
                    Items.DIVINE_RUNE_POUCH,
                    "Are you sure you want to stitch the thread of Elidinis into your rune pouch?",
                    "The thread is used up; reverting the pouch later returns the thread and the pouch.",
                )
            ) {
                return@queue
            }
            wait(3)
            val old = player.inventory[slot]?.takeIf { it.id == Items.RUNE_POUCH } ?: return@queue
            if (!player.inventory.contains(Items.THREAD_OF_ELIDINIS) || !player.inventory.contains(Items.NEEDLE)) return@queue
            player.inventory.remove(Items.THREAD_OF_ELIDINIS, 1)
            player.inventory[slot] = RunePouch.withContents(Item(Items.DIVINE_RUNE_POUCH), RunePouch.contents(old))
            itemMessageBox("You stitch the thread of Elidinis into your rune pouch.", item = Items.DIVINE_RUNE_POUCH)
        }
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

/*
 * Owner bug 2026-10-04 024552 ("runes in a (divine) rune pouch are not recognised by the spellbook"): the 667 spellbook greys a
 * spell from the cache's rune counter (script 19), which only reads inventory 93. The carried pouch's runes go to the client as
 * inventory 30001 just before inventory 93, and the client's rune counter adds them (client RspsRunePouch) - OSRS counts pouch
 * runes for a spell. Casting itself already pays from the pouch (RunePayment).
 */
gg.rsmod.game.model.entity.Player.inventoryFlushHook = { p ->
    val slot = RunePouch.pouchSlot(p.inventory)
    val runes = if (slot < 0) emptyList() else RunePouch.contents(p.inventory[slot]!!)
    val items = arrayOfNulls<Item>(4)
    runes.take(4).forEachIndexed { i, rune -> items[i] = rune }
    p.write(gg.rsmod.game.message.impl.UpdateInvFullMessage(containerKey = RUNE_POUCH_CLIENT_INV, items = items))
}

// The OSRS rune pouch window (RunePouchInterface, interface 1177): Withdraw from the Pouch box, Store from the Inventory box, the
// 1 / 5 / X / All quantity buttons and Close.
on_button(RunePouchInterface.WINDOW, RunePouchInterface.POUCH_GRID) {
    RunePouchInterface.withdraw(player, player.getInteractingSlot(), player.getInteractingOpcode())
}

on_button(RunePouchInterface.WINDOW, RunePouchInterface.INVENTORY_GRID) {
    RunePouchInterface.store(player, player.getInteractingSlot(), player.getInteractingOpcode())
}

RunePouchInterface.QUANTITIES.indices.forEach { index ->
    on_button(RunePouchInterface.WINDOW, RunePouchInterface.BUTTON_FIRST + index * RunePouchInterface.BUTTON_STRIDE) {
        RunePouchInterface.selectQuantity(player, index)
    }
}

on_button(RunePouchInterface.WINDOW, RunePouchInterface.CLOSE) {
    RunePouchInterface.close(player)
}

on_interface_close(RunePouchInterface.WINDOW) {
    RunePouchInterface.onClosed(player)
}
