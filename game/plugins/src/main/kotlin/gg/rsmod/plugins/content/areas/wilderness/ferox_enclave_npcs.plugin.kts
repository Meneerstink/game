package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.inter.bank.BankPin
import gg.rsmod.plugins.content.inter.bank.openBank
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface

/**
 * RCV-012 decision 3: the OSRS Ferox Enclave npcs (imported by OsrsNpcImportTool batch "ferox").
 *
 * Spawns: the OSRS Wiki infobox map of each npc ("square" markers are the npc's wander area centre with radius r, "pin" markers are a
 * fixed tile). Not spawned (SOURCE_GAP): the three Refugees (their map is the whole-enclave overview, no tile) and Perdu (map = No).
 * Dialogue: the "Standard dialogue" sections of the OSRS Wiki transcripts, verbatim. Branches for content this server does not have are
 * taken as the wiki's own condition decides: Vet'ion, Callisto and Venenatis do not exist here, so the "with no kills" lines are used;
 * Last Man Standing is parked, so Lisa and Justine use their "not on an official LMS world" lines. Ferox's paid respawn switch uses the
 * owner-approved tile next to the Old Nite pub ([FeroxRespawn], owner answer Q12). BLOCKED (recorded): Marten's Store-axe, the
 * Mercenary's banknote exchange.
 */

// ---- spawns (x, z from the wiki map; plane 0) ----
spawn_npc(npc = Npcs.FEROX, x = 3128, z = 3636, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.SISTER_SCAROPHIA, x = 3128, z = 3636, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.SIGISMUND, x = 3135, z = 3622, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.ZAMORAKIAN_ACOLYTE, x = 3135, z = 3636, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.ZAMORAKIAN_ACOLYTE_14380, x = 3135, z = 3636, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.ZAMORAKIAN_ACOLYTE_14381, x = 3135, z = 3636, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.SKULLY, x = 3139, z = 3626, walkRadius = 0, direction = Direction.SOUTH)
// Owner 2026-09-23: nobody may walk through any Skully - his tile is solid like the bank Skullys (SkullyRoster.blockTile).
on_world_init { gg.rsmod.plugins.content.mechanics.pvp.SkullyRoster.blockTile(world, Tile(3139, 3626, 0)) }
spawn_npc(npc = Npcs.BANKER_FEROX_ENCLAVE, x = 3135, z = 3629, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.MERCENARY_FEROX_ENCLAVE, x = 3130, z = 3629, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.ANDROS_MAI, x = 3152, z = 3644, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.CAMARST, x = 3152, z = 3644, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.DERSE_VENATOR, x = 3152, z = 3644, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.PHABELLE_BILE, x = 3152, z = 3644, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.MARTEN, x = 3153, z = 3630, walkRadius = 0, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.JUSTINE, x = 3142, z = 3635, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.LISA, x = 3142, z = 3635, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.WIZARD_LMS_14398, x = 3142, z = 3642, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.WIZARD_LMS_14399, x = 3142, z = 3642, walkRadius = 4, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.WIZARD_LMS_14400, x = 3142, z = 3642, walkRadius = 4, direction = Direction.SOUTH)

// ---- Ferox ----
on_npc_option(npc = Npcs.FEROX, option = "talk-to") {
    player.queue { ferox(this) }
}

suspend fun ferox(it: QueueTask) {
    it.chatNpc("Welcome wanderer.", wrap = true)
    val paid = gg.rsmod.plugins.content.areas.deathsoffice.RespawnPoints.owns(it.player, gg.rsmod.plugins.content.areas.deathsoffice.RespawnPoints.Point.FEROX)
    when (it.options("What is this place?", if (paid) "Ask about your respawn point." else "What can you do for me?", "Nevermind.")) {
        1 -> feroxWhatIsThisPlace(it)
        2 -> if (paid) feroxRespawnPoint(it) else feroxWhatCanYouDo(it)
        3 -> {
            it.chatPlayer("Never mind, I'm just looking around.", wrap = true)
            it.chatNpc("So be it.", wrap = true)
        }
    }
}

