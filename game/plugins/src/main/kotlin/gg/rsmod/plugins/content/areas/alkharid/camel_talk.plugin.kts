package gg.rsmod.plugins.content.areas.alkharid

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.quests.QuestStatus

/*
 * PROJECT48 run 4 O20 (Enakhra's Lament ONTGR): the Camulet lets its wearer talk to the desert camels, and they give Ugthanki dung
 * (the Camulet's recharge). OSRS Wiki 'Camulet': "When equipped, its wearer can speak to certain camels ... Players can receive dung
 * by speaking to certain camels (after partial completion of My Arm's Big Adventure) while wearing the Camulet ... You'll also need a
 * bucket to collect the dung." All lines, the 30 s dung object 6257, anim 895 and the menus: Void AlTheCamel.kt (npcOperate Talk-to
 * "*camel", objectOperate Pick-up "dung", itemOnObject bucket). The 'dung' answer here waits for My Arm's Big Adventure being started
 * (wiki); Void gives it always. Void's "You close your eyes..." text on the fade screen is not shown (our fade interface has no text
 * line); the fade itself = interfaces 115/170 as the charter ships.
 */
val CAMEL_IDS =
    listOf(
        Npcs.CAMEL, Npcs.AL_THE_CAMEL, Npcs.ELLY_THE_CAMEL, Npcs.OLLIE_THE_CAMEL, Npcs.CAM_THE_CAMEL, Npcs.ALICE_THE_CAMEL,
        Npcs.NEFERTI_THE_CAMEL,
    )
val DUNG_OBJ = 6257
val FILL_BUCKET_ANIM = 895
val HEARD_POEM = AttributeKey<Boolean>("al_the_camel")
val MABA_SLOT = 79

fun wearsCamulet(player: Player) = player.equipment[EquipmentType.AMULET.id]?.id == Items.CAMULET

suspend fun QueueTask.insult() {
    chatPlayer(
        when (world.random(2)) {
            0 -> "Mmm... looks like that camel would make a nice kebab."
            1 -> "I wonder if that camel has fleas..."
            else -> "If I go near that camel, it'll probably bite my hand off."
        },
    )
}

suspend fun QueueTask.bestOfLuck(camel: Int) {
    chatPlayer("Well, best of luck with that.")
    chatNpc("If you want to hear my poems once more, please come back again.", npc = camel)
}

suspend fun QueueTask.whatDoesSheThink(camel: Int) {
    chatPlayer("What does she think of your poems?")
    chatNpc("She's never heard them.", npc = camel)
    chatPlayer("Why not?")
    chatNpc("I suspect she loves another - Ollie, another camel who roams with her to the north.", npc = camel, facialExpression = FacialExpression.SAD)
    chatNpc("So I shall stay here and compose poems for her.", npc = camel, facialExpression = FacialExpression.SAD)
    when (options("Why not tell her how you feel?", "Well, best of luck with that.")) {
        1 -> {
            chatPlayer("Why not tell her how you feel?")
            chatNpc("She seems happy enough as she is, and I have my poems to comfort me.", npc = camel)
            bestOfLuck(camel)
        }
        else -> bestOfLuck(camel)
    }
}

suspend fun QueueTask.desertsDay(camel: Int, interrupt: Boolean) {
    if (!interrupt) chatNpc("That's my favourite poem. Ahem...", npc = camel)
    chatNpc("Shall I compare thee to a desert's day? Thou art drier and more rough-skinned.", npc = camel)
    chatNpc("Rough sandstorms shake the cactuses away And summer's heat defers to autumn wind.", npc = camel)
    if (interrupt) chatPlayer("Look, I don't really have the time to...")
    chatNpc("Sometimes too hot the eye of heaven shines, With Guthix' gold complexion often dimmed;", npc = camel)
    chatNpc("And every fair from fair sometime declines, By chance or desert's changing course untrimmed...", npc = camel)
    if (interrupt) {
        chatPlayer("Please, stop.")
        chatNpc("But I've only got six lines left!", npc = camel)
        chatPlayer("That's six too many. If I really want to hear your poetry, I'll tell you, okay?")
        chatNpc("Very well. Come back and talk to me if you want to hear more.", npc = camel)
        player.attr[HEARD_POEM] = true
    } else {
        chatNpc("But thine eternal desert shall not fade Nor lose possession of that sand thou owest;", npc = camel)
        chatNpc("Nor Zamorak brag thou art in his shades, When in eternal lines to sand thou growest,", npc = camel)
        chatNpc("So long as camels breathe or eyes can see, So long lives this, and this gives life to thee.", npc = camel)
        chatNpc("Ah, Elly, how beautiful you are.", npc = camel)
        whatDoesSheThink(camel)
    }
}

suspend fun QueueTask.justToSay(camel: Int) {
    chatNpc(
        "I wrote this poem when I went to the oasis to nibble at a tree, then discovered I'd left nothing for Elly to nibble. I was distraught.",
        npc = camel,
        facialExpression = FacialExpression.SAD,
    )
    chatNpc("This Is Just To Say", npc = camel)
    chatNpc("I have nibbled the cacti that were by the oasis,", npc = camel)
    chatNpc("and which you were probably saving for lunch.", npc = camel)
    chatNpc("Forgive me, they were delicious, so crunchy and so cold.", npc = camel)
    chatNpc("I wonder if she's forgiven me for eating her snack.", npc = camel)
    whatDoesSheThink(camel)
}

