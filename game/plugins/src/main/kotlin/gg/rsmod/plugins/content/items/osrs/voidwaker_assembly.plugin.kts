package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.grantOrRefund

/**
 * OSRS-IMPORT Voidwaker assembly - a genuine gap found by the 2026-09-17b audit round: the "voidwaker" batch
 * (tx-20260913-233456) imported the Voidwaker and its three untradeable pieces (hilt/blade/gem) but never wired a way
 * to actually assemble them, so the finished weapon was only obtainable by spawn.
 *
 * OSRS Wiki "Voidwaker" (raw wikitext, fetched 2026-09-17): "It is acquired by providing the Voidwaker blade, hilt,
 * and gem to Madam Sikaro in the Ferox Enclave Dungeon, who will reassemble the weapon for a fee of 500,000 coins."
 * "Attempting to combine the pieces on your own displays the message: 'You don't think you have the skills to
 * reassemble this weapon yourself.'" Madam Sikaro and the Ferox Enclave Dungeon do not exist in this 667 cache
 * (grep-confirmed absent from Npcs.kt); building a standalone NPC and dungeon region purely to gate one three-item
 * combine would be inventing unrelated content ("geen losstaande, niet-benodigde questcontent als afleiding"), the
 * same reasoning already used for the crystal singing bowl (placed at the Grand Exchange instead of a fake
 * Prifddinas object). ADAPTED here the same way: a direct player-performed combine for the 500,000 coin fee, without
 * the OSRS self-combine refusal (there is no NPC in this cache to perform the service instead).
 */
val ASSEMBLY_COST = 500_000

fun Player.hasVoidwakerPieces(): Boolean =
    inventory.contains(Items.VOIDWAKER_HILT) && inventory.contains(Items.VOIDWAKER_BLADE) && inventory.contains(Items.VOIDWAKER_GEM)

fun assembleVoidwaker(player: Player) {
    if (!player.hasVoidwakerPieces()) {
        player.message("You need the Voidwaker hilt, blade and gem to reassemble the Voidwaker.")
        return
    }
    if (player.inventory.getItemCount(Items.COINS_995) < ASSEMBLY_COST) {
        player.message("Reassembling the Voidwaker costs $ASSEMBLY_COST coins.")
        return
    }
    val consumed = listOf(Item(Items.VOIDWAKER_HILT, 1), Item(Items.VOIDWAKER_BLADE, 1), Item(Items.VOIDWAKER_GEM, 1), Item(Items.COINS_995, ASSEMBLY_COST))
    if (!consumed.all { player.inventory.remove(it.id, it.amount, assureFullRemoval = true).hasSucceeded() }) {
        consumed.forEach { player.inventory.add(it, assureFullInsertion = true) }
        return
    }
    if (!player.grantOrRefund(Item(Items.VOIDWAKER), consumed)) return
    player.message("You reassemble the Voidwaker.")
}

// Bound for every unordered pair of the three pieces so any click order finds and consumes the third piece too.
listOf(Items.VOIDWAKER_HILT to Items.VOIDWAKER_BLADE, Items.VOIDWAKER_HILT to Items.VOIDWAKER_GEM, Items.VOIDWAKER_BLADE to Items.VOIDWAKER_GEM).forEach { (a, b) ->
    on_item_on_item(item1 = a, item2 = b) { assembleVoidwaker(player) }
}
