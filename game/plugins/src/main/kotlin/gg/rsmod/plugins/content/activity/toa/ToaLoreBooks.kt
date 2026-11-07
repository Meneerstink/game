package gg.rsmod.plugins.content.activity.toa

/**
 * N462 the six ToA lore books (OSRS Wiki Tombs_of_Amascut 'Players can find six books during the raid'): the four captures are dropped by
 * Akkha/Ba-Ba/Kephri/Zebak, The wardens by the last Warden ('upon defeating them'), The jackal's torch by searching the bags (loc 44984,
 * op Search) in the west end of Osmumten's burial chamber. Item pages (Infobox): options Read/Drop, untradeable. Drop rarity 'Always' x1.
 * Text = the Wiki transcripts verbatim (oldid in each url), read with the existing book interface (content/items/books).
 */
object ToaLoreBooks {
    data class Book(val osrs: Int, val title: String, val sourceUrl: String, val text: String)

    val ALL: List<Book> = listOf(
        Book(27302, "Het's capture", "https://oldschool.runescape.wiki/w/Transcript:Het%27s_capture?oldid=14316776", """
            Osmumten's monument to Het is a sight to behold. I could spend days theorising about the symbolism behind every detail, but time is not on my side.

            In this great chamber dedicated to his glory, Het himself is now imprisoned, guarded by an entity unlike any I have encountered: Akkha.

            It is said that long ago, the Devourer was cursed never to gain followers. If this curse ever existed is hard to say, but if it did, its effectiveness seems to have diminished over the years. While few in number, there are those who truly pledge themselves to the Devourer and all her destructive ways.

            To be a follower of the Devourer is to be an outcast at best. It's therefore no surprise that these outcasts have often banded together in solidarity. One mostly forgotten but still notable grouping was the Children of Rebirth.

            The Children of Rebirth was a cult of former warriors dedicated to the Devourer's original form, Amascut - the goddess of rebirth. They were firm in their belief that they must achieve a perfect death, so that Amascut would allow them to be reborn in perfected form.

            When the day of their supposed rebirth came, they performed a ritual to Amascut and poisoned themselves in her name. Of course, it was long assumed that their ritual achieved nothing, but it seems that assumption wasn't quite correct.

            With Amascut long gone, the ritual instead called out to the Devourer and she arrived to find Akkha as the sole survivor among the many dead. His poison had failed, but he was on the verge of death.

            The Devourer took Akkha and sealed him in a sarcophagus, trapping him between life and death. She then performed a ritual of her own, calling out to Het.

            While Het almost certainly realised it was a trap, he was unable to ignore the call. Like many of his fellow cultists, Akkha's life prior to the cult was as a soldier dedicated to Het, and so the god felt he had a duty to help his former acolyte.

            Het arrived expecting to face the Devourer, but was faced with a manifestation of Akkha himself. Fueled by vengeance against the god who had abandoned him, this manifestation gave the Devourer the distraction she needed to capture Het.

            Now, all these years later, Akkha remains a slave to the Devourer. While still trapped in his sarcophagus, powerful manifestations of his emotions make for perfect guardians, ensuring that none will be able to free his former god.
        """.trimIndent()),
        Book(27304, "Apmeken's capture", "https://oldschool.runescape.wiki/w/Transcript:Apmeken%27s_capture?oldid=14321209", """
            By the time I first saw the chamber dedicated to Apmeken, it had already been taken over by its new inhabitants. They'd quickly customised the place to their liking and were ready to defend it from any invaders.

            This large family of undead baboons are probably among the more unusual of the Devourer's servants, and indeed like most of her thralls, their fate was not by choice. The Devourer's control over these creatures is all thanks to their matriarch, Ba-Ba.

            In life, Ba-Ba's troop dominated a large area of the Kharidian Desert, thriving under her watchful eye. Yet nothing lasts forever, and age was quickly catching up to Ba-Ba.

            Although a natural part of life, Ba-Ba could not accept her mortality. This fear was not born out of selfishness - her concern was for her troop. Upon her death, Ba-Ba's daughter would have become matriarch, but she was lazy and arrogant. Ba-Ba did not consider her a worthy successor and longed to continue ruling herself.

            Learning of Ba-Ba's desires, the Devourer promised her a way to rule her troop forever. Believing there to be no other option, Ba-Ba agreed.

            Ever the deceiver, the Devourer's true plan soon became clear. With a single spell, she killed Ba-Ba and her entire troop, only to reanimate them all together.

            With her new army, the Devourer had no difficulties in capturing Apmeken. Now, Ba-Ba rules eternally over her undead horde, ensuring Apmeken remains a prisoner to the Devourer.
        """.trimIndent()),
        Book(27306, "Scabaras' capture", "https://oldschool.runescape.wiki/w/Transcript:Scabaras%27_capture?oldid=14316812", """
            An empty lecture theatre isn't the first thing you'd associate with the god of isolation, but in truth, it makes perfect sense for Scabaras. This god has been misunderstood over the years, but history has made it clear that he is the most intelligent of the lesser deities. What better way to represent him than with an empty hall of knowledge?

            Of course, this section of Osmumten's tomb has now taken on a far more sinister meaning, for it is now home to Scabaras himself, and his guardian, Kephri.

            Similar to the cruel manner in which the Devourer captured the other lesser deities, Kephri was key to her plans with Scabaras, who was in fact the first lesser deity she captured.

            The last of her race, Kephri is a dawn scarab - one of an extremely long-lived and intelligent race of beetles. The rest of her kind were wiped out during the great war with the Ruinous Powers, but Kephri escaped death thanks to Scabaras himself.

            Over the years, Kephri dedicated herself to Scabaras, living a content life of isolation. However, no being is infallible, and her isolation soon began to take its toll.

            Fearing for the end of her race, Kephri longed for children to pass on her knowledge. The Devourer learnt of her desires and a pact was soon made. In return for her help, the Devourer would revive her race, making Kephri children even smarter than herself.

            Desire can be the death of reason, and even with all of her intelligence, Kephri failed to see the truth until it was too late. By warping another race of scarabs, the Devourer granted Kephri her children as promised, but they had no more intelligence than a common locust.

            However, the Devourer had promised children even smarter than Kephri herself, and that was a vow she intended to keep. She bound Kephri and destroyed her mind, leaving nothing more than a husk.

            In her final moments of consciousness, Kephri called out to Scabaras, but by the time he arrived, it was too late. The plan had worked perfectly, and thanks to Kephri, The Devourer had captured her first lesser deity.
        """.trimIndent()),
        Book(27308, "Crondis' capture", "https://oldschool.runescape.wiki/w/Transcript:Crondis%27_capture?oldid=14330255", """
            It's hard to say what sort of chamber Osmumten had built to represent Crondis, since most of it sank into the watery depths long ago. I would have given the world to see it in its original glory, but now it serves as just another tool for the Devourer.

            In this sunken chamber, the gluttonous Zebak can be found guarding Crondis. Once just a normal crocodile of the Elid, Zebak perfectly embodied Crondis' principles. However, like so many, he fell victim to the manipulation of the Devourer.

            To be a crocodile in these lands is to live a life of resourcefulness. Food is hard to come by, and death is always just a moment away. Zebak was never the strongest of his kind, or the fastest. However, he may well have been one of the smartest.

            Unable to compete with his rivals in the traditional way, Zebak instead used his ingenuity to lure them into elaborate traps. To begin with, he simply took their food and left them to starve, but over time, his hunger grew. Before long, he started to feast on his victims too.

            In the end, the once weak crocodile had grown to become the strongest. You'd be forgiven for thinking he might have stopped there, but Zebak's hunger knew no end. As he continued to feast, his food supply ran short. This is when the Devourer arrived.

            In return for his service, the Devourer promised Zebak that he would never go hungry again. Blinded by avarice, he willingly agreed.

            Together, Zebak and the Devourer lured Crondis to her imprisonment. Once she was trapped, Zebak eagerly awaited his reward. However, rather than granting the endless supply of food he craved, the Devourer cursed him.

            To a being like Zebak, the curse was a fate worse than death, for it caused every bite of food to turn to dust in his mouth. He longed for death, but the Devourer's curse prevented this as well, binding him to the world of the living.

            Now, the ever-hungry Zebak dwells in this tomb, ensuring that just like him, his former god will never be free.
        """.trimIndent()),
        Book(27310, "The wardens", "https://oldschool.runescape.wiki/w/Transcript:The_wardens?oldid=14330252", """
            It seems my time is up. The Devourer never discovered my writing, but it doesn't matter now anyway. She's found what she seeks and has no further need for us. These words will be my last.

            Much of our early history has long been lost, but traces can still be found by those of us who know where to look. Many tomes reference an ancient war in Osmumten's time, back when Tumeken and Elidinis still walked these lands.

            In war, great weapons can turn the tide against even the strongest foes, and legend has long spoken of Tumeken using a powerful staff to build two such weapons. It was believed any truth to these legends had long since been lost, but here in Osmumten's tomb, the truth is revealed: the weapons exist, and they are the prize the Devourer so fervently seeks.

            In truth, referring to Tumeken's creations as 'weapons' does them no justice. They are in fact two ancient automatons, built to embody Tumeken and Elidinis themselves and seemingly imbued with their divine essence. They are key to the Devourer's plan.

            Between the lesser deities and the Wardens, it's clear now what the Devourer wants. She intends to use the Wardens to claim power from the imprisoned lesser deities. With their power, she will become unstoppable.

            I wish I could keep writing, but she is closing in. I wish I had used my time more judiciously. So many things I should have done and said. All I can do now is pray my words help in some way, and that someone is able to overcome the Devourer.
        """.trimIndent()),
        Book(27312, "The jackal's torch", "https://oldschool.runescape.wiki/w/Transcript:The_jackal%27s_torch?oldid=14330251", """
            Once, long ago, three mortals were led through the land of the dead. They travelled together on the dismal roads of the Grim Underworld.

            The Devourer's beasts were not to be seen back then, but the Underworld was still a treacherous place: souls were lost in mist, and impossible whispers lead them astray to places even the gods knew not. But worst of all was the darkness. More than anything, the Grim Underworld was a dark place, where the Sun's light could never reach. But in his stead was his son. The guide and protector. The wanderer in darkness. Icthlarin, god of the dead.

            Icthlarin blindly led the party of three through the darkness, relying on scent alone. The only light by which to see was the glow from their spirits - enough only to not stumble and fall.

            At last, they came to a passageway, and Icthlarin turned to the first of the three and said: 'This is your port of call. Through here is music and peace everlasting. You will want for nothing. Now go, for my endless task continues and there are more souls to guide.'

            And so, the first of the three bowed graciously and passed on. After a journey through mist, they came to yet another passageway. Icthlarin turned this time to the second of the three and said: 'This is your port of call. Through here is discord and war everlasting. You will always want more. Now go, for my endless task continues and there are more souls to guide.'

            And so, the second of the three bowed respectfully and passed on.

            Then Icthlarin walked in silence. He stopped at every crossroads and crossing, every path and passageway, but still he walked. At last, he turned to the third and asked: 'Where is your port of call? I can find no place for you in all the Underworld. Your scent is on no hall.'

            The last of three replied: 'My place is with you. You outshine your father and all else above. Service, not rest, is my reward.'

            The god grew silent then and knew not what to say. They walked longer through mist until at last Icthlarin said, his back turned: 'You know not of what you speak. I have no port of call, no halls to call my own. My life is spent in in-betweens; my task is never done. No, my father's field, that is best for you.'

            Yet the mortal was persistent: 'I care not for your father's field nor any other hall! I need no port of call!'

            And Icthlarin was angered by their insolence: 'What aid can you give me? Your spirit is dim and has not even the brittle strength of bones.'

            The third answered: 'The light of my spirit may be dim, but it is better than darkness.'

            The god considered: 'It is a lonely path. You will meet many, but they will all pass you by.'

            And the third smiled: 'The better to share it.'

            Icthlarin grew silent once more, but then he turned to the cave wall. He tore free one of the roots of the world and extended it to his mortal companion and said: 'Come then, for our endless task continues, and there are more souls to guide.'

            And with the third's touch, their spirit became the first ember and lit the root's end. It was a beginning. Since then, Icthlarin has never wandered alone, for all those who serve him are wanderers in darkness too. They are the light in the jackal's torch.
        """.trimIndent()),
    )

