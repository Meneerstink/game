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

// G14: a dropped sigil stays visible to the one who dropped it only (wiki: not tradeable, GE only) - no floor trades.
gg.rsmod.game.model.entity.GroundItem.neverPublic = { Sigils.isSigilItem(it) }

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
        Sigils.Result.OK -> player.message(if (attuning) "You feel a surge of power as you attune to the Sigil." else "You unattune the Sigil. Its powers leave you for now.")
        Sigils.Result.NOT_AT_BANK -> player.message("You can only ${if (attuning) "attune" else "unattune"} a sigil at a bank.")
        Sigils.Result.SKULLED -> player.message("You cannot ${if (attuning) "attune" else "unattune"} a sigil while skulled.")
        Sigils.Result.FULL -> player.message("You already have attuned to ${Sigils.MAX_ATTUNED} Sigils. You must unattune one before attuning to more.")
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
            // Last Recall spends its cooldown only when its teleport really starts (SigilEffects.activateLastRecall).
            if (sigil == Sigil.LAST_RECALL) {
                SigilEffects.activateLastRecall(player)
                return@on_item_option
            }
            if (Sigils.activate(player, sigil)) {
                SigilFx.activated(player, sigil)
                SigilEffects.activated(player, sigil)
            }
        }
    }
    // Destroy on an ATTUNED sigil (wiki infobox: "You must unattune the Sigil before you can destroy it."). The inventory sends Drop /
    // Destroy as option 10 (IfButton1Handler), so it is bound by number; bound here, the generic Destroy dialogue (destroy_item) skips it.
    // Without this the item was destroyed while the sigil stayed attuned: its effect for ever and a slot that never came free.
    on_item_option(sigil.attunedId, 10) { player.message(Sigils.DESTROY_ATTUNED_MESSAGE) }
    listOf(sigil.attunedId, sigil.unattunedId).filter { hasInvOption(it, "inspect") }.forEach { id ->
        on_item_option(id, "inspect") {
            player.message("${sigil.label} (tier ${sigil.tier}, ${sigil.kind.name.lowercase()}).")
            // The wiki effect text (SigilTexts), cut into chat lines.
            SigilTexts.lines(sigil).forEach { player.message(it) }
        }
    }
}

on_login { player.timers[SigilSkilling.LITHENESS_TIMER] = SigilSkilling.LITHENESS_TICKS }

// An armed Formidable Fighter bonus is lost at death, with the OSRS line (SigilCombat.onDeath).
on_player_death { SigilCombat.onDeath(player) }

// SigilFx: the OSRS sounds "no longer active" (a window ran out) and "cooldown finished" of an activated sigil.
SigilFx.WINDOW_TIMER.forEach { (sigil, key) -> on_timer(key) { if (Sigils.effective(player, sigil)) SigilFx.windowEnded(player, sigil) } }
// Chat line from OSRS DMM video cMUhQb_j3dw 02:04 (Sigil of Binding; same wording with the sigil's own name for the others:
// HIisFATzF8c 09:48 Sigil of Finality). Green in both videos (cMUhQb_j3dw 05:34 transparent chat, HIisFATzF8c fixed chat).
SigilFx.COOLDOWN_TIMER.forEach { (sigil, key) ->
    on_timer(key) {
        if (Sigils.effective(player, sigil)) {
            SigilFx.cooldownFinished(player)
            player.message("<col=006000>The cooldown for your ${sigil.label} is over. It can now be activated.</col>")
        }
    }
}

// An attuned sigil whose item is nowhere (inventory, bank) can never be unattuned: free its slot and say so (Sigils.reconcile).
on_login {
    Sigils.reconcile(player).forEach { player.message("Your attunement to the ${it.label} has faded: its sigil is no longer with you.") }
}
on_timer(SigilSkilling.LITHENESS_TIMER) { SigilSkilling.lithenessTick(player) }

/*
 * S-K5 a: the starting sigil. A player who has not chosen one yet picks 1 of the 3 Tier 2 starting sigils a few ticks after login
 * (team test accounts and bots skip it so their scripts are not interrupted).
 */
