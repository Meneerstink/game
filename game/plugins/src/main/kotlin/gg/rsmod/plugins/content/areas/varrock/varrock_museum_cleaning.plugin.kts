package gg.rsmod.plugins.content.areas.varrock

import gg.rsmod.game.model.Gender
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import gg.rsmod.plugins.content.quests.foundation.bracket.TheDigSite

// PROJECT48 run 4 O17 (The Dig Site unlock): the Varrock Museum specimen cleaning area (south fenced-off corner of the museum
// ground floor). Before this only the objects stood there: the gate swung open for the world, the tools / rock pile / table / crate
// did nothing and the six archaeologists were not spawned. Sources (team/dossiers/PROJECT48-notities/wiki): OSRS Wiki "Uncleaned find"
// ("The cleaning activity can be done after finishing The Dig Site", cleaning table with the 14% crumble chance and its message,
// unique 13/51 + common 38/51 + unremarkable table, uniques not rolled again while held), "Storage crate" (reward table for common
// artefacts), "Tools (Varrock Museum)" (kit: leather boots + gloves only if not worn/held, rock pick, specimen brush, trowel),
// "Clean necklace" (shown to an archaeologist = ruby necklaces can be enchanted into digsite pendants; handing it in removes it from
// inventory and bank), Transcript:Sinco Doar (= the shared Varrock Museum archaeologist dialogue). Object/NPC tiles = the 667 map
// (tools/cache667/maplocs.py 12853/13109). No source (left out, not invented): the cleaning/pick-up animation, the display cases and
// kudos (BUGLIJST VARROCK-MUSEUM-KUDOS), the gate refusal line (P48-TEKST). Timing of the repeat (3 ticks) has no source either.

private val GATE_Z = 3446
private val ARCHAEOLOGISTS = intArrayOf(Npcs.BARNABUS_HURMA, Npcs.MARIUS_GISTE, Npcs.CADEN_AZRO, Npcs.THIAS_LEACKE, Npcs.SINCO_DOAR, Npcs.TINSE_TORPE)
private val TOOLS = intArrayOf(Items.ROCK_PICK, Items.SPECIMEN_BRUSH, Items.TROWEL)
private val COMMON_ARTEFACTS = intArrayOf(Items.POTTERY, Items.JEWELLERY, Items.OLD_CHIPPED_VASE, Items.ARROWHEADS)
private val UNIQUES = intArrayOf(Items.OLD_SYMBOL, Items.ANCIENT_SYMBOL, Items.OLD_COIN, Items.ANCIENT_COIN)
// The six archaeologists are already spawned in spawns_12853 (Barnabus, Marius, Caden, Thias) and spawns_13109 (Sinco, Tinse).

// ---------------------------------------------------------------- gate (per-player crossing, in only after The Dig Site)
on_obj_option(obj = Objs.GATE_24536, option = "Open") {
    val door = player.getInteractingGameObj()
    val entering = player.tile.z > GATE_Z
    if (entering && !TheDigSite.isFinished(player)) {
        player.message("You need to complete The Dig Site to help clean the finds.")
        return@on_obj_option
    }
    player.lockingQueue(lockState = LockState.FULL) {
        DoorCrossing.enter(this, player, door, openedId = door.id, open = {
            DoorCrossing.swingOpenFor(player, door, door.id, ticks = 2)
        })
        player.lock = LockState.NONE
    }
}

// ---------------------------------------------------------------- tool rack
on_obj_option(obj = Objs.TOOLS_24535, option = "Take") {
    val missing = mutableListOf<Int>()
    if (!player.equipment.contains(Items.LEATHER_BOOTS) && !player.inventory.contains(Items.LEATHER_BOOTS)) missing += Items.LEATHER_BOOTS
    if (!player.equipment.contains(Items.LEATHER_GLOVES) && !player.inventory.contains(Items.LEATHER_GLOVES)) missing += Items.LEATHER_GLOVES
    TOOLS.filterTo(missing) { !player.inventory.contains(it) }
    if (missing.isEmpty()) {
        player.message("You already have all the tools you need.")
        return@on_obj_option
    }
    if (player.inventory.freeSlotCount < missing.size) {
        player.message("You don't have enough inventory space.")
        return@on_obj_option
    }
    missing.forEach { player.inventory.add(it) }
}

