package gg.rsmod.plugins.content.areas.portkhazard

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask

/*
 * O22-ZONDER-BRON (SIGILS-QUESTS-REST, 24-11): Tindel Marchant identifies rusty swords and rusty scimitars for 100 coins.
 * Sources: OSRS Wiki Transcript:Tindel Marchant (every line below), "Rusty sword" and "Rusty scimitar" (Possible rewards, Jagex
 * rates /100: sword - iron short 20, iron long 37, bronze short 17, bronze long 7, steel short 6, steel long 4, mithril short 1,
 * mithril long 1, black short 3, black long 2, coins 1500-1999 1, nothing 1; scimitar - bronze 24, iron 57, steel 10, black 5,
 * mithril 2, coins 1500-1999 1, nothing 1). Barrows repairs stay with Bob/the armour stands (no repair flow here).
 */

val FEE = 100

/** (weight /100, item or -1 = antique coins or 0 = nothing, the name Tindel says). */
data class Outcome(val weight: Int, val item: Int, val name: String)

val SWORD = listOf(
    Outcome(20, Items.IRON_SWORD, "an iron shortsword"), Outcome(37, Items.IRON_LONGSWORD, "a long sword"),
    Outcome(17, Items.BRONZE_SWORD, "a bronze shortsword"), Outcome(7, Items.BRONZE_LONGSWORD, "a bronze long sword"),
    Outcome(6, Items.STEEL_SWORD, "a steel shortsword"), Outcome(4, Items.STEEL_LONGSWORD, "a steel long sword"),
    Outcome(1, Items.MITHRIL_SWORD, "a mithril shortsword"), Outcome(1, Items.MITHRIL_LONGSWORD, "a mithril long sword"),
    Outcome(3, Items.BLACK_SWORD, "a black shortsword"), Outcome(2, Items.BLACK_LONGSWORD, "a black longsword"),
    Outcome(1, -1, ""), Outcome(1, 0, ""),
)
val SCIMITAR = listOf(
    Outcome(24, Items.BRONZE_SCIMITAR, "a bronze scimitar"), Outcome(57, Items.IRON_SCIMITAR, "an iron scimitar"),
    Outcome(10, Items.STEEL_SCIMITAR, "a steel scimitar"), Outcome(5, Items.BLACK_SCIMITAR, "a black scimitar"),
    Outcome(2, Items.MITHRIL_SCIMITAR, "a mithril scimitar"), Outcome(1, -1, ""), Outcome(1, 0, ""),
)

fun roll(table: List<Outcome>): Outcome {
    var r = world.random(99)
    for (o in table) { if (r < o.weight) return o; r -= o.weight }
    return table.last()
}

suspend fun QueueTask.identify(rusty: Int) {
    val p = player
    // Both taken in one step: no free sword if the coins or the sword left the inventory during the dialogue.
    if (p.inventory.getItemCount(Items.COINS_995) < FEE || !p.inventory.contains(rusty)) return
    if (!p.inventory.remove(Items.COINS_995, FEE).hasSucceeded()) return
    if (!p.inventory.remove(rusty, 1).hasSucceeded()) { p.inventory.add(Items.COINS_995, FEE); return }
    val noun = if (rusty == Items.RUSTY_SCIMITAR) "scimitar" else "sword"
    // The wiki notes the scimitar box shows the rusty sword picture (sic); the right item is shown here.
    doubleItemMessageBox("You hand Tindel 100 coins plus the rusty $noun.", rusty, Items.COINS_995, amount2 = FEE)
    val o = roll(if (rusty == Items.RUSTY_SCIMITAR) SCIMITAR else SWORD)
    gg.rsmod.plugins.content.diaries.Diaries.event(p, "tindel:identify") // DIARIES-OSRS Ardougne easy 7 (the identify itself counts, also when the sword is worthless)
    when (o.item) {
        0 -> chatNpc("Sorry my friend, but the item wasn't worth anything. I've disposed of it for you.")
        -1 -> {
            val coins = world.random(1500..1999)
            chatNpc("Wow! The item you gave me was a very rare antique...no real use...but hey, it's worth some money!")
            p.inventory.add(Items.COINS_995, coins)
            itemMessageBox("Tindel gives you $coins coins.", Items.COINS_995, amountOrZoom = coins)
            chatNpc("That's a fair price for the item! I'll add it to my collection!")
        }
        else -> {
            chatNpc("There you go my friend, it turned out to be ${o.name}.")
            if (!p.inventory.add(o.item, 1).hasSucceeded()) world.spawn(gg.rsmod.game.model.entity.GroundItem(o.item, 1, p.tile, p))
            itemMessageBox("Tindel gives you ${o.name}.", o.item)
        }
    }
}

