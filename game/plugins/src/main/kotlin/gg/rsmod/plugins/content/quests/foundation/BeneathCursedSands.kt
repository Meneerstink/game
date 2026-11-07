package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.ToaIds
import gg.rsmod.plugins.content.quests.red

/**
 * N462 (owner 2026-11-06 16:5x): Beneath Cursed Sands, the way into the Tombs of Amascut, as a SHORT quest like the other foundation
 * quests ('die moet ook werken maar niet volledig net als de andere questen die we hebben'). Every line below is OSRS: OSRS Wiki
 * Transcript:Beneath Cursed Sands (oldid 15372813, ChatGPT-A N462-notities\toa\sources\wiki) - the opening at Jamila's stall, Maisa's
 * briefing at her camp, the trip to the necropolis excavation, the Head Menaphite Guard fight (OSRS npc 11529, combat 174) and Maisa's
 * debrief; the long Ullek / Nardah / Cure Me Pox / Menaphite Akh blocks are left out (GPT-A 60-quest.txt SHORT proposal 1-5). The
 * closing lines and both rewards are Maisa's from the OSRS ending (keris from Osman, circlet from the High Priest).
 *
 * OSRS Wiki Beneath Cursed Sands: 2 quest points, 50,000 Agility experience (since 30 November 2022), Keris partisan, Circlet of
 * water (uncharged), access to the Tombs of Amascut; recommended 85 Combat. Owner E2 (2026-11-06 19:1x): every account that had the
 * old always-complete list entry keeps it ([migrate]); only new accounts play this version.
 */
