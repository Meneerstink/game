package gg.rsmod.plugins.content.areas.portphasmatys

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/*
 * DRAGONTOOTH-KAART (SIGILS-QUESTS-REST, 24-11): the Dragontooth Island square was always in the map (rocks, trees, the Ghost
 * captain at 3792,3560), but the Ghost captain 1704 had no boat - only the generic transcript row ("Woooo" + "You cannot understand
 * the ghost.") - so nobody could get there. Sources: OSRS Wiki "Ghost captain" (25 ecto-tokens for the return trip, or a one-off
 * 500 for free trips; ghostspeak amulet needed; right-click Travel) and Transcript:Ghost captain (every line below, both docks and
 * the right-click Travel). Landing tiles: the free tile next to each captain (Port Phasmatys 3702,3487 by the captain at 3703,3487;
 * the island 3792,3559). Not here: the Ring of Charos(a) price of 10 (wiki: only after Ghosts Ahoy, which this game does not have,
 * and no transcript line gives that price).
 */

val CAPTAIN = Npcs.GHOST_CAPTAIN
val TRIP_COST = 25
val UNLIMITED_COST = 500
val TO_ISLAND = Tile(3792, 3559, 0)
val TO_PORT = Tile(3702, 3487, 0)
val FADE_OUT_INTERFACE = 170
val GHOST_CAPTAIN_UNLIMITED = AttributeKey<Boolean>(persistenceKey = "ghost_captain_unlimited")

fun onIsland(p: Player) = p.tile.x >= 3750
fun understands(p: Player) = gg.rsmod.plugins.content.diaries.DiaryItems.ghostspeak(p)
fun unlimited(p: Player) = p.attr[GHOST_CAPTAIN_UNLIMITED] == true

fun row(p: Player, destination: Tile, arrival: String?) {
    p.interruptQueues()
    DeadmanTimerGate.requestRoute(p, SevenSecondAction.Kind.TRANSPORT) {
        p.lockingQueue(TaskPriority.WEAK) {
            p.openInterface(FADE_OUT_INTERFACE, InterfaceDestination.MAIN_SCREEN_FULL)
            wait(2)
            p.moveTo(destination)
            wait(3)
            p.closeMainInterface()
            if (arrival != null) p.queue(TaskPriority.WEAK) { messageBox(arrival) }
        }
    }
}

/** Takes the fare and rows out; false (nothing taken) when the ectotokens are no longer there. */
fun payAndRow(p: Player, cost: Int): Boolean {
    if (p.inventory.getItemCount(Items.ECTOTOKEN) < cost || !p.inventory.remove(Items.ECTOTOKEN, cost).hasSucceeded()) return false
    if (cost == UNLIMITED_COST) p.attr[GHOST_CAPTAIN_UNLIMITED] = true
    row(p, TO_ISLAND, "After a long boat trip you arrive at Dragontooth Island.")
    return true
}

suspend fun QueueTask.portTalk() {
    val p = player
    if (!understands(p)) {
        chatNpc("Woooo wooo wooooo woooo")
        return
    }
    if (unlimited(p)) {
        chatNpc("Would you like to visit Dragontooth Island? It is a most pleasant island in the sea between Morytania and the lands of the east.")
        if (options("Yes please.", "No thanks.", title = "Select an option") == 1) {
            chatPlayer("Yes please.")
            row(p, TO_ISLAND, null)
        } else {
            chatPlayer("No thanks.")
        }
        return
    }
    chatNpc("Would you like to visit Dragontooth Island? It is a most pleasant island in the sea between Morytania and the lands of the east. It'll cost you 25 ectotokens for the return trip.")
    val hasTrip = p.inventory.getItemCount(Items.ECTOTOKEN) >= TRIP_COST
    val first = if (hasTrip) listOf("Okay, here's 25 ectotokens. Let's go.") else emptyList()
    val menu = first + listOf("If I pay 500 ectotokens, can I get unlimited trips?", "Not now, thanks.")
    when (menu[options(*menu.toTypedArray(), title = "Select an option") - 1]) {
        "Okay, here's 25 ectotokens. Let's go." -> {
            chatPlayer("Okay, here's 25 ectotokens. Let's go.")
            payAndRow(p, TRIP_COST)
        }
        "If I pay 500 ectotokens, can I get unlimited trips?" -> {
            chatPlayer("If I pay 500 ectotokens, can I get unlimited trips?")
            if (p.inventory.getItemCount(Items.ECTOTOKEN) >= UNLIMITED_COST) {
                chatNpc("Yes, that seems fair. Okay, hand them over and we can set off.")
                payAndRow(p, UNLIMITED_COST)
                return
            }
            chatNpc("Yes, I'd accept that deal. But as you don't have enough ectotokens for that, would you like to pay 25 ectotokens for a trip now?")
            val again = (if (p.inventory.getItemCount(Items.ECTOTOKEN) >= TRIP_COST) listOf("Okay, here's 25 ectotokens. Let's go.") else emptyList()) + "Not now, thanks."
            if (again[options(*again.toTypedArray(), title = "Select an option") - 1] == "Not now, thanks.") {
                chatPlayer("Not now, thanks.")
            } else {
                chatPlayer("Okay, here's 25 ectotokens. Let's go.")
                payAndRow(p, TRIP_COST)
            }
        }
        else -> chatPlayer("Not now, thanks.")
    }
}

suspend fun QueueTask.islandTalk() {
    val p = player
    while (true) {
        when (options("Can you take me back to Phasmatys, now?", "There isn't much on this island, is there?", "Actually, I don't want anything.", title = "Select an option")) {
            1 -> {
                chatPlayer("Can you take me back to Phasmatys, now?")
                if (understands(p)) chatNpc("Yes, climb in.")
                else messageBox("The ghost sailor wails at you incomprehensibly, and points at the boat, nodding his ghostly head.")
                row(p, TO_PORT, null)
                return
            }
            2 -> {
                chatPlayer("There isn't much on this island, is there?")
                if (understands(p)) {
                    chatNpc("Beautiful isn't it?")
                    chatPlayer("But it cost me a lot of ectotokens to get here!")
                    chatNpc("Worth every token, if you ask me.")
                } else {
                    chatNpc("Woooo wooo wooooo woooo")
                }
            }
            else -> {
                chatPlayer("Actually, I don't want anything.")
                if (!understands(p)) chatNpc("Woooo wooo wooooo woooo")
                return
            }
        }
    }
}

/** Right-click Travel (Transcript:Ghost captain "Right-click 'Travel' option"); from the island it is the paid-for way back. */
fun travel(p: Player) {
    if (!understands(p)) {
        p.queue { chatNpc("Woooo wooo wooooo woooo") }
        return
    }
    when {
        onIsland(p) -> row(p, TO_PORT, null)
        unlimited(p) -> row(p, TO_ISLAND, null)
        p.inventory.getItemCount(Items.ECTOTOKEN) >= TRIP_COST -> payAndRow(p, TRIP_COST)
        else -> p.queue { chatNpc("A return trip to Dragontooth Island costs 25 ectotokens.") }
    }
}

val CAPTAIN_OPS = world.definitions.get(NpcDef::class.java, CAPTAIN).options.filterNotNull().map { it.lowercase() }
if ("talk-to" in CAPTAIN_OPS) {
    on_npc_option(npc = CAPTAIN, option = "talk-to") { player.queue { if (onIsland(player)) islandTalk() else portTalk() } }
}
if ("travel" in CAPTAIN_OPS) on_npc_option(npc = CAPTAIN, option = "travel") { travel(player) }
