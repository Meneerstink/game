package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/**
 * The CoX lore books (OSRS Wiki item pages, Infobox options Read/Drop, untradeable): Tekton's journal (Tekton), Nistirio's manifesto (Vasa
 * Nistirio), Houndmaster's diary (Muttadile), Vanguard judgement (once all Vanguards are dead), Transdimensional notes (Vespula) - each
 * 'Always' x1, 'No longer dropped after reading the book' (Wiki drop notes); Creature keeper's journal by searching the creature keeper's corpse
 * (loc 29875 'Search'); the Dark journal from the ancient chest (CoxLoot). Text = the Wiki transcripts verbatim (Transcript: pages, 16-11),
 * read with the existing book interface (content/items/books, as ToA's ToaLoreBooks).
 * ONZEKER: whose reading stops the drop in a party - here the book still drops while any raider in the room has not read it.
 */
object CoxLoreBooks {
    data class Book(val osrs: Int, val title: String, val sourceUrl: String, val text: String)

    /** OSRS ids of the books an account has read ("20890,20888,..."). */
    val READ = AttributeKey<String>(persistenceKey = "cox_read_books")

    fun hasRead(p: Player, osrs: Int) = p.attr[READ].orEmpty().split(',').contains(osrs.toString())
    fun markRead(p: Player, osrs: Int) { if (!hasRead(p, osrs)) p.attr[READ] = (p.attr[READ].orEmpty().split(',').filter { it.isNotBlank() } + osrs.toString()).joinToString(",") }

    const val TEKTON = 20890
    const val VASA = 20888
    const val MUTTADILE = 20897
    const val VANGUARD = 20895
    const val VESPULA = 20893
    const val KEEPER = 20886
    const val DARK = 20899