object BeneathCursedSands : ShortQuest(
    name = "Beneath Cursed Sands",
    startPoint = "Talk to Jamila at her stall in Sophanem.",
    rewards = "2 Quest Points, 50,000 Agility XP, a Keris partisan, a Circlet of water and access to the Tombs of Amascut.",
    pointReward = 2,
    questId = OsrsQuestVarps.BENEATH_CURSED_SANDS,
    slot = OsrsQuestSlots.BENEATH_CURSED_SANDS,
    completedValue = 10,
    usesVarbits = false,
    startNpc = JAMILA,
    startNpcName = "Jamila",
    startPlace = "her stall in Sophanem",
    startLocation = SOPHANEM_STALL,
) {
    /** OSRS Wiki infobox: Recommended 85 Combat. */
    override val recommendation = Recommendation.combat(85)

    override val reward =
        QuestReward(
            icon = KERIS_PARTISAN ?: -1,
            xp = listOf(Skills.AGILITY to 50_000.0),
            items = listOfNotNull(KERIS_PARTISAN?.let { it to 1 }, CIRCLET_OF_WATER?.let { it to 1 }),
            lines =
                listOf(
                    "50,000 Agility XP",
                    "Keris partisan",
                    "Circlet of water",
                    "Access to the Tombs of Amascut",
                ),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc("Hello there. Looking to buy some of my wares? I also have a special item in stock that I reckon you'll love!", npc = JAMILA)
        chatPlayer("What's this special item?", facialExpression = FacialExpression.THINKING)
        chatNpc("Oh it's fantastic! It's an exclusive item just for you! Consider it a thank you for all you've done for our city.", npc = JAMILA)
        if (player.combatLevel < 85) {
            messageBox("Before starting this quest, be aware that your combat level is lower than the recommended level of 85.")
        }
        if (options("Yes.", "No.", title = "Start the Beneath Cursed Sands quest?") != 1) {
            chatPlayer("Thanks for the offer, but I'll pass.")
            chatNpc("Oh, I see. Well come back if you change your mind.", npc = JAMILA)
            return false
        }
        chatPlayer("Okay, let's have a look.")
        messageBox("Jamila shows you a gold ring.")
        chatPlayer("A ring?", facialExpression = FacialExpression.THINKING)
        chatNpc("Isn't it wonderful! Here, try it on!", npc = JAMILA)
        messageBox("Jamila tries to put the ring on your finger. As she does, she slips you a message.")
        chatNpc("Ah, it seems the ring is too small. Never mind.", npc = JAMILA)
        chatPlayer("Wait... what?", facialExpression = FacialExpression.CONFUSED)
        chatNpc("Well, time for me to get on with my work. Come back soon.", npc = JAMILA)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = MAISA,
                journal = listOf("Jamila slipped me a message. I should meet ${red("Maisa")} at her", "camp east of ${red("Sophanem")}."),
                done = listOf("I met Maisa at her camp east of Sophanem."),
                place = "Maisa's camp",
                location = MAISA_CAMP,
                talk = {
                    chatNpc("Good, you made it.")
                    chatPlayer("I know you! You're Maisa, that spy from Al Kharid. But you're meant to be in Menaphos!", facialExpression = FacialExpression.SHOCK)
                    chatNpc("I told Osman I was done with that posting, and I meant it.")
                    chatPlayer("But how did you get out of the city?", facialExpression = FacialExpression.THINKING)
                    chatNpc("The same way I helped those citizens of yours. Even with the quarantine, Menaphos are still sending their dead to Sophanem.")
                    chatPlayer("So you hid yourself among them? I see. Why are you out here though? And why did you send me that message?", facialExpression = FacialExpression.THINKING)
                    chatNpc("Because there's something very strange going on. I intend to work out what, but I need your help.")
                    chatPlayer("Right... so what are these strange happenings?")
                    chatNpc("Citizens from Menaphos have been disappearing. Guards as well. They've been leaving the city, despite the quarantine.", facialExpression = FacialExpression.WORRIED)
                    chatPlayer("They've been bypassing the quarantine? How?", facialExpression = FacialExpression.THINKING)
                    chatNpc("No idea, but I have managed to find out where they've been going.")
                    chatPlayer("Where?")
                    chatNpc("South of here, to the old necropolis. They're excavating something there, but I don't know what. There's also something strange going on with them.")
                    chatPlayer("What do you mean?")
                    chatNpc("I went to this excavation of theirs to have a look around, and the way they're acting is very unusual. It's like nothing I've seen before.")
                    chatPlayer("Interesting. I think I need to see this for myself.")
                    chatNpc("Well let me know when you're ready, and I'll take you down there.")
                    true
                },
            ),
            QuestStep(
                npc = MAISA,
                journal = listOf("I should go with ${red("Maisa")} to the excavation at the ${red("necropolis")}", "and defeat the ${red("Head Menaphite Guard")} (level 174)."),
                done = listOf("I defeated the Head Menaphite Guard at the necropolis."),
                place = "the necropolis",
                location = HEAD_GUARD_TILE,
                fight =
                    QuestFight(
                        npc = HEAD_MENAPHITE_GUARD ?: -1,
                        name = "Head Menaphite Guard",
                        level = 174,
                        tile = HEAD_GUARD_TILE,
                        shout = "Die...",
                    ),
                tip = "The Head Menaphite Guard hits with stab attacks (max 16) and mocks overhead prayers.",
                talk = {
                    if (options("Let's go.", "I need to do something first.") != 1) {
                        chatPlayer("I need to do something first.")
                        chatNpc("Well don't take too long!")
                        return@QuestStep false
                    }
                    chatPlayer("Let's go.")
                    chatPlayer("How weird. Can we get a closer look?", facialExpression = FacialExpression.THINKING)
                    chatNpc("Should be able to. When I came to investigate before, I was able to walk right up to them. They didn't even seem to care. It was like they were in some sort of trance.")
                    chatPlayer("I'm going to have a look around.")
                    chatNpc("I'll keep watch here. Try to keep a low profile.")
                    chatPlayer("I'll try, but low profile isn't really one of my specialties.")
                    chatNpc("Oh good. I'll prepare for violence then.")
                    true
                },
            ),
            QuestStep(
                npc = MAISA,
                journal = listOf("I should tell ${red("Maisa")} at her camp what I found", "at the necropolis."),
                done = listOf("Maisa opened the way into the Tombs of Amascut."),
                place = "Maisa's camp",
                location = MAISA_CAMP,
                talk = {
                    chatNpc("Nice job keeping things low profile.")
                    chatPlayer("To be fair, I did warn you.")
                    chatNpc("I suppose. Were you able to discover anything?")
                    chatPlayer("Sort of. I still don't know why these people are here, but I do know who sent them.")
                    chatNpc("Who?")
                    chatPlayer("Amascut, the Devourer.")
                    chatNpc("What? The goddess? You're joking!", facialExpression = FacialExpression.SHOCK)
                    chatNpc("Speaking of Osman, that reminds me.")
                    chatNpc("He left this for you.")
                    messageBox("Maisa shows you a spear with a keris on the end.")
                    chatNpc("Anyway, he wasn't the only one with something for you. This is from the High Priest.")
                    messageBox("Maisa shows you an enchanted circlet.")
                    chatPlayer("I'm sure I'll be able to put it to good use.")
                    chatNpc("The citizens should hopefully be nearly done unblocking the entrance to Osmumten's Tomb. I'm going to head down there.")
                    chatNpc("As for you, I'd say you've earnt a small break. Take whatever time you need to prepare, then meet me at the tomb once you're ready.")
                    chatPlayer("Alright, I'll see you soon. Good luck.")
                    true
                },
            ),
        )

    /** Owner E2: the old list entry (always complete, value 1) is kept as completed - once per account, before the first quest sync. */
    val MIGRATED = AttributeKey<Boolean>(persistenceKey = "bcs_short_migrated")

    fun migrate(player: Player) {
        if (player.attr[MIGRATED] == true) return
        player.attr[MIGRATED] = true
        val value = player.getVarp(questId)
        if (value in 1 until completedValue) {
            player.setVarp(questId, completedValue)
            player.attr[FoundationRewards.rewardedKey(this)] = true
        }
    }
}