// Transcript:Ferox "If the player has paid Ferox to switch their respawn point before". ADAPTED: "Lumbridge" is this server's home
// respawn (gameContext.home, itself inside the Enclave); the lines stay verbatim.
suspend fun feroxRespawnPoint(it: QueueTask) {
    it.chatPlayer("Can I talk to you about my respawn location?", wrap = true)
    val soul = if (it.player.appearance.gender == Gender.MALE) "man's" else "woman's"
    if (!FeroxRespawn.isActive(it.player)) {
        it.chatNpc("Would you like to respawn back in Ferox Enclave again?", wrap = true)
        when (it.options("Please switch my respawn back to the Enclave.", "No, I don't want to respawn in the Enclave")) {
            1 -> {
                it.chatPlayer("Please switch my respawn back to the Enclave.", wrap = true)
                it.chatNpc("The Wilderness takes a toll on a $soul soul. Are you sure you can handle it?", wrap = true)
                when (it.options("Yes, switch my respawn to the Enclave.", "No, maybe another time.")) {
                    1 -> {
                        it.chatPlayer("Yes, switch my respawn to the Enclave.", wrap = true)
                        FeroxRespawn.activate(it.player)
                        it.chatNpc("Fair enough, it has been done.", wrap = true)
                    }
                    2 -> {
                        it.chatPlayer("No, maybe another time.", wrap = true)
                        it.chatNpc("Understandable.", wrap = true)
                    }
                }
            }
            2 -> {
                it.chatPlayer("No, I don't want to respawn in the Enclave.", wrap = true)
                it.chatNpc("Understandable.", wrap = true)
            }
        }
        return
    }
    it.chatNpc("How are you finding our sanctuary?", wrap = true)
    when (it.options("Please switch my respawn back to Lumbridge.", "It's working for me so far, thanks.")) {
        1 -> {
            it.chatPlayer("Please switch my respawn back to Lumbridge.", wrap = true)
            it.chatNpc("Can't say I blame you, the Wilderness starts to wear down the soul after a while.", wrap = true)
            it.chatNpc("But are you sure? Come and see me if you want to respawn in our Enclave again; I won't need any more money from you.", wrap = true)
            when (it.options("Yes, switch my respawn to Lumbridge.", "No, I'll keep the Enclave respawn.")) {
                1 -> {
                    it.chatPlayer("Yes, switch my respawn to Lumbridge.", wrap = true)
                    FeroxRespawn.deactivate(it.player)
                    it.chatNpc("Done.", wrap = true)
                }
                2 -> {
                    it.chatPlayer("No, I'll keep the Enclave respawn.", wrap = true)
                    it.chatNpc("Fine.", wrap = true)
                }
            }
        }
        2 -> it.chatPlayer("It's working for me so far, thanks.", wrap = true)
    }
}

suspend fun feroxWhatIsThisPlace(it: QueueTask) {
    it.chatPlayer("What is this place?", wrap = true)
    it.chatNpc("You're surrounded by a relic of a long lost kingdom.", wrap = true)
    it.chatPlayer("What happened here?", wrap = true)
    it.chatNpc("Oh, I couldn't really tell you that.", wrap = true)
    it.chatNpc("This place has sat in ruin for an age, slowly destroyed by the harsh climate of the Wilderness.", wrap = true)
    it.chatPlayer("If it's a ruin, what's the point in being here?", wrap = true)
    it.chatNpc("I search for knowledge. I want to know what happened to this part of the world.", wrap = true)
    it.chatNpc("Whilst looking for answers, I slowly turned this broken town into a safe haven for travellers. Welcome to Ferox Enclave.", wrap = true)
    it.chatPlayer("Quite a dangerous place for a town, don't you think?", wrap = true)
    it.chatNpc("It is, but that's why it's needed! Look around, you can see how many others share my opinion.", wrap = true)
    it.chatNpc("You'll find yourself quite safe inside my enclave - the protective barriers should keep any troublemakers out.", wrap = true)
    it.chatNpc(
        "I will warn you though, if you've had Teleblock cast upon you, I will not allow you back into the Enclave. You'd be a danger to the others gathered here.",
        wrap = true,
    )
    when (it.options("What can you do for me?", "I'll be heading off then.")) {
        1 -> feroxWhatCanYouDo(it)
        2 -> {
            it.chatPlayer("Thanks for the information! I'll take a look around.", wrap = true)
            it.chatNpc("Tread carefully my friend.", wrap = true)
        }
    }
}