    val ALL: List<Book> = listOf(
        Book(20890, "Tekton's journal", "https://oldschool.runescape.wiki/w/Transcript:Tekton%27s_journal", """
            Tekton is strong! Tekton was always strong, but now Master made Tekton even stronger. Tekton is pleased.

            Tekton has served Master for long time. Tekton built huge buildings, clever buildings with magic in the walls. Tekton made buildings for people to praise Master in, big buildings with pretty decorations so everyone knows how great Master is.

            Then Master had to leave kingdom. People ungrateful, didn't understand they should obey Master like Tekton did.

            Tekton left kingdom with Master, and Master's loyal friends. Master came up mountain to find funny crystal.

            Tekton was clever, understood all about crystals. Tekton knew how to draw power from crystals to make magic buildings for Master.

            Tekton not sure how it worked anymore. Ever since Master made Tekton so strong, Tekton can't remember much about crystals.

            Tekton can't remember how to make buildings either.

            Tekton can't remember much about Tekton anymore. Didn't Tekton have a spouse back in the big kingdom? Tekton not sure what spouse is for anymore.

            But that okay. Master made Tekton strong, so Master must want Tekton's strength, not Tekton's memory.

            Mountain caves have magic that Master wanted, but Master didn't know how to take it. But Tekton clever - Tekton learnt to take the magic from the caves.

            Tekton built Master a new temple on mountain top. Tekton's temple could focus cave magic, bring it out of caves so anyone could use it without needing to be as clever as Tekton.

            Master used Tekton's temple to wield cave magic. It made Master very happy.

            But Master not so happy when everyone else started wielding cave magic too, especially nasty high priest. Master didn't really like the high priest.

            Master told Tekton to shut down temple. Master wanted Tekton to find new ways to wield cave magic, but so only Master could do it. Tekton broke temple like Master said - but only slightly, so Tekton can fix it again if Master ever changes his mind.

            Tekton tried to teach Master how to wield cave magic without needing temple, but Master couldn't do it. Maybe Master didn't understand crystals as well as Tekton did.

            Master not so happy about that.

            One day Master took Tekton into cave, and promised to reward Tekton for all Tekton's service. Master promised Tekton immortality! Tekton very happy.

            Master created new body for Tekton out of rock and metal from caves, and transferred Tekton's soul into it. Tekton's old body disintegrated, but that okay now Tekton was living in shiny new body that Master made.

            Master enchanted Tekton's anvil, and brought it into cave, and taught Tekton to use it to fix the new body whenever it get damaged. Master may not understand crystals as well as Tekton, but Master still very powerful.

            New body is very strong, stronger than Tekton's old body. Master protected it from magic damage and ranged damage too; anyone want to hit Tekton now, Tekton hit them straight back!

            Now Tekton very strong, but Master's friends were rude. They called Tekton stupid. Tekton got confused - new body is what Master made, so new body must be better. But Master's friends not want to talk to Tekton anymore, especially nasty high priest.

            So Tekton staying in cave now, near anvil. It safer for Tekton to stay near anvil, so Tekton can repair body if it get damaged.

            Tekton wonders sometimes why new body doesn't understand crystals as well as old body did.

            Tekton wonders what happened to Tekton's spouse from big kingdom, or Tekton's children. They sound important, but Tekton can't remember much.

            But Tekton not worried. Master made Tekton this body, so this body must be what Tekton needs to serve Master best.

            Tekton had better keep on using anvil to make body strong, until Master come back to give Tekton more work to do.

            Until then, Tekton can wait for Master.
        """.trimIndent()),
        Book(20888, "Nistirio's manifesto", "https://oldschool.runescape.wiki/w/Transcript:Nistirio%27s_manifesto", """
            Oh happy accident!

            Magic has been child's play to me, all my life. With a few words of power, I could twist the world to suit my pleasure.

            Our lord Xeric has made full use of my gifts; I dare say that my power was instrumental in his ascent to greatness, and he rightly rewarded me by appointing me as his High Priest.

            It is, of course, a matter of regret that I could not sustain our dominion over the kingdom of Great Kourend.

            But Kourend is a limited place, full of limited people. In these caverns, I have no doubt that we shall all learn to transcend such limits - indeed, my accident has already imbued me with power beyond my imaginings.

            We had always known that this mountain housed great power; that is why we travelled here after our exile from Kourend. However, it is one thing to perceive power, but it is often much harder to wield it for oneself. And then came my accident.

            I had entered a cavern in the great labyrinths under Mount Quidamortem, and found it to contain a huge number of purple crystals.

            Immediately I was drawn to investigate, for such crystals are a sign of the magicks that we've been seeking.

            If we could learn to wield this power for ourselves, nothing could prevent our triumph.

            While my attention was focused solely on the crystals, I failed to detect a tremor in the cavern. Suddenly the roof gave way, and I felt myself pinned to the ground by the crushing weight of the rocks falling from above.

            I admit I panicked - powerful though I am, I am not invulnerable, and the power within the mountain could have destroyed me.

            In my shock, I failed to maintain the bindings of my incorporeal form, so my essence flowed outwards, intersecting with the rocks and the fragments of crystal within them.

            That was how it happened. I should have faded away into oblivion, but instead I felt the rocks and crystal shards respond to my essence. I could move them. I could control them. And I could control the power that resided within them.

            Through such a simple accident, I had stumbled upon a method of wielding the power of the caves for myself. The rocks and crystals had become an avatar for my essence, and their power was mine to command.

            I returned, transfigured, to the others. They were astonished by my new form, and feared for their lives.

            They were right to be fearful. My power was beyond anything they could comprehend. With a mere thought I could have collapsed the mountain upon them all, then returned to Kourend to claim the kingdom as my own.

            In fact, that would be fitting. Xeric appointed me as his High Priest, but the time of priests is over.

            What is a priest?

            A priest is a conduit between the mundane and the transcendent, yet the priest remains mundane.

            A priest is a guide, teaching the plodding folk of this world to look beyond the everyday toil of their labour, elevating their thoughts to seek the divine, yet the priest must plod among them.

            I am not the priest.
            I am the god.

            I am the god, and I need no priests. There is no need for a guide or conduit, not when my power is manifest.

            If Xeric will not recognise me as his lord and master, away with him!
        """.trimIndent()),
        Book(20897, "Houndmaster's diary", "https://oldschool.runescape.wiki/w/Transcript:Houndmaster%27s_diary", """
            Please, dear reader, blame me not for my sins...

            How did I find myself in this damp and smelly hell-hole? I fear I can no longer leave; woe to those brave enough to try.

            I had received a letter, a request for my skills in hound mastery, to replace a creature keeper who had 'retired'. Or so the letter claimed.

            Sadly the truth is that anyone could have done this, but the pay was too good to refuse.

            Admittedly the location was a little odd - a mountain top, far to the west?

            Unfortunately I am but a man controlled by greed, and for that I now pay dearly.

            In the beginning, there were hounds to care for, more than a dozen strays. They seemed to enjoy my company, and I enjoyed theirs.

            My employer, Lord Xeric, was conducting various experiments on the hounds.

            Using the powers of this strange place, he sought to speed up the mutation.

            I was too scared to say anything. I'd heard wild rumours of the things hidden within these dark walls.

            Monsters?

            Cursed employees who had failed Lord Xeric, perhaps?

            I dared not think it - I focused all my energies on the task Lord Xeric had assigned me, so that I would not experience his displeasure for myself!

            The crystals? The beast below? Science? Something down here made Lord Xeric's experiment a success, or at least that's what he tells me. I fail to see the success of his creation.

            It had to be her though, of course it did. She was the strongest of the pack, my personal favourite, now a twisted, scaly abomination.

            Does she even remember me anymore? Would I want her to?

            Certainly not after what I've done!

            I'm not proud of it, but it keeps me alive enough to fulfil Master's demands, keeping her - no - keeping IT alive.

            When I believed that this situation couldn't become worse, I was a fool!

            Before Master began his wretched experiments, we failed to notice her... condition. There's two of them now.

            Perhaps I'm the true monster after all.

            A byproduct of the experiments is what keeps the creatures fed and well. My new role is more of a gardener than anything else.

            Now I labour in an incredibly draining job for a master who hasn't been here for months. Is my position a little more permanent than I had realised?

            The mountain just shook! Has Master returned to us?

            I must check on them, for he certainly will return if I fail him.
        """.trimIndent()),
        Book(20895, "Vanguard judgement", "https://oldschool.runescape.wiki/w/Transcript:Vanguard_judgement", """
            , reader, of the wrath of our Lord Xeric.

             of the punishment that awaits those who fail him.

             of the Judgement of the Vanguard.

            Our Lord Xeric forgives not, nor does he forget;  to serve him with fear!

            The penalty for failure is immense; do not risk suffering the same fate as the Vanguard.

            In our time in Kourend, along with his own magical power, Lord Xeric had a mighty army at his command, enforcing his rule over the people.

            Whenever traitors muttered against our Lord, his troops would ravage their homes in the dead of night, arresting anyone suspected of disloyalty or rebellion.

            Under questioning by the commanders, who were empowered to use any interrogation techniques they deemed necessary in Lord Xeric's service, the traitors would inevitably reveal more suspects for the troops to arrest.

            Thus the power of our Lord Xeric was maintained over his people. The most prominent commanders of Lord Xeric's army were known as the Vanguard, three elite officers who rose quickly through the ranks. Their cunning on the battlefield, their ruthlessness in the interrogation chambers and their loyalty to our Lord were exemplary.

            Each of the Vanguard was proficient in a different form of combat, and when the three stood together, none could prevail against their united power because they would share their strength to protect each other. For their successes in his service, Lord Xeric rewarded them with the command of his army.

            Then came the fall. A proletarian uprising caught the army by surprise. Had the Vanguard performed their duty, perhaps disaster would have been averted, and Lord Xeric would have remained on the throne of Kourend. However, even the legendary Vanguard were frail.

            As their troops were slaughtered around them by the rage of the proletariat, the Vanguard fled the kingdom to protect their own skins.

            Lord Xeric took note of their retreat, and I witnessed him vow that their fate would be worse than if they had stayed to die serving him.

            Lord Xeric's word is not given lightly; I shuddered to think of what would befall the Vanguard when we found them.

            Even as we marched away from Kourend ourselves, I was sure he would carry out his word and exact vengeance on the Vanguard.

            In time, Lord Xeric led us to Mount Quidamortem, and we established our new settlement within its caverns.

            As our Lord studied the secrets of the mountain, and the crystals within its depths, his power increased, bringing great cheer to those of us who still survived to serve him.

            Then, one day, our sentinels reported movement on the flanks of the mountain. It was the Vanguard. They had heard of our new settlement on Mount Quidamortem, and had come to re-pledge their allegiance to our Lord.

            Their apology was abject, I will admit. They admitted that they had fled the battle, abandoning their duty to Lord Xeric. They pledged that they would never fail him again, if he would take them back into his service.

            I waited for Lord Xeric to vent his wrath upon them. But he did not. Instead he welcomed the Vanguard, letting them approach his presence - an honour granted only to his most trusted servants. He bade us deck them in fine clothes and set out a feast in their honour, as though they were his long-lost kin rather than traitors who had failed him.

            I confess I almost came to doubt the wisdom of Lord Xeric - how could he stomach the presence of such craven turncoats, let alone grant them such honour?

            But the wisdom of Lord Xeric had not failed, nor had his memory.

            Lord Xeric invited the Vanguard to enter the caverns with him, so that he could imbue them with the same power that he could now draw from the crystals of the mountain. He allowed me to follow, as a witness.

            We travelled through the caverns to a fiery chamber, where glowing lava reflected off the facets of a hundred orange crystals. Once there, Lord Xeric invited the Vanguard to lower their defences so he could instil into them the power of the crystals.

            I watched in distaste as the traitors reached out to accept the power - power that had never been offered to me.

            Instead, I saw our Lord's expression change. The sickly benevolence in his eyes was replaced with bloodshot wrath, and his face contorted into a sneer of derision. The Vanguard realised that they had been tricked - but it was too late.

            Their grasping hands twisted. Their legs buckled. Their skin glowed, as if the heat of the chamber's lava had set their blood to boiling. From their mouths came a ghastly bubbling scream that rapidly died away as their ruined bodies collapsed to the ground. But that was not the end.

            Lord Xeric waved a hand in a curious gesture, and I felt a magical wave emanating from the crystals of the chamber. Writhing on the ground, the Vanguard began to melt and re-form into new shapes.

            Soon three lumpen figures lay, hunching on the ground at Lord Xeric's feet. There was no light of intelligence in their eyes; their souls had clearly been destroyed. And then, in tones of the coldest malice, Lord Xeric addressed them:

            Lord Xeric led me away from that terrible place. I could not speak. I could hardly walk, but I dared not show weakness before my Lord.

            But Lord Xeric spoke to me. He knew of the doubts I had harboured, he knew how I had felt when I believed he was welcoming the Vanguard back.

            And my Lord understood. He knew I would not doubt him again. (I never have.)

            That day, Lord Xeric bade me record all that I had seen of the Judgement of the Vanguard. My record is to stand as a testimonial to his wrath and his vengeance. I have written it down faithfully.

            So now you have read to the end, reader,  from what I have recorded here.

             that, if you must choose between serving Lord Xeric and saving your own skin, our Lord will not forgive those who choose poorly.

             from the fate of the Vanguard. And, if ye fail to learn... upon your own head be your fate.
        """.trimIndent()),
        Book(20893, "Transdimensional notes", "https://oldschool.runescape.wiki/w/Transcript:Transdimensional_notes", """
            This has not been our finest hour.

            We have faced many dangers since our departure from Kourend, and every single one has been overcome. The privations of the journey to Quidamortem, the ferocious beasts that dwelt here, even the weakness and treachery of our former allies - we have survived them all, and marched on triumphantly.

            For this we give thanks to our mighty lord, and we shall continue to serve him loyally as we explore these caverns.

            However, I must admit this latest experiment has not been a success.

            In the caverns beneath Quidamortem, our lord sensed a disruption in the fabric of reality.

            It is known, of course, that the Dark Altar can connect to other planes of existence. That is how it sustains our runic altars, by linking Kourend to whatever far-off realms contain the true Blood and Soul Altars. However, it still surprised us to find that the same power was available here.

            Naturally our lord sought to tap into this power - after all, it's what brought us to this cursed mountain in the first place. Unfortunately, our lord's attempt to rupture space and time was only too successful!

            Our lord managed, with much effort, to widen the disruption until a stable portal stood before us, allowing us to see out of this world and into some other realm.

            Through the portal, we glimpsed a strange, red-hued world, where monstrous creatures lurched through the tendrils of a landscape that looked - and smelt - like the intestines of a rotting corpse.

            In our awe, we looked for too long, forgetting that a door opens in two directions. We could see these creatures, and they could see us.

            Suddenly a vast form squeezed its way through the portal towards us. I have no words for it, but if a demon were to mate with a wasp, this creature would surely be the ungodly fruit of the union.

            We sought to drive it back with our magicks, but it seemed well protected from such attacks, and eventually our lord bade us retreat.

            Some of our minor servants had to be left behind as a distraction for the creature; their sacrifice bought us time to block the chamber behind us.

            Our lord fears that our experiment has permanently ruptured the walls between the worlds - even if we can find a way to close this portal, more may open spontaneously now that the way has opened. We shall have to tread carefully in future as we explore these caverns, lest we stumble across more such portals.

            However, the ability to open these portals may prove valuable, despite our current setback. I have seen our lord defeated before, back when we were driven out of Kourend, and I know he does not accept defeat.

            Just as he responded to our exile by leading us to Quidamortem in search of greater powers, I am sure he will respond to today's developments by finding a way to use them for his own purposes. After all, these portals would give us access to a limitless army of fearsome creatures, if we could find a way to control them.

            It's almost a shame that creature keeper died so early in the expedition - he was a fool, but his talents might have served us well here.

            Alternatively, if we were able to open a portal to a different place, we may even discover new worlds to conquer...

            But that is speculation. For now, our lord commands us to return to the chamber of the portal and the demonic wasp - perhaps he has a plan to slay the creature and take control of the portal.

            Our lord's council is smiling at me strangely as we prepare for our next battle with the creature. If I did not know better, I might suspect they were planning to use me to distract the beast, like we did with the servants before.

            However, I have served Lord Xeric loyally for many years, through good times and bad. I am sure he would never leave me to die like that.
        """.trimIndent()),
        Book(20886, "Creature keeper's journal", "https://oldschool.runescape.wiki/w/Transcript:Creature_keeper%27s_journal", """
            These scavengers are fascinating. I have read of similar things in the tales of the seafaring folk, who described voyages to distant lands where scuttling creatures called 'skavids' lurked in dark caves, stealing food and shiny objects from nearby settlements.

            Our scavengers may be a related branch of the species, though there is no sign of any intelligence here.

            Nevertheless, the larger scavengers have a degree of brute strength, and I envisage they could be useful as warriors if they can be suitably trained.

            First I must teach my scavengers to obey me. I will begin by making them dependent upon me for food, so that I can use food to reward obedience.

            I have collected a supply of cavern grub cocoons, to provide a fresh supply of grubs for my scavengers. These are securely locked away, so that the scavenger cannot obtain them for itself.

            Next, I began to feed a large scavenger with the grubs. I have fed it daily in its food trough; it no longer even thinks of going scavenging for itself, so it is becoming completely dependent on me.

            Soon I will begin to send it into battle against the other creatures that roam these caves, and teach it that it must fight if it wants to be fed.

            This technique of instilling dependency is marvellous. If I can make it work on the scavengers, consider how it could be applied to a wider range of minions. Perhaps one day I could even apply it to the citizens of Kourend, if we can ever return to the kingdom, by taking administrative control of all means of production so that the citizens are fed only at the 'benign' hand of their masters. This may prove a more effective means of control than the brutish methods that failed us before. But that is a consideration for another day.

            Meanwhile I have found some lizardmen in this cave that look like a suitable combat challenge for my scavenger - unfortunately the creatures saw me, and they attacked immediately. I had no trouble retreating, though I have begun to feel unwell since the incident; I do hope their spit has not infected me.
        """.trimIndent()),
        Book(20899, "Dark journal", "https://oldschool.runescape.wiki/w/Transcript:Dark_journal", """
            Our exploration of the caves is complete, and has been a success.

            Our lord's followers have dwindled in number, it is true. Few of us who shared his exile from Kourend are still alive to serve him today.

            Many fell to the perils of the cave; they were weak, and our lord teaches us that the weak should expect no other fate.

            Others forced our lord to discipline them, some for outright treachery, some for other failings. Let them remain in this cave as a testament to the supremacy of our lord!

            Our lord has graciously appointed me as his new High Priest, and I shall endeavour to be a more worthy servant than my vain predecessor.

            Nevertheless, though our party was reduced in numbers, our lord has led us to victory over the Guardian in the Deep, and has seized the power that he sought.

            Now we make our camp in the Guardian's chamber, among the skulls of its former prey. Although I doubt it has been permanently killed, the Guardian will not trouble anyone again for a long time.

            The weapons and armour that we have developed in the caves can stay here, in this place of safety. As our lord now wields power over the crystals of the cave, he intends to create a secure seal around our store. He says it would take an earthquake to shatter it!

            I am truly awestruck by the power that our lord now wields. If we were to return to Kourend, as he originally planned, I have no doubt that we would regain our former dominion over the kingdom with very little effort.

            However, while our lord remains as secretive as ever - in fact, he has become more so after the treachery of our former followers - I sense he has a new plan in mind.

            Perhaps the throne of Kourend has become too small a trophy to satisfy the ambitions of Lord Xeric.
        """.trimIndent()),
    )
}