// ---------------------------------------------------------------- rock pile (repeats until the inventory is full or the player acts)
on_obj_option(obj = Objs.DIG_SITE_SPECIMEN_ROCKS, option = "Take") {
    player.queue(TaskPriority.WEAK) {
        while (true) {
            if (!player.inventory.hasSpace) {
                player.message("You don't have enough inventory space.")
                break
            }
            player.inventory.add(Items.UNCLEANED_FIND)
            wait(3)
        }
    }
}

// ---------------------------------------------------------------- specimen table (use the find on it)
on_item_on_obj(obj = Objs.SPECIMEN_TABLE_24556, item = Items.UNCLEANED_FIND) {
    if (!kitReady(player)) {
        player.message("You'll need to be wearing your leather gloves and boots as well as have access to your trowel, rock pick and specimen brush.")
        return@on_item_on_obj
    }
    player.queue(TaskPriority.WEAK) {
        while (player.inventory.contains(Items.UNCLEANED_FIND) && kitReady(player)) {
            if (!player.inventory.remove(Items.UNCLEANED_FIND).hasSucceeded()) break
            cleanOne(player)
            wait(3)
        }
    }
}

fun kitReady(player: Player): Boolean =
    player.equipment.contains(Items.LEATHER_BOOTS) && player.equipment.contains(Items.LEATHER_GLOVES) &&
        TOOLS.all { player.inventory.contains(it) }

fun cleanOne(player: Player) {
    // OSRS Wiki Uncleaned find: 14% crumble, then 13/51 unique artefact table, 38/51 common artefact table.
    if (world.random(99) < 14) {
        player.message("The find crumbles to dust in your hands - oops! Better not let the professionals see that one!")
        return
    }
    val roll = world.random(50)
    val found =
        if (roll < 13) {
            // Unique table: 4 artefacts at 3/51 each, the clean necklace at 1/51; an artefact already held (or the necklace already
            // learnt) is replaced by a roll on the unremarkable table.
            val u = world.random(12)
            val item = if (u < 12) UNIQUES[u / 3] else Items.CLEAN_NECKLACE
            val held = player.inventory.contains(item) || player.bank.contains(item)
            val learnt = item == Items.CLEAN_NECKLACE && VarrockMuseum.learnt(player)
            if (held || learnt) unremarkable() else Item(item)
        } else {
            Item(COMMON_ARTEFACTS[world.random(3)])
        }
    player.inventory.add(found)
}

// Unremarkable finds (wiki weights 26/26/26/13/13/13).
fun unremarkable(): Item {
    val r = world.random(116)
    return when {
        r < 26 -> Item(Items.BROKEN_ARROW)
        r < 52 -> Item(Items.BROKEN_GLASS)
        r < 78 -> Item(Items.IRON_DAGGER)
        r < 91 -> Item(Items.UNCUT_JADE)
        r < 104 -> Item(Items.BONES)
        else -> Item(Items.COINS_995, 5)
    }
}