suspend fun feroxWhatCanYouDo(it: QueueTask) {
    it.chatPlayer("What can you do for me?", wrap = true)
    it.chatNpc("Well, if you'd like, I can make it so you respawn in our Enclave. It'll put you right in the heart of all the chaos that surrounds us.", wrap = true)
    when (it.options("Tell me more.", "That doesn't interest me.")) {
        1 -> {
            it.chatPlayer("Tell me more.", wrap = true)
            it.chatNpc(
                "This is how it'll work: When you die, you'll respawn right here in Ferox Enclave, just outside the pub we've set up. You will lose your items as normal of course.",
                wrap = true,
            )
            it.chatNpc(
                "I won't be able to do anything if you die somewhere like Castle Wars or the Emir's Arena; they have their own magic to keep you safe.",
                wrap = true,
            )
            // Owner 2026-09-26: respawn points are sold by Death in his office now (500,000 coins, once) - RespawnPoints.
            it.chatNpc(
                "It'll cost you though! Transporting you here is no easy task - Death himself arranges it these days. " +
                    "Speak to him in his office: ${String.format(java.util.Locale.US, "%,d", gg.rsmod.plugins.content.areas.deathsoffice.RespawnPoints.PRICE)} coins, a one time fee of course.",
                wrap = true,
            )
        }        2 -> {
            it.chatPlayer("That doesn't interest me.", wrap = true)
            it.chatNpc("Come find me if you change your mind.", wrap = true)
        }
    }
}

// ---- Sigismund ----
on_npc_option(npc = Npcs.SIGISMUND, option = "talk-to") {
    player.queue {
        chatPlayer("Hello!", wrap = true)
        chatNpc("Can I help you?", wrap = true)
        chatPlayer("I was just curious as to what you do around here.", wrap = true)
        chatNpc("I protect other travellers from monsters, both animal and human kind.", wrap = true)
        chatPlayer("Human? Can't say I've seen many human shaped monsters in the Wilderness.", wrap = true)
        chatNpc("When you've been in the Wilderness as long as I have, you learn that not all monsters show themselves on the outside.", wrap = true)
        chatPlayer("Uhm... alright then, I'll leave you to your duties.", wrap = true)
        chatNpc("Watch yourself out there, it can be hard to tell friend from foe in these lands.", wrap = true)
    }
}

// ---- Zamorakian Acolytes (one of three dialogues at random) ----
listOf(Npcs.ZAMORAKIAN_ACOLYTE, Npcs.ZAMORAKIAN_ACOLYTE_14380, Npcs.ZAMORAKIAN_ACOLYTE_14381).forEach { acolyte ->
    on_npc_option(npc = acolyte, option = "talk-to") {
        player.queue {
            when (world.random(2)) {
                0 -> {
                    chatPlayer("What brings you all the way out into the Wilderness?", wrap = true)
                    chatNpc("The hunt.", wrap = true)
                    chatPlayer("Oh... you partake in that?", wrap = true)
                    chatNpc("Not the hunt for other beings! The hunt for knowledge.", wrap = true)
                    chatPlayer("Knowledge? Can't say I've seen much of that around here. In case you didn't notice, it's all ruins.", wrap = true)
                    chatNpc("Maybe for someone like you, but to us followers of Zamorak, there's relics of his mighty power everywhere.", wrap = true)
                    chatPlayer("Isn't it a little dangerous to be looking for knowledge out here?", wrap = true)
                    chatNpc("That doesn't bother us. Our lord will protect us, we hunt in service of him.", wrap = true)
                    chatPlayer("I'll leave you to it then.", wrap = true)
                }
                1 -> {
                    chatPlayer("You're a lot calmer than the other Zamorakian followers I've come across in the Wilderness.", wrap = true)
                    chatNpc("We only search for answers.", wrap = true)
                    chatPlayer("Don't think you'll find much in these ruins.", wrap = true)
                    chatNpc("Be that as it may, it is our duty to honour our god and hunt for knowledge.", wrap = true)
                    chatPlayer("I'll leave you to it then.", wrap = true)
                }
                else -> {
                    chatNpc("All power to the almighty Zamorak.", wrap = true)
                    chatPlayer("I take it you're with the others dressed in red.", wrap = true)
                    chatNpc("We are here in service of the only true god.", wrap = true)
                    chatPlayer("I'll leave you to it then.", wrap = true)
                }
            }
        }
    }
}

// ---- Banker (Ferox Enclave): Bank here, Collect via banker_collect (cache options) ----
on_npc_option(npc = Npcs.BANKER_FEROX_ENCLAVE, option = "Bank", lineOfSightDistance = 2) {
    player.openBank()
}

