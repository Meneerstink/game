package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.plugins.content.objs.bank_locs.BankObjects
import gg.rsmod.plugins.content.objs.bank_locs.NotedFoodOnBank

/*
 * PROJECT48 SIGILS: the item options of the 130 sigil items (cache options: Attune / Unattune / Activate / Check / Inspect).
 * Attune and unattune only at a bank (a bank booth, chest or counter within 2 tiles), never while skulled.
 */

fun nearBank(player: Player): Boolean {
    for (dx in -2..2) {
        for (dz in -2..2) {
            val obj = world.getObject(player.tile.transform(dx, dz), ObjectType.INTERACTABLE) ?: continue
            if (obj.id in BankObjects.ALL) return true
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

/*
 * The cache items come from OSRS (build 2686) and differ per sigil: the un-attuned item has "Attune" or "Unlock", the attuned one has
 * "Unattune" (some also "Activate" / "Check") or ONLY "Destroy". Every option the item really has is hooked; an attuned sigil without
 * "Unattune" is un-attuned by using it on a bank (NotedFoodOnBank's any-item-on-bank hook calls [Sigils.useOnBank]).
 */
fun hasInvOption(
    id: Int,
    name: String,
): Boolean = world.definitions.get(ItemDef::class.java, id).inventoryMenu.any { it.equals(name, true) }

Sigil.values().forEach { sigil ->
    listOf("attune", "unlock").filter { hasInvOption(sigil.unattunedId, it) }.forEach { option ->
        on_item_option(sigil.unattunedId, option) {
            val slot = player.getInteractingItemSlot()
            sigilMessage(player, Sigils.attune(player, slot, nearBank(player)), attuning = true)
        }
    }
    if (hasInvOption(sigil.attunedId, "unattune")) {
        on_item_option(sigil.attunedId, "unattune") {
            val slot = player.getInteractingItemSlot()
            sigilMessage(player, Sigils.unattune(player, slot, nearBank(player)), attuning = false)
        }
    }
    if (hasInvOption(sigil.attunedId, "check")) {
        on_item_option(sigil.attunedId, "check") {
            Sigils.overview(player).forEach { player.message(it) }
        }
    }
    if (hasInvOption(sigil.attunedId, "activate")) {
        on_item_option(sigil.attunedId, "activate") {
            if (Sigils.activate(player, sigil)) SigilEffects.activated(player, sigil)
        }
    }
    listOf(sigil.attunedId, sigil.unattunedId).filter { hasInvOption(it, "inspect") }.forEach { id ->
        on_item_option(id, "inspect") {
            player.message("${sigil.label} (tier ${sigil.tier}, ${sigil.kind.name.lowercase()}).")
        }
    }
}

on_login { player.timers[SigilSkilling.LITHENESS_TIMER] = SigilSkilling.LITHENESS_TICKS }
on_timer(SigilSkilling.LITHENESS_TIMER) { SigilSkilling.lithenessTick(player) }

/*
 * S-K5 a: the starting sigil. A player who has not chosen one yet picks 1 of the 3 Tier 2 starting sigils a few ticks after login
 * (team test accounts and bots skip it so their scripts are not interrupted).
 */
on_login {
    if (player.attr[Sigils.START_CHOSEN] == true || player.username.lowercase().startsWith("teamtest")) return@on_login
    player.queue {
        wait(8)
        if (player.attr[Sigils.START_CHOSEN] == true) return@queue
        val labels = Sigils.START_CHOICES.map { it.label.removePrefix("Sigil of ").removePrefix("the ").replaceFirstChar { c -> c.uppercase() } }
        val choice = options(*labels.toTypedArray(), title = "Choose your starting sigil")
        if (choice in 1..labels.size) {
            val sigil = Sigils.START_CHOICES[choice - 1]
            if (player.inventory.add(sigil.unattunedId, 1).hasSucceeded() || player.bank.add(sigil.unattunedId, 1).hasSucceeded()) {
                player.attr[Sigils.START_CHOSEN] = true
                player.message("You receive the ${sigil.label}. Attune it at a bank (Attune).")
            }
        }
    }
}

on_command("sigils") {
    Sigils.overview(player).forEach { player.message(it) }
}
