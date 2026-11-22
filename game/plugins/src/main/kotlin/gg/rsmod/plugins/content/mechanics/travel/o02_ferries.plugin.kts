package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/*
 * PROJECT48 O02 (run 4): dead right-click travel options of spawned npcs (O01 scan), with OSRS Wiki behaviour:
 *  - Dwarven Ferryman "Cross-river" (OSRS Wiki "Dwarven Ferryman" + "Transcript:Dwarven Ferryman"): across the River Kelda in the
 *    Keldagrim mine for 2 coins; "The dwarf ferries you across the river.". The ferryman on the mines (north) bank takes you even
 *    without 2 coins, relieving you of a single coin if that is all you have; the south one sends you away ("You don't even have 2
 *    gold coins, humans!" / "Come back later."). A Ring of Charos(a) worn = free. The banks were checked with a bot on the test
 *    server (p163): from 2841,10129 the north ferrymen cannot be reached, from 2853,10145 the south one cannot.
 *  - Kathy Corkat "Travel" (wiki "Kathy Corkat" + transcript): from the Piscatoris side she rows you up the river for free
 *    ("Kathy Corkat rows you up the river..."), landing by her other spot west of the Gnome Stronghold (wiki map 2369,3486).
 *  - Lokar Searunner "Travel" at Pirates' Cove (wiki: "Pirate's Cove / Rellekka"): back to the Rellekka dock, where our
 *    Lunar Diplomacy step finds him.
 * Not wired: Ghost captain "Travel" (Dragontooth Island has no map in the 667 cache), Captain Bentley (the Travel id 408 is not
 * spawned) and the far Dwarven Ferryman spawn at 2784,10072 (not one of the two wiki ferrymen).
 */

val FERRY_FADE_IN = 115
val FERRY_FADE_OUT = 170

/** The River Kelda crossing: the two banks, and the area in which the ferrymen operate it. */
val KELDA_SOUTH_BANK = Tile(2841, 10129, 0)
// 2853,10145 = beside the north ferryman 1844 at 2854,10144 (bot 22-11 17:4x: 2838,10140 is a closed-in tile, "cannot step in any direction").
val KELDA_NORTH_BANK = Tile(2853, 10145, 0)

fun nearKeldaCrossing(t: Tile): Boolean = t.height == 0 && t.x in 2825..2865 && t.z in 10115..10155

val KATHY_UPRIVER_LANDING = Tile(2369, 3485, 0)
val LOKAR_RELLEKKA_DOCK = Tile(2621, 3685, 0)

fun Player.wearsCharos(): Boolean = equipment.contains(Items.RING_OF_CHAROS_A) || equipment.contains(Items.RING_OF_CHAROS_AI)

/** Fade to black, move, fade back (the charter boats' interfaces 115/170), behind the Deadman 7-second transport gate. */
fun ferry(
    player: Player,
    dest: Tile,
    message: String?,
    box: String?,
) {
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.lockingQueue(TaskPriority.WEAK) {
            player.openInterface(FERRY_FADE_IN, InterfaceDestination.MAIN_SCREEN_FULL)
            wait(2)
            player.moveTo(dest)
            player.openInterface(FERRY_FADE_OUT, InterfaceDestination.MAIN_SCREEN_FULL)
            wait(2)
            player.closeMainInterface()
            if (message != null) player.message(message)
            // The trip is over: the landing message is an ordinary dialogue, the player is not held locked until he clicks it.
            player.unlock()
            if (box != null) messageBox(box)
        }
    }
}

listOf(Npcs.DWARVEN_FERRYMAN, Npcs.DWARVEN_FERRYMAN_1844).forEach { ferryman ->
    on_npc_option(ferryman, "Cross-river") {
        val here = player.tile
        if (!nearKeldaCrossing(here)) return@on_npc_option
        val onNorthBank = here.z >= 10135
        val dest = if (onNorthBank) KELDA_SOUTH_BANK else KELDA_NORTH_BANK
        val coins = player.inventory.getItemCount(Items.COINS_995)
        if (coins < 2 && !player.wearsCharos()) {
            if (!onNorthBank) {
                player.queue {
                    chatNpc("You don't even have 2 gold coins, humans!", facialExpression = FacialExpression.ANGRY)
                    chatNpc("Come back later.", facialExpression = FacialExpression.ANGRY)
                }
                return@on_npc_option
            }
            // The mines-side ferryman makes an exception; one coin is all he takes.
            if (coins == 1) player.inventory.remove(Items.COINS_995, 1)
        } else if (!player.wearsCharos()) {
            player.inventory.remove(Items.COINS_995, 2)
        }
        ferry(player, dest, "The dwarf ferries you across the river.", null)
    }
}

on_npc_option(Npcs.KATHY_CORKAT_3831, "Travel") {
    ferry(player, KATHY_UPRIVER_LANDING, null, "Kathy Corkat rows you up the river...")
}

on_npc_option(Npcs.LOKAR_SEARUNNER_4537, "Travel") {
    ferry(player, LOKAR_RELLEKKA_DOCK, null, null)
}