on_npc_option(npc = Npcs.BANKER_FEROX_ENCLAVE, option = "talk-to", lineOfSightDistance = 2) {
    player.queue {
        chatNpc("Oh great, another customer. What do you want?", wrap = true)
        when (
            options(
                "I'd like to access my bank account, please.",
                "I'd like to check my PIN settings.",
                "I'd like to collect items.",
                "What are you doing out here?",
            )
        ) {
            1 -> player.openBank()
            2 -> BankPin.manage(player)
            3 -> GrandExchangeInterface.openCollectionBox(player)
            4 -> {
                chatPlayer("What are you doing out here?", wrap = true)
                chatNpc("I used to work in the East Varrock branch. Then a request came in to set up a bank in this new town.", wrap = true)
                chatNpc("The other clerks and myself pulled straws and I was unlucky enough to be sent to this stinking death trap.", wrap = true)
                chatNpc("Now I'm stuck here, ferrying items back and forth to the bank's safe in Varrock for people like you.", wrap = true)
                chatPlayer("That doesn't sound very fun, but you're doing a great service for us adventurers.", wrap = true)
                chatNpc("Tell that to my back and legs, I'm exhausted from carrying all this junk back and forth.", wrap = true)
            }
        }
    }
}

// ---- Camarst ----
on_npc_option(npc = Npcs.CAMARST, option = "talk-to") {
    player.queue {
        chatNpc("Welcome my friend to the Old Nite! What can I do for you?", wrap = true)
        when (options("Got anything strong to drink?", "Your pub looks a bit rough.", "Just browsing...")) {
            1 -> {
                chatPlayer("Got anything strong to drink?", wrap = true)
                chatNpc("Of course I do! I can offer you a big tasty beer, yours for only two coins.", wrap = true)
                if (player.inventory.getItemCount(Items.COINS_995) < 2) {
                    chatPlayer("Oh dear, I don't seem to have enough money.", wrap = true)
                    chatNpc("No worries, there's a bank just around the corner if you want to grab some coin.", wrap = true)
                } else {
                    chatPlayer("Sure, I'll take a beer.", wrap = true)
                    if (player.inventory.remove(Items.COINS_995, 2).hasSucceeded()) {
                        player.inventory.add(Items.BEER)
                    }
                }
            }
            2 -> {
                chatPlayer("Your pub looks a bit rough.", wrap = true)
                chatNpc(
                    "It's all part of the aesthetic! Adventurers who've just returned from the fight of their lives don't want a fancy la-de-da establishment like you'd find down south.",
                    wrap = true,
                )
                chatNpc("They want somewhere to relax and have some hearty ale.", wrap = true)
                chatPlayer("And that's what you offer with your broken tables and glass covered floors?", wrap = true)
                chatNpc("Aye it is, because here at the Old Nite, your safety is our lowest priority!", wrap = true)
            }
            3 -> chatPlayer("Just browsing...", wrap = true)
        }
    }
}

// ---- Sister Scarophia ----
on_npc_option(npc = Npcs.SISTER_SCAROPHIA, option = "talk-to") {
    player.queue {
        chatNpc("Welcome to my little chapel. Can I help you?", wrap = true)
        scarophiaOptions(this)
    }
}

suspend fun scarophiaOptions(it: QueueTask) {
    when (it.options("Why are you here?", "So whose sister are you?", "I'm fine, thanks.")) {
        1 -> {
            it.chatPlayer("Why are you here?", wrap = true)
            it.chatNpc(
                "I built this chapel here so that fighters could come and pray before battle. A fighter who ignores their spiritual needs may as well have stabbed themselves in the foot.",
                wrap = true,
            )
            it.chatNpc(
                "People keep claiming they're not interested in religion, but as soon as they're under attack they're yelling for 'Protect from Melee'. Sad, really.",
                wrap = true,
            )
            it.chatNpc("So is there something I can do for you?", wrap = true)
            scarophiaOptions(it)
        }
        2 -> {
            it.chatPlayer("So whose sister are you?", wrap = true)
            it.chatNpc("Ahahahahahaha, very funny! With jokes like that, you should become a banker.", wrap = true)
            it.chatNpc(
                "But no I am a sister to the broken and down trodden, I heard talk of this enclave being built and knew that my skills could be put to good use.",
                wrap = true,
            )
            it.chatNpc("I've found a worthy cause here running my little chapel, so I'm happy enough. Now, can I help you with something?", wrap = true)
            scarophiaOptions(it)
        }
        3 -> it.chatPlayer("I'm fine, thanks.", wrap = true)
    }
}

