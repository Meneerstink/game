package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.plugins.content.objs.bank_locs.NotedFoodOnBank

/*
 * PROJECT48 SIGILS: the item options of the 130 sigil items (cache options: Attune / Unattune / Activate / Check / Inspect).
 * Attune and unattune only at a bank (a bank booth, chest or counter within 2 tiles), never while skulled.
 */

fun nearBank(player: Player): Boolean {
    for (dx in -2..2) {
        for (dz in -2..2) {
            val obj = world.getObject(player.tile.transform(dx, dz), ObjectType.INTERACTABLE) ?: continue
            val def = world.definitions.getNullable(ObjectDef::class.java, obj.id) ?: continue
            if (NotedFoodOnBank.isBank(def)) return true
        }
    }
    return false
}

fun sigilMessage(
    player: Player,
    result: Sigils.Result,
    attuning: Boolean,
) {
    when (result) {
        Sigils.Result.OK -> player.message(if (attuning) "The sigil hums as it attunes to you." else "The sigil's attunement fades.")
        Sigils.Result.NOT_AT_BANK -> player.message("You can only ${if (attuning) "attune" else "unattune"} a sigil at a bank.")
        Sigils.Result.SKULLED -> player.message("You cannot ${if (attuning) "attune" else "unattune"} a sigil while skulled.")
        Sigils.Result.FULL -> player.message("You can only have ${Sigils.MAX_ATTUNED} sigils attuned at a time.")
        Sigils.Result.EXCLUSIVE -> player.message("This sigil cannot be used in conjunction with the sigil you already have attuned.")
        Sigils.Result.ALREADY -> player.message("You already have this sigil attuned.")
        else -> player.message("Nothing interesting happens.")
    }
}

Sigil.values().forEach { sigil ->
    on_item_option(sigil.unattunedId, "attune") {
        val slot = player.getInteractingItemSlot()
        sigilMessage(player, Sigils.attune(player, slot, nearBank(player)), attuning = true)
    }
    on_item_option(sigil.attunedId, "unattune") {
        val slot = player.getInteractingItemSlot()
        sigilMessage(player, Sigils.unattune(player, slot, nearBank(player)), attuning = false)
    }
    on_item_option(sigil.attunedId, "check") {
        Sigils.overview(player).forEach { player.message(it) }
    }
    on_item_option(sigil.attunedId, "activate") {
        if (Sigils.activate(player, sigil)) SigilEffects.activated(player, sigil)
    }
    on_item_option(sigil.attunedId, "inspect") {
        player.message("${sigil.label} (tier ${sigil.tier}, ${sigil.kind.name.lowercase()}).")
    }
    on_item_option(sigil.unattunedId, "inspect") {
        player.message("${sigil.label} (tier ${sigil.tier}, ${sigil.kind.name.lowercase()}).")
    }
}

on_command("sigils") {
    Sigils.overview(player).forEach { player.message(it) }
}