// ---------------------------------------------------------------- storage crate (common artefact -> 1 reward; wiki weights /195)
private val CRATE: List<Triple<Int, Int, IntRange>> =
    listOf(
        Triple(19, Items.COINS_995, 17..69), Triple(21, Items.BOWL, 1..1), Triple(21, Items.EMPTY_POT, 1..1),
        Triple(10, Items.IRON_BOLTS, 1..10), Triple(10, Items.IRON_DAGGER, 1..1), Triple(10, Items.BRONZE_LIMBS, 1..1),
        Triple(10, Items.WOODEN_STOCK, 1..1), Triple(10, Items.TIN_ORE, 1..1), Triple(10, Items.COPPER_ORE, 1..1),
        Triple(7, Items.IRON_ORE, 1..1), Triple(10, Items.COAL, 1..1), Triple(5, Items.MITHRIL_ORE, 1..1),
        Triple(10, Items.IRON_KNIFE, 1..1), Triple(10, Items.IRON_DART, 1..50), Triple(10, Items.IRON_ARROWTIPS, 1..49),
        Triple(5, Items.UNCUT_JADE, 1..1), Triple(5, Items.UNCUT_OPAL, 1..1), Triple(10, Items.BIG_BONES, 1..1),
        Triple(2, Items.ANTIQUE_LAMP_11185, 1..1),
    )

COMMON_ARTEFACTS.forEach { artefact ->
    on_item_on_obj(obj = Objs.STORAGE_CRATE, item = artefact) {
        if (!player.inventory.remove(artefact).hasSucceeded()) return@on_item_on_obj
        var r = world.random(CRATE.sumOf { it.first } - 1)
        val pick = CRATE.first { r -= it.first; r < 0 }
        player.inventory.add(pick.second, world.random(pick.third))
    }
}

// ---------------------------------------------------------------- archaeologists (Transcript:Sinco Doar, shared by all six)
ARCHAEOLOGISTS.forEach { npcId ->
    on_npc_option(npc = npcId, option = "Talk-to") {
        player.queue { archaeologist(npcId) }
    }
    (COMMON_ARTEFACTS + UNIQUES + Items.CLEAN_NECKLACE).forEach { find ->
        on_item_on_npc(item = find, npc = npcId) {
            player.queue {
                chatPlayer("Take a look at this...")
                showFind(npcId)
            }
        }
    }
}

suspend fun QueueTask.archaeologist(npcId: Int) {
    val title = if (player.appearance.gender == Gender.MALE) "sir" else "madam"
    when (world.random(2)) {
        0 -> chatNpc("Hello there; I see you're qualified. Come to help us out?", npc = npcId)
        1 -> chatNpc("Hello! You're that student who recently finished all three Earth Sciences exams, aren't you? Come to help us out?", npc = npcId)
        else -> chatNpc("Greetings, my dear $title. Have you come to give us a hand?", npc = npcId)
    }
    when (options("Yes, how can I help out?", "I found something interesting.", "No thanks.")) {
        1 -> {
            chatPlayer("Yes, how can I help out?")
            if (!kitReady(player)) {
                chatNpc(
                    "Well, you'll need to get your equipment first - it's all there on the tool rack. Use what you learned from your " +
                        "Earth Sciences exams. You'll need to be wearing your leather gloves and boots as well as have access to your " +
                        "trowel, rock pick and specimen brush.",
                    npc = npcId,
                )
            } else {
                chatNpc("I see you have your equipment - well done.", npc = npcId)
                chatNpc(
                    "To really give us a hand, you could clean some finds for us. They're mostly done - just some fine work to finish " +
                        "off. Take a find from the pile that the workmen are bringing in, then there are spare tables over there to " +
                        "clean them on. If you find anything interesting, let us know and we'll see if it's good enough to go on display.",
                    npc = npcId,
                )
            }
        }
        2 -> {
            chatPlayer("I found something interesting.")
            showFind(npcId)
        }
        3 -> chatPlayer("No, just here to watch.")
    }
}

private val CASES =
    mapOf(
        Items.OLD_COIN to Pair("I've cleaned this old coin - is it of any use?", 45),
        Items.ANCIENT_COIN to Pair("I found this really old coin - is it of any use?", 44),
        Items.ANCIENT_SYMBOL to Pair("I found this crumbly old decoration - is it of any use?", 37),
        Items.OLD_SYMBOL to Pair("I found this really old decoration - is it of any use?", 36),
        Items.POTTERY to Pair("I found this piece of pottery - is it of any use?", 22),
    )

private val CASE_TOLD = AttributeKey<Int>(persistenceKey = "museum_case_told")