// ---- Marten ----
on_npc_option(npc = Npcs.MARTEN, option = "talk-to") {
    player.queue {
        chatPlayer("Hello there.", wrap = true)
        chatNpc("Aye? Who's that, what do you want?", wrap = true)
        when (options("Who are you?", "Can you teach me about canoes?")) {
            1 -> {
                chatPlayer("Who are you?", wrap = true)
                chatNpc("Marten.", wrap = true)
                chatPlayer("Oh, straight to the point.", wrap = true)
                chatNpc("I got betta' things to do than waste my time talkin' to you.", wrap = true)
                chatPlayer("You're rather rude aren't you?", wrap = true)
                chatNpc("Fine, wanna know more 'bout canoes?", wrap = true)
                when (options("Yes", "No")) {
                    1 -> martenCanoes(this)
                    2 -> {
                        chatPlayer("No thanks, not from you at least.", wrap = true)
                        chatNpc("Then get out of here and stop botherin' me.", wrap = true)
                    }
                }
            }
            2 -> martenCanoes(this)
        }
    }
}

suspend fun martenCanoes(it: QueueTask) {
    it.chatPlayer("Could you teach me about canoes?", wrap = true)
    val level = it.player.skills.getMaxLevel(Skills.WOODCUTTING)
    if (level < 12) {
        it.chatNpc(
            "Hahaha! Sorry mate, you'll not be able to make any canoes outta that tree there. You'll need to get a bit more skilled at the craft of Woodcuttin'.",
            wrap = true,
        )
        it.chatNpc("But once you get a bit betta', you'll be able to make a canoe and ride the river.", wrap = true)
        return
    }
    it.chatNpc("It's not 'ard mate, you just gotta chop that tree o'er there down.", wrap = true)
    it.chatNpc("Then cut it down into a canoe. So simple a gobbo could do it.", wrap = true)
    when {
        level < 27 -> {
            it.chatNpc("But you got no chance of turning it into a beautiful canoe.", wrap = true)
            it.chatPlayer("Why's that?", wrap = true)
            it.chatNpc("I can tell by lookin' at ya! You ain't a woodcutter, most you'd be able to do is sail to the next stop on a log.", wrap = true)
        }
        level < 42 -> {
            it.chatNpc("Hmm, s'pose you could make a decent canoe with your skills. you'll be able to make a dugout, I reckons.", wrap = true)
            it.chatPlayer("How far will I be able to go in a dugout canoe?", wrap = true)
            it.chatNpc("Err, probably about two stops before it sinks on ya.", wrap = true)
        }
        level < 57 -> {
            it.chatPlayer("Could that take me back to Lumbridge?", wrap = true)
            it.chatNpc("Pah, I don't think so. You'll be lucky to make it past Varrock.", wrap = true)
        }
        else -> {
            it.chatNpc(
                "Well bless me axe, you're almost as good a woodchopper as meself! You'll have no issue craftin' the best of the best, the waka canoe.",
                wrap = true,
            )
            it.chatNpc("One of those beauties can take you up further in the Wilderness. Beautiful place that.", wrap = true)
            it.chatPlayer("Great, thank you.", wrap = true)
        }
    }
}