/** "Ok, I'll give it a go!" / Give-Sword: checks first, then the sword-or-scimitar choice. */
suspend fun QueueTask.giveSword() {
    val p = player
    val sword = p.inventory.contains(Items.RUSTY_SWORD)
    val scim = p.inventory.contains(Items.RUSTY_SCIMITAR)
    when {
        !sword && !scim -> { chatNpc("Sorry my friend, but you don't seem to have any swords that need to be identified."); return }
        p.inventory.getItemCount(Items.COINS_995) < FEE -> { chatNpc("Sorry my friend, but you don't seem to have enough money to cover my fee!"); return }
    }
    val rusty = if (sword && scim) {
        messageBox("Which would you like to identify?")
        if (options("The sword", "The scimitar", title = "Select an Option") == 1) Items.RUSTY_SWORD else Items.RUSTY_SCIMITAR
    } else if (sword) Items.RUSTY_SWORD else Items.RUSTY_SCIMITAR
    identify(rusty)
}

suspend fun QueueTask.tindelTalk() {
    chatNpc("Hello there! Welcome to my special antiques boutique.")
    while (true) {
        when (options("What do you do here?", "What's involved?", "What do I get from this?", "Ok, I'll give it a go!", "Ok, thanks.", title = "Select an Option")) {
            1 -> {
                chatPlayer("What do you do here?")
                chatNpc("I'm a specialist at identifying rare and antique weapons, specifically swords, but I plan to branch out. If you have any old and rusty weapons that you want me to check out, just show them to me, pay me 100 Gold and")
                chatNpc("I'll see if you have an antique on your hands.")
                chatNpc("I can also repair some antique weapons and armours, just show me the item and I'll let you know if I can repair it and for how much.")
            }
            2 -> {
                chatPlayer("What's involved?")
                chatNpc("Well, pay me 100 Gold and I'll see if any rusty swords that you've found are actually worth anything. Some of them might be worth some money! If it's an antique item though, I reserve the right to purchase it")
                chatNpc("immediately for adding to my own personal collection. I'll give you fair price for it.")
            }
            3 -> {
                chatPlayer("What do I get from this?")
                chatNpc("If I can reclaim the sword with my own specialist skills, I'll return it to you in peak condition. If it's an antique, I'll just give you what I think it's worth and I generally pay quite well. However, if it's just a piece of junk, I'll")
                chatNpc("simply give you the bad news and get rid of the item for you.")
            }
            4 -> { chatPlayer("Ok, I'll give it a go!"); giveSword(); return }
            else -> { chatPlayer("Ok, thanks."); return }
        }
    }
}

val TINDEL_OPS = world.definitions.get(NpcDef::class.java, Npcs.TINDEL_MARCHANT).options.filterNotNull().map { it.lowercase() }
if ("talk-to" in TINDEL_OPS) on_npc_option(npc = Npcs.TINDEL_MARCHANT, option = "talk-to") { player.queue { tindelTalk() } }
if ("give-sword" in TINDEL_OPS) on_npc_option(npc = Npcs.TINDEL_MARCHANT, option = "give-sword") { player.queue { giveSword() } }
on_item_on_npc(item = Items.RUSTY_SWORD, npc = Npcs.TINDEL_MARCHANT) { player.queue { identifyChecked(Items.RUSTY_SWORD) } }
on_item_on_npc(item = Items.RUSTY_SCIMITAR, npc = Npcs.TINDEL_MARCHANT) { player.queue { identifyChecked(Items.RUSTY_SCIMITAR) } }

suspend fun QueueTask.identifyChecked(rusty: Int) {
    if (player.inventory.getItemCount(Items.COINS_995) < FEE) {
        chatNpc("Sorry my friend, but you don't seem to have enough money to cover my fee!")
        return
    }
    identify(rusty)
}