suspend fun QueueTask.showFind(npcId: Int) {
    chatNpc("Oh? Let's take a look...", npc = npcId)
    val inv = player.inventory
    val find = (listOf(Items.CLEAN_NECKLACE, Items.OLD_COIN, Items.ANCIENT_COIN, Items.ANCIENT_SYMBOL, Items.OLD_SYMBOL, Items.POTTERY,
        Items.ARROWHEADS, Items.JEWELLERY, Items.OLD_CHIPPED_VASE)).firstOrNull { inv.contains(it) }
    when (find) {
        Items.CLEAN_NECKLACE -> necklace(npcId)
        Items.OLD_COIN, Items.ANCIENT_COIN, Items.ANCIENT_SYMBOL, Items.OLD_SYMBOL, Items.POTTERY -> displayFind(npcId, find)
        Items.ARROWHEADS -> {
            chatPlayer("I found these old arrowheads.")
            chatNpc(
                "Ahh, yes, we find lots of those - they're not important. We already have some in our display. Just add it to the " +
                    "crate of finds over near the door.",
                npc = npcId,
            )
            chatNpc("If there is anything already in the crate, feel free to take it as a reward for your work.", npc = npcId)
            chatPlayer("Oh, okay.")
        }
        Items.JEWELLERY -> {
            chatPlayer("I found these old necklaces.")
            chatNpc("An interesting find... Here, let me offer you something in return.", npc = npcId)
            chatPlayer("Oh! Okay.")
            if (player.inventory.remove(Items.JEWELLERY).hasSucceeded()) {
                player.inventory.add(Items.COINS_995, 5)
                itemMessageBox("The archaeologist gives you 5 coins... must have been worth a lot in his day!", Items.COINS_995, 5)
            }
        }
        Items.OLD_CHIPPED_VASE -> {
            chatPlayer("I cleaned up this old vase.")
            chatNpc(
                "Ah, the vases... I've lost count of how many we've found of those... The only completely intact one is in the " +
                    "display case already. Just add it to the crate of finds over near the door.",
                npc = npcId,
            )
            chatNpc("If there is anything already in the crate, feel free to take it as a reward for your work.", npc = npcId)
            chatPlayer("Right.")
        }
        else -> {
            chatPlayer("Err... I seem to have lost it. Sorry.")
            chatNpc("Sounds like you never had it, ${if (player.appearance.gender == Gender.MALE) "lad" else "lass"}!", npc = npcId)
        }
    }
}