/** Jamila at her craft stall in Sophanem (spawns_13099; OSRS Wiki Jamila map 3311,2780). 667 npc 5268 = Jamila (Talk-to, Trade); 5267 is
 * the 667 multi-npc on varbit 3275 (0 = nothing, 1 = 5268) that nothing sets - she was invisible and had no options (07-11 boot crash). */
const val JAMILA = gg.rsmod.plugins.api.cfg.Npcs.JAMILA

/** OSRS Wiki Beneath Cursed Sands startmap 3309,2779; Jamila's stall. */
val SOPHANEM_STALL = Tile(3311, 2779, 0)

/** OSRS Wiki Maisa (Beneath Cursed Sands version): campsite east of Sophanem, map 3378,2794. */
val MAISA_CAMP = Tile(3378, 2794, 0)

/** The fight at the necropolis excavation, before the pyramid entrance (toa_necropolis.plugin.kts OUTSIDE 3358,2709,1). ONZEKER: tile checked in the test client. */
val HEAD_GUARD_TILE = Tile(3358, 2706, 1)

/** Local ids of the OSRS imports (data/cfg/toa/ids.tsv): Head Menaphite Guard 11529, Keris partisan 25979, Circlet of water (uncharged) 26967. */
val HEAD_MENAPHITE_GUARD: Int? get() = ToaIds.npc(11529)

/** OSRS Maisa of Beneath Cursed Sands (npc 11474 BCS_MAISA_VIS = model 44185, head 44760; imported as the identical ToA Maisa 11807
 *  TOA_MAISA_VIS). 07-11 test client: the 667 npc 5281 was the old Contact! Maisa (moustache, sunglasses). */
val MAISA: Int get() = ToaIds.npc(11807) ?: Npcs.MAISA
val KERIS_PARTISAN: Int? get() = ToaIds.item(25979)
val CIRCLET_OF_WATER: Int? get() = ToaIds.item(26967)