    /** OSRS book id per boss room path (Het = Akkha, Apmeken = Ba-Ba, Scabaras = Kephri, Crondis = Zebak). */
    val CAPTURE: Map<ToaPath, Int> = mapOf(ToaPath.HET to 27302, ToaPath.APMEKEN to 27304, ToaPath.SCABARAS to 27306, ToaPath.CRONDIS to 27308)
    const val WARDENS = 27310
    const val JACKALS_TORCH = 27312
    const val BAGS = 44984

    /** 'Always' x1 from the boss (item Drop sources): on the ground under the boss that died last (largest max HP among the room's npcs;
     *  for the Wardens the Warden killed last). Public in the instance; ONZEKER: whether OSRS drops one per player is not published. */
    fun drop(world: gg.rsmod.game.model.World, key: ToaRoomKey, spawned: List<gg.rsmod.game.model.entity.Npc>) {
        val osrs = if (key.wardens) WARDENS else if (key.boss) key.path?.let(CAPTURE::get) else null
        val id = osrs?.let(ToaIds::item) ?: return
        val boss = spawned.maxByOrNull { it.getMaximumLifepoints() } ?: return
        world.spawn(gg.rsmod.game.model.entity.GroundItem(id, 1, gg.rsmod.game.model.Tile(boss.getCentreTile())))
    }
}