suspend fun QueueTask.displayFind(npcId: Int, find: Int) {
    val (line, case) = CASES.getValue(find)
    val bit = 1 shl UNIQUES.plus(Items.POTTERY).indexOf(find)
    val told = (player.attr[CASE_TOLD] ?: 0) and bit != 0
    if (told) {
        // The display cases do not exist on this server yet (BUGLIJST VARROCK-MUSEUM-KUDOS): the "after being told" line.
        val name = when (find) {
            Items.OLD_COIN -> "the old coin"; Items.ANCIENT_COIN -> "the ancient coin"; Items.ANCIENT_SYMBOL -> "the ancient symbol"
            Items.OLD_SYMBOL -> "the old symbol"; else -> "the pottery"
        }
        chatNpc("Back again? Have you put $name in display case $case yet?", npc = npcId)
        chatPlayer("No, I'll go do that now.")
        return
    }
    player.attr[CASE_TOLD] = (player.attr[CASE_TOLD] ?: 0) or bit
    chatPlayer(line)
    when (find) {
        Items.OLD_COIN -> {
            chatNpc("Oh my! What a wonderful find! If this is what I believe it to be, it would allow us to date the Dig Site city.", npc = npcId)
            chatNpc(
                "And look here, it has the name of the city too! Saranthium! That must be during the Saradominist occupation. This " +
                    "is a wonderful find. Please take it and place it in display case 45.",
                npc = npcId,
            )
            chatPlayer("Right, display case 45 it is...")
        }
        Items.ANCIENT_COIN -> {
            chatNpc("What have we here?", npc = npcId)
            chatNpc(
                "Oh! This is very interesting...and confusing at the same time. This coin means that the city that we're excavating " +
                    "on the Digsite - Saranthium - was actually inhabited much, much earlier by followers of Zamorak, at which time, " +
                    "the city was known as Senntisten.",
                npc = npcId,
            )
            chatNpc(
                "This is a most remarkable discovery, as we have other evidence indicating that Zarosian followers were resident " +
                    "before the Saradominists took over, but it also seems that Zamorakian forces used it during a battle of some " +
                    "sort. Still, it allows us to roughly date when the city changed from following Zaros and Zamorak to following " +
                    "Saradomin. Put it in display case 44.",
                npc = npcId,
            )
            chatPlayer("Case 44 it is!")
        }
        Items.ANCIENT_SYMBOL -> {
            chatNpc(
                "Ahh... That looks like an altar piece. Hmm, yes...from the early Saradominist worshippers by the looks of it. Quite " +
                    "delicate, too. Put it in display case 37.",
                npc = npcId,
            )
            chatPlayer("Case 37 it is!")
        }
        Items.OLD_SYMBOL -> {
            chatNpc("Wow!", npc = npcId)
            chatNpc(
                "That's in rather good condition, isn't it? An old altar decoration by the looks of things, from the Saradominist " +
                    "city no doubt. Put it in display case 36.",
                npc = npcId,
            )
            chatPlayer("Case 36 it is!")
        }
        else -> {
            chatNpc(
                "Ahh... Oh! Hmm...yes...made by Zaros worshippers, by the looks of it. Beautifully-shaped. It looks as if this would " +
                    "go nicely in display case 22. This piece reinforces our evidence that the city was occupied by Zarosians before " +
                    "Saradominists.",
                npc = npcId,
            )
            chatPlayer("Case 22 it is!")
        }
    }
}

suspend fun QueueTask.necklace(npcId: Int) {
    chatPlayer("I found this pretty necklace.")
    chatNpc("Interesting...yes...yes...hmmm.... It looks like it's enchanted.", npc = npcId)
    chatPlayer("With what?")
    chatNpc(
        "Oh, I think it's just a benign teleportation spell. I tinker in magic when I'm not doing archaeology. I can teach you how " +
            "to make more necklaces like this. Amazing that they had this kind of knowledge back then.",
        npc = npcId,
    )
    chatPlayer("That would be good. Where is the teleport to?")
    chatNpc(
        "Hmm...all the indications are that it's somewhere to the east of us, but I'm not sure how far. Perhaps it was a teleport " +
            "back to Saranthium. Let me just take a closer look.",
        npc = npcId,
    )
    // Handing it in removes every copy from the inventory and the bank (wiki Uncleaned find note).
    player.inventory.remove(Items.CLEAN_NECKLACE, player.inventory.getItemCount(Items.CLEAN_NECKLACE))
    val banked = player.bank.getItemCount(Items.CLEAN_NECKLACE)
    if (banked > 0) player.bank.remove(Items.CLEAN_NECKLACE, banked)
    player.attr[VarrockMuseum.RUBY_PENDANT_LEARNT] = true
    itemMessageBox("The archaeologist takes the necklace and turns it over in his hands, studying it closely.", Items.CLEAN_NECKLACE)
    itemMessageBox(
        "The archaeologist shows you the techniques for this particular spell, which requires five fire runes arranged around one " +
            "cosmic rune - and level 49 Magic - then casting the spell on a crafted ruby necklace of your own.",
        Items.CLEAN_NECKLACE,
    )
    player.message("<col=800000>You can make enchanted ruby necklaces.</col>")
    chatPlayer("So I just make a ruby necklace and use five fire runes and one cosmic rune to enchant it.")
    chatNpc("Yes, indeed. Well done, youngster, thanks for helping us.", npc = npcId)
}