// ---- Andros Mai (no Vet'ion here: "with no Vet'ion kills") ----
on_npc_option(npc = Npcs.ANDROS_MAI, option = "talk-to") {
    player.queue {
        chatPlayer("Well met, fellow adventurer.", wrap = true)
        chatNpc(
            "Adventurer? I am no mere Adventurer, I am Andros Mai, brother of the Holy Order of Saradomin. You would do well to greet me with respect.",
            wrap = true,
        )
        chatPlayer("Andros Mai? Sorry, but I can't say I've heard of you I'm afraid.", wrap = true)
        chatNpc("I wouldn't have expected someone of your... stature to know of me.", wrap = true)
        chatNpc("Tell me, what do you know of the vile Vet'ion?", wrap = true)
        chatPlayer("Nothing, but I have a feeling you're about to fill me in.", wrap = true)
        chatNpc("Vet'ion was a devout follower of the heretical god Zamorak. It is the will of my order and my duty as a follower of the true god Saradomin to end his tyranny.", wrap = true)
        chatNpc("After many years in my order's libraries, I stumbled upon an old tome which mentioned Vet'ion's final resting place, which I believe to be his source of power.", wrap = true)
        chatNpc("His very existence is an insult to my lord Saradomin and I will not suffer it to exist any longer.", wrap = true)
        chatPlayer("Then why are you waiting here? Seems to me that you don't want to face Vet'ion.", wrap = true)
        chatNpc("I have no fear of Vet'ion!", wrap = true)
        chatNpc("I am merely meditating and preparing for the battle. I need to focus my mind before going out to find his resting place.", wrap = true)
        chatPlayer("It's okay, sometimes I pretend to be busy when there's something I don't want to do, too.", wrap = true)
        chatNpc("This conversation is over! Leave me before I lose my temper.", wrap = true)
    }
}

// ---- Derse Venator (no Callisto here: "with no Callisto kills"; Craw's bow and Fangs of Venenatis exist) ----
on_npc_option(npc = Npcs.DERSE_VENATOR, option = "talk-to") {
    player.queue {
        val bow = player.inventory.contains(Items.CRAWS_BOW_U)
        val fangs = player.inventory.contains(Items.FANGS_OF_VENENATIS)
        if (!bow && !fangs) {
            derseCombat(this)
            return@queue
        }
        when (options("You look ready for combat.", "Ask about rare item.")) {
            1 -> derseCombat(this)
            2 -> derseRareItem(this, bow, fangs)
        }
    }
}

suspend fun derseCombat(it: QueueTask) {
    it.chatPlayer("You look ready for combat.", wrap = true)
    it.chatNpc("Aye, indeed I am.", wrap = true)
    it.chatPlayer("What brings you to this place? I can't imagine anyone coming here by choice.", wrap = true)
    it.chatNpc("You're right, I did not come here by choice. I came here for vengeance.", wrap = true)
    it.chatPlayer("Vengeance? Against whom? Did someone take your gardening equipment?", wrap = true)
    it.chatNpc("My mission is not against another man and I will not have it mocked.", wrap = true)
    it.chatPlayer("Okay, sorry, I was just trying to lighten the mood. Care to tell me what it is against then?", wrap = true)
    it.chatNpc("Callisto, the great bear of the wilderness.", wrap = true)
    it.chatPlayer("Can't say I've come across it before.", wrap = true)
    it.chatNpc("I'm not surprised, you'd do well to avoid him. Callisto is a great bear, his strength and size enhanced by the corruption of the Wilderness.", wrap = true)
    it.chatPlayer("Why are you so intent on taking him down?", wrap = true)
    it.chatNpc("Callisto killed my father. After hearing of a great beast in the heart of the Wilderness, he took it upon himself to bring him down.", wrap = true)
    it.chatNpc("He left our family home in Hosidius to hunt it down and...", wrap = true)
    it.chatNpc("... I haven't seen my father since. I swore I would avenge him and have dedicated my whole life to training for that moment.", wrap = true)
    it.chatPlayer("What happens after? When you do take Callisto down that is.", wrap = true)
    it.chatNpc("Oh... uh... I never thought about that...", wrap = true)
    it.chatNpc("My whole life has been spent preparing to avenge my father, I never thought about anything else...", wrap = true)
    it.chatPlayer("You seem like you've got a lot to think about then. I'll leave you to it.", wrap = true)
}