suspend fun QueueTask.listenTo(camel: Int) {
    chatNpc("Oh, it's you again. Have you come back to listen to my poems?", npc = camel)
    when (options("I'd love to!", "No, thank you.")) {
        1 -> {
            chatPlayer("I'd love to!", facialExpression = FacialExpression.HAPPY)
            chatNpc("That's so kind of you. Which one would you like to hear?", npc = camel)
            chatNpc("'Shall I compare thee to a desert's day' is my finest yet, but I've also composed others.", npc = camel)
            when (options("Listen to 'Shall I compare thee to a desert's day'.", "Listen to 'This Is Just To Say'.")) {
                1 -> desertsDay(camel, interrupt = false)
                else -> justToSay(camel)
            }
        }
        else -> {
            chatPlayer("No, thank you.")
            chatNpc("Ah, well. I shall return to writing poems to Elly's beauty.", npc = camel)
            whatDoesSheThink(camel)
        }
    }
}

suspend fun QueueTask.talkingToMe(camel: Int) {
    chatNpc("Sorry, were you saying something to me?", npc = camel)
    chatPlayer("No, er, nothing important.")
    chatNpc("Never mind, it is unimportant when I have such important matters weighing on my soul.", npc = camel, facialExpression = FacialExpression.SAD)
    chatPlayer("How important can a camel's problems be?")
    chatNpc(
        "Well, you see, there is a camel called Elly. A beautiful, wondrous camel, with hide like spun gold and teeth that shine like an oasis.",
        npc = camel,
    )
    chatPlayer("...I see.", facialExpression = FacialExpression.CONFUSED)
    chatNpc("I've written many poems describing her beauty. Would you like to hear one?", npc = camel)
    chatPlayer("It's all right, I'm...")
    desertsDay(camel, interrupt = true)
}

suspend fun QueueTask.askDung(camel: Npc) {
    val id = camel.id
    chatPlayer("I'm sorry to bother you, but could you spare me a little dung?", facialExpression = FacialExpression.HAPPY)
    chatNpc("Are you serious?", npc = id)
    chatPlayer("Oh yes. If you'd be so kind...")
    chatNpc("Well, just you close your eyes first. I'm not doing it while you're watching me!", npc = id)
    player.openInterface(170, InterfaceDestination.MAIN_SCREEN_FULL)
    wait(2)
    val dung = DynamicObject(DUNG_OBJ, 10, 0, camel.tile)
    world.queue {
        world.spawn(dung)
        wait(50) // 30 seconds
        world.remove(dung)
    }
    wait(2)
    player.openInterface(115, InterfaceDestination.MAIN_SCREEN_FULL)
    wait(2)
    player.closeMainInterface()
    chatNpc("I hope that's what you wanted!", npc = id)
    chatPlayer("Ohhh yes. Lovely.")
}

fun scoopPoop(player: Player) {
    if (!player.inventory.remove(Items.BUCKET, 1).hasSucceeded()) return
    player.inventory.add(Items.UGTHANKI_DUNG, 1)
    player.animate(FILL_BUCKET_ANIM)
    player.message("You scoop up some camel dung into the bucket.")
    if (player.inventory.getItemCount(Items.UGTHANKI_DUNG) >= 28) {
        player.queue { chatPlayer("Phew - that's enough dung.") }
    }
}

fun mabaStarted(player: Player) = QuestStatus.state(player, MABA_SLOT) != QuestStatus.State.NOT_STARTED

CAMEL_IDS.filter { id -> world.definitions.get(NpcDef::class.java, id).options.any { it == "Talk-to" } }.forEach { id ->
    on_npc_option(npc = id, option = "Talk-to") {
        val camel = player.getInteractingNpc()
        player.queue {
            if (!wearsCamulet(player)) {
                insult()
                player.message(
                    when (world.random(2)) {
                        0 -> "The camel turns its head and glares at you."
                        1 -> "The camel spits at you, and you jump back hurriedly.."
                        else -> "The camel tries to stamp on your foot, but you pull it back quickly."
                    },
                )
                return@queue
            }
            when (options("Ask the camel about its dung.", "Say something unpleasant.", "Neither - I'm a polite person.", title = "What would you like to do?")) {
                1 -> if (mabaStarted(player)) askDung(camel) else player.message("Nothing interesting happens.")
                2 -> {
                    insult()
                    if (player.attr[HEARD_POEM] == true) listenTo(id) else talkingToMe(id)
                }
            }
        }
    }
}

on_obj_option(obj = DUNG_OBJ, option = "Pick-up") {
    if (!player.inventory.contains(Items.BUCKET)) {
        player.queue { chatPlayer("I'm not picking that up. I'll need a container...") }
        return@on_obj_option
    }
    scoopPoop(player)
}

on_item_on_obj(obj = DUNG_OBJ, item = Items.BUCKET) { scoopPoop(player) }