fun needsStart(player: gg.rsmod.game.model.entity.Player) =
    player.attr[Sigils.START_CHOSEN] != true && !player.username.lowercase().startsWith("teamtest")

on_login {
    if (!needsStart(player)) return@on_login
    // OSRS line (DMM Armageddon video x_-SE55x4E0 01:16, SIGIL-TEKST-BRON).
    player.message("You are eligible to receive a free Sigil. Click the Sigils button on the Worn Items side panel to open the menu.")
    player.timers[Sigils.START_PROMPT_TIMER] = Sigils.START_PROMPT_FIRST_TICKS
}

/** The choice of the free starting sigil (the login prompt and the Worn Items Sigils button while none was chosen). */
fun chooseStart(player: gg.rsmod.game.model.entity.Player) {
    player.queue {
        if (player.attr[Sigils.START_CHOSEN] == true) return@queue
        val labels = Sigils.START_CHOICES.map { it.label.removePrefix("Sigil of ").removePrefix("the ").replaceFirstChar { c -> c.uppercase() } }
        val choice = options(*labels.toTypedArray(), title = "Choose your starting sigil")
        if (choice in 1..labels.size) {
            val sigil = Sigils.START_CHOICES[choice - 1]
            if (player.inventory.add(sigil.unattunedId, 1).hasSucceeded() || player.bank.add(sigil.unattunedId, 1).hasSucceeded()) {
                player.attr[Sigils.START_CHOSEN] = true
                // OSRS item box (DMM Armageddon video x_-SE55x4E0 01:36, SIGIL-TEKST-BRON).
                itemMessageBox(
                    "You have claimed your free sigil.<br>Click on it, in a safe area such as a bank, to attune<br>so you can receive its benefits.",
                    sigil.unattunedId,
                )
            } else {
                player.message("You have no room for your starting sigil. Make room and log in again to choose it.")
            }
        }
    }
}

// The choice opens only at a quiet moment (Sigils.quietForStartChoice): never over the new-player introduction, in a fight, in a
// dangerous area or over an open window. Until then the timer looks again every few seconds; it is asked once per login.
on_timer(Sigils.START_PROMPT_TIMER) {
    if (player.attr[Sigils.START_CHOSEN] == true) return@on_timer
    if (!Sigils.quietForStartChoice(player)) {
        player.timers[Sigils.START_PROMPT_TIMER] = Sigils.START_PROMPT_RETRY_TICKS
        return@on_timer
    }
    chooseStart(player)
}

on_command("sigils") {
    Sigils.overview(player).forEach { player.message(it) }
    AttunedSigilsUi.open(player)
}

// SIGIL-WORN-ICOON: the sigil slot of the Worn Items tab opens the Attuned Sigils window.
on_button(interfaceId = SigilWornSlot.INTERFACE_ID, component = SigilWornSlot.ICON) { if (needsStart(player)) chooseStart(player) else SigilWornSlot.click(player) }

// SIGIL-INFO-UI: the "Attuned Sigils" window (owner design 21-11). Unattune per slot = the item option's rules (bank, no skull).
on_button(interfaceId = AttunedSigilsUi.INTERFACE_ID, component = gg.rsmod.game.tools.importer.AttunedSigilsInterfaceImportTool.AS.CLOSE) {
    AttunedSigilsUi.close(player)
}
for (slot in 0 until gg.rsmod.game.tools.importer.AttunedSigilsInterfaceImportTool.AS.SLOTS) {
    val hit = gg.rsmod.game.tools.importer.AttunedSigilsInterfaceImportTool.AS.slot(slot) + gg.rsmod.game.tools.importer.AttunedSigilsInterfaceImportTool.AS.S_BUTTON_HIT
    on_button(interfaceId = AttunedSigilsUi.INTERFACE_ID, component = hit) {
        when (val result = AttunedSigilsUi.unattune(player, slot, nearBank(player))) {
            null -> Unit
            Sigils.Result.ITEM_CHANGED -> player.message("You need that sigil in your inventory to unattune it.")
            else -> sigilMessage(player, result, attuning = false)
        }
    }
}