suspend fun derseRareItem(
    it: QueueTask,
    bow: Boolean,
    fangs: Boolean,
) {
    it.chatPlayer("Hey. Do you know anything about this?", wrap = true)
    if (!bow) {
        it.messageBox("You show the Fangs of Venenatis to Derse Venator.")
        it.chatNpc("Those look like strong fangs. You could probably attach them to a bow to make it stronger.", wrap = true)
        it.chatPlayer("Interesting! I'll be on the lookout for a suitable bow.", wrap = true)
        return
    }
    it.messageBox("You show the bow to Derse Venator.")
    it.chatNpc("Ah, a fellow marksman. That's a unique bow you have there.", wrap = true)
    it.chatPlayer("Do you know anything about it?", wrap = true)
    it.chatNpc("I've done my fair bit of fletching in my day. I'd say this bow can be improved upon.", wrap = true)
    it.chatNpc("See those etches on it? You could put something sharp in there to improve the bow's strength.", wrap = true)
    if (!fangs) {
        it.chatPlayer("Interesting! I'll be on the lookout for a powerful enhancement like that.", wrap = true)
        return
    }
    it.chatPlayer("Perhaps I could use this?", wrap = true)
    it.messageBox("You show the Fangs of Venenatis to Derse Venator.")
    it.chatNpc("Yes. Those look like they could be attached to the weapon.", wrap = true)
    it.chatPlayer("Could you combine them?", wrap = true)
    it.chatNpc("Sure I can. For the right price.", wrap = true)
    it.chatPlayer("Sigh. How much?", wrap = true)
    it.chatNpc("500,000 coins would do nicely.", wrap = true)
    when (it.options("Yes", "No", title = "Pay 500,000 coins to combine the fangs and bow?")) {
        1 -> {
            val inventory = it.player.inventory
            if (inventory.getItemCount(Items.COINS_995) < 500_000) {
                it.chatPlayer("I don't have the money for that.", wrap = true)
                it.chatNpc("Feel free to come back when you do.", wrap = true)
                return
            }
            if (!inventory.contains(Items.CRAWS_BOW_U) || !inventory.contains(Items.FANGS_OF_VENENATIS)) return
            inventory.remove(Items.COINS_995, 500_000)
            inventory.remove(Items.CRAWS_BOW_U, 1)
            inventory.remove(Items.FANGS_OF_VENENATIS, 1)
            inventory.add(Items.WEBWEAVER_BOW_U)
            it.itemMessageBox("Derse Venator combines the items and gives you the Webweaver bow (u).", item = Items.WEBWEAVER_BOW_U)
            it.chatNpc("There we go! All done.", wrap = true)
        }
        2 -> it.chatPlayer("No thanks.", wrap = true)
    }
}

// ---- Phabelle Bile (no Venenatis here: "with no Venenatis kills"; Thammaron's sceptre and Skull of Vet'ion do not exist) ----
on_npc_option(npc = Npcs.PHABELLE_BILE, option = "talk-to") {
    player.queue {
        chatPlayer("Wow, didn't expect to find a scientist out here.", wrap = true)
        chatNpc("I'm out here on a mission, do you think I chose to hang around these lowlifes?", wrap = true)
        chatPlayer("I'm not one to judge.", wrap = true)
        chatPlayer("What is this 'mission' you mentioned?", wrap = true)
        chatNpc("I'm out here to conduct research, I've been told a giant spider lurks in the heart of the Wilderness.", wrap = true)
        chatNpc("Her venom is priceless and it would aid me greatly in my research.", wrap = true)
        chatPlayer("Research? What could you possibly want with a giant spider's venom?", wrap = true)
        chatNpc("Venenatis is the last of her kind. Lots of giant spiders used to dwell deep in the mountains until the savage dwarves killed them all.", wrap = true)
        chatNpc("She is the last one left and I must have a sample of her venom.", wrap = true)
        chatNpc("What I want with the specimen however, is none of your business!", wrap = true)
        chatPlayer("Woah sorry, didn't mean to touch a nerve. Fighting a giant spider sounds like a great idea.", wrap = true)
        chatNpc("I've tried to put it off for as long as I could, but this is the final component needed for my research.", wrap = true)
        chatPlayer("Then what are you waiting around here for? Go out there and fight her.", wrap = true)
        chatNpc("I would go out and fight her now, but I must admit, I am also a little scared. I've never been in a fight before.", wrap = true)
        chatPlayer("I'm sure you'll be alright, it's just a gigantic spider!", wrap = true)
        chatNpc("Thanks for your words of confidence...", wrap = true)
    }
}

// ---- Last Man Standing is parked: Lisa and Justine use their "not on an official LMS world" lines ----
listOf(Npcs.LISA, Npcs.LISA_14397).forEach { lisa ->
    on_npc_option(npc = lisa, option = "talk-to") {
        player.queue { chatNpc("I'm afraid you can't take part in Last Man Standing on this world.", wrap = true) }
    }
}

listOf("talk-to", "trade").forEach { option ->
    on_npc_option(npc = Npcs.JUSTINE, option = option) {
        player.queue { chatNpc("Sorry, shop's closed.", wrap = true) }
    }
}
